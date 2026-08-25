package com.lladlam.melox.ui.collection

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXAlbumSummary
import com.lladlam.melox.core.network.MeloXArtistDetail
import com.lladlam.melox.core.network.NeteaseCollectionDetailsClient
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassIconButton
import com.lladlam.melox.ui.glass.MeloXShapes
import com.lladlam.melox.ui.glass.MeloXSwipeAction
import com.lladlam.melox.ui.glass.MeloXSwipeActionRow
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.player.MeloXSongActionsOverlay
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXSwipeFullAction
import kotlinx.coroutines.launch

@Composable
internal fun MeloXArtistDetailScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val client = remember(app) { NeteaseCollectionDetailsClient(cookieProvider = { NeteaseSessionStore.readCookie(app) }) }
    val operations = remember(app) { NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(app) }) }
    val scope = rememberCoroutineScope()
    var detail by remember(id) { mutableStateOf<MeloXArtistDetail?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var followed by remember(id) { mutableStateOf<Boolean?>(null) }
    var followBusy by remember(id) { mutableStateOf(false) }
    var selectedSong by remember(id) { mutableStateOf<SearchSong?>(null) }

    LaunchedEffect(id) {
        loading = true
        runCatching { client.artistDetail(id) }
            .onSuccess {
                detail = it
                followed = it.followed
            }
            .onFailure { error = it.message ?: "歌手加载失败" }
        loading = false
    }
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = MeloXBottomContentClearance),
        ) {
            detail?.let { artist ->
                item(key = "artist-hero") {
                    ArtistHero(
                        artist = artist,
                        followed = followed,
                        followBusy = followBusy,
                        onFollow = {
                            val target = followed != true
                            followBusy = true
                            scope.launch {
                                runCatching { client.setArtistFollowed(id, target) }
                                    .onSuccess { followed = target }
                                    .onFailure { error = it.message ?: "关注操作失败" }
                                followBusy = false
                            }
                        },
                    )
                }
                item(key = "songs-title") { ArtistSectionTitle("热门单曲") }
                items(artist.hotSongs.take(10), key = { "artist-song-${it.id}" }) { song ->
                    ArtistSongRow(
                        song = song,
                        onPlay = { PlaybackCommands.playQueue(context, artist.hotSongs, song.id) },
                        onMore = { selectedSong = song },
                        onLike = {
                            scope.launch {
                                runCatching { operations.setSongLiked(song.id, true) }
                                    .onFailure { error = it.message ?: "添加到资料库失败" }
                            }
                        },
                    )
                }
                if (artist.albums.isNotEmpty()) {
                    item(key = "albums-title") { ArtistSectionTitle("专辑") }
                    item(key = "albums") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(artist.albums, key = { "artist-album-${it.id}" }) { album ->
                                ArtistAlbumCard(album) { MeloXCollectionDetailActivity.launchAlbum(context, album) }
                            }
                        }
                    }
                }
            }

            if (loading) item(key = "loading") {
                Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            error?.let { message ->
                item(key = "error") {
                    Text(message, modifier = Modifier.fillMaxWidth().padding(20.dp), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Box(Modifier.statusBarsPadding().padding(start = 20.dp, top = 9.dp)) {
            MeloXGlassIconButton(MeloXSymbol.ChevronLeft, onBack, contentDescription = "返回")
        }

        selectedSong?.let { song ->
            MeloXSongActionsOverlay(
                song = song,
                queue = detail?.hotSongs.orEmpty(),
                visible = true,
                onDismiss = { selectedSong = null },
            )
        }
    }
}

@Composable
private fun ArtistHero(
    artist: MeloXArtistDetail,
    followed: Boolean?,
    followBusy: Boolean,
    onFollow: () -> Unit,
) {
    var expanded by remember(artist.id) { mutableStateOf(false) }
    val background = MaterialTheme.colorScheme.background
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            AsyncImage(
                model = artist.coverUrl ?: artist.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = .16f),
                        .42f to Color.Transparent,
                        .78f to background.copy(alpha = .86f),
                        1f to background,
                    ),
                ),
            )
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    AsyncImage(
                        model = artist.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(artist.name, fontSize = 27.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (artist.aliases.isNotEmpty()) {
                            Text(artist.aliases.joinToString(" · "), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .54f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    ArtistMetric(artist.musicSize, "单曲")
                    ArtistMetric(artist.albumSize, "专辑")
                    ArtistMetric(artist.mvSize, "MV")
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            followed?.let {
                MeloXGlassButton(
                    onClick = onFollow,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !followBusy,
                    style = if (it) MeloXGlassButtonStyle.BorderedProminent else MeloXGlassButtonStyle.Bordered,
                    shape = MeloXShapes.capsule,
                ) { Text(if (it) "已关注" else "关注", fontWeight = FontWeight.SemiBold) }
            }
            artist.description?.takeIf(String::isNotBlank)?.let { description ->
                Text(
                    description,
                    modifier = Modifier.padding(top = 14.dp).clickable { expanded = !expanded },
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .58f),
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )
                Text(
                    if (expanded) "收起" else "展开全部",
                    modifier = Modifier.padding(top = 4.dp).clickable { expanded = !expanded },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ArtistMetric(value: Int, label: String) {
    Column {
        Text(value.toString(), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f))
    }
}

@Composable
private fun ArtistSectionTitle(title: String) {
    Text(title, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun ArtistSongRow(song: SearchSong, onPlay: () -> Unit, onMore: () -> Unit, onLike: () -> Unit) {
    val context = LocalContext.current
    MeloXSwipeActionRow(
        startActions = listOf(
            MeloXSwipeAction("下一首播放", MeloXSymbol.Next, Color(0xFF8E5AF7)) { PlaybackCommands.playNext(context, song) },
            MeloXSwipeAction("稍后播放", MeloXSymbol.Queue, Color(0xFFFF9F0A)) { PlaybackCommands.addToQueue(context, song) },
        ),
        endActions = listOf(MeloXSwipeAction("添加到资料库", MeloXSymbol.Heart, Color(0xFFFF3B30), onLike)),
        startFullSwipeActionIndex = if (MeloXSettingsRuntime.swipeFullAction == MeloXSwipeFullAction.AddToQueue) 1 else 0,
        onClick = onPlay,
        onLongClick = onMore,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(song.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(9.dp)))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(song.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artists, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ArtistAlbumCard(album: MeloXAlbumSummary, onClick: () -> Unit) {
    Column(Modifier.width(116.dp).clickable(onClick = onClick)) {
        AsyncImage(
            model = album.artworkUrl,
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(116.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.height(7.dp))
        Text(album.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(album.artistText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
