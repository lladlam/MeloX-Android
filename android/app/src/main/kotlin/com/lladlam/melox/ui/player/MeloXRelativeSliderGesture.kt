package com.lladlam.melox.ui.player

import android.os.SystemClock
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/*
 * ★ 2026-11-07：音乐进度条 / 音量条（含音频输出页）统一的「按压放大 + 相对拖动」交互。
 *
 * 用户口径：
 *   ① 被按住的控件放大显示，松手后恢复原本尺寸；
 *   ② 首次按下无论触点位于何处，都保持当前进度不变，不让进度跳到手指位置；
 *   ③ 拖动时以按下瞬间的原进度为基准，仅按手指相对起点的左右位移增减，
 *      不再按手指的绝对位置定位进度；
 *   ④ 按下 / 拖动 / 松手状态连贯，数值始终留在原有范围内；
 *   ⑤ （2026-11-07 追加）**与「点一下跳转」共存**：快速点按（未拖动且按压时长
 *      ≤ [MeloXSliderTapMaxDurationMs]）在**松手时**跳到点按位置；按住不放再松手
 *      或真正拖动过，一律不跳 —— 也即②在按压期间始终成立，只是松手时补一次点按语义。
 *
 * ⚠ 覆盖范围只限「音乐进度 + 音量」两类：设置页浮点滑条 / 歌词时间偏移走
 *   [com.lladlam.melox.ui.glass.MeloXLiquidSlider]，**不受本文件影响**（用户明确
 *   「音乐进度，音量进度就够了」，且要求其它页面行为不变）。
 *
 * ⚠ 设计要点：不接管 Material3 Slider 的手势，只在它的 onValueChange 回调里做
 *   「绝对值 → 相对增量」换算。Slider 每次手势回调的都是手指**绝对**位置换算出的值，
 *   因此 (raw − anchorRaw) 恰好就是手指相对按下位置的位移（已按 valueRange 归一化）；
 *   第一次回调只用来锚定，返回 null ⇒ 不通知业务层 ⇒ 点按 / 首次按下都不会跳值。
 *   这样 thumb / track / colors / steps / enabled / 无障碍全部保持原实现，视觉零改动。
 *
 * ⚠ 按压缩放走 drawWithContent 的画布矩阵而不是 graphicsLayer：
 *   graphicsLayer 会改变命中测试的坐标变换，手指 x 被反向缩放后会让相对位移的
 *   灵敏度偏掉；drawWithContent 只改绘制、不动 hit test，且静息 s==1f 直接透传
 *   ⇒ 静止态逐像素等价、按压期零重组（scaleState 只在绘制期读 .value）。
 */

/** 按住时的整体缩放；静息恒 1f。无过冲，松手精确回到原尺寸。 */
internal const val MeloXSliderPressedScale = 1.10f

/**
 * 「快速点按」判定窗口：从按下到松手的时长上限。
 *
 * ⚠ 为什么要这道闸门：没有它，任何「按住不动 → 松手」都会跳值，而用户的原始口径是
 *   「首次按下保持当前进度不变」——按住端详一会儿再松手却突然跳走，属于同一条报障。
 *   400ms 覆盖正常点按（实测 100~250ms），又把「按住不放」排除在外。
 */
internal const val MeloXSliderTapMaxDurationMs = 400L

/**
 * 判定「位置真的动了」的最小归一化位移（全量程的 0.01%）。
 * 300px 宽的条上约 0.03px ⇒ 远远小于触摸 slop，既过滤掉同位置重复回调，也不会形成死区。
 */
private const val MeloXSliderValueEpsilon = 1e-4f
/** 一次手势的收尾结果。 */
internal class MeloXSliderGestureEnd(
    /** 本次手势是否真的拖动过（发生过值变化）。 */
    val dragged: Boolean,
    /** 快速点按时手指落点的值（未拖动 + 按压时长在窗口内）；其余情况为 null。 */
    val tapValue: Float?,
)

@Stable
internal class MeloXRelativeSliderGesture(
    private val valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    /** 按下瞬间的原进度，整段手势保持不变。 */
    private var startValue = 0f

    /** 首次回调时手指绝对位置换算出的值，后续所有回调都减它 ⇒ 只剩相对位移。 */
    private var anchorRaw = 0f

    /** 自定义手势（音频输出页）累计的归一化位移。 */
    private var accumulated = 0f

    /** 按下时刻（uptimeMillis）；0 = 没有有效按下记录。 */
    private var downAtMs = 0L

    private var captured = false
    private var changed = false

    /** 手指是否按在控件上。由 Initial-pass 旁听驱动，不依赖 Slider 内部实现。 */
    var pressed by mutableStateOf(false)
        internal set

    /** 新手势开始：清掉上一段的锚点与标记（幂等，down 与 onDragStart 都会调）。 */
    fun reset() {
        captured = false
        accumulated = 0f
        changed = false
        anchorRaw = 0f
        downAtMs = SystemClock.uptimeMillis()
    }

    /**
     * Material3 Slider 的 onValueChange 入口。
     *
     * @param raw Slider 按手指**绝对**位置算出的值（已按 steps 量化）。
     * @param currentValue 首次回调时的显示值，用作整段拖动的基准；后续回调不会再用它，
     *   因此即便外层值随拖动变化也不会污染基准。
     * @return 首次按下返回 null（业务层完全不收到回调 ⇒ 进度不跳）；之后返回相对值。
     */
    fun onAbsoluteValue(raw: Float, currentValue: Float): Float? {
        val clamped = raw.coerceIn(valueRange.start, valueRange.endInclusive)
        if (!captured) {
            captured = true
            // 兜底：某些调用点（自实现手势）不走 [reset]，这里补一次按下时刻，
            // 否则点按窗口永远算不出时长 ⇒ 点按跳转会被误判成「按住」而失效。
            if (downAtMs == 0L) downAtMs = SystemClock.uptimeMillis()
            anchorRaw = clamped
            startValue = currentValue.coerceIn(valueRange.start, valueRange.endInclusive)
            return null
        }
        val delta = clamped - anchorRaw
        // ⚠ 位移小于一个像素的量级时**既不改值也不标记 changed**：
        //   Material3 Slider 在「按下 / 拖动起步」阶段可能用同一位置重复回调一次，
        //   若把这种重复当成本次手势「拖动过」，快速点按就会被误判 ⇒ 点按跳转永远失效。
        if (abs(delta) <= MeloXSliderValueEpsilon) return null
        changed = true
        return (startValue + delta).coerceIn(valueRange.start, valueRange.endInclusive)
    }

    /**
     * 自实现手势（音频输出页 OutputPanelSlider）的入口：直接吃每帧 dragAmount.x。
     * 天然是「按下瞬间原进度 + 相对位移累加」，与 [onAbsoluteValue] 共用同一套
     * 范围钳制与按下 / 松手状态。
     *
     * @return 尚未产生有效位移时返回 null；其余返回钳制后的新值。
     */
    fun onDragAmount(deltaPx: Float, trackWidthPx: Int, currentValue: Float): Float? {
        if (deltaPx == 0f) return null
        if (!captured) {
            captured = true
            if (downAtMs == 0L) downAtMs = SystemClock.uptimeMillis()
            startValue = currentValue.coerceIn(valueRange.start, valueRange.endInclusive)
        }
        // ★ 累积位移必须跟值域一起钳制（2026-11-07 加固）：
        //   不钳的话，拖到两端后手指继续滑的那段会被白记进 [accumulated]，回拖时
        //   得先把这段「空转」走完才动 ⇒ 表现为「往回拖了但数值不变」（迟滞），
        //   拖得越远滞后越久，松手前会误以为控件卡死。钳到「从 startValue 出发
        //   可实际到达的位移区间」后，反向拖动立刻生效。
        //   `startValue` 已在上方按值域钳过 ⇒ minAcc ≤ 0 ≤ maxAcc，区间永远合法。
        val minAcc = valueRange.start - startValue
        val maxAcc = valueRange.endInclusive - startValue
        accumulated = (accumulated + deltaPx / trackWidthPx.coerceAtLeast(1))
            .coerceIn(minAcc, maxAcc)
        if (abs(accumulated) <= MeloXSliderValueEpsilon) return null
        changed = true
        return (startValue + accumulated)
            .coerceIn(valueRange.start, valueRange.endInclusive)
    }

    /**
     * 松手 / 取消。
     *
     * 调用方按结果分流：`dragged == true` ⇒ 提交拖动后的显示值；`tapValue != null`
     * ⇒ 这是一次快速点按，跳到该值；两者都不成立 ⇒ 只按了一下又没在点按窗口内，
     * 什么都不做（避免「只按一下」触发无意义的 seek / 音量回读）。
     */
    fun finish(): MeloXSliderGestureEnd {
        val dragged = changed
        val heldMs = if (downAtMs == 0L) Long.MAX_VALUE
        else SystemClock.uptimeMillis() - downAtMs
        val tap = if (!dragged && captured && heldMs <= MeloXSliderTapMaxDurationMs) {
            anchorRaw
        } else {
            null
        }
        captured = false
        accumulated = 0f
        changed = false
        anchorRaw = 0f
        downAtMs = 0L
        pressed = false
        return MeloXSliderGestureEnd(dragged, tap)
    }
}

@Composable
internal fun rememberMeloXRelativeSliderGesture(
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
): MeloXRelativeSliderGesture = remember(valueRange) { MeloXRelativeSliderGesture(valueRange) }

/** 按压弹簧（各控件共用同一套参数 ⇒ 条与两侧图标永远不会错拍）。 */
@Composable
internal fun rememberMeloXSliderPressScale(active: Boolean): State<Float> =
    animateFloatAsState(
        targetValue = if (active) MeloXSliderPressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "meloX-slider-press-scale",
    )

/**
 * 把**任意内容**（滑块本体、两侧喇叭图标、整行音量条）按 [pressScale] 绕自身中心缩放。
 *
 * ⚠ 只改绘制、不动布局与命中测试；静息 `s == 1f` 直接透传 ⇒ 静止态逐像素等价。
 * ⚠ 参数不能叫 `scale`：会和 [androidx.compose.ui.graphics.drawscope.scale] 撞名。
 */
internal fun Modifier.meloXSliderPressVisual(pressScale: State<Float>): Modifier =
    drawWithContent {
        val s = pressScale.value
        if (s == 1f) {
            drawContent()
        } else {
            scale(s, s, Offset(size.width / 2f, size.height / 2f)) {
                this@drawWithContent.drawContent()
            }
        }
    }

/**
 * 只做「按没按住」的旁听 + [MeloXRelativeSliderGesture.reset] 时机锚定，**不做缩放**。
 *
 * 音量条要整行一起缩放（含两侧图标），缩放层由调用方加在 Row 上；若这里也缩放
 * 就会与 Row 叠加成 1.21 倍。进度条用现成的 [meloXRelativeSliderPress] 即可。
 *
 * ⚠ Initial pass 只旁听、不消费任何事件（Slider 手势 / 点击 / 无障碍完全不受影响）。
 * ⚠ `finally` 复位是必需的：组合被取消 / `enabled` 变化导致 pointerInput 协程重启时，
 *   没有它 `pressed` 会永久卡在 true（本项目踩过的布尔卡死坑）。
 */
internal fun Modifier.meloXSliderPressDetector(
    gesture: MeloXRelativeSliderGesture,
    enabled: Boolean = true,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    try {
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                gesture.reset()
                gesture.pressed = true
                // 跟踪这根手指直到抬起 / 取消。
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                }
                gesture.pressed = false
            }
        }
    } finally {
        gesture.pressed = false
    }
}

/**
 * 统一的按压反馈层：旁听按下 + 自身内容按弹簧放大。进度条等「控件就是一个滑块」
 * 的场景直接用它；音量条这类「滑块 + 两侧图标」的整行联动见 [meloXSliderPressDetector]。
 */
@Composable
internal fun Modifier.meloXRelativeSliderPress(
    gesture: MeloXRelativeSliderGesture,
    enabled: Boolean = true,
): Modifier = this
    .meloXSliderPressDetector(gesture, enabled)
    .meloXSliderPressVisual(rememberMeloXSliderPressScale(gesture.pressed))
