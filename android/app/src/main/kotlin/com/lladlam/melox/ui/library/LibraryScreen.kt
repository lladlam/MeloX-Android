package com.lladlam.melox.ui.library

import androidx.compose.material3.TextButton

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.lladlam.melox.ui.animation.MeloXMotion
import com.lladlam.melox.ui.animation.MeloXPageBoundsTransform
import com.lladlam.melox.ui.layout.rememberMeloXStableStatusBarHeight
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import coil3.compose.AsyncImage
import com.lladlam.melox.R
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.download.MeloXDownloadPlaylistRef
import com.lladlam.melox.core.download.MeloXProviderDownloadStore
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.library.NeteaseLibraryCache
import com.lladlam.melox.core.library.NeteaseLibrarySnapshot
import com.lladlam.melox.core.library.NeteasePlaylistDetail
import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.recommendation.LocalRecommendationStore
import com.lladlam.melox.playback.ProviderPlaybackCommands
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.model.MusicAccountSummary
import com.lladlam.melox.core.music.model.MusicAlbumSummary
import com.lladlam.melox.core.music.model.MusicArtistSummary
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.provider.MeloXLegacyUiBridge
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.core.music.provider.MeloXUnifiedFavorites
import com.lladlam.melox.core.music.provider.loadAllPlaylistTracks
import com.lladlam.melox.core.music.provider.AlbumCapability
import com.lladlam.melox.core.music.provider.ArtistCapability
import com.lladlam.melox.core.music.provider.LibraryCollectionCapability
import com.lladlam.melox.core.music.provider.MusicProvider
import com.lladlam.melox.core.music.provider.PlaylistCapability
import com.lladlam.melox.core.music.provider.PlaylistSyncCapability
import com.lladlam.melox.playback.MeloXPlaybackService
import com.lladlam.melox.core.music.provider.UserLibraryCapability
import com.lladlam.melox.core.music.provider.LocalAggregationCapability
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.core.network.NeteaseCollectionDetailsClient
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.MeloXInterruptiblePage
import com.lladlam.melox.ui.layout.LocalMeloXListDetail
import com.lladlam.melox.ui.layout.LocalMeloXInDetailPane
import com.lladlam.melox.ui.layout.rememberMeloXDetailSelection
import com.lladlam.melox.core.music.provider.loadAllMusicPages
import com.lladlam.melox.core.music.provider.loadAllAlbumTracks
import com.lladlam.melox.core.music.provider.loadAllArtistTracks
import com.lladlam.melox.core.music.provider.DownloadCapability
import com.lladlam.melox.core.provider.youtubemusic.YouTubeSessionStore
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import com.lladlam.melox.ui.glass.meloXLiquidBottomBar
import com.lladlam.melox.ui.glass.MeloXActionIcon
import com.lladlam.melox.ui.glass.MeloXSwipeAction
import com.lladlam.melox.ui.glass.MeloXSwipeActionRow
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXShapes
import com.lladlam.melox.ui.glass.MeloXTypography
import com.lladlam.melox.ui.glass.meloXContentSurface
import com.lladlam.melox.ui.glass.MeloXIosTopBar
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.glass.meloXLiquidTabSelection
import com.lladlam.melox.ui.glass.meloXIconDrawingScale
import com.lladlam.melox.ui.player.MeloXFlowingLightBackdrop
import com.lladlam.melox.ui.player.MeloXSongActionsOverlay
import com.lladlam.melox.ui.sharing.MeloXNeteaseResourceShareActivity
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.meloXEdgeOverscrollHaptic
import com.lladlam.melox.ui.settings.MeloXSwipeFullAction
import com.lladlam.melox.ui.layout.rememberMeloXWindowInfo
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import com.lladlam.melox.ui.theme.isMeloXDarkTheme
import com.lladlam.melox.ui.podcast.MeloXPodcastScreen
import com.lladlam.melox.ui.cloud.MeloXCloudMusicScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToInt

// 上游 0.6.2（5b7dcaa）：页面标题改走 StringRes，并新增「专辑 / 艺人」两页
// （仅在该 provider 支持时出现）。
private enum class MeloXLibraryPage(@androidx.annotation.StringRes val titleRes: Int) {
    Songs(R.string.library_page_songs),
    Playlists(R.string.library_page_playlists),
    Albums(R.string.library_browse_albums),
    Artists(R.string.library_browse_artists),
    Podcasts(R.string.library_page_podcasts),
    Cloud(R.string.library_page_cloud),
    History(R.string.library_page_history),
    Downloads(R.string.library_page_downloads);

    /** Mirrors iOS `LibraryView.searchable`: cloud has its own whole-drive search. */
    val supportsLibrarySearch: Boolean
        get() = this == Songs || this == Playlists || this == Albums ||
            this == Artists || this == History
}

@Composable
private fun MeloXLibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .meloXLiquidButton(
                shape = RoundedCornerShape(20.dp),
                surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .055f),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (query.isBlank()) {
            Text(
                stringResource(R.string.library_search_hint),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f),
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun librarySearchMatches(query: String, vararg fields: String?): Boolean =
    query.isBlank() || fields.any { field ->
        field?.contains(query, ignoreCase = true) == true
    }

/**
 * Presentation capability gate only. A page is present when the active source
 * implements the corresponding capability, not because of a source name.
 */
private fun MeloXLibraryPage.isEnabled(source: MusicSource, provider: MusicProvider?): Boolean = when {
    source == MusicSource.Bilibili -> this == MeloXLibraryPage.Playlists || this == MeloXLibraryPage.Downloads
    source == MusicSource.Local -> this == MeloXLibraryPage.Songs || this == MeloXLibraryPage.Playlists
    source != MusicSource.Netease -> when (this) {
        MeloXLibraryPage.Playlists -> true
        MeloXLibraryPage.Albums, MeloXLibraryPage.Artists -> provider is LibraryCollectionCapability
        else -> false
    }
    this == MeloXLibraryPage.Albums || this == MeloXLibraryPage.Artists -> false
    this == MeloXLibraryPage.Podcasts -> MeloXSettingsRuntime.podcastsEnabled && MeloXSettingsRuntime.podcastsLibraryPlacement
    this == MeloXLibraryPage.History -> MeloXSettingsRuntime.listeningHistoryEnabled
    this == MeloXLibraryPage.Cloud -> MeloXSettingsRuntime.cloudMusicEnabled && MeloXSettingsRuntime.cloudLibraryPlacement
    this == MeloXLibraryPage.Downloads -> MeloXSettingsRuntime.downloadsEnabled && MeloXSettingsRuntime.downloadsLibraryPlacement
    else -> true
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    session: NeteaseSessionStore,
    onLogin: (() -> Unit)?,
    source: MusicSource = MusicSource.Netease,
    forcedPageName: String? = null,
    playlistBackEnabled: Boolean = true,
    onModalVisibilityChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val window = rememberMeloXWindowInfo()
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val client = remember(appContext) {
        NeteaseLibraryClient(
            cookieProvider = { NeteaseSessionStore.readCookie(appContext) },
        )
    }
    // ★ 收藏列表左滑「取消收藏」用（2026-10-06）：真正写入网易云（t=2 取消订阅）。
    val playlistOperations = remember(appContext) {
        NeteaseMusicOperationsClient(
            cookieProvider = { NeteaseSessionStore.readCookie(appContext) },
        )
    }
    val provider = remember(source, appContext) {
        if (source == MusicSource.Netease) null else MeloXMusicProviders.create(appContext).require(source)
    }
    val providerLibrary = provider as? UserLibraryCapability
    val cache = remember(appContext) { NeteaseLibraryCache(appContext) }
    val downloadStore = remember(appContext) { MeloXDownloadStore.get(appContext) }
    val libraryPagePreferenceKey = if (source == MusicSource.Netease) {
        "library_last_page"
    } else {
        "library_last_page_${source.storageValue}"
    }

    val unifiedFavoritesAvailable = MeloXUnifiedFavorites.enabled(appContext)
    val initialLibraryPage = remember(source, forcedPageName, session.isLoggedIn, unifiedFavoritesAvailable) {
        val fallback = if (source == MusicSource.Netease &&
            !session.isLoggedIn &&
            unifiedFavoritesAvailable
        ) {
            MeloXLibraryPage.Playlists
        } else if (source == MusicSource.Netease || source == MusicSource.Local) {
            MeloXLibraryPage.Songs
        } else {
            MeloXLibraryPage.Playlists
        }
        val name = forcedPageName ?: if (MeloXSettingsRuntime.rememberLibraryPage) {
            MeloXSettingsPreferences.string(appContext, libraryPagePreferenceKey, fallback.name)
        } else fallback.name
        runCatching { MeloXLibraryPage.valueOf(name) }
            .getOrDefault(fallback)
            .takeIf { forcedPageName != null || it.isEnabled(source, provider) }
            ?: fallback
    }
    val onDetailSelected = rememberMeloXDetailSelection()
    var selectedPage by remember(source, forcedPageName) { mutableStateOf(initialLibraryPage) }
    var librarySearchQuery by remember(source) { mutableStateOf("") }
    var selectedPlaylist by remember(source, session.cookie) { mutableStateOf<NeteasePlaylistSummary?>(null) }
    var savedAlbums by remember(source) { mutableStateOf<List<MusicAlbumSummary>>(emptyList()) }
    var followedArtists by remember(source) { mutableStateOf<List<MusicArtistSummary>>(emptyList()) }
    var selectedAlbum by remember(source) { mutableStateOf<MusicAlbumSummary?>(null) }
    var selectedArtist by remember(source) { mutableStateOf<MusicArtistSummary?>(null) }
    var showLocalRecommendations by remember(source) { mutableStateOf(false) }
    var snapshot by remember(source, session.cookie) { mutableStateOf<NeteaseLibrarySnapshot?>(null) }
    var providerAccount by remember(source) { mutableStateOf<MusicAccountSummary?>(null) }
    var loading by remember(source, session.cookie) { mutableStateOf(source != MusicSource.Netease) }
    var errorMessage by remember(source, session.cookie) { mutableStateOf<String?>(null) }
    val playlistListState = rememberLazyListState()
    // 本地推荐歌单：readRecommendedTracks() 要反序列化 candidate_tracks +
    // recommendations 两个大 JSON，绝不能写在组合体里（每次重组都跑且在主线程）。
    // 用 produceState 异步加载一次。
    val localRecommendations by produceState(initialValue = emptyList<MusicTrack>(), context, source) {
        value = withContext(Dispatchers.IO) {
            runCatching { LocalRecommendationStore.readRecommendedTracks(context) }.getOrDefault(emptyList())
        }
    }

    suspend fun refreshLibrary() {
        loading = true
        errorMessage = null
        if (source == MusicSource.Netease) {
            if (!session.isLoggedIn) {
                snapshot = NeteaseLibrarySnapshot(emptyList(), emptyList(), emptyList())
                    .withUnifiedFavorites(appContext)
                loading = false
                return
            }
            if (session.profile == null) session.refreshProfile(force = true)
            val userId = session.profile?.userId
            if (userId == null) {
                loading = false
                return
            }
            runCatching { client.snapshot(userId) }
                .onSuccess { fresh ->
                    snapshot = fresh.withUnifiedFavorites(appContext)
                    session.profile?.userId?.let { uid ->
                        // 防御：网易云会话（MUSIC_U）过期后，/api/song/like/get 会返回
                        // HTTP 200 但空 ids 数组，导致本次刷新把已收藏歌曲「静默清空」。
                        // 若刷新连「我喜欢的音乐」歌单都加载不到（likedPlaylistId == null），
                        // 基本可判定会话已失效、收藏列表不可信——此时保留上次成功缓存的
                        // 收藏，避免用户所有爱心歌曲被自动删除消失。真正的「取消收藏」走
                        // withSongLiked 写入，不会经过这条自动刷新路径。
                        val persisted = cache.loadSnapshot(uid)?.let { cached ->
                            if (fresh.likedSongs.isEmpty() && fresh.likedPlaylistId == null && cached.likedSongs.isNotEmpty()) {
                                fresh.copy(likedSongs = cached.likedSongs)
                            } else {
                                fresh
                            }
                        } ?: fresh
                        cache.saveSnapshot(uid, persisted)
                    }
                }
                .onFailure { errorMessage = it.message ?: "音乐库加载失败" }
        } else {
            if (source == MusicSource.Local) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val tracks = (provider as? LocalAggregationCapability)
                            ?.aggregationTracks(page = 1, pageSize = 200)
                            ?.items
                            ?: error("本地音乐库能力不可用")
                        providerAccount = MusicAccountSummary(
                            source = MusicSource.Local,
                            id = "local",
                            displayName = "本地音乐库",
                            subtitle = "本地媒体文件",
                        )
                        snapshot = NeteaseLibrarySnapshot(
                            likedSongs = tracks.map(MeloXLegacyUiBridge::track),
                            playlists = emptyList(),
                            recentSongs = emptyList(),
                            likedPlaylistId = null,
                        ).withUnifiedFavorites(appContext)
                    }
                }.onFailure {
                    providerAccount = null
                    snapshot = null
                    errorMessage = it.message ?: "本地音乐库加载失败"
                }
                loading = false
                return
            }
            val capability = providerLibrary
            if (capability == null) {
                providerAccount = null
                snapshot = null
                errorMessage = "${source.displayName} 当前没有提供个人音乐库能力"
                loading = false
                return
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val account = capability.accountSummary()
                    val playlists = if (account != null && provider is PlaylistCapability) {
                        loadAllMusicPages(key = { it.id }) { page -> capability.userPlaylists(page = page, pageSize = 100) }
                    } else {
                        emptyList()
                    }
                    account to playlists
                }
            }.onSuccess { (account, playlists) ->
                providerAccount = account
                snapshot = if (account == null) null else {
                    MeloXLegacyUiBridge.library(playlists).withUnifiedFavorites(appContext)
                }
                val collections = provider as? LibraryCollectionCapability
                if (account != null && collections != null) {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            loadAllMusicPages(key = { it.id }) { page -> collections.savedAlbums(page = page, pageSize = 100) } to
                                loadAllMusicPages(key = { it.id }) { page -> collections.followedArtists(page = page, pageSize = 100) }
                        }
                    }.onSuccess { (albums, artists) ->
                        savedAlbums = albums
                        followedArtists = artists
                    }
                } else {
                    savedAlbums = emptyList()
                    followedArtists = emptyList()
                }
            }.onFailure { failure ->
                providerAccount = null
                snapshot = null
                errorMessage = failure.message ?: "${source.displayName} 音乐库加载失败"
            }
        }
        loading = false
    }

    val youtubeSessionRevision by YouTubeSessionStore.revision.collectAsState()
    LaunchedEffect(source, session.cookie, session.profile?.userId, youtubeSessionRevision.takeIf { source == MusicSource.YouTubeMusic }) {
        if (source == MusicSource.Netease) {
            val userId = session.profile?.userId
            if (userId == null) {
                if (MeloXUnifiedFavorites.enabled(appContext)) {
                    snapshot = NeteaseLibrarySnapshot(emptyList(), emptyList(), emptyList())
                        .withUnifiedFavorites(appContext)
                }
                loading = false
                return@LaunchedEffect
            }
            cache.loadSnapshot(userId)?.let { snapshot = it.withUnifiedFavorites(appContext) }
            if (NeteaseLibraryCache.beginLibraryColdStartRefresh(userId, cache.cacheDir)) {
                refreshLibrary()
            }
        } else {
            refreshLibrary()
        }
    }

    // The app-owned playlist can change from any source's song menu. Refresh
    // the injected Library row and its count without a network round trip.
    val unifiedFavoritesRevision = MeloXUnifiedFavorites.revision
    LaunchedEffect(unifiedFavoritesRevision, source) {
        val base = snapshot ?: if (source == MusicSource.Netease && !session.isLoggedIn) {
            NeteaseLibrarySnapshot(emptyList(), emptyList(), emptyList())
        } else {
            null
        }
        snapshot = base?.withUnifiedFavorites(appContext)
    }

    LaunchedEffect(source, selectedPage) {
        if (forcedPageName == null && MeloXSettingsRuntime.rememberLibraryPage) {
            MeloXSettingsPreferences.setString(appContext, libraryPagePreferenceKey, selectedPage.name)
        }
    }

    LaunchedEffect(
        source,
        MeloXSettingsRuntime.podcastsEnabled,
        MeloXSettingsRuntime.listeningHistoryEnabled,
        MeloXSettingsRuntime.cloudMusicEnabled,
        MeloXSettingsRuntime.downloadsEnabled,
    ) {
        if (forcedPageName == null && !selectedPage.isEnabled(source, provider)) {
            selectedPage = if (source == MusicSource.Netease || source == MusicSource.Local) MeloXLibraryPage.Songs else MeloXLibraryPage.Playlists
        }
    }

    BackHandler(enabled = playlistBackEnabled && selectedPlaylist != null) {
        selectedPlaylist = null
    }
    BackHandler(enabled = selectedAlbum != null) { selectedAlbum = null }
    BackHandler(enabled = selectedArtist != null) { selectedArtist = null }

    if (source == MusicSource.Netease && !session.isLoggedIn && !MeloXUnifiedFavorites.enabled(appContext)) {
        MeloXLibraryLoginUnavailable(onLogin, source)
        return
    }
    if (source != MusicSource.Netease && source != MusicSource.Local && !loading && providerAccount == null && errorMessage == null) {
        MeloXLibraryLoginUnavailable(onLogin, source)
        return
    }
    val splitPlaylistDetail = LocalMeloXListDetail.current && !LocalMeloXInDetailPane.current
    // Keep the outgoing destination alive until the page container disposes it.
    val selection = Triple(selectedPlaylist, selectedAlbum, selectedArtist)
    val detailOpen = selectedPlaylist != null || selectedAlbum != null || selectedArtist != null || showLocalRecommendations
    var retainedSelection by remember { mutableStateOf(selection) }
    var retainedRecommendations by remember { mutableStateOf(showLocalRecommendations) }
    androidx.compose.runtime.SideEffect {
        if (detailOpen) {
            retainedSelection = selection
            retainedRecommendations = showLocalRecommendations
        }
    }
    val (detailPlaylist, detailAlbum, detailArtist) = if (detailOpen) selection else retainedSelection
    val detailRecommendations = if (detailOpen) showLocalRecommendations else retainedRecommendations
    PullToRefreshBox(
        isRefreshing = loading && snapshot != null,
        onRefresh = { scope.launch { refreshLibrary() } },
        modifier = Modifier.fillMaxSize(),
    ) {
      SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
          val sharedScope = this

        AnimatedContent(
            targetState = selectedPlaylist.takeUnless { splitPlaylistDetail },
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                (fadeIn(tween(MeloXMotion.ContentEnterMillis, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(tween(MeloXMotion.ContentExitMillis, easing = FastOutSlowInEasing))).apply {
                    targetContentZIndex = if (targetState != null) 2f else 0f
                }
            },
            contentKey = { playlist -> playlist?.id ?: Long.MIN_VALUE },
            label = "library-playlist-detail-transition",
        ) { targetPlaylist ->
            val playlistTransitionVisibilityScope = this
            if (targetPlaylist != null) {
                MeloXPlaylistDetailScreen(
                    initialPlaylist = targetPlaylist,
                    client = client,
                    onBack = { selectedPlaylist = null },
                    sharedTransitionScope = sharedScope,
                    animatedVisibilityScope = playlistTransitionVisibilityScope,
                    onModalVisibilityChanged = onModalVisibilityChanged,
                    onLibraryChanged = { scope.launch { refreshLibrary() } },
                    likedPlaylistId = snapshot?.likedPlaylistId,
                    onSongLikeChanged = { song, liked ->
                        val current = snapshot ?: return@MeloXPlaylistDetailScreen
                        val updated = current.withSongLiked(song, liked)
                        snapshot = updated
                        session.profile?.userId?.let { userId ->
                            scope.launch { cache.saveSnapshot(userId, updated) }
                        }
                    },
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        // ⚠ 稳定值：可见式 `statusBarsPadding()` 在沉浸切换时会 0↔真实高度跳。
                        .padding(top = rememberMeloXStableStatusBarHeight())
                        .padding(horizontal = if (window.supportsTwoPane) window.gutter else 0.dp),
                ) {
                    MeloXIosTopBar(
                        title = stringResource(R.string.tab_library),
                    )

                    MeloXLibrarySegmentedPicker(
                        selected = selectedPage,
                        onSelected = { selectedPage = it },
                        source = source,
                        forcedPageName = forcedPageName,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )

                    if (selectedPage.supportsLibrarySearch) {
                        MeloXLibrarySearchField(
                            query = librarySearchQuery,
                            onQueryChange = { librarySearchQuery = it },
                            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp),
                        )
                    }

                    if (errorMessage != null && snapshot == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = errorMessage.orEmpty(),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = stringResource(R.string.library_reload),
                                    modifier = Modifier
                                        .padding(top = 12.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable { scope.launch { refreshLibrary() } }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    } else if (loading && snapshot == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val data = snapshot ?: NeteaseLibrarySnapshot(emptyList(), emptyList(), emptyList())
                        val libraryQuery = librarySearchQuery.trim()
                        val filteredLikedSongs = remember(data.likedSongs, libraryQuery) {
                            data.likedSongs.filter { song ->
                                librarySearchMatches(
                                    libraryQuery,
                                    song.name,
                                    song.artists,
                                    song.album,
                                )
                            }
                        }
                        val filteredRecentSongs = remember(data.recentSongs, libraryQuery) {
                            data.recentSongs.filter { song ->
                                librarySearchMatches(
                                    libraryQuery,
                                    song.name,
                                    song.artists,
                                    song.album,
                                )
                            }
                        }
                        val filteredPlaylists = remember(data.playlists, libraryQuery) {
                            data.playlists.filter { playlist ->
                                librarySearchMatches(
                                    libraryQuery,
                                    playlist.name,
                                    playlist.creatorName,
                                    playlist.description,
                                    playlist.providerPlaylist?.description,
                                )
                            }
                        }
                        val filteredSavedAlbums = remember(savedAlbums, libraryQuery) {
                            savedAlbums.filter { album ->
                                librarySearchMatches(
                                    libraryQuery,
                                    album.title,
                                    album.artists.joinToString(" / ") { it.name },
                                )
                            }
                        }
                        val filteredFollowedArtists = remember(followedArtists, libraryQuery) {
                            followedArtists.filter { artist ->
                                librarySearchMatches(
                                    libraryQuery,
                                    artist.name,
                                    artist.description,
                                )
                            }
                        }
                        when (selectedPage) {
                            MeloXLibraryPage.Songs -> MeloXLibrarySongsPage(
                                songs = filteredLikedSongs,
                                onPlay = { song ->
                                    PlaybackCommands.playQueue(
                                        context = context,
                                        songs = filteredLikedSongs,
                                        selectedSongId = song.id,
                                        onFailure = { errorMessage = it.message ?: "播放失败" },
                                    )
                                },
                                onPlayAll = {
                                    PlaybackCommands.playAll(
                                        context = context,
                                        songs = filteredLikedSongs,
                                        onFailure = { errorMessage = it.message ?: "播放失败" },
                                    )
                                },
                                onHeartMode = if (source == MusicSource.Netease) {
                                    {
                                        val seed = data.likedSongs.randomOrNull()
                                        val playlistId = data.likedPlaylistId
                                        if (seed != null && playlistId != null) scope.launch {
                                            runCatching { client.intelligenceModeSongs(seed.id, playlistId) }
                                                .onSuccess { songs -> PlaybackCommands.playAll(context, songs, heartMode = true) }
                                                .onFailure { errorMessage = it.message ?: "无法启动心动模式" }
                                        }
                                    }
                                } else null,
                            )

                            MeloXLibraryPage.Playlists -> MeloXLibraryPlaylistsPage(
                                playlists = filteredPlaylists,
                                localRecommendations = localRecommendations,
                                onLocalRecommendationsClick = {
                                    onDetailSelected()
                                    selectedPlaylist = null
                                    selectedAlbum = null
                                    selectedArtist = null
                                    showLocalRecommendations = true
                                },
                                onPlaylistClick = {
                                    onDetailSelected()
                                    selectedAlbum = null
                                    selectedArtist = null
                                    showLocalRecommendations = false
                                    selectedPlaylist = it
                                },
                                // ★ 歌单删除 / 取消收藏（2026-10-07 修复「收藏的歌单删不掉」）：
                                //   资料库歌单列表来自 `/api/user/playlist`，只含两类内容 ——
                                //     ① 系统歌单「我喜欢的音乐」：网易云不允许删除或取关 ⇒ 直接提示，不发请求
                                //     ② 用户自创歌单：必须用 `/api/playlist/delete` 真正删除
                                //   此前一律调 `setPlaylistSubscribed(false)`（订阅 / 取消订阅**别人的**
                                //   歌单），对上面两类都是无效操作 ⇒ 点了没反应。
                                onRemovePlaylist = { target ->
                                    scope.launch {
                                        if (target.isMeloXLocal) {
                                            errorMessage = "弥乐「我喜欢」不能在这里删除"
                                            return@launch
                                        }
                                        if (target.id == snapshot?.likedPlaylistId) {
                                            errorMessage = "「我喜欢的音乐」是系统歌单，无法删除"
                                            return@launch
                                        }
                                        val uid = session.profile?.userId
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                // 本列表不含订阅的歌单，因此除「我喜欢的音乐」外都是自创的；
                                                // creatorUserId 缺失时也按自创处理，避免退化成无效的取消订阅。
                                                if (uid == null || target.creatorUserId == null || target.creatorUserId == uid) {
                                                    playlistOperations.deletePlaylist(target.id)
                                                } else {
                                                    playlistOperations.setPlaylistSubscribed(target.id, false)
                                                }
                                            }
                                        }.onSuccess {
                                            refreshLibrary()
                                        }.onFailure {
                                            errorMessage = it.message ?: "删除歌单失败"
                                        }
                                    }
                                },
                                likedPlaylistId = snapshot?.likedPlaylistId,
                                listState = playlistListState,
                                sharedTransitionScope = sharedScope,
                                animatedVisibilityScope = playlistTransitionVisibilityScope,
                            )

                            MeloXLibraryPage.Podcasts -> MeloXPodcastScreen(subscriptionsOnly = true)

                            MeloXLibraryPage.Cloud -> Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            ) {
                                MeloXCloudMusicScreen(embedded = true)
                            }

                            MeloXLibraryPage.History -> MeloXLibrarySongsPage(
                                songs = filteredRecentSongs,
                                onPlay = { song ->
                                    PlaybackCommands.playQueue(
                                        context = context, songs = filteredRecentSongs, selectedSongId = song.id,
                                        onFailure = { errorMessage = it.message ?: "播放失败" },
                                    )
                                },
                                onPlayAll = {
                                    PlaybackCommands.playAll(
                                        context = context,
                                        songs = filteredRecentSongs,
                                        onFailure = { errorMessage = it.message ?: "播放失败" },
                                    )
                                },
                            )

                            MeloXLibraryPage.Downloads -> MeloXLibraryDownloadsPage(downloadStore)

                            MeloXLibraryPage.Albums -> MeloXLibraryCoverListPage(
                                rows = filteredSavedAlbums.map { album ->
                                    MeloXLibraryCoverRow(
                                        key = album.id.value,
                                        title = album.title,
                                        subtitle = album.artists.joinToString(" / ") { it.name }
                                            .ifBlank { album.id.sourceId.displayName },
                                        artworkUrl = album.artworkUrl,
                                    )
                                },
                                emptyRes = R.string.library_no_albums,
                                onClick = { index ->
                                    onDetailSelected()
                                    selectedPlaylist = null
                                    selectedArtist = null
                                    showLocalRecommendations = false
                                    selectedAlbum = filteredSavedAlbums.getOrNull(index)
                                },
                            )

                            MeloXLibraryPage.Artists -> MeloXLibraryCoverListPage(
                                rows = filteredFollowedArtists.map { artist ->
                                    MeloXLibraryCoverRow(
                                        key = artist.id.value,
                                        title = artist.name,
                                        subtitle = artist.description?.takeIf { it.isNotBlank() }
                                            ?: artist.id.sourceId.displayName,
                                        artworkUrl = artist.artworkUrl,
                                    )
                                },
                                emptyRes = R.string.library_no_artists,
                                onClick = { index ->
                                    onDetailSelected()
                                    selectedPlaylist = null
                                    selectedAlbum = null
                                    showLocalRecommendations = false
                                    selectedArtist = filteredFollowedArtists.getOrNull(index)
                                },
                            )
                        }
                    }
                }
        }
      }
    }
    }
    MeloXInterruptiblePage(
        visible = (splitPlaylistDetail && selectedPlaylist != null) || selectedAlbum != null || selectedArtist != null || showLocalRecommendations,
        onBack = {
            when {
                selectedArtist != null -> selectedArtist = null
                selectedAlbum != null -> selectedAlbum = null
                selectedPlaylist != null -> selectedPlaylist = null
                else -> showLocalRecommendations = false
            }
        },
    ) {
        when {
            splitPlaylistDetail && detailPlaylist != null && detailAlbum == null && detailArtist == null -> {
                val playlist = detailPlaylist
                key(playlist.id) {
                SharedTransitionLayout(Modifier.fillMaxSize()) {
                    AnimatedVisibility(visible = true) {
                        MeloXPlaylistDetailScreen(
                            initialPlaylist = playlist,
                            client = client,
                            onBack = { selectedPlaylist = null },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this,
                            onModalVisibilityChanged = onModalVisibilityChanged,
                            onLibraryChanged = { scope.launch { refreshLibrary() } },
                            likedPlaylistId = snapshot?.likedPlaylistId,
                            onSongLikeChanged = { song, liked ->
                                val current = snapshot ?: return@MeloXPlaylistDetailScreen
                                val updated = current.withSongLiked(song, liked)
                                snapshot = updated
                                session.profile?.userId?.let { userId ->
                                    scope.launch { cache.saveSnapshot(userId, updated) }
                                }
                            },
                        )
                    }
                }
                }
            }
            detailAlbum != null -> MeloXUnifiedProviderAlbumDetailScreen(
                album = detailAlbum, onBack = { selectedAlbum = null },
            )
            detailArtist != null -> MeloXProviderArtistDetailScreen(
                artist = detailArtist, onBack = { selectedArtist = null },
            )
            detailRecommendations -> LocalRecommendationPlaylistScreen(onBack = { showLocalRecommendations = false })
        }
    }
}

private enum class MeloXDownloadsPage { Root, Active, Playlists, PlaylistDetail }
private enum class MeloXLocalBrowseMode(val title: String) {
    Songs("歌曲"), Artists("艺术家"), Albums("专辑"), Folders("文件夹")
}

@Composable
private fun MeloXLibraryDownloadsPage(downloads: MeloXDownloadStore) {
    val context = LocalContext.current
    val providerDownloads = remember(context) { MeloXProviderDownloadStore.get(context) }
    var page by remember { mutableStateOf(MeloXDownloadsPage.Root) }
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var browseMode by remember { mutableStateOf(MeloXLocalBrowseMode.Songs) }
    var browseGroup by remember { mutableStateOf<String?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    val active = downloads.activeDownloads.values.toList()
    val paused = downloads.pausedDownloads.values.toList()
    val completed = downloads.downloads.toList()
    val providerCompleted = providerDownloads.downloads.toList()
    val providerAttempts = providerDownloads.attempts.toMap()
    val groups = downloads.downloadedPlaylists
    val browseGroups = remember(completed, browseMode) {
        when (browseMode) {
            MeloXLocalBrowseMode.Songs -> emptyMap()
            MeloXLocalBrowseMode.Artists -> completed.groupBy { it.song.artists.ifBlank { "未知艺术家" } }
            MeloXLocalBrowseMode.Albums -> completed.groupBy { it.song.album.ifBlank { "未知专辑" } }
            MeloXLocalBrowseMode.Folders -> mapOf("Music/MeloX" to completed)
        }.toSortedMap()
    }
    val visibleCompleted = remember(completed, browseMode, browseGroup, browseGroups) {
        if (browseMode == MeloXLocalBrowseMode.Songs) completed
        else browseGroup?.let { browseGroups[it].orEmpty() }.orEmpty()
    }

    fun exportSelected() {
        if (selectedIds.isEmpty()) return
        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            (context as? Activity)?.let {
                ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 4104)
            }
            exportMessage = "请授予存储权限后再次导出"
            return
        }
        downloads.exportToMusicLibrary(selectedIds) { result ->
            exportMessage = result.fold(
                onSuccess = { "已导出 $it 首到 Music/MeloX" },
                onFailure = { it.message ?: "导出失败" },
            )
        }
    }

    BackHandler(enabled = page != MeloXDownloadsPage.Root) {
        page = if (page == MeloXDownloadsPage.PlaylistDetail) MeloXDownloadsPage.Playlists else MeloXDownloadsPage.Root
        if (page != MeloXDownloadsPage.PlaylistDetail) selectedPlaylistId = null
    }

    val split = LocalMeloXListDetail.current && !LocalMeloXInDetailPane.current
    if (page == MeloXDownloadsPage.Root || split) LazyColumn(
  modifier = Modifier.fillMaxSize(),
  contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 146.dp),
  verticalArrangement = Arrangement.spacedBy(4.dp),
         ) {
  providerDownloads.taskRecoveryError?.let { message ->
      item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
  }
  items(providerAttempts.entries.toList(), key = { "provider-attempt:${it.key}" }) { (key, attempt) ->
      val failure = providerDownloads.downloadErrors[key]
      val running = providerDownloads.activeDownloads[key] == true
      Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
          Text(attempt.track.title, style = MaterialTheme.typography.titleSmall)
          Text(attempt.track.id.sourceId.value, style = MaterialTheme.typography.bodySmall)
          Text(failure ?: if (running) "下载中或等待下载" else "正在完成任务",
              color = if (failure != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (running) TextButton(onClick = { providerDownloads.cancel(attempt.track.id) }) { Text("取消下载") }
              else if (failure != null) {
                  TextButton(onClick = { providerDownloads.retry(attempt.track.id) }) { Text("重试") }
                  TextButton(onClick = { providerDownloads.dismissError(attempt.track.id) }) { Text("移除失败记录") }
              }
          }
      }
  }
  if (active.isNotEmpty()) {
      item {
          DownloadNavigationCard(
              title = stringResource(R.string.library_downloading),
              subtitle = "${formatDownloadSpeed(downloads.aggregateDownloadBytesPerSecond)} · 剩余 ${active.size} 首未完成",
              onClick = { page = MeloXDownloadsPage.Active },
          )
      }
  }
  if (completed.isNotEmpty()) {
      item {
          Row(
              Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
              Text(stringResource(R.string.library_downloaded), fontSize = 20.sp, fontWeight = FontWeight.Bold)
              Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                  if (selecting) {
                      Text(
                          if (selectedIds.size == completed.size) stringResource(R.string.library_deselect_all) else stringResource(R.string.library_select_all),
                          color = MaterialTheme.colorScheme.primary,
                          fontWeight = FontWeight.SemiBold,
                          modifier = Modifier.clickable {
                              selectedIds = if (selectedIds.size == completed.size) emptySet()
                                  else completed.map { it.song.id }.toSet()
                          },
                      )
                      Text(
                          stringResource(R.string.action_cancel),
                          color = MaterialTheme.colorScheme.primary,
                          fontWeight = FontWeight.SemiBold,
                          modifier = Modifier.clickable {
                              selecting = false
                              selectedIds = emptySet()
                          },
                      )
                  } else {
                      Text(
                          stringResource(R.string.library_select),
                          color = MaterialTheme.colorScheme.primary,
                          fontWeight = FontWeight.SemiBold,
                          modifier = Modifier.clickable { selecting = true },
                      )
                      Text(
                          stringResource(R.string.library_play_all),
                          color = MaterialTheme.colorScheme.primary,
                          fontWeight = FontWeight.SemiBold,
                          modifier = Modifier.clickable {
                              PlaybackCommands.playAll(context, downloads.downloadedSongs)
                          },
                      )
                  }
              }
          }
      }
      item {
          Row(
              Modifier.fillMaxWidth().padding(bottom = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
              MeloXLocalBrowseMode.entries.forEach { mode ->
                  Text(
                      mode.title,
                      color = if (browseMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                      fontWeight = if (browseMode == mode) FontWeight.Bold else FontWeight.Medium,
                      modifier = Modifier
                          .clip(RoundedCornerShape(14.dp))
                          .background(MaterialTheme.colorScheme.onBackground.copy(alpha = if (browseMode == mode) .10f else .04f))
                          .clickable {
                              browseMode = mode
                              browseGroup = null
                              selectedIds = emptySet()
                          }
                          .padding(horizontal = 12.dp, vertical = 7.dp),
                  )
              }
          }
      }
      exportMessage?.let { value ->
          item { Text(value, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)) }
      }
  if (groups.isNotEmpty()) {
      item {
          DownloadNavigationCard(
              title = stringResource(R.string.library_downloaded_playlists),
              subtitle = "${groups.size} 个歌单",
              onClick = { page = MeloXDownloadsPage.Playlists },
          )
      }
  }
      if (browseMode != MeloXLocalBrowseMode.Songs && browseGroup == null) {
          items(browseGroups.entries.toList(), key = { "browse-${browseMode.name}-${it.key}" }) { group ->
              DownloadNavigationCard(
                  title = group.key,
                  subtitle = "${group.value.size} 首歌曲",
                  onClick = { browseGroup = group.key },
              )
          }
      } else if (browseMode != MeloXLocalBrowseMode.Songs) {
          item { DownloadsSubpageHeader(browseGroup.orEmpty()) { browseGroup = null } }
      }
      items(visibleCompleted, key = { "download-${it.song.id}" }) { item ->
          val checked = item.song.id in selectedIds
          Row(
              Modifier
                  .fillMaxWidth()
                  .height(62.dp)
                  .clickable {
                      if (selecting) {
                          selectedIds = if (checked) selectedIds - item.song.id else selectedIds + item.song.id
                      } else {
                          PlaybackCommands.playQueue(context, downloads.downloadedSongs, item.song.id)
                      }
                  },
              verticalAlignment = Alignment.CenterVertically,
          ) {
              AsyncImage(
                  model = downloads.localArtworkUri(item.song.id) ?: item.song.artworkUrl,
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)),
              )
              Column(Modifier.weight(1f).padding(start = 12.dp)) {
                  Text(item.song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                  Text(
                      "${item.song.artists} · ${item.quality.title}",
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f),
                      fontSize = 12.sp,
                  )
              }
              if (selecting) {
                  Text(if (checked) "✓" else "○", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, modifier = Modifier.padding(10.dp))
              } else {
                  Text(stringResource(R.string.provider_delete), color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { downloads.remove(item.song.id) }.padding(10.dp))
              }
          }
      }
      if (selecting) {
          item {
              val canDelete = selectedIds.isNotEmpty()
              Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  Box(
                      Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(18.dp))
                          .background(MaterialTheme.colorScheme.primary.copy(alpha = if (canDelete) .14f else .05f))
                          .clickable(enabled = canDelete) { exportSelected() },
                      contentAlignment = Alignment.Center,
                  ) {
                      Text(stringResource(R.string.library_export_selected), color = MaterialTheme.colorScheme.primary.copy(alpha = if (canDelete) 1f else .4f), fontWeight = FontWeight.SemiBold)
                  }
                  Box(
                      Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(18.dp))
                          .background(MaterialTheme.colorScheme.error.copy(alpha = if (canDelete) .14f else .05f))
                          .clickable(enabled = canDelete) {
                              downloads.removeMany(selectedIds)
                              selectedIds = emptySet()
                              selecting = false
                          },
                      contentAlignment = Alignment.Center,
                  ) {
                      Text(
                          if (canDelete) "删除 ${selectedIds.size} 首" else stringResource(R.string.library_select_songs),
                          color = MaterialTheme.colorScheme.error.copy(alpha = if (canDelete) 1f else .4f),
                          fontWeight = FontWeight.SemiBold,
                      )
                  }
              }
          }
      }
  }

  if (providerCompleted.isNotEmpty()) {
      item {
          Text("Spotify / YouTube Music", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
      }
      items(providerCompleted, key = { "provider-download-${it.track.id.sourceId.value}:${it.track.id.value}" }) { item ->
          Row(
              Modifier.fillMaxWidth().height(62.dp).clickable {
                  ProviderPlaybackCommands.playQueue(context, providerCompleted.map { it.track }, item.track.id)
              },
              verticalAlignment = Alignment.CenterVertically,
          ) {
              AsyncImage(
                  model = item.track.artworkUrl,
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)),
              )
              Column(Modifier.weight(1f).padding(start = 12.dp)) {
                  Text(item.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                  Text(
                      "${item.track.artistText} · ${item.track.id.sourceId.displayName}",
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f),
                      fontSize = 12.sp,
                  )
              }
              Text(stringResource(R.string.provider_delete), color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { providerDownloads.remove(item.track.id) }.padding(10.dp))
          }
      }
  }

  if (active.isEmpty() && completed.isEmpty() && providerCompleted.isEmpty() && providerAttempts.isEmpty()) {
      item {
          Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text(stringResource(R.string.library_none_downloaded), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                  Text(
                      stringResource(R.string.library_download_hint),
                      modifier = Modifier.padding(top = 7.dp),
                      color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f),
                      fontSize = 13.sp,
                  )
              }
          }
      }
  }
        }

    MeloXInterruptiblePage(
        visible = page != MeloXDownloadsPage.Root,
        onBack = {
            page = if (page == MeloXDownloadsPage.PlaylistDetail) MeloXDownloadsPage.Playlists else MeloXDownloadsPage.Root
        },
    ) {
      Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
       when (page) {
        MeloXDownloadsPage.Root -> Unit
        MeloXDownloadsPage.Active -> LazyColumn(
  modifier = Modifier.fillMaxSize(),
  contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 146.dp),
  verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
  item { DownloadsSubpageHeader(stringResource(R.string.library_downloading)) { page = MeloXDownloadsPage.Root } }
  item {
      Text(
          "${formatDownloadSpeed(downloads.aggregateDownloadBytesPerSecond)} · 剩余 ${active.size} 首未完成",
          color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
          fontSize = 13.sp,
          modifier = Modifier.padding(bottom = 10.dp),
      )
  }
  items(active, key = { "active-${it.song.id}" }) { item ->
      Row(Modifier.fillMaxWidth().height(66.dp), verticalAlignment = Alignment.CenterVertically) {
          AsyncImage(
              model = item.song.artworkUrl,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)),
          )
          Column(Modifier.weight(1f).padding(start = 12.dp)) {
              Text(item.song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
              val progress = item.fractionCompleted?.let { "${(it * 100).toInt()}%" } ?: stringResource(R.string.library_preparing)
              Text(
                  "$progress · ${formatDownloadSpeed(item.bytesPerSecond)} · ${item.quality.title}",
                  color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f),
                  fontSize = 12.sp,
              )
          }
          Text(stringResource(R.string.action_pause), color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { downloads.pause(item.song.id) }.padding(10.dp))
          Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { downloads.cancel(item.song.id) }.padding(10.dp))
      }
  }
  if (paused.isNotEmpty()) {
      item {
          Text(
              stringResource(R.string.library_paused),
              fontSize = 15.sp,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
          )
      }
      items(paused, key = { "paused-${it.song.id}" }) { item ->
          Row(Modifier.fillMaxWidth().height(66.dp), verticalAlignment = Alignment.CenterVertically) {
              AsyncImage(
                  model = item.song.artworkUrl,
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)),
              )
              Column(Modifier.weight(1f).padding(start = 12.dp)) {
                  Text(item.song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                  val progress = item.fractionCompleted?.let { "${(it * 100).toInt()}%" } ?: stringResource(R.string.library_preparing)
                  Text(
                      "$progress · ${item.quality.title}",
                      color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f),
                      fontSize = 12.sp,
                  )
              }
              Text(stringResource(R.string.action_resume), color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { downloads.resume(item.song.id) }.padding(10.dp))
              Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { downloads.cancel(item.song.id) }.padding(10.dp))
          }
      }
  }
  if (active.isEmpty() && paused.isEmpty()) {
      item { Text(stringResource(R.string.library_none_active), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f), modifier = Modifier.padding(top = 24.dp)) }
  }
        }

        MeloXDownloadsPage.Playlists -> LazyColumn(
  modifier = Modifier.fillMaxSize(),
  contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 146.dp),
  verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
  item { DownloadsSubpageHeader(stringResource(R.string.library_downloaded_playlists)) { page = MeloXDownloadsPage.Root } }
  items(groups, key = { "download-playlist-${it.playlist.id}" }) { group ->
      Row(
          Modifier.fillMaxWidth().height(68.dp).clickable {
              selectedPlaylistId = group.playlist.id
              page = MeloXDownloadsPage.PlaylistDetail
          },
          verticalAlignment = Alignment.CenterVertically,
      ) {
          AsyncImage(
              model = downloads.localPlaylistArtworkUri(group.playlist.id) ?: group.playlist.artworkUrl,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
          )
          Column(Modifier.weight(1f).padding(start = 12.dp)) {
              Text(group.playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
              Text("已下载 ${group.songs.size} 首", color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f), fontSize = 12.sp)
          }
          MeloXActionIcon("›", Modifier.size(18.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .4f))
      }
  }
        }

        MeloXDownloadsPage.PlaylistDetail -> {
  val group = groups.firstOrNull { it.playlist.id == selectedPlaylistId }
  val songs = group?.songs?.map { it.song }.orEmpty()
  LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 146.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
      item { DownloadsSubpageHeader(group?.playlist?.name ?: stringResource(R.string.library_downloaded_playlists)) { page = MeloXDownloadsPage.Playlists } }
      group?.let { existing ->
          item {
              Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                  AsyncImage(
                      model = downloads.localPlaylistArtworkUri(existing.playlist.id) ?: existing.playlist.artworkUrl,
                      contentDescription = null,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                  )
                  Column(Modifier.weight(1f).padding(start = 12.dp)) {
                      Text(existing.playlist.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      Text("${songs.size} 首已下载歌曲", color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f), fontSize = 12.sp)
                  }
                  Text(
                      stringResource(R.string.library_play_all),
                      color = MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.SemiBold,
                      modifier = Modifier.clickable {
                          PlaybackCommands.playAll(context, songs)
                      }.padding(8.dp),
                  )
              }
          }
      }
      items(group?.songs.orEmpty(), key = { "playlist-song-${it.song.id}" }) { item ->
          Row(
              Modifier.fillMaxWidth().height(62.dp).clickable {
                  PlaybackCommands.playQueue(context, songs, item.song.id)
              },
              verticalAlignment = Alignment.CenterVertically,
          ) {
              AsyncImage(
                  model = downloads.localArtworkUri(item.song.id) ?: item.song.artworkUrl,
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)),
              )
              Column(Modifier.weight(1f).padding(start = 12.dp)) {
                  Text(item.song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                  Text(item.song.artists, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f), fontSize = 12.sp)
              }
          }
      }
  }
        }
       }
      }
    }
}

@Composable
private fun DownloadNavigationCard(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).height(66.dp)
  .meloXContentSurface(
      shape = MeloXShapes.card,
      surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .055f),
  )
  .clickable(onClick = onClick)
  .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
  Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
  Text(subtitle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        MeloXActionIcon("›", Modifier.size(18.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .42f))
    }
}

@Composable
private fun DownloadsSubpageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            MeloXActionIcon("‹", Modifier.size(20.dp), MaterialTheme.colorScheme.onBackground)
        }
        Text(title, style = MeloXTypography.title2)
    }
}

private fun formatDownloadSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1024L * 1024L -> "%.1f MB/s".format(bytesPerSecond / (1024.0 * 1024.0))
    bytesPerSecond >= 1024L -> "%.0f KB/s".format(bytesPerSecond / 1024.0)
    bytesPerSecond > 0L -> "$bytesPerSecond B/s"
    else -> "0 KB/s"
}

@Composable
private fun MeloXLibraryLoginUnavailable(
    onLogin: (() -> Unit)?,
    source: MusicSource,
    forcedPageName: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // ⚠ 稳定值：可见式 `statusBarsPadding()` 在沉浸切换时会 0↔真实高度跳。
            .padding(top = rememberMeloXStableStatusBarHeight())
            .padding(horizontal = 20.dp),
    ) {
        MeloXIosTopBar(
            title = stringResource(R.string.tab_library),
            contentPadding = PaddingValues(horizontal = 0.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.library_login_required), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (source == MusicSource.Netease) {
                        stringResource(R.string.library_login_netease_body)
                    } else {
                        "请先在设置中登录 ${source.displayName}，登录后可读取该平台提供的音乐库内容。"
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.50f),
                    textAlign = TextAlign.Center,
                )
                if (onLogin != null) {
                    MeloXGlassButton(
                        onClick = onLogin,
                        modifier = Modifier.padding(top = 18.dp),
                        style = MeloXGlassButtonStyle.BorderedProminent,
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Text(
                            if (source == MusicSource.Netease) stringResource(R.string.app_login_netease) else "前往登录 ${source.displayName}",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MeloXLibrarySegmentedPicker(
    selected: MeloXLibraryPage,
    onSelected: (MeloXLibraryPage) -> Unit,
    source: MusicSource,
    forcedPageName: String? = null,
    modifier: Modifier = Modifier,
) {
    val pickerContext = LocalContext.current.applicationContext
    val pageProvider = remember(source, pickerContext) {
        if (source == MusicSource.Netease) {
            null
        } else {
            runCatching { MeloXMusicProviders.create(pickerContext).require(source) }.getOrNull()
        }
    }
    val pages = MeloXLibraryPage.entries.filter { it.isEnabled(source, pageProvider) || it.name == forcedPageName }
    val panelShape = MeloXShapes.compact
    val lensShape = RoundedCornerShape(15.dp)
    val panelBackdrop = rememberLayerBackdrop()
    val dark = isMeloXDarkTheme()
    val selectedIndex = pages.indexOf(selected).coerceAtLeast(0)
    val lensPosition by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 1f,
            stiffness = 460f,
            visibilityThreshold = 0.001f,
        ),
        label = "library-segment-lens-position",
    )
    val panelTint = MaterialTheme.colorScheme.surface.copy(alpha = 0.10f)
    val panelSurface = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.055f)
    val selectionTint = if (dark) {
        Color.White.copy(alpha = 0.22f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .meloXLiquidBottomBar(
                shape = panelShape,
                tint = panelTint,
                surfaceColor = panelSurface,
            ),
    ) {
        val panelWidthPx = constraints.maxWidth

        Row(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0f)
                .layerBackdrop(panelBackdrop)
                .meloXLiquidBottomBar(
                    shape = panelShape,
                    tint = panelTint,
                    surfaceColor = panelSurface,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.forEach { page ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(page.titleRes), fontSize = 13.sp)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(1f / pages.size)
                .fillMaxHeight()
                .offset {
                    IntOffset(
                        x = (lensPosition * panelWidthPx / pages.size).roundToInt(),
                        y = 0,
                    )
                }
                .padding(horizontal = 1.dp, vertical = 1.dp)
                .meloXLiquidTabSelection(
                    shape = lensShape,
                    selected = true,
                    tint = selectionTint,
                    panelBackdrop = panelBackdrop,
                ),
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.forEach { page ->
                val isSelected = page == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelected(page) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(page.titleRes),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeloXLibrarySongsPage(
    songs: List<SearchSong>,
    onPlay: (SearchSong) -> Unit,
    onPlayAll: () -> Unit,
    onHeartMode: (() -> Unit)? = null,
) {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.library_no_songs),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                fontSize = 17.sp,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MeloXBottomContentClearance),
    ) {
        item {
            MeloXPlayAllRow(onPlayAll)
            onHeartMode?.let { action ->
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clickable(onClick = action).padding(start = 20.dp, end = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    MeloXActionIcon("♥", Modifier.size(22.dp), Color(0xFFFF3B30))
                    Text("心动模式", fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            MeloXInsetDivider(leading = 68.dp)
        }
        items(songs, key = { it.id }) { song ->
            MeloXLibraryTrackRow(song = song, onClick = { onPlay(song) })
            MeloXInsetDivider(leading = 68.dp)
        }
    }
}

@Composable
private fun MeloXPlayAllRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MeloXPlayGlyph(
            modifier = Modifier.size(28.dp),
            color = Color(0xFFFF3147),
        )
        Text(
            text = stringResource(R.string.library_play_all),
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun MeloXLibraryTrackRow(
    song: SearchSong,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = song.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = song.name,
                fontSize = 17.sp,
                lineHeight = 21.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = song.artists,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.46f),
            )
        }
        if (song.durationMs > 0L) {
            Text(
                text = formatDuration(song.durationMs),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.46f),
            )
        }
    }
}

private data class MeloXLibraryCoverRow(
    val key: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
)

@Composable
private fun MeloXLibraryCoverListPage(
    rows: List<MeloXLibraryCoverRow>,
    @androidx.annotation.StringRes emptyRes: Int,
    onClick: (Int) -> Unit,
) {
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(emptyRes),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                fontSize = 17.sp,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MeloXBottomContentClearance),
    ) {
        items(rows.size, key = { index -> "$index:${rows[index].key}" }) { index ->
            val row = rows[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clickable { onClick(index) }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = row.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(
                        row.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (row.subtitle.isNotBlank()) {
                        Text(
                            row.subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.46f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MeloXProviderArtistDetailScreen(
    artist: MusicArtistSummary,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val capability = remember(artist.id.sourceId) {
        MeloXMusicProviders.create(context)[artist.id.sourceId] as? ArtistCapability
    }
    var songs by remember(artist.id) { mutableStateOf<List<SearchSong>>(emptyList()) }
    var loading by remember(artist.id) { mutableStateOf(true) }
    var errorMessage by remember(artist.id) { mutableStateOf<String?>(null) }
    var refreshVersion by remember(artist.id) { mutableStateOf(0) }
    LaunchedEffect(artist.id, refreshVersion) {
        loading = true
        val reader = capability
        if (reader == null) {
            loading = false
            errorMessage = context.getString(R.string.library_capability_missing, artist.id.sourceId.displayName)
            return@LaunchedEffect
        }
        runCatching {
            withContext(Dispatchers.IO) {
                reader.loadAllArtistTracks(artist).tracks.map(MeloXLegacyUiBridge::track)
            }
        }.onSuccess {
            songs = it
            errorMessage = null
        }.onFailure { failure ->
            errorMessage = failure.message
        }
        loading = false
    }
    if (!loading && errorMessage == null) {
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = true) {
                MeloXUnifiedSongListDetailScreen(
                    playlist = NeteasePlaylistSummary(
                        id = artist.id.hashCode().toLong(),
                        name = artist.name,
                        coverUrl = artist.artworkUrl,
                        trackCount = songs.size,
                        creatorName = artist.id.sourceId.displayName,
                        description = artist.description,
                    ),
                    songs = songs,
                    onBack = onBack,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    source = artist.id.sourceId.builtIn,
                    onRefresh = { refreshVersion++ },
                )
            }
        }
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Text("‹", fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                artist.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f))
                Text(stringResource(R.string.account_retry), Modifier.clickable { refreshVersion++ }.padding(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MeloXLibraryPlaylistsPage(
    playlists: List<NeteasePlaylistSummary>,
    localRecommendations: List<com.lladlam.melox.core.music.model.MusicTrack> = emptyList(),
    onLocalRecommendationsClick: () -> Unit = {},
    onPlaylistClick: (NeteasePlaylistSummary) -> Unit,
    // ★ 收藏的歌单无法删除（2026-10-06 用户报障）：原先本列表行的删除/取消订阅入口
    //   **完全缺失**（只有 clickable 进详情页），而详情页的「更多」浮层里才有取消订阅
    //   ⇒ 用户在收藏列表里根本无从删起。现在给每行加左滑删除，与歌曲行的滑动删除
    //   同一套交互（MeloXSwipeActionRow + Trash），由调用方真正调 API 并刷新列表。
    onRemovePlaylist: (NeteasePlaylistSummary) -> Unit = {},
    // ★ 系统歌单「我喜欢的音乐」的 id：网易云不允许删除它，该行不给左滑删除入口。
    likedPlaylistId: Long? = null,
    listState: LazyListState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    if (playlists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.library_no_playlists),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                fontSize = 17.sp,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        // 触顶/触底橡胶回弹触感。
        modifier = Modifier.fillMaxSize().meloXEdgeOverscrollHaptic(listState),
        contentPadding = PaddingValues(bottom = MeloXBottomContentClearance),
    ) {
        if (localRecommendations.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(66.dp).clickable(onClick = onLocalRecommendationsClick).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AsyncImage(localRecommendations.firstOrNull()?.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_for_you_melox), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("本地算法 · ${localRecommendations.size} 首 · 只读", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f))
                    }
                    MeloXActionIcon("›", Modifier.size(18.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .4f))
                }
                MeloXInsetDivider(leading = 74.dp)
            }
        }
        item {
            Text(
                text = stringResource(R.string.library_page_playlists),
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.50f),
            )
        }
        items(playlists, key = { it.id }) { playlist ->
            // ★ 左滑露出「删除歌单」（与歌曲行的滑动删除同一套交互）。
            //   收藏列表原本没有任何删除入口，这是「收藏的歌单无法删除」的根因。
            //   系统歌单「我喜欢的音乐」不给入口 —— 网易云不允许删除/取关它，
            //   给了也是点了没反应（2026-10-07）。
            val removable = !playlist.isMeloXLocal && playlist.id != likedPlaylistId
            MeloXSwipeActionRow(
                startActions = emptyList(),
                endActions = if (removable) {
                    listOf(
                        MeloXSwipeAction(
                            label = "删除歌单",
                            symbol = MeloXSymbol.Trash,
                            color = Color(0xFFFF3B30),
                            onInvoke = { onRemovePlaylist(playlist) },
                        ),
                    )
                } else {
                    emptyList()
                },
                onClick = { onPlaylistClick(playlist) },
                onLongClick = null,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(66.dp)
                        .padding(start = 18.dp, end = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val sharedArtworkModifier = with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState(
                                key = playlistArtworkSharedKey(playlist.id),
                            ),
                            animatedVisibilityScope = animatedVisibilityScope,
                            // ⚠ 必须显式传：默认是渐近弹簧，会「追动靶」并在整页滑入结束的瞬间被硬切
                            //   补齐 —— 用户报的「最后一帧横移调整位置」。见 [MeloXPageBoundsTransform]。
                            boundsTransform = MeloXPageBoundsTransform,
                            // The cover itself must stay in the shared overlay for
                            // the row -> detail flight. The detail screen clips
                            // its settled cover; only the transition uses this
                            // elevated layer.
                            renderInOverlayDuringTransition = true,
                            zIndexInOverlay = 1f,
                        )
                    }

                    AsyncImage(
                        model = playlist.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = sharedArtworkModifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            playlist.name,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${playlist.trackCount} 首歌曲",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                        )
                    }
                    MeloXActionIcon(
                        "›",
                        Modifier.size(18.dp),
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.40f),
                    )
                }
            }
            MeloXInsetDivider(leading = 84.dp)
        }
    }
}

@Composable
private fun LocalRecommendationPlaylistScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    // readRecommendedTracks() 是 JSON 反序列化重活，别直接写在组合体里
    // （每次重组都会跑，且在主线程）→ 用 produceState 异步加载。
    val tracks by produceState(initialValue = emptyList<MusicTrack>(), context) {
        value = withContext(Dispatchers.IO) {
            runCatching { LocalRecommendationStore.readRecommendedTracks(context) }.getOrDefault(emptyList())
        }
    }
    // ⚠ 稳定值：可见式 `statusBarsPadding()` 在沉浸切换时会 0↔真实高度跳。
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = rememberMeloXStableStatusBarHeight()),
    ) {
        MeloXPlaylistToolbar(
            foreground = MaterialTheme.colorScheme.onBackground,
            onBack = onBack,
            onShare = {},
            showMore = false,
            onMore = {},
        )
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(stringResource(R.string.home_for_you_melox), fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("本地规则与轻量模型生成 · 只读内部歌单 · ${tracks.size} 首", modifier = Modifier.padding(top = 5.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .54f))
        }
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无达到相似度阈值的推荐歌曲\n请继续播放、收藏或完成歌曲后重新分析", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
            }
            return
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = MeloXBottomContentClearance)) {
            itemsIndexed(tracks, key = { _, track -> "local-recommendation-${track.id.sourceId.value}-${track.id.value}" }) { index, track ->
                Row(Modifier.fillMaxWidth().clickable { ProviderPlaybackCommands.playQueue(context, tracks, track.id) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", Modifier.width(38.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .45f), textAlign = TextAlign.Center)
                    AsyncImage(track.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text("${track.artistText} · ${track.id.sourceId.displayName}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MeloXInsetDivider(leading: androidx.compose.ui.unit.Dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = leading, end = 18.dp),
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
    )
}

/** Canonical playlist detail used by Library, Home, Explore, Search and account entry points. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MeloXUnifiedSongListDetailScreen(
    playlist: NeteasePlaylistSummary,
    songs: List<SearchSong>,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    artworkSharedKey: String? = null,
    source: MusicSource? = null,
    onRefresh: (() -> Unit)? = null,
) {
    val context = LocalContext.current.applicationContext
    val client = remember(context) {
        NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
    }
    val content: @Composable (SharedTransitionScope, AnimatedVisibilityScope) -> Unit = { shared, visibility ->
      MeloXPlaylistDetailScreen(
        initialPlaylist = playlist,
        client = client,
        onBack = onBack,
        sharedTransitionScope = shared,
        animatedVisibilityScope = visibility,
        onModalVisibilityChanged = {},
        providedSongs = songs,
        providedSource = source,
        onRefreshProvidedSongs = onRefresh,
        artworkSharedKey = artworkSharedKey,
    )
    }
    if (!LocalMeloXListDetail.current && sharedTransitionScope != null && animatedVisibilityScope != null) {
        content(sharedTransitionScope, animatedVisibilityScope)
    } else {
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = true) { content(this@SharedTransitionLayout, this) }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MeloXUnifiedPlaylistDetailScreen(
    playlist: NeteasePlaylistSummary,
    onBack: () -> Unit,
    onModalVisibilityChanged: (Boolean) -> Unit = {},
    // 入口页（首页/发现页/搜索）若要把列表卡片封面一镜到底地接到详情 hero，
    // 就把自己顶层 SharedTransitionLayout 的作用域与配对 key 传进来；
    // 不传则保持「自建作用域 + 淡入」的原有行为。
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    artworkSharedKey: String? = null,
) {
    val context = LocalContext.current.applicationContext
    val client = remember(context) {    NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
    }
    if (!LocalMeloXListDetail.current && sharedTransitionScope != null && animatedVisibilityScope != null) {
        MeloXPlaylistDetailScreen(
            initialPlaylist = playlist,
            client = client,
            onBack = onBack,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            onModalVisibilityChanged = onModalVisibilityChanged,
            artworkSharedKey = artworkSharedKey,
        )
    } else {
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = true) {
                MeloXPlaylistDetailScreen(
                    initialPlaylist = playlist,
                    client = client,
                    onBack = onBack,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    onModalVisibilityChanged = onModalVisibilityChanged,
                    artworkSharedKey = artworkSharedKey,
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MeloXUnifiedAlbumDetailScreen(
    albumId: Long,
    onBack: () -> Unit,
    onModalVisibilityChanged: (Boolean) -> Unit = {},
) {
    val placeholder = remember(albumId) {
        NeteasePlaylistSummary(
            id = albumId,
            name = "专辑",
            coverUrl = null,
            trackCount = 0,
            creatorName = "网易云音乐",
        )
    }
    val context = LocalContext.current.applicationContext
    val client = remember(context) {
        NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
    }
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = true) {
            MeloXPlaylistDetailScreen(
                initialPlaylist = placeholder,
                client = client,
                onBack = onBack,
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this,
                onModalVisibilityChanged = onModalVisibilityChanged,
                albumId = albumId,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MeloXUnifiedProviderAlbumDetailScreen(
    album: MusicAlbumSummary,
    onBack: () -> Unit,
    // 与 MeloXUnifiedPlaylistDetailScreen 同款：入口页（搜索页等）若要把结果卡片
    // 封面一镜到底接到详情 hero，就把自己顶层的 SharedTransitionLayout 作用域与
    // 配对 key 传进来；不传则维持「自建作用域 + 淡入」。
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    artworkSharedKey: String? = null,
) {
    val legacyId = remember(album.id) {
        album.id.toString().hashCode().toLong().let { value -> if (value >= 0L) -value - 1L else value }
    }
    val placeholder = remember(album, legacyId) {
        NeteasePlaylistSummary(
            id = legacyId,
            name = album.title,
            coverUrl = album.artworkUrl,
            trackCount = album.trackCount?.toInt() ?: 0,
            creatorName = album.artists.joinToString(" / ") { it.name },
        )
    }
    val context = LocalContext.current.applicationContext
    val client = remember(context) {
        NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
    }
    if (!LocalMeloXListDetail.current && sharedTransitionScope != null && animatedVisibilityScope != null) {
        MeloXPlaylistDetailScreen(
            initialPlaylist = placeholder,
            client = client,
            onBack = onBack,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            onModalVisibilityChanged = {},
            providerAlbum = album,
            artworkSharedKey = artworkSharedKey,
        )
    } else {
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = true) {
                MeloXPlaylistDetailScreen(
                    initialPlaylist = placeholder,
                    client = client,
                    onBack = onBack,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    onModalVisibilityChanged = {},
                    providerAlbum = album,
                    artworkSharedKey = artworkSharedKey,
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MeloXPlaylistDetailScreen(
    initialPlaylist: NeteasePlaylistSummary,
    client: NeteaseLibraryClient,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onModalVisibilityChanged: (Boolean) -> Unit,
    onSongLikeChanged: (SearchSong, Boolean) -> Unit = { _, _ -> },
    // ★ 取消收藏成功后回调，让资料库列表刷新（该歌单应从收藏列表消失）。
    onLibraryChanged: () -> Unit = {},
    albumId: Long? = null,
    providerAlbum: MusicAlbumSummary? = null,
    providedSongs: List<SearchSong>? = null,
    providedSource: MusicSource? = null,
    onRefreshProvidedSongs: (() -> Unit)? = null,
    /**
     * 网易云系统歌单「我喜欢的音乐」的 id（来自资料库快照 `likedPlaylistId`）。
     * 它既不能删除也不能取消订阅，详情页据此隐藏收藏按钮。（2026-10-07）
     */
    likedPlaylistId: Long? = null,
    // 由入口页（首页/发现页/搜索）指定的共享元素 key，用于把列表卡片
    // 的封面一镜到底地 morph 到详情 hero。null 时退回本页默认 key。
    artworkSharedKey: String? = null,
) {
    val context = LocalContext.current
    val detailWindow = rememberMeloXWindowInfo()
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val cache = remember(appContext) { NeteaseLibraryCache(appContext) }
    val accountClient = remember(appContext) {
        NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) })
    }
    val operationsClient = remember(appContext) {
        NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) })
    }
    val albumClient = remember(appContext) {
        NeteaseCollectionDetailsClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) })
    }
    val providerAlbumCapability = remember(providerAlbum?.id?.sourceId, appContext) {
        providerAlbum?.let { backing ->
            MeloXMusicProviders.create(appContext)[backing.id.sourceId] as? AlbumCapability
        }
    }
    val providerPlaylist = initialPlaylist.providerPlaylist
    val providerSyncCandidate = remember(providerPlaylist?.id?.sourceId, appContext) {
        providerPlaylist?.let { backing ->
            MeloXMusicProviders.create(appContext)[backing.id.sourceId] as? PlaylistSyncCapability
        }
    }
    val providerPlaylistCapability = remember(providerPlaylist?.id?.sourceId, appContext) {
        providerPlaylist?.let { backing ->
            MeloXMusicProviders.create(appContext)[backing.id.sourceId] as? PlaylistCapability
        }
    }
    val isProviderPlaylist = providerPlaylist != null
    val isAlbum = albumId != null || providerAlbum != null
    val collectionSource = providerPlaylist?.id?.sourceId?.builtIn ?: providerAlbum?.id?.sourceId?.builtIn ?: providedSource
        ?: providedSongs?.firstOrNull()?.providerTrack?.id?.sourceId?.builtIn
    val collectionSourceId = providerPlaylist?.id?.sourceId ?: providerAlbum?.id?.sourceId ?:
        providedSource?.let(com.lladlam.melox.core.music.model.SourceId::fromBuiltIn)
        ?: providedSongs?.firstOrNull()?.providerTrack?.id?.sourceId
    val isProviderCollection = isProviderPlaylist || providerAlbum != null || collectionSource != null
    val canDownloadCollection = remember(collectionSourceId) {
        collectionSourceId == null || MeloXMusicProviders.create(appContext)[collectionSourceId]?.let {
            it is DownloadCapability && (collectionSourceId.builtIn != null || it.supports(com.lladlam.melox.core.music.provider.MusicCapability.Download))
        } == true
    }
    var detail by remember(initialPlaylist.id) { mutableStateOf<NeteasePlaylistDetail?>(null) }
    val providerSync = providerSyncCandidate.takeUnless {
        (detail?.summary?.providerPlaylist ?: providerPlaylist)?.isEditable == false
    }
    var loading by remember(initialPlaylist.id) { mutableStateOf(true) }
    val pagedBilibiliPlaylist = providerPlaylist?.id?.sourceId?.builtIn == MusicSource.Bilibili && providedSongs == null
    var providerPage by remember(initialPlaylist.id) { mutableStateOf(1) }
    var providerHasMore by remember(initialPlaylist.id) { mutableStateOf(false) }
    var providerLoadingMore by remember(initialPlaylist.id) { mutableStateOf(false) }
    var providerPageError by remember(initialPlaylist.id) { mutableStateOf<String?>(null) }
    var providerLoadGeneration by remember(initialPlaylist.id) { mutableStateOf(0) }
    var errorMessage by remember(initialPlaylist.id) { mutableStateOf<String?>(null) }
    var searchQuery by remember(initialPlaylist.id) { mutableStateOf("") }
    var showPlaylistActions by remember(initialPlaylist.id) { mutableStateOf(false) }
    var showBatchDownload by remember(initialPlaylist.id) { mutableStateOf(false) }
    var selectedTrackAction by remember(initialPlaylist.id) { mutableStateOf<SearchSong?>(null) }
    var showProviderRename by remember(initialPlaylist.id) { mutableStateOf(false) }
    var providerSyncTrack by remember(initialPlaylist.id) { mutableStateOf<SearchSong?>(null) }
    var providerSyncBusy by remember(initialPlaylist.id) { mutableStateOf(false) }
    var providerSyncError by remember(initialPlaylist.id) { mutableStateOf<String?>(null) }
    var isSaved by remember(initialPlaylist.id) { mutableStateOf<Boolean?>(null) }
    // ★ 自创歌单标记（2026-10-09）：由 userPlaylists 查询结果确定，不依赖
    //   displayed.creatorUserId —— 从列表/首页进入详情时该字段常为 null，
    //   导致自创歌单取消收藏被误判成「退订别人的歌单」，走了无效的
    //   setPlaylistSubscribed(false) ⇒ 「收藏生效了，取消删除却不行」。
    var isOwnedPlaylist by remember(initialPlaylist.id) { mutableStateOf<Boolean?>(null) }
    var currentUserId by remember(initialPlaylist.id) { mutableStateOf<Long?>(null) }
    var savingPlaylist by remember(initialPlaylist.id) { mutableStateOf(false) }
    var palette by remember(initialPlaylist.coverUrl) { mutableStateOf(MeloXDetailPalette.LightFallback) }
    var sortMode by remember(initialPlaylist.id) { mutableStateOf(MeloXPlaylistSortMode.Original) }

    DisposableEffect(showPlaylistActions, showBatchDownload, selectedTrackAction, showProviderRename, providerSyncTrack) {
        val visible = showProviderRename || providerSyncTrack != null ||
            showPlaylistActions || showBatchDownload || selectedTrackAction != null
        onModalVisibilityChanged(visible)
        onDispose {
            if (visible) onModalVisibilityChanged(false)
        }
    }

    suspend fun refreshSavedState() {
        if (isAlbum) return
        if (initialPlaylist.isMeloXLocal) {
            isSaved = null
            isOwnedPlaylist = false
            return
        }
        if (isProviderCollection) {
            isSaved = null
            return
        }
        // ⚠ 系统歌单「我喜欢的音乐」既不能删除也不能取消订阅 —— 网易云的
        //   `/api/playlist/subscribe` 与 `/api/playlist/delete` 对它都是无效操作，
        //   此前点了毫无反应。这里直接不给按钮（isSaved = null ⇒ showSaveAction = false）。
        if (likedPlaylistId != null && initialPlaylist.id == likedPlaylistId) {
            isSaved = null
            return
        }
        val cookie = NeteaseSessionStore.readCookie(appContext)
        if (!NeteaseSessionStore.containsMusicU(cookie)) {
            isSaved = null
            return
        }
        runCatching {
            val profile = accountClient.accountProfile(cookie)
            currentUserId = profile.userId
            val playlists = withContext(Dispatchers.IO) {
                client.userPlaylistsBlocking(profile.userId)
            }
            val saved = playlists.any { it.id == initialPlaylist.id }
            // ⚠ 收藏的别人歌单也会出现在 /api/user/playlist 返回里，
            //   不能只凭「在列表里」判自创 ⇒ 必须核对创建者就是当前用户（2026-10-09）。
            //   否则收藏的别人歌单取消收藏会误走 deletePlaylist ⇒ 网易云拒绝「无权操作歌单」。
            val owned = playlists.any { it.id == initialPlaylist.id && it.creatorUserId == profile.userId }
            saved to owned
        }.onSuccess { (saved, owned) ->
            isSaved = saved
            isOwnedPlaylist = owned
        }
    }

    suspend fun refreshPlaylist() {
        providerLoadGeneration++
        providerHasMore = false
        providerPageError = null
        loading = true
        errorMessage = null
        if (providedSongs != null) {
            detail = NeteasePlaylistDetail(summary = initialPlaylist, songs = providedSongs)
            // ⚠ 不在此清 isSaved（2026-10-06）：收藏态由**另一个** LaunchedEffect 的
            //   refreshSavedState() 异步查得。若这里写 isSaved = null，而 refreshPlaylist
            //   又后于它完成，就会把已查到的收藏态覆盖成 null ⇒ ✓ 按钮退回「＋」⇒
            //   再点是「再收藏一次」，收藏/取消收藏再也切不动（用户报的「再点一次勾应取消收藏」）。
            //   providedSongs 只提供歌曲列表，与收藏态无关，本来就不该碰它。
            loading = false
            return
        }
        if (providerAlbum != null) {
            val capability = providerAlbumCapability
            if (capability == null) {
                errorMessage = "${providerAlbum.id.sourceId.displayName} 当前不提供专辑详情能力"
                loading = false
                return
            }
            runCatching {
                withContext(Dispatchers.IO) { capability.loadAllAlbumTracks(providerAlbum) }
            }.onSuccess { album ->
                detail = NeteasePlaylistDetail(
                    summary = initialPlaylist.copy(
                        name = album.summary.title,
                        coverUrl = album.summary.artworkUrl,
                        trackCount = album.tracks.size,
                        creatorName = album.summary.artists.joinToString(" / ") { it.name },
                    ),
                    songs = album.tracks.map(MeloXLegacyUiBridge::track),
                )
            }.onFailure { errorMessage = it.message ?: "专辑加载失败" }
        } else if (albumId != null) {
            runCatching { albumClient.albumDetail(albumId) }
                .onSuccess { album ->
                    detail = NeteasePlaylistDetail(
                        summary = NeteasePlaylistSummary(
                            id = album.album.id,
                            name = album.album.name,
                            coverUrl = album.album.artworkUrl,
                            trackCount = album.songs.size,
                            creatorName = album.album.artistText,
                            description = album.description,
                        ),
                        songs = album.songs,
                    )
                    isSaved = album.subscribed
                }
                .onFailure { errorMessage = it.message ?: "专辑加载失败" }
        } else if (providerPlaylist != null) {
            val capability = providerPlaylistCapability
            if (capability == null) {
                errorMessage = "${providerPlaylist.id.sourceId.displayName} 当前不提供歌单详情能力"
                loading = false
                return
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    if (pagedBilibiliPlaylist) capability.playlistDetail(providerPlaylist, page = 1, pageSize = 20)
                    else capability.loadAllPlaylistTracks(providerPlaylist, pageSize = 200)
                }
            }.onSuccess { providerDetail ->
                detail = MeloXLegacyUiBridge.playlistDetail(providerDetail)
                if (pagedBilibiliPlaylist) {
                    providerPage = 1
                    providerHasMore = providerDetail.hasMore ?: (providerDetail.tracks.size.toLong() < (providerDetail.total ?: 0L))
                }
            }.onFailure { errorMessage = it.message ?: "歌单加载失败" }
        } else {
            runCatching { client.playlistDetail(initialPlaylist.id) }
                .onSuccess {
                    detail = it
                    cache.savePlaylistDetail(initialPlaylist.id, it)
                }
                .onFailure { errorMessage = it.message ?: "歌单加载失败" }
        }
        loading = false
    }

    suspend fun loadNextProviderPage() {
        val playlist = providerPlaylist ?: return
        val capability = providerPlaylistCapability ?: return
        if (!pagedBilibiliPlaylist || loading || providerLoadingMore || !providerHasMore) return
        val generation = providerLoadGeneration
        providerLoadingMore = true
        providerPageError = null
        try {
            val nextPage = providerPage + 1
            val next = withContext(Dispatchers.IO) { capability.playlistDetail(playlist, page = nextPage, pageSize = 20) }
            if (generation != providerLoadGeneration) return
            val mapped = MeloXLegacyUiBridge.playlistDetail(next)
            val merged = (detail?.songs.orEmpty() + mapped.songs).distinctBy { it.id }
            detail = mapped.copy(songs = merged)
            providerPage = nextPage
            providerHasMore = next.hasMore ?: next.tracks.isNotEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (generation == providerLoadGeneration) providerPageError = error.message ?: "下一页加载失败"
        } finally {
            providerLoadingMore = false
        }
    }

    LaunchedEffect(initialPlaylist.id, providerPlaylist?.id, providedSongs) {
        if (isAlbum || providerPlaylist != null) {
            refreshPlaylist()
        } else {
            cache.loadPlaylistDetail(initialPlaylist.id)?.let { detail = it }
            loading = detail == null
            if (NeteaseLibraryCache.beginPlaylistColdStartRefresh(initialPlaylist.id, cache.cacheDir)) {
                refreshPlaylist()
            }
        }
    }

    LaunchedEffect(initialPlaylist.id, isProviderPlaylist) {
        refreshSavedState()
    }

    val displayed = detail?.summary ?: initialPlaylist
    // 只有网易云本源歌单才可能走服务端「云端随机」；专辑/第三方源保留本地随机。
    val cloudPlaylistId = if (isAlbum || providerPlaylist != null) {
        null
    } else {
        displayed.id.takeIf { it > 0L }
    }
    val songs = detail?.songs.orEmpty()
    val ownedPlaylistId = displayed.id.takeIf {
        !isAlbum &&
        displayed.creatorUserId != null && displayed.creatorUserId == currentUserId
    }

    LaunchedEffect(displayed.coverUrl) {
        palette = MeloXDetailPaletteProvider.paletteFor(displayed.coverUrl)
    }

    val foreground = if (palette.prefersDarkAppearance) Color.White else Color.Black
    val secondary = foreground.copy(alpha = 0.48f)
    val orderedSongs = remember(songs, sortMode) {
        when (sortMode) {
            MeloXPlaylistSortMode.Original -> songs
            MeloXPlaylistSortMode.Title -> songs.sortedBy { it.name.lowercase() }
            MeloXPlaylistSortMode.Artist -> songs.sortedWith(compareBy({ it.artists.lowercase() }, { it.name.lowercase() }))
            MeloXPlaylistSortMode.Album -> songs.sortedWith(compareBy({ it.album.lowercase() }, { it.name.lowercase() }))
        }
    }
    val filteredSongs = remember(orderedSongs, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) orderedSongs else orderedSongs.filter { song ->
            song.name.lowercase().contains(query) ||
                song.artists.lowercase().contains(query) ||
                song.album.lowercase().contains(query)
        }
    }

    // ==========================================
    // 1. 下拉懸停搜索欄：核心物理阻力與動畫狀態
    // ==========================================
    val density = LocalDensity.current
    val searchBarMaxHeightPx = with(density) { 64.dp.toPx() }
    val searchOffset = remember { Animatable(0f) }

    val searchScrollConnection = remember(searchBarMaxHeightPx) {
        object : NestedScrollConnection {
            // 新增：追蹤手勢狀態，實現「硬停頓 (Hard Stop)」
            private var lastEventTime = 0L
            private var hasScrolledListInGesture = false

            private fun checkResetGesture() {
                val now = System.currentTimeMillis()
                // 如果距離上次接收到滑動事件超過 200 毫秒 (手指鬆開重新按下)，重置手勢狀態
                if (now - lastEventTime > 200L) {
                    hasScrolledListInGesture = false
                }
                lastEventTime = now
            }

            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                checkResetGesture()

                // 1. 手指上滑時：絕對優先收起搜索欄
                if (available.y < 0 && searchOffset.value > 0f) {
                    val currentOffset = searchOffset.value
                    val newVal = currentOffset + available.y // available.y 是負數

                    if (newVal >= 0f) {
                        scope.launch { searchOffset.snapTo(newVal) }
                        return Offset(0f, available.y)
                    } else {
                        scope.launch { searchOffset.snapTo(0f) }
                        return Offset(0f, -currentOffset)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                checkResetGesture()

                // 核心攔截邏輯：只要這個手勢曾經讓列表滾動過 (consumed.y != 0)，就立刻標記
                if (consumed.y != 0f) {
                    hasScrolledListInGesture = true
                }

                // 2. 下拉拉出搜尋欄：必須滿足「非慣性滑動」且「本次手勢完全沒有滾動過列表」
                if (available.y > 0 && !source.toString().contains("Fling")) {
                    if (!hasScrolledListInGesture) {
                        val currentOffset = searchOffset.value

                        if (currentOffset < searchBarMaxHeightPx) {
                            val effectiveDrag = available.y * 0.5f
                            val newVal = currentOffset + effectiveDrag

                            if (newVal <= searchBarMaxHeightPx) {
                                scope.launch { searchOffset.snapTo(newVal) }
                                return Offset(0f, available.y)
                            } else {
                                scope.launch { searchOffset.snapTo(searchBarMaxHeightPx) }
                                val consumedActualDrag = (searchBarMaxHeightPx - currentOffset) / 0.5f
                                return Offset(0f, consumedActualDrag)
                            }
                        }
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (searchOffset.value > 0f && searchOffset.value < searchBarMaxHeightPx) {
                    if (searchOffset.value > searchBarMaxHeightPx / 2 || available.y > 500f) {
                        searchOffset.animateTo(searchBarMaxHeightPx, tween(250))
                    } else {
                        searchOffset.animateTo(0f, tween(250))
                    }
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    // Intercept the system / predictive back gesture so leaving the detail
    // returns to the previous screen instead of falling through to the OS
    // (which would drop the user to the launcher). Modal overlays declared
    // later in this composable register their own BackHandler and still win
    // while open, so this only fires when no sheet is showing.
    BackHandler(onBack = onBack)

    PullToRefreshBox(
        isRefreshing = loading && detail != null,
        onRefresh = { scope.launch { refreshPlaylist() } },
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Keep the exact MeloX artwork-driven background renderer for every source.
        MeloXFlowingLightBackdrop(
            artworkUrl = displayed.coverUrl,
            isPlaying = false,
            modifier = Modifier.fillMaxSize(),
        )
        // The animated backdrop can drift darker or lighter than the source
        // artwork used by the palette sampler. Add Apple's legibility layer so
        // the chosen foreground remains readable throughout that motion.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (palette.prefersDarkAppearance) {
                        Color.Black.copy(alpha = 0.26f)
                    } else {
                        Color.White.copy(alpha = 0.46f)
                    },
                ),
        )

        // 【核心修改】：替换原本割裂的 Column，改用 Box 叠加布局，实现真正的全屏边缘沉浸感 (Edge-to-Edge)
        Box(
            modifier = Modifier
                .fillMaxSize()
                // ⚠ 用**稳定**的状态栏高度，不用 `statusBarsPadding()`：后者吃「可见值」，
                //   沉浸 `hide()` 时归 0、退出 `show()` 时弹回真实高度 ⇒ 页面跟着上下跳。
                //   改吃稳定值 ⇒ 位置恒定、不再跳。
                .padding(top = rememberMeloXStableStatusBarHeight())
                .let {
                    if (MeloXSettingsRuntime.playlistSearchBarHidden) it.nestedScroll(searchScrollConnection)
                    else it
                },
        ) {
            // 1. 顶部悬浮层：利用 zIndex(1f) 确保 Toolbar 和搜索栏永远盖在列表最上方
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
            ) {
                MeloXPlaylistToolbar(
                    foreground = foreground,
                    onBack = onBack,
                    onShare = {
                        if (providerAlbum != null) {
                            shareProviderAlbum(context, providerAlbum)
                        } else if (isAlbum) {
                            MeloXNeteaseResourceShareActivity.launch(
                                context,
                                "album",
                                displayed.id,
                                displayed.name,
                                "https://music.163.com/album?id=${displayed.id}",
                            )
                        } else {
                            sharePlaylistFromDetail(context, displayed, collectionSource)
                        }
                    },
                    showMore = true,
                    onMore = {
                        showPlaylistActions = true
                    },
                )

                if (MeloXSettingsRuntime.playlistSearchBarHidden) {
                    // 【隐藏式搜索栏】：利用动态 Height 实现完全折叠与展开
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(with(density) { searchOffset.value.toDp() })
                            .clipToBounds(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        MeloXPlaylistSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            foreground = foreground,
                            modifier = Modifier
                                .padding(start = 18.dp, end = 18.dp, bottom = 10.dp)
                                .height(44.dp)
                        )
                    }
                } else {
                    MeloXPlaylistSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        foreground = foreground,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            } // 悬浮层 Column 结束

            // 2. 原始的歌曲列表：利用 zIndex(0f) 放在底层，满铺全屏幕，滑动时内容自然穿透到悬浮层底部
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (detailWindow.supportsTwoPane) 2 else 1),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
                    .let {
                        if (MeloXSettingsRuntime.playlistSearchBarHidden) {
                            it.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                              .drawWithContent {
                                  drawContent()
                                  val topFadePx = 58.dp.toPx() + (64.dp.toPx() - searchOffset.value)
                                  val fadeFraction = (topFadePx / size.height).coerceIn(0f, 1f)
                                  drawRect(
                                      brush = Brush.verticalGradient(
                                          0.0f to Color.Transparent,
                                          fadeFraction to Color.Black,
                                          1.0f to Color.Black
                                      ),
                                      blendMode = BlendMode.DstIn
                                  )
                              }
                              .layout { measurable, constraints ->
                                  val searchBarMaxPx = 64.dp.toPx()
                                  val extraHeight = searchBarMaxPx.roundToInt()
                                  val collapseAmount = (searchBarMaxPx - searchOffset.value).roundToInt()
                                  val placeable = measurable.measure(
                                      constraints.copy(
                                          minHeight = constraints.minHeight + extraHeight,
                                          maxHeight = constraints.maxHeight + extraHeight
                                      )
                                  )
                                  layout(placeable.width, placeable.height - extraHeight) {
                                      placeable.place(0, -collapseAmount)
                                  }
                              }
                        } else {
                            // Classic Mode: Restrict the drawing bounds of the list to be exactly below the header.
                            it.padding(top = 122.dp)
                        }
                    },
                state = rememberLazyGridState(),
                contentPadding = PaddingValues(
                    start = if (detailWindow.supportsTwoPane) detailWindow.gutter else 0.dp,
                    end = if (detailWindow.supportsTwoPane) detailWindow.gutter else 0.dp,
                    top = if (MeloXSettingsRuntime.playlistSearchBarHidden) 122.dp else 0.dp,
                    bottom = MeloXBottomContentClearance + if (MeloXSettingsRuntime.playlistSearchBarHidden) 64.dp else 0.dp,
                ),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MeloXStandardPlaylistHero(
                        playlist = displayed,
                        tracks = filteredSongs,
                        foreground = foreground,
                        secondary = secondary,
                        sourceLabel = providerAlbum?.id?.sourceId?.displayName
                            ?: displayed.providerPlaylist?.id?.sourceId?.displayName
                            ?: stringResource(R.string.share_netease),
                        onPlay = {
                            PlaybackCommands.playAll(
                                context = context,
                                songs = filteredSongs,
                                onFailure = { errorMessage = it.message ?: "播放失败" },
                                playlistId = cloudPlaylistId,
                            )
                        },
                        onShuffle = {
                            PlaybackCommands.playAll(
                                context = context,
                                songs = filteredSongs,
                                shuffle = true,
                                onFailure = { errorMessage = it.message ?: "播放失败" },
                                playlistId = cloudPlaylistId,
                            )
                        },
                        isSaved = isSaved == true,
                        // ★ 收藏态未知（isSaved == null）时**不显示**收藏/取消收藏钮（2026-10-06）。
                        //   原式 `!isProviderCollection && (!isAlbum || isSaved != null)` 对网易云歌单
                        //   恒为 true（isAlbum=false ⇒ !isAlbum=true），于是状态还没查回来就先把
                        //   按钮画出来，且画的是「＋ 未收藏」；点它走 `desired = isSaved != true`，
                        //   null 时 desired=true ⇒ 执行的是**再收藏一次** ⇒ 用户点「删除」却变成收藏，
                        //   表现为「收藏的歌单无法删除」。改成 null 时不渲染，等真实状态到位再显示。
                        showSaveAction = !isProviderCollection && isSaved != null,
                        onToggleSaved = {
                            if (!isProviderPlaylist && !savingPlaylist) {
                                val desired = isSaved != true
                                savingPlaylist = true
                                // ★ 乐观更新：点击立即切换按钮状态（✓ ↔ ＋），不再「点了没反应」。
                                //   失败时回滚并 Toast 报错（2026-10-09）。
                                isSaved = desired
                                scope.launch {
                                    try {
                                        // ★ 自创歌单的「取消收藏」= 删除歌单（2026-10-07）：
                                        //   `/api/playlist/subscribe t=2` 是「取消订阅**别人的**歌单」，
                                        //   对自己创建的歌单是无效操作 ⇒ 必须走 `/api/playlist/delete`。
                                        //   isOwnedPlaylist 由 userPlaylists 查询确定，creatorUserId 仅兜底
                                        //   （从列表/首页进入详情时 creatorUserId 常为 null，会误判成退订）。
                                        val isOwnPlaylist = !desired &&
                                            (isOwnedPlaylist == true ||
                                                (currentUserId != null && displayed.creatorUserId == currentUserId))
                                        // 超时保护：eapi 在网络黑洞时可能长时间不返回，此前 savingPlaylist
                                        // 无 finally 保护，一旦挂起就永久 true ⇒ 之后所有点击都被
                                        // !savingPlaylist 挡掉，表现为「点不动，没有任何反应」。
                                        withTimeout(15_000) {
                                            if (isAlbum) {
                                                albumClient.setAlbumSubscribed(displayed.id, desired)
                                            } else if (isOwnPlaylist) {
                                                operationsClient.deletePlaylist(displayed.id)
                                            } else {
                                                operationsClient.setPlaylistSubscribed(displayed.id, desired)
                                            }
                                        }
                                        if (isOwnPlaylist) {
                                            // 歌单已不存在，回资料库并刷新列表。
                                            onLibraryChanged()
                                            onBack()
                                        } else {
                                            // isSaved 已乐观更新，无需重复赋值。
                                            // 取消收藏后该歌单应从收藏列表消失 ⇒ 顺带刷新资料库。
                                            if (!desired) onLibraryChanged()
                                        }
                                    } catch (error: Exception) {
                                        // 失败：回滚乐观更新 + 可见提示。errorMessage 只在歌单为空时
                                        // 展示，有歌曲内容时用户看不到，必须 Toast 兜底。
                                        isSaved = !desired
                                        val message = if (error is TimeoutCancellationException) {
                                            if (isAlbum) "专辑收藏操作超时，请重试" else "歌单收藏操作超时，请重试"
                                        } else {
                                            error.message ?: if (isAlbum) "专辑收藏操作失败" else "歌单收藏操作失败"
                                        }
                                        errorMessage = message
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    } finally {
                                        savingPlaylist = false
                                    }
                                }
                            }
                        },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        artworkSharedKey = artworkSharedKey ?: playlistArtworkSharedKey(displayed.id),
                    )
                }

                when {
                    loading && songs.isEmpty() -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = foreground)
                        }
                    }
                    errorMessage != null && songs.isEmpty() -> item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                errorMessage.orEmpty(),
                                color = secondary,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                stringResource(R.string.account_retry),
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .clickable {
                                        scope.launch {
                                            refreshPlaylist()
                                        }
                                    }
                                    .padding(8.dp),
                                color = foreground,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    filteredSongs.isEmpty() && !providerHasMore -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.library_no_songs), color = secondary)
                        }
                    }
                    else -> gridItemsIndexed(
                        items = filteredSongs,
                        key = { _, song -> song.id },
                    ) { index, song ->
                        MeloXPlaylistTrackRow(
                            song = song,
                            index = index,
                            foreground = foreground,
                            showMore = true,
                            isAlbum = isAlbum,
                            onClick = {
                                PlaybackCommands.playQueue(
                                    context = context,
                                    songs = filteredSongs,
                                    selectedSongId = song.id,
                                    onFailure = { errorMessage = it.message ?: "播放失败" },
                                    playlistId = cloudPlaylistId,
                                )
                            },
                            onMore = {
                                selectedTrackAction = song
                            },
                            onPlayNext = { PlaybackCommands.playNext(context, song) },
                            onPlayLast = { PlaybackCommands.addToQueue(context, song) },
                            // 上游 0.6.2（5b7dcaa）：优先走 provider 自己的增删能力，
                            // 不支持的 provider 才回落到网易云操作 / 收藏。
                            endAction = run {
                                val sync = providerSync
                                val playlist = providerPlaylist
                                val track = song.providerTrack
                                if (sync != null && playlist != null && track != null) {
                                    MeloXSwipeAction(context.getString(R.string.library_remove_song), MeloXSymbol.Trash, Color(0xFFFF3B30)) {
                                        if (!providerSyncBusy) {
                                            providerSyncBusy = true
                                            scope.launch {
                                                runCatching {
                                                    withContext(Dispatchers.IO) { sync.removeTrackFromPlaylist(track, playlist) }
                                                }.onSuccess { refreshPlaylist() }
                                                    .onFailure { errorMessage = it.message ?: context.getString(R.string.library_remove_failed) }
                                                providerSyncBusy = false
                                            }
                                        }
                                    }
                                } else if (isProviderCollection) {
                                    null
                                } else if (ownedPlaylistId != null) {
                                    MeloXSwipeAction(context.getString(R.string.library_remove_song), MeloXSymbol.Trash, Color(0xFFFF3B30)) {
                                        scope.launch {
                                            runCatching { operationsClient.removeSongFromPlaylist(song.id, ownedPlaylistId) }
                                                .onSuccess { refreshPlaylist() }
                                                .onFailure { errorMessage = it.message ?: context.getString(R.string.library_remove_failed) }
                                        }
                                    }
                                } else {
                                    MeloXSwipeAction(context.getString(R.string.artist_add_library), MeloXSymbol.Heart, Color(0xFFFF3B30)) {
                                        scope.launch {
                                            runCatching { operationsClient.setSongLiked(song.id, true) }
                                                .onSuccess {
                                                    currentUserId?.let { userId ->
                                                        cache.loadSnapshot(userId)?.let { cached ->
                                                            cache.saveSnapshot(userId, cached.withSongLiked(song, true))
                                                        }
                                                    }
                                                    onSongLikeChanged(song, true)
                                                }
                                                .onFailure { errorMessage = it.message ?: context.getString(R.string.library_add_failed) }
                                        }
                                    }
                                }
                            },
                        )
                        if (song.id != filteredSongs.lastOrNull()?.id) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 66.dp, end = 20.dp),
                                thickness = 0.6.dp,
                                color = foreground.copy(alpha = 0.12f),
                            )
                        }
                    }
                }
                if (pagedBilibiliPlaylist && providerHasMore && !loading) {
                    item(key = "provider-playlist-next-page", span = { GridItemSpan(maxLineSpan) }) {
                        LaunchedEffect(providerPage) {
                            if (providerPageError == null) loadNextProviderPage()
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (providerLoadingMore) CircularProgressIndicator(color = foreground)
                            providerPageError?.let { message ->
                                Text(message, color = secondary, textAlign = TextAlign.Center)
                                Text("重试加载下一页", color = foreground,
                                    modifier = Modifier.clickable { scope.launch { loadNextProviderPage() } }.padding(8.dp))
                            }
                        }
                    }
                }
            }
        }

        val sync = providerSync
        val backingPlaylist = providerPlaylist
        if (sync != null && backingPlaylist != null && (showProviderRename || providerSyncTrack != null)) {
            val track = providerSyncTrack
            val index = track?.let { selected -> filteredSongs.indexOfFirst { it.id == selected.id } } ?: -1
            val canReorder = sortMode == MeloXPlaylistSortMode.Original && searchQuery.isBlank()
            val mutatePlaylist: (suspend () -> Unit) -> Unit = { block ->
                if (!providerSyncBusy) {
                    providerSyncBusy = true
                    providerSyncError = null
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { block() } }
                            .onSuccess {
                                showProviderRename = false
                                providerSyncTrack = null
                                refreshPlaylist()
                            }
                            .onFailure { providerSyncError = it.message }
                        providerSyncBusy = false
                    }
                }
            }
            MeloXProviderPlaylistSyncSheet(
                title = displayed.name,
                trackName = track?.name,
                canRename = track == null,
                canDelete = track == null && sync.canDeletePlaylists,
                onDelete = {
                    if (!providerSyncBusy) {
                        providerSyncBusy = true
                        providerSyncError = null
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { sync.deletePlaylist(backingPlaylist) } }
                                .onSuccess {
                                    showProviderRename = false
                                    onBack()
                                }
                                .onFailure { providerSyncError = it.message }
                            providerSyncBusy = false
                        }
                    }
                },
                canRemove = track?.providerTrack != null,
                canMoveUp = canReorder && index > 0,
                canMoveDown = canReorder && index >= 0 && index < filteredSongs.lastIndex,
                busy = providerSyncBusy,
                error = providerSyncError,
                onRename = { name -> mutatePlaylist { sync.renamePlaylist(backingPlaylist, name) } },
                onRemove = {
                    track?.providerTrack?.let { current ->
                        mutatePlaylist { sync.removeTrackFromPlaylist(current, backingPlaylist) }
                    }
                },
                onMoveUp = {
                    track?.providerTrack?.let { current ->
                        mutatePlaylist { sync.reorderPlaylistTrack(backingPlaylist, current, index - 1) }
                    }
                },
                onMoveDown = {
                    track?.providerTrack?.let { current ->
                        mutatePlaylist { sync.reorderPlaylistTrack(backingPlaylist, current, index + 1) }
                    }
                },
                onDismiss = {
                    showProviderRename = false
                    providerSyncTrack = null
                    providerSyncError = null
                },
            )
        }

        run {
            MeloXPlaylistActionsOverlay(
                playlist = displayed,
                visible = showPlaylistActions,
                onDismiss = { showPlaylistActions = false },
                onRefresh = { if (onRefreshProvidedSongs != null) onRefreshProvidedSongs() else scope.launch { refreshPlaylist() } },
                onBatchDownload = { showBatchDownload = true },
                onAnalyzePlaylist = {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, MeloXPlaybackService::class.java).apply {
                            action = MeloXPlaybackService.ACTION_ANALYZE_PLAYLIST
                            putExtra(
                                MeloXPlaybackService.EXTRA_ANALYSIS_SOURCE,
                                MusicSource.Netease.storageValue,
                            )
                            putExtra(
                                MeloXPlaybackService.EXTRA_ANALYSIS_PLAYLIST_ID,
                                displayed.id.toString(),
                            )
                        },
                    )
                },
                sortMode = sortMode,
                onSortModeChanged = { sortMode = it },
                providerCollection = isProviderCollection,
                canDownload = canDownloadCollection,
                onEdit = if (providerSync != null && !isAlbum) ({ showProviderRename = true }) else null,
                onShare = {
                    if (providerAlbum != null) {
                        shareProviderAlbum(context, providerAlbum)
                    } else if (isAlbum) {
                        MeloXNeteaseResourceShareActivity.launch(
                            context, "album", displayed.id, displayed.name,
                            "https://music.163.com/album?id=${displayed.id}",
                        )
                    } else {
                        sharePlaylistFromDetail(context, displayed, collectionSource)
                    }
                },
                playlistManagement = !isAlbum,
            )
            MeloXBatchDownloadSheet(
                songs = songs,
                sourcePlaylist = MeloXDownloadPlaylistRef(
                    id = displayed.id,
                    name = displayed.name,
                    artworkUrl = displayed.coverUrl,
                ),
                visible = showBatchDownload,
                onDismiss = { showBatchDownload = false },
            )
            val actionSong = selectedTrackAction
            if (actionSong != null) {
                MeloXSongActionsOverlay(
                    song = actionSong,
                    queue = songs,
                    visible = true,
                    onDismiss = { selectedTrackAction = null },
                    sourcePlaylist = MeloXDownloadPlaylistRef(
                        id = displayed.id,
                        name = displayed.name,
                        artworkUrl = displayed.coverUrl,
                    ),
                    sourceOwnedPlaylistId = ownedPlaylistId,
                    onEditPlaylistTrack = if (providerSync != null) ({ providerSyncTrack = actionSong }) else null,
                    onSourcePlaylistChanged = {
                        selectedTrackAction = null
                        scope.launch { refreshPlaylist() }
                    },
                )
            }
        }
    }
}

/**
 * Injects the app-owned favorites playlist at the top of Library → Playlists.
 * It is backed by [MeloXUnifiedFavorites] and is shown even when no provider is
 * logged in, so the local-only playlist never disappears with an account.
 */
private fun NeteaseLibrarySnapshot.withUnifiedFavorites(context: Context): NeteaseLibrarySnapshot {
    val summary = MeloXUnifiedFavorites.playlistSummary(context)
        ?: return copy(playlists = playlists.filterNot { it.isMeloXLocal })
    val local = NeteasePlaylistSummary(
        id = MeloXUnifiedFavorites.LegacyPlaylistId,
        name = summary.title,
        coverUrl = summary.artworkUrl,
        trackCount = summary.trackCount ?: 0,
        creatorName = summary.creatorName.orEmpty(),
        description = summary.description,
        providerPlaylist = summary,
        isMeloXLocal = true,
    )
    return copy(playlists = listOf(local) + playlists.filterNot { it.isMeloXLocal })
}

private fun NeteaseLibrarySnapshot.withSongLiked(song: SearchSong, liked: Boolean): NeteaseLibrarySnapshot =
    copy(
        likedSongs = if (liked) {
            listOf(song) + likedSongs.filterNot { it.id == song.id }
        } else {
            likedSongs.filterNot { it.id == song.id }
        },
    )

@Composable
private fun MeloXPlaylistToolbar(
    foreground: Color,
    onBack: () -> Unit,
    onShare: () -> Unit,
    showMore: Boolean = true,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MeloXGlassCircleButton(
            foreground = foreground,
            size = 44.dp,
            onClick = onBack,
        ) {
            MeloXBackGlyph(Modifier.size(22.dp), foreground)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MeloXGlassCircleButton(
                foreground = foreground,
                size = 44.dp,
                onClick = onShare,
            ) {
                MeloXShareGlyph(Modifier.size(22.dp), Color(0xFFFF3147))
            }
            if (showMore) {
                MeloXGlassCircleButton(
                    foreground = foreground,
                    size = 44.dp,
                    onClick = onMore,
                ) {
                    Text(
                        "•••",
                        color = Color(0xFFFF3147),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeloXPlaylistSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(glassColor(foreground)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeloXSearchGlyph(
            modifier = Modifier
                .padding(start = 14.dp)
                .size(20.dp),
            color = foreground,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    stringResource(R.string.library_search_in_playlist),
                    color = foreground.copy(alpha = 0.46f),
                    fontSize = 17.sp,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = foreground,
                    fontSize = 17.sp,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MeloXStandardPlaylistHero(
    playlist: NeteasePlaylistSummary,
    tracks: List<SearchSong>,
    foreground: Color,
    secondary: Color,
    sourceLabel: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    isSaved: Boolean,
    showSaveAction: Boolean,
    onToggleSaved: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    // 调用方可覆盖共享元素 key：首页/发现页的卡片与这里配对的 key
    // 必须带 collection 前缀，否则同一首页里两个 block 含同一歌单时
    // 同一作用域内会出现重复 key。
    artworkSharedKey: String = playlistArtworkSharedKey(playlist.id),
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val artworkSize = minOf(maxWidth * 0.68f, 300.dp)
        var descriptionExpanded by remember(playlist.id) { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 26.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val sharedArtworkModifier = with(sharedTransitionScope) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(
                        key = artworkSharedKey,
                    ),
                    animatedVisibilityScope = animatedVisibilityScope,
                    // ⚠ 必须显式传：默认是渐近弹簧，会「追动靶」并在整页滑入结束的瞬间被硬切
                    //   补齐 —— 用户报的「最后一帧横移调整位置」。见 [MeloXPageBoundsTransform]。
                    boundsTransform = MeloXPageBoundsTransform,
                    renderInOverlayDuringTransition = true,
                    zIndexInOverlay = 1f,
                )
            }

            // 阴影必须留在 sharedElement 修饰链之外。写在 sharedElement 之后的
            // elevation 会被当作元素内容一起画进过渡 overlay，返回时在封面四周
            // 留下一圈黑色投影（浅色背景上混成暗框）。外层 Box 承担阴影，
            // 静态观感不变，共享元素只剩图片本身。
            Box(
                modifier = Modifier
                    .size(artworkSize)
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(12.dp),
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.18f),
                        spotColor = Color.Black.copy(alpha = 0.18f),
                    ),
            ) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = sharedArtworkModifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                )
            }

            Text(
                text = playlist.name,
                modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp),
                color = foreground,
                fontSize = 22.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = playlist.creatorName.ifBlank { sourceLabel },
                modifier = Modifier.padding(top = 8.dp),
                color = foreground,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = buildString {
                    append("${if (playlist.trackCount > 0) playlist.trackCount else tracks.size} 首歌曲")
                    if (MeloXSettingsRuntime.showPlaylistPlayCount && playlist.playCount > 0) append(" · ${compactPlayCount(playlist.playCount)} 次播放")
                },
                modifier = Modifier.padding(top = 7.dp),
                color = secondary,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )

            Row(
                modifier = Modifier.padding(top = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MeloXGlassCircleButton(
                    foreground = foreground,
                    size = 54.dp,
                    enabled = tracks.isNotEmpty(),
                    onClick = onShuffle,
                ) {
                    MeloXShuffleGlyph(Modifier.size(26.dp), foreground)
                }

                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(50.dp)
                        .meloXLiquidButton(
                            shape = RoundedCornerShape(25.dp),
                            enabled = tracks.isNotEmpty(),
                            tint = if (foreground == Color.White) Color.White else Color.Black,
                            surfaceColor = if (foreground == Color.White) {
                                Color.White.copy(alpha = 0.82f)
                            } else {
                                Color.Black.copy(alpha = 0.82f)
                            },
                            lensRadius = 12.dp,
                            refractionHeight = 20.dp,
                        )
                        .clickable(enabled = tracks.isNotEmpty(), onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        MeloXPlayGlyph(
                            Modifier.size(19.dp),
                            if (foreground == Color.White) Color.Black else Color.White,
                        )
                        Text(
                            stringResource(R.string.action_play),
                            color = if (foreground == Color.White) Color.Black else Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (showSaveAction) {
                    MeloXGlassCircleButton(
                        foreground = foreground,
                        size = 54.dp,
                        onClick = onToggleSaved,
                    ) {
                        if (isSaved) MeloXCheckGlyph(Modifier.size(24.dp), foreground)
                        else MeloXPlusGlyph(Modifier.size(26.dp), foreground)
                    }
                }
            }

            playlist.description
                ?.takeUnless { description ->
                    description.isBlank() || description.equals("null", ignoreCase = true)
                }
                ?.let { description ->
                    Text(
                        text = description,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 24.dp)
                            .clickable { descriptionExpanded = !descriptionExpanded },
                        color = secondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = if (descriptionExpanded) 12 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
        }
    }
}

@Composable
private fun MeloXPlaylistTrackRow(
    song: SearchSong,
    index: Int,
    foreground: Color,
    showMore: Boolean = true,
    isAlbum: Boolean = false,
    onClick: () -> Unit,
    onMore: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayLast: () -> Unit,
    endAction: MeloXSwipeAction?,
) {
    MeloXSwipeActionRow(
        startActions = listOf(
            MeloXSwipeAction(stringResource(R.string.player_play_next), MeloXSymbol.Next, Color(0xFF8E5AF7), onPlayNext),
            MeloXSwipeAction(stringResource(R.string.artist_play_later), MeloXSymbol.Queue, Color(0xFFFF9F0A), onPlayLast),
        ),
        endActions = listOfNotNull(endAction),
        startFullSwipeActionIndex = if (MeloXSettingsRuntime.swipeFullAction == MeloXSwipeFullAction.AddToQueue) 1 else 0,
        onClick = onClick,
        onLongClick = if (showMore) onMore else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 【核心逻辑分流】
            if (isAlbum || !MeloXSettingsRuntime.playlistShowArtwork) {
                // ➔ 分支 1：如果是专辑或用户关闭了歌单封面，完美还原以前的“大数字 + 歌曲名”排版
                Text(
                    text = "${index + 1}",
                    modifier = Modifier.width(40.dp),
                    color = foreground.copy(alpha = 0.48f),
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Text(
                    text = song.name,
                    modifier = Modifier.weight(1f),
                    color = foreground,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                // ➔ 分支 2：如果是歌单，采用“封面 + 歌名/歌手”的现代排版
                AsyncImage(
                    // 【性能救星】：套用 optimized160Artwork，强制要求服务器只给 5KB 的极小缩略图！
                    model = optimized160Artwork(song.artworkUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = song.name,
                        color = foreground,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        maxLines = 1, // 性能优化：强制单行
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artists,
                        color = foreground.copy(alpha = 0.48f),
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        maxLines = 1, // 性能优化：强制单行
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeloXGlassCircleButton(
    foreground: Color,
    size: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .meloXLiquidButton(
                shape = CircleShape,
                enabled = enabled,
                surfaceColor = glassColor(foreground).copy(alpha = 0.48f),
                lensRadius = 11.dp,
                refractionHeight = 18.dp,
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun glassColor(foreground: Color): Color =
    if (foreground == Color.White) Color.Black.copy(alpha = 0.22f)
    else Color.White.copy(alpha = 0.64f)

@Composable
private fun MeloXPlayGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier.meloXIconDrawingScale()) {
        val path = Path().apply {
            moveTo(size.width * 0.24f, size.height * 0.12f)
            lineTo(size.width * 0.86f, size.height * 0.50f)
            lineTo(size.width * 0.24f, size.height * 0.88f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun MeloXPlusGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier.meloXIconDrawingScale()) {
        val stroke = size.minDimension * 0.11f
        val half = size.width * 0.30f
        drawLine(color, Offset(center.x - half, center.y), Offset(center.x + half, center.y), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(center.x, center.y - half), Offset(center.x, center.y + half), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun MeloXCheckGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier.meloXIconDrawingScale()) {
        val stroke = size.minDimension * 0.13f
        val p = Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.53f)
            lineTo(size.width * 0.44f, size.height * 0.75f)
            lineTo(size.width * 0.80f, size.height * 0.25f)
        }
        drawPath(p, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun MeloXBackGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier.meloXIconDrawingScale()) {
        val stroke = size.minDimension * 0.14f
        val p = Path().apply {
            moveTo(size.width * 0.67f, size.height * 0.14f)
            lineTo(size.width * 0.32f, size.height * 0.50f)
            lineTo(size.width * 0.67f, size.height * 0.86f)
        }
        drawPath(p, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun MeloXSearchGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier.meloXIconDrawingScale()) {
        val stroke = size.minDimension * 0.11f
        drawCircle(
            color = color,
            radius = size.minDimension * 0.30f,
            center = Offset(size.width * 0.42f, size.height * 0.40f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.62f, size.height * 0.61f),
            end = Offset(size.width * 0.86f, size.height * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun MeloXShareGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier.meloXIconDrawingScale()) {
        val stroke = size.minDimension * 0.09f
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.20f, size.height * 0.40f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.60f, size.height * 0.50f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.08f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.50f, size.height * 0.63f),
            end = Offset(size.width * 0.50f, size.height * 0.12f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        val arrow = Path().apply {
            moveTo(size.width * 0.34f, size.height * 0.28f)
            lineTo(size.width * 0.50f, size.height * 0.11f)
            lineTo(size.width * 0.66f, size.height * 0.28f)
        }
        drawPath(arrow, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun MeloXShuffleGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier.meloXIconDrawingScale()) {
        val stroke = size.minDimension * 0.095f
        val top = Path().apply {
            moveTo(size.width * 0.10f, size.height * 0.28f)
            cubicTo(
                size.width * 0.34f, size.height * 0.28f,
                size.width * 0.54f, size.height * 0.72f,
                size.width * 0.78f, size.height * 0.72f,
            )
        }
        val bottom = Path().apply {
            moveTo(size.width * 0.10f, size.height * 0.72f)
            cubicTo(
                size.width * 0.34f, size.height * 0.72f,
                size.width * 0.54f, size.height * 0.28f,
                size.width * 0.78f, size.height * 0.28f,
            )
        }
        drawPath(top, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawPath(bottom, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        val a1 = Path().apply {
            moveTo(size.width * 0.70f, size.height * 0.17f)
            lineTo(size.width * 0.89f, size.height * 0.28f)
            lineTo(size.width * 0.70f, size.height * 0.39f)
        }
        val a2 = Path().apply {
            moveTo(size.width * 0.70f, size.height * 0.61f)
            lineTo(size.width * 0.89f, size.height * 0.72f)
            lineTo(size.width * 0.70f, size.height * 0.83f)
        }
        drawPath(a1, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawPath(a2, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

private fun playlistArtworkSharedKey(playlistId: Long): String =
    "library-playlist-artwork-$playlistId"

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private fun compactPlayCount(value: Long): String = when {
    value >= 100_000_000L -> "%.1f 亿".format(value / 100_000_000.0)
    value >= 10_000L -> "%.1f 万".format(value / 10_000.0)
    else -> value.toString()
}

private fun optimized160Artwork(url: String?): String? {
    val source = url?.takeIf(String::isNotBlank) ?: return null
    if (!source.contains(".music.126.net")) return source
    val separator = if (source.contains('?')) '&' else '?'
    return if (source.contains("param=")) source else "$source${separator}param=160y160"
}

internal fun sharePlaylistFromDetail(context: android.content.Context, playlist: NeteasePlaylistSummary, source: MusicSource? = null) {
    val providerPlaylist = playlist.providerPlaylist
    if (providerPlaylist == null && source == null) {
        com.lladlam.melox.ui.sharing.MeloXNeteaseResourceShareActivity.launch(
            context = context,
            type = "playlist",
            id = playlist.id,
            title = playlist.name,
            url = "https://music.163.com/playlist?id=${playlist.id}",
        )
        return
    }

    val text = buildString {
        append(providerPlaylist?.title ?: playlist.name)
        val creator = providerPlaylist?.creatorName ?: playlist.creatorName
        if (creator.isNotBlank()) append(" · ").append(creator)
        append('\n').append(providerPlaylist?.id?.sourceId?.displayName ?: source?.displayName.orEmpty())
    }
    val intent = android.content.Intent.createChooser(
        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        "分享歌单",
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun shareProviderAlbum(context: android.content.Context, album: MusicAlbumSummary) {
    val text = buildString {
        append(album.title)
        val artists = album.artists.joinToString(" / ") { it.name }
        if (artists.isNotBlank()) append(" · ").append(artists)
        append('\n').append(album.id.sourceId.displayName)
    }
    val intent = android.content.Intent.createChooser(
        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        "分享专辑",
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
