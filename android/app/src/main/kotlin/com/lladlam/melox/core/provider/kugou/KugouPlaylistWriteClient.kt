package com.lladlam.melox.core.provider.kugou

import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/** Authenticated Kugou user-playlist writer using provider-native listid values. */
class KugouPlaylistWriteClient(
    private val sessionProvider: () -> KugouSession,
    httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    private val requests = KugouRequestClient(sessionProvider, httpClient)

    suspend fun writablePlaylists(
        page: Int = 1,
        pageSize: Int = 50,
    ): MusicPage<MusicPlaylistSummary> = withContext(Dispatchers.IO) {
        val session = sessionProvider()
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 50)
        if (!session.isLoggedIn) throw IOException("请先登录酷狗音乐账号")

        val response = requests.post(
            path = "/v7/get_all_list",
            params = mapOf(
                "plat" to "1",
                "userid" to session.userId.toString(),
                "token" to session.token,
            ),
            body = JSONObject()
                .put("userid", session.userId)
                .put("token", session.token)
                .put("total_ver", 979)
                .put("type", 2)
                .put("page", safePage)
                .put("pagesize", safeSize),
            headers = mapOf("x-router" to "cloudlist.service.kugou.com"),
        )

        val playlists = flattenObjects(response)
            .mapNotNull(::parseWritablePlaylist)
            .distinctBy { it.id.value }
            .take(safeSize)
        val total = findFirstLong(response, "total", "total_count", "count")
            .takeIf { it >= 0L }
            ?: playlists.size.toLong()
        MusicPage(
            items = playlists,
            page = safePage,
            pageSize = safeSize,
            total = total,
        )
    }

    suspend fun addTrackToPlaylist(
        track: MusicTrack,
        playlist: MusicPlaylistSummary,
    ) = withContext(Dispatchers.IO) {
        require(track.id.source == MusicSource.Kugou) { "only Kugou tracks can be written to Kugou playlists" }
        require(playlist.id.source == MusicSource.Kugou) { "playlist must belong to Kugou" }
        val session = sessionProvider()
        if (!session.isLoggedIn) throw IOException("请先登录酷狗音乐账号")
        val listId = playlist.id.value.toLongOrNull()
            ?.takeIf { it > 0L }
            ?: throw IOException("酷狗音乐没有返回可写入的歌单 listid")
        val metadata = track.providerMetadata as? ProviderTrackMetadata.Kugou
        val hash = metadata?.hash?.takeIf(String::isNotBlank)
            ?: track.id.value.takeIf(String::isNotBlank)
            ?: throw IOException("酷狗歌曲缺少 hash")
        val albumId = metadata?.albumId?.toLongOrNull() ?: 0L
        val mixSongId = metadata?.albumAudioId ?: 0L
        val clientTime = System.currentTimeMillis() / 1_000L

        val resource = JSONObject()
            .put("number", 1)
            .put("name", "${track.artistText} - ${track.title}")
            .put("hash", hash.uppercase())
            .put("size", 0)
            .put("sort", 0)
            .put("timelen", track.durationMs ?: 0L)
            .put("bitrate", 0)
            .put("album_id", albumId)
            .put("mixsongid", mixSongId)
        val body = JSONObject()
            .put("userid", session.userId)
            .put("token", session.token)
            .put("listid", listId)
            .put("list_ver", 0)
            .put("type", 0)
            .put("slow_upload", 1)
            .put("scene", "false;null")
            .put("data", JSONArray().put(resource))

        requests.post(
            path = "/cloudlist.service/v6/add_song",
            params = mapOf(
                "last_time" to clientTime.toString(),
                "last_area" to "gztx",
                "userid" to session.userId.toString(),
                "token" to session.token,
            ),
            body = body,
        )
    }

    /**
     * This parser deliberately accepts only provider-native listid/list_id.
     * global_collection_id and specialid are display/read identifiers and are
     * never promoted into a writable id.
     */
    private fun parseWritablePlaylist(item: JSONObject): MusicPlaylistSummary? {
        if (firstString(item, "hash", "Hash", "FileHash", "filehash").isNotBlank()) return null
        val listId = firstLong(item, "listid", "list_id").takeIf { it > 0L }
            ?: firstString(item, "listid", "list_id").toLongOrNull()?.takeIf { it > 0L }
            ?: return null
        val title = firstString(
            item,
            "specialname",
            "special_name",
            "listname",
            "list_name",
            "playlist_name",
            "name",
            "title",
        )
        if (title.isBlank()) return null
        return MusicPlaylistSummary(
            id = MusicResourceId(MusicSource.Kugou, listId.toString()),
            title = title,
            artworkUrl = kugouArtworkUrl(item),
            creatorName = firstString(item, "nickname", "nick_name", "username", "author_name")
                .takeIf(String::isNotBlank),
            trackCount = firstLong(item, "song_count", "songcount", "song_num", "count")
                .takeIf { it >= 0 }?.toInt(),
        )
    }

    private fun flattenObjects(root: Any?): List<JSONObject> = buildList {
        fun visit(value: Any?) {
            when (value) {
                is JSONObject -> {
                    add(value)
                    val keys = value.keys()
                    while (keys.hasNext()) visit(value.opt(keys.next()))
                }
                is JSONArray -> for (index in 0 until value.length()) visit(value.opt(index))
            }
        }
        visit(root)
    }

    private fun findFirstLong(root: Any?, vararg keys: String): Long {
        flattenObjects(root).forEach { objectValue ->
            val found = firstLong(objectValue, *keys)
            if (found >= 0L) return found
        }
        return -1L
    }

    private fun firstString(value: JSONObject, vararg keys: String): String =
        keys.asSequence().map(value::optString).firstOrNull(String::isNotBlank).orEmpty()

    private fun firstLong(value: JSONObject, vararg keys: String): Long =
        keys.asSequence().mapNotNull { key ->
            when (val raw = value.opt(key)) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull()
                else -> null
            }
        }.firstOrNull() ?: -1L

}
