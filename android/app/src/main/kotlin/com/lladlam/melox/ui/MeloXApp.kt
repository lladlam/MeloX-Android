package com.lladlam.melox.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
// ── 底部 chrome 明暗切换（2026-09-25）─────────────────────────────────
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import com.lladlam.melox.ui.glass.BottomBarToneTheme
import com.lladlam.melox.ui.glass.LocalBottomBarTone
import com.lladlam.melox.ui.glass.bottomBarTone
import com.lladlam.melox.ui.glass.darkness
import com.lladlam.melox.ui.glass.rememberBottomBarToneState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.zIndex
import com.lladlam.melox.core.account.rememberNeteaseSessionStore
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.BuildConfig
import com.lladlam.melox.R
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.provider.MusicProviderSelectionStore
import com.lladlam.melox.ui.account.NeteaseLoginScreen
import com.lladlam.melox.ui.account.MeloXAccountActivity
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
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
import com.lladlam.melox.ui.animation.BottomBarCollapseSpec
import com.lladlam.melox.ui.animation.BottomBarExpandSpec
import com.lladlam.melox.ui.animation.MeloXSprings
import com.lladlam.melox.ui.animation.NavExpandLeftShare
import com.lladlam.melox.ui.animation.meloXContentEnter
import com.lladlam.melox.ui.animation.meloXContentExit
import com.lladlam.melox.ui.animation.sprungFrac
import com.lladlam.melox.ui.glass.bottomGlassSurfaceColor
import com.lladlam.melox.ui.glass.bottomLiquidGlassTint
import com.lladlam.melox.ui.glass.meloXLiquidBottomBar
import com.lladlam.melox.ui.glass.meloXLiquidCaptureLayer
import com.lladlam.melox.ui.glass.meloXLiquidTabSelection
import com.lladlam.melox.ui.glass.publicdemo.PublicDampedDragAnimation
import com.lladlam.melox.ui.glass.publicdemo.PublicInteractiveHighlight
import com.lladlam.melox.ui.player.MeloXImmersivePlaybackEffect
import com.lladlam.melox.ui.player.MeloXIOSMiniPlayer
import com.lladlam.melox.ui.player.MeloXProviderLyricsLoader
import com.lladlam.melox.ui.player.MeloXIOSNowPlayingSharedHost
import com.lladlam.melox.ui.player.meloXPlayerTransitionDurationMillis
import com.lladlam.melox.ui.player.rememberMeloXPlaybackUiState
import com.lladlam.melox.ui.provider.ProviderExploreScreen
import com.lladlam.melox.ui.provider.ProviderHomeScreen
import com.lladlam.melox.ui.provider.ProviderLibraryScreen
import com.lladlam.melox.ui.provider.ProviderSearchScreen
import com.lladlam.melox.ui.provider.ProviderSettingsHub
import com.lladlam.melox.ui.provider.MeloXProviderServicesActivity
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AppTab(@StringRes val titleRes: Int) {
    Home(R.string.tab_home),
    Explore(R.string.tab_explore),
    Library(R.string.tab_library),
    Podcasts(R.string.tab_podcasts),
    Downloads(R.string.tab_downloads),
    Cloud(R.string.tab_cloud),
    Settings(R.string.tab_settings),
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
    var tabBarMinimized by rememberSaveable { mutableStateOf(false) }
    var scrollAccumulator by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(tabBarMinimized) {
        if (com.lladlam.melox.ui.settings.MeloXSettingsRuntime.hapticFeedbackEnabled)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(MeloXSettingsRuntime.disableAutomaticTabBarShrink) {
        if (MeloXSettingsRuntime.disableAutomaticTabBarShrink) tabBarMinimized = false
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
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val persistedSource = MusicProviderSelectionStore.selectedSource(context)
        if (persistedSource != selectedSource) {
            selectedSource = persistedSource
            tabBarMinimized = false
            libraryModalVisible = false
            heartModeLaunchAttempted = false
        }
    }
    val playbackState = rememberMeloXPlaybackUiState(connectionEnabled = playbackConnectionEnabled)
    val playerTransitionState = remember { SeekableTransitionState(false) }
    val playerTransition = rememberTransition(
        transitionState = playerTransitionState,
        label = "melox-player-transition",
    )
    var playerArtworkPageUsesScale by remember { mutableStateOf(false) }
    val playerScope = rememberCoroutineScope()
    var playerTransitionJob by remember { mutableStateOf<Job?>(null) }
    val clipboardTarget = remember(clipboardLinkRequest, selectedSource) {
        if (selectedSource == MusicSource.Netease) {
            clipboardLinkRequest?.let(NeteaseClipboardLink::parse)
        } else {
            null
        }
    }
    val openPlayer: () -> Unit = {
        if (playbackState.hasMedia) {
            playerTransitionJob?.cancel()
            playerTransitionJob = playerScope.launch {
                playerTransitionState.animateTo(
                    targetState = true,
                    animationSpec = playerAutomaticFractionSpec(),
                )
            }
        }
    }
    val closePlayer: () -> Unit = {
        playerTransitionJob?.cancel()
        playerTransitionJob = playerScope.launch {
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
                if (MeloXSettingsRuntime.disableAutomaticTabBarShrink) {
                    scrollAccumulator = 0f
                    return Offset.Zero
                }

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
                AppTab.Search -> false
            }
        }.let { if (AppTab.Settings in it) it else it + AppTab.Settings }
    LaunchedEffect(visibleRootTabs, selectedTab) {
        if (selectedTab !in visibleRootTabs && selectedTab != AppTab.Search) {
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

            MeloXImmersivePlaybackEffect(
                enabled = fullPlayerVisible && MeloXSettingsRuntime.immersivePlaybackEnabled,
            )

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
                    val searchTabBackClear = remember { mutableStateOf(com.lladlam.melox.ui.search.SearchBackAction.SwitchToHome) }
                    var searchTabAction by remember { mutableStateOf(com.lladlam.melox.ui.search.SearchBackAction.SwitchToHome) }
                    val isHomeRoot = selectedTab == AppTab.Home &&
                        !messagesVisible &&
                        !fullPlayerVisible &&
                        onboardingPage < 0 &&
                        !showNeteaseLogin &&
                        cloudControlChoicePending.not() &&
                        availableUpdate == null &&
                        clipboardTarget == null
                    val exitConfirmThresholdMs = 2_000L
                    var pendingExitAtMs by remember { mutableStateOf(0L) }
                    BackHandler {
                        if (selectedTab != AppTab.Home) {
                            if (selectedTab == AppTab.Search) {
                                if (searchTabAction != com.lladlam.melox.ui.search.SearchBackAction.SwitchToHome) {
                                    searchTabBackClear.value = searchTabAction
                                    return@BackHandler
                                }
                            }
                            // Any non-home root page returns to the default launch
                            // page first. System back from the search page or any
                            // other tab no longer exits the app directly.
                            tabBarMinimized = false
                            selectedTab = AppTab.Home
                            return@BackHandler
                        }
                        if (!isHomeRoot) {
                            // An overlay such as the player, messages or login is
                            // visible. Dismiss it instead of exiting.
                            when {
                                messagesVisible -> messagesVisible = false
                                showNeteaseLogin -> showNeteaseLogin = false
                                cloudControlChoicePending -> cloudControlChoicePending = false
                                availableUpdate != null -> availableUpdate = null
                                clipboardTarget != null -> onClipboardLinkConsumed()
                                else -> { /* no-op, let the system handle it */ }
                            }
                            return@BackHandler
                        }
                        val now = System.currentTimeMillis()
                        if (now - pendingExitAtMs < exitConfirmThresholdMs) {
                            (hostContext as? Activity)?.finish()
                        } else {
                            pendingExitAtMs = now
                            Toast.makeText(hostContext, "再按一次退出 MeloX", Toast.LENGTH_SHORT).show()
                        }
                    }
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { meloXContentEnter() togetherWith meloXContentExit() },
                        modifier = Modifier.fillMaxSize(),
                        label = "melox-page-transition",
                    ) { tab ->
                    rootPageState.SaveableStateProvider(tab.name) { when (tab) {
                         AppTab.Search -> if (selectedSource == MusicSource.Netease) SearchScreen(
                             backClearSignal = searchTabBackClear,
                             onSearchBackState = { searchTabAction = it },
                             onSearchExit = {
                                 tabBarMinimized = false
                                 selectedTab = AppTab.Home
                             },
                         ) else ProviderSearchScreen(selectedSource)
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
                            onOpenAccount = {
                                neteaseSession.profile?.userId?.let { userId ->
                                    MeloXAccountActivity.launch(hostContext, userId)
                                } ?: run {
                                    loginReturnTab = AppTab.Settings
                                    showNeteaseLogin = true
                                }
                            },
                            onOpenServices = { MeloXProviderServicesActivity.launch(hostContext) },
                            onOpenMessages = { messagesVisible = true },
                            initialRouteRequest = settingsRouteRequest,
                            onInitialRouteConsumed = { settingsRouteRequest = null },
                        )
                    } }
                    }
                }
            }

            // ══ 底部 chrome 明暗切换状态（2026-09-25）════════════════════════════
            //   `isMeloXDarkTheme()` **只能在这里读一次**，作为打底值；之后全部走 `bottomBarTone`。
            val systemDark = isMeloXDarkTheme()
            val toneState = rememberBottomBarToneState(systemDark)
            DisposableEffect(toneState) {
                onDispose { toneState.release() }
            }
            val bottomBarTone = toneState.darkness(systemDark)

            if (!messagesVisible) CompositionLocalProvider(
                LocalMeloXBackdrop provides bottomChromeBackdrop,
                // ── 底部 chrome 明暗切换（2026-09-25）─────────────────────────────
                //   Apple 口径：navbars / tabbars 属于「会按背后内容在明暗之间整体翻转」
                //   那一档（WWDC25 Session 219 @15:16）。用户拍板：**系统主题打底 +
                //   同时真实采样底栏背后像素** 覆盖。
                //   ⚠ 必须**整个底部 chrome 共用同一个 d**（nav / 搜索键 / 播放栏 /
                //   水珠 / 前景字形全部在内），否则会出现「底栏亮、搜索键暗」的碎裂感。
                LocalBottomBarTone provides bottomBarTone,
            ) {
                // ══ 明暗两种模式的底栏互相切换（方案 A，2026-09-25 用户选定）══════════
                //   整棵子树改用「LightColors ↔ DarkColors 插值出来的配色」重builder一个
                //   MaterialTheme。 ⇒ 所有读 `MaterialTheme.colorScheme.*` 的组件自动跟着过渡，
                //   **不需要**在底栏里手写任何颜色端点。
                //   ⚠ `isMeloXDarkTheme()` 也是读 `colorScheme.background.luminance()`，
                //     所以 subtree 里的布尔判定同样会平滑地跟着翻。
                BottomBarToneTheme(darkness = bottomBarTone) {
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
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // 采样的目标矩形 = 底部 chrome 在窗口里的实际位置。
                        .onGloballyPositioned { toneState.updateBounds(it) },
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
                                 applyPlayerArtworkScale = playerArtworkPageUsesScale,
                             )
                        }
                    },
                )
                }
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
                        onOpenPlaybackSettings = {
                            settingsRouteRequest = "Playback"
                            selectedTab = AppTab.Settings
                            closePlayer()
                        },
                        onLocalMetadataChanged = { playbackState.refreshCurrentLocalMetadata() },
                        onSeekCollapse = { fraction ->
                            playerTransitionJob?.cancelAndJoin()
                            playerTransitionJob = null
                            playerTransitionState.seekTo(
                                fraction = fraction.coerceIn(0f, 0.999f),
                                targetState = false,
                            )
                        },
                        onSettleCollapse = { collapse ->
                            playerTransitionJob?.cancelAndJoin()
                            playerTransitionJob = null
                            playerTransitionState.animateTo(
                                targetState = !collapse,
                                animationSpec = playerGestureSettleSpec(),
                            )
                        },
                         sharedTransitionScope = sharedScope,
                         animatedVisibilityScope = fullPlayerAnimatedVisibilityScope,
                         onArtworkPageChanged = { playerArtworkPageUsesScale = it },
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

/**
 * 展开态下底栏 nav 胶囊的高度与搜索键边长 —— 同一个设计基准（360dp 屏）。
 * 收缩态对应 [ChromeCompactSize]。两处必须同源，否则展开态会出现
 * 「胶囊比邻居高一截」的错位。
 */
private val ChromeExpandedSize = 57.dp

/** 收缩态下底栏 nav 胶囊的高度与搜索键边长。 */
private val ChromeCompactSize = 46.dp

/**
 * ⚠ **2026-09-25 一笔勾销**：本常量（及其自研「实心着色胶囊」Layer 2 方案）已随
 *   一比一复刻 BiliNext `LiquidGlassTabsBar.kt` 全部删除 —— Layer 2 现在是一块
 *   **真玻璃**（`meloXLiquidCaptureLayer`），不再是本项目自创的着色胶囊。
 *   保留此墓碑仅为阻止后来者重新发明它。
 */

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
        // 展开 / 收缩各用**一条完整弹簧**（方向不同、手感不同）：
        //   · 展开 bounce 0.245 → ζ=0.755，过冲 2.68% → 外扩 6.02dp（左 2.01 / 右 4.01dp，
        //     与搜索键的 7dp 缝留 3.00dp 视觉余量）—— 空间受限，所以收着弹、且**偏右**落；
        //   · 收缩 bounce 0.300 → ζ=0.700，过冲 4.60% → 10.30dp —— 四周空出来了，放开弹。
        // ⚠ 别让两个方向共用 spec：共用时保护邻居只剩「对展开方向事后压缩」一条路，
        //   而压缩后的曲线不再是弹簧（阻尼包络仍是 0.30 的），手感与收缩方向不对等。
        //   target 与 spec 在同一帧一起算 —— 方向就是 targetValue 本身。
        animationSpec = if (minimized) BottomBarCollapseSpec else BottomBarExpandSpec,
        label = "melox-tab-minimize-progress",
    )
    // 语义值（alpha / 图层门控 / 选中态判定）必须夹在 [0,1]，否则负 alpha 会炸。
    val progress = rawProgress.coerceIn(0f, 1f)

    // 几何值 = 弹簧本身（带符号限幅）。两侧对称、不过任何窗口 —— 详见 sprungFrac 注释：
    // 旧的窗口 [0.20,1.00] 在展开方向把几何钉死在弹簧峰值速度处，随后空转 246ms。
    val bounceFrac = sprungFrac(rawProgress)

    val labelStage = smoothStep(progress, 0.00f, 0.32f)   // alpha 用，保持夹住
    val dropStage = smoothStep(progress, 0.78f, 1.00f)    // 容器高度，不参与过冲

    val navHeight = lerpDpBouncy(ChromeExpandedSize, ChromeCompactSize, bounceFrac)
    // ⚠ f 的**负方向**才是「展开外扩」（f=0 是展开稳态、f=1 是收缩稳态）。
    // 这里只夹下界：搜索键右对齐，若跟着外扩会吃掉与 nav 之间的固有间隙，
    // 而它自身的外扩量只有 0.9dp、肉眼不可见 —— 整段间隙留给 nav 的过冲更划算。
    val searchSize = lerpDpBouncy(ChromeExpandedSize, ChromeCompactSize, bounceFrac.coerceAtLeast(0f))
    val mediaReveal by animateFloatAsState(
        targetValue = if (hasMedia) 1f else 0f,
        // 下方是 miniLift × mediaReveal 的纯乘子，不经过 lerpDp 的夹取，
        // 所以过冲天然可见 —— 播放栏出现时会先"弹过"再落位。
        animationSpec = spring(
            dampingRatio = MeloXSprings.dampingRatio(MeloXSprings.MiniPlayerRevealBounce),
            stiffness = MeloXSprings.MiniPlayerRevealStiffness,
        ),
        label = "melox-mini-player-reveal",
    )
    val expandedChromeHeight by animateDpAsState(
        targetValue = if (hasMedia) 124.dp else 64.dp,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 360f),
        label = "melox-chrome-height",
    )
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
            // nav 胶囊右缘 ↔ 搜索键左缘的固有间隙（展开态）。
            // 官方截图实测 = **10px = 7.12dp**（rim 峰法：nav 右缘 472 → 搜索键左缘 483，
            // 阈值 140~205 全区间稳定），取 7dp。旧值 12dp 是「先加大缝好让过冲有地方去」的临时口径。
            // ⚠ 这个缝同时是展开方向弹簧的约束：它定出右缘可用外扩 = 7 − 3（视觉余量）= 4dp，
            //   再按 NavExpandLeftShare(左1:右2) 反推出总外扩 6dp 与 BottomBarExpandBounce 0.245。
            //   改这个值必须回头重算那个常数。
            val searchGap = 7.dp
            val compactGap = 8.dp
            val expandedNavWidth =
                maxWidth - horizontalMargin * 2 - searchGap - ChromeExpandedSize
            // 宽度就是弹簧本身：lerpDpBouncy 不夹取，展开方向 f<0 会让胶囊长到
            // 272 + 6.02 = 278.02dp —— **不需要按预算压缩、也不需要限幅**，因为展开那条
            // 弹簧（BottomBarExpandBounce）本来就是照着这点余地定出来的。
            val navWidth = lerpDpBouncy(expandedNavWidth, compactSize, bounceFrac)
            // 外扩 = 胶囊变大，多出来的宽度往哪边落由 NavExpandLeftShare 决定：
            // 现取 **左 1 : 右 2（偏右）** —— 右侧固有缝只有 7dp，那点余量必须花在
            // 「朝搜索键弹过去」这个方向上；左侧是 12dp 屏幕边距、没有邻居，让出 2dp 很安全。
            // 代价：nav 中心在峰值轻微右漂 1dp（148 → 149），这是「右边弹得更多」的必然结果，
            // 不是 bug —— 想消除就只能回到 1/2 等分，而那正是被否掉的「对称」。
            // 收缩方向 f>0 宽度不超稳态，coerceAtLeast(0.dp) 让越界量归零 → 退回纯边距，
            // 也就是收缩天然是**左锚定**（左缘恒 12dp、右缘 284 → 60dp）。
            // 旧的「先喂右边、余量给左边」+「等比压缩」两层都被这一行替掉了。
            val navOffsetX = horizontalMargin -
                (navWidth - expandedNavWidth).coerceAtLeast(0.dp) * NavExpandLeftShare
            val navShape = Capsule()
            val primaryTabs = visibleRootTabs.map { it to it.rootGlyph() }

            val desiredCompactMiniVisibleWidth =
                (maxWidth - horizontalMargin * 2 - compactSize * 2 - compactGap * 2)
                    .coerceAtLeast(80.dp)
            val compactMiniWrapperWidth = desiredCompactMiniVisibleWidth
            val compactMiniWrapperX = horizontalMargin + compactSize + compactGap
            val miniWrapperWidth = lerpDpBouncy(maxWidth - horizontalMargin * 2, compactMiniWrapperWidth, bounceFrac)
            val miniWrapperX = lerpDpBouncy(horizontalMargin, compactMiniWrapperX, bounceFrac)
            val miniLift = lerpDpBouncy(66.dp, 0.dp, bounceFrac) * mediaReveal
            // 收缩态下播放栏需与两侧底栏按钮/搜索键共中心线。
            // 实测播放栏整体比按钮高 1dp（top/bottom 各差 4px），
            // 这里随收缩进度补一个 1dp 下移，把两者垂直中心压平。
            val miniCenterAlignNudge = lerpDpBouncy(0.dp, 1.dp, bounceFrac) * mediaReveal

            if (hasMedia) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(
                            x = miniWrapperX,
                            y = -miniLift + miniCenterAlignNudge,
                        )
                        .width(miniWrapperWidth),
                ) {
                    miniPlayer(progress)
                }
            }

            // ════════════════════════════════════════════════════════════════════════
            //  一比一复刻 BiliNext `LiquidGlassTabsBar.kt`（2026-09-25 用户指令）
            //
            //  权威源：D:/bilinext/app/src/main/java/com/android/bilinext/ui/
            //          components/LiquidGlassTabsBar.kt   （Apache-2.0 → GPL-3.0 允许）
            //
            //  BiliNext 三层结构（原值，**不再自创**）：
            //    L1 可见面板   : height = maxHeight(= navHeight)
            //                    + padding(horizontal = 10dp)
            //                    + vibrancy + blur(8dp) + lens(24dp·aspectScale, 24dp)
            //                    + layerBlock { scaleX = 1; scaleY = 1 }   ← 不随按压缩放
            //                    + Highlight(0.55·2·rim) / InnerShadow / Shadow
            //                    + onDrawSurface = drawRect(containerColor)
            //    L2 隐形捕获层 : height = DropletHeight = **navHeight − 8dp**
            //                    + padding(horizontal = 10dp)
            //                    + 同一套 vibrancy/blur/lens 但 **H、A 都 ×p**
            //                    + highlight ×p；**无 shadow / innerShadow**
            //                    + onDrawSurface = drawRect(containerColor)
            //                    + 内含**同一份 tab 内容**（图标用 accent 色染）
            //    L3 水珠       : height = DropletHeight（**与 L2 等高**）
            //                    width  = tabWidth × 1.15 + 2dp
            //                    + lens(8.4dp·p, 11.8dp·p, chromatic)   // 10/14 × 47/56
            //                    + Highlight/Shadow/InnerShadow(8dp·p) 全部 ×p
            //                    + layerBlock { scaleX = damped.scaleX; … 速度修正 }
            //                      ← **挂 dampedDragAnimation.modifier ⇒ 缩放真的生效**
            //                    + onDrawSurface = 0.22 黑/白 ×(1−p) + 0.03 黑 ×p
            //    ⇒ 源码 L101-102 的恒等式：
            //       「水珠 = Layer 2 高度，且都小于 Layer 1，让水珠边缘恰好触及 Layer 2 边界」
            //
            //  ⚠ 本项目的两处**保留差异**（用户口径优先，不跟 BiliNext 改）：
            //    ① 指示器颜色 = 黑/白（MeloX 用户拍板「指示器是黑白，不是蓝色」），
            //       BiliNext 的 accent 蓝只染 L2 的 tab 图标。
            //    ② 选中 tab 图标用红色（MeloX 既有主题口径），非 BiliNext 的 accent 蓝。
            // ════════════════════════════════════════════════════════════════════════
            // ⚠ 这里**不能再读 `isMeloXDarkTheme()`** —— 必须接同一个 `LocalBottomBarTone`，
            //   否则在 d 处于中间态时，容器色 / 指示器色会与别层的进度脱节。
            val tone = bottomBarTone()
            val frostedGlass = MeloXSettingsRuntime.frostedGlassEnabled
            // BiliNext L131-133：containerColor = 0xFAFAFA/0x121212 @ 0.35
            val containerColor = lerp(
                Color(0xFFFAFAFA).copy(0.35f),
                Color(0xFF121212).copy(0.35f),
                tone,
            )
            // 指示器本体涂层（官方口径，黑白）
            val selectionTint = lerp(
                Color.Black.copy(0.1f),
                Color.White.copy(0.1f),
                tone,
            )

            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = navOffsetX, y = 0.dp)
                    .width(navWidth)
                    .height(navHeight),
                contentAlignment = Alignment.CenterStart,
            ) {
                val density = LocalDensity.current
                val tabCount = primaryTabs.size.coerceAtLeast(1)

                // BiliNext L152-156：tabWidth = (maxWidth − 20dp) / n（= 两侧 10dp padding）
                val tabWidth = (maxWidth - 20.dp) / tabCount
                val tabWidthPx = with(density) { tabWidth.toPx() }

                // ★ 核心恒等式：水珠与 Layer 2 **等高**，两者都比 Layer 1 矮 8dp
                //   （BiliNext：L1=55dp / L2=DropletHeight=47dp / L3=47dp）
                val dropletHeight = (navHeight - 8.dp).coerceAtLeast(1.dp)
                val dropletWidth = tabWidth * 1.15f + 2.dp

                val selectedIndex = primaryTabs.indexOfFirst { it.first == selectedTab }
                val dockExpanded = progress < 0.56f
                val currentProgress by rememberUpdatedState(progress)
                val currentSelectedTab by rememberUpdatedState(selectedTab)
                val currentPrimaryTabs by rememberUpdatedState(primaryTabs)
                val currentTabWidthPx by rememberUpdatedState(tabWidthPx)
                val currentOnSelect by rememberUpdatedState(onSelect)
                // ⚠ **必须走 rememberUpdatedState**（2026-09-25 修 bug）。
                //   `dampedDock` 是 `remember(tabCount)` —— 只有 tab 数变化才会重建，
                //   所以它内部所有 lambda 会在**首次组合**时把当时的局部值**捕获死**。
                //   `selectedIndex` 若不是 State 包装，`onDragStopped` 里那句
                //   `if (target != selectedIndex)` 就永远在跟「App 启动时的选中下标」比：
                //   启动在首页(0) ⇒ 拖动指示器回首页时 `0 != 0` 为假 ⇒ **不触发切换**
                //   （真机症状：「拖到首页 tab 没反应，只有点击才行」）。
                val currentSelectedIndex by rememberUpdatedState(selectedIndex)

                // BiliNext L158-166 面板 recoil：拖动时整条面板轻微反向偏移
                val panelOffsetAnim = remember { Animatable(0f) }
                val panelOffset by remember(density) {
                    derivedStateOf {
                        val fraction = (panelOffsetAnim.value / constraints.maxWidth)
                            .coerceIn(-1f, 1f)
                        val sign = if (fraction >= 0f) 1f else -1f
                        with(density) {
                            4.dp.toPx() * sign * EaseOut.transform(abs(fraction))
                        }
                    }
                }

                val dampedDock = remember(tabCount) {
                    PublicDampedDragAnimation(
                        animationScope = dockScope,
                        initialValue = selectedIndex.coerceAtLeast(0).toFloat(),
                        valueRange = 0f..(tabCount - 1).toFloat(),
                        visibilityThreshold = 0.001f,
                        initialScale = 1f,
                        // BiliNext L185：pressedScale = 78/56
                        pressedScale = 78f / 56f,
                        onTap = { position ->
                            // BiliNext：索引 = 落点落在哪一格（tabWidth 等宽）
                            val index = ((position.x - currentTabWidthPx / 2f) / currentTabWidthPx)
                                .roundToInt()
                                .coerceIn(0, tabCount - 1)
                            if (currentProgress < 0.56f) {
                                currentOnSelect(currentPrimaryTabs[index].first)
                            } else if (currentProgress >= 0.68f) {
                                currentOnSelect(currentSelectedTab)
                            }
                        },
                        onDragStopped = {
                            val target = targetValue.roundToInt().coerceIn(0, tabCount - 1)
                            animateToValue(target.toFloat())
                            // ⚠ 用 `currentSelectedIndex`（rememberUpdatedState）而不是捕获死的
                            //   `selectedIndex` —— 见上面 currentSelectedIndex 的长注释。
                            if (target != currentSelectedIndex) {
                                currentOnSelect(currentPrimaryTabs[target].first)
                            }
                            dockScope.launch {
                                panelOffsetAnim.animateTo(
                                    0f,
                                    spring(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = 0.5f),
                                )
                            }
                        },
                        onDrag = { _, dragAmount ->
                            updateValue(
                                (targetValue + dragAmount.x / currentTabWidthPx.coerceAtLeast(1f))
                                    .coerceIn(0f, (tabCount - 1).toFloat()),
                            )
                            dockScope.launch {
                                panelOffsetAnim.snapTo(panelOffsetAnim.value + dragAmount.x)
                            }
                        },
                    )
                }
                LaunchedEffect(selectedIndex, dockExpanded) {
                    if (dockExpanded && selectedIndex >= 0) {
                        dampedDock.animateToValue(selectedIndex.toFloat())
                    }
                }
                val dockHighlight = remember(dockScope, dampedDock) {
                    PublicInteractiveHighlight(
                        animationScope = dockScope,
                        position = { size, _ ->
                            // BiliNext：(value + 0.5) × tabWidthPx + panelOffset
                            Offset(
                                (dampedDock.value + 0.5f) * currentTabWidthPx + panelOffset,
                                size.height / 2f,
                            )
                        },
                    )
                }

                val tabsBackdrop = rememberLayerBackdrop()
                // BiliNext L245：L2 的 tab 缩放（按压时图标放大 1.2×，只作用于捕获层内容）
                val tabScale: () -> Float = { lerp(1f, 1.2f, dampedDock.pressProgress) }
                // BiliNext L130：`accentColor = if (isDarkTheme) TabAccentDark else TabAccentLight`。
                // MeloX 保留自己的口径 —— 强调色 = 主题红（与选中 tab / 收缩态圆心标一致）。
                val accentColor = MeloXSystemColors.Red

                // ══ Layer 1：可见面板（BiliNext 原样）════════════════════════════
                // ⚠ **收缩态必须把 tab 行淡出**（2026-09-25 修 bug）：
                //   `navWidth` 会从展开 272dp 弹簧到 `compactSize` 48dp，而本 Row 里是
                //   `primaryTabs.forEach { RootTabButton(weight(1f)) }` —— 4 个 tab 在
                //   48dp − 20dp(padding) = 28dp 里各分 7dp，24dp 图标互相重叠、挤出容器，
                //   真机表现为**左侧一个糊成一团的畸形图标**（截图 tmp/mx_col2_bar.png）。
                //   ⇒ 用既有的 `expandedLayerAlpha`（progress 0.43→0.72 之间淡出）把
                //     **图标那层**隐去，收缩态改由下面的 compact 层渲染单个圆心标。
                //   ⚠ 淡出必须只作用在**内容**上，不能连 `meloXLiquidBottomBar` 一起淡 ——
                //     面板玻璃在收缩全程都得在（否则收缩中胶囊会闪一下消失）。
                Row(
                    modifier = Modifier
                        .graphicsLayer { translationX = panelOffset }
                        .meloXLiquidBottomBar(
                            shape = navShape,
                            tint = bottomLiquidGlassTint(),
                            surfaceColor = bottomGlassSurfaceColor(),
                            pressProgress = 0f,
                        )
                        .then(dockHighlight.modifier)
                        .height(navHeight)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 内容层：随收缩进度淡出（不含面板材质）
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = expandedLayerAlpha }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        primaryTabs.forEach { (tab, glyph) ->
                            RootTabButton(
                                title = tab.titleFor(source),
                                glyph = glyph,
                                // BiliNext L364：可见面板的图标**永远用未激活色**，
                                // 强调色只来自 L2 捕获层透出（`colorFilterTint = null` ⇒
                                // 这里的 `selected` 已无着色作用，仅用于关掉点击后的 shape 语义）。
                                selected = false,
                                labelAlpha = labelAlpha,
                                interactive = dockExpanded,
                                onClick = { onSelect(tab) },
                            )
                        }
                    }
                }

                // ══ 收缩态圆心标（MeloX 既有惯例，BiliNext 的「末位 tab 居中」等价物）════
                //   收缩后胶囊只剩 48dp 宽，4 个 tab 放不下 ⇒ 只留**当前选中 tab**的图标，
                //   居中、红色、25dp。`expandedLayerAlpha`/`compactLayerAlpha` 是一条交叉淡入
                //   淡出（0.43~0.72 淡出旧层 / 0.52~0.82 淡入新层），中间有重叠区，避免闪空。
                if (compactLayerAlpha > 0.001f) {
                    // ⚠ 收缩态点击展开（2026-09-25 修 bug）：
                    //   展开的手势只挂在 L3 水珠上（dampedDock.modifier @ L1404），而水珠在收缩态
                    //   被定位到胶囊最左缘（(value+0.5)*tabWidth，tabWidth 仅 7dp），并不在用户看到的
                    //   居中红图标下方；L2 捕获层又 alpha(0f) 铺满整条并 onClick={} 拦截 ⇒
                    //   点中间的「左边按钮」命中不到展开手势，无法展开。
                    //   ⇒ 让收缩态这块居中红图标本身可点：zIndex 抬到最上层（盖过 L2/L3），
                    //      **仅在收缩态**（compactLayerAlpha>0.5f）挂 clickable 调 onSelect(selectedTab)
                    //     （onSelect 在调用方会把 tabBarMinimized=false ⇒ 展开）。
                    //   ⚠ 展开态不挂 clickable，否则会抢掉 L1 tab 的点击 / 水珠拖拽。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = compactLayerAlpha }
                            .zIndex(2f)
                            .then(
                                if (compactLayerAlpha > 0.5f) {
                                    Modifier.clickable { onSelect(selectedTab) }
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        RootGlyphIcon(
                            glyph = selectedTab.rootGlyph(),
                            modifier = Modifier.size(25.dp),
                            color = MeloXSystemColors.Red,
                            selected = true,
                        )
                    }
                }

                // ══ Layer 2：隐形捕获层（BiliNext 原样，**一块真玻璃**）══════════
                CompositionLocalProvider(LocalGlassTabScale provides tabScale) {
                    Row(
                        modifier = Modifier
                            .clearAndSetSemantics { }
                            .alpha(0f)
                            .layerBackdrop(tabsBackdrop)
                            .graphicsLayer { translationX = panelOffset }
                            .meloXLiquidCaptureLayer(
                                shape = Capsule(),
                                surfaceColor = bottomGlassSurfaceColor(),
                                pressProgress = dampedDock.pressProgress,
                            )
                            .then(dockHighlight.modifier)
                            .height(dropletHeight)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        primaryTabs.forEach { (tab, glyph) ->
                            RootTabButton(
                                title = tab.titleFor(source),
                                glyph = glyph,
                                // ⚠ 捕获层里 `selected` 必须传 false、改用 colorFilterTint ——
                                //   见 RootTabButton 里 colorFilterTint 的长注释。
                                //   传 true 会让「只有选中格有色」，水珠划过别的格就没有变色效果。
                                selected = false,
                                labelAlpha = labelAlpha,
                                interactive = false,
                                // kyant0 / BiliNext 的「划过才变色」核心：
                                //   整层 tab 全染强调色（层 alpha=0 看不见），
                                //   水珠一采样 ⇒ 只把它盖住那一格"照"出来。
                                colorFilterTint = accentColor,
                                onClick = {},
                            )
                        }
                    }
                }

                // ══ Layer 3：水珠指示器（BiliNext 原样，**缩放真的生效**）════════
                // BiliNext L444-448：alpha 走弹簧（不是二值开关），退出搜索时同步淡出
                val indicatorAlpha by animateFloatAsState(
                    targetValue = if (dockExpanded) 1f else 0f,
                    animationSpec = spring(stiffness = 180f, dampingRatio = 0.7f),
                    label = "melox-indicator-alpha",
                )
                if (indicatorAlpha > 0.001f && !frostedGlass) {
                    // BiliNext L449：rememberCombinedBackdrop(backdrop, tabsBackdrop)
                    // 本项目 backdrop 来自 CompositionLocal（= bottomChromeBackdrop / pageBackdrop）
                    val indicatorBackdrop = rememberCombinedBackdrop(
                        LocalMeloXBackdrop.current ?: tabsBackdrop,
                        tabsBackdrop,
                    )
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                // BiliNext L489-494：
                                //   tx = 10dp + (value + 0.5)·tabWidthPx − size.width/2 + panelOffset
                                translationX = with(density) { 10.dp.toPx() } +
                                    (dampedDock.value + 0.5f) * tabWidthPx -
                                    size.width / 2f +
                                    panelOffset
                                alpha = indicatorAlpha
                            }
                            .padding(horizontal = 10.dp)
                            .then(dockHighlight.gestureModifier)
                            .then(dampedDock.modifier)
                            .meloXLiquidTabSelection(
                                shape = Capsule(),
                                selected = true,
                                tint = selectionTint,
                                panelBackdrop = tabsBackdrop,
                                pressProgress = dampedDock.pressProgress,
                                // ★ BiliNext L534-541：缩放**照抄**，因为水珠挂了
                                //   dampedDock.modifier ⇒ press() 对整个子树生效，
                                //   scaleX/scaleY 会真的走到 pressedScale = 78/56。
                                layerBlock = {
                                    scaleX = dampedDock.scaleX
                                    scaleY = dampedDock.scaleY
                                    val velocity = dampedDock.velocity / 10f
                                    scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                                    scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                                },
                            )
                            .height(dropletHeight)
                            .width(dropletWidth),
                        contentAlignment = Alignment.Center,
                    ) {
                    }
                }
            }

            val searchContentDescription = stringResource(R.string.tab_search)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = -horizontalMargin, y = 0.dp)
                    .size(searchSize)
                    // 搜索键材质与底栏 nav 胶囊完全同源：同样走
                    // meloXLiquidBottomBar（vibrancy + blur + lens，且只叠
                    // surfaceColor、不额外加 Screen 白纱与 Hue tint），
                    // 避免观感与左侧底栏对不上。
                    .meloXLiquidBottomBar(
                        shape = Capsule(),
                        tint = bottomLiquidGlassTint(),
                        surfaceColor = bottomGlassSurfaceColor(),
                        refractionHeight = lerpDp(10.dp, 8.dp, bounceFrac),
                    )
                    .clickable(role = Role.Button) { onSelect(AppTab.Search) }
                    .semantics {
                        contentDescription = searchContentDescription
                    },
                contentAlignment = Alignment.Center,
            ) {
                RootGlyphIcon(
                    glyph = RootGlyph.Search,
                    modifier = Modifier.size(lerpDp(28.dp, 27.dp, bounceFrac)),
                    // ⚠ 搜索键的字形同样不能写死 —— 它读的是同一个被插值的 scheme。
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
private fun RowScope.RootTabButton(
    title: String,
    glyph: RootGlyph,
    selected: Boolean,
    labelAlpha: Float,
    // 原先这里有个 `dark: Boolean` 且完全没人用。改成「整棵子树插值配色」后，
    // 前景直接读 `colorScheme.onSurface` 就会跟着动 ⇒ 该参数连调用点一并删掉。
    interactive: Boolean = true,
    // ⚠ **「指示器划过才变色」的全部机关就在这一个参数**（BiliNext `GlassTabItem.colorFilterTint`）：
    //   非 null ⇒ 该 tab 无条件用这个颜色（`selected` 被无视），用于 **L2 隐形捕获层**，
    //   把整层 tab 都染成强调色；层本身 `alpha(0f)` 看不见，**只有水珠采样到的那一格会被"照"出来**
    //   ⇒ 水珠移到哪，哪格就亮成强调色 —— 不需要任何逐 tab 动画。
    //   L1 可见面板传 null ⇒ `selected` 生效（MeloX 保留红色口径）。
    colorFilterTint: Color? = null,
    onClick: () -> Unit,
) {
    val desiredForeground = when {
        colorFilterTint != null -> colorFilterTint
        selected -> MeloXSystemColors.Red
        // 回到 `colorScheme.onSurface` —— 外层 `BottomBarToneTheme` 已经把整套配色
        // 插值过，这个读值自己就会跟着动，**不需要**再在这里写端点。
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    }
    val foreground by animateColorAsState(
        targetValue = desiredForeground,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 480f),
        label = "melox-tab-foreground",
    )
    // BiliNext `GlassTabItem(scaleOverride = tabScale)` 的等价物：
    // Layer 2 捕获层里的 tab 内容随按压放大（1 → 1.2×），只影响**被水珠折射的那张图**。
    // 可见面板（Layer 1）不提供 LocalGlassTabScale ⇒ 默认恒 1f，不受影响。
    val tabScale = LocalGlassTabScale.current()
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = tabScale
                scaleY = tabScale
            }
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .then(
                if (interactive) Modifier
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
                    }
                else Modifier.clearAndSetSemantics { },
            ),
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
            // BiliNext L704：`colorFilterTint != null` 也算「强调」→ SemiBold
            fontWeight = if (selected || colorFilterTint != null) FontWeight.SemiBold else FontWeight.Medium,
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
    AppTab.Search -> RootGlyph.Search
}

@Composable
private fun RootGlyphIcon(
    glyph: RootGlyph,
    modifier: Modifier,
    color: Color,
    selected: Boolean = false,
) {
    // The home tab uses the custom house vector (traced from the user's image)
    // instead of the tinted symbol glyph, so tint applies to it like the others.
    if (glyph == RootGlyph.Home) {
        Icon(
            painter = painterResource(R.drawable.melox_home_tab),
            contentDescription = null,
            modifier = modifier,
            tint = color,
        )
        return
    }
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
    durationMillis = meloXPlayerTransitionDurationMillis,
    easing = LinearEasing,
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

/**
 * 与 [lerpDp] 相同，但**不夹** fraction —— 专供带过冲的几何。
 *
 * ⚠ 必须用这个版本，否则弹簧的过冲会在最后一步被 [lerpDp] 吃掉，Q 弹永远看不见。
 * fraction 由 [sprungFrac] 产出，已限幅，不会把布局撑爆；但**外扩方向**是否安全
 * 取决于该几何周围有没有邻居 —— 现在这件事交给**弹簧参数自己**，不再做事后压缩：
 * nav 展开方向的越界量由 `MeloXSprings.BottomBarExpandBounce` 定死在 6.02dp
 * （右预算 4dp + 左侧让出 2dp），再按 `NavExpandLeftShare` 落到两端；
 * 收缩方向由 0.30 那条管着（10.30dp，四周没有邻居）。
 */
private fun lerpDpBouncy(start: Dp, end: Dp, fraction: Float): Dp =
    (start.value + (end.value - start.value) * fraction).dp

/**
 * BiliNext `LiquidGlassTabsBar.kt` L99 的等价物：让 Layer 2（隐形捕获层）里的
 * tab 内容在按压时整体放大（`lerp(1f, 1.2f, pressProgress)`）。
 *
 * ⚠ 它**只影响捕获层的背板内容**（= 被水珠折射的那张图），不影响可见面板 ——
 * 所以「按压时图标放大」这件事在本项目只表现为折射内容的呼吸感，1:1 复刻 BiliNext。
 */
private val LocalGlassTabScale: ProvidableCompositionLocal<() -> Float> =
    staticCompositionLocalOf { { 1f } }
