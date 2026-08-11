package com.lladlam.melox.core.audio

import com.lladlam.melox.core.account.NeteaseSessionStore
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

data class NeteasePlaybackSource(
    val url: String,
    val bitrate: Int?,
    val format: String?,
    val quality: MusicQuality?,
)

/**
 * Android port of the quality-sensitive parts of MeloX NeteaseAPI.
 *
 * The fallback order, level names, FLAC encodeType and sky/c51 handling mirror
 * the Swift implementation. A real MUSIC_U session is preserved for every
 * candidate request; logged-in playback never falls back to anonymous outer-url.
 */
class NeteaseQualityClient(
    private val cookieProvider: () -> String = { "" },
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val syntheticDeviceId: String = randomHex(26).uppercase()

    suspend fun audioAvailability(songId: Long): SongAudioAvailability =
        withContext(Dispatchers.IO) { audioAvailabilityBlocking(songId) }

    fun audioAvailabilityBlocking(songId: Long): SongAudioAvailability {
        if (songId <= 0L) return SongAudioAvailability.Unknown
        return runCatching {
            val songs = JSONArray().put(JSONObject().put("id", songId))
            val response = eapi(
                uri = "/api/v3/song/detail",
                data = JSONObject().put("c", songs.toString()),
            )
            val song = response.optJSONArray("songs")?.optJSONObject(0)
                ?: return@runCatching SongAudioAvailability.Unknown
            parseAvailability(song)
        }.getOrDefault(SongAudioAvailability.Unknown)
    }

    fun playbackSourceBlocking(
        songId: Long,
        requestedQuality: MusicQuality,
    ): NeteasePlaybackSource {
        val currentCookie = cookieProvider()
        val loggedIn = NeteaseSessionStore.containsMusicU(currentCookie)
        val availability = audioAvailabilityBlocking(songId)
        var lastError: Throwable? = null

        for (candidate in requestedQuality.playbackCandidates(availability)) {
            try {
                val payload = JSONObject()
                    .put("ids", "[$songId]")
                    .put("level", candidate.apiLevel)
                    .put("encodeType", "flac")
                if (candidate.requiresImmersiveType) {
                    payload.put("immerseType", "c51")
                }

                val response = eapi(
                    uri = "/api/song/enhance/player/url/v1",
                    data = payload,
                    authenticated = loggedIn,
                    cookieHeaderOverride = currentCookie.takeIf(String::isNotBlank),
                )
                val sources = response.optJSONArray("data") ?: JSONArray()
                val source = (0 until sources.length())
                    .asSequence()
                    .mapNotNull(sources::optJSONObject)
                    .firstOrNull { it.optLong("id", -1L) == songId }
                    ?: throw IOException("no source for ${candidate.apiLevel}")
                val rawUrl = source.optString("url").takeIf(String::isNotBlank)
                    ?: throw IOException("no URL for ${candidate.apiLevel}")

                val actual = MusicQuality.fromApiLevel(
                    source.optString("level").takeIf(String::isNotBlank),
                ) ?: candidate

                MusicQualityRuntime.recordActual(songId, actual)
                return NeteasePlaybackSource(
                    url = secureUrl(rawUrl),
                    bitrate = source.optInt("br").takeIf { it > 0 },
                    format = source.optString("type").takeIf(String::isNotBlank),
                    quality = actual,
                )
            } catch (error: Throwable) {
                lastError = error
            }
        }

        if (loggedIn) {
            throw IOException(
                "网易云登录态未返回可播放的 ${requestedQuality.title} 音源，且 MeloX 降级链路也没有可用资源",
                lastError,
            )
        }

        MusicQualityRuntime.recordActual(songId, MusicQuality.Standard)
        return NeteasePlaybackSource(
            url = "https://music.163.com/song/media/outer/url?id=$songId",
            bitrate = null,
            format = "mp3",
            quality = MusicQuality.Standard,
        )
    }

    fun downloadSourceBlocking(
        songId: Long,
        requestedQuality: MusicQuality,
    ): NeteasePlaybackSource {
        val availability = audioAvailabilityBlocking(songId)
        val loggedIn = NeteaseSessionStore.containsMusicU(cookieProvider())
        var lastError: Throwable? = null
        for (candidate in requestedQuality.playbackCandidates(availability)) {
            try {
                val payload = JSONObject()
                    .put("id", songId)
                    .put("level", candidate.apiLevel)
                if (candidate.requiresImmersiveType) payload.put("immerseType", "c51")
                val response = eapi(
                    uri = "/api/song/enhance/download/url/v1",
                    data = payload,
                    authenticated = loggedIn,
                    cookieHeaderOverride = cookieProvider().takeIf(String::isNotBlank),
                )
                val data = response.optJSONObject("data")
                    ?: response.optJSONArray("data")?.optJSONObject(0)
                    ?: throw IOException("download route returned no source")
                val rawUrl = data.optString("url").takeIf(String::isNotBlank)
                    ?: throw IOException("download route returned no URL")
                val actual = MusicQuality.fromApiLevel(data.optString("level").takeIf(String::isNotBlank)) ?: candidate
                return NeteasePlaybackSource(
                    url = secureUrl(rawUrl),
                    bitrate = data.optInt("br").takeIf { it > 0 },
                    format = data.optString("type").takeIf(String::isNotBlank),
                    quality = actual,
                )
            } catch (error: Throwable) {
                lastError = error
            }
        }
        // Upstream DownloadStore falls back to the ordinary playback source when
        // the account-specific download route is unavailable.
        return runCatching { playbackSourceBlocking(songId, requestedQuality) }
            .getOrElse { throw IOException("无法取得下载音源", lastError ?: it) }
    }

    private fun parseAvailability(song: JSONObject): SongAudioAvailability {
        fun resource(key: String): SongAudioResource? {
            val value = song.optJSONObject(key) ?: return null
            return SongAudioResource(
                bitrate = value.optInt("br").takeIf { it > 0 },
                sampleRate = value.optInt("sr").takeIf { it > 0 },
                size = value.optLong("size").takeIf { it > 0L },
            )
        }

        val keys = listOf("l", "m", "h", "sq", "hr", "je", "sk", "jm")
        return SongAudioAvailability(
            standard = resource("l"),
            medium = resource("m"),
            high = resource("h"),
            lossless = resource("sq"),
            hiResolution = resource("hr"),
            highDefinitionSurround = resource("je"),
            immersiveSurround = resource("sk"),
            ultraClearMaster = resource("jm"),
            isKnown = keys.any(song::has),
        )
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
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"
                },
            )
            .header("Accept", "*/*")

        if (useAuthenticatedSession) {
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
        if (url.startsWith("http://", ignoreCase = true)) {
            "https://${url.substringAfter("://")}"
        } else {
            url
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
