package com.lladlam.melox.ui.cloud

import androidx.compose.runtime.saveable.rememberSaveable
import com.lladlam.melox.ui.state.rememberRetainedPageState

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.R
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.network.MeloXCloudPage
import com.lladlam.melox.core.network.MeloXCloudSong
import com.lladlam.melox.core.network.NeteaseUniversalSearchClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXShapes
import com.lladlam.melox.ui.glass.MeloXSwipeAction
import com.lladlam.melox.ui.glass.MeloXSwipeActionRow
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXTypography
import com.lladlam.melox.ui.glass.MeloXIosTopBar
import com.lladlam.melox.ui.glass.meloXContentSurface
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.meloXEdgeOverscrollHaptic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CloudPageSize = 100
private const val CloudMaximumPages = 100

@Composable
fun MeloXCloudMusicScreen(
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val client = remember(app) {
        NeteaseUniversalSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(app) })
    }
    val scope = rememberCoroutineScope()
    var values by rememberRetainedPageState { mutableStateOf<List<MeloXCloudSong>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var searchingRemaining by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadMoreError by remember { mutableStateOf<String?>(null) }
    var quota by rememberSaveable { mutableStateOf("") }
    var totalCount by rememberSaveable { mutableIntStateOf(0) }
    var usedBytes by rememberSaveable { mutableLongStateOf(0L) }
    var maxBytes by rememberSaveable { mutableLongStateOf(0L) }
    var hasMore by rememberSaveable { mutableStateOf(false) }
    var nextOffset by rememberSaveable { mutableIntStateOf(0) }
    var searchReloadToken by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<MeloXCloudSong?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf<Float?>(null) }
    var uploadError by remember { mutableStateOf<String?>(null) }

    fun applyPage(page: MeloXCloudPage, append: Boolean) {
        values = if (append) {
            (values + page.values).distinctBy(MeloXCloudSong::id)
        } else {
            page.values
        }
        totalCount = page.totalCount
        usedBytes = page.usedBytes
        maxBytes = page.maxBytes
        hasMore = page.hasMore
        nextOffset = values.size
        quota = if (page.maxBytes > 0L) {
            app.getString(R.string.cloud_song_count, formatBytes(usedBytes), formatBytes(maxBytes), totalCount)
        } else {
            app.getString(R.string.cloud_song_total, totalCount)
        }
    }

    suspend fun refresh() {
        if (loading) return
        loading = true
        error = null
        loadMoreError = null
        runCatching { client.cloudSongs(limit = CloudPageSize, offset = 0) }
            .onSuccess {
                applyPage(it, append = false)
                if (query.isNotBlank()) searchReloadToken++
            }
            .onFailure { error = it.message ?: app.getString(R.string.cloud_load_failed) }
        loading = false
    }

    suspend fun loadMore() {
        if (loading || loadingMore || searchingRemaining || !hasMore) return
        loadingMore = true
        loadMoreError = null
        runCatching { client.cloudSongs(limit = CloudPageSize, offset = nextOffset) }
            .onSuccess { page ->
                if (page.values.isEmpty()) {
                    hasMore = false
                } else {
                    applyPage(page, append = true)
                }
            }
            .onFailure { loadMoreError = it.message ?: app.getString(R.string.cloud_load_failed) }
        loadingMore = false
    }

    suspend fun loadRemainingForSearch() {
        if (searchingRemaining || !hasMore) return
        searchingRemaining = true
        loadMoreError = null
        try {
            var pages = 0
            while (hasMore && pages < CloudMaximumPages) {
                val page = runCatching {
                    client.cloudSongs(limit = CloudPageSize, offset = nextOffset)
                }.getOrElse { failure ->
                    loadMoreError = failure.message ?: app.getString(R.string.cloud_load_failed)
                    break
                }
                if (page.values.isEmpty()) {
                    hasMore = false
                    break
                }
                applyPage(page, append = true)
                pages++
            }
        } finally {
            searchingRemaining = false
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            uploadProgress = 0f
            error = null
            runCatching {
                client.uploadCloudSong(app, uri) { sent, total ->
                    if (total > 0L) uploadProgress = (sent.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                }
            }
                .onSuccess {
                    uploadProgress = 1f
                    refresh()
                    if (query.isNotBlank()) loadRemainingForSearch()
                }
                .onFailure { uploadError = it.message ?: app.getString(R.string.cloud_upload_failed) }
            uploading = false
            uploadProgress = null
        }
    }

    LaunchedEffect(Unit) { if (values.isEmpty()) refresh() }

    val normalizedQuery = query.trim()
    val isSearching = normalizedQuery.isNotEmpty()
    LaunchedEffect(normalizedQuery, searchReloadToken) {
        if (normalizedQuery.isEmpty()) return@LaunchedEffect
        delay(300)
        loadRemainingForSearch()
    }

    uploadError?.let { message ->
        MeloXGlassDialog(
            visible = true,
            onDismiss = { uploadError = null },
        ) {
            Text(stringResource(R.string.cloud_upload_failed), style = MeloXTypography.title2)
            Text(
                message,
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                MeloXGlassButton(
                    onClick = { uploadError = null },
                    style = MeloXGlassButtonStyle.Plain,
                ) { Text(stringResource(R.string.action_done)) }
            }
        }
    }

    pendingDelete?.let { target ->
        MeloXGlassDialog(
            visible = true,
            onDismiss = { pendingDelete = null },
        ) {
            Text(stringResource(R.string.cloud_delete_title), style = MeloXTypography.title2)
            Text(
                stringResource(R.string.cloud_delete_body, target.song.name),
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MeloXGlassButton(
                    onClick = { pendingDelete = null },
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.Plain,
                ) { Text(stringResource(R.string.action_cancel)) }
                MeloXGlassButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch {
                            runCatching { client.deleteCloudSong(target.id) }
                                .onSuccess {
                                    values = values.filterNot { it.id == target.id }
                                    totalCount = (totalCount - 1).coerceAtLeast(0)
                                    nextOffset = values.size
                                    usedBytes = (usedBytes - target.fileSize).coerceAtLeast(0L)
                                    quota = if (maxBytes > 0L) {
                                        app.getString(
                                            R.string.cloud_song_count,
                                            formatBytes(usedBytes),
                                            formatBytes(maxBytes),
                                            totalCount,
                                        )
                                    } else {
                                        app.getString(R.string.cloud_song_total, totalCount)
                                    }
                                }
                                .onFailure { error = it.message ?: app.getString(R.string.cloud_delete_failed) }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.Destructive,
                ) { Text(stringResource(R.string.provider_delete)) }
            }
        }
    }

    val displayed = remember(values, normalizedQuery) {
        if (normalizedQuery.isEmpty()) values else values.filter {
            listOf(it.song.name, it.song.artists, it.song.album).any { text ->
                text.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }

    Column(if (embedded) modifier.fillMaxWidth() else modifier.fillMaxSize()) {
        if (embedded) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CloudHeaderActions(
                    uploading = uploading,
                    onUpload = { uploadLauncher.launch("audio/*") },
                    onRefresh = { scope.launch { refresh() } },
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    MeloXIosTopBar(
                        title = stringResource(R.string.cloud_title),
                        subtitle = quota,
                        modifier = Modifier.padding(horizontal = 0.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    )
                }
                CloudHeaderActions(
                    uploading = uploading,
                    onUpload = { uploadLauncher.launch("audio/*") },
                    onRefresh = { scope.launch { refresh() } },
                )
            }
        }
        Box(
            Modifier.padding(horizontal = 18.dp).fillMaxWidth().meloXLiquidButton(
                shape = RoundedCornerShape(22.dp),
                surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .055f),
            ).padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            if (query.isBlank()) {
                Text(stringResource(R.string.cloud_search), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f))
            }
            BasicTextField(
                query,
                { query = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                // 占位符走 onSurface，输入文字也必须走，否则深色模式下是黑字
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                ),
            )
        }
        val cloudListState = rememberLazyListState()
        LaunchedEffect(cloudListState, values.size, hasMore, isSearching) {
            snapshotFlow {
                cloudListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            }.collect { lastVisible ->
                val totalItems = cloudListState.layoutInfo.totalItemsCount
                if (!isSearching && hasMore && lastVisible >= totalItems - 4) {
                    loadMore()
                }
            }
        }
        PullToRefreshBox(
            isRefreshing = loading && values.isNotEmpty(),
            onRefresh = { scope.launch { refresh() } },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                loading && values.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null && values.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        Text(
                            stringResource(R.string.account_retry),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp).clickable { scope.launch { refresh() } },
                        )
                    }
                }
                else -> LazyColumn(
                    state = cloudListState,
                    modifier = Modifier.meloXEdgeOverscrollHaptic(cloudListState),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = MeloXBottomContentClearance),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (uploading) {
                        item(key = "uploading") {
                            Row(
                                Modifier.fillMaxWidth().meloXContentSurface(
                                    shape = MeloXShapes.card,
                                    surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .035f),
                                ).padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                val progress = uploadProgress
                                if (progress != null) {
                                    CircularProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                                Text(
                                    if (progress != null) {
                                        stringResource(R.string.cloud_upload_percent, (progress * 100).toInt())
                                    } else {
                                        stringResource(R.string.cloud_uploading)
                                    },
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                                )
                            }
                        }
                    }
                    if (displayed.isNotEmpty()) {
                        item(key = "play-all") {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (isSearching) {
                                        stringResource(R.string.cloud_search_results, displayed.size)
                                    } else {
                                        quota
                                    },
                                    modifier = Modifier.weight(1f),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f),
                                )
                                Text(
                                    if (isSearching) stringResource(R.string.cloud_play_search_results) else stringResource(R.string.cloud_play_all),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable {
                                        PlaybackCommands.playQueue(
                                            context,
                                            displayed.map(MeloXCloudSong::song),
                                            displayed.first().song.id,
                                        )
                                    }.padding(8.dp),
                                )
                            }
                        }
                    }
                    items(displayed, key = { it.id }) { item ->
                        MeloXSwipeActionRow(
                            startActions = emptyList(),
                            endActions = listOf(
                                MeloXSwipeAction(
                                    label = stringResource(R.string.provider_delete),
                                    symbol = MeloXSymbol.Trash,
                                    color = MaterialTheme.colorScheme.error,
                                ) { pendingDelete = item },
                            ),
                            onClick = {
                                PlaybackCommands.playQueue(context, displayed.map(MeloXCloudSong::song), item.song.id)
                            },
                            onLongClick = null,
                        ) {
                            CloudSongRow(item, onDelete = { pendingDelete = item })
                        }
                    }
                    if (displayed.isEmpty() && !loading && !searchingRemaining && (!isSearching || !hasMore)) {
                        item(key = "empty") {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    if (isSearching) stringResource(R.string.cloud_no_search_results) else stringResource(R.string.cloud_empty),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                                )
                                if (!isSearching) {
                                    MeloXGlassButton(
                                        onClick = { uploadLauncher.launch("audio/*") },
                                        style = MeloXGlassButtonStyle.Plain,
                                    ) { Text(stringResource(R.string.cloud_upload)) }
                                }
                            }
                        }
                    }
                    if (searchingRemaining) {
                        item(key = "searching") {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    stringResource(R.string.cloud_searching_all),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                                )
                            }
                        }
                    }
                    if (loadingMore) {
                        item(key = "loading-more") {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
                        }
                    }
                    loadMoreError?.let { message ->
                        item(key = "load-more-error") {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    message,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                                )
                                Text(
                                    if (isSearching) stringResource(R.string.account_retry) else stringResource(R.string.action_refresh),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 8.dp).clickable {
                                        scope.launch {
                                            if (isSearching) loadRemainingForSearch() else loadMore()
                                        }
                                    },
                                )
                            }
                        }
                    }
                    error?.let { message ->
                        item(key = "error") {
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudHeaderActions(
    uploading: Boolean,
    onUpload: () -> Unit,
    onRefresh: () -> Unit,
) {
    Text(
        if (uploading) stringResource(R.string.cloud_uploading) else stringResource(R.string.cloud_upload),
        color = if (uploading) MaterialTheme.colorScheme.primary.copy(alpha = .45f) else MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(enabled = !uploading, onClick = onUpload).padding(10.dp),
    )
    Text(
        stringResource(R.string.action_refresh),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onRefresh).padding(10.dp),
    )
}

@Composable
private fun CloudSongRow(item: MeloXCloudSong, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .meloXContentSurface(
                shape = MeloXShapes.card,
                surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .035f),
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            item.song.artworkUrl,
            null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(
                    item.song.artists.takeIf(String::isNotBlank),
                    formatBytes(item.fileSize).takeIf { item.fileSize > 0L },
                    item.bitrate.takeIf { it > 0 }?.let { "${it / 1_000} kbps" },
                ).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
            )
        }
        Text(
            stringResource(R.string.provider_delete),
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            modifier = Modifier.clickable(onClick = onDelete).padding(10.dp),
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
