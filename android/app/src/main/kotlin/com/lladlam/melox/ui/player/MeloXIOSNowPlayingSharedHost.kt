package com.lladlam.melox.ui.player

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.lladlam.melox.ui.glass.LocalMeloXBackdrop
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXPlayerBackgroundMode
import com.lladlam.melox.ui.settings.MeloXPlayerShell
import com.lladlam.melox.ui.settings.MeloXScreenAwakeMode
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import com.lladlam.melox.ui.layout.rememberMeloXWindowInfo
import com.lladlam.melox.core.network.MeloXSearchKind
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
    // 取证用：把下沉诊断挂到 app 私有外部目录（`mxdiag.txt`），供 `adb pull` 读取。
    DisposableEffect(context) {
        MeloXPlayerSinkDiag.bind(context)
        onDispose {}
    }
    val rememberPlayerPage = MeloXSettingsPreferences.boolean(context, "playback_remember_page", true)
    var page by remember(rememberPlayerPage) {
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
    var showActions by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var showLandscapeSkyline by remember { mutableStateOf(false) }
    var lyricsInterfaceHidden by remember { mutableStateOf(false) }
    val window = rememberMeloXWindowInfo()
    val isLandscape = window.isLandscape
    // ★ 平板横屏双栏（宽 ≥ 840dp 且高 ≥ 600dp）。手机 / 竖屏恒 false ⇒ 下面所有新分支不进。
    val twoPane = window.supportsPlayerTwoPane
    // ★ 统一主时间线：整条 orientation Transition 同时驱动封面（位置/尺寸）与场景层
    // （竖/横淡入淡出），转屏时「封面滑移 + 场景淡变」在**同一条 spring** 上连续发生 =
    // 真正的一镜到底。此前封面用独立 animateDpAsState(tween)、场景用独立 AnimatedContent(tween)，
    // 两套时钟各跑各的，读起来是「封面挪位 + 旧布局淡出 + 新布局淡入」三件事叠加 = 用户判「不及预期」。
    val orientTransition = updateTransition(targetState = isLandscape, label = "now-playing-orient")
    // ★★ 平板双栏「玩家卡片居中 ⇄ 左右分栏」的**单一时钟**（2026-10-07）。
    //   状态定义：**双栏 + `page == Artwork`**（= 还没选歌词 / 队列，即"封面态"）⇒ 左列整块
    //   移到**屏幕正中**、右列整个不画；点歌词 / 队列（`page != Artwork`）⇒ 反向回左列、
    //   右列淡入。手机 / 竖屏 `twoPane == false` ⇒ 恒 0，逐像素不变。
    //   ⚠⚠ 这**一个**值必须同时喂给场景层（Row 的 `offset`）与持久封面层（`centerXFrac`）：
    //     两处各跑一份动画，哪怕 spec 逐字相同，只要起跑帧差一帧，封面就会与左列占位错开
    //     （本仓库铁律：封面落点单点真源）。
    val columnCenter = remember { Animatable(0f) }
    val columnCenterTarget = if (twoPane && page == MeloXNowPlayingPage.Artwork) 1f else 0f
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
    val expansionProgress by animatedVisibilityScope.transition.animateFloat(
        transitionSpec = { meloXPlayerLinearFloatSpec() },
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
    val backdropAlpha = 1f
    val isolationAlpha = if (MeloXSettingsRuntime.playerBackgroundIsolationEnabled) {
        smoothStep(expansionProgress, 0.34f, 0.82f)
    } else {
        0f
    }
    val fullPlayerAlpha = smoothStep(expansionProgress, 0.04f, 0.52f)
    val collapseProgress = (1f - expansionProgress).coerceIn(0f, 1f)
    val latestCollapseProgress = rememberUpdatedState(collapseProgress)
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
    val shellSettledOpen = animatedVisibilityScope.transition.currentState ==
        animatedVisibilityScope.transition.targetState &&
        animatedVisibilityScope.transition.targetState == EnterExitState.Visible
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
    val flattenAnim = remember { Animatable(if (shellSettledOpen) 1f else 0f) }
    LaunchedEffect(shellSettledOpen) {
        if (shellSettledOpen) {
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
    val flattenAlpha = flattenAnim.value
    val cornerRadius =
        (meloXPlayerShellCornerDp(expansionProgress, shellPeakDp) * (1f - flattenAlpha)).dp
    // 外壳与页面用**同一个形状函数**（平滑圆角／Lamé 超椭圆）—— 正圆弧在直边处曲率跳变，
    // 就是用户说的「圆弧的过渡很生硬」的来源。
    val shellShape = with(LocalDensity.current) { MeloSmoothCornerShape(cornerRadius.toPx()) }
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
    val sinkBoundsTransform = remember(sinkPeakPx) {
        meloXPlayerSharedBoundsTransform(sinkPeakPx) { hostExitingState.value }
    }
    val sharedShellModifier = with(sharedTransitionScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = sharedPlayerShellKey()),
            animatedVisibilityScope = animatedVisibilityScope,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            boundsTransform = sinkBoundsTransform,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }

    // NowPlaying owns the player-level Back handler. Child modal overlays are
    // composed later and temporarily disable this handler, so Back always unwinds
    // the topmost visual layer before the player itself is dismissed.
    BackHandler(enabled = !showActions && !showQuality) {
        if (showLandscapeSkyline) showLandscapeSkyline = false else onDismiss()
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
    val transitionInFlight = expansionProgress < 0.999f
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
    var residentLayersReady by remember { mutableStateOf(false) }
    LaunchedEffect(transitionInFlight) {
        if (!transitionInFlight) residentLayersReady = true
    }

    // 逐帧计时（只读探针，见文件头；摘除时删这一段 + 那个文件即可）。
    // ⚠⚠ 2026-10-15 v2：key 必须用**相位**，不能再用 `transitionInFlight` ——
    //    后者在收起静止态（p=0）也是 true，LaunchedEffect 不会在打开时重启，
    //    v1 因此**从来没trace到过打开方向**（只有关闭方向），且一次 close 的会话
    //    会跨空闲延续到下一次 open、f 编号跨方向合并。相位 0/1/2 各自独立成会话：
    //    每次「过渡中」都是完整的一次打开或一次关闭。
    val morphPhase = when {
        expansionProgress >= 0.999f -> 2
        expansionProgress <= 0.001f -> 0
        else -> 1
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(actionsBackdrop),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(sharedShellModifier)
                    .clip(shellShape)
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
                        enabled = page == MeloXNowPlayingPage.Artwork && !isLandscape,
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
                            .then(
                                if (isolationAlpha > 0f) {
                                    Modifier.background(Color.Black.copy(alpha = isolationAlpha))
                                } else {
                                    Modifier
                                },
                            )
                            .layerBackdrop(playerControlBackdrop)
                            .graphicsLayer { alpha = backdropAlpha },
                ) {
                    if (
                        page == MeloXNowPlayingPage.Lyrics &&
                        MeloXSettingsRuntime.playerBackgroundMode == MeloXPlayerBackgroundMode.AppleLyrics
                    ) {
                        MeloXLyricsArtworkBackdrop(
                            artworkUrl = state.artworkUrl,
                            isPlaying = state.isPlaying && expansionProgress > 0.72f,
                        )
                    } else if (MeloXSettingsRuntime.playerBackgroundMode == MeloXPlayerBackgroundMode.FlowingLight) {
                        MeloXFlowingLightBackdrop(
                            mediaId = state.mediaId,
                            artworkUrl = state.artworkUrl,
                            isPlaying = expansionProgress > 0.72f,
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
                        MeloXBlurredArtworkBackdrop(state.artworkUrl)
                    }
                }

                CompositionLocalProvider(
                    LocalMeloXBackdrop provides playerControlBackdrop,
                    // ⚠⚠ 过渡进行中把**场景内全部控件**（暂停 / 上一首 / 下一首 / 音量条…）
                    //   显式禁用（用户报障「下拉返回的缩小播放器里的按钮还能被点击」）。
                    //   与根节点的 `PointerEventPass.Initial` 拦截器**双保险**。
                    LocalMeloXPlayerControlsEnabled provides !transitionInFlight,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = fullPlayerAlpha },
                    ) {
                        if (MeloXSettingsRuntime.playerShell == MeloXPlayerShell.Classic) {
                            MeloXClassicNowPlayingScene(
                                state = state,
                                page = page,
                                onPageChanged = { destination ->
                                    transitionSourcePage = page
                                    page = destination
                                },
                                onDismiss = onDismiss,
                                onShowActions = { showActions = true },
                                onShowQuality = { showQuality = true },
                            )
                        } else MeloXIOSNowPlayingScene(
                            state = state,
                            page = page,
                            transitionSourcePage = transitionSourcePage,
                            onDismiss = onDismiss,
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
                                expansionProgress >= 0.98f &&
                                !showLandscapeSkyline,
                            // ★ 常驻重层（歌词/队列）的首帧组合推到展开动画落定之后，见上方 latch 注释。
                            residentLayersReady = residentLayersReady,
                            orientTransition = orientTransition,
                            columnCenterProgress = columnCenterProgress,
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
                    expansionProgress = expansionProgress,
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
    onArtworkTap: () -> Unit,
    expansionProgress: Float,
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
    val isLandscape = window.isLandscape
    // ★ 平板横屏双栏（2026-10-07）：左列封面下方还要放「曲名/艺人行 + 控制条」整块，
    //   封面必须随之上抬（整块在列内居中 ⇒ 封面中心 = 下边距 + 封面高 / 2）。
    //   ⚠ 判据走 `window.supportsPlayerTwoPane`（宽度 ≥ 840dp **且** 高度 ≥ 600dp），
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
    val fullScreenScaleBlend = smoothStep(expansionProgress, 0.30f, 0.88f)
    val effectiveScale = 1f +
        (playbackScale - 1f) * fullScreenScaleBlend * (1f - pageFrameProgress)

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
        val landscapeSize = meloXLandscapeArtworkSize(maxWidth, maxHeight, twoPane)
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
        val context = LocalContext.current
        val density = LocalDensity.current.density
        val fullArtworkSizePx = fullArtworkRequestSizePx(fullArtworkSizeTarget.value, density)
        val fullArtworkModel = remember(context, state.artworkUrl, fullArtworkSizePx) {
            state.artworkUrl?.takeIf(String::isNotBlank)?.let { artworkUrl ->
                ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(fullArtworkSizePx, fullArtworkSizePx)
                    .precision(Precision.INEXACT)
                    .build()
            }
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
        val orientProgress by orientTransition.animateFloat(
            transitionSpec = {
                tween(300, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
            },
            label = "shared-artwork-orient-progress",
        ) { landscape -> if (landscape) 1f else 0f }
        // ⚠ 尺寸/位置/中心全部用**同一个（可能过冲的）orientProgress** 线性插值，**不要单独 clamp**：
        //   弹簧收尾会一起过冲 ⇒ 回弹一致。之前 `lerpDp` 把 fraction clamp 到 [0,1]（尺寸不过冲）
        //   而 centerX/YFrac 手动算（过冲）⇒ 位置在弹、尺寸不弹 = 收尾发飘/抖。
        val fullArtworkSize = portraitGeom.size + (landscapeSize - portraitGeom.size) * orientProgress
        // ⚠ 横屏终值从「左列正中 0.215」随 [columnCenterProgress] 连续滑到「屏幕正中 0.5」。
        //   必须与场景层 Row 的 `offset = columnCenterProgress × 0.285 × 行宽` 同解
        //   （0.5 − 0.215 = 0.285，行宽 == 本函数的 maxWidth），否则封面会与左列占位 /
        //   控制条横向错开。手机 / 竖屏恒 0 ⇒ 终值仍是 0.215，逐像素不变。
        //   ⚠ 在这里（本 composable 自己的 scope）读 lambda：本函数本来就每帧读
        //     `orientTransition.animateFloat`，重组范围仅限自身，不会波及 host / 场景。
        val landscapeCenterXFrac = 0.215f + (0.5f - 0.215f) * columnCenterProgress()
        val centerXFrac = 0.5f + (landscapeCenterXFrac - 0.5f) * orientProgress
        // ★ 横屏纵向目标：手机 / 竖屏恒 0.5f（封面在整屏正中）；**平板双栏**要上抬 ——
        //   左列封面下方多了「曲名/艺人行 + 控制条」整块，整块在列内居中 ⇒ 封面中心
        //   = 下边距 + 封面高 / 2，与场景层左列的 `Arrangement.Center` 严格同解。
        //   ⚠ 两边必须用**同一组常量 + 同一份 landscapeSize**，否则封面会与控制条错位。
        val landscapeCenterYFrac = if (twoPane) {
            meloXTabletLandscapeArtworkCenterYFrac(maxWidth, maxHeight)
        } else {
            0.5f
        }
        val centerYFrac =
            portraitGeom.centerYFrac + (landscapeCenterYFrac - portraitGeom.centerYFrac) * orientProgress
        val fullX = (maxWidth * centerXFrac - fullArtworkSize / 2f).coerceAtLeast(0.dp)
        val fullY = (maxHeight * centerYFrac - fullArtworkSize / 2f).coerceAtLeast(0.dp)

        // ★ 转屏「主角飞行」：航程中段封面临时抬起（放大 + 微升），落位后归位。
        //   驼峰保持原版 `sin(πp)` 观感（2026-10-07 两次误诊的结论，见 [meloXFlightHump]）：
        //   抖动的根因是弹簧 `visibilityThreshold` 默认 0.01 太大（见上方 transitionSpec），
        //   **不是**端点斜率；曾为"修斜率"换成 `sin²`、再换平台驼峰，两次都把观感改坏了。
        //   这里唯一保留的改动是**不再 clamp 输入**（去掉过冲段的二阶折点）。
        val flightIntensity = meloXFlightHump(orientProgress) * (1f - pageFrameProgress)
        val flightLiftScale = 1f + 0.09f * flightIntensity
        // ⛔ 原版这里还有 `flightLiftShadow = 26.dp * flightIntensity`，用来让阴影在转屏
        //   「主角飞行」时相对本体下沉。v96 起阴影与本体共用同一 transform
        //   （见下方`artworkTransform`），相对下沉 = 脱节 = 露馅 ⇒ 该变量已删除。
        //   转屏飞行时的立体感现在由本体 scale（flightLiftScale）整体带过去。

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
        val morphFreeze = expansionProgress > 0.01f && expansionProgress < 0.99f
        val effectivePageFrame = if (morphFreeze) destPageFrame else pageFrameProgress
        val targetSize = lerpDp(fullArtworkSize, 72.dp, effectivePageFrame)
        val targetX = lerpDp(fullX, 0.dp, effectivePageFrame)
        val targetY = lerpDp(fullY, compactFrameTop, effectivePageFrame)
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
        val targetRadius = lerpDp(6.dp, 12.dp, expansionProgress.coerceIn(0f, 1f))
        // ⚠ 必须与外壳 / 迷你栏侧用**同一个** `sinkPeakPx`（同一个下沉量），
        //   否则封面那个独立 overlay 会与外壳沉不同的量 ⇒ 封面在栏内错位。
        val artworkSinkPeakPx = with(LocalDensity.current) { MeloXPlayerSinkPeakDp.dp.toPx() }
        // ⚠ 与外壳同一条纪律：方向走 lambda、实例 key 不含方向（手势越阈值不重建）。
        //   ⚠⚠ 判据必须与外壳**逐字同义**（只认 `closingIntent`，不含 `targetState` 兜底，
        //   原因见上方 `isHostExiting` 的 2026-10-06 说明）—— 外壳与封面必须**同时沉 /
        //   同时不沉**，否则封面会在迷你栏内错位。
        val artworkSinkEnabledState = rememberUpdatedState(closingIntent)
        val artworkBoundsTransform = remember(artworkSinkPeakPx) {
            meloXPlayerSharedBoundsTransform(artworkSinkPeakPx) { artworkSinkEnabledState.value }
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
        val artworkTransform = Modifier.graphicsLayer {
            val bounce = switchBounce.value
            // 0 → 起点（缩小 + 半透明），1 → 原样；弹簧过冲段 bounce > 1 ⇒ 略微放大。
            val bounceScale = MeloXArtworkSwitchBounceScaleFrom +
                (1f - MeloXArtworkSwitchBounceScaleFrom) * bounce
            val liftScale = effectiveScale * flightLiftScale * bounceScale
            scaleX = liftScale
            scaleY = liftScale
            // alpha 只在前 40% 行程回到 1：把硬切磨柔，不是淡入淡出。
            alpha = MeloXArtworkSwitchBounceAlphaFrom +
                (1f - MeloXArtworkSwitchBounceAlphaFrom) *
                (bounce / MeloXArtworkSwitchBounceAlphaSpan).coerceAtMost(1f)
            translationX = artworkDrag.value
            translationY = -6.dp.toPx() * flightIntensity
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (hidden || transitionAnimationEligible) 0f else 1f
                },
        ) {
            Box(
                modifier = Modifier
                    .offset(x = targetX, y = targetY)
                    .size(targetSize)
                    .then(skylineSourceModifier)
                    .then(sharedArtworkModifier)
                    .then(artworkGestureModifier),
            ) {
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
                    model = fullArtworkModel,
                    // 稳态 = 1；切页 / 转屏途中按 pageFrameProgress 淡出；
                    // 「播放中」比「暂停」略强，与 AM 的呼吸感一致。
                    strength = fullScreenScaleBlend *
                        (1f - pageFrameProgress * 0.8f) *
                        artworkShadowStrength,
                    transform = artworkTransform,
                    edge = targetSize,
                    cornerRadius = targetRadius,
                )
                Artwork(
                    url = state.artworkUrl,
                    model = fullArtworkModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(artworkTransform)
                        .clip(RoundedCornerShape(targetRadius)),
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
    model: Any?,
    strength: Float,
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
    cornerRadius: Dp,
) {
    val alpha = strength.coerceIn(0f, 1f)
    // ⚠ `Modifier.blur` 走 RenderEffect，只在 API 31+ 生效；本机 minSdk 26。
    //   低版本上模糊是 no-op ⇒ 副本会退化成一圈**生硬的封面边**（比没有更丑），
    //   所以那种情况下整个阴影层直接不画。
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    if (alpha <= 0.004f || !supported) return

    val blurRadius = edge * MeloXArtworkShadowBlurRatio
    // blur 的渲染结果会被**裁到 blur 所在节点自身的边界**（RenderEffect 是 layer 级），
    // 所以承载 blur 的节点必须比本体大出 pad，否则溢出部分会被裁成硬边。
    val pad = blurRadius * MeloXArtworkShadowPadFactor

    // ★ 封面平均亮度（0=纯黑 1=纯白），由副本的 `onState` 异步采样写入。
    //   切歌时必须重置，否则会用上一张封面的亮度去压暗新封面。
    var coverLuma by remember(model, url) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    // ★ 压暗 colorFilter 按值缓存（2026-10-15 性能排查）：
    //   `graphicsLayer` 的 lambda **每帧**都会执行，而压暗系数 `dim` 只随
    //   `coverLuma`（切歌时异步采样，一首歌最多几跳）变化 ⇒ 每帧重建
    //   `ColorMatrix`（FloatArray(20)）+ `ColorFilter` 是纯浪费（打开动画
    //   360ms × 120Hz ≈ 43 次无谓分配，且新实例还会让 RenderNode 每帧重设
    //   paint）。dim 不变即复用**同一实例** ⇒ 零分配；数学逐值相同 ⇒ 零观感变化。
    val dimFilterCache = remember { MeloXDimColorFilterCache() }
    // 亮度 → 压暗系数。暗封面 0 压（保持 v94 手感），白封面压到 0.34。
    fun coverDimFactor(): Float {
        val t = smoothStep(coverLuma, MeloXArtworkShadowDimStartLuma, MeloXArtworkShadowDimFullLuma)
        return 1f - (1f - MeloXArtworkShadowWhiteDim) * t
    }

    Box(
        modifier = Modifier
            .matchParentSize()
            .then(transform),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = edge + pad * 2f, height = edge + pad * 2f)
                .blur(blurRadius, BlurredEdgeTreatment.Unbounded),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = model ?: url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { st ->
                    // ★ 采样封面**平均亮度**，用来按亮度自适应压暗副本（见文件头注释）。
                    if (st is coil3.compose.AsyncImagePainter.State.Success) {
                        val bmp = (st.result.image as? coil3.BitmapImage)?.bitmap
                        if (bmp != null) {
                            scope.launch(Dispatchers.Default) {
                                val luma = bmp.averageLuma()
                                if (luma != null && abs(luma - coverLuma) > 0.02f) coverLuma = luma
                            }
                        }
                    }
                },
                modifier = Modifier
                    // ★ 副本**原尺寸**，和本体一样大（源码里先缩到 20% 模糊、再放大 5 倍，
                    //   净尺寸不变 ⇒ `blurredImageView` 里那张图和本体同尺寸居中）。
                    //   ⛔ 别再乘任何 scale：缩到比本体小会被本体完全盖住 ⇒ 一点影都看不见。
                    .size(edge)
                    .graphicsLayer {
                        // ⚠ 这里**只放副本自身的 alpha**（强度），位移/缩放一律走外层
                        //   `transform` —— 与本体同一份 ⇒ 永远同步。
                        this.alpha = alpha
                        // ★ 亮封面必须额外压暗，否则模糊出来是**发光的白环**而不是影子。
                        //   压暗量随封面亮度平滑过渡：暗封面 0 压（维持"其他颜色还行"），
                        //   白封面压到 [MeloXArtworkShadowWhiteDim]（≈0.34）。
                        val dim = coverDimFactor()
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
                    .clip(RoundedCornerShape(cornerRadius)),
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
 * 采样位图的**平均相对亮度**（Rec.709 权重，0=纯黑 1=纯白）。
 *
 * 为什么需要：AM 的阴影是**同色**模糊副本（ShadowImageView.swift 里
 * `shadowAlpha = 1`、`blurredImageView.alpha = shadowAlpha`，**零 colorFilter**）。
 * 它的观感成立有个隐含前提 —— **AM 播放页背景本身就是封面的浅色模糊版**，
 * 白色副本融在同色浅背景里读不出来。
 * MeloX 播放页背景是**深色**的 ⇒ 同一个「不压暗的同色副本」在白色封面上
 * 就是一圈**发光的白环**（用户报障：「其他颜色还行，一到白色就很碍眼」）。
 *
 * ⇒ 必须按封面亮度自适应：亮封面压向灰暗（回归「影」的物理含义），
 *   暗封面不压（否则深色封面糊成黑边，那是 v92 栽的坑）。
 */
private fun android.graphics.Bitmap.averageLuma(): Float? {
    // ⚠ 必须先缩到极小尺寸再读像素：直接getPixels 全尺寸在主线程会卡。
    val w = 16
    val h = 16
    return try {
        val scaled = android.graphics.Bitmap.createScaledBitmap(this, w, h, true)
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        if (scaled !== this) scaled.recycle()
        var sum = 0f
        for (p in px) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            sum += (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
        }
        sum / (w * h)
    } catch (t: Throwable) {
        null
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

/**
 * ★ 亮封面自适应压暗（用户报障：「其他颜色还行，一到白色就很碍眼」）。
 *
 * 根因：AM 的阴影是**同色**模糊副本且 `shadowAlpha = 1`（零 colorFilter），
 * 它成立的前提是 **AM 播放页背景本身是封面的浅色模糊版** ⇒ 白色副本融在同色浅背景里。
 * MeloX 播放页背景是**深色**的 ⇒ 同一个「不压暗的同色副本」在白色封面上
 * 就是一圈**发光的白环**（发光 ≠ 立体感）。
 *
 * ⇒ 只对**亮**封面压暗：暗封面压暗系数 = 1（完全不动，保持「其他颜色还行」），
 *   白色封面压到 [MeloXArtworkShadowWhiteDim]。这样暗封面读成"影"，
 *   亮封面也回归"影"的物理含义（阴影是物体挡住光，不会比周围更亮）。
 */
/** 开始压暗的平均亮度（0~1）。低于此值完全不压。 */
private const val MeloXArtworkShadowDimStartLuma = 0.55f
/** 压到满的亮度。白色封面 ≈0.92 ⇒ 完全落在这之上。 */
private const val MeloXArtworkShadowDimFullLuma = 0.88f
/** 白封面的压暗系数：1 = 不压，0.34 = 压到约三分之一亮度。 */
private const val MeloXArtworkShadowWhiteDim = 0.34f

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
