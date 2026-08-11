/*
 * MeloX Backdrop controls
 *
 * Adapted from the LiquidButton and LiquidBottomTabs examples in
 * Kyant0/AndroidLiquidGlass. Upstream is licensed under Apache-2.0.
 * https://github.com/Kyant0/AndroidLiquidGlass
 */
package com.lladlam.melox.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/** The screen backdrop sampled by all MeloX liquid controls. */
val LocalMeloXBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Official LiquidButton-style glass, generalized so existing MeloX controls
 * keep their exact iOS-derived size, shape and content.
 */
@Composable
fun Modifier.meloXLiquidButton(
    shape: Shape,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    blurRadius: Dp = 2.dp,
    lensRadius: Dp = 12.dp,
    refractionHeight: Dp = 24.dp,
): Modifier {
    val backdrop = LocalMeloXBackdrop.current
    if (backdrop == null) {
        val stableSurface = when {
            surfaceColor != Color.Unspecified -> surfaceColor.copy(
                alpha = maxOf(surfaceColor.alpha, 0.46f),
            )
            tint == Color.Unspecified -> Color.White.copy(alpha = 0.46f)
            else -> tint.copy(alpha = maxOf(tint.alpha, 0.42f))
        }
        return background(stableSurface, shape)
            .border(0.75.dp, Color.White.copy(alpha = 0.62f), shape)
    }
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(blurRadius.toPx())
                lens(
                    lensRadius.toPx(),
                    refractionHeight.toPx(),
                    chromaticAberration = false,
                )
            },
            onDrawSurface = {
                if (tint != Color.Unspecified) {
                    drawRect(tint, blendMode = BlendMode.Hue)
                    drawRect(tint.copy(alpha = tint.alpha * 0.75f))
                }
                if (surfaceColor != Color.Unspecified) drawRect(surfaceColor)
            },
        )
}

/**
 * Plain background blur. Unlike Liquid Glass this applies no lens, refraction
 * or vibrancy; it only blurs the recorded scene and optionally lays a tint.
 */
@Composable
fun Modifier.meloXBackdropBlur(
    shape: Shape,
    blurRadius: Dp = 20.dp,
    surfaceColor: Color = Color.Transparent,
): Modifier {
    val backdrop = LocalMeloXBackdrop.current
    if (backdrop == null) return background(surfaceColor, shape)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = { blur(blurRadius.toPx()) },
        highlight = null,
        shadow = null,
        innerShadow = null,
        onDrawSurface = {
            if (surfaceColor != Color.Transparent) drawRect(surfaceColor)
        },
    )
}

/** Official LiquidBottomTabs-style outer panel. */
@Composable
fun Modifier.meloXLiquidBottomBar(
    shape: Shape,
    tint: Color,
    surfaceColor: Color,
): Modifier {
    val backdrop = LocalMeloXBackdrop.current
    if (backdrop == null) {
        val stableSurface = surfaceColor.copy(alpha = maxOf(surfaceColor.alpha, 0.48f))
        return background(stableSurface, shape)
            .border(0.75.dp, Color.White.copy(alpha = 0.62f), shape)
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(8.dp.toPx())
            lens(24.dp.toPx(), 24.dp.toPx(), chromaticAberration = false)
        },
        onDrawSurface = {
            drawRect(surfaceColor)
        },
    )
}

/** Moving/selected tab lens used inside the bottom panel. */
@Composable
fun Modifier.meloXLiquidTabSelection(
    shape: Shape,
    selected: Boolean,
    tint: Color,
    panelBackdrop: Backdrop? = null,
): Modifier {
    if (!selected) return this
    val backdrop = LocalMeloXBackdrop.current
    if (backdrop == null) {
        return background(tint.copy(alpha = maxOf(tint.alpha, 0.36f)), shape)
            .border(0.5.dp, Color.White.copy(alpha = 0.58f), shape)
    }
    // Official LiquidBottomTabs records the panel into a second Backdrop and
    // samples the combined page + panel scene for the moving selection.
    // Without this, the selected capsule samples page artwork directly and
    // appears skewed or punched through.
    val selectionBackdrop = if (panelBackdrop != null) {
        rememberCombinedBackdrop(backdrop, panelBackdrop)
    } else {
        backdrop
    }
    return drawBackdrop(
        backdrop = selectionBackdrop,
        shape = { shape },
        // In the upstream LiquidBottomTabs demo all selection refraction,
        // highlight and shadows are multiplied by pressProgress. At rest that
        // progress is zero, so keep the stable selected capsule distortion-free.
        effects = {},
        highlight = null,
        shadow = null,
        innerShadow = null,
        onDrawSurface = { drawRect(tint) },
    )
}
