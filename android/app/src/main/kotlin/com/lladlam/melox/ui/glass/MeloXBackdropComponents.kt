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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.lladlam.melox.ui.theme.isMeloXDarkTheme
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.glass.publicdemo.PublicInteractiveHighlight
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/** The screen backdrop sampled by all MeloX liquid controls. */
val LocalMeloXBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Shared interaction state for a liquid surface and the content that rides on it. */
class MeloXLiquidInteraction internal constructor(
    internal val highlight: PublicInteractiveHighlight,
)

@Composable
fun rememberMeloXLiquidInteraction(): MeloXLiquidInteraction {
    val animationScope = rememberCoroutineScope()
    return remember(animationScope) {
        MeloXLiquidInteraction(PublicInteractiveHighlight(animationScope))
    }
}

/**
 * Official LiquidButton-style glass, generalized so existing MeloX controls
 * keep their exact iOS-derived size, shape and content.
 */
@Composable
fun Modifier.meloXLiquidButton(
    shape: Shape,
    material: MeloXGlassMaterial = MeloXGlassMaterial.Regular,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    blurRadius: Dp? = null,
    lensRadius: Dp? = null,
    refractionHeight: Dp? = null,
    interaction: MeloXLiquidInteraction? = null,
): Modifier {
    val baseSpec = MeloXGlassSpec.forMaterial(material)
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = interaction?.highlight ?: remember(animationScope) {
        PublicInteractiveHighlight(animationScope)
    }
    return meloXGlassSurface(
        shape = shape,
        material = material,
        enabled = enabled,
        tint = tint,
        surfaceColor = surfaceColor,
        spec = baseSpec.copy(
            blurRadius = blurRadius ?: baseSpec.blurRadius,
            lensRadius = lensRadius ?: baseSpec.lensRadius,
            refractionHeight = refractionHeight ?: baseSpec.refractionHeight,
            // Apple reserves the clear variant for visually rich media.
            // Regular controls use blur/vibrancy without the lens distortion.
            useLens = true,
        ),
        pressProgress = if (enabled) interactiveHighlight.pressProgress else 0f,
        dragOffset = if (enabled) interactiveHighlight.offset else Offset.Zero,
    ).then(if (enabled && !MeloXSettingsRuntime.frostedGlassEnabled) interactiveHighlight.modifier else Modifier)
        .then(if (enabled && !MeloXSettingsRuntime.frostedGlassEnabled) interactiveHighlight.gestureModifier else Modifier)
}

/** Applies the same optical press/drag transform to content placed over liquid glass. */
fun Modifier.meloXLiquidContentTransform(interaction: MeloXLiquidInteraction): Modifier =
    graphicsLayer {
        val controlHeight = size.height.coerceAtLeast(1f)
        val dragOffset = interaction.highlight.offset
        val pressProgress = interaction.highlight.pressProgress
        val baseScale = 1f + (4.dp.toPx() / controlHeight) * pressProgress
        val maxOffset = size.minDimension.coerceAtLeast(1f)
        translationX = maxOffset * tanh(0.05f * dragOffset.x / maxOffset)
        translationY = maxOffset * tanh(0.05f * dragOffset.y / maxOffset)
        val maxDragScale = 4.dp.toPx() / controlHeight
        val angle = atan2(dragOffset.y, dragOffset.x)
        scaleX = baseScale + maxDragScale * abs(cos(angle) * dragOffset.x / size.maxDimension.coerceAtLeast(1f)) *
            (size.width / controlHeight).fastCoerceAtMost(1f)
        scaleY = baseScale + maxDragScale * abs(sin(angle) * dragOffset.y / size.maxDimension.coerceAtLeast(1f)) *
            (controlHeight / size.width.coerceAtLeast(1f)).fastCoerceAtMost(1f)
    }

/** Shared material entry point for all Native Component-style controls. */
@Composable
fun Modifier.meloXGlassSurface(
    shape: Shape,
    material: MeloXGlassMaterial = MeloXGlassMaterial.Regular,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    pressProgress: Float = 0f,
    dragOffset: Offset = Offset.Zero,
    spec: MeloXGlassSpec = MeloXGlassSpec.forMaterial(material),
): Modifier {
    val backdrop = LocalMeloXBackdrop.current
    val alphaScale = if (enabled) 1f else 0.48f
    val isPlain = surfaceColor == Color.Transparent && tint == Color.Unspecified
    val dark = isMeloXDarkTheme()
    if (isPlain) return this
    if (backdrop == null) {
        // Keep explicit translucency intact. The previous fallback raised a
        // 5% white tint to 72%, turning every regular glass field into a solid
        // gray slab when no sampled backdrop was available.
        val defaultAlpha = if (dark) 0.84f else 0.88f
        val tintAlphaFloor = if (dark) 0.18f else 0.22f
        val stableSurface = when {
            surfaceColor != Color.Unspecified -> surfaceColor.copy(
                alpha = surfaceColor.alpha * alphaScale,
            )
            tint == Color.Unspecified -> MaterialTheme.colorScheme.surface.copy(alpha = defaultAlpha * alphaScale)
            else -> tint.copy(alpha = maxOf(tint.alpha, tintAlphaFloor) * alphaScale)
        }
        return background(stableSurface, shape)
    }
    if (MeloXSettingsRuntime.frostedGlassEnabled) {
        return drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(spec.blurRadius.toPx()) },
            highlight = null,
            shadow = null,
            innerShadow = null,
            onDrawSurface = {
                if (tint != Color.Unspecified) drawRect(tint.copy(alpha = tint.alpha * alphaScale))
                if (surfaceColor != Color.Unspecified) drawRect(surfaceColor.copy(alpha = surfaceColor.alpha * alphaScale))
            },
        )
    }
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(spec.blurRadius.toPx())
            if (spec.useLens) {
                lens(
                    spec.lensRadius.toPx(),
                    spec.refractionHeight.toPx(),
                    depthEffect = pressProgress > 0.01f,
                    chromaticAberration = true,
                )
            }
        },
        highlight = {
            Highlight.Default.copy(
                alpha = ((if (dark) 0.32f else 0.48f) + 0.30f * pressProgress)
                    .coerceAtMost(1f),
            )
        },
        shadow = {
            Shadow(
                radius = 24.dp,
                color = Color.Black.copy(alpha = 0.12f),
                alpha = (0.08f + 0.22f * pressProgress) * if (enabled) 1f else 0.35f,
            )
        },
        innerShadow = {
            InnerShadow(
                radius = 4.dp + 8.dp * pressProgress,
                color = Color.Black.copy(alpha = 0.12f),
                alpha = (0.10f + 0.30f * pressProgress) * if (enabled) 1f else 0.35f,
            )
        },
        layerBlock = {
            val controlHeight = size.height.coerceAtLeast(1f)
            val scale = 1f + (4.dp.toPx() / controlHeight) * pressProgress
            val maxOffset = size.minDimension.coerceAtLeast(1f)
            translationX = maxOffset * tanh(0.05f * dragOffset.x / maxOffset)
            translationY = maxOffset * tanh(0.05f * dragOffset.y / maxOffset)
            val maxDragScale = 4.dp.toPx() / controlHeight
            val angle = atan2(dragOffset.y, dragOffset.x)
            scaleX = scale + maxDragScale * abs(cos(angle) * dragOffset.x / size.maxDimension.coerceAtLeast(1f)) *
                (size.width / controlHeight).fastCoerceAtMost(1f)
            scaleY = scale + maxDragScale * abs(sin(angle) * dragOffset.y / size.maxDimension.coerceAtLeast(1f)) *
                (controlHeight / size.width.coerceAtLeast(1f)).fastCoerceAtMost(1f)
        },
        onDrawSurface = {
            drawRect(
                Color.White.copy(alpha = if (dark) 0.045f else 0.12f),
                blendMode = BlendMode.Screen,
            )
            if (pressProgress > 0.001f) {
                drawRect(Color.White.copy(alpha = 0.08f * pressProgress), blendMode = BlendMode.Plus)
            }
            if (tint != Color.Unspecified && tint.alpha > 0.001f) {
                drawRect(tint.copy(alpha = tint.alpha * alphaScale), blendMode = BlendMode.Hue)
                drawRect(tint.copy(alpha = tint.alpha * 0.75f * alphaScale))
            }
            if (surfaceColor != Color.Unspecified) {
                drawRect(surfaceColor.copy(alpha = surfaceColor.alpha * alphaScale))
            }
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

/**
 * Standard content-layer material. Apple explicitly separates this from
 * Liquid Glass: lists, settings groups and content cards should provide
 * distinction without becoming another floating functional layer.
 */
@Composable
fun Modifier.meloXContentSurface(
    shape: Shape,
    surfaceColor: Color = Color.Unspecified,
): Modifier {
    val color = if (surfaceColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surface
    } else {
        surfaceColor
    }
    return background(color, shape)
}

/** Official LiquidBottomTabs-style outer panel. */
@Composable
fun Modifier.meloXLiquidBottomBar(
    shape: Shape,
    tint: Color,
    surfaceColor: Color,
    pressProgress: Float = 0f,
): Modifier {
    val backdrop = LocalMeloXBackdrop.current
    if (backdrop == null) {
        // Flatten the requested translucent material over the current page
        // color. Raising a dark tint to a fixed 48% made light segmented
        // controls look charcoal instead of iOS's subtle neutral fill.
        val stableSurface = surfaceColor.compositeOver(MaterialTheme.colorScheme.background)
        return background(stableSurface, shape)
    }
    if (MeloXSettingsRuntime.frostedGlassEnabled) {
        return drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(8.dp.toPx()) },
            highlight = null,
            shadow = null,
            innerShadow = null,
            onDrawSurface = { drawRect(surfaceColor) },
        )
    }
    val dark = isMeloXDarkTheme()
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(8.dp.toPx())
            lens(24.dp.toPx(), 28.dp.toPx(), chromaticAberration = true)
        },
        highlight = {
            Highlight.Default.copy(alpha = ((if (dark) 0.32f else 0.54f) + 0.38f * pressProgress).coerceAtMost(1f))
        },
        shadow = {
            Shadow(radius = 24.dp, color = Color.Black.copy(alpha = 0.10f), alpha = 0.10f)
        },
        innerShadow = {
            InnerShadow(
                radius = 4.dp + 6.dp * pressProgress,
                color = Color.Black.copy(alpha = 0.12f),
                alpha = 0.10f + 0.18f * pressProgress,
            )
        },
        layerBlock = {
            val scale = 1f + 16.dp.toPx() / size.width.coerceAtLeast(1f) * pressProgress
            scaleX = scale
            scaleY = scale
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
    pressProgress: Float = 0f,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    velocity: Float = 0f,
): Modifier {
    if (!selected) return this
    val backdrop = LocalMeloXBackdrop.current
    if (backdrop == null) {
        return background(tint.copy(alpha = maxOf(tint.alpha, 0.36f)), shape)
    }
    if (MeloXSettingsRuntime.frostedGlassEnabled) {
        return drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(8.dp.toPx()) },
            highlight = null,
            shadow = null,
            innerShadow = null,
            onDrawSurface = { drawRect(tint) },
        )
    }
    // Official LiquidBottomTabs records the panel into a second Backdrop and
    // samples the combined page + panel scene for the moving selection.
    val selectionBackdrop = if (panelBackdrop != null) {
        rememberCombinedBackdrop(backdrop, panelBackdrop)
    } else {
        backdrop
    }
    return drawBackdrop(
        backdrop = selectionBackdrop,
        shape = { shape },
        effects = {
            lens(
                14.dp.toPx() * pressProgress,
                22.dp.toPx() * pressProgress,
                chromaticAberration = true,
            )
        },
        highlight = { Highlight.Default.copy(alpha = 0.90f * pressProgress) },
        shadow = { Shadow(alpha = 0.84f * pressProgress) },
        innerShadow = {
            InnerShadow(radius = 10.dp * pressProgress, alpha = 0.86f * pressProgress)
        },
        layerBlock = {
            this.scaleX = scaleX
            this.scaleY = scaleY
            val normalizedVelocity = velocity / 10f
            this.scaleX /= 1f - (normalizedVelocity * 0.75f).coerceIn(-0.2f, 0.2f)
            this.scaleY *= 1f - (normalizedVelocity * 0.25f).coerceIn(-0.2f, 0.2f)
        },
        onDrawSurface = {
            drawRect(tint, alpha = 1f - pressProgress)
            drawRect(Color.Black.copy(alpha = 0.03f * pressProgress))
        },
    )
}
