package com.lladlam.melox.ui.glass

/*
 * Thin MeloX wrapper around the directly ported AndroidLiquidGlass Catalog demo.
 * Upstream is licensed under Apache-2.0:
 * https://github.com/Kyant0/AndroidLiquidGlass/blob/main/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidSlider.kt
 */

import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import com.lladlam.melox.ui.glass.liquidsliderdemo.MeloXDemoLiquidSlider
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import kotlin.math.roundToInt

@Composable
fun MeloXLiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onTransientValueChange: (Float) -> Unit = {},
    valueRange: ClosedFloatingPointRange<Float>,
    stepSize: Float = 100f,
    visibilityThreshold: Float = 0.01f,
    modifier: Modifier = Modifier,
    contentDescription: String,
) {
    require(valueRange.endInclusive > valueRange.start) { "valueRange must not be empty" }
    require(stepSize >= 0f) { "stepSize must be non-negative" }
    val externalValue = quantizeMeloXSliderValue(value, valueRange, stepSize)
    var transientValue by remember { mutableFloatStateOf(externalValue) }
    var interacting by remember { mutableStateOf(false) }
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnTransientValueChange by rememberUpdatedState(onTransientValueChange)
    val steps = if (stepSize > 0f) {
        (((valueRange.endInclusive - valueRange.start) / stepSize).roundToInt() - 1).coerceAtLeast(0)
    } else 0

    LaunchedEffect(externalValue, interacting) {
        if (!interacting) transientValue = externalValue
    }

    fun finish() {
        val settled = quantizeMeloXSliderValue(transientValue, valueRange, stepSize)
        transientValue = settled
        interacting = false
        if (settled != externalValue) latestOnValueChange(settled)
    }

    val semantics = Modifier.semantics(mergeDescendants = true) {
        this.contentDescription = contentDescription
        progressBarRangeInfo = ProgressBarRangeInfo(transientValue, valueRange, steps)
        setProgress { requested ->
            transientValue = quantizeMeloXSliderValue(requested, valueRange, stepSize)
            finish()
            true
        }
    }
    val backdrop = LocalMeloXBackdrop.current
    if (backdrop == null || MeloXSettingsRuntime.frostedGlassEnabled) {
        Slider(
            value = transientValue,
            onValueChange = {
                interacting = true
                transientValue = it.coerceIn(valueRange)
                latestOnTransientValueChange(transientValue)
            },
            onValueChangeFinished = ::finish,
            valueRange = valueRange,
            steps = steps,
            modifier = modifier.then(semantics),
        )
        return
    }

    MeloXDemoLiquidSlider(
        value = { transientValue },
        onValueChange = {
            interacting = true
            transientValue = it.coerceIn(valueRange)
            latestOnTransientValueChange(transientValue)
        },
        onValueChangeFinished = ::finish,
        valueRange = valueRange,
        visibilityThreshold = visibilityThreshold,
        backdrop = backdrop,
        modifier = modifier.then(semantics),
    )
}

internal fun quantizeMeloXSliderValue(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    stepSize: Float,
): Float {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    if (stepSize <= 0f) return clamped
    val steps = ((clamped - range.start) / stepSize).roundToInt()
    return (range.start + steps * stepSize).coerceIn(range.start, range.endInclusive)
}
