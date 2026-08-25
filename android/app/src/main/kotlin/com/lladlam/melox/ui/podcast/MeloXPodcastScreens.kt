package com.lladlam.melox.ui.podcast

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.network.MeloXPodcast
import com.lladlam.melox.core.network.MeloXPodcastCategory
import com.lladlam.melox.core.network.MeloXPodcastProgram
import com.lladlam.melox.core.network.NeteaseUniversalSearchClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassCard
import com.lladlam.melox.ui.glass.MeloXPinnedListPage
import com.lladlam.melox.ui.glass.MeloXShapes
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun MeloXPodcastScreen(
    modifier: Modifier = Modifier,
    subscriptionsOnly: Boolean = false,
    initialPodcastId: Long? = null,
    onExit: (() -> Unit)? = null,
    bottomPadding: Dp = MeloXBottomContentClearance,
) {
    val context = LocalContext.current.applicationContext
    val client = remember(context) {
        NeteaseUniversalSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
    }
    var selectedPodcast by remember(initialPodcastId) {
        mutableStateOf(initialPodcastId?.let { MeloXPodcast(id = it, name = "播客") })
    }
    var subscriptionGeneration by remember { mutableIntStateOf(0) }

    BackHandler(enabled = selectedPodcast != null) {
        if (initialPodcastId != null) onExit?.invoke() else selectedPodcast = null
    }

    Box(modifier.fillMaxSize()) {
        selectedPodcast?.let { podcast ->
            PodcastDetail(
                initialPodcast = podcast,
                client = client,
                bottomPadding = bottomPadding,
                onBack = {
                    if (initialPodcastId != null) onExit?.invoke() else selectedPodcast = null
                },
                onSubscriptionChanged = { subscriptionGeneration++ },
            )
        } ?: PodcastHome(
            client = client,
            subscriptionsOnly = subscriptionsOnly,
            reloadToken = subscriptionGeneration,
            bottomPadding = bottomPadding,
            onPodcast = { selectedPodcast = it },
        )
    }
}

@Composable
private fun PodcastHome(
    client: NeteaseUniversalSearchClient,
    subscriptionsOnly: Boolean,
    reloadToken: Int,
    bottomPadding: Dp,
    onPodcast: (MeloXPodcast) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var payload by remember { mutableStateOf(PodcastHomePayload()) }
    var selectedCategory by remember { mutableStateOf<MeloXPodcastCategory?>(null) }
    var categoryPodcasts by remember { mutableStateOf<List<MeloXPodcast>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var categoryLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var manualReload by remember { mutableIntStateOf(0) }

    suspend fun loadHome() {
        loading = true
        error = null
        runCatching {
            coroutineScope {
                if (subscriptionsOnly) {
                    val loggedIn = NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie(context))
                    PodcastHomePayload(
                        subscriptions = if (loggedIn) client.subscribedPodcasts(limit = 100).values else emptyList(),
                    )
                } else {
                    val featured = async { client.featuredPodcasts() }
                    val personalized = async { client.personalizedPodcasts(12) }
                    val categories = async { client.podcastCategories() }
                    PodcastHomePayload(
                        featured = featured.await(),
                        personalized = personalized.await(),
                        categories = categories.await(),
                    )
                }
            }
        }.onSuccess { payload = it }
            .onFailure { error = it.message ?: "播客加载失败" }
        loading = false
    }

    LaunchedEffect(reloadToken, manualReload, subscriptionsOnly) { loadHome() }
    LaunchedEffect(selectedCategory?.id) {
        val category = selectedCategory ?: run {
            categoryPodcasts = emptyList()
            return@LaunchedEffect
        }
        categoryLoading = true
        runCatching { client.podcastsByCategory(category.id, offset = 0, limit = 30).values }
            .onSuccess { loaded -> if (selectedCategory?.id == category.id) categoryPodcasts = loaded }
            .onFailure { error = it.message ?: "分类加载失败" }
        categoryLoading = false
    }

    PullToRefreshBox(
        isRefreshing = loading,
        onRefresh = { manualReload++ },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            loading && payload.isEmpty -> PodcastLoadingState()
            error != null && payload.isEmpty -> PodcastErrorState(error.orEmpty()) { scope.launch { manualReload++ } }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 22.dp, bottom = bottomPadding + 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item(key = "title") {
                    Text(
                        if (subscriptionsOnly) "订阅播客" else "播客",
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (!subscriptionsOnly && payload.categories.isNotEmpty()) {
                    item(key = "categories") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item(key = "all") {
                                PodcastCategoryButton("为你推荐", selectedCategory == null) { selectedCategory = null }
                            }
                            items(payload.categories, key = MeloXPodcastCategory::id) { category ->
                                PodcastCategoryButton(category.name, selectedCategory?.id == category.id) {
                                    selectedCategory = category
                                }
                            }
                        }
                    }
                }

                if (subscriptionsOnly) {
                    if (payload.subscriptions.isNotEmpty()) {
                        item(key = "subscriptions") {
                            PodcastSection("我的订阅", payload.subscriptions, onPodcast)
                        }
                    } else if (!loading) {
                        item(key = "empty-subscriptions") {
                            PodcastEmptyState(
                                if (NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie(context))) {
                                    "还没有订阅播客"
                                } else {
                                    "登录网易云音乐后查看订阅播客"
                                },
                            )
                        }
                    }
                } else {
                    item(key = "primary-section") {
                        when {
                            categoryLoading -> PodcastLoadingInline()
                            selectedCategory != null -> PodcastSection("${selectedCategory?.name.orEmpty()}播客", categoryPodcasts, onPodcast)
                            else -> PodcastSection("为你推荐", payload.personalized, onPodcast)
                        }
                    }
                    if (payload.featured.isNotEmpty()) {
                        item(key = "featured") { PodcastSection("精选播客", payload.featured, onPodcast) }
                    }
                }
            }
        }
    }
}

private data class PodcastHomePayload(
    val featured: List<MeloXPodcast> = emptyList(),
    val personalized: List<MeloXPodcast> = emptyList(),
    val categories: List<MeloXPodcastCategory> = emptyList(),
    val subscriptions: List<MeloXPodcast> = emptyList(),
) {
    val isEmpty: Boolean
        get() = featured.isEmpty() && personalized.isEmpty() && categories.isEmpty() && subscriptions.isEmpty()
}

@Composable
private fun PodcastCategoryButton(title: String, selected: Boolean, onClick: () -> Unit) {
    MeloXGlassButton(
        onClick = onClick,
        modifier = Modifier.height(44.dp),
        style = if (selected) MeloXGlassButtonStyle.BorderedProminent else MeloXGlassButtonStyle.Bordered,
        shape = MeloXShapes.capsule,
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 9.dp),
    ) { Text(title, maxLines = 1) }
}

@Composable
private fun PodcastSection(title: String, podcasts: List<MeloXPodcast>, onPodcast: (MeloXPodcast) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (podcasts.isEmpty()) {
            PodcastEmptyState("暂无播客")
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(podcasts, key = MeloXPodcast::id) { podcast ->
                    MeloXGlassCard(
                        modifier = Modifier.size(width = 176.dp, height = 244.dp),
                        onClick = { onPodcast(podcast) },
                    ) {
                        AsyncImage(
                            model = podcast.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)),
                        )
                        Text(
                            podcast.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            podcast.recommendation ?: podcast.host?.nickname.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastDetail(
    initialPodcast: MeloXPodcast,
    client: NeteaseUniversalSearchClient,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onSubscriptionChanged: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var podcast by remember(initialPodcast.id) { mutableStateOf(initialPodcast) }
    var programs by remember(initialPodcast.id) { mutableStateOf<List<MeloXPodcastProgram>>(emptyList()) }
    var loading by remember(initialPodcast.id) { mutableStateOf(true) }
    var subscribing by remember(initialPodcast.id) { mutableStateOf(false) }
    var error by remember(initialPodcast.id) { mutableStateOf<String?>(null) }
    var reloadKey by remember(initialPodcast.id) { mutableIntStateOf(0) }

    LaunchedEffect(initialPodcast.id, reloadKey) {
        loading = true
        error = null
        runCatching {
            coroutineScope {
                val detail = async { client.podcastDetail(initialPodcast.id) }
                val episodes = async { client.podcastPrograms(initialPodcast.id, offset = 0, limit = 100, ascending = false) }
                detail.await() to episodes.await().values
            }
        }.onSuccess { (loadedPodcast, loadedPrograms) ->
            loadedPodcast?.let { podcast = it }
            programs = loadedPrograms
        }.onFailure { error = it.message ?: "节目加载失败" }
        loading = false
    }

    MeloXPinnedListPage(
        title = podcast.name,
        subtitle = podcast.host?.nickname,
        onNavigateBack = onBack,
        bottomPadding = bottomPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        actions = {
            MeloXGlassButton(
                onClick = {
                    if (!NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie(context))) {
                        error = "请先登录网易云音乐后再订阅播客"
                    } else if (!subscribing) {
                        val desired = !podcast.subscribed
                        subscribing = true
                        scope.launch {
                            runCatching { client.setPodcastSubscribed(podcast.id, desired) }
                                .onSuccess {
                                    podcast = podcast.copy(subscribed = desired)
                                    onSubscriptionChanged()
                                }
                                .onFailure { error = it.message ?: "订阅更新失败" }
                            subscribing = false
                        }
                    }
                },
                enabled = !subscribing,
                style = if (podcast.subscribed) MeloXGlassButtonStyle.BorderedProminent else MeloXGlassButtonStyle.Bordered,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) { Text(if (podcast.subscribed) "已订阅" else "订阅", maxLines = 1) }
        },
    ) {
        item(key = "podcast-hero") {
            MeloXGlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = podcast.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(112.dp).clip(RoundedCornerShape(22.dp)),
                    )
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            podcast.host?.nickname ?: podcast.category.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            podcast.description.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (loading) item(key = "loading") { PodcastLoadingInline() }
        error?.let { message ->
            item(key = "error") {
                MeloXGlassCard(Modifier.fillMaxWidth()) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    MeloXGlassButton(
                        onClick = { reloadKey++ },
                        modifier = Modifier.fillMaxWidth(),
                        style = MeloXGlassButtonStyle.BorderedProminent,
                    ) { Text("重试") }
                }
            }
        }
        if (!loading && programs.isEmpty() && error == null) item(key = "empty") { PodcastEmptyState("暂无节目") }
        programs.forEach { program ->
            item(key = "program-${program.id}") {
                PodcastProgramRow(program) {
                    val songs = programs.mapNotNull(MeloXPodcastProgram::playbackSong)
                    program.playbackSong?.let { selected ->
                        PlaybackCommands.playQueue(context, songs, selected.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastProgramRow(program: MeloXPodcastProgram, onClick: () -> Unit) {
    MeloXGlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = program.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    program.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (program.durationMs > 0L) "${program.durationMs / 60_000L} 分钟" else "时长未知",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            MeloXSymbolIcon(MeloXSymbol.Play, Modifier.size(22.dp), MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun PodcastLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun PodcastLoadingInline() {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun PodcastErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            MeloXGlassButton(onRetry, style = MeloXGlassButtonStyle.BorderedProminent) { Text("重试") }
        }
    }
}

@Composable
private fun PodcastEmptyState(message: String) {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
