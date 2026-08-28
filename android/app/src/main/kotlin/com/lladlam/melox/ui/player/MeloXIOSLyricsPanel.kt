package com.lladlam.melox.ui.player

import android.content.Context
import android.content.Intent
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import java.text.BreakIterator
import java.util.Locale
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import com.lladlam.melox.core.lyrics.LyricHighlightStrategy
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricTimelineProcessor
import com.lladlam.melox.core.lyrics.LyricRomanizationAligner
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.lyrics.withPseudoTiming
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.MeloXAppVisibility
import com.lladlam.melox.ui.settings.MeloXLyricsStyle
import com.lladlam.melox.ui.theme.LocalMeloXFontFamily
import com.lladlam.melox.ui.theme.MeloXLanTingProFontFamily
import com.lladlam.melox.R
import androidx.core.content.res.ResourcesCompat
import com.lladlam.melox.ui.settings.MeloXLyricsRenderingQuality
import com.lladlam.melox.ui.settings.MeloXLyricAnnotationDisplayMode
import com.lladlam.melox.ui.settings.MeloXLyricsGroupingMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Android port of MeloX/AppleMusicLyricsView.swift + LyricPlaybackTimeline.swift
 * + SynchronizedLyricText.swift + LyricGlowTextRenderer.swift.
 *
 * Keep source constants here instead of "tuning by eye".  The rendering backend
 * is Compose rather than SwiftUI, but focus geometry, timing, dim/blur equations,
 * 120 ms colour hand-off, promoted current-line layout and cascade scheduling are
 * follows upstream MeloX. Cascade timing is shortened for Android's frame and
 * blur cost so quick consecutive lines do not leave several row animations in
 * flight at the same time.
 */
private object UpstreamLyrics {
    const val FONT_SIZE_SP = 34f
    const val LINE_HEIGHT_SP = 40.8f // fontSize * 1.2
    const val LINE_SPACING_DP = 28f
    const val CURRENT_LINE_SCALE = 1.02f
    const val FOCUS_POSITION = 0.25f
    const val BLUR_INTENSITY = 0.8f
    const val DIM_AMOUNT = 1f
    const val DISTANCE_BLUR_SCALE = 1.05f
    const val ROMANIZATION_FONT_SCALE = 0.65f
    const val TRANSLATION_FONT_SCALE = 0.65f
    const val ANNOTATION_OPACITY = 0.9f
    const val ANNOTATION_SPACING_DP = 4f
    const val FOCUS_COLOR_DURATION_MS = 120

    const val CASCADE_DELAY_MS = 14f
    const val CASCADE_DELAY_INCREASE_MS = 2.5f
    const val CASCADE_FOLLOWING_DELAY_MS = 22f
    const val CASCADE_CATCH_UP_RATIO = 0.97f
    const val CASCADE_CHASE_SPEED_GRADIENT = 0.82f
    const val CASCADE_DURATION_MS = 560f
    const val CASCADE_SNAP_THRESHOLD_MS = 180f
    const val CASCADE_BOUNCE = 0.14f
    const val CASCADE_BOUNCE_GRADIENT = 0.60f
    const val SCALE_BOUNCE = 0.16f
    const val SCALE_BOUNCE_DURATION_MS = 420

    const val GLOW_INTENSITY = 1f
    const val LONG_TONE_THRESHOLD_MS = 950f
    const val LONG_TONE_MAX_SCALE = 1.0625f
    const val GLOW_TAIL_MS = 550f
    const val HIGHLIGHT_GRADIENT_WIDTH = 0.7f
    const val HIGHLIGHT_GRADIENT_REDUCTION = 0.65f
    const val UNPLAYED_OPACITY = 0.3f
    const val UNPLAYED_BLUR_LEAD_MS = 2400f
    const val MIN_UNPLAYED_BLUR_FRACTION = 0.12f
    const val LIFT_CONTINUATION_MS = 320f
}

internal data class LyricsPanelPlaybackInitialization(
    val positionMs: Long,
    val holdAtTrackStart: Boolean,
    val resetListToStart: Boolean,
)

internal fun lyricsPanelPlaybackInitialization(
    isFirstComposition: Boolean,
    mediaIdChanged: Boolean,
    reportedPositionMs: Long,
): LyricsPanelPlaybackInitialization = if (!isFirstComposition && mediaIdChanged) {
    LyricsPanelPlaybackInitialization(
        positionMs = 0L,
        holdAtTrackStart = true,
        resetListToStart = true,
    )
} else {
    LyricsPanelPlaybackInitialization(
        positionMs = reportedPositionMs,
        holdAtTrackStart = false,
        resetListToStart = false,
    )
}

internal fun initialLyricsHighlightPositionMs(positionMs: Long, advanceMs: Long): Long =
    positionMs + advanceMs

@Composable
fun MeloXIOSLyricsPanel(
    state: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
    isInterfaceHidden: Boolean = false,
    onInterfaceInteraction: () -> Unit = {},
    onInterfaceVisibilityChange: (Boolean) -> Unit = {},
    allowAutomaticSkyline: Boolean = true,
    active: Boolean = true,
) {
    val configuration = LocalConfiguration.current
    if (allowAutomaticSkyline && configuration.screenWidthDp > configuration.screenHeightDp && MeloXSettingsRuntime.skylineEnabled) {
        MeloXSkylineLyricsPanel(state, modifier, onInterfaceInteraction, active)
        return
    }
    when (MeloXSettingsRuntime.lyricsStyle) {
        MeloXLyricsStyle.AppleMusic -> MeloXAppleMusicLyricsPanel(
            state,
            modifier,
            isInterfaceHidden,
            onInterfaceInteraction,
            onInterfaceVisibilityChange,
            active,
        )
        MeloXLyricsStyle.Eva -> MeloXEvaLyricsPanel(state, modifier, onInterfaceInteraction, active)
        MeloXLyricsStyle.TextPV -> MeloXTextPVLyricsPanel(state, modifier, onInterfaceInteraction, active)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MeloXAppleMusicLyricsPanel(
    state: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
    isInterfaceHidden: Boolean = false,
    onInterfaceInteraction: () -> Unit = {},
    onInterfaceVisibilityChange: (Boolean) -> Unit = {},
    active: Boolean = true,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val haptics = LocalHapticFeedback.current
    val activeState = rememberUpdatedState(active)
    // Keep four future lines composed below the viewport. Their independent
    // cascade animations can then run before clipping reveals them.
    val listState = rememberLazyListState(
        cacheWindow = LazyLayoutCacheWindow(
            ahead = 720.dp,
            behind = 240.dp,
        ),
    )
    val density = LocalDensity.current
    val mediaId = state.mediaId

    val automaticLyricSelectionEnabled = MeloXSettingsRuntime.automaticLyricSelectionEnabled
    var lyrics by remember(mediaId, automaticLyricSelectionEnabled) { mutableStateOf<LyricsDocument?>(null) }
    var isLoading by remember(mediaId) { mutableStateOf(false) }
    var errorMessage by remember(mediaId) { mutableStateOf<String?>(null) }
    var shareInitialIndex by remember(mediaId) { mutableStateOf<Int?>(null) }

    var anchorPositionMs by remember(mediaId) { mutableLongStateOf(state.positionMs) }
    var anchorRealtimeMs by remember(mediaId) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val renderedPositionState = remember(mediaId) { mutableLongStateOf(state.positionMs) }
    var holdTrackAtStart by remember(mediaId) { mutableStateOf(false) }
    var initializedMediaId by remember { mutableStateOf(mediaId) }
    var hasInitializedTrack by remember { mutableStateOf(false) }

    // A Bluetooth/headset skip can deliver the first UI state for the new item
    // before its position callback arrives.  Do not let the previous track's
    // progress keep the new lyric document focused in the middle of the list.
    // A newly composed panel, however, is re-entering the current track and must
    // retain its reported position.
    LaunchedEffect(mediaId) {
        val initialization = lyricsPanelPlaybackInitialization(
            isFirstComposition = !hasInitializedTrack,
            mediaIdChanged = hasInitializedTrack && initializedMediaId != mediaId,
            reportedPositionMs = state.positionMs,
        )
        initializedMediaId = mediaId
        hasInitializedTrack = true
        anchorPositionMs = initialization.positionMs
        anchorRealtimeMs = SystemClock.elapsedRealtime()
        renderedPositionState.longValue = initialization.positionMs
        holdTrackAtStart = initialization.holdAtTrackStart
        if (initialization.resetListToStart) listState.scrollToItem(0)
    }

    LaunchedEffect(state.positionMs, state.isPlaying, mediaId) {
        if (holdTrackAtStart) return@LaunchedEffect
        anchorPositionMs = state.positionMs
        anchorRealtimeMs = SystemClock.elapsedRealtime()
        renderedPositionState.longValue = state.positionMs
    }

    LaunchedEffect(mediaId, state.title, state.artist, state.album, state.durationMs, automaticLyricSelectionEnabled) {
        if (mediaId.isNullOrBlank()) return@LaunchedEffect
        isLoading = true
        errorMessage = null
        runCatching { MeloXProviderLyricsLoader.load(appContext, state) }
            .onSuccess { lyrics = it }
            .onFailure { errorMessage = it.message ?: "歌词加载失败" }
        // A newly selected item always enters from its lyric start anchor.
        // Subsequent seeks keep using the normal position callback above.
        holdTrackAtStart = false
        isLoading = false
    }

    val document = lyrics
    val pseudoTimingEnabled = MeloXSettingsRuntime.lyricPseudoTimingEnabled
    val renderedDocument = remember(document, pseudoTimingEnabled) {
        if (pseudoTimingEnabled) document?.withPseudoTiming() else document
    }
    val lines = renderedDocument?.lines.orEmpty()
    val hasSyllableSync = remember(renderedDocument) { lines.any { it.syllables.isNotEmpty() } }
    val bilibiliOffsetMs = rememberBilibiliLyricOffset(mediaId)
    val lyricAdvanceMs = effectiveBilibiliLyricAdvance(MeloXSettingsRuntime.lyricAdvanceMs, bilibiliOffsetMs)
    val isBilibiliLyrics = remember(mediaId) { isBilibiliMediaId(mediaId) }
    val usesWordByWordPresentation = hasSyllableSync && MeloXSettingsRuntime.lyricWordByWordEnabled
    val timedAdvanceMs = if (isBilibiliLyrics || MeloXSettingsRuntime.lyricAdvanceAppliesToWordByWord) {
        lyricAdvanceMs
    } else 0L
    val lineAdvanceMs = if (usesWordByWordPresentation) timedAdvanceMs else lyricAdvanceMs
    var highlightedIndex by remember(document) {
        mutableIntStateOf(
            renderedDocument?.highlightedIndex(
                initialLyricsHighlightPositionMs(renderedPositionState.longValue, lineAdvanceMs),
                LyricHighlightStrategy.FirstSyllableOrLineStart,
            ) ?: -1,
        )
    }
    var colorHighlightedIndex by remember(document) { mutableIntStateOf(highlightedIndex) }
    var activeTimedLineIndexes by remember(document) { mutableStateOf(emptySet<Int>()) }
    val playbackTimeProvider = remember(mediaId, timedAdvanceMs) {
        { renderedPositionState.longValue + timedAdvanceMs }
    }

    // Update the line index only when it actually changes. The per-frame time is
    // read later from Canvas, so the 60 Hz clock invalidates drawing rather than
    // recomposing and relaying out the complete lyric list.
    val renderingQuality = MeloXSettingsRuntime.lyricRenderingQuality
    val refreshRate = when (renderingQuality) {
        MeloXLyricsRenderingQuality.Low -> min(MeloXSettingsRuntime.lyricRefreshRate, 30)
        MeloXLyricsRenderingQuality.Balanced -> min(MeloXSettingsRuntime.lyricRefreshRate, 60)
        // Native glyph drawing is CPU-bound; more than one update per display
        // frame adds work without producing a visible state. 60 Hz remains the
        // ceiling even when a migrated preference contains 90/120 Hz.
        MeloXLyricsRenderingQuality.High -> min(MeloXSettingsRuntime.lyricRefreshRate, 60)
    }
    val interludes = remember(lines) { sourceLyricInterludes(lines) }
    val interludeByLyricIndex = remember(interludes) { interludes.associateBy { it.followingLyricIndex } }
    var activeInterludeIndex by remember(document) { mutableIntStateOf(-1) }

    // High-frequency clock only for Canvas word animation. Line focus changes
    // are computed in a separate event-driven loop so compositional work only
    // happens at lyric boundaries.
    LaunchedEffect(state.isPlaying, mediaId, document, refreshRate, MeloXAppVisibility.isForeground) {
        var lastFrameNanos = 0L
        val minimumFrameNanos = 1_000_000_000L / refreshRate.coerceIn(30, 120)
        while (true) {
            if (!activeState.value || !MeloXAppVisibility.isForeground || holdTrackAtStart) {
                delay(200L)
                continue
            }
            if (state.isPlaying) {
                val frameNanos = withFrameNanos { it }
                if (lastFrameNanos != 0L && frameNanos - lastFrameNanos < minimumFrameNanos) continue
                lastFrameNanos = frameNanos
            }
            val position = if (state.isPlaying) {
                anchorPositionMs + (SystemClock.elapsedRealtime() - anchorRealtimeMs)
            } else {
                anchorPositionMs
            }
            renderedPositionState.longValue = position
            val nextInterlude = interludes.indexOfFirst { position >= it.startTimeMs && position < it.followingLyricTimeMs }
            if (nextInterlude != activeInterludeIndex) activeInterludeIndex = nextInterlude
            if (!state.isPlaying) delay(200L)
        }
    }

    val lineIndexSeekSignal = remember(mediaId) { Channel<Unit>(Channel.CONFLATED) }
    var previousSeekPositionMs by remember(mediaId) { mutableLongStateOf(state.positionMs) }
    var previousSeekRealtimeMs by remember(mediaId) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.positionMs) {
        if (state.positionMs != previousSeekPositionMs) {
            val expected = previousSeekPositionMs + if (state.isPlaying) {
                SystemClock.elapsedRealtime() - previousSeekRealtimeMs
            } else {
                0L
            }
            if (kotlin.math.abs(state.positionMs - expected) > 200L) {
                lineIndexSeekSignal.trySend(Unit)
            }
            previousSeekPositionMs = state.positionMs
            previousSeekRealtimeMs = SystemClock.elapsedRealtime()
        }
    }

    LaunchedEffect(state.isPlaying, mediaId, document, hasSyllableSync, lineAdvanceMs, MeloXAppVisibility.isForeground) {
        while (true) {
            if (!activeState.value || !MeloXAppVisibility.isForeground || holdTrackAtStart || renderedDocument == null) {
                delay(200L)
                continue
            }
            val position = if (state.isPlaying) {
                anchorPositionMs + (SystemClock.elapsedRealtime() - anchorRealtimeMs)
            } else {
                anchorPositionMs
            }
            val effectivePosition = position + lineAdvanceMs
            val activeInterlude = if (
                MeloXSettingsRuntime.lyricInterludeCountdownEnabled
            ) interludes.getOrNull(activeInterludeIndex) else null
            val nextIndex = if (
                MeloXSettingsRuntime.lyricInterludeCountdownEnabled &&
                activeInterlude != null &&
                position >= activeInterlude.startTimeMs &&
                position < activeInterlude.followingLyricTimeMs
            ) {
                activeInterlude.followingLyricIndex
            } else {
                renderedDocument.highlightedIndex(effectivePosition, LyricHighlightStrategy.FirstSyllableOrLineStart) ?: -1
            }
            if (nextIndex != highlightedIndex) highlightedIndex = nextIndex
            val nextColorIndex = renderedDocument.highlightedIndex(
                effectivePosition + MeloXSettingsRuntime.lyricFocusColorLeadMs,
                LyricHighlightStrategy.FirstSyllableOrLineStart,
            ) ?: -1
            if (nextColorIndex != colorHighlightedIndex) colorHighlightedIndex = nextColorIndex
            val timedPosition = position + timedAdvanceMs
            val nextTimedLines = LyricTimelineProcessor.activeTimedLineIndexes(renderedDocument, timedPosition)
            if (nextTimedLines != activeTimedLineIndexes) activeTimedLineIndexes = nextTimedLines

            val lineWait = LyricTimelineProcessor.nextEventTimeMs(renderedDocument, effectivePosition)
                ?.minus(effectivePosition)
            val timedWait = LyricTimelineProcessor.nextEventTimeMs(renderedDocument, timedPosition)
                ?.minus(timedPosition)
            val waitMs = listOfNotNull(lineWait, timedWait).minOrNull()?.coerceIn(16L, 500L) ?: 250L
            withTimeoutOrNull(waitMs) { lineIndexSeekSignal.receive() }
            if (!state.isPlaying) delay(200L)
        }
    }

    val focusProgress = remember(document) {
        List(lines.size) { Animatable(0f) }
    }
    val scaleProgress = remember(document) {
        List(lines.size) { Animatable(0f) }
    }
    val cascadeLineProgress = remember(document) {
        List(lines.size) { Animatable(1f) }
    }
    val cascadeScrollProgress = remember(document) { Animatable(1f) }
    var cascadeDistancePx by remember(document) { mutableStateOf(0f) }
    var cascadeInitialOffsets by remember(document) {
        mutableStateOf<Map<Int, Float>>(emptyMap())
    }
    var cascadeDestinationOffsets by remember(document) {
        mutableStateOf<Map<Int, Float>>(emptyMap())
    }
    val rowHeightsPx = remember(
        document,
        MeloXSettingsRuntime.lyricFontScale,
        MeloXSettingsRuntime.lyricSpacingScale,
        MeloXSettingsRuntime.showLyricTranslation,
        MeloXSettingsRuntime.showLyricRomanization,
    ) { mutableStateMapOf<Int, Int>() }
    var viewportHeightPx by remember(document) { mutableIntStateOf(0) }
    var visualFocusIndex by remember(document) { mutableIntStateOf(-1) }
    var isBrowsingLyrics by remember(document) { mutableStateOf(false) }
    var playbackFocusGeneration by remember(document) { mutableIntStateOf(0) }
    var browseGeneration by remember(document) { mutableIntStateOf(0) }
    var scrollHideDistancePx by remember(document) { mutableStateOf(0f) }
    val latestInterfaceHidden = rememberUpdatedState(isInterfaceHidden)
    val latestVisibilityCallback = rememberUpdatedState(onInterfaceVisibilityChange)
    val latestInteractionCallback = rememberUpdatedState(onInterfaceInteraction)
    var lastCanScrollForward by remember(document) { mutableStateOf(true) }
    var lastCanScrollBackward by remember(document) { mutableStateOf(true) }
    var initialLyricsPositioned by remember(renderedDocument) { mutableStateOf(lines.isEmpty()) }
    LaunchedEffect(document) {
        snapshotFlow { listState.canScrollForward to listState.canScrollBackward }
            .collect { (canForward, canBackward) ->
                if (com.lladlam.melox.ui.settings.MeloXSettingsRuntime.hapticFeedbackEnabled) {
                    if (lastCanScrollForward && !canForward) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    if (lastCanScrollBackward && !canBackward) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
                lastCanScrollForward = canForward
                lastCanScrollBackward = canBackward
            }
    }

    val lyricFontScale = MeloXSettingsRuntime.lyricFontScale
    val lyricSpacingScale = MeloXSettingsRuntime.lyricSpacingScale
    val lineSpacingPx = with(density) { (UpstreamLyrics.LINE_SPACING_DP * lyricSpacingScale).dp.toPx() }
    val primaryHeightPx = with(density) { (UpstreamLyrics.LINE_HEIGHT_SP * lyricFontScale).sp.toPx() }
    val annotationFontPx = with(density) {
        max(UpstreamLyrics.FONT_SIZE_SP * lyricFontScale * MeloXSettingsRuntime.lyricRomanizationFontScale, 13f).sp.toPx()
    }
    val annotationSpacingPx = with(density) { UpstreamLyrics.ANNOTATION_SPACING_DP.dp.toPx() }

    fun estimatedHeight(index: Int): Float {
        rowHeightsPx[index]?.let { return it.toFloat() }
        val line = lines.getOrNull(index)
        var height = primaryHeightPx
        if (MeloXSettingsRuntime.showLyricRomanization && !line?.romanization.isNullOrBlank()) {
            height += annotationFontPx * 1.2f + annotationSpacingPx
        }
        if (MeloXSettingsRuntime.showLyricTranslation && !line?.translation.isNullOrBlank()) {
            height += annotationFontPx * 1.2f + annotationSpacingPx
        }
        return height
    }

    fun settledMovementOffset(index: Int, focusIndex: Int): Float {
        if (focusIndex !in lines.indices || index <= focusIndex) return 0f
        return max(
            estimatedHeight(focusIndex) * (MeloXSettingsRuntime.lyricFocusScale - 1f),
            0f,
        )
    }

    fun currentMovementOffset(index: Int): Float {
        val initial = cascadeInitialOffsets[index]
            ?: return settledMovementOffset(index, visualFocusIndex)
        val destination = cascadeDestinationOffsets[index] ?: 0f
        val scrollProgress = cascadeScrollProgress.value
        val lineProgress = cascadeLineProgress[index].value
        return initial + cascadeDistancePx * scrollProgress -
            (cascadeDistancePx + initial - destination) * lineProgress
    }

    suspend fun clearCascadePresentation(focusIndex: Int) {
        visualFocusIndex = focusIndex
        cascadeInitialOffsets = emptyMap()
        cascadeDestinationOffsets = emptyMap()
        cascadeDistancePx = 0f
        cascadeScrollProgress.snapTo(1f)
    }

    suspend fun handOffFocusColor(targetIndexes: Set<Int>) = coroutineScope {
        focusProgress.forEachIndexed { index, anim ->
            val target = if (index in targetIndexes) 1f else 0f
            if (abs(anim.value - target) > 0.0001f) {
                launch {
                    if (MeloXSettingsRuntime.lyricReduceMotion) anim.snapTo(target)
                    else anim.animateTo(
                        targetValue = target,
                        animationSpec = tween(
                            durationMillis = UpstreamLyrics.FOCUS_COLOR_DURATION_MS,
                            easing = SourceSmoothStepEasing,
                        ),
                    )
                }
            }
        }
    }

    suspend fun handOffFocusScale(previousIndex: Int, nextIndex: Int) = coroutineScope {
        if (MeloXSettingsRuntime.lyricReduceMotion) {
            scaleProgress.forEachIndexed { index, anim -> anim.snapTo(if (index == nextIndex) 1f else 0f) }
            return@coroutineScope
        }
        if (previousIndex in scaleProgress.indices && previousIndex != nextIndex) {
            launch {
                scaleProgress[previousIndex].animateTo(
                    0f,
                    tween(
                        durationMillis = MeloXSettingsRuntime.lyricScaleBounceDurationMs,
                            easing = if (MeloXSettingsRuntime.lyricScaleBounceEnabled) {
                                SourceSpringEasing(MeloXSettingsRuntime.lyricScaleBounce)
                            } else SourceSmoothStepEasing,
                    ),
                )
            }
        }
        if (nextIndex in scaleProgress.indices) {
            launch {
                scaleProgress[nextIndex].animateTo(
                    1f,
                    tween(
                        durationMillis = MeloXSettingsRuntime.lyricScaleBounceDurationMs,
                        easing = if (MeloXSettingsRuntime.lyricScaleBounceEnabled) {
                            SourceSpringEasing(MeloXSettingsRuntime.lyricScaleBounce)
                        } else SourceSmoothStepEasing,
                    ),
                )
            }
        }
    }

    LaunchedEffect(colorHighlightedIndex, activeTimedLineIndexes, activeInterludeIndex, document) {
        if (activeInterludeIndex >= 0) {
            handOffFocusColor(emptySet())
        } else {
            val targets = activeTimedLineIndexes.ifEmpty {
                colorHighlightedIndex.takeIf(lines.indices::contains)?.let(::setOf).orEmpty()
            }
            handOffFocusColor(targets)
        }
    }

    val scrollHideThresholdPx = with(density) { MeloXSettingsRuntime.lyricScrollHideThresholdDp.dp.toPx() }
    val lyricInteractionConnection = remember(document, scrollHideThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val offsetDelta = -available.y
                if (kotlin.math.abs(offsetDelta) < 0.01f) return Offset.Zero

                isBrowsingLyrics = true
                browseGeneration += 1

                if (offsetDelta < 0f) {
                    scrollHideDistancePx = 0f
                    if (latestInterfaceHidden.value) {
                        latestVisibilityCallback.value.invoke(true)
                    } else {
                        latestInteractionCallback.value.invoke()
                    }
                } else if (!latestInterfaceHidden.value) {
                    latestInteractionCallback.value.invoke()
                    scrollHideDistancePx += offsetDelta
                    if (scrollHideDistancePx >= scrollHideThresholdPx) {
                        scrollHideDistancePx = 0f
                        latestVisibilityCallback.value.invoke(false)
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(browseGeneration, document) {
        if (browseGeneration <= 0) return@LaunchedEffect
        delay(MeloXSettingsRuntime.lyricFollowDelayMs.toLong())
        isBrowsingLyrics = false
        playbackFocusGeneration += 1
    }

    LaunchedEffect(isBrowsingLyrics, document) {
        if (isBrowsingLyrics) clearCascadePresentation(visualFocusIndex)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeightPx = it.height },
    ) {
        val focusPosition = MeloXSettingsRuntime.lyricFocusPosition
        val topPaddingPx = viewportHeightPx * focusPosition
        val bottomPaddingPx = max(
            viewportHeightPx * (1f - focusPosition),
            with(density) { 40.dp.toPx() },
        )

        fun focusItemScrollOffset(index: Int): Int {
            if (index !in lines.indices || viewportHeightPx <= 0) return 0
            val viewportAnchor = viewportHeightPx * focusPosition
            val desiredItemTop = viewportAnchor - estimatedHeight(index) * focusPosition
            return -desiredItemTop.roundToInt()
        }

        val playbackFocusIndex = activeTimedLineIndexes.minOrNull() ?: highlightedIndex

        LaunchedEffect(
            playbackFocusIndex,
            playbackFocusGeneration,
            viewportHeightPx,
            isBrowsingLyrics,
            document,
        ) {
            val sourceIndex = highlightedIndex
            val nextIndex = playbackFocusIndex
            if (viewportHeightPx <= 0 || isBrowsingLyrics) {
                if (isBrowsingLyrics && sourceIndex in lines.indices) {
                    visualFocusIndex = sourceIndex
                }
                return@LaunchedEffect
            }

            if (nextIndex !in lines.indices) {
                listState.scrollToItem(0)
                initialLyricsPositioned = true
                return@LaunchedEffect
            }

            val previousIndex = visualFocusIndex
            val targetOffset = focusItemScrollOffset(nextIndex)

            if (previousIndex !in lines.indices) {
                listState.scrollToItem(nextIndex + 1, targetOffset)
                focusProgress.forEachIndexed { index, anim ->
                    val targets = activeTimedLineIndexes.ifEmpty {
                        colorHighlightedIndex.takeIf(lines.indices::contains)?.let(::setOf).orEmpty()
                    }
                    anim.snapTo(if (index in targets) 1f else 0f)
                }
                scaleProgress.forEachIndexed { index, anim ->
                    anim.snapTo(if (index == nextIndex) 1f else 0f)
                }
                clearCascadePresentation(nextIndex)
                initialLyricsPositioned = true
                return@LaunchedEffect
            }

            if (previousIndex == nextIndex) return@LaunchedEffect

            if (!MeloXSettingsRuntime.lyricAutoFollowEnabled) {
                handOffFocusScale(previousIndex, nextIndex)
                visualFocusIndex = nextIndex
                return@LaunchedEffect
            }

            if (MeloXSettingsRuntime.lyricReduceMotion) {
                clearCascadePresentation(nextIndex)
                handOffFocusScale(previousIndex, nextIndex)
                listState.scrollToItem(nextIndex + 1, targetOffset)
                return@LaunchedEffect
            }

            val baseDurationMs = sourceFocusAnimationDurationMs(nextIndex, lines)
            val isAdjacentForward = nextIndex == previousIndex + 1
            val effectivePosition = renderedPositionState.longValue + lyricAdvanceMs
            val remainingMs = sourceRemainingFocusDurationMs(nextIndex, effectivePosition, lines)

            val fullCascadeMs = max(baseDurationMs.toFloat(), MeloXSettingsRuntime.lyricCascadeDurationMs)
            val availableMs = remainingMs?.coerceAtLeast(0f)
            val cascadeDurationMs = if (availableMs == null) {
                fullCascadeMs
            } else {
                if (availableMs < MeloXSettingsRuntime.lyricSnapThresholdMs) 0f
                else min(fullCascadeMs, availableMs)
            }

            val desiredTop = -targetOffset.toFloat()
            val targetItem = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == nextIndex + 1 }
            if (!isAdjacentForward || cascadeDurationMs <= 0f || targetItem == null) {
                clearCascadePresentation(nextIndex)
                coroutineScope {
                    launch { handOffFocusScale(previousIndex, nextIndex) }
                    launch {
                        if (cascadeDurationMs <= 0f) {
                            listState.scrollToItem(nextIndex + 1, targetOffset)
                        } else {
                            listState.animateScrollToItem(nextIndex + 1, targetOffset)
                        }
                    }
                }
                return@LaunchedEffect
            }

            val movementDistance = targetItem.offset - desiredTop
            if (abs(movementDistance) <= 0.5f) {
                clearCascadePresentation(nextIndex)
                handOffFocusScale(previousIndex, nextIndex)
                return@LaunchedEffect
            }

            val visibleLineIndexes = listState.layoutInfo.visibleItemsInfo
                .mapNotNull { (it.index - 1).takeIf(lines.indices::contains) }
            val firstVisible = visibleLineIndexes.minOrNull() ?: max(nextIndex - 1, 0)
            val lastVisible = visibleLineIndexes.maxOrNull() ?: nextIndex
            val firstMoving = min(firstVisible, max(nextIndex - 1, 0))
            // The cache window keeps these rows composed even though the
            // viewport clips them. Each retains its own delayed spring.
            val lastMoving = min(lastVisible + 4, lines.lastIndex)
            val movingIndexes = firstMoving..lastMoving
            val carriedOffsets = movingIndexes.associateWith(::currentMovementOffset)
            val destinations = movingIndexes.associateWith { settledMovementOffset(it, nextIndex) }

            cascadeDistancePx = movementDistance
            cascadeInitialOffsets = carriedOffsets
            cascadeDestinationOffsets = destinations
            cascadeScrollProgress.snapTo(0f)
            movingIndexes.forEach { cascadeLineProgress[it].snapTo(0f) }
            visualFocusIndex = nextIndex

            val firstChasing = max(nextIndex - 1, 0)
            val maximumChaseOrder = max(lastMoving - firstChasing, 0)
            val lineTimings = sourceCascadeLineTimings(
                maximumLineOrder = maximumChaseOrder,
                animationDurationMs = cascadeDurationMs,
            )
            val slowestDuration = lineTimings.first().durationMs

            coroutineScope {
                launch { handOffFocusScale(previousIndex, nextIndex) }
                launch {
                    var previousScrollProgress = 0f
                    listState.scroll {
                        cascadeScrollProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = cascadeDurationMs.roundToInt(),
                                easing = SourceSmoothStepEasing,
                            ),
                        ) {
                            val delta = (value - previousScrollProgress) * movementDistance
                            scrollBy(delta)
                            previousScrollProgress = value
                        }
                    }
                }

                movingIndexes.forEach { index ->
                    val movementOrder = max(index - nextIndex, 0)
                    val chaseOrder = (index - firstChasing).coerceAtLeast(0)
                    val movementTiming = lineTimings[min(movementOrder, lineTimings.lastIndex)]
                    val chaseTiming = lineTimings[min(chaseOrder, lineTimings.lastIndex)]
                    val duration = slowestDuration +
                        (chaseTiming.durationMs - slowestDuration) *
                        MeloXSettingsRuntime.lyricCascadeChaseSpeedGradient
                    val bounce = sourceCascadeBounce(chaseOrder, maximumChaseOrder)
                    launch {
                        if (movementTiming.delayMs > 0f) delay(movementTiming.delayMs.toLong())
                        cascadeLineProgress[index].animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = max(duration, 1f).roundToInt(),
                                easing = SourceSpringEasing(bounce),
                            ),
                        )
                    }
                }

            }
            clearCascadePresentation(nextIndex)
        }

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
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = Color.White.copy(alpha = 0.52f),
                    fontSize = 15.sp,
                )
            }
            document != null && lines.isEmpty() -> {
                Text(
                    text = "暂无歌词",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 18.sp,
                )
            }
            document == null -> {
                Text(
                    text = "暂无歌词",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 18.sp,
                )
            }
            else -> {
                val focusAnchorY = viewportHeightPx * focusPosition
                val annotationHeightPx = annotationFontPx * 1.2f * 2f + annotationSpacingPx * 2f
                val lyricStridePx = max(primaryHeightPx + annotationHeightPx + lineSpacingPx, 1f)
                val layoutOverscanPx = (lyricStridePx * 4f).roundToInt()
                // Observing layoutInfo here during automatic playback scrolls
                // invalidated and recomposed every visible lyric item per
                // frame. Exact coordinates are only required while the user is
                // browsing; playback mode has a stable source-derived stride.
                val browsingVisibleItemsByIndex by remember(listState) {
                    derivedStateOf { listState.layoutInfo.visibleItemsInfo.associateBy { it.index } }
                }
                val visibleItemsByIndex = browsingVisibleItemsByIndex

                Layout(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .graphicsLayer { alpha = if (initialLyricsPositioned) 1f else 0f },
                    content = {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().nestedScroll(lyricInteractionConnection),
                        ) {
                    item(key = "lyrics-top-padding") {
                        Spacer(Modifier.height(with(density) { topPaddingPx.toDp() }))
                    }
                    itemsIndexed(
                        items = lines,
                        key = { index, line -> "${line.timeMs}:$index" },
                    ) { index, line ->
                        val interlude = interludeByLyricIndex[index]
                        // Reserve the interlude's space as soon as the document
                        // is laid out.  Inserting it only after playback reaches
                        // the gap makes the countdown pop in and fight the lyric
                        // scroll animation.
                        val showsInterlude = MeloXSettingsRuntime.lyricInterludeCountdownEnabled &&
                            interlude != null
                        val interludeHeightPx = if (showsInterlude) with(density) { 56.dp.toPx() } else 0f
                        val height = estimatedHeight(index) + interludeHeightPx
                        val visualOffset = currentMovementOffset(index)
                        val frameMinY = visibleItemsByIndex[index + 1]?.offset?.toFloat()
                            ?: focusAnchorY + (index - visualFocusIndex) * lyricStridePx
                        val visualMidY = frameMinY + visualOffset + height * 0.5f
                        val distance = if (isBrowsingLyrics || visualFocusIndex !in lines.indices) {
                            abs(visualMidY - focusAnchorY)
                        } else {
                            abs(index - visualFocusIndex) * lyricStridePx
                        }
                        val isActiveLine = index in activeTimedLineIndexes
                        val fp = focusProgress[index].value.coerceIn(0f, 1f)
                        // Some LRC/YRC files deliberately provide two distinct
                        // lines at virtually the same time.  Keep both readable
                        // instead of promoting only the last binary-search hit.
                        val effectiveFocus = fp
                        val distanceBlur = sourceDistanceBlurRadius(
                            distancePx = distance,
                            lyricStridePx = lyricStridePx,
                            intensity = UpstreamLyrics.BLUR_INTENSITY *
                                (if (isInterfaceHidden) MeloXSettingsRuntime.lyricHiddenInterfaceBlurScale else MeloXSettingsRuntime.lyricDistanceBlurScale) *
                                MeloXSettingsRuntime.lyricBlurStrength,
                            focusProgress = effectiveFocus,
                        )
                        val preceding = index == visualFocusIndex - 1
                        val following = index == visualFocusIndex + 1
                        val focusBlur = sourceFocusBlurRadius(
                            UpstreamLyrics.BLUR_INTENSITY * MeloXSettingsRuntime.lyricBlurStrength,
                            preceding,
                            following,
                        ) * (1f - effectiveFocus)
                        val distanceOpacity = sourceDistanceOpacity(
                            distance,
                            lyricStridePx,
                            MeloXSettingsRuntime.lyricDimAmount,
                            effectiveFocus,
                        )
                        val emphasis = sourceEmphasis(effectiveFocus, MeloXSettingsRuntime.lyricDimAmount)
                        val rowAlpha = (distanceOpacity * emphasis).coerceIn(0f, 1f)
                        val followingLineAlpha = sourceDistanceOpacity(
                            lyricStridePx,
                            lyricStridePx,
                            MeloXSettingsRuntime.lyricDimAmount,
                            0f,
                        ) * sourceEmphasis(0f, MeloXSettingsRuntime.lyricDimAmount)
                        val timedUnplayedAlpha = (followingLineAlpha / rowAlpha.coerceAtLeast(.001f))
                            .coerceIn(0f, 1f)
                        val scale = 1f + (MeloXSettingsRuntime.lyricFocusScale - 1f) *
                            max(scaleProgress[index].value, fp)

                        if (showsInterlude) {
                            MeloXLyricInterludeCountdown(
                                interlude = checkNotNull(interlude),
                                playbackTimeProvider = { renderedPositionState.longValue },
                                reduceMotion = MeloXSettingsRuntime.lyricReduceMotion,
                                visualScale = scale,
                                visualOffsetPx = visualOffset,
                                rowAlpha = rowAlpha,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }

                        MeloXUpstreamLyricLine(
                            line = line,
                            playbackTimeProvider = playbackTimeProvider,
                            // Only the focused line (plus its short colour hand-off)
                            // needs the playback clock. Subscribing every visible
                            // syllable row made the complete LazyColumn redraw at
                            // the lyric refresh rate on low-end devices.
                            supportsTimedLyrics = line.syllables.isNotEmpty() &&
                                MeloXSettingsRuntime.lyricWordByWordEnabled &&
                                (effectiveFocus > 0.001f || isActiveLine),
                            fontScale = lyricFontScale,
                            reduceMotion = MeloXSettingsRuntime.lyricReduceMotion,
                            focusProgress = effectiveFocus,
                            timingEffectsStrength = if (isActiveLine) 1f else effectiveFocus,
                            timedUnplayedAlpha = timedUnplayedAlpha,
                            visualScale = scale,
                            visualOffsetPx = visualOffset,
                            rowAlpha = rowAlpha,
                            distanceBlurDp = distanceBlur,
                            focusBlurDp = focusBlur,
                            renderingQuality = renderingQuality,
                            showTranslation = MeloXSettingsRuntime.showLyricTranslation &&
                                !line.translation.isNullOrBlank(),
                            showRomanization = MeloXSettingsRuntime.showLyricRomanization &&
                                !line.romanization.isNullOrBlank() &&
                                (MeloXSettingsRuntime.lyricRomanizationDisplayMode == MeloXLyricAnnotationDisplayMode.AllLines || isActiveLine),
                            reserveTranslation = MeloXSettingsRuntime.showLyricTranslation && !line.translation.isNullOrBlank(),
                            reserveRomanization = MeloXSettingsRuntime.showLyricRomanization && !line.romanization.isNullOrBlank(),
                            onMeasured = { measured ->
                                if (measured > 0 && rowHeightsPx[index] != measured) rowHeightsPx[index] = measured
                            },
                            tapSeekEnabled = MeloXSettingsRuntime.lyricTapSeekEnabled,
                            onClick = { onInterfaceInteraction(); state.seekTo(line.timeMs) },
                            longPressShareEnabled = MeloXSettingsRuntime.lyricLongPressShareEnabled,
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onInterfaceInteraction()
                                shareInitialIndex = index
                            },
                        )

                        if (index != lines.lastIndex) {
                            Spacer(Modifier.height((UpstreamLyrics.LINE_SPACING_DP * lyricSpacingScale).dp))
                        }
                    }
                    item(key = "lyrics-bottom-padding") {
                        Spacer(Modifier.height(with(density) { (bottomPaddingPx + layoutOverscanPx).toDp() }))
                    }
                        }
                    },
                ) { measurables, constraints ->
                    val expandedHeight = (constraints.maxHeight.toLong() + layoutOverscanPx)
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt()
                    val list = measurables.single().measure(
                        constraints.copy(minHeight = expandedHeight, maxHeight = expandedHeight),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        list.placeRelative(0, 0)
                    }
                }
            }
        }

        // MeloX iOS reserves only the former bottom-controls region as the
        // hidden-controls tap target. Covering the complete viewport here made
        // every lyric stop receiving scroll input as soon as transport chrome
        // retracted, so the lines the user was browsing appeared to vanish or
        // become unreachable.
        if (isInterfaceHidden) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(MeloXNowPlayingControlsHeight.dp)
                    .zIndex(100f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onInterfaceInteraction,
                    ),
            )
        }
    }
    shareInitialIndex?.let { initial ->
        MeloXLyricShareDialog(
            state = state,
            lines = lines,
            initialIndex = initial,
            onDismiss = { shareInitialIndex = null },
        )
    }
}

/** Three staggered instrumental bars, matching the report's 750ms wave entry. */
@Composable
private fun MeloXLyricInstrumentalWave(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "instrumental-wave")
    val phase0 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = CubicBezierEasing(.4f, 0f, 1f, 1f)),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "instrumental-wave-0",
    )
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, delayMillis = 50, easing = CubicBezierEasing(.4f, 0f, 1f, 1f)),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "instrumental-wave-1",
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, delayMillis = 100, easing = CubicBezierEasing(.4f, 0f, 1f, 1f)),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "instrumental-wave-2",
    )
    val phases = listOf(phase0, phase1, phase2)
    Canvas(modifier.fillMaxWidth().height(46.dp)) {
        val barWidth = 7.dp.toPx()
        val gap = 9.dp.toPx()
        val totalWidth = barWidth * 3f + gap * 2f
        val startX = (size.width - totalWidth) / 2f
        phases.forEachIndexed { index, phase ->
            val barHeight = 20.dp.toPx() + index * 7.dp.toPx()
            drawRoundRect(
                color = Color.White.copy(alpha = phase.coerceIn(0f, 1f)),
                topLeft = Offset(
                    startX + index * (barWidth + gap),
                    (size.height - barHeight) / 2f,
                ),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
private fun MeloXUpstreamLyricLine(
    line: LyricLine,
    playbackTimeProvider: () -> Long,
    supportsTimedLyrics: Boolean,
    fontScale: Float,
    reduceMotion: Boolean,
    focusProgress: Float,
    timingEffectsStrength: Float,
    timedUnplayedAlpha: Float,
    visualScale: Float,
    visualOffsetPx: Float,
    rowAlpha: Float,
    distanceBlurDp: Float,
    focusBlurDp: Float,
    renderingQuality: MeloXLyricsRenderingQuality,
    showTranslation: Boolean,
    showRomanization: Boolean,
    reserveTranslation: Boolean,
    reserveRomanization: Boolean,
    tapSeekEnabled: Boolean,
    longPressShareEnabled: Boolean,
    onMeasured: (Int) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val flipped = line.agent?.alignment == com.lladlam.melox.core.lyrics.LyricAgentAlignment.Flipped
    val lineAlignment = if (flipped) Alignment.End else Alignment.Start
    val lineTextAlign = if (flipped) TextAlign.End else TextAlign.Start
    val requestedBlur = max(max(distanceBlurDp, focusBlurDp), 0f)
    val effectiveBlur = when (renderingQuality) {
        MeloXLyricsRenderingQuality.Low -> 0f
        MeloXLyricsRenderingQuality.Balanced -> requestedBlur * .55f
        MeloXLyricsRenderingQuality.High -> requestedBlur
    }
    val accompanimentBefore = line.accompaniment.filter { it.timeMs < line.timeMs }
    val accompanimentAfter = line.accompaniment.filterNot { it.timeMs < line.timeMs }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onMeasured(it.height) }
            .graphicsLayer {
                translationY = visualOffsetPx
                scaleX = visualScale
                scaleY = visualScale
                alpha = rowAlpha
                transformOrigin = TransformOrigin(if (flipped) 1f else 0f, 0f)
            }
            .combinedClickable(
                enabled = tapSeekEnabled || longPressShareEnabled,
                onClick = { if (tapSeekEnabled) onClick() },
                onLongClick = { if (longPressShareEnabled) onLongClick() },
            )
            .padding(horizontal = 8.dp),
        horizontalAlignment = lineAlignment,
    ) {
        accompanimentBefore.forEach { vocal ->
            MeloXTimedAccompaniment(vocal, playbackTimeProvider, fontScale, reduceMotion, focusProgress, renderingQuality, effectiveBlur)
        }
        if (showRomanization) {
            MeloXRubyLyricText(
                line = line,
                playbackTimeProvider = playbackTimeProvider,
                // Only the current/transitioning line subscribes to the frame
                // clock. Static ruby rows otherwise invalidated every unit at
                // 60 Hz even though their visual result did not change.
                supportsTimedLyrics = supportsTimedLyrics,
                fontScale = fontScale,
                renderingQuality = renderingQuality,
                softBlurDp = effectiveBlur,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            MeloXGlyphLyricText(
                line = line,
                playbackTimeProvider = playbackTimeProvider,
                supportsTimedLyrics = supportsTimedLyrics,
                fontScale = fontScale,
                // MeloX iOS sends pseudo-timed LRC through this same
                // renderer. Only genuine long tones expand; ordinary glyphs
                // merely reveal and lift according to the selected mode.
                reduceMotion = reduceMotion,
                timingEffectsStrength = timingEffectsStrength,
                unplayedAlpha = timedUnplayedAlpha,
                renderingQuality = renderingQuality,
                softBlurDp = effectiveBlur,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        accompanimentAfter.forEach { vocal ->
            MeloXTimedAccompaniment(vocal, playbackTimeProvider, fontScale, reduceMotion, focusProgress, renderingQuality, effectiveBlur)
        }

        val romanSize = max(UpstreamLyrics.FONT_SIZE_SP * fontScale * MeloXSettingsRuntime.lyricRomanizationFontScale, 13f)
        if (!showRomanization && reserveRomanization) {
            val romanHeight = with(LocalDensity.current) { (romanSize * 1.2f).sp.toDp() }
            Spacer(Modifier.height(romanHeight + UpstreamLyrics.ANNOTATION_SPACING_DP.dp))
        }

        val translationSize = max(UpstreamLyrics.FONT_SIZE_SP * fontScale * MeloXSettingsRuntime.lyricTranslationFontScale, 13f)
        if (showTranslation) {
            Text(
                text = line.translation.orEmpty(),
                modifier = Modifier.fillMaxWidth().padding(top = UpstreamLyrics.ANNOTATION_SPACING_DP.dp),
                color = Color.White.copy(alpha = MeloXSettingsRuntime.lyricTranslationOpacity),
                textAlign = lineTextAlign,
                fontSize = translationSize.sp,
                lineHeight = translationSize.sp * 1.2f,
                fontWeight = MeloXSettingsRuntime.lyricFontWeight.composeWeight,
                style = TextStyle(
                    shadow = if (effectiveBlur > .05f) {
                        Shadow(
                            color = Color.White.copy(alpha = .48f),
                            offset = Offset.Zero,
                            blurRadius = effectiveBlur,
                        )
                    } else null,
                ),
            )
        } else if (reserveTranslation) {
            val translationHeight = with(LocalDensity.current) { (translationSize * 1.2f).sp.toDp() }
            Spacer(Modifier.height(translationHeight + UpstreamLyrics.ANNOTATION_SPACING_DP.dp))
        }
    }
}

@Composable
private fun MeloXTimedAccompaniment(
    vocal: com.lladlam.melox.core.lyrics.LyricAccompaniment,
    playbackTimeProvider: () -> Long,
    fontScale: Float,
    reduceMotion: Boolean,
    focusProgress: Float,
    renderingQuality: MeloXLyricsRenderingQuality,
    softBlurDp: Float,
) {
    val end = vocal.durationMs?.let { vocal.timeMs + it }
        ?: vocal.syllables.maxOfOrNull { it.endTimeMs }
        ?: vocal.timeMs
    val visible = playbackTimeProvider() in (vocal.timeMs - 600L)..(end + 600L)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 2 } + expandVertically(tween(600)),
        exit = fadeOut(tween(600)) + slideOutVertically(tween(600)) { it / 2 } + shrinkVertically(tween(600)),
    ) {
        val vocalLine = LyricLine(vocal.timeMs, vocal.durationMs, vocal.text, vocal.syllables, agent = vocal.agent)
        MeloXGlyphLyricText(
            line = vocalLine,
            playbackTimeProvider = playbackTimeProvider,
            supportsTimedLyrics = vocal.syllables.isNotEmpty(),
            fontScale = fontScale * .68f,
            reduceMotion = reduceMotion,
            timingEffectsStrength = focusProgress,
            renderingQuality = renderingQuality,
            softBlurDp = softBlurDp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).graphicsLayer { alpha = .72f },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeloXRubyLyricText(
    line: LyricLine,
    playbackTimeProvider: () -> Long,
    supportsTimedLyrics: Boolean,
    fontScale: Float,
    renderingQuality: MeloXLyricsRenderingQuality,
    softBlurDp: Float = 0f,
    modifier: Modifier = Modifier,
) {
    if (MeloXSettingsRuntime.lyricWordByWordEnabled) {
        Column(modifier = modifier) {
            MeloXGlyphLyricText(
                line = line,
                playbackTimeProvider = playbackTimeProvider,
                supportsTimedLyrics = supportsTimedLyrics,
                fontScale = fontScale,
                reduceMotion = false,
                timingEffectsStrength = 1f,
                renderingQuality = renderingQuality,
                softBlurDp = softBlurDp,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!line.romanization.isNullOrBlank()) {
                Text(
                    text = line.romanization.orEmpty(),
                    color = Color.White.copy(alpha = MeloXSettingsRuntime.lyricRomanizationOpacity),
                    modifier = Modifier.fillMaxWidth().padding(top = UpstreamLyrics.ANNOTATION_SPACING_DP.dp),
                    fontSize = max(
                        UpstreamLyrics.FONT_SIZE_SP * fontScale * MeloXSettingsRuntime.lyricRomanizationFontScale,
                        13f,
                    ).sp,
                    lineHeight = (
                        max(
                            UpstreamLyrics.FONT_SIZE_SP * fontScale * MeloXSettingsRuntime.lyricRomanizationFontScale,
                            13f,
                        ) * 1.2f
                    ).sp,
                    fontWeight = MeloXSettingsRuntime.lyricFontWeight.composeWeight,
                    textAlign = if (line.agent?.alignment == com.lladlam.melox.core.lyrics.LyricAgentAlignment.Flipped) TextAlign.End else TextAlign.Start,
                )
            }
        }
        return
    }

    val flipped = line.agent?.alignment == com.lladlam.melox.core.lyrics.LyricAgentAlignment.Flipped
    val units = remember(line) { LyricRomanizationAligner.units(line) }
    if (units.isEmpty()) {
        MeloXGlyphLyricText(
            line = line,
            playbackTimeProvider = playbackTimeProvider,
            supportsTimedLyrics = supportsTimedLyrics,
            fontScale = fontScale,
            reduceMotion = true,
            timingEffectsStrength = 1f,
            renderingQuality = renderingQuality,
            softBlurDp = softBlurDp,
            modifier = modifier,
        )
        return
    }

    val primarySize = UpstreamLyrics.FONT_SIZE_SP * fontScale
    val rubySize = max(primarySize * MeloXSettingsRuntime.lyricRomanizationFontScale, 13f)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            2.dp,
            alignment = if (flipped) Alignment.End else Alignment.Start,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        units.forEach { unit ->
            val start = unit.originalSyllables.minOfOrNull { it.startTimeMs } ?: 0L
            val end = unit.originalSyllables.maxOfOrNull { it.endTimeMs }?.coerceAtLeast(start + 1L) ?: 1L
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = unit.originalText,
                    color = Color.White,
                    modifier = Modifier.graphicsLayer {
                        alpha = if (!supportsTimedLyrics || unit.originalSyllables.isEmpty()) {
                            1f
                        } else {
                            val reveal = ((playbackTimeProvider() - start).toFloat() / (end - start).toFloat())
                                .coerceIn(0f, 1f)
                            0.3f + reveal * 0.7f
                        }
                    },
                    fontSize = primarySize.sp,
                    lineHeight = (UpstreamLyrics.LINE_HEIGHT_SP * fontScale).sp,
                    fontWeight = MeloXSettingsRuntime.lyricFontWeight.composeWeight,
                    maxLines = 1,
                    style = TextStyle(
                        shadow = if (softBlurDp > .05f) {
                            Shadow(Color.White.copy(alpha = .48f), Offset.Zero, softBlurDp)
                        } else null,
                    ),
                )
                if (!unit.romanizationText.isNullOrBlank()) {
                    val originalUnits = unit.originalText.codePointCount(0, unit.originalText.length).coerceAtLeast(1)
                    val rubyUnits = unit.romanizationText.codePointCount(0, unit.romanizationText.length).coerceAtLeast(1)
                    val rubyCompression = (originalUnits * 1.85f / rubyUnits).coerceIn(.68f, 1f)
                    Text(
                        text = unit.romanizationText,
                        color = Color.White.copy(alpha = MeloXSettingsRuntime.lyricRomanizationOpacity),
                        fontSize = (rubySize * rubyCompression).sp,
                        lineHeight = (rubySize * 1.2f).sp,
                        fontWeight = MeloXSettingsRuntime.lyricFontWeight.composeWeight,
                        maxLines = 1,
                        style = TextStyle(
                            shadow = if (softBlurDp > .05f) {
                                Shadow(Color.White.copy(alpha = .42f), Offset.Zero, softBlurDp)
                            } else null,
                        ),
                    )
                }
            }
        }
    }
}

private data class MeloXGlyphVisual(
    val reveal: Float,
    val liftPx: Float,
    val scale: Float,
    val glow: Float,
    val shakeXPx: Float,
    val shakeYPx: Float,
)

private data class MeloXGlyphTiming(
    val textOffset: Int,
    val start: Float,
    val end: Float,
    val liftStart: Float,
    val liftEnd: Float,
    val syllableStart: Float,
    val syllableEnd: Float,
    val characterIndex: Int,
    val characterCount: Int,
    val wordStart: Float,
    val wordEnd: Float,
    val wordCharacterIndex: Int,
    val wordCharacterCount: Int,
    val usesWordTimingForLongTone: Boolean,
    val longToneStart: Float,
    val longToneDuration: Float,
    val longToneCharacterIndex: Int,
    val longToneCharacterCount: Int,
    val isLongTone: Boolean,
    val expansionAmount: Float,
    val glowAmount: Float,
)

private data class MeloXDrawableGlyph(
    val textOffset: Int,
    val text: String,
    val bounds: Rect,
    val baseline: Float,
)

private val InactiveGlyphVisual = MeloXGlyphVisual(0f, 0f, 1f, 0f, 0f, 0f)

private data class MeloXLyricInterlude(
    val startTimeMs: Long,
    val countdownEndTimeMs: Long,
    val followingLyricTimeMs: Long,
    val followingLyricIndex: Int,
)

private fun sourceLyricInterludes(lines: List<LyricLine>): List<MeloXLyricInterlude> = buildList {
    lines.forEachIndexed { index, line ->
        val start = if (index == 0) {
            0L
        } else {
            val previous = lines[index - 1]
            max(
                previous.timeMs + (previous.durationMs ?: 0L),
                previous.syllables.maxOfOrNull { it.endTimeMs } ?: previous.timeMs,
            )
        }
        val countdownEnd = (line.timeMs - 250L).coerceAtLeast(start)
        if (countdownEnd - start >= 4_000L) {
            add(MeloXLyricInterlude(start, countdownEnd, line.timeMs, index))
        }
    }
}

@Composable
private fun MeloXLyricInterludeCountdown(
    interlude: MeloXLyricInterlude,
    playbackTimeProvider: () -> Long,
    reduceMotion: Boolean,
    visualScale: Float,
    visualOffsetPx: Float,
    rowAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .fillMaxWidth(0.28f)
            .height(48.dp)
            .graphicsLayer {
                translationY = visualOffsetPx
                scaleX = visualScale
                scaleY = visualScale
                alpha = rowAlpha
                transformOrigin = TransformOrigin(0f, 0f)
            },
    ) {
        val now = playbackTimeProvider()
        if (now < interlude.startTimeMs) return@Canvas
        val duration = (interlude.countdownEndTimeMs - interlude.startTimeMs).coerceAtLeast(1L)
        val elapsed = (now - interlude.startTimeMs).coerceIn(0L, duration)
        val remaining = interlude.countdownEndTimeMs - now
        if (remaining <= 0L) return@Canvas
        val durationFloat = duration.toFloat()
        val elapsedFloat = elapsed.toFloat()
        val baseRadius = 5.dp.toPx()
        val gap = 18.dp.toPx()
        val centerY = size.height / 2f
        repeat(3) { index ->
            val segmentStart = durationFloat * index / 3f
            val progress = ((elapsedFloat - segmentStart) / (durationFloat / 3f)).coerceIn(0.25f, 1f)
            val fadeOut = (remaining / 375f).coerceIn(0f, 1f)
            val breathe = if (reduceMotion) 1f else 1f +
                sin(elapsed.toDouble() / 1_500.0 * 2.0 * Math.PI).toFloat() * 0.05f
            drawCircle(
                color = Color.White.copy(alpha = progress * fadeOut),
                radius = baseRadius * breathe * if (index == 2) (1f + progress * 0.18f) else 1f,
                center = Offset(baseRadius + index * gap, centerY),
            )
        }
    }
}

private fun shareLyric(context: Context, state: MeloXPlaybackUiState, line: LyricLine) {
    val songUrl = state.mediaId
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
        ?.let { "https://music.163.com/song?id=$it" }
        .orEmpty()
    val text = buildString {
        append(line.text)
        if (state.title.isNotBlank()) {
            append("\n——《").append(state.title).append("》")
            if (state.artist.isNotBlank()) append(" · ").append(state.artist)
        }
        if (songUrl.isNotBlank()) append('\n').append(songUrl)
    }
    val intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        },
        "分享歌词",
    )
    context.startActivity(intent)
}

@Composable
private fun MeloXGlyphLyricText(
    line: LyricLine,
    playbackTimeProvider: () -> Long,
    supportsTimedLyrics: Boolean,
    fontScale: Float,
    reduceMotion: Boolean,
    timingEffectsStrength: Float,
    unplayedAlpha: Float = MeloXSettingsRuntime.lyricInactiveOpacity,
    renderingQuality: MeloXLyricsRenderingQuality,
    softBlurDp: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val flipped = line.agent?.alignment == com.lladlam.melox.core.lyrics.LyricAgentAlignment.Flipped
    val context = LocalContext.current
    val density = LocalDensity.current
    val lyricWeight = MeloXSettingsRuntime.lyricFontWeight.composeWeight
    val fontTypeface = remember {
        ResourcesCompat.getFont(context, R.font.mi_lan_pro) ?: Typeface.DEFAULT
    }
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val style = TextStyle(
            color = Color.White,
            fontFamily = LocalMeloXFontFamily.current,
            fontSize = (UpstreamLyrics.FONT_SIZE_SP * fontScale).sp,
            lineHeight = (UpstreamLyrics.LINE_HEIGHT_SP * fontScale).sp,
            fontWeight = lyricWeight,
            textAlign = if (flipped) TextAlign.End else TextAlign.Start,
        )
        val layout = remember(line.text, widthPx, style) {
            textMeasurer.measure(
                text = AnnotatedString(line.text),
                style = style,
                constraints = Constraints(minWidth = widthPx, maxWidth = widthPx),
                softWrap = true,
            )
        }
        val height = with(density) { layout.size.height.toDp() }
        val drawableGlyphs = remember(layout, line.text) {
            buildList {
                var offset = 0
                while (offset < line.text.length) {
                    val character = line.text[offset]
                    val codeUnitCount = if (
                        Character.isHighSurrogate(character) &&
                        offset + 1 < line.text.length &&
                        Character.isLowSurrogate(line.text[offset + 1])
                    ) 2 else 1
                    if (character != '\n' && character != '\r' && !Character.isLowSurrogate(character)) {
                        val bounds = runCatching { layout.getBoundingBox(offset) }.getOrNull()?.takeIf { box ->
                            box.width.isFinite() && box.height.isFinite() && box.width > 0f && box.height > 0f
                        }
                        if (bounds != null) {
                            val lineIndex = layout.getLineForOffset(offset)
                            val baseline = layout.getLineBaseline(lineIndex)
                            add(
                                MeloXDrawableGlyph(
                                    textOffset = offset,
                                    text = line.text.substring(offset, offset + codeUnitCount),
                                    bounds = bounds,
                                    baseline = baseline,
                                ),
                            )
                        }
                    }
                    offset += codeUnitCount
                }
            }
        }
        val liftMode = MeloXSettingsRuntime.lyricLiftMode
        val longToneDetectionMode = MeloXSettingsRuntime.lyricLongToneDetectionMode
        val longToneThresholdMs = MeloXSettingsRuntime.lyricLongToneThresholdMs
        val glyphTimings = remember(line, liftMode, longToneDetectionMode, longToneThresholdMs) {
            sourceGlyphTimings(
                line = line,
                liftMode = liftMode,
                longToneDetectionMode = longToneDetectionMode,
                longToneThresholdMs = longToneThresholdMs,
            )
        }
        val glyphPaint = remember(style, density, fontTypeface) {
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = with(density) { style.fontSize.toPx() }
                typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Typeface.create(fontTypeface, lyricWeight.weight, false)
                } else {
                    Typeface.create(fontTypeface, if (lyricWeight.weight >= 600) Typeface.BOLD else Typeface.NORMAL)
                }
            }
        }
        val blurMask = remember(softBlurDp, density) {
            val radiusPx = with(density) { softBlurDp.dp.toPx() }
            radiusPx.takeIf { it > .05f }?.let { BlurMaskFilter(it, BlurMaskFilter.Blur.NORMAL) }
        }

        val animatesTiming = supportsTimedLyrics &&
            timingEffectsStrength > 0.001f &&
            line.text.isNotEmpty()
        if (!animatesTiming) {
            Canvas(Modifier.fillMaxWidth().height(height)) {
                val staticAlpha = 1f
                if (blurMask == null) {
                    drawText(layout, color = Color.White.copy(alpha = staticAlpha))
                } else {
                    // Blur glyph masks directly. Unlike RenderEffect this does
                    // not allocate a screen-sized offscreen texture per lyric
                    // row, and the positions still come from Compose's layout.
                    glyphPaint.alpha = (staticAlpha * 255f).roundToInt()
                    glyphPaint.maskFilter = blurMask
                    drawableGlyphs.forEach { glyph ->
                        drawContext.canvas.nativeCanvas.drawText(
                            glyph.text,
                            glyph.bounds.left,
                            glyph.baseline,
                            glyphPaint,
                        )
                    }
                    glyphPaint.maskFilter = null
                }
            }
            return@BoxWithConstraints
        }

        Canvas(Modifier.fillMaxWidth().height(height)) {
            val playbackTimeMs = playbackTimeProvider()
            val effectsStrength = sourceTimingEffectsStrength(
                line = line,
                playbackTimeMs = playbackTimeMs,
                focusProgress = timingEffectsStrength,
            )
            if (effectsStrength <= 0.0001f) {
                drawText(
                    layout,
                    color = Color.White.copy(alpha = unplayedAlpha),
                )
                return@Canvas
            }

            // The unrevealed layer must remain at the ordinary unplayed lyric
            // opacity. Interpolating it from fully white made the whole next
            // line flash before the first syllable started revealing.
            val unplayedAlpha = unplayedAlpha.coerceIn(0f, 1f)

            if ((renderingQuality != MeloXLyricsRenderingQuality.High || reduceMotion) &&
                (!MeloXSettingsRuntime.lyricWordByWordEnabled || reduceMotion)
            ) {
                // Low/Balanced render the complete shaped row twice and reveal
                // it with one row mask. This preserves ligatures and reduces a
                // CJK line from dozens of native drawText/clip calls to two.
                drawText(layout, color = Color.White.copy(alpha = unplayedAlpha))
                val first = line.syllables.minOfOrNull { it.startTimeMs } ?: line.timeMs
                val last = line.syllables.maxOfOrNull { it.endTimeMs }
                    ?: (line.timeMs + (line.durationMs ?: 2_000L))
                val rowReveal = ((playbackTimeMs - first).toFloat() / (last - first).coerceAtLeast(1L))
                    .coerceIn(0f, 1f)
                if (rowReveal > 0f) {
                    if (isRtl) {
                        clipRect(left = size.width * (1f - rowReveal)) {
                            drawText(layout, color = Color.White)
                        }
                    } else {
                        clipRect(right = size.width * rowReveal) {
                            drawText(layout, color = Color.White)
                        }
                    }
                }
                return@Canvas
            }

            for (glyph in drawableGlyphs) {
                val bounds = glyph.bounds
                val fx = glyphTimings.getOrNull(glyph.textOffset)?.let { timing ->
                    sourceGlyphVisual(
                        timing = timing,
                        playbackTimeMs = playbackTimeMs,
                        density = density.density,
                        reduceMotion = reduceMotion,
                        fontScale = fontScale,
                    )
                } ?: InactiveGlyphVisual

                withTransform({
                    translate(
                        left = fx.shakeXPx * effectsStrength,
                        top = -fx.liftPx * effectsStrength + fx.shakeYPx * effectsStrength,
                    )
                    val presentationScale = 1f + (fx.scale - 1f) * effectsStrength
                    scale(
                        scaleX = presentationScale,
                        scaleY = presentationScale,
                        pivot = bounds.center,
                    )
                }) {
                    fun drawGlyph(alpha: Float) {
                        // The measured position and baseline come from the
                        // complete Compose layout, but only this glyph is
                        // rasterized. Both layers share this transformed
                        // coordinate space, matching MeloX iOS's runContext.
                        glyphPaint.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
                        drawContext.canvas.nativeCanvas.drawText(
                            glyph.text,
                            glyph.bounds.left,
                            glyph.baseline,
                            glyphPaint,
                        )
                    }

                    // Draw the unplayed layer after applying the glyph's lift
                    // and expansion. Keeping it at the original line position
                    // left a gray duplicate below every lifted white glyph.
                    drawGlyph(unplayedAlpha)

                    val reveal = fx.reveal.coerceIn(0f, 1f)
                    val glow = fx.glow * effectsStrength * MeloXSettingsRuntime.lyricGlowStrength
                    if (
                        reveal > 0f &&
                        glow > 0.001f &&
                        renderingQuality != MeloXLyricsRenderingQuality.Low &&
                        !reduceMotion
                    ) {
                        val glowRadius = with(density) {
                            (style.fontSize.toPx() * if (renderingQuality == MeloXLyricsRenderingQuality.High) .30f else .18f)
                                .coerceAtLeast(3.dp.toPx())
                        }
                        val revealFront = if (isRtl) {
                            bounds.right - bounds.width * reveal
                        } else {
                            bounds.left + bounds.width * reveal
                        }
                        clipRect(
                            left = if (isRtl) revealFront - glowRadius else bounds.left - glowRadius,
                            top = bounds.top - glowRadius,
                            right = if (isRtl) bounds.right + glowRadius else revealFront + glowRadius,
                            bottom = bounds.bottom + glowRadius,
                        ) {
                            glyphPaint.alpha = (glow.coerceIn(0f, 1f) * .648f * 255f).roundToInt()
                            glyphPaint.maskFilter = BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.NORMAL)
                            drawContext.canvas.nativeCanvas.drawText(
                                glyph.text,
                                glyph.bounds.left,
                                glyph.baseline,
                                glyphPaint,
                            )
                            glyphPaint.maskFilter = null
                        }
                    }

                    clipRect(
                        left = bounds.left,
                        top = bounds.top,
                        right = bounds.right,
                        bottom = bounds.bottom,
                    ) {
                        if (reveal <= 0f) return@clipRect

                        val feather = max(
                            bounds.width * MeloXSettingsRuntime.lyricHighlightGradientWidth,
                            1.5f * density.density,
                        )
                        val front = if (isRtl) {
                            bounds.right + feather - (bounds.width + feather) * reveal
                        } else {
                            bounds.left - feather + (bounds.width + feather) * reveal
                        }
                        val solidLeft = if (isRtl) max(front, bounds.left) else bounds.left
                        val solidRight = if (isRtl) bounds.right else min(front, bounds.right)
                        fun drawRevealed(alpha: Float) {
                            if (solidRight > solidLeft) {
                                clipRect(
                                    left = solidLeft,
                                    top = bounds.top,
                                    right = solidRight,
                                    bottom = bounds.bottom,
                                ) {
                                    drawGlyph(alpha)
                                }
                            }

                            val stopCount = when (renderingQuality) {
                                MeloXLyricsRenderingQuality.Low -> 1
                                MeloXLyricsRenderingQuality.Balanced -> 2
                                MeloXLyricsRenderingQuality.High -> 3
                            }
                            for (step in 0 until stopCount) {
                                val a = step.toFloat() / stopCount.toFloat()
                                val b = (step + 1).toFloat() / stopCount.toFloat()
                                val mid = (a + b) * .5f
                                val remaining = 1f - mid
                                val baseMask = remaining *
                                    (1f - MeloXSettingsRuntime.lyricHighlightGradientReduction * mid)
                                val maskAlpha = baseMask +
                                    (1f - baseMask) * glow.coerceIn(0f, 1f) * .14f
                                val left = if (isRtl) {
                                    max(front - feather * b, bounds.left)
                                } else {
                                    max(front + feather * a, bounds.left)
                                }
                                val right = if (isRtl) {
                                    min(front - feather * a, bounds.right)
                                } else {
                                    min(front + feather * b, bounds.right)
                                }
                                if (right > left) {
                                    clipRect(
                                        left = left,
                                        top = bounds.top,
                                        right = right,
                                        bottom = bounds.bottom,
                                    ) {
                                        drawGlyph(alpha * maskAlpha.coerceIn(0f, 1f))
                                    }
                                }
                            }
                        }

                        drawRevealed(1f)
                    }
                }
            }
        }
    }
}

private fun sourceGlyphTimings(
    line: LyricLine,
    liftMode: MeloXLyricsGroupingMode,
    longToneDetectionMode: MeloXLyricsGroupingMode,
    longToneThresholdMs: Int,
): List<MeloXGlyphTiming?> {
    val result = MutableList<MeloXGlyphTiming?>(line.text.length) { null }
    var searchFrom = 0
    for (syllable in line.syllables) {
        if (syllable.text.isEmpty()) continue
        val located = line.text.indexOf(syllable.text, startIndex = searchFrom)
        val startIndex = if (located >= 0) located else searchFrom.coerceAtMost(line.text.length)
        val count = syllable.text.length.coerceAtMost(line.text.length - startIndex)
        if (count <= 0) continue
        searchFrom = startIndex + count
        val syllableDuration = max(syllable.endTimeMs - syllable.startTimeMs, 0L).toFloat()
        val characterDuration = syllableDuration / count.toFloat()
        for (local in 0 until count) {
            val offset = startIndex + local
            val start = syllable.startTimeMs + characterDuration * local
            val end = if (local == count - 1) max(syllable.endTimeMs.toFloat(), start) else start + characterDuration
            result[offset] = MeloXGlyphTiming(
                textOffset = offset,
                start = start,
                end = end,
                liftStart = start,
                liftEnd = end + UpstreamLyrics.LIFT_CONTINUATION_MS,
                syllableStart = syllable.startTimeMs.toFloat(),
                syllableEnd = syllable.endTimeMs.toFloat(),
                characterIndex = local,
                characterCount = count,
                wordStart = start,
                wordEnd = end,
                wordCharacterIndex = 0,
                wordCharacterCount = 1,
                usesWordTimingForLongTone = false,
                longToneStart = syllable.startTimeMs.toFloat(),
                longToneDuration = syllableDuration,
                longToneCharacterIndex = local,
                longToneCharacterCount = count,
                isLongTone = false,
                expansionAmount = 0f,
                glowAmount = 0f,
            )
        }
    }

    // Match TimedLyricTextBuilder: highlights preserve individual character
    // timings, while a word block supplies the optional shared lift clock.
    sourceWordBlocks(line.text).forEach { block ->
        val timedOffsets = (block.start until block.end).filter { offset ->
            result.getOrNull(offset) != null && !line.text[offset].isWhitespace()
        }
        val wordStart = timedOffsets.minOfOrNull { result[it]!!.start } ?: return@forEach
        val wordEnd = timedOffsets.maxOfOrNull { result[it]!!.end } ?: return@forEach
        val positions = timedOffsets.withIndex().associate { it.value to it.index }
        val usesWordTimingForLongTone = timedOffsets.size > 1 && timedOffsets.all { offset ->
            line.text[offset].isAsciiLatinLetter()
        }
        for (offset in block.start until block.end) {
            result[offset] = result.getOrNull(offset)?.copy(
                wordStart = wordStart,
                wordEnd = wordEnd,
                wordCharacterIndex = positions[offset] ?: max(timedOffsets.size - 1, 0),
                wordCharacterCount = max(timedOffsets.size, 1),
                usesWordTimingForLongTone = usesWordTimingForLongTone,
            )
        }
    }

    return result.map { timing ->
        timing?.let {
            val liftStart = if (liftMode == MeloXLyricsGroupingMode.Word) it.wordStart else it.start
            val liftEnd = if (liftMode == MeloXLyricsGroupingMode.Word) it.wordEnd else it.end
            val usesWordGroup = it.usesWordTimingForLongTone ||
                longToneDetectionMode == MeloXLyricsGroupingMode.Word
            val toneStart = if (usesWordGroup) it.wordStart else it.syllableStart
            val toneEnd = if (usesWordGroup) it.wordEnd else it.syllableEnd
            val toneDuration = max(toneEnd - toneStart, 0f)
            val toneIndex = if (usesWordGroup) it.wordCharacterIndex else it.characterIndex
            val toneCount = if (usesWordGroup) it.wordCharacterCount else it.characterCount
            val longTone = !line.text[it.textOffset].isWhitespace() && toneDuration >= longToneThresholdMs
            val emphasisProgress = sourceSmootherStep(
                (toneDuration - longToneThresholdMs) / (2800f - longToneThresholdMs),
            )
            it.copy(
                liftStart = liftStart,
                liftEnd = liftEnd + UpstreamLyrics.LIFT_CONTINUATION_MS,
                longToneStart = toneStart,
                longToneDuration = toneDuration,
                longToneCharacterIndex = toneIndex,
                longToneCharacterCount = toneCount,
                isLongTone = longTone,
                expansionAmount = if (longTone) .7f + .3f * emphasisProgress else 0f,
                glowAmount = if (longTone) .32f + .38f * emphasisProgress else 0f,
            )
        }
    }
}

private fun sourceGlyphVisual(
    timing: MeloXGlyphTiming,
    playbackTimeMs: Long,
    density: Float,
    reduceMotion: Boolean,
    fontScale: Float,
): MeloXGlyphVisual {
    val duration = max(timing.end - timing.start, 0f)
    val raw = when {
        playbackTimeMs < timing.start -> 0f
        playbackTimeMs >= timing.end -> 1f
        duration <= 0f -> 1f
        else -> ((playbackTimeMs - timing.start) / duration).coerceIn(0f, 1f)
    }
    val reveal = sourceHighlightRevealProgress(
        playbackTimeMs.toFloat(),
        timing.start,
        timing.end,
        raw,
        timing.isLongTone,
    )
    val lift = if (playbackTimeMs <= timing.liftStart) 0f else sourceSmootherStep(
        (playbackTimeMs - timing.liftStart) / max(timing.liftEnd - timing.liftStart, 1f),
    )
    val risePx = if (reduceMotion) 0f else
        min(max(UpstreamLyrics.FONT_SIZE_SP * fontScale * .1f, 1.5f), 6f) * density
    val envelope = if (timing.isLongTone) sourceLongToneEnvelope(
        playbackTimeMs.toFloat(),
        timing.longToneStart,
        timing.longToneDuration,
        characterIndex = timing.longToneCharacterIndex,
        characterCount = timing.longToneCharacterCount,
    ) else 0f
    val longToneScale = if (reduceMotion) 1f else
        1f + (UpstreamLyrics.LONG_TONE_MAX_SCALE - 1f) * envelope * timing.expansionAmount *
            MeloXSettingsRuntime.lyricLongToneStrength
    // Word bounce: 1.0→1.2→1.0 when a word first becomes active (Apple Music style)
    val bounceScale = if (reduceMotion || timing.isLongTone || !MeloXSettingsRuntime.lyricWordBounceEnabled) 1f else {
        val bounceProgress = raw.coerceIn(0f, 1f)
        if (bounceProgress > 0f && bounceProgress < 0.5f) {
            1f + 0.2f * (bounceProgress / 0.5f)
        } else if (bounceProgress >= 0.5f && bounceProgress < 1f) {
            1.2f - 0.2f * ((bounceProgress - 0.5f) / 0.5f)
        } else {
            1f
        }
    }
    val scale = longToneScale * bounceScale
    val glow = if (reduceMotion || !MeloXSettingsRuntime.lyricGlowEnabled ||
        (MeloXSettingsRuntime.lyricGlowLongTonesOnly && !timing.isLongTone)
    ) 0f else {
        if (timing.isLongTone) envelope * timing.glowAmount else sourceOrdinaryGlowStrength(
            playbackTimeMs = playbackTimeMs.toFloat(),
            endMs = timing.end,
            rawProgress = raw,
        )
    }
    val shakeAmplitude = if (reduceMotion || !timing.isLongTone || raw <= 0f || raw >= 1f) 0f else {
        min(.58f * density, UpstreamLyrics.FONT_SIZE_SP * fontScale * .013f * density) *
            envelope * timing.expansionAmount * MeloXSettingsRuntime.lyricLongToneStrength
    }
    val shakePhase = (playbackTimeMs - timing.longToneStart).coerceAtLeast(0f)
    val shakeX = sin(shakePhase * .016f + timing.longToneCharacterIndex * 1.73f) * shakeAmplitude
    val shakeY = sin(shakePhase * .021f + timing.longToneCharacterIndex * .91f) * shakeAmplitude * .42f
    return MeloXGlyphVisual(
        reveal = reveal,
        liftPx = risePx * lift,
        scale = scale,
        glow = glow,
        shakeXPx = shakeX,
        shakeYPx = shakeY,
    )
}

private fun sourceOrdinaryGlowStrength(
    playbackTimeMs: Float,
    endMs: Float,
    rawProgress: Float,
): Float {
    if (playbackTimeMs <= endMs) {
        val attack = sourceSmootherStep(rawProgress / .24f)
        val breath = .82f + .18f * kotlin.math.sin(Math.PI.toFloat() * rawProgress)
        return attack * breath * .55f
    }
    val tail = ((playbackTimeMs - endMs) / UpstreamLyrics.GLOW_TAIL_MS).coerceIn(0f, 1f)
    return (1f - sourceSmootherStep(tail)) * .82f * .55f
}

private data class SourceTextBlock(val start: Int, val end: Int)

private fun sourceWordBlocks(text: String): List<SourceTextBlock> {
    if (text.isEmpty()) return emptyList()
    val tokens = mutableListOf<SourceTextBlock>()
    var phraseStart = -1
    for (index in 0..text.length) {
        val isWhitespace = index == text.length || text[index].isWhitespace()
        if (!isWhitespace && phraseStart < 0) phraseStart = index
        if (isWhitespace && phraseStart >= 0) {
            val phraseEnd = index
            val iterator = BreakIterator.getWordInstance(Locale.ROOT).apply {
                setText(text.substring(phraseStart, phraseEnd))
            }
            var tokenStart = iterator.first()
            var tokenEnd = iterator.next()
            var foundWord = false
            while (tokenEnd != BreakIterator.DONE) {
                val token = text.substring(phraseStart + tokenStart, phraseStart + tokenEnd)
                if (token.any { it.isLetterOrDigit() }) {
                    tokens += SourceTextBlock(phraseStart + tokenStart, phraseStart + tokenEnd)
                    foundWord = true
                }
                tokenStart = tokenEnd
                tokenEnd = iterator.next()
            }
            if (!foundWord) tokens += SourceTextBlock(phraseStart, phraseEnd)
            phraseStart = -1
        }
    }
    if (tokens.isEmpty()) return listOf(SourceTextBlock(0, text.length))
    val sorted = tokens.sortedBy { it.start }
    return buildList {
        sorted.forEachIndexed { index, token ->
            val start = if (index == 0) 0 else sorted[index - 1].end
            val end = if (index + 1 < sorted.size) sorted[index + 1].start else text.length
            if (start < end) add(SourceTextBlock(start, end))
        }
    }
}

private fun Char.isAsciiLatinLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'

private fun sourceTimingEffectsStrength(
    line: LyricLine,
    playbackTimeMs: Long,
    focusProgress: Float,
): Float {
    val focus = focusProgress.coerceIn(0f, 1f)
    if (focus <= 0f || line.syllables.isEmpty()) return 0f
    val firstSyllableStartMs = line.syllables.minOf { it.startTimeMs }
    val activation = sourceSmootherStep(
        (playbackTimeMs - firstSyllableStartMs).toFloat() /
            UpstreamLyrics.FOCUS_COLOR_DURATION_MS.toFloat(),
    )
    return min(focus, activation)
}

private fun sourceTimedAnnotatedString(line: LyricLine, playbackTimeMs: Long) =
    buildAnnotatedString {
        line.syllables.forEach { syllable ->
            val characters = syllable.text.toCharArray()
            if (characters.isEmpty()) return@forEach
            val syllableDuration = max(syllable.endTimeMs - syllable.startTimeMs, 0L).toFloat()
            val characterDuration = syllableDuration / characters.size.toFloat()
            characters.forEachIndexed { index, character ->
                val start = syllable.startTimeMs + characterDuration * index
                val end = if (index == characters.lastIndex) {
                    max(syllable.endTimeMs.toFloat(), start)
                } else {
                    start + characterDuration
                }
                val duration = max(end - start, 0f)
                val rawProgress = when {
                    playbackTimeMs < start -> 0f
                    playbackTimeMs >= end -> 1f
                    duration <= 0f -> 1f
                    else -> ((playbackTimeMs - start) / duration).coerceIn(0f, 1f)
                }
                val isLongTone = syllableDuration >= UpstreamLyrics.LONG_TONE_THRESHOLD_MS && !character.isWhitespace()
                val revealProgress = sourceHighlightRevealProgress(
                    playbackTimeMs.toFloat(), start, end, rawProgress, isLongTone,
                )
                val liftEnd = end + UpstreamLyrics.LIFT_CONTINUATION_MS
                val liftProgress = if (playbackTimeMs <= start) 0f else {
                    sourceSmootherStep((playbackTimeMs - start) / max(liftEnd - start, 1f))
                }
                val playedRise = min(max(UpstreamLyrics.FONT_SIZE_SP * 0.1f, 1.5f), 6f)
                val longEnvelope = if (isLongTone) {
                    sourceLongToneEnvelope(
                        playbackTimeMs.toFloat(),
                        syllable.startTimeMs.toFloat(),
                        syllableDuration,
                        index,
                        characters.size,
                    )
                } else 0f
                val expansionAmount = if (isLongTone) {
                    0.7f + 0.3f * sourceSmootherStep(
                        (syllableDuration - UpstreamLyrics.LONG_TONE_THRESHOLD_MS) /
                            (2800f - UpstreamLyrics.LONG_TONE_THRESHOLD_MS),
                    )
                } else 0f
                val glyphScale = 1f +
                    (UpstreamLyrics.LONG_TONE_MAX_SCALE - 1f) * longEnvelope * expansionAmount *
                        MeloXSettingsRuntime.lyricLongToneStrength
                val glowAmount = if (isLongTone) {
                    0.32f + 0.38f * sourceSmootherStep(
                        (syllableDuration - UpstreamLyrics.LONG_TONE_THRESHOLD_MS) /
                            (2800f - UpstreamLyrics.LONG_TONE_THRESHOLD_MS),
                    )
                } else 0f
                val glowStrength = if (MeloXSettingsRuntime.lyricGlowEnabled) {
                    longEnvelope * glowAmount * MeloXSettingsRuntime.lyricGlowStrength
                } else 0f
                val opacity = MeloXSettingsRuntime.lyricInactiveOpacity +
                    (1f - MeloXSettingsRuntime.lyricInactiveOpacity) * revealProgress

                val startOffset = length
                append(character)
                addStyle(
                    SpanStyle(
                        color = Color.White.copy(alpha = opacity.coerceIn(0f, 1f)),
                        fontSize = (UpstreamLyrics.FONT_SIZE_SP * glyphScale).sp,
                        fontWeight = MeloXSettingsRuntime.lyricFontWeight.composeWeight,
                        baselineShift = BaselineShift(
                            (playedRise / UpstreamLyrics.FONT_SIZE_SP) * liftProgress,
                        ),
                        shadow = if (glowStrength > 0f) {
                            Shadow(
                                color = Color.White.copy(alpha = (glowStrength * 0.7425f).coerceIn(0f, 1f)),
                                blurRadius = UpstreamLyrics.FONT_SIZE_SP * 0.3f,
                            )
                        } else null,
                    ),
                    startOffset,
                    length,
                )
            }
        }
    }

private fun sourceHighlightRevealProgress(
    playbackTimeMs: Float,
    startMs: Float,
    endMs: Float,
    rawProgress: Float,
    isLongTone: Boolean,
): Float {
    val regular = sourceSmootherStep(rawProgress)
    val duration = endMs - startMs
    if (!isLongTone || duration <= 460f) return regular
    val elapsed = max(playbackTimeMs - startMs, 0f)
    val attack = sourceSmootherStep(elapsed / 300f)
    val releaseStart = endMs - 160f
    val release = sourceSmootherStep((playbackTimeMs - releaseStart) / 160f)
    return (0.82f * attack + 0.08f * rawProgress + 0.10f * release).coerceIn(0f, 1f)
}

private fun sourceLongToneEnvelope(
    playbackTimeMs: Float,
    groupStartMs: Float,
    durationMs: Float,
    characterIndex: Int,
    characterCount: Int,
): Float {
    val stagger = if (characterCount > 1) {
        durationMs * 0.55f * characterIndex.toFloat() / (characterCount - 1).toFloat()
    } else 0f
    val animationDuration = max(durationMs, 1000f)
    val progress = ((playbackTimeMs - groupStartMs - stagger) / animationDuration).coerceIn(0f, 1f)
    return if (progress <= 0.5f) {
        sourceSmootherStep(progress / 0.5f)
    } else {
        1f - sourceSmootherStep((progress - 0.5f) / 0.5f)
    }
}

private fun sourceConcurrentLyricIndexes(
    lines: List<LyricLine>,
    highlightedIndex: Int,
    toleranceMs: Long = 280L,
): IntRange {
    if (highlightedIndex !in lines.indices) return IntRange.EMPTY
    val activation = sourceLineActivationTimeMs(lines[highlightedIndex])
    var first = highlightedIndex
    var last = highlightedIndex
    while (first > 0 && activation - sourceLineActivationTimeMs(lines[first - 1]) <= toleranceMs) first--
    while (last < lines.lastIndex && sourceLineActivationTimeMs(lines[last + 1]) - activation <= toleranceMs) last++
    return first..last
}

private fun sourceFocusAnimationDurationMs(index: Int, lines: List<LyricLine>): Int {
    if (index !in lines.indices) return 300
    val available = if (index + 1 < lines.size) {
        sourceLineActivationTimeMs(lines[index + 1]) - sourceLineActivationTimeMs(lines[index])
    } else null
    if (available == null || available <= 0L) return 300
    return (available * 0.30f).coerceIn(50f, 240f).roundToInt()
}

private fun sourceRemainingFocusDurationMs(
    index: Int,
    playbackTimeMs: Long,
    lines: List<LyricLine>,
): Float? {
    if (index !in lines.indices || index + 1 >= lines.size) return null
    return max((sourceLineActivationTimeMs(lines[index + 1]) - playbackTimeMs).toFloat(), 0f)
}

private fun sourceLineActivationTimeMs(line: LyricLine): Long =
    line.syllables.minOfOrNull { it.startTimeMs } ?: line.timeMs

private data class SourceCascadeLineTiming(val delayMs: Float, val durationMs: Float)

private fun sourceCascadeLineTimings(
    maximumLineOrder: Int,
    animationDurationMs: Float,
): List<SourceCascadeLineTiming> {
    val catchUpCompletionTime = animationDurationMs * MeloXSettingsRuntime.lyricCascadeCatchUpRatio
    val minimumCatchUpDuration = min(180f, animationDurationMs * 0.5f)
    return (0..maximumLineOrder.coerceAtLeast(0)).map { order ->
        if (order == 0) {
            SourceCascadeLineTiming(0f, animationDurationMs)
        } else {
            val accumulatedIncrease = order.toFloat() * (order - 1).toFloat() / 2f
            val delay = MeloXSettingsRuntime.lyricCascadeFollowingDelayMs +
                order * MeloXSettingsRuntime.lyricCascadeDelayMs +
                accumulatedIncrease * MeloXSettingsRuntime.lyricCascadeDelayIncreaseMs
            SourceCascadeLineTiming(
                delayMs = delay,
                durationMs = max(catchUpCompletionTime - delay, minimumCatchUpDuration),
            )
        }
    }
}

private fun sourceCascadeBounce(chaseOrder: Int, maximumChaseOrder: Int): Float {
    if (!MeloXSettingsRuntime.lyricCascadeBounceEnabled) return 0f
    val count = max(maximumChaseOrder + 1, 1)
    val position = chaseOrder.coerceIn(0, maximumChaseOrder) + 1
    val normalized = position.toFloat() / count.toFloat()
    val bounceScale = 1f - (1f - normalized) * MeloXSettingsRuntime.lyricCascadeBounceGradient
    return MeloXSettingsRuntime.lyricCascadeBounce * bounceScale
}

private fun sourceDistanceBlurRadius(
    distancePx: Float,
    lyricStridePx: Float,
    intensity: Float,
    focusProgress: Float,
): Float {
    val lineDistance = distancePx / lyricStridePx
    val blurProgress = max(lineDistance - 1.35f, 0f)
    val baseRadius = min(blurProgress * 3.1f, 10f)
    return baseRadius * intensity * (1f - focusProgress.coerceIn(0f, 1f))
}

private fun sourceFocusBlurRadius(
    intensity: Float,
    preceding: Boolean,
    following: Boolean,
): Float = ((if (preceding) 2.4f else 0f) + (if (following) 0.7f else 0f)) * intensity

private fun sourceDistanceOpacity(
    distancePx: Float,
    lyricStridePx: Float,
    dimAmount: Float,
    focusProgress: Float,
): Float {
    val lineDistance = distancePx / lyricStridePx
    val base = when {
        lineDistance <= 1f -> 1f - lineDistance * 0.44f
        lineDistance <= 2f -> 0.56f - (lineDistance - 1f) * 0.22f
        else -> max(0.12f, 0.34f - (lineDistance - 2f) * 0.07f)
    }
    val distanceOpacity = 1f - (1f - base) * dimAmount
    return distanceOpacity + (1f - distanceOpacity) * focusProgress.coerceIn(0f, 1f)
}

private fun sourceEmphasis(focusProgress: Float, dimAmount: Float): Float {
    val unfocused = 1f - (1f - 0.52f) * dimAmount
    return unfocused + (1f - unfocused) * focusProgress.coerceIn(0f, 1f)
}

private fun sourceSmootherStep(value: Float): Float {
    val p = value.coerceIn(0f, 1f)
    return p * p * p * (p * (p * 6f - 15f) + 10f)
}

private object SourceSmoothStepEasing : Easing {
    override fun transform(fraction: Float): Float {
        val p = fraction.coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }
}

private class SourceSpringEasing(private val bounce: Float) : Easing {
    override fun transform(fraction: Float): Float {
        val t = fraction.coerceIn(0f, 1f)
        if (bounce <= 0.0001f) return SourceSmoothStepEasing.transform(t)
        val damping = (1f - bounce.coerceIn(0f, 0.95f) * 0.72f).coerceIn(0.18f, 0.999f)
        val omega = 9.5f
        val damped = omega * sqrt(max(1f - damping * damping, 0.0001f))
        val envelope = exp((-damping * omega * t).toDouble()).toFloat()
        val raw = 1f - envelope * (
            kotlin.math.cos((damped * t).toDouble()).toFloat() +
                (damping / sqrt(max(1f - damping * damping, 0.0001f))) *
                sin((damped * t).toDouble()).toFloat()
            )
        val endEnvelope = exp((-damping * omega).toDouble()).toFloat()
        val endRaw = 1f - endEnvelope * (
            kotlin.math.cos(damped.toDouble()).toFloat() +
                (damping / sqrt(max(1f - damping * damping, 0.0001f))) *
                sin(damped.toDouble()).toFloat()
            )
        return if (abs(endRaw) > 0.0001f) raw / endRaw else raw
    }
}
