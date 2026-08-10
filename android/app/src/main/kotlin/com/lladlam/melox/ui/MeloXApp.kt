package com.lladlam.melox.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lladlam.melox.core.account.rememberNeteaseSessionStore
import com.lladlam.melox.ui.account.NeteaseLoginScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.lladlam.melox.ui.library.LibraryScreen
import com.lladlam.melox.ui.discovery.MeloXExploreScreen
import com.lladlam.melox.ui.discovery.MeloXHomeScreen
import com.lladlam.melox.ui.glass.LocalMeloXBackdrop
import com.lladlam.melox.ui.glass.meloXLiquidBottomBar
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.glass.meloXLiquidTabSelection
import com.lladlam.melox.ui.player.MeloXIOSMiniPlayer
import com.lladlam.melox.ui.player.MeloXIOSNowPlayingSharedHost
import com.lladlam.melox.ui.player.rememberMeloXPlaybackUiState
import com.lladlam.melox.ui.search.SearchScreen
import com.lladlam.melox.ui.search.MeloXSearchLaunchBus
import com.lladlam.melox.ui.settings.SettingsScreen
import com.lladlam.melox.core.network.MeloXSearchKind
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class AppTab(val title: String) {
    Home("首页"),
    Explore("发现"),
    Library("音乐库"),
    Settings("设置"),
    Search("搜索"),
}

private val MeloXAccent = Color(0xFFFF3147)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MeloXApp(
    openNowPlayingRequest: Int = 0,
) {
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    var showNeteaseLogin by remember { mutableStateOf(false) }
    var loginReturnTab by remember { mutableStateOf(AppTab.Settings) }
    var tabBarMinimized by remember { mutableStateOf(false) }
    var scrollAccumulator by remember { mutableFloatStateOf(0f) }
    var libraryModalVisible by remember { mutableStateOf(false) }
    val playbackState = rememberMeloXPlaybackUiState()
    val playerTransitionState = remember { SeekableTransitionState(false) }
    val playerTransition = rememberTransition(
        transitionState = playerTransitionState,
        label = "melox-player-transition",
    )
    val playerScope = rememberCoroutineScope()
    val openPlayer: () -> Unit = {
        if (playbackState.hasMedia) {
            playerScope.launch {
                playerTransitionState.animateTo(
                    targetState = true,
                    animationSpec = playerAutomaticFractionSpec(),
                )
            }
        }
    }
    val closePlayer: () -> Unit = {
        playerScope.launch {
            playerTransitionState.animateTo(
                targetState = false,
                animationSpec = playerAutomaticFractionSpec(),
            )
        }
    }
    val neteaseSession = rememberNeteaseSessionStore()
    val darkGlass = isSystemInDarkTheme()
    val screenControlBackdrop = rememberCanvasBackdrop {
        drawRect(
            brush = Brush.linearGradient(
                colors = if (darkGlass) {
                    listOf(Color(0xFF31323A), Color(0xFF111219))
                } else {
                    listOf(Color(0xFFFDFDFE), Color(0xFFD9DCE2))
                },
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
    }
    val bottomChromeBackdrop = rememberLayerBackdrop()

    val tabBarMinimizeConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                if (available.y < 0f) {
                    if (scrollAccumulator > 0f) scrollAccumulator = 0f
                    scrollAccumulator += available.y
                    if (scrollAccumulator <= -18f) {
                        tabBarMinimized = true
                        scrollAccumulator = 0f
                    }
                } else if (available.y > 0f) {
                    if (scrollAccumulator < 0f) scrollAccumulator = 0f
                    scrollAccumulator += available.y
                    if (scrollAccumulator >= 18f) {
                        tabBarMinimized = false
                        scrollAccumulator = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(openNowPlayingRequest, playbackState.hasMedia) {
        if (openNowPlayingRequest > 0 && playbackState.hasMedia) {
            playerTransitionState.animateTo(
                targetState = true,
                animationSpec = playerAutomaticFractionSpec(),
            )
        }
    }

    LaunchedEffect(playbackState.hasMedia) {
        if (!playbackState.hasMedia) {
            playerTransitionState.snapTo(false)
        }
    }

    LaunchedEffect(neteaseSession.cookie) {
        if (neteaseSession.isLoggedIn) {
            neteaseSession.refreshProfile()
        }
    }

    LaunchedEffect(selectedTab) {
        tabBarMinimized = false
        scrollAccumulator = 0f
        if (selectedTab != AppTab.Library) libraryModalVisible = false
    }

    CompositionLocalProvider(LocalMeloXBackdrop provides screenControlBackdrop) {
      Box(modifier = Modifier.fillMaxSize()) {
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            val sharedScope = this
            val fullPlayerVisible = playbackState.hasMedia &&
                (playerTransitionState.currentState || playerTransitionState.targetState)
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(tabBarMinimizeConnection)
                    .layerBackdrop(bottomChromeBackdrop)
                    // Library action sheets deliberately stay in the same Compose
                    // window so Liquid Glass can sample the collection behind them.
                    // Raise the whole screen while one is open; BottomChrome must
                    // never paint over the modal scrim/sheet.
                    .zIndex(if (libraryModalVisible && selectedTab == AppTab.Library && !fullPlayerVisible) 15f else 0f),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.background,
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    when (selectedTab) {
                        AppTab.Search -> SearchScreen()
                        AppTab.Home -> MeloXHomeScreen()
                        AppTab.Explore -> MeloXExploreScreen()
                        AppTab.Library -> LibraryScreen(
                            session = neteaseSession,
                            playlistBackEnabled = !fullPlayerVisible && !libraryModalVisible,
                            onModalVisibilityChanged = { libraryModalVisible = it },
                            onLogin = {
                                loginReturnTab = AppTab.Library
                                showNeteaseLogin = true
                            },
                        )
                        AppTab.Settings -> SettingsScreen(
                            session = neteaseSession,
                            onLogin = {
                                loginReturnTab = AppTab.Settings
                                showNeteaseLogin = true
                            },
                        )
                    }
                }
            }

            CompositionLocalProvider(LocalMeloXBackdrop provides bottomChromeBackdrop) {
                MeloXBottomChrome(
                    selectedTab = selectedTab,
                    onSelect = { tab ->
                        tabBarMinimized = false
                        selectedTab = tab
                    },
                    hasMedia = playbackState.hasMedia,
                    minimized = tabBarMinimized,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    miniPlayer = { compactProgress ->
                        playerTransition.AnimatedVisibility(
                            visible = { value -> !value },
                            enter = EnterTransition.None,
                            exit = ExitTransition.None,
                        ) {
                            MeloXIOSMiniPlayer(
                                state = playbackState,
                                onExpand = openPlayer,
                                compactProgress = compactProgress,
                                dynamicGlassEnabled = true,
                                sharedTransitionScope = sharedScope,
                                animatedVisibilityScope = this,
                            )
                        }
                    },
                )
            }

            if (fullPlayerVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Explicit z-order is important here: this transparent hit-test
                        // shield must sit above Scaffold/BottomChrome but below NowPlaying.
                        .zIndex(10f)
                        .pointerInput(fullPlayerVisible) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.forEach { change -> change.consume() }
                                }
                            }
                        },
                )
            }

            playerTransition.AnimatedVisibility(
                visible = { value -> value },
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(20f),
            ) {
                MeloXIOSNowPlayingSharedHost(
                    state = playbackState,
                    onDismiss = closePlayer,
                    onNavigateSearch = { query, kind ->
                        MeloXSearchLaunchBus.post(query, kind)
                        selectedTab = AppTab.Search
                        closePlayer()
                    },
                    onSeekCollapse = { fraction ->
                        playerTransitionState.seekTo(
                            fraction = fraction.coerceIn(0f, 0.999f),
                            targetState = false,
                        )
                    },
                    onSettleCollapse = { collapse ->
                        playerTransitionState.animateTo(
                            targetState = !collapse,
                            animationSpec = playerGestureSettleSpec(),
                        )
                    },
                    sharedTransitionScope = sharedScope,
                    animatedVisibilityScope = this,
                )
            }

        }

        if (showNeteaseLogin) {
            NeteaseLoginScreen(
                session = neteaseSession,
                onDismiss = { showNeteaseLogin = false },
                onLoggedIn = {
                    showNeteaseLogin = false
                    selectedTab = loginReturnTab
                },
            )
        }
      }
    }
}

@Composable
private fun MeloXSectionShell(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 48.dp),
    ) {
        Text(
            text = title,
            fontSize = 36.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = subtitle,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
        )
    }
}

@Composable
private fun MeloXBottomChrome(
    selectedTab: AppTab,
    onSelect: (AppTab) -> Unit,
    hasMedia: Boolean,
    minimized: Boolean,
    modifier: Modifier = Modifier,
    miniPlayer: @Composable (compactProgress: Float) -> Unit,
) {
    val tabsBackdrop = rememberLayerBackdrop()
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

    val navHeight = lerpDp(56.dp, 52.dp, sizeStage)
    val searchSize = lerpDp(56.dp, 52.dp, sizeStage)
    val expandedChromeHeight = if (hasMedia) 119.dp else 62.dp
    val chromeHeight = lerpDp(expandedChromeHeight, 58.dp, dropStage)
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
            val horizontalMargin = 16.dp
            val compactSize = 52.dp
            val expandedGap = 8.dp
            val compactGap = 6.dp
            val expandedNavWidth = maxWidth - horizontalMargin * 2 - expandedGap - 56.dp
            val navWidth = lerpDp(expandedNavWidth, compactSize, shrinkStage)
            val navShape = Capsule()
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
                (desiredCompactMiniVisibleWidth + 32.dp).coerceAtMost(maxWidth)
            val compactMiniWrapperX = horizontalMargin + compactSize + compactGap - 16.dp
            val miniWrapperWidth = lerpDp(maxWidth, compactMiniWrapperWidth, shrinkStage)
            val miniWrapperX = lerpDp(0.dp, compactMiniWrapperX, shrinkStage)
            val miniLift = lerpDp(62.dp, 0.dp, shrinkStage)

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
                    miniPlayer(progress)
                }
            }

            val dark = isSystemInDarkTheme()
            val selectionTint = if (dark) {
                MeloXAccent.copy(alpha = 0.28f)
            } else {
                MeloXAccent.copy(alpha = 0.16f)
            }
            val selectionBorder = if (dark) {
                Color.White.copy(alpha = 0.42f)
            } else {
                MeloXAccent.copy(alpha = 0.34f)
            }

            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = horizontalMargin, y = -3.dp)
                    .width(navWidth)
                    .height(navHeight)
                    .meloXLiquidBottomBar(
                        shape = navShape,
                        tint = bottomLiquidGlassTint(),
                        surfaceColor = bottomGlassFallbackColor().copy(alpha = 0.18f),
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
                val tabBarMaxWidthPx = constraints.maxWidth
                Box(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0f)
                            .layerBackdrop(tabsBackdrop)
                            .meloXLiquidBottomBar(
                                shape = navShape,
                                tint = bottomLiquidGlassTint(),
                                surfaceColor = bottomGlassFallbackColor().copy(alpha = 0.18f),
                            )
                            .padding(horizontal = 5.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        primaryTabs.forEach { (tab, glyph) ->
                            RootTabButton(
                                tab = tab,
                                glyph = glyph,
                                selected = selectedTab == tab,
                                labelAlpha = labelAlpha,
                                dark = dark,
                            )
                        }
                    }
                    val selectedIndex = primaryTabs.indexOfFirst { it.first == selectedTab }
                    val lensPosition by animateFloatAsState(
                        targetValue = selectedIndex.coerceAtLeast(0).toFloat(),
                        animationSpec = spring(
                            dampingRatio = 1f,
                            stiffness = 460f,
                            visibilityThreshold = 0.001f,
                        ),
                        label = "melox-tab-selection-position",
                    )
                    val lensAlpha by animateFloatAsState(
                        targetValue = if (selectedIndex >= 0 && progress < 0.56f) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.86f, stiffness = 440f),
                        label = "melox-tab-selection-alpha",
                    )
                    val lensVisibility = lensAlpha * expandedLayerAlpha
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.25f)
                            .fillMaxHeight()
                            .offset {
                                IntOffset(
                                    x = (
                                        lensPosition * tabBarMaxWidthPx / 4f
                                        ).roundToInt(),
                                    y = 0,
                                )
                            }
                            .padding(4.dp)
                            .meloXLiquidTabSelection(
                                shape = Capsule(),
                                selected = lensVisibility > 0.001f,
                                panelBackdrop = tabsBackdrop,
                                tint = selectionTint.copy(
                                    alpha = selectionTint.alpha * lensVisibility,
                                ),
                            )
                            .border(
                                width = if (dark) 0.9.dp else 0.8.dp,
                                color = selectionBorder.copy(
                                    alpha = selectionBorder.alpha * lensVisibility,
                                ),
                                shape = Capsule(),
                            ),
                    )
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
                                dark = dark,
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
                                modifier = Modifier.size(25.dp),
                                color = if (selectedTab == AppTab.Search) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MeloXAccent
                                },
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = -horizontalMargin, y = -3.dp)
                    .size(searchSize)
                    .meloXLiquidButton(
                        shape = Capsule(),
                        tint = bottomLiquidGlassTint(),
                        blurRadius = 6.dp,
                        lensRadius = 12.dp,
                        refractionHeight = 18.dp,
                        surfaceColor = bottomGlassFallbackColor().copy(alpha = 0.16f),
                    )
                    .clickable { onSelect(AppTab.Search) },
                contentAlignment = Alignment.Center,
            ) {
                RootGlyphIcon(
                    glyph = RootGlyph.Search,
                    modifier = Modifier.size(lerpDp(28.dp, 27.dp, sizeStage)),
                    color = if (selectedTab == AppTab.Search) {
                        MeloXAccent
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun bottomLiquidGlassTint(): Color =
    if (isSystemInDarkTheme()) {
        Color.Black.copy(alpha = 0.10f)
    } else {
        Color.White.copy(alpha = 0.12f)
    }

@Composable
private fun bottomGlassFallbackColor(): Color =
    if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
    } else {
        Color.White.copy(alpha = 0.56f)
    }

@Composable
private fun RowScope.RootTabButton(
    tab: AppTab,
    glyph: RootGlyph,
    selected: Boolean,
    labelAlpha: Float,
    dark: Boolean,
) {
    val foreground by animateColorAsState(
        targetValue = if (selected) {
            MeloXAccent
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
        },
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 480f),
        label = "melox-tab-foreground",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RootGlyphIcon(glyph = glyph, modifier = Modifier.size(24.dp), color = foreground)
        Text(
            text = tab.title,
            modifier = Modifier.graphicsLayer { alpha = labelAlpha },
            fontSize = 9.sp,
            lineHeight = 11.sp,
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
                val p = Path().apply {
                    moveTo(w * 0.26f, h * 0.22f)
                    lineTo(w * 0.26f, h * 0.72f)
                    cubicTo(w * 0.26f, h * 0.83f, w * 0.10f, h * 0.84f, w * 0.10f, h * 0.70f)
                    cubicTo(w * 0.10f, h * 0.57f, w * 0.29f, h * 0.55f, w * 0.37f, h * 0.62f)
                    lineTo(w * 0.37f, h * 0.28f)
                    lineTo(w * 0.83f, h * 0.18f)
                    lineTo(w * 0.83f, h * 0.61f)
                    cubicTo(w * 0.83f, h * 0.74f, w * 0.66f, h * 0.77f, w * 0.61f, h * 0.66f)
                }
                drawPath(p, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
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

private fun playerAutomaticFractionSpec() = tween<Float>(
    durationMillis = 460,
    easing = FastOutSlowInEasing,
)

private fun playerGestureSettleSpec() = spring<Float>(
    dampingRatio = 1.0f,
    stiffness = 420f,
    visibilityThreshold = 0.001f,
)

private fun smoothStep(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun lerpDp(start: Dp, end: Dp, progress: Float): Dp =
    (start.value + (end.value - start.value) * progress.coerceIn(0f, 1f)).dp
