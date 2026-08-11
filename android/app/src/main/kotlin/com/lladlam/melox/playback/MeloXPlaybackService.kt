package com.lladlam.melox.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
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
    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest
    private var resumeAfterFocusGain = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeAfterFocusGain = false
                player?.pause()
                incomingPlayer?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeAfterFocusGain = player?.playWhenReady == true || incomingPlayer?.playWhenReady == true
                player?.pause()
                incomingPlayer?.pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeAfterFocusGain) {
                    player?.play()
                    if (mixStartedAt > 0L) incomingPlayer?.play()
                    resumeAfterFocusGain = false
                }
            }
        }
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var recommendationJob: Job? = null
    private var recommendationSeed: Long? = null
    private var preparedMixSourceId: String? = null
    private var mixStartedAt = 0L
    private var mixDurationMs = 0L
    private var mixBaseVolume = 1f

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

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                requestPlaybackAudioFocus()
            } else if (incomingPlayer?.playWhenReady != true) {
                abandonPlaybackAudioFocus()
            }
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
                // Metadata/queue work does not belong in the 60 Hz crossfade loop.
                if (mixStartedAt == 0L) {
                    applyLocalArtworkMetadata(active)
                    PlaybackCommands.prioritizeManualQueue(active)
                    maybePrepareAutoplay(active)
                }
                maybeRunAutoMix(active)
            }
            handler.postDelayed(this, if (mixStartedAt > 0L) AUTOMIX_FRAME_MS else MODE_MONITOR_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        val platformAudioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(platformAudioAttributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()

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
                // All decks use identical attributes before playback starts. Audio
                // focus is owned by the service, so promoting the incoming deck does
                // not need setAudioAttributes() while it is already audible.
                setAudioAttributes(audioAttributes, false)
                setHandleAudioBecomingNoisy(observesSession)
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
            cancelPreparedMix()
            return
        }
        if (!active.isPlaying || active.repeatMode == Player.REPEAT_MODE_ONE || !active.hasNextMediaItem()) return
        if (mixStartedAt == 0L) PlaybackCommands.prioritizeManualQueue(active)
        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return
        val remaining = duration - active.currentPosition
        val sourceId = active.currentMediaItem?.mediaId ?: return
        if (incomingPlayer == null && remaining <= AUTOMIX_PRELOAD_MS) {
            prepareIncoming(active, sourceId)
        }
        val incoming = incomingPlayer ?: return
        if (preparedMixSourceId != sourceId) {
            if (mixStartedAt > 0L) completeAutoMix(active, incoming) else cancelPreparedMix()
            return
        }
        if (mixStartedAt == 0L && incoming.playbackState == Player.STATE_READY && remaining <= AUTOMIX_DURATION_MS) {
            mixBaseVolume = active.volume.coerceIn(0f, 1f)
            mixDurationMs = minOf(
                AUTOMIX_DURATION_MS,
                (remaining - AUTOMIX_HANDOFF_GUARD_MS).coerceAtLeast(MIN_AUTOMIX_DURATION_MS),
            )
            incoming.volume = 0f
            incoming.play()
            mixStartedAt = SystemClock.elapsedRealtime()
        }
        if (mixStartedAt > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - mixStartedAt
            val durationMs = mixDurationMs.coerceAtLeast(1L)
            val progress = (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            active.volume = mixBaseVolume * (1f - progress)
            incoming.volume = mixBaseVolume * progress
            if (progress >= 1f || remaining <= AUTOMIX_HANDOFF_GUARD_MS) {
                completeAutoMix(active, incoming)
            }
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
        val incoming = buildPlayer(managesAudioFocus = false, observesSession = false)
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
        old.volume = 0f
        incoming.volume = mixBaseVolume
        // Do NOT change AudioAttributes here. Media3 may recreate AudioTrack when
        // attributes change during playback, which is an audible handoff gap.
        incoming.setHandleAudioBecomingNoisy(true)
        incoming.addListener(playerListener)
        mediaSession?.setPlayer(incoming)
        player = incoming
        incomingPlayer = null
        preparedMixSourceId = null
        mixStartedAt = 0L
        mixDurationMs = 0L
        old.removeListener(playerListener)
        old.pause()
        old.release()
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

    private fun requestPlaybackAudioFocus(): Boolean {
        if (!::audioManager.isInitialized || !::audioFocusRequest.isInitialized) return true
        return audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonPlaybackAudioFocus() {
        if (::audioManager.isInitialized && ::audioFocusRequest.isInitialized) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
        }
    }

    private fun cancelPreparedMix() {
        val active = player
        if (mixStartedAt > 0L && active != null) active.volume = mixBaseVolume
        incomingPlayer?.run {
            removeListener(playerListener)
            pause()
            release()
        }
        incomingPlayer = null
        preparedMixSourceId = null
        mixStartedAt = 0L
        mixDurationMs = 0L
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Log.d(TAG, "Controller connected: ${controllerInfo.packageName}")
        return mediaSession
    }

    override fun onDestroy() {
        handler.removeCallbacks(modeMonitor)
        recommendationJob?.cancel()
        serviceScope.cancel()
        cancelPreparedMix()
        mediaSession?.release()
        mediaSession = null
        player?.removeListener(playerListener)
        player?.release()
        player = null
        abandonPlaybackAudioFocus()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MeloXPlayback"
        const val AUTOPLAY_PRELOAD_MS = 15_000L
        const val AUTOMIX_PRELOAD_MS = 10_000L
        const val AUTOMIX_DURATION_MS = 6_000L
        const val MIN_AUTOMIX_DURATION_MS = 1_500L
        const val AUTOMIX_HANDOFF_GUARD_MS = 700L
        const val AUTOMIX_FRAME_MS = 16L
        const val MODE_MONITOR_MS = 100L
    }
}
