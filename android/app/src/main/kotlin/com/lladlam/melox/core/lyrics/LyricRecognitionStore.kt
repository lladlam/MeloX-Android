package com.lladlam.melox.core.lyrics

import android.content.Context
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.model.MusicResourceId
import org.json.JSONObject

/** A song-recognition result persisted for a playback resource. */
data class RecognizedLyricTrack(
    val neteaseId: Long,
    val title: String,
    val artists: String,
    val album: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val savedAtEpochMs: Long = System.currentTimeMillis(),
) {
    fun toSearchSong(): SearchSong = SearchSong(
        id = neteaseId,
        name = title,
        artists = artists,
        album = album,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
    )
}

/**
 * Persistent cache for the last successful song-recognition identity of a
 * playback resource. Keeping this on disk means the same Bilibili video (or
 * any other provider item) does not need to download/decode and fingerprint
 * audio again after a process restart.
 */
object LyricRecognitionStore {
    private const val PreferencesName = "melox_lyric_recognition_ids"

    fun read(context: Context, playbackId: MusicResourceId): RecognizedLyricTrack? {
        val raw = preferences(context).getString(playbackId.key(), null) ?: return null
        return runCatching {
            val value = JSONObject(raw)
            val id = value.optLong("neteaseId", -1L)
            if (id <= 0L) return null
            RecognizedLyricTrack(
                neteaseId = id,
                title = value.optString("title"),
                artists = value.optString("artists"),
                album = value.optString("album"),
                artworkUrl = value.optString("artwork").takeIf(String::isNotBlank),
                durationMs = value.optLong("durationMs"),
                savedAtEpochMs = value.optLong("savedAt"),
            )
        }.getOrNull()
    }

    fun write(context: Context, playbackId: MusicResourceId, track: RecognizedLyricTrack) {
        val value = JSONObject()
            .put("neteaseId", track.neteaseId)
            .put("title", track.title)
            .put("artists", track.artists)
            .put("album", track.album)
            .put("artwork", track.artworkUrl.orEmpty())
            .put("durationMs", track.durationMs)
            .put("savedAt", track.savedAtEpochMs)
        preferences(context).edit().putString(playbackId.key(), value.toString()).apply()
    }

    fun clear(context: Context) {
        preferences(context).edit().clear().apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    private fun MusicResourceId.key(): String = "${sourceId.value}:$value"
}
