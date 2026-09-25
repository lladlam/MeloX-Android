package com.lladlam.melox.playback

import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.content.pm.ServiceInfo
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import androidx.core.app.NotificationCompat
import com.lladlam.melox.MainActivity
import com.lladlam.melox.MeloXAppVisibility
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.download.MeloXProviderDownloadStore
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXNetworkAvailability
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.lyrics.LyricTimelineProcessor
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigPolicy
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.core.music.provider.PlaylistCapability
import com.lladlam.melox.core.music.provider.UserLibraryCapability
import com.lladlam.melox.core.music.provider.ThirdPartyMusicSourceConsentStore
import com.lladlam.melox.platform.xiaomi.HyperOsFocusBridge
import com.lladlam.melox.platform.vivo.VivoAtomicIslandBridge
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXSystemLyricTitleMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@OptIn(UnstableApi::class)
class MeloXPlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var incomingPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private lateinit var mediaSourceFactory: DefaultMediaSourceFactory
    private lateinit var mediaPrefetcher: MeloXMediaPrefetcher
    private lateinit var downloadStore: MeloXDownloadStore
    private lateinit var providerDownloadStore: MeloXProviderDownloadStore
    private lateinit var playbackResolver: NeteasePlaybackResolver
    private lateinit var autoMixAnalyzer: MeloXAutoMixAudioAnalyzer
    private lateinit var equalizerController: MeloXEqualizerController
    private lateinit var playbackHistoryReporter: MeloXPlaybackHistoryReporter
    private var historySongId: Long? = null
    private val listenedTimeTracker = MeloXListenedTimeTracker()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var recommendationJob: Job? = null
    private var recommendationSeed: String? = null
    private var recommendationRetrySeed: String? = null
    private var recommendationRetryAfterRealtimeMs = 0L
    private var systemLyricsJob: Job? = null
    private var systemLyricsSongId: Long? = null
    private var systemLyricsDocument: LyricsDocument? = null
    private var systemLyricsOriginalMetadata: MediaMetadata? = null
    private var systemLyricsLastIndex = Int.MIN_VALUE
    private var systemLyricsLastDispatchRealtimeMs = 0L
    private var systemLyricsLastPlaying = false
    private var updatingSystemLyricsMetadata = false
    private var mixAnalysisJob: Job? = null
    private var mixAnalysisSourceId: String? = null
    private var analyzedMixPlan: MeloXAutoMixPlan? = null
    private var preparedMixSourceId: String? = null
    private var preparedMixTargetId: String? = null
    private var autoMixRetrySourceId: String? = null
    private var autoMixRetryAfterRealtimeMs = 0L
    private var mixStartedAt = 0L
    private var mixDurationMs = 0L
    private var mixBaseVolume = 1f
    private var mixOutgoingStartPositionMs = 0L
    private var mixIncomingStartPositionMs = 0L
    private var mixLastProgress = 0.0
    private var mixSessionOnIncoming = false
    private var cancellingMix = false
    private var playlistAnalysisJob: Job? = null
    private var analysisForegroundStarted = false
    private var backgroundAnalysisJob: Job? = null
    private var backgroundAnalysisScheduledId: String? = null
    private var prefetchJob: Job? = null
    private var smartQueueJob: Job? = null
    private var smartQueueSourceId: String? = null
    private var smartQueueGeneration = -1L
    private var mixSettings = MeloXAutoMixSettings()
    private var cachedAutoMixSettings = MeloXAutoMixSettings()
    private var cachedAutoMixSettingsAt = 0L
    private var mixPlan = MeloXAutoMixPlan(0L, 0L)
    private val mixEqualizerEnvelope = MeloXAutoMixEqualizerEnvelope()
    private var lastMaintenanceRealtimeMs = 0L
    private var appliedAudioFocusPolicy: Boolean? = null

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val active = player
            Log.e(TAG, "Playback failed: code=${error.errorCodeName}, message=${error.message}", error)
            if (active == null) return
            if (!MeloXNetworkAvailability.isOnline(this@MeloXPlaybackService) && skipToNextDownloaded(active)) {
                Log.i(TAG, "Offline playback skipped unavailable item after player error")
                return
            }
            if (active.hasNextMediaItem()) {
                active.seekToNextMediaItem()
                active.prepare()
                active.play()
                return
            }
            active.prepare()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            listenedTimeTracker.onPlayingChanged(SystemClock.elapsedRealtime(), isPlaying)
            if (!isPlaying) persistQueueIfEnabled()
            Log.d(TAG, "isPlaying=$isPlaying, ongoing=${isPlaybackOngoing()}")
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && !cancellingMix && hasPreparedMix()) {
                Log.i(TAG, "AutoMix cancelled because playback paused: reason=$reason")
                cancelPreparedMix()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val active = player ?: return
            if (playbackState == Player.STATE_ENDED) {
                historySongId?.let {
                    playbackHistoryReporter.recordDuration(
                        it,
                        elapsedMs = listenedTimeTracker.elapsedMs(
                            SystemClock.elapsedRealtime(),
                            active.duration.takeIf { value -> value != C.TIME_UNSET && value > 0L },
                        ),
                    )
                }
                historySongId = null
                listenedTimeTracker.reset(SystemClock.elapsedRealtime(), false)
                if (!MeloXNetworkAvailability.isOnline(this@MeloXPlaybackService)) {
                    if (!skipToNextDownloaded(active)) active.pause()
                    return
                }
                ensureAutoplayRecommendations()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            recommendationSeed = null
            recommendationRetrySeed = null
            recommendationRetryAfterRealtimeMs = 0L
            autoMixRetrySourceId = null
            autoMixRetryAfterRealtimeMs = 0L
            val transitionedId = mediaItem?.mediaId?.toLongOrNull(); val previousHistoryId = historySongId
            if (previousHistoryId != null && previousHistoryId != transitionedId) {
                playbackHistoryReporter.recordDuration(previousHistoryId, elapsedMs = listenedTimeTracker.elapsedMs(SystemClock.elapsedRealtime()))
            }
            if (transitionedId != previousHistoryId) {
                historySongId = transitionedId
                listenedTimeTracker.reset(SystemClock.elapsedRealtime(), player?.isPlaying == true)
                transitionedId?.let(playbackHistoryReporter::recordStart)
            }
            MeloXAudioReactiveRuntime.select(mediaItem?.mediaId)
            mediaItem?.let(downloadStore::recordPlayback)
            mediaItem?.let(::scheduleBackgroundAnalysis)
            // Smart Queue starts with one visible item and appends exactly one
            // analyzed match at a time. It must also run for the initial item,
            // not only after a Media3 automatic transition.
            mediaItem?.let { scheduleSmartQueueNext(it.mediaId) }
            persistQueueIfEnabled()
            val active = player
            if (transitionedId != systemLyricsSongId) {
                active?.let(::restoreSystemLyricsMetadata)
                resetSystemLyrics(mediaItem)
            }
            if (active != null) {
                applyLocalArtworkMetadata(active)
                prefetchFollowing(active)
                if (
                    MeloXPlaybackModePreferences.autoplay(this@MeloXPlaybackService) &&
                    active.currentMediaItemIndex >= active.mediaItemCount - 1 &&
                    MeloXNetworkAvailability.isOnline(this@MeloXPlaybackService)
                ) {
                    ensureAutoplayRecommendations()
                }
                if (!MeloXNetworkAvailability.isOnline(this@MeloXPlaybackService)) {
                    val id = active.currentMediaItem?.mediaId?.toLongOrNull()
                    if (id != null && !downloadStore.contains(id)) {
                        skipToNextDownloaded(active)
                    }
                }
            }
            // An active crossfade owns the handoff. Do not destroy the incoming
            // deck if the outgoing deck reaches its boundary a few milliseconds
            // before the monitor promotes the already-playing incoming deck.
            if (mixStartedAt == 0L && hasPreparedMix()) cancelPreparedMix()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            equalizerController.attach(audioSessionId)
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (updatingSystemLyricsMetadata) return
            if (mixStartedAt == 0L && preparedMixSourceId != null) {
                val active = player
                val currentId = active?.currentMediaItem?.mediaId
                val nextId = active?.currentMediaItemIndex
                    ?.plus(1)
                    ?.takeIf { it in 0 until active.mediaItemCount }
                    ?.let(active::getMediaItemAt)
                    ?.mediaId
                if (currentId != preparedMixSourceId || nextId != preparedMixTargetId) {
                    cancelPreparedMix()
                }
            }
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private val sessionCallback = object : MediaSession.Callback {
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int,
        ): Int {
            return when (MeloXAutoMixTransportPolicy.action(
                command = playerCommand,
                hasPreparedMix = hasPreparedMix(),
                transitionStarted = mixStartedAt > 0L,
                playWhenReady = session.player.playWhenReady,
            )) {
                MeloXAutoMixTransportAction.Allow -> SessionResult.RESULT_SUCCESS
                MeloXAutoMixTransportAction.PauseAndCancel -> {
                    pauseAllAndCancelMix()
                    SessionResult.RESULT_INFO_SKIPPED
                }
                MeloXAutoMixTransportAction.CancelThenAllow -> {
                    cancelPreparedMix()
                    SessionResult.RESULT_SUCCESS
                }
                MeloXAutoMixTransportAction.ContinueOnIncoming -> if (promoteIncomingSessionDuringMix()) {
                    SessionResult.RESULT_INFO_SKIPPED
                } else {
                    cancelPreparedMix()
                    SessionResult.RESULT_SUCCESS
                }
            }
        }
    }

    private fun prefetchFollowing(active: ExoPlayer) {
        val current = active.currentMediaItemIndex
        if (current !in 0 until active.mediaItemCount) return
        // Cache the active item as well as the next three. The active item is
        // needed by Smart analysis when it examines the outgoing tail later.
        val following = (current until minOf(current + PREFETCH_TRACK_COUNT + 1, active.mediaItemCount))
            .map(active::getMediaItemAt)
        prefetchJob?.cancel()
        prefetchJob = serviceScope.launch(Dispatchers.IO) {
            following.forEach { item ->
                if (!isActive) return@launch
                runCatching { mediaPrefetcher.cache(item) }
                    .onSuccess { Log.i(TAG, "Playback cache ready: ${item.mediaId}") }
                    .onFailure { Log.d(TAG, "Playback cache skipped for ${item.mediaId}: ${it.message}") }
            }
        }
    }

    private val modeMonitor = object : Runnable {
        override fun run() {
            val active = player
            if (active != null) {
                applyAudioFocusPolicy(active)
                val uiTransitionActive = MeloXPlayerTransitionState.isActive
                runCatching {
                    active.currentMediaItem?.let(::ensureBackgroundAnalysisScheduled)
                    active.currentMediaItem?.mediaId?.let(::scheduleSmartQueueNext)
                    maybePrepareAutoplay(active)
                    maybeRunAutoMix(active)
                    if (!uiTransitionActive) {
                        maybeUpdateSystemLyrics(active)
                        updateAudioReactiveVisuals(active)
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastMaintenanceRealtimeMs >= PLAYBACK_MAINTENANCE_INTERVAL_MS) {
                        lastMaintenanceRealtimeMs = now
                        if (!uiTransitionActive) {
                            applyLocalArtworkMetadata(active)
                        }
                        PlaybackCommands.prioritizeManualQueue(active)
                        enforceSleepTimer(active)
                        equalizerController.applySettings()
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Playback monitor recovered from failure", error)
                    recoverAutoMixFailure()
                }
            }
            val nextTickMs = when {
                mixStartedAt > 0L -> ACTIVE_MONITOR_INTERVAL_MS
                active?.isPlaying == true -> ACTIVE_MONITOR_INTERVAL_MS
                active?.currentMediaItem != null -> PAUSED_MONITOR_INTERVAL_MS
                else -> IDLE_MONITOR_INTERVAL_MS
            }
            handler.postDelayed(this, nextTickMs)
        }
    }

    private fun updateAudioReactiveVisuals(active: ExoPlayer) {
        val item = active.currentMediaItem ?: return
        // Visual motion follows the lightweight playback clock. Full-track
        // MediaCodec analysis is reserved for explicitly enabled AutoMix; doing
        // a second HTTP decode for decoration competes with ExoPlayer.
        MeloXAudioReactiveRuntime.publish(item.mediaId, active.currentPosition, active.isPlaying)
    }

    /** Keep timers alive across Activity recreation by owning them in playback. */
    private fun enforceSleepTimer(active: ExoPlayer) {
        val end = MeloXSettingsPreferences.long(this, SLEEP_TIMER_END_KEY, 0L)
        if (end <= 0L || System.currentTimeMillis() < end) return
        active.pause()
        MeloXSettingsPreferences.setLong(this, SLEEP_TIMER_END_KEY, 0L)
    }

    /**
     * Keep the gain envelope independent from the slower queue/autoplay monitor.
     * Upstream MeloX advances its two-deck envelope every 20 ms; driving volume
     * from the 100 ms mode monitor made the crossfade audibly step between gains.
     */
    private val mixEnvelope = object : Runnable {
        override fun run() {
            val active = player
            val incoming = incomingPlayer
            if (mixStartedAt == 0L || active == null || incoming == null) return
            val updated = runCatching { updateAutoMixEnvelope(active, incoming) }
                .onFailure { error ->
                    Log.e(TAG, "AutoMix envelope failed; restoring normal playback", error)
                    recoverAutoMixFailure()
                }
                .isSuccess
            if (updated && mixStartedAt > 0L) {
                handler.postDelayed(this, AUTOMIX_ENVELOPE_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        MeloXPlaybackModePreferences.initialize(this)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36",
                    "Referer" to "https://music.163.com/",
                ),
            )
        downloadStore = MeloXDownloadStore.get(this)
        providerDownloadStore = MeloXProviderDownloadStore.get(this)
        equalizerController = MeloXEqualizerController(this)
        playbackHistoryReporter = MeloXPlaybackHistoryReporter(this)
        ProviderPlaybackRuntime.initialize(this)
        val crossProviderFallback = CrossProviderPlaybackFallbackResolver(
            enabledProvider = {
                CrossProviderPlaybackPreferences.enabled(this@MeloXPlaybackService) &&
                    MeloXRemoteConfigPolicy.capabilityEnabled(
                        this@MeloXPlaybackService,
                        "cross_provider_fallback",
                    )
            },
            registryProvider = ProviderPlaybackRuntime::registryOrNull,
            cacheIdentityProvider = {
                CrossProviderPlaybackFallbackResolver.EligibleSources
                    .sortedBy(MusicSource::storageValue)
                    .joinToString("|") { source ->
                        "${source.storageValue}:${ProviderPlaybackRuntime.authKey(source)}"
                    }
            },
            eventLogger = { message -> Log.i(TAG, "Cross-provider fallback: $message") },
            fallbackConfigProvider = {
                MeloXRemoteConfigPolicy.activeConfig(this@MeloXPlaybackService).fallback
            },
        )
        val cookieProvider = { com.lladlam.melox.core.music.provider.PlaybackAccountStore.neteaseCookie(this@MeloXPlaybackService) }
        val lxUserPlayback = LxUserPlaybackResolver(this)
        val chkszPlayback = ChkszPlaybackResolver(this)
        playbackResolver = NeteasePlaybackResolver(
            cookieProvider = cookieProvider,
            client = NeteaseSearchClient(cookieProvider = cookieProvider),
            localSourceProvider = downloadStore::localPlaybackUri,
            providerLocalSourceProvider = providerDownloadStore::localPlaybackUri,
            crossProviderFallback = crossProviderFallback,
            chkszPlayback = chkszPlayback,
            lxUserPlayback = lxUserPlayback,
            providerPlaybackEnabled = { source ->
                MeloXRemoteConfigPolicy.providerPlaybackEnabled(this@MeloXPlaybackService, source)
            },
            thirdPartySourcesEnabled = { ThirdPartyMusicSourceConsentStore.enabled(this@MeloXPlaybackService) },
            thirdPartyOnlyForMembership = { ThirdPartyMusicSourceConsentStore.membershipFallbackOnly(this@MeloXPlaybackService) },
        )
        autoMixAnalyzer = MeloXAutoMixAudioAnalyzer(this)
        val upstream = DefaultDataSource.Factory(this, httpFactory)
        val resolving = ResolvingDataSource.Factory(
            upstream,
            playbackResolver,
        )
        val cached = CacheDataSource.Factory()
            .setCache(MeloXMediaCache.get(this))
            .setUpstreamDataSourceFactory(resolving)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val playbackCache = MeloXMediaCache.get(this)
        cached.setCache(playbackCache)
        mediaPrefetcher = MeloXMediaPrefetcher(
            analysisDirectory = java.io.File(cacheDir, "automix_analysis"),
            dataSourceFactory = cached,
            cache = playbackCache,
        )
        mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(cached)

        val active = buildPlayer(managesAudioFocus = shouldManageAudioFocus())
            player = active
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_NOW_PLAYING
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            1001,
            sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, active)
            .setSessionActivity(sessionActivity)
            .setCallback(sessionCallback)
            .build()
        handler.post { restorePersistedQueue(active) }
        createLyricsNotificationChannel()
        handler.post(modeMonitor)
        handler.postDelayed({
            player?.currentMediaItem?.let(::ensureBackgroundAnalysisScheduled)
        }, 1_000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ANALYZE_PLAYLIST) {
            val source = MusicSource.fromStorageValue(intent.getStringExtra(EXTRA_ANALYSIS_SOURCE))
            val playlistId = intent.getStringExtra(EXTRA_ANALYSIS_PLAYLIST_ID)
            if (!playlistId.isNullOrBlank()) startPlaylistAnalysis(source, playlistId)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startPlaylistAnalysis(source: MusicSource, playlistId: String) {
        if (playlistAnalysisJob?.isActive == true) return
        if (player?.isPlaying != true) {
            val notification = buildPlaylistAnalysisNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    ANALYSIS_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(ANALYSIS_NOTIFICATION_ID, notification)
            }
            analysisForegroundStarted = true
        }
        playlistAnalysisJob = serviceScope.launch(Dispatchers.IO) {
            runCatching {
                val items = if (source == MusicSource.Netease) {
                    val cookie = { NeteaseSessionStore.readCookie(this@MeloXPlaybackService) }
                    NeteaseLibraryClient(cookieProvider = cookie)
                        .playlistDetailBlocking(playlistId.toLong()).songs
                        .map { song -> PlaybackCommands.mediaItemFor(song, MusicQuality.Standard) }
                } else {
                    val provider = MeloXMusicProviders.create(this@MeloXPlaybackService).require(source)
                    val summary = MusicPlaylistSummary(MusicResourceId(source, playlistId), "")
                    val capability = provider as? PlaylistCapability
                        ?: error("${source.displayName} 当前没有歌单详情能力")
                    capability.playlistDetail(summary, page = 1, pageSize = 500).tracks.map { track ->
                        ProviderPlaybackCommands.mediaItemFor(
                            track = track,
                            neteaseQuality = MusicQuality.Standard,
                            qualityTier = AudioQualityTier.Standard,
                        )
                    }
                }
                val pendingItems = items.filterNot { item ->
                    autoMixAnalyzer.hasPersistentFullAnalysis(item.analysisSongId())
                }
                MeloXAudioAnalysisRuntime.start(items.size, items.size - pendingItems.size)
                postPlaylistAnalysisNotification()
                val permits = Semaphore(2)
                coroutineScope {
                    pendingItems.map { item ->
                        async(Dispatchers.IO) {
                            permits.withPermit {
                                val failed = runCatching {
                                    val standardItem = item.copyWithStandardAnalysisUri()
                                    mediaPrefetcher.cache(standardItem)
                                    val file = mediaPrefetcher.materialize(standardItem)
                                    MeloXAudioAnalysisLoad.begin()
                                    try {
                                        val uri = android.net.Uri.fromFile(file)
                                        autoMixAnalyzer.analyze(item.analysisSongId(), uri, 0L, Long.MAX_VALUE)
                                    } finally {
                                        MeloXAudioAnalysisLoad.end()
                                        file.delete()
                                    }
                                }.onFailure { Log.w(TAG, "Playlist analysis failed: ${item.mediaMetadata.title}", it) }.isFailure
                                MeloXAudioAnalysisRuntime.advance(failed)
                                val progress = MeloXAudioAnalysisRuntime.progress.value
                                Log.i(TAG, "Playlist analysis progress: ${progress.completed}/${items.size}")
                                postPlaylistAnalysisNotification()
                            }
                        }
                    }.awaitAll()
                }
            }.onFailure {
                Log.e(TAG, "Playlist audio analysis failed", it)
                postPlaylistAnalysisNotification(failed = true)
            }
            if (MeloXAudioAnalysisRuntime.progress.value.total > 0) {
                postPlaylistAnalysisNotification(completed = true)
            }
            withContext(Dispatchers.Main.immediate) {
            if (analysisForegroundStarted && player?.isPlaying != true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(Service.STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
                analysisForegroundStarted = false
            }
            playlistAnalysisJob = null
            }
        }
    }

    private fun postPlaylistAnalysisNotification(
        completed: Boolean = false,
        failed: Boolean = false,
    ) {
        getSystemService(NotificationManager::class.java).notify(
            ANALYSIS_NOTIFICATION_ID,
            buildPlaylistAnalysisNotification(completed, failed),
        )
    }

    private fun buildPlaylistAnalysisNotification(
        completed: Boolean = false,
        failed: Boolean = false,
    ): android.app.Notification {
        val progress = MeloXAudioAnalysisRuntime.progress.value
        val title = when {
            failed -> "歌曲分析失败"
            completed -> "歌曲分析完成"
            else -> "正在提前分析歌曲"
        }
        val detail = if (completed || failed) {
            "已完成 ${progress.completed}/${progress.total} 首${if (progress.failed > 0) "，失败 ${progress.failed} 首" else ""}"
        } else {
            "正在并列分析 2 首 · ${progress.completed}/${progress.total}"
        }
        val intent = PendingIntent.getActivity(
            this,
            ANALYSIS_NOTIFICATION_ID,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, ANALYSIS_NOTIFICATION_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle(title)
                .setContentText(detail)
                .setContentIntent(intent)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setOngoing(!completed && !failed)
                .setAutoCancel(completed || failed)
                .setProgress(
                    progress.total.coerceAtLeast(1),
                    progress.completed.coerceIn(0, progress.total.coerceAtLeast(1)),
                    false,
                )
                .build()
    }

    private fun scheduleBackgroundAnalysis(item: MediaItem) {
        if (!MeloXAudioAnalysisPreferences.persistentEnabled(this)) return
        backgroundAnalysisScheduledId = item.mediaId
        Log.i(TAG, "Background audio analysis scheduled: ${item.mediaId}")
        backgroundAnalysisJob?.cancel()
        backgroundAnalysisJob = serviceScope.launch(Dispatchers.IO) {
            delay(10_000L)
            val currentMediaId = withContext(Dispatchers.Main.immediate) {
                player?.currentMediaItem?.mediaId
            }
            if (currentMediaId != item.mediaId) return@launch
            val independent = MeloXAudioAnalysisPreferences.independentLineEnabled(this@MeloXPlaybackService)
            val analysisItem = if (independent) item.copyWithStandardAnalysisUri() else item
            runCatching {
                mediaPrefetcher.cache(analysisItem)
                val file = mediaPrefetcher.materialize(analysisItem)
                try {
                    val uri = android.net.Uri.fromFile(file)
                    autoMixAnalyzer.analyze(item.analysisSongId(), uri, 0L, ANALYSIS_WINDOW_MS)
                    autoMixAnalyzer.analyze(item.analysisSongId(), uri, -ANALYSIS_WINDOW_MS, ANALYSIS_WINDOW_MS)
                } finally {
                    file.delete()
                    if (independent) mediaPrefetcher.remove(analysisItem)
                }
            }.onSuccess { Log.i(TAG, "Background audio analysis ready: ${item.mediaId}") }
                .onFailure { Log.w(TAG, "Background audio analysis failed: ${item.mediaId}", it) }
        }
    }

    private fun scheduleSmartQueueNext(sourceId: String) {
        if (!MeloXPlaybackModePreferences.smartQueue(this)) return
        if (!MeloXPlaybackModePreferences.autoMix(this)) return
        val active = player ?: return
        // ExoPlayer may only be queried from its application thread. Capture
        // immutable MediaItem snapshots here on main before starting IO work.
        val currentItem = active.currentMediaItem ?: return
        if (currentItem.mediaId != sourceId) return
        val snapshot = MeloXSmartQueueBuilder.snapshot()
        if (snapshot.items.isEmpty()) return
        if (smartQueueGeneration != snapshot.generation) {
            smartQueueJob?.cancel()
            smartQueueGeneration = snapshot.generation
            smartQueueJob = serviceScope.launch(Dispatchers.IO) {
                val limiter = Semaphore(SMART_QUEUE_ANALYSIS_CONCURRENCY)
                coroutineScope {
                    (listOf(currentItem) + snapshot.items)
                        .distinctBy(MediaItem::mediaId)
                        .forEachIndexed { index, item ->
                            launch {
                                limiter.withPermit {
                                    runCatching { analyzeSmartQueueItem(item) }
                                        .onSuccess { analysis ->
                                            MeloXSmartQueueBuilder.recordAnalysis(
                                                snapshot.generation,
                                                item.mediaId,
                                                analysis,
                                            )
                                            val role = if (index == 0) "source" else "candidate"
                                            Log.i(
                                                TAG,
                                                "Smart Queue $role ready: ${item.mediaId}, " +
                                                    "bpm=${"%.1f".format(analysis.bpm)}, " +
                                                    "confidence=${"%.3f".format(analysis.confidence)}",
                                            )
                                            withContext(Dispatchers.Main.immediate) {
                                                commitSmartQueueChain(snapshot.generation, sourceId)
                                            }
                                        }
                                        .onFailure {
                                            if (it is kotlinx.coroutines.CancellationException) throw it
                                            MeloXSmartQueueBuilder.recordFailure(snapshot.generation, item.mediaId)
                                            Log.w(TAG, "Smart Queue analysis failed: ${item.mediaId}", it)
                                        }
                                }
                        }
                    }
                }
                withContext(Dispatchers.Main.immediate) {
                    commitSmartQueueChain(snapshot.generation, sourceId)
                }
            }
        }
        smartQueueSourceId = sourceId
        commitSmartQueueChain(snapshot.generation, sourceId)
        val duration = active.duration
        if (!active.hasNextMediaItem() && duration != C.TIME_UNSET && duration > 0L &&
            duration - active.currentPosition <= 30_000L && mixStartedAt == 0L
        ) {
            val selected = MeloXSmartQueueBuilder.emergencyCandidate(snapshot.generation) ?: return
            val consumed = MeloXSmartQueueBuilder.consume(snapshot.generation, selected.mediaId) ?: return
            active.addMediaItem(consumed)
            Log.w(TAG, "Smart Queue emergency append: source=$sourceId target=${consumed.mediaId}, remaining=${duration - active.currentPosition}, reason=no scored chain available")
        }
    }

    private fun commitSmartQueueChain(expectedGeneration: Long, initialSourceId: String) {
        val active = player ?: return
        if (!MeloXPlaybackModePreferences.smartQueue(this) || !MeloXPlaybackModePreferences.autoMix(this)) return
        if (MeloXSmartQueueBuilder.snapshot().generation != expectedGeneration) return
        if (active.mediaItemCount == 0 || mixStartedAt > 0L) return
        var sourceId = active.getMediaItemAt(active.mediaItemCount - 1).mediaId
        while (true) {
            val match = MeloXSmartQueueBuilder.bestAnalyzedMatch(expectedGeneration, sourceId) ?: break
            val consumed = MeloXSmartQueueBuilder.consume(expectedGeneration, match.item.mediaId) ?: break
            active.addMediaItem(consumed)
            if (preparedMixSourceId != null) incomingPlayer?.addMediaItem(consumed)
            Log.i(
                TAG,
                "Smart Queue appended ${match.reason} match immediately: ${consumed.mediaId}, " +
                    "source=$sourceId bpm=${"%.1f".format(match.analysis.bpm)}, " +
                    "tempoDistance=${"%.1f".format(match.tempoDistance)}, " +
                    "confidence=${"%.3f".format(match.analysis.confidence)}",
            )
            sourceId = consumed.mediaId
        }
    }

    private suspend fun analyzeSmartQueueItem(item: MediaItem): MeloXAutoMixTrackAnalysis {
        val standardItem = item.copyWithStandardAnalysisUri()
        val songId = standardItem.analysisSongId()
        MeloXAudioAnalysisLoad.begin()
        val file = try {
            mediaPrefetcher.materialize(standardItem)
        } catch (error: Throwable) {
            MeloXAudioAnalysisLoad.end()
            throw error
        }
        return try {
            autoMixAnalyzer.analyze(
                songId = songId,
                uri = android.net.Uri.fromFile(file),
                windowStartMs = 0L,
                windowDurationMs = Long.MAX_VALUE,
            )
        } finally {
            file.delete()
            MeloXAudioAnalysisLoad.end()
        }
    }

    private fun ensureBackgroundAnalysisScheduled(item: MediaItem) {
        if (backgroundAnalysisScheduledId == item.mediaId) return
        scheduleBackgroundAnalysis(item)
    }

    private fun MediaItem.copyWithStandardAnalysisUri(): MediaItem {
        val uri = localConfiguration?.uri ?: return this
        val standard = uri.buildUpon().clearQuery().apply {
            uri.queryParameterNames.forEach { name ->
                if (name != "quality" && name != "qualityTier") {
                    uri.getQueryParameters(name).forEach { value -> appendQueryParameter(name, value) }
                }
            }
            appendQueryParameter("quality", "standard")
            appendQueryParameter("qualityTier", "Standard")
        }.build()
        return buildUpon().setUri(standard).build()
    }

    private fun MediaItem.analysisSongId(): Long = mediaId.toLongOrNull() ?: mediaId.hashCode().toLong()

    private fun buildPlayer(
        managesAudioFocus: Boolean,
        observesSession: Boolean = managesAudioFocus,
    ): ExoPlayer =
        ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply {
                setAudioAttributes(audioAttributes, managesAudioFocus)
                setHandleAudioBecomingNoisy(managesAudioFocus)
                if (observesSession) addListener(playerListener)
            }

    private fun shouldManageAudioFocus(): Boolean =
        !MeloXSettingsPreferences.boolean(this, "playback_allow_other_apps", false)

    private fun applyAudioFocusPolicy(active: ExoPlayer) {
        val managesAudioFocus = shouldManageAudioFocus()
        if (appliedAudioFocusPolicy == managesAudioFocus) return
        active.setAudioAttributes(audioAttributes, managesAudioFocus)
        active.setHandleAudioBecomingNoisy(managesAudioFocus)
        appliedAudioFocusPolicy = managesAudioFocus
    }

    private fun maybePrepareAutoplay(active: ExoPlayer) {
        if (MeloXPlaybackModePreferences.smartQueue(this)) return
        if (!MeloXPlaybackModePreferences.autoplay(this)) return
        if (active.mediaItemCount <= 0 || active.currentMediaItemIndex < 0) return
        val atTail = active.currentMediaItemIndex >= active.mediaItemCount - 1
        if (!atTail) return
        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return
        val remaining = duration - active.currentPosition
        val preloadMs = if (MeloXPlaybackModePreferences.autoMix(this)) {
            maxOf(AUTOPLAY_PRELOAD_MS, currentAutoMixSettings().preloadLeadMs)
        } else {
            AUTOPLAY_PRELOAD_MS
        }
        if (remaining <= preloadMs && MeloXNetworkAvailability.isOnline(this)) {
            ensureAutoplayRecommendations()
        }
    }

    private fun ensureAutoplayRecommendations() {
        if (MeloXPlaybackModePreferences.smartQueue(this)) return
        if (!MeloXNetworkAvailability.isOnline(this)) return
        if (!MeloXPlaybackModePreferences.autoplay(this)) return
        val active = player ?: return
        val item = active.currentMediaItem ?: return
        val seed = item.mediaId
        resumeEndedAutoplayIfReady(active)
        if (recommendationJob?.isActive == true || recommendationSeed == seed) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (recommendationRetrySeed == seed && now < recommendationRetryAfterRealtimeMs) return
        if (recommendationRetrySeed == seed) {
            recommendationRetrySeed = null
            recommendationRetryAfterRealtimeMs = 0L
        }
        val metadata = item.mediaMetadata
        val seedSnapshot = AutoplaySeedSnapshot(
            queueIdentity = seed,
            neteaseId = PlaybackTrackIdentity.neteaseNumericId(item),
            title = metadata.extras?.getString(SYSTEM_ORIGINAL_TITLE_KEY)
                ?.takeIf(String::isNotBlank)
                ?: metadata.title?.toString().orEmpty(),
            artist = metadata.extras?.getString(SYSTEM_ORIGINAL_ARTIST_KEY)
                ?.takeIf(String::isNotBlank)
                ?: metadata.artist?.toString().orEmpty(),
            durationMs = metadata.extras?.getLong(PlaybackTrackIdentity.DurationMsExtra, 0L)
                ?.takeIf { it > 0L }
                ?: active.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                ?: 0L,
        )
        recommendationSeed = seed
        recommendationJob = serviceScope.launch {
            val cookie = { NeteaseSessionStore.readCookie(this@MeloXPlaybackService) }
            val result = runCatching {
                val neteaseSeed = seedSnapshot.neteaseId ?: findNeteaseAutoplaySeed(seedSnapshot, cookie)
                    ?: error("未找到可用于无尽播放的网易云匹配歌曲")
                withContext(Dispatchers.IO) {
                    NeteaseLibraryClient(cookieProvider = cookie).similarSongsBlocking(neteaseSeed, 30)
                }
            }
            var recommendationsCommitted = false
            try {
                if (
                    player !== active ||
                    !MeloXPlaybackModePreferences.autoplay(this@MeloXPlaybackService) ||
                    active.currentMediaItem?.mediaId != seedSnapshot.queueIdentity
                ) return@launch
                val recommendations = result.getOrElse { error ->
                    Log.w(TAG, "Autoplay recommendations failed for ${seedSnapshot.queueIdentity}", error)
                    emptyList()
                }
                val existing = (0 until active.mediaItemCount).map { active.getMediaItemAt(it).mediaId }.toSet()
                val quality = MusicQualityPreferences.read(this@MeloXPlaybackService)
                val additions = recommendations
                    .filterNot { it.id.toString() in existing }
                    .take(20)
                additions.forEach { song ->
                    active.addMediaItem(
                        PlaybackCommands.mediaItemFor(song, quality, PlaybackCommands.QUEUE_ORIGIN_BASE),
                    )
                }
                if (additions.isNotEmpty()) {
                    recommendationsCommitted = true
                    recommendationRetrySeed = null
                    recommendationRetryAfterRealtimeMs = 0L
                    PlaybackCommands.prioritizeManualQueue(active)
                    resumeEndedAutoplayIfReady(active)
                } else {
                    recommendationSeed = null
                    recommendationRetrySeed = seedSnapshot.queueIdentity
                    recommendationRetryAfterRealtimeMs = SystemClock.elapsedRealtime() + AUTOPLAY_RETRY_MS
                    Log.w(TAG, "Autoplay returned no new tracks for ${seedSnapshot.queueIdentity}; retry scheduled")
                }
            } finally {
                if (!recommendationsCommitted && recommendationSeed == seedSnapshot.queueIdentity) {
                    recommendationSeed = null
                }
                recommendationJob = null
            }
        }
    }

    private suspend fun findNeteaseAutoplaySeed(
        snapshot: AutoplaySeedSnapshot,
        cookie: () -> String,
    ): Long? {
        if (snapshot.title.isBlank()) return null
        val client = NeteaseSearchClient(cookieProvider = cookie)
        val candidates = coroutineScope {
            listOf(snapshot.title, "${snapshot.title} ${snapshot.artist}".trim())
                .distinct()
                .map { query ->
                    async { runCatching { client.searchSongs(query, limit = 20) }.getOrDefault(emptyList()) }
                }
                .awaitAll()
                .flatten()
                .distinctBy(SearchSong::id)
        }
        return selectNeteaseAutoplaySeed(snapshot.title, snapshot.artist, snapshot.durationMs, candidates)
    }

    private fun resumeEndedAutoplayIfReady(active: ExoPlayer) {
        if (!shouldResumeEndedAutoplay(active.playbackState, active.hasNextMediaItem())) return
        active.seekToNextMediaItem()
        active.prepare()
        active.play()
    }

    private data class AutoplaySeedSnapshot(
        val queueIdentity: String,
        val neteaseId: Long?,
        val title: String,
        val artist: String,
        val durationMs: Long,
    )

    private fun maybeRunAutoMix(active: ExoPlayer) {
        val enabled = MeloXPlaybackModePreferences.autoMix(this)
        if (MeloXPlaybackModeRuntime.autoMixEnabled != enabled) {
            MeloXPlaybackModeRuntime.autoMixEnabled = enabled
        }
        if (!enabled) {
            cancelPreparedMix(releaseStandby = true)
            return
        }
        active.currentMediaItem?.let(::ensureBackgroundAnalysisScheduled)
        if (!active.isPlaying || active.repeatMode == Player.REPEAT_MODE_ONE) {
            Log.d(TAG, "AutoMix skipped: playing=${active.isPlaying}, repeat=${active.repeatMode}")
            return
        }
        if (!active.hasNextMediaItem()) {
            Log.d(TAG, "AutoMix skipped: no next media item")
            if (!MeloXPlaybackModePreferences.smartQueue(this) &&
                MeloXPlaybackModePreferences.autoplay(this)
            ) maybePrepareAutoplay(active)
            return
        }
        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: run {
            Log.d(TAG, "AutoMix skipped: duration unset")
            return
        }
        val remaining = duration - active.currentPosition
        val sourceId = active.currentMediaItem?.mediaId ?: run {
            Log.d(TAG, "AutoMix skipped: no source id")
            return
        }
        if (autoMixRetrySourceId == sourceId && SystemClock.elapsedRealtime() < autoMixRetryAfterRealtimeMs) {
            Log.d(TAG, "AutoMix skipped: retry cooldown for $sourceId")
            return
        }
        if (autoMixRetrySourceId != null && autoMixRetrySourceId != sourceId) {
            autoMixRetrySourceId = null
            autoMixRetryAfterRealtimeMs = 0L
        }
        val settings = currentAutoMixSettings()
        Log.d(TAG, "AutoMix evaluate: source=$sourceId, remaining=$remaining, preload=${settings.preloadLeadMs}, prepared=$preparedMixSourceId")
        if (settings.mode == MeloXAutoMixMode.Smart &&
            mixAnalysisSourceId != sourceId &&
            mixAnalysisJob?.isActive != true
        ) {
            Log.i(TAG, "AutoMix starting analysis: source=$sourceId")
            startAutoMixAnalysis(active, sourceId, settings)
        }
        if (preparedMixSourceId == null && remaining <= settings.preloadLeadMs) {
            Log.i(TAG, "AutoMix preparing incoming: source=$sourceId, remaining=$remaining")
            PlaybackCommands.prioritizeManualQueue(active)
            prepareIncoming(active, sourceId)
        }
        val incoming = incomingPlayer ?: run {
            Log.d(TAG, "AutoMix skipped: incoming player not created")
            return
        }
        if (preparedMixSourceId != sourceId) {
            if (preparedMixSourceId == null) {
                // A standby deck can exist from a previous handoff while the
                // next preload has not started yet. Do not cancel and destroy
                // that deck on every monitor tick; wait for preloadLeadMs.
                return
            }
            Log.d(TAG, "AutoMix prepared source mismatch: prepared=$preparedMixSourceId, current=$sourceId")
            if (mixStartedAt > 0L) completeAutoMix(active, incoming) else cancelPreparedMix()
            return
        }
        val candidate = when (settings.mode) {
            MeloXAutoMixMode.Fixed -> MeloXAutoMixPlanner.plan(settings, remaining)
            MeloXAutoMixMode.Smart -> analyzedMixPlan ?: run {
                val fallback = MeloXAutoMixPlanner.plan(settings, remaining)
                // If the analysis job has already finished without a usable
                // plan, switch to fallback immediately instead of waiting until
                // the last second. Otherwise wait until the transition window.
                val analysisFinished = mixAnalysisJob?.isActive != true
                if (analysisFinished || remaining <= fallback.durationMs + ANALYSIS_FALLBACK_GUARD_MS) {
                    Log.i(TAG, "AutoMix using fallback: remaining=$remaining, fallback=${fallback.durationMs}, analysisFinished=$analysisFinished")
                    fallback
                } else {
                    Log.d(TAG, "AutoMix waiting for smart analysis: remaining=$remaining, fallback=${fallback.durationMs}")
                    return
                }
            }
        }
        if (!candidate.performsTransition) {
            Log.d(TAG, "AutoMix candidate performs no transition")
            return
        }
        val reachedTransitionPoint = if (candidate.usedSmartAnalysis) {
            active.currentPosition >= candidate.outgoingStartMs
        } else {
            remaining <= candidate.durationMs + candidate.outgoingEndOffsetMs
        }
        if (mixStartedAt == 0L) {
            if (incoming.playbackState != Player.STATE_READY) {
                Log.d(TAG, "AutoMix not starting: incoming not ready, state=${incoming.playbackState}")
            } else if (!reachedTransitionPoint) {
                Log.d(TAG, "AutoMix not starting: transition point not reached, remaining=$remaining, duration=${candidate.durationMs}, offset=${candidate.outgoingEndOffsetMs}")
            }
        }
        if (mixStartedAt == 0L && incoming.playbackState == Player.STATE_READY && reachedTransitionPoint) {
            // Do not start the second live decoder while either full-track
            // analyser still owns a MediaCodec instance. Cancellation is
            // cooperative; the next 100 ms monitor tick starts the mix after
            // the codec's finally block has released the native resource.
            val activeAnalysis = listOfNotNull(mixAnalysisJob)
                .filter(Job::isActive)
            if (activeAnalysis.isNotEmpty()) {
                activeAnalysis.forEach(Job::cancel)
                Log.d(TAG, "AutoMix cancelling active analysis before start")
                return
            }
            val actualDuration = minOf(
                candidate.durationMs,
                remaining - candidate.outgoingEndOffsetMs,
            )
            if (actualDuration < MeloXAutoMixPlanner.MIN_DURATION_MS) {
                Log.d(TAG, "AutoMix actual duration too small: $actualDuration")
                return
            }
            Log.i(TAG, "AutoMix starting transition: source=$sourceId, duration=$actualDuration, incomingStart=${candidate.incomingStartMs}")
            runCatching {
                mixSettings = settings
                mixPlan = candidate.copy(durationMs = actualDuration)
                mixBaseVolume = active.volume.coerceIn(0f, 1f)
                mixDurationMs = actualDuration
                if (candidate.incomingStartMs > 0L) incoming.seekTo(candidate.incomingStartMs)
                active.setPlaybackSpeed(candidate.outgoingStartRate)
                incoming.setPlaybackSpeed(candidate.incomingStartRate)
                incoming.volume = 0f
                incoming.play()
                if (supportsStableDeckEqualizers()) {
                    mixEqualizerEnvelope.attach(active.audioSessionId, incoming.audioSessionId)
                } else {
                    mixEqualizerEnvelope.release()
                }
                mixStartedAt = SystemClock.elapsedRealtime()
                mixOutgoingStartPositionMs = active.currentPosition.coerceAtLeast(0L)
                mixIncomingStartPositionMs = incoming.currentPosition.coerceAtLeast(0L)
                mixLastProgress = 0.0
                MeloXAutoMixTransitionRuntime.begin(active.currentMediaItem, incoming.currentMediaItem)
                handler.removeCallbacks(mixEnvelope)
                handler.post(mixEnvelope)
            }.onFailure { error ->
                Log.e(TAG, "AutoMix start failed; continuing current song", error)
                recoverAutoMixFailure()
            }
        }
    }

    private fun currentAutoMixSettings(): MeloXAutoMixSettings {
        val now = SystemClock.elapsedRealtime()
        if (now - cachedAutoMixSettingsAt >= SETTINGS_SNAPSHOT_INTERVAL_MS) {
            cachedAutoMixSettings = MeloXAutoMixSettings.read(this)
            cachedAutoMixSettingsAt = now
        }
        return cachedAutoMixSettings
    }

    private fun supportsStableDeckEqualizers(): Boolean {
        return MeloXAutoMixEqualizerEnvelope.supportsDeckEqualizers(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            userEqualizerEnabled = MeloXSettingsPreferences.boolean(this, "equalizer_enabled", false),
        )
    }

    private fun recoverAutoMixFailure() {
        autoMixRetrySourceId = player?.currentMediaItem?.mediaId
        autoMixRetryAfterRealtimeMs = SystemClock.elapsedRealtime() + AUTOMIX_FAILURE_COOLDOWN_MS
        runCatching { cancelPreparedMix(releaseStandby = true) }
            .onFailure { Log.e(TAG, "AutoMix cleanup also failed", it) }
    }

    private fun maybeUpdateSystemLyrics(active: ExoPlayer) {
        val currentItem = active.currentMediaItem ?: return
        val metadataEnabled = MeloXSettingsRuntime.systemLyricsEnabled
        val notificationEnabled = MeloXSettingsRuntime.lyricNotificationsEnabled
        val hyperIslandEnabled = MeloXSettingsPreferences.boolean(this, "hyperos_super_island_enabled", false)
        val effectiveNotification = notificationEnabled || hyperIslandEnabled
        val songId = currentItem.mediaId.toLongOrNull()?.takeIf { it > 0L }
        if (shouldClearLegacySystemLyrics(currentItem.mediaId)) {
            restoreSystemLyricsMetadata(active)
            resetSystemLyrics(null)
            getSystemService(NotificationManager::class.java).cancel(LYRICS_NOTIFICATION_ID)
            return
        }
        songId ?: return
        if (!metadataEnabled && !effectiveNotification && !VivoAtomicIslandBridge.isSupported()) {
            restoreSystemLyricsMetadata(active)
            (getSystemService(NotificationManager::class.java)).cancel(LYRICS_NOTIFICATION_ID)
            HyperOsFocusBridge.clearSuperIsland(this)
            return
        }
        if (!hyperIslandEnabled) HyperOsFocusBridge.clearSuperIsland(this)
        if (systemLyricsSongId != songId) resetSystemLyrics(currentItem)
        if (systemLyricsDocument == null && systemLyricsJob?.isActive != true) loadSystemLyrics(songId, currentItem)
        val document = systemLyricsDocument ?: return
        val advance = MeloXSettingsRuntime.lyricAdvanceMs.toLong()
        val index = document.highlightedIndex(active.currentPosition + advance) ?: return
        val now = SystemClock.elapsedRealtime()
        val lineChanged = index != systemLyricsLastIndex
        val playbackChanged = active.isPlaying != systemLyricsLastPlaying
        val periodicRefresh = now - systemLyricsLastDispatchRealtimeMs >= 1_000L
        if (!lineChanged && !playbackChanged && !periodicRefresh) return
        systemLyricsLastIndex = index
        systemLyricsLastPlaying = active.isPlaying
        systemLyricsLastDispatchRealtimeMs = now
        var line = document.lines.getOrNull(index)?.text?.trim().orEmpty()
        val nextLine = document.lines.getOrNull(index + 1)?.text?.trim().orEmpty()
        val original = systemLyricsOriginalMetadata ?: currentItem.mediaMetadata.also {
            systemLyricsOriginalMetadata = it
        }
        if (line.isBlank()) {
            line = renderNotificationTemplate(MeloXSettingsRuntime.lyricNotificationFallback, "", original)
                .ifBlank { original.title?.toString().orEmpty() }
        }
        if (line.isBlank()) return
        if (metadataEnabled && lineChanged) {
            val originalExtras = Bundle(original.extras ?: Bundle()).apply {
                putString(SYSTEM_ORIGINAL_TITLE_KEY, original.title?.toString().orEmpty())
                putString(SYSTEM_ORIGINAL_ARTIST_KEY, original.artist?.toString().orEmpty())
            }
            val titleMode = MeloXSettingsRuntime.systemLyricTitleMode
            val lyricMetadata = original.buildUpon().apply {
                if (titleMode == MeloXSystemLyricTitleMode.LyricFirst) {
                    setTitle(line)
                    setArtist(original.title)
                    setAlbumTitle(original.artist)
                } else {
                    setTitle(original.title)
                    setArtist(line)
                    setAlbumTitle(original.artist)
                }
                setExtras(originalExtras)
            }.build()
            val item = currentItem.buildUpon().setMediaMetadata(lyricMetadata).build()
            updatingSystemLyricsMetadata = true
            active.replaceMediaItem(active.currentMediaItemIndex, item)
            handler.post { updatingSystemLyricsMetadata = false }
        } else if (!metadataEnabled) {
            restoreSystemLyricsMetadata(active)
        }
        val notificationAllowedByScene =
            (!MeloXSettingsRuntime.lyricNotificationBackgroundOnly || !MeloXAppVisibility.isForeground) &&
                (!MeloXSettingsRuntime.lyricNotificationDismissWhenPaused || active.isPlaying)
        if (effectiveNotification && notificationAllowedByScene) postLyricsNotification(line, nextLine, original) else {
            getSystemService(NotificationManager::class.java).cancel(LYRICS_NOTIFICATION_ID)
        }
    }

    private fun loadSystemLyrics(songId: Long, item: MediaItem) {
        systemLyricsSongId = songId
        systemLyricsOriginalMetadata = item.mediaMetadata
        systemLyricsJob = serviceScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                downloadStore.localLyrics(songId) ?: runCatching {
                    NeteaseSearchClient(
                        cookieProvider = { com.lladlam.melox.core.music.provider.PlaybackAccountStore.neteaseCookie(this@MeloXPlaybackService) },
                    ).lyrics(songId)
                }.getOrNull()
            }
            if (systemLyricsSongId == songId) systemLyricsDocument = loaded?.let(LyricTimelineProcessor::process)
            systemLyricsJob = null
        }
    }

    private fun resetSystemLyrics(item: MediaItem?) {
        VivoAtomicIslandBridge.clear(this)
        systemLyricsJob?.cancel()
        systemLyricsJob = null
        systemLyricsSongId = item?.mediaId?.toLongOrNull()
        systemLyricsDocument = null
        systemLyricsOriginalMetadata = item?.mediaMetadata
        systemLyricsLastIndex = Int.MIN_VALUE
        systemLyricsLastDispatchRealtimeMs = 0L
    }

    private fun restoreSystemLyricsMetadata(active: ExoPlayer) {
        val original = systemLyricsOriginalMetadata ?: return
        val currentIndex = active.currentMediaItemIndex
        val index = currentIndex.takeIf { candidate ->
            candidate in 0 until active.mediaItemCount &&
                active.getMediaItemAt(candidate).mediaMetadata.extras?.containsKey(SYSTEM_ORIGINAL_TITLE_KEY) == true
        } ?: systemLyricsSongId?.let { songId ->
            (0 until active.mediaItemCount).firstOrNull { candidate ->
                val item = active.getMediaItemAt(candidate)
                item.mediaId.toLongOrNull() == songId &&
                    item.mediaMetadata.extras?.containsKey(SYSTEM_ORIGINAL_TITLE_KEY) == true
            }
        } ?: return
        val current = active.getMediaItemAt(index)
        updatingSystemLyricsMetadata = true
        active.replaceMediaItem(index, current.buildUpon().setMediaMetadata(original).build())
        handler.post { updatingSystemLyricsMetadata = false }
    }

    private fun createLyricsNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                LYRICS_NOTIFICATION_CHANNEL,
                "歌词",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示当前播放歌词"
                setSound(null, null)
                enableVibration(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ANALYSIS_NOTIFICATION_CHANNEL,
                "歌曲分析",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示歌单歌曲的音频分析进度"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun postLyricsNotification(line: String, nextLine: String, metadata: MediaMetadata) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_NOW_PLAYING
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val showNext = MeloXSettingsRuntime.lyricNotificationShowNextLine
        val title = renderNotificationTemplate(MeloXSettingsRuntime.lyricNotificationTitleTemplate, line, metadata)
            .ifBlank { line }
        val subtitle = renderNotificationTemplate(MeloXSettingsRuntime.lyricNotificationSubtitleTemplate, line, metadata)
        val detail = listOf(subtitle, nextLine.takeIf { showNext && it.isNotBlank() })
            .filterNotNull().filter(String::isNotBlank).joinToString("\n")
        val builder = NotificationCompat.Builder(this, LYRICS_NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setOngoing(player?.isPlaying == true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        if (MeloXSettingsRuntime.lyricNotificationShowArtwork) {
            metadata.artworkData?.let { bytes ->
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()?.let(builder::setLargeIcon)
            }
        }
        if (MeloXSettingsRuntime.lyricNotificationShowProgress) {
            val duration = player?.duration?.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
            if (duration > 0L) builder.setProgress(1_000, ((player?.currentPosition ?: 0L) * 1_000L / duration).toInt().coerceIn(0, 1_000), false)
        }
        val notification = builder.build()
        VivoAtomicIslandBridge.publish(
            context = this,
            line = line,
            songTitle = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            positionMs = player?.currentPosition ?: 0L,
            durationMs = player?.duration?.takeIf { it != C.TIME_UNSET } ?: 0L,
            isPlaying = player?.isPlaying == true,
            clickIntent = pendingIntent,
        )
        HyperOsFocusBridge.playbackPayload(
            context = this,
            lyric = line,
            songTitle = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            positionMs = player?.currentPosition ?: 0L,
            durationMs = player?.duration?.takeIf { it != C.TIME_UNSET } ?: 0L,
            isPlaying = player?.isPlaying == true,
        )?.let { HyperOsFocusBridge.attachFocusParams(notification, it) }
        getSystemService(NotificationManager::class.java).notify(LYRICS_NOTIFICATION_ID, notification)
    }

    private fun renderNotificationTemplate(template: String, lyric: String, metadata: MediaMetadata): String =
        template
            .replace("{lyric}", lyric)
            .replace("{song}", metadata.title?.toString().orEmpty())
            .replace("{artist}", metadata.artist?.toString().orEmpty())
            .replace("{album}", metadata.albumTitle?.toString().orEmpty())
            .trim().trim('·').trim()

    private fun updateAutoMixEnvelope(active: ExoPlayer, incoming: ExoPlayer) {
        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        val remaining = duration?.minus(active.currentPosition) ?: Long.MAX_VALUE
        val durationMs = mixDurationMs.coerceAtLeast(1L)
        // Drive the envelope from both decks' rendered media clocks, matching
        // upstream MeloX. This prevents the fade from running ahead while the
        // incoming decoder is technically READY but has not advanced audio yet.
        val outgoingProgress =
            (active.currentPosition - mixOutgoingStartPositionMs).coerceAtLeast(0L).toDouble() /
                durationMs.toDouble()
        val incomingProgress =
            (incoming.currentPosition - mixIncomingStartPositionMs).coerceAtLeast(0L).toDouble() /
                durationMs.toDouble()
        val progress = maxOf(
            mixLastProgress,
            minOf(outgoingProgress, incomingProgress),
        ).coerceIn(0.0, 1.0)
        mixLastProgress = progress
        MeloXAutoMixTransitionRuntime.update(
            value = progress,
            positionMs = incoming.currentPosition,
            durationMs = incoming.duration.takeUnless { it == C.TIME_UNSET || it < 0L } ?: 0L,
        )

        val gains = MeloXAutoMixEnvelope.gains(progress, mixSettings.fadeCurve)
        active.volume = mixBaseVolume * gains.outgoing
        incoming.volume = mixBaseVolume * gains.incoming
        active.setPlaybackSpeed(
            MeloXAutoMixEnvelope.rate(mixPlan.outgoingStartRate, mixPlan.outgoingEndRate, progress),
        )
        incoming.setPlaybackSpeed(
            MeloXAutoMixEnvelope.rate(mixPlan.incomingStartRate, mixPlan.incomingEndRate, progress),
        )
        mixEqualizerEnvelope.apply(progress)

        if (progress >= 1.0 ||
            remaining <= mixPlan.outgoingEndOffsetMs + MeloXAutoMixPlanner.HANDOFF_GUARD_MS
        ) {
            completeAutoMix(active, incoming)
        }
    }

    private fun prepareIncoming(active: ExoPlayer, sourceId: String) {
        PlaybackCommands.prioritizeManualQueue(active)
        val nextIndex = active.currentMediaItemIndex + 1
        if (nextIndex !in 0 until active.mediaItemCount) {
            Log.d(TAG, "AutoMix prepareIncoming: nextIndex out of range")
            return
        }
        val nextSongId = active.getMediaItemAt(nextIndex).mediaId.toLongOrNull()
        if (!MeloXNetworkAvailability.isOnline(this) &&
            (nextSongId == null || !downloadStore.contains(nextSongId))
        ) {
            Log.d(TAG, "AutoMix prepareIncoming: offline and next not downloaded")
            return
        }
        // Reuse the inactive deck just like upstream MeloX. Constructing and
        // releasing ExoPlayer at every handoff adds avoidable work at song edges.
        val incoming = incomingPlayer ?: buildPlayer(
            managesAudioFocus = false,
            observesSession = false,
        )
        incoming.stop()
        incoming.clearMediaItems()
        incoming.setAudioAttributes(audioAttributes, false)
        incoming.setHandleAudioBecomingNoisy(false)
        val items = List(active.mediaItemCount) { active.getMediaItemAt(it) }
        incoming.setMediaItems(items, nextIndex, 0L)
        incoming.volume = 0f
        incoming.prepare()
        incomingPlayer = incoming
        preparedMixSourceId = sourceId
        preparedMixTargetId = active.getMediaItemAt(nextIndex).mediaId
        mixStartedAt = 0L
        Log.i(TAG, "AutoMix prepareIncoming done: nextIndex=$nextIndex, nextId=${preparedMixTargetId}")
    }

    private fun startAutoMixAnalysis(
        active: ExoPlayer,
        sourceId: String,
        settings: MeloXAutoMixSettings,
    ) {
        val currentIndex = active.currentMediaItemIndex
        val nextIndex = currentIndex + 1
        if (currentIndex !in 0 until active.mediaItemCount || nextIndex !in 0 until active.mediaItemCount) return
        val outgoingItem = active.getMediaItemAt(currentIndex)
        val incomingItem = active.getMediaItemAt(nextIndex)
        val outgoingAnalysisItem = outgoingItem.copyWithStandardAnalysisUri()
        val incomingAnalysisItem = incomingItem.copyWithStandardAnalysisUri()
        val outgoingId = outgoingItem.mediaId.toLongOrNull() ?: return
        val incomingId = incomingItem.mediaId.toLongOrNull() ?: return
        mixAnalysisSourceId = sourceId
        analyzedMixPlan = null
        mixAnalysisJob = serviceScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    withTimeout(20_000L) {
                        Log.i(TAG, "AutoMix materializing analysis sources: outgoing=$outgoingId, incoming=$incomingId")
                        val outgoingFile = mediaPrefetcher.materialize(outgoingAnalysisItem)
                        val incomingFile = mediaPrefetcher.materialize(incomingAnalysisItem)
                        Log.i(TAG, "AutoMix materialized analysis sources: outgoingBytes=${outgoingFile.length()}, incomingBytes=${incomingFile.length()}")
                        try {
                            val (outgoing, incomingAnalysis) = coroutineScope {
                                val outgoingDeferred = async {
                                        autoMixAnalyzer.analyze(
                                            outgoingId,
                                            android.net.Uri.fromFile(outgoingFile),
                                            windowStartMs = 0L,
                                            windowDurationMs = Long.MAX_VALUE,
                                        )
                                }
                                val incomingDeferred = async {
                                        autoMixAnalyzer.analyze(
                                            incomingId,
                                            android.net.Uri.fromFile(incomingFile),
                                            windowStartMs = 0L,
                                            windowDurationMs = Long.MAX_VALUE,
                                        )
                                }
                                outgoingDeferred.await() to incomingDeferred.await()
                            }
                            val plan = MeloXAutoMixTransitionScorer.plan(settings, outgoing, incomingAnalysis)
                                ?: error(
                                    "analysis rejected: outgoing=${"%.3f".format(outgoing.confidence)}/" +
                                        "${"%.1f".format(outgoing.bpm)} BPM, incoming=" +
                                        "${"%.3f".format(incomingAnalysis.confidence)}/" +
                                        "${"%.1f".format(incomingAnalysis.bpm)} BPM, " +
                                        "minimum=${"%.3f".format(settings.minimumConfidence)}",
                                )
                            Triple(plan, outgoing, incomingAnalysis)
                        } finally {
                            outgoingFile.delete()
                            incomingFile.delete()
                        }
                    }
            }
            }
            val analysisState = "prepared=$preparedMixSourceId, analysisSource=$mixAnalysisSourceId, current=$sourceId"
            result.onSuccess { (plan, outgoing, _) ->
                if (mixAnalysisSourceId == sourceId) {
                    MeloXAudioReactiveRuntime.attach(sourceId, outgoing)
                    analyzedMixPlan = plan
                    Log.i(
                        TAG,
                        "AutoMix analysis ready: source=$sourceId, start=${plan.outgoingStartMs}, " +
                            "incoming=${plan.incomingStartMs}, duration=${plan.durationMs}",
                    )
                } else {
                    Log.w(TAG, "AutoMix analysis result discarded: $analysisState")
                }
            }.onFailure { error ->
                if (mixAnalysisSourceId == sourceId) {
                    analyzedMixPlan = null
                    Log.w(TAG, "AutoMix smart analysis unavailable for $sourceId: ${error.message}")
                } else {
                    Log.w(TAG, "AutoMix analysis failure discarded: $analysisState, error=${error.message}")
                }
            }
            mixAnalysisJob = null
        }
    }

    /**
     * Incoming has already been playing for the entire overlap. Promotion must
     * therefore never seek it again: seeking at handoff creates the audible
     * forward/backward jump reported at the outgoing song's original endpoint.
     */
    private fun completeAutoMix(old: ExoPlayer, incoming: ExoPlayer) {
        handler.removeCallbacks(mixEnvelope)
        mixEqualizerEnvelope.release()
        old.volume = 0f
        incoming.volume = mixBaseVolume
        incoming.setPlaybackSpeed(1f)
        val managesAudioFocus = shouldManageAudioFocus()
        incoming.setAudioAttributes(audioAttributes, managesAudioFocus)
        incoming.setHandleAudioBecomingNoisy(managesAudioFocus)
        appliedAudioFocusPolicy = managesAudioFocus
        incoming.addListener(playerListener)
        val session = mediaSession ?: error("MediaSession unavailable during AutoMix handoff")
        if (session.player !== incoming) session.setPlayer(incoming)
        // Publish both deck references together. After this point cleanup must
        // not throw, otherwise recovery could mistake the promoted deck for the
        // standby player and stop the song that is already audible.
        player = incoming
        incomingPlayer = old
        mixSessionOnIncoming = false
        // The listener is attached after this deck already owns its session, so
        // an audio-session callback is not guaranteed during promotion.
        equalizerController.attach(incoming.audioSessionId)
        autoMixRetrySourceId = null
        autoMixRetryAfterRealtimeMs = 0L
        preparedMixSourceId = null
        preparedMixTargetId = null
        mixAnalysisJob?.cancel()
        mixAnalysisJob = null
        mixAnalysisSourceId = null
        analyzedMixPlan = null
        mixStartedAt = 0L
        mixDurationMs = 0L
        mixOutgoingStartPositionMs = 0L
        mixIncomingStartPositionMs = 0L
        mixLastProgress = 0.0
        MeloXAutoMixTransitionRuntime.handoff()
        runCatching { old.removeListener(playerListener) }
        runCatching { old.pause() }
        runCatching { old.stop() }
        runCatching { old.clearMediaItems() }
        runCatching { old.setAudioAttributes(audioAttributes, false) }
        runCatching { old.setHandleAudioBecomingNoisy(false) }
        runCatching { old.setPlaybackSpeed(1f) }
        runCatching { old.volume = 0f }
        runCatching { applyLocalArtworkMetadata(incoming) }
        incoming.currentMediaItem?.mediaId?.let(::scheduleSmartQueueNext)
    }

    private fun applyLocalArtworkMetadata(active: ExoPlayer) {
        val index = active.currentMediaItemIndex
        if (index !in 0 until active.mediaItemCount) return
        val item = active.getMediaItemAt(index)
        val songId = item.mediaId.toLongOrNull() ?: return
        val localArtwork = downloadStore.localArtworkUri(songId) ?: return
        if (item.mediaMetadata.artworkUri == localArtwork) return
        val localItem = item.buildUpon()
            .setMediaMetadata(
                item.mediaMetadata.buildUpon()
                    .setArtworkUri(localArtwork)
                    .build(),
            )
            .build()
        active.replaceMediaItem(index, localItem)
    }

    private fun downloaded(mediaId: String): Boolean {
        mediaId.toLongOrNull()?.takeIf { downloadStore.contains(it) }?.let { return true }
        val id = PlaybackTrackIdentity.decode(mediaId) ?: return false
        return providerDownloadStore.isDownloaded(id)
    }

    private fun skipToNextDownloaded(active: ExoPlayer): Boolean {
        val current = active.currentMediaItemIndex
        if (current !in 0 until active.mediaItemCount) return false
        val forward = ((current + 1) until active.mediaItemCount).toList()
        val wrapped = if (active.repeatMode == Player.REPEAT_MODE_ALL) {
            (0 until current).toList()
        } else {
            emptyList()
        }
        val target = (forward + wrapped).firstOrNull { index ->
            downloaded(active.getMediaItemAt(index).mediaId)
        } ?: return false
        cancelPreparedMix()
        active.seekToDefaultPosition(target)
        if (active.playbackState == Player.STATE_IDLE) active.prepare()
        active.play()
        return true
    }

    private fun hasPreparedMix(): Boolean = preparedMixSourceId != null || mixStartedAt > 0L

    private fun pauseAllAndCancelMix() {
        val outgoing = player
        val incoming = incomingPlayer
        runCatching { outgoing?.pause() }
        runCatching { incoming?.pause() }
        cancelPreparedMix()
    }

    private fun promoteIncomingSessionDuringMix(): Boolean {
        if (mixStartedAt <= 0L) return false
        if (mixSessionOnIncoming) return true
        val incoming = incomingPlayer ?: return false
        if (incoming.currentMediaItem?.mediaId != preparedMixTargetId) return false
        val session = mediaSession ?: return false
        return runCatching {
            mixSessionOnIncoming = true
            session.setPlayer(incoming)
            Log.i(TAG, "AutoMix next: session promoted early to ${preparedMixTargetId}")
            true
        }.getOrElse { error ->
            mixSessionOnIncoming = false
            Log.e(TAG, "AutoMix next: unable to promote incoming session", error)
            false
        }
    }

    private fun cancelPreparedMix(releaseStandby: Boolean = false) {
        if (cancellingMix) return
        cancellingMix = true
        try {
            handler.removeCallbacks(mixEnvelope)
            mixEqualizerEnvelope.release()
            mixAnalysisJob?.cancel()
            mixAnalysisJob = null
            mixAnalysisSourceId = null
            analyzedMixPlan = null
            val active = player
            val incoming = incomingPlayer
            if (mixSessionOnIncoming && active != null && mediaSession?.player === incoming) {
                runCatching { mediaSession?.setPlayer(active) }
                    .onFailure { Log.e(TAG, "AutoMix cancellation could not restore outgoing session", it) }
            }
            mixSessionOnIncoming = false
            if (mixStartedAt > 0L && active != null) {
                runCatching { active.volume = mixBaseVolume }
                runCatching { active.setPlaybackSpeed(1f) }
            }
            incomingPlayer?.run {
                runCatching { removeListener(playerListener) }
                runCatching { pause() }
                runCatching { stop() }
                runCatching { clearMediaItems() }
                runCatching { volume = 0f }
                runCatching { setPlaybackSpeed(1f) }
                if (releaseStandby) runCatching { release() }
            }
            if (releaseStandby) incomingPlayer = null
            preparedMixSourceId = null
            preparedMixTargetId = null
            mixStartedAt = 0L
            mixDurationMs = 0L
            mixOutgoingStartPositionMs = 0L
            mixIncomingStartPositionMs = 0L
            mixLastProgress = 0.0
            MeloXAutoMixTransitionRuntime.clear()
            mixSettings = MeloXAutoMixSettings()
            mixPlan = MeloXAutoMixPlan(0L, 0L)
        } finally {
            cancellingMix = false
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Log.d(TAG, "Controller connected: ${controllerInfo.packageName}")
        val allowed = controllerInfo.packageName == packageName ||
            controllerInfo.packageName == "com.google.android.projection.gearhead" ||
            controllerInfo.packageName == "com.android.bluetooth"
        return mediaSession.takeIf { allowed }
    }

    override fun onDestroy() {
        persistQueueIfEnabled()
        historySongId?.let { playbackHistoryReporter.recordDuration(it, elapsedMs = listenedTimeTracker.elapsedMs(SystemClock.elapsedRealtime())) }
        historySongId = null
        playbackHistoryReporter.close()
        handler.removeCallbacks(modeMonitor)
        recommendationJob?.cancel()
        playlistAnalysisJob?.cancel()
        backgroundAnalysisJob?.cancel()
        smartQueueJob?.cancel()
        systemLyricsJob?.cancel()
        VivoAtomicIslandBridge.clear(this)
        getSystemService(NotificationManager::class.java).cancel(LYRICS_NOTIFICATION_ID)
        serviceScope.cancel()
        cancelPreparedMix(releaseStandby = true)
        equalizerController.release()
        autoMixAnalyzer.clear()
        mediaPrefetcher.clearAnalysisFiles()
        MeloXAudioReactiveRuntime.clear()
        mediaSession?.release()
        mediaSession = null
        player?.removeListener(playerListener)
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistQueueIfEnabled()
        super.onTaskRemoved(rootIntent)
    }

    private fun persistQueueIfEnabled() {
        if (!MeloXSettingsPreferences.boolean(this, "playback_save_queue", true)) return
        player?.let { MeloXPlaybackQueueStore.save(this, it) }
    }

    private fun restorePersistedQueue(active: ExoPlayer) {
        if (!MeloXSettingsPreferences.boolean(this, "playback_save_queue", true)) return
        val saved = MeloXPlaybackQueueStore.read(this) ?: return
        if (active.mediaItemCount > 0) return
        active.setMediaItems(saved.items, saved.index, saved.positionMs)
        active.prepare()
        active.pause()
        Log.i(TAG, "Restored persisted playback queue: ${saved.items.size} items, index=${saved.index}")
    }

    companion object {
        const val ACTION_ANALYZE_PLAYLIST = "com.lladlam.melox.action.ANALYZE_PLAYLIST"
        const val EXTRA_ANALYSIS_SOURCE = "analysis_source"
        const val EXTRA_ANALYSIS_PLAYLIST_ID = "analysis_playlist_id"
        const val TAG = "MeloXPlayback"
        const val AUTOPLAY_PRELOAD_MS = 60_000L
        const val AUTOPLAY_RETRY_MS = 15_000L
        const val PREFETCH_TRACK_COUNT = 3
        const val PLAYBACK_MAINTENANCE_INTERVAL_MS = 1_000L
        const val AUTOMIX_ENVELOPE_INTERVAL_MS = 20L
        const val AUTOMIX_FAILURE_COOLDOWN_MS = 30_000L
        const val SMART_QUEUE_ANALYSIS_CONCURRENCY = 2
        const val ANALYSIS_FALLBACK_GUARD_MS = 1_200L
        const val ANALYSIS_WINDOW_MS = 30_000L
        const val ACTIVE_MONITOR_INTERVAL_MS = 100L
        const val PAUSED_MONITOR_INTERVAL_MS = 500L
        const val IDLE_MONITOR_INTERVAL_MS = 2_000L
        const val SETTINGS_SNAPSHOT_INTERVAL_MS = 1_000L
        const val SLEEP_TIMER_END_KEY = "playback_sleep_timer_end_epoch_ms"
        const val SYSTEM_ORIGINAL_TITLE_KEY = "melox.system.original_title"
        const val SYSTEM_ORIGINAL_ARTIST_KEY = "melox.system.original_artist"
        const val LYRICS_NOTIFICATION_CHANNEL = "melox_lyrics"
        const val LYRICS_NOTIFICATION_ID = 1702
        const val ANALYSIS_NOTIFICATION_CHANNEL = "melox_analysis"
        const val ANALYSIS_NOTIFICATION_ID = 1004
    }
}

internal fun shouldClearLegacySystemLyrics(mediaId: String?): Boolean =
    mediaId?.toLongOrNull()?.takeIf { it > 0L } == null

internal fun shouldResumeEndedAutoplay(playbackState: Int, hasNextMediaItem: Boolean): Boolean =
    playbackState == Player.STATE_ENDED && hasNextMediaItem

internal fun selectNeteaseAutoplaySeed(
    title: String,
    artist: String,
    durationMs: Long,
    candidates: List<SearchSong>,
): Long? {
    val titleKey = normalizeAutoplayMetadata(title)
    val artistKeys = autoplayArtistKeys(artist)
    return candidates.asSequence()
        .filter { normalizeAutoplayMetadata(it.name) == titleKey }
        .filter { candidate ->
            val candidateArtists = autoplayArtistKeys(candidate.artists)
            artistKeys.isEmpty() || candidateArtists.any(artistKeys::contains)
        }
        .filter { candidate ->
            durationMs <= 0L || candidate.durationMs > 0L &&
                kotlin.math.abs(durationMs - candidate.durationMs) <= 3_000L
        }
        .maxByOrNull { candidate ->
            val candidateArtists = autoplayArtistKeys(candidate.artists)
            (if (candidateArtists == artistKeys) 100 else 50) -
                (if (durationMs > 0L) kotlin.math.abs(durationMs - candidate.durationMs) / 1_000L else 0L).toInt()
        }
        ?.id
}

private fun normalizeAutoplayMetadata(value: String): String = value.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]"), "")

private fun autoplayArtistKeys(value: String): Set<String> = value
    .split(Regex("\\s*(?:/|、|,|，|&|;|；)\\s*"))
    .map(::normalizeAutoplayMetadata)
    .filter(String::isNotBlank)
    .toSet()
