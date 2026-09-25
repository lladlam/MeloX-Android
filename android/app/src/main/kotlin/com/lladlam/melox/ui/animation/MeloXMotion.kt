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
     * 底栏**收缩**方向（展开 → 收缩）。bounce=0.30 → ζ=0.70，峰值过冲 4.60%
     * （行程 224dp → 回弹 10.30dp，峰值时 nav 宽 37.70dp；仍 > 收缩态 25dp 图标）。
     *
     * 收缩发生在「周围刚刚空出来」之后（nav 自己在变窄、搜索键也在缩），所以这里
     * 可以放开弹 —— 10.30dp 是用户反复验收过的幅度。
     *
     * 定档轨迹：0.30（首版）→ 0.34 → **0.37**（取消单边窗口后为保住原幅度而补偿 1.25×）
     * → 0.34（用户「非线性不错，但回弹幅度太大」）→ **0.30**（用户二次回访「还是大了些」）。
     *
     * ⚠ **0.30 是本条的甜区下限**。若仍嫌大，不要再往 0.28 压 —— ζ→1 的过程会先失去
     *   「弹」、再变成「迟钝」，而不是「更干脆」。那种情况应当**取消过冲**（限幅归 0）
     *   或改用带轻微过冲的 back-ease，而不是继续调 bounce。
     */
    const val BottomBarCollapseBounce = 0.30f

    /**
     * 底栏**展开**方向（收缩 → 展开）。bounce=0.245 → ζ=0.755，峰值过冲 2.68%
     * （行程 224dp → 外扩 6.02dp，左 2.01 / 右 4.01dp）。
     *
     * ⚠ 这不是「把 0.30 调小一点」，而是**由展开方向两侧剩下的空间反推出来的**，
     *   而且是被两层约束夹出来的：
     *   · 右侧：固有缝 7dp（官方截图实测 10px = 7.12dp），玻璃边缘的折射/发光在视觉上
     *     比布局边界大 1~2dp ⇒ 右缘可用外扩 **4dp**（硬顶，再大就读成「撞上搜索键」）；
     *   · 左侧：12dp 屏幕边距、没有邻居 ⇒ 可用 8dp，但不是硬顶。
     *   落点 = **右 4dp / 左 2dp（2:1 偏右，见 [NavExpandLeftShare]）** ⇒ 总外扩 6dp
     *   ⇒ `p = exp(−πζ/√(1−ζ²)) = 6 / 224 = 2.68%` → ζ = 0.7552 → bounce = 0.2448。
     *   物理上本来就该这样：展开是「朝有邻居的那一侧长大」，右侧只有 4dp 地方，
     *   所以重心必然偏右、整体也收着弹；收缩是「四周空出来了」⇒ 放开弹（0.30）。
     *   **两个方向各是一条完整的弹簧，几何上不再有任何事后压缩。**
     *
     * ⚠ 为什么必须是两个方向各自的 spec，而不是给展开方向事后「压缩」：
     *   共用一个 spec 时，保护邻居只能靠 `rawDp × gain` 把越界量缩进预算 —— 那条曲线
     *   **不再是弹簧**（阻尼包络仍是 0.30 的，只是整体缩了 0.78 倍），两个方向手感不对等。
     *   分开之后几何就是 `lerpDpBouncy(稳态宽, 收缩宽, f)` 一句，没有任何后处理。
     *
     * ⚠ 「左右怎么分」只由 [NavExpandLeftShare] 决定（纯几何，与弹簧解耦），但两者是
     *   一起定的 —— 改分配比例后必须回头重算这个 bounce。
     * ⚠ 行程一变（改 `searchGap` / 边距 / 屏宽）这个对应关系也会失效，同样要重算：
     *   `k = ln(1/p)/π` → `ζ = √(k²/(1+k²))` → `bounce = 1 − ζ`，其中
     *   `p = 总外扩dp / travel`，总外扩 = 右预算 ÷ 右缘占比。
     *   离线脚本 `tmp/sim_directional_spring.py` 可直接出这张表。
     */
    const val BottomBarExpandBounce = 0.245f

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
}

/** 过冲限幅（行程比例）。收缩方向 bounce 0.30 → 实际峰值 0.046（本例不会触及此上限）；
 *  224dp 行程 → 最窄 48−224×0.046≈37.7dp，仍大于 25dp 图标。
 *  展开方向的越界由 [MeloXSprings.BottomBarExpandBounce] 自己守住，同样不依赖这个上限。 */
internal const val SpringOvershootLimit = 0.09f

/**
 * 底栏弹簧的**两个方向 spec** —— 直接喂 `animateFloatAsState`。
 *
 * 展开 / 收缩是两次独立的动画事件（`minimized` 的两个跳变），所以可以、也应该各给一条
 * 弹簧：展开是朝有邻居的方向长大（空间受限 → 高阻尼 0.245、且落点偏右），收缩是四周空出来
 * （放开弹 0.30）。数值怎么来的见 [MeloXSprings.BottomBarExpandBounce]。
 *
 * ⚠ 调用方必须**按 target 选**：`if (minimized) CollapseSpec else ExpandSpec`。
 *   `animateFloatAsState` 只在 targetValue 变化时重启动画、并采纳**那一刻**的 spec，
 *   所以 target 与 spec 必须在同一帧一起算出来 —— 不要从动画帧反推方向。
 */
internal val BottomBarExpandSpec: SpringSpec<Float> = spring(
    dampingRatio = MeloXSprings.dampingRatio(MeloXSprings.BottomBarExpandBounce),
    stiffness = MeloXSprings.BottomBarStiffness,
    visibilityThreshold = 0.001f,
)

/** 收缩方向的另一半，见 [BottomBarExpandSpec] 与 [MeloXSprings.BottomBarCollapseBounce]。 */
internal val BottomBarCollapseSpec: SpringSpec<Float> = spring(
    dampingRatio = MeloXSprings.dampingRatio(MeloXSprings.BottomBarCollapseBounce),
    stiffness = MeloXSprings.BottomBarStiffness,
    visibilityThreshold = 0.001f,
)

/**
 * 展开外扩量分给**左缘**的比例（右缘拿 `1 − 这个值`）。
 *
 * 展开时胶囊变宽，多出来的那部分往哪边落是自由选择 —— 但两侧空间不对等：
 * · 右侧固有缝 7dp，扣掉玻璃边缘 1~2dp 的视觉外扩后只剩 **4dp**；
 * · 左侧是 12dp 屏幕边距，没有邻居。
 * 所以落点必须**偏右**（朝搜索键那一侧弹），否则右边那点余量根本用不上。
 *
 * 现取 **1/3（左 1 : 右 2）**：峰值时右缘 +4.01dp、左缘 −2.01dp、右缝 7 → 3.00dp。
 * 配合 [MeloXSprings.BottomBarExpandBounce]（0.245）反推，总外扩 6.02dp。
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
    limit: Float = SpringOvershootLimit,
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
