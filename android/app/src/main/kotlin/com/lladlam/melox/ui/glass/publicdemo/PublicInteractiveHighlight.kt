package com.lladlam.melox.ui.glass.publicdemo

/*
 * Based on the InteractiveHighlight demo shipped with
 * Kyant0/AndroidLiquidGlass (Apache-2.0). Kept as a tiny local adapter so the
 * app can use the demo's press/drag behavior without depending on the demo
 * application module, which is not published as a Maven artifact.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 光斑半径 = `minDimension × 该系数`（shader 分支与软件分支共用同一个口径）。 */
private const val FOCUS_GLOW_RADIUS_RATIO = 1.5f

/** 软件复刻光斑的等距采样点数（含两端）。 */
private const val FOCUS_GLOW_STEPS = 11

/**
 * 径向光斑的**强度剖面**（与 shader 里那行 `smoothstep` 逐字等价）。
 *
 * `W(u) = 1`（u ≤ plateau，平顶）；`plateau ~ 1` 按 `t²(3 − 2t)` 平滑降到 0。
 *
 * 🔴 2026-10-28（用户：「有光斑，但是只有一小团，没有 kyant0 的那种」）：
 *   上游 `plateau = 0.5` 是给 **200×48dp 胶囊**选的 —— 平顶半径 36dp，
 *   胶囊只有 24dp 高 ⇒ 整颗胶囊都落在平顶里 = 「整块被照亮」，看不出「团」。
 *   换到 **40dp 圆钮**上，同一个 0.5 ⇒ 平顶半径只有 15dp，亮点是个小球，
 *   剩下的半程又陡 ⇒ 观感就是「一小团」。
 *   ⇒ 把 plateau 降下来：亮核变大、过渡拉长，整颗按钮像被一层柔光罩住。
 *     ⚠ 但 plateau **不能低于 0.3**：再低平顶会吃掉整个按钮（边界强度仍很高），
 *       被玻璃胶囊裁掉时就是用户说的「截断」。
 *
 * @param u 归一化距离 `dist / radius`（0 = 圆心，1 = 光斑边缘）
 * @param plateau 平顶的比例（默认 0.5 = 上游）
 */
private fun focusGlowWeight(u: Float, plateau: Float): Float {
    if (u <= plateau) return 1f
    val t = ((u - plateau) / (1f - plateau)).coerceIn(0f, 1f)
    return 1f - t * t * (3f - 2f * t)
}

/**
 * 软件复刻径向光斑的**强度采样表**：从圆心（0）到光斑边缘（1）**等距 11 点**。
 *
 * ⚠ 为什么要**等距**：这样可以直接喂 `Brush.radialGradient(colors = ...)` 那个
 *   「不带 stops 参数」的老重载（颜色默认均匀分布），不必依赖新版的
 *   `vararg colorStops: Pair<Float, Color>` 重载，也不用为 spread 的写法纠结。
 *
 * ⚠ 改这里（或 [focusGlowWeight]）等于改光影形状，必须与 shader 里的
 *   `smoothstep(radius, radius * plateau, dist)` 一起改。
 */
private fun focusGlowWeights(plateau: Float): List<Float> =
    List(FOCUS_GLOW_STEPS) { i ->
        focusGlowWeight(i / (FOCUS_GLOW_STEPS - 1).toFloat(), plateau)
    }

/** Public-demo press highlight with a moving optical focus point. */
class PublicInteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    // 辉光强度（默认 = kyant0 官方原值）。底栏涂层厚（0.52）+ blur 仅 2dp，
    // 官方 0.08/0.15 照上去几乎看不见（真机实测峰值仅 +67/255）⇒ 底栏调用点传更大的值。
    private val panelGlowAlpha: Float = 0.08f,
    private val focusGlowAlpha: Float = 0.15f,
    /**
     * 没有 AGSL（API < 33）时，「手指处径向辉光」改用**纯 Compose 径向渐变**复刻。
     *
     * 🔴 2026-10-28（用户指令：「保持不变，但高光还是要能跟随手指移动」）：
     *   这一笔在上游唯一的实现是 `RuntimeShader`（AGSL 33+）。低版本没有 shader，
     *   上游 fallback 就把它**折进整块矩形**（`panelGlow + focusGlow`）⇒ 变成
     *   「整颗 30dp 圆钮均匀发白」，既不跟随手指、也不是光斑。
     *
     *   现在改成：同样的参数（圆心 = 手指、半径 = `minDimension * 1.5`、
     *   峰值 = [focusGlowAlpha]、`BlendMode.Plus`）用 `Brush.radialGradient`
     *   画一遍 —— 渐变颜色就是那支 shader 的 `smoothstep(radius, radius*0.5, dist)`
     *   采样出来的（见 [focusGlowWeight]），所以**形状、亮度、跟随行为逐像素等价**，
     *   只是不再需要 AGSL。`drawRect` + 径向渐变在 API 21+ 都成立。
     *
     * ⚠ **默认 false = 保持 kyant0 上游行为**，其它所有调用点（导航底栏水珠/面板、
     *   玻璃按钮、设置下拉）逐像素不变。只有迷你栏传 true —— 它要的是
     *   「按压反馈只落在那两颗 30dp 圆钮上、且光斑跟着手指走」。
     */
    private val softwareFocusGlow: Boolean = false,
    /**
     * 光斑半径 = `minDimension × 本系数`（上游 1.5）。
     *
     * 🔴 2026-10-28（用户两轮：「高光只在右边四分之一区域生效，多了会被截断，且位置在
     *   手指下面」）：这个系数**必须按宿主尺寸选**，不是常数：
     *   · shader 的剖面是 `smoothstep(radius, radius*0.5, dist)` ⇒ **`0.5r` 以内是平顶**
     *     （恒定满强度）、`0.5r~r` 才是渐变。
     *   · 上游宿主是「200×48dp 的胶囊按钮」⇒ `1.5r = 72dp`、平顶直径 72dp，
     *     手指在胶囊里横着滑，光斑的渐变环沿胶囊移动 ⇒ 看得出「跟手」。
     *   · 我们的宿主是 **40dp 圆钮** ⇒ 沿用 1.5 时 `r = 60dp`、平顶半径 30dp，
     *     而圆钮半对角只有 28.3dp ⇒ **整个按钮都落在平顶里**，手指怎么动观感都不变
     *     （这才是「不跟随」的真正原因；坐标夹取只是次要）。
     *   ⇒ 让调用点按自己的尺寸挑系数，使「渐变环」落在按钮内部。迷你栏用 0.5。
     *
     * ⚠ 默认 = 上游 1.5，其余调用点（底栏水珠/面板、玻璃按钮、设置下拉）逐像素不变。
     */
    private val focusGlowRadiusRatio: Float = FOCUS_GLOW_RADIUS_RATIO,
    /**
     * 允许「面板提亮」与「手指光斑」**画出节点边界**（默认关闭 = 上游口径）。
     *
     * 🔴 2026-10-28（用户：「现在高光只在圈里面了」）：迷你栏这两颗 30/36dp 圆钮上，
     *   光斑被关在圆圈里有**两个**叠加原因，缺一不可：
     *   ① 调用点的 `clip(CircleShape)` 挂在 `press.highlight.modifier` **外层**
     *      （modifier 链里越靠前越外）⇒ 高光整体被裁成圆；
     *   ② `drawRect(brush)` 的默认 `size` = 节点尺寸 ⇒ 半径再大，超出的部分也画不出来。
     *   ⇒ 这一笔同时解决 ②：改成 `drawCircle`（`center`/`radius` 都是节点局部坐标，
     *     不经过 `drawRect` 的 `translate`，所以**没有坐标系歧义**、也不受矩形边界限制）。
     *     ① 由调用点把 `press.highlight.modifier` 挪到 `clip` 之前解决。
     *
     * ⚠ 一旦光斑能溢出，**面板提亮也必须换成圆**：整节点 `drawRect` 是方的，
     *   离开 `clip` 之后会在圆钮四角露出方角白斑。
     *
     * ⚠ 默认 false ⇒ 其余调用点（底栏水珠/面板、玻璃按钮、设置下拉）逐像素不变。
     */
    private val allowPointerGlowOverflow: Boolean = false,
    /**
     * 光斑剖面的**平顶比例**（`dist ≤ plateau × radius` 处强度恒为 1）。
     *
     * 默认 0.5 = kyant0 上游（在 200×48dp 胶囊上「整颗胶囊被照亮」）。
     * 小宿主（40dp 圆钮）要传更小的值：0.5 时平顶半径只剩 15dp，
     * 看上去就是一个亮球 = 用户说的「只有一小团」。见 [focusGlowWeight]。
     */
    private val focusGlowPlateau: Float = 0.5f,
    /**
     * 光斑的**纵向压缩**系数（1 = 正圆，上游口径）。
     *
     * 🔴 2026-10-28（用户：「大小被限制成了那个圆形容器的大小」）：宿主是
     *   **40dp 圆钮、48dp 玻璃壳** ⇒ 按钮中心到壳上/下沿只有 **24dp**，
     *   壳还会 `clip` 成胶囊 —— 等向圆一旦超过 24dp 就被硬切（= 用户说的「截断」），
     *   不超过 24dp 又只能跟按钮一样大。这是几何死结。
     *   ⇒ 横向空间其实很充裕（整条迷你栏），所以把光斑压成**横向铺开的椭圆**：
     *     半径按 [focusGlowRadiusRatio] 给到 30dp（横向真正出圈），纵向乘本系数
     *     收到 21.6dp（在 24dp 边界之前就归零，不会撞到胶囊边）。
     *   ⚠ 只在 `allowPointerGlowOverflow` 分支生效；默认 1f ⇒ 其余调用点不变。
     */
    private val focusGlowSquash: Float = 1f,
) {
    private val pressSpec = spring<Float>(0.5f, 300f, 0.001f)
    private val positionSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)
    private val pressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var startPosition = Offset.Zero

    val pressProgress: Float get() = pressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    /**
     * 供**外部手势**（如迷你栏「横滑切歌」）手动驱动高光：在 [at] 处亮起并跟随手指。
     *
     * 🔴 2026-10-28（用户：「给滑动歌曲加上跟随高光」）：滑动切歌走的是自己的
     *   `detectHorizontalDragGestures`（横滑要消费事件、且不能触发整栏按压缩放），
     *   不能用本类的 `gestureModifier`（那是按压模型）。于是把「亮起 / 跟随 / 收起」
     *   拆成三个方法，由滑动手势在 `onDragStart / onHorizontalDrag / onDragEnd` 里调用，
     *   绘制仍复用同一套 `modifier`（shader 与软件渐变两条分支都生效）。
     */
    fun beginAt(at: Offset) {
        startPosition = at
        animationScope.launch {
            launch { pressAnimation.animateTo(1f, pressSpec) }
            launch { positionAnimation.snapTo(at) }
        }
    }

    /** 高光跟随手指移动到 [to]（节点局部坐标）。 */
    fun moveTo(to: Offset) {
        animationScope.launch { positionAnimation.snapTo(to) }
    }

    /** 结束（松手 / 取消）：高光淡出并回到起点。 */
    fun finish() {
        animationScope.launch {
            launch { pressAnimation.animateTo(0f, pressSpec) }
            launch { positionAnimation.animateTo(startPosition, positionSpec) }
        }
    }

    private val shader = if (isRuntimeShaderSupported()) {
        RuntimeShader(
            """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float plateau;
uniform float2 position;
half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * plateau, dist);
    return color * intensity;
}
""".trimIndent(),
        )
    } else null

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressAnimation.value
        if (progress > 0f) {
            // ── ① 面板提亮（整颗按钮 +panelGlowAlpha 白，上游原样）─────────────────
            if (allowPointerGlowOverflow) {
                // 允许溢出时不能用整节点 `drawRect`：离开 `clip` 之后会在圆钮四角
                // 露出方角白斑 ⇒ 按节点内切圆来画（= 按钮本身的形状）。
                drawCircle(
                    color = Color.White.copy(alpha = panelGlowAlpha * progress),
                    radius = size.minDimension * 0.5f,
                    blendMode = BlendMode.Plus,
                )
            } else {
                drawRect(Color.White.copy(alpha = panelGlowAlpha * progress), blendMode = BlendMode.Plus)
            }

            // ── ② 以手指为圆心的径向光斑 ──────────────────────────────────────────
            // 🔴🔴 2026-10-28 修（用户：「高光只在右边四分之一区域生效，多了会被截断，
            //   而且位置在手指下面」）：上一版为了「让光斑画出按钮之外」，在绘制区域外扩
            //   `pad = radius` 的同时把 `position` 也 `+ pad` 平移了 —— **那是错的**：
            //   `RuntimeShader` 的 `coord` 与 `Brush` 的 `center` 都是**绘制节点的局部
            //   坐标**，跟「矩形画在哪个 topLeft」无关。多出来的 `+ pad`（= 60dp）把整个
            //   光斑推到了手指的右下方，40dp 圆钮上只剩右下角约 1/4 落在平顶区 ⇒
            //   用户看到的那两个现象。
            //   ⇒ 现在的写法与上游逐字一致：整块 `drawRect`（不带 topLeft，覆盖整个节点）
            //     + `position` 就是手指坐标（只按上游口径夹回节点内）。
            val focus = position(size, positionAnimation.value)
            val radius = size.minDimension * focusGlowRadiusRatio
            val peak = focusGlowAlpha * progress
            if (radius > 0f && peak > 0f) {
                if (allowPointerGlowOverflow) {
                    // 🔴 溢出分支：一律 `drawCircle`。
                    //   · `center` / `radius` 都是**节点局部坐标**，`drawCircle` 不会像
                    //     `drawRect(brush, topLeft, size)` 那样替我们 `translate` ⇒ 圆心
                    //     就是手指、半径就是光斑半径，**没有任何需要补偿的偏移**；
                    //   · 圆可以画到节点之外（只要上层不再 clip），半径不必内切于按钮；
                    //   · `shader` 与软件渐变两条分支的输入口径完全一致（同一 radius /
                    //     center / peak / Plus），所以 API 33 上下观感相同。
                    //   ⚠ 不再夹取坐标：夹回节点内会把「手指滑到按钮边缘时探出去的那半
                    //     个光斑」按回圆里（正是「不跟手」的一半来源）。
                    val brush = if (shader != null) {
                        shader.setFloatUniform("size", radius * 2f, radius * 2f)
                        shader.setColorUniform("color", Color.White.copy(alpha = peak))
                        shader.setFloatUniform("radius", radius)
                        shader.setFloatUniform("plateau", focusGlowPlateau)
                        shader.setFloatUniform("position", focus.x, focus.y)
                        ShaderBrush(shader.asComposeShader())
                    } else {
                        // 没有 AGSL（API < 33）：用纯 Compose 径向渐变**复刻同一支 shader**
                        // （颜色表 = `smoothstep(radius, radius*plateau, dist)` 的等距采样，
                        //   见 [focusGlowWeight]）⇒ 低版本照样跟随手指移动。
                        Brush.radialGradient(
                            colors = focusGlowWeights(focusGlowPlateau)
                                .map { Color.White.copy(alpha = peak * it) },
                            center = focus,
                            radius = radius,
                        )
                    }
                    // ⚠ `focusGlowSquash < 1` 时按**椭圆**画：canvas 纵向压扁，
                    //   pivot = 手指 ⇒ 圆心不动，只是纵向半径变小（见该参数注释）。
                    scale(scaleX = 1f, scaleY = focusGlowSquash, pivot = focus) {
                        drawCircle(
                            brush = brush,
                            radius = radius,
                            center = focus,
                            blendMode = BlendMode.Plus,
                        )
                    }
                } else {
                    val cx = focus.x.fastCoerceIn(0f, size.width)
                    val cy = focus.y.fastCoerceIn(0f, size.height)
                    if (shader != null) {
                        shader.apply {
                            setFloatUniform("size", size.width, size.height)
                            setColorUniform("color", Color.White.copy(alpha = peak))
                            setFloatUniform("radius", radius)
                            setFloatUniform("plateau", focusGlowPlateau)
                            setFloatUniform("position", cx, cy)
                        }
                        drawRect(ShaderBrush(shader.asComposeShader()), blendMode = BlendMode.Plus)
                    } else if (softwareFocusGlow) {
                        // 没有 AGSL（API < 33）：用纯 Compose 径向渐变**复刻同一支 shader**
                        // （颜色表 = `smoothstep(radius, radius*plateau, dist)` 的等距采样，
                        //   见 [focusGlowWeight]）⇒ 低版本照样跟随手指移动。
                        // ⚠ `Brush` 的 `center` 同样是节点局部坐标，**不要**加任何偏移。
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = focusGlowWeights(focusGlowPlateau)
                                    .map { Color.White.copy(alpha = peak * it) },
                                center = Offset(cx, cy),
                                radius = radius,
                            ),
                            blendMode = BlendMode.Plus,
                        )
                    } else {
                        // 无 AGSL 且未开软件复刻（其余调用点，= kyant0 上游行为）：
                        // 这一笔折进整块矩形（Plus 加法 ⇒ 与上游一次画两笔等价）。
                        drawRect(Color.White.copy(alpha = peak), blendMode = BlendMode.Plus)
                    }
                }
            }
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        publicInspectDragGestures(
            onDragStart = { down ->
                startPosition = down
                animationScope.launch {
                    launch { pressAnimation.animateTo(1f, pressSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { pressAnimation.animateTo(0f, pressSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionSpec) }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch { pressAnimation.animateTo(0f, pressSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionSpec) }
                }
            },
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }
}

internal suspend fun PointerInputScope.publicInspectDragGestures(
    onDragStart: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit,
) {
    // This is the catalog helper's key behavior: press starts on pointer
    // down, before Compose's drag-slop threshold is crossed.
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        onDragStart(down.position)
        onDrag(initialDown, Offset.Zero)
        val upEvent = drag(initialDown.id) { change ->
            onDrag(change, change.positionChange())
        }
        if (upEvent == null) onDragCancel() else onDragEnd()
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    if (currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.changedToUpIgnoreConsumed()) return change
        // ⚠ 不要像官方 DragGestureInspector 那样 `isConsumed → 退出`：
        //   水珠上同一修饰符链里 `dampedDock.modifier` 在更内层、Main pass 先拿到事件
        //   并 `change.consume()`；这里一退就是 onDragCancel → 高光被提前杀掉
        //   （真机症状：按住有高光、手指一动就没）。
        //   位移已被消费不影响本用途 —— 高光的 onDrag 只读绝对 position。
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId,
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (change.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) return change
            pointer = otherDown.id
        } else if (change.previousPosition != change.position) {
            return change
        }
    }
}
