package com.lladlam.melox.ui.discovery

import com.lladlam.melox.ui.state.rememberRetainedPageState

import com.lladlam.melox.ui.layout.LocalMeloXListDetail
import com.lladlam.melox.ui.layout.rememberMeloXDetailSelection
import com.lladlam.melox.core.music.provider.loadAllMusicPages

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import org.json.JSONObject
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.account.NeteaseAccountProfile
import com.lladlam.melox.R
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.account.rememberNeteaseSessionStore
import com.lladlam.melox.core.library.NeteaseHomeContent
import com.lladlam.melox.core.library.NeteaseLibraryCache
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.model.MusicAccountSummary
import com.lladlam.melox.core.music.model.MusicHomeFeed
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicRankingSummary
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.provider.HomeFeedCapability
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.core.music.provider.MeloXLegacyUiBridge
import com.lladlam.melox.core.music.provider.PlaylistCapability
import com.lladlam.melox.core.music.provider.RankingCapability
import com.lladlam.melox.core.music.provider.UserLibraryCapability
import com.lladlam.melox.core.recommendation.LocalRecommendationStore
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.playback.ProviderPlaybackCommands
import com.lladlam.melox.ui.animation.MeloXMotion
import com.lladlam.melox.ui.animation.MeloXPageBoundsTransform
import com.lladlam.melox.ui.animation.meloXPageRedirectSpec
import com.lladlam.melox.ui.MeloXInterruptiblePage
import com.lladlam.melox.ui.animation.meloXSettledMillis
import com.lladlam.melox.ui.account.MeloXAccountActivity
import com.lladlam.melox.ui.collection.MeloXCollectionDetailActivity
import com.lladlam.melox.ui.glass.MeloXActionIcon
import com.lladlam.melox.ui.glass.MeloXGlassCard
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXShapes
import com.lladlam.melox.ui.glass.MeloXSystemColors
import com.lladlam.melox.ui.glass.MeloXTypography
import com.lladlam.melox.ui.glass.meloXContentSurface
import com.lladlam.melox.ui.glass.meloXGlassSurface
import com.lladlam.melox.ui.glass.MeloXIosTopBar
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXSymbolVariant
import com.lladlam.melox.ui.library.MeloXDetailPalette
import com.lladlam.melox.ui.library.MeloXDetailPaletteProvider
import com.lladlam.melox.ui.player.MeloXFlowingLightBackdrop
import com.lladlam.melox.ui.podcast.MeloXPodcastScreen
import com.lladlam.melox.ui.library.MeloXUnifiedPlaylistDetailScreen
import com.lladlam.melox.ui.library.MeloXUnifiedSongListDetailScreen
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.meloXEdgeOverscrollHaptic
import com.lladlam.melox.ui.meloXPageRecede
import com.lladlam.melox.ui.layout.rememberMeloXStableStatusBarHeight
import com.lladlam.melox.ui.layout.rememberMeloXWindowInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Accent = Color(0xFFFF3147)
private val Categories = listOf("推荐歌单", "排行榜", "精品歌单", "播客", "全部", "华语", "欧美", "流行", "摇滚", "民谣", "电子", "轻音乐", "影视原声", "ACG")

// 发现页可折叠头部：上滑时大标题缩小、整体压缩，标签行随头部高度收缩自然上移吸顶。
// 防透出：背景恒为页面底色（不透明），内容滚动进入头部区域时始终被盖住，绝不透出。
// 防抽搐：① 折叠进度直接由网格自身 firstVisibleItemScrollOffset 驱动（与 MeloXPinnedListPage
//        同款，不依赖嵌套滚动连接，杜绝头部与内容错位），越过首屏后强制保持折叠、不回弹；
//        ② 标题字号固定、仅用 graphicsLayer 缩放（绘制期，不触发文字重排版）。
private val ExploreHeaderTopInsetExpanded = 18.dp
private val ExploreHeaderTopInsetCollapsed = 10.dp
private val ExploreTitleBlockExpanded = 62.dp
private val ExploreTitleBlockCollapsed = 38.dp
private val ExploreTitleGapExpanded = 16.dp
private val ExploreTitleGapCollapsed = 8.dp
private val ExploreTabsBlock = 66.dp // 38 按钮 + 上下各 14 的 LazyRow 内边距
private val EXPLORE_HEADER_EXPANDED =
    ExploreHeaderTopInsetExpanded + ExploreTitleBlockExpanded + ExploreTitleGapExpanded + ExploreTabsBlock
private val EXPLORE_HEADER_COLLAPSED =
    ExploreHeaderTopInsetCollapsed + ExploreTitleBlockCollapsed + ExploreTitleGapCollapsed + ExploreTabsBlock
// 折叠行程（滚动距离）：越大折叠越「绵」，与标题/标签的视觉缩放解耦。
private val EXPLORE_COLLAPSE_SCROLL = 120.dp

// 首页/发现页覆盖层过渡的收尾余量：动画时长之外再留一点，等 sharedElement 的
// overlay 层真正收干净，再解锁并放开卡片。给少了会在切换瞬间看到 overlay 残影。
private const val DiscoveryOverlaySettleMillis = 60L

/** Home cards animate/press via navigation and haptics; disable the rectangular ripple. */
private object HomeNoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun hashCode(): Int = 0
    override fun equals(other: Any?): Boolean = other === this
}

private sealed interface DiscoveryTrack {
    val key: String
    val title: String
    val artist: String
    val artworkUrl: String?

    data class Netease(val song: SearchSong) : DiscoveryTrack {
        override val key: String = "netease:${song.id}"
        override val title: String = song.name
        override val artist: String = song.artists
        override val artworkUrl: String? = song.artworkUrl
    }

    data class Provider(val track: MusicTrack) : DiscoveryTrack {
        override val key: String = "${track.id.sourceId.value}:${track.id.value}"
        override val title: String = track.title
        override val artist: String = track.artistText
        override val artworkUrl: String? = track.artworkUrl
    }
}

private sealed interface DiscoveryCollection {
    val key: String
    val title: String
    val artworkUrl: String?
    val creatorName: String
    val playCount: Long
    val description: String?

    data class Netease(val playlist: NeteasePlaylistSummary) : DiscoveryCollection {
        override val key: String = "netease-playlist:${playlist.id}"
        override val title: String = playlist.name
        override val artworkUrl: String? = playlist.coverUrl
        override val creatorName: String = playlist.creatorName
        override val playCount: Long = playlist.playCount
        override val description: String? = playlist.description
    }

    data class ProviderPlaylist(val playlist: MusicPlaylistSummary) : DiscoveryCollection {
        override val key: String = "${playlist.id.sourceId.value}-playlist:${playlist.id.value}"
        override val title: String = playlist.title
        override val artworkUrl: String? = playlist.artworkUrl
        override val creatorName: String = playlist.creatorName.orEmpty()
        override val playCount: Long = playlist.playCount ?: 0L
        override val description: String? = playlist.description
    }

    data class ProviderRanking(val ranking: MusicRankingSummary) : DiscoveryCollection {
        override val key: String = "${ranking.id.sourceId.value}-ranking:${ranking.id.value}"
        override val title: String = ranking.title
        override val artworkUrl: String? = ranking.artworkUrl
        override val creatorName: String = ranking.id.sourceId.displayName
        override val playCount: Long = 0L
        override val description: String? = ranking.subtitle
    }
}


/** Serializes only NetEase cards; provider collections are not persisted. */
private fun encodeDiscoveryCollection(collection: DiscoveryCollection?): String? =
    (collection as? DiscoveryCollection.Netease)?.playlist?.let { playlist ->
        JSONObject()
            .put("id", playlist.id)
            .put("name", playlist.name)
            .put("cover", playlist.coverUrl.orEmpty())
            .put("count", playlist.trackCount)
            .put("creator", playlist.creatorName)
            .put("playCount", playlist.playCount)
            .put("description", playlist.description.orEmpty())
            .toString()
    }

private fun decodeDiscoveryCollection(raw: String?): DiscoveryCollection? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val value = JSONObject(raw)
        DiscoveryCollection.Netease(
            NeteasePlaylistSummary(
                id = value.getLong("id"),
                name = value.optString("name", "歌单"),
                coverUrl = value.optString("cover").takeIf(String::isNotBlank),
                trackCount = value.optInt("count"),
                creatorName = value.optString("creator"),
                playCount = value.optLong("playCount"),
                description = value.optString("description").takeIf(String::isNotBlank),
            ),
        )
    }.getOrNull()
}

/** Restores a NetEase playlist card after activity/process recreation. */
private val DiscoveryCollectionSaver: Saver<DiscoveryCollection?, String> = Saver(
    save = { encodeDiscoveryCollection(it) },
    restore = { decodeDiscoveryCollection(it) },
)

private const val LastCollectionKey = "home_last_collection"
private const val LastCollectionAtKey = "home_last_collection_at"
private const val LastCollectionTtlMs = 30L * 60L * 1000L

private fun rememberLastCollection(context: android.content.Context, collection: DiscoveryCollection?) {
    val prefs = context.applicationContext.getSharedPreferences("melox_app_settings", android.content.Context.MODE_PRIVATE)
    val encoded = encodeDiscoveryCollection(collection)
    if (encoded == null) {
        prefs.edit().remove(LastCollectionKey).remove(LastCollectionAtKey).apply()
    } else {
        prefs.edit()
            .putString(LastCollectionKey, encoded)
            .putLong(LastCollectionAtKey, System.currentTimeMillis())
            .apply()
    }
}

private fun restoreLastCollection(context: android.content.Context): DiscoveryCollection? {
    val prefs = context.applicationContext.getSharedPreferences("melox_app_settings", android.content.Context.MODE_PRIVATE)
    val savedAt = prefs.getLong(LastCollectionAtKey, 0L)
    if (savedAt <= 0L || System.currentTimeMillis() - savedAt > LastCollectionTtlMs) {
        prefs.edit().remove(LastCollectionKey).remove(LastCollectionAtKey).apply()
        return null
    }
    return decodeDiscoveryCollection(prefs.getString(LastCollectionKey, null))
}

private data class HomeAccountUi(
    val name: String,
    val avatarUrl: String?,
    val subtitle: String,
    val onClick: (() -> Unit)? = null,
)

private sealed interface HomeBlock {
    data object QuickActions : HomeBlock
    data class Collections(
        val title: String,
        val trailing: String,
        val values: List<DiscoveryCollection>,
    ) : HomeBlock
    data class Tracks(
        val title: String,
        val trailing: String,
        val values: List<DiscoveryTrack>,
    ) : HomeBlock
    data class Podcasts(val values: List<com.lladlam.melox.core.library.NeteaseHomePodcast>) : HomeBlock
}

@Composable
fun MeloXHomeScreen(
    source: MusicSource = MusicSource.Netease,
    onOpenTool: (String) -> Unit = {},
) {
    if (source == MusicSource.Netease) {
        NeteaseHomeDataScreen(onOpenTool)
    } else {
        ProviderHomeDataScreen(source, onOpenTool)
    }
}

@Composable
private fun NeteaseHomeDataScreen(onOpenTool: (String) -> Unit) {
    val onDetailSelected = rememberMeloXDetailSelection()
    val hostContext = LocalContext.current
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { NeteaseLibraryCache(context) }
    val client by remember(context) {
        lazy { NeteaseLibraryClient({ NeteaseSessionStore.readCookie(context) }) }
    }
    val scope = rememberCoroutineScope()
    val session = rememberNeteaseSessionStore()
    var content by rememberRetainedPageState(session.cookie, MeloXSettingsRuntime.musicArea, MeloXSettingsRuntime.podcastsEnabled) { mutableStateOf<NeteaseHomeContent?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedCollection by rememberSaveable(stateSaver = DiscoveryCollectionSaver) { mutableStateOf(restoreLastCollection(context)) }
    // 内容与可见性分开持有：返回时只把 selectedCollection 置空（让列表卡片重新进入过渡），
    // overlayCollection 保留，退出过渡期间详情仍被组合 → 封面才能一镜到底地 morph 回卡片。
    var overlayCollection by remember { mutableStateOf<DiscoveryCollection?>(null) }
    // Activity/process recreation restores selectedCollection; the transient
    // overlay content must be repopulated or the detail would render nothing.
    LaunchedEffect(selectedCollection) {
        if (selectedCollection != null && overlayCollection == null) {
            overlayCollection = selectedCollection
        }
        rememberLastCollection(context, selectedCollection)
    }
    var songList by rememberSaveable(stateSaver = HomeSongListSaver) { mutableStateOf<HomeSongList?>(null) }
    // 「每日推荐 / 热歌榜 / 私人雷达」歌单页的内容持有物。与 selectedCollection 同理：
    // 可见性看 songList（返回时置空，让底下首页复位），内容另存 overlaySongList、返回时不清。
    // 两边共用一个 state 的话，返回瞬间 content lambda 取到的是 null，AnimatedVisibility
    // 的退场过渡在演一个空壳 ⇒ 观感就是「没有返回动画」。
    var overlaySongList by remember { mutableStateOf<HomeSongList?>(null) }
    LaunchedEffect(songList) {
        if (songList != null && overlaySongList == null) overlaySongList = songList
    }
    var activeAction by remember { mutableStateOf<String?>(null) }
    // Reopening a loaded quick action (daily/ranked/radar) must not depend on a
    // second upstream request: the first request may be rate-limited by NetEase.
    var quickActionCache by rememberRetainedPageState(session.cookie) { mutableStateOf<Map<String, List<SearchSong>>>(emptyMap()) }
    var localRecommendations by rememberRetainedPageState { mutableStateOf(emptyList<com.lladlam.melox.core.recommendation.LocalRecommendationItem>()) }
    var localCandidates by rememberRetainedPageState { mutableStateOf(emptyList<MusicTrack>()) }
    val homeCacheKey = "${session.cookie.hashCode()}_${MeloXSettingsRuntime.musicArea}_${MeloXSettingsRuntime.podcastsEnabled}"

    fun refresh(forceServer: Boolean = false) {
        if (refreshing) return
        scope.launch {
            refreshing = true
            runCatching {
                if (session.isLoggedIn && session.profile == null) session.refreshProfile(force = true)
                withContext(Dispatchers.IO) {
                    client.homeContent(
                        area = MeloXSettingsRuntime.musicArea,
                        userId = session.profile?.userId,
                        podcastsEnabled = MeloXSettingsRuntime.podcastsEnabled,
                        refresh = forceServer,
                    )
                }
            }.onSuccess {
                content = it
                cache.saveHomeContent(homeCacheKey, it)
                error = null
            }.onFailure { error = it.message ?: "首页加载失败" }
            refreshing = false
        }
    }

    LaunchedEffect(homeCacheKey) {
        val localData = withContext(Dispatchers.IO) {
            LocalRecommendationStore.readRecommendations(context) to
                LocalRecommendationStore.readCandidateTracks(context)
        }
        localRecommendations = localData.first
        localCandidates = localData.second
        if (content == null) content = cache.loadHomeContent(homeCacheKey)
        if (session.isLoggedIn) session.refreshProfile()
        if (content == null || NeteaseLibraryCache.beginHomeColdStartRefresh(homeCacheKey, cache.cacheDir)) refresh(false)
    }

    val blocks = content?.let { value ->
        buildList<HomeBlock> {
            if (localRecommendations.isNotEmpty()) {
                val orderedCandidates = localRecommendations.mapNotNull { recommendation ->
                    localCandidates.firstOrNull { candidate ->
                        candidate.title == recommendation.title && candidate.artistText == recommendation.artist
                    }?.let { DiscoveryTrack.Provider(it) }
                }
                if (orderedCandidates.isNotEmpty()) add(HomeBlock.Tracks(stringResource(R.string.home_for_you_melox), stringResource(R.string.home_local_algorithm), orderedCandidates))
            }
            MeloXSettingsRuntime.homeSectionOrder.forEach { section ->
                when (section) {
                    "QuickActions" -> if (MeloXSettingsRuntime.homeQuickActionsEnabled) add(HomeBlock.QuickActions)
                    "Playlists" -> if (MeloXSettingsRuntime.homePlaylistsEnabled && value.playlists.isNotEmpty()) {
                        add(HomeBlock.Collections("每日推荐", stringResource(R.string.home_pull_to_refresh), value.playlists.map { DiscoveryCollection.Netease(it) }))
                    }
                    "NewSongs" -> if (MeloXSettingsRuntime.homeNewSongsEnabled && value.newSongs.isNotEmpty()) {
                        add(HomeBlock.Tracks(stringResource(R.string.home_for_you), stringResource(R.string.home_new_songs), value.newSongs.map { DiscoveryTrack.Netease(it) }))
                    }
                }
            }
            if (value.recentlyTrending.isNotEmpty()) add(HomeBlock.Tracks(stringResource(R.string.home_recent_trending), stringResource(R.string.home_from_netease_home), value.recentlyTrending.map { DiscoveryTrack.Netease(it) }))
            if (value.tailoredSongs.isNotEmpty()) add(HomeBlock.Tracks(stringResource(R.string.home_based_on_taste), stringResource(R.string.home_personalized), value.tailoredSongs.map { DiscoveryTrack.Netease(it) }))
            if (value.chartPlaylists.isNotEmpty()) add(HomeBlock.Collections("排行榜", stringResource(R.string.home_netease_charts), value.chartPlaylists.map { DiscoveryCollection.Netease(it) }))
            if (value.radarPlaylists.isNotEmpty()) add(HomeBlock.Collections("私人雷达", stringResource(R.string.home_your_radar), value.radarPlaylists.map { DiscoveryCollection.Netease(it) }))
            if (value.personalPlaylists.isNotEmpty()) add(HomeBlock.Collections(stringResource(R.string.home_my_playlists), stringResource(R.string.home_kept_for_you), value.personalPlaylists.map { DiscoveryCollection.Netease(it) }))
            if (value.regionalSongs.isNotEmpty()) add(HomeBlock.Tracks("${MeloXSettingsRuntime.musicArea}最近热门", stringResource(R.string.home_area_picks), value.regionalSongs.map { DiscoveryTrack.Netease(it) }))
            if (value.roamingSongs.isNotEmpty()) add(HomeBlock.Tracks("私人漫游", stringResource(R.string.home_explore_more), value.roamingSongs.map { DiscoveryTrack.Netease(it) }))
            if (value.similarSongs.isNotEmpty()) add(HomeBlock.Tracks("相似歌曲", stringResource(R.string.home_from_now_playing), value.similarSongs.map { DiscoveryTrack.Netease(it) }))
            if (
                value.podcasts.isNotEmpty() &&
                MeloXSettingsRuntime.podcastsEnabled &&
                MeloXSettingsRuntime.podcastsHomePlacement
            ) add(HomeBlock.Podcasts(value.podcasts))
        }
    }

    val account = session.profile?.let { profile ->
        HomeAccountUi(
            name = profile.nickname.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) } ?: stringResource(R.string.home_netease_user),
            avatarUrl = profile.avatarUrl,
            subtitle = profile.signature
                ?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
                ?: stringResource(R.string.home_profile_subtitle),
            onClick = { MeloXAccountActivity.launch(hostContext, profile.userId) },
        )
    }

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        val sharedScope = this
        // 覆盖层内容生命周期：只做「退场跑完再释放 overlayCollection」——
        // 退场期间详情子树仍在组合，封面才有机会一镜到底 morph 回卡片。
        // ⚠ 不再上锁/排队：退出途中再点卡会让 selectedCollection 立刻变非空 ⇒ 本 effect 被
        //   取消重启，内容不被清、直接续上；滑动改向由 MeloXInterruptiblePage 的弹簧接管。
        LaunchedEffect(selectedCollection) {
            if (selectedCollection == null && overlayCollection != null) {
                delay(meloXSettledMillis(MeloXMotion.PageExitMillis, DiscoveryOverlaySettleMillis))
                overlayCollection = null
            }
        }
        // 歌单页退场跑完再释放内容，理由同 overlayCollection。
        LaunchedEffect(songList) {
            if (songList == null && overlaySongList != null) {
                delay(meloXSettledMillis(MeloXMotion.PageExitMillis, DiscoveryOverlaySettleMillis))
                overlaySongList = null
            }
        }
        // ⚠ **hero push 时旧页也左移让出 1/3**（2026-10-02）：与推入页走同一条弹簧
        //   （[meloXPagePushSpec] / [meloXPageRedirectSpec] 同参数）⇒ 两页严格互补，
        //   观感与「无 hero 的整页推入」完全一致，区别只是多了一个封面 morph。
        //   ⚠ 旧页位移必须走 [meloXPageRecede] 的**布局期** place：共享元素 morph 的 bounds
        //     取自 lookahead 布局坐标，走 graphicsLayer 的话封面不跟随、半路与卡片脱开。
        val homeRecede = remember { Animatable(0f) }
        val homeCollectionOpen = selectedCollection != null
        LaunchedEffect(homeCollectionOpen) {
            val target = if (homeCollectionOpen) 1f else 0f
            if (homeRecede.value == target) return@LaunchedEffect // 首帧 / 已到位：不重播
            homeRecede.animateTo(target, meloXPageRedirectSpec())
        }
        Box(Modifier.fillMaxSize().meloXPageRecede { homeRecede.value }) {
        CompositionLocalProvider(LocalIndication provides HomeNoIndication) {
        MeloXHomeLayout(
            source = MusicSource.Netease,
            account = account,
            blocks = blocks,
            refreshing = refreshing,
            error = error,
            onRefresh = { refresh(true) },
            activeAction = activeAction,
            onQuickAction = quickAction@ { action ->
                when (action) {
                    "听歌识曲" -> {
                        onOpenTool("Recognition")
                        return@quickAction
                    }
                    "私信" -> {
                        onOpenTool("Messages")
                        return@quickAction
                    }
                    "播客" -> {
                        onOpenTool("Podcasts")
                        return@quickAction
                    }
                    "下载" -> {
                        onOpenTool("Downloads")
                        return@quickAction
                    }
                    "云盘" -> {
                        onOpenTool("Cloud")
                        return@quickAction
                    }
                }
                activeAction = action
                scope.launch {
                    quickActionCache[action]?.takeIf(List<SearchSong>::isNotEmpty)?.let { songs ->
                        if (action in HomeSongListActions) {
                            val list = HomeSongList(
                                title = action,
                                subtitle = "网易云音乐 · ${songs.size} 首歌曲",
                                artworkUrl = songs.firstOrNull()?.artworkUrl,
                                songs = songs,
                            )
                            onDetailSelected()
                            songList = list
                            overlaySongList = list
                        } else {
                            songs.firstOrNull()?.let {
                                PlaybackCommands.playQueue(context, songs, it.id, heartMode = action == "心动模式")
                            }
                        }
                        activeAction = null
                        return@launch
                    }
                    runCatching {
                        when (action) {
                            "每日推荐" -> client.dailyRecommendedSongs()
                            "热歌榜" -> client.hotSongs()
                            "私人漫游" -> client.personalFm(explore = true)
                            "私人雷达" -> {
                                val uid = session.profile?.userId ?: throw IllegalStateException("请先登录网易云音乐")
                                val snapshot = client.snapshot(uid)
                                val radar = snapshot.playlists.firstOrNull { it.name.contains("雷达") }
                                    ?: throw IllegalStateException("当前账号没有可用的私人雷达")
                                client.playlistDetail(radar.id).songs
                            }
                            "相似歌曲" -> PlaybackCommands.currentSongId()?.let { client.similarSongsBlocking(it) }
                                ?: throw IllegalStateException("请先播放一首歌曲")
                            "心动模式" -> {
                                val userId = session.profile?.userId ?: throw IllegalStateException("请先登录网易云音乐")
                                val snapshot = client.snapshot(userId)
                                val seed = snapshot.likedSongs.randomOrNull() ?: throw IllegalStateException("收藏歌曲为空")
                                val playlistId = snapshot.likedPlaylistId ?: throw IllegalStateException("没有找到“我喜欢的音乐”歌单")
                                client.intelligenceModeSongs(seed.id, playlistId)
                            }
                            else -> emptyList()
                        }
                    }.onSuccess { songs ->
                        if (songs.isNotEmpty()) quickActionCache = quickActionCache + (action to songs)
                        if (action in HomeSongListActions && songs.isNotEmpty()) {
                            val list = HomeSongList(
                                title = action,
                                subtitle = "网易云音乐 · ${songs.size} 首歌曲",
                                artworkUrl = songs.firstOrNull()?.artworkUrl,
                                songs = songs,
                            )
                            onDetailSelected()
                            songList = list
                            overlaySongList = list
                        } else {
                            songs.firstOrNull()?.let {
                                PlaybackCommands.playQueue(context, songs, it.id, heartMode = action == "心动模式")
                            } ?: run { error = "没有可播放的推荐歌曲" }
                        }
                    }.onFailure { error = it.message ?: "$action 加载失败" }
                    activeAction = null
                }
            },
            onCollection = {
                onDetailSelected()
                // 立即写入，不排队：退场途中再点卡时由 MeloXInterruptiblePage 的弹簧在途改向
                // （继承当前位移与速度），点下去即响应 —— 这正是「退出后能立刻点击」。
                selectedCollection = it
                overlayCollection = it
            },
            selectedCollectionKey = selectedCollection?.key,
            sharedTransitionScope = sharedScope,
        )
        }
        } // Box: 被 hero 推入页盖住的旧页（退让 1/3 屏宽）

        // 首页留在底下。歌单页只是叠上去，打开时从右侧滑入，预见式返回滑开时露出首页。
        HomeOverlayPage(
            shown = selectedCollection != null,
            // ⚠ 返回同步清空「隐藏集合」：卡片立即可见 ⇒ morph 落点有目标（不破坏一镜到底）。
            //   注意：只清在真实返回时；并行连点（A→B）走 onCollection 累加、不在此清，故旧卡片不闪回。
            onBack = { selectedCollection = null },
        ) {
            overlayCollection?.let { collection ->
                DiscoveryCollectionDetail(
                    collection = collection,
                    onBack = { selectedCollection = null },
                    sharedTransitionScope = sharedScope,
                    animatedVisibilityScope = this,
                    artworkSharedKey = discoveryCollectionArtworkSharedKey(collection),
                )
            }
        }
        HomeOverlayPage(
            shown = songList != null,
            onBack = { songList = null },
        ) {
            // 取 overlaySongList（返回时不清）：退场过渡期间详情仍被组合，那一滑才有东西可画。
            overlaySongList?.let { list ->
                HomeSongListDetail(list = list, onBack = { songList = null })
            }
        }
    }
}

private val HomeSongListActions = setOf("每日推荐", "热歌榜", "私人雷达")

@Composable
private fun HomeOverlayPage(
    shown: Boolean,
    onBack: () -> Unit,
    // 以 receiver 暴露 AnimatedVisibilityScope，让内部详情页能把封面
    // 接到同一个 SharedTransitionLayout 里的列表卡片上（一镜到底）。
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val progress = remember { Animatable(0f) }
    // 手势返回走完后 progress 停在 1（页面已滑出屏外）。下一次进入必须归零，
    // 否则新页面会带着满位移进来、永远停在屏幕右侧外面。
    LaunchedEffect(shown) { if (shown) progress.snapTo(0f) }
    // 详情叠在首页上。普通返回和预见式返回都先关掉它，
    // 滑开时露出底下的首页，而不是灰色底或「再按一次退出」。
    // ⚠ 普通返回键交给 MeloXInterruptiblePage 接管（onBack）：它在「退出途中」也保持启用并
    //   吞掉事件，避免连点穿透把页面连锁关掉。手势路径仍走下面的 PredictiveBackHandler。
    PredictiveBackHandler(enabled = shown) {
        try {
            it.collect { event -> progress.snapTo(event.progress) }
            // 先启动退出过渡：此刻位移仍在手势落点上，封面从这里一镜到底
            // morph 回列表卡片，剩余位移随后收尾。
            // 不能在 onBack() 之后 snapTo(0f)：那会在页面已经在屏外时把位移
            // 瞬间归零，整页闪回屏幕正中再滑出。
            onBack()
            progress.animateTo(1f, tween(160))
        } catch (_: CancellationException) {
            progress.animateTo(0f, tween(160))
        }
    }
    MeloXInterruptiblePage(
        visible = shown,
        retreatProgress = { progress.value },
        onBack = onBack,
        modifier = Modifier.fillMaxSize().zIndex(if (shown) 1f else 0f),
    ) {
        content()
    }
}

private data class HomeSongList(
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val songs: List<SearchSong>,
)

private fun encodeHomeSongList(list: HomeSongList?): String? {
    if (list == null) return null
    return runCatching {
        JSONObject()
            .put("title", list.title)
            .put("subtitle", list.subtitle)
            .put("artworkUrl", list.artworkUrl.orEmpty())
            .put("songs", org.json.JSONArray().apply {
                list.songs.forEach { song ->
                    put(JSONObject()
                        .put("id", song.id)
                        .put("name", song.name)
                        .put("artists", song.artists)
                        .put("album", song.album)
                        .put("albumId", song.albumId ?: -1L)
                        .put("artistId", song.artistId ?: -1L)
                        .put("artworkUrl", song.artworkUrl.orEmpty())
                        .put("durationMs", song.durationMs))
                }
            })
            .toString()
    }.getOrNull()
}

private fun decodeHomeSongList(raw: String?): HomeSongList? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val value = JSONObject(raw)
        val songs = value.optJSONArray("songs") ?: org.json.JSONArray()
        HomeSongList(
            title = value.optString("title"),
            subtitle = value.optString("subtitle"),
            artworkUrl = value.optString("artworkUrl").takeIf(String::isNotBlank),
            songs = buildList {
                for (index in 0 until songs.length()) {
                    val song = songs.optJSONObject(index) ?: continue
                    add(SearchSong(
                        id = song.optLong("id"),
                        name = song.optString("name"),
                        artists = song.optString("artists"),
                        album = song.optString("album"),
                        albumId = song.optLong("albumId", -1L).takeIf { it > 0L },
                        artistId = song.optLong("artistId", -1L).takeIf { it > 0L },
                        artworkUrl = song.optString("artworkUrl").takeIf(String::isNotBlank),
                        durationMs = song.optLong("durationMs"),
                    ))
                }
            },
        )
    }.getOrNull()
}

private val HomeSongListSaver: Saver<HomeSongList?, String> = Saver(
    save = { encodeHomeSongList(it) },
    restore = { decodeHomeSongList(it) },
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeSongListDetail(
    list: HomeSongList,
    onBack: () -> Unit,
) {
    val playlist = remember(list.title, list.artworkUrl, list.songs.size) {
        NeteasePlaylistSummary(
            id = -(list.title.hashCode().toLong() and 0x7fff_ffffL) - 1L,
            name = list.title,
            coverUrl = list.artworkUrl,
            trackCount = list.songs.size,
            creatorName = list.subtitle,
        )
    }
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = playlist,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                (
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = MeloXMotion.ContentEnterMillis,
                            easing = FastOutSlowInEasing,
                        ),
                    ) togetherWith fadeOut(
                        animationSpec = tween(
                            durationMillis = MeloXMotion.ContentExitMillis,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                ).apply { targetContentZIndex = 2f }
            },
            label = "home-song-list-detail",
        ) { target ->
            AnimatedVisibility(visible = true) {
                MeloXUnifiedSongListDetailScreen(
                    playlist = target,
                    songs = list.songs,
                    onBack = onBack,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                )
            }
        }
    }
}

@Composable
private fun ProviderHomeDataScreen(source: MusicSource, onOpenTool: (String) -> Unit) {
    val onDetailSelected = rememberMeloXDetailSelection()
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val registry = remember(source) { MeloXMusicProviders.create(context) }
    val provider = remember(source, registry) { registry.require(source) }
    val home = provider as? HomeFeedCapability
    val library = provider as? UserLibraryCapability
    var feed by rememberRetainedPageState(source) { mutableStateOf<MusicHomeFeed?>(null) }
    var account by rememberRetainedPageState(source) { mutableStateOf<MusicAccountSummary?>(null) }
    var refreshing by remember(source) { mutableStateOf(false) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var selectedCollection by rememberRetainedPageState(source) { mutableStateOf<DiscoveryCollection?>(null) }
    // 内容与可见性分开持有（与网易云首页同款）：返回时只把 selectedCollection 置空，
    // overlayCollection 保留到退场过渡跑完，否则退场期间 content lambda 已取到 null，
    // 那个过渡在演一个空壳 ⇒「没有返回动画」。
    var overlayCollection by remember(source) { mutableStateOf(selectedCollection) }

    fun refresh() {
        if (refreshing || home == null) return
        scope.launch {
            refreshing = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val feedResult = home.homeFeed(playlistLimit = 12, newSongLimit = 16, rankingLimit = 10)
                    val accountResult = runCatching { library?.accountSummary() }.getOrNull()
                    feedResult to accountResult
                }
            }.onSuccess { (feedResult, accountResult) ->
                feed = feedResult
                account = accountResult
                error = null
            }.onFailure { error = it.message ?: "首页加载失败" }
            refreshing = false
        }
    }

    LaunchedEffect(source) { if (feed == null) refresh() }

    // 退场过渡跑完再释放歌单页内容（理由见 overlayCollection）。
    LaunchedEffect(selectedCollection) {
        if (selectedCollection == null && overlayCollection != null) {
            delay(meloXSettledMillis(MeloXMotion.PageExitMillis, DiscoveryOverlaySettleMillis))
            overlayCollection = null
        }
    }

    val blocks = feed?.let { value ->
        buildList<HomeBlock> {
            if (provider is PlaylistCapability && value.recommendedPlaylists.isNotEmpty()) {
                add(HomeBlock.Collections("每日推荐", source.displayName, value.recommendedPlaylists.map { DiscoveryCollection.ProviderPlaylist(it) }))
            }
            if (value.newSongs.isNotEmpty()) {
                add(HomeBlock.Tracks(stringResource(R.string.home_for_you), stringResource(R.string.home_new_songs), value.newSongs.map { DiscoveryTrack.Provider(it) }))
            }
            if (provider is RankingCapability && value.rankings.isNotEmpty()) {
                add(HomeBlock.Collections("排行榜", source.displayName, value.rankings.map { DiscoveryCollection.ProviderRanking(it) }))
            }
        }
    }
    val accountUi = account?.let {
        HomeAccountUi(
            name = it.displayName,
            avatarUrl = it.avatarUrl,
            // Some providers serialize a missing subtitle as the literal string "null".
            // Keep that transport detail out of the UI and show a useful source label.
            subtitle = it.subtitle
                ?.takeUnless { value -> value.isBlank() || value.equals("null", ignoreCase = true) }
                ?: source.displayName,
        )
    }

    // ⚠ 2026-10-16：与 [NeteaseHomeDataScreen] 对齐。此前 provider（QQ / 酷狗…）首页**没有**
    //   顶层 [SharedTransitionLayout]，也没把作用域透传给卡片与详情 ⇒ 卡片不注册 sharedElement、
    //   详情不注册配对元素 ⇒ 打开歌单 / 排行榜时**一镜到底 morph 完全消失**
    //   （用户报障：「音乐服务选 QQ 时 morph 一镜到底动画消失」）。补齐同一套接线。
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        val sharedScope = this
        // ⚠ hero push 时旧页也左移让出 1/3（与网易云首页 / 发现页同款）：与推入页走同一条弹簧
        //   （[meloXPageRedirectSpec]），两页严格互补。旧页位移必须走 [meloXPageRecede]
        //   的**布局期** place：共享元素 morph 的 bounds 取自 lookahead 布局坐标，
        //   走 graphicsLayer 封面不跟随、半路与卡片脱开。
        val homeRecede = remember { Animatable(0f) }
        val homeCollectionOpen = selectedCollection != null
        LaunchedEffect(homeCollectionOpen) {
            val target = if (homeCollectionOpen) 1f else 0f
            if (homeRecede.value == target) return@LaunchedEffect
            homeRecede.animateTo(target, meloXPageRedirectSpec())
        }
        Box(Modifier.fillMaxSize().meloXPageRecede { homeRecede.value }) {
            MeloXHomeLayout(
                source = source,
                account = accountUi,
                blocks = blocks,
                refreshing = refreshing,
                error = if (home == null) "${source.displayName} 暂未提供首页数据" else error,
                onRefresh = ::refresh,
                activeAction = null,
                onQuickAction = { action ->
                    when (action) {
                        "听歌识曲" -> onOpenTool("Recognition")
                        "私信" -> onOpenTool("Messages")
                        "播客" -> onOpenTool("Podcasts")
                        "下载" -> onOpenTool("Downloads")
                        "云盘" -> onOpenTool("Cloud")
                    }
                },
                onCollection = {
                    onDetailSelected()
                    selectedCollection = it
                    overlayCollection = it
                },
                selectedCollectionKey = selectedCollection?.key,
                sharedTransitionScope = sharedScope,
            )
        }
        HomeOverlayPage(
            shown = selectedCollection != null,
            onBack = { selectedCollection = null },
        ) {
            // 取 overlayCollection（返回时不清），退场过渡期间详情仍被组合。
            overlayCollection?.let { collection ->
                DiscoveryCollectionDetail(
                    collection = collection,
                    onBack = { selectedCollection = null },
                    sharedTransitionScope = sharedScope,
                    animatedVisibilityScope = this,
                    artworkSharedKey = discoveryCollectionArtworkSharedKey(collection),
                )
            }
        }
    }
}

@Composable
private fun MeloXHomeLayout(
    source: MusicSource,
    account: HomeAccountUi?,
    blocks: List<HomeBlock>?,
    refreshing: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    activeAction: String?,
    onQuickAction: (String) -> Unit,
    onCollection: (DiscoveryCollection) -> Unit,
    // 当前打开的 collection key。被打开的那张卡片必须「退出」，否则
    // sharedElement 没有 enter/exit 可配对，封面不会 morph（官方范式）。
    selectedCollectionKey: String? = null,
    // 由 host 顶层 SharedTransitionLayout 传入；为空时卡片不做共享元素。
    sharedTransitionScope: SharedTransitionScope? = null,
) {
    val context = LocalContext.current
    val exploreListState = rememberLazyListState()
    // ⚠ 2026-09-29：状态栏避让从「容器 `statusBarsPadding()`」改成「滚动容器的顶部
    //   contentPadding」。原来 padding 打在 LazyColumn 的 modifier 上 ⇒ 列表视口
    //   被压到状态栏下缘(41dp)，滚动时内容在 y=41dp 处被裁掉、**永远进不了状态栏/挖孔区域**。
    //   改成 contentPadding 后：静止态首项位置不变（18+41），滚动时内容从状态栏底下穿出。
    val statusBarTop = rememberMeloXStableStatusBarHeight()
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        if (blocks == null) {
            EmptyOrLoading(refreshing, error)
        } else {
            LazyColumn(
                state = exploreListState,
                // 触顶/触底橡胶回弹触感。
                modifier = Modifier
                    .fillMaxSize()
                    .meloXEdgeOverscrollHaptic(exploreListState),
                contentPadding = PaddingValues(top = 18.dp + statusBarTop, bottom = 146.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item {
                    MeloXIosTopBar(
                        title = stringResource(R.string.tab_home),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        actions = {
                            account?.let { HomeAccountButton(it) }
                        },
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.home_greeting),
                        modifier = Modifier.padding(horizontal = 20.dp),
                        style = MeloXTypography.headline,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f),
                    )
                }
                blocks.forEach { block ->
                    when (block) {
                        HomeBlock.QuickActions -> item { HomeQuickActions(source, activeAction, onQuickAction) }
                        is HomeBlock.Collections -> {
                            item { SectionTitle(block.title, block.trailing, Modifier.padding(horizontal = 20.dp)) }
                            item {
                                CollectionRow(
                                    values = block.values,
                                    onSelect = onCollection,
                                    selectedCollectionKey = selectedCollectionKey,
                                    sharedTransitionScope = sharedTransitionScope,
                                )
                            }
                        }
                        is HomeBlock.Tracks -> {
                            item { SectionTitle(block.title, block.trailing, Modifier.padding(horizontal = 20.dp)) }
                            item { ThreeLineSongCarousel(block.values) { track -> playDiscoveryQueue(context, block.values, track) } }
                        }
                        is HomeBlock.Podcasts -> item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(block.values, key = { "podcast-${it.programId ?: it.id}" }) { podcast ->
                                    Column(
                                        Modifier.width(150.dp)
                                            .clickable {
                                                podcast.programId?.let { MeloXCollectionDetailActivity.launchPodcastProgram(context, it) }
                                                    ?: MeloXCollectionDetailActivity.launchPodcast(context, podcast.id)
                                            },
                                    ) {
                                        AsyncImage(
                                            podcast.artworkUrl,
                                            null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(150.dp).clip(RoundedCornerShape(14.dp)),
                                        )
                                        Text(
                                            podcast.name,
                                            Modifier.padding(top = 6.dp),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (blocks.isEmpty() && error == null) {
                    item { Text("${source.displayName} 当前没有返回可展示内容", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f)) }
                }
                error?.let { message -> item { Text(message, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error, fontSize = 13.sp) } }
            }
        }
    }
}

@Composable
private fun HomeAccountButton(account: HomeAccountUi) {
    val accountDescription = stringResource(R.string.accessibility_account)
    Box(
        modifier = Modifier
            .size(42.dp)
            .meloXGlassSurface(
                shape = CircleShape,
                tint = MeloXSystemColors.Red.copy(alpha = 0.12f),
                surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
            )
            .semantics {
                contentDescription = accountDescription
                role = Role.Button
            }
            .clickable(enabled = account.onClick != null, onClick = { account.onClick?.invoke() }),
        contentAlignment = Alignment.Center,
    ) {
        if (account.avatarUrl.isNullOrBlank()) {
            MeloXSymbolIcon(
                MeloXSymbol.Person,
                Modifier.size(25.dp),
                MeloXSystemColors.Red,
                MeloXSymbolVariant.Fill,
            )
        } else {
            AsyncImage(
                account.avatarUrl,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
    }
}

private data class HomeQuickAction(
    val title: String,
    val eyebrow: String,
    val subtitle: String,
    val symbol: MeloXSymbol,
    val colors: List<Color>,
)

@Composable
private fun HomeQuickActions(source: MusicSource, active: String?, perform: (String) -> Unit) {
    val actions = buildList {
        if (source == MusicSource.Netease) {
            add(HomeQuickAction("每日推荐", stringResource(R.string.home_eyebrow_daily), stringResource(R.string.home_subtitle_daily), MeloXSymbol.Calendar, listOf(Color(0xFFFF5B8A), Color(0xFFFF3147))))
            add(HomeQuickAction("热歌榜", stringResource(R.string.home_eyebrow_hot), stringResource(R.string.home_subtitle_hot), MeloXSymbol.Flame, listOf(Color(0xFFFFA14A), Color(0xFFFF5A36))))
            add(HomeQuickAction("心动模式", stringResource(R.string.home_eyebrow_heart), stringResource(R.string.home_subtitle_heart), MeloXSymbol.Heart, listOf(Color(0xFFFF6EAC), Color(0xFF9B5DE5))))
            add(HomeQuickAction("私人雷达", stringResource(R.string.home_eyebrow_radar), stringResource(R.string.home_subtitle_radar), MeloXSymbol.RadioWaves, listOf(Color(0xFF6B7BFF), Color(0xFF8C52FF))))
            add(HomeQuickAction("私人漫游", stringResource(R.string.home_eyebrow_roam), stringResource(R.string.home_subtitle_roam), MeloXSymbol.Walk, listOf(Color(0xFF26C6DA), Color(0xFF4285F4))))
            add(HomeQuickAction("相似歌曲", stringResource(R.string.home_eyebrow_similar), stringResource(R.string.home_subtitle_similar), MeloXSymbol.List, listOf(Color(0xFF58C9A3), Color(0xFF159D9A))))
        }
        add(HomeQuickAction("听歌识曲", stringResource(R.string.home_eyebrow_recognize), stringResource(R.string.home_subtitle_recognize), MeloXSymbol.Microphone, listOf(Color(0xFF7B61FF), Color(0xFF36C5F0))))
        if (source == MusicSource.Netease) add(HomeQuickAction("私信", stringResource(R.string.home_eyebrow_messages), stringResource(R.string.home_subtitle_messages), MeloXSymbol.Mail, listOf(Color(0xFFFF6B8B), Color(0xFFFF3B30))))
        if (source == MusicSource.Netease && MeloXSettingsRuntime.podcastsEnabled && MeloXSettingsRuntime.podcastsHomePlacement) {
            add(HomeQuickAction("播客", stringResource(R.string.home_eyebrow_podcasts), stringResource(R.string.home_subtitle_podcasts), MeloXSymbol.RadioWaves, listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))))
        }
        if (MeloXSettingsRuntime.downloadsEnabled && MeloXSettingsRuntime.downloadsHomePlacement) {
            add(HomeQuickAction("下载", stringResource(R.string.home_eyebrow_downloads), stringResource(R.string.home_subtitle_downloads), MeloXSymbol.Download, listOf(Color(0xFF0EA5E9), Color(0xFF14B8A6))))
        }
        if (source == MusicSource.Netease && MeloXSettingsRuntime.cloudMusicEnabled && MeloXSettingsRuntime.cloudHomePlacement) {
            add(HomeQuickAction("云盘", stringResource(R.string.home_eyebrow_cloud), stringResource(R.string.home_subtitle_cloud), MeloXSymbol.Storage, listOf(Color(0xFF64748B), Color(0xFF6366F1))))
        }
    }
    if (actions.isEmpty()) return
    val pages = actions.chunked(2)
    val pagerState = rememberPagerState(pageCount = { pages.size })
    // Two half-width cards per page; the pager snaps as one magnetic group so
    // a swipe never rests on a half-visible tile.
    HorizontalPager(
        state = pagerState,
        // Left edge follows the normal 20dp gutter; the right side keeps a
        // 44dp peek of the next pair so the carousel reads as swipeable.
        contentPadding = PaddingValues(start = 20.dp, end = 44.dp),
        pageSpacing = 12.dp,
    ) { page ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            pages[page].forEach { action ->
                HomeQuickActionCard(
                    action = action,
                    active = active,
                    perform = perform,
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(2 - pages[page].size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun HomeQuickActionCard(
    action: HomeQuickAction,
    active: String?,
    perform: (String) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.clickable(enabled = active == null) { perform(action.title) },
    ) {
        Text(action.eyebrow.uppercase(), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(action.title, modifier = Modifier.padding(top = 2.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(action.subtitle, modifier = Modifier.padding(top = 1.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .aspectRatio(1.25f)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(action.colors)),
            contentAlignment = Alignment.Center,
        ) {
            MeloXSymbolIcon(
                symbol = action.symbol,
                modifier = Modifier.fillMaxSize(),
                color = Color.White.copy(alpha = .24f),
                iconSize = 48.sp,
            )
            Text(action.title, modifier = Modifier.align(Alignment.BottomStart).padding(14.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (active == action.title) {
                CircularProgressIndicator(Modifier.size(40.dp), color = Color.White, strokeWidth = 3.dp)
            }
        }
    }
}

@Composable
fun MeloXExploreScreen(source: MusicSource = MusicSource.Netease) {
    if (source == MusicSource.Netease) {
        NeteaseExploreDataScreen()
    } else {
        ProviderExploreDataScreen(source)
    }
}

@Composable
private fun NeteaseExploreDataScreen() {
    val onDetailSelected = rememberMeloXDetailSelection()
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { NeteaseLibraryCache(context) }
    val client = remember(context) { NeteaseLibraryClient({ NeteaseSessionStore.readCookie(context) }) }
    val scope = rememberCoroutineScope()
    val visibleCategories = Categories.filter { item ->
        (item != "精品歌单" || MeloXSettingsRuntime.showHighQualityPlaylists) &&
            (item != "播客" || MeloXSettingsRuntime.podcastsEnabled)
    }
    var category by rememberSaveable { mutableStateOf(visibleCategories.first()) }
    var collections by rememberRetainedPageState(category) { mutableStateOf<List<DiscoveryCollection>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedCollection by rememberSaveable(stateSaver = DiscoveryCollectionSaver) { mutableStateOf<DiscoveryCollection?>(null) }
    // 见 NeteaseHomeDataScreen：内容保留到退出过渡结束，返回时封面才能 morph 回卡片。
    var overlayCollection by remember { mutableStateOf(selectedCollection) }

    fun refresh() {
        if (category == "播客" || refreshing) return
        val requested = category
        scope.launch {
            refreshing = true
            runCatching { client.explorePlaylists(requested) }
                .onSuccess {
                    if (category == requested) collections = it.map { playlist -> DiscoveryCollection.Netease(playlist) }
                    cache.saveExplore(requested, it)
                    error = null
                }
                .onFailure { error = it.message ?: "发现页加载失败" }
            refreshing = false
        }
    }

    LaunchedEffect(category) {
        if (category == "播客") return@LaunchedEffect
        if (collections.isEmpty()) collections = cache.loadExplore(category).orEmpty().map { DiscoveryCollection.Netease(it) }
        if (collections.isEmpty() || NeteaseLibraryCache.beginExploreColdStartRefresh(category, cache.cacheDir)) refresh()
    }

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        val sharedScope = this
        // 覆盖层内容生命周期：同 NeteaseHomeDataScreen，只做「退场跑完再释放内容」。
        LaunchedEffect(selectedCollection) {
            if (selectedCollection == null && overlayCollection != null) {
                delay(meloXSettledMillis(MeloXMotion.PageExitMillis, DiscoveryOverlaySettleMillis))
                overlayCollection = null
            }
        }
        // ⚠ hero push 时旧页也左移让出 1/3（与首页同款，2026-10-02）：与推入页走同一条弹簧
        //   （[meloXPageRedirectSpec]），两页严格互补，观感与「无 hero 的整页推入」一致，
        //   区别只是多一个封面 morph。旧页位移走 [meloXPageRecede] 的**布局期** place：
        //   共享元素 morph 的 bounds 取自 lookahead 布局坐标，走 graphicsLayer 封面不跟随、半路脱开。
        val exploreRecede = remember { Animatable(0f) }
        val exploreCollectionOpen = selectedCollection != null
        LaunchedEffect(exploreCollectionOpen) {
            val target = if (exploreCollectionOpen) 1f else 0f
            if (exploreRecede.value == target) return@LaunchedEffect
            exploreRecede.animateTo(target, meloXPageRedirectSpec())
        }
        MeloXExploreLayout(
            modifier = Modifier.fillMaxSize().meloXPageRecede { exploreRecede.value },
            categories = visibleCategories,
            category = category,
            onCategory = { category = it },
            collections = collections,
            refreshing = refreshing,
            error = error,
            onRefresh = ::refresh,
            showPodcast = category == "播客",
            onCollection = {
                onDetailSelected()
                // 立即写入，不排队：退场途中再点卡时由 MeloXInterruptiblePage 的弹簧在途改向
                // （继承当前位移与速度），点下去即响应 —— 这正是「退出后能立刻点击」。
                selectedCollection = it
                overlayCollection = it
            },
            selectedCollectionKey = selectedCollection?.key,
            sharedTransitionScope = sharedScope,
        )
        HomeOverlayPage(
            shown = selectedCollection != null,
            // ⚠ 返回同步清空「隐藏集合」：卡片立即可见 ⇒ morph 落点有目标（不破坏一镜到底）。
            onBack = { selectedCollection = null },
        ) {
            overlayCollection?.let { collection ->
                DiscoveryCollectionDetail(
                    collection = collection,
                    onBack = { selectedCollection = null },
                    sharedTransitionScope = sharedScope,
                    animatedVisibilityScope = this,
                    artworkSharedKey = discoveryCollectionArtworkSharedKey(collection),
                )
            }
        }
    }
}

@Composable
private fun ProviderExploreDataScreen(source: MusicSource) {
    val onDetailSelected = rememberMeloXDetailSelection()
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val provider = remember(source) { MeloXMusicProviders.create(context).require(source) }
    val home = provider as? HomeFeedCapability
    var feed by rememberRetainedPageState(source) { mutableStateOf<MusicHomeFeed?>(null) }
    var category by rememberSaveable(source) { mutableStateOf("推荐歌单") }
    var refreshing by remember(source) { mutableStateOf(false) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var selectedCollection by rememberRetainedPageState(source) { mutableStateOf<DiscoveryCollection?>(null) }
    // 内容与可见性分开持有（与网易云首页同款）：返回时只把 selectedCollection 置空，
    // overlayCollection 保留到退场过渡跑完，否则退场期间 content lambda 已取到 null，
    // 那个过渡在演一个空壳 ⇒「没有返回动画」。
    var overlayCollection by remember(source) { mutableStateOf(selectedCollection) }

    fun refresh() {
        if (refreshing || home == null) return
        scope.launch {
            refreshing = true
            runCatching {
                withContext(Dispatchers.IO) { home.homeFeed(playlistLimit = 40, newSongLimit = 0, rankingLimit = 30) }
            }.onSuccess {
                feed = it
                error = null
                if (category == "推荐歌单" && it.recommendedPlaylists.isEmpty() && it.rankings.isNotEmpty()) category = "排行榜"
            }.onFailure { error = it.message ?: "发现页加载失败" }
            refreshing = false
        }
    }

    LaunchedEffect(source) { if (feed == null) refresh() }

    // 退场过渡跑完再释放歌单页内容（理由见 overlayCollection）。
    LaunchedEffect(selectedCollection) {
        if (selectedCollection == null && overlayCollection != null) {
            delay(meloXSettledMillis(MeloXMotion.PageExitMillis, DiscoveryOverlaySettleMillis))
            overlayCollection = null
        }
    }

    val categories = buildList {
        add("推荐歌单")
        if (feed?.rankings?.isNotEmpty() == true) add("排行榜")
    }
    val collections = when (category) {
        "排行榜" -> feed?.rankings.orEmpty().map { DiscoveryCollection.ProviderRanking(it) }
        else -> feed?.recommendedPlaylists.orEmpty().map { DiscoveryCollection.ProviderPlaylist(it) }
    }

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        val sharedScope = this
        // ⚠ hero push 时旧页也左移让出 1/3（与首页/发现同款）：与推入页走同一条弹簧
        //   （[meloXPageRedirectSpec]），两页严格互补，观感与「无 hero 的整页推入」一致，
        //   区别只是多一个封面 morph。旧页位移走 [meloXPageRecede] 的**布局期** place：
        //   共享元素 morph 的 bounds 取自 lookahead 布局坐标，走 graphicsLayer 封面不跟随、半路脱开。
        val providerRecede = remember { Animatable(0f) }
        val providerCollectionOpen = selectedCollection != null
        LaunchedEffect(providerCollectionOpen) {
            val target = if (providerCollectionOpen) 1f else 0f
            if (providerRecede.value == target) return@LaunchedEffect
            providerRecede.animateTo(target, meloXPageRedirectSpec())
        }
        MeloXExploreLayout(
            modifier = Modifier.fillMaxSize().meloXPageRecede { providerRecede.value },
            categories = categories,
            category = category,
            onCategory = { category = it },
            collections = collections,
            refreshing = refreshing,
            error = if (home == null) "${source.displayName} 暂未提供发现数据" else error,
            onRefresh = ::refresh,
            showPodcast = false,
            onCollection = {
                onDetailSelected()
                selectedCollection = it
                overlayCollection = it
            },
            // ⚠ 2026-10-16：此前 provider 发现页**漏传** selectedCollectionKey ⇒ 被打开的那张卡片
            //   永远 `visible = true`（不「退出」）⇒ sharedElement 少了一端、封面不会一镜到底
            //   morph（网易云发现页一直传着，见 NeteaseExploreScreen）。补齐成同一套。
            selectedCollectionKey = selectedCollection?.key,
            sharedTransitionScope = sharedScope,
        )
        HomeOverlayPage(
            shown = selectedCollection != null,
            onBack = { selectedCollection = null },
        ) {
            // 取 overlayCollection（返回时不清），退场过渡期间详情仍被组合。
            overlayCollection?.let { collection ->
                DiscoveryCollectionDetail(
                    collection = collection,
                    onBack = { selectedCollection = null },
                    sharedTransitionScope = sharedScope,
                    animatedVisibilityScope = this,
                    artworkSharedKey = discoveryCollectionArtworkSharedKey(collection),
                )
            }
        }
    }
}

@Composable
private fun MeloXExploreLayout(
    categories: List<String>,
    category: String,
    onCategory: (String) -> Unit,
    collections: List<DiscoveryCollection>,
    refreshing: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    showPodcast: Boolean,
    onCollection: (DiscoveryCollection) -> Unit,
    selectedCollectionKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val collapseDistancePx = with(density) { EXPLORE_COLLAPSE_SCROLL.toPx() }
    // 折叠进度直接由网格自身的滚动状态驱动（与 MeloXPinnedListPage 同款，最稳）：
    // 首屏内随 firstVisibleItemScrollOffset 从 0→1 收缩；越过首屏（index>0）后强制保持
    // 折叠态，绝不回弹，也不会因嵌套滚动错位导致头部与内容脱节而透出内容。
    val gridState = rememberLazyGridState()
    val collapse by remember(gridState, collapseDistancePx) {
        derivedStateOf {
            if (showPodcast) 0f
            else if (gridState.firstVisibleItemIndex > 0) 1f
            else (gridState.firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
        }
    }
    val headerHeight = lerp(EXPLORE_HEADER_EXPANDED, EXPLORE_HEADER_COLLAPSED, collapse)
    // 指示器状态提到这一层：网格分支要自绘指示器并把它整体下移一个头部高度。
    val refreshState = rememberPullToRefreshState()

    // ⚠ 稳定值：可见式 `statusBarsPadding()` 在沉浸 `hide()`/`show()` 时会 0↔真实高度跳
    //   ⇒ 发现页整页跟着上下移。改吃稳定值 ⇒ 位置恒定。
    val exploreStatusBarTop = rememberMeloXStableStatusBarHeight()
    Box(modifier.fillMaxSize().padding(top = exploreStatusBarTop)) {
        // 内容滚动区：顶部预留 EXPANDED 高度，使首屏内容从头部之下开始。
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = if (showPodcast) EXPLORE_HEADER_EXPANDED else 0.dp),
        ) {
            if (showPodcast) {
                MeloXPodcastScreen()
            } else {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                    state = refreshState,
                    // ⚠默认指示器画在 PullToRefreshBox 的「顶边」：IndicatorBox 的
                    // translationY 从 -40dp 滑到 0，即全程贴在容器上沿。而本页顶部是一块
                    // 不透明的折叠头部覆盖层（zIndex 1f）。网格分支不像播客分支那样给内容
                    // 留 EXPLORE_HEADER_EXPANDED 顶内边距（网格靠 contentPadding 让内容从
                    // 头部下方开始），于是指示器被头部整块盖住 —— 下拉时屏幕上看不到任何
                    // 反馈（内容本身也不位移），用户只能读成「下拉没刷新」。
                    // 对策：整体下移一个头部高度，让它从头部下缘滑出来。
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            modifier = Modifier.align(Alignment.TopCenter).offset(y = headerHeight),
                            state = refreshState,
                            isRefreshing = refreshing,
                        )
                    },
                ) {
                    if (collections.isEmpty()) {
                        EmptyOrLoading(refreshing, error)
                    } else {
                        CollectionGrid(
                            values = collections,
                            onSelect = onCollection,
                            selectedCollectionKey = selectedCollectionKey,
                            sharedTransitionScope = sharedTransitionScope,
                            state = gridState,
                            contentTopPadding = EXPLORE_HEADER_EXPANDED,
                        )
                    }
                }
            }
        }

        // 折叠头部（吸顶覆盖层，不透明背景，内容永透不出）。
        ExploreCollapsingHeader(
            title = stringResource(R.string.tab_explore),
            categories = categories,
            category = category,
            onCategory = onCategory,
            collapse = collapse,
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f),
        )
    }
}

@Composable
private fun ExploreCollapsingHeader(
    title: String,
    categories: List<String>,
    category: String,
    onCategory: (String) -> Unit,
    collapse: Float,
    modifier: Modifier = Modifier,
) {
    // 标题字号 34sp→24sp 等效缩放：1 → 24/34，手算线性插值（避免 Float 版 lerp 重载缺失）。
    val titleScale = 1f + (24f / 34f - 1f) * collapse
    Column(
        modifier
            .fillMaxWidth()
            .height(lerp(EXPLORE_HEADER_EXPANDED, EXPLORE_HEADER_COLLAPSED, collapse))
            // 关键：背景恒不透明（页面底色），内容永远被挡在头部之下 → 绝不透出
            .background(MaterialTheme.colorScheme.background)
            .zIndex(1f),
    ) {
        Spacer(Modifier.height(lerp(ExploreHeaderTopInsetExpanded, ExploreHeaderTopInsetCollapsed, collapse)))
        // 标题块：高度随折叠收缩；字号固定，仅用 graphicsLayer 缩放（绘制期，不触发文字重排版）。
        Box(
            Modifier
                .height(lerp(ExploreTitleBlockExpanded, ExploreTitleBlockCollapsed, collapse))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = title,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // includeFontPadding=false + 显式 lineHeight：让字形盒子紧贴可见字形，
                // 消除 Compose 默认字体度量把「发现」下缘（descender）吃掉的裁切。
                lineHeight = 44.sp,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                modifier = Modifier.graphicsLayer {
                    scaleX = titleScale
                    scaleY = titleScale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    alpha = 1f - 0.15f * collapse
                },
            )
        }
        Spacer(Modifier.height(lerp(ExploreTitleGapExpanded, ExploreTitleGapCollapsed, collapse)))
        // 标签行：随头部高度收缩自然上移吸顶（仅依赖外层高度变化，自身布局固定，不抽搐）。
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories) { item ->
                MeloXGlassButton(
                    onClick = { onCategory(item) },
                    modifier = Modifier.height(38.dp),
                    style = if (category == item) {
                        com.lladlam.melox.ui.glass.MeloXGlassButtonStyle.BorderedProminent
                    } else {
                        com.lladlam.melox.ui.glass.MeloXGlassButtonStyle.Bordered
                    },
                    tint = if (category == item) Accent else Color.Unspecified,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = item.removeSuffix("歌单"),
                        color = if (category == item) Color.White else MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun LargeTitle(text: String, modifier: Modifier = Modifier) = Text(
    text,
    modifier,
    // Match iOS largeTitle metrics instead of letting the CJK fallback
    // overpower the content below it.
    style = MeloXTypography.largeTitle,
    color = MaterialTheme.colorScheme.onBackground,
)

@Composable
private fun SectionTitle(title: String, trailing: String, modifier: Modifier = Modifier) = Row(
    modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Text(title, style = MeloXTypography.title2)
    Text(trailing, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .42f), fontSize = 13.sp)
}

/**
 * 让「当前打开的那张卡片」退出 —— 这是 sharedElement 能配对的硬前提。
 * 其余卡片恒可见（首次组合不播动画），只有被点开的那张有 exit，
 * 封面才会从卡片位置连续地 morph 到详情 hero（官方 AnimatedVisibility 范式）。
 */
/**
 * 卡片 exit 过渡的**总时长**（2026-10-02 用户报「退出来时找不到共享元素」「位置偶尔会变化」）。
 *
 * ⚠⚠⚠ **必须远长于 hero morph 的 350ms**：`AnimatedVisibility` 在 exit 结束的那一刻会把内容
 * **从组合中移除**。若 exit 与 morph 同期结束，退出时那张卡片是**重新组合**出来的 ——
 * 布局要等一帧才确定 ⇒ sharedElement 的目标 bounds 在第一帧是错的 ⇒ 既找不到共享元素，
 * 位置也会漂一下。这两个症状是**同一个原因**。
 *
 * 手法：alpha 用 [keyframesSpec] 在前 350ms 就降到 0（**观感不变**），只把 transition 的
 * 总时长撑到本值 ⇒ 卡片在这段时间里始终留在组合中，morph 的两端都不会缺。
 */
private const val MeloXCardExitHoldMillis = 1200

@Composable
private fun DiscoveryCardVisibility(
    collection: DiscoveryCollection,
    selectedCollectionKey: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = LocalMeloXListDetail.current || collection.key != selectedCollectionKey,
        // ⚠⚠ **`enter` 必须是 `None`；`exit` 必须与 hero morph 同拍**
        //   （用户反复报「一个卡片没完全退出时点击另一个卡片打开，会残留上一个卡片」，试过
        //   `fadeIn` —— **没修复**，因为只要 enter 带任何动画就还会露半透明旧卡片）。
        //   · 为什么 None：sharedElement 的 morph 窗口由**详情端**的 exit 过渡提供
        //     （两端只要有一端在 transition 里就 morph），不依赖卡片端自己的 enter 时长；卡片端只要
        //     「就地出现」即可（= iOS 行为），也不需要任何手势 / 点击锁（用户明确「最好不是阉割并行手势」）。
        //   · ⚠ 旧卡片「并行连点 A→B」残留的根因：可见性由单值 `selectedCollection?.key` 驱动，
        //     切到 B 后 `collection.key != B.key` 让 A 误判「回来」⇒ 就地瞬现、露在新页后面。
        //     enter=None 已消掉「半透明旧卡」，但瞬间不透明旧卡仍在 —— 这是已知次要现象，暂不额外处理
        //     （曾试过「隐藏集合累加」「延迟清标志」两种方案，前者仍停留过久、后者引入变黑+断 morph，均撤销）。
        //   · ⚠⚠ `exit` 绝不能留默认弹簧：名义时长（≈400ms、实际收敛不可控）与 morph 的
        //     `tween(350ms)` **不同拍** ⇒ 卡片会在封面飞回来之前就被 dispose、失去 morph 目标 ⇒
        //     封面停半空 / 落点与卡片错开。统一成同拍 tween，exit 结束那一刻 morph 正好精确到位。
        //   ⚠ 首次组合本来就不播 enter ⇒ 首帧观感不变；打开方向走 exit，观感不变。
        enter = EnterTransition.None,
        // ⚠⚠ exit 的**总时长** = [MeloXCardExitHoldMillis]（1200ms），远超 morph 的 350ms ——
        //   目的不是让动画变慢，而是让卡片在 morph 全程都留在组合里（理由见该常量 KDoc）。
        //   alpha 用 keyframes 在前 350ms 就收完 ⇒ **观感与之前完全一致**；scaleOut 同样 350ms。
        exit = fadeOut(
            tween(
                durationMillis = MeloXCardExitHoldMillis,
                // ⚠ alpha 在前 350ms（= 1200 × 0.2917）内**按原曲线**收完，之后恒为 0 ——
                //   观感与 350ms 版本逐帧一致；多出来的 850ms 只是为了把 transition 的总时长
                //   撑住，让卡片留在组合中（`keyframesSpec` 在本项目 Compose 版本不可用，
                //   `fadeOut` 也没有 `keyframes` 参数，故用自定义 Easing 等效实现）。
                easing = Easing { f ->
                    FastOutSlowInEasing.transform((f / 0.2917f).coerceAtMost(1f))
                },
            ),
        ) + scaleOut(tween(MeloXMotion.PageTransitionMillis)),
        modifier = modifier,
        label = "discovery-card-${collection.key}",
        content = content,
    )
}

@Composable
private fun CollectionRow(
    values: List<DiscoveryCollection>,
    onSelect: (DiscoveryCollection) -> Unit,
    selectedCollectionKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
            itemsIndexed(values, key = { _, value -> value.key }) { index, collection ->
                // ⚠⚠⚠ 占位尺寸（宽 + 高）必须打在 `AnimatedVisibility` **外面这一层**（空的 `Box`），
            //   不能只挂在 `CollectionCard` 内容上：exit 结束、内容 dispose 后，挂在**内容**上的
            //   尺寸会跟着消失 ⇒ 该 item 宽/高塌成 0 ⇒ 后面卡片左移、anchor 跳变、下方 section
            //   上移一个卡片高（用户报「退出时卡片右移」「从右往左第三张退出时跑到最右去了」
            //   「下方内容跳一下」）。打在包裹层上，占位就与**内容生命周期解耦**：dispose 后该
            //   item 仍占 174×254dp（254 = 8 内边距 + 174 封面 + 7 + 19×2 两行标题 + 3 + 14 播放量
            //   + 8 内边距，即两行标题时也够、播放量不再溢出；详见 `CollectionCard`）。⚠ 宽按首卡 246
            //   / 其余 174 区分（与旧布局一致）；高用固定 254 占位，不**硬填** `CollectionCard` 的
            //   内容高度（标题 `maxLines=2` 行数不固定，硬填会裁字）。
            Box(
                Modifier
                    .animateItem()
                    .size(if (index == 0) 246.dp else 174.dp, 254.dp),
            ) {
                DiscoveryCardVisibility(
                    collection = collection,
                    selectedCollectionKey = selectedCollectionKey,
                    modifier = Modifier.size(if (index == 0) 246.dp else 174.dp, 254.dp),
                ) {
                        CollectionCard(
                            value = collection,
                            modifier = Modifier,
                            onClick = { onSelect(collection) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = this,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreeLineSongCarousel(values: List<DiscoveryTrack>, onSelect: (DiscoveryTrack) -> Unit) {
    val window = rememberMeloXWindowInfo()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(values.chunked(3), key = { group -> group.joinToString("-") { it.key } }) { group ->
            Column(
                modifier = Modifier.width(if (window.supportsTwoPane) 390.dp else 320.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                group.forEach { track -> SongRow(track) { onSelect(track) } }
            }
        }
    }
}

@Composable
private fun CollectionGrid(
    values: List<DiscoveryCollection>,
    onSelect: (DiscoveryCollection) -> Unit,
    selectedCollectionKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    state: LazyGridState = rememberLazyGridState(),
    contentTopPadding: Dp = 0.dp,
) {
    val window = rememberMeloXWindowInfo()
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(window.gridColumns),
        contentPadding = PaddingValues(start = window.gutter, end = window.gutter, top = contentTopPadding, bottom = 146.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        values.firstOrNull()?.let { hero ->
            item(key = "hero-${hero.key}", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                // ⚠ 同 `CollectionRow`：占位高度打在 `AnimatedVisibility` 外面的 `Box` 上，
                //   避免 exit dispose 后该 item 高度塌成 0、整排 section 上移。231dp 与标准卡一致。
                Box(Modifier.animateItem().fillMaxWidth().height(231.dp)) {
                    DiscoveryCardVisibility(
                        collection = hero,
                        selectedCollectionKey = selectedCollectionKey,
                        modifier = Modifier.fillMaxWidth().height(231.dp),
                    ) {
                        HeroCollectionCard(
                            value = hero,
                            onClick = { onSelect(hero) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = this,
                        )
                    }
                }
            }
        }
        items(values.drop(1), key = DiscoveryCollection::key) { collection ->
            // ⚠ 同 `CollectionRow`：占位高度打在 `AnimatedVisibility` 外面的 `Box` 上，
            //   exit dispose 后该 item 仍占 254dp，下方 section 不再上跳（详见 `CollectionRow` 注释）。
                Box(Modifier.animateItem().fillMaxWidth().height(254.dp)) {
                DiscoveryCardVisibility(
                    collection = collection,
                    selectedCollectionKey = selectedCollectionKey,
                    modifier = Modifier.fillMaxWidth().height(254.dp),
                ) {
                    CollectionCard(
                        value = collection,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelect(collection) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = this,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCollectionCard(
    value: DiscoveryCollection,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(if (rememberMeloXWindowInfo().supportsTwoPane) 2.9f else 1.75f)
            .clip(MeloXShapes.largeCard)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            value.artworkUrl,
            null,
            contentScale = ContentScale.Crop,
            // 🔴 同步修 hero 卡：外层 Box 的 largeCard 圆角不随共享子树进 overlay，
            //   给内层 AsyncImage 自身也挂一份 clip，飞行/落定时都带圆角（与长卡片同修法）。
            modifier = Modifier
                .meloXDiscoverySharedArtwork(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    key = discoveryCollectionArtworkSharedKey(value),
                )
                .fillMaxSize()
                .clip(MeloXShapes.largeCard),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text(stringResource(R.string.home_featured_this_week), color = Color.White.copy(alpha = .72f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(value.title, color = Color.White, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            (value.description ?: value.creatorName).takeIf(String::isNotBlank)?.let { Text(it, color = Color.White.copy(alpha = .72f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun CollectionCard(
    value: DiscoveryCollection,
    modifier: Modifier,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    Column(
        modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(8.dp),
    ) {
        val artworkShape = RoundedCornerShape(14.dp)
        // Keep the card visually complete while remote artwork is loading.
        // Without a stable surface, the fixed image slot becomes a blank hole
        // and the title appears detached from its card on slower networks.
        // 🔴 2026-10-16 修「返回时直角再变圆角」：原本 `sharedElement` + `clip` 都挂在外层 Box 上，
        //   飞行/落定期间 `renderInOverlayDuringTransition` 把共享内容画进 overlay，外层 Box 的
        //   clip 不随共享子树进 overlay ⇒ 一镜到底过程中封面是直角，落定才变圆角。
        //   照 LibraryScreen（推荐歌单 morph 正常）的写法：把 `sharedElement` + `clip` 都挪到
        //   内层 `AsyncImage` 自身上，共享元素飞行时也带圆角，消除直角帧。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(174.dp),
        ) {
            AsyncImage(
                value.artworkUrl,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .meloXDiscoverySharedArtwork(
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        key = discoveryCollectionArtworkSharedKey(value),
                    )
                    .fillMaxSize()
                    .clip(artworkShape),
            )
        }
        // 标题 + 播放量占满封面下的剩余高度：播放量用 `weight` 钉在卡片底部，
        // 标题最多两行（超出在自身内省略号）。这样两行标题时播放量也不会溢出到卡片外、
        // 被下一行卡片盖住（修复「播放量行下半被裁切」）。外层格子高度已从 231 提到
        // 254，给两行标题 + 播放量留出整块空间，内容不再超出格子。
        Column(Modifier.weight(1f)) {
            Text(
                value.title,
                modifier = Modifier.padding(top = 7.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            if (MeloXSettingsRuntime.showPlaylistPlayCount && value.playCount > 0L) {
                Text(
                    "${compactCount(value.playCount)} 次播放",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .42f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

private fun discoveryCollectionArtworkSharedKey(collection: DiscoveryCollection): String =
    "discovery-collection-artwork-${collection.key}"

/**
 * 把列表卡片封面挂到宿主顶层的 SharedTransitionLayout 上，使首页/发现页
 * 打开歌单时封面能从卡片位置一镜到底地 morph 到详情 hero。
 * key 带 [DiscoveryCollection.key] 前缀：同一个首页里不同 block 可能含
 * 同一歌单，裸 id 会在同一作用域内撞 key。
 * scope 为空（尚未接入共享元素的宿主）时原样返回，不影响常规渲染。
 */
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun Modifier.meloXDiscoverySharedArtwork(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    key: String,
): Modifier {
    if (LocalMeloXListDetail.current || sharedTransitionScope == null || animatedVisibilityScope == null) return this
    return with(sharedTransitionScope) {
        this@meloXDiscoverySharedArtwork.sharedElement(
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

private fun compactCount(value: Long): String = when {
    value >= 100_000_000L -> "%.1f亿".format(value / 100_000_000.0)
    value >= 10_000L -> "%.1f万".format(value / 10_000.0)
    else -> value.toString()
}

@Composable
private fun SongRow(
    song: DiscoveryTrack,
    // ⚠ 2026-10-16：可选前景色。默认 null ⇒ 标题沿用 `LocalContentColor`、副标题沿用
    //   `MaterialTheme.colorScheme.onBackground`，**与改动前逐像素一致**（其余调用点零影响）。
    //   只有铺了 artwork 驱动背景的排行榜详情才传 `foreground`，保证亮 / 暗背景下都可读。
    contentColor: Color? = null,
    onClick: () -> Unit,
) {
    val artworkShape = RoundedCornerShape(9.dp)
    val titleColor = contentColor ?: Color.Unspecified
    val subtitleColor = (contentColor ?: MaterialTheme.colorScheme.onBackground).copy(alpha = .48f)
    Row(
        Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(artworkShape),
        ) {
            AsyncImage(song.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = titleColor)
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = subtitleColor, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DiscoveryCollectionDetail(
    collection: DiscoveryCollection,
    onBack: () -> Unit,
    // 由首页/发现页 host 透传：与列表卡片处于同一 SharedTransitionLayout
    // 作用域、并用同一个 key，才能把封面一镜到底地 morph 到详情 hero。
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    artworkSharedKey: String? = null,
) {
    when (collection) {
        is DiscoveryCollection.Netease -> {
            MeloXUnifiedPlaylistDetailScreen(
                playlist = collection.playlist,
                onBack = onBack,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                artworkSharedKey = artworkSharedKey,
            )
            return
        }
        is DiscoveryCollection.ProviderPlaylist -> {
            MeloXUnifiedPlaylistDetailScreen(
                playlist = MeloXLegacyUiBridge.playlist(collection.playlist),
                onBack = onBack,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                artworkSharedKey = artworkSharedKey,
            )
            return
        }
        is DiscoveryCollection.ProviderRanking -> Unit
    }
    val context = LocalContext.current.applicationContext
    var refreshVersion by rememberSaveable(collection.key) { mutableStateOf(0) }
    var tracks by rememberRetainedPageState(collection.key, refreshVersion) { mutableStateOf<List<DiscoveryTrack>?>(null) }
    var error by remember(collection.key) { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)

    // ⚠ 2026-10-16：排行榜详情此前是「裸 LazyColumn」—— **整页没有任何背景**。
    //   `MeloXInterruptiblePage` 容器本身**不铺底**（只有 slide 几何，见其 KDoc）⇒ 页面必须由
    //   内容自己画不透明底。歌单 / 专辑详情走 `MeloXPlaylistDetailScreen`（`background(Color.Black)`
    //   + `MeloXFlowingLightBackdrop`）所以不透明；排行榜这条分支漏了 ⇒ **整页透明**，
    //   下层的首页 / 发现页直接透出来、两层同屏叠印（用户报障「排行榜的背景异常」，录屏可证）。
    //   这里补齐与统一详情页**同款**的 artwork 驱动背景 + 可读性层，前景色随调色板明暗切换。
    var palette by remember(collection.artworkUrl) { mutableStateOf(MeloXDetailPalette.LightFallback) }
    LaunchedEffect(collection.artworkUrl) {
        palette = MeloXDetailPaletteProvider.paletteFor(collection.artworkUrl)
    }
    val foreground = if (palette.prefersDarkAppearance) Color.White else Color.Black

    LaunchedEffect(collection.key, refreshVersion) {
        if (tracks != null) return@LaunchedEffect
        tracks = null
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                val ranking = (collection as DiscoveryCollection.ProviderRanking).ranking
                val provider = MeloXMusicProviders.create(context)[ranking.id.sourceId] ?: error("音源不可用：${ranking.id.sourceId.value}")
                val capability = provider as? RankingCapability
                    ?: throw IllegalStateException("${ranking.id.sourceId.displayName} 当前不提供排行榜详情")
                loadAllMusicPages(key = { it.id }) { page ->
                    capability.rankingTracks(ranking, page = page, pageSize = 150)
                }.map { DiscoveryTrack.Provider(it) }
            }
        }.onSuccess { tracks = it }
            .onFailure { error = it.message ?: "内容加载失败" }
    }

    tracks?.let { values ->
        val ranking = (collection as DiscoveryCollection.ProviderRanking).ranking
                MeloXUnifiedSongListDetailScreen(
                    playlist = NeteasePlaylistSummary(
                        id = ranking.id.hashCode().toLong(),
                        name = collection.title,
                        coverUrl = collection.artworkUrl,
                        creatorName = collection.creatorName,
                        description = collection.description,
                        trackCount = values.size,
                    ),
                    songs = values.mapNotNull { (it as? DiscoveryTrack.Provider)?.track }.map(MeloXLegacyUiBridge::track),
                    onBack = onBack,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    artworkSharedKey = artworkSharedKey ?: discoveryCollectionArtworkSharedKey(collection),
                    source = ranking.id.sourceId.builtIn,
                    onRefresh = { refreshVersion++ },
                )
        return
    }

    // 状态栏避让改放 contentPadding（打在 LazyColumn 的 modifier 上会把内容裁在状态栏下缘）
    val statusBarTop = rememberMeloXStableStatusBarHeight()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // 与统一详情页同款的 artwork 驱动流动光背景（上面已铺不透明黑底，这里再叠光流）。
        MeloXFlowingLightBackdrop(
            artworkUrl = collection.artworkUrl,
            isPlaying = false,
            modifier = Modifier.fillMaxSize(),
        )
        // Apple 式可读性层：光流在动画中会比采样到的封面偏亮 / 偏暗，压一层保证前景可读。
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 18.dp + statusBarTop,
                bottom = 146.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                        MeloXActionIcon("‹", Modifier.size(22.dp), foreground)
                    }
                    Text(collection.title, color = foreground, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AsyncImage(
                        collection.artworkUrl,
                        null,
                        contentScale = ContentScale.Crop,
                        // ⚠ 2026-10-16：排行榜详情此前是裸 LazyColumn，hero 封面是普通 AsyncImage、
                        //   **没挂共享元素** ⇒ 从列表卡片点开排行榜时封面不会一镜到底 morph
                        //   （推荐歌单走 `MeloXUnifiedPlaylistDetailScreen` 已带 hero 共享元素，正常；
                        //   用户报「只有排行榜没有 morph」）。这里与卡片端用同一个
                        //   `discoveryCollectionArtworkSharedKey` 注册 sharedElement，两端 key 一致
                        //   ⇒ 封面从卡片位置 morph 到 hero。scope 为空时原样返回，不影响常规渲染。
                        modifier = Modifier
                            .meloXDiscoverySharedArtwork(
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                key = artworkSharedKey ?: discoveryCollectionArtworkSharedKey(collection),
                            )
                            .size(156.dp)
                            .clip(RoundedCornerShape(22.dp)),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(collection.title, color = foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (collection.creatorName.isNotBlank()) {
                            Text(
                                collection.creatorName,
                                modifier = Modifier.padding(top = 7.dp),
                                color = foreground.copy(alpha = .52f),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
            collection.description?.takeIf(String::isNotBlank)?.let { description ->
                item { Text(description, color = foreground.copy(alpha = .55f), fontSize = 13.sp, lineHeight = 19.sp) }
            }
            when {
                error != null -> item {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.account_retry), Modifier.clickable { refreshVersion++ }.padding(12.dp), color = foreground)
                }
                else -> item {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                }
            }
        }
    }
}

private fun playDiscoveryQueue(
    context: android.content.Context,
    tracks: List<DiscoveryTrack>,
    selected: DiscoveryTrack,
) {
    when (selected) {
        is DiscoveryTrack.Netease -> {
            val songs = tracks.mapNotNull { (it as? DiscoveryTrack.Netease)?.song }
            PlaybackCommands.playQueue(context, songs, selected.song.id)
        }
        is DiscoveryTrack.Provider -> {
            val providerTracks = tracks.mapNotNull { (it as? DiscoveryTrack.Provider)?.track }
            ProviderPlaybackCommands.playQueue(context, providerTracks, selected.track.id)
        }
    }
}

@Composable
private fun EmptyOrLoading(loading: Boolean, error: String?) {
    // ⚠必须用「可滚动的」LazyColumn，不能是纯 Box：PullToRefreshBox 只能从子滚动容器的
    // overscroll 里拿到下拉手势，纯 Box 完全不可滚动 ⇒ 空态/失败态下拉彻底失效，用户
    // 连「拉一下重试」都做不到（看起来就是「下拉没反应」）。内容不足一屏时 LazyColumn
    // 仍会把多余的滚动量交给父级，所以这只是恢复可下拉性，视觉与原来一致（居中）。
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            if (loading) CircularProgressIndicator(color = Accent)
            else Text(error ?: stringResource(R.string.melox_state_empty), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f))
        }
    }
}
