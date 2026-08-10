package com.lladlam.melox.core.library

import android.content.Context
import com.lladlam.melox.core.model.SearchSong
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Persistent metadata cache with one network refresh per cold-start process. */
class NeteaseLibraryCache(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "netease_library_cache")

    suspend fun loadSnapshot(userId: Long): NeteaseLibrarySnapshot? = readJson(
        file = File(directory, "library_$userId.json"),
        decode = ::decodeSnapshot,
    )

    suspend fun saveSnapshot(userId: Long, snapshot: NeteaseLibrarySnapshot) {
        writeJson(File(directory, "library_$userId.json"), encodeSnapshot(snapshot))
    }

    suspend fun loadPlaylistDetail(playlistId: Long): NeteasePlaylistDetail? = readJson(
        file = File(directory, "playlist_$playlistId.json"),
        decode = ::decodePlaylistDetail,
    )

    suspend fun savePlaylistDetail(playlistId: Long, detail: NeteasePlaylistDetail) {
        writeJson(File(directory, "playlist_$playlistId.json"), encodePlaylistDetail(detail))
    }

    suspend fun loadHomeContent(): NeteaseHomeContent? = readJson(
        File(directory, "home.json"),
    ) { value ->
        NeteaseHomeContent(
            playlists = decodePlaylists(value.optJSONArray("playlists") ?: JSONArray()),
            newSongs = decodeSongs(value.optJSONArray("newSongs") ?: JSONArray()),
        )
    }

    suspend fun saveHomeContent(content: NeteaseHomeContent) {
        writeJson(
            File(directory, "home.json"),
            JSONObject()
                .put("playlists", encodePlaylists(content.playlists))
                .put("newSongs", encodeSongs(content.newSongs)),
        )
    }

    suspend fun loadExplore(category: String): List<NeteasePlaylistSummary>? = readJson(
        File(directory, "explore_${category.hashCode()}.json"),
    ) { value -> decodePlaylists(value.optJSONArray("playlists") ?: JSONArray()) }

    suspend fun saveExplore(category: String, playlists: List<NeteasePlaylistSummary>) {
        writeJson(
            File(directory, "explore_${category.hashCode()}.json"),
            JSONObject().put("playlists", encodePlaylists(playlists)),
        )
    }

    private suspend fun <T> readJson(file: File, decode: (JSONObject) -> T): T? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!file.isFile) return@runCatching null
                decode(JSONObject(file.readText()))
            }.getOrNull()
        }

    private suspend fun writeJson(file: File, value: JSONObject) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(value.toString())
        if (!temporary.renameTo(file)) {
            file.writeText(value.toString())
            temporary.delete()
        }
    }

    companion object {
        private val refreshedLibraries = mutableSetOf<Long>()
        private val refreshedPlaylists = mutableSetOf<Long>()
        private var refreshedHome = false
        private val refreshedExplore = mutableSetOf<String>()

        /** Returns true only for the first automatic refresh in this app process. */
        @Synchronized
        fun beginLibraryColdStartRefresh(userId: Long): Boolean =
            refreshedLibraries.add(userId)

        /** Each opened playlist is refreshed at most once in this app process. */
        @Synchronized
        fun beginPlaylistColdStartRefresh(playlistId: Long): Boolean =
            refreshedPlaylists.add(playlistId)

        @Synchronized
        fun beginHomeColdStartRefresh(): Boolean {
            if (refreshedHome) return false
            refreshedHome = true
            return true
        }

        @Synchronized
        fun beginExploreColdStartRefresh(category: String): Boolean = refreshedExplore.add(category)
    }
}

private fun encodeSnapshot(value: NeteaseLibrarySnapshot) = JSONObject()
    .put("playlists", encodePlaylists(value.playlists))
    .put("likedSongs", encodeSongs(value.likedSongs))
    .put("recentSongs", encodeSongs(value.recentSongs))

private fun decodeSnapshot(value: JSONObject) = NeteaseLibrarySnapshot(
    playlists = decodePlaylists(value.optJSONArray("playlists") ?: JSONArray()),
    likedSongs = decodeSongs(value.optJSONArray("likedSongs") ?: JSONArray()),
    recentSongs = decodeSongs(value.optJSONArray("recentSongs") ?: JSONArray()),
)

private fun encodePlaylistDetail(value: NeteasePlaylistDetail) = JSONObject()
    .put("summary", encodePlaylist(value.summary))
    .put("songs", encodeSongs(value.songs))

private fun decodePlaylistDetail(value: JSONObject): NeteasePlaylistDetail {
    val summary = decodePlaylist(value.getJSONObject("summary"))
    return NeteasePlaylistDetail(
        summary = summary,
        songs = decodeSongs(value.optJSONArray("songs") ?: JSONArray()),
    )
}

private fun encodePlaylists(values: List<NeteasePlaylistSummary>) = JSONArray().apply {
    values.forEach { put(encodePlaylist(it)) }
}

private fun decodePlaylists(values: JSONArray) = buildList {
    for (index in 0 until values.length()) {
        values.optJSONObject(index)?.let { add(decodePlaylist(it)) }
    }
}

private fun encodePlaylist(value: NeteasePlaylistSummary) = JSONObject()
    .put("id", value.id)
    .put("name", value.name)
    .put("coverUrl", value.coverUrl)
    .put("trackCount", value.trackCount)
    .put("creatorName", value.creatorName)
    .put("creatorUserId", value.creatorUserId)
    .put("playCount", value.playCount)
    .put("description", value.description)

private fun decodePlaylist(value: JSONObject) = NeteasePlaylistSummary(
    id = value.getLong("id"),
    name = value.optString("name"),
    coverUrl = value.optNullableString("coverUrl"),
    trackCount = value.optInt("trackCount"),
    creatorName = value.optString("creatorName"),
    creatorUserId = value.optLong("creatorUserId", -1L).takeIf { it > 0L },
    playCount = value.optLong("playCount"),
    description = value.optNullableString("description"),
)

private fun encodeSongs(values: List<SearchSong>) = JSONArray().apply {
    values.forEach { song ->
        put(
            JSONObject()
                .put("id", song.id)
                .put("name", song.name)
                .put("artists", song.artists)
                .put("album", song.album)
                .put("artworkUrl", song.artworkUrl)
                .put("durationMs", song.durationMs),
        )
    }
}

private fun decodeSongs(values: JSONArray) = buildList {
    for (index in 0 until values.length()) {
        val song = values.optJSONObject(index) ?: continue
        add(
            SearchSong(
                id = song.getLong("id"),
                name = song.optString("name"),
                artists = song.optString("artists"),
                album = song.optString("album"),
                artworkUrl = song.optNullableString("artworkUrl"),
                durationMs = song.optLong("durationMs"),
            ),
        )
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf(String::isNotBlank)
