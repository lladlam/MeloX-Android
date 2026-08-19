package com.lladlam.melox.ui.player

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.lladlam.melox.playback.MeloXAudioReactiveRuntime
import com.lladlam.melox.ui.settings.MeloXLyricsRenderingQuality
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import kotlin.math.sin
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/**
 * AGSL fragment shader that replicates fillFlowingMeshPixels() entirely on GPU.
 * 9 radial gradient fields with uniform control colors and centers.
 * API 31+ only; callers must check [isFlowingLightGpuSupported].
 */
private val FLOWING_LIGHT_SHADER = """
uniform float2 u_resolution;
uniform float u_phase;
uniform float u_energy;
uniform float u_beatPulse;
uniform float u_downbeatPulse;

layout(color) uniform half4 u_c0;
layout(color) uniform half4 u_c1;
layout(color) uniform half4 u_c2;
layout(color) uniform half4 u_c3;
layout(color) uniform half4 u_c4;
layout(color) uniform half4 u_c5;
layout(color) uniform half4 u_c6;
layout(color) uniform half4 u_c7;
layout(color) uniform half4 u_c8;

uniform float2 u_c0c;
uniform float2 u_c1c;
uniform float2 u_c2c;
uniform float2 u_c3c;
uniform float2 u_c4c;
uniform float2 u_c5c;
uniform float2 u_c6c;
uniform float2 u_c7c;
uniform float2 u_c8c;

layout(color) uniform half4 u_average;
uniform float u_radiusNorm;

half4 main(float2 coord) {
    float2 uv = coord / u_resolution;
    float aspect = u_resolution.x / u_resolution.y;

    float radiusNorm = u_radiusNorm;
    float baseWeight = 0.22;
    float pulseGain = 1.0 + u_energy * 0.10 + u_beatPulse * 0.07;
    float downbeatShade = 1.0 - clamp(u_downbeatPulse * 0.16, 0.0, 0.16);

    float2 centers[9];
    centers[0] = u_c0c; centers[1] = u_c1c; centers[2] = u_c2c;
    centers[3] = u_c3c; centers[4] = u_c4c; centers[5] = u_c5c;
    centers[6] = u_c6c; centers[7] = u_c7c; centers[8] = u_c8c;

    half4 colors[9];
    colors[0] = u_c0; colors[1] = u_c1; colors[2] = u_c2;
    colors[3] = u_c3; colors[4] = u_c4; colors[5] = u_c5;
    colors[6] = u_c6; colors[7] = u_c7; colors[8] = u_c8;

    float maxDim = max(u_resolution.x, u_resolution.y);
    float widthScale = u_resolution.x / maxDim;
    float heightScale = u_resolution.y / maxDim;

    float totalWeight = baseWeight;
    float r = u_average.r * baseWeight;
    float g = u_average.g * baseWeight;
    float b = u_average.b * baseWeight;

    for (int i = 0; i < 9; i++) {
        float2 c = centers[i];
        float dx = (uv.x - c.x) * widthScale / radiusNorm;
        float dy = (uv.y - c.y) * heightScale / radiusNorm;
        float dist2 = dx * dx + dy * dy;
        float falloff = 1.0 / (1.0 + dist2 * 4.5);
        float weight = falloff * falloff;
        half4 col = colors[i];
        totalWeight += weight;
        r += col.r * weight;
        g += col.g * weight;
        b += col.b * weight;
    }

    r = clamp(r / totalWeight * pulseGain * downbeatShade, 0.0, 1.0);
    g = clamp(g / totalWeight * pulseGain * downbeatShade, 0.0, 1.0);
    b = clamp(b / totalWeight * pulseGain * downbeatShade, 0.0, 1.0);

    return half4(r, g, b, 1.0);
}
""".trimIndent()

fun isFlowingLightGpuSupported(): Boolean = Build.VERSION.SDK_INT >= 31

/**
 * Compute the 9 radial-gradient center positions for a given phase/energy,
 * mirroring the exact CPU math in fillFlowingMeshPixels().
 */
fun computeFlowingLightCenters(
    colors: List<androidx.compose.ui.graphics.Color>,
    phase: Float,
    energy: Float,
): Pair<FloatArray, FloatArray> {
    val count = colors.size.coerceAtMost(9)
    val centersX = FloatArray(count)
    val centersY = FloatArray(count)
    for (index in 0 until count) {
        val row = index / 3
        val column = index % 3
        val baseX = when (column) { 0 -> .08f; 1 -> .50f; else -> .92f }
        val baseY = when (row) { 0 -> .10f; 1 -> .50f; else -> .90f }
        val localPhase = phase + index * .71f
        val displacement = .052f + energy.coerceIn(0f, 1f) * .045f
        centersX[index] = baseX + sin(localPhase) * displacement
        centersY[index] = baseY + kotlin.math.cos(localPhase * .83f) * displacement * .87f
    }
    return centersX to centersY
}

/**
 * GPU-accelerated Flowing Light backdrop using AGSL RuntimeShader (API 31+).
 * Drops the entire 9-field radial gradient math to the GPU via a single
 * fragment shader, eliminating the per-pixel CPU loop.
 *
 * On API < 31, falls back to the existing CPU-backed [MeloXFlowingLightBackdrop].
 */
@Composable
internal fun MeloXFlowingLightGpuBackdrop(
    artworkUrl: String?,
    isPlaying: Boolean,
    mediaId: String? = null,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val context = LocalContext.current
    var targetPalette by remember { mutableStateOf(ArtworkDynamicPalette.Fallback) }
    val renderingQuality = MeloXSettingsRuntime.lyricRenderingQuality
    val backgroundFrameRate = MeloXSettingsRuntime.lyricBackgroundFrameRate.coerceIn(15, 60)

    val shader = remember {
        if (isFlowingLightGpuSupported()) {
            android.graphics.RuntimeShader(FLOWING_LIGHT_SHADER)
        } else null
    }

    // Animation values stored outside Compose state to avoid recomposition.
    // Updated by coroutine, read by Canvas (which invalidates without recomposing).
    var phaseValue = 0f
    var energyValue = .18f
    var beatPulseValue = 0f
    var downbeatPulseValue = 0f
    var currentColorsValue = List(9) { index ->
        targetPalette.cells.getOrElse(index) { targetPalette.average }
    }
    var currentAverageValue = targetPalette.average
    var frameCounter by remember { mutableIntStateOf(0) }

    LaunchedEffect(artworkUrl) {
        targetPalette = ArtworkDynamicPaletteProvider.paletteFor(context, artworkUrl)
    }

    LaunchedEffect(isPlaying, visible, artworkUrl, mediaId, renderingQuality, backgroundFrameRate, targetPalette) {
        if (!visible) {
            awaitCancellation()
        }
        val requestedFrameDelayMs = (1_000L / backgroundFrameRate.toLong()).coerceAtLeast(1L)
        val frameDelayMs = when (renderingQuality) {
            MeloXLyricsRenderingQuality.Low -> requestedFrameDelayMs.coerceAtLeast(50L)
            MeloXLyricsRenderingQuality.Balanced -> requestedFrameDelayMs.coerceAtLeast(33L)
            MeloXLyricsRenderingQuality.High -> requestedFrameDelayMs.coerceAtLeast(16L)
        }

        if (!isPlaying) {
            val paletteBlend = .02f
            currentColorsValue = currentColorsValue.mapIndexed { i, c ->
                lerpColor(c, targetPalette.cells.getOrElse(i) { targetPalette.average }, paletteBlend)
            }
            currentAverageValue = lerpColor(currentAverageValue, targetPalette.average, paletteBlend)
            frameCounter++
            awaitCancellation()
        }

        while (true) {
            val sample = MeloXAudioReactiveRuntime.sample(mediaId)
            energyValue += (sample.energy - energyValue) * .18f
            beatPulseValue += (sample.beat - beatPulseValue) * .32f
            downbeatPulseValue += (sample.downbeat - downbeatPulseValue) * .24f
            val motion = .026f + energyValue.coerceIn(0f, 1f) * .038f + beatPulseValue * .016f
            phaseValue = (phaseValue + motion) % (Math.PI.toFloat() * 2f)
            val paletteBlend = (frameDelayMs / 800f).coerceIn(.02f, .18f)
            currentColorsValue = currentColorsValue.mapIndexed { i, c ->
                lerpColor(c, targetPalette.cells.getOrElse(i) { targetPalette.average }, paletteBlend)
            }
            currentAverageValue = lerpColor(currentAverageValue, targetPalette.average, paletteBlend)
            frameCounter++
            delay(frameDelayMs)
        }
    }

    // Read frameCounter to trigger Canvas invalidation without recomposition.
    @Suppress("UNUSED_VARIABLE")
    val _frameInvalidate = frameCounter

    if (shader != null) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val (centersX, centersY) = computeFlowingLightCenters(currentColorsValue, phaseValue, energyValue)
            val radiusNorm = (0.58f + energyValue.coerceIn(0f, 1f) * .08f + beatPulseValue * .035f)
                .coerceAtLeast(.01f)

            shader.setFloatUniform("u_resolution", size.width, size.height)
            shader.setFloatUniform("u_phase", phaseValue)
            shader.setFloatUniform("u_energy", energyValue)
            shader.setFloatUniform("u_beatPulse", beatPulseValue)
            shader.setFloatUniform("u_downbeatPulse", downbeatPulseValue)
            shader.setFloatUniform("u_radiusNorm", radiusNorm)

            for (i in 0 until 9) {
                val c = currentColorsValue.getOrElse(i) { currentAverageValue }
                shader.setColorUniform("u_c$i", android.graphics.Color.valueOf(c.toArgb()))
            }
            for (i in 0 until 9) {
                shader.setFloatUniform("u_c${i}c", centersX.getOrElse(i) { .5f }, centersY.getOrElse(i) { .5f })
            }
            shader.setColorUniform("u_average", android.graphics.Color.valueOf(currentAverageValue.toArgb()))

            drawRect(brush = ShaderBrush(shader))
        }
    } else {
        // API < 31 fallback: delegate to existing CPU implementation
        MeloXFlowingLightBackdrop(artworkUrl, isPlaying, mediaId, modifier)
    }
}
