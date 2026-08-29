package com.lladlam.melox.ui.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.Capsule
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.glass.meloXLiquidContentTransform
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXSymbolVariant
import com.lladlam.melox.ui.glass.rememberMeloXLiquidInteraction
import com.lladlam.melox.playback.MeloXAudioReactiveRuntime
import com.lladlam.melox.playback.MeloXAudioReactiveSample
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MeloXIOSMiniPlayer(
    state: MeloXPlaybackUiState,
    onExpand: () -> Unit,
    compactProgress: Float = 0f,
    dynamicGlassEnabled: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    applyPlayerArtworkScale: Boolean = true,
) {
    if (!state.hasMedia) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeWidth = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val contentOffset = remember { Animatable(0f) }
    val liquidInteraction = rememberMeloXLiquidInteraction()
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    var pendingDirection by remember { mutableIntStateOf(0) }
    var pendingOutgoingMediaId by remember { mutableStateOf<String?>(null) }
    var reactiveSample by remember { mutableStateOf(MeloXAudioReactiveSample.Idle) }

    LaunchedEffect(state.mediaId, pendingDirection) {
        if (pendingDirection != 0 && state.mediaId != pendingOutgoingMediaId) {
            contentOffset.snapTo(0f)
            pendingDirection = 0
            pendingOutgoingMediaId = null
        } else if (pendingDirection == 0) {
            contentOffset.snapTo(0f)
        }
    }

    LaunchedEffect(pendingOutgoingMediaId) {
        if (pendingOutgoingMediaId == null) return@LaunchedEffect
        kotlinx.coroutines.delay(1_500L)
        contentOffset.animateTo(0f, spring(dampingRatio = .72f, stiffness = 430f))
        pendingDirection = 0
        pendingOutgoingMediaId = null
    }

    val expansionProgress = if (animatedVisibilityScope != null) {
        val value by animatedVisibilityScope.transition.animateFloat(
            transitionSpec = { meloXPlayerLinearFloatSpec() },
            label = "mini-player-expansion-progress",
        ) { visibility ->
            if (visibility == EnterExitState.Visible) 0f else 1f
        }
        value
    } else {
        0f
    }

    // The mini visualizer is hidden during expansion. Stop its 20 Hz state
    // updates so the shared-bound transition can spend its frame budget on
    // layout and glass rendering instead.
    LaunchedEffect(state.mediaId, state.isPlaying, expansionProgress > 0.01f) {
        if (expansionProgress > 0.01f || !state.isPlaying) {
            reactiveSample = MeloXAudioReactiveSample.Idle
            return@LaunchedEffect
        }
        while (true) {
            reactiveSample = MeloXAudioReactiveRuntime.sample(state.mediaId)
            kotlinx.coroutines.delay(50L)
        }
    }

    // All source chrome is driven by the same reversible expansion progress as
    // the full-player destination. The source fades as real composited content,
    // rather than only lowering text/icon colors, so the reverse transition is
    // equally visible when returning to the mini player.
    val miniChromeAlpha = 1f - smoothStep(expansionProgress, 0.10f, 0.58f)
    val miniSurfaceAlpha = 1f - smoothStep(expansionProgress, 0.02f, 0.48f)
    val playerArtworkScale = if (
        !applyPlayerArtworkScale || MeloXSettingsRuntime.reduceMotion || !MeloXSettingsRuntime.artworkMotionEnabled || state.isPlaying
    ) 1f else 0.74f
    val sharedArtworkScale = 1f +
        (playerArtworkScale - 1f) * smoothStep(expansionProgress, 0.30f, 0.88f)
    val sharedShellModifier =
        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = sharedPlayerShellKey()),
                    animatedVisibilityScope = animatedVisibilityScope,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                    boundsTransform = MeloXPlayerShellBoundsTransform,
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                )
            }
        } else {
            Modifier
        }

    val compact = compactProgress.coerceIn(0f, 1f)
    val artworkSize = lerpDp(40.dp, 30.dp, compact)
    val artworkRadius = 6.dp
    val compactArtistAlpha = 1f - smoothStep(compact, 0.04f, 0.52f)
    val compactNextAlpha = 1f - smoothStep(compact, 0.04f, 0.50f)
    val controlStageWidth = lerpDp(72.dp, 36.dp, smoothStep(compact, 0.08f, 0.84f))
    val artistHeight = lerpDp(15.dp, 0.dp, smoothStep(compact, 0.04f, 0.72f))
    val dragDirection = when {
        contentOffset.value < 0f -> -1
        contentOffset.value > 0f -> 1
        else -> 0
    }
    val adjacentEntry = when (dragDirection) {
        -1 -> state.queue.getOrNull(
            if (state.currentIndex + 1 < state.queue.size) state.currentIndex + 1 else 0,
        )
        1 -> state.queue.getOrNull(
            if (state.currentIndex > 0) state.currentIndex - 1 else state.queue.lastIndex,
        )
        else -> null
    }
    val dragProgress = (kotlin.math.abs(contentOffset.value) / swipeWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val adjacentAlpha = smoothStep(dragProgress, 0.15f, 0.85f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(sharedShellModifier),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
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
                        interaction = liquidInteraction,
                    ),
            )
        }

        // The glass draws underneath this layer, but both now consume the same
        // press/drag transform. Keeping MiniPlay chrome in the shared layer
        // prevents text, artwork and controls from floating over a moving pill.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .meloXLiquidContentTransform(liquidInteraction),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(Capsule())
                    .padding(
                        horizontal = lerpDp(12.dp, 8.dp, compact),
                        vertical = lerpDp(6.dp, 3.dp, compact),
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(state.mediaId) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    accumulatedDrag = 0f
                                    scope.launch { contentOffset.stop() }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    accumulatedDrag += dragAmount
                                    scope.launch {
                                        contentOffset.snapTo((contentOffset.value + dragAmount).coerceIn(-swipeWidth * .86f, swipeWidth * .86f))
                                    }
                                },
                                onDragEnd = {
                                    val direction = when {
                                        accumulatedDrag <= -28f -> -1
                                        accumulatedDrag >= 28f -> 1
                                        else -> 0
                                    }
                                    if (direction != 0) {
                                        scope.launch {
                                            contentOffset.animateTo(direction * swipeWidth, spring(dampingRatio = .68f, stiffness = 360f))
                                            pendingDirection = direction
                                            pendingOutgoingMediaId = state.mediaId
                                            if (direction < 0) state.nextFromMiniPlayer() else state.previousFromMiniPlayer()
                                        }
                                    } else {
                                        scope.launch { contentOffset.animateTo(0f, spring(dampingRatio = .72f, stiffness = 430f)) }
                                    }
                                    accumulatedDrag = 0f
                                },
                                onDragCancel = {
                                    accumulatedDrag = 0f
                                    scope.launch { contentOffset.animateTo(0f, spring(dampingRatio = .72f, stiffness = 430f)) }
                                },
                            )
                        }
                        .clickable(interactionSource = null, indication = null, onClick = onExpand),
                ) {
                Row(
                    modifier = Modifier.fillMaxSize().graphicsLayer { translationX = contentOffset.value },
                    verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(lerpDp(10.dp, 8.dp, compact)),
                ) {
                val sharedArtworkModifier =
                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                sharedContentState = rememberSharedContentState(
                                    key = sharedPlayerArtworkKey(state.mediaId),
                                ),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = MeloXArtworkBoundsTransform,
                                renderInOverlayDuringTransition = true,
                                zIndexInOverlay = 100f,
                            )
                        }
                    } else {
                        Modifier
                    }
                Artwork(
                    url = state.artworkUrl,
                    modifier = Modifier
                        .size(artworkSize)
                        .then(sharedArtworkModifier)
                        .graphicsLayer {
                            scaleX = sharedArtworkScale
                            scaleY = sharedArtworkScale
                        }
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
                adjacentEntry?.let { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = adjacentAlpha
                                translationX = contentOffset.value - dragDirection * swipeWidth
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(lerpDp(10.dp, 8.dp, compact)),
                    ) {
                        Artwork(entry.artworkUrl, Modifier.size(artworkSize).clip(RoundedCornerShape(artworkRadius)))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(entry.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .64f))
                        }
                    }
                }
            }

            MiniDancingBars(
                isPlaying = state.isPlaying,
                sample = reactiveSample,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = miniChromeAlpha * 0.72f),
                modifier = Modifier
                            .width(15.dp)
                            .height(18.dp)
                    .graphicsLayer { alpha = miniChromeAlpha },
            )

            Box(
                modifier = Modifier
                    .width(controlStageWidth)
                    .height(36.dp)
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
}

@Composable
private fun MiniDancingBars(
    isPlaying: Boolean,
    sample: MeloXAudioReactiveSample,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val energy = if (isPlaying) sample.energy.coerceIn(0.08f, 1f) else 0.10f
    val beat = if (isPlaying) sample.beat else 0f
    val downbeat = if (isPlaying) sample.downbeat else 0f
    val bars = listOf(
        energy * (0.58f + downbeat * 0.30f),
        energy * (0.86f + beat * 0.22f),
        energy * (0.46f + downbeat * 0.42f),
        energy * (0.72f + beat * 0.28f),
    )
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
            .size(36.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled && drawAlpha > 0.05f,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        MeloXSymbolIcon(
            symbol = when (kind) {
                MiniGlyph.Play -> MeloXSymbol.Play
                MiniGlyph.Pause -> MeloXSymbol.Pause
                MiniGlyph.Forward -> MeloXSymbol.Next
            },
            modifier = Modifier.size(22.dp),
            color = color,
            variant = if (kind == MiniGlyph.Play || kind == MiniGlyph.Pause) MeloXSymbolVariant.Fill else MeloXSymbolVariant.Regular,
            iconSize = 22.sp,
        )
    }
}

private fun smoothStep(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun lerpDp(start: Dp, end: Dp, progress: Float): Dp =
    (start.value + (end.value - start.value) * progress.coerceIn(0f, 1f)).dp
