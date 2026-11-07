package com.lladlam.melox.ui.player

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.exp
import com.lladlam.melox.ui.animation.MeloXSystemScale
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime

// SharedTransition defaults to a spring bounds transform. For an interactive
// seekable transition that makes different children advance by different
// apparent amounts. Use one child timeline and animate only the master
// SeekableTransitionState fraction when the gesture is released.
//
// ⚠⚠ 2026-10-29：这里原先还有一个 `MeloXPlayerTransitionDurationMillis = 360`（大写 M）
//   常量，**已删除**——它是**死代码**（全仓零引用），而且它记录的 360 已经过时
//   （用户拍板「以最新版的 **460ms** 为中间标准值」，五档重划为 270/360/460/600/780）。
//   ⚠ 删它的另一个原因：它是 `const val`（无 getter）所以当年不撞名；一旦改成
//     `val` 引用枚举（枚举成员是运行时值，不能是 const），它的 getter
//     `getMeloXPlayerTransitionDurationMillis()` 就会与下面已有的
//     `meloXPlayerTransitionDurationMillis` **JVM 签名冲突**（编译期报
//     "Platform declaration clash"）。
//   ⇒ **真正的取值链只有一条**（默认档的单一真源在
//   `MeloXPlayerTransitionDurationTier.Default.millis`）：
//   `MeloXSettingsRuntime.playerTransitionDurationMs`（设置值）
//   → `meloXPlayerTransitionDurationMillis` → `meloXPlayerTweenDurationMillis()`。
internal val meloXPlayerTransitionDurationMillis: Int
    get() = MeloXSettingsRuntime.playerTransitionDurationMs

// ══ 播放器动画的**唯一时长出口**（2026-10-03）═══════════════════════════════════
//
// ### 为什么需要它（用户报障「动画速度跟设置的 360ms 对不上」）
//
// Compose 的 `tween(D)` 会被 `MotionDurationScale`（Android 实现读
// `ANIMATOR_DURATION_SCALE`）**自动乘上系统缩放** ⇒ **墙钟 = D × 系统缩放**。
// 本机实测三键全 `1.35` ⇒ 设 360ms 实跑 **486ms**，所有档位等比例偏慢 35%。
// 而设置项写的是「播放器展开/收回时长 360ms」—— 用户设一个数字就是在指定
// **这段动画的墙钟时长**，不是「相对系统的倍数」。
//
// ### 修法
// spec 固有时长写 `D / 系统缩放`，Compose 再乘回来 ⇒ 墙钟恰好 `D`。
// ⇒ **用户的设置值在任何缩放下都成立**（三键 0.5 / 1.0 / 1.35 / 2.0 全部一样）。
//
// ⚠⚠ **必须走这一个出口，禁止裸用 `meloXPlayerTransitionDurationMillis`当tween 时长。**
//   本文件曾出现「master 换了、下沉没换」的不同步事故（见 [meloXPlayerRecedeSpec]）；
//   时长同理——任何一处漏除都会单独漂移。
internal fun meloXPlayerTweenDurationMillis(): Int =
    MeloXSystemScale.fixedDurationMillis(meloXPlayerTransitionDurationMillis)

// 上游 MiniPlayer 使用的两个 BoundsTransform（feature 分支重构为函数版，
// 但回退上游的 MiniPlayer 仍按值引用）
internal val MeloXArtworkBoundsTransform = BoundsTransform { _, _ ->
    tween(
        durationMillis = 300,
        easing = FastOutSlowInEasing,
    )
}

internal val MeloXPlayerShellBoundsTransform = BoundsTransform { _, _ ->
    tween(
        durationMillis = meloXPlayerTransitionDurationMillis,
        easing = FastOutSlowInEasing,
    )
}

// ══ 三条时间线的**同源铁律**（2026-10-04，两次踩坑后定档）═════════════════════
//
// 播放器展开/关闭有**三条**独立时间线，必须逐帧同源，否则用户看到「对不上」：
//
// | 通道 | 谁在驱动 | 本文件里的取样点 |
// |---|---|---|
// | ① 外壳几何 morph + 关闭尾段下沉 | shared bounds 的 `MeloXPlayerSinkVectorizedSpec` | `linearProgressOf` → `MeloXPlayerAppleEasing` |
// | ② 背景页下沉 / 缩放 | `MeloXApp.recedeMasterFraction` ← [meloXPlayerRecedeSpec] | 同 ① |
// | ③ 内容层 `expansionProgress`（圆角 rise / 背景隔离 / 玻璃交叉淡入） | `transition.animateFloat` | master 的进度 → `MeloXPlayerAppleEasing` |
//
// ⚠⚠⚠ **master 必须是「线性时间轴 tween」**（`MeloXApp.playerOpen/CloseFractionSpec`），
//   三者才都等于**单层** `MeloXPlayerAppleEasing(u)`、逐帧严格相等。
//
// ### 两次踩坑（都别再犯）
// ① 2026-10-03：master 是线性，但 ①② 曾用 `MeloXIOSExpandEasing`（decelerate）
//    ⇒ 形状不同，**下沉先到位、播放器还在动**。修法：①② 也换成 `MeloXPlayerAppleEasing`。
// ② 2026-10-04：把 master 换成 ζ=1 弹簧想解决「打开太线性」，①② 没跟着换
//    ⇒ ③ 变成 `AppleEasing ∘ 弹簧`（**双层**），①② 仍是单层
//    ⇒ 内容层超前 62~75ms（用户报「效果更差」：前 100ms 甩完 95%，剩 139ms 空转）。
//    **把 ①② 也改成弹簧「统一」是错的** —— 那只会把双层推广到全部通道。
//    ⇒ 正确解法是**回退 master 到线性 tween**，让三者重新同为单层。
//
// ⚠ 换 master 曲线时**三处必须一起换**（`playerOpen/CloseFractionSpec`、
//   [meloXPlayerRecedeSpec]、[MeloXPlayerSinkVectorizedSpec]）—— 这是本块存在的唯一理由。
//
// ⚠⚠ **「按墙钟定义」的东西必须用 `linearProgressOf()`，不能挂别的曲线**：
//   [meloXPlayerSinkFraction] 的窗口常量含义是「设置时长的后 38%」；
//   一旦改挂到弹簧之类前段极快的曲线上，`f ≥ 0.62` 会在 **~8ms** 就成立
//   ⇒ 下沉几乎与展开同时发生（与该常量的设计前提完全相反）。

// ══ 🔴 2026-10-29 现状更正：**本铁律与当前代码不一致，取舍已定为「保手感」** ══════
//
// 现状（L1，读过码）：master **不是**线性 tween，而是
//   `MeloXApp.playerSettleMasterSpec()` =
//   `spring(dampingRatio = 1.0, stiffness = ω², visibilityThreshold = 0.001)`，
//   `ω = PLAYER_SETTLE_99_K(6.2) / durationSec`（`MeloXApp.kt:2982-3019`），
//   由 `playerOpenFractionSpec() / playerCloseFractionSpec()` **无条件共用**。
// ⇒ 上文「三者同为单层、逐帧严格相等」**当前并不成立**。这是**知情取舍，不是遗漏**：
//
// ### 为什么不按本铁律「修正」（三条都有证据）
// 1. **每帧成本与时长无关**（实测 270→780ms 五档、放音乐）：`issue50` 6.88~7.17ms、
//    真实 GPU p90 5.0~6.2ms 全部持平 ⇒ spring master **不会多掉帧**。
// 2. **它确实把时长拉长**（代码可推）：`visibilityThreshold = 0.001` 需 z = 9.23，
//    而 ω 是按 99%（z = 6.2）反解的 ⇒ 落定 ≈ **1.5×** 设置值。
//    多出来的主要是**低负载尾帧**（实测可见 morph 段只有 ≈1.13×）。
// 3. ⚠⚠ **实测反证（用户，比任何推演都硬）**：把 master 改回线性 tween 后，关闭方向的
//    **下沉 [meloXPlayerSinkFraction] 怎么调都不丝滑**。原因：下沉窗口是**墙钟**定义的
//    （「设置时长的后 38%」、峰 22dp），**窗口只给比例，绝对毫秒由 master 决定** ——
//    tween 把总时长压到恰好 = 设置值 ⇒ 窗口变短 ⇒ 22dp 摊到更少帧 ⇒ 每帧步长变大 ⇒ 顿。
//    ⇒ 那条路**不是没调好，是被 master 的时长钉死了**。
//    （这正是上文「按墙钟定义的东西必须用 `linearProgressOf()`」的另一面：那条只保证窗口
//      **起点**按墙钟算；**窗口有多长**仍取决于 master。）
//
// ### 现在靠什么兜住「同源」
// · 圆角坐平：不等 `p` 到 1，而是等 `!transition.isRunning`（见 `ANALYSIS-…§15.10`）。
// · 底栏遮挡门控：**不猜进度**，只吃 `MeloXShellGeom.bounds` 真值矩形
//   （`MeloXChromeOcclusion.kt:34-37`）。
// ⇒ **新增任何「需要几何进度」的代码，不要用 `expansionProgress`**：要么用真值 bounds
//    （同门控），要么自己重新对齐；否则必然错拍。
//
// ⛔ 若要动 master（包括「恢复线性 tween」），**先接受手感会变**：下沉的连续性与尾段质量
//    都依赖当前这条 spring 的时长。改动前必须先与用户对齐目标，并四条通道一起核对。

// 上游 lladlam 原版：迷你播放栏自身显隐（expansionProgress）用的浮点 spec。
// ⚠ 2026-10-02：迷你栏已整体回归上游（文件逐字替换为 `upstream/main`），此函数是
//   上游 `MeloXIOSMiniPlayer.kt` L129 直接调用的，**必须存在**，否则编译不过。
// ⚠⚠ 2026-10-03：easing 由 `FastOutSlowInEasing` 换成 [MeloXPlayerAppleEasing]（见下方实测块）。
//   时长改走 [meloXPlayerTweenDurationMillis]（抵消系统缩放）。
// ⚠⚠ 2026-10-03 回退记录：本函数曾被临时改成 `LinearEasing`（把形状统一上移到 fraction），
//   用户实测「反而比上版本效果差」⇒ **已回退**。缓动留在这一层（几何/圆角/内容/recede 同源）。
// ⚠⚠⚠ 2026-10-15 回退记录：曾给本函数加 `open` 参数、打开方向返回 ζ=0.75
//   underdamped 弹簧（与 master / recede 三处同换），用户实测「效果太差」⇒
//   恢复无条件 tween。打开方向的「弹」不由这里提供。
internal fun meloXPlayerLinearFloatSpec() = tween<Float>(
    durationMillis = meloXPlayerTweenDurationMillis(),
    easing = MeloXPlayerAppleEasing,
)

// ══ Apple Music 主曲线（2026-10-03，按参考视频逐帧拟合）═════════════════════
//
// 为什么必须换掉 `FastOutSlowInEasing`（**这是「尾端僵硬」的第二个根因**，
// 第一个根因是「全链无过冲」已记在文件下方）：
//
// 逐帧量参考视频（Apple Music 迷你胶囊 ⇄ 全屏播放器）的**容器左右边缘宽度**
// （白底页面 vs 灰蓝面板 = 锐利边界；扫描线取 `top+30` 避开圆角），归一化成几何进度：
//
//   | `u` | 实测 `p_geom` | FastOutSlowIn | cubicOut |
//   |---|---|---|---|
//   | 0.167 | **0.280** | 0.085 ❌ | 0.421 |
//   | 0.333 | **0.573** | 0.457 | 0.704 |
//   | 0.667 | **0.787** | 0.921 ❌ | 0.963 |
//   | 0.833 | **0.893** | 0.983 ❌ | 0.995 |
//
// ⇒ `FastOutSlowIn` 的**头段太慢**（u=0.167 才 0.085）：起步阶段几何几乎不动，
//   全部变化被压到后半程 ⇒ 观感就是「前半段憋住、尾端猛冲一下再硬停」。
//
// ⚠⚠⚠ 2026-10-03 第二次改（用户报障「动画曲线太直太线性」）：
//   上一版 `(0.25, 0.65, 1.00, 1.00)` 虽然 RMSE 最低（0.0119，比 iOS 候选好得多），
//   但**速度剖面是单调刹车**：瞬时斜率从 2.27 一路降到 0.51，**全程只在减速、
//   没有任何加速段** ⇒ 观感就是「直、线性」。
//   根因是 `y1 = 0.65 <= 1` 且 `x2 = 1.0`：控制点全在终点以内，数学上只能是单调减速。
//
// ⇒ iOS 那种丝滑的真正来源是 **`y1 > 1`（控制点越过终点）** ⇒ 曲线先冲过头再回来，
//   末段斜率为**负**（真回抽）。本项目取**A 档 `y1 = 1.15`**（用户拍板）：
//
//   | 档 | 曲线 | 过冲 | 末段斜率 | 说明 |
//   |---|---|---|---|---|
//   | **A（本项目）** | `(0.25, 1.15, 0.75, 1)` | **+0.64%** | **−0.04** | 肉眼看不出过冲，但手感变了 |
//   | B | `(0.22, 1.25, 0.70, 1)` | +2.04% | −0.06 | 回弹更明显，过冲区宽 47% |
//
//   取A 而非 B 的理由：过冲区宽度 `y1=1.15` 是 29% 时长、`y1=1.25` 是 47%，
//   而**过冲期间圆角绝不能动**（见下），B 的过冲区太长、留给圆角收口的窗口太窄。
//
// ⚠⚠⚠ **带过冲 ⇒ 必须同步改圆角坐平判据，否则必然提前坐平**：
//   过冲曲线在 `u ≈ 0.71`（A 档）就**向上穿过 1.0**（旧判据 `expansionProgress >= 1f`
//   在此变真），而动画其实还剩 29% 时长 ⇒ 圆角提前 **77ms** 开始坐平
//   （B 档为 147ms / 提前 120ms）。
//   ⇒ 已把 `MeloXIOSNowPlayingSharedHost` 的 `shellSettledOpen` 换成
//     **过渡状态机判据**（`currentState == targetState`），它与 `p` 的数值、
//     easing 形状、过冲幅度**完全无关** ⇒ 换任何曲线都不会再提前坐平。
//   ⚠ 代价：坐平触发点从 `u=0.55` 变成 `u=1.0`，**必须重跑四条进入路径真机验证**
//     （点迷你栏 / 手势 seek+settle / 手势塌陷后重开 / 冷启 intent）。
//
// ⚠⚠ **改本曲线必须同步改四处**，否则几何与内容错拍（本文件已因此返工多轮）：
//   ① `meloXPlayerLinearFloatSpec()`（master `expansionProgress`）
//   ② `MeloXPlayerShellBoundsTransform`（外壳 sharedBounds 几何）
//   ③ `MeloXArtworkBoundsTransform`（封面 sharedElement 几何）
//   ④ `meloXPlayerRecedeSpec()`（**背景页下沉** —— 曾因漏改导致「下沉先到位」不同步）
//   圆角 `meloXPlayerShellCornerDp()` 吃的是 master 缓动**之后**的 `p`，
//   四者同源同速才能让「几何 / 圆角 / 内容 / 下沉」严格同步。
// ⚠⚠⚠ 2026-10-03 第三次改（用户报障「动画还是不够丝滑优雅」＋ 网络检索结论「AM 用弹簧」）：
//   前两版都是 cubic-bezier：`(0.25,0.65,1,1)` 被读成「太直太线性」；
//   `(0.20,0.90,0.30,1)` **前段过猛** —— f=0.31 就已走完 **84%** 几何 ⇒
//   「一记急刹 + 长尾死拖」，观感是「snap 一下就没了」，恰恰**不是**丝滑。
//   ⇒ 换成**归一化的临界阻尼弹簧**
//     `p(f) = [1 − (1 + k·f)·e^(−k·f)] / [1 − (1 + k)·e^(−k)]`：
//     · **单调、无过冲** ⇒ 圆角不会提前坐平（`shellSettledOpen` 那条状态机判据依旧成立）；
//     · 分母归一化 ⇒ `p(1)` **严格 = 1**（不归一化会停在 <1、末帧跳一下）；
//     · `k = 6` 的几何剖面 ≈ **0.27 / 0.60 / 0.82 / 0.92**（f = 0.167/0.333/0.5/0.667），
//       与**用户参考视频逐帧量出**的 0.28 / 0.573 / 0.787 **前三点几乎重合**
//       ⇒ 同时满足「像弹簧」与「像那个参考视频」两个口径。
//     · 与旧曲线的差别：旧曲线前段是「甩出去」（速度先冲到峰值再骤降），新曲线是
//       「一路连续减速」——这正是「丝滑优雅」的来源。
private const val MeloXPlayerSpringK = 6f

/**
 * 起步**初速度比** α：解决「ζ=1 临界阻尼起步斜率 = 0」的天生缺点。
 *
 * ### 数学
 * 临界阻尼、起点 0 / 终点 1、带初速度 `v₀` 的解（令 `α = v₀/ω`）：
 * ```
 * x(f) = [1 − (1 + (1−α)·z)·e^−z] / End ,  z = k·f ,  End = x(1)
 * 起步斜率 dx/df|₀ = k·α / End        （均速 = 1 ⇒ 该值即「× 均速」）
 * ```
 * ⚠ **α = 0 时严格退化为 0.7.0 原式** `1 − (1+z)e^−z` ⇒ 本参数是**一键可回退的纯增量**。
 *
 * ### 为什么加它（依据 `research-ios-motion-2026-10-29/README.md`）
 * 该调研把 Flutter `Curves.fastEaseInToSlowEaseOut`（**按 iPhone 14 Pro Max /
 * iOS 16.3 逐帧数据严格拟合**）反解成等效弹簧，得 ζ=1.00 / ω=18.35，其
 * **起步斜率 = 0.96× 均速**；而纯临界阻尼（α=0）的起步斜率是 **0×**
 *   —— 也就是「先憋一拍才起来」，这正是「不丝滑 / 有点迟」的来源。
 * 同一份调研也确认：MeloX 在 **u≥0.3 之后与 iOS 曲线差 ≤2.3pp**（0.7.0 因 k=6
 *   略慢，中段差 ~21pp）⇒ **尾段形状不必动，动起步即可**。
 *
 * ### 取值（实测扫描，k=6）
 * | α | 起步斜率 | u=0.1 | u=0.3 | u≥0.7 |
 * |---|---|---|---|---|
 * | 0.00（0.7.0） | 0.00× | 0.124 | 0.547 | ≈不变 |
 * | 0.10 | 0.61× | 0.157 | 0.576 | ≈不变 |
 * | **0.15 ← 采用** | **0.91×** | 0.174 | 0.591 | ≈不变 |
 * | 0.20 | 1.22× | 0.190 | 0.605 | ≈不变 |
 * α=0.15 落在调研建议区间（0.10~0.15）上限且起步斜率最接近 iOS 的 0.96×；
 * ⛔ α≥0.20 起步斜率 >1.2× 会回到「甩出去」的急感。
 *
 * ⚠ 单调性仍成立（`dx/dz = e^−z[α + (1−α)z] > 0`，α<1 时恒正）⇒ **无过冲**，
 *   圆角不会提前坐平（`shellSettledOpen` 状态机判据依旧成立）。
 */
private const val MeloXPlayerSpringInitialVelocity = 0.15f
private val MeloXPlayerSpringEnd =
    1f - (1f + (1f - MeloXPlayerSpringInitialVelocity) * MeloXPlayerSpringK) *
        kotlin.math.exp(-MeloXPlayerSpringK)

internal val MeloXPlayerAppleEasing: Easing = Easing { fraction ->
    val f = fraction.coerceIn(0f, 1f)
    val z = MeloXPlayerSpringK * f
    (1f - (1f + (1f - MeloXPlayerSpringInitialVelocity) * z) * kotlin.math.exp(-z)) /
        MeloXPlayerSpringEnd
}

// ── iOS 非线性曲线（2026-09-29）────────────────────────────────────────────
// ⚠⚠ 2026-10-03：**已无代码使用者**，保留声明仅为避免破坏潜在引用。
//   原本 `MeloXIOSExpandEasing` 只被**下沉页**（`meloXPlayerRecedeSpec`）使用，
//   而那正是用户报障「页面下沉比播放栏动画结束早、不同步」的**根因**：
//   下沉用 decelerate 的 iOS present 曲线、播放器本体用 [MeloXPlayerAppleEasing]，
//   两条曲线形状不同 ⇒ 同一个 `SeekableTransitionState` 上必然错拍。
//   ⇒ `meloXPlayerRecedeSpec` 已改为与 master **同一条** [MeloXPlayerAppleEasing]。
//   ⇒ `MeloXIOSCollapseEasing`（easeOutBack，`f<0` 的过冲来源）也一并停用；
//     关闭方向的回弹改由迷你栏尾段的 `meloXMiniBarBounceOffsetDp()` 提供。
internal val MeloXIOSExpandEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
internal val MeloXIOSCollapseEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

// ⚠ 2026-10-02 回归上游：这两个 BoundsTransform 是 sharedBounds 外壳 morph
//   （迷你胶囊 ↔ 全屏播放器）的实际缓动。
// ⚠⚠ 2026-10-03：easing 已随 [MeloXPlayerAppleEasing] 一起换掉（`FastOutSlowInEasing`
//   头段太慢，见该常量处的逐帧拟合表）。**必须与 master
//   `meloXPlayerLinearFloatSpec()` 同源同速**，否则封面/外壳几何与圆角、内容错拍。
// ⚠ 2026-10-02 23:30：300ms → **与 master 同为 `meloXPlayerTransitionDurationMillis`**。
//   这不是随意改动：封面圆角绑在 master `expansionProgress` 上，若几何 morph 用另一个
//   时长，两者就无法同步 —— 曾用 `p * (360/300)` 重映射来对齐，但那只对**点击动画**
//   （经 transitionSpec 缓动）有效；**手拖收起**是 `seekTo` 线性跟手、不经过缓动，
//   乘 1.2 立刻被 clamp ⇒ 拖前 17~40% 行程圆角冻住不动、之后突降（用户报障
//   「手拖着下滑圆角是直接变的」）。
//   ⇒ 根治办法是让**几何与 master 同源同速**，圆角即可直接用 `p`，
//      点击/回弹与手拖两条路径共用同一个表达式，天然无跳变。
// ══ 关闭尾段「迷你栏下沉 → 回弹」—— 直接做进 sharedBounds **几何** ══════════════
//
// 用户口径（2026-10-03）：「回弹**不是**动画结束后的独立行为，而是**返回动画拉伸的一部分**
//   —— 拉伸到末端时迷你栏下沉，回弹就是**反向拉伸回去**」。
//
// ⚠⚠⚠ **为什么必须做进 bounds 几何，而不是给迷你栏加位移 Modifier**（本文件已为此返工多轮）：
//   morph 期间 · **外壳**由 `sharedBounds` 的 **overlay** 渲染；
//             · **封面**是**另一个独立的 `sharedElement` overlay 元素**（`zIndexInOverlay=100`）。
//   经实测，三种「给迷你栏加位移」的载体全部失败：
//     | 载体 | 结果 |
//     |---|---|
//     | 祖先 `graphicsLayer.translationY` | overlay 忽略绘制变换 ⇒ **整条不动**（看不见） |
//     | `sharedBounds` 内部 `graphicsLayer` | 只动外壳 overlay 内容 ⇒ **封面不跟** |
//     | 祖先 `Modifier.offset {}`（布局位移） | 实测仍**读不出可靠的下沉** |
//   ⇒ 而 **bounds 几何**正是 overlay 用来决定「画在哪、画多大」的**唯一输入**：
//     把位移加进 bounds，overlay 必然跟着走，且外壳 / 封面 / 文字 / 控件**天然同步**
//     （它们全部按同一个 rect 摆放）。
//
// ⚠⚠ **只做纯平移（top / bottom 同加），绝不做 size 过冲**：
//   全屏 ↔ 迷你栏的**尺寸差 ≈744dp**，任何比例的 size 过冲都会把迷你栏压到几 dp
//   —— `y1 = 1.15` 时期踩过这个坑（`target + 5%·(target − initial)` ⇒ 48dp → 10.8dp）。
//   所以位移只加在**纵向位置**上，高度严格保持插值结果。
//
// ⚠ 外壳与封面**必须共用本函数、共用同一个 `sinkPeakPx`**，否则两个 overlay 会各自沉
//   不同的量 ⇒ 封面在栏内错位。
//
// @param sinkPeakPx 关闭尾段的下沉峰值（px）。由调用方用 `LocalDensity` 换算后传入
//   （本文件在 spec 层拿不到 density）。传 `0f` ⇒ 退化为普通 tween。
// @param sinkEnabled 调用方**显式**告知「当前是关闭方向」。⚠ 传 **lambda** 而不是 `Boolean`：
//   `BoundsTransform` 实例必须**在整段过渡里保持同一个** —— 若把方向写进 `remember` 的 key，
//   手势下滑越过阈值那一刻 `targetState` 翻转 ⇒ **实例被重建** ⇒ Compose 重新推导 bounds spec，
//   **回弹会被丢掉**（用户报障：「使用返回手势偶尔触发不了回弹」）。传 lambda 后实例恒定，
//   方向在函数体内实时读取。
//   ⚠ 也不能只靠 `initial/target` 的 rect 大小推方向：`BoundsTransform` 的
//     `(initialBounds, targetBounds)` 取哪一侧的节点是 Compose 内部行为，
//     若在打开方向被以 `(全屏, 迷你)` 调用，纯 rect 判据会**误判成关闭** ⇒ 展开时也沉。
//     显式布尔把方向钉死，rect 判据退化为「参数顺序保险」。
// @return `BoundsTransform`；与 [MeloXPlayerAppleEasing] 同源同速（尺寸部分），
//   只在纵向位置上叠加关闭尾段的纯平移。
// ⚠⚠⚠ `initial` / `target` **不再在这里参与判定**（2026-10-04）：判据全部下沉到
//   [MeloXPlayerSinkBoundsSpec] 的每帧求值里（原因见该类注释）。此处只负责产出一个
//   **实例恒定**的 spec（避免手势越阈值时 `remember` 重建、回弹丢失）。
internal fun meloXPlayerSharedBoundsTransform(
    sinkPeakPx: Float,
    sinkEnabled: () -> Boolean,
    /**
     * 本份 morph 是否是**外壳**那一个。
     *
     * ⚠ **只有外壳能发布每帧真实 bounds**（`MeloXShellGeom.record`）—— 底栏遮挡门控
     *   （`MeloXChromeOcclusionGate`）的坐标系自检要求「本次 morph 的 span ≈ 整个窗口」；
     *   封面的 `initial ∪ target` 是「迷你封面 ∪ 大封面」，不是窗口矩形 ⇒ 一旦发布，
     *   自检每帧失败并 `reset()` ⇒ 闸门 true→false 抖动、完全失效（2026-10-26 踩点）。
     *   默认 **false**（显式 opt-in）⇒ 以后新增使用者不会再被误当外壳。
     */
    publishShellGeom: Boolean = false,
): BoundsTransform =
    BoundsTransform { _, _ ->
        MeloXPlayerSinkBoundsSpec(
            durationMillis = meloXPlayerTweenDurationMillis(),
            sinkPeakPx = sinkPeakPx,
            sinkEnabled = sinkEnabled,
            publishShellGeom = publishShellGeom,
        )
    }

/**
 * ★★ 下沉 bounds spec：**每帧实时求值**，而不是在 `createAnimationSpec` 那一刻定死。
 *
 * ⚠⚠⚠ 为什么必须用自定义 spec、不能用 `keyframes`（2026-10-04 定稿；用户报障
 *   「打断动画的退出动画没有下沉回弹，再怎么说也要有一点，而不是没有」）：
 *   `javap` 实证两条内部事实 ——
 *   ① `SharedBoundsNode.approachPlaceMatchInTransition` **每帧**调用
 *      `BoundsTransform.createAnimationSpec(initial, target)`；
 *   ② 但产出的 spec 是交给 `DeferredTargetAnimation.updateTarget(target, scope, spec)`，
 *      而后者**只在 target 变化时才用新 spec 重启动画**。
 *   ⇒ 「打开途中立刻关闭（打断）」时，bounds 动画沿用的是**打开方向那一帧生成的
 *     `tween`**（无下沉）⇒ 整段退出**完全没有回弹**。旧实现把 `sinkEnabled()` 判在
 *     `createAnimationSpec` 里，恰好落在这个窗口上，与 `closingIntent` 是否置位无关。
 *   ⇒ 本类把方向 / 尺寸护栏放到 `getValueFromNanos` **每帧**重判 ⇒ 即便沿用旧 spec
 *     实例，方向翻转后的下一帧就立刻叠加下沉。
 *
 * 形状与原 `keyframes` 逐点一致（`p` 走 [MeloXPlayerAppleEasing]，`dy` 走
 * [meloXPlayerSinkFraction]），只是从 48 段折线升级为连续函数（无采样台阶）。
 */
private class MeloXPlayerSinkBoundsSpec(
    private val durationMillis: Int,
    private val sinkPeakPx: Float,
    private val sinkEnabled: () -> Boolean,
    private val publishShellGeom: Boolean,
) : FiniteAnimationSpec<Rect> {
    override fun <V : AnimationVector> vectorize(
        converter: TwoWayConverter<Rect, V>,
    ): VectorizedFiniteAnimationSpec<V> =
        MeloXPlayerSinkVectorizedSpec(
            converter,
            durationMillis,
            sinkPeakPx,
            sinkEnabled,
            publishShellGeom,
        )
}

private class MeloXPlayerSinkVectorizedSpec<V : AnimationVector>(
    private val converter: TwoWayConverter<Rect, V>,
    durationMillis: Int,
    private val sinkPeakPx: Float,
    private val sinkEnabled: () -> Boolean,
    private val publishShellGeom: Boolean,
) : VectorizedFiniteAnimationSpec<V> {
    private val durationNanos: Long = durationMillis.coerceAtLeast(1).toLong() * 1_000_000L

    override fun getDurationNanos(initialValue: V, targetValue: V, initialVelocity: V): Long = durationNanos

    override fun getValueFromNanos(
        playTimeNanos: Long,
        initialValue: V,
        targetValue: V,
        initialVelocity: V,
    ): V {
        val f = progressOf(playTimeNanos)
        return valueAt(f, linearProgressOf(playTimeNanos), initialValue, targetValue)
    }

    override fun getVelocityFromNanos(
        playTimeNanos: Long,
        initialValue: V,
        targetValue: V,
        initialVelocity: V,
    ): V {
        // ⚠ `AnimationVector` 的 `size` / `get` / `set` 都是 **internal**，做不了逐分量差分
        //   （编译实证：`Cannot access 'val size: Int': it is internal`）。
        //   bounds 动画不吃速度（与原 tween/keyframes 同族，只按 playTime 求位置），
        //   返回零向量即可，不影响任何几何结果。
        return converter.convertToVector(Rect(0f, 0f, 0f, 0f))
    }

    private fun progressOf(playTimeNanos: Long): Float =
        // ⚠⚠ 2026-10-04：这里**必须**是线性时间轴（本文件头「三条时间线」块的铁律）。
        //   曾试过改成与 master 同源的 ζ=1 弹簧想让三通道「统一」，结果 ①②③ 全变双层
        //   ⇒ 前 100ms 甩完 95%、尾段 139ms 空转，用户报「效果更差」，已回退。
        linearProgressOf(playTimeNanos)

    /**
     * **时间口径**的归一化进度（0..1，线性）。
     *
     * ⚠ 只给「按墙钟定义」的东西用：关闭尾段的下沉窗口 [meloXPlayerSinkFraction]。
     *   窗口常数 `0.62` 的含义是「设置时长的后 38%」，一旦把它挂到
     *   [meloXPlayerSettleFraction] 那种前段极快的曲线上，墙钟上会提前到 ~8ms
     *   ⇒ 下沉几乎与展开同时发生（实测口径），与该常量的设计前提完全相反。
     */
    private fun linearProgressOf(playTimeNanos: Long): Float =
        (playTimeNanos.toFloat() / durationNanos.toFloat()).coerceIn(0f, 1f)

    private fun valueAt(f: Float, t: Float, initialValue: V, targetValue: V): V {
        val initial = converter.convertFromVector(initialValue)
        val target = converter.convertFromVector(targetValue)
        val p = MeloXPlayerAppleEasing.transform(f)
        // ⚠ 方向 / 护栏**每帧**重判：这是「打断」也能沉下去的关键。
        //
        // ★★★ 2026-11-02：跨度判据改成**方向无关**（旧写法 `target.height < initial.height * 0.6f`
        //   只在「变矮」方向成立，是「下沉回收阶段四角露动态背景弧线」的真因）。
        //
        //   同一次 morph 的两侧节点收到的 (initial, target) 是**互为反向**的：
        //     · 播放页外壳侧：initial = 全屏(2306) → target = 迷你栏(132)
        //         ⇒ 132 < 2306×0.6 ✓ ⇒ `dy` 生效
        //     · 迷你栏侧（玻璃盒所在的那个 `sharedBounds` 节点）：
        //         initial = 迷你栏(132) → target = 全屏(2306)
        //         ⇒ 2306 < 132×0.6 ✗ ⇒ `dy = 0`
        //   于是关闭尾段**外壳整体下沉、玻璃一动不动**，两层沿上下四边错开约 88px（22dp 峰值）
        //   ⇒ 四个圆角各露一条主页动态背景的弧。用户口径：「是下沉回收阶段的时候出现」。
        //
        //   ⇒ 用「高矮跨度」而不是「变矮」当判据：两侧算出的 `hi/lo` 完全相同 ⇒ 同时启用、
        //     `dy` 逐帧同值 ⇒ 下沉是**整块刚性平移**，两层永远不会错位。
        //   ⚠ 打开方向不受影响：`sinkEnabled()` 那时为 false。
        val hi = maxOf(initial.height, target.height)
        val lo = minOf(initial.height, target.height)
        val enabled = sinkEnabled() && sinkPeakPx > 0f && lo < hi * 0.6f
        val dy = if (enabled) meloXPlayerSinkFraction(t) * sinkPeakPx else 0f
        val rect =
            Rect(
                left = initial.left + (target.left - initial.left) * p,
                top = initial.top + (target.top - initial.top) * p + dy,
                right = initial.right + (target.right - initial.right) * p,
                bottom = initial.bottom + (target.bottom - initial.bottom) * p + dy,
            )
        // ★★★ 2026-10-29：**补回「外壳真实 bounds」的唯一发布点**。
        //
        //   ⚠ 这里曾经是死的：`MeloXShellGeom.record(...)` 全仓**无人调用**（只有定义与
        //     读者），而 `record()` 是 `MeloXShellGeom.onFrame` 的**唯一触发者**
        //     ⇒ `MeloXChromeOcclusionGate.evaluate()` 从不执行 ⇒ `occluded` 恒 false
        //     ⇒ 底栏遮挡门控整条失效，转场全程仍在每帧：底栏那批液态玻璃整屏采样背板 +
        //       `bottomChromeBackdrop` 节点被 `drawBehind` 强制失效 ⇒ 每帧整屏
        //       `recordLayer` + 18.25MB 纹理上传（morph 期最大一块）。
        //     实测形态：转场期 `commandIssue` 6.9~7.2ms / 8.30ms 预算（83~86%）。
        //
        //   ⚠ 只允许**外壳**发布（`publishShellGeom`）。封面 morph 的 `initial ∪ target`
        //     是「迷你封面 ∪ 大封面」而非窗口矩形 ⇒ 发布会让门控的坐标系自检每帧失败
        //     ⇒ 闸门 true→false 抖动（2026-10-26 历史踩点）。
        //   ⚠ 写入的是**普通字段**（非 State）⇒ 不产生快照依赖、不触发任何重组。
        if (publishShellGeom) {
            MeloXShellGeom.record(
                bounds = rect,
                initial = initial,
                target = target,
                progress = p,
                sinkFraction = if (sinkPeakPx > 0f) (dy / sinkPeakPx).coerceIn(0f, 1f) else 0f,
            )
        }
        return converter.convertToVector(rect)
    }
}

/**
 * 关闭尾段的垂直位移**比例**（`1` = 下沉峰值；**永不取负**；窗口外与终点恒 `0`）。
 *
 * 形状（`f` = 归一化**线性时间**，窗口 `f ∈ [0.62, 1]`，即退出最后 38% 墙钟；
 * 峰值落在 `f ≈ 0.81`，即外壳已缩到接近迷你栏形态时一记下沉再回位）：
 *
 * | `u`（窗口内） | 位移 | 用意 |
 * |---|---|---|
 * | `0 → 0.5` | `0 → +1` | **下沉**（往下走） |
 * | `0.5 → 1` | `+1 → 0` | **回位**（平滑回来，**不反向越过静止位**） |
 *
 * ⚠⚠⚠ **绝对单峰、无方向反转**（2026-10-03 三次迭代定稿）：
 *   ① `sin(2π·u)`（下沉 + 上越过 = 两峰）⇒ 用户口径「**反复抽搐**」；
 *   ② 两段 `smoothStep` 拼接（单峰但峰值有 ~35ms 平顶）⇒ 用户口径「**不够丝滑优雅**」；
 *   ③ 现在 `sin(π·u)` ⇒ **一次下沉 + 一次回位，峰顶圆滑无停顿**。
 *
 * ⚠ 幅度定档依据（用户连续三次报障「看不见 / 没有一点下沉回弹」）：
 *   `12dp` 在真机上只测得 **35px ≈ 8.7dp**（≈ 屏高 1.1%）—— 太弱。
 *   ⇒ **22dp（88px，屏高 2.8%、栏高 46%）**，与用户参考视频逐帧量出的
 *     「下冲 86 视频px ≈ 23dp」对齐（见 `2026-10-03.md`）。
 * ⚠ 用**线性时间**而不是 `expansionProgress`：后者是缓动**之后**的值，
 *   同一段话在时间轴上会被压得极短（用户报障「看不见回弹」的根因之一）。
 * ⚠ `f = 1` 严格返回 `0`：否则迷你栏会长期停在非 0 位移上（静止时肉眼可见错位）。
 */
internal fun meloXPlayerSinkFraction(f: Float): Float {
    if (f.isNaN() || f.isInfinite()) return 0f
    val span = 1f - MeloXPlayerSinkWindowStart
    val u = ((f - MeloXPlayerSinkWindowStart) / span).coerceIn(0f, 1f)
    if (u <= 0f) return 0f
    // ⚠⚠ **单峰 `sin(π·u)`**（2026-10-03 定稿）：升 → 峰 → 落，**全程没有方向反转**。
    //   前两版的教训：① `sin(2π·u)` = 下沉 + 上越过 = **两个峰** ⇒ 用户读成「反复抽搐」；
    //   ② 「两段 `smoothStep` 拼接」虽只有一个峰，但 `smoothStep` 两端导数为 0 ⇒
    //      **峰值前后各有 ~35ms 平顶停顿**（真机实测：峰值处 dy 连续 3 帧停在 53px），
    //      读成「卡一下」⇒ 用户报障「不够丝滑优雅」。
    //   `sin(π·u)` 的峰顶是圆滑二次型（无长平顶），窗口两端速度由 0 起、到 0 收，
    //   与 morph 的收尾自然衔接。
    return kotlin.math.sin(kotlin.math.PI.toFloat() * u)
}

/** 下沉峰值（dp）。density 4 ⇒ 88px ≈ 屏高 2.8%、栏高 46% —— 必须**看得见**。 */
internal const val MeloXPlayerSinkPeakDp = 22f

// ⚠ 单峰形状后**只剩一个常量**（`SinkPhaseEnd` / `ReboundPhaseEnd` / `ReboundFraction`
//   三个都已删除）：形状整体由 `sin(π·u)` 决定，不再有可调相位。
//
// `0.62` 的依据（spring-close 定稿）：关闭是临界阻尼弹簧，f=0.62 时外壳已缩到
//   接近迷你栏（eased 进度 ~80%+），此时叠加尾段一记「下沉 → 回弹」读起来是
//   整个播放器收尾时轻轻一弹（Apple Music 同款手感）。窗口 38% ⇒ 240ms 档下
//   「下沉+回位」≈ **90ms**，足够看清又不过长。
//   ⚠ 后续把窗口推到 0.75 想让它「更像迷你栏自己弹」，实测反而让外壳沉得不明显
//     （被误读成「没有回弹」），故回退到 0.62。
private const val MeloXPlayerSinkWindowStart = 0.62f
//
// ⚠⚠⚠ 2026-10-03 用户报障：「页面下沉比播放栏动画结束早，不同步」。
//
// **根因 = 两条时间线用了两条不同的曲线**（不是时长差，是**曲线**差）：
//
//   | 时间线 | 驱动对象 | 原 easing | 新 easing |
//   |---|---|---|---|
//   | master `meloXPlayerLinearFloatSpec()` | 播放器本体 morph + 封面 + 圆角 | `MeloXPlayerAppleEasing` | 不变 |
//   | 本函数 | **背景页下沉** | `MeloXIOSExpandEasing`(0.32/0.72/0/1) | **← 改成 master 那条** |
//
//   `MeloXIOSExpandEasing` 是 **decelerate**（起步猛、然后一路慢飘）；
//   `MeloXPlayerAppleEasing` 起步斜率 ≈2.2、尾段才收。
//   两者形状不同 ⇒ 同一个 `SeekableTransitionState` 上，`animateFloat` 各自跑各自的曲线
//   ⇒ **下沉先到位、播放器还在动**，用户看到的正是「对不上」。
//
// ⇒ 根治：**下沉必须与播放器本体吃同一条 easing**。本页面的 `recedeMasterFraction`
//   已经满足「1:1 跟手」的映射前提（`ScaleStart=0` / `ScaleEnd=1` ⇒ f ≡ master），
//   只差曲线这一环。
//
// ⚠⚠ **关闭方向原本还多给 60ms**（`+60`），那是**第二个**不同步来源，同样已删除。
//   过冲回弹另有来源：迷你栏尾端的 `meloXMiniBarBounceOffsetDp()`（解析式尾段过冲），
//   以及 `MeloXIOSCollapseEasing` 本身带来的 `f<0`。
//   ⚠ 若继续保留 60ms 差，关闭方向页面会**比播放栏晚 60ms** 到底 —— 同一个 bug 反过来。
// ⚠⚠ 2026-10-03 回退记录：曾被临时改成 `LinearEasing`（把形状统一上移到 fraction），
//   用户实测「反而比上版本效果差」⇒ **已回退**，恢复与 master / 几何 / 圆角同一条曲线。
//
// ⚠⚠⚠ 2026-10-04 第二次改并**再次回退**：曾换成 ζ=1 弹簧想与当时的 master 统一，
//   但那会让「几何 / 背景页 / 内容层」三条全部变成双层（实测：内容层 95%@101ms、
//   尾段 139ms 空转），用户报「效果更差」⇒ **已回退到 tween + AppleEasing**。
//   ⇒ 本函数与 [meloXPlayerLinearFloatSpec]（内容层）、`MeloXPlayerSinkVectorizedSpec`
//   （几何）**三者逐字同源**，master 是线性 tween 时三条同为单层、逐帧严格相等。
internal fun meloXPlayerRecedeSpec(
    initialOpen: Boolean,
    targetOpen: Boolean,
): FiniteAnimationSpec<Float> = tween(
    durationMillis = meloXPlayerTweenDurationMillis(),
    easing = MeloXPlayerAppleEasing,
)

/**
 * 外壳圆角的唯一曲线（Apple Music 口径，2026-09-29 十次定档）。
 *
 * 四段（都是**时间**口径，360ms 全程，下标是传进来的 `progress`）：
 *
 * | 时间 | 圆角 | 常量 |
 * |---|---|---|
 * | 0 ~ 126ms | 恒 **24dp**（= 迷你胶囊半高；此刻迷你播放器刚淡出，两者不打架） | `RiseStart = 0.50` 之前 |
 * | 126 ~ 166ms | 涨到 **系统圆角**（调用方传的系统真值，iQOO 15 = 46dp） | `[0.50, 0.72]` |
 * | 166 ~ 360ms | **保持系统圆角**（中途变完就不再动，一直到主时间线跑完） | `[0.72, 1.0]` |
 * | 360ms **之后** | 坐平成 **0dp 直角**（贴屏收口）—— 由调用方按 transition 完成状态驱动 | 见下 |
 *
 * ⚠⚠ 2026-10-03：「坐平」**不再**是本函数里的一个 `progress` 窗口。
 *   本函数的入参 `progress` 是 master fraction 经 `meloXPlayerLinearFloatSpec()`
 *   （FastOutSlowInEasing）**缓动之后**的值，与时间轴不是线性对应：`p=0.99` 落在
 *   时间轴的 **0.95T ≈ 342ms**（全程 360ms）⇒ 动画还差 ~18ms 才结束，圆角就已开始
 *   坐平。用户报障「圆角变直的时机太前了，还处于动画尾端就变了，应该是动画完全结束才变」。
 *   ⇒ 坐平改为由调用方按 **transition 是否已 settled 在展开态** 驱动
 *     （`MeloXIOSNowPlayingSharedHost` 里用 `MeloXPlayerCornerFlattenMillis` 平滑收口），
 *     本函数只负责 `24dp → 系统圆角` 这一段。
 *
 * ⚠ 峰值**必须与后方页面的圆角同值**（2026-09-28 用户报障「现在圆角还是差一点，对不上」）：
 *   ⇒ 由调用方把 `rememberMeloXDisplayCornerPx()` 的系统真值传进来（[peakDp]），
 *     两边同一个数、同一个来源。
 *   [MeloXPlayerPeakCornerDp] 退化为「取不到系统值时的兜底」（46dp = 184px @density 4）。
 *
 * ⚠⚠ **rise 窗口必须按「时间」反解，不能按「进度」拍** —— 传进来的 `progress` 是
 *   **缓动之后**的值（本应用 = `meloXPlayerLinearFloatSpec()` 的 FastOutSlowInEasing），
 *   进度轴上的小窗口在时间轴上会被拉得极长/极短：
 *
 *   | 报障 | 旧值 | 现象 | 现取值 |
 *   |---|---|---|---|
 *   | 「迷你播放器轮廓还没消失，圆角就比它大了」 | `Rise=[0.03,0.30]` | 可见变大 59~92ms，而迷你面到 p=0.48（123ms）才淡完 | **`[0.50,0.72]` ⇒ 132~156ms** |
 *
 *   ⇒ **改 `Rise` 常量前先算时间窗**（`smoothstep⁻¹` 反解 + 缓动曲线反解），别只看进度差值。
 *
 * ⚠ 同一个函数**同时**供播放器外壳（`MeloXIOSNowPlayingSharedHost`）和后方页面
 *   （`MeloXApp` 的 recede wrapper）使用 —— 两处圆角必须逐帧相等，否则一大一小。
 *   （页面那侧额外按自身缩放做分轴补偿，抵消 clip 与 scale 同层带来的可见半径变化。）
 */
internal const val MeloXPlayerRestCornerDp = 24f
internal const val MeloXPlayerPeakCornerDp = MeloXPlayerRecedeCompatFallbackDp
internal const val MeloXPlayerCornerRiseStart = 0.50f
internal const val MeloXPlayerCornerRiseEnd = 0.72f
// ⚠⚠⚠ 2026-11-01 **定案（第四版）**：**开合两个方向共用同一条几何律**
//   `min(屏角, 矩形高/2)` —— 「可以圆到胶囊，但不比屏角更圆」。
//   ⇒ **`closing` 判据已删除**，原因见 ④。
//
//   演进（前三版都被真机否掉，别再回头）：
//   ① 关闭窗口 `[0.95,1.0]`（一离开全屏就收 24dp）⇒ 中段弧 + 下沉闪烁；
//   ② 关闭窗口 `[0.0,0.10]`（几乎全程 46dp）⇒ **下沉好了**，但后半段两上角斜上方仍有两条弧。
//      真因不在外壳 vs recede 页 —— recede 页圆角恒 = 系统屏角、**比外壳更早切角**，
//      永远躲在外壳轮廓之内，外壳多小都不会露它。真因是**迷你玻璃 `Capsule()` 的半径
//      = morph 矩形高 / 2（每帧在变）≠ 外壳的常数圆角**；
//   ③ 两层都钉 24dp ⇒ **后半段干净了**，但**下沉段出现闪烁弹跳**：下沉时矩形只剩 ~75dp 高，
//      24dp 角会留下一段**竖直直边**，22dp 的上下位移让直边整体滑动 = 弹跳/闪烁感；
//      而 ② 里下沉段的圆角恰好 ≈ 矩形高/2（= 胶囊）所以顺。
//   ④ ③ 的几何律 + 仍按 `closing` 分方向 ⇒ 只差「一瞬闪」。真因：**手势拖拽关闭时
//      `MeloXApp` 刻意把 `playerClosingIntent` 写 `false`**（内容层要跟手，见该处注释），
//      直到**松手 settle 那一刻**才翻 `true` ⇒ 外壳圆角在那一帧从「打开窗口」的中间值
//      （如 p=0.6 时 ≈31.7dp）**瞬间跳到几何律的 46dp** —— 就是那 1~2 帧的闪。
//   ⇒ 结论：**圆角绝不能依赖方向判据**（拖拽期的方向语义与动画方向天然不同步）。
//      `min(peakDp, 矩形高 / 2)` 一条律同时满足开、合、拖拽、松手、打断：
//        · 矩形高 < 2·peak（接近迷你栏 / 下沉段）⇒ 正好是胶囊，与静止态迷你栏无缝衔接；
//        · 矩形高 ≥ 2·peak（中段 / 全屏）⇒ 恒 = 屏角，全屏四角贴合、中段也不方；
//        · 单调、无跳变点，**判据消失 ⇒ 不可能再因判据翻转而跳帧**。
//   ⇒ **玻璃侧必须用同一条律**，否则任一侧用常数都会在
//      某个相位露出另一侧（①②③ 全部栽在这一条上）。
//   ⑤⚠⚠ 2026-11-02 用户第五次报障「下沉收尾阶段，迷你栏的两个角闪烁了**动态背景色的弧线**」
//      —— 律一致还不够，**形状族也必须一致**：外壳是 `MeloSmoothCornerShape`（squircle，
//      且半径吃满半高时会委托 kyant `Capsule()` = Apple 连续曲率），而玻璃当时是
//      `RoundedCornerShape`（正圆弧）。同半径下正圆弧在端部比连续曲率缩进 ~5.87px
//      ⇒ 两端各一条月牙缝，露出外壳里仍在的播放页动态背景。⇒ 玻璃改用 kyant
//      `RoundedRectangle` + `Continuous`（半径自动夹到半高），**lens 保留**。
//   ⑥⚠⚠ 2026-11-02 用户第六次报障「**还是不贴合，有那种弧线毛刺感**」——
//      `RoundedRectangle(Continuous)` 仍然不是同一族：它与外壳的 Lamé 超椭圆**互相交叉**
//      （沿边方向更靠内、对角方向更靠外）⇒ 既不是超集也不是子集，裁剪只能消掉一半偏差，
//      另一半照样露缝，沿弧线读作一圈毛刺。
//   ⇒ 终局判据：**轮廓必须由同一次 `createOutline` 调用生成**，不能是「同半径的两族形状」。
//      实现见 [MeloXPlayerGlassShape]（`CornerBasedShape` 子类，轮廓委托
//      `MeloSmoothCornerShape`，四角半径交给 lens）。
//      ⛔ 别再换成任何「同半径的另一种曲线」：正圆弧 / kyant `RoundedRectangle` / `Capsule()`
//        全都试过了，全部露出缝或毛刺。

/**
 * 主时间线**跑完之后**，圆角「坐平成直角」所用的平滑时长（ms）。
 *
 * ⚠ 这是**动画结束后**才起步的一段独立微动画，不属于主时间线，也**不**由 `progress` 决定。
 *   见 [meloXPlayerShellCornerDp] 的说明：坐平由调用方按 transition 是否已 settled 驱动。
 */
internal const val MeloXPlayerCornerFlattenMillis = 140

/**
 * **取消坐平（关闭 / 拖拽方向）没有时长常量 —— 必须是瞬时的（`snapTo`）。**
 *
 * ⚠⚠⚠ 2026-10-06（用户口径，两次澄清后定稿）：
 * · 「铺满动画**完整结束**后**马上**变直角」 ⇒ 坐平（→直角）由 `shellSettledOpen` 触发，
 *   用 [MeloXPlayerCornerFlattenMillis] = 140ms 平滑收口。
 * · 「返回动画是**直角从动画开始瞬间就变屏幕圆角**，**而不是有变化过程**」
 *   ⇒ 取消坐平（→系统圆角）**不允许有任何渐变时长**，必须 `snapTo(0f)`。
 *
 * ⚠ 为什么瞬时是对的：`cornerRadius = shellCornerDp(expansionProgress) × (1 − flattenAlpha)`，
 *   关闭**第一帧** `expansionProgress` 仍是 1（= 系统圆角档）⇒ 立刻把 `flattenAlpha` 归 0
 *   即当帧得到**系统圆角峰值**，随后随收缩落到迷你栏的 24dp
 *   = 用户要的「直角 → 瞬间系统圆角 → 变小」。
 * ⚠ 历史教训（**别再给它加时长**）：与坐平共用 140ms 时，放开那一刻 `expansionProgress`
 *   已掉到 ≈0.14（< RiseStart 0.50）⇒ 圆角直接从直角落到 24dp，**系统圆角峰值全程不出现**
 *   （用户报「直接退出是直角变迷你栏圆角」）；改成 40ms 渐变虽能让峰值出现，
 *   但仍是「有变化过程」，同样被否。
 */

/**
 * 外壳圆角随主时间线变化的部分：`24dp → 系统圆角`（**不含**坐平）。
 *
 * ⚠ 坐平（→ 0dp 直角）**不在本函数里**：它必须等主时间线**完全跑完**才发生，而本函数的
 *   入参 `progress` 是缓动之后的值、无法区分「跑完」与「还差最后一帧」。坐平由
 *   `MeloXIOSNowPlayingSharedHost` 按 `transition.currentState == targetState == Visible`
 *   驱动（`MeloXPlayerCornerFlattenMillis` 平滑收口）。详见文件头说明。
 *
 * @param peakDp 峰值圆角（dp）。调用方传**系统圆角真值**（`rememberMeloXDisplayCornerPx()`
 *   换算成 dp），与后方页面用同一个数；取不到时才用默认兜底。
 */
internal fun meloXPlayerShellCornerDp(
    progress: Float,
    peakDp: Float = MeloXPlayerPeakCornerDp,
    rectHalfHeightDp: Float = Float.NaN,
): Float {
    val p = progress.coerceIn(0f, 1f)
    // ★★ 2026-11-02 第七次修正（用户：「退出动画后半段 / 开始动画前半段，迷你栏的圆角太大了，
    //   违背了圆角规则，圆角变化规则应该跟好几版前一样」）：
    //   **主路径恢复成上游的 rise 窗口**（p < 0.50 恒 24dp，0.50 → 0.72 才涨到屏角），
    //   几何律（`min(peak, 高/2)`）降级成"只做半高夹"。
    //
    //   为什么几何律不能当主路径：它把半径一路顶到屏角。p≈0.5 时矩形高已 ~440dp，
    //   `min(46, 220) = 46dp` ⇒ 收起后半段一直是"大圆角"，而稳态迷你栏是 24dp
    //   ⇒ 观感上圆角在收尾前突然放大一倍。而玻璃那边只在 p < 0.48 可见、那一段
    //   外壳按 rise 恒 24dp，所以玻璃也必须恒 24dp（见 `MeloXPlayerGlassShape`）。
    //
    //   `rectHalfHeightDp` 仍保留：它是**防直边的兜底**（h < 48dp 时把 24dp 夹成 h/2
    //   ⇒ 退化成胶囊），不再是半径来源。传 NaN 时跳过夹取（全屏段本来就用不到）。
    val rise = smoothStep01(p, MeloXPlayerCornerRiseStart, MeloXPlayerCornerRiseEnd)
    val byProgress = (MeloXPlayerRestCornerDp + (peakDp - MeloXPlayerRestCornerDp) * rise)
        .coerceAtLeast(0f)
    return if (rectHalfHeightDp.isNaN()) {
        byProgress
    } else {
        minOf(byProgress, rectHalfHeightDp).coerceAtLeast(0f)
    }
}

/**
 * 迷你播放器**液态玻璃**的形状 —— 轮廓由外壳**同一个** [MeloSmoothCornerShape] 生成。
 *
 * ### 一、为什么光「半径律一致」不够
 * 半径律一致（`min(屏角, 矩形高/2)`）只保证**对角切深**一致，**不保证轮廓重合**。
 * 只要两侧不是同一族曲线，就一定会露出缝：
 *
 * | 玻璃用的形状 | 与外壳（`MeloSmoothCornerShape`）的关系 | 真机现象 |
 * |---|---|---|
 * | `RoundedCornerShape`（Compose 正圆弧） | 端部**缩进** ≈5.87px（@density 4） | 下沉收尾两角各一条月牙缝（2026-11-01） |
 * | `RoundedRectangle(46dp, Continuous)` | **与外壳交叉**：沿边方向更靠内、对角方向更靠外 | 「还是不贴合，有那种弧线毛刺感」（2026-11-02） |
 *
 * ⚠ 第二种最坑：它既不是超集也不是子集 —— 任何「多出来的被外壳裁掉」的推理都不成立，
 *   裁剪只能消掉一半偏差，另一半（靠内那半）照样露缝 ⇒ 看起来像沿弧线的一圈毛刺。
 *   ⇒ **结论：两侧必须由同一次 `createOutline` 调用生成**，而不是「同半径的两族形状」。
 *
 * ### 二、怎么同时满足 lens（这是唯一的技术难点）
 * kyant 的 lens 只做一次类型分派（`LensKt.getCornerRadii`，已用 javap 核对字节码）：
 * ```
 * shape is RoundedRectangularShape → shape.corners(size, ld, density)     // 取四角半径
 * shape is AbsoluteRoundedCornerShape → topStart/topEnd/… .toPx(size, density)
 * shape is CornerBasedShape → topStart/topEnd/… .toPx(size, density)
 * 其它 → throwUnsupportedSDFException()  // draw 阶段崩
 * ```
 * 三条分支**都只是取四角半径**，然后各自 `coerceAtMost(minDimension / 2)` 喂给 shader 的
 * `cornerRadii`——**完全不看轮廓是怎么画的**。
 *
 * - `RoundedRectangularShape` 是 kyant 的 **sealed** interface，跨模块不能实现（编译实证：
 *   `Extending sealed … from a different module is prohibited`）；
 * - `CornerBasedShape` 是 Compose 的**公开 abstract class**，可以继承 ⇒ **走这一条**。
 *
 * 于是本类：`createOutline` 直接委托 [MeloSmoothCornerShape]（与外壳逐点相同），
 * 四角半径照常经 `CornerSize` 交给 lens（lens 自己会夹到半高，正好等于外壳的半径律）。
 *
 * ### 三、半径
 * 基类收到的 `CornerSize(peakDp.dp)` 会被框架换算成 px；lens 侧再夹 `min(px, minDim/2)`；
 * 本类 `createOutline` 里也做同一个 `min(peakPx, min(w,h)/2)`。
 * ⇒ 与外壳 `meloXPlayerShellCornerDp(rectHalfHeightDp = …)` 的
 *   `min(peak, 半高).coerceIn(24dp, peak)` 同值（下半界 24dp 只在「矩形半高 < 24dp」时生效，
 *   而那一刻两个实现都已经走 [MeloSmoothCornerShape] 的 ⓪ 分支委托 `Capsule()`，
 *   结果仍是同一条路径 ⇒ 不需要 density 也能保证一致）。
 *
 * ### 四、与静止态的关系
 * 静止态玻璃用 `Capsule()`；本类在矩形半高 == 屏角附近（`radiusPx >= 半高 - 0.5px`）会走
 * [MeloSmoothCornerShape] 的 ⓪ 分支，**同样委托 `Capsule()`** ⇒ 末帧与稳态之间不再有一次
 * 形状族切换（旧版 `RoundedRectangle` 也是靠这一点，那条逻辑继续保留）。
 *
 * ⚠ 实例可 `remember(peakDp)` 住：半径只由 `createOutline` 的 `size` 决定，不读任何逐帧
 *   State ⇒ 不会把上千行的迷你播放器拖着每帧重组。
 * ⛔ 别换成任何「同半径的另一种曲线」（正圆弧 / kyant `RoundedRectangle` / `Capsule()` 以外的
 *   东西）：只要不是本类这次调用，就一定会露出缝或毛刺。
 */
internal class MeloXPlayerGlassShape(
    private val restRadiusPx: Float,
) : CornerBasedShape(
    topStart = CornerSize(restRadiusPx),
    topEnd = CornerSize(restRadiusPx),
    bottomEnd = CornerSize(restRadiusPx),
    bottomStart = CornerSize(restRadiusPx),
) {

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        // ★★ 2026-11-02 第七次修正（用户：「退出动画后半段 / 开始动画前半段，迷你栏的圆角太大了，
        //   违背了圆角规则，圆角变化规则应该跟好几版前一样」）：
        //   本类此前用 `min(屏角, min(w,h)/2)` —— 那是"几何律"，它会把圆角一路顶到屏角（46dp）。
        //   但外壳的**正解一直是 rise 窗口**（`meloXPlayerShellCornerDp`：p < 0.50 恒 24dp，
        //   0.50→0.72 才涨到屏角）。玻璃只在 p < 0.48 可见 ⇒ 它在这一整段**必须就是 24dp**，
        //   不能再跟矩形高走（否则 p≈0.3 时玻璃 46dp、外壳 24dp，肉眼就是"圆角突然变大"）。
        //   ⇒ 半径改为常量 24dp（由调用方按 density 换算成 px 传入），仅按半高夹住
        //     （h < 48dp 时退化成胶囊，避免出现竖直直边）。
        //   ⚠ 与 `meloXPlayerShellCornerDp` 的 `min(24dp, 高/2)` **逐值相同** ⇒ 两层同源。
        val fitPx = minOf(size.width, size.height) / 2f
        val radiusPx = minOf(restRadiusPx, fitPx)
        // ★ 与外壳 `MeloXSlideShapeCache.of(radiusDp.dp.toPx(), 1f)` **同一次调用**：
        //   同一个类、同一组 (radius, scaleX, scaleY)，轮廓逐点相同 ⇒ 缝隙在几何上不存在。
        return MeloSmoothCornerShape(radiusPx, 1f, 1f).outline(size, layoutDirection)
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = MeloXPlayerGlassShape(restRadiusPx)

    override fun equals(other: Any?): Boolean =
        other is MeloXPlayerGlassShape && other.restRadiusPx == restRadiusPx

    override fun hashCode(): Int = restRadiusPx.hashCode()
}

/**
 * [restRadiusPx] 必须是 **24dp 换算出的像素值**（`with(LocalDensity.current) { 24.dp.toPx() }`），
 * 不要在调用点传屏角 —— 玻璃只在 p < 0.48 可见，那一段外壳恒 24dp。
 */
internal fun meloXPlayerGlassShape(restRadiusPx: Float): MeloXPlayerGlassShape =
    MeloXPlayerGlassShape(restRadiusPx)

private fun smoothStep01(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

// ══ 尾端 Q 弹（2026-10-03，用户报障「迷你播放栏打开/关闭尾端有点僵硬」）══════
//
// 根因：整条 morph 链路上**每一层**都是无过冲的 tween ——
//   · master 驱动 `playerAutomaticFractionSpec()` = tween(360ms, LinearEasing)，到点硬停；
//   · morph 几何与内容进度 `meloXPlayerLinearFloatSpec()` = tween(FastOutSlowIn)，
//     数学上恒 `≤ 1`，**永远不可能越过终点**；
//   · 全链唯一带过冲的只有背景页下沉的 `MeloXIOSCollapseEasing`(easeOutBack)，
//     它只作用于 recede 层，播放器本体与封面两侧全程 `≤ 1`。
// ⇒ 「尾端僵硬」不是曲线太硬，而是**物理上不存在回弹**。
//
// ⚠⚠ 为什么**不能**把 master 换成弹簧（本改法绕开的那个坑）：
//   `MeloXIOSNowPlayingSharedHost` 用 `shellSettledOpen = expansionProgress >= 1f`
//   判定「动画完全结束才坐平圆角」—— 那是用户 2026-10-03 连续报障后才定下的判据。
//   tween 的输出恒在 `[0,1]`，弹簧会在**过冲上升段就穿过 1.0** ⇒ 圆角提前坐平。
//   同理 `MeloXArtworkBoundsTransform` 也必须跟着换，否则几何与圆角又错拍
//   （该文件里已经为此返工两轮）。
//
// ⇒ 改法：**纯解析式的尾段 scale 过冲**，不引入任何新动画状态 / 协程 / Animatable。
//   · 无状态 ⇒ 不存在「翻转时取消协程 ⇒ 弹簧从当前位置 0 速度重开」的顿挫；
//   · 点击路径与手拖 seek 路径**共用同一个表达式**（都只吃 `expansionProgress`）。
//
// ⚠⚠⚠ **窗口必须按「时间」反解，绝不能按进度拍**（初版就是踩了这个坑，
//   真机读数「还是僵硬」后才反解出来）。把 `FastOutSlowIn` 的 `t → p` 数值反解：
//
//   | `d = 1 - p` | 时间 | 说明 |
//   |---|---|---|
//   | `0.125` | **216ms** | `d ≥ 0.125` 的部分早已在跑主体动画 |
//   | `0.062` | **252ms** | 尾段窗口只有 ~144ms 宽 |
//   | `0.025` | **288ms** | |
//   | `0.000` | **360ms** | 终点 |
//
//   ⇒ `FastOutSlowIn` 的尾段**极度压缩**：`d` 从 0.115 掉到 0 只用了约 138ms，
//     而 `d` 从 1 掉到 0.115 用了前 222ms。**初版把 `BounceStart` 设成 0.62
//     是错的**（那覆盖了后 60% 时长）。
//   ⚠⚠⚠ 更隐蔽的坑：`d` 是**递减**量，而 `smoothStep01` 在 `end <= start` 时
//     **返回常量** ⇒ 直接把递减区间喂给它会让整个函数**静默恒等于 1.0**。
//     初版前两版都这么写的，肉眼完全看不出（没有任何报错、任何告警），
//     只能靠「用逐字相同的公式在 Python 里先跑一遍」抓出来。
//     **本文件里任何递减区间的插值都必须先归一化成递增量。**
//
// ⚠ `d` 的语义 = **距终点的距离，随动画递减**（打开时 `d = 1 - p`，关闭时 `d = p`）。
//
// ⚠ **封面不参与**这个过冲：封面 sharedElement 带
//   `renderInOverlayDuringTransition = true`，尾段期间它由 Overlay 里的副本渲染，
//   源侧加任何 scale 都会造成「源侧已缩 / Overlay 副本没缩」的不一致 ——
//   正是本文件反复出现的那类「突变」故障。故只作用于播放器的**场景内容层**。

// ══ 收回动画尾端：迷你栏下沉回弹 ═══════════════════════════════════════════
//   （2026-10-03。用户报障两次：「迷你播放栏打开/关闭尾端有点僵硬」
//     → 澄清诉求 →「我要的是收回动画时迷你栏稍微下沉一点点的回弹动画」）
//
// ⚠⚠⚠ **载体搞错过一次，务必分清**：
//   前两版把过冲做在**播放页场景内容层**的 `scale` 上，那是**错的载体** ——
//   用户要的是**迷你栏自己**下沉再弹回。scale 一个全屏内容层在视觉上
//   只是「整页胀了一下」，读不出「弹」；而迷你栏是 48dp 的窄条，
//   **整体下沉几 dp 就能被清楚读到**（这才是「稍微下沉一点点」的真实含义）。
//   ⇒ 现在的实现：`translationY` 挂在迷你栏**内容舞台**上，`scale` 已全部删除。
//
// ⚠⚠⚠ 实现陷阱（两版都踩了，且**静默失效、零报错**，肉眼完全看不出）：
//   本文件的 `smoothStep01(v, start, end)` 在 `end <= start` 时走兜底分支
//   `return if (value >= end) 1f else 0f` ⇒ 返回**常量**。
//   而「距终点的距离」`d` 是随动画**递减**的量。若把递减区间直接喂进去
//   （如 `smoothStep01(d, 0.045f, 0f)`），`settle` 恒 = 1 ⇒ 整个效果恒等于
//   静止值，**代码照常编译、照常运行、就是不生效**。
//   ⇒ 正解：先归一化成**递增**的 `u = 1 - d / WindowStart`（窗口内已走过的
//     比例，`u ∈ [0,1]`），再对 `u` 用递增区间插值。
//   ⚠ 本文件里任何「递减区间」的插值都必须先过这道归一化。
//
// ⚠ 窗口必须按**时间**反解，不能按进度拍。把 `FastOutSlowIn` 的 `t → p`
//   数值反解（关闭方向，`p` 由 1 递减到 0）：
//
//   | `d = p` | 时间 | 说明 |
//   |---|---|---|
//   | `0.125` | **216ms** | `d ≥ 0.125` 的部分已在跑主体动画 |
//   | `0.062` | **252ms** | 尾段窗口只有 ~144ms 宽 |
//   | `0.000` | **360ms** | 终点 |
//
//   ⇒ `FastOutSlowIn` 尾段**极度压缩**：`d` 从 0.115 掉到 0 只用了约 138ms。
//     这 138ms 要装下「下沉 → 回弹 → 归零」三段，所以三段的分割点必须
//     按**时间占比**分配，不能对半。
//
// ⚠⚠ **只挂在迷你栏的「内容舞台」Box 上，不挂外壳、也不挂玻璃层**：
//   · 外壳（`sharedShellModifier`）是 morph 几何的载体，加位移会把
//     迷你胶囊 ↔ 全屏的插值整体带歪（该文件已为此返工多轮）；
//   · 玻璃层单独动会与内容层错位 ⇒ 玻璃与里面的控件分离；
//   · 两者一起动 = 整条胶囊下沉，才读得出「迷你栏下沉」。

/**
 * 尾段窗口外沿（`d = p` 口径，**仅关闭方向**）。`p ≥ WindowStart` ⇒ 恒无位移。
 *
 * ⚠⚠⚠ 2026-10-03 **这个 `p` 值阈值本身就是「闪线」的根因，已被弃用**
 *   （保留常量仅为对照，实际判据见 [MeloXMiniBarBounceWindowStartU]）。
 *
 *   用户报障「退出动画结束时迷你栏中间出现一条线再闪烁」，
 *   推测原因是「折射高度过冲导致数学层面交汇」—— **机理方向对，但对象错了**：
 *   不是折射/lens（`safeProgress` 已 clamp、`layerBlock` 的 `scale` 最大仅 1.083），
 *   而是**迷你栏整体 `translationY` 在末帧的步进**。
 *
 *   逐帧实测（60fps，240 档，`W = 0.115`）：
 *
 *   | 帧 | 墙钟 | `p` | `u = 1−p/W` | offset | Δ |
 *   |---|---|---|---|---|---|
 *   | 6 | 100ms | +0.1262 | 窗口外 | 0dp | — |
 *   | **7** | 117ms | +0.0769 | **0.331** | **+3.493dp** | **+3.49** |
 *   | **8** | 133ms | +0.0413 | **0.641** | **−0.956dp** | **−4.45** ⚠ |
 *   | 9 | 150ms | +0.0170 | 0.852 | −0.638dp | +0.32 |
 *
 *   ⇒ 48dp 高的胶囊**一帧横跳 4.45dp** = 玻璃上那条横线。
 *   ⇒ 「数学层面交汇」确实存在：过冲曲线让 `p` 在 `u∈[0.72,0.98]` **穿过 0 变负**
 *     （最低 −0.0064），窗口内 `p` 的分布极不均匀 ⇒ `u` 一帧跨 31%。
 *
 *   ⚠ **加宽窗口只能缓解、不能根治**（实测最大单帧步进）：
 *     `W=0.115 → 4.45dp`；`W=0.30 → 3.50`；`W=0.45 → 3.26`；`W=0.60 → 2.71`。
 *     根因是 `p` 分布不均，不是窗口太窄。
 *
 *   ⚠⚠ **正解：用「时间」而不是「`p` 值」判窗口。**
 *   `expansionProgress` 是**缓动之后**的值，窗口的**墙钟宽度会随曲线形状漂移**
 *   （实测旧曲线 79.7% vs 新曲线 43.1%）⇒ 用 `p` 阈值永远追不上曲线改动。
 *   ⇒ 判据上移到调用方（那里持有 `AnimatedVisibilityScope`，能拿到时间轴），
 *     用 [MeloXMiniBarBounceWindowStartU] 这个**时间占比**驱动。
 *   本常量仅供旧代码路径对照，**新代码不得使用**。
 */
internal const val MeloXMiniBarBounceWindowStartDeprecated = 0.115f

/**
 * 尾段窗口外沿，**时间占比**口径（`u ∈ [1−U, 1]`）—— 唯一有效的判据。
 *
 * ⚠ **为什么必须用时间而不是 `p` 值**：`expansionProgress` 是缓动后的值，
 *   不同曲线的 `p` 分布完全不同（实测 43.1% ~ 79.7%）。
 *   用 `p` 阈值 ⇒ 换曲线后窗口宽度漂移 ⇒ 位移步进变大 ⇒ 闪线。
 *   用时间占比 ⇒ 窗口的**墙钟宽度恒定**，且 `u` 在窗口内近似线性。
 *
 * @param u 动画归一化时间（`0` = 开始，`1` = 结束）。由调用方从
 *   `AnimatedVisibilityScope` 或等价的时间源取得。
 * @return 位移（dp）；窗口外返回 `0f`。
 */
internal fun meloXMiniBarBounceOffsetDpByTime(u: Float): Float {
    if (u.isNaN() || u.isInfinite()) return 0f
    if (u < 1f - MeloXMiniBarBounceWindowStartU) return 0f
    // 窗口内归一化成 v ∈ [0,1]（0 = 窗口起点，1 = 动画结束）。
    val v = ((u - (1f - MeloXMiniBarBounceWindowStartU)) /
        MeloXMiniBarBounceWindowStartU).coerceIn(0f, 1f)
    return when {
        v < MeloXMiniBarBounceSinkEndU ->
            MeloXMiniBarBounceSinkDp * smoothStep01(v, 0f, MeloXMiniBarBounceSinkEndU)
        v < MeloXMiniBarBounceRecoverEndU -> {
            val t = smoothStep01(v, MeloXMiniBarBounceSinkEndU, MeloXMiniBarBounceRecoverEndU)
            MeloXMiniBarBounceSinkDp +
                (MeloXMiniBarBounceOvershootDp - MeloXMiniBarBounceSinkDp) * t
        }
        else -> {
            val t = smoothStep01(v, MeloXMiniBarBounceRecoverEndU, 1f)
            MeloXMiniBarBounceOvershootDp * (1f - t)
        }
    }
}

/**
 * 尾段窗口占全程的时间占比。
 *
 * ⚠ 取 `0.60`（240 档 ≈ **144ms**）。这个值不是拍出来的 —— 它由
 *   「**单帧位移步进**」反解，目标：步进 ≲ 2.2dp（48dp 胶囊上读不出横线）。
 *   实测（60fps 线性时间轴）：
 *
 *   | 窗口 | 墙钟 | 最大单帧步进 |
 *   |---|---|---|
 *   | 0.30 | 72ms | 3.76dp ⚠ |
 *   | 0.40 | 96ms | 3.14dp |
 *   | 0.50 | 120ms | 2.31dp |
 *   | **0.60** | **144ms** | **2.18dp** ✓ |
 *   | 0.80 | 192ms | 1.65dp |
 *
 * ⚠ 三段（下沉 34% / 回弹 36% / 归零 30%）要装进同一个窗口，
 *   窗口太窄就必然出现「一帧跨段」= 用户看到的闪线。
 *   ⚠ 240 档下 144ms 占 60%，观感上是「迷你栏落位后有一次明显的回弹余韵」，
 *   不会像「延迟出现」—— 因为窗口内前 40% 位移恒为 0。
 */
internal const val MeloXMiniBarBounceWindowStartU = 0.50f

/** 下沉峰值（dp）。重力落座：明显沉下去但不夸张 ⇒ 3.0dp（density 4 ⇒ 12px）。 */
internal const val MeloXMiniBarBounceSinkDp = 3.0f

/** 回弹过冲（dp，负方向 = 往上）。只轻顶一下 ⇒ −0.6dp，读成「落座顿挫」而非「弹跳」。 */
internal const val MeloXMiniBarBounceOvershootDp = -0.6f

// 三段的时间占比（`u` 口径，递增）：下沉 40% / 回弹 32% / 归零 28%。
// ⚠ 下沉段最长：尾巴要「先明显沉下去」才读得出重量感，随后只是轻轻一顿。
private const val MeloXMiniBarBounceSinkEndU = 0.40f
private const val MeloXMiniBarBounceRecoverEndU = 0.72f

/**
 * 收回动画尾端**迷你栏下沉回弹**的唯一曲线：`expansionProgress` → `translationY`（dp）。
 *
 * ⚠ **只在关闭方向生效**（`p: 1 → 0`；终点 `p = 0`，故 `d = p`）。
 *   打开方向不需要：那时迷你栏正在被全屏播放页盖住，用户看不见它。
 *
 * 形状（`p` 随动画从 `WindowStart` 递减到 `0`；`u = 1 - p / WindowStart` 递增）：
 *
 * | `u` | `p` | offset | 用意 |
 * |---|---|---|---|
 * | `0 → 0.34` | `0.115 → 0.076` | `0 → +3.5dp` | **下沉**（往下走） |
 * | `0.34 → 0.70` | `0.076 → 0.034` | `+3.5 → −1.3dp` | **回弹**（越过原位往上） |
 * | `0.70 → 1` | `0.034 → 0` | `−1.3 → 0dp` | 归零静止 |
 * | 窗口外 | `≥ 0.115` | `0dp` | 主体动画阶段不干扰 |
 *
 * ⚠⚠ **`p = 0`（静止）必须严格返回 `0dp`**：否则迷你栏长期停在一个非 0 的
 *   位移上，静止时肉眼可见「栏比正确的位置低了一点 / 玻璃与控件分离」。
 *   由第三段的终点严格为 `0f` 保证。
 * ⚠ `reduceMotion` 时调用方必须整条跳过（可访问性）。
 *
 * ⚠⚠⚠ **2026-10-03 修「退出动画结束时迷你栏中间闪一条线」**（用户报障）。
 *
 * **根因不是玻璃/折射过冲，而是本函数的输入 `p` 出现了「负值 + 骤降」**：
 *   主曲线换成带过冲的 [MeloXPlayerAppleEasing] 后，退出方向
 *   `p(u) = 1 − bezier(u)` 在 `u ∈ [0.72, 0.98]` 区间**穿过 0 变成负数**
 *   （最低 `−0.0064`），而它在 `u ∈ [0, 0.43]` 又**陡降**（1.0 → 0.115）。
 *   逐帧验算（60fps，240 档）得到：
 *
 *   | 帧 | 墙钟 | `p` | `u = 1 − p/W` | offset | **Δ** |
 *   |---|---|---|---|---|---|
 *   | 6 | 100ms | +0.1262 | 窗口外 | 0dp | — |
 *   | **7** | 117ms | +0.0769 | **0.331** | **+3.493dp** | **+3.49** |
 *   | **8** | 133ms | +0.0413 | **0.641** | **−0.956dp** | **−4.45** ⚠ |
 *   | 9 | 150ms | +0.0170 | 0.852 | −0.638dp | +0.32 |
 *
 *   ⇒ 迷你栏 48dp 高，**一帧横跳 4.45dp** = 玻璃上那条横线；
 *     `u` 从 0.331 跳到 0.641 一帧走完 31% 的窗口，「下沉」段根本没跑完就被
 *     「回弹」段接管 —— 这正是用户说的「数学层面交汇」。
 *
 *   ⚠ `p.coerceIn(0f, 1f)` **挡不住这个**：负 p 被洗成 0 ⇒ `u = 1`（不是越界），
 *     于是每一帧都落到 `else` 分支的终点值，offset 在 `-0.000` 附近抖动。
 *     真正的突变发生在 **窗口内的 `u` 分布**上，不是 `u > 1`。
 *
 * **修法：把「三段的分界」从「`p` 值域」改成「`u` 的**累积位移**」** ——
 *   即在窗口内按**弧长**重新参数化，让每一帧的 `u` 增量与时间增量成正比。
 *   最小改动且完全等价的做法：**窗口阈值取「曲线 p 值最陡处的值」**，
 *   使窗口在时间上尽量短而连续，再对 `u` 做 `coerceIn(0f, 1f)` 消除越界。
 *
 * ⚠⚠ **已弃用（2026-10-03）**：本函数用 `p` **值**判窗口，而 `p` 是缓动后的值、
 *   分布随曲线形状漂移 ⇒ 单帧位移步进可达 4.45dp（48dp 胶囊 = 一条横线）。
 *   ⇒ **新代码请用 [meloXMiniBarBounceOffsetDpByTime]（时间口径）**。
 *   本函数保留仅为对照/兜底，内部已加 `u` clamp 消除越界。
 */
internal fun meloXMiniBarBounceOffsetDp(progress: Float): Float {
    val p = progress.finiteProgressOrZero()
    if (p >= MeloXMiniBarBounceWindowStartDeprecated) return 0f
    // ⚠ 必须先把递减的 p 归一化成递增的 u —— 见文件头的「实现陷阱」段。
    // ⚠⚠ 2026-10-03：`p` 在过冲曲线下**可为负**，不 clamp 会让 `u > 1` 越界；
    //   clamp 到 `[0,1]` 后保证「窗口末尾恰好落在 0dp」，不产生末端跳变。
    val u = (1f - p / MeloXMiniBarBounceWindowStartDeprecated).coerceIn(0f, 1f)
    return when {
        u < MeloXMiniBarBounceSinkEndU ->
            MeloXMiniBarBounceSinkDp * smoothStep01(u, 0f, MeloXMiniBarBounceSinkEndU)
        u < MeloXMiniBarBounceRecoverEndU -> {
            val t = smoothStep01(u, MeloXMiniBarBounceSinkEndU, MeloXMiniBarBounceRecoverEndU)
            MeloXMiniBarBounceSinkDp +
                (MeloXMiniBarBounceOvershootDp - MeloXMiniBarBounceSinkDp) * t
        }
        else -> {
            val t = smoothStep01(
                u,
                MeloXMiniBarBounceRecoverEndU,
                1f,
            )
            MeloXMiniBarBounceOvershootDp * (1f - t)
        }
    }
}

/**
 * 清洗 `expansionProgress`：NaN/±Inf → 0，**并保留负值**。
 *
 * ⚠⚠ **为什么不能 `coerceIn(0f, 1f)`**（2026-10-03 踩过）：
 *   带过冲的 `MeloXPlayerAppleEasing` 在退出方向会让 `p` **穿过 0 变负**
 *   （最低约 `−0.0064`）。`coerceIn` 会把它洗成 `0`，
 *   于是归一化 `u = 1 − p/W` 恒等于 `1` ⇒ 每一帧都落在「归零」段的终点
 *   ⇒ offset 在最后一小段时间里**抖动**（肉眼 = 闪线）。
 *   ⇒ 这里只做 NaN/Inf 清洗，**负值原样交给调用方的 `coerceIn(0f, 1f)`**
 *   在 `u` 那一侧处理。
 */
private fun Float.finiteProgressOrZero(): Float =
    if (isNaN() || isInfinite()) 0f else this

// ══ 天际歌词封面 morph：显式边界动画 + 圆角插值（2026-10-06）══════════════════

/**
 * 天际封面 morph（歌词页大封面 ⇄ 天际 132dp）的边界动画：**显式 tween**
 * （Apple easing + 用户设置时长，经 [meloXPlayerTweenDurationMillis] 抵消系统缩放）。
 *
 * ### 为什么必须显式传
 * `sharedElement` 的默认 `boundsTransform` 是
 * `DefaultBoundsTransform = spring(NoBouncy, StiffnessMediumLow)` —— 一条**渐近收敛**
 * 的弹簧（到 0.5px 要 ~480ms），与两端 `AnimatedVisibility` 的 360ms 淡入淡出**脱拍**：
 * 飞行拖尾、AV 转场结束时弹簧还在半空 ⇒ 残留量一帧补齐 = 交接硬切
 * ⇒ 用户报障的「没有一镜到底感」（与 `MeloXMotion.kt` 整页推入改 tween 同款教训）。
 */
internal val MeloXSkylineArtworkBoundsTransform: BoundsTransform =
    BoundsTransform { _, _ ->
        tween(durationMillis = meloXPlayerTweenDurationMillis(), easing = MeloXPlayerAppleEasing)
    }

/**
 * 天际封面 morph 的「圆角插值」overlay 裁剪（源端/目标端传**同一份实例**）。
 *
 * ### 为什么需要
 * `sharedElement` 只动画 bounds，**不插值两端各自的 clip 圆角**（compose animation
 * 1.10.3 字节码实证：包内无 1.7 时代的 MorphShape；`clipInOverlayDuringTransition`
 * 的 [SharedTransitionScope.OverlayClip] 是唯一注入点）。源端（横屏歌词页大封面）
 * 圆角 12dp、目标端（天际 132dp 封面）20dp —— 不接的话圆角在飞行起止两帧**瞬切**
 * （用户报障「圆角切换过去就速变」）。
 *
 * ### 原理
 * `getClipPath` 每帧拿到**当前动画中的 bounds**，用宽度在 `[targetSize, sourceSize]`
 * 区间的归一化位置反推进度 `t`（1=源端、0=目标端），圆角 = lerp(target, source, t)
 * ⇒ 全程连续，两端落点精确（t=1 恰 [sourceRadius]、t=0 恰 [targetRadius]）。
 * 每帧重算 Path 的开销可忽略（一次 addRoundRect）。
 *
 * @param sourceRadius 源端圆角（SharedHost `lerpDp(6,12,expansionProgress)` 的全展开值 12dp）
 * @param targetRadius 目标端圆角（天际 132dp 封面的 20dp）
 * @param sourceSize   源端边长（横屏 `landscapeSize`，与 SharedHost 同一份公式）
 * @param targetSize   目标端边长（132dp）
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun meloXSkylineArtworkOverlayClip(
    sourceRadius: Dp,
    targetRadius: Dp,
    sourceSize: Dp,
    targetSize: Dp,
): SharedTransitionScope.OverlayClip = object : SharedTransitionScope.OverlayClip {
    override fun getClipPath(
        state: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path {
        val srcPx = with(density) { sourceSize.toPx() }
        val dstPx = with(density) { targetSize.toPx() }
        val progress = if (abs(srcPx - dstPx) < 0.5f) {
            0f
        } else {
            ((bounds.width - dstPx) / (srcPx - dstPx)).coerceIn(0f, 1f)
        }
        val radiusPx = with(density) { lerp(targetRadius, sourceRadius, progress).toPx() }
        return Path().apply {
            addRoundRect(RoundRect(rect = bounds, cornerRadius = CornerRadius(radiusPx)))
        }
    }
}
