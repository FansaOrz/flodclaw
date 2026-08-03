package com.foldledger.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foldledger.domain.model.ReclassifyCandidate
import com.foldledger.domain.util.MoneyFormat
import com.foldledger.presentation.theme.CategoryColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 新增关键词后，逐笔确认是否把匹配到的流水改到目标分类。
 */
@Composable
fun ReclassifyConfirmDialog(
    candidates: List<ReclassifyCandidate>,
    onConfirmOne: (ReclassifyCandidate) -> Unit,
    onSkipOne: () -> Unit,
    onConfirmAll: () -> Unit,
    onDismissAll: () -> Unit,
) {
    if (candidates.isEmpty()) return
    var index by remember(candidates) { mutableStateOf(0) }
    if (index >= candidates.size) {
        onDismissAll()
        return
    }
    val c = candidates[index]
    val tx = c.item.transaction
    val timeFmt = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
    }
    val currentCat = c.item.categoryName ?: "未分类"
    val currentColor = CategoryColors.of(c.item.categoryName)
    val targetColor = CategoryColors.of(c.targetCategoryName)

    AlertDialog(
        onDismissRequest = onDismissAll,
        title = {
            Text("发现可能相关的账单（${index + 1}/${candidates.size}）")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "新增关键词后，这些账单也命中了「${c.targetCategoryName}」。请确认是否改类。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
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
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("当前：", style = MaterialTheme.typography.bodySmall)
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(currentColor, CircleShape),
                        )
                        Text(
                            currentCat,
                            style = MaterialTheme.typography.bodyMedium,
                            color = currentColor,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("建议：", style = MaterialTheme.typography.bodySmall)
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(targetColor, CircleShape),
                        )
                        Text(
                            c.targetCategoryName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = targetColor,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        "命中关键词：${c.matchedKeyword}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmOne(c)
                    index++
                },
            ) { Text("改到「${c.targetCategoryName}」") }
        },
        dismissButton = {
            Row {
                if (candidates.size > 1) {
                    TextButton(
                        onClick = {
                            onConfirmAll()
                            onDismissAll()
                        },
                    ) { Text("全部改类") }
                }
                TextButton(onClick = { index++ }) { Text("跳过") }
                TextButton(onClick = onDismissAll) { Text("全部忽略") }
            }
        },
    )
}
