package com.lladlam.melox.ui.player

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.lladlam.melox.BuildConfig
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * 「全屏播放页外壳**已经把底部 chrome 完全、不透明地盖住**」的几何判定
 * （2026-10-26，打开动画性能 B 方案）。
 *
 * ── 为什么需要它 ────────────────────────────────────────────────────────────
 * 真机 atrace 归因（`memory/2026-10-26.md` 21:55 段）：转场期每帧挂着 ~14 个液态玻璃
 * 图层（展开落定后 2.0、关掉玻璃 0.0）。多出来的那批里，底栏 chrome（nav 胶囊 + 搜索键
 * + L2 捕获层 + 水珠）占了绝大多数 —— 而**它们此刻正被播放页外壳整块压在下面**，
 * 一个像素都看不见，却仍在每帧：整屏采样背板纹理（`drawBackdrop` 要把整张 1440×3168
 * 的录制纹理重绘进节点）+ 跑 blur/lens 的 AGSL。
 *
 * 另外 `bottomChromeBackdrop` 的录制节点上挂着 `drawBehind { recedeFraction() }`，
 * 转场期每帧强制失效 ⇒ 每帧整屏 `recordLayer` + 18.25MB 纹理上传。它的内容此时同样
 * 被外壳盖住，纯浪费。
 *
 * ── 判据为什么必须「几何」而不是「进度阈值」──────────────────────────────────
 * 外壳是 `sharedBounds` 从**迷你栏矩形** morph 到**全屏矩形**，起点随底栏设置变
 * （竖屏 / 横屏 / 双栏 / 侧栏 / 内联迷你栏…），任何写死的 p 阈值都会在某种配置下
 * 早判 ⇒ 底栏玻璃先关掉、外壳却还没盖上去 ⇒ **露馅**。
 *
 * 而且三条时间线并不同源（`expansionProgress` 弹簧 / bounds 几何 `AppleEasing(tween)`
 * / recede master）—— 实测「内容层 p=1000 时 bounds 几何只有 937/1000」。所以本文件
 * **不猜进度**，只吃 `MeloXShellGeom.bounds` 这个由 `sharedBounds` 每帧求值处直接落下的
 * **真值矩形**（见 `MeloXPlayerSinkVectorizedSpec.valueAt`）。
 *
 * ── 严格性（「不要露馅」的四道保险）─────────────────────────────────────────
 * ① 坐标系自检：外壳动画**终点**必须正好等于整个窗口的矩形，否则一律判 false
 *    （宁可不停玻璃，也不在坐标系对不上时瞎判）。
 * ② 玻璃面按**真实形状**判（`Capsule()` 的胶囊/圆），并且要求它的完整轮廓（含圆角）
 *    全部落在外壳的不透明区域内 —— 即「覆盖」的字面定义，不加额外外扩余量
 *    （为什么不能加：见 [OcclusionMarginDp] 的实测记录，4dp 会让闸门推迟到动画最后一帧）。
 * ③ 圆角：外壳的圆角用的是**坐平前**的 `meloXPlayerShellCornerDp`（App 层看不到
 *    `flattenAnim`，只能取更大的那个）。圆角只会随坐平**变小** ⇒ 用大值判等于更严格。
 *    另外 Lamé 超椭圆（MeloX 实际用的平滑圆角）比正圆弧更接近直角 ⇒ 用正圆弧做
 *    内切判断同样偏保守。
 * ④ 底栏按**胶囊**判（`Capsule()` 的实际形状），不是按外接矩形判 —— 否则屏幕左下角
 *    那块永远落在系统圆角外面，判据会恒为 false、白做。弧上采样 5 点/角，步长误差
 *    ≈ 1.6px，被 1dp 以上的余量完全吸收。
 *
 * 判据只在**穿越**时写一次 State ⇒ 一次开合最多 2 次重组，不引入每帧重组。
 */

/**
 * 外壳**当前真实绘制矩形**的发布点。
 *
 * 写者：`MeloXPlayerSinkVectorizedSpec.valueAt` —— `sharedBounds` 每帧求值 bounds 的
 *   唯一位置。只有那里同时握有 `initial` / `target` / 缓动后的进度与**最终 Rect**。
 * 读者：[MeloXChromeOcclusionGate]（绘制期读普通字段，不产生快照依赖）。
 *
 * ⚠ 全部是**普通字段**，故意不是 `mutableStateOf`：写入方每帧都写（转场 300~400ms 里
 *   每帧一次），若做成 State 会把所有读者按帧拖着重算 —— 与仓库那条
 *   「禁根 composable 读逐帧量」是同一个道理。
 *   ★ 唯一例外：[sinkFraction]（快照 State）。它没有 `graphicsLayer`-外的读者、
 *     且绘制期读普通字段拿不到新值，见该字段说明。
 * ⚠ 动画停止后这里保留最后一次的值（这正是稳态要继续判「被覆盖」所需要的）。
 */
internal object MeloXShellGeom {
    /** 外壳本帧的 bounds（`sharedBounds` 的 bounds 空间）。null = 从未开过播放页。 */
    var bounds: Rect? = null

    /**
     * 本次 morph 的**跨度** = `initial` 与 `target` 的并集矩形。
     *
     * ⚠ 为什么不是直接存 `target`：打开方向 `target` 是**全屏**、收起方向 `target` 是
     *   **迷你栏**（两个方向的"起点/终点"是反的）。用并集后两种方向都恒等于
     *   「迷你栏 ∪ 全屏」= 全屏 ⇒ 坐标系自检（见 `MeloXChromeOcclusionGate`）在
     *   开、合两个方向都成立。
     */
    var span: Rect? = null

    /** 本帧几何进度（已过 `MeloXPlayerAppleEasing`，与 [bounds] 严格同源）。诊断用。 */
    var progress: Float = 0f

    /**
     * 本帧下沉包络的比例值（0..1，峰值 1；= `dy / sinkPeakPx`，无下沉时 0）。
     *
     * 🔴 **本对象里唯一的快照 State**（其余全是普通字段，理由见上方对象头注释）。之所以
     *   必须破例：读者是播放页**背景层**的 `graphicsLayer` lambda —— 那个 lambda 只在
     *   图层失效时才重跑，**读普通字段永远拿不到新值**（会一直停在上一次的采样）。
     *   写成 `mutableFloatStateOf` 后，每帧写入只让那一个图层失效，**不触发任何重组**。
     * ⛔ 别把它读进组合体（会按帧重组整页）；⛔ 别新增第二个读者（多一个就多一份按帧失效）。
     * ⚠ 动画停止后保留最后一次的值（关闭结束时 `meloXPlayerSinkFraction(1f) == 0f`，
     *   所以稳态自然是 0，不会让背景长期停在拉伸态）。
     */
    private val sinkFractionState = mutableFloatStateOf(0f)
    val sinkFraction: Float get() = sinkFractionState.floatValue

    /**
     * 外壳「已经贴到终点矩形」的余量（0..1，1 = 还差 ≥25% 高度，0 = 已严丝合缝）。
     *
     * ★★★ 2026-11-03 02:0x（用户真机照片定案，第 20 轮）：**这是「四角露播放页底色」的
     *   唯一正解所需的量**。
     *
     * 用户口径（复述三遍，此前一直没接住）：「颜色是跟播放页背景一样的，暂停黑色，那么
     * 四角就是黑色；播放页背景什么色，他就是什么色。」
     * ⇒ **没有黑色图层**，露出的是播放页外壳自己的底色。
     *
     * 几何：外壳高 48dp、内容层 `padding(vertical = 3.dp)` 后 42dp、圆钮 40dp
     *   ⇒ 圆钮外那圈 4dp 镶边在静止态是白玻璃（设计），关闭末段填的却是外壳底色。
     *   真机照片实测最大露出 16px = 4dp（density 4），与这条几何**逐像素吻合**。
     *
     * 为什么不能只调 `blackFallback` 的 alpha：那只管暂停黑。**播放态**露的是彩色封面
     * 背景（`backdropAlpha` 恒 1），改黑兜底对它无效 —— 用户 02:06 明确报「播放状态下
     * 还是很明显有」。
     *
     * ⇒ 做法：外壳高度进入「距终点 ≤25%」这一段时，整块内容层 alpha 收敛到 0，
     *   把迷你栏位置完整让给迷你栏自己（白玻璃 alpha 已 =1）。
     *   窗口只有 48dp→60dp 这一小段，视觉上是「收进迷你栏」，读不出渐隐。
     *
     * ⚠ 与 `sinkFraction` 同规矩：**只给一个 graphicsLayer 读者**，别读进组合体。
     */
    private val tailAlphaState = mutableFloatStateOf(1f)
    val tailAlpha: Float get() = tailAlphaState.floatValue

    /**
     * 每帧 bounds 求值后的回调（`MeloXApp` 挂 `MeloXChromeOcclusionGate::evaluate`）。
     *
     * ⚠⚠ 2026-10-26 第二次实测修正：判据原本靠「画黑底那个 Box」的 `drawBehind` 每帧
     *   读 `recedeFraction()` 来驱动，真机实测**整段转场只跑了 2 次**（诊断里
     *   `chromeOcclProbe` 只有 2 行，且都在几何 p≈0.99），于是：
     *     · 闸门几乎不提前（p=0.9932 才翻 true，等于转场期零收益）；
     *     · **收起方向一次都没翻回 false**（`reset()` 要等播放页彻底消失才兜底）
     *       ⇒ 收起动画里底栏玻璃一直停在"不透明实心"档 = 露馅。
     *   原因：Compose 的 draw 只在**该节点自身失效**时才重跑，而那条 `drawBehind` 的
     *   失效时机与 bounds 动画不在同一条链上。
     *   ⇒ 改为在 [record] 末尾直接回调 —— 这是 `sharedBounds` 每帧求 bounds 的唯一位置，
     *     **确定性每帧**执行，与任何绘制/失效优化无关。
     *   ⚠ 回调里会写 `occluded`（翻 true 时一次重组）。一次开合最多 2 次；稳态不再回调 ⇒ 零开销。
     */
    var onFrame: (() -> Unit)? = null

    /** 由 bounds 求值处每帧调用。 */
    fun record(
        bounds: Rect,
        initial: Rect,
        target: Rect,
        progress: Float,
        /**
         * 本帧下沉包络的**比例值**（0..1，峰值 1；`dy / sinkPeakPx`），无下沉时 0。
         *
     * 🔴 2026-10-28 13:5x 新增，用途：让**流光背景层**跟着这一记下沉做额外位移。
     *   ⚠ 必须由本函数发布而不是让背景层自己重算：dy 吃的是**线性墙钟 + 方向门控
     *     + 窗口下限换算**，重算一份必然与外壳错位（10-27 那次「两条通道不同源」
     *     的教训）。这里发布的正是 `valueAt` 里那个 dy 的同一个分子。
     *   ⚠⚠ 2026-10-28 14:2x 起**没有消费者**：13:5x 那版用它做壳内 `scaleY`
     *     拉伸，实测被外壳的 `clip = true` 裁掉、观感无效。14:0x 改用 bounds 底边
     *     延长（`dyStretch`），但用户判「更差」⇒ 14:2x **整体回到 0.6.5 的纯平移**
     *     （22dp 同时加在 top/bottom，零拉伸），本条比例因此闲置。
     *     ⇒ 保留它是因为「让背景独立位移」仍是这条需求的候选解（若将来又要「拉出去」），
     *     届时需要**外壳坐标系**的位移量而不是屏幕量 —— 本函数是唯一同时握有
     *     `bounds`（含当前纵向缩放比）的地方。
     *     ⛔ 别在没有人读的时候把它读进组合体。
         */
        sinkFraction: Float = 0f,
    ) {
        this.bounds = bounds
        span = Rect(
            minOf(initial.left, target.left),
            minOf(initial.top, target.top),
            maxOf(initial.right, target.right),
            maxOf(initial.bottom, target.bottom),
        )
        this.progress = progress
        // ⚠ 写快照 State：读者只有背景层那一个 `graphicsLayer`。见 [sinkFraction]。
        sinkFractionState.floatValue = sinkFraction
        // 外壳距「终点矩形」的余量。**判据只用几何方向**，不看 `closingIntent`。
        //
        // ★ 2026-11-03 02:1x（第 21 轮，用户报「手动下拉后半段还有缝」）：
        //   上一版由 `hostExitingState` 门控 —— 但手势拖拽收起期 `MeloXApp` 刻意把它
        //   保持 false（内容要跟手、不能被判成「正在退出」）⇒ **手势路径整段绕过**，
        //   只有点返回键那条路生效。用户实测正是「其他场景不明显了、手动下拉还有缝」。
        //   ⇒ 改成纯几何：`target` 比 `bounds` 矮 = 正在往小矩形收（关闭 / 跟手拖拽 /
        //     松手回落都算），才按「距终点 ≤25%」收敛。放大方向（打开）恒 1。
        //   ⚠ 稳态全屏播放页 target == bounds ⇒ 也落 1，不会把整页弄没。
        //
        // ★★★ 2026-11-03 12:0x（第 23 轮，用户报「deadzone 太宽 ⇒ 封面色玻璃在迷你栏
        //   白玻璃接管前就整块透明，中段出现空档＝观感『消失』」）：
        //   上一版 deadzone 0.5×（外壳高 ≤72dp 恒 0）虽然把黑角彻底压掉，但让外壳背景层
        //   在关闭进度 p≈0.3（外壳≈72dp）就消失，而迷你栏白玻璃（`miniSurfaceAlphaProvider`
        //   上界 0.48）要到 p<0.48 才接管 ⇒ 中间 p∈[0.3,0.48] 一段外壳背景没了、白玻璃还没来，
        //   露主页/空档，观感发秃。
        //   ⇒ 收窄回 **25% 窗口**（外壳高 48dp→0，60dp→1，封面色玻璃只在『贴到迷你栏』
        //     那 12dp 内才让位）。这样外壳封面色玻璃在整个收缩过程保留，只在最后收拢成
        //     迷你栏时才平滑交还给白玻璃，不再中途秃；黑角则回到此前『漏一点』的程度
        //     （54dp 附近外壳比白玻璃大 12dp 那圈半透明）——若不可接受，根治是让外壳 morph
        //     终点对齐白玻璃（48dp→42dp，代价：静止态丢 3dp 白边），或条件 clip。
        tailAlphaState.floatValue =
            if (target.height > 1f && target.height < bounds.height) {
                ((bounds.height - target.height) / (target.height * 0.25f)).coerceIn(0f, 1f)
            } else {
                1f
            }
        onFrame?.invoke()
    }
}

/**
 * 判据两侧的额外余量 —— **两档都是 0**。
 *
 * ⚠⚠ 2026-10-26 真机实测后从 `4dp / 1dp` 改为 0：那 4dp 把闸门从「几何 p≈0.76」
 *   一路推到「p=0.9964」（= 动画最后一帧），转场期一点都没省到。
 *
 *   原因很具体：搜索键玻璃面是 `Capsule()` 正方形（228px），它**紧贴屏幕右下圆角** ——
 *   矩形角到屏幕圆角圆心只有 161px，而 4dp×√2 = 22.6px 外扩后正好顶到 184px 的弧上。
 *   于是只要外壳比屏幕小一点点，判据就恒 false。
 *
 *   而这层「余量」本来就是**重复的保守**：圆角一栏已经取「曲线峰值（= 46dp 系统圆角）」
 *   当上界（见 [MeloXChromeOcclusionGate.shellRadiusPx]），且用**正圆弧内切**判
 *   （比 MeloX 实际的 Lamé 超椭圆更往里收）。换句话说，判据本身已经严格到
 *   「就算圆角拉到最大、也是往里多收一截」，再外扩玻璃面属于重复保险，代价却是闸门
 *   几乎不生效。
 *
 *   ⇒ margin = 0 时判据回到它的**字面定义**：「玻璃面的完整形状（含圆角）此刻是否
 *     全部落在外壳的不透明区域内」。这就是「覆盖」这件事本身，不早一帧、不晚一帧。
 *     边界误差由两处吸收：角弧 5 点采样（步长误差 ≈1.6px，方向朝内 = 偏保守）、
 *     正圆弧 vs 超椭圆（偏保守）。
 *
 *   ⚠ 滞回（进入严 / 保持松）也一并去掉：两档相等后就不存在「先判 true 再判 false」
 *     的抖动区间；而收起方向上真正会变的是 bounds（单调缩小）与圆角（单调减小），
 *     都不会在边界来回 ⇒ 不需要滞回。留滞回只会让退出变晚 = 玻璃露出时才恢复。
 */
private const val OcclusionMarginDp = 0f

/** 坐标系自检容差（px）。外壳终点与窗口矩形的偏差超过它就不判。 */
private const val OcclusionCoordTolPx = 2f

/**
 * 判「外壳正在收缩」的高度容差（px）。
 *
 * ⚠ 取小值：收起方向前段高度变化很快（整段约 368px），1px 足以可靠触发；
 *   而稳态恒定为 0 变化 ⇒ 不会误判。打开方向高度单调增 ⇒ 更不会误判。
 */
private const val OcclusionShrinkTolPx = 1f

/** 每个角弧的采样份数（份数 + 1 = 采样点数）。 */
private const val OcclusionArcSegments = 4

/**
 * 22.5° 步长的单位圆表（16 项，cos/sin 交错存放），表长 32。
 *
 * 存在理由：判据在**每一帧**求值（转场 360ms × 90Hz），每个玻璃面 4 段角弧 × 5 点，
 * 两个面就是 40 次三角函数。预计算成常量表后整条判定只剩乘加与比较。
 */
private val OcclusionUnitCircle: FloatArray = FloatArray(32).also { table ->
    for (i in 0 until 16) {
        val rad = (i * 22.5f) * (Math.PI.toFloat() / 180f)
        table[i * 2] = cos(rad)
        table[i * 2 + 1] = sin(rad)
    }
}

/**
 * 底部 chrome 玻璃面的**覆盖门控**。
 *
 * 用法（全部在 `MeloXApp` / `MeloXBottomChrome` 侧）：
 *  · 布局期把 chrome 玻璃面的窗口矩形写进 [navRect] / [searchRect]（普通字段）；
 *  · bounds 每帧求值处（`MeloXShellGeom.record`）回调一次 [evaluate]；
 *  · 组合期读 [occluded] 决定是否把 `LocalMeloXBackdrop` 换成 null
 *    （= `meloXGlassAvailable()` 为 false ⇒ 每个材质函数走已写好的**不透明**
 *      `background(stableSurface, shape)` 回落分支，`drawBackdrop` 一次都不跑）。
 *
 * ⚠ [occluded] 为 true 期间，`bottomChromeBackdrop` 录制节点上的
 *   `drawBehind { recedeFraction() }` 也会停止逐帧失效（见调用点）⇒ 录制冻结。
 *   两者必须一起停：只冻结录制而玻璃还在采样，会看到**陈旧内容**；只停玻璃而录制
 *   还在重录，则白付一次整屏 `recordLayer`。
 */
internal class MeloXChromeOcclusionGate {

    // ── 输入：全部普通字段（只在布局/组合变化时写，绘制期读）─────────────────

    /** nav 胶囊玻璃面的窗口矩形（`Capsule()`）。 */
    var navRect: Rect? = null

    /** 搜索键玻璃面的窗口矩形（`Capsule()`，正方形上即正圆）。null = 本配置下没有它。 */
    var searchRect: Rect? = null

    /** 窗口尺寸（px），外壳终点就是它。 */
    var windowSize: IntSize = IntSize.Zero

    /** 当前 density（dp→px）。 */
    var density: Float = 1f

    /** 系统屏幕圆角（px）—— 外壳圆角曲线的峰值，与宿主同一个来源。 */
    var peakCornerPx: Float = 0f

    /**
     * `MeloXApp` 根是否**贴窗口原点**。
     *
     * 判据把「外壳 bounds 空间」与「`boundsInWindow` 空间」当成同一个空间来比较，
     * 这只有在根布局没有偏移时才成立（edge-to-edge 全屏）。一旦发现根有偏移
     * （刘海屏 padding、分屏、画中画…），直接禁用整条闸门 —— 宁可不省，不许露馅。
     */
    var originVerified: Boolean = false

    /**
     * 上一帧外壳高度（px）—— 用于判 morph **方向**（收缩 = 收起方向）。
     *
     * ⚠ 必须用**高度趋势**而不是 `dy`：`dy` 同时加在 top/bottom、不影响高度；而
     *   打开方向高度单调增、收起方向单调减、展开后稳态恒定 ⇒ 三态可分。
     * `-1f` = 尚无有效帧（此时不做方向判定，宁可门控正常）。
     */
    private var lastShellHeight: Float = -1f

    // ── 输出：只在穿越时写 ─────────────────────────────────────────────────

    /** 外壳是否已完全、不透明地盖住底部 chrome 的全部玻璃面。 */
    val occluded: MutableState<Boolean> = mutableStateOf(false)

    /** 强制复位（播放页不可见时兜底，防止残留 true 把玻璃永久关掉）。 */
    fun reset() {
        if (occluded.value) occluded.value = false
    }

    /**
     * 每帧求值。调用点：`MeloXShellGeom.onFrame`（= `sharedBounds` 每帧对 bounds 求值的
     * 唯一位置，**确定性每帧**，见 [MeloXShellGeom.onFrame] 的踩点记录）。
     *
     * ⚠ 本函数**不读任何快照状态**（只读普通字段 + 必要时写 [occluded]）—— 避免把
     *   任何绘制/组合节点变成新的订阅者。
     */
    fun evaluate() {
        val shell = MeloXShellGeom.bounds
        val span = MeloXShellGeom.span
        val nav = navRect
        val w = windowSize.width
        val h = windowSize.height
        if (shell == null || span == null || nav == null || w <= 0 || h <= 0 || !originVerified) {
            lastShellHeight = -1f
            reset()
            return
        }
        // ① 坐标系自检：本次 morph 的跨度（迷你栏 ∪ 全屏）必须正好是整个窗口。
        if (abs(span.left) > OcclusionCoordTolPx ||
            abs(span.top) > OcclusionCoordTolPx ||
            abs(span.right - w) > OcclusionCoordTolPx ||
            abs(span.bottom - h) > OcclusionCoordTolPx
        ) {
            reset()
            return
        }

        // ★★★ 2026-10-29：**收起方向一律放行（不门控）** —— 等价于「给收方向留足够提前量」。
        //
        //   ### 问题（实测，放音乐、460 档、每版 n=3）
        //   门控在「外壳重新让开底栏」那一帧翻回 false ⇒ 玻璃采样与整屏 `recordLayer`
        //   **同时解冻**，这笔「解冻突发」（重录整屏 + 重采样/重跑 AGSL + 一次重组）
        //   正好落在**底栏开始可见**的那几帧上。实测收方向掉帧 4.0~5.0 → **6.3~7.5（+50%）**，
        //   而开方向只改善 ~17% ⇒ 合计净效果变差（+0.49 帧/循环）。
        //
        //   ### 为什么不能靠「几何提前量」救
        //   覆盖期只到 `geomP≈0.51`，而该点已落在关闭时间轴的 **f≈0.40**（AppleEasing 前段快）。
        //   要在曝光前 150ms 解冻，就得在 **f≈0.07（geomP≈0.02）** 就恢复 ⇒ 收起方向几乎一秒不省。
        //   ⇒ 「足够长的提前量」在收起方向**退化为「不门控」**，那就直接写清楚，别绕。
        //
        //   ### 现在的行为
        //   · 打开方向 / **播放页展开后的稳态**（高度不再变）⇒ 照常门控（稳态收益保留 ——
        //     这正是既有分析里「落定后仍持续掉帧」要治的那一段）；
        //   · 收起方向（高度单调减小）⇒ 立即 `reset()` 放行，回到无门控行为、无解冻突发。
        val shellH = shell.height
        val shrinking =
            lastShellHeight >= 0f && shellH < lastShellHeight - OcclusionShrinkTolPx
        lastShellHeight = shellH
        if (shrinking) {
            reset()
            return
        }

        val radiusPx = shellRadiusPx()
        val keep = occluded.value
        val marginPx = OcclusionMarginDp * density
        val next = capsuleCovered(shell, nav, radiusPx, marginPx) &&
            searchRect?.let { capsuleCovered(shell, it, radiusPx, marginPx) } != false
        if (next != keep) {
            occluded.value = next
        }
    }

    /**
     * 外壳本帧的圆角（px）—— **恒取上界**。
     *
     * ⚠⚠ 为什么不用任何进度去算真实圆角（2026-10-26 踩点）：
     *   宿主那里圆角 = `meloXPlayerShellCornerDp(expansionProgress) × (1 - flattenAnim)`，
     *   而 `expansionProgress`（内容层 spring/tween）与 bounds 几何**不是同一条时间线**
     *   —— 实测「内容层 p=1000 时 bounds 几何只有 937/1000」，即几何进度**滞后**。
     *   若拿几何进度代进圆角曲线，会算出**比真实更小**的半径 ⇒ 判据变宽松 ⇒ 可能在外壳
     *   圆角还没让开时就把底栏玻璃停掉 = 露馅。
     *
     *   ⇒ 这里直接取「曲线在 p 升满时的峰值」（= 系统屏幕圆角），它对**任何时刻**都成立：
     *     · p 在 rise 段：真实半径 ≤ 峰值；
     *     · p 升满后「坐平」：半径只会从峰值收到 0。
     *     用这个上界判 = 要求「就算圆角拉到最大，底栏也在壳内」⇒ 与进度彻底解耦，
     *     怎么改曲线/曲线参数都不会让判据失准（只会让它更保守）。
     *   代价：判 true 的时机被推迟到外壳几乎铺满（≈ p 0.9+）—— 这正好是「覆盖」这件事
     *     真正成立的位置，B 方案本来就只能吃这一段 + 稳态，不亏。
     */
    private fun shellRadiusPx(): Float {
        // 兜底再取一次宿主的默认峰值（46dp）：`displayCorner.usedPx` 万一没取到真值，
        // 也绝不能把上界算小。
        val fallback = MeloXPlayerPeakCornerDp * density
        return maxOf(peakCornerPx, fallback).coerceAtLeast(0f)
    }

    /**
     * 单个玻璃面是否被外壳完全覆盖。
     *
     * @param target 玻璃面外接矩形（真实形状是该矩形的**胶囊**内切）
     * @param shellRadius 外壳本帧圆角半径
     * @param margin 要求外壳多盖住的余量（> 0 = 更严格）
     */
    private fun capsuleCovered(
        shell: Rect,
        target: Rect,
        shellRadius: Float,
        margin: Float,
    ): Boolean {
        // 把玻璃面**外扩** margin ⇒ 等价于要求外壳多盖住这么多。
        val left = target.left - margin
        val top = target.top - margin
        val right = target.right + margin
        val bottom = target.bottom + margin
        if (right <= left || bottom <= top) return false

        // 四边必须在外壳的直边以内（先做最便宜的排除）。
        if (left < shell.left || top < shell.top || right > shell.right || bottom > shell.bottom) {
            return false
        }
        if (shellRadius <= 0f) return true

        // 胶囊半径 = min(w,h)/2（`Capsule()` 的定义）。四段角弧逐个采样。
        val capsuleRadius = min(right - left, bottom - top) / 2f
        if (capsuleRadius <= 0f) return true
        val cxLeft = left + capsuleRadius
        val cxRight = right - capsuleRadius
        val cyTop = top + capsuleRadius
        val cyBottom = bottom - capsuleRadius

        // 左上 / 右上 / 左下 / 右下（角度取屏幕坐标系：y 向下 ⇒ 90° 指向下）
        // 索引 = 角度 / 22.5°：0°→0 / 90°→4 / 180°→8 / 270°→12
        return arcCovered(shell, cxLeft, cyTop, capsuleRadius, 8, shellRadius) &&
            arcCovered(shell, cxRight, cyTop, capsuleRadius, 12, shellRadius) &&
            arcCovered(shell, cxLeft, cyBottom, capsuleRadius, 4, shellRadius) &&
            arcCovered(shell, cxRight, cyBottom, capsuleRadius, 0, shellRadius)
    }

    /**
     * 采样一段 90° 角弧（5 点），检查每个采样点都落在外壳的圆角矩形内。
     *
     * @param startDegIndex 起点角度 / `OcclusionArcStepDeg`（0 / 4 / 8 / 12）
     */
    private fun arcCovered(
        shell: Rect,
        centerX: Float,
        centerY: Float,
        radius: Float,
        startDegIndex: Int,
        shellRadius: Float,
    ): Boolean {
        for (i in 0..OcclusionArcSegments) {
            // `and 15` = 对 16 取模（表的项数正好是 2 的幂），避免最后一个点越界。
            val idx = ((startDegIndex + i) and 15) * 2
            val x = centerX + radius * OcclusionUnitCircle[idx]
            val y = centerY + radius * OcclusionUnitCircle[idx + 1]
            if (!pointCovered(shell, x, y, shellRadius)) return false
        }
        return true
    }

    /**
     * 单点是否落在外壳的圆角矩形内（正圆弧内切判断）。
     *
     * ⚠ 正圆弧比 MeloX 实际使用的 Lamé 超椭圆（平滑圆角）**更往里收** ⇒ 用正圆弧判会
     *   把一些其实被盖住的点判成没盖住 = 偏保守，正是本闸门要的方向。
     */
    private fun pointCovered(shell: Rect, x: Float, y: Float, radius: Float): Boolean {
        if (x < shell.left || x > shell.right || y < shell.top || y > shell.bottom) return false
        if (radius <= 0f) return true
        val cornerX = when {
            x < shell.left + radius -> shell.left + radius
            x > shell.right - radius -> shell.right - radius
            else -> return true
        }
        val cornerY = when {
            y < shell.top + radius -> shell.top + radius
            y > shell.bottom - radius -> shell.bottom - radius
            else -> return true
        }
        val dx = x - cornerX
        val dy = y - cornerY
        return dx * dx + dy * dy <= radius * radius
    }
}
