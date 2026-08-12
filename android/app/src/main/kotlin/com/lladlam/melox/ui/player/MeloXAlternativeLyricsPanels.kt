package com.lladlam.melox.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.lyrics.withPseudoTiming
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXTextPVStyle
import kotlin.math.PI
import kotlin.math.sin

@Composable
private fun rememberAlternativeLyrics(state: MeloXPlaybackUiState): AlternativeLyricsState {
    val context = LocalContext.current.applicationContext
    val mediaId = state.mediaId
    var document by remember(mediaId) { mutableStateOf<LyricsDocument?>(null) }
    var loading by remember(mediaId) { mutableStateOf(false) }
    var error by remember(mediaId) { mutableStateOf<String?>(null) }
    val client = remember(context) {
        NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
    }
    LaunchedEffect(mediaId) {
        val songId = mediaId?.toLongOrNull() ?: return@LaunchedEffect
        loading = true
        error = null
        val downloaded = MeloXDownloadStore.get(context).localLyrics(songId)
        if (downloaded != null) {
            document = downloaded
        } else {
            runCatching { client.lyrics(songId) }
                .onSuccess { document = it }
                .onFailure { error = it.message ?: "歌词加载失败" }
        }
        loading = false
    }
    val rendered = remember(document, MeloXSettingsRuntime.lyricPseudoTimingEnabled) {
        if (MeloXSettingsRuntime.lyricPseudoTimingEnabled) document?.withPseudoTiming() else document
    }
    val lines = rendered?.lines.orEmpty()
    val position = state.positionMs + MeloXSettingsRuntime.lyricAdvanceMs
    val index = rendered?.highlightedIndex(position)?.coerceIn(0, lines.lastIndex.coerceAtLeast(0)) ?: 0
    return AlternativeLyricsState(lines, index, position, loading, error)
}

private data class AlternativeLyricsState(
    val lines: List<LyricLine>,
    val index: Int,
    val positionMs: Long,
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
) {
    val state = rememberAlternativeLyrics(playback)
    val transition = rememberInfiniteTransition(label = "text-pv-clock")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7_000, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "text-pv-phase",
    )
    Box(modifier.fillMaxSize().clickable(onClick = onInteraction)) {
        TextPVBackground(MeloXSettingsRuntime.textPVStyle, phase)
        AlternativeLoading(state)
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
                    phase = phase,
                    onSeek = { playback.seekTo(state.lines[index].timeMs) },
                )
            }
        }
    }
}

@Composable
private fun TextPVBackground(style: MeloXTextPVStyle, phase: Float) {
    Canvas(Modifier.fillMaxSize()) {
        when (style) {
            MeloXTextPVStyle.Dynamic -> {
                drawRect(Color.Black.copy(alpha = .10f))
                repeat(7) { index ->
                    val x = size.width * ((index * .19f + phase * .12f) % 1.15f - .08f)
                    drawLine(Color.White.copy(alpha = .05f), Offset(x, 0f), Offset(x - size.height * .18f, size.height), 2f)
                }
                drawCircle(Color.White.copy(alpha = .08f), radius = size.minDimension * .28f, center = Offset(size.width * .78f, size.height * .22f), style = Stroke(3f))
            }
            MeloXTextPVStyle.Minimal -> drawRect(Color.Black.copy(alpha = .08f))
            MeloXTextPVStyle.Cyber -> {
                drawRect(Color(0xFF061018).copy(alpha = .52f))
                val spacing = size.minDimension / 12f
                var x = -spacing + phase * spacing
                while (x < size.width + spacing) {
                    drawLine(Color(0xFF76E8FF).copy(alpha = .10f), Offset(x, 0f), Offset(x, size.height), 1f)
                    x += spacing
                }
                var y = -spacing + phase * spacing
                while (y < size.height + spacing) {
                    drawLine(Color(0xFFFF4B89).copy(alpha = .08f), Offset(0f, y), Offset(size.width, y), 1f)
                    y += spacing
                }
            }
        }
    }
}

@Composable
private fun TextPVComposition(
    line: LyricLine,
    next: LyricLine?,
    style: MeloXTextPVStyle,
    phase: Float,
    onSeek: () -> Unit,
) {
    val alignment = when (style) {
        MeloXTextPVStyle.Dynamic -> Alignment.CenterStart
        MeloXTextPVStyle.Minimal -> Alignment.Center
        MeloXTextPVStyle.Cyber -> Alignment.BottomStart
    }
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = alignment) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onSeek)) {
            if (style == MeloXTextPVStyle.Cyber) {
                Text("LYRIC // ${(phase * 999).toInt().toString().padStart(3, '0')}", color = Color(0xFF76E8FF), fontSize = 11.sp, letterSpacing = 2.sp)
            }
            Text(
                line.text,
                color = Color.White,
                fontSize = when (style) {
                    MeloXTextPVStyle.Dynamic -> 48.sp
                    MeloXTextPVStyle.Minimal -> 38.sp
                    MeloXTextPVStyle.Cyber -> 42.sp
                },
                lineHeight = 52.sp,
                fontWeight = FontWeight.Black,
                textAlign = if (style == MeloXTextPVStyle.Minimal) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.scale(1f + sin(phase * 2f * PI.toFloat()) * .012f),
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
        state.lines.getOrNull(state.index + 1)?.let { ambient ->
            Text(
                ambient.text,
                color = Color.White.copy(alpha = .075f),
                fontSize = 72.sp,
                lineHeight = 78.sp,
                fontWeight = FontWeight.Black,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(.58f).rotate(-5f),
            )
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(.42f), verticalArrangement = Arrangement.Center) {
                Artwork(playback.artworkUrl, Modifier.size(132.dp).clip(RoundedCornerShape(20.dp)))
                Text(playback.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp))
                Text(playback.artist, color = Color.White.copy(alpha = .52f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(34.dp))
            AnimatedContent(
                targetState = state.index,
                modifier = Modifier.weight(.58f),
                transitionSpec = {
                    (slideInVertically(tween(560, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(420))) togetherWith
                        (slideOutVertically(tween(380)) { -it / 5 } + fadeOut(tween(300)))
                },
                label = "skyline-focus",
            ) { index ->
                val line = state.lines.getOrNull(index)
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        line?.text.orEmpty(),
                        color = Color.White,
                        fontSize = 42.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clickable { line?.let { playback.seekTo(it.timeMs) } },
                    )
                    if (MeloXSettingsRuntime.showLyricTranslation && !line?.translation.isNullOrBlank()) {
                        Text(line?.translation.orEmpty(), color = Color.White.copy(alpha = .62f), fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                    state.lines.getOrNull(index + 1)?.let { next ->
                        Text(next.text, color = Color.White.copy(alpha = .28f), fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 30.dp))
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
