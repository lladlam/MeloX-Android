package com.lladlam.melox.ui.sonify

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXAlbumSummary
import com.lladlam.melox.core.network.MeloXArtistDetail
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.library.MeloXDetailPaletteProvider
import com.lladlam.melox.ui.player.MeloXBlurredArtworkBackdrop

@Composable
internal fun SonifyArtistPage(
    artist: MeloXArtistDetail,
    allSongs: List<SearchSong>,
    followed: Boolean?,
    onPlaySong: (SearchSong) -> Unit,
    onPlayHot: () -> Unit,
    onMoreSong: (SearchSong) -> Unit,
    onOpenAlbum: (MeloXAlbumSummary) -> Unit,
    onToggleFollow: () -> Unit,
    onInfo: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    var paletteSeed by remember(artist.artworkUrl) { mutableStateOf(Color(0xFF111111)) }
    LaunchedEffect(artist.artworkUrl) {
        paletteSeed = MeloXDetailPaletteProvider.paletteFor(artist.artworkUrl).background
    }
    val accent by animateColorAsState(paletteSeed, label = "sonifyArtistAccent")
    val artworkColors = ArtistArtworkColors.fromRgb(accent.toArgb(), isDark)
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to artworkColors.bright.copy(alpha = .50f),
                    .45f to artworkColors.dark.copy(alpha = .24f),
                    1f to MaterialTheme.colorScheme.background,
                ),
            ),
    ) {
        MeloXBlurredArtworkBackdrop(
            artworkUrl = artist.artworkUrl,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = .38f },
        )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MeloXBottomContentClearance + 90.dp),
    ) {
        item(key = "sonify-artist-hero") {
            SonifyArtistHero(
                artist = artist,
                accent = accent,
                followed = followed,
                onPlayHot = onPlayHot,
                onToggleFollow = onToggleFollow,
                onInfo = onInfo,
            )
        }
        item(key = "sonify-artist-reflection") {
            SonifyArtistReflection(artist, accent)
        }
        val hot = artist.hotSongs.take(20)
        if (hot.isNotEmpty()) {
            item(key = "sonify-artist-hot-title") {
                SonifyArtistSectionTitle("热门歌曲")
            }
            items(hot, key = { "sonify-artist-hot-${it.id}" }) { song ->
                SonifySongRow(song, onClick = { onPlaySong(song) }, onMore = { onMoreSong(song) })
            }
        }
        if (artist.albums.isNotEmpty()) {
            item(key = "sonify-artist-albums-title") {
                SonifyArtistSectionTitle("专辑")
            }
            item(key = "sonify-artist-albums") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(artist.albums, key = { "sonify-artist-album-${it.id}" }) { album ->
                        Column(
                            modifier = Modifier
                                .width(154.dp)
                                .clickable { onOpenAlbum(album) },
                        ) {
                            AsyncImage(
                                album.artworkUrl,
                                null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(154.dp).clip(RoundedCornerShape(18.dp)),
                            )
                            Text(
                                album.name,
                                modifier = Modifier.padding(top = 8.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                album.type.orEmpty().ifBlank { album.artistText },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f),
                            )
                        }
                    }
                }
            }
        }
        val complete = allSongs.ifEmpty { artist.hotSongs }
        if (complete.isNotEmpty()) {
            item(key = "sonify-artist-all-title") {
                SonifyArtistSectionTitle("全部歌曲")
            }
            items(complete.take(50), key = { "sonify-artist-all-${it.id}" }) { song ->
                SonifySongRow(song, onClick = { onPlaySong(song) }, onMore = { onMoreSong(song) })
            }
        }
    }
    }
}

@Composable
private fun SonifyArtistHero(
    artist: MeloXArtistDetail,
    accent: Color,
    followed: Boolean?,
    onPlayHot: () -> Unit,
    onToggleFollow: () -> Unit,
    onInfo: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(470.dp)) {
        AsyncImage(
            artist.coverUrl ?: artist.artworkUrl,
            null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .22f),
                    .42f to Color.Transparent,
                    .78f to accent.copy(alpha = .88f),
                    1f to accent,
                ),
            ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text(
                artist.name,
                fontSize = 40.sp,
                lineHeight = 45.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (artist.aliases.isNotEmpty()) {
                Text(
                    artist.aliases.joinToString(" · "),
                    color = Color.White.copy(alpha = .72f),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SonifyHeroAction("播放热门", onPlayHot, filled = true)
                SonifyHeroAction(if (followed == true) "已关注" else "关注", onToggleFollow)
                SonifyHeroAction("资料", onInfo)
            }
        }
    }
}

@Composable
private fun SonifyHeroAction(label: String, onClick: () -> Unit, filled: Boolean = false) {
    Text(
        text = label,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (filled) Color.White else Color.White.copy(alpha = .18f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        color = if (filled) Color.Black else Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    )
}

@Composable
private fun SonifyArtistReflection(artist: MeloXArtistDetail, accent: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(0.dp)),
    ) {
        AsyncImage(
            artist.coverUrl ?: artist.artworkUrl,
            null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .graphicsLayer {
                    scaleY = -1f
                    alpha = .20f
                },
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to accent.copy(alpha = .30f),
                    .35f to MaterialTheme.colorScheme.background.copy(alpha = .55f),
                    1f to MaterialTheme.colorScheme.background,
                ),
            ),
        )
    }
}

@Composable
private fun SonifyArtistSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SonifySongRow(song: SearchSong, onClick: () -> Unit, onMore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 7.dp),
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
        Text(
            "•••",
            modifier = Modifier.clickable(onClick = onMore).padding(12.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f),
        )
    }
}
