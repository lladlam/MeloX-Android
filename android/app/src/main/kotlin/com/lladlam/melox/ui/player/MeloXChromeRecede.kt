package com.lladlam.melox.ui.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.dp
import com.lladlam.melox.BuildConfig
import com.lladlam.melox.ui.animation.MeloXPlayerRecede

/**
 * 背景页「下沉 + 缩小」的变换公式 —— **全仓唯一实现**。
 *
 * 两个消费者：
 *  ① `MeloXApp` 里 recede 外层 `graphicsLayer`（背景页本体）；
 *  ② [meloXChromeRecedeLayer]（底部 chrome，2026-10-26 用户口径「底栏跟着背景页一起
 *     下沉缩小、相对位置不变」）。
 *
 * ⚠⚠ 这两处**必须逐字同参**：一旦其中一处单独改数值（或单独加个 smoothStep 窗口），
 *   底栏就会相对背景页漂移，而这正是本文件要修掉的那个症状。所以公式只能从这里出。
 *
 * ⛔ 需要「下沉—回弹」的是**迷你播放栏**，不是这里：那条通道在共享元素 bounds 几何里
 *   （`meloXPlayerSharedBoundsTransform` 的 `dy`）。本层（导航栏）**只**做
 *   「与背景页逐像素同参」，⛔ 别在这层加任何位移（2026-10-27 加过一次，已撤）。
 *   下面的 kdoc 抄自 `MeloXApp` 里那段历史注释，改数值前先读它。
 */

/**
 * 横向缩放比例（`f=0` → 1，`f=1` → [MeloXPlayerRecede.PageScaleX]）。
 *
 * @param f 下沉进度（= `MeloXApp.recedeFraction()`，可能与 1 的差为负 —— 关闭过冲）。
 */
internal fun meloXRecedeScaleX(f: Float): Float = lerp(1f, MeloXPlayerRecede.PageScaleX, f)

/**
 * 纵向缩放比例。
 *
 * 默认**由横向内缩反推**（`sy = 1 − (1−sx)·宽/高` ⇒ 可见内缩在 x/y 上相等，黑边才等宽）；
 * [MeloXPlayerRecede.PageScaleYOverride] 非 0 时改用它（加大纵向内缩 ⇒ 底边额外上移，
 * 解掉用户 2026-10-20 报的「背景下沉的底部几乎不上移」，且 `PageSinkDp` 一行未动）。
 *
 * ⚠ 推导要用**缩放层自己的尺寸**：`f=0` 时 `lerp` 起点恒为 1，与尺寸无关；但 `f∈(0,1)`
 *   时 `sy` 依赖 `width/height`。页面层与 chrome 层都是**满屏**尺寸（这是它们共用同一
 *   变换的前提，见 [meloXChromeRecedeLayer] 的 transformOrigin 说明），所以同一个公式
 *   在两边给出同一个值。
 */
internal fun meloXRecedeScaleY(f: Float, width: Float, height: Float): Float {
    if (width <= 0f || height <= 0f) return 1f
    val syFull = if (MeloXPlayerRecede.PageScaleYOverride > 0f) {
        MeloXPlayerRecede.PageScaleYOverride
    } else {
        1f - (1f - MeloXPlayerRecede.PageScaleX) * width / height
    }
    return lerp(1f, syFull, f)
}

/**
 * 背景页 / 底部 chrome 共用的**向下沉降位移**（px，正值 = 向下）。
 *
 * ## 为什么不直接 `PageSinkDp.dp.toPx() * f`
 * `PageSinkDp`=30dp 是在竖屏 1440×3168 上定档的固定位移；横屏时屏幕**高度**只有一半，
 * 纵向可缩余量 `(H/2)(1−sy)` 从 206px 掉到 93.6px，而位移仍是 120px
 * ⇒ 卡片底边被推出屏幕 26px，底边与圆角整段硬裁（用户 2026-10-27 报的
 * 「下半页面在打开动画时表现为截断」）。
 *
 * ⇒ 改成**按可缩余量的比例**：`(H/2)(1−sy) × PageSinkRatio × f`。
 *   竖屏结果 = 206 × 0.583 = 120.1px（与旧值逐像素等同 ⇒ 竖屏观感零变化）；
 *   横屏 = 93.6 × 0.583 = 54.6px（下黑边 39px，与竖屏同一百分比）。
 *
 * ⚠ 页面层与 chrome 层**必须**都走这个函数：一处用旧固定值、另一处用比例值，
 *   底栏就会相对背景页纵向漂移（正是 `meloXChromeRecedeLayer` 要保的那条不变式）。
 *
 * @param height 本层节点高度（= 屏幕高；两层都是满屏节点）
 * @param scaleY 本次 `meloXRecedeScaleY` 的结果 ⇒ 余量与缩放天然同一帧
 */
internal fun meloXRecedeSinkPx(f: Float, height: Float, scaleY: Float): Float {
    if (f == 0f) return 0f
    // 尺寸未知（首帧 measure 前）：给 0，让这一帧维持恒等 —— 下一帧 height 一定有效。
    if (height <= 0f) return 0f
    val available = (height / 2f) * (1f - scaleY)
    return available * MeloXPlayerRecede.PageSinkRatio * f
}

/**
 * 底部 chrome 的「跟着背景页下沉缩小」层。
 *
 * ## 为什么必须是**满屏**的一层
 * `graphicsLayer` 默认绕**自己节点的中心**缩放。背景页那层是满屏的，中心 = 屏幕中心；
 * 要让底栏与背景页保持**逐像素**相同的相对位置（用户原话「跟背景页相对位置不变」），
 * 只能让这一层也绕**同一个点**（屏幕中心）缩放 —— 即让节点尺寸也等于屏幕尺寸，
 * 再用内部 `align` 把底栏摆到底部。**不要**把它改成 `matchParentSize` / 只包底栏本体：
 * 那样缩放中心变成底栏自己的中心（离屏幕中心约 1450px），转场期间底栏会绕自己转，
 * 与背景页错开一大截（f=1 时纵向差可达 60dp 以上）。
 *
 * 另外两边都在**同一个 lambda 里读同一个 `fraction()`**（同一条 master 曲线、
 * 同一帧求值）⇒ 缩/沉与背景页天然同拍，不存在两条 spec 错拍的可能。
 *
 * ## 为什么不会缩坏 sharedBounds 的迷你栏
 * 迷你栏（含内联那一档）确实落在这一层里，它同时是播放器外壳 `sharedBounds` 的源。
 * 但 `MeloXBottomChrome` 里那个 `AnimatedVisibility(visible = { !value })` 在**转场期间
 * 整个不组合**（`currentState` 在收起方向仍是"打开侧" ⇒ `currentlyVisible=false`），
 * 于是 morph 的两端几何都取自 `f≈0` 的录制值 —— 那一帧本层变换恰为**恒等**
 * ⇒ morph 几何与不缩时逐像素相同。
 * ⚠ 若将来把迷你栏改成"转场期间也保持组合"，这条前提就失效了 —— 那时必须把迷你栏
 *   移出本层（它是唯一需要排除的子树）。
 *
 * ## 为什么不需要 `shape` / `clip`
 * 背景页那层带系统圆角 + 裁剪，是因为它自己要收成卡片；底栏整块都在卡片**内部**
 * （f=1 时 nav 底边 2965px vs 卡片底边 3056px，边距 91px），裁不裁都看不见，
 * 少一层全屏路径裁剪反而更省。
 */
/**
 * ⛔⛔ 曾是「底栏专属前段俯冲」（2026-10-27 11:26 一版，`26dp × easeOutQuint`），
 * 已**整条删除**。
 *
 * 删除理由（用户原话）：「我说的是**迷你栏**下沉，底栏一直保持跟背景页相对位置就行了」。
 * ⇒ 导航栏（底栏）**不许有任何自己的位移**，它只做「跟背景页逐像素同参」这一件事；
 *   需要下沉的是**迷你播放栏**，那条通道在共享元素 bounds 几何里
 *   （`meloXPlayerSharedBoundsTransform` 的 `dy`，见 `MeloXPlayerTransitionSpec`）。
 * ⚠ 别再往这一层加任何位移 —— 那等于把导航栏和背景页拆开。
 */

internal fun Modifier.meloXChromeRecedeLayer(fraction: () -> Float): Modifier =
    graphicsLayer {
        // 只喂 draw 期（本 lambda 每帧执行），不进组合 ⇒ 不触发重组、不重排。
        val f = fraction()
        if (f == 0f) {
            // 稳态：恒等变换。显式写回 1/0 是必需的 —— 动画落定后本 lambda 还会再跑，
            // 不写回就会把最后一帧的非 1 缩放留在 RenderNode 上。
            scaleX = 1f
            scaleY = 1f
            translationY = 0f
            return@graphicsLayer
        }
        scaleX = meloXRecedeScaleX(f)
        scaleY = meloXRecedeScaleY(f, size.width, size.height)
        // ⚠ 与背景页同一条沉降公式（`meloXRecedeSinkPx`）。**别在这层单独加系数或位移。**
        translationY = meloXRecedeSinkPx(f, size.height, scaleY)
        // 🔬 2026-10-27 取证：本层拿到的**节点尺寸**是「缩放中心是否等于屏幕中心」的
        //   唯一判据 —— `graphicsLayer` 默认绕节点中心缩放，只有节点尺寸 = 屏幕尺寸时
        //   才与背景页那层同中心；一旦变成 wrap/matchParent 的其它值，底栏就会绕自己
        //   中心转、与背景页错开。（历史取证结论；诊断器已于 2026-10-30 移除。）
    }

