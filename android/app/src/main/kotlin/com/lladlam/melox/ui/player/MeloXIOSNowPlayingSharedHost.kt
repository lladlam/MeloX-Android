package com.lladlam.melox.ui.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollSource.Companion.UserInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.lladlam.melox.ui.glass.LocalMeloXBackdrop
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
    var transitionSourcePage by remember(state.mediaId) {
        mutableStateOf(MeloXNowPlayingPage.Artwork)
    }
    var showActions by remember(state.mediaId) { mutableStateOf(false) }
    var gestureCollapseProgress by remember(state.mediaId) { mutableFloatStateOf(0f) }
    var settleJob by remember(state.mediaId) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    // Two distinct scenes avoid recursive glass sampling:
    // controls sample the flowing-light player scene; the actions overlay samples
    // the fully composed Now Playing scene from outside that recording layer.
    val playerControlBackdrop = rememberLayerBackdrop()
    val actionsBackdrop = rememberLayerBackdrop()

    val expansionProgress by animatedVisibilityScope.transition.animateFloat(
        transitionSpec = { meloXPlayerLinearFloatSpec() },
        label = "full-player-expansion-progress",
    ) { visibility ->
        if (visibility == EnterExitState.Visible) 1f else 0f
    }

    val backdropAlpha = smoothStep(expansionProgress, 0.08f, 0.58f)
    val fullPlayerAlpha = smoothStep(expansionProgress, 0.46f, 0.90f)
    val collapseProgress = (1f - expansionProgress).coerceIn(0f, 1f)
    val latestCollapseProgress = rememberUpdatedState(collapseProgress)
    val latestPage = rememberUpdatedState(page)
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

        fun seekCollapseBy(delta: Float) {
            val old = gestureCollapseProgress
            val next = (old + delta / dragRangePx).coerceIn(0f, 0.999f)
            if (next == old) return
            gestureCollapseProgress = next
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                onSeekCollapse(next)
            }
        }

        suspend fun beginCollapseDrag() {
            settleJob?.cancelAndJoin()
            settleJob = null
            gestureCollapseProgress = latestCollapseProgress.value.coerceIn(0f, 0.999f)
            onSeekCollapse(gestureCollapseProgress)
        }

        fun settleFromCurrent(velocity: Float) {
            val releaseProgress = gestureCollapseProgress
            val shouldCollapse = releaseProgress >= 0.42f || velocity >= 1200f
            settleJob?.cancel()
            settleJob = scope.launch {
                onSettleCollapse(shouldCollapse)
                if (!shouldCollapse) {
                    gestureCollapseProgress = 0f
                }
                settleJob = null
            }
        }

        val dragState = rememberDraggableState { delta -> seekCollapseBy(delta) }

        // Lyrics/Queue own scrollable content. Child scrolling wins normally;
        // leftover downward motion at the top edge begins player collapse. Once
        // collapse has started, this connection owns both directions so the user
        // can reverse the transition without lifting the finger.
        val alternatePageCollapseConnection = remember(dragRangePx) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source != UserInput || latestPage.value == MeloXNowPlayingPage.Artwork) {
                        return Offset.Zero
                    }
                    if (gestureCollapseProgress <= 0f) return Offset.Zero
                    seekCollapseBy(available.y)
                    return Offset(x = 0f, y = available.y)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source != UserInput || latestPage.value == MeloXNowPlayingPage.Artwork) {
                        return Offset.Zero
                    }
                    if (available.y <= 0f && gestureCollapseProgress <= 0f) {
                        return Offset.Zero
                    }
                    if (gestureCollapseProgress <= 0f && available.y > 0f) {
                        settleJob?.cancel()
                        settleJob = null
                        gestureCollapseProgress = latestCollapseProgress.value.coerceIn(0f, 0.999f)
                    }
                    seekCollapseBy(available.y)
                    return Offset(x = 0f, y = available.y)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (latestPage.value == MeloXNowPlayingPage.Artwork ||
                        gestureCollapseProgress <= 0f
                    ) {
                        return Velocity.Zero
                    }
                    settleFromCurrent(available.y)
                    return available
                }
            }
        }

        // Alternate pages can always be dismissed by dragging the grabber even
        // when their list is currently scrolled away from the top.
        val alternateGrabberDragModifier = if (page != MeloXNowPlayingPage.Artwork) {
            Modifier.draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = { beginCollapseDrag() },
                onDragStopped = { velocity -> settleFromCurrent(velocity) },
            )
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(actionsBackdrop),
        ) {
            Box(
                modifier = sharedContainerModifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius))
                    .nestedScroll(alternatePageCollapseConnection)
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        enabled = page == MeloXNowPlayingPage.Artwork,
                        onDragStarted = { beginCollapseDrag() },
                        onDragStopped = { velocity -> settleFromCurrent(velocity) },
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(playerControlBackdrop)
                        .graphicsLayer { alpha = backdropAlpha },
                ) {
                    MeloXFlowingLightBackdrop(
                        artworkUrl = state.artworkUrl,
                        isPlaying = state.isPlaying,
                    )
                }

                CompositionLocalProvider(LocalMeloXBackdrop provides playerControlBackdrop) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = fullPlayerAlpha },
                    ) {
                        MeloXIOSNowPlayingScene(
                            state = state,
                            page = page,
                            transitionSourcePage = transitionSourcePage,
                            onDismiss = onDismiss,
                            onPageChanged = { destination ->
                                if (destination != page) {
                                    transitionSourcePage = page
                                    page = destination
                                }
                            },
                            onShowActions = { showActions = true },
                            grabberDragModifier = alternateGrabberDragModifier,
                        )
                    }
                }
            }

            SharedArtworkDestination(
                state = state,
                page = page,
                expansionProgress = expansionProgress,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }

        CompositionLocalProvider(LocalMeloXBackdrop provides actionsBackdrop) {
            MeloXNowPlayingActionsSheet(
                state = state,
                visible = showActions,
                onDismiss = { showActions = false },
            )
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
    // Upstream keeps one artwork alive and animates its frame for 0.48s when
    // switching between artwork and alternate pages.
    val headerProgress by animateFloatAsState(
        targetValue = if (page == MeloXNowPlayingPage.Artwork) 0f else 1f,
        animationSpec = tween(
            durationMillis = 480,
            easing = FastOutSlowInEasing,
        ),
        label = "persistent-artwork-page-frame",
    )

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

    val fullScreenScaleBlend = smoothStep(expansionProgress, 0.30f, 0.88f)
    val effectiveScale = 1f +
        (playbackScale - 1f) * fullScreenScaleBlend * (1f - headerProgress)

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
            // Keep the destination strictly square and fully inside the stable
            // SharedTransition coordinate space. The old maxWidth + 16dp target
            // was wider than its parent and could be clipped/scaled differently
            // on the two axes while the overlay was active.
            val fullArtworkSize = maxOf(
                170.dp,
                minOf(maxWidth, maxHeight - 92.dp),
            )
            val artworkFooterHeight = 78.dp
            val fullX = ((maxWidth - fullArtworkSize) / 2f).coerceAtLeast(0.dp)
            val fullY = (maxHeight - fullArtworkSize - artworkFooterHeight)
                .coerceAtLeast(0.dp)

            val targetSize = lerpDp(fullArtworkSize, 72.dp, headerProgress)
            val targetX = lerpDp(fullX, 0.dp, headerProgress)
            val targetY = lerpDp(fullY, 0.dp, headerProgress)
            val targetRadius = 12.dp

            val artworkSharedState = with(sharedTransitionScope) {
                rememberSharedContentState(key = sharedArtworkKey(state.mediaId))
            }
            val sharedModifier = with(sharedTransitionScope) {
                Modifier.sharedElement(
                    sharedContentState = artworkSharedState,
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = MeloXPlayerLinearBoundsTransform,
                    zIndexInOverlay = 3f,
                )
            }

            // Position and size the element *before* attaching sharedElement.
            // That makes the shared overlay capture the actual square bounds at
            // the final on-screen coordinate instead of a zero-origin box whose
            // child is offset internally. It also removes the one-frame flash
            // seen when expanding from compact MiniPlayer mode.
            Box(
                modifier = Modifier
                    .offset(x = targetX, y = targetY)
                    .size(targetSize)
                    .then(sharedModifier),
            ) {
                Artwork(
                    url = state.artworkUrl,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = effectiveScale
                            scaleY = effectiveScale
                        }
                        .shadow(
                            elevation = shadowElevation * (1f - headerProgress * 0.55f),
                            shape = RoundedCornerShape(targetRadius),
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.28f),
                            spotColor = Color.Black.copy(alpha = 0.28f),
                        )
                        .clip(RoundedCornerShape(targetRadius)),
                )
            }
        }

        Spacer(Modifier.height(MeloXNowPlayingControlsHeight.dp))
    }
}

private fun lerpDp(start: androidx.compose.ui.unit.Dp, end: androidx.compose.ui.unit.Dp, fraction: Float) =
    start + (end - start) * fraction.coerceIn(0f, 1f)

private fun smoothStep(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
