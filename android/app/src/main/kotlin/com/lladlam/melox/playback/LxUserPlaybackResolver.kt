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
import com.lladlam.melox.core.lyrics.LrcLyricsParser
import com.lladlam.melox.core.lyrics.LyricFileParser
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.core.music.provider.SearchCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
            MusicSource.Kugou,
            MusicSource.KugouLite,
            -> "kg"
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
        val title = track.title
        val artist = track.artistText
        if (title.isBlank() || artist.isBlank()) return null
        val origin = lxSourceCode(track.id.source) ?: return null
        val supported = supportedLxSourceCodes()
        if (supported.isEmpty()) return null
        // The track's own platform is always first; all remaining LX platforms
        // are attempted with a strict cross-source lookup so every call gets a
        // real platform ID instead of a NetEase numeric id reused as `songmid`.
        val orderedSources = buildList {
            add(origin)
            addAll(LX_ALL_SOURCES.filter { it != origin })
        }.filter { it in supported }
        Log.d(
            TAG,
            "LX resolve start provider=${track.id.source.storageValue} id=${track.id.value.take(8)} " +
                "quality=${quality.toLxQuality()} sources=${orderedSources.joinToString(",")} " +
                "title=${title.take(40)} artist=${artist.take(40)}",
        )
        for (code in orderedSources) {
            val candidate = trackForLxSource(track, code) ?: continue
            resolveSourceCandidates(candidate, quality, listOf(code))?.let { return it }
        }
        Log.i(TAG, "LX unresolved source=${track.id.source.storageValue} scripts=${LxUserSourceStore.list(appContext).size}")
        return null
    }

    private fun lxSourceCode(source: MusicSource): String? = when (source) {
        MusicSource.Netease -> "wy"
        MusicSource.QQMusic -> "tx"
        MusicSource.Kugou,
        MusicSource.KugouLite,
        -> "kg"
        MusicSource.Kuwo -> "kw"
        else -> null
    }

    /**
     * Strict cross-source ID lookup. The returned track must carry the target
     * provider's own resource ID; we never pass a title match's numeric id from
     * another platform to an LX script.
     */
    private fun trackForLxSource(track: MusicTrack, code: String): MusicTrack? {
        if (code == lxSourceCode(track.id.source)) return track
        // MeloX has no Migu provider, so there is no trustworthy Migu id to look up.
        if (code == "mg") return null
        val providerSource = when (code) {
            "wy" -> MusicSource.Netease
            "tx" -> MusicSource.QQMusic
            "kg" -> if (track.id.source == MusicSource.KugouLite) MusicSource.KugouLite else MusicSource.Kugou
            "kw" -> MusicSource.Kuwo
            else -> return null
        }
        val matched = searchMatchedProviderTrack(providerSource, track) ?: return null
        // Reject a provider result that somehow escaped its own catalog identity.
        if (matched.id.source != providerSource) {
            Log.w(TAG, "LX cross-source match rejected code=$code idSource=${matched.id.source.storageValue}")
            return null
        }
        Log.i(
            TAG,
            "LX cross-source match ${track.id.source.storageValue}:${track.id.value.take(8)} -> " +
                "$code:${matched.id.value.take(8)}",
        )
        return matched
    }

    private fun supportedLxSourceCodes(): Set<String> {
        val supported = linkedSetOf<String>()
        for (record in LxUserSourceStore.list(appContext)) {
            val script = LxUserSourceStore.script(appContext, record.id) ?: continue
            val runtime = runCatching { LxUserRuntime.session(record.id, script) }.getOrNull() ?: continue
            for (code in LX_ALL_SOURCES) {
                if (runtime.supportsSource(code) && runtime.supportsAction(code, "musicUrl")) supported += code
            }
        }
        return supported
    }

    private fun resolveSourceCandidates(
        track: MusicTrack,
        quality: AudioQualityTier,
        sourceCodes: List<String>,
    ): LxUserPlaybackResult? {
        val lxQuality = quality.toLxQuality()
        for (record in LxUserSourceStore.list(appContext)) {
            val script = LxUserSourceStore.script(appContext, record.id) ?: continue
            var phase = "load"
            val resolved = runCatching {
                val runtime = LxUserRuntime.session(record.id, script)
                phase = "request"
                sourceCodes
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
            }.onFailure { error ->
                Log.w(
                    TAG,
                    "LX failed source=${track.id.source.storageValue} script=${record.id} phase=$phase " +
                        "error=${error.javaClass.simpleName} detail=${error.safeLogMessage()}",
                )
            }.getOrNull()
            if (resolved != null) return resolved
        }
        return null
    }

    private fun searchMatchedProviderTrack(source: MusicSource, target: MusicTrack): MusicTrack? {
        val provider = runCatching { MeloXMusicProviders.create(appContext)[source] }.getOrNull() ?: return null
        val search = provider as? SearchCapability ?: return null
        val queries = listOf(
            "${target.title} ${target.artistText.substringBefore(" /")}".trim(),
            target.title.trim(),
        ).filter(String::isNotBlank).distinct()
        for (query in queries) {
            val results = runCatching {
                runBlocking(Dispatchers.IO) {
                    withTimeoutOrNull(8_000L) {
                        search.searchSongs(query, page = 1, pageSize = 20).items
                    }
                }
            }.getOrNull().orEmpty()
            val match = results
                .filter { isSafeLxPlaybackMatch(target.title, target.artistText, target.durationMs, it) }
                .maxByOrNull { candidate ->
                    val distance = kotlin.math.abs(
                        (candidate.durationMs ?: 0L) - (target.durationMs ?: candidate.durationMs ?: 0L),
                    )
                    -distance
                }
            if (match != null) return match
        }
        return null
    }

    private companion object {
        const val TAG = "MeloXThirdParty"
        val LX_ALL_SOURCES = listOf("kw", "kg", "tx", "wy", "mg")
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

internal enum class LxTitleVersion { Original, Live, Dj, Remix, Instrumental }

internal data class LxTitleIdentity(
    val baseTitle: String,
    val version: LxTitleVersion,
    val versionLabel: String,
)

internal fun lxTitleIdentity(title: String): LxTitleIdentity {
    val normalized = title.lowercase(Locale.ROOT)
    val version = when {
        Regex("(?:^|[^a-z])dj(?:[^a-z]|$)|电音|舞曲").containsMatchIn(normalized) -> LxTitleVersion.Dj
        Regex("remix|重混|混音版").containsMatchIn(normalized) -> LxTitleVersion.Remix
        Regex("live|现场|演唱会|音乐节").containsMatchIn(normalized) -> LxTitleVersion.Live
        Regex("instrumental|伴奏|纯音乐|off vocal|karaoke").containsMatchIn(normalized) -> LxTitleVersion.Instrumental
        else -> LxTitleVersion.Original
    }
    val versionPattern = Regex(
        "(?i)(?:[（(【\\[]?\\s*(?:dj(?:\\s+version)?|[^）)】\\]]*remix|live|现场版?|演唱会版?|音乐节版?|instrumental|伴奏版?|纯音乐|off\\s*vocal|karaoke)\\s*[）)】\\]]?)",
    )
    val base = normalizeLxText(title.replace(versionPattern, ""))
    val label = normalizeLxText(title).removePrefix(base)
    return LxTitleIdentity(base, version, label)
}

/** Playback-safe cross-provider match: title/version/artist first, duration last. */
internal fun isSafeLxPlaybackMatch(
    targetTitle: String,
    targetArtist: String,
    targetDurationMs: Long?,
    candidate: MusicTrack,
): Boolean {
    val target = lxTitleIdentity(withoutLxFeaturedArtist(targetTitle, targetArtist))
    val other = lxTitleIdentity(withoutLxFeaturedArtist(candidate.title, candidate.artistText))
    if (target.baseTitle.isBlank() || target.baseTitle != other.baseTitle) return false
    if (target.version != other.version) return false
    if (target.version != LxTitleVersion.Original && target.versionLabel != other.versionLabel) return false
    val targetArtistKey = normalizeLxText(targetArtist.substringBefore(" /"))
    val candidateArtistKey = normalizeLxText(candidate.artistText)
    if (targetArtistKey.isNotBlank() &&
        !candidateArtistKey.contains(targetArtistKey) &&
        !targetArtistKey.contains(candidateArtistKey)
    ) return false
    if (targetDurationMs != null && targetDurationMs > 0L) {
        val candidateDuration = candidate.durationMs ?: return true
        val hotRules = com.lladlam.melox.core.remoteconfig.MeloXHotRules.current
        val tolerance = if (target.version == LxTitleVersion.Original) {
            hotRules.lyricMatchOriginalToleranceMs
        } else {
            hotRules.lyricMatchVersionToleranceMs
        }
        if (kotlin.math.abs(targetDurationMs - candidateDuration) > tolerance) return false
    }
    return true
}

private fun withoutLxFeaturedArtist(title: String, artist: String): String {
    val suffix = Regex("(?i)\\s+(?:feat\\.?|ft\\.?)\\s*(.+?)\\s*$").find(title) ?: return title
    val featured = normalizeLxText(suffix.groupValues[1])
    if (featured.isBlank() || !normalizeLxText(artist).contains(featured)) return title
    return title.substring(0, suffix.range.first).trim()
}


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
