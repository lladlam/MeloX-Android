package com.lladlam.melox.ui.player

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.lladlam.melox.core.lyrics.LyricTimelineProcessor
import com.lladlam.melox.core.lyrics.withPseudoTiming
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXTextPVStyle
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

@Composable
private fun rememberAlternativeLyrics(
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
    LaunchedEffect(active, mediaId, state.title, state.artist, state.album, state.durationMs, automaticLyricSelectionEnabled) {
        if (!active || mediaId.isNullOrBlank() || loadedRequestKey == requestKey) return@LaunchedEffect
        loading = true
        error = null
        runCatching { MeloXProviderLyricsLoader.load(context, state) }
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
        if (!active || lines.isEmpty()) return@LaunchedEffect
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
    return AlternativeLyricsState(lines, index, loading, error)
}

private data class AlternativeLyricsState(
    val lines: List<LyricLine>,
    val index: Int,
    val loading: Boolean,
    val error: String?,
)

@Composable
private fun rememberTextPVLyrics(
    state: MeloXPlaybackUiState,
    active: Boolean,
): TextPVLyricsState {
    val context = LocalContext.current.applicationContext
    val mediaId = state.mediaId
    val automaticLyricSelectionEnabled = MeloXSettingsRuntime.automaticLyricSelectionEnabled
    var document by remember(mediaId, automaticLyricSelectionEnabled) { mutableStateOf<LyricsDocument?>(null) }
    var loading by remember(mediaId, automaticLyricSelectionEnabled) { mutableStateOf(false) }
    var error by remember(mediaId, automaticLyricSelectionEnabled) { mutableStateOf<String?>(null) }
    var loadedRequestKey by remember(mediaId, automaticLyricSelectionEnabled) { mutableStateOf<String?>(null) }
    val requestKey = lyricUiRequestKey(state, automaticLyricSelectionEnabled)

    LaunchedEffect(active, mediaId, state.title, state.artist, state.album, state.durationMs, automaticLyricSelectionEnabled) {
        if (!active || mediaId.isNullOrBlank() || loadedRequestKey == requestKey) return@LaunchedEffect
        loading = true
        error = null
        runCatching { MeloXProviderLyricsLoader.load(context, state) }
            .onSuccess {
                document = it
                loadedRequestKey = requestKey
            }
            .onFailure { error = it.message ?: "歌词加载失败" }
        loading = false
    }

    val lines = document?.lines.orEmpty()
    val trackOffsetMs = rememberBilibiliLyricOffset(mediaId)
    val advanceMs = effectiveBilibiliLyricAdvance(MeloXSettingsRuntime.lyricAdvanceMs, trackOffsetMs)
    var index by remember(mediaId, document) {
        mutableIntStateOf(
            if (lines.isEmpty()) 0 else {
                document?.highlightedIndex(state.positionMs + advanceMs)
                    ?.coerceIn(0, lines.lastIndex) ?: 0
            },
        )
    }

    LaunchedEffect(active, mediaId, document, state.positionMs, state.isPlaying, state.durationMs, advanceMs) {
        val activeDocument = document ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
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

        val anchorRealtimeMs = SystemClock.elapsedRealtime()
        while (isActive) {
            val position = (anchorPosition + SystemClock.elapsedRealtime() - anchorRealtimeMs).coerceAtMost(
                state.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE,
            )
            val nextIndex = activeDocument.highlightedIndex(position + advanceMs)
                ?.coerceIn(0, lines.lastIndex) ?: index
            if (nextIndex != index) index = nextIndex
            val nextEvent = lines.getOrNull(nextIndex + 1)?.timeMs
                ?: LyricTimelineProcessor.nextEventTimeMs(activeDocument, position + advanceMs)
            val waitMs = (nextEvent?.minus(position + advanceMs) ?: 250L).coerceIn(16L, 500L)
            kotlinx.coroutines.delay(waitMs)
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

internal fun lyricUiRequestKey(state: MeloXPlaybackUiState, automaticSelection: Boolean): String = buildString {
    append(state.mediaId)
    append(':').append(normalizeLyricMatchText(state.title))
    append(':').append(normalizeLyricMatchText(state.artist))
    append(':').append(normalizeLyricMatchText(state.album))
    append(':').append(state.durationMs.takeIf { it > 0L }?.let { ((it + 500L) / 1_000L) * 1_000L } ?: 0L)
    append(':').append(automaticSelection)
}

@Composable
internal fun MeloXEvaLyricsPanel(
    playback: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
    onInteraction: () -> Unit = {},
    active: Boolean = true,
) {
    val state = rememberAlternativeLyrics(playback, active)
    Box(modifier.fillMaxSize().clickable(onClick = onInteraction), contentAlignment = Alignment.Center) {
        AlternativeLoading(state)
        if (state.lines.isNotEmpty()) {
            AnimatedContent(
                targetState = state.index,
                transitionSpec = {
                    (slideInVertically(tween(520, easing = FastOutSlowInEasing)) { it / 3 } +
                        fadeIn(tween(420, delayMillis = 80)) + scaleIn(tween(520), initialScale = .92f)) togetherWith
                        (slideOutVertically(tween(360)) { -it / 4 } + fadeOut(tween(280)) + scaleOut(targetScale = 1.06f))
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
    active: Boolean = true,
) {
    val state = rememberTextPVLyrics(playback, active)
    val phaseState = remember { Animatable(0f) }
    val animationSpeed = MeloXSettingsRuntime.textPVAnimationSpeed
    val motionIntensity = if (!active || MeloXSettingsRuntime.lyricReduceMotion) 0f else {
        MeloXSettingsRuntime.textPVMotionIntensity
    }
    LaunchedEffect(active, animationSpeed, motionIntensity) {
        if (!active || animationSpeed <= 0f || motionIntensity <= 0f) {
            phaseState.snapTo(0f)
            return@LaunchedEffect
        }
        while (isActive) {
            phaseState.animateTo(
                1f,
                tween(
                durationMillis = if (animationSpeed <= 0f) 14_000 else {
                    (14_000f / animationSpeed).toInt().coerceIn(3_500, 140_000)
                },
                easing = FastOutSlowInEasing,
                ),
            )
            phaseState.snapTo(0f)
        }
    }
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

private enum class TextPVPattern {
    BlueRays, SplitSlash, PlaneBlocks, Grunge, Geometry, Rain, Hud, Film,
    Radial, Web, Ink, Villain, Clouds, Gingham, Stars, Pixels, CrimeTape, Petals,
}

private data class TextPVVisualSpec(
    val pattern: TextPVPattern,
    val background: Color,
    val accent: Color,
    val secondary: Color,
    val foreground: Color,
    val alignment: Alignment,
    val textAlign: TextAlign,
    val fontSize: Float,
    val lineHeight: Float,
    val weight: FontWeight,
    val rotation: Float,
    val letterSpacing: Float,
    val uppercase: Boolean,
    val marker: String?,
    val nextOpacity: Float,
    val motionX: Float,
    val motionY: Float,
    val pulse: Float,
)

private fun MeloXTextPVStyle.visualSpec(): TextPVVisualSpec = when (this) {
    MeloXTextPVStyle.BlueBold -> TextPVVisualSpec(TextPVPattern.BlueRays, Color(0xFF123A91), Color(0xFF8CC8FF), Color.White, Color.White, Alignment.CenterStart, TextAlign.Start, 50f, 54f, FontWeight.Black, -2f, -.4f, false, null, .18f, 16f, 0f, .018f)
    MeloXTextPVStyle.KineticSplit -> TextPVVisualSpec(TextPVPattern.SplitSlash, Color(0xFFF0E6D5), Color(0xFF8E1832), Color.Black, Color(0xFF15110F), Alignment.CenterEnd, TextAlign.End, 47f, 51f, FontWeight.ExtraBold, -5f, -.8f, false, null, .28f, -22f, 8f, .012f)
    MeloXTextPVStyle.BluePlane -> TextPVVisualSpec(TextPVPattern.PlaneBlocks, Color(0xFF0C2B68), Color(0xFF46A8FF), Color(0xFFBBD9FF), Color.White, Alignment.TopStart, TextAlign.Start, 44f, 48f, FontWeight.Bold, 0f, 1.2f, true, "PLANE", .24f, 28f, 18f, .008f)
    MeloXTextPVStyle.CyberGrunge -> TextPVVisualSpec(TextPVPattern.Grunge, Color(0xFF071318), Color(0xFF76E8FF), Color(0xFFFF4B89), Color(0xFFDBFAFF), Alignment.BottomStart, TextAlign.Start, 43f, 47f, FontWeight.Black, -1f, 1.4f, true, "GRUNGE", .16f, 12f, -10f, .022f)
    MeloXTextPVStyle.Geometric -> TextPVVisualSpec(TextPVPattern.Geometry, Color(0xFFF5C928), Color.Black, Color.White, Color.Black, Alignment.Center, TextAlign.Center, 39f, 44f, FontWeight.Black, 0f, -.6f, false, null, .30f, 0f, -16f, .026f)
    MeloXTextPVStyle.RainCity -> TextPVVisualSpec(TextPVPattern.Rain, Color(0xFF05131B), Color(0xFF3DFFB5), Color(0xFF8BE9FD), Color(0xFFD7FFF0), Alignment.BottomCenter, TextAlign.Center, 40f, 45f, FontWeight.Medium, 0f, 2.1f, false, "CITY RAIN", .13f, 0f, 22f, .004f)
    MeloXTextPVStyle.CyberpunkHUD -> TextPVVisualSpec(TextPVPattern.Hud, Color(0xFF090C15), Color(0xFFFFD23F), Color(0xFF00E5FF), Color(0xFFFFF1A8), Alignment.CenterEnd, TextAlign.End, 38f, 43f, FontWeight.Bold, 0f, 1.8f, true, "HUD", .20f, -18f, 0f, .010f)
    MeloXTextPVStyle.EmotionCinema -> TextPVVisualSpec(TextPVPattern.Film, Color(0xFF24334A), Color(0xFF9CC8FF), Color(0xFFF1C9AE), Color.White, Alignment.BottomStart, TextAlign.Start, 41f, 49f, FontWeight.SemiBold, 0f, .1f, false, "SCENE", .34f, 0f, -12f, .006f)
    MeloXTextPVStyle.HystericNight -> TextPVVisualSpec(TextPVPattern.Radial, Color(0xFF180A25), Color(0xFFE46CFF), Color(0xFFFF477E), Color.White, Alignment.Center, TextAlign.Center, 46f, 48f, FontWeight.Black, 4f, -.9f, true, null, .14f, 18f, 18f, .040f)
    MeloXTextPVStyle.SpiderWeb -> TextPVVisualSpec(TextPVPattern.Web, Color(0xFF07090F), Color(0xFFE7ECFF), Color(0xFF8C52FF), Color.White, Alignment.TopEnd, TextAlign.End, 42f, 47f, FontWeight.ExtraBold, 2f, .6f, false, "WEB", .17f, -14f, 12f, .014f)
    MeloXTextPVStyle.StaggeredText -> TextPVVisualSpec(TextPVPattern.Ink, Color(0xFFEBE7DD), Color(0xFF151515), Color(0xFFE84A5F), Color(0xFF151515), Alignment.CenterStart, TextAlign.Start, 54f, 56f, FontWeight.Black, -7f, -1.2f, false, null, .38f, 32f, -12f, .030f)
    MeloXTextPVStyle.CalmVillain -> TextPVVisualSpec(TextPVPattern.Villain, Color(0xFF17202A), Color(0xFFC6A15B), Color(0xFF66788A), Color(0xFFF1E9DC), Alignment.CenterStart, TextAlign.Start, 39f, 47f, FontWeight.Light, 0f, 2.8f, true, "ACT I", .26f, 5f, 0f, .002f)
    MeloXTextPVStyle.GirlyClouds -> TextPVVisualSpec(TextPVPattern.Clouds, Color(0xFFF3A9C3), Color.White, Color(0xFFFFE4EF), Color.White, Alignment.Center, TextAlign.Center, 38f, 43f, FontWeight.Bold, -1f, .3f, false, null, .32f, 12f, -12f, .020f)
    MeloXTextPVStyle.SweetPink -> TextPVVisualSpec(TextPVPattern.Gingham, Color(0xFFFFD0E1), Color(0xFFFF5E91), Color.White, Color(0xFF9B2851), Alignment.Center, TextAlign.Center, 37f, 42f, FontWeight.Black, 0f, .8f, false, "SWEET", .28f, -8f, 8f, .016f)
    MeloXTextPVStyle.FlyMeToTheMoon -> TextPVVisualSpec(TextPVPattern.Stars, Color(0xFF080D24), Color(0xFFBFCBFF), Color(0xFF6F81C7), Color.White, Alignment.Center, TextAlign.Center, 36f, 45f, FontWeight.Light, 0f, 3.2f, true, "ORBIT", .24f, 0f, -24f, .008f)
    MeloXTextPVStyle.KawaiiPixel -> TextPVVisualSpec(TextPVPattern.Pixels, Color(0xFFB8F1EA), Color(0xFFFF79AE), Color.White, Color(0xFF285A64), Alignment.BottomEnd, TextAlign.End, 40f, 42f, FontWeight.Black, 0f, 0f, false, "READY!", .30f, -16f, -6f, .012f)
    MeloXTextPVStyle.CrimeScene -> TextPVVisualSpec(TextPVPattern.CrimeTape, Color(0xFFE3D8C1), Color(0xFFB1182B), Color.Black, Color(0xFF17110D), Alignment.CenterStart, TextAlign.Start, 45f, 49f, FontWeight.Black, -3f, -.5f, true, "EVIDENCE", .22f, 24f, 6f, .018f)
    MeloXTextPVStyle.Haruhikage -> TextPVVisualSpec(TextPVPattern.Petals, Color(0xFFB9CAE3), Color(0xFFFFEFF7), Color(0xFF6D7FA0), Color(0xFF26364F), Alignment.BottomStart, TextAlign.Start, 37f, 46f, FontWeight.Medium, 0f, .4f, false, "春日影", .35f, 8f, -18f, .003f)
    MeloXTextPVStyle.Dynamic -> MeloXTextPVStyle.BlueBold.visualSpec()
    MeloXTextPVStyle.Minimal -> MeloXTextPVStyle.CalmVillain.visualSpec()
    MeloXTextPVStyle.Cyber -> MeloXTextPVStyle.CyberGrunge.visualSpec()
}

internal fun MeloXTextPVStyle.visualSignature(): String = visualSpec().run {
    listOf(pattern, background, accent, secondary, foreground, alignment, textAlign, fontSize, lineHeight, weight, rotation, letterSpacing, uppercase, marker, nextOpacity, motionX, motionY, pulse).joinToString("|")
}

@Composable
private fun TextPVBackground(style: MeloXTextPVStyle, phaseProvider: () -> Float, motionIntensity: Float) {
    val spec = remember(style) { style.visualSpec() }
    Canvas(Modifier.fillMaxSize()) {
        if (!size.width.isFinite() || !size.height.isFinite() || size.width <= 0f || size.height <= 0f) return@Canvas
        val phase = phaseProvider()
        val motion = phase * motionIntensity
        drawRect(spec.background.copy(alpha = .78f))
        when (spec.pattern) {
            TextPVPattern.BlueRays -> repeat(8) { index ->
                val x = size.width * ((index * .17f + motion * .12f) % 1.2f - .1f)
                drawLine(spec.accent.copy(alpha = .12f), Offset(x, 0f), Offset(x - size.height * .22f, size.height), 3f)
            }
            TextPVPattern.SplitSlash -> {
                drawLine(spec.accent.copy(alpha = .85f), Offset(-size.width * .1f, size.height * (.75f - motion * .12f)), Offset(size.width * 1.1f, size.height * (.25f + motion * .12f)), size.minDimension * .07f)
                drawLine(spec.secondary.copy(alpha = .22f), Offset(0f, size.height * .28f), Offset(size.width, size.height * .08f), 5f)
            }
            TextPVPattern.PlaneBlocks -> repeat(5) { index ->
                val width = size.width * (.18f + index * .045f)
                drawRect(spec.accent.copy(alpha = .07f + index * .025f), Offset(size.width * (.06f + index * .18f), size.height * ((index * .14f + motion * .08f) % .8f)), androidx.compose.ui.geometry.Size(width, size.height * .28f))
            }
            TextPVPattern.Grunge -> repeat(22) { index ->
                val y = size.height * ((index * .071f + motion * .09f) % 1f)
                drawLine(if (index % 2 == 0) spec.accent.copy(alpha = .11f) else spec.secondary.copy(alpha = .09f), Offset(0f, y), Offset(size.width * (.35f + (index % 7) * .1f), y + (index % 3 - 1) * 7f), 1f + index % 4)
            }
            TextPVPattern.Geometry -> repeat(5) { index ->
                val inset = size.minDimension * (.08f + index * .07f)
                drawRect(spec.accent.copy(alpha = .08f + index * .025f), Offset(inset, inset), androidx.compose.ui.geometry.Size(size.width - inset * 2f, size.height - inset * 2f), style = Stroke(3f))
            }
            TextPVPattern.Rain -> repeat(32) { index ->
                val x = size.width * ((index * .137f) % 1f)
                val y = size.height * ((index * .221f + motion * .45f) % 1.15f - .1f)
                drawLine(spec.accent.copy(alpha = .16f), Offset(x, y), Offset(x - 7f, y + 46f + index % 5 * 8f), 2f)
            }
            TextPVPattern.Hud -> {
                drawRect(spec.accent.copy(alpha = .18f), Offset(size.width * .06f, size.height * .08f), androidx.compose.ui.geometry.Size(size.width * .88f, size.height * .84f), style = Stroke(3f))
                repeat(7) { index -> drawLine(spec.secondary.copy(alpha = .12f), Offset(size.width * .58f, size.height * (.16f + index * .09f)), Offset(size.width * (.92f - motion * .03f), size.height * (.16f + index * .09f)), 2f) }
            }
            TextPVPattern.Film -> {
                drawRect(Color.Black.copy(alpha = .24f), Offset.Zero, androidx.compose.ui.geometry.Size(size.width, size.height * .11f))
                drawRect(Color.Black.copy(alpha = .24f), Offset(0f, size.height * .89f), androidx.compose.ui.geometry.Size(size.width, size.height * .11f))
                drawCircle(spec.accent.copy(alpha = .10f), size.minDimension * .34f, Offset(size.width * (.28f + motion * .05f), size.height * .48f))
            }
            TextPVPattern.Radial -> repeat(12) { index ->
                val angle = index * PI.toFloat() / 6f + motion * .45f
                drawLine(spec.accent.copy(alpha = .075f), center, Offset(center.x + kotlin.math.cos(angle) * size.maxDimension, center.y + sin(angle) * size.maxDimension), size.minDimension * .022f)
            }
            TextPVPattern.Web -> {
                repeat(10) { index ->
                    val angle = index * PI.toFloat() / 5f + motion * .08f
                    drawLine(spec.accent.copy(alpha = .12f), Offset(size.width, 0f), Offset(size.width + kotlin.math.cos(angle) * size.maxDimension, sin(angle) * size.maxDimension), 1.5f)
                }
                repeat(4) { index -> drawCircle(spec.secondary.copy(alpha = .10f), size.minDimension * (.16f + index * .12f), Offset(size.width, 0f), style = Stroke(2f)) }
            }
            TextPVPattern.Ink -> repeat(6) { index ->
                val y = size.height * (.17f + index * .13f)
                drawLine(spec.accent.copy(alpha = .09f), Offset(size.width * .04f, y), Offset(size.width * (.58f + index * .05f), y - 18f + motion * 8f), 8f - index * .7f)
            }
            TextPVPattern.Villain -> repeat(5) { index ->
                val y = size.height * (.20f + index * .14f)
                drawLine(spec.accent.copy(alpha = .08f + index * .018f), Offset(size.width * .08f, y), Offset(size.width * (.88f - index * .05f), y), 1f)
            }
            TextPVPattern.Clouds -> repeat(7) { index ->
                val x = size.width * ((index * .2f + motion * .08f) % 1.2f - .1f)
                drawCircle(spec.accent.copy(alpha = .15f), size.minDimension * (.10f + index % 3 * .035f), Offset(x, size.height * (.18f + index % 4 * .18f)))
            }
            TextPVPattern.Gingham -> {
                val spacing = size.minDimension / 9f
                repeat(14) { index -> drawLine(spec.accent.copy(alpha = .09f), Offset(index * spacing, 0f), Offset(index * spacing, size.height), spacing * .32f) }
                repeat(22) { index -> drawLine(spec.secondary.copy(alpha = .13f), Offset(0f, index * spacing), Offset(size.width, index * spacing), spacing * .28f) }
            }
            TextPVPattern.Stars -> {
                repeat(28) { index -> drawCircle(spec.accent.copy(alpha = .12f + index % 4 * .03f), 1.5f + index % 3, Offset(size.width * ((index * .173f + motion * .015f) % 1f), size.height * ((index * .317f) % 1f))) }
                drawCircle(spec.accent.copy(alpha = .18f), size.minDimension * .24f, Offset(size.width * .78f, size.height * .22f))
            }
            TextPVPattern.Pixels -> {
                val spacing = (size.minDimension / 13f).coerceAtLeast(8f)
                repeat((size.width / spacing).toInt().coerceAtMost(80) + 1) { index -> drawLine(spec.accent.copy(alpha = .09f), Offset(index * spacing, 0f), Offset(index * spacing, size.height), 2f) }
                repeat((size.height / spacing).toInt().coerceAtMost(120) + 1) { index -> drawLine(spec.secondary.copy(alpha = .12f), Offset(0f, index * spacing), Offset(size.width, index * spacing), 2f) }
            }
            TextPVPattern.CrimeTape -> repeat(3) { index ->
                val y = size.height * (.22f + index * .27f + motion * .025f)
                drawLine(spec.accent.copy(alpha = .76f), Offset(-size.width * .15f, y + size.height * .08f), Offset(size.width * 1.15f, y - size.height * .08f), size.minDimension * .045f)
            }
            TextPVPattern.Petals -> repeat(18) { index ->
                val x = size.width * ((index * .113f + motion * .05f) % 1.1f)
                val y = size.height * ((index * .179f + motion * .10f) % 1.1f)
                drawOval(spec.accent.copy(alpha = .18f), Offset(x, y), androidx.compose.ui.geometry.Size(8f + index % 4 * 3f, 16f + index % 5 * 3f))
            }
        }
    }
}

@Composable
private fun TextPVComposition(line: LyricLine, next: LyricLine?, style: MeloXTextPVStyle, phaseProvider: () -> Float, motionIntensity: Float, onSeek: () -> Unit) {
    val spec = remember(style) { style.visualSpec() }
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = spec.alignment) {
        Column(Modifier.fillMaxWidth(.94f).clickable(onClick = onSeek), horizontalAlignment = when (spec.textAlign) {
            TextAlign.Center -> Alignment.CenterHorizontally
            TextAlign.End -> Alignment.End
            else -> Alignment.Start
        }) {
            spec.marker?.let { marker ->
                Text("$marker // ${((line.timeMs / 10L) % 1000L).toString().padStart(3, '0')}", color = spec.accent, fontSize = 11.sp, letterSpacing = 2.sp)
            }
            Text(
                if (spec.uppercase) line.text.uppercase() else line.text,
                color = spec.foreground,
                fontSize = spec.fontSize.sp,
                lineHeight = spec.lineHeight.sp,
                fontWeight = spec.weight,
                letterSpacing = spec.letterSpacing.sp,
                textAlign = spec.textAlign,
                modifier = Modifier.rotate(spec.rotation).graphicsLayer {
                    val wave = sin(phaseProvider() * 2f * PI.toFloat()) * motionIntensity
                    translationX = wave * spec.motionX
                    translationY = wave * spec.motionY
                    scaleX = 1f + wave * spec.pulse
                    scaleY = 1f + wave * spec.pulse
                },
            )
            if (MeloXSettingsRuntime.showLyricTranslation && !line.translation.isNullOrBlank()) {
                Text(line.translation.orEmpty(), color = spec.foreground.copy(alpha = .62f), fontSize = 15.sp, textAlign = spec.textAlign, modifier = Modifier.padding(top = 12.dp))
            }
            next?.let { Text(it.text, color = spec.foreground.copy(alpha = spec.nextOpacity), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = spec.textAlign, modifier = Modifier.padding(top = 24.dp)) }
        }
    }
}

@Composable
internal fun MeloXSkylineLyricsPanel(
    playback: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
    onInteraction: () -> Unit = {},
    active: Boolean = true,
) {
    val state = rememberAlternativeLyrics(playback, active)
    val ambientLineCount = MeloXSettingsRuntime.skylineAmbientLines
    val ambientCharacterLimit = MeloXSettingsRuntime.skylineAmbientMaximumCharacters
    val ambientVisibleLimit = MeloXSettingsRuntime.skylineAmbientMaximumVisibleTexts
    val ambientPhase = remember { Animatable(0f) }
    LaunchedEffect(active, MeloXSettingsRuntime.lyricReduceMotion, MeloXSettingsRuntime.skylineAmbientDrift) {
        if (!active || MeloXSettingsRuntime.lyricReduceMotion || MeloXSettingsRuntime.skylineAmbientDrift <= 0f) {
            ambientPhase.snapTo(0f)
            return@LaunchedEffect
        }
        while (isActive) {
            ambientPhase.animateTo(1f, tween(12_000))
            ambientPhase.snapTo(0f)
        }
    }
    Box(modifier.fillMaxSize().clickable(onClick = onInteraction)) {
        val ambientTexts = remember(
            state.lines,
            state.index,
            ambientLineCount,
            ambientCharacterLimit,
            ambientVisibleLimit,
        ) {
            state.lines
                .drop(state.index + 1)
                .take(ambientLineCount)
                .flatMap { it.text.chunked(ambientCharacterLimit) }
                .filter(String::isNotBlank)
                .take(ambientVisibleLimit)
        }
        if (ambientTexts.isNotEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(.58f).rotate(-5f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                ambientTexts.forEachIndexed { ambientIndex, ambient ->
                    val fade = (1f - ambientIndex.toFloat() / ambientTexts.size.coerceAtLeast(1))
                    val tilt = sin((ambientIndex + 1) * 1.73f) * MeloXSettingsRuntime.skylineAmbientMaximumTilt
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
                            .graphicsLayer {
                                translationX = if (MeloXSettingsRuntime.lyricReduceMotion) 0f else {
                                    sin(ambientPhase.value * 2f * PI.toFloat() + ambientIndex) *
                                        14f * MeloXSettingsRuntime.skylineAmbientDrift
                                }
                            }
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
                    val effectivePosition = playback.positionMs +
                        effectiveBilibiliLyricAdvance(
                            MeloXSettingsRuntime.lyricAdvanceMs,
                            rememberBilibiliLyricOffset(playback.mediaId),
                        )
                    ((effectivePosition - line.timeMs).toFloat() / (lineEnd - line.timeMs).coerceAtLeast(1L))
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
                        Text(line.translation.orEmpty(), color = Color.White.copy(alpha = .62f), fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
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
