/*
 * Sonify status/navigation bar helpers (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/widgets/basic/RawWindowInsets.kt (master @
 * 209e1aa). Sonify replaces accompanist insets because HyperOS can report 0
 * through the Compose window-insets path. MeloX already relies on the Compose
 * WindowInsets path across the app and has been stable on it, so this port
 * uses the same foundation APIs while keeping Sonify's public helper names and
 * non-consuming padding semantics. This is the MeloX-native equivalent called
 * out by the migration brief.
 */
package com.lladlam.melox.ui.sonify

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lladlam.melox.ui.layout.rememberMeloXStableStatusBarHeight

/** Stable status-bar top inset in pixels. */
@Composable
fun rawStatusBarsTopPx(): Int {
    val dp = rememberMeloXStableStatusBarHeight()
    return with(LocalDensity.current) { dp.roundToPx() }
}

@Composable
fun rawStatusBarsTopDp(): Dp = rememberMeloXStableStatusBarHeight()

@Composable
fun rawNavigationBarsBottomPx(): Int {
    val dp = rawNavigationBarsBottomDp()
    return with(LocalDensity.current) { dp.roundToPx() }
}

/**
 * 20dp baseline: hiding the gesture pill must not move the Sonify bottom
 * chrome, matching the upstream Sonify behaviour.
 */
@Composable
fun rawNavigationBarsBottomDp(): Dp {
    val raw = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return maxOf(raw, 20.dp)
}

/** Non-consuming status-bar padding, matching Sonify's helper semantics. */
@Composable
fun Modifier.sonifyStatusBarsPadding(): Modifier {
    val top = rememberMeloXStableStatusBarHeight()
    return if (top > 0.dp) padding(top = top) else this
}

/** Non-consuming navigation-bar padding with Sonify's 20dp baseline. */
@Composable
fun Modifier.sonifyNavigationBarsPadding(): Modifier =
    padding(bottom = rawNavigationBarsBottomDp())

/** Height = status-bar inset + [additional]. */
@Composable
fun Modifier.sonifyStatusBarsHeight(additional: Dp = 0.dp): Modifier =
    height(rememberMeloXStableStatusBarHeight() + additional)

/** Height = navigation-bar inset + [additional]. */
@Composable
fun Modifier.sonifyNavigationBarsHeight(additional: Dp = 0.dp): Modifier =
    height(rawNavigationBarsBottomDp() + additional)

// Compatibility names used by the imported page source.
@Composable
fun Modifier.statusBarsPadding(): Modifier = sonifyStatusBarsPadding()

@Composable
fun Modifier.navigationBarsPadding(): Modifier = sonifyNavigationBarsPadding()

@Composable
fun Modifier.statusBarsHeight(additional: Dp = 0.dp): Modifier = sonifyStatusBarsHeight(additional)

@Composable
fun Modifier.navigationBarsHeight(additional: Dp = 0.dp): Modifier = sonifyNavigationBarsHeight(additional)

/** Kept for parity with the upstream file; MeloX does not need a provider. */
@Composable
fun ProvideSonifyWindowInsets(content: @Composable () -> Unit) = content()
