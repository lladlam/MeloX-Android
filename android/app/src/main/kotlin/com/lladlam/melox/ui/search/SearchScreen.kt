package com.lladlam.melox.ui.search

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.R
import com.lladlam.melox.core.account.NeteaseSessionStore
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
import com.lladlam.melox.ui.settings.MeloXSwipeFullAction
import com.lladlam.melox.ui.player.MeloXSongActionsOverlay
import com.lladlam.melox.ui.layout.rememberMeloXWindowInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MeloXSearchLaunch(val query: String, val kind: MeloXSearchKind, val nonce: Long = System.nanoTime())
object MeloXSearchLaunchBus {
    var request by mutableStateOf<MeloXSearchLaunch?>(null)
        private set
    fun post(query: String, kind: MeloXSearchKind) { request = MeloXSearchLaunch(query, kind) }
    fun consume(request: MeloXSearchLaunch) { if (this.request == request) this.request = null }
}

private val SearchAccent = MeloXSystemColors.Blue
private val SearchCategories = listOf("排行榜", "播客", "华语", "欧美", "日语", "韩语", "粤语", "流行", "摇滚", "民谣", "电子", "说唱", "R&B/Soul", "古典", "ACG", "影视原声", "学习", "工作", "放松", "夜晚")

private sealed interface ProviderSearchDestination {
    val source: MusicSource
    val key: String
    val kind: MeloXSearchKind
    val title: String
    val subtitle: String
    val artworkUrl: String?

    data class Playlist(val value: MusicPlaylistSummary) : ProviderSearchDestination {
        override val source = value.id.source
        override val key = "playlist:${source.storageValue}:${value.id.value}"
        override val kind = MeloXSearchKind.Playlists
        override val title = value.title
        override val subtitle = value.creatorName.orEmpty()
        override val artworkUrl = value.artworkUrl
    }

    data class Album(val value: MusicAlbumSummary) : ProviderSearchDestination {
        override val source = value.id.source
        override val key = "album:${source.storageValue}:${value.id.value}"
        override val kind = MeloXSearchKind.Albums
        override val title = value.title
        override val subtitle = value.artists.joinToString(" / ") { it.name }
        override val artworkUrl = value.artworkUrl
    }

    data class Artist(val value: MusicArtistSummary) : ProviderSearchDestination {
        override val source = value.id.source
        override val key = "artist:${source.storageValue}:${value.id.value}"
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

@Composable
fun SearchScreen(source: MusicSource = MusicSource.Netease) {
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
    val unifiedSources = MusicProviderSelectionStore.unifiedSources(appContext)

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
    var searchTrigger by rememberSaveable(source.name) { mutableIntStateOf(0) }
    var skipSearchDebounce by rememberSaveable(source.name) { mutableStateOf(false) }
    var kind by rememberSaveable(source.name) { mutableStateOf(MeloXSearchKind.Songs) }
    var songs by remember(source) { mutableStateOf<List<SearchSong>>(emptyList()) }
    var providerSongs by remember(source) { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var providerPlaylists by remember(source) { mutableStateOf<List<MusicPlaylistSummary>>(emptyList()) }
    var providerAlbums by remember(source) { mutableStateOf<List<MusicAlbumSummary>>(emptyList()) }
    var providerArtists by remember(source) { mutableStateOf<List<MusicArtistSummary>>(emptyList()) }
    var providerRecommendations by remember(source) { mutableStateOf<List<MusicPlaylistSummary>>(emptyList()) }
    var unifiedFailures by remember(source) { mutableStateOf<List<UnifiedMusicService.SearchFailure>>(emptyList()) }
    var media by remember(source) { mutableStateOf<List<MeloXSearchMediaItem>>(emptyList()) }
    var recommendations by remember(source) { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var categoryTitle by remember(source) { mutableStateOf<String?>(null) }
    var categoryPlaylists by remember(source) { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var selectedDetail by remember(source) { mutableStateOf<SearchDetailDestination?>(null) }
    var podcastDiscovery by remember(source) { mutableStateOf(false) }
    var loading by remember(source) { mutableStateOf(false) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var selectedActionSong by remember(source) { mutableStateOf<SearchSong?>(null) }
    val launchRequest = MeloXSearchLaunchBus.request

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

    LaunchedEffect(query, kind, source, unifiedEnabled, unifiedSources, searchTrigger) {
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
            return@LaunchedEffect
        }

        when (kind) {
            MeloXSearchKind.Songs -> {
                media = emptyList()
                providerPlaylists = emptyList()
                providerAlbums = emptyList()
                providerArtists = emptyList()
                if (unifiedEnabled) {
                    songs = emptyList()
                    runCatching {
                        withContext(Dispatchers.IO) {
                            unifiedService.searchSongs(
                                query = keyword,
                                sources = unifiedSources,
                                page = 1,
                                pageSizePerProvider = 25,
                            )
                        }
                    }.onSuccess { result ->
                        providerSongs = result.aggregated.mapNotNull { it.recommendation?.track }.ifEmpty { result.tracks }
                        unifiedFailures = result.failures
                    }.onFailure { failure ->
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
                            withContext(Dispatchers.IO) { capability.searchSongs(keyword, page = 1, pageSize = 50).items }
                        }.onSuccess { providerSongs = it }
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
        loading = false
    }

    BackHandler(enabled = podcastDiscovery || selectedDetail != null || categoryTitle != null) {
        when {
            podcastDiscovery -> podcastDiscovery = false
            selectedDetail != null -> selectedDetail = null
            else -> { categoryTitle = null; categoryPlaylists = emptyList() }
        }
    }

    if (podcastDiscovery) {
        MeloXPodcastScreen()
        return
    }

    selectedDetail?.let { destination ->
        SearchCollectionDetail(
            destination = destination,
            universal = universal,
            library = library,
            providerRegistry = providerRegistry,
            onBack = { selectedDetail = null },
        )
        return
    }

    categoryTitle?.let { title ->
        SearchCategoryPage(title, categoryPlaylists, loading, error, onBack = {
            categoryTitle = null; categoryPlaylists = emptyList(); error = null
        }, onPlaylist = { selectedDetail = SearchDetailDestination.Netease(it.asSearchItem()) })
        return
    }

    val window = rememberMeloXWindowInfo()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 26.dp)
            .padding(horizontal = if (window.supportsTwoPane) window.gutter else 0.dp),
    ) {
        MeloXIosTopBar(title = stringResource(R.string.tab_search))
        Spacer(Modifier.height(16.dp))
        SearchField(
            value = query,
            onValueChange = { query = it; skipSearchDebounce = false },
            onSearch = { skipSearchDebounce = true; searchTrigger += 1 },
            source = source,
        )
        if (query.isNotBlank()) {
            SearchScopes(kind = kind, availableKinds = availableKinds, onKind = { kind = it })
        }

        Box(Modifier.weight(1f)) {
            when {
                query.isBlank() && source == MusicSource.Netease -> SearchDiscovery(
                    recommendations = recommendations,
                    onPlaylist = { selectedDetail = SearchDetailDestination.Netease(it.asSearchItem()) },
                    onCategory = { category ->
                        if (category == "播客") {
                            podcastDiscovery = true
                        } else {
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
                    source = source,
                    recommendations = providerRecommendations,
                    onPlaylist = { selectedDetail = SearchDetailDestination.Provider(ProviderSearchDestination.Playlist(it)) },
                )
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SearchAccent)
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
                kind == MeloXSearchKind.Songs && (unifiedEnabled || source != MusicSource.Netease) -> ProviderSearchSongResults(
                    values = providerSongs,
                    failures = unifiedFailures,
                    showSource = unifiedEnabled,
                    onPlay = { track ->
                        ProviderPlaybackCommands.playQueue(
                            context = context,
                            tracks = providerSongs,
                            selectedTrackId = track.id,
                            onFailure = { failure -> error = failure.message ?: "播放失败" },
                        )
                    },
                )
                kind == MeloXSearchKind.Songs -> SearchSongResults(
                    values = songs,
                    onPlay = { song -> PlaybackCommands.playQueue(context, songs, song.id) },
                    onMore = { selectedActionSong = it },
                    onLike = { song ->
                        scope.launch {
                            runCatching { operations.setSongLiked(song.id, true) }
                                .onFailure { error = it.message ?: "添加到资料库失败" }
                        }
                    },
                )
                source != MusicSource.Netease && kind == MeloXSearchKind.Playlists -> ProviderSearchMediaResults(
                    values = providerPlaylists.map { ProviderSearchDestination.Playlist(it) },
                    onOpen = { selectedDetail = SearchDetailDestination.Provider(it) },
                )
                source != MusicSource.Netease && kind == MeloXSearchKind.Albums -> ProviderSearchMediaResults(
                    values = providerAlbums.map { ProviderSearchDestination.Album(it) },
                    onOpen = { selectedDetail = SearchDetailDestination.Provider(it) },
                )
                source != MusicSource.Netease && kind == MeloXSearchKind.Artists -> ProviderSearchMediaResults(
                    values = providerArtists.map { ProviderSearchDestination.Artist(it) },
                    onOpen = { selectedDetail = SearchDetailDestination.Provider(it) },
                )
                else -> SearchMediaResults(media) { item ->
                    when (item.kind) {
                        MeloXSearchKind.Albums, MeloXSearchKind.Artists, MeloXSearchKind.Podcasts -> MeloXCollectionDetailActivity.launch(context, item)
                        MeloXSearchKind.Users -> MeloXAccountActivity.launch(context, item.id)
                        else -> selectedDetail = SearchDetailDestination.Netease(item)
                    }
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
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    source: MusicSource,
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
                modifier = Modifier.size(21.dp),
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
    recommendations: List<NeteasePlaylistSummary>,
    onPlaylist: (NeteasePlaylistSummary) -> Unit,
    onCategory: (String) -> Unit,
) {
    val window = rememberMeloXWindowInfo()
    val categoryColumns = window.gridColumns.coerceIn(2, 4)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MeloXBottomContentClearance),
    ) {
        if (recommendations.isNotEmpty()) {
            item { Text("热门推荐", modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(recommendations, key = { it.id }) { p ->
                        Column(Modifier.width(160.dp).clickable { onPlaylist(p) }) {
                            AsyncImage(p.coverUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(160.dp).clip(RoundedCornerShape(14.dp)))
                            Text(p.name, modifier = Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        item { Text("浏览类别", modifier = Modifier.padding(start = 20.dp, top = 26.dp, bottom = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
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
    source: MusicSource,
    recommendations: List<MusicPlaylistSummary>,
    onPlaylist: (MusicPlaylistSummary) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MeloXBottomContentClearance),
    ) {
        if (recommendations.isNotEmpty()) {
            item { Text("热门推荐", modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(recommendations, key = { "${it.id.source.storageValue}:${it.id.value}" }) { p ->
                        Column(Modifier.width(160.dp).clickable { onPlaylist(p) }) {
                            AsyncImage(p.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(160.dp).clip(RoundedCornerShape(14.dp)))
                            Text(p.title, modifier = Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
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
private fun ProviderSearchSongResults(
    values: List<MusicTrack>,
    failures: List<UnifiedMusicService.SearchFailure>,
    showSource: Boolean,
    onPlay: (MusicTrack) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = MeloXBottomContentClearance),
    ) {
        if (failures.isNotEmpty()) {
            item {
                Text(
                    failures.joinToString("；") { "${it.source.displayName}：${it.message}" },
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        if (values.isEmpty()) {
            item { SearchEmptyInline("没有找到歌曲") }
        } else {
            items(values, key = { "provider:${it.id.source.storageValue}:${it.id.value}" }) { track ->
                SearchSwipeSongRow(
                    song = MeloXLegacyUiBridge.track(track),
                    onPlay = { onPlay(track) },
                    onMore = null,
                    endAction = null,
                    sourceLabel = track.id.source.displayName.takeIf { showSource },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
            }
        }
    }
}

@Composable
private fun ProviderSearchMediaResults(
    values: List<ProviderSearchDestination>,
    onOpen: (ProviderSearchDestination) -> Unit,
) {
    if (values.isEmpty()) { SearchEmpty("没有找到内容"); return }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = MeloXBottomContentClearance)) {
        items(values, key = ProviderSearchDestination::key) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(item) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    item.artworkUrl,
                    null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(54.dp).clip(if (item.kind == MeloXSearchKind.Artists) CircleShape else RoundedCornerShape(8.dp)),
                )
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

@Composable
private fun SearchSongResults(
    values: List<SearchSong>,
    onPlay: (SearchSong) -> Unit,
    onMore: (SearchSong) -> Unit,
    onLike: (SearchSong) -> Unit,
) {
    if (values.isEmpty()) { SearchEmpty("没有找到歌曲"); return }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = MeloXBottomContentClearance)) {
        items(values, key = { it.id }) { song ->
            SearchSwipeSongRow(
                song = song,
                onPlay = { onPlay(song) },
                onMore = { onMore(song) },
                endAction = MeloXSwipeAction("添加到资料库", MeloXSymbol.Heart, Color(0xFFFF3B30)) { onLike(song) },
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
            MeloXSwipeAction("下一首播放", MeloXSymbol.Next, Color(0xFF8E5AF7)) { PlaybackCommands.playNext(context, song) },
            MeloXSwipeAction("稍后播放", MeloXSymbol.Queue, Color(0xFFFF9F0A)) { PlaybackCommands.addToQueue(context, song) },
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
                        add(song.artists)
                        song.album.takeIf(String::isNotBlank)?.let(::add)
                        sourceLabel?.let(::add)
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
private fun SearchMediaResults(values: List<MeloXSearchMediaItem>, onOpen: (MeloXSearchMediaItem) -> Unit) {
    if (values.isEmpty()) { SearchEmpty("没有找到内容"); return }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = MeloXBottomContentClearance)) {
        items(values, key = { "${it.kind}-${it.id}" }) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(item) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(item.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(54.dp).clip(if (item.kind == MeloXSearchKind.Artists || item.kind == MeloXSearchKind.Users) CircleShape else RoundedCornerShape(8.dp)))
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

@Composable
private fun SearchCategoryPage(
    title: String,
    values: List<NeteasePlaylistSummary>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onPlaylist: (NeteasePlaylistSummary) -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 16.dp)) {
        SearchDetailHeader(title, onBack)
        when {
            title == "播客" -> SearchEmpty("播客请使用上方搜索框切换到“播客”范围进行搜索。")
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> SearchEmpty(error)
            values.isEmpty() -> SearchEmpty("暂无内容")
            else -> LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = MeloXBottomContentClearance)) {
                items(values, key = { it.id }) { p ->
                    Row(Modifier.fillMaxWidth().clickable { onPlaylist(p) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(p.coverUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(58.dp).clip(RoundedCornerShape(9.dp)))
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

@Composable
private fun SearchCollectionDetail(
    destination: SearchDetailDestination,
    universal: NeteaseUniversalSearchClient,
    library: NeteaseLibraryClient,
    providerRegistry: com.lladlam.melox.core.music.provider.MusicProviderRegistry,
    onBack: () -> Unit,
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
            )
            return
        }
        is SearchDetailDestination.Provider -> {
            val value = destination.value
            if (value is ProviderSearchDestination.Playlist) {
                MeloXUnifiedPlaylistDetailScreen(MeloXLegacyUiBridge.playlist(value.value), onBack)
                return
            }
            if (value is ProviderSearchDestination.Album) {
                MeloXUnifiedProviderAlbumDetailScreen(value.value, onBack)
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
                        val provider = providerRegistry.require(item.source)
                        val tracks = when (item) {
                            is ProviderSearchDestination.Playlist -> {
                                val capability = provider as? PlaylistCapability
                                    ?: throw IllegalStateException("${item.source.displayName} 当前不提供歌单详情")
                                capability.playlistDetail(item.value, page = 1, pageSize = 150).tracks
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

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 16.dp)) {
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
                            SearchPlayButton("随机") {
                                if (providerTracks.isNotEmpty()) {
                                    val shuffled = providerTracks.shuffled()
                                    shuffled.firstOrNull()?.let { ProviderPlaybackCommands.playQueue(context, shuffled, it.id) }
                                } else {
                                    val shuffled = songs.shuffled()
                                    shuffled.firstOrNull()?.let { PlaybackCommands.playQueue(context, shuffled, it.id) }
                                }
                            }
                            SearchPlayButton("播放") {
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
                providerTracks.isNotEmpty() -> items(providerTracks, key = { "detail:${it.id.source.storageValue}:${it.id.value}" }) { track ->
                    SearchSwipeSongRow(
                        song = MeloXLegacyUiBridge.track(track),
                        onPlay = { ProviderPlaybackCommands.playQueue(context, providerTracks, track.id) },
                        onMore = null,
                        endAction = null,
                        sourceLabel = track.id.source.displayName,
                    )
                }
                else -> items(songs, key = { it.id }) { song ->
                    SearchSwipeSongRow(
                        song = song,
                        onPlay = { PlaybackCommands.playQueue(context, songs, song.id) },
                        onMore = { selectedActionSong = song },
                        endAction = MeloXSwipeAction("添加到资料库", MeloXSymbol.Heart, Color(0xFFFF3B30)) {
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
