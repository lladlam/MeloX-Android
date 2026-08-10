package com.lladlam.melox.ui.player

import androidx.compose.runtime.Composable
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXSearchKind

@Composable
fun MeloXNowPlayingActionsSheet(
    state: MeloXPlaybackUiState,
    visible: Boolean,
    onDismiss: () -> Unit,
    onNavigateSearch: ((String, MeloXSearchKind) -> Unit)? = null,
) {
    val id = state.mediaId?.toLongOrNull() ?: -1L
    val song = SearchSong(
        id = id,
        name = state.title.ifBlank { "正在播放" },
        artists = state.artist,
        album = state.album,
        artworkUrl = state.artworkUrl,
        durationMs = state.durationMs,
    )
    MeloXSongActionsOverlay(
        song = song,
        queue = emptyList(),
        visible = visible && id > 0L,
        onDismiss = onDismiss,
        playbackState = state,
        onNavigateSearch = onNavigateSearch,
    )
}
