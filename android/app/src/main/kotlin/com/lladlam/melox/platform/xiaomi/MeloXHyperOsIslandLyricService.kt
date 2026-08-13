package com.lladlam.melox.platform.xiaomi

import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lladlam.melox.MainActivity
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.provider.LyricsCapability
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.playback.MeloXPlaybackService
import com.lladlam.melox.playback.PlaybackTrackIdentity
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated foreground owner for HyperOS Focus V3 lyric notifications.
 *
 * Keeping the Focus notification separate from Media3's standard media notification mirrors
 * the lifecycle HyperOS expects and avoids coupling Xiaomi-only payload updates to playback.
 */
class MeloXHyperOsIslandLyricService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var lyricsJob: Job? = null
    private var lyricKey: String? = null
    private var lyrics: LyricsDocument? = null
    private var lastPublishedIndex = Int.MIN_VALUE
    private var lastPublishedAt = 0L
    private var startedForeground = false

    private val providerRegistry by lazy { MeloXMusicProviders.create(applicationContext) }
    private val downloads by lazy { MeloXDownloadStore.get(applicationContext) }

    private val contentIntent by lazy {
        PendingIntent.getActivity(
            this,
            0x4d58,
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_NOW_PLAYING
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private val updater = object : Runnable {
        override fun run() {
            runCatching { updateIsland() }
                .onFailure { Log.w(TAG, "HyperOS island lyric update failed", it) }
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!HyperOsFocusBridge.supportsSuperIsland(this)) {
            stopSelf()
            return
        }
        startAsForeground(HyperOsFocusV3NotificationFactory.warmNotification(this, contentIntent))
        connectController()
        handler.post(updater)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isEnabled() || !HyperOsFocusBridge.supportsSuperIsland(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!startedForeground) {
            startAsForeground(HyperOsFocusV3NotificationFactory.warmNotification(this, contentIntent))
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, MeloXPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        val executor = Executor { command -> mainExecutor.execute(command) }
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { controller = it }
                    .onFailure { Log.w(TAG, "Unable to connect HyperOS lyric controller", it) }
            },
            executor,
        )
    }

    private fun updateIsland() {
        if (!isEnabled()) {
            stopSelf()
            return
        }
        val active = controller ?: return
        val item = active.currentMediaItem ?: return
        val identity = PlaybackTrackIdentity.fromMediaItem(item) ?: return
        val key = "${identity.source.storageValue}:${identity.value}"
        if (key != lyricKey) requestLyrics(item, key)

        val document = lyrics ?: return
        val advance = MeloXSettingsPreferences.int(this, "lyrics_advance_ms", 0)
        val position = (active.currentPosition + advance).coerceAtLeast(0L)
        val index = document.highlightedIndex(position) ?: return
        if (index !in document.lines.indices) return

        val now = SystemClock.elapsedRealtime()
        if (index == lastPublishedIndex) return
        if (lastPublishedIndex != Int.MIN_VALUE &&
            now - lastPublishedAt < HyperOsFocusV3NotificationFactory.MinimumRenderIntervalMs
        ) return

        val line = document.lines[index].text.trim().ifBlank { return }
        val next = document.lines.getOrNull(index + 1)?.text.orEmpty()
        val metadata = item.mediaMetadata
        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        val notification = HyperOsFocusV3NotificationFactory.lyricNotification(
            context = this,
            contentIntent = contentIntent,
            lyric = line,
            nextLine = next,
            songTitle = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            positionMs = active.currentPosition,
            durationMs = duration,
            isPlaying = active.isPlaying,
        )

        startAsForeground(notification)
        lastPublishedIndex = index
        lastPublishedAt = now
    }

    private fun requestLyrics(item: MediaItem, key: String) {
        lyricKey = key
        lyrics = null
        lastPublishedIndex = Int.MIN_VALUE
        lastPublishedAt = 0L
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { loadLyrics(item) }
                    .onFailure { Log.w(TAG, "Unable to load HyperOS island lyrics for $key", it) }
                    .getOrNull()
            }
            if (lyricKey == key) lyrics = loaded
            lyricsJob = null
        }
    }

    private suspend fun loadLyrics(item: MediaItem): LyricsDocument {
        val identity = PlaybackTrackIdentity.fromMediaItem(item)
            ?: return LyricsDocument(emptyList())
        if (identity.source == MusicSource.Netease) {
            val songId = identity.value.toLongOrNull() ?: return LyricsDocument(emptyList())
            return downloads.localLyrics(songId)
                ?: NeteaseSearchClient(
                    cookieProvider = { NeteaseSessionStore.readCookie(applicationContext) },
                ).lyrics(songId)
        }

        val capability = providerRegistry.require(identity.source) as? LyricsCapability
            ?: return LyricsDocument(emptyList())
        val metadata = item.mediaMetadata
        val artists = metadata.artist?.toString().orEmpty()
            .split(Regex("\\s*(?:、|/|&|,|;|；)\\s*"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .ifEmpty { listOf("未知歌手") }
            .map { MusicArtistRef(name = it) }
        val albumName = metadata.albumTitle?.toString().orEmpty()
        val track = MusicTrack(
            id = identity,
            title = metadata.title?.toString().orEmpty().ifBlank { "未知歌曲" },
            artists = artists,
            album = albumName.takeIf(String::isNotBlank)?.let { name ->
                MusicAlbumRef(
                    name = name,
                    artworkUrl = metadata.artworkUri?.toString(),
                )
            },
            artworkUrl = metadata.artworkUri?.toString(),
            durationMs = controller?.duration?.takeIf { it != C.TIME_UNSET && it > 0L },
        )
        return capability.lyrics(track)
    }

    private fun isEnabled(): Boolean =
        MeloXSettingsPreferences.boolean(this, PREFERENCE_KEY, false)

    private fun startAsForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                HyperOsFocusV3NotificationFactory.NotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(HyperOsFocusV3NotificationFactory.NotificationId, notification)
        }
        startedForeground = true
    }

    override fun onDestroy() {
        handler.removeCallbacks(updater)
        lyricsJob?.cancel()
        controller?.release()
        controller = null
        scope.cancel()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        super.onDestroy()
    }

    companion object {
        const val PREFERENCE_KEY = "lyrics_notifications_enabled"
        private const val UPDATE_INTERVAL_MS = 250L
        private const val TAG = "MeloXHyperOsIsland"
    }
}
