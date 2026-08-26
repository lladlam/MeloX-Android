package com.lladlam.melox.core.provider.kuwo

import android.util.Log
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import java.io.IOException
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/** Native Kuwo provider using the legacy public search/playback endpoints. */
class KuwoApiClient(
    httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    private val requests = KuwoRequestClient(httpClient)

    suspend fun searchSongs(
        query: String,
        page: Int = 1,
        pageSize: Int = 30,
    ): MusicPage<MusicTrack> {
        val normalized = query.trim()
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 50)
        if (normalized.isBlank()) return MusicPage(emptyList(), safePage, safeSize, 0)

        val response = requests.get(
            baseUrl = "https://search.kuwo.cn",
            path = "/r.s",
            params = mapOf(
                "ft" to "music",
                "itemset" to "web_2013",
                "client" to "kt",
                "pn" to (safePage - 1).toString(),
                "rn" to safeSize.toString(),
                "encoding" to "utf8",
                "all" to normalized,
                "rformat" to "json",
            ),
        )

        val total = response.optString("TOTAL").toLongOrNull()
            ?: response.optString("HIT").toLongOrNull()
        val list = response.optJSONArray("abslist") ?: JSONArray()
        val tracks = buildList {
            for (index in 0 until list.length()) {
                list.optJSONObject(index)?.let(::parseSearchTrack)?.let(::add)
            }
        }
        return MusicPage(
            items = tracks,
            page = safePage,
            pageSize = safeSize,
            total = total,
        )
    }

    suspend fun resolvePlayback(
        track: MusicTrack,
        quality: AudioQualityTier,
    ): PlaybackResolution {
        val metadata = track.requireKuwoMetadata()
        val mid = metadata.mid

        var lastError: IOException? = null
        for (candidate in quality.kuwoPlaybackCandidates()) {
            val response = runCatching {
                requests.get(
                    baseUrl = "https://antiserver.kuwo.cn",
                    path = "/anti.s",
                    params = mapOf(
                        "type" to "convert_url3",
                        "rid" to "MUSIC_$mid",
                        "format" to candidate.format,
                        "response" to "url",
                    ),
                )
            }.getOrElse { error ->
                lastError = IOException(error.message ?: "酷我音乐请求失败", error)
                Log.w(TAG, "Playback unavailable: mid=$mid format=${candidate.format}", error)
                null
            } ?: continue

            if (response.optInt("code", -1) != 200) {
                val message = response.optString("msg").ifBlank { "酷我音乐没有返回 ${candidate.format} 可播放链接" }
                lastError = IOException(message)
                Log.w(TAG, "Playback refused: mid=$mid format=${candidate.format} message=$message")
                continue
            }

            val url = response.optString("url").takeIf { it.isNotBlank() }
            if (url == null) {
                lastError = IOException("酷我音乐没有返回 ${candidate.format} 可播放链接")
                Log.w(TAG, "Playback returned no URL: mid=$mid format=${candidate.format}")
                continue
            }

            val requestedCandidate = quality.kuwoPlaybackCandidates().firstOrNull()
            if (candidate != requestedCandidate) {
                Log.i(TAG, "Playback quality fallback: mid=$mid requested=${quality.name} actual=${candidate.actualTier}")
            }
            Log.i(TAG, "Playback URL resolved: mid=$mid format=${candidate.format} endpoint=${url.redactedEndpoint()}")
            return PlaybackResolution.Playable(
                url = secureUrl(url),
                requestedQuality = quality,
                actualQuality = candidate.actualTier,
                format = candidate.format,
                requestHeaders = mapOf(
                    "User-Agent" to KuwoRequestClient.UserAgent,
                    "Referer" to "http://www.kuwo.cn/",
                    "Accept" to "*/*",
                    "Accept-Encoding" to "identity",
                ),
            )
        }

        val message = lastError?.message ?: "酷我音乐没有返回可播放链接"
        return PlaybackResolution.Unavailable(message)
    }

    private fun parseSearchTrack(item: JSONObject): MusicTrack? {
        val mid = item.optString("DC_TARGETID").toLongOrNull()
            ?: item.optString("MUSICRID").removePrefix("MUSIC_").toLongOrNull()
            ?: return null
        if (mid <= 0L) return null

        val title = decodeHtmlEntities(item.optString("NAME")).ifBlank { "未知歌曲" }
        val rawArtist = item.optString("ARTIST")
        val artistNames = decodeHtmlEntities(rawArtist)
            .split(Regex("\\s*(?:、|/|&|,|;|；)\\s*"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .ifEmpty { listOf("未知歌手") }
        val albumName = decodeHtmlEntities(item.optString("ALBUM")).takeIf(String::isNotBlank)
        val albumId = item.optString("ALBUMID").takeIf(String::isNotBlank)
        val durationSeconds = item.optString("DURATION").toLongOrNull()?.takeIf { it > 0L }
        val artwork = normalizeAlbumArtwork(item.optString("web_albumpic_short"))

        return MusicTrack(
            id = MusicResourceId(MusicSource.Kuwo, mid.toString()),
            title = title,
            artists = artistNames.map { MusicArtistRef(name = it) },
            album = albumName?.let {
                MusicAlbumRef(
                    id = albumId?.let { value -> MusicResourceId(MusicSource.Kuwo, value) },
                    name = it,
                    artworkUrl = artwork,
                )
            },
            artworkUrl = artwork,
            durationMs = durationSeconds?.times(1_000L),
            providerMetadata = ProviderTrackMetadata.Kuwo(mid = mid),
        )
    }

    private fun normalizeAlbumArtwork(value: String): String? = value
        .trim()
        .takeIf(String::isNotBlank)
        ?.let { "https://img1.kuwo.cn/star/albumcover/$it" }

    private fun decodeHtmlEntities(value: String): String = value
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&apos;", "'")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()

    private fun secureUrl(value: String): String =
        if (value.startsWith("http://", ignoreCase = true)) "https://${value.substringAfter("://")}" else value

    private fun String.redactedEndpoint(): String =
        substringBefore('?').take(80)

    private fun MusicTrack.requireKuwoMetadata(): ProviderTrackMetadata.Kuwo {
        val metadata = providerMetadata
        require(metadata is ProviderTrackMetadata.Kuwo) { "track must carry Kuwo metadata" }
        return metadata
    }

    private companion object {
        const val TAG = "KuwoApiClient"
    }
}

private data class KuwoPlaybackCandidate(
    val format: String,
    val actualTier: AudioQualityTier,
)

private fun AudioQualityTier.kuwoPlaybackCandidates(): List<KuwoPlaybackCandidate> = when (this) {
    AudioQualityTier.Standard -> listOf(KuwoPlaybackCandidate("mp3", AudioQualityTier.Standard))
    AudioQualityTier.High,
    AudioQualityTier.Lossless,
    AudioQualityTier.HiResolution,
    AudioQualityTier.Immersive,
    AudioQualityTier.Master,
    -> listOf(
        // The public antiserver endpoint only exposes ~128 kbps MP3/AAC.
        // We still report the requested tier as the closest available quality.
        KuwoPlaybackCandidate("mp3", AudioQualityTier.High),
        KuwoPlaybackCandidate("aac", AudioQualityTier.Standard),
    )
}
