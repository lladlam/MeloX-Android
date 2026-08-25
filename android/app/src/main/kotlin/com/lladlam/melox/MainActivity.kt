package com.lladlam.melox

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lladlam.melox.core.network.parseNeteaseListenTogetherInvitation
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigRuntime
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigConsent
import com.lladlam.melox.platform.lyricon.MeloXLyriconBridge
import com.lladlam.melox.platform.xiaomi.HyperOsFocusBridge
import com.lladlam.melox.ui.player.MeloXListenTogetherInviteActivity
import com.lladlam.melox.ui.MeloXApp
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import com.lladlam.melox.ui.theme.MeloXTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var openNowPlayingRequest by mutableIntStateOf(0)
    private var clipboardLinkRequest by mutableStateOf<String?>(null)
    private var playbackConnectionEnabled by mutableStateOf(false)
    private var lastClipboardText: String? = null
    private val firstDraw = CompletableDeferred<Unit>()
    private var clipboardScanJob: Job? = null
    private var handledRemoteConfigForegroundSession = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumePlaybackIntent(intent)
        MeloXSettingsPreferences.initializeCritical(this)
        completeAfterFirstDraw()

        setContent {
            MeloXTheme {
                MeloXApp(
                    openNowPlayingRequest = openNowPlayingRequest,
                    clipboardLinkRequest = clipboardLinkRequest,
                    onClipboardLinkConsumed = { clipboardLinkRequest = null },
                    playbackConnectionEnabled = playbackConnectionEnabled,
                )
            }
        }

        lifecycleScope.launch {
            firstDraw.await()
            delay(100L)
            playbackConnectionEnabled = true
        }

        lifecycleScope.launch {
            firstDraw.await()
            delay(250L)
            MeloXSettingsPreferences.initialize(this@MainActivity)
        }

        // Lyricon registers a cross-process provider. Starting it before
        // setContent delayed the first frame and left a white window on cold
        // launch. Listen Together is intentionally not started here: its UI or
        // an incoming invitation creates the coordinator only when needed.
        lifecycleScope.launch {
            firstDraw.await()
            delay(500L)
            MeloXLyriconBridge.start(applicationContext)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumePlaybackIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val foregroundSession = MeloXAppVisibility.foregroundSessionId
        if (foregroundSession != handledRemoteConfigForegroundSession) {
            handledRemoteConfigForegroundSession = foregroundSession
            lifecycleScope.launch {
                firstDraw.await()
                delay(750L)
                if (MeloXRemoteConfigConsent.enabled(applicationContext)) {
                    MeloXRemoteConfigRuntime.initializeAndRefresh(
                        applicationContext,
                        BuildConfig.VERSION_CODE,
                        force = true,
                    )
                }
            }
        }
        // Shizuku is optional. On HyperOS 3, request permission only when its
        // service is actually running; permission itself acts as the user's opt-in
        // to the short XMSF compatibility pulse used by some restricted ROM builds.
        lifecycleScope.launch {
            firstDraw.await()
            delay(500L)
            HyperOsFocusBridge.prepareShizukuCompatibility(this@MainActivity)
        }

        clipboardScanJob?.cancel()
        clipboardScanJob = lifecycleScope.launch {
            firstDraw.await()
            delay(100L)
            if (!com.lladlam.melox.ui.settings.MeloXSettingsRuntime.clipboardLinksEnabled) return@launch
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = manager.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
            if (text.isNotBlank() && text != lastClipboardText) {
                lastClipboardText = text
                val together = parseNeteaseListenTogetherInvitation(text)
                if (together != null) {
                    MeloXListenTogetherInviteActivity.launch(
                        this@MainActivity,
                        together.roomId,
                        together.inviterId,
                    )
                    return@launch
                }
                clipboardLinkRequest = text
            }
        }
    }

    private fun completeAfterFirstDraw() {
        val decorView = window.decorView
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (decorView.viewTreeObserver.isAlive) {
                    decorView.viewTreeObserver.removeOnPreDrawListener(this)
                }
                decorView.post { firstDraw.complete(Unit) }
                return true
            }
        }
        decorView.viewTreeObserver.addOnPreDrawListener(listener)
    }

    private fun consumePlaybackIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_NOW_PLAYING) {
            openNowPlayingRequest += 1
        }
    }

    companion object {
        const val ACTION_OPEN_NOW_PLAYING =
            "com.lladlam.melox.action.OPEN_NOW_PLAYING"
    }
}
