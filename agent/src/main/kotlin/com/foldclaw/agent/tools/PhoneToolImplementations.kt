package com.foldclaw.agent.tools

import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.model.ToolDescriptor
import com.foldclaw.domain.tool.AppLaunchBackend
import com.foldclaw.domain.tool.GetWeatherTool
import com.foldclaw.domain.tool.OpenAppTool
import com.foldclaw.domain.tool.OpenSettingsPageTool
import com.foldclaw.domain.tool.RingerModeBackend
import com.foldclaw.domain.tool.RiskLevel
import com.foldclaw.domain.tool.SetRingerModeTool
import com.foldclaw.domain.tool.Tool
import com.foldclaw.domain.tool.ToolContext
import com.foldclaw.domain.tool.ToolOutcome
import com.foldclaw.domain.tool.WeatherBackend
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class OpenAppToolImpl(
    private val backend: AppLaunchBackend,
) : Tool {
    override val descriptor: ToolDescriptor = OpenAppTool.descriptor
    override val riskLevel: RiskLevel = RiskLevel.REVERSIBLE_SIDE_EFFECT

    override suspend fun execute(ctx: ToolContext, argumentsJson: String): Result<ToolOutcome> {
        val parsed = parseArgs(argumentsJson)
            ?: return Result.Failure(DomainError(ErrorKind.ProviderInvalidResponse, "参数解析失败"))
        if (parsed.appName.isNullOrBlank() && parsed.packageName.isNullOrBlank()) {
            return Result.Failure(DomainError(ErrorKind.PolicyDenied, "请提供 appName 或 packageName"))
        }
        val resolved = when (val r = backend.resolve(parsed.appName, parsed.packageName)) {
            is Result.Success -> r.data
            is Result.Failure -> return Result.Success(ToolOutcome.Failure(r.error))
        }
        return when (val launch = backend.launch(resolved.packageName)) {
            is Result.Success -> Result.Success(
                ToolOutcome.SideEffect(
                    summary = buildString {
                        append("已打开「${resolved.label}」（${resolved.packageName}）。")
                        append("若用户目标不止打开应用，下一步必须 get_ui_tree 并继续操作，不要结束。")
                    },
                    expectedPackageNames = setOf(resolved.packageName),
                    expectedText = null,
                    irreversible = false,
                    launchedByIntent = true,
                ),
            )
            is Result.Failure -> Result.Success(ToolOutcome.Failure(launch.error))
        }
    }

    private fun parseArgs(json: String): OpenAppTool.Args? = try {
        val o = Json.decodeFromString<JsonObject>(json)
        OpenAppTool.Args(
            appName = o["appName"]?.jsonPrimitive?.contentOrNull?.takeIf { it != "null" },
            packageName = o["packageName"]?.jsonPrimitive?.contentOrNull?.takeIf { it != "null" },
        )
    } catch (_: Exception) {
        null
    }
}

class OpenSettingsPageToolImpl(
    private val backend: AppLaunchBackend,
) : Tool {
    override val descriptor: ToolDescriptor = OpenSettingsPageTool.descriptor
    override val riskLevel: RiskLevel = RiskLevel.REVERSIBLE_SIDE_EFFECT

    override suspend fun execute(ctx: ToolContext, argumentsJson: String): Result<ToolOutcome> {
        val page = try {
            val o = Json.decodeFromString<JsonObject>(argumentsJson.ifBlank { "{}" })
            o["page"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
        } catch (_: Exception) {
            null
        } ?: return Result.Failure(DomainError(ErrorKind.ProviderInvalidResponse, "缺少 page"))

        return when (val res = backend.openSettingsPage(page)) {
            is Result.Success -> Result.Success(
                ToolOutcome.SideEffect(
                    summary = buildString {
                        append("已打开系统设置页「$page」（${res.data.packageName}）。")
                        append("下一步必须 get_ui_tree，再 tap_node 进入具体项（如字体大小）并调整；不要到此结束。")
                    },
                    expectedPackageNames = setOf(
                        res.data.packageName,
                        "com.android.settings",
                        "com.samsung.android.settings",
                    ),
                    expectedText = null,
                    irreversible = false,
                    launchedByIntent = true,
                ),
            )
            is Result.Failure -> Result.Success(ToolOutcome.Failure(res.error))
        }
    }
}

class SetRingerModeToolImpl(
    private val backend: RingerModeBackend,
) : Tool {
    override val descriptor: ToolDescriptor = SetRingerModeTool.descriptor
    override val riskLevel: RiskLevel = RiskLevel.REVERSIBLE_SIDE_EFFECT

    override suspend fun execute(ctx: ToolContext, argumentsJson: String): Result<ToolOutcome> {
        val mode = try {
            val o = Json.decodeFromString<JsonObject>(argumentsJson.ifBlank { "{}" })
            o["mode"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
        } catch (_: Exception) {
            null
        } ?: return Result.Failure(DomainError(ErrorKind.ProviderInvalidResponse, "缺少 mode"))

        return when (val res = backend.setMode(mode)) {
            is Result.Success -> Result.Success(
                ToolOutcome.SideEffect(
                    summary = res.data,
                    expectedPackageNames = emptySet(),
                    expectedText = null,
                    irreversible = false,
                    launchedByIntent = false,
                ),
            )
            is Result.Failure -> Result.Success(ToolOutcome.Failure(res.error))
        }
    }
}

class GetWeatherToolImpl(
    private val backend: WeatherBackend,
) : Tool {
    override val descriptor: ToolDescriptor = GetWeatherTool.descriptor
    override val riskLevel: RiskLevel = RiskLevel.READ_ONLY

    override suspend fun execute(ctx: ToolContext, argumentsJson: String): Result<ToolOutcome> {
        val parsed = parseArgs(argumentsJson)
            ?: return Result.Failure(DomainError(ErrorKind.ProviderInvalidResponse, "参数解析失败"))
        if (parsed.city.isBlank()) {
            return Result.Failure(DomainError(ErrorKind.PolicyDenied, "城市不能为空"))
        }
        val offset = parsed.dayOffset.coerceIn(0, 7)
        return when (val res = backend.forecast(parsed.city.trim(), offset)) {
            is Result.Success -> Result.Success(ToolOutcome.Text(res.data))
            is Result.Failure -> Result.Success(ToolOutcome.Failure(res.error))
        }
    }

    private fun parseArgs(json: String): GetWeatherTool.Args? = try {
        val o = Json.decodeFromString<JsonObject>(json)
        val city = o["city"]?.jsonPrimitive?.contentOrNull ?: return null
        GetWeatherTool.Args(
            city = city,
            dayOffset = o["dayOffset"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
        )
    } catch (_: Exception) {
        null
    }
}
