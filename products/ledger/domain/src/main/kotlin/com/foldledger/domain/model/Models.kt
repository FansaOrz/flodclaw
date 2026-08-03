package com.foldledger.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class MoneyDirection {
    EXPENSE,
    INCOME,
    TRANSFER,
}

@Serializable
enum class AccountType {
    CASH,
    BANK,
    ALIPAY,
    WECHAT,
    CUSTOM,
}

@Serializable
enum class CaptureSource {
    MANUAL,
    WECHAT_NLS,
    WECHAT_A11Y,
    ALIPAY_NLS,
    ALIPAY_A11Y,
    BANK_SMS,
    WECHAT_IMPORT,
    ALIPAY_IMPORT,
    PENDING,
}

@Serializable
data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val balanceFen: Long = 0,
    val iconKey: String = "wallet",
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

@Serializable
data class Category(
    val id: Long = 0,
    val name: String,
    val direction: MoneyDirection,
    val iconKey: String = "tag",
    val keywords: List<String> = emptyList(),
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    /** 用户自定义 ARGB；null 表示用名称默认色 */
    val colorArgb: Int? = null,
)

@Serializable
data class Transaction(
    val id: Long = 0,
    val amountFen: Long,
    val direction: MoneyDirection,
    val merchant: String,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long?,
    val happenedAt: Long,
    val source: CaptureSource,
    val rawText: String? = null,
    val fingerprint: String,
    val note: String = "",
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Budget(
    val id: Long = 0,
    val yearMonth: String,
    val categoryId: Long?,
    val limitFen: Long,
    val note: String = "",
)

data class PendingCapture(
    val id: Long = 0,
    val amountFen: Long?,
    val merchant: String?,
    val direction: MoneyDirection = MoneyDirection.EXPENSE,
    val suggestedAccountType: AccountType?,
    val source: CaptureSource,
    val packageName: String,
    val rawTitle: String,
    val rawText: String,
    val fingerprint: String,
    val capturedAt: Long,
    val resolved: Boolean = false,
)

data class TransactionWithMeta(
    val transaction: Transaction,
    val accountName: String,
    val categoryName: String?,
    val toAccountName: String? = null,
)

data class CategorySpend(
    val categoryId: Long?,
    val categoryName: String,
    val amountFen: Long,
)

data class DailySpend(
    val dayEpochMs: Long,
    val amountFen: Long,
)

data class BudgetProgress(
    val budget: Budget,
    val spentFen: Long,
    val categoryName: String,
)

/** 新增关键词后，待用户确认是否改类的流水。 */
data class ReclassifyCandidate(
    val item: TransactionWithMeta,
    val matchedKeyword: String,
    val targetCategoryId: Long,
    val targetCategoryName: String,
)

/** 全局重复筛查中的一对疑似重复流水。 */
data class DuplicatePair(
    val first: TransactionWithMeta,
    val second: TransactionWithMeta,
    val reason: String,
)
