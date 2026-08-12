package com.lladlam.melox.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lladlam.melox.ui.glass.meloXBackdropBlur
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.playback.MeloXPlaybackModeRuntime
import kotlinx.coroutines.delay

/**
 * The portrait Now Playing scene mirrors the upstream iOS page architecture:
 * artwork details are transient, Lyrics and Queue live as resident layers, the
 * song header is shared by alternate pages, and the bottom controls never leave
 * the composition while pages change.
 *
 * The actual artwork is intentionally not drawn here. SharedHost owns one
 * persistent artwork layer so the same image can move between the full artwork
 * frame and the 72dp alternate-page header. Outer dismissal is page-aware: the
 * Artwork/Queue image can continue into MiniPlayer, while Lyrics lets MiniPlayer
 * own the waiting artwork at its final location.
 */
@Composable
internal fun MeloXIOSNowPlayingScene(
    state: MeloXPlaybackUiState,
    page: MeloXNowPlayingPage,
    transitionSourcePage: MeloXNowPlayingPage,
    onDismiss: () -> Unit,
    onPageChanged: (MeloXNowPlayingPage) -> Unit,
    onShowActions: () -> Unit,
    onShowQuality: () -> Unit,
    showLandscapeSkyline: Boolean = false,
    onShowLandscapeSkyline: () -> Unit = {},
    onHideLandscapeSkyline: () -> Unit = {},
    onLyricsInterfaceHiddenChange: (Boolean) -> Unit = {},
    grabberDragModifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    if (configuration.screenWidthDp > configuration.screenHeightDp) {
        MeloXIOSLandscapeNowPlayingScene(
            state = state,
            page = page,
            onDismiss = onDismiss,
            onPageChanged = onPageChanged,
            onShowActions = onShowActions,
            onShowQuality = onShowQuality,
            showSkyline = showLandscapeSkyline,
            onShowSkyline = onShowLandscapeSkyline,
            onHideSkyline = onHideLandscapeSkyline,
            grabberDragModifier = grabberDragModifier,
        )
        return
    }
    val directLyricsQueue = isDirectLyricsQueueTransition(transitionSourcePage, page)

    val artworkVisible = page == MeloXNowPlayingPage.Artwork
    val lyricsVisible = page == MeloXNowPlayingPage.Lyrics
    val queueVisible = page == MeloXNowPlayingPage.Queue
    var showsLyricsControls by remember(state.mediaId) { mutableStateOf(true) }
    var lyricsControlsActivityGeneration by remember(state.mediaId) { mutableIntStateOf(0) }

    LaunchedEffect(page, showsLyricsControls) {
        onLyricsInterfaceHiddenChange(
            page == MeloXNowPlayingPage.Lyrics && !showsLyricsControls,
        )
    }

    fun setLyricsControlsVisible(visible: Boolean) {
        showsLyricsControls = visible
        if (visible) lyricsControlsActivityGeneration += 1
    }

    LaunchedEffect(page) {
        showsLyricsControls = true
        if (page == MeloXNowPlayingPage.Lyrics) lyricsControlsActivityGeneration += 1
    }
    LaunchedEffect(page, showsLyricsControls, lyricsControlsActivityGeneration) {
        if (page != MeloXNowPlayingPage.Lyrics || !showsLyricsControls) return@LaunchedEffect
        delay(MeloXSettingsRuntime.lyricInterfaceAutoHideDelayMs.toLong())
        showsLyricsControls = false
    }

    val artworkAlpha by animateFloatAsState(
        targetValue = if (artworkVisible) 1f else 0f,
        animationSpec = if (artworkVisible) {
            tween(durationMillis = 220, delayMillis = 70, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 240, easing = FastOutSlowInEasing)
        },
        label = "scene-artwork-details-alpha",
    )
    val artworkOffset by animateDpAsState(
        targetValue = if (artworkVisible) 0.dp else (-300).dp,
        animationSpec = if (artworkVisible) {
            tween(durationMillis = 220, delayMillis = 70, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 240, easing = FastOutSlowInEasing)
        },
        label = "scene-artwork-details-offset",
    )

    val lyricsAlpha by animateFloatAsState(
        targetValue = if (lyricsVisible) 1f else 0f,
        animationSpec = when {
            directLyricsQueue -> tween(440, easing = FastOutSlowInEasing)
            lyricsVisible -> tween(340, delayMillis = 110, easing = FastOutSlowInEasing)
            transitionSourcePage == MeloXNowPlayingPage.Lyrics -> tween(240, easing = FastOutSlowInEasing)
            else -> tween(120)
        },
        label = "scene-lyrics-alpha",
    )
    val lyricsOffset by animateDpAsState(
        targetValue = if (page == MeloXNowPlayingPage.Artwork) 400.dp else 0.dp,
        animationSpec = when {
            directLyricsQueue -> tween(440, easing = FastOutSlowInEasing)
            lyricsVisible -> tween(340, delayMillis = 110, easing = FastOutSlowInEasing)
            transitionSourcePage == MeloXNowPlayingPage.Lyrics -> tween(240, easing = FastOutSlowInEasing)
            else -> tween(120)
        },
        label = "scene-lyrics-offset",
    )
    val lyricsScale by animateFloatAsState(
        targetValue = if (page == MeloXNowPlayingPage.Queue) 0.92f else 1f,
        animationSpec = if (directLyricsQueue) {
            tween(440, easing = FastOutSlowInEasing)
        } else {
            tween(240, easing = FastOutSlowInEasing)
        },
        label = "scene-lyrics-scale",
    )

    val queueAlpha by animateFloatAsState(
        targetValue = if (queueVisible) 1f else 0f,
        animationSpec = when {
            directLyricsQueue -> tween(440, easing = FastOutSlowInEasing)
            queueVisible -> tween(340, delayMillis = 110, easing = FastOutSlowInEasing)
            transitionSourcePage == MeloXNowPlayingPage.Queue -> tween(240, easing = FastOutSlowInEasing)
            else -> tween(120)
        },
        label = "scene-queue-alpha",
    )
    val queueOffset by animateDpAsState(
        targetValue = if (page == MeloXNowPlayingPage.Artwork) 400.dp else 0.dp,
        animationSpec = when {
            directLyricsQueue -> tween(440, easing = FastOutSlowInEasing)
            queueVisible -> tween(340, delayMillis = 110, easing = FastOutSlowInEasing)
            transitionSourcePage == MeloXNowPlayingPage.Queue -> tween(240, easing = FastOutSlowInEasing)
            else -> tween(120)
        },
        label = "scene-queue-offset",
    )
    val queueScale by animateFloatAsState(
        targetValue = if (page == MeloXNowPlayingPage.Lyrics) 0.92f else 1f,
        animationSpec = if (directLyricsQueue) {
            tween(440, easing = FastOutSlowInEasing)
        } else {
            tween(240, easing = FastOutSlowInEasing)
        },
        label = "scene-queue-scale",
    )

    val headerAlpha by animateFloatAsState(
        targetValue = if (artworkVisible) 0f else 1f,
        animationSpec = if (artworkVisible) {
            tween(240, easing = FastOutSlowInEasing)
        } else {
            tween(400, delayMillis = 80, easing = FastOutSlowInEasing)
        },
        label = "scene-song-header-alpha",
    )
    val headerOffset by animateDpAsState(
        targetValue = if (artworkVisible) 40.dp else 0.dp,
        animationSpec = if (artworkVisible) {
            tween(240, easing = FastOutSlowInEasing)
        } else {
            tween(400, delayMillis = 80, easing = FastOutSlowInEasing)
        },
        label = "scene-song-header-offset",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 32.dp),
        ) {
        SceneGrabber(
            onDismiss = onDismiss,
            dragModifier = grabberDragModifier,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (artworkVisible) 2f else 0f)
                    .graphicsLayer {
                        alpha = artworkAlpha
                        translationY = artworkOffset.toPx()
                    },
            ) {
                ArtworkDetailsWithoutArtwork(
                    state = state,
                    onShowActions = onShowActions,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (lyricsVisible) 2f else 0f)
                    .graphicsLayer {
                        alpha = lyricsAlpha
                        translationY = lyricsOffset.toPx()
                        scaleX = lyricsScale
                        scaleY = lyricsScale
                    }
                    .padding(top = 88.dp),
            ) {
                MeloXIOSLyricsPanel(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    isInterfaceHidden = !showsLyricsControls,
                    onInterfaceInteraction = { setLyricsControlsVisible(true) },
                    onInterfaceVisibilityChange = { setLyricsControlsVisible(it) },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (queueVisible) 2f else 0f)
                    .graphicsLayer {
                        alpha = queueAlpha
                        translationY = queueOffset.toPx()
                        scaleX = queueScale
                        scaleY = queueScale
                    }
                    // Queue rows end above the persistent playback controls;
                    // none of the list is hidden beneath the blurred control zone.
                    .padding(
                        top = 80.dp,
                        bottom = MeloXNowPlayingControlsHeight.dp,
                    ),
            ) {
                MeloXQueuePanel(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    showSongHeader = false,
                    interactive = queueVisible,
                )
            }

            val songHeaderShape = RoundedCornerShape(20.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .zIndex(3f)
                    .graphicsLayer {
                        alpha = headerAlpha
                        translationY = headerOffset.toPx()
                    }
                    .then(
                        if (queueVisible) {
                            Modifier
                                .clip(songHeaderShape)
                                .meloXBackdropBlur(
                                    shape = songHeaderShape,
                                    blurRadius = 20.dp,
                                    surfaceColor = Color.Black.copy(alpha = .10f),
                                )
                        } else Modifier
                    )
                    .padding(start = 84.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (MeloXPlaybackModeRuntime.heartModeActive) {
                        Text("心动模式", color = Color(0xFFFF7BA5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = state.title.ifBlank { "正在播放" },
                        color = Color.White,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.artist,
                        color = Color.White.copy(alpha = 0.64f),
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !artworkVisible, onClick = onShowActions),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "•••",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        }

        AnimatedVisibility(
            visible = page != MeloXNowPlayingPage.Lyrics || showsLyricsControls,
            modifier = Modifier
                .align(Alignment.BottomCenter),
            enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 8 },
            exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it / 8 },
        ) {
            val controlsSurface = if (page != MeloXNowPlayingPage.Artwork) {
                Modifier
                    .fillMaxWidth()
                    .height(MeloXNowPlayingControlsHeight.dp)
                    .meloXBackdropBlur(
                        shape = RectangleShape,
                        blurRadius = 24.dp,
                        surfaceColor = Color.Black.copy(alpha = .08f),
                    )
                    .padding(horizontal = 32.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            }
            Box(modifier = controlsSurface) {
                MeloXNowPlayingCoreControls(
                    state = state,
                    page = page,
                    onShowQuality = onShowQuality,
                    onPageSelected = { destination ->
                        setLyricsControlsVisible(true)
                        onPageChanged(
                            if (page == destination) MeloXNowPlayingPage.Artwork else destination,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun MeloXIOSLandscapeNowPlayingScene(
    state: MeloXPlaybackUiState,
    page: MeloXNowPlayingPage,
    onDismiss: () -> Unit,
    onPageChanged: (MeloXNowPlayingPage) -> Unit,
    onShowActions: () -> Unit,
    onShowQuality: () -> Unit,
    showSkyline: Boolean,
    onShowSkyline: () -> Unit,
    onHideSkyline: () -> Unit,
    grabberDragModifier: Modifier,
) {
    var showsLyricsControls by remember(state.mediaId) { mutableStateOf(true) }
    var activityGeneration by remember(state.mediaId) { mutableIntStateOf(0) }

    fun select(destination: MeloXNowPlayingPage) {
        showsLyricsControls = true
        activityGeneration += 1
        onPageChanged(if (page == destination) MeloXNowPlayingPage.Artwork else destination)
    }

    LaunchedEffect(page) {
        showsLyricsControls = true
        activityGeneration += 1
    }
    LaunchedEffect(page, showsLyricsControls, activityGeneration) {
        if (page != MeloXNowPlayingPage.Lyrics || !showsLyricsControls) return@LaunchedEffect
        delay(5_000L)
        showsLyricsControls = false
    }

    if (showSkyline && page == MeloXNowPlayingPage.Lyrics) {
        Box(Modifier.fillMaxSize()) {
            MeloXSkylineLyricsPanel(
                playback = state,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = .24f))
                    .clickable(onClick = onHideSkyline)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "退出天际歌词",
                    color = Color.White.copy(alpha = .86f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 2.dp),
    ) {
        SceneGrabber(onDismiss = onDismiss, dragModifier = grabberDragModifier)

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // The shared host owns the only artwork instance in this empty left
            // column, so rotations and page changes never duplicate the cover.
            Spacer(Modifier.weight(.43f).fillMaxHeight())
            Spacer(Modifier.width(24.dp))

            Column(modifier = Modifier.weight(.57f).fillMaxHeight()) {
                LandscapeSongHeader(
                    state = state,
                    onShowActions = onShowActions,
                    onShowSkyline = if (
                        page == MeloXNowPlayingPage.Lyrics && MeloXSettingsRuntime.skylineEnabled
                    ) onShowSkyline else null,
                )

                AnimatedContent(
                    targetState = page,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
                    label = "landscape-player-page",
                ) { destination ->
                    when (destination) {
                        MeloXNowPlayingPage.Artwork -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            MeloXNowPlayingCoreControls(
                                state = state,
                                page = page,
                                onShowQuality = onShowQuality,
                                onPageSelected = ::select,
                            )
                        }

                        MeloXNowPlayingPage.Lyrics -> Box(Modifier.fillMaxSize()) {
                            MeloXIOSLyricsPanel(
                                state = state,
                                modifier = Modifier.fillMaxSize().padding(bottom = 50.dp),
                                isInterfaceHidden = !showsLyricsControls,
                                onInterfaceInteraction = {
                                    showsLyricsControls = true
                                    activityGeneration += 1
                                },
                                onInterfaceVisibilityChange = {
                                    showsLyricsControls = it
                                    if (it) activityGeneration += 1
                                },
                                allowAutomaticSkyline = true,
                            )
                            LandscapeLyricsPageSelector(
                                visible = showsLyricsControls,
                                modifier = Modifier.align(Alignment.BottomCenter),
                                state = state,
                                page = page,
                                onPageSelected = ::select,
                            )
                        }

                        MeloXNowPlayingPage.Queue -> Box(Modifier.fillMaxSize()) {
                            MeloXQueuePanel(
                                state = state,
                                modifier = Modifier.fillMaxSize().padding(bottom = 50.dp),
                                showSongHeader = false,
                                interactive = true,
                            )
                            ScenePageSelector(
                                state = state,
                                page = page,
                                onPageSelected = ::select,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LandscapeLyricsPageSelector(
    visible: Boolean,
    state: MeloXPlaybackUiState,
    page: MeloXNowPlayingPage,
    onPageSelected: (MeloXNowPlayingPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(160)),
    ) {
        ScenePageSelector(state = state, page = page, onPageSelected = onPageSelected)
    }
}

@Composable
private fun LandscapeSongHeader(
    state: MeloXPlaybackUiState,
    onShowActions: () -> Unit,
    onShowSkyline: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (MeloXPlaybackModeRuntime.heartModeActive) {
                Text("心动模式", color = Color(0xFFFF7BA5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = state.title.ifBlank { "正在播放" },
                color = Color.White,
                fontSize = 19.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.artist,
                color = Color.White.copy(alpha = .58f),
                fontSize = 15.sp,
                lineHeight = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onShowSkyline != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onShowSkyline),
                contentAlignment = Alignment.Center,
            ) {
                Text("↗", color = Color.White.copy(alpha = .84f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onShowActions),
            contentAlignment = Alignment.Center,
        ) {
            Text("•••", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SceneGrabber(
    onDismiss: () -> Unit,
    dragModifier: Modifier,
) {
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "scene-grabber-scale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .then(dragModifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(width = 60.dp, height = 5.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.52f)),
        )
    }
}

@Composable
private fun ArtworkDetailsWithoutArtwork(
    state: MeloXPlaybackUiState,
    onShowActions: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val artworkSize = maxOf(
            170.dp,
            minOf(maxWidth, maxHeight - 92.dp),
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(artworkSize))
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title.ifBlank { "正在播放" },
                        color = Color.White,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.artist,
                        color = Color.White.copy(alpha = 0.64f),
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onShowActions),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "•••",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.height(MeloXNowPlayingControlsHeight.dp))
        }
    }
}

private fun isDirectLyricsQueueTransition(
    source: MeloXNowPlayingPage,
    target: MeloXNowPlayingPage,
): Boolean =
    (source == MeloXNowPlayingPage.Lyrics && target == MeloXNowPlayingPage.Queue) ||
        (source == MeloXNowPlayingPage.Queue && target == MeloXNowPlayingPage.Lyrics)
