package com.lladlam.melox.ui.player

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween

// SharedTransition defaults to a spring bounds transform. For an interactive
// seekable transition that makes different children advance by different
// apparent amounts. Use one linear child timeline and animate only the master
// SeekableTransitionState fraction when the gesture is released.
internal const val MeloXPlayerTransitionDurationMillis = 1000

internal val MeloXPlayerLinearBoundsTransform = BoundsTransform { _, _ ->
    tween(
        durationMillis = MeloXPlayerTransitionDurationMillis,
        easing = LinearEasing,
    )
}

internal fun meloXPlayerLinearFloatSpec() = tween<Float>(
    durationMillis = MeloXPlayerTransitionDurationMillis,
    easing = LinearEasing,
)
