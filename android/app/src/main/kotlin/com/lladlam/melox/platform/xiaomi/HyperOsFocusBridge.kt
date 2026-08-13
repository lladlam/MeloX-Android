package com.lladlam.melox.platform.xiaomi

import android.app.Notification
import android.content.Context
import android.os.Build
import android.provider.Settings
import org.json.JSONObject

/**
 * Lightweight Xiaomi integration boundary loaded by MeloX's ordinary playback service.
 *
 * This class intentionally contains no focus-api references. HyperOS 3 Focus V3 creation is
 * isolated in [HyperOsFocusV3NotificationFactory], which is only entered after protocol 3 is
 * detected. That keeps MeloX's existing API 26 compatibility path independent of the vendor SDK.
 */
object HyperOsFocusBridge {
    private const val FocusProtocolSetting = "notification_focus_protocol"
    private const val LegacyFocusParamKey = "miui.focus.param"

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
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
            protocol(context) == Protocol.HyperOs3

    /**
     * Compatibility path for the existing generic lyrics notification on HyperOS 1/2.
     * HyperOS 3 returns null because its dedicated Focus V3 foreground service owns the island.
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
}
