package com.lladlam.melox.ui.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.lladlam.melox.BuildConfig
import com.lladlam.melox.R
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.lladlam.melox.ui.glass.bottomGlassSurfaceColor
import com.lladlam.melox.ui.glass.bottomLiquidGlassTint
import com.lladlam.melox.ui.glass.meloXLiquidBottomBar
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXSymbolVariant
import com.lladlam.melox.ui.glass.MeloXLiquidInteraction
import com.lladlam.melox.ui.glass.finiteOrZero
import com.lladlam.melox.ui.glass.rememberMeloXLiquidInteraction
import com.lladlam.melox.playback.MeloXAudioReactiveRuntime
import com.lladlam.melox.playback.MeloXAudioReactiveSample
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime

internal val MeloXMiniPlayerHeight = 48.dp

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MeloXIOSMiniPlayer(
    state: MeloXPlaybackUiState,
    onExpand: () -> Unit,
    compactProgress: Float = 0f,
    vertical: Boolean = false,
    dynamicGlassEnabled: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    applyPlayerArtworkScale: Boolean = true,
    /**
     * 「全屏播放页本次主状态推进是**关闭方向**吗」——由 `MeloXApp` 传同一个
     * `playerClosingIntent`（与 host 侧同源）。仅用于下沉回弹的方向判据。
     */
    closingIntent: Boolean = false,
    // ★ 2026-10-15 修「打断动画期间点底栏误触抢回」：上报本 mini 播放器在 window 中的命中矩形，
    //   host overlay 的全屏 pointerInput 据此把 reopen 限定在 mini 区域，点底栏导航不再误入播放页。
    miniPlayerHitRect: MutableState<Rect?> = mutableStateOf(null),
    /**
     * 本栏液态玻璃要采样的背板，由 `MeloXApp` 传 `bottomChromeBackdrop`
     * （遮挡期内传 null ⇒ 走不透明回落，与既有门控行为一致）。
     *
     * 🔴 2026-11-02 新增，修「收缩态迷你栏下沉末段底色从专辑橙跳成主页深灰」。
     *
     *   换色的根因不是「背景不该在那儿」，而是**采样对象换了**：
     *   · 稳态迷你栏在底栏子树里，`LocalMeloXBackdrop` 被显式 provide 成
     *     `bottomChromeBackdrop`（录的是主页 recede 页）；
     *   · 转场期这一份被渲进**共享元素 overlay**，那是 `SharedTransitionLayout` 下的
     *     独立 subcomposition，拿到的是外层 `pageBackdrop`（甚至退化成不透明回落）
     *     ⇒ 两个渲染位置根本不是同一个 Backdrop 对象，转场一结束就换源，
     *     一帧内 ΔR≈86 = 用户说的「闪一下」。
     *
     *   ⛔ 不要再用「让播放页背景按 p 淡出」来掩盖：那拿整页观感去补一个对象不统一的
     *     缺陷，用户已判「这种显隐对观感的牺牲太大了」。
     *   ✅ 本参数把同一个 Backdrop 对象显式喂进材质（overlay 副本与稳态节点是同一次
     *     composable 调用，参数天然一致）⇒ 逐帧采同一份内容，换源事件不存在。
     */
    glassBackdrop: com.kyant.backdrop.Backdrop? = null,
) {
    if (!state.hasMedia) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeWidth = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val contentOffset = remember { Animatable(0f) }
    // ★★ 2026-10-28：**跟手位移与动画位移分离**（修「横滑时手指往左、歌曲反而往右」）。
    //   旧实现把跟手也塞进 `contentOffset`：`scope.launch { contentOffset.snapTo(contentOffset.value + dragAmount) }`
    //   —— 既是**增量**又是**跨协程**，于是有两条必然踩中的路：
    //     ① `onDragStart` 里的 `snapTo(0f)` 也是 `launch` 的，可能与第一个 move 的
    //        `snapTo` **乱序**执行 ⇒ 已累计的位移被抹回 0；
    //     ② 切歌后 `contentOffset` 正停在 `±swipeWidth`，若 `snapTo(0f)` 晚到，
    //        第一个 move 就在 `±swipeWidth` 上做加法 ⇒ 内容先**窜到一侧**再往回走，
    //        而 `coerceIn(±0.86w)` 把它钉在屏边 ⇒ 读起来就是「手指往左、歌曲往右」，
    //        并且下一次拖动从屏边起算 ⇒ 「不能连续切歌」。
    //   ⇒ 跟手改用**同步**的 [swipeDragOffset]（拖动中它是唯一真值，零协程、零乱序），
    //     `contentOffset` 退回它该干的活：出场 / 回弹的动画。
    //     交接（`onDragEnd`）时先 `contentOffset.snapTo(swipeDragOffset)` 再撤
    //     [swipeDragging]，两值相等 ⇒ 无缝、不闪。
    var swipeDragOffset by remember { mutableFloatStateOf(0f) }
    var swipeDragging by remember { mutableStateOf(false) }
    // 拖动拍号：`onDragEnd` 的交接写在协程里（要先 `snapTo` 再撤 [swipeDragging]），
    // 而用户可能在这 1~2 帧内已经开始**下一拍**拖动。协程里比对拍号，
    // 变了就放弃交接 —— 否则它会把新拖动刚设好的 `swipeDragging` 关掉
    // （症状就是「不能连续切歌」）。
    var swipeDragEpoch by remember { mutableIntStateOf(0) }
    // ── 按压高光 / 整栏放大：**每颗按钮各持一份**（2026-10-28 第三次改）──────────
    //   ⚠ 原先是**一份**、手势挂在整条栏上（官方 surface 语义）。但迷你栏同时是
    //     横滑切歌手柄 ⇒ 每次滑歌都会「按下先胀、手指一动又瘪回去」，
    //     连滑几下就是用户报的「又大又小」。⇒ 手势下放到两颗离散按钮，
    //     滑歌时 `pressProgress` 恒 0、整栏纹丝不动。
    //   ⚠ 为什么是**两份**而不是共用一份：`PublicInteractiveHighlight` 记录的手指
    //     坐标是**手势节点自己的局部坐标**，而高光要画在对应的按钮上 ⇒ 一份只能画在
    //     一颗按钮上。两份各画各的，坐标天然对齐，不需要任何窗口坐标换算。
    // 🔴 2026-10-28（用户第二条指令：「保持不变，但高光还是要能跟随手指移动」）：
    //   两份交互都传 `softwareFocusGlow = true`。按压的三笔里，只有「以手指为圆心的
    //   径向辉光」原本依赖 AGSL（API 33+）；现在的处理是**换绘制手段，不是删功能** ——
    //   低版本用 `Brush.radialGradient` 复刻同一支 shader（圆心=手指、半径=1.5×短边、
    //   峰值=focusGlowAlpha、Plus 混合），所以：
    //     · API 33+      → 走 RuntimeShader（上游原路径，逐像素不变）
    //     · API 33 以下  → 走纯 Compose 径向渐变，**同样跟随手指移动**
    //     · 两种情况下都只有「按住这两颗按钮」才会有光斑（`progress > 0` 才画）
    //   ⚠ 上游 fallback 的「整颗 30dp 圆钮均匀发白」被替换掉了（那不是光斑，也不跟手）。
    //   ⚠ **手势照挂**（`gestureModifier`）：它是 `pressProgress` 的唯一数据源，
    //     撤了整栏缩放的 Q 弹直接消失。
    //   ⚠ 这是**只在迷你栏生效**的选择：其余高光调用点（底栏水珠/面板、玻璃按钮、
    //     设置下拉）保持 kyant0 上游行为不变。
    // 🔴🔴 2026-10-28（用户：「高光只在右边四分之一区域生效，多了会被截断，而且位置在
    //   手指下面」）：上一版的 `allowPointerGlowOverflow` 是**错的**（给 shader 的
    //   `position` 多加了 `pad` 平移，整颗光斑被推到手指右下 60dp）。已整段删除，
    //   现在回到上游口径（`position` = 手指局部坐标、整节点 `drawRect`）。
    //   真正的「不跟随」原因是**半径**：上游 1.5×短边 ⇒ 平顶半径 30dp > 40dp 圆钮的
    //   半对角 28.3dp ⇒ 整颗按钮都在平顶里，手指怎么动都一个样。
    //   ⇒ 这两颗 40dp 圆钮传 `focusGlowRadiusRatio = MINI_FOCUS_GLOW_RADIUS_RATIO`(=0.5)，
    //     让渐变环（0.5r~r）落在按钮内部，光斑才会真的跟着手指跑。
    // ★ 按压高光（用户 2026-10-28：「按钮就正常高光，不拓展了」）：
    //   直接走 `rememberMeloXLiquidInteraction()` 默认 —— 标准 kyant0 行为：
    //   · 光斑**被圆钮形状裁住**（挂在 `clip(CircleShape)` 之内，见 `MiniVectorButton`），
    //     不做任何溢出 / 椭圆；
    //   · API 33+ 走 AGSL `RuntimeShader`（手指处径向辉光，跟随手指）；
    //   · 33 以下走上游 fallback（光斑折进整块矩形）= 官方原版行为。
    val playPress = rememberMeloXLiquidInteraction()
    val nextPress = rememberMeloXLiquidInteraction()

    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    var pendingDirection by remember { mutableIntStateOf(0) }
    var pendingOutgoingMediaId by remember { mutableStateOf<String?>(null) }
    // ★★ 2026-10-06 修「横滑切歌后抽搐闪回上一首」——「本次目标」快照。
    //   在 `onDragEnd` 越过阈值那一刻写入（实时读 state 的 queue/currentIndex），
    //   直到本次横滑完全落定才清。
    //
    //   ⚠⚠ **必须快照、不能每帧现算**（上一轮的错误就在这）：hold 期间
    //     `state.currentIndex` 可能因为 `PlaybackCommands.prioritizeManualQueue()`
    //     重排队列、或末位/首位回绕而**漂移**，此时 `neighborOf(pendingDirection)`
    //     会取到「再下一首」甚至「上一首」⇒ 那一帧迷你栏正中被另一首歌占据 ——
    //     用户看到的正是这个（「闪回上一首」）。
    //   ⇒ 快照锁定"用户亲眼看着它滑进来的那一首"，后续 state 怎么变都不影响它。
    var committedEntry by remember { mutableStateOf<MeloXQueueEntry?>(null) }
    var committedDirection by remember { mutableIntStateOf(0) }
    var reactiveSample by remember { mutableStateOf(MeloXAudioReactiveSample.Idle) }

    // ── 传输键动画（2026-10-28）：迷你栏的 play/pause 与 next 换成**播放页同款** ──
    //   三个控制器都在本 composable 顶层 `remember` ⇒ 竖屏 / 横屏（`vertical`）两个分支
    //   共用同一份状态，转屏不会把动画丢掉。
    //   · `miniPlayPauseMorph`：与 `ScenePlayPauseButton` 共用同一套「收缩→谷底换字→弹回」；
    //   · `miniNextPulse`：与 `SceneTransportButton` 共用同一台「传送带」；
    //   · `miniBarPress`：整条栏的**按压**放大（+ 手指处高光）—— 直接采用 kyant0 官方
    //     Button 的按压模型，进度由手指决定（按住撑着、抬手才收），见 `MeloXTransportMotion.kt` ③。
    //   实现与常量全部取自软件内已有代码（`PublicInteractiveHighlight` 就是官方那份的移植）。
    val miniPlayPauseMorph = rememberMeloXPlayPauseMorph(state.isPlaying)
    val miniNextPulse = rememberMeloXConveyorPulse()
    val miniBarPress = rememberMeloXMiniBarPress(playPress, nextPress)

    LaunchedEffect(state.mediaId, pendingDirection) {
        if (pendingDirection != 0 && state.mediaId != pendingOutgoingMediaId) {
            contentOffset.snapTo(0f)
            pendingDirection = 0
            pendingOutgoingMediaId = null
            committedEntry = null
            committedDirection = 0
        } else if (pendingDirection == 0 && committedEntry == null) {
            contentOffset.snapTo(0f)
        }
    }

    LaunchedEffect(pendingOutgoingMediaId) {
        if (pendingOutgoingMediaId == null) return@LaunchedEffect
        kotlinx.coroutines.delay(1_500L)
        contentOffset.animateTo(0f, spring(dampingRatio = .72f, stiffness = 430f))
        pendingDirection = 0
        pendingOutgoingMediaId = null
        committedEntry = null
        committedDirection = 0
    }

    // ⚠⚠⚠ 2026-10-22 动画流畅度：**返回 State，不解包、不在组合体里读**。
    //
    //   旧实现 `val value by …; value` ⇒ 本 composable 每帧重组（打开 360ms ≈ 32 次），
    //   而迷你栏是**常驻首屏**的组件 ⇒ 转场全程它陪着重组 32 次，还连带把
    //   `MiniVectorButton` / `MiniDancingBars` 这些子组件一起拖下水。
    //
    //   ⇒ 与 `MeloXIOSNowPlayingSharedHost` 同一套三层模型：
    //     · 视觉量（alpha / scale / 圆角）→ `graphicsLayer{}` 里读 `.value`
    //     · 行为判据（`> 0.01f` / `> 0.05f` / `<= 0.001f`）→ `derivedStateOf` 收成离散 State
    //     · 需要按值透传子组件的 → 传 `() -> Float`，让子组件自己去读
    val expansionProgressState: State<Float> = if (animatedVisibilityScope != null) {
        animatedVisibilityScope.transition.animateFloat(
            // 🔴 2026-10-28 16:5x：**必须传方向**。关闭方向的外壳弹簧慢 10%
            //   （`MeloXPlayerCloseSpringSlowdown`），内容层若仍用打开方向那条曲线，
            //   同一个 `f` 会算出差 1.4% 的 `p`（≈41px）⇒ 封面 / 圆角与外壳几何错拍
            //   （10-03 踩过一次：下沉先到位、播放器还在动）。
            transitionSpec = {
                meloXPlayerLinearFloatSpec()
            },
            label = "mini-player-expansion-progress",
        ) { visibility ->
            if (visibility == EnterExitState.Visible) 0f else 1f
        }
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    // 逐帧状态一律走 lambda（值传参会把**本**作用域每帧重组）
    val expansionProgressProvider: () -> Float = remember(expansionProgressState) {
        { expansionProgressState.value }
    }

    // ── 过渡态判定用的**线性时钟**（2026-10-03）─────────────────────────────────
    //   ★★ 关闭尾段的「迷你栏下沉 → 回弹」**已移入共享元素 bounds 几何**
    //      （`meloXPlayerSharedBoundsTransform`，见 `MeloXPlayerTransitionSpec`）：
    //      morph 期间外壳与封面由**两个独立 overlay** 渲染 —— 加在节点上的位移
    //      （祖先 / 内部 `graphicsLayer`、祖先 `Modifier.offset`）都读不出可靠下沉；
    //      只有 **bounds 几何**才是 overlay 唯一服从的输入，且外壳/封面天然同步。
    //   ⇒ 这里只留一条**与 transition 同源的线性时钟**，用途是判定「是否还在过渡中」
    //     （给按钮/点击门控）。稳态恒 1。
    val avScope = animatedVisibilityScope
    // ⚠ 传值的是下面那行 `by`（组合期读，供 `isTransitioning` 用）；
    //   这里额外留一个 **State 引用**，给绘制期读者（`miniLiquidScaleProvider`）用 ——
    //   绘制期读 `.value` 只让那个图层失效，不会把本组件按帧重组。
    val miniBounceClockState: State<Float> = if (avScope != null) {
        avScope.transition.animateFloat(
            transitionSpec = {
                tween(durationMillis = meloXPlayerTweenDurationMillis(), easing = LinearEasing)
            },
            label = "mini-player-settle-clock",
        ) { visibility -> if (visibility == EnterExitState.Visible) 1f else 0f }
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val miniBounceClock by miniBounceClockState
    // ⚠⚠⚠ 修「返回过程中还能点按钮」**且不能把迷你栏点死**（用户报障：「下滑关闭后
    //   点击迷你栏没有任何打开动作」）：
    //   判据**绝不能用 `transition.currentState != targetState`** —— 本页 transition 由
    //   `SeekableTransitionState` 驱动（手势 seek + settle），**下滑关闭后
    //   `currentState` 会长期 != `targetState`** ⇒ 那个判据会把迷你栏**永久禁用**。
    //   ⇒ 改用**与 transition 同源的线性时钟**：关闭方向 0→1，**恰好 1 = 完全就位**；
    //     稳态（迷你栏静止）恒为 1 ⇒ 只有 `< 0.98` 才算「过渡中」。
    val isTransitioning = avScope != null && miniBounceClock < 0.98f

    // The mini visualizer is hidden during expansion. Stop its 20 Hz state
    // updates so the shared-bound transition can spend its frame budget on
    // layout and glass rendering instead.
    // 「迷你栏已开始让位」——行为判据，收成离散 State（只在 0.01 穿越时翻一次）
    val miniChromeHidden by remember(expansionProgressState) {
        derivedStateOf { expansionProgressState.value > 0.01f }
    }
    LaunchedEffect(state.mediaId, state.isPlaying, miniChromeHidden) {
        if (miniChromeHidden || !state.isPlaying) {
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
    //
    // ⚠ 2026-10-22：两条 alpha + 封面 scale 全部改成**绘制期求值的 provider**
    //   —— 公式与阈值一字未改（`smoothStep` 参数逐字相同），只是不再在组合体里读。
    val miniChromeAlphaProvider: () -> Float = remember(expansionProgressState) {
        { 1f - smoothStep(expansionProgressState.value, 0.10f, 0.58f) }
    }
    // ⚠ `CompositionLocal` 不能在 draw 期读 ⇒ 在这里（组合期）捕获一次，供绘制期取色使用。
    val miniBarsOnSurface = MaterialTheme.colorScheme.onSurface
    val miniSurfaceAlphaProvider: () -> Float = remember(expansionProgressState) {
        // ★★★ 2026-11-02（第十八轮，回到**上游逐字**窗口 `(0.02, 0.48)`）。
        //   历史：fork 曾把它改成 (0.40,0.48) → (0.44,0.52)，理由是「玻璃淡出必须落在
        //   黑兜底不透明段（p ≥ 0.44）之内，否则主页背景从四角漏出来」。
        //   但那套推理的前提是**玻璃盒跟随 morph 矩形**（见下方玻璃盒注释）——
        //   矩形被拉到全屏时，玻璃就是一块全屏面板，任何透明度差错都会在四个角上放大成弧。
        //   ⇒ 现在玻璃盒恢复成上游的**固定迷你栏尺寸**（永远只有 48dp 高），
        //     玻璃在物理上不可能变成「大面板」，窗口就可以回到上游原值。
        //   ⚠ 与上游 `miniSurfaceAlpha = 1 - smoothStep(p, 0.02f, 0.48f)` **逐字相同**。
        { 1f - smoothStep(expansionProgressState.value, 0.02f, 0.48f) }
    }
    // ★★★ 2026-11-03（用户需求）：**转场期把液态折射连续降级，只在落定后恢复完整液态。**
    //
    //   用户口径：「播放页打开关闭动画，迷你栏出现的阶段都是完全完整液态玻璃……
    //     能不能在动画期间降低液态效果甚至关闭液态，只要能被肉眼识别的阶段使用液态玻璃
    //     就行（当然切换要平滑）」。
    //
    //   ### 为什么用 `miniBounceClock` 而不是 `expansionProgress`
    //   `expansionProgress` 是**内容层弹簧**，它与 `sharedBounds` 的几何**不同源**
    //   （既有实测：「内容层 p=1000 时几何只有 937/1000」），而且它在关闭尾段的
    //   22dp 下沉期间已经落回 ≈0 ⇒ 用 p 做判据会让「下沉那 38% 墙钟」也算稳态，
    //   恰好在背景移动最明显的一段把液态全开 —— 与本需求相反。
    //
    //   `miniBounceClock` 是**与 transition 同源的线性时钟**（`LinearEasing` tween）：
    //     · 关闭方向 0 → 1，**1 = 完全就位**（下沉结束、迷你栏静止）；
    //     · 打开方向 1 → 0（起点仍是完整液态，随动画淡出）；
    //     · 稳态恒 1。
    //   ⇒ 用它的**末段**当恢复窗口，天然覆盖「外壳收缩 + 下沉」整段动画。
    //
    //   ### 窗口 [0.86, 1.0] 是怎么定的
    //   转场 tween 约 360ms ⇒ 这 14% ≈ **50ms（90Hz 下 4~5 帧）**，是肉眼能读成
    //   「渐渐润起来」而非硬切的最短长度；再窄（如 0.92）就只剩 2~3 帧，会看出突现。
    //   而 0.86 处下沉位移 = `sin(π·(0.86−0.62)/0.38) × 22dp ≈ 20dp`，仍在下沉中 ——
    //   ⇒ 恢复段落在「背景还在缓慢移动」的区间内，液态是**渐显**的，不会被读成闪。
    //   ⛔ 别把下界往 0.62 之前推：那会让整段下沉都在折射流动的背景，就是前 20 轮
    //     反复报的「四角随背景闪」。
    //
    //   ### 平滑的技术前提（已实测，别怀疑）
    //   反编译 `io.github.kyant0:backdrop:2.0.0` 的 `LensKt`：AGSL 里
    //   `refractionHeight` / `refractionAmount` / `cornerRadii` **全是 uniform**，
    //   且 `RuntimeShaderCache` 按源码字符串缓存 shader ⇒ 逐帧改半径只走
    //   `setFloatUniform`，**不重编译、不新建 shader**。
    //   ⇒ 强度可以连续插值，这就是「平滑」的实现方式（不是靠布尔开关遮掩）。
    //
    //   ⚠ `avScope == null`（非共享转场路径：横屏大卡片 / 其它布局）时**必须返回 1**
    //     —— 那时 `miniBounceClockState` 恒 0，套同一个公式会把液态永久关死。
    val miniLiquidScaleProvider: () -> Float = remember(avScope, miniBounceClockState) {
        if (avScope == null) {
            { 1f }
        } else {
            // ★★★ 2026-11-03（第 26 轮，时机定案）：**lens 只在「完全收拢成迷你栏」
            //   (p < 0.02) 才开，过渡全程（p ≥ 0.02，含手动拖拽任意位置）一律关**。
            //
            //   ### 漏缝真正出现的窗口
            //   不是整段动画，而是**玻璃淡入、外壳还略大于 48dp 的那段 `p∈(0.02, 0.48)`**。
            //   此段若 lens 开着，会把玻璃边缘向内折、把外壳底色（封面色/暂停黑）从四角探出。
            //
            //   ### 之前几版为什么失败（别再走回头路）
            //   · 第 23/24 轮用 `miniBounceClock`（关闭 0→1）做窗口 0.86/0.97/0.9995：
            //     `miniBounceClock` 与 `expansionProgress`(p) **不同源**（L365 自陈），
            //     外壳已缩成迷你栏、玻璃已显示那段它仍把 lens 开着 ⇒ 露馅。
            //   · 第 25 轮把 lens 直接绑 `miniSurfaceAlphaProvider()`（= 玻璃透明度）：
            //     `p` 刚过 0.02 时玻璃还实心、lens 却仍≈全开 ⇒ 漏；且那段 smoothStep 渐变
            //     被肉眼抓到＝用户说的「半透明变液态的过程」。
            //
            //   ### 本版时机（用户口径：出现时关掉、关的时机够好就成）
            //   直接用玻璃显示进度 p 本身：
            //     · **p < 0.02**（完全收拢 / 稳态）：玻璃 alpha 已恒=1（实心）、外壳=48dp=玻璃
            //       ⇒ lens 开，保留「之前那种液态」稳态观感（含 48dp 小面固有极小折射残余，
            //       用户接受）。此点玻璃实心，lens 在其下硬切**看不到「开液态」突变**。
            //     · **p ≥ 0.02**（整个过渡 + 手动拖拽任意位置）：lens 关 ⇒ 玻璃边缘不内折，
            //       四角不透出外壳底色；且不存在独立渐变过程。
            //   ⇒ 关闭方向：整段收缩 lens 关，落定 (p→0) 瞬间才开 —— 与「出现时关掉」一致；
            //      打开方向：起点 (p=0) lens 开，p 一过 0.02 立刻关（玻璃仍实心 ⇒ 不可见）。
            //   ⚠ `avScope == null`（非共享转场：横屏大卡片等）恒 1f。
            //
            //   ### 技术前提（沿用上方 0.86 版注释，已实测）
            //   AGSL 的 `refractionHeight`/`cornerRadii` 全为 uniform，`RuntimeShaderCache`
            //   按源码缓存 ⇒ 强度逐帧插值是 `setFloatUniform`，不重编译、不新建 shader。
            { if (avScope == null) 1f else if (expansionProgressState.value < 0.02f) 1f else 0f }
        }
    }
    // 「chrome 还看得见」——原判据 `miniChromeAlpha > 0.05f` 的等价离散形式（阈值语义不变），
    // 供 `MiniDancingBars` 的 active 使用（它决定跑不跑 20Hz 采样，不能是逐帧值）。
    val miniChromeVisible by remember(expansionProgressState) {
        derivedStateOf {
            1f - smoothStep(expansionProgressState.value, 0.10f, 0.58f) > 0.05f
        }
    }

    val playerArtworkScale = if (
        !applyPlayerArtworkScale || MeloXSettingsRuntime.reduceMotion || !MeloXSettingsRuntime.artworkMotionEnabled || state.isPlaying
    ) 1f else 0.74f
    val sharedArtworkScaleProvider: () -> Float =
        remember(playerArtworkScale, expansionProgressState) {
            {
                1f + (playerArtworkScale - 1f) * smoothStep(
                    expansionProgressState.value,
                    0.30f,
                    0.88f,
                )
            }
        }
    // ★ 缩回期「下沉 → 回弹」由**共享元素 bounds 几何**提供（见 `MeloXPlayerTransitionSpec`）：
    //   morph 期外壳由 overlay 渲染，只有 bounds 几何是 overlay 唯一服从的输入，且外壳/封面
    //   天然同步。这里 shell 的 `BoundsTransform` 在关闭方向叠加尾段一记下沉回弹。
    //
    // ⚠⚠⚠ 2026-10-06 修：此处原来硬编码 `{ true }` —— **实现与上面注释不符**
    //   （注释说「在关闭方向」，代码却对**所有**方向生效）。真机探针（`mxdiag.txt`）
    //   逐帧实证的后果：
    //   · 「下拉一点松手**回弹**」（`settle collapse=false`）时本通道仍 `en=true`，
    //     在 f=0.628→0.901 段施加 **0→87→0 px** 的一整记下沉 ⇒ 用户报障
    //     「播放器会下沉抽搐一下再往上」；
    //   · 点迷你栏**打开**时（`openPlayer`）同样误差 (full→mini) 这一侧 ⇒ 误沉，读成「很难看」。
    //   ⚠ host 侧同一函数用的是 `closingIntent || targetState == PostExit`，两侧判据必须同义，
    //     否则共享元素的两个 overlay 会一个沉一个不沉（探针里同一帧 `en` 一 True 一 False）。
    //
    //   ⚠ 传 **lambda** 且 `remember` 的 key **不得含方向**（同 host 侧的警告）：手势下滑
    //     越过阈值那一刻若实例被重建 ⇒ Compose 重新推导 bounds spec ⇒ **回弹被丢掉**。
    val closingIntentState = rememberUpdatedState(closingIntent)
    val sinkPeakPx = with(LocalDensity.current) { MeloXPlayerSinkPeakDp.dp.toPx() }
    val sharedSinkBoundsTransform = remember(sinkPeakPx) {
        // ★ 2026-10-29 修复：本 morph 就是「外壳」那一个（迷你栏侧源）⇒ **必须显式**
        //   发布每帧 bounds 给底部 chrome 覆盖判据（`MeloXShellGeom.record` 曾被漏掉 ⇒
        //   整条遮挡门控失效 ⇒ 18.25MB/帧白付）。
        //   ⚠ 封面 artwork 也用同一个工厂，但它**不是**外壳，绝不能发布（见下方 KDoc 踩点）。
        meloXPlayerSharedBoundsTransform(
            sinkPeakPx = sinkPeakPx,
            publishShellGeom = true,
            // ★ 2026-10-28 22:0x：抗首帧快照竞态。`closingIntent` 在 sharedBounds 首帧
            //   可能还没翻（实测 p 走 k=8.70），⇒ 用 AnimatedVisibility 的 `targetState` 兜底：
            //   关闭时迷你栏**进入** ⇒ `Visible`，首帧必已置位、不竞态。与 host 侧
            //   `targetState == PostExit` 语义同义（两侧必须一致，否则共享元素一个沉一个不沉）。
            sinkEnabled = { closingIntentState.value },
        )
    }
    /**
     * **封面** morph 专用的 bounds transform（参数与外壳逐字相同，只是**不发布几何**）。
     *
     * ⚠⚠ 2026-10-26 踩点：迷你栏里的封面 `sharedElement`（`sharedPlayerArtworkKey`）原本
     *   直接复用上面的 `sharedSinkBoundsTransform` —— 给外壳开 `publishShellGeom` 后，
     *   封面也跟着发布每帧 bounds。而封面的 `initial ∪ target` 是「迷你封面 ∪ 大封面」，
     *   **不是窗口矩形** ⇒ 覆盖判据的坐标系自检每帧失败并 `reset()`，把刚判好的 `true`
     *   打回 `false`（实测：闸门每帧 true→false 抖动，一次开合几百条翻转日志）。
     *   ⇒ 拆成两个 `remember` 实例。两者**参数完全相同**（同 `sinkPeakPx` / 同方向 lambda），
     *     所以下沉行为逐帧一致、封面不会错位；差别只在是否发布几何。
     */
    val sharedArtworkBoundsTransform = remember(sinkPeakPx) {
        meloXPlayerSharedBoundsTransform(
            sinkPeakPx = sinkPeakPx,
            // ★ 2026-10-28 22:0x：抗首帧快照竞态。`closingIntent` 在 sharedBounds 首帧
            //   可能还没翻（实测 p 走 k=8.70），⇒ 用 AnimatedVisibility 的 `targetState` 兜底：
            //   关闭时迷你栏**进入** ⇒ `Visible`，首帧必已置位、不竞态。与 host 侧
            //   `targetState == PostExit` 语义同义（两侧必须一致，否则共享元素一个沉一个不沉）。
            sinkEnabled = { closingIntentState.value },
        )
    }
    val sharedShellModifier =
        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = sharedPlayerShellKey()),
                    animatedVisibilityScope = animatedVisibilityScope,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                    boundsTransform = sharedSinkBoundsTransform,
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    // ⚠⚠⚠ 2026-10-06 **必须为正**：本迷你栏侧（含**液态玻璃**）在 overlay 内
                    //   要浮在目标端（播放页，默认 `0f`）**之上**。
                    //
                    //   为什么：`sharedShellModifier` 这个 sharedBounds 节点**把迷你栏的
                    //   液态玻璃一起包在里面**（见下方 `meloXLiquidBottomBar` 处注释：
                    //   "Keep the glass surface out of the shared-bounds node itself"
                    //   —— 指的是不参与 **bounds 几何**，但**仍在节点内、仍走 overlay 渲染**）。
                    //   ⇒ 转场期间迷你栏这一份被渲进共享元素 overlay（`zIndex` 100f 档，
                    //     浮在播放页 `zIndex(20f)` 之上）。
                    //   若把本侧压到目标端之下（曾经的 `-1f`），则**播放页的副本盖住迷你栏**，
                    //   而播放页副本里含**专辑流光背景** ⇒ 肉眼即「**流光盖住迷你栏液态玻璃**」
                    //   （用户报障：「流光本来在动画执行过程中就有，正确是在迷你栏的下一层级，
                    //     现在是偶尔误盖住」）。
                    //
                    //   ⚠ 历史：`-1f` 是 2026-10-06 为修「轻微下拉下沉」加的，但那个症状的
                    //     真根因后来被定位为 **`expansionProgress` 回弹时先反向**
                    //     （探针实证 `P` 行 0.942→0.777，见 `MeloXIOSNowPlayingSharedHost`
                    //     里 `expansionProgress` 的说明）⇒ 该降序保护**既无效又有害**，已撤销。
                    //   ⚠ 封面是**独立的 sharedElement**（本侧 `99f` / host 侧 `100f`），
                    //     不受此处影响，仍由 host 那一份在上渲染。
                    zIndexInOverlay = 1f,
                )
            }
        } else {
            Modifier
        }

    val compact = compactProgress.coerceIn(0f, 1f)
    // ★ 展开态封面 40dp 太大（用户报障：展开态封面过大）。栏高 48dp、内容区
    //   （48 − 4×2 = 40dp），40dp 封面正好顶满上下边距、显得塞；收至 32dp 后
    //   上下各留 4dp，与 12dp 侧边距形成呼吸。收缩态 26 → 28dp（用户报障「收缩态
    //   的封面小了点」）。
    val artworkSize = lerpDp(40.dp, 30.dp, compact)
    val artworkRadius = 6.dp
    // ⚠ 2026-10-02 23:11 移除「morph 期把封面圆角收为直角」（`morphArtworkRadius`
    //   窗口 0.00~0.18）。这个窗口是第九轮基于**错误根因**加的（当时以为外壳
    //   Lamé 圆角在切封面），真根因是 `renderInOverlayDuringTransition = true`
    //   让**源侧 clip 根本不参与 morph** —— 所以源侧这个窗口从一开始就纯属多余。
    //
    //   它自己反而制造了用户报障的「关闭瞬间封面变直」：关闭时 p: 1→0，
    //   `1 - smoothStep(p, 0, 0.18)` 在 p ≥ 0.18 期间恒为 0（直角），
    //   实测前 **80ms 圆角一直是 0dp**，直到 p<0.18（约 260ms）才突增回 6dp
    //   ⇒ 正是「播放页封面圆角瞬间变直，直到动画结束变成小封面圆角才正常」。
    //
    //   ⇒ 恒定 6dp。morph 期的圆角连续性由 Overlay 副本
    //     （`MeloXIOSNowPlayingSharedHost` 的 `targetRadius`）负责。
    //
    // ★★ 2026-10-03 修正（用户报障「还是直接变」，HUD 读数实证）：
    //   HUD 显示 `p` 在变、`r` 在变、**但 `size` 恒为 296dp** ——
    //   因为播放页那个 Box 的 `targetSize = lerpDp(fullArtworkSize, 72.dp,
    //   pageFrameProgress)`，而 `pageFrameProgress` **完全不受拖拽影响**
    //   ⇒ 真正让封面缩小的不是它，是 `sharedBounds` **外壳**。
    //   于是形态被拆成两半：
    //     · 尺寸 ← 外壳插值（两端 Box 各自声明，sharedBounds 中间值）
    //     · 圆角 ← Overlay 副本那个**恒定 296dp** Box 上的 clip
    //   而两端 shape 必须**逐帧一致**，Compose 才会在 Overlay 里正确合成。
    //   之前迷你侧恒 6dp、播放侧 `lerpDp(6,12,p)` ⇒ 两端不一致 ⇒ 观感突变。
    //   ⇒ 两端统一用**同一个表达式**（下面），任意 p 下两端口径完全相同。
    //
    //   ⚠ 2026-10-22：表达式一字未改，但从**组合期**挪到**绘制期**——
    //   `clip()` 内部就是 `graphicsLayer(shape, clip = true)`，所以这里手写同义节点；
    //   `RoundedCornerShape` 是 data-class 风格（实现了 equals，按半径比较），
    //   半径不变时不会重设 outline。原实现每帧在组合体里 new 一个 Shape ⇒ 每帧重组。
    val artworkClipModifier = remember(expansionProgressState) {
        Modifier.graphicsLayer {
            // 6dp → 12dp，与 `MeloXIOSNowPlayingSharedHost` 的 `targetRadius` 逐字一致。
            shape = RoundedCornerShape(
                (6f + 6f * expansionProgressState.value.coerceIn(0f, 1f)).dp,
            )
            clip = true
        }
    }
    // `morph` 期间必须取消内容层 clip（Capsule 半径 = 行高/2，行高被 RemeasureToBounds
    // 拉到全屏时会暴涨成巨大圆形 —— 见下方使用点注释）。判据收成离散 State，
    // ⚠ 阈值是 **0.001**，与其它地方的 0.01 / 0.05 不是同一个数，别合并。
    val miniContentClipped by remember(expansionProgressState) {
        derivedStateOf { expansionProgressState.value <= 0.001f }
    }
    val compactNextAlpha = 1f - smoothStep(compact, 0.04f, 0.50f)
    // 「下一首」的 alpha = chromeAlpha × compactNextAlpha（后者由**外部参数** compactProgress
    // 决定，不是逐帧量）⇒ 做成 provider 供 MiniVectorButton 绘制期读，观感等价。
    val miniNextAlphaProvider: () -> Float =
        remember(miniChromeAlphaProvider, compactNextAlpha) {
            { miniChromeAlphaProvider() * compactNextAlpha }
        }
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
    // ★★ 2026-10-06 修「迷你栏横滑切歌后抽搐闪回上一首」────────────────────────────
    //   症状：横滑切歌，新歌刚滑到位，迷你栏会「抽搐」一下、**短暂闪出队列里另一首歌**
    //        （站在用户视角往往就是「上一首」），下一帧才回到真正的当前曲目。
    //
    //   根因（两处叠加，都在本段）：
    //   ① 缺「已提交」判据 ⇒ 播放状态切到新歌那一帧，`holdIncoming` 当帧就翻 false，
    //      可把 `contentOffset` 归零的 `snapTo(0f)` 在 `LaunchedEffect` 里、**晚一帧**
    //      才执行。于是有整整一帧：
    //        · 主行 = 新歌（`state.title`），却仍带着 ±swipeWidth 的出场偏移 ⇒ 画到屏外；
    //        · 相邻预览行按**新的** `currentIndex` 取到「新歌的再下一首」，
    //          而它的 `translationX = visualOffset − dragDirection×swipeWidth` 恰好 = 0
    //          ⇒ 迷你栏正中被那一首「再下一首」占据。
    //   ② 原 `pendingIncoming` 是**在 `onDragEnd` 里赋值的普通局部量**
    //      （`pendingIncoming = adjacentEntry`）。但 `onDragEnd` 活在
    //      `Modifier.pointerInput(state.mediaId) { … }` 的块里 —— 该块**只在 key 变化时
    //      重新执行**，闭包捕获的 `adjacentEntry` 是**上一次 key 变化那一帧组合的旧快照**
    //      ⇒ 要么恒为 `null`（`dragDirection` 当时是 0），要么是上一次落定后残留的**错误邻居**。
    //
    //   ⇒ 修法：①「要顶在 0 位的那一首」改成**由当前帧 state 现算**（`neighborOf`），
    //     不再跨协程传值；②新增 `swipeCommitted`：媒体已切走时**当帧**就把整块内容钉回
    //     0 位并显示 `state` 的真实曲目，不再依赖晚一帧的 `snapTo`。
    val swipeCommitted = pendingDirection != 0 && state.mediaId != pendingOutgoingMediaId

    // 「方向 → 队列邻居」的唯一实现。⚠ 必须在组合体里用**当前 state** 现算：
    //   `state.queue` / `state.currentIndex` 是 Compose state 读，任何时刻取到的都是最新值；
    //   而 `onDragEnd` 闭包里那个旧快照取不到（见上面 ②）。索引回绕口径与
    //   `nextFromMiniPlayer()` / `previousFromMiniPlayer()` 的 `playQueueIndex` 一致。
    val neighborOf: (Int) -> MeloXQueueEntry? = { direction ->
        when (direction) {
            -1 -> state.queue.getOrNull(
                if (state.currentIndex + 1 < state.queue.size) state.currentIndex + 1 else 0,
            )
            1 -> state.queue.getOrNull(
                if (state.currentIndex > 0) state.currentIndex - 1 else state.queue.lastIndex,
            )
            else -> null
        }
    }

    // Keep the song that was already sliding in. Replacing that row with the
    // newly current track tears the layout down and flashes the next title.
    //
    // ⚠ 判据用 `pendingDirection != 0` 而**不是**「偏移量 > 0.82×swipeWidth」：
    //   `pendingDirection` 只在 `contentOffset.animateTo(±swipeWidth)` **await 返回之后**
    //   才被置上 ⇒ 它天然表示「出场动画已经正好跑完、相邻预览恰好停在 0」这一刻，
    //   此时把主行切到那一首是**逐像素无缝**的。若改成用偏移量阈值（旧写法 0.82），
    //   切换会发生在预览还差 0.18 屏宽没走完时 ⇒ 那一首会**瞬间横跳 0.18 屏宽**。
    //
    // ★★ 2026-10-06（第二轮）：目标曲目改用 `committedEntry` **快照**（见其声明处），
    //   不再 `neighborOf(pendingDirection)` 现算 —— 现算会在 hold 期间随
    //   `currentIndex` 漂移，取到「再下一首 / 上一首」⇒ 抽搐闪回。
    val holdIncoming = !swipeCommitted && pendingDirection != 0 && committedEntry != null
    val shownEntry = if (holdIncoming) committedEntry else null
    val shownTitle = shownEntry?.title ?: state.title
    val shownArtist = shownEntry?.artist ?: state.artist
    val shownArtwork = shownEntry?.artworkUrl ?: state.artworkUrl
    val shownMediaId = shownEntry?.mediaId ?: state.mediaId
    // ⚠ `swipeCommitted` 必须一起归零（见上面 ①）——少这一条就是那一帧的闪回。
    // ★ 拖动中读**同步**的 [swipeDragOffset]；松手交接后读 `contentOffset`（动画值）。
    val visualOffset = if (holdIncoming || swipeCommitted) {
        0f
    } else if (swipeDragging) {
        swipeDragOffset
    } else {
        contentOffset.value
    }
    val dragDirection = when {
        visualOffset < 0f -> -1
        visualOffset > 0f -> 1
        else -> 0
    }
    // ★ 预览行曲目：松手后有快照 ⇒ 用快照（方向也锁快照）；拖拽中无快照 ⇒ 按当前方向现算。
    //   两条来源在 `currentIndex` 未变时结果相同，因此松手瞬间视觉连续；
    //   而松手之后即使 `currentIndex` 漂移，预览行也**不再跟着变**。
    val previewEntry = committedEntry ?: neighborOf(dragDirection)
    val previewDirection = if (committedEntry != null) committedDirection else dragDirection
    val dragProgress = (kotlin.math.abs(visualOffset) / swipeWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val adjacentAlpha = smoothStep(dragProgress, 0.15f, 0.85f)


    // 播放栏高度固定 48dp，与底栏收缩态的两侧按钮/搜索键完全等高。
    // 之前用 lerpDp(45,48) 且外层 Box 未定高，会随 compact 与 sharedBounds
    // 测量出更高/更矮的玻璃壳；这里统一钉死 48dp。
    val miniHeight = if (vertical) lerpDp(236.dp, 48.dp, compact) else 48.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(miniHeight)
            .onGloballyPositioned { coords ->
                val tl = coords.localToWindow(Offset.Zero)
                miniPlayerHitRect.value = Rect(
                    tl.x, tl.y,
                    tl.x + coords.size.width, tl.y + coords.size.height,
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(miniHeight)
                .then(sharedShellModifier)
                // ★ 2026-10-28：按压时**整条栏**（玻璃层 + 内容层，两层一起）放大
                //   —— kyant0 官方 Button 的按压模型：进度由手指决定（按住撑着、抬手收回），
                //   幅度 `MINI_BAR_PRESS_GROWTH`（峰值单侧 5.4dp，含官方弹簧的过冲约 6.2dp）。
                //   ⚠ 必须挂在 `sharedShellModifier` **之内**（更靠子级）：`graphicsLayer`
                //     不参与布局测量 ⇒ sharedBounds 的 morph 几何不受它影响；且 `scale()`
                //     内部按展开进度把 morph 期压回 1 ⇒ 转场全程不掺和。
                .graphicsLayer {
                    // ★★ 2026-10-28：缩放**只由两颗按钮的按压**驱动（见
                    //   `playPress` / `nextPress` 处注释）。横滑切歌全程
                    //   `pressProgress` 恒 0 ⇒ 整栏一次都不许变尺寸
                    //   （上一版用「一拖就收平」压制，结果每次滑歌都胀一下再瘪回去 =
                    //   用户报的「又大又小」）。
                    val s = miniBarPress
                        .scale(expansionProgressProvider())
                        .finiteOrZero()
                    scaleX = s
                    scaleY = s
                },
        ) {
        // ★★★ 2026-11-02（第十七轮，**退出转场**真因）：玻璃盒尺寸闸门。
        //
        //   上一轮的分层 bisect（一版包内含 shadow/highlight/lens/blur/surface 六种关闭组合）
        //   在**滚动稳态**下数据完全一致 ⇒ 证明那圈弧不在材质的任何一层里，方向一直错着。
        //   真正的现场是「从播放页退出」：逐帧实证见 `_exit/ex2.mp4` 帧 241/242 ——
        //   退出中段整页被一块**几乎全屏的圆角毛玻璃**盖住，四角各一个 96px 圆弧，
        //   随后跟着 sharedBounds 矩形收缩成迷你栏。那就是用户说的「四个角突出的弧」。
        //
        //   机制：玻璃盒是 `.fillMaxSize()`（跟随 sharedBounds 矩形，见下方注释），
        //   而材质门控只有 `p < 0.52`。退出时 p 从 1 往 0 掉，**刚跨过 0.52 的那一帧**
        //   矩形还有 ~885px 高（约 6 倍迷你栏），材质就在这一帧挂上 ⇒ 一次性画出
        //   全屏 blur(2dp) + lens(24dp) 的大圆角面。这一帧既是「弧」，也是退出时的顿挫
        //   （整屏 RenderEffect 每帧重算）。
        //   ⇒ 材质挂载必须同时要求「矩形已经收缩到迷你栏量级」。
        //   ⚠ 只在跨阈值时翻一次状态：`onSizeChanged` 每帧都会回调，逐帧 setState 会让
        //     本组件在转场期每帧重组（上千行），这里只在布尔翻转时写。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // ★★★ 2026-11-02（第十八轮）：**玻璃盒恢复成上游的固定迷你栏尺寸**。
                //
                //   历史（必读，别再改回去）：fork 在第十七轮把这里改成 `.fillMaxSize()`
                //   （跟随 sharedBounds 的 morph 矩形），理由是「固定 48dp 时玻璃只是外壳
                //   顶部一条细胶囊，外壳圆角却按几何律涨到屏角 ⇒ 两条弧在斜上方交叉」。
                //   但那个收益是假的、代价是真的：
                //     · 玻璃盒跟着矩形走 ⇒ 退出转场里它会变成一块**远大于迷你栏的圆角面板**
                //       （本层 alpha 在 p < 0.44 时恒 1，见 `miniSurfaceAlphaProvider`）；
                //     · 这块面板**四角各有一个圆弧**，跟着 sharedBounds 矩形一路收缩
                //       ⇒ 就是用户报的「四个角突出的弧在跳、在迷你栏外方向」。
                //     · 第十七轮只加了「高度 > 2×迷你栏就不挂材质」的门控 —— 那只是把
                //       全屏面板缩到 2 倍，四角弧照样在，用户判定「跟没修一样」。
                //   上游（`_up-baseline`，从不闪那版）的做法是：玻璃盒**恒为
                //   `.fillMaxWidth().height(miniHeight)`**，永远只有迷你栏那么大，
                //   转场里靠 `miniSurfaceAlpha` 淡出（窗口 0.02→0.48）让位给播放页本体。
                //   ⇒ 恢复上游几何：玻璃在物理上不可能变成「大面板」，四角弧失去载体。
                //   ⚠ 外壳（`sharedShellModifier`）仍然 morph，那才是转场期的几何载体。
                .height(miniHeight),
        ) {
            // Keep the glass surface out of the shared-bounds node itself.
            // When a capsule-shaped draw modifier is resized to the full
            // player, its fallback/lens can briefly become a dark giant pill.
            // The shared node remains a transparent geometry shell while the
            // actual MiniPlay glass fades before the full-screen scene owns the
            // surface.
            // ★ 2026-10-24：玻璃面可见性闸门（展开动画省 GPU）。
            //   `miniSurfaceAlphaProvider()` = 1 - smoothStep(p, 0.02, 0.48) ⇒ p ≥ 0.48 后恒 0。
            //   alpha = 0 时这层玻璃（`drawBackdrop` 的 vibrancy + blur(2dp) + lens(24dp)）
            //   已经完全不可见，但那个 RenderEffect 仍可能被逐帧执行（离屏纹理 + 高斯 + 透镜）。
            //   收成离散 State：不可见时整块玻璃 modifier 直接不挂 ⇒ 展开后约 52% 的帧
            //   一分玻璃钱都不付。判据只在 0.001 穿越时翻一次，不引入每帧重组
            //   （与 `miniChromeHidden` / `miniContentClipped` 同一套 derivedStateOf 套路）。
            val miniSurfaceVisible by remember(expansionProgressState) {
                // ★★ 2026-11-02：门控从「alpha > 0.001」改成「p < 0.52」，让材质**提前 4% 挂载**。
                //   原来材质在 p 跨过 0.48（alpha 恰为 0）那一帧才挂上 ⇒ 该帧要新建 RenderNode
                //   并跑第一遍离屏采样 + 高斯 + 透镜 ⇒ 单帧超预算，用户感受就是
                //   「在动态背景和玻璃之间切换会顿一下」。
                //   现在 p∈[0.48, 0.52] 材质已挂但 alpha 仍是 0 ⇒ 首帧成本发生在**完全不可见**
                //   的时候，可见段（p < 0.48）不再有建立尖峰。代价只是这 4% 区间的采样开销。
                //
                // ★★★ 2026-11-02（第十八轮）：尺寸条件**删除**。
                //   它是在「玻璃盒跟随 morph 矩形」这个错误前提下加的补丁（见上方玻璃盒注释）。
                //   玻璃盒现在恒为迷你栏尺寸，尺寸条件恒 true ⇒ 留着只会多一条无用判据。
                derivedStateOf { expansionProgressState.value < 0.52f }
            }
            // ★★ 2026-10-24 修「收起尾段，栏顶两个角的斜上方会多出一点点东西」。
            //
            //   根因（真机逐帧 + 像素差实证）：这一层玻璃用的是 `Capsule()`，圆角 =
            //   **当前层高度 / 2**；而收起时外壳内容层的裁剪圆角恒为
            //   `meloXPlayerShellCornerDp(p)`（p ≤ 0.50 时为定值 24dp）。morph 期
            //   `RemeasureToBounds` 会把本层重新测量到**比迷你栏更高的矩形**上
            //   ⇒ 玻璃的胶囊圆角 > 内容层的 24dp 圆角 ⇒ 内容层在两个**上角的斜对角**
            //   露在玻璃轮廓之外（像素差实测：栏顶两角各 4~9px、栏底一条 ≈6dp）。
            //   ⇒ morph 期让玻璃用**与内容层完全相同**的 24dp 圆角（按半高夹住）：
            //     · 栏静止时（48dp 高）24dp == 高/2 == 胶囊 ⇒ 与 `Capsule()` 逐像素等价；
            //     · morph 期两层的圆角严格相等 ⇒ 不再露边。
            //   ⚠ 形状不读任何逐帧动画值（判据是离散 State，0.001 穿越才翻一次），
            //     且 `RoundedCornerShape` 按值比较 ⇒ 不引入每帧重组 / 不重设 outline。
            //
            // ★★ 2026-10-31 修「收回刚结束那一下，迷你栏沿轮廓闪出一条 1~3px 背景色细线」。
            //   真机逐版定位结论（v9 圆角+lens 开 / v10b squircle+lens 关 **闪得一模一样**）：
            //   **与玻璃形状、lens 无关** —— 就是本层 Box 上那条多余的二次
            //   `Modifier.clip(miniGlassShape)` 造成的描边。上游（`_up-baseline`，从不闪那版）
            //   玻璃面只有 `meloXLiquidBottomBar(shape = …)` 的内部 shape，**不挂额外 clip**；
            //   而本移植版在 L765 多挂了一条 clip。crafts：收回最后一帧，fillMaxSize 的玻璃盒
            //   底边/斜对角圆角被这条 clip 再裁一遍，轮廓即描出那圈 1~3px 线（正合用户描述的
            //   「底边直线 + 斜对角圆角」）。
            //   ⇒ 方案（与上游逐字对齐）：
            //     · 玻璃材质 = `Capsule()`（lens 合法，液态折射完整保留，不崩）；
            //     · **删掉多余的 `.clip()`**；
            //     · `miniSurfaceVisible` 闸门改回——材质仍需在你不可见时整体不挂以省 GPU，
            //       但不再叠加第二次裁剪。见下方 Box 的注释分支。
            // ⚠⚠⚠ 2026-11-01 弧线真因（= 10-24 那条修复被人回退后复发）：
            //   morph 期 `RemeasureToBounds` 把本层重测到**当前 morph 矩形**
            //   （高 H 从屏高 2376px 收到迷你栏 192px，density 4），`Capsule()` 半径 = H/2 每帧在变；
            //   而外壳内容层圆角是 `meloXPlayerShellCornerDp(p)`。两者只在 H/2 == 外壳角那一帧相等
            //   ⇒ 整个收起过程中外壳都在**两上角的斜对角**探出玻璃轮廓（实测 p=0.48 差 436px、
            //     p=0.30 差 240px）—— 就是用户说的「左右两边斜上方两条弧线」。
            //   ⚠ 中途试过「两层都钉 24dp」：弧没了，但**下沉段出现闪烁弹跳**（矩形只剩 ~75dp 高时
            //     24dp 角留出竖直直边，22dp 下沉让直边整体滑动）。⇒ 常数两侧都不可行。
            // ★★ 2026-11-01 定案（第四版）：玻璃与外壳**全程走同一条几何律**
            //    `min(屏角, min(宽,高)/2)`（见 `meloXPlayerGlassShape`），
            //    **不看 `closingIntent`**。理由（用户报「肉眼看不到，但还是有一瞬在闪」）：
            //    手势拖拽收起期 `MeloXApp` 刻意把 `closingIntent` 写 false（内容层要跟手），
            //    松手 settle 那一帧才翻 true ⇒ 只要形状分方向，两层必在那一帧错形/跳变。
            //    同一条律后判据消失，开/合/拖拽/松手/打断全部连续。
            //    ⚠⚠ 别再写死 `Capsule()` 或 24dp —— 两者都表达不了「半径跟随矩形高」：
            //      · `Capsule()` = 高/2，morph 中段会远大于外壳的屏角 ⇒ 外壳在两上角斜对角探出；
            //      · 常数 24dp，下沉段矩形只剩 ~75dp 高时留出**竖直直边** ⇒ 下沉「闪烁弹跳」。
            //      只有「跟矩形高走的同一条律」能同时满足中段与下沉段。
            // ★★ 2026-11-02 第七次修正：玻璃半径从「屏角」改回**固定 24dp**。
            //   玻璃只在 p < 0.48 挂载/可见，而外壳在那一段（rise 窗口未启动）恒为 24dp
            //   ⇒ 两层必须同值 24dp，否则收尾阶段会出现「玻璃圆角 46dp / 外壳 24dp」，
            //   肉眼就是"迷你栏圆角突然变大"（用户 17:50 报障）。
            //   仍按半高夹住（见 `MeloXPlayerGlassShape.createOutline`）以抵消 h < 48dp 的直边。
            // ★★★ 2026-11-02（第十八轮）：**形状回到上游的 `Capsule()`，不再分 morph/稳态两支**。
            //   玻璃盒已恢复成固定迷你栏尺寸 ⇒ `Capsule()` 的半径恒 = 24dp（= 半高），
            //   正是稳态观感，也正是上游那一份逐字写法。
            //   `meloXPlayerGlassShape` / `MeloXPlayerGlassShape` 现在只在**外壳**用，
            //   玻璃侧不再引用 ⇒ 「玻璃形状 vs 外壳形状」这条错形来源整体消失。
            // ★★★ 2026-11-02（第十轮，真因）：**lens 半径必须随迷你栏高度收缩**。
            //
            //   `meloXLiquidBottomBar` 的 lens 半径原本写死 24dp（kyant0 官方面板值）。
            //   而本组件的 `miniHeight` 在竖屏有两档：展开态 236dp、收缩态 **48dp**
            //   （= 与底栏那两个圆球同一行的高度）。
            //   ⇒ 收缩态半高正好 24dp == lens 半径，圆角处被 5× 折射链放大弯折成一圈
            //     （同源故障在本仓库 `MeloXBackdropComponents` L645 段已有记录：
            //      把它照搬到 40dp 圆盘上就是「圆周被放大弯折成一圈」）。
            //     那一圈采的正是邻域里逐帧流动的动态背景 ⇒ 用户看到的
            //     「四个角都有动态背景颜色的弧线在闪」；折射环带同时白付一大截 AGSL，
            //     就是「非常卡顿」的主因。
            //   ⇒ 半径按**形状高度**夹取：`min(24dp, miniHeight × 0.40)`。
            //     · 竖屏 `vertical=false` ⇒ `miniHeight` 恒 48dp ⇒ **恒定 19.2dp**
            //       （< 半高 24dp，弯折条件彻底消除；竖屏全程不随任何动画值变化，
            //        不会让 `drawBackdrop` 重建 AGSL）。
            //     · 横屏大卡片 236dp ⇒ 仍是官方 24dp。
            //   ⚠⚠ 上一版这里按 `compact` 分档是**错的**，已废弃：
            //     竖屏 `vertical=false` ⇒ `miniHeight` 恒 48dp，而 `MeloXPositionedChrome`
            //     在 inline 路径传给本组件的 `compactProgress` 其实是**播放页展开进度**
            //     ⇒ 判据会在关闭动画跨 0.75 时让折射半径从 13.4 跳到 24dp，
            //     等于在转场中途改了一次材质，既没解决目标状态、还多一次重建。
            //   ★★★ 2026-11-02（第十二轮，真真因）：**19.2dp 还是太大，因为稳态形状是 `Capsule()`**。
            //
            //   `miniContentClipped`（= `expansionProgress <= 0.001`，即用户所说的「收缩态」）
            //   为 true 时 `miniGlassShape = Capsule()` —— 这一支是历史上为防 morph 期
            //   `RemeasureToBounds` 把行高拉到全屏、`Capsule` 半径暴涨成巨圆而留的。
            //   但稳态时它是活着的，且 **`Capsule` 的角半径恒 = size.height/2 = 24dp**：
            //   kyant lens 的 `getCornerRadii` 恰恰只读这个角半径，用它和 `lensRadius`
            //   一起搭折射场 ⇒ 只要 `lensRadius` 逼近 24dp，球面就顶到轮廓上，
            //   角部采样越过边界取到**玻璃外的像素**，那一圈正是「随背景逐帧变色的弧」。
            //   ⇒ **半径回到官方 24dp**（= 用户所说的「之前那种液态」）。
            //   ⚠ 15 轮里试过 19.2 / 10 / 16，全被否决（「液态效果又不是之前那种」）——
            //     折射半径直接决定液态的视觉强度，缩小它就是削弱液态，没有中间路线。
            //     所以这里**不再为了压闪去动半径**，压闪另找途径（见 `surfaceColor` 段说明）。
            //   ⚠ 保留这个 `val` 而不是内联字面量：它是唯一需要按高度分档的钩子
            //     （横屏 236dp 大卡片若将来要更大半径，改这里即可）。
            val miniLensRadius = 24.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // ★★★ 2026-11-02（第十八轮）：**恒挂 alpha 图层**（回到上游写法）。
                    //   原来这里有「p < 0.44 时不挂 graphicsLayer」的省合成优化，它等于把 alpha
                    //   强制成 1；配上当时的 (0.44,0.52) 窗口是自洽的，但窗口一回到上游的
                    //   (0.02,0.48)，那个优化就会把 p∈(0.02,0.44) 本该淡出的玻璃**画成不透明**。
                    //   ⇒ 去掉优化，永远按 provider 的 alpha 画。
                    .graphicsLayer { alpha = miniSurfaceAlphaProvider() }
                    .then(
                        if (miniSurfaceVisible) {
                            // 与底栏 nav 胶囊用同一套材质（meloXLiquidBottomBar + 同一
                            // tint/surfaceColor），保证深/浅色下观感一致。
                            // ⚠ `pressProgress` 显式传 0（2026-10-28）：它对材质的影响
                            //   （highlight / shadow / innerShadow 的 alpha）是在**组合期**
                            //   算的常量，喂一个逐帧变的值会让 `MeloXIOSMiniPlayer` 这个
                            //   上千行的 composable 在按压期间**每帧重组**；按压观感另由
                            //   外壳 `graphicsLayer` 缩放 + 两颗按钮各自的高光给到。
                            // ⚠ 不再挂 `.clip()` —— 形状完全交给 `meloXLiquidBottomBar`
                            //   的内部 shape（Capsule），否则会沿轮廓描出闪烁细线。
                            Modifier.meloXLiquidBottomBar(
                                shape = Capsule(),
                                tint = bottomLiquidGlassTint(),
                                // ★★★ 2026-11-02（第十六轮）：**浓度回退到全局值 0.52**。
                                //   十四轮 0.86、十五轮 0.62 都被用户否决：
                                //   「静态的液态效果直接丢失了」「液态效果又不是之前那种」。
                                //   ⇒ 不再给迷你栏做任何材质特殊化，观感 100% 等于
                                //     导航胶囊 / 搜索球（同一套 tint + 同一套浓度）。
                                surfaceColor = bottomGlassSurfaceColor(),
                                // ★★ 2026-11-02：显式背板 —— 转场期 overlay 副本与稳态节点
                                //   必须采同一个 Backdrop 对象，否则转场结束那一帧换源跳色。
                                //   见形参 `glassBackdrop` 的说明；⛔ 别再改回「淡播放页背景」。
                                backdropOverride = glassBackdrop,
                                pressProgress = 0f,
                                refractionHeight = 8.dp,
                                // ★★★ 2026-11-02（第十轮）：折射半径随高度收缩 ——
                                //   收缩态 48dp 高时若仍用 24dp（== 半高），四个圆角会被
                                //   放大弯折成一圈动态背景色弧并逐帧闪。见上方定义处说明。
                                // ★★★ 2026-11-02（第十六轮）：**blur 回退 2dp**。
                                //   十五轮抬到 4dp 的结果是「现在又卡」——blur 的开销随半径
                                //   超线性增长，而它对边缘折射带来的收益远小于代价。
                                //   ⇒ 不传，走形参默认 2dp（= 全局值，与底栏两颗球完全一致）。
                                lensRadius = miniLensRadius,
                                // ★★★ 2026-11-02（第十五轮）：**恢复 lens**（第十三轮曾整体关掉）。
                                //   用户明确：「静态的液态效果直接丢失了，不值得，继续修复」。
                                //   ⇒ 竖屏 48dp 迷你栏重新开折射，半径取 16dp（见 `miniLensRadius`）：
                                //     16 < 半高 24，球面不顶到轮廓，不弯折；同时比 10dp 的折射带宽，
                                //     液态观感才立得住。
                                //   ⚠ 第十三轮「关 lens 只把跳变 224→177」那条结论仍然成立 ——
                                //     它说明 lens 不是闪的主因，**不说明 lens 该关**。
                                lensEnabled = true,
                                // ★★★ 2026-11-03（用户需求）：**转场期液态强度连续降级**。
                                //   1f = 完整液态（只在落定后）；0f = 整条 lens 不挂。
                                //   ⚠ 必须是 provider：绘制期读 ⇒ 逐帧变化不重组本组件。
                                //   稳态下恒 1f ⇒ 静止观感与改动前**逐像素相同**。
                                liquidScale = miniLiquidScaleProvider,
                                // ⚠ 下面这几行是**横屏大卡片**（236dp，lensEnabled=true）适用的约束，
                                //   竖屏 48dp 已按上一段注释整体关掉 lens，不再走这条路径：
                                //   kyant 的 lens 只做一次类型分派 —— `LensKt.getCornerRadii` 里
                                //   `shape is CornerBasedShape → topStart/topEnd/… .toPx(...)`，
                                //   **只取四个角半径**喂给 shader，不看轮廓实现。
                                //   而 `meloXPlayerGlassShape()` 返回的是一个 `CornerBasedShape`
                                //   子类，它的 `createOutline` 直接委托 `MeloSmoothCornerShape`
                                //   ⇒ 轮廓 = 外壳同一次调用（逐点重合），半径照常喂给 lens。
                                //   ⛔ 别换成任何「同半径的另一种曲线」：正圆弧 → 月牙缝；
                                //     kyant `RoundedRectangle(Continuous)` → 与外壳交叉 → 弧线毛刺。
                                //   ⛔ 也别退回普通 `: Shape`（会被 `throwUnsupportedSDFException()`
                                //     在 draw 阶段崩掉）。
                            )
                        } else {
                            Modifier
                        },
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
                // ⚠ 2026-10-28（第三次改）：**按压手势不在这里**。原先挂在这一层
                //   （整条栏 = 官方 surface 语义），但迷你栏同时是横滑切歌手柄
                //   ⇒ 每滑一次歌都先胀后瘪（用户报「又大又小」）。现在手势下放到
                //   `MiniVectorButton`（见该 composable），滑歌全程 `pressProgress` 恒 0。
                // ⚠ 2026-10-28 撤掉 `meloXLiquidContentTransform(...)`：
                //   它按官方口径 `1 + 4dp/高度`（≈ +9.5%）放大**内容**，且叠 tanh 跟手位移。
                //   官方那颗按钮是「玻璃 + 内容一起胀」，我们这里玻璃由外壳 `graphicsLayer`
                //   统一放大 3.2% ⇒ 内容再单独胀 9.5% 会捅出胶囊、并和外壳缩放重复计数。
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            // ★ morph 期间取消 `.clip(Capsule())`：`Capsule` 半径 = 行高/2，而 morph
            //   期间 sharedBounds 用 `RemeasureToBounds`（L169）把整行从 48dp 重新测量
            //   成全屏高 ⇒ 半径同步暴涨（实测 90ms 已 ~125dp、末段 ~450dp），
            //   整行内容会被切成一个巨大的圆。**这个 bug 是真实的**，修的是它。
            //   ⚠ 注意：它与「封面圆角」无关——封面的 `sharedElement` 带
            //     `renderInOverlayDuringTransition = true`，morph 期间由
            //     `MeloXIOSNowPlayingSharedHost` 的副本渲染，**源侧 clip 不参与**。
            val miniContentShape: Shape? =
                if (!miniContentClipped) null else Capsule()

            if (vertical) {
                val artworkModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(sharedPlayerArtworkKey(shownMediaId)),
                            animatedVisibilityScope,
                            boundsTransform = sharedArtworkBoundsTransform,
                            renderInOverlayDuringTransition = true,
                            zIndexInOverlay = 99f,
                        )
                    }
                } else Modifier
                if (compact > .75f) {
                    Box(Modifier.fillMaxSize().clickable(
                        enabled = !isTransitioning || closingIntent,
                        interactionSource = null, indication = null, onClick = onExpand,
                    ), contentAlignment = Alignment.Center) {
                        Artwork(shownArtwork, Modifier.size(36.dp).then(artworkModifier)
                            .then(artworkClipModifier))
                    }
                } else {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                ) {
                    Column(
                        Modifier.fillMaxWidth().clickable(
                            enabled = !isTransitioning || closingIntent,
                            interactionSource = null, indication = null, onClick = onExpand,
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Artwork(shownArtwork, Modifier.size(40.dp).then(artworkModifier)
                            .graphicsLayer {
                                val sharedArtworkScale = sharedArtworkScaleProvider()
                                scaleX = sharedArtworkScale
                                scaleY = sharedArtworkScale
                            }
                            .then(artworkClipModifier))
                        Text(shownTitle.ifBlank { stringResource(R.string.player_now_playing) },
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            fontSize = titleFontSize, lineHeight = titleLineHeight,
                            modifier = Modifier.graphicsLayer { alpha = miniChromeAlphaProvider() })
                        Text(shownArtist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontSize = artistFontSize, lineHeight = artistLineHeight,
                            modifier = Modifier.graphicsLayer { alpha = miniChromeAlphaProvider() })
                    }
                    MiniVectorButton(
                        enabled = !isTransitioning,
                        // ★★ 视觉档位**与过渡彻底无关**（见 `MiniVectorButton.visualEnabled`）：
                        //   只由「这首歌在功能上可不可用」决定。过渡期的淡入淡出由
                        //   `visualAlpha`（`miniChromeAlphaProvider`）逐帧负责。
                        visualEnabled = true,
                        // ⚠ 起形变必须在 `togglePlayPause()`（= 换状态）**之前**：
                        //   状态一变上层就重组，协程最多"被打断"，不会"根本没开始"。
                        onClick = {
                            miniPlayPauseMorph.startMorphToggle()
                            state.togglePlayPause()
                        },
                        size = 40.dp, visualAlpha = miniChromeAlphaProvider,
                        press = playPress,
                    ) { color ->
                        MeloXPlayPauseMorphGlyph(
                            morph = miniPlayPauseMorph,
                            color = color,
                            iconSize = controlIconSize,
                            playCompensation = MINI_PLAY_GLYPH_COMPENSATION,
                            pauseCompensation = MINI_PAUSE_GLYPH_COMPENSATION,
                        )
                    }
                    MiniVectorButton(
                        enabled = !isTransitioning && (state.hasNext || state.repeatMode != 0),
                        // ★ 同 play/pause：过渡态只管可点性，视觉档位只看「有没有下一首」。
                        visualEnabled = state.hasNext || state.repeatMode != 0,
                        onClick = {
                            miniNextPulse.trigger()
                            state.next()
                        },
                        size = 40.dp, visualAlpha = miniNextAlphaProvider,
                        press = nextPress,
                    ) { color ->
                        MeloXChevronConveyorGlyph(
                            forward = true,
                            color = color,
                            textSizeSp = with(density) { controlIconSize.toSp() },
                            scrollProvider = { miniNextPulse.scroll.value },
                            fallbackSymbol = MeloXSymbol.Next,
                            fallbackSize = controlIconSize,
                        )
                    }
                }
                }
            } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(miniHeight)
                    .then(miniContentShape?.let { Modifier.clip(it) } ?: Modifier)
                    .padding(
                        horizontal = lerpDp(12.dp, 8.dp, compact),
                        // Expanded content was exactly 30dp (title 15 + artist 15)
                        // with no slack, so the last line's descenders were clipped.
                        // Keep 4dp instead of 6dp to leave room for both lines.
                        vertical = lerpDp(4.dp, 3.dp, compact),
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
                                    // ★★ 2026-10-28：跟手起点**同步**归零，不再 `launch { snapTo(0f) }`。
                                    //   旧写法是异步的 ⇒ 可能晚于第一个 move 执行，把已累计位移抹掉；
                                    //   而切歌后 `contentOffset` 正停在 ±swipeWidth，抹不掉时就会在
                                    //   屏边做加法 ⇒「手指往左、歌曲往右」。见 [swipeDragOffset] 处注释。
                                    swipeDragOffset = 0f
                                    swipeDragging = true
                                    swipeDragEpoch++
                                    // ⚠ 清 `pendingDirection` 同时会立刻释放上面的 `holdIncoming` /
                                    //   `swipeCommitted` 锁定位（否则新拖拽期间 `visualOffset` 被钉死 0）。
                                    pendingDirection = 0
                                    pendingOutgoingMediaId = null
                                    committedEntry = null
                                    committedDirection = 0
                                    // 上一次的出场 / 回弹动画若还在跑，先停掉（否则它会在拖动期间
                                    // 继续改 `contentOffset`；拖动中视觉读的是 `swipeDragOffset`，
                                    // 所以这里只是防止交接时抢值）。
                                    scope.launch { contentOffset.stop() }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    accumulatedDrag += dragAmount
                                    // ★ 同步赋值、**绝对值**（旧写法是 `contentOffset.value + dragAmount`
                                    //   再跨协程写回 ⇒ 增量 + 异步，必然乱序）。
                                    swipeDragOffset = (swipeDragOffset + dragAmount)
                                        .coerceIn(-swipeWidth * .86f, swipeWidth * .86f)
                                },
                                onDragEnd = {
                                    // ★ 同步快照：交接协程里再读就是"下一拍"的值了。
                                    val releasedOffset = swipeDragOffset
                                    val epoch = swipeDragEpoch
                                    val direction = when {
                                        accumulatedDrag <= -28f -> -1
                                        accumulatedDrag >= 28f -> 1
                                        else -> 0
                                    }
                                    // ★★ 2026-10-06（第二轮）「真的会换曲」预判 ─────────────────
                                    //   单曲队列、或命中「上一首 = 已播 ≥5s 时重播当前曲」
                                    //   （`previousRestartsAfterFiveSeconds`，**默认开**）这两条路径
                                    //   **曲目根本不变**。若照旧播切歌动画，`holdIncoming` 会一直顶着
                                    //   「上一首」不放（因为判据是"mediaId 变了"，而它永远不会变），
                                    //   直到 1.5s 超时才滑回 ⇒ 用户看到的就是
                                    //   「横滑后抽搐闪回上一首」。
                                    //   ⇒ 不换曲时**不做切歌视觉**：只弹回原位，命令照发（重播/回开头）。
                                    if (direction != 0 && state.miniSwipeChangesTrack(direction)) {
                                        // 快照本次目标（实时读 state 的 queue/currentIndex）⇒
                                        // hold 期间不再现算，避免 currentIndex 漂移取到错歌。
                                        committedEntry = neighborOf(direction)
                                        committedDirection = direction
                                        scope.launch {
                                            // ★ 交接（2026-10-28）：把动画值摆到手指松开的位置，
                                            //   再撤 `swipeDragging` —— 两者相等 ⇒ 视觉无缝。
                                            //   拍号变了说明已开始新拖动，本拍直接放弃交接。
                                            if (swipeDragEpoch == epoch) {
                                                contentOffset.snapTo(releasedOffset)
                                                swipeDragging = false
                                            }
                                            contentOffset.animateTo(direction * swipeWidth, spring(dampingRatio = .68f, stiffness = 360f))
                                            // ⚠ 这里**不再**写 `pendingIncoming`（已由 `committedEntry`
                                            //   取代）：该值在 `onDragEnd` 时已快照，本闭包活在
                                            //   `pointerInput(state.mediaId)` 块里，早期版本的
                                            //   `adjacentEntry` 是旧组合快照。
                                            pendingDirection = direction
                                            pendingOutgoingMediaId = state.mediaId
                                            if (direction < 0) state.nextFromMiniPlayer() else state.previousFromMiniPlayer()
                                        }
                                    } else {
                                        // 不换曲（含 `direction == 0` 未过阈值）⇒ 只弹回原位、不播切歌视觉。
                                        // ⚠⚠ 2026-10-06 修上一轮引入的小回归：脚本原意是「命令两个方向都发」，
                                        //   我只写了 `direction > 0` ⇒ **单曲队列时左滑（下一首）完全无响应**
                                        //   （`miniSwipeChangesTrack` 判 false，而 `nextFromMiniPlayer()`
                                        //   里的 `playQueueIndex(0)` 才是用户期望的"重播当前曲"语义）。
                                        //   `direction == 0`（没过 28px 阈值）不发命令，保持原样。
                                        when {
                                            direction < 0 -> state.nextFromMiniPlayer()
                                            direction > 0 -> state.previousFromMiniPlayer()
                                        }
                                        scope.launch {
                                            if (swipeDragEpoch == epoch) {
                                                contentOffset.snapTo(releasedOffset)
                                                swipeDragging = false
                                            }
                                            contentOffset.animateTo(0f, spring(dampingRatio = .72f, stiffness = 430f))
                                        }
                                    }
                                    accumulatedDrag = 0f
                                },
                                onDragCancel = {
                                    val releasedOffset = swipeDragOffset
                                    val epoch = swipeDragEpoch
                                    accumulatedDrag = 0f
                                    scope.launch {
                                        if (swipeDragEpoch == epoch) {
                                            contentOffset.snapTo(releasedOffset)
                                            swipeDragging = false
                                        }
                                        contentOffset.animateTo(0f, spring(dampingRatio = .72f, stiffness = 430f))
                                    }
                                },
                            )
                        }
                        // ⚠ 过渡进行中（收回/展开）禁用整条点击 —— 修「返回过程中还能点到
                        //   播放器按钮」。稳态（`isTransitioning=false`）行为不变。
                        // ★ 2026-10-15 转场可打断：**关闭方向不再禁用** —— `closingIntent` 为真时
                        //   即使 `miniBounceClock` 还没到 0.98（关闭尾段 / 刚关闭那一小段）也允许
                        //   点按抢回播放页（用户报障「完全关闭前不能再次点击打开」）。
                        //   打开方向仍禁用（避免展开途中重复触发 open）；稳态恒可点。
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            enabled = !isTransitioning || closingIntent,
                            onClick = onExpand,
                        ),
                ) {
                Row(
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        translationX = visualOffset
                        // ⚠ 2026-10-28：这里**不再**叠按压缩放 —— 外壳那一层
                        //   （`sharedShellModifier` 内侧的 `graphicsLayer`）已经对
                        //   「玻璃 + 内容」整体缩放了；两处都写就是 scale²
                        //   （1.032² ≈ 1.065 ⇒ 单侧 11dp，直接顶到导航胶囊）。
                    },
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
                                boundsTransform = sharedArtworkBoundsTransform,
                                renderInOverlayDuringTransition = true,
                                // ⚠ 与上面 shell 同理（2026-10-06 下沉加固）：源端封面必须排在
                                //   目标端封面（host 侧同为 overlay 层，`100f`）**之下**，
                                //   否则回弹时它会盖着播放页从全屏封面缩成迷你封面。
                                zIndexInOverlay = 99f,
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
                            val sharedArtworkScale = sharedArtworkScaleProvider()
                            scaleX = sharedArtworkScale
                            scaleY = sharedArtworkScale
                        }
                        .then(artworkClipModifier),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .wrapContentHeight(Alignment.CenterVertically)
                        .graphicsLayer { alpha = miniChromeAlphaProvider() },
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = shownTitle.ifBlank { stringResource(R.string.player_now_playing) },
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
                    Text(
                        text = shownArtist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = artistFontSize,
                        lineHeight = artistLineHeight,
                        softWrap = false,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                        modifier = Modifier.height(artistHeight),
                        style = LocalTextStyle.current.merge(
                            TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                        ),
                    )
                }
                }
                // ★ 预览行：`previewEntry` 松手后是**快照**（见其声明处）。
                //   `!holdIncoming` 时主行已顶着目标曲（0 位），此时预览行必然在
                //   ±整屏宽处、`adjacentAlpha = 0` ⇒ 直接不挂载，省一层（含封面图）合成。
                if (!holdIncoming) previewEntry?.let { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = adjacentAlpha
                                translationX = visualOffset - previewDirection * swipeWidth
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(lerpDp(10.dp, 8.dp, compact)),
                    ) {
                        Artwork(
                            entry.artworkUrl,
                            Modifier
                                .requiredSize(artworkSize)
                                .then(artworkClipModifier),
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
                                modifier = Modifier.height(artistHeight),
                                style = noFontPadding,
                            )
                        }
                    }
                }
            }

            MiniDancingBars(
                active = miniChromeVisible,
                isPlaying = state.isPlaying,
                sample = { reactiveSample },
                color = { miniBarsOnSurface.copy(alpha = miniChromeAlphaProvider() * 0.72f) },
                modifier = Modifier
                    .width(15.dp)
                    .height(18.dp)
                    .graphicsLayer { alpha = miniChromeAlphaProvider() },
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
                    // ⚠ 过渡进行中禁用（修「返回过程中还能点到播放器按钮」）。
                    enabled = !isTransitioning,
                    // ★ 视觉档位与过渡彻底无关（同 horizontal 分支）。
                    visualEnabled = true,
                    onClick = {
                        // ⚠ 起形变必须在 `togglePlayPause()`（= 换状态）**之前**：
                        //   状态一变上层就重组，协程最多"被打断"，不会"根本没开始"。
                        miniPlayPauseMorph.startMorphToggle()
                        state.togglePlayPause()
                    },
                    size = controlSize,
                    modifier = Modifier
                        .offset(x = MiniControlStartPull)
                        .zIndex(10f),
                    visualAlpha = miniChromeAlphaProvider,
                    press = playPress,
                ) { color ->
                    MeloXPlayPauseMorphGlyph(
                        morph = miniPlayPauseMorph,
                        color = color,
                        iconSize = controlIconSize,
                        playCompensation = MINI_PLAY_GLYPH_COMPENSATION,
                        pauseCompensation = MINI_PAUSE_GLYPH_COMPENSATION,
                    )
                }
                if (compactNextAlpha > 0.05f) {
                    MiniVectorButton(
                        enabled = !isTransitioning && (state.hasNext || state.repeatMode != 0),
                        // ★ 同 play/pause：过渡态只管可点性，视觉档位只看「有没有下一首」。
                        visualEnabled = state.hasNext || state.repeatMode != 0,
                        onClick = {
                            miniNextPulse.trigger()
                            state.next()
                        },
                        size = controlSize,
                        modifier = Modifier.zIndex(9f),
                        visualAlpha = miniNextAlphaProvider,
                        press = nextPress,
                    ) { color ->
                        MeloXChevronConveyorGlyph(
                            forward = true,
                            color = color,
                            // 与原来静态 `MeloXSymbolIcon(Next, iconSize = controlIconSize.toSp())`
                            // **逐字同字号** ⇒ 静止态（sv=0）与原渲染逐像素对齐。
                            textSizeSp = with(density) { controlIconSize.toSp() },
                            // ★ 绘制期读（组合期读会每帧重组整颗按钮）
                            scrollProvider = { miniNextPulse.scroll.value },
                            fallbackSymbol = MeloXSymbol.Next,
                            fallbackSize = controlIconSize,
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

/**
 * 四条音浪柱：随 [MeloXAudioReactiveSample] 的能量/节拍起舞（仅展开态挂载）。
 *
 * ⚠⚠ 卡顿修复（2026-10-06，用户报障「源代码的四条音乐波浪很卡、帧率很低」）——
 *   旧实现两层问题叠加：
 *   ① 采样循环 `delay(50)` = **20Hz 离散跳格**：120Hz 屏上每 50ms 才动一下，
 *      帧间完全静止 ⇒ 观感就是「帧率很低」；
 *   ② `reactiveSample` 在组合层读值、以值参数下传 ⇒ 每 50ms 把整个迷你栏
 *      （标题/按钮）重组一遍（铁律：逐帧状态只能在叶子消费点读）。
 *
 * 新架构：
 *   · 采样值经 [sample] lambda 进来，只在 `withFrameNanos` 帧循环里读——**不进组合订阅**；
 *   · 每帧把四条柱高向目标做**帧率无关的指数趋近**（起 28/s、落 12/s：起拍快、
 *     回落稍拖出「余韵」，音乐感更好），任意刷新率下都连续运动；
 *   · 柱高写进普通 FloatArray，再 bump 一个 IntState——该状态**只被 Canvas draw 块
 *     读取** ⇒ 写入仅触发 draw invalidation（重绘），零重组；
 *   · [active] = false（展开进播放页）直接空转帧循环，不抢共享元素转场的帧预算
 *     （与上方 20Hz 采样循环在 expansion 时的停更策略同源）。
 */
@Composable
private fun MiniDancingBars(
    active: Boolean,
    isPlaying: Boolean,
    sample: () -> MeloXAudioReactiveSample,
    // ⚠ 2026-10-22：`color` 改成 `() -> Color` —— 它的 alpha 带着播放页展开进度
    //   （`chromeAlpha × 0.72`），值传参会让**调用方**（迷你栏主作用域）每帧重组。
    //   `Canvas` 的 draw lambda 里读 provider ⇒ 只重绘，零重组。
    color: () -> Color,
    modifier: Modifier = Modifier,
) {
    // 初始直接落在静止高度：暂停态本就该是这个值，省掉一次入场跳动。
    val smooth = remember { FloatArray(4) { MINI_BARS_REST_HEIGHT } }
    val redrawTick = remember { mutableIntStateOf(0) }
    val activeState = rememberUpdatedState(active)
    val playingState = rememberUpdatedState(isPlaying)
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            // ⚠⚠⚠ 低功耗铁律（2026-10-16 真机取证：前台静置 4s 出 609 帧 ≈ 152fps）——
            //   `withFrameNanos` 的语义就是「**我要每一帧**」。只要这层循环还在转，
            //   App 就会按面板最高档持续出帧，平台 ARR **再怎么调度也降不下来**：
            //   它看到的明明是「每帧都有新内容」的应用。
            //   旧实现在「暂停 / 不可见」时**照样每帧申请**，只是把 energy 调小
            //   ⇒ 表面静止、实则满负荷，这是之前「静止也 120」的根因。
            //   ⇒ 正确做法：不播放 / 不可见时先做一段有预算的「余韵」回落，
            //     然后**彻底停帧**（suspend 等待），一帧都不再申请。
            if (!activeState.value || !playingState.value) {
                lastNanos = 0L
                var settleLast = 0L
                var settleStart = 0L
                while (!activeState.value || !playingState.value) {
                    val now = withFrameNanos { it }
                    if (settleStart == 0L) settleStart = now
                    val dt = if (settleLast == 0L) {
                        1f / 60f
                    } else {
                        ((now - settleLast) / 1_000_000_000f).coerceIn(1f / 240f, 0.1f)
                    }
                    settleLast = now
                    // 与活动帧同一条回落曲线（12/s）：起拍全套、落幕不断档，观感连续。
                    val fall = 1f - exp(-dt * 12f)
                    var settled = true
                    for (i in smooth.indices) {
                        smooth[i] += (MINI_BARS_REST_HEIGHT - smooth[i]) * fall
                        if (abs(smooth[i] - MINI_BARS_REST_HEIGHT) > 0.004f) settled = false
                    }
                    redrawTick.intValue++
                    if (settled || now - settleStart > MINI_BARS_SETTLE_BUDGET_NANOS) break
                }
                // 收尾完成 ⇒ **零帧申请**地挂起：等「可见且播放中」才回来。
                // 期间 gfxinfo 的 Total frames 停止增长，系统才有空间把帧率交还调度。
                snapshotFlow { activeState.value && playingState.value }
                    .filter { it }
                    .first()
                continue
            }
            withFrameNanos { now ->
                if (!activeState.value) {
                    lastNanos = 0L
                    return@withFrameNanos
                }
                // ★★ 2026-10-26（功耗/性能，真机取证见 [MINI_BARS_MAX_FPS]）：**出帧限频**。
                //   ⚠ 必须在读 `lastNanos` 之前返回：跳过的帧不更新 `lastNanos`，
                //     下一次真正出帧时 `dt` 才是「距上一张已绘帧」的真实间隔
                //     ⇒ 指数趋近的物理不变，只是采样变稀（⛔ 别把这一行挪到下面，
                //     那样每帧都推进 lastNanos、dt 退化成 8.3ms，柱高会整体变钝）。
                if (lastNanos != 0L && now - lastNanos < MINI_BARS_MIN_FRAME_NANOS) {
                    return@withFrameNanos
                }
                val dt = if (lastNanos == 0L) 1f / 60f
                else ((now - lastNanos) / 1_000_000_000f).coerceIn(1f / 240f, 0.1f)
                lastNanos = now
                val s = sample()
                val playing = playingState.value
                val energy = if (playing) s.energy.coerceIn(0.08f, 1f) else 0.10f
                val beat = if (playing) s.beat else 0f
                val downbeat = if (playing) s.downbeat else 0f
                // 目标柱高（与旧版同一组配方），起/落速率分帧率无关的指数系数。
                val rise = 1f - exp(-dt * 28f)
                val fall = 1f - exp(-dt * 12f)
                val targets = floatArrayOf(
                    energy * (0.58f + downbeat * 0.30f),
                    energy * (0.86f + beat * 0.22f),
                    energy * (0.46f + downbeat * 0.42f),
                    energy * (0.72f + beat * 0.28f),
                )
                for (i in 0..3) {
                    val t = targets[i].coerceIn(0.12f, 1f)
                    val rate = if (t > smooth[i]) rise else fall
                    smooth[i] += (t - smooth[i]) * rate
                }
                redrawTick.intValue++ // bump ⇒ 仅重绘（状态只被下方 draw 块订阅）
            }
        }
    }
    Canvas(modifier) {
        @Suppress("UNUSED_EXPRESSION")
        redrawTick.intValue // draw 层订阅点：此处读状态，写入只走重绘、不走重组
        // 绘制期取色（alpha 随展开进度变化）
        val barColor = color()
        val gap = size.width * .12f
        val barWidth = (size.width - gap * 3f) / 4f
        smooth.forEachIndexed { index, heightFraction ->
            val barHeight = size.height * heightFraction.coerceIn(.12f, 1f)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(index * (barWidth + gap), (size.height - barHeight) / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )
        }
    }
}

/**
 * 迷你栏 play/pause 字形的**视觉重量校准**（2026-10-30 定稿，经三轮真机反馈收敛）。
 *
 * ### 依据（字体轮廓 + 真机截图双实测）
 * `res/font/sf_pro_subset.ttf`（Medium / Fill）的墨迹：
 * ```
 * play.fill   0.7290 x 0.8203 em
 * pause.fill  0.5957 x 0.8076 em     ← 竖条本身更窄、更矮
 * ```
 * 未补偿时真机截图（iconSize 18dp / density 4）：
 * ```
 * play   墨迹 267 x 300 px   墨迹像素 46,796
 * pause  墨迹 170 x 227 px   墨迹像素 29,806   ← 只有 play 的 64%
 * ```
 * ⇒ 两个字形**都需要放大**才能与相邻的传输键（16dp 的 next/prev）视觉等重：
 * 上游原版两者都直接用 `iconSize`，在整个底栏里显得偏小。
 *
 * ### 取值（经六轮真机反馈收敛）
 * | 轮次 | play | pause | 用户反馈 |
 * |---|---|---|---|
 * | 1 | 1.15 | 1.00 | 「play 比 pause 小一圈」 |
 * | 2 | 1.27 | 1.00 | 「反了，是竖条比较小」 |
 * | 3 | 1.00 | 1.27 | 「竖条高度合适，但三角又偏小了」 |
 * | 4 | 1.30 | 1.27 | 「保持这个比例，都再缩小一点」 |
 * | 5 | 1.20 | 1.17 | 「缩小后的那两个还要调小」 |
 * | **6（定稿）** | **1.12** | **1.09** | 比例 1.0275 ≈ 1.0256（差 0.18%），再缩 6.7% / 6.8% |
 *
 * 计算依据：pause 高度 = `18 × 0.8076 × 1.09 = 15.85dp`；
 * play 高度 = `18 × 0.8203 × 1.12 = 16.54dp`（+4.4%，与历轮同比例）。
 * 三角填充率低于竖条，故 play 略高才能在**面积**上等重。
 *
 * ⚠ 两个旋钮独立，别只改一个 —— 单改任一方向都会立刻破坏平衡（前三轮都栽在这）。
 * ⚠ **改大小请等比缩放两者**，保持 `play / pause ≈ 1.026` 这个比例不变。
 * ⚠ 播放页那套传输键（`MeloXNowPlayingCoreControls`）走默认 1f，不受此处影响。
 */
private const val MINI_PLAY_GLYPH_COMPENSATION = 1.12f
private const val MINI_PAUSE_GLYPH_COMPENSATION = 1.09f

/**
 * 迷你栏传输键的公共外壳（alpha / 圆形热区 / 可点门控），字形由 [glyph] 槽位提供。
 *
 * ⚠ 为什么要拆成"外壳 + 槽位"：播放/暂停走**形变**、下一首走**传送带**（见
 *   `MeloXTransportMotion`），两者共用同一个外壳（同一套 alpha 与热区口径），
 *   差别只在字形 ⇒ 外壳只留一份，避免两份各自漂移。
 */
@Composable
private fun MiniVectorButton(
    enabled: Boolean,
    onClick: () -> Unit,
    size: Dp,
    /**
     * 这颗按钮自己的按压高光（官方 `PublicInteractiveHighlight`）。
     *
     * ⚠ 2026-10-28（第三次改，用户报「左右滑动歌曲，迷你栏会又大又小」）：
     *   按压手势与高光从**整条栏**下放到**按钮自己**。
     *   原因：迷你栏同时是横滑切歌手柄 ⇒ 手势挂在整条栏时，每次滑歌都会
     *   「按下先胀（弹簧爬升）→ 手指一动又被跟手进度压回去」，
     *   连滑几下就是又大又小。按钮是**离散**目标、不可能被横滑，天生没有这个矛盾。
     *   而整栏放大仍由 `MeloXMiniBarPress` 读这颗按钮的 `pressProgress` 驱动 ⇒
     *   观感还是「一按，整条栏轻轻胀」。
     *
     * ⚠ 手势**只读不消费**（`PointerEventPass.Initial` + `requireUnconsumed = false`
     *   + 自带 `drag()` 容忍已消费位移）⇒ 不抢下面 `clickable` 的点击。
     */
    press: MeloXLiquidInteraction,
    modifier: Modifier = Modifier,
    // ⚠ 2026-10-22：带上播放页展开进度的 alpha 一律走 `() -> Float`。
    //   它是每帧量，值传参会让**调用方**（迷你栏主作用域）每帧重组 —— 而迷你栏常驻首屏。
    visualAlpha: () -> Float = { 1f },
    /**
     * **仅用于视觉档位**（`baseAlpha` 的 0.94 / 0.26 两档）。
     *
     * ⛔⛔ **只能由「功能上是否可用」决定，绝不能掺任何过渡判据**
     *   （`isTransitioning` / `closingIntent` / `miniBounceClock` …）。
     *
     * 踩点（2026-11-01，两轮）：
     *   ① 第一轮把视觉档位绑在 `enabled = !isTransitioning` 上 ——
     *      `isTransitioning = miniBounceClock < 0.98f`，而 `miniBounceClock` 是**线性**
     *      tween（关闭方向 0→1 线性爬）⇒ 动画走到 **98%** 那一帧 `baseAlpha`
     *      **一帧内从 0.26 跳到 0.94**（亮度 3.6×）。98% 正落在下沉窗口
     *      `f ∈ (0.62, 1)` 内 ⇒ 用户看到「下沉的时候图标闪一下」。
     *   ② 第二轮改成 `|| closingIntent` —— **仍然闪**。因为手势拖拽收起期
     *      `MeloXApp` 刻意把 `closingIntent` 写 false（内容层要跟手），松手 settle
     *      那一帧才翻真；而**点击 / 返回键**关闭路径上它也可能整段为 false。
     *      ⇒ 只要档位还挂着任何一个「离散过渡判据」，那条判据翻真的那一帧就是一次跳变。
     *   结论：**过渡期的淡入淡出本来就该由 [visualAlpha] 逐帧负责**
     *      （`miniChromeAlphaProvider` = `1 - smoothStep(p, 0.10, 0.58)`，连续），
     *      档位再叠一层二值跳变纯属重复且必然踩在某一帧上。
     *      ⇒ 现在播放键恒 `true`、下一首只跟 `hasNext/repeatMode`（真禁用态 0.26），
     *      与「有没有下一首」这种**稳态事实**同源，转场全程不再有任何跳变。
     *   ⚠ 可点性仍必须由 [enabled]（含 `isTransitioning`）单独管：防过渡误触。
     */
    visualEnabled: Boolean = enabled,
    /** 字形槽位；拿到的是本按钮算好的前景色（含 enabled 的 alpha 档位）。 */
    glyph: @Composable (color: Color) -> Unit,
) {
    val baseAlpha = if (visualEnabled) 0.94f else 0.26f
    // 可点判据（原 `drawAlpha > 0.05f`）收成离散 State：只在阈值穿越时翻转。
    val alphaVisible by remember(visualAlpha) {
        derivedStateOf { visualAlpha().coerceIn(0f, 1f) > 0.05f }
    }
    val interactable = enabled && alphaVisible
    val reducedMotion = MeloXSettingsRuntime.reduceMotion
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = baseAlpha)
    Box(
        modifier = modifier
            // ★ 标准 kyant0 按压高光（用户 2026-10-28：「按钮就正常高光，不拓展了」）：
            //   高光挂在 `clip(CircleShape)` **之后**（= 内层），被圆钮形状裁住 ⇒
            //   光斑只在按钮内、跟随手指移动；不做任何溢出 / 椭圆。
            //   低版本（无 AGSL）走上游 fallback：光斑折进整块矩形（= 官方原版行为）。
            .size(size)
            .clip(CircleShape)
            .then(if (reducedMotion) Modifier else press.highlight.modifier)
            .graphicsLayer { alpha = visualAlpha().coerceIn(0f, 1f) }
            // ⚠ 手势同样跟着 `interactable` 走：按钮被禁用 / 已淡出（切歌过渡、收缩态
            //   next 淡出）时不该还能把整条栏按鼓起来。
            .then(
                if (reducedMotion || !interactable) Modifier
                else press.highlight.gestureModifier
            )
            .clickable(
                enabled = interactable,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        glyph(color)
    }
}

// ── 播放栏右侧控件尺寸（2026-09-25 收敛为「随内容区高度」）──────────────────
// 播放栏玻璃壳恒 48dp，但内边距随 compact 变化 ⇒ **内容区高度不同**：
//   · 展开态：48 − 3·2(外) − 6·2(内) = **30dp**
//   · 收缩态：48 − 3·2(外) − 3·2(内) = **36dp**   ⇒ 收缩态内容区**更高**
// 因此控件不能写死一个尺寸，必须跟内容区一起变（用户：收缩态要更大一点）。
// 展开 30dp 圆 / 18dp 图标；收缩 36dp 圆 / 19dp 图标。
// ⚠ 2026-10-30：收缩态图标由 **22dp → 19dp**（用户：「缩小后的迷你栏的 pause 和 play 还是大」）。
//   22dp 时收缩态字形高度 play 20.21dp / pause 19.37dp，比展开态大 **22%** ——
//   栏本身变小了、图标却更大，观感就是「一收缩图标反而顶出来」。
//   19dp 后只比展开态大 5.6%，保留「收缩态略大」的原意，又不会显得突兀。
//   ⚠ 改这里会同时影响 play/pause 与 next/prev（它们共用 controlIconSize）。
private val MiniControlSizeExpanded = 30.dp
private val MiniControlSizeCollapsed = 36.dp
private val MiniControlIconSizeExpanded = 18.dp
private val MiniControlIconSizeCollapsed = 19.dp
// 两圆之间的圆缘 gap（舞台已改为内容驱动宽度）。
//   图标间距 = gap + 2×圆内边距((size−icon)/2)。展开 (30−18)/2 = 6 ⇒ gap 0dp → 图标间距 12dp。
//   收缩 (36−19)/2 = 8.5 ⇒ gap 0dp → 图标间距 17dp（收缩态 next 淡出，此值仅过渡期可见）。
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
private const val MiniTitleFontDp = 13.5f
private const val MiniArtistFontDp = 12f
private const val MiniLineHeightDp = 15f

// 关闭尾段的「下沉 → 回弹」**不再**在此实现：它已移入共享元素 **bounds 几何**
//   （`meloXPlayerSharedBoundsTransform` / `meloXPlayerSinkFraction`，见
//    `MeloXPlayerTransitionSpec`）。原因：morph 期间外壳与封面由两个**独立 overlay**
//   渲染，加在节点上的任何位移都读不出可靠的下沉，只有 bounds 几何是 overlay 唯一
//   服从的输入，且外壳/封面/文字/控件天然同步。

private fun smoothStep(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun lerpDp(start: Dp, end: Dp, progress: Float): Dp =
    (start.value + (end.value - start.value) * progress.coerceIn(0f, 1f)).dp

/**
 * [MiniDancingBars] 的静止柱高：与原实现暂停态（`energy = 0.10` 落到 epsilon
 * 之前被 `coerceIn(0.12f, 1f)` 夹住）收敛后的结果一致 ⇒ 停帧前后外观不变。
 */
private const val MINI_BARS_REST_HEIGHT = 0.12f

/**
 * 「余韵」收尾预算。暂停后最多再申请这么多时间的帧做指数回落，到点立即停帧
 * （就算是 stable 到位也一样）——见 [MiniDancingBars] 里的低功耗铁律注释。
 */
private const val MINI_BARS_SETTLE_BUDGET_MS = 700L

private const val MINI_BARS_SETTLE_BUDGET_NANOS = MINI_BARS_SETTLE_BUDGET_MS * 1_000_000L

/**
 * ★★ 2026-10-26：**这四根柱子允许的最高出帧率**（60fps ⇒ 16.6ms 一帧）。
 *
 * ### 为什么要限频（真机实测，不是估算）
 *
 * 这四根柱子是 15×18dp —— 屏上不到 0.05% 的面积。但它是「每帧 bump 一次
 * `redrawTick`」的实现，而写这个 State 会让**整个 Compose 窗口**重放一遍绘制列表。
 * 于是「四根 8px 宽的柱子动一下」= 1440×3168 全屏重画一次，一秒 120 次。
 *
 * `dumpsys gfxinfo`（iQOO 15 / 120Hz / 播放中 / 页面完全静止不做任何操作）：
 *
 * | 场景 | frames/4s | fps | p50 |
 * |---|---|---|---|
 * | 首页 | 529 | **123** | 6ms |
 * | 发现 | 528 | 122.8 | 9ms |
 * | 音乐库 | 528 | 122.8 | 8ms |
 * | 设置（纯静态列表） | 528 | 122.8 | 6ms |
 * | 播放页（柱子不挂载） | 127 | **29.5** | 10ms |
 * | 任意页面 + **暂停** | 6 | **1.5** | — |
 *
 * ⇒ 判据闭环：①**每个 tab 都 120fps**（连纯静态的设置页）排除「某一页在动」；
 *   ②**播放页只 29.5fps**（柱子 `active = miniChromeVisible` 在此为 false，流光大
 *   网格 24fps）⇒ 出帧方只有柱子；③暂停即归零。atrace 同口径：静止播放态
 *   `Drawing 1440x3168` p50 **3.08ms** + `flush commands` 1.74ms + `syncFrameState`
 *   0.57ms ≈ **5.4ms/帧**，占 8.3ms 预算的 65% —— 什么都没做就在烧。
 *
 * ### 为什么是 60 而不是 30
 *
 * 历史口径：这里曾经是 `delay(50)` = 20Hz，被用户判「很卡、帧率很低」（见
 * [MiniDancingBars] 顶部的注释），所以才改成逐帧。60fps 的单帧位移只有 16.6ms 量级，
 * 而柱高走的是指数趋近（28/s 起、12/s 落），相邻两帧的差在 15dp 高度的柱子上
 * **不足 1px** ⇒ 60 与 120 不可辨；30fps 时差值翻倍到 ~2px，才有可见的台阶风险。
 * 想再省一半就把这里改成 `30`，但改之前先拿诊断包真机 A/B 一次（别再凭感觉砍）。
 *
 * ⚠ 这是**采样降频**不是**冻结**：`phase`/`energy` 该走的照走，跳过的帧不推进
 *   `lastNanos`，所以 `dt` 永远是「距上一张已绘帧」的真实间隔 ⇒ 曲线形状不变。
 */
private const val MINI_BARS_MAX_FPS = 60

/**
 * 出帧下限间隔 = 目标间隔 × **0.75**。
 *
 * ⚠⚠ 那个 0.75 是防「整数倍抖动」的，不是随手取的余量（2026-10-26 实测踩过）：
 *   `withFrameNanos` 的到点由 vsync 量化 —— 120Hz 下相邻帧正好 **8.333ms**，
 *   若下限写死 `1e9/60 = 16.667ms`，那么「熬过 2 个 vsync」的实测间隔是 16.666ms，
 *   比下限**小千分之一** ⇒ 偶发不通过 ⇒ 实际节奏退化成 16.6 / 25ms **交替**
 *   （真机 `dumpsys gfxinfo` 量到 41.6fps，而不是 60）。
 *   这种不均匀的台阶比稳定低帧率更难看，所以留 25% 容差：120Hz 下稳定落在
 *   每 2 个 vsync（= 稳定 60fps），90Hz 下落在每 2 个 vsync（= 45fps），均无抖动。
 */
private const val MINI_BARS_MIN_FRAME_NANOS =
    1_000_000_000L * 3 / (MINI_BARS_MAX_FPS.toLong() * 4)

