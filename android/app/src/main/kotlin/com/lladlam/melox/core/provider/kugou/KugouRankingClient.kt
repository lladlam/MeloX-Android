package com.lladlam.melox.core.provider.kugou

import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicRankingSummary
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

class KugouRankingClient(
    private val sessionProvider: () -> KugouSession,
    httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    private val requests = KugouRequestClient(sessionProvider, httpClient)

    suspend fun tracks(
        ranking: MusicRankingSummary,
        page: Int = 1,
        pageSize: Int = 100,
    ): MusicPage<MusicTrack> {
        require(ranking.id.source == MusicSource.Kugou)
        val rankId = ranking.id.value.toLongOrNull()
            ?: error("酷狗排行榜 ID 无效：${ranking.id.value}")
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 200)
        val response = requests.post(
            path = "/openapi/kmr/v2/rank/audio",
            body = JSONObject()
                .put("show_portrait_mv", 1)
                .put("show_type_total", 1)
                .put("filter_original_remarks", 1)
                .put("area_code", 1)
                .put("pagesize", safeSize)
                .put("rank_cid", 0)
                .put("type", 1)
                .put("page", safePage)
                .put("rank_id", rankId),
            headers = mapOf("kg-tid" to "369"),
        )
        val tracks = flattenObjects(response)
            .mapNotNull(::parseTrack)
            .distinctBy { it.id.value }
            .take(safeSize)
        val total = findFirstLong(response, "total", "total_count", "total_num", "count")
            .takeIf { it >= 0 }
        return MusicPage(
            items = tracks,
            page = safePage,
            pageSize = safeSize,
            total = total,
        )
    }

    private fun parseTrack(item: JSONObject): MusicTrack? {
        val hash = firstString(item, "FileHash", "Hash", "hash", "filehash").uppercase()
        if (hash.isBlank()) return null
        val (title, singer) = recoverKugouTrackText(
            firstString(item, "SongName", "songname", "AudioName", "audio_name", "FileName", "filename", "name"),
            kugouSingerName(item, "SingerName", "singername", "author_name", "AuthorName"),
        )
        if (title.isBlank()) return null
        val albumName = firstString(item, "AlbumName", "album_name", "albumname")
        val albumId = firstString(item, "AlbumID", "album_id", "albumid").takeIf(String::isNotBlank)
        val albumAudioId = firstLong(item, "album_audio_id", "MixSongID", "mixsongid", "AlbumAudioID", "Audioid", "audio_id")
            .takeIf { it > 0 }
        val artwork = kugouArtworkUrl(item)
        val artists = singer
            .split(Regex("\\s*(?:、|/|&|,|;|；)\\s*"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .ifEmpty { listOf("未知歌手") }
            .map { MusicArtistRef(name = it) }
        return MusicTrack(
            id = MusicResourceId(MusicSource.Kugou, hash),
            title = title,
            artists = artists,
            album = albumName.takeIf(String::isNotBlank)?.let {
                MusicAlbumRef(
                    id = albumId?.let { value -> MusicResourceId(MusicSource.Kugou, value) },
                    name = it,
                    artworkUrl = artwork,
                )
            },
            artworkUrl = artwork,
            durationMs = firstLong(item, "Duration", "duration", "time_length").takeIf { it > 0 }?.times(1_000L),
            providerMetadata = ProviderTrackMetadata.Kugou(
                hash = hash,
                albumAudioId = albumAudioId,
                albumId = albumId,
            ),
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
        flattenObjects(root).forEach { value ->
            val found = firstLong(value, *keys)
            if (found >= 0) return found
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
