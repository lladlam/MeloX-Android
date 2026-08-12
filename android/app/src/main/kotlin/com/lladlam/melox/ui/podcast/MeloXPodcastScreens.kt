package com.lladlam.melox.ui.podcast

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.network.MeloXPodcast
import com.lladlam.melox.core.network.MeloXPodcastCategory
import com.lladlam.melox.core.network.MeloXPodcastProgram
import com.lladlam.melox.core.network.NeteaseUniversalSearchClient
import com.lladlam.melox.playback.PlaybackCommands
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val PodcastAccent = Color(0xFFFF3147)

/** Upstream PodcastHome/Category/Detail/ProgramDetail navigation in one retained Compose subtree. */
@Composable
fun MeloXPodcastScreen(
    modifier: Modifier = Modifier,
    subscriptionsOnly: Boolean = false,
) {
    val context = LocalContext.current.applicationContext
    val client = remember(context) {
        NeteaseUniversalSearchClient(
            cookieProvider = { NeteaseSessionStore.readCookie(context) },
        )
    }
    var category by remember { mutableStateOf<MeloXPodcastCategory?>(null) }
    var podcast by remember { mutableStateOf<MeloXPodcast?>(null) }
    var program by remember { mutableStateOf<MeloXPodcastProgram?>(null) }
    var subscriptionGeneration by remember { mutableIntStateOf(0) }

    BackHandler(enabled = program != null || podcast != null || category != null) {
        when {
            program != null -> program = null
            podcast != null -> podcast = null
            else -> category = null
        }
    }

    Box(modifier.fillMaxSize()) {
        when {
            program != null -> PodcastProgramDetail(
                program = requireNotNull(program),
                onBack = { program = null },
            )
            podcast != null -> PodcastDetail(
                initialPodcast = requireNotNull(podcast),
                client = client,
                onBack = { podcast = null },
                onProgram = { program = it },
                onSubscriptionChanged = { subscriptionGeneration += 1 },
            )
            category != null -> PodcastCategory(
                category = requireNotNull(category),
                client = client,
                onBack = { category = null },
                onPodcast = { podcast = it },
            )
            else -> PodcastHome(
                client = client,
                subscriptionsOnly = subscriptionsOnly,
                reloadToken = subscriptionGeneration,
                onCategory = { category = it },
                onPodcast = { podcast = it },
            )
        }
    }
}

@Composable
private fun PodcastHome(
    client: NeteaseUniversalSearchClient,
    subscriptionsOnly: Boolean,
    reloadToken: Int,
    onCategory: (MeloXPodcastCategory) -> Unit,
    onPodcast: (MeloXPodcast) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var recommended by remember { mutableStateOf<List<MeloXPodcast>>(emptyList()) }
    var categories by remember { mutableStateOf<List<MeloXPodcastCategory>>(emptyList()) }
    var subscriptions by remember { mutableStateOf<List<MeloXPodcast>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var manualReload by remember { mutableIntStateOf(0) }

    suspend fun load() {
        loading = true
        error = null
        runCatching {
            coroutineScope {
                val subscribed = async { runCatching {
                    if (NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie(context))) {
                        client.subscribedPodcasts(limit = 50).values
                    } else emptyList()
                } }
                val featured = async { runCatching { if (subscriptionsOnly) emptyList() else client.featuredPodcasts() } }
                val personalized = async { runCatching { if (subscriptionsOnly) emptyList() else client.personalizedPodcasts(12) } }
                val loadedCategories = async { runCatching { if (subscriptionsOnly) emptyList() else client.podcastCategories() } }
                val subscribedResult = subscribed.await()
                val featuredResult = featured.await()
                val personalizedResult = personalized.await()
                val categoriesResult = loadedCategories.await()
                val failures = listOf(subscribedResult, featuredResult, personalizedResult, categoriesResult)
                    .mapNotNull { it.exceptionOrNull() }
                if (failures.isNotEmpty() && failures.size == 4) throw failures.first()
                HomePayload(
                    recommended = (featuredResult.getOrDefault(emptyList()) + personalizedResult.getOrDefault(emptyList()))
                        .distinctBy(MeloXPodcast::id),
                    categories = categoriesResult.getOrDefault(emptyList()),
                    subscriptions = subscribedResult.getOrDefault(emptyList()),
                )
            }
        }.onSuccess {
            recommended = it.recommended
            categories = it.categories
            subscriptions = it.subscriptions
        }.onFailure { error = it.message ?: "播客加载失败" }
        loading = false
    }

    LaunchedEffect(reloadToken, manualReload, subscriptionsOnly) { load() }

    PullToRefreshBox(
        isRefreshing = loading,
        onRefresh = { scope.launch { manualReload += 1 } },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 146.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Text(
                    if (subscriptionsOnly) "订阅播客" else "播客",
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (subscriptions.isNotEmpty()) {
                item { PodcastSectionTitle("我的订阅", "${subscriptions.size} 个") }
                item { PodcastStrip(subscriptions, onPodcast) }
            } else if (subscriptionsOnly && !loading) {
                item { PodcastEmpty(error ?: "还没有订阅播客") }
            }
            if (!subscriptionsOnly && recommended.isNotEmpty()) {
                item { PodcastSectionTitle("精选播客", "为你推荐") }
                item { PodcastStrip(recommended, onPodcast) }
            }
            if (!subscriptionsOnly && categories.isNotEmpty()) {
                item { PodcastSectionTitle("浏览分类", "${categories.size} 类") }
                items(categories.chunked(2)) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { value -> PodcastCategoryTile(value, Modifier.weight(1f)) { onCategory(value) } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (!loading && recommended.isEmpty() && categories.isEmpty() && subscriptions.isEmpty() && !subscriptionsOnly) {
                item { PodcastEmpty(error ?: "暂无播客推荐") }
            }
        }
    }
}

private data class HomePayload(
    val recommended: List<MeloXPodcast>,
    val categories: List<MeloXPodcastCategory>,
    val subscriptions: List<MeloXPodcast>,
)

@Composable
private fun PodcastCategory(
    category: MeloXPodcastCategory,
    client: NeteaseUniversalSearchClient,
    onBack: () -> Unit,
    onPodcast: (MeloXPodcast) -> Unit,
) {
    var values by remember(category.id) { mutableStateOf<List<MeloXPodcast>>(emptyList()) }
    var loading by remember(category.id) { mutableStateOf(false) }
    var hasMore by remember(category.id) { mutableStateOf(false) }
    var total by remember(category.id) { mutableIntStateOf(0) }
    var error by remember(category.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load(reset: Boolean) {
        if (loading) return
        loading = true
        val offset = if (reset) 0 else values.size
        runCatching { client.podcastsByCategory(category.id, offset, 30) }
            .onSuccess { page ->
                values = if (reset) page.values else (values + page.values).distinctBy(MeloXPodcast::id)
                hasMore = page.hasMore
                total = page.totalCount
                error = null
            }
            .onFailure { error = it.message ?: "分类加载失败" }
        loading = false
    }
    LaunchedEffect(category.id) { load(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 146.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PodcastBackHeader(category.name, onBack) }
        if (total > 0) item { Text("$total 个播客", color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f), fontSize = 12.sp) }
        items(values, key = { it.id }) { PodcastListRow(it) { onPodcast(it) } }
        item {
            when {
                loading -> PodcastLoading()
                error != null -> PodcastRetry(error.orEmpty()) { scope.launch { load(values.isEmpty()) } }
                hasMore -> PodcastRetry("继续加载") { scope.launch { load(false) } }
                values.isEmpty() -> PodcastEmpty("暂无播客")
            }
        }
    }
}

@Composable
private fun PodcastDetail(
    initialPodcast: MeloXPodcast,
    client: NeteaseUniversalSearchClient,
    onBack: () -> Unit,
    onProgram: (MeloXPodcastProgram) -> Unit,
    onSubscriptionChanged: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var podcast by remember(initialPodcast.id) { mutableStateOf(initialPodcast) }
    var programs by remember(initialPodcast.id) { mutableStateOf<List<MeloXPodcastProgram>>(emptyList()) }
    var ascending by remember(initialPodcast.id) { mutableStateOf(false) }
    var loading by remember(initialPodcast.id) { mutableStateOf(false) }
    var hasMore by remember(initialPodcast.id) { mutableStateOf(false) }
    var subscribing by remember(initialPodcast.id) { mutableStateOf(false) }
    var error by remember(initialPodcast.id) { mutableStateOf<String?>(null) }

    suspend fun load(reset: Boolean) {
        if (loading) return
        loading = true
        val offset = if (reset) 0 else programs.size
        runCatching {
            coroutineScope {
                val detail = async { if (reset) client.podcastDetail(podcast.id) else null }
                val page = async { client.podcastPrograms(podcast.id, offset, 30, ascending) }
                detail.await() to page.await()
            }
        }.onSuccess { (detail, page) ->
            detail?.let { podcast = it }
            programs = if (reset) page.values else (programs + page.values).distinctBy(MeloXPodcastProgram::id)
            hasMore = page.hasMore
            error = null
        }.onFailure { error = it.message ?: "节目加载失败" }
        loading = false
    }
    LaunchedEffect(initialPodcast.id, ascending) { load(true) }
    val songs = programs.mapNotNull(MeloXPodcastProgram::playbackSong)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 146.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PodcastBackHeader("播客", onBack) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(podcast.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(150.dp).clip(RoundedCornerShape(22.dp)))
                Column(Modifier.weight(1f)) {
                    Text(podcast.name, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text(podcast.host?.nickname ?: podcast.category.orEmpty(), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PodcastPill("▶ 播放") { songs.firstOrNull()?.let { PlaybackCommands.playQueue(context, songs, it.id) } }
                        PodcastPill(if (podcast.subscribed) "已订阅" else "订阅", enabled = !subscribing) {
                            if (!NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie(context))) {
                                error = "请先登录网易云音乐后再订阅播客"
                            } else scope.launch {
                                subscribing = true
                                val desired = !podcast.subscribed
                                runCatching { client.setPodcastSubscribed(podcast.id, desired) }
                                    .onSuccess { podcast = podcast.copy(subscribed = desired); onSubscriptionChanged() }
                                    .onFailure { error = it.message ?: "订阅更新失败" }
                                subscribing = false
                            }
                        }
                    }
                }
            }
        }
        podcast.description?.takeIf(String::isNotBlank)?.let { description ->
            item { Text(description, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .58f), fontSize = 13.sp, lineHeight = 19.sp) }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("节目", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(if (ascending) "最早优先" else "最新优先", color = PodcastAccent, modifier = Modifier.clickable { ascending = !ascending }.padding(8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        items(programs, key = { it.id }) { value -> PodcastProgramRow(value, onProgram) }
        item {
            when {
                loading -> PodcastLoading()
                error != null -> PodcastRetry(error.orEmpty()) { scope.launch { load(programs.isEmpty()) } }
                hasMore -> PodcastRetry("加载更多节目") { scope.launch { load(false) } }
                programs.isEmpty() -> PodcastEmpty("暂无节目")
            }
        }
    }
}

@Composable
private fun PodcastProgramDetail(program: MeloXPodcastProgram, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val song = program.playbackSong
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 146.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Box(Modifier.fillMaxWidth()) { PodcastBackHeader("节目", onBack) } }
        item { AsyncImage(program.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(210.dp).clip(RoundedCornerShape(22.dp))) }
        item { Text(program.name, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold) }
        item { Text(program.host?.nickname ?: program.radioName, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f)) }
        item { PodcastPill("▶ 播放节目", enabled = song != null) { song?.let { PlaybackCommands.playQueue(context, listOf(it), it.id) } } }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = .055f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                PodcastInfo("来自", program.radioName)
                program.createTimeMs?.let { PodcastInfo("发布日期", DateFormat.getDateInstance().format(Date(it))) }
                if (program.durationMs > 0L) PodcastInfo("时长", formatDuration(program.durationMs))
                if (program.listenerCount > 0L) PodcastInfo("播放", compactCount(program.listenerCount))
                if (program.likedCount > 0L) PodcastInfo("点赞", compactCount(program.likedCount))
                if (program.commentCount > 0L) PodcastInfo("评论", compactCount(program.commentCount))
            }
        }
        program.description?.takeIf(String::isNotBlank)?.let { item { Text(it, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .62f), lineHeight = 21.sp) } }
    }
}

@Composable private fun PodcastSectionTitle(title: String, trailing: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold); Text(trailing, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .42f), fontSize = 12.sp) }

@Composable
private fun PodcastStrip(values: List<MeloXPodcast>, onPodcast: (MeloXPodcast) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(values, key = { it.id }) { value ->
            Column(Modifier.width(152.dp).clickable { onPodcast(value) }) {
                AsyncImage(value.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(152.dp).clip(RoundedCornerShape(18.dp)))
                Text(value.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 7.dp))
                Text(value.recommendation ?: value.host?.nickname.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .45f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PodcastCategoryTile(value: MeloXPodcastCategory, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.height(72.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = .055f)).clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(value.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)))
        Text(value.name, Modifier.padding(start = 10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PodcastListRow(value: MeloXPodcast, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(74.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(value.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(value.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(value.host?.nickname ?: value.category.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f), fontSize = 12.sp)
        }
        Text("›", color = MaterialTheme.colorScheme.onBackground.copy(alpha = .4f), fontSize = 24.sp)
    }
}

@Composable
private fun PodcastProgramRow(value: MeloXPodcastProgram, onClick: (MeloXPodcastProgram) -> Unit) {
    Row(Modifier.fillMaxWidth().height(76.dp).clickable { onClick(value) }, verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(value.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(58.dp).clip(RoundedCornerShape(13.dp)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(value.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp)
            Text(formatDuration(value.durationMs), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .45f), fontSize = 11.sp)
        }
        Text("›", color = MaterialTheme.colorScheme.onBackground.copy(alpha = .4f), fontSize = 24.sp)
    }
}

@Composable private fun PodcastBackHeader(title: String, onBack: () -> Unit) = Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) { Text("‹", fontSize = 36.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 10.dp)); Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }

@Composable private fun PodcastPill(text: String, enabled: Boolean = true, onClick: () -> Unit) = Text(text, color = if (enabled) Color.White else Color.White.copy(alpha = .45f), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(PodcastAccent.copy(alpha = if (enabled) 1f else .4f)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp))

@Composable private fun PodcastInfo(label: String, value: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f)); Text(value, fontWeight = FontWeight.Medium) }

@Composable private fun PodcastLoading() = Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PodcastAccent) }

@Composable private fun PodcastEmpty(message: String) = Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { Text(message, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f)) }

@Composable private fun PodcastRetry(message: String, onClick: () -> Unit) = Text(message, color = PodcastAccent, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 20.dp))

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val seconds = durationMs / 1_000L
    return if (seconds >= 3_600L) "%d:%02d:%02d".format(seconds / 3_600L, seconds / 60L % 60L, seconds % 60L)
    else "%d:%02d".format(seconds / 60L, seconds % 60L)
}

private fun compactCount(value: Long): String = when {
    value >= 100_000_000L -> "%.1f亿".format(value / 100_000_000.0)
    value >= 10_000L -> "%.1f万".format(value / 10_000.0)
    else -> value.toString()
}
