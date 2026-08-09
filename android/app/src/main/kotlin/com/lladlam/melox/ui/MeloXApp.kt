package com.lladlam.melox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.lladlam.melox.core.account.rememberNeteaseSessionStore
import com.lladlam.melox.ui.account.NeteaseLoginScreen
import com.lladlam.melox.ui.library.LibraryScreen
import com.lladlam.melox.ui.player.MeloXIOSMiniPlayer
import com.lladlam.melox.ui.player.MeloXIOSNowPlayingSharedHost
import com.lladlam.melox.ui.player.rememberMeloXPlaybackUiState
import com.lladlam.melox.ui.search.SearchScreen
import com.lladlam.melox.ui.settings.SettingsScreen

enum class AppTab(val title: String) {
    Home("首页"),
    Explore("发现"),
    Library("音乐库"),
    Settings("设置"),
    Search("搜索"),
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MeloXApp(
    openNowPlayingRequest: Int = 0,
) {
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showNeteaseLogin by remember { mutableStateOf(false) }
    var loginReturnTab by remember { mutableStateOf(AppTab.Settings) }
    var tabBarMinimized by remember { mutableStateOf(false) }
    var scrollAccumulator by remember { mutableFloatStateOf(0f) }
    val playbackState = rememberMeloXPlaybackUiState()
    val neteaseSession = rememberNeteaseSessionStore()

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
            showNowPlaying = true
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
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            val sharedScope = this
            val fullPlayerVisible = showNowPlaying && playbackState.hasMedia

            // One and only one recording layer for bottom chrome. AndroidLiquidGlass
            // controls sample this source instead of recording their own backdrops.
            val glassBackdrop = rememberLayerBackdrop()

            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(tabBarMinimizeConnection)
                    .layerBackdrop(glassBackdrop),
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
                        AppTab.Home -> MeloXSectionShell(
                            "首页",
                            "每日推荐与个性化内容将按 iOS MeloX 结构接入。",
                        )
                        AppTab.Explore -> MeloXSectionShell(
                            "发现",
                            "推荐、排行榜、精品与分类内容正在迁移。",
                        )
                        AppTab.Library -> LibraryScreen(
                            session = neteaseSession,
                            playlistBackEnabled = !fullPlayerVisible,
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

            MeloXBottomChrome(
                selectedTab = selectedTab,
                onSelect = { tab ->
                    tabBarMinimized = false
                    selectedTab = tab
                },
                hasMedia = playbackState.hasMedia,
                minimized = tabBarMinimized,
                backdrop = glassBackdrop,
                modifier = Modifier.align(Alignment.BottomCenter),
                miniPlayer = {
                    AnimatedVisibility(
                        visible = !fullPlayerVisible,
                        enter = EnterTransition.None,
                        exit = ExitTransition.None,
                    ) {
                        MeloXIOSMiniPlayer(
                            state = playbackState,
                            onExpand = { showNowPlaying = true },
                            sharedTransitionScope = sharedScope,
                            animatedVisibilityScope = this,
                            glassBackdrop = glassBackdrop,
                        )
                    }
                },
            )

            if (fullPlayerVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.forEach { change -> change.consume() }
                                }
                            }
                        },
                )
            }

            AnimatedVisibility(
                visible = fullPlayerVisible,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                modifier = Modifier.fillMaxSize(),
            ) {
                MeloXIOSNowPlayingSharedHost(
                    state = playbackState,
                    onDismiss = { showNowPlaying = false },
                    sharedTransitionScope = sharedScope,
                    animatedVisibilityScope = this,
                )
            }

            BackHandler(enabled = fullPlayerVisible && !showNeteaseLogin) {
                showNowPlaying = false
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
