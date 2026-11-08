package com.lladlam.melox.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.media3.common.Player
import kotlinx.coroutines.flow.drop
import kotlin.math.abs
import kotlin.math.min
import com.lladlam.melox.ui.glass.MeloXShapes
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.meloXBackdropBlur
import com.lladlam.melox.ui.meloXEdgeOverscrollHaptic
import com.lladlam.melox.ui.meloXHaptic

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MeloXQueuePanel(
    state: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
    showSongHeader: Boolean = true,
    interactive: Boolean = true,
    // 用户与队列页发生交互（点模式按钮 / 点行 / 拖动列表 / 点空白处）时通知调用方。
    // 竖屏场景用它唤出**底部播放控件条**（暂停 / 上一首 / 下一首 + 页面选择器）——
    // 那条在歌词页与队列页静置自动隐藏（同歌词页沉浸口径），交互一次就回来。
    onInteract: () -> Unit = {},
    /**
     * 顶部四个模式按钮（随机 / 循环 / 无尽·心动 / 自动过渡）是否可见。
     *
     * ★ 横屏（手机横屏 / 平板横屏）采用**与歌词页同款**的自动显隐机制：
     * 调用方把 [MeloXNowPlayingScene] 里那条 `showsLyricsControls` 状态传进来 ——
     * 进入队列页 3 秒无操作后它会被自动置 false（沉浸口径，与底部三键浮层共用
     * 同一份计时器与 `setLyricsControlsVisible(true)` 唤醒逻辑），于是四个按钮随
     * 底部控件条一起淡出，列表顺势铺满整屏；手指点行 / 下拉滑 / 点按钮时又被唤回。
     * 竖屏恒为 true（按钮常驻，不占多少纵向空间，且竖屏没有"每屏歌太少"的痛点）。
     */
    modeControlsVisible: Boolean = true,
    /**
     * 紧凑模式（横屏开关）：外壳内边距与按钮高度等比收窄，给列表让出约 24dp
     * 纵向高度（横屏可视高度只有竖屏一半左右，少一行按钮 ≈ 多看到半首歌）。
     * 竖屏恒 false ⇒ 尺寸与旧版逐像素一致。
     */
    compact: Boolean = false,
    // ★ 上下边缘渐隐（2026-10-19 四轮，改为**歌词页同款**）：
    //   与 `MeloXIOSLyricsPanel` 的 `edgeFadeTop / edgeFadeBottom / edgeFadeBottomTerminal`
    //   一一对应、同一套实现（见下方 `queueEdgeFade`）。默认值只在横屏 / 平板等
    //   "没有常驻控件面板"的调用点生效 ⇒ 那些地方不传即行为不变。
    fadeTop: Dp = QueueListTopFade,
    fadeBottom: Dp = QueueListBottomFade,
    /**
     * 底部**归零点**距列表视口的距离（0 = 归零点就在视口底沿）。
     * 该点以下恒透明。竖屏场景传「底部控件面板的顶沿」，于是列表可以**铺满整屏**
     * （不再用 padding 给面板让位），而面板区域内本来就没有任何内容画出 ⇒ 既没有
     * 白白让出的空白，也不会出现"行滑进磨砂面板底下"。与歌词页 `edgeFadeBottomTerminal`
     * 完全同一套做法（那边解决的是白色歌词从 8% 黑模糊底板渗出）。
     */
    fadeBottomTerminal: Dp = 0.dp,
    /**
     * 播放页是否正在**运动**（页间切换 或 横竖屏切换，2026-10-22）。
     *
     * 只用来把顶部四键胶囊的背景模糊在运动期间降一档（20dp → 10dp），落定即恢复。
     * 那块胶囊是全屏宽，且整块跟着队列层做 scale / translation —— 运动期间它每帧
     * 都要重采背景 + 重做高斯，而这段时间人眼锁的是移动中的封面与队列行，
     * 模糊档位差看不出来。
     *
     * 默认 `false` ⇒ 横屏 / 平板等调用点不传即逐像素不变。
     */
    motionAnimating: Boolean = false,
) {
    val density = LocalDensity.current
    val currentEntry = state.queue.getOrNull(state.currentIndex)
    val history = if (state.currentIndex > 0) state.queue.take(state.currentIndex) else emptyList()
    val upcoming = if (state.currentIndex >= 0 && state.queue.isNotEmpty()) {
        state.queue.drop(state.currentIndex + 1)
    } else emptyList()
    val manualQueue = upcoming.filter { it.origin == MeloXQueueOrigin.Manual }
    val continuing = upcoming.filter { it.origin != MeloXQueueOrigin.Manual }
    val listState = rememberLazyListState()
    val historyItemCount = if (history.isEmpty()) 0 else history.size + 1

    LaunchedEffect(state.mediaId, historyItemCount) {
        runCatching { listState.scrollToItem(historyItemCount) }
    }

    // 唤出底部控件条（暂停 / 上一首 / 下一首那条）的手势**只有一种：手指向下滑**。
    // ⚠ 只挂 nestedScroll 不够：真机实测队列页拖动列表唤不出控件条
    //   （`onPreScroll` 在原地点被吞，没派发到这条连接）⇒ 改在指针层自己判位移。
    //   仍走 Initial pass 旁听、**不消费**任何事件 ⇒ 列表照常滚动、行照常点击、
    //   长按拖拽调序全不受影响。
    val latestInteract = rememberUpdatedState(onInteract)

    val topFadePx = with(density) { fadeTop.toPx() }
    val bottomFadePx = with(density) { fadeBottom.toPx() }
    val fadeTerminalPx = with(density) { fadeBottomTerminal.toPx() }
    val hasEdgeFade = topFadePx > 0f || bottomFadePx > 0f || fadeTerminalPx > 0f

    // ⚠ 结构决定观感（2026-10-18 用户三次否掉「区域盖在歌单上」的做法后定稿）：
    //
    //   Column { 顶部模式区（**独立占位**）; 列表 }
    //
    // 模式区是列表的**兄弟**，不是盖在列表上的浮层 ⇒ 那块区域里物理上不存在歌单内容，
    // 也就不可能「透出歌曲」。之前两版都把模式区做成 overlay 再靠背板采样去糊住下方歌曲：
    //   ① 组合背板（场景 + 列表）⇒ 每帧两个动态层互相激励，实测闪烁；
    //   ② 单背板（只录列表）⇒ `rememberLayerBackdrop` 录不到图层坐标，
    //      `LayerBackdrop.drawBackdrop` 首行即 return，只剩下 12% 暗罩
    //      ⇒ 歌曲原样清晰透出 = 用户看到的「直接透明看到歌单」。
    //   ⇒ 不再让任何采样层去"盖住"歌单，改为让它**根本不在那里**（模式区的材质
    //     只用来做质感，不承担遮挡职责）。
    //
    // 「截断」的真正来源不是模式区，而是**列表视口边缘把行切一刀**：
    //  · 顶沿（上游原版 Column 布局就存在）⇒ 最上面那行永远半行卡着；
    //  · 底沿 ⇒ 底部控件条收起后列表直达屏幕底，行切在小白条（手势条）区域。
    // 两处统一用渐隐遮罩（`queueEdgeFade`，等价 Apple Music 的 container edge fade）：
    // 行在抵达边缘之前就淡成透明，没有硬断面 —— 与歌词页 `lyricsEdgeFade` 同一手法。
    Column(modifier = modifier.fillMaxSize()) {
        if (showSongHeader && currentEntry != null) {
            QueueSongHeaderSurface(currentEntry)
        }

        // 顶部模式区：独立一段，占据 64dp 实高（背板 8 + 按钮 48 + 背板 8）。
        // 它下面同样没有列表内容 —— 列表从它下方开始。        // ★ 横屏自动显隐：可见性由 [modeControlsVisible] 驱动（= `showsLyricsControls`，
        //   与底部三键浮层同一份计时器）。淡出时 `shrinkVertically` 把这段高度收掉，
        //   下方列表 `weight(1f)` 顺势铺满整屏 ⇒ 沉浸态下多看到一整屏歌；
        //   唤醒（`onInteract` → `setLyricsControlsVisible(true)`）后 `expandVertically` 回来。
        // ⚠⚠ **竖屏不许走 AnimatedVisibility**（2026-10-20 修 bug）：
        //   `AnimatedVisibility` 首次组合时内部 `MutableTransitionState` 的 currentState
        //   仍是 false、targetState 才是 true ⇒ **第一帧按退出态测量**
        //   （`shrinkVertically` + `shrinkTowards = Top` ⇒ 高度 0、内容贴顶），
        //   之后才播 enter 动画。
        //   竖屏 `modeControlsVisible` 恒 true、本不该有任何动画，但只要这棵 Column
        //   被重建（切页 / `npMorphElement` 重新配对 / `showResidentLayers` 翻转），
        //   就会重新走一次首次组合 ⇒ 那一帧按钮栏"偏上且被压扁"，观感就是**偶发错位**，
        //   再进一次页面时组件已在树中、不再复现（用户口径：「偶发四个按钮偏上，
        //   再进一次就没了」）。
        //   ⇒ 竖屏（visible 恒 true）直接渲染，从结构上消掉那一帧；
        //     只有横屏才需要显隐动画。
        // ⚠ 竖屏还必须在按钮栏**上方**显式补一段间距（2026-10-20 用户报障
        //   「那四个按钮直接过于靠上」）：
        //   之前走 AnimatedVisibility 时，enter = expandVertically(Top) + fadeIn(180ms)
        //   —— 按钮栏是「晚 180ms 出现、且从上往下展开」的，主观上不会读成"贴顶"。
        //   改成直接渲染后同一帧就位，调用点给的 `padding(top = 80dp)` 就不够了
        //   （那 80dp 本来是给场景曲名栏让位的，不是给按钮栏的呼吸感）。
        //   歌词页同位置用 88dp（见 MeloXIOSNowPlayingScene.kt 的 `.padding(top = 88.dp)`），
        //   按钮栏是实心磨砂块、比文字更"重"，取 96dp 与顶部那一行拉开。
        //   横屏不受影响：它的间距由自己的紧凑档与显隐动画共同决定。
        val modeControlsTopGap = if (compact) 0.dp else 16.dp
        if (modeControlsVisible) {
            QueueModeControlsSurface(
                state = state,
                interactive = interactive,
                onInteract = onInteract,
                compact = compact,
                motionAnimating = motionAnimating,
                modifier = Modifier.padding(top = modeControlsTopGap),
            )
        } else {
            AnimatedVisibility(
                visible = false,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(180, easing = FastOutSlowInEasing)),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(160, easing = FastOutSlowInEasing)),
            ) {
                QueueModeControlsSurface(
                    state = state,
                    interactive = interactive,
                    onInteract = onInteract,
                    compact = compact,
                    motionAnimating = motionAnimating,
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // 手势（2026-10-18 用户口径）：**只有手指向下滑**才唤出底部控件条，
                // 上滑 / 点按都不唤出。实现上仍走 Initial pass 旁听、不消费任何事件
                // ⇒ 列表照常滚动、行照常点击、长按拖拽调序全不受影响。
                .pointerInput(interactive) {
                    if (!interactive) return@pointerInput
                    val threshold = QueueRevealDragThreshold.toPx()
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val originY = down.position.y
                            var revealed = false
                            // 跟踪这一根手指，位移**向下**超过阈值才唤出；抬起即结束。
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (!revealed && change.position.y - originY >= threshold) {
                                    revealed = true
                                    latestInteract.value.invoke()
                                }
                            }
                        }
                    }
                },
        ) {
            // ★⚠ 渐隐遮罩挂在**包一层 Box** 上、不直接挂在 LazyColumn 上（2026-10-19 定稿）：
            //   歌词页（`MeloXIOSLyricsPanel`）的边缘渐隐是真机验证有效的，它的挂法 =
            //   `Box { LazyColumn }`、fade 挂在 Box；队列页此前直接挂在 LazyColumn 本体上，
            //   真机实测（23:25 截图像素测量）**底带完全没画出来**——「Rendezvous」标题
            //   白字亮度直到切断线都保持 100%（y=1740 亮像素 1435 个，与中部完整行同级），
            //   行被视口一刀切断。这是歌词页与队列页之间唯一的结构差异，照抄有效的那份。
            //
            // ★ 渐隐本身也已换成歌词页那一套（单条归一化线性渐变 + 归零点），取代此前
            //   「两条独立渐隐带 · 顶线性 / 底三次方」的拼法 —— 详见 `queueEdgeFade`。
            //   `rememberSnapFlingBehavior` 与 `snapToBottomRowEdge()` 都已删除：吸附只能
            //   让静止态"不留残行"，代价是每次松手都跳一下（用户：「硬切方案视觉上不好看」）。
            //   渐隐接管后，行切在哪都无所谓，它自己会溶解在带子里。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 顶/底渐隐：Offscreen 是必需的 —— `BlendMode.DstIn` 只对离屏图层内部
                    // 有意义，否则会把父级画布（含页面背景）一并挖空。
                    .graphicsLayer {
                        if (hasEdgeFade) compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .queueEdgeFade(topFadePx, bottomFadePx, fadeTerminalPx),
            ) {
                LazyColumn(
                    state = listState,
                    userScrollEnabled = interactive,
                    modifier = Modifier
                        .fillMaxSize()
                        // 触顶/触底橡胶回弹触感。
                        .meloXEdgeOverscrollHaptic(listState),
                    // ⚠ bottom 恒 0：列表底沿由渐隐**归零点**决定，不再靠 contentPadding 让位
                    //   ⇒ 任何底部 padding 都会在归零点之下留出一条"透明但仍在滚"的死区。
                    contentPadding = PaddingValues(top = 0.dp, bottom = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (history.isNotEmpty()) {
                        item(key = "history-title") { QueueSectionTitle("历史记录", subdued = true) }
                        items(history, key = { "history-${it.entryId}" }) { entry ->
                            QueueRow(entry, state, interactive, onInteract)
                        }
                    }

                    if (manualQueue.isNotEmpty()) {
                        item(key = "manual-title") { QueueSectionTitle("队列") }
                        items(manualQueue, key = { "manual-${it.entryId}" }) { entry ->
                            QueueRow(entry, state, interactive, onInteract)
                        }
                    }

                    item(key = "continue-title") { QueueSectionTitle("继续播放") }
                    if (continuing.isEmpty()) {
                        item(key = "continue-empty") {
                            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                                Text("没有待播放歌曲", color = Color.White.copy(alpha = .55f), fontSize = 15.sp)
                            }
                        }
                    } else {
                        items(continuing, key = { "continue-${it.entryId}" }) { entry ->
                            QueueRow(entry, state, interactive, onInteract)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 列表顶/底沿渐隐：内容在抵达视口边缘之前淡成透明，取代硬裁
 * （等价 Apple Music 的 container edge fade）。
 *
 * ★ 2026-10-19 四轮：**整段换成歌词页同款**（与 `MeloXIOSLyricsPanel.lyricsEdgeFade`
 *   同一套实现），不再是「两条独立渐隐带、顶线性 / 底三次方」的拼法。三点实质差别：
 *   ① **单条归一化渐变**画满整个视口（`0 → topStop → bottomStop → bottomEnd → 1f`），
 *      中间一段是纯不透明。原来两条各自 `drawRect`，带子内沿处斜率突变，肉眼就是
 *      一条"带的边"；单条渐变天然连续，看不出带子在哪开始。
 *   ② **线性**，不是三次方。三次方在前 1/3 就把 alpha 压到 12.5%，真机上读作
 *      「一大块空白」；线性让行一直保持足够不透明度、只在末段溶解 —— 这才是
 *      Apple Music 的观感，也是歌词页一直用的曲线。
 *   ③ **归零点可上移**（`bottomTerminalPx`）：内容可以在视口底之前就淡完。竖屏据此
 *      把归零点钉在底部控件面板的**顶沿** ⇒ 列表可以**铺满整屏**（不再用 padding 给面板
 *      让位），而面板区域内本来就什么都不画：既没有白让出来的空白，也不会有行滑进
 *      磨砂面板底下。这正是歌词页解决「白歌词从 8% 黑模糊底板渗出」的同一手法。
 *
 * ⚠ 只能与 `CompositingStrategy.Offscreen` 配对使用（调用点已设），否则
 *   `BlendMode.DstIn` 会落到父级画布上、把整屏（含背景）一并挖空。
 *
 * @param topFadePx 顶部渐隐带高度：0 = 视口顶，topFadePx = 完全显形处。
 * @param bottomFadePx 底部渐隐带高度（自**归零点**往上量）。
 * @param bottomTerminalPx 归零点距视口底的距离（0 = 归零点即视口底）。该点以下恒透明。
 */
private fun Modifier.queueEdgeFade(
    topFadePx: Float,
    bottomFadePx: Float,
    bottomTerminalPx: Float = 0f,
): Modifier = drawWithContent {
    drawContent()
    if (topFadePx <= 0f && bottomFadePx <= 0f && bottomTerminalPx <= 0f) return@drawWithContent
    val h = size.height
    if (h <= 0f) return@drawWithContent
    val topStop = (topFadePx / h).coerceIn(0f, 1f)
    // ⚠ 歌词页那边把 bottomEnd 下限钳在 .52（保证中间留一段不透明）。队列不能照抄这个
    //   下限：归零点最高可以到"控件面板顶沿"，短视口下 1 − terminal/h 本就可能小于 .5，
    //   硬钳成 .52 会把归零点画到比面板顶还高的位置（内容提前消失）。改成只保证色标
    //   单调递增即可。
    val bottomEnd = if (bottomTerminalPx > 0f) {
        (1f - bottomTerminalPx / h).coerceIn(0f, 1f)
    } else {
        1f
    }
    val bottomStop = (bottomEnd - bottomFadePx / h).coerceIn(min(topStop, bottomEnd), bottomEnd)
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            topStop to Color.Black,
            bottomStop to Color.Black,
            bottomEnd to Color.Transparent,
            1f to Color.Transparent,
        ),
        blendMode = BlendMode.DstIn,
    )
}

@Composable
private fun QueueSongHeaderSurface(entry: MeloXQueueEntry) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .meloXBackdropBlur(
                shape = shape,
                blurRadius = 20.dp,
                surfaceColor = Color.Black.copy(alpha = .10f),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(entry.artworkUrl, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.artist, color = Color.White.copy(alpha = .62f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun QueueModeControlsSurface(
    state: MeloXPlaybackUiState,
    interactive: Boolean,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
    // ★ 横屏紧凑：外壳内边距与按钮间距等比收窄。四边仍等值（圆角法则），
    //   只是整体压薄 —— 横屏可视高度只有竖屏一半，少一段 ≈ 多看到近半首歌。
    compact: Boolean = false,
    // ★ 运动期间模糊降档（见 [MeloXQueuePanel] 的 `motionAnimating`）。
    motionAnimating: Boolean = false,
) {
    // 运动期间(页间切换 / 横竖屏)彻底关模糊：运动画面里胶囊模糊档位差不可辨，落定瞬时回满档；
    // 直接削掉这块全屏玻璃的离屏高斯 pass。转屏时竖横两套同场，收益乘二。
    val blurRadius = if (motionAnimating) 0.dp else QueueControlsBlurRadius
    val surfacePadding = if (compact) QueueModeSurfacePaddingCompact else QueueModeSurfacePadding
    val rowSpacing = if (compact) 8.dp else 12.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 材质是**平滑圆角胶囊**（不是直角矩形，也不是小圆角卡片）：
            // 直接用设计系统里那个胶囊 token（kyant 连续曲率 Capsule，迷你播放器 /
            // 底栏胶囊同一个 Shape），所以外框与全 app 胶囊件曲率完全一致。
            // 采样的是**场景背板**（artwork 层，与上方曲名栏同一条链路）——
            // 它一定是不透明内容 ⇒ 这块只会是磨砂材质，不会透出任何歌单内容。
            //
            // ★ 圆角法则（2026-10-18 用户指定）：**外圆角 = 内圆角 + 内边距**。
            //   内按钮也是同一个胶囊 token（48dp 高 ⇒ 半径 24dp），外壳高度
            //   48 + 8×2 = 64dp ⇒ 胶囊半径 32dp = 24 + 8 ✓ 两层曲率同心。
            //   ⚠ 内边距必须**四边等值**：横竖 padding 不一致时，单个外圆角
            //     不可能同时满足两个方向的「+padding」，视觉上就会出现
            //     「四角对不齐、蛋壳状」——这正是这条法则容易被破坏的地方。
            .meloXBackdropBlur(
                shape = MeloXShapes.capsule,
                blurRadius = blurRadius,
                surfaceColor = Color.Black.copy(alpha = QueueControlsTintAlpha),
            )
            .padding(surfacePadding),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
            // 长按不松手 = 气泡显示当前档位的简略介绍（随状态变化），松手即消失。
            QueueModeButton(
                MeloXSymbol.Shuffle,
                state.shuffleEnabled,
                enabled = interactive,
                hint = if (state.shuffleEnabled) "随机播放已开：歌单乱序播放" else "随机播放已关：按歌单顺序播放",
                onClick = state::toggleShuffle,
                onInteract = onInteract,
                modifier = Modifier.weight(1f),
                compact = compact,
            )
            QueueModeButton(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) MeloXSymbol.RepeatOne else MeloXSymbol.Repeat,
                state.repeatMode != Player.REPEAT_MODE_OFF,
                enabled = interactive,
                hint = when (state.repeatMode) {
                    Player.REPEAT_MODE_ONE -> "单曲循环：反复播放当前歌曲"
                    Player.REPEAT_MODE_ALL -> "列表循环：播完歌单从头再放"
                    else -> "循环已关：播完歌单即停"
                },
                onClick = state::cycleRepeatMode,
                onInteract = onInteract,
                modifier = Modifier.weight(1f),
                compact = compact,
            )
            // 无尽播放按钮：仅在网易云提供「心动模式」档，非网易云退化为二态
            // （关 → 无尽播放(∞) → 关），不出现心动档。图标随状态切换：
            // 心动模式显 HeartPulse，其余显 Infinity；selected 覆盖两种「开」语义；
            // 长按气泡说明当前档位与下一步去向。自动过渡是独立按钮（见下），不参与本循环。
            val heartModeAvailable = state.heartModeAvailable
            QueueModeButton(
                if (heartModeAvailable && state.heartModeEnabled) MeloXSymbol.HeartPulse else MeloXSymbol.Infinity,
                if (heartModeAvailable) state.autoplayEnabled || state.heartModeEnabled else state.autoplayEnabled,
                enabled = interactive && !state.heartModeLoading,
                hint = when {
                    state.heartModeLoading -> "正在向网易云索取心动推荐…"
                    heartModeAvailable && state.heartModeEnabled -> "心动模式已开：队列已换成心动推荐（再次点击关闭）"
                    state.autoplayEnabled ->
                        if (heartModeAvailable) "无尽播放已开：播完自动续播相似歌曲（再次点击切换心动模式）"
                        else "无尽播放已开：播完自动续播相似歌曲（再次点击关闭）"
                    else ->
                        if (heartModeAvailable) "播放模式已关：点击开启无尽播放，再点切换心动模式"
                        else "播放模式已关：点击开启无尽播放"
                },
                onClick = state::cyclePlayMode,
                onInteract = onInteract,
                modifier = Modifier.weight(1f),
                compact = compact,
                loading = state.heartModeLoading,
            )
            QueueModeButton(
                MeloXSymbol.AutoMix,
                state.autoMixEnabled,
                enabled = interactive,
                hint = if (state.autoMixEnabled) "自动过渡已开：歌曲间平滑衔接、智能重排队列" else "自动过渡已关：按队列顺序播放",
                onClick = state::toggleAutoMix,
                onInteract = onInteract,
                modifier = Modifier.weight(1f),
                compact = compact,
            )
        }
    }
}

@Composable
private fun QueueSectionTitle(title: String, subdued: Boolean = false) {
    Text(
        title,
        color = Color.White.copy(alpha = if (subdued) .62f else 1f),
        fontSize = if (subdued) 18.sp else 22.sp,
        fontWeight = FontWeight.Bold,
        // ⚠ top 8dp（2026-10-19 从 16dp 收窄）：标题上沿与模式区之间的空隙，加上顶渐隐带
        //   曾达 24dp+，用户报「四个按钮下面有很大空间」。分区标题本身有字号留白，8dp 够分。
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun QueueRow(
    entry: MeloXQueueEntry,
    state: MeloXPlaybackUiState,
    interactive: Boolean,
    onInteract: () -> Unit = {},
) {
    val dragOffset = remember(entry.entryId) { mutableFloatStateOf(0f) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(interactive, entry.entryId, state.currentIndex) {
                if (!interactive || entry.index <= state.currentIndex) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragEnd = { dragOffset.floatValue = 0f },
                    onDragCancel = { dragOffset.floatValue = 0f },
                ) { change, amount ->
                    change.consume()
                    dragOffset.floatValue += amount.y
                    val rowHeight = 52.dp.toPx()
                    when {
                        dragOffset.floatValue > rowHeight -> {
                            val current = state.queue.indexOfFirst { it.entryId == entry.entryId }
                            if (current > state.currentIndex && current < state.queue.lastIndex) {
                                state.moveQueueItem(current, current + 1)
                                dragOffset.floatValue -= rowHeight
                            }
                        }
                        dragOffset.floatValue < -rowHeight -> {
                            val current = state.queue.indexOfFirst { it.entryId == entry.entryId }
                            if (current > state.currentIndex + 1) {
                                state.moveQueueItem(current, current - 1)
                                dragOffset.floatValue += rowHeight
                            }
                        }
                    }
                }
            }
            .clickable(enabled = interactive) {
                // 点行即交互 ⇒ 唤出底部控件条（随后照旧放歌）。
                onInteract()
                state.queue.indexOfFirst { it.entryId == entry.entryId }
                    .takeIf { it >= 0 }
                    ?.let(state::playQueueIndex)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(entry.artworkUrl, Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.artist, color = Color.White.copy(alpha = .58f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        if (entry.index > state.currentIndex) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.List,
                modifier = Modifier.size(22.dp),
                color = Color.White.copy(alpha = .48f),
                iconSize = 20.sp,
                contentDescription = "调整播放顺序",
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueModeButton(
    symbol: MeloXSymbol,
    selected: Boolean,
    enabled: Boolean = true,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onInteract: () -> Unit = {},
    // ★ 横屏紧凑：按钮高度收窄（48 → 24dp），圆角法则仍成立（内 12 + 内边距 3 = 外 15）。
    // 图标同比例缩（24 → 14dp / iconSize 22 → 13sp）。占用高度从 64dp 降到 30dp。
    compact: Boolean = false,
    /**
     * 该档位的动作正在后台取数据（目前只有「进心动模式」：要先去网易云拉推荐队列）。
     *
     * 视觉是**图标呼吸**而不是置灰：置灰会被读成"按钮坏了"，而这里只是等一两秒。
     * 呼吸走 `graphicsLayer { alpha }`（绘制相位读值），不触发重组 —— 与播放页
     * 「组合期禁读动画值」同一口径。
     */
    loading: Boolean = false,
) {
    val buttonHeight = if (compact) QueueModeButtonHeightCompact else QueueModeButtonHeight
    val iconSizeDp = if (compact) 14.dp else 24.dp
    val iconFontSize = if (compact) 13.sp else 22.sp
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var showHint by remember { mutableStateOf(false) }
    val haptic = meloXHaptic()
    val density = LocalDensity.current
    val loadingAlpha = rememberInfiniteTransition(label = "queue-mode-loading").animateFloat(
        initialValue = 1f,
        targetValue = .28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "queue-mode-loading-alpha",
    )
    // 松手（或按钮被禁用/离开按住态）即收气泡；长按触发时才亮起。
    LaunchedEffect(pressed) {
        if (!pressed) showHint = false
    }
    Box(
        modifier = modifier
            .height(buttonHeight)
            // 内圆角 = 胶囊（48dp 高 ⇒ 24dp），与外壳同一个 `MeloXShapes.capsule`
            // token ⇒ 内 24 + 内边距 8 = 外 32，两层曲率同心（见外壳处的法则注释）。
            .clip(MeloXShapes.capsule)
            .background(Color.White.copy(alpha = if (selected) .70f else .12f))
            .combinedClickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = {
                    // 点按钮即交互 ⇒ 唤出底部控件条（此刻它多半是显出的）。
                    onInteract()
                    onClick()
                },
                // 长按只亮气泡，**不**触发切换（松手也不会补发 onClick）。
                onLongClick = {
                    onInteract()
                    showHint = true
                    haptic(HapticFeedbackType.LongPress)
                },
            )
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        MeloXSymbolIcon(
            symbol = symbol,
            modifier = Modifier
                .size(iconSizeDp)
                // 呼吸只在绘制相位读值：不重建、不触发本按钮重组。
                .graphicsLayer { alpha = if (loading) loadingAlpha.value else 1f },
            color = when {
                // loading 期间按钮是禁用的，但那只是防重入 —— 视觉上不该跟着置灰，
                // 否则"等一秒"会被读成"这个档位也不可用"。
                !enabled && !loading -> Color.White.copy(alpha = .25f)
                selected -> Color.Black.copy(alpha = .62f)
                else -> Color.White.copy(alpha = .86f)
            },
            iconSize = iconFontSize,
            contentDescription = when (symbol) {
                MeloXSymbol.Shuffle -> "随机播放"
                MeloXSymbol.Repeat -> "列表循环"
                MeloXSymbol.RepeatOne -> "单曲循环"
                MeloXSymbol.Infinity -> "无尽播放"
                MeloXSymbol.AutoMix -> "自动过渡"
                MeloXSymbol.HeartPulse -> "心动模式"
                else -> null
            },
        )
        if (showHint) {
            Popup(
                alignment = Alignment.TopStart,
                // 气泡悬在按钮上方，间距 8dp ⇒ offset = -(按钮高 + 8dp)。
                // 竖屏档按钮 48dp ⇒ 56dp；横屏紧凑档 32dp ⇒ 40dp。
                offset = IntOffset(0, with(density) { -(buttonHeight + 8.dp).roundToPx() }),
                onDismissRequest = { showHint = false },
                properties = PopupProperties(focusable = false),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C1C1E).copy(alpha = 0.88f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        hint,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 列表顶沿渐隐带高度（**线性**，见 [queueEdgeFade]）。
 *
 * ⚠⚠ **带宽就是被白白吃掉的显示面积**（2026-10-19 用户报障「按钮下面有很大空间 /
 *   进度条上面有很大空间 / 隐藏后底部也有很大空间，白白占位」——三处其实都是
 *   渐隐带本身：带内内容被淡到接近不可见 ⇒ 看起来就是一块空白）。
 *   所以带子只能窄到"软化边缘"的程度，**不许用加宽带去罩半行**。
 *
 * ★ 2026-10-19 四轮：改成歌词页同款**线性**曲线后，同样的带宽看起来"不那么空"——
 *   线性在前半带仍保持 50%~100% 不透明度（三次方只有 12.5%），所以可以给到 24dp
 *   而观感反而比先前 14dp 的三次方更实。顶沿下面是四个模式按钮（不是歌单），
 *   24dp 只负责把"列表从这里开始"这条边化开。
 */
private val QueueListTopFade = 24.dp

/**
 * 列表底沿渐隐带高度（**线性**，自**归零点**往上量，见 [queueEdgeFade]）。
 *
 * ⚠⚠ 同上：带宽 = 白吃的面积。曾放宽到 40dp / 72dp 想罩住半行残段，真机（23:25 截图）
 *   实测就是「进度条上面一大块空白」「隐藏后底部一大块空白」——带内几乎没有可见内容。
 *   根因是当时用的是**三次衰减**：前半带 alpha 就掉到 12.5%，视觉上等于没有内容。
 *
 * ★ 2026-10-19 四轮：换成歌词页的**线性** + **归零点**方案（与 `MeloXIOSLyricsPanel`
 *   的 `MeloXPortraitLyricsFadeBottom = 64.dp` 同一思路）。歌词页底带 64dp 也没被读成
 *   「空白」，因为线性让内容一路保持到末段才溶解。这里取 32dp ≈ 半行，效果 = "下一行
 *   探出来一半、末梢化开"（Apple Music 队列页的观感），而不是"切一刀"或"空一块"。
 */
private val QueueListBottomFade = 32.dp

/** 模式区模糊半径：与上方曲名栏（20dp）同档，顶部两块材质观感一致。 */
private val QueueControlsBlurRadius = 20.dp

/**
 * 模式按钮高度。内圆角 = 本值 ÷ 2（胶囊形），是「外圆角 = 内圆角 + 内边距」的**内**项。
 */
private val QueueModeButtonHeight = 48.dp

/**
 * ★ 横屏紧凑模式按钮高度：24dp ⇒ 内胶囊半径 12 + 内边距 3 = 外 15，圆角法则仍成立。
 *
 * 2026-10-20 用户两轮反馈「没感觉有任何变化」后定档：
 * 前两档（48→40→32）在 4x 密度（density=640）下只差 12dp=48px / 8dp=32px，
 * 横屏视口仅 360dp 高，肉眼看不出。这里一次压到 24dp（外壳总高 30dp），
 * 相对竖屏 64dp 省 34dp ≈ 136px ≈ 一整首歌（行步长 56dp）的六成，观感明确。
 * 图标同比例缩到 14dp，仍大于 comfortable touch target 的视觉下限。
 */
private val QueueModeButtonHeightCompact = 24.dp

/**
 * 模式区外壳的内边距。**必须四边等值**，否则「外圆角 = 内圆角 + 内边距」在横竖两个
 * 方向对不上（单个外圆角只有一个值）⇒ 四角视觉不齐。
 * 现值 8dp：外壳高 48 + 8×2 = 64 ⇒ 外胶囊半径 32 = 内胶囊 24 + 8 ✓
 */
private val QueueModeSurfacePadding = 8.dp

/** ★ 横屏紧凑模式外壳内边距：3dp ⇒ 外壳高 24 + 3×2 = 30 ⇒ 外胶囊半径 15 = 内胶囊 12 + 3 ✓ */
private val QueueModeSurfacePaddingCompact = 3.dp

/** 模式区暗色罩：只压一点，保证四键可读，同时保留 artwork 的层次。 */
private const val QueueControlsTintAlpha = 0.10f

/**
 * 唤出底部控件条的「向下滑」判定阈值：手指相对按下点**向下**位移超过这个值，
 * 才算一次下拉手势。取 12dp —— 大于系统手势 slop（约 8dp）以防把轻微抖动
 * 当成滑动，又小于正常下拉幅度，手感上就是"一拉就出来"。
 *
 * ⚠ 只认向下：上滑（继续往下看歌单）不唤出。点按同样不唤出 ——
 *   曾用「按下即唤出」，真机上翻歌单时控件条一直在跳出来。
 */
private val QueueRevealDragThreshold = 12.dp
