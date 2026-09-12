package com.lladlam.melox.ui.account

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.metrolist.innertube.YouTube
import com.lladlam.melox.core.provider.youtubemusic.YouTubeSession
import com.lladlam.melox.core.provider.youtubemusic.YouTubeSessionStore
import kotlinx.coroutines.launch

/** Square-compatible Google WebView login. MeloX stores only the resulting session context. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginScreen(
    onDismiss: () -> Unit,
    onLoggedIn: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var completing by remember { mutableStateOf(false) }
    var visitorData by remember { mutableStateOf("") }
    var dataSyncId by remember { mutableStateOf("") }

    fun complete() {
        if (completing) return
        val cookie = CookieManager.getInstance().getCookie(MUSIC_URL).orEmpty()
        if (cookie.isBlank()) {
            onDismiss()
            return
        }
        completing = true
        scope.launch {
            YouTube.cookie = cookie
            YouTube.visitorData = visitorData
            YouTube.dataSyncId = dataSyncId
            YouTube.accountInfo().onSuccess { info ->
                YouTubeSessionStore.write(
                    context,
                    YouTubeSession(
                        cookie = cookie,
                        visitorData = visitorData,
                        dataSyncId = dataSyncId,
                        accountName = info.name,
                    ),
                )
                webView?.stopLoading()
                onLoggedIn()
            }.onFailure {
                completing = false
                onDismiss()
            }
        }
    }

    BackHandler {
        if (webView?.canGoBack() == true) webView?.goBack() else complete()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            view.loadUrl("javascript:Square.visitorData(window.yt.config_.VISITOR_DATA)")
                            view.loadUrl("javascript:Square.dataSyncId(window.yt.config_.DATASYNC_ID)")
                            if (url?.contains("music.youtube.com") == true &&
                                CookieManager.getInstance().getCookie(MUSIC_URL).orEmpty().isNotBlank() &&
                                !completing
                            ) complete()
                        }
                    }
                    addJavascriptInterface(object {
                        @JavascriptInterface fun visitorData(value: String?) { visitorData = value.orEmpty() }
                        @JavascriptInterface fun dataSyncId(value: String?) { dataSyncId = value.orEmpty().substringBefore("||") }
                    }, "Square")
                    webView = this
                    loadUrl(SIGN_IN_URL)
                }
            },
        )
    }
}

private const val MUSIC_URL = "https://music.youtube.com"
private const val SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
