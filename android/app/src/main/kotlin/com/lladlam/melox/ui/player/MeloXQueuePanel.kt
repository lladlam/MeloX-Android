package com.lladlam.melox.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.lladlam.melox.ui.glass.meloXLiquidButton

@Composable
fun MeloXQueuePanel(
    state: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
    showSongHeader: Boolean = true,
) {
    val currentEntry = state.queue.getOrNull(state.currentIndex)
    val history = if (state.currentIndex > 0) state.queue.take(state.currentIndex) else emptyList()
    val upcoming = if (state.currentIndex >= 0 && state.queue.isNotEmpty()) {
        state.queue.drop(state.currentIndex + 1)
    } else emptyList()
    val manualQueue = upcoming.filter { it.origin == MeloXQueueOrigin.Manual }
    val continuing = upcoming.filter { it.origin != MeloXQueueOrigin.Manual }
    val listState = rememberLazyListState()

    // History lives physically above the current-song/header anchor. Opening the
    // page starts at the anchor, so an upward browse naturally reveals previously
    // played items without mixing them into "继续播放".
    LaunchedEffect(state.mediaId, history.size, showSongHeader) {
        val anchor = history.size + if (history.isNotEmpty()) 1 else 0
        runCatching { listState.scrollToItem(anchor.coerceAtLeast(0)) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (history.isNotEmpty()) {
            item(key = "history-title") { QueueSectionTitle("历史记录", subdued = true) }
            items(history, key = { "history-${it.index}-${it.mediaId}" }) { entry ->
                QueueRow(entry, state)
            }
        }

        item(key = "current-header") {
            if (showSongHeader) {
                currentEntry?.let { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Artwork(entry.artworkUrl, Modifier.size(68.dp).clip(RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.artist, color = Color.White.copy(alpha = .64f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            } else {
                Spacer(Modifier.fillMaxWidth().height(72.dp))
            }
        }

        item(key = "mode-controls") {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                QueueModeButton("↝", state.shuffleEnabled, onClick = state::toggleShuffle, modifier = Modifier.weight(1f))
                QueueModeButton(if (state.repeatMode == Player.REPEAT_MODE_ONE) "↻1" else "↻", state.repeatMode != Player.REPEAT_MODE_OFF, onClick = state::cycleRepeatMode, modifier = Modifier.weight(1f))
                QueueModeButton("∞", state.autoplayEnabled, onClick = state::toggleAutoplay, modifier = Modifier.weight(1f))
                QueueModeButton("◎", state.autoMixEnabled, onClick = state::toggleAutoMix, modifier = Modifier.weight(1f))
            }
        }

        if (manualQueue.isNotEmpty()) {
            item(key = "manual-title") { QueueSectionTitle("队列") }
            items(manualQueue, key = { "manual-${it.index}-${it.mediaId}" }) { entry -> QueueRow(entry, state) }
        }

        item(key = "continue-title") { QueueSectionTitle("继续播放") }
        if (continuing.isEmpty()) {
            item(key = "continue-empty") {
                Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                    Text("没有待播放歌曲", color = Color.White.copy(alpha = .55f), fontSize = 15.sp)
                }
            }
        } else {
            items(continuing, key = { "continue-${it.index}-${it.mediaId}" }) { entry -> QueueRow(entry, state) }
        }
    }
}

@Composable
private fun QueueSectionTitle(title: String, subdued: Boolean = false) {
    Text(
        title,
        color = Color.White.copy(alpha = if (subdued) .62f else 1f),
        fontSize = if (subdued) 18.sp else 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
    )
}

@Composable
private fun QueueRow(entry: MeloXQueueEntry, state: MeloXPlaybackUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { state.playQueueIndex(entry.index) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(entry.artworkUrl, Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.artist, color = Color.White.copy(alpha = .58f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun QueueModeButton(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .meloXLiquidButton(
                shape = RoundedCornerShape(22.dp),
                enabled = enabled,
                surfaceColor = if (selected) Color.White.copy(alpha = .62f) else Color.White.copy(alpha = .10f),
                lensRadius = if (selected) 11.dp else 8.dp,
                refractionHeight = 16.dp,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = when {
                !enabled -> Color.White.copy(alpha = .25f)
                selected -> Color.Black.copy(alpha = .62f)
                else -> Color.White.copy(alpha = .86f)
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
