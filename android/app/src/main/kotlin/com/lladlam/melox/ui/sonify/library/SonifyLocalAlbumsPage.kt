/*
 * Sonify LocalAlbums page (GPL-3.0), adapted for MeloX.
 *
 * Upstream: ui/pages/library/albums/LocalAlbums.kt (master @ 209e1aa).
 * Kept: large-title + adaptive square grid, first-row search, 7dp artwork,
 * 14sp title / 13sp "N 首" caption, alphabet index and sort menu.
 *
 * MeloX replacement: MusicLibrary.albums/MusicLibrary.Album -> caller-supplied
 * SonifyLocalAlbum list grouped from MeloX SearchSong data.
 */
package com.lladlam.melox.ui.sonify.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.ui.sonify.TitleWithLazyVerticalGrid
import com.lladlam.melox.ui.sonify.rememberIndexScrollOffsetPx
import com.lladlam.melox.ui.sonify.scrollToIndexedItem
import kotlinx.coroutines.launch

@Composable
internal fun SonifyLocalAlbumsPage(
    albums: List<SonifyLocalAlbum>,
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
            album.name.contains(searchText, true) || album.artistText.contains(searchText, true)
        }
    }
    val indexed = remember(filtered, sortOrdinal, descending, searchText) {
        sonifySortAndIndex(
            items = filtered,
            sortOrdinal = sortOrdinal,
            descending = descending,
            isSearching = searchText.isNotBlank(),
            nameSelector = { it.name },
            songCountSelector = { it.songs.size },
        )
    }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val topBarHeightPx = rememberIndexScrollOffsetPx()

    Column(Modifier.fillMaxSize()) {
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
                            else -> "暂无专辑"
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
                    key = { index, album -> "local-album-$index-${album.name}" },
                    contentType = { _, _ -> "LocalAlbums_item" },
                ) { _, album ->
                    SonifyAlbumGridCard(
                        title = album.name,
                        artistOrCount = "${album.songs.size} 首 · ${album.artistText}",
                        artworkUrl = album.artworkUrl,
                    ) {
                        onOpenAlbum(
                            SonifyAlbumTarget(
                                title = album.name,
                                artistText = album.artistText,
                                artworkUrl = album.artworkUrl,
                                localSongs = album.songs,
                                trackCount = album.songs.size,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LazyGridItemScope.SonifyAlbumGridCard(
    title: String,
    artistOrCount: String?,
    artworkUrl: String?,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            SonifyArtwork(
                url = artworkUrl,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 7.dp,
                contentDescription = title,
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(0.9f),
        )
        if (!artistOrCount.isNullOrBlank()) {
            Text(
                text = artistOrCount,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(0.6f),
                lineHeight = 17.sp,
            )
        }
    }
}
