package com.lladlam.melox.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.network.MeloXSearchKind
import com.lladlam.melox.playback.MeloXListenTogetherCoordinator
import com.lladlam.melox.playback.PlaybackTrackIdentity

@Composable
fun MeloXNowPlayingActionsSheet(
    state: MeloXPlaybackUiState,
    visible: Boolean,
    onDismiss: () -> Unit,
    onNavigateSearch: ((String, MeloXSearchKind) -> Unit)? = null,
    onLocalMetadataChanged: () -> Unit = {},
) {
    val context = LocalContext.current.applicationContext
    val identity = state.mediaId?.let(PlaybackTrackIdentity::decode)
    val neteaseId = identity
        ?.takeIf { it.source == MusicSource.Netease }
        ?.value
        ?.toLongOrNull()
        ?.takeIf { it > 0L }

    // Listen Together and the mature song-actions overlay are NetEase-only. Do
    // not start or expose them for QQ/Kugou just because they share Now Playing.
    LaunchedEffect(neteaseId) {
        if (neteaseId != null) MeloXListenTogetherCoordinator.ensureStarted(context)
    }
    val togetherState by MeloXListenTogetherCoordinator.state(context).collectAsState()
    var openedFromTogetherBadge by remember { mutableStateOf(false) }
    val effectiveVisible = visible || (neteaseId != null && openedFromTogetherBadge)

    Box(Modifier.fillMaxSize()) {
        val room = togetherState.room
        if (neteaseId != null && room != null && !effectiveVisible) {
            val status = when (togetherState.phase) {
                MeloXListenTogetherCoordinator.Phase.Reconnecting -> "一起听 · 重连中"
                else -> "一起听 · ${room.users.size.coerceAtLeast(1)} 人"
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 38.dp, end = 22.dp)
                    .background(Color.Black.copy(alpha = .28f), RoundedCornerShape(999.dp))
                    .clickable { openedFromTogetherBadge = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                androidx.compose.material3.Text(
                    text = status,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (neteaseId != null) {
            val song = SearchSong(
                id = neteaseId,
                name = state.title.ifBlank { "正在播放" },
                artists = state.artist,
                album = state.album,
                artworkUrl = state.artworkUrl,
                durationMs = state.durationMs,
            )
            val queue = state.queue.mapNotNull { entry ->
                val entryIdentity = PlaybackTrackIdentity.decode(entry.mediaId) ?: return@mapNotNull null
                if (entryIdentity.source != MusicSource.Netease) return@mapNotNull null
                val entryId = entryIdentity.value.toLongOrNull()?.takeIf { it > 0L } ?: return@mapNotNull null
                SearchSong(
                    id = entryId,
                    name = entry.title.ifBlank { "未知歌曲" },
                    artists = entry.artist.ifBlank { "未知歌手" },
                    album = if (entryId == neteaseId) state.album else "",
                    artworkUrl = entry.artworkUrl,
                    durationMs = if (entryId == neteaseId) state.durationMs else 0L,
                )
            }

            MeloXSongActionsOverlay(
                song = song,
                queue = queue,
                visible = effectiveVisible,
                onDismiss = {
                    if (openedFromTogetherBadge) openedFromTogetherBadge = false
                    if (visible) onDismiss()
                },
                playbackState = state,
                onNavigateSearch = onNavigateSearch,
            )
        } else if (identity != null) {
            MeloXProviderSongActionsOverlay(
                state = state,
                identity = identity,
                visible = visible,
                onDismiss = onDismiss,
                onNavigateSearch = onNavigateSearch,
                onLocalMetadataChanged = onLocalMetadataChanged,
            )
        }
    }
}
