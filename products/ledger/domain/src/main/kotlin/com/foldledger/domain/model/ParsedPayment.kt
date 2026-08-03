package com.foldledger.domain.model

/**
 * Parsed payment candidate before confirm / persist.
 */
data class ParsedPayment(
    val amountFen: Long,
    val merchant: String,
    val direction: MoneyDirection = MoneyDirection.EXPENSE,
    val suggestedAccountType: AccountType,
    val source: CaptureSource,
    val packageName: String,
    val rawTitle: String,
    val rawText: String,
    val capturedAt: Long,
    val fingerprint: String,
    /** 微信/支付宝交易单号，用于重新导入时覆盖旧记录 */
    val externalId: String? = null,
)
