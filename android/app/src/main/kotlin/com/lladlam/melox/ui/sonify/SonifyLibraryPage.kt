package com.lladlam.melox.ui.sonify

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.library.NeteaseLibrarySnapshot
import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.model.MusicArtistSummary
import com.lladlam.melox.core.music.model.MusicAlbumSummary
import com.lladlam.melox.ui.MeloXBottomContentClearance

@Composable
internal fun SonifyLibraryPage(
    snapshot: NeteaseLibrarySnapshot?,
    savedAlbums: List<MusicAlbumSummary>,
    followedArtists: List<MusicArtistSummary>,
    loading: Boolean,
    error: String?,
    onPlaySong: (SearchSong) -> Unit,
    onOpenPlaylist: (NeteasePlaylistSummary) -> Unit,
    onOpenAlbum: (MusicAlbumSummary) -> Unit,
    onOpenArtist: (MusicArtistSummary) -> Unit,
) {
    if (snapshot == null && loading) {
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(top = 10.dp, bottom = MeloXBottomContentClearance + 90.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "sonify-library-title") {
            Text(
                "资料库",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.5).sp,
            )
        }
        error?.let { message ->
            item { Text(message, modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.error) }
        }
        val recent = snapshot?.recentSongs.orEmpty().ifEmpty { snapshot?.likedSongs.orEmpty() }
        if (recent.isNotEmpty()) {
            item { SonifyLibrarySection("最近播放") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(recent.take(10), key = { "sonify-library-recent-${it.id}" }) { song ->
                        SonifySongCard(song) { onPlaySong(song) }
                    }
                }
            }
        }
        val playlists = snapshot?.playlists.orEmpty()
        if (playlists.isNotEmpty()) {
            item { SonifyLibrarySection("我的歌单") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(playlists, key = { "sonify-library-playlist-${it.id}" }) { playlist ->
                        SonifyPlaylistCard(playlist) { onOpenPlaylist(playlist) }
                    }
                }
            }
        }
        if (savedAlbums.isNotEmpty()) {
            item { SonifyLibrarySection("收藏专辑") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(savedAlbums, key = { "sonify-library-album-${it.id.value}" }) { album ->
                        Column(Modifier.width(154.dp).clickable { onOpenAlbum(album) }) {
                            AsyncImage(
                                album.artworkUrl,
                                null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(154.dp).clip(RoundedCornerShape(18.dp)),
                            )
                            Text(album.title, Modifier.padding(top = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(album.artists.joinToString(" / ") { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
                        }
                    }
                }
            }
        }
        if (followedArtists.isNotEmpty()) {
            item { SonifyLibrarySection("关注歌手") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(followedArtists, key = { "sonify-library-artist-${it.id.value}" }) { artist ->
                        Column(
                            modifier = Modifier.width(98.dp).clickable { onOpenArtist(artist) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AsyncImage(
                                artist.artworkUrl,
                                null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(86.dp).clip(CircleShape),
                            )
                            Text(artist.name, Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        val songs = snapshot?.likedSongs.orEmpty()
        if (songs.isNotEmpty()) {
            item { SonifyLibrarySection("我喜欢的音乐") }
            items(songs.take(30), key = { "sonify-library-song-${it.id}" }) { song ->
                SonifyLibrarySongRow(song) { onPlaySong(song) }
            }
        }
    }
}

@Composable
private fun SonifyLibrarySection(title: String) {
    Text(
        title,
        modifier = Modifier.padding(horizontal = 24.dp),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SonifySongCard(song: SearchSong, onClick: () -> Unit) {
    Column(Modifier.width(154.dp).clickable(onClick = onClick)) {
        AsyncImage(
            song.artworkUrl,
            null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(154.dp).clip(RoundedCornerShape(18.dp)),
        )
        Text(song.name, Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(song.artists, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
    }
}

@Composable
private fun SonifyPlaylistCard(playlist: NeteasePlaylistSummary, onClick: () -> Unit) {
    Column(Modifier.width(190.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(190.dp).clip(RoundedCornerShape(20.dp))) {
            AsyncImage(
                playlist.coverUrl,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = .55f)),
                ),
            )
        }
        Text(playlist.name, Modifier.padding(top = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("${playlist.trackCount} 首 · ${playlist.creatorName}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
    }
}

@Composable
private fun SonifyLibrarySongRow(song: SearchSong, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            song.artworkUrl,
            null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(song.artists, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
        }
    }
}
