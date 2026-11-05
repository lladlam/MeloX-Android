package com.lladlam.melox.lan

import android.content.Context
import android.os.Build
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.playback.PlaybackTrackIdentity
import java.security.SecureRandom
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal const val MELOX_LAN_MAGIC = "MELOX_LAN"
internal const val MELOX_LAN_VERSION = 1
internal const val MELOX_LAN_DISCOVERY_PORT = 47830
internal const val MELOX_LAN_DEFAULT_PORT = 47831
internal const val MELOX_LAN_MAX_MESSAGE_BYTES = 256 * 1024
internal const val MELOX_LAN_BARRIER = "together_barrier_v2"

data class MeloXLanPlayback(
    val playing: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Int = 100,
    val trackId: String? = null,
    val source: String? = null,
    val index: Int = 0,
    val queueLength: Int = 0,
    val canNext: Boolean = false,
    val canPrevious: Boolean = false,
    val ready: Boolean = false,
    val ended: Boolean = false,
    val sampleTimeMs: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("playing", playing)
        .put("title", title)
        .put("artist", artist)
        .put("album", album)
        .put("positionMs", positionMs)
        .put("durationMs", durationMs)
        .put("volume", volume)
        .put("index", index)
        .put("queueLength", queueLength)
        .put("canNext", canNext)
        .put("canPrevious", canPrevious)
        .put("ready", ready)
        .put("ended", ended)
        .put("serverTimeMs", sampleTimeMs)
        .apply {
            artworkUrl?.let { put("artworkUrl", it) }
            trackId?.let { put("trackId", it) }
            source?.let { put("source", it) }
        }

    companion object {
        fun fromJson(value: JSONObject?): MeloXLanPlayback {
            value ?: return MeloXLanPlayback()
            return MeloXLanPlayback(
                playing = value.optBoolean("playing"),
                title = value.optString("title"),
                artist = value.optString("artist"),
                album = value.optString("album"),
                artworkUrl = value.optString("artworkUrl").takeIf(String::isNotBlank),
                positionMs = value.optLong("positionMs").coerceAtLeast(0L),
                durationMs = value.optLong("durationMs").coerceAtLeast(0L),
                volume = value.optInt("volume", 100).coerceIn(0, 100),
                trackId = value.optString("trackId").takeIf(String::isNotBlank),
                source = value.optString("source").takeIf(String::isNotBlank),
                index = value.optInt("index"),
                queueLength = value.optInt("queueLength"),
                canNext = value.optBoolean("canNext"),
                canPrevious = value.optBoolean("canPrevious"),
                ready = value.optBoolean("ready"),
                ended = value.optBoolean("ended"),
                sampleTimeMs = value.optLong("serverTimeMs"),
            )
        }
    }
}

data class MeloXLanTrack(
    val source: String,
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("source", source)
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("album", album)
        .put("durationMs", durationMs)
        .apply { artworkUrl?.let { put("artworkUrl", it) } }

    /** Legacy UI model; NetEase only in v1, providers keep their identity. */
    fun toSearchSong(): SearchSong? {
        if (source == "netease") {
            val numericId = id.toLongOrNull()?.takeIf { it > 0L } ?: return null
            return SearchSong(
                id = numericId,
                name = title.ifBlank { "未知歌曲" },
                artists = artist.ifBlank { "未知歌手" },
                album = album,
                artworkUrl = artworkUrl,
                durationMs = durationMs,
            )
        }
        return null
    }

    companion object {
        fun fromJson(value: JSONObject?): MeloXLanTrack? {
            value ?: return null
            val source = value.optString("source").takeIf(String::isNotBlank) ?: return null
            val id = value.optString("id").takeIf(String::isNotBlank) ?: return null
            return MeloXLanTrack(
                source = source,
                id = id,
                title = value.optString("title"),
                artist = value.optString("artist"),
                album = value.optString("album"),
                artworkUrl = value.optString("artworkUrl").takeIf(String::isNotBlank),
                durationMs = value.optLong("durationMs").coerceAtLeast(0L),
            )
        }

        fun fromJsonArray(value: JSONArray?): List<MeloXLanTrack> {
            value ?: return emptyList()
            return buildList {
                for (index in 0 until value.length()) {
                    fromJson(value.optJSONObject(index))?.let(::add)
                }
            }
        }
    }
}

internal fun meloXLanTrackFromMediaItem(
    mediaId: String,
    title: String,
    artist: String,
    album: String,
    artworkUrl: String?,
    durationMs: Long,
): MeloXLanTrack? {
    val identity = PlaybackTrackIdentity.decode(mediaId) ?: return null
    return MeloXLanTrack(
        source = identity.sourceId.value,
        id = identity.value,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
    )
}

data class MeloXLanCastFormat(
    val codec: String = "pcm_s16le",
    val sampleRate: Int = 48_000,
    val channels: Int = 2,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("codec", codec)
        .put("sampleRate", sampleRate)
        .put("channels", channels)

    companion object {
        fun fromJson(value: JSONObject?): MeloXLanCastFormat {
            value ?: return MeloXLanCastFormat()
            return MeloXLanCastFormat(
                codec = value.optString("codec", "pcm_s16le"),
                sampleRate = value.optInt("sampleRate", 48_000).coerceIn(8_000, 192_000),
                channels = value.optInt("channels", 2).coerceIn(1, 2),
            )
        }
    }
}

data class MeloXLanCastHandshake(
    val port: Int,
    val format: MeloXLanCastFormat,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("castPort", port)
        .put("format", format.toJson())
}

/**
 * Temporary host-side audio proxy handed to a Together guest. The guest never
 * receives the host's account cookie; it only gets this opaque token.
 */
data class MeloXLanRelay(
    val baseUrl: String,
    val token: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("baseUrl", baseUrl)
        .put("token", token)

    companion object {
        fun fromJson(value: JSONObject?): MeloXLanRelay? {
            value ?: return null
            val baseUrl = value.optString("baseUrl").takeIf(String::isNotBlank) ?: return null
            val token = value.optString("token").takeIf(String::isNotBlank) ?: return null
            return MeloXLanRelay(baseUrl, token)
        }
    }
}

internal data class MeloXLanDevice(
    val deviceId: String,
    val name: String,
    val platform: String,
    val model: String = "",
    val appVersion: String = "",
    val address: String,
    val port: Int,
    val pinRequired: Boolean = true,
    val trusted: Boolean = false,
    val capabilities: Set<String> = emptySet(),
    val playback: MeloXLanPlayback = MeloXLanPlayback(),
) {
    fun toJson(includeAddress: Boolean = true): JSONObject = JSONObject()
        .put("magic", MELOX_LAN_MAGIC)
        .put("v", MELOX_LAN_VERSION)
        .put("type", "announce")
        .put("deviceId", deviceId)
        .put("name", name)
        .put("platform", platform)
        .put("model", model)
        .put("appVersion", appVersion)
        .put("port", port)
        .put("pinRequired", pinRequired)
        .put("trusted", trusted)
        .put("capabilities", meloXJsonStringArray(capabilities.toList()))
        .put("state", playback.toJson())
        .apply { if (includeAddress) put("address", address) }
}

internal fun meloXJsonStringArray(values: Collection<String>): JSONArray {
    val array = JSONArray()
    values.forEach(array::put)
    return array
}

/**
 * Persisted discovery/trust state. Kept in a dedicated preferences file so
 * clearing app settings does not silently break existing trusted devices.
 */
internal class MeloXLanPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("melox_lan", Context.MODE_PRIVATE)

    val appVersion: String
        get() = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")

    val deviceId: String
        get() = prefs.getString("device_id", null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }

    val deviceName: String
        get() = prefs.getString("device_name", null)
            ?: "${Build.MODEL} · MeloX".also { prefs.edit().putString("device_name", it).apply() }

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var port: Int
        get() = prefs.getInt("port", MELOX_LAN_DEFAULT_PORT).coerceIn(1024, 65535)
        set(value) = prefs.edit().putInt("port", value.coerceIn(1024, 65535)).apply()

    val pin: String
        get() = prefs.getString("pin", null) ?: newPin()
            .also { prefs.edit().putString("pin", it).apply() }

    fun regeneratePin(): String = newPin()
        .also { prefs.edit().putString("pin", it).apply() }

    private fun newPin(): String = "%04d".format(1000 + SecureRandom().nextInt(9000))

    fun isTrusted(deviceId: String): Boolean =
        trustedDevices().any { it.optString("deviceId") == deviceId }

    fun trustedDevices(): List<JSONObject> {
        val raw = prefs.getString("trusted_devices", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }.getOrDefault(emptyList())
    }

    fun recentPeers(): List<JSONObject> {
        val raw = prefs.getString("recent_peers", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }.getOrDefault(emptyList())
    }

    fun rememberPeer(deviceId: String, name: String, platform: String, address: String) {
        if (deviceId.isBlank()) return
        val peers = recentPeers().filterNot { it.optString("deviceId") == deviceId }.toMutableList()
        peers += JSONObject()
            .put("deviceId", deviceId)
            .put("name", name)
            .put("platform", platform)
            .put("address", address)
            .put("lastSeen", System.currentTimeMillis())
        val array = JSONArray()
        peers.takeLast(10).forEach(array::put)
        prefs.edit().putString("recent_peers", array.toString()).apply()
    }

    fun addTrusted(deviceId: String, name: String, platform: String) {
        if (deviceId.isBlank()) return
        val devices = trustedDevices().filterNot { it.optString("deviceId") == deviceId }.toMutableList()
        devices += JSONObject()
            .put("deviceId", deviceId)
            .put("name", name)
            .put("platform", platform)
        val array = JSONArray()
        devices.forEach(array::put)
        prefs.edit().putString("trusted_devices", array.toString()).apply()
    }

    fun removeTrusted(deviceId: String) {
        val array = JSONArray()
        trustedDevices().filterNot { it.optString("deviceId") == deviceId }.forEach(array::put)
        prefs.edit().putString("trusted_devices", array.toString()).apply()
    }
}
