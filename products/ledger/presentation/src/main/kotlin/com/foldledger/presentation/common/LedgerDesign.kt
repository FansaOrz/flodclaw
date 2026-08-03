package com.foldledger.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foldledger.presentation.theme.LedgerInk
import com.foldledger.presentation.theme.LedgerMint
import com.foldledger.presentation.theme.LedgerPorcelain
import com.foldledger.presentation.theme.LedgerCoral

@Composable
fun FoldLedgerMark(
    modifier: Modifier = Modifier,
    darkBackground: Boolean = true,
) {
    val background = if (darkBackground) LedgerMint else LedgerPorcelain
    val panel = if (darkBackground) LedgerPorcelain else LedgerInk
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = background,
    ) {
        Canvas(Modifier.size(48.dp).padding(9.dp)) {
            val width = size.width
            val height = size.height
            val leftPage = Path().apply {
                moveTo(width * 0.08f, height * 0.24f)
                quadraticTo(width * 0.28f, height * 0.14f, width * 0.47f, height * 0.30f)
                lineTo(width * 0.47f, height * 0.84f)
                quadraticTo(width * 0.28f, height * 0.70f, width * 0.08f, height * 0.77f)
                close()
            }
            val rightPage = Path().apply {
                moveTo(width * 0.53f, height * 0.30f)
                quadraticTo(width * 0.72f, height * 0.14f, width * 0.92f, height * 0.24f)
                lineTo(width * 0.92f, height * 0.77f)
                quadraticTo(width * 0.72f, height * 0.70f, width * 0.53f, height * 0.84f)
                close()
            }
            listOf(leftPage, rightPage).forEach { page ->
                drawPath(page, panel)
                drawPath(
                    path = page,
                    color = LedgerInk,
                    style = Stroke(
                        width = width * 0.065f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
            val flow = Path().apply {
                moveTo(width * 0.19f, height * 0.48f)
                lineTo(width * 0.35f, height * 0.48f)
                cubicTo(
                    width * 0.46f,
                    height * 0.48f,
                    width * 0.48f,
                    height * 0.62f,
                    width * 0.61f,
                    height * 0.62f,
                )
                lineTo(width * 0.78f, height * 0.62f)
            }
            drawPath(
                path = flow,
                color = LedgerInk,
                style = Stroke(width = width * 0.055f, cap = StrokeCap.Round),
            )
            drawCircle(
                color = LedgerCoral,
                radius = width * 0.055f,
                center = androidx.compose.ui.geometry.Offset(width * 0.79f, height * 0.62f),
            )
        }
    }
}

@Composable
fun LedgerPageHeader(
    title: String,
    eyebrow: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun LedgerMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    supporting: String? = null,
) {
    LedgerCard(modifier = modifier) {
        Box(
            Modifier
                .background(accent, CircleShape)
                .padding(horizontal = 12.dp, vertical = 3.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        supporting?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LedgerEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(16.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
