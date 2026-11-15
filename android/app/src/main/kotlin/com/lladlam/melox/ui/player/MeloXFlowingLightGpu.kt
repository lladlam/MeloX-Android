package com.lladlam.melox.ui.player

import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

/**
 * AGSL shader that reproduces `fillFlowingMeshPixels` entirely on the GPU.
 *
 * This class is referenced only through [Class.forName] from the common UI
 * code. R8 keeps it as-is, and API < 33 devices never load it, so the missing
 * `android.graphics.RuntimeShader` class cannot crash them during draw.
 *
 * The shader object is created once per composition and only its uniforms are
 * updated per frame; if any device rejects the program, callers fall back to
 * the CPU bitmap path instead of crashing.
 */
@Keep
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class MeloXFlowingLightGpu private constructor(
    private val shader: RuntimeShader,
) : MeloXFlowingLightShader {
    override fun brush(): ShaderBrush = object : ShaderBrush() {
        override fun createShader(size: androidx.compose.ui.geometry.Size): android.graphics.Shader = shader
    }

    override fun update(
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
    ) {
        shader.setFloatUniform("uResolution", canvasWidth, canvasHeight)
        val maxDimension = maxOf(meshWidth, meshHeight).toFloat().coerceAtLeast(1f)
        shader.setFloatUniform("uAspect", meshWidth / maxDimension, meshHeight / maxDimension)
        shader.setFloatUniform("uPhase", phase)
        shader.setFloatUniform("uEnergy", energy)
        shader.setFloatUniform("uBeatPulse", beatPulse)
        shader.setFloatUniform("uSaturation", saturation)
        shader.setFloatUniform("uBrightness", brightness)
        // Always fill all nine slots; unused ones fall back to the average color so
        // the shader never needs a dynamic loop bound.
        for (index in 0 until 9) {
            val color = colors.getOrNull(index) ?: average
            shader.setFloatUniform("uColor$index", color.red, color.green, color.blue)
        }
        shader.setFloatUniform("uAverage", average.red, average.green, average.blue)
    }

    companion object {
        private const val TAG = "MeloXFlowLightGpu"

        /** Public to reflection; kept by [Keep] and never referenced directly. */
        @Keep
        @JvmStatic
        fun create(): MeloXFlowingLightShader? = runCatching {
            MeloXFlowingLightGpu(RuntimeShader(AGSL))
        }.onSuccess {
            Log.i(TAG, "AGSL flowing-light shader ready")
        }.onFailure {
            Log.w(TAG, "AGSL flowing-light shader rejected; using CPU mesh", it)
        }.getOrNull()

        private const val AGSL = """
uniform vec2 uResolution;
uniform vec2 uAspect;
uniform float uPhase;
uniform float uEnergy;
uniform float uBeatPulse;
uniform float uSaturation;
uniform float uBrightness;
uniform vec3 uColor0;
uniform vec3 uColor1;
uniform vec3 uColor2;
uniform vec3 uColor3;
uniform vec3 uColor4;
uniform vec3 uColor5;
uniform vec3 uColor6;
uniform vec3 uColor7;
uniform vec3 uColor8;
uniform vec3 uAverage;

// Quarter-turn frequency multiples keep the 8pi phase wrap seamless.
vec2 roamCenter(int index, float t) {
    if (index == 0) return vec2(0.5 + 0.3 * sin(t * 0.50 + 0.0), 0.5 + 0.3 * cos(t * 0.25 + 0.0));
    if (index == 1) return vec2(0.5 + 0.3 * sin(t * 0.25 + 1.7), 0.5 + 0.3 * cos(t * 0.75 + 0.9));
    if (index == 2) return vec2(0.5 + 0.3 * sin(t * 0.75 + 3.4), 0.5 + 0.3 * cos(t * 0.50 + 1.8));
    if (index == 3) return vec2(0.5 + 0.3 * sin(t * 0.50 + 5.1), 0.5 + 0.3 * cos(t * 0.75 + 2.7));
    if (index == 4) return vec2(0.5 + 0.3 * sin(t * 0.25 + 6.8), 0.5 + 0.3 * cos(t * 0.50 + 3.6));
    if (index == 5) return vec2(0.5 + 0.3 * sin(t * 0.75 + 8.5), 0.5 + 0.3 * cos(t * 0.25 + 4.5));
    if (index == 6) return vec2(0.5 + 0.3 * sin(t * 0.50 + 10.2), 0.5 + 0.3 * cos(t * 0.50 + 5.4));
    if (index == 7) return vec2(0.5 + 0.3 * sin(t * 0.25 + 11.9), 0.5 + 0.3 * cos(t * 0.25 + 6.3));
    return vec2(0.5 + 0.3 * sin(t * 0.75 + 13.6), 0.5 + 0.3 * cos(t * 0.75 + 7.2));
}

vec3 colorAt(int index) {
    if (index == 0) return uColor0;
    if (index == 1) return uColor1;
    if (index == 2) return uColor2;
    if (index == 3) return uColor3;
    if (index == 4) return uColor4;
    if (index == 5) return uColor5;
    if (index == 6) return uColor6;
    if (index == 7) return uColor7;
    return uColor8;
}

vec3 flowingLight(vec2 fragCoord) {
    vec2 uv = fragCoord / max(uResolution, vec2(1.0));
    float t = uPhase;
    float energy = clamp(uEnergy, 0.0, 1.0);
    float beat = uBeatPulse;
    float radius = max(0.45 + energy * 0.07 + beat * 0.03, 0.01);
    float warp = 0.15 + energy * 0.05 + beat * 0.02;

    float warpX = sin(uv.y * 3.1 + t) * cos(uv.x * 2.3 - t * 0.75)
        + 0.5 * sin((uv.x + uv.y) * 4.9 - t * 1.25);
    float warpY = cos(uv.x * 2.9 - t) * sin(uv.y * 2.6 + t * 0.5)
        + 0.5 * cos((uv.x - uv.y) * 5.4 + t);

    vec2 warpedUv = uv + warp * vec2(warpX, warpY);
    warpedUv.x += warp * 0.28 * sin(warpedUv.y * 6.7 - t * 1.5);
    warpedUv.y += warp * 0.28 * cos(warpedUv.x * 7.1 + t * 1.5);

    float totalWeight = 0.16;
    vec3 sum = uAverage * totalWeight;

    for (int i = 0; i < 9; i++) {
        vec2 delta = (warpedUv - roamCenter(i, t)) * uAspect;
        float dist2 = dot(delta, delta) / (radius * radius);
        float falloff = 1.0 / (1.0 + dist2 * 4.5);
        float weight = falloff * falloff;
        totalWeight += weight;
        sum += colorAt(i) * weight;
    }

    vec3 rgb = sum / totalWeight;

    if (uSaturation != 1.0) {
        float lum = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
        rgb = lum + (rgb - lum) * uSaturation;
    }
    rgb *= uBrightness;

    return clamp(rgb, 0.0, 1.0);
}

half4 main(vec2 fragCoord) {
    return half4(half3(flowingLight(fragCoord)), 1.0);
}
"""
    }
}
