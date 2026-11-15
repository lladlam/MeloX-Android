package com.lladlam.melox.core.provider.kugou

import com.lladlam.melox.core.music.model.AudioQualityTier
import java.security.MessageDigest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * KuGou's public tracker endpoint. It needs no account, dfid or signature and
 * returns a complete stream for genuinely free tracks; paid tracks answer with
 * `status != 1`, so this path cannot bypass membership or copyright limits.
 *
 * This is the anonymous branch used when no KuGou session is stored. A signed-in
 * account keeps using the account-aware `/v5/url` endpoint.
 */
internal class KugouAnonymousPlaybackClient(
    private val httpClient: OkHttpClient,
) {
    fun resolve(
        hash: String,
        quality: AudioQualityTier,
        durationMs: Long?,
    ): KugouAnonymousUrl? {
        val normalized = hash.trim().lowercase()
        if (normalized.isBlank()) return null
        val key = md5Hex(normalized + TrackerSecret)
        for (br in kugouAnonymousQualityCandidates(quality)) {
            val url = "https://trackercdn.kugou.com/i/v2/".toHttpUrl().newBuilder()
                .addQueryParameter("key", key)
                .addQueryParameter("hash", normalized)
                .addQueryParameter("br", br)
                .addQueryParameter("appid", "1005")
                .addQueryParameter("pid", "2")
                .addQueryParameter("cmd", "25")
                .addQueryParameter("behavior", "play")
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", KugouRequestClient.UserAgent)
                .header("Accept", "application/json, text/plain, */*")
                .get()
                .build()
            val json = runCatching {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    JSONObject(response.body.string())
                }
            }.getOrNull() ?: continue
            if (json.optInt("status", 0) != 1) continue
            val raw = json.optJSONArray("url")?.let { array ->
                (0 until array.length())
                    .mapNotNull { index -> array.optString(index).takeIf(String::isNotBlank) }
                    .firstOrNull()
            } ?: json.optString("url").takeIf(String::isNotBlank) ?: continue
            val bitRate = json.optInt("bitRate", 0).takeIf { it > 0 }
            val fileSize = json.optLong("fileSize", 0L).takeIf { it > 0L }
            if (!kugouAnonymousLooksComplete(fileSize, bitRate, durationMs)) continue
            return KugouAnonymousUrl(
                url = if (raw.startsWith("http://", ignoreCase = true)) {
                    "https://${raw.substringAfter("://")}"
                } else {
                    raw
                },
                bitRate = bitRate,
                format = json.optString("extName").takeIf(String::isNotBlank),
            )
        }
        return null
    }

    private companion object {
        const val TrackerSecret = "kgcloudv2"

        fun md5Hex(value: String): String = MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

internal data class KugouAnonymousUrl(
    val url: String,
    val bitRate: Int?,
    val format: String?,
)

/**
 * The public tracker has no "preview" flag. When the response reports both a
 * size and a bitrate, a 30-second trial is far shorter than the requested
 * duration; reject it instead of letting the fallback play a fragment.
 */
internal fun kugouAnonymousLooksComplete(
    fileSize: Long?,
    bitRate: Int?,
    durationMs: Long?,
): Boolean {
    val seconds = durationMs?.takeIf { it > 0L }?.div(1_000.0) ?: return true
    val size = fileSize?.takeIf { it > 0L } ?: return true
    // Fall back to a conservative 128 kbps floor when the tracker omits the
    // bitrate (common for FLAC responses).
    val bytesPerSecond = bitRate?.takeIf { it > 0 }?.div(8.0) ?: 16_000.0
    return size / bytesPerSecond >= seconds * 0.8
}

/** Public tracker has no Hi-Res tier; SQ is the highest anonymous variant. */
internal fun kugouAnonymousQualityCandidates(quality: AudioQualityTier): List<String> = when (quality) {
    AudioQualityTier.Standard -> listOf("128")
    AudioQualityTier.High -> listOf("hq", "128")
    AudioQualityTier.Lossless,
    AudioQualityTier.HiResolution,
    AudioQualityTier.Immersive,
    AudioQualityTier.Master,
    -> listOf("sq", "hq", "128")
}

internal fun kugouAnonymousActualTier(bitRate: Int?, format: String?): AudioQualityTier = when {
    format.equals("flac", ignoreCase = true) -> AudioQualityTier.Lossless
    (bitRate ?: 0) >= 256_000 -> AudioQualityTier.High
    else -> AudioQualityTier.Standard
}
