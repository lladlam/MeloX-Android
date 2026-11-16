package com.lladlam.melox.ui.sonify

import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.model.MusicAlbumSummary
import com.lladlam.melox.core.network.MeloXArtistDetail
import com.lladlam.melox.core.network.MeloXAlbumSummary

/** MeloX-owned data accepted by the imported Sonify artist UI. */
data class MeloXArtistUiSong(
    val hash: String,
    val name: String,
    val author: String,
    val albumName: String?,
    val durationMs: Long,
    val artworkUrl: String?,
)

data class MeloXArtistUiAlbum(
    val albumId: String,
    val name: String,
    val singerName: String,
    val coverUrl: String,
    val songCount: Int,
    val publishTime: String?,
)

data class MeloXArtistUiDetail(
    val artistId: String,
    val name: String,
    val avatarUrl: String?,
    val coverUrl: String? = null,
    val artworkUrl: String? = null,
    val aliases: List<String> = emptyList(),
    val intro: String?,
    val songCount: Int,
    val albumCount: Int,
    val isFollowed: Boolean,
    val hotSongs: List<MeloXArtistUiSong> = emptyList(),
    val albums: List<MeloXArtistUiAlbum> = emptyList(),
)

/** Everything the copied artist page needs from the host app. */
interface MeloXArtistActions {
    fun play(song: MeloXArtistUiSong, queue: List<MeloXArtistUiSong>)
    fun toggleFollow()
    fun openAlbum(albumId: String)
    fun openHotSongs()
    fun openAlbums()
    fun openAllSongs()
    fun login()
    fun back()
}

fun MeloXArtistDetail.toSonifyUi(
    followed: Boolean?,
    songs: List<SearchSong> = hotSongs,
    albumItems: List<MeloXAlbumSummary> = albums,
): MeloXArtistUiDetail =
    MeloXArtistUiDetail(
        artistId = id.toString(),
        name = name,
        avatarUrl = artworkUrl ?: coverUrl,
        coverUrl = coverUrl,
        artworkUrl = artworkUrl,
        aliases = aliases,
        intro = description,
        songCount = musicSize,
        albumCount = albumSize,
        isFollowed = followed ?: this.followed == true,
        hotSongs = songs.map(SearchSong::toSonifyUi),
        albums = albumItems.map(MeloXAlbumSummary::toSonifyUi),
    )

fun SearchSong.toSonifyUi(): MeloXArtistUiSong =
    MeloXArtistUiSong(
        hash = providerTrack?.id?.value ?: id.toString(),
        name = name,
        author = artists,
        albumName = album,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
    )

fun MeloXAlbumSummary.toSonifyUi(): MeloXArtistUiAlbum =
    MeloXArtistUiAlbum(
        albumId = id.toString(),
        name = name,
        singerName = artistText,
        coverUrl = artworkUrl.orEmpty(),
        songCount = 0,
        publishTime = null,
    )

fun MusicAlbumSummary.toSonifyUi(): MeloXArtistUiAlbum =
    MeloXArtistUiAlbum(
        albumId = id.value,
        name = title,
        singerName = artists.joinToString(" / ") { it.name },
        coverUrl = artworkUrl.orEmpty(),
        songCount = trackCount?.toInt() ?: 0,
        publishTime = releaseDate,
    )
