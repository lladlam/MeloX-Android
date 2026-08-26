package com.lladlam.melox.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.lladlam.melox.core.music.provider.PlaybackAccountSlot
import com.lladlam.melox.core.provider.kuwo.KuwoPhoneAuthClient
import com.lladlam.melox.core.provider.kuwo.KuwoSessionStore

@Composable
fun KuwoLoginScreen(
    onDismiss: () -> Unit,
    onLoggedIn: () -> Unit,
    targetSlot: PlaybackAccountSlot = PlaybackAccountSlot.Main,
) {
    val context = LocalContext.current.applicationContext
    val phoneAuthClient = remember { KuwoPhoneAuthClient() }

    MeloXPhoneCodeLoginScreen(
        serviceName = "酷我音乐",
        brandColor = Color(0xFFFC5A44),
        description = "使用手机号登录酷我音乐，登录态仅保存在本机",
        onClose = onDismiss,
        onSendCode = { _, phone ->
            runCatching {
                phoneAuthClient.sendCode(phone)
            }
        },
        onSubmitCode = { _, phone, code ->
            runCatching {
                val session = phoneAuthClient.login(phone, code)
                KuwoSessionStore.write(context, session, playback = targetSlot == PlaybackAccountSlot.Playback)
                onLoggedIn()
            }
        },
        onWebLogin = { /* 酷我音乐仅支持手机号登录 */ },
        webFallbackEmphasis = false,
    )
}
