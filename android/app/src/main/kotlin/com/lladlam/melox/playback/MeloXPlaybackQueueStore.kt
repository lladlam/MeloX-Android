package com.lladlam.melox.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

internal data class MeloXPersistedQueue(
    val items: List<MediaItem>,
    val index: Int,
    val positionMs: Long,
)

internal object MeloXPlaybackQueueStore {
    private const val PREFS = "melox_playback_queue"
    private const val QUEUE = "queue"
    private const val INDEX = "index"
    private const val POSITION = "position"

    fun save(context: Context, player: androidx.media3.common.Player) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (player.mediaItemCount == 0) {
            preferences.edit().remove(QUEUE).remove(INDEX).remove(POSITION).commit()
            return
        }
        val array = JSONArray()
        repeat(player.mediaItemCount) { index ->
            val item = player.getMediaItemAt(index)
            val metadata = item.mediaMetadata
            val extras = metadata.extras
            array.put(JSONObject().apply {
                put("id", item.mediaId)
                put("uri", item.localConfiguration?.uri?.toString())
                put("title", metadata.title?.toString().orEmpty())
                put("artist", metadata.artist?.toString().orEmpty())
                put("album", metadata.albumTitle?.toString().orEmpty())
                put("artwork", metadata.artworkUri?.toString())
                put("origin", extras?.getString(PlaybackCommands.QUEUE_ORIGIN_KEY))
                put("originalIndex", extras?.getInt(PlaybackCommands.QUEUE_ORIGINAL_INDEX_KEY, -1) ?: -1)
                put("entryId", extras?.getString(PlaybackCommands.QUEUE_ENTRY_ID_KEY))
                put("durationMs", extras?.getLong(PlaybackTrackIdentity.DurationMsExtra, 0L) ?: 0L)
            })
        }
        preferences.edit()
            .putString(QUEUE, array.toString())
            .putInt(INDEX, player.currentMediaItemIndex.coerceAtLeast(0))
            .putLong(POSITION, player.currentPosition.coerceAtLeast(0L))
            .commit()
    }

    fun read(context: Context): MeloXPersistedQueue? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(QUEUE, null) ?: return null
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        val items = buildList {
            for (index in 0 until array.length()) {
                val value = array.optJSONObject(index) ?: continue
                val id = value.optString("id").takeIf(String::isNotBlank) ?: continue
                val extras = Bundle().apply {
                    value.optString("origin").takeIf(String::isNotBlank)?.let {
                        putString(PlaybackCommands.QUEUE_ORIGIN_KEY, it)
                    }
                    putInt(
                        PlaybackCommands.QUEUE_ORIGINAL_INDEX_KEY,
                        value.optInt("originalIndex", -1),
                    )
                    value.optString("entryId").takeIf(String::isNotBlank)?.let {
                        putString(PlaybackCommands.QUEUE_ENTRY_ID_KEY, it)
                    }
                    putLong(PlaybackTrackIdentity.DurationMsExtra, value.optLong("durationMs", 0L))
                }
                add(
                    MediaItem.Builder()
                        .setMediaId(id)
                        .apply { value.optString("uri").takeIf(String::isNotBlank)?.let { setUri(Uri.parse(it)) } }
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(value.optString("title"))
                                .setArtist(value.optString("artist"))
                                .setAlbumTitle(value.optString("album"))
                                .apply { value.optString("artwork").takeIf(String::isNotBlank)?.let { setArtworkUri(Uri.parse(it)) } }
                                .setExtras(extras)
                                .build(),
                        )
                        .build(),
                )
            }
        }
        return items.takeIf { it.isNotEmpty() }?.let {
            MeloXPersistedQueue(
                items = it,
                index = prefs.getInt(INDEX, 0).coerceIn(it.indices),
                positionMs = prefs.getLong(POSITION, 0L).coerceAtLeast(0L),
            )
        }
    }
}
