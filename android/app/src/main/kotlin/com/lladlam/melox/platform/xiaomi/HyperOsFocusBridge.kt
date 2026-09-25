package com.lladlam.melox.platform.xiaomi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.xzakota.hyper.notification.focus.FocusNotification
import com.xzakota.hyper.notification.island.model.BigIslandArea
import com.xzakota.hyper.notification.island.model.TextInfo

/**
 * Xiaomi HyperOS Focus / Super Island lyric integration.
 *
 * The Focus V3 payload shape is adapted from Kifranei/Halcyon (Apache-2.0),
 * then reduced to MeloX's provider-neutral playback metadata and lyric stream.
 * A dedicated HIGH-importance, silent notification channel is intentionally used:
 * Android cannot promote the existing LOW lyric channel after it has been created,
 * while HyperOS requires a Focus-capable channel for Super Island rendering.
 */
object HyperOsFocusBridge {
    private const val FOCUS_PROTOCOL_SETTING = "notification_focus_protocol"
    private const val SUPER_ISLAND_CHANNEL = "melox_super_island_lyrics_v1"

    @Volatile
    private var lastPublishedKey: String? = null

    enum class Protocol(val version: Int) {
        Unsupported(0), HyperOs1(1), HyperOs2(2), HyperOs3(3),
    }

    fun protocol(context: Context): Protocol {
        val version = runCatching {
            Settings.System.getInt(context.contentResolver, FOCUS_PROTOCOL_SETTING, 0)
        }.getOrDefault(0)
        return Protocol.entries.firstOrNull { it.version == version } ?: Protocol.Unsupported
    }

    fun supportsSuperIsland(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
            protocol(context) == Protocol.HyperOs3

    /**
     * Ask Shizuku for access only when HyperOS 3 and a live Shizuku service are present.
     * Missing Shizuku, a denied request, or an incompatible firewall backend simply leaves
     * the already-working direct Focus path untouched.
     */
    fun prepareShizukuCompatibility(context: Context) {
        if (!supportsSuperIsland(context)) return
        ShizukuXmsfNetworkHelper.prepare(context.applicationContext)
    }

    /**
     * Publishes the dedicated Focus V3 notification as a side effect.
     *
     * The return type stays Bundle-compatible with the legacy call site, but this method returns
     * null after publishing so the same Focus extras are not attached a second time to MeloX's
     * ordinary LOW-priority lyric notification.
     */
    fun playbackPayload(
        context: Context,
        lyric: String,
        songTitle: String,
        artist: String,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
    ): Bundle? {
        if (!supportsSuperIsland(context)) return null

        val text = lyric.trim()
        if (text.isBlank()) {
            clearSuperIsland(context)
            return null
        }

        val progress = if (durationMs > 0L) {
            ((positionMs.coerceIn(0L, durationMs) * 100L) / durationMs)
                .toInt()
                .coerceIn(0, 100)
        } else {
            0
        }
        val key = listOf(songTitle, artist, text, progress / 2, isPlaying).joinToString("|")
        if (key == lastPublishedKey) return null
        lastPublishedKey = key

        val appContext = context.applicationContext
        ensureSuperIslandChannel(appContext)

        val focusExtras = FocusNotification.buildV3 {
            business = "lyric_display"
            isShowNotification = true
            enableFloat = false
            updatable = true
            islandFirstFloat = false
            aodTitle = text.take(20).ifBlank { "♪" }
            ticker = text

            chatInfo {
                title = text
                content = songLabel(songTitle, artist)
                appIconPkg = appContext.packageName
            }

            progressInfo {
                this.progress = progress
                colorProgress = "#FF757575"
                colorProgressEnd = "#FF757575"
            }

            island {
                islandProperty = 1
                bigIslandArea {
                    applyMeloXLyric(
                        lyric = text,
                        songTitle = songTitle,
                        artist = artist,
                    )
                }
                smallIslandArea {
                    combinePicInfo {
                        progressInfo {
                            this.progress = progress
                            colorReach = "#FF757575"
                            colorUnReach = "#333333"
                        }
                    }
                }
            }
        }

        val notification = Notification.Builder(appContext, SUPER_ISLAND_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(text)
            .setContentText(songLabel(songTitle, artist))
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setOngoing(isPlaying)
            .setTimeoutAfter(if (isPlaying) 15_000L else 5_000L)
            .addExtras(focusExtras)
            .build()

        ShizukuXmsfNetworkHelper.dispatchFocusNotification(
            context = appContext,
            notification = notification,
        )
        return null
    }

    /** Kept for source compatibility with the previous bridge contract. */
    fun attachFocusParams(notification: Notification, focusExtras: Bundle): Notification =
        notification.apply { extras.putAll(focusExtras) }

    fun clearSuperIsland(context: Context) {
        lastPublishedKey = null
        ShizukuXmsfNetworkHelper.clearFocusNotification(context.applicationContext)
    }

    /** Settings-only authorization. Playback never calls this. */
    fun requestShizukuPermission(context: Context) {
        if (!supportsSuperIsland(context)) return
        ShizukuXmsfNetworkHelper.requestPermission(context.applicationContext)
    }

    private fun ensureSuperIslandChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(SUPER_ISLAND_CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                SUPER_ISLAND_CHANNEL,
                "HyperOS 岛歌词",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "在 HyperOS 超级岛显示当前歌词"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    private fun BigIslandArea.applyMeloXLyric(
        lyric: String,
        songTitle: String,
        artist: String,
    ) {
        val left = songLabel(songTitle, artist).ifBlank { "MeloX" }.take(24)
        val right = lyric.take(42).ifBlank { "♪" }
        imageTextInfoLeft {
            type = 1
            textInfo {
                title = left
                showHighlightColor = false
                narrowFont = false
            }
        }
        textInfo = TextInfo().apply {
            title = right
            showHighlightColor = false
            narrowFont = false
        }
    }

    private fun songLabel(songTitle: String, artist: String): String =
        listOf(songTitle.trim(), artist.trim())
            .filter(String::isNotBlank)
            .joinToString(" · ")
            .ifBlank { "MeloX" }
}
