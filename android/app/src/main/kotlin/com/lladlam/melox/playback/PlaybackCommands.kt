package com.lladlam.melox.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.audio.MusicQualityRuntime
import com.lladlam.melox.core.model.SearchSong
import java.util.concurrent.Executor

object PlaybackCommands {
    private const val TAG = "MeloXPlayback"

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
                    val queue = songs
                        .ifEmpty { return@addListener }
                        .map { song -> song.toMediaItem(quality) }
                    val startIndex = songs.indexOfFirst { it.id == selectedSongId }
                        .takeIf { it >= 0 }
                        ?: 0

                    activeController?.takeIf { it !== controller }?.release()
                    activeController = controller

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
        controller.addMediaItem(song.toMediaItem(quality))
    }

    fun playNext(context: Context, song: SearchSong) {
        val quality = MusicQualityPreferences.read(context.applicationContext)
        val controller = activeController
        if (controller == null) {
            playQueue(context, listOf(song), song.id)
            return
        }
        val insertion = (controller.currentMediaItemIndex + 1).coerceIn(0, controller.mediaItemCount)
        controller.addMediaItem(insertion, song.toMediaItem(quality))
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

    private fun SearchSong.toMediaItem(quality: MusicQuality): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(name)
            .setArtist(artists)
            .setAlbumTitle(album)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply {
                artworkUrl
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
