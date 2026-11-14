package com.lladlam.melox.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Transition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
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
import com.lladlam.melox.ui.MeloXUiMotionRuntime
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
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
    /**
     * 队列层的**第二级**就绪标志（2026-10-22，治「展开落定那一帧顿一下」）。
     *
     * [residentLayersReady] 是「歌词 + 队列」共用的一刀切。但两者在同**一帧**里一起组合
     * 就是一次双份尖峰：歌词侧是一整个 `LazyColumn`（`LazyLayoutCacheWindow` ahead 720dp
     * ⇒ 首屏之外还要多组合若干行）+ 逐字 `Canvas`；队列侧是另一整个 `LazyColumn`。两棵树
     * 同帧建完，那一刻的 measure/layout 之和很容易超过 11.07ms 预算 ⇒ 迷你栏 morph 收尾
     * 时出现**一次顿挫**（用户口径：「掉帧卡顿感」里最明显的那一下）。
     *
     * 拆成两级：落定后第 1 帧只建歌词，第 2 帧（+11ms）再建队列。用户在 11ms 内不可能
     * 切到队列页，所以「不会把卡顿转移到切页」这条前提依然成立。
     *
     * 默认 `true` ⇒ 其它调用点（Classic 外壳等）行为不变。
     */
    residentQueueLayersReady: Boolean = true,
    orientTransition: Transition<Boolean>,
    // ★ 平板双栏「玩家卡片居中」进度 0→1（0 = 左右分栏，1 = 左列滑到屏幕正中）。
    //   ⚠ 由 `MeloXIOSNowPlayingSharedHost` **单一计算后透传**（那里是唯一时钟），本层只消费：
    //     持久封面层的 `centerXFrac` 与这里 Row 的 `translationX` 必须同解，各跑一份动画会错拍。
    //   手机 / 竖屏恒 0 ⇒ 逐像素不变。
    //   ⚠ 用 `() -> Float` 而不是值：每帧状态，值传参会把调用方（host）每帧重组。
    columnCenterProgress: () -> Float = { 0f },
    // ★★ 2026-10-23：开 / 闭播放页（迷你栏 ↔ 全屏）的 morph 是否进行中。
    //
    //   用户报障：「迷你栏打开播放页还是不够丝滑，尤其停在歌词页时开闭最明显」。
    //   morph 期间外壳是**整页缩放**，歌词每行一个 `Modifier.blur`（离屏纹理 + 高斯）
    //   会被每帧重建；若这时**又恰好有一句歌词要滚动**，LazyColumn 的重新测量/布局
    //   再叠上去 = 用户感觉到的那一下顿挫。
    //
    //   ⇒ 本层只做一件事：把这个判据**原样转发**给歌词面板，让它在运动期间推迟
    //     自动跟随滚动（见 `MeloXIOSLyricsPanel.shellTransitioning`）。
    //
    //   🔴 刻意**不**用它去降任何视觉档（不降模糊、不降帧率）。2026-10-23 用户明令：
    //     不许出现肉眼可见的降级，尤其不许「动画结束的瞬间画面闪一下」——
    //     而降档类优化恰恰全是这个形状（运动期间压低、落定瞬间弹回）。
    //     想省成本只能省「本来就没画出来的东西」，不能省「已经画出来的像素」。
    //
    //   ⚠ 传 **State 本体**：本层不读值，只转发 ⇒ 不会把本层拖进重组。
    //   默认恒 false ⇒ 其它调用点（Classic 外壳等）行为完全不变。
    shellTransitioning: State<Boolean> = MeloXLyricsNeverTransitioning,
    // ★ 2026-10-24 修「塌缩时控件飞向右下角」：播放页**正在关闭**时为真。
    //   此时强制横竖屏过渡落定（出场淡变直接 `ExitTransition.None`），让非目标朝向的
    //   布局瞬间消失 ⇒ 该朝向的 `np-controls` 共享元素节点被移除 ⇒ 旋转形变失去配对伙伴，
    //   只剩落定朝向的 `np-controls` 与迷你栏配对（保留迷你↔全屏的关闭 morph），
    //   控件就不会被甩到错误的角落。打开方向恒 false ⇒ 不影响打开/常态转屏。
    //   默认 false ⇒ 其它调用点行为不变。
    closingPlayer: State<Boolean> = mutableStateOf(false),
    sharedTransitionScope: SharedTransitionScope,
) {
    val favorite = rememberTrackFavorite(state)
    // ★ 拖动进度条 → 歌词在对应进度跟随滚动（Apple Music 原版行为）。竖屏 scrubber 写入、
    //   竖屏歌词面板读取，二者同处本 composable 作用域。横屏走各自分支，按需另行接入。
    val scrubPreviewState = remember(state.mediaId) { MeloXScrubPreviewState() }
    // ★ 统一主时间线：竖/横两套布局都作为**共享 orientTransition** 的 AnimatedVisibility，
    //   淡入淡出与封面滑移跑在同一条 spring 上（不再是两个独立 tween 时钟）。
    //   转屏时旧布局淡出、新布局淡入与封面移动严格同步 = 一镜到底。
    // ⚠ 2026-10-22 第四轮：改成**跟着共享主时间线的目标值**，而不是自己再读一次
    //   `rememberMeloXWindowInfo()`。原因：主时间线现在有起跑闸（`ORIENT_SETTLE_FRAMES`，
    //   见 `MeloXIOSNowPlayingSharedHost` 里 `orientTarget` 那段），会晚两帧才翻。
    //   若这里立刻翻，那两帧就是「竖屏布局 + 横屏紧凑样式」= 一次可辨的闪变。
    //   ⇒ 一个转屏事件只能有一个时钟，样式判据跟着它走。
    val isLandscape = orientTransition.targetState
    // ⚠ 2026-10-23（方案 A，用户批准「转屏不完全走交叉淡变」）：
    //   转屏的 fade **只留出场，且压到 90ms**；入场不再淡入（`EnterTransition.None`）。
    //
    //   原因（atrace 实测，iQOO 15 / 120Hz）：交叉淡变让竖横两套布局同场 300ms，
    //   于是**每一帧都要建 2 个全屏离屏层**（`alpha caused saveLayer 1440x3168` +
    //   `3168x1440`，8 秒内 332 次）。这两层是转屏掉帧的主账：
    //   `Janky 3~7 (2.8~4.6%)`、`p99 32~53ms`，且 38/38 全部记在
    //   `Slow issue draw commands`（RT 侧 `Drawing 3168x1440` 最坏 85ms，
    //   UI 线程 `postAndWait` 一等就是 83ms）。
    //
    //   改成「入场即 alpha=1」后：入场那棵**根本不需要层**（alpha 恒 1 时 Skia 不建
    //   saveLayer），只剩出场这一层、且只活 90ms ⇒ 全屏层累计时长从
    //   2×230ms 降到 1×90ms（-81%）。
    //
    //   ⚠ 为什么还能保住「不闪」：出场淡出发生在**系统旋转动画进行中**（转屏起跑后
    //     ~90ms，系统旋转约 400~500ms），不是在动画结束时。屏幕那一刻正在被系统
    //     做 rotate/scale 变换，旧布局的消失被旋转运动盖住；而「结束时才移除」才是
    //     用户明令禁止的「动画结束闪一下」——本改动刻意避开了那个时刻。
    //   ⚠ 曲线保持线性：90ms 太短，缓动看不出来，反而会拉长视觉停留。
    val orientFadeOutSpec = tween<Float>(90)
    // ★ 2026-10-25（修「收起时控件往右下角消失 / 快速重进后下半控件迟迟不回」）：
    //   **不要**在塌缩期间把出场布局瞬间移除（上一版 `ExitTransition.None` 正是这么做的）。
    //   那会**中途取消**正在跑的 `np-controls` 共享元素形变，把元素吊在 overlay 里 ⇒
    //   控件飞走 / 消失、重进后要等它重新配对才回来（用户实测的确切描述）。
    //   ⇒ 出场恢复原 90ms 淡出（形变正常收尾、元素正常释放）；真正要摘掉的是
    //     「塌缩期间的 `np-controls` 形变」——见下面两个 call site 的
    //     `if (closingPlayer.value) Modifier else npMorphElement("np-controls", …)`。
    val orientExit = fadeOut(orientFadeOutSpec)


    // ★ 2026-10-22 第二轮（用户：「横竖屏切换动画，其他元素飞入动画也百分百卡顿」）：
    //   转屏那条时间线**之前完全没被降档覆盖**。
    //
    //   横竖屏切换不是「换一套布局」，而是竖横**两套布局同场 300ms**（下面两个
    //   `orientTransition.AnimatedVisibility`，共用同一条 tween）。于是这段时间：
    //     · `np-controls` 在竖横两套里各有一块 —— **两块「全屏宽 × 279dp」的玻璃同时算模糊**；
    //     · 歌词 / 队列列表也是两套 —— 两个 LazyColumn + 逐字 Canvas 同时在画；
    //     · 每个共享元素各自进 overlay，各自付一次全屏层创建 + 重录。
    //   ⇒ 这是「百分百卡」而不是「偶发卡」的结构性来源：它不是冷路径，每次转屏都要付双份。
    //
    //   ⚠ 这个判据必须声明在**函数作用域**（两个 AnimatedVisibility 之外）：横屏那套布局
    //     也要吃它，声明在里面就只有竖屏看得见（第一版就是这么写错的，编译器直接报错）。
    val orientationAnimating by remember(orientTransition) {
        derivedStateOf { orientTransition.isRunning }
    }
    // 写进运行期总闸：流光背景网格（SharedHost）与回前台预热（MeloXApp 根）读不到本函数
    // 内部的 transition，只能从这里拿。见 `MeloXUiMotionRuntime` 的三条硬约束。
    LaunchedEffect(orientationAnimating) {
        MeloXUiMotionRuntime.orientationTransition = orientationAnimating
    }
    // ⚠ 播放页被收起 / 转屏重建时本函数可能整棵移出组合 ⇒ 协程不会跑完，必须显式复位，
    //   否则背景会永久停在降频档、预热永久不跑（慢性病，见总闸文件第 3 条约束）。
    DisposableEffect(Unit) {
        onDispose {
            MeloXUiMotionRuntime.pageTransition = false
            MeloXUiMotionRuntime.orientationTransition = false
        }
    }

    orientTransition.AnimatedVisibility(
        visible = { !it },
        enter = EnterTransition.None,
        exit = orientExit,
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
    // 队列层晚一帧（见 [residentQueueLayersReady]）：两句都带上 `!artworkVisible` 旁路，
    // 否则冷启动直接进队列页时会白空一帧（那是可视回归，不是优化）。
    val showQueueLayers = showResidentLayers && (residentQueueLayersReady || !artworkVisible)

    // ── 预热抬起：治「歌单 / 歌词页打开动画顿挫」（2026-10-22）───────────────────────────
    //   用户报障的三种卡顿里，「横竖屏飞入」与「歌单打开」是**顿挫**（某一帧特别长），
    //   而不是持续低帧率 —— 这是「首次成本」的典型特征。
    //
    //   来源就在上面那套「延后组合 + alpha = 0」里：HWUI/Skia 对 `RenderNode` 的
    //   **alpha == 0 会整棵跳过绘制**（displaylist 都不执行）。所以这两棵子树在
    //   `lyricsAlpha / queueAlpha` 还是 0 的时候，虽然组合、测量、布局都做过了，
    //   **却从来没有真正栅格化过一次** —— 首次执行 displaylist、首次走那条
    //   blend / layer / 文本渲染 pipeline、首次上传封面纹理，全部被推到了
    //   「用户第一次切到这一页」的那一帧。那一刻就是用户感觉到的顿挫。
    //
    //   ⇒ 落定后让两棵树各以 **1/255 的真实 alpha** 画一帧（再画一帧留余量）：
    //     1/255 在 8-bit 合成里就是 1 个色阶，深色背景 + 流光背景之上完全不可辨，
    //     但它让 Skia 走完整条绘制路径，把上面那些「首次」提前付掉。
    //   ⚠ 只抬这么几帧，之后回到 0 ⇒ 静止在封面页时这两棵仍是**免费**的（不被绘制）。
    //   ⚠ 不要改成常驻 1/255：那会把本来免费的静止态变成每帧全量绘制，纯亏。
    val warmupLift = 1f / 255f
    var warmupTarget by remember { mutableIntStateOf(0) } // 0=无 1=歌词 2=队列
    var warmupDone by remember { mutableStateOf(false) }
    LaunchedEffect(showResidentLayers, showQueueLayers) {
        if (warmupDone || !showResidentLayers) return@LaunchedEffect
        warmupTarget = 1
        // 两帧：一帧让 alpha 生效，一帧真正提交。
        withFrameNanos { }
        withFrameNanos { }
        if (showQueueLayers) {
            warmupTarget = 2
            withFrameNanos { }
            withFrameNanos { }
        }
        warmupTarget = 0
        warmupDone = true
    }
    var showsLyricsControls by remember(state.mediaId) { mutableStateOf(true) }
    // ★ 2026-10-23（用户报障「竖屏歌词页，我明明在操作，它却按时长消失了」）：
    //   静置计时器的**重置脉冲**。
    //
    //   旧实现的唯一重置入口是 `setLyricsControlsVisible(true)`，而它只被两件事调用：
    //   ① 控件隐藏后「点按歌词唤出」；② 页面选择器点选。于是**滚动歌词、点播放/暂停、
    //   上一首/下一首、收藏、⋯、调音量**这些操作全都不会重置计时 —— 用户明明在操作，
    //   面板照样按原始起跑时间收走（默认 5s，设置范围 3~15s）。
    //
    //   改用 SharedFlow 而不是原来的 `mutableIntStateOf` 计数，是因为计数必须作为
    //   `LaunchedEffect` 的 key 才生效 ⇒ 每次重置都要在组合期读它 ⇒ **整个场景层重组**
    //   （滚动时每秒好几次，正是播放页最贵的重组）。SharedFlow 在计时协程里收，
    //   组合期零读取、零重组。判据语义不变：只是「重新起跑那一段计时」。
    val lyricsControlsActivity = remember(state.mediaId) { MutableSharedFlow<Unit>(extraBufferCapacity = 8) }
    // 底部控件条（暂停 / 上一首 / 下一首 + 页面选择器 + 音量）在**歌词页与队列页**
    // 静置自动隐藏（沉浸口径，2026-10-18 扩展到队列页：用户要求「隐藏的不是顶部
    // 面板，而是底下的暂停、下一首那个面板」）；封面页恒显（翻页入口，不能藏）。
    val bottomControlsAutoHide =
        page == MeloXNowPlayingPage.Lyrics || page == MeloXNowPlayingPage.Queue
    val bottomControlsVisible = !bottomControlsAutoHide || showsLyricsControls

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
        // 任何显式交互都算一次活动：面板自身回调（换字号 / 唤出）也会走到这里。
        lyricsControlsActivity.tryEmit(Unit)
        showsLyricsControls = visible
    }

    LaunchedEffect(page) {
        showsLyricsControls = true
    }
    // Treat every media item as fresh lyric-page activity.  Headset next/prev
    // changes the track without necessarily changing the page, so omitting the
    // media id left the controls permanently visible after a hardware skip.
    // ★ 拖动进度条期间**暂停**静置自动隐藏（2026-10-19 用户报障：拖进度条不该把控件条
    //   收走）。根因是进度条的 Slider 不经过 `setLyricsControlsVisible`，计时器照常跑；
    //   一旦拖动超过静置阈值，控件条（连同手指底下的进度条）会在拖拽中途被隐藏 ——
    //   体验直接断裂。判据取共享的 scrub 预览状态（仅拖动期间非 null），松手回 null
    //   后本 effect 重启并**重新完整计时**，所以松手不会立刻消失。
    //
    // ★★ 2026-10-23：计时改成「等一次活动脉冲，等不到才收」。
    //   原来是一次性 `delay(阈值)` 走到底：只要中途没有任何东西重启本 effect，
    //   5 秒一到照收 —— 而重启入口只有「点按唤出 / 点选页面」两条，**滚动歌词、
    //   点播放键 / 上一首 / 收藏 / ⋯、调音量**统统不算 ⇒ 用户正在操作却被打断。
    //   现在每一段计时都可以被 `lyricsControlsActivity` 的一次发射重置，
    //   发射源是场景根节点的指针嗅探（见 `meloXUserActivityPing`），覆盖整页。
    //   ⚠ 只在「真的等满一整段没有任何活动」时才隐藏，语义仍是静置自动隐藏。
    LaunchedEffect(page, state.mediaId, showsLyricsControls) {
        if (!bottomControlsAutoHide || !showsLyricsControls) return@LaunchedEffect
        val autoHideMs = MeloXSettingsRuntime.lyricInterfaceAutoHideDelayMs.toLong()
        var idleMs = 0L
        while (true) {
            // 200ms 一片：既可被活动脉冲立刻打断（`first()` 返回即有活动），
            // 也能及时看到「用户开始拖进度条」。最后一片按剩余量收敛 ⇒ 落点仍是 5s。
            val sliceMs = minOf(200L, autoHideMs - idleMs).coerceAtLeast(1L)
            val pinged = withTimeoutOrNull(sliceMs) { lyricsControlsActivity.first() } != null
            if (pinged) {
                idleMs = 0L
                continue
            }
            // 拖动进度条期间**不计时**（2026-10-19 用户报障：不该在拖拽中途被收走）。
            // 兜底：10s 没有新的拖动事件 ⇒ 判为手势已被系统取消，清掉预览态继续计时。
            if (scrubPreviewState.previewPositionMs != null) {
                if (scrubPreviewState.isStale(10_000L)) {
                    scrubPreviewState.clear()
                    idleMs = 0L
                } else {
                    idleMs = 0L
                }
                continue
            }
            idleMs += sliceMs
            if (idleMs >= autoHideMs) {
                showsLyricsControls = false
                return@LaunchedEffect
            }
        }
    }

    // Consolidate the page cross-fade into a single Transition so the five
    // page states share one spring timeline instead of ten independent
    // animate*AsState coroutines running in parallel.
    val pageTransition = updateTransition(
        targetState = page,
        label = "scene-page-transition",
    )

    // ★ 2026-10-22：页间切换是否**正在运动**（首尾各变一次，不是每帧）。
    //
    //   为什么需要它：这三条 animate* 用的是 `spring(dampingRatio = 0.7, stiffness = 300)`
    //   —— 欠阻尼弹簧，位置稳定到肉眼不可辨之后**还会继续跑**，实际 `isRunning`
    //   跨度约 0.9~1.3s，比一条 tween 长得多。也就是说「切页」这段时间里，下面这些
    //   每帧成本要付上一秒多，而不是 350ms。
    //
    //   本判据只用来做**运动期间的档位切换**，落定立刻恢复原值：
    //   ① 底栏控制区（全屏宽 × 279dp）的背景模糊 24dp → 10dp；
    //   ② 队列页顶部四键胶囊（全屏宽）的背景模糊 20dp → 10dp。
    //
    //   ⚠ 只读 `isRunning` 的布尔值。绝不读 `pageTransition` 的任何 fraction ——
    //     那正是本文件开头那段「10 个值一律不 by」要防的东西。
    val pageAnimating by remember(pageTransition) {
        derivedStateOf { pageTransition.isRunning }
    }

    // ★ 2026-10-22 第二轮：转屏那条时间线也要参与降档（判据本体在函数作用域，
    //   因为横屏那套布局同样要吃它，见 `orientationAnimating` 的注释）。
    //   两个来源合成一个布尔（首尾各变一次），消费点（两块玻璃的 blurRadius、
    //   队列四键胶囊）都只读它。
    val motionAnimating = pageAnimating || orientationAnimating

    LaunchedEffect(pageAnimating) { MeloXUiMotionRuntime.pageTransition = pageAnimating }

    // ⚠⚠⚠ 2026-10-22 动画流畅度：下面这 10 个值**一律不 `by` 解包**。
    //
    //   它们是 `spring(0.7, 300)` 驱动的页面交叉淡入/位移，切换期间**每帧都在变**。
    //   一旦 `by` 解包 + 在组合体里读，本函数（整个场景层：封面页 / 歌词 / 队列 /
    //   歌曲头）会在切页的整段 spring 里每帧重组 —— 而 C5「播放页内页切换」正是
    //   用户能明显感觉到的那一段。
    //
    //   好消息：这 10 个值的**全部消费点都在 `graphicsLayer {}` 里**
    //   （alpha / translationY / scaleX / scaleY，零布局影响）⇒ 直接改成绘制期读
    //   `.value` 即可：同一条 spring、同一个数值、同一帧画面，只是不再重建组合树。
    val artworkAlpha = pageTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-artwork-details-alpha",
    ) { if (it == MeloXNowPlayingPage.Artwork) 1f else 0f }

    val artworkOffset = pageTransition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-artwork-details-offset",
    ) { if (it == MeloXNowPlayingPage.Artwork) 0.dp else (-300).dp }

    val lyricsAlpha = pageTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-lyrics-alpha",
    ) { if (it == MeloXNowPlayingPage.Lyrics) 1f else 0f }

    val lyricsOffset = pageTransition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-lyrics-offset",
    ) { if (it == MeloXNowPlayingPage.Artwork) 400.dp else 0.dp }

    val lyricsScale = pageTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-lyrics-scale",
    ) { if (it == MeloXNowPlayingPage.Queue) 0.92f else 1f }

    val queueAlpha = pageTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-queue-alpha",
    ) { if (it == MeloXNowPlayingPage.Queue) 1f else 0f }

    val queueOffset = pageTransition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-queue-offset",
    ) { if (it == MeloXNowPlayingPage.Artwork) 400.dp else 0.dp }

    val queueScale = pageTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-queue-scale",
    ) { if (it == MeloXNowPlayingPage.Lyrics) 0.92f else 1f }

    val headerAlpha = pageTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-song-header-alpha",
    ) { if (it == MeloXNowPlayingPage.Artwork) 0f else 1f }

    val headerOffset = pageTransition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "scene-song-header-offset",
    ) { if (it == MeloXNowPlayingPage.Artwork) 40.dp else 0.dp }

    // ★ 2026-10-23：整页「用户正在操作」嗅探（见 `meloXUserActivityPing`）。
    //   挂在**场景根节点**而不是逐个按钮/列表：命中路径上祖先必然在链，
    //   滚动歌词、点控制条、拖音量、拖进度条全都覆盖，且零消费 ⇒ 不改变任何手势行为。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .meloXUserActivityPing(
                enabled = bottomControlsAutoHide,
                activity = lyricsControlsActivity,
            ),
    ) {
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
                        alpha = artworkAlpha.value
                        translationY = artworkOffset.value.toPx()
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
                            // 预热抬起：见上方 `warmupLift` 长注释。`max` 保证正常动画期间
                            // 完全不受影响（预热只在 alpha 恰好为 0 的静止态起作用）。
                            alpha = maxOf(lyricsAlpha.value, if (warmupTarget == 1) warmupLift else 0f)
                            translationY = lyricsOffset.value.toPx()
                            scaleX = lyricsScale.value
                            scaleY = lyricsScale.value
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
                        // ★ 拖动进度条时，让歌词在对应进度跟随滚动（Apple Music 原版行为）。
                        scrubPositionMs = scrubPreviewState.previewPositionMs,
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
                        // ★ 顶部渐隐（2026-10-18 加）：面板从 88dp 起、自身 clipToBounds，
                        //   以前最上面那行是**被视口顶沿硬切**的。与底部同手法补一条渐隐带，
                        //   半行在抵达顶沿前就溶解掉，没有断面。
                        edgeFadeTop = MeloXPortraitLyricsFadeTop,
                        edgeFadeBottom = MeloXPortraitLyricsFadeBottom,
                        edgeFadeBottomTerminal = lyricsFadeTerminal,
                        controlsRevealHeight = MeloXNowPlayingControlsHeight.dp,

                        edgeBlurMax = 0.dp,
                        // ★ 开 / 闭播放页期间逐行模糊归零 + 推迟自动跟随滚动（见形参注释）。
                        shellTransitioning = shellTransitioning,
                    )
                }
            }

            // ★ 同上（`showResidentLayers`）：队列列表同一处理。
            //   ⚠ 2026-10-22：条件改用 `showQueueLayers`（比歌词晚一帧），把落定那一刻的
            //   双份 LazyColumn 尖峰拆成两帧。见 [residentQueueLayersReady]。
            if (showQueueLayers) {
                // ★ 2026-10-19 四轮：队列页底部改成**歌词页那套**（不再给控件面板让位）。
                //
                //   旧做法：`.padding(bottom = queueBottomInset)` 把列表视口压到面板顶沿之上。
                //   后果：视口高度恒为「屏幕 − 安全区 − 面板高」，而行步长 60dp 除不尽它 ⇒
                //   静止态底部必然挂着一截被刀切的行。要么加宽带遮住它（用户：白白占位），
                //   要么让它裸露（用户：硬切难看）—— 两头堵，前几轮就是在这之间反复。
                //
                //   歌词页的解法（`MeloXIOSLyricsPanel.edgeFadeBottomTerminal`）：面板**不让位**，
                //   列表铺满整屏，只把渐隐的**归零点**抬到面板顶沿，让"面板区域内"的内容
                //   本来就淡成透明。队列照搬：列表 Box 不再吃 `queueBottomInset`，而是把同一个
                //   值当 `fadeBottomTerminal` 传给面板 ⇒ 面板区域内什么都没画出来，面板上方
                //   32dp 范围内行自然溶解，既没有白让的空白，也没有切面。
                //
                //   归零点从**屏幕底**量：控件面板贴 `meloXPlayerNavigationBarsPadding()` 底沿，
                //   所以面板顶沿距屏幕底 = 面板高 + 手势条安全区；面板隐藏时归 0（内容铺到
                //   屏幕最底，同 Apple Music）。
                val queueDensity = LocalDensity.current
                val navSafeDp = with(queueDensity) {
                    WindowInsets.navigationBars.getBottom(queueDensity).toDp()
                }
                val navSafePx = with(queueDensity) { navSafeDp.roundToPx() }
                val queueFadeTerminal by animateDpAsState(
                    targetValue = if (bottomControlsVisible) {
                        MeloXNowPlayingControlsHeight.dp + navSafeDp
                    } else {
                        0.dp
                    },
                    animationSpec = tween(durationMillis = 220),
                    label = "queue-fade-terminal",
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(sharedTransitionScope.npMorphElement("np-queue", avScope))
                        .zIndex(if (queueVisible) 2f else 0f)
                        .graphicsLayer {
                            // 预热抬起：同 lyrics，见 `warmupLift` 长注释。
                            alpha = maxOf(queueAlpha.value, if (warmupTarget == 2) warmupLift else 0f)
                            translationY = queueOffset.value.toPx()
                            scaleX = queueScale.value
                            scaleY = queueScale.value
                        }
                        // 队列行止于常驻播放控制区上方；底部不会被任何控件遮住。
                        // ★ 顶部四键区域在面板内部是列表的**兄弟**（Column 布局，
                        //   不盖在列表上）⇒ 那块区域物理上没有歌曲内容，不会透出歌单；
                        //   列表顶沿由面板内的渐隐遮罩软化，没有硬截断。
                        //   面板顶部这段 80dp 是留给场景自己的曲名栏（np-header）。
                        .padding(top = 80.dp)
                        // 列表向下延伸进手势条安全区（Apple Music 口径：内容滚到小白条后面）。
                        // 溢出绘制、不裁剪 ⇒ 视口底沿 = **屏幕底**，归零点才能按"距屏幕底"量。
                        // 根 Column 的导航栏 padding 本身不动 ⇒ 封面 / 歌词页零回归。
                        .layout { measurable, constraints ->
                            val placeable = if (navSafePx <= 0 || constraints.maxHeight == Constraints.Infinity) {
                                measurable.measure(constraints)
                            } else {
                                measurable.measure(
                                    constraints.copy(maxHeight = constraints.maxHeight + navSafePx),
                                )
                            }
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        },
                ) {
                    MeloXQueuePanel(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        showSongHeader = false,
                        interactive = queueVisible,
                        // 队列页的交互（点按钮 / 点行 / 拖动 / 点空白）⇒ 唤出底部控件条
                        // （静置自动隐藏的那条），与歌词页 onInterfaceInteraction 同口径。
                        onInteract = { setLyricsControlsVisible(true) },
                        // ★ 横屏（手机 / 平板横屏）：顶部四个模式按钮采用**与歌词页同款**的
                        //   自动显隐 —— 可见性直接吃 `bottomControlsVisible`（= `showsLyricsControls`，
                        //   与底部三键浮层同一个计时器 + 同一个 `setLyricsControlsVisible(true)` 唤醒）。
                        //   静置 3 秒后随底部条一起淡出，列表顺势铺满整屏；交互即唤回。
                        //   竖屏恒 true ⇒ 按钮常驻（竖屏不占多少纵向空间，也不存在"每屏歌太少"）。
                        modeControlsVisible = if (isLandscape) bottomControlsVisible else true,
                        // ★ 横屏紧凑：四个按钮高度 64→52dp，给列表让出约 12dp ≈ 多看到近半首歌。
                        compact = isLandscape,
                        // ★ 归零点 = 面板顶沿（隐藏时 0）。列表因此不需要为面板留 padding：
                        //   面板区域内恒透明，面板上方 32dp 内行溶解（见 `queueEdgeFade`）。
                        fadeBottomTerminal = queueFadeTerminal,
                        // ★ 页间 / 转屏运动期间把这块胶囊的背景模糊降一档（见形参注释）。
                        motionAnimating = motionAnimating,
                    )
                }
            }

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
                        alpha = headerAlpha.value
                        translationY = headerOffset.value.toPx()
                    }
                    // ⚠ 队列页这条 header **不挂任何底板**（2026-10-18 用户否掉「黑框」）：
                    //   曲名/封面直接浮在背景上，观感与下方四个胶囊键一致。
                    //   曾经在 queueVisible 时给它套 `meloXBackdropBlur(20dp, Black 10%)`，
                    //   真机上就是一块明显的圆角暗底 —— 不要再加回来。
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
                // ⚠ 歌词/列表页这条 header 的曲名比封面页（`ArtworkDetailsWithoutArtwork`
                //   那条仍是 20sp）小一号：行首被 72dp 缩略图占掉 84dp，
                //   曲名可用宽度只剩 ~116dp，小一号能少截一个字。
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
            visible = bottomControlsVisible,
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
            // ★ 2026-10-25：塌缩期间不挂 `np-controls` 形变 —— 避免它与「迷你栏↔全屏」的收起
            //   morph 抢同一个共享元素键、被中途取消后吊在 overlay 里（控件飞走/消失）。
            Box(modifier = controlsSurface.then(if (closingPlayer.value) Modifier else sharedTransitionScope.npMorphElement("np-controls", avScope))) {
                if (page != MeloXNowPlayingPage.Artwork) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .meloXBackdropBlur(
                                shape = RectangleShape,
                                // ★ 2026-10-22（用户：「歌单进入动画还是卡，尤其是底栏在歌单封面的
                                //   上面时最卡」）：这块面板是**全屏宽 × 279dp**，24dp 高斯每帧都在
                                //   整块面积上跑一遍 —— 它是整个播放页里唯一一块这个量级的实时模糊，
                                //   也是用户把"卡"指认到「底栏」的直接原因。
                                //
                                //   运动期间模糊降档：转屏期间彻底关到 0dp（整屏在转、控制栏在淡入淡出，
                                //   0dp 与满档完全看不出），页间切换降到 10dp；落定立刻回到 24dp。
                                //   那段运动里人眼锁的是移动中的封面 / 队列行 / 翻转的布局，
                                //   模糊档位差看不出来，但高斯离屏 pass 直接省掉；转屏时这份收益**乘二**
                                //   （两块同场）。静止态一个像素都不变 —— 这条只吃布尔判据
                                //   （`orientationAnimating` / `pageAnimating`），不动任何曲线、时长与几何。
                                // 转屏期间彻底关模糊(0dp)：整屏在转、控制栏在淡入淡出，0dp 与降档值
                                // 完全看不出；落定瞬时回 24dp(与既有降档→满档突变等价)。削掉这块
                                // 全屏玻璃的离屏高斯 pass —— 转屏时竖横两套同场，收益乘二。
                                blurRadius = when {
                                    orientationAnimating -> 0.dp
                                    pageAnimating -> MeloXControlsBlurRadiusWhilePaging
                                    else -> MeloXControlsBlurRadius
                                },
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
                    // ★ 拖动进度条 → 歌词跟随（Apple Music 原版行为）。
                    scrubPreviewState = scrubPreviewState,
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
        enter = EnterTransition.None,
        exit = orientExit,
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
            // ★★★ 2026-11-03（第 29 轮）：**补上漏传的 `shellTransitioning`**。
            //
            //   真因：下面这棵横屏场景的形参注释白纸黑字写着「开 / 闭播放页的 morph 判据，
            //   透传给横屏歌词面板」（见 `MeloXIOSLandscapeNowPlayingScene` 的形参声明），
            //   而横屏歌词面板也**确实**在用它（`MeloXIOSLyricsPanel` 的 `shellTransitioning`），
            //   但**这个调用点从来没把它传进来** ⇒ 横屏拿到的永远是默认值
            //   `MeloXLyricsNeverTransitioning`（恒 false）。
            //
            //   后果（横屏专属，竖屏不受影响）：
            //     ① 首次定位**不等转场落定** —— `MeloXIOSLyricsPanel` 里
            //        `if (shellTransitioning.value) { …withTimeoutOrNull(500ms) 等落定… }`
            //        整块被跳过 ⇒ 横屏的歌词在**外壳还在展开时**就 `scrollToItem` 定位、
            //        紧接着 reveal 启动 ⇒ 歌词与外壳**同步**冒出来（竖屏是「外壳先到、
            //        歌词后到朦胧渐显」）⇒ 两端观感不一致。
            //     ② morph 期间**自动跟随滚动不推迟** —— 歌词滚动会与外壳整页缩放抢同一帧，
            //        而歌词每行都挂着 `Modifier.blur`（一个 RenderEffect）⇒ 叠加卡顿。
            //
            //   ⇒ 传的是同一个 `transitionInFlightState`（`expansionProgress < 0.999f`），
            //     与竖屏**完全同源**，不引入新判据、不改任何曲线与几何。
            shellTransitioning = shellTransitioning,
            // 转屏时竖横两套同场 ⇒ 横屏这半边的模糊也要降档（见形参注释）。
            // ⚠ 这里只能用 `orientationAnimating`：横屏分支在竖屏那棵 lambda 之外，
            //   `motionAnimating`（含 `pageAnimating`）不在作用域内。横屏内部的页间切换
            //   走的是它自己那套 AnimatedContent，不在本条判据覆盖范围内。
            motionAnimating = orientationAnimating,
            // ★ 2026-10-25：塌缩期间横屏侧也停掉 np-controls 形变（见 Scene 内说明）。
            npControlsMorphEnabled = !closingPlayer.value,
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
// 判据 = [MeloXWindowInfo.supportsPlayerTwoPane]（官方 WindowSizeClass 语义：宽 / 高都「非
// Compact」，即宽 ≥ 600dp 且高 ≥ 480dp；高度门槛才是手机拦路虎——手机横屏高度 = 其竖屏宽度
// ≤ 440dp，稳定不通过；2026-10-24 由写死像素门槛升级为尺寸分级）。
// 与手机横屏的差别**只有一处**：控制区从「右列的一个页面」（`page == Artwork`）
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
/** 双栏左右列之间的固定间距（Row 里的 Spacer，两档共用）。 */
private val MeloXTabletLandscapeColumnGap = 24.dp

/** 超宽车机档：左列**固定封面**整块的左右留白。 */
private val MeloXUltrawideColumnPadding = 16.dp

/**
 * 超宽车机档：右列（控制条 / 歌词 / 队列轮换页）的最小宽度护栏。
 *
 * G 轮起控制条不再占左列，右边整块都是**内容页** ⇒ 护栏要比旧「歌词 320dp」宽，
 * 才放得下曲名 + 进度 + 走带三键；极窄自由窗口下也保证封面不把右列挤没。
 */
private val MeloXUltrawideMinPaneWidth = 420.dp

/**
 * 超宽车机档：左列（封面 + 下方三键）与右列内容之间的间距。
 * 比平板档 24dp 更紧 ⇒ 「封面与控制条靠近」，歌词 / 队列页的左缘也跟着左移。
 */
private val MeloXUltrawidePaneGap = 16.dp

/**
 * 超宽车机档：封面 → 曲名行 / 曲名行 → 控件块的间距（H 轮）。
 * 比平板档 16dp 更紧 ⇒ 整块像参考图那样贴着封面，不浪费车机稀缺的纵向高度。
 */
private val MeloXUltrawideMetaGap = 8.dp

/**
 * 超宽车机档：封面**下方整块**的固定高度（H 轮按参考图重做）。
 * = 曲名行 56 + 上下两个 8dp 间距 + 紧凑控件块（进度 + 走带 + 三键）。
 * ⚠ 与 [MeloXIOSNowPlayingSharedHost] 的封面纵向落点、[meloXLandscapeArtworkSize]
 *   的高度预算**三处同源**，改这里必须三处一起核。
 */
private val MeloXUltrawideControlsBlock =
    MeloXTabletLandscapeMetaHeight + MeloXUltrawideMetaGap * 2f +
        (MeloXCarHeadUnitControlsHeight + MeloXCarHeadUnitSelectorHeight).dp

/** 超宽车机档：左列整块的上下最小留白。 */
private val MeloXUltrawideColumnMargin = 16.dp

/** 超宽车机档右列歌词字号倍率：参考图那种大字歌词（当前行亮、上下文半透明）。 */
private const val MeloXUltrawideLyricFontMultiplier = 1.15f

/**
 * 超宽车机档封面的**视觉缩放峰值**，来自 SharedHost 两条同源弹簧（见
 * `MeloXIOSNowPlayingSharedHost` 的 `playbackScale` / `switchBounce`）：
 * · 暂停 → 播放：`0.74 → 1.0`，`spring(ζ=0.70, k=280)` 过冲
 *   ≈ `(1−0.74) × exp(−πζ/√(1−ζ²))` ≈ 1.2% ⇒ 峰值 ≈ 1.012；
 * · 切歌回弹：`0 → 1`，`spring(ζ=0.42, k=480)` 过冲 ≈ 23%，经
 *   `bounceScale = 0.90 + 0.10 × bounce` 衰减 ⇒ 峰值 ≈ 1.023。
 * 切歌瞬间恰好在播时两者相乘 ≈ **1.036**，取 1.04 作保守上界。
 *
 * ⚠ 这些缩放全挂在 `graphicsLayer` 上，**不进测量**：布局尺寸 (= 本函数返回值 =
 * Scene 占位 / SharedHost `targetSize` / morph 源矩形) 恒是满尺寸，只有可见封面会
 * 按 0.74 ~ 1.04 呼吸。所以「播放放大那一帧」是否越界，必须由本函数**预先除掉峰值**；
 * 暂停缩小只会更小，天然安全。
 */
private const val MeloXUltrawideArtworkVisualPeakScale = 1.04f

/**
 * H 轮：封面基线的绝对上限。
 *
 * ⚠⚠ 历史教训（别再犯）：E 轮把可见上限放到 `.98H` ⇒ K20P 播放态封面实测
 *   868/960px 几乎顶满屏高，用户明确「太大了会丑」；F 轮收回 `.82H`。
 *   H 轮按参考图重做，改为「左列竖向预算反推封面高」，不再用占屏高的比例常量，
 *   此值只作绝对上限兜底。
 */
private val MeloXUltrawideArtworkBaseSizeCap = 420.dp

/**
 * 宽度加成相对基线的**最大增幅**。再宽 / 再高的车机也不越过 +12%，
 * 否则高度富裕机型（如 h800dp）会把 420dp 基线一路推到 600dp+，同样霸屏。
 */
private const val MeloXUltrawideArtworkMaxWidthBonusFrac = 0.12f

/** 富余宽度从 0 涨到该值时加成涨满；配合 easeOut 实现「一点一点变大」并收敛。 */
private val MeloXUltrawideArtworkWidthBonusSpan = 480.dp

internal fun meloXLandscapeArtworkSize(
    maxWidth: Dp,
    maxHeight: Dp,
    twoPane: Boolean,
    ultrawide: Boolean = false,
): Dp {
    val cap = minOf(420.dp, maxHeight * .85f, maxWidth * .42f)
    // ★★ 超宽车机（2.2:1+）H 轮（2026-11-07，按用户参考图重做）：
    //   左列 = 封面 + 曲名行 + 进度/走带/三键**竖向堆叠成一整块**，右列大字歌词常驻。
    //   ⇒ 车机的瓶颈是**纵向**：必须先给封面下方 [MeloXUltrawideControlsBlock] 留够，
    //     剩下的高度才是封面，再按播放放大峰值归一（缩放全在 graphicsLayer、不进测量）。
    //     基线取「可用高的一半」（参考图封面约占半屏高），横向富余时 easeOut 渐进
    //     最多 +12% ⇒ 有多的空间封面就大一点，但不会回到 E 轮「顶满屏高」的丑态。
    //   ⚠ 单点真源：Scene 左列占位 / SharedHost 持久封面 X、Y 落点 / 天际 clip 的
    //     `sourceSize` 全都只调这一份公式 + [meloXUltrawideArtworkCenterYFrac]，
    //     改这里四处自动同步，不会出现「封面大了、占位没跟上」的错拍。
    if (ultrawide && twoPane) {
        // ① 纵向预算：上下留白 + 封面下方整块控件 ⇒ 封面可用高；再除播放放大峰值。
        val heightRoom = (
            maxHeight - MeloXUltrawideControlsBlock - MeloXUltrawideColumnMargin * 2f
            ).coerceAtLeast(0.dp)
        val peakSafeHeight = heightRoom / MeloXUltrawideArtworkVisualPeakScale
        // ② 横向护栏：左列左右留白 + 列间距 + 右列歌词/队列最小宽度。
        //   ⚠ 不为放大帧额外扣宽度：峰值外扩每边仅 `(1.04−1)/2 × 边长` ≈ 6dp，
        //     左 [MeloXUltrawideColumnPadding]=16dp 本就吃得下；多扣会让窄车机反而更小。
        val reserved = MeloXUltrawideColumnPadding * 2f + MeloXUltrawidePaneGap +
            MeloXUltrawideMinPaneWidth
        val widthRoom = (maxWidth - reserved).coerceAtLeast(0.dp)
        // ③ 基线 → 宽度富余 → easeOut 收敛加成（最多 +12%）。
        val base = minOf(
            peakSafeHeight,
            maxHeight * 0.50f,
            MeloXUltrawideArtworkBaseSizeCap,
        )
        val ceiling = minOf(
            peakSafeHeight,
            base * (1f + MeloXUltrawideArtworkMaxWidthBonusFrac),
        ).coerceAtLeast(base)
        val spare = (widthRoom - base).coerceAtLeast(0.dp)
        val t = (spare / MeloXUltrawideArtworkWidthBonusSpan).coerceIn(0f, 1f)
        val growth = t * (2f - t)
        val grown = base + (ceiling - base) * growth
        // ④ 硬护栏：峰值帧不越界、右列歌词不被挤没、可见下限。
        return minOf(grown, peakSafeHeight, widthRoom).coerceAtLeast(120.dp)
    }
    if (!twoPane) return cap.coerceAtLeast(1.dp)
    val budget = maxHeight - MeloXTabletLandscapeGap - MeloXTabletLandscapeControlsBlock -
        MeloXTabletLandscapeMargin * 2f
    return minOf(cap, budget).coerceAtLeast(160.dp)
}

/**
 * 超宽车机档持久封面的**横向**落点（SharedHost 用）。
 *
 * ★ 2026-11-07 二修：超宽档左列**按内容定宽**（不再是 `.43W` 权重），整行从内容区左缘
 *   起排、只留 [MeloXUltrawideColumnPadding] ⇒ 封面中心 = `padding + 边长 / 2`。
 *   旧解 `.215 × 行宽 − Row宽/2 + 边长/2` 依赖平板权重，左列一改就会与占位错开，
 *   故一并废弃（非超宽档的 `0.215 → 0.5` 弹簧不受影响）。
 * ⚠ H 轮：车机档**不再有居中 / 左移动画**（封面与控件竖排成一整块、恒在最左），
 *   本函数即最终横向落点，不再叠加任何 progress 位移。
 * 尺寸取 [meloXLandscapeArtworkSize] 同一份公式 ⇒ 两处不会错拍。
 * ⚠ 纵向落点改用 [meloXUltrawideArtworkCenterYFrac]：左列现在是「封面 + 下方控件块」
 *   整体垂直居中，封面中心不再是 0.5H。
 * ⚠ 暂停缩小 / 播放放大都是以**中心**为轴的 `graphicsLayer` 缩放，不改变落点、不进测量
 *   ⇒ 任何播放态下封面与左列占位严格同轴，不会忽左忽右。
 */
internal fun meloXUltrawideArtworkCenterXFrac(maxWidth: Dp, maxHeight: Dp): Float {
    val size = meloXLandscapeArtworkSize(maxWidth, maxHeight, twoPane = true, ultrawide = true)
    val center = MeloXUltrawideColumnPadding + size / 2f
    // ⚠ 分母兜 1dp：首帧 constraints 可能是 0×0（morph 起跑 / 转屏重置），
    //   `x / 0f` 会得 Infinity，`coerceIn(0,1)` 把它压成 1 ⇒ 封面瞬间贴到最右再跳回。
    //   （NaN 场景 coerceIn 会返回 NaN，再被下游用来定位同样出事。）兜底后得 0f，稳定在左缘。
    return (center.value / maxWidth.value.coerceAtLeast(1f)).coerceIn(0f, 1f)
}

/**
 * 超宽车机档持久封面的**纵向**落点（SharedHost 用，H 轮新增）。
 *
 * 左列 `Column(verticalArrangement = Arrangement.Center)` 把
 * 「封面 + [MeloXUltrawideControlsBlock]」整块垂直居中 ⇒ 封面中心 =
 * `(可用高 − 整块高) / 2 + 封面边长 / 2`。
 *
 * ⚠ 与 [meloXLandscapeArtworkSize] 取同一份 size，且与 Scene 左列的 Arrangement
 *   逐字同解。写死 0.5f 会让封面压在曲名行上 —— G4 及以前左列只有封面时才成立。
 */
internal fun meloXUltrawideArtworkCenterYFrac(maxWidth: Dp, maxHeight: Dp): Float {
    val size = meloXLandscapeArtworkSize(maxWidth, maxHeight, twoPane = true, ultrawide = true)
    val total = size + MeloXUltrawideControlsBlock
    val top = (maxHeight - total) / 2f
    // 分母兜 1dp，理由同 [meloXUltrawideArtworkCenterXFrac]（0×0 首帧不产出 Infinity / NaN）。
    return ((top + size / 2f).value / maxHeight.value.coerceAtLeast(1f)).coerceIn(0f, 1f)
}

/**
 * 平板双栏封面**中心**的纵向比例（SharedHost 的持久封面用）。
 *
 * 与场景层左列的 `Arrangement.Center` 严格同解：整块 = 封面 + [MeloXTabletLandscapeGap] +
 * [MeloXTabletLandscapeControlsBlock]，在列内居中 ⇒ 封面中心 = 下边距 + 封面高 / 2。
 */
internal fun meloXTabletLandscapeArtworkCenterYFrac(maxWidth: Dp, maxHeight: Dp): Float {
    val size = meloXLandscapeArtworkSize(maxWidth, maxHeight, twoPane = true)
    // 分母兜 1dp，理由同 [meloXUltrawideArtworkCenterXFrac]。
    return ((MeloXTabletLandscapeMargin + size / 2).value / maxHeight.value.coerceAtLeast(1f))
        .coerceIn(0f, 1f)
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
    // ★ 2026-10-23：开 / 闭播放页的 morph 判据，透传给横屏歌词面板（见外层同名形参）。
    shellTransitioning: State<Boolean> = MeloXLyricsNeverTransitioning,
    /**
     * 播放页是否正在运动（页间切换 / 横竖屏切换，2026-10-22）。
     *
     * 横屏这套布局自己没有 transition 句柄（那条时间线在 Scene 里），但转屏时竖横
     * **两套同时在画** ⇒ 横屏这块四键胶囊的模糊也得跟着降档，否则「双份全价」只减了一半。
     * 默认 `false` = 不传即逐像素不变。
     */
    motionAnimating: Boolean = false,
    // ★ 2026-10-25：塌缩期间横屏这半边的 `np-controls` 也要停掉形变（与竖屏侧同理）。
    //   默认 true ⇒ 其它调用点逐像素不变。
    npControlsMorphEnabled: Boolean = true,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val window = rememberMeloXWindowInfo()
    // ★ 平板横屏双栏：左列常驻播放器卡片、右列歌词常驻（见本文件顶部常量区的长注释）。
    //   判据 = 官方 WindowSizeClass 语义（宽 / 高都「非 Compact」= 宽 ≥ 600dp 且高 ≥ 480dp；
    //   2026-10-24 由写死像素门槛升级为尺寸分级）。高度门槛稳定拦截手机横屏 ≤ 440dp
    //   ⇒ 手机横屏 / 竖屏 / 折叠屏半开全部为 false，下面所有 `if (twoPane)` 分支都不进。
    val twoPane = window.supportsPlayerTwoPane
    // 封面态 = 还没选歌词 / 队列 ⇒ 左列移到屏幕正中，**右列整个留空**。
    // ⚠ 不能实现成「把右列推出屏外 + 调低 alpha」：实测那样会在屏右缘留一段低透明度的歌词
    //   残影，且转屏时有一段看得见的右移过程 —— 用户报障 2026-10-07：
    //   「常规页面转横屏会有一段歌词右移，正确应该是直接在中间」。
    //   真留空另有两个附带好处：① 转屏进来时 `AnimatedContent` 的**初始** target 就是
    //   Artwork ⇒ 第一帧就没有歌词（连 exit 动画都不需要）；② 不白跑一套歌词布局。
    // ★ H 轮（2026-11-07 按用户参考图重做）：车机档**没有独立的封面态 / 控制条页** ——
    //   左列 = 封面 + 曲名 + 进度 / 走带 / 三键**竖排成一整块**、恒在最左（不再位移）；
    //   右列 = 大字歌词常驻，点三键的「列表」才换成队列（歌词 / 队列两态）。
    //   `page == Artwork` 在车机一律折叠成 Lyrics ⇒ `panePage` 两档共用。
    //   平板双栏保持原「封面态右列留空 + 玩家卡片居中」语义，逐像素不变。
    val ultrawidePlayer = window.ultrawidePlayer
    val coverState = twoPane && !ultrawidePlayer && page == MeloXNowPlayingPage.Artwork
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
    // ★ 2026-10-23：与竖屏同一处修复 —— 重置脉冲改用 SharedFlow（见竖屏 `lyricsControlsActivity`
    //   的长注释：原来的组合期计数器只在「点按唤出 / 点选页面」时递增，滚动歌词与点三键都不算
    //   ⇒ 用户正在操作却被按时长收走）。
    val landscapeInterfaceActivity =
        remember(state.mediaId) { MutableSharedFlow<Unit>(extraBufferCapacity = 8) }

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
        landscapeInterfaceActivity.tryEmit(Unit)
        showsInterface = true
        // ★ 再点一次当前页 ⇒ 退回**封面态**（`Artwork`）。
        //   ⚠ 2026-10-07 起**双栏也恢复这条开关**（旧注释「双栏不做退回封面页的开关 /
        //     封面页这一态不存在」已作废）：封面态在双栏下的表达 = **左列滑到屏幕正中、
        //     右列推出屏外**（见 `columnCenterProgress`），三键全不亮。
        //     它同时是回到"玩家卡片居中"那一态的**唯一入口**，所以两个分支合并成一条。
        onPageChanged(
            when {
                // H 轮：车机只有「歌词 / 队列」两态、没有封面态可退回 ⇒ 再点当前页保持原页。
                ultrawidePlayer -> destination
                page == destination -> MeloXNowPlayingPage.Artwork
                else -> destination
            },
        )
    }

    LaunchedEffect(page) {
        showsInterface = true
    }
    LaunchedEffect(page, showsInterface) {
        // ⚠ 双栏没有可隐藏的浮层（右列既无曲名栏也无三键），而 `isInterfaceHidden` 会改
        //   歌词面板自身的字号 ⇒ 若沿用自动隐藏，读词读一半字号会突然变化。双栏恒不隐藏。
        if (twoPane) return@LaunchedEffect
        // 只有歌词页需要沉浸：封面页靠三键翻页、队列页 padding 已给三键让位，两页恒显。
        if (page != MeloXNowPlayingPage.Lyrics || !showsInterface) return@LaunchedEffect
        // 与竖屏同一条逻辑：等满一整段没有任何活动才收；任何活动脉冲都重新起跑。
        while (true) {
            val idled = withTimeoutOrNull(
                MeloXSettingsRuntime.lyricInterfaceAutoHideDelayMs.toLong(),
            ) {
                landscapeInterfaceActivity.first()
            }
            if (idled == null) {
                showsInterface = false
                return@LaunchedEffect
            }
        }
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
    // ★ 2026-10-23：整页「用户正在操作」嗅探（同竖屏那份）。横屏只在歌词页且非双栏
    //   才存在自动隐藏，其余情况关掉嗅探（不白跑指针观察）。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .meloXPlayerStatusBarsPadding()
            // 机型适配：横屏挖孔摄像头所在的那一侧短边留白，内容不挡摄像头。
            .meloXPlayerDisplayCutoutPadding()
            .padding(horizontal = 20.dp)
            .alpha(landscapeContentAlpha)
            .meloXUserActivityPing(
                enabled = !twoPane && page == MeloXNowPlayingPage.Lyrics,
                activity = landscapeInterfaceActivity,
            ),
    ) {
        // 中间「封面 + 右列」整块与 SharedHost 封面层的 BoxWithConstraints 边界完全一致
        // （同样的 statusBars + cutout + horizontal 20）⇒ 同一份 maxWidth / maxHeight。
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // ⚠ 与 SharedHost 持久封面**同一份**尺寸计算（单点在 `meloXLandscapeArtworkSize`），
            //   既给手机横屏的整块限高、也给天际共享元素源封面定尺寸（源 ⇄ 目标 132dp 形变）：
            //   手机横屏 = min(420dp, 85% 高, 42% 宽)；平板双栏还要扣掉左列封面下方整块。
            val landscapeSize = meloXLandscapeArtworkSize(maxWidth, maxHeight, twoPane, ultrawidePlayer)
            // 双栏「封面态」整行右移的像素量 = 行宽 × 0.285（**仅平板双栏**）。
            // 行宽 == 本 Box 的 maxWidth（Row 是 `fillMaxWidth`），也正是 SharedHost
            // 封面层那一份 maxWidth。
            // ★ H 轮：车机档恒不位移（左列整块播放器恒在最左），这个值在车机不会被消费。
            // H 轮：车机档恒不位移（封面 + 控件整块恒在最左）；平板仍用绑定 .43W 的 0.285 定值。
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
                        if (twoPane && !ultrawidePlayer) {
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
                    // ★ H 轮（2026-11-07，按参考图重做）：超宽车机左列 = **封面 + 曲名行 +
                    //   进度 / 走带 / 三键**，整块竖排、宽度严格等于封面边长，像桌面 / 手机
                    //   播放页那样是一个完整的「播放器」；右列大字歌词常驻，吃剩余全部宽度。
                    //   ⚠ 封面仍由 SharedHost 持久层绘制，这里只占位；X 落点
                    //     [meloXUltrawideArtworkCenterXFrac]、Y 落点
                    //     [meloXUltrawideArtworkCenterYFrac] 与本列 Arrangement 同解。
                    //   ⚠ 整块高度 = size + [MeloXUltrawideControlsBlock]，与尺寸公式的
                    //     纵向预算同源；平板档仍用原 .43f 权重 + 351dp 控制卡片，逐像素不变。
                    val ultrawideLeftColumnWidth =
                        landscapeSize + MeloXUltrawideColumnPadding * 2f
                    Column(
                        modifier = (
                            if (ultrawidePlayer) Modifier.width(ultrawideLeftColumnWidth)
                            else Modifier.weight(.43f)
                            ).fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.size(landscapeSize))
                        if (ultrawidePlayer) {
                            // ★ H 轮：封面下方依次是「曲名 / 艺人 + 收藏 / 更多」→
                            //   「进度 + 走带 + 三键」紧凑窄带，全部与封面**等宽**（参考图）。
                            //   ⚠ 三键由 CoreControls 内部那枚提供（`np-selector` 全树唯一），
                            //     不再单独画一枚 ScenePageSelector；容器锁死封面边长 ⇒
                            //     三键横向永远不越过封面的最大位置。
                            Spacer(Modifier.height(MeloXUltrawideMetaGap))
                            Row(
                                modifier = Modifier
                                    .width(landscapeSize)
                                    .height(MeloXTabletLandscapeMetaHeight),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                MeloXPlaybackTitleMenu(
                                    state = state,
                                    onOpenQueue = { select(MeloXNowPlayingPage.Queue) },
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
                                    MeloXSymbolIcon(
                                        MeloXSymbol.Ellipsis,
                                        Modifier.size(18.dp),
                                        Color.White,
                                        iconSize = 16.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(MeloXUltrawideMetaGap))
                            Box(Modifier.width(landscapeSize)) {
                                MeloXNowPlayingCoreControls(
                                    state = state,
                                    // 车机没有封面态：Artwork 一律按 Lyrics 显示三键选中态。
                                    page = panePage,
                                    onPageSelected = ::select,
                                    onShowQuality = onShowQuality,
                                    showPageSelector = true,
                                    compact = true,
                                    useVectorGlyphs = true,
                                    carHeadUnit = true,
                                    selectorModifier = sharedTransitionScope.npMorphElement(
                                        "np-selector",
                                        animatedVisibilityScope,
                                    ),
                                )
                            }
                        } else {
                            Spacer(Modifier.height(MeloXTabletLandscapeGap))
                            TabletLandscapePlayerColumn(
                                state = state,
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
                // ★ G3：车机用更紧的列间距，让控制条 / 歌词 / 队列都贴近封面。
                Spacer(
                    Modifier.width(
                        if (ultrawidePlayer) MeloXUltrawidePaneGap
                        else MeloXTabletLandscapeColumnGap,
                    ),
                )

                Column(
                    modifier = Modifier
                        .weight(if (ultrawidePlayer) 1f else .57f)
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
                            // ⚠ 双栏（含 H 轮车机）一律传 `panePage`（Artwork 折叠成 Lyrics）
                            //   ⇒ 右列只在「歌词 / 队列」之间切。车机不再有独立的控制条页，
                            //   左列那一整块播放器恒在、大字歌词常驻右侧（参考图形态）。
                            targetState = if (twoPane) panePage else page,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = { meloXContentEnter() togetherWith meloXContentExit() },
                            label = "landscape-player-page",
                        ) { destination ->
                            when (destination) {
                                // ★ 双栏封面态 ⇒ 右列**真的留空**（见 `coverState` 的注释：
                                //   推出屏外 + 降 alpha 会留残影、转屏时还会看到歌词右移）。
                                //   H 轮：车机的 Artwork 已折叠成 Lyrics ⇒ 本分支车机永不进入。
                                //   非双栏（手机横屏封面页）逐像素保持原实现。
                                MeloXNowPlayingPage.Artwork -> if (twoPane) Box(
                                    Modifier.fillMaxSize(),
                                ) else Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        // ★ 2026-10-25：塌缩期间不挂 np-controls 形变（同上）。
                                        .then(if (npControlsMorphEnabled) sharedTransitionScope.npMorphElement("np-controls", animatedVisibilityScope) else Modifier),
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
                                            // ★ G3：车机歌词左缘跟着左移，紧贴封面右侧；
                                            //   面板逐行自带 8dp ⇒ 不会真的贴边。
                                            .padding(
                                                start = if (ultrawidePlayer) 0.dp
                                                else MeloXLandscapeLyricsStartInset,
                                            ),
                                        // ⚠ 双栏恒 false：右列没有可隐藏的浮层，而隐藏会改歌词
                                        //   字号（见上方自动隐藏 LaunchedEffect 的注释）。
                                        isInterfaceHidden = !twoPane && !showsInterface,
                                        controlsRevealHeight = if (twoPane) 0.dp else 56.dp,
                                        onInterfaceInteraction = {
                                            landscapeInterfaceActivity.tryEmit(Unit)
                                            showsInterface = true
                                        },
                                        onInterfaceVisibilityChange = {
                                            landscapeInterfaceActivity.tryEmit(Unit)
                                            showsInterface = it
                                        },
                                        // Skyline is an explicit alternate presentation. Enabling it
                                        // exposes the affordance but must not override the lyric style.
                                        allowAutomaticSkyline = false,
                                        active = lyricsActive,
                                        // ★ 字号：手机横屏整体缩小（见常量注释）；平板双栏右列是
                                        //   整屏高，回到面板默认倍率，不再收缩。
                                        fontScaleMultiplier = when {
                                            // H 轮：车机右列是参考图那种大字歌词，比平板档放大一档；
                                            //   右列 900dp+ 宽，放大后长行仍不折行（真机已核）。
                                            ultrawidePlayer -> MeloXUltrawideLyricFontMultiplier
                                            twoPane -> MeloXTabletLandscapeLyricFontMultiplier
                                            else -> MeloXLandscapeLyricFontMultiplier
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
                                        // ★ 开 / 闭播放页期间逐行模糊归零 + 推迟自动跟随滚动。
                                        shellTransitioning = shellTransitioning,
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
                                        // + 底部三键），否则首尾项会被永久遮住。
                                        // ★ 左缘对齐：行 / 分区标题本来贴 0，补满 18dp 与三键最左那颗
                                        //   按钮的左缘齐平（见本文件顶部契约）。
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(
                                                // ★ G3：车机队列左缘同样跟着左移，贴紧封面右侧。
                                                start = if (ultrawidePlayer) 0.dp
                                                else MeloXCompactPageSelectorHPadding,
                                                // ⚠ 顶部 64dp 是给「裸浮层」曲名栏让位；双栏右列没有浮层
                                                //   （两者都在左列）⇒ 归零，列表铺满整列。
                                                top = if (twoPane) 0.dp else 64.dp,
                                                // ★ 底部让位**不靠 padding，靠渐隐归零点**（2026-10-21
                                                //   按竖屏歌词/队列页同款做法修正）：
                                                //   旧方案用 `bottom = 52dp` 把整个面板顶上去 ⇒ 视口被
                                                //   『三键占位 + 渐隐带』双重压缩，内容尾部还留一段实心空白，
                                                //   可读行反而比 `bottom=66dp` 时代更少（用户两轮报障）。
                                                //   ⇒ bottom 收到 0、列表铺满整列；用 `fadeBottomTerminal`
                                                //   把渐隐归零点顶到**三键顶部**（自屏幕底往上 52dp），
                                                //   让最后一行在抵达三键前自然溶解，既不遮三键、也不丢行。
                                                bottom = if (twoPane) 0.dp else 0.dp,
                                            ),
                                        showSongHeader = false,
                                        interactive = true,
                                        // 双栏右列同样是横屏几何 ⇒ 与手机横屏同一档紧凑
                                        // （四键栏 64 → 30dp）。右列没有常驻控件面板，
                                        // 少这一档会让横屏双栏比手机横屏多占 34dp。
                                        compact = true,
                                        // ★ 底部渐隐（2026-10-21，学竖屏队列页的 `fadeBottomTerminal`）：
                                        //   · 竖屏让位量 = 控制条高 279dp + 手势条安全区；
                                        //   · 横屏三键是裸浮层、无底板，实际占位 = 42dp 行高 +
                                        //     [MeloXLandscapeSelectorOffsetY](10dp) = 52dp。
                                        //   · 横屏三键是裸浮层、无底板，实际占位 = 42dp 行高 +
                                        //     [MeloXLandscapeSelectorOffsetY](10dp) = 52dp。
                                        //   ⇒ 位置定案（用户 2026-10-21 「合适了」）：
                                        //     归零点 = 20dp 让内容铺满到屏底附近；
                                        //     渐隐带 = 16dp，只把最末一行的**尾部**化开，
                                        //     给「贴底」加一层自然的淡出过渡（不再硬切）。
                                        //   ⚠ 不变量：fadeBottom ≤ fadeBottomTerminal，否则
                                        //     bottomStop 越过 bottomEnd、渐隐带跑出视口（白给）。
                                        fadeBottom = 16.dp,
                                        // ★ G3：车机三键搬到左列封面下方 ⇒ 右列底部又无浮层，
                                        //   归零点回到 20dp，与其它档逐像素一致。
                                        fadeBottomTerminal = 20.dp,
                                        // ★ 2026-10-22：横屏四键胶囊同样吃运动期降档。
                                        //   转屏时竖横两套都在场，两块胶囊的模糊是**同时**在算的
                                        //   ⇒ 这是「横竖屏切换百分百卡」里的一分子。
                                        motionAnimating = motionAnimating,
                                    )
                                }
                            }
                        }

                        // ⚠ 平板双栏**不画曲名栏浮层**：曲名行在左列常驻控制卡片里，
                        //   留着会压在歌词上、且屏幕上出现两份曲名。
                        //   ★ G3：底部三键浮层只给手机横屏（`if (!twoPane)`）。平板挂左列控制卡片、
                        //     车机挂左列封面正下方，两档都常驻且不会随右列三态消失。
                        //   ⚠ `np-selector` 全树唯一：三档互斥渲染，不会同时出现两枚。
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
                        }

                        // ★ G3：车机三键已搬到左列**封面正下方**（常驻、不随右列三态消失），
                        //   这里只保留非双栏（手机横屏）的底部浮层，避免两处重复。
                        //   ⚠ `np-selector` 全树唯一：平板挂左列控制卡片、车机挂左列封面下方、
                        //     手机横屏挂这里，三档互斥渲染。
                        if (!twoPane) {
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
                val landscapeSize = meloXLandscapeArtworkSize(maxWidth, maxHeight, twoPane, ultrawidePlayer)
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
    // H 轮：车机已不再复用本卡片（改用 [MeloXNowPlayingCoreControls] 的紧凑档），
    //   保留参数供平板 / 手机横屏按页面关闭三键；`np-selector` 全树仍必须唯一。
    showPageSelector: Boolean = true,
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
            showPageSelector = showPageSelector,
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
    val ops = remember(app) { NeteaseMusicOperationsClient(cookieProvider = cookieProvider) }
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

    // ★ 同步任务句柄：连点时取消上一次「待发的网络请求」，等用户停手后再发最终状态。
    //   既保证 UI 每一次点击都立即响应，又避免对限流接口狂轰（见下方 toggle 防抖）。
    val syncJobState = remember(mediaId) { mutableStateOf<Job?>(null) }

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
                // Local unified playlist + the owning provider must both change;
                // writing only locally let the next library refresh restore the
                // server value and visually drop the favorite.
                val desired = likedState.value != true
                track?.let { likedState.value = MeloXUnifiedFavorites.toggle(app, it) }
                val neteaseId = identity?.takeIf { it.sourceId.builtIn == MusicSource.Netease }
                    ?.value?.toLongOrNull()
                if (neteaseId != null) {
                    scope.launch {
                        runCatching {
                            com.lladlam.melox.core.network.NeteaseMusicOperationsClient(
                                cookieProvider = { NeteaseSessionStore.readCookie(app) },
                            ).setSongLiked(neteaseId, desired)
                        }
                    }
                }
                return@remember
            }
            val id = identity?.takeIf { it.sourceId.builtIn == MusicSource.Netease }
                ?.value?.toLongOrNull() ?: return@remember
            // ★ UI 立即翻转：无论网络成败，用户看到的状态永远等于「自己点的那一下的意图」，
            //   这是「点多几次按钮没反应」彻底消失的根本——点击一定有可见反馈。
            val desired = likedState.value != true
            likedState.value = desired
            val mine = ++seqState.value
            // 取消上一次尚未发出的同步（含其退避重试），用本次意图整体覆盖。
            syncJobState.value?.cancel()
            syncJobState.value = scope.launch {
                // ★ 防抖：连点时只等用户停手（350ms）发一次最终请求，从源头避开
                //   网易云「操作频繁，请稍后」限流——连点 5 下 = 1 次网络请求。
                try {
                    delay(350)
                } catch (_: CancellationException) {
                    return@launch // 被更新的 tap 取代，作废
                }
                if (seqState.value != mine) return@launch // 期间又 tap 过，本请求作废
                // ★ 限流/瞬时失败时带退避重试，直到服务器拿到最终状态为止。
                //   这样「点多几次」既能立即看到响应、最终也真正落库，而不是悄悄失败。
                var attempt = 0
                while (true) {
                    attempt++
                    try {
                        ops.setSongLiked(id, desired)
                        return@launch
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        val msg = e.message ?: ""
                        // 网易云限流提示词特征：「操作频繁」/「稍候」/「稍后」。
                        val rateLimited = msg.contains("操作频繁", ignoreCase = true)
                            || msg.contains("稍候") || msg.contains("稍后")
                        if (rateLimited && attempt < 3) {
                            delay(800L * attempt) // 退避后重试，乐观状态继续保留
                            continue
                        }
                        // 非限流错误（如未登录）：保持乐观状态**不回滚**，仅轻提示。
                        if (!rateLimited) {
                            MeloXPlaybackNotice.show(context, msg.ifBlank { "收藏失败，请稍后重试" })
                        }
                        return@launch
                    }
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

/**
 * 底栏控制区（`np-controls`）背景模糊的**静止档**。
 *
 * 这块面板是全屏宽 × [MeloXNowPlayingControlsHeight]（279dp），是整个播放页里
 * 面积最大的一块实时模糊。数值来自历史定稿（`Black 8%` + 24dp），改这里会影响
 * 静止态观感 —— 只有「页间切换运动期间」才允许临时降档，见下一项。
 */
private val MeloXControlsBlurRadius = 24.dp

/**
 * 底栏控制区背景模糊的**页间切换运动档**（2026-10-22）。
 *
 * 只在 `pageTransition.isRunning` 期间生效，落定立刻回到 [MeloXControlsBlurRadius]。
 * 依据：那三条页面过渡是 `spring(0.7, 300)`（欠阻尼），实际运动跨度约 1s，
 * 期间这块 279dp 的全宽模糊要每帧重算一整秒。运动画面上 10dp 与 24dp 是同一块
 * 磨砂，但高斯降采样链少一级。
 */
private val MeloXControlsBlurRadiusWhilePaging = 10.dp

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

/**
 * 「用户正在操作」嗅探器 —— 播放页静置自动隐藏（沉浸）的重置脉冲来源。
 *
 * 为什么挂在场景根节点上：静置计时器的重置入口原先只有「控件隐藏时点按唤出」和
 * 「点选页面选择器」两条，**滚动歌词 / 点播放暂停 / 上一首下一首 / 收藏 / ⋯ / 调音量
 * / 长按**全都不算活动 ⇒ 用户明明在操作，控件条照样按起跑时间被收走（用户报障）。
 * 挂根节点一次覆盖全部手势：祖先恒在命中路径上，不必逐个按钮接线，以后新增控件也自动覆盖。
 *
 * 三条硬约束：
 * ① **只观察，不消费**：走 [PointerEventPass.Initial]（全链路第一站），不调用
 *    `consume()`、不 return 任何东西 ⇒ 滚动、点击、拖拽全部原样继续下发给真正的接收者。
 *    （歌词根节点上那条 `detectPassThroughTap` 的注释解释过：一旦消费就会把歌词"焊死"。）
 * ② **节流**：长滑一次会产生上百个移动事件，逐个发射等于每次重组一遍场景层。
 *    这里按 [MeloXUserActivityPingIntervalMs] 节流，只要求「远小于最短的自动隐藏时长
 *    （3s）」，保证滑动期间计时器永远重置不到点。
 * ③ **不读组合期状态**：脉冲走 SharedFlow 在计时协程里收，调用方不做
 *    `mutableIntStateOf` 计数 ⇒ 重置动作本身**零重组**。
 */
private fun Modifier.meloXUserActivityPing(
    enabled: Boolean,
    activity: MutableSharedFlow<Unit>,
): Modifier {
    if (!enabled) return this
    // ⚠ key 用 `activity`（`remember` 出来的稳定实例）而不是 lambda：lambda 每次重组都是
    //   新实例，一旦被当作 key 就会让指针观察协程重启、把节流计时清零。
    return pointerInput(activity) {
        awaitPointerEventScope {
            var lastAtMs = Long.MIN_VALUE
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val isActivity = when (event.type) {
                    PointerEventType.Press,
                    PointerEventType.Move,
                    PointerEventType.Release,
                    -> true
                    else -> false
                }
                if (!isActivity) continue
                // 抬起也算一次：长按（不移动）之后松手同样不该马上被收走。
                val nowMs = event.changes.firstOrNull()?.uptimeMillis ?: continue
                if (lastAtMs == Long.MIN_VALUE ||
                    nowMs - lastAtMs >= MeloXUserActivityPingIntervalMs
                ) {
                    lastAtMs = nowMs
                    activity.tryEmit(Unit)
                }
            }
        }
    }
}

/** 活动脉冲节流窗口（ms）。必须显著小于自动隐藏时长的设置下限（3s）。 */
private const val MeloXUserActivityPingIntervalMs = 400L
