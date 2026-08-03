package com.foldledger.data.repo

import com.foldledger.data.db.AccountDao
import com.foldledger.data.db.BudgetDao
import com.foldledger.data.db.CategoryDao
import com.foldledger.data.db.PendingCaptureDao
import com.foldledger.data.db.TransactionDao
import com.foldledger.data.mapper.toDomain
import com.foldledger.data.mapper.toEntity
import com.foldledger.domain.model.Account
import com.foldledger.domain.model.Budget
import com.foldledger.domain.model.BudgetProgress
import com.foldledger.domain.model.CaptureSource
import com.foldledger.domain.model.Category
import com.foldledger.domain.model.CategorySpend
import com.foldledger.domain.model.DailySpend
import com.foldledger.domain.model.DuplicatePair
import com.foldledger.domain.model.MoneyDirection
import com.foldledger.domain.model.PendingCapture
import com.foldledger.domain.model.ReclassifyCandidate
import com.foldledger.domain.model.Transaction
import com.foldledger.domain.model.TransactionWithMeta
import com.foldledger.domain.repo.AccountRepository
import com.foldledger.domain.repo.BudgetRepository
import com.foldledger.domain.repo.CategoryRepository
import com.foldledger.domain.repo.PendingCaptureRepository
import com.foldledger.domain.repo.TransactionRepository
import com.foldledger.domain.util.DuplicateMatcher
import com.foldledger.domain.util.YearMonths
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val dao: AccountDao,
    private val transactionDao: TransactionDao,
) : AccountRepository {
    override fun observeAccounts(includeArchived: Boolean): Flow<List<Account>> =
        dao.observeAll(includeArchived).map { list -> list.map { it.toDomain() } }

    override suspend fun getAccount(id: Long): Account? = dao.get(id)?.toDomain()

    override suspend fun upsert(account: Account): Long = dao.upsert(account.toEntity())

    override suspend fun archive(id: Long) = dao.archive(id)

    override suspend fun delete(id: Long): String? {
        val used = transactionDao.countActiveByAccount(id)
        if (used > 0) return "该账户还有 $used 笔流水，无法删除。请先改流水账户或删除流水。"
        dao.delete(id)
        return null
    }
}

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val pendingDao: PendingCaptureDao,
) : CategoryRepository {
    override fun observeCategories(includeArchived: Boolean): Flow<List<Category>> =
        dao.observeAll(includeArchived).map { list -> list.map { it.toDomain() } }

    override suspend fun getCategory(id: Long): Category? = dao.get(id)?.toDomain()

    override suspend fun upsert(category: Category): Long = dao.upsert(category.toEntity())

    override suspend fun archive(id: Long) = dao.archive(id)

    override suspend fun delete(id: Long) {
        transactionDao.clearCategoryId(id, System.currentTimeMillis())
        dao.delete(id)
    }

    override suspend fun matchByMerchant(merchant: String): Category? {
        if (merchant.isBlank()) return null
        return matchByMerchantCached(merchant, dao.listActive().map { it.toDomain() })
    }

    private fun matchByMerchantCached(merchant: String, categories: List<Category>): Category? {
        if (merchant.isBlank()) return null
        val haystack = merchant.lowercase()
        var best: Pair<Category, Int>? = null
        for (cat in categories) {
            for (kw in cat.keywords) {
                val key = kw.trim().lowercase()
                if (key.isEmpty() || !haystack.contains(key)) continue
                if (best == null || key.length > best.second) {
                    best = cat to key.length
                }
            }
        }
        return best?.first
    }

    override suspend fun reclassifyUncategorized(): Int {
        val generic = listOf("支付宝商户", "支付宝", "微信支付", "微信转账", "微信", "未命名商户", "未知商户")
        val genericSet = generic.toSet()
        if (transactionDao.countUncategorized() == 0) {
            // 没有未分类时，仍可能需要修正泛化商户名，但量通常很小；直接按需列表
        }
        val brands = listOf(
            "滴滴出行", "滴滴", "美团外卖", "美团打车", "美团", "饿了么",
            "淘宝", "天猫", "京东", "拼多多", "高德打车", "高德", "哈啰",
            "必胜客", "肯德基", "麦当劳", "星巴克", "瑞幸",
        )
        val categories = dao.listActive().map { it.toDomain() }
        val pending = pendingDao.listUnresolved()
        val now = System.currentTimeMillis()
        var updated = 0
        // 只扫未分类 / 泛化商户，避免每次启动全表 O(N×关键词)
        for (tx in transactionDao.listNeedingReclassify(generic)) {
            var haystack = listOf(tx.merchant, tx.rawText, tx.note).joinToString("\n")
            if (tx.categoryId == null || tx.merchant in genericSet) {
                val twin = pending.firstOrNull { p ->
                    p.amountFen != null &&
                        p.amountFen == tx.amountFen &&
                        kotlin.math.abs(p.capturedAt - tx.happenedAt) < 30 * 60 * 1000L
                }
                if (twin != null) {
                    haystack = listOf(haystack, twin.merchant, twin.rawTitle, twin.rawText)
                        .joinToString("\n")
                }
            }
            val matched = matchByMerchantCached(haystack, categories) ?: continue
            if (matched.direction.name != tx.direction) continue
            val applyCat = tx.categoryId == null
            val brand = brands.firstOrNull { haystack.contains(it) }
            val needMerchant = brand != null && tx.merchant in genericSet
            if (!applyCat && !needMerchant) continue
            transactionDao.update(
                tx.copy(
                    categoryId = if (applyCat) matched.id else tx.categoryId,
                    merchant = if (needMerchant) brand!! else tx.merchant,
                    updatedAt = now,
                ),
            )
            updated++
            if (applyCat || needMerchant) {
                pending
                    .filter { p ->
                        p.amountFen == tx.amountFen &&
                            kotlin.math.abs(p.capturedAt - tx.happenedAt) < 30 * 60 * 1000L
                    }
                    .forEach { pendingDao.markResolved(it.id) }
            }
        }
        return updated
    }

    override suspend fun findReclassifyCandidates(
        targetCategoryId: Long,
        matchKeywords: List<String>,
        excludeTxIds: Set<Long>,
    ): List<ReclassifyCandidate> {
        val target = dao.get(targetCategoryId)?.toDomain() ?: return emptyList()
        val keys = matchKeywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            .distinct()
            .sortedByDescending { it.length }
        if (keys.isEmpty()) return emptyList()

        val result = mutableListOf<ReclassifyCandidate>()
        for (row in transactionDao.listActiveWithMeta()) {
            if (row.id in excludeTxIds) continue
            if (row.categoryId == targetCategoryId) continue
            if (row.direction != target.direction.name) continue

            val haystack = listOf(row.merchant, row.rawText, row.note).joinToString("\n").lowercase()
            val matchedKw = keys.firstOrNull { haystack.contains(it) } ?: continue

            // 若其他分类有「更长」的关键词命中，则这笔不会被新词抢走，仍提示用户确认（可强制改类）
            result.add(
                ReclassifyCandidate(
                    item = row.toDomain(),
                    matchedKeyword = matchedKw,
                    targetCategoryId = target.id,
                    targetCategoryName = target.name,
                ),
            )
        }
        return result
    }
}

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao,
    private val accountDao: AccountDao,
) : TransactionRepository {
    override fun observeTransactions(includeDeleted: Boolean): Flow<List<TransactionWithMeta>> =
        (if (includeDeleted) dao.observeIncludingDeleted() else dao.observeActive())
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getWithMeta(id: Long): TransactionWithMeta? =
        dao.getWithMeta(id)?.toDomain()

    override fun observeById(id: Long): Flow<TransactionWithMeta?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getByFingerprint(fingerprint: String): Transaction? =
        dao.getByFingerprint(fingerprint)?.toDomain()

    override suspend fun upsert(tx: Transaction): Long {
        if (tx.id == 0L) {
            val existing = dao.getByFingerprint(tx.fingerprint)
            if (existing != null) {
                // 活跃重复：直接返回；软删残留：用新数据复活，否则导入会「成功」但界面看不见
                if (existing.deletedAt == null) return existing.id
                return upsert(
                    tx.copy(
                        id = existing.id,
                        deletedAt = null,
                        createdAt = existing.createdAt,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
            val id = dao.insert(tx.copy(id = 0).toEntity())
            applyBalance(tx, reverse = false)
            return id
        }
        val old = dao.getById(tx.id) ?: dao.getByFingerprint(tx.fingerprint)
        if (old != null && old.deletedAt == null) {
            applyBalance(old.toDomain(), reverse = true)
        }
        dao.update(tx.copy(deletedAt = null).toEntity())
        if (tx.deletedAt == null) {
            applyBalance(tx.copy(deletedAt = null), reverse = false)
        }
        return tx.id
    }

    private suspend fun applyBalance(tx: Transaction, reverse: Boolean) {
        val sign = if (reverse) -1 else 1
        when (tx.direction) {
            MoneyDirection.EXPENSE -> accountDao.adjustBalance(tx.accountId, -tx.amountFen * sign)
            MoneyDirection.INCOME -> accountDao.adjustBalance(tx.accountId, tx.amountFen * sign)
            MoneyDirection.TRANSFER -> {
                accountDao.adjustBalance(tx.accountId, -tx.amountFen * sign)
                tx.toAccountId?.let { accountDao.adjustBalance(it, tx.amountFen * sign) }
            }
        }
    }

    override suspend fun softDelete(id: Long) {
        val row = dao.getById(id) ?: return
        if (row.deletedAt == null) applyBalance(row.toDomain(), reverse = true)
        dao.softDelete(id, System.currentTimeMillis())
    }

    override suspend fun softDeleteBySource(source: com.foldledger.domain.model.CaptureSource): Int {
        val rows = dao.listActiveBySource(source.name)
        for (row in rows) {
            softDelete(row.id)
        }
        return rows.size
    }

    override suspend fun softDeleteLikelyBillImports(
        importSource: CaptureSource,
        orderIds: Set<String>,
        amountMerchantKeys: Set<String>,
    ): Int {
        var removed = 0
        for (row in dao.listActive()) {
            if (isLikelyBillImportRow(row, importSource, orderIds, amountMerchantKeys)) {
                softDelete(row.id)
                removed++
            }
        }
        return removed
    }

    override suspend fun hardDeleteLikelyBillImports(
        importSource: CaptureSource,
        orderIds: Set<String>,
        amountMerchantKeys: Set<String>,
    ): Int {
        var removed = 0
        for (row in dao.listAll()) {
            if (isLikelyBillImportRow(row, importSource, orderIds, amountMerchantKeys) ||
                row.source == importSource.name
            ) {
                hardDelete(row.id)
                removed++
            }
        }
        return removed
    }

    override suspend fun hardDeleteMatchingImports(
        orderIds: Set<String>,
        fingerprints: Set<String>,
    ): Int {
        val ids = orderIds.filter { it.length >= 8 }.toSet()
        var removed = 0
        for (row in dao.listAll()) {
            val raw = row.rawText.orEmpty()
            val hitOrder = ids.any { raw.contains(it) }
            val hitFp = row.fingerprint in fingerprints
            if (hitOrder || hitFp) {
                hardDelete(row.id)
                removed++
            }
        }
        return removed
    }

    private fun isLikelyBillImportRow(
        row: com.foldledger.data.db.TransactionEntity,
        importSource: CaptureSource,
        orderIds: Set<String>,
        amountMerchantKeys: Set<String>,
    ): Boolean {
        val relatedSources = when (importSource) {
            CaptureSource.WECHAT_IMPORT -> setOf(
                CaptureSource.WECHAT_IMPORT.name,
                CaptureSource.WECHAT_NLS.name,
                CaptureSource.WECHAT_A11Y.name,
                CaptureSource.PENDING.name,
            )
            CaptureSource.ALIPAY_IMPORT -> setOf(
                CaptureSource.ALIPAY_IMPORT.name,
                CaptureSource.ALIPAY_NLS.name,
                CaptureSource.ALIPAY_A11Y.name,
                CaptureSource.PENDING.name,
            )
            else -> setOf(importSource.name)
        }
        if (row.source !in relatedSources && row.source != importSource.name) return false
        val raw = row.rawText.orEmpty()
        val key = "${row.amountFen}|${row.merchant}"
        val ids = orderIds.filter { it.length >= 8 }.toSet()
        val hitSource = row.source == importSource.name
        val hitOrder = ids.any { id -> raw.contains(id) }
        val hitImportShape = looksLikeBillImportRaw(raw)
        val hitAmountMerchant = key in amountMerchantKeys && row.source in relatedSources
        return hitSource || hitOrder || hitImportShape || hitAmountMerchant
    }

    private fun looksLikeBillImportRaw(raw: String): Boolean {
        if (raw.isBlank()) return false
        val first = raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
        if (first == "账单导入" || raw.startsWith("账单导入")) return true
        if (raw.contains(" | ") && (
                raw.contains("支付成功") ||
                    raw.contains("对方已收钱") ||
                    raw.contains("已转账") ||
                    raw.contains("已收钱") ||
                    raw.contains("交易成功") ||
                    raw.contains("商户消费") ||
                    raw.contains("扫二维码")
                )
        ) {
            return true
        }
        if (raw.count { it == ',' } >= 5 &&
            (raw.contains("支出") || raw.contains("收入") || raw.contains("支付成功") || raw.contains("对方已收钱"))
        ) {
            return true
        }
        val billTypes = setOf(
            "商户消费", "扫二维码付款", "扫二维码收款", "转账", "红包", "群收款",
            "微信红包", "二维码收款", "充值", "提现",
        )
        return first in billTypes
    }

    override suspend fun restore(id: Long) {
        val row = dao.getById(id) ?: return
        dao.restore(id, System.currentTimeMillis())
        applyBalance(row.copy(deletedAt = null).toDomain(), reverse = false)
    }

    override suspend fun hardDelete(id: Long) {
        val row = dao.getById(id)
        if (row != null && row.deletedAt == null) applyBalance(row.toDomain(), reverse = true)
        dao.hardDelete(id)
    }

    override fun observeCategorySpend(startMs: Long, endMs: Long): Flow<List<CategorySpend>> =
        dao.observeCategorySpend(startMs, endMs).map { rows -> rows.map { it.toDomain() } }

    override fun observeDailySpend(startMs: Long, endMs: Long): Flow<List<DailySpend>> =
        dao.observeDailySpend(startMs, endMs).map { rows -> rows.map { it.toDomain() } }

    override fun observeExpenseCount(startMs: Long, endMs: Long): Flow<Int> =
        dao.observeExpenseCount(startMs, endMs)

    override suspend fun listExpensesInRange(startMs: Long, endMs: Long): List<TransactionWithMeta> =
        dao.listExpensesInRange(startMs, endMs).map { it.toDomain() }

    override suspend fun sumExpense(startMs: Long, endMs: Long, categoryId: Long?): Long =
        dao.sumExpense(startMs, endMs, categoryId)

    override suspend fun findDuplicatePairs(windowMs: Long): List<DuplicatePair> {
        val rows = dao.listActiveWithMeta().map { it.toDomain() }
        return DuplicateMatcher.findPairs(rows, windowMs)
    }
}

@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
) : BudgetRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeBudgets(yearMonth: String): Flow<List<BudgetProgress>> {
        val (start, end) = YearMonths.monthRange(yearMonth)
        return combine(
            budgetDao.observeByMonth(yearMonth),
            categoryDao.observeAll(false),
            transactionDao.observeActiveCount(),
        ) { budgets, categories, _ ->
            Triple(budgets, categories, start to end)
        }.mapLatest { (budgets, categories, range) ->
            budgets.map { b ->
                val spent = transactionDao.sumExpense(range.first, range.second, b.categoryId)
                val name = when (b.categoryId) {
                    null -> "总预算"
                    else -> categories.firstOrNull { it.id == b.categoryId }?.name ?: "分类"
                }
                BudgetProgress(
                    budget = b.toDomain(),
                    spentFen = spent,
                    categoryName = name,
                )
            }
        }
    }

    override suspend fun upsert(budget: Budget): Long = budgetDao.upsert(budget.toEntity())

    override suspend fun delete(id: Long) = budgetDao.delete(id)
}

@Singleton
class PendingCaptureRepositoryImpl @Inject constructor(
    private val dao: PendingCaptureDao,
) : PendingCaptureRepository {
    override fun observePending(): Flow<List<PendingCapture>> =
        dao.observePending().map { list -> list.map { it.toDomain() } }

    override suspend fun insert(pending: PendingCapture): Long = dao.insert(pending.toEntity())

    override suspend fun markResolved(id: Long) = dao.markResolved(id)

    override suspend fun markResolvedByFingerprint(fingerprint: String) =
        dao.markResolvedByFingerprint(fingerprint)

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun existsFingerprint(fingerprint: String): Boolean =
        dao.countFingerprint(fingerprint) > 0

    override suspend fun getUnresolvedByFingerprint(fingerprint: String): PendingCapture? =
        dao.getUnresolvedByFingerprint(fingerprint)?.toDomain()
}
