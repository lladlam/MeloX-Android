/*
 * Sonify liquid-glass top-bar button (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/widgets/basic/LiquidTopBarButton.kt (master @
 * 209e1aa). The drawBackdrop + vibrancy + blur + lens pipeline, the
 * InteractiveHighlight press/drag tracking, the ClassYaba rim/edge-light and
 * the ring shadow are kept verbatim. Only the theme/setting hooks changed:
 *   isFlamingoInDarkMode -> isSonifyDark
 *   SettingsLibrary.GlassBlurScale -> local glassBlurScale (1f)
 *   LocalTitleBarAccentTint remains MeloX's Sonify composition local.
 */
package com.lladlam.melox.ui.sonify.artist

import android.graphics.BlurMaskFilter
import android.graphics.Path
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.toColorInt
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.lladlam.melox.ui.sonify.LocalTitleBarAccentTint
import com.lladlam.melox.ui.sonify.isSonifyDark
import com.lladlam.melox.ui.sonify.artist.liquid.InteractiveHighlight
import com.lladlam.melox.ui.sonify.artist.liquid.classyabaBlackRim
import com.lladlam.melox.ui.sonify.artist.liquid.classyabaWhiteEdge
import com.lladlam.melox.ui.sonify.artist.liquid.glassBlurScale
import kotlin.math.ceil

/**
 * 顶栏液态玻璃圆钮：42dp 圆，vibrancy + blur + lens 采样 backdrop 内容，
 * 白色边缘光环 + 背后环形投影，按压时放大 2dp 当量、弥散高光跟随按压点；
 * 点击带 Confirm 触感反馈。draggable = true 时按住可拖动：高光跟手、
 * 按钮朝手指方向微倾（≤5%）、松手回弹。
 */
@Composable
internal fun LiquidTopBarButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    icon: Painter,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    iconOffset: DpOffset = DpOffset.Zero,
    buttonHeight: Dp = 42.dp,
    backdropAlpha: () -> Float = { 1f },
    shadowAlpha: () -> Float = { 1f },
    iconTint: Color = Color.Unspecified,
    idleIconTint: Color? = null,
    containerColor: Color = Color.Unspecified,
    draggable: Boolean = false,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val isLightTheme = !isSonifyDark()
    val glassScale = glassBlurScale
    val accentTint = LocalTitleBarAccentTint.current
    val currentOnClick by rememberUpdatedState(onClick)
    val resolvedContainerColor = if (containerColor != Color.Unspecified) containerColor
    else if (isLightTheme) Color(0xFFFFFFFF).copy(alpha = 0.5f)
    else Color(0xFF1C1C1E).copy(alpha = 0.5f)
    val resolvedContentColor =
        if (isLightTheme) Color.Black.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f)
    val edgeLightColor =
        if (isLightTheme) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.32f)
    val rimHighlight: (() -> com.kyant.backdrop.highlight.Highlight?)? =
        if (isLightTheme) classyabaBlackRim else null
    val shadowColor = if (isLightTheme) "#12000000".toColorInt() else "#20000000".toColorInt()
    val interactionSource = remember { MutableInteractionSource() }
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
            onTap = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                currentOnClick()
            }
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                val progress = interactiveHighlight.pressProgress
                val scale = 1f + 6f.dp.toPx() / buttonHeight.toPx() * progress
                scaleX = scale
                scaleY = scale
                val pressOffset = interactiveHighlight.offset
                translationX = size.minDimension * 0.05f * pressOffset.x / size.maxDimension
                translationY = size.minDimension * 0.05f * pressOffset.y / size.maxDimension
            }
            .size(buttonHeight)
            .drawWithCache {
                val path = Path()
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                var lastBlur = -1f
                onDrawBehind {
                    val spread = shadowAlpha()
                    if (spread > 0.01f) {
                        val blurRadius = (10f * density * spread).coerceAtLeast(0.1f)
                        val shadowSpread = 2f * density * spread
                        val outerRadius = size.minDimension / 2f + shadowSpread
                        val innerRadius = size.minDimension / 2f
                        path.rewind()
                        path.addCircle(center.x, center.y, outerRadius, Path.Direction.CW)
                        path.addCircle(center.x, center.y, innerRadius, Path.Direction.CCW)
                        paint.color = android.graphics.Color.argb(
                            (android.graphics.Color.alpha(shadowColor) * 3.2f).coerceAtMost(255f).toInt(),
                            android.graphics.Color.red(shadowColor),
                            android.graphics.Color.green(shadowColor),
                            android.graphics.Color.blue(shadowColor)
                        )
                        if (blurRadius != lastBlur) {
                            lastBlur = blurRadius
                            paint.maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
                        }
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawPath(path, paint)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(buttonHeight)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    role = Role.Button,
                    onClick = {
                        if (!draggable) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            currentOnClick()
                        }
                    }
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx() * glassScale)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            lens(8f.dp.toPx(), 24f.dp.toPx())
                        }
                    },
                    highlight = rimHighlight,
                    shadow = { null },
                    layerBlock = {
                        alpha = backdropAlpha()
                    },
                    onDrawSurface = {
                        drawRect(resolvedContainerColor)
                        if (accentTint != null && accentTint.alpha > 0.005f) {
                            drawRect(accentTint)
                        }
                        drawRect(Color.Black.copy(alpha = 0.03f * interactiveHighlight.pressProgress))
                    }
                )
                .then(
                    if (isLightTheme) Modifier.classyabaWhiteEdge(CircleShape) else Modifier
                )
                .then(
                    if (isLightTheme) Modifier
                    else Modifier.circleEdgeLight(
                        color = edgeLightColor,
                        width = 0.15f.dp,
                        blurRadius = 0.5f.dp
                    )
                )
                .then(interactiveHighlight.modifier)
                .then(if (draggable) interactiveHighlight.gestureModifier else interactiveHighlight.pressOnlyModifier)
                .zIndex(0f)
        )
        val baseIconTint = if (iconTint != Color.Unspecified) iconTint else resolvedContentColor
        val idleTint = idleIconTint
        val iconModifier = Modifier
            .size(iconSize)
            .offset(iconOffset.x, iconOffset.y)
            .zIndex(1f)
            .then(
                if (idleTint != null) {
                    Modifier.graphicsLayer {
                        val presence = backdropAlpha().coerceIn(0f, 1f)
                        colorFilter = ColorFilter.tint(
                            androidx.compose.ui.graphics.lerp(idleTint, baseIconTint, presence)
                        )
                    }
                } else Modifier
            )
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            modifier = iconModifier,
            tint = if (idleTint != null) Color.White else baseIconTint
        )
    }
}

/**
 * Uniform 边缘光的圆形等效实现。
 */
internal fun Modifier.circleEdgeLight(
    color: Color,
    width: Dp,
    blurRadius: Dp
): Modifier = drawWithContent {
    drawContent()
    if (color.alpha <= 0f || width <= 0.dp) return@drawWithContent
    val radius = size.minDimension / 2f
    val strokePx = ceil(width.toPx().coerceAtMost(radius)) * 2f
    val blurPx = blurRadius.toPx()
    val path = Path().apply { addCircle(center.x, center.y, radius, Path.Direction.CW) }
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            this.color = color.toArgb()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = strokePx
            if (blurPx > 0f) {
                maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
            }
            xfermode = android.graphics.PorterDuffXfermode(
                if (color.red + color.green + color.blue < 1.5f) android.graphics.PorterDuff.Mode.SRC_OVER
                else android.graphics.PorterDuff.Mode.ADD
            )
        }
        canvas.save()
        canvas.nativeCanvas.clipPath(path)
        canvas.nativeCanvas.drawCircle(center.x, center.y, radius, paint)
        canvas.restore()
    }
}

/**
 * [circleEdgeLight] 的圆角矩形 / 胶囊版。
 */
internal fun Modifier.capsuleEdgeLight(
    color: Color,
    width: Dp,
    blurRadius: Dp
): Modifier = drawWithContent {
    drawContent()
    if (color.alpha <= 0f || width <= 0.dp) return@drawWithContent
    val radius = size.height / 2f
    val strokePx = ceil(width.toPx().coerceAtMost(radius)) * 2f
    val blurPx = blurRadius.toPx()
    val path = Path().apply {
        addRoundRect(0f, 0f, size.width, size.height, radius, radius, Path.Direction.CW)
    }
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            this.color = color.toArgb()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = strokePx
            if (blurPx > 0f) {
                maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
            }
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
        }
        canvas.save()
        canvas.nativeCanvas.clipPath(path)
        canvas.nativeCanvas.drawPath(path, paint)
        canvas.restore()
    }
}
