package com.lladlam.melox.ui.legal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXSystemColors

const val MELOX_LEGAL_VERSION = "1.1-2026-08-25"

enum class MeloXLegalDocument(
    val title: String,
    internal val assetPath: String,
) {
    PrivacyPolicy("隐私政策", "legal/privacy-policy-zh-CN.md"),
    Disclaimer("免责声明与使用须知", "legal/disclaimer-zh-CN.md"),
    CloudControlPrivacy("云控隐私协议", "legal/cloud-control-privacy-zh-CN.md"),
}

private enum class LegalBlockKind { Heading, Subheading, Paragraph, Bullet }

private data class LegalBlock(
    val kind: LegalBlockKind,
    val text: String,
)

@Composable
fun MeloXLegalLinks(
    modifier: Modifier = Modifier,
    tint: Color = MeloXSystemColors.Blue,
) {
    var selectedDocument by remember { mutableStateOf<MeloXLegalDocument?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegalLink("隐私政策", tint) { selectedDocument = MeloXLegalDocument.PrivacyPolicy }
            Text(
                text = "与",
                modifier = Modifier.padding(horizontal = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            LegalLink("免责声明", tint) { selectedDocument = MeloXLegalDocument.Disclaimer }
        }
        LegalLink("云控隐私协议", tint) { selectedDocument = MeloXLegalDocument.CloudControlPrivacy }
    }

    selectedDocument?.let { document ->
        MeloXLegalDocumentDialog(
            document = document,
            onDismiss = { selectedDocument = null },
        )
    }
}

@Composable
private fun LegalLink(text: String, tint: Color, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        color = tint,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun MeloXLegalDocumentDialog(
    document: MeloXLegalDocument,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val blocks = remember(document) {
        context.assets.open(document.assetPath).bufferedReader().use { reader ->
            parseLegalDocument(reader.readText())
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        MeloXSymbolIcon(
                            symbol = MeloXSymbol.Xmark,
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            contentDescription = "关闭${document.title}",
                            iconSize = 15.sp,
                        )
                    }
                    Text(
                        text = document.title,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.size(36.dp))
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 22.dp,
                        top = 8.dp,
                        end = 22.dp,
                        bottom = 32.dp,
                    ),
                ) {
                    itemsIndexed(blocks) { index, block ->
                        LegalBlockText(
                            block = block,
                            first = index == 0,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MeloXFirstLaunchLegalConsent(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onOpenProject: () -> Unit,
) {
    MeloXGlassDialog(visible = true, onDismiss = {}) {
        Text("欢迎使用 MeloX", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "MeloX 是非官方开源项目。使用前请阅读并同意隐私政策与免责声明，了解账号登录、第三方服务、内容版权和本地数据处理方式。",
            modifier = Modifier.padding(top = 9.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        MeloXLegalLinks(modifier = Modifier.padding(top = 10.dp))
        Text(
            text = "点击“同意并继续”即表示你已阅读并同意以上文件。你可以随时在设置中重新查看。",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.54f),
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Text(
            text = "项目主页与开源许可",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onOpenProject)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            color = MeloXSystemColors.Blue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MeloXGlassButton(
                onClick = onDecline,
                modifier = Modifier.weight(1f),
                style = MeloXGlassButtonStyle.Plain,
            ) { Text("不同意并退出") }
            MeloXGlassButton(
                onClick = onAgree,
                modifier = Modifier.weight(1f),
                style = MeloXGlassButtonStyle.BorderedProminent,
            ) { Text("同意并继续") }
        }
    }
}

@Composable
private fun LegalBlockText(block: LegalBlock, first: Boolean) {
    when (block.kind) {
        LegalBlockKind.Heading -> Text(
            text = block.text,
            modifier = Modifier.padding(top = if (first) 0.dp else 24.dp, bottom = 4.dp),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 25.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.Bold,
        )
        LegalBlockKind.Subheading -> Text(
            text = block.text,
            modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
        LegalBlockKind.Paragraph -> Text(
            text = block.text,
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        LegalBlockKind.Bullet -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "•",
                modifier = Modifier.padding(end = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
            Text(
                text = block.text,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
        }
    }
}

private fun parseLegalDocument(markdown: String): List<LegalBlock> = markdown
    .lineSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .mapNotNull { line ->
        when {
            line.startsWith("# ") -> LegalBlock(LegalBlockKind.Heading, line.removePrefix("# "))
            line.startsWith("## ") -> LegalBlock(LegalBlockKind.Subheading, line.removePrefix("## "))
            line.startsWith("### ") -> LegalBlock(LegalBlockKind.Subheading, line.removePrefix("### "))
            line.startsWith("- ") -> LegalBlock(LegalBlockKind.Bullet, line.removePrefix("- "))
            line == "---" -> null
            else -> LegalBlock(LegalBlockKind.Paragraph, line)
        }
    }
    .toList()
