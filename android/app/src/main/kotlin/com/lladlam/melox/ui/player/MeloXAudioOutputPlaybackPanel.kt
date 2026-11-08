package com.lladlam.melox.ui.player

import android.os.SystemClock
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.lladlam.melox.R
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.meloXGlassSurface
import com.lladlam.melox.ui.glass.meloXLiquidButton

/** Compact artwork-led output panel; the glass renderer remains shared with the player. */
@Composable
internal fun MeloXAudioOutputPlaybackPanel(
    visible: Boolean,
    state: MeloXPlaybackUiState,
    outputName: String,
    localSelected: Boolean,
    onOutputClick: () -> Unit,
    onDismiss: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit = {},
    devices: @Composable ColumnScope.() -> Unit = {},
) {
    if (!visible) return
    // ★ 2026-11-07：输出面板的三颗音乐控制键与播放页 / 迷你栏**共用同一份动效状态机**
    //   （`MeloXTransportMotion.kt`）：prev/next 走「传送带」双三角推进、play/pause 走
    //   「收缩 → 谷底换字 → 弹簧回弹」形变。放在这里（面板可见期）而不是各按钮内部，
    //   是为了让三颗键的动画生命周期与面板一致；关闭时自然释放。
    //   ⚠ trigger() / startMorphToggle() 必须在 onClick()（= 换歌 / 切播放态）**之前**调用：
    //     状态一变上层就重组，协程最多"被打断"，不会"根本没开始"。
    val playPauseMorph = rememberMeloXPlayPauseMorph(state.isPlaying)
    val prevPulse = rememberMeloXConveyorPulse()
    val nextPulse = rememberMeloXConveyorPulse()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = .20f)).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.safeDrawingPadding().padding(horizontal = 28.dp)
                        .widthIn(max = 360.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(38.dp))
                        .meloXGlassSurface(
                            shape = RoundedCornerShape(38.dp),
                            tint = Color.Black.copy(alpha = .10f),
                            surfaceColor = Color.Black.copy(alpha = .28f),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = state.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = .12f)),
                        )
                        Spacer(Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                state.title.ifBlank { stringResource(R.string.player_now_playing) },
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOf(state.artist, state.album).filter(String::isNotBlank).distinct().joinToString(" – "),
                                color = Color.White.copy(alpha = .58f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutputTransportButton(
                                    label = stringResource(R.string.player_output_previous),
                                    enabled = state.hasPrevious,
                                    onClick = {
                                        prevPulse.trigger()
                                        state.previous()
                                    },
                                ) { color ->
                                    MeloXChevronConveyorGlyph(
                                        forward = false,
                                        color = color,
                                        // 与原静态 `MeloXSymbolIcon(Previous, iconSize = 26.sp)`
                                        // 逐字同字号 ⇒ 静止态墨迹逐像素对齐。
                                        textSizeSp = 26.sp,
                                        scrollProvider = { prevPulse.scroll.value },
                                        fallbackSymbol = MeloXSymbol.Previous,
                                        fallbackSize = 26.dp,
                                    )
                                }
                                OutputTransportButton(
                                    label = stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
                                    enabled = state.hasMedia,
                                    // 播放页 play/pause 的按压是「轻微内收 + 过阻尼」，
                                    // 与两侧 prev/next 的 0.84f/欠阻尼不同，照搬其参数。
                                    pressTarget = 0.93f,
                                    pressDampingRatio = 1f,
                                    pressStiffness = 1500f,
                                    onClick = {
                                        playPauseMorph.startMorphToggle()
                                        state.togglePlayPause()
                                    },
                                ) { color ->
                                    MeloXPlayPauseMorphGlyph(
                                        morph = playPauseMorph,
                                        color = color,
                                        iconSize = 32.dp,
                                    )
                                }
                                OutputTransportButton(
                                    label = stringResource(R.string.player_output_next),
                                    enabled = state.hasNext,
                                    onClick = {
                                        nextPulse.trigger()
                                        state.next()
                                    },
                                ) { color ->
                                    MeloXChevronConveyorGlyph(
                                        forward = true,
                                        color = color,
                                        textSizeSp = 26.sp,
                                        scrollProvider = { nextPulse.scroll.value },
                                        fallbackSymbol = MeloXSymbol.Next,
                                        fallbackSize = 26.dp,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SceneProgressControl(state = state, onShowQuality = {}, showQuality = false)
                    Spacer(Modifier.height(18.dp))
                    OutputPanelSlider(
                        value = state.volume,
                        label = stringResource(R.string.player_output_volume),
                        onChange = state::changeVolume,
                        onFinish = state::endVolumeDrag,
                        onStart = state::beginVolumeDrag,
                        volume = true,
                    )
                    Spacer(Modifier.height(16.dp))
                    actions()
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(22.dp))
                            .meloXLiquidButton(
                                shape = RoundedCornerShape(22.dp),
                                surfaceColor = if (localSelected) Color(0xFFFFFDEB) else Color.Black.copy(alpha = .40f),
                            )
                            .clickable(role = Role.Button, onClick = onOutputClick)
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MeloXSymbolIcon(MeloXSymbol.Devices, Modifier.size(24.dp), if (localSelected) Color(0xFF4786FF) else Color.White)
                        Text(outputName, color = if (localSelected) Color.Black else Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp))
                    }
                    devices()
                }
            }
        }
    }
}

/**
 * 输出面板的音乐控制键：外壳只负责「按压反馈 + 命中区 + 无障碍」，字形由调用方传入
 * （播放页 / 迷你栏同款的传送带 / 形变 glyph，见 `MeloXTransportMotion.kt`）。
 *
 * ⚠ 按压缩放走 `drawWithContent` 的**画布矩阵**，不用 `graphicsLayer`：
 *   play/pause 的形变 glyph 内部也是画布变换（`DrawScope.scale`），两层矢量光栅化
 *   叠加 ⇒ 缩到谷底时字形倒角仍按变换后的 device 尺寸重新光栅化，不会被位图降采样
 *   抹平成直角（播放页 `ScenePlayPauseButton` 同源）。
 * ⚠ `pressScale` 保留 `State<Float>` 不 `by` 解包：只在绘制期读 `.value`，
 *   按压那 0.2s 全程零重组。
 */
@Composable
private fun OutputTransportButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    // 播放页 prev/next = 0.84f / 欠阻尼(ζ=0.5) / k=620；play/pause = 0.93f / ζ=1 / k=1500。
    pressTarget: Float = 0.84f,
    pressDampingRatio: Float = Spring.DampingRatioMediumBouncy,
    pressStiffness: Float = 620f,
    glyph: @Composable (Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed) pressTarget else 1f,
        animationSpec = spring(
            dampingRatio = pressDampingRatio,
            stiffness = pressStiffness,
        ),
        label = "output-transport-press",
    )
    val foreground = Color.White.copy(alpha = if (enabled) 1f else .35f)
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .drawWithContent {
                val s = pressScale.value
                if (s == 1f) drawContent() else scale(s) { this@drawWithContent.drawContent() }
            },
        contentAlignment = Alignment.Center,
    ) {
        glyph(foreground)
    }
}

@Composable
private fun OutputPanelSlider(
    value: Float,
    label: String,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit,
    onStart: () -> Unit = {},
    enabled: Boolean = true,
    volume: Boolean = false,
) {
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinish)
    val start by rememberUpdatedState(onStart)
    // ⚠ 用 State 而不是 `by` 解包：pointerInput 协程会活过多次重组，
    //   直接捕获 value 会拿到按下那一刻的旧值（相对拖动的基准必须是最新原进度）。
    val valueState = rememberUpdatedState(value)
    // ★ 统一「按压放大 + 相对拖动」（与播放页进度条 / 音量条同一份交互状态）。
    val gesture = rememberMeloXRelativeSliderGesture()
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(32.dp)
            .meloXRelativeSliderPress(gesture, enabled)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
                if (enabled) setProgress { start(); change(it.coerceIn(0f, 1f)); finish(); true }
            }
            .pointerInput(enabled) {
                // ★ 2026-11-07：点按与「按住不跳 / 拖动相对」**共存**。
                //   · 快速点一下（未拖动 + 按压时长 ≤ 窗口）⇒ 松手时跳到点按位置；
                //   · 按住不放再松手 ⇒ 不跳（保持「首次按下不改值」的原始口径）；
                //   · 拖动 ⇒ 走下面的 detectDragGestures，按相对位移增减。
                //   与 Material3 那些滑条共用 [MeloXSliderTapMaxDurationMs] 同一套判据。
                if (!enabled) return@pointerInput
                var pressAtMs = 0L
                detectTapGestures(
                    onPress = { pressAtMs = SystemClock.uptimeMillis() },
                    onTap = { offset ->
                        val heldMs = SystemClock.uptimeMillis() - pressAtMs
                        if (pressAtMs != 0L && heldMs <= MeloXSliderTapMaxDurationMs) {
                            val frac = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            start(); change(frac); finish()
                        }
                        pressAtMs = 0L
                    },
                )
            }
            .pointerInput(enabled) {
                if (enabled) {
                    var started = false
                    detectDragGestures(
                        onDragStart = { gesture.reset(); started = false },
                        onDragEnd = { if (gesture.finish().dragged) finish() },
                        onDragCancel = { if (gesture.finish().dragged) finish() },
                    ) { event, dragAmount ->
                        event.consume()
                        // ★ 只吃每帧相对位移 dragAmount.x，基准是按下瞬间的原进度；
                        //   原实现用 event.position.x（绝对位置）⇒ 手指落哪就跳到哪。
                        gesture.onDragAmount(
                            dragAmount.x,
                            size.width,
                            valueState.value,
                        )?.let { next ->
                            if (!started) { started = true; start() }
                            change(next)
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.fillMaxWidth().height(if (volume) 30.dp else 6.dp)
                .clip(CircleShape)
                .meloXGlassSurface(CircleShape, surfaceColor = Color.White.copy(alpha = .20f)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f, 1f)).background(Color.White.copy(alpha = .95f), CircleShape))
        }
        if (volume) {
            MeloXSymbolIcon(
                MeloXSymbol.Volume,
                Modifier.padding(start = 12.dp).size(18.dp),
                if (value >= .13f) Color.Black.copy(alpha = .48f) else Color.White.copy(alpha = .78f),
                iconSize = 18.sp,
            )
        }
    }
}
