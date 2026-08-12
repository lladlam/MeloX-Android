package com.lladlam.melox.ui.player

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricRomanizationAligner
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.lyrics.withPseudoTiming
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXLyricsStyle
import com.lladlam.melox.ui.settings.MeloXLyricAnnotationDisplayMode
import com.lladlam.melox.ui.settings.MeloXLyricsGroupingMode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    const val LONG_TONE_MAX_SCALE = 1.05f
    const val GLOW_TAIL_MS = 550f
    const val HIGHLIGHT_GRADIENT_WIDTH = 0.7f
    const val HIGHLIGHT_GRADIENT_REDUCTION = 0.65f
    const val UNPLAYED_OPACITY = 0.3f
    const val UNPLAYED_BLUR_LEAD_MS = 2400f
    const val MIN_UNPLAYED_BLUR_FRACTION = 0.12f
    const val LIFT_CONTINUATION_MS = 320f

}

@Composable
fun MeloXIOSLyricsPanel(
    state: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
    isInterfaceHidden: Boolean = false,
    onInterfaceInteraction: () -> Unit = {},
    onInterfaceVisibilityChange: (Boolean) -> Unit = {},
    allowAutomaticSkyline: Boolean = true,
) {
    val configuration = LocalConfiguration.current
    if (allowAutomaticSkyline && configuration.screenWidthDp > configuration.screenHeightDp && MeloXSettingsRuntime.skylineEnabled) {
        MeloXSkylineLyricsPanel(state, modifier, onInterfaceInteraction)
        return
    }
    when (MeloXSettingsRuntime.lyricsStyle) {
        MeloXLyricsStyle.AppleMusic -> MeloXAppleMusicLyricsPanel(
            state,
            modifier,
            isInterfaceHidden,
            onInterfaceInteraction,
            onInterfaceVisibilityChange,
        )
        MeloXLyricsStyle.Eva -> MeloXEvaLyricsPanel(state, modifier, onInterfaceInteraction)
        MeloXLyricsStyle.TextPV -> MeloXTextPVLyricsPanel(state, modifier, onInterfaceInteraction)
    }
}

@Composable
private fun MeloXAppleMusicLyricsPanel(
    state: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
    isInterfaceHidden: Boolean = false,
    onInterfaceInteraction: () -> Unit = {},
    onInterfaceVisibilityChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val haptics = LocalHapticFeedback.current
    val client = remember(context) {
        NeteaseSearchClient(
            cookieProvider = { NeteaseSessionStore.readCookie(appContext) },
        )
    }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val mediaId = state.mediaId

    var lyrics by remember(mediaId) { mutableStateOf<LyricsDocument?>(null) }
    var isLoading by remember(mediaId) { mutableStateOf(false) }
    var errorMessage by remember(mediaId) { mutableStateOf<String?>(null) }

    var anchorPositionMs by remember(mediaId) { mutableLongStateOf(state.positionMs) }
    var anchorRealtimeMs by remember(mediaId) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val renderedPositionState = remember(mediaId) { mutableLongStateOf(state.positionMs) }

    LaunchedEffect(state.positionMs, state.isPlaying, mediaId) {
        anchorPositionMs = state.positionMs
        anchorRealtimeMs = SystemClock.elapsedRealtime()
        renderedPositionState.longValue = state.positionMs
    }

    LaunchedEffect(mediaId) {
        val songId = mediaId?.toLongOrNull() ?: return@LaunchedEffect
        isLoading = true
        errorMessage = null
        val downloaded = MeloXDownloadStore.get(appContext).localLyrics(songId)
        if (downloaded != null) {
            lyrics = downloaded
        } else runCatching { client.lyrics(songId) }
            .onSuccess { lyrics = it }
            .onFailure { errorMessage = it.message ?: "歌词加载失败" }
        isLoading = false
    }

    val document = lyrics
    val pseudoTimingEnabled = MeloXSettingsRuntime.lyricPseudoTimingEnabled
    val renderedDocument = remember(document, pseudoTimingEnabled) {
        if (pseudoTimingEnabled) document?.withPseudoTiming() else document
    }
    val lines = renderedDocument?.lines.orEmpty()
    val hasSyllableSync = remember(renderedDocument) { lines.any { it.syllables.isNotEmpty() } }
    val lyricAdvanceMs = MeloXSettingsRuntime.lyricAdvanceMs.toLong()
    val usesWordByWordPresentation = hasSyllableSync && MeloXSettingsRuntime.lyricWordByWordEnabled
    val timedAdvanceMs = if (MeloXSettingsRuntime.lyricAdvanceAppliesToWordByWord) lyricAdvanceMs else 0L
    val lineAdvanceMs = if (usesWordByWordPresentation) timedAdvanceMs else lyricAdvanceMs
    var highlightedIndex by remember(document) {
        mutableIntStateOf(
            sourceHighlightedIndex(
                lines,
                state.positionMs + lineAdvanceMs,
            ),
        )
    }
    var colorHighlightedIndex by remember(document) { mutableIntStateOf(highlightedIndex) }
    val playbackTimeProvider = remember(mediaId, timedAdvanceMs) {
        { renderedPositionState.longValue + timedAdvanceMs }
    }

    // Update the line index only when it actually changes. The per-frame time is
    // read later from Canvas, so the 60 Hz clock invalidates drawing rather than
    // recomposing and relaying out the complete lyric list.
    val refreshRate = MeloXSettingsRuntime.lyricRefreshRate
    val interludes = remember(lines) { sourceLyricInterludes(lines) }
    val interludeByLyricIndex = remember(interludes) { interludes.associateBy { it.followingLyricIndex } }
    val revealedInterludes = remember(document) { mutableStateMapOf<Long, Boolean>() }
    var activeInterludeIndex by remember(document) { mutableIntStateOf(-1) }
    LaunchedEffect(state.isPlaying, mediaId, document, hasSyllableSync, lineAdvanceMs, refreshRate) {
        var lastFrameNanos = 0L
        val minimumFrameNanos = 1_000_000_000L / refreshRate.coerceIn(30, 120)
        while (true) {
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
            val effectivePosition = position + lineAdvanceMs
            val nextInterlude = interludes.indexOfFirst { position >= it.startTimeMs && position < it.followingLyricTimeMs }
            if (nextInterlude != activeInterludeIndex) activeInterludeIndex = nextInterlude
            val activeInterlude = interludes.getOrNull(nextInterlude)
            if (activeInterlude != null) revealedInterludes[activeInterlude.startTimeMs] = true
            val nextIndex = if (
                MeloXSettingsRuntime.lyricInterludeCountdownEnabled &&
                activeInterlude != null && position < activeInterlude.countdownEndTimeMs
            ) {
                activeInterlude.followingLyricIndex
            } else {
                sourceHighlightedIndex(lines, effectivePosition)
            }
            if (nextIndex != highlightedIndex) highlightedIndex = nextIndex
            val nextColorIndex = sourceHighlightedIndex(
                lines,
                effectivePosition + MeloXSettingsRuntime.lyricFocusColorLeadMs,
            )
            if (nextColorIndex != colorHighlightedIndex) colorHighlightedIndex = nextColorIndex
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
        // Upstream defaults translation to focused-line mode. Reserve its layout
        // height so focus promotion never changes the scroll geometry.
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
        // The list itself moves by -distance * scrollProgress. This translation
        // cancels that motion until the row's delayed progress starts, then lets
        // the row catch up to its destination with the source spring.
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

    suspend fun handOffFocusColor(nextIndex: Int) = coroutineScope {
        focusProgress.forEachIndexed { index, anim ->
            val target = if (index == nextIndex) 1f else 0f
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

    LaunchedEffect(colorHighlightedIndex, document) {
        if (colorHighlightedIndex in lines.indices) handOffFocusColor(colorHighlightedIndex)
    }

    // Only real pointer/nested-scroll input enters browsing mode. Programmatic
    // scrollTo/animateScrollTo must never disable its own lyric following.
    val scrollHideThresholdPx = with(density) { MeloXSettingsRuntime.lyricScrollHideThresholdDp.dp.toPx() }
    val lyricInteractionConnection = remember(document, scrollHideThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val offsetDelta = -available.y // match SwiftUI contentOffset delta
                if (kotlin.math.abs(offsetDelta) < 0.01f) return Offset.Zero

                isBrowsingLyrics = true
                browseGeneration += 1

                if (offsetDelta < 0f) {
                    // Browsing back toward previous lyrics restores controls immediately.
                    scrollHideDistancePx = 0f
                    if (latestInterfaceHidden.value) {
                        latestVisibilityCallback.value.invoke(true)
                    } else {
                        latestInteractionCallback.value.invoke()
                    }
                } else if (!latestInterfaceHidden.value) {
                    // Forward browsing keeps an already-hidden interface hidden; if
                    // controls are visible, activity resets the 5-second idle timer
                    // and 200dp of continued scrolling hides them, matching upstream.
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

    // A real drag cancels the automatic cascade. Remove any remaining visual
    // compensation so the rows track the user's finger one-to-one.
    LaunchedEffect(isBrowsingLyrics, document) {
        if (isBrowsingLyrics) clearCascadePresentation(visualFocusIndex)
    }

    BoxWithConstraints(
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
            val desiredItemTop = viewportAnchor -
                estimatedHeight(index) * focusPosition
            // LazyListState uses a positive value to scroll the item farther past
            // the viewport start. A negative value places its top below the start.
            return -desiredItemTop.roundToInt()
        }

        LaunchedEffect(
            highlightedIndex,
            playbackFocusGeneration,
            viewportHeightPx,
            isBrowsingLyrics,
            document,
        ) {
            val nextIndex = highlightedIndex
            if (nextIndex !in lines.indices || viewportHeightPx <= 0 || isBrowsingLyrics) {
                if (isBrowsingLyrics && nextIndex in lines.indices) {
                    visualFocusIndex = nextIndex
                }
                return@LaunchedEffect
            }

            val previousIndex = visualFocusIndex
            val targetOffset = focusItemScrollOffset(nextIndex)

            if (previousIndex !in lines.indices) {
                listState.scrollToItem(nextIndex + 1, targetOffset)
                focusProgress.forEachIndexed { index, anim ->
                    anim.snapTo(if (index == colorHighlightedIndex) 1f else 0f)
                }
                scaleProgress.forEachIndexed { index, anim ->
                    anim.snapTo(if (index == nextIndex) 1f else 0f)
                }
                clearCascadePresentation(nextIndex)
                return@LaunchedEffect
            }

            if (previousIndex == nextIndex) {
                return@LaunchedEffect
            }

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
            val lastMoving = max(
                lastVisible,
                min(nextIndex + 8, lines.lastIndex), // six safety + two preload
            )
            val movingIndexes = firstMoving..lastMoving
            val carriedOffsets = movingIndexes.associateWith(::currentMovementOffset)
            val destinations = movingIndexes.associateWith {
                settledMovementOffset(it, nextIndex)
            }

            // Prepare a new transition from the current presentation. Animatable's
            // cancellation and carried values make dense lyric changes continue
            // smoothly instead of restarting every row from zero.
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

                // Drive the logical LazyColumn scroll and its compensation from
                // one frame-clock value, avoiding drift between two animations.
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
                        if (movementTiming.delayMs > 0f) {
                            delay(movementTiming.delayMs.toLong())
                        }
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
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    color = Color.White.copy(alpha = 0.52f),
                    fontSize = 15.sp,
                )
            }

            document == null || lines.isEmpty() -> {
                Text(
                    text = "暂无歌词",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 18.sp,
                )
            }

            else -> {
                val focusAnchorY = viewportHeightPx * focusPosition
                val annotationHeightPx =
                    annotationFontPx * 1.2f * 2f + annotationSpacingPx * 2f
                val lyricStridePx = max(primaryHeightPx + annotationHeightPx + lineSpacingPx, 1f)
                val visibleItemsByIndex = listState.layoutInfo.visibleItemsInfo.associateBy { it.index }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(lyricInteractionConnection),
                ) {
                    item(key = "lyrics-top-padding") {
                        Spacer(Modifier.height(with(density) { topPaddingPx.toDp() }))
                    }
                    itemsIndexed(
                        items = lines,
                        key = { index, line -> "${line.timeMs}:$index" },
                    ) { index, line ->
                        val interlude = interludeByLyricIndex[index]
                        val showsInterlude = MeloXSettingsRuntime.lyricInterludeCountdownEnabled &&
                            interlude != null && revealedInterludes[interlude.startTimeMs] == true
                        val interludeHeightPx = if (showsInterlude) with(density) { 56.dp.toPx() } else 0f
                        val height = estimatedHeight(index) + interludeHeightPx
                        val visualOffset = currentMovementOffset(index)
                        val frameMinY = visibleItemsByIndex[index + 1]?.offset?.toFloat()
                            ?: focusAnchorY + (index - visualFocusIndex) * lyricStridePx
                        val visualMidY = frameMinY + visualOffset + height * 0.5f
                        // Automatic movement should not feed its own changing row
                        // geometry back into blur/opacity. The upstream transition
                        // freezes presentation geometry while the cascade runs.
                        // Index distance is its stable LazyColumn equivalent; while
                        // browsing, actual viewport distance remains interactive.
                        val distance = if (isBrowsingLyrics || visualFocusIndex !in lines.indices) {
                            abs(visualMidY - focusAnchorY)
                        } else {
                            abs(index - visualFocusIndex) * lyricStridePx
                        }
                        val fp = focusProgress[index].value.coerceIn(0f, 1f)
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
                        val reveal = sourceBottomRevealOpacity(
                            frameMinY = frameMinY,
                            movementOffset = visualOffset,
                            frameHeight = height,
                            viewportHeight = viewportHeightPx.toFloat(),
                        )
                        val rowAlpha = (distanceOpacity * emphasis * reveal).coerceIn(0f, 1f)
                        val scale = 1f +
                            (MeloXSettingsRuntime.lyricFocusScale - 1f) * scaleProgress[index].value

                        if (showsInterlude && interlude != null) {
                            MeloXLyricInterludeCountdown(
                                interlude = interlude,
                                playbackTimeProvider = { renderedPositionState.longValue },
                                reduceMotion = MeloXSettingsRuntime.lyricReduceMotion,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }

                        MeloXUpstreamLyricLine(
                            line = line,
                            playbackTimeProvider = playbackTimeProvider,
                            supportsTimedLyrics = line.syllables.isNotEmpty() &&
                                MeloXSettingsRuntime.lyricWordByWordEnabled,
                            fontScale = lyricFontScale,
                            reduceMotion = MeloXSettingsRuntime.lyricReduceMotion,
                            focusProgress = effectiveFocus,
                            visualScale = scale,
                            visualOffsetPx = visualOffset,
                            rowAlpha = rowAlpha,
                            distanceBlurDp = distanceBlur,
                            focusBlurDp = focusBlur,
                            showTranslation = MeloXSettingsRuntime.showLyricTranslation &&
                                !line.translation.isNullOrBlank() &&
                                (MeloXSettingsRuntime.lyricTranslationDisplayMode == MeloXLyricAnnotationDisplayMode.AllLines ||
                                    index == visualFocusIndex),
                            showRomanization = MeloXSettingsRuntime.showLyricRomanization &&
                                !line.romanization.isNullOrBlank() &&
                                (MeloXSettingsRuntime.lyricRomanizationDisplayMode == MeloXLyricAnnotationDisplayMode.AllLines ||
                                    index == visualFocusIndex),
                            reserveTranslation = MeloXSettingsRuntime.showLyricTranslation && !line.translation.isNullOrBlank(),
                            reserveRomanization = MeloXSettingsRuntime.showLyricRomanization && !line.romanization.isNullOrBlank(),
                            onMeasured = { measured ->
                                if (measured > 0 && rowHeightsPx[index] != measured) {
                                    rowHeightsPx[index] = measured
                                }
                            },
                            tapSeekEnabled = MeloXSettingsRuntime.lyricTapSeekEnabled,
                            onClick = { onInterfaceInteraction(); state.seekTo(line.timeMs) },
                            longPressShareEnabled = MeloXSettingsRuntime.lyricLongPressShareEnabled,
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onInterfaceInteraction()
                                shareLyric(context, state, line)
                            },
                        )

                        if (index != lines.lastIndex) {
                            Spacer(Modifier.height((UpstreamLyrics.LINE_SPACING_DP * lyricSpacingScale).dp))
                        }
                    }
                    item(key = "lyrics-bottom-padding") {
                        Spacer(Modifier.height(with(density) { bottomPaddingPx.toDp() }))
                    }
                }

            }
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
    visualScale: Float,
    visualOffsetPx: Float,
    rowAlpha: Float,
    distanceBlurDp: Float,
    focusBlurDp: Float,
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
    // One blur layer is materially cheaper than stacking distance and focus
    // blurs on every visible row, and removes a common source of scroll jank.
    val blurModifier = Modifier.blur(
        radius = max(max(distanceBlurDp, focusBlurDp), 0f).dp,
        edgeTreatment = BlurredEdgeTreatment.Unbounded,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onMeasured(it.height) }
            .graphicsLayer {
                translationY = visualOffsetPx
                scaleX = visualScale
                scaleY = visualScale
                alpha = rowAlpha
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .then(blurModifier)
            .combinedClickable(
                enabled = tapSeekEnabled || longPressShareEnabled,
                onClick = { if (tapSeekEnabled) onClick() },
                onLongClick = { if (longPressShareEnabled) onLongClick() },
            )
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        if (showRomanization) {
            MeloXRubyLyricText(
                line = line,
                playbackTimeProvider = playbackTimeProvider,
                supportsTimedLyrics = supportsTimedLyrics,
                fontScale = fontScale,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            MeloXGlyphLyricText(
                line = line,
                playbackTimeProvider = playbackTimeProvider,
                supportsTimedLyrics = supportsTimedLyrics,
                fontScale = fontScale,
                reduceMotion = reduceMotion,
                timingEffectsStrength = focusProgress,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // When the user enables translation, every source line that has a
        // translation keeps it directly underneath. Keeping annotations resident
        // also prevents focus changes from reflowing the scroll geometry.
        val romanSize = max(UpstreamLyrics.FONT_SIZE_SP * fontScale * MeloXSettingsRuntime.lyricRomanizationFontScale, 13f)
        if (!showRomanization && reserveRomanization) {
            val romanHeight = with(LocalDensity.current) { (romanSize * 1.2f).sp.toDp() }
            Spacer(Modifier.height(romanHeight + UpstreamLyrics.ANNOTATION_SPACING_DP.dp))
        }

        val translationSize = max(UpstreamLyrics.FONT_SIZE_SP * fontScale * MeloXSettingsRuntime.lyricTranslationFontScale, 13f)
        if (showTranslation) {
            Text(
                text = line.translation.orEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = UpstreamLyrics.ANNOTATION_SPACING_DP.dp),
                color = Color.White.copy(alpha = MeloXSettingsRuntime.lyricTranslationOpacity),
                textAlign = TextAlign.Start,
                fontSize = max(
                    UpstreamLyrics.FONT_SIZE_SP * fontScale * MeloXSettingsRuntime.lyricTranslationFontScale,
                    13f,
                ).sp,
                lineHeight = max(
                    UpstreamLyrics.FONT_SIZE_SP * fontScale * MeloXSettingsRuntime.lyricTranslationFontScale,
                    13f,
                ).sp * 1.2f,
                fontWeight = MeloXSettingsRuntime.lyricFontWeight.composeWeight,
            )
        } else if (reserveTranslation) {
            val translationHeight = with(LocalDensity.current) { (translationSize * 1.2f).sp.toDp() }
            Spacer(Modifier.height(translationHeight + UpstreamLyrics.ANNOTATION_SPACING_DP.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeloXRubyLyricText(
    line: LyricLine,
    playbackTimeProvider: () -> Long,
    supportsTimedLyrics: Boolean,
    fontScale: Float,
    modifier: Modifier = Modifier,
) {
    val units = remember(line) { LyricRomanizationAligner.units(line) }
    if (units.isEmpty()) {
        MeloXGlyphLyricText(
            line = line,
            playbackTimeProvider = playbackTimeProvider,
            supportsTimedLyrics = supportsTimedLyrics,
            fontScale = fontScale,
            reduceMotion = false,
            timingEffectsStrength = 1f,
            modifier = modifier,
        )
        return
    }

    val primarySize = UpstreamLyrics.FONT_SIZE_SP * fontScale
    val rubySize = max(primarySize * MeloXSettingsRuntime.lyricRomanizationFontScale, 13f)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
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
                )
                if (!unit.romanizationText.isNullOrBlank()) {
                    val originalUnits = unit.originalText.codePointCount(0, unit.originalText.length).coerceAtLeast(1)
                    val rubyUnits = unit.romanizationText.codePointCount(0, unit.romanizationText.length).coerceAtLeast(1)
                    // Match upstream ruby cells: a long phonetic annotation may
                    // compress inside its base glyph cell instead of forcing an
                    // otherwise short lyric unit onto its own wrapped row.
                    val rubyCompression = (originalUnits * 1.85f / rubyUnits).coerceIn(.68f, 1f)
                    Text(
                        text = unit.romanizationText,
                        color = Color.White.copy(alpha = MeloXSettingsRuntime.lyricRomanizationOpacity),
                        fontSize = (rubySize * rubyCompression).sp,
                        lineHeight = (rubySize * 1.2f).sp,
                        fontWeight = MeloXSettingsRuntime.lyricFontWeight.composeWeight,
                        maxLines = 1,
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
)

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
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxWidth(0.28f).height(48.dp)) {
        val now = playbackTimeProvider()
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
    val songUrl = state.mediaId?.let { "https://music.163.com/song?id=$it" }.orEmpty()
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

/**
 * Compose counterpart of upstream LyricGlowTextRenderer.
 *
 * Layout is measured once at the promoted line geometry. Timing effects are
 * applied only during Canvas drawing using TextLayoutResult glyph paths. This is
 * the important invariant from the SwiftUI TextRenderer implementation: lift,
 * long-tone expansion and glow never reflow the line or move the focus anchor.
 */
@Composable
private fun MeloXGlyphLyricText(
    line: LyricLine,
    playbackTimeProvider: () -> Long,
    supportsTimedLyrics: Boolean,
    fontScale: Float,
    reduceMotion: Boolean,
    timingEffectsStrength: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val style = TextStyle(
            color = Color.White,
            fontSize = (UpstreamLyrics.FONT_SIZE_SP * fontScale).sp,
            lineHeight = (UpstreamLyrics.LINE_HEIGHT_SP * fontScale).sp,
            fontWeight = MeloXSettingsRuntime.lyricFontWeight.composeWeight,
        )
        val layout = remember(line.text, widthPx, style) {
            textMeasurer.measure(
                text = AnnotatedString(line.text),
                style = style,
                constraints = Constraints(maxWidth = widthPx),
                softWrap = true,
            )
        }
        val height = with(density) { layout.size.height.toDp() }

        Canvas(Modifier.fillMaxWidth().height(height)) {
            val playbackTimeMs = playbackTimeProvider()
            val effectsStrength = sourceTimingEffectsStrength(
                line = line,
                playbackTimeMs = playbackTimeMs,
                focusProgress = timingEffectsStrength,
            )
            if (!supportsTimedLyrics || effectsStrength <= 0.0001f || line.text.isEmpty()) {
                // drawText keeps Compose/Android's normal shaping and font fallback,
                // including Japanese/CJK/emoji fallback fonts. This is also the
                // zero-strength state of MeloX's timed renderer, not a second
                // competing text path with different opacity.
                drawText(layout, color = Color.White)
                return@Canvas
            }

            // Reading the frame clock state inside the draw phase invalidates this
            // Canvas only. It avoids recomposing or relaying out the lyric row.
            val visuals = sourceGlyphVisuals(
                line = line,
                playbackTimeMs = playbackTimeMs,
                density = density.density,
                reduceMotion = reduceMotion,
                fontScale = fontScale,
            )

            // Render each timed character by clipping the *normally shaped full
            // TextLayoutResult*. This preserves fallback glyphs. getPathForRange()
            // is a selection/range enclosure path, not a glyph-outline API.
            for (offset in line.text.indices) {
                val ch = line.text[offset]
                if (ch == '\n' || ch == '\r' || Character.isLowSurrogate(ch)) continue
                val bounds = runCatching { layout.getBoundingBox(offset) }.getOrNull() ?: continue
                if (!bounds.width.isFinite() || !bounds.height.isFinite() || bounds.width <= 0f || bounds.height <= 0f) continue
                val fx = visuals.getOrElse(offset) { MeloXGlyphVisual(0f, 0f, 1f, 0f) }

                withTransform({
                    translate(left = 0f, top = -fx.liftPx * effectsStrength)
                    val presentationScale = 1f + (fx.scale - 1f) * effectsStrength
                    scale(
                        scaleX = presentationScale,
                        scaleY = presentationScale,
                        pivot = bounds.center,
                    )
                }) {
                    clipRect(
                        left = bounds.left,
                        top = bounds.top,
                        right = bounds.right,
                        bottom = bounds.bottom,
                    ) {
                        // Upstream draws a complete unplayed layer first.
                        drawText(
                            layout,
                            color = Color.White.copy(
                                alpha = 1f -
                                    (1f - MeloXSettingsRuntime.lyricInactiveOpacity) * effectsStrength,
                            ),
                        )

                        val reveal = fx.reveal.coerceIn(0f, 1f)
                        if (reveal <= 0f) return@clipRect

                        val feather = max(
                            bounds.width * MeloXSettingsRuntime.lyricHighlightGradientWidth,
                            1.5f * density.density,
                        )
                        val front = bounds.left - feather + (bounds.width + feather) * reveal
                        val solidRight = min(front, bounds.right)

                        fun drawRevealed(alpha: Float) {
                            if (solidRight > bounds.left) {
                                clipRect(
                                    left = bounds.left,
                                    top = bounds.top,
                                    right = solidRight,
                                    bottom = bounds.bottom,
                                ) {
                                    drawText(layout, color = Color.White.copy(alpha = alpha))
                                }
                            }

                            val stopCount = 8
                            for (step in 0 until stopCount) {
                                val a = step.toFloat() / stopCount.toFloat()
                                val b = (step + 1).toFloat() / stopCount.toFloat()
                                val mid = (a + b) * .5f
                                val remaining = 1f - mid
                                val maskAlpha = remaining *
                                    (1f - MeloXSettingsRuntime.lyricHighlightGradientReduction * mid)
                                val left = max(front + feather * a, bounds.left)
                                val right = min(front + feather * b, bounds.right)
                                if (right > left) {
                                    clipRect(
                                        left = left,
                                        top = bounds.top,
                                        right = right,
                                        bottom = bounds.bottom,
                                    ) {
                                        drawText(
                                            layout,
                                            color = Color.White.copy(
                                                alpha = alpha * maskAlpha.coerceIn(0f, 1f),
                                            ),
                                        )
                                    }
                                }
                            }
                        }

                        // Keep the upstream long-tone envelope without relying on
                        // vector glyph extraction. Two low-opacity passes provide
                        // the bloom while the final pass is the actual played text.
                        val glow = fx.glow * effectsStrength * MeloXSettingsRuntime.lyricGlowStrength
                        if (glow > 0.001f) {
                            drawRevealed((glow * .10f).coerceIn(0f, .24f))
                            drawRevealed((glow * .18f).coerceIn(0f, .36f))
                        }
                        drawRevealed(1f)
                    }
                }
            }
        }
    }
}

private fun sourceGlyphVisuals(
    line: LyricLine,
    playbackTimeMs: Long,
    density: Float,
    reduceMotion: Boolean,
    fontScale: Float,
): List<MeloXGlyphVisual> {
    val result = MutableList(line.text.length) {
        MeloXGlyphVisual(0f, 0f, 1f, 0f)
    }
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
            val duration = max(end - start, 0f)
            val raw = when {
                playbackTimeMs < start -> 0f
                playbackTimeMs >= end -> 1f
                duration <= 0f -> 1f
                else -> ((playbackTimeMs - start) / duration).coerceIn(0f, 1f)
            }
            val char = line.text[offset]
            val longToneDuration = if (MeloXSettingsRuntime.lyricLongToneDetectionMode == MeloXLyricsGroupingMode.Word) {
                syllableDuration
            } else {
                duration
            }
            val longTone = longToneDuration >= MeloXSettingsRuntime.lyricLongToneThresholdMs && !char.isWhitespace()
            val reveal = sourceHighlightRevealProgress(playbackTimeMs.toFloat(), start, end, raw, longTone)
            val liftStart = if (MeloXSettingsRuntime.lyricLiftMode == MeloXLyricsGroupingMode.Word) syllable.startTimeMs.toFloat() else start
            val liftBaseEnd = if (MeloXSettingsRuntime.lyricLiftMode == MeloXLyricsGroupingMode.Word) syllable.endTimeMs.toFloat() else end
            val liftEnd = liftBaseEnd + UpstreamLyrics.LIFT_CONTINUATION_MS
            val lift = if (playbackTimeMs <= liftStart) 0f else sourceSmootherStep(
                ((playbackTimeMs - liftStart) / max(liftEnd - liftStart, 1f)).toFloat(),
            )
            val risePx = if (reduceMotion) 0f else
                min(max(UpstreamLyrics.FONT_SIZE_SP * fontScale * .1f, 1.5f), 6f) * density
            val envelope = if (longTone) sourceLongToneEnvelope(
                playbackTimeMs.toFloat(), syllable.startTimeMs.toFloat(), syllableDuration, local, count,
            ) else 0f
            val expansionAmount = if (longTone) {
                .7f + .3f * sourceSmootherStep(
                    (syllableDuration - UpstreamLyrics.LONG_TONE_THRESHOLD_MS) /
                        (2800f - UpstreamLyrics.LONG_TONE_THRESHOLD_MS),
                )
            } else 0f
            val scale = if (reduceMotion) 1f else
                1f + (UpstreamLyrics.LONG_TONE_MAX_SCALE - 1f) * envelope * expansionAmount *
                    MeloXSettingsRuntime.lyricLongToneStrength
            val glowAmount = if (longTone) .32f + .38f * sourceSmootherStep(
                (syllableDuration - UpstreamLyrics.LONG_TONE_THRESHOLD_MS) /
                    (2800f - UpstreamLyrics.LONG_TONE_THRESHOLD_MS),
            ) else 0f
            result[offset] = MeloXGlyphVisual(
                reveal = reveal,
                liftPx = risePx * lift,
                scale = scale,
                glow = if (reduceMotion || !MeloXSettingsRuntime.lyricGlowEnabled ||
                    (MeloXSettingsRuntime.lyricGlowLongTonesOnly && !longTone)
                ) 0f else {
                    if (longTone) envelope * glowAmount else reveal * .22f
                },
            )
        }
    }
    return result
}

/**
 * MeloX feeds focus-colour presentation progress into LyricGlowTextRenderer so
 * its unplayed opacity, lift, scale and glow enter as one transition. Some YRC
 * files place the line timestamp slightly before the first syllable timestamp;
 * delaying presentation strength until that syllable begins prevents a fully
 * unplayed line from dimming during this metadata gap.
 */
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
                    playbackTimeMs.toFloat(),
                    start,
                    end,
                    rawProgress,
                    isLongTone,
                )
                val liftEnd = end + UpstreamLyrics.LIFT_CONTINUATION_MS
                val liftProgress = if (playbackTimeMs <= start) 0f else {
                    sourceSmootherStep(
                        ((playbackTimeMs - start) / max(liftEnd - start, 1f)).toFloat(),
                    )
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
                                color = Color.White.copy(alpha = (glowStrength * 0.55f).coerceIn(0f, 1f)),
                                blurRadius = UpstreamLyrics.FONT_SIZE_SP * 0.2f,
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

private fun sourceHighlightedIndex(lines: List<LyricLine>, playbackTimeMs: Long): Int {
    if (lines.isEmpty()) return -1
    var lower = 0
    var upper = lines.size
    while (lower < upper) {
        val middle = lower + (upper - lower) / 2
        if (sourceLineActivationTimeMs(lines[middle]) <= playbackTimeMs) lower = middle + 1 else upper = middle
    }
    return lower - 1
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

private fun sourceBottomRevealOpacity(
    frameMinY: Float,
    movementOffset: Float,
    frameHeight: Float,
    viewportHeight: Float,
): Float {
    val visualMinY = frameMinY + movementOffset
    val revealDistance = min(max(frameHeight * 0.8f, 32f), 72f)
    return ((viewportHeight - visualMinY) / revealDistance).coerceIn(0f, 1f)
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

/**
 * SwiftUI's .spring(duration:bounce:) has no byte-for-byte Compose equivalent.
 * This easing preserves MeloX's source duration and bounce parameter and uses a
 * normalized under-damped response so the visible overshoot/settling schedule
 * remains tied to the upstream values instead of arbitrary Compose stiffness.
 */
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
