package com.foldclaw.agent.tools

import com.foldclaw.domain.memory.MemoryStore
import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.model.ToolDescriptor
import com.foldclaw.domain.tool.DeviceStatusBackend
import com.foldclaw.domain.tool.ForgetFactTool
import com.foldclaw.domain.tool.GetDeviceStatusTool
import com.foldclaw.domain.tool.GetNotificationsTool
import com.foldclaw.domain.tool.ListMemoriesTool
import com.foldclaw.domain.tool.NotificationSummaryBackend
import com.foldclaw.domain.tool.RememberFactTool
import com.foldclaw.domain.tool.RiskLevel
import com.foldclaw.domain.tool.Tool
import com.foldclaw.domain.tool.ToolContext
import com.foldclaw.domain.tool.ToolOutcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class RememberFactToolImpl(
    private val store: MemoryStore,
) : Tool {
    override val descriptor: ToolDescriptor = RememberFactTool.descriptor
    override val riskLevel: RiskLevel = RiskLevel.REVERSIBLE_SIDE_EFFECT

    override suspend fun execute(ctx: ToolContext, argumentsJson: String): Result<ToolOutcome> {
        val key = stringArg(argumentsJson, "key")
            ?: return Result.Failure(DomainError(ErrorKind.ProviderInvalidResponse, "缺少 key"))
        val value = stringArg(argumentsJson, "value")
            ?: return Result.Failure(DomainError(ErrorKind.ProviderInvalidResponse, "缺少 value"))
        return try {
            val item = store.upsert(key, value)
            Result.Success(ToolOutcome.Text("已记住「${item.key}」= ${item.value}"))
        } catch (e: Exception) {
            Result.Success(ToolOutcome.Failure(DomainError(ErrorKind.ActionFailed, e.message ?: "记忆写入失败")))
        }
    }
}

class ForgetFactToolImpl(
    private val store: MemoryStore,
) : Tool {
    override val descriptor: ToolDescriptor = ForgetFactTool.descriptor
    override val riskLevel: RiskLevel = RiskLevel.REVERSIBLE_SIDE_EFFECT

    override suspend fun execute(ctx: ToolContext, argumentsJson: String): Result<ToolOutcome> {
        val key = stringArg(argumentsJson, "key")
        val id = longArg(argumentsJson, "id")
        val ok = when {
            !key.isNullOrBlank() -> store.deleteByKey(key)
            id != null -> store.deleteById(id)
            else -> return Result.Failure(DomainError(ErrorKind.ProviderInvalidResponse, "需要 key 或 id"))
        }
        return Result.Success(
            if (ok) ToolOutcome.Text("已删除记忆")
            else ToolOutcome.Failure(DomainError(ErrorKind.ActionFailed, "未找到对应记忆")),
        )
    }
}

class ListMemoriesToolImpl(
    private val store: MemoryStore,
) : Tool {
    override val descriptor: ToolDescriptor = ListMemoriesTool.descriptor
    override val riskLevel: RiskLevel = RiskLevel.READ_ONLY

    override suspend fun execute(ctx: ToolContext, argumentsJson: String): Result<ToolOutcome> {
        val items = store.list(50)
        if (items.isEmpty()) return Result.Success(ToolOutcome.Text("当前没有个人记忆。"))
        val text = items.joinToString("\n") { "#${it.id} ${it.key}: ${it.value}" }
        return Result.Success(ToolOutcome.Text(text))
    }
}

class GetDeviceStatusToolImpl(
    private val backend: DeviceStatusBackend,
) : Tool {
    override val descriptor: ToolDescriptor = GetDeviceStatusTool.descriptor
    override val riskLevel: RiskLevel = RiskLevel.READ_ONLY

    override suspend fun execute(ctx: ToolContext, argumentsJson: String): Result<ToolOutcome> =
        Result.Success(ToolOutcome.Text(backend.statusSummary()))
}

class GetNotificationsToolImpl(
    private val backend: NotificationSummaryBackend,
) : Tool {
    override val descriptor: ToolDescriptor = GetNotificationsTool.descriptor
    override val riskLevel: RiskLevel = RiskLevel.READ_ONLY

    override suspend fun execute(ctx: ToolContext, argumentsJson: String): Result<ToolOutcome> {
        if (!backend.isAccessGranted()) {
            return Result.Success(
                ToolOutcome.Text(
                    "尚未开启通知使用权。请在系统设置 → 通知使用权中允许 FoldClaw，然后重试。",
                ),
            )
        }
        val limit = longArg(argumentsJson, "limit")?.toInt()?.coerceIn(1, 30) ?: 12
        return Result.Success(ToolOutcome.Text(backend.recentSummaries(limit)))
    }
}

private fun stringArg(json: String, key: String): String? = try {
    val o = Json.decodeFromString<JsonObject>(json.ifBlank { "{}" })
    o[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
} catch (_: Exception) {
    null
}

private fun longArg(json: String, key: String): Long? = try {
    val o = Json.decodeFromString<JsonObject>(json.ifBlank { "{}" })
    o[key]?.jsonPrimitive?.longOrNull
} catch (_: Exception) {
    null
}
