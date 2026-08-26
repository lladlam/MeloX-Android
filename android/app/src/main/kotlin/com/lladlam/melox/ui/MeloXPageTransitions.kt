package com.lladlam.melox.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.lladlam.melox.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/** Matches Mei's ordinary NavDisplay page push/pop motion for Activity-hosted pages. */
internal fun Context.startMeloXPage(intent: Intent) {
    startActivity(intent)
    (this as? Activity)?.overridePendingTransition(
        R.anim.melox_page_push_enter,
        R.anim.melox_page_push_exit,
    )
}

internal fun Activity.finishMeloXPage() {
    finish()
    overridePendingTransition(
        R.anim.melox_page_pop_enter,
        R.anim.melox_page_pop_exit,
    )
}

/** Lets the previous Activity remain visible while a Mei-style predictive pop is in progress. */
internal fun Activity.prepareMeloXPagePredictiveBack() {
    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
}

@Composable
internal fun MeloXPredictiveBackPage(
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val fraction = progress.value
                    val scale = 1f - 0.08f * fraction
                    translationX = size.width * fraction
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
        ) {
            content()
        }
        PredictiveBackHandler {
            try {
                it.collect { event -> progress.snapTo(event.progress) }
                onBack()
            } catch (_: CancellationException) {
                progress.animateTo(0f, spring())
            }
        }
    }
}
