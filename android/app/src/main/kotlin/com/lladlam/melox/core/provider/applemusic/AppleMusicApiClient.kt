package com.lladlam.melox.core.provider.applemusic

import android.text.Html
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicAlbumDetail
import com.lladlam.melox.core.music.model.MusicAlbumSummary
import com.lladlam.melox.core.music.model.MusicArtistDetail
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicArtistSummary
import com.lladlam.melox.core.music.model.MusicHomeFeed
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicPlaylistDetail
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.music.model.TrackAvailability
import com.lladlam.melox.core.music.model.MusicAccountSummary
import com.lladlam.melox.core.music.provider.CatalogSearchCapability
import com.lladlam.melox.core.music.provider.AlbumCapability
import com.lladlam.melox.core.music.provider.ArtistCapability
import com.lladlam.melox.core.music.provider.HomeFeedCapability
import com.lladlam.melox.core.music.provider.LyricsCapability
import com.lladlam.melox.core.music.provider.MusicCapability
import com.lladlam.melox.core.music.provider.MusicProvider
import com.lladlam.melox.core.music.provider.PlaybackCapability
import com.lladlam.melox.core.music.provider.PlaylistCapability
import com.lladlam.melox.core.music.provider.SearchCapability
import com.lladlam.melox.core.music.provider.UserLibraryCapability
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Official Apple Music API adapter.
 *
 * Catalog/search/artwork/lyrics use the public REST API. Full-song playback is
 * deliberately delegated to Apple's official MusicKit playback AAR; the REST
 * API's preview URL is metadata only and is never used as normal playback.
 */
class AppleMusicApiClient(
    private val sessionProvider: () -> AppleMusicSession,
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) : MusicProvider,
    SearchCapability,
    CatalogSearchCapability,
    AlbumCapability,
    ArtistCapability,
    LyricsCapability,
    PlaybackCapability,
    HomeFeedCapability,
    UserLibraryCapability,
    PlaylistCapability {
    override val source: MusicSource = MusicSource.AppleMusic
    override val displayName: String = source.displayName
    override val capabilities: Set<MusicCapability> = setOf(
        MusicCapability.Search,
        MusicCapability.Playback,
        MusicCapability.Lyrics,
        MusicCapability.Library,
        MusicCapability.Playlists,
        MusicCapability.Albums,
        MusicCapability.Artists,
        MusicCapability.HomeRecommendations,
    )

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> =
        withContext(Dispatchers.IO) {
            val result = search(query, page, pageSize, "songs")
            MusicPage(result.items.mapNotNull(::parseTrack), page, pageSize, result.total)
        }

    override suspend fun searchPlaylists(query: String, page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        withContext(Dispatchers.IO) {
            val result = search(query, page, pageSize, "playlists")
            MusicPage(result.items.mapNotNull(::parsePlaylist), page, pageSize, result.total)
        }

    override suspend fun searchAlbums(query: String, page: Int, pageSize: Int): MusicPage<MusicAlbumSummary> =
        withContext(Dispatchers.IO) {
            val result = search(query, page, pageSize, "albums")
            MusicPage(result.items.mapNotNull(::parseAlbum), page, pageSize, result.total)
        }

    override suspend fun searchArtists(query: String, page: Int, pageSize: Int): MusicPage<MusicArtistSummary> =
        withContext(Dispatchers.IO) {
            val result = search(query, page, pageSize, "artists")
            MusicPage(result.items.mapNotNull(::parseArtist), page, pageSize, result.total)
        }

    override suspend fun lyrics(track: MusicTrack): LyricsDocument = withContext(Dispatchers.IO) {
        require(track.id.source == MusicSource.AppleMusic) { "只能读取 Apple Music 曲目的歌词" }
        val root = getJson(
            path = "v1/catalog/${sessionProvider().storefront}/songs/${track.id.value}",
            query = mapOf("include" to "lyrics"),
        )
        val lyrics = root.optJSONArray("data")?.optJSONObject(0)?.optJSONObject("relationships")
            ?.optJSONObject("lyrics")
            ?.optJSONArray("data")
            ?.optJSONObject(0)
            ?.optJSONObject("attributes")
            ?: return@withContext LyricsDocument(emptyList())
        parseLyrics(
            ttml = lyrics.optString("ttml"),
            text = lyrics.optString("text"),
        )
    }

    override suspend fun resolvePlayback(
        track: MusicTrack,
        quality: AudioQualityTier,
    ): PlaybackResolution {
        return PlaybackResolution.Unavailable(
            "Apple Music 全曲播放由 Apple 官方 MusicKit DRM 播放器处理；请先完成 Apple Music 授权",
        )
    }

    override suspend fun homeFeed(
        playlistLimit: Int,
        newSongLimit: Int,
        rankingLimit: Int,
    ): MusicHomeFeed = withContext(Dispatchers.IO) {
        val root = getJson(
            path = "v1/catalog/${sessionProvider().storefront}/charts",
            query = mapOf(
                "types" to "playlists,songs",
                "limit" to maxOf(playlistLimit, newSongLimit, rankingLimit).coerceIn(1, 25).toString(),
            ),
        )
        val charts = root.optJSONObject("results")
        val playlists = charts?.optJSONArray("playlists")?.objects().orEmpty()
            .flatMap { it.optJSONArray("data")?.objects().orEmpty() }
            .mapNotNull(::parsePlaylist)
            .take(playlistLimit)
        val songs = charts?.optJSONArray("songs")?.objects().orEmpty()
            .flatMap { it.optJSONArray("data")?.objects().orEmpty() }
            .mapNotNull(::parseTrack)
            .take(newSongLimit)
        MusicHomeFeed(recommendedPlaylists = playlists, newSongs = songs)
    }

    override suspend fun accountSummary(): MusicAccountSummary? {
        val session = sessionProvider()
        return if (session.hasUserAuthorization) {
            MusicAccountSummary(
                source = MusicSource.AppleMusic,
                id = "apple-music-user",
                displayName = "Apple Music",
                subtitle = "Music User Token 已配置",
            )
        } else null
    }

    override suspend fun userPlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> = withContext(Dispatchers.IO) {
        requireUserAuthorization()
        val root = getJson(
            path = "v1/me/library/playlists",
            query = mapOf(
                "limit" to pageSize.coerceIn(1, 100).toString(),
                "offset" to ((page - 1).coerceAtLeast(0) * pageSize).toString(),
            ),
            userRequired = true,
        )
        val data = root.optJSONArray("data")?.objects().orEmpty().mapNotNull(::parsePlaylist)
        MusicPage(data, page, pageSize, root.optJSONObject("meta")?.optLong("total"))
    }

    override suspend fun playlistDetail(
        playlist: MusicPlaylistSummary,
        page: Int,
        pageSize: Int,
    ): MusicPlaylistDetail = withContext(Dispatchers.IO) {
        require(playlist.id.source == MusicSource.AppleMusic) { "只能读取 Apple Music 歌单" }
        // userPlaylists() returns library IDs; those playlists are not catalog resources.
        val libraryPlaylist = playlist.creatorName.isNullOrBlank()
        val root = getJson(
            path = if (libraryPlaylist) {
                "v1/me/library/playlists/${playlist.id.value}"
            } else {
                "v1/catalog/${sessionProvider().storefront}/playlists/${playlist.id.value}"
            },
            query = mapOf("include" to "tracks"),
            userRequired = libraryPlaylist,
        )
        val summary = root.optJSONArray("data")?.optJSONObject(0)?.let(::parsePlaylist) ?: playlist
        val tracks = root.relationshipData("tracks").mapNotNull(::parseTrack)
        MusicPlaylistDetail(summary, tracks, tracks.size.toLong())
    }

    override suspend fun albumDetail(
        album: MusicAlbumSummary,
        page: Int,
        pageSize: Int,
    ): MusicAlbumDetail = withContext(Dispatchers.IO) {
        require(album.id.source == MusicSource.AppleMusic) { "只能读取 Apple Music 专辑" }
        val root = getJson(
            path = "v1/catalog/${sessionProvider().storefront}/albums/${album.id.value}",
            query = mapOf("include" to "tracks"),
        )
        val summary = root.optJSONArray("data")?.optJSONObject(0)?.let(::parseAlbum) ?: album
        val tracks = root.relationshipData("tracks").mapNotNull(::parseTrack)
        MusicAlbumDetail(summary, tracks, tracks.size.toLong())
    }

    override suspend fun artistDetail(
        artist: MusicArtistSummary,
        page: Int,
        pageSize: Int,
    ): MusicArtistDetail = withContext(Dispatchers.IO) {
        require(artist.id.source == MusicSource.AppleMusic) { "只能读取 Apple Music 歌手" }
        val root = getJson(
            path = "v1/catalog/${sessionProvider().storefront}/artists/${artist.id.value}",
            query = mapOf("include" to "albums"),
        )
        val summary = root.optJSONArray("data")?.optJSONObject(0)?.let(::parseArtist) ?: artist
        MusicArtistDetail(summary, emptyList(), null)
    }

    private fun search(query: String, page: Int, pageSize: Int, type: String): SearchResponse {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 25)
        val root = getJson(
            path = "v1/catalog/${sessionProvider().storefront}/search",
            query = mapOf(
                "term" to query.trim(),
                "types" to type,
                "limit" to safeSize.toString(),
                "offset" to ((safePage - 1) * safeSize).toString(),
            ),
        )
        val result = root.optJSONObject("results")?.optJSONObject(type)
        return SearchResponse(
            items = result?.optJSONArray("data")?.objects().orEmpty(),
            total = result?.optJSONObject("meta")?.optLong("total"),
        )
    }

    private fun getJson(
        path: String,
        query: Map<String, String> = emptyMap(),
        userRequired: Boolean = false,
    ): JSONObject {
        val session = sessionProvider()
        if (!session.isConfigured) throw IOException("Apple Music 未配置 Developer Token")
        if (userRequired && !session.hasUserAuthorization) {
            throw IOException("Apple Music 未配置 Music User Token")
        }
        val urlBuilder = "https://api.music.apple.com/".toHttpUrl().newBuilder()
            .addPathSegments(path.trimStart('/'))
        query.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            .header("Authorization", "Bearer ${session.developerToken}")
            .header("Accept", "application/json")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7")
        if (session.hasUserAuthorization) {
            requestBuilder.header("Media-User-Token", session.musicUserToken)
        }
        httpClient.newCall(requestBuilder.get().build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Apple Music API ${response.code}: ${body.take(240)}")
            }
            return JSONObject(body)
        }
    }

    private fun requireUserAuthorization() {
        if (!sessionProvider().hasUserAuthorization) throw IOException("Apple Music 未授权个人音乐库")
    }

    private fun parseTrack(value: JSONObject): MusicTrack? {
        val id = value.optString("id").takeIf(String::isNotBlank) ?: return null
        val attributes = value.optJSONObject("attributes") ?: return null
        val title = attributes.optString("name").takeIf(String::isNotBlank) ?: return null
        val preview = attributes.optJSONArray("previews")?.optJSONObject(0)?.optString("url")
            ?.takeIf(String::isNotBlank)
        val artwork = artworkUrl(attributes.optJSONObject("artwork"))
        val artistName = attributes.optString("artistName").ifBlank { "未知歌手" }
        val albumName = attributes.optString("albumName").takeIf(String::isNotBlank)
        val storefront = sessionProvider().storefront
        val audioTraits = attributes.optJSONArray("audioTraits")?.let { traits ->
            (0 until traits.length()).mapNotNull { index -> traits.optString(index).takeIf(String::isNotBlank) }
        }.orEmpty().map(String::lowercase)
        val bestQuality = when {
            audioTraits.any { it.contains("hi-res") || it.contains("hires") } -> AudioQualityTier.HiResolution
            audioTraits.any { it.contains("lossless") } -> AudioQualityTier.Lossless
            audioTraits.any { it.contains("atmos") || it.contains("spatial") } -> AudioQualityTier.Immersive
            else -> null
        }
        return MusicTrack(
            id = MusicResourceId(MusicSource.AppleMusic, id),
            title = title,
            artists = listOf(MusicArtistRef(name = artistName)),
            album = albumName?.let { MusicAlbumRef(name = it, artworkUrl = artwork) },
            artworkUrl = artwork,
            durationMs = attributes.optLong("durationInMillis").takeIf { it > 0L },
            availability = if (preview != null) TrackAvailability.PreviewOnly else TrackAvailability.SubscriptionRequired,
            providerMetadata = ProviderTrackMetadata.AppleMusic(id, storefront, preview),
            bestQuality = bestQuality,
            bestBitrate = when (bestQuality) {
                AudioQualityTier.HiResolution -> 1_411_200
                AudioQualityTier.Lossless -> 1_000_000
                AudioQualityTier.Immersive -> 1_000_000
                else -> null
            },
        )
    }

    private fun parsePlaylist(value: JSONObject): MusicPlaylistSummary? {
        val id = value.optString("id").takeIf(String::isNotBlank) ?: return null
        val attributes = value.optJSONObject("attributes") ?: return null
        val title = attributes.optString("name").takeIf(String::isNotBlank) ?: return null
        return MusicPlaylistSummary(
            id = MusicResourceId(MusicSource.AppleMusic, id),
            title = title,
            artworkUrl = artworkUrl(attributes.optJSONObject("artwork")),
            creatorName = attributes.optString("curatorName").takeIf(String::isNotBlank),
            description = attributes.optJSONObject("description")?.optString("standard")
                ?.takeIf(String::isNotBlank),
            trackCount = attributes.optInt("trackCount").takeIf { it > 0 },
        )
    }

    private fun parseAlbum(value: JSONObject): MusicAlbumSummary? {
        val id = value.optString("id").takeIf(String::isNotBlank) ?: return null
        val attributes = value.optJSONObject("attributes") ?: return null
        val title = attributes.optString("name").takeIf(String::isNotBlank) ?: return null
        return MusicAlbumSummary(
            id = MusicResourceId(MusicSource.AppleMusic, id),
            title = title,
            artworkUrl = artworkUrl(attributes.optJSONObject("artwork")),
            artists = listOf(MusicArtistRef(name = attributes.optString("artistName").ifBlank { "未知歌手" })),
            releaseDate = attributes.optString("releaseDate").takeIf(String::isNotBlank),
            trackCount = attributes.optInt("trackCount").takeIf { it > 0 }?.toLong(),
        )
    }

    private fun parseArtist(value: JSONObject): MusicArtistSummary? {
        val id = value.optString("id").takeIf(String::isNotBlank) ?: return null
        val attributes = value.optJSONObject("attributes") ?: return null
        val name = attributes.optString("name").takeIf(String::isNotBlank) ?: return null
        return MusicArtistSummary(
            id = MusicResourceId(MusicSource.AppleMusic, id),
            name = name,
            artworkUrl = artworkUrl(attributes.optJSONObject("artwork")),
        )
    }

    private fun artworkUrl(artwork: JSONObject?): String? {
        val template = artwork?.optString("url")?.takeIf(String::isNotBlank) ?: return null
        return template.replace("{w}", "800").replace("{h}", "800").replace("{f}", "jpg")
    }

    private fun parseLyrics(ttml: String, text: String): LyricsDocument {
        val lines = TtmlParagraph.findAll(ttml).mapNotNull { match ->
            val begin = attribute(match.groupValues[1], "begin") ?: return@mapNotNull null
            val content = Html.fromHtml(match.groupValues[2], Html.FROM_HTML_MODE_LEGACY)
                .toString().replace(Whitespace, " ").trim()
            content.takeIf(String::isNotBlank)?.let { LyricLine(begin, text = it) }
        }.toList()
        if (lines.isNotEmpty()) return LyricsDocument(lines)
        val plain = text.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        return LyricsDocument(plain.mapIndexed { index, line -> LyricLine(index * 3_000L, text = line) })
    }

    private fun attribute(attributes: String, name: String): Long? {
        val raw = Regex("(?:^|\\s)$name\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE)
            .find(attributes)?.groupValues?.getOrNull(1) ?: return null
        val parts = raw.split(":")
        val seconds = parts.lastOrNull()?.replace(',', '.')?.toDoubleOrNull() ?: return null
        val minutes = parts.dropLast(1).lastOrNull()?.toLongOrNull() ?: 0L
        val hours = parts.dropLast(2).lastOrNull()?.toLongOrNull() ?: 0L
        return ((hours * 3_600L + minutes * 60L) * 1_000L + (seconds * 1_000.0).toLong())
    }

    private data class SearchResponse(val items: List<JSONObject>, val total: Long?)

    private fun JSONObject.relationshipData(name: String): List<JSONObject> =
        optJSONObject("relationships")?.optJSONObject(name)?.optJSONArray("data")?.objects().orEmpty()

    private fun JSONArray.objects(): List<JSONObject> = buildList {
        for (index in 0 until length()) optJSONObject(index)?.let(::add)
    }

    private companion object {
        val TtmlParagraph = Regex("<p\\b([^>]*)>(.*?)</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val Whitespace = Regex("\\s+")
    }
}
