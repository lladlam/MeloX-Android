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
    private const val CURRENT_ID = "current_id"

    fun save(context: Context, player: androidx.media3.common.Player, flush: Boolean = false) {
        // 上游 0.6.2（8b8cf70）：冷启动 restore 流程跑完之前会观察到「空 player」，
        // 此时清库会把用户队列永久删掉 ⇒ 空队列直接返回，不再 remove。
        if (player.mediaItemCount == 0) return
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
                put("standardTrack", extras?.getString(PlaybackTrackIdentity.TrackJsonExtra))
                put("source", extras?.getString(PlaybackTrackIdentity.SourceExtra))
                put("resourceId", extras?.getString(PlaybackTrackIdentity.ResourceIdExtra))
            })
        }
        val currentIndex = player.currentMediaItemIndex.coerceIn(0, player.mediaItemCount - 1)
        val editor = preferences.edit()
            .putString(QUEUE, array.toString())
            .putInt(INDEX, currentIndex)
            .putLong(POSITION, player.currentPosition.coerceAtLeast(0L))
            .putString(CURRENT_ID, player.getMediaItemAt(currentIndex).mediaId)
        if (flush) editor.commit() else editor.apply()
    }

    /**
     * Cheap partial write for the resume position. Re-serialising the whole
     * queue (which can be hundreds of KB) on a timer would jank the main
     * thread, so a crash only costs the small window since the last full save.
     */
    fun savePosition(context: Context, index: Int, positionMs: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(INDEX, index.coerceAtLeast(0))
            .putLong(POSITION, positionMs.coerceAtLeast(0L))
            // This is called by the two-second playback monitor. Full queue saves
            // still support a synchronous flush during teardown.
            .apply()
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
                    value.optString("standardTrack").takeIf(String::isNotBlank)?.let { putString(PlaybackTrackIdentity.TrackJsonExtra, it) }
                    value.optString("source").takeIf(String::isNotBlank)?.let { putString(PlaybackTrackIdentity.SourceExtra, it) }
                    value.optString("resourceId").takeIf(String::isNotBlank)?.let { putString(PlaybackTrackIdentity.ResourceIdExtra, it) }
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
            // 按 mediaId 锚定当前索引：持久化后若条目被过滤/截断，旧存的数值 index
            // 会被 `coerceIn` 压到末尾 ⇒ 恢复到错误的歌。有 id 时用 id 反查，
            // 找不到再退回数值 index。
            val numericIndex = prefs.getInt(INDEX, 0).coerceIn(it.indices)
            val currentId = prefs.getString(CURRENT_ID, null)
            val index = if (currentId != null) {
                it.indexOfFirst { item -> item.mediaId == currentId }.takeIf { idx -> idx >= 0 } ?: numericIndex
            } else numericIndex
            MeloXPersistedQueue(
                items = it,
                index = index,
                positionMs = prefs.getLong(POSITION, 0L).coerceAtLeast(0L),
            )
        }
    }
}
