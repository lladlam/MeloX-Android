package com.lladlam.melox.ui.player

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val LYRIC_FRAME_DELAY_MS = 16L
private const val FOCUS_COLOR_DURATION_MS = 120

@Composable
fun MeloXIOSLyricsPanel(
    state: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val client = remember(context) {
        NeteaseSearchClient(
            cookieProvider = { NeteaseSessionStore.readCookie(context) },
        )
    }
    val listState = rememberLazyListState()
    val mediaId = state.mediaId
    var lyrics by remember(mediaId) { mutableStateOf<LyricsDocument?>(null) }
    var isLoading by remember(mediaId) { mutableStateOf(false) }
    var errorMessage by remember(mediaId) { mutableStateOf<String?>(null) }

    var anchorPositionMs by remember(mediaId) { mutableLongStateOf(state.positionMs) }
    var anchorRealtimeMs by remember(mediaId) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var renderedPositionMs by remember(mediaId) { mutableLongStateOf(state.positionMs) }

    LaunchedEffect(state.positionMs, state.isPlaying, mediaId) {
        anchorPositionMs = state.positionMs
        anchorRealtimeMs = SystemClock.elapsedRealtime()
        renderedPositionMs = state.positionMs
    }

    // Keep the word-by-word clock close to display refresh cadence. The old 50 ms
    // loop visibly stepped through YRC syllables and also made focus changes feel
    // late compared with MeloX/iOS.
    LaunchedEffect(state.isPlaying, mediaId) {
        while (true) {
            renderedPositionMs = if (state.isPlaying) {
                anchorPositionMs + (SystemClock.elapsedRealtime() - anchorRealtimeMs)
            } else {
                anchorPositionMs
            }
            delay(if (state.isPlaying) LYRIC_FRAME_DELAY_MS else 200L)
        }
    }

    LaunchedEffect(mediaId) {
        val songId = mediaId?.toLongOrNull() ?: return@LaunchedEffect
        isLoading = true
        errorMessage = null
        runCatching { client.lyrics(songId) }
            .onSuccess { lyrics = it }
            .onFailure { errorMessage = it.message ?: "歌词加载失败" }
        isLoading = false
    }

    val document = lyrics
    val highlightedIndex = document?.highlightedIndex(renderedPositionMs)

    // The layout height of every lyric row is now stable. This scroll animation no
    // longer fights a simultaneous 21sp -> 25sp remeasure, so focus movement can
    // remain continuous when the highlighted line advances.
    LaunchedEffect(highlightedIndex, mediaId) {
        val index = highlightedIndex ?: return@LaunchedEffect
        val target = (index - 2).coerceAtLeast(0)
        runCatching { listState.animateScrollToItem(target) }
    }

    Box(modifier = modifier) {
        when {
            isLoading && document == null -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White.copy(alpha = 0.9f),
                )
            }

            errorMessage != null && document == null -> {
                Text(
                    text = errorMessage.orEmpty(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    color = Color.White.copy(alpha = 0.52f),
                    fontSize = 15.sp,
                )
            }

            document == null || document.lines.isEmpty() -> {
                Text(
                    text = "暂无歌词",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 18.sp,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(top = 58.dp, bottom = 76.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    itemsIndexed(
                        items = document.lines,
                        key = { index, line -> "${line.timeMs}-$index" },
                    ) { index, line ->
                        MeloXAnimatedLyricLine(
                            line = line,
                            positionMs = renderedPositionMs,
                            active = index == highlightedIndex,
                            distanceFromFocus = highlightedIndex?.let { abs(index - it) } ?: 0,
                            onClick = { state.seekTo(line.timeMs) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MeloXAnimatedLyricLine(
    line: LyricLine,
    positionMs: Long,
    active: Boolean,
    distanceFromFocus: Int,
    onClick: () -> Unit,
) {
    // MeloX keeps focus color and focus geometry as separate transitions. The iOS
    // implementation uses a dedicated 0.12 s color hand-off; geometry settles more
    // gently. Keeping those two timelines separate prevents the old hard snap.
    val focusColorProgress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(
            durationMillis = FOCUS_COLOR_DURATION_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "lyric-focus-color-${line.timeMs}",
    )
    val focusScaleProgress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.88f,
            stiffness = 300f,
            visibilityThreshold = 0.001f,
        ),
        label = "lyric-focus-scale-${line.timeMs}",
    )
    val distanceAlpha by animateFloatAsState(
        targetValue = when (distanceFromFocus) {
            0 -> 1f
            1 -> 0.72f
            2 -> 0.52f
            3 -> 0.38f
            else -> 0.28f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 360f,
            visibilityThreshold = 0.001f,
        ),
        label = "lyric-distance-alpha-${line.timeMs}",
    )
    val focusLift by animateFloatAsState(
        targetValue = if (active) -5f else 0f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 280f,
            visibilityThreshold = 0.05f,
        ),
        label = "lyric-focus-lift-${line.timeMs}",
    )

    // Reserve the promoted/current-line layout size for every item and only scale
    // its rendered layer. This mirrors MeloX's promotedLayoutScale idea and, most
    // importantly, means a focus change never remeasures the LazyColumn.
    val visualScale = 0.84f + 0.16f * focusScaleProgress

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = visualScale
                scaleY = visualScale
                alpha = distanceAlpha
                translationY = focusLift
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        MeloXAlignedLyricText(
            line = line,
            positionMs = positionMs,
            active = active,
            focusProgress = focusColorProgress,
        )

        if (MeloXSettingsRuntime.showLyricTranslation) {
            line.translation
                ?.takeIf(String::isNotBlank)
                ?.let { translation ->
                    Text(
                        text = translation,
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                        textAlign = TextAlign.Start,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        color = Color.White.copy(alpha = lerp(0.28f, 0.68f, focusColorProgress)),
                    )
                }
        }

        if (MeloXSettingsRuntime.showLyricRomanization) {
            line.romanization
                ?.takeIf(String::isNotBlank)
                ?.let { romanization ->
                    Text(
                        text = romanization,
                        modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                        textAlign = TextAlign.Start,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = Color.White.copy(alpha = lerp(0.22f, 0.50f, focusColorProgress)),
                    )
                }
        }
    }
}

@Composable
private fun MeloXAlignedLyricText(
    line: LyricLine,
    positionMs: Long,
    active: Boolean,
    focusProgress: Float,
) {
    val annotated = if (active && line.syllables.isNotEmpty()) {
        buildAnnotatedString {
            for (syllable in line.syllables) {
                val progress = when {
                    positionMs < syllable.startTimeMs -> 0f
                    positionMs >= syllable.endTimeMs -> 1f
                    else -> {
                        val duration = (syllable.endTimeMs - syllable.startTimeMs)
                            .coerceAtLeast(1L)
                        ((positionMs - syllable.startTimeMs).toFloat() / duration.toFloat())
                            .coerceIn(0f, 1f)
                    }
                }
                val karaokeAlpha = 0.30f + 0.70f * progress
                withStyle(
                    SpanStyle(
                        color = Color.White.copy(
                            alpha = lerp(0.34f, karaokeAlpha, focusProgress),
                        ),
                        fontWeight = FontWeight.SemiBold,
                    ),
                ) {
                    append(syllable.text)
                }
            }
        }
    } else {
        buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = Color.White.copy(
                        alpha = lerp(0.34f, 1f, focusProgress),
                    ),
                    fontWeight = FontWeight.SemiBold,
                ),
            ) {
                append(line.text)
            }
        }
    }

    Text(
        text = annotated,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        // Fixed promoted layout metrics: focus is a visual transform, not a layout
        // mutation. This is the key change that removes the line-switch hitch.
        fontSize = 25.sp,
        lineHeight = 32.sp,
    )
}

private fun lerp(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress.coerceIn(0f, 1f)
