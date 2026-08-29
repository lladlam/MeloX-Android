package com.lladlam.melox.ui.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

internal object MeloXMotion {
    const val PageEnterMillis = 280
    const val PageExitMillis = 220
    const val ContentEnterMillis = 320
    const val ContentExitMillis = 240
    const val PanelEnterStiffness = 420f
    const val PanelExitStiffness = 500f
    const val DropdownStiffness = 320f
}

internal fun meloXPageEnter(fromRight: Boolean = true): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(MeloXMotion.PageEnterMillis, easing = FastOutSlowInEasing),
        initialOffsetX = { if (fromRight) it else -it / 4 },
    ) + fadeIn(tween(MeloXMotion.PageEnterMillis, easing = FastOutSlowInEasing))

internal fun meloXPageExit(toRight: Boolean = true): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(MeloXMotion.PageExitMillis, easing = FastOutSlowInEasing),
        targetOffsetX = { if (toRight) it / 4 else -it / 4 },
    ) + fadeOut(tween(MeloXMotion.PageExitMillis, easing = FastOutSlowInEasing))

internal fun meloXContentEnter() = fadeIn(
    tween(MeloXMotion.ContentEnterMillis, easing = FastOutSlowInEasing),
)

internal fun meloXContentExit() = fadeOut(
    tween(MeloXMotion.ContentExitMillis, easing = FastOutSlowInEasing),
)

internal fun meloXPanelEnter(initialScale: Float = 0.96f) =
    fadeIn(spring(dampingRatio = 0.86f, stiffness = MeloXMotion.PanelEnterStiffness)) +
        scaleIn(
            initialScale = initialScale,
            animationSpec = spring(dampingRatio = 0.86f, stiffness = MeloXMotion.PanelEnterStiffness),
        )

internal fun meloXPanelExit(targetScale: Float = 0.98f) =
    fadeOut(spring(dampingRatio = 0.90f, stiffness = MeloXMotion.PanelExitStiffness)) +
        scaleOut(
            targetScale = targetScale,
            animationSpec = spring(dampingRatio = 0.90f, stiffness = MeloXMotion.PanelExitStiffness),
        )
