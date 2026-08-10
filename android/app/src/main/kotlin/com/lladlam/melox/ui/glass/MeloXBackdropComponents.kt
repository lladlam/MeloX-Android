/*
 * MeloX Backdrop controls
 *
 * Adapted from the LiquidButton and LiquidBottomTabs examples in
 * Kyant0/AndroidLiquidGlass. Upstream is licensed under Apache-2.0.
 * https://github.com/Kyant0/AndroidLiquidGlass
 */
package com.lladlam.melox.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch

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
    val backdrop = LocalMeloXBackdrop.current ?: return this
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f, 0.001f) }

    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(blurRadius.toPx())
                lens(lensRadius.toPx(), refractionHeight.toPx())
            },
            highlight = {
                Highlight.Default.copy(alpha = 0.45f + press.value * 0.55f)
            },
            shadow = {
                Shadow(radius = 4.dp, alpha = 0.16f + press.value * 0.18f)
            },
            innerShadow = {
                InnerShadow(
                    radius = 5.dp * press.value,
                    alpha = press.value,
                )
            },
            layerBlock = {
                // Match the official LiquidButton example: a small glass-layer
                // expansion, without a second inverse graphicsLayer scale.
                val scale = lerp(1f, 1.04f, press.value)
                scaleX = scale
                scaleY = scale
            },
            onDrawSurface = {
                if (tint != Color.Unspecified) {
                    drawRect(tint, blendMode = BlendMode.Hue)
                    drawRect(tint.copy(alpha = tint.alpha * 0.75f))
                }
                if (surfaceColor != Color.Unspecified) drawRect(surfaceColor)
            },
        )
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                scope.launch { press.animateTo(1f, spring(0.55f, 420f, 0.001f)) }
                waitForUpOrCancellation()
                scope.launch { press.animateTo(0f, spring(0.68f, 360f, 0.001f)) }
            }
        }
}

/** Official LiquidBottomTabs-style outer panel. */
@Composable
fun Modifier.meloXLiquidBottomBar(
    shape: Shape,
    tint: Color,
    surfaceColor: Color,
): Modifier {
    val backdrop = LocalMeloXBackdrop.current ?: return this
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(8.dp.toPx())
            lens(24.dp.toPx(), 24.dp.toPx())
        },
        highlight = { Highlight.Ambient },
        shadow = { Shadow(radius = 6.dp, alpha = 0.20f) },
        onDrawSurface = {
            drawRect(tint, blendMode = BlendMode.Hue)
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
    val sceneBackdrop = LocalMeloXBackdrop.current ?: return this
    val backdrop = if (panelBackdrop != null) {
        rememberCombinedBackdrop(sceneBackdrop, panelBackdrop)
    } else {
        sceneBackdrop
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            // The official selected lens has virtually no refraction at rest;
            // strong chromatic refraction is only introduced while dragging.
            lens(0.5.dp.toPx(), 1.dp.toPx(), chromaticAberration = false)
        },
        highlight = { Highlight.Ambient },
        shadow = { Shadow(radius = 3.dp, alpha = 0.12f) },
        innerShadow = { InnerShadow(radius = 3.dp, alpha = 0.20f) },
        onDrawSurface = { drawRect(tint) },
    )
}
