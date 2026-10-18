package com.riffado.meetings.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Apple Human Interface Guidelines inspired palette (iOS 26 system colors).
 * We deliberately do NOT use Material You dynamic color: the app should look the same on every phone.
 */
@Immutable
data class AppleColors(
    val isDark: Boolean,
    /** systemGroupedBackground */
    val background: Color,
    /** secondarySystemGroupedBackground: inset list cells. */
    val cell: Color,
    /** tertiarySystemGroupedBackground: fields inside cells. */
    val cellElevated: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val fill: Color,
    val tint: Color,
    val red: Color,
    val green: Color,
    val orange: Color,
    val purple: Color,
    val teal: Color,
    val indigo: Color,
    val pink: Color,
    /** Base colour mixed into frosted glass surfaces. */
    val glassTint: Color,
    /** Opaque-ish colour used when real blur is unavailable (API < 31). */
    val glassFallback: Color,
    val glassStroke: Color,
    val cardGlass: Color,
)

val LightApple = AppleColors(
    isDark = false,
    background = Color(0xFFF2F2F7),
    cell = Color(0xFFFFFFFF),
    cellElevated = Color(0xFFF2F2F7),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    fill = Color(0x1F787880),
    tint = Color(0xFF007AFF),
    red = Color(0xFFFF3B30),
    green = Color(0xFF34C759),
    orange = Color(0xFFFF9500),
    purple = Color(0xFFAF52DE),
    teal = Color(0xFF30B0C7),
    indigo = Color(0xFF5856D6),
    pink = Color(0xFFFF2D55),
    glassTint = Color(0x8CFFFFFF),
    glassFallback = Color(0xEBF9F9FB),
    glassStroke = Color(0xFFFFFFFF),
    cardGlass = Color(0xB8FFFFFF),
)

val DarkApple = AppleColors(
    isDark = true,
    background = Color(0xFF000000),
    cell = Color(0xFF1C1C1E),
    cellElevated = Color(0xFF2C2C2E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0x99545458),
    fill = Color(0x3D767680),
    tint = Color(0xFF0A84FF),
    red = Color(0xFFFF453A),
    green = Color(0xFF30D158),
    orange = Color(0xFFFF9F0A),
    purple = Color(0xFFBF5AF2),
    teal = Color(0xFF40C8E0),
    indigo = Color(0xFF5E5CE6),
    pink = Color(0xFFFF375F),
    glassTint = Color(0x661C1C1E),
    glassFallback = Color(0xF01C1C1E),
    glassStroke = Color(0xFFFFFFFF),
    cardGlass = Color(0x5C2C2C2E),
)

val LocalAppleColors = staticCompositionLocalOf { LightApple }

/** iOS Dynamic Type "Large" scale, approximated with the platform font. */
object AppleType {
    private val tight = (-0.02).em
    val largeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = tight)
    val title1 = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = tight)
    val title2 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = tight)
    val title3 = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = tight)
    val headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.01).em)
    val callout = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
    val subheadline = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val caption2 = TextStyle(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium)
    val timer = TextStyle(fontSize = 72.sp, lineHeight = 80.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.03).em)
}

private val AppleTypography = Typography(
    displayLarge = AppleType.timer,
    displayMedium = AppleType.largeTitle.copy(fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.Light),
    displaySmall = AppleType.largeTitle,
    headlineLarge = AppleType.largeTitle,
    headlineMedium = AppleType.title1,
    headlineSmall = AppleType.title2,
    titleLarge = AppleType.title3,
    titleMedium = AppleType.headline,
    titleSmall = AppleType.subheadline.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = AppleType.body,
    bodyMedium = AppleType.callout,
    bodySmall = AppleType.footnote,
    labelLarge = AppleType.subheadline.copy(fontWeight = FontWeight.Medium),
    labelMedium = AppleType.footnote.copy(fontWeight = FontWeight.Medium),
    labelSmall = AppleType.caption2,
)

private fun AppleColors.toMaterial() = if (isDark) {
    darkColorScheme(
        primary = tint, onPrimary = Color.White, primaryContainer = tint.copy(alpha = 0.22f), onPrimaryContainer = label,
        secondary = indigo, onSecondary = Color.White, secondaryContainer = cellElevated, onSecondaryContainer = label,
        tertiary = orange, onTertiary = Color.Black,
        error = red, onError = Color.White, errorContainer = red.copy(alpha = 0.2f),
        background = background, onBackground = label,
        surface = cell, onSurface = label, surfaceVariant = cellElevated, onSurfaceVariant = secondaryLabel,
        surfaceContainerLowest = background, surfaceContainerLow = cell, surfaceContainer = cell,
        surfaceContainerHigh = cellElevated, surfaceContainerHighest = cellElevated,
        outline = separator, outlineVariant = separator,
    )
} else {
    lightColorScheme(
        primary = tint, onPrimary = Color.White, primaryContainer = tint.copy(alpha = 0.14f), onPrimaryContainer = label,
        secondary = indigo, onSecondary = Color.White, secondaryContainer = cellElevated, onSecondaryContainer = label,
        tertiary = orange, onTertiary = Color.White,
        error = red, onError = Color.White, errorContainer = red.copy(alpha = 0.14f),
        background = background, onBackground = label,
        surface = cell, onSurface = label, surfaceVariant = cellElevated, onSurfaceVariant = secondaryLabel,
        surfaceContainerLowest = Color.White, surfaceContainerLow = cell, surfaceContainer = cell,
        surfaceContainerHigh = cell, surfaceContainerHighest = cellElevated,
        outline = separator, outlineVariant = separator,
    )
}

@Composable
fun MeetingsTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val apple = if (darkTheme) DarkApple else LightApple
    CompositionLocalProvider(LocalAppleColors provides apple) {
        MaterialTheme(colorScheme = apple.toMaterial(), typography = AppleTypography, content = content)
    }
}

/** Shorthand used across the UI: `Apple.colors.tint`. */
object Apple {
    val colors: AppleColors
        @Composable get() = LocalAppleColors.current
}
