package com.lladlam.melox.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.audio.NeteaseQualityClient
import com.lladlam.melox.core.audio.SongAudioAvailability
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.network.MeloXNetworkAvailability
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.glass.meloXLiquidButton
import kotlinx.coroutines.launch

/**
 * Quality chooser. A local download only constrains quality while NetEase Music
 * itself is unreachable. As soon as music.163.com is reachable, online qualities
 * are queried and unlocked normally even if the current song is also downloaded.
 */
@Composable
internal fun MeloXQualitySelectionOverlay(
    state: MeloXPlaybackUiState,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val downloads = remember(context) { MeloXDownloadStore.get(context) }
    val client = remember(context) {
        NeteaseQualityClient(
            cookieProvider = { NeteaseSessionStore.readCookie(context) },
        )
    }
    val songId = state.mediaId?.toLongOrNull()
    val downloadedQuality = songId?.let(downloads::downloadedQuality)
    var neteaseReachable by remember(visible, songId) { mutableStateOf<Boolean?>(null) }
    var selected by remember(context, visible, songId) {
        mutableStateOf(MusicQualityPreferences.read(context))
    }
    var availability by remember(state.mediaId, visible) {
        mutableStateOf(SongAudioAvailability.Unknown)
    }
    var loading by remember(state.mediaId, visible) { mutableStateOf(false) }

    LaunchedEffect(visible, songId, downloadedQuality) {
        if (!visible || songId == null) return@LaunchedEffect
        loading = true
        neteaseReachable = null
        val reachable = MeloXNetworkAvailability.canReachNetease(context)
        neteaseReachable = reachable

        if (reachable) {
            selected = MusicQualityPreferences.read(context)
            availability = runCatching { client.audioAvailability(songId) }
                .getOrDefault(SongAudioAvailability.Unknown)
        } else {
            selected = downloadedQuality ?: MusicQualityPreferences.read(context)
            availability = SongAudioAvailability.Unknown
        }
        loading = false
    }

    BackHandler(enabled = visible, onBack = onDismiss)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = 520f)) + scaleIn(initialScale = 0.96f),
        exit = fadeOut(spring(stiffness = 620f)) + scaleOut(targetScale = 0.97f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 34.dp)
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .meloXLiquidButton(
                        shape = RoundedCornerShape(30.dp),
                        tint = Color.White.copy(alpha = 0.08f),
                        surfaceColor = Color.Black.copy(alpha = 0.12f),
                        blurRadius = 16.dp,
                        lensRadius = 20.dp,
                        refractionHeight = 24.dp,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "音质",
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            when (neteaseReachable) {
                                null -> "正在检测网易云连接"
                                true -> state.title.ifBlank { "在线播放" }
                                false -> if (downloadedQuality != null) {
                                    "离线 · 已下载 ${downloadedQuality.title}"
                                } else {
                                    "离线 · 当前歌曲没有本地音频"
                                }
                            },
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White.copy(alpha = 0.78f),
                            strokeWidth = 2.dp,
                        )
                    }
                }

                MusicQuality.entries.forEach { quality ->
                    val supported = when (neteaseReachable) {
                        true -> availability.supports(quality.apiLevel) != false
                        false -> downloadedQuality == quality
                        null -> false
                    }
                    val isSelected = quality == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .meloXLiquidButton(
                                shape = RoundedCornerShape(16.dp),
                                enabled = supported,
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                } else {
                                    Color.Transparent
                                },
                                surfaceColor = if (isSelected) {
                                    Color.White.copy(alpha = 0.08f)
                                } else {
                                    Color.Transparent
                                },
                                blurRadius = 10.dp,
                                lensRadius = 10.dp,
                                refractionHeight = 14.dp,
                            )
                            .clickable(enabled = supported) {
                                selected = quality
                                if (neteaseReachable == true) {
                                    scope.launch { PlaybackCommands.changeQuality(context, quality) }
                                }
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = quality.title,
                            modifier = Modifier.weight(1f),
                            color = Color.White.copy(alpha = if (supported) 0.94f else 0.30f),
                            fontSize = 17.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                        if (isSelected && supported) {
                            Text("✓", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        } else if (!supported && neteaseReachable == false) {
                            Text(
                                if (downloadedQuality != null) "未下载" else "离线",
                                color = Color.White.copy(alpha = 0.30f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
