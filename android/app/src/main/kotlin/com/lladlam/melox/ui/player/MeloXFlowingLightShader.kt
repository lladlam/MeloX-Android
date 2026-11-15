package com.lladlam.melox.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

/**
 * API-33-independent handle for the flowing-light AGSL shader.
 *
 * This interface deliberately has no [android.graphics.RuntimeShader] in its
 * signatures: API 29 devices must be able to verify and load this type without
 * resolving a class that only exists on Android 13+.
 */
internal interface MeloXFlowingLightShader {
    fun brush(): ShaderBrush

    fun update(
        canvasWidth: Float,
        canvasHeight: Float,
        colors: List<Color>,
        average: Color,
        phase: Float,
        energy: Float,
        beatPulse: Float,
        saturation: Float,
        brightness: Float,
        meshWidth: Int,
        meshHeight: Int,
    )
}
