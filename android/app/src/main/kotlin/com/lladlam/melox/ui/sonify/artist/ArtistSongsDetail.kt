/*
 * Sonify artist all-songs detail page (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/pages/library/artists/ArtistSongsDetail.kt
 * (master @ 209e1aa). The large-title scaffold, row heights, dividers,
 * skeleton/empty/error states and playback-at-index behaviour are kept.
 * ArtistSongsObject/KugouRepository/MediaController are replaced by the
 * allSongs list and MeloX playback callbacks supplied by the artist page.
 */
package com.lladlam.melox.ui.sonify.artist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.R
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.ui.sonify.SonifyLibraryDivider
import com.lladlam.melox.ui.sonify.SonifySongRow
import com.lladlam.melox.ui.sonify.Title

@Composable
internal fun ArtistSongsDetail(
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
        title = androidx.compose.ui.res.stringResource(R.string.sonify_artist_all_songs),
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

@Composable
internal fun ArtistSongListDetail(
    title: String,
    songs: List<SearchSong>,
    artistName: String,
    onBack: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onMoreAt: ((Int) -> Unit)?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    val listState = rememberLazyListState()
    val songKeys = remember(songs) { songs.map { "${it.id}-${it.name}" } }

    Title(
        title = title,
        subTitle = artistName,
        onBack = onBack,
        liquidBackButton = true,
        listState = listState
    ) {
        when {
            songs.isEmpty() && loading -> item("Skeleton") {
                ArtistSongsSkeleton()
            }
            songs.isEmpty() && error != null -> item("Error") {
                ArtistSongsError(
                    message = error,
                    onRetry = onRetry
                )
            }
            songs.isEmpty() -> item("Empty") {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.sonify_artist_empty),
                    fontSize = 18.sp,
                    modifier = Modifier
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                        .alpha(0.6f)
                )
            }
            else -> {
                itemsIndexed(
                    items = songs,
                    key = { index, _ -> songKeys.getOrElse(index) { "oob_$index" } },
                    contentType = { _, _ -> "artist_song" }
                ) { index, song ->
                    Box(Modifier) {
                        SonifySongRow(
                            song = song,
                            horizontalPadding = 20.dp,
                            onClick = { onPlayAt(index) },
                            onMore = onMoreAt?.let { more -> { more(index) } }
                        )
                    }
                    if (index < songs.lastIndex) {
                        SonifyLibraryDivider()
                    }
                }

                when {
                    loading -> item("FooterLoading") { ArtistListFooterLoading() }
                    error != null -> item("FooterError") {
                        ArtistSongsError(
                            message = error,
                            onRetry = onRetry,
                            compact = true
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}
