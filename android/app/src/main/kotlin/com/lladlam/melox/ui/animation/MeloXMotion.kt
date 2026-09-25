package com.lladlam.melox.ui.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sqrt

internal object MeloXMotion {
    const val PageEnterMillis = 280
    const val PageExitMillis = 220
    const val ContentEnterMillis = 320
    const val ContentExitMillis = 240
    const val IconEnterMillis = 180
    const val IconExitMillis = 120
    const val PanelEnterStiffness = 420f
    const val PanelExitStiffness = 500f
    const val DropdownStiffness = 320f
}

/**
 * iOS 口径的弹簧数值。
 *
 * Apple `spring(bounce:)` 里 `bounce` 与阻尼比的关系是 **`ζ = 1 - bounce`**：
 * bounce=0 → 临界、不过冲；0.2~0.4 是 HIG 的 Q 弹甜区；>0.4 会卡通化。
 * ζ<1 的峰值过冲 `p = exp(-πζ/√(1-ζ²))`：ζ=0.70 → 4.6%（明显 Q 弹），
 * ζ=0.90（改前）→ 0.15%（等于看不见）。
 *
 * 这里只放「需要 Q 弹」的那几处；普通过渡继续用上面的 tween / 不弹的 spring。
 * ⚠ 数值唯一真相源，调用点不要再写裸常量。
 */
internal object MeloXSprings {
    /**
     * 底栏**收缩**方向要保住的回弹幅度，单位 dp，不是 bounce。
     *
     * 360dp 屏上行程约 224dp、bounce 0.30 时峰值约 10.30dp，这是验收过的手感。
     * 更宽的屏如果仍用 0.30，同样的比例会把回弹放大到十几 dp。所以常量停在 dp，
     * bounce 由 [bottomBarBounce] 按当次行程反推。
     */
    const val BottomBarCollapseOvershootDp = 10.30f

    /**
     * 展开方向右缘最多能再往搜索键靠近多少。固有缝 7dp，扣掉玻璃边缘约 3dp 的视觉余量。
     * 总外扩 = 右预算 / (1 − [NavExpandLeftShare])，左缘按同一比例让出。
     */
    const val BottomBarExpandRightBudgetDp = 4f

    /**
     * ⚠ 刚度决定「过冲窗口」的长度，不是装饰参数。
     *
     * k=500 (response≈0.28s) → 268ms 就收完，过冲窗口只有 194ms，一闪而过读成「抖动」；
     * 降到 k=250 (response≈0.40s，对齐 Apple drawer / panel 的 0.3~0.4s) 后
     * 过冲窗口 194→276ms，才读得出「回弹」而不是「顿一下」。
     */
    const val BottomBarStiffness = 250f

    /** 迷你播放器出现时的上浮。同样是甜区。 */
    const val MiniPlayerRevealBounce = 0.34f
    const val MiniPlayerRevealStiffness = 300f

    /** bounce → Compose 阻尼比。 */
    fun dampingRatio(bounce: Float): Float = (1f - bounce).coerceIn(0.05f, 1f)

    /**
     * 把「这次行程里允许的过冲 dp」换成 bounce。
     *
     * `p = overshoot / travel = exp(−πζ / √(1−ζ²))`，解出来是
     * `k = ln(1/p) / π`，`ζ = √(k² / (1+k²))`，`bounce = 1 − ζ`。
     * 360dp、行程 224dp、收缩 10.30dp 时 bounce ≈ 0.30；展开总外扩 6dp 时 bounce ≈ 0.245。
     * 屏变更宽，行程变长，同样的 dp 对应更小的 bounce，过冲不会跟着放大。
     */
    fun bottomBarBounce(overshootDp: Float, travelDp: Float): Float {
        if (travelDp <= 1f || overshootDp <= 0f) return 0f
        val fraction = (overshootDp / travelDp).coerceIn(0.004f, 0.12f)
        val k = ln(1.0 / fraction) / PI
        val zeta = sqrt((k * k) / (1.0 + k * k))
        return (1.0 - zeta).toFloat().coerceIn(0.05f, 0.40f)
    }
}

/**
 * 底栏某一侧的弹簧。过冲 dp 按当次行程换成 bounce，所以宽屏不会把 360dp 上验收过的
 * 幅度同比放大。`animateFloatAsState` 只在 target 变化时采纳 spec，调用方要在
 * `minimized` 跳变的同一帧把对应方向传进去。
 */
internal fun bottomBarSpring(overshootDp: Float, travelDp: Float): SpringSpec<Float> = spring(
    dampingRatio = MeloXSprings.dampingRatio(MeloXSprings.bottomBarBounce(overshootDp, travelDp)),
    stiffness = MeloXSprings.BottomBarStiffness,
    visibilityThreshold = 0.001f,
)

/** 几何限幅要盖住这次行程上真正会出现的过冲，再留一点余量，避免把峰值削平。 */
internal fun bottomBarOvershootLimit(overshootDp: Float, travelDp: Float): Float {
    if (travelDp <= 1f) return 0.02f
    return (overshootDp / travelDp * 1.15f).coerceIn(0.02f, 0.12f)
}

/**
 * 展开外扩量分给**左缘**的比例（右缘拿 `1 − 这个值`）。
 *
 * 展开时胶囊变宽，多出来的那部分往哪边落是自由选择 —— 但两侧空间不对等：
 * · 右侧固有缝 7dp，扣掉玻璃边缘 1~2dp 的视觉外扩后只剩 **4dp**；
 * · 左侧是 12dp 屏幕边距，没有邻居。
 * 所以落点必须**偏右**（朝搜索键那一侧弹），否则右边那点余量根本用不上。
 *
 * 现取 **1/3（左 1 : 右 2）**。右缘预算 4dp，总外扩 = 4 / (2/3) = 6dp；
 * 360dp 行程上这就是原来的 bounce 0.245。宽屏行程更长，bounce 变小，dp 不变。
 *
 * ⚠ 只写**比例**、不写绝对 dp：胶囊宽度本身就是弹簧输出（`lerpDpBouncy` 不夹取），
 *   把绝对 dp 写死会在行程（`expandedNavWidth − compactSize`）变化后静默失配。
 *   想按「右缘最多 4dp」反推总外扩，写在注释里、由这个比例去乘即可。
 * ⚠ nav 的**中心会因此轻微右漂**：峰值 148 → 149dp（1dp）。这是「右边弹得更多」的
 *   必然代价，不是 bug —— 想消除就只能回到 1/2（左右等分），而那正是被否掉的「对称」。
 * ⚠ 收缩方向（f>0）宽度不超稳态，`coerceAtLeast(0.dp)` 让越界量归零 → 退回纯边距，
 *   也就是**收缩天然是左锚定**（左缘恒 12dp，右缘从 284 收到 60dp）。这个比例只作用于展开。
 */
internal const val NavExpandLeftShare = 1f / 3f

/**
 * 弹簧输出 → 几何比例（0 = 展开稳态，1 = 收缩稳态）。
 *
 * 只做**带符号限幅**：`[-limit, 1+limit]`，两侧对称，形状就是弹簧本身。
 *
 * ⚠ 不要再引入任何「区间重映射」（把弹簧输出按 [start,end] 线性映射）：
 *   任何单边窗口都等于**在某一个方向把几何运动在弹簧最高速处截断**。
 *   旧实现 `((v-0.20)/0.80).coerceIn(0f, …)` 的实测后果——
 *   · 收缩方向：只是「延后 49ms 启动」，末尾过冲完整 → 用户评价「不错」；
 *   · 展开方向：几何在 134ms 就被钉死在终点，此刻速度还有 1.45 dp/ms
 *     （峰值 2.10 的 69%），然后**空转 246ms** → 用户评价「直来直去」。
 *   同一个窗口的两面：一面是延迟启动，另一面就是撞墙。取消窗口后两个方向同形。
 *
 * ⚠ 若将来确实需要窗口（例如相位错峰），必须校验 **value=end 时结果恰好 =1**，
 *   否则弹簧剩余行程会让几何继续外推、稳态停在错值上。
 */
internal fun sprungFrac(
    value: Float,
    limit: Float,
): Float = value.coerceIn(-limit, 1f + limit)

internal fun meloXPageEnter(fromRight: Boolean = true): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(MeloXMotion.PageEnterMillis, easing = FastOutSlowInEasing),
        initialOffsetX = { if (fromRight) it else -it / 4 },
    ) + fadeIn(tween(MeloXMotion.PageEnterMillis, easing = FastOutSlowInEasing))

internal fun meloXPageExit(toRight: Boolean = true): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(MeloXMotion.PageExitMillis, easing = FastOutSlowInEasing),
        targetOffsetX = { if (toRight) it / 4 else -it / 4 },
    ) + fadeOut(tween(MeloXMotion.PageExitMillis, easing = FastOutSlowInEasing))

internal fun meloXContentEnter() = fadeIn(
    tween(MeloXMotion.ContentEnterMillis, easing = FastOutSlowInEasing),
)

internal fun meloXContentExit() = fadeOut(
    tween(MeloXMotion.ContentExitMillis, easing = FastOutSlowInEasing),
)

internal fun meloXPanelEnter(initialScale: Float = 0.96f) =
    fadeIn(spring(dampingRatio = 0.86f, stiffness = MeloXMotion.PanelEnterStiffness)) +
        scaleIn(
            initialScale = initialScale,
            animationSpec = spring(dampingRatio = 0.86f, stiffness = MeloXMotion.PanelEnterStiffness),
        )

internal fun meloXPanelExit(targetScale: Float = 0.98f) =
    fadeOut(spring(dampingRatio = 0.90f, stiffness = MeloXMotion.PanelExitStiffness)) +
        scaleOut(
            targetScale = targetScale,
            animationSpec = spring(dampingRatio = 0.90f, stiffness = MeloXMotion.PanelExitStiffness),
        )
