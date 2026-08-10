package com.lladlam.melox.ui.player

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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

    // Manual browsing suspends playback following and resumes after the same
    // 3-second default delay as AppSettings.lyricsFollowDelay upstream.
    LaunchedEffect(scrollState, document) {
        snapshotFlow { scrollState.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling && !automaticScroll) {
                isBrowsingLyrics = true
            } else if (!scrolling && isBrowsingLyrics && !automaticScroll) {
                delay(UpstreamLyrics.FOLLOW_DELAY_MS)
                isBrowsingLyrics = false
                playbackFocusGeneration += 1
            }
        }
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
            scrollState.scrollTo(targetScroll)
            movementOffsets.forEachIndexed { index, anim ->
                anim.snapTo(movementDistance + carried[index])
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
                        .verticalScroll(scrollState),
                ) {
                    Spacer(Modifier.height(with(density) { topPaddingPx.toDp() }))
                    lines.forEachIndexed { index, line ->
                        val height = estimatedHeight(index)
                        val visualOffset = movementOffsets[index].value
                        val frameMinY = rowContentTop(index, topPaddingPx) - scrollValue
                        val visualMidY = frameMinY + visualOffset + height * 0.5f
                        val distance = abs(visualMidY - focusAnchorY)
                        val fp = focusProgress[index].value.coerceIn(0f, 1f)
                        val distanceBlur = sourceDistanceBlurRadius(
                            distancePx = distance,
                            lyricStridePx = lyricStridePx,
                            intensity = UpstreamLyrics.BLUR_INTENSITY * UpstreamLyrics.DISTANCE_BLUR_SCALE,
                            focusProgress = fp,
                        )
                        val preceding = index == visualFocusIndex - 1
                        val following = index == visualFocusIndex + 1
                        val focusBlur = sourceFocusBlurRadius(
                            UpstreamLyrics.BLUR_INTENSITY,
                            preceding,
                            following,
                        )
                        val distanceOpacity = sourceDistanceOpacity(
                            distance,
                            lyricStridePx,
                            UpstreamLyrics.DIM_AMOUNT,
                            fp,
                        )
                        val emphasis = sourceEmphasis(fp, UpstreamLyrics.DIM_AMOUNT)
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
                            timed = hasSyllableSync && fp > 0.0001f,
                            focusProgress = fp,
                            visualScale = scale,
                            visualOffsetPx = visualOffset,
                            rowAlpha = rowAlpha,
                            distanceBlurDp = distanceBlur,
                            focusBlurDp = focusBlur,
                            showTranslation = MeloXSettingsRuntime.showLyricTranslation &&
                                !line.translation.isNullOrBlank() &&
                                index == visualFocusIndex,
                            showRomanization = MeloXSettingsRuntime.showLyricRomanization &&
                                !line.romanization.isNullOrBlank(),
                            onMeasured = { measured ->
                                if (measured > 0 && rowHeightsPx[index] != measured) {
                                    rowHeightsPx[index] = measured
                                    layoutRevision += 1
                                }
                            },
                            onClick = { state.seekTo(line.timeMs) },
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
        Text(
            text = if (timed && line.syllables.isNotEmpty()) {
                sourceTimedAnnotatedString(line, positionMs)
            } else {
                AnnotatedString(line.text)
            },
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            textAlign = TextAlign.Start,
            fontSize = UpstreamLyrics.FONT_SIZE_SP.sp,
            lineHeight = UpstreamLyrics.LINE_HEIGHT_SP.sp,
            fontWeight = FontWeight.Black,
        )

        // Romanization defaults to all-lines upstream; translation defaults to
        // focused-line. Hidden translation is accounted for in promoted layout
        // estimation so focus changes do not reflow the scroll geometry.
        if (showRomanization) {
            Text(
                text = line.romanization.orEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = UpstreamLyrics.ANNOTATION_SPACING_DP.dp),
                color = Color.White.copy(alpha = UpstreamLyrics.ANNOTATION_OPACITY),
                textAlign = TextAlign.Start,
                fontSize = max(
                    UpstreamLyrics.FONT_SIZE_SP * UpstreamLyrics.ROMANIZATION_FONT_SCALE,
                    13f,
                ).sp,
                lineHeight = max(
                    UpstreamLyrics.FONT_SIZE_SP * UpstreamLyrics.ROMANIZATION_FONT_SCALE,
                    13f,
                ).sp * 1.2f,
                fontWeight = FontWeight.Black,
            )
        }

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
        }
    }
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
