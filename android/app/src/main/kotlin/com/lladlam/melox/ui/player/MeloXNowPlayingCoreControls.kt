package com.lladlam.melox.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.audio.MusicQualityRuntime
import com.lladlam.melox.core.audio.NeteaseQualityClient
import com.lladlam.melox.core.audio.SongAudioAvailability
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.glass.meloXLiquidButton
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

internal const val MeloXNowPlayingControlsHeight = 279

@Composable
internal fun MeloXNowPlayingCoreControls(
    state: MeloXPlaybackUiState,
    page: MeloXNowPlayingPage,
    onPageSelected: (MeloXNowPlayingPage) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(MeloXNowPlayingControlsHeight.dp),
    ) {
        SceneProgressControl(state)
        Spacer(Modifier.height(19.dp))
        SceneTransportControls(state)
        Spacer(Modifier.height(31.dp))
        SceneVolumeControl(state)
        Spacer(Modifier.height(3.dp))
        ScenePageSelector(
            state = state,
            page = page,
            onPageSelected = onPageSelected,
        )
    }
}

@Composable
private fun SceneProgressControl(state: MeloXPlaybackUiState) {
    val sourceProgress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    var scrubbing by remember { mutableStateOf(false) }
    var localProgress by remember { mutableFloatStateOf(sourceProgress) }
    val trackHeight by animateDpAsState(
        targetValue = if (scrubbing) 6.dp else 4.dp,
        animationSpec = tween(120),
        label = "scene-progress-track-height",
    )

    LaunchedEffect(sourceProgress, scrubbing) {
        if (!scrubbing) localProgress = sourceProgress
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Slider(
            value = localProgress,
            onValueChange = {
                scrubbing = true
                localProgress = it.coerceIn(0f, 1f)
            },
            onValueChangeFinished = {
                if (state.durationMs > 0L) {
                    state.seekTo((state.durationMs * localProgress).roundToLong())
                }
                scrubbing = false
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            thumb = { Spacer(Modifier.size(0.dp)) },
            track = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(localProgress)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.96f)),
                    )
                }
            },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
        ) {
            val shownPosition = if (scrubbing) {
                (state.durationMs * localProgress).roundToLong()
            } else {
                state.positionMs
            }
            Text(
                text = sceneFormatDuration(shownPosition),
                modifier = Modifier.align(Alignment.CenterStart),
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )

            SceneQualityChip(
                state = state,
                modifier = Modifier.align(Alignment.Center),
            )

            Text(
                text = "−${sceneFormatDuration((state.durationMs - shownPosition).coerceAtLeast(0L))}",
                modifier = Modifier.align(Alignment.CenterEnd),
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SceneQualityChip(
    state: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val qualityClient = remember(context) {
        NeteaseQualityClient(
            cookieProvider = { NeteaseSessionStore.readCookie(context) },
        )
    }
    var expanded by remember { mutableStateOf(false) }
    var selected by remember(context) {
        mutableStateOf(
            MusicQualityPreferences.read(context).also { MusicQualityRuntime.selected = it },
        )
    }
    var actual by remember(state.mediaId) {
        mutableStateOf(MusicQualityRuntime.actualFor(state.mediaId?.toLongOrNull()))
    }
    var availability by remember(state.mediaId) {
        mutableStateOf(SongAudioAvailability.Unknown)
    }

    LaunchedEffect(state.mediaId) {
        val songId = state.mediaId?.toLongOrNull() ?: return@LaunchedEffect
        availability = runCatching { qualityClient.audioAvailability(songId) }
            .getOrDefault(SongAudioAvailability.Unknown)
    }
    LaunchedEffect(state.mediaId, selected) {
        val songId = state.mediaId?.toLongOrNull() ?: return@LaunchedEffect
        while (true) {
            actual = MusicQualityRuntime.actualFor(songId)
            delay(180L)
        }
    }

    val displayQuality = actual ?: selected
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "scene-quality-chip-press",
    )

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .height(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .meloXLiquidButton(
                    shape = RoundedCornerShape(7.dp),
                    surfaceColor = Color.White.copy(alpha = 0.10f),
                    lensRadius = 6.dp,
                    refractionHeight = 9.dp,
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                ) { expanded = true }
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier.size(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                SceneCupertinoGlyph(
                    kind = SceneGlyphKind.Waveform,
                    modifier = Modifier.size(12.dp),
                    color = Color.White.copy(alpha = 0.86f),
                )
            }
            Text(
                text = displayQuality.title,
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            MusicQuality.entries.forEach { quality ->
                val supported = availability.supports(quality.apiLevel) != false
                DropdownMenuItem(
                    enabled = supported,
                    text = {
                        Text(if (quality == selected) "✓ ${quality.title}" else quality.title)
                    },
                    onClick = {
                        selected = quality
                        actual = null
                        expanded = false
                        PlaybackCommands.changeQuality(context, quality)
                    },
                )
            }
        }
    }
}

@Composable
private fun SceneTransportControls(state: MeloXPlaybackUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        SceneTransportButton(
            kind = SceneGlyphKind.Backward,
            visualSize = 34.dp,
            onClick = {
                if (state.hasPrevious) state.previous() else state.seekTo(0L)
            },
        )
        Spacer(Modifier.weight(1f))
        ScenePlayPauseButton(state)
        Spacer(Modifier.weight(1f))
        SceneTransportButton(
            kind = SceneGlyphKind.Forward,
            visualSize = 34.dp,
            onClick = state::next,
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ScenePlayPauseButton(state: MeloXPlaybackUiState) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 620f,
        ),
        label = "scene-play-pause-press",
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = state::togglePlayPause,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = state.isPlaying,
            transitionSpec = {
                (
                    fadeIn(tween(180)) +
                        scaleIn(initialScale = 0.78f, animationSpec = tween(200)) +
                        slideInVertically(tween(200)) { (it * 0.24f).toInt() }
                    ) togetherWith (
                    fadeOut(tween(150)) +
                        scaleOut(targetScale = 0.78f, animationSpec = tween(180)) +
                        slideOutVertically(tween(180)) { -(it * 0.24f).toInt() }
                    )
            },
            label = "scene-play-pause-symbol-replace",
        ) { playing ->
            SceneCupertinoGlyph(
                kind = if (playing) SceneGlyphKind.Pause else SceneGlyphKind.Play,
                modifier = Modifier.size(48.dp),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun SceneTransportButton(
    kind: SceneGlyphKind,
    visualSize: Dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.84f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 620f,
        ),
        label = "scene-transport-press-${kind.name}",
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        SceneCupertinoGlyph(
            kind = kind,
            modifier = Modifier.size(visualSize),
            color = Color.White,
        )
    }
}

@Composable
private fun SceneVolumeControl(state: MeloXPlaybackUiState) {
    var dragging by remember { mutableStateOf(false) }
    var localVolume by remember { mutableFloatStateOf(state.volume) }
    val thumbSize by animateDpAsState(
        targetValue = if (dragging) 16.dp else 14.dp,
        animationSpec = tween(120),
        label = "scene-volume-thumb-size",
    )
    val trackHeight by animateDpAsState(
        targetValue = if (dragging) 4.dp else 3.dp,
        animationSpec = tween(120),
        label = "scene-volume-track-height",
    )

    LaunchedEffect(state.volume, dragging) {
        if (!dragging) localVolume = state.volume
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SceneCupertinoGlyph(
            kind = SceneGlyphKind.SpeakerLow,
            modifier = Modifier.size(12.dp),
            color = Color.White.copy(alpha = 0.62f),
        )

        Slider(
            value = localVolume,
            onValueChange = {
                dragging = true
                localVolume = it.coerceIn(0f, 1f)
                state.changeVolume(localVolume)
            },
            onValueChangeFinished = { dragging = false },
            modifier = Modifier
                .weight(1f)
                .height(32.dp),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(thumbSize)
                        .shadow(3.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            },
            track = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(localVolume)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.82f)),
                    )
                }
            },
        )

        SceneCupertinoGlyph(
            kind = SceneGlyphKind.SpeakerHigh,
            modifier = Modifier.size(15.dp),
            color = Color.White.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun ScenePageSelector(
    state: MeloXPlaybackUiState,
    page: MeloXNowPlayingPage,
    onPageSelected: (MeloXNowPlayingPage) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScenePageButton(
            kind = SceneGlyphKind.Lyrics,
            selected = page == MeloXNowPlayingPage.Lyrics,
            enabled = true,
            onClick = { onPageSelected(MeloXNowPlayingPage.Lyrics) },
        )

        ScenePageButton(
            kind = SceneGlyphKind.PipEnter,
            selected = false,
            enabled = false,
            onClick = {},
        )

        Box {
            ScenePageButton(
                kind = SceneGlyphKind.Queue,
                selected = page == MeloXNowPlayingPage.Queue,
                enabled = true,
                onClick = { onPageSelected(MeloXNowPlayingPage.Queue) },
            )

            if (
                page != MeloXNowPlayingPage.Queue &&
                (state.shuffleEnabled || state.repeatMode != 0)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.82f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            state.shuffleEnabled -> "S"
                            state.repeatMode == 1 -> "1"
                            else -> "R"
                        },
                        color = Color.Black.copy(alpha = 0.72f),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScenePageButton(
    kind: SceneGlyphKind,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 650f,
        ),
        label = "scene-page-button-press-${kind.name}",
    )
    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "scene-page-button-selected-${kind.name}",
    )
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (selected) 0.68f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "scene-page-button-bg-${kind.name}",
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                val s = pressScale * selectionScale
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = backgroundAlpha * 0.16f))
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        SceneCupertinoGlyph(
            kind = kind,
            modifier = Modifier.size(22.dp),
            color = when {
                !enabled -> Color.White.copy(alpha = 0.26f)
                selected -> Color.Black.copy(alpha = 0.68f)
                else -> Color.White.copy(alpha = 0.72f)
            },
        )
    }
}

private enum class SceneGlyphKind {
    Backward,
    Forward,
    Play,
    Pause,
    SpeakerLow,
    SpeakerHigh,
    Lyrics,
    PipEnter,
    Queue,
    Waveform,
}

@Composable
private fun SceneCupertinoGlyph(
    kind: SceneGlyphKind,
    modifier: Modifier,
    color: Color,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val min = size.minDimension
        val stroke = (min * 0.085f).coerceAtLeast(1.35f)

        when (kind) {
            SceneGlyphKind.Play -> {
                val p = Path().apply {
                    moveTo(w * 0.28f, h * 0.13f)
                    quadraticBezierTo(w * 0.22f, h * 0.10f, w * 0.22f, h * 0.22f)
                    lineTo(w * 0.22f, h * 0.78f)
                    quadraticBezierTo(w * 0.22f, h * 0.90f, w * 0.30f, h * 0.86f)
                    lineTo(w * 0.82f, h * 0.56f)
                    quadraticBezierTo(w * 0.91f, h * 0.50f, w * 0.82f, h * 0.44f)
                    close()
                }
                drawPath(p, color)
            }

            SceneGlyphKind.Pause -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.24f, h * 0.10f),
                    size = Size(w * 0.18f, h * 0.80f),
                    cornerRadius = CornerRadius(w * 0.045f),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.58f, h * 0.10f),
                    size = Size(w * 0.18f, h * 0.80f),
                    cornerRadius = CornerRadius(w * 0.045f),
                )
            }

            SceneGlyphKind.Backward,
            SceneGlyphKind.Forward -> {
                val forward = kind == SceneGlyphKind.Forward
                fun triangle(x0: Float, x1: Float) {
                    val p = Path()
                    if (forward) {
                        p.moveTo(x0, h * 0.17f)
                        p.lineTo(x1, h * 0.50f)
                        p.lineTo(x0, h * 0.83f)
                    } else {
                        p.moveTo(x1, h * 0.17f)
                        p.lineTo(x0, h * 0.50f)
                        p.lineTo(x1, h * 0.83f)
                    }
                    p.close()
                    drawPath(p, color)
                }
                triangle(w * 0.10f, w * 0.50f)
                triangle(w * 0.45f, w * 0.88f)
            }

            SceneGlyphKind.SpeakerLow,
            SceneGlyphKind.SpeakerHigh -> {
                val speaker = Path().apply {
                    moveTo(w * 0.08f, h * 0.41f)
                    lineTo(w * 0.29f, h * 0.41f)
                    lineTo(w * 0.54f, h * 0.22f)
                    quadraticBezierTo(w * 0.58f, h * 0.19f, w * 0.58f, h * 0.27f)
                    lineTo(w * 0.58f, h * 0.73f)
                    quadraticBezierTo(w * 0.58f, h * 0.81f, w * 0.54f, h * 0.78f)
                    lineTo(w * 0.29f, h * 0.59f)
                    lineTo(w * 0.08f, h * 0.59f)
                    close()
                }
                drawPath(speaker, color)

                if (kind == SceneGlyphKind.SpeakerHigh) {
                    drawArc(
                        color = color,
                        startAngle = -46f,
                        sweepAngle = 92f,
                        useCenter = false,
                        topLeft = Offset(w * 0.45f, h * 0.30f),
                        size = Size(w * 0.30f, h * 0.40f),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = color,
                        startAngle = -48f,
                        sweepAngle = 96f,
                        useCenter = false,
                        topLeft = Offset(w * 0.43f, h * 0.14f),
                        size = Size(w * 0.52f, h * 0.72f),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }

            SceneGlyphKind.Lyrics -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.07f, h * 0.09f),
                    size = Size(w * 0.86f, h * 0.70f),
                    cornerRadius = CornerRadius(w * 0.18f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                val tail = Path().apply {
                    moveTo(w * 0.61f, h * 0.78f)
                    lineTo(w * 0.52f, h * 0.93f)
                    lineTo(w * 0.72f, h * 0.79f)
                }
                drawPath(tail, color, style = Stroke(width = stroke, cap = StrokeCap.Round))

                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.28f, h * 0.31f),
                    size = Size(w * 0.10f, h * 0.18f),
                    cornerRadius = CornerRadius(w * 0.03f),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.53f, h * 0.31f),
                    size = Size(w * 0.10f, h * 0.18f),
                    cornerRadius = CornerRadius(w * 0.03f),
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.28f, h * 0.49f),
                    end = Offset(w * 0.23f, h * 0.58f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.53f, h * 0.49f),
                    end = Offset(w * 0.48f, h * 0.58f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }

            SceneGlyphKind.PipEnter -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.10f, h * 0.12f),
                    size = Size(w * 0.80f, h * 0.68f),
                    cornerRadius = CornerRadius(w * 0.10f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.48f, h * 0.51f),
                    size = Size(w * 0.38f, h * 0.34f),
                    cornerRadius = CornerRadius(w * 0.07f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.29f, h * 0.31f),
                    end = Offset(w * 0.47f, h * 0.49f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                val arrow = Path().apply {
                    moveTo(w * 0.39f, h * 0.48f)
                    lineTo(w * 0.49f, h * 0.49f)
                    lineTo(w * 0.48f, h * 0.39f)
                }
                drawPath(arrow, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
            }

            SceneGlyphKind.Queue -> {
                listOf(0.27f, 0.50f, 0.73f).forEach { y ->
                    drawCircle(
                        color = color,
                        radius = stroke * 0.72f,
                        center = Offset(w * 0.17f, h * y),
                    )
                    drawLine(
                        color = color,
                        start = Offset(w * 0.33f, h * y),
                        end = Offset(w * 0.88f, h * y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }

            SceneGlyphKind.Waveform -> {
                val xs = listOf(0.18f, 0.38f, 0.60f, 0.82f)
                val heights = listOf(0.36f, 0.72f, 0.55f, 0.30f)
                xs.zip(heights).forEach { (x, fraction) ->
                    val half = h * fraction * 0.5f
                    drawLine(
                        color = color,
                        start = Offset(w * x, h * 0.5f - half),
                        end = Offset(w * x, h * 0.5f + half),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

private fun sceneFormatDuration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
