package com.lladlam.melox.playback

import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.core.app.NotificationCompat
import com.lladlam.melox.MainActivity
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.network.MeloXNetworkAvailability
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.platform.xiaomi.HyperOsFocusBridge
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import com.lladlam.melox.ui.settings.MeloXSystemLyricTitleMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class MeloXPlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var incomingPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private lateinit var mediaSourceFactory: DefaultMediaSourceFactory
    private lateinit var downloadStore: MeloXDownloadStore
    private lateinit var playbackResolver: NeteasePlaybackResolver
    private lateinit var autoMixAnalyzer: MeloXAutoMixAudioAnalyzer
    private lateinit var equalizerController: MeloXEqualizerController
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var recommendationJob: Job? = null
    private var recommendationSeed: Long? = null
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
    private var reactiveAnalysisJob: Job? = null
    private var reactiveAnalysisMediaId: String? = null
    private var preparedMixSourceId: String? = null
    private var autoMixRetrySourceId: String? = null
    private var autoMixRetryAfterRealtimeMs = 0L
    private var mixStartedAt = 0L
    private var mixDurationMs = 0L
    private var mixBaseVolume = 1f
    private var mixOutgoingStartPositionMs = 0L
    private var mixIncomingStartPositionMs = 0L
    private var mixLastProgress = 0.0
    private var mixSettings = MeloXAutoMixSettings()
    private var mixPlan = MeloXAutoMixPlan(0L, 0L)
    private val mixEqualizerEnvelope = MeloXAutoMixEqualizerEnvelope()

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val active = player
            if (active != null && !MeloXNetworkAvailability.isOnline(this@MeloXPlaybackService)) {
                if (skipToNextDownloaded(active)) {
                    Log.i(TAG, "Offline playback skipped unavailable item after player error")
                    return
                }
            }
            Log.e(TAG, "Playback failed: code=${error.errorCodeName}, message=${error.message}", error)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "isPlaying=$isPlaying, ongoing=${isPlaybackOngoing()}")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val active = player ?: return
            if (playbackState == Player.STATE_ENDED) {
                if (!MeloXNetworkAvailability.isOnline(this@MeloXPlaybackService)) {
                    if (!skipToNextDownloaded(active)) active.pause()
                    return
                }
                ensureAutoplayRecommendations(forceAdvance = true)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            recommendationSeed = null
            autoMixRetrySourceId = null
            autoMixRetryAfterRealtimeMs = 0L
            val transitionedId = mediaItem?.mediaId?.toLongOrNull()
            reactiveAnalysisJob?.cancel()
            reactiveAnalysisJob = null
            reactiveAnalysisMediaId = null
            MeloXAudioReactiveRuntime.select(mediaItem?.mediaId)
            mediaItem?.let(downloadStore::recordPlayback)
            if (transitionedId != systemLyricsSongId) resetSystemLyrics(mediaItem)
            val active = player
            if (active != null) {
                applyLocalArtworkMetadata(active)
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
            if (mixStartedAt == 0L) cancelPreparedMix()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            equalizerController.attach(audioSessionId)
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (updatingSystemLyricsMetadata) return
            if (mixStartedAt == 0L) cancelPreparedMix()
        }
    }

    private val modeMonitor = object : Runnable {
        override fun run() {
            val active = player
            if (active != null) {
                runCatching {
                    applyLocalArtworkMetadata(active)
                    PlaybackCommands.prioritizeManualQueue(active)
                    maybePrepareAutoplay(active)
                    maybeRunAutoMix(active)
                    enforceSleepTimer(active)
                    maybeUpdateSystemLyrics(active)
                    equalizerController.applySettings()
                    updateAudioReactiveVisuals(active)
                }.onFailure { error ->
                    Log.e(TAG, "Playback monitor recovered from failure", error)
                    recoverAutoMixFailure()
                }
            }
            handler.postDelayed(this, 100L)
        }
    }

    private fun updateAudioReactiveVisuals(active: ExoPlayer) {
        val item = active.currentMediaItem ?: return
        MeloXAudioReactiveRuntime.publish(item.mediaId, active.currentPosition, active.isPlaying)
        if (!MeloXSettingsPreferences.boolean(this, "player_flowing_backdrop", true)) return
        if (reactiveAnalysisMediaId == item.mediaId || reactiveAnalysisJob?.isActive == true) return
        val songId = item.mediaId.toLongOrNull() ?: return
        reactiveAnalysisMediaId = item.mediaId
        reactiveAnalysisJob = serviceScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val quality = MusicQualityPreferences.read(this@MeloXPlaybackService)
                    val uri = playbackResolver.resolveSongUri(songId, quality)
                    autoMixAnalyzer.analyze(songId, uri)
                }
            }
            result.onSuccess { MeloXAudioReactiveRuntime.attach(item.mediaId, it) }
                .onFailure { Log.w(TAG, "Audio-reactive analysis unavailable for ${item.mediaId}", it) }
            reactiveAnalysisJob = null
        }
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
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36",
                    "Referer" to "https://music.163.com/",
                ),
            )
        downloadStore = MeloXDownloadStore.get(this)
        equalizerController = MeloXEqualizerController(this)
        val cookieProvider = { NeteaseSessionStore.readCookie(this@MeloXPlaybackService) }
        playbackResolver = NeteasePlaybackResolver(
            cookieProvider = cookieProvider,
            client = NeteaseSearchClient(cookieProvider = cookieProvider),
            localSourceProvider = downloadStore::localPlaybackUri,
        )
        autoMixAnalyzer = MeloXAutoMixAudioAnalyzer(this)
        val resolving = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this, httpFactory),
            playbackResolver,
        )
        mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(resolving)

        val active = buildPlayer(managesAudioFocus = true)
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
            .build()
        createLyricsNotificationChannel()
        handler.post(modeMonitor)
    }

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

    private fun maybePrepareAutoplay(active: ExoPlayer) {
        if (!MeloXNetworkAvailability.isOnline(this)) return
        if (!MeloXPlaybackModePreferences.autoplay(this)) return
        if (active.mediaItemCount <= 0 || active.currentMediaItemIndex < 0) return
        val atTail = active.currentMediaItemIndex >= active.mediaItemCount - 1
        if (!atTail) return
        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return
        val remaining = duration - active.currentPosition
        if (remaining <= AUTOPLAY_PRELOAD_MS) ensureAutoplayRecommendations(forceAdvance = false)
    }

    private fun ensureAutoplayRecommendations(forceAdvance: Boolean) {
        if (!MeloXNetworkAvailability.isOnline(this)) return
        if (!MeloXPlaybackModePreferences.autoplay(this)) return
        val active = player ?: return
        val seed = active.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        if (recommendationJob?.isActive == true || recommendationSeed == seed) {
            if (forceAdvance && active.playbackState == Player.STATE_ENDED && active.hasNextMediaItem()) {
                active.seekToNextMediaItem()
                active.play()
            }
            return
        }
        recommendationSeed = seed
        recommendationJob = serviceScope.launch {
            val cookie = { NeteaseSessionStore.readCookie(this@MeloXPlaybackService) }
            val recommendations = withContext(Dispatchers.IO) {
                runCatching { NeteaseLibraryClient(cookieProvider = cookie).similarSongsBlocking(seed, 30) }
                    .getOrDefault(emptyList())
            }
            val existing = (0 until active.mediaItemCount).map { active.getMediaItemAt(it).mediaId }.toSet()
            val quality = MusicQualityPreferences.read(this@MeloXPlaybackService)
            recommendations
                .filterNot { it.id.toString() in existing }
                .take(20)
                .forEach { song ->
                    active.addMediaItem(
                        PlaybackCommands.mediaItemFor(
                            song,
                            quality,
                            PlaybackCommands.QUEUE_ORIGIN_BASE,
                        ),
                    )
                }
            PlaybackCommands.prioritizeManualQueue(active)
            if (forceAdvance && active.playbackState == Player.STATE_ENDED && active.hasNextMediaItem()) {
                active.seekToNextMediaItem()
                active.prepare()
                active.play()
            }
            recommendationJob = null
        }
    }

    private fun maybeRunAutoMix(active: ExoPlayer) {
        if (!MeloXPlaybackModePreferences.autoMix(this)) {
            cancelPreparedMix(releaseStandby = true)
            return
        }
        if (!active.isPlaying || active.repeatMode == Player.REPEAT_MODE_ONE || !active.hasNextMediaItem()) return
        PlaybackCommands.prioritizeManualQueue(active)
        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return
        val remaining = duration - active.currentPosition
        val sourceId = active.currentMediaItem?.mediaId ?: return
        if (autoMixRetrySourceId == sourceId && SystemClock.elapsedRealtime() < autoMixRetryAfterRealtimeMs) return
        if (autoMixRetrySourceId != null && autoMixRetrySourceId != sourceId) {
            autoMixRetrySourceId = null
            autoMixRetryAfterRealtimeMs = 0L
        }
        val settings = MeloXAutoMixSettings.read(this)
        if (preparedMixSourceId == null && remaining <= settings.preloadLeadMs) {
            prepareIncoming(active, sourceId)
        }
        val incoming = incomingPlayer ?: return
        if (preparedMixSourceId != sourceId) {
            if (mixStartedAt > 0L) completeAutoMix(active, incoming) else cancelPreparedMix()
            return
        }
        if (settings.mode == MeloXAutoMixMode.Smart &&
            mixAnalysisSourceId != sourceId &&
            mixAnalysisJob?.isActive != true
        ) {
            startAutoMixAnalysis(active, sourceId, settings)
        }
        val candidate = when (settings.mode) {
            MeloXAutoMixMode.Fixed -> MeloXAutoMixPlanner.plan(settings, remaining)
            MeloXAutoMixMode.Smart -> analyzedMixPlan ?: run {
                val fallback = MeloXAutoMixPlanner.plan(settings, remaining)
                // Give full-track analysis the whole preload window. Only use
                // the selected failure policy when the transition is imminent.
                if (remaining <= fallback.durationMs + ANALYSIS_FALLBACK_GUARD_MS) fallback else return
            }
        }
        if (!candidate.performsTransition) return
        val reachedTransitionPoint = if (candidate.usedSmartAnalysis) {
            active.currentPosition >= candidate.outgoingStartMs
        } else {
            remaining <= candidate.durationMs + candidate.outgoingEndOffsetMs
        }
        if (mixStartedAt == 0L && incoming.playbackState == Player.STATE_READY && reachedTransitionPoint) {
            // Do not start the second live decoder while either full-track
            // analyser still owns a MediaCodec instance. Cancellation is
            // cooperative; the next 100 ms monitor tick starts the mix after
            // the codec's finally block has released the native resource.
            val activeAnalysis = listOfNotNull(mixAnalysisJob, reactiveAnalysisJob)
                .filter(Job::isActive)
            if (activeAnalysis.isNotEmpty()) {
                activeAnalysis.forEach(Job::cancel)
                return
            }
            val actualDuration = minOf(
                candidate.durationMs,
                remaining - candidate.outgoingEndOffsetMs,
            )
            if (actualDuration < MeloXAutoMixPlanner.MIN_DURATION_MS) return
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
                handler.removeCallbacks(mixEnvelope)
                handler.post(mixEnvelope)
            }.onFailure { error ->
                Log.e(TAG, "AutoMix start failed; continuing current song", error)
                recoverAutoMixFailure()
            }
        }
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
        val metadataEnabled = MeloXSettingsPreferences.boolean(this, "system_lyrics_enabled", false)
        val notificationEnabled = MeloXSettingsPreferences.boolean(this, "lyrics_notifications_enabled", false)
        val currentItem = active.currentMediaItem ?: return
        val songId = currentItem.mediaId.toLongOrNull() ?: return
        if (!metadataEnabled && !notificationEnabled) {
            restoreSystemLyricsMetadata(active)
            (getSystemService(NotificationManager::class.java)).cancel(LYRICS_NOTIFICATION_ID)
            return
        }
        if (systemLyricsSongId != songId) resetSystemLyrics(currentItem)
        if (systemLyricsDocument == null && systemLyricsJob?.isActive != true) loadSystemLyrics(songId, currentItem)
        val document = systemLyricsDocument ?: return
        val advance = MeloXSettingsPreferences.int(this, "lyrics_advance_ms", 0).toLong()
        val index = document.highlightedIndex(active.currentPosition + advance) ?: return
        val now = SystemClock.elapsedRealtime()
        val lineChanged = index != systemLyricsLastIndex
        val playbackChanged = active.isPlaying != systemLyricsLastPlaying
        val periodicRefresh = now - systemLyricsLastDispatchRealtimeMs >= 1_000L
        if (!lineChanged && !playbackChanged && !periodicRefresh) return
        systemLyricsLastIndex = index
        systemLyricsLastPlaying = active.isPlaying
        systemLyricsLastDispatchRealtimeMs = now
        val line = document.lines.getOrNull(index)?.text?.trim().orEmpty()
        val nextLine = document.lines.getOrNull(index + 1)?.text?.trim().orEmpty()
        if (line.isBlank()) return
        val original = systemLyricsOriginalMetadata ?: currentItem.mediaMetadata.also {
            systemLyricsOriginalMetadata = it
        }
        if (metadataEnabled && lineChanged) {
            val originalExtras = Bundle(original.extras ?: Bundle()).apply {
                putString(SYSTEM_ORIGINAL_TITLE_KEY, original.title?.toString().orEmpty())
                putString(SYSTEM_ORIGINAL_ARTIST_KEY, original.artist?.toString().orEmpty())
            }
            val titleMode = runCatching {
                MeloXSystemLyricTitleMode.valueOf(
                    MeloXSettingsPreferences.string(
                        this,
                        "system_lyrics_title_mode",
                        MeloXSystemLyricTitleMode.LyricFirst.name,
                    ),
                )
            }.getOrDefault(MeloXSystemLyricTitleMode.LyricFirst)
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
        if (notificationEnabled) postLyricsNotification(line, nextLine, original) else {
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
                        cookieProvider = { NeteaseSessionStore.readCookie(this@MeloXPlaybackService) },
                    ).lyrics(songId)
                }.getOrNull()
            }
            if (systemLyricsSongId == songId) systemLyricsDocument = loaded
            systemLyricsJob = null
        }
    }

    private fun resetSystemLyrics(item: MediaItem?) {
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
        val index = active.currentMediaItemIndex
        if (index !in 0 until active.mediaItemCount) return
        val current = active.getMediaItemAt(index)
        if (current.mediaMetadata.extras?.containsKey(SYSTEM_ORIGINAL_TITLE_KEY) != true) return
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
        val titleMode = runCatching {
            MeloXSystemLyricTitleMode.valueOf(
                MeloXSettingsPreferences.string(this, "system_lyrics_title_mode", MeloXSystemLyricTitleMode.LyricFirst.name),
            )
        }.getOrDefault(MeloXSystemLyricTitleMode.LyricFirst)
        val songLabel = listOf(metadata.title?.toString(), metadata.artist?.toString())
            .filter { !it.isNullOrBlank() }.joinToString(" · ")
        val showNext = MeloXSettingsPreferences.boolean(this, "lyrics_notification_next_line", false)
        val detail = if (showNext && nextLine.isNotBlank()) "$songLabel\n$nextLine" else songLabel
        val builder = NotificationCompat.Builder(this, LYRICS_NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(if (titleMode == MeloXSystemLyricTitleMode.LyricFirst) line else metadata.title)
            .setContentText(if (titleMode == MeloXSystemLyricTitleMode.LyricFirst) detail else line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (titleMode == MeloXSystemLyricTitleMode.LyricFirst) detail else listOf(line, nextLine.takeIf { showNext }).filterNotNull().joinToString("\n")))
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setOngoing(player?.isPlaying == true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        if (MeloXSettingsPreferences.boolean(this, "lyrics_notification_progress", true)) {
            val duration = player?.duration?.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
            if (duration > 0L) builder.setProgress(1_000, ((player?.currentPosition ?: 0L) * 1_000L / duration).toInt().coerceIn(0, 1_000), false)
        }
        val notification = builder.build()
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
        if (nextIndex !in 0 until active.mediaItemCount) return
        val nextSongId = active.getMediaItemAt(nextIndex).mediaId.toLongOrNull()
        if (!MeloXNetworkAvailability.isOnline(this) &&
            (nextSongId == null || !downloadStore.contains(nextSongId))
        ) {
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
        mixStartedAt = 0L
    }

    private fun startAutoMixAnalysis(
        active: ExoPlayer,
        sourceId: String,
        settings: MeloXAutoMixSettings,
    ) {
        val currentIndex = active.currentMediaItemIndex
        val nextIndex = currentIndex + 1
        if (currentIndex !in 0 until active.mediaItemCount || nextIndex !in 0 until active.mediaItemCount) return
        val outgoingId = active.getMediaItemAt(currentIndex).mediaId.toLongOrNull() ?: return
        val incomingId = active.getMediaItemAt(nextIndex).mediaId.toLongOrNull() ?: return
        mixAnalysisSourceId = sourceId
        analyzedMixPlan = null
        mixAnalysisJob = serviceScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val quality = MusicQualityPreferences.read(this@MeloXPlaybackService)
                    val outgoingUri = playbackResolver.resolveSongUri(outgoingId, quality)
                    val incomingUri = playbackResolver.resolveSongUri(incomingId, quality)
                    if (!settings.analyzeStreaming &&
                        (outgoingUri.scheme in setOf("http", "https") || incomingUri.scheme in setOf("http", "https"))
                    ) {
                        error("streaming analysis disabled")
                    }
                    val (outgoing, incomingAnalysis) = coroutineScope {
                        val outgoingDeferred = async { autoMixAnalyzer.analyze(outgoingId, outgoingUri) }
                        val incomingDeferred = async { autoMixAnalyzer.analyze(incomingId, incomingUri) }
                        outgoingDeferred.await() to incomingDeferred.await()
                    }
                    MeloXAutoMixTransitionScorer.plan(settings, outgoing, incomingAnalysis)
                        ?: error("analysis confidence below threshold")
                }
            }
            if (preparedMixSourceId == sourceId && mixAnalysisSourceId == sourceId) {
                result.onSuccess { plan ->
                    analyzedMixPlan = plan
                    Log.i(
                        TAG,
                        "AutoMix analysis ready: source=$sourceId, start=${plan.outgoingStartMs}, " +
                            "incoming=${plan.incomingStartMs}, duration=${plan.durationMs}",
                    )
                }.onFailure { error ->
                    analyzedMixPlan = null
                    Log.w(TAG, "AutoMix smart analysis unavailable for $sourceId", error)
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
        incoming.setAudioAttributes(audioAttributes, true)
        incoming.setHandleAudioBecomingNoisy(true)
        incoming.addListener(playerListener)
        val session = mediaSession ?: error("MediaSession unavailable during AutoMix handoff")
        session.setPlayer(incoming)
        // Publish both deck references together. After this point cleanup must
        // not throw, otherwise recovery could mistake the promoted deck for the
        // standby player and stop the song that is already audible.
        player = incoming
        incomingPlayer = old
        // The listener is attached after this deck already owns its session, so
        // an audio-session callback is not guaranteed during promotion.
        equalizerController.attach(incoming.audioSessionId)
        autoMixRetrySourceId = null
        autoMixRetryAfterRealtimeMs = 0L
        preparedMixSourceId = null
        mixAnalysisJob?.cancel()
        mixAnalysisJob = null
        mixAnalysisSourceId = null
        analyzedMixPlan = null
        mixStartedAt = 0L
        mixDurationMs = 0L
        mixOutgoingStartPositionMs = 0L
        mixIncomingStartPositionMs = 0L
        mixLastProgress = 0.0
        runCatching { old.removeListener(playerListener) }
        runCatching { old.pause() }
        runCatching { old.stop() }
        runCatching { old.clearMediaItems() }
        runCatching { old.setAudioAttributes(audioAttributes, false) }
        runCatching { old.setHandleAudioBecomingNoisy(false) }
        runCatching { old.setPlaybackSpeed(1f) }
        runCatching { old.volume = 0f }
        runCatching { applyLocalArtworkMetadata(incoming) }
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
            active.getMediaItemAt(index).mediaId.toLongOrNull()?.let(downloadStore::contains) == true
        } ?: return false
        cancelPreparedMix()
        active.seekToDefaultPosition(target)
        if (active.playbackState == Player.STATE_IDLE) active.prepare()
        active.play()
        return true
    }

    private fun cancelPreparedMix(releaseStandby: Boolean = false) {
        handler.removeCallbacks(mixEnvelope)
        mixEqualizerEnvelope.release()
        mixAnalysisJob?.cancel()
        mixAnalysisJob = null
        mixAnalysisSourceId = null
        analyzedMixPlan = null
        val active = player
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
        mixStartedAt = 0L
        mixDurationMs = 0L
        mixOutgoingStartPositionMs = 0L
        mixIncomingStartPositionMs = 0L
        mixLastProgress = 0.0
        mixSettings = MeloXAutoMixSettings()
        mixPlan = MeloXAutoMixPlan(0L, 0L)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Log.d(TAG, "Controller connected: ${controllerInfo.packageName}")
        return mediaSession
    }

    override fun onDestroy() {
        handler.removeCallbacks(modeMonitor)
        recommendationJob?.cancel()
        systemLyricsJob?.cancel()
        getSystemService(NotificationManager::class.java).cancel(LYRICS_NOTIFICATION_ID)
        serviceScope.cancel()
        cancelPreparedMix(releaseStandby = true)
        equalizerController.release()
        MeloXAudioReactiveRuntime.clear()
        mediaSession?.release()
        mediaSession = null
        player?.removeListener(playerListener)
        player?.release()
        player = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MeloXPlayback"
        const val AUTOPLAY_PRELOAD_MS = 15_000L
        const val AUTOMIX_ENVELOPE_INTERVAL_MS = 20L
        const val AUTOMIX_FAILURE_COOLDOWN_MS = 30_000L
        const val ANALYSIS_FALLBACK_GUARD_MS = 1_200L
        const val SLEEP_TIMER_END_KEY = "playback_sleep_timer_end_epoch_ms"
        const val SYSTEM_ORIGINAL_TITLE_KEY = "melox.system.original_title"
        const val SYSTEM_ORIGINAL_ARTIST_KEY = "melox.system.original_artist"
        const val LYRICS_NOTIFICATION_CHANNEL = "melox_lyrics"
        const val LYRICS_NOTIFICATION_ID = 1702
    }
}
