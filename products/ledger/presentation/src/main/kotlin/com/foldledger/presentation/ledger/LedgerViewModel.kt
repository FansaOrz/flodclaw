package com.foldledger.presentation.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldledger.capture.pipeline.CapturePipeline
import com.foldledger.domain.model.Account
import com.foldledger.domain.model.CaptureSource
import com.foldledger.domain.model.Category
import com.foldledger.domain.model.MoneyDirection
import com.foldledger.domain.model.PendingCapture
import com.foldledger.domain.model.ReclassifyCandidate
import com.foldledger.domain.model.Transaction
import com.foldledger.domain.model.TransactionWithMeta
import com.foldledger.domain.repo.AccountRepository
import com.foldledger.domain.repo.CategoryRepository
import com.foldledger.domain.repo.PendingCaptureRepository
import com.foldledger.domain.repo.TransactionRepository
import com.foldledger.domain.util.Fingerprint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** null = 全部；[UNCATEGORIZED_FILTER] = 未分类；其它 = 分类 id */
const val UNCATEGORIZED_FILTER = -1L

data class LedgerUiState(
    val transactions: List<TransactionWithMeta> = emptyList(),
    val filteredTransactions: List<TransactionWithMeta> = emptyList(),
    val pending: List<PendingCapture> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedId: Long? = null,
    val showEditor: Boolean = false,
    val searchQuery: String = "",
    val categoryFilterId: Long? = null,
)

@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val pendingRepo: PendingCaptureRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val pipeline: CapturePipeline,
) : ViewModel() {

    private val selectedId = MutableStateFlow<Long?>(null)
    private val showEditor = MutableStateFlow(false)
    private val searchQuery = MutableStateFlow("")
    private val categoryFilterId = MutableStateFlow<Long?>(null)

    private val base = combine(
        transactions.observeTransactions(false),
        pendingRepo.observePending(),
        accounts.observeAccounts(),
        categories.observeCategories(),
    ) { tx, pending, acc, cats ->
        LedgerUiState(transactions = tx, pending = pending, accounts = acc, categories = cats)
    }

    @OptIn(FlowPreview::class)
    val uiState: StateFlow<LedgerUiState> = combine(
        combine(base, selectedId, showEditor, categoryFilterId) { state, sel, editor, catFilter ->
            state.copy(
                selectedId = sel,
                showEditor = editor,
                categoryFilterId = catFilter,
            )
        },
        searchQuery,
        searchQuery.debounce(200).distinctUntilChanged(),
    ) { state, query, debouncedQuery ->
        state.copy(
            searchQuery = query,
            filteredTransactions = filterTransactions(
                state.transactions,
                debouncedQuery,
                state.categoryFilterId,
            ),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerUiState())

    /** 详情用：按 id 拉完整账单（含 rawText），列表为性能不含原文。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedDetail: StateFlow<TransactionWithMeta?> = selectedId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else transactions.observeById(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun loadDetail(id: Long): TransactionWithMeta? = transactions.getWithMeta(id)

    fun select(id: Long?) {
        selectedId.value = id
    }

    fun openEditor(open: Boolean) {
        showEditor.value = open
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setCategoryFilter(id: Long?) {
        categoryFilterId.value = id
    }

    fun saveManual(
        amountYuan: String,
        merchant: String,
        direction: MoneyDirection,
        accountId: Long,
        categoryId: Long?,
        toAccountId: Long?,
        note: String,
        editId: Long?,
    ) {
        viewModelScope.launch {
            val fen = parseYuanToFen(amountYuan) ?: return@launch
            val now = System.currentTimeMillis()
            val fp = if (editId != null) {
                "manual-edit-$editId-$now"
            } else {
                Fingerprint.of("manual", fen, merchant, now, 1)
            }
            val tx = Transaction(
                id = editId ?: 0,
                amountFen = fen,
                direction = direction,
                merchant = merchant.ifBlank { "手动记账" },
                accountId = accountId,
                toAccountId = toAccountId,
                categoryId = categoryId,
                happenedAt = now,
                source = CaptureSource.MANUAL,
                fingerprint = fp,
                note = note,
            )
            transactions.upsert(tx)
            showEditor.value = false
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { transactions.softDelete(id) }
    }

    fun restore(id: Long) {
        viewModelScope.launch { transactions.restore(id) }
    }

    fun updateCategory(txId: Long, categoryId: Long?) {
        viewModelScope.launch {
            val current = uiState.value.transactions
                .firstOrNull { it.transaction.id == txId }
                ?.transaction
                ?: return@launch
            if (current.categoryId == categoryId) return@launch
            transactions.upsert(
                current.copy(
                    categoryId = categoryId,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * 改分类，并可把勾选的关键词写入该类。
     * 扫描时会用：本次勾选词 + 目标分类里已能命中本笔账单的词，
     * 这样「伊莫健康」已在「工作」里时，再改同类账单仍会弹出其它待确认笔。
     */
    suspend fun assignCategoryWithKeywords(
        txId: Long,
        categoryId: Long,
        keywords: List<String>,
    ): List<ReclassifyCandidate> {
        val state = uiState.value
        val current = transactions.getWithMeta(txId)?.transaction
            ?: state.transactions.firstOrNull { it.transaction.id == txId }?.transaction
            ?: return emptyList()
        transactions.upsert(
            current.copy(
                categoryId = categoryId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        val cat = categories.getCategory(categoryId) ?: return emptyList()
        val toAdd = keywords.map { it.trim() }.filter { it.length >= 2 }.distinctBy { it.lowercase() }
        val occupiedElsewhere = state.categories
            .filter { it.id != categoryId }
            .flatMap { it.keywords }
            .map { it.trim().lowercase() }
            .toSet()
        val existing = cat.keywords.map { it.trim().lowercase() }.toSet()
        val safe = toAdd.filter {
            val key = it.lowercase()
            key !in occupiedElsewhere && key !in existing
        }
        val updatedCat = if (safe.isNotEmpty()) {
            val next = cat.copy(keywords = cat.keywords + safe)
            categories.upsert(next)
            next
        } else {
            cat
        }
        val haystack = listOf(current.merchant, current.rawText, current.note)
            .joinToString("\n")
            .lowercase()
        val fromCategory = updatedCat.keywords.filter { kw ->
            val key = kw.trim().lowercase()
            key.length >= 2 && haystack.contains(key)
        }
        val scanKeys = (toAdd + fromCategory).distinctBy { it.lowercase() }
        if (scanKeys.isEmpty()) return emptyList()
        return categories.findReclassifyCandidates(
            targetCategoryId = categoryId,
            matchKeywords = scanKeys,
            excludeTxIds = setOf(txId),
        )
    }

    fun applyReclassify(candidate: ReclassifyCandidate) {
        viewModelScope.launch {
            transactions.upsert(
                candidate.item.transaction.copy(
                    categoryId = candidate.targetCategoryId,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun applyReclassifyAll(candidates: List<ReclassifyCandidate>) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            candidates.forEach { c ->
                transactions.upsert(
                    c.item.transaction.copy(
                        categoryId = c.targetCategoryId,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    fun confirmPending(p: PendingCapture, accountId: Long, categoryId: Long?) {
        viewModelScope.launch {
            val amount = p.amountFen ?: return@launch
            val source = when {
                p.source == CaptureSource.WECHAT_IMPORT ||
                    p.source == CaptureSource.ALIPAY_IMPORT ||
                    p.source == CaptureSource.BANK_SMS -> p.source
                p.packageName.contains("tencent") -> CaptureSource.WECHAT_NLS
                p.packageName.contains("eg.android.Alipay") -> CaptureSource.ALIPAY_NLS
                else -> p.source
            }
            pipeline.confirmPending(
                pendingId = p.id,
                amountFen = amount,
                merchant = p.merchant ?: "未知商户",
                direction = p.direction,
                accountId = accountId,
                categoryId = categoryId,
                sourcePkg = p.packageName,
                source = source,
                rawText = "${p.rawTitle}\n${p.rawText}",
                capturedAt = p.capturedAt,
                fingerprint = p.fingerprint,
            )
        }
    }

    fun confirmPendingEdited(
        pending: PendingCapture,
        amountYuan: String,
        merchant: String,
        direction: MoneyDirection,
        accountId: Long,
        categoryId: Long?,
        note: String,
    ) {
        viewModelScope.launch {
            val amountFen = parseYuanToFen(amountYuan) ?: return@launch
            pipeline.confirmPending(
                pendingId = pending.id,
                amountFen = amountFen,
                merchant = merchant.trim().ifBlank { "未知商户" },
                direction = direction,
                accountId = accountId,
                categoryId = categoryId,
                sourcePkg = pending.packageName,
                source = pending.source,
                rawText = "${pending.rawTitle}\n${pending.rawText}",
                capturedAt = pending.capturedAt,
                fingerprint = pending.fingerprint,
                note = note.trim(),
            )
        }
    }

    fun dismissPending(id: Long) {
        viewModelScope.launch { pipeline.dismissPending(id) }
    }

    /**
     * @return 结果文案；大批量时在 IO 上跑。
     */
    suspend fun confirmAllPending(): String = withContext(Dispatchers.IO) {
        val state = uiState.value
        if (state.pending.isEmpty()) return@withContext "没有待确认的记录。"
        val result = pipeline.confirmAllPending(state.pending) { p ->
            state.accounts.firstOrNull { it.type.name == p.suggestedAccountType?.name }?.id
                ?: state.accounts.firstOrNull()?.id
        }
        "全部确认完成：成功 ${result.confirmed} 条，失败 ${result.failed} 条，跳过 ${result.skipped} 条。"
    }

    companion object {
        internal fun parseYuanToFen(value: String): Long? {
            val fen = runCatching {
                value.trim().toBigDecimal().movePointRight(2).longValueExact()
            }.getOrNull()
            return fen?.takeIf { it > 0L }
        }

        fun filterTransactions(
            transactions: List<TransactionWithMeta>,
            query: String,
            categoryFilterId: Long?,
        ): List<TransactionWithMeta> {
            val q = query.trim()
            return transactions.filter { item ->
                val tx = item.transaction
                val catOk = when (categoryFilterId) {
                    null -> true
                    UNCATEGORIZED_FILTER -> tx.categoryId == null
                    else -> tx.categoryId == categoryFilterId
                }
                if (!catOk) return@filter false
                if (q.isEmpty()) return@filter true
                val haystack = buildString {
                    append(tx.merchant)
                    append(' ')
                    append(item.categoryName.orEmpty())
                    append(' ')
                    append(item.accountName)
                    append(' ')
                    append(tx.note)
                    append(' ')
                    append(MoneyFormatSafe(tx.amountFen))
                }
                haystack.contains(q, ignoreCase = true)
            }
        }

        private fun MoneyFormatSafe(fen: Long): String =
            String.format("%.2f", fen / 100.0)
    }
}
