package com.lladlam.melox.ui.player

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.lyrics.LyricTimelineProcessor
import com.lladlam.melox.core.lyrics.AmlldbLyricsClient
import com.lladlam.melox.core.lyrics.BoundLyricSource
import com.lladlam.melox.core.lyrics.LyricBinding
import com.lladlam.melox.core.lyrics.LyricBindingStore
import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.provider.LyricsCapability
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.core.music.provider.SearchCapability
import com.lladlam.melox.core.provider.bilibili.BilibiliLyricAlignment
import com.lladlam.melox.core.provider.bilibili.BilibiliLyricSourceResult
import com.lladlam.melox.core.provider.bilibili.BilibiliTitleCandidate
import com.lladlam.melox.core.provider.bilibili.BilibiliPlaybackAssociation
import com.lladlam.melox.core.provider.bilibili.BilibiliPlaybackAssociationStore
import com.lladlam.melox.core.provider.bilibili.BilibiliProvider
import com.lladlam.melox.core.provider.local.LocalMusicRepository
import com.lladlam.melox.playback.LxUserPlaybackResolver
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.playback.PlaybackTrackIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences

/**
 * Single Now Playing lyric data entry point for every visual lyric style.
 *
 * Provider/network work is never performed by the rendering frame loop. The
 * loader owns in-flight work as well as the small LRU cache, so metadata updates
 * cannot cancel and restart the same lyric download/decryption while the renderer
 * is already animating. Apple Music / EVA / TextPV / Skyline all receive the same
 * stable [LyricsDocument] instance for one media identity.
 */
private const val AutomaticSelectionCacheVersion = 6

internal object MeloXProviderLyricsLoader {
    private const val MaxCachedDocuments = 24
    private const val TotalLoadTimeoutMs = 45_000L
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val inFlight = mutableMapOf<String, Deferred<LyricsDocument>>()
    private var preloadJob: Job? = null
    private val cache = object : LinkedHashMap<String, LyricsDocument>(MaxCachedDocuments, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsDocument>?): Boolean =
            size > MaxCachedDocuments
    }

    private fun cached(key: String): LyricsDocument? = synchronized(lock) { cache[key] }

    private fun remember(key: String, document: LyricsDocument): LyricsDocument = synchronized(lock) {
        cache[key] = document
        document
    }

    suspend fun load(
        context: Context,
        state: MeloXPlaybackUiState,
    ): LyricsDocument {
        val appContext = context.applicationContext
        val mediaId = state.mediaId?.takeIf(String::isNotBlank)
            ?: return LyricsDocument(emptyList())
        val resourceId = PlaybackTrackIdentity.decode(mediaId)
            ?: return LyricsDocument(emptyList())
        val localRecord = if (resourceId.source == MusicSource.Local) {
            LocalMusicRepository(appContext).track(resourceId.value)
        } else null
        // Local has no independent online lyric catalog. Always use the common
        // AMLL -> QQ -> NetEase -> persisted-local chain, even when automatic
        // selection is disabled for ordinary provider tracks.
        val auto = MeloXSettingsRuntime.automaticLyricSelectionEnabled ||
            resourceId.source == MusicSource.Local
        val bilibiliAlignment = resourceId.source == MusicSource.Bilibili &&
            MeloXSettingsPreferences.boolean(appContext, "bilibili_lyric_audio_alignment", false)
        val binding = if (auto && MeloXSettingsRuntime.lyricStrongBindingEnabled) {
            LyricBindingStore.read(appContext, resourceId)
        } else null
        val cacheKey = lyricCacheKey(
            resourceId = resourceId,
            title = state.title,
            artist = state.artist,
            durationMs = state.durationMs,
            automaticSelection = auto,
            bilibiliAlignment = bilibiliAlignment,
            binding = binding,
        )
        cached(cacheKey)?.let { return it }

        // Snapshot Compose/player state before handing work to the IO scope. This
        // avoids reading rapidly changing snapshot state from the worker thread.
        val snapshot = LyricTrackSnapshot(
            resourceId = resourceId,
            title = localRecord?.recognizedTitle ?: localRecord?.title ?: state.title,
            artist = localRecord?.recognizedArtist ?: localRecord?.artist ?: state.artist,
            album = localRecord?.recognizedAlbum ?: localRecord?.album ?: state.album,
            artworkUrl = localRecord?.recognizedArtworkUrl ?: localRecord?.artworkUri ?: state.artworkUrl,
            durationMs = state.durationMs,
            automaticSelection = auto,
            bilibiliAlignment = bilibiliAlignment,
            binding = binding,
        )
        return loadSnapshot(appContext, cacheKey, snapshot)
    }

    fun preloadQueue(context: Context, state: MeloXPlaybackUiState, count: Int = 2) {
        val appContext = context.applicationContext
        val automaticSelectionEnabled = MeloXSettingsRuntime.automaticLyricSelectionEnabled
        val bilibiliAlignment = MeloXSettingsPreferences.boolean(appContext, "bilibili_lyric_audio_alignment", false)
        val snapshots = state.queue
            .drop((state.currentIndex + 1).coerceAtLeast(0))
            .take(count.coerceIn(0, 4))
            .mapNotNull { entry ->
                val resourceId = PlaybackTrackIdentity.decode(entry.mediaId) ?: return@mapNotNull null
                val automaticSelection = automaticSelectionEnabled || resourceId.source == MusicSource.Local
                // Bilibili matching can issue several catalog requests. Do not
                // preload it without the active decoder duration.
                if (resourceId.source == MusicSource.Bilibili && entry.durationMs <= 0L) return@mapNotNull null
                LyricTrackSnapshot(
                    resourceId = resourceId,
                    title = entry.title,
                    artist = entry.artist,
                    album = "",
                    artworkUrl = entry.artworkUrl,
                    durationMs = entry.durationMs,
                    automaticSelection = automaticSelection,
                    bilibiliAlignment = resourceId.source == MusicSource.Bilibili && bilibiliAlignment,
                    binding = if (automaticSelection && MeloXSettingsRuntime.lyricStrongBindingEnabled) {
                        LyricBindingStore.read(appContext, resourceId)
                    } else null,
                )
            }
            .distinctBy { "${it.resourceId.source.storageValue}:${it.resourceId.value}" }
        preloadJob?.cancel()
        if (snapshots.isEmpty()) return
        preloadJob = workerScope.launch {
            for (snapshot in snapshots) {
                val cacheKey = snapshot.cacheKey()
                if (cached(cacheKey) == null) {
                    runCatching { loadSnapshot(appContext, cacheKey, snapshot) }
                }
            }
        }
    }

    private suspend fun loadSnapshot(
        appContext: Context,
        cacheKey: String,
        snapshot: LyricTrackSnapshot,
    ): LyricsDocument {
        cached(cacheKey)?.let { return it }
        val deferred = synchronized(lock) {
            cache[cacheKey]?.let { return it }
            inFlight[cacheKey] ?: workerScope.async {
                LyricTimelineProcessor.process(loadDocument(appContext, snapshot)).also { document ->
                    if (document.lines.isNotEmpty()) remember(cacheKey, document)
                }
            }.also { created ->
                inFlight[cacheKey] = created
                created.invokeOnCompletion {
                    synchronized(lock) {
                        if (inFlight[cacheKey] === created) inFlight.remove(cacheKey)
                    }
                }
            }
        }
        return withTimeoutOrNull(TotalLoadTimeoutMs) { deferred.await() } ?: run {
            synchronized(lock) {
                if (inFlight[cacheKey] === deferred) inFlight.remove(cacheKey)
            }
            Log.w("MeloXLyricsAuto", "Lyrics load timed out for $cacheKey")
            LyricsDocument(emptyList())
        }
    }

    private suspend fun loadDocument(
        appContext: Context,
        snapshot: LyricTrackSnapshot,
    ): LyricsDocument {
        if (snapshot.resourceId.source == MusicSource.Bilibili) {
            val policy = bilibiliLyricLoadPolicy(
                automaticSelection = snapshot.automaticSelection,
                alignmentEnabled = snapshot.bilibiliAlignment,
                durationMs = snapshot.durationMs,
            )
            if (policy.loadMatchedLyrics) return loadBilibiliMatched(appContext, snapshot)
            if (snapshot.automaticSelection) return LyricsDocument(emptyList())
        }
        if (snapshot.automaticSelection) {
            snapshot.binding?.let { binding ->
                val bound = loadBinding(appContext, binding)
                // Bindings created by the former source-first algorithm may
                // point at line-timed NetEase lyrics. Do not let such a stale
                // binding suppress a newly available QRC/YRC word timeline.
                if (hasWordTiming(bound)) return bound
            }
            return loadAutomatic(appContext, snapshot)
        }
        return loadCurrentProvider(appContext, snapshot)
    }

    private suspend fun loadBilibiliMatched(
        appContext: Context,
        snapshot: LyricTrackSnapshot,
    ): LyricsDocument = coroutineScope {
        if (snapshot.durationMs <= 0L) return@coroutineScope LyricsDocument(emptyList())
        val titleCandidates = BilibiliLyricAlignment.extractTitleCandidates(snapshot.title, snapshot.artist)
        Log.i(
            "MeloXBilibiliLyrics",
            "start id=${snapshot.resourceId.value} duration=${snapshot.durationMs} " +
                "candidates=${titleCandidates.joinToString(" | ") { candidate ->
                    candidate.title + candidate.artist?.let { " / $it" }.orEmpty()
                }}",
        )
        if (titleCandidates.isEmpty()) return@coroutineScope LyricsDocument(emptyList())
        val focusedCandidates = titleCandidates.take(3)
        val registry = MeloXMusicProviders.create(appContext)
        val neteaseProvider = registry.require(MusicSource.Netease)
        val qqProvider = registry.require(MusicSource.QQMusic)
        val neteaseSearch = neteaseProvider as? SearchCapability ?: return@coroutineScope LyricsDocument(emptyList())
        val neteaseLyrics = neteaseProvider as? LyricsCapability ?: return@coroutineScope LyricsDocument(emptyList())
        val qqSearch = qqProvider as? SearchCapability ?: return@coroutineScope LyricsDocument(emptyList())
        val qqLyrics = qqProvider as? LyricsCapability ?: return@coroutineScope LyricsDocument(emptyList())

        // Initial playback may use AMLL or QQ only. NetEase catalog lookup is
        // needed to address AMLL, but NetEase's own lyric endpoint is reserved
        // for the later three-provider duration verification.
        val qqResult = async {
            val track = findBilibiliLyricCatalogTrack(qqSearch, focusedCandidates) ?: return@async null
            runCatching { qqLyrics.lyrics(track) }
                .onFailure { Log.w("MeloXBilibiliLyrics", "QQ primary lyric request failed: ${it.message}") }
                .getOrNull()?.takeIf { it.lines.isNotEmpty() }
                ?.let { BilibiliLyricSourceResult("qq", it, track) }
        }
        val neteaseTrackResult = async {
            findBilibiliLyricCatalogTrack(neteaseSearch, focusedCandidates)
        }
        val amllResult = async {
            val track = neteaseTrackResult.await() ?: return@async null
            val id = track.id.value.toLongOrNull() ?: return@async null
            runCatching { AmlldbLyricsClient().lyrics(id) }
                .onFailure { Log.w("MeloXBilibiliLyrics", "AMLL primary lyric failed: ${it.message}") }
                .getOrNull()?.takeIf { it.lines.isNotEmpty() }
                ?.let { BilibiliLyricSourceResult("amll", it, track) }
        }
        val qq = qqResult.await()
        val amll = amllResult.await()
        val primaryResults = listOfNotNull(amll, qq)
        val primaryDocument = selectAutomaticLyrics(
            primaryResults.mapIndexed { priority, result -> AutoLyricCandidate(priority, result.document) },
        )
        val primary = primaryResults.firstOrNull { it.document === primaryDocument || it.document == primaryDocument }
        Log.i(
            "MeloXBilibiliLyrics",
            "primary=${primary?.source ?: "none"} lines=${primary?.document?.lines?.size ?: 0}",
        )
        if (primary == null || primary.document.lines.size <= 1) {
            return@coroutineScope primary?.document ?: LyricsDocument(emptyList())
        }
        val primaryDuration = BilibiliLyricAlignment.effectiveDuration(primary.document)
        val primaryMismatch = primaryDuration != null &&
            BilibiliLyricAlignment.audioClearlyMismatches(snapshot.durationMs, primaryDuration.durationMs)
        Log.i(
            "MeloXBilibiliLyrics",
            "primaryDuration=${primaryDuration?.durationMs} confidence=${primaryDuration?.confidence} " +
                "audio=${snapshot.durationMs} mismatch=$primaryMismatch",
        )
        if (primaryDuration != null &&
            !primaryMismatch
        ) return@coroutineScope primary.document

        // Once AMLL/QQ proves the audio duration is wrong, use the standardized
        // catalog identity for one NetEase match. Only now is NetEase lyric data
        // enabled, and all three timelines are compared before any association.
        val identityTrack = qq?.catalogTrack ?: primary.catalogTrack
        val canonicalCandidate = BilibiliTitleCandidate(
            title = identityTrack.title,
            artist = identityTrack.artistText.takeUnless { it == "未知歌手" },
        )
        val initiallyMatchedNetease = neteaseTrackResult.await()
        val neteaseTrack = initiallyMatchedNetease
            ?.takeIf { BilibiliLyricAlignment.isSafeCatalogMatch(it, canonicalCandidate) }
            ?: findBilibiliLyricCatalogTrack(neteaseSearch, listOf(canonicalCandidate))
        val neteaseResult = async {
            val track = neteaseTrack ?: return@async null
            runCatching { neteaseLyrics.lyrics(track) }
                .onFailure { Log.w("MeloXBilibiliLyrics", "NetEase verification lyric failed: ${it.message}") }
                .getOrNull()?.takeIf { it.lines.isNotEmpty() }
                ?.let { BilibiliLyricSourceResult("netease", it, track) }
        }
        val verificationAmllResult = async {
            val track = neteaseTrack ?: return@async null
            val id = track.id.value.toLongOrNull() ?: return@async null
            runCatching { AmlldbLyricsClient().lyrics(id) }
                .onFailure { Log.w("MeloXBilibiliLyrics", "AMLL verification lyric failed: ${it.message}") }
                .getOrNull()?.takeIf { it.lines.isNotEmpty() }
                ?.let { BilibiliLyricSourceResult("amll", it, track) }
        }
        val netease = neteaseResult.await()
        val verificationAmll = amll ?: verificationAmllResult.await()
        val results = listOfNotNull(
            verificationAmll,
            qq,
            netease,
        ).distinctBy(BilibiliLyricSourceResult::source)
        val selectedLyrics = selectAutomaticLyrics(
            results.ifEmpty { listOf(primary) }
                .mapIndexed { priority, result -> AutoLyricCandidate(priority, result.document) },
        ) ?: LyricsDocument(emptyList())
        val consensusDuration = BilibiliLyricAlignment.consensus(results)
        Log.i(
            "MeloXBilibiliLyrics",
            "verification=${results.joinToString { result ->
                val duration = BilibiliLyricAlignment.effectiveDuration(result.document)
                "${result.source}:${duration?.durationMs ?: "none"}:${duration?.confidence ?: "none"}"
            }} consensus=$consensusDuration " +
                "audio=${snapshot.durationMs} selectedLines=${selectedLyrics.lines.size}",
        )
        if (consensusDuration != null &&
            bilibiliLyricLoadPolicy(
                snapshot.automaticSelection,
                snapshot.bilibiliAlignment,
                snapshot.durationMs,
            ).saveReplacementAssociation &&
            BilibiliLyricAlignment.audioClearlyMismatches(snapshot.durationMs, consensusDuration)
        ) {
            val original = BilibiliProvider.parseIdentity(snapshot.resourceId.value)
            val canonical = canonicalCandidate
            val bilibili = registry.require(MusicSource.Bilibili) as? BilibiliProvider
            if (original != null && bilibili != null) {
                val query = listOf(canonical.title, canonical.artist).filterNotNull().filter(String::isNotBlank).joinToString(" ")
                val candidates = runCatching { bilibili.searchReplacementCandidates(query, 20) }.getOrDefault(emptyList())
                BilibiliLyricAlignment.selectReplacement(
                    candidates = candidates,
                    originalIdentity = snapshot.resourceId.value,
                    title = canonical.title,
                    artist = canonical.artist,
                    consensusDurationMs = consensusDuration,
                )?.track?.let { replacement ->
                    BilibiliProvider.parseIdentity(replacement.id.value)?.let { physical ->
                        val changed = BilibiliPlaybackAssociationStore.writeIfChanged(
                            appContext,
                            BilibiliPlaybackAssociation(
                                originalBvid = original.first,
                                originalCid = original.second,
                                replacementBvid = physical.first,
                                replacementCid = physical.second,
                                title = canonical.title,
                                consensusDurationMs = consensusDuration,
                                replacementCatalogDurationMs = replacement.durationMs,
                            ),
                        )
                        if (changed) {
                            Log.i(
                                "MeloXBilibiliLyrics",
                                "Saved replacement association for ${snapshot.resourceId.value}; it will apply on the next source open",
                            )
                        }
                    }
                }
            }
        }
        selectedLyrics
    }

    private suspend fun findBilibiliLyricCatalogTrack(
        search: SearchCapability,
        candidates: List<com.lladlam.melox.core.provider.bilibili.BilibiliTitleCandidate>,
    ): MusicTrack? {
        for (candidate in candidates) {
            val query = listOf(candidate.title, candidate.artist).filterNotNull().filter(String::isNotBlank).joinToString(" ")
            val rawMatches = runCatching { search.searchSongs(query, 1, 12).items }
                .onFailure {
                    Log.w("MeloXBilibiliLyrics", "catalog search failed query=$query error=${it.message}")
                }
                .getOrDefault(emptyList())
            val matches = rawMatches
                .filter { BilibiliLyricAlignment.isSafeCatalogMatch(it, candidate) }
            Log.i(
                "MeloXBilibiliLyrics",
                "catalog query=$query raw=${rawMatches.size} safe=${matches.size} " +
                    "top=${rawMatches.take(3).joinToString(" | ") { it.title }}",
            )
            val best = matches.maxByOrNull { track ->
                val exact = BilibiliLyricAlignment.normalize(track.title) == BilibiliLyricAlignment.normalize(candidate.title)
                (if (exact) 100 else 50) + if (track.artists.isNotEmpty()) 10 else 0
            }
            if (best != null) return best
        }
        return null
    }

    private suspend fun loadAutomatic(
        appContext: Context,
        snapshot: LyricTrackSnapshot,
    ): LyricsDocument = coroutineScope {
        val orderedSources = automaticLyricSourcesFor(snapshot.resourceId.source)
        val candidates = orderedSources.mapIndexed { index, source ->
            async {
                val priority = index + 1
                val startedAt = SystemClock.elapsedRealtime()
                val timeoutMs = when (source) {
                    LyricAutoSource.AmlL -> 12_000L
                    LyricAutoSource.QQMusic -> 30_000L
                    LyricAutoSource.Netease,
                    LyricAutoSource.Current -> 15_000L
                }
                val resolved = withTimeoutOrNull(timeoutMs) {
                    loadMatchedSource(appContext, snapshot, source)
                } ?: ResolvedLyrics(LyricsDocument(emptyList()), null)
                Log.d(
                    "MeloXLyricsAuto",
                    "source=$source elapsed=${SystemClock.elapsedRealtime() - startedAt}ms " +
                        "lines=${resolved.document.lines.size} " +
                        "wordLines=${resolved.document.lines.count { it.syllables.isNotEmpty() }} " +
                        "timeout=${resolved.document.lines.isEmpty() && SystemClock.elapsedRealtime() - startedAt >= timeoutMs}",
                )
                AutoLyricCandidate(priority, resolved.document, resolved.binding)
            }
        }.awaitAll()
        val selected = selectAutomaticLyricCandidate(candidates)
        Log.d(
            "MeloXLyricsAuto",
            "selectedPriority=${selected?.priority} lines=${selected?.document?.lines?.size ?: 0} " +
                "wordLines=${selected?.document?.lines?.count { it.syllables.isNotEmpty() } ?: 0}",
        )
        if (selected != null) {
            if (MeloXSettingsRuntime.lyricStrongBindingEnabled) {
                selected.binding?.let { LyricBindingStore.write(appContext, snapshot.resourceId, it) }
            }
            selected.document
        } else {
            LyricsDocument(emptyList())
        }
    }

    private suspend fun loadMatchedSource(
        appContext: Context,
        snapshot: LyricTrackSnapshot,
        source: LyricAutoSource,
    ): ResolvedLyrics {
        if (source == LyricAutoSource.AmlL) {
            val id = snapshot.resourceId.takeIf { it.source == MusicSource.Netease }?.value?.toLongOrNull()
                ?: snapshot.resourceId.takeIf { it.source == MusicSource.Local }
                    ?.let { LocalMusicRepository(appContext).track(it.value)?.recognizedNeteaseId }
                ?: findMatchedNeteaseTrack(appContext, snapshot)?.id?.value?.toLongOrNull()
                ?: return ResolvedLyrics.Empty
            val document = runCatching { AmlldbLyricsClient().lyrics(id) }.getOrDefault(LyricsDocument(emptyList()))
            return ResolvedLyrics(
                document,
                document.takeIf { it.lines.isNotEmpty() }?.let {
                    LyricBinding(BoundLyricSource.AmlL, resourceValue = id.toString(), title = snapshot.title, artist = snapshot.artist, durationMs = snapshot.durationMs)
                },
            )
        }
        if (source == LyricAutoSource.Current) {
            val document = loadCurrentProvider(appContext, snapshot)
            return ResolvedLyrics(document, document.takeIf { it.lines.isNotEmpty() }?.let {
                LyricBinding(BoundLyricSource.Provider, snapshot.resourceId.source, snapshot.resourceId.value, snapshot.title, snapshot.artist, snapshot.durationMs)
            })
        }
        val musicSource = when (source) {
            LyricAutoSource.QQMusic -> MusicSource.QQMusic
            LyricAutoSource.Netease -> MusicSource.Netease
            LyricAutoSource.Current -> snapshot.resourceId.source
        }
        if (musicSource == snapshot.resourceId.source) {
            val document = loadCurrentProvider(appContext, snapshot)
            return ResolvedLyrics(document, document.takeIf { it.lines.isNotEmpty() }?.let {
                LyricBinding(BoundLyricSource.Provider, musicSource, snapshot.resourceId.value, snapshot.title, snapshot.artist, snapshot.durationMs)
            })
        }
        val registry = MeloXMusicProviders.create(appContext)
        val provider = registry.require(musicSource)
        val search = provider as? SearchCapability ?: return ResolvedLyrics.Empty
        val lyrics = provider as? LyricsCapability ?: return ResolvedLyrics.Empty
        val queries = buildList {
            val clean = snapshot.title.replace(Regex("[（(].*?[）)]"), "").trim()
            if (clean.isNotBlank()) add(clean)
            add(snapshot.title)
            snapshot.artist.substringBefore(" /").takeIf(String::isNotBlank)?.let { artist ->
                add("$clean $artist".trim())
            }
        }.distinct()
        for (query in queries) {
            val results = runCatching { search.searchSongs(query, 1, 10).items }.getOrDefault(emptyList())
            val match = results
                .filter { candidate ->
                    isSafeCrossProviderLyricMatch(
                        targetTitle = snapshot.title,
                        targetArtist = snapshot.artist,
                        targetDurationMs = snapshot.durationMs,
                        candidate = candidate,
                    )
                }
                .maxByOrNull { candidate -> trackMatchScore(snapshot, candidate) }
                ?: continue
            val document = runCatching { lyrics.lyrics(match) }.getOrNull()
            if (document != null && document.lines.isNotEmpty()) {
                return ResolvedLyrics(
                    document,
                    LyricBinding(BoundLyricSource.Provider, musicSource, match.id.value, match.title, match.artistText, match.durationMs ?: snapshot.durationMs),
                )
            }
        }
        return ResolvedLyrics.Empty
    }

    private suspend fun loadBinding(appContext: Context, binding: LyricBinding): LyricsDocument {
        if (binding.source == BoundLyricSource.AmlL) {
            val id = binding.resourceValue.toLongOrNull() ?: return LyricsDocument(emptyList())
            return runCatching { AmlldbLyricsClient().lyrics(id) }.getOrDefault(LyricsDocument(emptyList()))
        }
        val providerSource = binding.provider ?: return LyricsDocument(emptyList())
        val provider = MeloXMusicProviders.create(appContext).require(providerSource)
        val lyrics = provider as? LyricsCapability ?: return LyricsDocument(emptyList())
        val track = MusicTrack(
            id = MusicResourceId(providerSource, binding.resourceValue),
            title = binding.title,
            artists = binding.artist.split(Regex("\\s*(?:、|/|&|,|;|；)\\s*")).filter(String::isNotBlank).map { MusicArtistRef(name = it) },
            durationMs = binding.durationMs.takeIf { it > 0L },
        )
        return runCatching { lyrics.lyrics(track) }.getOrDefault(LyricsDocument(emptyList()))
    }

    private suspend fun findMatchedNeteaseTrack(appContext: Context, snapshot: LyricTrackSnapshot): MusicTrack? {
        val provider = MeloXMusicProviders.create(appContext).require(MusicSource.Netease)
        val search = provider as? SearchCapability ?: return null
        val queries = listOf(snapshot.title, "${snapshot.title} ${snapshot.artist.substringBefore(" /")}").distinct()
        for (query in queries) {
            val match = runCatching { search.searchSongs(query, 1, 10).items }.getOrDefault(emptyList())
                .filter { isSafeCrossProviderLyricMatch(snapshot.title, snapshot.artist, snapshot.durationMs, it) }
                .maxByOrNull { trackMatchScore(snapshot, it) }
            if (match != null) return match
        }
        return null
    }

    private suspend fun loadCurrentProvider(
        appContext: Context,
        snapshot: LyricTrackSnapshot,
    ): LyricsDocument {
        val resourceId = snapshot.resourceId
        if (resourceId.source == MusicSource.Netease) {
            val songId = resourceId.value.toLongOrNull()
                ?: return LyricsDocument(emptyList())
            return MeloXDownloadStore.get(appContext).localLyrics(songId)
                ?: NeteaseSearchClient(
                    cookieProvider = { NeteaseSessionStore.readCookie(appContext) },
                ).lyrics(songId)
        }

        val provider = MeloXMusicProviders.create(appContext).require(resourceId.source)
        val lyricCapability = provider as? LyricsCapability
            ?: return LyricsDocument(emptyList())
        val artistRefs = snapshot.artist
            .split(Regex("\\s*(?:、|/|&|,|;|；)\\s*"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .ifEmpty { listOf("未知歌手") }
            .map { MusicArtistRef(name = it) }
        val album = snapshot.album.takeIf(String::isNotBlank)?.let {
            MusicAlbumRef(
                name = it,
                artworkUrl = snapshot.artworkUrl,
            )
        }
        val track = MusicTrack(
            id = resourceId,
            title = snapshot.title.ifBlank { "未知歌曲" },
            artists = artistRefs,
            album = album,
            artworkUrl = snapshot.artworkUrl,
            durationMs = snapshot.durationMs.takeIf { it > 0L },
        )
        // LX V5 sources may provide lyrics even when the native provider does
        // not. Try the user-source action before native provider fallbacks.
        runCatching { LxUserPlaybackResolver(appContext).resolveLyrics(track) }
            .getOrNull()
            ?.takeIf { it.lines.isNotEmpty() }
            ?.let { return it }
        return lyricCapability.lyrics(track)
    }

    private fun trackMatchScore(snapshot: LyricTrackSnapshot, candidate: MusicTrack): Int {
        val artist = normalizeLyricMatchText(snapshot.artist)
        val candidateArtist = normalizeLyricMatchText(candidate.artistText)
        return 100 +
            (if (artist.isNotBlank() && (candidateArtist.contains(artist) || artist.contains(candidateArtist))) 25 else 0) -
            (if (snapshot.durationMs > 0L) {
                (candidate.durationMs?.let { kotlin.math.abs(snapshot.durationMs - it) } ?: 0L) / 1_000L
            } else 0L).toInt()
    }

    private data class LyricTrackSnapshot(
        val resourceId: MusicResourceId,
        val title: String,
        val artist: String,
        val album: String,
        val artworkUrl: String?,
        val durationMs: Long,
        val automaticSelection: Boolean,
        val bilibiliAlignment: Boolean,
        val binding: LyricBinding?,
    ) {
        fun cacheKey(): String = lyricCacheKey(
            resourceId = resourceId,
            title = title,
            artist = artist,
            durationMs = durationMs,
            automaticSelection = automaticSelection,
            bilibiliAlignment = bilibiliAlignment,
            binding = binding,
        )
    }

    private data class ResolvedLyrics(val document: LyricsDocument, val binding: LyricBinding?) {
        companion object { val Empty = ResolvedLyrics(LyricsDocument(emptyList()), null) }
    }
}

internal data class AutoLyricCandidate(val priority: Int, val document: LyricsDocument, val binding: LyricBinding? = null)

internal enum class LyricAutoSource { AmlL, QQMusic, Netease, Current }

internal fun automaticLyricSourcesFor(source: MusicSource): List<LyricAutoSource> = buildList {
    add(LyricAutoSource.AmlL)
    if (source == MusicSource.QQMusic) {
        add(LyricAutoSource.Current)
        add(LyricAutoSource.Netease)
    } else {
        add(LyricAutoSource.QQMusic)
        if (source == MusicSource.Netease) {
            add(LyricAutoSource.Current)
        } else {
            add(LyricAutoSource.Netease)
            add(LyricAutoSource.Current)
        }
    }
}.distinct()

internal fun selectAutomaticLyrics(candidates: List<AutoLyricCandidate>): LyricsDocument? =
    selectAutomaticLyricCandidate(candidates)?.document

internal fun selectAutomaticLyricCandidate(candidates: List<AutoLyricCandidate>): AutoLyricCandidate? {
    // AMLL's authored word timing is the highest-quality result regardless of
    // which provider is currently selected. Do not let a provider-priority tie
    // break replace it with a different timed document.
    candidates.firstOrNull {
        it.document.source == com.lladlam.melox.core.lyrics.LyricSource.AmlL &&
            it.document.lines.isNotEmpty() &&
            hasWordTiming(it.document)
    }?.let { return it }
    candidates.firstOrNull { it.priority == 0 && it.document.lines.isNotEmpty() }?.let { return it }
    return selectBestQualityCandidate(
        candidates.filter { it.document.lines.isNotEmpty() && hasWordTiming(it.document) }
            .ifEmpty { candidates.filter { it.document.lines.isNotEmpty() } },
    )
}

private fun selectBestQualityCandidate(candidates: List<AutoLyricCandidate>): AutoLyricCandidate? {
    val bestScore = candidates.maxOfOrNull { lyricQualityScore(it.document) } ?: return null
    val nearQualityWindow = maxOf(10, bestScore / 20)
    return candidates
        .filter { bestScore - lyricQualityScore(it.document) <= nearQualityWindow }
        .minByOrNull(AutoLyricCandidate::priority)
}

private fun hasWordTiming(document: LyricsDocument): Boolean =
    document.lines.any { line -> line.syllables.isNotEmpty() }

internal fun lyricQualityScore(document: LyricsDocument): Int =
    document.lines.count { it.syllables.isNotEmpty() } * 100 +
        document.lines.count { !it.translation.isNullOrBlank() } * 10 +
        document.lines.count { !it.romanization.isNullOrBlank() } * 5 +
        document.lines.size

internal enum class LyricTrackVersion { Original, Live, Dj, Remix, Instrumental }

internal data class LyricTitleIdentity(
    val baseTitle: String,
    val version: LyricTrackVersion,
    val versionLabel: String,
)

internal fun lyricTitleIdentity(title: String): LyricTitleIdentity {
    val normalized = title.lowercase()
    val version = when {
        Regex("(?:^|[^a-z])dj(?:[^a-z]|$)|电音|舞曲").containsMatchIn(normalized) -> LyricTrackVersion.Dj
        Regex("remix|重混|混音版").containsMatchIn(normalized) -> LyricTrackVersion.Remix
        Regex("live|现场|演唱会|音乐节").containsMatchIn(normalized) -> LyricTrackVersion.Live
        Regex("instrumental|伴奏|纯音乐|off vocal|karaoke").containsMatchIn(normalized) -> LyricTrackVersion.Instrumental
        else -> LyricTrackVersion.Original
    }
    val versionPattern = Regex(
        "(?i)(?:[（(【\\[]?\\s*(?:dj(?:\\s+version)?|[^）)】\\]]*remix|live|现场版?|演唱会版?|音乐节版?|instrumental|伴奏版?|纯音乐|off\\s*vocal|karaoke)\\s*[）)】\\]]?)",
    )
    val base = normalizeLyricMatchText(title.replace(versionPattern, ""))
    val label = normalizeLyricMatchText(title).removePrefix(base)
    return LyricTitleIdentity(base, version, label)
}

internal fun isSafeCrossProviderLyricMatch(
    targetTitle: String,
    targetArtist: String,
    targetDurationMs: Long,
    candidate: MusicTrack,
): Boolean {
    val target = lyricTitleIdentity(targetTitle)
    val other = lyricTitleIdentity(candidate.title)
    if (target.baseTitle.isBlank() || target.baseTitle != other.baseTitle) return false
    if (target.version != other.version) return false
    if (target.version != LyricTrackVersion.Original && target.versionLabel != other.versionLabel) return false
    if (targetDurationMs > 0L) {
        val candidateDuration = candidate.durationMs ?: return false
        val toleranceMs = if (target.version == LyricTrackVersion.Original) 2_000L else 1_000L
        if (kotlin.math.abs(targetDurationMs - candidateDuration) > toleranceMs) return false
    }
    val targetArtistKey = normalizeLyricMatchText(targetArtist.substringBefore(" /"))
    val candidateArtistKey = normalizeLyricMatchText(candidate.artistText)
    return targetArtistKey.isBlank() || candidateArtistKey.contains(targetArtistKey) || targetArtistKey.contains(candidateArtistKey)
}

internal fun normalizeLyricMatchText(value: String): String = value.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]"), "")

internal fun lyricCacheKey(
    resourceId: MusicResourceId,
    title: String,
    artist: String,
    durationMs: Long,
    automaticSelection: Boolean,
    bilibiliAlignment: Boolean = false,
    binding: LyricBinding? = null,
): String = buildString {
    append(if (automaticSelection) "auto-v$AutomaticSelectionCacheVersion" else "provider")
    append(':').append(resourceId.source.storageValue).append(':').append(resourceId.value)
    if (automaticSelection) {
        append(":title:").append(normalizeLyricMatchText(title))
        append(":artist:").append(normalizeLyricMatchText(artist))
        append(":duration:").append(
            durationMs.takeIf { it > 0L }?.let { ((it + 500L) / 1_000L) * 1_000L } ?: "unknown",
        )
    }
    if (resourceId.source == MusicSource.Bilibili) append(":alignment:").append(bilibiliAlignment)
    binding?.let { append(":bound:").append(it.stableKey()) }
}

internal data class BilibiliLyricLoadPolicy(
    val loadMatchedLyrics: Boolean,
    val saveReplacementAssociation: Boolean,
)

internal fun bilibiliLyricLoadPolicy(
    automaticSelection: Boolean,
    alignmentEnabled: Boolean,
    durationMs: Long,
): BilibiliLyricLoadPolicy = BilibiliLyricLoadPolicy(
    loadMatchedLyrics = automaticSelection && durationMs > 0L,
    saveReplacementAssociation = automaticSelection && alignmentEnabled && durationMs > 0L,
)
