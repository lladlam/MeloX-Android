/*
 * Sonify library song row (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/pages/library/MusicList.kt (master @ 209e1aa).
 * Geometry is preserved: 64dp row, 22dp horizontal padding, 52dp artwork,
 * 16sp title / 13sp artists, 3.5dp corner radius.
 *
 * The upstream SongAddToPlaylistButton is replaced by MeloX's song actions
 * overlay entry, which owns like/favorite, add-to-playlist, download, share,
 * and provider-aware actions for every MeloX source.
 */
package com.lladlam.melox.ui.sonify.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon

@Composable
internal fun SonifyMusicList(
    music: SearchSong,
    horizontalPadding: Dp = 22.dp,
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    itemClick: () -> Unit,
) {
    Row(
        Modifier
            .height(64.dp)
            .fillMaxWidth()
            .clickable(onClick = itemClick)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SonifyArtwork(
            url = music.artworkUrl,
            modifier = Modifier.size(52.dp),
            cornerRadius = 3.5.dp,
            contentDescription = null,
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                text = music.name.ifBlank { "未知歌曲" },
                modifier = Modifier.padding(bottom = 1.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                lineHeight = 16.sp,
            )
            Text(
                text = music.artists.ifBlank { "未知歌手" },
                color = LocalContentColor.current.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                lineHeight = 13.sp,
            )
        }
        if (onMore != null) {
            Box(
                Modifier
                    .size(38.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onMore,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.MoreVertical,
                    modifier = Modifier.size(18.dp).alpha(0.45f),
                    color = LocalContentColor.current,
                )
            }
        }
    }
}
