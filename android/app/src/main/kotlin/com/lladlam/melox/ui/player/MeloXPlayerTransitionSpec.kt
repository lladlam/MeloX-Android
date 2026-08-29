package com.lladlam.melox.ui.player

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime

// SharedTransition defaults to a spring bounds transform. For an interactive
// seekable transition that makes different children advance by different
// apparent amounts. Use one child timeline and animate only the master
// SeekableTransitionState fraction when the gesture is released.
internal const val MeloXPlayerTransitionDurationMillis = 360

internal val meloXPlayerTransitionDurationMillis: Int
    get() = MeloXSettingsRuntime.playerTransitionDurationMs

internal val MeloXArtworkBoundsTransform = BoundsTransform { _, _ ->
    tween(
        durationMillis = 300,
        easing = FastOutSlowInEasing,
    )
}

internal val MeloXPlayerShellBoundsTransform = BoundsTransform { _, _ ->
    tween(
        durationMillis = meloXPlayerTransitionDurationMillis,
        easing = FastOutSlowInEasing,
    )
}

internal fun meloXPlayerLinearFloatSpec() = tween<Float>(
    durationMillis = meloXPlayerTransitionDurationMillis,
    easing = FastOutSlowInEasing,
)
