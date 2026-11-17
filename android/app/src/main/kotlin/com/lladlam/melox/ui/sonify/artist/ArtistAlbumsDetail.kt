/*
 * Sonify artist albums detail page (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/pages/library/artists/ArtistAlbumsDetail.kt
 * (master @ 209e1aa). Keeps the large-title adaptive grid, status footer and
 * album-card grid. ArtistAlbumsObject pagination becomes the MeloX artist
 * detail's already-loaded album list with loading/error/retry states.
 */
package com.lladlam.melox.ui.sonify.artist

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lladlam.melox.R
import com.lladlam.melox.core.network.MeloXAlbumSummary
import com.lladlam.melox.ui.sonify.TitleWithLazyVerticalGrid

@Composable
internal fun ArtistAlbumsDetail(
    albums: List<MeloXAlbumSummary>,
    artistName: String,
    onBack: () -> Unit,
    onOpenAlbum: (MeloXAlbumSummary) -> Unit,
    loading: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {}
) {
    val gridState = rememberLazyGridState()

    TitleWithLazyVerticalGrid(
        title = androidx.compose.ui.res.stringResource(R.string.sonify_artist_albums),
        subTitle = artistName,
        onBack = onBack,
        liquidBackButton = true,
        gridState = gridState
    ) {
        if (albums.isEmpty()) {
            item("Status", span = { GridItemSpan(maxLineSpan) }) {
                when {
                    loading -> ArtistAlbumRailSkeleton()
                    error != null -> ArtistSongsError(
                        message = error,
                        onRetry = onRetry
                    )
                    else -> androidx.compose.material3.Text(
                        text = androidx.compose.ui.res.stringResource(R.string.sonify_artist_no_albums),
                        modifier = Modifier.fillMaxWidth().padding(20.dp)
                    )
                }
            }
        }

        itemsIndexed(
            albums,
            key = { index, album -> "artist-album-page-$index-${album.id}" },
            contentType = { _, _ -> "artist_album_card" }
        ) { _, album ->
            ArtistAlbumGridCard(
                album = album,
                modifier = Modifier.fillMaxWidth()
            ) {
                onOpenAlbum(album)
            }
        }

        if (albums.isNotEmpty() && loading) {
            item("FooterLoading", span = { GridItemSpan(maxLineSpan) }) {
                ArtistListFooterLoading()
            }
        } else if (albums.isNotEmpty() && error != null) {
            item("FooterError", span = { GridItemSpan(maxLineSpan) }) {
                ArtistSongsError(
                    message = error,
                    onRetry = onRetry,
                    compact = true
                )
            }
        }
    }
}
