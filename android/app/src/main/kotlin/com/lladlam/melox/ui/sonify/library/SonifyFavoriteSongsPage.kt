/*
 * Sonify FavoriteSongsPage (GPL-3.0), adapted for MeloX.
 *
 * Upstream: ui/pages/library/playlists/FavoriteSongsPage.kt (master @ 209e1aa).
 * Upstream resolves the Kugou system "我喜欢" playlist and delegates to
 * OnlinePlaylistDetail. MeloX already owns that collection as
 * NeteaseLibrarySnapshot.likedSongs, so this page delegates to the Sonify
 * NormalMusic list instead of inventing a second favorite store.
 */
package com.lladlam.melox.ui.sonify.library

import androidx.compose.runtime.Composable
import com.lladlam.melox.core.model.SearchSong

@Composable
internal fun SonifyFavoriteSongsPage(
    songs: List<SearchSong>,
    onBack: () -> Unit,
    onPlay: (List<SearchSong>, Int) -> Unit,
    onMore: (SearchSong, List<SearchSong>) -> Unit,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    loading: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    SonifyNormalMusicPage(
        title = "我喜欢的音乐",
        songs = songs,
        emptyText = "还没有喜欢的歌曲",
        loading = loading,
        error = error,
        onBack = onBack,
        onPlay = onPlay,
        onMore = onMore,
        onRefresh = onRefresh,
        refreshing = refreshing,
        onRetry = onRetry,
    )
}
