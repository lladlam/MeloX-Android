/*
 * Sonify Home page (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/pages/Home.kt (master @ 209e1aa). The large
 * title, fixed-width 278dp recommendation pager, 268x354 card, extracted
 * colour mask and its typography are preserved.
 *
 * Data/playback replacement:
 *   MusicLibrary.songs / MediaController.prepare
 *     -> List<MusicTrack> from MeloX HomeBlock + PlaybackCommands.
 *   ImageViewModel.recommendMusicList
 *     -> page-local remember state seeded from the MeloX track list.
 *
 * The mask bitmap uses the same saturation/darkening recipe as Sonify's
 * imageResolve. The upstream 25px RenderScript blur is replaced with a
 * downscale/upscale box blur because MeloX does not ship RenderScript and the
 * mask is only a 192px background wash.
 */
package com.lladlam.melox.ui.sonify

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.toBitmap
import com.lladlam.melox.R
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private val recommendMaskCache = ConcurrentHashMap<String, Bitmap>()

/**
 * MeloX entry point for the Sonify Home appearance.
 *
 * This is the migrated Sonify Discovery.kt feed structure: a large title,
 * a recommendation pager followed by every MeloX HomeBlock in its configured
 * order, rendered as Sonify song columns, card rails and podcast rails.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SonifyHomePage(
    sections: List<SonifyHomeSection>,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    accountContent: @Composable () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val content: @Composable () -> Unit = {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 146.dp),
        ) {
            item("HomeHeader") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "主页",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.2).sp,
                    )
                    Spacer(Modifier.weight(1f))
                    accountContent()
                }
            }
            sections.forEach { section ->
                when (section) {
                    is SonifyHomeSection.Recommend -> item("RecommendCard") {
                        SonifyRecommendCard(
                            title = section.title,
                            subtitle = section.subtitle,
                            musicList = section.tracks,
                            onPlay = section.onPlay,
                        )
                    }

                    is SonifyHomeSection.Songs -> item("Songs:${section.title}") {
                        SonifyHomeSectionHeader(section.title)
                        if (section.songs.isEmpty()) {
                            Text(
                                stringResource(R.string.melox_state_loading),
                                Modifier.padding(horizontal = 20.dp),
                                fontSize = 13.sp,
                                color = LocalContentColor.current.copy(alpha = 0.45f),
                            )
                        } else {
                            val pagerState = rememberPagerState(
                                pageCount = { (section.songs.size + 3) / 4 },
                            )
                            SonifySongsPagerSection(
                                songs = section.songs,
                                pagerState = pagerState,
                                onPlayAt = section.onPlay,
                            )
                        }
                    }

                    is SonifyHomeSection.Collections -> item("Collections:${section.title}") {
                        SonifyHomeSectionHeader(section.title)
                        SonifyCollectionRail(section.items)
                    }

                    is SonifyHomeSection.Podcasts -> item("Podcasts:${section.title}") {
                        SonifyHomeSectionHeader(section.title)
                        SonifyPodcastRail(section.items)
                    }
                }
            }
        }
    }
    if (onRefresh != null) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) { content() }
    } else {
        content()
    }
}

@Composable
private fun SonifyHomeSectionHeader(title: String) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SonifyCollectionRail(items: List<SonifyHomeCollection>) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })
    HorizontalPager(
        state = pagerState,
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            pagerSnapDistance = PagerSnapDistance.atMost(Int.MAX_VALUE),
        ),
        pageSize = PageSize.Fixed(150.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
        pageSpacing = 10.dp,
        key = { items[it].key },
        beyondViewportPageCount = 1,
    ) { page ->
        SonifyCollectionCard(items[page])
    }
}

@Composable
private fun SonifyCollectionCard(item: SonifyHomeCollection) {
    Column(
        Modifier
            .width(150.dp)
            .clickable(onClick = item.onClick),
    ) {
        AsyncImage(
            model = item.artworkUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(7.dp)),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = item.title,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(0.9f),
        )
        if (item.subtitle.isNotBlank()) {
            Text(
                text = item.subtitle,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(0.6f),
            )
        }
    }
}

@Composable
private fun SonifyPodcastRail(items: List<SonifyHomePodcast>) {
    if (items.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.key }) { item ->
            Column(
                Modifier
                    .width(150.dp)
                    .clickable(onClick = item.onClick),
            ) {
                AsyncImage(
                    model = item.artworkUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(7.dp)),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(0.9f),
                )
            }
        }
    }
}

@Composable
private fun SonifyRecommendCard(
    title: String,
    subtitle: String,
    musicList: List<MusicTrack>,
    onPlay: (MusicTrack, List<MusicTrack>) -> Unit,
) {
    if (musicList.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { musicList.size })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(278.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 136.dp),
            key = { musicList[it].id.value },
            beyondViewportPageCount = 1,
        ) { page ->
            val music = musicList[page]
            SonifyRecommendCardItem(
                subTitle = subtitle,
                music = music,
                onClick = { scope.launch { onPlay(music, musicList) } },
            )
        }
    }
}

@Composable
private fun SonifyRecommendCardItem(
    subTitle: String,
    music: MusicTrack,
    onClick: () -> Unit,
) {
    Column(Modifier.width(268.dp)) {
        val context = LocalContext.current
        var maskBitmap by remember(music.artworkUrl) { mutableStateOf<Bitmap?>(null) }

        LaunchedEffect(music.artworkUrl) {
            val artwork = music.artworkUrl ?: return@LaunchedEffect
            maskBitmap = withContext(Dispatchers.IO) {
                recommendMaskCache[artwork] ?: runCatching {
                    delay(200)
                    val request = ImageRequest.Builder(context)
                        .data(artwork)
                        .size(192)
                        .bitmapConfig(Bitmap.Config.ARGB_8888)
                        .build()
                    val loaded = context.imageLoader.execute(request).image?.toBitmap()
                        ?: return@runCatching null
                    val mask = sonifyImageResolve(BitmapResolver.bitmapCompress(loaded, lowQuality = true))
                    recommendMaskCache[artwork] = mask
                    mask
                }.getOrNull()
            }
        }

        if (subTitle.isNotEmpty()) {
            Text(
                text = subTitle,
                fontSize = 15.sp,
                lineHeight = 15.sp,
                modifier = Modifier.alpha(0.6f),
            )
        }

        val shape = SonifyRoundedCornerShape(14.dp)
        val density = LocalDensity.current
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = if (subTitle.isNotEmpty()) 14.dp else 0.dp)
                .height(354.dp)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    clip = true
                    this.shape = shape
                }
                .drawWithCache {
                    val outline = shape.createOutline(
                        Size(size.width, size.height),
                        LayoutDirection.Ltr,
                        density,
                    )
                    onDrawWithContent {
                        drawContent()
                        drawOutline(
                            outline = outline,
                            color = Color.DarkGray.copy(alpha = 0.16f),
                            style = Stroke(width = 8f),
                        )
                    }
                }
                .clickable(onClick = onClick),
        ) {
            val imgContext = LocalContext.current
            AsyncImage(
                model = remember(music.artworkUrl) {
                    ImageRequest.Builder(imgContext)
                        .data(music.artworkUrl)
                        .size(540)
                        .crossfade(true)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(268.dp),
            )

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black,
                contentColor = Color.White,
            ) {
                maskBitmap?.let { bitmap ->
                    val imgContext = LocalContext.current
                    AsyncImage(
                        model = remember(bitmap) {
                            ImageRequest.Builder(imgContext)
                                .data(bitmap)
                                .crossfade(true)
                                .build()
                        },
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxSize(),
                        colorFilter = ColorFilter.tint(Color(0x33000000), BlendMode.Overlay),
                    )
                }

                Column(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = music.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = music.artistText,
                        fontSize = 13.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.alpha(0.6f).padding(top = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = music.album?.name.orEmpty(),
                        fontSize = 13.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.alpha(0.6f).padding(top = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * MeloX-native equivalent of Sonify's imageResolve(): desaturate-safe
 * saturation boost, dark overlay, and a cheap downscale box blur. The bitmap
 * is small (≤96px) so this is a few milliseconds.
 */
private fun sonifyImageResolve(source: Bitmap): Bitmap {
    val width = source.width.coerceAtLeast(1)
    val height = source.height.coerceAtLeast(1)
    val work = if (source.config == Bitmap.Config.ARGB_8888) {
        source.copy(Bitmap.Config.ARGB_8888, true)
    } else {
        source.copy(Bitmap.Config.ARGB_8888, true)
    }
    Canvas(work).apply {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(3f) })
        drawBitmap(work, 0f, 0f, paint)
        drawColor(AndroidColor.argb(0x33, 0, 0, 0), PorterDuff.Mode.OVERLAY)
        drawColor(AndroidColor.argb(0x40, 0, 0, 0))
    }
    val smallW = (width / 4).coerceAtLeast(1)
    val smallH = (height / 4).coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(work, smallW, smallH, true)
    val blurred = Bitmap.createScaledBitmap(small, width, height, true)
    if (small !== work) small.recycle()
    work.recycle()
    return blurred
}
