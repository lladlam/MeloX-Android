package com.lladlam.melox.ui.account

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lladlam.melox.R
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.music.provider.PlaybackAccountSlot
import com.lladlam.melox.core.network.NeteasePhoneAuthClient
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigPolicy
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.glass.MeloXGlassIconButton
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.theme.isMeloXDarkTheme
import com.lladlam.melox.ui.animation.meloXPagePushSpec
import com.lladlam.melox.ui.legal.MeloXLegalLinks
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume



@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NeteaseLoginScreen(
    session: NeteaseSessionStore,
    onDismiss: () -> Unit,
    onLoggedIn: () -> Unit,
    targetSlot: PlaybackAccountSlot = PlaybackAccountSlot.Main,
) {
    val activityContext = LocalContext.current
    val context = activityContext.applicationContext
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
    // 独立扫码登录页（后台自动点开扫码弹窗 → 提取 canvas 二维码原生显示）
    var showQrPage by remember { mutableStateOf(false) }

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
                    ?: activityContext.getString(R.string.account_login_verify_failed)
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
        Box(modifier = Modifier.fillMaxSize()) {
            // 手机号/验证码页 ↔ 扫码页：同款 push/pop 滑动转场（自右滑入、向左滑出），
            // 与外层登录页、设置子页一致。两屏叠放、扫码页在后（back 调度优先），
            // 过渡期不会两个 BackHandler 同时生效导致误关整页。
            AnimatedVisibility(
                visible = !showQrPage,
                enter = slideInHorizontally(meloXPagePushSpec()) { -it },
                exit = slideOutHorizontally(meloXPagePushSpec()) { -it },
            ) {
                MeloXPhoneCodeLoginScreen(
                    serviceName = stringResource(R.string.account_netease),
                    brandColor = Color(0xFFE60026),
                    description = stringResource(R.string.account_netease_phone_desc),
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
                    onQrLogin = { showQrPage = true },
                )
            }
            AnimatedVisibility(
                visible = showQrPage,
                enter = slideInHorizontally(meloXPagePushSpec()) { it },
                exit = slideOutHorizontally(meloXPagePushSpec()) { it },
            ) {
                NeteaseQrLoginScreen(
                    session = session,
                    onDismiss = { showQrPage = false },
                    onLoggedIn = onLoggedIn,
                    targetSlot = targetSlot,
                )
            }
        }
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
            // 返回键与全站统一：液态玻璃圆钮（MeloXGlassIconButton，44dp）。
            MeloXGlassIconButton(
                symbol = MeloXSymbol.ChevronLeft,
                onClick = onDismiss,
            )
            Text(
                text = stringResource(R.string.account_netease_phone_title),
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.action_cancel),
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
                                // 自动切到「扫码登录」tab：登录页默认是手机号登录，需手动点
                                // 一下才出二维码。SPA 的 tab 由前端 JS 挂载，onPageFinished 时
                                // 未必已渲染，脚本内部 setInterval 轮询重试（约 12s 上限）；
                                // 命中即 click()，找不到则退化为手动点（行为不变，不更差）。
                                view?.evaluateJavascript(NETEASE_AUTO_CLICK_QR_TAB_JS, null)
                                super.onPageFinished(view, url)
                            }
                        }
                        loadUrl(NETEASE_LOGIN_URL)
                    }
                },
            )

            if (verifying) {
                // ⚠ 这层浮在 WebView（网易云登录页，本身是浅色）上面。原先无论明暗主题
                // 都是 0.18 黑 + 白字 ⇒ 浅色主题下白字压在浅灰上几乎看不见。
                // 按主题把遮罩给足对比度（2026-10-23 深色模式可读性排查）。
                val dark = isMeloXDarkTheme()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (dark) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.72f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.account_verifying_login),
                            modifier = Modifier.padding(top = 12.dp),
                            color = if (dark) Color.White else Color.Black,
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

/** 网易云网页登录页地址（含 hash 路由，SPA 首页默认渲染首页而非登录框）。 */
internal const val NETEASE_LOGIN_URL = "https://music.163.com/#/login"

/**
 * 登录页加载完成后自动点完「顶部登录」→「扫码登录」两步的注入脚本。
 *
 * 网易云 #/login 默认只渲染首页：必须先点顶部「登录」（真实可点元素是 a.link，
 * 外层 div.m-tophead 只是包装层，click 它无效）打开弹窗，再点弹窗内「扫码登录」tab。
 * SPA 由前端 JS 挂载，onPageFinished 时未必已渲染，故 setInterval 轮询重试。
 *
 * ⚠ 这条路径只用于「网页登录」兜底页。独立扫码页（[NeteaseQrLoginScreen]）已改为
 * 直接调网页版 `/weapi/w/user/login/qr/` 系列接口拿图，不再需要跟 SPA 渲染时序赛跑。
 */
internal const val NETEASE_AUTO_CLICK_QR_TAB_JS = """
(function(){
  if (window.__meloxQrInit) return;
  window.__meloxQrInit = true;
  function textOf(n){ return (n.textContent || n.innerText || '').trim(); }
  function log(m){ try { (window.__meloxQrLog = window.__meloxQrLog || []).push(m); } catch(e){} }
  function safeClick(el){
    log('click:' + el.tagName);
    try { el.click(); } catch(e){ log('clickErr'); }
  }
  function findScanTab(){
    var nodes = document.querySelectorAll('a,button,li,div,span,em,i,label');
    for (var i = 0; i < nodes.length; i++){
      var t = textOf(nodes[i]);
      if (t && t.length <= 12 && (t.indexOf('扫码登录') >= 0 || t.indexOf('二维码登录') >= 0)) return nodes[i];
    }
    return null;
  }
  function findTopLoginButton(){
    var nodes = document.querySelectorAll('a.link, a[class*=link]');
    for (var i = 0; i < nodes.length; i++){
      if (textOf(nodes[i]) === '登录') return nodes[i];
    }
    var heads = document.querySelectorAll('div.m-tophead, div[class*=tophead]');
    for (var j = 0; j < heads.length; j++){
      var inner = heads[j].querySelector('a');
      if (inner && textOf(inner) === '登录') return inner;
      if (textOf(heads[j]) === '登录') return heads[j];
    }
    return null;
  }
  var tries = 0;
  var timer = setInterval(function(){
    try {
      tries++;
      if (window.__meloxQrClicked || tries > 30){
        clearInterval(timer);
        window.__meloxQrInit = false;
        log('stop');
        return;
      }
      var scan = findScanTab();
      if (scan){
        safeClick(scan);
        window.__meloxQrClicked = true;
        clearInterval(timer);
        return;
      }
      var login = findTopLoginButton();
      if (login){
        if (!login.__meloxTried){ login.__meloxTried = true; safeClick(login); log('openBox'); }
        else log('boxTried');
      } else log('noBtn');
    } catch(e){ log('tickErr'); }
  }, 400);
})();
"""

/** 汇总网易云网页 cookie（网页登录页使用）。 */
internal fun collectNeteaseCookieHeader(): String {
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
