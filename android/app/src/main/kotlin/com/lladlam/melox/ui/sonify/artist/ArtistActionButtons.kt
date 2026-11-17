/*
 * Sonify artist action buttons (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/pages/library/artists/ArtistActionButtons.kt
 * (master @ 209e1aa). Geometry, materials, press scale and the EvenOdd play
 * cut-out are kept. Sonify drawables are replaced by MeloX SF-symbol glyphs.
 *
 * These buttons are placed inside the page backdrop recording layer and must
 * never consume that backdrop themselves (RenderNode self-reference).
 */
package com.lladlam.melox.ui.sonify.artist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.R
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXSymbolVariant
import com.lladlam.melox.ui.sonify.artworkContentColor
import com.lladlam.melox.ui.sonify.isSonifyDark

/**
 * 动作区半透明圆钮（不消费 backdrop）：42dp 圆、白色半透底、按压回缩。
 */
@Composable
internal fun ArtistRoundActionButton(
    symbol: MeloXSymbol,
    contentDescription: String?,
    iconTint: Color,
    highlighted: Boolean,
    enabled: Boolean = true,
    iconSize: Dp = 23.dp,
    highlightContainerColor: Color? = null,
    motionEffects: Boolean = true,
    dimWhenDisabled: Boolean = true,
    containerColor: Color? = null,
    variant: MeloXSymbolVariant = MeloXSymbolVariant.Regular,
    onClick: () -> Unit
) {
    ArtistRoundShell(
        highlighted = highlighted,
        enabled = enabled,
        motionEffects = motionEffects,
        dimWhenDisabled = dimWhenDisabled,
        containerColor = containerColor,
        highlightContainerColor = highlightContainerColor,
        onClick = onClick
    ) { dim ->
        MeloXSymbolIcon(
            symbol = symbol,
            modifier = Modifier.size(iconSize),
            color = iconColorOf(iconTint, dim),
            variant = variant,
            contentDescription = contentDescription
        )
    }
}

/** 「加入资料库」圆钮：外壳与 [ArtistRoundActionButton] 同源，图标换成形变加号/勾号。 */
@Composable
internal fun ArtistCollectButton(
    collected: Boolean,
    enabled: Boolean = true,
    iconSize: Dp = 24.dp,
    iconTint: Color = Color.Unspecified,
    containerColor: Color? = null,
    motionEffects: Boolean = true,
    description: String? = null,
    onClick: () -> Unit
) {
    ArtistRoundShell(
        highlighted = false,
        enabled = enabled,
        motionEffects = motionEffects,
        dimWhenDisabled = false,
        containerColor = containerColor,
        highlightContainerColor = null,
        onClick = onClick
    ) { dim ->
        CollectIconMorph(
            collected = collected,
            contentDescription = description ?: if (collected) {
                androidx.compose.ui.res.stringResource(R.string.artist_saved)
            } else {
                androidx.compose.ui.res.stringResource(R.string.artist_save)
            },
            tint = iconColorOf(iconTint, dim),
            iconSize = iconSize,
            animated = motionEffects
        )
    }
}

@Composable
private fun ArtistRoundShell(
    highlighted: Boolean,
    enabled: Boolean,
    motionEffects: Boolean,
    dimWhenDisabled: Boolean,
    containerColor: Color?,
    highlightContainerColor: Color?,
    onClick: () -> Unit,
    content: @Composable BoxScope.(dim: Float) -> Unit
) {
    val isDark = isSonifyDark()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "artistActionPress")
    val dim = if (dimWhenDisabled && !enabled) 0.45f else 1f
    val container = when {
        highlighted -> highlightContainerColor ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else -> containerColor ?: Color.White.copy(alpha = if (isDark) 0.12f else 0.22f)
    }
    Box(
        modifier = Modifier
            .then(if (motionEffects) Modifier.graphicsLayer { scaleX = scale; scaleY = scale } else Modifier)
            .size(42.dp)
            .clip(CircleShape)
            .background(container.copy(alpha = container.alpha * dim))
            .border(
                width = 0.75.dp,
                color = Color.White.copy(alpha = 0.10f * dim),
                shape = CircleShape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content(dim)
    }
}

private fun iconColorOf(iconTint: Color, dim: Float): Color =
    (if (iconTint != Color.Unspecified) iconTint else Color.White).copy(alpha = dim)

/** 播放胶囊（实心 + 三角图标 + 文字）。 */
@Composable
internal fun PlayPillButton(
    label: String,
    enabled: Boolean = true,
    containerColor: Color = Color.White,
    contentColor: Color = Color(0xFF161616),
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val dim = if (enabled) 1f else 0.45f
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(containerColor.copy(alpha = containerColor.alpha * dim))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        MeloXSymbolIcon(
            symbol = MeloXSymbol.Play,
            modifier = Modifier.size(20.dp),
            color = contentColor.copy(alpha = contentColor.alpha * dim),
            variant = MeloXSymbolVariant.Fill
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = contentColor.copy(alpha = contentColor.alpha * dim),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 手机详情页操作行：随机圆钮 · 播放胶囊 · 可选尾部圆钮。 */
@Composable
internal fun CompactPlayActionRow(
    foregroundColor: Color,
    playEnabled: Boolean,
    onShuffle: () -> Unit,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier,
    shuffleEnabled: Boolean = playEnabled,
    trailing: @Composable (() -> Unit)? = null
) {
    val gap = 13.dp
    val buttonSize = 42.dp
    val roundContainer = foregroundColor.copy(alpha = 0.18f)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val trailingWidth = if (trailing != null) buttonSize + gap else 0.dp
        val remaining = maxWidth - buttonSize - gap - trailingWidth
        val pillWidth = (remaining * 2f / 3f).coerceAtLeast(140.dp)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtistRoundActionButton(
                symbol = MeloXSymbol.Shuffle,
                contentDescription = androidx.compose.ui.res.stringResource(R.string.artist_shuffle),
                iconTint = foregroundColor,
                highlighted = false,
                iconSize = 24.dp,
                enabled = shuffleEnabled,
                containerColor = roundContainer,
                onClick = onShuffle
            )
            Spacer(Modifier.width(gap))
            PlayPillButton(
                label = androidx.compose.ui.res.stringResource(R.string.artist_play),
                enabled = playEnabled,
                containerColor = foregroundColor,
                contentColor = artworkContentColor(foregroundColor),
                modifier = Modifier.width(pillWidth),
                onClick = onPlayAll
            )
            if (trailing != null) {
                Spacer(Modifier.width(gap))
                trailing()
            }
        }
    }
}

/**
 * 播放圆钮：EvenOdd 几何镂空，孔洞始终透出真实背景（不依赖离屏清除）。
 */
@Composable
internal fun ArtistPlayButton(
    enabled: Boolean,
    contentColor: Color = Color.White,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(64.dp)
            .drawBehind {
                val path = Path().apply {
                    val w = size.width
                    val h = size.height
                    moveTo(w * 0.37f, h * 0.33f)
                    cubicTo(w * 0.37f, h * 0.2925f, w * 0.389f, h * 0.28f, w * 0.42f, h * 0.299f)
                    lineTo(w * 0.678f, h * 0.475f)
                    cubicTo(w * 0.701f, h * 0.491f, w * 0.701f, h * 0.510f, w * 0.677f, h * 0.525f)
                    lineTo(w * 0.42f, h * 0.695f)
                    cubicTo(w * 0.389f, h * 0.714f, w * 0.37f, h * 0.701f, w * 0.37f, h * 0.664f)
                    close()
                }
                val button = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addOval(Rect(Offset.Zero, size))
                    addPath(path)
                }
                drawPath(button, color = contentColor)
            }
            .circleEdgeLight(contentColor.copy(alpha = 0.8f), 0.15.dp, 0.5.dp)
            .semantics { contentDescription = context.getString(R.string.artist_play) }
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    )
}
