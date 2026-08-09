package com.lladlam.melox.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.lladlam.melox.ui.glass.meloXLiquidGlass

private val MeloXBottomAccent = Color(0xFFFF3147)

/**
 * Floating MeloX bottom chrome.
 *
 * Performance rule: all surfaces below sample the one LayerBackdrop owned by MeloXApp.
 * There is no nested/combined backdrop and no hidden secondary capture layer.
 */
@Composable
internal fun MeloXBottomChrome(
    selectedTab: AppTab,
    onSelect: (AppTab) -> Unit,
    hasMedia: Boolean,
    minimized: Boolean,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    miniPlayer: @Composable () -> Unit,
) {
    val rawProgress by animateFloatAsState(
        targetValue = if (minimized) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.90f,
            stiffness = 330f,
            visibilityThreshold = 0.001f,
        ),
        label = "melox-tab-minimize-progress",
    )
    val progress = rawProgress.coerceIn(0f, 1f)

    val labelStage = smoothStep(progress, 0.00f, 0.32f)
    val sizeStage = smoothStep(progress, 0.00f, 0.36f)
    val shrinkStage = smoothStep(progress, 0.25f, 0.82f)
    val dropStage = smoothStep(progress, 0.78f, 1.00f)

    val navHeight = lerpDp(66.dp, 58.dp, sizeStage)
    val searchSize = lerpDp(66.dp, 58.dp, sizeStage)
    val expandedChromeHeight = if (hasMedia) 137.dp else 72.dp
    val chromeHeight = lerpDp(expandedChromeHeight, 64.dp, dropStage)
    val labelAlpha = 1f - labelStage
    val expandedLayerAlpha = 1f - smoothStep(progress, 0.43f, 0.72f)
    val compactLayerAlpha = smoothStep(progress, 0.52f, 0.82f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 5.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(chromeHeight),
        ) {
            val horizontalMargin = 14.dp
            val compactSize = 58.dp
            val expandedGap = 9.dp
            val compactGap = 4.dp
            val expandedNavWidth = maxWidth - horizontalMargin * 2 - expandedGap - 66.dp
            val navWidth = lerpDp(expandedNavWidth, compactSize, shrinkStage)
            val navRadius = lerpDp(34.dp, 29.dp, shrinkStage)
            val navShape = RoundedCornerShape(navRadius)
            val primaryTabs = listOf(
                AppTab.Home to RootGlyph.Home,
                AppTab.Explore to RootGlyph.Explore,
                AppTab.Library to RootGlyph.Library,
                AppTab.Settings to RootGlyph.Settings,
            )

            val desiredCompactMiniVisibleWidth =
                (maxWidth - horizontalMargin * 2 - compactSize * 2 - compactGap * 2)
                    .coerceAtLeast(80.dp)
            val compactMiniWrapperWidth =
                (desiredCompactMiniVisibleWidth + 28.dp).coerceAtMost(maxWidth)
            val compactMiniWrapperX = horizontalMargin + compactSize + compactGap - 14.dp
            val miniWrapperWidth = lerpDp(maxWidth, compactMiniWrapperWidth, shrinkStage)
            val miniWrapperX = lerpDp(0.dp, compactMiniWrapperX, shrinkStage)
            val miniLift = lerpDp(72.dp, 0.dp, shrinkStage)

            if (hasMedia) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(
                            x = miniWrapperX,
                            y = -3.dp - miniLift,
                        )
                        .width(miniWrapperWidth),
                ) {
                    miniPlayer()
                }
            }

            val dark = isSystemInDarkTheme()
            val navTint = if (dark) {
                Color.Black.copy(alpha = 0.08f)
            } else {
                Color.White.copy(alpha = 0.10f)
            }
            val fallback = if (dark) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)
            } else {
                Color.White.copy(alpha = 0.62f)
            }

            // The navigation capsule owns all four tab hit regions. This avoids invisible
            // child overlays stealing taps while the capsule is morphing into compact mode.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = horizontalMargin, y = -3.dp)
                    .width(navWidth)
                    .height(navHeight)
                    .meloXLiquidGlass(
                        backdrop = backdrop,
                        shape = navShape,
                        tint = navTint,
                        fallbackTint = fallback,
                        blurRadius = 8.dp,
                        refractionHeight = 12.dp,
                        refractionAmount = 16.dp,
                        enableLens = true,
                        highlightAlpha = 0.16f,
                        shadowAlpha = 0.10f,
                    )
                    .pointerInput(progress, selectedTab) {
                        detectTapGestures { tap ->
                            if (progress < 0.56f) {
                                val segmentWidth = size.width / 4f
                                val index = (tap.x / segmentWidth).toInt().coerceIn(0, 3)
                                onSelect(primaryTabs[index].first)
                            } else if (progress >= 0.68f) {
                                onSelect(selectedTab)
                            }
                        }
                    },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = expandedLayerAlpha }
                        .padding(horizontal = 5.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    primaryTabs.forEach { (tab, glyph) ->
                        RootTabButton(
                            tab = tab,
                            glyph = glyph,
                            selected = selectedTab == tab,
                            labelAlpha = labelAlpha,
                        )
                    }
                }

                if (progress > 0.50f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = compactLayerAlpha },
                        contentAlignment = Alignment.Center,
                    ) {
                        RootGlyphIcon(
                            glyph = selectedTab.rootGlyph(),
                            modifier = Modifier.size(27.dp),
                            color = if (selectedTab == AppTab.Search) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MeloXBottomAccent
                            },
                        )
                    }
                }
            }

            // AndroidLiquidGlass can render the circular mask directly, so the old Miuix
            // CircleShape fallback (which produced a polygon/hexagon artifact) is gone.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = -horizontalMargin, y = -3.dp)
                    .size(searchSize)
                    .meloXLiquidGlass(
                        backdrop = backdrop,
                        shape = CircleShape,
                        tint = navTint,
                        fallbackTint = fallback,
                        blurRadius = 6.dp,
                        refractionHeight = 10.dp,
                        refractionAmount = 12.dp,
                        enableLens = true,
                        highlightAlpha = 0.18f,
                        shadowAlpha = 0.10f,
                    )
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures { onSelect(AppTab.Search) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                RootGlyphIcon(
                    glyph = RootGlyph.Search,
                    modifier = Modifier.size(lerpDp(31.dp, 29.dp, sizeStage)),
                    color = if (selectedTab == AppTab.Search) {
                        MeloXBottomAccent
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun RowScope.RootTabButton(
    tab: AppTab,
    glyph: RootGlyph,
    selected: Boolean,
    labelAlpha: Float,
) {
    val foreground = if (selected) MeloXBottomAccent else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RootGlyphIcon(glyph = glyph, modifier = Modifier.size(26.dp), color = foreground)
        Text(
            text = tab.title,
            modifier = Modifier.graphicsLayer { alpha = labelAlpha },
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = foreground,
        )
    }
}

private enum class RootGlyph { Home, Explore, Library, Settings, Search }

private fun AppTab.rootGlyph(): RootGlyph = when (this) {
    AppTab.Home -> RootGlyph.Home
    AppTab.Explore -> RootGlyph.Explore
    AppTab.Library -> RootGlyph.Library
    AppTab.Settings -> RootGlyph.Settings
    AppTab.Search -> RootGlyph.Search
}

@Composable
private fun RootGlyphIcon(
    glyph: RootGlyph,
    modifier: Modifier,
    color: Color,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = size.minDimension * 0.115f

        when (glyph) {
            RootGlyph.Home -> {
                val roof = Path().apply {
                    moveTo(w * 0.10f, h * 0.48f)
                    lineTo(w * 0.50f, h * 0.14f)
                    lineTo(w * 0.90f, h * 0.48f)
                }
                drawPath(roof, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.24f, h * 0.43f),
                    size = Size(w * 0.52f, h * 0.43f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f),
                    style = Stroke(width = stroke),
                )
            }
            RootGlyph.Explore -> {
                drawCircle(color, radius = w * 0.35f, style = Stroke(width = stroke))
                val needle = Path().apply {
                    moveTo(w * 0.38f, h * 0.62f)
                    lineTo(w * 0.57f, h * 0.36f)
                    lineTo(w * 0.63f, h * 0.55f)
                    close()
                }
                drawPath(needle, color)
            }
            RootGlyph.Library -> {
                val path = Path().apply {
                    moveTo(w * 0.26f, h * 0.22f)
                    lineTo(w * 0.26f, h * 0.72f)
                    cubicTo(w * 0.26f, h * 0.83f, w * 0.10f, h * 0.84f, w * 0.10f, h * 0.70f)
                    cubicTo(w * 0.10f, h * 0.57f, w * 0.29f, h * 0.55f, w * 0.37f, h * 0.62f)
                    lineTo(w * 0.37f, h * 0.28f)
                    lineTo(w * 0.83f, h * 0.18f)
                    lineTo(w * 0.83f, h * 0.61f)
                    cubicTo(w * 0.83f, h * 0.74f, w * 0.66f, h * 0.77f, w * 0.61f, h * 0.66f)
                }
                drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
            RootGlyph.Settings -> {
                drawCircle(color, radius = w * 0.33f, style = Stroke(width = stroke))
                drawCircle(color, radius = w * 0.095f)
                repeat(8) { index ->
                    val angle = Math.toRadians((index * 45.0) - 90.0)
                    val cx = w / 2f
                    val cy = h / 2f
                    val r1 = w * 0.37f
                    val r2 = w * 0.47f
                    drawLine(
                        color = color,
                        start = Offset(
                            cx + kotlin.math.cos(angle).toFloat() * r1,
                            cy + kotlin.math.sin(angle).toFloat() * r1,
                        ),
                        end = Offset(
                            cx + kotlin.math.cos(angle).toFloat() * r2,
                            cy + kotlin.math.sin(angle).toFloat() * r2,
                        ),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
            RootGlyph.Search -> {
                drawCircle(
                    color = color,
                    radius = w * 0.29f,
                    center = Offset(w * 0.43f, h * 0.40f),
                    style = Stroke(width = stroke),
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.64f, h * 0.62f),
                    end = Offset(w * 0.86f, h * 0.84f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun smoothStep(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun lerpDp(start: Dp, end: Dp, progress: Float): Dp =
    (start.value + (end.value - start.value) * progress.coerceIn(0f, 1f)).dp
