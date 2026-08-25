package com.lladlam.melox.ui.account

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lladlam.melox.core.provider.bilibili.BilibiliProvider
import com.lladlam.melox.core.provider.bilibili.BilibiliSessionStore
import com.lladlam.melox.ui.legal.MeloXLegalLinks
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BilibiliLoginScreen(onDismiss: () -> Unit, onLoggedIn: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    BackHandler { webView?.takeIf(WebView::canGoBack)?.goBack() ?: onDismiss() }

    LaunchedEffect(webView) {
        if (webView == null) return@LaunchedEffect
        while (true) {
            CookieManager.getInstance().flush()
            val cookie = collectBilibiliCookies()
            if (BilibiliSessionStore.hasRequiredCookies(cookie)) {
                BilibiliSessionStore.write(context, cookie)
                onLoggedIn()
                return@LaunchedEffect
            }
            delay(600)
        }
    }
    DisposableEffect(Unit) { onDispose { webView?.stopLoading(); webView?.destroy() } }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(18.dp, 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("取消", Modifier.clickable(onClick = onDismiss).padding(8.dp), color = MaterialTheme.colorScheme.primary)
            Text("登录 Bilibili", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(48.dp))
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(Modifier.weight(1f)) {
            AndroidView(factory = { viewContext ->
                WebView(viewContext).apply {
                    webView = this
                    CookieManager.getInstance().let { manager ->
                        manager.setAcceptCookie(true)
                        manager.setAcceptThirdPartyCookies(this, true)
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = BilibiliProvider.UserAgent
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) { loading = false }
                    }
                    loadUrl("https://passport.bilibili.com/login")
                }
            }, modifier = Modifier.fillMaxSize())
        }
        MeloXLegalLinks(
            modifier = Modifier.padding(vertical = 6.dp),
            tint = androidx.compose.ui.graphics.Color(0xFFFB7299),
        )
    }
}

private fun collectBilibiliCookies(): String {
    val manager = CookieManager.getInstance()
    val values = linkedMapOf<String, String>()
    listOf("https://www.bilibili.com/", "https://passport.bilibili.com/", "https://api.bilibili.com/", "https://bilibili.com/").forEach { url ->
        manager.getCookie(url)?.split(';')?.forEach { item ->
            val parts = item.trim().split('=', limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank()) values[parts[0]] = parts[1]
        }
    }
    return values.toSortedMap().entries.joinToString("; ") { (key, value) -> "$key=$value" }
}
