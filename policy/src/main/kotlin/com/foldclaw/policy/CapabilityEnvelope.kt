package com.foldclaw.policy

import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.model.ToolDescriptor
import com.foldclaw.domain.tool.RiskLevel
import com.foldclaw.domain.tool.SensitiveTapLabels

/**
 * 任务能力信封。任务启动时冻结，不可被 UI 树/通知/模型输出扩大。
 */
data class CapabilityEnvelope(
    val taskId: String,
    val allowedPackages: Set<String>,
    val allowedDomains: Set<String>,
    val allowedTools: Set<String>,
    val allowedDataCategories: Set<DataCategory>,
    val dataReceivers: Set<String>,
    val maxSteps: Int,
    val maxDurationMs: Long,
    val maxTokens: Int,
    val maxCostUsd: Double,
    val allowSideEffects: Boolean,
) {
    companion object {
        /** Alpha 兼容矩阵包名：时钟 / 日历 / 设置。 */
        val ALPHA_PACKAGES: Set<String> = setOf(
            "com.android.settings",
            "com.samsung.android.settings",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.google.android.deskclock",
            "com.android.calendar",
            "com.samsung.android.calendar",
            "com.google.android.calendar",
            "com.foldclaw",
            "com.foldclaw.debug",
        )

        val ALPHA_TOOLS: Set<String> = setOf(
            "create_calendar_event",
            "set_alarm",
            "open_app",
            "open_settings_page",
            "set_ringer_mode",
            "get_weather",
            "get_device_status",
            "get_notifications",
            "remember_fact",
            "forget_fact",
            "list_memories",
            "get_ui_tree",
            "tap_node",
            "type_text",
            "swipe",
            "go_back",
            "go_home",
        )

        fun alphaDefault(
            taskId: String,
            allowedPackages: Set<String> = ALPHA_PACKAGES,
        ): CapabilityEnvelope = CapabilityEnvelope(
            taskId = taskId,
            allowedPackages = allowedPackages,
            allowedDomains = emptySet(),
            allowedTools = ALPHA_TOOLS,
            allowedDataCategories = setOf(
                DataCategory.UI_TREE,
                DataCategory.SCREEN_TEXT,
                DataCategory.NOTIFICATIONS,
            ),
            dataReceivers = emptySet(),
            maxSteps = 12,
            maxDurationMs = 120_000,
            maxTokens = 80_000,
            maxCostUsd = 0.35,
            allowSideEffects = true,
        )
    }
}

object CriticalTools {
    val NAMES: Set<String> = setOf(
        "pay",
        "transfer",
        "delete",
        "uninstall",
        "send_message",
        "send_sms",
        "factory_reset",
    )

    fun isCritical(toolName: String): Boolean = toolName in NAMES
}

enum class DataCategory {
    UI_TREE,
    SCREEN_TEXT,
    SCREEN_IMAGE,
    NOTIFICATIONS,
    CLIPBOARD,
    CONTACTS,
    CALENDAR,
}

class CapabilityGate(private val envelope: CapabilityEnvelope) {

    fun checkTool(
        toolName: String,
        riskLevel: RiskLevel,
        tool: ToolDescriptor,
        argumentsJson: String = "",
    ): Result<PolicyDecision> {
        if (toolName !in envelope.allowedTools) {
            return Result.Failure(DomainError(ErrorKind.PolicyCapabilityExceeded, "工具 $toolName 不在能力信封内"))
        }
        if (toolName == "tap_node" && SensitiveTapLabels.isSensitive(extractTapLabel(argumentsJson))) {
            return Result.Failure(
                DomainError(ErrorKind.PolicyDenied, "策略禁止点击敏感控件；请用户手动完成最终提交"),
            )
        }
        if (CriticalTools.isCritical(toolName)) {
            return Result.Success(PolicyDecision.RequireApproval)
        }
        return when (riskLevel) {
            RiskLevel.READ_ONLY -> Result.Success(PolicyDecision.Allow)
            RiskLevel.REVERSIBLE_SIDE_EFFECT -> {
                if (!envelope.allowSideEffects) Result.Success(PolicyDecision.RequireApproval)
                else Result.Success(PolicyDecision.Allow)
            }
            RiskLevel.IRREVERSIBLE_SIDE_EFFECT -> Result.Success(PolicyDecision.RequireApproval)
        }
    }

    fun checkDataUpload(category: DataCategory): Boolean =
        category in envelope.allowedDataCategories

    private fun extractTapLabel(argumentsJson: String): String? {
        val textMatch = Regex("\"text\"\\s*:\\s*\"([^\"]+)\"").find(argumentsJson)
        return textMatch?.groupValues?.getOrNull(1)
    }
}

sealed class PolicyDecision {
    object Allow : PolicyDecision()
    object RequireApproval : PolicyDecision()
    object Deny : PolicyDecision()
}
