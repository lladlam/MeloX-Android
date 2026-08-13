package com.lladlam.melox.platform.xiaomi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.provider.Settings
import com.xzakota.hyper.notification.focus.FocusNotification
import com.xzakota.hyper.notification.island.model.TextInfo
import org.json.JSONObject

/**
 * Xiaomi HyperOS Focus / Super Island integration boundary.
 *
 * The Focus V3 notification shape is independently adapted for MeloX from the
 * Apache-2.0 Halcyon implementation referenced during the HyperOS lyric port.
 * Normal playback remains owned by Media3; this object only builds the vendor
 * notification surface.
 */
object HyperOsFocusBridge {
    const val ChannelId = "melox_hyperos_super_island_lyrics_v1"
    const val NotificationId = 0x4d584953
    const val MinimumRenderIntervalMs = 1_500L

    private const val FocusProtocolSetting = "notification_focus_protocol"
    private const val LegacyFocusParamKey = "miui.focus.param"
    private const val DefaultAccent = 0xFF3482FF.toInt()

    enum class Protocol(val version: Int) {
        Unsupported(0),
        HyperOs1(1),
        HyperOs2(2),
        HyperOs3(3),
    }

    fun protocol(context: Context): Protocol {
        val version = runCatching {
            Settings.System.getInt(context.contentResolver, FocusProtocolSetting, 0)
        }.getOrDefault(0)
        return Protocol.entries.firstOrNull { it.version == version }
            ?: Protocol.Unsupported
    }

    fun supportsSuperIsland(context: Context): Boolean =
        protocol(context) == Protocol.HyperOs3

    /**
     * Compatibility path for the existing generic lyrics notification on HyperOS 1/2.
     * HyperOS 3 returns null here because the dedicated Focus V3 foreground service owns
     * Super Island updates and must not be duplicated by the old ad-hoc payload.
     */
    fun playbackPayload(
        context: Context,
        lyric: String,
        songTitle: String,
        artist: String,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
    ): String? {
        val protocol = protocol(context)
        if (protocol == Protocol.Unsupported || protocol == Protocol.HyperOs3) return null
        return JSONObject().apply {
            put("protocolVersion", protocol.version)
            put("scene", "music")
            put("source", "MeloX")
            put("title", lyric)
            put("song", songTitle)
            put("artist", artist)
            put("position", positionMs.coerceAtLeast(0L))
            put("duration", durationMs.coerceAtLeast(0L))
            put("playing", isPlaying)
        }.toString()
    }

    fun attachFocusParams(
        notification: Notification,
        islandParamsJson: String,
    ): Notification = notification.apply {
        extras.putString(LegacyFocusParamKey, islandParamsJson)
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(ChannelId) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                ChannelId,
                "HyperOS 岛歌词",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "在 HyperOS 超级岛显示当前播放歌词"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    fun warmNotification(
        context: Context,
        contentIntent: PendingIntent,
    ): Notification {
        ensureChannel(context)
        return Notification.Builder(context, ChannelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("MeloX 岛歌词")
            .setContentText("等待播放歌词")
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setLocalOnly(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    fun lyricNotification(
        context: Context,
        contentIntent: PendingIntent,
        lyric: String,
        nextLine: String,
        songTitle: String,
        artist: String,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
    ): Notification? {
        if (!supportsSuperIsland(context)) return null
        ensureChannel(context)

        val fullLyric = lyric.trim().ifBlank { "♪" }
        val trackLabel = listOf(songTitle.trim(), artist.trim())
            .filter(String::isNotBlank)
            .joinToString(" - ")
            .ifBlank { "MeloX" }
        val progress = if (durationMs > 0L) {
            ((positionMs.coerceIn(0L, durationMs) * 100L) / durationMs)
                .toInt()
                .coerceIn(0, 100)
        } else {
            0
        }
        val split = XiaomiSuperIslandLyricLayout.splitFullLyric(fullLyric)
        val accent = String.format("#FF%06X", DefaultAccent and 0xFFFFFF)

        val extras = FocusNotification.buildV3 {
            business = "lyric_display"
            isShowNotification = true
            enableFloat = false
            updatable = true
            islandFirstFloat = false
            aodTitle = fullLyric.take(20)
            ticker = fullLyric

            chatInfo {
                title = fullLyric
                content = trackLabel
                appIconPkg = context.packageName
            }

            progressInfo {
                this.progress = progress
                colorProgress = accent
                colorProgressEnd = accent
            }

            island {
                islandProperty = 1
                highlightColor = accent
                bigIslandArea {
                    imageTextInfoLeft {
                        type = 1
                        textInfo {
                            title = split.left.ifBlank { trackLabel }
                            showHighlightColor = false
                            narrowFont = false
                        }
                    }
                    textInfo = TextInfo().apply {
                        title = split.right.ifBlank { fullLyric }
                        showHighlightColor = true
                        narrowFont = false
                    }
                }
                shareData {
                    title = songTitle.ifBlank { "MeloX" }
                    content = artist.ifBlank { nextLine.ifBlank { fullLyric } }
                    shareContent = buildString {
                        append(fullLyric)
                        if (songTitle.isNotBlank()) append('\n').append(songTitle)
                        if (artist.isNotBlank()) append(" - ").append(artist)
                    }
                }
                smallIslandArea {
                    combinePicInfo {
                        progressInfo {
                            this.progress = progress
                            colorReach = accent
                            colorUnReach = "#333333"
                        }
                    }
                }
            }
        }

        return Notification.Builder(context, ChannelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(fullLyric)
            .setContentText(trackLabel)
            .setSubText(context.packageName)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setAutoCancel(false)
            .setLocalOnly(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(DefaultAccent)
            .addExtras(extras)
            .build()
    }
}
