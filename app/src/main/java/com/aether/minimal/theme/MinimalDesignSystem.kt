package com.aether.minimal.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class MinimalColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHover: Color,
    val borderHairline: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentSubtle: Color,
    val accentMuted: Color,
    val success: Color,
    val successSubtle: Color,
    val warning: Color,
    val warningSubtle: Color,
    val error: Color,
    val errorSubtle: Color,
    val codeBackground: Color,
    val codeGutter: Color,
    val terminalBackground: Color,
    val chipBackground: Color
)

val DarkMinimalColors = MinimalColors(
    isDark = true,
    background = Color(0xFF0C0E14),
    surface = Color(0xFF141722),
    surfaceElevated = Color(0xFF1B2030),
    surfaceHover = Color(0xFF22283B),
    borderHairline = Color(0xFF252B3C),
    borderSubtle = Color(0x336366F1),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textTertiary = Color(0xFF64748B),
    accent = Color(0xFF6366F1),
    accentSubtle = Color(0x1F6366F1),
    accentMuted = Color(0xFF818CF8),
    success = Color(0xFF10B981),
    successSubtle = Color(0x1A10B981),
    warning = Color(0xFFF59E0B),
    warningSubtle = Color(0x1AF59E0B),
    error = Color(0xFFEF4444),
    errorSubtle = Color(0x1AEF4444),
    codeBackground = Color(0xFF0E111A),
    codeGutter = Color(0xFF171B28),
    terminalBackground = Color(0xFF090A0E),
    chipBackground = Color(0xFF181D2A)
)

val LightMinimalColors = MinimalColors(
    isDark = false,
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF1F5F9),
    surfaceHover = Color(0xFFE2E8F0),
    borderHairline = Color(0xFFE2E8F0),
    borderSubtle = Color(0x224F46E5),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textTertiary = Color(0xFF94A3B8),
    accent = Color(0xFF4F46E5),
    accentSubtle = Color(0x144F46E5),
    accentMuted = Color(0xFF6366F1),
    success = Color(0xFF059669),
    successSubtle = Color(0x14059669),
    warning = Color(0xFFD97706),
    warningSubtle = Color(0x14D97706),
    error = Color(0xFFDC2626),
    errorSubtle = Color(0x14DC2626),
    codeBackground = Color(0xFFF8FAFC),
    codeGutter = Color(0xFFEDF2F7),
    terminalBackground = Color(0xFF0F172A),
    chipBackground = Color(0xFFF1F5F9)
)

object MinimalDimens {
    val hairline = 0.75.dp
    val borderStandard = 1.dp
    val radiusXs = 4.dp
    val radiusSm = 8.dp
    val radiusMd = 12.dp
    val radiusLg = 16.dp
    val radiusXl = 24.dp
    val radiusPill = 999.dp

    val padXs = 4.dp
    val padSm = 8.dp
    val padMd = 12.dp
    val padStandard = 16.dp
    val padLg = 20.dp
    val padXl = 24.dp

    val minTouchTarget = 48.dp
}

object MinimalTypography {
    val largeTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp
    )

    val title = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    )

    val headline = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp
    )

    val body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )

    val subhead = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )

    val footnote = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )

    val caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )

    val codeMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )

    val codeSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    )
}

val LocalMinimalColors = staticCompositionLocalOf { DarkMinimalColors }

@Composable
fun MinimalAetherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkMinimalColors else LightMinimalColors

    val materialColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            background = colors.background,
            onBackground = colors.textPrimary
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            background = colors.background,
            onBackground = colors.textPrimary
        )
    }

    CompositionLocalProvider(
        LocalMinimalColors provides colors
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}
