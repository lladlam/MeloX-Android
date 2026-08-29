package com.lladlam.melox.core.provider.kugou

import com.lladlam.melox.core.music.model.MusicAlbumDetail
import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicAlbumSummary
import com.lladlam.melox.core.music.model.MusicArtistDetail
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicArtistSummary
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import java.security.MessageDigest
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Provider-neutral catalog surface backed by the current KuGou Android API shapes.
 *
 * The public MeloX models intentionally keep only common semantics. KuGou-only
 * identifiers (hash / album_audio_id) stay in [ProviderTrackMetadata.Kugou].
 */
internal class KugouCatalogClient(
    private val sessionProvider: () -> KugouSession,
    httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    private val requests = KugouRequestClient(sessionProvider, httpClient)

    suspend fun searchPlaylists(query: String, page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> {
        val result = search("special", query, page, pageSize)
        return MusicPage(
            items = searchItems(result).mapNotNull(::parsePlaylist).distinctBy { it.id.value },
            page = safePage(page),
            pageSize = safeSize(pageSize),
            total = searchTotal(result),
        )
    }

    suspend fun searchAlbums(query: String, page: Int, pageSize: Int): MusicPage<MusicAlbumSummary> {
        val result = search("album", query, page, pageSize)
        return MusicPage(
            items = searchItems(result).mapNotNull(::parseAlbum).distinctBy { it.id.value },
            page = safePage(page),
            pageSize = safeSize(pageSize),
            total = searchTotal(result),
        )
    }

    suspend fun searchArtists(query: String, page: Int, pageSize: Int): MusicPage<MusicArtistSummary> {
        val result = search("author", query, page, pageSize)
        return MusicPage(
            items = searchItems(result).mapNotNull(::parseArtist).distinctBy { it.id.value },
            page = safePage(page),
            pageSize = safeSize(pageSize),
            total = searchTotal(result),
        )
    }

    suspend fun albumDetail(album: MusicAlbumSummary, page: Int, pageSize: Int): MusicAlbumDetail {
        require(album.id.source == MusicSource.Kugou)
        val safePage = safePage(page)
        val safeSize = pageSize.coerceIn(1, 200)
        val headers = mapOf("x-router" to "openapi.kugou.com", "kg-tid" to "255")

        val detail = runCatching {
            requests.post(
                baseUrl = "https://openapi.kugou.com",
                path = "/kmr/v2/albums",
                body = JSONObject()
                    .put("data", JSONArray().put(JSONObject().put("album_id", album.id.value)))
                    .put("is_buy", 0)
                    .put(
                        "fields",
                        "album_id,album_name,publish_date,sizable_cover,intro,language,is_publish,heat,type,quality,authors,exclusive,author_name,trans_param",
                    ),
                headers = headers,
            )
        }.getOrNull()

        val songs = requests.post(
            baseUrl = "https://openapi.kugou.com",
            path = "/v1/album_audio/lite",
            body = JSONObject()
                .put("album_id", album.id.value)
                .put("is_buy", "")
                .put("page", safePage)
                .put("pagesize", safeSize),
            headers = headers,
        )
        val summary = detail?.let(::findFirstObject)?.let(::parseAlbum) ?: album
        val tracks = findTrackObjects(songs).mapNotNull(::parseTrack).distinctBy { it.id.value }
        return MusicAlbumDetail(
            summary = summary,
            tracks = tracks,
            totalTracks = firstLong(findDataObject(songs), "total", "total_count", "totalnum", "count")
                .takeIf { it >= 0 },
        )
    }

    suspend fun artistDetail(artist: MusicArtistSummary, page: Int, pageSize: Int): MusicArtistDetail {
        require(artist.id.source == MusicSource.Kugou)
        val safePage = safePage(page)
        val safeSize = pageSize.coerceIn(1, 200)
        val detail = runCatching {
            requests.post(
                baseUrl = "https://openapi.kugou.com",
                path = "/kmr/v3/author",
                body = JSONObject().put("author_id", artist.id.value),
                headers = mapOf("x-router" to "openapi.kugou.com", "kg-tid" to "36"),
            )
        }.getOrNull()

        val session = sessionProvider()
        val clientTime = (System.currentTimeMillis() / 1_000L).toString()
        val audioBody = JSONObject()
            .put("appid", KugouRequestClient.AppId)
            .put("clientver", KugouRequestClient.ClientVersion)
            .put("mid", session.mid)
            .put("clienttime", clientTime)
            .put("key", signParamsKey(clientTime))
            .put("author_id", artist.id.value)
            .put("pagesize", safeSize)
            .put("page", safePage)
            .put("sort", 1)
            .put("area_code", "all")
        val songs = requests.post(
            baseUrl = "https://openapi.kugou.com",
            path = "/kmr/v1/audio_group/author",
            body = audioBody,
            headers = mapOf("x-router" to "openapi.kugou.com", "kg-tid" to "220"),
        )
        val summary = detail?.let(::findFirstObject)?.let(::parseArtist) ?: artist
        val tracks = findTrackObjects(songs).mapNotNull(::parseTrack).distinctBy { it.id.value }
        return MusicArtistDetail(
            summary = summary,
            tracks = tracks,
            totalTracks = firstLong(findDataObject(songs), "total", "total_count", "totalnum", "count")
                .takeIf { it >= 0 },
        )
    }

    private fun search(type: String, query: String, page: Int, pageSize: Int): JSONObject {
        val normalized = query.trim()
        if (normalized.isEmpty()) return JSONObject().put("data", JSONObject().put("lists", JSONArray()))
        return requests.get(
            path = "/v1/search/$type",
            params = mapOf(
                "albumhide" to "0",
                "iscorrection" to "1",
                "keyword" to normalized,
                "nocollect" to "0",
                "page" to safePage(page).toString(),
                "pagesize" to safeSize(pageSize).toString(),
                "platform" to "AndroidFilter",
            ),
            headers = mapOf("x-router" to "complexsearch.kugou.com"),
        )
    }

    private fun searchItems(root: JSONObject): List<JSONObject> {
        val data = findDataObject(root)
        val direct = firstArray(data, "lists", "list", "info", "items", "data")
        if (direct != null) return direct.objects()
        return flattenObjects(data).filter { value ->
            hasAny(value, "album_id", "albumid", "author_id", "specialid", "special_id", "id")
        }
    }

    private fun searchTotal(root: JSONObject): Long? =
        firstLong(findDataObject(root), "total", "total_count", "totalnum", "count").takeIf { it >= 0 }

    private fun parsePlaylist(item: JSONObject): MusicPlaylistSummary? {
        val id = firstString(item, "specialid", "special_id", "specialId", "id")
            .ifBlank { firstLong(item, "specialid", "special_id", "id").takeIf { it > 0 }?.toString().orEmpty() }
        if (id.isBlank()) return null
        return MusicPlaylistSummary(
            id = MusicResourceId(MusicSource.Kugou, id),
            title = firstString(item, "specialname", "special_name", "name", "title").ifBlank { "酷狗歌单" },
            artworkUrl = normalizeKugouArtworkUrl(firstString(item, "img", "imgurl", "image", "sizable_cover", "cover")),
            creatorName = firstString(item, "nickname", "author_name", "username", "creator").takeIf(String::isNotBlank),
            description = firstString(item, "intro", "desc", "description").takeIf(String::isNotBlank),
            trackCount = firstLong(item, "songcount", "song_count", "count").takeIf { it >= 0 }?.toInt(),
            playCount = firstLong(item, "playcount", "play_count", "heat").takeIf { it >= 0 },
        )
    }

    private fun parseAlbum(item: JSONObject): MusicAlbumSummary? {
        val id = firstString(item, "album_id", "albumid", "albumId", "id")
            .ifBlank { firstLong(item, "album_id", "albumid", "id").takeIf { it > 0 }?.toString().orEmpty() }
        if (id.isBlank()) return null
        val artistName = firstString(item, "author_name", "singername", "singer_name", "artist_name")
        val artistId = firstString(item, "author_id", "singer_id", "artist_id")
        return MusicAlbumSummary(
            id = MusicResourceId(MusicSource.Kugou, id),
            title = firstString(item, "album_name", "albumname", "name", "title").ifBlank { "酷狗专辑" },
            artworkUrl = normalizeKugouArtworkUrl(firstString(item, "sizable_cover", "img", "imgurl", "image", "cover")),
            artists = artistName.takeIf(String::isNotBlank)?.let {
                listOf(
                    MusicArtistRef(
                        id = artistId.takeIf(String::isNotBlank)?.let { value -> MusicResourceId(MusicSource.Kugou, value) },
                        name = it,
                    ),
                )
            }.orEmpty(),
            releaseDate = firstString(item, "publish_date", "publish_time", "publishtime", "release_date").takeIf(String::isNotBlank),
            trackCount = firstLong(item, "songcount", "song_count", "count", "total").takeIf { it >= 0 },
        )
    }

    private fun parseArtist(item: JSONObject): MusicArtistSummary? {
        val id = firstString(item, "author_id", "authorid", "singer_id", "singerid", "id")
            .ifBlank { firstLong(item, "author_id", "authorid", "singer_id", "id").takeIf { it > 0 }?.toString().orEmpty() }
        if (id.isBlank()) return null
        val name = firstString(item, "author_name", "singername", "singer_name", "name", "title")
        if (name.isBlank()) return null
        return MusicArtistSummary(
            id = MusicResourceId(MusicSource.Kugou, id),
            name = name,
            artworkUrl = normalizeKugouArtworkUrl(firstString(item, "sizable_avatar", "avatar", "img", "imgurl", "image")),
            description = firstString(item, "intro", "desc", "description").takeIf(String::isNotBlank),
            songCount = firstLong(item, "songcount", "song_count", "audio_count", "music_count").takeIf { it >= 0 },
            albumCount = firstLong(item, "albumcount", "album_count").takeIf { it >= 0 },
        )
    }

    private fun parseTrack(item: JSONObject): MusicTrack? {
        val hash = firstString(item, "hash", "Hash", "FileHash", "filehash", "audio_hash").uppercase()
        if (hash.isBlank()) return null
        val (title, singer) = recoverKugouTrackText(
            firstString(item, "audio_name", "AudioName", "SongName", "songname", "name", "FileName"),
            kugouSingerName(item, "author_name", "SingerName", "singername", "singer_name"),
        ).let { (value, artist) -> (value.ifBlank { "未知歌曲" }) to artist }
        val artists = singer
            .split(Regex("\\s*(?:、|/|&|,|;|；)\\s*"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .ifEmpty { listOf("未知歌手") }
            .map { MusicArtistRef(name = it) }
        val albumName = firstString(item, "album_name", "AlbumName", "albumname")
        val albumId = firstString(item, "album_id", "AlbumID", "albumid").takeIf(String::isNotBlank)
        val artwork = normalizeKugouArtworkUrl(firstString(item, "sizable_cover", "Image", "image", "img", "album_img"))
        val durationRaw = firstLong(item, "duration", "Duration", "time_length")
        val durationMs = durationRaw.takeIf { it > 0 }?.let { if (it > 100_000L) it else it * 1_000L }
        val albumAudioId = firstLong(item, "album_audio_id", "MixSongID", "mixsongid", "AlbumAudioID", "audio_id", "audioid")
            .takeIf { it > 0 }
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
            durationMs = durationMs,
            providerMetadata = ProviderTrackMetadata.Kugou(
                hash = hash,
                albumAudioId = albumAudioId,
                albumId = albumId,
            ),
        )
    }

    private fun findTrackObjects(root: JSONObject): List<JSONObject> {
        val data = findDataObject(root)
        val direct = firstArray(data, "songs", "audios", "audio", "lists", "list", "info", "items")
        if (direct != null) return direct.objects()
        return flattenObjects(data).filter { hasAny(it, "hash", "Hash", "FileHash", "audio_hash") }
    }

    private fun findDataObject(root: JSONObject): JSONObject =
        root.optJSONObject("data") ?: root.optJSONObject("result") ?: root

    private fun findFirstObject(root: JSONObject): JSONObject? {
        val data = findDataObject(root)
        firstArray(data, "data", "lists", "list", "info", "items")?.optJSONObject(0)?.let { return it }
        return flattenObjects(data).firstOrNull { it.length() > 1 }
    }

    private fun firstArray(value: JSONObject, vararg keys: String): JSONArray? =
        keys.asSequence().mapNotNull(value::optJSONArray).firstOrNull()

    private fun flattenObjects(value: Any?): List<JSONObject> = when (value) {
        is JSONObject -> buildList {
            add(value)
            value.keys().forEach { key -> addAll(flattenObjects(value.opt(key))) }
        }
        is JSONArray -> buildList {
            for (index in 0 until value.length()) addAll(flattenObjects(value.opt(index)))
        }
        else -> emptyList()
    }

    private fun JSONArray.objects(): List<JSONObject> = buildList {
        for (index in 0 until length()) optJSONObject(index)?.let(::add)
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

    private fun hasAny(value: JSONObject, vararg keys: String): Boolean = keys.any(value::has)

    private fun safePage(page: Int): Int = page.coerceAtLeast(1)
    private fun safeSize(pageSize: Int): Int = pageSize.coerceIn(1, 50)

    private fun signParamsKey(clientTime: String): String = md5Hex(
        "${KugouRequestClient.AppId}$AndroidSalt${KugouRequestClient.ClientVersion}$clientTime",
    )

    private fun md5Hex(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val AndroidSalt = "OIlwieks28dk2k092lksi2UIkp"
    }
}
