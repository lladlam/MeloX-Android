package com.lladlam.melox.ui.search

import com.lladlam.melox.ui.state.rememberRetainedPageState

import com.lladlam.melox.ui.layout.LocalMeloXListDetail
import com.lladlam.melox.ui.layout.rememberMeloXDetailSelection
import com.lladlam.melox.ui.library.MeloXProviderArtistDetailScreen

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.lladlam.melox.ui.layout.rememberMeloXStableStatusBarHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.lladlam.melox.R
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.download.MeloXProviderDownloadStore
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.model.MusicAlbumSummary
import com.lladlam.melox.core.music.model.MusicArtistSummary
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.provider.AlbumCapability
import com.lladlam.melox.core.music.provider.ArtistCapability
import com.lladlam.melox.core.music.provider.CatalogSearchCapability
import com.lladlam.melox.core.music.provider.HomeFeedCapability
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.core.music.provider.loadAllPlaylistTracks
import com.lladlam.melox.core.music.provider.MusicProviderSelectionStore
import com.lladlam.melox.core.music.provider.PlaylistCapability
import com.lladlam.melox.core.music.provider.SearchCapability
import com.lladlam.melox.core.music.provider.UnifiedMusicService
import com.lladlam.melox.core.network.MeloXSearchKind
import com.lladlam.melox.core.network.MeloXSearchMediaItem
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.core.network.NeteaseUniversalSearchClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.playback.ProviderPlaybackCommands
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.animation.MeloXMotion
import com.lladlam.melox.ui.animation.meloXPageRedirectSpec
import com.lladlam.melox.ui.animation.MeloXPageBoundsTransform
import com.lladlam.melox.ui.MeloXInterruptiblePage
import com.lladlam.melox.ui.animation.meloXSettledMillis
import com.lladlam.melox.ui.meloXPageRecede
import com.lladlam.melox.ui.account.MeloXAccountActivity
import com.lladlam.melox.ui.collection.MeloXCollectionDetailActivity
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassTextField
import com.lladlam.melox.ui.glass.MeloXShapes
import com.lladlam.melox.ui.glass.MeloXTypography
import com.lladlam.melox.ui.glass.meloXContentSurface
import com.lladlam.melox.ui.glass.MeloXIosTopBar
import com.lladlam.melox.ui.glass.MeloXActionIcon
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSearchBackMorphIcon
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXSystemColors
import com.lladlam.melox.ui.glass.MeloXSwipeAction
import com.lladlam.melox.ui.glass.MeloXSwipeActionRow
import com.lladlam.melox.ui.podcast.MeloXPodcastScreen
import com.lladlam.melox.ui.library.MeloXUnifiedPlaylistDetailScreen
import com.lladlam.melox.ui.library.MeloXUnifiedProviderAlbumDetailScreen
import com.lladlam.melox.core.music.provider.MeloXLegacyUiBridge
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.meloXEdgeOverscrollHaptic
import com.lladlam.melox.ui.settings.MeloXSwipeFullAction
import com.lladlam.melox.ui.player.MeloXSongActionsOverlay
import com.lladlam.melox.ui.layout.rememberMeloXWindowInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchBackAction { ClearOverlay, ClearQuery, SwitchToHome }

data class MeloXSearchLaunch(val query: String, val kind: MeloXSearchKind, val nonce: Long = System.nanoTime())
object MeloXSearchLaunchBus {
    var request by mutableStateOf<MeloXSearchLaunch?>(null)
        private set
    fun post(query: String, kind: MeloXSearchKind) { request = MeloXSearchLaunch(query, kind) }
    fun consume(request: MeloXSearchLaunch) { if (this.request == request) this.request = null }
}

private val SearchAccent = MeloXSystemColors.Blue

// 搜索页可折叠头部尺寸（对齐发现页 EXPLORE_* 同一套节奏）：
// 折叠行程 74dp（114 → 40）。40dp 已接近下限：标题缩到 24sp 后渲染盒约 31dp，
// 再压缩会让描边被 Box 裁掉；后续要更大行程只能改「折叠态不保留标题、让标题完全
// 让位」（另做 MEMO）。
private val SearchHeaderTopSpaceExpanded = 36.dp
private val SearchHeaderTopSpaceCollapsed = 4.dp
private val SearchTitleBlockExpanded = 62.dp
private val SearchTitleBlockCollapsed = 30.dp
private val SearchTitleGapExpanded = 16.dp
private val SearchTitleGapCollapsed = 6.dp
// 详情覆盖层过渡的收尾余量：动画时长之外再留一点，等 sharedElement 的 overlay
// 层真正收干净，再解锁并放开卡片。给少了会在切换瞬间看到 overlay 残影。
private const val OverlayTransitionSettleMillis = 60L
// 整页子页推入的收尾余量（2026-10-09）。推入弹簧是 ζ=1、k=800（ω≈28.3）⇒ 1px 收敛
// ≈0.27~0.34s、标称 350ms；再留 80ms 确保「组合真页面」发生在动画**完全落定之后**，
// 不会和最后几帧抢 UI 线程。⚠ 走 `meloXSettledMillis` ⇒ 自动跟随系统动画缩放。
private const val SubPageComposeSettleMillis = 80L
private val SearchCategories = listOf("排行榜", "播客", "华语", "欧美", "日语", "韩语", "粤语", "流行", "摇滚", "民谣", "电子", "说唱", "R&B/Soul", "古典", "ACG", "影视原声", "学习", "工作", "放松", "夜晚")

private sealed interface ProviderSearchDestination {
    val source: com.lladlam.melox.core.music.model.SourceId
    val key: String
    val kind: MeloXSearchKind
    val title: String
    val subtitle: String
    val artworkUrl: String?

    data class Playlist(val value: MusicPlaylistSummary) : ProviderSearchDestination {
        override val source = value.id.sourceId
        override val key = "playlist:${source.value}:${value.id.value}"
        override val kind = MeloXSearchKind.Playlists
        override val title = value.title
        override val subtitle = value.creatorName.orEmpty()
        override val artworkUrl = value.artworkUrl
    }

    data class Album(val value: MusicAlbumSummary) : ProviderSearchDestination {
        override val source = value.id.sourceId
        override val key = "album:${source.value}:${value.id.value}"
        override val kind = MeloXSearchKind.Albums
        override val title = value.title
        override val subtitle = value.artists.joinToString(" / ") { it.name }
        override val artworkUrl = value.artworkUrl
    }

    data class Artist(val value: MusicArtistSummary) : ProviderSearchDestination {
        override val source = value.id.sourceId
        override val key = "artist:${source.value}:${value.id.value}"
        override val kind = MeloXSearchKind.Artists
        override val title = value.name
        override val subtitle = buildList {
            value.songCount?.let { add("$it 首歌曲") }
            value.albumCount?.let { add("$it 张专辑") }
        }.joinToString(" · ")
        override val artworkUrl = value.artworkUrl
    }
}

private sealed interface SearchDetailDestination {
    val key: String
    val kind: MeloXSearchKind
    val title: String
    val subtitle: String
    val artworkUrl: String?

    data class Netease(val value: MeloXSearchMediaItem) : SearchDetailDestination {
        override val key = "netease:${value.kind}:${value.id}"
        override val kind = value.kind
        override val title = value.title
        override val subtitle = value.subtitle
        override val artworkUrl = value.artworkUrl
    }

    data class Provider(val value: ProviderSearchDestination) : SearchDetailDestination {
        override val key = value.key
        override val kind = value.kind
        override val title = value.title
        override val subtitle = value.subtitle
        override val artworkUrl = value.artworkUrl
    }
}

/**
 * 有没有可 morph 的 hero 封面 —— 决定这个目的地走「带一镜到底的详情覆盖层」还是
 * 「整页子页面」。
 *
 * 网易云专辑/歌手/播客根本不会走到这里（它们在 `SearchMediaResults` 里直接
 * `MeloXCollectionDetailActivity.launch`，是另一个 Activity）；provider 歌手详情
 * 只有标题 + 歌曲列表、没有 hero，所以归到整页子页面。
 */
private fun SearchDetailDestination.hasHeroOverlay(): Boolean = when (this) {
    is SearchDetailDestination.Netease -> value.kind == MeloXSearchKind.Playlists
    is SearchDetailDestination.Provider ->
        value is ProviderSearchDestination.Playlist || value is ProviderSearchDestination.Album
}

/**
 * 搜索页的「整页子页面」：从搜索主页推进去、返回时退回。它们没有可 morph 的 hero，
 * 走整页推入（`meloXPageEnter`/`meloXPageExit` + 自绘不透明背板），不用共享元素。
 *
 * ⚠ 这里的 `title`/`destination` 是**内容**，打开时写入、返回时不清 —— 可见性由
 * `subPageOpen` 实时推导。两边共用一个 state 的话，返回瞬间内容会先变空，子页面
 * 直接消失、退场动画什么都画不出来（和详情覆盖层同一个坑）。
 */
private sealed interface SearchSubPage {
    data object Podcast : SearchSubPage

    data class Category(val title: String) : SearchSubPage

    data class Detail(val destination: SearchDetailDestination) : SearchSubPage
}

@Composable
fun SearchScreen(
    source: MusicSource = MusicSource.Netease,
    backClearSignal: MutableState<SearchBackAction> = remember { mutableStateOf(SearchBackAction.SwitchToHome) },
    onSearchBackState: (SearchBackAction) -> Unit = {},
    onSearchExit: () -> Unit = {},
    onOpenRecognition: () -> Unit = {},
    pluginSourceId: String? = null,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val songClient = remember(appContext) { NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) }) }
    val universal = remember(appContext) { NeteaseUniversalSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) }) }
    val library = remember(appContext) { NeteaseLibraryClient({ NeteaseSessionStore.readCookie(appContext) }) }
    val operations = remember(appContext) { NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) }) }
    val providerRegistry = remember(appContext) { MeloXMusicProviders.create(appContext) }
    val currentProvider = remember(source, providerRegistry) { providerRegistry.require(source) }
    val providerSongSearch = currentProvider as? SearchCapability
    val providerCatalog = currentProvider as? CatalogSearchCapability
    val providerHome = currentProvider as? HomeFeedCapability
    val unifiedService = remember(providerRegistry) { UnifiedMusicService(providerRegistry) }
    val unifiedEnabled = MusicProviderSelectionStore.unifiedEnabled(appContext)
    val unifiedSources = MusicProviderSelectionStore.unifiedSourceIds(appContext)

    val availableKinds = remember(source, providerCatalog) {
        if (source == MusicSource.Netease) {
            MeloXSearchKind.entries.filter { it != MeloXSearchKind.Podcasts || MeloXSettingsRuntime.podcastsEnabled }
        } else {
            buildList {
                add(MeloXSearchKind.Songs)
                if (providerCatalog != null) {
                    add(MeloXSearchKind.Playlists)
                    add(MeloXSearchKind.Albums)
                    add(MeloXSearchKind.Artists)
                }
            }
        }
    }

    var query by rememberSaveable(source.name) { mutableStateOf("") }
    var selectedPlugin by rememberSaveable(source.name, pluginSourceId) { mutableStateOf(pluginSourceId) }
    val pluginGeneration by providerRegistry.changes.collectAsState()
    // Capture the generation-dependent list outside LazyRow's skippable content lambda.
    // Otherwise a cold-start restore can leave its source chips at the initial empty list.
    val enabledPluginProviders = remember(providerRegistry, pluginGeneration) { providerRegistry.enabledDynamicProviders() }
    var searchTrigger by rememberSaveable(source.name) { mutableIntStateOf(0) }
    var skipSearchDebounce by rememberSaveable(source.name) { mutableStateOf(false) }
    var kind by rememberSaveable(source.name) { mutableStateOf(MeloXSearchKind.Songs) }
    var songs by rememberRetainedPageState(source) { mutableStateOf<List<SearchSong>>(emptyList()) }
    var providerSongs by rememberRetainedPageState(source) { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var providerPlaylists by rememberRetainedPageState(source) { mutableStateOf<List<MusicPlaylistSummary>>(emptyList()) }
    var providerAlbums by rememberRetainedPageState(source) { mutableStateOf<List<MusicAlbumSummary>>(emptyList()) }
    var providerArtists by rememberRetainedPageState(source) { mutableStateOf<List<MusicArtistSummary>>(emptyList()) }
    var providerRecommendations by rememberRetainedPageState(source) { mutableStateOf<List<MusicPlaylistSummary>>(emptyList()) }
    var unifiedFailures by remember(source) { mutableStateOf<List<UnifiedMusicService.SearchFailure>>(emptyList()) }
    var providerSearchPage by rememberRetainedPageState(source, kind, query, unifiedSources, selectedPlugin) { mutableStateOf(1) }
    var providerSearchHasMore by rememberRetainedPageState(source, kind, query) { mutableStateOf(false) }
    var media by rememberRetainedPageState(source) { mutableStateOf<List<MeloXSearchMediaItem>>(emptyList()) }
    var recommendations by rememberRetainedPageState(source) { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var categoryTitle by rememberSaveable(source) { mutableStateOf<String?>(null) }
    var categoryPlaylists by rememberRetainedPageState(source) { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var selectedDetail by rememberRetainedPageState(source) { mutableStateOf<SearchDetailDestination?>(null) }
    // 详情覆盖层的内容与可见性必须拆成两个 state：返回时只关 selectedDetail，
    // overlayDetail 一直保留到退出过渡跑完。否则返回瞬间 let 取到空、详情子树被
    // 立刻拆掉，共享元素封面就只有进入、没有返回。
    var overlayDetail by remember(source) { mutableStateOf(selectedDetail) }
    // 卡片可见性直接用 `selectedDetail?.key` 驱动，与详情 hero 的 visible 完全同步
    // （进入即退出、返回即进入），确保 sharedElement 两端同时处于过渡、能一镜到底配对。
    // ⚠ 不再用 busy/排队串行化：`MeloXInterruptiblePage` 已支持在途改向（弹簧继承值与速度），
    //   退出途中再点卡直接改向即可 —— 排队会把点击压到 ~350ms 后才响应（＝「退出后不能立刻点击」）。
    // 整页子页面（分类页 / 播客页 / 没有 hero 的详情页）的**内容**：同样的道理，
    // 打开时写入、返回时不清，可见性由 subPageOpen 实时推导。
    var subPageContent by rememberRetainedPageState(source) { mutableStateOf<SearchSubPage?>(null) }
    val detailBackProgress = remember { Animatable(0f) }
    var podcastDiscovery by rememberSaveable(source) { mutableStateOf(false) }
    var loading by remember(source) { mutableStateOf(false) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var selectedActionSong by rememberRetainedPageState(source) { mutableStateOf<SearchSong?>(null) }
    val launchRequest = MeloXSearchLaunchBus.request
    var loadedSearch by rememberRetainedPageState(source) { mutableStateOf<List<Any?>?>(null) }

    val visibleSongs = songs
    val visibleProviderSongs = providerSongs

    // 顶部折叠头驱动 —— 复刻 [ExploreCollapsingHeader] 的精确逻辑：折叠进度直接由
    // 子列表自身的 firstVisibleItemScrollOffset 驱动（与发现页同款），不依赖嵌套滚动
    // 连接。首屏内随 offset 从 0→1 收缩；越过首屏（index>0）后强制保持折叠、不回弹。
    val density = LocalDensity.current
    val collapseDistancePx = with(density) { 120.dp.toPx() }
    val listState = rememberLazyListState()
    val collapseProgress by remember(listState, collapseDistancePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(source, availableKinds) {
        if (kind !in availableKinds) kind = MeloXSearchKind.Songs
    }

    LaunchedEffect(launchRequest, source) {
        launchRequest?.let { request ->
            query = request.query
            kind = request.kind.takeIf { it in availableKinds } ?: MeloXSearchKind.Songs
            MeloXSearchLaunchBus.consume(request)
        }
    }

    LaunchedEffect(source) {
        if (recommendations.isNotEmpty() || providerRecommendations.isNotEmpty()) return@LaunchedEffect
        if (source == MusicSource.Netease) {
            runCatching { library.explorePlaylists("推荐歌单", 10) }
                .onSuccess { recommendations = it }
        } else {
            runCatching {
                withContext(Dispatchers.IO) {
                    providerHome?.homeFeed(playlistLimit = 10, newSongLimit = 0, rankingLimit = 0)
                }
            }.onSuccess { providerRecommendations = it?.recommendedPlaylists.orEmpty() }
        }
    }

    LaunchedEffect(query, kind, source, unifiedEnabled, unifiedSources, searchTrigger, selectedPlugin, providerSearchPage, pluginGeneration) {
        val requestKey = listOf(query, kind, source, unifiedEnabled, unifiedSources, searchTrigger, selectedPlugin, providerSearchPage, pluginGeneration)
        if (loadedSearch == requestKey) return@LaunchedEffect
        providerSearchHasMore = false
        if (selectedPlugin != null) return@LaunchedEffect
        val keyword = query.trim()
        if (keyword.isBlank()) {
            songs = emptyList()
            providerSongs = emptyList()
            providerPlaylists = emptyList()
            providerAlbums = emptyList()
            providerArtists = emptyList()
            unifiedFailures = emptyList()
            media = emptyList()
            error = null
            loading = false
            return@LaunchedEffect
        }
        if (!skipSearchDebounce) delay(1500)
        skipSearchDebounce = false
        loading = true
        error = null

        val linkedId = if (source == MusicSource.Netease) parseSongLink(keyword) else null
        if (linkedId != null) {
            providerSongs = emptyList()
            unifiedFailures = emptyList()
            runCatching { universal.songDetail(linkedId) }
                .onSuccess {
                    songs = listOfNotNull(it)
                    media = emptyList()
                    kind = MeloXSearchKind.Songs
                }
                .onFailure { error = it.message ?: "无法读取歌曲链接" }
            loading = false
            if (error == null) loadedSearch = requestKey
            return@LaunchedEffect
        }

        when (kind) {
            MeloXSearchKind.Songs -> {
                media = emptyList()
                providerPlaylists = emptyList()
                providerAlbums = emptyList()
                providerArtists = emptyList()
                if (unifiedEnabled && unifiedSources.size > 1) {
                    songs = emptyList()
                    runCatching {
                        withContext(Dispatchers.IO) {
                            unifiedService.searchSourceIds(
                                query = keyword,
                                sources = unifiedSources,
                                page = providerSearchPage,
                                pageSizePerProvider = 25,
                            )
                        }
                    }.onSuccess { result ->
                        providerSongs = result.aggregated.mapNotNull { it.recommendation?.track }.ifEmpty { result.tracks }
                        unifiedFailures = result.failures
                        providerSearchHasMore = result.pages.values.any { it.hasMore }
                    }.onFailure { failure ->
                        if (failure is kotlinx.coroutines.CancellationException) throw failure
                        providerSongs = emptyList()
                        unifiedFailures = emptyList()
                        error = failure.message ?: "搜索失败"
                    }
                } else if (source == MusicSource.Netease) {
                    providerSongs = emptyList()
                    unifiedFailures = emptyList()
                    runCatching { songClient.ensureArtwork(songClient.searchSongs(keyword)) }
                        .onSuccess { songs = it }
                        .onFailure { error = it.message ?: "搜索失败" }
                } else {
                    songs = emptyList()
                    unifiedFailures = emptyList()
                    val capability = providerSongSearch
                    if (capability == null) {
                        providerSongs = emptyList()
                        error = "${source.displayName} 当前没有歌曲搜索能力"
                    } else {
                        runCatching {
                            withContext(Dispatchers.IO) { capability.searchSongs(keyword, page = providerSearchPage, pageSize = 50) }
                        }.onSuccess { providerSongs = it.items; providerSearchHasMore = it.hasMore }
                            .onFailure { error = it.message ?: "搜索失败" }
                    }
                }
            }

            MeloXSearchKind.Playlists -> {
                songs = emptyList(); providerSongs = emptyList(); media = emptyList(); unifiedFailures = emptyList()
                if (source == MusicSource.Netease) {
                    runCatching { universal.searchMedia(keyword, kind) }
                        .onSuccess { media = it }
                        .onFailure { error = it.message ?: "搜索失败" }
                } else {
                    val capability = providerCatalog
                    if (capability == null || currentProvider !is PlaylistCapability) error = "${source.displayName} 当前没有可用的歌单详情能力"
                    else runCatching {
                        withContext(Dispatchers.IO) { capability.searchPlaylists(keyword, page = 1, pageSize = 40).items }
                    }.onSuccess { providerPlaylists = it }
                        .onFailure { error = it.message ?: "搜索失败" }
                }
            }

            MeloXSearchKind.Albums -> {
                songs = emptyList(); providerSongs = emptyList(); media = emptyList(); unifiedFailures = emptyList()
                if (source == MusicSource.Netease) {
                    runCatching { universal.searchMedia(keyword, kind) }
                        .onSuccess { media = it }
                        .onFailure { error = it.message ?: "搜索失败" }
                } else {
                    val capability = providerCatalog
                    if (capability == null || currentProvider !is AlbumCapability) error = "${source.displayName} 当前没有可用的专辑详情能力"
                    else runCatching {
                        withContext(Dispatchers.IO) { capability.searchAlbums(keyword, page = 1, pageSize = 40).items }
                    }.onSuccess { providerAlbums = it }
                        .onFailure { error = it.message ?: "搜索失败" }
                }
            }

            MeloXSearchKind.Artists -> {
                songs = emptyList(); providerSongs = emptyList(); media = emptyList(); unifiedFailures = emptyList()
                if (source == MusicSource.Netease) {
                    runCatching { universal.searchMedia(keyword, kind) }
                        .onSuccess { media = it }
                        .onFailure { error = it.message ?: "搜索失败" }
                } else {
                    val capability = providerCatalog
                    if (capability == null || currentProvider !is ArtistCapability) error = "${source.displayName} 当前没有可用的歌手详情能力"
                    else runCatching {
                        withContext(Dispatchers.IO) { capability.searchArtists(keyword, page = 1, pageSize = 40).items }
                    }.onSuccess { providerArtists = it }
                        .onFailure { error = it.message ?: "搜索失败" }
                }
            }

            else -> {
                providerSongs = emptyList(); unifiedFailures = emptyList()
                if (source != MusicSource.Netease) {
                    media = emptyList()
                    error = "${source.displayName} 不提供${kind.title}搜索"
                } else {
                    runCatching { universal.searchMedia(keyword, kind) }
                        .onSuccess { media = it; songs = emptyList() }
                        .onFailure { error = it.message ?: "搜索失败" }
                }
            }
        }
        if (error == null) loadedSearch = requestKey
        loading = false
    }

    // 走「右侧滑入覆盖层」的详情：封面能从搜索结果卡片一镜到底接进详情 hero 的那几类。
    // 网易云专辑/歌手/播客打开的是另一个 Activity（共享元素不能跨 Activity 配对），
    // provider 歌手详情没有 hero 封面 —— 都不在此列，改走下面的整页子页面层。
    val overlayDestination = selectedDetail?.takeIf { it.hasHeroOverlay() }
    // 整页子页面是否展开（实时可见性；内容在 subPageContent 那一侧保留）。
    val subPageOpen = podcastDiscovery ||
        (categoryTitle != null) ||
        (selectedDetail != null && overlayDestination == null)
    // 手势返回走完后位移停在 1（页面已在屏外）。下一次进入必须归零，
    // 否则新页面会带着满位移进来、永远停在屏幕右侧外面。
    LaunchedEffect(overlayDestination) {
        if (overlayDestination != null) detailBackProgress.snapTo(0f)
    }
    // ── 「推入动画期间不组合重子页面」闸门（2026-10-09，修「搜索-浏览类别-播客」一顿一顿）──
    //
    // ⚠⚠ 根因：推入动画与子页面首屏**抢同一个 UI 线程**。动画期间本轮的目标帧率是 120fps
    //   （8.3ms/帧预算），只要有一项重活落在动画窗口里，
    //   帧就掉到 16.7ms+ 并被读成顿挫。播客首屏恰好带三样重的**脚手架**：
    //     ① 自己一层**嵌套** `SharedTransitionLayout`（本页已在外层 search 的 STL 里，
    //        再套一层 = 每帧多跑一次 lookahead）；
    //     ② `PullToRefreshBox`（含它自己的无限进度指示器）；
    //     ③ `PodcastLoadingState` 的 `CircularProgressIndicator`（又一条无限动画）。
    //   ⇒ 三者一起在推入那几帧被组合，就是用户看到的「一顿一顿」。
    //   ⚠ 2026-09-26 那次只延后了**网络请求**（`MeloXPodcastScreen(deferLoadMillis=…)`），
    //     **没有延后组合**，所以这条至今还在 —— 现在补的正是「组合」这一半。
    //
    // 对照：同一路径的「排行榜」（`SearchCategoryPage`）**不卡**，因为它只有一个 Column + 列表
    // （loading 也只是个 spinner），没有嵌套 STL / PTR。这是本判断最直接的旁证。
    //
    // ⛔⛔ **不能**对「有 hero 参与推入」的子页做这件事：分类页（`SearchCategoryPage`）与
    //   详情覆盖层里的共享元素必须**全程在场**，晚组合一步就配不上对、morph 直接断。
    //   所以只对「推入里没有任何 sharedElement 两端」的子页（播客页 / provider 歌手详情）开闸。
    //
    // ⚠ 关页时**不**复位：退场动画期间必须继续画真页面，复位会把它换成占位、滑出去时闪一下。
    //   复位交给下一次打开（`subPageOpen` 变 true 时先置 false）。
    var subPageComposed by remember(source) { mutableStateOf(false) }
    LaunchedEffect(subPageOpen) {
        if (subPageOpen) {
            subPageComposed = false
            delay(meloXSettledMillis(MeloXMotion.PageEnterMillis, SubPageComposeSettleMillis))
            subPageComposed = true
        }
    }
    // 打开详情时可见性（selectedDetail）与内容（overlayDetail）同时写入。
    // 返回只清 selectedDetail，内容留到退出过渡跑完再自然 dispose。
    val onDetailSelected = rememberMeloXDetailSelection()
    fun applyDetail(destination: SearchDetailDestination) {
        onDetailSelected()
        selectedDetail = destination
        overlayDetail = destination
        // 没有 hero 的详情（provider 歌手）落在整页子页面层，内容同样要留到退场结束。
        if (!destination.hasHeroOverlay()) subPageContent = SearchSubPage.Detail(destination)
    }
    // ⚠ 立即执行、不排队：退场途中再点卡由 MeloXInterruptiblePage 的弹簧在途改向
    //   （继承当前位移与速度），点下去即响应 —— 这正是「退出后能立刻点击」。
    //   直接执行旧过渡会被取消，但那是弹簧改向该做的事（位置/速度双连续，无卡死态）。
    fun openDetail(destination: SearchDetailDestination) = applyDetail(destination)
    // 详情覆盖层的内容生命周期：只做「退场跑完再释放 overlayDetail」——
    // 退场期间详情子树仍在组合，封面才有机会一镜到底 morph 回结果卡片。
    // 途中再打开会让 overlayDestination 立刻变非空 ⇒ 本 effect 取消重启，内容不被清、直接续上。
    LaunchedEffect(overlayDestination) {
        if (overlayDestination == null && overlayDetail != null) {
            delay(meloXSettledMillis(MeloXMotion.PageExitMillis, OverlayTransitionSettleMillis))
            overlayDetail = null
        }
    }
    // The host-level BackHandler always wins over the system back button, so the
    // search page reports its current back action to the host through
    // onSearchBackState. The host reads it inside its own BackHandler and either
    // clears the query/overlay (via backClearSignal) or switches to the home
    // page. The search page never writes backClearSignal itself, otherwise
    // reacting to an overlay would clear the overlay and loop forever.
    LaunchedEffect(query, podcastDiscovery, selectedDetail, categoryTitle, overlayDestination) {
        onSearchBackState(
            when {
                overlayDestination != null || podcastDiscovery || selectedDetail != null || categoryTitle != null ->
                    SearchBackAction.ClearOverlay
                query.isNotBlank() -> SearchBackAction.ClearQuery
                else -> SearchBackAction.SwitchToHome
            },
        )
    }
    var backActionConsumed by remember { mutableStateOf(SearchBackAction.SwitchToHome) }
    LaunchedEffect(backClearSignal.value, backActionConsumed) {
        val action = backClearSignal.value
        if (action == SearchBackAction.SwitchToHome || action == backActionConsumed) return@LaunchedEffect
        backActionConsumed = action
        when (action) {
            SearchBackAction.ClearOverlay -> {
                podcastDiscovery = false
                selectedDetail = null
                categoryTitle = null
                // ⚠ 这里**不清** categoryPlaylists：分类页要留着列表才播得完退场动画，
                // 下次打开会先 loading=true 再覆盖，不会闪到旧数据。
                error = null
            }
            SearchBackAction.ClearQuery -> {
                query = ""
                skipSearchDebounce = false
                searchTrigger += 1
            }
            SearchBackAction.SwitchToHome -> Unit
        }
        backClearSignal.value = SearchBackAction.SwitchToHome
    }
    PredictiveBackHandler(enabled = overlayDestination != null) {
        try {
            it.collect { event -> detailBackProgress.snapTo(event.progress) }
            // 先启动退出过渡：此刻位移仍停在手势落点上，封面从这里一镜到底
            // morph 回结果卡片，剩余位移随后收尾。不能在关闭详情之后 snapTo(0f)：
            // 那会在页面已经在屏外时把位移瞬间归零，满屏不透明的详情页会闪回
            // 屏幕正中再滑出去。
            selectedDetail = null
            detailBackProgress.animateTo(1f, tween(160))
        } catch (_: CancellationException) {
            detailBackProgress.animateTo(0f, tween(160))
        }
    }
    BackHandler(enabled = overlayDestination == null && (podcastDiscovery || selectedDetail != null || categoryTitle != null)) {
        when {
            podcastDiscovery -> podcastDiscovery = false
            selectedDetail != null -> selectedDetail = null
            // 只关可见性；categoryPlaylists 留给退场动画用（下次打开先 loading=true 再覆盖）。
            else -> categoryTitle = null
        }
    }

    // 顶层提供共享元素作用域：下面的结果卡片与右侧滑入的详情 hero 都在它里面，
    // 封面才能从卡片位置一镜到底 morph 到详情。
    SharedTransitionLayout(Modifier.fillMaxSize()) {
    val searchSharedScope = this
    Box(Modifier.fillMaxSize()) {
        // 搜索主页层。整页子页面推进来时**下沉 + 压暗 + 左移视差**（iOS 纵深），退回时反向复位。
        // ⚠ 有 hero 的详情（overlayDestination）打开时 subPageOpen 仍为 false ⇒ 主页保持静止；
        //   那是共享元素 morph 的必要条件（旧页一动、封面就错位），详见 [meloXPageRecede]。
        // ⚠⚠ **绝不能写 `by`**：委托会在组合作用域取 `State.value` ⇒ 搜索主页（含折叠头、
        //   分类行、结果列表）在整段推入/退回里**每帧重组**，真机读成掉帧。保留 State 对象、
        //   在 `meloXPageRecede { … }` 的 lambda 里读 `.value`，读取才落在 graphicsLayer
        //   阶段（只失效图层，不重组）。详见 [meloXPageRecede]。
        // ⚠ 时长/缓动**必须与推入页完全一致**：子页层已改为 `AnimatedContent` +
        //   `meloXPagePushSpec()`（ζ=1、k=800 临界阻尼弹簧），主页退让用**同一条**弹簧才严格互补
        //   （「子页滑出 (1→0)」与「主页回位 (…→0)」同曲线）；任何一侧换了参数，两页之间那条缝会忽宽忽窄。
        // ⚠ 统一走 `meloXPageRedirectSpec()`（同一条弹簧的 Float 通道）：被打断时弹簧继承当前速度
        //   ⇒ 主页平滑反转，无瞬移。旧版「snap 到起点再重放」会把主页从半路瞬移，正是顿挫来源，已废。
        val homeRecede = remember { Animatable(if (subPageOpen) 1f else 0f) }
        LaunchedEffect(subPageOpen) {
            val target = if (subPageOpen) 1f else 0f
            if (homeRecede.value == target) return@LaunchedEffect // 首帧 / 已到位：不重播
            homeRecede.animateTo(target, meloXPageRedirectSpec())
        }
        // ⚠ 主页被整页子页盖住（已下沉）时，它的卡片**不能再占用共享元素 key** —— 否则会与
        //   子页/详情里的同名卡片构成**重复 shared key**，morph 的起点会错位（`SearchCardVisibility`
        //   正是靠 `destinationKey != selectedKey` 让被选中那张卡进入 exit 过渡）。
        val homeSelectedKey = if (subPageOpen) null else selectedDetail?.key
        AnimatedVisibility(
            // 旧页始终在场，退让交给 [meloXPageRecede]（不再走 enter/exit 的淡出）。
            visible = true,
            modifier = Modifier
                .fillMaxSize()
                .meloXPageRecede { homeRecede.value }
                // 被覆盖时屏蔽主页的无障碍节点（与设置列表同款），否则读屏会同时读出两层。
                .then(if (subPageOpen && !LocalMeloXListDetail.current) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
                val window = rememberMeloXWindowInfo()
                Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = rememberMeloXStableStatusBarHeight())
            .padding(top = 26.dp)
            .padding(horizontal = if (window.supportsTwoPane) window.gutter else 0.dp),
                ) {
        // 顶部折叠头 —— 复刻 [ExploreCollapsingHeader] 的完整逻辑：**布局高度**随折叠进度
        // 收缩（而不是只做绘制期缩放），所以下方的搜索框、分类行、结果区会整体上移，
        // 正是「搜索框跟着一起上移」的来源。头部共收缩 74dp（36→4 + 62→30 + 16→6）。
        // ⚠ 标题块折叠到 30dp 后会小于 Text 自身的布局盒，必须给 Text 加
        //   wrapContentSize(unbounded=true)，否则盒子被父级 clamp、绘制被裁到盒底，
        //   标题下半截会「被水平切平」。
        Spacer(Modifier.height(lerp(SearchHeaderTopSpaceExpanded, SearchHeaderTopSpaceCollapsed, collapseProgress)))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(lerp(SearchTitleBlockExpanded, SearchTitleBlockCollapsed, collapseProgress))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(R.string.tab_search),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    // 关键①：不写 lineHeight。34sp 的自然行高已经大于 44sp 指定的额外 leading，
                    //   那部分 leading 不计进 Text 的布局盒，墨迹会被整体往下推约一行高 ——
                    //   标题就画到了块外、被块底切掉下半截（发现页能容忍是因为它的折叠块
                    //   有 38dp，比 30dp 的墨迹高；这里不能这么写）。
                    // 关键②：unbounded 让折叠后的 30dp 标题块**不能反向压缩** Text 自己的
                    //   布局盒。Box 本身不裁剪，所以盒子溢出到块外是安全的；绘制又被
                    //   graphicsLayer 等比缩到 24sp 等效，始终落在块内。
                    .wrapContentSize(unbounded = true)
                    .graphicsLayer {
                        // 34sp → 24sp 等效缩放，左侧锚定（transformOrigin x=0）不横向漂移。
                        val scale = 1f + (24f / 34f - 1f) * collapseProgress
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        alpha = 1f - 0.15f * collapseProgress
                    },
            )
        }
        Spacer(Modifier.height(lerp(SearchTitleGapExpanded, SearchTitleGapCollapsed, collapseProgress)))
        SearchField(
            value = query,
            onValueChange = { query = it; skipSearchDebounce = false },
            onSearch = { skipSearchDebounce = true; searchTrigger += 1 },
            source = source,
            onBack = { if (query.isNotBlank()) query = "" else onSearchExit() },
            onOpenRecognition = onOpenRecognition,
        )
        if (enabledPluginProviders.isNotEmpty() || selectedPlugin != null) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                item {
                    androidx.compose.material3.TextButton(onClick = { selectedPlugin = null; MusicProviderSelectionStore.setSelectedSource(appContext, source) }) {
                        Text(if (selectedPlugin == null) "✓ ${source.displayName}" else source.displayName)
                    }
                }
                items(enabledPluginProviders, key = { it.sourceId.value }) { provider ->
                    androidx.compose.material3.TextButton(onClick = { selectedPlugin = provider.sourceId.value; kind = MeloXSearchKind.Songs; MusicProviderSelectionStore.setSelectedSourceId(appContext, provider.sourceId) }) {
                        Text(if (selectedPlugin == provider.sourceId.value) "✓ ${provider.displayName}" else provider.displayName)
                    }
                }
            }
        }
        if (query.isNotBlank() && selectedPlugin == null) {
            SearchScopes(kind = kind, availableKinds = availableKinds, onKind = { kind = it })
        }

        Box(Modifier.weight(1f)) {
            when {
                selectedPlugin != null -> PluginSearchResults(providerRegistry, selectedPlugin!!, query, pluginGeneration)
                query.isBlank() && source == MusicSource.Netease -> SearchDiscovery(
                    state = listState,
                    recommendations = recommendations,
                    onPlaylist = { openDetail(SearchDetailDestination.Netease(it.asSearchItem())) },
                    sharedTransitionScope = searchSharedScope,
                     selectedKey = homeSelectedKey,
                     onCategory = { category ->
                         onDetailSelected()
                        if (category == "播客") {
                            subPageContent = SearchSubPage.Podcast
                            podcastDiscovery = true
                        } else {
                            subPageContent = SearchSubPage.Category(category)
                            categoryTitle = category
                            loading = true; error = null
                            scope.launch {
                                runCatching { library.explorePlaylists(category, 50) }
                                    .onSuccess { categoryPlaylists = it }
                                    .onFailure { error = it.message ?: "类别加载失败" }
                                loading = false
                            }
                        }
                    },
                )
                query.isBlank() -> ProviderSearchDiscovery(
                    state = listState,
                    source = source,
                    recommendations = providerRecommendations,
                    onPlaylist = { openDetail(SearchDetailDestination.Provider(ProviderSearchDestination.Playlist(it))) },
                    sharedTransitionScope = searchSharedScope,
                    selectedKey = homeSelectedKey,
                )
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SearchAccent)
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
                kind == MeloXSearchKind.Songs && (unifiedEnabled || source != MusicSource.Netease) -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(enabled = providerSearchPage > 1, onClick = { providerSearchPage-- }) { Text("上一页") }
                        Text("第 $providerSearchPage 页", Modifier.padding(12.dp))
                        TextButton(enabled = providerSearchHasMore, onClick = { providerSearchPage++ }) { Text("下一页") }
                    }
                    Box(Modifier.weight(1f)) { ProviderSearchSongResults(
                    state = listState,
                    values = visibleProviderSongs,
                    failures = unifiedFailures,
                    showSource = unifiedEnabled,
                    onPlay = { track ->
                        ProviderPlaybackCommands.playQueue(
                            context = context,
                            tracks = visibleProviderSongs,
                            selectedTrackId = track.id,
                            onFailure = { failure -> error = failure.message ?: "播放失败" },
                        )
                    },
                ) }
                }
                kind == MeloXSearchKind.Songs -> SearchSongResults(
                    state = listState,
                    values = visibleSongs,
                    onPlay = { song -> PlaybackCommands.playQueue(context, visibleSongs, song.id) },
                    onMore = { selectedActionSong = it },
                    onLike = { song ->
                        scope.launch {
                            runCatching { operations.setSongLiked(song.id, true) }
                                .onFailure { error = it.message ?: "添加到资料库失败" }
                        }
                    },
                )
                source != MusicSource.Netease && kind == MeloXSearchKind.Playlists -> ProviderSearchMediaResults(
                    state = listState,
                    values = providerPlaylists.map { ProviderSearchDestination.Playlist(it) },
                    onOpen = { openDetail(SearchDetailDestination.Provider(it)) },
                    sharedTransitionScope = searchSharedScope,
                    selectedKey = homeSelectedKey,
                )
                source != MusicSource.Netease && kind == MeloXSearchKind.Albums -> ProviderSearchMediaResults(
                    state = listState,
                    values = providerAlbums.map { ProviderSearchDestination.Album(it) },
                    onOpen = { openDetail(SearchDetailDestination.Provider(it)) },
                    sharedTransitionScope = searchSharedScope,
                    selectedKey = homeSelectedKey,
                )
                source != MusicSource.Netease && kind == MeloXSearchKind.Artists -> ProviderSearchMediaResults(
                    state = listState,
                    values = providerArtists.map { ProviderSearchDestination.Artist(it) },
                    onOpen = { openDetail(SearchDetailDestination.Provider(it)) },
                )
                else -> SearchMediaResults(
                    state = listState,
                    values = media,
                    onOpen = { item ->
                        when (item.kind) {
                            MeloXSearchKind.Albums, MeloXSearchKind.Artists, MeloXSearchKind.Podcasts -> MeloXCollectionDetailActivity.launch(context, item)
                            MeloXSearchKind.Users -> MeloXAccountActivity.launch(context, item.id)
                            else -> openDetail(SearchDetailDestination.Netease(item))
                        }
                    },
                    sharedTransitionScope = searchSharedScope,
                    selectedKey = homeSelectedKey,
                )
            }
        }
            }
        }
        // 整页子页面层：分类页（浏览类别 → 排行榜等）/ 播客页 / 没有 hero 的详情页（provider 歌手）。
        // 这几类没有可 morph 的 hero 封面，走「整页推入」：自带不透明背板 + 从右侧满宽滑入
        // 盖住主页，主页同步向左视差退让；退回时反向滑出。和「设置 → 详情页」同一套动作。
        // ⚠ 背板必须在这里自己画：MeloX 的页面本身都是透明底、不透明背板只在 MeloXApp 根部
        //   画一次；不补背板，整页滑入的全程会把下层列表透出来，观感是撕裂而不是推入。
        // 内容取 subPageContent（返回时不清），退场动画期间才有东西可画。
        MeloXInterruptiblePage(
            visible = subPageOpen,
            onBack = {
                when {
                    podcastDiscovery -> podcastDiscovery = false
                    selectedDetail != null -> selectedDetail = null
                    else -> categoryTitle = null
                }
            },
            retreatProgress = { 0f },
            modifier = Modifier.fillMaxSize().zIndex(1f),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
            when (val page = subPageContent) {
                // ⚠ 组合**本身**推到推入落定之后（见 `subPageComposed`）⇒ 动画窗口里只剩轻量占位：
                //   嵌套 STL / PullToRefreshBox / 无限进度动画 / 列表 / 图片解码全都不进动画帧。
                //   因此这里不再需要 `deferLoadMillis` —— 那是「已组合但先不拉数据」的半套补救，
                //   治不了「组合本身占 UI 线程」这一半。
                SearchSubPage.Podcast -> if (subPageComposed) MeloXPodcastScreen(
                    // 推入层 Box 自己不吃状态栏（兄弟子页都各自在内部 statusBarsPadding），
                    // 播客首页这里也必须让出，否则大标题会顶进状态栏。
                    applyStatusBarPadding = true,
                ) else MeloXSubPagePlaceholder()
                is SearchSubPage.Category -> SearchCategoryPage(
                    title = page.title,
                    values = categoryPlaylists,
                    loading = loading,
                    error = error,
                    onBack = { categoryTitle = null; error = null },
                    onPlaylist = { openDetail(SearchDetailDestination.Netease(it.asSearchItem())) },
                    sharedTransitionScope = searchSharedScope,
                    selectedKey = selectedDetail?.key,
                )
                // provider 歌手详情同样「推入里没有 sharedElement 两端」⇒ 同样可以晚组合。
                is SearchSubPage.Detail -> if (subPageComposed) SearchCollectionDetail(
                    destination = page.destination,
                    universal = universal,
                    library = library,
                    providerRegistry = providerRegistry,
                    onBack = { selectedDetail = null },
                ) else MeloXSubPagePlaceholder()
                null -> Unit
            }
            }
        }
        // 详情覆盖层。zIndex 要压在整页子页面层之上（从分类页点歌单时两层同时在场）。
        // visible 只由 selectedDetail 驱动，内容取 overlayDetail（返回时不清），
        // 于是退出过渡期间详情子树仍在组合里，封面才有机会一镜到底 morph 回结果卡片。
        MeloXInterruptiblePage(
            visible = overlayDestination != null,
            onBack = { selectedDetail = null },
            retreatProgress = { detailBackProgress.value },
            modifier = Modifier.fillMaxSize().zIndex(2f),
        ) {
            val detailVisibilityScope = this
            overlayDetail?.let { destination ->
                SearchCollectionDetail(
                    destination = destination,
                    universal = universal,
                    library = library,
                    providerRegistry = providerRegistry,
                    onBack = { selectedDetail = null },
                    sharedTransitionScope = searchSharedScope,
                    animatedVisibilityScope = detailVisibilityScope,
                    artworkSharedKey = searchArtworkSharedKey(destination),
                )
            }
        }
    }
    } // SharedTransitionLayout
    selectedActionSong?.let { song ->
        MeloXSongActionsOverlay(
            song = song,
            queue = visibleSongs,
            visible = true,
            onDismiss = { selectedActionSong = null },
        )
    }
}

@Composable
private fun PluginSearchResults(
    registry: com.lladlam.melox.core.music.provider.MusicProviderRegistry,
    sourceValue: String,
    query: String,
    generation: Long,
) {
    val context = LocalContext.current
    val state = rememberLazyListState()
    PluginCatalogBrowser(registry, sourceValue, query, generation) { tracks ->
        ProviderSearchSongResults(state, tracks, emptyList(), true) { track ->
            ProviderPlaybackCommands.playQueue(context, tracks, track.id)
        }
    }
}

/**
 * 结果卡片封面与详情 hero 共用的配对 key。带前缀是为了避免同一作用域内撞 key
 * （「热门推荐」与结果列表可能含同一个歌单）。key 直接复用
 * [SearchDetailDestination.key]，于是「点了哪张卡」和「详情开到哪个目的地」
 * 必然算出同一个 key。
 */
private const val SearchArtworkKeyPrefix = "search-collection-artwork-"

private fun searchArtworkSharedKey(destination: SearchDetailDestination): String =
    SearchArtworkKeyPrefix + destination.key

private fun searchArtworkSharedKey(destinationKey: String): String =
    SearchArtworkKeyPrefix + destinationKey

/**
 * 把结果卡片封面挂到宿主顶层的 SharedTransitionLayout 上，使搜索页打开歌单/专辑时
 * 封面能从卡片位置一镜到底 morph 到详情 hero。scope 为空（未接入共享元素的场景）
 * 时原样返回，不影响常规渲染。
 */
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun Modifier.meloXSearchSharedArtwork(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    key: String,
): Modifier {
    if (LocalMeloXListDetail.current || sharedTransitionScope == null || animatedVisibilityScope == null) return this
    return with(sharedTransitionScope) {
        this@meloXSearchSharedArtwork.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope,
            // ⚠ 必须显式传：默认是渐近弹簧，会「追动靶」并在整页滑入结束的瞬间被硬切补齐
            //   —— 用户报的「最后一帧横移调整位置」。见 [MeloXPageBoundsTransform] 的完整推导。
            boundsTransform = MeloXPageBoundsTransform,
            // 过渡期间封面留在共享 overlay 层，避免被列表/详情的裁剪吃掉。
            renderInOverlayDuringTransition = true,
            zIndexInOverlay = 1f,
        )
    }
}

/**
 * 被点选中的那张卡必须真的进入 exit 过渡，共享元素才会配对：
 * 恒可见（`AnimatedVisibility(visible = true)`）是配不上的。
 */
@Composable
private fun SearchCardVisibility(
    destinationKey: String,
    selectedKey: String?,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = LocalMeloXListDetail.current || destinationKey != selectedKey,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier,
        label = "search-card-$destinationKey",
        content = content,
    )
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    source: MusicSource,
    onBack: () -> Unit,
    onOpenRecognition: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val clearDescription = stringResource(R.string.search_clear)
    MeloXGlassTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .padding(horizontal = 20.dp),
        leadingContent = {
            MeloXSearchBackMorphIcon(
                focused = focused,
                modifier = Modifier
                    .size(44.dp)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(11.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                contentDescription = stringResource(if (focused) R.string.action_back else R.string.tab_search),
            )
        },
        placeholder = {
            Text(
                stringResource(R.string.search_placeholder),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f),
                fontSize = 17.sp,
            )
        },
        trailingContent = {
            Row {
                if (value.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(role = Role.Button) { onSearch() }
                            .semantics { contentDescription = "搜索" },
                        contentAlignment = Alignment.Center,
                    ) {
                        MeloXSymbolIcon(
                            symbol = MeloXSymbol.Search,
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(role = Role.Button) { onValueChange("") }
                            .semantics { contentDescription = clearDescription },
                        contentAlignment = Alignment.Center,
                    ) {
                        MeloXSymbolIcon(
                            symbol = MeloXSymbol.Xmark,
                            modifier = Modifier.size(15.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f),
                        )
                    }
                } else {
                    // 空态给「听歌识曲」让位，输入态让位给搜索 / 清除 ——
                    // 两个分支互斥，trailing 宽度恒为 44dp（空）或 88dp（有字），文本区不抖动。
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(role = Role.Button) { onOpenRecognition() }
                            .semantics { contentDescription = "听歌识曲" },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Microphone 已在 MeloXSymbolIcon 里被换成 ic_melox_mic
                        // （同 Settings 的品牌矢量分支），这里写法与相邻图标保持一致即可。
                        MeloXSymbolIcon(
                            symbol = MeloXSymbol.Microphone,
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f),
                        )
                    }
                }
            }
        },
        textStyle = androidx.compose.ui.text.TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            lineHeight = 22.sp,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        onFocusChanged = { focused = it },
    )
}

@Composable
private fun SearchScopes(
    kind: MeloXSearchKind,
    availableKinds: List<MeloXSearchKind>,
    onKind: (MeloXSearchKind) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(availableKinds) { item ->
            MeloXGlassButton(
                onClick = { onKind(item) },
                modifier = Modifier
                    .height(44.dp)
                    .padding(horizontal = 0.dp),
                style = MeloXGlassButtonStyle.Bordered,
                shape = MeloXShapes.capsule,
                tint = if (item == kind) MeloXSystemColors.Blue.copy(alpha = .28f) else Color.Transparent,
                surfaceColor = if (item == kind) MeloXSystemColors.Blue.copy(alpha = .16f) else MaterialTheme.colorScheme.onBackground.copy(alpha = .045f),
                contentPadding = PaddingValues(horizontal = 15.dp),
            ) {
                Text(
                    item.title,
                    color = if (item == kind) SearchAccent else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (item == kind) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SearchDiscovery(
    state: LazyListState = rememberLazyListState(),
    recommendations: List<NeteasePlaylistSummary>,
    onPlaylist: (NeteasePlaylistSummary) -> Unit,
    onCategory: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    selectedKey: String? = null,
) {
    val window = rememberMeloXWindowInfo()
    val categoryColumns = window.gridColumns.coerceIn(2, 4)
    LazyColumn(
        state = state,
        // 触顶/触底橡胶回弹触感。
        modifier = Modifier.fillMaxSize().meloXEdgeOverscrollHaptic(state),
        contentPadding = PaddingValues(bottom = MeloXBottomContentClearance),
    ) {
        if (recommendations.isNotEmpty()) {
            item { Text(stringResource(R.string.search_trending), modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(recommendations, key = { it.id }) { p ->
                        val destinationKey = SearchDetailDestination.Netease(p.asSearchItem()).key
                        SearchCardVisibility(
                            destinationKey = destinationKey,
                            selectedKey = selectedKey,
                            modifier = Modifier.animateItem(),
                        ) {
                            val cardVisibilityScope = this
                            Column(
                                Modifier
                                    .width(160.dp)
                                    .clickable {
                                        onPlaylist(p)
                                    },
                            ) {
                                // 封面单独包一层 Box 承载 sharedElement：size/clip 放在
                                // sharedElement 之后，阴影之类的外部效果严禁进这条链。
                                Box(
                                    Modifier
                                        .meloXSearchSharedArtwork(
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = cardVisibilityScope,
                                            key = searchArtworkSharedKey(destinationKey),
                                        )
                                        .size(160.dp)
                                        .clip(RoundedCornerShape(14.dp)),
                                ) {
                                    AsyncImage(p.coverUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Text(p.name, modifier = Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        item { Text(stringResource(R.string.search_browse), modifier = Modifier.padding(start = 20.dp, top = 26.dp, bottom = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        items(SearchCategories.filter { it != "播客" || MeloXSettingsRuntime.podcastsEnabled }.chunked(categoryColumns)) { pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { category -> SearchCategoryCard(category, Modifier.weight(1f)) { onCategory(category) } }
                repeat(categoryColumns - pair.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ProviderSearchDiscovery(
    state: LazyListState = rememberLazyListState(),
    source: MusicSource,
    recommendations: List<MusicPlaylistSummary>,
    onPlaylist: (MusicPlaylistSummary) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    selectedKey: String? = null,
) {
    LazyColumn(
        state = state,
        // 触顶/触底橡胶回弹触感。
        modifier = Modifier.fillMaxSize().meloXEdgeOverscrollHaptic(state),
        contentPadding = PaddingValues(bottom = MeloXBottomContentClearance),
    ) {
        if (recommendations.isNotEmpty()) {
            item { Text(stringResource(R.string.search_trending), modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(recommendations, key = { "${it.id.sourceId.value}:${it.id.value}" }) { p ->
                        val destinationKey = SearchDetailDestination.Provider(ProviderSearchDestination.Playlist(p)).key
                        SearchCardVisibility(
                            destinationKey = destinationKey,
                            selectedKey = selectedKey,
                            modifier = Modifier.animateItem(),
                        ) {
                            val cardVisibilityScope = this
                            Column(Modifier.width(160.dp).clickable { onPlaylist(p) }) {
                                Box(
                                    Modifier
                                        .meloXSearchSharedArtwork(
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = cardVisibilityScope,
                                            key = searchArtworkSharedKey(destinationKey),
                                        )
                                        .size(160.dp)
                                        .clip(RoundedCornerShape(14.dp)),
                                ) {
                                    AsyncImage(p.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Text(p.title, modifier = Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp), contentAlignment = Alignment.Center) {
                Text("搜索 ${source.displayName} 的歌曲、歌单、专辑或歌手", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
            }
        }
    }
}

@Composable
private fun SearchCategoryCard(title: String, modifier: Modifier, onClick: () -> Unit) {
    val tint = when (title.hashCode().mod(5)) {
        0 -> Color(0xFFE76F51); 1 -> Color(0xFF7B61FF); 2 -> Color(0xFF2A9D8F); 3 -> Color(0xFFE84A8A); else -> Color(0xFF3A86FF)
    }
    Box(
        modifier
            .height(96.dp)
            .clip(MeloXShapes.compact)
            .background(tint)
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.BottomStart,
    ) { Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

@Composable
internal fun ProviderSearchSongResults(
    state: LazyListState = rememberLazyListState(),
    values: List<MusicTrack>,
    failures: List<UnifiedMusicService.SearchFailure>,
    showSource: Boolean,
    onPlay: (MusicTrack) -> Unit,
) {
    val context = LocalContext.current
    val providerDownloads = remember(context) { MeloXProviderDownloadStore.get(context) }
    val providers = remember(context) { MeloXMusicProviders.create(context) }
    var actionTrack by remember { mutableStateOf<MusicTrack?>(null) }
    LazyColumn(
        state = state,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = MeloXBottomContentClearance),
    ) {
        if (failures.isNotEmpty()) {
            item {
                Text(
                    failures.joinToString("；") { "${it.sourceId.displayName}：${it.message}" },
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        if (values.isEmpty()) {
            item { SearchEmptyInline(stringResource(R.string.search_no_songs)) }
        } else {
            items(values, key = { "provider:${it.id.sourceId.value}:${it.id.value}" }) { track ->
                SearchSwipeSongRow(
                    song = MeloXLegacyUiBridge.track(track),
                    onPlay = { onPlay(track) },
                    onMore = { actionTrack = track },
                    endAction = if (providers[track.id.sourceId]?.let { it is com.lladlam.melox.core.music.provider.DownloadCapability && (it.sourceId.builtIn != null || it.supports(com.lladlam.melox.core.music.provider.MusicCapability.Download)) } == true) MeloXSwipeAction(stringResource(R.string.search_download_local), MeloXSymbol.Download, Color(0xFF0EA5E9)) {
                        providerDownloads.start(track)
                    } else null,
                    sourceLabel = track.id.sourceId.displayName.takeIf { showSource },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
            }
        }
    }
    actionTrack?.let { track ->
        MeloXSongActionsOverlay(
            song = MeloXLegacyUiBridge.track(track),
            queue = values.map(MeloXLegacyUiBridge::track),
            visible = true,
            onDismiss = { actionTrack = null },
        )
    }
}

@Composable
private fun ProviderSearchMediaResults(
    state: LazyListState = rememberLazyListState(),
    values: List<ProviderSearchDestination>,
    onOpen: (ProviderSearchDestination) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    selectedKey: String? = null,
) {
    if (values.isEmpty()) { SearchEmpty(stringResource(R.string.search_no_results)); return }
    LazyColumn(
        state = state,
        // 触顶/触底橡胶回弹触感。
        modifier = Modifier.meloXEdgeOverscrollHaptic(state),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = MeloXBottomContentClearance),
    ) {
        items(values, key = ProviderSearchDestination::key) { item ->
            val artworkShape = if (item.kind == MeloXSearchKind.Artists) CircleShape else RoundedCornerShape(8.dp)
            SearchCardVisibility(
                destinationKey = item.key,
                selectedKey = selectedKey,
                modifier = Modifier.animateItem(),
            ) {
                val cardVisibilityScope = this
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(item) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .meloXSearchSharedArtwork(
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = cardVisibilityScope,
                                key = searchArtworkSharedKey(item.key),
                            )
                            .size(54.dp)
                            .clip(artworkShape),
                    ) {
                        AsyncImage(item.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp)
                        Text(
                            item.subtitle.ifBlank { item.kind.title },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                            fontSize = 13.sp,
                        )
                    }
                    MeloXActionIcon("›", Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .3f))
                }
            }
        }
    }
}

@Composable
private fun SearchSongResults(
    state: LazyListState = rememberLazyListState(),
    values: List<SearchSong>,
    onPlay: (SearchSong) -> Unit,
    onMore: (SearchSong) -> Unit,
    onLike: (SearchSong) -> Unit,
) {
    if (values.isEmpty()) { SearchEmpty(stringResource(R.string.search_no_songs)); return }
    LazyColumn(
        state = state,
        // 触顶/触底橡胶回弹触感。
        modifier = Modifier.meloXEdgeOverscrollHaptic(state),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = MeloXBottomContentClearance),
    ) {
        items(values, key = { it.id }) { song ->
            SearchSwipeSongRow(
                song = song,
                onPlay = { onPlay(song) },
                onMore = { onMore(song) },
                endAction = MeloXSwipeAction(stringResource(R.string.artist_add_library), MeloXSymbol.Heart, Color(0xFFFF3B30)) { onLike(song) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
        }
    }
}

@Composable
private fun SearchSwipeSongRow(
    song: SearchSong,
    onPlay: () -> Unit,
    onMore: (() -> Unit)?,
    endAction: MeloXSwipeAction?,
    sourceLabel: String? = null,
) {
    val context = LocalContext.current
    MeloXSwipeActionRow(
        startActions = listOf(
            MeloXSwipeAction(stringResource(R.string.player_play_next), MeloXSymbol.Next, Color(0xFF8E5AF7)) { PlaybackCommands.playNext(context, song) },
            MeloXSwipeAction(stringResource(R.string.artist_play_later), MeloXSymbol.Queue, Color(0xFFFF9F0A)) { PlaybackCommands.addToQueue(context, song) },
        ),
        endActions = listOfNotNull(endAction),
        startFullSwipeActionIndex = if (MeloXSettingsRuntime.swipeFullAction == MeloXSwipeFullAction.AddToQueue) 1 else 0,
        onClick = onPlay,
        onLongClick = onMore,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(song.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(
                    buildList {
                        // Source first, then the artist: aggregation picks one
                        // source per identical song, so the choice must be
                        // visible before the artist rather than after it.
                        sourceLabel?.let(::add)
                        add(song.artists)
                        song.album.takeIf(String::isNotBlank)?.let(::add)
                    }.joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                )
            }
        }
    }
}

@Composable
private fun SearchMediaResults(
    state: LazyListState = rememberLazyListState(),
    values: List<MeloXSearchMediaItem>,
    onOpen: (MeloXSearchMediaItem) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    selectedKey: String? = null,
) {
    if (values.isEmpty()) { SearchEmpty(stringResource(R.string.search_no_results)); return }
    // 播客行内直接短路 launch（OneShot），需要 Activity context。
    val context = LocalContext.current
    LazyColumn(
        state = state,
        // 触顶/触底橡胶回弹触感。
        modifier = Modifier.meloXEdgeOverscrollHaptic(state),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = MeloXBottomContentClearance),
    ) {
        items(values, key = { "${it.kind}-${it.id}" }) { item ->
            val destinationKey = SearchDetailDestination.Netease(item).key
            val artworkShape = if (item.kind == MeloXSearchKind.Artists || item.kind == MeloXSearchKind.Users) CircleShape else RoundedCornerShape(8.dp)
            SearchCardVisibility(
                destinationKey = destinationKey,
                selectedKey = selectedKey,
                modifier = Modifier.animateItem(),
            ) {
                val cardVisibilityScope = this
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(item) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .meloXSearchSharedArtwork(
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = cardVisibilityScope,
                                key = searchArtworkSharedKey(destinationKey),
                            )
                            .size(54.dp)
                            .clip(artworkShape),
                    ) {
                        AsyncImage(item.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp)
                        Text(item.subtitle.ifBlank { if (item.trackCount > 0) "${item.trackCount} 首" else item.kind.title }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), fontSize = 13.sp)
                    }
                    MeloXActionIcon("›", Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .3f))
                }
            }
        }
    }
}

/**
 * **整页子页在「推入动画期间」的轻量占位。**
 *
 * ### 为什么需要它（2026-10-09，修「搜索-浏览类别-播客」一顿一顿）
 * 推入动画的目标帧率是 120fps（8.3ms/帧预算）——
 * 任何重脚手架只要落在动画窗口里就会把帧顶到 16.7ms+，读成顿挫。而播客首屏自带
 * **嵌套 `SharedTransitionLayout` + `PullToRefreshBox` + 无限进度动画**，一组合就占住 UI 线程。
 * 占位里**只有**一个（宿主已画的）背板 + 一个进度指示器 ⇒ 逐帧成本可忽略。
 * 推入落定后由真页面替换（判据见 `SearchScreen` 的 `subPageComposed`）。
 *
 * ⛔ 只给「推入期间没有任何 sharedElement 两端」的子页用 —— 分类页 / 详情覆盖层必须全程在场，
 *   晚组合一步共享元素就配不上对、morph 直接断。
 */
@Composable
private fun MeloXSubPagePlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SearchAccent)
    }
}

@Composable
private fun SearchCategoryPage(
    title: String,
    values: List<NeteasePlaylistSummary>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onPlaylist: (NeteasePlaylistSummary) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    selectedKey: String? = null,
) {
    Column(Modifier.fillMaxSize().padding(top = rememberMeloXStableStatusBarHeight()).padding(top = 16.dp)) {
        SearchDetailHeader(title, onBack)
        when {
            title == "播客" -> SearchEmpty(stringResource(R.string.search_podcast_hint))
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> SearchEmpty(error)
            values.isEmpty() -> SearchEmpty(stringResource(R.string.melox_state_empty))
            else -> LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = MeloXBottomContentClearance)) {
                items(values, key = { it.id }) { p ->
                    val destinationKey = SearchDetailDestination.Netease(p.asSearchItem()).key
                    SearchCardVisibility(
                        destinationKey = destinationKey,
                        selectedKey = selectedKey,
                        modifier = Modifier.animateItem(),
                    ) {
                        val cardVisibilityScope = this
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPlaylist(p) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .meloXSearchSharedArtwork(
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = cardVisibilityScope,
                                        key = searchArtworkSharedKey(destinationKey),
                                    )
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(9.dp)),
                            ) {
                                AsyncImage(p.coverUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text("${p.trackCount} 首歌曲", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCollectionDetail(
    destination: SearchDetailDestination,
    universal: NeteaseUniversalSearchClient,
    library: NeteaseLibraryClient,
    providerRegistry: com.lladlam.melox.core.music.provider.MusicProviderRegistry,
    onBack: () -> Unit,
    // 覆盖层打开时把宿主的共享元素作用域与配对 key 透传给详情 hero，封面才能
    // 从结果卡片一镜到底 morph 进来/回去。不传则保持「自建作用域 + 淡入」。
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    artworkSharedKey: String? = null,
) {
    when (destination) {
        is SearchDetailDestination.Netease -> if (destination.value.kind == MeloXSearchKind.Playlists) {
            val value = destination.value
            MeloXUnifiedPlaylistDetailScreen(
                playlist = com.lladlam.melox.core.library.NeteasePlaylistSummary(
                    id = value.id,
                    name = value.title,
                    coverUrl = value.artworkUrl,
                    trackCount = value.trackCount,
                    creatorName = value.subtitle,
                ),
                onBack = onBack,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                artworkSharedKey = artworkSharedKey,
            )
            return
        }
        is SearchDetailDestination.Provider -> {
            val value = destination.value
            if (value is ProviderSearchDestination.Playlist) {
                MeloXUnifiedPlaylistDetailScreen(
                    playlist = MeloXLegacyUiBridge.playlist(value.value),
                    onBack = onBack,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    artworkSharedKey = artworkSharedKey,
                )
                return
            }
            if (value is ProviderSearchDestination.Album) {
                MeloXUnifiedProviderAlbumDetailScreen(
                    album = value.value,
                    onBack = onBack,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    artworkSharedKey = artworkSharedKey,
                )
                return
            }
            if (value is ProviderSearchDestination.Artist) {
                MeloXProviderArtistDetailScreen(artist = value.value, onBack = onBack)
                return
            }
        }
    }
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val operations = remember(appContext) { NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) }) }
    var songs by remember(destination.key) { mutableStateOf<List<SearchSong>>(emptyList()) }
    var providerTracks by remember(destination.key) { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var loading by remember(destination.key) { mutableStateOf(true) }
    var error by remember(destination.key) { mutableStateOf<String?>(null) }
    var selectedActionSong by remember(destination.key) { mutableStateOf<SearchSong?>(null) }

    LaunchedEffect(destination.key) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                when (destination) {
                    is SearchDetailDestination.Netease -> {
                        val item = destination.value
                        val values = if (item.kind == MeloXSearchKind.Playlists) {
                            library.playlistDetail(item.id).songs
                        } else {
                            universal.collectionSongs(item)
                        }
                        values to emptyList<MusicTrack>()
                    }
                    is SearchDetailDestination.Provider -> {
                        val item = destination.value
                        val provider = providerRegistry[item.source] ?: error("音源已禁用或卸载：${item.source.value}")
                        val tracks = when (item) {
                            is ProviderSearchDestination.Playlist -> {
                                val capability = provider as? PlaylistCapability
                                    ?: throw IllegalStateException("${item.source.displayName} 当前不提供歌单详情")
                                capability.loadAllPlaylistTracks(item.value, pageSize = 200).tracks
                            }
                            is ProviderSearchDestination.Album -> {
                                val capability = provider as? AlbumCapability
                                    ?: throw IllegalStateException("${item.source.displayName} 当前不提供专辑详情")
                                capability.albumDetail(item.value, page = 1, pageSize = 150).tracks
                            }
                            is ProviderSearchDestination.Artist -> {
                                val capability = provider as? ArtistCapability
                                    ?: throw IllegalStateException("${item.source.displayName} 当前不提供歌手详情")
                                capability.artistDetail(item.value, page = 1, pageSize = 150).tracks
                            }
                        }
                        emptyList<SearchSong>() to tracks
                    }
                }
            }
        }.onSuccess { (neteaseSongs, commonTracks) ->
            songs = neteaseSongs
            providerTracks = commonTracks
        }.onFailure { error = it.message ?: "内容加载失败" }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(top = rememberMeloXStableStatusBarHeight()).padding(top = 16.dp)) {
        SearchDetailHeader(destination.title, onBack)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = MeloXBottomContentClearance)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        destination.artworkUrl,
                        null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(210.dp).clip(if (destination.kind == MeloXSearchKind.Artists) CircleShape else RoundedCornerShape(15.dp)),
                    )
                    Text(
                        destination.title,
                        modifier = Modifier.padding(top = 16.dp),
                        fontSize = 23.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (destination.subtitle.isNotBlank()) {
                        Text(destination.subtitle, modifier = Modifier.padding(top = 5.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                    }
                    val hasTracks = songs.isNotEmpty() || providerTracks.isNotEmpty()
                    if (hasTracks) {
                        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SearchPlayButton(stringResource(R.string.search_shuffle)) {
                                if (providerTracks.isNotEmpty()) {
                                    val shuffled = providerTracks.shuffled()
                                    shuffled.firstOrNull()?.let { ProviderPlaybackCommands.playQueue(context, shuffled, it.id) }
                                } else {
                                    val shuffled = songs.shuffled()
                                    shuffled.firstOrNull()?.let { PlaybackCommands.playQueue(context, shuffled, it.id) }
                                }
                            }
                            SearchPlayButton(stringResource(R.string.action_play)) {
                                if (providerTracks.isNotEmpty()) {
                                    providerTracks.firstOrNull()?.let { ProviderPlaybackCommands.playQueue(context, providerTracks, it.id) }
                                } else {
                                    songs.firstOrNull()?.let { PlaybackCommands.playQueue(context, songs, it.id) }
                                }
                            }
                        }
                    }
                }
            }
            when {
                loading -> item { Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                error != null -> item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
                providerTracks.isNotEmpty() -> items(providerTracks, key = { "detail:${it.id.sourceId.value}:${it.id.value}" }) { track ->
                    SearchSwipeSongRow(
                        song = MeloXLegacyUiBridge.track(track),
                        onPlay = { ProviderPlaybackCommands.playQueue(context, providerTracks, track.id) },
                        onMore = { selectedActionSong = MeloXLegacyUiBridge.track(track) },
                        endAction = MeloXSwipeAction(stringResource(R.string.search_download_local), MeloXSymbol.Download, Color(0xFF0EA5E9)) {
                            MeloXProviderDownloadStore.get(context).start(track)
                        },
                        sourceLabel = track.id.sourceId.displayName,
                    )
                }
                else -> items(songs, key = { it.id }) { song ->
                    SearchSwipeSongRow(
                        song = song,
                        onPlay = { PlaybackCommands.playQueue(context, songs, song.id) },
                        onMore = { selectedActionSong = song },
                        endAction = MeloXSwipeAction(stringResource(R.string.artist_add_library), MeloXSymbol.Heart, Color(0xFFFF3B30)) {
                            scope.launch {
                                runCatching { operations.setSongLiked(song.id, true) }
                                    .onFailure { error = it.message ?: "添加到资料库失败" }
                            }
                        },
                    )
                }
            }
        }
    }
    selectedActionSong?.let { song ->
        MeloXSongActionsOverlay(
            song = song,
            queue = songs,
            visible = true,
            onDismiss = { selectedActionSong = null },
        )
    }
}

@Composable
private fun SearchDetailHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        MeloXGlassButton(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(11.dp),
        ) {
            MeloXSymbolIcon(MeloXSymbol.ChevronLeft, Modifier.fillMaxSize(), MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(12.dp))
        Text(title, Modifier.weight(1f), style = MeloXTypography.title2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchPlayButton(title: String, onClick: () -> Unit) {
    MeloXGlassButton(
        onClick = onClick,
        modifier = Modifier.height(44.dp).width(120.dp),
        style = MeloXGlassButtonStyle.BorderedProminent,
        shape = MeloXShapes.capsule,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun SearchEmpty(message: String) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
    }
}

@Composable
private fun SearchEmptyInline(message: String) {
    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
    }
}

private fun parseSongLink(value: String): Long? {
    val patterns = listOf(Regex("[?&]id=(\\d+)"), Regex("/song/(\\d+)"))
    return patterns.firstNotNullOfOrNull { it.find(value)?.groupValues?.getOrNull(1)?.toLongOrNull() }
}

private fun NeteasePlaylistSummary.asSearchItem() = MeloXSearchMediaItem(
    id = id,
    kind = MeloXSearchKind.Playlists,
    title = name,
    subtitle = creatorName,
    artworkUrl = coverUrl,
    trackCount = trackCount,
)
