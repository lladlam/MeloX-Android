package com.lladlam.melox.core.provider.kugou

import com.lladlam.melox.core.music.model.MusicAccountSummary
import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicHomeFeed
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicRankingSummary
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import java.security.MessageDigest
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Discovery/library endpoints ported from MakcRe/KuGouMusicApi. Kept separate
 * from the playback client so a changing home endpoint cannot break playback.
 */
class KugouDiscoveryClient(
    private val sessionProvider: () -> KugouSession,
    httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    private val requests = KugouRequestClient(sessionProvider, httpClient)

    suspend fun accountSummary(): MusicAccountSummary? {
        val session = sessionProvider()
        if (!session.isLoggedIn) return null
        return MusicAccountSummary(
            source = MusicSource.Kugou,
            id = session.userId.toString(),
            displayName = "酷狗用户 ${session.userId}",
            subtitle = if (session.vipType > 0) "酷狗会员" else "酷狗音乐",
        )
    }

    suspend fun homeFeed(
        playlistLimit: Int = 12,
        newSongLimit: Int = 12,
        rankingLimit: Int = 8,
    ): MusicHomeFeed {
        val playlists = runCatching { recommendedPlaylists(playlistLimit) }.getOrDefault(emptyList())
        val newSongs = runCatching { newSongs(newSongLimit) }.getOrDefault(emptyList())
        val rankings = runCatching { rankings(rankingLimit) }.getOrDefault(emptyList())
        return MusicHomeFeed(
            recommendedPlaylists = playlists,
            newSongs = newSongs,
            rankings = rankings,
        )
    }

    suspend fun userPlaylists(
        page: Int = 1,
        pageSize: Int = 30,
    ): MusicPage<MusicPlaylistSummary> {
        val session = sessionProvider()
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 50)
        if (!session.isLoggedIn) return MusicPage(emptyList(), safePage, safeSize, 0)

        val body = JSONObject()
            .put("userid", session.userId)
            .put("token", session.token)
            .put("total_ver", 979)
            .put("type", 2)
            .put("page", safePage)
            .put("pagesize", safeSize)
        val response = requests.post(
            path = "/v7/get_all_list",
            params = mapOf(
                "plat" to "1",
                "userid" to session.userId.toString(),
                "token" to session.token,
            ),
            body = body,
            headers = mapOf("x-router" to "cloudlist.service.kugou.com"),
        )
        val candidates = flattenObjects(response)
            .mapNotNull(::parsePlaylist)
            .distinctBy { it.id.value }
        val total = findFirstLong(response, "total", "total_count", "count")
            .takeIf { it >= 0 }
            ?: candidates.size.toLong()
        return MusicPage(
            items = candidates.take(safeSize),
            page = safePage,
            pageSize = safeSize,
            total = total,
        )
    }

    private fun recommendedPlaylists(limit: Int): List<MusicPlaylistSummary> {
        val session = sessionProvider()
        val clientTime = (System.currentTimeMillis() / 1_000L).toString()
        val specialRecommend = JSONObject()
            .put("withtag", 1)
            .put("withsong", 1)
            .put("sort", 1)
            .put("ugc", 1)
            .put("is_selected", 0)
            .put("withrecommend", 1)
            .put("area_code", 1)
            .put("categoryid", 0)
        val body = JSONObject()
            .put("appid", KugouRequestClient.AppId)
            .put("mid", session.mid)
            .put("clientver", KugouRequestClient.ClientVersion)
            .put("platform", "android")
            .put("clienttime", clientTime)
            .put("userid", session.userId)
            .put("module_id", 1)
            .put("page", 1)
            .put("pagesize", limit.coerceIn(1, 30))
            .put("key", paramsKey(clientTime))
            .put("special_recommend", specialRecommend)
            .put("req_multi", 1)
            .put("retrun_min", 5)
            .put("return_special_falg", 1)
        val response = requests.post(
            path = "/v2/special_recommend",
            body = body,
            headers = mapOf("x-router" to "specialrec.service.kugou.com"),
        )
        return flattenObjects(response)
            .mapNotNull(::parsePlaylist)
            .distinctBy { it.id.value }
            .take(limit.coerceAtLeast(1))
    }

    private fun newSongs(limit: Int): List<MusicTrack> {
        val session = sessionProvider()
        val response = requests.post(
            path = "/musicadservice/container/v1/newsong_publish",
            body = JSONObject()
                .put("rank_id", 21608)
                .put("userid", session.userId)
                .put("page", 1)
                .put("pagesize", limit.coerceIn(1, 30))
                .put("tags", JSONArray()),
        )
        return flattenObjects(response)
            .mapNotNull(::parseTrack)
            .distinctBy { it.id.value }
            .take(limit.coerceAtLeast(1))
    }

    private fun rankings(limit: Int): List<MusicRankingSummary> {
        val response = requests.get(
            path = "/mobileservice/api/v5/rank/rec_rank_list",
        )
        return flattenObjects(response)
            .mapNotNull(::parseRanking)
            .distinctBy { it.id.value }
            .take(limit.coerceAtLeast(1))
    }

    private fun parsePlaylist(item: JSONObject): MusicPlaylistSummary? {
        // Song objects also have generic id/name fields; reject them first.
        if (firstString(item, "hash", "Hash", "FileHash", "filehash").isNotBlank()) return null
        val strongId = firstString(
            item,
            "global_collection_id",
            "global_specialid",
            "specialid",
            "special_id",
            "playlist_id",
            "listid",
            "list_id",
        )
        val numericStrongId = firstLong(
            item,
            "global_collection_id",
            "global_specialid",
            "specialid",
            "special_id",
            "playlist_id",
            "listid",
            "list_id",
        ).takeIf { it > 0 }?.toString().orEmpty()
        val id = strongId.ifBlank { numericStrongId }.ifBlank {
            // Some user-list responses expose only id; require a playlist-like title/cover too.
            val genericTitle = firstString(item, "specialname", "listname", "playlist_name")
            if (genericTitle.isBlank()) "" else firstString(item, "id")
                .ifBlank { firstLong(item, "id").takeIf { it > 0 }?.toString().orEmpty() }
        }
        if (id.isBlank()) return null
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
            id = MusicResourceId(MusicSource.Kugou, id),
            title = title,
            artworkUrl = normalizeArtwork(
                firstString(item, "imgurl", "img_url", "image", "pic", "cover", "cover_url", "sizable_cover"),
            ),
            creatorName = firstString(item, "nickname", "nick_name", "username", "author_name")
                .takeIf(String::isNotBlank),
            description = firstString(item, "intro", "description", "desc").takeIf(String::isNotBlank),
            trackCount = firstLong(item, "song_count", "songcount", "song_num", "count")
                .takeIf { it >= 0 }?.toInt(),
            playCount = firstLong(item, "play_count", "playcount", "listennum", "heat")
                .takeIf { it >= 0 },
        )
    }

    private fun parseRanking(item: JSONObject): MusicRankingSummary? {
        val id = firstString(item, "rankid", "rank_id")
            .ifBlank { firstLong(item, "rankid", "rank_id").takeIf { it > 0 }?.toString().orEmpty() }
        if (id.isBlank()) return null
        val title = firstString(item, "rankname", "rank_name", "name", "title")
        if (title.isBlank()) return null
        return MusicRankingSummary(
            id = MusicResourceId(MusicSource.Kugou, id),
            title = title,
            artworkUrl = normalizeArtwork(firstString(item, "imgurl", "img_url", "image", "banner7url", "cover")),
            subtitle = firstString(item, "update_frequency", "updateFrequency", "intro", "description")
                .takeIf(String::isNotBlank),
        )
    }

    private fun parseTrack(item: JSONObject): MusicTrack? {
        val hash = firstString(item, "FileHash", "Hash", "hash", "filehash").uppercase()
        if (hash.isBlank()) return null
        val (title, singerName) = recoverKugouTrackText(
            firstString(item, "SongName", "songname", "AudioName", "audio_name", "FileName", "filename", "name"),
            kugouSingerName(item, "SingerName", "singername", "author_name", "AuthorName"),
        )
        if (title.isBlank()) return null
        val albumName = firstString(item, "AlbumName", "album_name", "albumname")
        val albumId = firstString(item, "AlbumID", "album_id", "albumid").takeIf(String::isNotBlank)
        val albumAudioId = firstLong(item, "album_audio_id", "MixSongID", "mixsongid", "AlbumAudioID", "Audioid", "audio_id")
            .takeIf { it > 0 }
        val artwork = normalizeArtwork(firstString(item, "Image", "image", "img", "album_img", "sizable_cover"))
        val durationSeconds = firstLong(item, "Duration", "duration", "time_length").takeIf { it > 0 }
        val artists = singerName
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
            durationMs = durationSeconds?.times(1_000L),
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
        flattenObjects(root).forEach { objectValue ->
            val found = firstLong(objectValue, *keys)
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

    private fun normalizeArtwork(value: String): String? = value
        .trim()
        .takeIf(String::isNotBlank)
        ?.replace("{size}", "400")
        ?.let { if (it.startsWith("http://", true)) "https://${it.substringAfter("://")}" else it }

    /** MakcRe/KuGouMusicApi signParamsKey(clienttime). */
    private fun paramsKey(clientTime: String): String = md5Hex(
        "${KugouRequestClient.AppId}$AndroidSignatureSalt${KugouRequestClient.ClientVersion}$clientTime",
    )

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val AndroidSignatureSalt = "OIlwieks28dk2k092lksi2UIkp"
    }
}
