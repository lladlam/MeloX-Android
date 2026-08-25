package com.lladlam.melox.ui.account

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.BuildConfig
import com.lladlam.melox.core.network.MeloXHttpClient
import com.lladlam.melox.core.provider.spotify.SpotifyOAuth
import com.lladlam.melox.core.provider.spotify.SpotifySessionStore
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigPolicy
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXSystemColors
import com.lladlam.melox.ui.legal.MeloXLegalLinks
import kotlinx.coroutines.delay

@Composable
fun SpotifyLoginScreen(onDismiss: () -> Unit, onLoggedIn: () -> Unit) {
    val context = LocalContext.current.applicationContext
    if (!MeloXRemoteConfigPolicy.capabilityEnabled(context, "spotify_oauth")) {
        BackHandler(onBack = onDismiss)
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MeloXGlassButton(onClick = onDismiss, style = MeloXGlassButtonStyle.Plain) { Text("关闭") }
            Text("Spotify 登录暂时不可用", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Spotify OAuth 接口已由远程兼容性配置临时关闭。你可以稍后更新软件或重试。",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .64f),
            )
            MeloXLegalLinks()
        }
        return
    }
    var error by remember { mutableStateOf(SpotifySessionStore.consumeOAuthError(context)) }
    val configured = BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank()

    BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) {
        while (true) {
            SpotifySessionStore.consumeOAuthError(context)?.let { error = it }
            if (SpotifySessionStore.read(context).isLoggedIn) {
                onLoggedIn()
                return@LaunchedEffect
            }
            delay(400)
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MeloXGlassButton(onClick = onDismiss, style = MeloXGlassButtonStyle.Plain) {
                Text("取消", color = MeloXSystemColors.Red)
            }
            Text("登录 Spotify", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier)
        }
        Text(
            "使用系统浏览器完成 Spotify Authorization Code + PKCE 授权。MeloX 不使用 Client Secret，也不会读取 Spotify App 私有凭据。",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .62f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        if (!configured) {
            Text(
                "Spotify Client ID 未配置。请在 Gradle property 中设置 meloxSpotifyClientId，然后重新构建应用；并在 Spotify Dashboard 注册 ${SpotifyOAuth.RedirectUri}。",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        MeloXGlassButton(
            onClick = {
                runCatching {
                    val uri = SpotifyOAuth(context, BuildConfig.SPOTIFY_CLIENT_ID, MeloXHttpClient.shared)
                        .authorizationUri()
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { error = it.message ?: "无法启动 Spotify 授权" }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = configured,
            style = MeloXGlassButtonStyle.BorderedProminent,
        ) { Text("在浏览器中登录 Spotify") }
        Spacer(Modifier.weight(1f))
        MeloXLegalLinks(tint = androidx.compose.ui.graphics.Color(0xFF1DB954))
    }
}
