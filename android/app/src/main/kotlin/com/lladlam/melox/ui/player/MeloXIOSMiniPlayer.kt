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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.lladlam.melox.ui.glass.meloXLiquidGlass

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MeloXIOSMiniPlayer(
    state: MeloXPlaybackUiState,
    onExpand: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    glassBackdrop: LayerBackdrop? = null,
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

    // Keep the #286 shared-player geometry intact. Only the glass renderer changes here.
    val miniChromeAlpha = 1f - smoothStep(expansionProgress, 0.05f, 0.72f)
    val miniSurfaceAlpha = 1f - smoothStep(expansionProgress, 0.04f, 0.42f)

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
            .padding(horizontal = 14.dp, vertical = 3.dp)
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
        val miniShape = RoundedCornerShape(22.dp)
        val dark = isSystemInDarkTheme()
        val glassTint = if (dark) {
            Color.Black.copy(alpha = 0.08f)
        } else {
            Color.White.copy(alpha = 0.10f)
        }
        val fallbackTint = if (dark) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
        } else {
            Color.White.copy(alpha = 0.64f)
        }

        Surface(
            modifier = sharedContainerModifier
                .fillMaxWidth()
                .height(52.dp)
                .meloXLiquidGlass(
                    backdrop = glassBackdrop,
                    shape = miniShape,
                    tint = glassTint,
                    fallbackTint = fallbackTint,
                    alpha = miniSurfaceAlpha,
                    // MiniPlayer needs a little more blur than small buttons because it sits
                    // directly over list dividers. This still samples the single root backdrop.
                    blurRadius = 12.dp,
                    refractionHeight = 10.dp,
                    refractionAmount = 12.dp,
                    enableLens = true,
                    highlightAlpha = 0.12f,
                    shadowAlpha = 0.08f,
                ),
            shape = miniShape,
            color = Color.Transparent,
            border = null,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {}

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 9.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
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
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
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
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.54f * miniChromeAlpha,
                        ),
                    )
                }
            }

            MiniVectorButton(
                kind = if (state.isPlaying) MiniGlyph.Pause else MiniGlyph.Play,
                enabled = true,
                onClick = state::togglePlayPause,
                modifier = chromeOverlayModifier,
                visualAlpha = miniChromeAlpha,
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
