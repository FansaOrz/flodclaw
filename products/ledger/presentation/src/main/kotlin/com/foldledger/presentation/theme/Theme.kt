package com.foldledger.presentation.theme

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

val LedgerInk = Color(0xFF111318)
val LedgerPorcelain = Color(0xFFF7F8F6)
val LedgerMint = Color(0xFF62D6BF)
val LedgerCoral = Color(0xFFFF6F61)
val LedgerAmber = Color(0xFFFFB765)

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B61),
    onPrimary = Color.White,
    primaryContainer = LedgerMint,
    onPrimaryContainer = Color(0xFF0D302C),
    secondary = Color(0xFF4C6460),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDEDE9),
    onSecondaryContainer = Color(0xFF203632),
    tertiary = LedgerCoral,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE2DD),
    onTertiaryContainer = Color(0xFF5C1711),
    background = Color(0xFFF3F5F4),
    onBackground = LedgerInk,
    surface = Color.White,
    onSurface = LedgerInk,
    surfaceVariant = Color(0xFFE9EEEC),
    onSurfaceVariant = Color(0xFF626A68),
    outline = Color(0xFF89918F),
    outlineVariant = Color(0xFFDDE3E1),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FE5D4),
    onPrimary = Color(0xFF00372F),
    primaryContainer = Color(0xFF235F57),
    onPrimaryContainer = Color(0xFFC0F4E9),
    secondary = Color(0xFFB5CCC7),
    onSecondary = Color(0xFF203733),
    secondaryContainer = Color(0xFF354B47),
    onSecondaryContainer = Color(0xFFD1E8E2),
    tertiary = Color(0xFFFFB4A6),
    onTertiary = Color(0xFF670001),
    tertiaryContainer = Color(0xFF8F2820),
    onTertiaryContainer = Color(0xFFFFDAD3),
    background = Color(0xFF101412),
    onBackground = Color(0xFFE4E9E6),
    surface = Color(0xFF1A1F1D),
    onSurface = Color(0xFFE4E9E6),
    surfaceVariant = Color(0xFF252C29),
    onSurfaceVariant = Color(0xFFC1C9C5),
    outline = Color(0xFF8A938F),
    outlineVariant = Color(0xFF37403C),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 23.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun FoldLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
