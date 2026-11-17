/*
 * Sonify artist hot-songs detail page (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/pages/library/artists/ArtistHotSongsDetail.kt
 * (master @ 209e1aa). Shares the same large-title list layout as
 * ArtistSongsDetail; MeloX loads the hot-sorted artist tracks in one pass, so
 * the page receives the already-hot-ordered list from the artist page.
 */
package com.lladlam.melox.ui.sonify.artist

import androidx.compose.runtime.Composable
import com.lladlam.melox.R
import com.lladlam.melox.core.model.SearchSong

@Composable
internal fun ArtistHotSongsDetail(
    songs: List<SearchSong>,
    artistName: String,
    onBack: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onMoreAt: ((Int) -> Unit)? = null,
    loading: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {}
) {
    ArtistSongListDetail(
        title = androidx.compose.ui.res.stringResource(R.string.sonify_artist_hot_songs),
        songs = songs,
        artistName = artistName,
        onBack = onBack,
        onPlayAt = onPlayAt,
        onMoreAt = onMoreAt,
        loading = loading,
        error = error,
        onRetry = onRetry
    )
}
