package com.lladlam.melox.playback

import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import com.lladlam.melox.R
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.core.app.NotificationCompat
import com.lladlam.melox.MainActivity
import com.lladlam.melox.MeloXAppVisibility
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.lan.MeloXCastAudioProcessor
import com.lladlam.melox.lan.MeloXLanCastFormat
import com.lladlam.melox.lan.MeloXLanCastHandshake
import com.lladlam.melox.lan.MeloXLanCastReceiver
import com.lladlam.melox.lan.MeloXLanHost
import com.lladlam.melox.lan.MeloXLanPlayback
import com.lladlam.melox.lan.MeloXLanRelayDataSource
import com.lladlam.melox.lan.MeloXLanRuntime
import com.lladlam.melox.lan.MeloXLanTrack
import com.lladlam.melox.core.audio.MeloXPlaybackUnavailableException
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.download.MeloXProviderDownloadStore
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXNetworkAvailability
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.ui.player.MeloXProviderLyricsLoader
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.music.model.TrackAvailability
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigPolicy
import com.lladlam.melox.core.music.provider.FavoriteCapability
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.core.music.provider.MeloXUnifiedFavorites
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@OptIn(UnstableApi::class)
class MeloXPlaybackService : MediaSessionService(), MeloXLanHost {
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
    /**
     * 已经**真正出过声**并被记进历史/自动缓存计数的曲目。
     *
     * ⚠ 播放历史不能再在 `onMediaItemTransition` 里记（2026-09-28 修）：
     *   切到一首无音源的 VIP 歌时 transition 照样触发，于是「播过」被写进历史，
     *   而这首歌连一个字节的音频都没出来。改成进 `STATE_READY` 且真的在播时才落库。
     */
    private var startedSongId: Long? = null
    /** 连续跳过「无音源」曲目的次数，用来防止整队都无音源时无限打转。 */
    private var unavailableSkipStreak = 0
    private val listenedTimeTracker = MeloXListenedTimeTracker()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var recommendationJob: Job? = null
    private var recommendationSeed: String? = null
    private var recommendationRetrySeed: String? = null
    private var recommendationRetryAfterRealtimeMs = 0L
    private var systemLyricsJob: Job? = null
    private var systemLyricsMediaId: String? = null
    private var systemLyricsDocument: LyricsDocument? = null
    private var systemLyricsOriginalMetadata: MediaMetadata? = null
    private var systemLyricsLastIndex = Int.MIN_VALUE
    private var systemLyricsLastDispatchRealtimeMs = 0L
    private var systemLyricsLastPlaying = false
    private val lyricsNotificationArtwork = LyricsNotificationArtwork()

    private var notificationLeftAction = MeloXNotificationAction.PlayMode
    private var notificationRightAction = MeloXNotificationAction.Favorite
    private var notificationButtons: List<CommandButton> = emptyList()

    @Volatile private var lanPlayback = MeloXLanPlayback()
    @Volatile private var lanQueue: List<MeloXLanTrack> = emptyList()
    private var lanBarrier = false
    private var lanPreparing = false
    private var lanCommit: Runnable? = null
    private var lanCastReceiver: MeloXLanCastReceiver? = null
    private var lanTogetherSharesDevice = false
    private var lanTogetherLastSeekAtMs = 0L
    private val lanStateRunnable = object : Runnable {
        override fun run() {
            player?.let {
                lanPlayback = buildLanPlayback(it)
            }
            MeloXLanRuntime.onPlaybackUpdated()
            handler.postDelayed(this, 100L)
        }
    }

    private var colorOsLyricsPublisher: ColorOsLyricsPublisher? = null
    private var superLyricPublisher: SuperLyricPublisher? = null
    private var systemLyricHasTranslation = false
    private var systemLyricsSessionGeneration = 0L
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
    private var smartQueueJob: Job? = null
    private var prefetchJob: Job? = null
    private var notificationArtworkRevision = 0
    private val notificationArtworkRefresh = Runnable {
        if (notificationArtworkRevision <= 0) return@Runnable
        lyricsNotificationSource?.let { (line, next, metadata) ->
            postLyricsNotification(line, next, metadata)
        }
    }
    @Volatile private var lyricsNotificationSource: Triple<String, String, MediaMetadata>? = null
    private var smartQueueJobGeneration = 0L
    private var smartQueueSourceId: String? = null
    private var smartQueueGeneration = -1L
    private var mixSettings = MeloXAutoMixSettings()
    private var cachedAutoMixSettings = MeloXAutoMixSettings()
    private var cachedAutoMixSettingsAt = 0L
    private var mixPlan = MeloXAutoMixPlan(0L, 0L)
    private val mixEqualizerEnvelope = MeloXAutoMixEqualizerEnvelope()
    private var lastMaintenanceRealtimeMs = 0L
    private var lastQueuePositionPersistRealtimeMs = 0L
    private var appliedAudioFocusPolicy: Boolean? = null

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val active = player
            // 设置 → 播放：无音频源时是否自动跳到下一首（默认开）。
            val skipUnavailable = MeloXSettingsPreferences.boolean(this@MeloXPlaybackService, "playback_skip_unavailable", true)
            if (active != null && skipUnavailable && !MeloXNetworkAvailability.isOnline(this@MeloXPlaybackService)) {
                if (skipToNextDownloaded(active)) {
                    Log.i(TAG, "Offline playback skipped unavailable item after player error")
                    return
                }
            }
            Log.e(TAG, "Playback failed: code=${error.errorCodeName}, message=${error.message}", error)
            // ── 无音源（VIP / 会员 / 版权 / 地区 / 拿不到链接）────────────────
            // ⚠ 旧行为：**在线时什么都不做**，播放器就此卡在 STATE_IDLE + 错误态。
            //   于是迷你栏滑过去后，`MeloXIOSMiniPlayer` 的 1.5s 兜底把内容弹回原来
            //   那首 ⇒ 用户看到「怎么滑，一段时间后都会变回原来的歌曲」；而
            //   `onMediaItemTransition` 早就把这首没出声的歌写进了历史/推荐记录。
            //   这类错误**重试没有意义**（再试一万次也没有链接），正确做法是向前跳过。
            if (active == null || !isUnavailableSourceError(error)) {
                // 非音源类失败（网络抖动 / 解析超时 / 解码失败）以前同样是静默的，
                // 用户点完歌看不到任何动静。这里给一句兜底提示。
                MeloXPlaybackNotice.show(
                    this@MeloXPlaybackService,
                    MeloXPlaybackNotice.describe(
                        error,
                        offline = !MeloXNetworkAvailability.isOnline(this@MeloXPlaybackService),
                    ),
                )
                return
            }
            val title = active.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
            val skipped = skipUnavailable && skipForwardFromUnavailable(active)
            Log.i(TAG, "Unavailable source: ${error.message}, skipped=$skipped")
            // 跳不过去（单曲循环 / 整队都无音源）时也必须出声，否则又是零反馈。
            val message = when {
                title.isBlank() -> if (skipped) "当前歌曲暂无可播放音源，已跳过" else "当前歌曲暂无可播放音源"
                skipped -> "《$title》暂无可播放音源，已跳过"
                else -> "《$title》暂无可播放音源"
            }
            MeloXPlaybackNotice.show(this@MeloXPlaybackService, message)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            lanPlayback = buildLanPlayback(player)
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                lanQueue = readLanQueue(player)
            }
            MeloXLanRuntime.onPlaybackUpdated()
            if (events.containsAny(
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                )
            ) {
                handler.post { refreshNotificationButtons() }
            }
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                com.lladlam.melox.usb.MeloXUsbAudioSession.onSeek()
            }
            if (!lanPreparing && reason == Player.DISCONTINUITY_REASON_SEEK) MeloXLanRuntime.onTogetherSeek()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // This listener is also attached to AutoMix's standby deck; derive
            // the USB clock state from the deck that currently owns playback
            // so pausing the standby deck cannot freeze the DAC.
            com.lladlam.melox.usb.MeloXUsbAudioSession.onPlaybackChanged(player?.isPlaying == true)
            listenedTimeTracker.onPlayingChanged(SystemClock.elapsedRealtime(), isPlaying)
            Log.d(TAG, "isPlaying=$isPlaying, ongoing=${isPlaybackOngoing()}")
            // 与 STATE_READY 那条路径互补：无缝切歌（新歌已预加载、不经过 BUFFERING）
            // 时 `onPlaybackStateChanged` 可能根本不触发，这里兜住。
            if (isPlaying) player?.let(::markSongActuallyPlayed)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (lanBarrier && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                player?.let { lanPlayback = buildLanPlayback(it) }
                MeloXLanRuntime.onTogetherTrackEnded()
            }
            if (!playWhenReady && !cancellingMix && hasPreparedMix()) {
                Log.i(TAG, "AutoMix cancelled because playback paused: reason=$reason")
                cancelPreparedMix()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val active = player ?: return
            if (playbackState == Player.STATE_READY && active.isPlaying) {
                markSongActuallyPlayed(active)
            }
            if (playbackState == Player.STATE_ENDED) {
                if (lanBarrier) {
                    lanPlayback = buildLanPlayback(active)
                    MeloXLanRuntime.onTogetherTrackEnded()
                    return
                }
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
                // ⚠ 这里**不再** recordStart / recordPlayback（2026-09-28 修）：
                //   transition 只说明「切过去了」，不说明「出声了」。无音源的歌同样
                //   会走到这里，旧实现于是把它记进了历史与自动缓存计数。
                //   落库推迟到 `onPlaybackStateChanged(STATE_READY)` → `markSongActuallyPlayed`。
            }
            MeloXAudioReactiveRuntime.select(mediaItem?.mediaId)
            mediaItem?.let(::scheduleBackgroundAnalysis)
            // Smart Queue starts with one visible item and appends exactly one
            // analyzed match at a time. It must also run for the initial item,
            // not only after a Media3 automatic transition.
            mediaItem?.let { scheduleSmartQueueNext(it.mediaId) }
            val active = player
            if (mediaItem?.mediaId != systemLyricsMediaId) {
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
            // 切歌 ⇒ 当前索引变了，立即持久化索引（断点续播依赖它）。
            requestQueuePersist(force = true)
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
            // 队列结构变了（playQueue 全量入队 / moveMediaItem 重排 / 增删）→ 立即持久化，
            // 这样即使进程随后被系统杀掉，冷启动也能恢复整条队列而不是空的。
            requestQueuePersist(force = true)
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val result = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            if (!controller.isTrusted && controller.packageName != packageName) {
                return result.build()
            }
            val commands = result.build().availableSessionCommands.buildUpon()
                .add(SessionCommand(ACTION_NOTIFICATION_LEFT, Bundle.EMPTY))
                .add(SessionCommand(ACTION_NOTIFICATION_RIGHT, Bundle.EMPTY))
                .build()
            return result.setAvailableSessionCommands(commands).build()
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int,
        ): Int {
            if (com.lladlam.melox.dlna.DlnaSession.active) {
                when (playerCommand) {
                    Player.COMMAND_PLAY_PAUSE -> com.lladlam.melox.dlna.DlnaSession.toggle()
                    Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> com.lladlam.melox.dlna.DlnaSession.next()
                    Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> com.lladlam.melox.dlna.DlnaSession.previous()
                    else -> return super.onPlayerCommandRequest(session, controller, playerCommand)
                }
                return SessionResult.RESULT_INFO_SKIPPED
            }
            // The in-app previous button and the notification/MediaSession
            // previous button must obey the same "restart after 5 seconds"
            // preference. Media3's default uses a 3-second threshold, which is
            // why disabling the setting only worked inside the app.
            if (playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS ||
                playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) {
                cancelPreparedMix()
                val player = session.player
                val restartAfterFiveSeconds = MeloXSettingsRuntime.previousRestartsAfterFiveSeconds
                if (restartAfterFiveSeconds && player.currentPosition >= 5_000L) {
                    player.seekTo(0L)
                } else if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                } else {
                    player.seekTo(0L)
                }
                return SessionResult.RESULT_INFO_SKIPPED
            }
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

        /**
         * Handles favorite custom commands coming from external controllers.
         * vivo 原子随身听 and other system music panels look for FAVORITE-family
         * actions to render a system-level favorite button on the walkman card.
         * We register several ids in parallel because there is no public vivo
         * spec; the well-known AOSP id plus the Google Play Music legacy id plus
         * a vivo-private fallback cover every documented consumer today.
         */
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            command: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (command.customAction) {
                ACTION_NOTIFICATION_LEFT -> {
                    Log.i(TAG, "Notification left action from ${controller.packageName}")
                    return handleNotificationAction(notificationLeftAction)
                }
                ACTION_NOTIFICATION_RIGHT -> {
                    Log.i(TAG, "Notification right action from ${controller.packageName}")
                    return handleNotificationAction(notificationRightAction)
                }
                ACTION_CUSTOM_FAVORITE,
                ACTION_CUSTOM_LIKE,
                ACTION_CUSTOM_GPM_FAVORITE,
                ACTION_CUSTOM_VIVO_FAVORITE -> {
                    Log.i(TAG, "System FAVORITE custom command from ${controller.packageName}")
                    serviceScope.launch {
                        handleSystemFavoriteToggle(outcome = { Log.i(TAG, "FAVORITE outcome: $it") })
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, command, args)
        }
    }

    private fun prefetchFollowing(active: ExoPlayer) {
        val current = active.currentMediaItemIndex
        if (current !in 0 until active.mediaItemCount) return
        // A new track invalidates the previous prefetch; otherwise every skip
        // leaves an unpausable CacheWriter running in parallel.
        prefetchJob?.cancel()
        // Cache the active item as well as the next three. The active item is
        // needed by Smart analysis when it examines the outgoing tail later.
        val following = (current until minOf(current + PREFETCH_TRACK_COUNT + 1, active.mediaItemCount))
            .map(active::getMediaItemAt)
        prefetchJob = serviceScope.launch(Dispatchers.IO) {
            following.forEach { item ->
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
                    // Keep the resume position fresh so a crash (where onDestroy
                    // never runs) still restores the right song and position.
                    if (now - lastQueuePositionPersistRealtimeMs >= QUEUE_POSITION_PERSIST_INTERVAL_MS) {
                        lastQueuePositionPersistRealtimeMs = now
                        persistQueuePositionIfEnabled(active)
                    }
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
        val httpFactory = OkHttpDataSource.Factory(com.lladlam.melox.core.network.MeloXHttpClient.thirdPartyAndMedia)
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
        val thirdPartyResolver = ThirdPartyPlaybackResolver(
            context = this,
            enabledProvider = { ThirdPartyMusicSourceConsentStore.enabled(this@MeloXPlaybackService) },
            membershipOnlyProvider = {
                ThirdPartyMusicSourceConsentStore.membershipFallbackOnly(this@MeloXPlaybackService)
            },
        )
        playbackResolver = NeteasePlaybackResolver(
            cookieProvider = cookieProvider,
            client = NeteaseSearchClient(cookieProvider = cookieProvider),
            localSourceProvider = downloadStore::localPlaybackUri,
            providerLocalSourceProvider = providerDownloadStore::localPlaybackUri,
            crossProviderFallback = crossProviderFallback,
            thirdPartyResolver = thirdPartyResolver,
            providerPlaybackEnabled = { source ->
                MeloXRemoteConfigPolicy.providerPlaybackEnabled(this@MeloXPlaybackService, source)
            },
            thirdPartySourcesEnabled = { ThirdPartyMusicSourceConsentStore.enabled(this@MeloXPlaybackService) },
        )
        autoMixAnalyzer = MeloXAutoMixAudioAnalyzer(this)
        val defaultUpstream = DefaultDataSource.Factory(this, httpFactory)
        val upstream = androidx.media3.datasource.DataSource.Factory { PluginMediaDataSource(defaultUpstream) }
        val resolving = ResolvingDataSource.Factory(
            upstream,
            playbackResolver,
        )
        val lanAware = MeloXLanRelayDataSource.Factory(resolving)
        val cached = CacheDataSource.Factory()
            .setCache(MeloXMediaCache.get(this))
            .setUpstreamDataSourceFactory(lanAware)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val playbackCache = MeloXMediaCache.get(this)
        cached.setCache(playbackCache)
        mediaPrefetcher = MeloXMediaPrefetcher(
            analysisDirectory = java.io.File(cacheDir, "automix_analysis"),
            dataSourceFactory = cached,
            cache = playbackCache,
        )
        // AutoSync aligns lyrics against the same cached bytes the analysis
        // pipeline already materialises. The resolver stays lazy: it only runs
        // when the lyric loader actually decides to generate a timeline.
        com.lladlam.melox.core.autosync.AutoSyncPlaybackBridge.isCurrentTrack = { expected ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                val id = PlaybackTrackIdentity.fromMediaItem(player?.currentMediaItem)
                id != null && "${id.sourceId.value}:${id.value}" == expected
            }
        }
        com.lladlam.melox.core.autosync.AutoSyncPlaybackBridge.audioResolver = { expected ->
            // AutoSync runs on an IO coroutine, but ExoPlayer only allows
            // `currentMediaItem` on its application thread. Reading it off-thread
            // throws, and the throw was swallowed upstream — which looked exactly
            // like "AutoSync never ran". Hop to main for the read only; the
            // (potentially long) materialise stays on the caller's dispatcher.
            val item = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                player?.currentMediaItem
            }
            val id = PlaybackTrackIdentity.fromMediaItem(item)
            if (item == null || id == null || "${id.sourceId.value}:${id.value}" != expected) {
                null
            } else {
                // Never decode the user's playback stream: a hi-res item is tens
                // of MB and takes minutes to decode, while alignment only needs
                // 16 kHz mono. Downgrade to the standard analysis source exactly
                // like the auto-mix pipeline does before materialising anything.
                val analysisItem = item.copyWithStandardAnalysisUri()
                mediaPrefetcher.materialize(analysisItem).takeIf {
                    com.lladlam.melox.core.autosync.AutoSyncPlaybackBridge.isCurrentTrack?.invoke(expected) == true
                }
            }
        }
        val pluginAwareCache = androidx.media3.datasource.DataSource.Factory {
            PluginCacheRoutingDataSource(cached, resolving)
        }
        mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(pluginAwareCache)

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
        publishSystemFavoriteCustomActions()
        refreshNotificationButtons()
        mediaSession?.let { session ->
            runCatching { colorOsLyricsPublisher = ColorOsLyricsPublisher(this, session) }
                .onFailure { Log.w(TAG, "ColorOS lyrics publisher unavailable", it) }
        }
        runCatching { superLyricPublisher = SuperLyricPublisher(active, serviceScope) }
            .onFailure { Log.w(TAG, "SuperLyricApi publisher unavailable", it) }
        MeloXLanRuntime.attach(this, this)
        lanPlayback = buildLanPlayback(active)
        handler.post(lanStateRunnable)
        handler.post { restorePersistedQueue(active) }
        handler.postDelayed(queuePersistRunnable, QUEUE_PERSIST_INTERVAL_MS)
        createLyricsNotificationChannel()
        handler.post(modeMonitor)
        handler.postDelayed({
            player?.currentMediaItem?.let(::ensureBackgroundAnalysisScheduled)
        }, 1_000L)
    }

    /** True while the player is actually producing audio; used by PiP actions. */
    internal fun isPlaybackActive(): Boolean = player?.isPlaying == true

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREVIOUS -> player?.let { player ->
                if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo(0L)
            }
            ACTION_NEXT -> player?.seekToNextMediaItem()
            ACTION_TOGGLE_PLAYBACK -> player?.let { player ->
                if (player.isPlaying) player.pause() else player.play()
            }
            ACTION_ANALYZE_PLAYLIST -> {
                val source = intent.getStringExtra(EXTRA_ANALYSIS_SOURCE)?.let(com.lladlam.melox.core.music.model.SourceId::parse)?.builtIn
                val playlistId = intent.getStringExtra(EXTRA_ANALYSIS_PLAYLIST_ID)
                if (source != null && !playlistId.isNullOrBlank()) startPlaylistAnalysis(source, playlistId)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startPlaylistAnalysis(source: MusicSource, playlistId: String) {
        if (playlistAnalysisJob?.isActive == true) return
        if (player?.isPlaying != true) {
            val notification = buildPlaylistAnalysisNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
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
            val analysisSongId = item.analysisSongId()
            if (autoMixAnalyzer.hasPersistentFullAnalysis(analysisSongId) ||
                autoMixAnalyzer.hasPersistentAnalysis(analysisSongId)
            ) {
                Log.i(TAG, "Background audio analysis cached: ${item.mediaId}")
                return@launch
            }
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
            // 分析范围（池）：只纳入「当前这首 + 后面最靠前的 N 首」，历史条目永远不会轮到
            // 播放、不纳入。真正的下载/分析按滚动窗口进行（见 ensureSmartQueueAnalysis）——
            // 不在开播时一次性全量拉取：换一个歌单就是几十次整曲请求，既慢又容易触发上游
            // 风控/限流。没分析的条目留在时间线里按原顺序播（bestAnalyzedMatch 只认 analyses）。
            val limit = currentAutoMixSettings().smartQueueAnalysisLimit
            val targets = if (limit < 0) snapshot.futureItems else snapshot.futureItems.take(limit)
            val analysisTargets = targets.mapTo(mutableSetOf()) { it.mediaId }
            analysisTargets += currentItem.mediaId
            MeloXSmartQueueBuilder.setAnalysisTargets(snapshot.generation, analysisTargets)
            Log.i(
                TAG,
                "Smart Queue analysis pool: ${analysisTargets.size} track(s) " +
                    "of ${snapshot.items.size} remaining candidates (limit=$limit)",
            )
        }
        ensureSmartQueueAnalysis(active, snapshot.generation, sourceId)
        smartQueueSourceId = sourceId
        commitSmartQueueChain(snapshot.generation, sourceId)
    }

    /**
     * 滚动预分析：保证播放位置往后 [SMART_QUEUE_ANALYSIS_WINDOW] 首以内、尚未分析过的候选
     * 得到分析，随播放推进逐首补齐。已分析过的曲目命中持久缓存、不会重复下载，因此
     * 换歌单/开播只花窗口内几次请求，而不是按「分析上限」一次性全量请求。
     */
    private fun ensureSmartQueueAnalysis(active: ExoPlayer, generation: Long, sourceId: String) {
        // A running job only blocks the same queue generation. After a
        // generation change the old job has just been cancelled; waiting for
        // its cancellation to settle here would leave the new queue without
        // analysis until the next track transition.
        if (smartQueueJob?.isActive == true && smartQueueJobGeneration == generation) return
        val current = active.currentMediaItemIndex
        if (current !in 0 until active.mediaItemCount) return
        val end = minOf(current + SMART_QUEUE_ANALYSIS_WINDOW, active.mediaItemCount - 1)
        val window = (current..end)
            .map(active::getMediaItemAt)
            .distinctBy(MediaItem::mediaId)
            .filter { MeloXSmartQueueBuilder.needsAnalysis(generation, it.mediaId) }
        if (window.isEmpty()) return
        smartQueueJobGeneration = generation
        smartQueueJob = serviceScope.launch(Dispatchers.IO) {
            val limiter = Semaphore(SMART_QUEUE_ANALYSIS_CONCURRENCY)
            coroutineScope {
                window.forEach { item ->
                    launch {
                        limiter.withPermit {
                            runCatching { analyzeSmartQueueItem(item) }
                                .onSuccess { analysis ->
                                    MeloXSmartQueueBuilder.recordAnalysis(generation, item.mediaId, analysis)
                                    Log.i(
                                        TAG,
                                        "Smart Queue analysis ready: ${item.mediaId}, " +
                                            "bpm=${"%.1f".format(analysis.bpm)}, " +
                                            "confidence=${"%.3f".format(analysis.confidence)}",
                                    )
                                    withContext(Dispatchers.Main.immediate) {
                                        commitSmartQueueChain(generation, sourceId)
                                    }
                                }
                                .onFailure {
                                    if (it is kotlinx.coroutines.CancellationException) throw it
                                    MeloXSmartQueueBuilder.recordFailure(generation, item.mediaId)
                                    Log.w(TAG, "Smart Queue analysis failed: ${item.mediaId}", it)
                                }
                        }
                    }
                }
            }
            withContext(Dispatchers.Main.immediate) {
                commitSmartQueueChain(generation, sourceId)
            }
        }
    }

    private fun commitSmartQueueChain(expectedGeneration: Long, initialSourceId: String) {
        val active = player ?: return
        if (!MeloXPlaybackModePreferences.smartQueue(this) || !MeloXPlaybackModePreferences.autoMix(this)) return
        if (MeloXSmartQueueBuilder.snapshot().generation != expectedGeneration) return
        if (active.mediaItemCount == 0 || mixStartedAt > 0L) return

        // 整首歌单**已经**在时间线里（playQueue 全量 setMediaItems），这里只做重排：
        // 每次都把匹配搬进「上一首之后」，而不是往末尾堆——堆末尾的话播放仍按原顺序
        // 走，智能队列等于没生效。不再 addMediaItem：那会把候选扣在池外，队列面板
        // （按 mediaItemCount 建）看着就是空的。
        // 断点续排：`tailMediaId()` 是消费顺序的最后一首，从它的当前位置继续延伸；
        // 若用户手动跳到链之后，就丢弃旧链尾，从当前播放项重新起链。
        val current = active.currentMediaItemIndex.coerceIn(0, active.mediaItemCount - 1)
        val tailId = MeloXSmartQueueBuilder.tailMediaId()
        val tailPosition = tailId?.let { id ->
            (0 until active.mediaItemCount).indexOfFirst { active.getMediaItemAt(it).mediaId == id }
        }?.takeIf { it >= 0 } ?: current
        val chainStart = tailPosition.coerceAtLeast(current)
        var sourceId = active.getMediaItemAt(chainStart).mediaId
        var insertAt = chainStart + 1

        while (insertAt <= active.mediaItemCount - 1) {
            val match = MeloXSmartQueueBuilder.bestAnalyzedMatch(expectedGeneration, sourceId) ?: break
            val consumed = MeloXSmartQueueBuilder.consume(expectedGeneration, match.item.mediaId) ?: break
            // 当前播放项在 begin() 时就已计入 consumedIds，候选里不会出现 ⇒ from != current。
            val from = (0 until active.mediaItemCount).indexOfFirst {
                active.getMediaItemAt(it).mediaId == consumed.mediaId
            }
            if (from < 0 || from == insertAt) break
            active.moveMediaItem(from, insertAt)
            // 混音目标 deck 是 prepareIncoming 整份拷贝的，镜像同一次搬移，交接后顺序一致。
            val incoming = incomingPlayer
            if (preparedMixSourceId != null && incoming != null) {
                runCatching {
                    val at = (0 until incoming.mediaItemCount).indexOfFirst {
                        incoming.getMediaItemAt(it).mediaId == consumed.mediaId
                    }
                    if (at >= 0 && insertAt <= incoming.mediaItemCount - 1 && at != insertAt) {
                        incoming.moveMediaItem(at, insertAt)
                    }
                }
            }
            Log.i(
                TAG,
                "Smart Queue reordered ${match.reason} match: ${consumed.mediaId}, " +
                    "source=$sourceId bpm=${"%.1f".format(match.analysis.bpm)}, " +
                    "tempoDistance=${"%.1f".format(match.tempoDistance)}, " +
                    "confidence=${"%.3f".format(match.analysis.confidence)}",
            )
            sourceId = consumed.mediaId
            insertAt++
        }
    }

    private suspend fun analyzeSmartQueueItem(item: MediaItem): MeloXAutoMixTrackAnalysis {
        val standardItem = item.copyWithStandardAnalysisUri()
        MeloXAudioAnalysisLoad.begin()
        return try {
            analyzeFullTrack(standardItem, standardItem.analysisSongId())
        } finally {
            MeloXAudioAnalysisLoad.end()
        }
    }

    /**
     * 整曲分析（0..MAX 窗口）：已持久化的结果直接复用——不下载、不解码；缺失时才按
     * 标准音质落盘分析一次。已分析过的曲目重复播放/换歌单不会再产生下载请求。
     */
    private suspend fun analyzeFullTrack(item: MediaItem, songId: Long): MeloXAutoMixTrackAnalysis {
        autoMixAnalyzer.persistentFullAnalysis(songId)?.let { return it }
        val file = mediaPrefetcher.materialize(item)
        return try {
            autoMixAnalyzer.analyze(
                songId = songId,
                uri = android.net.Uri.fromFile(file),
                windowStartMs = 0L,
                windowDurationMs = Long.MAX_VALUE,
            )
        } finally {
            file.delete()
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
    ): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(MeloXCastAudioProcessor(tapAudio = observesSession)))
                // Media3's float sink bypasses the custom PCM processor chain.
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(false)
                .build()

            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: AudioSink,
                eventHandler: Handler,
                eventListener: AudioRendererEventListener,
                out: ArrayList<Renderer>,
            ) {
                super.buildAudioRenderers(
                    context,
                    // Force OFF: with the extension mode ON, DefaultRenderersFactory
                    // reflectively finds androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
                    // on the classpath (the Jellyfin artifact) and adds it too, which
                    // would decode every format twice. The renderer is added explicitly
                    // below instead.
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF,
                    QtiFlacDecoderWorkaround.mediaCodecSelector,
                    enableDecoderFallback,
                    audioSink,
                    eventHandler,
                    eventListener,
                    out,
                )
                // Software fallback after the platform decoders: only takes formats
                // no MediaCodec decoder handles, e.g. FLAC once the broken QTI
                // decoder is hidden. Android 8.1 and older are the only affected
                // versions; avoid constructing (and loading the native library for)
                // this renderer on newer devices.
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
                    out.add(FfmpegAudioRenderer(eventHandler, eventListener, audioSink))
                }
            }
        }
        return ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply {
                setAudioAttributes(audioAttributes, managesAudioFocus)
                setHandleAudioBecomingNoisy(managesAudioFocus)
                if (observesSession) addListener(playerListener)
            }
    }

    private fun buildLanPlayback(active: Player): MeloXLanPlayback {
        val item = active.currentMediaItem
        val metadata = item?.mediaMetadata
        val identity = PlaybackTrackIdentity.fromMediaItem(item)
        return MeloXLanPlayback(
            playing = active.playWhenReady,
            title = metadata?.title?.toString().orEmpty(),
            artist = metadata?.artist?.toString().orEmpty(),
            album = metadata?.albumTitle?.toString().orEmpty(),
            artworkUrl = metadata?.artworkUri?.toString(),
            positionMs = active.currentPosition.coerceAtLeast(0L),
            durationMs = active.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L,
            volume = (active.volume * 100f).toInt().coerceIn(0, 100),
            trackId = identity?.let { "${it.sourceId.value}:${it.value}" },
            source = identity?.sourceId?.value,
            index = active.currentMediaItemIndex,
            queueLength = active.mediaItemCount,
            canNext = active.hasNextMediaItem(),
            canPrevious = active.hasPreviousMediaItem() || active.currentPosition > 0L,
            ready = active.playbackState == Player.STATE_READY,
            ended = active.playbackState == Player.STATE_ENDED ||
                (!active.playWhenReady && active.duration > 0L && active.currentPosition >= active.duration - 100L),
            sampleTimeMs = System.currentTimeMillis(),
        )
    }

    override fun snapshot(): MeloXLanPlayback = lanPlayback

    override suspend fun dlnaSource(): com.lladlam.melox.dlna.DlnaMediaSource? {
        val captured = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            val active = player ?: return@withContext null
            val item = active.currentMediaItem ?: return@withContext null
            val uri = item.localConfiguration?.uri ?: return@withContext null
            Triple(item, uri, buildLanPlayback(active))
        } ?: return null
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val spec = playbackResolver.resolveDataSpec(androidx.media3.datasource.DataSpec.Builder()
                .setUri(captured.second).build())
            val state = captured.third
            val mime = captured.first.localConfiguration?.mimeType ?: when (spec.uri.lastPathSegment?.substringAfterLast('.')?.lowercase()) {
                "flac" -> "audio/flac"; "m4a", "aac" -> "audio/mp4"; "wav" -> "audio/wav"
                "ogg", "opus" -> "audio/ogg"; else -> "audio/mpeg"
            }
            com.lladlam.melox.dlna.DlnaMediaSource(spec.uri.toString(), spec.httpRequestHeaders, state.title,
                state.artist, state.album, state.artworkUrl, state.durationMs, state.positionMs,
                state.trackId ?: captured.first.mediaId, mime)
        }
    }

    override fun queueSnapshot(): List<MeloXLanTrack> = lanQueue

    private fun readLanQueue(active: Player): List<MeloXLanTrack> {
        return buildList {
            for (index in 0 until active.mediaItemCount) {
                val item = active.getMediaItemAt(index)
                val metadata = item.mediaMetadata
                val track = com.lladlam.melox.lan.meloXLanTrackFromMediaItem(
                    mediaId = item.mediaId,
                    title = metadata.title?.toString().orEmpty(),
                    artist = metadata.artist?.toString().orEmpty(),
                    album = metadata.albumTitle?.toString().orEmpty(),
                    artworkUrl = metadata.artworkUri?.toString(),
                    durationMs = metadata.extras?.getLong(
                        PlaybackTrackIdentity.DurationMsExtra,
                        0L,
                    ) ?: 0L,
                ) ?: continue
                add(track)
            }
        }
    }

    override fun currentTrack(): MeloXLanTrack? {
        // LAN transfer calls this from IO; ExoPlayer is confined to this service's
        // main looper, so read the immutable media snapshot there.
        val player = player ?: return null
        val snapshot = if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            runCatching { player.currentMediaItem to player.duration }.getOrNull()
        } else {
            val latch = java.util.concurrent.CountDownLatch(1)
            var value: Pair<androidx.media3.common.MediaItem?, Long>? = null
            handler.post {
                value = runCatching { player.currentMediaItem to player.duration }.getOrNull()
                latch.countDown()
            }
            if (!latch.await(2, java.util.concurrent.TimeUnit.SECONDS)) return null
            value
        } ?: return null
        val item = snapshot.first ?: return null
        val metadata = item.mediaMetadata
        return com.lladlam.melox.lan.meloXLanTrackFromMediaItem(
            mediaId = item.mediaId,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString().orEmpty(),
            artworkUrl = metadata.artworkUri?.toString(),
            durationMs = snapshot.second.takeIf { it > 0L && it != C.TIME_UNSET }
                ?: metadata.extras?.getLong(PlaybackTrackIdentity.DurationMsExtra, 0L)
                ?: 0L,
        )
    }

    override fun control(command: String, positionMs: Long?, volume: Int?): Boolean {
        handler.post {
            val active = player ?: return@post
            when (command) {
                "play" -> if (!active.playWhenReady) active.play()
                "pause" -> active.pause()
                "toggle" -> if (active.isPlaying) active.pause() else active.play()
                "next" -> if (active.hasNextMediaItem()) active.seekToNextMediaItem()
                "dlna.previous" -> if (active.hasPreviousMediaItem()) active.seekToPreviousMediaItem()
                "together.next" -> if (active.hasNextMediaItem()) {
                    active.seekToNextMediaItem()
                    active.playWhenReady = true
                }
                "previous" -> {
                    if (active.currentPosition >= 5_000L) {
                        active.seekTo(0L)
                    } else if (active.hasPreviousMediaItem()) {
                        active.seekToPreviousMediaItem()
                    }
                }
                "seek" -> positionMs?.let { active.seekTo(it.coerceAtLeast(0L)) }
                "volume" -> volume?.let { active.volume = it.coerceIn(0, 100) / 100f }
            }
        }
        return true
    }

    override fun transfer(
        track: MeloXLanTrack,
        queue: List<MeloXLanTrack>,
        positionMs: Long,
    ): Boolean {
        if (track.source != "netease") return false
        val song = track.toSearchSong() ?: return false
        val songs = queue.mapNotNull { it.toSearchSong() }.ifEmpty { listOf(song) }
        val selected = songs.firstOrNull { it.id == song.id } ?: song
        val fullQueue = if (songs.any { it.id == selected.id }) songs else listOf(selected) + songs
        handler.post {
            PlaybackCommands.playQueue(
                context = this,
                songs = fullQueue,
                selectedSongId = selected.id,
                startPositionMs = positionMs.coerceAtLeast(0L),
            )
        }
        return true
    }

    override fun startCast(format: MeloXLanCastFormat): MeloXLanCastHandshake? {
        lanCastReceiver?.stop()
        lanCastReceiver = null
        val receiver = MeloXLanCastReceiver(serviceScope)
        val handshake = receiver.start(format) ?: return null
        lanCastReceiver = receiver
        handler.post { player?.pause() }
        return handshake
    }

    override fun stopCast() {
        lanCastReceiver?.stop()
        lanCastReceiver = null
    }

    override fun startTogether(
        queue: List<MeloXLanTrack>,
        index: Int,
        positionMs: Long,
        playing: Boolean,
        relay: com.lladlam.melox.lan.MeloXLanRelay?,
        sameDevice: Boolean,
    ): Boolean {
        if (relay != null) {
            val items = queue.map { track ->
                val songId = track.id.toLongOrNull()?.takeIf { it > 0L } ?: return false
                val base = relay.baseUrl.removePrefix("http://").removePrefix("https://").trimEnd('/')
                val uri = "melox-lan://$base/audio?token=${java.net.URLEncoder.encode(relay.token, "UTF-8")}&songId=$songId"
                MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(track.id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title.ifBlank { "未知歌曲" })
                            .setArtist(track.artist.ifBlank { "未知歌手" })
                            .setAlbumTitle(track.album)
                            .apply { track.artworkUrl?.let { setArtworkUri(android.net.Uri.parse(it)) } }
                            .build(),
                    )
                    .build()
            }
            if (items.isEmpty()) return false
            handler.post {
                val active = player ?: return@post
                lanTogetherSharesDevice = sameDevice
                applyAudioFocusPolicy(active)
                lanTogetherLastSeekAtMs = android.os.SystemClock.elapsedRealtime()
                active.setMediaItems(items, index.coerceIn(0, items.lastIndex), positionMs.coerceAtLeast(0L))
                active.prepare()
                if (playing) active.play() else active.pause()
            }
            return true
        }
        val songs = queue.mapNotNull { it.toSearchSong() }
        if (songs.isEmpty()) return false
        handler.post {
            val active = player ?: return@post
            lanTogetherSharesDevice = sameDevice
            applyAudioFocusPolicy(active)
            lanTogetherLastSeekAtMs = android.os.SystemClock.elapsedRealtime()
            active.setMediaItems(songs.map { PlaybackCommands.mediaItemFor(it) }, index.coerceIn(0, songs.lastIndex), positionMs.coerceAtLeast(0L))
            active.prepare()
            active.playWhenReady = playing
        }
        return true
    }

    override fun stopTogether() {
        handler.post {
            lanPreparing = false
            lanCommit?.let(handler::removeCallbacks)
            lanCommit = null
            setTogetherBarrier(false)
            player?.let { active ->
                active.pause()
                active.setPlaybackSpeed(1f)
                lanTogetherSharesDevice = false
                applyAudioFocusPolicy(active)
            }
        }
    }

    override fun applyTogetherState(positionMs: Long, playing: Boolean, index: Int) {
        correctTogether(positionMs, playing, index, late = false)
    }

    override fun correctTogether(positionMs: Long, playing: Boolean, index: Int, late: Boolean, forceSeek: Boolean) {
        handler.post {
            val active = player ?: return@post
            if (lanPreparing) return@post
            val target = positionMs.coerceAtLeast(0L)
            if (!forceSeek && lanBarrier && active.duration > 0L && active.currentPosition >= active.duration - 100L) return@post
            val now = android.os.SystemClock.elapsedRealtime()
            if (forceSeek && index in 0 until active.mediaItemCount) {
                active.seekTo(index, target)
                active.setPlaybackSpeed(1f)
                lanTogetherLastSeekAtMs = now
            } else if (!late && index in 0 until active.mediaItemCount && active.currentMediaItemIndex != index) {
                active.seekTo(index, target)
                lanTogetherLastSeekAtMs = now
            } else if (!late && com.lladlam.melox.lan.shouldCorrectTogetherPosition(
                    ready = active.playbackState == Player.STATE_READY,
                    localPositionMs = active.currentPosition,
                    hostPositionMs = target,
                    elapsedSinceSeekMs = now - lanTogetherLastSeekAtMs,
                )) {
                active.seekTo(target)
                active.setPlaybackSpeed(1f)
                lanTogetherLastSeekAtMs = now
            } else {
                active.setPlaybackSpeed(if (!late && playing && active.playbackState == Player.STATE_READY) {
                    com.lladlam.melox.lan.togetherPlaybackRate(target - active.currentPosition)
                } else 1f)
            }
            if (active.playWhenReady != playing) {
                active.playWhenReady = playing
            }
        }
    }

    override fun setTogetherBarrier(enabled: Boolean) {
        if (android.os.Looper.myLooper() != handler.looper) {
            handler.post { setTogetherBarrier(enabled) }
            return
        }
        lanBarrier = enabled
        player?.pauseAtEndOfMediaItems = enabled
        if (enabled) cancelPreparedMix()
        if (!enabled) {
            lanPreparing = false
            lanCommit?.let(handler::removeCallbacks)
            lanCommit = null
            player?.setPlaybackSpeed(1f)
        }
    }

    override suspend fun prepareTogetherRound(
        queue: List<MeloXLanTrack>?, index: Int, positionMs: Long,
        relay: com.lladlam.melox.lan.MeloXLanRelay?, sameDevice: Boolean,
    ): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        val active = player ?: return@withContext false
        setTogetherBarrier(true)
        lanCommit?.let(handler::removeCallbacks)
        lanCommit = null
        lanPreparing = true
        active.pause()
        active.setPlaybackSpeed(1f)
        if (queue != null) {
            if (!startTogether(queue, index, positionMs, false, relay, sameDevice)) return@withContext false
            // startTogether posts its queue mutation; process it before checking READY.
            kotlinx.coroutines.delay(1)
        } else {
            if (active.playbackState == Player.STATE_ENDED || active.currentMediaItemIndex != index) {
                active.seekTo(index.coerceIn(0, (active.mediaItemCount - 1).coerceAtLeast(0)), positionMs)
            }
            active.prepare()
        }
        kotlinx.coroutines.withTimeoutOrNull(30_000L) {
            while (active.playbackState != Player.STATE_READY) {
                if (active.playerError != null) return@withTimeoutOrNull false
                kotlinx.coroutines.delay(10L)
            }
            true
        } ?: false
    }

    override fun commitTogetherRound(startAtLocalMs: Long, positionMs: Long, playing: Boolean) {
        handler.post {
            lanCommit?.let(handler::removeCallbacks)
            val task = Runnable {
                lanPreparing = false
                player?.let { active ->
                    if (kotlin.math.abs(active.currentPosition - positionMs) > 100L) active.seekTo(positionMs)
                    active.setPlaybackSpeed(1f)
                    active.playWhenReady = playing
                }
                lanCommit = null
            }
            lanCommit = task
            handler.postDelayed(task, (startAtLocalMs - System.currentTimeMillis()).coerceIn(0L, 5_000L))
        }
    }

    private fun shouldManageAudioFocus(): Boolean =
        !lanTogetherSharesDevice &&
            !MeloXSettingsPreferences.boolean(this, "playback_allow_other_apps", false)

    private fun applyAudioFocusPolicy(active: ExoPlayer) {
        val managesAudioFocus = shouldManageAudioFocus()
        if (appliedAudioFocusPolicy == managesAudioFocus) return
        active.setAudioAttributes(audioAttributes, managesAudioFocus)
        active.setHandleAudioBecomingNoisy(managesAudioFocus)
        appliedAudioFocusPolicy = managesAudioFocus
    }

    private fun maybePrepareAutoplay(active: ExoPlayer) {
        if (lanBarrier) return
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
                val quality = com.lladlam.melox.core.audio.MeloXNetworkQualityPolicy.effectiveQuality(this@MeloXPlaybackService)
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
        if (lanBarrier) return
        val enabled = MeloXPlaybackModePreferences.autoMix(this)
        if (MeloXPlaybackModeRuntime.autoMixEnabled != enabled) {
            MeloXPlaybackModeRuntime.autoMixEnabled = enabled
        }
        if (!enabled) {
            cancelPreparedMix(releaseStandby = true)
            return
        }
        // The standby deck builds without the PCM tap, so an AutoMix crossfade
        // would silence the USB DAC and strand it after promotion.
        if (com.lladlam.melox.usb.MeloXUsbAudioSession.active) {
            if (hasPreparedMix()) {
                Log.i(TAG, "AutoMix cancelled: USB exclusive output is active")
                cancelPreparedMix(releaseStandby = true)
            }
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
        val mediaId = currentItem.mediaId.takeIf(String::isNotBlank)
        if (shouldClearLegacySystemLyrics(currentItem.mediaId)) {
            restoreSystemLyricsMetadata(active)
            resetSystemLyrics(null)
            getSystemService(NotificationManager::class.java).cancel(LYRICS_NOTIFICATION_ID)
            return
        }
        mediaId ?: return
        if (!metadataEnabled && !effectiveNotification && !VivoAtomicIslandBridge.isSupported()) {
            restoreSystemLyricsMetadata(active)
            superLyricPublisher?.stop()
            colorOsLyricsPublisher?.setLyrics(null, null)
            (getSystemService(NotificationManager::class.java)).cancel(LYRICS_NOTIFICATION_ID)
            return
        }
        if (systemLyricsMediaId != mediaId) resetSystemLyrics(currentItem)
        if (systemLyricsDocument == null && systemLyricsJob?.isActive != true) loadSystemLyrics(mediaId, currentItem)
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
        systemLyricHasTranslation = document.lines.any { !it.translation.isNullOrBlank() }
        var line = document.lines.getOrNull(index)?.text?.trim().orEmpty()
        val nextLine = document.lines.getOrNull(index + 1)?.text?.trim().orEmpty()
        val original = systemLyricsOriginalMetadata ?: currentItem.mediaMetadata.also {
            systemLyricsOriginalMetadata = it
        }
        // External lyric consumers: ColorOS lockscreen lyrics (lyricInfo) and
        // SuperLyricApi publishers reuse the same timeline as the native UI.
        if (metadataEnabled) {
            runCatching {
                val payload = buildColorOsLyricPayload(
                    document = document,
                    songId = legacyNumericSystemId(mediaId),
                    songName = original.title?.toString().orEmpty(),
                    artistName = original.artist?.toString().orEmpty(),
                    albumName = original.albumTitle?.toString(),
                    packageName = packageName,
                    sessionGeneration = systemLyricsSessionGeneration,
                )
                colorOsLyricsPublisher?.setLyrics(
                    mediaId = mediaId,
                    payload = payload,
                    hasTranslation = systemLyricHasTranslation,
                )
            }.onFailure { Log.w(TAG, "Unable to publish ColorOS lyrics", it) }
            runCatching {
                superLyricPublisher?.setSong(
                    document = document,
                    songId = mediaId,
                    songName = original.title?.toString(),
                    artistName = original.artist?.toString(),
                    albumName = original.albumTitle?.toString(),
                    translation = MeloXSettingsRuntime.showLyricTranslation,
                    roma = MeloXSettingsRuntime.showLyricRomanization,
                )
                superLyricPublisher?.syncPlayback()
            }.onFailure { Log.w(TAG, "Unable to publish SuperLyric lyrics", it) }
        }
        if (line.isBlank()) {
            line = renderNotificationTemplate(MeloXSettingsRuntime.lyricNotificationFallback, "", original)
                .ifBlank { original.title?.toString().orEmpty() }
        }
        if (line.isBlank()) return
        val titleMode = MeloXSettingsRuntime.systemLyricTitleMode
        val isOplus = Build.MANUFACTURER.let { manufacturer ->
            listOf("OPPO", "OnePlus", "realme", "OPlus").any { manufacturer.equals(it, ignoreCase = true) }
        }
        // On OPlus devices the ColorOS lyricInfo channel is authoritative;
        // replacing title/artist on top of it makes the lockscreen double-render.
        if (metadataEnabled && lineChanged && !isOplus) {
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

    private fun loadSystemLyrics(mediaId: String, item: MediaItem) {
        systemLyricsMediaId = mediaId
        systemLyricsOriginalMetadata = item.mediaMetadata
        systemLyricsJob = serviceScope.launch {
            val metadata = item.mediaMetadata
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    MeloXProviderLyricsLoader.load(
                        context = this@MeloXPlaybackService,
                        mediaId = mediaId,
                        title = metadata.title?.toString().orEmpty(),
                        artist = metadata.artist?.toString().orEmpty(),
                        album = metadata.albumTitle?.toString().orEmpty(),
                        artworkUrl = metadata.artworkUri?.toString(),
                        durationMs = metadata.durationMs?.takeIf { it > 0L }
                            ?: player?.duration?.takeIf { it != C.TIME_UNSET && it > 0L }
                            ?: 0L,
                        // System/floating surfaces only need line text, so keep
                        // the fast document and skip background AutoSync jobs.
                        onUpgrade = null,
                    )
                }.getOrNull()
            }
            if (systemLyricsMediaId == mediaId) systemLyricsDocument = loaded
            systemLyricsJob = null
        }
    }

    /** ColorOS only needs a stable numeric identity; real NetEase IDs stay intact. */
    private fun legacyNumericSystemId(mediaId: String): Long =
        mediaId.toLongOrNull()?.takeIf { it > 0L }
            ?: (mediaId.hashCode().toLong() and 0xffffffffL)

    private fun resetSystemLyrics(item: MediaItem?) {
        lyricsNotificationArtwork.clear()
        VivoAtomicIslandBridge.clear(this)
        colorOsLyricsPublisher?.setLyrics(null, null)
        superLyricPublisher?.stop()
        systemLyricsSessionGeneration++
        systemLyricsJob?.cancel()
        systemLyricsJob = null
        systemLyricsMediaId = item?.mediaId
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
        } ?: systemLyricsMediaId?.let { mediaId ->
            (0 until active.mediaItemCount).firstOrNull { candidate ->
                val item = active.getMediaItemAt(candidate)
                item.mediaId == mediaId &&
                    item.mediaMetadata.extras?.containsKey(SYSTEM_ORIGINAL_TITLE_KEY) == true
            }
        } ?: return
        val current = active.getMediaItemAt(index)
        updatingSystemLyricsMetadata = true
        active.replaceMediaItem(index, current.buildUpon().setMediaMetadata(original).build())
        handler.post { updatingSystemLyricsMetadata = false }
    }

    private fun createLyricsNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
        lyricsNotificationSource = Triple(line, nextLine, metadata)
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
            lyricsNotificationArtwork.current()?.let(builder::setLargeIcon)
            val bytes = metadata.artworkData
            if (bytes != null && !lyricsNotificationArtwork.isCurrent(bytes)) {
                lyricsNotificationArtwork.enqueue(bytes) { bitmap ->
                    notificationArtworkRevision++
                    handler.removeCallbacks(notificationArtworkRefresh)
                    handler.postDelayed(notificationArtworkRefresh, 120L)
                }
            }
        } else {
            lyricsNotificationArtwork.clear()
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
        if (MeloXSettingsPreferences.boolean(this, "hyperos_super_island_enabled", false)) {
            runCatching {
                HyperOsFocusBridge.playbackPayload(
                    context = this,
                    lyric = line,
                    nextLyric = nextLine,
                    songTitle = metadata.title?.toString().orEmpty(),
                    artist = metadata.artist?.toString().orEmpty(),
                    positionMs = player?.currentPosition ?: 0L,
                    durationMs = player?.duration?.takeIf { it != C.TIME_UNSET } ?: 0L,
                    isPlaying = player?.isPlaying == true,
                )?.let { HyperOsFocusBridge.attachFocusParams(notification, it) }
            }.onFailure { Log.w(TAG, "Unable to publish optional Super Island lyrics", it) }
        }
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
                        val (outgoing, incomingAnalysis) = coroutineScope {
                            val outgoingDeferred = async { analyzeFullTrack(outgoingAnalysisItem, outgoingId) }
                            val incomingDeferred = async { analyzeFullTrack(incomingAnalysisItem, incomingId) }
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

    private fun skipToNextDownloaded(active: ExoPlayer): Boolean =
        skipToNextMatching(active) { item ->
            item.mediaId.toLongOrNull()?.let(downloadStore::contains) == true
        }

    /**
     * 向前（列表循环时允许回卷到队首）跳到第一首满足 [accept] 的曲目并立刻起播。
     * ⚠ **只向前，绝不回退到上一首。**
     */
    private fun skipToNextMatching(active: ExoPlayer, accept: (MediaItem) -> Boolean): Boolean {
        val current = active.currentMediaItemIndex
        if (current !in 0 until active.mediaItemCount) return false
        val forward = ((current + 1) until active.mediaItemCount).toList()
        val wrapped = if (active.repeatMode == Player.REPEAT_MODE_ALL) {
            (0 until current).toList()
        } else {
            emptyList()
        }
        val target = (forward + wrapped).firstOrNull { index -> accept(active.getMediaItemAt(index)) }
            ?: return false
        cancelPreparedMix()
        active.seekToDefaultPosition(target)
        if (active.playbackState == Player.STATE_IDLE) active.prepare()
        active.play()
        return true
    }

    /** 无音源专用：跳到**任意**下一首（单曲循环下不跳，否则会原地打转）。 */
    private fun skipForwardFromUnavailable(active: ExoPlayer): Boolean {
        // 单曲循环：Media3 眼里「下一首」就是自己 ⇒ 跳过去还是这首，只会无限打转。
        if (active.repeatMode == Player.REPEAT_MODE_ONE) return false
        // 整队都无音源 ⇒ 别再跳了，否则 error → skip → error 会一直转下去。
        // 连续跳过上限 = 队列长度（跳完一圈还没播起来，就说明确实一首都播不了）。
        if (unavailableSkipStreak >= active.mediaItemCount.coerceAtLeast(1)) {
            Log.w(TAG, "Skipped $unavailableSkipStreak unavailable items in a row; stop to avoid a loop")
            unavailableSkipStreak = 0
            return false
        }
        unavailableSkipStreak++
        return skipToNextMatching(active) { true }
    }

    private fun isUnavailableSourceError(error: PlaybackException): Boolean {
        var cursor: Throwable? = error
        var depth = 0
        while (cursor != null && depth++ < 8) {
            if (cursor is MeloXPlaybackUnavailableException) return true
            cursor = cursor.cause
        }
        return false
    }

    /**
     * 这首歌**真的出声了** —— 「播过一首歌」的唯一合法落库点。
     *
     * 播放历史（网易云 weblog）与自动缓存计数都只在这里写，不再写在
     * `onMediaItemTransition`：无音源的歌照样会触发 transition，却永远不会出声。
     * [startedSongId] 做去重，暂停/恢复、单曲循环重播都不会重复记。
     */
    private fun markSongActuallyPlayed(active: Player) {
        unavailableSkipStreak = 0
        val item = active.currentMediaItem ?: return
        val id = item.mediaId.toLongOrNull() ?: return
        if (id == startedSongId) return
        startedSongId = id
        // transition 漏设 / 顺序异常时兜住，保证 recordDuration 记到的是同一首。
        if (historySongId != id) historySongId = id
        playbackHistoryReporter.recordStart(id)
        downloadStore.recordPlayback(item)
        Log.d(TAG, "Playback actually started for mediaId=${item.mediaId}")
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
        val pkg = controllerInfo.packageName
        val uid = controllerInfo.uid
        // External media controllers that are safe to hand the session to:
        //  - Self package (in-app UI)
        //  - Google Car / Bluetooth (universal media consumers)
        //  - System UI (lockscreen card, notification, quick settings)
        //  - vivo 原子随身听 (OriginOS walkman card, wear, system music panel)
        //  - Any process in the system uid range [1000, 2000)
        // Everything else is denied to avoid accidental cross-app leakage.
        val isSystemMediaController = pkg == "com.android.systemui" ||
            pkg == "com.android.bluetooth" ||
            pkg == "com.google.android.projection.gearhead"
        val isVivoSystem = pkg == "com.vivo.music" ||
            pkg == "com.vivo.wearmanager" ||
            pkg.startsWith("com.vivo.music") ||
            pkg.startsWith("com.vivo.atomic") ||
            pkg.startsWith("com.iqoo.music")
        val allowed = pkg == packageName ||
            isSystemMediaController ||
            isVivoSystem ||
            uid in 1000 until 2000
        Log.d(TAG, "Controller connected: $pkg (uid=$uid, allowed=$allowed)")
        return mediaSession.takeIf { allowed }
    }

    override fun onDestroy() {
        com.lladlam.melox.usb.MeloXUsbAudioSession.release()
        // Remote sinks outlive a cancelled service scope; release them before
        // the local player disappears so their polling cannot target a dead session.
        runCatching { com.lladlam.melox.miplay.MiPlaySession.disconnect() }
        serviceScope.launch { runCatching { com.lladlam.melox.dlna.DlnaSession.disconnect(resumeLocal = false) } }
        MeloXLanRuntime.detach(this)
        handler.removeCallbacks(lanStateRunnable)
        lanCastReceiver?.stop()
        lanCastReceiver = null
        com.lladlam.melox.core.autosync.AutoSyncPlaybackBridge.audioResolver = null
        com.lladlam.melox.core.autosync.AutoSyncPlaybackBridge.isCurrentTrack = { false }
        persistQueueIfEnabled(immediate = true)
        historySongId?.let { playbackHistoryReporter.recordDuration(it, elapsedMs = listenedTimeTracker.elapsedMs(SystemClock.elapsedRealtime())) }
        historySongId = null
        playbackHistoryReporter.close()
        handler.removeCallbacks(modeMonitor)
        handler.removeCallbacks(queuePersistRunnable)
        recommendationJob?.cancel()
        playlistAnalysisJob?.cancel()
        backgroundAnalysisJob?.cancel()
        smartQueueJob?.cancel()
        prefetchJob?.cancel()
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
        colorOsLyricsPublisher?.release()
        colorOsLyricsPublisher = null
        superLyricPublisher?.stop()
        superLyricPublisher = null
        player?.removeListener(playerListener)
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistQueueIfEnabled(immediate = true)
        super.onTaskRemoved(rootIntent)
    }

    private fun persistQueueIfEnabled(immediate: Boolean = false) {
        if (!MeloXSettingsPreferences.boolean(this, "playback_save_queue", true)) return
        player?.let { MeloXPlaybackQueueStore.save(this, it, flush = immediate) }
    }

    /**
     * 可靠持久化队列：除 `onDestroy`/`onTaskRemoved` 外，在**时间线变化**
     * （`playQueue` 一次性 setMediaItems 后立刻触发）和**切歌**
     * （`onMediaItemTransition`）立即落盘，并辅以播放中的周期写（覆盖进度断点）。
     *
     * 旧实现只在 `onDestroy` 写：vivo 杀进程（OOM / 划后台 / 重启）不保证
     * `onDestroy` 跑 ⇒ `melox_playback_queue` 长期为空 ⇒ 冷启动 `restorePersistedQueue`
     * 读到 null，待播放列表为空（或只剩 UI 侧 `restoreLastPlaybackIfNeeded` 塞的 1 首）。
     *
     * `handler` 是主线程 Looper，写 prefs 走 `apply()`（非 flush）避免卡 UI；
     * `onDestroy`/`onTaskRemoved` 走 `immediate=true` 的 `commit()` 保证退出前落盘。
     */
    private var lastQueuePersistElapsedMs = 0L
    private val QUEUE_PERSIST_INTERVAL_MS = 10_000L
    private val queuePersistRunnable = object : Runnable {
        override fun run() {
            val active = player
            if (active != null && active.isPlaying && active.mediaItemCount > 0) {
                requestQueuePersist(force = false)
            }
            handler.postDelayed(this, QUEUE_PERSIST_INTERVAL_MS)
        }
    }

    private fun requestQueuePersist(force: Boolean) {
        val active = player ?: return
        if (active.mediaItemCount == 0) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastQueuePersistElapsedMs < QUEUE_PERSIST_INTERVAL_MS) return
        lastQueuePersistElapsedMs = now
        handler.post { persistQueueIfEnabled(immediate = false) }
    }

    private fun persistQueuePositionIfEnabled(active: ExoPlayer) {
        if (!MeloXSettingsPreferences.boolean(this, "playback_save_queue", true)) return
        if (active.mediaItemCount == 0) return
        MeloXPlaybackQueueStore.savePosition(
            this,
            active.currentMediaItemIndex.coerceAtLeast(0),
            active.currentPosition.coerceAtLeast(0L),
        )
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

    /**
     * Expose FAVORITE-family custom actions on the MediaSession so external
     * system controllers (vivo 原子随身听, Wear, lockscreen, Android Auto) can
     * discover a favorite action.
     *
     * We publish multiple ids in parallel because vivo's walkman spec is not
     * publicly documented; covering the AOSP convention, the legacy Google
     * Play Music convention, and a vivo-private fallback maximizes the odds
     * the panel recognizes the action on any given ROM revision.
     */
    private fun publishSystemFavoriteCustomActions() {
        runCatching {
            val session = mediaSession ?: return
            val buttons = listOf(
                CommandButton.Builder()
                    .setSessionCommand(SessionCommand(ACTION_CUSTOM_FAVORITE, Bundle()))
                    .setDisplayName("收藏")
                    .setIconResId(android.R.drawable.ic_menu_gallery)
                    .build(),
                CommandButton.Builder()
                    .setSessionCommand(SessionCommand(ACTION_CUSTOM_GPM_FAVORITE, Bundle()))
                    .setDisplayName("收藏")
                    .setIconResId(android.R.drawable.ic_menu_gallery)
                    .build(),
                // HyperOS 4 may only surface shuffle/repeat from the slot-based
                // custom layout. These are standard player commands, so the
                // session executes them without a custom handler.
                CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE)
                    .setDisplayName("随机播放")
                    .setIconResId(android.R.drawable.ic_menu_sort_by_size)
                    .build(),
                CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE)
                    .setDisplayName("循环模式")
                    .setIconResId(android.R.drawable.ic_menu_rotate)
                    .build(),
            )
            session.setCustomLayout(buttons)
            Log.i(TAG, "Published ${buttons.size} custom actions on MediaSession")
        }.onFailure { error ->
            Log.w(TAG, "Failed to publish system favorite custom actions", error)
        }
    }

    /**
     * Publishes the two user-selectable media buttons (left / right slot) on
     * the MediaSession. Settings → System lyrics lets the user pick between
     * play-mode cycling, favorite, floating lyrics, or no button.
     */
    private fun refreshNotificationButtons() {
        val session = mediaSession ?: return
        runCatching {
            notificationLeftAction = MeloXNotificationAction.fromPreference(
                MeloXSettingsPreferences.string(this, "notification_left_action", "").takeIf { it.isNotBlank() },
                MeloXNotificationAction.PlayMode,
            )
            notificationRightAction = MeloXNotificationAction.fromPreference(
                MeloXSettingsPreferences.string(this, "notification_right_action", "").takeIf { it.isNotBlank() },
                MeloXNotificationAction.Favorite,
            )
            val buttons = listOfNotNull(
                notificationButton(notificationLeftAction, left = true),
                notificationButton(notificationRightAction, left = false),
            )
            if (buttons != notificationButtons) {
                notificationButtons = buttons
                session.setMediaButtonPreferences(buttons)
                Log.i(TAG, "Updated notification buttons: left=$notificationLeftAction right=$notificationRightAction")
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to refresh notification buttons", error)
        }
    }

    private fun notificationButton(action: MeloXNotificationAction, left: Boolean): CommandButton? {
        val active = player ?: return null
        val (iconRes, labelRes) = when (action) {
            MeloXNotificationAction.None -> return null
            MeloXNotificationAction.PlayMode -> when {
                MeloXPlaybackModePreferences.shuffle(this) ->
                    R.drawable.ic_melox_notification_shuffle to R.string.notification_mode_shuffle
                active.repeatMode == Player.REPEAT_MODE_ONE ->
                    R.drawable.ic_melox_notification_repeat_one to R.string.notification_mode_repeat_one
                else ->
                    R.drawable.ic_melox_notification_repeat_all to R.string.notification_mode_repeat_all
            }
            MeloXNotificationAction.Favorite -> R.drawable.ic_melox_notification_favorite to R.string.notification_action_favorite
            MeloXNotificationAction.FloatingLyrics -> R.drawable.ic_melox_notification_floating_lyrics to R.string.notification_action_floating_lyrics
        }
        // ICON_UNDEFINED keeps controllers from substituting a predefined icon
        // for our custom glyph.
        return CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(iconRes)
            .setDisplayName(getString(labelRes))
            .setSessionCommand(
                SessionCommand(
                    if (left) ACTION_NOTIFICATION_LEFT else ACTION_NOTIFICATION_RIGHT,
                    Bundle.EMPTY,
                ),
            )
            .setSlots(
                if (left) CommandButton.SLOT_BACK_SECONDARY else CommandButton.SLOT_FORWARD_SECONDARY,
                CommandButton.SLOT_OVERFLOW,
            )
            .setEnabled(active.currentMediaItem != null)
            .build()
    }

    private fun handleNotificationAction(action: MeloXNotificationAction): ListenableFuture<SessionResult> = when (action) {
        MeloXNotificationAction.None -> Futures.immediateFuture(
            SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED),
        )
        MeloXNotificationAction.PlayMode -> {
            if (player?.currentMediaItem == null) {
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))
            } else {
                switchPlayModeCycle()
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }
        MeloXNotificationAction.Favorite -> {
            serviceScope.launch {
                handleSystemFavoriteToggle(outcome = { Log.i(TAG, "Notification favorite: $it") })
            }
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        MeloXNotificationAction.FloatingLyrics -> {
            val success = player?.currentMediaItem != null && requestFloatingLyricsPip()
            Futures.immediateFuture(
                SessionResult(
                    if (success) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_INVALID_STATE,
                ),
            )
        }
    }

    /** Cycles 列表循环 → 单曲循环 → 随机播放 and never leaves the three states. */
    private fun switchPlayModeCycle() {
        val active = player ?: return
        when {
            MeloXPlaybackModePreferences.shuffle(this) -> {
                PlaybackCommands.setExplicitShuffle(this, active, false)
                active.repeatMode = Player.REPEAT_MODE_ALL
            }
            active.repeatMode == Player.REPEAT_MODE_ONE -> {
                active.repeatMode = Player.REPEAT_MODE_ALL
                PlaybackCommands.setExplicitShuffle(this, active, true)
            }
            else -> {
                active.repeatMode = Player.REPEAT_MODE_ONE
            }
        }
        refreshNotificationButtons()
    }

    /** Filled in by the floating-lyrics PiP implementation; returns false while unavailable. */
    private fun requestFloatingLyricsPip(): Boolean {
        return com.lladlam.melox.ui.player.MeloXFloatingLyricsPip.request(this)
    }

    /**
     * Handle a favorite custom command dispatched by an external system
     * controller (vivo 原子随身听 etc.). Toggles the favorite state of the
     * currently playing track against the provider that actually owns it.
     *
     * This is intentionally narrow:
     *  - Only tracks that carry a resolvable [PlaybackTrackIdentity] (i.e.
     *    provider-backed MediaItems) participate.
     *  - Only providers that implement [FavoriteCapability] participate.
     *  - NetEase legacy tracks (mediaId = numeric id) are silently ignored;
     *    those tracks don't flow through the provider FavoriteCapability path.
     *  - On failure, the caller gets a SessionResult(RESULT_SUCCESS) anyway
     *    because external controllers generally can't display a rich error,
     *    and we want to avoid a confusing failure toast from system UI.
     */
    private suspend fun handleSystemFavoriteToggle(outcome: (String) -> Unit = {}) {
        val active = player ?: return outcome("no player")
        val item = active.currentMediaItem ?: return outcome("no current item")
        if (MeloXUnifiedFavorites.enabled(this)) {
            val identity = PlaybackTrackIdentity.fromMediaItem(item)
                ?: item.mediaId.toLongOrNull()?.takeIf { it > 0L }
                    ?.let { MusicResourceId(MusicSource.Netease, it.toString()) }
                ?: return outcome("unresolvable identity")
            val track = buildMusicTrackFromMediaItem(item, identity)
            val targetFavorite = MeloXUnifiedFavorites.toggle(this, track)
            runCatching {
                val extras = Bundle(item.mediaMetadata.extras).apply {
                    putBoolean(PlaybackTrackIdentity.IsFavoriteExtra, targetFavorite)
                }
                active.replaceMediaItem(
                    active.currentMediaItemIndex,
                    item.buildUpon()
                        .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build())
                        .build(),
                )
            }
            Log.i(TAG, "System favorite toggled in MeloX favorites -> $targetFavorite")
            outcome("favorited=$targetFavorite")
            return
        }
        val identity = PlaybackTrackIdentity.fromMediaItem(item)
        if (identity == null) {
            // NetEase legacy MediaItems use the numeric song id as mediaId and
            // have no provider identity. They are still the most common case,
            // so the system favorite button must not silently do nothing.
            val songId = item.mediaId.toLongOrNull()?.takeIf { it > 0L }
                ?: return outcome("unresolvable identity")
            val cookie = { NeteaseSessionStore.readCookie(this) }
            val targetFavorite = withContext(Dispatchers.IO) {
                val session = NeteaseSessionStore(this@MeloXPlaybackService)
                if (session.profile == null) session.refreshProfile()
                val userId = session.profile?.userId
                val currentlyFavorite = userId != null && runCatching {
                    NeteaseLibraryClient(cookieProvider = cookie)
                        .likedSongIdsBlocking(userId)
                        .contains(songId)
                }.getOrDefault(false)
                !currentlyFavorite
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    NeteaseMusicOperationsClient(cookieProvider = cookie)
                        .setSongLiked(songId, targetFavorite)
                }
            }.onSuccess {
                Log.i(TAG, "System favorite toggled for NetEase song $songId -> $targetFavorite")
                outcome("favorited=$targetFavorite")
            }.onFailure { error ->
                Log.w(TAG, "System favorite toggle failed for NetEase song $songId", error)
                outcome("failed: ${error.message}")
            }
            return
        }
        val registry = ProviderPlaybackRuntime.registryOrNull() ?: return outcome("no provider registry")
        val provider = registry[identity.sourceId]
        if (identity.sourceId.builtIn == null && provider?.supports(com.lladlam.melox.core.music.provider.MusicCapability.Favorites) != true)
            return outcome("provider does not declare Favorites")
        val favoriteCapability = provider as? FavoriteCapability ?: return outcome("provider has no FavoriteCapability: ${identity.sourceId.displayName}")

        val currentExtras = item.mediaMetadata.extras
        val currentlyFavorite = currentExtras?.getBoolean(PlaybackTrackIdentity.IsFavoriteExtra, false) ?: false
        val actionTrack = buildMusicTrackFromMediaItem(item, identity)
        val targetFavorite = runCatching {
            if (favoriteCapability is com.lladlam.melox.core.music.provider.FavoriteStateCapability)
                !favoriteCapability.isFavorite(actionTrack) else !currentlyFavorite
        }.getOrElse { return outcome("favorite status query failed") }

        runCatching {
            favoriteCapability.setFavorite(actionTrack, targetFavorite)
        }.onSuccess {
            val updatedExtras = Bundle(currentExtras).apply {
                putBoolean(PlaybackTrackIdentity.IsFavoriteExtra, targetFavorite)
            }
            val updatedItem = item.buildUpon()
                .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(updatedExtras).build())
                .build()
            runCatching {
                active.replaceMediaItem(active.currentMediaItemIndex, updatedItem)
            }.onFailure { metaError ->
                Log.w(TAG, "Failed to refresh MediaItem extras after favorite toggle", metaError)
            }
            Log.i(TAG, "System favorite toggled for ${identity.sourceId.displayName}:${identity.value} -> $targetFavorite")
            outcome("favorited=${targetFavorite}")
        }.onFailure { error ->
            Log.w(TAG, "System favorite toggle failed for ${identity.sourceId.displayName}:${identity.value}", error)
            outcome("failed: ${error.message}")
        }
    }

    private fun buildMusicTrackFromMediaItem(item: MediaItem, identity: MusicResourceId): MusicTrack {
        PlaybackTrackIdentity.restoredTrack(item)?.takeIf { it.id == identity }?.let { return it }
        val extras = item.mediaMetadata.extras
        val title = extras?.getString(PlaybackTrackIdentity.TitleExtra)
            ?.takeIf { it.isNotBlank() }
            ?: item.mediaMetadata.title?.toString().orEmpty()
        val artist = extras?.getString(PlaybackTrackIdentity.ArtistExtra)
            ?.takeIf { it.isNotBlank() }
            ?: item.mediaMetadata.artist?.toString().orEmpty()
        val album = extras?.getString(PlaybackTrackIdentity.AlbumExtra)
            ?.takeIf { it.isNotBlank() }
        val artwork = extras?.getString(PlaybackTrackIdentity.ArtworkExtra)
            ?.takeIf { it.isNotBlank() }
        val durationMs = extras?.getLong(PlaybackTrackIdentity.DurationMsExtra, 0L)
            ?.takeIf { it > 0L }
        return MusicTrack(
            id = identity,
            title = title.ifBlank { "未知歌曲" },
            artists = listOf(MusicArtistRef(name = artist.ifBlank { "未知歌手" })),
            artworkUrl = artwork,
            durationMs = durationMs,
            availability = TrackAvailability.Playable,
            providerMetadata = when (identity.sourceId.builtIn) {
                MusicSource.Netease -> ProviderTrackMetadata.Netease(
                    numericId = identity.value.toLongOrNull() ?: 0L,
                )
                MusicSource.QQMusic -> ProviderTrackMetadata.QQMusic(songMid = identity.value)
                MusicSource.Kugou -> ProviderTrackMetadata.Kugou(hash = identity.value)
                MusicSource.Kuwo -> ProviderTrackMetadata.Kuwo(mid = identity.value.toLongOrNull() ?: 0L)
                MusicSource.AppleMusic -> ProviderTrackMetadata.AppleMusic(
                    catalogId = identity.value,
                    storefront = "us",
                )
                MusicSource.Bilibili -> com.lladlam.melox.core.provider.bilibili.BilibiliProvider
                    .parseIdentity(identity.value)
                    ?.let { (bvid, cid) -> ProviderTrackMetadata.Bilibili(bvid, cid) }
                    ?: ProviderTrackMetadata.Empty
                MusicSource.Spotify -> ProviderTrackMetadata.Spotify(trackId = identity.value)
                MusicSource.YouTubeMusic -> ProviderTrackMetadata.YouTube(videoId = identity.value)
                MusicSource.Local -> ProviderTrackMetadata.Local(
                    contentUri = item.localConfiguration?.uri?.getQueryParameter("localContentUri").orEmpty(),
                    fileKey = identity.value,
                )
                MusicSource.Jellyfin -> ProviderTrackMetadata.Empty
                MusicSource.Qishui -> ProviderTrackMetadata.Empty
                MusicSource.KugouLite -> ProviderTrackMetadata.Kugou(hash = identity.value)
                null -> ProviderTrackMetadata.Empty
            },
        )
    }

    companion object {
        const val ACTION_ANALYZE_PLAYLIST = "com.lladlam.melox.action.ANALYZE_PLAYLIST"
        const val ACTION_PREVIOUS = "com.lladlam.melox.action.PREVIOUS"
        const val ACTION_NEXT = "com.lladlam.melox.action.NEXT"
        const val ACTION_TOGGLE_PLAYBACK = "com.lladlam.melox.action.TOGGLE_PLAYBACK"
        const val EXTRA_ANALYSIS_SOURCE = "analysis_source"
        const val EXTRA_ANALYSIS_PLAYLIST_ID = "analysis_playlist_id"
        const val TAG = "MeloXPlayback"
        // Well-known custom action ids used by system music controllers.
        // android.media.action.FAVORITE is the AOSP convention; com.google
        // .android.music.FAVORITE is the legacy Google Play Music convention;
        // the vivo-private one is a defensive fallback because vivo's
        // 原子随身听 spec is not publicly documented.
        const val ACTION_CUSTOM_FAVORITE = "android.media.action.FAVORITE"
        const val ACTION_CUSTOM_LIKE = "android.media.action.LIKE"
        const val ACTION_NOTIFICATION_LEFT = "com.lladlam.melox.action.NOTIFICATION_LEFT"
        const val ACTION_NOTIFICATION_RIGHT = "com.lladlam.melox.action.NOTIFICATION_RIGHT"
        const val ACTION_CUSTOM_GPM_FAVORITE = "com.google.android.music.FAVORITE"
        const val ACTION_CUSTOM_VIVO_FAVORITE = "com.vivo.media.action.FAVORITE"
        const val AUTOPLAY_PRELOAD_MS = 60_000L
        const val AUTOPLAY_RETRY_MS = 15_000L
        const val PREFETCH_TRACK_COUNT = 3
        const val PLAYBACK_MAINTENANCE_INTERVAL_MS = 1_000L
        const val QUEUE_POSITION_PERSIST_INTERVAL_MS = 2_000L
        const val AUTOMIX_ENVELOPE_INTERVAL_MS = 20L
        const val AUTOMIX_FAILURE_COOLDOWN_MS = 30_000L
        const val SMART_QUEUE_ANALYSIS_CONCURRENCY = 2

        /** 滚动预分析窗口：只保证播放位置往后这么多首以内的候选有分析结果。 */
        const val SMART_QUEUE_ANALYSIS_WINDOW = 6
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
    mediaId.isNullOrBlank() || PlaybackTrackIdentity.decode(mediaId) == null

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
