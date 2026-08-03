package com.foldledger.presentation.ledger

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldledger.domain.model.MoneyDirection
import com.foldledger.domain.model.PendingCapture
import com.foldledger.domain.model.ReclassifyCandidate
import com.foldledger.domain.model.TransactionWithMeta
import com.foldledger.domain.util.MoneyFormat
import com.foldledger.domain.util.YearMonths
import com.foldledger.presentation.common.ReclassifyConfirmDialog
import com.foldledger.presentation.common.CategoryColorDot
import com.foldledger.presentation.common.CategoryIconBadge
import com.foldledger.presentation.common.CategoryNameWithColor
import com.foldledger.presentation.common.LedgerPageHeader
import com.foldledger.presentation.theme.CategoryColors
import com.foldledger.presentation.theme.LedgerCoral
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch

private val ChinaTz: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerRoute(
    wide: Boolean,
    viewModel: LedgerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDetail by viewModel.selectedDetail.collectAsStateWithLifecycle()
    // 列表项可能不含 rawText；详情优先用按 id 拉到的完整数据
    val selected = selectedDetail
        ?: state.filteredTransactions.firstOrNull { it.transaction.id == state.selectedId }
        ?: state.transactions.firstOrNull { it.transaction.id == state.selectedId }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var busyMsg by remember { mutableStateOf<String?>(null) }
    var resultMsg by remember { mutableStateOf<String?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var confirmAllRequested by remember { mutableStateOf(false) }
    var pendingToReview by remember { mutableStateOf<PendingCapture?>(null) }
    var pendingToDismiss by remember { mutableStateOf<PendingCapture?>(null) }
    var transactionToDelete by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    var learnSession by remember { mutableStateOf<CategoryLearnSession?>(null) }
    var reclassifyQueue by remember { mutableStateOf<List<ReclassifyCandidate>>(emptyList()) }

    fun resolveAccountId(p: PendingCapture): Long? =
        state.accounts.firstOrNull { it.type.name == p.suggestedAccountType?.name }?.id
            ?: state.accounts.firstOrNull()?.id

    fun requestCategoryChange(item: TransactionWithMeta, categoryId: Long?) {
        if (categoryId != null) {
            val cat = state.categories.firstOrNull { it.id == categoryId } ?: return
            scope.launch {
                val full = viewModel.loadDetail(item.transaction.id) ?: item
                learnSession = CategoryLearnSession(item = full, category = cat)
            }
        } else {
            viewModel.updateCategory(item.transaction.id, null)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LedgerPageHeader(
                title = "我的账本",
                eyebrow = "FOLDLEDGER",
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            imageVector = if (showFilters) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (showFilters) "收起搜索" else "搜索与筛选",
                        )
                    }
                    if (state.pending.isNotEmpty()) {
                        TextButton(
                            onClick = { confirmAllRequested = true },
                        ) { Text("确认 ${state.pending.size} 笔") }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openEditor(true) }) {
                Icon(Icons.Default.Add, contentDescription = "手动记账")
            }
        },
    ) { padding ->
        if (wide) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                LedgerList(
                    state = state,
                    listState = listState,
                    onSearch = viewModel::setSearchQuery,
                    onCategoryFilter = viewModel::setCategoryFilter,
                    onSelect = viewModel::select,
                    onChangeCategory = ::requestCategoryChange,
                    onConfirmPending = { p ->
                        resolveAccountId(p)?.let { viewModel.confirmPending(p, it, null) }
                    },
                    onReviewPending = { pendingToReview = it },
                    onDismissPending = { pendingToDismiss = it },
                    filtersVisible = showFilters,
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider()
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .fillMaxSize(),
                ) {
                    if (selected != null) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            DetailPane(
                                item = selected,
                                categories = state.categories,
                                onChangeCategory = { requestCategoryChange(selected, it) },
                                onDelete = { transactionToDelete = selected.transaction.id },
                            )
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    if (state.filteredTransactions.isEmpty()) {
                                        "暂无匹配流水"
                                    } else {
                                        "选择一笔流水查看详情"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (selected != null) {
                    DetailPane(
                        item = selected,
                        categories = state.categories,
                        onChangeCategory = { requestCategoryChange(selected, it) },
                        onDelete = { transactionToDelete = selected.transaction.id },
                        onBack = { viewModel.select(null) },
                    )
                } else {
                    LedgerList(
                        state = state,
                        listState = listState,
                        onSearch = viewModel::setSearchQuery,
                        onCategoryFilter = viewModel::setCategoryFilter,
                        onSelect = viewModel::select,
                        onChangeCategory = ::requestCategoryChange,
                        onConfirmPending = { p ->
                            resolveAccountId(p)?.let { viewModel.confirmPending(p, it, null) }
                        },
                        onReviewPending = { pendingToReview = it },
                        onDismissPending = { pendingToDismiss = it },
                        filtersVisible = showFilters,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    learnSession?.let { session ->
        KeywordLearnDialog(
            session = session,
            allCategories = state.categories,
            onDismiss = { learnSession = null },
            onConfirm = { keywords ->
                scope.launch {
                    val found = viewModel.assignCategoryWithKeywords(
                        txId = session.item.transaction.id,
                        categoryId = session.category.id,
                        keywords = keywords,
                    )
                    learnSession = null
                    if (found.isNotEmpty()) {
                        reclassifyQueue = found
                    } else {
                        resultMsg = if (keywords.isNotEmpty()) {
                            "已归类并保存关键词。没有发现其他需确认的账单。"
                        } else {
                            "已改分类。没有发现其他需确认的账单。"
                        }
                    }
                }
            },
            onSkipKeywords = {
                // 仅改分类：仍用该类已能命中本笔的关键词去扫其它账单
                scope.launch {
                    val found = viewModel.assignCategoryWithKeywords(
                        txId = session.item.transaction.id,
                        categoryId = session.category.id,
                        keywords = emptyList(),
                    )
                    learnSession = null
                    if (found.isNotEmpty()) {
                        reclassifyQueue = found
                    }
                }
            },
        )
    }

    if (reclassifyQueue.isNotEmpty()) {
        ReclassifyConfirmDialog(
            candidates = reclassifyQueue,
            onConfirmOne = { viewModel.applyReclassify(it) },
            onSkipOne = { },
            onConfirmAll = { viewModel.applyReclassifyAll(reclassifyQueue) },
            onDismissAll = { reclassifyQueue = emptyList() },
        )
    }

    if (state.showEditor) {
        ManualEditorDialog(
            accounts = state.accounts,
            categories = state.categories,
            onDismiss = { viewModel.openEditor(false) },
            onSave = { amount, merchant, dir, accountId, categoryId, toAccountId, note ->
                viewModel.saveManual(amount, merchant, dir, accountId, categoryId, toAccountId, note, null)
            },
        )
    }

    pendingToReview?.let { pending ->
        PendingReviewDialog(
            pending = pending,
            accounts = state.accounts,
            categories = state.categories,
            onDismiss = { pendingToReview = null },
            onSave = { amount, merchant, direction, accountId, categoryId, note ->
                viewModel.confirmPendingEdited(
                    pending = pending,
                    amountYuan = amount,
                    merchant = merchant,
                    direction = direction,
                    accountId = accountId,
                    categoryId = categoryId,
                    note = note,
                )
                pendingToReview = null
            },
        )
    }

    if (confirmAllRequested) {
        AlertDialog(
            onDismissRequest = { confirmAllRequested = false },
            title = { Text("确认全部 ${state.pending.size} 笔？") },
            text = {
                Text("将使用识别金额、推荐账户和自动分类直接入账。金额未知的记录会跳过，之后仍可单独核对。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmAllRequested = false
                        scope.launch {
                            busyMsg = "正在确认 ${state.pending.size} 条…"
                            resultMsg = try {
                                viewModel.confirmAllPending()
                            } catch (t: Throwable) {
                                "全部确认失败：${t.message ?: t.javaClass.simpleName}"
                            } finally {
                                busyMsg = null
                            }
                        }
                    },
                ) { Text("确认入账") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAllRequested = false }) { Text("先核对") }
            },
        )
    }

    pendingToDismiss?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingToDismiss = null },
            title = { Text("不记录这笔？") },
            text = {
                Text(
                    "${pending.merchant ?: "待识别商户"} · " +
                        (pending.amountFen?.let { "¥${MoneyFormat.fenToYuan(it)}" } ?: "金额未知") +
                        "\n这条识别结果会从待确认列表移除。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissPending(pending.id)
                        pendingToDismiss = null
                    },
                ) { Text("不记录") }
            },
            dismissButton = {
                TextButton(onClick = { pendingToDismiss = null }) { Text("保留") }
            },
        )
    }

    transactionToDelete?.let { transactionId ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("移到回收站？") },
            text = { Text("这笔流水会移到回收站，操作完成后可以立即撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(transactionId)
                        viewModel.select(null)
                        transactionToDelete = null
                        scope.launch {
                            if (
                                snackbarHostState.showSnackbar(
                                    message = "已移到回收站",
                                    actionLabel = "撤销",
                                    withDismissAction = true,
                                ) == SnackbarResult.ActionPerformed
                            ) {
                                viewModel.restore(transactionId)
                            }
                        }
                    },
                ) { Text("移到回收站") }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) { Text("取消") }
            },
        )
    }

    busyMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("请稍候") },
            text = { Text(msg) },
            confirmButton = {},
        )
    }
    resultMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { resultMsg = null },
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { resultMsg = null }) { Text("知道了") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LedgerList(
    state: LedgerUiState,
    listState: LazyListState,
    onSearch: (String) -> Unit,
    onCategoryFilter: (Long?) -> Unit,
    onSelect: (Long) -> Unit,
    onChangeCategory: (TransactionWithMeta, Long?) -> Unit,
    onConfirmPending: (PendingCapture) -> Unit,
    onReviewPending: (PendingCapture) -> Unit,
    onDismissPending: (PendingCapture) -> Unit,
    filtersVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val visibleTx = state.filteredTransactions
    val monthSummary = remember(visibleTx) {
        val (start, end) = YearMonths.monthRange(YearMonths.current())
        val monthTx = visibleTx.filter {
            it.transaction.happenedAt >= start && it.transaction.happenedAt < end
        }
        MonthSummary(
            expenseFen = monthTx.filter { it.transaction.direction == MoneyDirection.EXPENSE }
                .sumOf { it.transaction.amountFen },
            incomeFen = monthTx.filter { it.transaction.direction == MoneyDirection.INCOME }
                .sumOf { it.transaction.amountFen },
            count = monthTx.size,
        )
    }
    val dayGroups = remember(visibleTx) {
        visibleTx
            .groupBy { dayStartChina(it.transaction.happenedAt) }
            .toSortedMap(compareByDescending { it })
            .map { (day, list) ->
                val expenseFen = list
                    .filter { it.transaction.direction == MoneyDirection.EXPENSE }
                    .sumOf { it.transaction.amountFen }
                DayGroup(dayEpochMs = day, expenseFen = expenseFen, items = list)
            }
    }
    val filterActive = state.searchQuery.isNotBlank() || state.categoryFilterId != null

    Column(modifier.fillMaxSize()) {
        if (filtersVisible) {
            LedgerSearchAndFilter(
                query = state.searchQuery,
                categoryFilterId = state.categoryFilterId,
                categories = state.categories,
                onSearch = onSearch,
                onCategoryFilter = onCategoryFilter,
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item(key = "month-summary") {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    MonthSummaryCard(monthSummary)
                }
            }
            if (state.pending.isNotEmpty() && !filterActive) {
                item(key = "pending-header") {
                    Text(
                        "待确认",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                items(state.pending, key = { "p-${it.id}" }) { p ->
                    Box(
                        Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 6.dp,
                        ),
                    ) {
                        PendingRow(
                            p = p,
                            onConfirm = { onConfirmPending(p) },
                            onReview = { onReviewPending(p) },
                            onDismiss = { onDismissPending(p) },
                        )
                    }
                }
                item(key = "tx-header") {
                    Text(
                        "流水",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            if (filterActive) {
                item(key = "filter-hint") {
                    Text(
                        "已筛选 ${visibleTx.size} 笔",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            dayGroups.forEach { group ->
                stickyHeader(key = "day-${group.dayEpochMs}") {
                    DaySectionHeader(group)
                }
                items(group.items, key = { it.transaction.id }) { item ->
                    TransactionRow(
                        item = item,
                        categories = state.categories,
                        onClick = { onSelect(item.transaction.id) },
                        onChangeCategory = { catId -> onChangeCategory(item, catId) },
                    )
                }
            }
            if (visibleTx.isEmpty() && (state.pending.isEmpty() || filterActive)) {
                item {
                    Text(
                        when {
                            filterActive -> "没有匹配的流水，试试换个关键词或分类。"
                            else -> "暂无流水。完成一笔微信/支付宝付款，或点右下角手动记账。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerSearchAndFilter(
    query: String,
    categoryFilterId: Long?,
    categories: List<com.foldledger.domain.model.Category>,
    onSearch: (String) -> Unit,
    onCategoryFilter: (Long?) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 16.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("搜索") },
            placeholder = { Text("商户 / 分类 / 备注 / 金额") },
            textStyle = MaterialTheme.typography.bodyMedium,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onSearch("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "清除")
                    }
                }
            },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = categoryFilterId == null,
                onClick = { onCategoryFilter(null) },
                label = { Text("全部") },
            )
            FilterChip(
                selected = categoryFilterId == UNCATEGORIZED_FILTER,
                onClick = {
                    onCategoryFilter(
                        if (categoryFilterId == UNCATEGORIZED_FILTER) null else UNCATEGORIZED_FILTER,
                    )
                },
                label = { Text("未分类") },
            )
            categories.forEach { cat ->
                FilterChip(
                    selected = categoryFilterId == cat.id,
                    onClick = {
                        onCategoryFilter(if (categoryFilterId == cat.id) null else cat.id)
                    },
                    label = { Text(cat.name) },
                    leadingIcon = {
                        CategoryColorDot(
                            name = cat.name,
                            colorArgb = cat.colorArgb,
                            size = 8.dp,
                        )
                    },
                )
            }
        }
    }
}

private data class MonthSummary(
    val expenseFen: Long,
    val incomeFen: Long,
    val count: Int,
)

private data class DayGroup(
    val dayEpochMs: Long,
    val expenseFen: Long,
    val items: List<TransactionWithMeta>,
)

@Composable
private fun MonthSummaryCard(summary: MonthSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SummaryStat(
                label = "本月支出",
                value = "¥${MoneyFormat.fenToYuan(summary.expenseFen)}",
                valueColor = LedgerCoral,
            )
            SummaryStat(
                label = "本月收入",
                value = "¥${MoneyFormat.fenToYuan(summary.incomeFen)}",
                valueColor = MaterialTheme.colorScheme.primary,
            )
            SummaryStat(
                label = "笔数",
                value = "${summary.count}",
                valueColor = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SummaryStat(
    label: String,
    value: String,
    valueColor: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
        )
    }
}

@Composable
private fun DaySectionHeader(group: DayGroup) {
    val label = remember(group.dayEpochMs) {
        SimpleDateFormat("M月d日", Locale.CHINA).apply { timeZone = ChinaTz }
            .format(Date(group.dayEpochMs))
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Text(
            "$label · 支¥${MoneyFormat.fenToYuan(group.expenseFen)}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun PendingRow(
    p: PendingCapture,
    onConfirm: () -> Unit,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
) {
    val capturedTime = remember(p.capturedAt) {
        SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).apply { timeZone = ChinaTz }
            .format(Date(p.capturedAt))
    }
    val canQuickConfirm = (p.amountFen ?: 0L) > 0L
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "自动识别 · ${captureSourceLabel(p.source.name)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    p.merchant ?: "待识别商户",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    p.amountFen?.let { "¥${MoneyFormat.fenToYuan(it)}" } ?: "金额未知",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "$capturedTime · 入账前可修改金额、商户、账户和分类",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onReview,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("核对并入账") }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onDismiss) { Text("不记录") }
                TextButton(
                    onClick = onConfirm,
                    enabled = canQuickConfirm,
                ) { Text("信息无误，快速记账") }
            }
        }
    }
}

private fun captureSourceLabel(source: String): String = when (source) {
    "WECHAT_NLS", "WECHAT_A11Y", "WECHAT_IMPORT" -> "微信"
    "ALIPAY_NLS", "ALIPAY_A11Y", "ALIPAY_IMPORT" -> "支付宝"
    "BANK_SMS" -> "银行短信"
    else -> "支付信息"
}

@Composable
private fun TransactionRow(
    item: TransactionWithMeta,
    categories: List<com.foldledger.domain.model.Category>,
    onClick: () -> Unit,
    onChangeCategory: (Long?) -> Unit,
) {
    val tx = item.transaction
    val time = remember(tx.happenedAt) {
        SimpleDateFormat("HH:mm", Locale.CHINA).apply { timeZone = ChinaTz }
            .format(Date(tx.happenedAt))
    }
    val title = tx.merchant.ifBlank { "未命名商户" }
    val catName = item.categoryName ?: "未分类"
    val matchedCat = categories.firstOrNull { it.id == tx.categoryId }
    val catColor = CategoryColors.of(item.categoryName, matchedCat?.colorArgb)
    val amountColor = when (tx.direction) {
        MoneyDirection.INCOME -> MaterialTheme.colorScheme.secondary
        MoneyDirection.EXPENSE -> MaterialTheme.colorScheme.onSurface
        MoneyDirection.TRANSFER -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    }
    val prefix = when (tx.direction) {
        MoneyDirection.EXPENSE, MoneyDirection.TRANSFER -> "-"
        MoneyDirection.INCOME -> "+"
    }
    var showPicker by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIconBadge(
                    name = item.categoryName,
                    colorArgb = matchedCat?.colorArgb,
                    size = 32.dp,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            modifier = Modifier.clickable { showPicker = true },
                            color = catColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                catName,
                                style = MaterialTheme.typography.labelSmall,
                                color = catColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                        Text(
                            "$time · ${item.accountName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "$prefix¥${MoneyFormat.fenToYuan(tx.amountFen)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = amountColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = 58.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
            )
        }
    }

    if (showPicker) {
        CategoryPickerDialog(
            direction = tx.direction,
            categories = categories,
            selectedId = tx.categoryId,
            onDismiss = { showPicker = false },
            onSelect = {
                onChangeCategory(it)
                showPicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailPane(
    item: TransactionWithMeta,
    categories: List<com.foldledger.domain.model.Category>,
    onChangeCategory: (Long?) -> Unit,
    onDelete: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val tx = item.transaction
    var categoryExpanded by remember(tx.id, tx.categoryId) { mutableStateOf(false) }
    val eligible = remember(categories, tx.direction) {
        categories.filter {
            it.direction == tx.direction || tx.direction == MoneyDirection.TRANSFER
        }
    }
    val currentName = item.categoryName ?: "未分类"

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("返回") }
        }
        Text(tx.merchant, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("¥${MoneyFormat.fenToYuan(tx.amountFen)}", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(12.dp))
        Text("账户：${item.accountName}")
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(categoryExpanded, { categoryExpanded = it }) {
            OutlinedTextField(
                value = currentName,
                onValueChange = {},
                readOnly = true,
                label = { Text("分类") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(categoryExpanded, { categoryExpanded = false }) {
                DropdownMenuItem(
                    text = { CategoryNameWithColor(name = "未分类") },
                    onClick = {
                        onChangeCategory(null)
                        categoryExpanded = false
                    },
                )
                eligible.forEach { cat ->
                    DropdownMenuItem(
                        text = {
                            CategoryNameWithColor(
                                name = cat.name,
                                colorArgb = cat.colorArgb,
                            )
                        },
                        onClick = {
                            onChangeCategory(cat.id)
                            categoryExpanded = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("来源：${tx.source.name}")
        if (tx.note.isNotBlank()) Text("备注：${tx.note}")
        if (!tx.rawText.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("原始文案", style = MaterialTheme.typography.titleMedium)
            Text(tx.rawText!!, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onDelete) { Text("删除到回收站") }
    }
}

@Composable
private fun CategoryPickerDialog(
    direction: MoneyDirection,
    categories: List<com.foldledger.domain.model.Category>,
    selectedId: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit,
) {
    val eligible = categories.filter {
        it.direction == direction || direction == MoneyDirection.TRANSFER
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改分类") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    onClick = { onSelect(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CategoryNameWithColor(
                        name = "未分类",
                        trailing = if (selectedId == null) "✓ " else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                eligible.forEach { cat ->
                    TextButton(
                        onClick = { onSelect(cat.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CategoryNameWithColor(
                            name = cat.name,
                            colorArgb = cat.colorArgb,
                            trailing = if (selectedId == cat.id) "✓ " else null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private data class CategoryLearnSession(
    val item: TransactionWithMeta,
    val category: com.foldledger.domain.model.Category,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingReviewDialog(
    pending: PendingCapture,
    accounts: List<com.foldledger.domain.model.Account>,
    categories: List<com.foldledger.domain.model.Category>,
    onDismiss: () -> Unit,
    onSave: (
        amount: String,
        merchant: String,
        direction: MoneyDirection,
        accountId: Long,
        categoryId: Long?,
        note: String,
    ) -> Unit,
) {
    var amount by remember(pending.id) {
        mutableStateOf(pending.amountFen?.let { MoneyFormat.fenToYuan(it) }.orEmpty())
    }
    var merchant by remember(pending.id) {
        mutableStateOf(pending.merchant.orEmpty())
    }
    var note by remember(pending.id) { mutableStateOf("") }
    var direction by remember(pending.id) { mutableStateOf(pending.direction) }
    var accountId by remember(pending.id, accounts) {
        mutableStateOf(
            accounts.firstOrNull { it.type == pending.suggestedAccountType }?.id
                ?: accounts.firstOrNull()?.id
                ?: 0L,
        )
    }
    var categoryId by remember(pending.id) { mutableStateOf<Long?>(null) }
    var accountExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    val amountValid = LedgerViewModel.parseYuanToFen(amount) != null
    val eligibleCategories = categories.filter { it.direction == direction }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("核对自动记账") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "${captureSourceLabel(pending.source.name)} · 识别结果尚未写入账本",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("金额（元）") },
                    singleLine = true,
                    isError = amount.isNotBlank() && !amountValid,
                    supportingText = if (amount.isNotBlank() && !amountValid) {
                        { Text("请输入大于 0 的金额") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("商户") },
                    singleLine = true,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        MoneyDirection.EXPENSE to "支出",
                        MoneyDirection.INCOME to "收入",
                        MoneyDirection.TRANSFER to "转账",
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = direction == value,
                            onClick = {
                                direction = value
                                if (categories.none { it.id == categoryId && it.direction == value }) {
                                    categoryId = null
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
                ExposedDropdownMenuBox(accountExpanded, { accountExpanded = it }) {
                    OutlinedTextField(
                        value = accounts.firstOrNull { it.id == accountId }?.name ?: "没有可用账户",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("账户") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(accountExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(accountExpanded, { accountExpanded = false }) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    accountId = account.id
                                    accountExpanded = false
                                },
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(categoryExpanded, { categoryExpanded = it }) {
                    OutlinedTextField(
                        value = categories.firstOrNull { it.id == categoryId }?.name ?: "自动分类",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("分类") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(categoryExpanded, { categoryExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("自动分类") },
                            onClick = {
                                categoryId = null
                                categoryExpanded = false
                            },
                        )
                        eligibleCategories.forEach { category ->
                            DropdownMenuItem(
                                text = {
                                    CategoryNameWithColor(
                                        name = category.name,
                                        colorArgb = category.colorArgb,
                                    )
                                },
                                onClick = {
                                    categoryId = category.id
                                    categoryExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注（可选）") },
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(amount, merchant, direction, accountId, categoryId, note)
                },
                enabled = amountValid && accountId > 0L,
            ) { Text("确认入账") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后处理") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordLearnDialog(
    session: CategoryLearnSession,
    allCategories: List<com.foldledger.domain.model.Category>,
    onDismiss: () -> Unit,
    onConfirm: (keywords: List<String>) -> Unit,
    onSkipKeywords: () -> Unit,
) {
    val tx = session.item.transaction
    val occupiedElsewhere = remember(allCategories, session.category.id) {
        allCategories
            .filter { it.id != session.category.id }
            .flatMap { it.keywords }
            .map { it.trim().lowercase() }
            .toSet()
    }
    val alreadyInTarget = remember(session.category) {
        session.category.keywords.map { it.trim().lowercase() }.toSet()
    }
    val suggestions = remember(tx.id, session.category.id, occupiedElsewhere, alreadyInTarget) {
        KeywordSuggestions.suggest(
            merchant = tx.merchant,
            rawText = tx.rawText,
            note = tx.note,
            occupiedElsewhere = occupiedElsewhere,
            alreadyInTarget = alreadyInTarget,
        )
    }
    val selected = remember(suggestions) {
        mutableStateListOf<String>().also { list ->
            suggestions.filter { it.selectedByDefault }.forEach { list.add(it.text) }
            if (list.isEmpty()) {
                suggestions.firstOrNull()?.let { list.add(it.text) }
            }
        }
    }
    var draft by remember { mutableStateOf("") }
    var draftError by remember { mutableStateOf<String?>(null) }
    var editError by remember { mutableStateOf<String?>(null) }

    fun validateKeyword(value: String, ignoreIndex: Int? = null): String? {
        val t = value.trim()
        if (t.length < 2) return "至少 2 个字"
        val key = t.lowercase()
        if (key in occupiedElsewhere) return "已被其他分类占用"
        // 已在目标分类的词允许保留：用来触发扫描其它同类账单
        val dup = selected.withIndex().any { (i, s) ->
            ignoreIndex != i && s.trim().equals(t, ignoreCase = true)
        }
        if (dup) return "与已选项重复"
        return null
    }

    fun tryAddDraft() {
        val err = validateKeyword(draft)
        if (err != null) {
            draftError = err
            return
        }
        selected.add(draft.trim())
        draft = ""
        draftError = null
    }

    fun commitSelected(): List<String>? {
        val cleaned = selected.map { it.trim() }.filter { it.isNotEmpty() }
        cleaned.forEachIndexed { index, word ->
            val err = validateKeyword(word, ignoreIndex = index)
            if (err != null) {
                editError = "「$word」$err"
                return null
            }
        }
        val distinct = cleaned.distinctBy { it.lowercase() }
        editError = null
        return distinct
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("归入「${session.category.name}」") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "已细拆商户名与账单原文。点候选加入；下方已选可直接改字。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                if (suggestions.isNotEmpty()) {
                    Text("候选（点选加入/取消）", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        suggestions.forEach { sug ->
                            val checked = selected.any { it.equals(sug.text, ignoreCase = true) }
                            FilterChip(
                                selected = checked,
                                onClick = {
                                    if (checked) {
                                        selected.removeAll { it.equals(sug.text, ignoreCase = true) }
                                    } else {
                                        selected.add(sug.text)
                                    }
                                    editError = null
                                },
                                label = { Text(sug.text) },
                            )
                        }
                    }
                } else {
                    Text(
                        "没有自动筛出候选，可在下方手动添加。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }

                Text("已选关键词（可编辑）", style = MaterialTheme.typography.labelLarge)
                if (selected.isEmpty()) {
                    Text(
                        "尚未选择，点上方候选或手动添加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                } else {
                    selected.forEachIndexed { index, word ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            OutlinedTextField(
                                value = word,
                                onValueChange = {
                                    selected[index] = it
                                    editError = null
                                },
                                singleLine = true,
                                label = { Text("关键词 ${index + 1}") },
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    selected.removeAt(index)
                                    editError = null
                                },
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "删除")
                            }
                        }
                    }
                }
                if (editError != null) {
                    Text(
                        editError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            draftError = null
                        },
                        label = { Text("手动添加") },
                        placeholder = { Text("输入后可再编辑") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        isError = draftError != null,
                        supportingText = draftError?.let { { Text(it) } },
                    )
                    TextButton(
                        onClick = { tryAddDraft() },
                        enabled = draft.isNotBlank(),
                    ) { Text("添加") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val words = commitSelected() ?: return@TextButton
                    onConfirm(words)
                },
            ) {
                Text(if (selected.isEmpty()) "确认归类" else "归类并学习(${selected.size})")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSkipKeywords) { Text("仅改分类") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualEditorDialog(
    accounts: List<com.foldledger.domain.model.Account>,
    categories: List<com.foldledger.domain.model.Category>,
    onDismiss: () -> Unit,
    onSave: (
        amount: String,
        merchant: String,
        direction: MoneyDirection,
        accountId: Long,
        categoryId: Long?,
        toAccountId: Long?,
        note: String,
    ) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(MoneyDirection.EXPENSE) }
    var accountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 0L) }
    var toAccountId by remember { mutableStateOf<Long?>(null) }
    var categoryId by remember { mutableStateOf(categories.firstOrNull { it.direction == direction }?.id) }
    var accountExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var directionExpanded by remember { mutableStateOf(false) }
    val amountValid = LedgerViewModel.parseYuanToFen(amount) != null
    val canSave = amountValid &&
        accountId > 0L &&
        (direction != MoneyDirection.TRANSFER || toAccountId != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动记账") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("金额（元）") },
                    singleLine = true,
                    isError = amount.isNotBlank() && !amountValid,
                    supportingText = if (amount.isNotBlank() && !amountValid) {
                        { Text("请输入最多两位小数且大于 0 的金额") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(merchant, { merchant = it }, label = { Text("商户") }, singleLine = true)
                ExposedDropdownMenuBox(directionExpanded, { directionExpanded = it }) {
                    OutlinedTextField(
                        value = when (direction) {
                            MoneyDirection.EXPENSE -> "支出"
                            MoneyDirection.INCOME -> "收入"
                            MoneyDirection.TRANSFER -> "转账"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(directionExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(directionExpanded, { directionExpanded = false }) {
                        listOf(MoneyDirection.EXPENSE, MoneyDirection.INCOME, MoneyDirection.TRANSFER).forEach {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (it) {
                                            MoneyDirection.EXPENSE -> "支出"
                                            MoneyDirection.INCOME -> "收入"
                                            MoneyDirection.TRANSFER -> "转账"
                                        },
                                    )
                                },
                                onClick = {
                                    direction = it
                                    if (categories.none { category -> category.id == categoryId && category.direction == it }) {
                                        categoryId = null
                                    }
                                    if (it != MoneyDirection.TRANSFER) {
                                        toAccountId = null
                                    }
                                    directionExpanded = false
                                },
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(accountExpanded, { accountExpanded = it }) {
                    OutlinedTextField(
                        value = accounts.firstOrNull { it.id == accountId }?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("账户") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(accountExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(accountExpanded, { accountExpanded = false }) {
                        accounts.forEach {
                            DropdownMenuItem(
                                text = { Text(it.name) },
                                onClick = {
                                    accountId = it.id
                                    accountExpanded = false
                                },
                            )
                        }
                    }
                }
                if (direction == MoneyDirection.TRANSFER) {
                    var toExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(toExpanded, { toExpanded = it }) {
                        OutlinedTextField(
                            value = accounts.firstOrNull { it.id == toAccountId }?.name ?: "选择转入账户",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("转入账户") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(toExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(toExpanded, { toExpanded = false }) {
                            accounts.filter { it.id != accountId }.forEach {
                                DropdownMenuItem(
                                    text = { Text(it.name) },
                                    onClick = {
                                        toAccountId = it.id
                                        toExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                ExposedDropdownMenuBox(categoryExpanded, { categoryExpanded = it }) {
                    OutlinedTextField(
                        value = categories.firstOrNull { it.id == categoryId }?.name ?: "未分类",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("分类") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(categoryExpanded, { categoryExpanded = false }) {
                        DropdownMenuItem(
                            text = { CategoryNameWithColor(name = "未分类") },
                            onClick = {
                                categoryId = null
                                categoryExpanded = false
                            },
                        )
                        categories.filter {
                            it.direction == direction || direction == MoneyDirection.TRANSFER
                        }.forEach {
                            DropdownMenuItem(
                                text = {
                                    CategoryNameWithColor(
                                        name = it.name,
                                        colorArgb = it.colorArgb,
                                    )
                                },
                                onClick = {
                                    categoryId = it.id
                                    categoryExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("备注") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(amount, merchant, direction, accountId, categoryId, toAccountId, note)
                },
                enabled = canSave,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun dayStartChina(happenedAt: Long): Long {
    return Calendar.getInstance(ChinaTz, Locale.CHINA).apply {
        timeInMillis = happenedAt
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
