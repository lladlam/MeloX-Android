// Copyright 2026 MeloX contributors
// SPDX-License-Identifier: Apache-2.0
//
// Glass rendering is provided by Kyant0/AndroidLiquidGlass (Backdrop), Apache-2.0.

package com.lladlam.melox.ui.glass

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * Thin MeloX styling layer over Kyant0/AndroidLiquidGlass.
 *
 * The app owns a single LayerBackdrop at the root. Every bottom-chrome surface samples that
 * same recording layer; this function never allocates another backdrop, combined backdrop,
 * or hidden capture layer. Geometry can therefore animate without multiplying capture cost.
 */
internal fun Modifier.meloXLiquidGlass(
    backdrop: Backdrop?,
    shape: Shape,
    tint: Color,
    fallbackTint: Color,
    alpha: Float = 1f,
    blurRadius: Dp = 8.dp,
    refractionHeight: Dp = 12.dp,
    refractionAmount: Dp = 16.dp,
    enableLens: Boolean = true,
    highlightAlpha: Float = 0.16f,
    shadowAlpha: Float = 0.10f,
): Modifier {
    val effectAlpha = alpha.coerceIn(0f, 1f)

    if (backdrop == null) {
        return background(
            color = fallbackTint.copy(alpha = fallbackTint.alpha * effectAlpha),
            shape = shape,
        )
    }

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            if (blurRadius > 0.dp) {
                blur(blurRadius.toPx())
            }
            if (enableLens && refractionHeight > 0.dp && refractionAmount > 0.dp) {
                lens(
                    refractionHeight.toPx(),
                    refractionAmount.toPx(),
                    chromaticAberration = false,
                )
            }
        },
        highlight = {
            Highlight.Default.copy(alpha = highlightAlpha.coerceIn(0f, 1f) * effectAlpha)
        },
        shadow = {
            Shadow(alpha = shadowAlpha.coerceIn(0f, 1f) * effectAlpha)
        },
        layerBlock = {
            this.alpha = effectAlpha
        },
        onDrawSurface = {
            drawRect(tint)
        },
    )
}
