package com.lladlam.melox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun MeloXPinkCat(modifier: Modifier = Modifier) {
    Canvas(modifier.size(150.dp)) {
        val pink = Color(0xFFFF6FAE)
        val lightPink = Color(0xFFFFB4D2)
        val darkPink = Color(0xFFB83270)
        val center = Offset(size.width / 2f, size.height / 2f + 8f)
        val head = RoundRect(center.x - 43f, center.y - 35f, center.x + 43f, center.y + 40f, 30f, 30f)
        drawRoundRect(
            color = lightPink,
            topLeft = Offset(head.left, head.top),
            size = Size(head.width, head.height),
            cornerRadius = CornerRadius(30f, 30f),
        )
        val ears = Path().apply {
            moveTo(center.x - 39f, center.y - 25f)
            lineTo(center.x - 52f, center.y - 66f)
            lineTo(center.x - 17f, center.y - 42f)
            close()
            moveTo(center.x + 39f, center.y - 25f)
            lineTo(center.x + 52f, center.y - 66f)
            lineTo(center.x + 17f, center.y - 42f)
            close()
        }
        drawPath(ears, pink)
        drawCircle(darkPink, 4f, Offset(center.x - 17f, center.y - 4f))
        drawCircle(darkPink, 4f, Offset(center.x + 17f, center.y - 4f))
        drawCircle(pink, 6f, Offset(center.x, center.y + 10f))
        drawArc(darkPink, 205f, 65f, false, Offset(center.x - 8f, center.y + 8f), Size(16f, 16f), style = Stroke(2f))
        drawArc(darkPink, 270f, 65f, false, Offset(center.x - 8f, center.y + 8f), Size(16f, 16f), style = Stroke(2f))
        drawLine(darkPink, Offset(center.x - 25f, center.y + 9f), Offset(center.x - 58f, center.y + 4f), 2f)
        drawLine(darkPink, Offset(center.x - 25f, center.y + 16f), Offset(center.x - 58f, center.y + 19f), 2f)
        drawLine(darkPink, Offset(center.x + 25f, center.y + 9f), Offset(center.x + 58f, center.y + 4f), 2f)
        drawLine(darkPink, Offset(center.x + 25f, center.y + 16f), Offset(center.x + 58f, center.y + 19f), 2f)
    }
}
