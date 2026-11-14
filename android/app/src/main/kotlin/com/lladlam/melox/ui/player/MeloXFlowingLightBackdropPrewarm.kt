package com.lladlam.melox.ui.player

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.lladlam.melox.ui.settings.MeloXLyricsRenderingQuality
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Computes one static flowing-light frame ahead of the player opening.
 *
 * Only palette extraction and a single mesh fill run here — no animation loop
 * and no bitmap writes. The result is published into
 * [MeloXFlowingLightVisualStore], so the player's first frame after expanding
 * paints an already-correct backdrop instead of starting from the fallback
 * palette while the shell transition is still animating.
 */
internal object MeloXFlowingLightBackdropPrewarm {
    suspend fun prewarm(
        context: Context,
        artworkUrl: String?,
        outgoingArtworkUrl: String? = null,
        incomingArtworkUrl: String? = null,
    ) {
        val meshWidth = when (MeloXSettingsRuntime.lyricRenderingQuality) {
            MeloXLyricsRenderingQuality.Low -> 24
            MeloXLyricsRenderingQuality.Balanced -> 32
            MeloXLyricsRenderingQuality.High -> 48
        }
        val meshHeight = meshWidth * 23 / 10
        val saturation = MeloXSettingsRuntime.flowingLightSaturation.coerceIn(0f, 2f)
        val brightness = MeloXSettingsRuntime.flowingLightBrightness.coerceIn(.4f, 1.6f)
        val reduceMotion = MeloXSettingsRuntime.reduceMotion

        val appContext = context.applicationContext
        val sources = listOf(outgoingArtworkUrl ?: artworkUrl, incomingArtworkUrl)
            .filterNotNull().filter(String::isNotBlank).distinct()
        val palette = if (sources.isEmpty()) {
            ArtworkDynamicPalette.Fallback
        } else {
            val palettes = coroutineScope {
                sources.map { source -> async { ArtworkDynamicPaletteProvider.paletteFor(appContext, source) } }
                    .awaitAll()
            }
            palettes.reduceOrNull { first, second -> first.combinedWith(second) }
                ?: ArtworkDynamicPalette.Fallback
        }

        val pixels = withContext(Dispatchers.Default) {
            val colors = List(9) { index ->
                palette.cells.getOrElse(index) { palette.average }
            }
            val centersX = FloatArray(colors.size)
            val centersY = FloatArray(colors.size)
            val output = IntArray(meshWidth * meshHeight)
            fillFlowingMeshPixels(
                pixels = output,
                meshWidth = meshWidth,
                meshHeight = meshHeight,
                colors = colors,
                average = palette.average,
                phase = 0f,
                energy = .18f,
                beatPulse = 0f,
                reactive = false,
                saturation = saturation,
                brightness = brightness,
                centersX = centersX,
                centersY = centersY,
            )
            output
        }

        MeloXFlowingLightVisualStore.publish(
            artworkUrl = artworkUrl,
            mixOutgoingArtworkUrl = outgoingArtworkUrl,
            mixIncomingArtworkUrl = incomingArtworkUrl,
            meshWidth = meshWidth,
            meshHeight = meshHeight,
            saturation = saturation,
            brightness = brightness,
            reduceMotion = reduceMotion,
            palette = palette,
            colors = List(9) { index -> palette.cells.getOrElse(index) { palette.average } },
            average = palette.average,
            phase = 0f,
            energy = .18f,
            beatPulse = 0f,
            pixels = pixels,
        )
    }
}
