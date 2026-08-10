package com.lladlam.melox.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val upcoming = if (state.currentIndex >= 0 && state.queue.isNotEmpty()) {
        buildList {
            addAll(state.queue.drop(state.currentIndex + 1))
            if (state.repeatMode == Player.REPEAT_MODE_ALL && state.currentIndex > 0) {
                addAll(state.queue.take(state.currentIndex))
            }
        }
    } else {
        emptyList()
    }

    Column(
        modifier = modifier.padding(top = 8.dp),
    ) {
        if (showSongHeader) {
            currentEntry?.let { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Artwork(
                        url = entry.artworkUrl,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = entry.artist,
                            color = Color.White.copy(alpha = 0.64f),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        } else {
            // The shared/persistent Now Playing artwork and song header are drawn
            // above this resident queue page. Keep the reference-height slot so
            // queue controls and rows retain the same upstream layout geometry.
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            QueueModeButton(
                label = "↝",
                selected = state.shuffleEnabled,
                onClick = state::toggleShuffle,
                modifier = Modifier.weight(1f),
            )
            QueueModeButton(
                label = if (state.repeatMode == Player.REPEAT_MODE_ONE) "↻1" else "↻",
                selected = state.repeatMode != Player.REPEAT_MODE_OFF,
                onClick = state::cycleRepeatMode,
                modifier = Modifier.weight(1f),
            )
            QueueModeButton(
                label = "∞",
                selected = false,
                enabled = false,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
            QueueModeButton(
                label = "◎",
                selected = false,
                enabled = false,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = "继续播放",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
        )

        if (upcoming.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "没有待播放歌曲",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 15.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items = upcoming,
                    key = { entry -> "${entry.mediaId}-${entry.index}" },
                ) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { state.playQueueIndex(entry.index) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Artwork(
                            url = entry.artworkUrl,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = entry.artist,
                                color = Color.White.copy(alpha = 0.58f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
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
                surfaceColor = if (selected) {
                    Color.White.copy(alpha = 0.62f)
                } else {
                    Color.White.copy(alpha = 0.10f)
                },
                lensRadius = if (selected) 11.dp else 8.dp,
                refractionHeight = 16.dp,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> Color.White.copy(alpha = 0.25f)
                selected -> Color.Black.copy(alpha = 0.62f)
                else -> Color.White.copy(alpha = 0.86f)
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
