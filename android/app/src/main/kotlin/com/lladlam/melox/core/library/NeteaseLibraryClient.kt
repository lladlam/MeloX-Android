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
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** Authenticated library routes mirrored from MeloX NeteaseAPI.swift. */
class NeteaseLibraryClient(
    private val cookieProvider: () -> String,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val syntheticDeviceId: String = randomHex(26).uppercase()

    suspend fun snapshot(userId: Long): NeteaseLibrarySnapshot = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val playlists = userPlaylistsBlocking(userId)
        val likedIds = likedSongIdsBlocking(userId)
        val liked = songDetailsBlocking(likedIds.take(100))
        val recent = recentSongsBlocking(100)
        NeteaseLibrarySnapshot(
            playlists = playlists,
            likedSongs = liked,
            recentSongs = recent,
        )
    }

    suspend fun playlistDetail(playlistId: Long): NeteasePlaylistDetail =
        withContext(Dispatchers.IO) { playlistDetailBlocking(playlistId) }

    suspend fun homeContent(limit: Int = 12): NeteaseHomeContent = withContext(Dispatchers.IO) {
        val authenticated = NeteaseSessionStore.containsMusicU(cookieProvider())
        val playlistsResponse = eapi(
            uri = "/api/personalized/playlist",
            data = JSONObject().put("limit", limit).put("total", true).put("n", 1_000),
            authenticated = authenticated,
        )
        val songsResponse = eapi(
            uri = "/api/personalized/newsong",
            data = JSONObject().put("type", "recommend").put("limit", limit).put("areaId", 0),
            authenticated = authenticated,
        )
        val songItems = songsResponse.optJSONArray("result") ?: JSONArray()
        val songs = buildList {
            for (index in 0 until songItems.length()) {
                val item = songItems.optJSONObject(index) ?: continue
                parseSong(item.optJSONObject("song") ?: item)?.let(::add)
            }
        }
        NeteaseHomeContent(
            playlists = parsePlaylists(playlistsResponse.optJSONArray("result") ?: JSONArray()),
            newSongs = songs,
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

    fun similarSongsBlocking(songId: Long, limit: Int = 50): List<SearchSong> {
        if (songId <= 0L) return emptyList()
        // Upstream uses /api/v1/discovery/simiSong. The direct EAPI transport is
        // accepted by the same interface host and keeps Android on one client.
        val response = eapi(
            uri = "/api/v1/discovery/simiSong",
            data = JSONObject().put("songid", songId).put("limit", limit.coerceIn(1, 50)),
            authenticated = NeteaseSessionStore.containsMusicU(cookieProvider()),
        )
        val songs = response.optJSONArray("songs") ?: JSONArray()
        return buildList {
            for (index in 0 until songs.length()) parseSong(songs.optJSONObject(index))?.let(::add)
        }
    }

    fun userPlaylistsBlocking(userId: Long, limit: Int = 2_000): List<NeteasePlaylistSummary> {
        ensureLoggedIn()
        val response = eapi(
            uri = "/api/user/playlist",
            data = JSONObject()
                .put("uid", userId)
                .put("limit", limit)
                .put("offset", 0)
                .put("includeVideo", true),
            authenticated = true,
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
        val ids = response.optJSONArray("ids") ?: JSONArray()
        return buildList(ids.length()) {
            for (index in 0 until ids.length()) {
                ids.optLong(index).takeIf { it > 0L }?.let(::add)
            }
        }
    }

    fun recentSongsBlocking(limit: Int = 100): List<SearchSong> {
        ensureLoggedIn()
        val response = eapi(
            uri = "/api/play-record/song/list",
            data = JSONObject().put("limit", limit),
            authenticated = true,
        )
        val list = response.optJSONObject("data")?.optJSONArray("list") ?: JSONArray()
        return buildList {
            for (index in 0 until list.length()) {
                val songObject = list.optJSONObject(index)?.optJSONObject("data") ?: continue
                parseSong(songObject)?.let(::add)
            }
        }
    }

    fun playlistDetailBlocking(playlistId: Long): NeteasePlaylistDetail {
        val authenticated = NeteaseSessionStore.containsMusicU(cookieProvider())
        val response = eapi(
            uri = "/api/v6/playlist/detail",
            data = JSONObject()
                .put("id", playlistId)
                .put("n", 100)
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
        val missing = desiredIds.filterNot(byId::containsKey)
        if (missing.isNotEmpty()) {
            missing.chunked(100).forEach { page ->
                songDetailsBlocking(page).forEach { byId[it.id] = it }
            }
        }
        val songs = if (desiredIds.isNotEmpty()) {
            desiredIds.mapNotNull(byId::get)
        } else {
            byId.values.toList()
        }
        return NeteasePlaylistDetail(summary, songs)
    }

    fun songDetailsBlocking(ids: List<Long>): List<SearchSong> {
        if (ids.isEmpty()) return emptyList()
        val descriptors = JSONArray().apply {
            ids.take(100).forEach { put(JSONObject().put("id", it)) }
        }
        val response = eapi(
            uri = "/api/v3/song/detail",
            data = JSONObject().put("c", descriptors.toString()),
            authenticated = NeteaseSessionStore.containsMusicU(cookieProvider()),
        )
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
        val album = value.optJSONObject("al") ?: value.optJSONObject("album")
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
