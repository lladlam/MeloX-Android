package com.lladlam.melox.ui.player

import android.view.View
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.lladlam.melox.BuildConfig
import com.lladlam.melox.ui.layout.rememberMeloXWindowInfo
import kotlinx.coroutines.delay

/**
 * 播放页安全区 inset 的**跨注销缓存**（px）。
 *
 * ⚠ 为什么必须是模块级、而不是 `remember`：
 *   `MeloXIOSNowPlayingScene` 的竖屏 / 横屏两套布局是**同一条** `orientTransition`
 *   （`MeloXIOSNowPlayingSharedHost` 里 `updateTransition(isLandscape)`）下的两个
 *   `Transition.AnimatedVisibility`。转屏时旧布局 `exit` 跑完即被 **dispose**，
 *   里面所有 `remember`（含下面两个 padding 助手的 inset 状态）全部丢失；转回竖屏时
 *   竖屏子树是**全新组合**，只能重新向系统要一次 inset。
 *
 *   而转屏后最初若干帧 `ViewCompat.getRootWindowInsets(view)` 仍是**旧方向的值**
 *   （横屏：状态栏被沉浸藏掉 ⇒ top = 0；手势条在侧边 ⇒ navigationBars.bottom = 0），
 *   要等下一次 inset 派发才更新 ⇒ 那一小段时间里顶部 / 底部 padding **双双为 0**：
 *     · 顶部曲名行 / 收藏 / 更多**顶上去了**（少一整条状态栏高）；
 *     · 底部走带 + 三键**掉到屏幕最底**（少一整条导航栏高）。
 *   随后 inset 到位，两端再各自「归位」——即用户报的
 *   「转竖屏时顶部歌名和按钮下移归位、底部三按钮偏下再上移归位」（2026-10-08）。
 *
 *   ⇒ 缓存**上一次在竖屏窗口下读到的真实值**，让重建后的第一帧就有正确 padding，
 *     整段转屏不再有位移。（与 [meloXPlayerStatusBarsPadding] 里「首帧同步读真实值、
 *     不要从 0 起跳」是同源修复 —— 那次只覆盖了「进播放页挂载」这条路径，
 *     没覆盖「转屏导致子树重建」。）
 *
 * ⚠ 只在**窗口是竖的**（`height >= width`）且读到 > 0 时写入：横屏档这两条 inset 本来就
 *   按契约恒 0（见两个助手的 KDOC），把横屏读数写进来只会污染竖屏取值。
 */
private object MeloXPlayerInsetCache {
    var statusBarTopPx: Int = 0
    var navigationBarBottomPx: Int = 0
}

/** 真实状态栏高度（忽略可见性 —— vivo 沉浸后连这个都可能清零，取 max 由调用方负责）。 */
private fun meloXLiveStatusBarTopPx(view: View): Int =
    ViewCompat.getRootWindowInsets(view)
        ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())
        ?.top ?: 0

/** 真实底部导航栏高度（忽略可见性）。 */
private fun meloXLiveNavigationBarBottomPx(view: View): Int =
    ViewCompat.getRootWindowInsets(view)
        ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
        ?.bottom ?: 0

/** 当前 app 窗口是否竖着（`height >= width`）—— 决定 inset 读数能否写进 [MeloXPlayerInsetCache]。 */
private fun meloXIsPortraitWindow(view: View): Boolean = view.height >= view.width

/**
 * Player-area status-bar padding that keeps content in a stable position and independent of
 * whether the system status bar is currently visible (immersive) or not.
 *
 * Normal `statusBarsPadding()` collapses when the status bar is hidden, causing the cover and
 * song info to jump. This helper never uses the visibility-dependent inset: in portrait it uses
 * the *stable* (ignoring-visibility) height; in landscape it is zero (see below).
 *
 * ⚠ 横屏与竖屏规则**不同**：
 * - **横屏**（`isLandscape`）＝ `MeloXImmersivePlaybackEffect` **无条件强制沉浸**（`systemBars`
 *   恒被 `hide()`），且挖孔在**侧边**（不在顶部）⇒ 顶部**零留白**、内容满幅顶到最上，与沉浸前
 *   观感一致；恒藏 ⇒ 取 0 也**不会**随开关/转屏跳。
 * - **竖屏**＝稳定值（忽略可见性）：竖屏顶部是**挖孔区**，内容必须避开，故保留稳定高度；
 *   且该值与 `hide()/show()` 无关 ⇒ 切沉浸无位移。**不再**按 `immersivePlaybackEnabled`
 *   回退到可见式（回退＝可见 inset 归 0、恢复时弹回 ⇒ 上下跳）。
 */
fun Modifier.meloXPlayerStatusBarsPadding(): Modifier = composed {
    val isLandscape = rememberMeloXWindowInfo().isLandscape
    val view = LocalView.current
    val density = LocalDensity.current
    // ⚠ **首帧同步读真实值**，不要从 0 起跳。
    //   `MeloXIOSNowPlayingSharedHost` 只在播放页打开时挂载（折叠态只有迷你栏），
    //   挂载首帧 `remember { mutableIntStateOf(0) }` 会让封面终点**少掉一整条状态栏高度**
    //   （封面偏下），直到 `DisposableEffect` 提交后读到真实 inset 才重排上浮 —— 正是用户
    //   说的「封面偏下再上浮矫正」。改为挂载当帧就把缓存初值设为当前真实高度 ⇒ 终点首帧即正确。
    //   仍保留下面的 `DisposableEffect` 做后续显隐更新（隐藏期间按「可见且 >0 才写入」稳定）。
    //   ★ 2026-10-08 补：**转屏导致子树重建**是同一类问题的第二条路径（竖屏子树在横屏期间被
    //     dispose，`remember` 全丢）。重建首帧 `isVisible(statusBars)` 仍为 false（横屏是沉浸态）
    //     ⇒ 初值又变 0 ⇒ 顶部整条下移归位。故 `live == 0` 时退回 [MeloXPlayerInsetCache]。
    //   ❌ 顺带删掉 `isVisible(statusBars)` 判据本身 —— 下方 DisposableEffect 的注释早就写明
    //     **vivo 上它可能恒为 false**，这里却拿它当守卫，等于首帧必然取 0。
    var stableTopPx by remember {
        mutableIntStateOf(
            meloXLiveStatusBarTopPx(view).takeIf { it > 0 }
                ?: MeloXPlayerInsetCache.statusBarTopPx,
        )
    }

    // ⚠ **兜底：挖孔（摄像头）顶部 inset 与状态栏显隐无关**。vivo 在 `hide(statusBars)` 后连
    //   `getInsetsIgnoringVisibility(statusBars)` 都清零（见下），但 `displayCutout` **不清**
    //   （实测 `frame=[0,0][1440,142] visible=true`）。所以「进播放页时状态栏已被藏」导致
    //   stableTopPx 采到 0 的场景下，用挖孔高兜底 ⇒ 顶部 grabber / 封面不会压到摄像头。
    var cutoutTopPx by remember(view) { mutableIntStateOf(0) }
    val configuration = LocalConfiguration.current
    LaunchedEffect(view, configuration.screenWidthDp, configuration.screenHeightDp) {
        repeat(20) {
            val c = ViewCompat.getRootWindowInsets(view)
                ?.getInsets(WindowInsetsCompat.Type.displayCutout())?.top ?: 0
            if (c > 0) {
                cutoutTopPx = c
                // ★ 同时喂缓存：vivo 在沉浸态会把 `statusBars` 读数一起清零，这条路径可能是
                //   竖屏下**唯一**能拿到「顶部安全区高度」的来源（挖孔高通常 = 状态栏高）。
                //   口径与上面 `top = maxOf(stableTopPx, cutoutTopPx)` 保持一致。
                if (meloXIsPortraitWindow(view)) {
                    MeloXPlayerInsetCache.statusBarTopPx =
                        maxOf(MeloXPlayerInsetCache.statusBarTopPx, c)
                }
                return@LaunchedEffect
            }
            delay(50L)
        }
    }

    // ⚠ **只在拿到 >0 的值时写入缓存**：部分 ROM（实测 vivo）在 `hide(statusBars)` 后连
    //   `getInsetsIgnoringVisibility(statusBars)` 也一起清零 ⇒ 若照单写入，缓存会随显隐
    //   0↔真实高度跳，退出沉浸时内容就下移（本类 bug 的真凶）。改为「>0 才写入」，
    //   隐藏期间保留上一次真实高度 = 真正稳定。
    //   ⚠⚠**不要用 `insets.isVisible(statusBars)` 当守卫**：vivo 上它可能恒为 false，
    //   加了会导致缓存**一次都没被写入**、恒为 0 ⇒ 内容顶到状态栏底下。只看 `v > 0`。
    DisposableEffect(view) {
        // ⚠ 同一条「>0 才写入」纪律同时喂给缓存：横屏 / 沉浸期间读到 0 时不清缓存，
        //   这样转屏重建的第一帧就有值可用（见 [MeloXPlayerInsetCache]）。
        val record: (WindowInsetsCompat) -> Unit = { insets ->
            val v = maxOf(
                insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top,
                insets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
            )
            if (v > 0) {
                stableTopPx = v
                if (meloXIsPortraitWindow(view)) {
                    MeloXPlayerInsetCache.statusBarTopPx = v
                }
            }
        }
        ViewCompat.getRootWindowInsets(view)?.let(record)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            record(insets)
            insets
        }
        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
        }
    }

    val top = if (isLandscape) 0.dp else with(density) { maxOf(stableTopPx, cutoutTopPx).toDp() }
    padding(top = top)
}

/**
 * Display-cutout（挖孔摄像头）水平内边距 —— 让播放器**内容**（左侧大封面 / 右侧信息栏）
 * 避开摄像头挖孔区域，**背景仍保持满屏**（本 Modifier 只加在内容容器上，不碰背板）。
 *
 * - 竖屏：挖孔在顶部状态栏区域，`displayCutout` 的 left/right 读数为 0 ⇒ 本 Modifier 无副作用。
 * - 横屏：挖孔落在某一侧短边，left/right 之一 > 0 ⇒ 内容自动内缩，不挡摄像头。
 * - 若系统本身对横屏做了 letterbox（挖孔区被排到内容区之外），left/right 同为 0 ⇒ 也不会多缩。
 *
 * ⚠ 不注册 insets 监听（`ViewCompat.setOnApplyWindowInsetsListener` 是**覆盖式**的，
 *   再注册一个会顶掉 [meloXPlayerStatusBarsPadding] 里那个）——改用**轮询到拿到为止**，
 *   并在转屏（`screenWidthDp/screenHeightDp` 变化）后重新读取。
 */
fun Modifier.meloXPlayerDisplayCutoutPadding(): Modifier = composed {
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var leftPx by remember(view, configuration.screenWidthDp, configuration.screenHeightDp) {
        mutableIntStateOf(0)
    }
    var rightPx by remember(view, configuration.screenWidthDp, configuration.screenHeightDp) {
        mutableIntStateOf(0)
    }
    LaunchedEffect(view, configuration.screenWidthDp, configuration.screenHeightDp) {
        repeat(20) {
            val cutout = ViewCompat.getRootWindowInsets(view)
                ?.getInsets(WindowInsetsCompat.Type.displayCutout())
            if (cutout != null && (cutout.left > 0 || cutout.right > 0)) {
                leftPx = cutout.left
                rightPx = cutout.right
                return@LaunchedEffect
            }
            delay(50L)
        }
    }
    // 转屏时挖孔 inset 从 0 跳到非零会让「居中」内容横向跳一格 = 抽搐。
    // 用 animateIntAsState 把这次跳变摊平成 ~200ms 滑动，眼睛看到的是顺移而非抖动。
    val leftAnim by animateIntAsState(
        targetValue = leftPx,
        animationSpec = tween(durationMillis = 200),
        label = "cutoutLeft",
    )
    val rightAnim by animateIntAsState(
        targetValue = rightPx,
        animationSpec = tween(durationMillis = 200),
        label = "cutoutRight",
    )
    padding(
        start = with(density) { leftAnim.toDp() },
        end = with(density) { rightAnim.toDp() },
    )
}

/**
 * 播放器区域**稳定**的底部导航栏内边距（忽略可见性）。
 *
 * 与 [meloXPlayerStatusBarsPadding] 同源：横屏 [WindowInsetsCompat.Type.systemBars] 被 `hide()`
 * 时，普通 `navigationBarsPadding()` 的可见 inset 归 0 ⇒ 底部控件（对齐 BottomCenter）与 Column
 * 底沿塌下来，转屏 / 切换沉浸开关就整页上下跳。改用忽略可见性的真实高度让底部 padding 恒定。
 *
 * 取数路径与 MeloXApp 里底栏 chrome 用的 `stableNavigationBarsPadding` 一致，保证「页面内容」
 * 与「底部 chrome」的底沿基准相同，不会出现内容与底栏互相错位。
 *
 * ⚠ 与 [meloXPlayerStatusBarsPadding] 同规则：**横屏恒为 0**（横屏强制沉浸 + `systemBars`
 *   恒藏 ⇒ 底栏满幅），竖屏用稳定值（竖屏只藏 `statusBars`，导航栏恒可见 ⇒ 稳定值恒等于真实值）。
 *
 * ⚠ **不注册** `setOnApplyWindowInsetsListener`（覆盖式，会顶掉 [meloXPlayerStatusBarsPadding]
 *   里那个），改轮询到拿到为止；转屏后按 screenWidthDp/screenHeightDp 重新读取。
 */
fun Modifier.meloXPlayerNavigationBarsPadding(): Modifier = composed {
    val isLandscape = rememberMeloXWindowInfo().isLandscape
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // ★ 2026-10-08：转屏后竖屏子树被 dispose ⇒ 这里的 `remember` 是**全新组合**，而首帧
    //   `navigationBars.bottom` 还是横屏的值（手势条在侧边 ⇒ 0）⇒ 底部控制条掉到屏幕最底，
    //   下一帧 inset 到位才上移归位（用户报障：「底部三按钮会偏下，然后上移归位」）。
    //   故 `live == 0` 时退回 [MeloXPlayerInsetCache] 里上一次竖屏读到的真实值。
    var navBottomPx by remember(view, configuration.screenWidthDp, configuration.screenHeightDp) {
        mutableIntStateOf(
            meloXLiveNavigationBarBottomPx(view).takeIf { it > 0 }
                ?: MeloXPlayerInsetCache.navigationBarBottomPx,
        )
    }
    LaunchedEffect(view, configuration.screenWidthDp, configuration.screenHeightDp) {
        // ⚠ **不要先 delay 再读**：inset 要等系统下一次派发，原实现先 `delay(50L)` 才读第一个值
        //   ⇒ 至少 3 帧里底部是错的。改成当帧先读一次，再按帧（16ms）轮询兜底。
        repeat(20) {
            val live = meloXLiveNavigationBarBottomPx(view)
            if (live > 0) {
                navBottomPx = live
                if (meloXIsPortraitWindow(view)) {
                    MeloXPlayerInsetCache.navigationBarBottomPx = live
                }
                return@LaunchedEffect
            }
            delay(16L)
        }
    }
    val bottom = if (isLandscape) 0.dp else with(density) { navBottomPx.toDp() }
    // ── 临时取证（转屏安全区滞后）：用完即删 ──────────────────────────────────
    // ★ 2026-10-26 功耗：同上方 `meloXPlayerStatusBarsPadding` —— 实参在门控内求值。
    padding(bottom = bottom)
}
