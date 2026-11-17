/*
 * Sonify artist-detail parts (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/pages/library/artists/ArtistDetailParts.kt
 * (master @ 209e1aa). Section headers, skeleton rows, 4-per-page song pager,
 * the 150dp album rail and the latest-album card keep their upstream metrics.
 * KugouNewSong/KugouNewAlbum become SearchSong/MeloXAlbumSummary; Sonify
 * drawables and shared-element cover transitions are MeloX-native.
 */
package com.lladlam.melox.ui.sonify.artist

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.R
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXAlbumSummary
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.sonify.SonifyLibraryDivider
import com.lladlam.melox.ui.sonify.SonifyRoundedCornerShape
import com.lladlam.melox.ui.sonify.SonifySongRow
import com.lladlam.melox.ui.sonify.isSonifyDark

internal data class ArtistPagingSnapshot(
    val current: Int,
    val count: Int,
    val page: Int,
    val loading: Boolean,
    val error: String?,
    val endReached: Boolean
)

@Composable
internal fun ArtistSectionHeader(title: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp)
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            MeloXSymbolIcon(
                symbol = MeloXSymbol.ChevronRight,
                modifier = Modifier.size(20.dp).alpha(0.35f),
                color = LocalContentColor.current
            )
        }
    }
}

@Composable
internal fun ArtistSectionError(error: String, busy: Boolean = false, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)) {
        Text(error, fontSize = 15.sp, modifier = Modifier.alpha(0.65f))
        Spacer(Modifier.height(8.dp))
        Text(
            androidx.compose.ui.res.stringResource(R.string.sonify_artist_retry),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(enabled = !busy, role = Role.Button, onClick = onRetry)
                .padding(vertical = 6.dp).alpha(if (busy) 0.45f else 1f)
        )
    }
}

@Composable
private fun ArtistEmptyStatus(text: String) {
    Text(text, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp).alpha(0.6f))
}

@Composable
internal fun ArtistSongsError(
    message: String,
    onRetry: () -> Unit,
    compact: Boolean = false
) {
    Column(Modifier.padding(horizontal = 22.dp, vertical = if (compact) 8.dp else 10.dp)) {
        Text(
            text = message,
            fontSize = if (compact) 14.sp else 18.sp,
            modifier = Modifier.alpha(0.6f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.sonify_artist_retry),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onRetry)
        )
    }
}

@Composable
internal fun ArtistListFooterLoading() {
    Text(
        text = androidx.compose.ui.res.stringResource(R.string.melox_state_loading),
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .wrapContentWidth(Alignment.CenterHorizontally)
            .alpha(0.5f)
    )
}

/** 内容与骨架的交叉淡化；MeloX 没有 Sonify 的 FadeInContent 页面助手。 */
@Composable
internal fun ArtistFadeInContent(
    hasData: Boolean,
    skeleton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Crossfade(targetState = hasData, animationSpec = tween(180), label = "artistContentFade") { show ->
        if (show) content() else skeleton()
    }
}

@Composable
internal fun ArtistSongSection(
    songs: List<SearchSong>,
    loading: Boolean,
    error: String?,
    pagerState: PagerState,
    onRetry: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onMoreAt: ((Int) -> Unit)? = null,
    probeName: String = ""
) {
    Column(Modifier.fillMaxWidth()) {
        ArtistFadeInContent(
            hasData = songs.isNotEmpty(),
            skeleton = {
                when {
                    loading -> ArtistSongsSkeleton()
                    error == null -> ArtistEmptyStatus(
                        androidx.compose.ui.res.stringResource(R.string.sonify_artist_empty)
                    )
                }
            }
        ) {
            SongsPagerSection(songs, pagerState, onPlayAt, onMoreAt)
        }
        if (error != null) ArtistSectionError(error, loading, onRetry)
        if (loading && songs.isNotEmpty()) {
            Text(
                androidx.compose.ui.res.stringResource(R.string.melox_state_loading),
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp).alpha(0.5f)
            )
        }
    }
}

@Composable
internal fun SongsPagerSection(
    songs: List<SearchSong>,
    pagerState: PagerState,
    onPlayAt: (Int) -> Unit,
    onMoreAt: ((Int) -> Unit)? = null
) {
    val pageKeys = remember(songs) {
        songs.chunked(4).mapIndexed { index, column ->
            "artist-page-$index-${column.first().id}"
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val startInset = 20.dp
        val endInset = 39.dp
        val pageWidth = (maxWidth - startInset - endInset).coerceAtLeast(1.dp)
        HorizontalPager(
            state = pagerState,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                pagerSnapDistance = PagerSnapDistance.atMost(Int.MAX_VALUE)
            ),
            pageSize = PageSize.Fixed(pageWidth),
            contentPadding = PaddingValues(start = startInset, end = endInset),
            verticalAlignment = Alignment.Top,
            key = { page -> pageKeys.getOrElse(page) { "artist-page-$page-empty" } },
            beyondViewportPageCount = 0
        ) { page ->
            Column(Modifier.fillMaxWidth()) {
                repeat(4) { row ->
                    val index = page * 4 + row
                    val item = songs.getOrNull(index) ?: return@repeat
                    Box(Modifier.fillMaxWidth()) {
                        SonifySongRow(
                            song = item,
                            horizontalPadding = 0.dp,
                            onClick = { onPlayAt(index) },
                            onMore = onMoreAt?.let { more -> { more(index) } }
                        )
                    }
                    if (row < 3 && index + 1 < songs.size) {
                        SonifyLibraryDivider(startPadding = 66.dp, endPadding = 16.dp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ArtistSongsSkeleton() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        repeat(4) {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtistSkeletonBlock(Modifier.size(52.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArtistSkeletonBlock(Modifier.fillMaxWidth(0.65f).height(13.dp))
                    ArtistSkeletonBlock(Modifier.fillMaxWidth(0.42f).height(11.dp))
                }
            }
        }
    }
}

@Composable
internal fun ArtistHeaderSkeleton() {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ArtistSkeletonBlock(Modifier.width(150.dp).height(14.dp))
        ArtistSkeletonBlock(Modifier.width(108.dp).height(10.dp))
    }
}

@Composable
internal fun ArtistSkeletonBlock(modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LocalContentColor.current.copy(alpha = 0.09f))
    )
}

internal val ArtistAlbumRailCardWidth = 150.dp
internal val ArtistAlbumRailCardGap = 12.dp
internal val ArtistAlbumRailPageSize = ArtistAlbumRailCardWidth + ArtistAlbumRailCardGap
internal val ArtistAlbumRailSnapDistance: PagerSnapDistance = PagerSnapDistance.atMost(Int.MAX_VALUE)

@Composable
internal fun ArtistAlbumsSection(
    albums: List<MeloXAlbumSummary>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpen: (MeloXAlbumSummary) -> Unit,
    probeName: String = ""
) {
    Column(Modifier.fillMaxWidth()) {
        ArtistFadeInContent(
            hasData = albums.isNotEmpty(),
            skeleton = {
                when {
                    loading -> ArtistAlbumRailSkeleton()
                    error == null -> ArtistEmptyStatus(
                        androidx.compose.ui.res.stringResource(R.string.sonify_artist_no_albums)
                    )
                }
            }
        ) {
            ArtistAlbumRail(albums = albums, onOpen = onOpen)
        }
        if (error != null) ArtistSectionError(error, loading, onRetry)
    }
}

@Composable
private fun ArtistAlbumRail(
    albums: List<MeloXAlbumSummary>,
    onOpen: (MeloXAlbumSummary) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { albums.size })
    HorizontalPager(
        state = pagerState,
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            pagerSnapDistance = ArtistAlbumRailSnapDistance
        ),
        pageSize = PageSize.Fixed(ArtistAlbumRailPageSize),
        contentPadding = PaddingValues(horizontal = 20.dp),
        verticalAlignment = Alignment.Top,
        key = { page -> "artist-album-$page-${albums[page].id}" },
        beyondViewportPageCount = 0
    ) { page ->
        val album = albums[page]
        ArtistAlbumRailCard(
            album = album,
            modifier = Modifier.width(ArtistAlbumRailCardWidth)
        ) { onOpen(album) }
        Spacer(Modifier.width(ArtistAlbumRailCardGap))
    }
}

@Composable
private fun ArtistAlbumRailCard(
    album: MeloXAlbumSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(modifier.clickable(role = Role.Button, onClick = onClick)) {
        AsyncImage(
            model = album.artworkUrl,
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(SonifyRoundedCornerShape(10.dp))
        )
        Spacer(Modifier.height(7.dp))
        Text(
            album.name,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            album.artistText,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = LocalContentColor.current.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun ArtistAlbumRailSkeleton() {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            Column(Modifier.width(ArtistAlbumRailCardWidth)) {
                ArtistSkeletonBlock(Modifier.fillMaxWidth().aspectRatio(1f))
                Spacer(Modifier.height(8.dp))
                ArtistSkeletonBlock(Modifier.fillMaxWidth(0.75f).height(13.dp))
            }
        }
    }
}

/** 推荐专辑卡片：半透明白圆角容器 + 顶部亮、底部收的白色渐变描边。 */
@Composable
internal fun ArtistLatestAlbumCard(
    album: MeloXAlbumSummary,
    underlayColor: Color,
    onOpen: (MeloXAlbumSummary) -> Unit
) {
    val isDark = isSonifyDark()
    val shape = SonifyRoundedCornerShape(22.dp)
    val lightCard = underlayColor.luminance() > 0.179f
    val secondaryText = if (lightCard) Color(0xFF161616).copy(alpha = 0.55f)
    else Color.White.copy(alpha = 0.45f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .shadow(8.dp, shape, clip = false)
            .clip(shape)
            .background(underlayColor)
            .background(Color.White.copy(alpha = if (isDark) 0.12f else 0.22f))
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.15f))
                ),
                shape = shape
            )
            .clickable(role = Role.Button) { onOpen(album) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = album.artworkUrl,
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(92.dp).clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            album.type?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 12.5.sp, lineHeight = 16.sp, color = secondaryText)
            }
            Text(
                album.name,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (album.artistText.isNotBlank()) {
                Text(
                    album.artistText,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 专辑大全页网格卡片。 */
@Composable
internal fun ArtistAlbumGridCard(
    album: MeloXAlbumSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(modifier.clickable(role = Role.Button, onClick = onClick)) {
        AsyncImage(
            model = album.artworkUrl,
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(SonifyRoundedCornerShape(10.dp))
        )
        Spacer(Modifier.height(7.dp))
        Text(
            album.name,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            album.artistText,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = LocalContentColor.current.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
