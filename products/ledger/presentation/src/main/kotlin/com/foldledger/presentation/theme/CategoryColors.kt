package com.foldledger.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** 支出分类配色：优先用户自定义，否则按名称表/稳定哈希。 */
object CategoryColors {
    private val palette = listOf(
        Color(0xFF2A9D8F),
        Color(0xFFE76F51),
        Color(0xFF457B9D),
        Color(0xFFE9C46A),
        Color(0xFF9B5DE5),
        Color(0xFFF15BB5),
        Color(0xFF00BBF9),
        Color(0xFF00F5D4),
        Color(0xFFF77F00),
        Color(0xFF6A4C93),
        Color(0xFF2D6A4F),
        Color(0xFFBC4749),
    )

    private val named = mapOf(
        "餐饮" to Color(0xFFE76F51),
        "交通" to Color(0xFF457B9D),
        "购物" to Color(0xFF9B5DE5),
        "居住" to Color(0xFF2A9D8F),
        "运动健身" to Color(0xFF2D6A4F),
        "娱乐" to Color(0xFFF15BB5),
        "医疗" to Color(0xFFBC4749),
        "通讯" to Color(0xFF00BBF9),
        "转账" to Color(0xFFE9C46A),
        "其他" to Color(0xFF8D99AE),
        "未分类" to Color(0xFFADB5BD),
        "生活" to Color(0xFF4EA8DE),
        "工作" to Color(0xFF577590),
    )

    /** 编辑分类时可选色板（默认色 + 扩展色）。 */
    val swatches: List<Color> = (
        named.values + palette + listOf(
            Color(0xFF264653),
            Color(0xFF8AB17D),
            Color(0xFFD62828),
            Color(0xFFFB8500),
            Color(0xFF023E8A),
            Color(0xFF7B2CBF),
        )
        ).distinctBy { it.toArgb() }

    fun of(categoryName: String?, colorArgb: Int? = null): Color {
        if (colorArgb != null) return Color(colorArgb)
        val name = categoryName?.ifBlank { null } ?: "未分类"
        named[name]?.let { return it }
        val idx = (name.hashCode().and(0x7fffffff)) % palette.size
        return palette[idx]
    }

    fun defaultArgb(categoryName: String?): Int = of(categoryName, null).toArgb()
}
