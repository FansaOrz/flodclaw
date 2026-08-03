package com.foldledger.data.parse

import com.foldledger.domain.model.AccountType
import com.foldledger.domain.model.CaptureSource
import com.foldledger.domain.model.MoneyDirection
import com.foldledger.domain.model.ParsedPayment
import com.foldledger.domain.util.Fingerprint
import com.foldledger.domain.util.MoneyFormat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parse Chinese bank / payment SMS bodies into ledger candidates.
 */
@Singleton
class BankSmsParser @Inject constructor() {

    fun parse(address: String, body: String, receivedAt: Long): ParsedPayment? {
        val text = body.replace('\u00A0', ' ').trim()
        if (text.length < 8) return null
        if (!looksLikeBankSms(address, text)) return null

        val amount = extractAmount(text) ?: return null
        val direction = detectDirection(text) ?: return null
        val merchant = extractMerchant(text) ?: bankNameFrom(address, text)
        val fingerprint = Fingerprint.of("sms:$address", amount, merchant, receivedAt, windowSec = 300)

        return ParsedPayment(
            amountFen = amount,
            merchant = merchant,
            direction = direction,
            suggestedAccountType = AccountType.BANK,
            source = CaptureSource.BANK_SMS,
            packageName = "sms:$address",
            rawTitle = address,
            rawText = text.take(400),
            capturedAt = receivedAt,
            fingerprint = fingerprint,
        )
    }

    private fun looksLikeBankSms(address: String, text: String): Boolean {
        val addrHit = bankAddressHints.any { address.contains(it, ignoreCase = true) }
        val bodyHit = bankBodyHints.any { text.contains(it) }
        val hasMoney = extractAmount(text) != null
        return hasMoney && (addrHit || bodyHit)
    }

    private fun detectDirection(text: String): MoneyDirection? {
        val expense = listOf("支出", "消费", "支付", "扣款", "取现", "转出", "付款", "代付")
        val income = listOf("收入", "存入", "转入", "入账", "收款", "退款", "到账", "工资")
        val exp = expense.any { text.contains(it) }
        val inc = income.any { text.contains(it) }
        return when {
            exp && !inc -> MoneyDirection.EXPENSE
            inc && !exp -> MoneyDirection.INCOME
            exp -> MoneyDirection.EXPENSE
            inc -> MoneyDirection.INCOME
            else -> null
        }
    }

    private fun extractAmount(text: String): Long? {
        val patterns = listOf(
            Regex("""人民币\s*([\d,]+(?:\.\d{1,2})?)\s*元"""),
            Regex("""RMB\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:支出|收入|消费|支付|扣款|存入|转入|转出|付款)[^\d￥¥]{0,8}[￥¥]?\s*([\d,]+(?:\.\d{1,2})?)\s*元?"""),
            Regex("""[￥¥]\s*([\d,]+(?:\.\d{1,2})?)"""),
            Regex("""([\d,]+(?:\.\d{1,2})?)\s*元"""),
        )
        for (p in patterns) {
            val m = p.find(text) ?: continue
            val fen = MoneyFormat.yuanTextToFen(m.groupValues[1].replace(",", ""))
            if (fen != null && fen > 0) return fen
        }
        return null
    }

    private fun extractMerchant(text: String): String? {
        val patterns = listOf(
            Regex("""(?:商户|向|在)([\u4e00-\u9fa5A-Za-z0-9（）()·\-]{2,20})(?:完成|支付|消费|付款)"""),
            Regex("""【([^】]{2,20})】"""),
            Regex("""尾号\d{4}[^\n]{0,30}?(?:消费|支付|支出)([^\d￥¥]{2,20})"""),
        )
        for (p in patterns) {
            val m = p.find(text) ?: continue
            val name = m.groupValues[1].trim()
                .replace(Regex("""(银行|信用卡|借记卡|账户)$"""), "")
                .take(24)
            if (name.length >= 2) return name
        }
        return null
    }

    private fun bankNameFrom(address: String, text: String): String {
        bankNames.forEach { name ->
            if (text.contains(name) || address.contains(name)) return name
        }
        return address.ifBlank { "银行短信" }
    }

    companion object {
        private val bankAddressHints = listOf(
            "95588", "95533", "95599", "95566", "95559", "95555", "95558",
            "95528", "95580", "106", "bank", "BANK",
        )
        private val bankBodyHints = listOf(
            "银行", "信用卡", "借记卡", "尾号", "人民币", "账户", "支出", "收入",
            "工商银行", "建设银行", "农业银行", "中国银行", "交通银行", "招商银行",
            "邮储银行", "浦发银行", "中信银行", "光大银行", "民生银行", "兴业银行",
            "平安银行", "广发银行", "华夏银行", "北京银行", "上海银行",
        )
        private val bankNames = listOf(
            "工商银行", "建设银行", "农业银行", "中国银行", "交通银行", "招商银行",
            "邮储银行", "浦发银行", "中信银行", "光大银行", "民生银行", "兴业银行",
            "平安银行", "广发银行", "华夏银行", "北京银行", "上海银行",
        )
    }
}
