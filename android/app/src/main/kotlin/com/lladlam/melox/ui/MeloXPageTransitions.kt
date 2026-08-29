package com.lladlam.melox.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.lladlam.melox.ui.animation.meloXPageEnter
import kotlinx.coroutines.CancellationException

/** Starts an Activity page without adding a second custom transition. */
internal fun Context.startMeloXPage(intent: Intent) {
    startActivity(intent)
}

internal fun Activity.finishMeloXPage() {
    finish()
}

/** Lets the previous Activity show through while the system runs back preview. */
internal fun Activity.prepareMeloXPagePredictiveBack() {
    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
}

/** Applies the same edge-following back motion used by Settings detail pages. */
@Composable
internal fun MeloXPredictiveBackPage(
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    PredictiveBackHandler {
        try {
            it.collect { event -> progress.snapTo(event.progress) }
            progress.animateTo(1f, tween(160))
            onBack()
        } catch (_: CancellationException) {
            progress.animateTo(0f, tween(160))
        }
    }
    AnimatedVisibility(
        visible = entered,
        enter = meloXPageEnter(fromRight = true),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = size.width * progress.value
                    val scale = 1f - 0.08f * progress.value
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
        ) {
            content()
        }
    }
}
