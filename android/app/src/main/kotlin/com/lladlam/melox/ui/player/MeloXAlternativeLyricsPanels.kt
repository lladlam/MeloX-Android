package com.lladlam.melox.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.lyrics.withPseudoTiming
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXTextPVStyle
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

@Composable
private fun rememberAlternativeLyrics(state: MeloXPlaybackUiState): AlternativeLyricsState {
    val context = LocalContext.current.applicationContext
    val mediaId = state.mediaId
    var document by remember(mediaId) { mutableStateOf<LyricsDocument?>(null) }
    var loading by remember(mediaId) { mutableStateOf(false) }
    var error by remember(mediaId) { mutableStateOf<String?>(null) }
    LaunchedEffect(mediaId, state.title, state.artist, state.album, state.durationMs) {
        if (mediaId.isNullOrBlank()) return@LaunchedEffect
        loading = true
        error = null
        runCatching { MeloXProviderLyricsLoader.load(context, state) }
            .onSuccess { document = it }
            .onFailure { error = it.message ?: "歌词加载失败" }
        loading = false
    }
    val rendered = remember(document, MeloXSettingsRuntime.lyricPseudoTimingEnabled) {
        if (MeloXSettingsRuntime.lyricPseudoTimingEnabled) document?.withPseudoTiming() else document
    }
    val lines = rendered?.lines.orEmpty()
    val smoothPosition = rememberSmoothPlaybackPosition(state)
    val position = smoothPosition + MeloXSettingsRuntime.lyricAdvanceMs
    val index = rendered?.highlightedIndex(position)?.coerceIn(0, lines.lastIndex.coerceAtLeast(0)) ?: 0
    return AlternativeLyricsState(lines, index, position, loading, error)
}

@Composable
private fun rememberSmoothPlaybackPosition(state: MeloXPlaybackUiState): Long {
    var position by remember(state.mediaId) { mutableLongStateOf(state.positionMs) }
    LaunchedEffect(state.mediaId, state.positionMs, state.isPlaying, state.durationMs) {
        val anchorPosition = state.positionMs
        if (!state.isPlaying) {
            position = anchorPosition
            return@LaunchedEffect
        }
        var anchorFrameNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (anchorFrameNanos == 0L) anchorFrameNanos = frameNanos
                val elapsedMs = (frameNanos - anchorFrameNanos) / 1_000_000L
                position = (anchorPosition + elapsedMs).coerceAtMost(
                    state.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE,
                )
            }
        }
    }
    return position
}

private data class AlternativeLyricsState(
    val lines: List<LyricLine>,
    val index: Int,
    val positionMs: Long,
    val loading: Boolean,
    val error: String?,
)

@Composable
private fun rememberTextPVLyrics(state: MeloXPlaybackUiState): TextPVLyricsState {
    val context = LocalContext.current.applicationContext
    val mediaId = state.mediaId
    var document by remember(mediaId) { mutableStateOf<LyricsDocument?>(null) }
    var loading by remember(mediaId) { mutableStateOf(false) }
    var error by remember(mediaId) { mutableStateOf<String?>(null) }

    LaunchedEffect(mediaId, state.title, state.artist, state.album, state.durationMs) {
        if (mediaId.isNullOrBlank()) return@LaunchedEffect
        loading = true
        error = null
        runCatching { MeloXProviderLyricsLoader.load(context, state) }
            .onSuccess { document = it }
            .onFailure { error = it.message ?: "歌词加载失败" }
        loading = false
    }

    val lines = document?.lines.orEmpty()
    val advanceMs = MeloXSettingsRuntime.lyricAdvanceMs.toLong()
    var index by remember(mediaId, document) {
        mutableIntStateOf(
            if (lines.isEmpty()) 0 else {
                document?.highlightedIndex(state.positionMs + advanceMs)
                    ?.coerceIn(0, lines.lastIndex) ?: 0
            },
        )
    }

    LaunchedEffect(mediaId, document, state.positionMs, state.isPlaying, state.durationMs, advanceMs) {
        val activeDocument = document ?: return@LaunchedEffect
        if (lines.isEmpty()) {
            index = 0
            return@LaunchedEffect
        }

        val anchorPosition = state.positionMs
        if (!state.isPlaying) {
            val nextIndex = activeDocument.highlightedIndex(anchorPosition + advanceMs)
                ?.coerceIn(0, lines.lastIndex) ?: index
            if (nextIndex != index) index = nextIndex
            return@LaunchedEffect
        }

        var anchorFrameNanos = 0L
        var lastCheckNanos = 0L
        while (isActive) {
            val frameNanos = withFrameNanos { it }
            if (anchorFrameNanos == 0L) anchorFrameNanos = frameNanos
            if (lastCheckNanos != 0L && frameNanos - lastCheckNanos < 33_000_000L) continue
            lastCheckNanos = frameNanos
            val elapsedMs = (frameNanos - anchorFrameNanos) / 1_000_000L
            val position = (anchorPosition + elapsedMs).coerceAtMost(
                state.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE,
            )
            val nextIndex = activeDocument.highlightedIndex(position + advanceMs)
                ?.coerceIn(0, lines.lastIndex) ?: index
            if (nextIndex != index) index = nextIndex
        }
    }

    return TextPVLyricsState(lines, index, loading, error)
}

private data class TextPVLyricsState(
    val lines: List<LyricLine>,
    val index: Int,
    val loading: Boolean,
    val error: String?,
)

@Composable
internal fun MeloXEvaLyricsPanel(
    playback: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
    onInteraction: () -> Unit = {},
) {
    val state = rememberAlternativeLyrics(playback)
    Box(modifier.fillMaxSize().clickable(onClick = onInteraction), contentAlignment = Alignment.Center) {
        AlternativeLoading(state)
        if (state.lines.isNotEmpty()) {
            AnimatedContent(
                targetState = state.index,
                transitionSpec = {
                    (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 300f)) { it / 3 } +
                        fadeIn(spring(dampingRatio = 0.7f, stiffness = 300f)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 300f), initialScale = .92f)) togetherWith
                        (slideOutVertically(spring(dampingRatio = 0.7f, stiffness = 300f)) { -it / 4 } + fadeOut(spring(dampingRatio = 0.7f, stiffness = 300f)) + scaleOut(spring(dampingRatio = 0.7f, stiffness = 300f), targetScale = 1.06f))
                },
                label = "eva-lyric-composition",
            ) { index ->
                EvaComposition(
                    line = state.lines[index],
                    previous = state.lines.getOrNull(index - 1),
                    next = state.lines.getOrNull(index + 1),
                    onSeek = { playback.seekTo(state.lines[index].timeMs) },
                )
            }
        }
    }
}

@Composable
private fun EvaComposition(line: LyricLine, previous: LyricLine?, next: LyricLine?, onSeek: () -> Unit) {
    val length = line.text.codePointCount(0, line.text.length)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 30.dp).clickable(onClick = onSeek),
        verticalArrangement = Arrangement.Center,
    ) {
        previous?.let {
            Text(
                it.text.uppercase(),
                color = Color.White.copy(alpha = .20f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.rotate(-2f),
            )
        }
        Spacer(Modifier.size(18.dp))
        when {
            length <= 7 -> Text(
                line.text,
                color = Color.White,
                fontSize = 52.sp,
                lineHeight = 55.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
                modifier = Modifier.rotate(-1.5f),
            )
            length <= 18 -> Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.size(width = 5.dp, height = 48.dp).background(Color.White))
                Spacer(Modifier.width(14.dp))
                Text(
                    line.text,
                    color = Color.White,
                    fontSize = 38.sp,
                    lineHeight = 43.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
            }
            else -> Text(
                line.text,
                color = Color.White,
                fontSize = 30.sp,
                lineHeight = 37.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Justify,
            )
        }
        if (MeloXSettingsRuntime.showLyricRomanization && !line.romanization.isNullOrBlank()) {
            Text(line.romanization.orEmpty(), color = Color.White.copy(alpha = .48f), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        }
        if (MeloXSettingsRuntime.showLyricTranslation && !line.translation.isNullOrBlank()) {
            Text(line.translation.orEmpty(), color = Color.White.copy(alpha = .72f), fontSize = 16.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 7.dp))
        }
        Spacer(Modifier.size(24.dp))
        next?.let {
            Text(
                it.text,
                color = Color.White.copy(alpha = .28f),
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 38.dp).rotate(1f),
            )
        }
    }
}

@Composable
internal fun MeloXTextPVLyricsPanel(
    playback: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
    onInteraction: () -> Unit = {},
) {
    val state = rememberTextPVLyrics(playback)
    val transition = rememberInfiniteTransition(label = "text-pv-clock")
    val animationSpeed = MeloXSettingsRuntime.textPVAnimationSpeed
    val motionIntensity = if (MeloXSettingsRuntime.lyricReduceMotion) 0f else {
        MeloXSettingsRuntime.textPVMotionIntensity
    }
    val phaseState = transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animationSpeed <= 0f || motionIntensity <= 0f) 0f else 1f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = if (animationSpeed <= 0f) 14_000 else {
                    (14_000f / animationSpeed).toInt().coerceIn(3_500, 140_000)
                },
                easing = FastOutSlowInEasing,
            ),
            RepeatMode.Restart,
        ),
        label = "text-pv-phase",
    )
    Box(modifier.fillMaxSize().clickable(onClick = onInteraction)) {
        TextPVBackground(
            style = MeloXSettingsRuntime.textPVStyle,
            phaseProvider = { phaseState.value },
            motionIntensity = motionIntensity,
        )
        TextPVLoading(state)
        if (state.lines.isNotEmpty()) {
            AnimatedContent(
                targetState = state.index,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn(tween(520), initialScale = .82f)) togetherWith
                        (fadeOut(tween(240)) + scaleOut(tween(360), targetScale = 1.18f))
                },
                label = "text-pv-line",
            ) { index ->
                TextPVComposition(
                    line = state.lines[index],
                    next = state.lines.getOrNull(index + 1),
                    style = MeloXSettingsRuntime.textPVStyle,
                    phaseProvider = { phaseState.value },
                    motionIntensity = motionIntensity,
                    onSeek = { playback.seekTo(state.lines[index].timeMs) },
                )
            }
        }
    }
}

@Composable
private fun TextPVBackground(
    style: MeloXTextPVStyle,
    phaseProvider: () -> Float,
    motionIntensity: Float,
) {
    val staticStyles = setOf(
        MeloXTextPVStyle.StaggeredText,
        MeloXTextPVStyle.FlyMeToTheMoon,
        MeloXTextPVStyle.KawaiiPixel,
    )
    Canvas(Modifier.fillMaxSize()) {
        if (!size.width.isFinite() || !size.height.isFinite() || size.width <= 0f || size.height <= 0f) {
            return@Canvas
        }
        val phase = if (style in staticStyles) 0f else phaseProvider()
        if (style in staticStyles) {
            drawTextPVStaticBackground(style)
        } else {
            drawTextPVBackground(style, phase, motionIntensity)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTextPVStaticBackground(style: MeloXTextPVStyle) {
    when (style) {
        MeloXTextPVStyle.StaggeredText -> {
            drawRect(Color.Black.copy(alpha = .14f))
            drawScatteredText(0f, 0f, chars = listOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'K', 'M', 'N', 'P', 'R', 'S', 'T'), count = 12, minSize = 14f, maxSize = 38f, color = Color.White)
        }
        MeloXTextPVStyle.FlyMeToTheMoon -> {
            drawRect(Color(0xFF080D24).copy(alpha = .74f))
            repeat(18) { index -> drawCircle(Color.White.copy(alpha = .1f), 1.5f + index % 3, Offset(size.width * ((index * .173f) % 1f), size.height * ((index * .317f) % 1f))) }
            drawCircle(Color(0xFFBFCBFF).copy(alpha = .16f), size.minDimension * .24f, Offset(size.width * .78f, size.height * .22f))
        }
        MeloXTextPVStyle.KawaiiPixel -> {
            drawRect(Color(0xFFB8F1EA).copy(alpha = .52f))
            val spacing = (size.minDimension / 14f).coerceAtLeast(8f)
            val xCount = (size.width / spacing).toInt().coerceIn(0, 96) + 1
            repeat(xCount) { index ->
                val x = index * spacing
                drawLine(Color(0xFFFF79AE).copy(alpha = .08f), Offset(x, 0f), Offset(x, size.height), 2f)
            }
            val yCount = (size.height / spacing).toInt().coerceIn(0, 160) + 1
            repeat(yCount) { index ->
                val y = index * spacing
                drawLine(Color.White.copy(alpha = .09f), Offset(0f, y), Offset(size.width, y), 2f)
            }
            drawScatteredShapes(0f, 0f, count = 10, minSize = 5f, maxSize = 18f, color = Color(0xFFFF79AE), alpha = 0.22f)
        }
        else -> {}
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTextPVBackground(
    style: MeloXTextPVStyle,
    phase: Float,
    motionIntensity: Float,
) {
    when (style) {
        MeloXTextPVStyle.BlueBold, MeloXTextPVStyle.BluePlane, MeloXTextPVStyle.Dynamic -> {
            drawRect(Color(0xFF123A91).copy(alpha = .56f))
            repeat(7) { index ->
                val x = size.width * ((index * .19f + phase * .12f * motionIntensity) % 1.15f - .08f)
                drawLine(Color.White.copy(alpha = .05f), Offset(x, 0f), Offset(x - size.height * .18f, size.height), 2f)
            }
            drawCircle(Color.White.copy(alpha = .08f), radius = size.minDimension * .28f, center = Offset(size.width * .78f, size.height * .22f), style = Stroke(3f))
        }
        MeloXTextPVStyle.KineticSplit, MeloXTextPVStyle.CrimeScene -> {
            drawRect(Color(0xFFF0E6D5).copy(alpha = .72f))
            drawLine(Color(0xFF8E1832).copy(alpha = .72f), Offset(-size.width * .1f, size.height * (.72f - phase * .12f * motionIntensity)), Offset(size.width * 1.1f, size.height * (.28f + phase * .12f * motionIntensity)), size.minDimension * .055f)
        }
        MeloXTextPVStyle.Geometric -> {
            drawRect(Color(0xFFF5C928).copy(alpha = .76f))
            repeat(4) { index ->
                drawRect(Color.Black.copy(alpha = .06f + index * .018f), topLeft = Offset(size.width * (.08f + index * .07f), size.height * (.12f + index * .07f)), size = androidx.compose.ui.geometry.Size(size.minDimension * (.74f - index * .1f), size.minDimension * (.74f - index * .1f)), style = Stroke(3f))
            }
        }
        MeloXTextPVStyle.CyberGrunge, MeloXTextPVStyle.RainCity, MeloXTextPVStyle.CyberpunkHUD,
        MeloXTextPVStyle.SpiderWeb, MeloXTextPVStyle.Cyber -> {
            drawRect(Color(0xFF061018).copy(alpha = .52f))
            val spacing = (size.minDimension / 12f).coerceAtLeast(8f)
            val xCount = (size.width / spacing).toInt().coerceIn(0, 96) + 3
            repeat(xCount) { index ->
                val x = -spacing + phase * spacing * motionIntensity + index * spacing
                drawLine(Color(0xFF76E8FF).copy(alpha = .10f), Offset(x, 0f), Offset(x, size.height), 1f)
            }
            val yCount = (size.height / spacing).toInt().coerceIn(0, 160) + 3
            repeat(yCount) { index ->
                val y = -spacing + phase * spacing * motionIntensity + index * spacing
                drawLine(Color(0xFFFF4B89).copy(alpha = .08f), Offset(0f, y), Offset(size.width, y), 1f)
            }
        }
        MeloXTextPVStyle.EmotionCinema, MeloXTextPVStyle.CalmVillain, MeloXTextPVStyle.Haruhikage,
        MeloXTextPVStyle.Minimal -> {
            drawRect(Color(0xFF24334A).copy(alpha = .38f))
            repeat(5) { index ->
                val y = size.height * (.18f + index * .15f)
                drawLine(Color(0xFF9CC8FF).copy(alpha = .05f), Offset(0f, y), Offset(size.width, y + sin(phase * 6.28f + index) * 24f * motionIntensity), 2f)
            }
            if (style != MeloXTextPVStyle.Minimal) {
                drawScatteredText(phase, motionIntensity, chars = listOf('夜', '夢', '影', '光', '風', '星', '雨', '花', '月', '雪'), count = 8, minSize = 16f, maxSize = 42f, color = Color(0xFF9CC8FF))
            }
        }
        MeloXTextPVStyle.HystericNight -> {
            drawRect(Color(0xFF180A25).copy(alpha = .58f))
            repeat(10) { index ->
                val angle = index * PI.toFloat() / 5f + phase * .35f * motionIntensity
                val end = Offset(size.width / 2f + kotlin.math.cos(angle) * size.maxDimension, size.height / 2f + sin(angle) * size.maxDimension)
                drawLine(Color(0xFFE46CFF).copy(alpha = .055f), center, end, size.minDimension * .025f)
            }
        }
        MeloXTextPVStyle.GirlyClouds, MeloXTextPVStyle.SweetPink -> {
            drawRect(Color(0xFFF3A9C3).copy(alpha = .55f))
            repeat(7) { index ->
                val x = size.width * ((index * .22f + phase * .08f * motionIntensity) % 1.2f - .1f)
                drawLine(Color.White.copy(alpha = .11f), Offset(x, 0f), Offset(x - size.height * .25f, size.height), size.minDimension * .018f)
            }
            drawCircle(Color.White.copy(alpha = .12f), size.minDimension * .22f, Offset(size.width * .12f, size.height * .15f))
            drawScatteredShapes(phase, motionIntensity, count = 14, minSize = 6f, maxSize = 24f, color = Color.White, alpha = 0.18f)
        }
        else -> {}
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScatteredShapes(
    phase: Float,
    motionIntensity: Float,
    count: Int = 18,
    minSize: Float = 4f,
    maxSize: Float = 30f,
    color: Color = Color.White,
    alpha: Float = 0.3f,
) {
    for (index in 0 until count) {
        val seed = index * 5
        val side = minSize + ((seed * 7 + 13) % 100) / 100f * (maxSize - minSize)
        val drift = sin(phase * 0.1f * motionIntensity + index.toFloat()) * 14f * motionIntensity
        val cx = (((seed * 17 + 3) % 100) / 100f) * size.width + drift
        val cy = (((seed * 31 + 7) % 100) / 100f) * size.height - drift * 0.55f
        val shapeType = index % 3
        when (shapeType) {
            0 -> drawCircle(color.copy(alpha = alpha), side / 2f, Offset(cx, cy))
            1 -> drawRect(color.copy(alpha = alpha), topLeft = Offset(cx - side / 2f, cy - side * 0.36f), size = androidx.compose.ui.geometry.Size(side, side * 0.72f))
            2 -> {
                val s = side / 2f
                drawRect(color.copy(alpha = alpha), topLeft = Offset(cx - s, cy - s), size = androidx.compose.ui.geometry.Size(side, side))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScatteredText(
    phase: Float,
    motionIntensity: Float,
    chars: List<Char>,
    count: Int = 15,
    minSize: Float = 20f,
    maxSize: Float = 60f,
    color: Color = Color.White,
) {
    if (chars.isEmpty()) return
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    for (index in 0 until count) {
        val seed = index * 3
        val charIndex = ((seed * 11 + 5) % chars.size)
        val opacity = (0.25f + ((seed * 7 + 13) % 100) / 100f * 0.5f
            + sin(phase * (0.2f + ((seed * 13 + 17) % 100) / 100f * 0.4f)
                + ((seed * 19 + 23) % 100) / 100f * 6.28f) * 0.12f).coerceIn(0f, 1f)
        val fontSize = minSize + ((seed * 23 + 29) % 100) / 100f * (maxSize - minSize)
        val cx = (((seed * 3 + 1) % 100) / 100f) * size.width
        val cy = (((seed * 3 + 2) % 100) / 100f) * size.height
        paint.color = color.copy(alpha = opacity).toArgb()
        paint.textSize = fontSize
        drawContext.canvas.nativeCanvas.drawText(
            chars[charIndex].toString(),
            cx,
            cy,
            paint,
        )
    }
}

@Composable
private fun TextPVComposition(
    line: LyricLine,
    next: LyricLine?,
    style: MeloXTextPVStyle,
    phaseProvider: () -> Float,
    motionIntensity: Float,
    onSeek: () -> Unit,
) {
    val centered = style in setOf(MeloXTextPVStyle.Geometric, MeloXTextPVStyle.GirlyClouds, MeloXTextPVStyle.SweetPink, MeloXTextPVStyle.FlyMeToTheMoon, MeloXTextPVStyle.Minimal)
    val bottom = style in setOf(MeloXTextPVStyle.CyberGrunge, MeloXTextPVStyle.RainCity, MeloXTextPVStyle.CyberpunkHUD, MeloXTextPVStyle.SpiderWeb, MeloXTextPVStyle.Cyber)
    val alignment = if (centered) Alignment.Center else if (bottom) Alignment.BottomStart else Alignment.CenterStart
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = alignment) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onSeek)) {
            if (bottom) {
                val marker = ((line.timeMs / 10L) % 1000L).toString().padStart(3, '0')
                Text("LYRIC // $marker", color = Color(0xFF76E8FF), fontSize = 11.sp, letterSpacing = 2.sp)
            }
            Text(
                line.text,
                color = Color.White,
                fontSize = when {
                    centered -> 38.sp
                    bottom -> 42.sp
                    style == MeloXTextPVStyle.StaggeredText -> 52.sp
                    else -> 48.sp
                },
                lineHeight = 52.sp,
                fontWeight = FontWeight.Black,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.graphicsLayer {
                    val phase = phaseProvider()
                    val scale = 1f + sin(phase * 2f * PI.toFloat()) * .012f * motionIntensity
                    scaleX = scale
                    scaleY = scale
                },
            )
            if (MeloXSettingsRuntime.showLyricTranslation && !line.translation.isNullOrBlank()) {
                Text(line.translation.orEmpty(), color = Color.White.copy(alpha = .62f), fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
            }
            next?.let { Text(it.text, color = Color.White.copy(alpha = .20f), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 26.dp)) }
        }
    }
}

@Composable
internal fun MeloXSkylineLyricsPanel(
    playback: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
    onInteraction: () -> Unit = {},
) {
    val state = rememberAlternativeLyrics(playback)
    Box(modifier.fillMaxSize().clickable(onClick = onInteraction)) {
        val ambientTexts = state.lines
            .drop(state.index + 1)
            .take(MeloXSettingsRuntime.skylineAmbientLines)
            .flatMap { it.text.chunked(MeloXSettingsRuntime.skylineAmbientMaximumCharacters) }
            .filter(String::isNotBlank)
            .take(MeloXSettingsRuntime.skylineAmbientMaximumVisibleTexts)
        if (ambientTexts.isNotEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(.58f).rotate(-5f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                ambientTexts.forEachIndexed { ambientIndex, ambient ->
                    val fade = (1f - ambientIndex.toFloat() / ambientTexts.size.coerceAtLeast(1))
                    val tilt = sin((ambientIndex + 1) * 1.73f) * MeloXSettingsRuntime.skylineAmbientMaximumTilt
                    val drift = if (MeloXSettingsRuntime.lyricReduceMotion) 0f else {
                        sin(state.positionMs / 1_800f + ambientIndex) * 14f * MeloXSettingsRuntime.skylineAmbientDrift
                    }
                    Text(
                        ambient,
                        color = Color.White.copy(
                            alpha = (.08f * MeloXSettingsRuntime.skylineAmbientOpacity * (.45f + fade * .55f))
                                .coerceIn(.02f, .22f),
                        ),
                        fontSize = MeloXSettingsRuntime.skylineAmbientFontSize.sp,
                        lineHeight = (MeloXSettingsRuntime.skylineAmbientFontSize * 1.08f).sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .graphicsLayer { translationX = drift }
                            .rotate(tilt)
                            .blur((MeloXSettingsRuntime.skylineAmbientBlur * (1f + ambientIndex * .08f)).dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (MeloXSettingsRuntime.skylineShowSongInfo) {
                Column(Modifier.weight(.42f), verticalArrangement = Arrangement.Center) {
                    Artwork(playback.artworkUrl, Modifier.size(132.dp).clip(RoundedCornerShape(20.dp)))
                    Text(playback.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp))
                    Text(playback.artist, color = Color.White.copy(alpha = .52f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(34.dp))
            }
            AnimatedContent(
                targetState = state.index,
                modifier = Modifier.weight(if (MeloXSettingsRuntime.skylineShowSongInfo) .58f else 1f),
                transitionSpec = {
                    (slideInVertically(tween(560, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(420))) togetherWith
                        (slideOutVertically(tween(380)) { -it / 5 } + fadeOut(tween(300)))
                },
                label = "skyline-focus",
            ) { index ->
                val line = state.lines.getOrNull(index)
                val nextLine = state.lines.getOrNull(index + 1)
                val lineEnd = line?.durationMs?.let { line.timeMs + it }
                    ?: nextLine?.timeMs
                    ?: (line?.timeMs?.plus(3_000L) ?: 1L)
                val timedProgress = if (line == null || line.syllables.isEmpty()) 0f else {
                    ((state.positionMs - line.timeMs).toFloat() / (lineEnd - line.timeMs).coerceAtLeast(1L))
                        .coerceIn(0f, 1f)
                }
                val currentScale = 1f +
                    (MeloXSettingsRuntime.skylineCurrentMaximumScale - 1f) * timedProgress
                Column(
                    modifier = Modifier.fillMaxWidth(MeloXSettingsRuntime.skylineCurrentWidth),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        line?.text.orEmpty(),
                        color = Color.White,
                        fontSize = MeloXSettingsRuntime.skylineCurrentFontSize.sp,
                        lineHeight = (MeloXSettingsRuntime.skylineCurrentFontSize * 1.12f).sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .scale(currentScale)
                            .clickable { line?.let { playback.seekTo(it.timeMs) } },
                    )
                    if (MeloXSettingsRuntime.showLyricTranslation && !line?.translation.isNullOrBlank()) {
                        Text(line?.translation.orEmpty(), color = Color.White.copy(alpha = .62f), fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                    nextLine?.let { next ->
                        Text(
                            next.text,
                            color = Color.White.copy(alpha = MeloXSettingsRuntime.skylineNextOpacity),
                            fontSize = MeloXSettingsRuntime.skylineNextFontSize.sp,
                            lineHeight = (MeloXSettingsRuntime.skylineNextFontSize * 1.2f).sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = MeloXSettingsRuntime.skylineCurrentSpacing.dp),
                        )
                    }
                }
            }
        }
        AlternativeLoading(state)
    }
}

@Composable
private fun AlternativeLoading(state: AlternativeLyricsState) {
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.error, color = MaterialTheme.colorScheme.error) }
        !state.loading && state.lines.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无歌词", color = Color.White.copy(alpha = .56f)) }
    }
}

@Composable
private fun TextPVLoading(state: TextPVLyricsState) {
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.error, color = MaterialTheme.colorScheme.error) }
        !state.loading && state.lines.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无歌词", color = Color.White.copy(alpha = .56f)) }
    }
}
