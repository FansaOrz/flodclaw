package com.foldledger.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foldledger.domain.model.CaptureSource
import com.foldledger.domain.model.DuplicatePair
import com.foldledger.domain.model.TransactionWithMeta
import com.foldledger.domain.util.MoneyFormat
import com.foldledger.presentation.theme.CategoryColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 全局重复筛查：逐对确认保留哪一笔（另一笔软删），或都保留。
 */
@Composable
fun DuplicateConfirmDialog(
    pairs: List<DuplicatePair>,
    onKeepFirst: (DuplicatePair) -> Unit,
    onKeepSecond: (DuplicatePair) -> Unit,
    onKeepBoth: () -> Unit,
    onDismissAll: () -> Unit,
) {
    if (pairs.isEmpty()) return
    var index by remember(pairs) { mutableStateOf(0) }
    if (index >= pairs.size) {
        onDismissAll()
        return
    }
    val pair = pairs[index]
    val timeFmt = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
    }

    AlertDialog(
        onDismissRequest = onDismissAll,
        title = {
            Text("疑似重复（${index + 1}/${pairs.size}）")
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "匹配原因：${pair.reason}\n请保留其中一笔，或都保留（真的花了两次）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                DuplicateCard(label = "A", item = pair.first, timeFmt = timeFmt)
                DuplicateCard(label = "B", item = pair.second, timeFmt = timeFmt)
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(
                    onClick = {
                        onKeepFirst(pair)
                        index++
                    },
                ) { Text("保留 A，删除 B") }
                TextButton(
                    onClick = {
                        onKeepSecond(pair)
                        index++
                    },
                ) { Text("保留 B，删除 A") }
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        onKeepBoth()
                        index++
                    },
                ) { Text("都保留") }
                TextButton(onClick = onDismissAll) { Text("结束筛查") }
            }
        },
    )
}

@Composable
private fun DuplicateCard(
    label: String,
    item: TransactionWithMeta,
    timeFmt: SimpleDateFormat,
) {
    val tx = item.transaction
    val catColor = CategoryColors.of(item.categoryName)
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "$label · ${sourceLabel(tx.source)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            tx.merchant.ifBlank { "未命名商户" },
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "¥${MoneyFormat.fenToYuan(tx.amountFen)}",
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            timeFmt.format(Date(tx.happenedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Text(
            "账户：${item.accountName} · 分类：${item.categoryName ?: "未分类"}",
            style = MaterialTheme.typography.bodySmall,
            color = catColor,
        )
        if (tx.note.isNotBlank()) {
            Text("备注：${tx.note}", style = MaterialTheme.typography.bodySmall)
        }
        if (!tx.rawText.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                tx.rawText!!.take(120) + if (tx.rawText!!.length > 120) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

private fun sourceLabel(source: CaptureSource): String = when (source) {
    CaptureSource.MANUAL -> "手动"
    CaptureSource.WECHAT_IMPORT -> "微信导入"
    CaptureSource.ALIPAY_IMPORT -> "支付宝导入"
    CaptureSource.WECHAT_NLS, CaptureSource.WECHAT_A11Y -> "微信实时"
    CaptureSource.ALIPAY_NLS, CaptureSource.ALIPAY_A11Y -> "支付宝实时"
    else -> source.name
}
