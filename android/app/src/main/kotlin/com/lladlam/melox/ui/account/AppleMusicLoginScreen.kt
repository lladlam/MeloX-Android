package com.lladlam.melox.ui.account

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.provider.applemusic.AppleMusicSessionStore
import com.lladlam.melox.core.provider.applemusic.AppleMusicSdkBridge
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassTextField
import com.lladlam.melox.ui.glass.MeloXSystemColors
import com.lladlam.melox.ui.legal.MeloXLegalLinks

/**
 * Apple Music credential configuration.
 *
 * Apple does not provide a complete Android third-party authorize() flow like
 * MusicKit on iOS. This screen accepts tokens produced by the user's own
 * MusicKit/backend integration and never asks for an Apple password.
 */
@Composable
fun AppleMusicLoginScreen(
    onDismiss: () -> Unit,
    onLoggedIn: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val initial = remember { AppleMusicSessionStore.read(context) }
    var developerToken by remember { mutableStateOf(initial.developerToken) }
    var musicUserToken by remember { mutableStateOf(initial.musicUserToken) }
    var storefront by remember { mutableStateOf(initial.storefront) }
    var error by remember { mutableStateOf<String?>(null) }
    val officialAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val token = AppleMusicSdkBridge.extractMusicUserToken(result.data)
        if (token.isNullOrBlank()) {
            error = "Apple Music 授权未返回 Music User Token，请确认账号有订阅并重试"
        } else {
            AppleMusicSessionStore.write(context, developerToken, token, storefront)
            onLoggedIn()
        }
    }

    BackHandler(onBack = onDismiss)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MeloXGlassButton(
                onClick = onDismiss,
                style = MeloXGlassButtonStyle.Plain,
            ) { Text("取消", color = MeloXSystemColors.Red) }
            Text("配置 Apple Music", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(1.dp).weight(0.01f))
        }
        Text(
            "使用 Apple Developer / MusicKit 生成的凭据。MeloX 只调用官方 Apple Music API，不读取 Apple Music APK 的私有登录或 DRM 凭据。",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .62f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        MeloXGlassTextField(
            value = developerToken,
            onValueChange = { developerToken = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Developer Token", color = Color.Gray) },
        )
        MeloXGlassTextField(
            value = musicUserToken,
            onValueChange = { musicUserToken = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Music User Token（可选）", color = Color.Gray) },
        )
        MeloXGlassTextField(
            value = storefront,
            onValueChange = { storefront = it.take(8); error = null },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Storefront，例如 us / cn", color = Color.Gray) },
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        MeloXGlassButton(
            onClick = {
                val intent = developerToken.trim().takeIf(String::isNotBlank)?.let {
                    AppleMusicSdkBridge.createAuthenticationIntent(context, it)
                }
                if (intent == null) {
                    error = if (developerToken.isBlank()) {
                        "请先填写 Developer Token"
                    } else {
                        "未找到 Apple Music 官方 Android SDK，请把 musickitauth-release-*.aar 放入 app/libs 后重新编译"
                    }
                } else {
                    officialAuthLauncher.launch(intent)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            style = MeloXGlassButtonStyle.BorderedProminent,
        ) { Text("在 Apple Music 中登录并获取 Token") }
        MeloXGlassButton(
            onClick = {
                if (developerToken.isBlank()) {
                    error = "请先填写 Developer Token"
                } else {
                    AppleMusicSessionStore.write(context, developerToken, musicUserToken, storefront)
                    onLoggedIn()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            style = MeloXGlassButtonStyle.Bordered,
        ) { Text("仅保存 API 配置") }
        if (initial.isConfigured) {
            MeloXGlassButton(
                onClick = {
                    AppleMusicSessionStore.clear(context)
                    onLoggedIn()
                },
                modifier = Modifier.fillMaxWidth(),
                style = MeloXGlassButtonStyle.Destructive,
            ) { Text("清除 Apple Music 配置") }
        }
        Spacer(Modifier.weight(1f))
        MeloXLegalLinks()
    }
}
