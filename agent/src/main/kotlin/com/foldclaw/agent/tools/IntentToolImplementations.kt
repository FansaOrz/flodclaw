package com.foldclaw.agent.tools

import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.model.ToolDescriptor
import com.foldclaw.domain.tool.AlarmSetTool
import com.foldclaw.domain.tool.CalendarInsertTool
import com.foldclaw.domain.tool.RiskLevel
import com.foldclaw.domain.tool.Tool
import com.foldclaw.domain.tool.ToolContext
import com.foldclaw.domain.tool.ToolOutcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

/**
 * 执行后端抽象。生产实现走 IntentExecutors(device 模块),
 * 测试用 Fake。让工具保持可单测——不直接碰 Android Intent。
 */
interface IntentBackend {
    fun createCalendarEvent(args: CalendarInsertTool.Args): Result<Unit>
    fun setAlarm(args: AlarmSetTool.Args): Result<Unit>
}

/** AOSP + 三星时钟包名（Z Fold / One UI 常见为后者）。 */
val CLOCK_PACKAGES: Set<String> = setOf(
    "com.android.deskclock",
    "com.sec.android.app.clockpackage",
    "com.google.android.deskclock",
)

/** AOSP + 三星日历包名。 */
val CALENDAR_PACKAGES: Set<String> = setOf(
    "com.android.calendar",
    "com.samsung.android.calendar",
    "com.google.android.calendar",
)

/**
 * 日历工具实现。REVERSIBLE_SIDE_EFFECT:只预填草稿,用户在日历 App 内确认保存。
 */
class CalendarInsertToolImpl(
    private val backend: IntentBackend,
) : Tool {
    override val descriptor: ToolDescriptor = CalendarInsertTool.descriptor
    override val riskLevel: RiskLevel = RiskLevel.REVERSIBLE_SIDE_EFFECT

    override suspend fun execute(ctx: ToolContext, argumentsJson: String): Result<ToolOutcome> {
        val parsed = parseArgs(argumentsJson)
            ?: return Result.Failure(DomainError(ErrorKind.ProviderInvalidResponse, "参数解析失败"))
        if (parsed.title.isBlank()) {
            return Result.Failure(DomainError(ErrorKind.PolicyDenied, "日程标题不能为空"))
        }
        if (parsed.startEpochMs <= 0) {
            return Result.Failure(DomainError(ErrorKind.PolicyDenied, "开始时间无效"))
        }
        return when (val res = backend.createCalendarEvent(parsed)) {
            is Result.Success -> Result.Success(
                ToolOutcome.SideEffect(
                    summary = "已在日历打开新建日程页并预填「${parsed.title}」,请在日历应用内确认保存。",
                    expectedPackageNames = CALENDAR_PACKAGES,
                    expectedText = parsed.title,
                    irreversible = false,
                    launchedByIntent = true,
                )
            )
            is Result.Failure -> Result.Success(ToolOutcome.Failure(res.error))
        }
    }

    private fun parseArgs(json: String): CalendarInsertTool.Args? {
        return try {
            val o = Json.decodeFromString<JsonObject>(json)
            val start = o["startEpochMs"]?.jsonPrimitive?.longOrNull ?: return null
            CalendarInsertTool.Args(
                title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                location = o["location"]?.jsonPrimitive?.contentOrNull,
                startEpochMs = start,
                endEpochMs = o["endEpochMs"]?.jsonPrimitive?.longOrNull,
                allDay = o["allDay"]?.jsonPrimitive?.booleanOrNull ?: false,
                description = o["description"]?.jsonPrimitive?.contentOrNull,
            )
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * 闹钟工具实现。
 * 风险定为 REVERSIBLE_SIDE_EFFECT：强制 skipUi=false，系统时钟会再确认，
 * FoldClaw 不再每次弹审批卡。支付/删除/卸载等 CRITICAL 工具才强制确认。
 */
class AlarmSetToolImpl(
    private val backend: IntentBackend,
) : Tool {
    override val descriptor: ToolDescriptor = AlarmSetTool.descriptor
    override val riskLevel: RiskLevel = RiskLevel.REVERSIBLE_SIDE_EFFECT

    override suspend fun execute(ctx: ToolContext, argumentsJson: String): Result<ToolOutcome> {
        val parsed = parseArgs(argumentsJson)
            ?: return Result.Failure(DomainError(ErrorKind.ProviderInvalidResponse, "参数解析失败"))
        if (parsed.hour !in 0..23 || parsed.minutes !in 0..59) {
            return Result.Failure(DomainError(ErrorKind.PolicyDenied, "闹钟时间非法"))
        }
        // 强制不跳过 UI:必须显示确认界面
        val safe = parsed.copy(skipUi = false)
        return when (val res = backend.setAlarm(safe)) {
            is Result.Success -> Result.Success(
                ToolOutcome.SideEffect(
                    summary = "已打开闹钟设置 ${safe.hour}:${"%02d".format(safe.minutes)}" +
                        (safe.label?.let { "「$it」" } ?: "") +
                        "，请在时钟应用内确认。",
                    expectedPackageNames = CLOCK_PACKAGES,
                    expectedText = null,
                    irreversible = false,
                    launchedByIntent = true,
                )
            )
            is Result.Failure -> Result.Success(ToolOutcome.Failure(res.error))
        }
    }

    private fun parseArgs(json: String): AlarmSetTool.Args? {
        return try {
            val o = Json.decodeFromString<JsonObject>(json)
            val hour = o["hour"]?.jsonPrimitive?.longOrNull?.toInt() ?: return null
            val minutes = o["minutes"]?.jsonPrimitive?.longOrNull?.toInt() ?: return null
            AlarmSetTool.Args(
                hour = hour,
                minutes = minutes,
                label = o["label"]?.jsonPrimitive?.contentOrNull,
                message = o["message"]?.jsonPrimitive?.contentOrNull,
                vibrate = o["vibrate"]?.jsonPrimitive?.booleanOrNull ?: true,
                skipUi = false, // 永远不静默
            )
        } catch (e: Exception) {
            null
        }
    }
}
