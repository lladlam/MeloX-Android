package com.lladlam.melox.core.provider.local

import android.content.Context
import android.net.Uri
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.lyrics.AmlldbLyricsClient
import com.lladlam.melox.core.recognition.SongRecognitionClient
import kotlin.math.abs
import kotlinx.coroutines.CancellationException

data class LocalRecognitionOutcome(
    val local: LocalTrackRecord,
    val matched: SearchSong?,
)

class LocalRecognitionCoordinator(
    context: Context,
    private val repository: LocalMusicRepository = LocalMusicRepository(context),
) {
    private val appContext = context.applicationContext

    suspend fun recognize(fileKey: String): LocalRecognitionOutcome {
        val local = repository.track(fileKey) ?: error("本地歌曲记录不存在")
        val recognition = SongRecognitionClient(appContext)
        val result = try {
            val segmentLengthMs = 9_000L
            val lastStartMs = (local.durationMs - segmentLengthMs).coerceAtLeast(0L)
            val starts = listOf(
                (local.durationMs - segmentLengthMs).coerceAtLeast(0L) / 2L,
                0L,
                lastStartMs,
            ).distinct()
            var lastFailure: Throwable? = null
            var matched: com.lladlam.melox.core.recognition.SongRecognitionResult? = null
            for (startMs in starts) {
                try {
                    matched = recognition.recognizeLocal(Uri.parse(local.contentUri), local.durationMs, startMs)
                        .firstOrNull()
                    if (matched != null) break
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    lastFailure = failure
                }
            }
            if (matched == null && starts.isNotEmpty() && lastFailure != null) throw lastFailure
            matched
        } finally {
            recognition.close()
        }
        val matched = result?.song?.let { recognized ->
            val candidates = NeteaseSearchClient(cookieProvider = { "" }).searchSongs(
                "${recognized.name} ${recognized.artists}",
                limit = 20,
            )
            candidates
                .filter { candidate ->
                    candidate.name.equals(recognized.name, ignoreCase = true) &&
                        candidate.artists.split(" / ").any { it.equals(recognized.artists, ignoreCase = true) } &&
                        (local.durationMs <= 0L || candidate.durationMs <= 0L || abs(candidate.durationMs - local.durationMs) <= 8_000L)
                }
                .minByOrNull { candidate ->
                    if (local.durationMs > 0L && candidate.durationMs > 0L) abs(candidate.durationMs - local.durationMs) else Long.MAX_VALUE
                }
                ?: candidates.firstOrNull()
        }
        val enriched = matched?.let {
            NeteaseSearchClient(cookieProvider = { "" }).ensureArtwork(it)
        }
        enriched?.let {
            repository.updateRecognition(fileKey, it)
            runCatching { AmlldbLyricsClient().lyrics(it.id) }
                .getOrNull()
                ?.let { lyrics -> repository.updateLyrics(fileKey, lyrics) }
        }
        return LocalRecognitionOutcome(repository.track(fileKey) ?: local, enriched)
    }
}
