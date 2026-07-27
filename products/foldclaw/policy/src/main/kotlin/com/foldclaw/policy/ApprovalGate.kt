package com.foldclaw.policy

import com.foldclaw.domain.tool.RiskLevel

/**
 * 审批请求：展示给用户的可读摘要 + 绑定令牌。
 * Orchestrator 在 RequireApproval 时挂起，等待 [ApprovalGate.request] 返回。
 */
data class ApprovalRequest(
    val toolName: String,
    val humanSummary: String,
    val argumentsJson: String,
    val riskLevel: RiskLevel,
    val token: ApprovalToken,
)

data class ApprovalResponse(
    val approved: Boolean,
    /** 勾选「始终允许此类操作」时为 true。 */
    val remember: Boolean = false,
)

/**
 * 审批门。生产实现把请求推到 UI 并等待用户点允许/拒绝；
 * 测试实现可立即返回。
 */
fun interface ApprovalGate {
    suspend fun request(request: ApprovalRequest): ApprovalResponse
}

/** 测试用：立即批准/拒绝。 */
class ImmediateApprovalGate(
    private val approve: Boolean = true,
    private val remember: Boolean = false,
) : ApprovalGate {
    override suspend fun request(request: ApprovalRequest): ApprovalResponse =
        ApprovalResponse(approved = approve, remember = remember)
}
