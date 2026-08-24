package com.lladlam.melox.ui.glass

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

data class MeloXSwipeAction(
    val label: String,
    val symbol: MeloXSymbol,
    val color: Color,
    val onInvoke: () -> Unit,
)

@Composable
fun MeloXSwipeActionRow(
    startActions: List<MeloXSwipeAction>,
    endActions: List<MeloXSwipeAction>,
    startFullSwipeActionIndex: Int = 0,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val actionWidthPx = with(density) { 120.dp.toPx() }
    var rowWidthPx by remember { mutableFloatStateOf(1f) }
    var offsetPx by remember { mutableFloatStateOf(0f) }
    var dragDisplacementPx by remember { mutableFloatStateOf(0f) }
    var crossedFullThreshold by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    fun animateOffset(target: Float, after: (() -> Unit)? = null) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(
                initialValue = offsetPx,
                targetValue = target,
                animationSpec = spring(dampingRatio = .82f, stiffness = 260f),
            ) { value, _ -> offsetPx = value }
            after?.invoke()
        }
    }

    fun invokeAction(action: MeloXSwipeAction) {
        action.onInvoke()
        animateOffset(0f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .onSizeChanged { size -> rowWidthPx = size.width.toFloat().coerceAtLeast(1f) },
    ) {
        MeloXSwipeActionLayer(
            actions = startActions,
            revealPx = offsetPx.coerceAtLeast(0f),
            rowWidthPx = rowWidthPx,
            actionWidthPx = actionWidthPx,
            fullSwipeActionIndex = startFullSwipeActionIndex,
            alignStart = true,
            onAction = ::invokeAction,
        )
        MeloXSwipeActionLayer(
            actions = endActions,
            revealPx = (-offsetPx).coerceAtLeast(0f),
            rowWidthPx = rowWidthPx,
            actionWidthPx = actionWidthPx,
            fullSwipeActionIndex = 0,
            alignStart = false,
            onAction = ::invokeAction,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .pointerInput(startActions, endActions, rowWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            settleJob?.cancel()
                            dragDisplacementPx = offsetPx
                            crossedFullThreshold = false
                        },
                        onHorizontalDrag = { change, amount ->
                            dragDisplacementPx += amount
                            val actions = if (offsetPx + amount >= 0f) startActions else endActions
                            if (actions.isEmpty()) return@detectHorizontalDragGestures
                            change.consume()
                            val proposed = offsetPx + amount
                            val revealLimit = actions.size * actionWidthPx
                            offsetPx = if (abs(proposed) <= revealLimit) {
                                proposed
                            } else {
                                sign(proposed) * (revealLimit + (abs(proposed) - revealLimit) * .28f)
                            }
                            val full = abs(dragDisplacementPx) >= rowWidthPx * FULL_SWIPE_THRESHOLD
                            if (full != crossedFullThreshold) {
                                crossedFullThreshold = full
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDragCancel = { animateOffset(0f) },
                        onDragEnd = {
                            val actions = if (offsetPx >= 0f) startActions else endActions
                            val full = actions.isNotEmpty() && crossedFullThreshold
                            if (full) {
                                val action = if (offsetPx >= 0f) {
                                    actions[startFullSwipeActionIndex.coerceIn(actions.indices)]
                                } else {
                                    actions.first()
                                }
                                animateOffset(sign(offsetPx) * rowWidthPx) {
                                    action.onInvoke()
                                    animateOffset(0f)
                                }
                            } else {
                                val revealWidth = actions.size * actionWidthPx
                                val target = if (actions.isNotEmpty() && abs(offsetPx) >= actionWidthPx * .48f) {
                                    sign(offsetPx) * revealWidth
                                } else {
                                    0f
                                }
                                animateOffset(target)
                            }
                        },
                    )
                }
                .combinedClickable(
                    onClick = {
                        if (abs(offsetPx) > 1f) animateOffset(0f) else onClick()
                    },
                    onLongClick = onLongClick?.let { action ->
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            action()
                        }
                    },
                ),
        ) {
            content()
        }
    }
}

@Composable
private fun BoxScope.MeloXSwipeActionLayer(
    actions: List<MeloXSwipeAction>,
    revealPx: Float,
    rowWidthPx: Float,
    actionWidthPx: Float,
    fullSwipeActionIndex: Int,
    alignStart: Boolean,
    onAction: (MeloXSwipeAction) -> Unit,
) {
    if (actions.isEmpty() || revealPx <= .5f) return
    val density = LocalDensity.current
    val fullProgress = (
        (revealPx / rowWidthPx - FULL_SWIPE_THRESHOLD) /
            (1f - FULL_SWIPE_THRESHOLD)
        ).coerceIn(0f, 1f)
    val revealWidth = with(density) { revealPx.toDp() }
    val alignment = if (alignStart) Alignment.CenterStart else Alignment.CenterEnd
    val primaryIndex = fullSwipeActionIndex.coerceIn(actions.indices)

    Box(Modifier.matchParentSize(), contentAlignment = alignment) {
        Row(
            modifier = Modifier
                .width(revealWidth)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            actions.forEachIndexed { index, action ->
                val baseShare = 1f / actions.size
                val share = if (index == primaryIndex) {
                    baseShare + (1f - baseShare) * fullProgress
                } else {
                    baseShare * (1f - fullProgress)
                }.coerceAtLeast(.001f)
                val actionWidth = revealPx * share
                val iconProgress = (actionWidth / (actionWidthPx * .72f)).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .weight(share)
                        .fillMaxHeight()
                        .padding(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(action.color)
                        .combinedClickable(
                            onClick = { onAction(action) },
                            onLongClick = { onAction(action) },
                        )
                        .semantics { contentDescription = action.label },
                    contentAlignment = Alignment.Center,
                ) {
                    MeloXSymbolIcon(
                        action.symbol,
                        Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                alpha = iconProgress
                                scaleX = .72f + .28f * iconProgress
                                scaleY = .72f + .28f * iconProgress
                            },
                        Color.White,
                    )
                }
            }
        }
    }
}

private const val FULL_SWIPE_THRESHOLD = .44f
