package com.lladlam.melox.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Hides the system status bar while [enabled] is true and restores it on dispose.
 *
 * This is used for the "沉浸式播放" setting: when the full-screen player is open,
 * the top status bar is automatically hidden.
 */
@Composable
fun MeloXImmersivePlaybackEffect(enabled: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(enabled) {
        val activity = context.findActivity() ?: return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val type = WindowInsetsCompat.Type.statusBars()

        if (enabled) {
            controller.hide(type)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(type)
        }

        onDispose {
            controller.show(type)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
