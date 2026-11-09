package com.lladlam.melox.playback

import android.content.Context
import android.util.Log
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.provider.lxuser.LxUserRuntime
import com.lladlam.melox.core.provider.lxuser.LxUserScript
import com.lladlam.melox.core.provider.lxuser.LxUserSourceStore
import com.lladlam.melox.core.provider.lxuser.buildLxMusicInfo
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.lyrics.LrcLyricsParser
import com.lladlam.melox.core.lyrics.LyricFileParser
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal data class LxUserPlaybackResult(
    val sourceId: String,
    val url: String,
    val requestHeaders: Map<String, String> = emptyMap(),
    /** Quality identifier negotiated with the script (`master`, `hires`, ...). */
    val sourceQuality: String? = null,
    /** Best-effort tier represented by [sourceQuality]; not a probe of the URL. */
    val actualQuality: AudioQualityTier? = null,
)

/** Resolves a song through locally installed LX Music user API scripts. */
class LxUserPlaybackResolver(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val inFlight = ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<LxUserPlaybackResult?>>()

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
        val data = (value as? Map<*, *>) ?: return (value as? String)?.let(LyricFileParser::parse)
        val lyric = data["lyric"]?.toString().orEmpty().ifBlank { data["lrc"]?.toString().orEmpty() }
        if (lyric.isBlank()) return null
        val rich = LyricFileParser.parse(lyric)
        if (rich.lines.any { it.syllables.isNotEmpty() } ||
            com.lladlam.melox.core.lyrics.LyricifyLyricsParser.isLines(lyric)) return rich
        return LrcLyricsParser.parse(
            lrc = lyric,
            translation = data["tlyric"]?.toString().orEmpty().ifBlank { data["translation"]?.toString().orEmpty() },
            romanization = data["rlyric"]?.toString().orEmpty().ifBlank { data["romalrc"]?.toString().orEmpty() },
        )
    }

    private fun resolveAction(track: MusicTrack, action: String): Any? {
        val sourceCode = when (track.id.sourceId.builtIn) {
            MusicSource.QQMusic -> "tx"
            MusicSource.Kugou -> "kg"
            MusicSource.Kuwo -> "kw"
            MusicSource.Netease -> "wy"
            else -> return null
        }
        val song = buildLxMusicInfo(track, sourceCode, AudioQualityTier.Standard.toLxQuality())
        for (record in LxUserSourceStore.list(appContext)) {
            val script = LxUserSourceStore.script(appContext, record.id) ?: continue
            val result = runCatching {
                val runtime = LxUserRuntime.session(record.id, script)
                if (!runtime.supportsAction(sourceCode, action)) return@runCatching null
                runtime.callAction(action, song + mapOf(
                        "source" to sourceCode,
                        "type" to "128k",
                        "musicInfo" to song,
                    ))
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
        if (track.id.sourceId.builtIn == null) return null
        val requestKey = "${track.id.source.storageValue}:${track.id.value}:${quality.name}:${cacheIdentity()}"
        val pending = java.util.concurrent.CompletableFuture<LxUserPlaybackResult?>()
        val existing = inFlight.putIfAbsent(requestKey, pending)
        if (existing != null) return runCatching { existing.get(45L, java.util.concurrent.TimeUnit.SECONDS) }.getOrNull()
        return try {
            val resolved = resolveUncached(track, quality)
            pending.complete(resolved)
            resolved
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(requestKey, pending)
        }
    }

    private fun resolveUncached(track: MusicTrack, quality: AudioQualityTier): LxUserPlaybackResult? {
        val sourceCode = when (track.id.sourceId.builtIn) {
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
        Log.d(TAG, "LX resolve start provider=${track.id.source.storageValue} id=${resourceId.take(8)} quality=$lxQuality " +
            "source=$sourceCode title=${title.take(40)} artist=${artist.take(40)}")
        for (record in LxUserSourceStore.list(appContext)) {
            val script = LxUserSourceStore.script(appContext, record.id) ?: continue
            var phase = "load"
                    runCatching {
                val runtime = LxUserRuntime.session(record.id, script)
                    phase = "request"
                    (sourceCode?.let(::listOf) ?: listOf("kw", "kg", "tx", "wy", "mg"))
                        .filter { runtime.supportsSource(it) && runtime.supportsAction(it, "musicUrl") }
                        .flatMap { source ->
                            lxQualityFallbacks(lxQuality).map { requestedQuality ->
                                source to runtime.qualityFor(source, requestedQuality)
                            }
                        }
                        .distinct()
                        .mapNotNull { (source, sourceQuality) ->
                            Log.d(TAG, "LX candidate script=${record.id} source=$source requested=$lxQuality actual=$sourceQuality")
                            val candidateSong = buildLxMusicInfo(track, source, sourceQuality)
                            runCatching {
                                runtime.callAction(
                                    "musicUrl",
                                    candidateSong + mapOf(
                                        "source" to source,
                                        "type" to sourceQuality,
                                        "musicInfo" to candidateSong,
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
                                url?.let {
                                    LxUserPlaybackResult(
                                        sourceId = record.id,
                                        url = it,
                                        sourceQuality = sourceQuality,
                                        actualQuality = lxQualityTier(sourceQuality),
                                    )
                                }
                            }
                        }
                        .firstOrNull() ?: throw IOException("LX 音乐源没有返回可播放链接")
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
        val requestedArtist = normalizeLxText(track.artistText)
        val match = candidates
            .asSequence()
            .filter { normalizeLxText(it.name) == normalizeLxText(track.title) }
            .filter { candidate ->
                requestedArtist.isBlank() || normalizeLxText(candidate.artists).contains(requestedArtist)
            }
            .filter { candidate -> track.durationMs == null || candidate.durationMs <= 0L || kotlin.math.abs(candidate.durationMs - track.durationMs) <= 3_000L }
            .filter { candidate ->
                val name = candidate.name.lowercase(Locale.ROOT)
                !name.contains("live") && !name.contains("翻唱") && !name.contains("cover")
            }
            .sortedBy { candidate -> kotlin.math.abs(candidate.durationMs - (track.durationMs ?: candidate.durationMs)) }
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
        for (record in LxUserSourceStore.list(appContext)) {
            val script = LxUserSourceStore.script(appContext, record.id) ?: continue
            val result = runCatching {
                val runtime = LxUserRuntime.session(record.id, script)
                    if (!runtime.supportsAction("wy", "musicUrl")) return@runCatching null
                    lxQualityFallbacks(quality.toLxQuality())
                        .map { requestedQuality -> runtime.qualityFor("wy", requestedQuality) }
                        .distinct()
                        .asSequence()
                        .mapNotNull { sourceQuality ->
                            val candidateSong = buildLxMusicInfo(track, "wy", sourceQuality)
                            val response = runCatching {
                                runtime.callAction("musicUrl", candidateSong + mapOf(
                                    "source" to "wy",
                                    "type" to sourceQuality,
                                    "musicInfo" to candidateSong,
                                ))
                            }.onFailure { Log.w(TAG, "LX Netease candidate failed quality=$sourceQuality detail=${it.safeLogMessage()}") }.getOrNull()
                            val url = when (response) {
                                is String -> response
                                is Map<*, *> -> response["url"]?.toString()
                                else -> null
                            }?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                            url?.let {
                                LxUserPlaybackResult(
                                    sourceId = record.id,
                                    url = it,
                                    sourceQuality = sourceQuality,
                                    actualQuality = lxQualityTier(sourceQuality),
                                )
                            }
                        }.firstOrNull()
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private companion object {
        const val TAG = "MeloXThirdParty"
    }
}

/** Every LX quality at or below [requested], highest first. */
internal fun lxQualityFallbacks(requested: String): List<String> {
    val index = com.lladlam.melox.core.provider.lxuser.LX_QUALITY_ORDER.indexOf(requested)
    if (index < 0) return listOf(requested)
    return com.lladlam.melox.core.provider.lxuser.LX_QUALITY_ORDER
        .subList(0, index + 1)
        .asReversed()
}

private fun normalizeLxText(value: String): String = value
    .lowercase(Locale.ROOT)
    .replace(Regex("[\\s\\p{Punct}·•，。！？、（）()\\[\\]【】]"), "")


internal fun AudioQualityTier.toLxQuality(): String = when (this) {
    AudioQualityTier.Standard -> "128k"
    AudioQualityTier.High -> "320k"
    AudioQualityTier.Lossless -> "flac"
    AudioQualityTier.HiResolution -> "hires"
    // HighDefinitionSurround/ImmersiveSurround share this tier; ask for the
    // higher LX name so qualityFor can still fall back to `atmos` when needed.
    AudioQualityTier.Immersive -> "atmos_plus"
    AudioQualityTier.Master -> "master"
}

internal fun lxQualityTier(quality: String?): AudioQualityTier? = when (quality) {
    "128k" -> AudioQualityTier.Standard
    "320k" -> AudioQualityTier.High
    "flac" -> AudioQualityTier.Lossless
    "flac24bit", "hires" -> AudioQualityTier.HiResolution
    "atmos", "atmos_plus" -> AudioQualityTier.Immersive
    "master" -> AudioQualityTier.Master
    else -> null
}

private fun Throwable.safeLogMessage(): String = message.orEmpty()
    .replace(Regex("https?://\\S+"), "<url>")
    .replace(Regex("(?i)(apikey|api_key|token|key)=([^&\\s]+)"), "$1=<redacted>")
    .replace('\n', ' ')
    .take(240)
    .ifBlank { "none" }
