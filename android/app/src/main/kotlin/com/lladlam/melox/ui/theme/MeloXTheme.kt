package com.lladlam.melox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXThemeMode
import com.lladlam.melox.ui.glass.MeloXTypography as GlassTypography

private val LightColors = lightColorScheme(
    primary = Color(0xFF007AFF),
    error = Color(0xFFFF3B30),
    background = Color(0xFFF7F7FA),
    surface = Color(0xFFFDFDFE),
    onPrimary = Color.White,
    onBackground = Color(0xFF17171A),
    onSurface = Color(0xFF17171A),
    surfaceVariant = Color(0xFFE9E9EE),
    onSurfaceVariant = Color(0xFF5D5D66),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),
    error = Color(0xFFFF453A),
    background = Color(0xFF0B0B0D),
    surface = Color(0xFF151518),
    onPrimary = Color.White,
    onBackground = Color(0xFFF5F5F7),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF252529),
    onSurfaceVariant = Color(0xFFB8B8C0),
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
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
        headlineLarge = GlassTypography.largeTitle,
        headlineMedium = GlassTypography.title2,
        headlineSmall = GlassTypography.headline,
        bodyLarge = GlassTypography.body,
        bodyMedium = GlassTypography.subheadline,
        bodySmall = GlassTypography.caption,
    )
}

private val MeloXShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

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
        shapes = MeloXShapes,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}

/**
 * Returns the app's effective appearance rather than the device appearance.
 * Custom glass surfaces must follow an explicit Light/Dark override too.
 */
@Composable
fun isMeloXDarkTheme(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f
