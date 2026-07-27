package com.foldclaw.policy

import kotlinx.serialization.Serializable

/**
 * 审批令牌。防 TOCTOU：令牌绑定目标包名、参数摘要、窗口、过期时间、一次性 nonce。
 *
 * 审查报告 §4.3：执行前重读状态；页面/参数变化则失效。
 * 审查报告 §5：审批期间禁止 Accessibility 操作本应用审批窗、系统认证界面。
 */
@Serializable
data class ApprovalToken(
    val token: String,
    val toolName: String,
    val targetPackage: String?,
    val argumentsDigest: String, // SHA-256 摘要，不是明文
    val windowTitle: String?,
    val displayId: Int,
    val issuedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val nonce: String,
    var consumed: Boolean = false,
) {
    fun isExpired(nowEpochMs: Long): Boolean = nowEpochMs > expiresAtEpochMs
}

class ApprovalManager(
    private val ttlMs: Long = 60_000L,
) {

    /**
     * 生成审批令牌。参数以摘要形式绑定，不存明文。
     */
    fun issue(
        toolName: String,
        targetPackage: String?,
        argumentsJson: String,
        windowTitle: String?,
        displayId: Int,
        nowEpochMs: Long,
    ): ApprovalToken {
        val digest = sha256Hex(argumentsJson)
        return ApprovalToken(
            token = "appr_${nowEpochMs}_${(argumentsJson.length * 31).toString(16)}",
            toolName = toolName,
            targetPackage = targetPackage,
            argumentsDigest = digest,
            windowTitle = windowTitle,
            displayId = displayId,
            issuedAtEpochMs = nowEpochMs,
            expiresAtEpochMs = nowEpochMs + ttlMs,
            nonce = digest.take(16),
        )
    }

    /**
     * 校验令牌是否仍有效，且参数/窗口与签发时一致。
     * 消费后置 consumed=true，一次性。
     */
    fun validate(
        token: ApprovalToken,
        currentArgumentsJson: String,
        currentPackage: String?,
        currentWindowTitle: String?,
        currentDisplayId: Int,
        nowEpochMs: Long,
    ): Boolean {
        if (token.consumed) return false
        if (token.isExpired(nowEpochMs)) return false
        if (sha256Hex(currentArgumentsJson) != token.argumentsDigest) return false
        // 仅校验签发时已知的绑定字段：null 表示签发时无观察，不因后续包名出现而误杀（Intent 路径常见）
        if (token.targetPackage != null && currentPackage != token.targetPackage) return false
        if (token.windowTitle != null && currentWindowTitle != token.windowTitle) return false
        if (currentDisplayId != token.displayId) return false
        token.consumed = true
        return true
    }

    private fun sha256Hex(input: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
