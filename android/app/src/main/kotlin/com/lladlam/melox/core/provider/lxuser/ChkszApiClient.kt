package com.lladlam.melox.core.provider.lxuser

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.provider.thirdparty.resolverQualityFallbacks
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

/** Optional third-party resolver backed by api.chksz.com. */
class ChkszApiClient(
    private val apiKeyProvider: () -> String,
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
    private val baseUrlProvider: () -> String = { "https://api.chksz.com" },
) {
    fun resolveNetease(songId: Long, quality: AudioQualityTier): String? =
        request(
            endpoint = "163_music",
            params = mapOf(
                "id" to songId.toString(),
                "level" to quality.toChkszLevel(),
                "type" to "json",
            ),
        )

    /**
     * CHKSZ also exposes QQ Music and KuGou. When a NetEase song is unavailable
     * at CHKSZ's NetEase endpoint, search those two catalogues and resolve the
     * strict title/artist match through the same API key.
     */
    internal fun resolveCrossSource(title: String, artist: String, quality: AudioQualityTier): ChkszResolvedUrl? {
        if (title.isBlank() || artist.isBlank()) return null
        for (platform in listOf(ChkszPlatform.QQMusic, ChkszPlatform.Kugou)) {
            val matches = runCatching { search(platform, "$title $artist", limit = 10) }
                .getOrDefault(emptyList())
            val match = selectChkszMatch(title, artist, matches) ?: continue
            for (candidate in resolverQualityFallbacks(quality)) {
                resolve(platform, match.id, candidate)?.let {
                    return ChkszResolvedUrl(it, candidate)
                }
            }
        }
        return null
    }

    internal fun search(
        platform: ChkszPlatform,
        keyword: String,
        limit: Int = 10,
    ): List<ChkszSearchItem> {
        val key = apiKeyProvider().trim().takeIf(String::isNotBlank) ?: return emptyList()
        val url = apiUrl(
            platform.endpoint,
            mapOf(
                "msg" to keyword,
                "num" to limit.coerceIn(1, 50).toString(),
                "type" to "json",
                "apikey" to key,
            ),
        )
        val request = Request.Builder().url(url)
            .header("User-Agent", "MeloX/Android")
            .header("Accept", "application/json, text/plain, */*")
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use emptyList()
            val root = runCatching { JSONObject(response.body.string()) }.getOrNull()
                ?: return@use emptyList()
            val list = root.optJSONArray("list") ?: return@use emptyList()
            (0 until list.length()).mapNotNull { index ->
                val item = list.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString(platform.idParameter).takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                ChkszSearchItem(
                    id = id,
                    name = item.optString("name"),
                    singer = item.optString("singer"),
                )
            }
        }
    }

    internal fun resolve(
        platform: ChkszPlatform,
        id: String,
        quality: AudioQualityTier,
    ): String? = request(
        endpoint = platform.endpoint,
        params = mapOf(
            platform.idParameter to id,
            "size" to quality.toChkszSize(),
            "type" to "json",
        ),
    )

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
        val url = apiUrl(endpoint, params + ("apikey" to key))
        val request = Request.Builder().url(url)
            .header("User-Agent", "MeloX/Android")
            .header("Accept", "application/json, text/plain, */*")
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            runCatching { findUrl(JSONObject(response.body.string())) }.getOrNull()
        }
    }

    private fun apiUrl(endpoint: String, params: Map<String, String>): HttpUrl {
        val base = baseUrlProvider().trim().ifBlank { "https://api.chksz.com" }.trimEnd('/')
        val path = if (base.endsWith("/api")) "$base/$endpoint" else "$base/api/$endpoint"
        return path.toHttpUrl().newBuilder()
            .apply { params.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()
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

internal enum class ChkszPlatform(val endpoint: String, val idParameter: String) {
    QQMusic("qq_music", "mid"),
    Kugou("kugou_music", "id"),
}

internal data class ChkszSearchItem(
    val id: String,
    val name: String,
    val singer: String,
)

internal data class ChkszResolvedUrl(
    val url: String,
    val quality: AudioQualityTier,
)

internal fun selectChkszMatch(
    title: String,
    artist: String,
    items: List<ChkszSearchItem>,
): ChkszSearchItem? {
    val wantedTitle = normalizeChkszText(title)
    if (wantedTitle.isBlank()) return null
    val wantedArtists = artist.split(Regex("\\s*(?:/|、|，|,|;|；)\\s*"))
        .map(::normalizeChkszText)
        .filter(String::isNotBlank)
    return items.firstOrNull { item ->
        normalizeChkszText(item.name) == wantedTitle &&
            wantedArtists.any { wanted -> normalizeChkszText(item.singer).contains(wanted) }
    }
}

private fun normalizeChkszText(value: String): String =
    value.lowercase().filter { it.isLetterOrDigit() }

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
