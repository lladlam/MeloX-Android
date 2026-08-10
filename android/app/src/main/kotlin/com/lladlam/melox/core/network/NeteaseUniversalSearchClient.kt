package com.lladlam.melox.core.network

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

enum class MeloXSearchKind(val apiType: Int, val title: String) {
    Songs(1, "歌曲"), Albums(10, "专辑"), Artists(100, "歌手"), Playlists(1000, "歌单"), Podcasts(1009, "播客")
}

data class MeloXSearchMediaItem(
    val id: Long,
    val kind: MeloXSearchKind,
    val title: String,
    val subtitle: String = "",
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
)

/** Search routes mirrored from upstream MeloX SearchView/NeteaseAPI. */
class NeteaseUniversalSearchClient(
    private val cookieProvider: () -> String,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val syntheticDeviceId = randomHex(26).uppercase()

    suspend fun searchMedia(keywords: String, kind: MeloXSearchKind, limit: Int = 30): List<MeloXSearchMediaItem> = withContext(Dispatchers.IO) {
        if (kind == MeloXSearchKind.Songs) return@withContext emptyList()
        val query = keywords.trim()
        if (query.isEmpty()) return@withContext emptyList()
        val response = eapi(
            "/api/search/get",
            JSONObject().put("s", query).put("type", kind.apiType).put("limit", limit.coerceIn(1, 50)).put("offset", 0),
        )
        val result = response.optJSONObject("result") ?: return@withContext emptyList()
        val values = when (kind) {
            MeloXSearchKind.Albums -> result.optJSONArray("albums")
            MeloXSearchKind.Artists -> result.optJSONArray("artists")
            MeloXSearchKind.Playlists -> result.optJSONArray("playlists")
            MeloXSearchKind.Podcasts -> result.optJSONArray("djRadios") ?: result.optJSONArray("radios")
            else -> null
        } ?: JSONArray()
        buildList {
            for (i in 0 until values.length()) {
                val value = values.optJSONObject(i) ?: continue
                val id = value.optLong("id", -1L)
                if (id <= 0L) continue
                when (kind) {
                    MeloXSearchKind.Albums -> add(MeloXSearchMediaItem(
                        id, kind,
                        value.optString("name").ifBlank { "未命名专辑" },
                        value.optJSONObject("artist")?.optString("name").orEmpty(),
                        secure(value.optString("picUrl").takeIf(String::isNotBlank)),
                        value.optInt("size", 0),
                    ))
                    MeloXSearchKind.Artists -> add(MeloXSearchMediaItem(
                        id, kind,
                        value.optString("name").ifBlank { "未知歌手" },
                        buildList {
                            val aliases = value.optJSONArray("alias") ?: JSONArray()
                            for (j in 0 until aliases.length()) aliases.optString(j).takeIf(String::isNotBlank)?.let(::add)
                        }.joinToString(" / "),
                        secure(value.optString("picUrl").takeIf(String::isNotBlank) ?: value.optString("img1v1Url").takeIf(String::isNotBlank)),
                    ))
                    MeloXSearchKind.Playlists -> add(MeloXSearchMediaItem(
                        id, kind,
                        value.optString("name").ifBlank { "未命名歌单" },
                        value.optJSONObject("creator")?.optString("nickname").orEmpty(),
                        secure(value.optString("coverImgUrl").takeIf(String::isNotBlank) ?: value.optString("picUrl").takeIf(String::isNotBlank)),
                        value.optInt("trackCount", 0),
                    ))
                    MeloXSearchKind.Podcasts -> add(MeloXSearchMediaItem(
                        id, kind,
                        value.optString("name").ifBlank { "未命名播客" },
                        value.optJSONObject("dj")?.optString("nickname").orEmpty(),
                        secure(value.optString("picUrl").takeIf(String::isNotBlank)),
                        value.optInt("programCount", 0),
                    ))
                    else -> Unit
                }
            }
        }
    }

    suspend fun songDetail(songId: Long): SearchSong? = withContext(Dispatchers.IO) {
        val arr = JSONArray().put(JSONObject().put("id", songId))
        val result = eapi("/api/v3/song/detail", JSONObject().put("c", arr.toString()))
        parseSong(result.optJSONArray("songs")?.optJSONObject(0))
    }

    suspend fun collectionSongs(item: MeloXSearchMediaItem): List<SearchSong> = withContext(Dispatchers.IO) {
        val values = when (item.kind) {
            MeloXSearchKind.Albums -> eapi("/api/v1/album/${item.id}", JSONObject()).optJSONArray("songs")
            MeloXSearchKind.Artists -> eapi("/api/v1/artist/${item.id}", JSONObject()).optJSONArray("hotSongs")
            MeloXSearchKind.Podcasts -> {
                val response = eapi(
                    "/api/dj/program/byradio",
                    JSONObject().put("radioId", item.id).put("limit", 100).put("offset", 0).put("asc", false),
                )
                val programs = response.optJSONArray("programs") ?: JSONArray()
                return@withContext buildList {
                    for (i in 0 until programs.length()) {
                        val program = programs.optJSONObject(i) ?: continue
                        parseSong(program.optJSONObject("mainSong"))?.let(::add)
                    }
                }
            }
            else -> JSONArray()
        } ?: JSONArray()
        buildList {
            for (i in 0 until values.length()) parseSong(values.optJSONObject(i))?.let(::add)
        }
    }

    private fun parseSong(value: JSONObject?): SearchSong? {
        value ?: return null
        val id = value.optLong("id", -1L)
        if (id <= 0L) return null
        val artistArray = value.optJSONArray("ar") ?: value.optJSONArray("artists") ?: JSONArray()
        val artists = buildList {
            for (i in 0 until artistArray.length()) artistArray.optJSONObject(i)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
        }.joinToString(" / ")
        val album = value.optJSONObject("al") ?: value.optJSONObject("album")
        return SearchSong(
            id = id,
            name = value.optString("name").ifBlank { "未知歌曲" },
            artists = artists.ifBlank { "未知歌手" },
            album = album?.optString("name").orEmpty(),
            artworkUrl = secure(album?.optString("picUrl")?.takeIf(String::isNotBlank) ?: album?.optString("blurPicUrl")?.takeIf(String::isNotBlank)),
            durationMs = value.optLong("dt", value.optLong("duration", 0L)).coerceAtLeast(0L),
        )
    }

    private fun eapi(uri: String, data: JSONObject): JSONObject {
        val now = System.currentTimeMillis()
        val cookieHeader = cookieProvider()
        val cookies = NeteaseSessionStore.parseCookie(cookieHeader)
        val authenticated = NeteaseSessionStore.containsMusicU(cookieHeader)
        val header = if (authenticated) authenticatedHeader(cookies, now) else JSONObject()
            .put("os", "ios").put("appver", "9.0.90").put("osver", "18.0")
            .put("buildver", (now / 1000L).toString()).put("channel", "distribution")
            .put("requestId", "${now}_0000").put("__csrf", "")
        val requestData = JSONObject(data.toString()).put("header", header).put("e_r", false)
        val json = requestData.toString()
        val digest = md5Hex("nobody${uri}use${json}md5forencrypt")
        val encrypted = "$uri-36cd479b6b5-$json-36cd479b6b5-$digest"
        val params = aes(encrypted.toByteArray(Charsets.UTF_8), "e82ckenh8dichen8".toByteArray()).toHex()
        val builder = Request.Builder()
            .url("https://interface.music.163.com${uri.replace("/api/", "/eapi/")}")
            .header("Accept", "*/*")
            .header("User-Agent", if (authenticated) "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)" else "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148")
        if (authenticated) builder.header("Cookie", encodedCookie(header))
        val request = builder.post(FormBody.Builder().add("params", params).build()).build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw IOException("网易云请求失败：HTTP ${response.code}")
            if (body.isBlank()) throw IOException("网易云返回了空响应")
            val result = JSONObject(body)
            val code = result.optInt("code", response.code)
            if (code !in 200..299) throw IOException(result.optString("message").ifBlank { result.optString("msg") }.ifBlank { "请求失败（$code）" })
            return result
        }
    }

    private fun authenticatedHeader(cookies: Map<String, String>, now: Long) = JSONObject()
        .put("osver", cookies["osver"] ?: "16.2")
        .put("deviceId", cookies["deviceId"] ?: syntheticDeviceId)
        .put("os", cookies["os"] ?: "iPhone OS")
        .put("appver", cookies["appver"] ?: "9.0.90")
        .put("versioncode", cookies["versioncode"] ?: "140")
        .put("mobilename", cookies["mobilename"] ?: "")
        .put("buildver", cookies["buildver"] ?: (now / 1000L).toString())
        .put("resolution", cookies["resolution"] ?: "1170x2532")
        .put("__csrf", cookies["__csrf"] ?: "")
        .put("channel", cookies["channel"] ?: "distribution")
        .put("requestId", "${now}_${randomDigits(4)}")
        .apply { cookies["MUSIC_U"]?.takeIf(String::isNotBlank)?.let { put("MUSIC_U", it) } }

    private fun encodedCookie(values: JSONObject): String = buildList {
        val it = values.keys(); while (it.hasNext()) add(it.next())
    }.sorted().joinToString("; ") { key -> "${enc(key)}=${enc(values.optString(key))}" }

    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    private fun secure(url: String?): String? = url?.let { if (it.startsWith("http://", true)) "https://${it.substringAfter("://")}" else it }
    private fun randomHex(n: Int): String { val b = ByteArray(n); SecureRandom().nextBytes(b); return b.joinToString("") { "%02x".format(it) } }
    private fun randomDigits(n: Int) = buildString(n) { repeat(n) { append(('0'.code + SecureRandom().nextInt(10)).toChar()) } }
    private fun md5Hex(v: String) = MessageDigest.getInstance("MD5").digest(v.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun aes(data: ByteArray, key: ByteArray): ByteArray = Cipher.getInstance("AES/ECB/PKCS5Padding").run { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")); doFinal(data) }
    private fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }
}
