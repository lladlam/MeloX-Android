package com.lladlam.melox.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lladlam.melox.ui.player.MeloSmoothCornerShape
import com.lladlam.melox.R
import com.lladlam.melox.core.remoteconfig.MeloXHotStyles
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXThemeMode
import com.lladlam.melox.ui.glass.MeloXTypography as GlassTypography
import com.lladlam.melox.ui.theme.MeloXLanTingProFontFamily
import androidx.core.view.WindowCompat

// ⚠ 2026-09-25：由 `private` 放开到 `internal` —— 底部 chrome 需要在**这两套真实
//   配色**之间插值（`lerpColorScheme`），而不是再造一组近似色。
internal val LightColors = lightColorScheme(
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

internal val DarkColors = darkColorScheme(
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

/**
 * In-app MeloX brand logo（`ic_launcher_foreground`）——唯一来源，让所有 logo
 * （欢迎页 / 闪屏 / 播放页占位）都是同一个「应用图标风格」圆角矩形。
 *
 * ⚠⚠ 2026-10-25 修正：PNG 本体是 432×432，**可见红块只占中心 288×288**（四周各 72px
 * 透明边距 = 自适应图标前景的标准留白，像素实测）。所以 `Modifier.size(X).clip(shape)`
 * 裁的是**盒子**的角 —— 红块缩在中心 2/3 里，**根本碰不到裁切**，看上去仍是直角
 * （上一版就是这么失效的：源码改了、APK 也装了，视觉上什么都没变）。
 *
 * ⇒ 正确做法：整图仍按盒子大小画，但把**内容盒**（盒子的 2/3）拿出来裁圆角 ——
 * 内容被放大到贴满内容盒，clip 才真正裁到红块的直角。logo 的显示尺寸不变。
 */

/** PNG 可见内容占其边长的比例（288/432 = 2/3）。 */
private const val MeloXBrandLogoContentFraction = 288f / 432f

/**
 * 内容盒的圆角：用项目统一的**平滑圆角**（Lamé 超椭圆、连续曲率）替代正圆弧。
 *
 * 正圆弧的曲率在切点从 1/R 跳到 0（G2 不连续），肉眼读成「直线突然拐」的硬边；
 * 超椭圆 n=2.5 的切点曲率连续 → 0，过渡是「长出来」的，跟系统圆角/播放页外壳一致的手感。
 * 半径按可见边长的 **20%** 给（比旧版 25% 收敛，圆角不再过大），任何尺寸下比例一致。
 * `MeloSmoothCornerShape` 内部做了尺寸补偿，可见外切深 = 同半径正圆弧，所以 20% 即
 * 等价于一个 20% 的 `RoundedCornerShape` 的观感尺寸。
 */
internal val MeloXBrandLogoShape: Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radiusPx = 0.20f * size.width.coerceAtMost(size.height)
        return MeloSmoothCornerShape(radiusPx).createOutline(size, layoutDirection, density)
    }
}

@Composable
internal fun MeloXBrandLogo(boxSize: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(boxSize), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(boxSize * MeloXBrandLogoContentFraction)
                .clip(MeloXBrandLogoShape),
            contentAlignment = Alignment.Center,
        ) {
            // 整图放大到 boxSize（= 内容盒的 1.5 倍）居中，内容正好贴满内容盒。
            // ⚠ 必须 requiredSize：`size()` 会被内容盒的约束压回 69.33dp（红块又缩回
            //   中心 2/3，等于没修）；requiredSize 才让整图真正溢出、由 clip 裁出圆角。
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.requiredSize(boxSize),
            )
        }
    }
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = LocalView.current
    val fontFamily = rememberMeloXFontFamily(context)
    val hotColors by MeloXHotStyles.colors.collectAsState()
    val baseScheme = if (darkTheme) DarkColors else LightColors
    val colorScheme = remember(baseScheme, hotColors, darkTheme) {
        baseScheme.withHotOverrides(hotColors, darkTheme)
    }
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
            colorScheme = colorScheme,
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
