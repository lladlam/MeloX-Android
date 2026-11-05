package com.lladlam.melox.ui.account

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import com.lladlam.melox.BuildConfig
import com.lladlam.melox.R
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.lladlam.melox.core.provider.youtubemusic.hasYouTubeAuthCookie
import com.lladlam.melox.core.provider.youtubemusic.isYouTubeMusicPage
import com.lladlam.melox.core.provider.youtubemusic.mergeYouTubeCookies
import com.lladlam.melox.core.provider.youtubemusic.normalizeYouTubeContextToken
import com.lladlam.melox.core.provider.youtubemusic.YouTubeSession
import com.lladlam.melox.core.provider.youtubemusic.YouTubeSessionStore
import com.metrolist.innertube.YouTube
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray

/**
 * Square-compatible Google WebView login. MeloX stores only the resulting session context.
 *
 * Google hands out no token for YouTube Music, so the only session it recognises is the
 * one its own web player uses: a Google cookie plus the `VISITOR_DATA` and `DATASYNC_ID`
 * the page keeps in `window.yt.config_`. The web view loads Google's real sign-in page and
 * MeloX never sees the credentials, only the cookie that results.
 *
 * `SAPISID` is written for `youtube.com`, not always for `music.youtube.com`, and it can
 * land a moment after the page finishes. Reading a single host the instant the page loads
 * is how a finished login comes back with no cookie.
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
    var loginError by remember { mutableStateOf<String?>(null) }

    fun complete(abandonIfMissing: Boolean) {
        if (completing) return
        val view = webView
        if (view == null || !isYouTubeMusicPage(runCatching { view.url }.getOrNull())) {
            if (abandonIfMissing) onDismiss()
            return
        }
        completing = true
        scope.launch {
          try {
            // evaluateJavascript is asynchronous: await this page's config before
            // snapshotting the cookie/session. Never reuse a previous page's ids.
            val pair = suspendCancellableCoroutine<Triple<String, String, String>?> { continuation ->
                view.evaluateJavascript(CONFIG_SCRIPT) { value ->
                    if (continuation.isActive) continuation.resume(decodeJsPair(value))
                }
            }
            if (!isYouTubeMusicPage(runCatching { view.url }.getOrNull())) return@launch
            val cookie = youtubeSessionCookie()
            if (!hasYouTubeAuthCookie(cookie)) {
                if (abandonIfMissing) onDismiss()
                return@launch
            }
            visitorData = normalizeYouTubeContextToken(pair?.first)
            dataSyncId = normalizeYouTubeContextToken(pair?.second).substringBefore("||")
            YouTube.cookie = cookie
            YouTube.visitorData = visitorData.takeIf(String::isNotBlank)
            YouTube.dataSyncId = dataSyncId.takeIf(String::isNotBlank)
            YouTube.pageId = null
            val authUser = pair?.third?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: "0"
            YouTube.authUser = authUser
            if (visitorData.isBlank()) {
                YouTube.visitorData().onSuccess { fresh ->
                    visitorData = fresh
                    YouTube.visitorData = fresh
                }
            }
            val resolvedVisitorData = visitorData
            val resolvedDataSyncId = dataSyncId
            // Square verifies the captured context before persisting it. A failed
            // check must remain visible, rather than becoming a broken library.
            val name = YouTube.accountInfo().getOrThrow().name
            webView?.apply {
                stopLoading()
                clearHistory()
            }
            YouTubeSessionStore.write(
                context,
                YouTubeSession(
                    cookie = cookie,
                    visitorData = resolvedVisitorData,
                    dataSyncId = resolvedDataSyncId,
                    accountName = name,
                    authUser = authUser,
                ),
            )
            onLoggedIn()
          } catch (error: CancellationException) {
            throw error
          } catch (error: Exception) {
            loginError = context.getString(R.string.youtube_login_validation_failed, error.message.orEmpty().take(500))
            YouTubeSessionStore.apply(context, force = true)
          } finally {
            completing = false
          }
        }
    }

    LaunchedEffect(webView) {
        val view = webView ?: return@LaunchedEffect
        while (isActive) {
            val currentUrl = runCatching { view.url }.getOrNull()
            if (!completing && loginError == null && isYouTubeMusicPage(currentUrl) && hasYouTubeAuthCookie(youtubeSessionCookie())) {
                complete(abandonIfMissing = false)
            }
            delay(600)
        }
    }

    BackHandler {
        val view = webView
        // Google's flow is several pages deep, so back should walk it rather than
        // abandoning a sign-in halfway through.
        val canGoBack = runCatching { view?.canGoBack() == true }.getOrDefault(false)
        if (canGoBack) view?.goBack() else complete(abandonIfMissing = true)
    }

    Column(Modifier.fillMaxSize()) {
        loginError?.let { message ->
            Text(message, Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.error)
            MeloXGlassButton(
                onClick = { webView?.reload() },
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MeloXGlassButtonStyle.BorderedProminent,
            ) {
                Text(stringResource(R.string.youtube_login_retry))
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { viewContext ->
                if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
                WebView(viewContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                            loginError = null
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            if (isYouTubeMusicPage(url)) {
                                loginError = null
                                complete(abandonIfMissing = false)
                            }
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (request.isForMainFrame) {
                                loginError = view.context.getString(
                                    R.string.youtube_login_network_error,
                                    error.errorCode,
                                    error.description?.toString().orEmpty(),
                                )
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest,
                            errorResponse: WebResourceResponse,
                        ) {
                            if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                                loginError = view.context.getString(
                                    R.string.youtube_login_http_error,
                                    errorResponse.statusCode,
                                )
                            }
                        }
                    }
                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(this, true)
                    webView = this
                    loadUrl(SIGN_IN_URL)
                }
            },
            // Destroy exactly when Compose releases this view. The previous
            // DisposableEffect(webView) could destroy a still-attached WebView while
            // the Google page was loading, taking the shared renderer process down
            // with it (blank white page).
            onRelease = { released ->
                if (webView === released) webView = null
                released.stopLoading()
                released.loadUrl("about:blank")
                released.destroy()
            },
        )
    }
}

/**
 * Google writes the session across several hosts. `getCookie("music.youtube.com")`
 * often returns the anonymous visitor cookie and omits `SAPISID`, which lives on
 * `youtube.com`. Prefer cookies scoped to the music host when keys overlap.
 */
internal fun youtubeSessionCookie(): String {
    val manager = CookieManager.getInstance()
    manager.flush()
    return mergeYouTubeCookies(COOKIE_URLS.map { manager.getCookie(it).orEmpty() })
}

private fun decodeJsPair(value: String?): Triple<String, String, String>? {
    val raw = value?.takeIf { it.isNotBlank() && it != "null" } ?: return null
    val decoded = runCatching { JSONArray("[$raw]").getString(0) }.getOrElse { raw.trim('"') }
    if (decoded.isBlank() || decoded == "null") return null
    val parts = decoded.split('\u001f', limit = 3)
    return Triple(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" }, parts.getOrElse(2) { "0" })
}

private val COOKIE_URLS = listOf(
    "https://music.youtube.com",
    "https://www.youtube.com",
    "https://youtube.com",
)

private const val CONFIG_SCRIPT =
    "(function(){var c=window.yt&&window.yt.config_;if(!c)return '';return (c.VISITOR_DATA||'')+'\\u001f'+(c.DATASYNC_ID||'')+'\\u001f'+(c.SESSION_INDEX||'0');})()"
private const val SIGN_IN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"
