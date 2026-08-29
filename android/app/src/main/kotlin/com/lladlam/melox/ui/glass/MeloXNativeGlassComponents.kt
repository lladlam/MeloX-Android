package com.lladlam.melox.ui.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import com.lladlam.melox.ui.glass.publicdemo.PublicInteractiveHighlight
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import com.lladlam.melox.ui.glass.publicdemo.LiquidDragAnimation
import com.lladlam.melox.ui.glass.publicdemo.PublicDampedDragAnimation
import androidx.compose.ui.draw.drawBehind
import com.lladlam.melox.ui.theme.isMeloXDarkTheme
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime

@Composable
fun MeloXGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: MeloXGlassButtonStyle = MeloXGlassButtonStyle.Bordered,
    material: MeloXGlassMaterial = MeloXGlassMaterial.Regular,
    // Apple’s default glass effect shape is a capsule. Larger cards pass an
    // explicit rounded rectangle when they need a different silhouette.
    shape: Shape = MeloXShapes.capsule,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        PublicInteractiveHighlight(animationScope)
    }
    val buttonTint = when (style) {
        MeloXGlassButtonStyle.Bordered -> tint
        MeloXGlassButtonStyle.BorderedProminent -> MeloXSystemColors.Red
        MeloXGlassButtonStyle.Plain -> Color.Transparent
        MeloXGlassButtonStyle.Destructive -> MeloXSystemColors.Red
    }
    val buttonSurface = when {
        surfaceColor != Color.Unspecified -> surfaceColor
        style == MeloXGlassButtonStyle.BorderedProminent -> MeloXSystemColors.Red.copy(alpha = 0.92f)
        style == MeloXGlassButtonStyle.Destructive -> MeloXSystemColors.Red.copy(alpha = 0.12f)
        style == MeloXGlassButtonStyle.Plain -> Color.Transparent
        else -> Color.Unspecified
    }
    val contentColor = when (style) {
        MeloXGlassButtonStyle.BorderedProminent -> Color.White
        MeloXGlassButtonStyle.Destructive -> MeloXSystemColors.Red
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .meloXGlassSurface(
                shape = shape,
                material = material,
                enabled = enabled,
                tint = buttonTint,
                surfaceColor = buttonSurface,
                pressProgress = if (enabled) interactiveHighlight.pressProgress else 0f,
                dragOffset = if (enabled) interactiveHighlight.offset else Offset.Zero,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
             .then(if (enabled && !MeloXSettingsRuntime.frostedGlassEnabled) interactiveHighlight.modifier else Modifier)
             .then(if (enabled && !MeloXSettingsRuntime.frostedGlassEnabled) interactiveHighlight.gestureModifier else Modifier)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

@Composable
fun MeloXGlassIconButton(
    symbol: MeloXSymbol,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(44.dp),
    enabled: Boolean = true,
    selected: Boolean = false,
    contentDescription: String? = null,
) {
    MeloXGlassButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        contentPadding = PaddingValues(10.dp),
        modifier = modifier.semantics {
            this.contentDescription = contentDescription ?: symbol.sfSymbolName
        },
    ) {
        MeloXSymbolIcon(
            symbol = symbol,
            modifier = Modifier.size(24.dp),
            color = if (selected) MeloXSystemColors.Red else MaterialTheme.colorScheme.onSurface,
            variant = if (selected) MeloXSymbolVariant.Fill else MeloXSymbolVariant.Regular,
        )
    }
}

/** Compact iOS-style switch used by settings and provider controls. */
@Composable
fun MeloXGlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val dark = isMeloXDarkTheme()
    val accent = if (dark) Color(0xFF30D158) else Color(0xFF34C759)
    val track = if (dark) Color(0xFF787880).copy(alpha = 0.36f) else Color(0xFF787878).copy(alpha = 0.20f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val travelPx = with(density) { 20.dp.toPx() }
    val tapThresholdPx = with(density) { 2.dp.toPx() }
    val scope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val currentChecked by rememberUpdatedState(checked)
    val animation = remember(scope) {
        LiquidDragAnimation(
            animationScope = scope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (!enabled) return@LiquidDragAnimation
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    didDrag = false
                } else {
                    fraction = if (currentChecked) 0f else 1f
                }
                onCheckedChange(fraction == 1f)
                if (com.lladlam.melox.ui.settings.MeloXSettingsRuntime.hapticFeedbackEnabled)
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            },
            onDrag = { _, dragAmount ->
                if (!enabled) return@LiquidDragAnimation
                if (!didDrag) {
                    didDrag = kotlin.math.abs(dragAmount.x) > tapThresholdPx
                }
                val delta = dragAmount.x / travelPx
                fraction = if (isLtr) (fraction + delta).coerceIn(0f, 1f)
                else (fraction - delta).coerceIn(0f, 1f)
            },
        )
    }
    LaunchedEffect(animation) {
        snapshotFlow { fraction }.collectLatest(animation::updateValue)
    }
    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (target != fraction) {
            fraction = target
            animation.animateToValue(target)
        }
    }

    val trackBackdrop = rememberLayerBackdrop()
    val pageBackdrop = LocalMeloXBackdrop.current
    Box(
        modifier = modifier
            .width(64.dp)
            .height(28.dp)
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                if (!enabled) disabled()
                onClick {
                    if (enabled) onCheckedChange(!checked)
                    enabled
                }
            }
            .then(if (enabled) animation.modifier else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .drawBehind { drawRect(colorLerp(track, accent, animation.value)) }
                .size(width = 64.dp, height = 28.dp),
        )
        Box(
            Modifier
                .graphicsLayer {
                    val padding = 2.dp.toPx()
                    translationX = if (isLtr) lerp(padding, padding + travelPx, animation.value)
                    else lerp(-padding, -(padding + travelPx), animation.value)
                }
                .then(
                    if (pageBackdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = rememberCombinedBackdrop(pageBackdrop, trackBackdrop),
                            shape = { Capsule() },
                            effects = {
                                val p = animation.pressProgress
                                blur(8.dp.toPx() * (1f - p))
                                lens(5.dp.toPx() * p, 10.dp.toPx() * p, chromaticAberration = true)
                            },
                            highlight = {
                                Highlight.Ambient.copy(
                                    width = Highlight.Ambient.width / 1.5f,
                                    blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                                    alpha = animation.pressProgress,
                                )
                            },
                            shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f)) },
                            innerShadow = { InnerShadow(radius = 4.dp * animation.pressProgress, alpha = animation.pressProgress) },
                            layerBlock = {
                                scaleX = animation.scaleX
                                scaleY = animation.scaleY
                                val velocity = animation.velocity / 50f
                                scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                                alpha = if (enabled) 1f else 0.45f
                            },
                            onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - animation.pressProgress)) },
                        )
                    } else {
                        Modifier
                            .background(Color.White, MeloXShapes.capsule)
                            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
                    },
                )
                .size(width = 40.dp, height = 24.dp),
        )
    }
}

@Composable
fun MeloXGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    textStyle: TextStyle = TextStyle.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .heightIn(min = 50.dp)
            .meloXGlassSurface(
                shape = MeloXShapes.capsule,
                material = MeloXGlassMaterial.Regular,
                enabled = enabled,
                surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.055f),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leadingContent?.invoke()
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = textStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { onFocusChanged?.invoke(it.isFocused) },
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) placeholder?.invoke()
                    innerTextField()
                }
            },
        )
        trailingContent?.invoke()
    }
}

@Composable
fun MeloXGlassToolbarButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    MeloXGlassButton(
        onClick = onClick,
        modifier = modifier,
        shape = MeloXShapes.capsule,
        style = if (selected) MeloXGlassButtonStyle.BorderedProminent else MeloXGlassButtonStyle.Bordered,
                tint = if (selected) MeloXSystemColors.Red.copy(alpha = 0.22f) else Color.Unspecified,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            color = if (selected) MeloXSystemColors.Red else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** A reusable iOS grouped-content surface for cards and settings sections. */
@Composable
fun MeloXGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MeloXShapes.card,
    material: MeloXGlassMaterial = MeloXGlassMaterial.Regular,
    surfaceColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .meloXGlassSurface(
                shape = shape,
                material = material,
                surfaceColor = surfaceColor,
            )
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ) else Modifier,
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** iOS segmented/capsule selection control built on the same glass primitive. */
@Composable
fun MeloXGlassSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .meloXGlassSurface(
                shape = MeloXShapes.capsule,
                material = MeloXGlassMaterial.Regular,
                surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.055f),
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            MeloXGlassButton(
                onClick = { onSelected(index) },
                modifier = Modifier.weight(1f),
                style = if (index == selectedIndex) MeloXGlassButtonStyle.BorderedProminent
                else MeloXGlassButtonStyle.Plain,
                shape = MeloXShapes.capsule,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = item,
                    style = MeloXTypography.subheadline,
                    maxLines = 1,
                )
            }
        }
    }
}
