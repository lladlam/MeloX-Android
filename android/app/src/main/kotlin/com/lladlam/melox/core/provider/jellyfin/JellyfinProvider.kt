package com.lladlam.melox.core.provider.jellyfin

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicAlbumDetail
import com.lladlam.melox.core.music.model.MusicAlbumSummary
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicArtistDetail
import com.lladlam.melox.core.music.model.MusicArtistSummary
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicPlaylistDetail
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.model.TrackAvailability
import com.lladlam.melox.core.music.provider.MusicCapability
import com.lladlam.melox.core.music.provider.MusicProvider
import com.lladlam.melox.core.music.provider.PlaybackCapability
import com.lladlam.melox.core.music.provider.SearchCapability
import com.lladlam.melox.core.music.provider.CatalogSearchCapability
import com.lladlam.melox.core.music.provider.AlbumCapability
import com.lladlam.melox.core.music.provider.ArtistCapability
import com.lladlam.melox.core.music.provider.PlaylistCapability
import com.lladlam.melox.core.music.provider.UserLibraryCapability
import com.lladlam.melox.core.music.provider.FavoriteCapability
import com.lladlam.melox.core.music.provider.PlaylistWriteCapability
import com.lladlam.melox.core.music.provider.PlaylistSyncCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject

class JellyfinProvider(
    private val sessionProvider: () -> JellyfinSession,
    httpClient: OkHttpClient,
) : MusicProvider, SearchCapability, PlaybackCapability, CatalogSearchCapability,
    AlbumCapability, ArtistCapability, PlaylistCapability, UserLibraryCapability, FavoriteCapability,
    PlaylistWriteCapability, PlaylistSyncCapability {
    private val api = JellyfinApiClient(httpClient)

    override val source = MusicSource.Jellyfin
    override val displayName = source.displayName
    override val capabilities = setOf(
        MusicCapability.Search,
        MusicCapability.Playback,
        MusicCapability.Library,
        MusicCapability.Albums,
        MusicCapability.Artists,
        MusicCapability.Playlists,
        MusicCapability.Favorites,
    )

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> =
        withContext(Dispatchers.IO) {
            val session = requireSession()
            val size = pageSize.coerceIn(1, 100)
            val json = api.get(
                session,
                "/Users/${session.userId}/Items",
                mapOf(
                    "IncludeItemTypes" to "Audio",
                    "Recursive" to "true",
                    "SearchTerm" to query,
                    "StartIndex" to ((page - 1).coerceAtLeast(0) * size).toString(),
                    "Limit" to size.toString(),
                    "SortBy" to "SortName",
                ),
            )
            val items = json.optJSONArray("Items") ?: return@withContext MusicPage(emptyList(), page, size, 0L)
            val tracks = buildList {
                for (index in 0 until items.length()) items.optJSONObject(index)?.let { add(toTrack(it)) }
            }
            MusicPage(tracks, page, size, json.optLong("TotalRecordCount").takeIf { it > 0L })
        }

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution =
        withContext(Dispatchers.IO) {
            val session = requireSession()
            PlaybackResolution.Playable(
                url = "${session.baseUrl}/Audio/${track.id.value}/universal",
                requestHeaders = mapOf("X-Emby-Token" to session.accessToken),
                requestedQuality = quality,
                actualQuality = quality,
                format = "audio",
            )
        }

    override suspend fun searchAlbums(query: String, page: Int, pageSize: Int): MusicPage<MusicAlbumSummary> =
        withContext(Dispatchers.IO) { itemPage("MusicAlbum", query, page, pageSize) { toAlbum(it) } }

    override suspend fun searchArtists(query: String, page: Int, pageSize: Int): MusicPage<MusicArtistSummary> =
        withContext(Dispatchers.IO) { itemPage("MusicArtist", query, page, pageSize) { toArtist(it) } }

    override suspend fun searchPlaylists(query: String, page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        withContext(Dispatchers.IO) { itemPage("Playlist", query, page, pageSize) { toPlaylist(it) } }

    override suspend fun accountSummary() = sessionProvider().takeIf { it.isLoggedIn }?.let {
        com.lladlam.melox.core.music.model.MusicAccountSummary(source, it.userId, it.userName, subtitle = it.serverUrl)
    }

    override suspend fun userPlaylists(page: Int, pageSize: Int) =
        withContext(Dispatchers.IO) { itemPage("Playlist", "", page, pageSize) { toPlaylist(it) } }

    override suspend fun playlistDetail(playlist: MusicPlaylistSummary, page: Int, pageSize: Int): MusicPlaylistDetail =
        withContext(Dispatchers.IO) {
            val session = requireSession()
            val json = api.get(session, "/Playlists/${playlist.id.value}/Items", mapOf(
                "UserId" to session.userId,
                "StartIndex" to ((page - 1).coerceAtLeast(0) * pageSize).toString(),
                "Limit" to pageSize.toString(),
            ))
            val tracks = json.optJSONArray("Items")?.let { array -> buildList {
                for (index in 0 until array.length()) array.optJSONObject(index)?.let { add(toTrack(it)) }
            } } ?: emptyList()
            MusicPlaylistDetail(playlist, tracks, json.optLong("TotalRecordCount").takeIf { it > 0 })
        }

    override suspend fun albumDetail(album: MusicAlbumSummary, page: Int, pageSize: Int): MusicAlbumDetail =
        withContext(Dispatchers.IO) {
            val tracks = queryTracks(mapOf("ParentId" to album.id.value), page, pageSize)
            MusicAlbumDetail(album, tracks, tracks.size.toLong())
        }

    override suspend fun artistDetail(artist: MusicArtistSummary, page: Int, pageSize: Int): MusicArtistDetail =
        withContext(Dispatchers.IO) {
            val tracks = queryTracks(mapOf("ArtistIds" to artist.id.value), page, pageSize)
            MusicArtistDetail(artist, tracks, tracks.size.toLong())
        }

    override suspend fun setFavorite(track: MusicTrack, favorite: Boolean) {
        withContext(Dispatchers.IO) {
            val session = requireSession()
            if (favorite) api.post(session, "/Users/${session.userId}/FavoriteItems/${track.id.value}")
            else api.delete(session, "/Users/${session.userId}/FavoriteItems/${track.id.value}")
        }
    }

    override suspend fun writablePlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        userPlaylists(page, pageSize)

    override suspend fun addTrackToPlaylist(track: MusicTrack, playlist: MusicPlaylistSummary) {
        withContext(Dispatchers.IO) {
            api.post(requireSession(), "/Playlists/${playlist.id.value}/Items?Ids=${track.id.value}")
        }
    }

    override suspend fun createPlaylist(name: String): MusicPlaylistSummary = withContext(Dispatchers.IO) {
        val session = requireSession()
        val json = api.post(session, "/Playlists?Name=${java.net.URLEncoder.encode(name, "UTF-8")}")
        toPlaylist(json)
    }

    override suspend fun renamePlaylist(playlist: MusicPlaylistSummary, name: String) {
        withContext(Dispatchers.IO) {
            api.post(requireSession(), "/Playlists/${playlist.id.value}", JSONObject().put("Name", name))
        }
    }

    override suspend fun removeTrackFromPlaylist(track: MusicTrack, playlist: MusicPlaylistSummary) {
        withContext(Dispatchers.IO) {
            api.delete(requireSession(), "/Playlists/${playlist.id.value}/Items?EntryIds=${track.id.value}")
        }
    }

    override suspend fun reorderPlaylistTrack(playlist: MusicPlaylistSummary, track: MusicTrack, newIndex: Int) {
        withContext(Dispatchers.IO) {
            api.post(requireSession(), "/Playlists/${playlist.id.value}/Items/${track.id.value}/Move/$newIndex")
        }
    }

    private fun requireSession(): JellyfinSession = sessionProvider().takeIf { it.isLoggedIn }
        ?: throw IllegalStateException("Jellyfin 尚未登录")

    private fun <T> itemPage(type: String, query: String, page: Int, pageSize: Int, map: (JSONObject) -> T): MusicPage<T> {
        val session = requireSession()
        val size = pageSize.coerceIn(1, 100)
        val json = api.get(session, "/Users/${session.userId}/Items", mapOf(
            "IncludeItemTypes" to type, "Recursive" to "true", "SearchTerm" to query,
            "StartIndex" to ((page - 1).coerceAtLeast(0) * size).toString(), "Limit" to size.toString(),
        ))
        val array = json.optJSONArray("Items")
        val items = if (array == null) emptyList() else buildList {
            for (index in 0 until array.length()) array.optJSONObject(index)?.let { add(map(it)) }
        }
        return MusicPage(items, page, size, json.optLong("TotalRecordCount").takeIf { it > 0 })
    }

    private fun queryTracks(filters: Map<String, String>, page: Int, pageSize: Int): List<MusicTrack> {
        val session = requireSession()
        val size = pageSize.coerceIn(1, 100)
        val json = api.get(session, "/Users/${session.userId}/Items", filters + mapOf(
            "IncludeItemTypes" to "Audio", "Recursive" to "true",
            "StartIndex" to ((page - 1).coerceAtLeast(0) * size).toString(), "Limit" to size.toString(),
        ))
        val array = json.optJSONArray("Items") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) array.optJSONObject(index)?.let { add(toTrack(it)) }
        }
    }

    private fun toTrack(item: JSONObject): MusicTrack {
        val itemId = item.optString("Id")
        val artistItems = item.optJSONArray("ArtistItems")
        val artist = item.optString("AlbumArtist").ifBlank { artistItems?.optJSONObject(0)?.optString("Name").orEmpty() }
        val artistName = artist.ifBlank { "未知歌手" }
        val album = item.optString("Album").takeIf(String::isNotBlank)
        val session = sessionProvider()
        val artwork = if (session.isLoggedIn) "${session.baseUrl}/Items/$itemId/Images/Primary?api_key=${session.accessToken}" else null
        return MusicTrack(
            id = MusicResourceId(MusicSource.Jellyfin, itemId),
            title = item.optString("Name").ifBlank { itemId },
            artists = listOf(MusicArtistRef(name = artistName)),
            album = album?.let { MusicAlbumRef(name = it) },
            artworkUrl = artwork,
            durationMs = item.optLong("RunTimeTicks").takeIf { it > 0L }?.div(10_000L),
            availability = TrackAvailability.Playable,
        )
    }

    private fun toAlbum(item: JSONObject) = MusicAlbumSummary(
        MusicResourceId(source, item.optString("Id")), item.optString("Name"), imageUrl(item),
        listOf(MusicArtistRef(name = item.optString("AlbumArtist").ifBlank { "未知歌手" })),
        item.optString("PremiereDate").takeIf(String::isNotBlank), item.optLong("ChildCount").takeIf { it > 0 },
    )

    private fun toArtist(item: JSONObject) = MusicArtistSummary(
        MusicResourceId(source, item.optString("Id")), item.optString("Name"), imageUrl(item),
    )

    private fun toPlaylist(item: JSONObject) = MusicPlaylistSummary(
        MusicResourceId(source, item.optString("Id")), item.optString("Name"), imageUrl(item),
        trackCount = item.optInt("ChildCount").takeIf { it > 0 },
    )

    private fun imageUrl(item: JSONObject): String? = sessionProvider().takeIf { it.isLoggedIn }?.let {
        "${it.baseUrl}/Items/${item.optString("Id")}/Images/Primary?api_key=${it.accessToken}"
    }
}
