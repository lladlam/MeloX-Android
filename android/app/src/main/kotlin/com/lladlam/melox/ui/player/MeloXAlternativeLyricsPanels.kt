package com.lladlam.melox.ui.player

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricTimelineProcessor
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.lyrics.withPseudoTiming
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import kotlinx.coroutines.isActive

internal data class AlternativeLyricsState(
    val lines: List<LyricLine>,
    val index: Int,
    val loading: Boolean,
    val error: String?,
    val isUnsynchronized: Boolean = false,
)

/**
 * Shared loader for the three alt lyric stages (EVA / Text PV / Skyline).
 * Everything goes through [MeloXProviderLyricsLoader] so the same provider,
 * language, binding and auto-sync rules apply to every visual style.
 */
@Composable
internal fun rememberAlternativeLyrics(
    state: MeloXPlaybackUiState,
    active: Boolean,
): AlternativeLyricsState {
    val context = LocalContext.current.applicationContext
    val mediaId = state.mediaId
    val automaticLyricSelectionEnabled = MeloXSettingsRuntime.automaticLyricSelectionEnabled
    var document by remember(mediaId, automaticLyricSelectionEnabled) { mutableStateOf<LyricsDocument?>(null) }
    var loading by remember(mediaId, automaticLyricSelectionEnabled) { mutableStateOf(false) }
    var error by remember(mediaId, automaticLyricSelectionEnabled) { mutableStateOf<String?>(null) }
    var loadedRequestKey by remember(mediaId, automaticLyricSelectionEnabled) { mutableStateOf<String?>(null) }
    val requestKey = lyricUiRequestKey(state, automaticLyricSelectionEnabled)
    val currentRequestKey = rememberUpdatedState(requestKey)
    LaunchedEffect(active, requestKey) {
        if (!active || mediaId.isNullOrBlank() || loadedRequestKey == requestKey) return@LaunchedEffect
        loading = true
        error = null
        runCatching {
            MeloXProviderLyricsLoader.load(context, state) { upgraded ->
                // AutoSync/AMLL may finish after the fast line-timed path; only
                // accept it while this panel still shows the same track.
                if (state.mediaId == mediaId && currentRequestKey.value == requestKey) document = upgraded
            }
        }
            .onSuccess {
                document = it
                loadedRequestKey = requestKey
            }
            .onFailure { error = it.message ?: "歌词加载失败" }
        loading = false
    }
    val rendered = remember(document, MeloXSettingsRuntime.lyricPseudoTimingEnabled) {
        if (MeloXSettingsRuntime.lyricPseudoTimingEnabled) document?.withPseudoTiming() else document
    }
    val lines = rendered?.lines.orEmpty()
    val trackOffsetMs = rememberBilibiliLyricOffset(mediaId)
    val advanceMs = effectiveBilibiliLyricAdvance(MeloXSettingsRuntime.lyricAdvanceMs, trackOffsetMs)
    var index by remember(mediaId, rendered) {
        mutableIntStateOf(
            rendered?.highlightedIndex(state.positionMs + advanceMs)
                ?.coerceIn(0, lines.lastIndex.coerceAtLeast(0)) ?: 0,
        )
    }
    LaunchedEffect(active, mediaId, rendered, state.positionMs, state.isPlaying, state.durationMs, advanceMs) {
        val activeDocument = rendered ?: return@LaunchedEffect
        if (!active || activeDocument.isUnsynchronized || lines.isEmpty()) return@LaunchedEffect
        val anchorPosition = state.positionMs
        if (!state.isPlaying) {
            val next = activeDocument.highlightedIndex(anchorPosition + advanceMs)
                ?.coerceIn(0, lines.lastIndex) ?: index
            if (next != index) index = next
            return@LaunchedEffect
        }
        val anchorRealtimeMs = SystemClock.elapsedRealtime()
        while (isActive) {
            val position = (anchorPosition + SystemClock.elapsedRealtime() - anchorRealtimeMs).coerceAtMost(
                state.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE,
            )
            val next = activeDocument.highlightedIndex(position + advanceMs)
                ?.coerceIn(0, lines.lastIndex) ?: index
            if (next != index) index = next
            val nextEvent = lines.getOrNull(next + 1)?.timeMs
                ?: LyricTimelineProcessor.nextEventTimeMs(activeDocument, position + advanceMs)
            val waitMs = (nextEvent?.minus(position + advanceMs) ?: 250L).coerceIn(16L, 500L)
            kotlinx.coroutines.delay(waitMs)
        }
    }
    return AlternativeLyricsState(lines, index, loading, error, rendered?.isUnsynchronized == true)
}

internal fun lyricUiRequestKey(state: MeloXPlaybackUiState, automaticSelection: Boolean): String = buildString {
    append(state.mediaId)
    append(':').append(normalizeLyricMatchText(state.title))
    append(':').append(normalizeLyricMatchText(state.artist))
    append(':').append(normalizeLyricMatchText(state.album))
    append(':').append(state.durationMs.takeIf { it > 0L }?.let { ((it + 500L) / 1_000L) * 1_000L } ?: 0L)
    append(':').append(automaticSelection)
    append(':').append(requestedLyricPreference().cacheKey())
}

@Composable
internal fun AlternativeLoading(state: AlternativeLyricsState) {
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(state.error, color = MaterialTheme.colorScheme.error)
        }
        !state.loading && state.lines.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无歌词", color = Color.White.copy(alpha = .56f))
        }
    }
}
