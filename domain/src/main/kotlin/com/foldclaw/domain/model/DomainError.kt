package com.foldclaw.domain.model

/**
 * 领域错误。所有错误都有可读 reason 和可选 metadata，便于写 Timeline 而不泄漏秘密。
 */
data class DomainError(
    val kind: ErrorKind,
    val reason: String,
    val metadata: Map<String, String> = emptyMap(),
)

enum class ErrorKind {
    // Provider 侧
    ProviderUnavailable,
    ProviderAuth,
    ProviderRateLimit,
    ProviderInvalidResponse,
    ProviderTimeout,

    // 策略与审批
    PolicyDenied,
    PolicyCapabilityExceeded,
    ApprovalRequired,
    ApprovalExpired,
    ApprovalMismatch,

    // 设备与执行
    NodeNotFound,
    ActionFailed,
    VerificationFailed,
    DeviceCapabilityMissing,

    // 状态机
    InvalidStateTransition,
    TaskCancelled,
    TaskInterrupted,
    StepLimitExceeded,
    TokenBudgetExceeded,
    CostBudgetExceeded,

    // 数据
    PersistenceFailure,
    SecretBlocked,

    // 兜底
    Unknown,
}
