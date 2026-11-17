/*
 * Sonify library navigation/data models, MeloX-specific.
 *
 * These are presentation-only adapters over MeloX data:
 *   SearchSong / NeteasePlaylistSummary / MeloXAlbumSummary / MusicAlbumSummary
 *   / MusicArtistSummary.
 * No Sonify KugouRepository, DiscoveryObject, MusicLibrary or SettingsLibrary
 * objects are imported.
 */
package com.lladlam.melox.ui.sonify.library

import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.model.MusicAlbumSummary
import com.lladlam.melox.core.music.model.MusicArtistSummary
import com.lladlam.melox.core.network.MeloXAlbumSummary

internal data class SonifyAlbumTarget(
    val title: String,
    val artistText: String,
    val artworkUrl: String?,
    val neteaseAlbumId: Long? = null,
    val providerAlbum: MusicAlbumSummary? = null,
    val localSongs: List<SearchSong> = emptyList(),
    val trackCount: Int = localSongs.size,
)

internal data class SonifyAlbumData(
    val title: String,
    val artistText: String,
    val artworkUrl: String?,
    val description: String?,
    val songs: List<SearchSong>,
    val trackNumbers: List<Int?> = emptyList(),
    val subscribed: Boolean? = null,
)

internal data class SonifyArtistData(
    val artist: com.lladlam.melox.core.network.MeloXArtistDetail,
    val allSongs: List<SearchSong>,
    val canFollow: Boolean,
)

internal data class SonifyPlaylistData(
    val name: String,
    val artworkUrl: String?,
    val description: String?,
    val songs: List<SearchSong>,
    val trackCount: Int = songs.size,
    val creatorName: String? = null,
    val subscribed: Boolean? = null,
)

internal data class SonifyAlbumCard(
    val title: String,
    val artistText: String,
    val artworkUrl: String?,
    val songCount: Int,
    val target: SonifyAlbumTarget,
)

internal data class SonifyArtistTarget(
    val name: String,
    val artworkUrl: String? = null,
    val neteaseArtistId: Long? = null,
    val providerArtist: MusicArtistSummary? = null,
)

internal data class SonifyLocalAlbum(
    val name: String,
    val artistText: String,
    val artworkUrl: String?,
    val songs: List<SearchSong>,
)

internal data class SonifyLocalArtist(
    val name: String,
    val artworkUrl: String?,
    val songs: List<SearchSong>,
    val neteaseArtistId: Long? = null,
)

internal data class SonifyPlaylistTarget(
    val summary: NeteasePlaylistSummary,
    val providedSongs: List<SearchSong>? = null,
    val isLikedSongs: Boolean = false,
)

internal sealed interface SonifyLibraryRoute {
    data object Hub : SonifyLibraryRoute
    data class Songs(
        val title: String,
        val songs: List<SearchSong>,
        val emptyText: String,
    ) : SonifyLibraryRoute

    data object DailyRecommendations : SonifyLibraryRoute
    data object LocalAlbums : SonifyLibraryRoute
    data object CollectedAlbums : SonifyLibraryRoute
    data class Album(val target: SonifyAlbumTarget) : SonifyLibraryRoute
    data object LocalArtists : SonifyLibraryRoute
    data object OnlineArtists : SonifyLibraryRoute
    data class Artist(val target: SonifyArtistTarget) : SonifyLibraryRoute
    data object Playlists : SonifyLibraryRoute
    data object OnlinePlaylists : SonifyLibraryRoute
    data class Playlist(val target: SonifyPlaylistTarget) : SonifyLibraryRoute
    data object FavoriteSongs : SonifyLibraryRoute
}

internal fun MeloXAlbumSummary.asSonifyTarget(): SonifyAlbumTarget = SonifyAlbumTarget(
    title = name,
    artistText = artistText,
    artworkUrl = artworkUrl,
    neteaseAlbumId = id,
)

internal fun MusicAlbumSummary.asSonifyTarget(): SonifyAlbumTarget = SonifyAlbumTarget(
    title = title,
    artistText = artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" },
    artworkUrl = artworkUrl,
    providerAlbum = this,
    trackCount = trackCount?.toInt() ?: 0,
)

internal fun MusicArtistSummary.asSonifyTarget(): SonifyArtistTarget = SonifyArtistTarget(
    name = name,
    artworkUrl = artworkUrl,
    providerArtist = this,
)
