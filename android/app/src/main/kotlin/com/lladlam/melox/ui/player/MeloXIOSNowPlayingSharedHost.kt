package com.lladlam.melox.ui.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MeloXIOSNowPlayingSharedHost(
    state: MeloXPlaybackUiState,
    onDismiss: () -> Unit,
    onSeekCollapse: suspend (Float) -> Unit,
    onSettleCollapse: suspend (Boolean) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    var page by remember(state.mediaId) { mutableStateOf(MeloXNowPlayingPage.Artwork) }
    var gestureCollapseProgress by remember(state.mediaId) { mutableFloatStateOf(0f) }
    var settleJob by remember(state.mediaId) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val expansionProgress by animatedVisibilityScope.transition.animateFloat(
        transitionSpec = { meloXPlayerLinearFloatSpec() },
        label = "full-player-expansion-progress",
    ) { visibility ->
        if (visibility == EnterExitState.Visible) 1f else 0f
    }

    val backdropAlpha = smoothStep(expansionProgress, 0.08f, 0.58f)
    val fullPlayerAlpha = smoothStep(expansionProgress, 0.46f, 0.90f)
    val collapseProgress = (1f - expansionProgress).coerceIn(0f, 1f)
    // Keep the fully expanded player softly rounded instead of snapping to
    // square screen corners. As it approaches MiniPlayer, round it a bit more.
    val cornerRadius = (24f + 8f * smoothStep(collapseProgress, 0f, 1f)).dp

    val sharedContainerModifier = with(sharedTransitionScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(
                key = sharedPlayerContainerKey(state.mediaId),
            ),
            animatedVisibilityScope = animatedVisibilityScope,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            boundsTransform = MeloXPlayerLinearBoundsTransform,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val dragRangePx = (constraints.maxHeight * 0.86f).coerceAtLeast(1f)
        val dragState = rememberDraggableState { delta ->
            gestureCollapseProgress = (
                gestureCollapseProgress + delta / dragRangePx
                ).coerceIn(0f, 0.999f)
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                onSeekCollapse(gestureCollapseProgress)
            }
        }

        Box(
            modifier = sharedContainerModifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius))
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    enabled = page == MeloXNowPlayingPage.Artwork,
                    onDragStarted = {
                        // A new gesture always takes ownership immediately. If a
                        // previous short drag is still springing back, cancel that
                        // settle instead of blocking the next pointer gesture until
                        // the old animation completes.
                        settleJob?.cancelAndJoin()
                        settleJob = null

                        // Resume from the exact visual position where the interrupted
                        // settle currently is, rather than resetting to Full and
                        // producing a jump on repeated short drags.
                        gestureCollapseProgress = collapseProgress.coerceIn(0f, 0.999f)
                        onSeekCollapse(gestureCollapseProgress)
                    },
                    onDragStopped = { velocity ->
                        val releaseProgress = gestureCollapseProgress
                        val shouldCollapse = releaseProgress >= 0.42f || velocity >= 1200f

                        // Do not suspend draggable until the settle animation ends.
                        // Keep it in a cancellable job so the next touch can interrupt
                        // the spring and continue from the current frame immediately.
                        settleJob?.cancel()
                        settleJob = scope.launch {
                            onSettleCollapse(shouldCollapse)
                            if (!shouldCollapse) {
                                gestureCollapseProgress = 0f
                            }
                            settleJob = null
                        }
                    },
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = backdropAlpha },
            ) {
                MeloXFlowingLightBackdrop(
                    artworkUrl = state.artworkUrl,
                    isPlaying = state.isPlaying,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fullPlayerAlpha },
            ) {
                MeloXIOSNowPlayingV2(
                    state = state,
                    onDismiss = onDismiss,
                    page = page,
                    onPageChanged = { page = it },
                    drawBackdrop = false,
                    drawArtwork = false,
                )
            }

        }

        // Keep the artwork destination in the stable SharedTransition root
        // coordinate space. Nesting it inside the remeasured sharedBounds made
        // its target move while the artwork itself was also transforming.
        SharedArtworkDestination(
            state = state,
            page = page,
            expansionProgress = expansionProgress,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedArtworkDestination(
    state: MeloXPlaybackUiState,
    page: MeloXNowPlayingPage,
    expansionProgress: Float,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val playbackScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.74f,
        animationSpec = if (state.isPlaying) {
            spring(
                dampingRatio = 0.70f,
                stiffness = 280f,
                visibilityThreshold = 0.001f,
            )
        } else {
            spring(
                dampingRatio = 0.94f,
                stiffness = 360f,
                visibilityThreshold = 0.001f,
            )
        },
        label = "shared-artwork-playback-scale",
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (state.isPlaying) 26.dp else 14.dp,
        animationSpec = spring(
            dampingRatio = 0.92f,
            stiffness = 320f,
        ),
        label = "shared-artwork-shadow",
    )

    val artworkAlpha = if (page == MeloXNowPlayingPage.Artwork) {
        1f
    } else {
        1f - smoothStep(expansionProgress, 0.72f, 0.985f)
    }

    val fullScreenScaleBlend = smoothStep(expansionProgress, 0.30f, 0.88f)
    val effectiveScale = 1f + (playbackScale - 1f) * fullScreenScaleBlend

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 32.dp),
    ) {
        Spacer(Modifier.height(30.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val artworkSize = maxOf(
                170.dp,
                minOf(maxWidth + 16.dp, maxHeight - 92.dp),
            )

            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.weight(1f))

                val sharedModifier = with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState(
                            key = sharedArtworkKey(state.mediaId),
                        ),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = MeloXPlayerLinearBoundsTransform,
                        zIndexInOverlay = 3f,
                    )
                }

                Box(
                    modifier = sharedModifier
                        .size(artworkSize),
                ) {
                    Artwork(
                        url = state.artworkUrl,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = artworkAlpha
                                scaleX = effectiveScale
                                scaleY = effectiveScale
                            }
                            .shadow(
                                elevation = shadowElevation,
                                shape = RoundedCornerShape(12.dp),
                                clip = false,
                                ambientColor = Color.Black.copy(alpha = 0.28f * artworkAlpha),
                                spotColor = Color.Black.copy(alpha = 0.28f * artworkAlpha),
                            )
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }

                Spacer(Modifier.height(20.dp))

                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = state.title.ifBlank { "正在播放" },
                        color = Color.Transparent,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        text = state.artist.ifBlank { " " },
                        color = Color.Transparent,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(279.dp))
    }
}

private fun smoothStep(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
