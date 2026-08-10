package com.lladlam.melox.ui.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.ui.glass.meloXLiquidBottomBar

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MeloXIOSMiniPlayer(
    state: MeloXPlaybackUiState,
    onExpand: () -> Unit,
    compactProgress: Float = 0f,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    if (!state.hasMedia) return

    var accumulatedDrag by remember(state.mediaId) { mutableFloatStateOf(0f) }

    val expansionProgress = if (animatedVisibilityScope != null) {
        val value by animatedVisibilityScope.transition.animateFloat(
            transitionSpec = {
                spring(
                    dampingRatio = 0.90f,
                    stiffness = 320f,
                    visibilityThreshold = 0.001f,
                )
            },
            label = "mini-player-expansion-progress",
        ) { visibility ->
            if (visibility == EnterExitState.Visible) 0f else 1f
        }
        value
    } else {
        0f
    }

    // Keep #286 geometry untouched. Fade source chrome over most of the morph.
    // These values are actual draw alpha below, not a parent graphics layer, so
    // they remain effective after the chrome is lifted into SharedTransitionScope's overlay.
    val miniChromeAlpha = 1f - smoothStep(expansionProgress, 0.05f, 0.72f)
    val miniSurfaceAlpha = 1f - smoothStep(expansionProgress, 0.04f, 0.42f)

    // The shared bounds itself is rendered in SharedTransitionScope's overlay.
    // Lift source chrome into the same overlay so it is not abruptly covered by
    // the growing container. Its visual fade is applied to the child draw content.
    val chromeOverlayModifier =
        if (sharedTransitionScope != null) {
            with(sharedTransitionScope) {
                Modifier.renderInSharedTransitionScopeOverlay(
                    zIndexInOverlay = 2f,
                )
            }
        } else {
            Modifier
        }

    val sharedContainerModifier =
        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(
                        key = sharedPlayerContainerKey(state.mediaId),
                    ),
                    animatedVisibilityScope = animatedVisibilityScope,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                )
            }
        } else {
            Modifier
        }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
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
            },
    ) {
        val compact = compactProgress.coerceIn(0f, 1f)
        val miniShape = RoundedCornerShape(25.dp)
        val artworkSize = lerpDp(40.dp, 30.dp, compact)
        val artistAlpha = 1f - smoothStep(compact, 0.04f, 0.52f)
        val nextAlpha = 1f - smoothStep(compact, 0.08f, 0.68f)
        val dark = isSystemInDarkTheme()
        val glassTint = if (dark) {
            Color.Black.copy(alpha = 0.12f)
        } else {
            Color.White.copy(alpha = 0.16f)
        }
        val fallbackTint = if (dark) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.64f)
        } else {
            Color.White.copy(alpha = 0.66f)
        }

        Surface(
            modifier = sharedContainerModifier
                .fillMaxWidth()
                .height(50.dp)
                .graphicsLayer { alpha = miniSurfaceAlpha }
                .meloXLiquidBottomBar(
                    shape = miniShape,
                    tint = glassTint,
                    surfaceColor = fallbackTint.copy(alpha = fallbackTint.alpha * 0.24f),
                ),
            shape = miniShape,
            color = Color.Transparent,
            border = null,
            tonalElevation = 0.dp,
            shadowElevation = (2f * miniSurfaceAlpha).dp,
        ) {}

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
                    .clickable(onClick = onExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val sharedArtworkModifier =
                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                sharedContentState = rememberSharedContentState(
                                    key = sharedArtworkKey(state.mediaId),
                                ),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        }
                    } else {
                        Modifier
                    }

                Artwork(
                    url = state.artworkUrl,
                    modifier = sharedArtworkModifier
                        .size(artworkSize)
                        .clip(RoundedCornerShape(lerpDp(9.dp, 7.dp, compact))),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(chromeOverlayModifier),
                ) {
                    Text(
                        text = state.title.ifBlank { "正在播放" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        softWrap = false,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = miniChromeAlpha,
                        ),
                    )
                    Text(
                        text = state.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        softWrap = false,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.54f * miniChromeAlpha * artistAlpha,
                        ),
                    )
                }
            }

            MiniVectorButton(
                kind = if (state.isPlaying) MiniGlyph.Pause else MiniGlyph.Play,
                enabled = true,
                onClick = state::togglePlayPause,
                modifier = chromeOverlayModifier,
                visualAlpha = miniChromeAlpha * nextAlpha,
            )
            MiniVectorButton(
                kind = MiniGlyph.Forward,
                enabled = state.hasNext || state.repeatMode != 0,
                onClick = state::next,
                modifier = chromeOverlayModifier,
                visualAlpha = miniChromeAlpha,
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
    val color = MaterialTheme.colorScheme.onSurface.copy(
        alpha = baseAlpha * visualAlpha.coerceIn(0f, 1f),
    )
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled && visualAlpha > 0.05f,
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

private fun lerpDp(start: androidx.compose.ui.unit.Dp, end: androidx.compose.ui.unit.Dp, progress: Float) =
    (start.value + (end.value - start.value) * progress.coerceIn(0f, 1f)).dp
