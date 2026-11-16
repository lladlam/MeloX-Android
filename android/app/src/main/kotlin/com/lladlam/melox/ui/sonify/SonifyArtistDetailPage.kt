/*
 * Sonify ArtistDetail page (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/pages/library/artists/ArtistDetail.kt (master
 * @ 209e1aa) plus ArtistDetailParts.kt, ArtistActionButtons.kt,
 * ArtistHeroReflection.kt and HeroBakeCache.kt.
 *
 * Preserved from the original:
 *   - full-bleed hero with bottom-weighted artwork palette;
 *   - "photo → extracted colour" vertical stretch reflection baked off-thread;
 *   - inline intro expansion, i / play / follow action row;
 *   - 20-song hot rail, latest-album card, album rail and all-song pager,
 *     4 songs per page with the same 20dp / 39dp insets;
 *   - progressive-blur top bar and small title on scroll.
 *
 * MeloX replacements:
 *   KugouRepository / ArtistSongsObject / MediaController
 *     -> MeloXArtistDetail + SearchSong + PlaybackCommands, supplied by
 *        MeloXArtistDetailScreen.
 *   OnlineAlbumObject / NavController
 *     -> onOpenAlbum callback into MeloXUnifiedAlbumDetailScreen.
 *   Sonify drawables
 *     -> MeloXSymbol font icons.
 */
package com.lladlam.melox.ui.sonify

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.toBitmap
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.lladlam.melox.R
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXAlbumSummary
import com.lladlam.melox.core.network.MeloXArtistDetail
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val HeroPhoneHeightFraction = 0.45f
private const val HeroTabletMaxHeightFraction = 0.75f
private const val HeroTabletReflectionBandMaxDp = 260f
private const val HeroTabletContentRaiseDp = 40f
private const val HeroBakeMaxWidthPx = 1024f
private val HeroBakeGate = Mutex()

@Composable
fun SonifyArtistDetailPage(
    artist: MeloXArtistDetail?,
    followed: Boolean?,
    allSongs: List<SearchSong>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onPlay: (List<SearchSong>, Int) -> Unit,
    onMoreSong: (SearchSong) -> Unit,
    onOpenAlbum: (MeloXAlbumSummary) -> Unit,
    onToggleFollow: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val isDark = isSonifyDark()
    ArtistPresentationCache.initialize(app)

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.toFloat()
    val screenHeightDp = configuration.screenHeightDp.toFloat()
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val landscapeTablet = isTablet && screenWidthDp > screenHeightDp
    val portraitTablet = isTablet && screenWidthDp <= screenHeightDp
    val isLandscape = screenWidthDp > screenHeightDp
    val heroAspect = when {
        portraitTablet -> screenWidthDp /
            (minOf(screenWidthDp, screenHeightDp * HeroTabletMaxHeightFraction) - HeroTabletContentRaiseDp)
                .coerceAtLeast(200f)
        isLandscape -> 1f
        else -> screenWidthDp / (screenHeightDp * HeroPhoneHeightFraction).coerceAtLeast(200f)
    }

    val artistName = artist?.name?.takeIf { it.isNotBlank() } ?: "未知歌手"
    val avatarUrl = artist?.artworkUrl?.takeIf { it.isNotBlank() }
        ?: artist?.coverUrl?.takeIf { it.isNotBlank() }
    val avatarAbsent = artist != null && avatarUrl == null
    var heroImageFailed by remember(avatarUrl) { mutableStateOf(false) }
    val noUsableAvatar = avatarAbsent || heroImageFailed
    val identitySeed = artist?.id?.toString()?.takeIf { it.isNotBlank() } ?: artistName
    val identityPaletteKey = "artist-identity:$identitySeed#$heroAspect"
    val avatarPaletteKey = avatarUrl?.let { "$it#bottom-weighted-v2-128-$heroAspect" }
    val paletteKey = avatarPaletteKey ?: identityPaletteKey
    val paletteState = remember(paletteKey) { ArtistPresentationCache.palette(paletteKey) }
    val identityRgb = remember(identitySeed, noUsableAvatar, isDark) {
        if (noUsableAvatar) artistIdentityRgb(identitySeed, isDark) else null
    }
    val originalRgb = paletteState.value ?: identityRgb
    val artworkColors = remember(originalRgb, isDark) { ArtistArtworkColors.fromRgb(originalRgb, isDark) }
    val hasResolvedArtwork = originalRgb != null
    val bgColorState = animateColorAsState(
        targetValue = artworkColors.accent,
        animationSpec = if (hasResolvedArtwork) tween(600) else tween(0),
        label = "sonifyArtistBg",
    )
    val contentColor = if (hasResolvedArtwork) artworkColors.content
    else if (isDark) Color.White else Color(0xFF161616)

    LaunchedEffect(identityPaletteKey, identityRgb) {
        if (identityRgb != null) {
            ArtistPresentationCache.seedPalette(identityPaletteKey, identityRgb)
        }
    }

    val heroBitmap = remember(avatarUrl) { mutableStateOf<Bitmap?>(null) }
    var heroRevealed by remember(avatarUrl) { mutableStateOf(hasResolvedArtwork) }
    LaunchedEffect(avatarUrl, heroAspect, noUsableAvatar) {
        if (avatarUrl == null || heroImageFailed) {
            if (noUsableAvatar) heroRevealed = true
            return@LaunchedEffect
        }
        val bitmap = runCatching {
            context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                    .size(1024)
                    .memoryCacheKey("artist-hero:$avatarUrl")
                    .diskCacheKey(avatarUrl)
                    .build()
            ).image?.toBitmap()
        }.getOrNull()
        if (bitmap == null) {
            heroImageFailed = true
            heroRevealed = true
            return@LaunchedEffect
        }
        heroBitmap.value = bitmap
        runCatching {
            ArtistPresentationCache.loadPalette(paletteKey) {
                withContext(Dispatchers.Default) {
                    val sampleSrc = if (bitmap.width > 128) Bitmap.createScaledBitmap(
                        bitmap,
                        128,
                        (128f * bitmap.height / bitmap.width).roundToInt().coerceAtLeast(1),
                        false,
                    ) else bitmap
                    sampleArtistBottomColor(sampleSrc, heroAspect)
                }
            }
        }
        artist?.id?.takeIf { it > 0 }?.let {
            ArtistPresentationCache.rememberArtistPalette(it.toString(), heroAspect, paletteKey)
        }
        heroRevealed = true
    }

    val songs = allSongs.ifEmpty { artist?.hotSongs.orEmpty() }
    val hotSongs = remember(artist?.hotSongs, songs) {
        (artist?.hotSongs.orEmpty().ifEmpty { songs }).take(20)
    }
    val albums = artist?.albums.orEmpty()
    val latestAlbum = remember(albums) { albums.firstOrNull() }

    val pagerState = rememberPagerState(pageCount = { (songs.size + 3) / 4 })
    val hotPagerState = rememberPagerState(pageCount = { (hotSongs.size + 3) / 4 })
    val listState = rememberLazyListState()
    val heroScrolledOut = remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "Hero" }
            info == null || info.offset + info.size <= 0
        }
    }
    val smallTitleAlpha by animateFloatAsState(
        targetValue = if (heroScrolledOut.value) 1f else 0f,
        animationSpec = tween(150),
        label = "sonifyArtistSmallTitle",
    )
    val barVisible by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val pageBackdrop = rememberLayerBackdrop()
    var introOpen by remember(artist?.id) { mutableStateOf(false) }
    var expandedSection by remember(artist?.id) { mutableStateOf<String?>(null) }
    BackHandler(enabled = expandedSection != null) { expandedSection = null }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val heroHeight = when {
                portraitTablet -> minOf(maxWidth, maxHeight * HeroTabletMaxHeightFraction) - HeroTabletContentRaiseDp.dp
                isLandscape -> minOf(maxHeight * 0.62f, maxWidth * 0.72f).coerceAtLeast(220.dp)
                else -> maxHeight * HeroPhoneHeightFraction
            }.coerceAtLeast(200.dp)
            val heroMaxWidth = maxWidth
            val bandHeight = if (portraitTablet) {
                minOf(heroHeight * ArtistReflectionFraction, HeroTabletReflectionBandMaxDp.dp)
            } else {
                heroHeight * ArtistReflectionFraction
            }
            val density = LocalDensity.current
            val heroHeightPx = with(density) { heroHeight.toPx() }
            val bandHeightPx = with(density) { bandHeight.toPx() }
            val bakeWidthPx = with(density) { heroMaxWidth.toPx() }
            val maxBlurPx = with(density) { ArtistReflectionMaxBlurDp.dp.toPx() }
            val bakeScale = (HeroBakeMaxWidthPx / bakeWidthPx).coerceAtMost(1f)
            val bakeW = (bakeWidthPx * bakeScale).roundToInt().coerceAtLeast(1)
            val bakePhotoH = (heroHeightPx * bakeScale).roundToInt().coerceAtLeast(1)
            val bakeBandH = (bandHeightPx * bakeScale).roundToInt().coerceAtLeast(1)
            val bakeBlurPx = maxBlurPx * bakeScale
            val bakeCacheKey = remember(avatarUrl, heroAspect, bakeW, bakePhotoH, bakeBandH) {
                avatarUrl?.let {
                    "v$ArtistHeroBakeRecipeVersion|$it|$heroAspect|" +
                        "s$ArtistReflectionStretch|${bakeW}x${bakePhotoH}x${bakeBandH}"
                }
            }
            var bakedHero by remember(avatarUrl) {
                mutableStateOf(bakeCacheKey?.let(HeroBakeCache::memoryGet))
            }

            LaunchedEffect(avatarUrl, bakeWidthPx, heroHeightPx, bandHeightPx, heroBitmap.value) {
                if (isLandscape) return@LaunchedEffect
                if (avatarUrl == null || heroImageFailed) return@LaunchedEffect
                val cacheKey = bakeCacheKey ?: return@LaunchedEffect
                HeroBakeCache.memoryGet(cacheKey)?.let {
                    bakedHero = it
                    return@LaunchedEffect
                }
                withContext(Dispatchers.IO) { HeroBakeCache.load(context, cacheKey) }?.let {
                    bakedHero = it
                    return@LaunchedEffect
                }
                val src = heroBitmap.value ?: return@LaunchedEffect
                val shader = newArtistHeroReflectionShader(bakeW, bakePhotoH, bakeBandH, bakeBlurPx)
                val baked = HeroBakeGate.withLock {
                    currentCoroutineContext().ensureActive()
                    withContext(Dispatchers.Default) {
                        runCatching {
                            bakeHeroReflection(src, bakeW, bakePhotoH, bakeBandH, bakeBlurPx, shader)
                        }.getOrNull()
                    }
                } ?: return@LaunchedEffect
                HeroBakeCache.put(context, cacheKey, baked)
                bakedHero = baked.asImageBitmap()
            }

            Box(Modifier.fillMaxSize().background(bgColorState.value)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .layerBackdrop(pageBackdrop)
                        .background(bgColorState.value),
                ) {
                if (!isLandscape && !heroScrolledOut.value && bakedHero != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(heroHeight + bandHeight)
                            .graphicsLayer {
                                translationY = (heroOffsetInViewport(listState) ?: 0).toFloat()
                            }
                            .drawWithContent {
                                val offset = heroOffsetInViewport(listState)
                                val scrollAlpha = if (offset == null) 0f
                                else (((offset + heroHeightPx) / bandHeightPx).coerceIn(0f, 1f))
                                bakedHero?.let {
                                    drawImage(
                                        it,
                                        alpha = scrollAlpha,
                                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                                    )
                                }
                            },
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical(allowTopOverscroll = false),
                    contentPadding = if (isLandscape) PaddingValues(top = 54.dp) else PaddingValues(0.dp),
                    overscrollEffect = null,
                    flingBehavior = rememberOverscrollFlingBehavior { listState },
                ) {
                    item("Hero") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(heroHeight)
                                .graphicsLayer { alpha = if (heroRevealed) 1f else 0f },
                        ) {
                            if (noUsableAvatar) {
                                ArtistMonogramHero(
                                    name = artistName,
                                    background = bgColorState.value,
                                    content = contentColor,
                                )
                            } else if (bakedHero == null) {
                                AsyncImage(
                                    model = remember(avatarUrl) {
                                        ImageRequest.Builder(context)
                                            .data(avatarUrl)
                                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                                            .size(1024)
                                            .memoryCacheKey("artist-hero:$avatarUrl")
                                            .diskCacheKey(avatarUrl)
                                            .build()
                                    },
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    onError = { heroImageFailed = true },
                                )
                            }
                            Text(
                                text = artistName,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (noUsableAvatar) contentColor else Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = 32f)),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 24.dp)
                                    .padding(bottom = 8.dp),
                            )
                        }
                    }

                    item("Actions") {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val roundContainer = contentColor.copy(alpha = 0.18f)
                                if (!artist?.description.isNullOrBlank()) {
                                    SonifyRoundActionButton(
                                        symbol = MeloXSymbol.Info,
                                        contentDescription = stringResource(id = R.string.sonify_artist_intro),
                                        iconTint = if (introOpen) Color.White else contentColor,
                                        highlighted = introOpen,
                                        iconSize = 28.dp,
                                        containerColor = roundContainer,
                                        onClick = { introOpen = !introOpen },
                                    )
                                    Spacer(Modifier.width(26.dp))
                                }
                                SonifyPlayButton(
                                    enabled = songs.isNotEmpty(),
                                    contentColor = contentColor,
                                    onClick = {
                                        if (songs.isNotEmpty()) onPlay(songs, 0)
                                    },
                                )
                                Spacer(Modifier.width(26.dp))
                                SonifyRoundActionButton(
                                    symbol = if (followed == true) MeloXSymbol.Star else MeloXSymbol.Star,
                                    contentDescription = stringResource(
                                        id = if (followed == true) R.string.sonify_artist_following
                                        else R.string.sonify_artist_follow,
                                    ),
                                    iconTint = if (followed == true) {
                                        if (isDark) Color(0xFFFF453A) else Color(0xFFE53935)
                                    } else contentColor,
                                    variant = if (followed == true) MeloXSymbolVariant.Fill else MeloXSymbolVariant.Regular,
                                    containerColor = roundContainer,
                                    enabled = !loading,
                                    onClick = onToggleFollow,
                                )
                            }
                        }

                    item("HeaderStatus") {
                        Column(Modifier.fillMaxWidth()) {
                            if (loading && artist == null) {
                                SonifyArtistSongsSkeleton()
                            } else if (artist != null) {
                                val lightBackdrop = bgColorState.value.luminance() > 0.179f
                                Text(
                                    text = "${artist.musicSize.coerceAtLeast(songs.size)} 首 · ${artist.albumSize.coerceAtLeast(albums.size)} 张专辑",
                                    fontSize = 14.sp,
                                    color = if (lightBackdrop) Color(0xFF161616).copy(alpha = 0.55f)
                                    else Color.White.copy(alpha = 0.45f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                )
                            }
                            error?.let {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)) {
                                    Text(it, fontSize = 15.sp, modifier = Modifier.alpha(0.65f))
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.sonify_artist_retry),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable(onClick = onRetry)
                                            .padding(vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }

                    item("Intro") {
                        AnimatedVisibility(
                            visible = introOpen && !artist?.description.isNullOrBlank(),
                            enter = expandVertically(tween(260)) + fadeIn(tween(200)),
                            exit = shrinkVertically(tween(220)) + fadeOut(tween(160)),
                        ) {
                            val intro = artist?.description
                            if (!intro.isNullOrBlank()) {
                                Column(Modifier.fillMaxWidth()) {
                                    SonifyArtistSectionHeader(stringResource(R.string.sonify_artist_intro))
                                    val lightBackdrop = bgColorState.value.luminance() > 0.179f
                                    Text(
                                        text = intro,
                                        fontSize = 14.sp,
                                        lineHeight = 22.sp,
                                        color = if (lightBackdrop) Color(0xFF161616).copy(alpha = 0.55f)
                                        else Color.White.copy(alpha = 0.45f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp)
                                            .padding(bottom = 6.dp),
                                    )
                                }
                            }
                        }
                    }

                    latestAlbum?.let { album ->
                        item("LatestAlbum") {
                            SonifyLatestAlbumCard(
                                album = album,
                                underlayColor = bgColorState.value,
                                onClick = { onOpenAlbum(album) },
                            )
                        }
                    }

                    if (hotSongs.isNotEmpty()) {
                        item("HotHeader") {
                            SonifyArtistSectionHeader(
                                title = stringResource(R.string.sonify_artist_hot_songs),
                                onClick = { expandedSection = "hot" },
                            )
                        }
                        item("HotSongs") {
                            SonifySongsPagerSection(
                                songs = hotSongs,
                                pagerState = hotPagerState,
                                onPlayAt = { onPlay(hotSongs, it) },
                                onMoreAt = { index -> hotSongs.getOrNull(index)?.let(onMoreSong) },
                            )
                        }
                    }

                    if (albums.isNotEmpty()) {
                        item("AlbumsHeader") {
                            SonifyArtistSectionHeader(
                                title = stringResource(R.string.sonify_artist_albums),
                                onClick = { expandedSection = "albums" },
                            )
                        }
                        item("Albums") {
                            SonifyAlbumRail(albums, onOpenAlbum)
                        }
                    }

                    if (songs.isNotEmpty()) {
                        item("AllSongsHeader") {
                            SonifyArtistSectionHeader(
                                title = stringResource(R.string.sonify_artist_all_songs),
                                onClick = { expandedSection = "all" },
                            )
                        }
                        item("AllSongs") {
                            SonifySongsPagerSection(
                                songs = songs,
                                pagerState = pagerState,
                                onPlayAt = { onPlay(songs, it) },
                                onMoreAt = { index -> songs.getOrNull(index)?.let(onMoreSong) },
                            )
                        }
                    } else if (loading) {
                        item("ResolvingSongs") { SonifyArtistSongsSkeleton() }
                    }

                    item("navbar") {
                        Spacer(modifier = Modifier.sonifyNavigationBarsHeight(134.dp))
                    }
                }
                }

                SonifyArtistTopBar(
                    title = artistName,
                    backdrop = pageBackdrop,
                    tint = bgColorState.value,
                    visible = barVisible,
                    titleAlpha = smallTitleAlpha,
                    contentColor = contentColor,
                    onBack = onBack,
                )

                expandedSection?.let { section ->
                    SonifyArtistExpandedSection(
                        title = when (section) {
                            "hot" -> stringResource(R.string.sonify_artist_hot_songs)
                            "albums" -> stringResource(R.string.sonify_artist_albums)
                            else -> stringResource(R.string.sonify_artist_all_songs)
                        },
                        songs = when (section) {
                            "hot" -> hotSongs
                            "all" -> songs
                            else -> emptyList()
                        },
                        albums = if (section == "albums") albums else emptyList(),
                        background = bgColorState.value,
                        contentColor = contentColor,
                        onBack = { expandedSection = null },
                        onPlay = { queue, index -> onPlay(queue, index) },
                        onMoreSong = onMoreSong,
                        onOpenAlbum = onOpenAlbum,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistMonogramHero(name: String, background: Color, content: Color) {
    val initial = remember(name) { name.trim().take(1).uppercase() }
    Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
        if (initial.isNotEmpty()) {
            Text(
                text = initial,
                fontSize = 132.sp,
                fontWeight = FontWeight.Bold,
                color = content.copy(alpha = 0.88f),
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SonifyLatestAlbumCard(
    album: MeloXAlbumSummary,
    underlayColor: Color,
    onClick: () -> Unit,
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
            .clip(shape)
            .background(underlayColor)
            .background(Color.White.copy(alpha = if (isDark) 0.12f else 0.22f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = album.artworkUrl,
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(92.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = album.type ?: "Album",
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                color = secondaryText,
            )
            Text(
                text = album.name,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = album.artistText,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                color = secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SonifyAlbumRail(albums: List<MeloXAlbumSummary>, onOpen: (MeloXAlbumSummary) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { albums.size })
    HorizontalPager(
        state = pagerState,
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            pagerSnapDistance = PagerSnapDistance.atMost(Int.MAX_VALUE),
        ),
        pageSize = PageSize.Fixed(162.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        verticalAlignment = Alignment.Top,
        key = { page -> "sonify-album-$page-${albums[page].id}" },
        beyondViewportPageCount = 0,
    ) { page ->
        val album = albums[page]
        Column(
            Modifier.width(150.dp).clickable { onOpen(album) },
        ) {
            AsyncImage(
                model = album.artworkUrl,
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(150.dp).clip(SonifyRoundedCornerShape(18.dp)),
            )
            Text(
                text = album.name,
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = album.artistText,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = LocalContentColor.current.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SonifyArtistExpandedSection(
    title: String,
    songs: List<SearchSong>,
    albums: List<MeloXAlbumSummary>,
    background: Color,
    contentColor: Color,
    onBack: () -> Unit,
    onPlay: (List<SearchSong>, Int) -> Unit,
    onMoreSong: (SearchSong) -> Unit,
    onOpenAlbum: (MeloXAlbumSummary) -> Unit,
) {
    val expandedListState = rememberLazyListState()
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(Modifier.fillMaxSize().zIndex(3f).background(background)) {
            Column(Modifier.fillMaxSize().sonifyStatusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SonifyGlassCircleButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.sonify_back),
                        tint = contentColor,
                        onClick = onBack,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LazyColumn(
                    state = expandedListState,
                    modifier = Modifier.fillMaxSize().overScrollVertical(),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    flingBehavior = rememberOverscrollFlingBehavior { expandedListState },
                ) {
                    if (songs.isNotEmpty()) {
                        items(songs.size, key = { "expanded-song-$it-${songs[it].id}" }) { index ->
                            SonifySongRow(
                                song = songs[index],
                                onClick = { onPlay(songs, index) },
                                onMore = { onMoreSong(songs[index]) },
                            )
                        }
                    }
                    if (albums.isNotEmpty()) {
                        items(albums.size, key = { "expanded-album-${albums[it].id}" }) { index ->
                            val album = albums[index]
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenAlbum(album) }.padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = album.artworkUrl,
                                    contentDescription = album.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                                )
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text(album.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = album.artistText,
                                        fontSize = 13.sp,
                                        color = LocalContentColor.current.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SonifyArtistTopBar(
    title: String,
    backdrop: LayerBackdrop,
    tint: Color,
    visible: Boolean,
    titleAlpha: Float,
    contentColor: Color,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
        ) {
            androidx.compose.foundation.layout.Spacer(
                Modifier
                    .drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = { blur(14.dp.toPx()) },
                        onDrawSurface = { drawRect(tint.copy(alpha = 0.7f)) },
                    )
                    .fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .sonifyStatusBarsPadding()
                .height(54.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 5.dp).height(48.dp)) {
                Box(Modifier.align(Alignment.CenterStart).padding(start = 16.5.dp)) {
                    SonifyGlassCircleButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.sonify_back),
                        tint = contentColor,
                        onClick = onBack,
                    )
                }
                Text(
                    text = title,
                    fontSize = 18.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 70.dp)
                        .alpha(titleAlpha),
                )
            }
        }
    }
}

/** Hero item offset in the viewport (px), or null when it is not visible. */
private fun heroOffsetInViewport(listState: androidx.compose.foundation.lazy.LazyListState): Int? =
    listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "Hero" }?.offset

/** Match ContentScale.Crop and average the bottom 28%, weighted downward. */
internal fun sampleArtistBottomColor(bitmap: Bitmap, heroAspect: Float): Int? {
    val imageAspect = bitmap.width.toFloat() / bitmap.height
    val cropWidth = if (imageAspect > heroAspect) (bitmap.height * heroAspect).toInt().coerceIn(1, bitmap.width) else bitmap.width
    val cropHeight = if (imageAspect < heroAspect) (bitmap.width / heroAspect).toInt().coerceIn(1, bitmap.height) else bitmap.height
    val left = (bitmap.width - cropWidth) / 2
    val top = (bitmap.height - cropHeight) / 2
    var red = 0.0
    var green = 0.0
    var blue = 0.0
    var total = 0.0
    val bottomStart = (cropHeight * 0.72f).toInt()
    for (y in bottomStart until cropHeight) {
        val lowerWeight = 1.0 + 3.0 * (y - bottomStart) / (cropHeight - bottomStart).coerceAtLeast(1)
        for (x in 0 until cropWidth) {
            val pixel = bitmap.getPixel(left + x, top + y)
            val weight = lowerWeight * AndroidColor.alpha(pixel) / 255.0
            red += AndroidColor.red(pixel) * weight
            green += AndroidColor.green(pixel) * weight
            blue += AndroidColor.blue(pixel) * weight
            total += weight
        }
    }
    if (total == 0.0) return null
    return AndroidColor.rgb((red / total).toInt(), (green / total).toInt(), (blue / total).toInt())
}
