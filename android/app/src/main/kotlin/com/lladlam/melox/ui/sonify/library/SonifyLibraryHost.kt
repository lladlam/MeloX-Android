/*
 * Sonify library navigation host (GPL-3.0), adapted for MeloX.
 *
 * This is the replacement for Sonify's NavHost + LibraryObject route table.
 * It keeps a small in-memory page stack so every Sonify library row opens a
 * Sonify page and Back returns to the Sonify hub, never to the MeloX segmented
 * library UI.
 *
 * Data sources are MeloX-only:
 *   NeteaseLibrarySnapshot / NeteaseLibraryClient / NeteaseCollectionDetailsClient
 *   NeteaseMusicOperationsClient
 *   MusicProvider, PlaylistCapability, AlbumCapability, ArtistCapability,
 *   PlaylistSyncCapability, MeloXUnifiedFavorites
 *   PlaybackCommands
 */
package com.lladlam.melox.ui.sonify.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.library.NeteaseLibrarySnapshot
import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.model.MusicAlbumSummary
import com.lladlam.melox.core.music.model.MusicArtistSummary
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.provider.AlbumCapability
import com.lladlam.melox.core.music.provider.ArtistCapability
import com.lladlam.melox.core.music.provider.MeloXLegacyUiBridge
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.core.music.provider.MeloXUnifiedFavorites
import com.lladlam.melox.core.music.provider.PlaylistCapability
import com.lladlam.melox.core.music.provider.PlaylistSyncCapability
import com.lladlam.melox.core.music.provider.loadAllAlbumTracks
import com.lladlam.melox.core.music.provider.loadAllArtistTracks
import com.lladlam.melox.core.music.provider.loadAllPlaylistTracks
import com.lladlam.melox.core.network.NeteaseCollectionDetailsClient
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.animation.meloXPageEnter
import com.lladlam.melox.ui.animation.meloXPageExit
import com.lladlam.melox.ui.glass.LocalMeloXIconScale
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.library.MeloXUnifiedPlaylistDetailScreen
import com.lladlam.melox.ui.player.MeloXSongActionsOverlay
import com.lladlam.melox.ui.sonify.SonifyLibraryEntry
import com.lladlam.melox.ui.sonify.SonifyLibraryPage
import kotlinx.coroutines.launch

@Composable
internal fun SonifyLibraryHost(
    source: MusicSource,
    session: NeteaseSessionStore,
    snapshot: NeteaseLibrarySnapshot?,
    providerAlbums: List<MusicAlbumSummary>,
    providerArtists: List<MusicArtistSummary>,
    loading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val detailsClient = remember(appContext) {
        NeteaseCollectionDetailsClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) })
    }
    val libraryClient = remember(appContext) {
        NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) })
    }
    val operationsClient = remember(appContext) {
        NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) })
    }
    val provider = remember(source, appContext) {
        if (source == MusicSource.Netease) null else runCatching {
            MeloXMusicProviders.create(appContext).require(source)
        }.getOrNull()
    }
    val playlistCapability = provider as? PlaylistCapability
    val albumCapability = provider as? AlbumCapability
    val artistCapability = provider as? ArtistCapability
    val playlistSync = provider as? PlaylistSyncCapability

    val stack = remember { mutableStateListOf<SonifyLibraryRoute>(SonifyLibraryRoute.Hub) }
    var navForward by remember { mutableStateOf(true) }
    fun push(route: SonifyLibraryRoute) {
        navForward = true
        stack.add(route)
    }
    fun pop() {
        if (stack.size > 1) {
            navForward = false
            stack.removeAt(stack.lastIndex)
        }
    }
    BackHandler(enabled = stack.size > 1) { pop() }

    var selectedSong by remember { mutableStateOf<SearchSong?>(null) }
    var selectedQueue by remember { mutableStateOf<List<SearchSong>>(emptyList()) }

    var dailySongs by remember { mutableStateOf<List<SearchSong>>(emptyList()) }
    var dailyLoading by remember { mutableStateOf(false) }
    var dailyError by remember { mutableStateOf<String?>(null) }

    var neteaseAlbums by remember { mutableStateOf<List<com.lladlam.melox.core.network.MeloXAlbumSummary>?>(null) }
    var neteaseAlbumsLoading by remember { mutableStateOf(false) }
    var neteaseAlbumsError by remember { mutableStateOf<String?>(null) }

    var neteaseArtists by remember { mutableStateOf<List<MusicArtistSummary>?>(null) }
    var neteaseArtistsLoading by remember { mutableStateOf(false) }
    var neteaseArtistsError by remember { mutableStateOf<String?>(null) }

    val current = stack.last()
    LaunchedEffect(current) {
        when (current) {
            SonifyLibraryRoute.DailyRecommendations -> {
                if (dailySongs.isNotEmpty() || dailyLoading) return@LaunchedEffect
                dailyLoading = true
                runCatching { libraryClient.dailyRecommendedSongs() }
                    .onSuccess {
                        dailySongs = it
                        dailyError = null
                    }
                    .onFailure { dailyError = it.message ?: "每日推荐加载失败" }
                dailyLoading = false
            }

            SonifyLibraryRoute.CollectedAlbums -> {
                if (source != MusicSource.Netease || neteaseAlbums != null || neteaseAlbumsLoading) return@LaunchedEffect
                neteaseAlbumsLoading = true
                runCatching { detailsClient.savedAlbums() }
                    .onSuccess {
                        neteaseAlbums = it
                        neteaseAlbumsError = null
                    }
                    .onFailure { neteaseAlbumsError = it.message ?: "收藏专辑加载失败" }
                neteaseAlbumsLoading = false
            }

            SonifyLibraryRoute.OnlineArtists -> {
                if (source != MusicSource.Netease || neteaseArtists != null || neteaseArtistsLoading) return@LaunchedEffect
                neteaseArtistsLoading = true
                runCatching { detailsClient.followedArtists() }
                    .onSuccess {
                        neteaseArtists = it
                        neteaseArtistsError = null
                    }
                    .onFailure { neteaseArtistsError = it.message ?: "关注艺人加载失败" }
                neteaseArtistsLoading = false
            }

            else -> Unit
        }
    }

    val likedSongs = snapshot?.likedSongs.orEmpty()
    val localAlbums = remember(likedSongs) { buildSonifyLocalAlbums(likedSongs) }
    val localArtists = remember(likedSongs) { buildSonifyLocalArtists(likedSongs) }
    val playlistTargets = remember(snapshot, likedSongs) {
        buildSonifyPlaylistTargets(snapshot, likedSongs)
    }
    val onlineArtistTargets: List<SonifyArtistTarget> = remember(source, providerArtists, neteaseArtists) {
        if (source == MusicSource.Netease) {
            neteaseArtists.orEmpty().map { artist ->
                SonifyArtistTarget(
                    name = artist.name,
                    artworkUrl = artist.artworkUrl,
                    neteaseArtistId = artist.id.value.toLongOrNull(),
                )
            }
        } else {
            providerArtists.map { it.asSonifyTarget() }
        }
    }
    val collectedAlbumCards: List<SonifyAlbumCard> = remember(source, providerAlbums, neteaseAlbums) {
        if (source == MusicSource.Netease) {
            neteaseAlbums.orEmpty().map { album ->
                SonifyAlbumCard(
                    title = album.name,
                    artistText = album.artistText,
                    artworkUrl = album.artworkUrl,
                    songCount = 0,
                    target = album.asSonifyTarget(),
                )
            }
        } else {
            providerAlbums.map { album ->
                SonifyAlbumCard(
                    title = album.title,
                    artistText = album.artists.joinToString(" / ") { it.name },
                    artworkUrl = album.artworkUrl,
                    songCount = album.trackCount?.toInt() ?: 0,
                    target = album.asSonifyTarget(),
                )
            }
        }
    }

    val pageBackground = MaterialTheme.colorScheme.background
    AnimatedContent(
        targetState = current,
        transitionSpec = {
            if (navForward) {
                (meloXPageEnter(veilColor = pageBackground) togetherWith
                    meloXPageExit(toRight = false)).using(null)
            } else {
                (meloXPageEnter(fromRight = false, veilColor = pageBackground) togetherWith
                    meloXPageExit(toRight = true)).using(null)
            }
        },
        label = "SonifyLibraryPage",
    ) { route ->
        CompositionLocalProvider(
            LocalMeloXIconScale provides if (route == SonifyLibraryRoute.Hub) 1f else 0.85f,
        ) {
            when (route) {
        SonifyLibraryRoute.Hub -> {
            val localEntries = buildList {
                add(
                    SonifyLibraryEntry("歌曲", icon = MeloXSymbol.MusicNote) {
                        push(
                            SonifyLibraryRoute.Songs(
                                title = "歌曲",
                                songs = likedSongs,
                                emptyText = "还没有收藏的歌曲",
                            ),
                        )
                    },
                )
                add(
                    SonifyLibraryEntry("专辑", icon = MeloXSymbol.MusicNote) {
                        push(SonifyLibraryRoute.LocalAlbums)
                    },
                )
                add(
                    SonifyLibraryEntry("艺人", icon = MeloXSymbol.Person) {
                        push(SonifyLibraryRoute.LocalArtists)
                    },
                )
                add(
                    SonifyLibraryEntry("播放列表", icon = MeloXSymbol.List) {
                        push(SonifyLibraryRoute.Playlists)
                    },
                )
            }
            val onlineEntries = buildList {
                add(
                    SonifyLibraryEntry("在线艺人", icon = MeloXSymbol.Person) {
                        push(SonifyLibraryRoute.OnlineArtists)
                    },
                )
                add(
                    SonifyLibraryEntry("我的在线歌单", icon = MeloXSymbol.List) {
                        push(SonifyLibraryRoute.OnlinePlaylists)
                    },
                )
                if (source == MusicSource.Netease) {
                    add(
                        SonifyLibraryEntry("每日推荐", icon = MeloXSymbol.Calendar) {
                            push(SonifyLibraryRoute.DailyRecommendations)
                        },
                    )
                }
                add(
                    SonifyLibraryEntry("专辑", icon = MeloXSymbol.MusicNote) {
                        push(SonifyLibraryRoute.CollectedAlbums)
                    },
                )
                // 音乐画廊：MeloX has no dedicated gallery destination. It is
                // deliberately omitted instead of shipping a dead row or
                // falling back to the MeloX library/player scaffold.
            }
            SonifyLibraryPage(
                localEntries = localEntries,
                onlineEntries = onlineEntries,
                showLocal = source == MusicSource.Netease ||
                    source == MusicSource.Local ||
                    likedSongs.isNotEmpty(),
                onOpenSettings = onOpenSettings ?: {},
            )
        }

        is SonifyLibraryRoute.Songs -> SonifyNormalMusicPage(
            title = route.title,
            songs = route.songs,
            emptyText = route.emptyText,
            loading = loading && route.songs.isEmpty(),
            error = errorMessage.takeIf { route.songs.isEmpty() },
            onBack = { pop() },
            onPlay = { queue, index ->
                val song = queue.getOrNull(index) ?: return@SonifyNormalMusicPage
                PlaybackCommands.playQueue(context, queue, song.id)
            },
            onMore = { song, queue ->
                selectedSong = song
                selectedQueue = queue
            },
            onRefresh = onRefresh,
            refreshing = loading,
            onRetry = onRefresh,
        )

        SonifyLibraryRoute.DailyRecommendations -> SonifyNormalMusicPage(
            title = "每日推荐",
            songs = dailySongs,
            emptyText = "今日暂无推荐",
            loading = dailyLoading,
            error = dailyError,
            onBack = { pop() },
            onPlay = { queue, index ->
                val song = queue.getOrNull(index) ?: return@SonifyNormalMusicPage
                PlaybackCommands.playQueue(context, queue, song.id)
            },
            onMore = { song, queue ->
                selectedSong = song
                selectedQueue = queue
            },
            onRefresh = {
                scope.launch {
                    dailyLoading = true
                    dailyError = null
                    runCatching { libraryClient.dailyRecommendedSongs() }
                        .onSuccess { dailySongs = it }
                        .onFailure { dailyError = it.message ?: "每日推荐加载失败" }
                    dailyLoading = false
                }
            },
            refreshing = dailyLoading,
            onRetry = {
                dailySongs = emptyList()
                dailyError = null
                scope.launch {
                    dailyLoading = true
                    runCatching { libraryClient.dailyRecommendedSongs() }
                        .onSuccess { dailySongs = it }
                        .onFailure { dailyError = it.message ?: "每日推荐加载失败" }
                    dailyLoading = false
                }
            },
        )

        SonifyLibraryRoute.LocalAlbums -> SonifyLocalAlbumsPage(
            albums = localAlbums,
            onBack = { pop() },
            onOpenAlbum = { push(SonifyLibraryRoute.Album(it)) },
            onRefresh = onRefresh,
            refreshing = loading,
            loading = loading && snapshot == null,
            error = errorMessage.takeIf { snapshot == null },
        )

        SonifyLibraryRoute.CollectedAlbums -> SonifyCollectedAlbumsPage(
            albums = collectedAlbumCards,
            onBack = { pop() },
            onOpenAlbum = { push(SonifyLibraryRoute.Album(it)) },
            onRefresh = {
                if (source == MusicSource.Netease) {
                    neteaseAlbums = null
                    neteaseAlbumsError = null
                    scope.launch {
                        neteaseAlbumsLoading = true
                        runCatching { detailsClient.savedAlbums() }
                            .onSuccess { neteaseAlbums = it }
                            .onFailure { neteaseAlbumsError = it.message ?: "收藏专辑加载失败" }
                        neteaseAlbumsLoading = false
                    }
                } else {
                    onRefresh()
                }
            },
            refreshing = if (source == MusicSource.Netease) neteaseAlbumsLoading else loading,
            loading = if (source == MusicSource.Netease) neteaseAlbumsLoading else loading && providerAlbums.isEmpty(),
            error = if (source == MusicSource.Netease) neteaseAlbumsError else errorMessage.takeIf { providerAlbums.isEmpty() },
        )

        is SonifyLibraryRoute.Album -> SonifyAlbumDetailPage(
            target = route.target,
            onBack = { pop() },
            onPlay = { queue, index ->
                val song = queue.getOrNull(index) ?: return@SonifyAlbumDetailPage
                PlaybackCommands.playQueue(context, queue, song.id)
            },
            onMore = { song, queue ->
                selectedSong = song
                selectedQueue = queue
            },
            loadAlbum = {
                loadSonifyAlbum(
                    target = route.target,
                    detailsClient = detailsClient,
                    albumCapability = albumCapability,
                )
            },
            setSubscribed = if (source == MusicSource.Netease && route.target.neteaseAlbumId != null) {
                { subscribed -> runCatching { detailsClient.setAlbumSubscribed(route.target.neteaseAlbumId, subscribed) } }
            } else {
                null
            },
        )

        SonifyLibraryRoute.LocalArtists -> SonifyLocalArtistsPage(
            artists = localArtists,
            onBack = { pop() },
            onOpenSongs = { artist ->
                push(
                    SonifyLibraryRoute.Songs(
                        title = artist.name,
                        songs = artist.songs,
                        emptyText = "这位艺人还没有本地歌曲",
                    ),
                )
            },
            onRefresh = onRefresh,
            refreshing = loading,
        )

        SonifyLibraryRoute.OnlineArtists -> SonifyOnlineArtistsPage(
            artists = onlineArtistTargets,
            onBack = { pop() },
            onOpenArtist = { push(SonifyLibraryRoute.Artist(it)) },
            onRefresh = {
                if (source == MusicSource.Netease) {
                    neteaseArtists = null
                    neteaseArtistsError = null
                    scope.launch {
                        neteaseArtistsLoading = true
                        runCatching { detailsClient.followedArtists() }
                            .onSuccess { neteaseArtists = it }
                            .onFailure { neteaseArtistsError = it.message ?: "关注艺人加载失败" }
                        neteaseArtistsLoading = false
                    }
                } else {
                    onRefresh()
                }
            },
            refreshing = if (source == MusicSource.Netease) neteaseArtistsLoading else loading,
            loading = if (source == MusicSource.Netease) neteaseArtistsLoading else loading && providerArtists.isEmpty(),
            error = if (source == MusicSource.Netease) neteaseArtistsError else errorMessage.takeIf { providerArtists.isEmpty() },
        )

        is SonifyLibraryRoute.Artist -> SonifyArtistPage(
            target = route.target,
            onBack = { pop() },
            onPlay = { queue, index ->
                val song = queue.getOrNull(index) ?: return@SonifyArtistPage
                PlaybackCommands.playQueue(context, queue, song.id)
            },
            onMoreSong = { song, queue ->
                selectedSong = song
                selectedQueue = queue
            },
            onOpenAlbum = { album -> push(SonifyLibraryRoute.Album(album.asSonifyTarget())) },
            loadArtist = {
                loadSonifyArtist(
                    target = route.target,
                    detailsClient = detailsClient,
                    artistCapability = artistCapability,
                )
            },
            setFollowed = if (source == MusicSource.Netease && route.target.neteaseArtistId != null) {
                { followed -> runCatching { detailsClient.setArtistFollowed(route.target.neteaseArtistId, followed) } }
            } else {
                null
            },
        )

        SonifyLibraryRoute.Playlists -> SonifyPlaylistsPage(
            playlists = playlistTargets,
            onBack = { pop() },
            onOpenPlaylist = { target -> push(SonifyLibraryRoute.Playlist(target)) },
            onRefresh = onRefresh,
            refreshing = loading,
            loading = loading && snapshot == null,
            error = errorMessage.takeIf { snapshot == null },
        )

        SonifyLibraryRoute.OnlinePlaylists -> SonifyOnlinePlaylistsPage(
            playlists = playlistTargets,
            onBack = { pop() },
            onOpenPlaylist = { target -> push(SonifyLibraryRoute.Playlist(target)) },
            onRefresh = onRefresh,
            refreshing = loading,
            loading = loading && snapshot == null,
            error = errorMessage.takeIf { snapshot == null },
            canCreate = source == MusicSource.Netease || playlistSync != null,
            onCreate = when {
                source == MusicSource.Netease -> { name ->
                    runCatching {
                        operationsClient.createPlaylist(name = name, privatePlaylist = false)
                        onRefresh()
                    }
                }

                playlistSync != null -> { name ->
                    runCatching {
                        playlistSync.createPlaylist(name)
                        onRefresh()
                    }
                }

                else -> null
            },
            canDelete = { target ->
                target.summary.id > 0 &&
                    !target.summary.isMeloXLocal &&
                    !target.isLikedSongs &&
                    target.summary.specialType != 5 &&
                    target.summary.id != snapshot?.likedPlaylistId &&
                    (
                        (
                            source == MusicSource.Netease &&
                                (
                                    target.summary.creatorUserId == null ||
                                        target.summary.creatorUserId == session.profile?.userId
                                    )
                            ) ||
                            (playlistSync?.canDeletePlaylists == true &&
                                target.summary.providerPlaylist != null)
                        )
            },
            onDelete = { target ->
                runCatching {
                    if (source == MusicSource.Netease) {
                        val userId = session.profile?.userId
                        val creator = target.summary.creatorUserId
                        if (userId == null || creator == null || creator == userId) {
                            operationsClient.deletePlaylist(target.summary.id)
                        } else {
                            operationsClient.setPlaylistSubscribed(target.summary.id, false)
                        }
                    } else {
                        val summary = target.summary.providerPlaylist
                        val sync = playlistSync
                        if (summary == null || sync == null) error("这个音源不能删除歌单")
                        sync.deletePlaylist(summary)
                    }
                    onRefresh()
                }
            },
        )

        is SonifyLibraryRoute.Playlist -> MeloXUnifiedPlaylistDetailScreen(
            playlist = route.target.summary,
            onBack = { pop() },
        )

        SonifyLibraryRoute.FavoriteSongs -> SonifyFavoriteSongsPage(
            songs = likedSongs,
            onBack = { pop() },
            onPlay = { queue, index ->
                val song = queue.getOrNull(index) ?: return@SonifyFavoriteSongsPage
                PlaybackCommands.playQueue(context, queue, song.id)
            },
            onMore = { song, queue ->
                selectedSong = song
                selectedQueue = queue
            },
            onRefresh = onRefresh,
            refreshing = loading,
            loading = loading && likedSongs.isEmpty(),
            error = errorMessage.takeIf { likedSongs.isEmpty() },
            onRetry = onRefresh,
        )
                }
        }
    }

    selectedSong?.let { song ->
        MeloXSongActionsOverlay(
            song = song,
            queue = selectedQueue.ifEmpty { listOf(song) },
            visible = true,
            onDismiss = { selectedSong = null },
        )
    }
}

private fun buildSonifyLocalAlbums(songs: List<SearchSong>): List<SonifyLocalAlbum> =
    songs.groupBy { song ->
        song.albumId?.let { "id:$it" } ?: "name:${song.album.ifBlank { "未知专辑" }}"
    }.map { (_, grouped) ->
        val first = grouped.first()
        SonifyLocalAlbum(
            name = first.album.ifBlank { "未知专辑" },
            artistText = first.artists.ifBlank { "未知歌手" },
            artworkUrl = grouped.firstNotNullOfOrNull { it.artworkUrl },
            songs = grouped,
        )
    }.sortedBy { it.name.lowercase() }

private fun buildSonifyLocalArtists(songs: List<SearchSong>): List<SonifyLocalArtist> =
    songs.groupBy { song ->
        song.artists.substringBefore(" / ").ifBlank { "未知歌手" }
    }.map { (name, grouped) ->
        SonifyLocalArtist(
            name = name,
            artworkUrl = grouped.firstNotNullOfOrNull { it.artworkUrl },
            songs = grouped,
            neteaseArtistId = grouped.firstNotNullOfOrNull { it.artistId },
        )
    }.sortedBy { it.name.lowercase() }

private fun buildSonifyPlaylistTargets(
    snapshot: NeteaseLibrarySnapshot?,
    likedSongs: List<SearchSong>,
): List<SonifyPlaylistTarget> {
    if (snapshot == null) return emptyList()
    val existingLiked = snapshot.playlists.firstOrNull { it.id == snapshot.likedPlaylistId }
        ?: snapshot.playlists.firstOrNull { it.specialType == 5 }
    val syntheticLiked = if (
        existingLiked == null &&
        (likedSongs.isNotEmpty() || snapshot.likedPlaylistId != null)
    ) {
        NeteasePlaylistSummary(
            id = snapshot.likedPlaylistId ?: -1L,
            name = "我喜欢的音乐",
            coverUrl = likedSongs.firstNotNullOfOrNull { it.artworkUrl },
            trackCount = likedSongs.size,
            specialType = 5,
            creatorName = "网易云音乐",
        )
    } else {
        null
    }
    val likedSummary = existingLiked ?: syntheticLiked
    val likedTarget = likedSummary?.let { summary ->
        SonifyPlaylistTarget(
            summary = summary.copy(
                trackCount = summary.trackCount.takeIf { it > 0 } ?: likedSongs.size,
            ),
            providedSongs = likedSongs,
            isLikedSongs = true,
        )
    }
    val rest = snapshot.playlists
        .filterNot { likedSummary != null && it.id == likedSummary.id }
        .map { SonifyPlaylistTarget(summary = it) }
    return listOfNotNull(likedTarget) + rest
}

private suspend fun loadSonifyAlbum(
    target: SonifyAlbumTarget,
    detailsClient: NeteaseCollectionDetailsClient,
    albumCapability: AlbumCapability?,
): Result<SonifyAlbumData> = runCatching {
    val neteaseId = target.neteaseAlbumId
    when {
        neteaseId != null -> {
            val detail = detailsClient.albumDetail(neteaseId)
            SonifyAlbumData(
                title = detail.album.name,
                artistText = detail.album.artistText,
                artworkUrl = detail.album.artworkUrl ?: target.artworkUrl,
                description = detail.description,
                songs = detail.songs,
                subscribed = detail.subscribed,
            )
        }

        target.providerAlbum != null -> {
            val capability = albumCapability ?: error("这个音源没有专辑详情能力")
            val detail = capability.loadAllAlbumTracks(target.providerAlbum)
            SonifyAlbumData(
                title = detail.summary.title,
                artistText = detail.summary.artists.joinToString(" / ") { it.name },
                artworkUrl = detail.summary.artworkUrl,
                description = null,
                songs = detail.tracks.map(MeloXLegacyUiBridge::track),
            )
        }

        target.localSongs.isNotEmpty() -> SonifyAlbumData(
            title = target.title,
            artistText = target.artistText,
            artworkUrl = target.artworkUrl,
            description = null,
            songs = target.localSongs,
        )

        else -> error("专辑不可用")
    }
}

private suspend fun loadSonifyPlaylist(
    target: SonifyPlaylistTarget,
    libraryClient: NeteaseLibraryClient,
    playlistCapability: PlaylistCapability?,
    likedSongs: List<SearchSong>,
    context: android.content.Context,
): Result<SonifyPlaylistData> = runCatching {
    if (target.isLikedSongs) {
        return@runCatching SonifyPlaylistData(
            name = target.summary.name,
            artworkUrl =
                target.summary.coverUrl ?: (target.providedSongs ?: likedSongs).firstNotNullOfOrNull { it.artworkUrl },
            description = target.summary.description,
            songs = target.providedSongs ?: likedSongs,
            creatorName = target.summary.creatorName,
        )
    }
    if (target.summary.isMeloXLocal) {
        return@runCatching SonifyPlaylistData(
            name = target.summary.name,
            artworkUrl = target.summary.coverUrl,
            description = target.summary.description,
            songs = MeloXUnifiedFavorites.tracks(context).map(MeloXLegacyUiBridge::track),
            creatorName = target.summary.creatorName,
        )
    }
    val providerPlaylist = target.summary.providerPlaylist
    if (providerPlaylist != null) {
        val capability = playlistCapability ?: error("这个音源没有歌单详情能力")
        val detail = capability.loadAllPlaylistTracks(providerPlaylist)
        return@runCatching SonifyPlaylistData(
            name = detail.summary.title,
            artworkUrl = detail.summary.artworkUrl ?: target.summary.coverUrl,
            description = detail.summary.description ?: target.summary.description,
            songs = detail.tracks.map(MeloXLegacyUiBridge::track),
            creatorName = detail.summary.creatorName,
        )
    }
    if (target.summary.id <= 0L) error("歌单不可用")
    val detail = libraryClient.playlistDetail(target.summary.id)
    SonifyPlaylistData(
        name = detail.summary.name,
        artworkUrl = detail.summary.coverUrl,
        description = detail.summary.description,
        songs = detail.songs,
        creatorName = detail.summary.creatorName,
        subscribed = detail.summary.subscribed,
    )
}

private suspend fun loadSonifyArtist(
    target: SonifyArtistTarget,
    detailsClient: NeteaseCollectionDetailsClient,
    artistCapability: ArtistCapability?,
): Result<SonifyArtistData> = runCatching {
    val neteaseId = target.neteaseArtistId
    if (neteaseId != null) {
        val artist = detailsClient.artistDetail(neteaseId)
        val songs = buildList {
            var offset = 0
            while (offset < 500) {
                val page = detailsClient.artistSongs(neteaseId, 100, offset)
                if (page.isEmpty()) break
                addAll(page)
                if (page.size < 100) break
                offset += page.size
            }
        }
        val allSongs = if (songs.isNotEmpty()) songs else artist.hotSongs
        return@runCatching SonifyArtistData(
            artist = artist,
            allSongs = allSongs,
            canFollow = true,
        )
    }
    val summary = target.providerArtist
    if (summary != null) {
        val capability = artistCapability ?: error("这个音源没有歌手详情能力")
        val detail = capability.loadAllArtistTracks(summary)
        val songs = detail.tracks.map(MeloXLegacyUiBridge::track)
        return@runCatching SonifyArtistData(
            artist = com.lladlam.melox.core.network.MeloXArtistDetail(
                id = summary.id.value.hashCode().toLong(),
                name = detail.summary.name,
                artworkUrl = detail.summary.artworkUrl,
                coverUrl = detail.summary.artworkUrl,
                aliases = emptyList(),
                description = detail.summary.description,
                musicSize = detail.totalTracks?.toInt() ?: songs.size,
                albumSize = detail.summary.albumCount?.toInt() ?: 0,
                mvSize = 0,
                followed = null,
                hotSongs = songs.take(20),
                albums = emptyList(),
            ),
            allSongs = songs,
            canFollow = false,
        )
    }
    error("歌手数据不可用")
}
