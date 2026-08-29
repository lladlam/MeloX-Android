package com.lladlam.melox.playback

import android.content.Context
import android.util.Log
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.provider.lxuser.LxUserRuntime
import com.lladlam.melox.core.provider.lxuser.LxUserScript
import com.lladlam.melox.core.provider.lxuser.LxUserSourceStore
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.lyrics.LrcLyricsParser
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
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

    internal fun resolveArtwork(track: MusicTrack): String? =
        resolveAction(track, "pic")?.let { value ->
            when (value) {
                is String -> value
                is Map<*, *> -> sequenceOf("url", "picUrl", "artworkUrl")
                    .mapNotNull { value[it]?.toString() }.firstOrNull { it.startsWith("http") }
                else -> null
            }
        }?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    internal fun resolveLyrics(track: MusicTrack): LyricsDocument? {
        val value = resolveAction(track, "lyric") ?: return null
        val data = (value as? Map<*, *>) ?: return (value as? String)?.let(LrcLyricsParser::parse)
        val lyric = data["lyric"]?.toString().orEmpty().ifBlank { data["lrc"]?.toString().orEmpty() }
        if (lyric.isBlank()) return null
        return LrcLyricsParser.parse(
            lrc = lyric,
            translation = data["tlyric"]?.toString().orEmpty().ifBlank { data["translation"]?.toString().orEmpty() },
            romanization = data["rlyric"]?.toString().orEmpty().ifBlank { data["romalrc"]?.toString().orEmpty() },
        )
    }

    private fun resolveAction(track: MusicTrack, action: String): Any? {
        val sourceCode = when (track.id.source) {
            MusicSource.QQMusic -> "tx"
            MusicSource.Kugou -> "kg"
            MusicSource.Kuwo -> "kw"
            MusicSource.Netease -> "wy"
            else -> return null
        }
        val song = standardMusicInfo(track, sourceCode, AudioQualityTier.Standard.toLxQuality())
        for (record in LxUserSourceStore.list(appContext)) {
            val script = LxUserSourceStore.script(appContext, record.id) ?: continue
            val result = runCatching {
                LxUserRuntime().use { runtime ->
                    runtime.load(LxUserScript(script))
                    runtime.callAction(action, song + mapOf(
                        "source" to sourceCode,
                        "type" to "128k",
                        "musicInfo" to song,
                    ))
                }
            }.onFailure { error ->
                Log.w(TAG, "LX $action failed script=${record.id} detail=${error.safeLogMessage()}")
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

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
                        .flatMap { source ->
                            lxQualityFallbacks(lxQuality).asSequence().map { requestedQuality -> source to requestedQuality }
                        }
                        .mapNotNull { (source, requestedQuality) ->
                            val sourceQuality = runtime.qualityFor(source, requestedQuality)
                            Log.d(TAG, "LX candidate script=${record.id} source=$source requested=$requestedQuality actual=$sourceQuality")
                            runCatching {
                                runtime.callAction(
                                    "musicUrl",
                                    song + mapOf(
                                        "source" to source,
                                        "type" to sourceQuality,
                                        "musicInfo" to song,
                                    ),
                                )
                            }.onFailure {
                                Log.w(TAG, "LX candidate failed script=${record.id} source=$source quality=$sourceQuality detail=${it.safeLogMessage()}")
                            }.getOrNull()?.let { value ->
                                val url = when (value) {
                                    is String -> value
                                    is Map<*, *> -> value["url"]?.toString()
                                    else -> null
                                }?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                                Log.d(TAG, "LX candidate result script=${record.id} source=$source quality=$sourceQuality url=${url != null}")
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
        if (track.id.source != MusicSource.Netease) {
            resolveViaNeteaseMatch(track, quality)?.let { return it }
        }
        Log.i(TAG, "LX unresolved source=${track.id.source.storageValue} scripts=${LxUserSourceStore.list(appContext).size}")
        return null
    }

    private fun resolveViaNeteaseMatch(track: MusicTrack, quality: AudioQualityTier): LxUserPlaybackResult? {
        val query = track.title.trim()
        val candidates = runCatching {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                NeteaseSearchClient().searchSongs(query, limit = 50)
            }
        }.onFailure { Log.w(TAG, "LX Netease match search failed title=${track.title.take(40)} detail=${it.safeLogMessage()}") }
            .getOrNull().orEmpty()
        val match = candidates
            .asSequence()
            .filter { normalizeLxText(it.name) == normalizeLxText(track.title) }
            .filter { candidate -> track.durationMs == null || candidate.durationMs <= 0L || kotlin.math.abs(candidate.durationMs - track.durationMs) <= 8_000L }
            .sortedWith(compareByDescending<com.lladlam.melox.core.model.SearchSong> {
                val requestedArtist = normalizeLxText(track.artistText)
                if (requestedArtist.isNotBlank() && normalizeLxText(it.artists).contains(requestedArtist)) 1 else 0
            }.thenBy { candidate -> kotlin.math.abs(candidate.durationMs - (track.durationMs ?: candidate.durationMs)) })
            .firstOrNull()
        if (match == null) {
            Log.w(TAG, "LX Netease match not found title=${track.title.take(40)} candidates=${candidates.size}")
            return null
        }

        val neteaseTrack = MusicTrack(
            id = MusicResourceId(MusicSource.Netease, match.id.toString()),
            title = match.name,
            artists = listOf(MusicArtistRef(name = match.artists)),
            durationMs = match.durationMs,
            artworkUrl = match.artworkUrl,
            providerMetadata = ProviderTrackMetadata.Netease(match.id),
        )
        Log.i(TAG, "LX Netease match source=${track.id.source.storageValue} id=${track.id.value.take(8)} -> ${match.id}")
        return resolveNeteaseTrack(neteaseTrack, quality)
    }

    private fun resolveNeteaseTrack(track: MusicTrack, quality: AudioQualityTier): LxUserPlaybackResult? {
        val song = standardMusicInfo(track, "wy", quality.toLxQuality())
        for (record in LxUserSourceStore.list(appContext)) {
            val script = LxUserSourceStore.script(appContext, record.id) ?: continue
            val result = runCatching {
                LxUserRuntime().use { runtime ->
                    runtime.load(LxUserScript(script))
                    lxQualityFallbacks(quality.toLxQuality()).asSequence().mapNotNull { requestedQuality ->
                        val sourceQuality = runtime.qualityFor("wy", requestedQuality)
                        val response = runCatching {
                            runtime.callAction("musicUrl", song + mapOf(
                                "source" to "wy",
                                "type" to sourceQuality,
                                "musicInfo" to song,
                            ))
                        }.onFailure { Log.w(TAG, "LX Netease candidate failed quality=$sourceQuality detail=${it.safeLogMessage()}") }.getOrNull()
                        val url = when (response) {
                            is String -> response
                            is Map<*, *> -> response["url"]?.toString()
                            else -> null
                        }?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                        url?.let { LxUserPlaybackResult(record.id, it) }
                    }.firstOrNull()
                }
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private companion object {
        const val TAG = "MeloXThirdParty"
    }
}

private fun lxQualityFallbacks(requested: String): List<String> = when (requested) {
    "flac24bit" -> listOf("flac24bit", "flac", "320k", "128k")
    "flac" -> listOf("flac", "320k", "128k")
    "320k" -> listOf("320k", "128k")
    else -> listOf(requested)
}

private fun normalizeLxText(value: String): String = value
    .lowercase(Locale.ROOT)
    .replace(Regex("[\\s\\p{Punct}·•，。！？、（）()\\[\\]【】]"), "")


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
            // LX's canonical model keeps QQ's mid, numeric id and media mid
            // separately. User sources rely on all three fields.
            "songId" to track.id.value,
            "id" to (provider.numericSongId ?: 0L),
            "strMediaMid" to (provider.mediaMid ?: track.id.value),
            "albumMid" to (track.album?.id?.value ?: ""),
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
