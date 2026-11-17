/*
 * Global artist detail entry point.
 *
 * The migrated Sonify ArtistDetail page is now the artist page for every entry
 * (search, library, collection activity, Sonify library). The old MeloX
 * artist appearance is intentionally removed.
 */
package com.lladlam.melox.ui.collection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXArtistDetail
import com.lladlam.melox.core.network.NeteaseCollectionDetailsClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.library.MeloXUnifiedAlbumDetailScreen
import com.lladlam.melox.ui.player.MeloXSongActionsOverlay
import com.lladlam.melox.ui.sonify.artist.ArtistDetail
import com.lladlam.melox.ui.state.rememberRetainedPageState
import kotlinx.coroutines.launch

@Composable
internal fun MeloXArtistDetailScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val client = remember(app) {
        NeteaseCollectionDetailsClient(cookieProvider = { NeteaseSessionStore.readCookie(app) })
    }
    val scope = rememberCoroutineScope()
    var detail by rememberRetainedPageState(id) { mutableStateOf<MeloXArtistDetail?>(null) }
    var loading by remember(id) { mutableStateOf(detail == null) }
    var refreshing by remember(id) { mutableStateOf(false) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var followed by rememberSaveable(id) { mutableStateOf<Boolean?>(null) }
    var followBusy by remember(id) { mutableStateOf(false) }
    var selectedSong by remember(id) { mutableStateOf<SearchSong?>(null) }
    var selectedQueue by remember(id) { mutableStateOf<List<SearchSong>>(emptyList()) }
    var allSongs by rememberRetainedPageState(id) { mutableStateOf<List<SearchSong>>(emptyList()) }
    var selectedAlbumId by rememberSaveable(id) { mutableStateOf<Long?>(null) }

    suspend fun loadArtistSongs(): List<SearchSong> {
        val collected = mutableListOf<SearchSong>()
        var offset = 0
        while (offset < 500) {
            val page = runCatching { client.artistSongs(id, 100, offset) }.getOrDefault(emptyList())
            if (page.isEmpty()) break
            collected += page
            if (page.size < 100) break
            offset += page.size
        }
        return collected
    }

    suspend fun reloadArtist(showRefresh: Boolean) {
        if (showRefresh) refreshing = true else if (detail == null) loading = true
        error = null
        runCatching { client.artistDetail(id) }
            .onSuccess {
                detail = it
                followed = it.followed
            }
            .onFailure { error = it.message ?: "歌手加载失败" }
        if (allSongs.isEmpty() || showRefresh) {
            allSongs = loadArtistSongs()
        }
        loading = false
        refreshing = false
    }

    LaunchedEffect(id) {
        if (detail == null) {
            reloadArtist(showRefresh = false)
        } else if (allSongs.isEmpty()) {
            allSongs = loadArtistSongs()
        }
    }

    BackHandler(enabled = selectedAlbumId != null) {
        selectedAlbumId = null
    }

    Box(Modifier.fillMaxSize()) {
        ArtistDetail(
            artist = detail,
            followed = followed,
            allSongs = allSongs,
            loading = loading,
            error = error,
            canFollow = true,
            onBack = onBack,
            onPlay = { queue, index ->
                val song = queue.getOrNull(index) ?: return@ArtistDetail
                PlaybackCommands.playQueue(context, queue, song.id)
            },
            onMoreSong = { song, queue ->
                selectedSong = song
                selectedQueue = queue
            },
            onOpenAlbum = { selectedAlbumId = it.id },
            onToggleFollow = {
                if (followBusy) return@ArtistDetail
                val target = followed != true
                followBusy = true
                scope.launch {
                    runCatching { client.setArtistFollowed(id, target) }
                        .onSuccess { followed = target }
                        .onFailure { error = it.message ?: "关注操作失败" }
                    followBusy = false
                }
            },
            onRetry = { scope.launch { reloadArtist(showRefresh = false) } },
            onRefresh = { scope.launch { reloadArtist(showRefresh = true) } },
            refreshing = refreshing,
        )

        selectedAlbumId?.let { albumId ->
            Box(Modifier.fillMaxSize().zIndex(20f)) {
                MeloXUnifiedAlbumDetailScreen(
                    albumId = albumId,
                    onBack = { selectedAlbumId = null },
                )
            }
        }

        selectedSong?.let { song ->
            MeloXSongActionsOverlay(
                song = song,
                queue = selectedQueue.ifEmpty { allSongs.ifEmpty { detail?.hotSongs.orEmpty() } },
                visible = true,
                onDismiss = { selectedSong = null },
            )
        }
    }
}
