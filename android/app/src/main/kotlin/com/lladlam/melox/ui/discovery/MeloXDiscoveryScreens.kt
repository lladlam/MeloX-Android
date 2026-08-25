package com.lladlam.melox.ui.discovery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
import com.lladlam.melox.ui.podcast.MeloXPodcastScreen
import com.lladlam.melox.ui.library.MeloXUnifiedPlaylistDetailScreen
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.layout.rememberMeloXWindowInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Accent = Color(0xFFFF3147)
private val Categories = listOf("推荐歌单", "排行榜", "精品歌单", "播客", "全部", "华语", "欧美", "流行", "摇滚", "民谣", "电子", "轻音乐", "影视原声", "ACG")

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
        override val key: String = "${track.id.source.storageValue}:${track.id.value}"
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
        override val key: String = "${playlist.id.source.storageValue}-playlist:${playlist.id.value}"
        override val title: String = playlist.title
        override val artworkUrl: String? = playlist.artworkUrl
        override val creatorName: String = playlist.creatorName.orEmpty()
        override val playCount: Long = playlist.playCount ?: 0L
        override val description: String? = playlist.description
    }

    data class ProviderRanking(val ranking: MusicRankingSummary) : DiscoveryCollection {
        override val key: String = "${ranking.id.source.storageValue}-ranking:${ranking.id.value}"
        override val title: String = ranking.title
        override val artworkUrl: String? = ranking.artworkUrl
        override val creatorName: String = ranking.id.source.displayName
        override val playCount: Long = 0L
        override val description: String? = ranking.subtitle
    }
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
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { NeteaseLibraryCache(context) }
    val client by remember(context) {
        lazy { NeteaseLibraryClient({ NeteaseSessionStore.readCookie(context) }) }
    }
    val scope = rememberCoroutineScope()
    val session = rememberNeteaseSessionStore()
    var content by remember { mutableStateOf<NeteaseHomeContent?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedCollection by remember { mutableStateOf<DiscoveryCollection?>(null) }
    var activeAction by remember { mutableStateOf<String?>(null) }
    var localRecommendations by remember { mutableStateOf(emptyList<com.lladlam.melox.core.recommendation.LocalRecommendationItem>()) }
    var localCandidates by remember { mutableStateOf(emptyList<MusicTrack>()) }
    val homeCacheKey = "${session.cookie.hashCode()}_${MeloXSettingsRuntime.musicArea}_${MeloXSettingsRuntime.podcastsEnabled}"

    selectedCollection?.let { collection ->
        DiscoveryCollectionDetail(collection = collection, onBack = { selectedCollection = null })
        return
    }

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
        content = cache.loadHomeContent(homeCacheKey)
        if (session.isLoggedIn) session.refreshProfile()
        if (NeteaseLibraryCache.beginHomeColdStartRefresh(homeCacheKey)) refresh(false)
    }

    val blocks = content?.let { value ->
        buildList<HomeBlock> {
            if (localRecommendations.isNotEmpty()) {
                val orderedCandidates = localRecommendations.mapNotNull { recommendation ->
                    localCandidates.firstOrNull { candidate ->
                        candidate.title == recommendation.title && candidate.artistText == recommendation.artist
                    }?.let { DiscoveryTrack.Provider(it) }
                }
                if (orderedCandidates.isNotEmpty()) add(HomeBlock.Tracks("MeloX 为你推荐", "跨平台本地算法", orderedCandidates))
            }
            MeloXSettingsRuntime.homeSectionOrder.forEach { section ->
                when (section) {
                    "QuickActions" -> if (MeloXSettingsRuntime.homeQuickActionsEnabled) add(HomeBlock.QuickActions)
                    "Playlists" -> if (MeloXSettingsRuntime.homePlaylistsEnabled && value.playlists.isNotEmpty()) {
                        add(HomeBlock.Collections("每日推荐", "下拉刷新", value.playlists.map { DiscoveryCollection.Netease(it) }))
                    }
                    "NewSongs" -> if (MeloXSettingsRuntime.homeNewSongsEnabled && value.newSongs.isNotEmpty()) {
                        add(HomeBlock.Tracks("为你推荐", "新歌", value.newSongs.map { DiscoveryTrack.Netease(it) }))
                    }
                }
            }
            if (value.recentlyTrending.isNotEmpty()) add(HomeBlock.Tracks("近期云村热播", "来自网易云首页", value.recentlyTrending.map { DiscoveryTrack.Netease(it) }))
            if (value.tailoredSongs.isNotEmpty()) add(HomeBlock.Tracks("根据你的喜好为你推荐", "个性化", value.tailoredSongs.map { DiscoveryTrack.Netease(it) }))
            if (value.chartPlaylists.isNotEmpty()) add(HomeBlock.Collections("排行榜", "网易云榜单", value.chartPlaylists.map { DiscoveryCollection.Netease(it) }))
            if (value.radarPlaylists.isNotEmpty()) add(HomeBlock.Collections("私人雷达", "你的雷达歌单", value.radarPlaylists.map { DiscoveryCollection.Netease(it) }))
            if (value.personalPlaylists.isNotEmpty()) add(HomeBlock.Collections("我的歌单", "为你保留", value.personalPlaylists.map { DiscoveryCollection.Netease(it) }))
            if (value.regionalSongs.isNotEmpty()) add(HomeBlock.Tracks("${MeloXSettingsRuntime.musicArea}最近热门", "地区推荐", value.regionalSongs.map { DiscoveryTrack.Netease(it) }))
            if (value.roamingSongs.isNotEmpty()) add(HomeBlock.Tracks("私人漫游", "探索更多", value.roamingSongs.map { DiscoveryTrack.Netease(it) }))
            if (value.similarSongs.isNotEmpty()) add(HomeBlock.Tracks("相似歌曲", "根据当前播放", value.similarSongs.map { DiscoveryTrack.Netease(it) }))
            if (
                value.podcasts.isNotEmpty() &&
                MeloXSettingsRuntime.podcastsEnabled &&
                MeloXSettingsRuntime.podcastsHomePlacement
            ) add(HomeBlock.Podcasts(value.podcasts))
        }
    }

    val account = session.profile?.let { profile ->
        HomeAccountUi(
            name = profile.nickname.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) } ?: "网易云音乐用户",
            avatarUrl = profile.avatarUrl,
            subtitle = profile.signature
                ?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
                ?: "查看主页、听歌排行与歌单",
            onClick = { MeloXAccountActivity.launch(context, profile.userId) },
        )
    }

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
                    songs.firstOrNull()?.let {
                        PlaybackCommands.playQueue(context, songs, it.id, heartMode = action == "心动模式")
                    } ?: run { error = "没有可播放的推荐歌曲" }
                }.onFailure { error = it.message ?: "$action 加载失败" }
                activeAction = null
            }
        },
        onCollection = { selectedCollection = it },
    )
}

@Composable
private fun ProviderHomeDataScreen(source: MusicSource, onOpenTool: (String) -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val registry = remember(source) { MeloXMusicProviders.create(context) }
    val provider = remember(source, registry) { registry.require(source) }
    val home = provider as? HomeFeedCapability
    val library = provider as? UserLibraryCapability
    var feed by remember(source) { mutableStateOf<MusicHomeFeed?>(null) }
    var account by remember(source) { mutableStateOf<MusicAccountSummary?>(null) }
    var refreshing by remember(source) { mutableStateOf(false) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var selectedCollection by remember(source) { mutableStateOf<DiscoveryCollection?>(null) }

    selectedCollection?.let { collection ->
        DiscoveryCollectionDetail(collection = collection, onBack = { selectedCollection = null })
        return
    }

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

    LaunchedEffect(source) { refresh() }

    val blocks = feed?.let { value ->
        buildList<HomeBlock> {
            if (provider is PlaylistCapability && value.recommendedPlaylists.isNotEmpty()) {
                add(HomeBlock.Collections("每日推荐", source.displayName, value.recommendedPlaylists.map { DiscoveryCollection.ProviderPlaylist(it) }))
            }
            if (value.newSongs.isNotEmpty()) {
                add(HomeBlock.Tracks("为你推荐", "新歌", value.newSongs.map { DiscoveryTrack.Provider(it) }))
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
        onCollection = { selectedCollection = it },
    )
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
) {
    val context = LocalContext.current.applicationContext
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        if (blocks == null) {
            EmptyOrLoading(refreshing, error)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(top = 18.dp, bottom = 146.dp),
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
                            item { CollectionRow(block.values, onCollection) }
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
                                        Modifier.width(150.dp).clickable {
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
            .clip(CircleShape)
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

@Composable
private fun HomeQuickActions(source: MusicSource, active: String?, perform: (String) -> Unit) {
    data class Action(
        val title: String,
        val eyebrow: String,
        val subtitle: String,
        val symbol: MeloXSymbol,
        val colors: List<Color>,
    )
    val actions = buildList {
        if (source == MusicSource.Netease) {
            add(Action("每日推荐", "每日更新", "为你定制的歌曲", MeloXSymbol.Calendar, listOf(Color(0xFFFF5B8A), Color(0xFFFF3147))))
            add(Action("热歌榜", "全站热门", "大家都在听", MeloXSymbol.Flame, listOf(Color(0xFFFFA14A), Color(0xFFFF5A36))))
            add(Action("心动模式", "为你心动", "喜欢与惊喜交替播放", MeloXSymbol.Heart, listOf(Color(0xFFFF6EAC), Color(0xFF9B5DE5))))
            add(Action("私人雷达", "持续发现", "发现符合你口味的歌单", MeloXSymbol.RadioWaves, listOf(Color(0xFF6B7BFF), Color(0xFF8C52FF))))
            add(Action("私人漫游", "探索模式", "漫游到新的好音乐", MeloXSymbol.Walk, listOf(Color(0xFF26C6DA), Color(0xFF4285F4))))
            add(Action("相似歌曲", "从当前歌曲出发", "播放更多相似歌曲", MeloXSymbol.List, listOf(Color(0xFF58C9A3), Color(0xFF159D9A))))
        }
        add(Action("听歌识曲", "快捷工具", "识别环境中正在播放的歌曲", MeloXSymbol.Microphone, listOf(Color(0xFF7B61FF), Color(0xFF36C5F0))))
        if (source == MusicSource.Netease) add(Action("私信", "网易云社交", "查看联系人和私信会话", MeloXSymbol.Mail, listOf(Color(0xFFFF6B8B), Color(0xFFFF3B30))))
        if (source == MusicSource.Netease && MeloXSettingsRuntime.podcastsEnabled && MeloXSettingsRuntime.podcastsHomePlacement) {
            add(Action("播客", "首页页面", "浏览播客与节目", MeloXSymbol.RadioWaves, listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))))
        }
        if (MeloXSettingsRuntime.downloadsEnabled && MeloXSettingsRuntime.downloadsHomePlacement) {
            add(Action("下载", "本地音乐", "浏览已下载的歌曲", MeloXSymbol.Download, listOf(Color(0xFF0EA5E9), Color(0xFF14B8A6))))
        }
        if (source == MusicSource.Netease && MeloXSettingsRuntime.cloudMusicEnabled && MeloXSettingsRuntime.cloudHomePlacement) {
            add(Action("云盘", "个人音乐", "打开网易云音乐云盘", MeloXSymbol.Storage, listOf(Color(0xFF64748B), Color(0xFF6366F1))))
        }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(actions, key = { it.title }) { action ->
            Column(
                Modifier
                    .width(310.dp)
                    .clickable(enabled = active == null) { perform(action.title) },
            ) {
                Text(action.eyebrow.uppercase(), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(action.title, modifier = Modifier.padding(top = 3.dp), fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text(action.subtitle, modifier = Modifier.padding(top = 2.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.48f)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(action.colors)),
                    contentAlignment = Alignment.Center,
                ) {
                    MeloXSymbolIcon(
                        symbol = action.symbol,
                        modifier = Modifier.fillMaxSize(),
                        color = Color.White.copy(alpha = .24f),
                        iconSize = 72.sp,
                    )
                    Text(action.title, modifier = Modifier.align(Alignment.BottomStart).padding(18.dp), color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    if (active == action.title) {
                        CircularProgressIndicator(Modifier.size(52.dp), color = Color.White, strokeWidth = 3.dp)
                    }
                }
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
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { NeteaseLibraryCache(context) }
    val client = remember(context) { NeteaseLibraryClient({ NeteaseSessionStore.readCookie(context) }) }
    val scope = rememberCoroutineScope()
    val visibleCategories = Categories.filter { item ->
        (item != "精品歌单" || MeloXSettingsRuntime.showHighQualityPlaylists) &&
            (item != "播客" || MeloXSettingsRuntime.podcastsEnabled)
    }
    var category by remember { mutableStateOf(visibleCategories.first()) }
    var collections by remember { mutableStateOf<List<DiscoveryCollection>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedCollection by remember { mutableStateOf<DiscoveryCollection?>(null) }

    selectedCollection?.let { collection ->
        DiscoveryCollectionDetail(collection = collection, onBack = { selectedCollection = null })
        return
    }

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
        collections = cache.loadExplore(category).orEmpty().map { DiscoveryCollection.Netease(it) }
        if (NeteaseLibraryCache.beginExploreColdStartRefresh(category)) refresh()
    }

    MeloXExploreLayout(
        categories = visibleCategories,
        category = category,
        onCategory = { category = it },
        collections = collections,
        refreshing = refreshing,
        error = error,
        onRefresh = ::refresh,
        showPodcast = category == "播客",
        onCollection = { selectedCollection = it },
    )
}

@Composable
private fun ProviderExploreDataScreen(source: MusicSource) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val provider = remember(source) { MeloXMusicProviders.create(context).require(source) }
    val home = provider as? HomeFeedCapability
    var feed by remember(source) { mutableStateOf<MusicHomeFeed?>(null) }
    var category by remember(source) { mutableStateOf("推荐歌单") }
    var refreshing by remember(source) { mutableStateOf(false) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var selectedCollection by remember(source) { mutableStateOf<DiscoveryCollection?>(null) }

    selectedCollection?.let { collection ->
        DiscoveryCollectionDetail(collection = collection, onBack = { selectedCollection = null })
        return
    }

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

    LaunchedEffect(source) { refresh() }

    val categories = buildList {
        add("推荐歌单")
        if (feed?.rankings?.isNotEmpty() == true) add("排行榜")
    }
    val collections = when (category) {
        "排行榜" -> feed?.rankings.orEmpty().map { DiscoveryCollection.ProviderRanking(it) }
        else -> feed?.recommendedPlaylists.orEmpty().map { DiscoveryCollection.ProviderPlaylist(it) }
    }

    MeloXExploreLayout(
        categories = categories,
        category = category,
        onCategory = { category = it },
        collections = collections,
        refreshing = refreshing,
        error = if (home == null) "${source.displayName} 暂未提供发现数据" else error,
        onRefresh = ::refresh,
        showPodcast = false,
        onCollection = { selectedCollection = it },
    )
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
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 18.dp)) {
        MeloXIosTopBar(
            title = stringResource(R.string.tab_explore),
            // Explore is not inside a horizontally padded LazyColumn like
            // Home, so give the large title the same 20dp safe inset.
            contentPadding = PaddingValues(horizontal = 20.dp),
        )
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
        Box(modifier = Modifier.weight(1f)) {
            if (showPodcast) {
                MeloXPodcastScreen()
            } else {
                PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    if (collections.isEmpty()) EmptyOrLoading(refreshing, error) else CollectionGrid(collections, onCollection)
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

@Composable
private fun CollectionRow(values: List<DiscoveryCollection>, onSelect: (DiscoveryCollection) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(values, key = { _, value -> value.key }) { index, collection ->
            CollectionCard(collection, Modifier.width(if (index == 0) 246.dp else 174.dp)) { onSelect(collection) }
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
private fun CollectionGrid(values: List<DiscoveryCollection>, onSelect: (DiscoveryCollection) -> Unit) {
    val window = rememberMeloXWindowInfo()
    LazyVerticalGrid(
        columns = GridCells.Fixed(window.gridColumns),
        contentPadding = PaddingValues(start = window.gutter, end = window.gutter, bottom = 146.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        values.firstOrNull()?.let { hero ->
            item(key = "hero-${hero.key}", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                HeroCollectionCard(hero) { onSelect(hero) }
            }
        }
        items(values.drop(1), key = DiscoveryCollection::key) { collection ->
            CollectionCard(collection, Modifier.fillMaxWidth()) { onSelect(collection) }
        }
    }
}

@Composable
private fun HeroCollectionCard(value: DiscoveryCollection, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(if (rememberMeloXWindowInfo().supportsTwoPane) 2.9f else 1.75f)
            .clip(MeloXShapes.largeCard)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(value.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text("本周主推", color = Color.White.copy(alpha = .72f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(value.title, color = Color.White, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            (value.description ?: value.creatorName).takeIf(String::isNotBlank)?.let { Text(it, color = Color.White.copy(alpha = .72f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun CollectionCard(value: DiscoveryCollection, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        val artworkShape = RoundedCornerShape(14.dp)
        // Keep the card visually complete while remote artwork is loading.
        // Without a stable surface, the fixed image slot becomes a blank hole
        // and the title appears detached from its card on slower networks.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(174.dp)
                .clip(artworkShape),
        ) {
            AsyncImage(
                value.artworkUrl,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            value.title,
            modifier = Modifier.padding(top = 7.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (MeloXSettingsRuntime.showPlaylistPlayCount && value.playCount > 0L) {
            Text("${compactCount(value.playCount)} 次播放", color = MaterialTheme.colorScheme.onBackground.copy(alpha = .42f), fontSize = 11.sp)
        }
    }
}

private fun compactCount(value: Long): String = when {
    value >= 100_000_000L -> "%.1f亿".format(value / 100_000_000.0)
    value >= 10_000L -> "%.1f万".format(value / 10_000.0)
    else -> value.toString()
}

@Composable
private fun SongRow(song: DiscoveryTrack, onClick: () -> Unit) {
    val artworkShape = RoundedCornerShape(9.dp)
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
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun DiscoveryCollectionDetail(
    collection: DiscoveryCollection,
    onBack: () -> Unit,
) {
    when (collection) {
        is DiscoveryCollection.Netease -> {
            MeloXUnifiedPlaylistDetailScreen(collection.playlist, onBack)
            return
        }
        is DiscoveryCollection.ProviderPlaylist -> {
            MeloXUnifiedPlaylistDetailScreen(MeloXLegacyUiBridge.playlist(collection.playlist), onBack)
            return
        }
        is DiscoveryCollection.ProviderRanking -> Unit
    }
    val context = LocalContext.current.applicationContext
    var tracks by remember(collection.key) { mutableStateOf<List<DiscoveryTrack>?>(null) }
    var error by remember(collection.key) { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)

    LaunchedEffect(collection.key) {
        runCatching {
            withContext(Dispatchers.IO) {
                val ranking = (collection as DiscoveryCollection.ProviderRanking).ranking
                val provider = MeloXMusicProviders.create(context).require(ranking.id.source)
                val capability = provider as? RankingCapability
                    ?: throw IllegalStateException("${ranking.id.source.displayName} 当前不提供排行榜详情")
                capability.rankingTracks(ranking, page = 1, pageSize = 150)
                    .items.map { DiscoveryTrack.Provider(it) }
            }
        }.onSuccess { tracks = it }
            .onFailure { error = it.message ?: "内容加载失败" }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 146.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    MeloXActionIcon("‹", Modifier.size(22.dp), MaterialTheme.colorScheme.onBackground)
                }
                Text(collection.title, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(
                    collection.artworkUrl,
                    null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(156.dp).clip(RoundedCornerShape(22.dp)),
                )
                Column(Modifier.weight(1f)) {
                    Text(collection.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (collection.creatorName.isNotBlank()) {
                        Text(
                            collection.creatorName,
                            modifier = Modifier.padding(top = 7.dp),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
                            fontSize = 13.sp,
                        )
                    }
                    val value = tracks.orEmpty()
                    if (value.isNotEmpty()) {
                        Text(
                            "▶  播放全部",
                            modifier = Modifier
                                .padding(top = 15.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Accent)
                                .clickable { playDiscoveryQueue(context, value, value.first()) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        collection.description?.takeIf(String::isNotBlank)?.let { description ->
            item { Text(description, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f), fontSize = 13.sp, lineHeight = 19.sp) }
        }
        val value = tracks
        when {
            value != null -> items(value, key = DiscoveryTrack::key) { song ->
                SongRow(song) { playDiscoveryQueue(context, value, song) }
            }
            error != null -> item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            else -> item {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (loading) CircularProgressIndicator(color = Accent)
        else Text(error ?: "暂无内容", color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f))
    }
}
