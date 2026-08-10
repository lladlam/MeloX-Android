package com.lladlam.melox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFFE5484D),
    background = Color(0xFFF7F7FA),
    surface = Color(0xFFFDFDFE),
    onPrimary = Color.White,
    onBackground = Color(0xFF17171A),
    onSurface = Color(0xFF17171A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF6369),
    background = Color(0xFF0B0B0D),
    surface = Color(0xFF151518),
    onPrimary = Color.White,
    onBackground = Color(0xFFF5F5F7),
    onSurface = Color(0xFFF5F5F7),
)

// SF Pro cannot be redistributed in an Android app. Use Android's licensed
// system sans family as one app-wide typeface, including its CJK fallbacks,
// while retaining MeloX's iOS-derived sizes, weights, and line heights.
private val MeloXTypography = Typography().let { base ->
    val family = FontFamily.SansSerif
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
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
}

@Composable
fun MeloXTheme(
    darkTheme: Boolean = when (MeloXSettingsRuntime.themeMode) {
        MeloXThemeMode.System -> isSystemInDarkTheme()
        MeloXThemeMode.Light -> false
        MeloXThemeMode.Dark -> true
    },
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MeloXTypography,
        content = content,
    )
}
