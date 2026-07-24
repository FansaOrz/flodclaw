package com.foldclaw.policy

import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.model.ToolDescriptor
import com.foldclaw.domain.tool.RiskLevel

/**
 * 确定性策略引擎。完全独立于模型。
 *
 * 审查报告 §5：模型不能修改策略；低级 tap/type_text 不得直接完成发送/删除/支付；
 * 未知页面默认禁止副作用。
 */
class PolicyEngine(
    private val envelope: CapabilityEnvelope,
    private val gate: CapabilityGate = CapabilityGate(envelope),
) {

    /**
     * 对一次工具调用做策略校验。返回 Allow / RequireApproval / Deny。
     */
    fun evaluate(
        toolName: String,
        riskLevel: RiskLevel,
        tool: ToolDescriptor,
        argumentsJson: String = "",
    ): Result<PolicyDecision> = gate.checkTool(toolName, riskLevel, tool, argumentsJson)

    /**
     * 校验工具参数中的包名/域名是否在信封内。
     * 用于 Intent 工具：不允许模型把请求指向白名单外的 App。
     */
    fun checkTargetPackage(pkg: String?): Result<Unit> {
        if (pkg == null) return Result.Success(Unit)
        if (pkg !in envelope.allowedPackages && envelope.allowedPackages.isNotEmpty()) {
            return Result.Failure(DomainError(ErrorKind.PolicyDenied, "目标包名 $pkg 不在白名单"))
        }
        return Result.Success(Unit)
    }

    /**
     * 秘密数据永久禁止：密码、OTP、PIN、CVV、API Key 等。
     * 这是一票否决，不进入信封协商。
     */
    fun isSecretBlocked(text: String?): Boolean {
        if (text == null) return false
        val lower = text.lowercase()
        return SECRET_PATTERNS.any { lower.contains(it) }
    }

    companion object {
        private val SECRET_PATTERNS = listOf(
            "otp", "验证码", "verification code",
            "password", "密码", "pin",
            "cvv", "cv2",
            "api key", "sk-",
            "助记词", "mnemonic", "recovery phrase",
        )
    }
}
