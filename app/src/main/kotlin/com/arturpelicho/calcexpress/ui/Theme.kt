package com.arturpelicho.calcexpress.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.arturpelicho.calcexpress.FontChoice
import com.arturpelicho.calcexpress.R
import com.arturpelicho.calcexpress.ThemeChoice

/** Colours specific to the calculator that Material's scheme does not cover. */
data class CalcColors(
    val background: Color,
    val display: Color,
    val text: Color,
    val muted: Color,
    val keyNumber: Color,
    val keyOperator: Color,
    val keyFunction: Color,
    val keyAction: Color,
    val keyEquals: Color,
    val onKeyEquals: Color,
    val accent: Color,
    /** Outline used for power mode and the active case; contrasts with the theme. */
    val highlight: Color,
    val caseBorder: Color,
    val error: Color,
    val graphLine: Color,
    val graphFill: Color,
    val isDark: Boolean,
)

val ClearColors = CalcColors(
    background = Color(0xFFFBF8F1),
    display = Color(0xFFFFFDF8),
    text = Color(0xFF1B2433),
    muted = Color(0xFF6B7484),
    keyNumber = Color(0xFFFFFFFF),
    keyOperator = Color(0xFFE2EBF8),
    keyFunction = Color(0xFFF2ECDF),
    keyAction = Color(0xFFEDE6D6),
    keyEquals = Color(0xFF2B5DA8),
    onKeyEquals = Color(0xFFFFFFFF),
    accent = Color(0xFF2B5DA8),
    highlight = Color(0xFFD9822B),
    caseBorder = Color(0xFF8FA6C8),
    error = Color(0xFFB3261E),
    graphLine = Color(0xFF2B5DA8),
    graphFill = Color(0x402B5DA8),
    isDark = false,
)

/** OLED: true black backgrounds. */
val DarkColors = CalcColors(
    background = Color(0xFF000000),
    display = Color(0xFF000000),
    text = Color(0xFFEDEFF3),
    muted = Color(0xFF8C93A0),
    keyNumber = Color(0xFF141414),
    keyOperator = Color(0xFF0E1A2C),
    keyFunction = Color(0xFF1B1B1B),
    keyAction = Color(0xFF222222),
    keyEquals = Color(0xFF6EA8FF),
    onKeyEquals = Color(0xFF000000),
    accent = Color(0xFF6EA8FF),
    highlight = Color(0xFFFFB74D),
    caseBorder = Color(0xFF4A5A75),
    error = Color(0xFFFF6B6B),
    graphLine = Color(0xFF6EA8FF),
    graphFill = Color(0x406EA8FF),
    isDark = true,
)

val LocalCalcColors = staticCompositionLocalOf { ClearColors }
val LocalMathFont = staticCompositionLocalOf<FontFamily> { FontFamily.Serif }

val LatexFont = FontFamily(
    Font(R.font.cmu_serif_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.cmu_serif_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.cmu_serif_bold, FontWeight.Bold, FontStyle.Normal),
)

@Composable
fun CalcTheme(theme: ThemeChoice, font: FontChoice, content: @Composable () -> Unit) {
    val dark = when (theme) {
        ThemeChoice.CLEAR -> false
        ThemeChoice.DARK -> true
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = if (dark) DarkColors else ClearColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = Color.Black,
            background = colors.background, surface = colors.background,
            onBackground = colors.text, onSurface = colors.text,
            surfaceVariant = Color(0xFF121212), onSurfaceVariant = colors.muted,
            secondaryContainer = Color(0xFF0E1A2C), onSecondaryContainer = colors.text,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = Color.White,
            background = colors.background, surface = colors.background,
            onBackground = colors.text, onSurface = colors.text,
            surfaceVariant = Color(0xFFF2ECDF), onSurfaceVariant = colors.muted,
            secondaryContainer = Color(0xFFE2EBF8), onSecondaryContainer = colors.text,
        )
    }
    val family = if (font == FontChoice.LATEX) LatexFont else FontFamily.SansSerif
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
    CompositionLocalProvider(LocalCalcColors provides colors, LocalMathFont provides family) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

fun mathStyle(family: FontFamily, size: Float, color: Color, italic: Boolean = false) = TextStyle(
    fontFamily = family,
    fontSize = androidx.compose.ui.unit.TextUnit(size, androidx.compose.ui.unit.TextUnitType.Sp),
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    color = color,
)
