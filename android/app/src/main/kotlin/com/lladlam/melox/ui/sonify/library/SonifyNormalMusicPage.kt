/*
 * Sonify NormalMusic page (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/pages/library/NormalMusic.kt (master @ 209e1aa).
 * Preserved: large-title scaffold, search, sort menu, alphabet index bar,
 * play / shuffle action row, 64dp song rows and dividers.
 *
 * MeloX replacements:
 *   MusicLibrary.songs / SettingsLibrary -> caller-supplied SearchSong list and
 *   Sonify local sort state; MediaController.prepare -> PlaybackCommands.
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.sonify.Title
import com.lladlam.melox.ui.sonify.rememberIndexScrollOffsetPx
import com.lladlam.melox.ui.sonify.scrollToIndexedItem
import com.lladlam.melox.ui.sonify.withNight
import kotlinx.coroutines.launch

private enum class SonifySongSort {
    Name,
    Artist,
    Duration,
}

@Composable
internal fun SonifyNormalMusicPage(
    title: String,
    songs: List<SearchSong>,
    emptyText: String = "暂无歌曲",
    loading: Boolean = false,
    error: String? = null,
    onBack: () -> Unit,
    onPlay: (List<SearchSong>, Int) -> Unit,
    onMore: (SearchSong, List<SearchSong>) -> Unit,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    var searchText by rememberSaveable(title) { mutableStateOf("") }
    var sortOrdinal by rememberSaveable(title) { mutableIntStateOf(SonifySongSort.Name.ordinal) }
    var descending by rememberSaveable(title) { mutableStateOf(false) }

    val filtered = remember(songs, searchText) {
        if (searchText.isBlank()) songs else songs.filter { song ->
            song.name.contains(searchText, true) ||
                song.artists.contains(searchText, true) ||
                song.album.contains(searchText, true)
        }
    }
    val indexed = remember(filtered, sortOrdinal, descending, searchText) {
        when (sortOrdinal) {
            SonifySongSort.Artist.ordinal -> sonifyAlphabetIndex(
                items = filtered,
                descending = descending,
                isSearching = searchText.isNotBlank(),
                keySelector = { it.artists.substringBefore(" / ") },
            )

            SonifySongSort.Duration.ordinal -> {
                val sorted = filtered.sortedBy { it.durationMs }
                com.lladlam.melox.ui.sonify.IndexedListResult(
                    if (descending) sorted.asReversed() else sorted,
                    emptyList(),
                    emptyList(),
                )
            }

            else -> sonifyAlphabetIndex(
                items = filtered,
                descending = descending,
                isSearching = searchText.isNotBlank(),
                keySelector = { it.name },
            )
        }
    }
    val currentItems = indexed.sortedItems
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val topBarHeightPx = rememberIndexScrollOffsetPx()
    val hasSearch = remember { derivedStateOf { searchText.isNotEmpty() } }

    if (songs.isEmpty() && (loading || error != null)) {
        Title(title = title, onBack = onBack, onRefresh = onRefresh, refreshing = refreshing) {
            item("Status") {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    SonifyOnlineStatusItem(
                        status = error?.let { "error:$it" } ?: "loading",
                        loadingText = "加载中…",
                        emptyText = emptyText,
                    )
                    if (error != null && onRetry != null) {
                        Text(
                            "重试",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable(onClick = onRetry)
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
        return
    }
    if (songs.isEmpty()) {
        Title(title = title, onBack = onBack, onRefresh = onRefresh, refreshing = refreshing) {
            item("Empty") {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    SonifyEmptyStatus(emptyText, horizontalPadding = 0.dp)
                }
            }
        }
        return
    }

    Title(
        title = title,
        onBack = onBack,
        listState = listState,
        rightBarIcon = {
            SonifyLibrarySortMenuButton(
                selectedSort = { sortOrdinal },
                onSelectSort = { sortOrdinal = it },
                descending = { descending },
                onSelectDescending = { descending = it },
                supportsSongCount = false,
            )
        },
        indexBarLabels = indexed.labels,
        onIndexSelect = { index ->
            val target = indexed.firstItemIndices.getOrNull(index) ?: return@Title
            scope.launch { listState.scrollToIndexedItem(3 + target, topBarHeightPx) }
        },
        onRefresh = onRefresh,
        refreshing = refreshing,
    ) {
        item("SearchField") {
            val keyboard = LocalSoftwareKeyboardController.current
            SonifySearchTextField(
                text = searchText,
                placeholder = "搜索歌曲",
                onValueChange = { searchText = it },
                onSearch = { if (hasSearch.value) keyboard?.hide() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(top = 5.dp),
            )
        }
        item("Options") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(top = 12.dp, bottom = 15.dp),
            ) {
                SonifyNormalButton(
                    symbol = MeloXSymbol.Play,
                    label = "播放",
                    modifier = Modifier.weight(1f),
                ) {
                    if (currentItems.isNotEmpty()) onPlay(currentItems, 0)
                }
                Spacer(Modifier.width(15.dp))
                SonifyNormalButton(
                    symbol = MeloXSymbol.Shuffle,
                    label = "随机播放",
                    modifier = Modifier.weight(1f),
                ) {
                    if (currentItems.isNotEmpty()) {
                        onPlay(currentItems.shuffled(), 0)
                    }
                }
            }
        }
        itemsIndexed(
            currentItems,
            key = { index, song -> "sonify-normal-$index-${song.id}" },
            contentType = { _, _ -> "normal_music_item" },
        ) { index, song ->
            SonifyMusicList(
                music = song,
                onMore = { onMore(song, currentItems) },
                itemClick = { onPlay(currentItems, index) },
            )
            if (index < currentItems.lastIndex) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 88.dp, end = 16.dp)
                        .height(0.5.dp)
                        .background((Color.Black withNight Color.White).copy(alpha = 0.15f)),
                )
            }
        }
    }
}
