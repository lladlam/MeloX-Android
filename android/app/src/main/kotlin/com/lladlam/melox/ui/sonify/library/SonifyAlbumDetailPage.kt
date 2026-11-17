/*
 * Sonify AlbumInfo page (GPL-3.0), adapted for MeloX.
 *
 * Upstream: ui/pages/library/albums/AlbumInfo.kt (master @ 209e1aa).
 * Preserved: compact centered cover header for phones, Apple-Music wide header
 * for >=600dp, play/shuffle, track-number rows, alphabet index for name sort,
 * title-bar menu, pull-to-refresh and the "N 首歌曲, 约 M 分钟" footer.
 *
 * MeloX replacements: MusicLibrary.Album -> caller loader over
 * NeteaseCollectionDetailsClient.albumDetail (NetEase) or AlbumCapability
 * (other providers); MediaController -> PlaybackCommands supplied by the host.
 */
package com.lladlam.melox.ui.sonify.library

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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.ui.sonify.LocalTitlePageBackdrop
import com.lladlam.melox.ui.sonify.MochiIndexBar
import com.lladlam.melox.ui.sonify.SonifyPrimary
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

private enum class SonifyAlbumSort {
    TrackNumber,
    Name,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SonifyAlbumDetailPage(
    target: SonifyAlbumTarget,
    onBack: () -> Unit,
    onPlay: (List<SearchSong>, Int) -> Unit,
    onMore: (SearchSong, List<SearchSong>) -> Unit,
    loadAlbum: suspend () -> Result<SonifyAlbumData>,
    setSubscribed: (suspend (Boolean) -> Result<Unit>)? = null,
) {
    val isDark = isSonifyDark()
    val pageColor = Color.White withNight Color.Black
    var data by remember(target) { mutableStateOf<SonifyAlbumData?>(null) }
    var loading by remember(target) { mutableStateOf(true) }
    var refreshing by remember(target) { mutableStateOf(false) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    var sortOrdinal by rememberSaveable(target.title) { mutableIntStateOf(SonifyAlbumSort.TrackNumber.ordinal) }
    var descending by rememberSaveable(target.title) { mutableStateOf(false) }
    var subscribeBusy by remember(target) { mutableStateOf(false) }
    var subscribeError by remember(target) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun runLoad(pull: Boolean) {
        if (pull) refreshing = true else if (data == null) loading = true
        loadAlbum()
            .onSuccess {
                data = it
                error = null
            }
            .onFailure { error = it.message ?: "专辑加载失败" }
        loading = false
        refreshing = false
    }

    LaunchedEffect(target) { runLoad(pull = false) }

    val songs = data?.songs.orEmpty()
    val nameIndex = remember(songs, sortOrdinal, descending) {
        if (sortOrdinal == SonifyAlbumSort.Name.ordinal && songs.isNotEmpty()) {
            sonifyAlphabetIndex(
                items = songs,
                descending = descending,
                isSearching = false,
                keySelector = { it.name },
            )
        } else {
            com.lladlam.melox.ui.sonify.IndexedListResult(emptyList(), emptyList(), emptyList())
        }
    }
    val sortedSongs = remember(songs, sortOrdinal, descending, nameIndex) {
        if (sortOrdinal == SonifyAlbumSort.Name.ordinal) {
            nameIndex.sortedItems
        } else if (descending) {
            songs.asReversed()
        } else {
            songs
        }
    }
    val mainArtists = remember(data, songs) {
        songs.firstOrNull()?.artists
            ?.split(" / ")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()
    }
    val subscribeAction: (@Composable () -> Unit)? =
        if (setSubscribed != null && data?.subscribed != null) {
            {
                val subscribed = data?.subscribed == true
                SonifyDropdownMenuAction(
                    text = if (subscribed) "取消收藏专辑" else "收藏专辑",
                ) {
                    if (!subscribeBusy) {
                        subscribeBusy = true
                        scope.launch {
                            setSubscribed(!subscribed)
                                .onSuccess {
                                    data = data?.copy(subscribed = !subscribed)
                                    subscribeError = null
                                }
                                .onFailure { subscribeError = it.message ?: "操作失败" }
                            subscribeBusy = false
                        }
                    }
                }
            }
        } else {
            null
        }
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    val state = rememberLazyListState()
    val titleBackdrop = rememberTitleBackdrop()
    val showSmallTitle = rememberShowSmallTitle(rememberAlpha(state), state)
    val topBarHeightPx = rememberIndexScrollOffsetPx()

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
                    item("AlbumHeader") {
                        val album = data
                        if (album == null) {
                            if (loading) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                                    SonifyAlbumHeaderSkeleton()
                                }
                            }
                        } else if (isWide) {
                            SonifyDetailPageHeader(
                                title = album.title,
                                coverUrl = album.artworkUrl,
                                tag = "ALBUM",
                                onPlayAll = {
                                    if (album.songs.isNotEmpty()) onPlay(album.songs, 0)
                                },
                                onShuffle = {
                                    if (album.songs.isNotEmpty()) onPlay(album.songs.shuffled(), 0)
                                },
                                extraLines = {
                                    Text(
                                        text = album.artistText,
                                        fontSize = 17.5.sp,
                                        lineHeight = 23.5.sp,
                                        color = SonifyPrimary,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                },
                                countText = "${album.songs.size} 首",
                                intro = album.description,
                            )
                        } else {
                            SonifyCompactDetailHeader(
                                title = album.title,
                                coverUrl = album.artworkUrl,
                                meta = "${album.artistText} · ${album.songs.size} 首",
                                tag = "ALBUM",
                                playEnabled = album.songs.isNotEmpty(),
                                onPlayAll = { onPlay(album.songs, 0) },
                                onShuffle = { onPlay(album.songs.shuffled(), 0) },
                                intro = album.description,
                            )
                        }
                    }

                    if (error != null && data != null) {
                        item("RefreshError") {
                            Text(
                                error.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                            )
                        }
                    }

                    item("AlbumDividerTop") { SonifyAlbumDivider() }

                    if (data == null && !loading) {
                        item("Status") {
                            SonifyStatusFooter(
                                text = error ?: "专辑为空",
                                showSpinner = false,
                                isError = error != null,
                                retry = error != null,
                                onRetry = { scope.launch { runLoad(pull = false) } },
                            )
                        }
                    } else {
                        itemsIndexed(
                            sortedSongs,
                            key = { index, song -> "album-${index}-${song.id}" },
                            contentType = { _, _ -> "AlbumInfo_item" },
                        ) { index, song ->
                            if (isWide) {
                                SonifyDetailSongRowWide(
                                    music = song,
                                    onMore = { onMore(song, sortedSongs) },
                                    itemClick = { onPlay(sortedSongs, index) },
                                )
                            } else {
                                SonifyAlbumTrackRow(
                                    music = song,
                                    trackNumber = index + 1,
                                    mainArtists = mainArtists,
                                    onMore = { onMore(song, sortedSongs) },
                                    itemClick = { onPlay(sortedSongs, index) },
                                )
                            }
                            if (index < sortedSongs.lastIndex) {
                                Spacer(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = if (isWide) 88.dp else 50.dp, end = 16.dp)
                                        .alpha(if (isWide) 0.15f else 0.25f)
                                        .height(0.5.dp)
                                        .background(Color.Black withNight Color.White),
                                )
                            }
                        }
                    }

                    item("AlbumDividerBottom") { SonifyAlbumDivider() }

                    if (sortedSongs.isNotEmpty()) {
                        item("AlbumOthers") {
                            val minutes = sortedSongs.sumOf { it.durationMs } / 60_000
                            Text(
                                text = "${sortedSongs.size} 首歌曲, 约 $minutes 分钟",
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .alpha(0.4f)
                                    .padding(horizontal = 18.dp)
                                    .padding(top = 18.dp),
                            )
                        }
                    }

                    if (subscribeError != null) {
                        item("SubscribeError") {
                            Text(
                                subscribeError.orEmpty(),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                            )
                        }
                    }

                    item("navbar") { Spacer(Modifier.sonifyNavigationBarsHeight(134.dp)) }
                }
            }

            TitleBar(
                title = data?.title ?: target.title,
                onBack = onBack,
                showSmallTitle = showSmallTitle,
                backdrop = titleBackdrop,
                rightBarIcon = {
                    SonifyDetailSortMenuButton(
                        sortOptions = listOf(
                            SonifyAlbumSort.TrackNumber.ordinal to "按曲目序号",
                            SonifyAlbumSort.Name.ordinal to "按名称排序",
                        ),
                        selectedSort = sortOrdinal,
                        onSelectSort = { sortOrdinal = it },
                        descending = descending,
                        onSelectDescending = { descending = it },
                        leadingContent = subscribeAction,
                    )
                },
            )

            if (nameIndex.labels.isNotEmpty()) {
                MochiIndexBar(
                    labels = nameIndex.labels,
                    onSelect = { index ->
                        val targetIndex = nameIndex.firstItemIndices.getOrNull(index) ?: return@MochiIndexBar
                        scope.launch {
                            state.scrollToIndexedItem(2 + targetIndex, topBarHeightPx)
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

@Composable
private fun SonifyAlbumHeaderSkeleton() {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 50.dp).height(220.dp)) {
            SonifyArtistSkeletonBlock(Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(12.dp))
        SonifyArtistSkeletonBlock(Modifier.fillMaxWidth(0.5f).height(18.dp))
    }
}
