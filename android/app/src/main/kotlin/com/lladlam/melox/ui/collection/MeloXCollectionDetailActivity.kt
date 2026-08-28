package com.lladlam.melox.ui.collection
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.download.MeloXDownloadPlaylistRef
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.*
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.finishMeloXPage
import com.lladlam.melox.ui.startMeloXPage
import com.lladlam.melox.ui.glass.MeloXActionIcon
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.library.MeloXBatchDownloadSheet
import com.lladlam.melox.ui.library.MeloXUnifiedAlbumDetailScreen
import com.lladlam.melox.ui.podcast.MeloXPodcastScreen
import com.lladlam.melox.ui.sharing.MeloXNeteaseResourceShareActivity
import com.lladlam.melox.ui.theme.MeloXTheme
import kotlinx.coroutines.launch

class MeloXCollectionDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val isProgram = intent.getBooleanExtra(EXTRA_PROGRAM, false)
        val kind = runCatching { MeloXSearchKind.valueOf(intent.getStringExtra(EXTRA_KIND).orEmpty()) }.getOrNull()
        if (id <= 0L || (!isProgram && kind !in setOf(MeloXSearchKind.Albums, MeloXSearchKind.Artists, MeloXSearchKind.Podcasts))) {
            finish()
            return
        }
        val onExit: () -> Unit = ::finish
        setContent {
            MeloXTheme {
                val page: @Composable () -> Unit = {
                    when {
                        isProgram -> PodcastProgramScreen(id, onExit)
                        kind == MeloXSearchKind.Albums -> MeloXUnifiedAlbumDetailScreen(id, ::finish)
                        kind == MeloXSearchKind.Artists -> MeloXArtistDetailScreen(id, onExit)
                        kind == MeloXSearchKind.Podcasts -> MeloXPodcastScreen(
                            initialPodcastId = id,
                            onExit = onExit,
                            bottomPadding = 32.dp,
                        )
                    }
                }
                page()
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_PROGRAM = "program"
        fun launch(context: Context, item: MeloXSearchMediaItem) = launch(context, item.id, item.kind)
        fun launchAlbum(context: Context, a: MeloXAlbumSummary) = launch(context, a.id, MeloXSearchKind.Albums)
        fun launchPodcast(context: Context, id: Long) = launch(context, id, MeloXSearchKind.Podcasts)
        fun launchPodcastProgram(context: Context, id: Long) {
            context.startMeloXPage(Intent(context, MeloXCollectionDetailActivity::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_PROGRAM, true).apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
        private fun launch(context: Context, id: Long, kind: MeloXSearchKind) {
            val intent = Intent(context, MeloXCollectionDetailActivity::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_KIND, kind.name)
                .apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (kind == MeloXSearchKind.Albums) context.startActivity(intent) else context.startMeloXPage(intent)
        }
    }
}
@Composable private fun Header(title: String, onBack: () -> Unit) = Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).meloXLiquidButton(shape = CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { MeloXActionIcon("‹", Modifier.size(20.dp), MaterialTheme.colorScheme.onSurface) }; Spacer(Modifier.width(12.dp)); Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
@Composable
private fun AlbumScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val client = remember(app) { NeteaseCollectionDetailsClient(cookieProvider = { NeteaseSessionStore.readCookie(app) }) }
    val downloads = remember(app) { MeloXDownloadStore.get(app) }
    val scope = rememberCoroutineScope()
    var detail by remember(id) { mutableStateOf<MeloXAlbumDetail?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var query by remember(id) { mutableStateOf("") }
    var subscribed by remember(id) { mutableStateOf<Boolean?>(null) }
    var showBatchDownload by remember(id) { mutableStateOf(false) }

    LaunchedEffect(id) {
        runCatching { client.albumDetail(id) }
            .onSuccess { detail = it; subscribed = it.subscribed }
            .onFailure { error = it.message ?: "专辑加载失败" }
        loading = false
    }
    val songs = detail?.songs.orEmpty()
    val filtered = remember(songs, query) {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) songs else songs.filter {
            it.name.lowercase().contains(normalized) || it.artists.lowercase().contains(normalized)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, MeloXBottomContentClearance),
    ) {
        item { Header(detail?.album?.name ?: "专辑", onBack) }
        detail?.let { value ->
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(value.album.artworkUrl, value.album.name, contentScale = ContentScale.Crop, modifier = Modifier.size(210.dp).clip(RoundedCornerShape(16.dp)))
                    Text(value.album.name, Modifier.padding(top = 15.dp), fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(value.album.artistText, Modifier.padding(top = 5.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Action("播放") { songs.firstOrNull()?.let { PlaybackCommands.playQueue(context, songs, it.id) } }
                        Action("随机") { songs.shuffled().let { queue -> queue.firstOrNull()?.let { PlaybackCommands.playQueue(context, queue, it.id) } } }
                        subscribed?.let { state ->
                            Action(if (state) "已收藏" else "收藏") {
                                val target = !state
                                scope.launch {
                                    runCatching { client.setAlbumSubscribed(id, target) }
                                        .onSuccess { subscribed = target }
                                        .onFailure { error = it.message }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Action("批量下载") { showBatchDownload = true }
                        Action("分享") { MeloXNeteaseResourceShareActivity.launch(context, "album", id, value.album.name, "https://music.163.com/album?id=$id") }
                    }
                    value.description?.let { Text(it, Modifier.fillMaxWidth().padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 13.sp) }
                }
            }
        }
        item {
            BasicTextField(
                query,
                { query = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().meloXLiquidButton(shape = RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                decorationBox = { inner -> if (query.isBlank()) Text("在专辑中搜索", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f)); inner() },
            )
        }
        if (loading) item { Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        items(filtered, key = { "album-${it.id}" }) { song ->
            Track(song, { PlaybackCommands.playQueue(context, songs, song.id) }) {
                downloads.start(song, MusicQualityPreferences.read(app))
            }
        }
    }
    MeloXBatchDownloadSheet(
        songs = songs,
        sourcePlaylist = detail?.album?.let { album -> MeloXDownloadPlaylistRef(album.id, album.name, album.artworkUrl) },
        visible = showBatchDownload,
        onDismiss = { showBatchDownload = false },
    )
}
@Composable private fun ArtistScreen(id: Long, onBack: () -> Unit) { val context = LocalContext.current; val app = context.applicationContext; val client = remember(app) { NeteaseCollectionDetailsClient(cookieProvider = { NeteaseSessionStore.readCookie(app) }) }; var detail by remember(id) { mutableStateOf<MeloXArtistDetail?>(null) }; var loading by remember(id) { mutableStateOf(true) }; var error by remember(id) { mutableStateOf<String?>(null) }; LaunchedEffect(id) { runCatching { client.artistDetail(id) }.onSuccess { detail = it }.onFailure { error = it.message ?: "歌手加载失败" }; loading = false }; BackHandler(onBack = onBack); val v = detail; LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, MeloXBottomContentClearance)) { item { Header(v?.name ?: "歌手", onBack) }; v?.let { a -> item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { AsyncImage(a.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(176.dp).clip(CircleShape)); Text(a.name, Modifier.padding(top = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold); if (a.aliases.isNotEmpty()) Text(a.aliases.joinToString(" / ")); Action("播放热门歌曲") { a.hotSongs.firstOrNull()?.let { PlaybackCommands.playQueue(context, a.hotSongs, it.id) } } } }; item { Text("热门歌曲", fontSize = 21.sp, fontWeight = FontWeight.Bold) }; items(a.hotSongs.take(50), key = { "artist-song-${it.id}" }) { Track(it, { PlaybackCommands.playQueue(context, a.hotSongs, it.id) }) }; if (a.albums.isNotEmpty()) { item { Text("专辑", fontSize = 21.sp, fontWeight = FontWeight.Bold) }; items(a.albums, key = { "artist-album-${it.id}" }) { al -> Row(Modifier.fillMaxWidth().clickable { MeloXCollectionDetailActivity.launchAlbum(context, al) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(al.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(9.dp))); Column(Modifier.weight(1f).padding(start = 11.dp)) { Text(al.name, maxLines = 1); Text(al.type ?: al.artistText, fontSize = 12.sp) }; Text("›", fontSize = 24.sp) } } } }; if (loading) item { CircularProgressIndicator() }; error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } } } }
@Composable private fun PodcastProgramScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val client = remember(app) { NeteaseUniversalSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(app) }) }
    var program by remember(id) { mutableStateOf<MeloXPodcastProgram?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        runCatching { client.podcastProgramDetail(id) }
            .onSuccess { program = it ?: run { error = "节目不存在"; null } }
            .onFailure { error = it.message ?: "节目加载失败" }
        loading = false
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, MeloXBottomContentClearance)) {
        item { Header(program?.name ?: "播客节目", onBack) }
        program?.let { value ->
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(value.artworkUrl, value.name, contentScale = ContentScale.Crop, modifier = Modifier.size(210.dp).clip(RoundedCornerShape(18.dp)))
                    Text(value.name, Modifier.padding(top = 14.dp), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(value.radioName, Modifier.padding(top = 5.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                    value.playbackSong?.let { song -> Action("播放本期节目") { PlaybackCommands.playQueue(context, listOf(song), song.id) } }
                    value.description?.let { Text(it, Modifier.fillMaxWidth().padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f)) }
                    Action("查看播客") { MeloXCollectionDetailActivity.launchPodcast(context, value.radioId) }
                }
            }
        }
        if (loading) item { CircularProgressIndicator() }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
}
@Composable private fun PodcastScreen(id: Long, onBack: () -> Unit) { val context = LocalContext.current; val app = context.applicationContext; val client = remember(app) { NeteaseUniversalSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(app) }) }; val scope = rememberCoroutineScope(); var podcast by remember(id) { mutableStateOf<MeloXPodcast?>(null) }; var programs by remember(id) { mutableStateOf<List<MeloXPodcastProgram>>(emptyList()) }; var loading by remember(id) { mutableStateOf(true) }; var error by remember(id) { mutableStateOf<String?>(null) }; var subscribed by remember(id) { mutableStateOf(false) }; LaunchedEffect(id) { runCatching { client.podcastDetail(id) to client.podcastPrograms(id, limit = 100).values }.onSuccess { (p, list) -> podcast = p; subscribed = p?.subscribed == true; programs = list }.onFailure { error = it.message ?: "播客加载失败" }; loading = false }; BackHandler(onBack = onBack); val playable = programs.mapNotNull(MeloXPodcastProgram::playbackSong); LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, MeloXBottomContentClearance)) { item { Header(podcast?.name ?: "播客", onBack) }; podcast?.let { p -> item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { AsyncImage(p.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(190.dp).clip(RoundedCornerShape(18.dp))); Text(p.name, Modifier.padding(top = 12.dp), fontSize = 23.sp, fontWeight = FontWeight.Bold); Row { Action("播放") { playable.firstOrNull()?.let { PlaybackCommands.playQueue(context, playable, it.id) } }; Action(if (subscribed) "已订阅" else "订阅") { scope.launch { runCatching { client.setPodcastSubscribed(id, !subscribed) }.onSuccess { subscribed = !subscribed }.onFailure { error = it.message } } } }; p.description?.let { Text(it, Modifier.padding(top = 10.dp)) } } } }; if (loading) item { CircularProgressIndicator() }; error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }; items(programs, key = { "program-${it.id}" }) { pr -> pr.playbackSong?.let { song -> Track(song, { PlaybackCommands.playQueue(context, playable, song.id) }) } ?: Text(pr.name) } } }
@Composable private fun Track(song: SearchSong, onPlay: () -> Unit, onDownload: (() -> Unit)? = null) = Row(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(song.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))); Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(song.name, maxLines = 1); Text(song.artists, fontSize = 12.sp) }; onDownload?.let { MeloXActionIcon("↓", Modifier.size(20.dp).clickable(onClick = it).padding(2.dp), MaterialTheme.colorScheme.primary) } }
@Composable private fun Action(title: String, onClick: () -> Unit) = Box(Modifier.padding(6.dp).meloXLiquidButton(shape = RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
