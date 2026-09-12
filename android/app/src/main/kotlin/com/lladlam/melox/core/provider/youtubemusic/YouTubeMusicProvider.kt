package com.lladlam.melox.core.provider.youtubemusic

import android.content.Context
import android.net.Uri
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicAccountSummary
import com.lladlam.melox.core.music.model.MusicAlbumDetail
import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicAlbumSummary
import com.lladlam.melox.core.music.model.MusicArtistDetail
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicArtistSummary
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicPlaylistDetail
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.model.TrackAvailability
import com.lladlam.melox.core.music.provider.AlbumCapability
import com.lladlam.melox.core.music.provider.ArtistCapability
import com.lladlam.melox.core.music.provider.CatalogSearchCapability
import com.lladlam.melox.core.music.provider.DownloadCapability
import com.lladlam.melox.core.music.provider.MusicCapability
import com.lladlam.melox.core.music.provider.MusicProvider
import com.lladlam.melox.core.music.provider.PlaybackCapability
import com.lladlam.melox.core.music.provider.PlaylistCapability
import com.lladlam.melox.core.music.provider.PlaylistWriteCapability
import com.lladlam.melox.core.music.provider.SearchCapability
import com.lladlam.melox.core.music.provider.UserLibraryCapability
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.Dispatchers
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
    DownloadCapability, UserLibraryCapability, PlaylistCapability, PlaylistWriteCapability,
    AlbumCapability, ArtistCapability {
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
        MusicCapability.Library,
        MusicCapability.Playlists,
        MusicCapability.Albums,
        MusicCapability.Artists,
    )

    private val newPipeInitialised = AtomicBoolean(false)

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
        if (!session.isLoggedIn) return@withContext null
        YouTubeSessionStore.apply(appContext)
        YouTube.accountInfo().getOrNull()?.let { info ->
            MusicAccountSummary(source, info.email.orEmpty(), info.name, info.thumbnailUrl)
        }
    }

    override suspend fun userPlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        withContext(Dispatchers.IO) {
            if (page > 1 || !session.isLoggedIn) return@withContext MusicPage(emptyList(), page, pageSize, 0)
            YouTubeSessionStore.apply(appContext)
            val items = YouTube.library(LIKED_PLAYLISTS_BROWSE_ID).getOrThrow().items
                .filterIsInstance<PlaylistItem>().map { it.toPlaylistSummary() }
            MusicPage(items, page, pageSize, items.size.toLong(), false)
        }

    override suspend fun writablePlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        userPlaylists(page, pageSize)

    override suspend fun addTrackToPlaylist(track: MusicTrack, playlist: MusicPlaylistSummary) {
        require(track.id.source == source && playlist.id.source == source)
        check(session.isLoggedIn) { "YouTube Music 需要登录后才能修改歌单" }
        YouTubeSessionStore.apply(appContext)
        YouTube.addToPlaylist(playlist.id.value, track.id.value).getOrThrow()
    }

    override suspend fun playlistDetail(
        playlist: MusicPlaylistSummary,
        page: Int,
        pageSize: Int,
    ): MusicPlaylistDetail = withContext(Dispatchers.IO) {
        val result = YouTube.playlist(playlist.id.value).getOrThrow()
        val tracks = result.songs.map { it.toMusicTrack() }
        MusicPlaylistDetail(playlist.copy(
            title = result.playlist.title,
            artworkUrl = result.playlist.thumbnail,
            creatorName = result.playlist.author?.name,
        ), tracks, tracks.size.toLong())
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

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution {
        require(track.id.source == source) { "YouTubeMusicProvider cannot handle ${track.id.source}" }
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureNewPipe()
                val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=${track.id.value}")
                val stream = info.audioStreams
                    .filter { it.content.isNotBlank() }
                    .maxByOrNull { it.averageBitrate }
                    ?: error("YouTube Music 无可用音频流")
                PlaybackResolution.Playable(
                    url = stream.content,
                    requestedQuality = quality,
                    actualQuality = quality,
                    bitrate = stream.averageBitrate.takeIf { it > 0 },
                    format = "m4a",
                    expiresAtEpochMs = System.currentTimeMillis() + STREAM_URL_TTL_MS,
                )
            }.getOrElse { PlaybackResolution.Unavailable(it.message ?: "YouTube Music 播放失败") }
        }
    }

    override suspend fun resolveDownload(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution =
        resolvePlayback(track, quality)

    private suspend fun <T> search(
        query: String,
        page: Int,
        pageSize: Int,
        filter: YouTube.SearchFilter,
        map: (YTItem) -> T?,
    ): MusicPage<T> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext MusicPage(emptyList(), page, pageSize, 0, false)
        YouTubeSessionStore.apply(appContext)
        val result = YouTube.search(query.trim(), filter).getOrThrow()
        val items = result.items.mapNotNull(map).take(pageSize.coerceAtLeast(1))
        MusicPage(items, page, pageSize, result.items.size.toLong(), result.continuation != null)
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
    )

    private fun PlaylistItem.toPlaylistSummary() = MusicPlaylistSummary(
        id = MusicResourceId(source, id),
        title = title,
        artworkUrl = thumbnail,
        creatorName = author?.name,
        trackCount = songCountText?.filter(Char::isDigit)?.toIntOrNull(),
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

    private fun Artist.toArtistSummary() = MusicArtistSummary(
        id = MusicResourceId(source, id ?: name),
        name = name,
    )

    companion object {
        private const val LIKED_PLAYLISTS_BROWSE_ID = "FEmusic_liked_playlists"
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
