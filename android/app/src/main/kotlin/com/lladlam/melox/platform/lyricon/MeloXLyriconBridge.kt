package com.lladlam.melox.platform.lyricon

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricSyllable
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.provider.bilibili.BilibiliLyricOffsetStore
import com.lladlam.melox.playback.PlaybackTrackIdentity
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.playback.MeloXPlaybackService
import com.lladlam.melox.ui.player.MeloXPlaybackUiState
import com.lladlam.melox.ui.player.MeloXProviderLyricsLoader
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Bridges MeloX's provider-neutral Media3 playback/lyrics state into Lyricon.
 *
 * One MediaController is kept for the application process. Lyrics are loaded only when the
 * media id changes, then sent as a complete Song/RichLyricLine timeline. Playback position and
 * playing state are lightweight shared-memory updates, so Lyricon can render word-timed lyrics
 * without MeloX broadcasting a new line every frame.
 */
object MeloXLyriconBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var started = false
    private var provider: LyriconProvider? = null
    private var playbackState: MeloXPlaybackUiState? = null
    private var syncJob: Job? = null
    private var lyricLoadJob: Job? = null

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true

        val appContext = context.applicationContext
        val lyriconProvider = LyriconFactory.createProvider(appContext).apply {
            autoSync = true
            register()
        }
        provider = lyriconProvider

        val state = MeloXPlaybackUiState(appContext)
        playbackState = state
        val token = SessionToken(
            appContext,
            ComponentName(appContext, MeloXPlaybackService::class.java),
        )
        val future = MediaController.Builder(appContext, token).buildAsync()
        val handler = Handler(Looper.getMainLooper())
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { controller ->
                        state.bind(controller)
                        startSyncLoop(appContext, state, lyriconProvider)
                    }
            },
            { command -> handler.post(command) },
        )
    }

    private fun startSyncLoop(
        context: Context,
        state: MeloXPlaybackUiState,
        lyriconProvider: LyriconProvider,
    ) {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            var observedReloadKey: LyriconReloadKey? = null
            var initialized = false

            while (isActive) {
                state.refresh()
                val mediaId = state.mediaId
                val reloadKey = lyriconReloadKey(
                    mediaId = mediaId,
                    title = state.title,
                    artist = state.artist,
                    durationMs = state.durationMs,
                    automaticSelection = MeloXSettingsRuntime.automaticLyricSelectionEnabled,
                )
                if (!initialized || reloadKey != observedReloadKey) {
                    initialized = true
                    observedReloadKey = reloadKey
                    lyricLoadJob?.cancel()

                    if (mediaId == null) {
                        lyriconProvider.player.setSong(null)
                    } else {
                        val requestedKey = requireNotNull(reloadKey)
                        lyricLoadJob = scope.launch {
                            val document = runCatching {
                                MeloXProviderLyricsLoader.load(context, state)
                            }.getOrElse { LyricsDocument(emptyList()) }
                            if (lyriconReloadKey(
                                    mediaId = state.mediaId,
                                    title = state.title,
                                    artist = state.artist,
                                    durationMs = state.durationMs,
                                    automaticSelection = MeloXSettingsRuntime.automaticLyricSelectionEnabled,
                                ) != requestedKey
                            ) return@launch

                            lyriconProvider.player.setSong(
                                document.toLyriconSong(
                                    mediaId = requestedKey.mediaId,
                                    title = requestedKey.title,
                                    artist = requestedKey.artist,
                                    durationMs = requestedKey.durationMs,
                                ),
                            )
                            lyriconProvider.player.setDisplayTranslation(
                                document.lines.any { !it.translation.isNullOrBlank() },
                            )
                            lyriconProvider.player.setDisplayRoma(
                                document.lines.any { !it.romanization.isNullOrBlank() },
                            )
                            lyriconProvider.player.setPlaybackState(state.isPlaying)
                            lyriconProvider.player.setPosition(
                                lyriconPosition(context, requestedKey.mediaId, state.positionMs),
                            )
                        }
                    }
                }

                lyriconProvider.player.setPlaybackState(state.isPlaying)
                lyriconProvider.player.setPosition(lyriconPosition(context, mediaId, state.positionMs))
                delay(if (state.isPlaying) 500L else 1_000L)
            }
        }
    }

    private fun lyriconPosition(context: Context, mediaId: String?, positionMs: Long): Long {
        val identity = mediaId?.let(PlaybackTrackIdentity::decode)
        val offset = if (identity?.source == MusicSource.Bilibili) {
            BilibiliLyricOffsetStore.read(context, identity.value)
        } else 0
        val advance = MeloXSettingsRuntime.lyricAdvanceMs + offset
        return (positionMs + advance).coerceAtLeast(0L)
    }

    private fun LyricsDocument.toLyriconSong(
        mediaId: String,
        title: String,
        artist: String,
        durationMs: Long,
    ): Song {
        val richLines = lines.mapIndexedNotNull { index, line ->
            line.toLyriconLine(lines.getOrNull(index + 1)?.timeMs)
        }
        return Song(
            id = mediaId,
            name = title.ifBlank { null },
            artist = artist.ifBlank { null },
            duration = durationMs.coerceAtLeast(0L),
            lyrics = richLines,
        )
    }

    private fun LyricLine.toLyriconLine(nextStartMs: Long?): RichLyricLine? {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return null

        val beginMs = timeMs.coerceAtLeast(0L)
        val inferredDuration = durationMs
            ?: nextStartMs?.minus(beginMs)?.takeIf { it > 0L }
            ?: 3_000L
        val endMs = (beginMs + inferredDuration.coerceAtLeast(1L)).coerceAtLeast(beginMs + 1L)

        return RichLyricLine(
            begin = beginMs,
            end = endMs,
            duration = endMs - beginMs,
            text = text,
            words = syllables.toLyriconWords(),
            translation = translation,
            roma = romanization,
        )
    }

    private fun List<LyricSyllable>.toLyriconWords(): List<LyricWord>? =
        takeIf { it.isNotEmpty() }?.mapNotNull { syllable ->
            val text = syllable.text.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val beginMs = syllable.startTimeMs.coerceAtLeast(0L)
            val endMs = syllable.endTimeMs.coerceAtLeast(beginMs + 1L)
            LyricWord(
                begin = beginMs,
                end = endMs,
                duration = endMs - beginMs,
                text = text,
            )
        }?.takeIf { it.isNotEmpty() }
}

internal data class LyriconReloadKey(
    val mediaId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val automaticSelection: Boolean,
)

internal fun lyriconReloadKey(
    mediaId: String?,
    title: String,
    artist: String,
    durationMs: Long,
    automaticSelection: Boolean,
): LyriconReloadKey? = mediaId?.let {
    LyriconReloadKey(
        mediaId = it,
        title = title,
        artist = artist,
        durationMs = stableLyriconDurationMs(durationMs),
        automaticSelection = automaticSelection,
    )
}

internal fun stableLyriconDurationMs(durationMs: Long): Long =
    durationMs.takeIf { it > 0L }?.let { ((it + 500L) / 1_000L) * 1_000L } ?: 0L
