package com.lladlam.melox.platform.vivo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * Vivo OriginOS Atomic Island integration.
 *
 * This is intentionally not user-configurable: on vivo devices MeloX always attempts the
 * vendor notification path and keeps the normal Android notification as the fallback.
 */
object VivoAtomicIslandBridge {
    private const val CHANNEL_ID = "melox_vivo_atomic_island"
    private const val NOTIFICATION_ID = 1704
    private const val MIN_UPDATE_INTERVAL_MS = 10_000L
    private const val MAX_DURATION_MS = 8 * 60 * 60 * 1_000L

    private const val OPERATION = "notification.superx.operation"
    private const val SHOW_NOTIFY = "notification.superx.showNotify"
    private const val TEMPLATE = "notification.superx.template"
    private const val CLICK_RESP = "notification.superx.clickResp"
    private const val SCENE = "notification.superx.scene"
    private const val BASE_INFOS = "notification.superx.baseInfos"
    private const val BASE_ICON = "notification.superx.baseInfos.icon"
    private const val BASE_TITLE = "notification.superx.baseInfos.title"
    private const val BASE_CONTENT = "notification.superx.baseInfos.content"

    private var activeSongKey: String? = null
    private var lastPublishedAt = 0L

    fun isSupported(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        return manufacturer.contains("vivo") || brand.contains("vivo")
    }

    fun publish(
        context: Context,
        line: String,
        songTitle: String,
        artist: String,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        clickIntent: PendingIntent,
    ) {
        if (!isSupported()) return
        val appContext = context.applicationContext
        val songKey = "$songTitle\u0000$artist"
        val now = SystemClock.elapsedRealtime()
        synchronized(this) {
            if (songKey == activeSongKey && now - lastPublishedAt < MIN_UPDATE_INTERVAL_MS) return
            val operation = if (songKey == activeSongKey) 1 else 0
            publishNotification(
                context = appContext,
                operation = operation,
                line = line,
                songTitle = songTitle,
                artist = artist,
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                clickIntent = clickIntent,
            )
            activeSongKey = songKey
            lastPublishedAt = now
        }
    }

    fun clear(context: Context) {
        if (!isSupported()) return
        synchronized(this) {
            if (activeSongKey == null) return
            publishNotification(
                context = context.applicationContext,
                operation = 2,
                line = "",
                songTitle = "",
                artist = "",
                positionMs = 0L,
                durationMs = 0L,
                isPlaying = false,
                clickIntent = null,
            )
            activeSongKey = null
            lastPublishedAt = 0L
        }
    }

    private fun publishNotification(
        context: Context,
        operation: Int,
        line: String,
        songTitle: String,
        artist: String,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        clickIntent: PendingIntent?,
    ) {
        ensureChannel(context)
        val title = songTitle.trim().ifBlank { "MeloX" }
        val content = artist.trim().ifBlank { line.trim().ifBlank { "♪" } }
        val progress = if (durationMs > 0L) {
            ((positionMs.coerceIn(0L, durationMs) * 100L) / durationMs).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val extras = Bundle().apply {
            putInt(OPERATION, operation)
            putBoolean(SHOW_NOTIFY, true)
            putInt(TEMPLATE, 2)
            putString(SCENE, "MUSIC_PLAYBACK")
            clickIntent?.let { putParcelable(CLICK_RESP, it) }
            putBundle(BASE_INFOS, Bundle().apply {
                putParcelable(BASE_ICON, Icon.createWithResource(context, android.R.drawable.ic_media_play))
                putCharSequence(BASE_TITLE, title)
                putCharSequence(BASE_CONTENT, content)
            })
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(content)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(operation != 2 && isPlaying)
            .setLocalOnly(true)
            .setTimeoutAfter(if (operation == 2) 5_000L else MAX_DURATION_MS)
            .setExtras(extras)
        if (durationMs > 0L) builder.setProgress(100, progress, false)
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, builder.build())
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "vivo 原子岛",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "在 vivo 原子岛显示当前播放状态"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }
}
