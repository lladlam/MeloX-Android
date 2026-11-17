package com.lladlam.melox.ui.sonify.artist.liquid

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle

/**
 * ClassYaba 式玻璃按钮描边 —— **只复刻浅色模式**；深色模式刻意不移植，保持项目原状。
 *
 * 配方出自 D:\adxm\ClassYaba 的 `ui/components/liquid/LiquidButton.kt`，由两层互补的
 * 描边叠加而成（数值逐项对齐，改动请两边同改）：
 *
 * 1. [classyabaBlackRim] —— **黑色环绕环**。用 [WrapHighlightStyle] 沿轮廓整圈连续：
 *    左右两侧最深、向上下平滑过渡但**不归零**（保留 `baseline × color.alpha`）。
 *    内置 [HighlightStyle.Default] 的强度是 `pow(abs(d), falloff)`（d = 边缘法线与
 *    angle 方向的点积），垂直中点 d = 0 → 强度恒为 0，无论 falloff 怎么调都做不出
 *    "整圈都有黑边"。ClassYaba 当初魔改出 [WrapHighlightStyle] 正是为这件事。
 * 2. [Modifier.classyabaWhiteEdge] —— **白色提亮边**。`HighlightStyle.Default(angle = 90)`
 *    直上直下，亮在上下直边、左右归零，与黑环互补，合起来才是"左右暗、上下亮"的
 *    立体描边。挂在 [emptyBackdrop] 上单独叠一层：不重新采样玻璃内容，也不会盖掉黑环。
 *
 * 与项目既有的 `circleEdgeLight` / `capsuleEdgeLight`（整圈均匀白描边）的区别正是这次
 * 要复刻的重点：均匀白环没有左右深色，按钮边缘缺少 ClassYaba 那种立体感。
 *
 * **用法（两层都要挂，缺一层就不是 ClassYaba 的观感）**
 * ```
 * Modifier
 *     .drawBackdrop(..., highlight = classyabaBlackRim)  // ① 黑环走材质自身的 highlight 槽
 *     .classyabaWhiteEdge(shape)                        // ② 白边追加在材质之后
 * ```
 * 这个顺序与 ClassYaba 的取法完全一致（那边同样是黑环交给材质的 `highlight` 参数、
 * 白边再追加一层 `drawBackdrop`），所以叠层顺序也天然一致：
 * `玻璃面 + 表面色 → 白边 → 黑环`（黑环 0.5dp 压在 1dp 白边内缘上，最外缘留一线白）。
 * 要照抄的是这个结果，而不是"谁写在前面"的字面直觉——修改器链里后写的节点先绘制。
 *
 * 白边追加在材质之后还有两个想要的效果：材质 `layerBlock` 的 alpha（如顶栏滚动联动的
 * 玻璃淡入淡出）建在外层 graphicsLayer 上，会把内层的白边一起带上；按钮自身的
 * `clip(shape)` 也罩得住，描边不会越出圆角。
 */

/**
 * 黑色环绕环（ClassYaba 浅色分支 `blackSideHighlight` 原值）：
 * 0.5dp 线宽 / 0.2dp 虚化 / 整层 alpha 1 / 左右最深（angle = 0）/ falloff 0.9 /
 * 上下保留 baseline 0.4（上下黑 ≈ 0.4 × 0.5 = 0.2，肉眼清晰可见）。
 *
 * `blendMode = SrcOver` 是硬性要求：[BlendMode.Plus] 对纯黑不可见（加色加不出黑）。
 *
 * 稳定单例：`HighlightElement.equals` 逐项比较回调引用，内联 lambda 每次重组都会被判成
 * "参数变了" → 白白 invalidateDraw，所以这里给顶层 val。
 */
val classyabaBlackRim: () -> Highlight? = {
    Highlight(
        width = 0.5f.dp,
        blurRadius = 0.2f.dp,
        alpha = 1f,
        style = WrapHighlightStyle(
            color = Color.Black.copy(alpha = 0.5f),
            blendMode = BlendMode.SrcOver,
            angle = 0f,
            falloff = 0.9f,
            baseline = 0.4f,
        ),
    )
}

/**
 * 白色提亮边覆盖层（ClassYaba 浅色分支 `defaultHighlight` 原值）：
 * 1dp 线宽 / 0.1dp 虚化 / 整层 alpha 0.8，`HighlightStyle.Default(angle = 90)`。
 *
 * 挂 [emptyBackdrop]：不采样玻璃（既省一层采样，也不会把下层的黑环盖掉），只贡献描边，
 * 因此能叠在任意既有材质（玻璃 / 磨砂 / 半透明纯色）之上。
 *
 * 注：`HighlightStyle.Default` 的着色器会把 color 的 alpha 强制成 1（本 fork 与 ClassYaba
 * 用的 backdrop 2.0.0 行为一致），实际深浅由 `alpha` 字段决定；`color.alpha = 0.4f`
 * 只是为与 ClassYaba 源码逐字可对照。
 *
 * @param shape 描边轮廓，必须与该按钮材质层所用的形状完全一致。
 */
fun Modifier.classyabaWhiteEdge(shape: Shape): Modifier = classyabaWhiteEdge(shape = { shape })

/** [classyabaWhiteEdge] 的 lambda 形状重载（轮廓需在绘制期求值时用，如随展开进度变形）。 */
fun Modifier.classyabaWhiteEdge(shape: () -> Shape): Modifier = this.drawBackdrop(
    backdrop = emptyBackdrop(),
    shape = shape,
    effects = ClassyabaEdgeNoEffects,
    highlight = ClassyabaWhiteEdgeHighlight,
    shadow = null,
    onDrawBackdrop = ClassyabaEdgeNoDraw,
)

private val ClassyabaWhiteEdgeHighlight: () -> Highlight? = {
    Highlight(
        // 比 ClassYaba 原值（1dp / alpha 0.8）更薄更浅：真机上 42dp 圆钮的上下亮边
        // 显得偏粗偏亮，压到 0.6dp / alpha 0.5 才和其它描边同一量级。
        width = 0.6f.dp,
        blurRadius = 0.1f.dp,
        alpha = 0.5f,
        style = HighlightStyle.Default(
            color = Color.White.copy(alpha = 0.4f),
            angle = 90f,
        ),
    )
}

/** 无效果链（稳定单例）：内联 `{}` 会让 `DrawBackdropElement.equals` 每次重组都判变。 */
private val ClassyabaEdgeNoEffects: BackdropEffectScope.() -> Unit = {}

/** 空 onDrawBackdrop：本层只画描边，不重复绘制内容。 */
private val ClassyabaEdgeNoDraw: DrawScope.(DrawScope.() -> Unit) -> Unit = {}
