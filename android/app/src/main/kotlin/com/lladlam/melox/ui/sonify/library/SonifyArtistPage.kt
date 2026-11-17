/*
 * Sonify artist detail host (GPL-3.0), adapted for MeloX.
 *
 * The page body is the already-migrated ui/sonify/SonifyArtistDetailPage.kt
 * (upstream artists/ArtistDetail.kt + ArtistDetailParts.kt). This wrapper owns
 * MeloX data loading and follow state so the page never falls back to the
 * MeloX artist detail scaffold.
 */
package com.lladlam.melox.ui.sonify.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXAlbumSummary
import com.lladlam.melox.ui.sonify.SonifyArtistDetailPage
import kotlinx.coroutines.launch

@Composable
internal fun SonifyArtistPage(
    target: SonifyArtistTarget,
    onBack: () -> Unit,
    onPlay: (List<SearchSong>, Int) -> Unit,
    onMoreSong: (SearchSong, List<SearchSong>) -> Unit,
    onOpenAlbum: (MeloXAlbumSummary) -> Unit,
    loadArtist: suspend () -> Result<SonifyArtistData>,
    setFollowed: (suspend (Boolean) -> Result<Unit>)? = null,
) {
    var data by remember(target) { mutableStateOf<SonifyArtistData?>(null) }
    var loading by remember(target) { mutableStateOf(true) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    var followed by remember(target) { mutableStateOf<Boolean?>(null) }
    var followBusy by remember(target) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        if (data == null) loading = true
        loadArtist()
            .onSuccess {
                data = it
                followed = it.artist.followed
                error = null
            }
            .onFailure { error = it.message ?: "歌手加载失败" }
        loading = false
    }

    LaunchedEffect(target) { load() }

    val artist = data?.artist
    SonifyArtistDetailPage(
        artist = artist,
        followed = followed,
        allSongs = data?.allSongs.orEmpty(),
        loading = loading,
        error = error,
        onBack = onBack,
        onPlay = { queue, index -> onPlay(queue, index) },
        onMoreSong = { song ->
            onMoreSong(song, data?.allSongs.orEmpty())
        },
        onOpenAlbum = onOpenAlbum,
        onToggleFollow = {
            if (followBusy || setFollowed == null || data?.canFollow != true) return@SonifyArtistDetailPage
            val next = followed != true
            followBusy = true
            scope.launch {
                setFollowed(next)
                    .onSuccess {
                        followed = next
                        data = data?.copy(artist = data!!.artist.copy(followed = next))
                    }
                    .onFailure { error = it.message ?: "关注操作失败" }
                followBusy = false
            }
        },
        onRetry = { scope.launch { load() } },
        canFollow = data?.canFollow == true && setFollowed != null,
    )
}
