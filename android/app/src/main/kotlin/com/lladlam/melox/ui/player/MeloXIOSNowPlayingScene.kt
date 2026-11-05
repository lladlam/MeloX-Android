package com.lladlam.melox.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Transition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lladlam.melox.ui.glass.meloXBackdropBlur
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.meloXIconDrawingScale
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.animation.meloXContentEnter
import com.lladlam.melox.ui.animation.meloXContentExit
import com.lladlam.melox.ui.layout.rememberMeloXWindowInfo
import com.lladlam.melox.playback.MeloXPlaybackModeRuntime
import com.lladlam.melox.playback.MeloXPlaybackNotice
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.music.model.TrackAvailability
import com.lladlam.melox.core.music.provider.MeloXUnifiedFavorites
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.provider.local.LocalMusicRepository
import com.lladlam.melox.playback.PlaybackTrackIdentity

/**
 * The portrait Now Playing scene mirrors the upstream iOS page architecture:
 * artwork details are transient, Lyrics and Queue live as resident layers, the
 * song header is shared by alternate pages, and the bottom controls never leave
 * the composition while pages change.
 *
 * The actual artwork is intentionally not drawn here. SharedHost owns one
 * persistent artwork layer so the same image can move between the full artwork
 * frame and the 72dp alternate-page header. Outer dismissal is page-aware: the
 * Artwork/Queue image can continue into MiniPlayer, while Lyrics lets MiniPlayer
 * own the waiting artwork at its final location.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MeloXIOSNowPlayingScene(
    state: MeloXPlaybackUiState,
    page: MeloXNowPlayingPage,
    transitionSourcePage: MeloXNowPlayingPage,
    onDismiss: () -> Unit,
    onPageChanged: (MeloXNowPlayingPage) -> Unit,
    onShowActions: () -> Unit,
    onShowQuality: () -> Unit,
    showLandscapeSkyline: Boolean = false,
    onShowLandscapeSkyline: () -> Unit = {},
    onHideLandscapeSkyline: () -> Unit = {},
    onLyricsInterfaceHiddenChange: (Boolean) -> Unit = {},
    grabberDragModifier: Modifier = Modifier,
    lyricsActive: Boolean = true,
    // ★★ 2026-10-15（打开播放页流畅度）：两棵**常驻重层**（歌词 LazyColumn / 队列列表）是否可以
    //   参与组合。由 `MeloXIOSNowPlayingSharedHost` 在展开动画**落定后**置真（单向 latch，
    //   原因见那里的长注释）。默认 `true` = 逐像素回退到旧行为（Classic 外壳等其它调用点不受影响）。
    //
    //   为什么可以延后：封面页（打开播放页时的初始页）这两层的 `alpha` 恒 0 ⇒ 一个像素都看不见，
    //   但 `graphicsLayer{alpha=0}` 并不会让 Compose 跳过子树，它们照常组合/测量/布局/绘制。
    //   延后 ⇒ 「打开」这条时间线上不再为看不见的内容付电；落定后立刻组合 ⇒ 之后滑到歌词页
    //   时早已就绪，**不会**把卡顿转移到切页。
    residentLayersReady: Boolean = true,
    orientTransition: Transition<Boolean>,
    // ★ 平板双栏「玩家卡片居中」进度 0→1（0 = 左右分栏，1 = 左列滑到屏幕正中）。
    //   ⚠ 由 `MeloXIOSNowPlayingSharedHost` **单一计算后透传**（那里是唯一时钟），本层只消费：
    //     持久封面层的 `centerXFrac` 与这里 Row 的 `translationX` 必须同解，各跑一份动画会错拍。
    //   手机 / 竖屏恒 0 ⇒ 逐像素不变。
    //   ⚠ 用 `() -> Float` 而不是值：每帧状态，值传参会把调用方（host）每帧重组。
    columnCenterProgress: () -> Float = { 0f },
    sharedTransitionScope: SharedTransitionScope,
) {
    val favorite = rememberTrackFavorite(state)
    // ★ 统一主时间线：竖/横两套布局都作为**共享 orientTransition** 的 AnimatedVisibility，
    //   淡入淡出与封面滑移跑在同一条 spring 上（不再是两个独立 tween 时钟）。
    //   转屏时旧布局淡出、新布局淡入与封面移动严格同步 = 一镜到底。
    val isLandscape = rememberMeloXWindowInfo().isLandscape
    // ⚠ 与系统旋转动画对齐（2026-10-09）：同上，固定 300ms tween 保证与系统
    //   旋转同时起、同时止 = 布局换页的 fade 与封面移动同步，三者融合为一次运动。
    // ⚠ 同上（SharedHost）：CubicBezierEasing(0.16,1,0.3,1) 快速启动、柔和落定。
    val orientSpec = tween<Float>(300, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
    orientTransition.AnimatedVisibility(
        visible = { !it },
        enter = fadeIn(orientSpec),
        exit = fadeOut(orientSpec),
    ) {
    val avScope = this
    val directLyricsQueue = isDirectLyricsQueueTransition(transitionSourcePage, page)

    val artworkVisible = page == MeloXNowPlayingPage.Artwork
    val lyricsVisible = page == MeloXNowPlayingPage.Lyrics
    val queueVisible = page == MeloXNowPlayingPage.Queue
    // ★ 常驻重层（歌词 / 队列）本次组合是否参与：见 [residentLayersReady] 形参的说明。
    //   旁路：**当前页不是封面页**时立刻组合 —— 冷启动直接进歌词页 / 程序化切页等情形
    //   本来就需要它们在场，绝不能延后（那是可视回归，不是优化）。
    val showResidentLayers = residentLayersReady || !artworkVisible
    var showsLyricsControls by remember(state.mediaId) { mutableStateOf(true) }
    var lyricsControlsActivityGeneration by remember(state.mediaId) { mutableIntStateOf(0) }

    LaunchedEffect(page, showsLyricsControls) {
        onLyricsInterfaceHiddenChange(
            page == MeloXNowPlayingPage.Lyrics && !showsLyricsControls,
        )
    }

    // The lyric page has no per-frame drawing of its own while it is idle, so a
    // track switch updated the artwork behind it without repainting the recorded
    // backdrop the blurred control bar samples from. The bar then kept the old
    // song's tint until the user scrolled. Pumping a few frames on every artwork
    // change re-records the layer immediately.
    LaunchedEffect(state.artworkUrl, page) {
        if (page == MeloXNowPlayingPage.Artwork) return@LaunchedEffect
        repeat(10) {
            withFrameNanos { }
        }
    }

    fun setLyricsControlsVisible(visible: Boolean) {
        showsLyricsControls = visible
        if (visible) lyricsControlsActivityGeneration += 1
    }

    LaunchedEffect(page) {
        showsLyricsControls = true
        if (page == MeloXNowPlayingPage.Lyrics) lyricsControlsActivityGeneration += 1
    }
    // Treat every media item as fresh lyric-page activity.  Headset next/prev
    // changes the track without necessarily changing the page, so omitting the
    // media id left the controls permanently visible after a hardware skip.
    LaunchedEffect(page, state.mediaId, showsLyricsControls, lyricsControlsActivityGeneration) {
        if (page != MeloXNowPlayingPage.Lyrics || !showsLyricsControls) return@LaunchedEffect
        delay(MeloXSettingsRuntime.lyricInterfaceAutoHideDelayMs.toLong())
        showsLyricsControls = false
    }

    // Consolidate the page cross-fade into a single Transition so the five
    // page states share one spring timeline instead of ten independent
    // animate*AsState coroutines running in parallel.
    val pageTransition = updateTransition(
        targetState = page,
        label = "scene-page-transition",
    )

    val artworkAlpha by pageTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-artwork-details-alpha",
    ) { if (it == MeloXNowPlayingPage.Artwork) 1f else 0f }

    val artworkOffset by pageTransition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-artwork-details-offset",
    ) { if (it == MeloXNowPlayingPage.Artwork) 0.dp else (-300).dp }

    val lyricsAlpha by pageTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-lyrics-alpha",
    ) { if (it == MeloXNowPlayingPage.Lyrics) 1f else 0f }

    val lyricsOffset by pageTransition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-lyrics-offset",
    ) { if (it == MeloXNowPlayingPage.Artwork) 400.dp else 0.dp }

    val lyricsScale by pageTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-lyrics-scale",
    ) { if (it == MeloXNowPlayingPage.Queue) 0.92f else 1f }

    val queueAlpha by pageTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-queue-alpha",
    ) { if (it == MeloXNowPlayingPage.Queue) 1f else 0f }

    val queueOffset by pageTransition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-queue-offset",
    ) { if (it == MeloXNowPlayingPage.Artwork) 400.dp else 0.dp }

    val queueScale by pageTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-queue-scale",
    ) { if (it == MeloXNowPlayingPage.Lyrics) 0.92f else 1f }

    val headerAlpha by pageTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-song-header-alpha",
    ) { if (it == MeloXNowPlayingPage.Artwork) 0f else 1f }

    val headerOffset by pageTransition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-song-header-offset",
    ) { if (it == MeloXNowPlayingPage.Artwork) 40.dp else 0.dp }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .meloXPlayerStatusBarsPadding()
                .meloXPlayerNavigationBarsPadding()
                .padding(horizontal = 32.dp),
        ) {
        SceneGrabber(
            onDismiss = onDismiss,
            dragModifier = grabberDragModifier,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (artworkVisible) 2f else 0f)
                    .graphicsLayer {
                        alpha = artworkAlpha
                        translationY = artworkOffset.toPx()
                    },
            ) {
                ArtworkDetailsWithoutArtwork(
                    state = state,
                    favorite = favorite,
                    onShowActions = onShowActions,
                    onOpenQueue = { onPageChanged(MeloXNowPlayingPage.Queue) },
                    // 封面页的标题在这里（而非顶部那条 alpha=0 的 header）⇒ 转屏时
                    // 由它跟横屏 LandscapeSongHeader 配对形变。
                    headerMorphModifier = if (page == MeloXNowPlayingPage.Artwork) {
                        Modifier.then(sharedTransitionScope.npMorphElement("np-header", avScope))
                    } else {
                        Modifier
                    },
                )
            }

            // ★ 延后组合（`showResidentLayers`）：封面页展开动画期间这层恒不可见（alpha=0），
            //   **Box / npMorphElement 节点本身保留** —— 空 Box 不绘制、也不占测量成本，
            //   共享元素配对关系（与迷你栏、与队列页）都不受影响；被推后的只有那棵 LazyColumn。
            if (showResidentLayers) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(sharedTransitionScope.npMorphElement("np-lyrics", avScope))
                        .zIndex(if (lyricsVisible) 2f else 0f)
                        .graphicsLayer {
                            alpha = lyricsAlpha
                            translationY = lyricsOffset.toPx()
                            scaleX = lyricsScale
                            scaleY = lyricsScale
                        }
                        .padding(top = 88.dp),
                ) {
                    // 竖屏歌词底部渐隐的**归零点**：随底部控件条的显隐在「控件条顶沿(279dp) ↔
                    // 视口底(0)」之间过渡。控件条显示时上移以防白色歌词从 8% 黑模糊底板渗出；
                    // 控件条自动隐藏（沉浸）时归零，把整块空间还给歌词。tween 平滑避免跳变。
                    val lyricsFadeTerminal by animateDpAsState(
                        targetValue = if (showsLyricsControls) MeloXNowPlayingControlsHeight.dp else 0.dp,
                        animationSpec = tween(durationMillis = 220),
                        label = "portraitLyricsFadeTerminal",
                    )
                    MeloXIOSLyricsPanel(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        isInterfaceHidden = !showsLyricsControls,
                        onInterfaceInteraction = { setLyricsControlsVisible(true) },
                        onInterfaceVisibilityChange = { setLyricsControlsVisible(it) },
                        active = lyricsActive,
                        // ★ 底部渐隐（同横屏的 Apple container edge fade，取代"靠遮挡"）。
                        //   竖屏歌词面板铺满到 Column 底、**没有底部让位**，那一截由底部控件条
                        //   盖住；但控件条只有 `meloXBackdropBlur`（`Black 8%` + 24dp 模糊），
                        //   白色高亮歌词会透出（真机截图：进度条一带能看到下层 `(4665,777)`
                        //   虚影、三控件下方还有整行）——即用户报的「本该被下半操作区遮挡的
                        //   歌词出现了」，转屏后遮挡层重建的那几帧尤其明显。
                        //   ⇒ 归零点抬到控件条**顶沿**（两侧都按屏幕底量，故正好 = 控件条高），
                        //   控件条区域内歌词恒透明 ⇒ 不再依赖那层模糊底板去挡。
                        //   ⚠⚠ 但控件条 3 秒无操作会**自动隐藏**（沉浸模式）。若归零点恒钉在
                        //   顶沿，隐藏后下方 279dp（占视口 ~40%）就成了"既没控件、也没歌词"的
                        //   空白（用户报的「控件隐藏了，下面的歌词不出来，白白浪费下面的空间」）。
                        //   ⇒ 归零点**跟随控件条显隐**：显示 → 顶沿（防渗出）；隐藏 → 0（把空间
                        //   还给歌词）。用 tween 平滑，避免控件条淡出瞬间歌词"跳"出来。
                        //   `edgeBlurMax = 0.dp`：竖屏只要渐隐、不要边缘模糊。
                        edgeFadeBottom = MeloXPortraitLyricsFadeBottom,
                        edgeFadeBottomTerminal = lyricsFadeTerminal,
                        controlsRevealHeight = MeloXNowPlayingControlsHeight.dp,
                        edgeBlurMax = 0.dp,
                    )
                }
            }

            // ★ 同上（`showResidentLayers`）：队列列表同一处理。
            if (showResidentLayers) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(sharedTransitionScope.npMorphElement("np-queue", avScope))
                        .zIndex(if (queueVisible) 2f else 0f)
                        .graphicsLayer {
                            alpha = queueAlpha
                            translationY = queueOffset.toPx()
                            scaleX = queueScale
                            scaleY = queueScale
                        }
                        // 队列行止于常驻播放控制区上方；
                        // 列表不会有任何部分被模糊控制区遮住。
                        .padding(
                            top = 80.dp,
                            bottom = MeloXNowPlayingControlsHeight.dp,
                        ),
                ) {
                    MeloXQueuePanel(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        showSongHeader = false,
                        interactive = queueVisible,
                    )
                }
            }

            val songHeaderShape = RoundedCornerShape(20.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .then(
                        // 封面页这条 header 是 alpha=0 的（标题改由封面下方那条画），
                        // 该页不挂 key，避免与 ArtworkDetails 里的同 key 节点重复。
                        if (page == MeloXNowPlayingPage.Artwork) Modifier
                        else sharedTransitionScope.npMorphElement("np-header", avScope),
                    )
                    .zIndex(3f)
                    .graphicsLayer {
                        alpha = headerAlpha
                        translationY = headerOffset.toPx()
                    }
                    .then(
                        if (queueVisible) {
                            Modifier
                                .clip(songHeaderShape)
                                .meloXBackdropBlur(
                                    shape = songHeaderShape,
                                    blurRadius = 20.dp,
                                    surfaceColor = Color.Black.copy(alpha = .10f),
                                )
                        } else Modifier
                    )
                    // ⚠ 行首必须给「共享封面缩略图」留位：非封面页（歌词/列表）时
                    //   `SharedArtworkDestination` 会把封面缩到 72dp、贴在 x = 32dp / top = 本行 top
                    //   （即 32..104dp）。所以标题要退到 32 + 84 = 116dp，否则缩略图会盖住歌名头部
                    //   与整条歌手行（曾出现"歌词页只看得见『更爱美人』"）。
                    //   84dp = 72dp 缩略图 + 12dp 间隙（与上游一致）；右侧 32 + 8 = 40dp 不动，
                    //   保证收藏/菜单按钮右缘在两页同位置。
                    .padding(start = 84.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MeloXPlaybackTitleMenu(
                    state = state,
                    onOpenQueue = { onPageChanged(MeloXNowPlayingPage.Queue) },
                    modifier = Modifier.weight(1f),
                    titleSize = 19.sp,
                    titleLineHeight = 23.sp,
                    artistSize = 18.sp,
                    artistLineHeight = 22.sp,
                    showHeartModeLabel = true,
                )
                MeloXCircleIconButton(onClick = favorite.toggle) {
                    MeloXAnimatedFavoriteStar(
                        liked = favorite.liked == true,
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                    )
                }
                MeloXCircleIconButton(onClick = onShowActions) {
                    MeloXSymbolIcon(
                        MeloXSymbol.Ellipsis,
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        iconSize = 16.sp,
                    )
                }
            }
        }

        }

        AnimatedVisibility(
            visible = page != MeloXNowPlayingPage.Lyrics || showsLyricsControls,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .meloXPlayerNavigationBarsPadding(),
            enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 8 },
            exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it / 8 },
        ) {
            val controlsSurface = if (page != MeloXNowPlayingPage.Artwork) {
                Modifier
                    .fillMaxWidth()
                    .height(MeloXNowPlayingControlsHeight.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(MeloXNowPlayingControlsHeight.dp)
            }
            Box(modifier = controlsSurface.then(sharedTransitionScope.npMorphElement("np-controls", avScope))) {
                if (page != MeloXNowPlayingPage.Artwork) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .meloXBackdropBlur(
                                shape = RectangleShape,
                                blurRadius = 24.dp,
                            )
                            // Consume blank-area taps so they cannot fall
                            // through the lower lyric control surface.
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                    )
                }
                Box(Modifier.fillMaxSize().padding(horizontal = 32.dp)) {
                MeloXNowPlayingCoreControls(
                    state = state,
                    page = page,
                    onShowQuality = onShowQuality,
                    // 竖屏 iOS 同样换高精度矢量字形（喇叭 / AirPlay / 列表），但保持竖屏布局尺寸。
                    useVectorGlyphs = true,
                    onPageSelected = { destination ->
                        setLyricsControlsVisible(true)
                        onPageChanged(
                            if (page == destination) MeloXNowPlayingPage.Artwork else destination,
                        )
                    },
                    selectorModifier = Modifier.then(
                        sharedTransitionScope.npMorphElement("np-selector", avScope),
                    ),
                )
                }
            }
        }
    }
    }
    orientTransition.AnimatedVisibility(
        visible = { it },
        enter = fadeIn(orientSpec),
        exit = fadeOut(orientSpec),
    ) {
        val avScope = this
        MeloXIOSLandscapeNowPlayingScene(
            state = state,
            page = page,
            onDismiss = onDismiss,
            onPageChanged = onPageChanged,
            onShowActions = onShowActions,
            onShowQuality = onShowQuality,
            favorite = favorite,
            showSkyline = showLandscapeSkyline,
            onShowSkyline = onShowLandscapeSkyline,
            onHideSkyline = onHideLandscapeSkyline,
            grabberDragModifier = grabberDragModifier,
            lyricsActive = lyricsActive,
            columnCenterProgress = columnCenterProgress,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = avScope,
        )
    }
}

/**
 * 「同一个控件形变到新位置」的桥：同一个 key 打在横竖两套子树的对应控件上，
 * 转屏时 Compose 把它从竖屏矩形连续形变（滑 + 缩/放）到横屏矩形，
 * 而不是两套控件各自交叉淡入。复用播放页已有的 SharedTransitionLayout。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.npMorphElement(
    key: String,
    animatedVisibilityScope: AnimatedVisibilityScope,
): Modifier = Modifier.sharedElement(
    sharedContentState = rememberSharedContentState(key = key),
    animatedVisibilityScope = animatedVisibilityScope,
)

/**
 * 横屏右列「左缘对齐」契约（2026-09-30 用户要求）。
 *
 * 除底部三键外，右列其余部件（曲名栏 / 歌词 / 队列）的左缘一律与三键里**最左那颗按钮
 * 的左缘**齐平 —— 基准 = [MeloXCompactPageSelectorHPadding]（18dp，即选中态那圈圆形
 * 底板的左缘，也是 `ScenePageSelector(compact = true)` 的左右留白）。
 *
 * 为什么下面是三个不同的数：各部件自带的内边距不同，
 *  · 曲名栏 [LandscapeSongHeader]：容器无内边距 ⇒ 补满 18dp；
 *  · 歌词 [MeloXIOSLyricsPanel]：逐行自带 [MeloXLyricLineHorizontalPadding] ⇒ 补 18−8 = 10dp；
 *  · 队列 [MeloXQueuePanel]：行 / 分区标题无内边距 ⇒ 补满 18dp。
 * 要挪基准只改 [MeloXCompactPageSelectorHPadding]，三处跟着走。
 */
private val MeloXLandscapeLyricsStartInset =
    MeloXCompactPageSelectorHPadding - MeloXLyricLineHorizontalPadding

/**
 * 横屏底部三键浮层的**下移量**（真机量测：42dp 触控行内图标垂直居中会让图标底高出封面底
 * ~10dp，整体下移这么多让图标底与封面底平齐）。
 *
 * ⚠ 三键实际占位 = 42dp 行高 + 本值 ≈ 52dp，正是 `MeloXLandscapeLyricsFadeBottom`（56dp）
 * 注释里说的"让开的量"。改本值必须一并核对那条底部渐隐带。
 */
private val MeloXLandscapeSelectorOffsetY = 10.dp

/**
 * 横屏底部三键**隐藏**时，歌词底部渐隐带的收窄值。
 *
 * 语义：三键在歌词视口内占位 = 42dp 行高 − `MeloXLandscapeSelectorOffsetY`
 * （只有落在视口内的 32dp 被三键实际遮住；剩下 10dp 在 Row 之外）
 * ⇒ 三键隐藏后，可让歌词**下延**这么多而不越界；同步把渐隐带按同量收窄，
 * 可读区就能顺带扩进那 32dp（否则渐隐恒在视口底 ⇒ 隐藏只是没遮挡、也不多出可读区）。
 *
 * ⚠ 不变量：`MeloXLandscapeLyricsFadeBottomHidden
 *   = MeloXLandscapeLyricsFadeBottom − (42dp − MeloXLandscapeSelectorOffsetY)`
 *   ⇒ 改三键行高 / offset / 渐隐带任一处都需同步核对本常量。
 */
private val MeloXLandscapeLyricsFadeBottomHidden = 24.dp

/**
 * 横屏高亮行的焦点锚点。⚠ 与 [MeloXIOSLyricsPanel.MeloXLandscapeLyricsFocusPosition]
 * （0.30f，为有底部三键时的"让开量"调）**不同**：
 *
 * - 焦点行的视口内绝对位置 = `viewportHeight × focusPosition`。
 * - 用户报"高亮行偏视口中央靠上" —— 0.30f 落在视口 y=92dp，中央 y=153dp，
 *   肉眼偏上 ~61dp。抬到 0.47f ⇒ y=143.8dp，恰是"64dp 曲名栏 + 24dp 底渐隐带"
 *   之间可读区间（y 64..282）的**几何中心** y=173dp 上方 29dp 处，即视觉中央。
 *
 * ⚠ 不变量：`0.47f ≈ (0.30 + 0.64) / 2` —— 上界 0.30 = 原三键占位下界，
 * 下界 0.64 = `MeloXPortraitLyricsFadeTop / 306dp`（顶栏占比）。
 * 改 `MeloXLandscapeSelectorOffsetY` / `MeloXLandscapeLyricsFadeBottom` /
 * 曲名栏高度 都要复核本值。
 */
private const val MeloXLandscapeLyricsFocusPositionOverride = 0.47f

// ============================================================================================
// 平板横屏「双栏」—— 左播放器 / 右歌词（2026-10-07）
//
// 判据 = [MeloXWindowInfo.supportsPlayerTwoPane]（宽 ≥ 840dp **且** 高 ≥ 600dp；手机横屏 792×360dp
// 不通过）。与手机横屏的差别**只有一处**：控制区从「右列的一个页面」（`page == Artwork`）
// 搬到**左列封面下方常驻**，右列由「封面页 / 歌词 / 队列」三态互斥改为**歌词常驻**
// （队列仍可由左列控制条里的列表键切入，只是不再有"封面页"这一态）。
// 手机横屏 / 竖屏 / Compact 全部走原分支，逐像素不变。
//
// ⚠ 纵向预算是硬约束：封面边长由「屏高 − 封面下整块 − 上下留白」反推（见
//   [meloXLandscapeArtworkSize]），SharedHost 的**持久封面**用同一份结果定纵向落点
//   （[meloXTabletLandscapeArtworkCenterYFrac]）—— 两组常量是本文件与 SharedHost 的
//   唯一耦合点，改一处必须同时核对。
// ============================================================================================

/** 左列：封面下沿 → 曲名行的间距。 */
private val MeloXTabletLandscapeGap = 28.dp

/** 左列：曲名 / 艺人行高度（两行 20sp 文本 + 收藏 / 更多两枚圆钮）。 */
private val MeloXTabletLandscapeMetaHeight = 56.dp

/** 左列：曲名行 → 控制条之间的间距。 */
private val MeloXTabletLandscapeMetaGap = 16.dp

/**
 * 左列：封面以下整块的高度（曲名行 + 间距 + 控制条）。
 *
 * 控制条高度取自 [MeloXNowPlayingControlsHeight]（与竖屏底部控件条**同一份**常量，
 * 保证同一控件在竖屏与平板横屏下比例一致）。整块被固定成这个高度 ⇒ SharedHost
 * 不必测量内容就能算出封面落点。
 *
 * ⚠ 这个定值**只在竖屏档（`compact = false`）下才恰好被填满**：
 * 竖屏档孩子高 = 52 + 19 + 82 + 31 + 42 + 3 + 50 = 279dp（逐项加和就是
 * [MeloXNowPlayingControlsHeight] 的来历）；横屏档孩子高只有 245dp，会剩 34dp 死区。
 * ⇒ 双栏左列调 [MeloXNowPlayingCoreControls] 时**必须** `compact = false`，
 * 否则本块高与真实内容对不上（封面落点也会跟着错）。详见该调用点的长注释。
 */
private val MeloXTabletLandscapeControlsBlock =
    MeloXTabletLandscapeMetaHeight + MeloXTabletLandscapeMetaGap +
        MeloXNowPlayingControlsHeight.dp

/** 左列整块的上下留白（反推封面边长，见 [meloXLandscapeArtworkSize]）。 */
internal val MeloXTabletLandscapeMargin = 28.dp

/**
 * 双栏「玩家卡片居中」时 `Row` 的横向位移比例（× 行宽）。
 *
 * 左列权重 `.43f` ⇒ 列心落在行宽的 `0.43 ÷ 2 = 0.215`；居中态要把它推到行宽正中 `0.5`
 * ⇒ 位移 = `0.5 − 0.215 = 0.285` × 行宽。
 *
 * ⚠⚠ 必须与 `MeloXIOSNowPlayingSharedHost` 里持久封面层的 `landscapeCenterXFrac`
 *   （`0.215f → 0.5f`）**同解**：两处不同解 ⇒ 封面与左列占位 / 控制条横向错开
 *   （本仓库铁律：封面落点单点真源）。行宽 == SharedHost 的 `maxWidth`（两边边界逐字一致）。
 */
private const val MeloXTabletColumnCenterShiftFrac = 0.285f

/**
 * 平板横屏右列歌词的底部渐隐带。
 *
 * 手机横屏那条 [MeloXLandscapeLyricsFadeBottom]（56dp）是为了让开底部三键浮层；双栏右列
 * **没有任何浮层**（曲名行与三键都在左列）⇒ 上下对称，与 [MeloXLandscapeLyricsFadeTop] 同值。
 */
private val MeloXTabletLandscapeLyricsFadeBottom = 64.dp

/**
 * 平板横屏右列字号倍率。
 *
 * 手机横屏用 [MeloXLandscapeLyricFontMultiplier]（0.85f）是因为右列高度只有封面那么高
 * （≤ 420dp）；双栏右列是**整屏高**，不必收缩 ⇒ 回到面板默认（1.0f）。
 */
private const val MeloXTabletLandscapeLyricFontMultiplier = 1f

/**
 * 横屏大封面边长。**三处必须同源**：SharedHost 的持久封面、场景主布局的左列占位、
 * 天际歌词 clip 的源尺寸（[meloXSkylineArtworkOverlayClip]）。
 *
 * · 手机横屏 = 原公式 `min(420dp, 85% 高, 42% 宽)`（实际恒取 420dp 上限）；
 * · 平板双栏额外扣掉左列封面下方那块 [MeloXTabletLandscapeControlsBlock] 与上下留白 ——
 *   否则「封面 + 控制区」整块高于屏高，两侧留白会被吃光。
 */
internal fun meloXLandscapeArtworkSize(maxWidth: Dp, maxHeight: Dp, twoPane: Boolean): Dp {
    val cap = minOf(420.dp, maxHeight * .85f, maxWidth * .42f)
    if (!twoPane) return cap.coerceAtLeast(1.dp)
    val budget = maxHeight - MeloXTabletLandscapeGap - MeloXTabletLandscapeControlsBlock -
        MeloXTabletLandscapeMargin * 2f
    return minOf(cap, budget).coerceAtLeast(160.dp)
}

/**
 * 平板双栏封面**中心**的纵向比例（SharedHost 的持久封面用）。
 *
 * 与场景层左列的 `Arrangement.Center` 严格同解：整块 = 封面 + [MeloXTabletLandscapeGap] +
 * [MeloXTabletLandscapeControlsBlock]，在列内居中 ⇒ 封面中心 = 下边距 + 封面高 / 2。
 */
internal fun meloXTabletLandscapeArtworkCenterYFrac(maxWidth: Dp, maxHeight: Dp): Float {
    val size = meloXLandscapeArtworkSize(maxWidth, maxHeight, twoPane = true)
    return ((MeloXTabletLandscapeMargin + size / 2).value / maxHeight.value).coerceIn(0f, 1f)
}

/**
 * 横屏底部三键浮层的自动显隐壳（enter / exit 与竖屏底部控件条逐字同源：
 * 220/260 + 180/220、`FastOutSlowInEasing`、`slide = h/8` ⇒ 转屏前后观感一致）。
 *
 * ⚠ 为什么单独抽一层：调用点处于「Column 里的 Box」，两套 layout receiver 同时在作用域中，
 * 直接写 `AnimatedVisibility(...)` 会被编译器回退到外层 `ColumnScope` 版本并报
 * "cannot be called in this context with an implicit receiver"。抽成无 receiver 上下文的小
 * 壳 ⇒ 只解析到顶层那一个，编译稳定。`modifier` 由调用方在 BoxScope 里拼好（含 `.align`）。
 */
@Composable
private fun LandscapeSelectorVisibility(
    visible: Boolean,
    modifier: Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) +
            slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 8 },
        exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) +
            slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it / 8 },
    ) {
        content()
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MeloXIOSLandscapeNowPlayingScene(
    state: MeloXPlaybackUiState,
    page: MeloXNowPlayingPage,
    onDismiss: () -> Unit,
    onPageChanged: (MeloXNowPlayingPage) -> Unit,
    onShowActions: () -> Unit,
    onShowQuality: () -> Unit,
    showSkyline: Boolean,
    onShowSkyline: () -> Unit,
    onHideSkyline: () -> Unit,
    favorite: MeloXTrackFavorite,
    grabberDragModifier: Modifier,
    lyricsActive: Boolean = true,
    // 平板双栏「玩家卡片居中」进度 0→1（0 = 左右分栏，1 = 左列在屏幕正中）。手机恒 0。
    // ⚠ `() -> Float`：每帧状态只能在**叶子消费点**（`offset` / `graphicsLayer` 的 lambda）读。
    columnCenterProgress: () -> Float = { 0f },
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val window = rememberMeloXWindowInfo()
    // ★ 平板横屏双栏：左列常驻播放器卡片、右列歌词常驻（见本文件顶部常量区的长注释）。
    //   判据 = 宽 ≥ 840dp **且** 高 ≥ 600dp ⇒ 手机横屏（792×360dp）/ 竖屏 / 折叠屏半开
    //   全部为 false，下面所有 `if (twoPane)` 分支都不进，行为与今天逐像素一致。
    val twoPane = window.supportsPlayerTwoPane
    // 封面态 = 还没选歌词 / 队列 ⇒ 左列移到屏幕正中，**右列整个留空**。
    // ⚠ 不能实现成「把右列推出屏外 + 调低 alpha」：实测那样会在屏右缘留一段低透明度的歌词
    //   残影，且转屏时有一段看得见的右移过程 —— 用户报障 2026-10-07：
    //   「常规页面转横屏会有一段歌词右移，正确应该是直接在中间」。
    //   真留空另有两个附带好处：① 转屏进来时 `AnimatedContent` 的**初始** target 就是
    //   Artwork ⇒ 第一帧就没有歌词（连 exit 动画都不需要）；② 不白跑一套歌词布局。
    val coverState = twoPane && page == MeloXNowPlayingPage.Artwork
    // 双栏右列三态：封面态 = Artwork（该分支在双栏下渲染空 Box）/ 队列 / 歌词。
    // ⚠ 「再点一次当前页 → 退回封面态」在双栏下**已恢复**（见 `select()`）—— 那是回到居中态
    //   的唯一入口 ⇒ 左列三键选中态必须用**原始 `page`**（`Artwork` 时三键全不亮），
    //   不能用这里折叠后的 `panePage`，否则居中的同时"歌词"还亮着。
    val panePage = when {
        coverState -> MeloXNowPlayingPage.Artwork
        page == MeloXNowPlayingPage.Queue -> MeloXNowPlayingPage.Queue
        else -> MeloXNowPlayingPage.Lyrics
    }
    // ★ 横屏「界面隐藏」总开关：一件开关同时管两块 chrome ——
    //   ① 歌词面板自身的界面（字号 / 天际入口等，走 `isInterfaceHidden`）；
    //   ② 底部三键浮层（`AnimatedVisibility`，见本文件末尾的 np-selector 那一段）。
    //   两块一起淡出、一起被歌词点按 / 滑动唤出，与竖屏那份 `showsLyricsControls`
    //   同一套语义（竖屏它还管底部控件条）。
    var showsInterface by remember(state.mediaId) { mutableStateOf(true) }
    var activityGeneration by remember(state.mediaId) { mutableIntStateOf(0) }

    // ★ 歌词底部渐隐带**跟随三键显隐**：显示态用 `MeloXLandscapeLyricsFadeBottom`（56dp）
    //   让开三键；隐藏态收窄到 24dp（少 32dp = 三键在视口内的可见占位）⇒ 可读区下延进
    //   那 32dp，同时渐隐终点仍收在 Row 底（= 屏幕 333dp），不会溢出到屏幕外。
    //   ⚠ 只影响歌词页；且不改 `edgeFadeBottomTerminal`（横屏恒 0 = 归零点即视口底）。
    //   时长与三键 exit `slideOutVertically(220)` 对齐 ⇒ 三键消失到歌词下延几乎同步。
    val landscapeLyricsFadeBottom by animateDpAsState(
        targetValue = if (showsInterface) MeloXLandscapeLyricsFadeBottom
            else MeloXLandscapeLyricsFadeBottomHidden,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "landscapeLyricsFadeBottom",
    )

    fun select(destination: MeloXNowPlayingPage) {
        showsInterface = true
        activityGeneration += 1
        // ★ 再点一次当前页 ⇒ 退回**封面态**（`Artwork`）。
        //   ⚠ 2026-10-07 起**双栏也恢复这条开关**（旧注释「双栏不做退回封面页的开关 /
        //     封面页这一态不存在」已作废）：封面态在双栏下的表达 = **左列滑到屏幕正中、
        //     右列推出屏外**（见 `columnCenterProgress`），三键全不亮。
        //     它同时是回到"玩家卡片居中"那一态的**唯一入口**，所以两个分支合并成一条。
        onPageChanged(
            if (page == destination) MeloXNowPlayingPage.Artwork else destination,
        )
    }

    LaunchedEffect(page) {
        showsInterface = true
        activityGeneration += 1
    }
    LaunchedEffect(page, showsInterface, activityGeneration) {
        // ⚠ 双栏没有可隐藏的浮层（右列既无曲名栏也无三键），而 `isInterfaceHidden` 会改
        //   歌词面板自身的字号 ⇒ 若沿用自动隐藏，读词读一半字号会突然变化。双栏恒不隐藏。
        if (twoPane) return@LaunchedEffect
        // 只有歌词页需要沉浸：封面页靠三键翻页、队列页 padding 已给三键让位，两页恒显。
        if (page != MeloXNowPlayingPage.Lyrics || !showsInterface) return@LaunchedEffect
        delay(MeloXSettingsRuntime.lyricInterfaceAutoHideDelayMs.toLong())
        showsInterface = false
    }

    // ★★ 天际歌词「一镜到底」：歌词页左侧那枚大封面（与 SharedHost 持久封面同位置）
    //   ⇄ 天际里居中的 132dp 封面，走共享元素 `np-skyline-artwork`。
    //   · 源 = 下面主布局左 43% 区域里那枚 `AnimatedVisibility` 封面（`visible =
    //     page==Lyrics && !showSkyline`），位置/尺寸严格对齐 SharedHost 持久封面 ⇒
    //     与身后那枚（z 在上、带手势/切歌回弹）完全重合，静态看不出有两枚。
    //   · 目标 = 文末 `AnimatedVisibility(visible = showSkyline && page==Lyrics)` 里的
    //     `MeloXSkylineLyricsPanel` 那枚 132dp 封面。
    //   两端同属本 scene 所在的 `SharedTransitionLayout`，skyline 开关瞬间同时
    //   exit / enter ⇒ 封面从左侧「飞」到天际中央（进入 / 返回都一镜到底）。
    //   ⚠ 这里**不再 early-return**：天际作为覆盖层挂在主布局之上，主布局（歌词页）
    //     始终在底层渲染，返回时不会被重建。

    // ★★ 天际开启时把歌词页主内容整体淡出（退出时淡回）。⚠ 必须做：skyline 面板根是
    //   **透明**的（只有 clickable，无背景），主内容若继续渲染会整个透出来 —— 用户报
    //   「天际歌词直接盖在歌词页面上」（改覆盖层后两层同时可见）。用 alpha 而不是
    //   `if` 移除：morph 源封面在主内容里，组合必须存活到 exit 走完；alpha 不影响
    //   共享元素 overlay 的绘制（overlay 画在 SharedTransitionLayout 层，不吃这层 alpha）。
    //   360ms 与两端 AnimatedVisibility 的淡入淡出同拍 ⇒ 进入/返回都是干净的交叉淡化 + 飞行。
    //   触控无冲突：skyline 根 `fillMaxSize().clickable` 在最上层消费全部触摸。
    //   ⚠ 判据走 [meloXSkylineCanRender]（与 SharedHost 的源注册 / 天际层可见性同源）：
    //     平板双栏的**封面态**同样可以进天际 ⇒ 那时也要把整屏内容淡出。
    val landscapeContentHidden = showSkyline && meloXSkylineCanRender(page, twoPane)
    val landscapeContentAlpha by animateFloatAsState(
        targetValue = if (landscapeContentHidden) 0f else 1f,
        // 与天际 AV 淡入淡出 / bounds 飞行同一时长出口 ⇒ 三条时间线永远同拍。
        animationSpec = tween(meloXPlayerTweenDurationMillis(), easing = FastOutSlowInEasing),
        label = "landscapeSkylineContentAlpha",
    )

    // 根布局必须是 Box（不是 Column）：中间块容器的边界要与 SharedHost 封面层的
    // BoxWithConstraints **完全一致**（同样的 statusBars + cutout + horizontal 20，
    // 不吃 grabber 的流式高度、没有 vertical 2dp），这样同一份 maxHeight + 同一份
    // 居中 ⇒ 右列顶部（歌名/收藏）与底部（三键）跟大封面上下沿严格对齐。
    // grabber 改为顶部 overlay，不再挤压中间块。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .meloXPlayerStatusBarsPadding()
            // 机型适配：横屏挖孔摄像头所在的那一侧短边留白，内容不挡摄像头。
            .meloXPlayerDisplayCutoutPadding()
            .padding(horizontal = 20.dp)
            .alpha(landscapeContentAlpha),
    ) {
        // 中间「封面 + 右列」整块与 SharedHost 封面层的 BoxWithConstraints 边界完全一致
        // （同样的 statusBars + cutout + horizontal 20）⇒ 同一份 maxWidth / maxHeight。
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // ⚠ 与 SharedHost 持久封面**同一份**尺寸计算（单点在 `meloXLandscapeArtworkSize`），
            //   既给手机横屏的整块限高、也给天际共享元素源封面定尺寸（源 ⇄ 目标 132dp 形变）：
            //   手机横屏 = min(420dp, 85% 高, 42% 宽)；平板双栏还要扣掉左列封面下方整块。
            val landscapeSize = meloXLandscapeArtworkSize(maxWidth, maxHeight, twoPane)
            // 双栏「封面态」整行右移的像素量 = 行宽 × 0.285。行宽 == 本 Box 的 maxWidth
            // （Row 是 `fillMaxWidth`），也正是 SharedHost 封面层那一份 maxWidth。
            val columnShiftPx = constraints.maxWidth * MeloXTabletColumnCenterShiftFrac
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    // ⚠ 双栏**不加** maxContentWidth 限宽：SharedHost 的持久封面横向落点走
                    //   `maxWidth × 0.215`（= 左列正中），限宽会把这一行整体内缩 ⇒ 封面与左列
                    //   中心错开（屏越宽偏得越多）。手机横屏保持原限宽，逐像素不变。
                    .then(if (twoPane) Modifier else Modifier.widthIn(max = window.maxContentWidth))
                    .then(
                        if (twoPane) Modifier.fillMaxHeight()
                        else Modifier.height(landscapeSize),
                    )
                    // ★ 双栏「封面态」整行右移：左列列心 `0.215` → 行宽正中 `0.5`
                    //   （用户口径 2026-10-07：「不点击歌词按键时左边的部件向右移动到中心，
                    //     点击歌词后反向回来」）。
                    //   ⚠ 只平移**整行**、不改右列宽度 ⇒ 歌词不会被压缩重排，只是被推出屏外并
                    //     同步淡出（见下面右列的 alpha）。位移量必须与 SharedHost 持久封面的
                    //     `landscapeCenterXFrac` 同解，见 [MeloXTabletColumnCenterShiftFrac]。
                    //   ⚠ 用 `graphicsLayer` 的 lambda 读进度：每帧只重绘、不重组。
                    //   手机 / 竖屏 `columnCenterProgress` 恒 0 且不挂图层，逐像素不变。
                    .then(
                        if (twoPane) {
                            // ⚠ 用 `offset`（**真实布局位移**）而不是 `graphicsLayer.translationX`：
                            //   后者只改绘制、不改 `LayoutCoordinates` ⇒ 共享元素（`np-selector` /
                            //   `np-controls` / `np-lyrics`）会按**未位移**的坐标飞 ⇒ 转屏错位。
                            //   `offset {}` 的 lambda 里读进度：只重排、不重组。
                            Modifier.offset {
                                IntOffset((columnCenterProgress() * columnShiftPx).roundToInt(), 0)
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                if (twoPane) {
                    // ★ 平板双栏左列 = 封面占位 + 曲名/艺人行 + 控制条，整块在列内垂直居中。
                    //   内容宽度 = 封面边长且水平居中 ⇒ 与 SharedHost 的 `centerXFrac = 0.215`
                    //   （= 左列正中）严格同轴；**封面仍由 SharedHost 持久层绘制**，这里只占位。
                    //   ⚠ 整块高度固定 = `landscapeSize + Gap + ControlsBlock`，SharedHost 正是
                    //     据此反推封面纵向落点 ⇒ 三者必须同步。
                    Column(
                        modifier = Modifier.weight(.43f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.size(landscapeSize))
                        Spacer(Modifier.height(MeloXTabletLandscapeGap))
                        TabletLandscapePlayerColumn(
                            state = state,
                            // ⚠ 传**原始 `page`**（不是折叠后的 `panePage`）：封面态下三键要全不亮。
                            page = page,
                            favorite = favorite,
                            onShowActions = onShowActions,
                            onShowQuality = onShowQuality,
                            onPageSelected = ::select,
                            modifier = Modifier
                                .width(landscapeSize)
                                .height(MeloXTabletLandscapeControlsBlock),
                            selectorModifier = sharedTransitionScope.npMorphElement(
                                "np-selector",
                                animatedVisibilityScope,
                            ),
                        )
                    }
                } else {
                    // ★★ 横屏左列**不自绘封面**（2026-10-06 修「双封面」）：SharedHost 的持久封面
                    //   在横屏恒为左列大封面（`pageFrameProgress = if (isLandscape) 0f`），它已把
                    //   自己注册成 `np-skyline-artwork` 的共享元素源端（见 SharedArtworkDestination）。
                    //   这里若再画一枚等位副本，转屏进入横屏歌词 / 天际退出时会与持久封面叠成
                    //   「动画封面盖在一张一模一样的静态封面上」（用户报障）。恢复纯占位 Spacer，
                    //   本仓库铁律回归：**SharedHost 持有左列唯一 artwork 实例**。
                    Spacer(Modifier.weight(.43f).fillMaxHeight())
                }
                Spacer(Modifier.width(24.dp))

                Column(
                    modifier = Modifier
                        .weight(.57f)
                        .fillMaxHeight(),
                ) {
                    // 曲名栏（64dp）与底部三键（52dp）为浮层（不占流式高度），**不加任何底板 /
                    // 遮罩 / 模糊**（同上游原版 lladlam/MeloX-Android@c3ff9d2 与横屏封面页）。
                    // ★ 2026-09-30 遮挡改为分两种处理：
                    //   · **歌词页** —— 靠面板自身的「上下边缘渐隐」（`edgeFadeTop/Bottom`，
                    //     即 Apple Music 的 container edge fade）：歌词铺满整列，行在栏底 /
                    //     三键处淡出到透明。既不留硬性空白带，也不会被切出一道边。
                    //   · **队列页** —— 是列表、没有"渐隐"语义，仍用 `padding(top/bottom)` 让位。
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(
                            // ⚠ 双栏传 `panePage`（Artwork 一律折叠成 Lyrics）⇒ 右列只会在
                            //   「歌词 / 队列」之间切，封面页分支在双栏下永不进入。
                            targetState = if (twoPane) panePage else page,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = { meloXContentEnter() togetherWith meloXContentExit() },
                            label = "landscape-player-page",
                        ) { destination ->
                            when (destination) {
                                // ★ 双栏封面态 ⇒ 右列**真的留空**（见 `coverState` 的注释：
                                //   推出屏外 + 降 alpha 会留残影、转屏时还会看到歌词右移）。
                                //   非双栏（手机横屏封面页）逐像素保持原实现。
                                MeloXNowPlayingPage.Artwork -> if (twoPane) Box(
                                    Modifier.fillMaxSize(),
                                ) else Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(sharedTransitionScope.npMorphElement("np-controls", animatedVisibilityScope)),
                                ) {
                                    // 封面页按「曲名栏下方 ~ 底部三键上方」那块居中。嵌套一层，
                                    // 避免给共享元素本身加 padding（那会改掉 np-controls 的形变边界）。
                                    Box(
                                        Modifier.fillMaxSize().padding(top = 64.dp, bottom = 66.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        MeloXNowPlayingCoreControls(
                                            state = state,
                                            page = page,
                                            onShowQuality = onShowQuality,
                                            onPageSelected = ::select,
                                            showPageSelector = false,
                                            compact = true,
                                            // ★ 进度条 / 音量条左右**对称**内缩 18dp（用户明确要求）：
                                            //   左缘落到与曲名栏 / 底部三键最左那颗按钮同一基准，
                                            //   右侧同缩 ⇒ 中心仍与暂停键同轴（走带行不缩，暂停键
                                            //   仍在右列正中）。竖屏走默认 0.dp，逐像素不变。
                                            barsInset = MeloXCompactPageSelectorHPadding,
                                        )
                                    }
                                }

                                MeloXNowPlayingPage.Lyrics -> Box(
                                    Modifier
                                        .fillMaxSize()
                                        .then(sharedTransitionScope.npMorphElement("np-lyrics", animatedVisibilityScope)),
                                ) {
                                    MeloXIOSLyricsPanel(
                                        state = state,
                                        // ⚠ 2026-09-30：**不再给上下浮层留空 padding**。改为歌词铺满整列、
                                        //   由面板自身在上下边缘渐隐（`edgeFadeTop/Bottom`）——这是 Apple
                                        //   Music 的 container edge fade 做法（见面板内常量注释与 MEMORY.md）。
                                        //   相比"留空让位"：栏下不再有一条硬性空白带、末行也不再被三键压住，
                                        //   滚到栏后 / 三键下的行是"淡出"而不是"被切掉"。
                                        // ★ 左缘对齐：面板自身无内边距、但逐行自带 8dp ⇒ 再补 10dp，
                                        //   让歌词左缘与底部三键最左那颗按钮的左缘齐平（见本文件顶部契约）。
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(start = MeloXLandscapeLyricsStartInset),
                                        // ⚠ 双栏恒 false：右列没有可隐藏的浮层，而隐藏会改歌词
                                        //   字号（见上方自动隐藏 LaunchedEffect 的注释）。
                                        isInterfaceHidden = !twoPane && !showsInterface,
                                        controlsRevealHeight = if (twoPane) 0.dp else 56.dp,
                                        onInterfaceInteraction = {
                                            showsInterface = true
                                            activityGeneration += 1
                                        },
                                        onInterfaceVisibilityChange = {
                                            showsInterface = it
                                            if (it) activityGeneration += 1
                                        },
                                        // Skyline is an explicit alternate presentation. Enabling it
                                        // exposes the affordance but must not override the lyric style.
                                        allowAutomaticSkyline = false,
                                        active = lyricsActive,
                                        // ★ 字号：手机横屏整体缩小（见常量注释）；平板双栏右列是
                                        //   整屏高，回到面板默认倍率，不再收缩。
                                        fontScaleMultiplier = if (twoPane) {
                                            MeloXTabletLandscapeLyricFontMultiplier
                                        } else {
                                            MeloXLandscapeLyricFontMultiplier
                                        },
                                        // ★ 上下边缘渐隐 + 焦点行锚点（均只影响横屏；竖屏走默认值）。
                                        //   ⚠ 底部渐隐带**跟随三键显隐**（显示 56dp / 隐藏 24dp，
                                        //   见本文件顶部 `landscapeLyricsFadeBottom` 的注释），否则
                                        //   三键隐藏后底端仍会渐隐 ⇒ 用户报的「隐藏了但显示区没扩」。
                                        //   平板双栏右列没有任何浮层 ⇒ 上下对称的定值渐隐。
                                        edgeFadeTop = MeloXLandscapeLyricsFadeTop,
                                        edgeFadeBottom = if (twoPane) {
                                            MeloXTabletLandscapeLyricsFadeBottom
                                        } else {
                                            landscapeLyricsFadeBottom
                                        },
                                        // ★ 高亮行锚点抬到 0.47f（面板默认 0.30f 让当前行贴曲名栏
                                        //   下方 ~11dp ⇒ 视觉明显偏上）。见本文件顶部
                                        //   `MeloXLandscapeLyricsFocusPositionOverride` 的注释。
                                        focusPositionOverride = MeloXLandscapeLyricsFocusPositionOverride,
                                    )
                                }

                                MeloXNowPlayingPage.Queue -> Box(
                                    Modifier
                                        .fillMaxSize()
                                        .then(sharedTransitionScope.npMorphElement("np-queue", animatedVisibilityScope)),
                                ) {
                                    MeloXQueuePanel(
                                        state = state,
                                        // 队列是列表、不是歌词：仍然给两个浮层让位（顶部 64dp 曲名栏
                                        // + 底部 52dp 三键），否则首尾项会被永久遮住。
                                        // ★ 左缘对齐：行 / 分区标题本来贴 0，补满 18dp 与三键最左那颗
                                        //   按钮的左缘齐平（见本文件顶部契约）。
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(
                                                start = MeloXCompactPageSelectorHPadding,
                                                // ⚠ 那 64dp / 66dp 是给曲名栏与底部三键两个**裸浮层**
                                                //   让位的；双栏右列没有浮层（两者都在左列）⇒ 归零，
                                                //   列表铺满整列（同歌词页的"不留硬性空白带"口径）。
                                                top = if (twoPane) 0.dp else 64.dp,
                                                bottom = if (twoPane) 0.dp else 66.dp,
                                            ),
                                        showSongHeader = false,
                                        interactive = true,
                                    )
                                }
                            }
                        }

                        // ⚠ 平板双栏**不画**这两个浮层：右列是纯粹的「歌词 / 队列」，曲名行与
                        //   三键都已搬进左列常驻控制卡片（`TabletLandscapePlayerColumn`）——
                        //   留着会直接压在歌词上，且屏幕上出现两份曲名、两份三键。
                        //   ⚠ 同一枚三键的共享元素 key `np-selector` 随之挪到左列那枚上
                        //   （左右两处互斥渲染，树里始终只有一个）。
                        if (!twoPane) {
                            Box(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .then(
                                        sharedTransitionScope.npMorphElement("np-header", animatedVisibilityScope),
                                    ),
                            ) {
                                // 曲名栏保持裸浮层（同上游原版）：下方由歌词的上下边缘渐隐 / 队列的
                                // 内容让位避开，不会被任何内容"实心"压住，所以不铺底板。
                                LandscapeSongHeader(
                                    state = state,
                                    favorite = favorite,
                                    onShowActions = onShowActions,
                                    onOpenQueue = { onPageChanged(MeloXNowPlayingPage.Queue) },
                                    onShowSkyline = if (
                                        page == MeloXNowPlayingPage.Lyrics && MeloXSettingsRuntime.skylineEnabled
                                    ) onShowSkyline else null,
                                )
                            }

                            // 横屏底部三个常驻按钮：歌词 / 播放设备 / 列表。
                            // 抽出为独立于页面的底栏，歌词页与队列页都不再各自带选择器。
                            // 真机量测（iQOO 15 截图）：42dp 触控行内图标垂直居中会让图标底
                            // 高出封面底 ~10dp，整体下移让图标底与封面底平齐。
                            // ★ 自动显隐（与歌词面板界面共用 `showsInterface`）：歌词页静置
                            //   一段时间后连同歌词界面一起淡出，把那 42dp 一整条还给歌词 ——
                            //   三键是**裸浮层、无底板**，歌词本就从它身后透出，遮着等于白压住
                            //   最下面可读的那几行。唤出仍走歌词面板根节点的 pass-through tap /
                            //   滑动（`onInterfaceInteraction`），**不另设捕获层**（见
                            //   `MeloXIOSLyricsPanel` 根 Box 的 `pointerInput` 注释：压在列表上
                            //   的覆盖层会让被盖住的歌词完全滑不动）。
                            //   封面页恒显（翻页入口）、队列页恒显（列表 padding 已让位 66dp）。
                            LandscapeSelectorVisibility(
                                visible = page != MeloXNowPlayingPage.Lyrics || showsInterface,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .offset(y = MeloXLandscapeSelectorOffsetY),
                            ) {
                                // 三键同为裸浮层（同上游原版）：歌词由底部边缘渐隐避开、队列由
                                // contentPadding 让位避开，不铺底板。
                                ScenePageSelector(
                                    state = state,
                                    page = page,
                                    onPageSelected = ::select,
                                    compact = true,
                                    modifier = Modifier.then(
                                        sharedTransitionScope.npMorphElement("np-selector", animatedVisibilityScope),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        // grabber overlay：不再占流式高度（否则会把中间块往下挤，破坏与大封面对齐）。
        // ★ 横屏单点小白条也正常退出（与「下拉小白条 / 系统返回」同一条 onDismiss 退出路径）。
        SceneGrabber(onDismiss = onDismiss, dragModifier = grabberDragModifier, tapToDismiss = true)
    }

    // ★★ 天际歌词「一镜到底」目标层：覆盖在主布局（歌词页）之上。
    //   `AnimatedVisibility` 的 `this` 即共享元素的 target scope；skyline 开启瞬间本层
    //   enter ⇒ 同 key `np-skyline-artwork` 从 SharedHost 持久大封面（源端，注册见
    //   SharedArtworkDestination）一镜到底飞入天际中央 132dp 封面。返回时本层 exit ⇒ 飞回。
    //   ⚠ enter/exit 用 360ms 淡入淡出：给 morph 飞行时长；封面过渡期由
    //   overlay 渲染（实心飞行），原地残影被盖住 ⇒ 看不到淡变。覆盖层之下歌词页始终在渲染，返回时不会被重建。
    AnimatedVisibility(
        // ⚠ 与 SharedHost 的 `skylineSourceModifier` 注册判据**逐字同源**（[meloXSkylineCanRender]）：
        //   两端条件不一致时源/目标会在同一帧里只挂上一端 ⇒ 共享元素直接不配对（封面瞬移）。
        visible = showSkyline && meloXSkylineCanRender(page, twoPane),
        // ★ 同源封面：给真实时长让 morph 有飞行时间（见上方源封面注释）。
        //   ⚠ 时长走 meloXPlayerTweenDurationMillis()（与 boundsTransform 同源）：用户改
        //   设置时长时淡入淡出与飞行保持同拍，不再各走各的。
        enter = fadeIn(tween(meloXPlayerTweenDurationMillis(), easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(meloXPlayerTweenDurationMillis(), easing = FastOutSlowInEasing)),
    ) {
        val skylineTargetScope = this
        Box(Modifier.fillMaxSize()) {
            // 圆角插值 clip 需要 sourceSize（= 横屏大封面 landscapeSize）⇒ 用与主布局 /
            // SharedHost **同一份公式**重算（此处不在那个 BoxWithConstraints 作用域内）。
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val landscapeSize = meloXLandscapeArtworkSize(maxWidth, maxHeight, twoPane)
                val skylineClip = remember(landscapeSize) {
                    meloXSkylineArtworkOverlayClip(
                        // 12dp = SharedHost 源端 lerpDp(6,12,expansionProgress) 的全展开态。
                        sourceRadius = 12.dp,
                        targetRadius = 20.dp,
                        sourceSize = landscapeSize,
                        targetSize = 132.dp,
                    )
                }
                MeloXSkylineLyricsPanel(
                    playback = state,
                    modifier = Modifier.fillMaxSize(),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = skylineTargetScope,
                    skylineOverlayClip = skylineClip,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .meloXPlayerStatusBarsPadding()
                    .padding(top = 12.dp, end = 20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = .24f))
                    .clickable(onClick = onHideSkyline)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "退出天际歌词",
                    color = Color.White.copy(alpha = .86f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * 平板横屏双栏 · 左列「曲名/艺人行 + 常驻控制条」（2026-10-07）。
 *
 * 即参考图左列那块，自上而下：曲名 / 艺人（左）＋ 收藏 / 更多（右）→ 进度 + 走带 + 音量 +
 * 三键（歌词 / 播放设备 / 列表）。控制条**直接复用**竖屏底部那条同款组件
 * [MeloXNowPlayingCoreControls]（连 `ScenePageSelector` 一起）⇒ 同一控件在竖屏与平板横屏下
 * 的比例、材质、触感必然一致，不另写一套。
 *
 * ⚠ 高度由调用方**固定**成 `MeloXTabletLandscapeControlsBlock`（= 本行 + [MeloXTabletLandscapeMetaGap]
 *   + 控制条高）。SharedHost 正是靠这个定值反推持久封面的纵向落点 ⇒ 任何高度调整必须两端同步，
 *   否则封面会相对控制卡片上下错位。
 */
@Composable
private fun TabletLandscapePlayerColumn(
    state: MeloXPlaybackUiState,
    page: MeloXNowPlayingPage,
    favorite: MeloXTrackFavorite,
    onShowActions: () -> Unit,
    onShowQuality: () -> Unit,
    onPageSelected: (MeloXNowPlayingPage) -> Unit,
    modifier: Modifier = Modifier,
    selectorModifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // 与竖屏封面页的曲名行同参（20sp / 20sp、艺人 alpha .64、收藏 18dp、更多 16sp），
        // 保证同一条信息在竖屏与平板横屏下读起来是同一个东西。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MeloXTabletLandscapeMetaHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MeloXPlaybackTitleMenu(
                state = state,
                onOpenQueue = { onPageSelected(MeloXNowPlayingPage.Queue) },
                modifier = Modifier.weight(1f),
                titleSize = 20.sp,
                titleLineHeight = 24.sp,
                artistSize = 20.sp,
                artistLineHeight = 24.sp,
            )
            MeloXCircleIconButton(onClick = favorite.toggle) {
                MeloXAnimatedFavoriteStar(
                    liked = favorite.liked == true,
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                )
            }
            MeloXCircleIconButton(onClick = onShowActions) {
                MeloXSymbolIcon(MeloXSymbol.Ellipsis, Modifier.size(18.dp), Color.White, iconSize = 16.sp)
            }
        }

        Spacer(Modifier.height(MeloXTabletLandscapeMetaGap))

        // 进度 / 走带 / 音量 / 三键。左列内容宽 = 封面边长 ⇒ 两条 bar 天然与封面左右齐平
        // （不需要横屏封面页那套 `barsInset` 对称内缩）。
        //
        // ⚠⚠⚠ `compact` **必须 false**（2026-10-07 用户报障「平板横屏音量按钮一层扩大」根因）：
        //   `MeloXNowPlayingControlsHeight = 279` 这个定值**是按竖屏档堆出来的**
        //   —— 进度 52 + 19 + 走带 82 + 31 + 音量 42 + 3 + 三键 50 = 279。
        //   本列是"手机竖屏样式的竖向卡片"（用户对双栏的原始定义），所以它必须吃**竖屏档**。
        //   一旦传 `compact = true`：
        //     ① 孩子高度变成 52 + 14 + 70 + 22 + 42 + 3 + 42 = **245dp**，
        //        比定值 279dp 少 34dp ⇒ 列内底部凭空多出 34dp 死区，
        //        整块看着**偏心**（真机实测：上留白 30dp / 下留白 85dp，差值 34.5dp 对得上）；
        //     ② [SceneVolumeControl] 的喇叭图标会切到**横屏放大档**：盒 18×24 / 36×24dp
        //        （墨迹高 24dp），而竖屏档是 12dp / 15dp ⇒ 喇叭比同列其它字形（三键 22dp 档、
        //        这里本该 12dp）大出一倍，观感就是"音量按钮多出一层/被放大"。
        //   ⇒ 这一处同时决定了**列高是否贴合 279dp** 与**音量喇叭的尺寸档**，不能跟着横屏走。
        MeloXNowPlayingCoreControls(
            state = state,
            page = page,
            onShowQuality = onShowQuality,
            onPageSelected = onPageSelected,
            showPageSelector = true,
            compact = false,
            useVectorGlyphs = true,
            selectorModifier = selectorModifier,
        )
    }
}

/**
 * 当前播放曲目的收藏状态 + 切换回调，复用于播放页三个头部（竖屏主头部、横屏头部、
 * 封面页头部）。songId 由 [MeloXPlaybackUiState.mediaId] 反解（与
 * [MeloXIOSNowPlayingV2] 同一条取法）；未登录 / 取状态失败时回退为「未收藏」。
 */
private data class MeloXTrackFavorite(
    val liked: Boolean?,
    val toggle: () -> Unit,
)

@Composable
private fun rememberTrackFavorite(state: MeloXPlaybackUiState): MeloXTrackFavorite {
    val context = LocalContext.current
    val app = context.applicationContext
    val mediaId = state.mediaId
    val identity = remember(mediaId) { mediaId?.let(PlaybackTrackIdentity::decode) }
    val unifiedFavoritesEnabled = MeloXUnifiedFavorites.enabled(app)
    val unifiedFavoritesRevision = MeloXUnifiedFavorites.revision
    val track = remember(identity, state.title, state.artist, state.album, state.artworkUrl, state.durationMs) {
        identity?.let { id ->
            MusicTrack(
                id = id,
                title = state.title.ifBlank { "未知歌曲" },
                artists = listOf(MusicArtistRef(name = state.artist.ifBlank { "未知歌手" })),
                album = state.album.takeIf(String::isNotBlank)?.let { MusicAlbumRef(name = it) },
                artworkUrl = state.artworkUrl,
                durationMs = state.durationMs.takeIf { it > 0L },
                availability = TrackAvailability.Playable,
                providerMetadata = when (id.sourceId.builtIn) {
                    MusicSource.Netease -> ProviderTrackMetadata.Netease(
                        numericId = id.value.toLongOrNull() ?: 0L,
                    )
                    MusicSource.QQMusic -> ProviderTrackMetadata.QQMusic(songMid = id.value)
                    MusicSource.Kugou, MusicSource.KugouLite ->
                        ProviderTrackMetadata.Kugou(hash = id.value)
                    MusicSource.Kuwo -> ProviderTrackMetadata.Kuwo(
                        mid = id.value.toLongOrNull() ?: 0L,
                    )
                    MusicSource.AppleMusic -> ProviderTrackMetadata.AppleMusic(
                        catalogId = id.value,
                        storefront = "us",
                    )
                    MusicSource.Bilibili -> com.lladlam.melox.core.provider.bilibili.BilibiliProvider
                        .parseIdentity(id.value)
                        ?.let { (bvid, cid) -> ProviderTrackMetadata.Bilibili(bvid, cid) }
                        ?: ProviderTrackMetadata.Empty
                    MusicSource.Spotify -> ProviderTrackMetadata.Spotify(trackId = id.value)
                    MusicSource.YouTubeMusic -> ProviderTrackMetadata.YouTube(videoId = id.value)
                    MusicSource.Local -> ProviderTrackMetadata.Local(
                        contentUri = LocalMusicRepository(app).track(id.value)?.contentUri.orEmpty(),
                        fileKey = id.value,
                    )
                    MusicSource.Jellyfin, MusicSource.Qishui -> ProviderTrackMetadata.Empty
                    null -> ProviderTrackMetadata.Empty
                },
            )
        }
    }
    val cookieProvider = remember(app) { { NeteaseSessionStore.readCookie(app) } }
    val library = remember(app) { NeteaseLibraryClient(cookieProvider = cookieProvider) }
    val account = remember(app) { NeteaseSearchClient(cookieProvider = cookieProvider) }
    val scope = rememberCoroutineScope()

    // ★ 用显式 State 而不是 `var by remember { mutableStateOf(...) }` 委托 —— 委托属性
    //   在 remember(…, scope) { lambda } 的 lambda 内部被引用时，编译器对 `liked = x`
    //   的解析路径可能走 delegate setter 但走 lambda 捕获时的 getter，跨 composition 边界
    //   有微妙的「快照」行为，是「连点几次后按钮失效」的隐藏放大器。显式 State 保证
    //   lambda 内的 `likedState.value` 每次读都是最新值，与外面 re-composition 的
    //   `val liked = likedState.value` 读的是同一份实时快照。
    val likedState = remember(mediaId) { mutableStateOf<Boolean?>(null) }
    val liked = likedState.value

    // ★ 序列号：每一次 tap 都递增，只有 seq==mine 的请求回写才能落地。
    //   放在 State 里以便 LaunchedEffect（拉取服务器真值）也能读，用于「查询期间用户
    //   已 tap 过就不再覆盖」这一判断。
    val seqState = remember { mutableIntStateOf(0) }

    // ★ 首屏查询（切歌后拉取当前收藏集合）。seq 判据避免覆盖刚发生的乐观 tap。
    LaunchedEffect(mediaId, track, unifiedFavoritesEnabled, unifiedFavoritesRevision) {
        likedState.value = null
        if (unifiedFavoritesEnabled) {
            likedState.value = track?.let { MeloXUnifiedFavorites.contains(app, it) }
            return@LaunchedEffect
        }
        val id = identity?.takeIf { it.sourceId.builtIn == MusicSource.Netease }
            ?.value?.toLongOrNull() ?: return@LaunchedEffect
        val querySeqAtStart = seqState.value
        runCatching {
            val profile = account.accountProfile()
            withContext(Dispatchers.IO) { library.likedSongIdsBlocking(profile.userId) }.contains(id)
        }.onSuccess {
            // 查询期间若用户已 tap 过（seq 变了），结果作废 —— 不能让「服务器旧值」
            //   抹掉「用户刚点的乐观状态」，这是「点了几次之后按钮没反应」的核心根因。
            if (seqState.value == querySeqAtStart) likedState.value = it
        }.onFailure {
            if (seqState.value == querySeqAtStart) likedState.value = false
        }
    }

    val toggle = remember(mediaId, track, unifiedFavoritesEnabled, scope) {
        {
            if (unifiedFavoritesEnabled) {
                track?.let { likedState.value = MeloXUnifiedFavorites.toggle(app, it) }
                return@remember
            }
            val id = identity?.takeIf { it.sourceId.builtIn == MusicSource.Netease }
                ?.value?.toLongOrNull() ?: return@remember
            // Show immediate feedback; the background writer reports failure.
            val desired = likedState.value != true
            likedState.value = desired
            val mine = ++seqState.value
            com.lladlam.melox.core.network.NeteaseFavoriteSync.submit(app, id, desired) { result ->
                result.onFailure { error ->
                    if (seqState.value == mine) likedState.value = null
                    MeloXPlaybackNotice.show(app, "收藏状态同步失败：${error.message ?: "请稍后重试"}")
                }
            }
        }
    }
    return MeloXTrackFavorite(liked, toggle)
}

/**
 * 播放页头部圆形图标按钮：40.dp 圆 + 白 10% 底（与示意图一致），菜单键与收藏键共用，
 * 保证两个按钮视觉重量一致。
 */
@Composable
private fun MeloXCircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(30.dp)
            .background(Color.White.copy(alpha = 0.10f), CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ── 天际歌词入口图标（横贯出框 · R2 上长下短）────────────────────────────
//  构图：左侧连续曲率封面 + 右侧三行歌词 + 横贯圆框的地平线。
//  ⚠ 三行的**重心在中间那行**（= 当前句，最长最粗最亮），上下两行收短变淡 ——
//    真实天际歌词就是这个重心分布，不是「首行最重」。
//  ⚠ 地平线不两端收在墨迹内，而是**横贯整个圆框并被圆边裁断**，读作延伸出画面的
//    天际线；两端各留一段渐隐过渡（alpha 0.06→0.82），硬裁会像"被刀切"。
//  ⚠ 地平线线宽必须给到 ≈ 中间行的 78%（1.05/1.35）。早先 0.6dp/α0.55 的细线既
//    不像地平线，又因为向两端远伸出墨迹之外而读成「锤柄」——和左侧方块凑成锤子。
//  ⚠ 同理把封面↔中行间隙从 1.7 拉到 3.2dp：锤头与锤柄必须断开一段距离，
//    贴在一起时不管线多粗都还是锤子。
//  ⚠ 上下两行的 alpha 必须 ≥ 0.5：18dp 实机里 0.28 那条基本只剩一个痕迹。
//    且上下**长度差拉到 1.9×（5.0/2.6）** —— 小尺寸下长度差比 alpha 差更不易被
//    渲染糊掉，同时破掉"三条等长像菜单图标"的规整感。
//  ⚠ 中行线宽 1.35 而非 1.4：只比地平线重 0.35dp 时中行读成"和地平线一样粗"，
//    焦点凸不出来。
private const val MELOX_SKYLINE_COVER = 6.0f      // 封面边长 dp
private const val MELOX_SKYLINE_COVER_R = 1.55f   // 封面圆角半径 dp（连续曲率 n=2.5）
private const val MELOX_SKYLINE_COVER_W = 0.8f    // 封面描边 dp
private const val MELOX_SKYLINE_GAP_X = 3.2f      // 封面 ↔ 歌词列 水平间隙 dp（去锤柄的关键）
private const val MELOX_SKYLINE_GAP_Y = 2.4f      // 行间距 dp
private const val MELOX_SKYLINE_GAP_BL = 3.6f     // 歌词列 ↔ 地平线 dp
private const val MELOX_SKYLINE_MID_LEN = 8.4f    // 中间行（当前句）长度 dp
private const val MELOX_SKYLINE_UP_LEN = 5.0f
private const val MELOX_SKYLINE_DN_LEN = 2.6f
private const val MELOX_SKYLINE_MID_W = 1.35f
private const val MELOX_SKYLINE_SIDE_W = 0.80f
private const val MELOX_SKYLINE_UP_A = 0.78f    // 上行不透明度（18dp 里要 ≥0.7 才稳定可辨）
private const val MELOX_SKYLINE_DN_A = 0.50f    // 下行：主次靠长度差（5.0/2.6 ≈ 1.9×）而非只靠 alpha
private const val MELOX_SKYLINE_BASE_W = 1.05f    // 地平线线宽 dp（= 中间行的 78%）
private const val MELOX_SKYLINE_BASE_A = 0.82f    // 地平线峰值 alpha
private const val MELOX_SKYLINE_FADE = 0.14f      // 两端渐隐占比（各自）
private const val MELOX_SKYLINE_EXP = 2.5f        // Lamé 指数：越大越"方"

/**
 * 天际歌词入口字形。画在 [boxSize] 方框内，按**整块墨迹的外接矩形**居中
 * （只按封面居中会把整块墨迹的重心拖到右边 —— 封面右侧还有歌词列和地平线）。
 *
 * @param boxSize 画布边长 dp —— 取 30dp（= 按钮圆框直径），地平线才能横贯到圆边被裁断。
 */
@Composable
private fun MeloXSkylineGlyph(
    modifier: Modifier = Modifier,
    boxSize: Dp = 30.dp,
    color: Color = Color.White,
) {
    val steps = 24
    Canvas(
        modifier = modifier
            .size(boxSize)
            .semantics { contentDescription = "天际歌词" },
    ) {
        // ⚠ DrawScope 自身实现 Density，直接 Dp.toPx() 即可换算像素；
        //   不能去读 Density.density 属性（需额外 import 且易与局部遮蔽）。
        fun px(dp: Float): Float = dp.dp.toPx()

        val cover = px(MELOX_SKYLINE_COVER)
        val gapX = px(MELOX_SKYLINE_GAP_X)
        val gapY = px(MELOX_SKYLINE_GAP_Y)
        val gapBl = px(MELOX_SKYLINE_GAP_BL)
        val midLen = px(MELOX_SKYLINE_MID_LEN)

        // 整块墨迹包围盒（地平线横贯出框、无限宽，不参与居中计算）
        val inkW = cover + gapX + midLen
        val inkH = cover + gapBl
        val left = (size.width - inkW) / 2f
        val top = (size.height - inkH) / 2f

        // ── 封面：Lamé 超椭圆连续曲率（n=2.5）+ 尺寸补偿 ──────────────
        //  外接半径 ×spanFactor 使对角切深与同半径正圆弧一致：角看起来一样大，
        //  但曲率是连续长起来的，而不是正圆弧与直边硬接。
        val e = 2f / MELOX_SKYLINE_EXP
        val spanFactor =
            ((1.0 - 2.0.pow(-0.5)) / (1.0 - 2.0.pow(-1.0 / MELOX_SKYLINE_EXP))).toFloat()
        val span = (px(MELOX_SKYLINE_COVER_R) * spanFactor).coerceAtMost(cover / 2f)

        val coverPath = Path()
        fun corner(cx: Float, cy: Float, dx: Float, dy: Float, tFrom: Double, tTo: Double) {
            for (i in 0..steps) {
                val t = tFrom + (tTo - tFrom) * i / steps
                coverPath.lineTo(
                    cx + dx * span * cos(t).toFloat().coerceAtLeast(0f).pow(e),
                    cy + dy * span * sin(t).toFloat().coerceAtLeast(0f).pow(e),
                )
            }
        }
        coverPath.moveTo(left + span, top)
        coverPath.lineTo(left + cover - span, top)
        corner(left + cover - span, top + span, 1f, -1f, Math.PI / 2, 0.0)
        coverPath.lineTo(left + cover, top + cover - span)
        corner(left + cover - span, top + cover - span, 1f, 1f, 0.0, Math.PI / 2)
        coverPath.lineTo(left + span, top + cover)
        corner(left + span, top + cover - span, -1f, 1f, Math.PI / 2, 0.0)
        coverPath.lineTo(left, top + span)
        corner(left + span, top + span, -1f, -1f, 0.0, Math.PI / 2)
        coverPath.close()

        drawPath(
            path = coverPath,
            color = color,
            style = Stroke(width = px(MELOX_SKYLINE_COVER_W)),
        )

        // ── 三行歌词 ──────────────────────────────────────────────────
        //  中间行（当前句）与封面中心严格同高 ⇒ 视觉重心落在中间。
        val x0 = left + cover + gapX
        val centerY = top + cover / 2f
        fun lyric(offY: Float, lenDp: Float, strokeDp: Float, alpha: Float) {
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(x0, centerY + offY),
                end = Offset(x0 + px(lenDp), centerY + offY),
                strokeWidth = px(strokeDp),
                cap = StrokeCap.Round,
            )
        }
        lyric(-gapY, MELOX_SKYLINE_UP_LEN, MELOX_SKYLINE_SIDE_W, MELOX_SKYLINE_UP_A)
        lyric(0f, MELOX_SKYLINE_MID_LEN, MELOX_SKYLINE_MID_W, 1f)
        lyric(gapY, MELOX_SKYLINE_DN_LEN, MELOX_SKYLINE_SIDE_W, MELOX_SKYLINE_DN_A)

        // ── 地平线：横贯整个画布、被圆框裁断；两端渐隐 ──────────────────
        //  alpha 沿 x 走 0.06 → 0.82 → 0.82 → 0.06；用 Brush 做原生渐变，
        //  避免逐段 drawLine 留下接缝。
        val baseY = top + inkH
        val halfW = size.width / 2f
        val solidHalf = halfW * (1f - MELOX_SKYLINE_FADE)
        val peak = MELOX_SKYLINE_BASE_A
        drawLine(
            brush = Brush.horizontalGradient(
                0f to color.copy(alpha = 0.06f),
                (0.5f - MELOX_SKYLINE_FADE / 2f) to color.copy(alpha = peak),
                (0.5f + MELOX_SKYLINE_FADE / 2f) to color.copy(alpha = peak),
                1f to color.copy(alpha = 0.06f),
                startX = halfW - solidHalf,
                endX = halfW + solidHalf,
            ),
            start = Offset(0f, baseY),
            end = Offset(size.width, baseY),
            strokeWidth = px(MELOX_SKYLINE_BASE_W),
        )
    }
}

/**
 * 收藏星星的动画版本，替代原 [MeloXSymbolIcon] 的 `Star` 硬切换。
 *
 * - **收藏（liked=true）**：实心星**从中心向外扩张**填满（扩张半径 0→outer），
 *   并带一次**过冲放大的回弹**（先瞬弹到 1.30×，再用欠阻尼弹簧回落到 1）。
 * - **取消收藏（liked=false）**：实心星从中心**缩回消失**（半径 outer→0），并带一次
 *   **收缩回弹**（先瞬缩到 0.72×，再弹回 1）。
 *
 * 几何与 `MeloXSymbol.Star` 矢量完全一致（outer=min/2×0.92、innerRatio=0.40、
 * 描边≈outer×0.16）；描边与填充同尺寸，填充严格落在描边内，保证“空心→实心”连续过渡、
 * 而非两个独立字形的硬切。
 */
/** 10 个星形顶点（偶数=外点、奇数=内点，i=0 在正上方；单位 = drawOuter）。
 *  与 `MeloXSymbol.Star` 的 `angle = -PI/2 + i*PI/5`、`innerRatio = 0.40` 逐值一致。 */
private val MELOX_STAR_VERTEX_X = listOf(
    0f, 0.587785252f, 0.951056516f, 0.951056516f, 0.587785252f,
    0f, -0.587785252f, -0.951056516f, -0.951056516f, -0.587785252f,
)

private val MELOX_STAR_VERTEX_Y = listOf(
    -1f, -0.809016994f, -0.309016994f, 0.309016994f, 0.809016994f,
    1f, 0.809016994f, 0.309016994f, -0.309016994f, -0.809016994f,
)

/** 以 (cx, cy) 为中心、外点半径 [outerR] 构造五角星路径（内点 = outerR × 0.40）。 */
private fun starPathFor(cx: Float, cy: Float, outerR: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outerR else outerR * 0.40f
        val x = cx + r * MELOX_STAR_VERTEX_X[i]
        val y = cy + r * MELOX_STAR_VERTEX_Y[i]
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

@Composable
private fun MeloXAnimatedFavoriteStar(
    liked: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    contentDescription: String? = if (liked) "取消收藏" else "收藏",
) {
    val fill = animateFloatAsState(
        targetValue = if (liked) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "favorite-fill",
    )
    val scale = remember { Animatable(1f) }
    var firstRun by remember { mutableStateOf(true) }
    LaunchedEffect(liked) {
        if (firstRun) {
            // 首帧不播放回弹，避免已收藏曲目一进页面就“弹”一下。
            firstRun = false
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        // 过冲：收藏先放大、取消先收缩，再弹簧回弹到 1。
        scale.snapTo(if (liked) 1.30f else 0.72f)
        scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 500f))
    }
    Canvas(
        modifier = modifier
            .meloXIconDrawingScale()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .then(
                if (contentDescription != null) {
                    Modifier.semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescription
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = min(size.width, size.height) / 2f * 0.92f
        val strokeW = outer * 0.16f
        // 描边态沿中心线绘制：把路径内缩半个描边宽，使外缘恰好落在 outer 上；
        // 填充用同一 drawOuter，保证填充严格落在描边内、与空心态视觉大小一致。
        val drawOuter = outer - strokeW / 2f
        // 空心描边（始终可见）；中心线取 drawOuter，外缘恰好落在 outer 上。
        drawPath(
            starPathFor(cx, cy, drawOuter),
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // 填充：从中心向外扩张。fill=1 时外点半径到 outer（与旧实心态几何完全重合），
        // 因此静止态 = 描边外缘 + 实心填满，视觉上是一整颗实心星。
        if (fill.value > 0.001f) {
            drawPath(starPathFor(cx, cy, outer * fill.value), color = color)
        }
    }
}

@Composable
private fun LandscapeSongHeader(
    state: MeloXPlaybackUiState,
    favorite: MeloXTrackFavorite,
    onShowActions: () -> Unit,
    onOpenQueue: () -> Unit,
    onShowSkyline: (() -> Unit)? = null,
) {
    // ★ 天际按钮丝滑过渡（2026-09-30）：横屏从「队列」切到「歌词」时，右上角从
    //   「收藏 + 省略号」两颗按钮变成「收藏 + 天际 + 省略号」三颗。原来用
    //   `if (onShowSkyline != null)` 硬切，收藏和省略号两颗按钮的位置会瞬移。
    //   现在用一个 progress 同步驱动三件事：
    //     ①天际按钮槽宽度 0 ↔ 30dp（让邻居平滑内滑）
    //     ②天际按钮槽与其左侧收藏的间隙 0 ↔ 14dp
    //     ③天际按钮自身淡入淡出
    //   Row 里的固定 `spacedBy(14.dp)` 改用显式 Spacer，才能让这三段同步动画。
    val skylineVisible = onShowSkyline != null
    val skylineProgress by animateFloatAsState(
        targetValue = if (skylineVisible) 1f else 0f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "header-skyline-progress",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            // ★ 左缘对齐（2026-09-30）：与底部三键里最左那颗按钮的左缘齐平。padding 放在
            //   fillMaxWidth / height **之后** ⇒ 行本身仍是「满宽 × 64dp」，
            //   np-header 的形变边界不变，只有内容右移 18dp。
            .padding(start = MeloXCompactPageSelectorHPadding),
        // 顶对齐（非居中）：让收藏圈顶和歌名行顶贴住大封面顶沿（真机量测
        // 居中时歌名 cap 比封面顶低 ~15dp）。文字行高自带的 ~4dp cap 空隙保留。
        verticalAlignment = Alignment.Top,
    ) {
        MeloXPlaybackTitleMenu(
            state = state,
            onOpenQueue = onOpenQueue,
            modifier = Modifier.weight(1f),
            titleSize = 19.sp,
            titleLineHeight = 23.sp,
            artistSize = 15.sp,
            artistLineHeight = 19.sp,
            showHeartModeLabel = true,
        )
        Spacer(Modifier.width(14.dp))
        MeloXCircleIconButton(onClick = favorite.toggle) {
            MeloXAnimatedFavoriteStar(
                liked = favorite.liked == true,
                modifier = Modifier.size(18.dp),
                color = Color.White,
            )
        }
        // 天际按钮槽与其左侧收藏的间隙：随 progress 从 0 涨到 14dp。
        Spacer(Modifier.width(14.dp * skylineProgress))
        // 天际按钮槽：宽度随 progress 从 0 涨到 30dp。按钮始终在槽内居中，通过
        // clip 从中心向两侧「展开」（水平弦切），alpha 同步淡入淡出。enabled 直接
        // 绑 skylineVisible ⇒ 隐藏过程中即便槽还有残余像素也不会拦截点击。
        Box(
            modifier = Modifier
                .width(30.dp * skylineProgress)
                .clip(RectangleShape),
            contentAlignment = Alignment.Center,
        ) {
            val onShowSkylineAction: () -> Unit = onShowSkyline ?: {}
            MeloXCircleIconButton(
                onClick = onShowSkylineAction,
                enabled = skylineVisible,
                modifier = Modifier.alpha(skylineProgress),
            ) {
                // ★ 自绘字形：左侧连续曲率封面 + 右侧三行歌词（重心在中间行 = 当前句）
                //   + 横贯圆框被裁断的地平线（两端渐隐）。画布取 30dp = 圆框直径，
                //   地平线才能真正顶到圆边。之前这里用 "↗"，映射到 SF Share 字形、
                //   语义是"分享"，与天际歌词无关。
                MeloXSkylineGlyph(
                    color = Color.White.copy(alpha = .84f),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        MeloXCircleIconButton(onClick = onShowActions) {
            MeloXSymbolIcon(MeloXSymbol.Ellipsis, Modifier.size(18.dp), Color.White, iconSize = 16.sp)
        }
    }
}

@Composable
private fun SceneGrabber(
    onDismiss: () -> Unit,
    dragModifier: Modifier,
    // 横屏现已放开单点退出：横屏调用点传 true，**点**小白条也走 onDismiss 正常退出
    //   （点击仍由本 Box 消费，不会穿透到下层内容）；下拉拖拽同样调用 onDismiss。
    tapToDismiss: Boolean = true,
) {
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "scene-grabber-scale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .then(dragModifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { if (tapToDismiss) onDismiss() },
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(width = 72.dp, height = 4.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.52f)),
        )
    }
}

@Composable
private fun ArtworkDetailsWithoutArtwork(
    state: MeloXPlaybackUiState,
    favorite: MeloXTrackFavorite,
    onShowActions: () -> Unit,
    onOpenQueue: () -> Unit,
    headerMorphModifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val artworkSize = maxOf(
            170.dp,
            minOf(maxWidth, maxHeight - 92.dp),
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(artworkSize))
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(headerMorphModifier)
                    // 与顶部 header 同参：32 + 8 = 40dp 两侧对称，且封面页↔歌词/列表
                    // 页切换时收藏/菜单按钮不跳位。
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MeloXPlaybackTitleMenu(
                    state = state,
                    onOpenQueue = onOpenQueue,
                    modifier = Modifier.weight(1f),
                    titleSize = 20.sp,
                    titleLineHeight = 24.sp,
                    artistSize = 20.sp,
                    artistLineHeight = 24.sp,
                )
                MeloXCircleIconButton(onClick = favorite.toggle) {
                    MeloXAnimatedFavoriteStar(
                        liked = favorite.liked == true,
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                    )
                }
                MeloXCircleIconButton(onClick = onShowActions) {
                    MeloXSymbolIcon(MeloXSymbol.Ellipsis, Modifier.size(18.dp), Color.White, iconSize = 16.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.height(MeloXNowPlayingControlsHeight.dp))
            // ⚠ 这里**不要**再加 [MeloXPortraitArtworkRaise]：歌名栏要保持在原位（贴控制条上方），
            //   上移只作用于**封面**（见 `SharedArtworkDestination` 的 portraitY）⇒ 只有封面往上走，
            //   歌名栏不动。（两端同加会让歌名栏一起上移，用户已否决。）
        }
    }
}

private fun isDirectLyricsQueueTransition(
    source: MeloXNowPlayingPage,
    target: MeloXNowPlayingPage,
): Boolean =
    (source == MeloXNowPlayingPage.Lyrics && target == MeloXNowPlayingPage.Queue) ||
        (source == MeloXNowPlayingPage.Queue && target == MeloXNowPlayingPage.Lyrics)

private fun MeloXPlaybackUiState.transitionDisplayTitle(): String =
    if (isInTransition) incomingTitle.ifBlank { title.ifBlank { "正在播放" } }
    else title.ifBlank { "正在播放" }

private fun MeloXPlaybackUiState.transitionDisplayArtist(): String =
    if (isInTransition) incomingArtist.ifBlank { artist } else artist
