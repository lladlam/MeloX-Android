/*
 * Sonify CollectedAlbums page (GPL-3.0), adapted for MeloX.
 *
 * Upstream: ui/pages/library/albums/CollectedAlbums.kt (master @ 209e1aa).
 * Kept the local-albums grid visual, search, sort, pull-to-refresh and
 * loading/empty/error status. Upstream's Kugou collected-album objects are
 * replaced by MeloX provider-neutral saved albums plus NetEase album-sublist
 * data loaded by the host.
 */
package com.lladlam.melox.ui.sonify.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.lladlam.melox.ui.sonify.TitleWithLazyVerticalGrid
import com.lladlam.melox.ui.sonify.rememberIndexScrollOffsetPx
import com.lladlam.melox.ui.sonify.scrollToIndexedItem
import kotlinx.coroutines.launch

@Composable
internal fun SonifyCollectedAlbumsPage(
    albums: List<SonifyAlbumCard>,
    onBack: () -> Unit,
    onOpenAlbum: (SonifyAlbumTarget) -> Unit,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    loading: Boolean = false,
    error: String? = null,
) {
    var searchText by rememberSaveable { mutableStateOf("") }
    var sortOrdinal by rememberSaveable { mutableIntStateOf(SonifyLibrarySort.Name.ordinal) }
    var descending by rememberSaveable { mutableStateOf(false) }

    val filtered = remember(albums, searchText) {
        if (searchText.isBlank()) albums else albums.filter { album ->
            album.title.contains(searchText, true) || album.artistText.contains(searchText, true)
        }
    }
    val indexed = remember(filtered, sortOrdinal, descending, searchText) {
        sonifySortAndIndex(
            items = filtered,
            sortOrdinal = sortOrdinal,
            descending = descending,
            isSearching = searchText.isNotBlank(),
            nameSelector = { it.title },
            songCountSelector = { it.songCount },
        )
    }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val topBarHeightPx = rememberIndexScrollOffsetPx()

    TitleWithLazyVerticalGrid(
        title = "专辑",
        onBack = onBack,
        gridState = gridState,
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
            val target = indexed.firstItemIndices.getOrNull(index) ?: return@TitleWithLazyVerticalGrid
            scope.launch { gridState.scrollToIndexedItem(2 + target, topBarHeightPx) }
        },
    ) {
        item("SearchField", span = { GridItemSpan(maxLineSpan) }) {
            val keyboard = LocalSoftwareKeyboardController.current
            SonifySearchTextField(
                text = searchText,
                placeholder = "搜索专辑",
                onValueChange = { searchText = it },
                onSearch = { if (searchText.isNotEmpty()) keyboard?.hide() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (indexed.sortedItems.isEmpty()) {
            item("Status", span = { GridItemSpan(maxLineSpan) }) {
                SonifyStatusFooter(
                    text = error ?: when {
                        loading -> "加载中…"
                        searchText.isNotBlank() -> "没有匹配的专辑"
                        else -> "暂无收藏专辑"
                    },
                    showSpinner = loading,
                    isError = error != null,
                    retry = error != null && onRefresh != null,
                    onRetry = { onRefresh?.invoke() },
                )
            }
        } else {
            itemsIndexed(
                indexed.sortedItems,
                key = { index, album -> "collected-album-$index-${album.title}" },
                contentType = { _, _ -> "collected_album_item" },
            ) { _, album ->
                SonifyAlbumGridCard(
                    title = album.title,
                    artistOrCount = buildString {
                        if (album.songCount > 0) append("${album.songCount} 首")
                        if (album.songCount > 0 && album.artistText.isNotBlank()) append(" · ")
                        if (album.artistText.isNotBlank()) append(album.artistText)
                    },
                    artworkUrl = album.artworkUrl,
                ) {
                    onOpenAlbum(album.target)
                }
            }
        }
    }
}

internal val SonifyCollectedAlbumGridPadding = 18.dp
