package com.lladlam.melox.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.audio.MusicQualityRuntime
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.model.SearchSong
import java.util.concurrent.Executor

object PlaybackCommands {
    private const val TAG = "MeloXPlayback"
    const val QUEUE_ORIGIN_KEY = "melox.queue.origin"
    const val QUEUE_ORIGIN_BASE = "base"
    const val QUEUE_ORIGIN_MANUAL = "manual"
    const val QUEUE_ORIGINAL_INDEX_KEY = "melox.queue.original_index"
    const val QUEUE_ORIGINAL_INDEX_UNSET = -1

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }

    @Volatile
    private var activeController: MediaController? = null

    fun playQueue(
        context: Context,
        songs: List<SearchSong>,
        selectedSongId: Long,
        onFailure: ((Throwable) -> Unit)? = null,
    ) {
        val appContext = context.applicationContext
        val quality = MusicQualityPreferences.read(appContext)
        MusicQualityRuntime.selected = quality
        val token = SessionToken(
            appContext,
            ComponentName(appContext, MeloXPlaybackService::class.java),
        )
        val controllerFuture = MediaController.Builder(appContext, token).buildAsync()

        controllerFuture.addListener(
            {
                try {
                    val controller = controllerFuture.get()
                    val sourceSongs = songs.ifEmpty { return@addListener }
                    val downloads = MeloXDownloadStore.get(appContext)
                    val originalQueue = sourceSongs.mapIndexed { index, song ->
                        song.toMediaItem(
                            quality = quality,
                            queueOrigin = QUEUE_ORIGIN_BASE,
                            originalIndex = index,
                            artworkOverride = downloads.localArtworkUri(song.id),
                        )
                    }
                    val sourceStartIndex = sourceSongs.indexOfFirst { it.id == selectedSongId }
                        .takeIf { it >= 0 } ?: 0
                    val useShuffle = MeloXPlaybackModePreferences.shuffle(appContext)
                    val queue = if (useShuffle) {
                        val selected = originalQueue[sourceStartIndex]
                        listOf(selected) + originalQueue
                            .filterIndexed { index, _ -> index != sourceStartIndex }
                            .shuffled()
                    } else originalQueue
                    val startIndex = if (useShuffle) 0 else sourceStartIndex

                    activeController?.takeIf { it !== controller }?.release()
                    activeController = controller

                    // MeloX uses the physical pending-play list as the source of truth.
                    // Keep Media3's opaque shuffle order disabled so UI, next(), and AutoMix
                    // all observe exactly the same order.
                    controller.shuffleModeEnabled = false
                    controller.setMediaItems(queue, startIndex, C.TIME_UNSET)
                    controller.prepare()
                    controller.play()

                    Log.d(
                        TAG,
                        "Playback queue dispatched: size=${queue.size}, start=$startIndex, song=$selectedSongId, quality=${quality.apiLevel}",
                    )
                } catch (error: Throwable) {
                    Log.e(TAG, "Unable to connect MediaController", error)
                    onFailure?.invoke(error)
                }
            },
            mainExecutor,
        )
    }


    fun addToQueue(context: Context, song: SearchSong) {
        val quality = MusicQualityPreferences.read(context.applicationContext)
        val controller = activeController
        if (controller == null) {
            playQueue(context, listOf(song), song.id)
            return
        }
        controller.addMediaItem(song.toMediaItem(quality, QUEUE_ORIGIN_MANUAL, QUEUE_ORIGINAL_INDEX_UNSET, MeloXDownloadStore.get(context.applicationContext).localArtworkUri(song.id)))
    }

    fun playNext(context: Context, song: SearchSong) {
        val quality = MusicQualityPreferences.read(context.applicationContext)
        val controller = activeController
        if (controller == null) {
            playQueue(context, listOf(song), song.id)
            return
        }
        val insertion = (controller.currentMediaItemIndex + 1).coerceIn(0, controller.mediaItemCount)
        controller.addMediaItem(insertion, song.toMediaItem(quality, QUEUE_ORIGIN_MANUAL, QUEUE_ORIGINAL_INDEX_UNSET, MeloXDownloadStore.get(context.applicationContext).localArtworkUri(song.id)))
    }

    /**
     * Persist a MeloX quality choice and rebuild the currently installed queue
     * with quality-bearing melox:// URIs. This forces ExoPlayer to reopen the
     * current item, so a switch from standard to lossless/Hi-Res takes effect
     * immediately instead of waiting for the next song.
     */
    fun changeQuality(
        context: Context,
        quality: MusicQuality,
    ) {
        val appContext = context.applicationContext
        MusicQualityPreferences.write(appContext, quality)
        MusicQualityRuntime.selected = quality
        MusicQualityRuntime.clear()

        val controller = activeController ?: return
        val currentIndex = controller.currentMediaItemIndex.coerceAtLeast(0)
        val currentPosition = controller.currentPosition.coerceAtLeast(0L)
        val shouldResume = controller.playWhenReady
        val items = List(controller.mediaItemCount) { index ->
            val item = controller.getMediaItemAt(index)
            val songId = item.mediaId.toLongOrNull()
            if (songId == null) {
                item
            } else {
                MediaItem.Builder()
                    .setMediaId(item.mediaId)
                    .setUri(NeteasePlaybackResolver.uriForSong(songId, quality))
                    .setMediaMetadata(item.mediaMetadata)
                    .build()
            }
        }

        if (items.isEmpty()) return
        controller.setMediaItems(
            items,
            currentIndex.coerceIn(0, items.lastIndex),
            currentPosition,
        )
        controller.prepare()
        if (shouldResume) controller.play()
    }

    fun setExplicitShuffle(
        context: Context,
        player: Player,
        enabled: Boolean,
    ) {
        val count = player.mediaItemCount
        if (count <= 0 || player.currentMediaItemIndex !in 0 until count) {
            MeloXPlaybackModePreferences.setShuffle(context, enabled)
            player.shuffleModeEnabled = false
            return
        }
        val currentIndex = player.currentMediaItemIndex
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val resume = player.playWhenReady
        val items = List(count) { player.getMediaItemAt(it) }
        val historyAndCurrent = items.take(currentIndex + 1)
        val future = items.drop(currentIndex + 1)
        val manual = future.filter { it.queueOrigin() == QUEUE_ORIGIN_MANUAL }
        val base = future.filter { it.queueOrigin() != QUEUE_ORIGIN_MANUAL }
        val orderedBase = if (enabled) {
            base.shuffled()
        } else {
            base.sortedWith(compareBy<MediaItem> { item ->
                item.mediaMetadata.extras?.getInt(QUEUE_ORIGINAL_INDEX_KEY, QUEUE_ORIGINAL_INDEX_UNSET)
                    ?.takeIf { it >= 0 } ?: Int.MAX_VALUE
            }.thenBy { it.mediaId })
        }
        val rebuilt = historyAndCurrent + manual + orderedBase
        player.shuffleModeEnabled = false
        player.setMediaItems(rebuilt, currentIndex.coerceIn(0, rebuilt.lastIndex), currentPosition)
        player.prepare()
        if (resume) player.play()
        MeloXPlaybackModePreferences.setShuffle(context, enabled)
    }

    internal fun mediaItemFor(
        song: SearchSong,
        quality: MusicQuality = MusicQualityRuntime.selected,
        queueOrigin: String = QUEUE_ORIGIN_BASE,
        originalIndex: Int = QUEUE_ORIGINAL_INDEX_UNSET,
        artworkOverride: Uri? = null,
    ): MediaItem = song.toMediaItem(quality, queueOrigin, originalIndex, artworkOverride)

    private fun MediaItem.queueOrigin(): String =
        mediaMetadata.extras?.getString(QUEUE_ORIGIN_KEY) ?: QUEUE_ORIGIN_BASE

    private fun SearchSong.toMediaItem(
        quality: MusicQuality,
        queueOrigin: String,
        originalIndex: Int = QUEUE_ORIGINAL_INDEX_UNSET,
        artworkOverride: Uri? = null,
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(name)
            .setArtist(artists)
            .setAlbumTitle(album)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(Bundle().apply {
                putString(QUEUE_ORIGIN_KEY, queueOrigin)
                putInt(QUEUE_ORIGINAL_INDEX_KEY, originalIndex)
            })
            .apply {
                artworkOverride?.let(::setArtworkUri) ?: artworkUrl
                    ?.takeIf(String::isNotBlank)
                    ?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(NeteasePlaybackResolver.uriForSong(id, quality))
            .setMediaMetadata(metadata)
            .build()
    }
}
