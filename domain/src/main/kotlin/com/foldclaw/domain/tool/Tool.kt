package com.foldclaw.domain.tool

import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ObservationSnapshot
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.model.ToolDescriptor

/**
 * 工具执行上下文。工具拿到当前观察快照、能力信封和包名白名单，但不直接访问 PolicyEngine。
 */
data class ToolContext(
    val taskId: String,
    val stepIndex: Int,
    val snapshot: ObservationSnapshot?,
    val allowedPackages: Set<String>,
    val allowedDomains: Set<String>,
)

/**
 * Agent 可调用的工具。低级工具（tap/type_text 等）实现 DeviceActionTool；
 * 高层工具（建日程等）直接实现本接口。策略层据此区分语义风险。
 *
 * 关键：工具返回 [ToolOutcome]，由 Verifier 判定是否真的成功；
 * 工具自己不声称「已执行」。
 */
interface Tool {
    val descriptor: ToolDescriptor
    val riskLevel: RiskLevel

    /**
     * 执行工具。[argumentsJson] 已经过 schema 校验。
     */
    suspend fun execute(ctx: ToolContext, argumentsJson: String): Result<ToolOutcome>
}

enum class RiskLevel {
    /** 只读：读 UI 树、读通知摘要。 */
    READ_ONLY,

    /** 可逆副作用：填草稿、导航、滚动。不产生最终提交。 */
    REVERSIBLE_SIDE_EFFECT,

    /** 不可逆副作用：发送、删除、支付、购买。MVP 禁止由低级工具直接完成。 */
    IRREVERSIBLE_SIDE_EFFECT,
}

/**
 * 工具执行结果。
 */
sealed class ToolOutcome {
    /** 文本结果，回传给模型作为 tool 消息。 */
    data class Text(val text: String) : ToolOutcome()

    /**
     * 产生了副作用，附带可验证的预期状态变化。
     *
     * @param expectedPackageNames 期望前台包名集合；空集合表示不做包名校验。
     * @param launchedByIntent true 表示已通过 startActivity 成功拉起系统 UI。
     *   此时若 Accessibility 未连接或前台包尚未切换，不应把整次任务判失败。
     */
    data class SideEffect(
        val summary: String,
        val expectedPackageNames: Set<String> = emptySet(),
        val expectedText: String? = null,
        val irreversible: Boolean,
        val launchedByIntent: Boolean = false,
    ) : ToolOutcome() {
        /** 兼容旧单包名写法。 */
        constructor(
            summary: String,
            expectedPackageName: String?,
            expectedText: String?,
            irreversible: Boolean,
        ) : this(
            summary = summary,
            expectedPackageNames = expectedPackageName?.let { setOf(it) } ?: emptySet(),
            expectedText = expectedText,
            irreversible = irreversible,
            launchedByIntent = false,
        )
    }

    /** 需要用户接管（登录/验证码/安全窗口）。 */
    data class NeedsUserHandoff(val reason: String) : ToolOutcome()

    /** 失败。 */
    data class Failure(val error: DomainError) : ToolOutcome()
}
