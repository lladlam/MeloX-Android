package com.lladlam.melox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.R
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.theme.MeloXBrandLogo
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.legal.*

/** Full-screen first-run pages; account sign-in starts after the consent steps. */
@Composable
internal fun MeloXOnboardingWelcomePage(
    onContinue: () -> Unit,
    onLicenses: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MeloXBrandLogo(112.dp)
            Spacer(Modifier.height(26.dp))
            Text(
                "MeloX",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.onboarding_welcome_subtitle),
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                lineHeight = 23.sp,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.onboarding_disclaimer),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MeloXGlassButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                style = MeloXGlassButtonStyle.BorderedProminent,
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                Text(stringResource(R.string.onboarding_continue), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            MeloXGlassButton(
                onClick = onLicenses,
                modifier = Modifier.fillMaxWidth(),
                style = MeloXGlassButtonStyle.Plain,
                contentPadding = PaddingValues(vertical = 9.dp),
            ) {
                Text(stringResource(R.string.onboarding_licenses), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun MeloXOnboardingChoicePage(
    title: String,
    primaryLabel: String,
    secondaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler { }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MeloXBrandLogo(88.dp, Modifier.padding(top = 24.dp))
            Text(title, modifier = Modifier.padding(top = 22.dp, bottom = 16.dp),
                color = MaterialTheme.colorScheme.onBackground, fontSize = 28.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            content()
            Spacer(Modifier.height(24.dp))
        }
        MeloXGlassButton(onClick = onPrimary, modifier = Modifier.fillMaxWidth(),
            style = MeloXGlassButtonStyle.BorderedProminent,
            contentPadding = PaddingValues(vertical = 12.dp)) {
            Text(primaryLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        MeloXGlassButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth(),
            style = MeloXGlassButtonStyle.Plain,
            contentPadding = PaddingValues(vertical = 9.dp)) {
            Text(secondaryLabel, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun MeloXOnboardingLegalPage(onAgree: () -> Unit, onDecline: () -> Unit, onOpenProject: () -> Unit) {
    MeloXOnboardingChoicePage(
        title = stringResource(R.string.legal_welcome),
        primaryLabel = stringResource(R.string.legal_agree_continue),
        secondaryLabel = stringResource(R.string.legal_decline_exit),
        onPrimary = onAgree, onSecondary = onDecline,
    ) {
        Text(stringResource(R.string.legal_welcome_body), fontSize = 15.sp, lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f), textAlign = TextAlign.Center)
        MeloXLegalLinks(modifier = Modifier.padding(vertical = 16.dp))
        Text(stringResource(R.string.legal_agree_note), fontSize = 12.sp, lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .54f), textAlign = TextAlign.Center)
        MeloXGlassButton(onClick = onOpenProject, style = MeloXGlassButtonStyle.Plain) {
            Text(stringResource(R.string.legal_project))
        }
    }
}

@Composable
internal fun MeloXOnboardingCloudPage(onReject: () -> Unit, onAccept: () -> Unit) {
    var showPolicy by remember { mutableStateOf(false) }
    MeloXOnboardingChoicePage(
        title = "云控选择", primaryLabel = "同意并启用", secondaryLabel = "暂不启用",
        onPrimary = onAccept, onSecondary = onReject,
    ) {
        Text(MELOX_CLOUD_CONTROL_MESSAGE, fontSize = 15.sp, lineHeight = 23.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f))
        MeloXGlassButton(onClick = { showPolicy = true }, style = MeloXGlassButtonStyle.Plain) {
            Text("查看云控隐私协议")
        }
    }
    if (showPolicy) MeloXLegalDocumentDialog(MeloXLegalDocument.CloudControlPrivacy, onDismiss = { showPolicy = false })
}

@Composable
internal fun MeloXOnboardingAccountPage(
    isLoggedIn: Boolean,
    nickname: String?,
    avatarUrl: String?,
    onLogin: () -> Unit,
    onFinish: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (isLoggedIn) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(CircleShape),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    nickname.orEmpty().ifBlank { stringResource(R.string.onboarding_account_title) },
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.onboarding_account_logged_in),
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                MeloXBrandLogo(88.dp)
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.onboarding_account_title),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.onboarding_account_body),
                    modifier = Modifier.padding(top = 10.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Column(
                    Modifier.fillMaxWidth().padding(top = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf(
                        R.string.onboarding_benefit_sync,
                        R.string.onboarding_benefit_recommend,
                        R.string.onboarding_benefit_local,
                    ).forEach { benefit ->
                        Text(
                            "• ${stringResource(benefit)}",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .74f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            if (isLoggedIn) {
                MeloXGlassButton(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(),
                    style = MeloXGlassButtonStyle.BorderedProminent,
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.onboarding_start), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                MeloXGlassButton(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                    style = MeloXGlassButtonStyle.BorderedProminent,
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.app_login_netease), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                MeloXGlassButton(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(),
                    style = MeloXGlassButtonStyle.Plain,
                    contentPadding = PaddingValues(vertical = 9.dp),
                ) {
                    Text(stringResource(R.string.onboarding_maybe_later), fontSize = 14.sp)
                }
            }
        }
    }
}

/** Post-onboarding appearance chooser. Can be reopened only by clearing prefs. */
@Composable
internal fun MeloXAppearancePickerPage(
    onSelectMeloX: () -> Unit,
    onSelectAppleMusic: () -> Unit,
) {
    BackHandler { }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "选择你的外观",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "之后可以在「设置 › 外观」中随时修改",
            modifier = Modifier.padding(top = 8.dp, bottom = 22.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppearanceChoiceCard(
                label = "弥乐外观",
                modifier = Modifier.weight(1f),
                onClick = onSelectMeloX,
            ) { MeloXHomePreview() }
            AppearanceChoiceCard(
                label = "Apple Music 外观",
                modifier = Modifier.weight(1f),
                onClick = onSelectAppleMusic,
            ) { AppleMusicHomePreview() }
        }
        Text(
            "点击图片即可开始使用",
            modifier = Modifier.padding(top = 20.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun AppearanceChoiceCard(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.56f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                .background(Color.Black)
                .border(0.75.dp, Color.White.copy(alpha = .14f), androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                .clickable(onClick = onClick),
        ) { content() }
        Text(
            label,
            modifier = Modifier.padding(top = 10.dp),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MeloXHomePreview() {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("首页", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("下午好", color = Color.White.copy(alpha = .45f), fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PreviewTile("每日推荐", listOf(Color(0xFFFF4D7D), Color(0xFFFF7A5C)), Modifier.weight(1f))
            PreviewTile("热歌榜", listOf(Color(0xFFFFA23D), Color(0xFFFF7A3D)), Modifier.weight(1f))
        }
        Text("每日推荐", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(2) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(86.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
                        .background(if (index == 0) Color(0xFF6B4B9A) else Color(0xFF225577)),
                )
            }
        }
    }
}

@Composable
private fun PreviewTile(title: String, colors: List<Color>, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(64.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(colors)),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(7.dp))
    }
}

@Composable
private fun AppleMusicHomePreview() {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("主页", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("为你推荐", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text("来自网易云音乐", color = Color.White.copy(alpha = .45f), fontSize = 9.sp)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .weight(1.35f)
                    .height(150.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF3A2B45), Color(0xFF1E1A24)))),
            )
            Box(
                Modifier
                    .weight(.65f)
                    .height(150.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF254C77), Color(0xFF10243D)))),
            )
        }
        Text("每日推荐", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(58.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
                        .background(if (index == 0) Color(0xFF7A4D77) else if (index == 1) Color(0xFF3E6C8A) else Color(0xFF6B5A3E)),
                )
            }
        }
    }
}
