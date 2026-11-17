/*
 * Shared Sonify list/row primitives (GPL-3.0), adapted for MeloX.
 *
 * Upstream references:
 *   ui/pages/library/Item.kt
 *   ui/pages/library/LibraryDivider.kt
 *   ui/pages/settings/ListHeader.kt
 *   ui/pages/settings/Divider.kt
 *   ui/pages/library/artists/ArtistActionButtons.kt
 *   ui/pages/library/MusicList.kt
 *   ui/pages/library/artists/ArtistDetailParts.kt
 *
 * The layouts and metrics are kept. Sonify drawables are replaced by MeloX's
 * SF-symbol font icons, and Coil2 ShadowImageWithCache is replaced by Coil3
 * AsyncImage with the same 52dp / 3.5dp artwork geometry.
 */
package com.lladlam.melox.ui.sonify

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXSymbolVariant

@Composable
fun SonifyListHeader(
    content: String,
    horizontalPadding: Dp = 20.dp,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, bottom = 6.dp),
    ) {
        Text(
            text = content,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            color = SonifyHeadline withNight SonifyHeadlineDark,
        )
    }
}

@Composable
fun SonifyGroupSpacer() {
    Spacer(Modifier.height(23.dp))
}

@Composable
fun SonifyLibraryDivider(startPadding: Dp = 60.dp, endPadding: Dp = 16.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startPadding, end = endPadding)
            .height(0.5.dp)
            .alpha(0.15f)
            .background(Color.Black withNight Color.White),
    )
}

@Composable
fun SonifySmallLabelItem(
    icon: MeloXSymbol,
    label: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.padding(start = 18.dp, end = 2.dp).size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            MeloXSymbolIcon(
                symbol = icon,
                modifier = Modifier.size(34.dp),
                color = iconTint,
            )
        }
        Text(
            text = label,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MeloXSymbolIcon(
            symbol = MeloXSymbol.ChevronRight,
            modifier = Modifier
                .padding(end = 21.dp)
                .size(width = 12.dp, height = 16.dp)
                .alpha(0.3f),
            color = LocalContentColor.current,
            variant = MeloXSymbolVariant.Regular,
        )
    }
}

@Composable
fun SonifyArtistSectionHeader(title: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp)
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            MeloXSymbolIcon(
                symbol = MeloXSymbol.ChevronRight,
                modifier = Modifier.size(20.dp).alpha(0.35f),
                color = LocalContentColor.current,
            )
        }
    }
}

@Composable
fun SonifyRoundActionButton(
    symbol: MeloXSymbol,
    contentDescription: String?,
    iconTint: Color,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    iconSize: Dp = 23.dp,
    containerColor: Color? = null,
    variant: MeloXSymbolVariant = MeloXSymbolVariant.Regular,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "sonifyActionPress")
    val dark = isSonifyDark()
    val container = containerColor ?: Color.White.copy(alpha = if (dark) 0.12f else 0.22f)
    val dim = if (enabled) 1f else 0.45f
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(container.copy(alpha = container.alpha * dim))
            .border(0.75.dp, Color.White.copy(alpha = 0.10f * dim), CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(Modifier),
        contentAlignment = Alignment.Center,
    ) {
        MeloXSymbolIcon(
            symbol = symbol,
            modifier = Modifier.size(iconSize).alpha(dim),
            color = if (highlighted) Color.White else iconTint,
            variant = variant,
        )
    }
}

@Composable
fun SonifyPlayButton(
    enabled: Boolean,
    contentColor: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(64.dp)
            .background(contentColor, CircleShape)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MeloXSymbolIcon(
            symbol = MeloXSymbol.Play,
            modifier = Modifier.padding(start = 4.dp).size(28.dp),
            color = if (contentColor.luminance() > 0.5f) Color.Black else Color.White,
            variant = MeloXSymbolVariant.Fill,
        )
    }
}

@Composable
fun SonifySongRow(
    song: SearchSong,
    horizontalPadding: Dp = 20.dp,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .height(64.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(3.5.dp)),
        )
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = song.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                lineHeight = 17.sp,
            )
            Text(
                text = song.artists,
                color = LocalContentColor.current.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                lineHeight = 14.sp,
            )
        }
        if (onMore != null) {
            Box(
                Modifier.size(38.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onMore,
                ),
                contentAlignment = Alignment.Center,
            ) {
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.MoreVertical,
                    modifier = Modifier.size(18.dp),
                    color = LocalContentColor.current.copy(alpha = 0.38f),
                )
            }
        }
    }
}

/**
 * Sonify artist song section: 4 songs per page, 20dp / 39dp insets, same
 * snap behaviour as upstream.
 */
@Composable
fun SonifySongsPagerSection(
    songs: List<SearchSong>,
    pagerState: PagerState,
    onPlayAt: (Int) -> Unit,
    onMoreAt: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
        val startInset = 20.dp
        val endInset = 39.dp
        val pageWidth = (maxWidth - startInset - endInset).coerceAtLeast(1.dp)
        HorizontalPager(
            state = pagerState,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                pagerSnapDistance = PagerSnapDistance.atMost(Int.MAX_VALUE),
            ),
            pageSize = PageSize.Fixed(pageWidth),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = startInset, end = endInset),
            verticalAlignment = Alignment.Top,
            key = { page -> "sonify-page-$page-${songs.getOrNull(page * 4)?.id ?: -1}" },
            beyondViewportPageCount = 0,
        ) { page ->
            Column(Modifier.fillMaxWidth()) {
                repeat(4) { row ->
                    val index = page * 4 + row
                    val item = songs.getOrNull(index) ?: return@repeat
                    SonifySongRow(
                        song = item,
                        horizontalPadding = 0.dp,
                        onClick = { onPlayAt(index) },
                        onMore = onMoreAt?.let { more -> { more(index) } },
                    )
                    if (row < 3 && index + 1 < songs.size) {
                        SonifyLibraryDivider(startPadding = 66.dp, endPadding = 16.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun SonifySkeletonBlock(modifier: Modifier, cornerRadius: Dp = 6.dp) {
    Box(
        modifier.clip(RoundedCornerShape(cornerRadius))
            .background(LocalContentColor.current.copy(alpha = 0.09f)),
    )
}

@Composable
fun SonifyArtistSongsSkeleton() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        repeat(4) {
            Row(
                Modifier.fillMaxWidth().height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SonifySkeletonBlock(Modifier.size(52.dp), 3.5.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SonifySkeletonBlock(Modifier.fillMaxWidth(0.65f).height(13.dp))
                    SonifySkeletonBlock(Modifier.fillMaxWidth(0.42f).height(11.dp))
                }
            }
        }
    }
}
