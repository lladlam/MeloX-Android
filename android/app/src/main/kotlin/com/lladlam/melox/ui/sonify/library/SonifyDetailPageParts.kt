/*
 * Sonify detail-page parts (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/pages/library/DetailPageParts.kt (master @
 * 209e1aa). Kept: Apple-Music-style wide header, compact cover header,
 * collapsible intro, album track row and the wide song row.
 *
 * MeloX replacements: ShadowImage/Coil2 -> Coil3 AsyncImage, Sonify drawables
 * -> MeloXSymbol, MediaController -> PlaybackCommands in callers.
 */
package com.lladlam.melox.ui.sonify.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.sonify.SonifyRoundActionButton
import com.lladlam.melox.ui.sonify.sonifyStatusBarsPadding

@Composable
internal fun SonifyDetailPageHeader(
    title: String,
    coverUrl: String?,
    tag: String,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    extraLines: (@Composable () -> Unit)? = null,
    countText: String? = null,
    intro: String? = null,
    accentColor: Color? = null,
    trailingSymbol: MeloXSymbol? = null,
    trailingDescription: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    BoxWithConstraints(modifier) {
        val coverSize = (maxWidth * 0.28f).coerceIn(130.dp, 300.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 9.5.dp)
                .padding(horizontal = 18.dp)
                .padding(bottom = coverSize * 0.06f + 3.dp)
                .sonifyStatusBarsPadding(),
        ) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.size(coverSize)) {
                    SonifyArtwork(
                        url = coverUrl,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 7.dp,
                        contentDescription = null,
                    )
                }
                Spacer(Modifier.width(15.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(top = 4.dp),
                ) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    extraLines?.invoke()
                    if (countText != null) {
                        Text(
                            text = countText,
                            fontSize = 17.5.sp,
                            lineHeight = 23.5.sp,
                            color = accentColor ?: LocalContentColor.current,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Text(
                        text = tag,
                        fontSize = 11.5.sp,
                        modifier = Modifier.alpha(0.4f).padding(top = 2.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    if (!intro.isNullOrBlank()) {
                        SonifyDetailIntro(
                            text = intro,
                            modifier = Modifier.padding(bottom = 10.dp),
                            maxLines = 4,
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        SonifyNormalButton(
                            symbol = MeloXSymbol.Shuffle,
                            label = "随机播放",
                            modifier = Modifier.weight(1f),
                            contentColor = accentColor,
                            containerColor = accentColor?.copy(alpha = 0.18f),
                            onClick = onShuffle,
                        )
                        Spacer(Modifier.width(15.dp))
                        SonifyNormalButton(
                            symbol = MeloXSymbol.Play,
                            label = "播放",
                            modifier = Modifier.weight(1f),
                            contentColor = accentColor,
                            containerColor = accentColor?.copy(alpha = 0.18f),
                            onClick = onPlayAll,
                        )
                        if (trailingSymbol != null && onTrailingClick != null) {
                            Spacer(Modifier.width(12.dp))
                            SonifyRoundActionButton(
                                symbol = trailingSymbol,
                                contentDescription = trailingDescription,
                                iconTint = accentColor ?: LocalContentColor.current,
                                containerColor = accentColor?.copy(alpha = 0.18f),
                                iconSize = 22.dp,
                                onClick = onTrailingClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact (phone) centered cover header used by album/playlist detail pages.
 */
@Composable
internal fun SonifyCompactDetailHeader(
    title: String,
    coverUrl: String?,
    meta: String,
    tag: String,
    playEnabled: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    intro: String? = null,
    trailingSymbol: MeloXSymbol? = null,
    trailingDescription: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 9.5.dp)
            .padding(horizontal = 18.dp)
            .sonifyStatusBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SonifyArtwork(
            url = coverUrl,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 50.dp)
                .aspectRatio(1f),
            cornerRadius = 7.dp,
            contentDescription = null,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "$meta · $tag",
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(0.6f).padding(top = 4.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SonifyNormalButton(
                symbol = MeloXSymbol.Shuffle,
                label = "随机播放",
                modifier = Modifier.weight(1f),
                onClick = { if (playEnabled) onShuffle() },
            )
            Spacer(Modifier.width(15.dp))
            SonifyNormalButton(
                symbol = MeloXSymbol.Play,
                label = "播放",
                modifier = Modifier.weight(1f),
                onClick = { if (playEnabled) onPlayAll() },
            )
            if (trailingSymbol != null && onTrailingClick != null) {
                Spacer(Modifier.width(12.dp))
                SonifyRoundActionButton(
                    symbol = trailingSymbol,
                    contentDescription = trailingDescription,
                    iconTint = LocalContentColor.current,
                    highlighted = false,
                    iconSize = 22.dp,
                    onClick = onTrailingClick,
                )
            }
        }
        if (!intro.isNullOrBlank()) {
            SonifyDetailIntro(
                text = intro,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 12.dp),
            )
        }
    }
}

private const val DetailIntroMore = "…更多"
private const val DetailIntroLess = "收起"

@Composable
internal fun SonifyDetailIntro(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(
            text = text,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = LocalContentColor.current.copy(alpha = 0.55f),
            maxLines = if (expanded) Int.MAX_VALUE else maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (expanded) DetailIntroLess else DetailIntroMore,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = LocalContentColor.current,
            modifier = Modifier.clickable(role = Role.Button) { expanded = !expanded },
        )
    }
}

@Composable
internal fun SonifyDetailSongRowWide(
    music: SearchSong,
    onMore: (() -> Unit)? = null,
    itemClick: () -> Unit,
) {
    Row(
        Modifier
            .height(64.dp)
            .fillMaxWidth()
            .clickable(onClick = itemClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.fillMaxWidth(0.5f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SonifyArtwork(
                url = music.artworkUrl,
                modifier = Modifier.size(52.dp),
                cornerRadius = 3.5.dp,
            )
            Text(
                text = music.name,
                modifier = Modifier.padding(start = 16.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                lineHeight = 16.sp,
            )
        }
        Box(Modifier.weight(1f)) {
            Text(
                text = music.artists,
                modifier = Modifier.alpha(0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                lineHeight = 13.sp,
            )
        }
        if (music.durationMs > 0) {
            Text(
                text = sonifyFormatDuration(music.durationMs),
                modifier = Modifier.alpha(0.5f).padding(start = 12.dp),
                fontSize = 13.sp,
                lineHeight = 13.sp,
            )
        }
        if (onMore != null) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.MoreVertical,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp)
                    .alpha(0.45f)
                    .clickable(role = Role.Button, onClick = onMore),
                color = LocalContentColor.current,
            )
        }
    }
}

@Composable
internal fun SonifyAlbumTrackRow(
    music: SearchSong,
    trackNumber: Int?,
    mainArtists: List<String>,
    modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null,
    itemClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = itemClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.width(24.dp).fillMaxHeight(),
        ) {
            Text(
                text = "${trackNumber ?: "-"}",
                fontSize = 16.sp,
                modifier = Modifier.alpha(0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = music.name,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
            )
            if (mainArtists.isEmpty() || mainArtists.none { music.artists.contains(it, ignoreCase = true) }) {
                Text(
                    text = music.artists,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                    modifier = Modifier.alpha(0.4f),
                )
            }
        }
        if (onMore != null) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.MoreVertical,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp)
                    .alpha(0.45f)
                    .clickable(role = Role.Button, onClick = onMore),
                color = LocalContentColor.current,
            )
        }
    }
}

internal fun sonifyFormatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
internal fun SonifyAlbumDivider(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 16.dp)
            .alpha(0.2f)
            .height(0.5.dp)
            .background(LocalContentColor.current),
    )
}

internal val SonifyCoverArtworkTopPadding: Dp = 9.5.dp
