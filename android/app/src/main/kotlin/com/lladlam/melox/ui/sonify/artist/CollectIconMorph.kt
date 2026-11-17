package com.lladlam.melox.ui.sonify.artist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * 「加入资料库」的加号 ↔ 勾号**形变**图标（不是交叉淡化：一个字形连续变成另一个）。
 *
 * 两个字形都用圆头描边画——原 ic_add / ic_checkmark 本就是圆头描边的外轮廓，
 * 坐标直接按它们的 viewport(32×32) 反解，故静止态与原图标视觉一致。
 * 两条线段、四个端点、线宽全部随进度插值；勾号的两条线段在 elbow 共点，
 * 圆头端点叠合即为圆角拐点，不需要 Path。
 *
 * 配对：竖臂 → 长臂、横臂 → 短臂。长条保持长、横条收进短臂，
 * 中途形态最像「勾号正在成形」，不会绞成乱麻（SVG 逐帧比对过）。
 */
@Composable
internal fun CollectIconMorph(
    collected: Boolean,
    contentDescription: String?,
    tint: Color,
    iconSize: Dp = 24.dp,
    animated: Boolean = true
) {
    val progress by animateFloatAsState(
        targetValue = if (collected) 1f else 0f,
        animationSpec = if (animated) IconMorphSpring else snap(),
        label = "collectIconMorph"
    )
    Canvas(
        modifier = Modifier
            .size(iconSize)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            }
    ) {
        // 一律先按 32 单位坐标系画，再乘 unit 缩到实际尺寸
        val unit = this.size.minDimension / 32f
        val width = lerp(PlusStrokeWidth, CheckStrokeWidth, progress) * unit
        fun point(from: Offset, to: Offset) = Offset(
            x = lerp(from.x, to.x, progress) * unit,
            y = lerp(from.y, to.y, progress) * unit
        )
        drawLine(
            color = tint,
            start = point(PlusVerticalStart, CheckLongStart),
            end = point(PlusVerticalEnd, CheckLongEnd),
            strokeWidth = width,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = point(PlusHorizontalStart, CheckShortStart),
            end = point(PlusHorizontalEnd, CheckShortEnd),
            strokeWidth = width,
            cap = StrokeCap.Round
        )
    }
}

/** 略微回弹（过冲约 1.5%，肉眼不可见），约 210ms 落定。 */
private val IconMorphSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 900f, visibilityThreshold = 0.001f)

// ---- 32 单位坐标系（y 向下，与 drawable viewport 一致）----

// 加号：两条圆头线段，臂端已扣掉圆头半径（原图标外沿 6.33 ↔ 25.67）
private val PlusVerticalStart = Offset(16f, 7.38f)
private val PlusVerticalEnd = Offset(16f, 24.62f)
private val PlusHorizontalStart = Offset(7.38f, 16f)
private val PlusHorizontalEnd = Offset(24.62f, 16f)
private const val PlusStrokeWidth = 2.10f

// 勾号：长臂 + 短臂（由 ic_checkmark 轮廓反解，外沿 5.64..26.36 × 6.66..26.99）
private val CheckLongStart = Offset(24.96f, 8.05f)
private val CheckLongEnd = Offset(13.57f, 25.57f)
private val CheckShortStart = Offset(7.06f, 17.59f)
private val CheckShortEnd = Offset(13.57f, 25.57f)
private const val CheckStrokeWidth = 2.84f
