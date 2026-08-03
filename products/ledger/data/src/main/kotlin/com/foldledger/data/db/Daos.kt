package com.foldledger.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class TxJoinRow(
    val id: Long,
    val amountFen: Long,
    val direction: String,
    val merchant: String,
    val accountId: Long,
    val toAccountId: Long?,
    val categoryId: Long?,
    val happenedAt: Long,
    val source: String,
    val rawText: String?,
    val fingerprint: String,
    val note: String,
    val deletedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val accountName: String,
    val categoryName: String?,
    val toAccountName: String?,
)

data class CategorySpendRow(
    val categoryId: Long?,
    val categoryName: String?,
    val amountFen: Long,
)

data class DailySpendRow(
    val dayEpochMs: Long,
    val amountFen: Long,
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE archived = 0 OR :includeArchived = 1 ORDER BY sortOrder, id")
    fun observeAll(includeArchived: Boolean): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE type = :type AND archived = 0 LIMIT 1")
    suspend fun getByType(type: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AccountEntity): Long

    @Query("UPDATE accounts SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE accounts SET balanceFen = balanceFen + :delta WHERE id = :id")
    suspend fun adjustBalance(id: Long, delta: Long)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Query("SELECT * FROM accounts")
    suspend fun listAll(): List<AccountEntity>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE archived = 0 OR :includeArchived = 1 ORDER BY sortOrder, id")
    fun observeAll(includeArchived: Boolean): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE archived = 0")
    suspend fun listActive(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CategoryEntity): Long

    @Query("UPDATE categories SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories")
    suspend fun listAll(): List<CategoryEntity>
}

@Dao
interface TransactionDao {
    /** 列表用：不拉 rawText，减轻内存与主线程压力。详情走 observeById。 */
    @Query(
        """
        SELECT t.id, t.amountFen, t.direction, t.merchant, t.accountId, t.toAccountId,
               t.categoryId, t.happenedAt, t.source, NULL AS rawText, t.fingerprint,
               t.note, t.deletedAt, t.createdAt, t.updatedAt,
               COALESCE(a.name, '未知账户') AS accountName,
               c.name AS categoryName,
               ta.name AS toAccountName
        FROM transactions t
        LEFT JOIN accounts a ON a.id = t.accountId
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN accounts ta ON ta.id = t.toAccountId
        WHERE t.deletedAt IS NULL
        ORDER BY t.happenedAt DESC, t.id DESC
        """,
    )
    fun observeActive(): Flow<List<TxJoinRow>>

    @Query(
        """
        SELECT t.id, t.amountFen, t.direction, t.merchant, t.accountId, t.toAccountId,
               t.categoryId, t.happenedAt, t.source, NULL AS rawText, t.fingerprint,
               t.note, t.deletedAt, t.createdAt, t.updatedAt,
               COALESCE(a.name, '未知账户') AS accountName,
               c.name AS categoryName,
               ta.name AS toAccountName
        FROM transactions t
        LEFT JOIN accounts a ON a.id = t.accountId
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN accounts ta ON ta.id = t.toAccountId
        ORDER BY t.happenedAt DESC, t.id DESC
        """,
    )
    fun observeIncludingDeleted(): Flow<List<TxJoinRow>>

    @Query(
        """
        SELECT t.*,
               COALESCE(a.name, '未知账户') AS accountName,
               c.name AS categoryName,
               ta.name AS toAccountName
        FROM transactions t
        LEFT JOIN accounts a ON a.id = t.accountId
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN accounts ta ON ta.id = t.toAccountId
        WHERE t.id = :id
        """,
    )
    fun observeById(id: Long): Flow<TxJoinRow?>

    @Query("""
        SELECT t.*,
               COALESCE(a.name, '未知账户') AS accountName,
               c.name AS categoryName,
               ta.name AS toAccountName
        FROM transactions t
        LEFT JOIN accounts a ON a.id = t.accountId
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN accounts ta ON ta.id = t.toAccountId
        WHERE t.id = :id
        """)
    suspend fun getWithMeta(id: Long): TxJoinRow?

    @Query("SELECT * FROM transactions WHERE fingerprint = :fp LIMIT 1")
    suspend fun getByFingerprint(fp: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE fingerprint = :fp AND deletedAt IS NULL LIMIT 1")
    suspend fun getActiveByFingerprint(fp: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    @Query("UPDATE transactions SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query(
        """
        SELECT categoryId, c.name AS categoryName, SUM(amountFen) AS amountFen
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.deletedAt IS NULL AND t.direction = 'EXPENSE'
          AND t.happenedAt >= :startMs AND t.happenedAt < :endMs
        GROUP BY categoryId
        ORDER BY amountFen DESC
        """,
    )
    fun observeCategorySpend(startMs: Long, endMs: Long): Flow<List<CategorySpendRow>>

    /**
     * 按东八区自然日聚合。28800000 = 8h，把 UTC 毫秒对齐到中国时区日界。
     */
    @Query(
        """
        SELECT
          ((((happenedAt + 28800000) / 86400000) * 86400000) - 28800000) AS dayEpochMs,
          SUM(amountFen) AS amountFen
        FROM transactions
        WHERE deletedAt IS NULL AND direction = 'EXPENSE'
          AND happenedAt >= :startMs AND happenedAt < :endMs
        GROUP BY dayEpochMs
        ORDER BY dayEpochMs
        """,
    )
    fun observeDailySpend(startMs: Long, endMs: Long): Flow<List<DailySpendRow>>

    @Query(
        """
        SELECT COALESCE(SUM(amountFen), 0) FROM transactions
        WHERE deletedAt IS NULL AND direction = 'EXPENSE'
          AND happenedAt >= :startMs AND happenedAt < :endMs
          AND (:categoryId IS NULL OR categoryId = :categoryId)
        """,
    )
    suspend fun sumExpense(startMs: Long, endMs: Long, categoryId: Long?): Long

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE deletedAt IS NULL AND direction = 'EXPENSE'
          AND happenedAt >= :startMs AND happenedAt < :endMs
        """,
    )
    fun observeExpenseCount(startMs: Long, endMs: Long): Flow<Int>

    @Query(
        """
        SELECT t.id, t.amountFen, t.direction, t.merchant, t.accountId, t.toAccountId,
               t.categoryId, t.happenedAt, t.source, NULL AS rawText, t.fingerprint,
               t.note, t.deletedAt, t.createdAt, t.updatedAt,
               COALESCE(a.name, '未知账户') AS accountName,
               c.name AS categoryName,
               ta.name AS toAccountName
        FROM transactions t
        LEFT JOIN accounts a ON a.id = t.accountId
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN accounts ta ON ta.id = t.toAccountId
        WHERE t.deletedAt IS NULL AND t.direction = 'EXPENSE'
          AND t.happenedAt >= :startMs AND t.happenedAt < :endMs
        ORDER BY t.happenedAt DESC, t.id DESC
        """,
    )
    suspend fun listExpensesInRange(startMs: Long, endMs: Long): List<TxJoinRow>

    /** 仅用于预算等「表有变化」的轻量失效信号，不搬运整表。 */
    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL")
    suspend fun listActive(): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL
          AND (categoryId IS NULL OR merchant IN (:genericMerchants))
        """,
    )
    suspend fun listNeedingReclassify(genericMerchants: List<String>): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL AND categoryId IS NULL")
    suspend fun countUncategorized(): Int

    @Query(
        """
        SELECT t.*,
               COALESCE(a.name, '未知账户') AS accountName,
               c.name AS categoryName,
               ta.name AS toAccountName
        FROM transactions t
        LEFT JOIN accounts a ON a.id = t.accountId
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN accounts ta ON ta.id = t.toAccountId
        WHERE t.deletedAt IS NULL
        ORDER BY t.happenedAt DESC, t.id DESC
        """,
    )
    suspend fun listActiveWithMeta(): List<TxJoinRow>

    @Query("SELECT * FROM transactions WHERE source = :source AND deletedAt IS NULL")
    suspend fun listActiveBySource(source: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions")
    suspend fun listAll(): List<TransactionEntity>

    @Query("UPDATE transactions SET categoryId = NULL, updatedAt = :now WHERE categoryId = :categoryId")
    suspend fun clearCategoryId(categoryId: Long, now: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId AND deletedAt IS NULL")
    suspend fun countActiveByAccount(accountId: Long): Int
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth ORDER BY id")
    fun observeByMonth(yearMonth: String): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BudgetEntity): Long

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM budgets")
    suspend fun listAll(): List<BudgetEntity>
}

@Dao
interface PendingCaptureDao {
    @Query("SELECT * FROM pending_captures WHERE resolved = 0 ORDER BY capturedAt DESC")
    fun observePending(): Flow<List<PendingCaptureEntity>>

    @Query("SELECT * FROM pending_captures WHERE resolved = 0 ORDER BY capturedAt DESC")
    suspend fun listUnresolved(): List<PendingCaptureEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PendingCaptureEntity): Long

    @Query("UPDATE pending_captures SET resolved = 1 WHERE id = :id")
    suspend fun markResolved(id: Long)

    @Query("UPDATE pending_captures SET resolved = 1 WHERE fingerprint = :fp AND resolved = 0")
    suspend fun markResolvedByFingerprint(fp: String)

    @Query("DELETE FROM pending_captures WHERE id = :id")
    suspend fun delete(id: Long)

    /** 仅统计未解决 pending，避免已确认指纹挡住后续流程。 */
    @Query("SELECT COUNT(*) FROM pending_captures WHERE fingerprint = :fp AND resolved = 0")
    suspend fun countFingerprint(fp: String): Int

    @Query("SELECT * FROM pending_captures WHERE fingerprint = :fp AND resolved = 0 LIMIT 1")
    suspend fun getUnresolvedByFingerprint(fp: String): PendingCaptureEntity?
}
