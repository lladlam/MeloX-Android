package com.lladlam.melox.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** iOS-style large-title/navigation header used by non-player screens. */
@Composable
fun MeloXIosTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    /**
     * 标题字号。默认 [MeloXTypography.largeTitle]（34sp）—— 一级页面用。
     *
     * 深层级页面（如扫码登录）标题较长且不是页面主角，34sp 会显得过大并挤压返回键，
     * 可降到 [MeloXTypography.title2]（22sp）。带默认值，已有调用点不受影响。
     */
    titleStyle: TextStyle = MeloXTypography.largeTitle,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
    ) {
        if (navigation != null) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    navigation.invoke()
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    style = titleStyle.inheritAmbientFamily(),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
            }
            Text(
                text = title,
                style = titleStyle.inheritAmbientFamily(),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        subtitle?.let {
            Text(
                text = it,
                style = MeloXTypography.subheadline.inheritAmbientFamily(),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f),
            )
        }
    }
}

/** iOS grouped-list container. Rows provide their own inset separators. */
@Composable
fun MeloXIosGroupedList(
    modifier: Modifier = Modifier,
    surfaceColor: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.045f),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .meloXContentSurface(
                shape = MeloXShapes.largeCard,
                surfaceColor = surfaceColor,
            ),
        content = content,
    )
}

/** Reusable iOS settings/list row with optional leading, detail and trailing content. */
@Composable
fun MeloXIosListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    detail: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevronTint: Color = MeloXSystemColors.Red.copy(alpha = 0.88f),
    onClick: (() -> Unit)? = null,
    showTopSeparator: Boolean = true,
    /**
     * 副标题最大行数。默认 2 行（与历史行为一致）；设置页的长说明可传更大的值，
     * 让整段文字换行显示而不是被截断。
     */
    subtitleMaxLines: Int = 2,
) {
    val separator = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val minHeight = if (subtitle == null) 56.dp else 68.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 默认仍是固定高度（与历史行为一致）；只有显式放开行数的调用方才允许整行变高。
            .then(
                if (subtitleMaxLines > 2) Modifier.heightIn(min = minHeight)
                else Modifier.height(minHeight),
            )
            .drawBehind {
                if (showTopSeparator) {
                    drawLine(
                        color = separator,
                        start = androidx.compose.ui.geometry.Offset(16.dp.toPx(), 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width - 16.dp.toPx(), 0f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ) else Modifier,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MeloXTypography.body.inheritAmbientFamily(), color = MaterialTheme.colorScheme.onBackground)
            subtitle?.let {
                Text(
                    it,
                    style = MeloXTypography.subheadline.inheritAmbientFamily(),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        detail?.let {
            Text(
                it,
                style = MeloXTypography.subheadline.inheritAmbientFamily(),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
            )
        }
        trailing?.invoke()
        if (onClick != null && trailing == null) {
            Spacer(Modifier.width(8.dp))
            MeloXSymbolIcon(
                MeloXSymbol.ChevronRight,
                Modifier.size(20.dp),
                chevronTint,
                iconSize = 19.sp,
            )
        }
    }
}

/** iOS-style row-leading system icon, kept separate for consistent tint/geometry. */
@Composable
fun MeloXIosListIcon(
    symbol: MeloXSymbol,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    MeloXSymbolIcon(symbol, modifier, tint, iconSize = 22.sp)
}
