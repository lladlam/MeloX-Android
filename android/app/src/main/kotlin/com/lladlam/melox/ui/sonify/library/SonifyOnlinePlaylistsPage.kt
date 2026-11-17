/*
 * Sonify OnlinePlaylists page (GPL-3.0), adapted for MeloX.
 *
 * Upstream: ui/pages/library/playlists/OnlinePlaylists.kt (master @ 209e1aa).
 * Kept: large-title list, 64dp rows / 52dp artwork, status row, sort menu,
 * create-playlist entry, long-press delete and pull-to-refresh.
 *
 * MeloX replacement: KugouRepository/PendingPlaylistStore -> host callbacks
 * backed by NeteaseMusicOperationsClient (NetEase) or PlaylistSyncCapability
 * (other providers). No pending-cloud propagation cache is copied; mutations
 * simply refresh the MeloX library snapshot.
 */
package com.lladlam.melox.ui.sonify.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.lladlam.melox.ui.sonify.Title
import com.lladlam.melox.ui.sonify.rememberIndexScrollOffsetPx
import com.lladlam.melox.ui.sonify.scrollToIndexedItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SonifyOnlinePlaylistsPage(
    playlists: List<SonifyPlaylistTarget>,
    onBack: () -> Unit,
    onOpenPlaylist: (SonifyPlaylistTarget) -> Unit,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    loading: Boolean = false,
    error: String? = null,
    canCreate: Boolean = false,
    onCreate: (suspend (String) -> Result<Unit>)? = null,
    canDelete: (SonifyPlaylistTarget) -> Boolean = { false },
    onDelete: (suspend (SonifyPlaylistTarget) -> Result<Unit>)? = null,
) {
    var sortOrdinal by rememberSaveable { mutableIntStateOf(SonifyLibrarySort.Name.ordinal) }
    var descending by rememberSaveable { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var createBusy by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<SonifyPlaylistTarget?>(null) }
    var deleteBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val indexed = remember(playlists, sortOrdinal, descending) {
        sonifySortAndIndex(
            items = playlists,
            sortOrdinal = sortOrdinal,
            descending = descending,
            isSearching = false,
            nameSelector = { it.summary.name },
            songCountSelector = { it.summary.trackCount },
        )
    }
    val listState = rememberLazyListState()
    val topBarHeightPx = rememberIndexScrollOffsetPx()

    Title(
        title = "我的在线歌单",
        onBack = onBack,
        listState = listState,
        rightBarIcon = {
            SonifyLibrarySortMenuButton(
                selectedSort = { sortOrdinal },
                onSelectSort = { sortOrdinal = it },
                descending = { descending },
                onSelectDescending = { descending = it },
                leadingAction = if (canCreate && onCreate != null) {
                    SonifyMenuAction("新建歌单") {
                        createName = ""
                        createError = null
                        showCreate = true
                    }
                } else null,
            )
        },
        onRefresh = onRefresh,
        refreshing = refreshing,
        indexBarLabels = indexed.labels,
        onIndexSelect = { index ->
            val target = indexed.firstItemIndices.getOrNull(index) ?: return@Title
            scope.launch { listState.scrollToIndexedItem(2 + target, topBarHeightPx) }
        },
    ) {
        item("Status") {
            SonifyOnlineStatusItem(
                status = error?.let { "error:$it" }
                    ?: when {
                        playlists.isNotEmpty() -> "ok"
                        loading -> "loading"
                        else -> "empty"
                    },
                loadingText = "加载歌单…",
                emptyText = "暂无在线歌单",
            )
        }
        itemsIndexed(
            indexed.sortedItems,
            key = { index, item -> "online-playlist-$index-${item.summary.id}" },
            contentType = { _, _ -> "online_playlist_item" },
        ) { index, target ->
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpenPlaylist(target) },
                        onLongClick = {
                            if (canDelete(target) && onDelete != null) deleteTarget = target
                        },
                    )
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SonifyArtwork(
                    url = target.summary.coverUrl,
                    modifier = Modifier.size(52.dp),
                    cornerRadius = 3.5.dp,
                    contentDescription = target.summary.name,
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                ) {
                    Text(
                        text = target.summary.name,
                        modifier = Modifier.padding(bottom = 1.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 16.sp,
                        lineHeight = 16.sp,
                    )
                    Text(
                        text = "${target.summary.trackCount} 首",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.alpha(0.5f),
                    )
                }
            }
            if (index < indexed.sortedItems.lastIndex) {
                SonifyListDivider(startPadding = 90.dp)
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { if (!createBusy) showCreate = false },
            title = { Text("新建歌单") },
            text = {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .padding(horizontal = 2.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (createName.isBlank()) {
                            Text("歌单名称", modifier = Modifier.alpha(0.45f))
                        }
                        BasicTextField(
                            value = createName,
                            onValueChange = { createName = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    createError?.let { message ->
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !createBusy && createName.isNotBlank() && onCreate != null,
                    onClick = {
                        val create = onCreate ?: return@TextButton
                        createBusy = true
                        createError = null
                        scope.launch {
                            create(createName.trim())
                                .onSuccess {
                                    showCreate = false
                                    createName = ""
                                }
                                .onFailure { createError = it.message ?: "创建歌单失败" }
                            createBusy = false
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(enabled = !createBusy, onClick = { showCreate = false }) { Text("取消") }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!deleteBusy) deleteTarget = null },
            title = { Text("删除歌单") },
            text = { Text("确定删除「${target.summary.name}」？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    enabled = !deleteBusy && onDelete != null,
                    onClick = {
                        val remove = onDelete ?: return@TextButton
                        deleteBusy = true
                        scope.launch {
                            remove(target)
                            deleteBusy = false
                            deleteTarget = null
                        }
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(enabled = !deleteBusy, onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}
