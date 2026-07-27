package com.foldclaw.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 海墨青绿：避开常见紫白 / 奶油赤陶 AI 套皮。 */
object FoldClawColors {
    val Ink = Color(0xFF0E2F36)
    val Teal = Color(0xFF1F7A6C)
    val TealBright = Color(0xFF2BB5A0)
    val Mist = Color(0xFFEEF6F4)
    val MistDeep = Color(0xFFD9EBE6)
    val Foam = Color(0xFFF7FBFA)
    val Sand = Color(0xFFF3EDE4)
    val Alert = Color(0xFFC45C26)
    val OnInk = Color(0xFFF3FAF8)
}

private val LightColors = lightColorScheme(
    primary = FoldClawColors.Teal,
    onPrimary = Color.White,
    primaryContainer = FoldClawColors.MistDeep,
    onPrimaryContainer = FoldClawColors.Ink,
    secondary = FoldClawColors.Ink,
    onSecondary = FoldClawColors.OnInk,
    secondaryContainer = FoldClawColors.Sand,
    onSecondaryContainer = FoldClawColors.Ink,
    tertiary = FoldClawColors.TealBright,
    onTertiary = FoldClawColors.Ink,
    background = FoldClawColors.Mist,
    onBackground = FoldClawColors.Ink,
    surface = FoldClawColors.Foam,
    onSurface = FoldClawColors.Ink,
    surfaceVariant = Color(0xFFE2EFEC),
    onSurfaceVariant = Color(0xFF3D5652),
    outline = Color(0xFF8AA9A2),
    error = FoldClawColors.Alert,
)

private val DarkColors = darkColorScheme(
    primary = FoldClawColors.TealBright,
    onPrimary = FoldClawColors.Ink,
    primaryContainer = Color(0xFF164A44),
    onPrimaryContainer = FoldClawColors.Mist,
    secondary = FoldClawColors.MistDeep,
    onSecondary = FoldClawColors.Ink,
    background = Color(0xFF0A1C20),
    onBackground = FoldClawColors.Foam,
    surface = Color(0xFF12262B),
    onSurface = FoldClawColors.Foam,
    surfaceVariant = Color(0xFF1C3439),
    onSurfaceVariant = Color(0xFFB7CDC8),
    error = Color(0xFFE28B5A),
)

private val FoldTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.2.sp,
    ),
)

private val FoldShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun FoldClawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = FoldTypography,
        shapes = FoldShapes,
        content = content,
    )
}
