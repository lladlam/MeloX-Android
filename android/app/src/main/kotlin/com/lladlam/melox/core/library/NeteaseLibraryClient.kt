package com.lladlam.melox.core.library

import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.model.SearchSong
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** Authenticated library routes mirrored from MeloX NeteaseAPI.swift. */
class NeteaseLibraryClient(
    private val cookieProvider: () -> String,
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    private val syntheticDeviceId: String = randomHex(26).uppercase()
    private val authenticatedWeapi = com.lladlam.melox.core.network.NeteaseAuthenticatedWeapi(cookieProvider, httpClient)

    suspend fun snapshot(userId: Long): NeteaseLibrarySnapshot = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val allPlaylists = userPlaylistsBlocking(userId)
        val likedPlaylistId = allPlaylists.firstOrNull()?.id
        val playlists = allPlaylists
        val likedIds = likedSongIdsBlocking(userId)
        val likedById = likedIds.chunked(100).flatMap(::songDetailsBlocking).associateBy(SearchSong::id)
        // A transient failure in the song-detail request comes back as an empty payload.
        // Never report "no favorites" when the id list says otherwise, or the caller would
        // cache the empty result and the user's favorites would vanish.
        if (likedIds.isNotEmpty() && likedById.isEmpty()) {
            throw IOException("liked song details unavailable")
        }
        val liked = likedIds.mapNotNull(likedById::get)
        val recent = recentSongsBlocking(100)
        NeteaseLibrarySnapshot(playlists = playlists, likedSongs = liked, recentSongs = recent, likedPlaylistId = likedPlaylistId)
    }

    suspend fun playlistDetail(playlistId: Long): NeteasePlaylistDetail = withContext(Dispatchers.IO) {
        val base = fetchPlaylistBase(playlistId)
        val missing = base.desiredIds.filterNot(base.byId::containsKey)
        val extra = if (missing.isEmpty()) emptyList() else coroutineScope {
            // A 1000-track playlist otherwise needs ten serial song-detail calls;
            // fetching the 100-id chunks in parallel cuts first-open latency.
            missing.chunked(100)
                .map { chunk -> async(Dispatchers.IO) { songDetailsBlocking(chunk) } }
                .awaitAll()
                .flatten()
        }
        assemblePlaylistDetail(base, extra)
    }

    /** Summary plus the tracks already embedded in playlist/detail; no extra requests. */
    fun playlistDetailFastBlocking(playlistId: Long): NeteasePlaylistDetail =
        assemblePlaylistDetail(fetchPlaylistBase(playlistId), emptyList())

    /**
     * Server-side playlist shuffle ("云端随机"), mirroring Mei's
     * `/api/playlist/random/list/get`. Returns the shuffled song ids in server
     * order, or null when the endpoint rejects the request so callers can fall
     * back to a local shuffle.
     */
    suspend fun randomPlaylistIds(
        playlistId: Long,
        seedSongId: Long,
        alg: String = "",
    ): List<Long>? = withContext(Dispatchers.IO) {
        if (playlistId <= 0L || seedSongId <= 0L) return@withContext null
        ensureLoggedIn()
        val payload = JSONObject()
            .put("playlistId", playlistId)
            .put("songId", seedSongId)
            .put("alg", alg)
        val response = runCatching { authenticatedWeapi.post("/api/playlist/random/list/get", payload) }
            .recoverCatching { eapi("/api/playlist/random/list/get", payload, true) }
            .getOrNull() ?: return@withContext null
        val data = response.optJSONObject("data") ?: JSONObject()
        val raw = data.optJSONArray("songIds") ?: data.optJSONArray("list") ?: JSONArray()
        val ids = buildList {
            for (index in 0 until raw.length()) {
                val value = raw.opt(index)
                val id = when (value) {
                    is Number -> value.toLong()
                    is String -> value.toLongOrNull()
                    is JSONObject -> value.optLong("id", 0L).takeIf { it > 0L }
                        ?: value.optString("songId").toLongOrNull()
                    else -> null
                } ?: continue
                if (id > 0L) add(id)
            }
        }
        ids.distinct().takeIf { it.isNotEmpty() }
    }

    suspend fun homeContent(
        limit: Int = 12,
        area: String = "全部",
        userId: Long? = null,
        podcastsEnabled: Boolean = true,
        refresh: Boolean = false,
    ): NeteaseHomeContent = withContext(Dispatchers.IO) {
        val authenticated = NeteaseSessionStore.containsMusicU(cookieProvider())
        val serverBlocks = if (authenticated) {
            runCatching {
                NeteaseHomeBlockParser.parse(
                    eapi("/api/homepage/block/page", JSONObject().put("refresh", refresh), true),
                )
            }.getOrNull()
        } else null

        val playlistsResponse = eapi(
            "/api/personalized/playlist",
            JSONObject().put("limit", limit).put("total", true).put("n", 1_000),
            authenticated,
        )
        val fallbackPlaylists = parsePlaylists(playlistsResponse.optJSONArray("result") ?: JSONArray())
        val songData = JSONObject().put("type", "recommend").put("limit", limit).put("areaId", areaId(area))
        val songsResponse = if (authenticated) try {
            authenticatedWeapi.post("/api/personalized/newsong", songData)
        } catch (error: IOException) {
            if (!error.message.orEmpty().contains("空响应")) throw error
            eapi("/api/personalized/newsong", songData, true)
        } else eapi("/api/personalized/newsong", songData, false)
        val songItems = songsResponse.optJSONArray("result") ?: JSONArray()
        val fallbackNewSongs = buildList {
            for (index in 0 until songItems.length()) {
                val item = songItems.optJSONObject(index) ?: continue
                parseSong(item.optJSONObject("song") ?: item)?.let(::add)
            }
        }

        val accountPlaylists = if (authenticated && userId != null) {
            runCatching { userPlaylistsBlocking(userId).drop(1) }.getOrDefault(emptyList())
        } else emptyList()
        val fallbackRadar = accountPlaylists.filter { it.name.contains("雷达") }.take(limit)
        val fallbackPersonal = accountPlaylists.filter { it.creatorUserId == userId }.take(limit)
        val fallbackRecent = runCatching { topSongs("全部", limit) }.getOrDefault(emptyList())
        val fallbackCharts = runCatching { explorePlaylists("排行榜", limit) }.getOrDefault(emptyList())
        val fallbackRegional = runCatching { topSongs(area, limit) }.getOrDefault(emptyList())
        val fallbackRoaming = if (authenticated) runCatching { personalFm(explore = true, limit = limit) }.getOrDefault(emptyList()) else emptyList()
        val likedSeedId = if (authenticated && userId != null) runCatching { likedSongIdsBlocking(userId).firstOrNull() }.getOrNull() else null
        val fallbackSimilar = likedSeedId?.let { runCatching { similarSongsBlocking(it, limit) }.getOrDefault(emptyList()) }.orEmpty()
        val fallbackPodcasts = if (podcastsEnabled) runCatching {
            val path = "/api/program/recommend/v1"
            val data = JSONObject().put("limit", limit.coerceIn(1, 50)).put("offset", 0)
            val result = if (authenticated) try {
                authenticatedWeapi.post(path, data)
            } catch (error: IOException) {
                if (!error.message.orEmpty().contains("空响应")) throw error
                eapi(path, data, true)
            } else eapi(path, data, false)
            val values = result.optJSONArray("programs") ?: JSONArray()
            buildList {
                for (index in 0 until values.length()) {
                    val program = values.optJSONObject(index) ?: continue
                    val radio = program.optJSONObject("radio") ?: continue
                    val radioId = radio.optLong("id", -1L)
                    if (radioId <= 0L) continue
                    val programName = program.optString("name").ifBlank { radio.optString("name").ifBlank { "播客节目" } }
                    val cover = secureUrl(program.optString("coverUrl").takeIf(String::isNotBlank) ?: radio.optString("picUrl").orEmpty()).takeIf(String::isNotBlank)
                    val playbackSong = parseSong(program.optJSONObject("mainSong") ?: JSONObject())?.copy(
                        name = programName,
                        artists = program.optJSONObject("dj")?.optString("nickname").orEmpty().ifBlank { radio.optString("name") },
                        album = radio.optString("name"),
                        artworkUrl = cover,
                    )
                    add(NeteaseHomePodcast(radioId, programName, cover, program.optLong("id", 0L).takeIf { it > 0L }, playbackSong))
                }
            }.distinctBy(NeteaseHomePodcast::id).take(limit)
        }.getOrDefault(emptyList()) else emptyList()

        fun <T> serverOrFallback(server: List<T>?, fallback: List<T>): List<T> = server?.takeIf { it.isNotEmpty() } ?: fallback
        NeteaseHomeContent(
            playlists = serverOrFallback(serverBlocks?.recommendedPlaylists, fallbackPlaylists),
            newSongs = fallbackNewSongs,
            recentlyTrending = serverOrFallback(serverBlocks?.recentlyTrending, fallbackRecent),
            tailoredSongs = serverBlocks?.tailoredSongs.orEmpty(),
            chartPlaylists = serverOrFallback(serverBlocks?.chartPlaylists, fallbackCharts),
            radarPlaylists = serverOrFallback(serverBlocks?.radarPlaylists, fallbackRadar),
            personalPlaylists = serverOrFallback(serverBlocks?.personalPlaylists, fallbackPersonal),
            regionalSongs = serverOrFallback(serverBlocks?.regionalSongs, fallbackRegional),
            roamingSongs = serverOrFallback(serverBlocks?.roamingSongs, fallbackRoaming),
            similarSongs = serverOrFallback(serverBlocks?.likedSongRecommendations, fallbackSimilar),
            podcasts = if (podcastsEnabled) serverOrFallback(serverBlocks?.podcasts, fallbackPodcasts) else emptyList(),
        )
    }

    suspend fun explorePlaylists(category: String, limit: Int = 50): List<NeteasePlaylistSummary> =
        withContext(Dispatchers.IO) {
            val authenticated = NeteaseSessionStore.containsMusicU(cookieProvider())
            val response = when (category) {
                "推荐歌单" -> eapi(
                    "/api/personalized/playlist",
                    JSONObject().put("limit", limit).put("total", true).put("n", 1_000),
                    authenticated,
                )
                "排行榜" -> eapi("/api/toplist", JSONObject(), authenticated)
                "精品歌单" -> eapi(
                    "/api/playlist/highquality/list",
                    JSONObject().put("cat", "全部").put("limit", limit).put("lasttime", 0).put("total", true),
                    authenticated,
                )
                else -> eapi(
                    "/api/playlist/list",
                    JSONObject().put("cat", category).put("order", "hot").put("offset", 0)
                        .put("limit", limit).put("total", true),
                    authenticated,
                )
            }
            val values = when (category) {
                "推荐歌单" -> response.optJSONArray("result")
                "排行榜" -> response.optJSONArray("list")
                else -> response.optJSONArray("playlists")
            } ?: JSONArray()
            parsePlaylists(values)
        }

    suspend fun intelligenceModeSongs(seedSongId: Long, playlistId: Long): List<SearchSong> = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val response = eapi(
            uri = "/api/playmode/intelligence/list",
            data = JSONObject().put("songId", seedSongId).put("type", "fromPlayOne")
                .put("playlistId", playlistId).put("startMusicId", seedSongId).put("count", 1),
            authenticated = true,
        )
        val data = response.optJSONArray("data") ?: JSONArray()
        val ids = buildList {
            for (index in 0 until data.length()) data.optJSONObject(index)?.optLong("id", 0L)?.takeIf { it > 0L }?.let(::add)
        }
        if (ids.isEmpty()) throw IOException("网易云暂时没有返回可播放的心动模式歌曲")
        val byId = ids.chunked(100).flatMap { page -> songDetailsBlocking(page) }.associateBy(SearchSong::id)
        ids.mapNotNull(byId::get)
    }

    suspend fun dailyRecommendedSongs(): List<SearchSong> = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val response = eapi(
            "/api/v3/discovery/recommend/songs",
            JSONObject().put("offset", 0).put("total", true).put("limit", 100),
            true,
        )
        val values = response.optJSONObject("data")?.optJSONArray("dailySongs")
            ?: response.optJSONArray("recommend") ?: JSONArray()
        buildList {
            for (index in 0 until values.length()) parseSong(values.optJSONObject(index))?.let(::add)
        }
    }

    suspend fun personalFm(explore: Boolean = true, limit: Int = 30): List<SearchSong> = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val response = eapi(
            "/api/v1/radio/get",
            JSONObject().put("mode", if (explore) "EXPLORE" else "FAMILIAR").put("limit", limit.coerceIn(1, 50)),
            true,
        )
        val values = response.optJSONArray("data") ?: JSONArray()
        buildList {
            for (index in 0 until values.length()) parseSong(values.optJSONObject(index))?.let(::add)
        }
    }

    suspend fun hotSongs(): List<SearchSong> = withContext(Dispatchers.IO) {
        val charts = explorePlaylists("排行榜", 80)
        val hot = charts.firstOrNull { it.name.contains("热歌") } ?: charts.firstOrNull()
            ?: throw IOException("网易云没有返回排行榜")
        playlistDetailBlocking(hot.id).songs
    }

    fun similarSongsBlocking(songId: Long, limit: Int = 50): List<SearchSong> {
        if (songId <= 0L) return emptyList()
        val data = JSONObject().put("songid", songId).put("limit", limit.coerceIn(1, 50)).put("offset", 0)
        val loggedIn = NeteaseSessionStore.containsMusicU(cookieProvider())
        val response = if (loggedIn) try { authenticatedWeapi.post("/api/v1/discovery/simiSong", data) } catch (error: IOException) {
            if (!error.message.orEmpty().contains("空响应")) throw error
            eapi("/api/v1/discovery/simiSong", data, true)
        } else eapi("/api/v1/discovery/simiSong", data, false)
        val songs = response.optJSONArray("songs") ?: JSONArray()
        return buildList { for (index in 0 until songs.length()) parseSong(songs.optJSONObject(index))?.let(::add) }
    }

    suspend fun topSongs(area: String, limit: Int = 12): List<SearchSong> = withContext(Dispatchers.IO) {
        val data = JSONObject().put("areaId", areaId(area)).put("total", true)
        val response = if (NeteaseSessionStore.containsMusicU(cookieProvider())) try { authenticatedWeapi.post("/api/v1/discovery/new/songs", data) } catch (error: IOException) {
            if (!error.message.orEmpty().contains("空响应")) throw error
            eapi("/api/v1/discovery/new/songs", data, true)
        } else eapi("/api/v1/discovery/new/songs", data, false)
        val values = response.optJSONArray("data") ?: JSONArray()
        buildList { for (index in 0 until minOf(values.length(), limit.coerceIn(1, 100))) parseSong(values.optJSONObject(index))?.let(::add) }
    }

    private fun areaId(area: String): Int = when (area) { "华语", "中国" -> 7; "日本", "日语" -> 8; "韩国", "韩语" -> 16; "欧美" -> 96; else -> 0 }

    fun userPlaylistsBlocking(userId: Long, limit: Int = 2_000): List<NeteasePlaylistSummary> {
        val authenticated = NeteaseSessionStore.containsMusicU(cookieProvider())
        val response = eapi(
            uri = "/api/user/playlist",
            data = JSONObject()
                .put("uid", userId)
                .put("limit", limit)
                .put("offset", 0)
                .put("includeVideo", true),
            authenticated = authenticated,
        )
        return parsePlaylists(response.optJSONArray("playlist") ?: JSONArray())
    }

    fun likedSongIdsBlocking(userId: Long): List<Long> {
        ensureLoggedIn()
        val response = eapi(
            uri = "/api/song/like/get",
            data = JSONObject().put("uid", userId),
            authenticated = true,
        )
        val ids = response.optJSONArray("ids")
            ?: throw IOException("like/get rejected (code ${response.optInt("code", -1)})")
        return buildList(ids.length()) {
            for (index in 0 until ids.length()) {
                ids.optLong(index).takeIf { it > 0L }?.let(::add)
            }
        }
    }

    fun recentSongsBlocking(limit: Int = 100): List<SearchSong> {
        ensureLoggedIn()
        val path = "/api/play-record/song/list"
        val data = JSONObject().put("limit", limit)
        val response = try {
            authenticatedWeapi.post(path, data)
        } catch (error: IOException) {
            if (!error.message.orEmpty().contains("空响应")) throw error
            eapi(path, data, true)
        }
        val list = response.optJSONObject("data")?.optJSONArray("list") ?: JSONArray()
        return buildList {
            for (index in 0 until list.length()) {
                val songObject = list.optJSONObject(index)?.optJSONObject("data") ?: continue
                parseSong(songObject)?.let(::add)
            }
        }
    }

    fun playlistDetailBlocking(playlistId: Long): NeteasePlaylistDetail {
        val base = fetchPlaylistBase(playlistId)
        val missing = base.desiredIds.filterNot(base.byId::containsKey)
        val extra = missing.chunked(100).flatMap(::songDetailsBlocking)
        return assemblePlaylistDetail(base, extra)
    }

    private data class PlaylistDetailBase(
        val summary: NeteasePlaylistSummary,
        val desiredIds: List<Long>,
        val byId: Map<Long, SearchSong>,
    )

    private fun fetchPlaylistBase(playlistId: Long): PlaylistDetailBase {
        val authenticated = NeteaseSessionStore.containsMusicU(cookieProvider())
        val response = eapi(
            uri = "/api/v6/playlist/detail",
            data = JSONObject()
                .put("id", playlistId)
                .put("n", 1000)
                .put("s", 8),
            authenticated = authenticated,
        )
        val playlist = response.optJSONObject("playlist")
            ?: throw IOException("网易云没有返回歌单详情")
        val summary = parsePlaylist(playlist)
            ?: throw IOException("无法解析歌单")

        val trackIds = playlist.optJSONArray("trackIds") ?: JSONArray()
        val desiredIds = buildList {
            for (index in 0 until trackIds.length()) {
                trackIds.optJSONObject(index)?.optLong("id")
                    ?.takeIf { it > 0L }
                    ?.let(::add)
            }
        }
        val initialTracks = playlist.optJSONArray("tracks") ?: JSONArray()
        val byId = LinkedHashMap<Long, SearchSong>()
        for (index in 0 until initialTracks.length()) {
            parseSong(initialTracks.optJSONObject(index))?.let { byId[it.id] = it }
        }
        return PlaylistDetailBase(summary, desiredIds, byId)
    }

    private fun assemblePlaylistDetail(
        base: PlaylistDetailBase,
        extra: List<SearchSong>,
    ): NeteasePlaylistDetail {
        val byId = LinkedHashMap(base.byId)
        extra.forEach { byId[it.id] = it }
        val songs = if (base.desiredIds.isNotEmpty()) {
            base.desiredIds.mapNotNull(byId::get)
        } else {
            byId.values.toList()
        }
        return NeteasePlaylistDetail(base.summary, songs)
    }

    fun songDetailsBlocking(ids: List<Long>): List<SearchSong> {
        if (ids.isEmpty()) return emptyList()
        val descriptors = JSONArray().apply { ids.take(100).forEach { put(JSONObject().put("id", it)) } }
        val path = "/api/v3/song/detail"
        val data = JSONObject().put("c", descriptors.toString())
        val authenticated = NeteaseSessionStore.containsMusicU(cookieProvider())
        val response = if (authenticated) try {
            authenticatedWeapi.post(path, data)
        } catch (error: IOException) {
            if (!error.message.orEmpty().contains("空响应")) throw error
            eapi(path, data, true)
        } else eapi(path, data, false)
        val songs = response.optJSONArray("songs") ?: JSONArray()
        return buildList {
            for (index in 0 until songs.length()) {
                parseSong(songs.optJSONObject(index))?.let(::add)
            }
        }
    }

    private fun parsePlaylists(array: JSONArray): List<NeteasePlaylistSummary> = buildList {
        for (index in 0 until array.length()) {
            parsePlaylist(array.optJSONObject(index))?.let(::add)
        }
    }

    private fun parsePlaylist(value: JSONObject?): NeteasePlaylistSummary? {
        value ?: return null
        val id = value.optLong("id", -1L)
        if (id <= 0L) return null
        val description = value.optString("description")
            .takeIf(String::isNotBlank)
            ?: value.optString("copywriter").takeIf(String::isNotBlank)
        return NeteasePlaylistSummary(
            id = id,
            name = value.optString("name").ifBlank { "未命名歌单" },
            coverUrl = sequenceOf(
                value.optString("coverImgUrl"),
                value.optString("picUrl"),
                value.optString("coverUrl"),
            )
                .firstOrNull(String::isNotBlank)
                ?.let(::secureUrl),
            trackCount = value.optInt("trackCount").coerceAtLeast(0),
            creatorName = value.optJSONObject("creator")
                ?.optString("nickname")
                .orEmpty(),
            creatorUserId = value.optJSONObject("creator")
                ?.optLong("userId", -1L)
                ?.takeIf { it > 0L },
            playCount = value.optLong("playCount", 0L).coerceAtLeast(0L),
            description = description,
        )
    }

    private fun parseSong(value: JSONObject?): SearchSong? {
        value ?: return null
        val id = value.optLong("id", -1L)
        if (id <= 0L) return null
        val artistsArray = value.optJSONArray("ar")
            ?: value.optJSONArray("artists")
            ?: JSONArray()
        val artists = buildList {
            for (index in 0 until artistsArray.length()) {
                artistsArray.optJSONObject(index)
                    ?.optString("name")
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
            }
        }.joinToString(" / ")
        val artistId = if (artistsArray.length() > 0) artistsArray.optJSONObject(0)?.optLong("id")?.takeIf { it > 0 } else null
        val album = value.optJSONObject("al") ?: value.optJSONObject("album")
        val albumId = album?.optLong("id")?.takeIf { it > 0 }
        val artwork = album?.optString("picUrl")
            ?.takeIf(String::isNotBlank)
            ?.let(::secureUrl)
            ?: album?.optString("blurPicUrl")
                ?.takeIf(String::isNotBlank)
                ?.let(::secureUrl)
        val duration = value.optLong("dt", value.optLong("duration", 0L)).coerceAtLeast(0L)
        return SearchSong(
            id = id,
            name = value.optString("name").ifBlank { "未知歌曲" },
            artists = artists.ifBlank { "未知歌手" },
            album = album?.optString("name").orEmpty(),
            albumId = albumId,
            artistId = artistId,
            artworkUrl = artwork,
            durationMs = duration,
        )
    }

    private fun ensureLoggedIn() {
        if (!NeteaseSessionStore.containsMusicU(cookieProvider())) {
            throw IOException("请先登录网易云音乐")
        }
    }

    private fun eapi(
        uri: String,
        data: JSONObject,
        authenticated: Boolean = true,
    ): JSONObject {
        val timestampMillis = System.currentTimeMillis()
        val cookieHeader = cookieProvider()
        val cookies = NeteaseSessionStore.parseCookie(cookieHeader)
        val header = if (authenticated) {
            authenticatedEapiHeader(cookies, timestampMillis)
        } else {
            JSONObject()
                .put("os", "ios")
                .put("appver", "9.0.90")
                .put("osver", "18.0")
                .put("requestId", "${timestampMillis}_0000")
        }
        val requestData = JSONObject(data.toString())
            .put("header", header)
            .put("e_r", false)
        val json = requestData.toString()
        val digest = md5Hex("nobody${uri}use${json}md5forencrypt")
        val encryptedPayload = "$uri-36cd479b6b5-$json-36cd479b6b5-$digest"
        val params = aesEcbEncrypt(
            encryptedPayload.toByteArray(Charsets.UTF_8),
            "e82ckenh8dichen8".toByteArray(Charsets.UTF_8),
        ).toHexUppercase()

        val requestBuilder = Request.Builder()
            .url("https://interface.music.163.com${uri.replace("/api/", "/eapi/")}")
            .header(
                "User-Agent",
                if (authenticated) {
                    "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"
                } else {
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"
                },
            )
            .header("Accept", "*/*")
        if (authenticated) {
            requestBuilder.header("Cookie", encodedCookieHeader(header))
        }
        val request = requestBuilder
            .post(FormBody.Builder().add("params", params).build())
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw IOException("网易云请求失败：HTTP ${response.code}")
            }
            if (body.isBlank()) throw IOException("网易云返回了空响应")
            val result = JSONObject(body)
            val code = result.optInt("code", response.code)
            if (code !in 200..299) {
                val message = result.optString("message")
                    .ifBlank { result.optString("msg") }
                    .ifBlank { "请求失败" }
                throw IOException("网易云请求失败（$code）：$message")
            }
            return result
        }
    }

    private fun authenticatedEapiHeader(
        cookies: Map<String, String>,
        timestampMillis: Long,
    ): JSONObject {
        val header = JSONObject()
            .put("osver", cookies["osver"] ?: "16.2")
            .put("deviceId", cookies["deviceId"] ?: syntheticDeviceId)
            .put("os", cookies["os"] ?: "iPhone OS")
            .put("appver", cookies["appver"] ?: "9.0.90")
            .put("versioncode", cookies["versioncode"] ?: "140")
            .put("mobilename", cookies["mobilename"] ?: "")
            .put("buildver", cookies["buildver"] ?: (timestampMillis / 1_000L).toString())
            .put("resolution", cookies["resolution"] ?: "1170x2532")
            .put("__csrf", cookies["__csrf"] ?: "")
            .put("channel", cookies["channel"] ?: "distribution")
            .put("requestId", "${timestampMillis}_${randomDigits(4)}")
        cookies["MUSIC_U"]?.takeIf(String::isNotBlank)?.let { header.put("MUSIC_U", it) }
        return header
    }

    private fun encodedCookieHeader(values: JSONObject): String {
        val keys = buildList {
            val iterator = values.keys()
            while (iterator.hasNext()) add(iterator.next())
        }.sorted()
        return keys.joinToString("; ") { key ->
            "${encodeURIComponent(key)}=${encodeURIComponent(values.optString(key))}"
        }
    }

    private fun secureUrl(url: String): String =
        if (url.startsWith("http://", true)) "https://${url.substringAfter("://")}" else url

    private fun encodeURIComponent(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~")

    private fun randomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun randomDigits(length: Int): String = buildString(length) {
        repeat(length) { append(('0'.code + SecureRandom().nextInt(10)).toChar()) }
    }

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun aesEcbEncrypt(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    private fun ByteArray.toHexUppercase(): String =
        joinToString("") { "%02X".format(it) }
}
