package com.lladlam.melox.ui.player

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

internal const val MeloXPlayerTransitionDurationMillis = 300

internal val MeloXPlayerLinearBoundsTransform = BoundsTransform { _, _ ->
    tween(
        durationMillis = MeloXPlayerTransitionDurationMillis,
        easing = LinearOutSlowInEasing,
    )
}

internal fun playerAutomaticFractionSpec() = tween<Float>(
    durationMillis = MeloXPlayerTransitionDurationMillis,
    easing = LinearOutSlowInEasing,
)

internal fun playerGestureSettleSpec() = spring<Float>(
    dampingRatio = 1.0f,
    stiffness = 200f,
    visibilityThreshold = 0.001f,
)
