package com.lladlam.melox.ui.glass

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.lladlam.melox.ui.MeloXUiMotionRuntime
import com.lladlam.melox.ui.theme.DarkColors
import com.lladlam.melox.ui.theme.LightColors
import kotlinx.coroutines.delay
import kotlin.math.pow

/**
 * 底部 chrome（nav 胶囊 / 搜索键 / 播放栏）的**暗度**控制器。
 *
 * `d = 0` 亮式、`d = 1` 暗式。Apple 对 navbars / tabbars 这一档的定义就是
 * 「按背后内容在明暗之间整体翻转」（WWDC25 Session 219 @15:16），所以这里是
 * 一个**离散的目标值**，平滑交给过渡动画做，而不是逐帧连续微调。
 *
 * ### 为什么一定要由单一标量驱动
 * 涂层、高光、阴影、前景色全部 `lerp(亮端点, 暗端点, d)`。任何一层自己再去读
 * `isMeloXDarkTheme()`，都会在 d 处于中间态时错位 ⇒ 读起来是涂抹不是切换。
 *
 * ### 采样源：底栏自己的背板图层（2026-10-19 换源）
 *
 * 数据源现在是 **`meloXLiquidBottomBar` 在 `onDrawBackdrop` 里顺手录下的背板内容**
 * （`GraphicsLayer`，等比缩到 ~96px 宽），不再是窗口 `PixelCopy`。两个收益都是结构性的：
 *
 * ① **天然不含自己的玻璃**。录的是 `drawBackdrop()` 的**原始内容** —— `vibrancy` /
 *    `blur` / `lens` 全挂在 kyant0 自己那层的 `renderEffect` 上，涂层与高光走
 *    `onDrawSurface` / `Highlight`，两者都不进这份录制。⇒ 「采到的就是底栏背后是什么」
 *    在**结构上**成立，`a = 0` 不需要证明。
 * ② **不再阻塞渲染管线**。`PixelCopy` 会在 RenderThread 上同步等**当前窗口帧**的 GPU
 *    工作做完（真机 trace 实测 `copySurfaceInto` 单次 3~16ms ⇒ 120ms 周期下每 7~8 帧
 *    必掉一帧，用户报「任何动画都一顿一顿」）。`toImageBitmap()` 走独立
 *    `HardwareRenderer` + 自有 ImageReader surface，只等它那 96px 的活，不排进主窗口
 *    队列。⇒ 上一轮为躲 PixelCopy 加的 `OnPreDrawListener` 忙闲门控、
 *    `DRAW_QUIET_WINDOW_MS` 跳过、以及「动画期不采样」三条补丁**全部删除**
 *    （它们有副作用：迷你栏流光/进度条一动画，采样就几乎永不发生，色调被冻住）。
 *
 * ⚠ 因为采样不再经过窗口合成，下面这段 2026-09 的自反馈推导**只作历史存档**，
 *   别再照着它去改数据源：那套「只采底栏上沿往上 8dp 带子」的绕法已经不需要了
 *   （band 在底栏外面，反而容易被底栏上方的内容干扰）。
 *
 * ### 历史存档：为什么当时必须避开自己的玻璃（PixelCopy 时代）
 *
 * 旧实现抓**底栏自己的矩形**。PixelCopy 抓的是窗口合成结果，**里面已经混进我们自己的
 * 涂层**，于是判据只能靠
 *
 *     captured = content·(1 − A) + coat·A        ⇒      T = 0.5(1 − A) + coat·A
 *
 * 去预测「content 是中灰时应该采到多少」。这里有个**结构性**的坑：判据以为我们的涂层
 * 往 captured 里注入了 `A·coat`（浅式高达 `+0.578` 线性光），一旦真实注入量不是这个数，
 * 两个方向的阈值就**互相不一致**。令真实有效遮盖度为 `a`、content 亮度为 `c`：
 *
 *     亮式 → 转暗：  (1−a)·c + a        < 0.7290
 *     暗式 → 转亮：  (1−a)·c + 0.006a   > 0.2745
 *
 * 两式**同时成立**（= 背景纹丝不动却无限来回翻）⇔ **`a < 0.457`**。而 ① 采样矩形里
 * 胶囊↔搜索键之间根本没有玻璃，② `vibrancy/lens/highlight` 都不是仿射 —— 面积加权后
 * 的真实 `a` 远低于名义值，稳落该区间 ⇒ 自反馈环路，调死区救不回来。
 * 当时改成「只采底栏上沿往上 8dp 的背景带」把 `a` 变成真的 0 才压住了它。
 * ⇒ 换源后这个约束由数据源结构保证，**不再靠选区域来保证**。
 */
internal class BottomBarToneState internal constructor() {

    /**
     * 采样源图层（2026-10-19）。由 `MeloXApp` 建、经 `LocalBottomBarLuminanceLayer`
     * 传进导航胶囊的 `meloXLiquidBottomBar`，**绘制期**由材质侧 `record` 写入。
     *
     * 采样循环只读它，不再持有任何窗口/位图/布局坐标。
     */
    internal var luminanceLayer: GraphicsLayer? = null

    /** 目标暗度。`NaN` = 还没决出来，退化为系统主题。 */
    internal var rawTarget: Float = Float.NaN

    /** 对外消费的动画目标（mutableStateOf，驱动 recomposition）。 */
    internal var target by mutableFloatStateOf(Float.NaN)
        private set


    /** 已经释放 —— 采样循环看到它就必须退出。 */
    internal var released: Boolean = false

    internal fun publishTarget(v: Float) {
        target = v
        rawTarget = v
    }

    internal fun release() {
        released = true
        luminanceLayer = null
    }
}

/**
 * 采样间隔（**活跃档**）：底栏色调尚未稳定（刚启动 / 刚翻过档 / 检测到背景在变）时用。
 *
 * ⚠⚠ 2026-11-07 对齐上游 0.7.2 的速度语义（用户确认上游"速度正常"）：
 * 上游是**单档 `SAMPLE_INTERVAL_MS = 120ms`**，识别跟手；本地原本双档 400ms/500ms 降频
 * 把识别拖慢到 ~1s 肉眼可见迟钝。⇒ 活跃档压回 120ms（= 上游节奏）。
 *
 * 本路径是 `GraphicsLayer.toImageBitmap()`（约 5KB、独立 GPU 上下文、不排主窗口帧队列），
 * 同频比上游 PixelCopy 更轻快；一次 readback 最坏 ~90ms < 120ms 间隔，不会叠拍。
 * 之前「封面卡顿」是 8 次/秒 readback 在特定设备测出，上游 120ms PixelCopy 能正常，
 * 本路径同频 readback 更省，应无回归；若有卡顿再单独降稳态档。
 */
private const val SAMPLE_INTERVAL_ACTIVE_MS = 120L

/**
 * 采样间隔（**稳态档**）：色调已稳定、背景无明显变化时用。
 *
 * ⚠ 2026-11-07 对齐上游：稳态也用 120ms（上游单档 120ms）。
 * 底栏着色跟随背景，背景随时可能变化，稳态降频会让识别迟钝（用户报障）。
 * 为留一点防抢帧余量，稳态评论保留独立档位，但值同上游节奏 120ms。
 */
private const val SAMPLE_INTERVAL_IDLE_MS = 120L

/**
 * 采样图层的目标宽度（px）。
 *
 * `recordLuminanceSource` 会按这个宽度把底栏背板**等比缩小**后录进 `GraphicsLayer`，
 * 于是 `toImageBitmap()` 读回的数据量只有 96×~14 ≈ 5KB（原来 PixelCopy 是 32×8，
 * 但它站在整帧窗口截图上）。96 宽在这个高度上仍有近两千个采样点，
 * 均值足够稳；再小就开始受「一个图标/一个字的明暗」影响。
 */
private const val LUMINANCE_SAMPLE_WIDTH = 96

/**
 * 把底栏背板**等比缩小**录进采样图层。
 *
 * 在 `meloXLiquidBottomBar` 的 `onDrawBackdrop` 里调用 —— 那一笔本来就要把背板画出来，
 * 这里只是**再记一份缩小版**：
 *
 * - **CPU**：`GraphicsLayer.record` 只往 `Picture`/`RenderNode` 里记绘制指令，不栅格化；
 * - **GPU**：多一次小尺寸 `drawLayer`（96px 宽），由 RenderThread 异步消费，
 *   不像 `PixelCopy` 那样把主窗口当前帧的 GPU 完成**同步等**出来。
 *
 * ⚠ `size` 必须显式给**缩小后**的尺寸，并配 `scale()` 把内容压进去：只写 `scale()`
 *   不换 `size` 的话，图层仍是原始尺寸，`toImageBitmap()` 照样读回一整张
 *   1440×~200 —— 白忙一场。
 *
 * @param sourceSize 背板内容尺寸（= 底栏自己的尺寸，调用处是 `DrawScope.size`）。
 * @param densityScope 调用处的 `DrawScope` **本身**（`DrawScope : Density`）。
 *   ⚠ 别传 `DrawScope.density` —— 那个是 `Float`（`Density` 接口的标量属性），
 *   而 `GraphicsLayer.record` 要的是 `Density` 对象。类型对不上，编译直接报
 *   `actual type is 'Float', but 'Density' was expected`。
 */
internal fun GraphicsLayer.recordLuminanceSource(
    densityScope: Density,
    layoutDirection: LayoutDirection,
    sourceSize: Size,
    block: DrawScope.() -> Unit,
) {
    if (sourceSize.width < 1f || sourceSize.height < 1f) return
    val sourceWidth = sourceSize.width.toInt().coerceAtLeast(1)
    val targetWidth = LUMINANCE_SAMPLE_WIDTH.coerceAtMost(sourceWidth)
    // 高度按原比例走；底栏这种宽扁区域会得到 ~14px。至少 1px，避免除零/空位图。
    val targetHeight =
        (sourceSize.height * targetWidth / sourceSize.width).toInt().coerceAtLeast(1)
    record(
        density = densityScope,
        layoutDirection = layoutDirection,
        size = IntSize(targetWidth, targetHeight),
    ) {
        scale(
            scaleX = targetWidth.toFloat() / sourceSize.width,
            scaleY = targetHeight.toFloat() / sourceSize.height,
            pivot = Offset.Zero,
        ) {
            block()
        }
    }
}

/**
 * 切换判据的**中点**，单位是 CIELAB L*（感知亮度，0–100），不是线性光亮度 Y。
 *
 * ⚠ **不要用 Y 当中灰。** `meanLinearLuminance()` 返回的 Y 是物理量，
 * 它的感知等距点在 L* 上，而 **Y = 0.5 对应 L* ≈ 72** —— 也就是肉眼明显偏亮
 * 的灰。拿 Y 0.44 当门槛，等于把「该不该切暗式」判给了一片本来挺亮的内容，
 * 于是「不该切黑的都切了」（2026-09-25 用户反馈）。
 *
 * L* 才是按感知等距设计的标度：**L* = 50 才是字面意义的中灰**。
 * 想更宽松（更晚才切暗式）就往下调，比如 42；想更积极就往上。
 */
private const val TONE_FLIP_MID_LSTAR = 50.0f

/**
 * 围绕 [TONE_FLIP_MID_LSTAR] 的双侧迟滞宽度（L* 单位）。
 *
 * 现在是净采样（不含自己的玻璃），所以门槛就是 content 自己的感知亮度，
 * 不需要任何仿射换算：
 * - 当前亮式 ⇒ 要 `L* < 50 − Δ` 才转暗
 * - 当前暗式 ⇒ 要 `L* > 50 + Δ` 才转亮
 *
 * ⇒ 落在中间 2Δ 窗口里的内容**保持现状不变**，而背景静止时候选值是常数
 *  ⇒ **数学上不可能来回翻**（第一版的自反馈环路正是这么来的）。
 */
private const val TONE_FLIP_DEADBAND_LSTAR = 5.0f

/**
 * 连续几次"同侧越界"才真的切。
 *
 * 采样带紧贴内容底部，进度条、封面旋转这类**局部动画**偶尔会把均值推出边界一帧；
 * 要求连续命中可以过滤掉这种单帧噪声，代价只是 ~120ms 的响应延迟。
 */
private const val TONE_CONFIRM_COUNT = 2

/**
 * 两次翻转之间的最短驻留。
 *
 * 既覆盖 [ToneTransition] 本身的收敛时间，也挡掉快速滑过时的一连串翻转。
 * ⚠ 冷却期间**不要**清零确认计数，否则每次都从头数，滚动时会明显迟钝。
 *
 * ⚠ 2026-11-07 回归上游 0.7.2 原值 400ms：用户确认上游"速度正常"，
 * 识别快的关键是采样 120ms（已对齐），过渡/冷却不必激进微调，采信上游原版手感。
 */
private const val TONE_SWITCH_COOLDOWN_MS = 400L

/**
 * 过渡曲线。**刻意用 critically damped（dampingRatio = 1）**：这是在换材质调子，
 * 不是跟手手势 —— 过冲会被读成「闪一下」，不是在弹。
 *
 * ⚠ 2026-11-07 回归上游 0.7.2 原值 stiffness = 180：深色/浅色切换的收敛动画
 * 保持上游原版（临界阻尼平滑过渡），与 120ms 采样配合即上游手感。
 */
private val ToneTransition = spring<Float>(dampingRatio = 1f, stiffness = 180f)

@Composable
internal fun rememberBottomBarToneState(
    systemDark: Boolean,
    /**
     * 底栏是否**被全屏播放页完全遮住**（`fullPlayerVisible`）。
     *
     * ⚠⚠⚠ 2026-10-06（用户报障）：「播放页的**专辑流光取色盖住底栏**，没有了液态效果」。
     *   根因：本采样器是**看底栏背后录到的背板内容**来判定明暗的，而播放页打开时
     *   那里面只有**播放页的专辑流光像素** —— 它会被当成「底栏背后真实内容」，把 tone 翻到
     *   错误档位（深色流光 ⇒ 底栏整体翻成暗式 ⇒ 深色底栏叠在深色流光上，
     *   折射完全看不见 = 用户说的「没有了液态效果」）。
     *   而翻转本身有**去抖计数 + 400ms 冷却**（[TONE_SWITCH_COOLDOWN_MS]），
     *   关掉播放页后要好几拍才恢复 ⇒ 「有时候」看到。
     *   ⇒ 播放页期间**暂停采样、保持当前档位**（不清零计数、不 publish）；
     *     播放页一关立刻按真实内容重新采样。
     */
    paused: Boolean = false,
    /**
     * 深色模式下**锁死暗色底栏**：底栏不再根据背后内容自适应翻成浅色。
     *
     * ⚠ 只锁深色：仅当 `systemDark && lockDarkInDarkMode` 同时成立时，把 tone 钉死在
     *   `d = 1`（暗式）并**跳过采样**；浅色模式下自适应（暗背景→暗底栏）照常。
     *   用 `rememberUpdatedState` 而非 key：开关切换不该重启采样循环（会丢失 `decided`/
     *   冷却状态，反而制造一次额外翻转）。开关一开，下一轮循环即钉死暗式，无需重建协程。
     */
    lockDarkInDarkMode: Boolean = false,
): BottomBarToneState {
    val state = remember { BottomBarToneState() }
    // ⚠ 用 `rememberUpdatedState` 而非 key：暂停开关不该重启整个采样循环
    //   （重启会丢掉 `decided`/冷却状态，反而制造一次额外翻转）。
    val pausedState = rememberUpdatedState(paused)
    // 同 `pausedState`：锁开关切换时只更新值，不重启循环。
    val lockDarkState = rememberUpdatedState(lockDarkInDarkMode)

    LaunchedEffect(state, systemDark) {
        // 首帧先落到系统主题，避免还没采样就翻一下。
        val fallback = if (systemDark) 1f else 0f
        state.publishTarget(fallback)

        var decided = Float.NaN
        var pending = Float.NaN
        var pendingCount = 0
        var lastSwitchAt = 0L

        while (true) {
            // ⚠ 自适应降频（2026-10-19 修「采样上下文抢帧导致动画卡顿」）：
            //   色调已稳定（已 `decided` 且无 pending 翻转）⇒ 降到 1 次/秒稳态档，
            //   把 `toImageBitmap()` 每拍重建 GPU 上下文的开销压到最低；
            //   初始（decided=NaN）或正在确认翻转 ⇒ 用活跃档快速跟上背景变化。
            val stable = !decided.isNaN() && pendingCount == 0
            delay(if (stable) SAMPLE_INTERVAL_IDLE_MS else SAMPLE_INTERVAL_ACTIVE_MS)
            if (state.released) break
            // ⚠ 深色模式「保持暗色底栏」开关：只锁深色（浅色模式自适应照常）。
            //   锁定时把 tone 钉死在 d=1（暗式）并**跳过采样**，
            //   背后亮内容（如浅色封面）不再把下面的底栏翻成浅色。
            //   `decided` 记为 1f，开关关闭后下一轮自然回到下方采样逻辑；
            //   若背后确为亮内容会按正常去抖/冷却翻回浅式，不会卡死。
            if (lockDarkState.value && systemDark) {
                if (state.target != 1f) state.publishTarget(1f)
                decided = 1f
                pending = Float.NaN
                pendingCount = 0
                continue
            }
            // ⚠⚠ 播放页完全遮住底栏时**跳过采样**（理由见 `paused` 参数说明）。
            if (pausedState.value) continue
            // ★ 2026-10-23：转屏动画期间也跳过采样。
            //   真机取证（iQOO 15 / 120Hz，atrace + gfxinfo）：转屏那 300~400ms 里
            //   `RenderProxy#copyHWBitmapInto readback` 出现 15 次/次转屏，p50 8.6ms、
            //   最坏 90ms —— 每一次都要新建一套 HardwareRenderer + ImageReader + GPU 上下文
            //   （见 `sampleLuminance` 的注释），正好砸在本就只剩 8.33ms 的帧预算上，
            //   表现为「封面飞行途中顿一下」。同一次转屏 `Janky 38/628`、`p99 57ms`。
            //   而转屏期底栏要么被播放页盖住、要么整块在旋转 —— 色调晚 300ms 更新一次
            //   是**不可感知**的（采样本来就是 400ms/1000ms 一拍）。
            //   ⚠ 用普通字段而非 key：不能在转屏时重启采样循环（会丢 `decided`/冷却状态）。
            if (MeloXUiMotionRuntime.orientationTransition) continue
            // ⚠ 还没录过（胶囊未挂载 / 首帧尚未绘制）⇒ 这一拍没有数据。
            //   `GraphicsLayer.size` 在第一次 `record` 之前是 0，用它当就绪判据，
            //   比「等某个 flag」准：录制本身就是绘制期的事实。
            val lum = state.luminanceLayer?.let { layer ->
                if (layer.size.width <= 0 || layer.size.height <= 0) null
                else sampleLuminance(layer)
            } ?: continue

            // Y 是物理亮度、不是感知亮度，必须先换到 L* 上再比阈值（见
            // [TONE_FLIP_MID_LSTAR] 的注释）。
            val lstar = yToLstar(lum)


            val current = if (decided.isNaN()) fallback else decided
            val want = if (current > 0.5f) {
                // 现在暗式 ⇒ 背后要明显亮于感知中灰才回亮式
                if (lstar > TONE_FLIP_MID_LSTAR + TONE_FLIP_DEADBAND_LSTAR) 0f else 1f
            } else {
                // 现在亮式 ⇒ 背后要明显暗于感知中灰才进暗式
                if (lstar < TONE_FLIP_MID_LSTAR - TONE_FLIP_DEADBAND_LSTAR) 1f else 0f
            }

            if (want == current) {
                // ⚠ 这里必须把 `decided` 落定一次（2026-10-19 修）。
                //   原先只 `continue`，而 `decided` 初值是 NaN ⇒ 下面 `stable` 用的
                //   `!decided.isNaN()` 永远为假 ⇒ 色调一直稳定也永远进不了稳态档，
                //   实测采样间隔全程 0.406s（应该是 1.0s）。落定后第一拍仍走活跃档，
                //   第二拍起才切稳态，冷启动的响应速度不受影响。
                if (decided.isNaN()) decided = want
                pending = Float.NaN
                pendingCount = 0
                continue
            }
            if (pending != want) {
                pending = want
                pendingCount = 1
            } else {
                pendingCount++
            }
            if (pendingCount < TONE_CONFIRM_COUNT) continue

            val now = SystemClock.uptimeMillis()
            if (now - lastSwitchAt < TONE_SWITCH_COOLDOWN_MS) continue

            decided = want
            pendingCount = 0
            lastSwitchAt = now
            state.publishTarget(want)
        }
    }

    return state
}

/**
 * 用「插值后的配色」包住底部 chrome 这一棵子树。
 *
 * ⚠ **typography / shapes 必须从外层 MaterialTheme 原样传下来** —— `MaterialTheme()`
 *   是按参数重构整套 theme，不传就退回 Compose 默认值，兰亭 Pro 字体会丢。
 */
@Composable
internal fun BottomBarToneTheme(
    darkness: Float,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = lerpColorScheme(LightColors, DarkColors, darkness),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

/**
 * 读取当前暗度；无论采样是否可用都返回一个稳定值。
 *
 * @param systemDark 系统主题（打底值）。用户口径：「默认根据系统，然后同时真实采样背后像素」。
 */
@Composable
internal fun BottomBarToneState.darkness(systemDark: Boolean): Float {
    val fallback = if (systemDark) 1f else 0f
    val targetValue = if (target.isNaN()) fallback else target
    return animateFloatAsState(
        targetValue = targetValue,
        animationSpec = ToneTransition,
        label = "melox-bottom-bar-tone",
    ).value
}


/**
 * 把采样图层读回来算**线性光**平均亮度；拿不到数据返回 null。
 *
 * 两条成本线，都跟旧的 PixelCopy 路径不同：
 * - `toImageBitmap()` 走独立的 `HardwareRenderer` + 自有 ImageReader surface，
 *   只等这一小块（96×~14）的 GPU 完成，**不排进主窗口的帧队列**；
 * - 读回量 ≈ 5KB（原来 PixelCopy 是 32×8，但它要等整帧窗口合成结果）。
 *
 * ⚠ 任何异常都必须退化成 null，让上层 `?: continue` 跳过这一拍 —— 采样只是
 *   「锦上添花」，不能因为图层还没准备好/GPU 上下文被回收就把 App 打崩。
 *   已知会抛的：`IllegalStateException`（layer 未 attach）、
 *   `IllegalArgumentException`（size 非法）、个别 ROM 的 `SecurityException`。
 *   ⇒ 抓 `Throwable`，但 `CancellationException` 必须放行（否则破坏协程取消语义）。
 */
private suspend fun sampleLuminance(layer: GraphicsLayer): Float? {
    val image = try {
        layer.toImageBitmap()
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        return null
    }
    val n = image.width * image.height
    if (n <= 0) return null
    val px = IntArray(n)
    image.readPixels(px)
    return meanLinearLuminance(px)
}

/**
 * sRGB 像素 → 线性光 → 相对亮度，按 **alpha 加权**取均值。
 *
 * ⚠⚠ **必须按 alpha 加权，不能直接 `sum / px.size`**（2026-10-19 实证坑）：
 *   kyant0 的 `DrawBackdropNode` 会按 `effectScope.padding`（模糊/折射留边）把图层
 *   撑大 `padding*2`，而背板内容只画在**中间那块** —— 录出来的缩略图四周是**透明**
 *   （argb = 0x00000000）。直接平均等于把一圈纯黑算进去 ⇒ 均值被系统性压低
 *   ⇒ 底栏会比真实背景更早翻成暗式。按 alpha 加权后透明边权重为 0，自动排除。
 *
 * @return `null` = 整张图全透明（首帧 / 图层还没画内容），调用方跳过这一拍，
 *         **不要**当成「亮度 0」，否则会立刻翻到暗式。
 */
private fun meanLinearLuminance(px: IntArray): Float? {
    var sum = 0f
    var weight = 0f
    for (c in px) {
        val a = ((c ushr 24) and 0xFF) / 255f
        if (a <= 0f) continue
        // 位图是预乘 alpha 的话此处会偏暗，但 `GraphicsLayer.toImageBitmap()`
        // 给的是非预乘 ARGB_8888（Compose 的 ImageBitmap 口径），直接用即可。
        val r = ((c shr 16) and 0xFF) / 255f
        val g = ((c shr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        sum += a * (
            0.2126f * srgbToLinear(r) +
                0.7152f * srgbToLinear(g) +
                0.0722f * srgbToLinear(b)
            )
        weight += a
    }
    return if (weight > 0.0001f) sum / weight else null
}

private fun srgbToLinear(v: Float): Float =
    if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)

/**
 * 线性光相对亮度 Y → CIELAB L*（感知亮度，D65 白点，0–100）。
 *
 * 关键性质：**L* 是感知等距的** —— 相邻两点看起来的明暗差距一样大。Y 不是，
 * 它把绝大部分量程压在 0–0.2 的暗部，所以 Y 空间的「中点」在人眼看来是一片
 * 明显偏亮的灰（Y 0.5 ↔ L* 72）。阈值必须放在 L* 上才有直观意义。
 *
 * 分段点在 `216/24389 ≈ 0.008856`，两段在那里严格连续（L* = 8.0），
 * 不会出现跳变把死区顶歪。
 */
private fun yToLstar(y: Float): Float {
    val yn = y.coerceIn(0f, 1f)
    val eps = 216f / 24389f
    return if (yn > eps) {
        116f * yn.pow(1f / 3f) - 16f
    } else {
        // 与上式在 eps 处相切：116 * (841/108) * Y
        116f * 841f / 108f * yn
    }
}

/**
 * 在**两套真实 Material 配色**之间插值 —— 这就是「明暗两种模式的底栏互相切换」的落地方式。
 *
 * `d = 0` 就是货真价实渲染出来的「亮模式底栏」，`d = 1` 就是「暗模式底栏」，
 * 中间态则是整棵子树里所有读 `MaterialTheme.colorScheme.*` 的组件**自动**一起过渡。
 * ⚠ 因此**不要**再在底栏里手写字形色之类的近似端点 —— scheme 一动它们就跟着动了。
 *
 * ### 为什么用 `copy` 而不是构造新的
 * `ColorScheme` 字段近 50 个且版本间会增减。用 `light.copy(字段 = ...)` 的写法：
 * - 只写关心的字段，**未列出的字段自动取 light 的值**（走到 d=1 时它们不插值，也不炸）；
 * - 字段名写错是**编译期错误**，不会静默失效。
 *
 * ### 成本
 * `Color` 是 inline value class（一个 ULong），所以这里每帧只多分配 **1 个 ColorScheme 对象**
 * 加几十次 lerp —— 全也不是 hot loop 的重点。
 */
internal fun lerpColorScheme(light: ColorScheme, dark: ColorScheme, d: Float): ColorScheme =
    light.copy(
        primary = lerp(light.primary, dark.primary, d),
        onPrimary = lerp(light.onPrimary, dark.onPrimary, d),
        primaryContainer = lerp(light.primaryContainer, dark.primaryContainer, d),
        onPrimaryContainer = lerp(light.onPrimaryContainer, dark.onPrimaryContainer, d),
        secondary = lerp(light.secondary, dark.secondary, d),
        onSecondary = lerp(light.onSecondary, dark.onSecondary, d),
        secondaryContainer = lerp(light.secondaryContainer, dark.secondaryContainer, d),
        onSecondaryContainer = lerp(light.onSecondaryContainer, dark.onSecondaryContainer, d),
        tertiary = lerp(light.tertiary, dark.tertiary, d),
        onTertiary = lerp(light.onTertiary, dark.onTertiary, d),
        tertiaryContainer = lerp(light.tertiaryContainer, dark.tertiaryContainer, d),
        onTertiaryContainer = lerp(light.onTertiaryContainer, dark.onTertiaryContainer, d),
        background = lerp(light.background, dark.background, d),
        onBackground = lerp(light.onBackground, dark.onBackground, d),
        surface = lerp(light.surface, dark.surface, d),
        onSurface = lerp(light.onSurface, dark.onSurface, d),
        surfaceVariant = lerp(light.surfaceVariant, dark.surfaceVariant, d),
        onSurfaceVariant = lerp(light.onSurfaceVariant, dark.onSurfaceVariant, d),
        surfaceTint = lerp(light.surfaceTint, dark.surfaceTint, d),
        inverseSurface = lerp(light.inverseSurface, dark.inverseSurface, d),
        inverseOnSurface = lerp(light.inverseOnSurface, dark.inverseOnSurface, d),
        inversePrimary = lerp(light.inversePrimary, dark.inversePrimary, d),
        error = lerp(light.error, dark.error, d),
        onError = lerp(light.onError, dark.onError, d),
        errorContainer = lerp(light.errorContainer, dark.errorContainer, d),
        onErrorContainer = lerp(light.onErrorContainer, dark.onErrorContainer, d),
        outline = lerp(light.outline, dark.outline, d),
        outlineVariant = lerp(light.outlineVariant, dark.outlineVariant, d),
        scrim = lerp(light.scrim, dark.scrim, d),
    )

