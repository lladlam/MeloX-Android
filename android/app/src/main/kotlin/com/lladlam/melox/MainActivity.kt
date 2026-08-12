package com.lladlam.melox

import android.content.Intent
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lladlam.melox.ui.MeloXApp
import com.lladlam.melox.ui.theme.MeloXTheme
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences

class MainActivity : ComponentActivity() {
    private var openNowPlayingRequest by mutableIntStateOf(0)
    private var clipboardLinkRequest by mutableStateOf<String?>(null)
    private var lastClipboardText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumePlaybackIntent(intent)
        MeloXSettingsPreferences.initialize(this)

        setContent {
            MeloXTheme {
                MeloXApp(
                    openNowPlayingRequest = openNowPlayingRequest,
                    clipboardLinkRequest = clipboardLinkRequest,
                    onClipboardLinkConsumed = { clipboardLinkRequest = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumePlaybackIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (!com.lladlam.melox.ui.settings.MeloXSettingsRuntime.clipboardLinksEnabled) return
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = manager.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        if (text.isNotBlank() && text != lastClipboardText) {
            lastClipboardText = text
            clipboardLinkRequest = text
        }
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
