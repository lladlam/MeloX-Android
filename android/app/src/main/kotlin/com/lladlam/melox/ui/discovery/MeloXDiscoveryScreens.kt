package com.lladlam.melox.ui.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.library.NeteaseHomeContent
import com.lladlam.melox.core.library.NeteaseLibraryCache
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.playback.PlaybackCommands
import kotlinx.coroutines.launch

private val Accent = Color(0xFFFF3147)
private val Categories = listOf("推荐歌单", "排行榜", "精品歌单", "全部", "华语", "欧美", "流行", "摇滚", "民谣", "电子", "轻音乐", "影视原声", "ACG")

@Composable
fun MeloXHomeScreen() {
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { NeteaseLibraryCache(context) }
    val client = remember(context) { NeteaseLibraryClient({ NeteaseSessionStore.readCookie(context) }) }
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf<NeteaseHomeContent?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedPlaylist by remember { mutableStateOf<NeteasePlaylistSummary?>(null) }

    selectedPlaylist?.let { playlist ->
        DiscoveryPlaylistDetail(playlist = playlist, onBack = { selectedPlaylist = null })
        return
    }

    fun refresh() {
        if (refreshing) return
        scope.launch {
            refreshing = true
            runCatching { client.homeContent() }
                .onSuccess { content = it; cache.saveHomeContent(it); error = null }
                .onFailure { error = it.message ?: "首页加载失败" }
            refreshing = false
        }
    }
    LaunchedEffect(Unit) {
        content = cache.loadHomeContent()
        if (NeteaseLibraryCache.beginHomeColdStartRefresh()) refresh()
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = ::refresh, modifier = Modifier.fillMaxSize()) {
        val value = content
        if (value == null) {
            EmptyOrLoading(refreshing, error)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 70.dp, bottom = 146.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item { LargeTitle("首页") }
                item { SectionTitle("每日推荐", "下拉可刷新") }
                item { PlaylistRow(value.playlists) { selectedPlaylist = it } }
                item { SectionTitle("为你推荐", "新歌") }
                items(value.newSongs, key = { it.id }) { song -> SongRow(song) { PlaybackCommands.playQueue(context, value.newSongs, song.id) } }
                error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) } }
            }
        }
    }
}

@Composable
fun MeloXExploreScreen() {
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { NeteaseLibraryCache(context) }
    val client = remember(context) { NeteaseLibraryClient({ NeteaseSessionStore.readCookie(context) }) }
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf(Categories.first()) }
    var playlists by remember { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedPlaylist by remember { mutableStateOf<NeteasePlaylistSummary?>(null) }

    selectedPlaylist?.let { playlist ->
        DiscoveryPlaylistDetail(playlist = playlist, onBack = { selectedPlaylist = null })
        return
    }

    fun refresh() {
        if (refreshing) return
        val requested = category
        scope.launch {
            refreshing = true
            runCatching { client.explorePlaylists(requested) }
                .onSuccess { if (category == requested) playlists = it; cache.saveExplore(requested, it); error = null }
                .onFailure { error = it.message ?: "发现页加载失败" }
            refreshing = false
        }
    }
    LaunchedEffect(category) {
        playlists = cache.loadExplore(category).orEmpty()
        if (NeteaseLibraryCache.beginExploreColdStartRefresh(category)) refresh()
    }

    Column(Modifier.fillMaxSize().padding(top = 70.dp)) {
        LargeTitle("发现", Modifier.padding(horizontal = 20.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(Categories) { item ->
                Text(
                    text = item.removeSuffix("歌单"),
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (category == item) Accent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                        .clickable { category = item }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (category == item) Color.White else MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = ::refresh, modifier = Modifier.weight(1f)) {
            if (playlists.isEmpty()) EmptyOrLoading(refreshing, error) else PlaylistGrid(playlists) { selectedPlaylist = it }
        }
    }
}

@Composable private fun LargeTitle(text: String, modifier: Modifier = Modifier) = Text(text, modifier, fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
@Composable private fun SectionTitle(title: String, trailing: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold); Text(trailing, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .42f), fontSize = 13.sp) }

@Composable
private fun PlaylistRow(values: List<NeteasePlaylistSummary>, onSelect: (NeteasePlaylistSummary) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(values, key = { it.id }) { playlist -> PlaylistCard(playlist, Modifier.width(174.dp)) { onSelect(playlist) } }
    }
}

@Composable
private fun PlaylistGrid(values: List<NeteasePlaylistSummary>, onSelect: (NeteasePlaylistSummary) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 146.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) { items(values, key = { it.id }) { playlist -> PlaylistCard(playlist, Modifier.fillMaxWidth()) { onSelect(playlist) } } }
}

@Composable
private fun PlaylistCard(value: NeteasePlaylistSummary, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        AsyncImage(value.coverUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(174.dp).clip(RoundedCornerShape(14.dp)))
        Text(value.name, modifier = Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SongRow(song: SearchSong, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(song.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(song.artists, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun DiscoveryPlaylistDetail(
    playlist: NeteasePlaylistSummary,
    onBack: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val client = remember(context) {
        NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
    }
    var detail by remember(playlist.id) { mutableStateOf<com.lladlam.melox.core.library.NeteasePlaylistDetail?>(null) }
    var error by remember(playlist.id) { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    LaunchedEffect(playlist.id) {
        runCatching { client.playlistDetail(playlist.id) }
            .onSuccess { detail = it }
            .onFailure { error = it.message ?: "歌单加载失败" }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 146.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹", fontSize = 44.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 10.dp))
                Text(playlist.name, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(playlist.coverUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(156.dp).clip(RoundedCornerShape(22.dp)))
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (playlist.creatorName.isNotBlank()) Text(playlist.creatorName, modifier = Modifier.padding(top = 7.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f), fontSize = 13.sp)
                    val songs = detail?.songs.orEmpty()
                    if (songs.isNotEmpty()) {
                        Text(
                            "▶  播放全部",
                            modifier = Modifier.padding(top = 15.dp).clip(RoundedCornerShape(22.dp)).background(Accent).clickable { PlaybackCommands.playQueue(context, songs, songs.first().id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = Color.White, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        playlist.description?.takeIf(String::isNotBlank)?.let { description ->
            item { Text(description, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f), fontSize = 13.sp, lineHeight = 19.sp) }
        }
        val value = detail
        when {
            value != null -> items(value.songs, key = { it.id }) { song ->
                SongRow(song) { PlaybackCommands.playQueue(context, value.songs, song.id) }
            }
            error != null -> item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            else -> item { Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) } }
        }
    }
}

@Composable
private fun EmptyOrLoading(loading: Boolean, error: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (loading) CircularProgressIndicator(color = Accent) else Text(error ?: "暂无内容", color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f))
    }
}

private fun playPlaylist(context: android.content.Context, playlist: NeteasePlaylistSummary) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        runCatching {
            NeteaseLibraryClient({ NeteaseSessionStore.readCookie(context) }).playlistDetail(playlist.id)
        }.onSuccess { detail -> detail.songs.firstOrNull()?.let { PlaybackCommands.playQueue(context, detail.songs, it.id) } }
    }
}