package com.lladlam.melox.core.provider.local

import android.content.Context
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricQuality
import com.lladlam.melox.core.lyrics.LyricSource
import com.lladlam.melox.core.lyrics.LyricSyllable
import com.lladlam.melox.core.lyrics.LyricTimingKind
import com.lladlam.melox.core.lyrics.LyricsDocument
import org.json.JSONArray
import org.json.JSONObject

/** Small process-safe JSON index until the app adopts a database dependency. */
class LocalMusicRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "melox_local_music",
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun tracks(): List<LocalTrackRecord> = readArray("tracks")
        .mapNotNull(::decodeTrack)
        .filterNot { it.contentUri.isBlank() }

    @Synchronized
    fun replaceTracks(value: List<LocalTrackRecord>) {
        val oldFavorites = tracks().associate { it.fileKey to it.isFavorite }
        val array = JSONArray()
        value.distinctBy(LocalTrackRecord::fileKey).forEach { track ->
            array.put(encodeTrack(track.copy(isFavorite = oldFavorites[track.fileKey] ?: track.isFavorite)))
        }
        preferences.edit().putString("tracks", array.toString()).apply()
    }

    @Synchronized
    fun track(fileKey: String): LocalTrackRecord? = tracks().firstOrNull { it.fileKey == fileKey }

    @Synchronized
    fun setFavorite(fileKey: String, favorite: Boolean) {
        replaceTracks(tracks().map { if (it.fileKey == fileKey) it.copy(isFavorite = favorite) else it })
    }

    @Synchronized
    fun updateRecognition(fileKey: String, song: com.lladlam.melox.core.model.SearchSong): Boolean {
        val current = tracks().firstOrNull { it.fileKey == fileKey } ?: return false
        replaceTracks(tracks().map {
            if (it.fileKey != fileKey) it else it.copy(
                recognizedNeteaseId = song.id,
                recognizedTitle = song.name,
                recognizedArtist = song.artists,
                recognizedAlbum = song.album,
                recognizedArtworkUrl = song.artworkUrl,
            )
        })
        return current.fileKey == fileKey
    }

    @Synchronized
    fun updateLyrics(fileKey: String, lyrics: LyricsDocument): Boolean {
        if (tracks().none { it.fileKey == fileKey }) return false
        replaceTracks(tracks().map { if (it.fileKey == fileKey) it.copy(cachedLyrics = lyrics) else it })
        return true
    }

    @Synchronized
    fun playlists(): List<LocalPlaylist> = readArray("playlists").mapNotNull { value ->
        val item = value as? JSONObject ?: return@mapNotNull null
        val id = item.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
        val keys = item.optJSONArray("trackKeys")?.let { array ->
            buildList { for (index in 0 until array.length()) add(array.optString(index)) }
        }.orEmpty()
        LocalPlaylist(id, item.optString("name").ifBlank { "本地歌单" }, keys)
    }

    @Synchronized
    fun savePlaylist(playlist: LocalPlaylist) {
        val updated = playlists().filterNot { it.id == playlist.id } + playlist
        val array = JSONArray()
        updated.forEach { value ->
            array.put(
                JSONObject()
                    .put("id", value.id)
                    .put("name", value.name)
                    .put("trackKeys", JSONArray(value.trackKeys)),
            )
        }
        preferences.edit().putString("playlists", array.toString()).apply()
    }

    @Synchronized
    fun removePlaylist(id: String) {
        val array = JSONArray()
        playlists().filterNot { it.id == id }.forEach { value ->
            array.put(
                JSONObject()
                    .put("id", value.id)
                    .put("name", value.name)
                    .put("trackKeys", JSONArray(value.trackKeys)),
            )
        }
        preferences.edit().putString("playlists", array.toString()).apply()
    }

    @Synchronized
    fun scanRoots(): List<LocalScanRoot> = readArray("roots").mapNotNull { value ->
        val item = value as? JSONObject ?: return@mapNotNull null
        val uri = item.optString("uri").takeIf(String::isNotBlank) ?: return@mapNotNull null
        LocalScanRoot(uri, item.optInt("flags", 0))
    }

    @Synchronized
    fun addScanRoot(root: LocalScanRoot) {
        val roots = scanRoots().filterNot { it.uri == root.uri } + root
        val array = JSONArray()
        roots.forEach { array.put(JSONObject().put("uri", it.uri).put("flags", it.persistedFlags)) }
        preferences.edit().putString("roots", array.toString()).apply()
    }

    @Synchronized
    fun removeScanRoot(uri: String) {
        val array = JSONArray()
        scanRoots().filterNot { it.uri == uri }.forEach {
            array.put(JSONObject().put("uri", it.uri).put("flags", it.persistedFlags))
        }
        preferences.edit().putString("roots", array.toString()).apply()
    }

    private fun readArray(key: String): List<Any> {
        val raw = preferences.getString(key, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList { for (index in 0 until array.length()) add(array.opt(index)) }
    }

    private fun encodeTrack(track: LocalTrackRecord) = JSONObject()
        .put("fileKey", track.fileKey)
        .put("contentUri", track.contentUri)
        .put("displayName", track.displayName)
        .put("title", track.title)
        .put("artist", track.artist)
        .put("album", track.album)
        .put("durationMs", track.durationMs)
        .put("mimeType", track.mimeType)
        .put("sizeBytes", track.sizeBytes)
        .put("lastModifiedMs", track.lastModifiedMs)
        .put("sourceRootUri", track.sourceRootUri)
        .put("artworkUri", track.artworkUri)
        .put("isFavorite", track.isFavorite)
        .put("recognizedNeteaseId", track.recognizedNeteaseId)
        .put("recognizedTitle", track.recognizedTitle)
        .put("recognizedArtist", track.recognizedArtist)
        .put("recognizedAlbum", track.recognizedAlbum)
        .put("recognizedArtworkUrl", track.recognizedArtworkUrl)
        .put("cachedLyrics", track.cachedLyrics?.let(::encodeLyrics))

    private fun decodeTrack(value: Any): LocalTrackRecord? {
        val item = value as? JSONObject ?: return null
        val key = item.optString("fileKey").takeIf(String::isNotBlank) ?: return null
        return LocalTrackRecord(
            fileKey = key,
            contentUri = item.optString("contentUri"),
            displayName = item.optString("displayName"),
            title = item.optString("title"),
            artist = item.optString("artist"),
            album = item.optString("album"),
            durationMs = item.optLong("durationMs"),
            mimeType = item.optString("mimeType").takeIf(String::isNotBlank),
            sizeBytes = item.optLong("sizeBytes"),
            lastModifiedMs = item.optLong("lastModifiedMs"),
            sourceRootUri = item.optString("sourceRootUri").takeIf(String::isNotBlank),
            artworkUri = item.optString("artworkUri").takeIf(String::isNotBlank),
            isFavorite = item.optBoolean("isFavorite"),
            recognizedNeteaseId = item.optLong("recognizedNeteaseId").takeIf { it > 0L },
            recognizedTitle = item.optString("recognizedTitle").takeIf(String::isNotBlank),
            recognizedArtist = item.optString("recognizedArtist").takeIf(String::isNotBlank),
            recognizedAlbum = item.optString("recognizedAlbum").takeIf(String::isNotBlank),
            recognizedArtworkUrl = item.optString("recognizedArtworkUrl").takeIf(String::isNotBlank),
            cachedLyrics = item.optJSONObject("cachedLyrics")?.let(::decodeLyrics),
        )
    }

    private fun encodeLyrics(document: LyricsDocument) = JSONObject()
        .put("source", document.source.name)
        .put("quality", document.quality.name)
        .put("pseudoTimingAllowed", document.pseudoTimingAllowed)
        .put("lines", JSONArray().apply { document.lines.forEach { line ->
            put(JSONObject()
                .put("timeMs", line.timeMs).put("durationMs", line.durationMs).put("text", line.text)
                .put("translation", line.translation).put("romanization", line.romanization)
                .put("timingKind", line.timingKind.name)
                .put("syllables", JSONArray().apply { line.syllables.forEach { put(encodeSyllable(it)) } })
                .put("romanizationSyllables", JSONArray().apply { line.romanizationSyllables.forEach { put(encodeSyllable(it)) } }))
        } })

    private fun encodeSyllable(value: LyricSyllable) = JSONObject()
        .put("text", value.text).put("startTimeMs", value.startTimeMs).put("endTimeMs", value.endTimeMs)

    private fun decodeLyrics(value: JSONObject): LyricsDocument = LyricsDocument(
        lines = buildList {
            val lines = value.optJSONArray("lines") ?: return@buildList
            for (index in 0 until lines.length()) {
                val line = lines.optJSONObject(index) ?: continue
                add(LyricLine(
                    timeMs = line.optLong("timeMs"),
                    durationMs = line.optLong("durationMs").takeIf { !line.isNull("durationMs") },
                    text = line.optString("text"),
                    translation = line.optString("translation").takeIf(String::isNotBlank),
                    romanization = line.optString("romanization").takeIf(String::isNotBlank),
                    timingKind = runCatching { LyricTimingKind.valueOf(line.optString("timingKind")) }.getOrDefault(LyricTimingKind.Precise),
                    syllables = decodeSyllables(line.optJSONArray("syllables")),
                    romanizationSyllables = decodeSyllables(line.optJSONArray("romanizationSyllables")),
                ))
            }
        },
        source = runCatching { LyricSource.valueOf(value.optString("source")) }.getOrDefault(LyricSource.Local),
        quality = runCatching { LyricQuality.valueOf(value.optString("quality")) }.getOrDefault(LyricQuality.Fallback),
        pseudoTimingAllowed = value.optBoolean("pseudoTimingAllowed", true),
    )

    private fun decodeSyllables(array: JSONArray?): List<LyricSyllable> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val value = array.optJSONObject(index) ?: continue
            add(LyricSyllable(value.optString("text"), value.optLong("startTimeMs"), value.optLong("endTimeMs")))
        }
    }
}
