package com.lladlam.melox.ui.account

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.music.provider.PlaybackAccountSlot
import com.lladlam.melox.core.network.NeteasePhoneAuthClient
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigPolicy
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.legal.MeloXLegalLinks
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val NETEASE_LOGIN_URL = "https://music.163.com/#/login"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NeteaseLoginScreen(
    session: NeteaseSessionStore,
    onDismiss: () -> Unit,
    onLoggedIn: () -> Unit,
    targetSlot: PlaybackAccountSlot = PlaybackAccountSlot.Main,
) {
    val context = LocalContext.current.applicationContext
    val phoneAuthClient = remember { NeteasePhoneAuthClient() }
    var useWebLogin by remember {
        mutableStateOf(!MeloXRemoteConfigPolicy.capabilityEnabled(context, "netease_phone_login"))
    }
    var loginPrepared by remember { mutableStateOf(targetSlot == PlaybackAccountSlot.Main) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageLoading by remember { mutableStateOf(true) }
    var verifying by remember { mutableStateOf(false) }
    var verificationError by remember { mutableStateOf<String?>(null) }
    var handledCookie by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = useWebLogin) {
        val view = webView
        if (view?.canGoBack() == true) view.goBack() else useWebLogin = false
    }

    LaunchedEffect(targetSlot, useWebLogin) {
        if (!useWebLogin) return@LaunchedEffect
        if (targetSlot == PlaybackAccountSlot.Playback) {
            suspendCancellableCoroutine { continuation ->
                CookieManager.getInstance().removeAllCookies {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            CookieManager.getInstance().flush()
        }
        loginPrepared = true
    }

    LaunchedEffect(webView, loginPrepared) {
        if (webView == null || !loginPrepared) return@LaunchedEffect
        while (true) {
            val candidate = collectNeteaseCookieHeader()
            if (
                candidate.isNotBlank() &&
                NeteaseSessionStore.containsMusicU(candidate) &&
                candidate != handledCookie &&
                !verifying
            ) {
                handledCookie = candidate
                verifying = true
                verificationError = null
                val result = session.acceptAuthenticatedCookie(candidate, persist = targetSlot == PlaybackAccountSlot.Main)
                verifying = false
                if (result.isSuccess) {
                    if (targetSlot == PlaybackAccountSlot.Playback) NeteaseSessionStore.writePlaybackCookie(context, candidate)
                    CookieManager.getInstance().flush()
                    webView?.post(onLoggedIn)
                    return@LaunchedEffect
                }
                verificationError = result.exceptionOrNull()?.message
                    ?: "登录状态验证失败，请稍后重试"
                handledCookie = null
            }
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
        }
    }

    if (!useWebLogin) {
        MeloXPhoneCodeLoginScreen(
            serviceName = "网易云音乐",
            brandColor = Color(0xFFE60026),
            description = "使用手机号接收短信验证码，登录后即可同步你的网易云音乐内容。",
            onClose = onDismiss,
            onSendCode = { countryCode, phone ->
                runCatching { phoneAuthClient.sendCode(countryCode, phone) }
            },
            onSubmitCode = { countryCode, phone, code ->
                runCatching {
                    val cookie = phoneAuthClient.login(countryCode, phone, code)
                    session.acceptAuthenticatedCookie(
                        cookie,
                        persist = targetSlot == PlaybackAccountSlot.Main,
                    ).getOrThrow()
                    if (targetSlot == PlaybackAccountSlot.Playback) {
                        NeteaseSessionStore.writePlaybackCookie(context, cookie)
                    }
                    onLoggedIn()
                }
            },
            onWebLogin = { useWebLogin = true },
        )
    } else Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "取消",
                modifier = Modifier
                    .meloXLiquidButton(
                        shape = RoundedCornerShape(18.dp),
                        tint = Color(0xFFFF3147),
                        surfaceColor = Color(0xFFFF3147).copy(alpha = 0.08f),
                        lensRadius = 7.dp,
                        refractionHeight = 11.dp,
                    )
                    .clickable(onClick = onDismiss)
                    .padding(8.dp),
                color = Color(0xFFFF3147),
                fontSize = 16.sp,
            )
            Text(
                text = "手机号登录网易云音乐",
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "取消",
                modifier = Modifier.padding(8.dp),
                color = Color.Transparent,
                fontSize = 16.sp,
            )
        }

        if (pageLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Box(modifier = Modifier.weight(1f)) {
            if (loginPrepared) AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webView = this
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.userAgentString =
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/124.0.0.0 Safari/537.36"

                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                pageLoading = false
                                super.onPageFinished(view, url)
                            }
                        }
                        loadUrl(NETEASE_LOGIN_URL)
                    }
                },
            )

            if (verifying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            text = "正在验证登录状态…",
                            modifier = Modifier.padding(top = 12.dp),
                            color = Color.White,
                        )
                    }
                }
            }

            verificationError?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 13.sp,
                )
            }
        }
        MeloXLegalLinks(
            modifier = Modifier.padding(vertical = 6.dp),
            tint = Color(0xFFE60026),
        )
    }
}

private fun collectNeteaseCookieHeader(): String {
    val manager = CookieManager.getInstance()
    val values = linkedMapOf<String, String>()
    val urls = listOf(
        "https://music.163.com/",
        "https://interface.music.163.com/",
    )

    urls.forEach { url ->
        manager.getCookie(url)
            ?.split(';')
            ?.forEach { item ->
                val parts = item.trim().split('=', limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank()) {
                    values[parts[0].trim()] = parts[1].trim()
                }
            }
    }

    return values.toSortedMap().entries.joinToString("; ") { (key, value) -> "$key=$value" }
}
