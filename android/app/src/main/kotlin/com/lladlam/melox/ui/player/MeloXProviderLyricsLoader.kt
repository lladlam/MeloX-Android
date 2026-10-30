package com.lladlam.melox.ui.player

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.autosync.AutoSyncCoordinator
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.lyrics.LyricSource
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.lyrics.MeloXLyricPreference
import com.lladlam.melox.core.lyrics.MeloXLyricScript
import com.lladlam.melox.core.lyrics.MeloXLyricScriptConverter
import com.lladlam.melox.core.lyrics.LyricTimelineProcessor
import com.lladlam.melox.core.lyrics.AmlldbLyricQuery
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /**
     * How long a line-timed current-provider lyric waits for an authored
     * word-timed AMLL lyric before falling back. Short on purpose: AMLL mirror
     * lookups usually answer in well under a second, so this only covers the
     * common case while keeping playback snappy.
     */
    private const val AmllWordTimingWindowMs = 2_000L

    /**
     * Background budget for upgrading a line-timed document to an authored
     * word-timed AMLL one after the fast window already fell back. Longer than
     * [AmllWordTimingWindowMs] because the user already has lyrics on screen;
     * this only chases the upgrade.
     */
    private const val AmllUpgradeTimeoutMs = 15_000L

    /**
     * A late upgrade may only replace what is on screen while the track is
     * still near its start; swapping the lyric style mid-song would be
     * jarring. Panels enforce this against the live playback position when the
     * [load] upgrade callback fires, since the loader does not track position.
     */
    internal const val AmllUpgradeWindowMs = 30_000L

    /**
     * Guards the late upgrade against mismatched songs: the AMLL document must
     * not describe a very different track than the one it would replace.
     */
    private const val AmllUpgradeMinLineRatio = 0.5
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

    /**
     * @param onUpgrade optional callback invoked later with a higher-quality
     *   document for the same track (today: a line-timed current-provider lyric
     *   upgraded to an authored word-timed AMLL one). Callers that can refresh
     *   their state should pass it; the fast return value is unaffected.
     */
    suspend fun load(
        context: Context,
        state: MeloXPlaybackUiState,
        onUpgrade: ((LyricsDocument) -> Unit)? = null,
    ): LyricsDocument = load(
        context = context,
        mediaId = state.mediaId,
        title = state.title,
        artist = state.artist,
        album = state.album,
        artworkUrl = state.artworkUrl,
        durationMs = state.durationMs,
        onUpgrade = onUpgrade,
    )

    /**
     * Value-based variant used by system lyrics and the floating overlay. It
     * shares the exact provider matching, fallbacks and cache with the in-app
     * lyrics panel so every source gets identical lyrics.
     */
    suspend fun load(
        context: Context,
        mediaId: String?,
        title: String,
        artist: String,
        album: String,
        artworkUrl: String?,
        durationMs: Long,
        onUpgrade: ((LyricsDocument) -> Unit)? = null,
    ): LyricsDocument {
        val appContext = context.applicationContext
        val resolvedMediaId = mediaId?.takeIf(String::isNotBlank)
            ?: return LyricsDocument(emptyList())
        val resourceId = PlaybackTrackIdentity.decode(resolvedMediaId)
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
        val preference = requestedLyricPreference()
        val cacheKey = lyricCacheKey(
            resourceId = resourceId,
            title = title,
            artist = artist,
            durationMs = durationMs,
            automaticSelection = auto,
            bilibiliAlignment = bilibiliAlignment,
            binding = binding,
            preference = preference,
        )
        cached(cacheKey)?.let { hit ->
            // Queue preloading caches line-timed documents before any caller asks
            // for upgrades. Returning straight here would permanently suppress
            // AutoSync for the track, so a cache hit still gets its pass.
            if (onUpgrade != null) {
                scheduleAutoSyncIfNeeded(
                    appContext,
                    cacheKey,
                    "${resourceId.source.storageValue}:${resourceId.value}",
                    hit,
                    onUpgrade,
                )
            }
            return scripted(hit)
        }

        // Snapshot Compose/player state before handing work to the IO scope. This
        // avoids reading rapidly changing snapshot state from the worker thread.
        val snapshot = LyricTrackSnapshot(
            resourceId = resourceId,
            title = localRecord?.recognizedTitle ?: localRecord?.title ?: title,
            artist = localRecord?.recognizedArtist ?: localRecord?.artist ?: artist,
            album = localRecord?.recognizedAlbum ?: localRecord?.album ?: album,
            artworkUrl = localRecord?.recognizedArtworkUrl ?: localRecord?.artworkUri ?: artworkUrl,
            durationMs = durationMs,
            automaticSelection = auto,
            bilibiliAlignment = bilibiliAlignment,
            binding = binding,
            preference = preference,
        )
        return loadSnapshot(appContext, cacheKey, snapshot, onUpgrade)
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
        onUpgrade: ((LyricsDocument) -> Unit)? = null,
    ): LyricsDocument {
        cached(cacheKey)?.let { hit ->
            if (onUpgrade != null) {
                scheduleAutoSyncIfNeeded(
                    appContext,
                    cacheKey,
                    "${snapshot.resourceId.source.storageValue}:${snapshot.resourceId.value}",
                    hit,
                    onUpgrade,
                )
            }
            return scripted(hit)
        }
        val deferred = synchronized(lock) {
            cache[cacheKey]?.let { return scripted(it) }
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
        val document = withTimeoutOrNull(TotalLoadTimeoutMs) { deferred.await() } ?: run {
            deferred.cancel()
            synchronized(lock) {
                if (inFlight[cacheKey] === deferred) inFlight.remove(cacheKey)
            }
            Log.w("MeloXLyricsAuto", "Lyrics load timed out for $cacheKey")
            throw java.io.IOException("歌词加载超时")
        }
        if (onUpgrade != null) {
            val trackKey = "${snapshot.resourceId.source.storageValue}:${snapshot.resourceId.value}"
            // A timeline generated on an earlier visit is reused with no model
            // work at all; generation only starts when nothing is stored.
            val stored = runCatching {
                AutoSyncCoordinator.applyStored(appContext, document, trackKey)
            }.getOrNull()
            val amllJob = scheduleWordTimingUpgrade(appContext, cacheKey, snapshot, stored ?: document, onUpgrade)
            if (stored != null) {
                remember(cacheKey, stored)
            } else {
                scheduleAutoSyncUpgrade(appContext, cacheKey, trackKey, document, amllJob, onUpgrade)
            }
            return scripted(stored ?: document)
        }
        return scripted(document)
    }

    /**
     * Generates a real word-level timeline locally when the provider only gave
     * line timing, then hands it to [onUpgrade] exactly like the AMLL path.
     *
     * Authored AMLL timing always wins: this waits for [amllJob] first and
     * re-checks the cache, so a successful AMLL upgrade always suppresses
     * generation. Every failure (no model, no cached audio, infeasible
     * alignment) simply leaves the line-timed document on screen.
     */
    private fun scheduleAutoSyncUpgrade(
        appContext: Context,
        cacheKey: String,
        trackKey: String,
        document: LyricsDocument,
        amllJob: Job?,
        onUpgrade: (LyricsDocument) -> Unit,
    ) {
        if (!MeloXSettingsRuntime.autoSyncWordLyricsEnabled) return
        if (document.lines.isEmpty() || hasWordTiming(document)) return
        workerScope.launch {
            amllJob?.join()
            if (!MeloXSettingsRuntime.autoSyncWordLyricsEnabled) return@launch
            // AMLL may have won the race while we waited.
            if (hasWordTiming(cached(cacheKey) ?: document)) return@launch
            val upgraded = runCatching {
                AutoSyncCoordinator.generate(appContext, document, trackKey)
            }.onFailure { Log.w("MeloXLyricsAuto", "autoSync failed for $trackKey", it) }
                .getOrNull() ?: return@launch
            // An authored upgrade or a song change may have arrived during inference.
            if (hasWordTiming(cached(cacheKey) ?: document)) return@launch
            if (com.lladlam.melox.core.autosync.AutoSyncPlaybackBridge.isCurrentTrack?.invoke(trackKey) == false) return@launch
            remember(cacheKey, upgraded)
            Log.d(
                "MeloXLyricsAuto",
                "autoSync applied lines=${upgraded.lines.size} " +
                    "wordLines=${upgraded.lines.count { it.syllables.isNotEmpty() }}",
            )
            withContext(Dispatchers.Main.immediate) { onUpgrade(scripted(upgraded)) }
        }
    }

    /**
     * AutoSync pass for a document that is already in memory.
     *
     * A stored timeline is handed to [onUpgrade] immediately; otherwise
     * generation is started (after the AMLL chase, which always wins). A
     * document that already carries word timing is left untouched.
     */
    private fun scheduleAutoSyncIfNeeded(
        appContext: Context,
        cacheKey: String,
        trackKey: String,
        document: LyricsDocument,
        onUpgrade: (LyricsDocument) -> Unit,
    ) {
        if (document.lines.isEmpty() || hasWordTiming(document)) return
        val stored = runCatching {
            AutoSyncCoordinator.applyStored(appContext, document, trackKey)
        }.getOrNull()
        if (stored != null) {
            remember(cacheKey, stored)
            workerScope.launch {
                withContext(Dispatchers.Main.immediate) { onUpgrade(scripted(stored)) }
            }
            return
        }
        scheduleAutoSyncUpgrade(appContext, cacheKey, trackKey, document, null, onUpgrade)
    }

    /**
     * Chases an authored word-timed AMLL document in the background when the
     * fast path settled for a line-timed one. On success the better document is
     * cached for the track and handed to [onUpgrade] on the main thread so UI
     * state can be updated directly. Every guard lives inside the coroutine so
     * a cancelled or mismatched upgrade simply does nothing.
     */
    private fun scheduleWordTimingUpgrade(
        appContext: Context,
        cacheKey: String,
        snapshot: LyricTrackSnapshot,
        current: LyricsDocument,
        onUpgrade: (LyricsDocument) -> Unit,
    ): Job? {
        // Nothing to chase when the on-screen document already comes from AMLL
        // (it is the target), when the track has no lyrics at all, or when
        // automatic selection is off (an explicit choice is final). A
        // non-AMLL word-timed document is still worth upgrading: authored AMLL
        // timing is generally more precise than provider word timing.
        if (current.lines.isEmpty() ||
            current.source == LyricSource.AmlL ||
            !snapshot.automaticSelection
        ) {
            return null
        }
        return workerScope.launch {
            val upgraded = runCatching {
                withTimeoutOrNull(AmllUpgradeTimeoutMs) {
                    loadMatchedSource(appContext, snapshot, LyricAutoSource.AmlL)
                }
            }.getOrNull()?.takeIf { resolved ->
                resolved.document.lines.isNotEmpty() && hasWordTiming(resolved.document)
            } ?: return@launch

            if (!isUpgradeCompatible(current, upgraded.document)) {
                Log.d(
                    "MeloXLyricsAuto",
                    "amllUpgrade discarded lines=${upgraded.document.lines.size} " +
                        "currentLines=${current.lines.size}",
                )
                return@launch
            }

            val processed = LyricTimelineProcessor.process(upgraded.document)
            remember(cacheKey, processed)
            Log.d(
                "MeloXLyricsAuto",
                "amllUpgrade applied lines=${processed.lines.size} " +
                    "wordLines=${processed.lines.count { it.syllables.isNotEmpty() }}",
            )
            withContext(Dispatchers.Main.immediate) { onUpgrade(scripted(processed)) }
        }
    }

    /**
     * Rejects an upgrade that looks like a different song. Line counts are the
     * cheapest stable signal: a ratio beyond [AmllUpgradeMinLineRatio] is
     * treated as a bad match rather than a legitimate timing upgrade.
     */
    private fun isUpgradeCompatible(current: LyricsDocument, upgraded: LyricsDocument): Boolean {
        if (current.lines.isEmpty()) return true
        val ratio = upgraded.lines.size.toDouble() / current.lines.size.toDouble()
        return ratio >= AmllUpgradeMinLineRatio && ratio <= (1.0 / AmllUpgradeMinLineRatio)
    }
    private fun scripted(document: LyricsDocument): LyricsDocument =
        applyLyricTranslationPreference(document, requestedLyricPreference())

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
                val bound = loadBinding(appContext, binding, snapshot.preference)
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
            runCatching { AmlldbLyricsClient().lyrics(id, snapshot.preference) }
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
            runCatching { AmlldbLyricsClient().lyrics(id, snapshot.preference) }
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
    ): LyricsDocument {
        val orderedSources = automaticLyricSourcesFor(snapshot.resourceId.source)
        // Every source is started together. The current provider still supplies
        // the fast line-timed fallback, but it must not suppress a better
        // word-timed lyric from another provider. AMLL word timing has absolute
        // priority; otherwise word-timed providers beat line-only lyrics.
        //
        // The jobs run on the loader's own scope rather than the caller's: the
        // lyric clients use blocking OkHttp calls that ignore coroutine
        // cancellation, so a structured scope would still wait for the losing
        // sources to finish and defeat the early return.
        val jobs = orderedSources.associateWith { source ->
            workerScope.async { resolveAutomaticSource(appContext, snapshot, source) }
        }
        jobs[LyricAutoSource.Current]?.let { current ->
            val currentResolved = current.await()
            if (currentResolved.document.lines.isNotEmpty()) {
                // If the current source has only line timing, briefly let all
                // cross-source candidates finish so QQ QRC / NetEase YRC can
                // beat it. If it already has word timing, only AMLL can outrank
                // it. Line-timed AMLL never displaces the current document.
                val betterTimed = if (hasWordTiming(currentResolved.document)) {
                    firstWordTimedCandidate(
                        jobs,
                        orderedSources.filter { it == LyricAutoSource.AmlL },
                    )
                } else {
                    firstWordTimedCandidate(
                        jobs,
                        orderedSources.filter { it != LyricAutoSource.Current },
                    )
                }
                val selectedSource = betterTimed?.first ?: LyricAutoSource.Current
                val selected = betterTimed?.second ?: AutoLyricCandidate(
                    priority = 0,
                    document = currentResolved.document,
                    binding = currentResolved.binding,
                )
                jobs.forEach { (source, job) ->
                    if (source != selectedSource) job.cancel()
                }
                Log.d(
                    "MeloXLyricsAuto",
                    "selectedAutomatic source=${selected.document.source} " +
                        "lines=${selected.document.lines.size} " +
                        "wordLines=${selected.document.lines.count { it.syllables.isNotEmpty() }} " +
                        "replacedCurrent=${selected.document != currentResolved.document}",
                )
                return rememberBinding(
                    appContext,
                    snapshot,
                    selected.binding,
                    selected.document,
                )
            }
        }
        val candidates = orderedSources
            .filter { it != LyricAutoSource.Current }
            .mapIndexed { index, source ->
                val resolved = jobs.getValue(source).await()
                AutoLyricCandidate(index + 1, resolved.document, resolved.binding)
            }
        val selected = selectAutomaticLyricCandidate(candidates)
        Log.d(
            "MeloXLyricsAuto",
            "selectedPriority=${selected?.priority} lines=${selected?.document?.lines?.size ?: 0} " +
                "wordLines=${selected?.document?.lines?.count { it.syllables.isNotEmpty() } ?: 0}",
        )
        return selected?.let { rememberBinding(appContext, snapshot, it.binding, it.document) }
            ?: LyricsDocument(emptyList())
    }

    /**
     * Waits up to the fast word-timing window for the requested already-running
     * provider jobs. The jobs complete in any order; selection is made only
     * after the window or all candidates settle, so AMLL can outrank a faster
     * QQ response. Completed results survive a window timeout.
     */
    private suspend fun firstWordTimedCandidate(
        jobs: Map<LyricAutoSource, Deferred<ResolvedLyrics>>,
        sources: List<LyricAutoSource>,
    ): Pair<LyricAutoSource, AutoLyricCandidate>? {
        val candidates = mutableListOf<Pair<LyricAutoSource, AutoLyricCandidate>>()
        val availableSources = sources.filter { jobs[it] != null }
        val completed = Channel<Pair<LyricAutoSource, ResolvedLyrics>>(Channel.UNLIMITED)
        availableSources.forEach { source ->
            workerScope.launch {
                runCatching { jobs.getValue(source).await() }
                    .getOrNull()
                    ?.let { completed.trySend(source to it) }
            }
        }
        try {
            withTimeoutOrNull(AmllWordTimingWindowMs) {
                repeat(availableSources.size) {
                    val (source, resolved) = completed.receive()
                    if (hasWordTiming(resolved.document)) {
                        candidates += source to AutoLyricCandidate(
                            priority = sources.indexOf(source),
                            document = resolved.document,
                            binding = resolved.binding,
                        )
                    }
                }
            }
        } finally {
            completed.close()
        }
        val selected = selectAutomaticLyricCandidate(candidates.map { it.second })
            ?: return null
        return candidates.first { it.second == selected }
    }

    private suspend fun resolveAutomaticSource(
        appContext: Context,
        snapshot: LyricTrackSnapshot,
        source: LyricAutoSource,
    ): ResolvedLyrics {
        val startedAt = SystemClock.elapsedRealtime()
        val timeoutMs = when (source) {
            LyricAutoSource.AmlL -> 12_000L
            LyricAutoSource.QQMusic -> 30_000L
            LyricAutoSource.Netease,
            LyricAutoSource.Current -> 15_000L
        }
        val resolved = runCatching {
            withTimeoutOrNull(timeoutMs) { loadMatchedSource(appContext, snapshot, source) }
        }.getOrNull() ?: ResolvedLyrics(LyricsDocument(emptyList()), null)
        Log.d(
            "MeloXLyricsAuto",
            "source=$source elapsed=${SystemClock.elapsedRealtime() - startedAt}ms " +
                "lines=${resolved.document.lines.size} " +
                "wordLines=${resolved.document.lines.count { it.syllables.isNotEmpty() }} " +
                "timeout=${resolved.document.lines.isEmpty() && SystemClock.elapsedRealtime() - startedAt >= timeoutMs}",
        )
        return resolved
    }

    private fun rememberBinding(
        appContext: Context,
        snapshot: LyricTrackSnapshot,
        binding: LyricBinding?,
        document: LyricsDocument,
    ): LyricsDocument {
        if (MeloXSettingsRuntime.lyricStrongBindingEnabled) {
            binding?.let { LyricBindingStore.write(appContext, snapshot.resourceId, it) }
        }
        return document
    }

    private suspend fun loadMatchedSource(
        appContext: Context,
        snapshot: LyricTrackSnapshot,
        source: LyricAutoSource,
    ): ResolvedLyrics {
        if (source == LyricAutoSource.AmlL) {
            val query = amlldbQuery(appContext, snapshot)
                ?: findMatchedNeteaseTrack(appContext, snapshot)?.id?.value?.toLongOrNull()
                    ?.let(AmlldbLyricQuery::Netease)
                ?: return ResolvedLyrics.Empty
            val document = runCatching { AmlldbLyricsClient().lyrics(query, snapshot.preference) }
                .getOrDefault(LyricsDocument(emptyList()))
            return ResolvedLyrics(
                document,
                document.takeIf { it.lines.isNotEmpty() }?.let {
                    LyricBinding(BoundLyricSource.AmlL, resourceValue = query.bindingValue(), title = snapshot.title, artist = snapshot.artist, durationMs = snapshot.durationMs)
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

    private suspend fun loadBinding(appContext: Context, binding: LyricBinding, preference: MeloXLyricPreference): LyricsDocument {
        if (binding.source == BoundLyricSource.AmlL) {
            val query = amlldbQuery(binding.resourceValue) ?: return LyricsDocument(emptyList())
            return runCatching { AmlldbLyricsClient().lyrics(query, preference) }
                .getOrDefault(LyricsDocument(emptyList()))
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

    private fun amlldbQuery(resourceValue: String): AmlldbLyricQuery? {
        resourceValue.toLongOrNull()?.let { return AmlldbLyricQuery.Netease(it) }
        val separator = resourceValue.indexOf(':')
        if (separator <= 0) return null
        val value = resourceValue.substring(separator + 1).takeIf(String::isNotBlank) ?: return null
        return when (resourceValue.substring(0, separator)) {
            "qq" -> AmlldbLyricQuery.QQ(value)
            "spotify" -> AmlldbLyricQuery.Spotify(value)
            "apple" -> AmlldbLyricQuery.AppleMusic(value)
            else -> null
        }
    }

    private fun amlldbQuery(appContext: Context, snapshot: LyricTrackSnapshot): AmlldbLyricQuery? {
        val id = snapshot.resourceId
        return when (id.source) {
            MusicSource.Netease -> id.value.toLongOrNull()?.let(AmlldbLyricQuery::Netease)
            MusicSource.QQMusic -> id.value.takeIf(String::isNotBlank)?.let(AmlldbLyricQuery::QQ)
            MusicSource.Spotify -> id.value.takeIf(String::isNotBlank)?.let(AmlldbLyricQuery::Spotify)
            MusicSource.AppleMusic -> id.value.takeIf(String::isNotBlank)?.let(AmlldbLyricQuery::AppleMusic)
            MusicSource.Local -> LocalMusicRepository(appContext).track(id.value)
                ?.recognizedNeteaseId
                ?.let(AmlldbLyricQuery::Netease)
            else -> null
        }
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
        val preference: MeloXLyricPreference = requestedLyricPreference(),
    ) {
        fun cacheKey(): String = lyricCacheKey(
            resourceId = resourceId,
            title = title,
            artist = artist,
            durationMs = durationMs,
            automaticSelection = automaticSelection,
            bilibiliAlignment = bilibiliAlignment,
            binding = binding,
            preference = preference,
        )
    }

    private data class ResolvedLyrics(val document: LyricsDocument, val binding: LyricBinding?) {
        companion object { val Empty = ResolvedLyrics(LyricsDocument(emptyList()), null) }
    }
}

internal data class AutoLyricCandidate(val priority: Int, val document: LyricsDocument, val binding: LyricBinding? = null)

internal fun requestedLyricPreference(): MeloXLyricPreference =
    MeloXLyricPreference.fromSetting(MeloXSettingsRuntime.lyricLanguage)

internal fun applyLyricTranslationPreference(document: LyricsDocument, preference: MeloXLyricPreference): LyricsDocument {
    // These endpoints supply Chinese translations, with no target-language API.
    val chineseOnly = document.source in setOf(LyricSource.Netease, LyricSource.QQMusic, LyricSource.Kugou)
    val selected = if (preference.languageTag != null && chineseOnly) document.copy(
        lines = document.lines.map { line ->
            line.copy(translation = null, accompaniment = line.accompaniment.map { it.copy(translation = null) })
        },
    ) else document
    return MeloXLyricScriptConverter.convert(selected, preference.script)
}

internal enum class LyricAutoSource { AmlL, QQMusic, Netease, Current }

internal fun automaticLyricSourcesFor(source: MusicSource): List<LyricAutoSource> = buildList {
    // The current provider's own lyrics are always preferred; the other
    // platforms are only consulted when it has none.
    add(LyricAutoSource.Current)
    add(LyricAutoSource.AmlL)
    if (source == MusicSource.QQMusic) {
        add(LyricAutoSource.Netease)
    } else {
        add(LyricAutoSource.QQMusic)
        if (source != MusicSource.Netease) add(LyricAutoSource.Netease)
    }
}.distinct()

internal fun selectAutomaticLyrics(candidates: List<AutoLyricCandidate>): LyricsDocument? =
    selectAutomaticLyricCandidate(candidates)?.document

internal fun selectAutomaticLyricCandidate(candidates: List<AutoLyricCandidate>): AutoLyricCandidate? {
    // This runs over the cross-platform fallback candidates only: the current
    // provider is resolved first and short-circuits before reaching here.
    // Among these, AMLL only has absolute priority when it contains authored
    // word timing; line-timed AMLL must not prevent the ordered QQ -> NetEase
    // fallback selected by automaticLyricSourcesFor().
    candidates.firstOrNull {
        it.document.source == com.lladlam.melox.core.lyrics.LyricSource.AmlL &&
            it.document.lines.isNotEmpty() &&
            hasWordTiming(it.document)
    }?.let { return it }
    // Authored AMLL is the highest-quality word-timed source. Other genuine
    // word-timed provider lyrics outrank any line-only document; among equal
    // timing classes, preserve the source order selected by the caller.
    candidates.asSequence()
        .filter { it.document.lines.isNotEmpty() && hasWordTiming(it.document) }
        .minByOrNull(AutoLyricCandidate::priority)
        ?.let { return it }
    return candidates
        .asSequence()
        .filterNot { it.document.source == com.lladlam.melox.core.lyrics.LyricSource.AmlL }
        .filter { it.document.lines.isNotEmpty() }
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
        // Tolerances come from the signed rules so cross-provider matching can be
        // tightened or loosened without an APK.
        val hotRules = com.lladlam.melox.core.remoteconfig.MeloXHotRules.current
        val toleranceMs = if (target.version == LyricTrackVersion.Original) {
            hotRules.lyricMatchOriginalToleranceMs
        } else {
            hotRules.lyricMatchVersionToleranceMs
        }
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
    preference: MeloXLyricPreference = requestedLyricPreference(),
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
    append(":translation-v2:").append(preference.cacheKey())
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
