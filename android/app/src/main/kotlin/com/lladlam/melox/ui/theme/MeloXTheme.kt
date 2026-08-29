package com.lladlam.melox.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXThemeMode
import com.lladlam.melox.ui.glass.MeloXTypography as GlassTypography
import com.lladlam.melox.ui.theme.MeloXLanTingProFontFamily
import androidx.core.view.WindowCompat

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
    val family = MeloXLanTingProFontFamily
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = LocalView.current
    val fontFamily = rememberMeloXFontFamily(context)
    SideEffect {
        context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
    CompositionLocalProvider(LocalMeloXFontFamily provides fontFamily) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = MeloXTypography.copyWithFamily(fontFamily),
            shapes = MeloXShapes,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                content = content,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Typography.copyWithFamily(family: FontFamily): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

/**
 * Returns the app's effective appearance rather than the device appearance.
 * Custom glass surfaces must follow an explicit Light/Dark override too.
 */
@Composable
fun isMeloXDarkTheme(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f
