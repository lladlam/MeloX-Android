package com.lladlam.melox.ui.player

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime

/**
 * Player-area status-bar padding that keeps content in the same position when the
 * status bar is hidden via the "沉浸式播放" setting.
 *
 * Normal [statusBarsPadding] collapses when the system status bar is hidden, causing
 * the cover and song info to jump up. This helper uses the *stable* (ignoring visibility)
 * status-bar height while immersive playback is enabled, so the layout reserves the same
 * top space regardless of whether the status bar is currently visible.
 */
fun Modifier.meloXPlayerStatusBarsPadding(): Modifier = composed {
    if (!MeloXSettingsRuntime.immersivePlaybackEnabled) {
        return@composed statusBarsPadding()
    }

    val view = LocalView.current
    val density = LocalDensity.current
    var stableTopPx by remember { mutableIntStateOf(0) }

    DisposableEffect(view) {
        ViewCompat.getRootWindowInsets(view)?.let { insets ->
            stableTopPx = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            stableTopPx = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top
            insets
        }
        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
        }
    }

    padding(top = with(density) { stableTopPx.toDp() })
}
