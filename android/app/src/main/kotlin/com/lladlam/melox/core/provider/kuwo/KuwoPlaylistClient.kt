package com.lladlam.melox.core.provider.kuwo

import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicPlaylistDetail
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import java.io.IOException
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/** Kuwo cloud playlists documented by Kuwo's native Android client. */
class KuwoPlaylistClient(
    private val sessionProvider: () -> KuwoSession,
    httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    private val requests = KuwoRequestClient(httpClient)

    fun userPlaylists(page: Int = 1, pageSize: Int = 30): MusicPage<MusicPlaylistSummary> {
        val session = requireSession()
        val response = requests.get(
            baseUrl = "http://nplserver.kuwo.cn",
            path = "/pl.svc",
            params = authParams(session) + mapOf(
                "op" to "getlistbyuid",
                "newver" to "3",
                "bigid" to "1",
                "uid" to session.userId,
                "loginUid" to session.userId,
            ),
        )
        val items = findArray(response, "data", "playlist", "playlists", "pl", "list") ?: JSONArray()
        val all = buildList {
            for (index in 0 until items.length()) {
                parseSummary(items.optJSONObject(index))?.let(::add)
            }
        }
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 100)
        val start = (safePage - 1) * safeSize
        return MusicPage(all.drop(start).take(safeSize), safePage, safeSize, all.size.toLong())
    }

    fun detail(playlist: MusicPlaylistSummary, page: Int = 1, pageSize: Int = 100): MusicPlaylistDetail {
        require(playlist.id.source == MusicSource.Kuwo)
        val session = requireSession()
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 200)
        val response = requests.get(
            baseUrl = "http://mobilist.kuwo.cn",
            path = "/list.s",
            params = authParams(session) + mapOf(
                "user" to session.userId,
                "type" to "songlist",
                "id" to playlist.id.value,
                "pn" to (safePage - 1).toString(),
                "rn" to safeSize.toString(),
                "corp" to "kuwo",
                "newver" to "3",
                "hasmv" to "1",
                "hasinner" to "1",
                "apiv" to "0",
                "isnew" to "2",
                "newcate" to "1",
                "isvip" to "0",
            ),
        )
        val songs = findArray(response, "musiclist", "abslist", "songlist", "data", "list") ?: JSONArray()
        val tracks = buildList {
            for (index in 0 until songs.length()) parseTrack(songs.optJSONObject(index))?.let(::add)
        }
        val total = firstLong(response, "total", "totalnum", "songnum", "total_song_num")
            .takeIf { it >= 0 } ?: playlist.trackCount?.toLong()
        return MusicPlaylistDetail(playlist, tracks, total)
    }

    private fun requireSession(): KuwoSession = sessionProvider().takeIf(KuwoSession::isLoggedIn)
        ?: throw IOException("请先登录酷我音乐")

    private fun authParams(session: KuwoSession): Map<String, String> = mapOf(
        "uid" to session.userId,
        "loginUid" to session.userId,
        "loginSid" to session.token,
        "plat" to "ar",
        "prod" to "kwplayer_ar",
    )

    private fun parseSummary(item: JSONObject?): MusicPlaylistSummary? {
        if (item == null) return null
        val id = firstString(item, "id", "pid", "playlistid", "sourceid", "listid")
        if (id.isBlank()) return null
        return MusicPlaylistSummary(
            id = MusicResourceId(MusicSource.Kuwo, id),
            title = firstString(item, "name", "title", "listname", "playlistname").ifBlank { "酷我歌单" },
            artworkUrl = firstString(item, "pic", "picurl", "img", "album_pic", "logo").toArtworkUrl(),
            creatorName = firstString(item, "username", "creator", "nickname").takeIf(String::isNotBlank),
            description = firstString(item, "desc", "description").takeIf(String::isNotBlank),
            trackCount = firstLong(item, "songnum", "songcount", "count").takeIf { it >= 0 }?.toInt(),
            playCount = firstLong(item, "playnum", "playcount", "listennum").takeIf { it >= 0 },
        )
    }

    private fun parseTrack(item: JSONObject?): MusicTrack? {
        if (item == null) return null
        val mid = firstString(item, "rid", "musicrid", "DC_TARGETID", "id").removePrefix("MUSIC_").toLongOrNull()
            ?: return null
        val title = firstString(item, "name", "songname", "title", "musicname").ifBlank { "未知歌曲" }
        val artistText = firstString(item, "artist", "artistname", "singer", "singername")
        val artists = artistText.split(Regex("\\s*(?:、|/|&|,|;|；)\\s*"))
            .map(String::trim).filter(String::isNotBlank)
            .ifEmpty { listOf("未知歌手") }
            .map { name -> MusicArtistRef(name = name) }
        val artwork = firstString(item, "albumpic", "album_pic", "pic", "picurl").toArtworkUrl()
        val album = firstString(item, "album", "albumname").takeIf(String::isNotBlank)?.let {
            MusicAlbumRef(name = it, artworkUrl = artwork)
        }
        return MusicTrack(
            id = MusicResourceId(MusicSource.Kuwo, mid.toString()),
            title = title,
            artists = artists,
            album = album,
            artworkUrl = artwork,
            durationMs = firstLong(item, "duration", "song_duration").takeIf { it > 0 }?.times(1_000L),
            providerMetadata = ProviderTrackMetadata.Kuwo(mid),
        )
    }

    private fun findArray(root: JSONObject, vararg keys: String): JSONArray? {
        for (key in keys) root.optJSONArray(key)?.let { return it }
        for (key in keys) root.optJSONObject(key)?.let { nested -> findArray(nested, *keys)?.let { return it } }
        return null
    }

    private fun firstString(item: JSONObject, vararg keys: String): String =
        keys.asSequence().map(item::optString).firstOrNull(String::isNotBlank).orEmpty()

    private fun firstLong(item: JSONObject, vararg keys: String): Long =
        keys.asSequence().mapNotNull { key ->
            when (val value = item.opt(key)) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }
        }.firstOrNull() ?: -1L

    private fun String.toArtworkUrl(): String? = trim().takeIf(String::isNotBlank)?.let {
        when {
            it.startsWith("http://") -> "https://${it.substringAfter("://")}"
            it.startsWith("https://") -> it
            it.startsWith("http") -> it
            else -> "https://img1.kuwo.cn/star/albumcover/$it"
        }
    }
}
