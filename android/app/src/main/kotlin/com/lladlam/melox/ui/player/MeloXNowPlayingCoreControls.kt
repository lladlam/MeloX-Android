package com.lladlam.melox.ui.player

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import com.lladlam.melox.ui.glass.meloXScenePageDisc
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.lladlam.melox.ui.glass.MeloSfTypefaceCache
import com.lladlam.melox.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import com.lladlam.melox.ui.theme.LocalMeloXFontFamily
import com.lladlam.melox.ui.theme.isMeloXDarkTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.audio.MusicQualityRuntime
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.playback.PlaybackTrackIdentity
import com.lladlam.melox.playback.CrossProviderPlaybackRuntime
import com.lladlam.melox.playback.ProviderPlaybackQualityRuntime
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.glass.MeloXPlaybackModeGlyph
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.meloXIconDrawingScale
import com.lladlam.melox.platform.MeloXVolumeKeyBridge
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXVolumeControlMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToLong

internal const val MeloXNowPlayingControlsHeight = 279

/**
 * 播放器**场景内控件是否可交互**（暂停 / 上一首 / 下一首 / 音量条…）。
 *
 * 由 `MeloXIOSNowPlayingSharedHost` 在**展开/收起过渡进行中**提供 `false`。
 * 背景（2026-10-03 用户报障「下拉返回的缩小播放器里的按钮还是能被点击（如暂停、音量）」）：
 * 过渡期场景半可见地铺在屏幕上，这些控件**看着还在、且能点到**。
 * 根节点的 `pointerInput` 拦截器虽已改用 `PointerEventPass.Initial` 先吞事件，但
 * `Modifier.clickable` / `Slider` 对「已被消费的 down」在部分版本下仍会响应
 * ⇒ 这里再做一层**显式 enabled 门控**兜底，双保险。
 *
 * 默认 `true`：未提供时行为与原来完全一致。
 */
internal val LocalMeloXPlayerControlsEnabled =
    androidx.compose.runtime.compositionLocalOf { true }

@Composable
internal fun MeloXNowPlayingCoreControls(
    state: MeloXPlaybackUiState,
    page: MeloXNowPlayingPage,
    onPageSelected: (MeloXNowPlayingPage) -> Unit,
    onShowQuality: () -> Unit,
    showPageSelector: Boolean = true,
    compact: Boolean = false,
    /**
     * 是否对「喇叭 / AirPlay / 列表」三类字形使用高精度矢量重绘（2026-09-30 用户要求）。
     *
     * 默认跟随 [compact]：横屏(compact=true)与竖屏 iOS 都走矢量；Classic 场景不传
     * （compact 默认 false）⇒ 保留原 SF 字形，逐像素不变。竖屏 iOS 在调用处显式传
     * `useVectorGlyphs = true`，只换字形、不动布局尺寸。
     */
    useVectorGlyphs: Boolean = compact,
    /**
     * 进度条 / 音量条的**左右对称内缩**（dp）。
     *
     * 横屏封面页用：右列其它部件左缘一律与底部三键里最左那颗按钮齐平（基准 =
     * [MeloXCompactPageSelectorHPadding]，见 `MeloXIOSNowPlayingScene` 顶部契约），
     * 但这两条 bar **不许因此偏心** —— 左右同缩 ⇒ 左缘落到基准、中心仍与暂停键同轴
     * （暂停键在满宽走带行里居中；走带行本身不缩，否则暂停键会跟着偏）。
     * 与竖屏结构同理：竖屏两条 bar 也整体在 `padding(horizontal = 32.dp)` 里，
     * 左缘与选择器按钮的 32dp 留白同基准、且中心在容器中心。
     * 竖屏传默认 0.dp ⇒ 逐像素不变。
     */
    barsInset: Dp = 0.dp,
    selectorModifier: Modifier = Modifier,
) {
    // 横屏布局把「歌词 / 播放设备 / 列表」三个按钮抽成常驻底栏，
    // 控制区本身只保留进度 + 走带 + 音量，高度相应收窄。
    val controlsHeight = if (showPageSelector) {
        MeloXNowPlayingControlsHeight
    } else {
        MeloXNowPlayingControlsHeight - 53
    }
    val transportGap = if (compact) 14.dp else 19.dp
    val volumeGap = if (compact) 22.dp else 31.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(controlsHeight.dp),
    ) {
        SceneProgressControl(state, onShowQuality, Modifier.padding(horizontal = barsInset))
        Spacer(Modifier.height(transportGap))
        SceneTransportControls(state, compact = compact)
        Spacer(Modifier.height(volumeGap))
        SceneVolumeControl(
            state,
            Modifier.padding(horizontal = barsInset),
            compact = compact,
            useVectorGlyphs = useVectorGlyphs,
        )
        if (showPageSelector) {
            Spacer(Modifier.height(3.dp))
            ScenePageSelector(
                state = state,
                page = page,
                onPageSelected = onPageSelected,
                compact = compact,
                useVectorGlyphs = useVectorGlyphs,
                modifier = selectorModifier,
            )
        }
    }
}

@Composable
internal fun SceneProgressControl(
    state: MeloXPlaybackUiState,
    onShowQuality: () -> Unit,
    modifier: Modifier = Modifier,
    showQuality: Boolean = true,
) {
    val displayedDurationMs = if (state.isInTransition && state.incomingDurationMs > 0L) {
        state.incomingDurationMs
    } else state.durationMs
    val displayedPositionMs = if (state.isInTransition) state.incomingPositionMs else state.positionMs
    val sourceProgress = if (displayedDurationMs > 0L) {
        (displayedPositionMs.toFloat() / displayedDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    var scrubbing by remember(state.mediaId) { mutableStateOf(false) }
    var localProgress by remember(state.mediaId) { mutableFloatStateOf(sourceProgress) }
    var scrubMediaId by remember(state.mediaId) { mutableStateOf<String?>(null) }
    var scrubDurationMs by remember(state.mediaId) { mutableLongStateOf(0L) }
    val trackHeight by animateDpAsState(
        targetValue = if (scrubbing) 6.dp else 4.dp,
        animationSpec = tween(120),
        label = "scene-progress-track-height",
    )

    LaunchedEffect(sourceProgress, scrubbing) {
        if (!scrubbing) localProgress = sourceProgress
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Slider(
            enabled = !state.isInTransition,
            value = localProgress,
            onValueChange = {
                if (!scrubbing) {
                    scrubMediaId = state.mediaId
                    scrubDurationMs = state.durationMs
                }
                scrubbing = true
                localProgress = it.coerceIn(0f, 1f)
            },
            onValueChangeFinished = {
                if (scrubMediaId == state.mediaId && scrubDurationMs > 0L) {
                    state.seekTo((scrubDurationMs * localProgress).roundToLong())
                }
                scrubbing = false
                scrubMediaId = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            thumb = { Spacer(Modifier.size(0.dp)) },
            track = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(localProgress)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.96f)),
                    )
                }
            },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
        ) {
            val shownPosition = if (scrubbing) {
                (state.durationMs * localProgress).roundToLong()
            } else {
                displayedPositionMs
            }
            Text(
                text = sceneFormatDuration(shownPosition),
                modifier = Modifier.align(Alignment.CenterStart),
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 11.sp,
                fontFamily = LocalMeloXFontFamily.current,
                fontWeight = FontWeight.Medium,
            )

            if (showQuality && MeloXSettingsRuntime.showPlayerQualityTip) {
                SceneQualityChip(
                    state = state,
                    onShowQuality = onShowQuality,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Text(
                text = "−${sceneFormatDuration((displayedDurationMs - shownPosition).coerceAtLeast(0L))}",
                modifier = Modifier.align(Alignment.CenterEnd),
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 11.sp,
                fontFamily = LocalMeloXFontFamily.current,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SceneQualityChip(
    state: MeloXPlaybackUiState,
    onShowQuality: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val downloads = remember(context) { MeloXDownloadStore.get(context) }
    val identity = remember(state.mediaId) { state.mediaId?.let(PlaybackTrackIdentity::decode) }
    val neteaseSongId = identity
        ?.takeIf { it.sourceId.builtIn == MusicSource.Netease }
        ?.value
        ?.toLongOrNull()
    val downloadedQuality = neteaseSongId?.let(downloads::downloadedQuality)
    var selected by remember(context, state.mediaId) {
        mutableStateOf(
            MusicQualityPreferences.read(context).also { MusicQualityRuntime.selected = it },
        )
    }
    var neteaseActual by remember(state.mediaId) {
        mutableStateOf(MusicQualityRuntime.actualFor(neteaseSongId))
    }
    var providerActual by remember(state.mediaId) {
        mutableStateOf(ProviderPlaybackQualityRuntime.actualFor(identity))
    }
    var fallbackSource by remember(state.mediaId) {
        mutableStateOf(CrossProviderPlaybackRuntime.sourceFor(neteaseSongId))
    }

    LaunchedEffect(state.mediaId) {
        while (true) {
            selected = MusicQualityRuntime.selected
            if (identity?.sourceId?.builtIn == MusicSource.Netease) {
                neteaseActual = MusicQualityRuntime.actualFor(neteaseSongId)
                fallbackSource = CrossProviderPlaybackRuntime.sourceFor(neteaseSongId)
            } else {
                providerActual = ProviderPlaybackQualityRuntime.actualFor(identity)
                fallbackSource = null
            }
            // Quality changes are user-driven and infrequent; polling at frame-
            // like cadence needlessly recomposed the entire controls column.
            delay(750L)
        }
    }

    val qualityTitle = when {
        downloadedQuality != null -> downloadedQuality.title
        identity?.sourceId?.builtIn == MusicSource.Netease -> (neteaseActual ?: selected).title
        providerActual != null -> providerActual!!.sceneTitle()
        else -> selected.title
    }
    val displayTitle = fallbackSource?.let { "${it.displayName} · $qualityTitle" } ?: qualityTitle
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "scene-quality-chip-press",
    )

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .height(48.dp)
                .meloXLiquidButton(
                    shape = RoundedCornerShape(7.dp),
                    surfaceColor = Color.White.copy(alpha = 0.10f),
                    lensRadius = 6.dp,
                    refractionHeight = 9.dp,
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                ) { onShowQuality() }
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier.size(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.AutoMix,
                    modifier = Modifier.size(12.dp),
                    color = Color.White.copy(alpha = 0.86f),
                    iconSize = 14.sp,
                )
            }
            Text(
                text = displayTitle,
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SceneTransportControls(
    state: MeloXPlaybackUiState,
    compact: Boolean = false,
) {
    val rowHeight = if (compact) 70.dp else 82.dp
    val buttonSize = if (compact) 52.dp else 64.dp
    val sideGlyph = if (compact) 28.dp else 34.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        SceneTransportButton(
            kind = SceneGlyphKind.Backward,
            visualSize = sideGlyph,
            size = buttonSize,
            onClick = {
                if (state.hasPrevious) state.previous() else state.seekTo(0L)
            },
        )
        Spacer(Modifier.weight(1f))
        ScenePlayPauseButton(state, compact = compact)
        Spacer(Modifier.weight(1f))
        SceneTransportButton(
            kind = SceneGlyphKind.Forward,
            visualSize = sideGlyph,
            size = buttonSize,
            onClick = state::next,
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ScenePlayPauseButton(
    state: MeloXPlaybackUiState,
    compact: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 620f,
        ),
        label = "scene-play-pause-press",
    )
    val buttonSize = if (compact) 52.dp else 64.dp
    val glyphSize = if (compact) 40.dp else 48.dp

    Box(
        modifier = Modifier
            .size(buttonSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                // ⚠ 过渡期禁用（用户报障「缩小播放器里的暂停还能点」）。
                enabled = LocalMeloXPlayerControlsEnabled.current,
                onClick = state::togglePlayPause,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = state.isPlaying,
            transitionSpec = {
                (
                    fadeIn(tween(180)) +
                        scaleIn(initialScale = 0.78f, animationSpec = tween(200)) +
                        slideInVertically(tween(200)) { (it * 0.24f).toInt() }
                    ) togetherWith (
                    fadeOut(tween(150)) +
                        scaleOut(targetScale = 0.78f, animationSpec = tween(180)) +
                        slideOutVertically(tween(180)) { -(it * 0.24f).toInt() }
                    )
            },
            label = "scene-play-pause-symbol-replace",
        ) { playing ->
            SceneCupertinoGlyph(
                kind = if (playing) SceneGlyphKind.Pause else SceneGlyphKind.Play,
                modifier = Modifier.size(glyphSize),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun SceneTransportButton(
    kind: SceneGlyphKind,
    visualSize: Dp,
    size: Dp = 64.dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.84f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 620f,
        ),
        label = "scene-transport-press-${kind.name}",
    )

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                // ⚠ 过渡期禁用（上一首 / 下一首）。
                enabled = LocalMeloXPlayerControlsEnabled.current,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        SceneCupertinoGlyph(
            kind = kind,
            modifier = Modifier.size(visualSize),
            color = Color.White,
        )
    }
}

/**
 * [SceneVolumeControl] 里那条"音量跟随弹簧"的物理状态：位置 / 速度。
 *
 * ⚠ 存在的唯一理由：这两个量**必须跨 `LaunchedEffect` 重启存活**。
 *   音量跟随动画的 effect key 里带了 `state.volume`，而实体音量键每来一格都会改它 ⇒
 *   effect 每次换档都重启一次。若把 pos/vel 写成 effect 的局部变量，重启即被重置
 *   （速度回到 0）⇒ 每换一档都要"先刹停、再起步" ⇒ 条就"一格一格一顿一顿"。
 *   放在这个对象里，前后相继的多个 effect 实例是在**同一个累加器**上继续推进，只前进不倒退。
 *
 * ⚠ 故意**不是** Compose state：pos/vel 是纯物理量，只在逐帧循环里被读写，
 *   不需要参与快照观察（否则每帧写一次速度就要多触发一次重组）。
 */
private class MeloXVolumeFollowState(start: Float) {
    /** 当前显示位置（0f..1f）。 */
    var position: Float = start

    /** 当前速度（单位/秒）。临界阻尼弹簧下这是**连续性**的载体，绝不能在换档时清零。 */
    var velocity: Float = 0f
}

/**
 * iOS 橡皮筋公式 `b = (1 − 1/(x·c/d + 1))·d` 里的 `c`（Apple / UIScrollView 取 **0.55**）。
 *
 * ⚠ 这里取 **1.1** 而不是 Apple 的 0.55，是为"4dp 细条 + 单按就要看得见"标定的（2026-10-07）。
 *   数值模拟（ω=16 / ζ=0.5 / release=200ms）下的单按峰值 `|position|` 与拉长量：
 *     c=0.7（Apple 量级）→ 0.48 ⇒ 6.2dp，单按几乎看不出"顶到墙"
 *     c=0.9 → 0.55 ⇒ 7.2dp｜**c=1.1 → 0.61 ⇒ 7.9dp（采用）**
 *     c=1.6 → 0.72 ⇒ 9.3dp（偏夸张，且长按时很快就顶满、丢掉"越推越硬"的渐进感）
 *   位移曲线（pull = 越界按次数，上限归一 1.0，c=1.1）：
 *     1 次 → 0.52 ｜ 2 → 0.69 ｜ 3 → 0.76 ｜ 5 → 0.85 ｜ 10 → 0.92 ｜ ∞ → 1.00（**永不到达**）
 *   ⇒ 仍是 iOS 那条"越推越硬但撞不到墙"的凹曲线，只是整体更陡、**第一下就有明确回应**。
 */
private const val IOS_RUBBER_BAND_C = 1.1f

/**
 * 抬手后延迟这么久才真正释放蓄力。
 *
 * ⚠⚠ **不能小于峰值到达时间**，否则"单按一格"根本看不见拉伸（2026-10-07 用户报障）。
 *   解析解实测：ω=16/ζ=0.5 下，从 0 冲到 target≈0.5 的**峰值出现在 ~217ms**；
 *   旧值 90ms 会在动作**爬升途中**就把 target 归零 ⇒ 观感是"顶了一下又缩回"，
 *   人眼判为"没动" ⇒ 表现为**只有一直按着（事件连发累积 pull）才看得出拉伸**。
 *   取 200ms 让峰值刚好落在释放前，单按可见时长 ~317ms（拉长 7.9dp / 变粗 3.1dp）。
 *   仍需**大于**连续快按时"抬起 → 再按下"的几十毫秒间隙，否则蓄力接不上、一抽一抽。
 */
private const val RELEASE_DELAY_MS = 200L

/**
 * 兜底释放：距上次音量键事件超过这么久还没等来 `onKeyUp`，就强制收回。
 *
 * ⚠ 必须大于**长按音量键的初始重复延迟**（系统约为 500ms，之后才每 50ms 连发），
 *   否则长按刚按下的头 0.5s 会被误判成"已抬手" ⇒ 条提前缩回再被连发推开，出现一抽。
 * ⚠ 存在的意义是防呆：万一某个 ROM 压根不投递 `onKeyUp`，条也绝不会永远卡在拉长态。
 */
private const val HOLD_FALLBACK_MS = 700L

/**
 * [SceneVolumeControl] 里"边界过冲拉长"（rubber band）的物理状态。
 *
 * `position`：0 = 常态；+1 = 向**上限侧**拉满（条向右长出去）；-1 = 向**下限侧**拉满（向左长出去）。
 *
 * ⚠ `position` 是 Compose state，这不是笔误 —— 它只在 [drawVolumeTrack] 的 `Canvas` lambda
 *   （**绘制阶段**）里被读，于是每帧只失效绘制层、**不触发重组**。
 *   ⛔⛔ 它**绝不能**在 composable 主体或 `Slider` 的 `track` **组合期插槽**里被读：
 *     那种 lambda 会被 Material3 `Slider` 缓存，绘制时执行的是上一次组合算好的常量
 *     ⇒ 拉伸量恒为首帧的 0（2026-10-07 v74 实测：条高全程 28px、max 处按 11 次宽度 Δ0）。
 *   `velocity` / `direction` 故意**不是** state：前者是纯物理量、只在帧循环里读写；
 *   后者虽是瞬时量，但**绘制期每帧都会重新读取**，不需要靠 state 通知。
 */
private class MeloXVolumeStretchState {
    var position by mutableFloatStateOf(0f)
    var velocity: Float = 0f
    var direction: Int = 1

    /**
     * **原始推力**（未做阻尼），每越界按一次 +1，抬手/超时归零。
     *
     * ⚠ 它和 [position] 的关系就是 iOS 橡皮筋公式（见调用点注释）：
     *   `位移 = pull·c / (1 + pull·c)` —— 凹函数 ⇒ **越推越硬、渐近逼近上限但永远撞不到墙**。
     *   旧实现是"固定冲量 + 硬夹到 1.0"，推两下就死死顶在满量程、速度还被清零，
     *   Apple 在 WWDC18 里把这种手感直接叫做 "hit a wall / feel broken"。
     */
    var pull: Float = 0f

    /** 抬手后的释放时刻（0 = 未排定）。用 `System.nanoTime()` 计，与 `withFrameNanos` 同源。 */
    var releaseAtNanos: Long = 0L

    /** 最近一次音量键事件的时刻：`onKeyUp` 万一不被某些 ROM 投递时，靠它超时兜底释放。 */
    var lastEventNanos: Long = 0L
}

/**
 * 边界"橡皮筋拉长"的**变形量**（单位：dp），在**绘制阶段**由 [drawVolumeTrack] 按 `s` 现算。
 *
 * ⚠⚠ 2026-10-07 用户报障「极限音量下再单点音量键也没有拉伸」——**根因是变形量算错了阶段**
 *   （v74 实测：条高全程恒定 28px、max 处按 11 次宽度 Δ0，而同期音量跟随动画完全正常）：
 *   旧实现把 `val s = abs(volumeStretch.position)` 和 `h`/`extra` 一起算在 Material3 `Slider`
 *   的 `track` **组合期**插槽里。但那个插槽产出的 lambda 会被 Slider **缓存**，绘制时执行的是
 *   「上一次组合算好的常量」⇒ `position` 每帧变化只失效绘制层，而绘制层读到的是**首帧的 0**
 *   ⇒ 拉伸量恒为 0，条永远不动。
 *   （诱因是 [MeloXVolumeStretchState.position] 的注释谎称"只在 graphicsLayer 里被读"——
 *     那是 v73 之前的实现；v74 换成 `Modifier.layout` 后读取点已变成组合期，注释没跟着改。）
 *
 *   ⇒ 正解：**绘制期现算**。`Canvas`/`drawBehind` 的 lambda 每帧重跑、在其中读 `position`
 *     只失效绘制层、不触发重组，同时每帧都能拿到最新位移。
 *
 * ⛔ 圆角必须**在绘制里自己算**（`drawRoundRect` + 半径 = 高/2），不要交给 `clip(CircleShape)`：
 *   任何非均匀缩放（`scaleX`）都会把 `CircleShape` 拉成椭圆（用户实测"圆角不对劲"），
 *   而 `CornerSize.toPx` 只返回**单一半径**、无法表达 X≠Y 的圆角 ⇒ 没有补偿 API 可走。
 *   直接按当前高度算半径 = 真正的胶囊端头，**与缩放完全无关**。
 */
private const val STRETCH_EXTRA_H = 5f
/** 边界拉长时单侧的外鼓量（dp）：`extra = s · STRETCH_EXTRA_W`。 */
private const val STRETCH_EXTRA_W = 13f

@Composable
private fun SceneVolumeControl(
    state: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
    // compact 仅决定左右喇叭图标盒尺寸 / 间距（横屏 18×24/36×24 + 12dp，竖屏 12/15dp + 10dp）；
    // useVectorGlyphs 决定喇叭是否走高精度矢量（左=无弧、右=三弧）。条体始终用**原材质**
    // （轨道 0.20 白 + 当前音量 0.82 白填充、无白边），按用户要求只加粗到与进度条相当(4dp)
    // 并去掉圆球 thumb，这一条对所有形态（竖屏 / 横屏 / Classic）全局一致。
    compact: Boolean = false,
    useVectorGlyphs: Boolean = false,
) {
    var dragging by remember { mutableStateOf(false) }
    // ★ 用于把松手时的系统音量回读丢出主线程（binder 不能挡 UI 帧）。
    val scope = rememberCoroutineScope()
    // ★ 拖动跟手值（连续量，直接来自手指位置，**不加任何动画**）。
    var dragVolume by remember { mutableFloatStateOf(state.volume) }
    // ★ 显示值 = **单一通道**（`dragVolume` 一个变量喂给 Slider）。
    //
    // ⚠⚠ 2026-10-07 用户报障「滑动音量条**偶尔**还会跳变」——根因在旧实现的这一行：
    //     `val shownVolume = if (dragging) dragVolume else easedVolume`
    //   它在**松手那一帧做了一次硬切换**：显示值从「手指的**连续**值」瞬间换成
    //   「`easedVolume` 的**量化**值」。
    //     · System 模式下系统音量是**离散档位**（maxVolume 通常 15 档 ⇒ 每格 ≈ 0.067），
    //       `changeVolume` 会把手指的连续值量化后再回写 `state.volume`；
    //     · 手指停在两档之间时，这一步瞬移最大 ≈ **6.7%** ⇒ 就是那次"跳变"；
    //     · Player 模式是连续量、无量化，永远不出现 ⇒ 也解释了为什么是"**偶尔**"。
    //   （旧注释里「松手时 easedVolume 早已收敛到 state.volume，故切回无跳变」这个推断
    //     是错的：收敛的目标本身就是量化值，跟手指位置差着最多半档。）
    //
    //   修法（已二次迭代，见下）：**不再分出第二条显示通道**，非拖拽期让 `dragVolume` 自己
    //   平滑追到 `state.volume`：
    //     · 拖拽期：`onValueChange` 直接写 `dragVolume`（1:1、零帧滞后，与原来完全一致）；
    //     · 松手：`dragging=false` 只是让下面这个 effect 重启，起点取的就是**手指松开时的
    //       当前值** ⇒ 从那里平滑滑到真实档位，**不存在切换、不可能跳**。
    //
    // ⚠⚠⚠ 2026-10-07 用户报障「**按系统音量键时音量条一格一格一顿一顿**」——
    //   根因就是上一版这里用的 `animate(..., tween(220, FastOutSlowInEasing))`：
    //     · `FastOutSlowInEasing` = cubic-bezier(0.4, 0, 0.2, 1)，**起始斜率为 0**（从静止加速）；
    //     · 音量键每来一格都会改 `state.volume` ⇒ 这个 LaunchedEffect 每格重启一次 ⇒ `animate`
    //       被取消、下一格又从**零速度**起手 ⇒ 每换一档都要「先刹停、再起步」；
    //     · 长按/连按音量键（约每 100ms 一格）时，条永远走不完一程就被打断 ⇒ 停-走-停-走，
    //       观感正是用户说的「一顿一顿」。单按一格时看着反而还行 ⇒ 也解释了这个现象只在
    //       **连续调节**时才明显。
    //   修法：把**速度本身也当作状态**（临界阻尼弹簧 ζ=1，逐帧积分）：
    //     · 换 target 时 `pos` 与 `vel` 都还在 ⇒ **速度连续**，不存在刹停 / 重新起步；
    //     · ζ=1 ⇒ 单调趋近、**无过冲**（不会越过档位再弹回来，也就不存在"弹一下再停"）；
    //     · 弹簧到位时长与距离无关 ⇒ 单按一格与长按连跳的手感一致；
    //     · 仍然只写 `dragVolume` 这一个通道，拖拽分支**一行没动**。
    //
    //   ⚠⚠ 关键细节：弹簧的 `position`/`velocity` 必须**跨 effect 重启存活**。
    //     `state.volume` 是下面 LaunchedEffect 的 key，音量键每来一格都会让 effect 重启一次；
    //     若把 pos/vel 放在 effect 的**局部变量**里，重启就得重新初始化 ⇒ 速度又被清零 ⇒
    //     等于原病没治。所以放到 [MeloXVolumeFollowState]（普通 class，**不是 Compose state**：
    //     这两个量是纯物理量、只被逐帧循环读写，不该参与快照观察，否则每帧多触发一次重组）。
    //     这样多个前后相继的 effect 实例是在**同一个累加器**上继续推进，只会前进、不会倒退。
    val volumeFollow = remember { MeloXVolumeFollowState(state.volume) }
    LaunchedEffect(state.volume, dragging) {
        // 拖拽期绝不介入：任何补间都会让条与手指脱节。
        if (dragging) {
            // 手指说了算：位置贴住手指、速度归零（松手时不该继承拖动前的残余速度）。
            volumeFollow.position = dragVolume
            volumeFollow.velocity = 0f
            return@LaunchedEffect
        }
        // ⚠ 非拖拽期 `dragVolume` 恒等于 `volumeFollow.position` ⇒ 这行是**幂等**的；
        //   真正起作用的只有「拖动 → 松手」那一帧：把起点对齐到**手指最后的位置**
        //   （拖拽期间 `state.volume` 只在跨格时变，手指停在格内时 position 会落后于手指）。
        //   速度已在拖拽分支清零，这里不动它。
        volumeFollow.position = dragVolume
        // ω = 30 rad/s ≈ Compose StiffnessHigh 一档。逐帧数值模拟（60Hz 半隐式欧拉、系统音量
        //   15 档 ⇒ 一格 6.7%）实测的取舍：
        //     ω=20：单格到位 383ms（到亚像素），长按 100ms/格时跟随滞后 0.48 格（约 9px/300px 条宽）；
        //     ω=30：单格到位 283ms，             长按跟随滞后 0.22 格（约 4px）；
        //     ω=40：单格到位 217ms，             跟随滞后 0.12 格 —— 已接近"跳"而不是"滑"。
        //   取 30：单格 ≈ 0.28s 的匀滑行程（贴近 iOS 音量 HUD 的约 0.25s），且长按连跳时
        //   只落后 1/5 格 ⇒ 既"丝滑"又不显拖沓。
        val omega = 30f
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            // 每次进入循环的第一帧按 60Hz **名义步长**积分，两个坑一次避开：
            //   · 若改成"首帧 dt=0 直接跳过"⇒ 连按音量键时每格都白丢一帧，又会残留顿挫；
            //   · 若沿用上一轮迭代的陈旧时间戳 ⇒ 从静置被唤醒时 dt 会顶到 50ms 上限，
            //     一步就跨完一整格 = 肉眼可见的"弹一下"。（参数见下一条注释）
            val dt = if (last == 0L) 1f / 60f
            else ((now - last) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
            last = now
            val target = state.volume
            // 临界阻尼弹簧（半隐式欧拉）：a = ω²(target − pos) − 2ω·vel
            //   dt 上限 50ms ⇒ ω·dt ≤ 1 < 2，是半隐式欧拉的稳定区，掉帧时不会发散。
            volumeFollow.velocity +=
                (omega * omega * (target - volumeFollow.position) - 2f * omega * volumeFollow.velocity) * dt
            volumeFollow.position += volumeFollow.velocity * dt
            // ⚠ 到位阈值按**亚像素**取：0.001 是全量程的 0.1%，条宽 300px 时只有 0.3px ⇒
            //   最后一帧"补齐吸附"是不可见的。**不要**再收紧到 1e-4 / 1e-3（旧值）：
            //   临界阻尼的尾段是 (1+ωt)e^{-ωt} 的双指数衰减，从 0.1% 收到 0.01% 要多跑
            //   约 180ms 的空转帧 —— 而 `withFrameNanos` 会持续申请新帧，白烧的帧循环会
            //   拖住刷新率（本项目 VRR 静置回落对这点很敏感）。
            if (abs(target - volumeFollow.position) < 1e-3f && abs(volumeFollow.velocity) < 1e-2f) {
                // 到位：吸附到精确档位并**退出循环**。
                // ⚠ 必须退出 —— `withFrameNanos` 会持续申请新帧，常驻循环会钉住刷新率
                //   （本项目 VRR 控制器对"静置回落"极敏感，绝不能让空转的帧循环续期）。
                volumeFollow.position = target
                volumeFollow.velocity = 0f
                dragVolume = target
                break
            }
            // 只写这一个显示通道；coerce 只作用于显示，物理量 pos 不夹（免得撞墙粘住）。
            dragVolume = volumeFollow.position.coerceIn(0f, 1f)
        }
    }
    val shownVolume = dragVolume

    // ══ 实体音量键通道（2026-10-07 用户：「物理音量键调节时播放页音量移动**还是**不平滑」）══
    //
    // 为什么弹簧修完还是不平滑：弹簧只解决"动画本身的顿挫"，**输入侧**还卡着一条 150ms 的延迟。
    //   · vivo 上硬件音量键不发 VOLUME_CHANGED_ACTION（本仓库 2026-10-06 实测）⇒ 播放页唯一
    //     的信息源是 `MeloXPlayerUi` 里那条 150ms 轮询 ⇒ 按键后条最多等 150ms 才起步
    //     ⇒ 观感就是"先顿一下、再滑一下"。
    //   · 实体按键事件是**同步**送到 Activity 的（`MainActivity.onKeyDown` → `MeloXVolumeKeyBridge`），
    //     用它把"该刷新了"推过来，这条延迟就从「≤150ms」压到「≈1 帧」。
    //   · 顺带拿到"越界按"（顶到最大/最小后继续按）：越界按在系统侧**不留痕迹**（音量值不变），
    //     轮询永远看不见它，只有实体按键事件能识别 ⇒ 这也是"过冲拉长"能做到的前提。
    val volumeStretch = remember { MeloXVolumeStretchState() }
    // 手指一碰本条，蓄力立刻作废：否则"顶到头拉长着"的时候去拖滑块，条会一直挂在拉长态
    //   （主 effect 的 key 只有 eventTick，拖拽开始并不会让它重启）。
    //   只清 pull 就够 —— 回弹循环下一帧看到 pull=0 就会把条送回常态并自行退出。
    LaunchedEffect(dragging) {
        if (dragging) {
            volumeStretch.pull = 0f
            volumeStretch.releaseAtNanos = 0L
            volumeStretch.lastEventNanos = 0L
            // ⚠ 必须把拉长位移也清掉：主 effect 在 `dragging` 时直接 return，不会跑回弹循环，
            //   若只清 pull 不复位 position，拖动时条会一直挂在拉长态（用户之前踩过）。
            volumeStretch.position = 0f
            volumeStretch.velocity = 0f
        }
    }
    // ⚠ key **只放 eventTick**，绝不能把 `dragging` 也放进来：
    //   松手那一帧 `dragging` 会 true→false，effect 跟着重启一次；此时若音量恰好停在端点，
    //   那次"并非按键引起"的重启就会被判成越界按 ⇒ 松手瞬间凭空拉长一下。
    //   `dragging` 在块内读即可 —— effect 重启时用的就是当次组合的最新值。
    LaunchedEffect(MeloXVolumeKeyBridge.eventTick) {
        if (MeloXVolumeKeyBridge.eventTick == 0) return@LaunchedEffect
        // ⚠⚠ `dragging` 自愈（2026-10-07 用户报障「极限过冲永无拉伸」的最终根因）：
        //   本地 `dragging` 只由 `onValueChangeFinished` 复位，而那只在**用户手指**抬起时触发。
        //   转场期 `LocalMeloXPlayerControlsEnabled=false` 会让 Slider 忽略手势、手势被父层
        //   （下拉收起）截断等情形下 `onValueChangeFinished` **根本不会来** ⇒ `dragging` 永久卡 true
        //   ⇒ ① 本effect 每拍都进不了越界分支；② `LaunchedEffect(state.volume, dragging)`
        //      也永远走"拖拽期"分支、停止跟随音量 ⇒ 表现为"整体都没有 AM 动效和交互"。
        //   既然收到实体音量键就说明用户没在拖这条 ⇒ 直接自愈复位。
        if (dragging) dragging = false
        // 手指正在拖本条 ⇒ 按键交给系统，条不参与（避免与手指位置打架）。
        // ⚠⚠ 用「本次按键是否真的改变了音量」当唯一互斥判据，**不再**用本地 `dragging`：
        //   本地 `dragging` 只由 `onValueChangeFinished` 复位，而那只在**用户手指**抬起时触发；
        //   一旦因任何原因没复位（转场期禁用、手势被父层截断、页面重建…）它会**永久卡 true**，
        //   之后每次按键都在这里 return ⇒ "过冲拉伸永远不发生"（2026-10-07 实测定位）。
        //   而"音量值真的没动"本身就是越界的定义，天然覆盖了拖拽中（拖拽时音量由手指控制，
        //   按键的增删会被下一行的 `changed` 反映出来）。
        // ⚠ 也不因 `volumeControlMode` 提前 return：越界拉长是**纯视觉反馈**，
        //   与"音量写到系统还是播放器内部"无关（两种模式下音量键都会顶住）。
        //   真正需要 System 模式的地方是 `syncSystemVolumeNow()` 内部，它自己会判。

        val bridge = MeloXVolumeKeyBridge
        val dir = bridge.lastDirection
        if (bridge.lastWasRelease) {
            // ── 抬手：排定释放 ──────────────────────────────────────────────────
            //   不立即清零，而是延后 [RELEASE_DELAY_MS] 再释放：连续快按时
            //   "抬起 → 再按下"的间隙远小于这个值 ⇒ 蓄力能接上，不会一次一抽。
            volumeStretch.releaseAtNanos = System.nanoTime() + RELEASE_DELAY_MS * 1_000_000L
        } else {
            // ── 按下 ────────────────────────────────────────────────────────────
            // ① 即时读一次系统档位，把 150ms 轮询的延迟压到 ≈1 帧。
            //    音量键的真正处理在 system_server 的 `interceptKeyBeforeQueueing`，早于事件投递给 App
            //    ⇒ 这里读到的通常就是**调完之后**的值，无需任何乐观预测。
            //    ⚠ 这是一次主线程 binder，但**每次按键仅一次**（≈0.1ms）；被禁掉的是
            //      "拖动期每像素一次 binder"，两者量级完全不同，别混为一谈。
            //    ⚠ 再补读一次兜竞态：`AudioService.adjustStreamVolume` 是 **oneway** 调用
            //      （system_server 不等待落盘就返回），理论上存在"按键事件先到 App、音量还没生效"
            //      的窗口。少了这一步，普通按键会被误判成"越界按"而凭空拉长一下。
            var changed = state.syncSystemVolumeNow()
            if (!changed) {
                withFrameNanos { }
                withFrameNanos { }
                changed = state.syncSystemVolumeNow()
            }
            if (dir != 0) volumeStretch.direction = dir
            // ② 越界按 = 音量值**真的没动**（`!changed`）+ 而且**确实停在那一端**。
            //    越界按在系统侧不留痕迹（音量值不变），只能由实体按键事件识别 ——
            //    这正是必须走 `Activity.onKeyDown` 而不能靠轮询的原因。
            //    ⚠ 端点判据用**归一化后**的 `state.volume`（两种模式都是 0..1），
            //      不要写死 1e-3f 之外的余量：vivo 的 `STREAM_MUSIC` Max 是 150（不是 15），
            //      归一化后正好 1.0，端点命中。
            val atEnd = dir != 0 &&
                (if (dir > 0) state.volume >= 1f - 1e-3f else state.volume <= 1e-3f)
            // ⚠⚠ 长按自动重复**不再累加**蓄力（2026-10-07 用户报障「长按到极限会二次拉伸」）。
            //   `repeatCount > 0` = 同一根手指还没抬，系统在自动重复发 onKeyDown。
            //   iOS 语义是**一次手势 = 一次蓄力脉冲**，与系统重复几次无关；
            //   新的蓄力必须等手指真正抬起后再按下（`repeatCount == 0`）。
            //   ⇒ 这里既不累加、也不清零：保持当前蓄力，让条维持在同一拉伸量。
            val freshPress = bridge.isFreshPress
            if (!changed && atEnd) {
                if (freshPress) {
                    // 新的按压：蓄力**吃满**一整份（不是 +1），保证「单按一格就看得见拉伸」。
                    // 旧实现 `pull += 1f` 依赖"多次累加才够大"，而单按时 iOS 橡皮筋
                    // c=1.1 只给出 0.52target ⇒ 加上释放延迟，视觉上几乎等于没动。
                    volumeStretch.pull = 1f
                }
                // 长按重复：什么都不做（维持既有拉伸量）
            } else {
                // 音量真的动了 ⇒ 不再是"顶住"状态，取消蓄力，让条收回常态。
                volumeStretch.pull = 0f
            }
            // ⚠ 只有"新手势"才取消上一次抬手排定的释放；长按重复期间保持它，
            //   否则长按中途就会因release 超时而把条拉回去（视觉上闪一下）。
            if (freshPress) volumeStretch.releaseAtNanos = 0L
        }
        volumeStretch.lastEventNanos = System.nanoTime()

        // ── 弹性跟随 + 释放判定 ─────────────────────────────────────────────────
        // 跟随**解析解**推进（不是数值积分）：
        // ⚠ 这里绝不能用半隐式欧拉那套 —— 阻尼项 `2ζω·dt` 在 ω=16~18 时已达 0.3，
        //   每帧要削掉 30% 速度，数值阻尼会把幅度吃掉一大半、还会把过程压成硬跳。
        //   （实测：同一组参数半隐式欧拉算出的峰值只有 0.046 量程 ≈ 0.6dp，肉眼完全不可见，
        //     而解析解是 1.000 —— 这是算法问题，调参救不了。）
        // ⚠ 解析解还与**帧率无关**：60Hz / 120Hz 走同一条轨迹。
        // ★ 2026-10-07 用户报障：「原版的拉伸没有这种回弹弹簧」——
        //   旧实现 ζ=0.50 是**欠阻尼**，抬手回位时会越过 0 摆到反侧、再摆回来，
        //   视觉上就是「duang 一下」的多余振荡 —— 那是弹簧玩具，不是 iOS。
        //   iOS 橡皮筋回位是**单调收敛、不越界**：等价于**临界阻尼 ζ=1**。
        val omega = 18f
        val decay = omega                 // ζ=1 ⇒ ζω = ω
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 1f / 60f
            else ((now - last) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
            last = now
            // 释放判定（两条路，任一生效即释放）：
            //   ① 抬手后过了 [RELEASE_DELAY_MS]；
            //   ② 兜底：距上次按键事件超过 [HOLD_FALLBACK_MS] 说明这个 ROM 可能压根不投递
            //      `onKeyUp` ⇒ 宁可提前收回，也不能让条永远卡在拉长态。
            if (volumeStretch.releaseAtNanos != 0L && now >= volumeStretch.releaseAtNanos) {
                volumeStretch.pull = 0f
                volumeStretch.releaseAtNanos = 0L
            }
            if (volumeStretch.lastEventNanos != 0L &&
                now - volumeStretch.lastEventNanos > HOLD_FALLBACK_MS * 1_000_000L
            ) {
                volumeStretch.pull = 0f
                volumeStretch.releaseAtNanos = 0L
            }
            // ★ iOS 橡皮筋（UIScrollView 自 iOS 6 起的官方公式，已归一到"上限 = 1"）：
            //     b = (1 − 1/(x·c/d + 1))·d        Apple 取 c = 0.55
            //   把 x 记作 pull、d 归一成 1，公式化简为 b = pull·c/(1 + pull·c)。
            //   ⚠ 这里用 [IOS_RUBBER_BAND_C]=1.1 而非 Apple 的 0.55 —— 见该常量注释：
            //     要保证「**单按一格**就看得见拉伸」，c 必须够大（详见 2026-10-07 模拟数据）。
            //   凹函数 ⇒ **越推越硬、渐近逼近上限但永远撞不到墙**：
            //     单按 → 0.52；连按 8 次 → 0.85；∞ → 1.00（永不到达）。
            val target = volumeStretch.direction * IOS_RUBBER_BAND_C * volumeStretch.pull /
                (1f + IOS_RUBBER_BAND_C * volumeStretch.pull)
            // 带目标位置的阻尼振子：x'' = −ω²(x − target) − 2ζω·x'
            //   令 y = x − target ⇒ y'' = −ω²y − 2ζω·y'（单帧内 target 视为常数，逐帧更新）
            //   ⛔ ζ=1（临界阻尼）专用解：y(t) = (y0 + (v0 + ω·y0)·t)·e^{−ωt}
            //     这是**单调收敛、绝不过冲**的唯一解析形式；
            //     欠阻尼那个 e^{−ζωt}(y·cos ωd t + B·sin ωd t) 在 ζ=1 时 ωd→0（除零），
            //     且它本身就是"会摆回去"的振荡解 —— 与 iOS 手感相反，⛔ 勿再改回。
            val y = volumeStretch.position - target
            val vel = volumeStretch.velocity
            val e = exp(-decay * dt)
            val b = vel + decay * y                       // = v0 + ω·y0（ω=ζω）
            val yNew = (y + b * dt) * e
            val vNew = (b - decay * (y + b * dt)) * e
            volumeStretch.position = target + yNew
            volumeStretch.velocity = vNew
            // 收干净就退出：`withFrameNanos` 会持续申请新帧，常驻循环会钉住刷新率
            //   （本项目 VRR 控制器对"静置回落"极敏感，绝不能让空转的帧循环续期）。
            if (volumeStretch.pull == 0f && volumeStretch.releaseAtNanos == 0L &&
                abs(yNew) < 0.01f && abs(vNew) < 0.08f
            ) {
                volumeStretch.position = 0f
                volumeStretch.velocity = 0f
                break
            }
        }
    }

    val trackHeight by animateDpAsState(
        targetValue = if (dragging) 6.dp else 4.dp,
        animationSpec = tween(120),
        label = "scene-volume-track-height",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            // ★ 音量区内的**竖直**拖拽必须在本层吃掉，不能冒泡到页面级「下拉收起」
            //   （2026-10-07 用户报障，完整根因见函数注释）。
            .meloXVolumeAreaBlocksPageCollapse(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 10.dp),
    ) {
        SceneCupertinoGlyph(
            kind = SceneGlyphKind.SpeakerLow,
            useVectorGlyphs = useVectorGlyphs,
            // compact 盒子给到墨迹光学尺寸（字体轮廓高 24dp ⇒ 墨 16.2×24dp）。
            modifier = if (compact) Modifier.size(width = 18.dp, height = 24.dp) else Modifier.size(12.dp),
            color = Color.White.copy(alpha = 0.62f),
        )

        Slider(
            value = shownVolume,
            // ⚠ 过渡期禁用（用户报障「缩小播放器里的音量还能点/拖」）。
            enabled = LocalMeloXPlayerControlsEnabled.current,
            onValueChange = {
                // beginVolumeDrag：拖动期间抑制系统音量回灌（广播 + 轮询），
                // 否则系统回读的**跳格**值会与手指**连续**位置打架 ⇒ 抽搐。
                state.beginVolumeDrag()
                dragging = true
                dragVolume = it.coerceIn(0f, 1f)
                state.changeVolume(dragVolume)
            },
            onValueChangeFinished = {
                // 拖动结束：解除抑制，让轮询/广播重新接管（对齐到系统真实档位）。
                dragging = false
                state.endVolumeDrag()
                // 松手瞬间取一次权威值，让滑块精确落在真实系统档位上（不靠估算）。
                // ⚠ 走 Dispatchers.Default 异步：这里是同步 binder（getStreamVolume），
                //   跑在主线程会与拖动末帧抢时间 ⇒ 掉帧闪一下。
                scope.launch(Dispatchers.Default) { state.syncSystemVolume() }
            },
            modifier = Modifier
                .weight(1f)
                .height(32.dp),
            thumb = { Spacer(Modifier.size(0.dp)) },
            track = {
                val h = trackHeight
                // ⚠ 绘制节点必须比"常态条"**大出 STRETCH_EXTRA_H 的一圈**（上下各留一份），
                //   否则 `barH = baseH + 2·STRETCH_EXTRA_H·s` 会超出本节点边界 ⇒ 被父层裁掉
                //   ⇒ 又变成"拉伸量算对了但看不见"（v76 实测：条高恒定、Δ0）。
                //   常态时 `s=0` ⇒ 画出的条仍是 `h`，几何与旧实现完全一致。
                Box(
                    modifier = Modifier
                        // ⚠⚠⚠ 这里**绝对不能**加 `padding(horizontal = STRETCH_EXTRA_W)`。
                        //   2026-10-07 连续两次踩坑：
                        //   ① 加了 padding（为了给拉伸留富余）→ 常态条被硬缩窄 2·13 = 26dp，
                        //      用户报障「AM 的音量条有这么短吗」；
                        //   ② 只在绘制侧把那份 13dp 补回来、padding 留着 → 整条**右移 13dp**，
                        //      用户报障「音量条偏右」。
                        //   正确解：**节点保持 Slider 全宽**（不 padding），富余靠"画到节点外"
                        //   实现 —— 纵向富余必须留（画高会被裁），横向不需要：Canvas / Slider
                        //   都不裁剪超出自身边界的绘制（拉伸一直是这样работа的，见下）。
                        //   常态 `s=0` ⇒ 条 = [0, 节点全宽] = Slider 全宽，与 AM 一致。
                        .fillMaxWidth()
                        .height(h + STRETCH_EXTRA_H.dp * 2f),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawVolumeTrack(
                            position = volumeStretch.position,
                            direction = volumeStretch.direction,
                            fillFraction = shownVolume,
                            extraH = STRETCH_EXTRA_H.dp,
                            extraW = STRETCH_EXTRA_W.dp,
                        )
                    }
                }
            },
        )

        SceneCupertinoGlyph(
            kind = SceneGlyphKind.SpeakerHigh,
            useVectorGlyphs = useVectorGlyphs,
            // compact 墨迹宽高比 1.5016 ⇒ 盒宽 = 1.5016×24 ≈ 36dp（高 24dp 与左侧等高）。
            modifier = if (compact) Modifier.size(width = 36.dp, height = 24.dp) else Modifier.size(15.dp),
            color = Color.White.copy(alpha = 0.62f),
        )
    }
}

/**
 * 画音量条本体（轨道底色 + 已填充段），**在绘制阶段**按 [position] 现算"过冲拉伸"的变形。
 *
 * ★ 为什么必须放在绘制阶段（而不是 `Slider` 的 `track` 组合插槽里）：
 *   Material3 `Slider` 会把 `track` 插槽产出的 lambda **缓存**；写在插槽里的量只在
 *   **组合期**求值一次，绘制时用的是那次的结果。`position` 是每帧变化的 state，
 *   在组合期读它 ⇒ 拿到的是**首帧的 0** ⇒ 拉伸永远不发生（v74 实测：条高恒定 28px、
 *   max 处连按 11 次宽度 Δ0）。放进 `Canvas` 的绘制 lambda 后每帧重跑、每帧拿最新值，
 *   且只失效绘制层、不触发重组。
 *
 * ★ 圆角为什么自己算：半径直接取 `高/2`（胶囊形），**与任何缩放无关** ⇒ 永远真圆。
 *   走 `clip(CircleShape)` + 非均匀 `scaleX` 会被拉成椭圆（用户实测"圆角不对劲"），
 *   而 `CornerSize.toPx` 只返回单一半径、无法表达 X≠Y 的圆角，没有补偿 API。
 *
 * @param position 拉伸位移（0=常态，±1=拉满），由橡皮筋公式 damped 得到。
 * @param direction ≥0 顶上限（向右长出去）、<0 顶下限（向左长出去）。
 * @param fillFraction 已填充比例（0..1，单通道显示值）。
 * @param extraH 拉满时的**额外高度**（dp）。
 * @param extraW 拉满时单侧的**额外宽度**（dp）。
 */
private fun DrawScope.drawVolumeTrack(
    position: Float,
    direction: Int,
    fillFraction: Float,
    extraH: Dp,
    extraW: Dp,
) {
    val s = abs(position)
    // 常态高度 = 布局给的整条高度（绘制层拿不到 Dp，换算成 px 用 size.height 即可）。
    // ⚠ 节点比常态条大出上下各 [extraH]（见调用点注释），所以这里要**减掉**那份富余，
    //   还原成"常态条高"，否则常态下条会比原来粗 extraH。
    val baseH = (size.height - extraH.toPx() * 2f).coerceAtLeast(1f)
    val extraHPx = extraH.toPx() * s
    val extraWPx = extraW.toPx() * s
    // 变粗：以**中心**为轴上下对称增厚（不改变行的中心线，避免与喇叭图标错位）。
    val barH = baseH + extraHPx
    val top = (size.height - barH) / 2f
    val radius = CornerRadius(barH / 2f, barH / 2f)
    // 变长：整条（含底色）单侧外鼓；顶上限向右、顶下限向左。
    //   ⚠⚠⚠ 2026-10-07 连续两次踩坑，务必读注释再改：
    //   ① 调用点**不能**加 `padding(horizontal = STRETCH_EXTRA_W)` 来"留富余"——
    //      常态下那条 padding 生效 ⇒ 条被缩窄 26dp（「AM 的音量条有这么短吗」）。
    //   ② 也**不能**在绘制侧把 padding 补回来（`left = slackW`）——
    //      节点已经窄了 26dp，再右移 13dp 只会让整条偏右（「音量条偏右」）。
    //   ⇒ 正解：节点宽 = Slider 全宽（不padding）；拉伸时单侧外鼓到**节点之外**，
    //      Canvas 与 Slider 都不裁剪越界绘制（纵向同理：上面那圈 extraH 只是布局富余）。
    val left = if (direction >= 0) 0f else -extraWPx
    val totalW = size.width + extraWPx
    // ① 轨道底色（整条拉伸后的胶囊）
    drawRoundRect(
        color = Color.White.copy(alpha = 0.20f),
        topLeft = Offset(left, top),
        size = Size(totalW, barH),
        cornerRadius = radius,
    )
    // ② 已填充段：宽度按同一比例缩放（拉伸时填充也一起变长），左端与底色同侧对齐。
    //   ⚠⚠ 右端**方角**（2026-10-07 用户报障：「白色音量条右边有圆角，
    //   非最大音量时不应该圆」）：iOS/AM 的音量条只有**左端**是半圆（起点），
    //   右端随填充量推进是**平切口**；只有**满格**（fill ≥ 0.999）时右端才变成半圆，
    //   整条成为完整胶囊。实现见下面：非满格走**每角独立半径的单一子路径**。
    val frac = fillFraction.coerceIn(0f, 1f)
    val fillW = (totalW * frac).coerceAtLeast(0f)
    if (fillW > 0f) {
        if (frac >= 0.999f) {
            // 满格：整条胶囊（两端半圆）
            drawRoundRect(
                color = Color.White.copy(alpha = 0.82f),
                topLeft = Offset(left, top),
                size = Size(fillW, barH),
                cornerRadius = radius,
            )
        } else {
            // 非满格：**单个子路径、一次 draw**，左端两角圆、右端两角方。
            // ⚠⚠⚠ 两轮踩坑（2026-10-07，勿退回旧写法）：
            //   ① 分两次 draw（右侧 `drawRect(left+r…)` + 左端 `drawRoundRect(…, 2r)`）
            //      → 两者在 `[left+r, left+2r]` **互压**；填充是 alpha=0.82 的半透明白，
            //      叠一次 0.82、叠两次 `1−(1−0.82)²≈0.97` ⇒ 左端「比白色还白的白斑」。
            //   ② 改成「左圆头子路径 + 右侧 `addRect` 子路径」拼一次 draw 后，两段仍是
            //      **两个子路径**，接缝处抗锯齿各画半边 ⇒ 裂成「一个圆点 + 一段矩形条」。
            //   ⇒ 正解只有一个：**一个子路径 + 每角独立半径**（右=0、左=r）。
            val r2 = minOf(barH / 2f, fillW / 2f)
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(left, top, left + fillW, top + barH),
                        topLeft = CornerRadius(r2, r2),
                        topRight = CornerRadius.Zero,
                        bottomRight = CornerRadius.Zero,
                        bottomLeft = CornerRadius(r2, r2),
                    ),
                )
            }
            drawPath(path, Color.White.copy(alpha = 0.82f))
        }
    }
}

/**
 * ★★ 2026-10-07 用户报障：「播放页音量调节的**上下**区域会触发下滑收起播放页」。
 *
 * 根因是**这次手势没人接**：
 *   ① 音量条本身是**水平** `Slider`（`draggable(orientation = Horizontal)`），对竖直位移
 *      一概不消费；而 Row 高 42dp、Slider 只有 32dp ⇒ 条体**正上方 / 正下方各 5dp**
 *      纯属空白，连 Slider 的命中区都进不去。
 *   ② 整页「下拉收起」的 `draggable(orientation = Vertical)` 挂在**高得多的祖先**上
 *      （`MeloXIOSNowPlayingSharedHost` 的全屏 Box —— 该处注释写的是「竖屏保留整屏下拉收起」）。
 *   ⇒ 手指在这片区域**竖着**划时，位移无人消费，一路冒泡到那个祖先，播放页被收起。
 *
 * 修法：在音量控件这一层就把**竖直**拖拽吃掉，让页面级手势收不到。
 *   ① **不消费 down** —— Slider 要靠 down 才进入拖拽 / 点按设值，吞了它整条就废了。
 *   ② **只消费「已判定为竖直」之后的位移**；且一旦发现这次手势已被**更深的节点**接管
 *      （`isConsumed`，即水平 Slider 已经吃掉位移）就立刻让位 ⇒ 水平调音量逐像素不变。
 *   ③ 本节点是页面级 `draggable` 的**后代**，而 `Main` pass 的派发顺序是**叶 → 根**
 *      （见 `MeloXIOSNowPlayingSharedHost` 里对 `Initial` / `Main` 方向的论述）⇒ 这里先
 *      消费，上层那台 `draggable` 的 slop 判定随即作废，下拉收起根本不会启动。
 *
 * ⚠ 判据用「竖向位移 > touchSlop **且** 竖向 > 横向」，与系统 Slider 的取向锁定同义：
 *   用户想横划调音量时横向占优 ⇒ 本函数永不介入。
 * ⚠ 这不是「干掉下拉手势」——它只覆盖音量控件这一条 42dp 的区域；页面其余部分
 *   （封面 / 空白）照旧下拉即收起。
 */
private fun Modifier.meloXVolumeAreaBlocksPageCollapse(): Modifier = this.pointerInput(Unit) {
    // ⚠ touchSlop 在 PointerInputScope 上取（`AwaitPointerEventScope` 未必暴露它）。
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var engaged = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            // 更深的子节点（水平 Slider）已经接管这次手势 ⇒ 让位，一个位移都不消费。
            if (change.isConsumed) return@awaitEachGesture
            if (!engaged) {
                val dy = change.position.y - down.position.y
                val dx = change.position.x - down.position.x
                if (abs(dy) > slop && abs(dy) > abs(dx)) engaged = true
            }
            if (engaged) change.consume()
            if (!change.pressed) break
        }
    }
}

/**
 * 横屏底栏三键（[ScenePageSelector] 的 `compact` 形态）的左右留白。
 *
 * ★ 这一份同时是**横屏右列其它部件的左缘基准**（2026-09-30 用户要求）：除三键外的部件
 *   （曲名栏 / 歌词 / 队列）左缘一律与三键里**最左那颗按钮的左缘**齐平 —— 也就是选中态
 *   那圈圆形底板的左缘。派生差值见 `MeloXIOSNowPlayingScene` 的
 *   `MeloXLandscapeLyricsStartInset`。调用点不要再写死 18.dp。
 */
internal val MeloXCompactPageSelectorHPadding = 18.dp

@Composable
internal fun ScenePageSelector(
    state: MeloXPlaybackUiState,
    page: MeloXNowPlayingPage,
    onPageSelected: (MeloXNowPlayingPage) -> Unit,
    compact: Boolean = false,
    // ★ 默认跟随 compact：横屏底栏（compact=true）直接调用本函数时也能自动拿到矢量字形，
    //   不必每个 call site 都显式传 true；显式传参仍优先。竖屏 / Classic 走 false（原 SF 字形）。
    useVectorGlyphs: Boolean = compact,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rowHeight = if (compact) 42.dp else 50.dp
    val hPadding = if (compact) MeloXCompactPageSelectorHPadding else 32.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
            .padding(horizontal = hPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScenePageButton(
            kind = SceneGlyphKind.Lyrics,
            selected = page == MeloXNowPlayingPage.Lyrics,
            enabled = true,
            compact = compact,
            useVectorGlyphs = useVectorGlyphs,
            onClick = { onPageSelected(MeloXNowPlayingPage.Lyrics) },
        )

        ScenePageButton(
            kind = SceneGlyphKind.AirPlay,
            selected = false,
            enabled = true,
            compact = compact,
            useVectorGlyphs = useVectorGlyphs,
            onClick = { state.showOutputPicker = true },
        )

        Box {
            ScenePageButton(
                kind = SceneGlyphKind.Queue,
                selected = page == MeloXNowPlayingPage.Queue,
                enabled = true,
                compact = compact,
                useVectorGlyphs = useVectorGlyphs,
                onClick = { onPageSelected(MeloXNowPlayingPage.Queue) },
            )

            // ★ 右上角徽标：本体图标 + 当前「歌单播放形式」（2026-09-30 用户要求）——
            //   随机=shuffle 箭头、单曲循环=循环箭头内叠「1」、列表循环=裸循环箭头。
            //   深色圆底 + 亮色图形（对比截图）。仍只在「不在列表页且有非默认模式」时出现。
            if (
                page != MeloXNowPlayingPage.Queue &&
                (state.shuffleEnabled || state.repeatMode != 0)
            ) {
                // 基座直径较原值缩 5%（20→19dp / 18→17.1dp），并整体朝右上挪 1.5dp，
                // 让徽标与列表按钮本体的图标/触控气泡脱开、不再相贴（2026-10-15 用户要求）。
                val badgeSize = (if (compact) 18.dp else 20.dp) * 0.95f
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 1.5.dp, y = (-1.5).dp)
                        .size(badgeSize)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.42f)),
                    contentAlignment = Alignment.Center,
                ) {
                    ScenePlaybackModeBadge(
                        shuffle = state.shuffleEnabled,
                        repeatMode = state.repeatMode,
                        size = badgeSize * 0.72f,
                        modifier = Modifier,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
            }
        }
    }
}


/**
 * 当前歌单播放形式的徽标图形：随机 = 交叉箭头；循环 = 带箭头的开环；
 * 单曲循环 = 同一环 + 右上角的「1」。
 *
 * 字形来源统一在 [com.lladlam.melox.ui.glass.MeloXPlaybackModeGlyph]（1:1 复刻
 * SF Symbols 真实轮廓，实现见 `ui/glass/MeloXSymbol.kt`）—— 与队列面板顶部模式开关
 * 里的「列表循环 / 单曲循环」图标共用同一份几何（用户 2026-10-15 要求两处「同款」）。
 */
@Composable
private fun ScenePlaybackModeBadge(
    shuffle: Boolean,
    repeatMode: Int,
    size: Dp,
    modifier: Modifier,
    color: Color,
) {
    val symbol = when {
        shuffle -> MeloXSymbol.Shuffle
        repeatMode == 1 -> MeloXSymbol.RepeatOne
        else -> MeloXSymbol.Repeat
    }
    MeloXPlaybackModeGlyph(
        symbol = symbol,
        size = size,
        modifier = modifier.meloXIconDrawingScale(),
        color = color,
    )
}

@Composable
private fun ScenePageButton(
    kind: SceneGlyphKind,
    selected: Boolean,
    enabled: Boolean,
    compact: Boolean = false,
    useVectorGlyphs: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 650f,
        ),
        label = "scene-page-button-press-${kind.name}",
    )
    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "scene-page-button-selected-${kind.name}",
    )
    val buttonSize = if (compact) 40.dp else 48.dp
    val glyphSize = if (compact) 18.dp else 22.dp
    // ★ 圆盘比按钮本体小一圈（2026-10-01 用户反馈「圆太大」）。按钮本体仍是热区，
    //   只有可见的玻璃圆盘收小；图标按 buttonSize 居中，与圆盘同心。
    val discSize = buttonSize * 0.8f

    Box(
        modifier = Modifier
            .size(buttonSize)
            .graphicsLayer {
                val s = pressScale * selectionScale
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .semantics {
                contentDescription = when (kind) {
                    SceneGlyphKind.Lyrics -> "歌词"
                    SceneGlyphKind.AirPlay -> "播放设备"
                    SceneGlyphKind.Queue -> "接下来播放"
                    else -> kind.name
                }
            }
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // ★★ 选中态「白色高斯模糊圆盘 + 镂空图标」（2026-10-04 用户要求，2026-10-05 扩到 Queue）：
        //   圆盘 = 白色高斯模糊玻璃（kyant0 vibrancy + blur），深浅主题统一白色盘。
        //   歌词按钮选中态：用 SF 字形轮廓（quote.bubble，U+10032E）在白色纱上挖洞
        //   （ClipOp.Difference），洞形状 = 原歌词图标，透出其下模糊背景 ⇒ 真镂空、
        //   且图标形状与原字形逐像素一致（用户要求「图标跟原来一样」）。
        //   队列（歌单列表，最右按钮）选中态：用与矢量 Queue 字形完全一致的几何挖洞，
        //   同样真镂空（2026-10-05 用户要求「最右按钮也镂空」）。
        //   其余按钮（AirPlay 等不可选中）选中态：白盘 + 深色反色图标（保证在白盘上可见）。
        val context = LocalContext.current
        val density = LocalDensity.current
        val lyricsHolePath = remember(context, density, glyphSize, discSize) {
            // 2026-10-14 卡顿修复：改走进程级缓存（MeloSfTypefaceCache），
            // 播放页展开首帧不再重复同步加载 sf_pro_subset。
            val tf = MeloSfTypefaceCache.obtain(context, android.graphics.Typeface.NORMAL)
            if (tf == null) {
                null
            } else {
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf
                    textSize = with(density) { glyphSize.toPx() }
                    textAlign = android.graphics.Paint.Align.LEFT
                    fontFeatureSettings = "'ss16' 1"
                }
                val g = String(Character.toChars(0x10032E))
                val b = android.graphics.Rect()
                p.getTextBounds(g, 0, g.length, b)
                val discPx = with(density) { discSize.toPx() }
                val cx = discPx / 2f - (b.left + b.right) / 2f
                val base = discPx / 2f - (b.top + b.bottom) / 2f
                val ap = android.graphics.Path()
                p.getTextPath(g, 0, g.length, cx, base, ap)
                ap.asComposePath()
            }
        }
        // 队列（歌单列表，最右按钮）选中态「镂空图标」：用与 [SceneCupertinoGlyph]
        // 中 Queue 矢量字形**完全相同**的归一化几何（圆点 + 粗圆头横线，list.bullet
        // 样式）在白色纱上挖洞，透出模糊背景 ⇒ 与歌词按钮一致的「真镂空」
        // （2026-10-05 用户要求）。holePath 须位于圆盘坐标系（0..discPx）、与字形
        // 同心，否则镂空位置会与未选中时的实色字形错位。
        val queueHolePath = remember(density, glyphSize, discSize) {
            val glyphPx = with(density) { glyphSize.toPx() }
            val discPx = with(density) { discSize.toPx() }
            val off = (discPx - glyphPx) / 2f
            val lineStroke = 0.11f * glyphPx
            val dotR = lineStroke * 0.62f
            val halfStroke = lineStroke / 2f
            val barX1 = off + 0.30f * glyphPx
            val barX2 = off + 0.92f * glyphPx
            val p = Path()
            listOf(0.22f, 0.50f, 0.78f).forEach { yn ->
                val cy = off + yn * glyphPx
                p.addOval(
                    Rect(
                        left = off + 0.10f * glyphPx - dotR,
                        top = cy - dotR,
                        right = off + 0.10f * glyphPx + dotR,
                        bottom = cy + dotR,
                    ),
                )
                p.addRect(Rect(left = barX1, top = cy - halfStroke, right = barX2, bottom = cy + halfStroke))
                p.addOval(Rect(left = barX1 - halfStroke, top = cy - halfStroke, right = barX1 + halfStroke, bottom = cy + halfStroke))
                p.addOval(Rect(left = barX2 - halfStroke, top = cy - halfStroke, right = barX2 + halfStroke, bottom = cy + halfStroke))
            }
            p
        }
        if (selected) {
            Box(
                Modifier
                    .size(discSize)
                    .meloXScenePageDisc(
                        surfaceColor = Color.White.copy(alpha = 0.85f),
                        holePath = when (kind) {
                            SceneGlyphKind.Lyrics -> lyricsHolePath
                            SceneGlyphKind.Queue -> queueHolePath
                            else -> null
                        },
                    ),
            )
        }
        if (!(selected && (kind == SceneGlyphKind.Lyrics || kind == SceneGlyphKind.Queue))) {
            SceneCupertinoGlyph(
                kind = kind,
                useVectorGlyphs = useVectorGlyphs,
                compact = compact,
                modifier = Modifier.size(glyphSize),
                color = when {
                    !enabled -> Color.White.copy(alpha = 0.26f)
                    selected -> Color.Black.copy(alpha = 0.82f) // 白盘上深色图标
                    else -> Color.White.copy(alpha = 0.72f)
                },
            )
        }
    }
}

private enum class SceneGlyphKind {
    Backward,
    Forward,
    Play,
    Pause,
    SpeakerLow,
    SpeakerHigh,
    Lyrics,
    AirPlay,
    Queue,
    Waveform,
}

/** 改用内置矢量重绘的字形集合（其余仍走 SF 字体字形）。 */
private val VECTOR_GLYPHS = setOf(
    SceneGlyphKind.SpeakerLow,
    SceneGlyphKind.SpeakerHigh,
    SceneGlyphKind.AirPlay,
    SceneGlyphKind.Queue,
)

/**
 * 音量图标的**字体真轮廓**墨迹路径（2026-09-30 用户要求按参考截图高精度矢量还原）。
 * 横屏 / 竖屏 iOS 共用，Classic 仍走 SF 字形。
 *
 * 来源：`sf_pro_subset.ttf` 的 `speaker.wave.2.fill`（U+1002A7，glyph `uni1002A7.medium`，
 * upm 2048）用 fontTools 逐轮廓抽取（脚本 `tmp/extract_speaker.py`）——喇叭体与弧1、弧2
 * 直接取字体轮廓；弧3 由弧2 按同族几何外推（关于喇叭竖直中线 y=721 放大 1.32，左缘
 * 与弧2 的间隙取弧1→弧2 的实际间距 104 units）。坐标已归一化：**高度 = 1**、y 向下
 * （屏幕系），原点在各自墨迹 bbox 左上角；绘制时按 Canvas 高度等比缩放、水平居中。
 * 右图标墨迹宽高比 1.5016，左图标（仅喇叭体）0.6752。
 */
private val SpeakerLowInkPath: Path = Path().apply {
    moveTo(0.611f, 1.0f)
    quadraticTo(0.611f, 1.0f, 0.591f, 1.0f)
    quadraticTo(0.574f, 0.991f, 0.556f, 0.983f)
    quadraticTo(0.546f, 0.974f, 0.537f, 0.964f)
    lineTo(0.295f, 0.735f)
    quadraticTo(0.29f, 0.73f, 0.281f, 0.73f)
    lineTo(0.118f, 0.73f)
    quadraticTo(0.118f, 0.73f, 0.061f, 0.73f)
    quadraticTo(0.031f, 0.699f, 0.0f, 0.668f)
    quadraticTo(0.0f, 0.637f, 0.0f, 0.606f)
    lineTo(0.0f, 0.395f)
    quadraticTo(0.0f, 0.395f, 0.0f, 0.334f)
    quadraticTo(0.031f, 0.302f, 0.061f, 0.271f)
    quadraticTo(0.09f, 0.271f, 0.118f, 0.271f)
    lineTo(0.281f, 0.271f)
    quadraticTo(0.29f, 0.271f, 0.295f, 0.266f)
    lineTo(0.537f, 0.039f)
    quadraticTo(0.537f, 0.039f, 0.559f, 0.019f)
    quadraticTo(0.575f, 0.009f, 0.59f, 0.0f)
    quadraticTo(0.6f, 0.0f, 0.61f, 0.0f)
    quadraticTo(0.61f, 0.0f, 0.639f, 0.0f)
    quadraticTo(0.657f, 0.02f, 0.675f, 0.039f)
    quadraticTo(0.675f, 0.053f, 0.675f, 0.067f)
    lineTo(0.675f, 0.937f)
    quadraticTo(0.675f, 0.937f, 0.675f, 0.964f)
    quadraticTo(0.657f, 0.982f, 0.639f, 1.0f)
    quadraticTo(0.625f, 1.0f, 0.611f, 1.0f)
    close()
}

private val SpeakerHighInkPath: Path = Path().apply {
    moveTo(0.579f, 0.975f)
    quadraticTo(0.579f, 0.975f, 0.56f, 0.975f)
    quadraticTo(0.543f, 0.967f, 0.527f, 0.959f)
    quadraticTo(0.518f, 0.95f, 0.508f, 0.941f)
    lineTo(0.28f, 0.724f)
    quadraticTo(0.274f, 0.719f, 0.266f, 0.719f)
    lineTo(0.112f, 0.719f)
    quadraticTo(0.112f, 0.719f, 0.058f, 0.719f)
    quadraticTo(0.029f, 0.69f, 0.0f, 0.66f)
    quadraticTo(0.0f, 0.631f, 0.0f, 0.601f)
    lineTo(0.0f, 0.402f)
    quadraticTo(0.0f, 0.402f, 0.0f, 0.344f)
    quadraticTo(0.029f, 0.314f, 0.058f, 0.284f)
    quadraticTo(0.085f, 0.284f, 0.112f, 0.284f)
    lineTo(0.266f, 0.284f)
    quadraticTo(0.274f, 0.284f, 0.28f, 0.279f)
    lineTo(0.508f, 0.065f)
    quadraticTo(0.508f, 0.065f, 0.529f, 0.045f)
    quadraticTo(0.544f, 0.036f, 0.559f, 0.028f)
    quadraticTo(0.568f, 0.028f, 0.578f, 0.028f)
    quadraticTo(0.578f, 0.028f, 0.605f, 0.028f)
    quadraticTo(0.622f, 0.046f, 0.639f, 0.065f)
    quadraticTo(0.639f, 0.078f, 0.639f, 0.091f)
    lineTo(0.639f, 0.914f)
    quadraticTo(0.639f, 0.914f, 0.639f, 0.94f)
    quadraticTo(0.623f, 0.957f, 0.606f, 0.975f)
    quadraticTo(0.592f, 0.975f, 0.579f, 0.975f)
    moveTo(0.831f, 0.729f)
    quadraticTo(0.831f, 0.729f, 0.815f, 0.718f)
    quadraticTo(0.813f, 0.7f, 0.811f, 0.683f)
    quadraticTo(0.817f, 0.674f, 0.823f, 0.665f)
    quadraticTo(0.823f, 0.665f, 0.846f, 0.633f)
    quadraticTo(0.859f, 0.59f, 0.872f, 0.547f)
    quadraticTo(0.872f, 0.524f, 0.872f, 0.501f)
    quadraticTo(0.872f, 0.501f, 0.872f, 0.454f)
    quadraticTo(0.859f, 0.411f, 0.847f, 0.368f)
    quadraticTo(0.835f, 0.352f, 0.823f, 0.336f)
    quadraticTo(0.823f, 0.336f, 0.81f, 0.319f)
    quadraticTo(0.813f, 0.301f, 0.815f, 0.283f)
    quadraticTo(0.823f, 0.277f, 0.831f, 0.272f)
    quadraticTo(0.831f, 0.272f, 0.845f, 0.262f)
    quadraticTo(0.862f, 0.265f, 0.878f, 0.269f)
    quadraticTo(0.884f, 0.276f, 0.889f, 0.284f)
    quadraticTo(0.889f, 0.284f, 0.92f, 0.325f)
    quadraticTo(0.938f, 0.382f, 0.955f, 0.439f)
    quadraticTo(0.955f, 0.47f, 0.955f, 0.501f)
    quadraticTo(0.955f, 0.501f, 0.955f, 0.562f)
    quadraticTo(0.938f, 0.619f, 0.92f, 0.676f)
    quadraticTo(0.905f, 0.696f, 0.889f, 0.717f)
    quadraticTo(0.889f, 0.717f, 0.878f, 0.732f)
    quadraticTo(0.862f, 0.735f, 0.845f, 0.738f)
    quadraticTo(0.838f, 0.734f, 0.831f, 0.729f)
    moveTo(1.03f, 0.869f)
    quadraticTo(1.03f, 0.869f, 1.016f, 0.859f)
    quadraticTo(1.014f, 0.842f, 1.012f, 0.825f)
    quadraticTo(1.017f, 0.817f, 1.023f, 0.809f)
    quadraticTo(1.023f, 0.809f, 1.066f, 0.746f)
    quadraticTo(1.091f, 0.666f, 1.115f, 0.586f)
    quadraticTo(1.115f, 0.543f, 1.115f, 0.501f)
    quadraticTo(1.115f, 0.501f, 1.115f, 0.415f)
    quadraticTo(1.091f, 0.335f, 1.067f, 0.255f)
    quadraticTo(1.045f, 0.223f, 1.023f, 0.192f)
    quadraticTo(1.023f, 0.192f, 1.011f, 0.176f)
    quadraticTo(1.013f, 0.159f, 1.016f, 0.142f)
    quadraticTo(1.023f, 0.137f, 1.03f, 0.132f)
    quadraticTo(1.03f, 0.132f, 1.045f, 0.121f)
    quadraticTo(1.062f, 0.125f, 1.079f, 0.128f)
    quadraticTo(1.084f, 0.136f, 1.089f, 0.143f)
    quadraticTo(1.089f, 0.143f, 1.141f, 0.213f)
    quadraticTo(1.17f, 0.307f, 1.198f, 0.401f)
    quadraticTo(1.198f, 0.451f, 1.198f, 0.501f)
    quadraticTo(1.198f, 0.501f, 1.198f, 0.6f)
    quadraticTo(1.17f, 0.693f, 1.141f, 0.787f)
    quadraticTo(1.115f, 0.822f, 1.089f, 0.858f)
    quadraticTo(1.089f, 0.858f, 1.079f, 0.873f)
    quadraticTo(1.062f, 0.876f, 1.045f, 0.879f)
    quadraticTo(1.038f, 0.874f, 1.03f, 0.869f)
    moveTo(1.28f, 0.987f)
    quadraticTo(1.28f, 0.987f, 1.261f, 0.974f)
    quadraticTo(1.258f, 0.951f, 1.255f, 0.929f)
    quadraticTo(1.262f, 0.918f, 1.27f, 0.907f)
    quadraticTo(1.27f, 0.907f, 1.327f, 0.824f)
    quadraticTo(1.359f, 0.719f, 1.392f, 0.614f)
    quadraticTo(1.392f, 0.557f, 1.392f, 0.5f)
    quadraticTo(1.392f, 0.5f, 1.392f, 0.387f)
    quadraticTo(1.36f, 0.281f, 1.328f, 0.176f)
    quadraticTo(1.299f, 0.135f, 1.27f, 0.094f)
    quadraticTo(1.27f, 0.094f, 1.254f, 0.072f)
    quadraticTo(1.257f, 0.049f, 1.261f, 0.027f)
    quadraticTo(1.27f, 0.02f, 1.28f, 0.013f)
    quadraticTo(1.28f, 0.013f, 1.3f, 0.0f)
    quadraticTo(1.322f, 0.004f, 1.344f, 0.009f)
    quadraticTo(1.351f, 0.019f, 1.358f, 0.029f)
    quadraticTo(1.358f, 0.029f, 1.426f, 0.121f)
    quadraticTo(1.464f, 0.245f, 1.502f, 0.369f)
    quadraticTo(1.502f, 0.435f, 1.502f, 0.5f)
    quadraticTo(1.502f, 0.5f, 1.502f, 0.631f)
    quadraticTo(1.464f, 0.755f, 1.426f, 0.878f)
    quadraticTo(1.392f, 0.925f, 1.358f, 0.972f)
    quadraticTo(1.358f, 0.972f, 1.344f, 0.992f)
    quadraticTo(1.322f, 0.996f, 1.3f, 1.0f)
    quadraticTo(1.29f, 0.994f, 1.28f, 0.987f)
    close()
}

@Composable
private fun SceneCupertinoGlyph(
    kind: SceneGlyphKind,
    modifier: Modifier,
    color: Color,
    // ★ 高精度矢量重绘（2026-09-30 用户要求参照截图）：喇叭=实心无弧线 / 三条弧线、
    //   AirPlay=三条同心弧（平切端帽）+底部实心三角、列表=圆点+粗圆线。由 [useVectorGlyphs] 控制：
    //   横屏 / 竖屏 iOS 走矢量；Classic 走原 SF 字形 / 原 Canvas，逐像素不变。
    useVectorGlyphs: Boolean = false,
    // ★ compact（横屏底栏）下 SF 字形必须按 box 收尺寸：Lyrics 不在 VECTOR_GLYPHS，
    //   走 MeloXSymbolIcon 时按 iconSize(sp) 画、无视 Modifier.size，默认 22.sp 墨迹
    //   ~23dp 远大于 18dp 圆框 ⇒ 比两侧矢量按钮（AirPlay/Queue）明显大。横屏把 Lyrics
    //   收到 18.sp（墨迹≈18.8dp，与圆框同比例，与竖屏 22sp/22dp 一致），竖屏/Classic 不变。
    compact: Boolean = false,
) {
    if (useVectorGlyphs && kind in VECTOR_GLYPHS) {
        Canvas(modifier = modifier.meloXIconDrawingScale()) {
            val w = size.width
            val h = size.height
            when (kind) {
                SceneGlyphKind.SpeakerLow, SceneGlyphKind.SpeakerHigh -> {
                    // 字体真轮廓（见 [SpeakerLowInkPath] 注释）：按 Canvas 高度等比缩放、
                    // 水平居中；宽度不足时按宽度适配。填充走 nonZero（字形无孔，全实体）。
                    val low = kind == SceneGlyphKind.SpeakerLow
                    val ink = if (low) SpeakerLowInkPath else SpeakerHighInkPath
                    val aspect = if (low) 0.6752f else 1.5016f
                    val s = minOf(size.width / aspect, size.height)
                    val dx = (size.width - aspect * s) / 2f
                    val dy = (size.height - s) / 2f
                    // asAndroidPath 返回的是底层实例，transform 会原地改 ⇒ 先拷贝再变换。
                    val transformed = android.graphics.Path(ink.asAndroidPath()).apply {
                        transform(android.graphics.Matrix().apply {
                            postScale(s, s)
                            postTranslate(dx, dy)
                        })
                    }
                    drawPath(transformed.asComposePath(), color)
                }

                SceneGlyphKind.AirPlay -> {
                    // 三条同心弧 + 底部实心三角（2026-09-30 高精度重绘，几何像素取证自参考图）。
                    // 归一化到墨迹高 H=431px（墨迹宽高比 1.0348）：圆心 (0.5162, 0.5244)，
                    // 外圆与墨迹顶/左右缘相切；三弧中线半径 0.4838/0.3376/0.1937、线宽 0.0789
                    // （环间隙≈线宽的 0.83 倍）；弧端被「与三角边平行」的斜线统一裁切
                    // （斜边斜率 0.844，偏距=环间隙）⇒ 平切端帽 + 每弧终止角不同
                    // （135.3°/133.4°/128.5°，自正上方顺时针量）。三角尖伸入内环黑心，
                    // 尖/底角圆角 18/20px（quadraticBezier 倒角）。
                    val inkAspect = 1.0348f
                    val s = minOf(w / inkAspect, h)
                    val dx = (w - inkAspect * s) / 2f
                    val dy = (h - s) / 2f
                    fun px(nx: Float) = dx + nx * s
                    fun py(ny: Float) = dy + ny * s
                    val cx = px(0.51624f)
                    val cy = py(0.52436f)
                    val ringStroke = 0.0789f * s
                    listOf(
                        0.48376f to 135.33f,
                        0.33759f to 133.37f,
                        0.19374f to 128.52f,
                    ).forEach { (rm, thetaEnd) ->
                        drawArc(
                            color = color,
                            startAngle = 270f - thetaEnd,
                            sweepAngle = 2f * thetaEnd,
                            useCenter = false,
                            topLeft = Offset(cx - rm * s, cy - rm * s),
                            size = Size(2f * rm * s, 2f * rm * s),
                            style = Stroke(width = ringStroke, cap = StrokeCap.Butt),
                        )
                    }
                    // 实心三角（尖角圆滑，quadratic 倒角；坐标同上归一化系）。
                    val tri = Path().apply {
                        moveTo(px(0.54818f), py(0.60629f))   // 尖右切点
                        lineTo(px(0.85325f), py(0.96775f))   // 右底角斜边切点
                        quadraticTo(
                            px(0.87852f), py(0.99768f),      // 右底角（尖）
                            px(0.83935f), py(0.99768f),
                        )
                        lineTo(px(0.19313f), py(0.99768f))   // 底边
                        quadraticTo(
                            px(0.15397f), py(0.99768f),      // 左底角（尖）
                            px(0.17923f), py(0.96775f),
                        )
                        lineTo(px(0.48430f), py(0.60629f))   // 尖左切点
                        quadraticTo(
                            px(0.51624f), py(0.56845f),      // 尖顶（尖）
                            px(0.54818f), py(0.60629f),
                        )
                        close()
                    }
                    drawPath(tri, color)
                }

                SceneGlyphKind.Queue -> {
                    // 列表：圆点 + 粗圆头横线（list.bullet 样式）。
                    val lineStroke = 0.11f * h
                    listOf(0.22f, 0.50f, 0.78f).forEach { y ->
                        drawCircle(
                            color = color,
                            radius = lineStroke * 0.62f,
                            center = Offset(w * 0.10f, h * y),
                        )
                        drawLine(
                            color = color,
                            start = Offset(w * 0.30f, h * y),
                            end = Offset(w * 0.92f, h * y),
                            strokeWidth = lineStroke,
                            cap = StrokeCap.Round,
                        )
                    }
                }

                // VECTOR_GLYPHS 只含上面四种；其余分支走字体字形，这里不会命中。
                else -> {}
            }
        }
        return
    }
    val sfSymbol = when (kind) {
        SceneGlyphKind.Backward -> MeloXSymbol.Previous
        SceneGlyphKind.Forward -> MeloXSymbol.Next
        SceneGlyphKind.Play -> MeloXSymbol.Play
        SceneGlyphKind.Pause -> MeloXSymbol.Pause
        SceneGlyphKind.SpeakerLow -> MeloXSymbol.Volume
        SceneGlyphKind.SpeakerHigh -> MeloXSymbol.Volume
        SceneGlyphKind.Queue -> MeloXSymbol.Queue
        SceneGlyphKind.Lyrics -> MeloXSymbol.Lyrics
        SceneGlyphKind.Waveform -> MeloXSymbol.AutoMix
        else -> null
    }
    if (sfSymbol != null) {
        MeloXSymbolIcon(
            symbol = sfSymbol,
            modifier = modifier,
            color = color,
            variant = if (kind == SceneGlyphKind.Play || kind == SceneGlyphKind.Pause) {
                com.lladlam.melox.ui.glass.MeloXSymbolVariant.Fill
            } else {
                com.lladlam.melox.ui.glass.MeloXSymbolVariant.Regular
            },
            iconSize = when (kind) {
                SceneGlyphKind.Play, SceneGlyphKind.Pause -> 48.sp
                SceneGlyphKind.Backward, SceneGlyphKind.Forward -> 34.sp
                SceneGlyphKind.Queue, SceneGlyphKind.Waveform -> 22.sp
                // ★ 横屏(compact)圆框=18dp，SF 字形按 iconSize 画不收 box ⇒ 22.sp 墨迹~23dp
                //   比两侧矢量按钮大；收到 18.sp（墨迹≈18.8dp，与圆框同比例，等于竖屏 22sp/22dp）。
                SceneGlyphKind.Lyrics -> if (compact) 18.sp else 22.sp
                else -> 18.sp
            },
        )
        return
    }
    Canvas(modifier = modifier.meloXIconDrawingScale()) {
        val w = size.width
        val h = size.height
        val min = size.minDimension
        val stroke = (min * 0.085f).coerceAtLeast(1.35f)

        when (kind) {
            SceneGlyphKind.Play -> {
                val p = Path().apply {
                    moveTo(w * 0.28f, h * 0.13f)
                    quadraticTo(w * 0.22f, h * 0.10f, w * 0.22f, h * 0.22f)
                    lineTo(w * 0.22f, h * 0.78f)
                    quadraticTo(w * 0.22f, h * 0.90f, w * 0.30f, h * 0.86f)
                    lineTo(w * 0.82f, h * 0.56f)
                    quadraticTo(w * 0.91f, h * 0.50f, w * 0.82f, h * 0.44f)
                    close()
                }
                drawPath(p, color)
            }

            SceneGlyphKind.Pause -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.24f, h * 0.10f),
                    size = Size(w * 0.18f, h * 0.80f),
                    cornerRadius = CornerRadius(w * 0.045f),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.58f, h * 0.10f),
                    size = Size(w * 0.18f, h * 0.80f),
                    cornerRadius = CornerRadius(w * 0.045f),
                )
            }

            SceneGlyphKind.Backward,
            SceneGlyphKind.Forward -> {
                val forward = kind == SceneGlyphKind.Forward
                fun triangle(x0: Float, x1: Float) {
                    val p = Path()
                    if (forward) {
                        p.moveTo(x0, h * 0.17f)
                        p.lineTo(x1, h * 0.50f)
                        p.lineTo(x0, h * 0.83f)
                    } else {
                        p.moveTo(x1, h * 0.17f)
                        p.lineTo(x0, h * 0.50f)
                        p.lineTo(x1, h * 0.83f)
                    }
                    p.close()
                    drawPath(p, color)
                }
                triangle(w * 0.10f, w * 0.50f)
                triangle(w * 0.45f, w * 0.88f)
            }

            SceneGlyphKind.SpeakerLow,
            SceneGlyphKind.SpeakerHigh -> {
                val speaker = Path().apply {
                    moveTo(w * 0.08f, h * 0.41f)
                    lineTo(w * 0.29f, h * 0.41f)
                    lineTo(w * 0.54f, h * 0.22f)
                    quadraticTo(w * 0.58f, h * 0.19f, w * 0.58f, h * 0.27f)
                    lineTo(w * 0.58f, h * 0.73f)
                    quadraticTo(w * 0.58f, h * 0.81f, w * 0.54f, h * 0.78f)
                    lineTo(w * 0.29f, h * 0.59f)
                    lineTo(w * 0.08f, h * 0.59f)
                    close()
                }
                drawPath(speaker, color)

                if (kind == SceneGlyphKind.SpeakerHigh) {
                    drawArc(
                        color = color,
                        startAngle = -46f,
                        sweepAngle = 92f,
                        useCenter = false,
                        topLeft = Offset(w * 0.45f, h * 0.30f),
                        size = Size(w * 0.30f, h * 0.40f),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = color,
                        startAngle = -48f,
                        sweepAngle = 96f,
                        useCenter = false,
                        topLeft = Offset(w * 0.43f, h * 0.14f),
                        size = Size(w * 0.52f, h * 0.72f),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }

            SceneGlyphKind.Lyrics -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.07f, h * 0.09f),
                    size = Size(w * 0.86f, h * 0.70f),
                    cornerRadius = CornerRadius(w * 0.18f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                val tail = Path().apply {
                    moveTo(w * 0.61f, h * 0.78f)
                    lineTo(w * 0.52f, h * 0.93f)
                    lineTo(w * 0.72f, h * 0.79f)
                }
                drawPath(tail, color, style = Stroke(width = stroke, cap = StrokeCap.Round))

                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.28f, h * 0.31f),
                    size = Size(w * 0.10f, h * 0.18f),
                    cornerRadius = CornerRadius(w * 0.03f),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.53f, h * 0.31f),
                    size = Size(w * 0.10f, h * 0.18f),
                    cornerRadius = CornerRadius(w * 0.03f),
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.28f, h * 0.49f),
                    end = Offset(w * 0.23f, h * 0.58f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.53f, h * 0.49f),
                    end = Offset(w * 0.48f, h * 0.58f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }

            SceneGlyphKind.AirPlay -> {
                drawArc(
                    color = color,
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(w * 0.08f, h * 0.02f),
                    size = Size(w * 0.84f, h * 0.72f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color,
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(w * 0.24f, h * 0.18f),
                    size = Size(w * 0.52f, h * 0.44f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                val output = Path().apply {
                    moveTo(w * 0.50f, h * 0.50f)
                    lineTo(w * 0.78f, h * 0.92f)
                    lineTo(w * 0.22f, h * 0.92f)
                    close()
                }
                drawPath(output, color)
            }

            SceneGlyphKind.Queue -> {
                listOf(0.27f, 0.50f, 0.73f).forEach { y ->
                    drawCircle(
                        color = color,
                        radius = stroke * 0.72f,
                        center = Offset(w * 0.17f, h * y),
                    )
                    drawLine(
                        color = color,
                        start = Offset(w * 0.33f, h * y),
                        end = Offset(w * 0.88f, h * y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }

            SceneGlyphKind.Waveform -> {
                val xs = listOf(0.18f, 0.38f, 0.60f, 0.82f)
                val heights = listOf(0.36f, 0.72f, 0.55f, 0.30f)
                xs.zip(heights).forEach { (x, fraction) ->
                    val half = h * fraction * 0.5f
                    drawLine(
                        color = color,
                        start = Offset(w * x, h * 0.5f - half),
                        end = Offset(w * x, h * 0.5f + half),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

private fun AudioQualityTier.sceneTitle(): String = when (this) {
    AudioQualityTier.Standard -> "标准"
    AudioQualityTier.High -> "高品质"
    AudioQualityTier.Lossless -> "无损"
    AudioQualityTier.HiResolution -> "Hi-Res"
    AudioQualityTier.Immersive -> "臻品/环绕"
    AudioQualityTier.Master -> "臻品母带"
}

private fun sceneFormatDuration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
