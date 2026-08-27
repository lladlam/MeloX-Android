package com.lladlam.melox.core.provider.lxuser

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

/** Optional third-party resolver backed by api.chksz.com. */
class ChkszApiClient(
    private val apiKeyProvider: () -> String,
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    fun resolveNetease(songId: Long, quality: AudioQualityTier): String? {
        val key = apiKeyProvider().trim().takeIf(String::isNotBlank) ?: return null
        val url = "https://api.chksz.com/api/163_music".toHttpUrl().newBuilder()
                .addQueryParameter("id", songId.toString())
                .addQueryParameter("level", quality.toChkszLevel())
                .addQueryParameter("type", "json")
                .addQueryParameter("apikey", key)
                .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MeloX/0.4.5")
            .header("Accept", "application/json, text/plain, */*")
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body.string()
            runCatching { findUrl(JSONObject(body)) }.getOrNull()
        }
    }

    fun resolveTrack(track: MusicTrack, quality: AudioQualityTier): String? = when (val metadata = track.providerMetadata) {
        is ProviderTrackMetadata.Netease -> resolveNetease(metadata.numericId, quality)
        is ProviderTrackMetadata.QQMusic -> request("qq_music", mapOf(
            "mid" to metadata.songMid,
            "size" to quality.toChkszSize(),
            "type" to "json",
        ))
        is ProviderTrackMetadata.Kugou -> request("kugou_music", mapOf(
            "id" to metadata.hash,
            "size" to quality.toChkszSize(),
            "type" to "json",
        ))
        else -> null
    }

    private fun request(endpoint: String, params: Map<String, String>): String? {
        val key = apiKeyProvider().trim().takeIf(String::isNotBlank) ?: return null
        val url = "https://api.chksz.com/api/$endpoint".toHttpUrl().newBuilder()
            .apply { params.forEach { (name, value) -> addQueryParameter(name, value) } }
            .addQueryParameter("apikey", key)
            .build()
        val request = Request.Builder().url(url)
            .header("User-Agent", "MeloX/0.4.5")
            .header("Accept", "application/json, text/plain, */*")
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            runCatching { findUrl(JSONObject(response.body.string())) }.getOrNull()
        }
    }

    private fun findUrl(value: Any?): String? = when (value) {
        is JSONObject -> {
            sequenceOf("url", "playUrl", "play_url", "downloadUrl", "audioUrl")
                .map(value::optString).firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                ?: value.keys().asSequence().mapNotNull { findUrl(value.opt(it)) }.firstOrNull()
        }
        is JSONArray -> (0 until value.length()).asSequence().mapNotNull { findUrl(value.opt(it)) }.firstOrNull()
        is String -> value.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        else -> null
    }
}

private fun AudioQualityTier.toChkszLevel(): String = when (this) {
    AudioQualityTier.Standard -> "standard"
    AudioQualityTier.High -> "exhigh"
    AudioQualityTier.Lossless -> "lossless"
    AudioQualityTier.HiResolution -> "hires"
    AudioQualityTier.Immersive -> "jyeffect"
    AudioQualityTier.Master -> "jymaster"
}

private fun AudioQualityTier.toChkszSize(): String = when (this) {
    AudioQualityTier.Standard -> "128k"
    AudioQualityTier.High -> "320k"
    AudioQualityTier.Lossless -> "flac"
    AudioQualityTier.HiResolution -> "hires"
    AudioQualityTier.Immersive, AudioQualityTier.Master -> "master"
}
