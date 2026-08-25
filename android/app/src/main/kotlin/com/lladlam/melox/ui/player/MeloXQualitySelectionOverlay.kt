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
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.playback.PlaybackTrackIdentity
import com.lladlam.melox.playback.CrossProviderPlaybackRuntime
import com.lladlam.melox.playback.ProviderPlaybackQualityRuntime
import com.lladlam.melox.core.music.provider.PlaybackAccountStore
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXActionIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Quality chooser. A downloaded NetEase track is physically one encoded file,
 * so its local quality is locked to the quality recorded at download time. */
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
            cookieProvider = { PlaybackAccountStore.neteaseCookie(context) },
        )
    }
    val identity = remember(state.mediaId) { state.mediaId?.let(PlaybackTrackIdentity::decode) }
    val source = identity?.source ?: MusicSource.Netease
    val foreground = MaterialTheme.colorScheme.onSurface
    val secondaryForeground = foreground.copy(alpha = 0.62f)
    val songId = identity
        ?.takeIf { it.source == MusicSource.Netease }
        ?.value
        ?.toLongOrNull()
    val downloadedQuality = songId?.let(downloads::downloadedQuality)
    var selected by remember(context, visible, state.mediaId) {
        mutableStateOf(downloadedQuality ?: MusicQualityPreferences.read(context))
    }
    var availability by remember(state.mediaId, visible) {
        mutableStateOf(SongAudioAvailability.Unknown)
    }
    var providerActual by remember(state.mediaId, visible) {
        mutableStateOf(ProviderPlaybackQualityRuntime.actualFor(identity))
    }
    var fallbackSource by remember(state.mediaId, visible) {
        mutableStateOf(CrossProviderPlaybackRuntime.sourceFor(songId))
    }
    var loading by remember(state.mediaId, visible) { mutableStateOf(false) }

    LaunchedEffect(visible, source, songId, downloadedQuality) {
        if (!visible) return@LaunchedEffect
        selected = downloadedQuality ?: MusicQualityPreferences.read(context)
        if (source != MusicSource.Netease) {
            loading = false
            availability = SongAudioAvailability.Unknown
            while (visible) {
                providerActual = ProviderPlaybackQualityRuntime.actualFor(identity)
                delay(180L)
            }
            return@LaunchedEffect
        }
        fallbackSource = CrossProviderPlaybackRuntime.sourceFor(songId)
        if (songId == null) return@LaunchedEffect
        if (downloadedQuality != null) {
            selected = downloadedQuality
            loading = false
            availability = SongAudioAvailability.Unknown
            return@LaunchedEffect
        }
        loading = true
        availability = runCatching { client.audioAvailability(songId) }
            .getOrDefault(SongAudioAvailability.Unknown)
        loading = false
    }
    LaunchedEffect(visible, songId) {
        if (!visible || songId == null) return@LaunchedEffect
        while (visible) {
            fallbackSource = CrossProviderPlaybackRuntime.sourceFor(songId)
            delay(180L)
        }
    }

    BackHandler(enabled = visible, onBack = onDismiss)

    MeloXGlassDialog(
        visible = visible,
        onDismiss = onDismiss,
    ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "音质",
                            color = foreground,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            when {
                                downloadedQuality != null -> "已下载 · ${downloadedQuality.title}"
                                fallbackSource != null -> "实际音源：${fallbackSource!!.displayName}"
                                source != MusicSource.Netease && providerActual != null ->
                                    "${source.displayName} · 实际播放：${providerActual!!.displayTitle()}"
                                source != MusicSource.Netease -> "${source.displayName} · ${state.title.ifBlank { "正在播放" }}"
                                else -> state.title.ifBlank { "正在播放" }
                            },
                            color = secondaryForeground,
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = foreground.copy(alpha = 0.78f),
                            strokeWidth = 2.dp,
                        )
                    }
                }

                MusicQuality.entries.forEach { quality ->
                    val supported = when {
                        downloadedQuality != null -> quality == downloadedQuality
                        source == MusicSource.Netease -> availability.supports(quality.apiLevel) != false
                        source == MusicSource.QQMusic -> quality in QQSelectableQualities
                        source == MusicSource.Kugou -> quality in KugouSelectableQualities
                        source == MusicSource.Bilibili -> quality in BilibiliSelectableQualities
                        source == MusicSource.Spotify -> true
                        else -> false
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
                                    foreground.copy(alpha = 0.10f)
                                } else {
                                    Color.Transparent
                                },
                                blurRadius = 10.dp,
                                lensRadius = 10.dp,
                                refractionHeight = 14.dp,
                            )
                            .clickable(enabled = supported) {
                                if (downloadedQuality == null) {
                                    selected = quality
                                    providerActual = null
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
                            color = foreground.copy(alpha = if (supported) 0.94f else 0.30f),
                            fontSize = 17.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                        if (isSelected) {
                            MeloXActionIcon("✓", Modifier.size(18.dp), foreground)
                        } else if (!supported) {
                            Text(
                                when {
                                    downloadedQuality != null -> "未下载"
                                    source == MusicSource.Netease -> "不可用"
                                    else -> "平台不提供"
                                },
                                color = foreground.copy(alpha = 0.30f),
                                fontSize = 12.sp,
                            )
    }
}
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

private val QQSelectableQualities = setOf(
    MusicQuality.Standard,
    MusicQuality.High,
    MusicQuality.Lossless,
    MusicQuality.HighDefinitionSurround,
    MusicQuality.ImmersiveSurround,
    MusicQuality.UltraClearMaster,
)

private val KugouSelectableQualities = setOf(
    MusicQuality.Standard,
    MusicQuality.High,
    MusicQuality.Lossless,
    MusicQuality.HiResolution,
)

private val BilibiliSelectableQualities = setOf(
    MusicQuality.Standard,
    MusicQuality.High,
    MusicQuality.Lossless,
    MusicQuality.HiResolution,
    MusicQuality.ImmersiveSurround,
)

private fun AudioQualityTier.displayTitle(): String = when (this) {
    AudioQualityTier.Standard -> "标准"
    AudioQualityTier.High -> "高品质"
    AudioQualityTier.Lossless -> "无损"
    AudioQualityTier.HiResolution -> "Hi-Res"
    AudioQualityTier.Immersive -> "臻品/环绕"
    AudioQualityTier.Master -> "臻品母带"
}
