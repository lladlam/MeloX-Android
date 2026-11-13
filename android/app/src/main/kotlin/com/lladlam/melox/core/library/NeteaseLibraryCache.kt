package com.lladlam.melox.core.library

import android.content.Context
import com.lladlam.melox.core.model.SearchSong
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent metadata cache with one network refresh per cold-start process.
 *
 * 公开构造接收 Android [Context]，目录固定在 `filesDir/netease_library_cache`；
 * [internal] 构造接收任意 [File]，仅供单元测试注入临时目录（JVM 测试没有 [Context]）。
 */
class NeteaseLibraryCache internal constructor(
    private val directory: File,
) {
    internal val cacheDir: File get() = directory

    constructor(context: Context) : this(
        File(context.applicationContext.filesDir, "netease_library_cache"),
    )

    suspend fun loadSnapshot(userId: Long): NeteaseLibrarySnapshot? = readJson(
        file = File(directory, "library_$userId.json"),
        decode = ::decodeSnapshot,
    )

    suspend fun saveSnapshot(userId: Long, snapshot: NeteaseLibrarySnapshot) {
        writeJson(File(directory, "library_$userId.json")) { encodeSnapshot(snapshot) }
    }

    suspend fun loadPlaylistDetail(playlistId: Long): NeteasePlaylistDetail? = readJson(
        file = File(directory, "playlist_$playlistId.json"),
        decode = ::decodePlaylistDetail,
    )

    suspend fun savePlaylistDetail(playlistId: Long, detail: NeteasePlaylistDetail) {
        writeJson(File(directory, "playlist_$playlistId.json")) { encodePlaylistDetail(detail) }
    }

    suspend fun loadHomeContent(cacheKey: String): NeteaseHomeContent? = readJson(File(directory, "home_${safeCacheKey(cacheKey)}.json")) { value ->
        NeteaseHomeContent(
            playlists = decodePlaylists(value.optJSONArray("playlists") ?: JSONArray()),
            newSongs = decodeSongs(value.optJSONArray("newSongs") ?: JSONArray()),
            recentlyTrending = decodeSongs(value.optJSONArray("recentlyTrending") ?: JSONArray()),
            tailoredSongs = decodeSongs(value.optJSONArray("tailoredSongs") ?: JSONArray()),
            chartPlaylists = decodePlaylists(value.optJSONArray("chartPlaylists") ?: JSONArray()),
            radarPlaylists = decodePlaylists(value.optJSONArray("radarPlaylists") ?: JSONArray()),
            personalPlaylists = decodePlaylists(value.optJSONArray("personalPlaylists") ?: JSONArray()),
            regionalSongs = decodeSongs(value.optJSONArray("regionalSongs") ?: JSONArray()),
            roamingSongs = decodeSongs(value.optJSONArray("roamingSongs") ?: JSONArray()),
            similarSongs = decodeSongs(value.optJSONArray("similarSongs") ?: JSONArray()),
            podcasts = decodeHomePodcasts(value.optJSONArray("podcasts") ?: JSONArray()),
        )
    }
    suspend fun saveHomeContent(cacheKey: String, content: NeteaseHomeContent) {
        writeJson(File(directory, "home_${safeCacheKey(cacheKey)}.json")) { JSONObject()
            .put("playlists", encodePlaylists(content.playlists)).put("newSongs", encodeSongs(content.newSongs))
            .put("recentlyTrending", encodeSongs(content.recentlyTrending)).put("tailoredSongs", encodeSongs(content.tailoredSongs))
            .put("chartPlaylists", encodePlaylists(content.chartPlaylists))
            .put("radarPlaylists", encodePlaylists(content.radarPlaylists)).put("personalPlaylists", encodePlaylists(content.personalPlaylists))
            .put("regionalSongs", encodeSongs(content.regionalSongs)).put("roamingSongs", encodeSongs(content.roamingSongs))
            .put("similarSongs", encodeSongs(content.similarSongs)).put("podcasts", encodeHomePodcasts(content.podcasts)) }
    }

    suspend fun loadExplore(category: String): List<NeteasePlaylistSummary>? = readJson(
        File(directory, "explore_${safeCacheKey(category)}.json"),
    ) { value -> decodePlaylists(value.optJSONArray("playlists") ?: JSONArray()) }

    suspend fun saveExplore(category: String, playlists: List<NeteasePlaylistSummary>) {
        writeJson(
            File(directory, "explore_${safeCacheKey(category)}.json"),
        ) { JSONObject().put("playlists", encodePlaylists(playlists)) }
    }

    private suspend fun <T> readJson(file: File, decode: (JSONObject) -> T): T? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!file.isFile) return@runCatching null
                if (System.currentTimeMillis() - file.lastModified() > MAX_CACHE_AGE_MS) {
                    file.delete()
                    return@runCatching null
                }
                decode(JSONObject(file.readText()))
            }.getOrNull()
        }

    private suspend fun writeJson(file: File, value: () -> JSONObject) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val encoded = value().toString()
        val temporary = File.createTempFile("${file.name}.", ".tmp", file.parentFile)
        try {
            temporary.writeText(encoded)
            if (!temporary.renameTo(file)) {
                file.writeText(encoded)
            }
        } finally {
            temporary.delete()
        }
        pruneCache()
    }

    /**
     * 清理缓存目录里的「瞬态」文件：home / explore / playlist 这类随浏览、刷新反复落盘
     * 的文件。
     *
     * ⚠ **「我喜欢的音乐」快照 (`library_*.json`) 受保护，永不在此被删除。**
     * 原因：所有缓存共用 `files/netease_library_cache` 一个目录，而 `pruneCache` 按
     * `lastModified` 倒序、越过 `MAX_CACHE_FILES` 就删。library 快照只在冷启动刷新或
     * 点赞切换时重写，比 home / explore / playlist 少得多 ⇒ 它几乎永远是目录里最旧
     * 的文件，一旦瞬态缓存文件累计超过 128 个，library 快照会第一个被清掉。清掉之后
     * `loadSnapshot` 返回 null，而本进程的冷启动刷新闸门 (`refreshedLibraries`) 已经
     * 放过一次 ⇒ 收藏列表会一直空白到进程重启。这正是「收藏的歌过段时间消失、消失时间
     * 不固定」的根因。把 library 快照排除出按时间/数量的淘汰，就能杜绝这一路径。
     *
     * 字节上限同样只对瞬态文件生效：统计/删除都不计入受保护的 library 快照，因此即便
     * 收藏非常多，也不会因为「目录总字节超 32MB」被误删。playlist 详情同理受保护
     * （它本身也可能很大、且点开歌单后用户会长期依赖），只删 home / explore 这类
     * 纯浏览缓存。
     */
    @Suppress("MemberVisibilityCanBePrivate")
    internal fun pruneCache() {
        val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }.orEmpty()
        // Favorites snapshots must survive pruning (they are expensive to rebuild
        // and a lost snapshot looks like the user's library disappeared).
        val library = files.filter { it.name.startsWith("library_") }
        // Playlist detail entries are useful but rebuildable; cap them separately
        // so browsing many playlists cannot grow the cache without bound.
        var playlistBytes = 0L
        files.filter { it.name.startsWith("playlist_") }
            .sortedByDescending(File::lastModified)
            .forEachIndexed { index, cached ->
                playlistBytes += cached.length()
                if (index >= MAX_PLAYLIST_CACHE_FILES || playlistBytes > MAX_PLAYLIST_CACHE_BYTES) {
                    cached.delete()
                }
            }
        val transient = files.filterNot { it in library || it.name.startsWith("playlist_") }
            .sortedByDescending(File::lastModified)
        var retainedBytes = 0L
        transient.forEachIndexed { index, cached ->
            retainedBytes += cached.length()
            if (index >= MAX_CACHE_FILES || retainedBytes > MAX_CACHE_BYTES) cached.delete()
        }
    }

    companion object {
        private val refreshedLibraries = mutableSetOf<Long>()
        private val refreshedPlaylists = mutableSetOf<Long>()
        private val refreshedHomes = mutableSetOf<String>()
        private val refreshedExplore = mutableSetOf<String>()

        // Cold-start refresh gate: returns true only for the first automatic refresh
        // in this process AND only when the on-disk cache is stale. This stops the app
        // from re-pulling the network every time the system kills and revives it while
        // backgrounded (the "restart / reload on return" problem), while still refreshing
        // genuinely stale data. FRESH_WINDOW_MS is a balance: short enough that returning
        // from background never forces a reload, long enough that abandoned caches refresh.
        @Synchronized
        fun beginLibraryColdStartRefresh(userId: Long, directory: File): Boolean {
            if (!rememberRefreshKey(refreshedLibraries, userId)) return false
            return isCacheStale(File(directory, "library_$userId.json"))
        }

        @Synchronized
        fun beginPlaylistColdStartRefresh(playlistId: Long, directory: File): Boolean {
            if (!rememberRefreshKey(refreshedPlaylists, playlistId)) return false
            return isCacheStale(File(directory, "playlist_$playlistId.json"))
        }

        @Synchronized
        fun beginHomeColdStartRefresh(cacheKey: String, directory: File): Boolean {
            if (!rememberRefreshKey(refreshedHomes, cacheKey)) return false
            return isCacheStale(File(directory, "home_${safeCacheKey(cacheKey)}.json"))
        }

        @Synchronized
        fun beginExploreColdStartRefresh(category: String, directory: File): Boolean {
            if (!rememberRefreshKey(refreshedExplore, category)) return false
            return isCacheStale(File(directory, "explore_${safeCacheKey(category)}.json"))
        }

        /** Bounds the process-lifetime refresh gates; old entries may refresh once more. */
        private fun <T> rememberRefreshKey(keys: MutableSet<T>, key: T): Boolean {
            if (keys.size >= MAX_REFRESH_KEYS) keys.clear()
            return keys.add(key)
        }

        private fun isCacheStale(file: File): Boolean =
            !(file.isFile && System.currentTimeMillis() - file.lastModified() < FRESH_WINDOW_MS)

        private const val FRESH_WINDOW_MS = 30L * 60L * 1000L
        private const val MAX_CACHE_FILES = 128
        private const val MAX_CACHE_BYTES = 32L * 1024L * 1024L
        private const val MAX_PLAYLIST_CACHE_FILES = 48
        private const val MAX_PLAYLIST_CACHE_BYTES = 64L * 1024L * 1024L
        private const val MAX_CACHE_AGE_MS = 30L * 24L * 60L * 60L * 1_000L
        private const val MAX_REFRESH_KEYS = 256
    }
}

private fun encodeSnapshot(value: NeteaseLibrarySnapshot) = JSONObject().put("playlists", encodePlaylists(value.playlists)).put("likedSongs", encodeSongs(value.likedSongs)).put("recentSongs", encodeSongs(value.recentSongs)).put("likedPlaylistId", value.likedPlaylistId)
private fun decodeSnapshot(value: JSONObject) = NeteaseLibrarySnapshot(decodePlaylists(value.optJSONArray("playlists") ?: JSONArray()), decodeSongs(value.optJSONArray("likedSongs") ?: JSONArray()), decodeSongs(value.optJSONArray("recentSongs") ?: JSONArray()), value.optLong("likedPlaylistId", -1L).takeIf { it > 0L })

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
    .put("specialType", value.specialType ?: JSONObject.NULL)

private fun decodePlaylist(value: JSONObject) = NeteasePlaylistSummary(
    id = value.getLong("id"),
    name = value.optString("name"),
    coverUrl = value.optNullableString("coverUrl"),
    trackCount = value.optInt("trackCount"),
    creatorName = value.optString("creatorName"),
    creatorUserId = value.optLong("creatorUserId", -1L).takeIf { it > 0L },
    playCount = value.optLong("playCount"),
    description = value.optNullableString("description"),
    specialType = value.optInt("specialType", -1).takeIf { it >= 0 },
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

private fun encodeHomePodcasts(values: List<NeteaseHomePodcast>) = JSONArray().apply {
    values.forEach { podcast ->
        put(
            JSONObject()
                .put("id", podcast.id)
                .put("name", podcast.name)
                .put("artworkUrl", podcast.artworkUrl)
                .put("programId", podcast.programId ?: JSONObject.NULL)
                .put("playbackSong", podcast.playbackSong?.let { encodeSongs(listOf(it)).optJSONObject(0) } ?: JSONObject.NULL),
        )
    }
}
private fun decodeHomePodcasts(values: JSONArray) = buildList {
    for (index in 0 until values.length()) {
        val value = values.optJSONObject(index) ?: continue
        val id = value.optLong("id", -1L)
        if (id <= 0L) continue
        val song = value.optJSONObject("playbackSong")?.let { decodeSongs(JSONArray().put(it)).firstOrNull() }
        add(
            NeteaseHomePodcast(
                id,
                value.optString("name").ifBlank { "播客节目" },
                value.optNullableString("artworkUrl"),
                value.optLong("programId", 0L).takeIf { it > 0L },
                song,
            ),
        )
    }
}
private fun safeCacheKey(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
private fun JSONObject.optNullableString(name: String): String? = if (isNull(name)) null else optString(name).takeIf(String::isNotBlank)
