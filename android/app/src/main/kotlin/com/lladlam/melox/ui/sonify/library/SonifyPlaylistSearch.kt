/*
 * Sonify in-page playlist search (GPL-3.0), adapted for MeloX.
 *
 * Upstream: ui/pages/library/playlists/PlaylistSearch.kt (master @ 209e1aa).
 * Preserved: the 42dp round button that morphs into a full-width field,
 * absolute-position geometry, delayed unmount while collapsing, IME search
 * action, and the clear affordance. Sonify drawables use MeloXSymbol.
 */
package com.lladlam.melox.ui.sonify.library

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import kotlinx.coroutines.delay

internal val SonifySearchMorphSpec: FiniteAnimationSpec<Float> =
    spring(dampingRatio = 1.2f, stiffness = 800f, visibilityThreshold = 0.001f)

internal const val SonifySearchMorphSettled = 0.995f
internal const val SonifySearchMorphDurationMs = 380L

@Composable
internal fun SonifyMorphSearchField(
    fraction: State<Float>,
    geometry: SonifyPlayActionGeometry,
    contentColor: Color,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    expanded: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val fill = contentColor.copy(alpha = 0.18f)
    val interaction = remember { MutableInteractionSource() }
    val buttonSizePx = with(density) { geometry.buttonSize.toPx() }
    val expandedWidthPx = with(density) { geometry.expandedWidth.toPx() }
    val trailingCenterPx = with(density) { geometry.trailingCenterX.toPx() }
    val collapsedSizePx = with(density) { SonifySearchFieldCollapsedSize.toPx() }
    val expandedHeightPx = with(density) { SonifySearchFieldExpandedHeight.toPx() }
    val collapsedRadiusPx = collapsedSizePx / 2f
    val expandedRadiusPx = with(density) { 12.dp.toPx() }
    val rimWidthPx = with(density) { 0.75.dp.toPx() }

    var keepMountedWhileCollapsing by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) keepMountedWhileCollapsing = true
        else {
            delay(SonifySearchMorphDurationMs)
            keepMountedWhileCollapsing = false
        }
    }
    val expandedLayerPresent = expanded || keepMountedWhileCollapsing

    Box(
        modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val f = fraction.value.coerceIn(0f, 1f)
                val widthPx = lerp(buttonSizePx, expandedWidthPx, f)
                val heightPx = lerp(collapsedSizePx, expandedHeightPx, f)
                val centerX = lerp(trailingCenterPx, expandedWidthPx / 2f, f)
                val placeable = measurable.measure(
                    Constraints.fixed(
                        (widthPx + 0.5f).toInt().coerceAtLeast(1),
                        (heightPx + 0.5f).toInt().coerceAtLeast(1),
                    ),
                )
                val boxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
                val boxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else expandedHeightPx.toInt()
                layout(boxWidth, boxHeight) {
                    placeable.place(
                        (centerX - widthPx / 2f + 0.5f).toInt(),
                        ((boxHeight - heightPx) / 2f + 0.5f).toInt(),
                    )
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val f = fraction.value.coerceIn(0f, 1f)
                    val radiusPx = lerp(collapsedRadiusPx, expandedRadiusPx, f)
                    drawRoundRect(color = fill, cornerRadius = CornerRadius(radiusPx))
                    val rimAlpha = 0.10f * (1f - f)
                    if (rimAlpha > 0.004f) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = rimAlpha),
                            cornerRadius = CornerRadius(radiusPx),
                            style = Stroke(width = rimWidthPx),
                        )
                    }
                }
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                    if (!expanded) {
                        onOpen()
                    } else {
                        runCatching { focusRequester.requestFocus() }
                        keyboard?.show()
                    }
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.Search,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { alpha = (1f - fraction.value * 2.2f).coerceIn(0f, 1f) },
                    color = contentColor,
                )
            }

            if (expandedLayerPresent) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp)
                        .graphicsLayer {
                            alpha = SonifyPlayActionMotion.inputAlpha(fraction.value)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MeloXSymbolIcon(
                        symbol = MeloXSymbol.Search,
                        modifier = Modifier.size(18.dp),
                        color = contentColor.copy(alpha = 0.55f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.text.isEmpty()) {
                            Text(
                                text = "搜索歌单内的歌曲",
                                fontSize = 16.sp,
                                maxLines = 1,
                                modifier = Modifier.alpha(0.5f),
                                color = contentColor,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(color = contentColor, fontSize = 16.sp),
                            cursorBrush = SolidColor(contentColor),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Search,
                            ),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .semantics { contentDescription = "搜索歌单内的歌曲" },
                        )
                    }
                    if (query.text.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onClear() },
                            contentAlignment = Alignment.Center,
                        ) {
                            MeloXSymbolIcon(
                                symbol = MeloXSymbol.Xmark,
                                modifier = Modifier.size(19.dp),
                                color = contentColor.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SonifyWideSearchFieldRow(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onClear: () -> Unit,
    contentColor: Color,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(SonifySearchFieldExpandedHeight)
            .background(contentColor.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeloXSymbolIcon(
            symbol = MeloXSymbol.Search,
            modifier = Modifier.size(18.dp),
            color = contentColor.copy(alpha = 0.55f),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.text.isEmpty()) {
                Text(
                    text = "搜索歌单内的歌曲",
                    fontSize = 16.sp,
                    maxLines = 1,
                    modifier = Modifier.alpha(0.5f),
                    color = contentColor,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = contentColor, fontSize = 16.sp),
                cursorBrush = SolidColor(contentColor),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
        if (query.text.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onClear() },
                contentAlignment = Alignment.Center,
            ) {
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.Xmark,
                    modifier = Modifier.size(19.dp),
                    color = contentColor.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
internal fun SonifyPlaylistEmptyState(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MeloXSymbolIcon(
            symbol = MeloXSymbol.Library,
            modifier = Modifier.size(70.dp).alpha(0.3f),
            color = LocalContentColor.current,
        )
        Text(
            text = text,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp).alpha(0.55f),
        )
    }
}
