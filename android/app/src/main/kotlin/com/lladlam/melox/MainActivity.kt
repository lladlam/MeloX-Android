package com.lladlam.melox

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lladlam.melox.core.network.parseNeteaseListenTogetherInvitation
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigRuntime
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigConsent
import com.lladlam.melox.platform.MeloXVolumeKeyBridge
import com.lladlam.melox.platform.lyricon.MeloXLyriconBridge
import com.lladlam.melox.platform.xiaomi.HyperOsFocusBridge
import com.lladlam.melox.ui.player.MeloXListenTogetherInviteActivity
import com.lladlam.melox.ui.player.MeloXFloatingLyricsPip
import com.lladlam.melox.ui.player.MeloXFloatingLyricsPipScreen
import com.lladlam.melox.ui.MeloXApp
import com.lladlam.melox.ui.layout.MeloXDetailPageOwner
import com.lladlam.melox.ui.layout.MeloXDetailPaneState
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.theme.MeloXTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), MeloXDetailPageOwner {
    override val detailPaneState = MeloXDetailPaneState()
    private var openNowPlayingRequest by mutableIntStateOf(0)
    private var clipboardLinkRequest by mutableStateOf<String?>(null)
    private var playbackConnectionEnabled by mutableStateOf(false)
    private var lastClipboardText: String? = null
    private val firstDraw = CompletableDeferred<Unit>()
    private var clipboardScanJob: Job? = null
    private var handledRemoteConfigForegroundSession = -1L
    private val pipModeState = mutableStateOf(false)

    /** Installs the service → PiP bridge used by the notification's floating-lyrics action. */
    private fun requestFloatingLyricsPip(service: com.lladlam.melox.playback.MeloXPlaybackService): Boolean {
        if (isInPictureInPictureMode) {
            moveTaskToBack(true)
            return true
        }
        return runCatching {
            enterPictureInPictureMode(
                MeloXFloatingLyricsPip.params(this, service.isPlaybackActive()),
            )
        }.isSuccess
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (MeloXSettingsRuntime.floatingLyricsEnabled && !isInPictureInPictureMode) {
            runCatching {
                enterPictureInPictureMode(
                    MeloXFloatingLyricsPip.params(this, false),
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipModeState.value = isInPictureInPictureMode
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        savedInstanceState?.getParcelableArrayList<Intent>("melox_detail_pages")?.let(detailPaneState::restorePages)
        val coldStart = savedInstanceState == null
        val returnFromBackground = coldStart &&
            System.currentTimeMillis() - MeloXAppVisibility.lastBackgroundedAtMs(this) <= RETURN_FROM_BACKGROUND_WINDOW_MS
        enableEdgeToEdge()
        consumePlaybackIntent(intent)
        MeloXSettingsPreferences.initializeCritical(this)
        completeAfterFirstDraw()
        MeloXFloatingLyricsPip.install { service -> requestFloatingLyricsPip(service) }

        setContent {
            MeloXTheme {
                val mainPageState = rememberSaveableStateHolder()
                var splashVisible by remember { mutableStateOf(coldStart && !returnFromBackground && !pipModeState.value) }
                val pageAlpha by animateFloatAsState(
                    targetValue = if (splashVisible) 0f else 1f,
                    animationSpec = tween(350),
                    label = "melox-start-page",
                )
                val splashAlpha by animateFloatAsState(
                    targetValue = if (splashVisible) 1f else 0f,
                    animationSpec = tween(350),
                    label = "melox-start-icon",
                )
                LaunchedEffect(coldStart, returnFromBackground, pipModeState.value) {
                    if (coldStart && !returnFromBackground && !pipModeState.value) delay(1_000L)
                    splashVisible = false
                }
                Box(Modifier.fillMaxSize()) {
                    if (pipModeState.value) {
                        MeloXFloatingLyricsPipScreen(Modifier.fillMaxSize())
                    } else {
                     Box(Modifier.fillMaxSize().alpha(pageAlpha)) {
                        mainPageState.SaveableStateProvider("main") {
                         MeloXApp(
                            openNowPlayingRequest = openNowPlayingRequest,
                            clipboardLinkRequest = clipboardLinkRequest,
                            onClipboardLinkConsumed = { clipboardLinkRequest = null },
                            playbackConnectionEnabled = playbackConnectionEnabled,
                         )
                        }
                    }
                    if (splashAlpha > 0f) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(104.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .alpha(splashAlpha),
                        )
                    }
                    }
                }
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
            runCatching { MeloXLyriconBridge.start(applicationContext) }
                .onFailure { android.util.Log.w("MeloXStartup", "Skipping optional Lyricon initialization", it) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelableArrayList("melox_detail_pages", detailPaneState.savePages())
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumePlaybackIntent(intent)
    }

    /**
     * 实体音量键**只观察、不消费**（2026-10-07）。
     *
     * vivo 上硬件音量键不发 `VOLUME_CHANGED_ACTION`（本仓库 2026-10-06 实测），
     * 播放页只能靠 `MeloXPlayerUi` 里那条 150ms 轮询得知音量变了 ⇒ 按键后条最多滞后 150ms
     * 才起步，观感就是"顿一下才滑"。这里把按键信号同步推给 UI，把延迟压到 ≈1 帧。
     *
     * ⚠ 一律 `return super.onKeyDown(...)`（**绝不 return true**）：
     *   音量键的真正处理在 system_server 的 `interceptKeyBeforeQueueing`，早于事件投递给 App
     *   ⇒ 我们在这里读到的已是**调完之后**的音量；一旦消费掉事件，反而会打断系统的音量 HUD。
     *   而且这件事**必须在音量调到边界后也有信号**（越界按键在系统侧音量值不变），
     *   只能靠按键事件本身来识别 —— 这也是"过冲拉长"能做到的前提。
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        //⚠ `repeatCount` 必须透传：长按时系统会自动重复发 onKeyDown（repeatCount 递增），
        //   而物理上只有**一次**手指按下。UI 侧靠它区分"新手势"与"同一手势的重复"，
        //   否则长按到极限会反复累加蓄力 ⇒ 条二次拉伸（iOS 不会，见 Bridge 注释）。
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP ->
                MeloXVolumeKeyBridge.onVolumeKeyDown(MeloXVolumeKeyBridge.DIRECTION_UP, event.repeatCount)
            KeyEvent.KEYCODE_VOLUME_DOWN ->
                MeloXVolumeKeyBridge.onVolumeKeyDown(MeloXVolumeKeyBridge.DIRECTION_DOWN, event.repeatCount)
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * 松开的信号专供"过冲拉长"回弹（iOS 的橡皮筋是抬手即回，不靠超时猜）。
     * 同样只观察、不消费。
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> MeloXVolumeKeyBridge.onVolumeKeyUp(MeloXVolumeKeyBridge.DIRECTION_UP)
            KeyEvent.KEYCODE_VOLUME_DOWN -> MeloXVolumeKeyBridge.onVolumeKeyUp(MeloXVolumeKeyBridge.DIRECTION_DOWN)
        }
        return super.onKeyUp(keyCode, event)
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
        // Optional compatibility must not prevent startup, including when the ROM
        // cannot link a vendor SDK class. The bridge also checks the feature switch.
        lifecycleScope.launch {
            firstDraw.await()
            delay(500L)
            if (!com.lladlam.melox.ui.settings.MeloXSettingsPreferences.boolean(
                    applicationContext, "hyperos_super_island_enabled", false,
                )) return@launch
            runCatching { HyperOsFocusBridge.prepareShizukuCompatibility(this@MainActivity) }
                .onFailure { android.util.Log.w("MeloXStartup", "Skipping optional Super Island initialization", it) }
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

    override fun onPause() {
        super.onPause()
    }

    private fun completeAfterFirstDraw() {
        val decorView = window.decorView
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (decorView.viewTreeObserver.isAlive) {
                    decorView.viewTreeObserver.removeOnPreDrawListener(this)
                }
                decorView.post {
                    firstDraw.complete(Unit)
                }
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
        /** Cold starts within this window of the last backgrounding are treated as returns, not fresh launches. */
        private const val RETURN_FROM_BACKGROUND_WINDOW_MS = 15L * 60L * 1000L
    }
}
