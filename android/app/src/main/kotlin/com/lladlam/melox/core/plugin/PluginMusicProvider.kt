package com.lladlam.melox.core.plugin

import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricQuality
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.*
import com.lladlam.melox.core.music.provider.*
import org.json.JSONArray
import org.json.JSONObject

/** Maps only versioned MeloX fields. Platform-specific conversion belongs in the script. */
class PluginMusicProvider(val manifest: PluginManifest, private val runtime: PluginInvoker) :
    MusicProvider, SearchCapability, PlaybackCapability, LyricsCapability, CatalogSearchCapability,
    PlaylistCapability, AlbumCapability, ArtistCapability, RankingCatalogCapability, HomeFeedCapability,
    UserLibraryCapability, LibraryCollectionCapability, FavoriteStateCapability, AuthenticationCapability, DownloadCapability {
    override val sourceId = manifest.sourceId
    override val displayName = manifest.name
    override val capabilities = manifest.capabilities.mapTo(mutableSetOf()) {
        when (it) {
            PluginCapability.Search -> MusicCapability.Search
            PluginCapability.Playback -> MusicCapability.Playback
            PluginCapability.Lyrics -> MusicCapability.Lyrics
            PluginCapability.Playlists -> MusicCapability.Playlists
            PluginCapability.Albums -> MusicCapability.Albums
            PluginCapability.Artists -> MusicCapability.Artists
            PluginCapability.Rankings -> MusicCapability.Rankings
            PluginCapability.HomeFeed -> MusicCapability.HomeRecommendations
            PluginCapability.UserLibrary -> MusicCapability.Library
            PluginCapability.Favorites -> MusicCapability.Favorites
            PluginCapability.Authentication -> MusicCapability.Authentication
            PluginCapability.Download -> MusicCapability.Download
        }
    }
    private val codec = PluginCatalogCodec(manifest)
    internal fun mediaBroker(): PluginHttpBroker = (runtime as? PluginRuntime)?.mediaBroker() ?: PluginHttpBroker(manifest)

    override suspend fun authenticationStatus(): MusicAuthenticationState {
        val result = JSONObject(call(PluginCapability.Authentication, "authenticationStatus"))
        return when (result.getString("type")) {
            "notRequired" -> MusicAuthenticationState.NotRequired
            "signedOut" -> MusicAuthenticationState.SignedOut
            "signedIn" -> MusicAuthenticationState.SignedIn(codec.account(result.getJSONObject("account")))
            "expired" -> MusicAuthenticationState.Expired(codec.optionalText(result, "accountId", 4096))
            else -> error("invalid authentication state")
        }
    }

    private suspend fun call(capability: PluginCapability, method: String, vararg args: Any): String {
        require(capability in manifest.capabilities && method in capability.methods) { "UNSUPPORTED_OPERATION" }
        val result = runtime.invoke(method, JSONArray(args.toList()))
        PluginWireContract.check(method, result)
        require(result.toByteArray(Charsets.UTF_8).size <= PluginIsolatedService.MAX_BYTES) { "plugin result too large" }
        return result
    }
    private suspend fun catalogPage(capability: PluginCapability, method: String, query: String?, page: Int, size: Int): JSONObject {
        require(page > 0 && size in 1..100 && (query == null || query.length <= 1024))
        return JSONObject(if (query == null) call(capability, method, page, size) else call(capability, method, query, page, size))
    }

    suspend fun playlists(page: Int = 1, pageSize: Int = 30) = codec.page(
        catalogPage(PluginCapability.Playlists, "playlists", null, page, pageSize), page, pageSize, codec::playlist)
    override suspend fun searchPlaylists(query: String, page: Int, pageSize: Int) = codec.page(
        catalogPage(PluginCapability.Playlists, "searchPlaylists", query, page, pageSize), page, pageSize, codec::playlist)
    override suspend fun searchAlbums(query: String, page: Int, pageSize: Int) = codec.page(
        catalogPage(PluginCapability.Albums, "searchAlbums", query, page, pageSize), page, pageSize, codec::album)
    override suspend fun searchArtists(query: String, page: Int, pageSize: Int) = codec.page(
        catalogPage(PluginCapability.Artists, "searchArtists", query, page, pageSize), page, pageSize, codec::artist)
    override suspend fun rankings(page: Int, pageSize: Int) = codec.page(
        catalogPage(PluginCapability.Rankings, "rankings", null, page, pageSize), page, pageSize, codec::ranking)

    private suspend fun detail(capability: PluginCapability, method: String, id: MusicResourceId, page: Int, size: Int): JSONObject {
        require(id.sourceId == sourceId && page > 0 && size in 1..100) { "invalid collection request" }
        return JSONObject(call(capability, method, id.value, page, size))
    }
    override suspend fun playlistDetail(playlist: MusicPlaylistSummary, page: Int, pageSize: Int): MusicPlaylistDetail {
        val value = detail(PluginCapability.Playlists, "playlistDetail", playlist.id, page, pageSize)
        val summary = codec.playlist(value.getJSONObject("summary")); require(summary.id == playlist.id)
        val tracks = codec.tracks(value.getJSONObject("tracks"), page, pageSize)
        return MusicPlaylistDetail(summary, tracks.items, tracks.total, tracks.hasMore)
    }
    override suspend fun albumDetail(album: MusicAlbumSummary, page: Int, pageSize: Int): MusicAlbumDetail {
        val value = detail(PluginCapability.Albums, "albumDetail", album.id, page, pageSize)
        val summary = codec.album(value.getJSONObject("summary")); require(summary.id == album.id)
        val tracks = codec.tracks(value.getJSONObject("tracks"), page, pageSize)
        return MusicAlbumDetail(summary, tracks.items, tracks.total, tracks.hasMore)
    }
    override suspend fun artistDetail(artist: MusicArtistSummary, page: Int, pageSize: Int): MusicArtistDetail {
        val value = detail(PluginCapability.Artists, "artistDetail", artist.id, page, pageSize)
        val summary = codec.artist(value.getJSONObject("summary")); require(summary.id == artist.id)
        val tracks = codec.tracks(value.getJSONObject("tracks"), page, pageSize)
        return MusicArtistDetail(summary, tracks.items, tracks.total, tracks.hasMore)
    }
    override suspend fun rankingTracks(ranking: MusicRankingSummary, page: Int, pageSize: Int): MusicPage<MusicTrack> = codec.tracks(
        detail(PluginCapability.Rankings, "rankingTracks", ranking.id, page, pageSize), page, pageSize)
    override suspend fun homeFeed(playlistLimit: Int, newSongLimit: Int, rankingLimit: Int): MusicHomeFeed {
        require(playlistLimit in 0..100 && newSongLimit in 0..100 && rankingLimit in 0..100)
        val value = JSONObject(call(PluginCapability.HomeFeed, "homeFeed", playlistLimit, newSongLimit, rankingLimit))
        return MusicHomeFeed(codec.list(value, "recommendedPlaylists", playlistLimit, codec::playlist),
            codec.list(value, "newSongs", newSongLimit, codec::track), codec.list(value, "rankings", rankingLimit, codec::ranking))
    }
    override suspend fun accountSummary(): MusicAccountSummary? {
        val json = call(PluginCapability.UserLibrary, "accountSummary")
        return if (json == "null") null else codec.account(JSONObject(json))
    }
    override suspend fun userPlaylists(page: Int, pageSize: Int) = codec.page(
        catalogPage(PluginCapability.UserLibrary, "userPlaylists", null, page, pageSize), page, pageSize, codec::playlist)
    override suspend fun savedAlbums(page: Int, pageSize: Int) = codec.page(
        catalogPage(PluginCapability.UserLibrary, "savedAlbums", null, page, pageSize), page, pageSize, codec::album)
    override suspend fun followedArtists(page: Int, pageSize: Int) = codec.page(
        catalogPage(PluginCapability.UserLibrary, "followedArtists", null, page, pageSize), page, pageSize, codec::artist)
    override suspend fun isFavorite(track: MusicTrack): Boolean {
        checkTrack(track)
        return when (call(PluginCapability.Favorites, "isFavorite", trackJson(track))) {
            "true" -> true; "false" -> false; else -> error("favorite state must be a boolean")
        }
    }
    override suspend fun setFavorite(track: MusicTrack, favorite: Boolean) {
        checkTrack(track)
        require(call(PluginCapability.Favorites, "setFavorite", trackJson(track), favorite) == "null") { "write must resolve without a value" }
    }

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> {
        require(PluginCapability.Search in manifest.capabilities) { "UNSUPPORTED_OPERATION" }
        require(query.length <= 1024 && page > 0 && pageSize in 1..100)
        return codec.tracks(JSONObject(call(PluginCapability.Search, "search", query, page, pageSize)), page, pageSize)
    }

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution =
        resolveAudio(PluginCapability.Playback, "resolvePlayback", track, quality)

    override suspend fun resolveDownload(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution =
        resolveAudio(PluginCapability.Download, "resolveDownload", track, quality)

    private suspend fun resolveAudio(capability: PluginCapability, method: String, track: MusicTrack, quality: AudioQualityTier): PlaybackResolution {
        checkTrack(track)
        val result = JSONObject(call(capability, method, trackJson(track), wire(quality.name)))
        require(result.opt("type") is String) { "resolution type must be a string" }
        return when (result.getString("type")) {
            "playable" -> {
                val url = result.getString("url")
                validateMediaUrl(url)
                val rawHeaders = result.optJSONObject("headers")
                require(!result.has("headers") || rawHeaders != null) { "media headers must be an object" }
                require(rawHeaders == null || rawHeaders.length() <= 32) { "too many media headers" }
                val headers = buildMap {
                    rawHeaders?.keys()?.forEach { key ->
                        require(key.matches(Regex("[A-Za-z0-9-]{1,80}")) && key.lowercase(java.util.Locale.ROOT) !in setOf(
                            "host", "connection", "content-length", "transfer-encoding", "proxy-authorization", "proxy-connection", "upgrade"
                        )) { "forbidden media header" }
                        require(rawHeaders.opt(key) is String) { "media header must be a string" }
                        val value = rawHeaders.getString(key)
                        require(value.length <= 4096 && '\r' !in value && '\n' !in value) { "invalid media header" }
                        put(key, value)
                    }
                }
                val expires = optionalNumber(result, "expiresAtEpochMs", 9_007_199_254_740_991L)
                require(expires == null || expires > System.currentTimeMillis()) { "expired playback URL" }
                val format = if (result.has("format")) { require(result.opt("format") is String); result.getString("format").also { require(it.length <= 64) } } else null
                PluginMediaFormats.check(url, format, result.has("drm") || result.has("licenseUrl"))
                PlaybackResolution.Playable(url, headers, quality,
                    actualQuality = result.optString("actualQuality").takeIf { it.isNotEmpty() }?.let { value ->
                        AudioQualityTier.entries.firstOrNull { wire(it.name) == value } ?: error("invalid quality")
                    }, bitrate = optionalNumber(result, "bitrate", Int.MAX_VALUE.toLong())?.toInt(),
                    format = format, expiresAtEpochMs = expires, credentialReference = if (result.has("credential")) {
                        require(result.opt("credential") == "active" && manifest.authentication?.type in setOf("apiToken", "oauth2Pkce")) { "invalid media credential reference" }
                        "active"
                    } else null)
            }
            "preview" -> {
                val url = result.getString("url"); validateMediaUrl(url)
                PluginMediaFormats.check(url, drm = result.has("drm") || result.has("licenseUrl"))
                PlaybackResolution.Preview(url, optionalNumber(result, "durationMs", 86_400_000))
            }
            "loginRequired" -> PlaybackResolution.LoginRequired
            "subscriptionRequired" -> PlaybackResolution.SubscriptionRequired
            "regionRestricted" -> PlaybackResolution.RegionRestricted
            "copyrightRestricted" -> PlaybackResolution.CopyrightRestricted
            "unavailable" -> PlaybackResolution.Unavailable(result.optString("reason").take(512))
            else -> error("invalid playback resolution")
        }
    }

    override suspend fun lyrics(track: MusicTrack): LyricsDocument {
        require(PluginCapability.Lyrics in manifest.capabilities) { "UNSUPPORTED_OPERATION" }
        checkTrack(track)
        val result = JSONObject(call(PluginCapability.Lyrics, "lyrics", trackJson(track)))
        require(!result.has("isUnsynchronized") || result.opt("isUnsynchronized") is Boolean)
        val lines = result.getJSONArray("lines")
        require(lines.length() <= 2000) { "too many lyric lines" }
        var previous = -1L
        val mapped = (0 until lines.length()).map { index ->
            val line = lines.getJSONObject(index)
            val time = requireNotNull(optionalNumber(line, "timeMs", 86_400_000, minimum = 0))
            require(time in 0..86_400_000 && time >= previous) { "invalid lyric timing" }
            previous = time
            fun lyricText(key: String): String? {
                if (!line.has(key)) return null
                require(line.opt(key) is String) { "lyric text must be a string" }
                return line.getString(key).also { require(it.codePointCount(0, it.length) <= 4096) { "lyric text too long" } }
            }
            val text = requireNotNull(lyricText("text"))
            LyricLine(timeMs = time, text = text,
                durationMs = optionalNumber(line, "durationMs", 86_400_000, minimum = 0),
                translation = lyricText("translation")?.takeIf { it.isNotEmpty() },
                romanization = lyricText("romanization")?.takeIf { it.isNotEmpty() })
        }
        return LyricsDocument(mapped, quality = LyricQuality.LineSynchronized, pseudoTimingAllowed = false,
            isUnsynchronized = result.optBoolean("isUnsynchronized", false))
    }

    private fun checkTrack(track: MusicTrack) { require(track.id.sourceId == sourceId) { "track belongs to another source" } }
    private fun validateMediaUrl(url: String) {
        PluginResultValidator.validateUrlSyntax(url)
        val uri = java.net.URI(url)
        require(uri.scheme == "https" && uri.host in manifest.networkDomains && uri.port in setOf(-1, 443)) { "media permission denied" }
    }
    private fun trackJson(track: MusicTrack) = JSONObject().put("id", track.id.value).put("title", track.title)
        .put("artists", JSONArray(track.artists.map { artist -> JSONObject().put("name", artist.name).apply {
            artist.id?.takeIf { it.sourceId == sourceId }?.let { put("id", it.value) }
        } }))
        .apply {
            track.durationMs?.let { put("durationMs", it) }
            track.album?.let { album -> put("album", JSONObject().put("name", album.name).apply {
                album.id?.takeIf { it.sourceId == sourceId }?.let { put("id", it.value) }
            }) }
            put("availability", wire(track.availability.name))
            track.bestQuality?.let { put("bestQuality", wire(it.name)) }
            track.bestBitrate?.let { put("bestBitrate", it) }
        }
    private fun wire(value: String) = value.replaceFirstChar { it.lowercaseChar() }
    private fun optionalNumber(value: JSONObject, key: String, maximum: Long, minimum: Long = 1): Long? {
        if (!value.has(key)) return null
        require(value.opt(key) is Number) { "$key must be a number" }
        val number = value.getDouble(key)
        require(number.isFinite() && number == kotlin.math.floor(number) && number >= minimum && number <= maximum) { "invalid $key" }
        return number.toLong()
    }
}
