package com.lladlam.melox.ui.account

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.SystemClock
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lladlam.melox.core.provider.qqmusic.QQMusicApiClient
import com.lladlam.melox.core.provider.qqmusic.QQMusicPhoneAuthClient
import com.lladlam.melox.core.provider.qqmusic.QQMusicSecurityChallengeException
import com.lladlam.melox.core.provider.qqmusic.QQMusicSessionStore
import com.lladlam.melox.core.music.provider.PlaybackAccountSlot
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigPolicy
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.legal.MeloXLegalLinks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val QQ_MUSIC_LOGIN_URL = "https://y.qq.com/"
private const val COOKIE_POLL_INTERVAL_MS = 500L
private const val COOKIE_STABLE_POLLS_BEFORE_VERIFY = 3
private const val VERIFY_RETRY_COOLDOWN_MS = 8_000L

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun QQMusicLoginScreen(
    onDismiss: () -> Unit,
    onLoggedIn: () -> Unit,
    targetSlot: PlaybackAccountSlot = PlaybackAccountSlot.Main,
) {
    val context = LocalContext.current.applicationContext
    val phoneAuthClient = remember { QQMusicPhoneAuthClient() }
    val scope = rememberCoroutineScope()
    var pendingPhone by rememberSaveable { mutableStateOf("") }
    var resumeAtCodeStep by rememberSaveable { mutableStateOf(false) }
    var useWebLogin by remember { mutableStateOf(true) }
    var showQrLoginTip by rememberSaveable { mutableStateOf(true) }
    var webLoginUrl by remember { mutableStateOf(QQ_MUSIC_LOGIN_URL) }
    var securityChallengeActive by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageLoading by remember { mutableStateOf(true) }
    var verifying by remember { mutableStateOf(false) }
    var securityRetrying by remember { mutableStateOf(false) }
    var verificationError by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = useWebLogin) {
        val view = webView
        if (view?.canGoBack() == true) view.goBack() else onDismiss()
    }

    LaunchedEffect(webView) {
        if (webView == null) return@LaunchedEffect

        var previousCandidate = ""
        var stablePolls = 0
        var lastAttemptFingerprint: String? = null
        var lastAttemptAt = 0L

        while (true) {
            val candidate = collectQQMusicCookieHeader()
            val session = QQMusicSessionStore.parse(candidate)

            if (candidate.isNotBlank() && candidate == previousCandidate) {
                stablePolls += 1
            } else {
                previousCandidate = candidate
                stablePolls = if (candidate.isBlank()) 0 else 1
            }

            if (!securityChallengeActive && session.isLoggedIn && stablePolls >= COOKIE_STABLE_POLLS_BEFORE_VERIFY && !verifying) {
                val fingerprint = "${session.uin}:${session.musicKey}"
                val now = SystemClock.elapsedRealtime()
                val shouldAttempt =
                    fingerprint != lastAttemptFingerprint || now - lastAttemptAt >= VERIFY_RETRY_COOLDOWN_MS

                if (shouldAttempt) {
                    lastAttemptFingerprint = fingerprint
                    lastAttemptAt = now
                    verifying = true
                    verificationError = null

                    val result = runCatching {
                        QQMusicApiClient(sessionProvider = { session }).accountProfile(session)
                    }

                    verifying = false
                    if (result.isSuccess) {
                        QQMusicSessionStore.write(context, candidate, playback = targetSlot == PlaybackAccountSlot.Playback)
                        CookieManager.getInstance().flush()
                        onLoggedIn()
                        return@LaunchedEffect
                    }

                    verificationError = result.exceptionOrNull()?.message
                        ?: "QQ音乐登录状态验证失败，请稍后重试"
                }
            }

            delay(COOKIE_POLL_INTERVAL_MS)
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
            serviceName = "QQ音乐",
            brandColor = Color(0xFF20C573),
            description = "使用手机号登录 QQ音乐，访问你的收藏与歌单。",
            onClose = onDismiss,
            onSendCode = { countryCode, phone ->
                try {
                    phoneAuthClient.sendCode(countryCode, phone)
                    Result.success(Unit)
                } catch (challenge: QQMusicSecurityChallengeException) {
                    webLoginUrl = challenge.securityUrl
                    securityChallengeActive = true
                    useWebLogin = true
                    Result.failure(challenge)
                } catch (error: Exception) {
                    Result.failure(error)
                }
            },
            onSubmitCode = { countryCode, phone, code ->
                try {
                    val cookie = phoneAuthClient.login(countryCode, phone, code)
                    val parsedSession = QQMusicSessionStore.parse(cookie)
                    QQMusicApiClient(sessionProvider = { parsedSession }).accountProfile()
                    QQMusicSessionStore.write(
                        context,
                        cookie,
                        playback = targetSlot == PlaybackAccountSlot.Playback,
                    )
                    onLoggedIn()
                    Result.success(Unit)
                } catch (challenge: QQMusicSecurityChallengeException) {
                    webLoginUrl = challenge.securityUrl
                    securityChallengeActive = true
                    useWebLogin = true
                    Result.failure(challenge)
                } catch (error: Exception) {
                    Result.failure(error)
                }
            },
            onWebLogin = {
                webLoginUrl = QQ_MUSIC_LOGIN_URL
                securityChallengeActive = false
                useWebLogin = true
            },
            webFallbackEmphasis = true,
            initialPhone = pendingPhone,
            startAtCodeStep = resumeAtCodeStep,
            onPhoneChanged = {
                pendingPhone = it
                resumeAtCodeStep = false
            },
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
                text = if (securityChallengeActive) "返回" else "取消",
                modifier = Modifier
                    .meloXLiquidButton(
                        shape = RoundedCornerShape(18.dp),
                        tint = Color(0xFFFF3147),
                        surfaceColor = Color(0xFFFF3147).copy(alpha = 0.08f),
                        lensRadius = 7.dp,
                        refractionHeight = 11.dp,
                    )
                    .clickable {
                        onDismiss()
                    }
                    .padding(8.dp),
                color = Color(0xFFFF3147),
                fontSize = 16.sp,
            )
            Text(
                text = if (securityChallengeActive) "QQ音乐安全验证" else "登录 QQ音乐",
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (securityChallengeActive) "验证完成" else "取消",
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = securityChallengeActive && !securityRetrying) {
                        scope.launch {
                            securityRetrying = true
                            verificationError = null
                            runCatching {
                                phoneAuthClient.sendCode("86", pendingPhone.filter(Char::isDigit))
                            }.onSuccess {
                                webView?.stopLoading()
                                webView?.destroy()
                                webView = null
                                securityChallengeActive = false
                                webLoginUrl = QQ_MUSIC_LOGIN_URL
                                resumeAtCodeStep = true
                                useWebLogin = false
                            }.onFailure { error ->
                                verificationError = error.message ?: "安全验证尚未生效，请稍后重试"
                            }
                            securityRetrying = false
                        }
                    }
                    .padding(8.dp),
                color = if (securityChallengeActive) Color(0xFF20C573) else Color.Transparent,
                fontSize = 16.sp,
            )
        }

        if (pageLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        webView = this
                        setBackgroundColor(AndroidColor.TRANSPARENT)

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.userAgentString =
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/124.0.0.0 Safari/537.36"

                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            private var firstVisiblePageCommitted = false

                            override fun onPageCommitVisible(view: WebView?, url: String?) {
                                if (!firstVisiblePageCommitted) {
                                    firstVisiblePageCommitted = true
                                    pageLoading = false
                                }
                                super.onPageCommitVisible(view, url)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (!firstVisiblePageCommitted) {
                                    firstVisiblePageCommitted = true
                                    pageLoading = false
                                }
                                super.onPageFinished(view, url)
                            }
                        }
                        loadUrl(webLoginUrl)
                    }
                },
            )

            if (verifying || securityRetrying) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            shape = RoundedCornerShape(18.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = if (securityRetrying) "正在重新发送验证码…" else "正在验证 QQ音乐登录状态…",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                    )
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
            tint = Color(0xFF20C573),
        )
    }
    if (showQrLoginTip) {
        MeloXGlassDialog(visible = true, onDismiss = { showQrLoginTip = false }) {
            Text("QQ 登录提示", style = MaterialTheme.typography.titleLarge)
            Text(
                "如果使用 QQ 扫码登录，请先截图二维码，再把 MeloX 挂到小窗，然后前往 QQ 扫描截图中的二维码，否则可能无法完成登录。",
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                lineHeight = 21.sp,
            )
            MeloXGlassButton(
                onClick = { showQrLoginTip = false },
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                style = MeloXGlassButtonStyle.BorderedProminent,
            ) { Text("知道了") }
        }
    }
}

private fun collectQQMusicCookieHeader(): String {
    val manager = CookieManager.getInstance()
    val values = linkedMapOf<String, String>()
    val urls = listOf(
        "https://y.qq.com/",
        "https://u.y.qq.com/",
        "https://c.y.qq.com/",
        "https://c6.y.qq.com/",
        "https://music.qq.com/",
        "https://qq.com/",
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
