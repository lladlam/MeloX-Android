package com.lladlam.melox.playback

import android.util.Log
import androidx.media3.common.Player
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper
import com.hchen.superlyricapi.SuperLyricLine
import com.hchen.superlyricapi.SuperLyricWord
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricsDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Adapts the shared lyric timeline to SuperLyricApi's current-line publishing
 * contract. Ported from Mei_MeloX_Android.
 */
internal class SuperLyricPublisher(
    private val player: Player,
    private val scope: CoroutineScope,
) {
    private var songId: String? = null
    private var document: LyricsDocument? = null
    private var songName: String? = null
    private var artistName: String? = null
    private var albumName: String? = null
    private var displayTranslation = true
    private var displayRoma = true
    private var updateJob: Job? = null
    private var lastLineIndex = Int.MIN_VALUE
    private var reportedFailure = false

    fun setSong(
        document: LyricsDocument?,
        songId: String?,
        songName: String?,
        artistName: String?,
        albumName: String?,
        translation: Boolean,
        roma: Boolean,
    ) {
        this.document = document
        this.songId = songId
        this.songName = songName
        this.artistName = artistName
        this.albumName = albumName
        displayTranslation = translation
        displayRoma = roma
        lastLineIndex = Int.MIN_VALUE
    }

    fun syncPlayback() {
        updateJob?.cancel()
        lastLineIndex = Int.MIN_VALUE
        if (!player.isPlaying || document == null) {
            publishCurrentLine()
            return
        }
        updateJob = scope.launch {
            while (isActive && player.isPlaying) {
                publishCurrentLine()
                val position = player.currentPosition.coerceAtLeast(0L)
                val nextBoundary = document?.lines.orEmpty().asSequence()
                    .flatMap { line ->
                        sequenceOf(line.timeMs, line.timeMs + (line.durationMs ?: 3_000L))
                    }
                    .filter { it > position }
                    .minOrNull()
                val waitMs = nextBoundary?.let {
                    ((it - position) / player.playbackParameters.speed.toDouble()).toLong()
                } ?: 1_000L
                delay(waitMs.coerceIn(20L, 1_000L))
            }
        }
    }

    private fun publishCurrentLine() {
        callApi {
            if (!SuperLyricHelper.isAvailable()) {
                lastLineIndex = Int.MIN_VALUE
                return@callApi
            }
            if (!SuperLyricHelper.isPublisherRegistered()) {
                SuperLyricHelper.registerPublisher()
                SuperLyricHelper.setSystemPlayStateListenerEnabled(true)
                lastLineIndex = Int.MIN_VALUE
            }
            val current = document
            val position = player.currentPosition.coerceAtLeast(0L)
            val lines = current?.lines.orEmpty()
            val index = if (player.isPlaying && current != null && !current.isUnsynchronized &&
                songId == player.currentMediaItem?.mediaId
            ) {
                lines.indexOfLast { position >= it.timeMs }
            } else {
                -1
            }
            if (index == lastLineIndex) return@callApi
            val data = SuperLyricData()
                .setTitle(songName)
                .setArtist(artistName)
                .setAlbum(albumName)
            if (index < 0) {
                SuperLyricHelper.sendStop(data)
            } else {
                val line = lines[index]
                data.setLyric(line.toSuperLyricLine())
                if (displayTranslation) {
                    line.translation?.takeIf { it.isNotBlank() }?.let {
                        data.setTranslation(SuperLyricLine(it, line.timeMs, lineEndMs(line, lines, index)))
                    }
                }
                val secondary = line.romanization?.takeIf { displayRoma && it.isNotBlank() }
                secondary?.let {
                    data.setSecondary(SuperLyricLine(it, line.timeMs, lineEndMs(line, lines, index)))
                }
                SuperLyricHelper.sendLyric(data)
            }
            lastLineIndex = index
        }
    }

    private fun lineEndMs(line: LyricLine, lines: List<LyricLine>, index: Int): Long =
        line.durationMs?.let { line.timeMs + it }
            ?: lines.getOrNull(index + 1)?.timeMs
            ?: (line.timeMs + 3_000L)

    fun stop() {
        updateJob?.cancel()
        updateJob = null
        callApi {
            if (SuperLyricHelper.isAvailable() && SuperLyricHelper.isPublisherRegistered()) {
                SuperLyricHelper.sendStop(SuperLyricData())
                SuperLyricHelper.unregisterPublisher()
            }
        }
        document = null
        lastLineIndex = Int.MIN_VALUE
    }

    private inline fun callApi(block: () -> Unit) {
        try {
            block()
        } catch (error: Exception) {
            reportFailure(error)
        } catch (error: LinkageError) {
            // Missing platform API access must not interrupt the playback service.
            reportFailure(error)
        }
    }

    private fun reportFailure(error: Throwable) {
        lastLineIndex = Int.MIN_VALUE
        if (!reportedFailure) {
            reportedFailure = true
            Log.w("MeloXSuperLyric", "SuperLyricApi publication failed", error)
        }
    }
}

private fun LyricLine.toSuperLyricLine(): SuperLyricLine = SuperLyricLine(
    text,
    syllables
        .filter { it.text.isNotBlank() }
        .map { SuperLyricWord(it.text, it.startTimeMs, it.endTimeMs) }
        .toTypedArray(),
    timeMs,
    lineEndMsForLine(this),
)

private fun lineEndMsForLine(line: LyricLine): Long =
    line.durationMs?.let { line.timeMs + it } ?: (line.timeMs + 3_000L)
