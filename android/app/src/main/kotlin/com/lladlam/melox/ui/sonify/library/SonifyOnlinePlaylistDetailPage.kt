/*
 * Sonify OnlinePlaylistDetail page (GPL-3.0), adapted for MeloX.
 *
 * Upstream: ui/pages/library/playlists/OnlinePlaylistDetail.kt (master @
 * 209e1aa). Preserved: color-extracted/plain cover header (compact + wide),
 * play/shuffle, in-page morph search entry, result-position-preserving local
 * filter, sort menu, alphabet index, pull-to-refresh, title bar and footer.
 *
 * Removed because MeloX has no equivalent data layer and they would be dead
 * buttons: Kugou add-songs dialog, pending playlist propagation cache,
 * collaborative-playlist ownership logic. The playlist's real MeloX songs and
 * provider-neutral playback/menu actions are used instead.
 */
package com.lladlam.melox.ui.sonify.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.sonify.IndexedListResult
import com.lladlam.melox.ui.sonify.LocalTitlePageBackdrop
import com.lladlam.melox.ui.sonify.MochiIndexBar
import com.lladlam.melox.ui.sonify.TitleBar
import com.lladlam.melox.ui.sonify.isSonifyDark
import com.lladlam.melox.ui.sonify.rememberAlpha
import com.lladlam.melox.ui.sonify.rememberIndexScrollOffsetPx
import com.lladlam.melox.ui.sonify.rememberShowSmallTitle
import com.lladlam.melox.ui.sonify.rememberTitleBackdrop
import com.lladlam.melox.ui.sonify.scrollToIndexedItem
import com.lladlam.melox.ui.sonify.sonifyNavigationBarsHeight
import com.lladlam.melox.ui.sonify.sonifyStatusBarsPadding
import com.lladlam.melox.ui.sonify.withNight
import kotlinx.coroutines.launch

private enum class SonifyPlaylistSort {
    Default,
    Name,
    Artist,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SonifyOnlinePlaylistDetailPage(
    target: SonifyPlaylistTarget,
    onBack: () -> Unit,
    onPlay: (List<SearchSong>, Int) -> Unit,
    onMore: (SearchSong, List<SearchSong>) -> Unit,
    loadPlaylist: suspend () -> Result<SonifyPlaylistData>,
    setSubscribed: (suspend (Boolean) -> Result<Unit>)? = null,
) {
    val isDark = isSonifyDark()
    val pageColor = Color.White withNight Color.Black
    var data by remember(target) { mutableStateOf<SonifyPlaylistData?>(null) }
    var loading by remember(target) { mutableStateOf(true) }
    var refreshing by remember(target) { mutableStateOf(false) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    var sortOrdinal by remember(target) { mutableIntStateOf(SonifyPlaylistSort.Default.ordinal) }
    var descending by remember(target) { mutableStateOf(false) }
    var subscribeBusy by remember(target) { mutableStateOf(false) }
    var actionMessage by remember(target) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val search = rememberSonifyPageSearch(resetKey = target.summary.id)
    BackHandler(enabled = search.expanded) { search.dismiss() }
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    val state = rememberLazyListState()
    val titleBackdrop = rememberTitleBackdrop()
    val showSmallTitle = rememberShowSmallTitle(rememberAlpha(state), state)
    val topBarHeightPx = rememberIndexScrollOffsetPx()

    suspend fun runLoad(pull: Boolean) {
        if (pull) refreshing = true else if (data == null) loading = true
        loadPlaylist()
            .onSuccess {
                data = it
                error = null
            }
            .onFailure { error = it.message ?: "歌单加载失败" }
        loading = false
        refreshing = false
    }

    LaunchedEffect(target) { runLoad(pull = false) }

    val songs = data?.songs.orEmpty()
    val alphabet = remember(songs, sortOrdinal, descending, search.keyword) {
        if (search.keyword.isNotEmpty() || sortOrdinal == SonifyPlaylistSort.Default.ordinal) {
            IndexedListResult(emptyList(), emptyList(), emptyList())
        } else {
            sonifyAlphabetIndex(
                items = songs,
                descending = descending,
                isSearching = false,
                keySelector = if (sortOrdinal == SonifyPlaylistSort.Artist.ordinal) {
                    { it.artists.substringBefore(" / ") }
                } else {
                    { it.name }
                },
            )
        }
    }
    val sorted = remember(songs, sortOrdinal, descending, alphabet) {
        when {
            sortOrdinal == SonifyPlaylistSort.Default.ordinal ->
                if (descending) songs.asReversed() else songs
            alphabet.sortedItems.isNotEmpty() -> alphabet.sortedItems
            descending -> songs.asReversed()
            else -> songs
        }
    }
    val visible = remember(sorted, search.keyword) {
        sonifyFilterTracks(
            tracks = sorted,
            query = search.keyword,
            name = { it.name },
            artist = { it.artists },
        )
    }
    val visibleSongs = remember(visible) { visible.map { it.second } }
    val subscribeAction: (@Composable () -> Unit)? =
        if (setSubscribed != null && data?.subscribed != null) {
            {
                val subscribed = data?.subscribed == true
                SonifyDropdownMenuAction(if (subscribed) "取消收藏歌单" else "收藏歌单") {
                    if (!subscribeBusy) {
                        subscribeBusy = true
                        scope.launch {
                            setSubscribed(!subscribed)
                                .onSuccess {
                                    data = data?.copy(subscribed = !subscribed)
                                    actionMessage = null
                                }
                                .onFailure { actionMessage = it.message ?: "操作失败" }
                            subscribeBusy = false
                        }
                    }
                }
            }
        } else {
            null
        }
    val tracksStartIndex = 3 + (if (search.expanded) 1 else 0)

    CompositionLocalProvider(LocalTitlePageBackdrop provides titleBackdrop) {
        Box(Modifier.fillMaxSize().background(pageColor)) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { scope.launch { runLoad(pull = true) } },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = state,
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(titleBackdrop)
                        .background(pageColor),
                    contentPadding = PaddingValues(bottom = 18.dp, top = 54.dp),
                ) {
                    item("PlaylistHeader") {
                        val playlist = data
                        if (playlist == null) {
                            if (loading) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                                    SonifyArtistSkeletonBlock(Modifier.fillMaxWidth().height(220.dp))
                                }
                            }
                        } else if (isWide) {
                            SonifyDetailPageHeader(
                                title = playlist.name,
                                coverUrl = playlist.artworkUrl,
                                tag = "PLAYLIST",
                                onPlayAll = {
                                    if (playlist.songs.isNotEmpty()) onPlay(playlist.songs, 0)
                                },
                                onShuffle = {
                                    if (playlist.songs.isNotEmpty()) onPlay(playlist.songs.shuffled(), 0)
                                },
                                countText = "${playlist.songs.size} 首",
                                intro = playlist.description,
                                trailingSymbol = if (!search.expanded) MeloXSymbol.Search else null,
                                trailingDescription = "搜索歌单内的歌曲",
                                onTrailingClick = if (!search.expanded) ({ search.open() }) else null,
                            )
                        } else {
                            SonifyCompactDetailHeader(
                                title = playlist.name,
                                coverUrl = playlist.artworkUrl,
                                meta = "${playlist.songs.size} 首",
                                tag = "PLAYLIST",
                                playEnabled = playlist.songs.isNotEmpty(),
                                onPlayAll = { onPlay(playlist.songs, 0) },
                                onShuffle = { onPlay(playlist.songs.shuffled(), 0) },
                                intro = playlist.description,
                                trailingSymbol = if (!search.expanded) MeloXSymbol.Search else null,
                                trailingDescription = "搜索歌单内的歌曲",
                                onTrailingClick = if (!search.expanded) ({ search.open() }) else null,
                            )
                        }
                    }

                    if (search.expanded) {
                        item("WideSearchField") {
                            SonifyWideSearchFieldRow(
                                query = search.query,
                                onQueryChange = { search.query = it },
                                onClear = {
                                    search.clearQuery()
                                },
                                contentColor = androidx.compose.material3.LocalContentColor.current,
                                focusRequester = search.fieldFocus,
                            )
                        }
                    }

                    item("Status") {
                        SonifyOnlineStatusItem(
                            status = error?.let { "error:$it" }
                                ?: when {
                                    visibleSongs.isNotEmpty() -> "ok"
                                    loading -> "loading"
                                    search.keyword.isNotEmpty() -> "empty"
                                    else -> "empty"
                                },
                            loadingText = "加载歌曲…",
                            emptyText = if (search.keyword.isNotEmpty()) "没有匹配的歌曲" else "歌单还没有歌曲",
                        )
                    }

                    if (visibleSongs.isNotEmpty()) {
                        item("PlaylistDividerTop") { SonifyDetailDivider() }
                    }

                    itemsIndexed(
                        visibleSongs,
                        key = { index, song -> "playlist-track-$index-${song.id}" },
                        contentType = { _, _ -> "online_playlist_track" },
                    ) { index, song ->
                        if (isWide) {
                            SonifyDetailSongRowWide(
                                music = song,
                                onMore = { onMore(song, visibleSongs) },
                                itemClick = { onPlay(visibleSongs, index) },
                            )
                        } else {
                            SonifyMusicList(
                                music = song,
                                onMore = { onMore(song, visibleSongs) },
                                itemClick = { onPlay(visibleSongs, index) },
                            )
                        }
                        if (index < visibleSongs.lastIndex) {
                            Spacer(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 88.dp, end = 16.dp)
                                    .height(0.5.dp)
                                    .background(Color.Black withNight Color.White),
                            )
                        }
                    }

                    if (visibleSongs.isNotEmpty()) {
                        item("PlaylistDividerBottom") { SonifyDetailDivider() }
                        item("PlaylistOthers") {
                            Text(
                                text = "${visibleSongs.size} 首歌曲, 约 ${visibleSongs.sumOf { it.durationMs } / 60_000} 分钟",
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .padding(horizontal = 18.dp)
                                    .padding(top = 18.dp),
                            )
                        }
                    }

                    actionMessage?.let { message ->
                        item("ActionMessage") {
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                            )
                        }
                    }

                    item("navbar") { Spacer(Modifier.sonifyNavigationBarsHeight(134.dp)) }
                }
            }

            TitleBar(
                title = data?.name ?: target.summary.name,
                onBack = { if (search.expanded) search.dismiss() else onBack() },
                showSmallTitle = showSmallTitle,
                backdrop = titleBackdrop,
                rightBarIcon = {
                    SonifyDetailSortMenuButton(
                        sortOptions = listOf(
                            SonifyPlaylistSort.Default.ordinal to "默认排序",
                            SonifyPlaylistSort.Name.ordinal to "按名称排序",
                            SonifyPlaylistSort.Artist.ordinal to "按歌手排序",
                        ),
                        selectedSort = sortOrdinal,
                        onSelectSort = { sortOrdinal = it },
                        descending = descending,
                        onSelectDescending = { descending = it },
                        leadingContent = subscribeAction,
                    )
                },
            )

            if (alphabet.labels.isNotEmpty()) {
                MochiIndexBar(
                    labels = alphabet.labels,
                    onSelect = { index ->
                        val targetIndex = alphabet.firstItemIndices.getOrNull(index) ?: return@MochiIndexBar
                        scope.launch {
                            state.scrollToIndexedItem(tracksStartIndex + targetIndex, topBarHeightPx)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .sonifyStatusBarsPadding()
                        .navigationBarsPadding()
                        .padding(top = 54.dp, bottom = 110.dp, end = 2.dp),
                )
            }
        }
    }
}
