package com.lladlam.melox.core.provider.youtubemusic

import android.content.Context
import android.net.Uri
import com.lladlam.melox.core.lyrics.LrcLibLyricsClient
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicAccountSummary
import com.lladlam.melox.core.music.model.MusicAlbumDetail
import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicAlbumSummary
import com.lladlam.melox.core.music.model.MusicArtistDetail
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicArtistSummary
import com.lladlam.melox.core.music.model.MusicHomeFeed
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicPlaylistDetail
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicRankingSummary
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.music.model.TrackAvailability
import com.lladlam.melox.core.music.provider.AlbumCapability
import com.lladlam.melox.core.music.provider.ArtistCapability
import com.lladlam.melox.core.music.provider.CatalogSearchCapability
import com.lladlam.melox.core.music.provider.DownloadCapability
import com.lladlam.melox.core.music.provider.FavoriteCapability
import com.lladlam.melox.core.music.provider.HomeFeedCapability
import com.lladlam.melox.core.music.provider.LibraryCollectionCapability
import com.lladlam.melox.core.music.provider.LyricsCapability
import com.lladlam.melox.core.music.provider.MusicCapability
import com.lladlam.melox.core.music.provider.MusicProvider
import com.lladlam.melox.core.music.provider.PlaybackCapability
import com.lladlam.melox.core.music.provider.PlaylistCapability
import com.lladlam.melox.core.music.provider.PlaylistSyncCapability
import com.lladlam.melox.core.music.provider.PlaylistWriteCapability
import com.lladlam.melox.core.music.provider.RankingCapability
import com.lladlam.melox.core.music.provider.SearchCapability
import com.lladlam.melox.core.music.provider.UserLibraryCapability
import com.lladlam.melox.core.music.provider.loadAllPlaylistTracks
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.ChartsPage
import com.metrolist.innertube.pages.HomePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicBoolean

/** Square-compatible YouTube Music backend: InnerTube catalogue, NewPipe streams. */
class YouTubeMusicProvider(
    context: Context,
    private val httpClient: okhttp3.OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) : MusicProvider, SearchCapability, CatalogSearchCapability, PlaybackCapability,
    DownloadCapability, FavoriteCapability, UserLibraryCapability, PlaylistCapability, PlaylistWriteCapability,
    AlbumCapability, ArtistCapability, HomeFeedCapability, LyricsCapability, RankingCapability,
    LibraryCollectionCapability, PlaylistSyncCapability {
    private val appContext = context.applicationContext
    private val session: YouTubeSession
        get() = YouTubeSessionStore.read(appContext)

    init {
        YouTubeSessionStore.apply(appContext)
    }

    override val source = MusicSource.YouTubeMusic
    override val displayName = source.displayName
    override val capabilities = setOf(
        MusicCapability.Search,
        MusicCapability.Playback,
        MusicCapability.Lyrics,
        MusicCapability.Library,
        MusicCapability.Favorites,
        MusicCapability.Rankings,
        MusicCapability.Playlists,
        MusicCapability.PlaylistWrite,
        MusicCapability.Albums,
        MusicCapability.Artists,
        MusicCapability.HomeRecommendations,
    )

    private val newPipeInitialised = AtomicBoolean(false)
    private val lyricsClient = LrcLibLyricsClient(httpClient)
    private val catalogPager = YouTubeCursorPager<YTItem> { "${it.javaClass.simpleName}:${it.id}" }
    private val playlistPager = YouTubeCursorPager<SongItem> { it.setVideoId ?: it.id }
    private val playlistSummaries = java.util.concurrent.ConcurrentHashMap<String, MusicPlaylistSummary>()

    private fun requestKey(value: String): String =
        "$value:${YouTubeSessionStore.authFingerprint(appContext)}:${appContext.resources.configuration.locales[0].toLanguageTag()}"

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> =
        search(query, page, pageSize, YouTube.SearchFilter.FILTER_SONG) { item -> (item as? SongItem)?.toMusicTrack() }

    override suspend fun searchPlaylists(query: String, page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        search(query, page, pageSize, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST) { item ->
            (item as? PlaylistItem)?.toPlaylistSummary()
        }

    override suspend fun searchAlbums(query: String, page: Int, pageSize: Int): MusicPage<MusicAlbumSummary> =
        search(query, page, pageSize, YouTube.SearchFilter.FILTER_ALBUM) { item ->
            (item as? AlbumItem)?.toAlbumSummary()
        }

    override suspend fun searchArtists(query: String, page: Int, pageSize: Int): MusicPage<MusicArtistSummary> =
        search(query, page, pageSize, YouTube.SearchFilter.FILTER_ARTIST) { item ->
            (item as? ArtistItem)?.toArtistSummary()
        }

    override suspend fun accountSummary(): MusicAccountSummary? = withContext(Dispatchers.IO) {
        val activeSession = YouTubeSessionStore.apply(appContext)
        if (!activeSession.isLoggedIn) return@withContext null
        // account_menu supplies display metadata, not library authorization.
        // Its failure used to turn a saved login into a misleading login prompt
        // and prevented the authenticated library request from running at all.
        // Like Square, use the saved account here and validate with library().
        MusicAccountSummary(
            source = source,
            id = activeSession.pageId.ifBlank { activeSession.dataSyncId }.ifBlank { "youtube" },
            displayName = activeSession.accountName.ifBlank { "YouTube Music" },
        )
    }

    override suspend fun userPlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        libraryPage(page, pageSize, LIKED_PLAYLISTS_BROWSE_ID, includeLiked = true) {
            (it as? PlaylistItem)?.toPlaylistSummary()
        }

    override suspend fun writablePlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        libraryPage(page, pageSize, LIKED_PLAYLISTS_BROWSE_ID, writableOnly = true) {
            (it as? PlaylistItem)?.toPlaylistSummary()
        }

    override suspend fun setFavorite(track: MusicTrack, favorite: Boolean) {
        require(track.id.source == source)
        check(session.isLoggedIn) { "YouTube Music 需要登录后才能收藏" }
        YouTubeSessionStore.apply(appContext)
        YouTube.likeVideo(track.id.value, favorite).getOrThrow()
    }

    override suspend fun addTrackToPlaylist(track: MusicTrack, playlist: MusicPlaylistSummary) {
        require(track.id.source == source && playlist.id.source == source)
        check(session.isLoggedIn) { "YouTube Music 需要登录后才能修改歌单" }
        YouTubeSessionStore.apply(appContext)
        if (playlist.id.value == LIKED_SONGS_PLAYLIST_ID) {
            YouTube.likeVideo(track.id.value, true).getOrThrow()
            return
        }
        check(playlist.isEditable != false) { "这个 YouTube 歌单不可编辑" }
        YouTube.addToPlaylist(playlist.id.value, track.id.value).getOrThrow()
    }

    override suspend fun savedAlbums(page: Int, pageSize: Int): MusicPage<MusicAlbumSummary> =
        libraryPage(page, pageSize, SAVED_ALBUMS_BROWSE_ID) { (it as? AlbumItem)?.toAlbumSummary() }

    override suspend fun followedArtists(page: Int, pageSize: Int): MusicPage<MusicArtistSummary> =
        libraryPage(page, pageSize, FOLLOWED_ARTISTS_BROWSE_ID) { (it as? ArtistItem)?.toArtistSummary() }

    override suspend fun createPlaylist(name: String): MusicPlaylistSummary = withContext(Dispatchers.IO) {
        check(session.isLoggedIn) { "YouTube Music 需要登录后才能新建歌单" }
        YouTubeSessionStore.apply(appContext)
        val id = YouTube.createPlaylist(name.ifBlank { "New playlist" })
        MusicPlaylistSummary(MusicResourceId(source, id), name.ifBlank { "New playlist" }, isEditable = true)
    }

    override suspend fun renamePlaylist(playlist: MusicPlaylistSummary, name: String) {
        require(playlist.id.source == source)
        check(playlist.isEditable != false && playlist.id.value != LIKED_SONGS_PLAYLIST_ID) { "这个 YouTube 歌单不可编辑" }
        check(session.isLoggedIn) { "YouTube Music 需要登录后才能修改歌单" }
        YouTubeSessionStore.apply(appContext)
        YouTube.renamePlaylist(playlist.id.value, name).getOrThrow()
    }

    override suspend fun removeTrackFromPlaylist(track: MusicTrack, playlist: MusicPlaylistSummary) {
        require(track.id.source == source && playlist.id.source == source)
        if (playlist.id.value == LIKED_SONGS_PLAYLIST_ID) {
            setFavorite(track, false)
            return
        }
        check(playlist.isEditable != false) { "这个 YouTube 歌单不可编辑" }
        val membership = track.providerMetadata as? ProviderTrackMetadata.YouTube
        val setVideoId = membership?.setVideoId
            ?: error("这首歌没有 YouTube 歌单成员标识，无法从歌单移除")
        check(session.isLoggedIn) { "YouTube Music 需要登录后才能修改歌单" }
        YouTubeSessionStore.apply(appContext)
        YouTube.removeFromPlaylist(playlist.id.value, track.id.value, setVideoId).getOrThrow()
    }

    override suspend fun reorderPlaylistTrack(playlist: MusicPlaylistSummary, track: MusicTrack, newIndex: Int) {
        require(track.id.source == source && playlist.id.source == source)
        check(playlist.isEditable != false && playlist.id.value != LIKED_SONGS_PLAYLIST_ID) { "这个 YouTube 歌单不可编辑" }
        val membership = track.providerMetadata as? ProviderTrackMetadata.YouTube
        val setVideoId = membership?.setVideoId
            ?: error("这首歌没有 YouTube 歌单成员标识，无法调整顺序")
        check(session.isLoggedIn) { "YouTube Music 需要登录后才能修改歌单" }
        YouTubeSessionStore.apply(appContext)
        val songs = loadAllPlaylistTracks(playlist).tracks
        val without = songs.filterNot { (it.providerMetadata as? ProviderTrackMetadata.YouTube)?.setVideoId == setVideoId }
        val successor = without.getOrNull(newIndex.coerceIn(0, without.size))
        YouTube.moveSongPlaylist(playlist.id.value, setVideoId, (successor?.providerMetadata as? ProviderTrackMetadata.YouTube)?.setVideoId).getOrThrow()
    }

    override val canDeletePlaylists = true

    override suspend fun deletePlaylist(playlist: MusicPlaylistSummary) {
        require(playlist.id.source == source)
        if (playlist.id.value == LIKED_SONGS_PLAYLIST_ID) error("喜欢的歌曲不能删除")
        check(playlist.isEditable != false) { "这个 YouTube 歌单不可编辑" }
        check(session.isLoggedIn) { "YouTube Music 需要登录后才能删除歌单" }
        YouTubeSessionStore.apply(appContext)
        YouTube.deletePlaylist(playlist.id.value).getOrThrow()
    }

    override suspend fun rankingTracks(
        ranking: MusicRankingSummary,
        page: Int,
        pageSize: Int,
    ): MusicPage<MusicTrack> = withContext(Dispatchers.IO) {
        require(ranking.id.source == source)
        val section = chartSections().firstOrNull { it.title == ranking.id.value }
            ?: error("这个榜单已经不在 YouTube Music 的排行里")
        val tracks = section.items.filterIsInstance<SongItem>().map { it.toMusicTrack() }
        val size = pageSize.coerceAtLeast(1)
        val offset = (page.coerceAtLeast(1) - 1) * size
        MusicPage(tracks.drop(offset).take(size), page, size, tracks.size.toLong(), false)
    }

    override suspend fun playlistDetail(
        playlist: MusicPlaylistSummary,
        page: Int,
        pageSize: Int,
    ): MusicPlaylistDetail = withContext(Dispatchers.IO) {
        require(playlist.id.source == source)
        YouTubeSessionStore.apply(appContext)
        val key = requestKey("playlist:${playlist.id.value}")
        val result = playlistPager.page(key, page, pageSize, first = {
            val initial = YouTube.playlist(playlist.id.value).getOrThrow()
            if (playlistSummaries.size >= 24) playlistSummaries.clear()
            playlistSummaries[key] = initial.playlist.toPlaylistSummary()
            YouTubeCursorPager.Batch(initial.songs, initial.songsContinuation ?: initial.continuation)
        }, next = { cursor ->
            val more = YouTube.playlistContinuation(cursor).getOrThrow()
            YouTubeCursorPager.Batch(more.songs, more.continuation)
        })
        // An unknown total must still tell the shared all-tracks loader to ask
        // for another page; never report the first server batch as the total.
        val total = result.total ?: (page.toLong() * pageSize + 1)
        MusicPlaylistDetail(playlistSummaries[key] ?: playlist, result.items.map { it.toMusicTrack() }, total)
    }

    override suspend fun albumDetail(
        album: MusicAlbumSummary,
        page: Int,
        pageSize: Int,
    ): MusicAlbumDetail = withContext(Dispatchers.IO) {
        val result = YouTube.album(album.id.value).getOrThrow()
        val tracks = result.songs.map { it.toMusicTrack() }
        MusicAlbumDetail(result.album.toAlbumSummary(), tracks, tracks.size.toLong())
    }

    override suspend fun artistDetail(
        artist: MusicArtistSummary,
        page: Int,
        pageSize: Int,
    ): MusicArtistDetail = withContext(Dispatchers.IO) {
        val result = YouTube.artist(artist.id.value).getOrThrow()
        val tracks = result.sections.flatMap { it.items }.filterIsInstance<SongItem>().map { it.toMusicTrack() }
        MusicArtistDetail(result.artist.toArtistSummary(), tracks, tracks.size.toLong())
    }

    /**
     * YouTube Music's own home, folded into the three shelves the home screen draws.
     *
     * Playlists and albums become the collection row, songs become the track row,
     * and the charts page becomes the ranking row.
     */
    override suspend fun homeFeed(
        playlistLimit: Int,
        newSongLimit: Int,
        rankingLimit: Int,
    ): MusicHomeFeed = withContext(Dispatchers.IO) {
        YouTubeSessionStore.apply(appContext)
        var home = YouTube.home().getOrThrow()
        val playlists = mutableListOf<MusicPlaylistSummary>()
        val songs = mutableListOf<MusicTrack>()
        home.sections.forEach { section -> section.collect(playlists, songs) }
        val visited = mutableSetOf<String>()
        while ((playlists.distinctBy { it.id }.size < playlistLimit || songs.distinctBy { it.id }.size < newSongLimit) && visited.size < 8) {
            val cursor = home.continuation ?: break
            if (!visited.add(cursor)) break
            val next = YouTube.home(continuation = cursor)
            next.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            home = next.getOrNull() ?: break
            home.sections.forEach { section -> section.collect(playlists, songs) }
        }
        val rankings = runCatching { chartSections() }.getOrDefault(emptyList())
            .filter { section -> section.items.any { it is SongItem } }
            .map { section ->
                val preview = section.items.filterIsInstance<SongItem>().take(3).map { it.toMusicTrack() }
                MusicRankingSummary(
                    id = MusicResourceId(source, section.title),
                    title = section.title,
                    artworkUrl = preview.firstOrNull()?.artworkUrl,
                    previewTracks = preview,
                )
            }
        MusicHomeFeed(
            recommendedPlaylists = playlists.distinctBy { it.id }.take(playlistLimit.coerceAtLeast(0)),
            newSongs = songs.distinctBy { it.id }.take(newSongLimit.coerceAtLeast(0)),
            rankings = rankings.take(rankingLimit.coerceAtLeast(0)),
        )
    }

    private suspend fun chartSections(): List<ChartsPage.ChartSection> {
        YouTubeSessionStore.apply(appContext)
        return YouTube.getChartsPage().getOrThrow().sections
    }

    override suspend fun lyrics(track: MusicTrack): LyricsDocument {
        require(track.id.source == source)
        return lyricsClient.lyrics(
            title = track.title,
            artist = track.artists.firstOrNull()?.name.orEmpty(),
            durationMs = track.durationMs ?: 0L,
        )
    }

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution {
        require(track.id.source == source) { "YouTubeMusicProvider cannot handle ${track.id.source}" }
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureNewPipe()
                val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=${track.id.value}")
                val streams = info.audioStreams.filter { it.content.isNotBlank() }
                // YouTube only serves lossy audio, so never report the requested
                // Lossless/Hi-Res tier as if it had been delivered. Pick the stream
                // closest to the requested data rate and report its real tier.
                val targetBitrate = when (quality) {
                    AudioQualityTier.Standard -> 128
                    AudioQualityTier.High -> 256
                    else -> Int.MAX_VALUE
                }
                val stream = streams.minByOrNull { kotlin.math.abs(it.averageBitrate - targetBitrate) }
                    ?: error("YouTube Music 无可用音频流")
                val actualQuality = when {
                    stream.averageBitrate >= 256 -> AudioQualityTier.High
                    else -> AudioQualityTier.Standard
                }
                PlaybackResolution.Playable(
                    url = stream.content,
                    requestedQuality = quality,
                    actualQuality = actualQuality,
                    bitrate = stream.averageBitrate.takeIf { it > 0 }?.times(1_000),
                    format = stream.format?.suffix,
                    expiresAtEpochMs = System.currentTimeMillis() + STREAM_URL_TTL_MS,
                )
            }.getOrElse { PlaybackResolution.Unavailable(it.message ?: "YouTube Music 播放失败") }
        }
    }

    override suspend fun resolveDownload(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution =
        resolvePlayback(track, quality)

    private suspend fun <T> libraryPage(
        page: Int,
        pageSize: Int,
        browseId: String,
        includeLiked: Boolean = false,
        writableOnly: Boolean = false,
        map: (YTItem) -> T?,
    ): MusicPage<T> = withContext(Dispatchers.IO) {
        if (!session.isLoggedIn) return@withContext MusicPage(emptyList(), page, pageSize, 0)
        YouTubeSessionStore.apply(appContext)
        fun select(items: List<YTItem>) = (if (writableOnly) {
            items.filter { it is PlaylistItem && it.isEditable && it.id != LIKED_SONGS_PLAYLIST_ID }
        } else items).filter { map(it) != null }
        val result = catalogPager.page(requestKey("library:$browseId:$writableOnly:$includeLiked"), page, pageSize, first = {
            val initial = YouTube.library(browseId).getOrThrow()
            val liked = if (includeLiked && initial.items.none { it.id == LIKED_SONGS_PLAYLIST_ID }) {
                listOf(PlaylistItem(id = LIKED_SONGS_PLAYLIST_ID, title = "Liked songs", author = null, songCountText = null, thumbnail = null, playEndpoint = null, shuffleEndpoint = null, radioEndpoint = null))
            } else emptyList()
            YouTubeCursorPager.Batch(liked + select(initial.items), initial.continuation)
        }, next = { cursor ->
            val more = YouTube.libraryContinuation(cursor).getOrThrow()
            YouTubeCursorPager.Batch(select(more.items), more.continuation)
        })
        MusicPage(result.items.mapNotNull(map), page, pageSize, result.total, result.hasMore)
    }

    private suspend fun <T> search(
        query: String,
        page: Int,
        pageSize: Int,
        filter: YouTube.SearchFilter,
        map: (YTItem) -> T?,
    ): MusicPage<T> = withContext(Dispatchers.IO) {
        YouTubeSessionStore.apply(appContext)
        val result = catalogPager.page(requestKey("search:${query.trim()}:$filter"), page, pageSize, first = {
            val initial = YouTube.search(query.trim(), filter).getOrThrow()
            YouTubeCursorPager.Batch(initial.items, initial.continuation)
        }, next = { cursor ->
            val more = YouTube.searchContinuation(cursor).getOrThrow()
            YouTubeCursorPager.Batch(more.items, more.continuation)
        })
        MusicPage(result.items.mapNotNull(map), page, pageSize, result.total, result.hasMore)
    }

    private fun ensureNewPipe() {
        if (newPipeInitialised.get()) return
        synchronized(this) {
            if (newPipeInitialised.get()) return
            NewPipe.init(NewPipeDownloader(httpClient), Localization.DEFAULT)
            newPipeInitialised.set(true)
        }
    }

    private fun SongItem.toMusicTrack() = MusicTrack(
        id = MusicResourceId(source, id),
        title = title,
        artists = artists.map { MusicArtistRef(it.id?.let { id -> MusicResourceId(source, id) }, it.name) },
        album = album?.let { MusicAlbumRef(MusicResourceId(source, it.id), it.name) },
        artworkUrl = thumbnail,
        durationMs = duration?.takeIf { it > 0 }?.times(1000L),
        availability = TrackAvailability.Playable,
        providerMetadata = ProviderTrackMetadata.YouTube(videoId = id, setVideoId = setVideoId),
        bestQuality = AudioQualityTier.High,
        bestBitrate = 256_000,
    )

    private fun PlaylistItem.toPlaylistSummary() = MusicPlaylistSummary(
        id = MusicResourceId(source, id),
        title = title,
        artworkUrl = thumbnail,
        creatorName = author?.name,
        trackCount = songCountText?.filter(Char::isDigit)?.toIntOrNull(),
        isEditable = isEditable && id != LIKED_SONGS_PLAYLIST_ID,
    )

    private fun AlbumItem.toAlbumSummary() = MusicAlbumSummary(
        id = MusicResourceId(source, browseId),
        title = title,
        artworkUrl = thumbnail,
        artists = artists.orEmpty().map { MusicArtistRef(it.id?.let { id -> MusicResourceId(source, id) }, it.name) },
        releaseDate = year?.toString(),
    )

    private fun ArtistItem.toArtistSummary() = MusicArtistSummary(
        id = MusicResourceId(source, id),
        name = title,
        artworkUrl = thumbnail,
    )

    private fun HomePage.Section.collect(
        playlists: MutableList<MusicPlaylistSummary>,
        songs: MutableList<MusicTrack>,
    ) {
        items.forEach { item ->
            when (item) {
                is SongItem -> songs += item.toMusicTrack()
                is PlaylistItem -> playlists += item.toPlaylistSummary()
                is AlbumItem -> playlists += MusicPlaylistSummary(
                    id = MusicResourceId(source, item.playlistId),
                    title = item.title,
                    artworkUrl = item.thumbnail,
                    creatorName = item.artists?.joinToString(", ") { it.name },
                )
                else -> Unit
            }
        }
    }

    private fun Artist.toArtistSummary() = MusicArtistSummary(
        id = MusicResourceId(source, id ?: name),
        name = name,
    )

    companion object {
        private const val LIKED_PLAYLISTS_BROWSE_ID = "FEmusic_liked_playlists"
        private const val SAVED_ALBUMS_BROWSE_ID = "FEmusic_liked_albums"
        private const val FOLLOWED_ARTISTS_BROWSE_ID = "FEmusic_library_corpus_track_artists"
        /** YouTube Music's own liked-songs playlist. `YouTube.playlist` reads it by this id. */
        private const val LIKED_SONGS_PLAYLIST_ID = "LM"
        private const val STREAM_URL_TTL_MS = 90 * 60 * 1_000L
    }
}

private class NewPipeDownloader(private val http: okhttp3.OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
            .url(request.url())
            .addHeader("User-Agent", USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        http.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) throw ReCaptchaException("YouTube rate limited", request.url())
            return Response(response.code, response.message, response.headers.toMultimap(), response.body?.string(), response.request.url.toString())
        }
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0.0.0 Safari/537.36"
    }
}
