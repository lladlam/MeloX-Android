package com.lladlam.melox.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.lladlam.melox.ui.layout.LocalMeloXListDetail
import com.lladlam.melox.ui.layout.LocalMeloXInDetailPane
import com.lladlam.melox.ui.layout.MeloXAdaptiveDetail
import com.lladlam.melox.ui.layout.openMeloXDetailPage
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import com.lladlam.melox.ui.animation.MeloXPageSlideSpec
import com.lladlam.melox.ui.animation.meloXPagePushSpec
import com.lladlam.melox.ui.glass.finiteOrZero
import com.lladlam.melox.ui.player.MeloSmoothCornerShape
import com.lladlam.melox.ui.player.rememberMeloXDisplayCornerPx
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXThemeMode
import com.lladlam.melox.ui.theme.DarkColors
import com.lladlam.melox.ui.theme.LightColors
import kotlin.math.roundToInt

/**
 * **所有「自右侧推入 / 退回」页面的公共几何**：水平位移 + 0.92 缩放 + **系统屏幕圆角**。
 *
 * 原先是每个页面各写一段内联 `graphicsLayer { translationX / scale / transformOrigin }`
 * （设置详情、搜索子页、搜索详情、首页详情、播客详情，共 5 处），**全都只做位移和缩放、
 * 没有形状** —— 页面在半路是一条直角边横切过屏幕（用户报障「pop 和 push 页面是直角，
 * 没有读取系统屏幕圆角」）。集中到一处后不会再各改各的。
 *
 * ### 圆角取值
 * 用 [rememberMeloXDisplayCornerPx]（= 系统屏幕圆角真值，iQOO 15 = 184px = 46dp），
 * **与播放页下沉背板、播放器外壳共用同一个值**（这是既有约定：同一屏里两个圆角差一点点
 * 就肉眼对不上）。⚠ **全程不收半径** —— 不要再按「与屏幕圆角同心」去缩（2026-09-28 那版
 * 184→105px 已被用户否掉）。
 *
 * 形状用 [MeloSmoothCornerShape]（Lamé 超椭圆、连续曲率）而不是正圆弧：正圆弧曲率在直边
 * 处跳变，肉眼读成「生硬」。与播放页背板同一个 shape，两者不会在屏幕上读成两种角。
 * `scaleX/scaleY` 传进去做**分轴补偿**，保证缩放之后**可见**半径仍是 displayCornerPx。
 *
 * ### ⚠⚠ `clip` 只在「有动画进程」时开（2026-10-03 改）
 * 旧实现是 `clip = true` **恒开**，理由是「静止时页面铺满全屏，裁掉的正好落在物理屏幕圆角
 * 外侧（系统本就遮住那块），观感零变化」。**这个前提不成立**：它要求「ROM 实际遮罩半径 ==
 * 上报半径」且形状一致，实测真机不是 —— 页面四角会被切出一条**看得见**的屏幕圆角缺口，
 * 缺口露出的是窗口底色/黑背板 ⇒ **亮色页面（如歌单详情）四角发黑**，正是用户报障
 * 「在某些界面，比如歌单，**应该表现为直角（没有任何动画进程）**，但是异常表现为了屏幕圆角」。
 *
 * 而这条裁剪在静止时**根本不需要** —— 它唯一的用途是「页面半露在屏上时，让露出来的那条边
 * 读成系统圆角」（2026-09-28 用户口径「pop 和 push 页面是直角，没有读取系统屏幕圆角」
 * 指的是**动画中**那条边，见 [MeloXInterruptiblePage]）。⇒ 判据 = **有动画进程才裁**：
 * - 容器还在推入 / 退回（调用方给的 `settled == false`）⇒ 裁；
 * - 跟手退回 `p > 0`（页面确实在位移 / 缩小）⇒ 裁；
 * - **静止（已 settle 且 `p == 0`）⇒ 不裁**，页面四角就是直角。
 *
 * ⚠ 跟手退回的位移是**逐帧长出来**的，拖动刚开始时进度还很小 —— 「跟手」这一条必须挂在
 *   `p > 0` 上（哪怕 p 只有 0.001），否则页面左边缘会以直角横切过屏幕。
 *
 * ⚠ 整页推入/退回的位移**不在这里** —— 已交给 [MeloXInterruptiblePage] 里的 `AnimatedContent`
 *   （`slideIntoContainer` / `slideOutOfContainer`）。本修饰符现在只负责**预测式返回手势**
 *   那一条通道（带 0.92 缩放的跟手位移），叠加在 `AnimatedContent` 的内容**内部**；
 *   但**裁剪**要同时覆盖「容器推入/退回中」这一段（那一段 `p` 恒为 0）。
 *
 * @param progress 跟手退回的进度 0→1（只有预测式返回会用到）。非跟手场景传默认的常量 0。
 * @param settled 容器自己的过渡是否已经结束（`currentState == targetState`）。
 *   ⚠ 默认 `false` = 保持旧行为（恒裁）；调用方漏传只会「多裁」不会「少裁」，
 *     不会把动画中那条圆角边弄丢。
 */
@Composable
internal fun Modifier.meloXPageSlideGeometry(
    progress: () -> Float = { 0f },
    settled: Boolean = false,
): Modifier {
    val displayCornerPx = rememberMeloXDisplayCornerPx()
    val shapeCache = remember { MeloXSlideShapeCache() }
    return this.graphicsLayer {
        // 进度会进 HWUI 矩阵与 Shape 半径，NaN/±Inf 不可逆 ⇒ 出口按项目约定清洗。
        val p = progress().finiteOrZero().coerceIn(0f, 1f)
        translationX = size.width * p
        // 缩放只在「跟手退回」时生效：手势把页面推出去的过程中页面轻微缩小。
        val scale = 1f - MeloXPageSlideScaleDrop * p
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(0f, 0.5f)
        // 静止时不裁（见上）；`shape` 只在真的要裁时喂，免得白白重建 outline。
        val clipping = !settled || p > 0.0005f
        clip = clipping
        if (clipping) shape = shapeCache.of(displayCornerPx, meloXQuantizedScale(scale))
    }
}

/**
 * `MeloSmoothCornerShape` 的**实例缓存**（按「半径 + 量化缩放」命中）。
 *
 * ⚠ 为什么必须缓存：`MeloSmoothCornerShape` 是普通 class、**没有实现 `equals`**（引用相等），
 * 所以 `graphicsLayer { shape = MeloSmoothCornerShape(...) }` 每帧都会喂进一个"新"形状 ⇒
 * 图层实现每次都 `RenderNode.setOutline(shape.createOutline(...))`，而
 * `RenderNode.setOutline` 又是按**引用**判等（新 `Outline` 必然不等）⇒
 * **节点每帧被标脏、裁剪每帧重建**，页面越大越贵。
 *
 * 缩放已经过 [meloXQuantizedScale] 吸附到 1/64 ⇒ 整段推入（p = 0，缩放恒为 1）
 * 只会产出 **1 个**实例 ⇒ outline 从「每帧重建」降到「整段 1 次」；
 * 跟手/退回整段也只有个位数次。
 *
 * ⚠ 只在绘制期（`graphicsLayer` 的 lambda）里用，**不参与 Compose 快照** ——
 *   因此故意用普通字段而不是 `mutableStateOf`（在那里写状态是非法且会引入额外失效）。
 *   每个调用点各自 `remember` 一个实例，不存在跨图层串用。
 */
internal class MeloXSlideShapeCache {
    private var cachedRadius = Float.NaN
    private var cachedScaleX = Float.NaN
    private var cachedScaleY = Float.NaN
    private var cachedShape: Shape? = null

    fun of(radiusPx: Float, scale: Float): Shape = ofXY(radiusPx, scale, scale)

    /**
     * **分轴版**：页面退让层（`MeloXApp` 里 recede 的 `graphicsLayer`）挂的是
     * **sx ≠ sy 的非等比缩放**（见那边的 `sx` / `syFull` 推导），进不了单轴入口。
     *
     * ⚠ 为什么这里必须也走缓存：同一个坑在两个调用点都存在 —— 早前只有推入/退回
     *   这一条是被认真优化过的（`of` + [meloXQuantizedScale]），而播放页打开时
     *   **外壳 morph 与后方页面退让是同时跑的**（两边的 master fraction 严格同拍），
     *   漏掉的那一边照样每帧喂一个新 shape ⇒ 一边便宜一边贵，总成本并没降下来。
     */
    fun ofXY(radiusPx: Float, scaleX: Float, scaleY: Float): Shape {
        val hit = cachedShape
        if (hit != null && cachedRadius == radiusPx && cachedScaleX == scaleX && cachedScaleY == scaleY) {
            return hit
        }
        return MeloSmoothCornerShape(radiusPx, scaleX, scaleY).also {
            cachedRadius = radiusPx
            cachedScaleX = scaleX
            cachedScaleY = scaleY
            cachedShape = it
        }
    }
}

/** 推入/退回页面在跟手退让到屏外时的缩放落点（= 原先各处内联的 `0.08f`）。 */
private const val MeloXPageSlideScaleDrop = 0.08f

/**
 * 把喂给 [MeloSmoothCornerShape] 的**缩放吸附到 1/64 步长**（性能用，不是观感用）。
 *
 * 形状的 `scaleX/scaleY` 是**分轴半径补偿**（rx = span/sx），所以它一变，outline 就是一条
 * 新路径 ⇒ RenderNode 的 `setOutline` 一次、裁剪层重栅格化一次。而推入/退回每帧都在改这个
 * 值 ⇒ **一层 128 点的路径裁剪被每帧重建**（进入页 + 旧页 = 两层）。
 * 吸附后一段 0.92→1.0 的过渡里 outline 只重建 ~6 次。
 *
 * 代价：可见半径误差 ≤ 0.85%（184px → ±1.6px），肉眼不可辨；观感与不吸附等价。
 */
internal fun meloXQuantizedScale(scale: Float): Float =
    if (!scale.isFinite()) 1f else (scale * 64f).roundToInt() / 64f

/**
 * **「被盖住的下层页」的退让几何：只做一件事 —— 向左让出 1/3 屏宽。**
 *
 * ### ⚠ 为什么只有位移（2026-10-01 重做，用户：「重做这部分动画，iOS 风格的 push 和 pop」）
 * iOS `UINavigationController` 的 push/pop 里旧页**只有水平位移**：新页满宽从右侧压上来，
 * 旧页以大约 **1/3 的速度**向左走，两页之间始终留着一条缝 —— 纵深是从**那道缝 + 内容错位**
 * 里读出来的，不是从「往上面叠了什么」里读出来的。所以这里刻意**不缩放、不压暗、不裁剪**：
 *
 * - **不缩放**：一缩就读成「原地变小」，与「被新页顶走」是两回事（用户：重点不是下沉）。
 * - **不压暗**：整页压暗会把旧页内容吃掉，而那道缝恰恰要靠旧页内容**还在动**才成立。
 * - **不裁剪**：旧页左缘全程在屏外，右缘全程被新页盖住（新页左缘 `(1-p)·W` 恒 **小于**
 *   旧页右缘 `(1-p/3)·W`，两页永不脱开）⇒ 旧页四个圆角**一次都不会露在屏上**，
 *   裁剪纯属白付一层离屏缓冲（页面自身露在外面的那条边由**推入页**的
 *   [meloXPageSlideGeometry] 负责系统圆角，那才是需要裁的那个）。
 *
 * 与 [meloXPageSlideGeometry]（推入页自身）配对使用，且**必须同拍**：
 * 两页用同一个时长 + 同一个缓动（[com.lladlam.melox.ui.animation.MeloXMotion.PageTransitionMillis]
 * + `MeloXPageSlideSpec`）时，`旧页位移 = -W/3 · p` 与 `新页位移 = (1-p)·W` 在任意时刻都严格互补；
 * 任何一侧换了时长/缓动，缝就会忽宽忽窄，读成「两页各走各的」。
 *
 * ### ⚠ 跟手退回时**必须由手指驱动**，不能只跟 `route != null`
 * 预测式返回拖到一半松手 / 取消，旧页要跟着手指走。调用点的写法是
 * `min(动画值, 1 - backProgress)`：推入时取动画值，跟手与收尾时取手指那一项，
 * 且在 `route = null` 的交界处两项都已经是 0 ⇒ 不会跳。
 *
 * ### ⚠ 只能用在「无 hero morph」的 push
 * 旧页一旦位移/缩放，它内部卡片的**屏幕坐标就变了** —— 从卡片 morph 出来的共享元素封面
 * 会在半路错位。所以 discovery 详情 / podcast 详情 / 搜索详情（**有 hero**）的旧页
 * **必须保持静止**，只有「整页推入、无 hero」的两处（设置详情、搜索子页）能用它。
 *
 * ### ⚠ `progress` 的来源（调用方约定）
 * 由调用方用 `animateFloatAsState(animationSpec = MeloXPageSpec, ...)` 跑
 * （临界阻尼**形状**的缓动，推入/退回同一条），
 * 再与跟手进度取 `minOf(动画值, 1f - backProgress)`：
 * - 推入：`1 - backProgress` 恒为 1 ⇒ 取动画值，0→1 平滑展开；
 * - 跟手退回 / 收尾：`backProgress > 0` ⇒ 取手指那一项，旧页**严格跟着手指**（无滞后）；
 * - `route` 翻转（详情被移除）的那一帧，两项都已经是 0 ⇒ 不会跳帧。
 * ⚠ 固定时长（不是渐近收敛的弹簧）⇒ 旧页与新页**在同一个时刻精确到位**，不会出现
 *   「新页已经停了、旧页还在爬」的收尾错位。代价是目前被中断时从 0 速度重开
 *   （tween 的固有性质，中断点会有一次轻微顿挫）—— 换来的是收尾不再有长尾。
 *
 * ⚠ 跟手进度**归零的时机**在调用方（下次打开时清零），不在这里 —— 提前清零会让
 *   `1 - backProgress` 重新变回 1，旧页瞬移回屏内。
 *
 * @param progress 被覆盖的程度：0 = 前台，1 = 完全退到后面。
 */
internal fun Modifier.meloXPageRecede(progress: () -> Float): Modifier =
    composed {
        if (LocalMeloXListDetail.current && !LocalMeloXInDetailPane.current) return@composed this
    // ⚠⚠ **必须是布局期位移，不能是 `graphicsLayer { translationX }`**（2026-10-02 改）。
    //   共享元素 morph 的两端 bounds 取自 `SharedTransitionLayout` 的 **lookahead 布局坐标**，
    //   而 `graphicsLayer` 的绘制期变换**不进 lookahead** ⇒ 旧页动了、卡片在屏上的实际位置变了，
    //   但 morph 读到的起点没变 ⇒ 封面留在原地、与卡片脱开（就是「半路错位」）。
    //   改成布局期 place 偏移后，卡片 bounds 每帧跟着旧页更新 ⇒ 封面**跟着卡片一起左移**，
    //   于是「旧页让出 1/3 + 封面一镜到底」可以同时成立。
    //   ⚠ 代价：状态读取落在 place 阶段（每帧重新 place 本图层，**不重新测量子树**、也不重组）。
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            // 进度会进 place 偏移 ⇒ 出口清洗。
            val p = progress().finiteOrZero().coerceIn(0f, 1f)
            // ⚠ 用 `place`（绝对坐标）而不是 `placeRelative`：原 `translationX` 是绝对左移，
            //   RTL 下不镜像。
            placeable.place((-placeable.width * MeloXPageRecedeParallax * p).roundToInt(), 0)
        }
    }
        // ⚠⚠⚠ **图层隔离，不能删**（2026-10-19 修「左侧推入一顿一顿」）。
        //   上面的布局期 `place` 每帧都会改本节点在父显示列表里的 translate。Compose 的
        //   `AndroidComposeView.dispatchDraw` 在布局变化后要**重录显示列表**，而重录的粒度
        //   取决于是不是有独立 RenderNode：
        //   · **没有** `graphicsLayer` ⇒ 本节点整棵子树的绘制指令（每段文本、每张
        //     `drawBitmap`、每个圆角矩形…）都内联在父显示列表里 ⇒ 位移一变就要把**整页**
        //     重新录一遍。首页/发现页一屏几百条指令，120Hz 下 8.3ms 预算直接爆掉，
        //     肉眼就是「旧页往左走时一顿一顿」。
        //   · **有** `graphicsLayer` ⇒ 本节点被提升为独立 RenderNode，父显示列表里只剩
        //     「translate + drawRenderNode」两条指令，位移变化只改这两条，子树**零重录**。
        //   ⚠ 位置：必须挂在 `layout` 的**内侧**（链上更靠后）。`graphicsLayer` 在外侧时，
        //     layer 的内容就是「子树按旧坐标画出来的结果」，平移仍会让 layer 内容失效重录。
        //   ⚠⚠ **绝不能写 `CompositingStrategy.Offscreen`**（2026-10-19 三次修，
        //     用户报「底栏液态玻璃变成半透明了」）：
        //     本轮一度改成 `Offscreen`，理由是「trace 里没有 1440x3168 的 `drawLayer`
        //     ⇒ 层没建」。**那条推论是错的** —— `drawLayer` 是 HWUI 真的去开一块离屏
        //     surface 才会出现的 slice，而"父显示列表里只有一条 `drawRenderNode`"这件事
        //     与它无关：只要 coordinator 挂了 `graphicsLayer`，Compose 就给了它独立的
        //     `RenderNode` / 独立 display list，`alpha = 1f` 这种"无属性"的层照样是独立的，
        //     只是 HWUI 走 fast path 直接把它画进当前画布、不开 surface。
        //     ⇒ `alpha = 1f` 已经满足「位移只改 op 的 translate、子树零重录」，这就够了。
        //     而 `Offscreen` 会把这个整页节点变成**离屏合成层**，它正好坐在
        //     `bottomChromeBackdrop` 的**录制内容里**（`MeloXApp` 里 recede Box 的
        //     `Modifier.layerBackdrop`）：底栏玻璃把这个 layer 当 shader 的 `content`
        //     采样，嵌套的离屏层在这里被清成**全透明** ⇒ 玻璃只剩 `surfaceColor`
        //     那 52% 涂层 + 高光 ⇒ 用户看到的「半透明」。
        .graphicsLayer { alpha = 1f }
    }

/**
 * **旧页向左让出的视差（屏宽比例）= 1/3**。
 * 与 iOS `UINavigationController` push 里旧视图的落点同量级（`-width/3`）。
 * ⚠ 用户 2026-10-01 定调：「**重点不是下沉，是旧页面左移**」——这个值就是这套效果的全部，
 *   只许加大，不许用缩放/压暗去「补」层次。
 */
private const val MeloXPageRecedeParallax = 1f / 3f

/**
 * **整页推入 / 退回容器 —— 直接用 `AnimatedContent`（Compose 自带的 push/pop 原语）**。
 *
 * ### 为什么换掉手写方案
 * 之前是「手动 `Animatable` 驱动位移 + `AnimatedVisibility` 当 350ms 时长载体」。它有两个
 * 结构性缺陷，都不是调参能治的：
 * 1. **打断时丢速度**：位移由 `LaunchedEffect(visible)` 里的 `animateTo` 驱动，翻转会把上一个
 *    协程**取消**掉 —— 弹簧只能从当前**位置**、以 **0 速度**重开 ⇒ 页面先「一滞」再加速
 *    （用户报「顿挫」）。
 * 2. **打断语义得自己维护**：退回途中点击要能被正确接管、返回键不能穿透…… 一堆手写分支。
 *
 * `AnimatedContent` 是框架自带的「按状态切换内容」原语，正好就是 push/pop：
 * - 目标翻转时它**不重建动画**，而是把同一条 `TransitionAnimationState` 改向 ⇒ 用
 *   [meloXPagePushSpec]（临界阻尼弹簧）时**位置与速度双连续**，反转如 iOS 般顺滑；
 * - `slideIntoContainer` / `slideOutOfContainer` 就是官方为整页推入/退回提供的 API；
 * - `AnimatedContentScope : AnimatedVisibilityScope`（Compose 1.7）⇒ 内部的 `sharedElement`
 *   一镜到底照常接入同一个 `SharedTransitionLayout`。
 *
 * ### 与「被盖住的下层页」的关系
 * 本容器**只装覆盖层**（详情/子页），下层页在容器之外、全程不动 —— 这正是 hero morph 的
 * 硬要求（旧页一动，从卡片 morph 出来的封面就会半路错位）。有退让需求的旧页走
 * [meloXPageRecede]，与本容器无关。
 *
 * ### 返回键
 * `AnimatedContent` 只在「该内容存在」时组合它 —— 退场途中被滑走的那一页**仍在组合**
 * （退出过渡没跑完），所以把 `BackHandler` 挂在 `shown == true` 的分支里，它就自动满足：
 * 前台按=关闭；**退出途中按=被本页吞掉、不穿透**到下层/Activity；退场结束组件注销 ⇒
 * 返回键自动交还。不需要任何手写的 in-flight 判断。
 *
 * ### 预测式返回手势
 * 仍由调用方持有并驱动 [retreatProgress]（带 0.92 缩放的跟手位移），叠加在**内容内部**的
 * [meloXPageSlideGeometry] 上；与容器自身的推入/退回位移互不干扰（手势只在容器已静止时发生）。
 *
 * ### ⚠⚠ 「推入期间不要组合重内容」= 调用方的义务（2026-10-09，修播客页一顿一顿）
 * 本容器只负责把内容**推入**，管不了内容有多重。而推入期的目标帧率是 **120fps（8.3ms/帧）**
 * 子页只要在推入那几帧里组合自己的重脚手架，
 * 就会和滑动/下层退让抢 UI 线程，帧一顶过 16.7ms 就被肉眼读成「一顿一顿」。
 *
 * 已知的重脚手架（按代价排序）：
 * - **嵌套 `SharedTransitionLayout`** —— 本页通常已在外层某个 STL 里，再套一层 = 每帧多跑
 *   一次 lookahead 遍历；
 * - `PullToRefreshBox`（自带一条无限进度动画）；
 * - 其它无限动画（`CircularProgressIndicator` 等），每条都在逐帧请求新帧；
 * - 长列表 + 图片解码 / 同步构造的网络客户端。
 *
 * 判据只有一条：**这次推入里有没有 `sharedElement` 的两端？**
 * - **有**（卡片↔详情 hero morph、分类页里可点开的歌单行）⇒ 内容必须**全程在场**，
 *   晚组合一步共享元素就配不上对、morph 直接断。此时只能靠「先组合、但延后拉数据」
 *   缓解（`MeloXPodcastScreen(deferLoadMillis = …)` 那种半套补救）。
 * - **没有** ⇒ 应当把**内容的组合整体推迟到推入落定之后**，动画窗口里只放一个轻量占位。
 *   参考实现：`SearchScreen` 的 `subPageComposed` + `MeloXSubPagePlaceholder`
 *   （落定判据走 `meloXSettledMillis(PageEnterMillis, 余量)`，自动跟随系统动画缩放）。
 *
 * @param visible 是否显示（= 详情/子页是否打开）。
 * @param retreatProgress 预测式返回手势进度 0→1（调用方持有并驱动）；无手势的页面传 `{ 0f }`。
 * @param onBack 非 null 时由本容器接管返回键（调用方应删掉自己的 `BackHandler`）。
 * @param content 内容；其 receiver 即 `AnimatedVisibilityScope`，供内部 `sharedElement` 接入
 *   同一 `SharedTransitionLayout` 做一镜到底。
 */
@Composable
internal fun MeloXInterruptiblePage(
    visible: Boolean,
    modifier: Modifier = Modifier,
    retreatProgress: () -> Float = { 0f },
    // 非 null 时由本容器统一接管返回键（调用方应删掉自己的 BackHandler）：
    //  · 前台按返回 ⇒ onBack()（关闭）；
    //  · 退出途中再按返回 ⇒ 吞掉不穿透（否则会穿透到下层/Activity，表现为「连点把页面整个关掉」）。
    onBack: (() -> Unit)? = null,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    MeloXAdaptiveDetail(visible, onDismiss = onBack) { shown ->
        MeloXInlinePage(shown, modifier, retreatProgress, onBack, content)
    }
}

@Composable
private fun MeloXInlinePage(
    visible: Boolean,
    modifier: Modifier,
    retreatProgress: () -> Float,
    onBack: (() -> Unit)?,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedContent(
        targetState = visible,
        transitionSpec = {
            if (targetState) {
                // 打开：详情自右侧满宽推入。下层页在容器之外、全程不动（hero morph 的硬要求），
                // 所以另一个内容（空占位）用 ExitTransition.None 立即离场。
                //
                // ⚠⚠ 这里必须用 **tween**（[MeloXPageSlideSpec]），不能退回 `meloXPagePushSpec()`
                //   那条弹簧（2026-10-19 二次定案，用户报「一直在左右顿挫横移」）。
                //   弹簧是**渐近收敛**：k=800 时残差降到 1px 要 **≈390ms**（不是旧注释写的
                //   0.27s —— 0.27s 只到 5.5px）。也就是 350ms 载体窗结束后，页面还要再爬
                //   ~40ms，且每帧位移只有 0.1~0.5px ⇒ `IntOffset` 取整后变成
                //   「0,0,1,0,0,1…」的阶梯，肉眼正是「顿挫横移」。
                //   旧页 meanwhile 也在同族弹簧上爬（见 `meloXPageRecede`），两页末段互不同步
                //   ⇒ 相对缝宽在这 40ms 里还在变，读成「左右错动」。
                //   tween 在 `durationMillis` 处**精确等于目标**，尾巴恒为 0；而
                //   [MeloXPageEasing] 本身就是 ζ=1 阻尼解按同一时标采样的形状 ⇒ 起步/中段
                //   观感与弹簧一致，只是末端精确到位。
                (slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = MeloXPageSlideSpec,
                ) togetherWith ExitTransition.None)
                // ⚠ 显式**关掉尺寸动画**（2026-10-19）：`AnimatedContent` 默认带
                //   `SizeTransform()`，会在两页尺寸不同时对**容器尺寸**做一条独立动画。
                //   而 `slideIntoContainer` 的位移是按 `contentSize` 算的 ⇒ 容器宽度一旦
                //   在动画中插值，位移目标每帧都在变，页面看起来就是「一边横移一边被
                //   来回推」。两个分支本来就都是 `fillMaxSize()`、尺寸恒等，这条动画
                //   从来没用，只会引入风险。
                .using(null)
            } else {
                // 关闭：详情滑回右侧；空占位（target 内容）用 EnterTransition.None 立即就位（本就不可见）。
                // ⚠ `togetherWith` 左操作数必须是 **EnterTransition**：这里进入的是空占位、退出的是详情。
                (EnterTransition.None togetherWith slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = MeloXPageSlideSpec,
                )).using(null)
            }
        },
        modifier = modifier,
        label = "MeloXInterruptiblePage",
    ) { shown ->
        if (shown) {
            // 退场途中本分支仍被组合（退出过渡没跑完）⇒ 返回键依旧被吞掉、不穿透到下层/Activity；
            // 退场结束后组件注销 ⇒ 返回键自动交还。无需手写 in-flight 判断。
            if (onBack != null) BackHandler { onBack() }
            // ⚠ 「静止」判据 = 本内容自己的过渡已结束（`currentState == targetState`）：
            //   推入途中是 PreEnter→Visible、退回途中是 Visible→PostExit，两者都不相等 ⇒ 裁圆角；
            //   两边相等 = 已经落位、没有任何动画进程 ⇒ 不裁，四角就是直角。
            //   （`AnimatedContentScope : AnimatedVisibilityScope`，`transition` 就是本内容的。）
            val settled = transition.currentState == transition.targetState
            // ⚠⚠ **进入过渡期间无视 `retreatProgress`**（2026-10-19，用户报「左右顿挫横移」）。
            //   位移是两条通道**相加**的：`AnimatedContent` 的 slideIntoContainer 一条，
            //   本修饰符的 `translationX` 另一条。而 `retreatProgress` 通常是一个
            //   调用方持有的 `Animatable`，**上一次手势返回结束后停在 1f 而不是 0f**
            //   （典型：`HomeOverlayPage` 的 `progress.animateTo(1f, tween(160))`，页面
            //   已经滑出屏外，值留在 1）。调用方靠 `LaunchedEffect(shown) { snapTo(0f) }`
            //   复位 —— 结果要等一次协程分发，可能晚于过渡的第一帧 ⇒ 那几帧里页面
            //   多吃了整整一个屏宽（起点跑到 2W 外），随后突然跳回来 = 一次可见的
            //   「往右一窜」。
            //   进入期间本来就不可能发生预测式返回手势（页面还在推入），所以这里直接
            //   屏蔽是**零代价**的：既吃掉残值，也不影响任何真实的跟手退回。
            //   ⚠ 只在「进入」屏蔽（`EnterExitState.PreEnter`）：退出方向（`PostExit`）必须
            //     保留它，否则手势走到一半松手、`onBack()` 之后位移会被瞬移回 0。
            val entering = transition.currentState == EnterExitState.PreEnter
            Box(
                Modifier
                    .fillMaxSize()
                    .meloXPageSlideGeometry(
                        progress = { if (entering) 0f else retreatProgress() },
                        settled = settled,
                    ),
            ) {
                content()
            }
        } else {
            // 空占位：只为了让 AnimatedContent 有个「关闭态」内容可切（本身不可见）。
            Box(Modifier.fillMaxSize())
        }
    }
}

/**
 * 主窗口内的详情交给自适应导航；从其它 Activity 打开时仍使用原生页面。
 *
 * ⚠⚠ **不要在这里做任何转场定制** —— `overridePendingTransition` / `ActivityOptions
 *   .makeCustomAnimation` / 主题里的 `windowAnimationStyle` 一个都不要用。转场是平台的活：
 *   窗口的位置与速度连续性由 WindowManager 保证，快速连续点按、转场途中反向都不会
 *   「中途接上」也不会「从头重放」，并且天然带系统屏幕圆角与预测式返回。
 *
 * 这个结论是从 Orange（`com.baron.orange`）的设置页转场反推出来的（报告：
 * `tmp/orange-page-transition-recon.md`）：它那套手感是**零 App 代码**的系统 Activity 转场
 * —— 全包没有 `res/anim*`、没有 `overrideActivityTransition`、主题也没覆盖
 * `windowAnimationStyle`。
 */
internal fun Context.startMeloXPage(intent: Intent) {
    if (!openMeloXDetailPage(intent)) startActivity(intent)
}

/** 关闭一个页面 Activity。转场同样交给平台，配对地不要配 `overridePendingTransition`。 */
internal fun Activity.finishMeloXPage() {
    finish()
}

/**
 * **页面 Activity 的窗口准备**：把窗口底色设成「App 当前真实配色」下的页面底色。
 *
 * ### 为什么需要运行时覆盖
 * `Theme.MeloX.Page` 里的 `@color/melox_page_background` 是**静态**资源，只能表达
 * 「跟随系统深/浅色」；而 MeloX 的「主题模式」是**应用级**的（`MeloXSettingsRuntime`，不走
 * AppCompatDelegate / uiMode）—— 用户在系统浅色下选了「深色」时，静态色就是错的。
 * 窗口不透明 ⇒ 转场第一帧起就要用这个底色作画，色不对会看到一次明显的闪变。
 *
 * ⚠ 必须在 `setContent` **之前**调用（越早越好）：窗口在 onCreate 走完前就可能已经上屏。
 * ⚠ 只能设成**不透明**色：不透明窗口 + 透明底 = 先画一帧纯黑。
 *
 * 颜色取 [LightColors] / [DarkColors] 的 `background`（与 Compose 侧同一个事实来源，
 * 不是另抄一组十六进制字面量）。
 */
internal fun Activity.prepareMeloXPageWindow() {
    val dark = when (MeloXSettingsRuntime.themeMode) {
        MeloXThemeMode.Dark -> true
        MeloXThemeMode.Light -> false
        MeloXThemeMode.System ->
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }
    window.setBackgroundDrawable(
        ColorDrawable((if (dark) DarkColors.background else LightColors.background).toArgb()),
    )
}

/**
 * **页面 Activity 的内容宿主 —— 转场完全交给平台，这里一帧动画都不做。**
 *
 * ### ⚠ 为什么这不是原来的 `MeloXPredictiveBackPage`
 * 原来这里挂了两样东西，现在都删了。成因不同，但同源 —— 都是「在应用内把系统已经做得更好的
 * 事又重放了一遍」：
 *
 * 1. **入场**：`AnimatedVisibility(enter = meloXPageEnter(...))` 在 Compose 里跑整页位移，
 *    与 `Theme.MeloX.Page` 给的原生窗口转场**叠加** —— 同一次推入被播两遍；而且 Compose 位移
 *    挂在组合/绘制帧上，被打断时必然有顿挫（Position 与 Velocity 不能双连续）。
 * 2. **退回**：`PredictiveBackHandler { progress.snapTo(...) }` 自研跟手位移。真实 Activity +
 *    `android:enableOnBackInvokedCallback="true"` **本来就支持**预测式返回：系统会把上一个
 *    Activity 拉出来做跟手与取消回弹。自研那一份只是跟系统抢同一个手势。
 *
 * 现在整页的推入 / 退回 / 打断 / 预测式返回**全部**由 WindowManager 负责，与 [startMeloXPage]、
 * [prepareMeloXPageWindow] 的约定一致。
 *
 * ### 与 [MeloXInterruptiblePage] 的分工
 * - **跨 Activity**（本宿主）：整页跳转、且没有 hero 封面需要 morph 的页面。转场走平台。
 * - **同 Activity**（[MeloXInterruptiblePage]）：需要共享元素一镜到底的页面（首页 / 搜索 /
 *   播客详情）。共享元素跨 Activity 代价太高，只能留在同一棵组合里。
 *
 * @param content 页面内容。⚠ 返回键不需要在这里接线：默认就是 Activity 的 `finish()`。
 */
@Composable
internal fun MeloXPageHost(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
    }
}
