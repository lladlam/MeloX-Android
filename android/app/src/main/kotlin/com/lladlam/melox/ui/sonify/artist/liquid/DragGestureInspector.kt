package com.lladlam.melox.ui.sonify.artist.liquid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.fastFirstOrNull
import kotlin.math.abs

suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)

        val down = awaitFirstDown(false)
        val drag = initialDown

        onDragStart(down)
        onDrag(drag, Offset.Zero)
        val upEvent =
            drag(
                pointerId = drag.id,
                onDrag = { onDrag(it, it.positionChange()) }
            )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

/**
 * 独占式拖动：越过 touchSlop 后即消费指针，父级滚动容器不再收到未消费事件。
 *
 * 用于 Toggle 开关等"滑动只应作用于控件本身"的场景：在开关上按下再滑动时，
 * 父级 LazyColumn/LazyVerticalGrid 不再跟着滚页面。未越过 touchSlop 即抬起仍视为
 * 点按（走 [onEnd]，由调用方按"未拖动"处理）；被其它手势先消费时走 [onCancel]，
 * 调用方应回弹而不提交。
 */
suspend fun PointerInputScope.claimDragGestures(
    onStart: (down: PointerInputChange) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
    onDrag: (dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onStart(down)

        var pointer = down.id
        var total = Offset.Zero
        var claimed = false
        var finished = false
        var cancelled = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.fastFirstOrNull { it.id == pointer }
            if (change == null) {
                cancelled = true
                break
            }
            if (change.changedToUpIgnoreConsumed()) {
                finished = true
                break
            }
            if (change.isConsumed) {
                cancelled = true
                break
            }
            val delta = change.positionChange()
            total += delta
            if (!claimed) {
                // touchSlop 前不认领也不上报：既是点按判定窗口，也避免拇指在滑动手势
                // 起点微抖；越过即独占，方向不论（纵向滑动同样不该滚页面）。
                if (total.getDistance() <= viewConfiguration.touchSlop) continue
                claimed = true
            }
            change.consume()
            onDrag(delta)
        }
        if (finished) onEnd() else onCancel()
    }
}

/**
 * 水平独占拖动：只有**横向位移越过 touchSlop 且占优**时才认领并消费指针。
 *
 * 用于滚动容器里的横向滑条：纵向为主的滑动不认领、原样留给父级 LazyColumn，
 * 避免"拖滑条顺带把页面滚了"；横向一旦认领就独占，页面不会再跟着滚。
 * 未越过 touchSlop 即抬起视为点按（走 [onEnd]，调用方按"未拖动"处理）。
 */
suspend fun PointerInputScope.claimHorizontalDragGestures(
    onStart: (down: PointerInputChange) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
    onDrag: (dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onStart(down)

        var pointer = down.id
        var total = Offset.Zero
        var claimed = false
        var finished = false
        var cancelled = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.fastFirstOrNull { it.id == pointer }
            if (change == null) {
                cancelled = true
                break
            }
            if (change.changedToUpIgnoreConsumed()) {
                finished = true
                break
            }
            if (change.isConsumed) {
                cancelled = true
                break
            }
            val delta = change.positionChange()
            total += delta
            if (!claimed) {
                if (total.getDistance() <= viewConfiguration.touchSlop) continue
                // 纵向占优：这是滚页面，不是拖条。不认领、直接退出，
                // 事件留给父级滚动容器（此时我们还没消费过任何事件）。
                if (abs(total.x) <= abs(total.y)) {
                    cancelled = true
                    break
                }
                claimed = true
            }
            change.consume()
            onDrag(delta)
        }
        if (finished) onEnd() else onCancel()
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) {
        return null
    }
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) {
            return null
        }
        if (change.changedToUpIgnoreConsumed()) {
            return change
        }
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else {
            val hasDragged = dragEvent.previousPosition != dragEvent.position
            if (hasDragged) {
                return dragEvent
            }
        }
    }
}
