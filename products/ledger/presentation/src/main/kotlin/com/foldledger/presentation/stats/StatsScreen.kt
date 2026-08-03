package com.foldledger.presentation.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.foldledger.domain.model.CaptureSource
import com.foldledger.domain.model.Category
import com.foldledger.domain.model.CategorySpend
import com.foldledger.domain.model.DailySpend
import com.foldledger.domain.model.TransactionWithMeta
import com.foldledger.domain.repo.CategoryRepository
import com.foldledger.domain.repo.TransactionRepository
import com.foldledger.domain.util.MoneyFormat
import com.foldledger.domain.util.YearMonths
import com.foldledger.presentation.theme.CategoryColors
import com.foldledger.presentation.common.LedgerCard
import com.foldledger.presentation.common.LedgerMetric
import com.foldledger.presentation.common.LedgerPageHeader
import com.foldledger.presentation.theme.LedgerCoral
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

enum class StatsRangePreset(val label: String) {
    THIS_MONTH("本月"),
    LAST_7_DAYS("近7天"),
    LAST_30_DAYS("近30天"),
    LAST_3_MONTHS("近3个月"),
    THIS_YEAR("今年"),
    ALL("全部"),
}

private enum class ChartStyle(val label: String) {
    BAR("柱状图"),
    LINE("折线图"),
}

private enum class CategoryChartStyle(val label: String) {
    BARS("横条"),
    PIE("饼图"),
}

private enum class ExpenseSort(val label: String) {
    TIME_DESC("时间近→远"),
    TIME_ASC("时间远→近"),
    AMOUNT_DESC("金额大→小"),
    AMOUNT_ASC("金额小→大"),
    MERCHANT("商户名"),
}

data class StatsUiState(
    val preset: StatsRangePreset = StatsRangePreset.THIS_MONTH,
    val rangeLabel: String = "",
    val daily: List<DailySpend> = emptyList(),
    val byCategory: List<CategorySpend> = emptyList(),
    val categories: List<Category> = emptyList(),
    val totalFen: Long = 0,
    val txCount: Int = 0,
    val rangeStartMs: Long = 0,
    val rangeEndMs: Long = 0,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
) : ViewModel() {
    private val preset = MutableStateFlow(StatsRangePreset.THIS_MONTH)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StatsUiState> = preset.flatMapLatest { p ->
        val (start, end) = rangeOf(p)
        combine(
            transactions.observeDailySpend(start, end),
            transactions.observeCategorySpend(start, end),
            transactions.observeExpenseCount(start, end),
            categories.observeCategories(),
        ) { daily, cats, count, categoryList ->
            StatsUiState(
                preset = p,
                rangeLabel = formatRangeLabel(p, start, end),
                daily = daily,
                byCategory = cats,
                categories = categoryList,
                totalFen = cats.sumOf { it.amountFen },
                txCount = count,
                rangeStartMs = start,
                rangeEndMs = end,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setPreset(p: StatsRangePreset) {
        preset.value = p
    }

    suspend fun loadExpensesInRange(startMs: Long, endMs: Long): List<TransactionWithMeta> =
        withContext(Dispatchers.IO) {
            transactions.listExpensesInRange(startMs, endMs)
        }

    suspend fun loadDetail(id: Long): TransactionWithMeta? = withContext(Dispatchers.IO) {
        transactions.getWithMeta(id)
    }

    companion object {
        private val china = TimeZone.getTimeZone("Asia/Shanghai")

        fun rangeOf(preset: StatsRangePreset): Pair<Long, Long> {
            val now = Calendar.getInstance(china, Locale.CHINA)
            val endExclusive = dayEndExclusive(now)
            return when (preset) {
                StatsRangePreset.THIS_MONTH -> YearMonths.monthRange(YearMonths.current())
                StatsRangePreset.LAST_7_DAYS -> {
                    val start = Calendar.getInstance(china, Locale.CHINA).apply {
                        add(Calendar.DAY_OF_YEAR, -6)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    start to endExclusive
                }
                StatsRangePreset.LAST_30_DAYS -> {
                    val start = Calendar.getInstance(china, Locale.CHINA).apply {
                        add(Calendar.DAY_OF_YEAR, -29)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    start to endExclusive
                }
                StatsRangePreset.LAST_3_MONTHS -> {
                    val start = Calendar.getInstance(china, Locale.CHINA).apply {
                        add(Calendar.MONTH, -2)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    start to endExclusive
                }
                StatsRangePreset.THIS_YEAR -> {
                    val start = Calendar.getInstance(china, Locale.CHINA).apply {
                        set(Calendar.MONTH, Calendar.JANUARY)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    start to endExclusive
                }
                StatsRangePreset.ALL -> 0L to endExclusive
            }
        }

        private fun dayEndExclusive(cal: Calendar): Long {
            return Calendar.getInstance(china, Locale.CHINA).apply {
                timeInMillis = cal.timeInMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
        }

        private fun formatRangeLabel(preset: StatsRangePreset, start: Long, end: Long): String {
            if (preset == StatsRangePreset.ALL) return "全部时间"
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply { timeZone = china }
            val endInclusive = end - 1
            return "${fmt.format(Date(start))} ~ ${fmt.format(Date(endInclusive))}"
        }

        fun dayStartChina(happenedAt: Long): Long {
            return Calendar.getInstance(china, Locale.CHINA).apply {
                timeInMillis = happenedAt
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }
}

private fun colorFor(
    spend: CategorySpend,
    categories: List<Category>,
): Color {
    val argb = categories.firstOrNull { it.id == spend.categoryId }?.colorArgb
    return CategoryColors.of(spend.categoryName, argb)
}

private fun colorForTx(
    item: TransactionWithMeta,
    categories: List<Category>,
): Color {
    val argb = categories.firstOrNull { it.id == item.transaction.categoryId }?.colorArgb
    return CategoryColors.of(item.categoryName, argb)
}


private fun sortExpenses(
    items: List<TransactionWithMeta>,
    sort: ExpenseSort,
): List<TransactionWithMeta> = when (sort) {
    ExpenseSort.TIME_DESC -> items.sortedByDescending { it.transaction.happenedAt }
    ExpenseSort.TIME_ASC -> items.sortedBy { it.transaction.happenedAt }
    ExpenseSort.AMOUNT_DESC -> items.sortedByDescending { it.transaction.amountFen }
    ExpenseSort.AMOUNT_ASC -> items.sortedBy { it.transaction.amountFen }
    ExpenseSort.MERCHANT -> items.sortedWith(
        compareBy<TransactionWithMeta> { it.transaction.merchant.lowercase() }
            .thenByDescending { it.transaction.happenedAt },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsRoute(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var chartStyle by remember { mutableStateOf(ChartStyle.BAR) }
    var categoryChartStyle by remember { mutableStateOf(CategoryChartStyle.BARS) }
    var selectedDay by remember { mutableStateOf<DailySpend?>(null) }
    var selectedCategory by remember { mutableStateOf<CategorySpend?>(null) }
    var selectedTx by remember { mutableStateOf<TransactionWithMeta?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LedgerPageHeader(
                title = "消费洞察",
                eyebrow = state.rangeLabel.ifBlank { "统计分析" },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatsRangePreset.entries.forEach { p ->
                    FilterChip(
                        selected = state.preset == p,
                        onClick = { viewModel.setPreset(p) },
                        label = { Text(p.label) },
                    )
                }
            }
            LedgerMetric(
                label = "支出",
                value = "¥${MoneyFormat.fenToYuan(state.totalFen)}",
                modifier = Modifier.fillMaxWidth(),
                accent = LedgerCoral,
                supporting = "${state.txCount} 笔交易 · ${state.rangeLabel}",
            )
            LedgerCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("每日走势", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "点击图表查看当日明细",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChartStyle.entries.forEach { style ->
                            FilterChip(
                                selected = chartStyle == style,
                                onClick = { chartStyle = style },
                                label = { Text(style.label) },
                            )
                        }
                    }
                }
                when (chartStyle) {
                    ChartStyle.BAR -> DailyBarChart(state.daily, onDayClick = { selectedDay = it })
                    ChartStyle.LINE -> DailyLineChart(state.daily, onDayClick = { selectedDay = it })
                }
            }
            LedgerCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("分类构成", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "看看钱主要花在了哪里",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryChartStyle.entries.forEach { style ->
                            FilterChip(
                                selected = categoryChartStyle == style,
                                onClick = { categoryChartStyle = style },
                                label = { Text(style.label) },
                            )
                        }
                    }
                }
                when (categoryChartStyle) {
                    CategoryChartStyle.BARS -> CategoryBarList(
                        items = state.byCategory,
                        totalFen = state.totalFen,
                        categories = state.categories,
                        onClick = { selectedCategory = it },
                    )
                    CategoryChartStyle.PIE -> CategoryPieChart(
                        items = state.byCategory,
                        totalFen = state.totalFen,
                        categories = state.categories,
                        onClick = { selectedCategory = it },
                    )
                }
                if (state.byCategory.isEmpty()) {
                    Text(
                        "这个时间范围内还没有支出",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    selectedDay?.let { day ->
        var dayTx by remember(day.dayEpochMs) { mutableStateOf<List<TransactionWithMeta>?>(null) }
        LaunchedEffect(day.dayEpochMs, state.rangeStartMs, state.rangeEndMs) {
            dayTx = null
            val all = viewModel.loadExpensesInRange(state.rangeStartMs, state.rangeEndMs)
            dayTx = all.filter {
                StatsViewModel.dayStartChina(it.transaction.happenedAt) == day.dayEpochMs
            }
        }
        val dayLabel = remember(day.dayEpochMs) {
            SimpleDateFormat("M月d日", Locale.CHINA)
                .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
                .format(Date(day.dayEpochMs))
        }
        val timeFmt = remember {
            SimpleDateFormat("HH:mm", Locale.CHINA)
                .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
        }
        AlertDialog(
            onDismissRequest = { selectedDay = null },
            title = { Text("$dayLabel · 支出 ¥${MoneyFormat.fenToYuan(day.amountFen)}") },
            text = {
                when {
                    dayTx == null -> Text("加载中…")
                    dayTx!!.isEmpty() -> Text("当日暂无支出流水")
                    else -> {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            dayTx!!.forEach { item ->
                                ExpenseDetailRow(
                                    item = item,
                                    timeFmt = timeFmt,
                                    categories = state.categories,
                                    onClick = { selectedTx = item },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDay = null }) { Text("关闭") }
            },
        )
    }

    selectedCategory?.let { cat ->
        var catTxRaw by remember(cat.categoryId, cat.categoryName) {
            mutableStateOf<List<TransactionWithMeta>?>(null)
        }
        var sort by remember(cat.categoryId, cat.categoryName) {
            mutableStateOf(ExpenseSort.TIME_DESC)
        }
        LaunchedEffect(cat.categoryId, cat.categoryName, state.rangeStartMs, state.rangeEndMs) {
            catTxRaw = null
            val all = viewModel.loadExpensesInRange(state.rangeStartMs, state.rangeEndMs)
            catTxRaw = all.filter { item ->
                when {
                    cat.categoryId != null -> item.transaction.categoryId == cat.categoryId
                    else -> item.transaction.categoryId == null ||
                        item.categoryName == cat.categoryName ||
                        (item.categoryName.isNullOrBlank() && cat.categoryName == "未分类")
                }
            }
        }
        val catTx = catTxRaw?.let { sortExpenses(it, sort) }
        val timeFmt = remember {
            SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
                .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
        }
        AlertDialog(
            onDismissRequest = { selectedCategory = null },
            title = {
                Text("${cat.categoryName} · 支出 ¥${MoneyFormat.fenToYuan(cat.amountFen)}")
            },
            text = {
                when {
                    catTx == null -> Text("加载中…")
                    catTx.isEmpty() -> Text("该分类暂无支出流水")
                    else -> {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "共 ${catTx.size} 笔",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ExpenseSort.entries.forEach { option ->
                                    FilterChip(
                                        selected = sort == option,
                                        onClick = { sort = option },
                                        label = { Text(option.label) },
                                    )
                                }
                            }
                            catTx.forEach { item ->
                                ExpenseDetailRow(
                                    item = item,
                                    timeFmt = timeFmt,
                                    categories = state.categories,
                                    showCategory = false,
                                    onClick = { selectedTx = item },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedCategory = null }) { Text("关闭") }
            },
        )
    }

    selectedTx?.let { preview ->
        var detail by remember(preview.transaction.id) { mutableStateOf<TransactionWithMeta?>(null) }
        LaunchedEffect(preview.transaction.id) {
            detail = viewModel.loadDetail(preview.transaction.id) ?: preview
        }
        val shown = detail ?: preview
        val timeFmt = remember {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
        }
        AlertDialog(
            onDismissRequest = { selectedTx = null },
            title = { Text("账单详情") },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        shown.transaction.merchant.ifBlank { "未命名商户" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "¥${MoneyFormat.fenToYuan(shown.transaction.amountFen)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        timeFmt.format(Date(shown.transaction.happenedAt)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                    Text("账户：${shown.accountName}")
                    Text("分类：${shown.categoryName ?: "未分类"}")
                    Text("来源：${sourceLabel(shown.transaction.source)}")
                    if (shown.transaction.note.isNotBlank()) {
                        Text("备注：${shown.transaction.note}")
                    }
                    if (!shown.transaction.rawText.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("原始文案", style = MaterialTheme.typography.titleMedium)
                        Text(
                            shown.transaction.rawText!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    } else if (detail == null) {
                        Text(
                            "加载中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTx = null }) { Text("关闭") }
            },
        )
    }
}

private fun sourceLabel(source: CaptureSource): String = when (source) {
    CaptureSource.MANUAL -> "手动"
    CaptureSource.WECHAT_IMPORT -> "微信导入"
    CaptureSource.ALIPAY_IMPORT -> "支付宝导入"
    CaptureSource.WECHAT_NLS, CaptureSource.WECHAT_A11Y -> "微信实时"
    CaptureSource.ALIPAY_NLS, CaptureSource.ALIPAY_A11Y -> "支付宝实时"
    CaptureSource.BANK_SMS -> "银行短信"
    else -> source.name
}

@Composable
private fun CategoryBarList(
    items: List<CategorySpend>,
    totalFen: Long,
    categories: List<Category>,
    onClick: (CategorySpend) -> Unit,
) {
    items.forEach { cat ->
        val ratio = if (totalFen == 0L) 0f else cat.amountFen.toFloat() / totalFen
        val color = colorFor(cat, categories)
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { onClick(cat) }
                .padding(vertical = 4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(color, CircleShape),
                    )
                    Text(cat.categoryName)
                }
                Text("¥${MoneyFormat.fenToYuan(cat.amountFen)}")
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth(),
                color = color,
            )
        }
    }
}

@Composable
private fun CategoryPieChart(
    items: List<CategorySpend>,
    totalFen: Long,
    categories: List<Category>,
    onClick: (CategorySpend) -> Unit,
) {
    if (items.isEmpty() || totalFen <= 0L) return
    val slices = remember(items, categories, totalFen) {
        items.map { it to colorFor(it, categories) }
    }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f),
        ) {
            var start = -90f
            val diameter = size.minDimension
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            slices.forEach { (cat, color) ->
                val sweep = 360f * (cat.amountFen.toFloat() / totalFen)
                drawArc(
                    color = color,
                    startAngle = start,
                    sweepAngle = sweep.coerceAtLeast(0.5f),
                    useCenter = true,
                    topLeft = topLeft,
                    size = arcSize,
                )
                start += sweep
            }
        }
        Spacer(Modifier.height(8.dp))
        slices.forEach { (cat, color) ->
            val pct = if (totalFen == 0L) 0f else cat.amountFen * 100f / totalFen
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onClick(cat) }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(10.dp).background(color, CircleShape))
                    Text(cat.categoryName)
                    Text(
                        String.format(Locale.CHINA, "%.1f%%", pct),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
                Text("¥${MoneyFormat.fenToYuan(cat.amountFen)}")
            }
        }
    }
}

@Composable
private fun ExpenseDetailRow(
    item: TransactionWithMeta,
    timeFmt: SimpleDateFormat,
    categories: List<Category>,
    showCategory: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val tx = item.transaction
    val catColor = colorForTx(item, categories)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                tx.merchant.ifBlank { "未命名商户" },
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showCategory) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(catColor, CircleShape),
                    )
                    Text(
                        item.categoryName ?: "未分类",
                        style = MaterialTheme.typography.bodySmall,
                        color = catColor,
                    )
                    Text(
                        "· ${timeFmt.format(Date(tx.happenedAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                } else {
                    Text(
                        timeFmt.format(Date(tx.happenedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                    if (item.accountName.isNotBlank()) {
                        Text(
                            "· ${item.accountName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }
        Text(
            "¥${MoneyFormat.fenToYuan(tx.amountFen)}",
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DailyBarChart(
    daily: List<DailySpend>,
    onDayClick: (DailySpend) -> Unit,
) {
    val max = remember(daily) { daily.maxOfOrNull { it.amountFen }?.coerceAtLeast(1) ?: 1L }
    val barColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val dayFormat = remember {
        SimpleDateFormat("M/d", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
    }

    if (daily.isEmpty()) {
        Text("暂无按日数据", color = axisColor, style = MaterialTheme.typography.bodySmall)
        return
    }

    val scroll = rememberScrollState()
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        daily.forEach { d ->
            val ratio = (d.amountFen.toFloat() / max).coerceIn(0f, 1f)
            Column(
                modifier = Modifier
                    .width(52.dp)
                    .clickable { onDayClick(d) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "¥${MoneyFormat.fenToYuan(d.amountFen)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .fillMaxHeight(ratio.coerceAtLeast(0.02f))
                            .background(barColor, shape = MaterialTheme.shapes.extraSmall),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = dayFormat.format(Date(d.dayEpochMs)),
                    color = axisColor,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DailyLineChart(
    daily: List<DailySpend>,
    onDayClick: (DailySpend) -> Unit,
) {
    val max = remember(daily) { daily.maxOfOrNull { it.amountFen }?.coerceAtLeast(1) ?: 1L }
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val dayFormat = remember {
        SimpleDateFormat("M/d", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
    }

    if (daily.isEmpty()) {
        Text("暂无按日数据", color = axisColor, style = MaterialTheme.typography.bodySmall)
        return
    }

    val scroll = rememberScrollState()
    val colWidth = 52.dp
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .height(148.dp),
        ) {
            val chartWidth = colWidth * daily.size
            Canvas(
                Modifier
                    .width(chartWidth)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp),
            ) {
                if (daily.isEmpty()) return@Canvas
                val colPx = size.width / daily.size.coerceAtLeast(1)
                val points = daily.mapIndexed { i, d ->
                    val x = colPx * i + colPx / 2f
                    val y = size.height * (1f - (d.amountFen.toFloat() / max).coerceIn(0f, 1f))
                    Offset(x, y)
                }
                val path = Path().apply {
                    points.forEachIndexed { i, p ->
                        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
                points.forEach { p ->
                    drawCircle(color = lineColor, radius = 4.dp.toPx(), center = p)
                }
            }
            Row(
                Modifier
                    .width(chartWidth)
                    .fillMaxHeight(),
            ) {
                daily.forEach { d ->
                    Box(
                        Modifier
                            .width(colWidth)
                            .fillMaxHeight()
                            .clickable { onDayClick(d) },
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            daily.forEach { d ->
                Column(
                    Modifier
                        .width(colWidth)
                        .clickable { onDayClick(d) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "¥${MoneyFormat.fenToYuan(d.amountFen)}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        dayFormat.format(Date(d.dayEpochMs)),
                        color = axisColor,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
