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
import com.lladlam.melox.ui.settings.MeloXSplitChromePosition
import kotlin.math.roundToInt

internal data class MeloXChromeOffsets(val navigation: IntOffset, val miniPlayer: IntOffset)

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
    return IntRect(left, safeTop, right, (viewport.height - margin).coerceAtLeast(safeTop))
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
): MeloXChromeOffsets {
    fun anchor(size: IntSize, position: MeloXSplitChromePosition, mini: Boolean): IntOffset {
        val other = if (mini) navigationPosition else miniPlayerPosition.takeIf { miniPlayer.height > 0 }
        val area = meloXChromeArea(viewport, position, other, mini, gap, railWidth, safeLeft, safeRight, safeTop)
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
        }.coerceIn(safeTop, (viewport.height - gap - miniPlayer.height).coerceAtLeast(safeTop)))
    }
    if (navigationPosition == miniPlayerPosition && navigationPosition.isSideRail && miniPlayer.height > 0) {
        val progress = coupledProgress.coerceIn(0f, 1f)
        // The compact rail reserves a middle slot between the selected tab and Search.
        mini = IntOffset(
            (mini.x + (nav.x - mini.x) * progress).roundToInt(),
            (mini.y + (nav.y + (navigation.height - miniPlayer.height) / 2 - mini.y) * progress).roundToInt(),
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
        val viewport = IntSize(width, height)
        val gap = 8.dp.roundToPx()
        val railWidth = 72.dp.roundToPx()
        val safeLeft = safeInsets.getLeft(this, direction).coerceAtLeast(gap)
        val safeRight = safeInsets.getRight(this, direction).coerceAtLeast(gap)
        val safeTop = safeInsets.getTop(this).coerceAtLeast(gap)
        fun controlConstraints(position: MeloXSplitChromePosition, other: MeloXSplitChromePosition?, mini: Boolean): Constraints {
            if (!split) return Constraints(minWidth = width, maxWidth = width, maxHeight = height)
            val area = meloXChromeArea(viewport, position, other, mini, gap, railWidth, safeLeft, safeRight, safeTop)
            val childWidth = if (position.isSideRail) railWidth.coerceAtMost(area.width) else area.width
            return Constraints(minWidth = childWidth, maxWidth = childWidth, maxHeight = area.height)
        }
        val nav = measurables[0].measure(controlConstraints(navigationPosition, miniPlayerPosition.takeIf { hasMedia }, false))
        val mini = measurables[1].measure(if (split && hasMedia) controlConstraints(miniPlayerPosition, navigationPosition, true) else Constraints.fixed(0, 0))
        val offsets = if (split) meloXChromeOffsets(
            viewport, IntSize(nav.width, nav.height), IntSize(mini.width, mini.height),
            navigationPosition, miniPlayerPosition, gap, railWidth, safeLeft, safeRight, safeTop, coupledProgress,
        ) else MeloXChromeOffsets(IntOffset.Zero, IntOffset.Zero)
        layout(width, if (split) height else nav.height) {
            nav.place(offsets.navigation)
            mini.place(offsets.miniPlayer)
        }
    }
}
