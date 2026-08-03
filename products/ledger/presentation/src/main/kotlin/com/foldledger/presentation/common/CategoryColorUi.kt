package com.foldledger.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldledger.presentation.theme.CategoryColors

@Composable
fun CategoryColorDot(
    name: String?,
    colorArgb: Int? = null,
    size: Dp = 10.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(size)
            .background(CategoryColors.of(name, colorArgb), CircleShape),
    )
}

@Composable
fun CategoryIconBadge(
    name: String?,
    colorArgb: Int? = null,
    size: Dp = 42.dp,
    modifier: Modifier = Modifier,
) {
    val color = CategoryColors.of(name, colorArgb)
    val icon = when (name) {
        "餐饮" -> Icons.Default.Restaurant
        "交通" -> Icons.Default.DirectionsBus
        "购物" -> Icons.Default.ShoppingBag
        "居住" -> Icons.Default.Home
        "运动健身" -> Icons.Default.FitnessCenter
        "娱乐" -> Icons.Default.Movie
        "医疗" -> Icons.Default.LocalHospital
        "通讯" -> Icons.Default.PhoneAndroid
        "转账" -> Icons.Default.SwapHoriz
        "工作" -> Icons.Default.Work
        else -> Icons.Default.Category
    }
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = color.copy(alpha = 0.13f),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = name,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(size * 0.24f),
        )
    }
}

@Composable
fun CategoryNameWithColor(
    name: String,
    colorArgb: Int? = null,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val color = CategoryColors.of(name, colorArgb)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryColorDot(name = name, colorArgb = colorArgb)
        Text(if (trailing != null) "$trailing$name" else name)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryColorPicker(
    categoryName: String,
    selectedArgb: Int?,
    onSelect: (Int?) -> Unit,
) {
    val defaultColor = CategoryColors.of(categoryName, null)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "分类颜色",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ColorSwatch(
                color = defaultColor,
                selected = selectedArgb == null,
                label = "默认",
                onClick = { onSelect(null) },
            )
            CategoryColors.swatches.forEach { swatch ->
                val argb = swatch.toArgb()
                ColorSwatch(
                    color = swatch,
                    selected = selectedArgb == argb,
                    onClick = { onSelect(argb) },
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    label: String? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(28.dp)
                .background(color, CircleShape)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape)
                    },
                )
                .clickable(onClick = onClick),
        )
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}
