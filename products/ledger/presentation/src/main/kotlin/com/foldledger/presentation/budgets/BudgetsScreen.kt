package com.foldledger.presentation.budgets

import androidx.compose.ui.Modifier

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.foldledger.domain.model.Budget
import com.foldledger.domain.model.BudgetProgress
import com.foldledger.domain.repo.BudgetRepository
import com.foldledger.domain.util.MoneyFormat
import com.foldledger.domain.util.YearMonths
import com.foldledger.presentation.common.LedgerCard
import com.foldledger.presentation.common.LedgerEmptyState
import com.foldledger.presentation.common.LedgerPageHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgets: BudgetRepository,
) : ViewModel() {
    private val ym = YearMonths.current()
    val items: StateFlow<List<BudgetProgress>> = budgets.observeBudgets(ym)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addTotal(limitYuan: String) {
        viewModelScope.launch {
            val fen = ((limitYuan.toDoubleOrNull() ?: return@launch) * 100).toLong()
            budgets.upsert(Budget(yearMonth = ym, categoryId = null, limitFen = fen))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { budgets.delete(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsRoute(viewModel: BudgetsViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LedgerPageHeader(
                title = "预算计划",
                eyebrow = YearMonths.current(),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.budget.id }) { bp ->
                val ratio = if (bp.budget.limitFen == 0L) 0f else
                    (bp.spentFen.toFloat() / bp.budget.limitFen).coerceAtMost(1.5f)
                LedgerCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(bp.categoryName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${(ratio * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (ratio > 1f) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    Text(
                        "已用 ¥${MoneyFormat.fenToYuan(bp.spentFen)} · 预算 ¥${MoneyFormat.fenToYuan(bp.budget.limitFen)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { ratio.coerceAtMost(1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (ratio > 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    if (ratio > 1f) {
                        Text("已超支", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { viewModel.delete(bp.budget.id) }) { Text("移除预算") }
                }
            }
            if (items.isEmpty()) {
                item {
                    LedgerEmptyState(
                        icon = Icons.Default.Savings,
                        title = "为本月留一道边界",
                        message = "设置一个总预算，消费进度会在这里清晰呈现。",
                    )
                }
            }
        }
    }
    if (showAdd) {
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("本月总预算（元）") },
            text = { OutlinedTextField(amount, { amount = it }, label = { Text("金额") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addTotal(amount)
                    showAdd = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } },
        )
    }
}
