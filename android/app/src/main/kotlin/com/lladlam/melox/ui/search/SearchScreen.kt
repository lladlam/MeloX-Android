package com.lladlam.melox.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.library.NeteasePlaylistDetail
import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXSearchKind
import com.lladlam.melox.core.network.MeloXSearchMediaItem
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.network.NeteaseUniversalSearchClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.podcast.MeloXPodcastScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class MeloXSearchLaunch(val query: String, val kind: MeloXSearchKind, val nonce: Long = System.nanoTime())
object MeloXSearchLaunchBus {
    var request by mutableStateOf<MeloXSearchLaunch?>(null)
        private set
    fun post(query: String, kind: MeloXSearchKind) { request = MeloXSearchLaunch(query, kind) }
    fun consume(request: MeloXSearchLaunch) { if (this.request == request) this.request = null }
}

private val SearchAccent = Color(0xFFFF3147)
private val SearchCategories = listOf("排行榜", "播客", "华语", "欧美", "日语", "韩语", "粤语", "流行", "摇滚", "民谣", "电子", "说唱", "R&B/Soul", "古典", "ACG", "影视原声", "学习", "工作", "放松", "夜晚")

@Composable
fun SearchScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val songClient = remember(appContext) { NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) }) }
    val universal = remember(appContext) { NeteaseUniversalSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) }) }
    val library = remember(appContext) { NeteaseLibraryClient({ NeteaseSessionStore.readCookie(appContext) }) }

    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(MeloXSearchKind.Songs) }
    var songs by remember { mutableStateOf<List<SearchSong>>(emptyList()) }
    var media by remember { mutableStateOf<List<MeloXSearchMediaItem>>(emptyList()) }
    var recommendations by remember { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var categoryTitle by remember { mutableStateOf<String?>(null) }
    var categoryPlaylists by remember { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var selectedMedia by remember { mutableStateOf<MeloXSearchMediaItem?>(null) }
    var podcastDiscovery by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val launchRequest = MeloXSearchLaunchBus.request

    LaunchedEffect(launchRequest) {
        launchRequest?.let { request ->
            query = request.query
            kind = request.kind
            MeloXSearchLaunchBus.consume(request)
        }
    }

    LaunchedEffect(Unit) {
        runCatching { library.explorePlaylists("推荐歌单", 10) }.onSuccess { recommendations = it }
    }

    LaunchedEffect(query, kind) {
        val keyword = query.trim()
        if (keyword.isBlank()) {
            songs = emptyList(); media = emptyList(); error = null; loading = false
            return@LaunchedEffect
        }
        delay(350)
        loading = true; error = null
        val linkedId = parseSongLink(keyword)
        if (linkedId != null) {
            runCatching { universal.songDetail(linkedId) }
                .onSuccess { songs = listOfNotNull(it); media = emptyList(); kind = MeloXSearchKind.Songs }
                .onFailure { error = it.message ?: "无法读取歌曲链接" }
            loading = false
            return@LaunchedEffect
        }
        if (kind == MeloXSearchKind.Songs) {
            runCatching { songClient.ensureArtwork(songClient.searchSongs(keyword)) }
                .onSuccess { songs = it; media = emptyList() }
                .onFailure { error = it.message ?: "搜索失败" }
        } else {
            runCatching { universal.searchMedia(keyword, kind) }
                .onSuccess { media = it; songs = emptyList() }
                .onFailure { error = it.message ?: "搜索失败" }
        }
        loading = false
    }

    BackHandler(enabled = podcastDiscovery || selectedMedia != null || categoryTitle != null) {
        when {
            podcastDiscovery -> podcastDiscovery = false
            selectedMedia != null -> selectedMedia = null
            else -> { categoryTitle = null; categoryPlaylists = emptyList() }
        }
    }

    if (podcastDiscovery) {
        MeloXPodcastScreen()
        return
    }

    selectedMedia?.let { destination ->
        SearchCollectionDetail(destination, universal, library) { selectedMedia = null }
        return
    }

    categoryTitle?.let { title ->
        SearchCategoryPage(title, categoryPlaylists, loading, error, onBack = {
            categoryTitle = null; categoryPlaylists = emptyList(); error = null
        }, onPlaylist = { selectedMedia = it.asSearchItem() })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 26.dp),
    ) {
        Text(
            "搜索",
            modifier = Modifier.padding(horizontal = 20.dp),
            fontSize = 40.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        SearchField(query, { query = it })
        if (query.isNotBlank()) {
            SearchScopes(kind = kind, onKind = { kind = it })
        }

        Box(Modifier.weight(1f)) {
            when {
                query.isBlank() -> SearchDiscovery(
                    recommendations = recommendations,
                    onPlaylist = { selectedMedia = it.asSearchItem() },
                    onCategory = { category ->
                        if (category == "播客") {
                            podcastDiscovery = true
                        } else {
                            categoryTitle = category
                            loading = true; error = null
                            scope.launch {
                                runCatching { library.explorePlaylists(category, 50) }
                                    .onSuccess { categoryPlaylists = it }
                                    .onFailure { error = it.message ?: "类别加载失败" }
                                loading = false
                            }
                        }
                    },
                )
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SearchAccent) }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
                kind == MeloXSearchKind.Songs -> SearchSongResults(songs) { song ->
                    PlaybackCommands.playQueue(context, songs, song.id)
                }
                else -> SearchMediaResults(media) { selectedMedia = it }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(50.dp)
            .meloXLiquidButton(
                shape = RoundedCornerShape(25.dp),
                surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .055f),
                lensRadius = 9.dp,
                refractionHeight = 16.dp,
            )
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("⌕", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isBlank()) Text("音乐内容或网易云链接", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotBlank()) Text("×", modifier = Modifier.clickable { onValueChange("") }.padding(5.dp), fontSize = 22.sp)
    }
}

@Composable
private fun SearchScopes(kind: MeloXSearchKind, onKind: (MeloXSearchKind) -> Unit) {
    val values = MeloXSearchKind.entries.filter { it != MeloXSearchKind.Podcasts || MeloXSettingsRuntime.podcastsEnabled }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(values) { item ->
            Box(
                Modifier.height(34.dp).meloXLiquidButton(
                    shape = RoundedCornerShape(17.dp),
                    tint = if (item == kind) SearchAccent.copy(alpha = .30f) else Color.Transparent,
                    surfaceColor = if (item == kind) SearchAccent.copy(alpha = .16f) else MaterialTheme.colorScheme.onBackground.copy(alpha = .045f),
                ).clickable { onKind(item) }.padding(horizontal = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.title, color = if (item == kind) SearchAccent else MaterialTheme.colorScheme.onSurface, fontWeight = if (item == kind) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SearchDiscovery(
    recommendations: List<NeteasePlaylistSummary>,
    onPlaylist: (NeteasePlaylistSummary) -> Unit,
    onCategory: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MeloXBottomContentClearance),
    ) {
        if (recommendations.isNotEmpty()) {
            item { Text("热门推荐", modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(recommendations, key = { it.id }) { p ->
                        Column(Modifier.width(160.dp).clickable { onPlaylist(p) }) {
                            AsyncImage(p.coverUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(160.dp).clip(RoundedCornerShape(14.dp)))
                            Text(p.name, modifier = Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        item { Text("浏览类别", modifier = Modifier.padding(start = 20.dp, top = 26.dp, bottom = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        items(SearchCategories.filter { it != "播客" || MeloXSettingsRuntime.podcastsEnabled }.chunked(2)) { pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { index, category -> SearchCategoryCard(category, Modifier.weight(1f)) { onCategory(category) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SearchCategoryCard(title: String, modifier: Modifier, onClick: () -> Unit) {
    val tint = when (title.hashCode().mod(5)) {
        0 -> Color(0xFFE76F51); 1 -> Color(0xFF7B61FF); 2 -> Color(0xFF2A9D8F); 3 -> Color(0xFFE84A8A); else -> Color(0xFF3A86FF)
    }
    Box(
        modifier.height(92.dp).clip(RoundedCornerShape(15.dp)).background(tint).clickable(onClick = onClick).padding(14.dp),
        contentAlignment = Alignment.BottomStart,
    ) { Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SearchSongResults(values: List<SearchSong>, onPlay: (SearchSong) -> Unit) {
    if (values.isEmpty()) { SearchEmpty("没有找到歌曲"); return }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = MeloXBottomContentClearance)) {
        items(values, key = { it.id }) { song ->
            Row(Modifier.fillMaxWidth().clickable { onPlay(song) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(song.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text("${song.artists}${song.album.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
        }
    }
}

@Composable
private fun SearchMediaResults(values: List<MeloXSearchMediaItem>, onOpen: (MeloXSearchMediaItem) -> Unit) {
    if (values.isEmpty()) { SearchEmpty("没有找到内容"); return }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = MeloXBottomContentClearance)) {
        items(values, key = { "${it.kind}-${it.id}" }) { item ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(item) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(item.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(54.dp).clip(if (item.kind == MeloXSearchKind.Artists) CircleShape else RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp)
                    Text(item.subtitle.ifBlank { if (item.trackCount > 0) "${item.trackCount} 首" else item.kind.title }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), fontSize = 13.sp)
                }
                Text("›", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .3f))
            }
        }
    }
}

@Composable
private fun SearchCategoryPage(
    title: String,
    values: List<NeteasePlaylistSummary>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onPlaylist: (NeteasePlaylistSummary) -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 16.dp)) {
        SearchDetailHeader(title, onBack)
        when {
            title == "播客" -> SearchEmpty("播客请使用上方搜索框切换到“播客”范围进行搜索。")
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> SearchEmpty(error)
            values.isEmpty() -> SearchEmpty("暂无内容")
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp).let { PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = MeloXBottomContentClearance) }) {
                items(values, key = { it.id }) { p ->
                    Row(Modifier.fillMaxWidth().clickable { onPlaylist(p) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(p.coverUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(58.dp).clip(RoundedCornerShape(9.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text("${p.trackCount} 首歌曲", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCollectionDetail(
    item: MeloXSearchMediaItem,
    universal: NeteaseUniversalSearchClient,
    library: NeteaseLibraryClient,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var songs by remember(item.id, item.kind) { mutableStateOf<List<SearchSong>>(emptyList()) }
    var loading by remember(item.id, item.kind) { mutableStateOf(true) }
    var error by remember(item.id, item.kind) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.id, item.kind) {
        loading = true; error = null
        runCatching {
            if (item.kind == MeloXSearchKind.Playlists) library.playlistDetail(item.id).songs else universal.collectionSongs(item)
        }.onSuccess { songs = it }.onFailure { error = it.message ?: "内容加载失败" }
        loading = false
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 16.dp)) {
        SearchDetailHeader(item.title, onBack)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = MeloXBottomContentClearance)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(item.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(210.dp).clip(if (item.kind == MeloXSearchKind.Artists) CircleShape else RoundedCornerShape(15.dp)))
                    Text(item.title, modifier = Modifier.padding(top = 16.dp), fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.subtitle.isNotBlank()) Text(item.subtitle, modifier = Modifier.padding(top = 5.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SearchPlayButton("随机") {
                            val shuffled = songs.shuffled(); shuffled.firstOrNull()?.let { PlaybackCommands.playQueue(context, shuffled, it.id) }
                        }
                        SearchPlayButton("播放") { songs.firstOrNull()?.let { PlaybackCommands.playQueue(context, songs, it.id) } }
                    }
                }
            }
            when {
                loading -> item { Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                error != null -> item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
                else -> items(songs, key = { it.id }) { song ->
                    Row(Modifier.fillMaxWidth().clickable { PlaybackCommands.playQueue(context, songs, song.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(song.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(7.dp)))
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artists, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchDetailHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).meloXLiquidButton(shape = CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Text("‹", fontSize = 30.sp) }
        Spacer(Modifier.width(12.dp))
        Text(title, Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchPlayButton(title: String, onClick: () -> Unit) {
    Box(Modifier.height(44.dp).width(120.dp).meloXLiquidButton(shape = RoundedCornerShape(22.dp), surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .08f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SearchEmpty(message: String) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) { Text(message, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)) }
}

private fun parseSongLink(value: String): Long? {
    val patterns = listOf(Regex("[?&]id=(\\d+)"), Regex("/song/(\\d+)"))
    return patterns.firstNotNullOfOrNull { it.find(value)?.groupValues?.getOrNull(1)?.toLongOrNull() }
}

private fun NeteasePlaylistSummary.asSearchItem() = MeloXSearchMediaItem(
    id = id,
    kind = MeloXSearchKind.Playlists,
    title = name,
    subtitle = creatorName,
    artworkUrl = coverUrl,
    trackCount = trackCount,
)
