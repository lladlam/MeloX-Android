package com.lladlam.melox.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.remoteconfig.MeloXRemoteNotice
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXSystemColors

@Composable
fun MeloXRemoteNoticeDialog(
    notice: MeloXRemoteNotice,
    onAcknowledge: () -> Unit,
) {
    val (level, color) = when (notice.level) {
        "outage" -> "服务中断" to MeloXSystemColors.Red
        "warning" -> "兼容性提醒" to Color(0xFFFF9F0A)
        else -> "音乐源通知" to MeloXSystemColors.Blue
    }
    MeloXGlassDialog(visible = true, onDismiss = onAcknowledge) {
        Text(
            text = level,
            modifier = Modifier
                .background(color.copy(alpha = .14f), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = notice.title,
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = notice.message,
            modifier = Modifier.padding(top = 9.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f),
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            MeloXGlassButton(
                onClick = onAcknowledge,
                modifier = Modifier.fillMaxWidth(),
                style = MeloXGlassButtonStyle.BorderedProminent,
            ) { Text("我知道了") }
        }
    }
}
