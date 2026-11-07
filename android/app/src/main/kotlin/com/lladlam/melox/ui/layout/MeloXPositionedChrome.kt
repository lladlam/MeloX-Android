package com.lladlam.melox.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lladlam.melox.BuildConfig
import com.lladlam.melox.ui.settings.MeloXSplitChromePosition
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import kotlin.math.roundToInt

internal data class MeloXChromeOffsets(val navigation: IntOffset, val miniPlayer: IntOffset)

/**
 * 双栏底栏**下沿**留白（自 safeDrawing 底往上量）。
 *
 * 2026-10-18 用户反馈「底栏迷你栏选左下/右下时，还可以再靠近底部一点」：
 * 原来下沿按通用 `gap`(8dp) 算，再叠上导航栏 inset 与各自内边距 ⇒ 实测离屏幕底
 * 约 35dp，明显比 iOS 的悬浮底栏高。这里单独给下沿一个更小的值，
 * 左右/顶部仍用 `gap`（那三边是有邻居的，不能压）。
 *
 * 2026-10-20 用户再次要求「还能更往下一点」：4dp → 2dp。
 * 保留 2dp 而不是 0：① 胶囊外圈还有阴影/blur 需要 2dp 才不被裁；
 * ② 完全贴底会吃到系统手势条的触摸区，容易误触。
 * ⚠ 这一层才是**真正**把控件从屏幕底推开的量；`MeloXBottomChrome` 里 nav Column 的
 *   `bottom` padding(4dp) 在控件容器内部，只影响胶囊在容器内的位置，不影响控件与屏幕底的距离。
 */
private val SplitChromeBottomMargin = 2.dp

/** Divide BEFORE applying system insets: both controls use the page's exact divider. */
internal fun meloXChromeArea(
    viewport: IntSize,
    position: MeloXSplitChromePosition,
    otherPosition: MeloXSplitChromePosition?,
    miniPlayer: Boolean,
    margin: Int,
    railWidth: Int,
    safeLeft: Int = margin,
    safeRight: Int = margin,
    safeTop: Int = margin,
    bottomMargin: Int = margin,
): IntRect {
    val divider = viewport.width / 2
    var left = if (position.right) divider + margin else safeLeft.coerceAtLeast(margin)
    var right = if (position.right) viewport.width - safeRight.coerceAtLeast(margin) else divider - margin
    // A rail owns the OUTER edge. A second rail, or a horizontal dock, sits inward.
    if (otherPosition?.right == position.right && otherPosition.isSideRail && (!position.isSideRail || miniPlayer)) {
        val spacing = if (position == otherPosition && position.isSideRail) (margin / 8).coerceAtLeast(1) else margin
        if (position.right) right -= railWidth + spacing else left += railWidth + spacing
    }
    right = right.coerceAtLeast(left)
    return IntRect(left, safeTop, right, (viewport.height - bottomMargin).coerceAtLeast(safeTop))
}

internal fun meloXChromeOffsets(
    viewport: IntSize,
    navigation: IntSize,
    miniPlayer: IntSize,
    navigationPosition: MeloXSplitChromePosition,
    miniPlayerPosition: MeloXSplitChromePosition,
    gap: Int,
    railWidth: Int = 72,
    safeLeft: Int = gap,
    safeRight: Int = gap,
    safeTop: Int = gap,
    coupledProgress: Float = 0f,
    bottomMargin: Int = gap,
): MeloXChromeOffsets {
    fun anchor(size: IntSize, position: MeloXSplitChromePosition, mini: Boolean): IntOffset {
        val other = if (mini) navigationPosition else miniPlayerPosition.takeIf { miniPlayer.height > 0 }
        val area = meloXChromeArea(viewport, position, other, mini, gap, railWidth, safeLeft, safeRight, safeTop, bottomMargin)
        return IntOffset(
            if (position.right) area.right - size.width else area.left,
            when (position.vertical) {
                -1 -> area.top
                0 -> area.top + (area.height - size.height) / 2
                else -> area.bottom - size.height
            }.coerceAtLeast(area.top),
        )
    }
    val nav = anchor(navigation, navigationPosition, false)
    var mini = anchor(miniPlayer, miniPlayerPosition, true)
    val overlaps = miniPlayer.height > 0 && mini.x < nav.x + navigation.width &&
        nav.x < mini.x + miniPlayer.width && mini.y < nav.y + navigation.height + gap &&
        nav.y < mini.y + miniPlayer.height + gap
    if (overlaps) {
        val above = nav.y - miniPlayer.height - gap
        val below = nav.y + navigation.height + gap
        mini = IntOffset(mini.x, when {
            miniPlayerPosition.vertical == -1 -> below
            above >= safeTop -> above
            else -> below
        }.coerceIn(safeTop, (viewport.height - bottomMargin - miniPlayer.height).coerceAtLeast(safeTop)))
    }
    if (navigationPosition == miniPlayerPosition && navigationPosition.isSideRail && miniPlayer.height > 0) {
        val progress = coupledProgress.coerceIn(0f, 1f)
        // The compact rail reserves a middle slot between the selected tab and Search.
        mini = IntOffset(
            (mini.x + (nav.x - mini.x) * progress).roundToInt(),
            (mini.y + (nav.y + (navigation.height - miniPlayer.height) / 2 - mini.y) * progress).roundToInt(),
        )
    }
    // ★ 中心线对齐（2026-10-18 用户裁定）：
    //   左右两条底栏高度不同 —— 导航胶囊 62dp、迷你播放栏 48dp。若按**底边**齐平，
    //   较高的胶囊顶边与内容行会比迷你栏高出一大截，真机上读起来就是「左栏偏上」。
    //   ⇒ 两者都停在底部时改为**垂直中心重合**：以胶囊中心为基准摆放迷你栏，
    //     胶囊较高 ⇒ 它向下多探出的那点由下沿留白吸收（`SplitChromeBottomMargin`）。
    //   ⚠ 只处理「都在底部」这一档；侧栏(vertical=0)/顶部(-1)各有自己的对齐语义，不掺进来。
    if (miniPlayer.height > 0 &&
        navigationPosition.vertical == 1 && miniPlayerPosition.vertical == 1
    ) {
        val navCenter = nav.y + navigation.height / 2
        mini = IntOffset(
            mini.x,
            (navCenter - miniPlayer.height / 2).coerceAtLeast(safeTop),
        )
    }
    return MeloXChromeOffsets(nav, mini)
}

/** Measured and clipped control bounds are contained in one physical half of the window. */
@Composable
internal fun MeloXPositionedChrome(
    split: Boolean,
    hasMedia: Boolean,
    coupledProgress: Float = 0f,
    navigationPosition: MeloXSplitChromePosition,
    miniPlayerPosition: MeloXSplitChromePosition,
    modifier: Modifier = Modifier,
    miniPlayer: @Composable () -> Unit,
    navigation: @Composable () -> Unit,
) {
    val safeInsets = WindowInsets.safeDrawing
    val direction = LocalLayoutDirection.current
    Layout(
        modifier = modifier,
        content = {
            Box(Modifier.clipToBounds()) { navigation() }
            Box(Modifier.clipToBounds()) { if (split && hasMedia) miniPlayer() }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        // ★★ 2026-10-27 兜底：**宽度判据最终以本次 measure 的约束为准**。
        //   传入的 `split` 现在已经是布局真值（`BoxWithConstraints` 的 constraints），
        //   这里再按**本节点的约束**或一次，是给「将来有人又把配置口径传回来」留的保险：
        //   只要窗口已经是横的（`width > height`），就绝不允许走「整屏宽」那条分支 ——
        //   那条分支正是「底栏长变短」的唯一成因（`minWidth = maxWidth = width`）。
        //   ⚠ 用 OR 而不是覆盖：非横屏时仍完全听传入值（`splitScreenEnabled` 关闭等场景）。
        val splitEffective = split || (MeloXSettingsRuntime.splitScreenEnabled && width > height)
        val viewport = IntSize(width, height)
        val gap = 8.dp.roundToPx()
        val railWidth = 72.dp.roundToPx()
        // 双栏底栏下沿单独用更小的留白（见 SplitChromeBottomMargin）。
        val bottomMargin = SplitChromeBottomMargin.roundToPx()
        val safeLeft = safeInsets.getLeft(this, direction).coerceAtLeast(gap)
        val safeRight = safeInsets.getRight(this, direction).coerceAtLeast(gap)
        val safeTop = safeInsets.getTop(this).coerceAtLeast(gap)
        fun controlConstraints(position: MeloXSplitChromePosition, other: MeloXSplitChromePosition?, mini: Boolean): Constraints {
            if (!splitEffective) return Constraints(minWidth = width, maxWidth = width, maxHeight = height)
            val area = meloXChromeArea(viewport, position, other, mini, gap, railWidth, safeLeft, safeRight, safeTop, bottomMargin)
            val childWidth = if (position.isSideRail) railWidth.coerceAtMost(area.width) else area.width
            return Constraints(minWidth = childWidth, maxWidth = childWidth, maxHeight = area.height)
        }
        val nav = measurables[0].measure(controlConstraints(navigationPosition, miniPlayerPosition.takeIf { hasMedia }, false))
        val mini = measurables[1].measure(if (splitEffective && hasMedia) controlConstraints(miniPlayerPosition, navigationPosition, true) else Constraints.fixed(0, 0))
        val offsets = if (splitEffective) meloXChromeOffsets(
            viewport, IntSize(nav.width, nav.height), IntSize(mini.width, mini.height),
            navigationPosition, miniPlayerPosition, gap, railWidth, safeLeft, safeRight, safeTop, coupledProgress,
            bottomMargin,
        ) else MeloXChromeOffsets(IntOffset.Zero, IntOffset.Zero)
        // 🔬 2026-10-27 转屏取证：`split` / 窗口宽 / 导航约束宽三者**同帧**的真值。
        //   （历史上仅诊断构建启用；诊断器已于 2026-10-30 移除。）
        //   ⚠ 必须写在 `layout { }` **之前**：measure lambda 的最后一条语句必须是
        //     `layout(...)`（它返回 MeasureResult），写到后面返回值会变成 Unit。
        layout(width, if (splitEffective) height else nav.height) {
            nav.place(offsets.navigation)
            mini.place(offsets.miniPlayer)
        }
    }
}
