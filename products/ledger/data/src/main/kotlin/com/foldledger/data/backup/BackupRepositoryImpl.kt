package com.foldledger.data.backup

import com.foldledger.data.db.AccountDao
import com.foldledger.data.db.BudgetDao
import com.foldledger.data.db.CategoryDao
import com.foldledger.data.db.TransactionDao
import com.foldledger.data.mapper.toDomain
import com.foldledger.data.mapper.toEntity
import com.foldledger.domain.model.Account
import com.foldledger.domain.model.Budget
import com.foldledger.domain.model.Category
import com.foldledger.domain.model.Transaction
import com.foldledger.domain.repo.BackupRepository
import com.foldledger.domain.util.MoneyFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long,
    val accounts: List<Account>,
    val categories: List<Category>,
    val transactions: List<Transaction>,
    val budgets: List<Budget>,
)

@Serializable
data class CategoriesExportPayload(
    val version: Int = 1,
    val exportedAt: Long,
    val categories: List<Category>,
)

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
) : BackupRepository {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override suspend fun exportCsv(): String {
        val rows = transactionDao.listActive()
        val sb = StringBuilder()
        sb.appendLine("id,amount_yuan,direction,merchant,account_id,category_id,happened_at,source,note")
        rows.forEach { t ->
            sb.appendLine(
                listOf(
                    t.id,
                    MoneyFormat.fenToYuan(t.amountFen),
                    t.direction,
                    csvEscape(t.merchant),
                    t.accountId,
                    t.categoryId ?: "",
                    t.happenedAt,
                    t.source,
                    csvEscape(t.note),
                ).joinToString(","),
            )
        }
        return sb.toString()
    }

    override suspend fun exportJsonBackup(): String {
        val payload = BackupPayload(
            exportedAt = System.currentTimeMillis(),
            accounts = accountDao.listAll().map { it.toDomain() },
            categories = categoryDao.listAll().map { it.toDomain() },
            transactions = transactionDao.listActive().map { it.toDomain() },
            budgets = budgetDao.listAll().map { it.toDomain() },
        )
        return json.encodeToString(payload)
    }

    override suspend fun importJsonBackup(jsonText: String) {
        val payload = json.decodeFromString<BackupPayload>(jsonText)
        payload.accounts.forEach { accountDao.upsert(it.toEntity()) }
        payload.categories.forEach { categoryDao.upsert(it.toEntity()) }
        payload.budgets.forEach { budgetDao.upsert(it.toEntity()) }
        payload.transactions.forEach { tx ->
            if (transactionDao.getByFingerprint(tx.fingerprint) == null) {
                runCatching { transactionDao.insert(tx.copy(id = 0).toEntity()) }
            }
        }
    }

    override suspend fun exportCategoriesJson(): String {
        val payload = CategoriesExportPayload(
            exportedAt = System.currentTimeMillis(),
            categories = categoryDao.listAll().map { it.toDomain() },
        )
        return json.encodeToString(payload)
    }

    override suspend fun importCategoriesJson(jsonText: String): String {
        val payload = runCatching {
            json.decodeFromString<CategoriesExportPayload>(jsonText)
        }.getOrElse {
            // 兼容完整备份 JSON：只取 categories 段
            val full = json.decodeFromString<BackupPayload>(jsonText)
            CategoriesExportPayload(exportedAt = full.exportedAt, categories = full.categories)
        }
        if (payload.categories.isEmpty()) return "文件中没有分类数据。"

        val existing = categoryDao.listAll()
        var created = 0
        var updated = 0
        for (incoming in payload.categories) {
            val match = existing.firstOrNull {
                it.name.equals(incoming.name, ignoreCase = true) &&
                    it.direction == incoming.direction.name
            }
            if (match == null) {
                categoryDao.upsert(
                    incoming.copy(id = 0, archived = false).toEntity(),
                )
                created++
            } else {
                val oldKeywords = match.keywordsCsv.split(',', '，')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val mergedKeywords = (oldKeywords + incoming.keywords)
                    .distinctBy { it.lowercase() }
                categoryDao.upsert(
                    match.copy(
                        keywordsCsv = mergedKeywords.joinToString(","),
                        colorArgb = incoming.colorArgb ?: match.colorArgb,
                        iconKey = incoming.iconKey.ifBlank { match.iconKey },
                        sortOrder = incoming.sortOrder,
                        archived = false,
                    ),
                )
                updated++
            }
        }
        return "分类导入完成：新建 $created 个，更新 $updated 个（关键词已合并，不覆盖你本地多出来的词）。"
    }

    private fun csvEscape(value: String): String {
        val needs = value.contains(',') || value.contains('"') || value.contains('\n')
        return if (needs) "\"${value.replace("\"", "\"\"")}\"" else value
    }
}
