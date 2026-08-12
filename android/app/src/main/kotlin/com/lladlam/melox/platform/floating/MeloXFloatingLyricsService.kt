package com.lladlam.melox.platform.floating

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lladlam.melox.MainActivity
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.playback.MeloXPlaybackService
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import com.lladlam.melox.ui.settings.MeloXSecondaryLyricMode
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** System overlay renderer used while another app is in the foreground. */
class MeloXFloatingLyricsService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { handler.post(it) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private var primaryText: TextView? = null
    private var secondaryText: TextView? = null
    private var lyrics: LyricsDocument? = null
    private var lyricsSongId: Long? = null
    private var lyricsJob: Job? = null
    private var lastIndex = Int.MIN_VALUE

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            loadLyrics(mediaItem?.mediaId?.toLongOrNull())
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            updateText()
            handler.postDelayed(this, 80L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        createOverlay()
        connectPlayer()
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            MeloXSettingsPreferences.setBoolean(this, "floating_lyrics_enabled", false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connectPlayer() {
        val future = MediaController.Builder(
            this,
            SessionToken(this, ComponentName(this, MeloXPlaybackService::class.java)),
        ).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { player ->
                controller = player
                player.addListener(listener)
                loadLyrics(player.currentMediaItem?.mediaId?.toLongOrNull())
            }
        }, mainExecutor)
    }

    private fun loadLyrics(songId: Long?) {
        if (songId == null || lyricsSongId == songId) return
        lyricsSongId = songId
        lyrics = null
        lastIndex = Int.MIN_VALUE
        primaryText?.text = "正在读取歌词"
        secondaryText?.text = ""
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            val document = withContext(Dispatchers.IO) {
                MeloXDownloadStore.get(this@MeloXFloatingLyricsService).localLyrics(songId)
                    ?: runCatching {
                        NeteaseSearchClient(
                            cookieProvider = { NeteaseSessionStore.readCookie(this@MeloXFloatingLyricsService) },
                        ).lyrics(songId)
                    }.getOrNull()
            }
            if (lyricsSongId == songId) lyrics = document
        }
    }

    private fun updateText() {
        val player = controller ?: return
        val document = lyrics ?: return
        val advance = MeloXSettingsPreferences.int(this, "lyrics_advance_ms", 0).toLong()
        val index = document.highlightedIndex(player.currentPosition + advance) ?: return
        if (index == lastIndex) return
        lastIndex = index
        val line = document.lines.getOrNull(index)
        primaryText?.text = line?.text.orEmpty()
        val mode = runCatching {
            MeloXSecondaryLyricMode.valueOf(
                MeloXSettingsPreferences.string(this, "floating_lyrics_secondary_mode", MeloXSecondaryLyricMode.Auto.name),
            )
        }.getOrDefault(MeloXSecondaryLyricMode.Auto)
        val translation = line?.translation.orEmpty()
        val romanization = line?.romanization.orEmpty()
        val nextLine = document.lines.getOrNull(index + 1)?.text.orEmpty()
        secondaryText?.text = when (mode) {
            MeloXSecondaryLyricMode.Translation -> translation
            MeloXSecondaryLyricMode.Romanization -> romanization
            MeloXSecondaryLyricMode.NextLine -> nextLine
            MeloXSecondaryLyricMode.Hidden -> ""
            MeloXSecondaryLyricMode.Auto -> when {
                MeloXSettingsPreferences.boolean(this, "lyrics_translation", true) && translation.isNotBlank() -> translation
                MeloXSettingsPreferences.boolean(this, "lyrics_romanization", true) && romanization.isNotBlank() -> romanization
                else -> nextLine
            }
        }
    }

    private fun createOverlay() {
        val manager = getSystemService(WindowManager::class.java)
        windowManager = manager
        val density = resources.displayMetrics.density
        val highContrast = MeloXSettingsPreferences.boolean(this, "floating_lyrics_high_contrast", true)
        val fontSize = MeloXSettingsPreferences.int(this, "floating_lyrics_font_size", 18).coerceIn(14, 28)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((18 * density).toInt(), (10 * density).toInt(), (18 * density).toInt(), (10 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (highContrast) Color.argb(205, 12, 12, 16) else Color.argb(125, 18, 18, 22))
                cornerRadius = 22 * density
                setStroke((1 * density).toInt(), Color.argb(75, 255, 255, 255))
            }
        }
        primaryText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = fontSize.toFloat()
            gravity = Gravity.CENTER
            maxLines = 2
            text = "MeloX 悬浮歌词"
        }
        secondaryText = TextView(this).apply {
            setTextColor(Color.argb(150, 255, 255, 255))
            textSize = (fontSize * .68f).coerceAtLeast(10f)
            gravity = Gravity.CENTER
            maxLines = 1
        }
        container.addView(primaryText, LinearLayout.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT))
        container.addView(secondaryText, LinearLayout.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply { topMargin = (3 * density).toInt() })
        val params = WindowManager.LayoutParams(
            (330 * density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (18 * density).toInt()
            y = (110 * density).toInt()
        }
        makeDraggable(container, params)
        manager.addView(container, params)
        overlay = container
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "悬浮歌词", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun buildNotification(): android.app.Notification {
        val open = PendingIntent.getActivity(
            this,
            1801,
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_NOW_PLAYING
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1802,
            Intent(this, MeloXFloatingLyricsService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("MeloX 悬浮歌词")
            .setContentText("正在其他应用上方显示歌词")
            .setContentIntent(open)
            .addAction(0, "停止", stop)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        lyricsJob?.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        overlay?.let { runCatching { windowManager?.removeView(it) } }
        overlay = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "melox_floating_lyrics"
        const val NOTIFICATION_ID = 1800
        const val ACTION_STOP = "com.lladlam.melox.action.STOP_FLOATING_LYRICS"
    }
}
