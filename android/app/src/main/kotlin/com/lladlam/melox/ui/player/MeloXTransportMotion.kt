package com.lladlam.melox.ui.player

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.lladlam.melox.ui.glass.MeloXLiquidInteraction
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXSymbolVariant
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════════════════
// 传输键动画的**共享实现**（2026-10-28 从 `MeloXNowPlayingCoreControls` 抽出）
//
// 目的：迷你栏的 play/pause 与 next 要和**播放页同款**，而播放页那两份实现
//   （`ScenePlayPauseButton` 的形变、`SceneTransportButton` 的传送带）原本是
//   private、且把字号/颜色写死在 34.sp / White ⇒ 迷你栏（18~22sp、onSurface 色）
//   没法直接用。⇒ 把**状态机与绘制**抽到这里，常量继续留在原处（单一真相源），
//   两边只传自己的字号与颜色。
//
// ⚠ 取用顺序（用户口径）：① 软件内已有实现 → ② kyant0 源码仓库 → ③ 其他。
//   本文件三条分别是：传送带字形复用 `rememberSfChevronSpec` / `drawChevronHalf`
//   / `SCENE_TRANSPORT_SPRING`（①）；形变复用 `SCENE_MORPH_*` 全套常数（①）；
//   整栏点按反馈**直接采用 kyant0 官方 Button 的按压模型**（②）—— 复用软件内已移植好的
//   `PublicInteractiveHighlight`（按压弹簧 + 手指位置高光着色器都是官方的），
//   本文件只负责把 `pressProgress` 折算成整栏缩放，见 ③ 段注释。
// ════════════════════════════════════════════════════════════════════════════

// ───────────────────────────── ① 播放/暂停形变 ─────────────────────────────

/**
 * 播放/暂停形变的状态机（**播放页与迷你栏共用一份**）。
 *
 * 逻辑与 `ScenePlayPauseButton` 2026-10-23 定稿版**逐行相同**，只是从 composable
 * 里挪出来，好让两个尺寸的按钮共用同一套时序（改一处两边一起变）：
 *   · 单次点击：一条曲线缩到谷底（115ms）→ 途中 0.5 处换字 → 弹簧弹回（过冲 ~1.10）；
 *   · 连点（间隔 < 300ms）：浅谷底 0.80 + 60ms → 硬弹簧回弹，绝不越点越小；
 *   · 外部引起的播放态变化（切歌自播 / 焦点丢失 / 耳机拔出 / 通知栏）同样走形变。
 *
 * ⚠ 三个**必须在同一次事件里写完**的量（分开写会多一次重组、中间那帧字形是错的）：
 *   [fromIsPlaying]（出发字形）、[targetIsPlaying]（目标）、[swapped]（是否已换字）。
 */
internal class MeloXPlayPauseMorphController {
    /**
     * 形变缩放。**故意不在组合期 `by` 解包** —— 解包就是每帧重组整颗按钮
     * （连带字形的 Canvas / Paint / 度量全重建）。保留 Animatable，只在绘制期读。
     */
    val scale = Animatable(1f)

    /** 目标是否播放。**乐观值**：点击当帧就翻，不等播放器状态回抛（实测迟 1~2 帧）。 */
    var targetIsPlaying: Boolean by mutableStateOf(false)
        private set

    /** 这一拍「从哪个字形出发」——必须在点击那一刻固化当前显示的字形。 */
    var fromIsPlaying: Boolean by mutableStateOf(false)
        private set

    /** 是否已越过换字点：false ⇒ 仍在显示 [fromIsPlaying]。 */
    var swapped: Boolean by mutableStateOf(true)
        private set

    /** 起拍计数：每次点击 +1，用于重启动画协程。 */
    var token: Int by mutableIntStateOf(0)
        private set

    private var primed = false
    private var rapid = false
    // 上一次起手时刻（uptime）。用普通数组而不是 State：它只参与一次算术判断，
    // 写它绝不该引起重组（[token] 那次重组已经够了）。
    private val clock = longArrayOf(Long.MIN_VALUE)

    /** 当前应该显示的字形（组合期读 ⇒ 只在换字那一帧重组）。 */
    val displayIsPlaying: Boolean get() = if (swapped) targetIsPlaying else fromIsPlaying

    /** 起手：固化出发字形 → 翻目标 → 起拍。 */
    fun startMorph(to: Boolean) {
        val nowMs = SystemClock.uptimeMillis()
        rapid = clock[0] != Long.MIN_VALUE && nowMs - clock[0] < SCENE_MORPH_RAPID_WINDOW_MS
        clock[0] = nowMs
        fromIsPlaying = if (swapped) targetIsPlaying else fromIsPlaying
        // ★ 连点：**立即换字**。连点时的形变已经缩到 0.8 就走，看不见「谷底换字」那一拍，
        //   再让字形等到阈值才翻反而会比手指慢半拍。
        swapped = rapid
        targetIsPlaying = to
        token++
    }

    /** 点击路径：起手到「当前目标的反面」（乐观翻转）。 */
    fun startMorphToggle() {
        startMorph(!targetIsPlaying)
    }

    /**
     * 外部引起的播放态变化。若只是回抛确认乐观值（相等）则不重复起动画。
     */
    fun onExternalIsPlaying(isPlaying: Boolean) {
        if (!primed) {
            primed = true
            targetIsPlaying = isPlaying
            fromIsPlaying = isPlaying
            return
        }
        if (isPlaying == targetIsPlaying) return
        startMorph(isPlaying)
    }

    /** 一拍形变的完整时序。被下一拍顶掉时抛 CancellationException，无需补救。 */
    suspend fun runMorph() {
        if (token == 0) return
        if (MeloXSettingsRuntime.reduceMotion) {
            scale.snapTo(1f)
            swapped = true
            return
        }
        try {
            if (rapid) {
                scale.animateTo(
                    targetValue = SCENE_MORPH_RAPID_VALLEY,
                    animationSpec = tween(
                        durationMillis = SCENE_MORPH_RAPID_DIP_MS,
                        easing = SCENE_MORPH_SHRINK_EASE,
                    ),
                )
                scale.animateTo(targetValue = 1f, animationSpec = SCENE_MORPH_RAPID_RECOVER)
            } else {
                scale.animateTo(
                    targetValue = SCENE_MORPH_VALLEY,
                    animationSpec = tween(
                        durationMillis = SCENE_MORPH_SHRINK_MS,
                        easing = SCENE_MORPH_SHRINK_EASE,
                    ),
                    block = {
                        if (!swapped && value <= SCENE_MORPH_SWAP_SCALE) swapped = true
                    },
                )
                swapped = true
                scale.animateTo(targetValue = 1f, animationSpec = SCENE_MORPH_REBOUND)
            }
        } catch (_: Throwable) {
            swapped = true
        }
    }
}

@Composable
internal fun rememberMeloXPlayPauseMorph(isPlaying: Boolean): MeloXPlayPauseMorphController {
    val controller = remember { MeloXPlayPauseMorphController() }
    LaunchedEffect(controller, isPlaying) { controller.onExternalIsPlaying(isPlaying) }
    LaunchedEffect(controller, controller.token) { controller.runMorph() }
    return controller
}

/**
 * 播放/暂停字形（带形变）。
 *
 * ⚠ 缩放**必须走绘制期画布变换**（`DrawScope.scale`），不能走 `graphicsLayer`：
 *   `graphicsLayer{ scaleX/scaleY }` 是把**已栅格化**的位图降采样，SF 字形
 *   `play.fill` / `pause.fill` 的倒角只有约 0.045 个字形宽，缩到谷底（0.22）时
 *   只剩 1~2px，再经双线性采样就被抹平成直角 ⇒ 「圆角比原来的数值小」。
 *   而画布矩阵会让字形按变换后的 device 尺寸**重新矢量光栅化**，圆角严格 = 设计值×scale。
 */
@Composable
internal fun MeloXPlayPauseMorphGlyph(
    morph: MeloXPlayPauseMorphController,
    color: Color,
    iconSize: Dp,
    /**
     * 播放三角的视觉重量补偿（只作用于 Play 字形）。
     *
     * ⚠ 迷你栏当前传 **1f** —— 因为两边已经用 [pauseCompensation] 一起放大了，
     *   再单独放大三角会让它重新偏大（2026-10-30 用户实测「反了，是竖条比较小」）。
     */
    playCompensation: Float = 1f,
    /**
     * 暂停竖条的视觉重量补偿（只作用于 Pause 字形）。
     *
     * ★ 2026-10-30（用户口径）：「**我要的是竖条放大，三角不变**」。
     *
     * ### 为什么需要它
     * `pause.fill` 的两根竖条在 SF 字体里本身**偏窄**（墨迹 0.5957 × 0.8076 em，
     * 而 `play.fill` 是 0.7290 × 0.8203 em）。放大前真机截图实测：
     * ```
     * play   墨迹 267 x 300 px   墨迹像素 46,796
     * pause  墨迹 170 x 227 px   墨迹像素 29,806   ← 只有 play 的 64%
     * ```
     * ⇒ 竖条看起来「小一圈」。把 Pause 也放大到与 Play 同一档（1.27）后，
     * 两者墨迹像素比 = 0.97（差 2.7%），视觉重量才真正匹配。
     *
     * ⚠ 与 [playCompensation] 是**两个独立旋钮**，不要同时给两者都设 >1
     *   （那会把三角重新顶大）。迷你栏现在的口径是：play = 1f、pause = 1.27。
     */
    pauseCompensation: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val showing = morph.displayIsPlaying
    val glyphScale = if (showing) pauseCompensation else playCompensation
    // ⚠ 图标字号必须**锁 dp**（`Dp.toSp()` = dp / fontScale）—— 见迷你栏
    //   `titleFontSize` 处的踩坑说明：写 `Float.toSp()` 会命中「把入参当 px」的重载，
    //   字号缩到 1/density。
    val boxSize = (iconSize.value * glyphScale).dp
    val fontSize = with(density) { boxSize.toSp() }
    Box(
        modifier = modifier.drawWithContent {
            val s = morph.scale.value
            if (s == 1f) drawContent() else scale(s) { this@drawWithContent.drawContent() }
        },
    ) {
        MeloXSymbolIcon(
            symbol = if (showing) MeloXSymbol.Pause else MeloXSymbol.Play,
            modifier = Modifier.size(boxSize),
            color = color,
            variant = MeloXSymbolVariant.Fill,
            iconSize = fontSize,
        )
    }
}

// ───────────────────────────── ② 传送带（上/下一首） ─────────────────────────────

/**
 * 「传送带」的一拍推进（Apple 原版时序见 `MeloXNowPlayingCoreControls` 里
 * `SceneTransportButton` 上方的逐帧取证注释：后三角平移一个步距 / 新三角从 0 长大 /
 * 前三角锚在自己的 apex 缩到 0 并淡出，三者由同一个欠阻尼弹簧驱动）。
 *
 * ⚠ 必须在 onClick()（= 换歌）**之前**触发：换歌会让上层重组，协程最多"被打断"，
 *   而不会"根本没开始"。
 */
internal class MeloXConveyorPulse internal constructor(
    private val scope: CoroutineScope,
) {
    /** 弹簧位置 0→1。**只在绘制期读**（组合期读会每帧重组整颗按钮）。 */
    val scroll = Animatable(0f)

    fun trigger() {
        if (MeloXSettingsRuntime.reduceMotion) return
        scope.launch {
            try {
                scroll.snapTo(0f)
                scroll.animateTo(1f, SCENE_TRANSPORT_SPRING)
                scroll.snapTo(0f)
            } catch (_: Throwable) {
                // 被下一次点击 / 重组取消：下一拍会 snapTo(0) 重新起手，无需补救。
            }
        }
    }
}

@Composable
internal fun rememberMeloXConveyorPulse(): MeloXConveyorPulse {
    val scope = rememberCoroutineScope()
    return remember(scope) { MeloXConveyorPulse(scope) }
}

/**
 * 传送带字形（`backward.fill` / `forward.fill` 的两个同向三角）。
 *
 * 播放页（`SceneTransportButton`）与迷你栏共用这一份；差别只在 [textSizeSp] 与 [color]
 * ⇒ 迷你栏 18~22sp 的墨迹与它自己原来的静态字形**逐像素同源**（都走
 * `rememberSfChevronSpec` 的同一套字体度量）。
 *
 * @param textSizeSp 与该按钮**静态字形**完全相同的字号（迷你栏传 `iconFontSize`，
 *   播放页传 34.sp）⇒ 静止态（sv=0）与原 `MeloXSymbolIcon` 渲染逐像素对齐。
 * @param scrollProvider 传送带进度（绘制期读，见 [MeloXConveyorPulse.scroll]）。
 */
@Composable
internal fun MeloXChevronConveyorGlyph(
    forward: Boolean,
    color: Color,
    textSizeSp: TextUnit,
    scrollProvider: () -> Float,
    fallbackSymbol: MeloXSymbol,
    fallbackSize: Dp,
    modifier: Modifier = Modifier,
) {
    val spec = rememberSfChevronSpec(
        kind = if (forward) SceneGlyphKind.Forward else SceneGlyphKind.Backward,
        textSizeSp = textSizeSp,
    )
    val halves = spec?.halves
    if (spec == null || halves == null) {
        // 字体加载失败 / 轮廓拆不出两个三角（理论上不会发生）⇒ 退回原版静态字形：
        // 宁可不动画，也不要崩。
        MeloXSymbolIcon(
            symbol = fallbackSymbol,
            modifier = modifier.size(fallbackSize),
            color = color,
            variant = MeloXSymbolVariant.Regular,
            iconSize = textSizeSp,
        )
        return
    }
    val inkW = spec.bounds.width().toFloat()
    val inkH = spec.bounds.height()
    val density = LocalDensity.current
    // 画布比墨迹外框大一圈（×SCENE_GLYPH_CANVAS_SCALE）：弹簧过冲时后三角会探出
    // 墨迹右缘约 0.12 pitch，不给富余就会被裁掉一截尖。
    val canvasW = with(density) { (inkW * SCENE_GLYPH_CANVAS_SCALE).toDp() }
    val canvasH = with(density) { (inkH * SCENE_GLYPH_CANVAS_SCALE).toDp() }
    val dir = if (forward) 1f else -1f
    Canvas(modifier = modifier.size(canvasW, canvasH)) {
        // ★ 绘制期读动画值（唯一正确的读法）：`scrollProvider()` 在 draw 阶段被登记为
        //   快照依赖 ⇒ 弹簧每帧变化只让这块 Canvas 重绘，不触发上层 composition。
        val sv = scrollProvider()
        val w = this.size.width
        val h = this.size.height
        val pitch = halves.pitch
        // 字形坐标系（pen 原点 / 基线）→ 画布坐标系。与 `SfGlyphIcon` 的居中公式一字不差
        // ⇒ 静止态与原来的 `drawText` 逐像素对齐。
        val originX = w * 0.5f - (spec.bounds.left + spec.bounds.right) * 0.5f
        val baseline = h * 0.5f - (spec.bounds.top + spec.bounds.bottom) * 0.5f
        val pivotY = (spec.bounds.top + spec.bounds.bottom) * 0.5f

        // next(»)：前=右三角、后=左三角、跳过方向=右；prev(«) 完全镜像。
        val rearPivot = if (forward) spec.bounds.left.toFloat() else spec.bounds.right.toFloat()
        val frontPivot = if (forward) spec.bounds.right.toFloat() else spec.bounds.left.toFloat()
        val rearHalf = if (forward) halves.left else halves.right
        val frontHalf = if (forward) halves.right else halves.left

        val shift = pitch * sv * dir              // ① 后三角位移（一个步距）
        val grow = sv.coerceAtLeast(0f)           // ② 新三角生长（与 ① 同一条曲线）
        // ③ 前三角退出进度：实测 sv≈0.30 时已缩到 0.19、sv≈0.55 时归零。
        val u = (sv / SCENE_FRONT_EXIT_SPAN).coerceIn(0f, 1f)
        val frontScale = (1f - u) * (1f - u)      // 二次收缩：起步快、收尾急
        val frontAlpha = kotlin.math.sqrt(1f - u) // 淡出比收缩慢一档 ⇒ "边缩边淡"

        clipRect(
            left = -SCENE_TRANSPORT_OVERFLOW,
            top = 0f,
            right = w + SCENE_TRANSPORT_OVERFLOW,
            bottom = h,
        ) {
            drawChevronHalf(
                half = rearHalf,
                originX = originX,
                baseline = baseline,
                dx = shift,
                color = color,
            )
            drawChevronHalf(
                half = rearHalf,
                originX = originX,
                baseline = baseline,
                scale = grow,
                pivotX = rearPivot,
                pivotY = pivotY,
                color = color,
            )
            drawChevronHalf(
                half = frontHalf,
                originX = originX,
                baseline = baseline,
                scale = frontScale,
                pivotX = frontPivot,
                pivotY = pivotY,
                alpha = frontAlpha,
                color = color,
            )
        }
    }
}

// ───────────── ③ 整栏点按反馈（照抄 kyant0 官方 Button 的触感）─────────────────

/**
 * 迷你栏的按压放大：**完全采用 kyant0 官方 `LiquidButton` 的按压模型**。
 *
 * 官方（`netease_module/kyant0/.../catalog/components/LiquidButton.kt` +
 * `.../catalog/utils/InteractiveHighlight.kt`）：
 *   ① 按下 → `pressProgress` 由 `spring(ζ=0.5, k=300)` 推向 1；
 *   ② 抬起 / 取消 → **同一条弹簧**推回 0；
 *   ③ `scale = 1 + 4dp / 高度 × pressProgress`（48dp 高 ⇒ 8.3%）；
 *   ④ 叠一层**高光**：整面 +8% 白（`BlendMode.Plus`）+ 以手指为圆心的
 *      +15% 径向辉光；手指移动时圆心跟着走（`tanh` 阻尼跟随）。
 *   ⇒ 触感的关键**不是曲线形状**，而是“进度由手指决定”：按住就撑着，抬手才收。
 *
 * 本项目已有的等价物（本文件复用，**未新造轮子**）：
 *   · `PublicInteractiveHighlight`（`ui/glass/publicdemo/`）= 官方那份的移植，
 *     弹簧 / 着色器 / 手势一字未改；
 *   · 迷你栏现在给 play / next **各持一份** `rememberMeloXLiquidInteraction()`：
 *     手势与高光都挂在按钮自身上（见下方 [MeloXMiniBarPress] 的长注释），
 *     本文件只负责把两份 `pressProgress` 折算成整栏缩放。
 *
 * ⚠ 幅度为什么不照抄 4dp/height：官方那颗按钮高 48dp、宽约 200dp，
 *   8.3% 只多出 4dp 高 / 16dp 宽；迷你栏是**满宽 336dp**，同样 8.3% 会让左右
 *   各外扩 **14dp**（直接顶出屏幕边距）。⇒ 按“绝对外扩量”折算：`0.032 ⇒ 单侧 5.4dp`。
 *   官方弹簧欠阻尼会过冲到 ~1.16 ⇒ 实际峰值 ≈ **1.037（单侧 6.2dp）**，
 *   仍落在迷你栏与底栏导航胶囊之间那道 ~10dp 的缝里。
 *   ⛔ 别再往上调：> 1.04 会读成“整条栏变胖 / 吃掉那道缝”。
 *
 * ⚠ 之前那几版全部废弃（tween 冲顶 + spring 收回 / 单峰 sin 包络 /
 *   120ms 前倾）—— 它们都是“点一下自动播一段动作”，**与按多久无关**：
 *     · 按得短 ⇒ 动作还在播，读成“过快了”（用户 2026-10-28 反馈）；
 *     · 按得长 ⇒ 早已回位、手指还按着，读成“没有触感”。
 *   官方模型没有这个矛盾：`pressProgress` 是**手指状态的函数**。
 *   ⛔ 不要再用 `tween` / 包络去复刻它，也不要去调 `MINI_BAR_PRESS_GROWTH`
 *     模拟“手感”—— 手感来源就是那条官方弹簧（ζ=0.5 的过冲 = 那点“duang”）。
 *
 * ════════════════════════════════════════════════════════════════════════════
 * 🔴 2026-10-28「这个按压到底能生效多少」的判定结论（三笔独立机制，逐笔判定）：
 *
 *   ① 整栏缩放（本文件的 `MINI_BAR_PRESS_GROWTH`，读 `pressProgress`）
 *        · 毛玻璃底栏模式：✅ 生效。它挂在**外壳的 `graphicsLayer`** 上，玻璃面
 *          （`meloXLiquidBottomBar`）只是被放大的子内容；毛玻璃模式换的只是玻璃的
 *          画法（`blur(8dp)` 取代 vibrancy+lens），与缩放无关。
 *          ⚠ 面板自身那条官方「按压膨胀」（`pressProgress` 传进材质，+16dp/width·p）
 *            在两种模式下都**显式传 0f** —— 那是刻意的：材质里 highlight/shadow/
 *            innerShadow 的 alpha 在**组合期**求值，喂逐帧值会让上千行的
 *            `MeloXIOSMiniPlayer` 每帧重组。别把「不生效」误判成 bug。
 *        · Android 13 以下：✅ 生效（纯 Compose 变换，不依赖 RenderEffect/AGSL）。
 *
 *   ② 按钮整面 +8% 白（`PublicInteractiveHighlight`，`BlendMode.Plus`）
 *        · 两种模式 / 所有版本：✅ 生效（纯 `drawRect`）。
 *
 *   ③ 以手指为圆心的 +15% 径向辉光
 *        · 毛玻璃模式：✅ 生效（与背板材质无关，画在按钮自己身上）。
 *        · Android 13（API 33）以上：✅ 走 `RuntimeShader`（上游原路径，逐像素不变）。
 *        · Android 13 以下：✅ **同样生效** —— 原实现只有 AGSL 一条路，没有 shader 时
 *          上游 fallback 会把 ③ 折进 ②（`panelGlow + focusGlow` 一块矩形）⇒
 *          「整颗 30dp 圆钮均匀发白」，既不跟手也不是光斑。
 *          现在改用 `Brush.radialGradient` **复刻同一支 shader**（圆心=手指、
 *          半径=1.5×短边、峰值=`focusGlowAlpha`、Plus 混合，stop 表就是
 *          `smoothstep(radius, radius*0.5, dist)` 的采样）⇒ 低版本照样跟随手指移动。
 *          ⇒ 结论：**三笔在两种模式 × 所有版本下全部生效**，没有需要禁用的一笔。
 *        ⚠ `gestureModifier` **必须照挂**：它是 ① 与 ③ 的唯一数据源（手指坐标 +
 *          `pressProgress`），撤了整栏不弹、光斑也不会出现。
 *        ⚠ 光斑只在 `pressProgress > 0` 时绘制 ⇒ 依然**只在按这两颗按钮时触发**；
 *          横滑切歌走 Main pass、按压走 Initial pass 且不消费，互不干扰。
 * ════════════════════════════════════════════════════════════════════════════
 */
private const val MINI_BAR_PRESS_GROWTH = 0.032f

/**
 * 整栏缩放：**只认 play / next 两颗按钮的按压**，与横滑切歌彻底解耦。
 *
 * ⚠⚠ 2026-10-28（用户：左右滑动歌曲时迷你栏会**又大又小**）—— 根因是**结构**，不是参数：
 *   上一版把按压手势挂在**整条栏**上（官方 surface 语义），又用「一拖就收平」把
 *   跟手位移折算成 `(1 − dragProgress)` 去压制它。两条叠起来在物理上是矛盾的：
 *     · 按下当帧 `pressProgress` 已经开始爬（弹簧），整栏先胀到 1.03x；
 *     · 手指一动（越过 touch slop ≈ 8dp，只要 30~50ms）`dragProgress` 就往上走 ⇒
 *       24dp 内把缩放压回 1.00。
 *   ⇒ 每一次横滑都是「胀一下 → 立刻瘪回去」，连滑几下就是**又大又小**；
 *     更糟的是拖回起点 / 松手后 `dragProgress` 回落，鼓包还会**再胀一次**。
 *   ⇒ 任何「按下就胀、拖动就压」的方案都躲不掉这一下，只能从**区域**上分开：
 *     横滑的手柄是信息区，按压反馈应该只属于那两颗**离散按钮**。
 *   现在：`gestureModifier` 挂在 `MiniVectorButton` 上（见 `MeloXIOSMiniPlayer`）⇒
 *   滑歌曲时 `pressProgress` 恒 0，整栏纹丝不动；按按钮时照旧整栏轻胀。
 *
 * ⚠ 取 `max` 而不是求和：两颗按钮共用同一份整栏缩放，各持一份
 *   `PublicInteractiveHighlight`（高光圆心必须是**各自按钮**的局部坐标）；
 *   万一双指同时按，求和会变成 2 倍幅度。
 */
internal class MeloXMiniBarPress internal constructor(
    private val play: MeloXLiquidInteraction,
    private val next: MeloXLiquidInteraction,
) {
    /**
     * 整栏缩放。**只在 `graphicsLayer{}` 里调** —— 内部读
     * `highlight.pressProgress`（`Animatable.value`），依赖登记到**绘制阶段**
     * ⇒ 按压全程零重组。
     *
     * @param expansionProgress 播放页展开进度（0 = 迷你栏静止）。
     *   morph 期归 1：外壳几何由 `sharedBounds` 逐帧重算，此时再叠一个
     *   3% 的 `graphicsLayer` 缩放会与 morph 打架（观感：迷你栏突然胖一圈再变形）。
     */
    fun scale(expansionProgress: Float): Float {
        val press = maxOf(
            play.highlight.pressProgress,
            next.highlight.pressProgress,
        )
        if (press <= 0f) return 1f
        val morphGuard = (1f - expansionProgress).coerceIn(0f, 1f)
        return 1f + MINI_BAR_PRESS_GROWTH * press * morphGuard
    }
}

@Composable
internal fun rememberMeloXMiniBarPress(
    play: MeloXLiquidInteraction,
    next: MeloXLiquidInteraction,
): MeloXMiniBarPress = remember(play, next) { MeloXMiniBarPress(play, next) }
