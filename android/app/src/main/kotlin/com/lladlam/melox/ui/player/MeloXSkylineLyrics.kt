package com.lladlam.melox.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private data class SkylineAmbientItem(
    val id: Int,
    val text: String,
    val x: Float,
    val y: Float,
    val baseScale: Float,
    val baseOpacity: Float,
    val rotation: Float,
    val driftX: Float,
    val driftY: Float,
    val stage: Int,
)

private fun skylineAccent(artworkUrl: String?, mediaId: String?): Color {
    val seed = (artworkUrl.orEmpty() + mediaId.orEmpty()).hashCode()
    val hue = ((seed ushr 8) and 0xFF) / 255f * 360f
    return Color.hsv(hue, .46f, 1f)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MeloXSkylineLyricsPanel(
    playback: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
    onInteraction: () -> Unit = {},
    active: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    skylineOverlayClip: SharedTransitionScope.OverlayClip? = null,
) {
    val state = rememberAlternativeLyrics(playback, active)
    if (state.isUnsynchronized) {
        MeloXUnsynchronizedLyrics(state.lines, modifier)
        return
    }
    val accent = remember(playback.artworkUrl, playback.mediaId) {
        skylineAccent(playback.artworkUrl, playback.mediaId)
    }
    Box(modifier.fillMaxSize().background(Color.Black).clickable(onClick = onInteraction)) {
        SkylineBackground(accent)
        if (state.lines.isNotEmpty()) {
            val index = state.index.coerceIn(state.lines.indices)
            SkylineAmbientField(
                line = state.lines[index],
                accent = accent,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (MeloXSettingsRuntime.skylineShowSongInfo) {
                    Column(
                        Modifier.weight(.38f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val skylineArtModifier = if (
                            sharedTransitionScope != null && animatedVisibilityScope != null
                        ) {
                            with(sharedTransitionScope) {
                                if (skylineOverlayClip != null) {
                                    Modifier.sharedElement(
                                        sharedContentState = rememberSharedContentState("np-skyline-artwork"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        boundsTransform = MeloXSkylineArtworkBoundsTransform,
                                        renderInOverlayDuringTransition = true,
                                        zIndexInOverlay = 100f,
                                        clipInOverlayDuringTransition = skylineOverlayClip,
                                    )
                                } else {
                                    Modifier.sharedElement(
                                        sharedContentState = rememberSharedContentState("np-skyline-artwork"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        boundsTransform = MeloXSkylineArtworkBoundsTransform,
                                        renderInOverlayDuringTransition = true,
                                        zIndexInOverlay = 100f,
                                    )
                                }
                            }
                        } else {
                            Modifier
                        }
                        Artwork(
                            playback.artworkUrl,
                            skylineArtModifier.size(132.dp).clip(RoundedCornerShape(20.dp)),
                        )
                        Text(
                            playback.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        Text(
                            playback.artist,
                            color = Color.White.copy(alpha = .52f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(34.dp))
                }
                SkylineCentralLyrics(
                    lines = state.lines,
                    index = index,
                    positionMs = playback.positionMs,
                    accent = accent,
                    onSeek = { playback.seekTo(it.timeMs) },
                    modifier = Modifier.weight(if (MeloXSettingsRuntime.skylineShowSongInfo) .62f else 1f),
                )
            }
        }
        AlternativeLoading(state)
    }
}

@Composable
private fun SkylineBackground(accent: Color) {
    Box(Modifier.fillMaxSize().drawWithContent {
        drawRect(Color.Black.copy(alpha = .88f))
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = .12f), Color.Transparent),
                center = Offset(size.width * .5f, size.height * .38f),
                radius = size.maxDimension * .7f,
            ),
        )
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = .16f),
                .5f to Color.Transparent,
                1f to Color.Black.copy(alpha = .58f),
            ),
        )
        drawContent()
    })
}

@Composable
private fun SkylineAmbientField(
    line: LyricLine,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val maxVisible = MeloXSettingsRuntime.skylineAmbientMaximumVisibleTexts.coerceIn(4, 24)
    val maxCharacters = MeloXSettingsRuntime.skylineAmbientMaximumCharacters.coerceIn(1, 4)
    val fontScale = MeloXSettingsRuntime.skylineAmbientFontSize.sp
    val opacityScale = MeloXSettingsRuntime.skylineAmbientOpacity
    val blurScale = MeloXSettingsRuntime.skylineAmbientBlur
    val maxTilt = MeloXSettingsRuntime.skylineAmbientMaximumTilt
    val driftScale = MeloXSettingsRuntime.skylineAmbientDrift
    val reduceMotion = MeloXSettingsRuntime.lyricReduceMotion
    var items by remember { mutableStateOf<List<SkylineAmbientItem>>(emptyList()) }
    var nextId by remember { mutableStateOf(0) }

    LaunchedEffect(line.text, maxCharacters, maxVisible) {
        val grown = items.mapNotNull { item ->
            if (item.stage >= 3) null else item.copy(stage = item.stage + 1)
        }
        val fragments = skylineFragments(line.text, maxCharacters)
        val selected = if (fragments.size <= 4) fragments else {
            val step = fragments.size / 4f
            List(4) { index -> fragments[min((index * step).toInt(), fragments.lastIndex)] }
        }
        val additions = selected.map { fragment ->
            val identifier = nextId++
            val seed = (identifier + 1) * -7046029254386353131L
            val random = Random(seed)
            val x = random.nextFloat()
            val y = random.nextFloat()
            val scale = .82f + random.nextFloat() * .36f
            val opacity = .15f + random.nextFloat() * .09f
            val rotation = (random.nextFloat() * 2f - 1f) * maxTilt
            SkylineAmbientItem(
                id = identifier,
                text = fragment,
                x = x,
                y = y,
                baseScale = scale,
                baseOpacity = opacity,
                rotation = rotation,
                driftX = (if (x < .5f) 1f else -1f) * (6f + random.nextFloat() * 12f),
                driftY = (if (y < .5f) -1f else 1f) * (2f + random.nextFloat() * 4f),
                stage = 0,
            )
        }
        items = (grown + additions).takeLast(maxVisible)
    }

    val drift = rememberInfiniteTransition(label = "skyline-ambient").animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(9_000), RepeatMode.Reverse),
        label = "skyline-drift",
    )
    val driftValue = drift.value
    BoxWithConstraints(
        modifier.clipToBounds().drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    .06f to Color.Black,
                    .92f to Color.Black,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        },
    ) {
        items.forEach { item ->
            val growth = when (item.stage) {
                0 -> .78f
                1 -> 1f
                2 -> 1.26f
                else -> 1.6f
            }
            val blurRadius = when (item.stage) {
                0 -> .4f
                1 -> 1.1f
                2 -> 2.4f
                else -> 4.6f
            }
            val opacity = when (item.stage) {
                0 -> item.baseOpacity
                1 -> item.baseOpacity * .86f
                2 -> item.baseOpacity * .62f
                else -> item.baseOpacity * .36f
            }
            val animatedScale by animateFloatAsState(
                targetValue = growth,
                animationSpec = tween(if (reduceMotion) 0 else 820),
                label = "skyline-growth-${item.id}",
            )
            val driftOffset = if (reduceMotion) 0f else driftValue
            Text(
                item.text,
                color = accent.copy(alpha = (opacity * opacityScale).coerceIn(0f, 1f)),
                fontSize = fontScale,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = maxWidth.toPx() * item.x + item.driftX * driftScale * driftOffset
                        translationY = maxHeight.toPx() * item.y + item.driftY * driftScale * driftOffset
                        scaleX = item.baseScale * animatedScale
                        scaleY = item.baseScale * animatedScale
                        rotationZ = item.rotation + sin(driftOffset * Math.PI.toFloat() * 2f) * 2f * driftScale
                    }
                    .blur((blurRadius * blurScale).dp),
            )
        }
    }
}

private fun skylineFragments(text: String, maximumCharacters: Int): List<String> {
    val groups = text.split(Regex("[\\s\\p{Punct}]+")).filter(String::isNotEmpty)
    return groups.flatMap { group ->
        group.chunked(maximumCharacters.coerceAtLeast(1))
    }.filter(String::isNotBlank)
}

@Composable
private fun SkylineCentralLyrics(
    lines: List<LyricLine>,
    index: Int,
    positionMs: Long,
    accent: Color,
    onSeek: (LyricLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentFontSize = MeloXSettingsRuntime.skylineCurrentFontSize.coerceIn(36f, 84f)
    val nextFontSize = MeloXSettingsRuntime.skylineNextFontSize.coerceIn(14f, 44f)
    val spacing = MeloXSettingsRuntime.skylineCurrentSpacing.coerceIn(4f, 36f)
    val widthFraction = MeloXSettingsRuntime.skylineCurrentWidth.coerceIn(.4f, .82f)
    val maximumScale = MeloXSettingsRuntime.skylineCurrentMaximumScale.coerceIn(1f, 1.2f)
    val nextOpacity = MeloXSettingsRuntime.skylineNextOpacity.coerceIn(.2f, .8f)
    val reduceMotion = MeloXSettingsRuntime.lyricReduceMotion
    val current = lines[index]
    val previous = lines.getOrNull(index - 1)
    val next = lines.getOrNull(index + 1)
    val fontSizeScale = MeloXSettingsRuntime.lyricFontScale

    AnimatedContent(
        targetState = index,
        transitionSpec = {
            (fadeIn(tween(if (reduceMotion) 0 else 260)) +
                slideInVertically(tween(if (reduceMotion) 0 else 360)) { it / 5 }) togetherWith
                (fadeOut(tween(if (reduceMotion) 0 else 220)) +
                    slideOutVertically(tween(if (reduceMotion) 0 else 280)) { -it / 6 })
        },
        label = "skyline-central",
    ) { targetIndex ->
        val line = lines.getOrNull(targetIndex) ?: return@AnimatedContent
        val following = lines.getOrNull(targetIndex + 1)
        val lineEnd = line.durationMs?.let { line.timeMs + it }
            ?: following?.timeMs
            ?: (line.timeMs + 3_000L)
        val playbackProgress = ((positionMs - line.timeMs).toFloat() /
            (lineEnd - line.timeMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
        val scale = 1f + (maximumScale - 1f) * playbackProgress
        SkylineCentralLayout(
            current = line,
            previous = lines.getOrNull(targetIndex - 1),
            next = following,
            currentFontSize = currentFontSize,
            nextFontSize = nextFontSize,
            spacing = spacing,
            widthFraction = widthFraction,
            nextOpacity = nextOpacity,
            accent = accent,
            scale = scale,
            maximumScale = maximumScale,
            fontSizeScale = fontSizeScale,
            onSeek = onSeek,
            modifier = modifier,
        )
    }
}

@Composable
private fun SkylineCentralLayout(
    current: LyricLine,
    previous: LyricLine?,
    next: LyricLine?,
    currentFontSize: Float,
    nextFontSize: Float,
    spacing: Float,
    widthFraction: Float,
    nextOpacity: Float,
    accent: Color,
    scale: Float,
    maximumScale: Float,
    fontSizeScale: Float,
    onSeek: (LyricLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val lyricWidth = maxWidth * widthFraction
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                previous?.let {
                    Text(
                        it.text,
                        color = Color.White.copy(alpha = 0f),
                        fontSize = currentFontSize.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(lyricWidth).blur((currentFontSize * .12f).dp),
                    )
                }
                Text(
                    current.text,
                    color = Color.White,
                    fontSize = (currentFontSize * fontSizeScale).sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .width(lyricWidth)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clickable { onSeek(current) },
                )
                next?.let {
                    Text(
                        it.text,
                        color = Color.White.copy(alpha = nextOpacity),
                        fontSize = (nextFontSize * fontSizeScale).sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(lyricWidth),
                    )
                }
            },
        ) { measurables, constraints ->
            val loose = Constraints(
                minWidth = 0,
                maxWidth = constraints.maxWidth,
                minHeight = 0,
                maxHeight = constraints.maxHeight,
            )
            val placeables = measurables.map { it.measure(loose) }
            val currentHeight = placeables.getOrNull(if (previous == null) 0 else 1)?.height ?: 0
            val previewScale = nextFontSize / currentFontSize.coerceAtLeast(1f)
            val outgoingTravel = (currentFontSize * .55f).coerceAtLeast(28f)
            val nextHeight = placeables.lastOrNull()?.height ?: 0
            layout(constraints.maxWidth, constraints.maxHeight) {
                var index = 0
                if (previous != null) {
                    val placeable = placeables[index++]
                    placeable.place(
                        x = (constraints.maxWidth - placeable.width) / 2,
                        y = constraints.maxHeight / 2 - placeable.height / 2 - outgoingTravel.toInt(),
                    )
                }
                run {
                    val placeable = placeables[index++]
                    placeable.place(
                        x = (constraints.maxWidth - placeable.width) / 2,
                        y = (constraints.maxHeight - placeable.height) / 2,
                    )
                }
                if (next != null) {
                    val placeable = placeables[index]
                    val nextCenter = constraints.maxHeight / 2 +
                        (currentHeight * maxOf(maximumScale, 1f) * .5f + spacing * density +
                            placeable.height * previewScale * .5f).roundToInt()
                    placeable.place(
                        x = (constraints.maxWidth - placeable.width) / 2,
                        y = nextCenter - placeable.height / 2,
                    )
                }
            }
        }
    }
}
