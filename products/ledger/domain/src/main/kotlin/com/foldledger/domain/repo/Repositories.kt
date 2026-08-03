package com.foldledger.domain.repo

import com.foldledger.domain.model.Account
import com.foldledger.domain.model.Budget
import com.foldledger.domain.model.BudgetProgress
import com.foldledger.domain.model.Category
import com.foldledger.domain.model.CategorySpend
import com.foldledger.domain.model.DailySpend
import com.foldledger.domain.model.DuplicatePair
import com.foldledger.domain.model.PendingCapture
import com.foldledger.domain.model.ReclassifyCandidate
import com.foldledger.domain.model.Transaction
import com.foldledger.domain.model.TransactionWithMeta
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeAccounts(includeArchived: Boolean = false): Flow<List<Account>>
    suspend fun getAccount(id: Long): Account?
    suspend fun upsert(account: Account): Long
    suspend fun archive(id: Long)
    /** @return null if ok, otherwise error message */
    suspend fun delete(id: Long): String?
}

interface CategoryRepository {
    fun observeCategories(includeArchived: Boolean = false): Flow<List<Category>>
    suspend fun getCategory(id: Long): Category?
    suspend fun upsert(category: Category): Long
    suspend fun archive(id: Long)
    suspend fun delete(id: Long)
    suspend fun matchByMerchant(merchant: String): Category?
    /** 用当前关键词给「未分类」流水重新归类（不覆盖已有分类）。 */
    suspend fun reclassifyUncategorized(): Int
    /**
     * 找出命中 [matchKeywords]、且当前分类不是 [targetCategoryId] 的流水，
     * 用于新增关键词后让用户逐笔确认是否改类。
     */
    suspend fun findReclassifyCandidates(
        targetCategoryId: Long,
        matchKeywords: List<String>,
        excludeTxIds: Set<Long> = emptySet(),
    ): List<ReclassifyCandidate>
}

interface TransactionRepository {
    fun observeTransactions(includeDeleted: Boolean = false): Flow<List<TransactionWithMeta>>
    fun observeById(id: Long): Flow<TransactionWithMeta?>
    suspend fun getWithMeta(id: Long): TransactionWithMeta?
    suspend fun getByFingerprint(fingerprint: String): Transaction?
    suspend fun upsert(tx: Transaction): Long
    suspend fun softDelete(id: Long)
    suspend fun softDeleteBySource(source: com.foldledger.domain.model.CaptureSource): Int
    /**
     * 清除「账单导入」残留。默认硬删除，避免软删后指纹占坑导致重导「成功但看不见」。
     */
    suspend fun softDeleteLikelyBillImports(
        importSource: com.foldledger.domain.model.CaptureSource,
        orderIds: Set<String>,
        amountMerchantKeys: Set<String> = emptySet(),
    ): Int

    suspend fun hardDeleteLikelyBillImports(
        importSource: com.foldledger.domain.model.CaptureSource,
        orderIds: Set<String>,
        amountMerchantKeys: Set<String> = emptySet(),
    ): Int

    /** 仅删除本次账单中出现的单号/指纹，保留其他时间段的历史导入 */
    suspend fun hardDeleteMatchingImports(orderIds: Set<String>, fingerprints: Set<String>): Int

    suspend fun restore(id: Long)
    suspend fun hardDelete(id: Long)
    fun observeCategorySpend(startMs: Long, endMs: Long): Flow<List<CategorySpend>>
    fun observeDailySpend(startMs: Long, endMs: Long): Flow<List<DailySpend>>
    fun observeExpenseCount(startMs: Long, endMs: Long): Flow<Int>
    suspend fun listExpensesInRange(startMs: Long, endMs: Long): List<TransactionWithMeta>
    suspend fun sumExpense(startMs: Long, endMs: Long, categoryId: Long?): Long
    /** 扫描活跃流水中的疑似重复对（同额、时间接近、商户相似）。 */
    suspend fun findDuplicatePairs(windowMs: Long = 24L * 60 * 60 * 1000): List<DuplicatePair>
}

interface BudgetRepository {
    fun observeBudgets(yearMonth: String): Flow<List<BudgetProgress>>
    suspend fun upsert(budget: Budget): Long
    suspend fun delete(id: Long)
}

interface PendingCaptureRepository {
    fun observePending(): Flow<List<PendingCapture>>
    suspend fun insert(pending: PendingCapture): Long
    suspend fun markResolved(id: Long)
    suspend fun markResolvedByFingerprint(fingerprint: String)
    suspend fun delete(id: Long)
    suspend fun existsFingerprint(fingerprint: String): Boolean
    suspend fun getUnresolvedByFingerprint(fingerprint: String): PendingCapture?
}

interface SettingsRepository {
    val autoConfirm: Flow<Boolean>
    val clearRawAfterSave: Flow<Boolean>
    val onboardingDone: Flow<Boolean>
    suspend fun setAutoConfirm(value: Boolean)
    suspend fun setClearRawAfterSave(value: Boolean)
    suspend fun setOnboardingDone(value: Boolean)
}

interface BackupRepository {
    suspend fun exportCsv(): String
    suspend fun exportJsonBackup(): String
    suspend fun importJsonBackup(json: String)
    /** 仅导出分类（含关键词/颜色），便于重装后恢复标签。 */
    suspend fun exportCategoriesJson(): String
    /** @return 导入结果文案 */
    suspend fun importCategoriesJson(json: String): String
}
