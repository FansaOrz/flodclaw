package com.foldledger.data.parse

import com.foldledger.domain.model.AccountType
import com.foldledger.domain.model.CaptureSource
import com.foldledger.domain.model.MoneyDirection
import com.foldledger.domain.model.ParsedPayment
import com.foldledger.domain.util.Fingerprint
import com.foldledger.domain.util.MoneyFormat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentNotificationParser @Inject constructor() {

    fun parse(
        packageName: String,
        title: String,
        text: String,
        postTime: Long,
    ): ParsedPayment? {
        return when (packageName) {
            PKG_WECHAT -> parseWechat(title, text, postTime)
            PKG_ALIPAY -> parseAlipay(title, text, postTime)
            else -> null
        }
    }

    fun parseAlipayUiText(screenText: String, capturedAt: Long): ParsedPayment? {
        if (!screenText.contains("支付成功") &&
            !screenText.contains("付款成功") &&
            !screenText.contains("交易成功") &&
            !screenText.contains("完成")
        ) {
            // still allow if clear amount + 支付宝付款 cues
            if (!screenText.contains("付款") && !screenText.contains("支付")) return null
        }
        val amount = extractAmount(screenText) ?: return null
        val merchant = extractAlipayMerchant(screenText) ?: "支付宝商户"
        val fingerprint = Fingerprint.of(PKG_ALIPAY, amount, merchant, capturedAt)
        return ParsedPayment(
            amountFen = amount,
            merchant = merchant,
            direction = MoneyDirection.EXPENSE,
            suggestedAccountType = AccountType.ALIPAY,
            source = CaptureSource.ALIPAY_A11Y,
            packageName = PKG_ALIPAY,
            rawTitle = "支付成功",
            rawText = screenText.take(500),
            capturedAt = capturedAt,
            fingerprint = fingerprint,
        )
    }

    /**
     * WeChat in-app transfer / payment success screen (no system notification required).
     * Intentionally loose: WeChat often only exposes partial text via a11y.
     */
    fun parseWechatUiText(screenText: String, capturedAt: Long): ParsedPayment? {
        val successHints = listOf(
            "转账成功", "已转账", "支付成功", "付款成功", "待对方确认收款",
            "待确认收款", "零钱支付成功", "支付完成", "转账", "付款给",
            "已支付", "支付", "零钱", "元",
        )
        // Must look like a money result screen: has amount AND at least one money-ish hint
        // that is stronger than just "元" alone when possible.
        val strongHints = listOf(
            "转账成功", "已转账", "支付成功", "付款成功", "待对方确认收款",
            "待确认收款", "零钱支付成功", "支付完成", "转账", "付款", "已支付",
        )
        val hasStrong = strongHints.any { screenText.contains(it) }
        val amount = extractAmount(screenText) ?: return null
        if (!hasStrong && !screenText.contains("微信支付")) return null
        if (!hasStrong && successHints.none { screenText.contains(it) }) return null

        val merchant = extractWechatMerchant("", screenText) ?: "微信转账"
        val direction = when {
            screenText.contains("向你转账") ||
                screenText.contains("给你转账") ||
                screenText.contains("已收款") ||
                screenText.contains("收款成功") -> MoneyDirection.INCOME
            else -> MoneyDirection.EXPENSE
        }
        return ParsedPayment(
            amountFen = amount,
            merchant = merchant,
            direction = direction,
            suggestedAccountType = AccountType.WECHAT,
            source = CaptureSource.WECHAT_A11Y,
            packageName = PKG_WECHAT,
            rawTitle = "微信页面",
            rawText = screenText.take(500),
            capturedAt = capturedAt,
            fingerprint = Fingerprint.of(PKG_WECHAT, amount, merchant, capturedAt),
        )
    }

    private fun parseWechat(title: String, text: String, postTime: Long): ParsedPayment? {
        val combined = "$title $text"
        if (!looksLikePayment(combined, wechatHints)) return null
        val amount = extractAmount(combined) ?: return null
        val merchant = extractWechatMerchant(title, text) ?: "微信支付"
        val direction = when {
            // 别人转给你 / 收款
            combined.contains("向你转账") ||
                combined.contains("给你转账") ||
                combined.contains("转账给你") ||
                combined.contains("已收款") ||
                combined.contains("收款到账") ||
                combined.contains("收到转账") ||
                combined.contains("收款") ||
                combined.contains("入账") -> MoneyDirection.INCOME
            // 你转给别人 / 付款
            else -> MoneyDirection.EXPENSE
        }
        return ParsedPayment(
            amountFen = amount,
            merchant = merchant,
            direction = direction,
            suggestedAccountType = AccountType.WECHAT,
            source = CaptureSource.WECHAT_NLS,
            packageName = PKG_WECHAT,
            rawTitle = title,
            rawText = text,
            capturedAt = postTime,
            fingerprint = Fingerprint.of(PKG_WECHAT, amount, merchant, postTime),
        )
    }

    private fun parseAlipay(title: String, text: String, postTime: Long): ParsedPayment? {
        val combined = "$title $text"
        if (!looksLikePayment(combined, alipayHints)) return null
        val amount = extractAmount(combined) ?: return null
        val merchant = extractAlipayMerchant(combined) ?: title.ifBlank { "支付宝" }
        val direction = if (combined.contains("收款") || combined.contains("入账") || combined.contains("退款")) {
            MoneyDirection.INCOME
        } else {
            MoneyDirection.EXPENSE
        }
        return ParsedPayment(
            amountFen = amount,
            merchant = merchant,
            direction = direction,
            suggestedAccountType = AccountType.ALIPAY,
            source = CaptureSource.ALIPAY_NLS,
            packageName = PKG_ALIPAY,
            rawTitle = title,
            rawText = text,
            capturedAt = postTime,
            fingerprint = Fingerprint.of(PKG_ALIPAY, amount, merchant, postTime),
        )
    }

    private fun looksLikePayment(text: String, hints: List<String>): Boolean {
        if (extractAmount(text) == null) return false
        return hints.any { text.contains(it) }
    }

    private fun extractAmount(text: String): Long? {
        val patterns = listOf(
            Regex("""[￥¥]\s*(\d+(?:\.\d{1,2})?)"""),
            Regex("""(\d+(?:\.\d{1,2})?)\s*元"""),
            Regex("""金额[：:]\s*(\d+(?:\.\d{1,2})?)"""),
            Regex("""支付了?\s*(\d+(?:\.\d{1,2})?)"""),
            Regex("""付款\s*(\d+(?:\.\d{1,2})?)"""),
            Regex("""转账\s*[￥¥]?\s*(\d+(?:\.\d{1,2})?)"""),
            Regex("""已转账\s*[￥¥]?\s*(\d+(?:\.\d{1,2})?)"""),
        )
        for (p in patterns) {
            val m = p.find(text) ?: continue
            val fen = MoneyFormat.yuanTextToFen(m.groupValues[1])
            if (fen != null && fen > 0) return fen
        }
        return null
    }

    private fun extractWechatMerchant(title: String, text: String): String? {
        val patterns = listOf(
            Regex("""向(.+?)转账"""),
            Regex("""转账给(.+?)[\s，,￥¥\d]"""),
            Regex("""你已向(.+?)转账"""),
            Regex("""(.+?)向你转账"""),
            Regex("""(.+?)给你转账"""),
            Regex("""待对方确认收款\s*(.+?)\s*[￥¥]"""),
            Regex("""收款方[：:]\s*(.+)"""),
            Regex("""转账给\s*(.+)"""),
            Regex("""向(.+?)付款"""),
            Regex("""支付给(.+?)[\s，,]"""),
            Regex("""商户[：:]?\s*(.+)"""),
            Regex("""已支付.*?给(.+)"""),
        )
        val combined = "$title $text"
        for (p in patterns) {
            val m = p.find(combined) ?: continue
            val name = m.groupValues[1].trim()
                .replace(Regex("""[￥¥].*"""), "")
                .take(40)
            if (name.isNotBlank() && name !in listOf("你", "微信", "微信支付")) return name
        }
        return title.takeIf {
            it.isNotBlank() &&
                !it.contains("微信支付") &&
                !it.contains("微信") &&
                !it.contains("转账")
        }
    }

    private fun extractAlipayMerchant(text: String): String? {
        val known = listOf(
            "滴滴出行", "滴滴", "美团外卖", "美团", "饿了么", "淘宝", "天猫",
            "京东", "拼多多", "盒马", "瑞幸", "星巴克", "高德打车", "哈啰",
        )
        known.forEach { brand ->
            if (text.contains(brand)) return brand
        }
        val patterns = listOf(
            Regex("""付给(.+?)[\s，,\d￥¥]"""),
            Regex("""向(.+?)付款"""),
            Regex("""向(.+?)支付"""),
            Regex("""交易对象[：:]\s*(.+)"""),
            Regex("""商户[：:]?\s*(.+)"""),
            Regex("""成功支付给(.+)"""),
            Regex("""付款给(.+?)[\s，,\d￥¥]"""),
        )
        for (p in patterns) {
            val m = p.find(text) ?: continue
            val name = m.groupValues[1].trim()
                .replace(Regex("""[￥¥]\d.*"""), "")
                .take(40)
            if (name.isNotBlank()) return name
        }
        return null
    }

    companion object {
        const val PKG_WECHAT = "com.tencent.mm"
        const val PKG_ALIPAY = "com.eg.android.AlipayGphone"

        private val wechatHints = listOf(
            "支付成功", "付款成功", "已支付", "微信支付", "付款给", "向你付款", "收款",
            // 转账场景（发出 / 收到）
            "转账", "已转账", "转账成功", "向你转账", "给你转账", "转账给你",
            "已收款", "收款到账", "收到转账",
        )
        private val alipayHints = listOf(
            "支付成功", "付款成功", "已支付", "交易成功", "收款", "退款", "花呗",
            "付款给", "成功付款", "支出", "消费", "转账成功", "已转出",
        )
    }
}
