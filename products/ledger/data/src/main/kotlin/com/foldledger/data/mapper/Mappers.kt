package com.foldledger.data.mapper

import com.foldledger.data.db.AccountEntity
import com.foldledger.data.db.BudgetEntity
import com.foldledger.data.db.CategoryEntity
import com.foldledger.data.db.CategorySpendRow
import com.foldledger.data.db.DailySpendRow
import com.foldledger.data.db.PendingCaptureEntity
import com.foldledger.data.db.TransactionEntity
import com.foldledger.data.db.TxJoinRow
import com.foldledger.domain.model.Account
import com.foldledger.domain.model.AccountType
import com.foldledger.domain.model.Budget
import com.foldledger.domain.model.CaptureSource
import com.foldledger.domain.model.Category
import com.foldledger.domain.model.CategorySpend
import com.foldledger.domain.model.DailySpend
import com.foldledger.domain.model.MoneyDirection
import com.foldledger.domain.model.PendingCapture
import com.foldledger.domain.model.Transaction
import com.foldledger.domain.model.TransactionWithMeta

fun AccountEntity.toDomain() = Account(
    id = id,
    name = name,
    type = AccountType.valueOf(type),
    balanceFen = balanceFen,
    iconKey = iconKey,
    sortOrder = sortOrder,
    archived = archived,
)

fun Account.toEntity() = AccountEntity(
    id = id,
    name = name,
    type = type.name,
    balanceFen = balanceFen,
    iconKey = iconKey,
    sortOrder = sortOrder,
    archived = archived,
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    direction = MoneyDirection.valueOf(direction),
    iconKey = iconKey,
    keywords = keywordsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    sortOrder = sortOrder,
    archived = archived,
    colorArgb = colorArgb,
)

fun Category.toEntity() = CategoryEntity(
    id = id,
    name = name,
    direction = direction.name,
    iconKey = iconKey,
    keywordsCsv = keywords.joinToString(","),
    sortOrder = sortOrder,
    archived = archived,
    colorArgb = colorArgb,
)

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    amountFen = amountFen,
    direction = MoneyDirection.valueOf(direction),
    merchant = merchant,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    happenedAt = happenedAt,
    source = CaptureSource.valueOf(source),
    rawText = rawText,
    fingerprint = fingerprint,
    note = note,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    amountFen = amountFen,
    direction = direction.name,
    merchant = merchant,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    happenedAt = happenedAt,
    source = source.name,
    rawText = rawText,
    fingerprint = fingerprint,
    note = note,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun TxJoinRow.toDomain() = TransactionWithMeta(
    transaction = Transaction(
        id = id,
        amountFen = amountFen,
        direction = MoneyDirection.valueOf(direction),
        merchant = merchant,
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        happenedAt = happenedAt,
        source = CaptureSource.valueOf(source),
        rawText = rawText,
        fingerprint = fingerprint,
        note = note,
        deletedAt = deletedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    ),
    accountName = accountName,
    categoryName = categoryName,
    toAccountName = toAccountName,
)

fun BudgetEntity.toDomain() = Budget(
    id = id,
    yearMonth = yearMonth,
    categoryId = categoryId,
    limitFen = limitFen,
    note = note,
)

fun Budget.toEntity() = BudgetEntity(
    id = id,
    yearMonth = yearMonth,
    categoryId = categoryId,
    limitFen = limitFen,
    note = note,
)

fun PendingCaptureEntity.toDomain() = PendingCapture(
    id = id,
    amountFen = amountFen,
    merchant = merchant,
    direction = MoneyDirection.valueOf(direction),
    suggestedAccountType = suggestedAccountType?.let { AccountType.valueOf(it) },
    source = CaptureSource.valueOf(source),
    packageName = packageName,
    rawTitle = rawTitle,
    rawText = rawText,
    fingerprint = fingerprint,
    capturedAt = capturedAt,
    resolved = resolved,
)

fun PendingCapture.toEntity() = PendingCaptureEntity(
    id = id,
    amountFen = amountFen,
    merchant = merchant,
    direction = direction.name,
    suggestedAccountType = suggestedAccountType?.name,
    source = source.name,
    packageName = packageName,
    rawTitle = rawTitle,
    rawText = rawText,
    fingerprint = fingerprint,
    capturedAt = capturedAt,
    resolved = resolved,
)

fun CategorySpendRow.toDomain() = CategorySpend(
    categoryId = categoryId,
    categoryName = categoryName ?: "未分类",
    amountFen = amountFen,
)

fun DailySpendRow.toDomain() = DailySpend(
    dayEpochMs = dayEpochMs,
    amountFen = amountFen,
)
