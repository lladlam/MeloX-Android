package com.lladlam.melox.ui.player

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.lladlam.melox.ui.glass.LocalMeloXBackdrop
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXScreenAwakeMode
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import com.lladlam.melox.core.network.MeloXSearchKind
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MeloXIOSNowPlayingSharedHost(
    state: MeloXPlaybackUiState,
    onDismiss: () -> Unit,
    onNavigateSearch: (String, MeloXSearchKind) -> Unit = { _, _ -> },
    onSeekCollapse: suspend (Float) -> Unit,
    onSettleCollapse: suspend (Boolean) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    // A track transition updates the content inside the existing player. It must
    // not recreate the page/gesture state or send the shared element back to its
    // MiniPlayer bounds while the full-screen player is still open.
    val context = LocalContext.current.applicationContext
    val rememberPlayerPage = MeloXSettingsPreferences.boolean(context, "playback_remember_page", true)
    var page by remember(rememberPlayerPage) {
        mutableStateOf(
            if (rememberPlayerPage) runCatching {
                MeloXNowPlayingPage.valueOf(
                    MeloXSettingsPreferences.string(context, "playback_last_page", MeloXNowPlayingPage.Artwork.name),
                )
            }.getOrDefault(MeloXNowPlayingPage.Artwork) else MeloXNowPlayingPage.Artwork,
        )
    }
    var transitionSourcePage by remember {
        mutableStateOf(MeloXNowPlayingPage.Artwork)
    }
    var showActions by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var showLandscapeSkyline by remember { mutableStateOf(false) }
    var lyricsInterfaceHidden by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    var gestureCollapseProgress by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val hostView = LocalView.current
    LaunchedEffect(isLandscape) {
        if (!isLandscape) showLandscapeSkyline = false else lyricsInterfaceHidden = false
    }
    val skylineVisible = isLandscape && page == MeloXNowPlayingPage.Lyrics &&
        (MeloXSettingsRuntime.skylineEnabled || showLandscapeSkyline)
    DisposableEffect(
        MeloXSettingsRuntime.screenAwakeMode,
        MeloXSettingsRuntime.skylineKeepsScreenAwake,
        page,
        lyricsInterfaceHidden,
        skylineVisible,
    ) {
        val previous = hostView.keepScreenOn
        hostView.keepScreenOn = (skylineVisible && MeloXSettingsRuntime.skylineKeepsScreenAwake) || when (MeloXSettingsRuntime.screenAwakeMode) {
            MeloXScreenAwakeMode.Disabled -> false
            MeloXScreenAwakeMode.Player -> true
            MeloXScreenAwakeMode.Lyrics -> page == MeloXNowPlayingPage.Lyrics
            MeloXScreenAwakeMode.HiddenLyricsInterface ->
                page == MeloXNowPlayingPage.Lyrics && lyricsInterfaceHidden
        }
        onDispose { hostView.keepScreenOn = previous }
    }

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
                key = sharedPlayerContainerKey(),
            ),
            animatedVisibilityScope = animatedVisibilityScope,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            boundsTransform = MeloXPlayerLinearBoundsTransform,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }

    // NowPlaying owns the player-level Back handler. Child modal overlays are
    // composed later and temporarily disable this handler, so Back always unwinds
    // the topmost visual layer before the player itself is dismissed.
    BackHandler(enabled = !showActions && !showQuality) {
        if (showLandscapeSkyline) showLandscapeSkyline = false else onDismiss()
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
                    if (MeloXSettingsRuntime.flowingBackdropEnabled) {
                        MeloXFlowingLightBackdrop(
                            mediaId = state.mediaId,
                            artworkUrl = state.artworkUrl,
                            isPlaying = state.isPlaying,
                        )
                    } else {
                        MeloXBlurredArtworkBackdrop(state.artworkUrl)
                    }
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
                                showLandscapeSkyline = false
                                if (destination != page) {
                                    transitionSourcePage = page
                                    page = destination
                                    if (rememberPlayerPage) {
                                        MeloXSettingsPreferences.setString(context, "playback_last_page", destination.name)
                                    }
                                }
                            },
                            onShowActions = {
                                showQuality = false
                                showActions = true
                            },
                            onShowQuality = {
                                showActions = false
                                showQuality = true
                            },
                            showLandscapeSkyline = showLandscapeSkyline,
                            onShowLandscapeSkyline = { showLandscapeSkyline = true },
                            onHideLandscapeSkyline = { showLandscapeSkyline = false },
                            onLyricsInterfaceHiddenChange = { lyricsInterfaceHidden = it },
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
                hidden = showLandscapeSkyline,
            )
        }

        CompositionLocalProvider(LocalMeloXBackdrop provides actionsBackdrop) {
            MeloXNowPlayingActionsSheet(
                state = state,
                visible = showActions,
                onDismiss = { showActions = false },
                onNavigateSearch = onNavigateSearch,
            )
            MeloXQualitySelectionOverlay(
                state = state,
                visible = showQuality,
                onDismiss = { showQuality = false },
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
    hidden: Boolean = false,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
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
        targetValue = if (!MeloXSettingsRuntime.artworkMotionEnabled || state.isPlaying) 1f else 0.74f,
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

    val pageFrameProgress = if (isLandscape) 0f else headerProgress
    val fullScreenScaleBlend = smoothStep(expansionProgress, 0.30f, 0.88f)
    val effectiveScale = 1f +
        (playbackScale - 1f) * fullScreenScaleBlend * (1f - pageFrameProgress)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = if (isLandscape) 20.dp else 32.dp),
    ) {
        val contentTop = 30.dp
        val portraitContentHeight = (maxHeight - contentTop - MeloXNowPlayingControlsHeight.dp)
            .coerceAtLeast(1.dp)
        val fullArtworkSize = if (isLandscape) {
            maxOf(170.dp, minOf(460.dp, maxHeight - 42.dp, maxWidth * .43f))
        } else {
            maxOf(170.dp, minOf(maxWidth, portraitContentHeight - 92.dp))
        }
        val artworkFooterHeight = 78.dp
        val fullX = if (isLandscape) {
            ((maxWidth * .43f - fullArtworkSize) / 2f).coerceAtLeast(0.dp)
        } else {
            ((maxWidth - fullArtworkSize) / 2f).coerceAtLeast(0.dp)
        }
        val fullY = if (isLandscape) {
            contentTop + ((maxHeight - contentTop - fullArtworkSize) / 2f).coerceAtLeast(0.dp)
        } else {
            contentTop + (portraitContentHeight - fullArtworkSize - artworkFooterHeight)
                .coerceAtLeast(0.dp)
        }

        val targetSize = lerpDp(fullArtworkSize, 72.dp, pageFrameProgress)
        val targetX = lerpDp(fullX, 0.dp, pageFrameProgress)
        val targetY = lerpDp(fullY, contentTop, pageFrameProgress)
        val targetRadius = 12.dp

        val artworkSharedState = with(sharedTransitionScope) {
            rememberSharedContentState(key = sharedArtworkKey())
        }
        val sharedModifier = with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = artworkSharedState,
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = MeloXPlayerLinearBoundsTransform,
                zIndexInOverlay = 3f,
            )
        }

        Box(
            modifier = Modifier
                .offset(x = targetX, y = targetY)
                .size(targetSize)
                .graphicsLayer { alpha = if (hidden) 0f else 1f }
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
                        elevation = shadowElevation * (1f - pageFrameProgress * 0.55f),
                        shape = RoundedCornerShape(targetRadius),
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.28f),
                        spotColor = Color.Black.copy(alpha = 0.28f),
                    )
                    .clip(RoundedCornerShape(targetRadius)),
            )
        }
    }
}

private fun lerpDp(start: androidx.compose.ui.unit.Dp, end: androidx.compose.ui.unit.Dp, fraction: Float) =
    start + (end - start) * fraction.coerceIn(0f, 1f)

private fun smoothStep(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
