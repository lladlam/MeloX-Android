package com.lladlam.melox.ui.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.lladlam.melox.ui.glass.bottomGlassSurfaceColor
import com.lladlam.melox.ui.glass.bottomLiquidGlassTint
import com.lladlam.melox.ui.glass.meloXLiquidBottomBar
import com.lladlam.melox.ui.glass.meloXLiquidContentTransform
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXSymbolVariant
import com.lladlam.melox.ui.glass.rememberMeloXLiquidInteraction
import com.lladlam.melox.playback.MeloXAudioReactiveRuntime
import com.lladlam.melox.playback.MeloXAudioReactiveSample
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MeloXIOSMiniPlayer(
    state: MeloXPlaybackUiState,
    onExpand: () -> Unit,
    compactProgress: Float = 0f,
    dynamicGlassEnabled: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    applyPlayerArtworkScale: Boolean = true,
) {
    if (!state.hasMedia) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeWidth = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val contentOffset = remember { Animatable(0f) }
    val liquidInteraction = rememberMeloXLiquidInteraction()
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    var pendingDirection by remember { mutableIntStateOf(0) }
    var pendingOutgoingMediaId by remember { mutableStateOf<String?>(null) }
    var pendingIncoming by remember { mutableStateOf<MeloXQueueEntry?>(null) }
    var reactiveSample by remember { mutableStateOf(MeloXAudioReactiveSample.Idle) }

    LaunchedEffect(state.mediaId, pendingDirection) {
        if (pendingDirection != 0 && state.mediaId != pendingOutgoingMediaId) {
            contentOffset.snapTo(0f)
            pendingDirection = 0
            pendingOutgoingMediaId = null
            pendingIncoming = null
        } else if (pendingDirection == 0) {
            contentOffset.snapTo(0f)
            pendingIncoming = null
        }
    }

    LaunchedEffect(pendingOutgoingMediaId) {
        if (pendingOutgoingMediaId == null) return@LaunchedEffect
        kotlinx.coroutines.delay(1_500L)
        contentOffset.animateTo(0f, spring(dampingRatio = .72f, stiffness = 430f))
        pendingDirection = 0
        pendingOutgoingMediaId = null
        pendingIncoming = null
    }

    val expansionProgress = if (animatedVisibilityScope != null) {
        val value by animatedVisibilityScope.transition.animateFloat(
            transitionSpec = { meloXPlayerLinearFloatSpec() },
            label = "mini-player-expansion-progress",
        ) { visibility ->
            if (visibility == EnterExitState.Visible) 0f else 1f
        }
        value
    } else {
        0f
    }

    // The mini visualizer is hidden during expansion. Stop its 20 Hz state
    // updates so the shared-bound transition can spend its frame budget on
    // layout and glass rendering instead.
    LaunchedEffect(state.mediaId, state.isPlaying, expansionProgress > 0.01f) {
        if (expansionProgress > 0.01f || !state.isPlaying) {
            reactiveSample = MeloXAudioReactiveSample.Idle
            return@LaunchedEffect
        }
        while (true) {
            reactiveSample = MeloXAudioReactiveRuntime.sample(state.mediaId)
            kotlinx.coroutines.delay(50L)
        }
    }

    // All source chrome is driven by the same reversible expansion progress as
    // the full-player destination. The source fades as real composited content,
    // rather than only lowering text/icon colors, so the reverse transition is
    // equally visible when returning to the mini player.
    val miniChromeAlpha = 1f - smoothStep(expansionProgress, 0.10f, 0.58f)
    val miniSurfaceAlpha = 1f - smoothStep(expansionProgress, 0.02f, 0.48f)
    val playerArtworkScale = if (
        !applyPlayerArtworkScale || MeloXSettingsRuntime.reduceMotion || !MeloXSettingsRuntime.artworkMotionEnabled || state.isPlaying
    ) 1f else 0.74f
    val sharedArtworkScale = 1f +
        (playerArtworkScale - 1f) * smoothStep(expansionProgress, 0.30f, 0.88f)
    val sharedShellModifier =
        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = sharedPlayerShellKey()),
                    animatedVisibilityScope = animatedVisibilityScope,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                    boundsTransform = MeloXPlayerShellBoundsTransform,
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                )
            }
        } else {
            Modifier
        }

    val compact = compactProgress.coerceIn(0f, 1f)
    val artworkSize = lerpDp(40.dp, 30.dp, compact)
    val artworkRadius = 6.dp
    val compactNextAlpha = 1f - smoothStep(compact, 0.04f, 0.50f)
    // 收缩态内容区(36dp) 比展开态(30dp) 更高 ⇒ 控件随内容区放大（用户口径）。
    // 用同一根 compact 曲线（与内边距同源），保证「栏高变化 ↔ 控件大小」同步、不会错拍。
    val controlMetric = smoothStep(compact, 0.20f, 0.80f)
    val controlSize = lerpDp(MiniControlSizeExpanded, MiniControlSizeCollapsed, controlMetric)
    val controlIconSize = lerpDp(MiniControlIconSizeExpanded, MiniControlIconSizeCollapsed, controlMetric)
    // 两圆之间的**圆缘**间距。图标间距 = 本值 + 2×圆内边距((size−icon)/2)。
    // 展开 (30−18)/2 = 6 ⇒ 图标间距 = gap + 12。
    val controlGap = lerpDp(MiniControlGapExpanded, MiniControlGapCollapsed, controlMetric)
    // 收缩后仍显示歌手，不再把这一行收成 0。
    val artistHeight = 15.dp
    // ── 排版必须**锁 dp、不锁 sp**（2026-09-25 跨机型修「作者名下沉被裁切」）────────
    //   故障现象：其他机型上「歌曲作者名下沉、下半截被切平」（真机截图实测作者名只画出
    //     约六成高度，切边正好落在 15dp 作者行盒的底沿）。
    //   根因：栏高恒 48dp、内容区恒 30dp（= 48 − 3·2 − 6·2），**而行盒是 dp、字身是 sp**。
    //     `sp` 随系统「字体大小」`fontScale` 等比放大（HyperOS / ColorOS / MIUI 常见
    //     1.15，用户还可能再调到 1.3+），而 dp 的行盒一分不涨 ⇒ 12sp 的作者名在
    //     fontScale=1.15 的机器上字身大 15%，descent 落到 15dp 行盒之外被裁掉。
    //     同一台基准机（fontScale=1.0）只差 1~2px，肉眼看不出来 —— 这正是「只有其他机型
    //     才复现」的原因，**不是玻璃/折射问题，别再往 backdrop 方向查**。
    //   ⚠ **必须是 `Dp.toSp()`，接收者要 `Dp` 而不是 `Float`**（2026-09-25 踩坑实录）：
    //     `Density` 上有两个同名的 `toSp` 重载，语义完全不同 ——
    //       · `Dp.toSp()`        = dp / fontScale  ⇒ 本栏要的「锁 dp」换算；
    //       · `Density.toSp(Float)` = **把入参当作 px**（字节码是 `toDp(value).toSp()`，
    //         即 value / density / fontScale）⇒ 写 `14f.toSp()` 会得到 3.5sp，
    //         在 density=4 的机器上字号直接**缩到 1/4**（真机实测：旧包标题宽 504px，
    //         误用后 126px，正好 ÷4 = ÷density；用户反馈「变得太小了」即此）。
    //     ⇒ 一律用 `with(density) { X.dp.toSp() }`，**不要**用 `Float.toSp()`。
    //   换算结果 = dp / fontScale，再乘回去 ⇒ 任何 fontScale 下本栏排版几何与基准机
    //   逐像素一致。**字号只由下面的 dp 常量决定，不要再写裸 `sp`、也不要用 Float.toSp。**
    val titleFontSize = with(density) { MiniTitleFontDp.dp.toSp() }
    val titleLineHeight = with(density) { MiniLineHeightDp.dp.toSp() }
    val artistFontSize = with(density) { MiniArtistFontDp.dp.toSp() }
    val artistLineHeight = with(density) { MiniLineHeightDp.dp.toSp() }
    // Keep the song that was already sliding in. Replacing that row with the
    // newly current track tears the layout down and flashes the next title.
    val holdIncoming = pendingIncoming != null &&
        pendingDirection != 0 &&
        state.mediaId == pendingOutgoingMediaId &&
        kotlin.math.abs(contentOffset.value) > swipeWidth * 0.82f
    val shownEntry = if (holdIncoming) pendingIncoming else null
    val shownTitle = shownEntry?.title ?: state.title
    val shownArtist = shownEntry?.artist ?: state.artist
    val shownArtwork = shownEntry?.artworkUrl ?: state.artworkUrl
    val shownMediaId = shownEntry?.mediaId ?: state.mediaId
    val visualOffset = if (holdIncoming) 0f else contentOffset.value
    val dragDirection = when {
        visualOffset < 0f -> -1
        visualOffset > 0f -> 1
        else -> 0
    }
    val adjacentEntry = when (dragDirection) {
        -1 -> state.queue.getOrNull(
            if (state.currentIndex + 1 < state.queue.size) state.currentIndex + 1 else 0,
        )
        1 -> state.queue.getOrNull(
            if (state.currentIndex > 0) state.currentIndex - 1 else state.queue.lastIndex,
        )
        else -> null
    }
    val dragProgress = (kotlin.math.abs(visualOffset) / swipeWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val adjacentAlpha = smoothStep(dragProgress, 0.15f, 0.85f)

    // 播放栏高度固定 48dp，与底栏收缩态的两侧按钮/搜索键完全等高。
    // 之前用 lerpDp(45,48) 且外层 Box 未定高，会随 compact 与 sharedBounds
    // 测量出更高/更矮的玻璃壳；这里统一钉死 48dp。
    val miniHeight = 48.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(miniHeight)
            .then(sharedShellModifier),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(miniHeight),
        ) {
            // Keep the glass surface out of the shared-bounds node itself.
            // When a capsule-shaped draw modifier is resized to the full
            // player, its fallback/lens can briefly become a dark giant pill.
            // The shared node remains a transparent geometry shell while the
            // actual MiniPlay glass fades before the full-screen scene owns the
            // surface.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = miniSurfaceAlpha }
                    // 与底栏 nav 胶囊用同一套材质（meloXLiquidBottomBar + 同一
                    // tint/surfaceColor），保证深/浅色下观感一致；按压反馈通过
                    // pressProgress 传入，内容层仍走 meloXLiquidContentTransform。
                    .meloXLiquidBottomBar(
                        shape = Capsule(),
                        tint = bottomLiquidGlassTint(),
                        surfaceColor = bottomGlassSurfaceColor(),
                        pressProgress = liquidInteraction.highlight.pressProgress,
                        refractionHeight = 8.dp,
                    ),
            )
        }

        // The glass draws underneath this layer, but both now consume the same
        // press/drag transform. Keeping MiniPlay chrome in the shared layer
        // prevents text, artwork and controls from floating over a moving pill.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(miniHeight)
                .padding(vertical = 3.dp)
                .meloXLiquidContentTransform(liquidInteraction),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(miniHeight)
                    .clip(Capsule())
                    .padding(
                        horizontal = lerpDp(12.dp, 8.dp, compact),
                        vertical = lerpDp(6.dp, 3.dp, compact),
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(state.mediaId) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    accumulatedDrag = 0f
                                    scope.launch { contentOffset.stop() }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    accumulatedDrag += dragAmount
                                    scope.launch {
                                        contentOffset.snapTo((contentOffset.value + dragAmount).coerceIn(-swipeWidth * .86f, swipeWidth * .86f))
                                    }
                                },
                                onDragEnd = {
                                    val direction = when {
                                        accumulatedDrag <= -28f -> -1
                                        accumulatedDrag >= 28f -> 1
                                        else -> 0
                                    }
                                    if (direction != 0) {
                                        scope.launch {
                                            contentOffset.animateTo(direction * swipeWidth, spring(dampingRatio = .68f, stiffness = 360f))
                                            pendingIncoming = adjacentEntry
                                            pendingDirection = direction
                                            pendingOutgoingMediaId = state.mediaId
                                            if (direction < 0) state.nextFromMiniPlayer() else state.previousFromMiniPlayer()
                                        }
                                    } else {
                                        scope.launch { contentOffset.animateTo(0f, spring(dampingRatio = .72f, stiffness = 430f)) }
                                    }
                                    accumulatedDrag = 0f
                                },
                                onDragCancel = {
                                    accumulatedDrag = 0f
                                    scope.launch { contentOffset.animateTo(0f, spring(dampingRatio = .72f, stiffness = 430f)) }
                                },
                            )
                        }
                        .clickable(interactionSource = null, indication = null, onClick = onExpand),
                ) {
                Row(
                    modifier = Modifier.fillMaxSize().graphicsLayer { translationX = visualOffset },
                    verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(lerpDp(10.dp, 8.dp, compact)),
                ) {
                val sharedArtworkModifier =
                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                sharedContentState = rememberSharedContentState(
                                    key = sharedPlayerArtworkKey(shownMediaId),
                                ),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = MeloXArtworkBoundsTransform,
                                renderInOverlayDuringTransition = true,
                                zIndexInOverlay = 100f,
                            )
                        }
                    } else {
                        Modifier
                    }
                Artwork(
                    url = shownArtwork,
                    modifier = Modifier
                        // `size` / `aspectRatio` 都会服从父级最大高度，封面被压扁后再被 Crop 裁成
                        // 长方形。`requiredSize` 忽略那条高度上限，封面保持正方形，行本身垂直居中。
                        .requiredSize(artworkSize)
                        .then(sharedArtworkModifier)
                        .graphicsLayer {
                            scaleX = sharedArtworkScale
                            scaleY = sharedArtworkScale
                        }
                        .clip(RoundedCornerShape(artworkRadius)),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .wrapContentHeight(Alignment.CenterVertically)
                        .graphicsLayer { alpha = miniChromeAlpha },
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = shownTitle.ifBlank { "正在播放" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = titleFontSize,
                        // ⚠ `lineHeight` 必须与播放栏的高度预算对齐（2026-09-25 修作者名裁切）：
                        //   内容区只有 30dp，title + artist 两行必须 ≤ 30dp，否则底部被裁。
                        //   两行各 15dp（= `MiniLineHeightDp`），14dp 字配 15dp 行距 = 1.07×，不挤。
                        //   ⚠ 这里**只能**用 `titleLineHeight`（dp→sp 换算），不能写裸 `15.sp`：
                        //     裸 sp 会随 fontScale 放大、撑破 30dp 预算 —— 跨机型裁切的成因，
                        //     见上面 `titleFontSize` 处的长注释。
                        lineHeight = titleLineHeight,
                        softWrap = false,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        // ⚠ `includeFontPadding = false` 是**必须的**（2026-09-25 修作者名裁切）：
                        //   Android 默认 `includeFontPadding = true` 会在行高之外额外加
                        //   ascent/descent 空白，标题+作者两行的**实际布局高 > 行高之和**，
                        //   而播放栏内容区只有 30dp（48 − 3·2 − 6·2），于是底部的作者名被裁。
                        //   BiliNext `GlassTabItem` 也是显式关掉它（`PlatformTextStyle(includeFontPadding = false)`）。
                        style = LocalTextStyle.current.merge(
                            TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                        ),
                    )
                    // ⚠ `height(artistHeight)` 只是**布局预算**（展开 15dp → 收缩态 0dp），
                    //   **不能**让作者名的**绘制**也受这个高度约束（2026-09-25 跨机型修）：
                    //   一旦 Text 被 15dp 的 maxHeight 量过，字身 descent 超出行盒的部分就会被
                    //   切掉 —— 基准机上只削 1~2px 看不出来，fontScale 更大的机型直接削掉四成，
                    //   就是本次「作者名下沉被裁切」的现场。
                    //   ⇒ ① `wrapContentHeight(unbounded = true)`：Text 按自身行高自然测量，
                    //        溢出 15dp 的部分照常画出来（下方还有 9dp 内边距 + 胶囊裁剪余量兜底）；
                    //     ② **去掉这一层的 `graphicsLayer { alpha }`**：离屏层同样按盒子尺寸裁
                    //        溢出内容，是第二个裁切源；淡出改由文字颜色 alpha 承担（视觉等价）。
                    Box(
                        modifier = Modifier
                            .height(artistHeight)
                            .wrapContentHeight(Alignment.CenterVertically, unbounded = true),
                    ) {
                        Text(
                            text = shownArtist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = artistFontSize,
                            lineHeight = artistLineHeight,
                            softWrap = false,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                            modifier = Modifier.wrapContentHeight(
                                align = Alignment.CenterVertically,
                                unbounded = true,
                            ),
                            // 同标题：必须关掉字体额外留白，否则作者名会被 15dp 的行盒裁掉下半截。
                            style = LocalTextStyle.current.merge(
                                TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                            ),
                        )
                    }
                }
                }
                adjacentEntry?.let { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = adjacentAlpha
                                translationX = visualOffset - dragDirection * swipeWidth
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(lerpDp(10.dp, 8.dp, compact)),
                    ) {
                        Artwork(
                            entry.artworkUrl,
                            Modifier
                                .requiredSize(artworkSize)
                                .clip(RoundedCornerShape(artworkRadius)),
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .wrapContentHeight(Alignment.CenterVertically),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            // ⚠ 与主条目同样的 `includeFontPadding = false`（见主条目长注释）：
                            //   否则横滑切入的这一份也会在 30dp 内容区里把作者名裁掉，
                            //   而它恰好是**滑动过程中用户正在看的那一份**。
                            val noFontPadding = LocalTextStyle.current.merge(
                                TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                            )
                            Text(
                                entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontSize = titleFontSize, lineHeight = titleLineHeight, softWrap = false,
                                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                                style = noFontPadding,
                            )
                            Text(
                                entry.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontSize = artistFontSize, lineHeight = artistLineHeight, softWrap = false,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .64f),
                                style = noFontPadding,
                            )
                        }
                    }
                }
            }

            MiniDancingBars(
                isPlaying = state.isPlaying,
                sample = reactiveSample,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = miniChromeAlpha * 0.72f),
                modifier = Modifier
                            .width(15.dp)
                            .height(18.dp)
                    .graphicsLayer { alpha = miniChromeAlpha },
            )

            // ── 控件舞台（内容驱动宽度）──────────────────────────────────────
            // ⚠ 关键教训（2026-09-25 v0.9.7→0.9.9）：舞台**不能**写成固定宽度。
            //   父 Row 里 title 是 weight(1f)，会吞掉所有剩余空间 ⇒ 舞台宽一改，
            //   title 宽度反向变化，**整块舞台被推着往右滑**，把「下一首」顶到玻璃右缘
            //   （v0.9.9 实测 stage 右移 ~11dp、play 右移 ~22dp）。
            //   正解：舞台宽度由内容决定（两个圆 + 显式 gap），Row 尺寸**恒定**，
            //        这样调间距就只是调间距，不会牵动任何别的元素。
            Row(
                modifier = Modifier
                    .height(controlSize)
                    .zIndex(8f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(controlGap),
            ) {
                MiniVectorButton(
                    kind = if (state.isPlaying) MiniGlyph.Pause else MiniGlyph.Play,
                    enabled = true,
                    onClick = state::togglePlayPause,
                    size = controlSize,
                    iconSize = controlIconSize,
                    modifier = Modifier
                        .offset(x = MiniControlStartPull)
                        .zIndex(10f),
                    visualAlpha = miniChromeAlpha,
                )
                if (compactNextAlpha > 0.05f) {
                    MiniVectorButton(
                        kind = MiniGlyph.Forward,
                        enabled = state.hasNext || state.repeatMode != 0,
                        onClick = state::next,
                        size = controlSize,
                        iconSize = controlIconSize,
                        modifier = Modifier.zIndex(9f),
                        visualAlpha = miniChromeAlpha * compactNextAlpha,
                    )
                }
            }
        }
    }
}
}

@Composable
private fun MiniDancingBars(
    isPlaying: Boolean,
    sample: MeloXAudioReactiveSample,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val energy = if (isPlaying) sample.energy.coerceIn(0.08f, 1f) else 0.10f
    val beat = if (isPlaying) sample.beat else 0f
    val downbeat = if (isPlaying) sample.downbeat else 0f
    val bars = listOf(
        energy * (0.58f + downbeat * 0.30f),
        energy * (0.86f + beat * 0.22f),
        energy * (0.46f + downbeat * 0.42f),
        energy * (0.72f + beat * 0.28f),
    )
    Canvas(modifier) {
        val gap = size.width * .12f
        val barWidth = (size.width - gap * 3f) / 4f
        bars.forEachIndexed { index, heightFraction ->
            val barHeight = size.height * heightFraction.coerceIn(.12f, 1f)
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), (size.height - barHeight) / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )
        }
    }
}

private enum class MiniGlyph { Play, Pause, Forward }

@Composable
private fun MiniVectorButton(
    kind: MiniGlyph,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    visualAlpha: Float = 1f,
) {
    val baseAlpha = if (enabled) 0.94f else 0.26f
    val drawAlpha = visualAlpha.coerceIn(0f, 1f)
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = baseAlpha)
    // ⚠ 图标字号也必须**锁 dp**（2026-09-25 跨机型修，同作者名裁切）：
    //   `iconSize` 是 dp（随内容区 30/36dp 收敛），但写 `iconSize.value.sp` 会随
    //   `fontScale` 放大 ⇒ 大字体机型上 22dp 图标画成 25dp+，被外圈
    //   `clip(CircleShape)` 切掉边缘。经 dp→sp 换算后图标恒 = iconSize dp。
    //   ⚠ `iconSize` **本身就是 `Dp`**，直接 `iconSize.toSp()` 即可；
    //     `iconSize.value.toSp()` 会命中把入参当 **px** 的 `Density.toSp(Float)`，
    //     图标会同样缩到 1/density（详见 `titleFontSize` 处的踩坑说明）。
    val iconFontSize = with(LocalDensity.current) { iconSize.toSp() }
    Box(
        modifier = modifier
            .graphicsLayer { alpha = drawAlpha }
            .size(size)
            .clip(CircleShape)
            .clickable(
                enabled = enabled && drawAlpha > 0.05f,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        MeloXSymbolIcon(
            symbol = when (kind) {
                MiniGlyph.Play -> MeloXSymbol.Play
                MiniGlyph.Pause -> MeloXSymbol.Pause
                MiniGlyph.Forward -> MeloXSymbol.Next
            },
            // 圆缩小 ⇒ 图标必须同比例缩，否则图标会顶满圆/溢出
            modifier = Modifier.size(iconSize),
            color = color,
            variant = if (kind == MiniGlyph.Play || kind == MiniGlyph.Pause) MeloXSymbolVariant.Fill else MeloXSymbolVariant.Regular,
            iconSize = iconFontSize,
        )
    }
}

// ── 播放栏右侧控件尺寸（2026-09-25 收敛为「随内容区高度」）──────────────────
// 播放栏玻璃壳恒 48dp，但内边距随 compact 变化 ⇒ **内容区高度不同**：
//   · 展开态：48 − 3·2(外) − 6·2(内) = **30dp**
//   · 收缩态：48 − 3·2(外) − 3·2(内) = **36dp**   ⇒ 收缩态内容区**更高**
// 因此控件不能写死一个尺寸，必须跟内容区一起变（用户：收缩态要更大一点）。
// 展开 30dp 圆 / 18dp 图标；收缩 36dp 圆 / 22dp 图标（= v0.9.6 之前的原始尺寸）。
private val MiniControlSizeExpanded = 30.dp
private val MiniControlSizeCollapsed = 36.dp
private val MiniControlIconSizeExpanded = 18.dp
private val MiniControlIconSizeCollapsed = 22.dp
// 两圆之间的圆缘 gap（舞台已改为内容驱动宽度）。
//   图标间距 = gap + 2×圆内边距((size−icon)/2)。展开 (30−18)/2 = 6 ⇒ gap 0dp → 图标间距 12dp。
//   收缩 (36−22)/2 = 7 ⇒ gap 0dp → 图标间距 14dp（收缩态 next 淡出，此值仅过渡期可见）。
//   「三点 ↔ 暂停」= 父 Row spacedBy(10dp) + 圆内边距(6dp) + StartPull(4dp) = 12dp（= 对称）。
private val MiniControlGapExpanded = 0.dp
private val MiniControlGapCollapsed = 0.dp
// 暂停键向左微拉 4dp：把「三点→暂停」从 16dp 收到 12dp，与「暂停→下一首」对齐。
//   ⚠ 只用它压「三点↔暂停」这一侧；**绝不能**用它当「暂停↔下一首」的杠杆（v0.9.7 翻车原因）。
private val MiniControlStartPull = (-4).dp

// ── 播放栏排版（**dp 口径**，单一真相源；2026-09-25 跨机型修作者名裁切）────────
// ⚠ 这三个值是「希望文字**渲染成多少 dp**」，不是 sp。调用点一律用
//   `with(density) { MiniTitleFontDp.dp.toSp() }` 换算（**接收者必须是 `Dp`**，
//   写成 `Float.toSp()` 会命中「把入参当 px」的 `Density.toSp(Float)`，字号缩到
//   1/density —— 详见 `titleFontSize` 处的踩坑说明）—— 直接写 `14.sp` 会随系统
//   fontScale 放大而撑破 30dp 内容预算，导致作者名 descent 被裁（跨机型故障根因，
//   详见 `MeloXIOSMiniPlayer` 里 `titleFontSize` 处的长注释）。
// 预算核对（展开态）：内容区 30dp = 标题行 15dp + 作者行 15dp，两行各 15dp 行高。
private const val MiniTitleFontDp = 14f
private const val MiniArtistFontDp = 12f
private const val MiniLineHeightDp = 15f

private fun smoothStep(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun lerpDp(start: Dp, end: Dp, progress: Float): Dp =
    (start.value + (end.value - start.value) * progress.coerceIn(0f, 1f)).dp
