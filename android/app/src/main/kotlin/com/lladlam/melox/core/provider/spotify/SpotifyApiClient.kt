package com.lladlam.melox.core.provider.spotify

import android.content.Context
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
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.music.model.TrackAvailability
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal object SpotifyJsonMapper {
    fun track(value: JSONObject): MusicTrack? {
        val id = value.optString("id").takeIf(String::isNotBlank) ?: return null
        val title = value.optString("name").takeIf(String::isNotBlank) ?: return null
        val albumObject = value.optJSONObject("album")
        val artwork = imageUrl(albumObject?.optJSONArray("images"))
        val album = albumObject?.optString("name")?.takeIf(String::isNotBlank)?.let { name ->
            MusicAlbumRef(
                id = albumObject.optString("id").takeIf(String::isNotBlank)
                    ?.let { MusicResourceId(MusicSource.Spotify, it) },
                name = name,
                artworkUrl = artwork,
            )
        }
        return MusicTrack(
            id = MusicResourceId(MusicSource.Spotify, id),
            title = title,
            artists = artists(value.optJSONArray("artists")),
            album = album,
            artworkUrl = artwork,
            durationMs = value.optLong("duration_ms").takeIf { it > 0L },
            availability = if (value.optBoolean("is_playable", true)) TrackAvailability.Playable
            else TrackAvailability.RegionRestricted,
            providerMetadata = ProviderTrackMetadata.Spotify(
                trackId = id,
                isrc = value.optJSONObject("external_ids")?.optString("isrc")?.takeIf(String::isNotBlank),
            ),
        )
    }

    fun playlist(value: JSONObject): MusicPlaylistSummary? {
        val id = value.optString("id").takeIf(String::isNotBlank) ?: return null
        val title = value.optString("name").takeIf(String::isNotBlank) ?: return null
        val count = value.optJSONObject("items")?.optInt("total")
            ?: value.optJSONObject("tracks")?.optInt("total")
        return MusicPlaylistSummary(
            id = MusicResourceId(MusicSource.Spotify, id),
            title = title,
            artworkUrl = imageUrl(value.optJSONArray("images")),
            creatorName = value.optJSONObject("owner")?.optString("display_name")?.takeIf(String::isNotBlank),
            description = value.optString("description").takeIf(String::isNotBlank),
            trackCount = count?.takeIf { it >= 0 },
        )
    }

    fun album(value: JSONObject): MusicAlbumSummary? {
        val id = value.optString("id").takeIf(String::isNotBlank) ?: return null
        val title = value.optString("name").takeIf(String::isNotBlank) ?: return null
        return MusicAlbumSummary(
            id = MusicResourceId(MusicSource.Spotify, id),
            title = title,
            artworkUrl = imageUrl(value.optJSONArray("images")),
            artists = artists(value.optJSONArray("artists")),
            releaseDate = value.optString("release_date").takeIf(String::isNotBlank),
            trackCount = value.optLong("total_tracks").takeIf { it > 0L },
        )
    }

    fun artist(value: JSONObject): MusicArtistSummary? {
        val id = value.optString("id").takeIf(String::isNotBlank) ?: return null
        val name = value.optString("name").takeIf(String::isNotBlank) ?: return null
        return MusicArtistSummary(
            id = MusicResourceId(MusicSource.Spotify, id),
            name = name,
            artworkUrl = imageUrl(value.optJSONArray("images")),
        )
    }

    fun playlistItem(value: JSONObject): MusicTrack? =
        track(value.optJSONObject("item") ?: value.optJSONObject("track") ?: value)

    private fun artists(values: JSONArray?): List<MusicArtistRef> = values.objects().mapNotNull { value ->
        value.optString("name").takeIf(String::isNotBlank)?.let { name ->
            MusicArtistRef(
                id = value.optString("id").takeIf(String::isNotBlank)
                    ?.let { MusicResourceId(MusicSource.Spotify, it) },
                name = name,
            )
        }
    }

    private fun imageUrl(images: JSONArray?): String? = images?.optJSONObject(0)
        ?.optString("url")?.takeIf(String::isNotBlank)
}

class SpotifyApiClient(
    context: Context,
    private val clientId: String,
    private val httpClient: OkHttpClient,
) {
    private val appContext = context.applicationContext
    private val oauth = SpotifyOAuth(appContext, clientId, httpClient)
    private val refreshMutex = Mutex()

    suspend fun accountSummary(): MusicAccountSummary = withContext(Dispatchers.IO) {
        val root = get("me")
        val id = root.optString("id").takeIf(String::isNotBlank)
            ?: throw IOException("Spotify 账号响应缺少 id")
        SpotifySessionStore.updateAccountId(appContext, id)
        MusicAccountSummary(
            source = MusicSource.Spotify,
            id = id,
            displayName = root.optString("display_name").ifBlank { id },
            avatarUrl = root.optJSONArray("images")?.optJSONObject(0)?.optString("url")?.takeIf(String::isNotBlank),
            subtitle = root.optString("product").takeIf(String::isNotBlank),
        )
    }

    suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> =
        search(query, page, pageSize, "track", "tracks", SpotifyJsonMapper::track)

    suspend fun searchPlaylists(query: String, page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        search(query, page, pageSize, "playlist", "playlists", SpotifyJsonMapper::playlist)

    suspend fun searchAlbums(query: String, page: Int, pageSize: Int): MusicPage<MusicAlbumSummary> =
        search(query, page, pageSize, "album", "albums", SpotifyJsonMapper::album)

    suspend fun searchArtists(query: String, page: Int, pageSize: Int): MusicPage<MusicArtistSummary> =
        search(query, page, pageSize, "artist", "artists", SpotifyJsonMapper::artist)

    suspend fun userPlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> = withContext(Dispatchers.IO) {
        val result = pageItems("me/playlists", page, pageSize, SpotifyJsonMapper::playlist)
        MusicPage(result.items, page.coerceAtLeast(1), pageSize.coerceAtLeast(1), result.total)
    }

    suspend fun writablePlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> = withContext(Dispatchers.IO) {
        val accountId = accountSummary().id
        val requested = pageSize.coerceIn(1, 50)
        var offset = 0
        val writable = mutableListOf<MusicPlaylistSummary>()
        do {
            val root = get("me/playlists", mapOf("limit" to "50", "offset" to offset.toString()))
            val chunk = root.optJSONArray("items").objects()
            writable += chunk.filter { item ->
                item.optBoolean("collaborative") || item.optJSONObject("owner")?.optString("id") == accountId
            }.mapNotNull(SpotifyJsonMapper::playlist)
            offset += chunk.size
            val total = root.optLong("total").coerceAtLeast(0L)
        } while (chunk.isNotEmpty() && offset < total)
        val from = ((page.coerceAtLeast(1) - 1) * requested).coerceAtMost(writable.size)
        val items = writable.drop(from).take(requested)
        MusicPage(items, page.coerceAtLeast(1), requested, writable.size.toLong())
    }

    suspend fun playlistDetail(
        playlist: MusicPlaylistSummary,
        page: Int,
        pageSize: Int,
    ): MusicPlaylistDetail = withContext(Dispatchers.IO) {
        val root = get("playlists/${playlist.id.value}")
        val summary = SpotifyJsonMapper.playlist(root) ?: playlist
        val result = pageItems("playlists/${playlist.id.value}/items", page, pageSize, SpotifyJsonMapper::playlistItem)
        MusicPlaylistDetail(summary, result.items, result.total)
    }

    suspend fun albumDetail(album: MusicAlbumSummary, page: Int, pageSize: Int): MusicAlbumDetail =
        withContext(Dispatchers.IO) {
            val root = get("albums/${album.id.value}")
            val summary = SpotifyJsonMapper.album(root) ?: album
            val result = pageItems("albums/${album.id.value}/tracks", page, pageSize) { value ->
                SpotifyJsonMapper.track(value.apply {
                    if (!has("album")) put("album", JSONObject().put("id", album.id.value)
                        .put("name", summary.title).put("images", root.optJSONArray("images")))
                })
            }
            MusicAlbumDetail(summary, result.items, result.total)
        }

    suspend fun artistDetail(artist: MusicArtistSummary, page: Int, pageSize: Int): MusicArtistDetail =
        withContext(Dispatchers.IO) {
            val summary = SpotifyJsonMapper.artist(get("artists/${artist.id.value}")) ?: artist
            val tracks = get("artists/${artist.id.value}/top-tracks")
                .optJSONArray("tracks").objects().mapNotNull(SpotifyJsonMapper::track)
            val size = pageSize.coerceAtLeast(1)
            val offset = (page.coerceAtLeast(1) - 1) * size
            MusicArtistDetail(summary, tracks.drop(offset).take(size), tracks.size.toLong())
        }

    suspend fun setFavorite(track: MusicTrack, favorite: Boolean) = withContext(Dispatchers.IO) {
        require(track.id.source == MusicSource.Spotify) { "只能收藏 Spotify 曲目" }
        request(
            if (favorite) "PUT" else "DELETE",
            "me/library",
            query = mapOf("uris" to "spotify:track:${track.id.value}"),
        )
        Unit
    }

    suspend fun addTrackToPlaylist(track: MusicTrack, playlist: MusicPlaylistSummary) = withContext(Dispatchers.IO) {
        require(track.id.source == MusicSource.Spotify && playlist.id.source == MusicSource.Spotify) {
            "只能将 Spotify 曲目添加到 Spotify 歌单"
        }
        val body = JSONObject().put("uris", JSONArray().put("spotify:track:${track.id.value}"))
        request("POST", "playlists/${playlist.id.value}/items", body)
        Unit
    }

    private suspend fun <T> search(
        query: String,
        page: Int,
        pageSize: Int,
        type: String,
        container: String,
        mapper: (JSONObject) -> T?,
    ): MusicPage<T> = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceAtLeast(1)
        var offset = (safePage - 1) * safeSize
        var remaining = safeSize
        var total: Long? = null
        val items = mutableListOf<T>()
        while (remaining > 0 && offset <= MaximumSearchOffset) {
            val limit = remaining.coerceAtMost(MaximumSearchLimit)
            val root = get("search", mapOf(
                "q" to query.trim(),
                "type" to type,
                "market" to "from_token",
                "limit" to limit.toString(),
                "offset" to offset.toString(),
            )).optJSONObject(container) ?: JSONObject()
            total = root.optLong("total").takeIf { it >= 0L } ?: total
            val chunk = root.optJSONArray("items").objects()
            items += chunk.mapNotNull(mapper)
            if (chunk.size < limit) break
            offset += chunk.size
            remaining -= chunk.size
        }
        MusicPage(
            items,
            safePage,
            safeSize,
            total,
        )
    }

    private suspend fun <T> pageItems(
        path: String,
        page: Int,
        pageSize: Int,
        mapper: (JSONObject) -> T?,
    ): PageResult<T> {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceAtLeast(1)
        var offset = (safePage - 1) * safeSize
        var remaining = safeSize
        var total: Long? = null
        val items = mutableListOf<T>()
        while (remaining > 0) {
            val limit = remaining.coerceAtMost(50)
            val root = get(path, mapOf("limit" to limit.toString(), "offset" to offset.toString()))
            total = root.optLong("total").takeIf { it >= 0L } ?: total
            val chunk = root.optJSONArray("items").objects()
            items += chunk.mapNotNull(mapper)
            if (chunk.size < limit) break
            remaining -= chunk.size
            offset += chunk.size
        }
        return PageResult(items, total)
    }

    private suspend fun get(path: String, query: Map<String, String> = emptyMap()): JSONObject =
        request("GET", path, query = query)

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        query: Map<String, String> = emptyMap(),
        retried: Boolean = false,
    ): JSONObject {
        var session = SpotifySessionStore.read(appContext)
        if (!session.isLoggedIn) throw IOException("请先登录 Spotify")
        if (session.needsRefresh()) session = refresh(session.accessToken)
        val url = apiUrl(path, query)
        val requestBody = body?.toString()?.toRequestBody(JsonMediaType)
            ?: "".toRequestBody().takeIf { method == "PUT" }
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Accept", "application/json")
            .method(method, requestBody.takeIf { method != "GET" })
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (response.code == 401 && !retried) {
                refresh(session.accessToken)
                return request(method, path, body, query, retried = true)
            }
            if (response.code == 429) {
                val retryAfter = response.header("Retry-After")?.let { value ->
                    value.toLongOrNull()?.let { "，请在 $it 秒后重试" } ?: "，请稍后重试（Retry-After: $value）"
                }.orEmpty()
                throw IOException("Spotify API 请求过于频繁$retryAfter")
            }
            if (!response.isSuccessful) {
                val apiMessage = runCatching {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message")
                }.getOrNull()?.takeIf(String::isNotBlank)
                throw IOException("Spotify API ${response.code}: ${apiMessage ?: "请求失败"}")
            }
            return if (responseBody.isBlank()) JSONObject() else JSONObject(responseBody)
        }
    }

    private suspend fun refresh(failedAccessToken: String): SpotifySession = refreshMutex.withLock {
        val current = SpotifySessionStore.read(appContext)
        if (current.accessToken.isNotBlank() && current.accessToken != failedAccessToken && !current.needsRefresh()) {
            current
        } else {
            if (clientId.isBlank()) throw IOException("未配置 Spotify Client ID；请设置 Gradle property meloxSpotifyClientId")
            oauth.refresh(current)
        }
    }

    private fun apiUrl(path: String, query: Map<String, String>): HttpUrl = ApiBase.toHttpUrl().newBuilder()
        .addPathSegments(path.trim('/'))
        .apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }
        .build()

    private data class PageResult<T>(val items: List<T>, val total: Long?)

    private companion object {
        const val ApiBase = "https://api.spotify.com/v1/"
        const val MaximumSearchLimit = 10
        const val MaximumSearchOffset = 1_000
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
    }
}

private fun JSONArray?.objects(): List<JSONObject> = buildList {
    if (this@objects == null) return@buildList
    for (index in 0 until length()) optJSONObject(index)?.let(::add)
}
