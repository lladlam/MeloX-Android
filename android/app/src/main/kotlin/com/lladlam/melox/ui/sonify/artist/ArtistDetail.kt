/*
 * Sonify artist detail page (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/pages/library/artists/ArtistDetail.kt
 * (master @ 209e1aa) plus ArtistDetailParts.kt, ArtistActionButtons.kt and
 * ArtistHeroReflection.kt.
 *
 * Preserved from upstream:
 *   - full-bleed hero with bottom-weighted artwork palette;
 *   - photo -> extracted-colour vertical reflection baked off-thread;
 *   - progressive-blur top bar with the liquid-glass draggable back button;
 *   - page backdrop + exported bar-surface backdrop sampling;
 *   - inline intro, action row, latest album, hot songs, album rail, all-song
 *     pager and full-list detail pages;
 *   - loading skeletons, error/retry, pull-to-refresh and follow state.
 *
 * MeloX replacements:
 *   KugouRepository / ArtistSongsObject / KugouSyncCoordinator
 *     -> MeloXArtistDetail / SearchSong / NeteaseCollectionDetailsClient.
 *   OnlineAlbumObject / NavController / MediaController
 *     -> onOpenAlbum / onPlay / onMoreSong callbacks.
 *   Sonify drawables -> MeloXSymbol font icons.
 */
package com.lladlam.melox.ui.sonify.artist

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
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
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.lladlam.melox.R
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXAlbumSummary
import com.lladlam.melox.core.network.MeloXArtistDetail
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolVariant
import com.lladlam.melox.ui.sonify.ArtistArtworkColors
import com.lladlam.melox.ui.sonify.ArtistPresentationCache
import com.lladlam.melox.ui.sonify.ArtistReflectionFraction
import com.lladlam.melox.ui.sonify.ArtistReflectionMaxBlurDp
import com.lladlam.melox.ui.sonify.ArtistHeroBakeRecipeVersion
import com.lladlam.melox.ui.sonify.ArtistReflectionStretch
import com.lladlam.melox.ui.sonify.HeroBakeCache
import com.lladlam.melox.ui.sonify.LocalTitleBarAccentTint
import com.lladlam.melox.ui.sonify.LocalTitleOverlayHost
import com.lladlam.melox.ui.sonify.LocalTitlePageBackdrop
import com.lladlam.melox.ui.sonify.SonifySettings
import com.lladlam.melox.ui.sonify.TitleOverlayHost
import com.lladlam.melox.ui.sonify.TitleOverlayHostSlot
import com.lladlam.melox.ui.sonify.accentTintOf
import com.lladlam.melox.ui.sonify.artistIdentityRgb
import com.lladlam.melox.ui.sonify.bakeHeroReflection
import com.lladlam.melox.ui.sonify.isSonifyDark
import com.lladlam.melox.ui.sonify.newArtistHeroReflectionShader
import com.lladlam.melox.ui.sonify.sonifyNavigationBarsHeight
import com.lladlam.melox.ui.sonify.statusBarsPadding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetail(
    artist: MeloXArtistDetail?,
    followed: Boolean?,
    allSongs: List<SearchSong>,
    loading: Boolean,
    error: String?,
    canFollow: Boolean,
    onBack: () -> Unit,
    onPlay: (List<SearchSong>, Int) -> Unit,
    onMoreSong: (SearchSong, List<SearchSong>) -> Unit,
    onOpenAlbum: (MeloXAlbumSummary) -> Unit,
    onToggleFollow: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
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
    var heroImageFailed by remember(avatarUrl) { mutableStateOf(false) }
    val heroDecodeFailed = remember(avatarUrl) { mutableStateOf(false) }
    val avatarAbsent = artist != null && avatarUrl == null
    val noUsableAvatar = avatarAbsent || heroImageFailed || heroDecodeFailed.value
    val identitySeed = artist?.id?.takeIf { it > 0 }?.toString() ?: artistName
    val identityPaletteKey = "artist-identity:$identitySeed#$heroAspect"
    val avatarPaletteKey = avatarUrl
        ?.takeIf { !heroImageFailed && !heroDecodeFailed.value }
        ?.let { "$it#bottom-weighted-v2-128-$heroAspect" }
    val paletteKey = avatarPaletteKey
        ?: (if (noUsableAvatar) identityPaletteKey else null)
        ?: artist?.id?.takeIf { it > 0 }
            ?.let { ArtistPresentationCache.paletteKeyFor(it.toString(), heroAspect) }
    val paletteState = remember(paletteKey) { paletteKey?.let(ArtistPresentationCache::palette) }
    val identityRgb = remember(identitySeed, noUsableAvatar, isDark) {
        if (noUsableAvatar) artistIdentityRgb(identitySeed, isDark) else null
    }
    val originalRgb = paletteState?.value ?: identityRgb
    val artworkColors = remember(originalRgb, isDark) { ArtistArtworkColors.fromRgb(originalRgb, isDark) }
    val hasResolvedArtwork = originalRgb != null
    val bgColorState = animateColorAsState(
        targetValue = artworkColors.accent,
        animationSpec = if (hasResolvedArtwork) tween(600) else tween(0),
        label = "artistPageBg"
    )
    val placeholderAccent = remember(isDark) { ArtistArtworkColors.fromRgb(null, isDark).accent }
    val contentColor = if (hasResolvedArtwork) artworkColors.content
    else if (isDark) Color.White else Color(0xFF161616)

    LaunchedEffect(identityPaletteKey, identityRgb, heroAspect) {
        if (identityRgb != null) {
            ArtistPresentationCache.seedPalette(identityPaletteKey, identityRgb)
            artist?.id?.takeIf { it > 0 }?.let {
                ArtistPresentationCache.rememberArtistPalette(it.toString(), heroAspect, identityPaletteKey)
            }
        }
    }

    val pageBackdrop = rememberLayerBackdrop()
    val barSurfaceBackdrop = rememberLayerBackdrop()
    val barGlassBackdrop = rememberCombinedBackdrop(pageBackdrop, barSurfaceBackdrop)
    val listState = rememberLazyListState()

    val songs = allSongs.ifEmpty { artist?.hotSongs.orEmpty() }
    val hotSongs = remember(artist?.hotSongs, songs) {
        (artist?.hotSongs.orEmpty().ifEmpty { songs }).take(20)
    }
    val albums = artist?.albums.orEmpty()
    val pagerState = rememberPagerState(pageCount = { (songs.size + 3) / 4 })
    val hotPagerState = rememberPagerState(pageCount = { (hotSongs.size + 3) / 4 })
    val latestAlbum = remember(albums) { albums.firstOrNull() }

    val heroScrolledOut = remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "Hero" }
            info == null || info.offset + info.size <= 0
        }
    }
    val smallTitleAlpha by animateFloatAsState(
        targetValue = if (heroScrolledOut.value) 1f else 0f,
        animationSpec = tween(150),
        label = "artistSmallTitle"
    )

    var introOpen by rememberSaveable(artist?.id) { mutableStateOf(false) }
    var expandedSection by rememberSaveable(artist?.id) { mutableStateOf<String?>(null) }
    BackHandler(enabled = expandedSection != null) { expandedSection = null }

    // Full-list detail pages replace the artist page while open, matching the
    // upstream navigation destinations without introducing a second page host.
    when (expandedSection) {
        "hot" -> {
            ArtistHotSongsDetail(
                songs = songs,
                artistName = artistName,
                onBack = { expandedSection = null },
                onPlayAt = { index -> if (index in songs.indices) onPlay(songs, index) },
                onMoreAt = { index -> songs.getOrNull(index)?.let { onMoreSong(it, songs) } },
                loading = loading,
                error = error,
                onRetry = onRetry
            )
            return
        }
        "albums" -> {
            ArtistAlbumsDetail(
                albums = albums,
                artistName = artistName,
                onBack = { expandedSection = null },
                onOpenAlbum = onOpenAlbum,
                loading = loading,
                error = error,
                onRetry = onRetry
            )
            return
        }
        "all" -> {
            ArtistSongsDetail(
                songs = songs,
                artistName = artistName,
                onBack = { expandedSection = null },
                onPlayAt = { index -> if (index in songs.indices) onPlay(songs, index) },
                onMoreAt = { index -> songs.getOrNull(index)?.let { onMoreSong(it, songs) } },
                loading = loading,
                error = error,
                onRetry = onRetry
            )
            return
        }
    }

    val heroBitmap = remember(avatarUrl) { mutableStateOf<Bitmap?>(null) }
    val heroRevealed = remember(avatarUrl) { mutableStateOf(hasResolvedArtwork) }

    LaunchedEffect(avatarUrl, heroAspect, noUsableAvatar) {
        if (avatarUrl == null || heroImageFailed || heroDecodeFailed.value) {
            if (noUsableAvatar) heroRevealed.value = true
            return@LaunchedEffect
        }
        val url = avatarUrl
        val cacheKey = paletteKey ?: return@LaunchedEffect
        val bitmap = runCatching {
            context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                    .size(1024)
                    .memoryCacheKey("artist-hero:$url")
                    .diskCacheKey(url)
                    .build()
            ).image?.toBitmap()
        }.getOrNull() ?: run {
            heroDecodeFailed.value = true
            heroRevealed.value = true
            return@LaunchedEffect
        }
        heroBitmap.value = bitmap
        runCatching {
            ArtistPresentationCache.loadPalette(cacheKey) {
                withContext(Dispatchers.Default) {
                    val sampleSrc = if (bitmap.width > 128) Bitmap.createScaledBitmap(
                        bitmap, 128,
                        (128f * bitmap.height / bitmap.width).roundToInt().coerceAtLeast(1), false
                    ) else bitmap
                    sampleArtistBottomColor(sampleSrc, heroAspect)
                }
            }
        }
        artist?.id?.takeIf { it > 0 }?.let {
            ArtistPresentationCache.rememberArtistPalette(it.toString(), heroAspect, cacheKey)
        }
        heroRevealed.value = true
    }

    fun playAt(list: List<SearchSong>, index: Int) {
        if (index in list.indices) onPlay(list, index)
    }

    val overlayHost = remember { TitleOverlayHost() }
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalTitlePageBackdrop provides pageBackdrop,
        LocalTitleBarAccentTint provides accentTintOf(artworkColors.accent, isDark),
        LocalTitleOverlayHost provides overlayHost,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val heroHeight = when {
                portraitTablet -> minOf(maxWidth, maxHeight * HeroTabletMaxHeightFraction) - HeroTabletContentRaiseDp.dp
                isLandscape -> maxHeight
                else -> maxHeight * HeroPhoneHeightFraction
            }.coerceAtLeast(200.dp)
            val heroMaxWidth = maxWidth
            val bandHeight = if (portraitTablet)
                minOf(heroHeight * ArtistReflectionFraction, HeroTabletReflectionBandMaxDp.dp)
            else heroHeight * ArtistReflectionFraction
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
            var pendingHeroBake by remember(avatarUrl) {
                mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
            }
            var backdropBakeSettled by remember(avatarUrl) { mutableStateOf(false) }

            val backdropReady = error != null || (!loading && artist == null) ||
                artistBackdropReady(
                    immersiveBackdrop = !isLandscape,
                    avatarAbsent = noUsableAvatar,
                    contentResolved = heroRevealed.value,
                    bakeSettled = bakedHero != null || pendingHeroBake != null ||
                        backdropBakeSettled || heroDecodeFailed.value
                )
            val backdropAlphaState = animateFloatAsState(
                targetValue = if (backdropReady) 1f else 0f,
                animationSpec = if (backdropReady) tween(400) else tween(0),
                label = "artistBackdropReveal"
            )
            val backdropAlpha = backdropAlphaState.value

            LaunchedEffect(avatarUrl, bakeWidthPx, heroHeightPx, bandHeightPx, originalRgb, heroBitmap.value) {
                if (isLandscape) return@LaunchedEffect
                if (avatarUrl == null || heroImageFailed || heroDecodeFailed.value) return@LaunchedEffect
                if (portraitTablet && originalRgb == null) return@LaunchedEffect
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
                val bakedBitmap = HeroBakeGate.withLock {
                    currentCoroutineContext().ensureActive()
                    withContext(Dispatchers.Default) {
                        try {
                            bakeHeroReflection(src, bakeW, bakePhotoH, bakeBandH, bakeBlurPx, shader)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            null
                        }
                    }
                }
                if (bakedBitmap == null) {
                    backdropBakeSettled = true
                    return@LaunchedEffect
                }
                HeroBakeCache.put(context, cacheKey, bakedBitmap)
                pendingHeroBake = bakedBitmap.asImageBitmap()
            }
            LaunchedEffect(pendingHeroBake) {
                if (pendingHeroBake == null) return@LaunchedEffect
                snapshotFlow { heroOffsetInViewport(listState) to listState.isScrollInProgress }
                    .first { (o, scrolling) -> o != null && !scrolling }
                bakedHero = pendingHeroBake ?: return@LaunchedEffect
                pendingHeroBake = null
            }

            @Composable fun HeroPhoto(modifier: Modifier) {
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
                    modifier = modifier,
                    onError = { heroImageFailed = true }
                )
            }

            @Composable fun ArtistMonogramHero(name: String, background: Color, content: Color) {
                val initial = remember(name) { name.trim().take(1).uppercase() }
                Box(
                    Modifier.fillMaxSize().background(background),
                    contentAlignment = Alignment.Center
                ) {
                    if (initial.isNotEmpty()) {
                        Text(
                            text = initial,
                            fontSize = 132.sp,
                            fontWeight = FontWeight.Bold,
                            color = content.copy(alpha = 0.88f),
                            modifier = Modifier.offset(y = (-24).dp)
                        )
                    }
                }
            }

            ArtistBackdropFill(bgColorState, backdropAlpha)

            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(pageBackdrop)
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
                                val o = heroOffsetInViewport(listState)
                                val scrollA = if (o == null) 0f
                                else (((o + heroHeightPx) / bandHeightPx).coerceIn(0f, 1f))
                                bakedHero?.let {
                                    drawImage(
                                        it,
                                        alpha = scrollA * backdropAlpha,
                                        dstSize = IntSize(
                                            size.width.roundToInt(), size.height.roundToInt()
                                        )
                                    )
                                }
                            }
                    )
                }

                val listContent: @Composable () -> Unit = {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = if (isLandscape) PaddingValues(top = 54.dp) else PaddingValues(0.dp)
                    ) {
                        item("Hero") {
                            if (isLandscape) {
                                ArtistTabletLandscapeHeader(
                                    name = artistName,
                                    avatarUrl = avatarUrl,
                                    avatarRevealed = heroRevealed.value,
                                    hasIntro = !artist?.description.isNullOrBlank(),
                                    introOpen = introOpen,
                                    onIntro = { introOpen = !introOpen },
                                    songsEnabled = songs.isNotEmpty(),
                                    onPlay = { playAt(songs, 0) },
                                    showFollow = canFollow,
                                    isFollowed = followed == true,
                                    followEnabled = !loading,
                                    onFollow = onToggleFollow,
                                    countsLine = if (artist != null) {
                                        "${artist.musicSize.coerceAtLeast(songs.size)} " +
                                            androidx.compose.ui.res.stringResource(R.string.artist_songs) +
                                            " · " +
                                            "${artist.albumSize.coerceAtLeast(albums.size)} " +
                                            androidx.compose.ui.res.stringResource(R.string.artist_albums)
                                    } else null,
                                    loading = loading,
                                    error = error,
                                    onRetry = onRetry
                                )
                            } else {
                                Box(
                                    Modifier.fillMaxWidth().height(heroHeight)
                                        .graphicsLayer { alpha = backdropAlpha }
                                ) {
                                    if (noUsableAvatar) {
                                        ArtistMonogramHero(
                                            name = artistName,
                                            background = bgColorState.value,
                                            content = contentColor
                                        )
                                    } else if (bakedHero == null) {
                                        HeroPhoto(Modifier.fillMaxSize())
                                    }
                                    Text(
                                        text = artistName,
                                        fontSize = 30.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (noUsableAvatar) contentColor else Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        style = TextStyle(
                                            shadow = Shadow(
                                                Color.Black.copy(alpha = 0.45f),
                                                blurRadius = 32f
                                            )
                                        ),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(horizontal = 24.dp)
                                            .padding(bottom = 8.dp)
                                    )
                                }
                            }
                        }

                        if (!isLandscape) {
                            item("Actions") {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val intro = artist?.description
                                    val roundContainer = contentColor.copy(alpha = 0.18f)
                                    if (!intro.isNullOrBlank()) {
                                        ArtistRoundActionButton(
                                            symbol = MeloXSymbol.Info,
                                            contentDescription = androidx.compose.ui.res.stringResource(
                                                R.string.sonify_artist_intro
                                            ),
                                            iconTint = if (introOpen) Color.White else contentColor,
                                            highlighted = introOpen,
                                            iconSize = 32.dp,
                                            containerColor = roundContainer,
                                            onClick = { introOpen = !introOpen }
                                        )
                                        Spacer(Modifier.width(26.dp))
                                    }
                                    ArtistPlayButton(
                                        enabled = songs.isNotEmpty(),
                                        contentColor = contentColor
                                    ) { playAt(songs, 0) }
                                    if (canFollow) {
                                        Spacer(Modifier.width(26.dp))
                                        ArtistRoundActionButton(
                                            symbol = MeloXSymbol.Star,
                                            contentDescription = androidx.compose.ui.res.stringResource(
                                                id = if (followed == true) R.string.sonify_artist_following
                                                else R.string.sonify_artist_follow
                                            ),
                                            iconTint = if (followed == true) {
                                                if (isDark) Color(0xFFFF453A) else Color(0xFFE53935)
                                            } else contentColor,
                                            highlighted = false,
                                            iconSize = 28.dp,
                                            motionEffects = false,
                                            dimWhenDisabled = false,
                                            containerColor = roundContainer,
                                            enabled = !loading,
                                            variant = if (followed == true) {
                                                MeloXSymbolVariant.Fill
                                            } else MeloXSymbolVariant.Regular,
                                            onClick = onToggleFollow
                                        )
                                    }
                                }
                            }
                        }

                        item("HeaderStatus") {
                            Column(Modifier.fillMaxWidth()) {
                                Crossfade(
                                    targetState = artist != null,
                                    animationSpec = tween(180),
                                    label = "artistHeaderFade"
                                ) { hasData ->
                                    val info = artist
                                    if (!hasData || info == null) {
                                        ArtistHeaderSkeleton()
                                    } else {
                                        val lightBackdrop = bgColorState.value.luminance() > 0.179f
                                        Text(
                                            text = "${info.musicSize.coerceAtLeast(songs.size)} " +
                                                androidx.compose.ui.res.stringResource(R.string.artist_songs) +
                                                " · " +
                                                "${info.albumSize.coerceAtLeast(albums.size)} " +
                                                androidx.compose.ui.res.stringResource(R.string.artist_albums),
                                            fontSize = 14.sp,
                                            color = if (lightBackdrop) Color(0xFF161616).copy(alpha = 0.55f)
                                            else Color.White.copy(alpha = 0.45f),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                        )
                                    }
                                }
                                error?.let {
                                    ArtistSectionError(it, loading, onRetry)
                                }
                            }
                        }

                        item("Intro") {
                            AnimatedVisibility(
                                visible = introOpen,
                                enter = expandVertically(tween(260)) + fadeIn(tween(200)),
                                exit = shrinkVertically(tween(220)) + fadeOut(tween(160))
                            ) {
                                val intro = artist?.description
                                if (!intro.isNullOrBlank()) {
                                    Column(Modifier.fillMaxWidth()) {
                                        ArtistSectionHeader(
                                            androidx.compose.ui.res.stringResource(R.string.sonify_artist_intro)
                                        )
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
                                                .padding(bottom = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (artist != null) {
                            latestAlbum?.let { album ->
                                item("LatestAlbum") {
                                    ArtistLatestAlbumCard(
                                        album = album,
                                        underlayColor = bgColorState.value,
                                        onOpen = onOpenAlbum
                                    )
                                }
                            }
                            item("HotHeader") {
                                ArtistSectionHeader(
                                    androidx.compose.ui.res.stringResource(R.string.sonify_artist_hot_songs)
                                ) { expandedSection = "hot" }
                            }
                            item("HotSongs") {
                                ArtistSongSection(
                                    songs = hotSongs,
                                    loading = loading && hotSongs.isEmpty(),
                                    error = null,
                                    pagerState = hotPagerState,
                                    onRetry = onRetry,
                                    onPlayAt = { playAt(hotSongs, it) },
                                    onMoreAt = { index ->
                                        hotSongs.getOrNull(index)?.let { onMoreSong(it, hotSongs) }
                                    },
                                    probeName = "HotSongs"
                                )
                            }
                            item("AlbumsHeader") {
                                ArtistSectionHeader(
                                    androidx.compose.ui.res.stringResource(R.string.sonify_artist_albums)
                                ) { expandedSection = "albums" }
                            }
                            item("Albums") {
                                ArtistAlbumsSection(
                                    albums = albums,
                                    loading = loading && albums.isEmpty(),
                                    error = null,
                                    onRetry = onRetry,
                                    onOpen = onOpenAlbum,
                                    probeName = "Albums"
                                )
                            }
                            item("AllSongsHeader") {
                                ArtistSectionHeader(
                                    androidx.compose.ui.res.stringResource(R.string.sonify_artist_all_songs)
                                ) { expandedSection = "all" }
                            }
                            item("AllSongs") {
                                ArtistSongSection(
                                    songs = songs,
                                    loading = loading && songs.isEmpty(),
                                    error = null,
                                    pagerState = pagerState,
                                    onRetry = onRetry,
                                    onPlayAt = { playAt(songs, it) },
                                    onMoreAt = { index ->
                                        songs.getOrNull(index)?.let { onMoreSong(it, songs) }
                                    },
                                    probeName = "AllSongs"
                                )
                            }
                        } else if (loading) {
                            item("ResolvingSongs") { ArtistSongsSkeleton() }
                        }

                        item("navbar") {
                            Spacer(modifier = Modifier.sonifyNavigationBarsHeight(134.dp))
                        }
                    }
                }

                if (onRefresh != null) {
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        listContent()
                    }
                } else {
                    listContent()
                }
            }

            val barVisible by remember(listState) {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
                }
            }
            val barAlpha by animateFloatAsState(
                if (barVisible) 1f else 0f,
                tween(150),
                label = "artistBarAlpha"
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
            ) {
                AnimatedVisibility(
                    visible = SonifySettings.BarBlurEffect && barVisible,
                    modifier = Modifier.matchParentSize(),
                    enter = fadeIn(tween(120)),
                    exit = fadeOut(tween(120))
                ) {
                    Spacer(
                        Modifier
                            .then(
                                artistTopBarMaterial(
                                    backdrop = pageBackdrop,
                                    colorState = bgColorState,
                                    exportedBackdrop = barSurfaceBackdrop
                                )
                            )
                            .fillMaxSize()
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(54.dp)
                ) {
                    Box(Modifier.fillMaxWidth().padding(top = 5.dp).height(48.dp)) {
                        Box(Modifier.align(Alignment.CenterStart).padding(start = 16.5.dp)) {
                            if (SonifySettings.BarBlurEffect) {
                                LiquidTopBarButton(
                                    onClick = onBack,
                                    backdrop = barGlassBackdrop,
                                    icon = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                                    contentDescription = androidx.compose.ui.res.stringResource(R.string.sonify_back),
                                    iconSize = 18.dp,
                                    iconOffset = DpOffset((-2).dp, 0.dp),
                                    backdropAlpha = { barAlpha },
                                    shadowAlpha = { barAlpha },
                                    idleIconTint = contentColor,
                                    draggable = true
                                )
                            } else {
                                Box(
                                    Modifier.size(42.dp).clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onBack
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                                        contentDescription = androidx.compose.ui.res.stringResource(R.string.sonify_back),
                                        modifier = Modifier.size(18.dp),
                                        tint = contentColor
                                    )
                                }
                            }
                        }
                        Text(
                            text = artistName,
                            fontSize = 18.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 70.dp)
                                .alpha(smallTitleAlpha)
                        )
                    }
                    if (!SonifySettings.BarBlurEffect) {
                        AnimatedVisibility(
                            visible = heroScrolledOut.value,
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        ) {
                            Spacer(
                                Modifier.fillMaxWidth()
                                    .alpha(0.12f).height(1.dp)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            }

            Box(Modifier.fillMaxSize().zIndex(2f)) {
                TitleOverlayHostSlot(overlayHost)
            }
        }
    }
}

@Composable
private fun ArtistTabletLandscapeHeader(
    name: String,
    avatarUrl: String?,
    avatarRevealed: Boolean,
    hasIntro: Boolean,
    introOpen: Boolean,
    onIntro: () -> Unit,
    songsEnabled: Boolean,
    onPlay: () -> Unit,
    showFollow: Boolean,
    isFollowed: Boolean,
    followEnabled: Boolean,
    onFollow: () -> Unit,
    countsLine: String?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isSonifyDark()
    val contentColor = LocalContentColor.current
    val roundContainer = contentColor.copy(alpha = 0.18f)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 14.dp, bottom = 8.dp)
            .padding(horizontal = 18.dp)
    ) {
        val coverSize = (maxWidth * 0.28f).coerceIn(130.dp, 300.dp)
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top
        ) {
            Box(Modifier.size(coverSize).clip(com.lladlam.melox.ui.sonify.SonifyRoundedCornerShape(7.dp))) {
                if (avatarUrl != null && avatarRevealed) {
                    AsyncImage(
                        model = remember(avatarUrl, avatarRevealed) {
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
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(roundContainer), contentAlignment = Alignment.Center) {
                        Text(
                            text = name.trim().take(1).uppercase(),
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    }
                }
            }
            Spacer(Modifier.width(15.dp))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = name,
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasIntro) {
                        ArtistRoundActionButton(
                            symbol = MeloXSymbol.Info,
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.sonify_artist_intro),
                            iconTint = if (introOpen) Color.White else contentColor,
                            highlighted = introOpen,
                            iconSize = 24.dp,
                            containerColor = roundContainer,
                            onClick = onIntro
                        )
                        Spacer(Modifier.width(20.dp))
                    }
                    ArtistPlayButton(
                        enabled = songsEnabled,
                        contentColor = contentColor,
                        onClick = onPlay
                    )
                    if (showFollow) {
                        Spacer(Modifier.width(20.dp))
                        ArtistRoundActionButton(
                            symbol = MeloXSymbol.Star,
                            contentDescription = androidx.compose.ui.res.stringResource(
                                id = if (isFollowed) R.string.sonify_artist_following
                                else R.string.sonify_artist_follow
                            ),
                            iconTint = if (isFollowed) {
                                if (isDark) Color(0xFFFF453A) else Color(0xFFE53935)
                            } else contentColor,
                            highlighted = false,
                            iconSize = 28.dp,
                            motionEffects = false,
                            dimWhenDisabled = false,
                            containerColor = roundContainer,
                            enabled = followEnabled,
                            variant = if (isFollowed) MeloXSymbolVariant.Fill else MeloXSymbolVariant.Regular,
                            onClick = onFollow
                        )
                    }
                }
                if (countsLine != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = countsLine,
                        fontSize = 14.sp,
                        color = LocalContentColor.current.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (loading) {
                    Spacer(Modifier.height(10.dp))
                    ArtistHeaderSkeleton()
                }
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    ArtistSectionError(error, loading, onRetry)
                }
            }
        }
    }
}

private fun heroOffsetInViewport(listState: androidx.compose.foundation.lazy.LazyListState): Int? =
    listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "Hero" }?.offset

internal fun artistBackdropReady(
    immersiveBackdrop: Boolean,
    avatarAbsent: Boolean,
    contentResolved: Boolean,
    bakeSettled: Boolean
): Boolean = !immersiveBackdrop || avatarAbsent || (contentResolved && bakeSettled)

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

@Composable
private fun ArtistBackdropFill(
    colorState: androidx.compose.runtime.State<Color>,
    alpha: Float
) {
    val color = colorState.value
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(color = color, alpha = alpha) }
    )
}

@Composable
private fun artistTopBarMaterial(
    backdrop: LayerBackdrop,
    colorState: androidx.compose.runtime.State<Color>,
    exportedBackdrop: LayerBackdrop
): Modifier {
    val tint = colorState.value
    return if (SonifySettings.BarBlurEffect) {
        titleBarProgressiveBlur(
            backdrop = backdrop,
            tintColor = tint,
            exportedBackdrop = exportedBackdrop
        )
    } else {
        Modifier.drawPlainBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            effects = { blur(14.dp.toPx()) },
            onDrawSurface = { drawRect(tint.copy(alpha = 0.7f)) }
        )
    }
}
