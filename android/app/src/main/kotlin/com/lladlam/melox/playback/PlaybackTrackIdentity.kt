package com.lladlam.melox.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource

/**
 * Media3 IDs used by new providers. A legacy numeric ID is intentionally parsed
 * as NetEase so existing queues and every iOS-migrated NetEase path keep working.
 */
object PlaybackTrackIdentity {
    private const val Prefix = "melox:"
    const val SourceExtra = "melox.provider.source"
    const val ResourceIdExtra = "melox.provider.resource_id"
    const val DurationMsExtra = "melox.track.duration_ms"

    fun encode(id: MusicResourceId): String =
        "$Prefix${id.source.storageValue}:${Uri.encode(id.value)}"

    fun decode(mediaId: String): MusicResourceId? {
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
        val value = Uri.decode(payload.substring(separator + 1)).trim()
        return value.takeIf(String::isNotBlank)?.let { MusicResourceId(source, it) }
    }

    fun fromMediaItem(item: MediaItem?): MusicResourceId? {
        item ?: return null
        val extras = item.mediaMetadata.extras
        val sourceValue = extras?.getString(SourceExtra)
        val resourceId = extras?.getString(ResourceIdExtra)
        if (!sourceValue.isNullOrBlank() && !resourceId.isNullOrBlank()) {
            val source = MusicSource.entries.firstOrNull { it.storageValue == sourceValue }
            if (source != null) return MusicResourceId(source, resourceId)
        }
        return decode(item.mediaId)
    }

    fun neteaseNumericId(item: MediaItem?): Long? =
        fromMediaItem(item)
            ?.takeIf { it.source == MusicSource.Netease }
            ?.value
            ?.toLongOrNull()
}
