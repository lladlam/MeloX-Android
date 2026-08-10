package com.lladlam.melox.ui.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MeloXIOSNowPlayingSharedHost(
    state: MeloXPlaybackUiState,
    transitionState: SeekableTransitionState<Boolean>,
    motionSpec: FiniteAnimationSpec<Float>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    var page by remember(state.mediaId) { mutableStateOf(MeloXNowPlayingPage.Artwork) }
    var dragPixels by remember(state.mediaId) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    val expansionProgress by animatedVisibilityScope.transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = 0.90f,
                stiffness = 320f,
                visibilityThreshold = 0.001f,
            )
        },
        label = "full-player-expansion-progress",
    ) { visibility ->
        if (visibility == EnterExitState.Visible) 1f else 0f
    }

    // The palette is part of the transforming container itself. It starts becoming
    // visible on the very first frames instead of waiting for the full-player chrome.
    val backdropAlpha = smoothStep(expansionProgress, 0.00f, 0.42f)
    val fullPlayerAlpha = smoothStep(expansionProgress, 0.62f, 0.96f)
    val cornerRadius = (22f * (1f - smoothStep(expansionProgress, 0.00f, 0.94f))).dp

    LaunchedEffect(expansionProgress) {
        if (expansionProgress <= 0.01f) {
            dragPixels = 0f
        }
    }

    val requestDismiss = {
        scope.launch { transitionState.animateTo(false, motionSpec) }
        Unit
    }

    val sharedContainerModifier = with(sharedTransitionScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(
                key = sharedPlayerContainerKey(state.mediaId),
            ),
            animatedVisibilityScope = animatedVisibilityScope,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            // This container changes from a 52dp pill to a full-screen surface.
            // Remeasuring the backdrop on every animated bound keeps the actual
            // mask attached to the mini-player rectangle instead of scaling a
            // premeasured full-screen layer inside it.
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
      val dragRange = (constraints.maxHeight * 0.58f).coerceAtLeast(1f)
      val dragState = rememberDraggableState { delta ->
          dragPixels = (dragPixels + delta).coerceIn(0f, dragRange)
          val fraction = (dragPixels / dragRange).coerceIn(0f, 0.999f)
          scope.launch(start = CoroutineStart.UNDISPATCHED) {
              // Seek the same transition used by Back and by expansion. There
              // is no second translation/scale animation layered on top.
              transitionState.seekTo(fraction, targetState = false)
          }
      }

      Box(
        modifier = sharedContainerModifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius))
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                enabled = page == MeloXNowPlayingPage.Artwork && expansionProgress >= 0.995f,
                onDragStarted = { dragPixels = 0f },
                onDragStopped = { velocity ->
                    val commit = (dragPixels / dragRange) >= 0.28f || velocity >= 1350f
                    scope.launch {
                        transitionState.animateTo(commit.not(), motionSpec)
                        dragPixels = 0f
                    }
                },
            ),
      ) {
        // Record only the visual scene. Player controls are drawn later as a
        // sibling, so their glass never recursively samples itself.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backdropAlpha },
        ) {
            MeloXFlowingLightBackdrop(
                artworkUrl = state.artworkUrl,
                isPlaying = state.isPlaying,
            )
            SharedArtworkDestination(
                state = state,
                page = page,
                expansionProgress = expansionProgress,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = fullPlayerAlpha },
        ) {
            MeloXIOSNowPlayingV2(
                state = state,
                onDismiss = requestDismiss,
                page = page,
                onPageChanged = { page = it },
                drawBackdrop = false,
                drawArtwork = false,
            )
        }
      }
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

    // On lyrics/queue, keep that page in place while collapsing. The artwork fades
    // in as the player starts shrinking instead of forcing the page back to Artwork.
    val artworkAlpha = if (page == MeloXNowPlayingPage.Artwork) {
        1f
    } else {
        1f - smoothStep(expansionProgress, 0.72f, 0.985f)
    }

    // At the mini-player endpoint the artwork fills its 40dp rect. The paused
    // artwork shrink gradually applies only as the player approaches full-screen,
    // so reversing the transition at any point remains continuous.
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

                // Reserve exactly the same metadata space as the artwork page.
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
