package com.lladlam.melox.core.provider.kugou

import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicPlaylistDetail
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

class KugouPlaylistClient(
    private val sessionProvider: () -> KugouSession,
    httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    private val requests = KugouRequestClient(sessionProvider, httpClient)

    suspend fun detail(
        playlist: MusicPlaylistSummary,
        page: Int = 1,
        pageSize: Int = 100,
    ): MusicPlaylistDetail {
        require(playlist.id.source == MusicSource.Kugou)
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 200)
        val session = sessionProvider()

        val detail = runCatching {
            requests.post(
                path = "/v3/get_list_info",
                body = JSONObject()
                    .put(
                        "data",
                        JSONArray().put(
                            JSONObject().put("global_collection_id", playlist.id.value),
                        ),
                    )
                    .put("userid", session.userId)
                    .put("token", session.token),
                headers = mapOf("x-router" to "pubsongs.kugou.com"),
            )
        }.getOrNull()

        val songsResponse = requests.get(
            path = "/pubsongs/v2/get_other_list_file_nofilt",
            params = mapOf(
                "area_code" to "1",
                "begin_idx" to ((safePage - 1) * safeSize).toString(),
                "plat" to "1",
                "type" to "1",
                "mode" to "1",
                "personal_switch" to "1",
                "extend_fields" to "abtags,hot_cmt,popularization",
                "pagesize" to safeSize.toString(),
                "global_collection_id" to playlist.id.value,
            ),
        )
        val tracks = flattenObjects(songsResponse)
            .mapNotNull(::parseTrack)
            .distinctBy { it.id.value }
            .take(safeSize)
        val summary = detail
            ?.let(::flattenObjects)
            ?.mapNotNull(::parseSummary)
            ?.firstOrNull()
            ?: playlist
        val total = findFirstLong(songsResponse, "total", "total_count", "count", "filesize")
            .takeIf { it >= 0 }
            ?: tracks.size.toLong()
        return MusicPlaylistDetail(
            summary = summary,
            tracks = tracks,
            total = total,
        )
    }

    private fun parseSummary(item: JSONObject): MusicPlaylistSummary? {
        if (firstString(item, "hash", "Hash", "FileHash").isNotBlank()) return null
        val id = firstString(item, "global_collection_id", "global_specialid", "specialid", "special_id")
            .ifBlank {
                firstLong(item, "global_collection_id", "global_specialid", "specialid", "special_id")
                    .takeIf { it > 0 }?.toString().orEmpty()
            }
        if (id.isBlank()) return null
        val title = firstString(item, "specialname", "special_name", "listname", "name", "title")
        if (title.isBlank()) return null
        return MusicPlaylistSummary(
            id = MusicResourceId(MusicSource.Kugou, id),
            title = title,
            artworkUrl = normalizeArtwork(firstString(item, "imgurl", "image", "pic", "cover", "sizable_cover")),
            creatorName = firstString(item, "nickname", "username", "author_name").takeIf(String::isNotBlank),
            description = firstString(item, "intro", "description", "desc").takeIf(String::isNotBlank),
            trackCount = firstLong(item, "song_count", "songcount", "song_num", "count").takeIf { it >= 0 }?.toInt(),
            playCount = firstLong(item, "play_count", "playcount", "heat").takeIf { it >= 0 },
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
        val artwork = normalizeArtwork(firstString(item, "Image", "image", "img", "album_img", "AlbumImage", "sizable_cover"))
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

    private fun normalizeArtwork(value: String): String? = value.trim()
        .takeIf(String::isNotBlank)
        ?.replace("{size}", "400")
        ?.let { if (it.startsWith("http://", true)) "https://${it.substringAfter("://")}" else it }
}
