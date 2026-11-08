package com.lladlam.melox.ui.glass

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lladlam.melox.MeloXAppVisibility
import com.lladlam.melox.ui.MeloXUiMotionRuntime
import com.lladlam.melox.ui.player.MeloXNowPlayingControlsHeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * 冷启动「管线预热」（2026-10-19）。
 *
 * ### 要治的现象
 * 清数据后冷启动、**第一次**打开播放页，转场那 350ms 会掉 1~3 帧（用户口述「还是会卡一次」）。
 * 日志取证（`MeloX-logs-1792406020132`）：
 *
 *     AdrenoVK-0: Shader compilation failed for shaderType: 0|6   → 141 次
 *     AdrenoVK-0: Pipeline create failed                          → 142 次
 *
 * 同期 UI 线程 `lateness` p50 0.25ms / p90 3.52ms —— **主线程完全无辜**。
 * 掉帧值全是 vsync 整数倍（33.19 = 3×11.06、44.26 = 4×11.06），说明帧已经提交、
 * 卡在**下游**（RenderThread / GPU 驱动）。⇒ 是 Skia 在第一次用到某个 pipeline 时
 * 现场向 Adreno 驱动申请编译（同步阻塞 RenderThread），编完就进进程内缓存，
 * 所以「冷启动卡一次，之后 2.4%」。
 *
 * ### 这个文件做什么
 * 在**用户不敏感的时刻**把播放页最贵的那几族 draw 各画 **一次**，
 * 把驱动编译提前吃掉，之后再整块移除。时机分两条（2026-11-03 改）：
 *   · **冷启动**：**首帧提交后 400ms**（原来是 `ON_RESUME + 2500ms` ⇒ 有媒体时
 *     用户 ~1s 内点开迷你栏，预热还没跑 ⇒ 第一次开页当场编 shader ⇒ 巨卡）；
 *   · **回前台**：`ON_RESUME + 500ms`（Surface 重建 ⇒ pipeline 缓存被清）。
 * 现在一共五块（见下方 ①~⑤）：
 * ①~③ 是 48dp 小块（外挂 3.5 倍缩放，走 morph 的缩放采样分支），
 * ④ 是**按真实尺寸**（全屏宽 × `MeloXNowPlayingControlsHeight`）的模糊带，
 * ⑤ 是**按真实全屏**的流光背景（位图 High 滤波采样 + 垂直渐变）。
 *
 * ⚠⚠ **它走的必须是真实生产组件**，不能手搓 `RenderEffect`：
 *   ① kyant0 的 `lens` 是 **AGSL `RuntimeShader`**，编译单元是 shader 源码本身，
 *      shader 一变就是另一条 pipeline —— 只有调同一个 `meloXGlassSurface` 才命中；
 *   ② 玻璃内部还有 `vibrancy / blur / Highlight / Shadow / InnerShadow` 与
 *      `BlendMode.Screen / Plus / Hue`，都是独立 pipeline，抄不出来。
 *
 * ⚠ **为什么非要挂在 UI 树里、不能后台离屏画**：
 *   Skia 的 pipeline / shader 缓存是**按 `GrDirectContext` 私有**的。主窗口用 HWUI 那一套
 *   context；自己 new 一个 `HardwareRenderer` / `GraphicsLayer.toImageBitmap()` 就是
 *   **另一个 context**，编出来的 pipeline 主窗口用不到，白烧一次 GPU（这也是本仓
 *   `BottomBarToneSampler` 踩过的坑）。所以只能在**主窗口的渲染树里**画。
 *
 * ### 代价
 * ⚠ 2026-10-25 更正：**不再是「一块 48dp」**。①~③ 仍是 48dp，但 ④ 是整条底栏
 *   模糊带（全屏宽 × 279dp、24dp 高斯）、⑤ 是整屏的流光背景 —— 真实尺寸本来就是
 *   这条路径的判据（见 ④ 的说明），代价随之上去。整块只画 ~120ms（≈8 帧）后从
 *   组合里移除。
 * 编译开销本身就是「迟早要付的那一笔」，这里只是把它挪到安静时刻；
 * 万一同帧抢到，最坏是掉 1~2 帧，且发生在首屏已经可用之后。
 * ⚠ 若某次真机测量发现这 8 帧自己就把首屏拖卡：先量 ④/⑤ 各自那一笔，再决定
 *   是缩 ⑤ 的目标尺寸、还是把 ④⑤ 拆成两轮（先 ①~③，隔几帧再 ④⑤）。
 *
 * ### 已知不确定性（别当成已证结论）
 * 这**不能**保证覆盖播放页全部 89 次创建 —— 共享元素 morph（`renderInOverlayDuringTransition`
 * 的跨 RenderNode 引用、大图层缩放、路径裁剪）那一族在这里画不出来，它们可能仍然
 * 要等到第一次转场才编译。要确认收益，判据是**同一进程第二次开播放页 vs 第一次**
 * 的 `player-open` 掉帧率差（当时用本地帧采样器测得；该工具已于 2026-10-30 移除，
 * 复测请改用 `atrace` / `FrameMetrics`）。
 */
private const val PREWARM_ENABLED = true

/**
 * 冷启动预热的等待（2026-11-03，从 2500ms 改到 400ms）。
 *
 * ⚠⚠ 原来的 2500ms 是「首屏落定后再等 2.5 秒」的读法，但**冷启动的致命场景恰是「有媒体」**：
 *   Home 立刻显示迷你栏，用户 ~1s 内就点开播放页 ⇒ 预热在 2.5s 之后才跑，
 *   用户的**第一次**开页当场编译 shader ⇒ 巨卡（`Pipeline create failed` ×142，
 *   掉帧值全是 vsync 整数倍 ⇒ 卡在 RenderThread / Adreno 驱动同步编译）。
 * ⇒ 现在改成「**首帧提交后 400ms**」：Home 还空着（用户还在等数据/找歌）时就把
 *   pipeline 编掉，早于任何可能的点击。
 *
 * ⚠ 400ms 是折中：再早会与首帧末段抢 RenderThread（首帧尚未提交完就画预热块），
 *   再晚就逼近「用户已经点开」的时间窗。
 */
private const val PREWARM_COLD_FIRST_FRAME_DELAY_MS = 400L

/** 预热块持有期间的**让路轮询步长**（≈1 帧 @60Hz）。见 `runPrewarm` 里的持有循环。 */
private const val PREWARM_HOLD_STEP_MS = 16L

/**
 * 回前台（熄屏唤醒 / 退后台归来）后的预热延迟。比冷启动短很多：
 * 用户醒来大概率马上就要点播放页，得在那一发之前把 pipeline 重新编好；
 * 但也要给 Surface 重建/首帧留一点余量，和他们 onResume 里重投帧率票的节奏一致。
 */
private const val PREWARM_RETURN_DELAY_MS = 500L

/** 保持可见的时长（≈8 帧 @90Hz）。只要覆盖一次真实提交即可，不需要更久。 */
private const val PREWARM_HOLD_MS = 120L

/**
 * 与用户动画「让路」的最长等待（2026-10-22）。
 * 播放页正在运动时不挂预热块（否则两边抢 RenderThread，用户读作「卡炸」）；
 * 落定后再补。等超过这个上限就放弃本轮 —— 用户还在连续操作，不能为了预热去追。
 */
private const val PREWARM_DEFER_MAX_MS = 1_500L

/** 让路时的轮询步长。 */
private const val PREWARM_DEFER_STEP_MS = 100L

/**
 * 等「回前台结算窗口」过期的上限（2026-10-22 第四轮）。
 * 窗口本身 1.5s，留 300ms 余量；超过就照常预热（宁可撞，也不能永久不预热）。
 */
private const val PREWARM_SETTLE_MAX_MS = 1_800L

/**
 * 预热块的边长。**不要缩到 1~8dp**：kyant0 的 `DrawBackdropNode` 会按
 * `effectScope.padding`（模糊/折射留边）把图层撑大，尺寸小于留边时那条路径的
 * 边界行为和真实卡片不同，有画出别的变体（甚至触发库内除零）的风险。
 * 48dp 已经小到不可见，又足够接近真实卡片的量级。
 */
private val PREWARM_SIZE = 48.dp

/**
 * 预热块的**外放大倍数**（2026-10-22，治「转屏飞入 / 迷你栏开合」的顿挫）。
 *
 * 上文「已知不确定性」那段写过：共享元素 morph 那一族（跨 RenderNode 引用、大图层缩放、
 * 路径裁剪）用 48dp 的方块**画不出来**，仍要等第一次转场才编译。
 * 而用户点名的两个「顿挫」场景恰好**全是 morph**：
 *   · 迷你栏 ↔ 播放页（`np-controls` / `np-header` / `np-selector` 等 6 个 key）
 *   · 竖屏 ↔ 横屏（`npMorphElement` 把控件从竖屏矩形形变到横屏矩形）
 *
 * GPU 驱动（AdrenoVK）编 pipeline 是**同步阻塞 RenderThread** 的，一编就是 30~80ms
 * ⇒ 表现成「动画开始第一帧顿一下」，而不是持续低帧率。这正是用户区分的
 * 「顿挫」和「掉帧」的差别。所以要提前把这一族的变体编掉。
 *
 * 为什么是 3.5：morph 期间图层按 `graphicsLayer{scaleX/scaleY}` 放大，Skia 会选
 * **缩放采样 / mipmap / tiled-render 分支**，与 1:1 直画不是同一条 pipeline。
 * 48dp × 3.5 = 168dp，量级足够走出那条分支，同时仍在屏内（transformOrigin 设在左上角）。
 *
 * ⚠ 不要为了「更接近真实」而放大到全屏：预热块会**真的**每帧做一次该路径的绘制，
 *   全屏 scale + 30dp 模糊在 120ms 里能烧掉 8~10ms/帧，反而把首屏拖卡。
 */
private const val PREWARM_MORPH_SCALE = 3.5f

/**
 * ⑤ 用的流光探针位图尺寸 = 真实网格位图的 **High 档**（2026-10-25）。
 * 见 `MeloXFlowingLightBackdrop`：`meshWidth`（Low 24 / Balanced 32 / High 48）、
 * `meshHeight = meshWidth * 23 / 10`。
 *
 * ⚠ 只画 48 这一档就够：pipeline 由「滤波模式 / 平铺模式 / alpha 类型」选出来，
 *   源位图尺寸只决定纹理大小，不换族。
 * ⚠ 位图**必须不透明**（这里填死一个灰值）：全透明图像的绘制有被 Skia 当
 *   nothing-to-draw 直接跳过的风险，跳过 = 什么都没预热。
 */
private const val PREWARM_FLOW_MESH_WIDTH = 48
private const val PREWARM_FLOW_MESH_HEIGHT = PREWARM_FLOW_MESH_WIDTH * 23 / 10

/**
 * 管线预热节点。挂在 `LocalMeloXBackdrop` 的 provider **以内**（玻璃需要背板），
 * 且要在兄弟序列的**最后**（保证不被 fillMaxSize 的页面盖掉 —— HWUI 不做遮挡剔除，
 * 但没必要赌）。
 *
 * ⚠⚠ 2026-10-19 晚：由「冷启动一次」改成「每个前台会话 / `ON_RESUME` 都重跑一次」。
 *   现在冷启动预热的 cache 是**按进程的 GrDirectContext 私有**的，而熄屏 / 退后台再回
 *   来会**重建 Surface / Vulkan 上下文**（见 `MainActivity.onResume` 注释「从后台回前台
 *   时 Surface 会重建」）⇒ 那批 pipeline 缓存又被清掉 ⇒ 闲置久后**第一次**开播放页又掉帧
 *   （第二次才顺）。所以回前台时也要补一次，才能兜住「久了第一下发卡」这个场景。
 */
@Composable
internal fun MeloXGlassPipelinePrewarm() {
    if (!PREWARM_ENABLED) return
    var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    /**
     * 预热流程本体（**不含触发时机**），两条触发路径共用。
     *
     * 局部 suspend 函数：捕获的是 `remember` 出来的 `MutableState` 本身，
     * 跨重组稳定 ⇒ 哪条路径启动都写到同一个可见性开关上。
     */
    suspend fun runPrewarm() {
        // ★ 2026-10-22：**不与播放页动画抢 RenderThread**。
        //   用户口径：「退后台一段时间，打开迷你栏动画直接卡炸，第二次不卡」。
        //   这条时间线是：回前台 → 500ms 时挂预热块 → 画 120ms。而用户点迷你栏的时刻
        //   恰好也在这个窗口里 ⇒ 预热块那 8 帧的玻璃 + 驱动同步编译，正好压在
        //   展开动画的头几帧上，两边互相抢 ⇒ 「卡炸」。
        //   所以：运动进行中就等，落定再补；等太久（用户已经在连续操作）这一轮就放弃，
        //   下次回前台还会再来一次 —— 绝不为了预热去打断或延迟用户的动画。
        var waited = 0L
        while (MeloXUiMotionRuntime.anyPlayerMotion && waited < PREWARM_DEFER_MAX_MS) {
            delay(PREWARM_DEFER_STEP_MS)
            waited += PREWARM_DEFER_STEP_MS
        }
        if (MeloXUiMotionRuntime.anyPlayerMotion) return
        // ★ 2026-10-22 第四轮：回前台**结算窗口**内不许开预热块（见 `MeloXUiMotionRuntime`）。
        //   实测回前台 500ms 挂块，正好落在用户点迷你栏的那段窗口里：115 帧 / 79fps /
        //   掉 45 帧，其中一帧 `commandIssue` 89.55ms；紧随其后的 `shell-transition`
        //   是 121fps 掉 0 —— 说明坏的是「预热和动画抢」，不是动画本身。
        //   ⚠ 这里**等**而不是**放弃**：窗口有界（1.5s）且自动过期，不属于「用户正在
        //     连续操作」；预热本来就是提前付未来的账，晚 1.5s 补上照样有效，
        //     而抢走用户正要看的那一下等于白预热。
        var settleWaited = 0L
        while (MeloXUiMotionRuntime.foregroundSettling && settleWaited < PREWARM_SETTLE_MAX_MS) {
            delay(PREWARM_DEFER_STEP_MS)
            settleWaited += PREWARM_DEFER_STEP_MS
        }
        visible = true
        // ★★ 2026-11-03：持有期间**主动让路** —— 用户一旦开始播放页动画就立刻收块。
        //   原来只有「开块**前**」的一次让路判断；开块后的 120ms 里若用户点开迷你栏，
        //   预热块那几帧的玻璃 + 驱动同步编译正好压在展开动画的头几帧上 ⇒ 仍是「卡炸」。
        //   现在每 16ms 查一次 `anyPlayerMotion`，命中就立刻收块（已经发起的那一帧编译
        //   会做完，但不再叠加新的绘制）⇒ 预热永远不会和用户的动画正面抢。
        var held = 0L
        while (held < PREWARM_HOLD_MS) {
            delay(PREWARM_HOLD_STEP_MS)
            held += PREWARM_HOLD_STEP_MS
            if (MeloXUiMotionRuntime.anyPlayerMotion) break
        }
        visible = false
    }

    // ── 触发路径 ①：冷启动（前台会话 1）—— 首帧提交后 + 400ms ──────────────
    //   ★★ 2026-11-03（治「冷启动打开动画巨卡」）：原来冷启动是 `ON_RESUME + 2500ms`，
    //   而有媒体时 Home 立刻显示迷你栏、用户 ~1s 内点开 ⇒ 预热压根没跑 ⇒ 第一次开页
    //   当场编 shader ⇒ 巨卡。现在挪到首帧之后，让它赶在用户点击之前付掉。
    //   ⚠ `withFrameNanos` 只保证「进入了下一帧」，真正提交要再等一帧 ⇒ 之后还留了
    //     400ms 余量，两者合起来既不抢首帧末段，也早于用户的点击。
    LaunchedEffect(Unit) {
        if (MeloXAppVisibility.foregroundSessionId > 1L) return@LaunchedEffect // 非冷启动 → 交给路径 ②
        withFrameNanos { }
        delay(PREWARM_COLD_FIRST_FRAME_DELAY_MS)
        // 等待期间若曾回前台（会话号变了），这一轮交给路径 ②，避免重复预热。
        if (MeloXAppVisibility.foregroundSessionId > 1L) return@LaunchedEffect
        runPrewarm()
    }

    // ── 触发路径 ②：回前台（熄屏唤醒 / 退后台归来）—— ON_RESUME + 500ms ──────
    //   Surface / Vulkan 上下文重建 ⇒ 进程内 pipeline 缓存被清 ⇒ 久了第一次开页又掉帧。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            // 冷启动由路径 ① 负责（时机不同），这里只管「回来」。
            if (MeloXAppVisibility.foregroundSessionId <= 1L) return@launch
            delay(PREWARM_RETURN_DELAY_MS)
            runPrewarm()
        }
    }
    if (!visible) return

    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth()) {
    Row(
        // alpha 0.01（≈2.5/255）：肉眼不可辨，但**不能写 0f** —— alpha 0 的 layer
        // 会被 Skia 在取 pipeline 之前就 quick-reject，等于什么都没预热。
        //
        // ⚠ 2026-10-22：外层再挂一层 **scale 3.5 + transformOrigin 左上角**，让下面这几族
        //   绘制走到「大图层缩放」分支（共享元素 morph 用的就是这条），见 [PREWARM_MORPH_SCALE]。
        //   缩放挂在**外层**、alpha 挂在同一层：两者都在绘制期求值，合并成一个 RenderNode，
        //   不会额外多一层离屏。
        modifier = Modifier.graphicsLayer {
            alpha = 0.01f
            scaleX = PREWARM_MORPH_SCALE
            scaleY = PREWARM_MORPH_SCALE
            transformOrigin = TransformOrigin(0f, 0f)
        },
    ) {
        // ① 完整液态玻璃（vibrancy + blur + AGSL lens + Highlight/Shadow/InnerShadow
        //    + Screen/Plus/Hue 混合）—— 播放页大面积玻璃与底栏胶囊共用这一族。
        Box(
            modifier = Modifier
                .size(PREWARM_SIZE)
                .meloXGlassSurface(shape = shape, surfaceColor = Color.White),
        )
        // ② 纯模糊层（`meloXBackdropBlur`）：歌词面板 / 队列面板这类「只糊不折」的表面。
        //    ⚠ 单列出来是因为 `frostedGlassEnabled` 关闭时 ① 也只会走 blur ——
        //    那种情况下 ① 已经把这条路径覆盖掉，多画一次无害（缓存命中即返回）。
        Box(
            modifier = Modifier
                .size(PREWARM_SIZE)
                .meloXBackdropBlur(shape = shape, blurRadius = 24.dp),
        )
        // ③ 离屏合成 + `DstIn` 挖空（2026-10-22 新增）。
        //    队列页与歌词页的边缘渐隐是 `graphicsLayer{compositingStrategy = Offscreen}` +
        //    `drawRect(verticalGradient, BlendMode.DstIn)`。这条路径要 saveLayer +
        //    独立 blend 状态，是**另一族 pipeline**，①② 都覆盖不到。
        //    ⚠ 缺了它，用户第一次切到队列页 / 歌词页时那一帧仍会同步编译 ⇒ 顿一下。
        Box(
            modifier = Modifier
                .size(PREWARM_SIZE)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.25f to Color.Black,
                            0.75f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .background(Color.White, shape),
        )
        // ⚠ 2026-11-05：原 ⑥「大尺寸图形层探针」（304×544dp / 304×448dp 两块
        //   alpha=0.01 的 Box）已删除。真机 atrace 实证：目标 1216×1536~2496 尺寸族
        //   **全部在首次展开后 +0.38~0.57s 才创建**，冷启早期与预热窗口一个都没有；
        //   且连续两次展开仍重新创建同尺寸大层 ⇒ HWUI 不按尺寸跨展开复用，
        //   「按固定尺寸预热层纹理」在原理上不成立，只会白烧 GPU。⛔ 请勿再加回。
    }

    // ④ **按真实尺寸**的底栏模糊带（2026-10-22 新增，治「退后台后第一次开播放页卡」）。
    //
    //   上面 ①②③ 都只有 48dp。但用户指认的卡点在**底栏**：它是全屏宽 × 279dp、
    //   24dp 高斯。模糊是**面积敏感**的 —— 1024px 宽和 200px 宽的输入在 Skia 里可能
    //   走不同的降采样级数 / 分块路径，48dp 那块**命中不了**真实尺寸那一条。
    //   于是「第二次不卡、第一次卡」就被解释了：第一次现场编译真实尺寸那一族。
    //
    //   所以这里放一条**与底栏同宽同高**的模糊带，alpha 仍是 1/255（肉眼不可辨），
    //   只为走完整条绘制路径。位置在屏幕最上方 279dp —— 反正 1/255，看不见。
    //
    //   ⚠ 高度故意 import 真实常量 `MeloXNowPlayingControlsHeight`，不写魔数：
    //     底栏改高时这里跟着变，否则又退化成"预热的不是真实尺寸"。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MeloXNowPlayingControlsHeight.dp)
            .graphicsLayer { alpha = 0.01f }
            .meloXBackdropBlur(shape = RectangleShape, blurRadius = 24.dp),
    )

    // ⑤ 流光背景的 Canvas 体（2026-10-25 新增）—— `MeloXFlowingLightBackdrop` 画的那两笔：
    //   · 一张 48×110 的小位图用 `FilterQuality.High` 放大铺满全屏；
    //   · 一层「下半区压暗」的垂直渐变。
    //
    //   ⚠ ①②③④ **一笔都没覆盖它**：①② 是玻璃/模糊，③ 是离屏合成 + `DstIn`，④ 是模糊带 ——
    //     「位图采样（High 滤波）」和「线性渐变填充」是另外两族 pipeline。播放页在
    //     `playerBackgroundMode == FlowingLight`（默认档）下**第一帧就铺它**，而这块背景
    //     又是 `MeloXNowPlayingSharedHost` 里被 `sharedBounds`/`RemeasureToBounds` 逐帧
    //     重测的那一层 ⇒ 开页 morph 的头几帧就要现场编译这一族。
    //
    //   ⚠ 尺寸照抄真实全屏（真实 Canvas 是 `Modifier.fillMaxSize()`，宿主是全屏 Box）：
    //     同 ④ 的道理 —— 画一条不是真实尺寸/真实路径的近似，等于白烧一次 GPU。
    //   ⚠ alpha 0.01 挂**外层图层**，不写进 `drawImage(alpha = …)`：真实那一笔的 paint
    //     alpha 是 1（不透明源 + SrcOver），用 0.01 的 paint alpha 去画有命中另一条
    //     「半透明源」pipeline 的风险 —— 那就预热错了族（和 ④ 同款写法）。
    val flowMeshProbe = remember {
        Bitmap.createBitmap(
            PREWARM_FLOW_MESH_WIDTH,
            PREWARM_FLOW_MESH_HEIGHT,
            Bitmap.Config.ARGB_8888,
        ).apply { eraseColor(0xFF4A4A4A.toInt()) }.asImageBitmap()
    }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 0.01f },
    ) {
        drawImage(
            image = flowMeshProbe,
            dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.High,
        )
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Black.copy(alpha = 0.04f),
                    0.52f to Color.Black.copy(alpha = 0.10f),
                    1f to Color.Black.copy(alpha = 0.48f),
                ),
            ),
        )
    }
    }
}
