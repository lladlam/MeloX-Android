package com.lladlam.melox.core.network

import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.model.SearchSong
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

data class MeloXAlbumSummary(
    val id: Long,
    val name: String,
    val artworkUrl: String?,
    val artistText: String,
    val type: String?,
)

data class MeloXAlbumDetail(
    val album: MeloXAlbumSummary,
    val description: String?,
    val songs: List<SearchSong>,
    val subscribed: Boolean?,
)

data class MeloXArtistDetail(
    val id: Long,
    val name: String,
    val artworkUrl: String?,
    val coverUrl: String?,
    val aliases: List<String>,
    val description: String?,
    val musicSize: Int,
    val albumSize: Int,
    val mvSize: Int,
    val followed: Boolean?,
    val hotSongs: List<SearchSong>,
    val albums: List<MeloXAlbumSummary>,
)

class NeteaseCollectionDetailsClient(
    cookieProvider: () -> String,
    httpClient: OkHttpClient = MeloXHttpClient.shared,
) {
    private val cookieProvider = cookieProvider
    private val eapi = NeteaseAuthenticatedEapi(cookieProvider, httpClient)
    private val weapi = NeteaseAuthenticatedWeapi(cookieProvider, httpClient)

    suspend fun albumDetail(id: Long): MeloXAlbumDetail = withContext(Dispatchers.IO) {
        val logged = NeteaseSessionStore.containsMusicU(cookieProvider())
        val response = eapi.post("/api/v1/album/$id", authenticated = logged)
        val value = response.optJSONObject("album") ?: throw IOException("网易云没有返回专辑信息")
        val album = parseAlbum(value) ?: throw IOException("无法解析专辑")
        val songs = parseSongs(response.optJSONArray("songs"))
        val subscribed = if (logged) runCatching {
            val data = JSONObject().put("id", id)
            val dynamic = try {
                weapi.post("/api/album/detail/dynamic", data)
            } catch (error: IOException) {
                if (!error.message.orEmpty().contains("空响应")) throw error
                eapi.post("/api/album/detail/dynamic", data)
            }
            dynamic.optBoolean("isSub", false)
        }.getOrNull() else null
        MeloXAlbumDetail(
            album = album,
            description = value.optString("description").takeIf(String::isNotBlank)
                ?: value.optString("briefDesc").takeIf(String::isNotBlank),
            songs = songs,
            subscribed = subscribed,
        )
    }

    suspend fun setAlbumSubscribed(id: Long, subscribed: Boolean) = withContext(Dispatchers.IO) {
        val path = if (subscribed) "/api/album/sub" else "/api/album/unsub"
        val data = JSONObject().put("id", id)
        try {
            weapi.post(path, data)
        } catch (error: IOException) {
            if (!error.message.orEmpty().contains("空响应")) throw error
            eapi.post(path, data)
        }
        Unit
    }

    suspend fun artistDetail(id: Long): MeloXArtistDetail = withContext(Dispatchers.IO) {
        val logged = NeteaseSessionStore.containsMusicU(cookieProvider())
        val songsResponse = eapi.post("/api/v1/artist/$id", authenticated = logged)
        val legacyArtist = songsResponse.optJSONObject("artist")
            ?: throw IOException("网易云没有返回歌手信息")
        val headData = runCatching {
            eapi.post(
                "/api/artist/head/info/get",
                JSONObject().put("id", id.toString()),
                logged,
            ).optJSONObject("data")
        }.getOrNull()
        val richArtist = headData?.optJSONObject("artist")
        val artist = richArtist ?: legacyArtist
        val albumsResponse = eapi.post(
            "/api/artist/albums/$id",
            JSONObject().put("limit", 100).put("offset", 0).put("total", true),
            logged,
        )
        val aliasesJson = artist.optJSONArray("alias") ?: artist.optJSONArray("transNames") ?: JSONArray()
        val aliases = buildList {
            for (index in 0 until aliasesJson.length()) {
                aliasesJson.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
        val albumsJson = albumsResponse.optJSONArray("hotAlbums") ?: JSONArray()
        val albums = buildList {
            for (index in 0 until albumsJson.length()) parseAlbum(albumsJson.optJSONObject(index))?.let(::add)
        }
        MeloXArtistDetail(
            id = artist.optLong("id", id),
            name = artist.optString("name").ifBlank { "未知歌手" },
            artworkUrl = secure(
                artist.optString("avatar").takeIf(String::isNotBlank)
                    ?: artist.optString("picUrl").takeIf(String::isNotBlank)
                    ?: artist.optString("img1v1Url").takeIf(String::isNotBlank),
            ),
            coverUrl = secure(
                artist.optString("cover").takeIf(String::isNotBlank)
                    ?: headData?.optJSONObject("user")?.optString("backgroundUrl")?.takeIf(String::isNotBlank)
                    ?: artist.optString("picUrl").takeIf(String::isNotBlank),
            ),
            aliases = aliases,
            description = artist.optString("briefDesc").takeIf(String::isNotBlank),
            musicSize = artist.optInt("musicSize", songsResponse.optJSONArray("hotSongs")?.length() ?: 0),
            albumSize = artist.optInt("albumSize", albums.size),
            mvSize = artist.optInt("mvSize", 0),
            followed = headData?.optJSONObject("user")?.takeIf { it.has("followed") }?.optBoolean("followed"),
            hotSongs = parseSongs(songsResponse.optJSONArray("hotSongs")),
            albums = albums,
        )
    }

    suspend fun setArtistFollowed(id: Long, followed: Boolean) = withContext(Dispatchers.IO) {
        val path = if (followed) "/api/artist/sub" else "/api/artist/unsub"
        eapi.post(
            path,
            JSONObject().put("artistId", id).put("artistIds", "[$id]"),
            authenticated = true,
        )
        Unit
    }

    private fun parseSongs(values: JSONArray?): List<SearchSong> = buildList {
        val songs = values ?: JSONArray()
        for (index in 0 until songs.length()) parseSong(songs.optJSONObject(index))?.let(::add)
    }

    private fun parseAlbum(value: JSONObject?): MeloXAlbumSummary? {
        value ?: return null
        val id = value.optLong("id", -1L)
        if (id <= 0L) return null
        val artistsJson = value.optJSONArray("artists") ?: value.optJSONArray("ar") ?: JSONArray()
        val artists = buildList {
            for (index in 0 until artistsJson.length()) {
                artistsJson.optJSONObject(index)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
            }
        }.joinToString(" / ")
        return MeloXAlbumSummary(
            id = id,
            name = value.optString("name").ifBlank { "未命名专辑" },
            artworkUrl = secure(
                value.optString("picUrl").takeIf(String::isNotBlank)
                    ?: value.optString("blurPicUrl").takeIf(String::isNotBlank),
            ),
            artistText = artists.ifBlank {
                value.optJSONObject("artist")?.optString("name").orEmpty().ifBlank { "未知歌手" }
            },
            type = value.optString("type").takeIf(String::isNotBlank),
        )
    }

    private fun parseSong(value: JSONObject?): SearchSong? {
        value ?: return null
        val id = value.optLong("id", -1L)
        if (id <= 0L) return null
        val artistsJson = value.optJSONArray("ar") ?: value.optJSONArray("artists") ?: JSONArray()
        val artists = buildList {
            for (index in 0 until artistsJson.length()) {
                artistsJson.optJSONObject(index)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
            }
        }.joinToString(" / ")
        val album = value.optJSONObject("al") ?: value.optJSONObject("album")
        return SearchSong(
            id = id,
            name = value.optString("name").ifBlank { "未知歌曲" },
            artists = artists.ifBlank { "未知歌手" },
            album = album?.optString("name").orEmpty(),
            artworkUrl = secure(
                album?.optString("picUrl")?.takeIf(String::isNotBlank)
                    ?: album?.optString("blurPicUrl")?.takeIf(String::isNotBlank),
            ),
            durationMs = value.optLong("dt", value.optLong("duration", 0L)).coerceAtLeast(0L),
        )
    }

    private fun secure(value: String?): String? = value?.let {
        if (it.startsWith("http://", true)) "https://${it.substringAfter("://")}" else it
    }
}
