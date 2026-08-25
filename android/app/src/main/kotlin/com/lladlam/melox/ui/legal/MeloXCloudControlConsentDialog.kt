package com.lladlam.melox.ui.legal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXSystemColors

const val MELOX_CLOUD_CONTROL_MESSAGE =
    "我希望你能了解最新的功能：云控。关于云控，你可以在“云控隐私协议”里面查看详细内容。云控只用于控制音乐源及其下属兼容功能，不用于其他目的。启用后，MeloX 会在每次应用进入前台时检查一次签名配置，并在应用保持前台运行期间每两小时检查一次。你可以选择拒绝或确定；确定代表你同意云控隐私协议，拒绝不会影响未依赖云控的功能，之后也可以随时在设置中修改选择。"

@Composable
fun MeloXCloudControlConsentDialog(
    onReject: () -> Unit,
    onAccept: () -> Unit,
) {
    var showPolicy by remember { mutableStateOf(false) }
    MeloXGlassDialog(visible = true, onDismiss = {}) {
        Text("欢迎使用MeloX！", style = MaterialTheme.typography.titleLarge)
        Text(
            MELOX_CLOUD_CONTROL_MESSAGE,
            modifier = Modifier.padding(top = 9.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f),
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Text(
            "查看云控隐私协议",
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button) { showPolicy = true }
                .padding(horizontal = 6.dp, vertical = 7.dp),
            color = MeloXSystemColors.Blue,
            fontWeight = FontWeight.Medium,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MeloXGlassButton(
                onClick = onReject,
                modifier = Modifier.weight(1f),
                style = MeloXGlassButtonStyle.Plain,
            ) { Text("拒绝") }
            MeloXGlassButton(
                onClick = onAccept,
                modifier = Modifier.weight(1f),
                style = MeloXGlassButtonStyle.BorderedProminent,
            ) { Text("确定") }
        }
    }
    if (showPolicy) {
        MeloXLegalDocumentDialog(
            document = MeloXLegalDocument.CloudControlPrivacy,
            onDismiss = { showPolicy = false },
        )
    }
}
