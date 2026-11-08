package com.lladlam.melox.ui.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.unveilIn
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import kotlin.math.exp
import kotlin.math.roundToInt

internal object MeloXMotion {
    /**
     * **整页推入 / 退回的时长 = 350ms** —— iOS `UINavigationController` push/pop 的口径。
     *
     * ⚠ 推入与退回**必须同长**（iOS 两个方向就是同一个 0.35s）。旧版 280 / 220 是 Material
     * 的分向节奏，配上「退回」那条更短的收尾，读起来是「进去慢、回来蹿」—— 正是用户
     * 2026-10-01 报的「pop/push 差点味道」，所以这里收敛成一个值。
     *
     * ⚠ **所有手写 `delay` 必须继续从这个常量推导**（`meloXSettledMillis`）：数值唯一真相源，
     * 改了时长而漏改延迟就会退回 PR #61 修掉的那类 bug（墙上时钟与动画时钟脱钩）。
     */
    const val PageTransitionMillis = 350

    /**
     * **入页首帧遮罩时长 = 60ms** —— 与 [PageTransitionMillis]=350ms 解耦，只盖头一截。
     *
     * 首帧（页面首次绘制录制）实测 19~23ms，超过 90Hz 的 11.07ms 预算 ⇒ 必丢一拍
     * （首个可见变化落在 +22~26ms 而非 +11ms）。`unveilIn` 把入页在头 [PageVeilMillis]
     * 内用**页面自身背景色**盖住，遮掉这帧越期。
     *
     * 观感零变化：60ms 时页面只滑进 ~17% 屏宽、且盖的是背景色（= 页面自绘背板），
     * 揭示完成时页面仍在屏缘，几乎看不见「揭开」动作。⚠ 不改 [PageTransitionMillis]
     * （横移手感不变），只叠一层与滑入**并行**的极短遮罩。
     */
    const val PageVeilMillis = 60

    /** 推入时长（= [PageTransitionMillis]）。 */
    const val PageEnterMillis = PageTransitionMillis

    /** 退回时长（= [PageTransitionMillis]，**与推入同长**）。 */
    const val PageExitMillis = PageTransitionMillis
    const val ContentEnterMillis = 320
    const val ContentExitMillis = 240
    const val CategorySwitchEnterMillis = 240
    const val CategorySwitchExitMillis = 180
    const val IconEnterMillis = 180
    const val IconExitMillis = 120
    const val PanelEnterStiffness = 420f
    const val PanelExitStiffness = 500f
    const val DropdownStiffness = 320f
}

/**
 * iOS 口径的弹簧数值。
 *
 * Apple `spring(bounce:)` 里 `bounce` 与阻尼比的关系是 **`ζ = 1 - bounce`**：
 * bounce=0 → 临界、不过冲；0.2~0.4 是 HIG 的 Q 弹甜区；>0.4 会卡通化。
 * ζ<1 的峰值过冲 `p = exp(-πζ/√(1-ζ²))`：ζ=0.70 → 4.6%（明显 Q 弹），
 * ζ=0.90（改前）→ 0.15%（等于看不见）。
 *
 * 这里只放「需要 Q 弹」的那几处；普通过渡继续用上面的 tween / 不弹的 spring。
 * ⚠ 数值唯一真相源，调用点不要再写裸常量。
 */
internal object MeloXSprings {
    /**
     * 底栏**收缩**方向（展开 → 收缩）。bounce=0.30 → ζ=0.70，峰值过冲 4.60%
     * （行程 224dp → 回弹 10.30dp，峰值时 nav 宽 37.70dp；仍 > 收缩态 25dp 图标）。
     *
     * 收缩发生在「周围刚刚空出来」之后（nav 自己在变窄、搜索键也在缩），所以这里
     * 可以放开弹 —— 10.30dp 是用户反复验收过的幅度。
     *
     * 定档轨迹：0.30（首版）→ 0.34 → **0.37**（取消单边窗口后为保住原幅度而补偿 1.25×）
     * → 0.34（用户「非线性不错，但回弹幅度太大」）→ **0.30**（用户二次回访「还是大了些」）。
     *
     * ⚠ **0.30 是本条的甜区下限**。若仍嫌大，不要再往 0.28 压 —— ζ→1 的过程会先失去
     *   「弹」、再变成「迟钝」，而不是「更干脆」。那种情况应当**取消过冲**（限幅归 0）
     *   或改用带轻微过冲的 back-ease，而不是继续调 bounce。
     */
    const val BottomBarCollapseBounce = 0.30f

    /**
     * 底栏**展开**方向（收缩 → 展开）。bounce=0.245 → ζ=0.755，峰值过冲 2.68%
     * （行程 224dp → 外扩 6.02dp，左 2.01 / 右 4.01dp）。
     *
     * ⚠ 这不是「把 0.30 调小一点」，而是**由展开方向两侧剩下的空间反推出来的**，
     *   而且是被两层约束夹出来的：
     *   · 右侧：固有缝 7dp（官方截图实测 10px = 7.12dp），玻璃边缘的折射/发光在视觉上
     *     比布局边界大 1~2dp ⇒ 右缘可用外扩 **4dp**（硬顶，再大就读成「撞上搜索键」）；
     *   · 左侧：12dp 屏幕边距、没有邻居 ⇒ 可用 8dp，但不是硬顶。
     *   落点 = **右 4dp / 左 2dp（2:1 偏右，见 [NavExpandLeftShare]）** ⇒ 总外扩 6dp
     *   ⇒ `p = exp(−πζ/√(1−ζ²)) = 6 / 224 = 2.68%` → ζ = 0.7552 → bounce = 0.2448。
     *   物理上本来就该这样：展开是「朝有邻居的那一侧长大」，右侧只有 4dp 地方，
     *   所以重心必然偏右、整体也收着弹；收缩是「四周空出来了」⇒ 放开弹（0.30）。
     *   **两个方向各是一条完整的弹簧，几何上不再有任何事后压缩。**
     *
     * ⚠ 为什么必须是两个方向各自的 spec，而不是给展开方向事后「压缩」：
     *   共用一个 spec 时，保护邻居只能靠 `rawDp × gain` 把越界量缩进预算 —— 那条曲线
     *   **不再是弹簧**（阻尼包络仍是 0.30 的，只是整体缩了 0.78 倍），两个方向手感不对等。
     *   分开之后几何就是 `lerpDpBouncy(稳态宽, 收缩宽, f)` 一句，没有任何后处理。
     *
     * ⚠ 「左右怎么分」只由 [NavExpandLeftShare] 决定（纯几何，与弹簧解耦），但两者是
     *   一起定的 —— 改分配比例后必须回头重算这个 bounce。
     * ⚠ 行程一变（改 `searchGap` / 边距 / 屏宽）这个对应关系也会失效，同样要重算：
     *   `k = ln(1/p)/π` → `ζ = √(k²/(1+k²))` → `bounce = 1 − ζ`，其中
     *   `p = 总外扩dp / travel`，总外扩 = 右预算 ÷ 右缘占比。
     *   离线脚本 `tmp/sim_directional_spring.py` 可直接出这张表。
     */
    const val BottomBarExpandBounce = 0.245f

    /**
     * ⚠ 刚度决定「过冲窗口」的长度，不是装饰参数。
     *
     * k=500 (response≈0.28s) → 268ms 就收完，过冲窗口只有 194ms，一闪而过读成「抖动」；
     * 降到 k=250 (response≈0.40s，对齐 Apple drawer / panel 的 0.3~0.4s) 后
     * 过冲窗口 194→276ms，才读得出「回弹」而不是「顿一下」。
     */
    const val BottomBarStiffness = 250f

    /** 迷你播放器出现时的上浮。同样是甜区。 */
    const val MiniPlayerRevealBounce = 0.34f
    const val MiniPlayerRevealStiffness = 300f

    /** bounce → Compose 阻尼比。 */
    fun dampingRatio(bounce: Float): Float = (1f - bounce).coerceIn(0.05f, 1f)
}

/**
 * 播放器展开时「后方页面」的下沉量（Apple Music 口径，2026-09-28 八次定档）。
 *
 * 全屏播放器是覆盖层，底下的页面要**往中心缩小**，四角浮出**与屏幕圆角同心**的圆弧，
 * 页面外侧露出的是**纯黑舞台**（不是主题背景色），页面上再压暗一档 ——
 * 缩放/位移/圆角/背板全都只吃 draw 期（graphicsLayer / drawBehind），不重排页面。
 *
 * ⚠ 页面外侧舞台**全程纯黑**（用户报障：「外面全程是黑的」）：只在完全没有下沉时
 *   铺主题背景色，一旦下沉立刻切纯黑（渐变到黑会让刚露出的那条缝发白）。
 *
 * ⚠ **圆角全程 = 系统屏幕圆角，不随下沉变化**（用户口径 2026-09-28 八次定档：
 *   「不要收圆角，我要的就是全程系统圆角」）。
 *   系统真值 = `dumpsys window` 的 `mRoundedCorners radius=184`（iQOO 15，四角同值），
 *   运行时从 `WindowInsets` RoundedCorner 取、**轮询到拿到为止**（组合期读一次多半是 null）。
 *   ⇒ f=0 时页面圆角与屏幕物理切角**完全重合**（零多余黑角）；下沉过程中保持同一半径。
 *   上一版按「与屏幕圆角同心」去收半径（184→105px）**已被用户否掉**，别再回去。
 *
 * ⚠ **纵向比例由横向内缩反推**（不写死 PageScaleY）：
 *   `sy = 1 − (1 − PageScaleX) · 屏宽 / 屏高` ⇒ 可见内缩在 x/y 上相等
 *   （360×792dp：横向 30.6dp / 纵向 30.6dp），顺带解掉「上下空隙天生是左右 2.2 倍」。
 *
 * ⚠ 同步：缩放**不套任何 smoothStep 窗口**（[ScaleStart]=0 / [ScaleEnd]=1）——
 *   它与外壳 morph 是同一条 `meloXPlayerRecedeSpec()`（打开 iOS present / 关闭
 *   iOS 回弹曲线，同 duration），1:1 跟手，从 0 起、到 1 止。
 *
 * ⚠ 只缩 Scaffold 页面层，**不能**缩底部 chrome：chrome 里挂着 MiniPlayer 的
 *   sharedBounds 源，祖先 graphicsLayer 一缩，morph 几何就和目标对不上。
 */
internal object MeloXPlayerRecede {
    /** 横向缩小目标比例 ⇒ 单边内缩 `(1−0.83)·360/2 = 30.6dp`。 */
    const val PageScaleX = 0.83f

    /**
     * 背景页**向下沉降**位移（dp，正值 = 向下）。Apple Music 口径：背景除了同心
     * 内缩，还会整体下滑 —— aplus.rs 对 iOS card deck 的补充是「与卡片严格同步、
     * 只 dim 不 blur、spring 几乎无过冲」。
     *
     * 数值沿革（公开渠道无 AM 该位移的实测值，按真机反馈逐档调）：
     * · 13f（首版）：15pt@414pt 深度单位折算 360dp —— 太保守，用户报「没 iOS AM 感觉」。
     * · 25f：约 2 倍。f=1 时上下黑边 5.6 / 55.6dp，背景下半截滑进底栏
     *   （chrome 不随页面缩放/沉降， AM 同款「页面沉到 tab 栏底下」的物理感）。
     * · **30f（现状）**：用户「再激进」口径。f=1 时上黑边仅剩 0.6dp、下黑边 60.6dp
     *   —— 背景上缘几乎贴住屏幕顶，整页肉眼呈「被卡片整个压下去」。
     * 觉得深/浅改这里即可（单个常量，横纵缩放与它互不依赖）。
     *
     * 同步机制（"严格同步"的结构保证，不是"保持相等"）：
     * `MeloXApp` 的 recede `graphicsLayer` 里，缩放与 `translationY` 读的是
     * **同一个** `recedeFraction()`（master fraction，与播放器外壳 morph 共用
     * `meloXPlayerRecedeSpec` 同一条曲线），且在同一個 lambda、同一帧、同一
     * RenderNode 里求值 ⇒ 背景的缩/沉与卡片推进**天然同拍**，不存在两条 spec
     * 错拍的可能（本对象上方历史注释里被废弃的两条路正是错在这）。
     *
     * 副作用（刻意为之）：f=1 时上下黑边不再等宽。AM 的观感正是「背景被卡片
     * 压下去」，等宽反而不是那个味道。
     */
    const val PageSinkDp = 30f

    /**
     * 纵向缩放**覆盖值**（0f = 不覆盖，按横向内缩等距反推）。
     *
     * ⚠⚠ 2026-10-20 修正本对象的历史注释：上面那段「f=1 时上黑边仅剩 0.6dp、
     *   下黑边 60.6dp」**上下写反了**。按 `MeloXApp` 里的真实公式重算（transformOrigin
     *   未设置 ⇒ **中心缩放**，iQOO15 1440×3168）：
     *   · `syFull = 1 − (1−0.83)×1440/3168 = 0.9227` ⇒ 缩放本身**四边各内缩 122.6px**
     *   · `translationY = 30dp = 93.9px` 向下
     *   ⇒ **上黑边 216px(69dp)、下黑边 28px(9dp)**。差值 60dp 与注释吻合，方向相反。
     *   用户报障正是这个现象：「背景下沉的底部几乎不上移」（底边只上移 9dp）。
     *
     * 修法有两条：① 调小 [PageSinkDp]（用户 2026-10-20 明确「保持 30dp 不动」）；
     * ② **本覆盖值** —— 加大纵向缩放，底边多收、上边多让。下沉量一行未动，
     * 「被卡片压下去」的纵深完整保留。
     *
     * 取 0.87 ⇒ 内缩 206px ⇒ 上黑边 300px(96dp)、**下黑边 112px(36dp)**（原 9dp，×4）。
     * 想更夸张/更收敛只改这一个数（0 = 恢复等距反推）。
     */
    const val PageScaleYOverride = 0.87f

    /**
     * 下沉量占「纵向可缩余量」的**比例**（2026-10-27 新增，修正横屏裁切）。
     *
     * ## 为什么不能只用固定 dp
     * `translationY` 原来是死值 [PageSinkDp]=30dp(=120px @density4)。这个值是在
     * **竖屏 1440×3168** 上定档的：此时纵向可缩余量 `(H/2)(1−sy) = (3168/2)×0.13 = 206px`
     * ⇒ 下黑边 = 206 − 120 = **86px**，上黑边 326px ⇒「被卡片压下去」的观感。
     *
     * 但**横屏 3168×1440** 时 `H/2` 只剩 720：可缩余量 `720×0.13 = 93.6px`，
     * 而位移仍是 120px ⇒ `93.6 − 120 = −26.4px`，即**背景页底边被推出屏幕外 26px**，
     * 卡片底边与底部圆角整段被硬裁。用户 2026-10-27 报的
     * 「下半页面在打开动画时表现为截断」就是这条（只在横屏可见）。
     *
     * ## 为什么用比例而不是夹取
     * 夹到 `available`（= 底边刚好贴屏幕底）会变成「下黑边 0」，横屏读起来是「贴着底
     * 被切平」，仍不是卡片。改用**比例**后下黑边恒为屏高的同一个百分比
     * （竖屏 86/3168 = 2.71%，横屏 1440×2.71% = 39px），上下留白关系与竖屏一致。
     *
     * 取值 = 竖屏实测比值：`120 / 206 = 0.583` ⇒ **竖屏位移逐像素不变**（120.1px），
     * 横屏自动收成 54.6px。改这个数等于调「背景被压多深」，越界（>0.9）又会把底边推出去。
     */
    const val PageSinkRatio = 0.583f

    /** 压暗 alpha（只罩被缩小的页面本身）。 */
    const val PageScrimAlpha = 0.24f

    /** 缩放/下沉的起止（master fraction 口径）。0→1 = 与外壳动画完全同步。 */
    const val ScaleStart = 0f
    const val ScaleEnd = 1f

    /**
     * 关闭过冲回弹的**历史**（2026-09-29 → 2026-10-03 再次改）：
     *
     * 1. 原本 `spring(ζ=0.30, k=220)` —— 关闭方向单独用弹簧会让「播放器外壳」与
     *    「背景页」**不同步**（外壳吃 master、背景吃弹簧）。已废弃。
     * 2. 改为「整条 master 用 `MeloXIOSCollapseEasing`（easeOutBack）」——
     *    ⚠⚠ 2026-10-03 **这条路也走不通**：`meloXPlayerRecedeSpec` 只驱动**背景页下沉**，
     *    播放器本体走的是另一条 spec（master）。两条曲线形状不同
     *    （decelerate vs 起步斜率 ≈2.2）⇒ 用户报障「页面下沉比播放栏动画结束早、不同步」。
     * 3. **现状**：`meloXPlayerRecedeSpec` 与 master **共用同一条**
     *    [MeloXPlayerAppleEasing]（见 `MeloXPlayerTransitionSpec`），开/关/本体/下沉
     *    全部同曲线同时长 ⇒ 天然同拍。
     *    下沉的过冲回弹改由**迷你栏尾段**的 `meloXMiniBarBounceOffsetDp()` 提供
     *    （解析式尾段过冲，不依赖曲线 `f<0`）。
     *
     * ⇒ 教训：只要「本体」与「背景」是**两条 spec**，就一定会错拍；
     *   要同拍就必须让它们**引用同一个常量**，而不是"保持相等"。
     */
}

/** 过冲限幅（行程比例）。收缩方向 bounce 0.30 → 实际峰值 0.046（本例不会触及此上限）；
 *  224dp 行程 → 最窄 48−224×0.046≈37.7dp，仍大于 25dp 图标。
 *  展开方向的越界由 [MeloXSprings.BottomBarExpandBounce] 自己守住，同样不依赖这个上限。 */
internal const val SpringOvershootLimit = 0.09f

/**
 * 底栏弹簧的**两个方向 spec** —— 直接喂 `animateFloatAsState`。
 *
 * 展开 / 收缩是两次独立的动画事件（`minimized` 的两个跳变），所以可以、也应该各给一条
 * 弹簧：展开是朝有邻居的方向长大（空间受限 → 高阻尼 0.245、且落点偏右），收缩是四周空出来
 * （放开弹 0.30）。数值怎么来的见 [MeloXSprings.BottomBarExpandBounce]。
 *
 * ⚠ 调用方必须**按 target 选**：`if (minimized) CollapseSpec else ExpandSpec`。
 *   `animateFloatAsState` 只在 targetValue 变化时重启动画、并采纳**那一刻**的 spec，
 *   所以 target 与 spec 必须在同一帧一起算出来 —— 不要从动画帧反推方向。
 */
internal val BottomBarExpandSpec: SpringSpec<Float> = spring(
    dampingRatio = MeloXSprings.dampingRatio(MeloXSprings.BottomBarExpandBounce),
    stiffness = MeloXSprings.BottomBarStiffness,
    visibilityThreshold = 0.001f,
)

/** 收缩方向的另一半，见 [BottomBarExpandSpec] 与 [MeloXSprings.BottomBarCollapseBounce]。 */
internal val BottomBarCollapseSpec: SpringSpec<Float> = spring(
    dampingRatio = MeloXSprings.dampingRatio(MeloXSprings.BottomBarCollapseBounce),
    stiffness = MeloXSprings.BottomBarStiffness,
    visibilityThreshold = 0.001f,
)

/**
 * 展开外扩量分给**左缘**的比例（右缘拿 `1 − 这个值`）。
 *
 * 展开时胶囊变宽，多出来的那部分往哪边落是自由选择 —— 但两侧空间不对等：
 * · 右侧固有缝 7dp，扣掉玻璃边缘 1~2dp 的视觉外扩后只剩 **4dp**；
 * · 左侧是 12dp 屏幕边距，没有邻居。
 * 所以落点必须**偏右**（朝搜索键那一侧弹），否则右边那点余量根本用不上。
 *
 * 现取 **1/3（左 1 : 右 2）**：峰值时右缘 +4.01dp、左缘 −2.01dp、右缝 7 → 3.00dp。
 * 配合 [MeloXSprings.BottomBarExpandBounce]（0.245）反推，总外扩 6.02dp。
 *
 * ⚠ 只写**比例**、不写绝对 dp：胶囊宽度本身就是弹簧输出（`lerpDpBouncy` 不夹取），
 *   把绝对 dp 写死会在行程（`expandedNavWidth − compactSize`）变化后静默失配。
 *   想按「右缘最多 4dp」反推总外扩，写在注释里、由这个比例去乘即可。
 * ⚠ nav 的**中心会因此轻微右漂**：峰值 148 → 149dp（1dp）。这是「右边弹得更多」的
 *   必然代价，不是 bug —— 想消除就只能回到 1/2（左右等分），而那正是被否掉的「对称」。
 * ⚠ 收缩方向（f>0）宽度不超稳态，`coerceAtLeast(0.dp)` 让越界量归零 → 退回纯边距，
 *   也就是**收缩天然是左锚定**（左缘恒 12dp，右缘从 284 收到 60dp）。这个比例只作用于展开。
 */
internal const val NavExpandLeftShare = 1f / 3f

/**
 * 弹簧输出 → 几何比例（0 = 展开稳态，1 = 收缩稳态）。
 *
 * 只做**带符号限幅**：`[-limit, 1+limit]`，两侧对称，形状就是弹簧本身。
 *
 * ⚠ 不要再引入任何「区间重映射」（把弹簧输出按 [start,end] 线性映射）：
 *   任何单边窗口都等于**在某一个方向把几何运动在弹簧最高速处截断**。
 *   旧实现 `((v-0.20)/0.80).coerceIn(0f, …)` 的实测后果——
 *   · 收缩方向：只是「延后 49ms 启动」，末尾过冲完整 → 用户评价「不错」；
 *   · 展开方向：几何在 134ms 就被钉死在终点，此刻速度还有 1.45 dp/ms
 *     （峰值 2.10 的 69%），然后**空转 246ms** → 用户评价「直来直去」。
 *   同一个窗口的两面：一面是延迟启动，另一面就是撞墙。取消窗口后两个方向同形。
 *
 * ⚠ 若将来确实需要窗口（例如相位错峰），必须校验 **value=end 时结果恰好 =1**，
 *   否则弹簧剩余行程会让几何继续外推、稳态停在错值上。
 */
internal fun sprungFrac(
    value: Float,
    limit: Float = SpringOvershootLimit,
): Float = value.coerceIn(-limit, 1f + limit)

/**
 * **整页推入 / 退回的动画规格 —— 350ms 固定时长 + 临界阻尼形状的缓动**。
 *
 * ### ⚠ 为什么不是 `spring(ζ = 1, k = 350)`（2026-10-01 第四次返工，真机逐帧实测否掉）
 * 用户报「**动画的最后一帧会进行一次横移调整位置，导致了动画不连续**」。
 * 真机 720p 逐帧追详情页左缘 / 埋点落盘逐帧布局 x，行程 1440px 的「还差多少 / 用时」：
 *
 * | 还差 | 改前 `spring(ζ=1, k=350)`（真机实测） | 改后 `tween(350ms)`（按 [MeloXPageEasing] 解算） |
 * | --- | --- | --- |
 * | ≤ 120px | 0.334s | 0.160s |
 * | ≤ 60px | 0.376s | 0.192s |
 * | ≤ 30px | 0.417s | 0.222s |
 * | ≤ 10px | 0.479s | 0.267s |
 * | ≤ 3px | 0.542s | 0.307s |
 * | ≤ 1px | 0.589s | 0.331s |
 * | 0（归位）| **0.609s** | **0.350s** |
 *
 * 也就是改前**前 380ms 走完 96%，剩下 230ms 只挪最后 60px**（60px = 15dp，肉眼看得见）；
 * 最后 60px 一段，改前用掉 0.233s、改后 0.158s。
 * 观感就是「已经到位了，又慢慢横移补了一下」——正是用户报的那一幕。
 *
 * ⚠⚠ 本表「改后」一列**曾经是错的**（写成 0.298 → 0.478s），根因记在这里免得再犯：
 *   那一版拿「10 倍慢放」的录屏量时间轴，但 `animator_duration_scale` 是
 *   `WindowRecomposer` **在 App 启动时读一次**——改设置对已跑起来的进程无效 ⇒
 *   那卷带子其实是 1 倍实时速，而我把它除以了 10。
 *   现值改为**按 [MeloXPageEasing] 解析解算**，并与两组真机数据互校：
 *   埋点（`onGloballyPositioned` 落盘逐帧 x，拟合 ω=9.0、RMSE 5px）与像素反解逐点吻合；
 *   整段可见运动的实测终点 = **341~355ms**（= 标称 350ms）。
 *
 * 根因是**弹簧渐近收敛**：`x(t) = target + (x₀−target)·(1+ωt)e^{−ωt}` 永远只是逼近目标，
 * Compose 靠 `visibilityThreshold` 判定结束，**阈值以外必然剩一截尾巴**。这不是数值没调好，
 * 是结构性代价 —— 阈值调小 ⇒ 尾巴更长；阈值调大 ⇒ 尾巴短了，但最后一帧要**一次跳掉阈值那一段**，
 * 「横移调整位置」反而更明显。**两者零和，没有两全解。**
 * （上一版 KDoc 说弹簧「不存在长尾」是错的，此处更正。）
 *
 * ⚠ 弹簧这条尾巴**不止影响整页滑入**：`sharedElement` 的默认 `boundsTransform` 也是一条
 *   同族的渐近弹簧（见 [MeloXPageBoundsTransform]）——只改本条 spec 治不了 hero morph。
 *
 * ### 修法：固定时长，精确落点
 * 改成 `tween`：它在 `durationMillis` 处**精确等于目标值**，尾巴恒为 0、收尾跳变恒为 0。
 * 形状用 [MeloXPageEasing]（把 ζ = 1 的阻尼解按同一时标采样、终点归一到 1）——
 * 起步无折点、中段一气呵成、末端减速到零，观感与弹簧一致，但**在 t = 1 就是 1**。
 *
 * 顺带修正：真实收敛时间从实测的 0.609s 回到标称 [MeloXMotion.PageTransitionMillis] = 350ms。
 * 这条以前是**反的** —— 弹簧那条 `MeloXPageSpec` 的 `getDurationNanos` 会带上 `EndVelocity`
 * 去解收敛（数值不稳、偏长），而 `meloXSettledMillis` 取的是 `max(标称, 推算)`，
 * 于是「按标称 350ms 解锁」的时刻落在动画真正结束之前（0.609s），锁早了。
 *
 * ⚠ 推入页、被盖住的下层页（`Modifier.meloXPageRecede`）、overlay 三处**必须共用本规格**，
 *   同时起步时 `旧页 = −W/3·p` 与 `新页 = (1−p)·W` 才**在任意时刻**严格互补。
 *   两条 spec 共用同一个时长 + 同一个缓动 ⇒ 同拍是**构造上的保证**，
 *   不再依赖「两条弹簧参数抄成一样」这种靠人守的约定。
 *
 * ### ⚠ 为什么要声明两条（类型参数不同，但他俩必须同源）
 * Compose 的 `slideInHorizontally` / `slideOutHorizontally` 的 `animationSpec` 是
 * **`FiniteAnimationSpec<IntOffset>`**（位移按整像素推进），而 `fadeIn` / `fadeOut` /
 * `animateFloatAsState` / `Animatable.animateTo` 要的是 **`FiniteAnimationSpec<Float>`**。
 * `FiniteAnimationSpec` 是**不变的**（invariant），两者不能互相赋值、也不能强转
 * （擦除后能编译但运行期按 Float 解 IntOffset）。
 * 所以只能声明两条 —— 但**时长与缓动必须取自同一个来源**（见下）。
 */

/**
 * 采样终点（无量纲 `ω·t`）。
 * `9.0` 处残差 `(1 + 9)·e^{−9} ≈ 0.123%` —— 再往后肉眼已无位移，继续拉长只是把尾巴接回来。
 */
private const val MeloXPageEaseOmegaTEnd = 9f

/** 终点归一化系数 `1 / s(ω·t_end)`：保证 `transform(1f)` 恰好落在 1（而不是 0.9988）。 */
private val MeloXPageEaseNormalization =
    1f / (1f - (1f + MeloXPageEaseOmegaTEnd) * exp(-MeloXPageEaseOmegaTEnd))

/**
 * 临界阻尼形状的窄口缓动：`s(f) = [1 − (1 + x)·e^{−x}] / [1 − (1 + x_end)·e^{−x_end}]`，
 * 其中 `x = x_end · f`（把阻尼解拉伸到固定时长的 `[0, 1]` 上）。
 *
 * 单调递增、无过冲；`f = 0` 精确 0、`f = 1` 精确 1；末端斜率趋零但不为零（= 减速到位）。
 */
private object MeloXPageEasing : Easing {
    override fun transform(fraction: Float): Float {
        // 缓动输出会进 HWUI 矩阵 ⇒ 按项目约定清洗非有限值。
        if (!fraction.isFinite()) return 1f
        if (fraction <= 0f) return 0f
        if (fraction >= 1f) return 1f
        val x = MeloXPageEaseOmegaTEnd * fraction
        return (1f - (1f + x) * exp(-x)) * MeloXPageEaseNormalization
    }
}

/**
 * **转场规格 —— 350ms 固定时长 + 临界阻尼形状的缓动（`tween` 实现）**。
 *
 * ### 连续打断 = 从头开始
 * `tween` 在**被打断**时（推入途中点返回 / 反复横跳）**从当前位置、时间归零重放**整个过渡，
 * 起点速度天然为 0（`MeloXPageEasing` 在进度 0 处导数为 0）⇒ 平滑加速、没有急停顿挫，
 * 且每次新事件都重新跑一次干净的 350ms 过渡 —— 这就是「连续打断」该有的观感。
 * （早期试过「带初始速度的 Hermite 续上」，结果页面从断点带速飞出、过冲脱屏，已被否掉。）
 *
 * ### 两个不变量（改时长/缓动必须同时改 [MeloXPageSpec] / [MeloXPageSlideSpec] /
 * [MeloXPageBoundsTransform] 三条）
 * 1. **350ms 精确落点**：`tween` 在 `durationMillis` 处精确等于目标值，尾部恒为 0、
 *    `getDurationNanos` 可信，`meloXSettledMillis` 不再提前解锁。
 * 2. **与 sharedElement morph 逐字同源**：三个通道共用同一个 `MeloXPageEasing` 与同一时长，
 *    `sharedElement(boundsTransform = MeloXPageBoundsTransform)` 与整页滑入在同一时刻精确到位。
 */









/** 转场的 **Float 通道**：`fadeIn` / `fadeOut` / `animateFloatAsState` / `Animatable.animateTo`。 */
internal val MeloXPageSpec: FiniteAnimationSpec<Float> = tween(
    durationMillis = MeloXMotion.PageTransitionMillis,
    easing = MeloXPageEasing,
)

/**
 * 同一组「时长 + 缓动」的 **IntOffset 通道**：只给
 * `slideInHorizontally` / `slideOutHorizontally` 用。
 * ⚠ 与 [MeloXPageSpec] 必须**逐字同源**（同一条 tween 的时长与 easing），
 *   改一个必须改另一个 —— 否则推入位移与淡入不同速，两页之间那条缝会忽宽忽窄。
 */
internal val MeloXPageSlideSpec: FiniteAnimationSpec<IntOffset> = tween<IntOffset>(
    durationMillis = MeloXMotion.PageTransitionMillis,
    easing = MeloXPageEasing,
)

/**
 * **转场「在途改向」规格 —— 连续打断时用弹簧接管，替代「snap 回起点重放」**。
 *
 * ### 为什么打断不能 replay、也不能 tween
 * - replay（snap 回方向起点再 tween）：页面从半路**瞬移**回屏缘 —— 顿挫的主源。
 * - tween 从当前值重放：位置连续，但 `MeloXPageEasing` 在进度 0 处导数为 0 ⇒
 *   速度从「在途值」瞬间跳到 0，页面猛地一滞 —— 顿挫的次源。
 * - 弹簧被打断时天然**继承当前值与当前速度**并向新目标收敛 ⇒ 位置、速度双连续，
 *   反转如 iOS 般顺滑，零顿挫。
 *
 * ### 参数
 * - `dampingRatio = 1f`（临界阻尼）：零过冲 —— 改向冲过头会把页面推过静息位（可见）。
 * - `stiffness = 800f`：ω≈28.2，全程收敛 ~325ms ≤ 350ms 载体窗（内容不会先被移除），
 *   半程改向 ~150ms —— 比整段略快，读起来是「立即响应」。
 * - `visibilityThreshold = 0.001f`（≈1.4px@1440px）：收尾残差亚像素，无「最后一帧横移」。
 *
 * ⚠ 只用于**被打断的改向**；干净（非打断）的推入/退回仍走 [MeloXPageSpec]（350ms 精确落点）。
 */
internal fun meloXPageRedirectSpec(): SpringSpec<Float> = spring(
    dampingRatio = 1f,
    stiffness = MeloXPageSpringStiffness / MeloXSystemScale.stiffnessDivisor,
    visibilityThreshold = 0.001f,
)

/**
 * 页面转场弹簧的**基准刚度**（ζ=1 临界阻尼；ω≈28.3 rad/s ⇒ 1px 收敛 ≈341ms、90% 行程 ≈141ms）。
 *
 * ⚠ 只能经 [meloXPagePushSpec] / [meloXPageRedirectSpec] 使用（按 [MeloXSystemScale] 折算），
 *   不要直接塞进 `spring()` —— 那就绕过了「系统缩放桥」，首页又变成不跟随系统缩放了。
 */
internal const val MeloXPageSpringStiffness = 800f

/**
 * **整页推入 / 退回的滑移规格（⚠ 2026-10-19 起页面转场已不再使用它）**。
 *
 * `MeloXInterruptiblePage` 现在走 [MeloXPageSlideSpec]（tween、350ms 精确落点），
 * 下层页退让（`Modifier.meloXPageRecede`）走 [MeloXPageSpec] —— 两页同为 tween、同一条
 * [MeloXPageEasing] ⇒ **任意时刻**严格互补且同一帧精确到位。
 *
 * 退用原因是「末端长尾」这条结构性缺陷（用户报「一直在左右顿挫横移」）：
 * 弹簧渐近收敛，k=800 时残差降到 1px 要 ≈390ms —— 比 350ms 载体窗多出 ~40ms，
 * 这 40ms 里每帧位移只有 0.1~0.5px，`IntOffset` 取整后就是「0,0,1,0,0,1…」的阶梯；
 * 而下层页同族弹簧的收尾阈值不同（见 [meloXPageRedirectSpec]），两页末段互不同步
 * ⇒ 相对缝宽在收尾处还在变，读成「左右错动」。
 * 下面关于「弹簧打断更顺」的论证仍然成立，只是它换不来「精确落点」——
 * 现在的取舍是**优先落点准确**（tween 打断时会从当前位置重放，起点速度归零，
 * 位置连续、无瞬移 —— 见 [MeloXPageSpec] 上面那段「连续打断 = 从头开始」）。
 *
 * 保留此函数供其它（非整页转场）场景使用；**页面推入/退回请用 [MeloXPageSlideSpec]**。
 *
 * **原文：整页推入 / 退回的滑移规格** —— 给 `AnimatedContent` 的 `slideIntoContainer` /
 * `slideOutOfContainer` 用（[com.lladlam.melox.ui.MeloXInterruptiblePage]）。
 *
 * ### 为什么是弹簧而不是 `tween`
 * 官方文档（Customize animations）给的结论：**「spring can handle interruptions more smoothly
 * than duration-based AnimationSpec types because it guarantees the continuity of velocity
 * when target value changes amid animations」**。
 * `AnimatedContent` 在目标翻转（推入途中点返回 / 反复横跳）时**不重建动画**，而是把同一条
 * `TransitionAnimationState` 改向 —— 只有弹簧会**继承当前速度**；`tween` 会把速度归零，
 * 中断点有一下急滞（＝顿挫的次源）。
 *
 * ### 为什么不用「snap 回起点重放」
 * 那会让页面从半路**瞬移**回屏缘 —— 顿挫的主源。弹簧改向位置、速度双连续，读起来像 iOS。
 *
 * ### ⚠ 与早前被否掉的 `spring(ζ=1, k=350)` 的区别（别再退回 tween）
 * k=350 ⇒ ω≈18.7，1px 收敛要 **~609ms**：末段还在以肉眼可见的速度爬行，用户报
 * 「动画的最后一帧会进行一次横移调整位置」。k=800 ⇒ ω≈28.3，1px 收敛 ≈ **0.27s**、
 * 标称观感 ≈350ms —— **没有长尾**，该缺陷不复现。
 * `visibilityThreshold = IntOffset(1, 1)`：收尾残差 ≤1px（亚像素），不会有「最后一帧横移」。
 *
 * ⚠ 与 [MeloXPageBoundsTransform]（hero morph 的 `boundsTransform`）**必须尽量同拍**：
 *   两条都挂在同一个 `Transition` 上，改一条的时长/刚度必须复核另一条。
 */
internal fun meloXPagePushSpec(): FiniteAnimationSpec<IntOffset> = spring(
    dampingRatio = 1f,
    stiffness = MeloXPageSpringStiffness / MeloXSystemScale.stiffnessDivisor,
    visibilityThreshold = IntOffset(1, 1),
)

/**
 * **共享元素（一镜到底）封面 morph 的矩形边界规格** —— 与整页滑入**逐字同源**。
 *
 * ### ⚠⚠ 为什么必须显式传：`sharedElement` 的默认边界动画是**渐近弹簧**
 * `SharedTransitionScope` 的 `sharedElement` / `sharedBounds` 的 `boundsTransform` 默认值是
 * `DefaultBoundsTransform = { _, _ -> spring(DampingRatioNoBouncy, StiffnessMediumLow) }`
 * —— 又是一条**渐近收敛**的弹簧（与 [MeloXPageSpec] 改前那条同病）。
 *
 * 在「整页推入 + 封面 morph」这种**两层同时在动**的场景里，这条弹簧会读出两重缺陷：
 *
 * 1. **追动靶的稳态滞后**：morph 的 target 是 hero 在**根坐标系**里的矩形，而新页正在横向滑入
 *    ⇒ target 自己每帧都在横移。弹簧追一个动靶天生带稳态滞后，封面会一直「挂在」目标后面。
 * 2. **交接瞬间的硬切**：`sharedElement` 的生灭由 `animatedVisibilityScope` 的转场决定。
 *    整页滑入一结束（现在 = 精确 350ms），`SharedTransitionScope` 就**停止消费这份动画、把封面
 *    交给它自己的布局位置**。此刻弹簧若还在半空（它收敛到 0.5px 要 ~480ms），残留量就被
 *    **一帧内补齐** ⇒ 用户报的「**动画的最后一帧会进行一次横移调整位置，导致了动画不连续**」。
 *
 * 改前的整页滑入也是弹簧（同样拖到 ~609ms），两边都慢、交接时刻大致同步，错拍不显眼；
 * 把滑入改成 tween 精确落点之后，交接必然落在 morph 半空 ⇒ 那条硬切反而暴露出来。
 *
 * ### 修法：与滑入共用同一条 tween
 * 时长取 [MeloXMotion.PageTransitionMillis]、缓动取 [MeloXPageEasing]（**同一个私有 object**，
 * 不是「照着抄一遍」）⇒ 两层在同一个时刻**同时**精确到位，「交接」那一刻动画值已经等于目标，
 * 硬切幅度恒为 0。
 *
 * ⚠ 播放器早就这么做了（`MeloXPlayerTransitionSpec.kt` 的 `MeloXArtworkBoundsTransform` /
 *   `MeloXPlayerShellBoundsTransform`，同为 `BoundsTransform { _, _ -> tween(...) }`）——
 *   本值是把同一条结论补到**页面级** hero（首页/发现页、资料库、搜索、播客）。
 * ⚠ 与 [MeloXPageSlideSpec] 一样，改时长/缓动必须**同时**改到这两条，别只改一边。
 */
/**
 * `sharedElement(boundsTransform = …)` 用的包装。
 *
 * ⚠ 每次 morph 开始时求值 ⇒ 时长在这里**现算**，才能跟随 [MeloXSystemScale]；
 *   它与 [meloXPagePushSpec] 的刚度折算在同一刻读同一个 `ratio`，两条严格同拍。
 */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
internal val MeloXPageBoundsTransform = androidx.compose.animation.BoundsTransform { _, _ ->
    tween(
        durationMillis = (MeloXMotion.PageTransitionMillis * MeloXSystemScale.durationMultiplier)
            .roundToInt()
            .coerceAtLeast(1),
        easing = MeloXPageEasing,
    )
}

/**
 * 「整页推入」的一对动作：新页自右侧满宽滑入（`fromRight = true`）。
 * 设置 → 详情页、搜索页 → 分类页（浏览类别→排行榜）都走这一套。
 *
 * ### ⚠ 满宽滑入、**全程不透明**（不再淡入）
 * 这里原先挂在 `slideInHorizontally(initialOffsetX = { it })` 上的是 `fadeIn`。整页位移
 * 本来就是「让开 — 顶上来」的**遮挡**关系：新页还在半路时只有一半屏幕有它，淡入等于把
 * 下层页从半透明的它底下透出来 —— 读成「两页互相糊了一下」，不是 iOS 的「新页压上去」。
 * 位移本身已经把页面从屏外带进来了，不需要透明度兜底（与退回方向的说明同源）。
 *
 * ### 时长 / 缓动
 * 都取 [MeloXMotion.PageTransitionMillis] 的同一组「时长 + 缓动」（位移走 [MeloXPageSlideSpec]，
 * 透明度走 [MeloXPageSpec]），且**被盖住的下层页用的是同一对参数**
 * （见 `Modifier.meloXPageRecede`），两页才严格互补。
 * ⚠ 被盖住的下层页**不再走 `fromRight = false`（1/4 位移 + 淡出）** —— 它改由
 *   `Modifier.meloXPageRecede(progress)` 驱动（**纯左移**，与推入页互补）。
 *
 * ⚠ **推入的页面必须自绘不透明背板**（`Modifier.fillMaxSize().background(colorScheme.background)`）：
 *   MeloX 的页面自身都是透明底、不透明背板只在 `MeloXApp` 根部画一次，整页滑动
 *   全程会把下层列表透出来，观感是「撕裂」而不是「推入」。
 *   反过来，只要页面自带背板，就不要改成「两页各自交叉淡化」——两层文字叠影更糊。
 */
@OptIn(ExperimentalAnimationApi::class)
internal fun meloXPageEnter(fromRight: Boolean = true, veilColor: Color): EnterTransition =
    slideInHorizontally(
        animationSpec = MeloXPageSlideSpec,
        initialOffsetX = { if (fromRight) it else -it / 4 },
    ) + unveilIn(
        animationSpec = tween<Color>(durationMillis = MeloXMotion.PageVeilMillis, easing = LinearEasing),
        initialColor = veilColor,
    )

/**
 * 退回（pop）方向的一对动作。
 *
 * ### ⚠ `toRight = true`（被退回的那一页）：**满宽滑出、不淡出**
 * 用户报障：「删去退回页面的逐渐隐藏」。原来它是 `targetOffsetX = it / 4` **+ `fadeOut`** ——
 * 页面只往右挪 1/4 屏就原地淡掉，读起来不是「滑走」而是「糊着消失」。这两个参数是**一对**：
 * 只滑 1/4 时动画结束时页面还有 3/4 留在屏内，`AnimatedVisibility` 一移除就是硬切，
 * 淡出正是为了遮住那次硬切。因此**要去掉淡出，就必须把位移改成满宽**
 * （与推入的 `initialOffsetX = it` 对称 —— 位移本身把页面送出屏外，不再需要透明度兜底）。
 *
 * ### `toRight = false`（被盖住的下层页）：⚠ 现已改由 `Modifier.meloXPageRecede`
 * 这是原先「搜索主页在子页推入时向左退让（1/4 视差 + 淡出）」用的分支。现在下层页
 * **统一改由 `Modifier.meloXPageRecede(progress)` 驱动** —— 旧页不再出场/退场、也不再淡出，
 * 而是**纯左移**（iOS 纵深：旧页平移 -W/3，见 `ui/MeloXPageTransitions.kt`）。
 * 此分支因此暂无调用者，保留以维持 API 对称。
 */
internal fun meloXPageExit(toRight: Boolean = true): ExitTransition =
    if (toRight) {
        slideOutHorizontally(
            animationSpec = MeloXPageSlideSpec,
            targetOffsetX = { it },
        )
    } else {
        slideOutHorizontally(
            animationSpec = MeloXPageSlideSpec,
            targetOffsetX = { -it / 4 },
        ) + fadeOut(MeloXPageSpec)
    }

/**
 * **全屏覆盖层**的推入/弹出：满宽滑入 + 淡入，返回时满宽滑出 + 淡出。
 * 用在 [com.lladlam.melox.ui.MeloXApp] 根部那一层的就地弹层（听歌识曲这类
 * 「不切 tab、关掉就退回打开它的页面」的整页覆盖）。
 *
 * ⚠ 与 [meloXPageEnter]/[meloXPageExit] 的区别在**退出的位移量**：
 *   那一对是给**同级兄弟页**用的，被盖住的页同时在做反向的 1/4 视差，
 *   两者叠起来才读成一次完整的推入/退回。全屏覆盖层底下是整个 Scaffold、
 *   **不跟着动**，若还按 1/4 退让就会变成「只滑出去四分之一就原地淡掉」，
 *   收尾是断的 ⇒ 这里两侧都走满宽。
 *
 * ⚠ 覆盖层必须自绘不透明背板（`Modifier.fillMaxSize().background(colorScheme.background)`），
 *   否则满宽滑入全程会把下层页面透出来。
 *
 * ⚠ 弹出方向**不淡出**，与 [meloXPageExit] 的退回页保持同一个手感（见那里的说明）。
 */
@OptIn(ExperimentalAnimationApi::class)
internal fun meloXOverlayEnter(veilColor: Color): EnterTransition =
    slideInHorizontally(
        animationSpec = MeloXPageSlideSpec,
        initialOffsetX = { it },
    ) + fadeIn(MeloXPageSpec) + unveilIn(
        animationSpec = tween<Color>(durationMillis = MeloXMotion.PageVeilMillis, easing = LinearEasing),
        initialColor = veilColor,
    )

internal fun meloXOverlayExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = MeloXPageSlideSpec,
        targetOffsetX = { it },
    )

internal fun meloXContentEnter() = fadeIn(
    tween(MeloXMotion.ContentEnterMillis, easing = FastOutSlowInEasing),
)

internal fun meloXContentExit() = fadeOut(
    tween(MeloXMotion.ContentExitMillis, easing = FastOutSlowInEasing),
)

/**
 * 播客首页**切分类**的一对动作：新旧分区各做 1/4 宽的视差位移 + 淡入淡出。
 *
 * 纯淡出会露出下层「精选播客」，读成闪烁；纯滑动又太像翻页。位移只有 1/4 宽，
 * 配合淡入淡出读起来是「这一排分区换了」而不是「翻页」。
 *
 * ⚠ 位移方向由 [dir] 决定、不固定从左往右 —— 调用方按新旧分类 id 的先后给 ±1，
 *   这样往回切（分类 → 为你推荐）也是同一套手感，不会有「只有前进才滑」的不对称。
 * ⚠ 只用在 [androidx.compose.animation.AnimatedContent] 的 `transitionSpec` 里，
 *   不要拿去做整页推入（整页要满宽滑动 + 自带不透明背板，见 [meloXPageEnter]）。
 */
internal fun meloXCategoryEnter(dir: Int): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(MeloXMotion.CategorySwitchEnterMillis, easing = FastOutSlowInEasing),
        initialOffsetX = { it / 4 * dir },
    ) + fadeIn(tween(MeloXMotion.CategorySwitchEnterMillis, easing = FastOutSlowInEasing))

internal fun meloXCategoryExit(dir: Int): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(MeloXMotion.CategorySwitchExitMillis, easing = FastOutSlowInEasing),
        targetOffsetX = { it / 4 * dir },
    ) + fadeOut(tween(MeloXMotion.CategorySwitchExitMillis, easing = FastOutSlowInEasing))

internal fun meloXPanelEnter(initialScale: Float = 0.96f) =
    fadeIn(spring(dampingRatio = 0.86f, stiffness = MeloXMotion.PanelEnterStiffness)) +
        scaleIn(
            initialScale = initialScale,
            animationSpec = spring(dampingRatio = 0.86f, stiffness = MeloXMotion.PanelEnterStiffness),
        )

internal fun meloXPanelExit(targetScale: Float = 0.98f) =
    fadeOut(spring(dampingRatio = 0.90f, stiffness = MeloXMotion.PanelExitStiffness)) +
        scaleOut(
            targetScale = targetScale,
            animationSpec = spring(dampingRatio = 0.90f, stiffness = MeloXMotion.PanelExitStiffness),
        )
