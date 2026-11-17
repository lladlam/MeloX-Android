/*
 * Sonify progressive-blur top bar (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/widgets/basic/TitleBarProgressiveBlur.kt
 * (master @ 209e1aa). The AGSL 32-tap golden-angle progressive blur, the tint
 * curve and the exported-backdrop recording are kept. The upstream file is
 * written against Sonify's backdrop fork (`downsampleScale`,
 * `RuntimeShaderEffect`); this adaptation uses the kyant backdrop 2.0.0
 * `runtimeShaderEffect` entry point and the same shader maths without the
 * downsample factor.
 */
package com.lladlam.melox.ui.sonify.artist

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect

private val MaxBlurRadius = 14.dp
private val BlurTail = 24.dp
private val FadeLength = 42.dp
private const val TINT_MAX_ALPHA = 0.8f

/**
 * 顶栏真·渐进模糊：模糊半径随 Y 连续变化（顶部最大、向下收到 0），
 * 缓冲用 backdrop 的 padding 向外扩一圈，模糊能采样到节点框外的真实内容。
 * [exportedBackdrop] 同时录下本层绘制结果（模糊 + 底色 + 淡出），供顶栏玻璃圆钮采样。
 */
@Composable
fun titleBarProgressiveBlur(
    backdrop: Backdrop,
    tintColor: Color,
    exportedBackdrop: LayerBackdrop? = null
): Modifier {
    val effects: com.kyant.backdrop.BackdropEffectScope.() -> Unit =
        remember(tintColor) {
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val maxRadiusPx = MaxBlurRadius.roundToPx().toFloat()
                    padding = maxRadiusPx
                    val pad = padding
                    val contentW = size.width
                    val contentH = size.height
                    val bufferW = contentW + 2f * pad
                    val bufferH = contentH + 2f * pad
                    val fadeStart = (size.height - FadeLength.toPx()).coerceAtLeast(0f)
                    runtimeShaderEffect("MeloXProgressiveBlur", PROGRESSIVE_BLUR_SHADER, "content") {
                        setFloatUniform("contentOrigin", pad, pad)
                        setFloatUniform("contentSize", contentW, contentH)
                        setFloatUniform("bufferSize", bufferW, bufferH)
                        setFloatUniform("maxRadius", maxRadiusPx)
                        setFloatUniform("fadeStart", fadeStart)
                        setColorUniform("tint", tintColor)
                        setFloatUniform("tintMax", TINT_MAX_ALPHA)
                    }
                } else {
                    blur(4.dp.toPx())
                }
            }
        }

    return Modifier
        .layout { measurable, constraints ->
            val tail = BlurTail.roundToPx()
            if (!constraints.hasBoundedHeight || tail <= 0) {
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            } else {
                val barHeight = constraints.maxHeight
                val placeable = measurable.measure(
                    constraints.copy(maxHeight = barHeight + tail)
                )
                layout(placeable.width, barHeight) { placeable.place(0, 0) }
            }
        }
        .drawPlainBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            effects = effects,
            exportedBackdrop = exportedBackdrop
        )
}

private const val SOFTER_STEP = """
float softerstep(float a, float b, float x) {
    float s = clamp((x - a) / max(b - a, 0.0001), 0.0, 1.0);
    return s * s * s * (s * (s * 6.0 - 15.0) + 10.0);
}
"""

private const val PROGRESSIVE_BLUR_SHADER = """
uniform shader content;
uniform float2 contentOrigin;
uniform float2 contentSize;
uniform float2 bufferSize;
uniform float maxRadius;
uniform float fadeStart;
layout(color) uniform half4 tint;
uniform float tintMax;

$SOFTER_STEP

float ign(float2 p) {
    return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715))));
}

half4 progressiveBlur(float2 coord, float radius) {
    if (radius < 0.5) {
        return content.eval(coord);
    }
    float ang = ign(coord) * 6.2831853;
    float ca = cos(ang);
    float sa = sin(ang);
    float2 u = float2(ca, sa) * radius;
    float2 v = float2(-sa, ca) * radius;
    float2 maxCoord = max(bufferSize - 1.0, float2(0.0));
    half4 sum = half4(0.0);
    float wsum = 0.0;
    half4 c;
    c = content.eval(coord);
    if (c.a > 0.02) { sum += c * 0.07571; wsum += 0.07571; }
    c = content.eval(clamp(coord + u * -0.20194 + v * 0.18499, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.07077; wsum += 0.07077; }
    c = content.eval(clamp(coord + u * 0.03091 + v * -0.35220, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.06765; wsum += 0.06765; }
    c = content.eval(clamp(coord + u * 0.25453 + v * 0.33199, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.06468; wsum += 0.06468; }
    c = content.eval(clamp(coord + u * -0.46709 + v * -0.08262, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.06183; wsum += 0.06183; }
    c = content.eval(clamp(coord + u * 0.44247 + v * -0.28146, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.05911; wsum += 0.05911; }
    c = content.eval(clamp(coord + u * -0.14800 + v * 0.55054, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.05651; wsum += 0.05651; }
    c = content.eval(clamp(coord + u * -0.28225 + v * -0.54345, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.05402; wsum += 0.05402; }
    c = content.eval(clamp(coord + u * 0.61236 + v * 0.22363, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.05165; wsum += 0.05165; }
    c = content.eval(clamp(coord + u * -0.63706 + v * 0.26297, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.04937; wsum += 0.04937; }
    c = content.eval(clamp(coord + u * 0.30711 + v * -0.65627, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.04720; wsum += 0.04720; }
    c = content.eval(clamp(coord + u * 0.22694 + v * 0.72353, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.04512; wsum += 0.04512; }
    c = content.eval(clamp(coord + u * -0.68401 + v * -0.39640, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.04314; wsum += 0.04314; }
    c = content.eval(clamp(coord + u * 0.80242 + v * -0.17641, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.04124; wsum += 0.04124; }
    c = content.eval(clamp(coord + u * -0.48971 + v * 0.69656, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.03942; wsum += 0.03942; }
    c = content.eval(clamp(coord + u * -0.11313 + v * -0.87304, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.03769; wsum += 0.03769; }
    c = content.eval(clamp(coord + u * 0.69453 + v * 0.58535, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.03603; wsum += 0.03603; }
    c = content.eval(clamp(coord + u * -0.93462 + v * 0.03865, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.03445; wsum += 0.03445; }
    c = content.eval(clamp(coord + u * 0.68173 + v * -0.67841, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.03293; wsum += 0.03293; }
    c = content.eval(clamp(coord + u * -0.04561 + v * 0.98637, float2(0.0), maxCoord));
    if (c.a > 0.02) { sum += c * 0.03148; wsum += 0.03148; }
    if (wsum < 0.0001) {
        return content.eval(coord);
    }
    return sum / wsum;
}

half4 main(float2 coord) {
    float2 local = coord - contentOrigin;
    if (local.x < 0.0 || local.y < 0.0 ||
        local.x >= contentSize.x || local.y >= contentSize.y) {
        return content.eval(coord);
    }
    float t = clamp(local.y / max(contentSize.y, 1.0), 0.0, 1.0);
    float u = 1.0 - smoothstep(0.0, 1.0, t);
    float radius = maxRadius * u;
    half4 color = progressiveBlur(coord, radius);
    float edge = softerstep(fadeStart, contentSize.y, local.y);
    color *= (1.0 - edge);
    if (tintMax > 0.0) {
        float presence = clamp(color.a, 0.0, 1.0);
        float tintCurve = u * u;
        color = mix(color, tint * ((1.0 - edge) * presence), tintMax * tintCurve * presence);
    }
    return color;
}
"""
