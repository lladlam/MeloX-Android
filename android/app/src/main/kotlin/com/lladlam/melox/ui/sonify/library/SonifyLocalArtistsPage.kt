/*
 * Sonify LocalArtists page (GPL-3.0), adapted for MeloX.
 *
 * Upstream: ui/pages/library/artists/LocalArtists.kt (master @ 209e1aa).
 * Kept: large-title list, search, sort menu, alphabet index, 56dp rows with
 * 48dp circular artwork and 15dp gap.
 *
 * Local artist rows intentionally open the Sonify NormalMusic list of that
 * artist's local songs, matching upstream's local-library behaviour.
 */
package com.lladlam.melox.ui.sonify.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
internal fun SonifyLocalArtistsPage(
    artists: List<SonifyLocalArtist>,
    onBack: () -> Unit,
    onOpenSongs: (SonifyLocalArtist) -> Unit,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
) {
    var searchText by rememberSaveable { mutableStateOf("") }
    var sortOrdinal by rememberSaveable { mutableIntStateOf(SonifyLibrarySort.Name.ordinal) }
    var descending by rememberSaveable { mutableStateOf(false) }

    val display = remember(artists, searchText) {
        if (searchText.isBlank()) artists else artists.filter {
            it.name.contains(searchText, true)
        }
    }
    val indexed = remember(display, sortOrdinal, descending, searchText) {
        sonifySortAndIndex(
            items = display,
            sortOrdinal = sortOrdinal,
            descending = descending,
            isSearching = searchText.isNotBlank(),
            nameSelector = { it.name },
            songCountSelector = { it.songs.size },
        )
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val topBarHeightPx = rememberIndexScrollOffsetPx()

    if (artists.isEmpty()) {
        Title(
            title = "艺人",
            onBack = onBack,
            onRefresh = onRefresh,
            refreshing = refreshing,
        ) {
            item("tip_no_artist") {
                SonifyColumnEmpty("暂无艺人")
            }
        }
        return
    }

    Title(
        title = "艺人",
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
            scope.launch { listState.scrollToIndexedItem(2 + target, topBarHeightPx) }
        },
    ) {
        item("SearchField") {
            val keyboard = LocalSoftwareKeyboardController.current
            SonifySearchTextField(
                text = searchText,
                placeholder = "搜索艺人",
                onValueChange = { searchText = it },
                onSearch = { if (searchText.isNotEmpty()) keyboard?.hide() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(top = 5.dp, bottom = 12.dp),
            )
        }
        itemsIndexed(
            indexed.sortedItems,
            key = { index, artist -> "local-artist-$index-${artist.name}" },
            contentType = { _, _ -> "local_artist" },
        ) { index, artist ->
            SonifyLocalArtistRow(artist) { onOpenSongs(artist) }
            if (index < indexed.sortedItems.lastIndex) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 81.dp, end = 16.dp)
                        .alpha(0.15f)
                        .height(0.5.dp)
                        .background(Color.Black withNight Color.White),
                )
            }
        }
    }
}

@Composable
private fun SonifyLocalArtistRow(
    artist: SonifyLocalArtist,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SonifyArtwork(
            url = artist.artworkUrl,
            modifier = Modifier.size(48.dp).clip(CircleShape),
            cornerRadius = 24.dp,
            contentDescription = null,
        )
        Spacer(Modifier.width(15.dp))
        Column(Modifier.fillMaxWidth().weight(1f)) {
            Text(
                text = artist.name,
                fontSize = 16.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
            )
            Text(
                text = "${artist.songs.size} 首歌曲",
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(0.5f),
            )
        }
        MeloXSymbolIcon(
            symbol = MeloXSymbol.ChevronRight,
            modifier = Modifier.height(12.dp).padding(end = 8.dp).alpha(0.3f),
            color = LocalContentColor.current,
        )
    }
}
