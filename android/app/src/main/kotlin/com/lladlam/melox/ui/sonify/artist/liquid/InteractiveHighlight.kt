package com.lladlam.melox.ui.sonify.artist.liquid

import android.graphics.RuntimeShader
import android.os.Build
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: (Size, Offset) -> Offset = { _, offset -> offset },
    // 手势层叠在被选中 Tab 上方，普通点击不会穿透到底下的 Tab 按钮；
    // "按下后未拖动即抬起"视为点按当前选中 Tab（iOS 再点回根部语义）
    private val onTap: () -> Unit = {},
    /**
     * 光晕半径倍率（NexioSchedule 1.6.5 InteractiveHighlight 同义）：实际半径 = 基准 × 1.5 × 本值。
     * 在绘制期读取，可传会变的值（如下拉面板的展开进度）让光晕随状态收放。
     */
    private val radiusScale: () -> Float = { 1f },
    /**
     * 光晕半径上限（dp）。null = 直接用节点自身 minDimension（既有调用点行为不变）。
     * 非 null 时取 min(自身尺寸, 本值)：收起态的小圆按钮不会被大基准漫出圆外。
     */
    private val radiusBaseDp: Dp? = null,
    /**
     * 浅色模式的径向峰值。默认 0.15 = 既有调用点（顶栏玻璃圆钮等）的观感；
     * 下拉面板按 1.6.5 的全局规则传 0.05（近白底加白会被截断成白斑，必须压低）。
     *
     * 取 lambda 而非常量：绘制期直读，容器变换类控件可在展开途中从「钮」的 0.15
     * 连续收到「面板」的 0.05，不必重组。
     */
    private val lightPeakAlpha: () -> Float = { 0.15f },
    /** 深色模式峰值恒为 0.15；构造期传入（绘制期读不到 Composable 主题）。 */
    private val isDarkMode: Boolean = false,
) {
    private val press = Animatable(0f, 0.001f)
    private val location = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var start = Offset.Zero
    private var dragDistance = 0f

    // LiquidTopBarButton 需要在绘制期读取按压进度（layerBlock 缩放）与按压位移（向手指偏移）
    val pressProgress: Float get() = press.value
    val offset: Offset get() = location.value - start

    /**
     * 外部驱动接口（与 [gestureModifier] 同一套动画，供已消费指针事件的手势层
     * 直接调用）：滑选等独占手势会消费事件，自挂的 inspectDragGestures 看到
     * isConsumed 即取消——高光/形变需要由手势层经这三个方法全程驱动。
     * 坐标是**本节点局部坐标系**。
     */
    fun externalDragStart(position: Offset) {
        start = position
        animationScope.launch {
            launch { press.animateTo(1f, spring(0.5f, 300f, 0.001f)) }
            launch { location.snapTo(start) }
        }
    }

    fun externalDragMove(position: Offset) {
        animationScope.launch { location.snapTo(position) }
    }

    fun externalDragEnd() {
        animationScope.launch {
            launch { press.animateTo(0f, spring(0.5f, 300f, 0.001f)) }
            launch { location.animateTo(start, spring(0.5f, 300f, Offset.VisibilityThreshold)) }
        }
    }

    // 弥散高光：官方 demo 同款 AGSL，径向 smoothstep 渐隐 + Plus 混合；
    // 之前简化成 drawCircle 纯色圆，边缘生硬，视觉上退化为半透明遮罩
    private val shader =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
            )
        } else {
            null
        }

    val modifier = Modifier.drawWithContent {
        val progress = press.value
        if (progress > 0f) {
            val center = position(size, location.value)
            val peak = (if (isDarkMode) 0.15f else lightPeakAlpha()) * progress
            // 半径/圆心两个分支共用同一份计算，避免 shader 与渐变各算一份漂移掉
            val radiusBase = radiusBaseDp
            val radius = (if (radiusBase != null) minOf(size.minDimension, radiusBase.toPx())
            else size.minDimension) * 1.5f * radiusScale()
            val haloCenter = Offset(
                center.x.fastCoerceIn(0f, size.width),
                center.y.fastCoerceIn(0f, size.height)
            )
            if (shader != null) {
                drawRect(
                    Color.White.copy(0.08f * progress),
                    blendMode = BlendMode.Plus
                )
                shader.apply {
                    setFloatUniform("size", size.width, size.height)
                    setColorUniform("color", Color.White.copy(peak).toArgb())
                    setFloatUniform("radius", radius)
                    setFloatUniform("position", haloCenter.x, haloCenter.y)
                }
                drawRect(
                    ShaderBrush(shader),
                    blendMode = BlendMode.Plus
                )
            } else {
                // API < 33 无 AGSL：径向渐变模拟弥散，避免纯色圆的遮罩感
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(peak), Color.Transparent),
                        center = haloCenter,
                        radius = radius
                    ),
                    radius = radius,
                    center = haloCenter,
                    blendMode = BlendMode.Plus
                )
            }
        }
        drawContent()
    }
    val gestureModifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                start = down.position
                dragDistance = 0f
                animationScope.launch {
                    launch { press.animateTo(1f, spring(0.5f, 300f, 0.001f)) }
                    launch { location.snapTo(start) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { press.animateTo(0f, spring(0.5f, 300f, 0.001f)) }
                    launch { location.animateTo(start, spring(0.5f, 300f, Offset.VisibilityThreshold)) }
                }
                if (dragDistance < viewConfiguration.touchSlop) {
                    onTap()
                }
            },
            onDragCancel = {
                animationScope.launch {
                    press.animateTo(0f, spring(0.5f, 300f, 0.001f))
                    location.animateTo(start, spring(0.5f, 300f, Offset.VisibilityThreshold))
                }
            }
        ) { change, _ ->
            dragDistance += change.positionChange().getDistance()
            animationScope.launch { location.snapTo(change.position) }
        }
    }

    /**
     * **独占**指针的版本：[gestureModifier] 全程不 consume，因此外层若还挂着拖动容器
     * （画廊根节点的 `claimDragGestures`、列表滚动等），从本控件上起手滑动会**穿透**
     * 过去把外层一起拖走——实机表现就是"按住按钮拖动，卡片却跟着翻页"。
     *
     * 本变体在越过 `touchSlop` 后 consume 每一个 change，外层容器看到 `isConsumed`
     * 即放弃认领（`claimDragGestures` / `claimHorizontalDragGestures` 都以该判据取消），
     * 于是拖动只作用在控件自身：高光跟手、按钮倾斜，页面不动。
     *
     * `touchSlop` 之前**不** consume：这段窗口既是点按判定，也避免拇指按下时微抖
     * 就把外层的翻页手势掐掉。未越过 slop 即抬起 → [onTap]。
     *
     * 与 [gestureModifier] 的差异只有"consume"这一点，动画规格完全一致。
     */
    val claimingGestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            start = down.position
            dragDistance = 0f
            animationScope.launch {
                launch { press.animateTo(1f, spring(0.5f, 300f, 0.001f)) }
                launch { location.snapTo(start) }
            }

            var pointer = down.id
            var claimed = false
            var total = 0f
            var released = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.fastFirstOrNull { it.id == pointer }
                if (change == null) break
                if (change.changedToUpIgnoreConsumed()) {
                    released = true
                    break
                }
                val delta = change.positionChange()
                val distance = delta.getDistance()
                total += distance
                dragDistance += distance
                if (!claimed) {
                    if (total <= viewConfiguration.touchSlop) continue
                    claimed = true
                }
                // 越过 slop 后独占：外层滚动/翻页容器看到 isConsumed 即放弃
                change.consume()
                animationScope.launch { location.snapTo(change.position) }
            }

            animationScope.launch {
                launch { press.animateTo(0f, spring(0.5f, 300f, 0.001f)) }
                launch { location.animateTo(start, spring(0.5f, 300f, Offset.VisibilityThreshold)) }
            }
            if (released && !claimed) {
                onTap()
            }
        }
    }

    // 仅按压版：不消费指针事件、不跟手拖动，用 clickable 的按钮仍能正常收到点击。
    // 按下时把 location 吸附到按压点，弥散高光从手指位置绽开（Nexio 原版 pressOnly
    // 不更新 position，高光会固定在 (0,0) 角落，这里修正）。
    val pressOnlyModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            awaitEachGesture {
                val down = awaitFirstDown()
                start = down.position
                animationScope.launch {
                    location.snapTo(down.position)
                    press.animateTo(1f, spring(0.5f, 300f, 0.001f))
                }
                // 等待所有手指释放再回落，避免多指场景提前熄灭
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                } while (event.changes.any { it.pressed })
                animationScope.launch {
                    press.animateTo(0f, spring(0.5f, 300f, 0.001f))
                }
            }
        }
}
