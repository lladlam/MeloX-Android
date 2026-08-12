package com.lladlam.melox.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lladlam.melox.playback.MeloXAudioReactiveRuntime
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private const val PALETTE_TRANSITION_MS = 800
private const val FLOW_FRAME_MS = 33L

@Composable
internal fun MeloXBlurredArtworkBackdrop(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Box(modifier.fillMaxSize()) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.18f; scaleY = 1.18f }.blur(38.dp),
        )
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = .30f))
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = .05f), Color.Black.copy(alpha = .48f)),
                ),
            )
        }
    }
}

/**
 * Android renderer for MeloX's artwork-driven Flowing Light background.
 * Palette extraction is identical in shape to ArtworkAccentColorProvider:
 * 160px downsample -> 3x3 cell averages. Android has no SwiftUI MeshGradient
 * equivalent on all supported API levels, so the same nine control colors are
 * blended as overlapping moving radial fields.
 */
@Composable
internal fun MeloXFlowingLightBackdrop(
    artworkUrl: String?,
    isPlaying: Boolean,
    mediaId: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var targetPalette by remember { mutableStateOf(ArtworkDynamicPalette.Fallback) }
    var phase by remember { mutableFloatStateOf(0f) }
    var energy by remember { mutableFloatStateOf(.18f) }
    var beatPulse by remember { mutableFloatStateOf(0f) }
    var downbeatPulse by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(artworkUrl) {
        targetPalette = ArtworkDynamicPaletteProvider.paletteFor(context, artworkUrl)
    }

    LaunchedEffect(isPlaying, artworkUrl, mediaId) {
        while (true) {
            val sample = MeloXAudioReactiveRuntime.sample(mediaId)
            energy += (sample.energy - energy) * .18f
            beatPulse += (sample.beat - beatPulse) * .32f
            downbeatPulse += (sample.downbeat - downbeatPulse) * .24f
            if (isPlaying) {
                val motion = .026f + energy.coerceIn(0f, 1f) * .038f + beatPulse * .016f
                phase = (phase + motion) % (Math.PI.toFloat() * 2f)
            }
            delay(FLOW_FRAME_MS)
        }
    }

    val c0 by animateColorAsState(targetPalette.cells.getOrElse(0) { targetPalette.average }, tween(PALETTE_TRANSITION_MS), label = "flow-c0")
    val c1 by animateColorAsState(targetPalette.cells.getOrElse(1) { targetPalette.average }, tween(PALETTE_TRANSITION_MS), label = "flow-c1")
    val c2 by animateColorAsState(targetPalette.cells.getOrElse(2) { targetPalette.average }, tween(PALETTE_TRANSITION_MS), label = "flow-c2")
    val c3 by animateColorAsState(targetPalette.cells.getOrElse(3) { targetPalette.average }, tween(PALETTE_TRANSITION_MS), label = "flow-c3")
    val c4 by animateColorAsState(targetPalette.cells.getOrElse(4) { targetPalette.average }, tween(PALETTE_TRANSITION_MS), label = "flow-c4")
    val c5 by animateColorAsState(targetPalette.cells.getOrElse(5) { targetPalette.average }, tween(PALETTE_TRANSITION_MS), label = "flow-c5")
    val c6 by animateColorAsState(targetPalette.cells.getOrElse(6) { targetPalette.average }, tween(PALETTE_TRANSITION_MS), label = "flow-c6")
    val c7 by animateColorAsState(targetPalette.cells.getOrElse(7) { targetPalette.average }, tween(PALETTE_TRANSITION_MS), label = "flow-c7")
    val c8 by animateColorAsState(targetPalette.cells.getOrElse(8) { targetPalette.average }, tween(PALETTE_TRANSITION_MS), label = "flow-c8")
    val average by animateColorAsState(targetPalette.average, tween(PALETTE_TRANSITION_MS), label = "flow-average")
    val colors = listOf(c0, c1, c2, c3, c4, c5, c6, c7, c8)

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(average)
        val maxDimension = maxOf(size.width, size.height)
        val radius = maxDimension * (0.58f + energy.coerceIn(0f, 1f) * .08f + beatPulse * .035f)

        colors.forEachIndexed { index, color ->
            val row = index / 3
            val column = index % 3
            val baseX = when (column) {
                0 -> 0.08f
                1 -> 0.50f
                else -> 0.92f
            }
            val baseY = when (row) {
                0 -> 0.10f
                1 -> 0.50f
                else -> 0.90f
            }
            val localPhase = phase + index * 0.71f
            val displacement = .052f + energy.coerceIn(0f, 1f) * .045f
            val x = size.width * (baseX + sin(localPhase) * displacement)
            val y = size.height * (baseY + cos(localPhase * 0.83f) * displacement * .87f)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = (0.72f + energy * .12f + beatPulse * .08f).coerceIn(.68f, .94f)),
                        color.copy(alpha = (0.28f + energy * .09f).coerceIn(.26f, .42f)),
                        Color.Transparent,
                    ),
                    center = Offset(x, y),
                    radius = radius,
                ),
            )
        }

        // MeloX keeps the lower control region darker for white text/controls.
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Black.copy(alpha = 0.04f),
                    0.52f to Color.Black.copy(alpha = 0.10f),
                    1f to Color.Black.copy(alpha = 0.48f),
                ),
            ),
        )

        // A short downbeat vignette gives the field the same musical breathing
        // cue as upstream without flashing or changing the control contrast.
        if (downbeatPulse > .01f) {
            drawRect(Color.Black.copy(alpha = (downbeatPulse * .16f).coerceIn(0f, .16f)))
        }
    }
}
