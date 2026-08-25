package com.lladlam.melox.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lladlam.melox.core.account.rememberNeteaseSessionStore
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.BuildConfig
import com.lladlam.melox.R
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.provider.MusicProviderSelectionStore
import com.lladlam.melox.ui.account.NeteaseLoginScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.lladlam.melox.ui.library.LibraryScreen
import com.lladlam.melox.ui.legal.MELOX_LEGAL_VERSION
import com.lladlam.melox.ui.legal.MeloXFirstLaunchLegalConsent
import com.lladlam.melox.ui.legal.MeloXCloudControlConsentDialog
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigConsent
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigSource
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigRuntime
import com.lladlam.melox.core.remoteconfig.MeloXRemoteNotice
import com.lladlam.melox.core.remoteconfig.MeloXRemoteNoticeStore
import com.lladlam.melox.ui.legal.MeloXRemoteNoticeDialog
import com.lladlam.melox.ui.messages.MessagesScreen
import com.lladlam.melox.ui.podcast.MeloXPodcastScreen
import com.lladlam.melox.ui.cloud.MeloXCloudMusicScreen
import com.lladlam.melox.ui.discovery.MeloXExploreScreen
import com.lladlam.melox.ui.discovery.MeloXHomeScreen
import com.lladlam.melox.ui.glass.LocalMeloXBackdrop
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXSymbolVariant
import com.lladlam.melox.ui.glass.MeloXSystemColors
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.theme.isMeloXDarkTheme
import com.lladlam.melox.ui.glass.meloXLiquidBottomBar
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.glass.meloXLiquidTabSelection
import com.lladlam.melox.ui.glass.publicdemo.PublicDampedDragAnimation
import com.lladlam.melox.ui.player.MeloXIOSMiniPlayer
import com.lladlam.melox.ui.player.MeloXProviderLyricsLoader
import com.lladlam.melox.ui.player.MeloXIOSNowPlayingSharedHost
import com.lladlam.melox.ui.player.rememberMeloXPlaybackUiState
import com.lladlam.melox.ui.provider.ProviderExploreScreen
import com.lladlam.melox.ui.provider.ProviderHomeScreen
import com.lladlam.melox.ui.provider.ProviderLibraryScreen
import com.lladlam.melox.ui.provider.ProviderSearchScreen
import com.lladlam.melox.ui.provider.ProviderSettingsHub
import com.lladlam.melox.ui.provider.ProviderServicesScreen
import com.lladlam.melox.ui.search.SearchScreen
import com.lladlam.melox.ui.search.MeloXSearchLaunchBus
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.core.network.MeloXSearchKind
import com.lladlam.melox.core.network.NeteaseClipboardLink
import com.lladlam.melox.core.network.NeteaseClipboardTarget
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.update.MeloXRelease
import com.lladlam.melox.core.update.MeloXUpdateClient
import com.lladlam.melox.playback.PlaybackCommands
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class AppTab(@StringRes val titleRes: Int) {
    Home(R.string.tab_home),
    Explore(R.string.tab_explore),
    Library(R.string.tab_library),
    Podcasts(R.string.tab_podcasts),
    Downloads(R.string.tab_downloads),
    Cloud(R.string.tab_cloud),
    Settings(R.string.tab_settings),
    Services(R.string.tab_services),
    Search(R.string.tab_search),
}


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MeloXApp(
    openNowPlayingRequest: Int = 0,
    clipboardLinkRequest: String? = null,
    onClipboardLinkConsumed: () -> Unit = {},
    playbackConnectionEnabled: Boolean = true,
) {
    val hostContext = LocalContext.current
    val context = hostContext.applicationContext
    var selectedSource by remember {
        mutableStateOf(MusicProviderSelectionStore.selectedSource(context))
    }
    val initialTab = remember(context) {
        runCatching {
            AppTab.valueOf(
                if (MeloXSettingsRuntime.rememberLastTab) {
                    MeloXSettingsPreferences.string(context, "general_last_tab", MeloXSettingsRuntime.defaultTab)
                } else MeloXSettingsRuntime.defaultTab,
            )
        }.getOrDefault(AppTab.Home)
    }
    var selectedTab by remember { mutableStateOf(initialTab) }
    var settingsRouteRequest by remember { mutableStateOf<String?>(null) }
    var messagesVisible by remember { mutableStateOf(false) }
    BackHandler(enabled = messagesVisible) { messagesVisible = false }
    var showNeteaseLogin by remember { mutableStateOf(false) }
    var loginReturnTab by remember { mutableStateOf(AppTab.Settings) }
    var tabBarMinimized by remember { mutableStateOf(false) }
    var scrollAccumulator by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(tabBarMinimized) {
        if (com.lladlam.melox.ui.settings.MeloXSettingsRuntime.hapticFeedbackEnabled)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    var libraryModalVisible by remember { mutableStateOf(false) }
    var onboardingPage by remember {
        mutableStateOf(if (MeloXSettingsPreferences.boolean(context, "onboarding_completed", false)) -1 else 0)
    }
    var availableUpdate by remember { mutableStateOf<MeloXRelease?>(null) }
    val remoteConfigStatus by MeloXRemoteConfigRuntime.status.collectAsState()
    var pendingRemoteNotice by remember { mutableStateOf<MeloXRemoteNotice?>(null) }
    var cloudControlChoicePending by remember {
        mutableStateOf(!MeloXRemoteConfigConsent.choiceMade(context))
    }
    LaunchedEffect(remoteConfigStatus, cloudControlChoicePending) {
        pendingRemoteNotice = remoteConfigStatus.config.notice?.takeIf {
            !cloudControlChoicePending &&
                MeloXRemoteConfigConsent.enabled(context) &&
                remoteConfigStatus.source == MeloXRemoteConfigSource.VerifiedRemote &&
                MeloXRemoteNoticeStore.shouldShow(context, it)
        }
    }
    var heartModeLaunchAttempted by remember { mutableStateOf(false) }
    val playbackState = rememberMeloXPlaybackUiState(connectionEnabled = playbackConnectionEnabled)
    val playerTransitionState = remember { SeekableTransitionState(false) }
    val playerTransition = rememberTransition(
        transitionState = playerTransitionState,
        label = "melox-player-transition",
    )
    val playerScope = rememberCoroutineScope()
    val clipboardTarget = remember(clipboardLinkRequest, selectedSource) {
        if (selectedSource == MusicSource.Netease) {
            clipboardLinkRequest?.let(NeteaseClipboardLink::parse)
        } else {
            null
        }
    }
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
    val rootPageState = rememberSaveableStateHolder()
    // Page glass samples a stable background layer. The page itself is
    // recorded separately for the bottom chrome, so neither layer samples
    // its own controls and HWUI never enters a recursive RenderNode graph.
    val pageBackdrop = rememberLayerBackdrop()
    val bottomChromeBackdrop = rememberLayerBackdrop()

    LaunchedEffect(playbackConnectionEnabled) {
        if (!playbackConnectionEnabled) return@LaunchedEffect
        if (MeloXSettingsPreferences.boolean(context, "update_auto_check", true)) {
            val now = System.currentTimeMillis()
            val last = MeloXSettingsPreferences.string(context, "update_last_check_ms", "0").toLongOrNull() ?: 0L
            if (now - last >= 24L * 60L * 60L * 1000L) {
                val client = MeloXUpdateClient(context)
                runCatching { client.latestStableRelease() }.getOrNull()?.let { release ->
                    MeloXSettingsPreferences.setString(context, "update_last_check_ms", now.toString())
                    if (client.isNewer(release.version, BuildConfig.VERSION_NAME)) availableUpdate = release
                }
            }
        }
    }

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

    LaunchedEffect(clipboardLinkRequest, clipboardTarget, selectedSource) {
        if (clipboardLinkRequest != null && clipboardTarget == null) onClipboardLinkConsumed()
    }

    LaunchedEffect(playbackState.hasMedia) {
        if (!playbackState.hasMedia) {
            playerTransitionState.snapTo(false)
        }
    }

    LaunchedEffect(
        playbackState.currentIndex,
        playbackState.queue.map { it.mediaId },
        MeloXSettingsRuntime.automaticLyricSelectionEnabled,
    ) {
        if (playbackState.hasMedia) {
            MeloXProviderLyricsLoader.preloadQueue(context, playbackState, count = 2)
        }
    }

    LaunchedEffect(neteaseSession.cookie, playbackConnectionEnabled) {
        if (playbackConnectionEnabled && neteaseSession.isLoggedIn) {
            neteaseSession.refreshProfile()
        }
    }

    LaunchedEffect(
        selectedSource,
        neteaseSession.cookie,
        onboardingPage,
        MeloXSettingsRuntime.startsHeartModeOnLaunch,
        playbackState.hasMedia,
        playbackConnectionEnabled,
    ) {
        if (!playbackConnectionEnabled || selectedSource != MusicSource.Netease || heartModeLaunchAttempted ||
            onboardingPage >= 0 || playbackState.hasMedia ||
            !MeloXSettingsRuntime.startsHeartModeOnLaunch || !neteaseSession.isLoggedIn
        ) return@LaunchedEffect
        heartModeLaunchAttempted = true
        if (neteaseSession.profile == null) neteaseSession.refreshProfile(force = true)
        val userId = neteaseSession.profile?.userId ?: return@LaunchedEffect
        val client = NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
        val songs = runCatching {
            val snapshot = client.snapshot(userId)
            val seed = snapshot.likedSongs.randomOrNull() ?: error("收藏歌曲为空")
            val playlist = snapshot.playlists.firstOrNull() ?: error("没有可用歌单")
            client.intelligenceModeSongs(seed.id, playlist.id)
        }.getOrNull().orEmpty()
        songs.firstOrNull()?.let { first ->
            PlaybackCommands.playQueue(context, songs, first.id, heartMode = true)
        }
    }

    LaunchedEffect(selectedTab) {
        tabBarMinimized = false
        scrollAccumulator = 0f
        if (selectedTab != AppTab.Library) libraryModalVisible = false
        if (MeloXSettingsRuntime.rememberLastTab) {
            MeloXSettingsPreferences.setString(context, "general_last_tab", selectedTab.name)
        }
    }

    val visibleRootTabs = (if (selectedSource == MusicSource.Bilibili) {
        listOf(AppTab.Library, AppTab.Settings)
    } else MeloXSettingsRuntime.tabOrder.mapNotNull { runCatching { AppTab.valueOf(it) }.getOrNull() })
        .filter {
            when (it) {
                AppTab.Home -> MeloXSettingsRuntime.homeTabEnabled
                AppTab.Explore -> MeloXSettingsRuntime.exploreTabEnabled
                AppTab.Library -> MeloXSettingsRuntime.libraryTabEnabled
                AppTab.Podcasts -> MeloXSettingsRuntime.podcastsEnabled && MeloXSettingsRuntime.podcastsTabPlacement
                AppTab.Downloads -> MeloXSettingsRuntime.downloadsEnabled && MeloXSettingsRuntime.downloadsTabPlacement
                AppTab.Cloud -> MeloXSettingsRuntime.cloudMusicEnabled && MeloXSettingsRuntime.cloudTabPlacement
                AppTab.Settings -> true
                AppTab.Services -> false
                AppTab.Search -> false
            }
        }.let { if (AppTab.Settings in it) it else it + AppTab.Settings }
    LaunchedEffect(visibleRootTabs, selectedTab) {
        if (selectedTab !in visibleRootTabs && selectedTab != AppTab.Search && selectedTab != AppTab.Services) {
            selectedTab = visibleRootTabs.first()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
      // This is intentionally a small, stable source layer. It gives every
      // page control the same optical backdrop without recording the control
      // into the layer it samples.
      Box(
          modifier = Modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .layerBackdrop(pageBackdrop),
      )
      CompositionLocalProvider(LocalMeloXBackdrop provides pageBackdrop) {
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            val sharedScope = this
            val fullPlayerVisible = playbackState.hasMedia &&
                (playerTransitionState.currentState || playerTransitionState.targetState)
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(tabBarMinimizeConnection)
                    .layerBackdrop(bottomChromeBackdrop)
                    .zIndex(if (libraryModalVisible && selectedTab == AppTab.Library && !fullPlayerVisible) 15f else 0f),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.background,
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            when {
                                initialState in visibleRootTabs && targetState in visibleRootTabs ->
                                    fadeIn(tween(220)) togetherWith fadeOut(tween(160))

                                initialState == AppTab.Services && targetState == AppTab.Settings ->
                                    slideInHorizontally(
                                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                                        initialOffsetX = { -it / 4 },
                                    ) togetherWith slideOutHorizontally(
                                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                                        targetOffsetX = { it },
                                    )

                                else -> slideInHorizontally(
                                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                                    initialOffsetX = { it },
                                ) togetherWith slideOutHorizontally(
                                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                                    targetOffsetX = { -it / 4 },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "melox-page-transition",
                    ) { tab ->
                    rootPageState.SaveableStateProvider(tab.name) { when (tab) {
                        AppTab.Search -> if (selectedSource == MusicSource.Netease) SearchScreen() else ProviderSearchScreen(selectedSource)
                        AppTab.Home -> MeloXHomeScreen(
                            source = selectedSource,
                            onOpenTool = { route ->
                                when (route) {
                                    "Podcasts" -> selectedTab = AppTab.Podcasts
                                    "Downloads" -> selectedTab = AppTab.Downloads
                                    "Cloud" -> selectedTab = AppTab.Cloud
                                    else -> {
                                        settingsRouteRequest = route
                                        selectedTab = AppTab.Settings
                                    }
                                }
                            },
                        )
                        AppTab.Explore -> if (selectedSource == MusicSource.Netease) MeloXExploreScreen() else ProviderExploreScreen(selectedSource)
                        AppTab.Library -> if (selectedSource == MusicSource.Netease) {
                            LibraryScreen(
                                session = neteaseSession,
                                playlistBackEnabled = !fullPlayerVisible && !libraryModalVisible,
                                onModalVisibilityChanged = { libraryModalVisible = it },
                                onLogin = {
                                    loginReturnTab = AppTab.Library
                                    showNeteaseLogin = true
                                },
                            )
                        } else {
                            ProviderLibraryScreen(selectedSource)
                        }
                        AppTab.Podcasts -> MeloXPodcastScreen()
                        AppTab.Downloads -> LibraryScreen(
                            session = neteaseSession,
                            onLogin = {
                                loginReturnTab = AppTab.Downloads
                                showNeteaseLogin = true
                            },
                            forcedPageName = "Downloads",
                        )
                        AppTab.Cloud -> MeloXCloudMusicScreen()
                        AppTab.Settings -> ProviderSettingsHub(
                            currentSource = selectedSource,
                            onSourceSelected = { source ->
                                selectedSource = source
                                MusicProviderSelectionStore.setSelectedSource(context, source)
                                tabBarMinimized = false
                                libraryModalVisible = false
                                heartModeLaunchAttempted = false
                                // Provider switching changes only the backing data source.
                                // Stay on the current Settings route and preserve all MeloX settings UI/state.
                            },
                            neteaseSession = neteaseSession,
                            onNeteaseLogin = {
                                loginReturnTab = AppTab.Settings
                                showNeteaseLogin = true
                            },
                            onOpenServices = { selectedTab = AppTab.Services },
                            onOpenMessages = { messagesVisible = true },
                            initialRouteRequest = settingsRouteRequest,
                            onInitialRouteConsumed = { settingsRouteRequest = null },
                        )
                        AppTab.Services -> ProviderServicesScreen(
                            currentSource = selectedSource,
                            onSourceSelected = { source ->
                                selectedSource = source
                                MusicProviderSelectionStore.setSelectedSource(context, source)
                            },
                            neteaseSession = neteaseSession,
                            onNeteaseLogin = {
                                loginReturnTab = AppTab.Services
                                showNeteaseLogin = true
                            },
                            onBack = { selectedTab = AppTab.Settings },
                        )
                    } }
                    }
                }
            }

            if (selectedTab != AppTab.Services && !messagesVisible) CompositionLocalProvider(LocalMeloXBackdrop provides bottomChromeBackdrop) {
                MeloXBottomChrome(
                    selectedTab = selectedTab,
                    source = selectedSource,
                    onSelect = { tab ->
                        tabBarMinimized = false
                        selectedTab = tab
                    },
                    hasMedia = playbackState.hasMedia,
                    minimized = tabBarMinimized,
                    visibleRootTabs = visibleRootTabs,
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

            AnimatedVisibility(
                visible = messagesVisible,
                enter = slideInHorizontally(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    initialOffsetX = { it },
                ),
                exit = slideOutHorizontally(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    targetOffsetX = { it },
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(15f),
            ) {
                MessagesScreen(onBack = { messagesVisible = false })
            }

            playerTransition.AnimatedVisibility(
                visible = { value -> value },
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(20f),
            ) {
                val fullPlayerAnimatedVisibilityScope = this
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    MeloXIOSNowPlayingSharedHost(
                        state = playbackState,
                        onDismiss = closePlayer,
                        onNavigateSearch = { query, kind ->
                            if (selectedSource == MusicSource.Netease) {
                                MeloXSearchLaunchBus.post(query, kind)
                            }
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
                        animatedVisibilityScope = fullPlayerAnimatedVisibilityScope,
                    )
                }
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
        clipboardTarget?.let { target ->
            MeloXAppDialog(
                title = "打开剪贴板链接？",
                message = if (target is NeteaseClipboardTarget.Song) {
                    "检测到网易云歌曲，是否立即播放？"
                } else {
                    "检测到网易云歌单，是否立即打开并播放？"
                },
                onDismiss = onClipboardLinkConsumed,
                onConfirm = {
                    onClipboardLinkConsumed()
                    playerScope.launch {
                        val client = NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
                        val songs = withContext(Dispatchers.IO) {
                            runCatching {
                                when (target) {
                                    is NeteaseClipboardTarget.Song -> client.songDetailsBlocking(listOf(target.id))
                                    is NeteaseClipboardTarget.Playlist -> client.playlistDetailBlocking(target.id).songs
                                }
                            }.getOrDefault(emptyList())
                        }
                        songs.firstOrNull()?.let { PlaybackCommands.playQueue(context, songs, it.id) }
                    }
                },
            )
        }
        if (onboardingPage >= 0) {
            if (onboardingPage == 0) {
                MeloXFirstLaunchLegalConsent(
                    onAgree = {
                        MeloXSettingsPreferences.setString(context, "legal_consent_version", MELOX_LEGAL_VERSION)
                        MeloXSettingsPreferences.setLong(context, "legal_consent_at", System.currentTimeMillis())
                        onboardingPage = 1
                    },
                    onDecline = { (hostContext as? Activity)?.finish() },
                    onOpenProject = {
                        runCatching {
                            hostContext.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lladlam/MeloX-Android")),
                            )
                        }
                    },
                )
            } else {
                MeloXAppDialog(
                    title = "连接音乐服务",
                    message = "默认使用网易云音乐；你可以稍后在设置中切换 QQ音乐或酷狗音乐。各平台登录态只保存在本机。",
                    dismissLabel = "稍后再说",
                    confirmLabel = "登录网易云音乐",
                    onDismiss = {
                        MeloXSettingsPreferences.setBoolean(context, "onboarding_completed", true)
                        onboardingPage = -1
                    },
                    onConfirm = {
                        MeloXSettingsPreferences.setBoolean(context, "onboarding_completed", true)
                        onboardingPage = -1
                        loginReturnTab = AppTab.Settings
                        showNeteaseLogin = true
                    },
                )
            }
        }
        if (onboardingPage < 0 && cloudControlChoicePending) {
            MeloXCloudControlConsentDialog(
                onReject = {
                    MeloXRemoteConfigConsent.reject(context)
                    playerScope.launch { MeloXRemoteConfigRuntime.clearCache(context) }
                    cloudControlChoicePending = false
                },
                onAccept = {
                    MeloXRemoteConfigConsent.accept(context)
                    MeloXRemoteConfigRuntime.initializeAndRefresh(context, BuildConfig.VERSION_CODE, force = true)
                    cloudControlChoicePending = false
                },
            )
        }
        availableUpdate?.takeIf {
            onboardingPage < 0 && !cloudControlChoicePending && pendingRemoteNotice == null
        }?.let { release ->
            MeloXAppDialog(
                title = "发现 MeloX ${release.version}",
                message = release.name + release.notes.takeIf(String::isNotBlank)?.let { "\n\n${it.take(500)}" }.orEmpty(),
                dismissLabel = "稍后",
                confirmLabel = if (release.apkUrl != null) "下载 APK" else "查看发布",
                onDismiss = { availableUpdate = null },
                onConfirm = {
                    availableUpdate = null
                    playerScope.launch {
                        val target = runCatching { MeloXUpdateClient(context).downloadUrl(release) }.getOrNull()
                            ?: release.pageUrl
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                    }
                },
            )
        }
        pendingRemoteNotice?.takeIf {
            onboardingPage < 0 && !cloudControlChoicePending
        }?.let { notice ->
            LaunchedEffect(notice.id, notice.frequency) {
                MeloXRemoteNoticeStore.markShown(context, notice)
            }
            MeloXRemoteNoticeDialog(
                notice = notice,
                onAcknowledge = { pendingRemoteNotice = null },
            )
        }
        if (BuildConfig.DEBUG && MeloXSettingsRuntime.performanceOverlayEnabled) {
            MeloXPerformanceOverlay()
        }
      }
    }
}

@Composable
private fun MeloXAppDialog(
    title: String,
    message: String,
    dismissLabel: String = "取消",
    confirmLabel: String = "确定",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MeloXGlassDialog(visible = true, onDismiss = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            message,
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MeloXGlassButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                style = MeloXGlassButtonStyle.Plain,
            ) { Text(dismissLabel) }
            MeloXGlassButton(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                style = MeloXGlassButtonStyle.BorderedProminent,
            ) { Text(confirmLabel) }
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
    source: MusicSource,
    onSelect: (AppTab) -> Unit,
    hasMedia: Boolean,
    minimized: Boolean,
    visibleRootTabs: List<AppTab>,
    modifier: Modifier = Modifier,
    miniPlayer: @Composable (compactProgress: Float) -> Unit,
) {
    val tabsBackdrop = rememberLayerBackdrop()
    val dockScope = rememberCoroutineScope()
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

    val navHeight = lerpDp(64.dp, 48.dp, sizeStage)
    val searchSize = lerpDp(64.dp, 48.dp, sizeStage)
    val expandedChromeHeight = if (hasMedia) 124.dp else 64.dp
    val chromeHeight = lerpDp(expandedChromeHeight, 56.dp, dropStage)
    val labelAlpha = 1f - labelStage
    val expandedLayerAlpha = 1f - smoothStep(progress, 0.43f, 0.72f)
    val compactLayerAlpha = smoothStep(progress, 0.52f, 0.82f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            // Mei keeps the 64dp navigation capsule 8dp above the gesture
            // inset, giving the dock the same breathing room as iOS.
            .padding(bottom = 8.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(chromeHeight),
        ) {
            val horizontalMargin = 12.dp
            val compactSize = 48.dp
            val expandedGap = 8.dp
            val compactGap = 8.dp
            val expandedNavWidth = maxWidth - horizontalMargin * 2 - expandedGap - 64.dp
            val navWidth = lerpDp(expandedNavWidth, compactSize, shrinkStage)
            val navShape = Capsule()
            val primaryTabs = visibleRootTabs.map { it to it.rootGlyph() }

            val desiredCompactMiniVisibleWidth =
                (maxWidth - horizontalMargin * 2 - compactSize * 2 - compactGap * 2)
                    .coerceAtLeast(80.dp)
            val compactMiniWrapperWidth = desiredCompactMiniVisibleWidth
            val compactMiniWrapperX = horizontalMargin + compactSize + compactGap
            val miniWrapperWidth = lerpDp(maxWidth - horizontalMargin * 2, compactMiniWrapperWidth, shrinkStage)
            val miniWrapperX = lerpDp(horizontalMargin, compactMiniWrapperX, shrinkStage)
            val miniLift = lerpDp(66.dp, 0.dp, shrinkStage)

            if (hasMedia) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(
                            x = miniWrapperX,
                            y = -miniLift,
                        )
                        .width(miniWrapperWidth),
                ) {
                    miniPlayer(progress)
                }
            }

            val dark = isMeloXDarkTheme()
            // Mei uses the app's red accent for the selected tab. Blue makes
            // this chrome read as Material even when the glass effect is on.
            val selectionTint = if (dark) {
                MeloXSystemColors.Red.copy(alpha = 0.30f)
            } else {
                MeloXSystemColors.Red.copy(alpha = 0.16f)
            }
            val selectionBorder = if (dark) {
                Color.White.copy(alpha = 0.42f)
            } else {
                MeloXSystemColors.Red.copy(alpha = 0.34f)
            }

            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = horizontalMargin, y = 0.dp)
                    .width(navWidth)
                    .height(navHeight),
            ) {
                val tabBarMaxWidthPx = constraints.maxWidth
                val density = LocalDensity.current
                val selectionEdgeInset = 5.dp
                val selectionEdgeInsetPx = with(density) { selectionEdgeInset.toPx() }
                val selectionTravelWidthPx = (tabBarMaxWidthPx - selectionEdgeInsetPx * 2f).coerceAtLeast(1f)
                val tabCount = primaryTabs.size.coerceAtLeast(1)
                val selectionSegmentPx = selectionTravelWidthPx / tabCount.toFloat()
                val selectionWidth = (maxWidth - selectionEdgeInset * 2f) / tabCount.toFloat()
                val selectedIndex = primaryTabs.indexOfFirst { it.first == selectedTab }
                val dampedDock = remember(tabCount) {
                    PublicDampedDragAnimation(
                        animationScope = dockScope,
                        initialValue = selectedIndex.coerceAtLeast(0).toFloat(),
                        valueRange = 0f..(tabCount - 1).toFloat(),
                        visibilityThreshold = 0.001f,
                        initialScale = 1f,
                        pressedScale = 78f / 56f,
                        onTap = { position ->
                            if (progress < 0.56f) {
                                val index = ((position.x - selectionEdgeInsetPx) / selectionSegmentPx)
                                    .toInt()
                                    .coerceIn(0, tabCount - 1)
                                onSelect(primaryTabs[index].first)
                            } else if (progress >= 0.68f) {
                                onSelect(selectedTab)
                            }
                        },
                        onDragStopped = {
                            val target = targetValue.roundToInt().coerceIn(0, tabCount - 1)
                            animateToValue(target.toFloat())
                            onSelect(primaryTabs[target].first)
                        },
                        onDrag = { _, dragAmount ->
                            updateValue(
                                targetValue + dragAmount.x / selectionSegmentPx,
                            )
                        },
                    )
                }
                LaunchedEffect(selectedIndex, progress) {
                    if (progress < 0.56f && selectedIndex >= 0) {
                        dampedDock.animateToValue(selectedIndex.toFloat())
                    }
                }
                val dockHighlight = remember(dockScope, dampedDock, selectionSegmentPx) {
                    com.lladlam.melox.ui.glass.publicdemo.PublicInteractiveHighlight(
                        animationScope = dockScope,
                        position = { size, _ ->
                            Offset(
                                selectionEdgeInsetPx + (dampedDock.value + 0.5f) * selectionSegmentPx,
                                size.height / 2f,
                            )
                        },
                    )
                }
                // Keep the backdrop panel and the moving optical element as
                // siblings. The selection must not be a child of the panel's
                // backdrop layer, otherwise the panel bounds clip its lift.
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(dockHighlight.gestureModifier)
                        .then(dampedDock.modifier),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .meloXLiquidBottomBar(
                                shape = navShape,
                                tint = bottomLiquidGlassTint(),
                                surfaceColor = bottomGlassFallbackColor().copy(alpha = 0.18f),
                                pressProgress = dampedDock.pressProgress,
                            )
                            .then(dockHighlight.modifier),
                    ) {
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
                                title = tab.titleFor(source),
                                glyph = glyph,
                                selected = selectedTab == tab,
                                labelAlpha = labelAlpha,
                                dark = dark,
                                onClick = { onSelect(tab) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = expandedLayerAlpha }
                            .padding(horizontal = 5.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        primaryTabs.forEach { (tab, glyph) ->
                            RootTabButton(
                                title = tab.titleFor(source),
                                glyph = glyph,
                                selected = selectedTab == tab,
                                labelAlpha = labelAlpha,
                                dark = dark,
                                onClick = { onSelect(tab) },
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
                                    MeloXSystemColors.Red
                                },
                                selected = true,
                            )
                        }
                    }
                    }

                    val lensAlpha by animateFloatAsState(
                        targetValue = if (selectedIndex >= 0 && progress < 0.56f) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.86f, stiffness = 440f),
                        label = "melox-tab-selection-alpha",
                    )
                    val lensVisibility = lensAlpha * expandedLayerAlpha
                    Box(
                        modifier = Modifier
                            // This is a sibling overlay, not content inside
                            // the panel backdrop. It can lift and stretch
                            // beyond one tab without being clipped by Dock.
                            .width(selectionWidth)
                            .fillMaxHeight()
                            .offset {
                                IntOffset(
                                    x = (selectionEdgeInsetPx + dampedDock.value * selectionSegmentPx).roundToInt(),
                                    y = 0,
                                )
                            }
                            .padding(4.dp)
                            .meloXLiquidTabSelection(
                                shape = Capsule(),
                                selected = lensVisibility > 0.001f,
                                panelBackdrop = tabsBackdrop,
                                pressProgress = dampedDock.pressProgress,
                                scaleX = dampedDock.scaleX,
                                scaleY = dampedDock.scaleY,
                                velocity = dampedDock.velocity,
                                tint = selectionTint.copy(
                                    alpha = selectionTint.alpha * lensVisibility,
                                ),
                            )
                    )
                }
            }

            val searchContentDescription = stringResource(R.string.tab_search)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = -horizontalMargin, y = 0.dp)
                    .size(searchSize)
                    .meloXLiquidButton(
                        shape = Capsule(),
                        tint = bottomLiquidGlassTint(),
                        blurRadius = 6.dp,
                        lensRadius = 12.dp,
                        refractionHeight = 18.dp,
                        surfaceColor = bottomGlassFallbackColor().copy(alpha = 0.16f),
                    )
                    .clickable(role = Role.Button) { onSelect(AppTab.Search) }
                    .semantics {
                        contentDescription = searchContentDescription
                    },
                contentAlignment = Alignment.Center,
            ) {
                RootGlyphIcon(
                    glyph = RootGlyph.Search,
                    modifier = Modifier.size(lerpDp(28.dp, 27.dp, sizeStage)),
                    color = if (selectedTab == AppTab.Search) {
                        MeloXSystemColors.Red
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    selected = selectedTab == AppTab.Search,
                )
            }
        }
    }
}

@Composable
private fun bottomLiquidGlassTint(): Color =
    if (isMeloXDarkTheme()) {
        Color.Black.copy(alpha = 0.10f)
    } else {
        Color.White.copy(alpha = 0.12f)
    }

@Composable
private fun bottomGlassFallbackColor(): Color =
    if (isMeloXDarkTheme()) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
    } else {
        Color.White.copy(alpha = 0.56f)
    }

@Composable
private fun RowScope.RootTabButton(
    title: String,
    glyph: RootGlyph,
    selected: Boolean,
    labelAlpha: Float,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val foreground by animateColorAsState(
        targetValue = if (selected) {
            MeloXSystemColors.Red
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
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics {
                contentDescription = title
                role = Role.Tab
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RootGlyphIcon(
            glyph = glyph,
            modifier = Modifier.size(24.dp),
            color = foreground,
            selected = selected,
        )
        Text(
            text = title,
            modifier = Modifier.graphicsLayer { alpha = labelAlpha },
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = foreground,
        )
    }
}

private enum class RootGlyph { Home, Explore, Library, Podcasts, Downloads, Cloud, Settings, Search }

@Composable
@Suppress("UNUSED_PARAMETER")
private fun AppTab.titleFor(source: MusicSource): String = stringResource(titleRes)

private fun AppTab.rootGlyph(): RootGlyph = when (this) {
    AppTab.Home -> RootGlyph.Home
    AppTab.Explore -> RootGlyph.Explore
    AppTab.Library -> RootGlyph.Library
    AppTab.Podcasts -> RootGlyph.Podcasts
    AppTab.Downloads -> RootGlyph.Downloads
    AppTab.Cloud -> RootGlyph.Cloud
    AppTab.Settings -> RootGlyph.Settings
    AppTab.Services -> RootGlyph.Settings
    AppTab.Search -> RootGlyph.Search
}

@Composable
private fun RootGlyphIcon(
    glyph: RootGlyph,
    modifier: Modifier,
    color: Color,
    selected: Boolean = false,
) {
    MeloXSymbolIcon(
        symbol = when (glyph) {
            RootGlyph.Home -> MeloXSymbol.Home
            RootGlyph.Explore -> MeloXSymbol.Explore
            RootGlyph.Library -> MeloXSymbol.Library
            RootGlyph.Podcasts -> MeloXSymbol.RadioWaves
            RootGlyph.Downloads -> MeloXSymbol.Download
            RootGlyph.Cloud -> MeloXSymbol.Storage
            RootGlyph.Settings -> MeloXSymbol.Settings
            RootGlyph.Search -> MeloXSymbol.Search
        },
        modifier = modifier,
        color = color,
        variant = if (selected) MeloXSymbolVariant.Fill else MeloXSymbolVariant.Regular,
    )
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
