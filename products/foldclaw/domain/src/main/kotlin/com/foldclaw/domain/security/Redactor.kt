package com.foldclaw.domain.security

/**
 * 确定性脱敏：日志、Ledger、通知文案在落盘/展示前必须经过此函数。
 * 不依赖模型，不记录原文密钥。
 */
object Redactor {

    private val patterns: List<Pair<Regex, String>> = listOf(
        Regex("""sk-[A-Za-z0-9_\-]{8,}""") to "sk-***",
        Regex("""(?i)(bearer)\s+[A-Za-z0-9\-._~+/]+=*""") to "$1 ***",
        Regex("""(?i)(api[_-]?key|dashscope[_-]?api[_-]?key)\s*[:=]\s*\S+""") to "$1=***",
        Regex("""(?i)(password|密码|pin|otp|验证码|cvv)\s*[:=]\s*\S+""") to "$1=***",
        Regex("""\b\d{13,19}\b""") to "[CARD]", // 粗粒度卡号屏蔽
    )

    fun redact(text: String?, maxLen: Int = 240): String {
        if (text.isNullOrBlank()) return ""
        var out: String = text
        for ((regex, replacement) in patterns) {
            out = regex.replace(out, replacement)
        }
        return if (out.length <= maxLen) out else out.take(maxLen) + "…"
    }

    /** 通知/UI 短摘要：只保留前缀，避免完整用户指令含隐私。 */
    fun brief(text: String?, maxLen: Int = 48): String {
        val r = redact(text, maxLen = maxLen * 2)
        return if (r.length <= maxLen) r else r.take(maxLen) + "…"
    }
}
