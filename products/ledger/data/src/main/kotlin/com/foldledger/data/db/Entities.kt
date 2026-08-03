package com.foldledger.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val balanceFen: Long = 0,
    val iconKey: String = "wallet",
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val direction: String,
    val iconKey: String = "tag",
    val keywordsCsv: String = "",
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    /** 用户自定义色；null 表示用名称默认色 */
    val colorArgb: Int? = null,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["deletedAt", "happenedAt"]),
        Index(value = ["categoryId"]),
        Index(value = ["direction", "happenedAt"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountFen: Long,
    val direction: String,
    val merchant: String,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val happenedAt: Long,
    val source: String,
    val rawText: String? = null,
    val fingerprint: String,
    val note: String = "",
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearMonth: String,
    val categoryId: Long? = null,
    val limitFen: Long,
    val note: String = "",
)

@Entity(
    tableName = "pending_captures",
    indices = [Index(value = ["fingerprint"], unique = true)],
)
data class PendingCaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountFen: Long? = null,
    val merchant: String? = null,
    val direction: String,
    val suggestedAccountType: String? = null,
    val source: String,
    val packageName: String,
    val rawTitle: String,
    val rawText: String,
    val fingerprint: String,
    val capturedAt: Long,
    val resolved: Boolean = false,
)
