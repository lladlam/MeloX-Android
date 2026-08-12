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
import com.lladlam.melox.core.network.MeloXNetworkAvailability
import java.io.IOException
import java.util.concurrent.Executor

object PlaybackCommands {
    private const val TAG = "MeloXPlayback"
    const val QUEUE_ORIGIN_KEY = "melox.queue.origin"
    const val QUEUE_ORIGIN_BASE = "base"
    const val QUEUE_ORIGIN_MANUAL = "manual"
    const val QUEUE_ORIGINAL_INDEX_KEY = "melox.queue.original_index"
    const val QUEUE_ORIGINAL_INDEX_UNSET = -1
    const val HEART_MODE_KEY = "melox.playback.heart_mode"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }

    @Volatile
    private var activeController: MediaController? = null

    fun currentSongId(): Long? = activeController?.currentMediaItem?.mediaId?.toLongOrNull()

    fun playQueue(
        context: Context,
        songs: List<SearchSong>,
        selectedSongId: Long,
        startPositionMs: Long = C.TIME_UNSET,
        heartMode: Boolean = false,
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
                    val offline = !MeloXNetworkAvailability.isOnline(appContext)
                    val sourceStartIndex = sourceSongs.indexOfFirst { it.id == selectedSongId }
                        .takeIf { it >= 0 } ?: 0
                    val playable = sourceSongs.mapIndexed { index, song -> index to song }
                        .let { indexed ->
                            if (offline) indexed.filter { (_, song) -> downloads.contains(song.id) }
                            else indexed
                        }
                    if (playable.isEmpty()) {
                        controller.release()
                        onFailure?.invoke(IOException("离线状态下没有可播放的已下载歌曲"))
                        return@addListener
                    }
                    val selectedPair = playable.firstOrNull { it.first == sourceStartIndex }
                        ?: playable.firstOrNull { it.first > sourceStartIndex }
                        ?: playable.first()
                    val originalQueue = playable.map { (index, song) ->
                        song.toMediaItem(
                            quality = quality,
                            queueOrigin = QUEUE_ORIGIN_BASE,
                            originalIndex = index,
                            artworkOverride = downloads.localArtworkUri(song.id),
                            heartMode = heartMode,
                        )
                    }
                    val selectedItemIndex = originalQueue.indexOfFirst {
                        it.mediaMetadata.extras?.getInt(QUEUE_ORIGINAL_INDEX_KEY) == selectedPair.first
                    }.coerceAtLeast(0)
                    val useShuffle = MeloXPlaybackModePreferences.shuffle(appContext)
                    val queue = if (useShuffle) {
                        val selected = originalQueue[selectedItemIndex]
                        listOf(selected) + originalQueue.filterIndexed { index, _ -> index != selectedItemIndex }.shuffled()
                    } else {
                        originalQueue
                    }
                    val startIndex = if (useShuffle) 0 else selectedItemIndex

                    activeController?.takeIf { it !== controller }?.release()
                    activeController = controller
                    controller.shuffleModeEnabled = false
                    controller.setMediaItems(queue, startIndex, startPositionMs)
                    MeloXPlaybackModeRuntime.heartModeActive = heartMode
                    controller.prepare()
                    controller.play()

                    Log.d(
                        TAG,
                        "Playback queue dispatched: size=${queue.size}, start=$startIndex, offline=$offline, quality=${quality.apiLevel}",
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
        val appContext = context.applicationContext
        val downloads = MeloXDownloadStore.get(appContext)
        if (!MeloXNetworkAvailability.isOnline(appContext) && !downloads.contains(song.id)) return
        val quality = MusicQualityPreferences.read(appContext)
        val controller = activeController
        if (controller == null) {
            playQueue(context, listOf(song), song.id)
            return
        }
        prioritizeManualQueue(controller)
        val current = controller.currentMediaItemIndex.coerceAtLeast(-1)
        val manualCount = ((current + 1) until controller.mediaItemCount).count { index ->
            controller.getMediaItemAt(index).queueOrigin() == QUEUE_ORIGIN_MANUAL
        }
        val insertion = (current + 1 + manualCount).coerceIn(0, controller.mediaItemCount)
        controller.addMediaItem(
            insertion,
            song.toMediaItem(
                quality,
                QUEUE_ORIGIN_MANUAL,
                QUEUE_ORIGINAL_INDEX_UNSET,
                downloads.localArtworkUri(song.id),
            ),
        )
    }

    fun playNext(context: Context, song: SearchSong) {
        val appContext = context.applicationContext
        val downloads = MeloXDownloadStore.get(appContext)
        if (!MeloXNetworkAvailability.isOnline(appContext) && !downloads.contains(song.id)) return
        val quality = MusicQualityPreferences.read(appContext)
        val controller = activeController
        if (controller == null) {
            playQueue(context, listOf(song), song.id)
            return
        }
        prioritizeManualQueue(controller)
        val insertion = (controller.currentMediaItemIndex + 1).coerceIn(0, controller.mediaItemCount)
        controller.addMediaItem(
            insertion,
            song.toMediaItem(
                quality,
                QUEUE_ORIGIN_MANUAL,
                QUEUE_ORIGINAL_INDEX_UNSET,
                downloads.localArtworkUri(song.id),
            ),
        )
    }

    fun changeQuality(context: Context, quality: MusicQuality) {
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
            if (songId == null) item else MediaItem.Builder()
                .setMediaId(item.mediaId)
                .setUri(NeteasePlaybackResolver.uriForSong(songId, quality))
                .setMediaMetadata(item.mediaMetadata)
                .build()
        }
        if (items.isEmpty()) return
        controller.setMediaItems(items, currentIndex.coerceIn(0, items.lastIndex), currentPosition)
        controller.prepare()
        if (shouldResume) controller.play()
    }

    /**
     * Shuffle is represented by the real future timeline. We move existing items
     * instead of rebuilding the player, so the currently playing decoder/position
     * stays untouched. Manual queue items always remain before base items.
     */
    fun setExplicitShuffle(context: Context, player: Player, enabled: Boolean) {
        val count = player.mediaItemCount
        if (count <= 0 || player.currentMediaItemIndex !in 0 until count) {
            MeloXPlaybackModePreferences.setShuffle(context, enabled)
            player.shuffleModeEnabled = false
            return
        }
        val currentIndex = player.currentMediaItemIndex
        val items = List(count) { player.getMediaItemAt(it) }
        val future = items.drop(currentIndex + 1)
        val manual = future.filter { it.queueOrigin() == QUEUE_ORIGIN_MANUAL }
        val base = future.filter { it.queueOrigin() != QUEUE_ORIGIN_MANUAL }
        val orderedBase = if (enabled) {
            base.shuffled()
        } else {
            base.sortedWith(
                compareBy<MediaItem> { item ->
                    item.mediaMetadata.extras
                        ?.getInt(QUEUE_ORIGINAL_INDEX_KEY, QUEUE_ORIGINAL_INDEX_UNSET)
                        ?.takeIf { it >= 0 } ?: Int.MAX_VALUE
                }.thenBy { it.mediaId },
            )
        }
        reorderFutureInPlace(player, manual + orderedBase)
        player.shuffleModeEnabled = false
        MeloXPlaybackModePreferences.setShuffle(context, enabled)
    }

    fun prioritizeManualQueue(player: Player) {
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex !in 0 until player.mediaItemCount) return
        val items = List(player.mediaItemCount) { player.getMediaItemAt(it) }
        val future = items.drop(currentIndex + 1)
        val manual = future.filter { it.queueOrigin() == QUEUE_ORIGIN_MANUAL }
        val base = future.filter { it.queueOrigin() != QUEUE_ORIGIN_MANUAL }
        if (manual.isEmpty()) return
        reorderFutureInPlace(player, manual + base)
    }

    internal fun mediaItemFor(
        song: SearchSong,
        quality: MusicQuality = MusicQualityRuntime.selected,
        queueOrigin: String = QUEUE_ORIGIN_BASE,
        originalIndex: Int = QUEUE_ORIGINAL_INDEX_UNSET,
        artworkOverride: Uri? = null,
        heartMode: Boolean = MeloXPlaybackModeRuntime.heartModeActive,
    ): MediaItem = song.toMediaItem(quality, queueOrigin, originalIndex, artworkOverride, heartMode)

    private fun reorderFutureInPlace(player: Player, desiredFuture: List<MediaItem>) {
        val start = player.currentMediaItemIndex + 1
        if (start < 0 || desiredFuture.isEmpty()) return
        val simulated = MutableList(player.mediaItemCount) { player.getMediaItemAt(it) }
        desiredFuture.forEachIndexed { offset, wanted ->
            val target = start + offset
            if (target !in simulated.indices) return@forEachIndexed
            val from = (target until simulated.size).firstOrNull { simulated[it] === wanted }
                ?: (target until simulated.size).firstOrNull { simulated[it] == wanted }
                ?: return@forEachIndexed
            if (from != target) {
                player.moveMediaItem(from, target)
                val moved = simulated.removeAt(from)
                simulated.add(target, moved)
            }
        }
    }

    private fun MediaItem.queueOrigin(): String =
        mediaMetadata.extras?.getString(QUEUE_ORIGIN_KEY) ?: QUEUE_ORIGIN_BASE

    private fun SearchSong.toMediaItem(
        quality: MusicQuality,
        queueOrigin: String,
        originalIndex: Int = QUEUE_ORIGINAL_INDEX_UNSET,
        artworkOverride: Uri? = null,
        heartMode: Boolean = MeloXPlaybackModeRuntime.heartModeActive,
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(name)
            .setArtist(artists)
            .setAlbumTitle(album)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(
                Bundle().apply {
                    putString(QUEUE_ORIGIN_KEY, queueOrigin)
                    putInt(QUEUE_ORIGINAL_INDEX_KEY, originalIndex)
                    putBoolean(HEART_MODE_KEY, heartMode)
                },
            )
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
