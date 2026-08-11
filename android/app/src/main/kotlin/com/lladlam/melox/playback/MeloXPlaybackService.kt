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
            Log.e(TAG, "Playback failed: code=${error.errorCodeName}, message=${error.message}", error)
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "isPlaying=$isPlaying, ongoing=${isPlaybackOngoing()}")
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) ensureAutoplayRecommendations(forceAdvance = true)
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            recommendationSeed = null
            // During an active crossfade the outgoing deck may report an end/transition
            // before the 100 ms monitor tick performs the handoff. Do not tear down the
            // prepared incoming deck in that tiny window; the next tick promotes it at
            // the already-heard position instead of replaying the overlap from zero.
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
                maybePrepareAutoplay(active)
                maybeRunAutoMix(active)
            }
            handler.postDelayed(this, 100L)
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
        val downloadStore = MeloXDownloadStore.get(this)
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
            this, 1001, sessionActivityIntent,
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
        if (!MeloXPlaybackModePreferences.autoplay(this)) return
        if (active.mediaItemCount <= 0 || active.currentMediaItemIndex < 0) return
        val atTail = active.currentMediaItemIndex >= active.mediaItemCount - 1
        if (!atTail) return
        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return
        val remaining = duration - active.currentPosition
        if (remaining <= AUTOPLAY_PRELOAD_MS) ensureAutoplayRecommendations(forceAdvance = false)
    }

    private fun ensureAutoplayRecommendations(forceAdvance: Boolean) {
        if (!MeloXPlaybackModePreferences.autoplay(this)) return
        val active = player ?: return
        val seed = active.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        if (recommendationJob?.isActive == true || recommendationSeed == seed) {
            if (forceAdvance && active.playbackState == Player.STATE_ENDED && active.hasNextMediaItem()) {
                active.seekToNextMediaItem(); active.play()
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
                    active.addMediaItem(PlaybackCommands.mediaItemFor(song, quality, PlaybackCommands.QUEUE_ORIGIN_BASE))
                }
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
        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return
        val remaining = duration - active.currentPosition
        val sourceId = active.currentMediaItem?.mediaId ?: return
        if (incomingPlayer == null && remaining <= AUTOMIX_PRELOAD_MS) {
            prepareIncoming(active, sourceId)
        }
        val incoming = incomingPlayer ?: return
        if (preparedMixSourceId != sourceId) {
            if (mixStartedAt > 0L) {
                completeAutoMix(active, incoming)
            } else {
                cancelPreparedMix()
            }
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
        val nextIndex = active.currentMediaItemIndex + 1
        if (nextIndex !in 0 until active.mediaItemCount) return
        val incoming = buildPlayer(managesAudioFocus = false, observesSession = false)
        val items = List(active.mediaItemCount) { active.getMediaItemAt(it) }
        incoming.setMediaItems(items, nextIndex, 0L)
        incoming.volume = 0f
        incoming.prepare()
        incomingPlayer = incoming
        preparedMixSourceId = sourceId
        mixStartedAt = 0L
    }

    private fun completeAutoMix(old: ExoPlayer, incoming: ExoPlayer) {
        val heardPosition = (SystemClock.elapsedRealtime() - mixStartedAt).coerceAtLeast(0L)
        if (heardPosition > 0L && kotlin.math.abs(incoming.currentPosition - heardPosition) > 300L) {
            incoming.seekTo(heardPosition)
        }
        incoming.volume = mixBaseVolume
        incoming.setAudioAttributes(audioAttributes, true)
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
        mediaSession?.release(); mediaSession = null
        player?.removeListener(playerListener)
        player?.release(); player = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MeloXPlayback"
        const val AUTOPLAY_PRELOAD_MS = 15_000L
        const val AUTOMIX_PRELOAD_MS = 10_000L
        const val AUTOMIX_DURATION_MS = 6_000L
        const val MIN_AUTOMIX_DURATION_MS = 1_500L
        const val AUTOMIX_HANDOFF_GUARD_MS = 350L
    }
}
