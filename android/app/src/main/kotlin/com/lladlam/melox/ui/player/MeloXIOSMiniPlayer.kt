package com.lladlam.melox.ui.player

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.Capsule
import com.lladlam.melox.ui.glass.meloXLiquidButton

@Composable
fun MeloXIOSMiniPlayer(
    state: MeloXPlaybackUiState,
    onExpand: () -> Unit,
    compactProgress: Float = 0f,
    dynamicGlassEnabled: Boolean = true,
    expansionProgress: Float = 0f,
) {
    if (!state.hasMedia) return

    var accumulatedDrag by remember(state.mediaId) { mutableFloatStateOf(0f) }

    // All source chrome is driven by the same reversible expansion progress as
    // the full-player destination. The source fades as real composited content,
    // rather than only lowering text/icon colors, so the reverse transition is
    // equally visible when returning to the mini player.
    // Fade the mini chrome out later while expanding and back in earlier
    // while collapsing, so the return transition overlaps with full-player
    // content and does not black out.
    val miniChromeAlpha = 1f - smoothStep(expansionProgress, 0.28f, 0.62f)
    val miniSurfaceAlpha = 1f - smoothStep(expansionProgress, 0.05f, 0.45f)

    val compact = compactProgress.coerceIn(0f, 1f)
    val artworkSize = lerpDp(40.dp, 30.dp, compact)
    val artworkRadius = lerpDp(9.dp, 7.dp, compact)
    val compactArtistAlpha = 1f - smoothStep(compact, 0.04f, 0.52f)
    val compactNextAlpha = 1f - smoothStep(compact, 0.04f, 0.50f)
    val controlStageWidth = lerpDp(82.dp, 44.dp, smoothStep(compact, 0.08f, 0.84f))
    val artistHeight = lerpDp(15.dp, 0.dp, smoothStep(compact, 0.04f, 0.72f))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) {
            // Keep the glass surface out of the shared-bounds node itself.
            // When a capsule-shaped draw modifier is resized to the full
            // player, its fallback/lens can briefly become a dark giant pill.
            // The shared node remains a transparent geometry shell while the
            // actual MiniPlay glass fades before the full-screen scene owns the
            // surface.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = miniSurfaceAlpha }
                    .meloXLiquidButton(
                        shape = Capsule(),
                        blurRadius = 2.dp,
                        lensRadius = 28.dp,
                        refractionHeight = 16.dp,
                        surfaceColor = Color.White.copy(alpha = 0.06f),
                    ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(start = 7.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(state.mediaId) {
                        detectHorizontalDragGestures(
                            onDragStart = { accumulatedDrag = 0f },
                            onHorizontalDrag = { _, dragAmount -> accumulatedDrag += dragAmount },
                            onDragEnd = {
                                when {
                                    accumulatedDrag <= -48f -> state.next()
                                    accumulatedDrag >= 48f -> state.previous()
                                }
                                accumulatedDrag = 0f
                            },
                            onDragCancel = { accumulatedDrag = 0f },
                        )
                    }
                    .clickable(onClick = onExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Artwork fades out as expansion progresses — the full player's
                // artwork (driven by manual lerp) takes over visually.
                Artwork(
                    url = state.artworkUrl,
                    modifier = Modifier
                        .graphicsLayer { alpha = 1f - smoothStep(expansionProgress, 0.0f, 0.35f) }
                        .size(artworkSize)
                        .clip(RoundedCornerShape(artworkRadius)),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = miniChromeAlpha },
                ) {
                    Text(
                        text = state.title.ifBlank { "正在播放" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        softWrap = false,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Box(
                        modifier = Modifier
                            .height(artistHeight)
                            .graphicsLayer { alpha = compactArtistAlpha },
                    ) {
                        Text(
                            text = state.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            softWrap = false,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                        )
                    }
                }
            }

            MiniDancingBars(
                isPlaying = state.isPlaying,
                visible = miniChromeAlpha > 0.05f,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = miniChromeAlpha * 0.72f),
                modifier = Modifier
                    .width(17.dp)
                    .height(20.dp)
                    .graphicsLayer { alpha = miniChromeAlpha },
            )

            Box(
                modifier = Modifier
                    .width(controlStageWidth)
                    .height(44.dp)
                    .zIndex(8f),
            ) {
                MiniVectorButton(
                    kind = if (state.isPlaying) MiniGlyph.Pause else MiniGlyph.Play,
                    enabled = true,
                    onClick = state::togglePlayPause,
                    modifier = Modifier
                        .align(if (compact > 0.55f) Alignment.Center else Alignment.CenterStart)
                        .zIndex(10f),
                    visualAlpha = miniChromeAlpha,
                )
                if (compactNextAlpha > 0.05f) {
                    MiniVectorButton(
                        kind = MiniGlyph.Forward,
                        enabled = state.hasNext || state.repeatMode != 0,
                        onClick = state::next,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .zIndex(9f),
                        visualAlpha = miniChromeAlpha * compactNextAlpha,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniDancingBars(
    isPlaying: Boolean,
    visible: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = if (isPlaying && visible) {
        rememberInfiniteTransition(label = "mini-dancing-bars")
    } else null
    val bars = listOf(.52f, .78f, .38f, .66f).mapIndexed { index, base ->
        transition?.animateFloat(
            initialValue = base * .58f,
            targetValue = base,
            animationSpec = infiniteRepeatable(
                animation = tween(270 + index * 23),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "mini-dancing-bar-$index",
        )?.value ?: base * .62f
    }
    Canvas(modifier) {
        val gap = size.width * .12f
        val barWidth = (size.width - gap * 3f) / 4f
        bars.forEachIndexed { index, heightFraction ->
            val barHeight = size.height * heightFraction.coerceIn(.12f, 1f)
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), (size.height - barHeight) / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )
        }
    }
}

private enum class MiniGlyph { Play, Pause, Forward }

@Composable
private fun MiniVectorButton(
    kind: MiniGlyph,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualAlpha: Float = 1f,
) {
    val baseAlpha = if (enabled) 0.94f else 0.26f
    val drawAlpha = visualAlpha.coerceIn(0f, 1f)
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = baseAlpha)
    Box(
        modifier = modifier
            .graphicsLayer { alpha = drawAlpha }
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled && drawAlpha > 0.05f,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(if (kind == MiniGlyph.Forward) 25.dp else 23.dp)) {
            when (kind) {
                MiniGlyph.Play -> {
                    val path = Path().apply {
                        moveTo(size.width * 0.24f, size.height * 0.14f)
                        lineTo(size.width * 0.82f, size.height * 0.50f)
                        lineTo(size.width * 0.24f, size.height * 0.86f)
                        close()
                    }
                    drawPath(path, color)
                }
                MiniGlyph.Pause -> {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(size.width * 0.24f, size.height * 0.14f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.17f, size.height * 0.72f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.035f),
                    )
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(size.width * 0.59f, size.height * 0.14f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.17f, size.height * 0.72f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.035f),
                    )
                }
                MiniGlyph.Forward -> {
                    val first = Path().apply {
                        moveTo(size.width * 0.06f, size.height * 0.16f)
                        lineTo(size.width * 0.49f, size.height * 0.50f)
                        lineTo(size.width * 0.06f, size.height * 0.84f)
                        close()
                    }
                    val second = Path().apply {
                        moveTo(size.width * 0.45f, size.height * 0.16f)
                        lineTo(size.width * 0.88f, size.height * 0.50f)
                        lineTo(size.width * 0.45f, size.height * 0.84f)
                        close()
                    }
                    drawPath(first, color)
                    drawPath(second, color)
                }
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
