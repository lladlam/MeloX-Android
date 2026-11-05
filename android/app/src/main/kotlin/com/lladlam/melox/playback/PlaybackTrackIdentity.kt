package com.lladlam.melox.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.SourceId
import com.lladlam.melox.core.music.model.SourceResourceUri

/**
 * Media3 IDs used by new providers. A legacy numeric ID is intentionally parsed
 * as NetEase so existing queues and every iOS-migrated NetEase path keep working.
 */
object PlaybackTrackIdentity {
    private const val Prefix = "melox:"
    const val SourceExtra = "melox.provider.source"
    const val ResourceIdExtra = "melox.provider.resource_id"
    const val DurationMsExtra = "melox.track.duration_ms"
    const val TitleExtra = "melox.track.title"
    const val ArtistExtra = "melox.track.artist"
    const val AlbumExtra = "melox.track.album"
    const val ArtworkExtra = "melox.track.artwork"
    const val IsFavoriteExtra = "melox.track.is_favorite"
    const val TrackJsonExtra = "melox.track.standard_json"

    internal fun restoredTrack(item: MediaItem): com.lladlam.melox.core.music.model.MusicTrack? {
        val identity = fromMediaItem(item) ?: return null
        val raw = item.mediaMetadata.extras?.getString(TrackJsonExtra) ?: return null
        return runCatching {
            com.lladlam.melox.core.plugin.PluginJson.check(raw)
            com.lladlam.melox.core.music.model.MeloXTrackJson.decode(org.json.JSONObject(raw))?.takeIf { it.id == identity }
        }.getOrNull()
    }

    fun encode(id: MusicResourceId): String = if (id.sourceId.builtIn == null)
        SourceResourceUri.encode(id.sourceId, id.value)
    else "$Prefix${id.sourceId.value}:${Uri.encode(id.value)}"

    fun decode(mediaId: String): MusicResourceId? {
        SourceResourceUri.decode(mediaId)?.let { return MusicResourceId(it.first, it.second) }
        mediaId.toLongOrNull()?.takeIf { it > 0L }?.let {
            return MusicResourceId(MusicSource.Netease, it.toString())
        }
        if (!mediaId.startsWith(Prefix)) return null
        val payload = mediaId.removePrefix(Prefix)
        val separator = payload.indexOf(':')
        if (separator <= 0 || separator >= payload.lastIndex) return null
        val source = MusicSource.entries.firstOrNull {
            it.storageValue == payload.substring(0, separator)
        } ?: return null
        val value = percentDecode(payload.substring(separator + 1)).trim()
        return value.takeIf(String::isNotBlank)?.let { MusicResourceId(source, it) }
    }


    /** JVM-safe percent decoding; keeps '+' literal like Android's Uri.decode. */
    private fun percentDecode(value: String): String {
        if ('%' !in value) return value
        val out = java.io.ByteArrayOutputStream(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%' && index + 2 < value.length) {
                val byte = value.substring(index + 1, index + 3).toIntOrNull(16)
                if (byte != null) {
                    out.write(byte)
                    index += 3
                    continue
                }
            }
            val bytes = char.toString().toByteArray(Charsets.UTF_8)
            out.write(bytes, 0, bytes.size)
            index++
        }
        return out.toString(Charsets.UTF_8.name())
    }

    fun fromMediaItem(item: MediaItem?): MusicResourceId? {
        item ?: return null
        val extras = item.mediaMetadata.extras
        val sourceValue = extras?.getString(SourceExtra)
        val resourceId = extras?.getString(ResourceIdExtra)
        return decodeWithExtras(item.mediaId, sourceValue, resourceId)
    }

    internal fun decodeWithExtras(mediaId: String, sourceValue: String?, resourceId: String?): MusicResourceId? {
        if (!sourceValue.isNullOrBlank()) {
            // Explicit source is authoritative even if the extras are malformed.
            // A numeric mediaId must not turn an unknown provider into NetEase.
            val source = SourceId.parse(sourceValue) ?: return null
            return resourceId?.takeIf(String::isNotBlank)?.let { MusicResourceId(source, it) }
        }
        return decode(mediaId)
    }

    fun neteaseNumericId(item: MediaItem?): Long? =
        fromMediaItem(item)
            ?.takeIf { it.sourceId.builtIn == MusicSource.Netease }
            ?.value
            ?.toLongOrNull()
}
