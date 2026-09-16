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

/**
 * Square-compatible Google WebView login. MeloX stores only the resulting session context.
 *
 * Google hands out no token for YouTube Music, so the only session it recognises is the
 * one its own web player uses: a Google cookie plus the `VISITOR_DATA` and `DATASYNC_ID`
 * the page keeps in `window.yt.config_`. The web view loads Google's real sign-in page and
 * MeloX never sees the credentials, only the cookie that results.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginScreen(
    onDismiss: () -> Unit,
    onLoggedIn: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var completing by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var visitorData by remember { mutableStateOf("") }
    var dataSyncId by remember { mutableStateOf("") }

    fun complete() {
        if (completing) return
        val cookie = CookieManager.getInstance().getCookie(MUSIC_URL).orEmpty()
        // Backing out before signing in is an ordinary outcome, not a failure.
        if (cookie.isBlank()) {
            onDismiss()
            return
        }
        completing = true
        val capturedVisitorData = visitorData
        val capturedDataSyncId = dataSyncId
        scope.launch {
            YouTube.cookie = cookie
            YouTube.visitorData = capturedVisitorData.takeIf(String::isNotBlank)
            YouTube.dataSyncId = capturedDataSyncId.takeIf(String::isNotBlank)
            // The session is only worth keeping if it can actually read the account.
            YouTube.accountInfo()
                .onSuccess { info ->
                    webView?.apply {
                        stopLoading()
                        clearHistory()
                    }
                    YouTubeSessionStore.write(
                        context,
                        YouTubeSession(
                            cookie = cookie,
                            visitorData = capturedVisitorData,
                            dataSyncId = capturedDataSyncId,
                            accountName = info.name,
                        ),
                    )
                    onLoggedIn()
                }
                .onFailure {
                    completing = false
                    onDismiss()
                }
        }
    }

    BackHandler {
        val view = webView
        // Google's flow is several pages deep, so back should walk it rather than
        // abandoning a sign-in halfway through.
        if (view?.canGoBack() == true) view.goBack() else complete()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            view.loadUrl(
                                "javascript:Square.visitorData(window.yt.config_ && window.yt.config_.VISITOR_DATA)",
                            )
                            view.loadUrl(
                                "javascript:Square.dataSyncId(window.yt.config_ && window.yt.config_.DATASYNC_ID)",
                            )
                            // Landing back on music.youtube.com with a cookie is what
                            // "signed in" looks like; the flow can be any number of
                            // pages before this one.
                            val signedIn = url?.contains("music.youtube.com") == true &&
                                CookieManager.getInstance().getCookie(MUSIC_URL).orEmpty().isNotBlank()
                            if (signedIn && !completing) complete()
                        }
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun visitorData(value: String?) {
                                if (!value.isNullOrBlank()) visitorData = value
                            }

                            @JavascriptInterface
                            fun dataSyncId(value: String?) {
                                // Two ids separated by `||`; the first is this account's.
                                if (!value.isNullOrBlank()) dataSyncId = value.substringBefore("||")
                            }
                        },
                        "Square",
                    )
                    webView = this
                    loadUrl(SIGN_IN_URL)
                }
            },
        )
    }
}

private const val MUSIC_URL = "https://music.youtube.com"
private const val SIGN_IN_URL =
    "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
