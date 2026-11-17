/*
 * Sonify OnlineArtists page (GPL-3.0), adapted for MeloX.
 *
 * Upstream: ui/pages/library/artists/OnlineArtists.kt (master @ 209e1aa).
 * Kept: large-title list, search, sort menu, pull-to-refresh, circular
 * avatars and direct artist-detail navigation.
 *
 * Upstream's FollowedArtistsObject/KugouAccountState is replaced by MeloX
 * provider-neutral followed artists (or the NetEase artist-sublist loaded by
 * the host). Login is handled by the surrounding MeloX account flow.
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
internal fun SonifyOnlineArtistsPage(
    artists: List<SonifyArtistTarget>,
    onBack: () -> Unit,
    onOpenArtist: (SonifyArtistTarget) -> Unit,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    loading: Boolean = false,
    error: String? = null,
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
            item("Status") {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    SonifyOnlineStatusItem(
                        status = error?.let { "error:$it" } ?: if (loading) "loading" else "empty",
                        loadingText = "加载关注艺人…",
                        emptyText = "暂无关注艺人",
                    )
                }
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
                supportsSongCount = false,
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
            key = { index, artist -> "online-artist-$index-${artist.name}" },
            contentType = { _, _ -> "online_artist_item" },
        ) { index, artist ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { onOpenArtist(artist) }
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
                Text(
                    text = artist.name,
                    fontSize = 16.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f),
                )
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.ChevronRight,
                    modifier = Modifier.height(12.dp).padding(end = 8.dp).alpha(0.3f),
                    color = LocalContentColor.current,
                )
            }
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
