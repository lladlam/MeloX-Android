package com.lladlam.melox.core.network

import com.lladlam.melox.core.account.NeteaseAccountProfile
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.lyrics.NeteaseLyricParser
import com.lladlam.melox.core.lyrics.withoutPlaceholderLyrics
import com.lladlam.melox.core.lyrics.LrcLyricsParser
import com.lladlam.melox.core.lyrics.LyricSource
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.audioQualityForBitrate
import com.lladlam.melox.core.remoteconfig.MeloXHotRules
import com.lladlam.melox.core.remoteconfig.MeloXHotScripts
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class NeteaseSearchClient(
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
    private val cookieProvider: () -> String = { "" },
) {
    private val syntheticDeviceId: String = randomHex(26).uppercase()

    suspend fun accountProfile(
        cookieHeader: String = cookieProvider(),
    ): NeteaseAccountProfile = withContext(Dispatchers.IO) {
        if (!NeteaseSessionStore.containsMusicU(cookieHeader)) {
            throw IOException("请先登录网易云音乐")
        }

        val response = eapi(
            uri = "/api/w/nuser/account/get",
            data = JSONObject(),
            authenticated = true,
            cookieHeaderOverride = cookieHeader,
        )
        val profile = response.optJSONObject("profile")
        if (profile == null) {
            NeteaseQrDiag.log(
                "accountProfile",
                "profile=null keys=${response.keys().asSequence().toList()} " +
                    "code=${response.optInt("code", -1)} cookieKeys=${NeteaseQrDiag.cookieKeys(cookieHeader)}",
            )
            throw IOException("网易云登录状态无效")
        }
        val userId = profile.optLong("userId", -1L)
        if (userId <= 0L) throw IOException("网易云返回了无效的用户信息")

        NeteaseAccountProfile(
            userId = userId,
            nickname = profile.optString("nickname").ifBlank { "网易云用户" },
            avatarUrl = profile.optString("avatarUrl")
                .takeIf(String::isNotBlank)
                ?.let(::secureUrl),
            backgroundUrl = profile.optString("backgroundUrl")
                .takeIf(String::isNotBlank)
                ?.let(::secureUrl),
            signature = profile.optString("signature").takeIf(String::isNotBlank),
        )
    }

    suspend fun searchSongs(
        keywords: String,
        limit: Int = 30,
    ): List<SearchSong> = withContext(Dispatchers.IO) {
        // Signed rules strip known literal suffixes first, then the signed
        // `search.keywords` hook may rewrite whatever is left. Both are optional
        // and fall back to the caller's keyword.
        val stripped = MeloXHotRules.stripTitleSuffixes(keywords.trim())
        val query = MeloXHotScripts.run(
            "search.keywords",
            JSONObject().put("keywords", stripped),
        )?.optString("keywords")?.trim()?.takeIf(String::isNotEmpty)
            ?: stripped
        if (query.isEmpty()) return@withContext emptyList()

        val payload = JSONObject()
            .put("s", query)
            .put("type", 1)
            .put("limit", limit.coerceIn(1, 50))
            .put("offset", 0)

        val response = eapi(
            uri = "/api/search/get",
            data = payload,
        )

        val result = response.optJSONObject("result") ?: return@withContext emptyList()
        val songs = result.optJSONArray("songs") ?: JSONArray()
        buildList {
            for (index in 0 until songs.length()) {
                val song = songs.optJSONObject(index) ?: continue
                val id = song.optLong("id", -1L)
                if (id <= 0L) continue

                val artistsArray = song.optJSONArray("ar")
                    ?: song.optJSONArray("artists")
                    ?: JSONArray()
                val artists = buildList {
                    for (artistIndex in 0 until artistsArray.length()) {
                        artistsArray.optJSONObject(artistIndex)
                            ?.optString("name")
                            ?.takeIf(String::isNotBlank)
                            ?.let(::add)
                    }
                }.joinToString(" / ")

                val albumObject = song.optJSONObject("al")
                    ?: song.optJSONObject("album")
                val album = albumObject?.optString("name").orEmpty()
                val artwork = artworkFromAlbum(albumObject)

                val quality = neteaseSearchQuality(song)
                add(
                    SearchSong(
                        id = id,
                        name = song.optString("name", "未知歌曲"),
                        artists = artists.ifBlank { "未知歌手" },
                        album = album,
                        artworkUrl = artwork,
                        durationMs = neteaseSearchDurationMs(song),
                        bestQuality = quality?.first,
                        bestBitrate = quality?.second,
                    ),
                )
            }
        }
    }

    /**
     * NetEase search embeds one object per quality tier (`hr`, `sq`, `h`, `m`,
     * `l`). Some accounts/responses omit all of them, so no hint is better
     * than claiming a tier the item may not actually have.
     */
    private fun neteaseSearchQuality(song: JSONObject): Pair<AudioQualityTier, Int?>? {
        val tiers = listOf(
            "hr" to AudioQualityTier.HiResolution,
            "sq" to AudioQualityTier.Lossless,
            "h" to AudioQualityTier.High,
            "m" to AudioQualityTier.Standard,
            "l" to AudioQualityTier.Standard,
        )
        for ((key, tier) in tiers) {
            val value = song.optJSONObject(key) ?: continue
            val bitrate = value.optInt("br", value.optInt("bitrate", 0)).takeIf { it > 0 }
            if (bitrate != null) return tier to bitrate
        }
        val embedded = song.optJSONObject("quality") ?: return null
        val bitrate = embedded.optInt("br", embedded.optInt("bitrate", 0)).takeIf { it > 0 }
        return audioQualityForBitrate(bitrate)?.let { it to bitrate }
    }

    suspend fun ensureArtwork(songs: List<SearchSong>): List<SearchSong> =
        withContext(Dispatchers.IO) {
            if (songs.isEmpty()) return@withContext songs

            val missingIds = songs
                .asSequence()
                .filter { it.artworkUrl.isNullOrBlank() }
                .map { it.id }
                .filter { it > 0L }
                .distinct()
                .toList()

            if (missingIds.isEmpty()) return@withContext songs

            runCatching {
                val songDescriptors = JSONArray().apply {
                    missingIds.forEach { id -> put(JSONObject().put("id", id)) }
                }
                val response = eapi(
                    uri = "/api/v3/song/detail",
                    data = JSONObject().put("c", songDescriptors.toString()),
                )
                val details = response.optJSONArray("songs") ?: return@runCatching songs
                val artworkById = buildMap<Long, String> {
                    for (index in 0 until details.length()) {
                        val detail = details.optJSONObject(index) ?: continue
                        val id = detail.optLong("id", -1L)
                        if (id <= 0L) continue
                        val albumObject = detail.optJSONObject("al")
                            ?: detail.optJSONObject("album")
                        artworkFromAlbum(albumObject)?.let { artwork -> put(id, artwork) }
                    }
                }

                songs.map { song ->
                    if (!song.artworkUrl.isNullOrBlank()) song
                    else artworkById[song.id]
                        ?.let { artwork -> song.copy(artworkUrl = artwork) }
                        ?: song
                }
            }.getOrDefault(songs)
        }

    suspend fun ensureArtwork(song: SearchSong): SearchSong =
        ensureArtwork(listOf(song)).firstOrNull() ?: song

    suspend fun lyrics(songId: Long): LyricsDocument = withContext(Dispatchers.IO) {
        val primary = try {
            parseLyrics(
                eapi(
                    uri = "/api/song/lyric/v1",
                    data = JSONObject()
                        .put("id", songId)
                        .put("cp", false)
                        .put("tv", 0)
                        .put("lv", 0)
                        .put("rv", 0)
                        .put("kv", 0)
                        .put("yv", 0)
                        .put("ytv", 0)
                        .put("yrv", 0),
                ),
            ).withoutPlaceholderLyrics()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        }
        if (primary != null && primary.lines.isNotEmpty()) return@withContext primary

        // Account/client-specific responses can report "instrumental" for songs
        // whose public lyric endpoint still has LRC. Do not forward account cookies.
        try {
            val url = okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host("music.163.com")
                .addPathSegments("api/song/lyric")
                .addQueryParameter("id", songId.toString())
                .apply {
                    listOf("lv", "tv", "rv", "kv", "yv", "ytv", "yrv").forEach {
                        addQueryParameter(it, "-1")
                    }
                }
                .build()
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://music.163.com/")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("网易云公开歌词请求失败：HTTP ${response.code}")
                val body = JSONObject(response.body.string())
                if (body.optInt("code") != 200) throw IOException("网易云公开歌词错误码 ${body.optInt("code")}")
                parseLyrics(body).withoutPlaceholderLyrics()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            primary ?: throw error
        }
    }

    private fun parseLyrics(response: JSONObject): LyricsDocument {
        val timed = NeteaseLyricParser.parse(
            yrc = response.optJSONObject("yrc")?.optString("lyric").orEmpty(),
            lrc = response.optJSONObject("lrc")?.optString("lyric").orEmpty(),
            translatedYrc = response.optJSONObject("ytlrc")?.optString("lyric").orEmpty(),
            translatedLrc = response.optJSONObject("tlyric")?.optString("lyric").orEmpty(),
            romanizedYrc = response.optJSONObject("yromalrc")?.optString("lyric").orEmpty(),
            romanizedLrc = response.optJSONObject("romalrc")?.optString("lyric").orEmpty(),
        )
        return (if (timed.lines.isNotEmpty()) timed else
            LrcLyricsParser.parse(response.optJSONObject("lrc")?.optString("lyric").orEmpty()))
            .copy(source = LyricSource.Netease)
    }

    suspend fun playbackUrl(songId: Long): String = withContext(Dispatchers.IO) {
        playbackUrlBlocking(songId)
    }

    internal fun playbackUrlBlocking(songId: Long): String {
        val currentCookie = cookieProvider()
        val loggedIn = NeteaseSessionStore.containsMusicU(currentCookie)

        try {
            val payload = JSONObject()
                .put("ids", "[$songId]")
                .put("level", "standard")
                .put("encodeType", "flac")

            val response = eapi(
                uri = "/api/song/enhance/player/url/v1",
                data = payload,
                authenticated = loggedIn,
                cookieHeaderOverride = currentCookie.takeIf(String::isNotBlank),
            )

            val sources = response.optJSONArray("data") ?: JSONArray()
            for (index in 0 until sources.length()) {
                val source = sources.optJSONObject(index) ?: continue
                if (source.optLong("id", -1L) != songId) continue
                val rawUrl = source.optString("url").takeIf(String::isNotBlank) ?: continue
                return secureUrl(rawUrl)
            }

            if (loggedIn) {
                throw IOException("网易云登录态未返回可播放链接，可能是账号权限或版权限制")
            }
        } catch (error: Exception) {
            // Once a real MUSIC_U session exists we must not silently fall back to
            // an anonymous outer-url endpoint: doing so discards VIP/cloud/region
            // permissions and makes a successful login effectively useless.
            if (loggedIn) {
                if (error is IOException) throw error
                throw IOException("登录态播放链接获取失败", error)
            }
        }

        // Anonymous compatibility path only. Logged-in playback always goes through
        // the authenticated EAPI path above.
        return "https://music.163.com/song/media/outer/url?id=$songId"
    }

    private fun artworkFromAlbum(albumObject: JSONObject?): String? =
        albumObject
            ?.optString("picUrl")
            ?.takeIf(String::isNotBlank)
            ?.let(::secureUrl)
            ?: albumObject
                ?.optString("blurPicUrl")
                ?.takeIf(String::isNotBlank)
                ?.let(::secureUrl)

    private fun secureUrl(url: String): String =
        if (url.startsWith("http://", ignoreCase = true)) {
            "https://${url.substringAfter("://")}" 
        } else {
            url
        }

    private fun eapi(
        uri: String,
        data: JSONObject,
        authenticated: Boolean? = null,
        cookieHeaderOverride: String? = null,
    ): JSONObject {
        val timestampMillis = System.currentTimeMillis()
        val cookieHeader = cookieHeaderOverride ?: cookieProvider()
        val cookies = NeteaseSessionStore.parseCookie(cookieHeader)
        val useAuthenticatedSession = authenticated
            ?: NeteaseSessionStore.containsMusicU(cookieHeader)

        val header = if (useAuthenticatedSession) {
            authenticatedEapiHeader(cookies, timestampMillis)
        } else {
            JSONObject()
                .put("os", "ios")
                .put("appver", "9.0.90")
                .put("osver", "18.0")
                .put("buildver", (timestampMillis / 1_000L).toString())
                .put("channel", "distribution")
                .put("requestId", "${timestampMillis}_0000")
                .put("__csrf", "")
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

        val path = uri.replace("/api/", "/eapi/")
        val requestBuilder = Request.Builder()
            .url("https://interface.music.163.com$path")
            .header(
                "User-Agent",
                if (useAuthenticatedSession) {
                    "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"
                } else {
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) " +
                        "AppleWebKit/605.1.15 Mobile/15E148"
                },
            )
            .header("Accept", "*/*")

        if (useAuthenticatedSession) {
            // MeloX's iOS/watch EAPI client serializes the EAPI header as the
            // request Cookie header. MUSIC_U is part of that header when logged in.
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

            val jsonObject = JSONObject(body)
            val code = jsonObject.optInt("code", response.code)
            if (code !in 200..299) {
                val message = jsonObject.optString("message")
                    .ifBlank { jsonObject.optString("msg") }
                    .ifBlank { "请求失败" }
                NeteaseQrDiag.log("eapi", "uri=$uri code=$code message=$message")
                throw IOException("网易云请求失败（$code）：$message")
            }
            return jsonObject
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

        cookies["MUSIC_U"]?.takeIf(String::isNotBlank)?.let { musicU ->
            header.put("MUSIC_U", musicU)
        }
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
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun randomDigits(length: Int): String =
        buildString(length) {
            repeat(length) { append(('0'.code + SecureRandom().nextInt(10)).toChar()) }
        }

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun aesEcbEncrypt(
        data: ByteArray,
        key: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    private fun ByteArray.toHexUppercase(): String =
        joinToString("") { byte -> "%02X".format(byte) }
}

internal fun neteaseSearchDurationMs(song: JSONObject): Long =
    song.optLong("dt", song.optLong("duration", 0L)).coerceAtLeast(0L)
