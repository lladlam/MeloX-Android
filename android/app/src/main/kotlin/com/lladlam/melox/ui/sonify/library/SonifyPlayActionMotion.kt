/*
 * Sonify action-row motion curves (GPL-3.0), adapted for MeloX.
 *
 * Upstream: ui/pages/library/playlists/PlayActionMotion.kt and
 * PlayActionRowMotion.kt (master @ 209e1aa). Pure timing functions are kept
 * verbatim; the action row composables are adapted to MeloXSymbol icons and
 * Coil3 artwork while preserving the 42dp buttons, 44dp play pill, 13dp gap
 * and the "squeeze into search field" behaviour.
 */
package com.lladlam.melox.ui.sonify.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXSymbolVariant

internal object SonifyPlayActionMotion {
    private const val PILL_SQUEEZE_START = 0.12f
    private const val PILL_SQUEEZE_END = 0.86f
    private const val PILL_SLIDE_START = 0f
    private const val PILL_SLIDE_END = 0.82f
    private const val PILL_CONTENT_FADE_AT = 0.55f
    private const val PILL_ALPHA_START = 0.55f
    private const val PILL_ALPHA_END = 0.92f
    private const val SHUFFLE_START = 0.10f
    private const val SHUFFLE_END = 0.80f
    private const val OUT_BLUR_START = 0f
    private const val OUT_BLUR_END = 0.75f
    private const val IN_START = 0.32f
    private const val IN_END = 0.88f
    const val OUT_BLUR_DP = 11f
    const val IN_BLUR_DP = 11f
    const val BLUR_STEPS = 8

    private fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v

    private fun remap(fraction: Float, start: Float, end: Float): Float {
        if (end <= start) return if (fraction >= end) 1f else 0f
        return clamp01((fraction - start) / (end - start))
    }

    fun pillWidthFraction(fraction: Float): Float =
        1f - remap(fraction, PILL_SQUEEZE_START, PILL_SQUEEZE_END)

    fun pillSlideFraction(fraction: Float): Float =
        remap(fraction, PILL_SLIDE_START, PILL_SLIDE_END)

    fun pillContentAlpha(fraction: Float): Float {
        val squeezed = 1f - pillWidthFraction(fraction)
        return 1f - clamp01(squeezed / PILL_CONTENT_FADE_AT)
    }

    fun pillAlpha(fraction: Float): Float =
        1f - remap(fraction, PILL_ALPHA_START, PILL_ALPHA_END)

    const val SHUFFLE_MIN_SCALE = 0.08f

    fun shuffleScale(fraction: Float): Float {
        val k = remap(fraction, SHUFFLE_START, SHUFFLE_END)
        return 1f + (SHUFFLE_MIN_SCALE - 1f) * k
    }

    fun shuffleSlideFraction(fraction: Float): Float =
        remap(fraction, SHUFFLE_START, SHUFFLE_END)

    fun shuffleAlpha(fraction: Float): Float = 1f - remap(fraction, SHUFFLE_START, SHUFFLE_END)

    fun outBlurDp(fraction: Float): Float =
        OUT_BLUR_DP * remap(fraction, OUT_BLUR_START, OUT_BLUR_END)

    fun inputAlpha(fraction: Float): Float = remap(fraction, IN_START, IN_END)

    fun inputBlurDp(fraction: Float): Float =
        IN_BLUR_DP * (1f - remap(fraction, IN_START, IN_END))

    fun blurStep(radiusDp: Float, maxDp: Float): Int {
        if (maxDp <= 0f) return 0
        val k = clamp01(radiusDp / maxDp)
        return (k * BLUR_STEPS + 0.5f).toInt().coerceIn(0, BLUR_STEPS)
    }

    fun stepToDp(step: Int, maxDp: Float): Float =
        maxDp * step.coerceIn(0, BLUR_STEPS) / BLUR_STEPS
}

internal val SonifySearchFieldCollapsedSize = 42.dp
internal val SonifySearchFieldExpandedHeight = 44.dp
internal val SonifySearchActionGap = 13.dp
internal val SonifySearchActionButtonSize = 42.dp
internal val SonifySearchActionMinPillWidth = 140.dp
internal val SonifyActionRowBlurBleed = SonifyPlayActionMotion.OUT_BLUR_DP.dp
internal val SonifyActionRowTopPadding = 14.dp
internal val SonifyActionRowBottomPadding = 10.dp

internal class SonifyPlayActionGeometry(maxWidth: Dp) {
    val buttonSize = SonifySearchActionButtonSize
    val gap = SonifySearchActionGap
    private val trailingWidth = buttonSize + gap
    private val remaining = maxWidth - buttonSize - gap - trailingWidth
    val pillWidth = (remaining * 2f / 3f).coerceAtLeast(SonifySearchActionMinPillWidth)
    private val rowWidth = buttonSize + gap + pillWidth + trailingWidth
    private val rowStart = (maxWidth - rowWidth) / 2f
    val shuffleLeft = rowStart
    val pillLeft = rowStart + buttonSize + gap
    val trailingLeft = rowStart + buttonSize + gap + pillWidth + gap
    val trailingCenterX = trailingLeft + buttonSize / 2f
    val expandedWidth = maxWidth
}

@Composable
internal fun SonifyPlayActionRowMotion(
    fraction: State<Float>,
    foregroundColor: Color,
    playEnabled: Boolean,
    onShuffle: () -> Unit,
    onPlayAll: () -> Unit,
    searchQuery: TextFieldValue,
    onSearchQueryChange: (TextFieldValue) -> Unit,
    onSearchClear: () -> Unit,
    searchFocus: FocusRequester,
    searchExpanded: Boolean,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(SonifySearchFieldExpandedHeight + SonifyActionRowBlurBleed * 2),
    ) {
        val geometry = remember(maxWidth) { SonifyPlayActionGeometry(maxWidth) }
        val f = fraction.value.coerceIn(0f, 1f)
        val outBlurDp = SonifyPlayActionMotion.outBlurDp(f)
        val content: @Composable () -> Unit = {
            SonifyActionRowContent(
                geometry = geometry,
                foregroundColor = foregroundColor,
                playEnabled = playEnabled,
                onShuffle = onShuffle,
                onPlayAll = onPlayAll,
                f = f,
            )
        }
        if (outBlurDp > 0.05f) {
            Box(Modifier.fillMaxSize().blur(outBlurDp.dp, BlurredEdgeTreatment.Unbounded)) {
                content()
            }
        } else {
            content()
        }
        SonifyMorphSearchField(
            fraction = fraction,
            geometry = geometry,
            contentColor = foregroundColor,
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onClear = onSearchClear,
            focusRequester = searchFocus,
            expanded = searchExpanded,
            onOpen = onOpenSearch,
        )
    }
}

@Composable
private fun SonifyActionRowContent(
    geometry: SonifyPlayActionGeometry,
    foregroundColor: Color,
    playEnabled: Boolean,
    onShuffle: () -> Unit,
    onPlayAll: () -> Unit,
    f: Float,
) {
    val shuffleAlpha = SonifyPlayActionMotion.shuffleAlpha(f)
    if (shuffleAlpha > 0.001f) {
        val shuffleLeft = geometry.shuffleLeft -
            (geometry.shuffleLeft + geometry.buttonSize) *
            SonifyPlayActionMotion.shuffleSlideFraction(f)
        SonifyRoundMotionButton(
            symbol = MeloXSymbol.Shuffle,
            contentDescription = "随机播放",
            contentColor = foregroundColor,
            enabled = playEnabled,
            onClick = onShuffle,
            modifier = Modifier
                .sonifyPlaceAt(
                    left = shuffleLeft,
                    width = geometry.buttonSize,
                    ownHeight = geometry.buttonSize,
                )
                .graphicsLayer {
                    val s = SonifyPlayActionMotion.shuffleScale(f)
                    scaleX = s
                    scaleY = s
                    alpha = shuffleAlpha
                },
        )
    }
    val pillAlpha = SonifyPlayActionMotion.pillAlpha(f)
    if (pillAlpha > 0.001f) {
        val pillWidth = geometry.pillWidth * SonifyPlayActionMotion.pillWidthFraction(f)
        if (pillWidth > 0.5.dp) {
            val pillLeft = geometry.pillLeft * (1f - SonifyPlayActionMotion.pillSlideFraction(f))
            val contentAlpha = SonifyPlayActionMotion.pillContentAlpha(f)
            Box(
                Modifier
                    .sonifyPlaceAt(
                        left = pillLeft,
                        width = pillWidth,
                        ownHeight = SonifySearchFieldExpandedHeight,
                    )
                    .graphicsLayer { alpha = pillAlpha },
            ) {
                Box(Modifier.fillMaxSize().clipToBounds()) {
                    SonifyPlayPill(
                        enabled = playEnabled,
                        foregroundColor = foregroundColor,
                        contentAlpha = contentAlpha,
                        onClick = onPlayAll,
                        modifier = Modifier.width(geometry.pillWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun SonifyPlayPill(
    enabled: Boolean,
    foregroundColor: Color,
    contentAlpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val dim = if (enabled) 1f else 0.45f
    val contentColor = if (foregroundColor.luminance() > 0.5f) Color.Black else Color.White
    Row(
        modifier = modifier
            .height(SonifySearchFieldExpandedHeight)
            .clip(CircleShape)
            .background(foregroundColor.copy(alpha = foregroundColor.alpha * dim))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 36.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeloXSymbolIcon(
            symbol = MeloXSymbol.Play,
            modifier = Modifier.size(20.dp).graphicsLayer { alpha = contentAlpha },
            color = contentColor.copy(alpha = contentColor.alpha * dim),
            variant = MeloXSymbolVariant.Fill,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "播放",
            color = contentColor.copy(alpha = contentColor.alpha * dim),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.graphicsLayer { alpha = contentAlpha },
        )
    }
}

@Composable
private fun SonifyRoundMotionButton(
    symbol: MeloXSymbol,
    contentDescription: String,
    contentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dim = if (enabled) 1f else 0.45f
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(contentColor.copy(alpha = 0.18f * dim))
            .border(0.75.dp, Color.White.copy(alpha = 0.10f * dim), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = if (pressed) 0.96f else 1f
                    scaleX = s
                    scaleY = s
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            MeloXSymbolIcon(
                symbol = symbol,
                modifier = Modifier.size(24.dp),
                color = contentColor.copy(alpha = dim),
            )
        }
    }
}

private fun Modifier.sonifyPlaceAt(
    left: Dp,
    width: Dp,
    ownHeight: Dp,
): Modifier = this.layout { measurable, constraints ->
    val rowH = if (constraints.hasBoundedHeight) constraints.maxHeight else 44.dp.roundToPx()
    val bleedPx = ((rowH - 44.dp.roundToPx()) / 2).coerceAtLeast(0)
    val ownH = ownHeight.roundToPx()
        .coerceAtMost(rowH - bleedPx * 2)
        .coerceAtLeast(1)
    val w = width.roundToPx().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(w, ownH))
    layout(constraints.maxWidth, rowH) {
        val contentRowTop = bleedPx
        val contentRowH = rowH - bleedPx * 2
        placeable.place(left.roundToPx(), contentRowTop + (contentRowH - ownH) / 2)
    }
}
