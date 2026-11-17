package com.lladlam.melox.ui.sonify.artist.liquid

import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtMost
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.RuntimeShaderCache
import com.kyant.backdrop.highlight.HighlightStyle
import kotlin.math.PI

/**
 * 环绕式高光：沿 [angle] 方向（默认水平=左右两侧）最强，向垂直方向平滑过渡变浅，
 * 但整圈边缘都保留一定强度（由 [baseline] 控制），不会像 backdrop 1.0.5 内置的
 * [HighlightStyle.Default] 那样在垂直中点强度恒为 0。
 *
 * 内置 Default 的强度是 `pow(abs(d), falloff)`，d=边缘法线与 angle 方向的点积；
 * 上下中点 d=0 → 强度恒 0，无论 falloff 怎么调都做不到"整个按钮都有高光"。
 * 本实现改为 `baseline + (1-baseline) * pow(abs(d), falloff)`，保证整圈连续。
 *
 * 数值配方与 ClassYaba 的同名实现逐项一致（黑边整圈环绕 + 左右最深）。
 *
 * @param color 高光颜色（含 alpha 控制深浅）。
 * @param blendMode 黑色描边必须用 [BlendMode.SrcOver]（Plus 对黑色不可见）。
 * @param angle 最强方向，单位度。0=左右，90=上下。
 * @param falloff 过渡曲线指数。1=线性；>1 时最深处附近过渡慢、往两侧越来越快（缓入），
 *   可消除直边上的硬黑线。
 * @param baseline 最弱处（垂直方向）保留的强度比例 0..1，越大越均匀布满。
 */
@Immutable
data class WrapHighlightStyle(
    override val color: Color = Color.Black.copy(alpha = 0.24f),
    override val blendMode: BlendMode = BlendMode.SrcOver,
    val angle: Float = 0f,
    val falloff: Float = 1f,
    val baseline: Float = 0.35f,
) : HighlightStyle {

    override fun DrawScope.createShader(
        shape: Shape,
        runtimeShaderCache: RuntimeShaderCache
    ): RuntimeShader? {
        // backdrop fork（2.0.1）的 HighlightStyle.createShader 返回 RuntimeShader?，
        // 不再是 1.0.5 的 Shader?；AGSL RuntimeShader 需要 API 33+，
        // 低版本返回 null 回退为纯色描边。
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runtimeShaderCache.obtainRuntimeShader("WrapHighlight", WrapHighlightShaderString)
                .apply {
                    setFloatUniform("size", size.width, size.height)
                    setFloatUniform("cornerRadii", getCornerRadii(shape))
                    // 保留调色 alpha（不用 copy(alpha=1f)），深浅交给 color 而不是 Highlight.alpha。
                    setColorUniform("color", color)
                    setFloatUniform("angle", angle * (PI / 180f).toFloat())
                    setFloatUniform("falloff", falloff)
                    setFloatUniform("baseline", baseline)
                }
        } else {
            null
        }
    }
}

private fun DrawScope.getCornerRadii(shape: Shape): FloatArray {
    val size = size
    val maxRadius = size.minDimension / 2f
    // Kyant shapes（Capsule/RoundedRectangle）不是 Compose CornerBasedShape，
    // 走兜底 = 满圆角，对胶囊几何恰好正确。
    val cornerShape = shape as? CornerBasedShape ?: return FloatArray(4) { maxRadius }
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val topLeft = if (isLtr) cornerShape.topStart.toPx(size, this) else cornerShape.topEnd.toPx(size, this)
    val topRight = if (isLtr) cornerShape.topEnd.toPx(size, this) else cornerShape.topStart.toPx(size, this)
    val bottomRight = if (isLtr) cornerShape.bottomEnd.toPx(size, this) else cornerShape.bottomStart.toPx(size, this)
    val bottomLeft = if (isLtr) cornerShape.bottomStart.toPx(size, this) else cornerShape.bottomEnd.toPx(size, this)
    return floatArrayOf(
        topLeft.fastCoerceAtMost(maxRadius),
        topRight.fastCoerceAtMost(maxRadius),
        bottomRight.fastCoerceAtMost(maxRadius),
        bottomLeft.fastCoerceAtMost(maxRadius),
    )
}

private const val WrapHighlightShaderString = """
uniform float2 size;
uniform float4 cornerRadii;
layout(color) uniform half4 color;
uniform float angle;
uniform float falloff;
uniform float baseline;

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;

    // 渐变按「沿轴的位置比例」算，而不是方位角。
    // 胶囊左右是半圆帽，用方位角会让整个帽子都接近满强度→糊成黑点/黑块。
    // 改用位置比例：沿 axis 方向投影后除以该方向半长，左右最边缘=1、垂直中线=0，
    // 整条边平滑过渡，端帽不再堆积出硬黑点。
    float2 axis = float2(cos(angle), sin(angle));
    float proj = abs(dot(centeredCoord, axis));
    float halfExtent = abs(axis.x) * halfSize.x + abs(axis.y) * halfSize.y;
    float t = clamp(proj / max(halfExtent, 1.0), 0.0, 1.0);

    // 过渡曲线：pow(t, falloff)，t=1 为最深处(左右)，t=0 为最浅(上下)。
    //   falloff < 1（如 0.9）：起点(t=1)附近平缓=峰是"软"的不会是尖点，
    //     且暗部从起点就开始延长地往中间渐变 → 渐变被拉长、强度更柔。
    //   falloff = 1：线性。
    //   falloff > 1：暗部收紧到最边缘 → 越来越像尖点（不要）。
    float directional = pow(t, falloff);
    float intensity = baseline + (1.0 - baseline) * directional;
    return color * intensity;
}"""
