package com.lladlam.melox.platform.xiaomi

import android.app.Notification
import android.content.Context
import android.provider.Settings
import org.json.JSONObject

/**
 * Xiaomi HyperOS integration boundary.
 *
 * Normal music playback should continue to use Android MediaSession/Media3.
 * This bridge is reserved for HyperOS focus notifications / Super Island
 * scenarios that do not map cleanly to the standard media notification.
 */
object HyperOsFocusBridge {
    private const val FOCUS_PROTOCOL_SETTING = "notification_focus_protocol"
    private const val FOCUS_PARAM_KEY = "miui.focus.param"

    enum class Protocol(val version: Int) {
        Unsupported(0),
        HyperOs1(1),
        HyperOs2(2),
        HyperOs3(3),
    }

    fun protocol(context: Context): Protocol {
        val version = runCatching {
            Settings.System.getInt(context.contentResolver, FOCUS_PROTOCOL_SETTING, 0)
        }.getOrDefault(0)
        return Protocol.entries.firstOrNull { it.version == version }
            ?: Protocol.Unsupported
    }

    fun supportsSuperIsland(context: Context): Boolean =
        protocol(context) == Protocol.HyperOs3

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
        if (protocol == Protocol.Unsupported) return null
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
            if (protocol == Protocol.HyperOs3) put("superIsland", true)
        }.toString()
    }

    /**
     * Attaches Xiaomi's documented focus-notification payload to an already
     * built Android notification. Callers remain responsible for Xiaomi's
     * scene approval/permission requirements and for providing a valid JSON
     * payload for the target HyperOS protocol.
     */
    fun attachFocusParams(
        notification: Notification,
        islandParamsJson: String,
    ): Notification = notification.apply {
        extras.putString(FOCUS_PARAM_KEY, islandParamsJson)
    }
}
