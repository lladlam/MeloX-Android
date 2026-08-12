package com.lladlam.melox.core.network

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
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

data class MeloXPodcastHost(
    val id: Long = 0L,
    val nickname: String = "网易云主播",
    val avatarUrl: String? = null,
)

data class MeloXPodcast(
    val id: Long,
    val name: String,
    val artworkUrl: String? = null,
    val description: String? = null,
    val recommendation: String? = null,
    val categoryId: Long? = null,
    val category: String? = null,
    val programCount: Int = 0,
    val subscriberCount: Long = 0L,
    val playCount: Long = 0L,
    val host: MeloXPodcastHost? = null,
    val subscribed: Boolean = false,
)

data class MeloXPodcastCategory(
    val id: Long,
    val name: String,
    val artworkUrl: String? = null,
)

data class MeloXPodcastProgram(
    val id: Long,
    val name: String,
    val artworkUrl: String? = null,
    val description: String? = null,
    val createTimeMs: Long? = null,
    val durationMs: Long = 0L,
    val listenerCount: Long = 0L,
    val likedCount: Long = 0L,
    val commentCount: Long = 0L,
    val radioId: Long,
    val radioName: String,
    val host: MeloXPodcastHost? = null,
    val playbackSong: SearchSong? = null,
)

data class MeloXPodcastPage<T>(
    val values: List<T>,
    val hasMore: Boolean = false,
    val totalCount: Int = values.size,
)

data class MeloXCloudSong(
    val id: Long,
    val song: SearchSong,
    val fileSize: Long = 0L,
    val bitrate: Int = 0,
    val addTimeMs: Long = 0L,
)

data class MeloXCloudPage(
    val values: List<MeloXCloudSong>,
    val totalCount: Int,
    val usedBytes: Long,
    val maxBytes: Long,
    val hasMore: Boolean,
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

    suspend fun uploadCloudSong(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val cookie = cookieProvider()
        if (!NeteaseSessionStore.containsMusicU(cookie)) throw IOException("请先登录网易云音乐")
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.takeIf(String::isNotBlank) ?: "music.mp3"
        val extension = displayName.substringAfterLast('.', "mp3").lowercase().ifBlank { "mp3" }
        val stem = displayName.substringBeforeLast('.').filterNot(Char::isWhitespace).replace('.', '_').ifBlank { "music" }
        val temporary = java.io.File.createTempFile("melox-cloud-", ".$extension", context.cacheDir)
        try {
            resolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
                ?: throw IOException("无法读取所选音频文件")
            if (temporary.length() <= 0L) throw IOException("所选音频文件为空")
            val md5 = MessageDigest.getInstance("MD5").let { digest ->
                temporary.inputStream().use { input ->
                    val buffer = ByteArray(1_048_576)
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            val metadata = MediaMetadataRetriever()
            val (title, artist, album) = try {
                metadata.setDataSource(context, uri)
                Triple(
                    metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank) ?: displayName.substringBeforeLast('.'),
                    metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank) ?: "未知艺术家",
                    metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf(String::isNotBlank) ?: "未知专辑",
                )
            } finally {
                runCatching { metadata.release() }
            }
            val bitrate = 999_000
            val check = eapi(
                "/api/cloud/upload/check",
                JSONObject().put("bitrate", bitrate.toString()).put("ext", "").put("length", temporary.length())
                    .put("md5", md5).put("songId", "0").put("version", 1),
            )
            val songId = check.optString("songId").toLongOrNull() ?: check.optLong("songId", 0L)
            val metadataToken = eapi(
                "/api/nos/token/alloc",
                JSONObject().put("bucket", "").put("ext", extension).put("filename", stem).put("local", false)
                    .put("nos_product", 3).put("type", "audio").put("md5", md5),
            ).optJSONObject("result") ?: throw IOException("网易云未返回云盘资源令牌")
            if (check.optBoolean("needUpload", false)) {
                val bucket = "jd-musicrep-privatecloud-audio-public"
                val uploadToken = eapi(
                    "/api/nos/token/alloc",
                    JSONObject().put("bucket", bucket).put("ext", extension).put("filename", stem).put("local", false)
                        .put("nos_product", 3).put("type", "audio").put("md5", md5),
                ).optJSONObject("result") ?: throw IOException("网易云未返回上传令牌")
                val lbsRequest = Request.Builder().url("https://wanproxy.127.net/lbs?version=1.0&bucketname=$bucket").get().build()
                val uploadHost = httpClient.newCall(lbsRequest).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("云盘上传节点请求失败：HTTP ${response.code}")
                    val hosts = JSONObject(response.body.string()).optJSONArray("upload") ?: JSONArray()
                    hosts.optString(0).takeIf(String::isNotBlank) ?: throw IOException("网易云没有返回可用上传节点")
                }
                val objectKey = uploadToken.optString("objectKey")
                val encodedKey = URLEncoder.encode(objectKey, Charsets.UTF_8.name()).replace("+", "%20")
                val uploadUrl = "${uploadHost.trimEnd('/')}/$bucket/$encodedKey?offset=0&complete=true&version=1.0"
                val uploadRequest = Request.Builder().url(uploadUrl)
                    .header("x-nos-token", uploadToken.optString("token"))
                    .header("Content-MD5", md5)
                    .post(temporary.asRequestBody("audio/mpeg".toMediaType()))
                    .build()
                httpClient.newCall(uploadRequest).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("音频上传失败：HTTP ${response.code}")
                }
            }
            val info = eapi(
                "/api/upload/cloud/info/v2",
                JSONObject().put("md5", md5).put("songid", songId).put("filename", displayName)
                    .put("song", title).put("album", album).put("artist", artist).put("bitrate", bitrate.toString())
                    .put("resourceId", metadataToken.optString("resourceId")),
            )
            val publishedSongId = info.optString("songId").toLongOrNull() ?: info.optLong("songId", songId)
            eapi("/api/cloud/pub/v2", JSONObject().put("songid", publishedSongId))
        } finally {
            runCatching { temporary.delete() }
        }
    }

    suspend fun songDetail(songId: Long): SearchSong? = withContext(Dispatchers.IO) {
        val arr = JSONArray().put(JSONObject().put("id", songId))
        val result = eapi("/api/v3/song/detail", JSONObject().put("c", arr.toString()))
        parseSong(result.optJSONArray("songs")?.optJSONObject(0))
    }

    suspend fun cloudSongs(limit: Int = 200, offset: Int = 0): MeloXCloudPage = withContext(Dispatchers.IO) {
        val response = eapi(
            "/api/v1/cloud/get",
            JSONObject().put("limit", limit.coerceIn(1, 200)).put("offset", offset.coerceAtLeast(0)),
        )
        val data = response.optJSONArray("data") ?: JSONArray()
        val values = buildList {
            for (index in 0 until data.length()) {
                val value = data.optJSONObject(index) ?: continue
                val simple = value.optJSONObject("simpleSong")
                val parsed = parseSong(simple) ?: continue
                add(
                    MeloXCloudSong(
                        id = value.optLong("songId", parsed.id),
                        song = parsed,
                        fileSize = value.optLong("fileSize", 0L),
                        bitrate = value.optInt("bitrate", 0),
                        addTimeMs = value.optLong("addTime", 0L),
                    ),
                )
            }
        }
        val total = response.optInt("count", offset + values.size)
        MeloXCloudPage(
            values = values,
            totalCount = total,
            usedBytes = response.optLong("size", 0L),
            maxBytes = response.optLong("maxSize", 0L),
            hasMore = response.optBoolean("hasMore", offset + values.size < total),
        )
    }

    suspend fun deleteCloudSong(songId: Long) = withContext(Dispatchers.IO) {
        eapi("/api/cloud/del", JSONObject().put("songIds", JSONArray().put(songId)))
        Unit
    }

    suspend fun podcastCategories(): List<MeloXPodcastCategory> = withContext(Dispatchers.IO) {
        val values = eapi("/api/djradio/category/get", JSONObject()).optJSONArray("categories") ?: JSONArray()
        buildList {
            for (index in 0 until values.length()) {
                val value = values.optJSONObject(index) ?: continue
                val id = value.optLong("id", -1L)
                if (id <= 0L) continue
                add(
                    MeloXPodcastCategory(
                        id = id,
                        name = value.optString("name").ifBlank { "播客" },
                        artworkUrl = secure(
                            value.optString("pic96x96Url").takeIf(String::isNotBlank)
                                ?: value.optString("pic56x56Url").takeIf(String::isNotBlank),
                        ),
                    ),
                )
            }
        }
    }

    suspend fun featuredPodcasts(): List<MeloXPodcast> = withContext(Dispatchers.IO) {
        parsePodcasts(eapi("/api/djradio/recommend/v1", JSONObject()).optJSONArray("djRadios"))
    }

    suspend fun personalizedPodcasts(limit: Int = 12): List<MeloXPodcast> = withContext(Dispatchers.IO) {
        parsePodcasts(
            eapi("/api/djradio/personalize/rcmd", JSONObject().put("limit", limit.coerceIn(1, 50)))
                .optJSONArray("data"),
        )
    }

    suspend fun podcastsByCategory(
        categoryId: Long,
        offset: Int = 0,
        limit: Int = 30,
    ): MeloXPodcastPage<MeloXPodcast> = withContext(Dispatchers.IO) {
        val response = eapi(
            "/api/djradio/hot",
            JSONObject()
                .put("cateId", categoryId)
                .put("limit", limit.coerceIn(1, 50))
                .put("offset", offset.coerceAtLeast(0)),
        )
        val values = parsePodcasts(response.optJSONArray("djRadios"))
        val total = response.optInt("count", offset + values.size)
        MeloXPodcastPage(
            values = values,
            hasMore = if (response.has("hasMore")) response.optBoolean("hasMore") else offset + values.size < total,
            totalCount = total,
        )
    }

    suspend fun podcastDetail(id: Long): MeloXPodcast? = withContext(Dispatchers.IO) {
        val response = eapi("/api/djradio/v2/get", JSONObject().put("id", id))
        parsePodcast(response.optJSONObject("data") ?: response.optJSONObject("djRadio"))
    }

    suspend fun podcastPrograms(
        radioId: Long,
        offset: Int = 0,
        limit: Int = 30,
        ascending: Boolean = false,
    ): MeloXPodcastPage<MeloXPodcastProgram> = withContext(Dispatchers.IO) {
        val response = eapi(
            "/api/dj/program/byradio",
            JSONObject()
                .put("radioId", radioId)
                .put("limit", limit.coerceIn(1, 50))
                .put("offset", offset.coerceAtLeast(0))
                .put("asc", ascending),
        )
        val source = response.optJSONArray("programs") ?: JSONArray()
        val values = buildList {
            for (index in 0 until source.length()) parsePodcastProgram(source.optJSONObject(index))?.let(::add)
        }
        val total = response.optInt("count", offset + values.size)
        MeloXPodcastPage(
            values = values,
            hasMore = if (response.has("more")) response.optBoolean("more") else offset + values.size < total,
            totalCount = total,
        )
    }

    suspend fun subscribedPodcasts(offset: Int = 0, limit: Int = 50): MeloXPodcastPage<MeloXPodcast> =
        withContext(Dispatchers.IO) {
            val response = eapi(
                "/api/djradio/get/subed",
                JSONObject()
                    .put("limit", limit.coerceIn(1, 100))
                    .put("offset", offset.coerceAtLeast(0))
                    .put("total", true),
            )
            val values = parsePodcasts(response.optJSONArray("djRadios")).map { it.copy(subscribed = true) }
            val total = response.optInt("count", offset + values.size)
            MeloXPodcastPage(
                values = values,
                hasMore = if (response.has("hasMore")) response.optBoolean("hasMore") else offset + values.size < total,
                totalCount = total,
            )
        }

    suspend fun setPodcastSubscribed(id: Long, subscribed: Boolean) = withContext(Dispatchers.IO) {
        eapi(if (subscribed) "/api/djradio/sub" else "/api/djradio/unsub", JSONObject().put("id", id))
        Unit
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

    private fun parsePodcasts(values: JSONArray?): List<MeloXPodcast> {
        val source = values ?: JSONArray()
        return buildList {
            for (index in 0 until source.length()) parsePodcast(source.optJSONObject(index))?.let(::add)
        }
    }

    private fun parsePodcast(value: JSONObject?): MeloXPodcast? {
        value ?: return null
        val id = value.optLong("id", -1L)
        if (id <= 0L) return null
        val host = parsePodcastHost(value.optJSONObject("dj"))
        return MeloXPodcast(
            id = id,
            name = value.optString("name").ifBlank { "未知播客" },
            artworkUrl = secure(value.optString("picUrl").takeIf(String::isNotBlank)),
            description = value.optString("desc").takeIf(String::isNotBlank),
            recommendation = value.optString("rcmdText").takeIf(String::isNotBlank)
                ?: value.optString("rcmdtext").takeIf(String::isNotBlank),
            categoryId = value.optLong("categoryId", 0L).takeIf { it > 0L },
            category = value.optString("category").takeIf(String::isNotBlank),
            programCount = value.optInt("programCount", 0),
            subscriberCount = value.optLong("subCount", 0L),
            playCount = value.optLong("playCount", 0L),
            host = host,
            subscribed = value.optBoolean("subed", false),
        )
    }

    private fun parsePodcastHost(value: JSONObject?): MeloXPodcastHost? {
        value ?: return null
        return MeloXPodcastHost(
            id = value.optLong("userId", 0L),
            nickname = value.optString("nickname").ifBlank { "网易云主播" },
            avatarUrl = secure(value.optString("avatarUrl").takeIf(String::isNotBlank)),
        )
    }

    private fun parsePodcastProgram(value: JSONObject?): MeloXPodcastProgram? {
        value ?: return null
        val id = value.optLong("id", -1L)
        if (id <= 0L) return null
        val radio = value.optJSONObject("radio") ?: JSONObject()
        val host = parsePodcastHost(value.optJSONObject("dj"))
        val radioName = radio.optString("name").ifBlank { "未知播客" }
        val cover = secure(
            value.optString("coverUrl").takeIf(String::isNotBlank)
                ?: radio.optString("picUrl").takeIf(String::isNotBlank),
        )
        val parsedMainSong = parseSong(value.optJSONObject("mainSong"))
        val resolvedDuration = value.optLong("duration", 0L).takeIf { it > 0L }
            ?: parsedMainSong?.durationMs.orEmptyDuration()
        val mainSong = parsedMainSong?.copy(
            name = value.optString("name").ifBlank { "未知节目" },
            artists = host?.nickname ?: radioName,
            album = radioName,
            artworkUrl = cover,
            durationMs = resolvedDuration,
        )
        return MeloXPodcastProgram(
            id = id,
            name = value.optString("name").ifBlank { "未知节目" },
            artworkUrl = cover,
            description = value.optString("description").takeIf(String::isNotBlank),
            createTimeMs = value.optLong("createTime", 0L).takeIf { it > 0L },
            durationMs = resolvedDuration,
            listenerCount = value.optLong("listenerCount", 0L),
            likedCount = value.optLong("likedCount", 0L),
            commentCount = value.optLong("commentCount", 0L),
            radioId = radio.optLong("id", 0L),
            radioName = radioName,
            host = host,
            playbackSong = mainSong,
        )
    }

    private fun Long?.orEmptyDuration(): Long = this ?: 0L

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
