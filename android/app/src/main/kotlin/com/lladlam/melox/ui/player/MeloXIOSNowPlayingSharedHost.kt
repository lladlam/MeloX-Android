package com.lladlam.melox.ui.player

import androidx.activity.compose.BackHandler
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollSource.Companion.UserInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.lladlam.melox.BuildConfig
import com.lladlam.melox.ui.MeloXSlideShapeCache
import com.lladlam.melox.ui.MeloXUiMotionRuntime
import com.lladlam.melox.ui.glass.LocalMeloXBackdrop
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXPlayerBackgroundMode
import com.lladlam.melox.ui.settings.MeloXPlayerShell
import com.lladlam.melox.ui.settings.MeloXScreenAwakeMode
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import com.lladlam.melox.ui.layout.rememberMeloXWindowInfo
import com.lladlam.melox.core.network.MeloXSearchKind
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MeloXIOSNowPlayingSharedHost(
    state: MeloXPlaybackUiState,
    onDismiss: () -> Unit,
    // ★ 2026-10-15 新增：**转场可打断**（用户口径「完全关闭前不能再次点击打开」）。
    //   关闭动画进行中，在播放面上做一次**点按**（非拖拽）即抢回播放页 ——
    //   由 `MeloXApp` 传 `openPlayer`（与点迷你栏**同一条链路**）⇒ 关闭途中再点一下，
    //   动画从**当前 fraction 连续**回 1f（`SeekableTransitionState.animateTo` 天生支持打断：
    //   位置连续、不跳变、不重播）。默认 `{}` ⇒ 其它调用点行为不变。
    onReopen: () -> Unit = {},
    // ★ 2026-10-15 修「打断动画期间点底栏误触抢回」：reopen 触发范围限制在 mini 播放器
    //   自身命中矩形内。该矩形由 mini 播放器挂 `onGloballyPositioned` 实时上报（window 坐标），
    //   本函数全屏 `pointerInput` 据此只在点击落在 mini 区域时才抢回；其余区域仍只拦截
    //   （防按钮误触）不抢回 —— 于是关闭动画中点底栏导航不再误入播放页。
    miniPlayerHitRect: MutableState<Rect?> = mutableStateOf(null),
    onNavigateSearch: (String, MeloXSearchKind) -> Unit = { _, _ -> },
    // ⚠ 三方合并遗留（2026-10-11）：上游 3f877ea 在此新增了 `onNavigateAlbum` /
    //   `onNavigateArtist`（播放页 ⋯ 菜单跳专辑 / 歌手），但本地侧**从来没有这条链路**
    //   —— `MeloXNowPlayingActionsSheet` 与下游 `NeteaseSongActionsOverlay` 都不带这两个
    //   参数，且 `MeloXPlaybackUiState` 也没有 `albumId` / `artistId`。
    //   ⇒ 完整采纳需要**连锁合并** ActionsSheet + SongActionsOverlay + UI state，
    //   会继续扩散，故本轮**整体退回本地 API**，该上游特性单独记为待办（见 PR 说明）。
    onOpenPlaybackSettings: () -> Unit,
    onLocalMetadataChanged: () -> Unit = {},
    onSeekCollapse: suspend (Float) -> Unit,
    onSettleCollapse: suspend (Boolean) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    // ⚠ 由 MeloXApp 层传入（2026-09-30）：封面背景背板声明在更外层，以便迷你栏也能
    //   采样它做关闭过渡的交叉淡入。本函数只负责「录制」它。
    playerControlBackdrop: LayerBackdrop,
    onArtworkPageChanged: (Boolean) -> Unit = {},
    // ⚠⚠⚠ 2026-10-04 修「下沉回弹偶尔消失」：主状态**正在朝关闭推进**的显式 intent，
    //   由 `MeloXApp` 在调用 `animateTo/seekTo` **之前**同步写入（见那里的注释）。
    //   `BoundsTransform` 生成 spec 时会读方向，而它求值的那一帧与 `targetState` 翻转的
    //   那一帧之间没有顺序保证 ⇒ 只看 `targetState` 会偶发漏判 ⇒ keyframes 被退化成普通
    //   tween ⇒ 那一轮回弹**完全消失**。本 intent 在动画起跑前就已确定 ⇒ 不会被漏读。
    //   ⚠ 语义是「**或**」：`targetState == PostExit` 仍保留（覆盖未走 intent 的历史路径），
    //     两者任一为真即判定关闭。默认 false ⇒ 其它调用点行为不变。
    closingIntent: Boolean = false,
    // ⚠⚠⚠ 2026-10-06 新增（修「回弹时内容层先反向」）：`SeekableTransitionState.targetState`
    //   的镜像，由 `MeloXApp` 直接传入。
    //   为什么需要它：`expansionProgress` 的**动画目标值必须随拖拽/回弹发生变化**，
    //   否则 `TransitionAnimationState` 会被视为「已完成」，**不参与 `SeekableTransitionState`
    //   的 seek** ⇒ 拖拽时内容层不跟随（实测：p 恒 1.000，P 行几乎无输出）。
    //   而原来的 `EnterExitState` 虽然会变（拖拽时翻成 PostExit ⇒ 目标 0f），但它在**松手
    //   头几帧仍是退场侧** ⇒ 动画继续朝 0f 跑 ⇒ p 先掉 16.5% 再升回（就是「下沉抽搐」）。
    //   `targetState` 同时满足两边：拖拽收起时它是 `false`（目标 0f ⇒ 动画活跃 ⇒ seek 生效），
    //   回弹/打开时它是 `true`（目标 1f ⇒ 不回退）。默认 `true` ⇒ 漏传时表现为「朝向打开」，
    //   不会造成误关闭。
    transitionTargetOpen: Boolean = true,
) {
    // A track transition updates the content inside the existing player. It must
    // not recreate the page/gesture state or send the shared element back to its
    // MiniPlayer bounds while the full-screen player is still open.
    val context = LocalContext.current.applicationContext
    val rememberPlayerPage = MeloXSettingsPreferences.boolean(context, "playback_remember_page", true)
    var page by androidx.compose.runtime.saveable.rememberSaveable(rememberPlayerPage) {
        mutableStateOf(
            if (rememberPlayerPage) runCatching {
                MeloXNowPlayingPage.valueOf(
                    MeloXSettingsPreferences.string(context, "playback_last_page", MeloXNowPlayingPage.Artwork.name),
                )
            }.getOrDefault(MeloXNowPlayingPage.Artwork) else MeloXNowPlayingPage.Artwork,
        )
    }
    var transitionSourcePage by remember {
        mutableStateOf(MeloXNowPlayingPage.Artwork)
    }
    var showActions by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    // ⋯ 菜单「一起听徽标」打开路径（`openedFromTogetherBadge`）不经 `showActions`，
    // 其可见性由菜单回调上报（见 `MeloXNowPlayingActionsSheet` 的
    // `onEffectiveVisibleChange`）。宿主据此判断整屏背板捕获有没有消费者。
    var actionsOverlayVisible by remember { mutableStateOf(false) }
    var showLandscapeSkyline by remember { mutableStateOf(false) }
    var lyricsInterfaceHidden by remember { mutableStateOf(false) }
    val window = rememberMeloXWindowInfo()
    val isLandscape = window.isLandscape
    // ★ 平板横屏双栏（官方 WindowSizeClass 语义：宽 / 高都「非 Compact」= 宽 ≥ 600dp 且高 ≥ 480dp；
    //   2026-10-24 由写死像素门槛升级为尺寸分级；高度门槛才是手机拦路虎）。
    //   手机 / 竖屏恒 false ⇒ 下面所有新分支不进。
    val twoPane = window.supportsPlayerTwoPane
    // ★ 统一主时间线：整条 orientation Transition 同时驱动封面（位置/尺寸）与场景层
    // （竖/横淡入淡出），转屏时「封面滑移 + 场景淡变」在**同一条 spring** 上连续发生 =
    // 真正的一镜到底。此前封面用独立 animateDpAsState(tween)、场景用独立 AnimatedContent(tween)，
    // 两套时钟各跑各的，读起来是「封面挪位 + 旧布局淡出 + 新布局淡入」三件事叠加 = 用户判「不及预期」。
    // ★★ 2026-10-22 第四轮：转屏主时间线的**起跑闸**（用户：「横竖屏切换动画百分百卡顿」）。
    //
    //   实测（FrameMetrics + atrace，第 3 段）：转屏最坏帧 `Choreographer#doFrame` 646ms，
    //   其中 `animation` 相位 362ms、RenderThread `Drawing 3168x1440` 291ms、
    //   `postComposition` 206ms；而**同一动作换到空闲时刻**最坏只有 61.5ms
    //   （`Compose:recompose` 0.84ms、`measureAndLayout` 2.0ms）。
    //   ⇒ 瓶颈不是重组/布局，而是「本层 300ms 动画的起跑帧和系统重配置那一帧撞在一起」。
    //
    //   做法：`isLandscape` 翻过来之后先让 [ORIENT_SETTLE_FRAMES] 帧再起跑。
    //   代价：旧布局在新尺寸上多停 ~17ms（120Hz 两帧），肉眼不可辨；
    //   换来的：整段 fade + 封面滑移不再与系统重配置抢帧。
    //   ⚠ 这是**观感类**改动，下一次实测若无改善就整段回退（把常量改成 0 即可，
    //     不需要动任何别的代码）。
    var orientTarget by remember { mutableStateOf(isLandscape) }
    LaunchedEffect(isLandscape) {
        if (isLandscape == orientTarget) return@LaunchedEffect
        repeat(ORIENT_SETTLE_FRAMES) { withFrameNanos { } }
        orientTarget = isLandscape
    }
    // ★ 2026-10-25（修「横转竖后马上收起 ⇒ 控件异常地往右下角飞」）：收起**一发生**
    //   （`transitionTargetOpen` 由 MeloXApp 在 animateTo/seekTo 之前同步写入，是最早信号）
    //   就把 `orientTarget` 立即钉到当前设备朝向，**跳过 [ORIENT_SETTLE_FRAMES] 着陆缓冲**。
    //   否则「刚转完屏就收起」时 orientTarget 可能仍停在旧朝向 ⇒ 场景以旧朝向布局为可见目标、
    //   封面几何也按旧朝向算 ⇒ 收起 morph 作用在错误布局的控件上（横屏左列 → 迷你栏）= 往右下角飞。
    LaunchedEffect(transitionTargetOpen) {
        if (!transitionTargetOpen && orientTarget != isLandscape) orientTarget = isLandscape
    }
    val orientTransition = updateTransition(targetState = orientTarget, label = "now-playing-orient")
    // ★ 2026-10-25（用户批准）：**横竖屏形变进行中禁止退出**。窗口 = 那条 spring（约 0.3s），
    //   代价很小，却能彻底消除「转屏 + 收起」两条 morph 抢同一个 `np-controls` 共享元素键
    //   ⇒ 控件飞向右下角 / 凭空消失 / 重进后迟迟不回 这一整类问题（共享元素生命周期不可控）。
    //   只读离散的 `isRunning`（起步 / 落定各翻一次），不是逐帧值。
    val orientationBusy = orientTransition.isRunning
    // ★★ 平板双栏「玩家卡片居中 ⇄ 左右分栏」的**单一时钟**（2026-10-07）。
    //   状态定义：**双栏 + `page == Artwork`**（= 还没选歌词 / 队列，即"封面态"）⇒ 左列整块
    //   移到**屏幕正中**、右列整个不画；点歌词 / 队列（`page != Artwork`）⇒ 反向回左列、
    //   右列淡入。手机 / 竖屏 `twoPane == false` ⇒ 恒 0，逐像素不变。
    //   ⚠⚠ 这**一个**值必须同时喂给场景层（Row 的 `offset`）与持久封面层（`centerXFrac`）：
    //     两处各跑一份动画，哪怕 spec 逐字相同，只要起跑帧差一帧，封面就会与左列占位错开
    //     （本仓库铁律：封面落点单点真源）。
    val columnCenter = remember { Animatable(0f) }
    // ★ H 轮（2026-11-07 按参考图重做）：车机档**没有封面态 / 控制条页**，左列
    //   「封面 + 控件」整块恒在最左、右列大字歌词常驻 ⇒ 恒不位移。
    //   平板双栏仍保留原「封面态玩家卡片居中」语义，逐像素不变。
    val columnCenterTarget =
        if (twoPane && !window.ultrawidePlayer && page == MeloXNowPlayingPage.Artwork) 1f else 0f
    // ⚠⚠ 转屏 / **进入横屏必须瞬时归位，绝不滑**：转屏本身已经是一整段 spring 主时间线，
    //   再叠一段「歌词右移 360ms」就会被读成「转横屏时先有一段歌词右移」
    //   （用户报障 2026-10-07：*常规页面转横屏会有一段歌词右移，正确应该是直接在中间*）。
    //   只有**横屏内的用户切页**（点歌词 / 队列）才走动画。
    LaunchedEffect(twoPane) { columnCenter.snapTo(columnCenterTarget) }
    LaunchedEffect(page) {
        columnCenter.animateTo(
            targetValue = columnCenterTarget,
            // 与场景层「天际内容淡出」同一条几何时长（设置项只影响几何，内容层吃 spring）。
            animationSpec = tween(meloXPlayerTweenDurationMillis(), easing = FastOutSlowInEasing),
        )
    }
    // ⚠⚠⚠ 必须**以 lambda 下传**，不能在 host 里读 `columnCenter.value`：它是每帧变化的状态，
    //   在外层读一次就等于每帧重组整个 host + 场景（本仓库铁律：每帧状态只能在叶子消费点读）。
    val columnCenterProgress: () -> Float = { columnCenter.value }
    var gestureCollapseProgress by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var seekJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val hostView = LocalView.current
    LaunchedEffect(isLandscape) {
        if (!isLandscape) showLandscapeSkyline = false else lyricsInterfaceHidden = false
    }
    LaunchedEffect(page) {
        onArtworkPageChanged(page == MeloXNowPlayingPage.Artwork)
    }
    // ★ 天际「此刻可呈现」= 有一枚可用的封面源（持久封面本体）。**唯一**判据见
    //   [meloXSkylineCanRender]：歌词页恒可、平板双栏的**封面态**也可（封面停在屏幕正中、
    //   天际开启期间不产生任何布局位移）、队列页不可。手机 / 竖屏恒等价于 `page == Lyrics`。
    val skylineVisible = isLandscape && showLandscapeSkyline && meloXSkylineCanRender(page, twoPane)
    // ★ 平板双栏里 ⋯「更多」→「天际歌词」的动作（2026-10-07）。
    //   队列页没有封面源 ⇒ 先把页面切到歌词页；**列心目标两页都是 0**（见上面的
    //   `columnCenterTarget`：只有封面态才是 1）⇒ 这次切页**不产生任何位移**，
    //   持久封面原地不动，morph 起点稳定。封面态则**故意不动 page** —— 让它直接从
    //   屏幕正中飞进天际，省掉一段会和 morph 抢位置的"左列滑回分栏"动画。
    val openLandscapeSkyline: () -> Unit = {
        showActions = false
        showQuality = false
        if (page == MeloXNowPlayingPage.Queue) {
            transitionSourcePage = page
            page = MeloXNowPlayingPage.Lyrics
            if (rememberPlayerPage) {
                MeloXSettingsPreferences.setString(context, "playback_last_page", MeloXNowPlayingPage.Lyrics.name)
            }
        }
        showLandscapeSkyline = true
    }
    DisposableEffect(
        MeloXSettingsRuntime.screenAwakeMode,
        MeloXSettingsRuntime.skylineKeepsScreenAwake,
        page,
        lyricsInterfaceHidden,
        skylineVisible,
    ) {
        val previous = hostView.keepScreenOn
        hostView.keepScreenOn = (skylineVisible && MeloXSettingsRuntime.skylineKeepsScreenAwake) || when (MeloXSettingsRuntime.screenAwakeMode) {
            MeloXScreenAwakeMode.Disabled -> false
            MeloXScreenAwakeMode.Player -> true
            MeloXScreenAwakeMode.Lyrics -> page == MeloXNowPlayingPage.Lyrics
            MeloXScreenAwakeMode.HiddenLyricsInterface ->
                page == MeloXNowPlayingPage.Lyrics && lyricsInterfaceHidden
        }
        onDispose { hostView.keepScreenOn = previous }
    }

    // Two distinct scenes avoid recursive glass sampling:
    // controls sample the flowing-light player scene; the actions overlay samples
    // the fully composed Now Playing scene from outside that recording layer.
    // ⚠ `playerControlBackdrop` 由 MeloXApp 层传入（见函数签名），本处只录制、不创建。
    val actionsBackdrop = rememberLayerBackdrop()

    // ⚠ 2026-10-02 回归上游原版：必须用 `meloXPlayerLinearFloatSpec()`（= FastOutSlowInEasing）。
    //   本地原先是 `MeloXIOSExpandEasing`（CubicBezier 0.32/0.72/0/1，decelerate：起步猛、
    //   然后一路慢飘）。它与迷你栏侧的 master 不是同一条曲线 ⇒ 两端不同拍。
    //   现在迷你栏文件已整体回归上游，两端统一为官方 FastOutSlowInEasing。
    // ⚠⚠⚠ 2026-10-06 修「轻微下拉松手后进度先反向」—— 用户报「播放页轻微下拉下沉
    //   （抽搐一下再往上）」。真机探针实测定量（`P` 行，一次「下拉一点松手」）：
    //     t=209836 p=0.942 （settle 事件 t=209846）
    //     t=209868 p=0.894  ← 开始**反向下降**
    //     t=209943 p=0.777  ← 最低（75ms 内向"关闭"方向跑了 16.5%）
    //     t=209958 p=0.788  ← 才回升
    //     t=210527 p=0.999  ← 584ms 后到位
    //   ⇒ 那记"先掉下去再上来"就是用户看到的下沉。
    //
    //   根因：`AnimatedVisibility` 的 `EnterExitState` 在回弹**头几帧**仍停在退场侧。
    //   字节码实证（`animation` 1.10.3 的 `AnimatedEnterExitImpl`）：它每帧会
    //   `Transition.getTargetState()` 与 `Transition.getCurrentState()` **各求值一次**
    //   `visible` lambda；而 `SeekableTransitionState` 在回弹全程 `currentState` 仍是
    //   收起侧（探针 `E settle+f0..5 cs=false ts=true` 连续 6 帧为证）⇒ 离散状态机在
    //   回弹头几帧给出"退场"⇒ 旧写法 `if (visibility == Visible) 1f else 0f` 的目标值
    //   瞬间变 **0f** ⇒ 进度先往关闭方向跑。
    //
    //   ⛔⛔ **v4（`fraction` 直算）已回滚（2026-10-06，用户报「一点击播放键就卡住」）**：
    //     当时的写法是 `MeloXPlayerAppleEasing(fraction)` 直接算，思路本身没错
    //     （`SeekableTransitionState.fraction` 确实是权威位置、单调无漂移），**但错在读取位置** ——
    //     `fraction` 必须在 `MeloXApp` 的**根组合体**里读才能当参数传进来，而它**每帧变化**
    //     ⇒ **整个 App**（底栏 / 列表 / 歌词 / 播放器）都订阅了转场动画的每一帧 ⇒
    //     一次状态变更（如点播放键）即进入"全树每帧重组" ⇒ **直接卡死**。
    //   ⛔ **铁律：严禁在任何"外层 / 根"composable 里读 `SeekableTransitionState.fraction`。**
    //     若将来仍要直算，必须用 `() -> Float` lambda 把读取**限制在 host 内部**（范围小、代价可测），
    //     绝不可以用值参数把它抬到根部。
    //   ⇒ 当前回到 v3 写法：`animateFloat` + `transitionTargetOpen`。
    // ⚠⚠⚠ 2026-10-11 修「打开瞬间迷你栏封面圆角变大 / 封面变小」——**实证根因**：
    //   原写法 `{ _ -> if (transitionTargetOpen) 1f else 0f }` 把 lambda 的**目标状态参数**
    //   丢掉了，而 `animateFloat` 的 **initialValue 也是用这同一个 lambda 求的**
    //   （Compose 取初值时把 `transition.currentState` 传进去）。
    //   打开时 [transitionTargetOpen] 早在内容组合**之前**就被 `openPlayer` 写成 `true`，
    //   于是 initial == target == 1f ⇒ 动画被判「已完成」⇒ **不参与 `SeekableTransitionState`
    //   的 seek** ⇒ 打开全程 `expansionProgress` 恒 1.0（探针实证：
    //   `P t=30981 p=1000`，其后 400ms 内纹丝不动，直到收起才开始降 998→983→…→45）。
    //   连带两处直接依赖它的判据同时失效：
    //     · `targetRadius = lerpDp(6.dp, 12.dp, expansionProgress)` ⇒ 打开第一帧就跳到
    //       12dp（迷你栏封面本该是 6dp）⇒ 用户看到的「圆角变大」；
    //     · `morphFreeze = expansionProgress in (0.01, 0.99)` ⇒ 判据永不成立 ⇒ 封面尺寸
    //       退化成 `lerpDp(full, 72.dp, pageFrameProgress)` ⇒ 用户看到的「封面变小」。
    //   ⇒ 改用 `Segment.targetState` 语义：打开时 `currentState` 仍是 PreEnter ⇒ 初值 0f、
    //     目标 1f ⇒ 动画真的被创建、真的跟随 seek；关闭时反之。
    //   ⛔ 为什么**不能**读 `currentState`（v2 的踩法）：回弹全程它停在 PostExit
    //     （探针 `E settle+f0..5 cs=false ts=true` 连续 6 帧）⇒ 目标恒 0f ⇒ 动画继续朝 0 跑
    //     ⇒ 那记「先掉 16.5% 再回升」的下沉抽搐。
    // ⚠⚠⚠ 2026-10-22 动画流畅度（**本文件最核心的一条纪律，改前必读**）：
    //
    //   ⛔ **不许用 `by` 解包**，也**不许在本 composable 体内读 `.value`**。
    //
    //   这里是 1853 行的 SharedHost 顶层。`animateFloat` 返回的是 `State<Float>`，
    //   一旦 `by` 解包并在组合体里读，本 restartable scope 就**每帧失效** ⇒
    //   整棵播放页（外壳 / 场景 / 歌词 / 队列 / 封面）在转场 360ms × 90Hz ≈ **32 帧里
    //   全部重组 32 次**，每帧还要重跑 measure/layout。这就是「打开播放页偶尔卡一下」
    //   的结构性根因 —— 不是曲线问题，也不是时长问题。
    //
    //   ⇒ 三类读法，各走各的路（与仓库里的铁律「禁根 composable 读
    //     `SeekableTransitionState.fraction` 下传」同源，这里只是当年没一起改掉）：
    //     · **视觉量**（alpha/圆角/scale）：在 `graphicsLayer{}` / `drawBehind{}` 里读
    //       `expansionProgressState.value` ⇒ 只失效重绘，**0 次重组**
    //     · **行为判据**（是否过渡中 / 是否播放 / 是否落定）：`derivedStateOf` 收成
    //       离散 State —— 结果只在**阈值穿越**时通知，转场全程只变 2~3 次
    //     · **真需要组合期标量的**：只在叶子组件里读，绝不在这个作用域里读
    //
    //   ⚠ 别担心 `derivedStateOf` 有延迟：它是**同步重算**的，读 `.value` 拿到的永远是
    //     当前值；只是「结果没变就不通知读取者」。所以 `transitionInFlight` 这种判据
    //     既不会晚一帧，也不会每帧重组。
    val expansionProgressState = animatedVisibilityScope.transition.animateFloat(
        // 🔴 2026-10-28 16:5x：**必须传方向**，理由同 `MeloXIOSMiniPlayer` 的同一处
        //   —— 关闭方向外壳弹簧慢 10%，内容层不同源会差 1.4%（≈41px）。
        transitionSpec = {
            meloXPlayerLinearFloatSpec()
        },
        label = "full-player-expansion-progress",
    ) { visibility ->
        if (visibility == EnterExitState.Visible) 1f else 0f
    }
    // Let the shared artwork establish the transition first. The background
    // and scene then take over on the same master timeline, which prevents a
    // black first frame from winning the z-order over MiniPlay.
    // ⚠ 2026-10-02 回归上游常量 `1f`（用户报障：本地打开时「迷你栏直接消失、播放页
    //   背景很突兀」，关闭时「渐隐一路降到完全消失」；官方两者都不会）。
    //   本地原先是 `1f - smoothStep(1 - expansionProgress, 0.58f, 1.0f)`，只想在收起方向
    //   末段淡出；但它成立的前提是 iOS 曲线（p 起步就冲高 ⇒ 打开方向恒为 1）。换成官方
    //   FastOutSlowInEasing 后前提失效，实测**打开方向前 120ms backdropAlpha 恒为 0**
    //   ⇒ 播放页背景全黑、之后突然亮起；关闭方向会一路降到 0 = 背景完全消失。
    //   官方注释已说明：背板必须从第一帧合成，延迟 alpha 会让共享容器在展开前半程显示黑面。
    // 🔴🔴 2026-11-02 最终定案：回到恒 `1f`，**任何方向的进度淡出都不再允许**。
    //   中途曾改成 `smoothStep(p, 0.02→0.42)`、又压到 `0.00→0.14`，两次都被用户否决
    //   （「渐隐的效果很差」「这种显隐对观感的牺牲太大了」）—— 那是在用整页观感去补
    //   一个**背板对象不统一**的缺陷。现在跳色改在源头解决：
    //   `MeloXIOSMiniPlayer(glassBackdrop = bottomChromeBackdrop)`，overlay 副本与稳态
    //   节点采同一个 Backdrop 对象 ⇒ 「换源」事件不存在 ⇒ 本层无需渐变。
    //   完整推导见下方 `backdropAlpha` 的 kdoc。
    // ── 视觉量：只做成「绘制期求值的 provider」，组合期一个数都不算 ──────────────
    //   `isolationEnabled` 是设置项（只在用户改设置时变），留在组合期读没问题；
    //   进度的部分一律进 lambda（原实现 `smoothStep(expansionProgress, …)` 写在组合体里，
    //   是整树每帧重组的直接来源之一）。
    val isolationEnabled = MeloXSettingsRuntime.playerBackgroundIsolationEnabled
    val isolationAlphaProvider = remember(isolationEnabled, expansionProgressState) {
        { if (isolationEnabled) smoothStep(expansionProgressState.value, 0.34f, 0.82f) else 0f }
    }
    // ★★ 2026-10-31 修「收回后半段，黑体/流光与迷你栏不够贴合、沿轮廓露出一圈」。
    //   只动**兜底黑这一层**，`backdropAlpha` 恒 1f 绝不动 ⇒ 流光/封面/背景观感一点不损。
    //   根因：本层兜底黑原本 `drawRect(Color.Black, alpha = 1f)` **无条件全程铺满**。外壳
    //   fillMaxSize + clip(shellShape) 在 morph 期压到迷你胶囊附近时，这层不透明黑仍以
    //   alpha=1 怼在胶囊轮廓**外侧**（跟随外壳矩形容差、不与胶囊贴死）⇒ 收回后半段
    //   胶囊四周露出一圈黑色兜底 = 用户看到的「黑体不贴合」。**播放中**则是同一层黑
    //   铺在流光之下；由于流光自己随 `shellExpandedEnough`（>0.72f）门控，收回早期就关了，
    //   于是露出的主要是那层黑，颜色判别“暂停黑 / 播放稍淡”都源自它。
    //   上游 `.dev.up`（从不闪）只在隔离窗口画黑 `if (isolationAlpha > 0f)
    //   background(Black.copy(alpha = isolationAlpha))`，`isolationAlpha = smoothStep(p, 0.34→0.82)`
    //   ⇒ collapse 尾部 (p<0.34) 已完全消失，胶囊四周没有不透明黑。
    //   ⇒ 对齐上游：兜底黑改用**同一条**隔离曲线 `smoothStep(p, 0.34→0.82)` 沿收回方向淡出。
    //   不强依赖 `isolationEnabled` 设置（它在用户关隔离时恒 0）：兜底黑的职责是「展开/旋转期
    //   盖住首页」，天然只该在展开态不透明；收回后半段 p<0.34 淡没正合其语义，且只有这层。
    //   `drawRect` 本身无条件保留（alpha=0 空操作，零成本）。
    //   ⛔ 别把它跟 v12 那次错误混同：v12 是连 `backdropAlpha`（流光/封面）一起淡掉，把背景
    //   砍没、观感废了；本改**只**淡兜底黑，正是上游结构，二者绝不同等。
    // ★ 真正的 lambda 见下方 `hostExitingState` 声明之后：黑兜底淡出**只门控在收起方向**。
    //   此处不提前声明，是因为它需要读 `closingIntent` 的 live State，而该 State 在下方才建立。
    // 手势 / 嵌套滚动要读「收起进度」，但读点全在 suspend 或手势回调里（**不在组合期**）
    // ⇒ 直接持有 derivedState，读 `.value` 不订阅重组。
    val latestCollapseProgress = remember(expansionProgressState) {
        derivedStateOf { (1f - expansionProgressState.value).coerceIn(0f, 1f) }
    }
    val latestPage = rememberUpdatedState(page)
    DisposableEffect(Unit) {
        onDispose {
            seekJob?.cancel()
            settleJob?.cancel()
        }
    }
    // 圆角走 [meloXPlayerShellCornerDp]（唯一曲线）：迷你端 24dp → 涨到**系统圆角**
    // （峰值由 rememberMeloXDisplayCornerPx() 取真值，与后方页面**同一个数**），
    // 然后等主时间线**完全跑完**才坐平成直角。必须由同一条 master 时间线驱动，
    // 手势 seekTo 时才不会与外壳 morph 错拍。
    val shellPeakDp = with(LocalDensity.current) { rememberMeloXDisplayCornerPx().toDp().value }
    // 外壳圆角形状的**实例缓存**（按半径命中，见 `MeloXSlideShapeCache` 的长注释）。
    // ⚠ 只在绘制期（`graphicsLayer` 的 lambda）里用，故意不是快照状态。
    val shellShapeCache = remember { MeloXSlideShapeCache() }
    // ⚠⚠ 2026-10-03 修「圆角变直的时机太前了，还处于动画尾端就变了」（用户报障）。
    //   旧实现把坐平挂在 `meloXPlayerShellCornerDp` 的 `p∈[0.99,1.0]` 窗口上，但 `p` 是
    //   FastOutSlowInEasing **缓动之后**的值：`p=0.99` 落在时间轴 **0.95T ≈ 342ms**
    //   （全程 360ms）⇒ 动画还差 ~18ms 收尾，圆角就已开始坐平 = 用户看到的「尾端就变」。
    //
    // ⚠⚠⚠ 2026-10-03 第二次改（用户报障：「在某些界面，比如歌单，应该表现为直角（没有任何
    //   动画进程），但是异常表现为了屏幕圆角」）：坐平判据改成**只读外壳自己的主时间线终值**。
    //   `expansionProgress` 的 tween 只有在**它自己跑完那一刻**才会等于 `1f`（缓动之后的值在
    //   t=0.999 时 ≈0.99999，不会提前触顶）⇒「动画完全结束才坐平」由构造保证 —— 这正是用户
    //   那句「应该是动画完全结束才变」的字面定义，比任何间接信号都直接。
    //   ⚠ 真机读数（临时 debug 覆盖层，四条进入路径实测）：`p=1.0 flat=1.0 r=0dp
    //     cs=Vis ts=Vis seek=false` —— 点迷你栏 / 手势 seek+settle / 冷启 intent（含从亮色
    //     歌单页打开）全都落在这上面。所以这一条**不是**在修「坐平不触发」，只是把判据钉在
    //     最直白的定义上（原先用 transition 状态机是间接的，还要依赖 `SeekableTransitionState`
    //     的 seek 语义）。⚠ 该读数已删除，别再靠它验收。
    //   「亮色歌单页四角发黑」的真正来源是**页面级裁剪**（`meloXPageSlideGeometry` 的
    //     `clip` 恒开），已在那里修掉，见该文件。
    //   再叠一个 140ms 的平滑量只为避免末帧硬跳：动画期间 `flattenAlpha` 恒 0 ⇒ 圆角
    //   严格等于旧表达式（**不违反**「直接用 p、不做重映射/系数」那条跟手约定）。
    // ⚠⚠⚠ 2026-10-03 第三次改：判据从 `expansionProgress >= 1f` 换成**过渡状态机**。
    //   起因：主曲线为了iOS 丝滑改成了**带过冲**的 `(0.25, 1.15, 0.75, 1)`
    //   （见 `MeloXPlayerAppleEasing`），而 `p >= 1f` 这个判据问的是「p 到 1 了吗」——
    //   过冲曲线在 `u ≈ 0.71`（墙钟约 189ms / 267ms）就**向上穿过 1.0**，
    //   判据提前为真 ⇒ 圆角提前 **77ms** 开始坐平（B 档更早 120ms）。
    //   那些像素变化已经回来了 ⇒ 圆角提前坐平 = 用户报障过的「尾端就变」。
    //
    //   ⇒ 改问「**过渡跑完了吗**」：`currentState == targetState == Visible`。
    //   这个判据与 `p` 的数值、easing 形状、过冲幅度**完全无关** ⇒ 换任何曲线都稳。
    //
    // ⚠ 讽刺的是这个判据**曾经用过**，后被换成 `p >= 1f`，当时理由是「它间接、
    //   依赖 `SeekableTransitionState` 的 seek 语义」。现在教训正好反过来：
    //   `p >= 1f` 恰恰是**依赖 easing 数值**的那个 —— 今天换带过冲的曲线它就崩。
    //   ⇒ 判据应当依赖**动画是否结束**这个事实，而不是**进度条的数值**。
    //
    // ⚠⚠ **代价：坐平触发点从 `u≈0.55` 变成 `u=1.0`，四条进入路径必须重跑真机验证**
    //   （点迷你栏 / 手势 seek+settle / 手势塌陷后重开 / 冷启 `ACTION_OPEN_NOW_PLAYING`）。
    //   `Transition.isSeeking` 是**属性**不是函数（写 `isSeeking()` 编译不过）。
    // ⚠⚠⚠ 2026-10-26 修用户报障「打开动画圆角有时丢失 / 有时还没完全贴合就变成直角」——
    //   **真机逐帧记录**（8 次开合）：
    //     · 内容层 `p`（`expansionProgress`）到 1000 在 t=3553；
    //     · **同一时刻** bounds 几何线性进度只有 **937/1000**（≈99.4% 位置，外壳还差 ~18px）；
    //     · `transition.isRunning` 直到 t=3942 才为 false。
    //   ⇒ 旧判据 `p >= 1f` 在几何还差十几 px 时就打开坐平闸门，140ms 后圆角已是直角
    //     （比几何真正贴合早 ~190ms）= 用户看到的那一帧。
    //   根因：master fraction 走的是 **spring**（`playerOpenFractionSpec()` →
    //     `playerSettleMasterSpec()`，ζ=1、stiffness 由设置时长反解），而几何 bounds 走
    //     `tween(360, linear)` + `MeloXPlayerAppleEasing` ⇒ **两条时间线不同源**：
    //     spring 数值 ~320ms 就到顶，tween 要 ~685ms 才到 998。
    //   ⇒ 坐平必须等 **transition 彻底静止**（几何动画也结束），不能只看 `p`。
    //   ⚠ 不退回 `currentState == targetState` 单判据：2026-10-21 曾因 seek 中途漏触发
    //     （用户报「圆角不变直」），故保留「主时间线已跑完」作为附加必要条件，两者同时成立才坐平。
    //   ⚠ `p` 用 `>= 0.999f` 而不是 `>= 1f`：它经过 AppleEasing 归一化，浮点上不保证精确 1.0，
    //     严格比较会让坐平永不触发（正是 10-21 那个 bug 的形态）。
    val transitionTargetState = animatedVisibilityScope.transition.targetState
    val shellSettledByState by remember(transitionTargetState, expansionProgressState) {
        derivedStateOf {
            transitionTargetState == EnterExitState.Visible &&
                expansionProgressState.value >= 0.999f &&
                !animatedVisibilityScope.transition.isRunning
        }
    }
    // ⚠⚠⚠ 2026-10-06：坐平 / **取消坐平**——方向分离，且取消坐平是**瞬时**的。
    //   用户口径（两次澄清后）：
    //   · 「铺满动画**完整结束**后**马上**变直角」 ⇒ 打开方向 = `shellSettledOpen` 触发，
    //     仍保留 140ms 的平滑收口（避免末帧硬跳）。
    //   · 「返回动画是**直角从动画开始瞬间就变屏幕圆角**，**而不是有变化过程**」
    //     ⇒ 关闭/拖拽方向必须 **`snapTo`（瞬时）**，不能给任何渐变时长。
    //   ⚠ 上一版给的是 40ms 渐变（为了让峰值出现），**仍然是"变化过程"**，被否。
    //   ⚠ 机制上为什么瞬时是对的：`cornerRadius = shellCornerDp(expansionProgress) × (1 − flattenAlpha)`，
    //     关闭**第一帧** `expansionProgress` 仍是 1（= 系统圆角档）⇒ 只要立刻把 `flattenAlpha`
    //     归 0，圆角**当帧就是系统圆角峰值**，随后随收缩自然落到迷你栏的 24dp
    //     ⇒ 正是用户要的「直角 → 瞬间系统圆角 → 变小」。
    //     反之若给 40~140ms 渐变，等 `flattenAlpha` 放开时 `expansionProgress` 已掉到 0.14~0.5
    //     （< RiseStart 0.50）⇒ 圆角直接落到 24dp，**峰值从未出现**（用户最初报的 bug）。
    val flattenAnim = remember { Animatable(if (shellSettledByState) 1f else 0f) }
    LaunchedEffect(shellSettledByState) {
        if (shellSettledByState) {
            flattenAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = MeloXPlayerCornerFlattenMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            flattenAnim.snapTo(0f)
        }
    }
    // ⚠⚠ 2026-10-22：`cornerRadius` / `shellShape` **不再在组合期求值**。
    //
    //   旧实现是 `val cornerRadius = (shellCornerDp(expansionProgress) * (1 - flattenAlpha)).dp`
    //   + `MeloSmoothCornerShape(cornerRadius.toPx())`，然后 `.clip(shellShape)` ——
    //   这两行让**整个 1853 行的宿主**每帧重组（圆角还叠了 `flattenAnim` 这条 140ms 动画，
    //   即转场结束后还要再重组 12 帧）。
    //
    //   现在整条链挪到消费点的 `graphicsLayer { clip = true; shape = … }` 里（见下方外壳 Box）：
    //   `GraphicsLayerScope` 本身就是 `Density`，`.dp.toPx()` 直接用；`shape` 每帧赋**新实例**
    //   ⇒ RenderNode 的 clipPath 每帧照常更新（⛔ 别改成"身份恒定的 Shape"：
    //   `RenderNodeLayer` 是比对 shape 相等性来决定要不要重设 clipPath 的，
    //   同一实例会被判「没变」⇒ 圆角会真的停在第一帧不动）。
    //   ⛔ 也不许把 `flattenAnim.value` 提到组合期 —— 它同样每帧在变。
    // ★ 关闭尾段「下沉 → 回弹」由**共享元素 bounds 几何**提供（见
    //   `meloXPlayerSharedBoundsTransform`）：host 侧与迷你栏侧**必须用同一个
    //   `sinkPeakPx`**，两个 overlay 才会沉同样的量、封面不错位。
    val sinkPeakPx = with(LocalDensity.current) { MeloXPlayerSinkPeakDp.dp.toPx() }
    // 关闭方向判定（本次推进**确定**走向收起）。外壳 / 封面 / 迷你栏三处必须逐字同义。
    // ⚠⚠⚠ 2026-10-06：判据**只认 `closingIntent`**，不再用
    //   `transition.targetState == PostExit` 兜底 —— 手势**拖拽**期间
    //   `MeloXApp.onSeekCollapse` 会 `seekTo(targetState = false)`，`targetState`
    //   立刻变 `PostExit`，但**那并不代表本次一定会走到完全收起**（松手可能回弹）。
    //   用它兜底 ⇒ 手指一拖过 f=0.62 就提前把下沉施加出来，松手回弹时下沉被瞬间撤销
    //   ⇒ 用户报障「下拉一点松手后，播放器会下沉抽搐一下再往上」「半程取消还会下沉」。
    //   而 `closingIntent` 的语义正是「本次推进确定走向收起」，由 `MeloXApp` 在
    //   **全部 5 个主状态变更点**同步写入（openPlayer=false / closePlayer=true /
    //   intent 打开=false / onSeekCollapse=false / onSettleCollapse=collapse）。
    // ⚠ 方向以 **lambda** 传入、`remember` 的 key **不含方向**（见 `meloXPlayerSharedBoundsTransform`
    //   的说明）：否则手势越阈值时实例被重建、回弹丢失。
    val isHostExiting = closingIntent
    val hostExitingState = rememberUpdatedState(isHostExiting)
    // ★★ 2026-10-31（接 v13 后续）：黑兜底淡出**只在「收起方向」生效**，展开/已展开方向恒不透明。
    //   背景：v13 把 `drawRect(Black)` 的 alpha 改成双向 `smoothStep(p, 0.34→0.82)`——
    //     收起方向确实让黑在 p<0.34 淡没、不再沿迷你胶囊轮廓露一圈（修原 bug）；
    //     但**打开方向**也跟着被这条曲线削了：p∈(0.34,0.82) 期间黑以中段 alpha 提前半透，
    //     首页从外壳圆角处漏进来，被读成「展开态多余的弧线闪烁」（用户 v13 后新报障）。
    //   ⇒ 展开/已展开方向恒 = 不透明黑（正是 v13 之前、也对齐上游 `.dev.up`「盖住首页防漏」语义），
    //     只让**收起方向**沿用隔离曲线淡出。一次只动这一处，黑兜底的职责不变、流光/封面不受影响。
    //   `hostExitingState` 是 `closingIntent` 的 live State（每次组合更新 `.value`），在 draw 期读取即
    //   当前方向，且不引入重组；lambda 用 `remember(expansionProgressState, hostExitingState)` 持有引用，
    //   读 `.value` 永远取最新方向。
    // ★★★ 2026-11-02（第二次）：上界 0.82 → **0.44**，修「四个角都有动态背景颜色的弧线在闪」。
    //
    //   真因是**两层同时变透明的空窗**，不是形状不匹配：
    //     · 迷你玻璃 `alpha = 1 - smoothStep(p, 0.44, 0.52)`（见 `miniSurfaceAlphaProvider`）
    //     · 本层黑兜底 `alpha = smoothStep(p, 0.34, 0.82)`
    //   原窗口下 p=0.44 时：玻璃 ≈1.0（好）但黑兜底只有 `smoothStep(0.44,0.34,0.82)≈0.15`；
    //   p=0.48 玻璃 ≈0.5、黑兜底 ≈0.23 ⇒ 合成不透明度 ≈0.62，剩下 38% 是**主页动态背景**。
    //   玻璃轮廓的四角曲率最大，透出来的背景在那里最扎眼 ⇒ 用户报的
    //   「四个角都有动态背景颜色的弧线在圆角那闪」。
    //   （这也解释了为什么它从"两个底角"变成"四个角"：以前只有下沉段两底角错位，
    //     现在是整条轮廓一圈都在漏。）
    //
    //   ⇒ 把上界压到 0.44：`p ≥ 0.44` 时黑兜底**恒不透明**，正好完整覆盖玻璃的淡出区间；
    //     `p < 0.44` 时玻璃 alpha 恒 1（不淡出）⇒ 任意时刻至少有一层完全不透明，
    //     结构上不存在"背景透出来"的时刻。
    //   ⚠ 零观感代价：`p ≥ 0.44` 这一段外壳内部被彩色背景层（`backdropAlpha` 恒 1）
    //     完整盖住，黑兜底透不透明都看不见；而"不再沿迷你胶囊轮廓露黑"的原始目的
    //     只需要 `p < 0.44` 时黑=0（玻璃此时不透明地盖着它），语义不变。
    // ★★★ 2026-11-03（第十九轮）：**淡出窗口从 (0.34,0.44) 拉宽到 (0.02,0.34)**。
    //
    //   现场（正常速度退出录屏 `_exit/v18.mp4`，1080x2376，逐帧像素实证）：
    //     · 帧 8 → 帧 9：屏幕上部采样点 (540,300) **一帧之内**从 `0,0,0`（播放页纯黑）
    //       跳到 `199,198,201`（主界面白）⇒ 整块外壳在同一帧内变透明；
    //     · 整屏均值 34.9 → 81.9 → 97.9 → **178.3**，其中 97.9→178.3 这一跳（+80，
    //       同段其它帧只有 +6~8）就是用户报的「从播放页退出时闪一下」。
    //   真因：本层是 fork 2026-10-31 为「收起后半段黑体不贴迷你胶囊」加的黑兜底，
    //   但窗口只有 `p ∈ [0.34, 0.44]`（10% 进度）。退出时 p 从 1 掉到 0，这段在缓动
    //   尾部只占 1~2 帧 ⇒ 黑 alpha 在 1~2 帧内从 1 掉到 0，而外壳此刻仍是大矩形
    //   （播放页内容还在原地）⇒ 整块面板一帧内变透明、露出主界面 = 「闪」。
    //   ⇒ 下界压到 0.02、上界压到 0.34：不透明段延长到 p=0.34（外壳已明显收缩），
    //     淡出段拉宽到 32% 进度（≈10 帧@60fps），任意一帧都不会「整块突透明」。
    //   ⚠ 下界 0.02 与迷你玻璃 `1 - smoothStep(p, 0.02, 0.48)` 的下界**同值**：
    //     p<0.02 时玻璃 alpha 已 =1，黑正好在该点消失，交接不留空窗。
    //
    // ★★★ 2026-11-03 02:0x：**保持 (0.02, 0.34)，不要再往前推**。
    //   本轮试过 (0.50, 0.78)，方向错：那一区间外壳还是全屏，黑兜底一淡，暂停态整屏会
    //   从纯黑变成模糊封面 ⇒ 关闭前段「中途变亮」。
    //   「四角露播放页底色」已由外壳末段让位（`MeloXShellGeom.tailAlpha` + 内容层
    //   `graphicsLayer`）从根上解决：暂停黑与播放彩色一视同仁，那圈镶边整体让给迷你栏。
    val blackFallbackAlphaProvider = remember(expansionProgressState, hostExitingState) {
        {
            if (hostExitingState.value) {
                smoothStep(expansionProgressState.value, 0.02f, 0.34f)
            } else {
                1f
            }
        }
    }
    /**
     * **播放页彩色背景层（流光 / 模糊封面 / 歌词封面）的 alpha** —— 恒 `1f`。
     *
     * 🔴🔴 2026-11-02 最终定案：**不再按进度淡出**。曾经为了掩盖「迷你栏玻璃换源跳色」
     *   把它做成 `smoothStep(p, 0.02, 0.42)`（后来压到 `0.00 → 0.14`），用户两次否决：
     *   「渐隐的效果很差」「这种显隐对观感的牺牲太大了」。
     *
     *   ### 为什么淡出是错的方向
     *   换色的真正原因是**采样对象不是同一个 Backdrop**：
     *   · 稳态迷你栏在底栏子树里，`MeloXApp` 把 `LocalMeloXBackdrop` 显式 provide 成
     *     `bottomChromeBackdrop`（录的是主页 recede 页）；
     *   · 转场期迷你栏那一份被渲进**共享元素 overlay**（`SharedTransitionLayout` 下的
     *     独立 subcomposition）⇒ `LocalMeloXBackdrop` 退回外层 `pageBackdrop`
     *     ⇒ 转场一结束就换源，实测一帧内 ΔR≈86。
     *   ⇒ 拿「整页背景按进度变透明」去补一个**对象不统一**的缺陷，代价是整段收起的观感，
     *     属于用错药。
     *
     *   ### 现在的解
     *   `MeloXIOSMiniPlayer(glassBackdrop = bottomChromeBackdrop)` 直接把稳态那份背板喂进
     *   玻璃材质（见 `meloXLiquidBottomBar(backdropOverride = …)`）：overlay 副本与稳态
     *   节点是同一次 composable 调用，参数天然一致 ⇒ 逐帧采同一份内容，
     *   **「换源」这个事件在结构上不存在**，所以本层回到恒 `1f`、画面与从未改过时逐像素一致。
     *
     *   ⛔ 别再把它改成任何形式的进度淡出 —— 也不会再需要：跳色已在上游（背板对象）解决。
     *   ⛔ 也别删掉本字段：`.graphicsLayer { alpha = backdropAlpha }` 的离屏合成是既有
     *     绘制结构的一部分，改动会牵动背景层的混合结果。
     */
    val backdropAlpha = 1f
    val sinkBoundsTransform = remember(sinkPeakPx) {
        // ★ 2026-10-29 修复：宿主侧的外壳 morph（另一端是迷你栏）。与迷你栏侧一起构成
        //   「外壳」这一对 morph ⇒ **必须显式**发布每帧 bounds 给底部 chrome 覆盖判据
        //   （`MeloXShellGeom.record` 曾被漏掉 ⇒ 整条遮挡门控失效 ⇒ 18.25MB/帧白付）。
        //   ⚠ 下面（`artworkSinkPeakPx` 那处）的**封面** morph 绝不能被当成外壳，保持默认 false。
        meloXPlayerSharedBoundsTransform(
            sinkPeakPx = sinkPeakPx,
            publishShellGeom = true,
            // ★ 2026-10-28 22:0x：抗首帧竞态。`closingIntent` 在 sharedBounds 首帧可能
            //   未翻（实测 p 走 k=8.70）；改用 AnimatedVisibility `targetState` 兜底：关闭时
            //   全屏播放页**退出** ⇒ `PostExit`，首帧必已置位。与迷你栏侧 `== Visible` 同义。
            sinkEnabled = { hostExitingState.value },
        )
    }
    val sharedShellModifier = with(sharedTransitionScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = sharedPlayerShellKey()),
            animatedVisibilityScope = animatedVisibilityScope,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            boundsTransform = sinkBoundsTransform,
            // 🔴🔴 2026-10-29 08:3x：回退 `ScaleToBounds` → 仍用 `RemeasureToBounds`。
            //   `ScaleToBounds(Crop)` 虽省去每帧重测，但它是「满屏布局整块矩阵缩放」——
            //   圆角半径同比缩到≈0（打开/回收的迷你态看到直角）、满屏深色背景被裁成小方块
            //   （迷你栏四周直角黑框）。这是矩阵缩放圆角的固有缺陷，无法在外层简单补 clip 修复。
            //   ⇒ 回到 0.7.0 的 `RemeasureToBounds`：每帧按当前插值尺寸重排、圆角按当前尺寸重裁，
            //      圆角永远正确。后半段掉帧改从「减少每帧重排开销」方向另治（见下方内容 alpha /
            //      graphicsLayer 每帧 lambda，以及后续 on-device gfxinfo 量化），不再动 resizeMode。
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }

    // NowPlaying owns the player-level Back handler. Child modal overlays are
    // composed later and temporarily disable this handler, so Back always unwinds
    // the topmost visual layer before the player itself is dismissed.
    BackHandler(enabled = !showActions && !showQuality) {
        if (showLandscapeSkyline) {
            showLandscapeSkyline = false
            return@BackHandler
        }
        // ★ 2026-10-25：转屏形变未结束先不退出（见 `orientationBusy`）。
        if (orientTransition.isRunning) return@BackHandler
        onDismiss()
    }

    // ★ 过渡期交互拦截（2026-10-02 建，2026-10-03 修 pass）——用户报障「动画未消失时
    //   还能操纵到播放页的组件（如暂停、音量）」：`expansionProgress` 落在两端之外时说明
    //   展开/收起动画还在跑。此时场景已半可见地铺在屏幕上，音量条/进度条/按钮却全都已可点，
    //   且点到的是**正在位移中**的控件。
    //
    //   ⚠⚠⚠ **必须用 `PointerEventPass.Initial`（ROOT→LEAF）**：Compose 的默认
    //     `Main` pass 是 **LEAF→ROOT**（子节点先于父节点）⇒ 挂在根 Box 上的拦截器
    //     **总是晚于子按钮的 `clickable` 一步**，吞不掉 —— 旧实现就是因此**形同虚设**，
    //     按钮照样能点。`Initial` pass 方向相反（根先于子），在根处 `consume()` 后
    //     子节点根本收不到这次按下的手势 ⇒ 真正拦住。
    //   只在**按下那一刻**判断：不在过渡期就完全不碰事件 ⇒ 下拉收起的 down 发生在
    //   播放页全展开时（`transitionInFlight=false`），拖拽通道(NestedScroll/draggable)
    //   照常工作，不受影响。
    //   ⚠⚠ 下界**必须去掉**（2026-10-03 用户报障「缩小成迷你栏的**瞬间**点击，音量还是
    //     受影响」）：改前是 `p > 0.001f && p < 0.999f`，而 `expansionProgress` 是**缓动后**
    //     的值，收起尾段它很快就掉到 `0.001` 以下 ⇒ `transitionInFlight` 会在**播放页还没
    //     真正消失**时就翻回 false ⇒ 音量条 / 按钮又变可点（那就是「瞬间」的残留）。
    //     ⇒ 改为「**只要没完全展开就算过渡中**」（稳态展开 p=1 ⇒ false，一切照旧）。
    // ⚠ 2026-10-22：语义一字未改（仍是「只要没完全展开就算过渡中」，判据与上方那段
    //   「下界必须去掉」的血泪注释完全一致），只是**收成离散 State**。
    //   原来在组合体里读 `expansionProgress` ⇒ 这个 Boolean 每帧被重算、顺带把整棵树拖着重组成 32 次。
    //   现在 Boolean 只在 `0.999` 穿越时翻一次 ⇒ 转场全程重组 1 次。
    // ★ 2026-10-23：保留 State **本体**（`transitionInFlightState`），另解包一个 `by` 版本。
    //   本体要下传给 Scene → 歌词面板，让面板在自己的内部读 `.value`；
    //   中间层（Scene）**只转发引用、不读值** ⇒ 不会因此多重组一次。
    //   用途：开/闭播放页的 morph 期间把歌词逐行模糊降到 0（见 `MeloXIOSLyricsPanel`
    //   的 `shellTransitioning` 注释——真机实测模糊是歌词页 jank 的第一大头）。
    val transitionInFlightRaw = remember(expansionProgressState) {
        derivedStateOf { expansionProgressState.value < 0.999f }
    }
    // ★ 2026-11-06：**正式生效的优化**（真机 A/B 验证，见 SPEC-open-jank-2026-11-06.md §5.5）。
    //   实测依据（真机 atrace）：`p` 跨过 0.999 的那一帧，`transitionInFlight` 由 true 翻 false，
    //   `LocalMeloXPlayerControlsEnabled` 随之由 false 翻 true ⇒ 整棵场景控件树重组 + display list
    //   重录（`Record View#draw()` 12.78ms），后续几帧主线程阻塞在 `postAndWait`，掉 5–6 帧 ——
    //   这正是「展开动画最后一顿」。把翻转推迟 120ms（画面已静止），同样的掉帧不再可感知。
    //   ⚠ 语义完全不变：false→true（开始过渡）**立即**生效，只有 true→false 被推迟，
    //     所以收起方向、手势拦截、控件禁用都比原来更保守，不会漏拦。
    val transitionInFlightState =
        if (MELOX_SETTLE_HOLD_MS == 0L) transitionInFlightRaw
        else rememberDelayedFalse(transitionInFlightRaw, MELOX_SETTLE_HOLD_MS)
    val transitionInFlight by transitionInFlightState
    // ★ 2026-10-24 修「塌缩时控件飞向右下角」：播放页**正在关闭**（朝 Hidden 收拢且未落定）
    //   这一段时间，强制把横竖屏过渡视为已落定（见 Scene 里 `closingPlayer`）。
    //   否则塌缩（expansionProgress 1→0，可用尺寸在缩）与 orientTransition 的横竖屏交叉
    //   淡变同框跑：np-controls 在「竖屏底栏矩形」与「横屏左列矩形」之间插值，而被插值的
    //   约束正跟着塌缩在缩 ⇒ 拿到错误目标矩形 ⇒ 控件被甩到右下角、底栏留空（同时漏首页）。
    //   判定精确限定在「关闭方向」（`targetState == Hidden`），打开方向绝不受影响。
    // ★ 2026-10-25：改用**最早**的收起信号 `transitionTargetOpen`（MeloXApp 在 animateTo /
    //   seekTo **之前**同步写）。旧判据 `targetState == PostExit && expansionProgress < 0.999`
    //   在收起**第一帧**（p 还没从 1.0 掉下来）为 false ⇒ 那一帧 Scene 仍按 90ms `fadeOut`
    //   起转场，横竖两套布局同时活着 ⇒ `np-controls` 配对到旧朝向矩形 ⇒ 控件往右下角飞。
    val closingPlayerState = remember(transitionTargetOpen) {
        mutableStateOf(!transitionTargetOpen)
    }
    // ★ 2026-10-22：把「外壳正在动」写进运行期总闸，供两处**不在本函数里**的付钱方读取：
    //   ① 流光背景网格（同文件下方 `MeloXFlowingLightBackdrop`）—— 运动期降频；
    //   ② 回前台管线预热（MeloXApp 根）—— 运动期不挂预热块，避免和动画抢 RenderThread。
    //   只写布尔（首尾各一次），不读 `expansionProgress` 的任何连续值。
    //   ⚠ 播放页收起 / 转屏时本函数可能整棵被移出组合，协程不会跑完 ⇒ 必须显式复位，
    //     否则背景会永久停在降频档、预热永久不跑（见 `MeloXUiMotionRuntime` 第 3 条约束）。
    LaunchedEffect(transitionInFlight) {
        if (transitionInFlight) {
            MeloXUiMotionRuntime.shellTransition = true
        } else {
            MeloXUiMotionRuntime.shellTransition = false
        }
    }
    DisposableEffect(Unit) {
        onDispose { MeloXUiMotionRuntime.shellTransition = false }
    }
    // ⚠ 2026-10-22：另外两条**阈值穿越型**判据，同样收成离散 State。
    //   它们原本是在组合体里对 `expansionProgress` 直接比较 ⇒ 每帧重算 + 每帧重组
    //   （`isPlaying` 还会一路下传进背景/歌词，波及面很大）。
    //   语义一字未改：> 0.72f / >= 0.98f 与原文完全相同。
    val shellExpandedEnough by remember(expansionProgressState) {
        derivedStateOf { expansionProgressState.value > 0.72f }
    }
    val shellFullyExpanded by remember(expansionProgressState) {
        derivedStateOf { expansionProgressState.value >= 0.98f }
    }
    // ⚠ 2026-10-22：下传给 `SharedArtworkDestination` 的必须是 **lambda**，不能是解包后的 Float。
    //   在这里（SharedHost 的 scope）读 `.value` 会让整棵宿主重新变成每帧重组 ——
    //   前面所有下沉白费。改为把「怎么读」传下去，让封面那个**自己的** scope 去读
    //   （它本来就因为 `orientTransition` 每帧重组，范围仅限自身，见该函数内注释）。
    val expansionProgressProvider: () -> Float = remember(expansionProgressState) {
        { expansionProgressState.value }
    }
    val blockClicksWhileTransitioning = rememberUpdatedState(transitionInFlight)
    // ★ 转场可打断（2026-10-15）：`onReopen` 供下面 `pointerInput(Unit)` 读取 ——
    //   那段 lambda 只创建一次，必须走 `rememberUpdatedState` 才能拿到最新回调。
    val onReopenState = rememberUpdatedState(onReopen)
    // ★ 2026-10-15 修「点底栏误触抢回」：记录本全屏 Box 自身的 window 坐标，
    //   用以把 pointerInput 收到的局部坐标换算成 window 坐标，再与 mini 命中矩形比较。
    val hostCoordsState = remember { mutableStateOf<LayoutCoordinates?>(null) }

    // ★★★ 2026-10-15（打开播放页流畅度）：**常驻重层延后组合**。
    //
    // 事实：`MeloXIOSNowPlayingScene` 里「歌词」与「队列」两棵子树是**常驻**的（页面切换靠
    // 各自的 alpha/offset 做交叉淡入，见 scene 里那两个 Box）。封面页（打开播放页时的
    // 初始页）它们的 alpha 恒 0 ⇒ **一个像素都看不见**，但 `graphicsLayer{alpha=0}` 不会让
    // Compose 跳过子树：两棵子树照常参与**组合 / 测量 / 布局 / 绘制**。而它们恰好是本页最重
    // 的两块 —— 歌词是一整个 `LazyColumn`（`LazyLayoutCacheWindow(ahead=720dp, behind=240dp)`
    // ⇒ 视口外还多组合若干行）+ 逐字 `Canvas`；队列是一整个列表。
    //
    // 于是「打开播放页」这条 360ms 的 morph 时间线上，每一帧都在为**看不见的内容**付电。
    //
    // ⇒ 把它们的**首次组合**推到展开动画落定之后（`expansionProgress` 到 1）。
    //   · 为什么用 latch（单向置真）：收起方向 `transitionInFlight` 会重新变 true，
    //     若直接拿它当判据，下拉返回时歌词会在**手势中途凭空消失**（用户报障过的那类事故）。
    //   · 落定后立刻组合 ⇒ 用户之后滑到歌词/队列页时**早已就绪**，不会把卡顿转移到切页。
    //   · 场景侧还留了「当前页不是封面页 ⇒ 立刻组合」的旁路（冷启动直接进歌词页等），
    //     所以这条只影响「封面页的展开动画」这一种情形。
    //
    //   ★ 2026-10-22：拆成**两级**。原来歌词树和队列树在同一帧一起建，两棵 LazyColumn
    //     的 measure/layout 叠在一起 ⇒ morph 落定那一帧出现一次顿挫（用户：「掉帧卡顿感」
    //     最明显的那一下）。现在落定后第 1 帧只建歌词，让出一帧再建队列（+11ms）。
    //     用户在 11ms 内不可能切到队列页，「不转移卡顿」的前提依然成立。
    var residentLayersReady by remember { mutableStateOf(false) }
    var residentQueueLayersReady by remember { mutableStateOf(false) }
    LaunchedEffect(transitionInFlight) {
        if (!transitionInFlight) {
            // ★ 2026-10-24：落定后让出若干帧（`MELOX_RESIDENT_LAYERS_SETTLE_GRACE_FRAMES`），
            // 把歌词 / 队列层的**首次组合 + 首次栅格化**整体推到静止态（封面页 idle），
            // 避免与 morph 尾帧抢帧 / 帧序竞态导致「第一次展开偶尔不丝滑、第二次正常」
            // （第二次因 one-way latch + alpha=0 跳过绘制而不再付费）。见常量注释。
            // 期间若用户又收起（`transitionInFlight` 翻回 true）本 effect 取消、不会误置真；
            // 切到歌词 / 队列页的 `!artworkVisible` 旁路仍即时组合，宽限期内也照常。
            val graceFrames = MELOX_RESIDENT_LAYERS_SETTLE_GRACE_FRAMES
            repeat(graceFrames) { withFrameNanos { } }
            residentLayersReady = true
            // 等这一帧真正画出去再建第二棵树，两笔成本不会落在同一帧。
            withFrameNanos { }
            residentQueueLayersReady = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { hostCoordsState.value = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        // 只在按下那一刻判断；不在过渡期就完全不碰事件，下滑手势照常。
                        val down = awaitPointerEvent(PointerEventPass.Initial)
                            .changes.firstOrNull { it.changedToDown() } ?: continue
                        if (!blockClicksWhileTransitioning.value) continue
                        // 吞掉这一整段手势（含后续 up），避免 down 之后仍被判成 click。
                        // ★ 同时把这一段分类成「点按」还是「拖拽」：**点按**（最大位移 ≤ touchSlop
                        //   且时长 ≤ longPressTimeout）且**本次推进正朝关闭**（`hostExitingState`
                        //   = `closingIntent`）时，升级为「抢回播放页」—— 打断关闭、
                        //   从当前 fraction 连续回打开。拖拽一律不触发（保留原拦截语义）。
                        var pointerId = down.id
                        var maxDistance = 0f
                        var lastUptime = down.uptimeMillis
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            var stillPressed = false
                            for (change in event.changes) {
                                if (change.id == pointerId) {
                                    change.consume()
                                    lastUptime = change.uptimeMillis
                                    maxDistance = maxOf(
                                        maxDistance,
                                        (change.position - down.position).getDistance(),
                                    )
                                    if (change.pressed) stillPressed = true
                                }
                            }
                            if (!stillPressed) break
                        }
                        val isTap = maxDistance <= viewConfiguration.touchSlop &&
                            (lastUptime - down.uptimeMillis) <= viewConfiguration.longPressTimeoutMillis
                        // ★ 2026-10-15 修「打断动画期间点底栏也进入播放页」：reopen 只在「点按落在
                        //   mini 播放器命中矩形内」时才抢回。把 Box 局部坐标换算到 window 坐标再与
                        //   `miniPlayerHitRect` 比较（该矩形由 mini 播放器实时上报）。其余区域仍走
                        //   上面的 `consume()` 拦截（防按钮误触），但不抢回 ⇒ 点底栏导航不再误入。
                        val tapInWindow = (hostCoordsState.value?.localToWindow(Offset.Zero) ?: Offset.Zero) + down.position
                        val withinMini = miniPlayerHitRect.value?.contains(tapInWindow) == true
                        if (isTap && hostExitingState.value && withinMini) onReopenState.value()
                    }
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        val dragRangePx = (constraints.maxHeight * 0.86f).coerceAtLeast(1f)

        fun seekCollapseBy(delta: Float) {
            val old = gestureCollapseProgress
            val next = (old + delta / dragRangePx).coerceIn(0f, 0.999f)
            if (next == old) return
            gestureCollapseProgress = next
            seekJob?.cancel()
            seekJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                onSeekCollapse(next)
            }
        }

        suspend fun beginCollapseDrag() {
            settleJob?.cancelAndJoin()
            settleJob = null
            seekJob?.cancelAndJoin()
            seekJob = null
            gestureCollapseProgress = latestCollapseProgress.value.coerceIn(0f, 0.999f)
            onSeekCollapse(gestureCollapseProgress)
        }

        fun settleFromCurrent(velocity: Float) {
            val releaseProgress = gestureCollapseProgress
            val shouldCollapse = releaseProgress >= 0.42f || velocity >= 1200f
            settleJob?.cancel()
            settleJob = scope.launch {
                seekJob?.cancelAndJoin()
                seekJob = null
                // ⚠ 无论往哪边 settle，都必须**立即**清零手势标记（2026-10-02 修
                //   「圆角不跟手」）：松手后的动画由 `onSettleCollapse` → `animateTo`
                //   驱动（经过 transitionSpec 缓动），与「跟手路径」是两套。
                //   原来只在 `!shouldCollapse` 时清零 ⇒ 收起方向动画期间
                //   `gestureCollapseProgress` 仍是非 0 的旧值，圆角会继续走跟手路径、
                //   与真正的进度打架。`releaseProgress` 已在上面记下，不影响判定。
                gestureCollapseProgress = 0f
                onSettleCollapse(shouldCollapse)
                settleJob = null
            }
        }

        val dragState = rememberDraggableState { delta -> seekCollapseBy(delta) }

        // Lyrics/Queue own scrollable content. Child scrolling wins normally;
        // leftover downward motion at the top edge begins player collapse. Once
        // collapse has started, this connection owns both directions so the user
        // can reverse the transition without lifting the finger.
        val alternatePageCollapseConnection = remember(dragRangePx) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source != UserInput || latestPage.value == MeloXNowPlayingPage.Artwork) {
                        return Offset.Zero
                    }
                    if (gestureCollapseProgress <= 0f) return Offset.Zero
                    seekCollapseBy(available.y)
                    return Offset(x = 0f, y = available.y)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source != UserInput || latestPage.value == MeloXNowPlayingPage.Artwork) {
                        return Offset.Zero
                    }
                    if (available.y <= 0f && gestureCollapseProgress <= 0f) {
                        return Offset.Zero
                    }
                    if (gestureCollapseProgress <= 0f && available.y > 0f) {
                        settleJob?.cancel()
                        settleJob = null
                        gestureCollapseProgress = latestCollapseProgress.value.coerceIn(0f, 0.999f)
                    }
                    seekCollapseBy(available.y)
                    return Offset(x = 0f, y = available.y)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (latestPage.value == MeloXNowPlayingPage.Artwork ||
                        gestureCollapseProgress <= 0f
                    ) {
                        return Velocity.Zero
                    }
                    settleFromCurrent(available.y)
                    return available
                }
            }
        }

        // Alternate pages can always be dismissed by dragging the grabber even
        // when their list is currently scrolled away from the top.
        // ★ 横屏规则「只有下拉小白条 / 系统返回能退出」：横屏没有整屏下拉，所以横屏时
        //   **所有页面**（含 Artwork 封面页）都把收起手势挂在 grabber 上 —— 否则横屏封面页
        //   会变成完全无法下拉退出。
        val alternateGrabberDragModifier = if (page != MeloXNowPlayingPage.Artwork || isLandscape) {
            Modifier.draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = { beginCollapseDrag() },
                onDragStopped = { velocity -> settleFromCurrent(velocity) },
            )
        } else {
            Modifier
        }

        // ★ 2026-10-25：整屏背板捕获按**消费者门控**挂载。`actionsBackdrop`（本函数内
        //   `rememberLayerBackdrop()`）的唯一消费者是 ⋯ 菜单与音质浮层（见下方
        //   `CompositionLocalProvider(LocalMeloXBackdrop provides actionsBackdrop)`，
        //   其注释「the actions overlay samples the fully composed Now Playing scene
        //   from outside that recording layer」）——两者都关着时（**整个开合 morph
        //   全程**）没有任何东西采样这块纹理，但 `LayerBackdropNode.draw` 是无条件重录
        //   （kyant 库反编译确认）：morph 期整棵子树每帧失效 ⇒ 每帧整屏重录+重传。
        //   atrace 实测（tmp/morph/morph3.trace，V2505A 1440x3168）：morph 期该捕获
        //   每帧上传 18.25MB，是 morph 期纹理上传的最大单项（morph 16~33MB/帧
        //   vs 播放页静止 0.01MB/帧 vs 首页 3.3MB/帧）。
        //   ⚠ 关断后留 Grace：浮层退场动画（ModalBottomSheet / MeloXGlassDialog，
        //   ~200-350ms）期间玻璃仍在采样背板，不能当场摘捕获。
        //   ⚠ 判据只在开/关两帧翻转 ⇒ 不引入每帧重组（与 `miniSurfaceVisible` 同套路）。
        var actionsBackdropNeeded by remember { mutableStateOf(false) }
        LaunchedEffect(showActions, showQuality, actionsOverlayVisible) {
            if (showActions || showQuality || actionsOverlayVisible) {
                actionsBackdropNeeded = true
            } else {
                delay(ACTIONS_BACKDROP_EXIT_GRACE_MS)
                actionsBackdropNeeded = false
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (actionsBackdropNeeded) {
                        Modifier.layerBackdrop(actionsBackdrop)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(sharedShellModifier)
                    // 外壳与页面用**同一个形状函数**（平滑圆角／Lamé 超椭圆）—— 正圆弧在直边处
                    // 曲率跳变，就是用户说的「圆弧的过渡很生硬」的来源。
                    // ⚠ 2026-10-22：`graphicsLayer { shape = …; clip = true }` 与原来的
                    //   `Modifier.clip(shellShape)` **完全同义**（`clip()` 内部实现就是
                    //   `graphicsLayer(shape = shape, clip = true)`），唯一区别是**求值挪到
                    //   绘制相位** ⇒ 每帧只更新图层属性，不再重组整棵宿主。
                    // ⚠⚠ 形状必须走 `MeloXSlideShapeCache`（不是每次 new）：`MeloSmoothCornerShape`
                    //   没实现 equals（引用相等），每帧喂新实例会让 `RenderNode.setOutline`
                    //   每帧重设 + 裁剪层每帧重栅格化 —— 这正是 `MeloXPageTransitions.kt` 里
                    //   已经踩过并写了缓存的坑（那份注释还点名「播放页打开时外壳 morph 与
                    //   后方页面退让同时跑，漏掉的那一边照样每帧喂新 shape」）。
                    //   缓存按半径命中 ⇒ 半径不变时 outline 一次都不重建；半径变（动画中）
                    //   也只重建那么多次，与逐帧新建相比是数量级差异。
                    // ⚠ `flattenAnim.value` 也必须在这里读（它同样每帧在变）。
                    .graphicsLayer {
                        // ⚠⚠⚠ 2026-10-24 修「动画末端圆角偶尔没跟屏幕贴合（提前变直）」：
                        //   坐平乘子 `(1 - flattenAnim)` 的生效时机原本只取决于
                        //   `shellSettledByState`（过渡状态机 settle / `p>=1` 兜底）的翻转，
                        //   而它与「主时间线真正渲染到 p=1 那一帧」之间**存在帧序竞态**：
                        //   · `shellSettledByState` 可能在 p 仍 < 1 的最后一帧之前一帧就翻真；
                        //   · 或重组合时 `flattenAnim` 被 `remember { Animatable(if (shellSettledByState) 1f else 0f) }`
                        //     初始化成 1f（已坐平），而本次又处于展开过程 ⇒ 整段打开动画圆角都被乘到 0。
                        //   两种情况下，打开动画末端的圆角都会**提前变成直角**（不贴合屏幕）。
                        //
                        //   ⇒ 把坐平乘子的生效**直接门控在「本帧 p 真正 >= 1」**：
                        //      `p < 1` 时强制 flatten 部分为 0 ⇒ 圆角严格 = `meloXPlayerShellCornerDp(p)`，
                        //      在 p→1 时取到系统屏幕圆角峰值（贴合屏幕），绝不会被提前坐平；
                        //      `p >= 1`（主时间线已跑完）才允许 `flattenAnim` 介入，给出原设计的 140ms
                        //      平滑收口到直角。
                        //   该闸门只读 `expansionProgressState.value` 与 `flattenAnim.value` 这两个本就在
                        //    graphicsLayer lambda 内读取的值（不违反「组合期禁读动画值」那条性能铁律）。
                        //   关闭方向不受影响：关闭第一帧 p 仍 =1 但 `flattenAnim` 已被 `snapTo(0)`，
                        //   乘子 = (1 - 0) = 1 ⇒ 圆角当帧就是系统圆角峰值，随后随收缩落到 24dp（原语义不变）。
                        val p = expansionProgressState.value
                        val flattenRaw = flattenAnim.value
                        // ⚠ 与 `shellSettledByState` 同一阈值（0.999f，不是严格 1f）：
                        //   这条闸门只是「flatten 未启动前先别让它生效」的保险，
                        //   `flattenAnim` 现在只在 transition 彻底静止后才涨（见 §坐平判据）。
                        // ★★ 2026-11-01 定案（第四版，演进表见 `MeloXPlayerTransitionSpec` 常量处）：
                        //   **两个方向共用**几何律 `min(屏角, 矩形高/2)`，**`closing` 判据已删除**。
                        //   ④ 的真因：手势拖拽收起时 `MeloXApp` 刻意把 `playerClosingIntent` 写
                        //   `false`（内容层要跟手），直到**松手 settle 那一帧**才翻 true ⇒ 外壳圆角
                        //   会从「打开窗口」的中间值（p=0.6 时 ≈31.7dp）**瞬间跳到**几何律的 46dp
                        //   —— 正是用户报的「肉眼看不到、但有一瞬在闪」。
                        //   ⚠ 必须用 `MeloXShellGeom.bounds`（sharedBounds 每帧落下的**真值矩形**），
                        //     不能用本节点 `size`：迷你玻璃那层用的是它自己的 shapeSize，
                        //     二者同等于这个 bounds 矩形 ⇒ 两层逐帧同形。
                        //   ⚠ 陈旧值防护：`bounds` / `progress` 是**普通字段且永不清空**，上一轮收起
                        //     结束后会留成迷你栏矩形。若本帧 `p` 与几何进度差得远（典型：点按关闭的
                        //     第一帧 p=1、而遗留矩形只有 48dp 高）⇒ 判为陈旧、传 NaN，让函数回退到
                        //     进度窗口（p≈1 ⇒ 屏角），避免全屏四角被算成 24dp 闪一帧。
                        // ★★★ 2026-11-02 定案（探针 MXFIT 逐帧实证，替换旧的
                        //   `MeloXShellGeom.bounds` + `geomFresh` 口径）：
                        //
                        //   旧口径的两个致命点（真机/模拟器都实测到）：
                        //   ① `geomFresh = abs(MeloXShellGeom.progress - p) <= 0.2` **几乎恒 false**。
                        //      `record()` 由**两侧** overlay 副本各自调用（迷你栏侧 + 播放页侧），
                        //      而两侧的 `p` 是**互补的**（关闭时一侧 0.90、另一侧 0.10）——
                        //      最后写入者是谁就留谁的值 ⇒ 与本帧 `p` 永远差 ~0.8 ⇒ 判陈旧。
                        //      实测整段关闭动画 `geomFresh=false`、`halfHDp=NaN`，外壳圆角被
                        //      兜底窗口一路钉在 `24dp`；而玻璃走几何律 `min(屏角, min(w,h)/2)`
                        //      （p=0.10 时 = 63.6dp）⇒ 外壳 24dp / 玻璃 63.6dp，
                        //      外壳的直角块从玻璃四角探出 ⇒ 用户报的「左右斜上方两条弧线」。
                        //   ② 即便 `geomFresh` 侥幸为 true，`bounds` 也可能滞后一帧（共享元素
                        //      remeasure 与本节点 graphicsLayer 求值次序不定）。
                        //
                        //   ⇒ 直接用**本层 `size`**：`GraphicsLayerScope.size` 就是外壳这一刻
                        //     被 `RemeasureToBounds` 测出来的真实矩形，与玻璃那层
                        //     （现在也是 `fillMaxSize`）**同帧同值**，不存在进度口径问题，
                        //     也不存在陈旧值问题（上一轮遗留的 `bounds` 与本帧 size 无关）。
                        //   ⚠ 必须与玻璃那边 `MeloXPlayerGlassShape.createOutline` 的
                        //     `min(peakPx, min(w,h)/2)` **同值**：本函数取 `min(peak, 高/2)`，
                        //     因为 morph 期恒有 `高 < 宽` ⇒ 两者都等于 `min(peak, 高/2)`。
                        //   ⚠ 不要改回 `MeloXShellGeom.bounds`：那个字段是**全局单槽**，
                        //     被两条互补动画线同时写，天然不适合当半径来源（它只适合
                        //     `MeloXChromeOcclusion` 那种「大范围遮挡」判定，误差容忍度高）。
                        val shellRectH = size.height
                        val halfHDp = if (shellRectH > 0f) shellRectH.toDp().value / 2f else Float.NaN
                        val radiusDp = meloXPlayerShellCornerDp(
                            p,
                            shellPeakDp,
                            rectHalfHeightDp = halfHDp,
                        ) * (1f - (if (p >= 0.999f) flattenRaw else 0f))
                        shape = shellShapeCache.of(radiusDp.dp.toPx(), 1f)
                        clip = true
                    }
                    // ★ 横屏：不挂「歌词/队列列表滚到顶后继续下拉 = 收起」的嵌套滚动连接。
                    //   否则在横屏滚歌词/列表时，连续下拉会一路把播放页收掉 = 误退出。
                    //   横屏的收起只认「下拉小白条」。
                    .then(
                        if (isLandscape) {
                            Modifier
                        } else {
                            Modifier.nestedScroll(alternatePageCollapseConnection)
                        },
                    )
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        // ★ 横屏禁用「整屏下拉收起」（竖屏保留）：横屏只有下拉小白条 /
                        //   系统返回手势能退出播放页。
                        // ★ 2026-10-25：转屏形变未结束时也禁用下拉收起（见 `orientationBusy`）。
                        enabled = page == MeloXNowPlayingPage.Artwork && !isLandscape && !orientationBusy,
                        onDragStarted = { beginCollapseDrag() },
                        onDragStopped = { velocity -> settleFromCurrent(velocity) },
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // Keep the opaque fallback inside the shared
                            // surface. Putting it on the sharedBounds node
                            // makes the black fallback itself participate in
                            // the capsule-to-screen transform, which exposes
                            // four dark corners during expansion.
                            // ⚠ 2026-10-22：原来是
                            //     `if (isolationAlpha > 0f) Modifier.background(Color.Black.copy(alpha = …))`
                            //   —— 判据和颜色都在**组合期**求值，于是隔离透明度这条曲线
                            //   又把整棵宿主拖着每帧重组。改成语义等价的
                            //   `drawBehind { drawRect(…) }`（`background(color)` 的实现本来
                            //   就是 `drawBehind { drawRect(color) }`，绘制顺序完全一致；
                            //   alpha=0 时 drawRect 是空操作，所以不再需要那个 `if`）。
                            .drawBehind {
                                // ★ 2026-10-24 修「转屏 / 塌缩时下半区漏出首页」：内容层底层
                                //   **恒铺一层不透明深色**。正常态被模糊背景完全盖住、零观感差异；
                                //   一旦转屏约束滞后 1 帧（内容层比窗口矮一截）、或背景模糊未就绪、
                                //   或隔离黑还没到阈值，这一层兜底，保证外壳内容**永远不透出背后**。
                                // ★★ 2026-10-31 兜底黑改随收起进度淡出（见 `blackFallbackAlphaProvider`，
                                //   **只在 closing 方向生效**）：防止收回后半段这层不透明黑沿迷你胶囊轮廓
                                //   露出一圈（“黑体不够贴合”）；展开/已展开方向恒不透明（对齐上游「盖住首页
                                //   防漏」）。`drawRect` 仍无条件保留，alpha=0 时空操作；流光/封面由
                                //   `backdropAlpha` 恒 1 不出问题。
                                // ★ 2026-11-01 **定案：黑兜底恒跟随外壳裁剪（`drawRect`），不做任何
                                //   逐方向的角补偿**。B 方案（关闭方向裁 24dp）已彻底废弃：它让黑层
                                //   比外壳小一圈，外壳 46dp 区域反而在黑层之外裸露。
                                //   ⚠ 之前记录的根因（「外壳角 vs recede 页角不一致」）**是错的**：
                                //   recede 页圆角恒 = 系统屏角，比外壳更早切角 ⇒ 永远躲在外壳轮廓之内，
                                //   不存在「露出 recede 弧」。收起后半段那两条斜上方弧线真因是
                                //   **迷你玻璃 `Capsule()`（半径 = morph 矩形高/2，每帧在变）与外壳
                                //   24dp 圆角不相等** ⇒ 外壳在两上角斜对角探出玻璃轮廓。
                                //   ⇒ 修在同形的另一端（`MeloXIOSMiniPlayer.miniGlassShape`），本层不动。
                                //   保留 v14 的 alpha gate：closing 方向随进度淡出，展开方向恒满（防首页漏）。
                                drawRect(
                                    Color.Black,
                                    alpha = blackFallbackAlphaProvider(),
                                )
                                val isolationAlpha = isolationAlphaProvider()
                                if (isolationAlpha > 0f) {
                                    drawRect(Color.Black, alpha = isolationAlpha)
                                }
                            }
                            .layerBackdrop(playerControlBackdrop)
                            // ★★ 2026-11-02：恢复恒 1f。换源跳色已改在**背板对象**上解决
                            //   （迷你栏玻璃显式采 `bottomChromeBackdrop`，见 `backdropAlpha`
                            //   的说明），不再需要拿整页渐隐去掩盖。
                            .graphicsLayer {
                                // ★★★ 2026-11-03 02:0x（第 20 轮，真机照片定案）：
                                //   关闭末段整块让位给迷你栏。
                                //   根因：外壳高 48dp、内容层 42dp、圆钮 40dp ⇒ 圆钮外那圈
                                //   4dp 镶边在静止态是白玻璃，关闭末段填的却是**外壳自己的
                                //   底色**（暂停黑 / 播放态封面彩色，用户两次指出「颜色跟播放
                                //   页背景一样」）。改黑兜底只能治暂停态，播放态照样露。
                                //   ⇒ 外壳高度进入「距终点 ≤25%」（48dp→60dp）这一段时，
                                //     整块 alpha 收敛到 0，把那圈交还给迷你栏白玻璃。
                                //   `tailAlpha` 与 bounds 严格同源（`MeloXShellGeom.record`），
                                //   不是另一条进度曲线 ⇒ 不会与几何错拍。
                                alpha = backdropAlpha * MeloXShellGeom.tailAlpha
                            },
                ) {
                    // 🔴🔴 2026-10-28 14:0x：**「流光背景被额外往下拉」不在这里做**。
                    //   13:5x 曾在本层加 `graphicsLayer{ scaleY = 1+0.35·sf; origin=TopCenter }`，
                    //   用户装上后判「观感上还是想要 0.6.5 那种被额外往下拉的效果」。
                    //   根因是**外壳带 `clip = true`**（见上面 966 行那段 `graphicsLayer`）：
                    //   本层拉长后超出底边的部分被整块裁掉 ⇒ 屏幕上只剩「迷你栏里那块光斑
                    //   纵向放大一点、底部被切」，读不出「被拽下去」。
                    //   ⇒ 14:2x **回到 0.6.5 的纯平移**：没有任何额外拉伸，88px(22dp)
                    //     整体下沉**本身就是**用户要的那一记「被拉下去」。
                    //     `BackdropStretch`（bounds 底边多延 dy×0.6）试过一版，被读成
                    //     「板子变形」⇒ 已删除。详见 `MeloXPlayerTransitionSpec.kt` 的
                    //     `meloXPlayerSinkFraction` KDoc。
                    //   ⛔ 别在这里再叠一次 scaleY（双重拉伸会把光斑拉失真）；<br>
                    //   ⛔ 也别改成 translationY（外壳纵向压缩比 ≈0.09，px 位移会被压没）。
                    if (
                        page == MeloXNowPlayingPage.Lyrics &&
                        MeloXSettingsRuntime.playerBackgroundMode == MeloXPlayerBackgroundMode.AppleLyrics
                    ) {
                        MeloXLyricsArtworkBackdrop(
                            artworkUrl = state.artworkUrl,
                            isPlaying = state.isPlaying && shellExpandedEnough,
                            planeOverride = if (transitionInFlight) 1 else null,
                        )
                    } else if (MeloXSettingsRuntime.playerBackgroundMode == MeloXPlayerBackgroundMode.FlowingLight) {
                        MeloXFlowingLightBackdrop(
                            mediaId = state.mediaId,
                            artworkUrl = state.artworkUrl,
                            isPlaying = shellExpandedEnough,
                            mixOutgoingArtworkUrl = state.outgoingArtworkUrl
                                .takeIf { state.isInTransition && !state.transitionHandedOff },
                            mixIncomingArtworkUrl = state.incomingArtworkUrl
                                .takeIf { state.isInTransition && !state.transitionHandedOff },
                        )
                    } else if (MeloXSettingsRuntime.playerBackgroundMode == MeloXPlayerBackgroundMode.CustomImage) {
                        MeloXCustomImageBackdrop(
                            path = MeloXSettingsRuntime.customBackgroundPath,
                            artworkUrl = state.artworkUrl,
                        )
                    } else {
                        // 2026-11-06：回到上游原版调用（不再传 relaxed，模糊半径恒 38dp）。
                        MeloXBlurredArtworkBackdrop(state.artworkUrl)
                        // ★ 2026-11-04：打开动画早期（expansionProgress 0→0.25）在背景之上叠一层
                        //   快速淡出的微亮白光（峰值 10% alpha）→ 制造「点亮」感、把视线引导到内容层，
                        //   并让转场尾段读起来更顺。关闭方向末 25% 进度也会淡入一点微光，但此刻背景
                        //   本就在退、几乎不可见，无副作用。读取走绘制期 lambda（读
                        //   expansionProgressState.value），0 重组。
                        //
                        // 🔴🔴 2026-11-06（第 24 轮，无真机轮）：**去掉图层**。
                        //   原来写的是 `.graphicsLayer { alpha = … }.background(Color.White)`。
                        //   `alpha < 1` 会让 HWUI 为这个节点开一张 **满屏** 离屏表面
                        //   （本机 1440×3168 ≈ 4.56M px ≈ 18MB），每帧一张、p<0.25 的整段
                        //   （460ms × 25% ≈ 14 帧 @120Hz）都在付。而它要画的只是**一块纯白**。
                        //   ⇒ 纯色填充 + 图层 alpha 与「直接用带 alpha 的画笔填充」**逐像素等价**
                        //     （单块实心、无自身重叠，src-over 的结果与先离屏再合成完全一致），
                        //     但后者不需要任何离屏表面。
                        //   ⚠ 别改回 `.graphicsLayer{alpha}`；⚠ 也别把 `drawRect` 换成
                        //     `background(Color.White.copy(alpha))` —— 那会在**组合期**读进度，
                        //     整页按帧重组（本文件其他地方的铁律）。
                        Box(
                            Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    val p = expansionProgressState.value
                                    val a = if (p < 0.25f) (0.25f - p) / 0.25f * 0.10f else 0f
                                    if (a > 0f) drawRect(Color.White, alpha = a)
                                },
                        )
                    }
                }

                CompositionLocalProvider(
                    LocalMeloXBackdrop provides playerControlBackdrop,
                    // ⚠⚠ 过渡进行中把**场景内全部控件**（暂停 / 上一首 / 下一首 / 音量条…）
                    //   显式禁用（用户报障「下拉返回的缩小播放器里的按钮还能被点击」）。
                    //   与根节点的 `PointerEventPass.Initial` 拦截器**双保险**。
                    LocalMeloXPlayerControlsEnabled provides !transitionInFlight,
                ) {
                    // ★★ 2026-10-26：收起方向内容淡出的**分母** —— 窗口高度（px）。
                    //   外壳是 `fillMaxSize`，所以「本帧 size.height / 窗口高度」就是
                    //   外壳当前高度比。取不到（0）时退化成 1f ⇒ 该帧按「未收缩」处理。
                    val shellFullHeightPx = LocalWindowInfo.current.containerSize.height.toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // ⚠ 2026-10-22：`fullPlayerAlpha` 不再在组合期算，
                            //   改在绘制相位读 —— 与原来同一个 smoothStep、同一组阈值。
                            // ★★ 2026-10-26（scale 模式）：阈值改到 0.30→0.72（见
                            //   `MeloXShellContentFade*`）。原来的 0.04→0.52 是给
                            //   `RemeasureToBounds` 配的 —— 那种模式下内容在小盒子里**重排**，
                            //   早早淡入不会有形变；换成 `ScaleToBounds` 后整页被等比压扁，
                            //   p≈0.3 时容器只有 50% 高、内容已 54% 不透明 ⇒ 会看到"文字被拉扁"。
                            //   `MeloXShellScaleMorph=false` 时本行逐字回到历史阈值。
                            .graphicsLayer {
                                // 🔴🔴 2026-10-29 00:3x：曲线整文件换回 0.7.0 后那批
                                //   `MeloXShell*Fade*` / `MeloXShellScaleMorph` 常量已不存在。
                                //   它们在本地的取值（`ScaleMorph=false` + `ContentFollowsProgress=true`）
                                //   恒等价于 HEAD 的 `smoothStep(expansionProgressState.value, 0.04f, 0.52f)`
                                //   ⇒ 直接写成该常量表达式，与 0.7.0 逐字相同。
                                alpha = smoothStep(expansionProgressState.value, 0.04f, 0.52f)
                            },
                    ) {
                        if (MeloXSettingsRuntime.playerShell == MeloXPlayerShell.Classic) {
                            MeloXClassicNowPlayingScene(
                                state = state,
                                page = page,
                                onPageChanged = { destination ->
                                    transitionSourcePage = page
                                    page = destination
                                },
                                onDismiss = { if (!orientTransition.isRunning) onDismiss() },
                                onShowActions = { showActions = true },
                                onShowQuality = { showQuality = true },
                            )
                        } else MeloXIOSNowPlayingScene(
                            state = state,
                            page = page,
                            transitionSourcePage = transitionSourcePage,
                            // ★ 2026-10-25：转屏形变未结束时忽略退出（点小白条 / 抓手同源）。
                            onDismiss = { if (!orientTransition.isRunning) onDismiss() },
                            onPageChanged = { destination ->
                                showLandscapeSkyline = false
                                if (destination != page) {
                                    transitionSourcePage = page
                                    page = destination
                                    if (rememberPlayerPage) {
                                        MeloXSettingsPreferences.setString(context, "playback_last_page", destination.name)
                                    }
                                }
                            },
                            onShowActions = {
                                showQuality = false
                                showActions = true
                            },
                            onShowQuality = {
                                showActions = false
                                showQuality = true
                            },
                            showLandscapeSkyline = showLandscapeSkyline,
                            onShowLandscapeSkyline = { showLandscapeSkyline = true },
                            onHideLandscapeSkyline = { showLandscapeSkyline = false },
                            onLyricsInterfaceHiddenChange = { lyricsInterfaceHidden = it },
                            grabberDragModifier = alternateGrabberDragModifier,
                            lyricsActive = page == MeloXNowPlayingPage.Lyrics &&
                                shellFullyExpanded &&
                                !showLandscapeSkyline,
                            // ★ 常驻重层（歌词/队列）的首帧组合推到展开动画落定之后，见上方 latch 注释。
                            residentLayersReady = residentLayersReady,
                            residentQueueLayersReady = residentQueueLayersReady,
                            orientTransition = orientTransition,
                            columnCenterProgress = columnCenterProgress,
                            // ★ 开 / 闭播放页的 morph 判据（离散 State）：让 Scene 把底栏玻璃、
                            //   四键胶囊、歌词逐行模糊在运动期间降档，落定自动恢复。
                            shellTransitioning = transitionInFlightState,
                            // ★ 塌缩（关闭）方向判定：塌缩期间强制横竖屏过渡落定、关掉 np-controls
                            //   旋转形变，见 Scene 内 `closingPlayer` 用法与本条修复说明。
                            closingPlayer = closingPlayerState,
                            sharedTransitionScope = sharedTransitionScope,
                        )
                    }
                }

            }
        }

        // Keep the artwork outside the player surface's clip and stacking
        // layer so the shared flight remains above the expanding backdrop.
        if (MeloXSettingsRuntime.playerShell != MeloXPlayerShell.Classic) {
                SharedArtworkDestination(
                    state = state,
                    page = page,
                    onArtworkTap = {
                        transitionSourcePage = page
                        page = if (page == MeloXNowPlayingPage.Artwork) MeloXNowPlayingPage.Lyrics else MeloXNowPlayingPage.Artwork
                        if (rememberPlayerPage) {
                            MeloXSettingsPreferences.setString(context, "playback_last_page", page.name)
                        }
                    },
                    expansionProgress = expansionProgressProvider,
                    hidden = showLandscapeSkyline,
                    // ⚠ 只在「播放页已完全展开（不在转场/收起途中）」才接管封面手势。
                    gesturesEnabled = !transitionInFlight && !showLandscapeSkyline,
                    orientTransition = orientTransition,
                    columnCenterProgress = columnCenterProgress,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    closingIntent = closingIntent,
                )
        }

        CompositionLocalProvider(LocalMeloXBackdrop provides actionsBackdrop) {
            MeloXNowPlayingActionsSheet(
                state = state,
                visible = showActions,
                onEffectiveVisibleChange = { actionsOverlayVisible = it },
                onDismiss = { showActions = false },
                onNavigateSearch = onNavigateSearch,
                onLocalMetadataChanged = onLocalMetadataChanged,
                // ★ 只有**平板双栏**才把「天际歌词」挂进 ⋯ 更多：手机横屏的天际入口仍是右列
                //   曲名栏上那枚字形按钮（`LandscapeSongHeader`），竖屏则从来进不去天际。
                //   ⚠ 这里**不看** `MeloXSettingsRuntime.skylineEnabled`：那个开关的语义是
                //   「横屏**自动**启用」（进歌词页就自动进天际），手动点菜单位置属于显式意图，
                //   关掉自动时更应该给一条手动通道，否则平板双栏就彻底没有天际入口了。
                onShowSkyline = if (twoPane) openLandscapeSkyline else null,
            )
            MeloXQualitySelectionOverlay(
                state = state,
                visible = showQuality,
                onDismiss = { showQuality = false },
                onOpenPlaybackSettings = onOpenPlaybackSettings,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedArtworkDestination(
    state: MeloXPlaybackUiState,
    page: MeloXNowPlayingPage,
    // ⚠ 2026-10-22：与下方 `columnCenterProgress` 同一条纪律 —— **每帧状态一律走 `() -> Float`**。
    //   原来这里是值传参，调用方（1853 行的 SharedHost）为了算出这个 Float 必须每帧重组，
    //   于是整棵播放页跟着重组 32 次。改成 lambda 后：host 只在转场起止重组，
    //   每帧读值发生在**本函数自己的 scope**（它本来就因 `orientTransition` 每帧重组，
    //   范围仅限封面这一块）。
    onArtworkTap: () -> Unit,
    expansionProgress: () -> Float,
    hidden: Boolean = false,
    gesturesEnabled: Boolean = false,
    orientTransition: Transition<Boolean>,
    // 双栏「玩家卡片居中」进度 0→1（0 = 左右分栏，1 = 左列滑到屏幕正中）。
    // ⚠ 由外层 host **单一计算后透传**（见那里的长注释）；手机 / 竖屏恒 0，逐像素不变。
    // ⚠ 用 `() -> Float` 而不是值：这是每帧状态，值传参会把**调用方**（host）每帧重组。
    columnCenterProgress: () -> Float = { 0f },
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    // 由外层 host 透传：`MeloXApp` 在动画起跑前写下的方向 intent（详见那里注释）。
    closingIntent: Boolean = false,
) {
    var transitionAnimationEligible by remember { mutableStateOf(false) }
    var previousTransitionActive by remember { mutableStateOf(false) }
    LaunchedEffect(state.isInTransition, page, MeloXSettingsRuntime.transitionUiEnabled) {
        if (state.isInTransition && !previousTransitionActive) {
            transitionAnimationEligible =
                page == MeloXNowPlayingPage.Artwork && MeloXSettingsRuntime.transitionUiEnabled
        } else if (state.isInTransition && page != MeloXNowPlayingPage.Artwork) {
            transitionAnimationEligible = false
        } else if (!state.isInTransition) {
            transitionAnimationEligible = false
        }
        previousTransitionActive = state.isInTransition
    }
    val window = rememberMeloXWindowInfo()
    // ★★ Fix3（时钟同步）：封面本地 `isLandscape` 改走与场景层**完全相同**的
    //   `orientTransition.targetState`，不再用 `window.isLandscape`（即时翻转）。
    //   原因：窗口一旋转，`window.isLandscape` 立刻翻，但场景的几何 `orientProgress`
    //   要等 `ORIENT_SETTLE_FRAMES` 起跑闸才起跑（晚 ~2 帧）；这 2 帧里封面的分页逻辑
    //   （`pageFrameProgress`/`destPageFrame`/padding/手势/天际源）已按横屏算、几何还竖屏
    //   ⇒ "容器已横、进度仍竖" 错位，歌词页还会 "72dp 缩略图硬跳大封面"。
    //   两者共用同一个 `orientTransition` 实例（Scene 与封面同参传入）⇒ targetState 严格同帧，
    //   封面与场景在同一时钟上连续变形 = 真正一镜到底。`twoPane` 仍走 `window`，不受影响。
    val isLandscape = orientTransition.targetState
    // ★ 平板横屏双栏（2026-10-07）：左列封面下方还要放「曲名/艺人行 + 控制条」整块，
    //   封面必须随之上抬（整块在列内居中 ⇒ 封面中心 = 下边距 + 封面高 / 2）。
    //   ⚠ 判据走 `window.supportsPlayerTwoPane`（官方 WindowSizeClass 语义：宽 / 高都「非
    //   Compact」= 宽 ≥ 600dp 且高 ≥ 480dp；2026-10-24 由写死像素门槛升级为尺寸分级），
    //   手机横屏 792×360dp 不满足 ⇒ 恒 false，本文件对其逐像素不变。
    val twoPane = window.supportsPlayerTwoPane
    // Upstream keeps one artwork alive and animates its frame for 0.48s when
    // switching between artwork and alternate pages.
    val headerProgress by animateFloatAsState(
        targetValue = if (page == MeloXNowPlayingPage.Artwork) 0f else 1f,
        animationSpec = tween(
            durationMillis = 480,
            easing = FastOutSlowInEasing,
        ),
        label = "persistent-artwork-page-frame",
    )

    val playbackScale by animateFloatAsState(
        targetValue = if (MeloXSettingsRuntime.reduceMotion || !MeloXSettingsRuntime.artworkMotionEnabled || state.isPlaying) 1f else 0.74f,
        animationSpec = if (state.isPlaying) {
            spring(
                dampingRatio = 0.70f,
                stiffness = 280f,
                visibilityThreshold = 0.001f,
            )
        } else {
            spring(
                dampingRatio = 0.94f,
                stiffness = 360f,
                visibilityThreshold = 0.001f,
            )
        },
        label = "shared-artwork-playback-scale",
    )
    // ★★ 切歌回弹：换歌那一帧，封面从「缩小 + 半透明」弹回原样，取代原来的硬切
    //    （用户反馈「切歌时专辑封面太生硬」）。
    //    ⚠ 三条纪律：
    //    1. 只认 `mediaId`、**不认** `artworkUrl`：封面 url 会被异步回填（Lx 补封面 / 本地扫描），
    //       那是同一首歌的加载，弹一下会被误读成换歌。
    //    2. 「开始播放 / 停止播放」（mediaId 的 null 那一侧）**不弹**：那时播放器本身正在展开
    //       或收起，再叠一层缩放会跟展开动画打架。
    //    3. 「减弱动效」或关闭「封面动效」⇒ 恒 1，原样直切。
    //    ⚠ 读取放在 `graphicsLayer` lambda 里（与 `phaseProvider = { animatable.value }` 同源）：
    //       composition 里直接读 Animatable.value 不订阅快照。
    val switchBounce = remember { Animatable(1f) }
    val switchBounceSeen = remember { ArtworkSwitchBounceSeen() }
    LaunchedEffect(state.mediaId) {
        val previous = switchBounceSeen.mediaId
        val current = state.mediaId
        switchBounceSeen.mediaId = current
        if (previous == null || current == null) return@LaunchedEffect
        if (MeloXSettingsRuntime.reduceMotion || !MeloXSettingsRuntime.artworkMotionEnabled) {
            return@LaunchedEffect
        }
        switchBounce.snapTo(0f)
        switchBounce.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = MeloXArtworkSwitchBounceDamping,
                stiffness = MeloXArtworkSwitchBounceStiffness,
                visibilityThreshold = 0.001f,
            ),
        )
    }

    // ★★ 横屏封面手势（用户要求）：双击 = 播放/暂停，左滑 = 下一首，右滑 = 上一首。
    //   ⚠ 接管范围收得很紧，三条纪律：
    //    1. **竖屏根本不挂** pointerInput：竖屏封面那块区域属于页面级手势（下拉收起等），
    //       多一层手势会改变命中/消费行为，风险远大于收益。
    //    2. 只消费**水平**位移（`dragging` 判定用 `dx`）：竖直方向不消费 ⇒ 下拉收起不受影响。
    //    3. 未展开 / 转场中 / 覆盖层显示时 `enabled=false` ⇒ 手势直接放行（不消费事件）。
    val artworkGesturesEnabled = rememberUpdatedState(gesturesEnabled && !hidden)
    val latestArtworkTap = rememberUpdatedState(onArtworkTap)
    val artworkDrag = remember { Animatable(0f) }
    val artworkGestureScope = rememberCoroutineScope()
    val artworkMaxDragPx = with(LocalDensity.current) { MeloXArtworkSwipeMaxDrag.toPx() }
    val artworkThresholdPx = with(LocalDensity.current) { MeloXArtworkSwipeThreshold.toPx() }
    val artworkGestureModifier = if (isLandscape || MeloXSettingsRuntime.artworkTapLyrics || MeloXSettingsRuntime.artworkSwipeTracks) {
        Modifier.pointerInput(artworkMaxDragPx, artworkThresholdPx, isLandscape, MeloXSettingsRuntime.artworkTapLyrics, MeloXSettingsRuntime.artworkSwipeTracks, page) {
            meloXArtworkGestures(
                enabled = { artworkGesturesEnabled.value },
                swipeEnabled = MeloXSettingsRuntime.artworkSwipeTracks && page == MeloXNowPlayingPage.Artwork || isLandscape && !MeloXSettingsRuntime.artworkSwipeTracks,
                thresholdPx = artworkThresholdPx,
                maxDragPx = artworkMaxDragPx,
                onDragDelta = { delta ->
                    artworkGestureScope.launch {
                        artworkDrag.snapTo(
                            (artworkDrag.value + delta * MeloXArtworkSwipeDamping)
                                .coerceIn(-artworkMaxDragPx, artworkMaxDragPx),
                        )
                    }
                },
                onRelease = { dragged ->
                    artworkGestureScope.launch {
                        artworkDrag.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
                        )
                    }
                    when {
                        // 左滑（位移为负）→ 下一首；右滑 → 上一首（与 mini 播放器一致）。
                        dragged <= -artworkThresholdPx -> state.next()
                        dragged >= artworkThresholdPx -> state.previousTrack()
                    }
                },
                onDoubleTap = { state.togglePlayPause() },
                onTap = if (MeloXSettingsRuntime.artworkTapLyrics && page != MeloXNowPlayingPage.Queue) {
                    { latestArtworkTap.value() }
                } else null,
            )
        }
    } else {
        Modifier
    }

    // ★ 阴影强度（模糊副本的 alpha）：AM 的阴影会随播放态轻微呼吸 —— 播放中略强、暂停略收。
    //   旧实现这里是 `elevation = 26.dp / 14.dp` 喂给平台 `.shadow()`；平台 elevation 阴影
    //   在 vivo/iQOO 上几乎不可见且不可控（RenderNode 离屏缓存 + 厂商 ambient/spot 差异），
    //   已于 2026-10-07 换成「封面自身的模糊副本」，这个动画保留下来驱动副本的 alpha。
    val artworkShadowStrength by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.86f,
        animationSpec = spring(
            dampingRatio = 0.92f,
            stiffness = 320f,
        ),
        label = "shared-artwork-shadow",
    )

    val pageFrameProgress = if (isLandscape) 0f else headerProgress
    // ★★ 2026-11-03（第 32 轮）：**暂停缩放的「退出保持」**——修「退出时封面先放大回
    //   播放中尺寸，再做退出动画」。
    //
    //   现象（用户报障）：「横屏暂停时封面是小的，可是退出时他会放大到播放中的大封面
    //   再进行动画」。
    //   根因：`fullScreenScaleBlend = smoothStep(expansionProgress(), .30f, .88f)` 本来只是
    //   「展开途中别把封面二次缩小」的闸门（展开时封面从迷你尺寸飞出，若同时叠加暂停缩小
    //   会变成 0.74×0.74）。但它在**收起方向也会从 1 掉到 0** ⇒ 代入
    //   `effectiveScale = 1 + (playbackScale-1) * blend` 后，暂停缩小（0.74）被逐步还原成
    //   1.0 ⇒ 封面在退出前半段先「长回全尺寸」，然后才跟着外壳缩。
    //   正解：**收起方向把 blend 锁死在 1**，让 `effectiveScale` 全程等于 `playbackScale`。
    //   终点天然对齐、不会在交接处弹跳：迷你栏封面自己也有**同一套**暂停缩小
    //   （`MeloXIOSMiniPlayer.kt` 的同一个 0.74 判据），所以「保持小尺寸飞回去」= 到达即吻合。
    //
    //   ⚠ 判据**不能只靠 `closingIntent`**：手势拖拽收起时 `MeloXApp` 刻意把它写成 false
    //     （内容层要跟手，见 `MeloXShellGeom` 处那段注释），松手 settle 那帧才翻 true。
    //     所以这里用「展开进度**本帧在下降**」判方向，并对整段收起**锁存**——单帧 delta≈0
    //     （动画暂停/掉帧）时不会误清锁存。
    //   ⚠ 用普通数组而不是 snapshot state：它每帧在组合里更新，**绝不能触发重组**
    //     （`ArtworkSwitchBounceSeen` 是同一种用法）。下标 0 = 上一帧进度，1 = 锁存标志。
    val artworkScaleHold = remember { floatArrayOf(-1f, 0f) }
    val expansionNow = expansionProgress()
    val closingNow = expansionNow < artworkScaleHold[0] - 1e-4f || closingIntent
    if (closingNow) {
        artworkScaleHold[1] = 1f
    } else if (expansionNow >= 0.999f) {
        // 重新展开到落定 ⇒ 解除锁存，下次展开仍走原来的渐进闸门。
        artworkScaleHold[1] = 0f
    }
    artworkScaleHold[0] = expansionNow
    val fullScreenScaleBlend = if (artworkScaleHold[1] > 0.5f) {
        1f
    } else {
        smoothStep(expansionNow, 0.30f, 0.88f)
    }
    val effectiveScale = 1f +
        (playbackScale - 1f) * fullScreenScaleBlend * (1f - pageFrameProgress)
    // ★★ 关闭方向把封面「测量尺寸」钉到视觉尺寸 —— 修「横屏暂停下拉关闭，小封面突然变大成大封面」。
    //   根因：`sharedElement` 的 morph 源矩形 = 节点**测量**尺寸（= `targetSize`），内层
    //   `graphicsLayer` 缩放（暂停态 0.74）不进入测量 ⇒ morph 起飞瞬间源矩形是满布局尺寸 ⇒
    //   暂停态那枚 0.74 的小封面会瞬间弹回满尺寸再缩下去（用户报「小封面突然变大成大封面，再拿
    //   这个大封面 morph 回去」）。正确行为：morph 源 = 视觉小封面（= 0.74×targetSize）。
    //   修法：把 `effectiveScale` 从「内层 graphicsLayer 缩放」搬到「盒子测量尺寸」，内层只留
    //   bounce ⇒ 测量尺寸 = 视觉尺寸；且 `盒子尺寸 × 内层 scale` 恒 ≡ 旧 `targetSize × effectiveScale`
    //   （量不变，打开方向逐像素等价）。
    //   ⚠ 仅 `closingNow` 锁存时（`artworkScaleHold[1] > 0.5f`）才搬：关闭途中 `effectiveScale`
    //     恒 0.74 ⇒ 盒子尺寸恒定、不引入逐帧重测；打开方向盒子仍 = `targetSize`，旧行为不变。
    //   ⚠ 内层 clip 半径同步按 `artworkBoxScale` 同比补偿（见下方 `plainArtworkClip`）⇒
    //     可见圆角逐像素不变（否则盒子缩小会让圆角被稀释成更小半径）。
    val artworkBoxScale = if (artworkScaleHold[1] > 0.5f) effectiveScale else 1f
    // ★ 内层 transform 的缩放基准与盒子**互补**：关闭方向已把 `effectiveScale` 搬到盒子
    //   测量尺寸（morph 源 = 视觉小封面），内层就只留 `bounce`；打开/原地方向盒子仍是
    //   `targetSize`，`effectiveScale` 必须**留在内层**，否则暂停缩小丢失 ⇒ 封面停在满尺寸
    //   （即「暂停态被放大」，本仓 2026-11-07 报障）。两种方向视觉缩放恒 = `effectiveScale ×
    //   bounce`、与改动前逐像素等价；盒子 `×` 内层 `×` 乘积不变 ⇒ 不双重叠加、不开销。
    val artworkInnerScale = if (artworkScaleHold[1] > 0.5f) 1f else effectiveScale

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .meloXPlayerStatusBarsPadding()
            // 与场景层用**同一份**挖孔内边距，保证封面左列与右侧信息列仍严格对齐。
            .meloXPlayerDisplayCutoutPadding()
            .padding(horizontal = if (isLandscape) 20.dp else 32.dp),
    ) {
        val contentTop = 30.dp
        // ⚠ 非封面页（歌词/列表）那枚 72dp 缩略图的纵向落点，必须等于**场景层**内容区
        //   top = `meloXPlayerStatusBarsPadding` + `SceneGrabber` 高（当前 22dp）。
        //   缩略图与场景 header 行同为 72dp ⇒ 同 top 即天然对齐（缩略图与歌名/按钮同一条水平带）。
        //   之前沿用 `contentTop(30dp)` ⇒ 缩略图比 header 行低 8dp（grabber 由 30dp 改到 22dp 的遗留）。
        //   ⚠ 改 `SceneGrabber` 高度时**必须同步这里**。
        val compactFrameTop = 22.dp
        val portraitContentHeight = (maxHeight - contentTop - MeloXNowPlayingControlsHeight.dp)
            .coerceAtLeast(1.dp)
        // ★ 统一主时间线：封面尺寸/位置由**同一条 orientation Transition** 驱动
        // （与场景层共用 `orientTransition`）。转屏时封面与场景在同一 spring 上连续变形，
        // 不再是「封面独立 tween + 场景独立 tween」两套时钟。
        val portraitSize = minOf(maxWidth, portraitContentHeight - 92.dp).coerceAtLeast(1.dp)
        // ⚠ 横屏封面边长必须与场景层（主布局 + 天际 clip 源尺寸）**同一份**计算 ⇒ 抽到
        //   `meloXLandscapeArtworkSize` 单点维护（平板双栏要在同一公式里扣掉左列控制区）。
        val landscapeSize = meloXLandscapeArtworkSize(
            maxWidth,
            maxHeight,
            twoPane,
            window.ultrawidePlayer,
        )
        // ⚠ 竖屏几何快照必须**先于**图片请求声明（下面只用它兜底，不在本段写）。
        //   真正的锁存发生在本函数末尾、确认容器已是竖形之后。
        val portraitGeom = remember { PortraitArtworkGeom() }
        // 图片请求尺寸按「目标方向终值」取（不随动画每帧重取，避免转屏途中反复重载）。
        // ⚠⚠ 竖屏侧必须用**已锁存的** `portraitGeom.size`兜底，不能直接用本帧的 `portraitSize`：
        //   转屏起手那一帧 `BoxWithConstraints` 的约束可能**还是横屏的**，
        //   此时 `portraitSize = min(横屏宽, (横屏高 − 30 − 279) − 92)` 是负数 ⇒ 被
        //   `coerceAtLeast(1.dp)` 兜成 **1.dp** ⇒ 会真的发一个 1px 的图片请求，
        //   下一帧约束更新后再发正常尺寸 ⇒ 中途重载一次 = 转屏起手一记闪动。
        //   `portraitGeom.size` 只在「容器确实是竖的」时才被写入（见下方），恒为良好值。
        val fullArtworkSizeTarget = if (orientTransition.targetState) {
            landscapeSize
        } else {
            maxOf(portraitSize, portraitGeom.size)
        }
        // ★ Fix2：图片请求尺寸跨方向恒定（取两方向静止终值的较大者），转屏不再重建 ImageRequest
        //   ⇒ 本体 + 阴影副本两处 AsyncImage 零重载（去掉起手那一记解码 / 状态变更顿挫）。
        //   `baseEdge` 仍用 `fullArtworkSizeTarget`（终点边长）保持逐像素等价，两者用途分开。
        val fullArtworkRequestSizeDp = maxOf(portraitGeom.size, landscapeSize)
        val context = LocalContext.current
        val density = LocalDensity.current.density
        // ★ 2026-10-23（转屏卡顿主因之二，atrace 定位）：请求尺寸**量化到 64px 步长**。
        //   转屏期 `portraitGeom.size` 是**逐帧刷新**的快照（非 state，专门为了不触发重组），
        //   于是 `px = dp × density` 每帧都不同 ⇒ `remember(..., fullArtworkSizePx)` 每帧
        //   重建 `ImageRequest` ⇒ Coil 每帧重发请求 + 重解码 + 重算亮度
        //   ⇒ 两下转屏共 26~28 次 `RenderProxy#copyHWBitmapInto readback`
        //   （HARDWARE 位图像素回读，p50 7.4~8.8ms，最坏 90ms，且抢主窗口的 GPU）。
        //   量化后每 64px 才重建一次。请求本来就是 `Precision.INEXACT`、显示又是
        //   `ContentScale.Crop`，±64px 的解码目标差不可见；且**向上取整**保证永不小于显示
        //   尺寸（不会变糊）。
        val fullArtworkSizePx =
            ((fullArtworkRequestSizePx(fullArtworkRequestSizeDp.value, density) + 63) / 64) * 64
        val fullArtworkModel = remember(context, state.artworkUrl, fullArtworkSizePx) {
            state.artworkUrl?.takeIf(String::isNotBlank)?.let { artworkUrl ->
                ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(fullArtworkSizePx, fullArtworkSizePx)
                    .precision(Precision.INEXACT)
                    .build()
            }
        }
        // ★★ 2026-10-26（修「静止久了点 next 卡第一下」）：**预取下一首封面**。
        //
        //   实测（atrace，暂停态点 next，1440×3168@120Hz）点击后：
        //     · 第 1 帧主线程 16.29ms（animation 5.93 + traversal 10.31，其中 Record 7.22）；
        //     · 同时另一线程在解码 1216×1216 封面 **40.60ms**（副层再并行解一遍 38.17ms）；
        //     · 帧间隔 34.7ms（丢 4 帧），之后又有一帧 15.72ms —— 那是**解码完成的
        //       `onState` 回调**触发的第二记整窗重录（Compose 单 RenderNode：任何失效都
        //       重录 1440×3168 全屏，跟"改了多少"无关）。
        //   ⇒ 点击瞬间没有一帧是干净的：既在等解码，又在重录两遍。
        //
        //   预取后切歌时 Coil 内存命中：解码归零，且封面在**第一帧就 Success**
        //   ⇒ 第二记独立重录被合并掉。请求参数与 [fullArtworkModel] **逐项相同**
        //   （url / size / precision）—— 差一项就是另一个内存缓存 key，等于没预取。
        //
        //   ⚠ 只预取 next，不做 prev：反向会让常驻解码位图翻倍（每张 1216²≈5.9MB），
        //     而用户点 prev 的频次远低于 next。
        LaunchedEffect(state.nextArtworkUrl, fullArtworkSizePx) {
            val nextUrl = state.nextArtworkUrl?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
            if (nextUrl == state.artworkUrl) return@LaunchedEffect
            SingletonImageLoader.get(context).enqueue(
                ImageRequest.Builder(context)
                    .data(nextUrl)
                    .size(fullArtworkSizePx, fullArtworkSizePx)
                    .precision(Precision.INEXACT)
                    .build(),
            )
        }
        val artworkFooterHeight = 78.dp
        // ★ 竖屏**封面单独**上移量（用户反馈"封面太靠下"）。⚠ 只影响封面：歌名栏保持原位，
        //   所以**不要**在 `ArtworkDetailsWithoutArtwork` 里加等高 Spacer（那会把歌名栏一起顶上去）。
        val portraitY = (
            contentTop +
                (portraitContentHeight - portraitSize - artworkFooterHeight) -
                MeloXPortraitArtworkRaise
            ).coerceAtLeast(0.dp)

        // ★★ 位置改用「容器比例(center fraction)」表达，不用绝对 dp：转屏瞬间容器尺寸会突变，
        //   同一个 x/y 数字进入新容器后含义就变了 ⇒ 封面会先"跳"到错的位置再滑 = "不是直接飞入目标位"。
        //   竖屏封面水平居中(0.5)、横屏在左列中心(0.43÷2 = 0.215)；纵向中心比例实时锁存
        //   （横屏容器里算不出竖屏那一份，必须在竖屏时记下）。
        // ⚠ 竖屏几何用**非 state 快照**保存：在 composition 里写 `mutableStateOf` 会触发反复
        //   重组（弹簧每帧都在动）⇒ 封面抖动。非 state 写入不触发任何失效；Compose 跳过本
        //   scope 时自动保留上一份竖屏值，正是我们要的。
        //   （`portraitGeom` 本身已在图片请求那段声明，此处只做写入。）
        // ⚠⚠ 锁存条件是「**容器真的是竖的**」（`maxHeight > maxWidth`），**不能只看 `!isLandscape`**。
        //   两者不是同一时刻更新的：`isLandscape` 来自 `LocalWindowInfo.containerSize`
        //   （配置变更回调那一刻就翻），而 `BoxWithConstraints` 的 maxWidth/maxHeight 要等
        //   **下一次 measure** 才换成新值 ⇒ 中间存在一个 1 帧窗口：
        //   `isLandscape == false` 但容器约束**还是横屏的**。
        //   那一帧里 `portraitContentHeight = 横屏高 − 30 − 279` 已经是负数 ⇒
        //   `portraitSize = min(横屏宽, 负数 − 92)` 被 `coerceAtLeast(1.dp)` 兜成 **1.dp**，
        //   一旦写进锁存，封面就是**从 1dp 起飞**（整段动画尺寸全错）。
        //   真机上肉眼是否可见取决于「配置回调」与「measure」谁先谁后（不确定），
        //   而修掉它的代价只有半个 if —— 没有理由赌。
        if (maxHeight.value > maxWidth.value) {
            portraitGeom.size = portraitSize
            portraitGeom.centerYFrac = ((portraitY + portraitSize / 2).value / maxHeight.value)
                .coerceIn(0f, 1f)
        }
        // ⚠ 曲线选型（2026-10-09）：
        //   LinearEasing 匀速——无加减速，机械、硬；
        //   FastOutSlowInEasing 两头慢中间快——减速段被误判为卡顿；
        //   LinearOutSlowInEasing 匀速开始→减速结束——前 40% 才入速，飞入感弱；
        //   CubicBezierEasing(0.16,1,0.3,1)——前 16% 时间即冲到 100% 速度，
        //   后 70% 柔和减速落定。既有强启动感（飞），又不硬。
        // ⚠ 曲线选型（2026-10-23 改）：原 `CubicBezierEasing(0.16,1,0.3,1)` 是**强减速**——
        //   起手瞬间冲到满速（弹射感）、之后 70% 的时长慢爬到终点（拖尾），落点虽准但
        //   "前冲后拖"读起来就是生硬 / 不优雅。换成 **对称缓入缓出** `(0.33,0,0.2,1)`：
        //   起步零速度（不起跳）、中段最快、落定零速度（不拖尾）= 丝滑的"滑入滑出"。
        //   ⚠ 仍固定 300ms：与系统旋转动画同时起止，布局淡入淡出也走同一条主轴时钟，
        //     两者融合成一次运动（改时长会脱离系统旋转 ⇒ 封面比窗口晚停 = 更不丝滑）。
        // ★ Fix1（修「转屏封面百分百顿挫」根因）：orientProgress 等纯视觉量**只在绘制期读**，
        //   组合期不订阅 ⇒ BoxWithConstraints 不再每帧 re-subcompose / measure / place / 重录。
        //   曲线与场景层共用（对称缓入缓出 300ms）。
        val orientProgressState = orientTransition.animateFloat(
            transitionSpec = {
                tween(300, easing = CubicBezierEasing(0.33f, 0f, 0.2f, 1f))
            },
            label = "shared-artwork-orient-progress",
        ) { landscape -> if (landscape) 1f else 0f }

        // 横屏左列中心 0.215 随 columnCenterProgress 滑到屏幕中心 0.5（与场景层 Row 同解）。
        // ★ H 轮超宽车机档：恒左对齐，落点 = padding + 边长/2
        //   （[meloXUltrawideArtworkCenterXFrac]），**不再叠加任何居中位移** ——
        //   车机已改成「左列整块播放器 + 右列大字歌词常驻」，没有封面态可居中。
        val landscapeCenterXFracOf = {
            if (twoPane && window.ultrawidePlayer) {
                meloXUltrawideArtworkCenterXFrac(maxWidth, maxHeight)
            } else {
                0.215f + (0.5f - 0.215f) * columnCenterProgress()
            }
        }
        // 横屏纵向目标：手机/竖屏恒 0.5；★ H 轮车机档左列是「封面 + 下方控件块」
        //   整体垂直居中 ⇒ 封面中心不再是 0.5H，走 [meloXUltrawideArtworkCenterYFrac]
        //   （与 Scene 左列 Arrangement.Center 同解，否则封面会压在曲名行上）。
        val landscapeCenterYFracTarget = when {
            twoPane && window.ultrawidePlayer ->
                meloXUltrawideArtworkCenterYFrac(maxWidth, maxHeight)
            twoPane -> meloXTabletLandscapeArtworkCenterYFrac(maxWidth, maxHeight)
            else -> 0.5f
        }

        // ★ 冻结几何：布局只认「目标方向静止终值」，转屏途中恒定 ⇒ 零 measure/place。
        val frozenEdge = fullArtworkSizeTarget
        val endCenterXFrac = if (orientTransition.targetState) landscapeCenterXFracOf() else 0.5f
        val endCenterYFrac =
            if (orientTransition.targetState) landscapeCenterYFracTarget else portraitGeom.centerYFrac
        val frozenX = (maxWidth * endCenterXFrac - frozenEdge / 2f).coerceAtLeast(0.dp)
        val frozenY = (maxHeight * endCenterYFrac - frozenEdge / 2f).coerceAtLeast(0.dp)

        // 转屏进行中 且 不在开合 morph 中 ⇒ 冻结几何 + transform（morph 期 72dp↔全屏真在变，冻了错位）。
        val morphFreeze = expansionProgress().let { it > 0.01f && it < 0.99f }
        val orientationAnimating = orientTransition.currentState != orientTransition.targetState
        val orientationFrozen = orientationAnimating && !morphFreeze

        // ★ 转屏 transform（绘制期）：以 frozenEdge 为基准**关于中心缩放**，位移把中心送到逐帧
        //   目标中心。静止态 p=0/1 ⇒ s=1、translation=0，与旧 layout 逐像素等价。所有逐帧视觉量
        //   （orientProgress / columnCenterProgress / flightIntensity）只在 lambda 内读。
        // ★★ 2026-10-23 修「转屏进竖屏歌词页：封面先画成屏幕正中的大图、转屏结束才跳到左上角 72dp」：
        //   冻结几何这条快路径只插值了「横屏左列大封面 ⇄ 竖屏**居中**大封面」，把
        //   `pageFrameProgress`（竖屏非封面页 = 1 ⇒ 左上角 72dp 缩略图）整段**绕过了**
        //   （见上方 targetSize/targetX/targetY 的 `if (orientationFrozen)` 分支）。
        //   于是转屏那 300ms 里封面一直是"封面页姿态"，等 `orientationFrozen` 翻 false 的
        //   那一帧 layout 才切到 72dp ⇒ 用户看到的就是「中间一个大封面，然后（硬跳）到左上角」。
        //   ⇒ 修法：**竖屏端几何本身**按 pageFrame 混到 72dp 左上角，再与横屏端按 p 插值。
        //   · `pf` 只在绘制期读（`headerProgress` 是 State）⇒ 不触发重组 / 不重录；
        //   · `pf = pfPortrait × (1 − p)`：p=1 是横屏（横屏任何页都是左列大封面，pageFrame 恒 0），
        //     p=0 才是竖屏（取页面对应的终值）⇒ 横→竖 是「左列大封面 → 左上角缩略图」
        //     **一次飞到位**，竖→横 是反向，全程只有一段运动；
        //   · p 落到 0/1 时与静止 layout 的 lerpDp(.., 72.dp, pageFrameProgress) 逐像素等价
        //     ⇒ 落定零跳变。
        // ★ 转屏期封面缩放由下方 `orientTransform` 的 `scaleX/Y = s` 统一驱动；
        //   封面与阴影的 clip 角用固定 `targetRadius`，视觉圆角 = `targetRadius × s`，
        //   与盒子缩放同源（同一个 `s`），无需任何旁路快照 / 补偿字段。
        val orientTransform = Modifier.graphicsLayer {
            val p = orientProgressState.value
            val pfPortrait = if (page == MeloXNowPlayingPage.Artwork) 0f else headerProgress
            val pf = pfPortrait * (1f - p)
            // 竖屏端（p=0 侧）：缩略图落点必须与 layout 静止态同源（top = compactFrameTop、边长 72dp、
            // 水平贴左 ⇒ 中心 x = 36dp），否则收敛点会差几个 dp = 落定一记微跳。
            val portraitEdge = lerpDp(portraitGeom.size, 72.dp, pf)
            val portraitCx = lerpDp(maxWidth * 0.5f, 36.dp, pf)
            val portraitCy = lerpDp(maxHeight * portraitGeom.centerYFrac, compactFrameTop + 36.dp, pf)
            val basePx = frozenEdge.toPx().coerceAtLeast(1f)
            val edgePx = (portraitEdge + (landscapeSize - portraitEdge) * p).toPx()
            val flightIntensity = meloXFlightHump(p) * (1f - pageFrameProgress)
            val flightLiftScale = 1f + 0.09f * flightIntensity
            val s = (edgePx / basePx * flightLiftScale).coerceAtLeast(0.0001f)
            // ★ 2026-11-07 修「转屏中途封面圆角跳变」：旧方案把本帧 s 交给 clip 做 1/s 补偿
            //   （`meloXCompensatedCornerShape`）⇒ 依赖普通字段 `orientationScaleHolder.scale` 与
            //   此处 `scaleX/Y = s` 帧间同步，但嵌套 graphicsLayer 合成时序差导致失步、视觉圆角
            //   中途跳变（k20p 实证）。现改为：clip 用固定 `targetRadius`，缩放完全交给下方
            //   `scaleX/Y = s` 统一驱动 ⇒ 视觉圆角 = `targetRadius × s`，角与盒子缩放同源
            //   （同一个 `s`），严格同步、零跳变。封面与阴影共用此机制、共用固定角。
            scaleX = s
            scaleY = s
            val cxPx = (portraitCx + (maxWidth * landscapeCenterXFracOf() - portraitCx) * p).toPx()
            val cyPx = (portraitCy + (maxHeight * landscapeCenterYFracTarget - portraitCy) * p).toPx()
            translationX = cxPx - maxWidth.toPx() * endCenterXFrac
            translationY = cyPx - maxHeight.toPx() * endCenterYFrac - 6.dp.toPx() * flightIntensity
        }

        // ★ 修「歌词页打开时封面先到正中、展开完才跳左上角」：morph 期间封面必须停在**目标页**
        //   的静止位，而不是无条件钉死全屏居中。
        //   · 封面页（Artwork）：静止位 = 全屏居中 ⇒ destPageFrame = 0；
        //   · 歌词 / 队列页（竖屏）：静止位 = 左上角 72dp 缩略图 ⇒ destPageFrame = 1。
        //   morph 期间用 destPageFrame 取代 live pageFrameProgress，封面直接以目标页姿态进场
        //   （mini → 72dp 左上角 一镜到底）；morph 结束瞬间 live pageFrameProgress 已等于该值
        //   （page 打开前已从 playback_last_page 还原、headerProgress 到位），故无边界跳变。
        //   ⚠ 顺带根治早先「打开瞬间封面变小」：封面页打开时若 headerProgress 残留非 0，旧实现
        //   （morph 期间不钉死）会让封面以 72dp 姿态进场；钉死到目标页位即一并消除。
        //   ⚠ 仅在 morph（expansionProgress ∈ (0.01,0.99)）期间生效；收起 / 页间滑动
        //   （exp >= 0.99）仍走 live pageFrameProgress，切页滑动观感不变。
        //   ⚠ 横屏 / 平板双栏：歌词页仍是左列大封面（pageFrameProgress 恒 0，见本函数 997 行），
        //   destPageFrame 必须随之恒 0，否则会引入横屏「先 72dp 再跳全屏」新跳变。
        val destPageFrame = if (page == MeloXNowPlayingPage.Artwork || isLandscape) 0f else 1f
        val effectivePageFrame = if (morphFreeze) destPageFrame else pageFrameProgress
        // ★ live 布局（morph / 静止态）：仅当 orientationFrozen==false 才在组合期读
        //   `orientProgressState.value`；纯转屏期间不读 ⇒ 不触发每帧重组。
        val orientProgressVal = if (orientationFrozen) 0f else orientProgressState.value
        val fullArtworkSize = portraitGeom.size + (landscapeSize - portraitGeom.size) * orientProgressVal
        val centerXFrac = 0.5f + (landscapeCenterXFracOf() - 0.5f) * orientProgressVal
        val centerYFrac =
            portraitGeom.centerYFrac + (landscapeCenterYFracTarget - portraitGeom.centerYFrac) * orientProgressVal
        val fullX = (maxWidth * centerXFrac - fullArtworkSize / 2f).coerceAtLeast(0.dp)
        val fullY = (maxHeight * centerYFrac - fullArtworkSize / 2f).coerceAtLeast(0.dp)
        val targetSize = if (orientationFrozen) frozenEdge else lerpDp(fullArtworkSize, 72.dp, effectivePageFrame)
        val targetX = if (orientationFrozen) frozenX else lerpDp(fullX, 0.dp, effectivePageFrame)
        val targetY = if (orientationFrozen) frozenY else lerpDp(fullY, compactFrameTop, effectivePageFrame)
        // ★ 圆角从迷你侧 6dp 连续长到 12dp。封面 sharedElement 带
        //   `renderInOverlayDuringTransition = true` ⇒ 过渡期间由本函数这份副本渲染，
        //   源侧 clip 不参与。
        //
        //   ⚠⚠ 这里**必须直接用 `expansionProgress`，不做任何重映射/系数**
        //     （用户连续报障：23:19「值对了但还是突变」、23:25「手拖下滑圆角是直接变的」）。
        //     踩过的两条弯路，都源于试图"补偿"几何与 master 的时长差：
        //       ① 叠 `smoothStep` 二次缓动 → p 本身已是 S 曲线，中段被压得更急
        //          （每 30ms 增量 1.324 → 1.768dp），适得其反；
        //       ② 乘 `360/300 = 1.2` 对齐几何 → 只对**点击**（经 transitionSpec 缓动）
        //          有效；**手拖**是 `seekTo` 线性跟手、不过缓动，×1.2 立刻被 clamp
        //          ⇒ 拖前 17~40% 行程圆角冻在 12dp 不动、之后突降 = 「直接变」。
        //
        //   ⇒ 根治：已把 `MeloXArtworkBoundsTransform` 的时长从 300ms 改成与 master
        //     相同的 `meloXPlayerTransitionDurationMillis`（见该文件注释），
        //     **几何与圆角同源同速** ⇒ 这里直接用 `p` 即严格同步。
        //     同时点击路径与手拖路径**共用同一个表达式**，切换时零跳变。
        val targetRadius = lerpDp(6.dp, 12.dp, expansionProgress().coerceIn(0f, 1f))
        // ★ 转屏期圆角处理：冻结几何期间外层 `orientTransform` 统一缩放盒子，clip 用固定
        //   `targetRadius × artworkBoxScale`，视觉圆角 = `targetRadius × s`（角随盒子同源缩放、零跳变）。
        //   非转屏期同样走 `plainArtworkClip` ⇒ 与旧行为逐像素等价。
        //   ⚠ 本体与阴影层**共用这一个 Shape**：两者在冻结态的 layout 边长都是 `frozenEdge`
        //     （阴影的 `base` == `baseEdge` == `fullArtworkSizeTarget`），同一 Shape 给出的半径一致
        //     ⇒ 不会再出现「本体圆了、方形阴影还露直角黑边」。
        // ★ 关闭方向盒子缩到 `targetSize * artworkBoxScale`、内层不再缩放，可见圆角会被
        //   稀释（旧 = `targetRadius × effectiveScale`）。按 `artworkBoxScale` 同比补偿 clip 半径
        //   ⇒ 屏幕上圆角逐像素不变（打开方向 `artworkBoxScale=1` ⇒ 等同旧）。
        val plainArtworkClip = remember(targetRadius, artworkBoxScale) {
            RoundedCornerShape(targetRadius * artworkBoxScale)
        }
        // ★ 转屏冻结期**不再做 1/s 圆角补偿**（旧 `meloXCompensatedCornerShape`）：
        //   补偿依赖普通字段 `orientationScaleHolder.scale` 与外层 `orientTransform` 的
        //   `scaleX/Y = s` 帧间同步，但嵌套 `graphicsLayer` 的合成时序差导致二者失步 ⇒
        //   视觉圆角在转屏中途跳变（k20p 实证：中段圆角近乎直角、稳定后恢复）。
        //   固定角 + 外层统一缩放后，视觉圆角 = `targetRadius × s`，角与盒子缩放**同源**
        //   （同一个 `s`），严格同步、零跳变；角随封面等比缩放，观感自然（同迷你栏）。
        val artworkClipModifier = Modifier.clip(plainArtworkClip)
        // ⚠ 必须与外壳 / 迷你栏侧用**同一个** `sinkPeakPx`（同一个下沉量），
        //   否则封面那个独立 overlay 会与外壳沉不同的量 ⇒ 封面在栏内错位。
        val artworkSinkPeakPx = with(LocalDensity.current) { MeloXPlayerSinkPeakDp.dp.toPx() }
        // ⚠ 与外壳同一条纪律：方向走 lambda、实例 key 不含方向（手势越阈值不重建）。
        //   ⚠⚠ 判据必须与外壳**逐字同义**（只认 `closingIntent`，不含 `targetState` 兜底，
        //   原因见上方 `isHostExiting` 的 2026-10-06 说明）—— 外壳与封面必须**同时沉 /
        //   同时不沉**，否则封面会在迷你栏内错位。
        val artworkSinkEnabledState = rememberUpdatedState(closingIntent)
        val artworkBoundsTransform = remember(artworkSinkPeakPx) {
            // ⚠ 这是**封面** morph，不是外壳 ⇒ `publishShellGeom` 保持默认 false。
            //   若它发布 bounds，覆盖判据的坐标系自检会每帧失败并 `reset()`
            //   （2026-10-26 实测 351 次翻转的根因就是这里）。
            meloXPlayerSharedBoundsTransform(
                sinkPeakPx = artworkSinkPeakPx,
                // ★ 2026-10-28 22:0x：同 host 外壳侧，用 `targetState == PostExit` 兜底抗首帧竞态
                //   （封面 morph 与外壳共用方向，否则封面会与外壳错拍）。
                sinkEnabled = { artworkSinkEnabledState.value },
            )
        }
        val sharedArtworkModifier = with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(
                    key = sharedPlayerArtworkKey(state.mediaId),
                ),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = artworkBoundsTransform,
                renderInOverlayDuringTransition = true,
                zIndexInOverlay = 100f,
            )
        }
        // ★★ 天际歌词「一镜到底」源端（2026-10-06 修「双封面」）：横屏歌词页把**持久封面自己**
        //   注册为 `np-skyline-artwork` 源 —— 天际目标层（场景里 132dp）进入/退出时，封面从
        //   左列大封面直接飞入/飞回天际中央。不再需要场景层自绘等位副本（那种副本与持久封面
        //   同帧叠画 = 用户报障的「动画封面盖在一张一模一样的静态封面上」，转屏进横屏歌词同理）。
        //   ⚠ 只在横屏歌词页注册：竖屏时本节点是 72dp 缩略图、队列/封面页没有天际目标，
        //   注册了也只是 inert；且避免在 mini-morph（mini key）之外无谓常驻第二个 key。
        //   ⚠ 本节点的 `graphicsLayer{alpha}` 会被 overlay 飞行副本继承（hidden 瞬时 0 会把
        //   飞行中的封面一起变透明）⇒ alpha 已上移到外层包装 Box（见下方渲染结构）——
        //   祖先 alpha 不作用于 overlay 副本，原地隐藏与实心飞行两者兼得。
        // ⚠ 判据必须与场景侧（天际层可见性 / 内容淡出）**逐字同源** —— 走
        //   [meloXSkylineCanRender]，别写死 `page == Lyrics`：否则平板双栏从封面态
        //   开天际时**没有源**，封面会凭空出现而不是飞过去。
        val skylineSourceModifier = if (isLandscape && meloXSkylineCanRender(page, twoPane)) {
            with(sharedTransitionScope) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState("np-skyline-artwork"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    // ★ 显式 tween（Apple easing + 设置时长）：默认 boundsTransform 是渐近
                    //   弹簧（~480ms 才稳），与两端 AV 的 360ms 淡入淡出脱拍 ⇒ 飞行拖尾 +
                    //   交接硬切 = 用户报的「没有一镜到底感」。
                    boundsTransform = MeloXSkylineArtworkBoundsTransform,
                    renderInOverlayDuringTransition = true,
                    zIndexInOverlay = 100f,
                    // ★ 圆角插值：`sharedElement` 不插值两端 clip 圆角，12dp（本端全展开态）
                    //   ⇄ 20dp（天际 132dp 端）会在飞行起止瞬切 = 用户报的「圆角速变」。
                    //   OverlayClip 每帧按当前 bounds 宽度反推进度、连续 lerp 圆角。
                    clipInOverlayDuringTransition = meloXSkylineArtworkOverlayClip(
                        sourceRadius = targetRadius,
                        targetRadius = 20.dp,
                        sourceSize = landscapeSize,
                        targetSize = 132.dp,
                    ),
                )
            }
        } else {
            Modifier
        }

        // ★ alpha 必须挂在共享元素节点的**祖先**上（2026-10-06）：overlay 飞行副本只重放
        //   节点自身的绘制——节点自有 `graphicsLayer{alpha=0}` 会把「飞行中的共享元素」
        //   一起变透明（天际 morph 会整段消失）；祖先 alpha 不作用于 overlay 副本 ⇒
        //   「原地隐藏」与「飞行实心」兼得。手势/命中不受 alpha 影响（gesturesEnabled
        //   在调用点已单独门控 skyline）。内层 Box 的 offset/size 仍是共享元素边界，
        //   坐标系与外层 fillMaxSize 同原点 ⇒ 位置不变。

        // ★★★ 封面本体与阴影层**共用**的这一份 transform。
        // 抽出来的唯一理由：阴影层是独立子节点，若本体自己带 layer、阴影不带，
        // 本体一缩/一移/一淡就与阴影脱节 ⇒ 露馅（用户 2026-10-07 报障）。
        // ⚠ 用 `remember` 之外不可缓存 —— 里面读的是每帧变化的 spring 值，必须每帧重算。
        // ★ Fix1：飞行驼峰（flightLiftScale / flightIntensity）已上移到 `orientTransform`
        //   （绘制期，关于中心缩放 + 上抬），由它统一施加到本体与阴影，两者严格同步。
        //   ⚠ 此处绝不可再叠飞行：否则转屏时「内层 flight + 外层 orient」双重缩放，封面会
        //     放大过头 / 上抬错位，与场景层脱节。
        val artworkTransform = Modifier.graphicsLayer {
            val bounce = switchBounce.value
            // 0 → 起点（缩小 + 半透明），1 → 原样；弹簧过冲段 bounce > 1 ⇒ 略微放大。
            val bounceScale = MeloXArtworkSwitchBounceScaleFrom +
                (1f - MeloXArtworkSwitchBounceScaleFrom) * bounce
                // ★ `effectiveScale` 在关闭方向已搬到盒子「测量尺寸」（见 `artworkBoxScale` 注释），
            //   此处内层只乘 `artworkInnerScale`（关闭=1、打开=effectiveScale）⇒ 视觉缩放恒 =
            //   `effectiveScale × bounce`、与改动前逐像素等价；且不与盒子缩放双重叠加。
            val s = artworkInnerScale * bounceScale
            scaleX = s
            scaleY = s
            // alpha 只在前 40% 行程回到 1：把硬切磨柔，不是淡入淡出。
            alpha = MeloXArtworkSwitchBounceAlphaFrom +
                (1f - MeloXArtworkSwitchBounceAlphaFrom) *
                (bounce / MeloXArtworkSwitchBounceAlphaSpan).coerceAtMost(1f)
            translationX = artworkDrag.value
            translationY = 0f
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (hidden || transitionAnimationEligible) 0f else 1f
                },
        ) {
            // ★★ 2026-10-24（用户报障：从迷你栏打开播放页时，封面外圈套着一大块模糊影，
            //   只在封面页出现、只在开页动画途中，落定后正常）。
            //   根因：下面 `meloXArtworkShadowLayer` 的 `edge`（决定 blur 半径与外扩 pad 的
            //   几何基准）传的是 **`targetSize`**，而开页 morph 期封面本体被 sharedBounds
            //   **`RemeasureToBounds` 重新测量成了小尺寸** ⇒ 本体小、阴影的外扩量/模糊半径
            //   仍是满尺寸 ⇒ 小封面外圈套一大块模糊影（真机录像：模糊块比封面大一圈、上溢
            //   到页面顶部圆角处）。
            //   ⇒ morph 期把**实测布局边长**喂给 `edge`：阴影层的冻结机制（`baseEdge` +
            //     `graphicsLayer{scale}`）会按 `edge/baseEdge` 等比缩放，几何随之对齐，
            //     且**不重栅格**。静止态实测边长 == `targetSize` ⇒ 逐像素等价；
            //     转屏仍走 `frozenEdge`（那条口径不变）。
        // ★ 关闭方向把封面「测量尺寸」钉到视觉尺寸（见上方 `artworkBoxScale` 注释）：
        //   盒子尺寸 × 居中补偿偏移 = 旧 `targetSize` 的**中心**，内层只留 bounce ⇒ 测量矩形
        //   = 视觉小封面，morph 源不再瞬间弹回满尺寸。打开方向 `artworkBoxScale=1` ⇒
        //   偏移为 0、尺寸 = `targetSize`，等同旧行为。
        val artworkBoxSize = targetSize * artworkBoxScale
        val artworkBoxOffset = (targetSize - artworkBoxSize) / 2f
        Box(
            modifier = Modifier
                .offset(x = targetX + artworkBoxOffset, y = targetY + artworkBoxOffset)
                .size(artworkBoxSize)
                .then(skylineSourceModifier)
                .then(sharedArtworkModifier)
                // ★ Fix1：转屏冻结几何时挂 `orientTransform`（在 sharedArtworkModifier **之后**、
                //   内层），由它做逐帧缩放/位移；sharedElement 边界保持冻结尺寸，morph 期
                //   orientationFrozen=false 不挂 ⇒ 飞行/缩放与场景层严格同步、不脱节。
                .then(if (orientationFrozen) orientTransform else Modifier)
                .then(artworkGestureModifier),
            ) {
                // 开合 morph / 下拉手势期把装饰性模糊副本按**进度**淡入淡出（见下方 `strength` 注释）。
                // ★ 2026-10-24 第二版：不再用 `animateFloatAsState`（时间动画），改成**纯进度函数**
                //   在绘制期读（`strengthScale` provider）。原因：时间动画与手指脱钩 ——
                //   下滑跟手时页体立刻缩、模糊影却要等 260ms 才退；松手回弹时同理，
                //   用户报「下滑再松手时，模糊消失和出现也很突兀难看」。
                //   改成 p 的函数后：手到哪模糊就到哪（跟手），且**零重组**。
                //★★★ 阴影与本体**必须共用同一份 transform**（2026-10-07 修「封面下面放缩略图，
                //   一旦封面变小或移动就会露馅」）。
                //   踩过的坑：阴影层原本是**独立子节点**，而本体的
                //   `liftScale`（effectiveScale × flightLiftScale × bounceScale）、切歌 alpha、
                //   `translationX/Y` 全挂在 `Artwork` **内部**的 graphicsLayer 上
                //   ⇒ 切歌回弹 / 转屏主角飞行 / 拖拽时本体动、阴影不动 ⇒ 直接露馅。
                //✅ 正解：抽一份 `artworkTransform`，本体与阴影层**都挂它**。
                //   · 两者尺寸/中心一致（同为 fillMaxSize / matchParentSize）
                //     ⇒ 同一个 layer 变换对二者产生完全相同的位移与缩放。
                //   · `alpha` 也在这份里 ⇒ 切歌淡入时阴影同步淡入，不会残留。
                //   · overlay 飞行副本会重放整个 sharedElement 子树，阴影层与本体一起被重放
                //     ⇒ 相对关系不变、不会重复或错位。
                // ⚠ 阴影层**不再自带 lift**：本体抬起时阴影必须跟着抬，
                //   「阴影留在原地」这种拟物偏移在跟随架构下就是错的（那正是露馅的来源）。
                meloXArtworkShadowLayer(
                    url = state.artworkUrl,
                    // ★ 2026-10-25：副层**自加载**（与本体同一 `model`，Coil 缓存命中、各自
                    //   持有有效引用）。⚠ 不要传本体抽出的位图 —— 池化位图长期持有会被回收，
                    //   画出来是黑的（详见函数形参注释）。
                    model = fullArtworkModel,
                    // 稳态 = 1；切页 / 转屏途中按 pageFrameProgress 淡出；
                    // 「播放中」比「暂停」略强，与 AM 的呼吸感一致。
                    //
                    // ★★ 2026-10-24 修「歌词/队列页封面缩到左上角 72dp 后，四角有明显阴影」：
                    //   真机截图复现：缩略图四周一圈暗晕、四角最重（就是本阴影副本）。
                    //   旧系数 `(1 - pageFrameProgress * 0.8f)` 在缩略图态还剩 0.2 强度，而 halo 的
                    //   blur / pad 按 2.5% / 1.6× 随边长**等比**缩小 ⇒ 相对观感与全尺寸封面一样重，
                    //   在深色播放页背景上读作「四角一圈黑影」。AM 口径：这份同色模糊副本是
                    //   **大封面**的立体感；header 行的 72dp 缩略图是平的（ShadowImageView 的
                    //   blurredImageView 只属于大图场景）。
                    //   ⇒ 改为 smoothstep 淡出：pageFrame ≥ 0.95（缩略图落定）强度归零，
                    //     封面缩小过程里平滑消退（非突变）；封面页（pageFrame = 0）淡出系数 = 1
                    //     ⇒ 与旧值逐像素等价；横屏 pageFrameProgress 恒 0 ⇒ 不受影响。
                    // ★★ 2026-10-24（用户报障：从迷你栏**打开**播放页时，小封面外圈铺着
                    //   一大块放大模糊的封面，上溢到页面顶部；只在封面页、只在开页动画途中、
                    //   落定后正常）。
                    //   试过让 `edge` 跟随实测边长（见下方 `edge` 参数），**实测无效**：
                    //   这一层的渲染矩形由自己的 `edge + 2*pad` 决定，而 morph 期封面本体
                    //   是被 sharedBounds 缩放/重测的 —— 两套几何不同源，改 `edge` 拿不到
                    //   那个"当前可见尺寸"。
                    //   ⇒ 改为**在开合 morph 期间淡出这份装饰性副本**（它只是大封面的
                    //     立体感 / AM 的 blurredImageView），落定后再淡回来。
                    //   ⚠⚠ 第一版写成 `if (morphFreeze) 0f` 硬切，用户当场否掉：
                    //     「出现模糊有点突兀，不够优雅」—— 落定那一帧模糊影凭空冒出。
                    //     ⇒ 必须**缓入**：`animateFloatAsState` + 260ms FastOutSlowIn 过渡，
                    //       morph 结束时模糊是从 0 慢慢长回满强度的，读作封面逐渐有了立体感。
                    //   ⚠ 判据仍是**离散** `morphFreeze`（p∈(0.01,0.99)，全程只翻两次）；
                    //     只有动画自身的 260ms 过渡会重组这一小棵子树（开/合各一次），
                    //     不在组合期读逐帧动画值。静止态系数恒 1 ⇒ 与改前逐像素一致。
                    // ★ 上游口径：模糊影强度 = `fullScreenScaleBlend`（≈ expansionProgress 在
                    //   0.30→0.88 的 smoothStep）×（1 − 封面页占比 × 0.8）× 播放/暂停强度。
                    //   `fullScreenScaleBlend` 由本体共用 ⇒ 模糊层与封面**严格同步**缩放/淡入，
                    //   全程跟随 sharedElement 飞行（不冻结、不额外淡出），即上游打开动画
                    //   「同色模糊副本跟手放大」的观感。
                    strength = fullScreenScaleBlend *
                        (1f - pageFrameProgress * 0.8f) *
                        artworkShadowStrength,
                    // ★ 开合 morph 期间提前组合 ⇒ 图像 / 亮度在窗口打开前就绪，消除「进入动画时模糊闪烁」。
                    preload = morphFreeze,
                    transform = artworkTransform,
                    // ★ Fix1：转屏冻结时阴影几何也钉死在 `frozenEdge`（== fullArtworkSizeTarget），
                    //   由外层 orientTransform 统一缩放；仅 morph 期（orientationFrozen=false）
                    //   走 live `targetSize` 跟踪 72dp↔全屏。
                    // ★ 2026-10-24：morph 期用**实测边长**，否则阴影会比本体大一圈（见上方
                    //   `liveArtworkEdgePx` 注释）。静止态实测边长 == `targetSize` ⇒ 等价。
                    // ★ 2026-10-25（修「进入动画时封面旁模糊闪烁」）：`edge` 原先只在
                    //   `morphFreeze`（p∈(0.01,0.99)）期间用实测边长、p 一过 0.99 就切回
                    //   `targetSize`。而这个「切源」的帧恰好落在 `strengthScale` 刚升满
                    //   （p=0.99 ⇒ 模糊影满强度）那一帧 —— 只要此刻实测边长与 `targetSize`
                    //   还差一点点，满强度的模糊影就会瞬间缩/张一下，肉眼就是「闪烁」。
                    //   ⇒ 只要量到过实测边长就一直用它（静止态实测边长 == `targetSize`，
                    //     逐像素等价），彻底消除这个切源动作。
                    // ★ 上游口径：模糊层几何基准恒为封面静止边长。
                    //   视觉缩放/位移全部走 `artworkTransform`（与本体同一份），
                    //   严格跟随 sharedElement 飞行 morph；不再用实测布局边长
                    //   （会让 morph 期模糊层比封面大很多 —— 用户复报）。
                    // ★ 关闭方向盒子已缩到 `targetSize * artworkBoxScale`，阴影几何须同缩
                    //   否则模糊层会比封面大一圈（关闭瞬间露出大光晕）。打开方向
                    //   `artworkBoxScale=1` ⇒ 等同旧 `targetSize`。
                    edge = (if (orientationFrozen) frozenEdge else targetSize) * artworkBoxScale,
                    // ★ 冻结基准 = **目标方向的静止封面边长**（不随动画每帧变，只在转屏翻向时
                    //   变一次）。静止态 `targetSize == 它` ⇒ s = 1 ⇒ 逐像素等价。
                    baseEdge = fullArtworkSizeTarget,
                    // ★ 冻结判据（2026-10-22 修正）：
                    //   旧 `effectivePageFrame < 0.5f` 只在**封面页**冻结，歌词/队列页
                    //   （pageFrameProgress=1）转屏时 `freezeEligible=false` ⇒ 阴影每帧重栅格 +
                    //   重建 RenderEffect ≈ +1ms/帧 —— 正是「转屏封面移动卡顿」残余来源之一。
                    //   ✅ 新判据：页面处于**稳定态**（不在 morph 中）即冻结（任意方向、任意页，
                    //   含歌词页稳定态与 72dp 缩略图态——缩略图态几何补偿后逐像素等价，见函数体），
                    //   仅「歌词/队列页 morph（72dp↔全屏）」期间放开跟踪（那一段封面尺寸真在变）。
                    //   封面页 morph 仍走旧逻辑冻结（effectivePageFrame<0.5），避免 sharedElement
                    //   飞行与阴影脱节。净效果：转屏全程（无论停在哪一页）阴影恒定，不再逐帧重栅格。
                    freezeEligible = !morphFreeze || effectivePageFrame < 0.5f,
                    cornerRadius = targetRadius,
                    // ★ 阴影圆角不再做 1/s 补偿（同封面）：固定 `targetRadius`，由外层统一缩放，
                    //   视觉角 = `targetRadius × s`，与封面严格同源、零跳变；直角黑边问题已由
                    //   2026-11-06 回退补偿缩放壳解决，与此无关。
                )
                Artwork(
                    url = state.artworkUrl,
                    model = fullArtworkModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(artworkTransform)
                        .then(artworkClipModifier),
                )
            }
        }

        // Transition overlay: dual cover slide animation
        if (state.isInTransition && transitionAnimationEligible) {
            TransitionOverlay(
                state = state,
                fullArtworkSize = fullArtworkSize,
                targetX = targetX,
                targetY = targetY,
                targetRadius = targetRadius,
            )
        }
    }
}

internal fun fullArtworkRequestSizePx(fullArtworkSizeDp: Float, density: Float): Int =
    (fullArtworkSizeDp * density).roundToInt().coerceAtLeast(1)

/**
 * 竖屏封面几何的**非 state 快照**（写它不触发重组，避免弹簧每帧重组导致的抖动）。
 * 仅存竖屏时才更新；横屏期间保留上一份竖屏值，作为转屏动画的"出发点"。
 */
/**
 * 竖屏**封面**的上移量（歌名栏不动，只抬封面）。
 *
 * 调参历史（真机量测，播放态）：
 * - `0.dp`  ⇒ 封面顶 ≈139dp（几乎贴住歌名栏）→ 用户「封面太靠下」
 * - `60.dp` ⇒ 封面顶 ≈79dp（离 grabber 仅 38dp）→ 用户「封面太靠上」
 * - `30.dp` ⇒ 封面顶 ≈109dp（上方 68dp / 与歌名间隙 ~36dp）—— 取中间值
 *
 * ⚠ 只作用于 `SharedArtworkDestination` 的 portraitY：**歌名栏保持原位**（用户明确要求歌名栏
 * 不跟着上移），所以 `ArtworkDetailsWithoutArtwork` 里**不能**再加等高的 Spacer。
 */
internal val MeloXPortraitArtworkRaise = 30.dp

/**
 * ★ 封面立体感 = **封面图自身的模糊副本**（Apple Music 口径），不是黑色暗影。
 *
 * ## 原版是什么样（2026-10-07 从 git HEAD 抽出来核对过）
 * 原版只有**一层**平台 elevation shadow：
 * `.shadow(elevation = shadowElevation * fullScreenScaleBlend * (1 - pageFrameProgress*0.55) + flightLiftShadow,
 *   shape = RoundedCornerShape(targetRadius), clip = false,
 *   ambientColor = Black 0.28, spotColor = Black 0.28)`，
 * 其中 `shadowElevation = animateDpAsState(if (isPlaying) 26.dp else 14.dp, spring ζ=0.92 k=320)`。
 * ⛔ 别再叠这层：它是纯黑投影，和下面的同色副本是两种不同的影，叠起来必然发脏。
 * 它的 spring 呼吸动画保留下来，改成驱动副本的 alpha（见`artworkShadowStrength`）。
 *
 * ## ★★ 架构铁律：阴影与本体必须共用同一份 transform
 * 阴影层是本体的**兄弟子节点**。若本体自己带 `graphicsLayer`（缩放/位移/alpha）而阴影不带，
 * 那么切歌回弹、转屏「主角飞行」、横向拖拽时**本体动、阴影不动**⇒ 直接露馅
 * （用户报障：「封面下面放缩略图，一旦封面变小或移动就会露馅」）。
 * ✅ 正解：调用点抽一份 `artworkTransform`（含 `liftScale`、切歌 alpha、`translationX/Y`），
 *   本体与阴影层**都**挂它；阴影层内部只保留自己的 alpha / 压暗 colorFilter。
 * ⛔ 因此阴影层**不允许**再有任何独立位移——包括原版的 `flightLiftShadow`（已删）。
 *   「本体抬起时阴影相对下沉」这类拟物偏移，在跟随架构下就是露馅本身。
 *
 * ## 证据（社区两个独立复刻，口径完全一致）
 * - `olddonkey/ShadowImageView`（README 自述 "A apple music cover picture shadow style
 *   image library"）：模糊的是 **`blurImageLayer.contents = image.cgImage`（同一张封面）**，
 *   `blurRadius` 默认 **3**（注释明确「2 or 3 for most cases」，**不要调大**），
 *   `blurredImageView.alpha = shadowAlpha`，`sendSubview(toBack:)` 垫在本体下面。
 * - `PierrePerrin/ShadowView`（PPMusicImageShadow，"imitates in real time the shadow
 *   blurred effect of iOS Music App"）：同一思路。
 *
 * ⇒ AM 的观感来自「一层和封面**同色**、很轻的高斯模糊副本」，**不是**一圈黑色。
 *
 * ## 源码真实 pipeline（照 ShadowImageView.swift，不用猜）
 * 1. `containerLayer.frame = realImageSize * 1.4`（透明容器，比图大 40%）；
 * 2. `blurImageLayer.frame = (0.2·w, 0.2·h, realImageSize)` —— **图在容器里居中、
 *    尺寸等于原图大小**，按 `imageCornerRaidus` 裁圆角；
 * 3. `containerImage.resized(withPercentage: 0.2)` —— **整张容器缩到 20%**；
 * 4. 对这张 20% 小图做 `CIGaussianBlur(radius = 3)`；
 * 5. 结果塞进 `blurredImageView`（frame = `realImageSize * 1.4`，居中，`alpha = shadowAlpha`）。
 *
 * 关键换算：blur 半径 3 作用在 **0.2× 尺寸**的图上，再放大 5 倍回到 1.4× 容器
 * ⇒ 等效到本体边长 = 3 / 0.2 / 1.4 ≈ **1.07%** ⇒ AM 是**极窄的一圈淡晕**。
 *
 * ## 前三版为什么都不对（用户三次报障）
 * - **v87**：`BlurMaskFilter` 画一圈**外扩 20dp 的黑色**圆角矩形，**同时**留着原elevation
 *   shadow ⇒ **三层黑叠在一起**，生硬发脏。
 * - **v92**：换成模糊副本，但①**压暗过头**（自己加 RGB × 0.42，源码里**零 colorFilter**
 *   —— `shadowColor`/`shadowSaturation` 是**另一个库** PierrePerrin/ShadowView 的参数）
 *   ②**把图本身放大 1.22×**（源码放大的是**容器**，图该保持原尺寸）⇒ 深色封面糊成黑边。
 * - **v93**：去掉了压暗，但加了 `scale = 1/1.4` —— **副本被缩得比本体还小**，
 *   被本体完全盖住 ⇒ 露出的量≈0，等于没有阴影。
 *
 * ## 现在的做法
 * 副本**原尺寸**居中（和本体一样大）→ blur（半径 = 边长 × 2.5%，比源码 1.07% 略宽
 * 让真机上看得出）→ 只设 alpha。露在本体外的就是 blur 自然溢出的一圈。
 * ⚠ blur 的 RenderEffect **裁到所在 layer 边界** ⇒ 承载 blur 的节点每边要留
 *   `blurRadius × 1.6` 的余量，否则溢出被裁成硬边（v87 的教训）。
 * ⚠ 边长必须在**外层 wrapper**（`matchParentSize`）上量：在副本自己身上量会自反馈放大。
 *
 * ⛔ `Modifier.blur` 在 API < 31 是 no-op（`minSdk 26`），那时会退化成一圈生硬的
 *   封面边 ⇒ 低版本整个阴影层不画。
 */
@Composable
private fun BoxScope.meloXArtworkShadowLayer(
    url: String?,
    // ★ 2026-10-25：**自加载**（`model ?: url`，与上游一致）。⚠ 不要改成「复用本体的位图」：
    //   Coil `result.image.bitmap` 是**池化位图**，抽出来由本层长期持有后会被回收 ⇒ 画出来是黑
    //   （2026-10-25 实测：封面外圈光晕由泛白变黑）。自加载让 Coil 按 request 缓存命中同一份
    //   解码（与本体不重复算），又由本层这份 painter 持有有效引用。`preload` 让它在 morph 期
    //   提前组合、在淡入窗口打开前就开始加载（防「进动画时模糊闪」的那一份仍有效）。
    model: Any?,
    strength: Float,
    // ★★ 2026-10-25（修「进入动画时封面旁模糊闪烁」）：开合 morph 期间**提前组合**这份
    //   装饰副本 —— 即使此刻 `strength`（alpha）还是 0。否则它要等 alpha 越过 0.004
    //   （开页 ~p 0.32）才首次组合，其 AsyncImage 与「平均亮度」采样都要到那时才开始，
    //   图像 / 压暗量有可能在模糊影淡入窗口（p∈[0.90,0.99]）**打开之后**才到达 ⇒ 读作
    //   进入动画里的一记闪烁 / 爆闪。提前组合后，图像与亮度在窗口打开前就已就绪。
    //   非 morph 期（静止 / 歌词页 72dp 缩略图）仍按原 `alpha` 门槛跳过组合，零额外开销。
    preload: Boolean = false,
    // ★ 与本体共用的 transform（见调用点）：保证阴影和封面永远同步缩放/位移/淡入。
    //   ⛔ 不要在这里再加任何独立位移或缩放，那会让两者脱节。
    transform: Modifier,
    // ★ 封面边长，**由调用点直接传入**（2026-10-07 修「转屏末帧抖一下」）。
    //
    //   旧实现用 `onSizeChanged` 测：`mutableFloatStateOf` 的写入发生在**布局之后**，
    //   下一次重组才生效 ⇒ 转屏途中每一帧封面都在变尺寸，而阴影的 `edge`/`blurRadius`/
    //   `pad` 永远慢**一帧**。弹簧收尾时相邻两帧的尺寸差本来已经很小，慢一帧等于把
    //   差值放大到肉眼可辨 ⇒ 阴影的模糊半径与外扩量在末帧「跳」一下再归位，
    //   读作封面抖动（本体其实没动，抖的是套在它外面那圈影）。
    //
    //   ⚠ 与本体的尺寸同源（`Box.size(targetSize)` + 副本 `fillMaxSize()`），
    //   且不含自反馈（副本尺寸依赖 edge，但 edge 来自外部常量，不依赖副本自身测量）
    //   ⇒ 直接传值在几何上等价，却把「慢一帧」彻底消除。
    edge: Dp,
    // ★★ 冻结基准边长（2026-10-23，修「转屏封面百分百顿挫」）。
    //
    //   实测（atrace 逐帧，36 帧转屏 / 22 个 drawLayer）：**只有封面这一块方层每帧变尺寸**
    //   （1267→1261→1256→…→1204，步长 1~6px），其余 21 层全程恒定。按「方层是否变化」分组：
    //   变化帧 Drawing 5.85ms / flush 2.31ms，未变帧 4.81ms / 1.75ms
    //   ⇒ 阴影层每帧重栅格 + 重算 RenderEffect ≈ **+1.0ms/帧**（8.33ms 预算的 12%，
    //   正好把一帧推过 60Hz 的 16.67ms 线 ⇒ 滑到 3 个 vsync = 用户看到的顿挫）。
    //
    //   根因：`edge` 由 `fullArtworkSize` 每帧线性插值驱动，而 `blurRadius = edge * 2.5%`
    //   与节点边长 `edge + 2*pad` **同时**每帧改 ⇒ RenderEffect 每帧重建 + 整层重栅格。
    //
    //   修法（数学等价）：模糊半径与节点边长都是 `edge` 的**线性函数** ⇒ 把内层几何钉死在
    //   基准边长 `baseEdge`，外层用 `graphicsLayer{scale}` 缩放补偿：
    //     s·blur(base) = blur(edge) ✓   s·(base+2pad) = edge+2pad ✓   s·base = edge ✓
    //   ⇒ 整个过程阴影层**永不重栅格**，退化成一枚被 GPU 缩放的静态贴图。
    //   ⚠ `baseEdge` 传「目标方向的静止封面边长」（`fullArtworkSizeTarget`，Dp）⇒ 静止态
    //     s 恰好 = 1，**逐像素等价**（不只是近似）。
    //   ⚠ 转屏途中 `baseEdge` 会随方向翻一次（1 次重栅格，落在系统重配置那一帧上）。
    //   ⚠ 开合 morph 阶段收益为 0 —— 那里 `strength`（alpha）本身每帧在动，父层无论如何
    //     都要重算模糊。本条只针对「alpha 恒定」的转屏。
    baseEdge: Dp,
    // ★★ 是否允许冻结（2026-10-23 修「冻结实测没生效」）。
    //   旧门限 `edge >= restEdge*0.72` 依赖 `restEdge`（= `fullArtworkSizeTarget`，又依赖
    //   `portraitGeom.size` 这份快照）。若转屏起手那帧容器还是横屏、或 App 从横屏冷启，
    //   `portraitGeom.size` 没锁存好（甚至还是初值 0.dp）⇒ `restEdge` 退化 ⇒ 门限判错 ⇒
    //   冻结整段失效 ⇒ 阴影层每帧重栅格（atrace 实测 1382→1322→…，+1ms/帧 = 顿挫）。
    //   改用**直接判据**：封面接近全尺寸（`effectivePageFrame` 小）时必冻结；缩略图态
    //   （歌词/队列页 72dp，`effectivePageFrame→1`）必不冻结（否则大模糊层缩到 17.6%，
    //   栅格面积 ~20× = 性能倒退）。这个值不依赖任何快照，恒正确。
    freezeEligible: Boolean,
    cornerRadius: Dp,
) {
    val alpha = strength.coerceIn(0f, 1f)
    // ⚠ `Modifier.blur` 走 RenderEffect，只在 API 31+ 生效；本机 minSdk 26。
    //   低版本上模糊是 no-op ⇒ 副本会退化成一圈**生硬的封面边**（比没有更丑），
    //   所以那种情况下整个阴影层直接不画。
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    if ((alpha <= 0.004f && !preload) || !supported) return

    // ★ 裁剪：固定 `cornerRadius`，缩放由外层统一驱动 ⇒ 本体与阴影的视觉圆角恒同源同值。
    val clipModifier = Modifier.clip(RoundedCornerShape(cornerRadius))

    // ★★ 2026-11-06：**回到上游几何** —— 不做「冻结基准 + 外层缩放补偿」。
    //
    //   为什么必须去掉（真机取证，arc=81 把模糊半径放大 8 倍仍**完全看不见**）：
    //   补偿缩放壳是一层 `graphicsLayer{scale}` ⇒ 一个尺寸=父约束（封面边长）的
    //   离屏 RenderNode；而 blur 节点（`base + 2*pad`，比封面大一圈）是它的子节点。
    //   子节点画到 RenderNode 边界之外的部分，**在合成时被直接丢弃**
    //   ⇒ 整圈模糊溢出 100% 被裁掉，只剩被封面本体完全盖住的中心 ⇒ 立体感彻底消失。
    //   （这正是本文件 §「graphicsLayer{alpha} 不能挂最外层」同一条铁律。）
    //
    //   上游没有这层：blur 节点自己就是 `edge + 2*pad` 尺寸的 RenderEffect 层，
    //   `Unbounded` 让它把模糊画在自己的边界内 ⇒ 溢出完整可见。
    //
    //   ⚠ 转屏冻结（`baseEdge` / `freezeEligible`）因此失效：那是纯性能优化
    //     （省 ~1ms/帧），与「看得见立体感」冲突时以后者为准。
    val base = edge

    val blurRadius = base * MeloXArtworkShadowBlurRatio
    // blur 的渲染结果会被**裁到 blur 所在节点自身的边界**（RenderEffect 是 layer 级），
    // 所以承载 blur 的节点必须比本体大出 pad，否则溢出部分会被裁成硬边。
    val pad = blurRadius * MeloXArtworkShadowPadFactor

    // ★ 封面平均亮度（0=纯黑 1=纯白），由副本的 `onState` 异步采样写入。
    //   切歌时必须重置，否则会用上一张封面的亮度去压暗新封面。
    //   ⚠ 只以 `url` 为 key：`model` 是 `ImageRequest` 实例，转屏期尺寸快照逐帧变 ⇒
    //     `remember(model, url)` 会**每帧重置成 0f**（压暗量跟着抖），而这跟封面本身无关。
    var coverLuma by remember(url) { mutableFloatStateOf(0f) }
    // ★ 亮度来源：[ArtworkDynamicPalette.luma]（封面软件位图的面积平均，与从 Coil 位图
    //   采样数学等价）。2026-11-06 按用户要求**回到上游口径**：压暗锚点 = 封面自身亮度，
    //   不再锚「封面背后的背景亮度」。
    //   ⚠ 不要改回 `onState` 里读 `result.image.bitmap`：Coil 交来的是 HARDWARE 位图，
    //     读像素要 `copy(ARGB_8888)` ⇒ 一次同步 GPU→CPU 回读（实测 12.7ms + 抢 RT 10.85ms）。
    val shadowContext = LocalContext.current
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val palette = ArtworkDynamicPaletteProvider.paletteFor(shadowContext, url)
        if (abs(palette.luma - coverLuma) > 0.02f) coverLuma = palette.luma
    }
    // ★ 压暗 colorFilter 按值缓存（2026-10-15 性能排查）：
    //   `graphicsLayer` 的 lambda **每帧**都会执行，而压暗系数 `dim` 只随 `coverLuma`
    //   （切歌时异步写入，一首歌最多几跳）变化 ⇒ 每帧重建 `ColorMatrix`（FloatArray(20)）
    //   + `ColorFilter` 是纯浪费。dim 不变即复用**同一实例** ⇒ 零分配，数学逐值相同。
    val dimFilterCache = remember { MeloXDimColorFilterCache() }
    // 亮度 → 压暗系数（**上游口径**）：暗封面完全不压（保持「其他颜色还行」），
    // 白封面压到 [MeloXArtworkShadowWhiteDim] ⇒ 读成「影」而不是一圈发光白环。
    fun coverDimFactor(luma: Float): Float =
        1f - (1f - MeloXArtworkShadowWhiteDim) *
            smoothStep(luma, MeloXArtworkShadowDimStartLuma, MeloXArtworkShadowDimFullLuma)

    Box(
        modifier = Modifier
            // ★ 上游机制：承载层 `fillMaxSize()` 跟随封面 Box（cover Box 尺寸=封面边长），
            //   模糊节点 (`edge + 2*pad`) 比它大一圈的溢出由 `Unbounded` 画满自身层边界
            //   ⇒ 完整可见。视觉缩放/位移全部走 `transform`（与本体同一份）⇒ 严格跟随 morph。
            //   ⚠ 不要用 `requiredSize` 自定尺寸：那会脱离封面 Box、不再跟随，且 `edge` 在
            //     morph 期被实测布局边长扰乱 ⇒ 模糊层比封面大很多（用户复报，2026-11-06）。
            .fillMaxSize()
            .then(transform),
        contentAlignment = Alignment.Center,
    ) {
        // ★ 2026-11-06：上游结构 —— blur 节点自己就是最外层（尺寸 `edge + 2*pad`）。
        //   `Unbounded` 让模糊画满自己的层边界，溢出（pad）随后在父层完整可见。
        //   ⚠ 不要再往它外面套带 `graphicsLayer` 的壳：那会创建一层尺寸=封面边长的
        //     RenderNode，把 pad 那圈溢出裁掉（真机实测：整圈模糊会 100% 消失）。
        Box(
            modifier = Modifier
                .size(width = edge + pad * 2f, height = edge + pad * 2f)
                .blur(blurRadius, BlurredEdgeTreatment.Unbounded),
            contentAlignment = Alignment.Center,
        ) {
                // ★★ 2026-10-25（修「封面外圈光晕变黑」）：**回到自加载**。上一版改成复用本体
                //   传下来的 Coil `result.image.bitmap` —— 那是**池化位图**，被抽出来长期持有
                //   后会被回收 ⇒ 画出来是黑的。改回副层自己 AsyncImage：本层这份 painter
                //   持有的引用在显示期间有效。
                //   ⚠ 这段原注释写着「Coil 按 request 缓存命中同一份解码（与本体不重复算）」，
                //     2026-10-26 atrace 实测**该说法不成立** ⇒ 已按实测改写（见下条）。
                // ★★ 2026-11-06：**回到上游取图** —— 副层直接用本体同一个 `model`
                //   （同一个 Coil 请求，缓存命中，不另造 `ImageRequest`）。
                //   历史：2026-10-26 为省解码曾改成 384px 的独立请求；用户 2026-11-06
                //   反馈打开动画的立体模糊"效果不好"，而真机显影测试（模糊半径 ×8）
                //   却完全看不到这层 —— 独立请求是首要嫌疑，一并回退。
                AsyncImage(
                    model = model ?: url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // ★★ 2026-10-26（修「预取后第一帧反而更长」）：**不再从这个位图采样亮度**。
                    //   Coil 交来的是 **HARDWARE** 位图（像素在 GPU 里），要读它必须
                    //   `copy(ARGB_8888)` ⇒ `RenderProxy#copyHWBitmapInto readback`：
                    //   一次**同步 GPU→CPU 回读**，实测 12.69ms，同时 RenderThread 侧
                    //   `copyImageInto` 再抢 10.85ms 同一块 GPU。而且这笔开销**与像素量无关**
                    //   —— 384×384 一样要 ~12ms（等一次 GPU 往返的固定代价）。
                    //   预取把封面提前到第一帧 Success 后，回读正好砸在点击帧上 ⇒
                    //   doFrame 16.3 → 26.1ms（比不预取还糟），这就是它的来源。
                    //   亮度改由 [ArtworkDynamicPalette.luma] 白送（同一张封面的软件位图，
                    //   面积平均 ⇒ 与原来 1216px 采样数学等价），见上方 `LaunchedEffect(url)`。
                    modifier = Modifier
                        // ★ 副本**原尺寸**，和本体一样大（源码里先缩到 20% 模糊、再放大 5 倍，
                        //   净尺寸不变 ⇒ `blurredImageView` 里那张图和本体同尺寸居中）。
                        //   ⛔ 别再乘任何 scale：缩到比本体小会被本体完全盖住 ⇒ 一点影都看不见。
                        // ★ 上游：副本与封面**同尺寸** `size(edge)`（preferred，居中于模糊节点）。
                        //   外层 `matchParentSize` 跟随封面，模糊节点 = `edge + 2*pad`，故 `size(edge)`
                        //   取满模糊节点 ⇒ 副本与封面严格同尺寸、随 morph 同步缩放。
                        .size(edge)
                        .graphicsLayer {
                            // ⚠ 这里**只放副本自身的 alpha**（强度），位移/缩放一律走外层
                            //   `transform` —— 与本体同一份 ⇒ 永远同步。
                            this.alpha = alpha
                            // ★★ 2026-10-25：压暗系数 = 背景亮度 / 封面亮度（锚背景，不锚封面）。
                            //   背景是封面派生色时 ≈1 ⇒ 白封面出白环（AM 同色副本观感）；
                            //   背景远暗于封面时锚不动，退到下限 [MeloXArtworkShadowWhiteDim]
                            //   ⇒ 仍是「影」而不是眩光白环。取动画值避免就绪帧硬跳。
                            val dim = coverDimFactor(coverLuma)
                            if (dim < 0.999f) {
                                // ⚠ 只压亮度、**不降饱和**：RGB 同乘一个正数在 HSL 里只改 L、
                                //   不改 H/S ⇒ 色相与饱和度比例**天然完整保留**。
                                //   所以不需要饱和度矩阵（也不需要 `postConcat`/`timesAssign`，
                                //   纯对角阵一步到位）。上一版额外降饱和纯属多余，纯灰反而显脏。
                                // ★ 矩阵经 [MeloXDimColorFilterCache] 按 dim 缓存：dim 不变时
                                //   复用同一实例，不再每帧分配（零观感变化，见上方声明处）。
                                colorFilter = dimFilterCache.forDim(dim)
                            }
                        }
                        .then(clipModifier),
                )
        }
    }
}

/**
 * 压暗 colorFilter 的**按值缓存**（draw 期安全：纯字段读写，不触发重组/重绘调度）。
 *
 * 为什么需要：阴影副本的 `graphicsLayer` lambda 每帧执行，但压暗系数 `dim` 只随
 * 封面平均亮度（切歌时异步采样，一首歌最多几跳）变化。原实现每帧
 * `ColorMatrix()` + `ColorFilter.colorMatrix()` 各分配一次 —— 打开动画
 * 360ms × 120Hz ≈ 43 次无谓分配，且新实例还会让 RenderNode 每帧重设 paint。
 * 缓存后 dim 不变即复用同一实例：数学逐值相同 ⇒ **零观感变化**，纯省分配。
 *
 * ⚠ 之所以不走 `remember(dim)`：调用点在 `graphicsLayer` lambda（draw 期）里，
 *   不能调 composable API；且按值比较比按 key 重组更便宜（ dim 不变时连
 *   recomposition 都不触发）。
 */
private class MeloXDimColorFilterCache {
    private var key: Float = Float.NaN
    private var cached: ColorFilter? = null

    fun forDim(dim: Float): ColorFilter {
        val existing = cached
        if (existing != null && key == dim) return existing
        val created = ColorFilter.colorMatrix(
            ColorMatrix().apply { setToScale(dim, dim, dim, 1f) },
        )
        key = dim
        cached = created
        return created
    }
}

/**
 * 模糊半径 / 封面边长。
 *
 * 源码：`resized(0.2)` 后 `CIGaussianBlur(radius = 3)`，blur 半径作用在**0.2× 尺寸**的图上，
 * 放大 5 倍回到 1.4× 容器 ⇒ 等效到本体边长 = 3 / 0.2 / 1.4 ≈ **1.07%**。
 * 这里取 2.5%：比源码略大一点，让真机上"看得出有影"，但仍属于**窄边**。
 * ⛔ 别调大：AM 的观感是"极窄的一圈淡晕"，一旦放宽就变成明显的彩色光圈（前三版栽在这）。
 */
private const val MeloXArtworkShadowBlurRatio = 0.025f
/**
 * blur 承载节点的每边余量 = blur 半径 × 本系数。
 * RenderEffect 裁到自身 layer 边界 ⇒ 余量不足会把模糊溢出裁成硬边（v87 的教训）。
 */
private const val MeloXArtworkShadowPadFactor = 1.6f

/** 开始压暗的平均亮度（0~1）。低于此值完全不压（上游口径：暗封面保持原样）。 */
private const val MeloXArtworkShadowDimStartLuma = 0.55f
/** 压到满的亮度。白色封面 ≈0.92 ⇒ 完全落在这之上。 */
private const val MeloXArtworkShadowDimFullLuma = 0.88f

/**
 * **白封面的压暗系数**（1 = 不压，0.34 = 压到约三分之一亮度）。
 *
 * AM 的阴影是同色模糊副本且 `shadowAlpha = 1`（零 colorFilter），成立前提是
 * **AM 播放页背景是封面的浅色模糊版** ⇒ 白色副本融在同色浅背景里。MeloX 的背景
 * 一旦比封面暗，同一个副本在白色封面上就是一圈**发光的白环**（用户报障「一到白色
 * 就很碍眼」）⇒ 只对亮封面压暗，暗封面完全不压（保持「其他颜色还行」）。
 *
 * ⚠ 2026-11-06 按用户要求**回到上游口径**：压暗只看封面自身亮度，不再锚背景亮度。
 *   （2026-10-25 曾改成「背景亮度 / 封面亮度」，副作用是暗封面的立体感被压到几乎看不见。）
 */
private const val MeloXArtworkShadowWhiteDim = 0.34f

/**
 * 阴影副层的**解码边长**（px），2026-10-26。
 *
 * 副层只做 `blur(边长 × 2.5%)` 后当光晕用 —— 模糊半径是全尺寸封面的 2.5%，
 * 也就是**高频细节在渲染前就被整片抹掉**，源图分辨率再高也换不回一个像素的清晰度。
 * 但实测（atrace，暂停态切歌）：本体 1216×1216 解码 40.6ms、副层**另起一个线程再解一遍
 * 38.2ms**（Coil 没去重，两次 decode 时间戳几乎完全重叠），外加两张 5.9MB 的
 * `CreateImage` Vulkan 纹理 —— 全部白烧在一个"糊到看不清"的图层上。
 *
 * 384 ⇒ 面积是 1216 的 10%，解码落在 3~4ms，纹理 0.6MB。
 * ⚠ 不要再往上调：副层在屏幕上的边长是封面边长（≈1216px），放大 3.2× 全靠 GPU 双线性，
 *   而它**紧接着就被 blur**，放大引入的软化会被模糊吞掉，肉眼不可辨。
 */
private const val MeloXArtworkShadowDecodePx = 384

/**
 * ⋯ 菜单 / 音质浮层关断后，整屏背板捕获（`actionsBackdrop`）延迟摘除的 Grace 时长。
 *
 * 浮层退场动画（ModalBottomSheet / MeloXGlassDialog 自带 ~200-350ms 淡出+缩放）期间
 * 玻璃面板仍在采样这块背板 ⇒ 必须等退场画完再摘，否则退场中的玻璃会 sampled 到
 * 空背板（丢模糊、可见跳变）。
 */
private const val ACTIONS_BACKDROP_EXIT_GRACE_MS = 450L

/**
 * 切歌回弹的**起点缩放**（1 = 不弹）。
 *
 * 观感核算：起点 0.90、弹簧 ζ=0.42 ⇒ 过冲峰值 ≈ 1.023（幅度 10% 的行程上过冲 ~2.3%），
 * stiffness 480 ⇒ 收尾 ≈ 0.45s。想「更弹」就压低这个值或降 ζ（ζ 越小过冲越大），
 * **别动 stiffness**（只影响快慢）。
 */
private const val MeloXArtworkSwitchBounceScaleFrom = 0.90f
/** 回弹期封面的起点透明度：只用来把硬切磨柔，**不是**淡入淡出 ⇒ 取值不宜太低。 */
private const val MeloXArtworkSwitchBounceAlphaFrom = 0.55f
/** alpha 回到 1 所占的行程比例（越小越快恢复不透明）。 */
private const val MeloXArtworkSwitchBounceAlphaSpan = 0.4f
private const val MeloXArtworkSwitchBounceDamping = 0.42f
private const val MeloXArtworkSwitchBounceStiffness = 480f

/** 切歌回弹的「上一首」记录。**非 state**：写它不该触发重组。 */
private class ArtworkSwitchBounceSeen {
    var mediaId: String? = null
}

/** 横屏封面横滑的**跟手阻尼**（1 = 完全跟手；<1 让封面比手指慢一点，手感更"重"）。 */
private const val MeloXArtworkSwipeDamping = 0.55f
/** 横滑跟手位移的上限（封面最多被拖走多少）。 */
private val MeloXArtworkSwipeMaxDrag = 88.dp
/** 判定「切歌」的横滑阈值：低于它只回弹归位，不切歌。 */
private val MeloXArtworkSwipeThreshold = 44.dp
/** 双击判定窗口（ms）。 */
private const val MeloXArtworkDoubleTapTimeoutMillis = 320L

/**
 * 转屏主时间线起跑前让出的帧数（2026-10-22 第四轮）。
 *
 * 实测转屏最坏一帧 `Choreographer#doFrame` 646ms（`animation` 相位 362ms、
 * RenderThread `Drawing 3168x1440` 291ms），而同一动作在空闲时刻最坏只有 61.5ms ⇒
 * 本层 300ms 动画的**起跑帧**正好压在同一批系统重配置工作上。
 * 让 2 帧即可越过那道尖峰：`withFrameNanos` 等的是下一次帧回调，而重配置那一帧
 * 本身要 300ms 才结束。
 *
 * ⚠ 0 = 完全恢复原时序（回退开关，不需要改任何别的地方）。
 */
private const val ORIENT_SETTLE_FRAMES = 2

/**
 * 常驻重层（歌词 / 队列）在展开落定后、真正参与组合前的「宽限帧数」（2026-10-24）。
 *
 * 原逻辑：`transitionInFlight` 一翻 false（即 `expansionProgress` 跑到 0.999、morph 落定那一帧）
 * 就立刻置 `residentLayersReady = true`，歌词层随之**首次组合 + 预热首次栅格化**（含模糊 shader
 * 编译、逐字 Canvas、边缘渐隐），这一笔偶发压在 morph 尾帧的 GPU 负载上 ⇒ 用户报的
 * 「第一次展开偶尔不丝滑、第二次正常」（第二次 `residentLayersReady` / `warmupDone` 已是
 * one-way latch 真值，且 alpha=0 时 Skia 整棵跳过绘制 ⇒ 不再付费）。
 *
 * ⇒ 落定后让出若干帧，把首次组合 + 首次栅格化整体推到**静止态（封面页 idle）**，
 * 绝不与 morph 尾帧抢帧、也不存在帧序竞态 ⇒ 偶发的那一帧掉帧消失。
 * ⚠ `!artworkVisible` 旁路已保证：用户若在宽限期内切到歌词 / 队列页，会立刻组合，
 *   本宽限只影响「停在封面页 idle」的情形，不影响切页就绪。
 * ⚠ 0 = 完全恢复旧时序（回退开关，不需改别处）。
 */
private const val MELOX_RESIDENT_LAYERS_SETTLE_GRACE_FRAMES = 6

/**
 * 「播放页落定」这个布尔（`transitionInFlight`）的 **true→false 延迟**（毫秒）。
 *
 * ⚠⚠ **2026-11-06 16:xx 定论：默认值已回退为 0（无效）。**
 *  当初的 framestats 交替 A/B（`rt>16.6` 3.74%→2.09%、p99 34.5→23.0）**是噪声** ——
 *  同一台机器上连续 4 批的批间漂移（4.89%↔2.06%）比处置差异还大。改用「落定那一帧的
 *  atrace 切片」直接判：`arc 0`（延迟 120ms）与 `arc 97`（不延迟）的
 *  `Record View#draw()` 重录**都落在 tap+716~791ms**，逐帧结构完全一致 ⇒
 *  这 120ms 从未移动过那个尖峰，`LocalMeloXPlayerControlsEnabled` 也不是它的成因
 *  （`arc 94` 恒 true 同样无效）。
 *
 * 保留开关只为可复现历史对照：`arc 96` = 250ms、`arc 97` = 0。
 */
private const val MELOX_SETTLE_HOLD_MS = 0L

/**
 * 横屏封面的手势识别：**双击** 与 **横滑** 走同一个 [awaitEachGesture] 循环。
 *
 * ⚠ 为什么不用 `detectTapGestures` + `detectHorizontalDragGestures` 两个 `pointerInput`：
 *   同一节点上先挂的那个会先拿到事件，一旦它消费了 down，后面的 drag 检测器就再也
 *   收不到这次手势（`awaitFirstDown` 默认要求未消费）⇒ 双击与横滑二选一失效。
 *
 * - 位移超过 `viewConfiguration.touchSlop` 才进入拖动（之后才消费事件）⇒ 轻点/竖直滑动不受影响；
 * - 松手时按**累计水平位移**判方向：≤ −阈值 ⇒ 下一首，≥ +阈值 ⇒ 上一首，否则只归位；
 * - 双击窗口内第二次轻点 ⇒ [onDoubleTap]（播放/暂停）。
 */
private suspend fun PointerInputScope.meloXArtworkGestures(
    enabled: () -> Boolean,
    swipeEnabled: Boolean,
    thresholdPx: Float,
    maxDragPx: Float,
    onDragDelta: (Float) -> Unit,
    onRelease: (Float) -> Unit,
    onDoubleTap: () -> Unit,
    onTap: (() -> Unit)?,
) {
    var lastTapUptime = 0L
    awaitEachGesture {
        val down = awaitFirstDown()
        // 未启用：本次手势完全不参与（不消费 ⇒ 事件照常传给下层）。
        if (!enabled()) return@awaitEachGesture
        var dragged = 0f
        var verticalDrag = 0f
        var dragging = false
        val pointerId = down.id
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (!change.pressed) {
                if (!dragging && abs(dragged) <= viewConfiguration.touchSlop && abs(verticalDrag) <= viewConfiguration.touchSlop && !change.isConsumed) {
                    if (onTap != null) {
                        change.consume()
                        onTap()
                        break
                    }
                    val elapsed = change.uptimeMillis - lastTapUptime
                    if (elapsed in 1..MeloXArtworkDoubleTapTimeoutMillis) {
                        lastTapUptime = 0L
                        change.consume()
                        onDoubleTap()
                    } else {
                        lastTapUptime = change.uptimeMillis
                    }
                }
                break
            }
            val dx = change.position.x - change.previousPosition.x
            dragged += dx
            verticalDrag += change.position.y - change.previousPosition.y
            if (swipeEnabled && !dragging && abs(dragged) > viewConfiguration.touchSlop && abs(dragged) > abs(verticalDrag)) dragging = true
            if (dragging) {
                change.consume()
                // 越界后的多余位移不再叠加，避免"拖到底还能继续拖"的橡皮筋错觉。
                if ((dragged <= maxDragPx && dragged >= -maxDragPx) || dragged * dx < 0f) {
                    onDragDelta(dx)
                }
            }
        }
        if (dragging) onRelease(dragged)
    }
}

/**
 * 转屏「主角飞行」驼峰：**`sin(πp)`**（原版观感，保留）。
 *
 * ## 曾经的两次误诊（写下来别再走回头路）
 *
 * ### 误诊①曾以为 `sin(πp)` 的端点斜率 ±π 是抖动根因 ⇒ 换成 `sin²(πp)`
 * **错。** 抖动真正的根因是弹簧的 `visibilityThreshold` 默认 0.01 太大：
 * `orientProgress` 还差 1%（≈1.6dp）就被判定结束并**直接吸附**⇒ 那 1.6dp 是硬跳。
 * 把阈值收紧到 `1e-4`（见 `transitionSpec`）后，末帧的 `|dp/dt| ~ 1e-4·ω`，
 * 端点斜率的实际贡献 = `6dp × π × 1e-4 × ω ≈ 0.0002dp` ⇒ **完全可以忽略**。
 * 也就是说：**阈值修好之后，`sin` 本身就不抖了**，②是多余且有害的改动。
 *
 * ### 误诊 ② `sin²` 端点斜率确实为 0，但**观感被削**
 * `sin²` 在 (0,1) 内处处 ≤ `sin`（只在中点相等）⇒ p=0.25/0.75 抬升0.707→0.5、
 * 均值 0.637→0.5 ⇒ 起飞/落地前的「引带」消失、中段只剩窄鼓包
 * ⇒ 用户反馈「不抖了，但动画变难看」。**修数学不能修掉观感。**
 *
 * ## 唯一的保留修改：去掉 `coerceIn(0, 1)`
 * 弹簧（ζ=0.82）过冲到 p ≈ ±0.016。clamp 会把 `d/dp = ±π` **硬压成 0** ⇒ 过冲段
 * 制造一个二阶折点（封面「刹一下又弹回来」）。
 * 不clamp 时 `sin(πp)` 在 0 附近线性 ⇒ 过冲段输出仅 −0.05，对应缩小 0.45% /
 * 上移 0.3dp，**连续且肉眼不可见**，比 clamp 的折点好。
 *
 * @param orientProgress 可能过冲的 0~1 插值分数（**未 clamp**）
 * @return 中段峰值 1，两端 0
 */
private fun meloXFlightHump(orientProgress: Float): Float =
    sin(PI * orientProgress.toDouble()).toFloat()

private class PortraitArtworkGeom {
    var size: androidx.compose.ui.unit.Dp = 0.dp
    var centerYFrac: Float = 0.5f
}


private fun lerpDp(start: androidx.compose.ui.unit.Dp, end: androidx.compose.ui.unit.Dp, fraction: Float) =
    start + (end - start) * fraction.coerceIn(0f, 1f)

private fun smoothStep(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@Composable
private fun TransitionOverlay(
    state: MeloXPlaybackUiState,
    fullArtworkSize: androidx.compose.ui.unit.Dp,
    targetX: androidx.compose.ui.unit.Dp,
    targetY: androidx.compose.ui.unit.Dp,
    targetRadius: androidx.compose.ui.unit.Dp,
) {
    val exitProgress by animateFloatAsState(
        targetValue = if (state.transitionHandedOff) 1f else 0f,
        animationSpec = tween(
            durationMillis = 620,
            easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f),
        ),
        label = "automix-cover-handoff",
    )
    val entryFraction = (state.transitionProgress / 0.28f).coerceIn(0f, 1f)
    // Pronounced ease-out-back: covers separate quickly, overshoot slightly,
    // then settle into the symmetric paired layout.
    val split = easeOutBack(entryFraction)
    val gap = 16.dp
    val pairedSize = (fullArtworkSize - gap) / 2f
    val outgoingSize = lerp(fullArtworkSize, pairedSize, split)
    val outgoingX = targetX - (pairedSize + gap) * exitProgress
    val outgoingY = targetY + (fullArtworkSize - outgoingSize) / 2f
    val incomingSize = lerp(pairedSize, fullArtworkSize, exitProgress)
    val incomingPairedX = targetX + pairedSize + gap
    val incomingX = lerp(
        targetX + fullArtworkSize + gap,
        incomingPairedX,
        split,
    ).let { pairedX -> lerp(pairedX, targetX, exitProgress) }
    val incomingY = targetY + (fullArtworkSize - incomingSize) / 2f

    if (exitProgress < 0.999f) {
        Box(
            modifier = Modifier
                .offset(x = outgoingX, y = outgoingY)
                .size(outgoingSize)
                .clip(RoundedCornerShape(targetRadius))
                .graphicsLayer { alpha = 1f - exitProgress },
        ) {
            Artwork(
                url = state.outgoingArtworkUrl ?: state.artworkUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (split > 0.01f) {
        Box(
            modifier = Modifier
                .offset(x = incomingX, y = incomingY)
                .size(incomingSize)
                .clip(RoundedCornerShape(targetRadius)),
        ) {
            Artwork(
                url = state.incomingArtworkUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun easeOutBack(value: Float): Float {
    val t = value.coerceIn(0f, 1f) - 1f
    val overshoot = 1.24f
    return (1f + (overshoot + 1f) * t * t * t + overshoot * t * t)
        .coerceIn(0f, 1.08f)
}

/**
 * 把一个 `State<Boolean>` 的 **true→false 翻转推迟 [holdMs] 毫秒**（false→true 立即生效）。
 *
 * 用途见 `transitionInFlightState` 处注释：`p` 跨过 0.999 的那一帧会让整棵控件树重组 +
 * 重录 display list，而那正是展开动画的最后一帧。把翻转挪到画面静止之后，同样的掉帧
 * 就不再表现为「顿一下」。
 *
 * ⚠ 期间若源重新变 true，本次 `LaunchedEffect` 被取消 ⇒ 不会误置 false（收起方向安全）。
 */
@Composable
private fun rememberDelayedFalse(src: State<Boolean>, holdMs: Long): State<Boolean> {
    val out = remember { mutableStateOf(src.value) }
    LaunchedEffect(src.value) {
        if (src.value) {
            out.value = true
        } else {
            delay(holdMs)
            out.value = false
        }
    }
    return out
}
