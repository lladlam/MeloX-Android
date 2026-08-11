package com.lladlam.melox.ui.player

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
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
 * intentionally kept numerically identical to upstream MeloX.
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

    const val CASCADE_DELAY_MS = 21f
    const val CASCADE_DELAY_INCREASE_MS = 5f
    const val CASCADE_FOLLOWING_DELAY_MS = 30f
    const val CASCADE_CATCH_UP_RATIO = 0.97f
    const val CASCADE_CHASE_SPEED_GRADIENT = 0.70f
    const val CASCADE_DURATION_MS = 740f
    const val CASCADE_SNAP_THRESHOLD_MS = 260f
    const val CASCADE_BOUNCE = 0.26f
    const val CASCADE_BOUNCE_GRADIENT = 0.85f
    const val SCALE_BOUNCE = 0.32f
    const val SCALE_BOUNCE_DURATION_MS = 580

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

    const val FOLLOW_DELAY_MS = 3000L
    const val NON_YRC_ADVANCE_MS = 200L
}

@Composable
fun MeloXIOSLyricsPanel(
    state: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
    isInterfaceHidden: Boolean = false,
    onInterfaceInteraction: () -> Unit = {},
    onInterfaceVisibilityChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current.applicationContext
    val client = remember(context) {
        NeteaseSearchClient(
            cookieProvider = { NeteaseSessionStore.readCookie(context) },
        )
    }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
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

    // SwiftUI TimelineView(.animation) tracks the display clock. 16 ms keeps the
    // Android presentation on the same 60 Hz minimum cadence used by upstream.
    LaunchedEffect(state.isPlaying, mediaId) {
        while (true) {
            renderedPositionMs = if (state.isPlaying) {
                anchorPositionMs + (SystemClock.elapsedRealtime() - anchorRealtimeMs)
            } else {
                anchorPositionMs
            }
            delay(if (state.isPlaying) 16L else 200L)
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
    val lines = document?.lines.orEmpty()
    val hasSyllableSync = remember(document) { lines.any { it.syllables.isNotEmpty() } }
    val effectivePositionMs = renderedPositionMs + if (hasSyllableSync) 0L else UpstreamLyrics.NON_YRC_ADVANCE_MS
    val highlightedIndex = remember(lines, effectivePositionMs) {
        sourceHighlightedIndex(lines, effectivePositionMs)
    }

    val movementOffsets = remember(document) {
        List(lines.size) { Animatable(0f) }
    }
    val focusProgress = remember(document) {
        List(lines.size) { Animatable(0f) }
    }
    val scaleProgress = remember(document) {
        List(lines.size) { Animatable(0f) }
    }
    val rowHeightsPx = remember(document) { mutableStateMapOf<Int, Int>() }
    var layoutRevision by remember(document) { mutableIntStateOf(0) }
    var viewportHeightPx by remember(document) { mutableIntStateOf(0) }
    var visualFocusIndex by remember(document) { mutableIntStateOf(-1) }
    var isBrowsingLyrics by remember(document) { mutableStateOf(false) }
    var automaticScroll by remember(document) { mutableStateOf(false) }
    var playbackFocusGeneration by remember(document) { mutableIntStateOf(0) }
    var browseGeneration by remember(document) { mutableIntStateOf(0) }
    var scrollHideDistancePx by remember(document) { mutableStateOf(0f) }
    val latestInterfaceHidden = rememberUpdatedState(isInterfaceHidden)
    val latestVisibilityCallback = rememberUpdatedState(onInterfaceVisibilityChange)
    val latestInteractionCallback = rememberUpdatedState(onInterfaceInteraction)

    val lineSpacingPx = with(density) { UpstreamLyrics.LINE_SPACING_DP.dp.toPx() }
    val primaryHeightPx = with(density) { UpstreamLyrics.LINE_HEIGHT_SP.sp.toPx() }
    val annotationFontPx = with(density) {
        max(UpstreamLyrics.FONT_SIZE_SP * UpstreamLyrics.ROMANIZATION_FONT_SCALE, 13f).sp.toPx()
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

    fun rowContentTop(index: Int, topPaddingPx: Float): Float {
        var result = topPaddingPx
        for (i in 0 until index) {
            result += estimatedHeight(i) + lineSpacingPx
        }
        return result
    }

    fun focusedFollowingOffset(index: Int, focusIndex: Int): Float {
        if (focusIndex !in lines.indices || index <= focusIndex) return 0f
        return max(estimatedHeight(focusIndex) * (UpstreamLyrics.CURRENT_LINE_SCALE - 1f), 0f)
    }

    suspend fun handOffFocusColor(nextIndex: Int) = coroutineScope {
        focusProgress.forEachIndexed { index, anim ->
            val target = if (index == nextIndex) 1f else 0f
            if (abs(anim.value - target) > 0.0001f) {
                launch {
                    anim.animateTo(
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
        if (previousIndex in scaleProgress.indices && previousIndex != nextIndex) {
            launch {
                scaleProgress[previousIndex].animateTo(
                    0f,
                    tween(
                        durationMillis = UpstreamLyrics.SCALE_BOUNCE_DURATION_MS,
                        easing = SourceSmoothStepEasing,
                    ),
                )
            }
        }
        if (nextIndex in scaleProgress.indices) {
            launch {
                scaleProgress[nextIndex].animateTo(
                    1f,
                    tween(
                        durationMillis = UpstreamLyrics.SCALE_BOUNCE_DURATION_MS,
                        easing = SourceSpringEasing(UpstreamLyrics.SCALE_BOUNCE),
                    ),
                )
            }
        }
    }

    // Only real pointer/nested-scroll input enters browsing mode. Programmatic
    // scrollTo/animateScrollTo must never disable its own lyric following.
    val scrollHideThresholdPx = with(density) { 200.dp.toPx() }
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
        delay(UpstreamLyrics.FOLLOW_DELAY_MS)
        isBrowsingLyrics = false
        playbackFocusGeneration += 1
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeightPx = it.height },
    ) {
        val topPaddingPx = viewportHeightPx * UpstreamLyrics.FOCUS_POSITION
        val bottomPaddingPx = max(
            viewportHeightPx * (1f - UpstreamLyrics.FOCUS_POSITION),
            with(density) { 40.dp.toPx() },
        )

        fun targetScrollFor(index: Int): Int {
            if (index !in lines.indices || viewportHeightPx <= 0) return scrollState.value
            val rowTop = rowContentTop(index, topPaddingPx)
            val rowHeight = estimatedHeight(index)
            val viewportAnchor = viewportHeightPx * UpstreamLyrics.FOCUS_POSITION
            return (rowTop + rowHeight * UpstreamLyrics.FOCUS_POSITION - viewportAnchor)
                .roundToInt()
                .coerceIn(0, scrollState.maxValue.coerceAtLeast(0))
        }

        LaunchedEffect(
            highlightedIndex,
            playbackFocusGeneration,
            viewportHeightPx,
            layoutRevision,
            document,
        ) {
            val nextIndex = highlightedIndex
            if (nextIndex !in lines.indices || viewportHeightPx <= 0 || isBrowsingLyrics) {
                if (isBrowsingLyrics && nextIndex in lines.indices) {
                    handOffFocusColor(nextIndex)
                    visualFocusIndex = nextIndex
                }
                return@LaunchedEffect
            }

            val previousIndex = visualFocusIndex
            val targetScroll = targetScrollFor(nextIndex)

            if (previousIndex !in lines.indices) {
                automaticScroll = true
                scrollState.scrollTo(targetScroll)
                movementOffsets.forEachIndexed { index, anim ->
                    anim.snapTo(focusedFollowingOffset(index, nextIndex))
                }
                focusProgress.forEachIndexed { index, anim ->
                    anim.snapTo(if (index == nextIndex) 1f else 0f)
                }
                scaleProgress.forEachIndexed { index, anim ->
                    anim.snapTo(if (index == nextIndex) 1f else 0f)
                }
                visualFocusIndex = nextIndex
                automaticScroll = false
                return@LaunchedEffect
            }

            if (previousIndex == nextIndex) {
                // Geometry may have changed after annotations were measured.
                if (abs(scrollState.value - targetScroll) > 2) {
                    automaticScroll = true
                    scrollState.scrollTo(targetScroll)
                    automaticScroll = false
                }
                return@LaunchedEffect
            }

            scope.launch { handOffFocusColor(nextIndex) }
            scope.launch { handOffFocusScale(previousIndex, nextIndex) }

            val baseDurationMs = sourceFocusAnimationDurationMs(nextIndex, lines)
            val isAdjacentForward = nextIndex == previousIndex + 1
            val remainingMs = sourceRemainingFocusDurationMs(nextIndex, effectivePositionMs, lines)
            val oldScroll = scrollState.value
            val movementDistance = (targetScroll - oldScroll).toFloat()

            if (!isAdjacentForward) {
                automaticScroll = true
                scrollState.animateScrollTo(
                    targetScroll,
                    tween(baseDurationMs, easing = SourceSmoothStepEasing),
                )
                coroutineScope {
                    movementOffsets.forEachIndexed { index, anim ->
                        launch {
                            anim.animateTo(
                                focusedFollowingOffset(index, nextIndex),
                                tween(baseDurationMs, easing = SourceSmoothStepEasing),
                            )
                        }
                    }
                }
                visualFocusIndex = nextIndex
                automaticScroll = false
                return@LaunchedEffect
            }

            val fullCascadeMs = max(baseDurationMs.toFloat(), UpstreamLyrics.CASCADE_DURATION_MS)
            val availableMs = remainingMs?.coerceAtLeast(0f)
            val cascadeDurationMs = if (availableMs == null) {
                fullCascadeMs
            } else {
                if (availableMs < UpstreamLyrics.CASCADE_SNAP_THRESHOLD_MS) 0f
                else min(fullCascadeMs, availableMs)
            }

            automaticScroll = true
            // Same preparation used by AppleMusicLyricsView: put the scroll view at
            // its destination without animation and carry every row by the inverse
            // movement distance, then release rows through the cascade.
            val carried = movementOffsets.map { it.value }
            scrollState.animateScrollTo(
                targetScroll,
                tween(
                    durationMillis = max(cascadeDurationMs, 1f).roundToInt(),
                    easing = SourceSmoothStepEasing,
                ),
            )
            movementOffsets.forEachIndexed { index, anim ->
                anim.snapTo(carried[index])
            }
            visualFocusIndex = nextIndex

            if (cascadeDurationMs <= 0f || abs(movementDistance) <= 0.5f) {
                movementOffsets.forEachIndexed { index, anim ->
                    anim.snapTo(focusedFollowingOffset(index, nextIndex))
                }
                automaticScroll = false
                return@LaunchedEffect
            }

            val firstMoving = max(nextIndex - 1, 0)
            val lastMoving = min(nextIndex + 8, lines.lastIndex) // 6 safety + 2 preload
            val maxChaseOrder = max(lastMoving - firstMoving, 0)
            val lineTimings = sourceCascadeLineTimings(
                maximumLineOrder = max(lastMoving - nextIndex, 0),
                animationDurationMs = cascadeDurationMs,
            )
            val slowestDuration = lineTimings.firstOrNull()?.durationMs ?: cascadeDurationMs

            coroutineScope {
                movementOffsets.forEachIndexed { index, anim ->
                    val destination = focusedFollowingOffset(index, nextIndex)
                    if (index !in firstMoving..lastMoving) {
                        launch { anim.snapTo(destination) }
                        return@forEachIndexed
                    }
                    val movementOrder = max(index - nextIndex, 0)
                    val chaseOrder = max(index - firstMoving, 0)
                    val movementTiming = lineTimings[min(movementOrder, lineTimings.lastIndex)]
                    val chaseTiming = lineTimings[min(chaseOrder, lineTimings.lastIndex)]
                    val duration = slowestDuration +
                        (chaseTiming.durationMs - slowestDuration) * UpstreamLyrics.CASCADE_CHASE_SPEED_GRADIENT
                    val bounce = sourceCascadeBounce(chaseOrder, maxChaseOrder)
                    launch {
                        delay(movementTiming.delayMs.toLong())
                        anim.animateTo(
                            destination,
                            tween(
                                durationMillis = max(duration, 1f).roundToInt(),
                                easing = SourceSpringEasing(bounce),
                            ),
                        )
                    }
                }
            }
            automaticScroll = false
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
                val scrollValue = scrollState.value.toFloat()
                val focusAnchorY = viewportHeightPx * UpstreamLyrics.FOCUS_POSITION
                val annotationHeightPx =
                    annotationFontPx * 1.2f * 2f + annotationSpacingPx * 2f
                val lyricStridePx = max(primaryHeightPx + annotationHeightPx + lineSpacingPx, 1f)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(lyricInteractionConnection)
                        .verticalScroll(scrollState),
                ) {
                    Spacer(Modifier.height(with(density) { topPaddingPx.toDp() }))
                    lines.forEachIndexed { index, line ->
                        val height = estimatedHeight(index)
                        val visualOffset = 0f
                        val frameMinY = rowContentTop(index, topPaddingPx) - scrollValue
                        val visualMidY = frameMinY + visualOffset + height * 0.5f
                        val distance = abs(visualMidY - focusAnchorY)
                        val fp = focusProgress[index].value.coerceIn(0f, 1f)
                        // The incoming line starts the 120 ms colour handoff before
                        // its timestamp, then keeps full emphasis until the normal
                        // focus Animatable catches up. This prevents the one-frame
                        // dark trough that used to happen at sentence boundaries.
                        val incomingLead = when {
                            index == highlightedIndex + 1 -> sourceSmootherStep(
                                ((effectivePositionMs - (line.timeMs - UpstreamLyrics.FOCUS_COLOR_DURATION_MS)) /
                                    UpstreamLyrics.FOCUS_COLOR_DURATION_MS.toFloat()).coerceIn(0f, 1f),
                            )
                            index == highlightedIndex &&
                                effectivePositionMs - line.timeMs in 0..UpstreamLyrics.FOCUS_COLOR_DURATION_MS.toLong() -> 1f
                            else -> 0f
                        }
                        val effectiveFocus = max(fp, incomingLead)
                        val distanceBlur = sourceDistanceBlurRadius(
                            distancePx = distance,
                            lyricStridePx = lyricStridePx,
                            intensity = UpstreamLyrics.BLUR_INTENSITY * UpstreamLyrics.DISTANCE_BLUR_SCALE,
                            focusProgress = effectiveFocus,
                        )
                        val preceding = index == visualFocusIndex - 1
                        val following = index == visualFocusIndex + 1
                        val focusBlur = sourceFocusBlurRadius(
                            UpstreamLyrics.BLUR_INTENSITY,
                            preceding,
                            following,
                        ) * (1f - effectiveFocus)
                        val distanceOpacity = sourceDistanceOpacity(
                            distance,
                            lyricStridePx,
                            UpstreamLyrics.DIM_AMOUNT,
                            effectiveFocus,
                        )
                        val emphasis = sourceEmphasis(effectiveFocus, UpstreamLyrics.DIM_AMOUNT)
                        val reveal = sourceBottomRevealOpacity(
                            frameMinY = frameMinY,
                            movementOffset = visualOffset,
                            frameHeight = height,
                            viewportHeight = viewportHeightPx.toFloat(),
                        )
                        val rowAlpha = (distanceOpacity * emphasis * reveal).coerceIn(0f, 1f)
                        val scale = 1f +
                            (UpstreamLyrics.CURRENT_LINE_SCALE - 1f) * scaleProgress[index].value

                        MeloXUpstreamLyricLine(
                            line = line,
                            positionMs = renderedPositionMs,
                            timed = hasSyllableSync && index == highlightedIndex,
                            focusProgress = effectiveFocus,
                            visualScale = scale,
                            visualOffsetPx = visualOffset,
                            rowAlpha = rowAlpha,
                            distanceBlurDp = distanceBlur,
                            focusBlurDp = focusBlur,
                            showTranslation = MeloXSettingsRuntime.showLyricTranslation &&
                                !line.translation.isNullOrBlank(),
                            showRomanization = MeloXSettingsRuntime.showLyricRomanization &&
                                !line.romanization.isNullOrBlank(),
                            reserveTranslation = MeloXSettingsRuntime.showLyricTranslation && !line.translation.isNullOrBlank(),
                            reserveRomanization = MeloXSettingsRuntime.showLyricRomanization && !line.romanization.isNullOrBlank(),
                            onMeasured = { measured ->
                                if (measured > 0 && rowHeightsPx[index] != measured) {
                                    rowHeightsPx[index] = measured
                                    layoutRevision += 1
                                }
                            },
                            onClick = { onInterfaceInteraction(); state.seekTo(line.timeMs) },
                        )

                        if (index != lines.lastIndex) {
                            Spacer(Modifier.height(UpstreamLyrics.LINE_SPACING_DP.dp))
                        }
                    }
                    Spacer(Modifier.height(with(density) { bottomPaddingPx.toDp() }))
                }
            }
        }
    }
}

@Composable
private fun MeloXUpstreamLyricLine(
    line: LyricLine,
    positionMs: Long,
    timed: Boolean,
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
    onMeasured: (Int) -> Unit,
    onClick: () -> Unit,
) {
    val blurModifier = Modifier
        .blur(
            radius = max(distanceBlurDp, 0f).dp,
            edgeTreatment = BlurredEdgeTreatment.Unbounded,
        )
        .blur(
            radius = max(focusBlurDp, 0f).dp,
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
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        MeloXGlyphLyricText(
            line = line,
            playbackTimeMs = positionMs,
            timed = timed && line.syllables.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )

        // When the user enables translation, every source line that has a
        // translation keeps it directly underneath. Keeping annotations resident
        // also prevents focus changes from reflowing the scroll geometry.
        val romanSize = max(UpstreamLyrics.FONT_SIZE_SP * UpstreamLyrics.ROMANIZATION_FONT_SCALE, 13f)
        if (showRomanization) {
            Text(
                text = line.romanization.orEmpty(),
                modifier = Modifier.fillMaxWidth().padding(top = UpstreamLyrics.ANNOTATION_SPACING_DP.dp),
                color = Color.White.copy(alpha = UpstreamLyrics.ANNOTATION_OPACITY),
                textAlign = TextAlign.Start, fontSize = romanSize.sp, lineHeight = (romanSize * 1.2f).sp, fontWeight = FontWeight.Black,
            )
        } else if (reserveRomanization) {
            Spacer(Modifier.height((romanSize * 1.2f).dp + UpstreamLyrics.ANNOTATION_SPACING_DP.dp))
        }

        val translationSize = max(UpstreamLyrics.FONT_SIZE_SP * UpstreamLyrics.TRANSLATION_FONT_SCALE, 13f)
        if (showTranslation) {
            Text(
                text = line.translation.orEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = UpstreamLyrics.ANNOTATION_SPACING_DP.dp),
                color = Color.White.copy(alpha = UpstreamLyrics.ANNOTATION_OPACITY),
                textAlign = TextAlign.Start,
                fontSize = max(
                    UpstreamLyrics.FONT_SIZE_SP * UpstreamLyrics.TRANSLATION_FONT_SCALE,
                    13f,
                ).sp,
                lineHeight = max(
                    UpstreamLyrics.FONT_SIZE_SP * UpstreamLyrics.TRANSLATION_FONT_SCALE,
                    13f,
                ).sp * 1.2f,
                fontWeight = FontWeight.Black,
            )
        } else if (reserveTranslation) {
            Spacer(Modifier.height((translationSize * 1.2f).dp + UpstreamLyrics.ANNOTATION_SPACING_DP.dp))
        }
    }
}

private data class MeloXGlyphVisual(
    val reveal: Float,
    val liftPx: Float,
    val scale: Float,
    val glow: Float,
)

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
    playbackTimeMs: Long,
    timed: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val style = TextStyle(
            color = Color.White,
            fontSize = UpstreamLyrics.FONT_SIZE_SP.sp,
            lineHeight = UpstreamLyrics.LINE_HEIGHT_SP.sp,
            fontWeight = FontWeight.Black,
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
        val visuals = remember(line, playbackTimeMs, timed, density.density) {
            if (timed) sourceGlyphVisuals(line, playbackTimeMs, density.density)
            else List(line.text.length) { MeloXGlyphVisual(1f, 0f, 1f, 0f) }
        }

        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (!timed || line.text.isEmpty()) {
                // drawText keeps Compose/Android's normal shaping and font fallback,
                // including Japanese/CJK/emoji fallback fonts.
                drawText(layout, color = Color.White)
                return@Canvas
            }

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
                    translate(left = 0f, top = -fx.liftPx)
                    scale(scaleX = fx.scale, scaleY = fx.scale, pivot = bounds.center)
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
                            color = Color.White.copy(alpha = UpstreamLyrics.UNPLAYED_OPACITY),
                        )

                        val reveal = fx.reveal.coerceIn(0f, 1f)
                        if (reveal <= 0f) return@clipRect

                        val feather = max(
                            bounds.width * UpstreamLyrics.HIGHLIGHT_GRADIENT_WIDTH,
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
                                    (1f - UpstreamLyrics.HIGHLIGHT_GRADIENT_REDUCTION * mid)
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
                        if (fx.glow > 0.001f) {
                            drawRevealed((fx.glow * .10f).coerceIn(0f, .24f))
                            drawRevealed((fx.glow * .18f).coerceIn(0f, .36f))
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
            val longTone = syllableDuration >= UpstreamLyrics.LONG_TONE_THRESHOLD_MS && !char.isWhitespace()
            val reveal = sourceHighlightRevealProgress(playbackTimeMs.toFloat(), start, end, raw, longTone)
            val liftEnd = end + UpstreamLyrics.LIFT_CONTINUATION_MS
            val lift = if (playbackTimeMs <= start) 0f else sourceSmootherStep(
                ((playbackTimeMs - start) / max(liftEnd - start, 1f)).toFloat(),
            )
            val risePx = min(max(UpstreamLyrics.FONT_SIZE_SP * .1f, 1.5f), 6f) * density
            val envelope = if (longTone) sourceLongToneEnvelope(
                playbackTimeMs.toFloat(), syllable.startTimeMs.toFloat(), syllableDuration, local, count,
            ) else 0f
            val expansionAmount = if (longTone) {
                .7f + .3f * sourceSmootherStep(
                    (syllableDuration - UpstreamLyrics.LONG_TONE_THRESHOLD_MS) /
                        (2800f - UpstreamLyrics.LONG_TONE_THRESHOLD_MS),
                )
            } else 0f
            val scale = 1f + (UpstreamLyrics.LONG_TONE_MAX_SCALE - 1f) * envelope * expansionAmount
            val glowAmount = if (longTone) .32f + .38f * sourceSmootherStep(
                (syllableDuration - UpstreamLyrics.LONG_TONE_THRESHOLD_MS) /
                    (2800f - UpstreamLyrics.LONG_TONE_THRESHOLD_MS),
            ) else 0f
            result[offset] = MeloXGlyphVisual(
                reveal = reveal,
                liftPx = risePx * lift,
                scale = scale,
                glow = envelope * glowAmount,
            )
        }
    }
    return result
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
                    (UpstreamLyrics.LONG_TONE_MAX_SCALE - 1f) * longEnvelope * expansionAmount
                val glowAmount = if (isLongTone) {
                    0.32f + 0.38f * sourceSmootherStep(
                        (syllableDuration - UpstreamLyrics.LONG_TONE_THRESHOLD_MS) /
                            (2800f - UpstreamLyrics.LONG_TONE_THRESHOLD_MS),
                    )
                } else 0f
                val glowStrength = longEnvelope * glowAmount
                val opacity = UpstreamLyrics.UNPLAYED_OPACITY +
                    (1f - UpstreamLyrics.UNPLAYED_OPACITY) * revealProgress

                val startOffset = length
                append(character)
                addStyle(
                    SpanStyle(
                        color = Color.White.copy(alpha = opacity.coerceIn(0f, 1f)),
                        fontSize = (UpstreamLyrics.FONT_SIZE_SP * glyphScale).sp,
                        fontWeight = FontWeight.Black,
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
        if (lines[middle].timeMs <= playbackTimeMs) lower = middle + 1 else upper = middle
    }
    return lower - 1
}

private fun sourceFocusAnimationDurationMs(index: Int, lines: List<LyricLine>): Int {
    if (index !in lines.indices) return 300
    val available = if (index + 1 < lines.size) {
        lines[index + 1].timeMs - lines[index].timeMs
    } else null
    if (available == null || available <= 0L) return 300
    return (available * 0.35f).coerceIn(50f, 300f).roundToInt()
}

private fun sourceRemainingFocusDurationMs(
    index: Int,
    playbackTimeMs: Long,
    lines: List<LyricLine>,
): Float? {
    if (index !in lines.indices || index + 1 >= lines.size) return null
    return max((lines[index + 1].timeMs - playbackTimeMs).toFloat(), 0f)
}

private data class SourceCascadeLineTiming(val delayMs: Float, val durationMs: Float)

private fun sourceCascadeLineTimings(
    maximumLineOrder: Int,
    animationDurationMs: Float,
): List<SourceCascadeLineTiming> {
    val catchUpCompletionTime = animationDurationMs * UpstreamLyrics.CASCADE_CATCH_UP_RATIO
    val minimumCatchUpDuration = min(180f, animationDurationMs * 0.5f)
    return (0..maximumLineOrder.coerceAtLeast(0)).map { order ->
        if (order == 0) {
            SourceCascadeLineTiming(0f, animationDurationMs)
        } else {
            val accumulatedIncrease = order.toFloat() * (order - 1).toFloat() / 2f
            val delay = UpstreamLyrics.CASCADE_FOLLOWING_DELAY_MS +
                order * UpstreamLyrics.CASCADE_DELAY_MS +
                accumulatedIncrease * UpstreamLyrics.CASCADE_DELAY_INCREASE_MS
            SourceCascadeLineTiming(
                delayMs = delay,
                durationMs = max(catchUpCompletionTime - delay, minimumCatchUpDuration),
            )
        }
    }
}

private fun sourceCascadeBounce(chaseOrder: Int, maximumChaseOrder: Int): Float {
    val count = max(maximumChaseOrder + 1, 1)
    val position = chaseOrder.coerceIn(0, maximumChaseOrder) + 1
    val normalized = position.toFloat() / count.toFloat()
    val bounceScale = 1f - (1f - normalized) * UpstreamLyrics.CASCADE_BOUNCE_GRADIENT
    return UpstreamLyrics.CASCADE_BOUNCE * bounceScale
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
