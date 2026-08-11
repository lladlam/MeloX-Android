package com.lladlam.melox.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
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
import com.lladlam.melox.MainActivity
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.network.MeloXNetworkAvailability
import com.lladlam.melox.core.network.NeteaseSearchClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class MeloXPlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var incomingPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private lateinit var mediaSourceFactory: DefaultMediaSourceFactory
    private lateinit var downloadStore: MeloXDownloadStore
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var recommendationJob: Job? = null
    private var recommendationSeed: Long? = null
    private var preparedMixSourceId: String? = null
    private var mixStartedAt = 0L
    private var mixDurationMs = 0L
    private var mixBaseVolume = 1f
    private var mixOutgoingStartPositionMs = 0L
    private var mixIncomingStartPositionMs = 0L
    private var mixLastProgress = 0.0
    private var mixSettings = MeloXAutoMixSettings()
    private var mixPlan = MeloXAutoMixPlan(0L, 0L)

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

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (mixStartedAt == 0L) cancelPreparedMix()
        }
    }

    private val modeMonitor = object : Runnable {
        override fun run() {
            val active = player
            if (active != null) {
                applyLocalArtworkMetadata(active)
                PlaybackCommands.prioritizeManualQueue(active)
                maybePrepareAutoplay(active)
                maybeRunAutoMix(active)
            }
            handler.postDelayed(this, 100L)
        }
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
            updateAutoMixEnvelope(active, incoming)
            if (mixStartedAt > 0L) {
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
        val cookieProvider = { NeteaseSessionStore.readCookie(this@MeloXPlaybackService) }
        val resolving = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this, httpFactory),
            NeteasePlaybackResolver(
                cookieProvider = cookieProvider,
                client = NeteaseSearchClient(cookieProvider = cookieProvider),
                localSourceProvider = downloadStore::localPlaybackUri,
            ),
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
        val settings = MeloXAutoMixSettings.read(this)
        if (preparedMixSourceId == null && remaining <= settings.preloadLeadMs) {
            prepareIncoming(active, sourceId)
        }
        val incoming = incomingPlayer ?: return
        if (preparedMixSourceId != sourceId) {
            if (mixStartedAt > 0L) completeAutoMix(active, incoming) else cancelPreparedMix()
            return
        }
        val candidate = MeloXAutoMixPlanner.plan(settings, Long.MAX_VALUE / 4L)
        if (!candidate.performsTransition) return
        if (mixStartedAt == 0L && incoming.playbackState == Player.STATE_READY && remaining <= candidate.durationMs) {
            val actualDuration = minOf(
                candidate.durationMs,
                remaining - MeloXAutoMixPlanner.HANDOFF_GUARD_MS,
            )
            if (actualDuration < MeloXAutoMixPlanner.MIN_DURATION_MS) return
            mixSettings = settings
            mixPlan = candidate.copy(durationMs = actualDuration)
            mixBaseVolume = active.volume.coerceIn(0f, 1f)
            mixDurationMs = actualDuration
            if (candidate.incomingStartMs > 0L) incoming.seekTo(candidate.incomingStartMs)
            active.setPlaybackSpeed(candidate.outgoingStartRate)
            incoming.setPlaybackSpeed(candidate.incomingStartRate)
            incoming.volume = 0f
            incoming.play()
            mixStartedAt = SystemClock.elapsedRealtime()
            mixOutgoingStartPositionMs = active.currentPosition.coerceAtLeast(0L)
            mixIncomingStartPositionMs = incoming.currentPosition.coerceAtLeast(0L)
            mixLastProgress = 0.0
            handler.removeCallbacks(mixEnvelope)
            handler.post(mixEnvelope)
        }
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

        if (progress >= 1.0 || remaining <= MeloXAutoMixPlanner.HANDOFF_GUARD_MS) {
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

    /**
     * Incoming has already been playing for the entire overlap. Promotion must
     * therefore never seek it again: seeking at handoff creates the audible
     * forward/backward jump reported at the outgoing song's original endpoint.
     */
    private fun completeAutoMix(old: ExoPlayer, incoming: ExoPlayer) {
        handler.removeCallbacks(mixEnvelope)
        old.volume = 0f
        incoming.volume = mixBaseVolume
        incoming.setPlaybackSpeed(1f)
        incoming.setAudioAttributes(audioAttributes, true)
        incoming.setHandleAudioBecomingNoisy(true)
        incoming.addListener(playerListener)
        mediaSession?.setPlayer(incoming)
        player = incoming
        preparedMixSourceId = null
        mixStartedAt = 0L
        mixDurationMs = 0L
        mixOutgoingStartPositionMs = 0L
        mixIncomingStartPositionMs = 0L
        mixLastProgress = 0.0
        old.removeListener(playerListener)
        old.pause()
        old.stop()
        old.clearMediaItems()
        old.setAudioAttributes(audioAttributes, false)
        old.setHandleAudioBecomingNoisy(false)
        old.setPlaybackSpeed(1f)
        old.volume = 0f
        incomingPlayer = old
        applyLocalArtworkMetadata(incoming)
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
        val active = player
        if (mixStartedAt > 0L && active != null) {
            active.volume = mixBaseVolume
            active.setPlaybackSpeed(1f)
        }
        incomingPlayer?.run {
            removeListener(playerListener)
            pause()
            stop()
            clearMediaItems()
            volume = 0f
            setPlaybackSpeed(1f)
            if (releaseStandby) release()
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
        serviceScope.cancel()
        cancelPreparedMix(releaseStandby = true)
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
    }
}
