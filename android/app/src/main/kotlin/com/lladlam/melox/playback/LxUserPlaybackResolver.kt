package com.lladlam.melox.playback

import android.content.Context
import android.util.Log
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.provider.lxuser.LxUserRuntime
import com.lladlam.melox.core.provider.lxuser.LxUserScript
import com.lladlam.melox.core.provider.lxuser.LxUserSourceStore
import java.io.IOException
import java.util.Locale

internal data class LxUserPlaybackResult(
    val sourceId: String,
    val url: String,
    val requestHeaders: Map<String, String> = emptyMap(),
)

/** Resolves a song through locally installed LX Music user API scripts. */
class LxUserPlaybackResolver(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun cacheIdentity(): String = LxUserSourceStore.list(appContext).joinToString("|") { it.id }

    internal fun resolve(
        songId: Long,
        title: String,
        artist: String,
        durationMs: Long?,
        quality: AudioQualityTier,
    ): LxUserPlaybackResult? {
        return resolve(
            MusicTrack(
                id = com.lladlam.melox.core.music.model.MusicResourceId(MusicSource.Netease, songId.toString()),
                title = title,
                artists = listOf(com.lladlam.melox.core.music.model.MusicArtistRef(name = artist)),
                durationMs = durationMs,
            ),
            quality,
        )
    }

    internal fun resolve(track: MusicTrack, quality: AudioQualityTier): LxUserPlaybackResult? {
        val sourceCode = when (track.id.source) {
            MusicSource.QQMusic -> "tx"
            MusicSource.Kugou -> "kg"
            MusicSource.Kuwo -> "kw"
            MusicSource.Netease -> null
            else -> return null
        }
        val title = track.title
        val artist = track.artistText
        if (title.isBlank() || artist.isBlank()) return null
        val resourceId = track.id.value
        val lxQuality = quality.toLxQuality()
        val song = standardMusicInfo(track, sourceCode, lxQuality)
        Log.d(TAG, "LX resolve start provider=${track.id.source.storageValue} id=${resourceId.take(8)} quality=$lxQuality " +
            "source=$sourceCode title=${title.take(40)} artist=${artist.take(40)}")
        val actionArgs = mapOf<String, Any?>(
            "id" to resourceId,
            "songId" to resourceId,
            "mid" to resourceId,
            "songmid" to resourceId,
            "hash" to resourceId,
            "name" to title,
            "title" to title,
            "artist" to artist,
            "singer" to artist,
            "duration" to track.durationMs?.div(1_000L),
            "durationMs" to track.durationMs,
            "quality" to lxQuality,
            "musicInfo" to song,
        )
        for (record in LxUserSourceStore.list(appContext)) {
            val script = LxUserSourceStore.script(appContext, record.id) ?: continue
            var phase = "load"
                    runCatching {
                LxUserRuntime().use { runtime ->
                    runtime.load(LxUserScript(script))
                    phase = "request"
                    (sourceCode?.let(::listOf) ?: listOf("kw", "kg", "tx", "wy", "mg")).asSequence()
                        .mapNotNull { source ->
                            val sourceQuality = runtime.qualityFor(source, lxQuality)
                            Log.d(TAG, "LX candidate script=${record.id} source=$source requested=$lxQuality actual=$sourceQuality")
                            val response = runtime.callAction(
                                "musicUrl",
                                song + mapOf(
                                    "source" to source,
                                    "type" to sourceQuality,
                                    "musicInfo" to song,
                                ),
                            )
                            response?.let { value ->
                                val url = when (value) {
                                    is String -> value
                                    is Map<*, *> -> value["url"]?.toString()
                                    else -> null
                                }?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                                Log.d(TAG, "LX candidate result script=${record.id} source=$source url=${url != null}")
                                url?.let { LxUserPlaybackResult(record.id, it) }
                            }
                        }
                        .firstOrNull() ?: throw IOException("LX 音乐源没有返回可播放链接")
                }
                    }.onSuccess {
                Log.i(TAG, "LX resolved source=${track.id.source.storageValue} script=${record.id}")
                return it
                    }.onFailure { error ->
                Log.w(
                    TAG,
                    "LX failed source=${track.id.source.storageValue} script=${record.id} phase=$phase " +
                        "error=${error.javaClass.simpleName} detail=${error.safeLogMessage()}",
                )
                    }
        }
        Log.i(TAG, "LX unresolved source=${track.id.source.storageValue} scripts=${LxUserSourceStore.list(appContext).size}")
        return null
    }

    private companion object {
        const val TAG = "MeloXThirdParty"
    }
}

private fun standardMusicInfo(
    track: MusicTrack,
    sourceCode: String?,
    quality: String,
): Map<String, Any?> {
    val durationSeconds = track.durationMs?.coerceAtLeast(0L)?.div(1_000L)
    val interval = durationSeconds?.let { seconds ->
        String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L)
    }
    val metadata = when (val provider = track.providerMetadata) {
        is com.lladlam.melox.core.music.model.ProviderTrackMetadata.QQMusic -> mapOf(
            "songId" to (provider.numericSongId ?: track.id.value),
            "strMediaMid" to (provider.mediaMid ?: track.id.value),
            "albumMid" to null,
        )
        is com.lladlam.melox.core.music.model.ProviderTrackMetadata.Kugou -> mapOf(
            "songId" to track.id.value,
            "hash" to provider.hash,
            "albumId" to provider.albumId,
        )
        is com.lladlam.melox.core.music.model.ProviderTrackMetadata.Kuwo -> mapOf(
            "songId" to provider.mid,
        )
        else -> mapOf("songId" to track.id.value)
    } + mapOf(
        "qualitys" to listOf(quality),
        "_qualitys" to emptyMap<String, Any?>(),
        "albumName" to (track.album?.name ?: ""),
        "picUrl" to track.artworkUrl,
    )
    return mapOf(
        "id" to track.id.value,
        "name" to track.title,
        "singer" to track.artistText,
        "artist" to track.artistText,
        "source" to sourceCode,
        "interval" to interval,
        "meta" to metadata,
        "songId" to track.id.value,
        "songmid" to track.id.value,
        "mid" to track.id.value,
        "hash" to track.id.value,
        "title" to track.title,
        "duration" to durationSeconds,
        "durationMs" to track.durationMs,
        "quality" to quality,
    )
}

private fun AudioQualityTier.toLxQuality(): String = when (this) {
    AudioQualityTier.Standard -> "128k"
    AudioQualityTier.High -> "320k"
    AudioQualityTier.Lossless -> "flac"
    AudioQualityTier.HiResolution, AudioQualityTier.Immersive, AudioQualityTier.Master -> "flac24bit"
}

private fun Throwable.safeLogMessage(): String = message.orEmpty()
    .replace(Regex("https?://\\S+"), "<url>")
    .replace(Regex("(?i)(apikey|api_key|token|key)=([^&\\s]+)"), "$1=<redacted>")
    .replace('\n', ' ')
    .take(240)
    .ifBlank { "none" }
