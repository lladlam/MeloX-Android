/*
 * Sonify PlayLists page (GPL-3.0), adapted for MeloX.
 *
 * Upstream: ui/pages/library/playlists/PlayLists.kt (master @ 209e1aa).
 * Kept: large-title list, 80dp rows with 64dp artwork, sort menu, alphabet
 * index and dividers. The local "add playlist" and local-DB favorite rows are
 * omitted because MeloX playlists are provider-backed; the app-owned "我喜欢"
 * collection is still shown when the host supplies it.
 */
package com.lladlam.melox.ui.sonify.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.sonify.Title
import com.lladlam.melox.ui.sonify.rememberIndexScrollOffsetPx
import com.lladlam.melox.ui.sonify.scrollToIndexedItem
import com.lladlam.melox.ui.sonify.withNight
import kotlinx.coroutines.launch

@Composable
internal fun SonifyPlaylistsPage(
    playlists: List<SonifyPlaylistTarget>,
    onBack: () -> Unit,
    onOpenPlaylist: (SonifyPlaylistTarget) -> Unit,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    loading: Boolean = false,
    error: String? = null,
) {
    var sortOrdinal by rememberSaveable { mutableIntStateOf(SonifyLibrarySort.Default.ordinal) }
    var descending by rememberSaveable { mutableStateOf(false) }
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
    val sorted = indexed.sortedItems
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val topBarHeightPx = rememberIndexScrollOffsetPx()

    if (playlists.isEmpty()) {
        Title(
            title = "播放列表",
            onBack = onBack,
            onRefresh = onRefresh,
            refreshing = refreshing,
        ) {
            item("Status") {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    SonifyOnlineStatusItem(
                        status = error?.let { "error:$it" } ?: if (loading) "loading" else "empty",
                        loadingText = "加载歌单…",
                        emptyText = "暂无播放列表",
                    )
                }
            }
        }
        return
    }

    Title(
        title = "播放列表",
        onBack = onBack,
        listState = listState,
        rightBarIcon = {
            SonifyLibrarySortMenuButton(
                selectedSort = { sortOrdinal },
                onSelectSort = { sortOrdinal = it },
                descending = { descending },
                onSelectDescending = { descending = it },
            )
        },
        onRefresh = onRefresh,
        refreshing = refreshing,
        indexBarLabels = indexed.labels,
        onIndexSelect = { index ->
            val target = indexed.firstItemIndices.getOrNull(index) ?: return@Title
            scope.launch { listState.scrollToIndexedItem(1 + target, topBarHeightPx) }
        },
    ) {
        itemsIndexed(
            sorted,
            key = { index, item -> "playlist-$index-${item.summary.id}" },
            contentType = { _, _ -> "playlist_item" },
        ) { index, target ->
            Row(
                Modifier
                    .height(80.dp)
                    .fillMaxWidth()
                    .clickable { onOpenPlaylist(target) }
                    .padding(start = 22.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SonifyArtwork(
                    url = target.summary.coverUrl,
                    modifier = Modifier.size(64.dp),
                    cornerRadius = 4.dp,
                    contentDescription = target.summary.name,
                )
                Column(
                    Modifier
                        .padding(start = 16.dp)
                        .weight(1f),
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
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.ChevronRight,
                    modifier = Modifier.height(12.dp).alpha(0.3f),
                    color = LocalContentColor.current,
                )
            }
            if (index < sorted.lastIndex) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 102.dp, end = 16.dp)
                        .alpha(0.15f)
                        .height(0.5.dp)
                        .background(Color.Black withNight Color.White),
                )
            }
        }
    }
}
