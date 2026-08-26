package com.lladlam.melox.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.audio.MusicQualityRuntime
import com.lladlam.melox.core.audio.NeteaseQualityClient
import com.lladlam.melox.core.audio.SongAudioResource
import com.lladlam.melox.core.audio.SongAudioAvailability
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.playback.PlaybackTrackIdentity
import com.lladlam.melox.playback.CrossProviderPlaybackRuntime
import com.lladlam.melox.playback.ProviderPlaybackQualityRuntime
import com.lladlam.melox.core.music.provider.PlaybackAccountStore
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import kotlinx.coroutines.delay

/** Reports the active audio quality; changing the preference belongs to Settings. */
@Composable
internal fun MeloXQualitySelectionOverlay(
    state: MeloXPlaybackUiState,
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenPlaybackSettings: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val downloads = remember(context) { MeloXDownloadStore.get(context) }
    val client = remember(context) {
        NeteaseQualityClient(
            cookieProvider = { PlaybackAccountStore.neteaseCookie(context) },
        )
    }
    val identity = remember(state.mediaId) { state.mediaId?.let(PlaybackTrackIdentity::decode) }
    val source = identity?.source ?: MusicSource.Netease
    val foreground = MaterialTheme.colorScheme.onSurface
    val songId = identity
        ?.takeIf { it.source == MusicSource.Netease }
        ?.value
        ?.toLongOrNull()
    val downloadedQuality = songId?.let(downloads::downloadedQuality)
    var availability by remember(state.mediaId, visible) {
        mutableStateOf(SongAudioAvailability.Unknown)
    }
    var providerActual by remember(state.mediaId, visible) {
        mutableStateOf(ProviderPlaybackQualityRuntime.actualFor(identity))
    }
    var fallbackSource by remember(state.mediaId, visible) {
        mutableStateOf(CrossProviderPlaybackRuntime.sourceFor(songId))
    }

    LaunchedEffect(visible, source, songId, downloadedQuality) {
        if (!visible) return@LaunchedEffect
        if (source != MusicSource.Netease) {
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
            availability = SongAudioAvailability.Unknown
            return@LaunchedEffect
        }
        availability = runCatching { client.audioAvailability(songId) }
            .getOrDefault(SongAudioAvailability.Unknown)
    }
    LaunchedEffect(visible, songId) {
        if (!visible || songId == null) return@LaunchedEffect
        while (visible) {
            fallbackSource = CrossProviderPlaybackRuntime.sourceFor(songId)
            delay(180L)
        }
    }

    BackHandler(enabled = visible, onBack = onDismiss)

    val selected = downloadedQuality ?: MusicQualityPreferences.read(context)
    val actualQuality = when {
        downloadedQuality != null -> downloadedQuality
        source == MusicSource.Netease -> MusicQualityRuntime.actualFor(songId) ?: selected
        providerActual != null -> providerActual!!.toMusicQuality() ?: selected
        else -> selected
    }
    val sourceLabel = when {
        downloadedQuality != null -> "已下载音频"
        fallbackSource != null -> "实际音源：${fallbackSource!!.displayName}"
        source != MusicSource.Netease -> "${source.displayName} 音源"
        else -> "当前播放"
    }
    val details = qualityDetails(
        resource = availability.resourceFor(actualQuality),
        quality = actualQuality,
        source = source,
        downloaded = downloadedQuality != null,
    )

    MeloXGlassDialog(
        visible = visible,
        onDismiss = onDismiss,
    ) {
        Text("音质", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "当前音质：${actualQuality.title}",
            modifier = Modifier.padding(top = 9.dp),
            color = foreground.copy(alpha = 0.68f),
            fontSize = 14.sp,
        )
        Text(
            text = details,
            modifier = Modifier.padding(top = 4.dp),
            color = foreground.copy(alpha = 0.68f),
            fontSize = 14.sp,
        )
        Text(
            text = sourceLabel,
            modifier = Modifier.padding(top = 4.dp),
            color = foreground.copy(alpha = 0.54f),
            fontSize = 12.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MeloXGlassButton(
                onClick = {
                    onDismiss()
                    onOpenPlaybackSettings()
                },
                modifier = Modifier.weight(1f),
                style = MeloXGlassButtonStyle.Plain,
            ) { Text("设置") }
            MeloXGlassButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                style = MeloXGlassButtonStyle.BorderedProminent,
            ) { Text("确认") }
        }
    }
}

private fun SongAudioAvailability.resourceFor(quality: MusicQuality): SongAudioResource? = when (quality) {
    MusicQuality.Standard -> standard
    MusicQuality.High -> high ?: medium
    MusicQuality.Lossless -> lossless
    MusicQuality.HiResolution -> hiResolution
    MusicQuality.HighDefinitionSurround -> highDefinitionSurround
    MusicQuality.ImmersiveSurround -> immersiveSurround
    MusicQuality.UltraClearMaster -> ultraClearMaster
}

private fun qualityDetails(
    resource: SongAudioResource?,
    quality: MusicQuality,
    source: MusicSource,
    downloaded: Boolean,
): String {
    if (downloaded) return "本地文件 · 参数以下载音频为准"
    val details = buildList {
        resource?.bitrate?.let { add("${it / 1000} kbps") }
        resource?.sampleRate?.let { add("${it / 1_000.0} kHz") }
        when (quality) {
            MusicQuality.HighDefinitionSurround,
            MusicQuality.ImmersiveSurround,
            -> add("5.1 声道")
            else -> if (source == MusicSource.Netease) add("2 声道")
        }
    }
    return details.joinToString(" · ").ifBlank { "${source.displayName} 未提供音频参数" }
}

private fun AudioQualityTier.toMusicQuality(): MusicQuality? = when (this) {
    AudioQualityTier.Standard -> MusicQuality.Standard
    AudioQualityTier.High -> MusicQuality.High
    AudioQualityTier.Lossless -> MusicQuality.Lossless
    AudioQualityTier.HiResolution -> MusicQuality.HiResolution
    AudioQualityTier.Immersive -> MusicQuality.ImmersiveSurround
    AudioQualityTier.Master -> MusicQuality.UltraClearMaster
}
