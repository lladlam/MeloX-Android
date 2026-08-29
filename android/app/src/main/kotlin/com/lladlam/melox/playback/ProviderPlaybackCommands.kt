package com.lladlam.melox.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import java.util.UUID
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.audio.MusicQualityRuntime
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.provider.applemusic.AppleMusicSdkBridge
import com.lladlam.melox.core.provider.applemusic.AppleMusicSessionStore
import java.util.concurrent.Executor

/** Queue entry point for QQ/Kugou and future mixed-provider results. */
object ProviderPlaybackCommands {
    fun playQueue(
        context: Context,
        tracks: List<MusicTrack>,
        selectedTrackId: MusicResourceId,
        startPositionMs: Long = C.TIME_UNSET,
        onFailure: ((Throwable) -> Unit)? = null,
    ) {
        if (tracks.isEmpty()) return
        val appContext = context.applicationContext
        if (tracks.all { it.id.source == MusicSource.AppleMusic }) {
            // MusicKit playback bypasses Media3, so Media3-backed lyrics remain
            // unavailable for this hidden source until the backend is unified.
            val session = AppleMusicSessionStore.read(appContext)
            val startIndex = tracks.indexOfFirst { it.id == selectedTrackId }.coerceAtLeast(0)
            if (AppleMusicSdkBridge.playCatalogQueue(
                    context = appContext,
                    session = session,
                    catalogIds = tracks.map { it.id.value },
                    startIndex = startIndex,
                )
            ) {
                return
            }
            onFailure?.invoke(IllegalStateException("Apple Music 官方 DRM SDK 未安装或授权未完成"))
            return
        }
        ProviderPlaybackRuntime.initialize(appContext)
        val neteaseQuality = MusicQualityPreferences.read(appContext)
        MusicQualityRuntime.selected = neteaseQuality
        val qualityTier = neteaseQuality.toCommonTier()
        val startIndex = tracks.indexOfFirst { it.id == selectedTrackId }.coerceAtLeast(0)
        val items = tracks.mapIndexed { index, track ->
            track.toMediaItem(
                neteaseQuality = neteaseQuality,
                qualityTier = qualityTier,
                originalIndex = index,
            )
        }
        val token = SessionToken(
            appContext,
            ComponentName(appContext, MeloXPlaybackService::class.java),
        )
        val future = MediaController.Builder(appContext, token).buildAsync()
        val executor = Executor { command -> ContextCompat.getMainExecutor(appContext).execute(command) }
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    // One controller ownership path for both legacy MeloX UI actions
                    // and provider-backed queues. Existing UI can add/play-next
                    // without knowing which service owns the track.
                    PlaybackCommands.adoptController(controller)
                    controller.shuffleModeEnabled = false
                    controller.setMediaItems(items, startIndex, startPositionMs)
                    controller.prepare()
                    controller.play()
                }.onFailure { onFailure?.invoke(it) }
            },
            executor,
        )
    }

    internal fun mediaItemFor(
        track: MusicTrack,
        neteaseQuality: MusicQuality = MusicQuality.Standard,
        qualityTier: AudioQualityTier = neteaseQuality.toCommonTier(),
        queueOrigin: String = PlaybackCommands.QUEUE_ORIGIN_BASE,
        originalIndex: Int = PlaybackCommands.QUEUE_ORIGINAL_INDEX_UNSET,
    ): MediaItem = track.toMediaItem(neteaseQuality, qualityTier, queueOrigin, originalIndex)

    private fun MusicTrack.toMediaItem(
        neteaseQuality: MusicQuality,
        qualityTier: AudioQualityTier,
        queueOrigin: String = PlaybackCommands.QUEUE_ORIGIN_BASE,
        originalIndex: Int,
    ): MediaItem {
        val extras = Bundle().apply {
            putString(PlaybackCommands.QUEUE_ORIGIN_KEY, queueOrigin)
            putInt(PlaybackCommands.QUEUE_ORIGINAL_INDEX_KEY, originalIndex)
            putString(PlaybackCommands.QUEUE_ENTRY_ID_KEY, UUID.randomUUID().toString())
            putBoolean(PlaybackCommands.HEART_MODE_KEY, false)
            putString(PlaybackTrackIdentity.SourceExtra, id.source.storageValue)
            putString(PlaybackTrackIdentity.ResourceIdExtra, id.value)
            putLong(PlaybackTrackIdentity.DurationMsExtra, normalizedQueueDurationMs(durationMs))
            putString(PlaybackTrackIdentity.TitleExtra, title)
            putString(PlaybackTrackIdentity.ArtistExtra, artistText)
            putString(PlaybackTrackIdentity.AlbumExtra, album?.name.orEmpty())
            putString(PlaybackTrackIdentity.ArtworkExtra, artworkUrl.orEmpty())
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artistText)
            .setAlbumTitle(album?.name.orEmpty())
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(extras)
            .apply {
                artworkUrl?.takeIf(String::isNotBlank)?.let {
                    setArtworkUri(android.net.Uri.parse(it))
                }
            }
            .build()

        val neteaseId = id.value.toLongOrNull()
            ?.takeIf { id.source == MusicSource.Netease && it > 0L }
        val localUri = (providerMetadata as? ProviderTrackMetadata.Local)
            ?.contentUri?.takeIf(String::isNotBlank)
        return MediaItem.Builder()
            .setMediaId(neteaseId?.toString() ?: PlaybackTrackIdentity.encode(id))
            .setUri(
                if (localUri != null) {
                    Uri.parse(localUri)
                } else if (neteaseId != null) {
                    NeteasePlaybackResolver.uriForSong(
                        songId = neteaseId,
                        quality = neteaseQuality,
                        title = title,
                        artist = artistText,
                        durationMs = durationMs,
                    )
                } else {
                    ProviderPlaybackResolver.uriForTrack(this, qualityTier)
                },
            )
            .setMediaMetadata(metadata)
            .build()
    }
}

internal fun MusicQuality.toCommonTier(): AudioQualityTier = when (this) {
    MusicQuality.Standard -> AudioQualityTier.Standard
    MusicQuality.High -> AudioQualityTier.High
    MusicQuality.Lossless -> AudioQualityTier.Lossless
    MusicQuality.HiResolution -> AudioQualityTier.HiResolution
    MusicQuality.HighDefinitionSurround,
    MusicQuality.ImmersiveSurround -> AudioQualityTier.Immersive
    MusicQuality.UltraClearMaster -> AudioQualityTier.Master
}

internal fun normalizedQueueDurationMs(durationMs: Long?): Long = durationMs?.coerceAtLeast(0L) ?: 0L
