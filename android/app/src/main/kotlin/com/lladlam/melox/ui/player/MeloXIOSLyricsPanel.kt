package com.lladlam.melox.ui.player

import com.lladlam.melox.core.lyrics.precedesLead

import android.content.Context
import android.content.Intent
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import java.text.BreakIterator
import java.util.Locale
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import com.lladlam.melox.core.lyrics.LyricHighlightStrategy
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricSyllable
import com.lladlam.melox.core.lyrics.LyricTimelineProcessor
import com.lladlam.melox.core.lyrics.LyricRomanizationAligner
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.lyrics.withPseudoTiming
import com.lladlam.melox.core.lyrics.withQqBracketBackingVocals
import com.lladlam.melox.core.lyrics.withoutOuterBrackets
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.meloXEdgeOverscrollHaptic
import com.lladlam.melox.MeloXAppVisibility
import com.lladlam.melox.ui.settings.MeloXLyricsStyle
import com.lladlam.melox.ui.theme.MeloXLanTingProFontFamily
import com.lladlam.melox.R
import androidx.core.content.res.ResourcesCompat
import com.lladlam.melox.ui.settings.MeloXLyricsRenderingQuality
import com.lladlam.melox.ui.settings.MeloXLyricAnnotationDisplayMode
import com.lladlam.melox.ui.settings.MeloXLyricsGroupingMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Android port of MeloX/AppleMusicLyricsView.swift + LyricPlaybackTimeline.swift
 * + SynchronizedLyricText.swift + LyricGlowTextRenderer.swift.
 *
 * Keep source constants here instead of "tuning by eye".  The rendering backend
 * is Compose rather than SwiftUI, but focus geometry, timing, dim/blur equations,
 * 120 ms colour hand-off, promoted current-line layout and cascade scheduling are
 * follows upstream MeloX. Cascade timing is shortened for Android's frame and
 * blur cost so quick consecutive lines do not leave several row animations in
 * flight at the same time.
 */
private object UpstreamLyrics {
    const val FONT_SIZE_SP = 34f
    const val LINE_HEIGHT_SP = 40.8f // fontSize * 1.2
    const val LINE_SPACING_DP = 28f
    const val CURRENT_LINE_SCALE = 1.02f
    const val FOCUS_POSITION = 0.25f
    const val BLUR_INTENSITY = 0.8f
    // ★ 行 alpha 低于此值才跳过模糊（2026-10-24 回调 0.18 → 0.05）。
    // 旧值 0.18 把 alpha<0.18 的远行整条跳过模糊，把 `distanceBlur` 的深度渐变从
    // 「远处柔化」砍成「远处发暗但清晰」，视觉上只剩焦点紧邻前后两行（走 `focusBlur`）
    // 还在糊 —— 与完整版本景深观感不符（用户反馈）。回调到 0.05 只跳过**接近不可见**
    // 的行，恢复渐变；稳态播放时模糊半径恒定 ⇒ RenderEffect 缓存不逐帧重建，不会回到
    // 10-19 之前 55% janky（那次是全局模糊强度过高，与此门槛正交）。
    const val BLUR_SKIP_ALPHA = 0.05f
    // ★★ 光晕「满档」的定义（2026-10-24，用户报障：设置显示 100%，实际生效不是 100%）。
    //   「逐字光晕」是 4 档百分比（关闭/60/100/140），语义上就是**强度的直接倍数**。
    //   但旧实现在 alpha 上另外乘了一个 `.648`，而字形光晕量本身峰值只有 `.7`
    //   （长音 `glowAmount` = .32–.7；普通字 `sourceOrdinaryGlowStrength` 峰值 .55）
    //   ⇒ 选 100% 实际峰值只有 .7×.648 ≈ **45%**，选 140% 也只有 ≈64%，60% 只剩 ≈27%
    //   —— 「显示值」与「生效值」整体对不上，档位之间也只差一点点。
    //   现在把满档归一到 1.0：先除掉各分支自身的峰值，再乘设置值 ⇒
    //   100% = 满强度、60% = 六成、140% = 一倍四（超过纯白由 alpha 钳制）。
    const val LONG_TONE_GLOW_PEAK = .7f
    const val ORDINARY_GLOW_PEAK = .55f
    // 进场揭示（见 `MeloXIOSLyricsPanel` 里 `lyricsReveal` 的注释）：0→1 的淡入曲线。
    // ★★ 2026-11-03（「渐显再丝滑一点」）：从固定 260ms ease-out tween 改为**无回弹弹簧**
    //   （`REVEAL_STIFFNESS` + `REVEAL_DAMPING_RATIO = 1`，即 `Spring.DampingRatioNoBounce`）。
    //   弹簧天然缓入缓出、无速度拐点 ⇒ 比「快起慢收」的固定 bezier 更顺；等效落定 ≈ 320/刚度。
    const val REVEAL_STIFFNESS = 320f
    const val REVEAL_DAMPING_RATIO = 1f
    // ★★ 2026-11-03（第 34 轮）：曾有的 `REVEAL_RISE_DP`(12f) 上浮已删除。
    //   用户录屏逐帧取证：文字在 reveal≈0.3~0.5 时已经可读，而 translationY 还剩大半 ——
    //   眼睛看到的是「歌词加载好了 → 整块又向上滑 11~12dp → 猛地落位」（互相关测得
    //   1.62→1.67s 同帧跳 ~38 设备px ≈ 12dp）。上浮完成得比「可见」晚 ⇒ 被读成加载后的
    //   第二次移动。现在 reveal **只做淡入**：第一帧就是最终位置，淡入呈现的即稳态画面。
    //   位移感由逐行模糊模型的景深提供（未播放行模糊、当前行清晰），不再需要整块上浮。
    // 首次定位允许消耗的 morph 时长（上限预算，配合 `shellTransitioning` 提前退出）。
    // `shellTransitioning` = `transitionInFlightState`（`expansionProgress < 0.999f`，见 SharedHost）：
    // 打开方向外壳展到 ≈99% 就落定 ⇒ 此预算**只卡安全上限**，实际等待 = 转场基本结束那一刻。
    // ★★ 2026-11-03（第 27 轮，用户新需求「歌词可以不同步出现、但要渐渐出现、不要突兀跳变」）：
    //   改回非 0（500ms 上限）⇒ 歌词 reveal 在**转场落定后**才启动（外壳先到、歌词后到），
    //   用 alpha 缓入 + 12dp 上浮 + **高斯模糊收敛**（见 graphicsLayer）做渐显，
    //   不再在展开首帧就硬出现（`scrollToItem` 的 measure/layout 也因此从最重首帧挪到运动之后）。
    //   之前（2026-10-24）设 0 是治「等满弹簧尾段导致 2.30s 才硬切」；现在 `transitionInFlight`
    //   判据已改为「转场基本结束就落定」（不是等弹簧停），所以 500ms 上限≈转场时长，不会回到 2.30s。
    //   ⚠ `withTimeoutOrNull` 兜底：万一判据不回落也不会让歌词永远不出现。
    const val INITIAL_REVEAL_POSITION_MS = 500L
    const val DIM_AMOUNT = 1f
    const val DISTANCE_BLUR_SCALE = 1.05f
    const val ROMANIZATION_FONT_SCALE = 0.65f
    const val TRANSLATION_FONT_SCALE = 0.65f
    const val ANNOTATION_OPACITY = 0.9f
    const val ANNOTATION_SPACING_DP = 4f
    const val FOCUS_COLOR_DURATION_MS = 120
    // ★★ 2026-11-03（第 31 轮）：**焦点模糊模型的渐入时长**。
    //   与 `FOCUS_COLOR_DURATION_MS`（120ms，只翻颜色，快一点更跟手）分开：
    //   这里的过渡要覆盖「整屏模糊半径」的变化，120ms 依然读得出台阶感，
    //   260ms 与歌词 reveal 的节奏一致，观感是「焦点慢慢浮出来」。
    const val FOCUS_MODEL_RAMP_MS = 260
    // ★★ 2026-11-03（第 31 轮）：**每行最终模糊半径的输出平滑时长**（兜底）。
    //   `FOCUS_MODEL_RAMP_MS` 只能覆盖「焦点模型切换」一类来源；半径的输入里还有随布局
    //   即时变化的量（`visibleItemsByIndex[...].offset` 在 `scrollToItem` 那一帧整体换掉），
    //   任何一处输入阶跃都会直接变成半径阶跃。这里对最终半径再叠一段短低通消除残差。
    //   取 160ms：短到不拖慢正常换行手感，长到足以抹掉单帧台阶（<100ms 仍读得出跳）。
    const val LYRIC_BLUR_SMOOTH_MS = 160

    const val CASCADE_DELAY_MS = 14f
    const val CASCADE_DELAY_INCREASE_MS = 2.5f
    const val CASCADE_FOLLOWING_DELAY_MS = 22f
    const val CASCADE_CATCH_UP_RATIO = 0.97f
    const val CASCADE_CHASE_SPEED_GRADIENT = 0.82f
    const val CASCADE_DURATION_MS = 560f
    const val CASCADE_SNAP_THRESHOLD_MS = 180f
    const val CASCADE_BOUNCE = 0.14f
    const val CASCADE_BOUNCE_GRADIENT = 0.60f
    const val SCALE_BOUNCE = 0.16f
    const val SCALE_BOUNCE_DURATION_MS = 420

    const val GLOW_INTENSITY = 1f
    const val LONG_TONE_THRESHOLD_MS = 950f
    const val LONG_TONE_MAX_SCALE = 1.0625f
    const val GLOW_TAIL_MS = 550f
    const val HIGHLIGHT_GRADIENT_WIDTH = 0.7f
    const val HIGHLIGHT_GRADIENT_REDUCTION = 0.65f
    const val UNPLAYED_OPACITY = 0.3f
    const val UNPLAYED_BLUR_LEAD_MS = 2400f
    const val MIN_UNPLAYED_BLUR_FRACTION = 0.12f
    const val LIFT_CONTINUATION_MS = 320f
    // ★ 2026-11-03（第 33 轮）：曾有的 `REVEAL_BLUR_MAX_DP`(8f) / `REVEAL_BLUR_FRACTION`(0.72f)
    //   —— 整块歌词层随 reveal 收敛的高斯模糊 —— 已删除。真机录屏逐帧取证：它把**当前
    //   正在播放的行**也糊掉了（稳态里该行是清晰的），观感是「糊一大片→慢慢对焦」像
    //   加载。模糊深度现在只由逐行模糊模型决定（未播放行的模糊本身就是朦胧感），
    //   reveal 只做淡入 + 上浮；焦点模型在首次定位时（alpha=0 不可见）瞬时建立到稳态。
}

internal data class LyricsPanelPlaybackInitialization(
    val positionMs: Long,
    val holdAtTrackStart: Boolean,
    val resetListToStart: Boolean,
)

internal fun lyricsPanelPlaybackInitialization(
    isFirstComposition: Boolean,
    mediaIdChanged: Boolean,
    reportedPositionMs: Long,
): LyricsPanelPlaybackInitialization = if (!isFirstComposition && mediaIdChanged) {
    LyricsPanelPlaybackInitialization(
        positionMs = 0L,
        holdAtTrackStart = true,
        resetListToStart = true,
    )
} else {
    LyricsPanelPlaybackInitialization(
        positionMs = reportedPositionMs,
        holdAtTrackStart = false,
        resetListToStart = false,
    )
}

internal fun initialLyricsHighlightPositionMs(positionMs: Long, advanceMs: Long): Long =
    positionMs + advanceMs

/**
 * 歌词**逐行**自带的水平内边距（左右同值）。
 *
 * ⚠ 这不是"左边距"调参入口，而是行自身的呼吸位。横屏要求「除底部三键外的部件左缘与三键
 * 里最左那颗按钮的左缘齐平」时，调用点用它换算补差值（见 `MeloXIOSNowPlayingScene` 的
 * `MeloXLandscapeLyricsStartInset`），所以**别在行上写死 8.dp**。
 */
internal val MeloXLyricLineHorizontalPadding = 8.dp

/**
 * 横屏歌词字号系数（唯一调参入口）。横屏可视高度只有竖屏的一半左右，同一字号（34sp ×
 * 用户 `lyricFontScale`）显得过大、一屏放不下几行 ⇒ 横屏按此系数整体等比缩小。
 * ⚠ 只影响横屏歌词面板，**不动**用户设置里的全局字号。
 */
internal const val MeloXLandscapeLyricFontMultiplier = 0.85f

/**
 * 横屏歌词面板的底部留白。**已弃用（2026-09-30），调用点已移除，保留仅供历史参考。**
 *
 * 硬让位是个没有正确解的两难：让到 32dp 仍会透字（三键顶沿 = 右列底 − 32dp）、
 * 让到 52dp 又会让底部显得很空。现改由 [MeloXLandscapeLyricsFadeBottom] 的**渐隐**承担 ——
 * 三键下方的行是"淡出到透明"而不是"被切掉"。
 * 历史：50dp → 20dp（「歌词下区域离底部三组件还有很多距离」）→ 32dp（消除歌词透字）→ 弃用。
 */
internal val MeloXLandscapeLyricsBottomPadding = 32.dp

/**
 * 横屏歌词「上下边缘渐隐」的高度（Apple Music 的 container edge fade，唯一调参入口）。
 *
 * 做法来源：Apple Music 处理歌词与上下 UI 衔接**不是**给浮层加底板、也不是给歌词留一条
 * 与 UI 等高的空 padding，而是让歌词**铺满**可用区、由自身在边界处淡出到透明（取代硬裁）。
 * 证据见 `MEMORY.md`「歌词 ↔ 上下 UI 衔接的上游权威做法」。
 *
 * - [MeloXLandscapeLyricsFadeTop] = 64dp，正好等于横屏曲名栏 `LandscapeSongHeader` 的高度
 *   ⇒ 歌词在「栏底」处才完全显形，滚到栏后的行自然消隐。
 * - [MeloXLandscapeLyricsFadeBottom] = 56dp，让开底部三键浮层（行高 42dp + `offset(y=10dp)`
 *   + 余量）⇒ 末行不再被三键压住，也不需要 `MeloXLandscapeLyricsBottomPadding` 的硬让位。
 */
internal val MeloXLandscapeLyricsFadeTop = 64.dp
internal val MeloXLandscapeLyricsFadeBottom = 56.dp

/**
 * 横屏歌词「边缘渐隐带」内的**最大模糊半径**（dp，唯一调参入口；0dp = 只渐隐不模糊）。
 *
 * 为什么不能靠已有的按行距衰减的模糊（[sourceDistanceBlurRadius]）来出这个效果：
 * 那条是按「行中点到焦点行的行距」算的，而横屏焦点在 0.30 ⇒ 视口**最上沿**距焦点
 * 只有 `306dp × 0.30 ≈ 92dp ÷ 34.7dp ≈ 0.8` 个行距，代入即 `0`（要超过 1.35 个行距才起糊）。
 * 也就是「焦点偏上」时按行距算的模糊**够不到边缘**，必须在边缘单独给。
 *
 * 本项按**行中点到视口边缘的像素距离**线性铺开（`smootherStep` 缓入，边沿更糊）：
 * 内沿 = 0、屏幕边 = 本值。几何上是行自身 y 的连续函数 ⇒ 滚动时逐帧连续变化，
 * **没有**「行滚进某区间突然变糊」的跳变 —— 那正是早先固定 y 区间硬模糊被否决的原因，
 * 这里不是那个做法。
 *
 * 只在渐隐带存在时生效（`edgeFadeTop/Bottom > 0`，即仅横屏）⇒ 竖屏逐帧不变。
 */
internal val MeloXLandscapeLyricsEdgeBlurMax = 6.dp

/**
 * 竖屏歌词「底部渐隐带」高度（唯一调参入口，配 `edgeFadeBottomTerminal` 一起用）。
 *
 * 背景：竖屏歌词面板是**铺满**到 Column 底部的（只有 `padding(top = 88.dp)`、**没有底部
 * 让位**），底下那一截被底部控件条盖住。控件条只有 `meloXBackdropBlur`（`surfaceColor`
 * 仅 `Black 8%` + 24dp 模糊）—— 对**白色高亮歌词**遮挡不足，会"幽灵般"透出来。真机
 * 截图实证：`0:00  [标准]  -0:00` 那一带能看到下层 `(4665,777)` 的虚影、三控件下方还有
 * 一整行。用户描述为「本该被下半操作区遮挡的歌词出现了」。
 *
 * 做法同横屏（Apple 的 container edge fade）：不加强遮挡、也不给歌词留空让位，而是让
 * 歌词**在控件条区域内本身就淡到透明**。区别在于竖屏控件条高 279dp（占视口 ~45%），
 * 远大于横屏的 56dp，而纵向渐隐是线性 ⇒ 光靠加宽渐隐带没用（控件条落在带**起点**、
 * alpha 仍是 1）。所以必须把**归零点**上移到控件条顶沿（见 `bottomTerminalPx`），
 * 本常量只负责"归零点之上多宽开始变淡"。
 *
 * ⚠⚠ 归零点**必须跟随控件条显隐**（见 `MeloXIOSNowPlayingScene` 竖屏调用点的
 * `lyricsFadeTerminal`）：控件条 3 秒无操作会自动隐藏，若归零点恒钉在顶沿，隐藏后
 * 下方 279dp（占视口 ~40%）会变成"既没控件、也没歌词"的空白 —— 用户报的
 * 「控件隐藏了，下面的歌词不出来，白白浪费下面的空间」。
 */
internal val MeloXPortraitLyricsFadeBottom = 64.dp

/**
 * 竖屏歌词「顶部渐隐带」高度（唯一调参入口，2026-10-18 加）。
 *
 * 背景：竖屏歌词面板是从 `padding(top = 88.dp)` 开始的（曲名栏下方），面板自身
 * `clipToBounds()` ⇒ 滚到最上面的那一行是被**视口顶沿一刀切断**的（用户报的
 * 「竖屏歌词页最上面的歌词也有一种硬切的感觉」）。以前只给了底部渐隐，顶部没有。
 *
 * 做法与底部/横屏完全同一套（Apple 的 container edge fade）：行在抵达顶沿之前
 * 就淡成透明，没有断面。取 48dp ≈ 竖屏一行歌词的高度，所以"半行"会自然溶解，
 * 又不会把第二行也吃掉。`edgeBlurMax = 0.dp` ⇒ 竖屏只要渐隐、不要边缘模糊。
 */
internal val MeloXPortraitLyricsFadeTop = 48.dp

/**
 * 横屏歌词的焦点行锚点。竖屏用户设置值 `lyricFocusPosition` 默认 0.25，但横屏右列只有
 * ~306dp 高、且顶部有 64dp 曲名栏 ⇒ 0.25 会让当前行（行顶）压到栏底。抬到 0.30 让当前行
 * 落在栏下方 ~11dp 处，同时仍比原来 `padding(top=48dp)` 的落点更高（消除栏下空白）。
 */
internal const val MeloXLandscapeLyricsFocusPosition = 0.30f

/**
 * 拖动进度条（scrub）时向上层（歌词面板）广播「预览位置」，让歌词在对应进度跟随滚动
 * （Apple Music 原版行为：拖拽滑块时歌词实时滚动到该时间点对应的那一行）。
 *
 * 仅在拖动期间持有非 null 值；松手后回到 null，歌词恢复正常播放跟随。
 * 由 `MeloXNowPlayingCoreControls` 写入、由 `MeloXAppleMusicLyricsPanel` 读取。
 */
internal class MeloXScrubPreviewState {
    var previewPositionMs by mutableStateOf<Long?>(null)
        private set

    // 最后一次更新的时间。用来兜底「手势被系统取消 ⇒ Slider 不回调
    // `onValueChangeFinished`」的极端情况：若一直卡在非 null，静置自动隐藏会被
    // 永远挂起（控件条再也收不起来）。
    private var lastUpdateElapsedMs = 0L

    fun update(positionMs: Long) {
        previewPositionMs = positionMs
        lastUpdateElapsedMs = android.os.SystemClock.elapsedRealtime()
    }

    fun clear() {
        previewPositionMs = null
        lastUpdateElapsedMs = 0L
    }

    /** 拖动位置超过 [timeoutMs] 没有新事件 ⇒ 视为手势已被取消。 */
    fun isStale(timeoutMs: Long): Boolean = previewPositionMs != null &&
        android.os.SystemClock.elapsedRealtime() - lastUpdateElapsedMs > timeoutMs
}

/**
 * 「永远不在开闭转场中」的占位 State。
 *
 * `shellTransitioning` 的默认值用它而不是 `mutableStateOf(false)`：默认参数在**每次**
 * 调用点求值，`mutableStateOf` 会在每次重组时新建一个可观察对象；这里用同一个常量，
 * 不分配、不产生任何快照订阅。给不关心转场的调用点（Classic 外壳、天际等）使用。
 */
internal val MeloXLyricsNeverTransitioning: State<Boolean> = mutableStateOf(false)

@Composable
fun MeloXIOSLyricsPanel(
    state: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
    isInterfaceHidden: Boolean = false,
    onInterfaceInteraction: () -> Unit = {},
    onInterfaceVisibilityChange: (Boolean) -> Unit = {},
    allowAutomaticSkyline: Boolean = true,
    active: Boolean = true,
    // ★ 横屏专用字号系数（默认 1f = 不动）。横屏可视高度只有竖屏的一半，同一字号显得过大
    //   ⇒ 横屏传 <1 缩小。**只乘在读取 `lyricFontScale` 处**，不改用户设置里的全局字号。
    fontScaleMultiplier: Float = 1f,
    // ★ 横屏专用「上下边缘渐隐」高度（默认 0dp = 关闭 ⇒ 竖屏行为完全不变）。
    //   见 `MeloXLandscapeLyricsFadeTop` 的注释：取代「给浮层加底板 / 给歌词留空让位」。
    edgeFadeTop: Dp = 0.dp,
    edgeFadeBottom: Dp = 0.dp,
    // ★ 横屏专用焦点行锚点（默认 null = 沿用用户设置 `lyricFocusPosition`）。
    focusPositionOverride: Float? = null,
    // ★ 底部渐隐的**归零点**距视口底的距离（默认 0dp = 归零点即视口底，即旧行为）。
    //   竖屏传「底部控件条高度」⇒ 控件条区域内歌词恒透明，遮挡不再依赖那层模糊底板。
    edgeFadeBottomTerminal: Dp = 0.dp,
    // ★ 渐隐带内的最大边缘模糊（dp）。默认沿用横屏值；竖屏传 0dp（只要渐隐、不要模糊）。
    edgeBlurMax: Dp = MeloXLandscapeLyricsEdgeBlurMax,
    controlsRevealHeight: Dp = 0.dp,
    // ★ 拖动进度条时的「预览位置」（ms）。非 null 时歌词滚动到该时间点对应的行
    //   （Apple Music 原版行为）；松手后为 null，歌词恢复按播放进度跟随。
    scrubPositionMs: Long? = null,
    // ★★ 2026-10-23：开 / 闭播放页（迷你栏 ↔ 全屏）的 morph 是否进行中。
    //
    //   为什么需要它（用户：「迷你栏打开播放页还是不够丝滑，尤其停在歌词页时」）：
    //   morph 期间外壳是**整页缩放**，而歌词每一行都挂着 `Modifier.blur` = 一个
    //   `RenderEffect`（整行大小的离屏纹理 + 高斯），缩放导致每帧重建。此时若**又恰好
    //   有一句歌词要滚动**，LazyColumn 的重新测量/布局会再叠上去 ⇒ 那一下就是顿挫。
    //
    //   ⇒ 本面板只在运动期间**推迟自动跟随滚动**（见下方 `LaunchedEffect` 里的等待）。
    //
    //   🔴 **不降任何视觉档**（不降模糊、不降帧率、不改字号）。2026-10-23 用户明令：
    //     不许出现肉眼可见的降级，尤其不许「动画结束的瞬间画面闪一下」——
    //     而「运动期间压低、落定弹回」正是那个形状。省成本只能省没画出来的东西。
    //
    //   ⚠ 传 **State 本体**：中间层（Scene）只转发、不读值。
    //   默认恒 false ⇒ 其它调用点（天际 / Eva / TextPV / Classic 外壳）行为完全不变。
    shellTransitioning: State<Boolean> = MeloXLyricsNeverTransitioning,
) {
    val revealHeightPx = with(LocalDensity.current) { controlsRevealHeight.toPx() }
    val revealInteraction = rememberUpdatedState(onInterfaceInteraction)
    val lyricsModifier = modifier.pointerInput(isInterfaceHidden, revealHeightPx) {
        if (!isInterfaceHidden || revealHeightPx <= 0f) return@pointerInput
        // Observe at Initial so a recognised tap is consumed before a lyric's
        // combinedClickable sees its up event. Down/move stay unconsumed: the
        // lyrics can still be scrolled from this same bottom region.
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val inControls = down.position.y >= size.height - revealHeightPx
                var moved = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (event.changes.size > 1 || change == null ||
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                    if (event.changes.all { !it.pressed }) {
                        if (inControls && !moved && change != null && !change.isConsumed) {
                            event.changes.forEach { it.consume() }
                            revealInteraction.value.invoke()
                        }
                        break
                    }
                }
            }
        }
    }
    val configuration = LocalConfiguration.current
    if (allowAutomaticSkyline && configuration.screenWidthDp > configuration.screenHeightDp && MeloXSettingsRuntime.skylineEnabled) {
        MeloXSkylineLyricsPanel(state, lyricsModifier, onInterfaceInteraction, active)
        return
    }
    when (MeloXSettingsRuntime.lyricsStyle) {
        MeloXLyricsStyle.AppleMusic -> MeloXAppleMusicLyricsPanel(
            state,
            lyricsModifier,
            isInterfaceHidden,
            onInterfaceInteraction,
            onInterfaceVisibilityChange,
            active,
            fontScaleMultiplier,
            edgeFadeTop,
            edgeFadeBottom,
            focusPositionOverride,
            edgeFadeBottomTerminal,
            edgeBlurMax,
            scrubPositionMs,
            shellTransitioning,
        )
        MeloXLyricsStyle.Eva -> MeloXEvaLyricsPanel(state, lyricsModifier, onInterfaceInteraction, active)
        MeloXLyricsStyle.TextPV -> MeloXTextPVLyricsPanel(state, lyricsModifier, onInterfaceInteraction, active)
    }
}

/**
 * ★★ 「旁听式」点按识别：**不消费任何 pointer 事件**。
 *
 * 控件隐藏时歌词面板底部会挂一层透明的「点按唤出控件」捕获层。它早先用的是
 * `clickable`，而 `clickable` 会消费 pointer down ⇒ 底下 LazyColumn 的
 * scrollable 再也拿不到 down ⇒ **控件一隐藏，底部那片歌词就完全滑不动**
 * （用户报的 bug）。
 *
 * 本实现只监听、不消费：手指位移超过 `touchSlop` 即判为滑动，事件原样留给下面
 * 的歌词列表去滚动；只有「按下后未越过 slop 就抬起」才触发 [onTap]。
 *
 * @param onTap 识别为点按时回调（唤出控件条）。
 */
internal suspend fun PointerInputScope.detectPassThroughTap(onTap: () -> Unit) {
    awaitPointerEventScope {
        while (true) {
            // requireUnconsumed=false：即使下游已消费也照样观察，避免漏判。
            val down = awaitFirstDown(requireUnconsumed = false)
            val slop = viewConfiguration.touchSlop
            var moved = false
            var released = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val allUp = event.changes.all { !it.pressed }
                if (!moved && !released) {
                    val change: PointerInputChange? =
                        event.changes.firstOrNull { it.id == down.id }
                    if (change == null) {
                        released = true
                    } else if (!change.pressed) {
                        released = true
                    } else if ((change.position - down.position).getDistance() > slop) {
                        // 判定为滑动：本轮不再触发点按，事件全程留给歌词列表。
                        moved = true
                    }
                }
                if (allUp) break
            }
            if (released && !moved) onTap()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MeloXAppleMusicLyricsPanel(
    state: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
    isInterfaceHidden: Boolean = false,
    onInterfaceInteraction: () -> Unit = {},
    onInterfaceVisibilityChange: (Boolean) -> Unit = {},
    active: Boolean = true,
    fontScaleMultiplier: Float = 1f,
    edgeFadeTop: Dp = 0.dp,
    edgeFadeBottom: Dp = 0.dp,
    focusPositionOverride: Float? = null,
    edgeFadeBottomTerminal: Dp = 0.dp,
    edgeBlurMax: Dp = MeloXLandscapeLyricsEdgeBlurMax,
    // ★ 拖动进度条时的「预览位置」（ms）。见 `MeloXIOSLyricsPanel` 同名参数。
    scrubPositionMs: Long? = null,
    // ★ 开 / 闭播放页的 morph 是否进行中。见 `MeloXIOSLyricsPanel` 同名参数的完整说明。
    shellTransitioning: State<Boolean> = MeloXLyricsNeverTransitioning,
) {
    // 在循环里读取最新值（LaunchedEffect 不会因它变化而重起），用 state holder 承载。
    val scrubPositionMsState = rememberUpdatedState(scrubPositionMs)
    // ★ 拖动进度条期间的「扁平预览」进度（2026-10-19 用户要求）：
    //   1 ⇒ 取消未播放歌词的模糊 / 缩放层级，整篇同层清晰、当前时间点那一行高亮；
    //   0 ⇒ 完全回到正常的「按离焦点行距离衰减」的模糊 + 层级。
    //   用动画而不是硬切：拖动开始 / 松手时 180ms 平滑过渡，松手后无缝接回原效果。
    val scrubFlat by animateFloatAsState(
        targetValue = if (scrubPositionMs != null) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "lyricScrubFlat",
    )
    val context = LocalContext.current
    val appContext = context.applicationContext
    val haptics = LocalHapticFeedback.current
    val activeState = rememberUpdatedState(active)
    // Keep four future lines composed below the viewport. Their independent
    // cascade animations can then run before clipping reveals them.
    val listState = rememberLazyListState(
        cacheWindow = LazyLayoutCacheWindow(
            ahead = 720.dp,
            behind = 240.dp,
        ),
    )
    val density = LocalDensity.current
    val mediaId = state.mediaId

    val automaticLyricSelectionEnabled = MeloXSettingsRuntime.automaticLyricSelectionEnabled
    var lyrics by remember(mediaId, automaticLyricSelectionEnabled) { mutableStateOf<LyricsDocument?>(null) }
    var isLoading by remember(mediaId) { mutableStateOf(false) }
    var errorMessage by remember(mediaId) { mutableStateOf<String?>(null) }
    var shareInitialIndex by remember(mediaId) { mutableStateOf<Int?>(null) }

    var anchorPositionMs by remember(mediaId) { mutableLongStateOf(state.positionMs) }
    var anchorRealtimeMs by remember(mediaId) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val renderedPositionState = remember(mediaId) { mutableLongStateOf(state.positionMs) }
    var holdTrackAtStart by remember(mediaId) { mutableStateOf(false) }
    var initializedMediaId by remember { mutableStateOf(mediaId) }
    var hasInitializedTrack by remember { mutableStateOf(false) }

    // A Bluetooth/headset skip can deliver the first UI state for the new item
    // before its position callback arrives.  Do not let the previous track's
    // progress keep the new lyric document focused in the middle of the list.
    // A newly composed panel, however, is re-entering the current track and must
    // retain its reported position.
    LaunchedEffect(mediaId) {
        val initialization = lyricsPanelPlaybackInitialization(
            isFirstComposition = !hasInitializedTrack,
            mediaIdChanged = hasInitializedTrack && initializedMediaId != mediaId,
            reportedPositionMs = state.positionMs,
        )
        initializedMediaId = mediaId
        hasInitializedTrack = true
        anchorPositionMs = initialization.positionMs
        anchorRealtimeMs = SystemClock.elapsedRealtime()
        renderedPositionState.longValue = initialization.positionMs
        holdTrackAtStart = initialization.holdAtTrackStart
        if (initialization.resetListToStart) listState.scrollToItem(0)
    }

    LaunchedEffect(state.positionMs, state.isPlaying, mediaId) {
        // ⚠ hold 期间也照样收位置回调：只冻「显示位」（renderedPositionState 归上面的时钟
        // 循环管，它 hold 期会跳过刷新），**不冻锚点**。否则歌词慢加载（红尘客栈实测
        // 13.5s）期间播放/暂停的位置全被吞掉，歌词晚到时锚点还停在初始值 —— 暂停后
        // 没有新回调进来，高亮位置会一直错着（2026-10-24 实测缺陷）。
        anchorPositionMs = state.positionMs
        anchorRealtimeMs = SystemClock.elapsedRealtime()
        if (!holdTrackAtStart) renderedPositionState.longValue = state.positionMs
    }

    val lyricRequestKey = lyricUiRequestKey(state, automaticLyricSelectionEnabled)
    val currentLyricRequestKey = rememberUpdatedState(lyricRequestKey)
    LaunchedEffect(lyricRequestKey) {
        if (mediaId.isNullOrBlank()) return@LaunchedEffect
        isLoading = true
        errorMessage = null
        runCatching {
            MeloXProviderLyricsLoader.load(appContext, state) { upgraded ->
                // Authored-source swaps keep their early window. Local model
                // timing can finish later, but must still belong to this track.
                val livePositionMs = anchorPositionMs +
                    (SystemClock.elapsedRealtime() - anchorRealtimeMs)
                if (state.mediaId == mediaId && currentLyricRequestKey.value == lyricRequestKey &&
                    (upgraded.autoSyncModelKey != null || livePositionMs <= MeloXProviderLyricsLoader.AmllUpgradeWindowMs)
                ) {
                    lyrics = upgraded
                    if (upgraded.autoSyncModelKey != null) {
                        android.util.Log.i(
                            "MeloXLyricsAuto",
                            "AutoSync displayed track=$mediaId wordLines=${upgraded.lines.count { it.syllables.isNotEmpty() }}",
                        )
                    }
                }
            }
        }
            .onSuccess { loaded ->
                lyrics = loaded
                // 诊断（2026-10-24 红尘客栈回归）：歌词时间轴跨度 vs 音频时长。选源选到带
                // 片头垫层的剪辑（MV = 母带 + 1.9s 数字静音垫）时，同一份逐字歌词会整体
                // 早 1.9s —— 跨度差会露出垫层长度。真机上用这条日志确认选源修复是否生效：
                // 修复后 delta 应稳定等于「末句之后尾奏的长度」，不再随上传版本多出 1~2s。
                val lyricEndMs = loaded.lines.maxOfOrNull { line ->
                    maxOf(
                        line.timeMs + (line.durationMs ?: 0L),
                        line.syllables.maxOfOrNull { it.endTimeMs } ?: 0L,
                    )
                } ?: 0L
                if (lyricEndMs > 0L && state.durationMs > 0L) {
                    android.util.Log.i(
                        "MeloXLyricsSpan",
                        "track=$mediaId lyricEnd=${lyricEndMs}ms audioDuration=${state.durationMs}ms " +
                            "delta=${state.durationMs - lyricEndMs}ms lines=${loaded.lines.size}",
                    )
                }
            }
            .onFailure { errorMessage = it.message ?: "歌词加载失败" }
        // A newly selected item always enters from its lyric start anchor.
        // Subsequent seeks keep using the normal position callback above.
        holdTrackAtStart = false
        isLoading = false
    }

    val document = lyrics
    val pseudoTimingEnabled = MeloXSettingsRuntime.lyricPseudoTimingEnabled
    val qqBracketBackingEnabled = MeloXSettingsRuntime.qqBracketBackingVocals
    // Downloaded lyrics are re-parsed as LyricSource.Local and online QQ
    // documents can be replaced by AMLL/NetEase, so the provider that authored
    // the text is not reliably recoverable here. The line-level pattern (the
    // whole line is one bracket pair) is the actual contract; the switch is
    // opt-in and only rewrites those lines.
    val renderedDocument = remember(document, pseudoTimingEnabled, qqBracketBackingEnabled) {
        val withBackingVocals = if (qqBracketBackingEnabled) {
            document?.withQqBracketBackingVocals()
        } else {
            document
        }
        if (pseudoTimingEnabled) withBackingVocals?.withPseudoTiming() else withBackingVocals
    }
    val lines = renderedDocument?.lines.orEmpty()
    if (renderedDocument?.isUnsynchronized == true) {
        MeloXUnsynchronizedLyrics(lines, modifier)
        return
    }
    val hasSyllableSync = remember(renderedDocument) { lines.any { it.syllables.isNotEmpty() } }
    val bilibiliOffsetMs = rememberBilibiliLyricOffset(mediaId)
    val lyricAdvanceMs = effectiveBilibiliLyricAdvance(MeloXSettingsRuntime.lyricAdvanceMs, bilibiliOffsetMs)
    val isBilibiliLyrics = remember(mediaId) { isBilibiliMediaId(mediaId) }
    val usesWordByWordPresentation = hasSyllableSync && MeloXSettingsRuntime.lyricWordByWordEnabled
    val timedAdvanceMs = if (isBilibiliLyrics || MeloXSettingsRuntime.lyricAdvanceAppliesToWordByWord) {
        lyricAdvanceMs
    } else 0L
    val lineAdvanceMs = if (usesWordByWordPresentation) timedAdvanceMs else lyricAdvanceMs
    var highlightedIndex by remember(document) {
        mutableIntStateOf(
            renderedDocument?.highlightedIndex(
                initialLyricsHighlightPositionMs(renderedPositionState.longValue, lineAdvanceMs),
                LyricHighlightStrategy.FirstSyllableOrLineStart,
            ) ?: -1,
        )
    }
    var colorHighlightedIndex by remember(document) { mutableIntStateOf(highlightedIndex) }
    var activeTimedLineIndexes by remember(document) { mutableStateOf(emptySet<Int>()) }
    val playbackTimeProvider = remember(mediaId, timedAdvanceMs) {
        { renderedPositionState.longValue + timedAdvanceMs }
    }

    // Update the line index only when it actually changes. The per-frame time is
    // read later from Canvas, so the 60 Hz clock invalidates drawing rather than
    // recomposing and relaying out the complete lyric list.
    val renderingQuality = MeloXSettingsRuntime.lyricRenderingQuality
    val refreshRate = when (renderingQuality) {
        MeloXLyricsRenderingQuality.Low -> min(MeloXSettingsRuntime.lyricRefreshRate, 30)
        MeloXLyricsRenderingQuality.Balanced -> min(MeloXSettingsRuntime.lyricRefreshRate, 60)
        // Native glyph drawing is CPU-bound; more than one update per display
        // frame adds work without producing a visible state. 60 Hz remains the
        // ceiling even when a migrated preference contains 90/120 Hz.
        MeloXLyricsRenderingQuality.High -> min(MeloXSettingsRuntime.lyricRefreshRate, 60)
    }
    val interludes = remember(lines) { sourceLyricInterludes(lines) }
    val interludeByLyricIndex = remember(interludes) { interludes.associateBy { it.followingLyricIndex } }
    var activeInterludeIndex by remember(document) { mutableIntStateOf(-1) }

    // High-frequency clock only for Canvas word animation. Line focus changes
    // are computed in a separate event-driven loop so compositional work only
    // happens at lyric boundaries.
    LaunchedEffect(state.isPlaying, mediaId, document, refreshRate, MeloXAppVisibility.isForeground) {
        var lastFrameNanos = 0L
        val minimumFrameNanos = 1_000_000_000L / refreshRate.coerceIn(30, 120)
        while (true) {
            if (!activeState.value || !MeloXAppVisibility.isForeground || holdTrackAtStart) {
                delay(200L)
                continue
            }
            if (state.isPlaying) {
                val frameNanos = withFrameNanos { it }
                if (lastFrameNanos != 0L && frameNanos - lastFrameNanos < minimumFrameNanos) continue
                lastFrameNanos = frameNanos
            }
            val scrub = scrubPositionMsState.value
            val position = if (scrub != null) {
                scrub
            } else if (state.isPlaying) {
                anchorPositionMs + (SystemClock.elapsedRealtime() - anchorRealtimeMs)
            } else {
                anchorPositionMs
            }
            renderedPositionState.longValue = position
            val nextInterlude = interludes.indexOfFirst { position >= it.startTimeMs && position < it.followingLyricTimeMs }
            if (nextInterlude != activeInterludeIndex) activeInterludeIndex = nextInterlude
            if (!state.isPlaying) delay(200L)
        }
    }

    val lineIndexSeekSignal = remember(mediaId) { Channel<Unit>(Channel.CONFLATED) }
    var seekGeneration by remember(mediaId) { mutableIntStateOf(0) }
    var previousSeekPositionMs by remember(mediaId) { mutableLongStateOf(state.positionMs) }
    var previousSeekRealtimeMs by remember(mediaId) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.positionMs) {
        if (state.positionMs != previousSeekPositionMs) {
            val expected = previousSeekPositionMs + if (state.isPlaying) {
                SystemClock.elapsedRealtime() - previousSeekRealtimeMs
            } else {
                0L
            }
            val driftMs = state.positionMs - expected
            if (kotlin.math.abs(driftMs) > 200L) {
                lineIndexSeekSignal.trySend(Unit)
            }
            // A real seek (progress bar, queue jump, previous-to-start) can be
            // several seconds away from the extrapolated position. Only then
            // re-anchor the focused lyric line and cancel lyric browsing, so a
            // buffering stall or a late position update does not yank the list.
            if (kotlin.math.abs(driftMs) > 1_000L) {
                seekGeneration++
            }
            previousSeekPositionMs = state.positionMs
            previousSeekRealtimeMs = SystemClock.elapsedRealtime()
        }
    }

    LaunchedEffect(state.isPlaying, mediaId, document, hasSyllableSync, lineAdvanceMs, MeloXAppVisibility.isForeground) {
        while (true) {
            if (!activeState.value || !MeloXAppVisibility.isForeground || holdTrackAtStart || renderedDocument == null) {
                delay(200L)
                continue
            }
            val scrub = scrubPositionMsState.value
            val position = if (scrub != null) {
                scrub
            } else if (state.isPlaying) {
                anchorPositionMs + (SystemClock.elapsedRealtime() - anchorRealtimeMs)
            } else {
                anchorPositionMs
            }
            val effectivePosition = position + lineAdvanceMs
            val activeInterlude = if (
                MeloXSettingsRuntime.lyricInterludeCountdownEnabled
            ) interludes.getOrNull(activeInterludeIndex) else null
            val nextIndex = if (
                MeloXSettingsRuntime.lyricInterludeCountdownEnabled &&
                activeInterlude != null &&
                position >= activeInterlude.startTimeMs &&
                position < activeInterlude.followingLyricTimeMs
            ) {
                activeInterlude.followingLyricIndex
            } else {
                renderedDocument.highlightedIndex(effectivePosition, LyricHighlightStrategy.FirstSyllableOrLineStart) ?: -1
            }
            if (nextIndex != highlightedIndex) highlightedIndex = nextIndex
            val nextColorIndex = renderedDocument.highlightedIndex(
                effectivePosition + MeloXSettingsRuntime.lyricFocusColorLeadMs,
                LyricHighlightStrategy.FirstSyllableOrLineStart,
            ) ?: -1
            if (nextColorIndex != colorHighlightedIndex) colorHighlightedIndex = nextColorIndex
            val timedPosition = position + timedAdvanceMs
            val nextTimedLines = LyricTimelineProcessor.activeTimedLineIndexes(renderedDocument, timedPosition)
            if (nextTimedLines != activeTimedLineIndexes) activeTimedLineIndexes = nextTimedLines

            if (scrub != null) {
                // 拖动预览：每帧刷新行索引，紧跟滑块（不依赖 seek 信号，避免滞后）。
                delay(16L)
            } else {
                val lineWait = LyricTimelineProcessor.nextEventTimeMs(renderedDocument, effectivePosition)
                    ?.minus(effectivePosition)
                val timedWait = LyricTimelineProcessor.nextEventTimeMs(renderedDocument, timedPosition)
                    ?.minus(timedPosition)
                val waitMs = listOfNotNull(lineWait, timedWait).minOrNull()?.coerceIn(16L, 500L) ?: 250L
                withTimeoutOrNull(waitMs) { lineIndexSeekSignal.receive() }
                if (!state.isPlaying) delay(200L)
            }
        }
    }

    val focusProgress = remember(document) {
        List(lines.size) { Animatable(0f) }
    }
    val scaleProgress = remember(document) {
        List(lines.size) { Animatable(0f) }
    }
    val cascadeLineProgress = remember(document) {
        List(lines.size) { Animatable(1f) }
    }
    val cascadeScrollProgress = remember(document) { Animatable(1f) }
    var cascadeDistancePx by remember(document) { mutableStateOf(0f) }
    var cascadeInitialOffsets by remember(document) {
        mutableStateOf<Map<Int, Float>>(emptyMap())
    }
    var cascadeDestinationOffsets by remember(document) {
        mutableStateOf<Map<Int, Float>>(emptyMap())
    }
    var isTransitioning by remember(document) { mutableStateOf(false) }
    var previousFocusForDetection by remember(document) { mutableIntStateOf(-1) }
    val rowHeightsPx = remember(
        document,
        MeloXSettingsRuntime.lyricFontScale,
        fontScaleMultiplier,
        MeloXSettingsRuntime.lyricSpacingScale,
        MeloXSettingsRuntime.showLyricTranslation,
        MeloXSettingsRuntime.showLyricRomanization,
    ) { mutableStateMapOf<Int, Int>() }
    var viewportHeightPx by remember(document) { mutableIntStateOf(0) }
    var visualFocusIndex by remember(document) { mutableIntStateOf(-1) }
    // ★★ 2026-11-03（第 31 轮）：**焦点模糊模型 ramp（0→1）**——修「模糊→淡模糊跳变」。
    //
    //   背景（用户报障原文）：「歌词显隐后默认全部模糊，然后又触发了首句候选后应该是
    //   淡模糊（准备播放到他了），这时候就会出现模糊向淡模糊转变（这不是问题），重点
    //   在于他会跳变，过渡没做好」。
    //
    //   根因：每行模糊 = `sourceDistanceBlurRadius(...)`，是**纯函数、逐帧直算、零插值**，
    //   而它的输入在两套模型之间硬切：
    //     · `distance`：焦点无效（`visualFocusIndex = -1`，此分支里列表被 `scrollToItem(0)`
    //       顶到最上面）⇒ 按「行中线到焦点锚点的**像素**距离」算 ⇒ 所有行都离锚点很远
    //       ⇒ 整屏最大模糊（= 用户说的「默认全部模糊」）；
    //       焦点一旦有效 ⇒ 换成「|行号 − 焦点行号| × 行高」。
    //     · `focusBlur`：相邻行额外叠加 2.4× / 0.7× intensity，**只在焦点有效后出现**
    //       （`preceding` / `following` 依赖 `visualFocusIndex`）。
    //   ⇒ 「首句候选确立」那一帧，距离公式整套换 + `focusBlur` 从 0 突然出现 ⇒ 整屏
    //     模糊半径集体阶跃，这就是那一下跳变。
    //
    //   修法：**不改任何一端的稳态取值**，只把两套模型按这个 ramp 线性混合 ——
    //   ramp=0 时与「焦点未定」逐像素等价，ramp=1 时与「焦点已定」逐像素等价，
    //   中间是连续插值 ⇒ 物理上不可能出现单帧阶跃。稳态 ramp 恒为 1 ⇒ 零额外成本。
    //   ⚠ 只混合「会跳的这两项」（distance / focusBlur），不碰颜色、不碰透明度、
    //     不削弱模糊强度（用户明令：不许靠降模糊或提不透明度掩盖）。
    val focusModelRamp = remember(document) { Animatable(0f) }
    LaunchedEffect(document, visualFocusIndex in lines.indices) {
        val targetRamp = if (visualFocusIndex in lines.indices) 1f else 0f
        if (MeloXSettingsRuntime.lyricReduceMotion) {
            focusModelRamp.snapTo(targetRamp)
        } else if (abs(focusModelRamp.value - targetRamp) > 0.0001f) {
            focusModelRamp.animateTo(
                targetValue = targetRamp,
                animationSpec = tween(
                    durationMillis = UpstreamLyrics.FOCUS_MODEL_RAMP_MS,
                    easing = SourceSmoothStepEasing,
                ),
            )
        }
    }
    var isBrowsingLyrics by remember(document) { mutableStateOf(false) }
    var playbackFocusGeneration by remember(document) { mutableIntStateOf(0) }
    var browseGeneration by remember(document) { mutableIntStateOf(0) }
    LaunchedEffect(seekGeneration) {
        if (seekGeneration > 0) {
            isBrowsingLyrics = false
            playbackFocusGeneration++
        }
    }
    var scrollHideDistancePx by remember(document) { mutableStateOf(0f) }
    val latestInterfaceHidden = rememberUpdatedState(isInterfaceHidden)
    val latestVisibilityCallback = rememberUpdatedState(onInterfaceVisibilityChange)
    val latestInteractionCallback = rememberUpdatedState(onInterfaceInteraction)
    // ★ 2026-10-15：初值改 `null` = "还没观测到过任何状态"，**跳过首次发射**。
    //   旧实现把初值写死 `true`，于是「true→false」这个判据在**零交互的挂载瞬间**就成立：
    //   歌词面板刚组合时列表必然停在 (index=0, offset=0)，`canScrollBackward` 一上来就是
    //   `false` ⇒ 凭空震一记 `LongPress`。用户长期把这记误触发当成了「打开播放页的反馈」
    //   （在旧口径下面板于转场开始即组合 ⇒ 它恰好落在点击瞬间）。
    //   ⚠ 这条误触发在 2026-10-15「常驻重层延后组合」后被动后移，暴露成了「打开完之后才有」；
    //     现在打开那记已由 `MeloXApp` 的 `playerOpenThump` 显式承担，这里必须去掉误触发，
    //     否则会在展开落定后多震一记。
    //   改成 `null` 后语义收窄为：**先观测到一次真实状态、之后才发生 true→false** 才震
    //   （播放自动滚到歌词末尾 / 用户拖到底等），挂载与换歌时的误触发随之消失。
    var lastCanScrollForward by remember(document) { mutableStateOf<Boolean?>(null) }
    var lastCanScrollBackward by remember(document) { mutableStateOf<Boolean?>(null) }
    var initialLyricsPositioned by remember(renderedDocument) { mutableStateOf(lines.isEmpty()) }
    // ★★ 2026-10-24（用户报障：「迷你栏直接进歌词页时，其他内容都显示好了、动画也结束了，
    //   歌词才突然出现，太突兀太生硬」）。
    //   原实现是 `alpha = if (initialLyricsPositioned) 1f else 0f` —— 一帧之内从 0 跳到 1，
    //   而且这个跳变发生在 morph **尾段结束之后**（`shellTransitioning` 要等
    //   `expansionProgress` 掉到 0.999 以下，弹簧尾巴很长）⇒ 观感是「页面先停住，然后歌词
    //   啪一下出现」。
    //   现在：定位完成的判据不变（必须先 `scrollToItem` 到当前行，否则会看到歌词从第一行
    //   「跳」到当前行），但**表现**换成一段缓动 —— 无回弹弹簧淡入
    //   （见 `REVEAL_STIFFNESS` / `REVEAL_DAMPING_RATIO`），弹簧天然缓入缓出无速度拐点 ⇒ 更丝滑；
    //   ★★ 2026-11-03（第 34 轮）：曾经的「+ 12dp 上浮」已删 —— 上浮比「可见」完成得晚，
    //   观感是「加载好了又整体上滑一下」（用户录屏逐帧 + 互相关取证，见 `REVEAL_RISE_DP`
    //   删除处的注释）。现在 reveal 只做淡入，第一帧即最终位置；
    //   并且首次定位不再吃满整个 morph 尾段（见 `INITIAL_REVEAL_POSITION_MS`），
    //   于是这段淡入正好压在页面收尾的运动里，页面落定与歌词浮现是同一段运动。
    //   ⚠ 只在绘制相位读 `lyricsReveal.value`（graphicsLayer）⇒ 不触发重组/布局。
    val lyricsReveal = remember(renderedDocument) {
        Animatable(if (lines.isEmpty()) 1f else 0f)
    }
    LaunchedEffect(renderedDocument, initialLyricsPositioned) {
        if (initialLyricsPositioned) {
            lyricsReveal.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = UpstreamLyrics.REVEAL_DAMPING_RATIO,
                    stiffness = UpstreamLyrics.REVEAL_STIFFNESS,
                ),
            )
        } else {
            // 换歌 / 重新定位时直接归零，不做「淡出」——旧内容本来就在同一帧被替换掉。
            lyricsReveal.snapTo(0f)
        }
    }
    LaunchedEffect(document) {
        snapshotFlow { listState.canScrollForward to listState.canScrollBackward }
            .collect { (canForward, canBackward) ->
                if (com.lladlam.melox.ui.settings.MeloXSettingsRuntime.hapticFeedbackEnabled) {
                    if (lastCanScrollForward == true && !canForward) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    if (lastCanScrollBackward == true && !canBackward) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
                lastCanScrollForward = canForward
                lastCanScrollBackward = canBackward
            }
    }

    // ★ 横屏系数只乘在这一处：下面所有字号 / 行高 / 注释尺寸都以这个局部变量为基准，
    //   所以一处即可整体等比缩小（竖屏 multiplier = 1f ⇒ 行为完全不变）。
    val lyricFontScale = MeloXSettingsRuntime.lyricFontScale * fontScaleMultiplier
    val lyricSpacingScale = MeloXSettingsRuntime.lyricSpacingScale
    val lineSpacingPx = with(density) { (UpstreamLyrics.LINE_SPACING_DP * lyricSpacingScale).dp.toPx() }
    val primaryHeightPx = with(density) { (UpstreamLyrics.LINE_HEIGHT_SP * lyricFontScale).sp.toPx() }
    val annotationFontPx = with(density) {
        max(UpstreamLyrics.FONT_SIZE_SP * lyricFontScale * MeloXSettingsRuntime.lyricRomanizationFontScale, 13f).sp.toPx()
    }
    val annotationSpacingPx = with(density) { UpstreamLyrics.ANNOTATION_SPACING_DP.dp.toPx() }

    fun estimatedHeight(index: Int): Float {
        rowHeightsPx[index]?.let { return it.toFloat() }
        val line = lines.getOrNull(index)
        var height = primaryHeightPx
        if (MeloXSettingsRuntime.showLyricRomanization && !line?.romanization.isNullOrBlank()) {
            height += annotationFontPx * 1.2f + annotationSpacingPx
        }
        if (MeloXSettingsRuntime.showLyricTranslation && !line?.translation.isNullOrBlank()) {
            height += annotationFontPx * 1.2f + annotationSpacingPx
        }
        return height
    }

    fun settledMovementOffset(index: Int, focusIndex: Int): Float {
        if (focusIndex !in lines.indices || index <= focusIndex) return 0f
        return max(
            estimatedHeight(focusIndex) * (MeloXSettingsRuntime.lyricFocusScale - 1f),
            0f,
        )
    }

    fun currentMovementOffset(index: Int): Float {
        val initial = cascadeInitialOffsets[index]
            ?: return settledMovementOffset(index, visualFocusIndex)
        val destination = cascadeDestinationOffsets[index] ?: 0f
        val scrollProgress = cascadeScrollProgress.value
        val lineProgress = cascadeLineProgress[index].value
        return initial + cascadeDistancePx * scrollProgress -
            (cascadeDistancePx + initial - destination) * lineProgress
    }

    suspend fun clearCascadePresentation(focusIndex: Int) {
        visualFocusIndex = focusIndex
        cascadeInitialOffsets = emptyMap()
        cascadeDestinationOffsets = emptyMap()
        cascadeDistancePx = 0f
        cascadeScrollProgress.snapTo(1f)
    }

    suspend fun handOffFocusColor(targetIndexes: Set<Int>) = coroutineScope {
        focusProgress.forEachIndexed { index, anim ->
            val target = if (index in targetIndexes) 1f else 0f
            if (abs(anim.value - target) > 0.0001f) {
                launch {
                    if (MeloXSettingsRuntime.lyricReduceMotion) anim.snapTo(target)
                    else anim.animateTo(
                        targetValue = target,
                        animationSpec = tween(
                            durationMillis = UpstreamLyrics.FOCUS_COLOR_DURATION_MS,
                            easing = SourceSmoothStepEasing,
                        ),
                    )
                }
            }
        }
    }

    suspend fun handOffFocusScale(previousIndex: Int, nextIndex: Int) = coroutineScope {
        if (MeloXSettingsRuntime.lyricReduceMotion) {
            scaleProgress.forEachIndexed { index, anim -> anim.snapTo(if (index == nextIndex) 1f else 0f) }
            return@coroutineScope
        }
        if (previousIndex in scaleProgress.indices && previousIndex != nextIndex
        ) {
            launch {
                scaleProgress[previousIndex].animateTo(
                    0f,
                    tween(
                        durationMillis = MeloXSettingsRuntime.lyricScaleBounceDurationMs,
                            easing = if (MeloXSettingsRuntime.lyricScaleBounceEnabled) {
                                SourceSpringEasing(MeloXSettingsRuntime.lyricScaleBounce)
                            } else SourceSmoothStepEasing,
                    ),
                )
            }
        }
        if (nextIndex in scaleProgress.indices) {
            launch {
                scaleProgress[nextIndex].animateTo(
                    1f,
                    tween(
                        durationMillis = MeloXSettingsRuntime.lyricScaleBounceDurationMs,
                        easing = if (MeloXSettingsRuntime.lyricScaleBounceEnabled) {
                            SourceSpringEasing(MeloXSettingsRuntime.lyricScaleBounce)
                        } else SourceSmoothStepEasing,
                    ),
                )
            }
        }
    }

    LaunchedEffect(colorHighlightedIndex, activeTimedLineIndexes, activeInterludeIndex, document) {
        if (activeInterludeIndex >= 0) {
            handOffFocusColor(emptySet())
        } else {
            val targets = activeTimedLineIndexes.ifEmpty {
                colorHighlightedIndex.takeIf(lines.indices::contains)?.let(::setOf).orEmpty()
            }
            handOffFocusColor(targets)
        }
    }

    // Keep neighboring lyric lines in place. The previous implementation
    // animated a completed line upward and faded it out at the next cue.

    val scrollHideThresholdPx = with(density) { MeloXSettingsRuntime.lyricScrollHideThresholdDp.dp.toPx() }
    val lyricInteractionConnection = remember(document, scrollHideThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (isTransitioning) return Offset.Zero
                val offsetDelta = -available.y
                if (kotlin.math.abs(offsetDelta) < 0.01f) return Offset.Zero

                isBrowsingLyrics = true
                browseGeneration += 1

                if (offsetDelta < 0f) {
                    scrollHideDistancePx = 0f
                    if (latestInterfaceHidden.value) {
                        latestVisibilityCallback.value.invoke(true)
                    } else {
                        latestInteractionCallback.value.invoke()
                    }
                } else if (!latestInterfaceHidden.value) {
                    latestInteractionCallback.value.invoke()
                    scrollHideDistancePx += offsetDelta
                    if (scrollHideDistancePx >= scrollHideThresholdPx) {
                        scrollHideDistancePx = 0f
                        latestVisibilityCallback.value.invoke(false)
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(browseGeneration, document) {
        if (browseGeneration <= 0) return@LaunchedEffect
        delay(MeloXSettingsRuntime.lyricFollowDelayMs.toLong())
        isBrowsingLyrics = false
        playbackFocusGeneration += 1
    }

    LaunchedEffect(isBrowsingLyrics, document) {
        if (isBrowsingLyrics) {
            clearCascadePresentation(visualFocusIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeightPx = it.height }
            // ★★ 「控件隐藏时点按唤出控件」挂在面板**根节点**（歌词 LazyColumn 的祖先）。
            //    必须放在这里而不是做成一层覆盖 Box：Compose 命中测试在同一层级里一旦
            //    命中某个带 pointerInput 的节点，就**停止测试它下方的兄弟节点** ⇒ 任何
            //    压在歌词列表之上的捕获层（无论是否消费事件、`clickable` 或 `pointerInput`）
            //    都会让被它盖住的那片歌词**完全滑不动**（真机复现：底部 279dp 内滑动
            //    变化像素 = 0，紧邻其上的 y=1800 同一手势变化 12 万像素）。
            //    祖先与后代属同一条命中路径，不构成遮挡；本回调**不消费任何事件**，
            //    纯滑动照旧交给列表滚动。控件已显示时不再重复唤出。
            .pointerInput(latestInterfaceHidden) {
                detectPassThroughTap {
                    if (latestInterfaceHidden.value) {
                        latestInteractionCallback.value.invoke()
                    }
                }
            },
    ) {
        // 横屏传 override（默认 null ⇒ 沿用用户设置，竖屏行为不变）。
        val focusPosition = focusPositionOverride ?: MeloXSettingsRuntime.lyricFocusPosition
        val topPaddingPx = viewportHeightPx * focusPosition
        val bottomPaddingPx = max(
            viewportHeightPx * (1f - focusPosition),
            with(density) { 40.dp.toPx() },
        )

        fun focusItemScrollOffset(index: Int): Int {
            if (index !in lines.indices || viewportHeightPx <= 0) return 0
            val viewportAnchor = viewportHeightPx * focusPosition
            val desiredItemTop = viewportAnchor - estimatedHeight(index) * focusPosition
            return -desiredItemTop.roundToInt()
        }

        val playbackFocusIndex = activeTimedLineIndexes.minOrNull() ?: highlightedIndex

        // ★ 拖动进度条（scrub）时：由专用 LaunchedEffect 即时把歌词滚动到预览位置对应的那一行
        //   （Apple Music 原版行为）。正常播放时本块不介入（scrubPositionMs == null）。
        LaunchedEffect(scrubPositionMs, viewportHeightPx, document, lines) {
            val scrub = scrubPositionMs ?: return@LaunchedEffect
            if (viewportHeightPx <= 0 || isBrowsingLyrics) return@LaunchedEffect
            val idx = renderedDocument?.highlightedIndex(
                scrub + lineAdvanceMs,
                LyricHighlightStrategy.FirstSyllableOrLineStart,
            ) ?: -1
            if (idx !in lines.indices) {
                listState.scrollToItem(0)
                return@LaunchedEffect
            }
            // 即时定位到该行的焦点锚点：跟随滑块手感，不做级联动画（拖动期间逐帧刷新）。
            listState.scrollToItem(idx + 1, focusItemScrollOffset(idx))
        }

        LaunchedEffect(
            playbackFocusIndex,
            playbackFocusGeneration,
            viewportHeightPx,
            isBrowsingLyrics,
            document,
            // 拖动期间让自动跟随让位给上面的 preview 滚动，避免两套逻辑抢着滚。
            scrubPositionMs,
        ) {
            // ★ 2026-10-23：开 / 闭播放页的 morph 期间**推迟**自动跟随滚动。
            //   滚动会让 LazyColumn 每帧重新测量 / 布局、每行重建离屏模糊层，而此刻整页
            //   还在被 scale ⇒ 两笔成本叠在同一帧，就是用户说的「停在歌词页时开闭最不丝滑」。
            //   ⚠ 这条为什么**不违反**「不许有肉眼可见降级」：
            //     它不改任何**像素**——不降模糊、不降帧率、不改字号/亮度。
            //     只是把「本来要滚的那一下」从运动中间挪到运动之后（≤400ms）。
            //     高亮行的点亮时刻由 `colorHighlightedIndex` / `activeTimedLineIndexes`
            //     独立驱动，**照常准点变色**，被推迟的只有列表位移。
            //   ⚠ `withTimeoutOrNull` 兜底：万一判据不回落，也不会让歌词永远不跟随。
            if (shellTransitioning.value) {
                // ★ 2026-10-24：**首次定位**不再让出 morph 时间 —— 它是一次瞬时
                //   `scrollToItem`（不是逐帧滚动），等满 `shellTransitioning` 的整个弹簧尾段
                //   （判据是 `expansionProgress < 0.999f`）正是用户看到的
                //   「动画都结束了歌词才出来」。真机逐帧实测见常量注释。
                //   之后的自动跟随仍按原口径等满（1.2s 兜底），避免滚动与 morph 抢帧。
                val settleBudgetMs = if (visualFocusIndex !in lines.indices) {
                    UpstreamLyrics.INITIAL_REVEAL_POSITION_MS
                } else {
                    1_200L
                }
                if (settleBudgetMs > 0L) {
                    withTimeoutOrNull(settleBudgetMs) {
                        while (shellTransitioning.value) withFrameNanos { }
                    }
                }
            }
            val sourceIndex = highlightedIndex
            val nextIndex = playbackFocusIndex
            if (viewportHeightPx <= 0 || isBrowsingLyrics || scrubPositionMs != null) {
                if (isBrowsingLyrics && sourceIndex in lines.indices) {
                    visualFocusIndex = sourceIndex
                }
                return@LaunchedEffect
            }

            if (nextIndex !in lines.indices) {
                listState.scrollToItem(0)
                initialLyricsPositioned = true
                return@LaunchedEffect
            }

            val previousIndex = visualFocusIndex
            val targetOffset = focusItemScrollOffset(nextIndex)

            // ★★ 2026-11-03（第 35 轮）：两种「首次聚焦」必须分开处理 ——
            //   ① 歌词尚未显示（打开播放页 / 换歌重新定位）：定位必须发生在淡入第一帧
            //      之前 ⇒ 保持瞬时 `scrollToItem`，否则会看到歌词从第一行「跳」到当前行。
            //   ② 歌词已经可见（典型：暂停在 0:00 打开播放页，首句随后才成为候选）：
            //      原来同样瞬时 `scrollToItem`，整屏从「列表顶部」硬跳到「焦点锚点」，
            //      差值 ≈ 行高 × focusPosition（真机十几 dp）—— 用户报的
            //      「第一句歌词还是会上移，其他时间段正常」正是这一下（v34 删掉的
            //      `REVEAL_RISE_DP` 只是另一半）。改为**不进本分支**，落进下方通用换行
            //      路径：与后续每次换行完全同一段级联平滑滚动 + `handOffFocusScale`
            //      焦点缩放交接；「无焦点模型 → 焦点模型」由 `focusModelRamp` 的
            //      LaunchedEffect 平滑爬 0→1（可见状态下禁止瞬时换模型，见其声明处推导）。
            //   `initialLyricsPositioned` 兜底：淡入开始即 true，但 reveal 值为 0 的那一帧
            //   仍按不可见处理（阈值 0.02 ≪ 最早可辨 alpha）。
            if (previousIndex !in lines.indices &&
                !(initialLyricsPositioned && lyricsReveal.value > 0.02f)
            ) {
                listState.scrollToItem(nextIndex + 1, targetOffset)
                // ★ 2026-11-03（第 31 轮）：首次确立焦点**不再 `snapTo` 焦点色**。
                //   原来这里把整屏 `focusProgress` 在一帧内翻过去：如果这一刻歌词已经可见
                //   （例如暂停在 0:00，点播放后首句刚成为候选），颜色/亮度就是一次硬跳。
                //   改为走与后续每次换行**同一段** `handOffFocusColor`（120ms smooth-step）
                //   ⇒ 连续收敛。模糊本身不靠它，由 `focusModelRamp` 负责。
                //   `scaleProgress` 仍保持瞬时：首次定位时行位置被 `scrollToItem` 同时
                //   改变，放大动画叠上去会像「文字在长大」，与后续换行手感不同。
                val initialTargets = activeTimedLineIndexes.ifEmpty {
                    colorHighlightedIndex.takeIf(lines.indices::contains)?.let(::setOf).orEmpty()
                }
                scaleProgress.forEachIndexed { index, anim ->
                    anim.snapTo(if (index == nextIndex) 1f else 0f)
                }
                clearCascadePresentation(nextIndex)
                // ★ 2026-11-03（第 33 轮）：此刻 `lyricsReveal` 仍为 0 —— 整块歌词**完全不可
                //   见**，所以「无焦点模型 → 焦点模型」的切换可以直接瞬时完成，不必走
                //   260ms ramp：把 ramp 钉到 1，reveal 淡入呈现的第一帧就是稳态画面
                //   （当前行清晰、未播放行模糊）。否则 ramp 会在可见状态下慢慢爬，
                //   当前行从重模糊逐渐「对焦」，观感像加载（真机录屏逐帧取证）。
                //   ramp 的 LaunchedEffect 见 `visualFocusIndex in lines.indices` 键：
                //   目标已是 1 ⇒ 自动 no-op，后续换行/浏览态切换不受影响。
                focusModelRamp.snapTo(1f)
                initialLyricsPositioned = true
                handOffFocusColor(initialTargets)
                return@LaunchedEffect
            }

            if (previousIndex == nextIndex) {
                if (playbackFocusGeneration > 0 && MeloXSettingsRuntime.lyricAutoFollowEnabled) {
                    listState.scrollToItem(nextIndex + 1, targetOffset)
                }
                return@LaunchedEffect
            }

            if (!MeloXSettingsRuntime.lyricAutoFollowEnabled) {
                handOffFocusScale(previousIndex, nextIndex)
                visualFocusIndex = nextIndex
                return@LaunchedEffect
            }

            if (MeloXSettingsRuntime.lyricReduceMotion) {
                clearCascadePresentation(nextIndex)
                handOffFocusScale(previousIndex, nextIndex)
                listState.scrollToItem(nextIndex + 1, targetOffset)
                return@LaunchedEffect
            }

            val baseDurationMs = sourceFocusAnimationDurationMs(nextIndex, lines)
            val skippedLineCount = (nextIndex - previousIndex).coerceAtLeast(1)
            // Overlapping cues keep the focus on the earliest line. When they
            // end together, the next cue is only one visual step away even if
            // its index skipped the lines that were already on screen.
            // ★ 2026-11-03（第 35 轮）：previousIndex = -1（首焦点经 v35 可见分支落进本
            //   路径）时 range 为 (-1 until nextIndex)，`lines[index]` 直接
            //   IndexOutOfBoundsException（真机 18:17 栈：Index -1 / length 28）。
            //   首焦点不存在「重叠 cue 判定」前提 ⇒ 置 false，走普通级联。
            val overlappingStep = previousIndex >= 0 && previousIndex < nextIndex &&
                (previousIndex until nextIndex).all { index ->
                    sourceLineActivationTimeMs(lines[index + 1]) -
                        sourceLineActivationTimeMs(lines[index]) <= 280L
                }
            val isAdjacentForward = previousIndex < nextIndex && (skippedLineCount == 1 || overlappingStep)
            val effectivePosition = renderedPositionState.longValue + lyricAdvanceMs
            val remainingMs = sourceRemainingFocusDurationMs(nextIndex, effectivePosition, lines)

            val fullCascadeMs = max(baseDurationMs.toFloat(), MeloXSettingsRuntime.lyricCascadeDurationMs)
            val availableMs = remainingMs?.coerceAtLeast(0f)
            val cascadeDurationMs = if (availableMs == null) {
                fullCascadeMs
            } else {
                if (availableMs < MeloXSettingsRuntime.lyricSnapThresholdMs) 0f
                else min(fullCascadeMs, availableMs)
            }

            val desiredTop = -targetOffset.toFloat()
            val targetItem = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == nextIndex + 1 }
            if (cascadeDurationMs <= 0f || targetItem == null) {
                clearCascadePresentation(nextIndex)
                coroutineScope {
                    launch { handOffFocusScale(previousIndex, nextIndex) }
                    launch {
                        if (cascadeDurationMs <= 0f) {
                            listState.scrollToItem(nextIndex + 1, targetOffset)
                        } else {
                            listState.animateScrollToItem(
                                nextIndex + 1,
                                targetOffset,
                            )
                        }
                    }
                }
                return@LaunchedEffect
            }

            if (!isAdjacentForward) {
                // Keep all intervening lyric rows visible; only move the list
                // to the new focus anchor when several cues were skipped.
                clearCascadePresentation(nextIndex)
                handOffFocusScale(previousIndex, nextIndex)
                listState.animateScrollToItem(nextIndex + 1, targetOffset)
                return@LaunchedEffect
            }

            val movementDistance = targetItem.offset - desiredTop
            if (abs(movementDistance) <= 0.5f) {
                clearCascadePresentation(nextIndex)
                handOffFocusScale(previousIndex, nextIndex)
                return@LaunchedEffect
            }

            val visibleLineIndexes = listState.layoutInfo.visibleItemsInfo
                .mapNotNull { (it.index - 1).takeIf(lines.indices::contains) }
            val firstVisible = visibleLineIndexes.minOrNull() ?: max(nextIndex - 1, 0)
            val lastVisible = visibleLineIndexes.maxOrNull() ?: nextIndex
            val firstMoving = min(firstVisible, max(nextIndex - 1, 0))
            // The cache window keeps these rows composed even though the
            // viewport clips them. Each retains its own delayed spring.
            val lastMoving = min(lastVisible + 4, lines.lastIndex)
            val movingIndexes = firstMoving..lastMoving
            val carriedOffsets = movingIndexes.associateWith(::currentMovementOffset)
            val destinations = movingIndexes.associateWith { settledMovementOffset(it, nextIndex) }

            cascadeDistancePx = movementDistance
            cascadeInitialOffsets = carriedOffsets
            cascadeDestinationOffsets = destinations
            cascadeScrollProgress.snapTo(0f)
            movingIndexes.forEach { cascadeLineProgress[it].snapTo(0f) }
            visualFocusIndex = nextIndex

            val firstChasing = max(nextIndex - 1, 0)
            val maximumChaseOrder = max(lastMoving - firstChasing, 0)
            val lineTimings = sourceCascadeLineTimings(
                maximumLineOrder = maximumChaseOrder,
                animationDurationMs = cascadeDurationMs,
            )
            val slowestDuration = lineTimings.first().durationMs

            coroutineScope {
                launch { handOffFocusScale(previousIndex, nextIndex) }
                launch {
                    var previousScrollProgress = 0f
                    listState.scroll {
                        cascadeScrollProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = cascadeDurationMs.roundToInt(),
                                easing = SourceSmoothStepEasing,
                            ),
                        ) {
                            val delta = (value - previousScrollProgress) * movementDistance
                            scrollBy(delta)
                            previousScrollProgress = value
                        }
                    }
                }

                movingIndexes.forEach { index ->
                    val movementOrder = max(index - nextIndex, 0)
                    val chaseOrder = (index - firstChasing).coerceAtLeast(0)
                    val movementTiming = lineTimings[min(movementOrder, lineTimings.lastIndex)]
                    val chaseTiming = lineTimings[min(chaseOrder, lineTimings.lastIndex)]
                    val duration = slowestDuration +
                        (chaseTiming.durationMs - slowestDuration) *
                        MeloXSettingsRuntime.lyricCascadeChaseSpeedGradient
                    val bounce = sourceCascadeBounce(chaseOrder, maximumChaseOrder)
                    launch {
                        if (movementTiming.delayMs > 0f) delay(movementTiming.delayMs.toLong())
                        cascadeLineProgress[index].animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = max(duration, 1f).roundToInt(),
                                easing = SourceSpringEasing(bounce),
                            ),
                        )
                    }
                }

            }
            clearCascadePresentation(nextIndex)
        }

        when {
            isLoading && document == null -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            errorMessage != null && document == null -> {
                Text(
                    text = errorMessage.orEmpty(),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = Color.White.copy(alpha = 0.52f),
                    fontSize = 15.sp,
                )
            }
            document != null && lines.isEmpty() -> {
                Text(
                    text = "暂无歌词",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 18.sp,
                )
            }
            document == null -> {
                Text(
                    text = "暂无歌词",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 18.sp,
                )
            }
            else -> {
                val focusAnchorY = viewportHeightPx * focusPosition
                val annotationHeightPx = annotationFontPx * 1.2f * 2f + annotationSpacingPx * 2f
                val lyricStridePx = max(primaryHeightPx + annotationHeightPx + lineSpacingPx, 1f)
                val layoutOverscanPx = (lyricStridePx * 4f).roundToInt()
                // Observing layoutInfo here during automatic playback scrolls
                // invalidated and recomposed every visible lyric item per
                // frame. Exact coordinates are only required while the user is
                // browsing; playback mode has a stable source-derived stride.
                val browsingVisibleItemsByIndex by remember(listState) {
                    derivedStateOf { listState.layoutInfo.visibleItemsInfo.associateBy { it.index } }
                }
                // ⚠ 只在「用户手动浏览歌词」时才订阅 `layoutInfo`（上面注释描述的优化，
                //   此前代码其实**无条件**读了它）：自动播放 / 拖动进度条时 `layoutInfo`
                //   同样每帧变化 ⇒ `associateBy` 每帧重建一个 Map，并让包含 LazyColumn
                //   的这一层每帧重组所有可见行。非浏览态走下面那条
                //   `focusAnchorY + (index - visualFocusIndex) * lyricStridePx` 的解析路径，
                //   不依赖可见项坐标，精度足够（这正是注释里说的「stable source-derived stride」）。
                val visibleItemsByIndex =
                    if (isBrowsingLyrics) browsingVisibleItemsByIndex else emptyMap()

                val edgeFadeTopPx = with(density) { edgeFadeTop.toPx() }
                val edgeFadeBottomPx = with(density) { edgeFadeBottom.toPx() }
                val edgeFadeTerminalPx = with(density) { edgeFadeBottomTerminal.toPx() }
                val hasEdgeFade = edgeFadeTopPx > 0f || edgeFadeBottomPx > 0f ||
                    edgeFadeTerminalPx > 0f
                Layout(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        // 上下边缘渐隐（替换此前"留空让位"的做法）。所有 fade 参数都是默认值
                        // （0 / 0 / 0）时不建离屏层、修饰符内部短路 ⇒ 旧调用点逐字节一致。
                        .graphicsLayer {
                            // ★ 2026-10-24：硬切的 0/1 改成 `lyricsReveal` 的缓入
                            //   （见变量声明处的长注释）。alpha 在绘制相位读，不触发重组/布局。
                            // ★★ 2026-11-03（第 33 轮，真机录屏取证后回退）：曾在这里挂整块
                            //   `BlurEffect` 随 reveal 收敛（「从朦胧对焦到清晰」）。真机录屏
                            //   逐帧看下来它把**当前正在播放的那一行**也一起糊掉了 —— 稳态里
                            //   当前行是清晰的，reveal 却让它从重模糊慢慢「对焦」，观感是
                            //   「糊一大片→加载中」，而不是优雅浮现。
                            //   现在模糊深度**只由逐行模糊模型决定**（当前行清晰、未播放行模糊
                            //   —— 这本身就是「朦胧感」的来源），焦点模型在首次定位时（alpha=0、
                            //   完全不可见）已瞬时建立到稳态（见首次定位分支的
                            //   `focusModelRamp.snapTo(1f)`），淡入呈现的第一帧就是最终画面结构。
                            // ★★★ 2026-11-03（第 34 轮）：曾有的 `translationY = (1-reveal)*rise`
                            //   12dp 上浮也删了 —— 用户录屏互相关取证：文字可读时上浮还剩大半，
                            //   观感是「加载好了又整体上滑一下再落位」。reveal 现在只做淡入，
                            //   第一帧即最终位置。
                            alpha = lyricsReveal.value
                            if (hasEdgeFade) {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                        }
                        .lyricsEdgeFade(edgeFadeTopPx, edgeFadeBottomPx, edgeFadeTerminalPx),
                    content = {
                        LazyColumn(
                            state = listState,
                            // 触顶/触底橡胶回弹触感：嵌套在歌词手势 connection 外层，
                            // 两个 nestedScroll 各自独立收到 post-scroll，互不吞掉。
                            modifier = Modifier.fillMaxSize().meloXEdgeOverscrollHaptic(listState).nestedScroll(lyricInteractionConnection),
                        ) {
                    item(key = "lyrics-top-padding") {
                        Spacer(Modifier.height(with(density) { topPaddingPx.toDp() }))
                    }
                    itemsIndexed(
                        items = lines,
                        key = { index, line -> "${line.timeMs}:$index" },
                    ) { index, line ->
                        // Lyric rows remain visible as the active cue changes.
                        val interlude = interludeByLyricIndex[index]
                        // Reserve the interlude's space as soon as the document
                        // is laid out.  Inserting it only after playback reaches
                        // the gap makes the countdown pop in and fight the lyric
                        // scroll animation.
                        val showsInterlude = MeloXSettingsRuntime.lyricInterludeCountdownEnabled &&
                            interlude != null
                        val interludeHeightPx = if (showsInterlude) with(density) { 56.dp.toPx() } else 0f
                        val height = estimatedHeight(index) + interludeHeightPx
                        val cascadeOffset = currentMovementOffset(index)
                        // 拖动预览时把级联绘制位移一并压平：定位已由 `scrollToItem` 即时完成，
                        // 再叠一段旧级联位移会让行位置和手指对不上。
                        val visualOffset = cascadeOffset * (1f - scrubFlat)
                        val disappearAlpha = 1f
                        val frameMinY = visibleItemsByIndex[index + 1]?.offset?.toFloat()
                            ?: focusAnchorY + (index - visualFocusIndex) * lyricStridePx
                        val visualMidY = frameMinY + visualOffset + height * 0.5f
                        // ★★ 2026-11-03（第 31 轮）：两套 `distance` 模型**连续插值**，
                        //   不再是「焦点一有效就整套换公式」（那会在同一帧让整屏模糊跳变，
                        //   见 `focusModelRamp` 声明处的完整推导）。
                        //   `focusRamp = 1` ⇒ 与旧行为逐像素一致；`= 0` ⇒ 与「焦点未定」
                        //   的像素距离模型逐像素一致；中间是线性混合，平滑无阶跃。
                        val focusRamp = focusModelRamp.value.coerceIn(0f, 1f)
                        val distanceNoFocus = abs(visualMidY - focusAnchorY)
                        val distanceFocus = abs(index - visualFocusIndex) * lyricStridePx
                        val distance = if (isBrowsingLyrics || visualFocusIndex !in lines.indices) {
                            distanceNoFocus
                        } else {
                            distanceNoFocus + (distanceFocus - distanceNoFocus) * focusRamp
                        }
                        // ★ 拖动进度条时（`scrubFlat` → 1）的预览行：`activeTimedLineIndexes`
                        //   由 scrub 位置驱动；纯 LRC 没有逐字行时退到 `highlightedIndex`，
                        //   否则那一行不会被点亮（用户报的「对应部分不亮」）。
                        val scrubActiveIndex = activeTimedLineIndexes.minOrNull() ?: highlightedIndex
                        val isActiveLine = index in activeTimedLineIndexes ||
                            (scrubFlat > 0.001f && index == scrubActiveIndex)
                        val fp = focusProgress[index].value.coerceIn(0f, 1f)
                        // Some LRC/YRC files deliberately provide two distinct
                        // lines at virtually the same time.  Keep both readable
                        // instead of promoting only the last binary-search hit.
                        //
                        // 拖动预览时旧焦点行淡出（否则它还带着放大 / 高亮停在原地），
                        // 松手后 `scrubFlat` 归 0，焦点自动跟随重新接管。
                        val effectiveFocus = fp * (1f - scrubFlat)
                        val distanceBlur = sourceDistanceBlurRadius(
                            distancePx = distance,
                            lyricStridePx = lyricStridePx,
                            intensity = UpstreamLyrics.BLUR_INTENSITY *
                                (if (isInterfaceHidden) MeloXSettingsRuntime.lyricHiddenInterfaceBlurScale else MeloXSettingsRuntime.lyricDistanceBlurScale) *
                                MeloXSettingsRuntime.lyricBlurStrength,
                            focusProgress = effectiveFocus,
                        ) * (1f - scrubFlat)
                        val preceding = index == visualFocusIndex - 1
                        val following = index == visualFocusIndex + 1
                        // ★★ 2026-11-03（第 31 轮）：`focusBlur`（相邻行 2.4× / 0.7×）原先只在
                        //   `visualFocusIndex` 变有效的那一帧**从 0 突然出现**，是跳变的第二个
                        //   来源。乘 `focusRamp` 让它随 `distance` 同一段过渡一起浮出。
                        //   ramp=1 时与旧行为完全一致（稳态零差异）。
                        val focusBlur = sourceFocusBlurRadius(
                            UpstreamLyrics.BLUR_INTENSITY * MeloXSettingsRuntime.lyricBlurStrength,
                            preceding,
                            following,
                        ) * (1f - effectiveFocus) * (1f - scrubFlat) * focusRamp
                        // ★★ 2026-11-03（第 31 轮）：**最终半径输出平滑**（兜底，见
                        //   `LYRIC_BLUR_SMOOTH_MS` 的注释）。`focusModelRamp` 负责把「焦点模型
                        //   切换」变连续；这里再把从输入到半径的最后一段也做低通 —— 目标突变时
                        //   半径滑过去而不是跳过去，稳态到达后无逐帧开销。
                        //   ⚠ 只平滑「半径随时间的变化率」，不改任何稳态强度、不降模糊档。
                        val rawBlurDp = max(distanceBlur, focusBlur)
                        val smoothBlurDp = animateFloatAsState(
                            targetValue = rawBlurDp,
                            animationSpec = tween(
                                durationMillis = UpstreamLyrics.LYRIC_BLUR_SMOOTH_MS,
                                easing = FastOutSlowInEasing,
                            ),
                            label = "lyricLineBlur",
                        ).value
                        // 横屏渐隐带内的边缘邻近模糊：行越贴近视口上/下边缘越糊。
                        // 竖屏 edgeFadeTop/Bottom 均为 0 ⇒ 恒 0，行为完全不变。
                        val edgeBlur = (if (hasEdgeFade && edgeBlurMax > 0.dp) {
                            sourceEdgeProximityBlur(
                                rowMidYpx = visualMidY,
                                viewportHeightPx = viewportHeightPx,
                                topBandPx = edgeFadeTopPx,
                                bottomBandPx = edgeFadeBottomPx,
                                maxBlurDp = edgeBlurMax.value,
                            )
                        } else {
                            0f
                        }) * (1f - scrubFlat)
                        val distanceOpacity = sourceDistanceOpacity(
                            distance,
                            lyricStridePx,
                            MeloXSettingsRuntime.lyricDimAmount,
                            effectiveFocus,
                        )
                        val emphasis = sourceEmphasis(effectiveFocus, MeloXSettingsRuntime.lyricDimAmount)
                        val rowAlphaRaw = (distanceOpacity * emphasis * disappearAlpha).coerceIn(0f, 1f)
                        // 拖动预览时的「同层」目标：预览行保持全亮，其余行统一到固定浅灰
                        //（不是随距离连续衰减的层级）⇒ 整篇一样清晰，只有对应位置亮。
                        val flatLineAlpha = if (index == scrubActiveIndex) 1f else .62f
                        val rowAlpha = rowAlphaRaw + (flatLineAlpha - rowAlphaRaw) * scrubFlat
                        val followingLineAlpha = sourceDistanceOpacity(
                            lyricStridePx,
                            lyricStridePx,
                            MeloXSettingsRuntime.lyricDimAmount,
                            0f,
                        ) * sourceEmphasis(0f, MeloXSettingsRuntime.lyricDimAmount)
                        val timedUnplayedRaw = (followingLineAlpha / rowAlphaRaw.coerceAtLeast(.001f))
                            .coerceIn(0f, 1f)
                        // ⚠ 扁平化后行 alpha 已统一到 1，未播放字必须换成**绝对**透明度：
                        //   再乘相对比值会 clamp 到 1 ⇒ 整行全白，"逐字亮到哪"就看不出来了。
                        val timedUnplayedAlpha = timedUnplayedRaw +
                            (followingLineAlpha - timedUnplayedRaw) * scrubFlat
                        val scaleRaw = 1f + (MeloXSettingsRuntime.lyricFocusScale - 1f) *
                            max(scaleProgress[index].value, fp)
                        // 拖动预览时取消聚焦放大 ⇒ 所有行同尺寸（用户在意的「大小层级」）。
                        val scale = scaleRaw + (1f - scaleRaw) * scrubFlat
                        // 逐行图层属性量化（性能，非观感）：近焦/稳态行 alpha≈0.95–1.0、
                        // scale≈1.0，吸附到精确 1.0 ⇒ 去掉每帧 saveLayer / 图层重建；其余行
                        // 按台阶量化，使同台阶内不触发逐行 recomposition。台阶在 34sp 文字上
                        // 不可辨，过渡观感逐字节一致。滚动位移 visualOffset 保持连续（平滑）。
                        val quantizedRowAlpha = meloxQuantizeRowAlpha(rowAlpha)
                        val quantizedScale = meloxQuantizeLayerScale(scale)

                        if (showsInterlude) {
                            MeloXLyricInterludeCountdown(
                                interlude = checkNotNull(interlude),
                                playbackTimeProvider = { renderedPositionState.longValue },
                                reduceMotion = MeloXSettingsRuntime.lyricReduceMotion,
                                visualScale = quantizedScale,
                                visualOffsetPx = visualOffset,
                                rowAlpha = quantizedRowAlpha,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }

                        MeloXUpstreamLyricLine(
                            line = line,
                            playbackTimeProvider = playbackTimeProvider,
                            // Only the focused line (plus its short colour hand-off)
                            // needs the playback clock. Subscribing every visible
                            // syllable row made the complete LazyColumn redraw at
                            // the lyric refresh rate on low-end devices.
                            supportsTimedLyrics = line.syllables.isNotEmpty() &&
                                MeloXSettingsRuntime.lyricWordByWordEnabled &&
                                (effectiveFocus > 0.001f || isActiveLine),
                            fontScale = lyricFontScale,
                            reduceMotion = MeloXSettingsRuntime.lyricReduceMotion,
                            focusProgress = effectiveFocus,
                            timingEffectsStrength = if (isActiveLine) 1f else effectiveFocus,
                            timedUnplayedAlpha = timedUnplayedAlpha,
                            visualScale = quantizedScale,
                            visualOffsetPx = visualOffset,
                            rowAlpha = quantizedRowAlpha,
                            // ★ 2026-11-03（第 31 轮）：传**平滑后**的半径。`distanceBlur` 与
                            //   `focusBlur` 已经在 `smoothBlurDp` 里取过 `max`，而下游只做
                            //   `max(distanceBlurDp, focusBlurDp, edgeBlurDp)`，所以这里
                            //   `focusBlurDp = 0f` 不会丢信息 —— 反而避免把未平滑的值叠回去。
                            distanceBlurDp = smoothBlurDp,
                            focusBlurDp = 0f,
                            edgeBlurDp = edgeBlur,
                            renderingQuality = renderingQuality,
                            showTranslation = MeloXSettingsRuntime.showLyricTranslation &&
                                !line.translation.isNullOrBlank(),
                            showRomanization = MeloXSettingsRuntime.showLyricRomanization &&
                                !line.romanization.isNullOrBlank() &&
                                (MeloXSettingsRuntime.lyricRomanizationDisplayMode == MeloXLyricAnnotationDisplayMode.AllLines || isActiveLine),
                            reserveTranslation = MeloXSettingsRuntime.showLyricTranslation && !line.translation.isNullOrBlank(),
                            reserveRomanization = MeloXSettingsRuntime.showLyricRomanization && !line.romanization.isNullOrBlank(),
                            onMeasured = { measured ->
                                if (measured > 0 && rowHeightsPx[index] != measured) rowHeightsPx[index] = measured
                            },
                            tapSeekEnabled = MeloXSettingsRuntime.lyricTapSeekEnabled,
                            onClick = { onInterfaceInteraction(); state.seekTo(line.timeMs) },
                            longPressShareEnabled = MeloXSettingsRuntime.lyricLongPressShareEnabled,
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onInterfaceInteraction()
                                shareInitialIndex = index
                            },
                        )

                        if (index != lines.lastIndex) {
                            Spacer(Modifier.height((UpstreamLyrics.LINE_SPACING_DP * lyricSpacingScale).dp))
                        }
                    }
                    item(key = "lyrics-bottom-padding") {
                        Spacer(Modifier.height(with(density) { (bottomPaddingPx + layoutOverscanPx).toDp() }))
                    }
                        }
                    },
                ) { measurables, constraints ->
                    val expandedHeight = (constraints.maxHeight.toLong() + layoutOverscanPx)
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt()
                    val list = measurables.single().measure(
                        constraints.copy(minHeight = expandedHeight, maxHeight = expandedHeight),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        list.placeRelative(0, 0)
                    }
                }
            }
        }

        // ⚠⚠ 「控件隐藏后点按唤出控件」的捕获层**已删除**，改为挂在面板根节点
        //    （见本函数最外层 `Box` 的 `pointerInput`）。原因：Compose 命中测试
        //    一旦命中某个带 pointerInput 的节点，就**不再测试它下方的兄弟节点** ——
        //    捕获层与歌词 `LazyColumn` 是同级兄弟，所以不管它是否消费事件，都会让
        //    底部 279dp 的歌词彻底滑不动（用户报的 bug；换成不消费的
        //    `detectPassThroughTap` 也无效，因为问题在命中测试阶段而非消费阶段）。
        //    挂到根节点（`LazyColumn` 的祖先）后：祖先与后代在同一条命中路径上，
        //    不构成遮挡，歌词在任何位置都能滚。
    }
    shareInitialIndex?.let { initial ->
        MeloXLyricShareDialog(
            state = state,
            lines = lines,
            initialIndex = initial,
            onDismiss = { shareInitialIndex = null },
        )
    }
}

/** Three staggered instrumental bars, matching the report's 750ms wave entry. */
@Composable
private fun MeloXLyricInstrumentalWave(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "instrumental-wave")
    // ⚠⚠ 2026-10-22 动画流畅度：这三个值**不 `by` 解包**。
    //   它们是 `infiniteRepeatable(750ms)` ⇒ 值永远在变；在组合体里读会让本 composable
    //   **永久每帧重组**（器乐段一直停在歌词页时，90Hz 无休止）。而它们唯一的消费者
    //   就是下面 `Canvas` 的 draw lambda ⇒ 在那里读 `.value` 即可：
    //   同一组动画、同一条曲线、同一个相位关系，只是读取时机从组合期挪到绘制期。
    val phase0 = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = CubicBezierEasing(.4f, 0f, 1f, 1f)),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "instrumental-wave-0",
    )
    val phase1 = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, delayMillis = 50, easing = CubicBezierEasing(.4f, 0f, 1f, 1f)),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "instrumental-wave-1",
    )
    val phase2 = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, delayMillis = 100, easing = CubicBezierEasing(.4f, 0f, 1f, 1f)),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "instrumental-wave-2",
    )
    Canvas(modifier.fillMaxWidth().height(46.dp)) {
        // 绘制期读取（不触发重组，只重绘本 Canvas）
        val phases = listOf(phase0.value, phase1.value, phase2.value)
        val barWidth = 7.dp.toPx()
        val gap = 9.dp.toPx()
        val totalWidth = barWidth * 3f + gap * 2f
        val startX = (size.width - totalWidth) / 2f
        phases.forEachIndexed { index, phase ->
            val barHeight = 20.dp.toPx() + index * 7.dp.toPx()
            drawRoundRect(
                color = Color.White.copy(alpha = phase.coerceIn(0f, 1f)),
                topLeft = Offset(
                    startX + index * (barWidth + gap),
                    (size.height - barHeight) / 2f,
                ),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )
        }
    }
}

/**
 * 逐行图层属性量化（性能，非观感）。每行歌词套一个 `graphicsLayer{ alpha; scaleX/Y }`，
 * 只要 `alpha≠1` Compose 每帧就为该行做一次 `saveLayer` 离屏合成（trace 里约 8500 次/12s）。
 * 稳态播放时近焦行本就接近全亮（alpha≈0.95–1.0），吸附到精确 1.0 即可去掉这笔合成；
 * 其余行按 0.02 台阶量化，使同台阶内不触发逐行 recomposition / 图层重建。
 * 台阶在 34sp 文字上不可辨，过渡观感逐字节一致。
 */
private fun meloxQuantizeRowAlpha(alpha: Float): Float {
    if (alpha >= 0.98f) return 1f
    return (alpha * 50f).roundToInt() / 50f
}

/** 同上，针对聚焦缩放：近 1.0 的值吸附到精确 1.0（去掉 transform/saveLayer），其余 0.01 台阶。 */
private fun meloxQuantizeLayerScale(scale: Float): Float {
    if (scale >= 0.995f && scale <= 1.005f) return 1f
    return (scale * 100f).roundToInt() / 100f
}

@Composable
private fun MeloXUpstreamLyricLine(
    line: LyricLine,
    playbackTimeProvider: () -> Long,
    supportsTimedLyrics: Boolean,
    fontScale: Float,
    reduceMotion: Boolean,
    focusProgress: Float,
    timingEffectsStrength: Float,
    timedUnplayedAlpha: Float,
    visualScale: Float,
    visualOffsetPx: Float,
    rowAlpha: Float,
    distanceBlurDp: Float,
    focusBlurDp: Float,
    // 边缘邻近模糊（dp）：横屏渐隐带内额外叠加，见 `MeloXLandscapeLyricsEdgeBlurMax`。
    // 默认 0f ⇒ 竖屏与其它样式逐帧不变。
    edgeBlurDp: Float = 0f,
    renderingQuality: MeloXLyricsRenderingQuality,
    showTranslation: Boolean,
    showRomanization: Boolean,
    reserveTranslation: Boolean,
    reserveRomanization: Boolean,
    tapSeekEnabled: Boolean,
    longPressShareEnabled: Boolean,
    onMeasured: (Int) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val flipped = line.agent?.alignment == com.lladlam.melox.core.lyrics.LyricAgentAlignment.Flipped
    val lineAlignment = if (flipped) Alignment.End else Alignment.Start
    val lineTextAlign = if (flipped) TextAlign.End else TextAlign.Start
    val requestedBlur = max(max(distanceBlurDp, focusBlurDp), max(edgeBlurDp, 0f))
    val qualityBlur = when (renderingQuality) {
        MeloXLyricsRenderingQuality.Low -> 0f
        MeloXLyricsRenderingQuality.Balanced -> requestedBlur * .55f
        MeloXLyricsRenderingQuality.High -> requestedBlur
    }
    // ★ 远端行模糊门槛（见 [BLUR_SKIP_ALPHA] 常量注释）：只跳过**接近不可见**的行，
    // 保留 `distanceBlur` 的深度模糊渐变（远行更糊，Apple Music 式景深）。
    // 旧值 0.18 把这道渐变砍成了「远处发暗但清晰」，用户反馈不符完整版本观感，
    // 2026-10-24 回调到 0.05。
    //
    // ★ 量化到 0.5dp：`Modifier.blur` 只要半径这个 Dp 值变了就换一个
    // RenderEffect（新离屏层 + 高斯），而 `focusProgress` 驱动的模糊过渡是**每帧
    // 连续**的 ⇒ 逐帧重建。0.5dp 台阶在 34sp 文字上不可辨，却能把重建次数降一个
    // 数量级（同 `sourceEdgeProximityBlur` 的 0.25dp 量化思路）。稳态播放时每行模糊
    // 半径恒定 ⇒ 纹理被缓存不重建；真正付费只在滚动 / 转场期间，且已受量化约束。
    val quantizedBlur = (if (rowAlpha < UpstreamLyrics.BLUR_SKIP_ALPHA) 0f else qualityBlur).let {
        (it * 2f).roundToInt() / 2f
    }
    // Merge note: keep local quantizedBlur (remote-row blur gate + 0.5dp quantization);
    // adopt upstream precedesLead vocal-onset comparison with remember(line).
    val (accompanimentBefore, accompanimentAfter) = remember(line) {
        line.accompaniment.partition { it.precedesLead(line) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onMeasured(it.height) }
            .graphicsLayer {
                translationY = visualOffsetPx
                scaleX = visualScale
                scaleY = visualScale
                alpha = rowAlpha
                transformOrigin = TransformOrigin(if (flipped) 1f else 0f, 0f)
            }
            .combinedClickable(
                enabled = tapSeekEnabled || longPressShareEnabled,
                onClick = { if (tapSeekEnabled) onClick() },
                onLongClick = { if (longPressShareEnabled) onLongClick() },
            )
            .padding(horizontal = MeloXLyricLineHorizontalPadding),
        horizontalAlignment = lineAlignment,
    ) {
        accompanimentBefore.forEach { vocal ->
            MeloXTimedAccompaniment(vocal, playbackTimeProvider, fontScale, reduceMotion, renderingQuality, quantizedBlur)
        }
        if (reserveRomanization) {
            MeloXRubyLyricText(
                line = line,
                playbackTimeProvider = playbackTimeProvider,
                // Only the current/transitioning line subscribes to the frame
                // clock. Static ruby rows otherwise invalidated every unit at
                // 60 Hz even though their visual result did not change.
                supportsTimedLyrics = supportsTimedLyrics,
                fontScale = fontScale,
                reduceMotion = reduceMotion,
                timingEffectsStrength = timingEffectsStrength,
                unplayedAlpha = timedUnplayedAlpha,
                renderingQuality = renderingQuality,
                softBlurDp = quantizedBlur,
                showRuby = showRomanization,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            MeloXGlyphLyricText(
                line = line,
                playbackTimeProvider = playbackTimeProvider,
                supportsTimedLyrics = supportsTimedLyrics,
                fontScale = fontScale,
                // MeloX iOS sends pseudo-timed LRC through this same
                // renderer. Only genuine long tones expand; ordinary glyphs
                // merely reveal and lift according to the selected mode.
                reduceMotion = reduceMotion,
                timingEffectsStrength = timingEffectsStrength,
                unplayedAlpha = timedUnplayedAlpha,
                renderingQuality = renderingQuality,
                softBlurDp = quantizedBlur,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        accompanimentAfter.forEach { vocal ->
            MeloXTimedAccompaniment(vocal, playbackTimeProvider, fontScale, reduceMotion, renderingQuality, quantizedBlur)
        }

        val translationSize = max(UpstreamLyrics.FONT_SIZE_SP * fontScale * MeloXSettingsRuntime.lyricTranslationFontScale, 13f)
        if (showTranslation) {
            Text(
                text = line.translation.orEmpty(),
                modifier = Modifier.fillMaxWidth()
                    .blur(quantizedBlur.dp)
                    .padding(top = UpstreamLyrics.ANNOTATION_SPACING_DP.dp),
                color = Color.White.copy(alpha = MeloXSettingsRuntime.lyricTranslationOpacity),
                textAlign = lineTextAlign,
                fontSize = translationSize.sp,
                lineHeight = translationSize.sp * 1.2f,
                fontWeight = MeloXSettingsRuntime.lyricFontWeight.composeWeight,
                style = TextStyle(
                    shadow = if (quantizedBlur > .05f) {
                        Shadow(
                            color = Color.White.copy(alpha = .48f),
                            offset = Offset.Zero,
                            blurRadius = quantizedBlur,
                        )
                    } else null,
                ),
            )
        } else if (reserveTranslation) {
            val translationHeight = with(LocalDensity.current) { (translationSize * 1.2f).sp.toDp() }
            Spacer(Modifier.height(translationHeight + UpstreamLyrics.ANNOTATION_SPACING_DP.dp))
        }
    }
}

@Composable
private fun MeloXTimedAccompaniment(
    vocal: com.lladlam.melox.core.lyrics.LyricAccompaniment,
    playbackTimeProvider: () -> Long,
    fontScale: Float,
    reduceMotion: Boolean,
    renderingQuality: MeloXLyricsRenderingQuality,
    softBlurDp: Float,
) {
    val end = vocal.durationMs?.let { vocal.timeMs + it }
        ?: vocal.syllables.maxOfOrNull { it.endTimeMs }
        ?: vocal.timeMs
    val visible = playbackTimeProvider() in (vocal.timeMs - 600L)..(end + 600L)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 2 } + expandVertically(tween(600)),
        exit = fadeOut(tween(600)) + slideOutVertically(tween(600)) { it / 2 } + shrinkVertically(tween(600)),
    ) {
        val vocalSyllables = vocal.syllables.withoutOuterBrackets()
        val vocalLine = LyricLine(
            vocal.timeMs,
            vocal.durationMs,
            vocal.text.withoutOuterBrackets(),
            vocalSyllables,
            agent = vocal.agent,
        )
        // Lead vocals stay on the line clock. Accompaniment keeps its own
        // syllable times, so only the words already reached light up.
        MeloXGlyphLyricText(
            line = vocalLine,
            playbackTimeProvider = playbackTimeProvider,
            supportsTimedLyrics = vocal.syllables.isNotEmpty(),
            fontScale = fontScale * .68f,
            reduceMotion = reduceMotion,
            timingEffectsStrength = 1f,
            followOwnTiming = true,
            renderingQuality = renderingQuality,
            softBlurDp = softBlurDp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).graphicsLayer { alpha = .72f },
        )
    }
}

@Composable
private fun MeloXRubyLyricText(
    line: LyricLine,
    playbackTimeProvider: () -> Long,
    supportsTimedLyrics: Boolean,
    fontScale: Float,
    reduceMotion: Boolean,
    timingEffectsStrength: Float,
    unplayedAlpha: Float,
    renderingQuality: MeloXLyricsRenderingQuality,
    softBlurDp: Float = 0f,
    showRuby: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val useSystemFont = MeloXSettingsRuntime.lyricSystemFontEnabled
    val fontTypeface = remember(useSystemFont) {
        if (useSystemFont) Typeface.DEFAULT
        else ResourcesCompat.getFont(context, R.font.mi_lan_pro) ?: Typeface.DEFAULT
    }
    val measureStyle = TextStyle(
        fontFamily = if (useSystemFont) FontFamily.Default else MeloXLanTingProFontFamily,
        fontSize = (UpstreamLyrics.FONT_SIZE_SP * fontScale).sp,
        fontWeight = MeloXSettingsRuntime.lyricFontWeight.composeWeight,
        textAlign = if (line.agent?.alignment == com.lladlam.melox.core.lyrics.LyricAgentAlignment.Flipped) {
            TextAlign.End
        } else {
            TextAlign.Start
        },
    )
    val textMeasurer = rememberTextMeasurer(cacheSize = 16)
    val romanSize = max(
        UpstreamLyrics.FONT_SIZE_SP * fontScale * MeloXSettingsRuntime.lyricRomanizationFontScale,
        13f,
    )
    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val layout = remember(line.text, widthPx, measureStyle) {
            textMeasurer.measure(
                text = AnnotatedString(line.text),
                style = measureStyle,
                constraints = Constraints(minWidth = widthPx, maxWidth = widthPx),
                softWrap = true,
            )
        }
        // Split a wrapped lyric into its visual rows first, then render each
        // row as its own glyph line followed by its own romanization row.
        // This keeps every romanization locked directly under the words it
        // belongs to instead of placing one block under the whole paragraph.
        val segments = remember(line, layout) { splitRomanizedSegments(line, layout) }
        Column(Modifier.fillMaxWidth()) {
            segments.forEach { segment ->
                MeloXGlyphLyricText(
                    line = segment.line,
                    playbackTimeProvider = playbackTimeProvider,
                    supportsTimedLyrics = supportsTimedLyrics,
                    fontScale = fontScale,
                    reduceMotion = reduceMotion,
                    timingEffectsStrength = timingEffectsStrength,
                    unplayedAlpha = unplayedAlpha,
                    renderingQuality = renderingQuality,
                    softBlurDp = softBlurDp,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showRuby && segment.units.isNotEmpty()) {
                    MeloXRomanizationRow(
                        text = segment.line.text,
                        units = segment.units,
                        fontScale = fontScale,
                        flipped = line.agent?.alignment == com.lladlam.melox.core.lyrics.LyricAgentAlignment.Flipped,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (!showRuby && !line.romanization.isNullOrBlank()) {
                Spacer(
                    Modifier.height(
                        with(density) { (romanSize * 1.2f).sp.toDp() } +
                            UpstreamLyrics.ANNOTATION_SPACING_DP.dp,
                    ),
                )
            }
        }
    }
}

/**
 * Draws each romanization fragment centred over the exact characters it
 * belongs to, so the annotation follows per-character alignment instead of
 * forming one left-aligned text row.
 */
@Composable
private fun MeloXRomanizationRow(
    text: String,
    units: List<MeloXRomanizedUnit>,
    fontScale: Float,
    flipped: Boolean,
    modifier: Modifier = Modifier,
) {
    if (units.isEmpty() || text.isEmpty()) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val useSystemFont = MeloXSettingsRuntime.lyricSystemFontEnabled
    val typeface = remember(useSystemFont) {
        if (useSystemFont) Typeface.DEFAULT
        else ResourcesCompat.getFont(context, R.font.mi_lan_pro) ?: Typeface.DEFAULT
    }
    val weight = MeloXSettingsRuntime.lyricFontWeight.composeWeight
    val measureStyle = TextStyle(
        fontFamily = if (useSystemFont) FontFamily.Default else MeloXLanTingProFontFamily,
        fontSize = (UpstreamLyrics.FONT_SIZE_SP * fontScale).sp,
        fontWeight = weight,
        textAlign = if (flipped) TextAlign.End else TextAlign.Start,
    )
    val textMeasurer = rememberTextMeasurer(cacheSize = 16)
    val romanSize = max(
        UpstreamLyrics.FONT_SIZE_SP * fontScale * MeloXSettingsRuntime.lyricRomanizationFontScale,
        13f,
    )
    val spacingPx = with(density) { UpstreamLyrics.ANNOTATION_SPACING_DP.dp.toPx() }
    val romanHeightPx = with(density) { (romanSize * 1.2f).sp.toPx() }
    BoxWithConstraints(modifier) {
        val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val layout = remember(text, widthPx, measureStyle) {
            textMeasurer.measure(
                text = AnnotatedString(text),
                style = measureStyle,
                constraints = Constraints(minWidth = widthPx, maxWidth = widthPx),
                softWrap = true,
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(with(density) { (romanHeightPx + spacingPx).toDp() }),
        ) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = with(density) { romanSize.sp.toPx() }
                alpha = (MeloXSettingsRuntime.lyricRomanizationOpacity.coerceIn(0f, 1f) * 255f).roundToInt()
                this.typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Typeface.create(typeface, weight.weight, false)
                } else {
                    Typeface.create(typeface, if (weight.weight >= 600) Typeface.BOLD else Typeface.NORMAL)
                }
            }
            val metrics = paint.fontMetrics
            val baseline = spacingPx + (romanHeightPx - (metrics.descent - metrics.ascent)) * .5f - metrics.ascent
            val baseTextSize = with(density) { romanSize.sp.toPx() }
            // First pass: the exact horizontal span of each unit's characters.
            val spans = units.mapNotNull { unit ->
                val start = unit.start.coerceIn(0, text.length)
                val end = unit.end.coerceIn(start, text.length)
                if (end <= start) return@mapNotNull null
                var left = Float.MAX_VALUE
                var right = -Float.MAX_VALUE
                for (offset in start until end) {
                    val box = runCatching { layout.getBoundingBox(offset) }.getOrNull() ?: continue
                    if (!box.width.isFinite() || !box.height.isFinite()) continue
                    left = min(left, box.left)
                    right = max(right, box.right)
                }
                val center = if (right > left) {
                    (left + right) * .5f
                } else {
                    runCatching { layout.getHorizontalPosition(start, true) }.getOrDefault(0f)
                }
                MeloXRomanizedSpan(unit.reading, left, right, center)
            }
            // Second pass: give every unit the space up to the midpoint of its
            // neighbours. A reading that would enter the next unit's area is
            // scaled down until it fits, so annotations never collide.
            spans.forEachIndexed { index, span ->
                val previous = spans.getOrNull(index - 1)
                val next = spans.getOrNull(index + 1)
                val leftLimit = previous?.let { (it.center + span.center) * .5f } ?: 0f
                val rightLimit = next?.let { (it.center + span.center) * .5f } ?: size.width
                val available = (rightLimit - leftLimit).coerceAtLeast(1f)
                paint.textSize = baseTextSize
                val naturalWidth = paint.measureText(span.reading)
                if (naturalWidth > available) {
                    paint.textSize = (baseTextSize * available / naturalWidth)
                        .coerceAtLeast(baseTextSize * .35f)
                }
                val width = paint.measureText(span.reading)
                val x = (span.center - width * .5f)
                    .coerceIn(leftLimit, (rightLimit - width).coerceAtLeast(leftLimit))
                drawContext.canvas.nativeCanvas.drawText(span.reading, x, baseline, paint)
            }
            paint.textSize = baseTextSize
        }
    }
}

private data class MeloXRomanizedSpan(
    val reading: String,
    val left: Float,
    val right: Float,
    val center: Float,
)

private data class MeloXRomanizedUnit(val start: Int, val end: Int, val reading: String)

private data class MeloXRomanizedSegment(
    val line: LyricLine,
    val units: List<MeloXRomanizedUnit>,
)

/**
 * Splits one logical lyric line at the same wrap boundaries measured by the
 * glyph renderer. Each visual row keeps the original character offsets so its
 * syllables and romanization units stay on the row where they are drawn.
 */
private fun splitRomanizedSegments(
    line: LyricLine,
    layout: TextLayoutResult,
): List<MeloXRomanizedSegment> {
    val text = line.text
    if (text.isEmpty()) return listOf(MeloXRomanizedSegment(line.copy(romanization = null), emptyList()))

    val syllableRanges = mutableListOf<Triple<LyricSyllable, Int, Int>>()
    var syllableSearch = 0
    for (syllable in line.syllables) {
        if (syllable.text.isEmpty()) continue
        val found = text.indexOf(syllable.text, syllableSearch)
        if (found < 0) continue
        syllableSearch = found + syllable.text.length
        syllableRanges += Triple(syllable, found, found + syllable.text.length)
    }
    val unitRanges = mutableListOf<Triple<String, Int, Int>>()
    var unitSearch = 0
    for (unit in LyricRomanizationAligner.units(line)) {
        val original = unit.originalText
        val reading = unit.romanizationText?.trim().orEmpty()
        if (original.isEmpty() || reading.isEmpty()) continue
        val found = text.indexOf(original, unitSearch)
        if (found < 0) continue
        unitSearch = found + original.length
        unitRanges += Triple(reading, found, found + original.length)
    }
    if (unitRanges.isEmpty()) {
        line.romanization?.trim()?.takeIf(String::isNotEmpty)?.let { reading ->
            unitRanges += Triple(reading, 0, text.length)
        }
    }

    val segments = mutableListOf<MeloXRomanizedSegment>()
    for (index in 0 until layout.lineCount) {
        val start = layout.getLineStart(index)
        val end = layout.getLineEnd(index, true).coerceAtMost(text.length)
        if (end <= start) continue
        val segmentText = text.substring(start, end).trimEnd()
        if (segmentText.isEmpty()) continue
        val syllables = syllableRanges.mapNotNull { (syllable, from, to) ->
            val overlapStart = max(start, from)
            val overlapEnd = min(end, to)
            if (overlapEnd <= overlapStart) return@mapNotNull null
            val total = (to - from).coerceAtLeast(1)
            val duration = (syllable.endTimeMs - syllable.startTimeMs).coerceAtLeast(0L)
            val localStart = syllable.startTimeMs + duration * (overlapStart - from) / total
            val localEnd = syllable.startTimeMs + duration * (overlapEnd - from) / total
            syllable.copy(
                text = text.substring(overlapStart, overlapEnd),
                startTimeMs = localStart,
                endTimeMs = max(localEnd, localStart + 1L),
            )
        }
        val units = unitRanges.mapNotNull { (reading, from, to) ->
            val overlapStart = max(start, from)
            val overlapEnd = min(end, to)
            if (overlapEnd <= overlapStart) return@mapNotNull null
            val fragment = if (overlapStart == from && overlapEnd == to) {
                reading
            } else {
                // A unit straddles this wrap: split its reading proportionally
                // so every visual row still gets the part that belongs to it.
                val total = (to - from).coerceAtLeast(1)
                val ratioStart = (overlapStart - from).toFloat() / total
                val ratioEnd = (overlapEnd - from).toFloat() / total
                val left = (ratioStart * reading.length).toInt().coerceIn(0, reading.length)
                val right = (ratioEnd * reading.length).toInt().coerceIn(left, reading.length)
                reading.substring(left, right).trim()
            }
            fragment.takeIf(String::isNotBlank)?.let {
                MeloXRomanizedUnit(
                    start = (overlapStart - start).coerceIn(0, segmentText.length),
                    end = (overlapEnd - start).coerceIn(0, segmentText.length),
                    reading = it,
                )
            }
        }.filter { it.end > it.start }
        segments += MeloXRomanizedSegment(
            line.copy(
                text = segmentText,
                syllables = syllables,
                translation = null,
                romanization = null,
                romanizationSyllables = emptyList(),
                accompaniment = emptyList(),
            ),
            units,
        )
    }
    return segments.ifEmpty {
        listOf(MeloXRomanizedSegment(line.copy(romanization = null), emptyList()))
    }
}

private data class MeloXGlyphVisual(
    val reveal: Float,
    val liftPx: Float,
    val scale: Float,
    val glow: Float,
    val shakeXPx: Float,
    val shakeYPx: Float,
)

private data class MeloXGlyphTiming(
    val textOffset: Int,
    val start: Float,
    val end: Float,
    val liftStart: Float,
    val liftEnd: Float,
    val syllableStart: Float,
    val syllableEnd: Float,
    val characterIndex: Int,
    val characterCount: Int,
    val wordStart: Float,
    val wordEnd: Float,
    val wordCharacterIndex: Int,
    val wordCharacterCount: Int,
    val usesWordTimingForLongTone: Boolean,
    val longToneStart: Float,
    val longToneDuration: Float,
    val longToneCharacterIndex: Int,
    val longToneCharacterCount: Int,
    val isLongTone: Boolean,
    val expansionAmount: Float,
    val glowAmount: Float,
)

private data class MeloXDrawableGlyph(
    val textOffset: Int,
    val text: String,
    val bounds: Rect,
    val baseline: Float,
)

private val InactiveGlyphVisual = MeloXGlyphVisual(0f, 0f, 1f, 0f, 0f, 0f)

private data class MeloXLyricInterlude(
    val startTimeMs: Long,
    val countdownEndTimeMs: Long,
    val followingLyricTimeMs: Long,
    val followingLyricIndex: Int,
)

private fun sourceLyricInterludes(lines: List<LyricLine>): List<MeloXLyricInterlude> = buildList {
    lines.forEachIndexed { index, line ->
        val start = if (index == 0) {
            0L
        } else {
            val previous = lines[index - 1]
            max(
                previous.timeMs + (previous.durationMs ?: 0L),
                previous.syllables.maxOfOrNull { it.endTimeMs } ?: previous.timeMs,
            )
        }
        val countdownEnd = (line.timeMs - 250L).coerceAtLeast(start)
        if (countdownEnd - start >= 4_000L) {
            add(MeloXLyricInterlude(start, countdownEnd, line.timeMs, index))
        }
    }
}

@Composable
private fun MeloXLyricInterludeCountdown(
    interlude: MeloXLyricInterlude,
    playbackTimeProvider: () -> Long,
    reduceMotion: Boolean,
    visualScale: Float,
    visualOffsetPx: Float,
    rowAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .fillMaxWidth(0.28f)
            .height(48.dp)
            .graphicsLayer {
                translationY = visualOffsetPx
                scaleX = visualScale
                scaleY = visualScale
                alpha = rowAlpha
                transformOrigin = TransformOrigin(0f, 0f)
            },
    ) {
        val now = playbackTimeProvider()
        if (now < interlude.startTimeMs) return@Canvas
        val duration = (interlude.countdownEndTimeMs - interlude.startTimeMs).coerceAtLeast(1L)
        val elapsed = (now - interlude.startTimeMs).coerceIn(0L, duration)
        val remaining = interlude.countdownEndTimeMs - now
        if (remaining <= 0L) return@Canvas
        val durationFloat = duration.toFloat()
        val elapsedFloat = elapsed.toFloat()
        val baseRadius = 5.dp.toPx()
        val gap = 18.dp.toPx()
        val centerY = size.height / 2f
        repeat(3) { index ->
            val segmentStart = durationFloat * index / 3f
            val progress = ((elapsedFloat - segmentStart) / (durationFloat / 3f)).coerceIn(0.25f, 1f)
            val fadeOut = (remaining / 375f).coerceIn(0f, 1f)
            val breathe = if (reduceMotion) 1f else 1f +
                sin(elapsed.toDouble() / 1_500.0 * 2.0 * Math.PI).toFloat() * 0.05f
            drawCircle(
                color = Color.White.copy(alpha = progress * fadeOut),
                radius = baseRadius * breathe * if (index == 2) (1f + progress * 0.18f) else 1f,
                center = Offset(baseRadius + index * gap, centerY),
            )
        }
    }
}

private fun shareLyric(context: Context, state: MeloXPlaybackUiState, line: LyricLine) {
    val songUrl = state.mediaId
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
        ?.let { "https://music.163.com/song?id=$it" }
        .orEmpty()
    val text = buildString {
        append(line.text)
        if (state.title.isNotBlank()) {
            append("\n——《").append(state.title).append("》")
            if (state.artist.isNotBlank()) append(" · ").append(state.artist)
        }
        if (songUrl.isNotBlank()) append('\n').append(songUrl)
    }
    val intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        },
        "分享歌词",
    )
    context.startActivity(intent)
}

@Composable
private fun MeloXGlyphLyricText(
    line: LyricLine,
    playbackTimeProvider: () -> Long,
    supportsTimedLyrics: Boolean,
    fontScale: Float,
    reduceMotion: Boolean,
    timingEffectsStrength: Float,
    unplayedAlpha: Float = MeloXSettingsRuntime.lyricInactiveOpacity,
    followOwnTiming: Boolean = false,
    renderingQuality: MeloXLyricsRenderingQuality,
    softBlurDp: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val flipped = line.agent?.alignment == com.lladlam.melox.core.lyrics.LyricAgentAlignment.Flipped
    val context = LocalContext.current
    val density = LocalDensity.current
    val lyricWeight = MeloXSettingsRuntime.lyricFontWeight.composeWeight
    val useSystemFont = MeloXSettingsRuntime.lyricSystemFontEnabled
    val fontTypeface = remember(useSystemFont) {
        if (useSystemFont) Typeface.DEFAULT
        else ResourcesCompat.getFont(context, R.font.mi_lan_pro) ?: Typeface.DEFAULT
    }
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val style = TextStyle(
            color = Color.White,
            fontFamily = if (useSystemFont) FontFamily.Default else MeloXLanTingProFontFamily,
            fontSize = (UpstreamLyrics.FONT_SIZE_SP * fontScale).sp,
            lineHeight = (UpstreamLyrics.LINE_HEIGHT_SP * fontScale).sp,
            fontWeight = lyricWeight,
            textAlign = if (flipped) TextAlign.End else TextAlign.Start,
        )
        val layout = remember(line.text, widthPx, style) {
            textMeasurer.measure(
                text = AnnotatedString(line.text),
                style = style,
                constraints = Constraints(minWidth = widthPx, maxWidth = widthPx),
                softWrap = true,
            )
        }
        val height = with(density) { layout.size.height.toDp() }
        val drawableGlyphs = remember(layout, line.text) {
            buildList {
                var offset = 0
                while (offset < line.text.length) {
                    val character = line.text[offset]
                    val codeUnitCount = if (
                        Character.isHighSurrogate(character) &&
                        offset + 1 < line.text.length &&
                        Character.isLowSurrogate(line.text[offset + 1])
                    ) 2 else 1
                    if (character != '\n' && character != '\r' && !Character.isLowSurrogate(character)) {
                        val bounds = runCatching { layout.getBoundingBox(offset) }.getOrNull()?.takeIf { box ->
                            box.width.isFinite() && box.height.isFinite() && box.width > 0f && box.height > 0f
                        }
                        if (bounds != null) {
                            val lineIndex = layout.getLineForOffset(offset)
                            val baseline = layout.getLineBaseline(lineIndex)
                            add(
                                MeloXDrawableGlyph(
                                    textOffset = offset,
                                    text = line.text.substring(offset, offset + codeUnitCount),
                                    bounds = bounds,
                                    baseline = baseline,
                                ),
                            )
                        }
                    }
                    offset += codeUnitCount
                }
            }
        }
        val liftMode = MeloXSettingsRuntime.lyricLiftMode
        val longToneDetectionMode = MeloXSettingsRuntime.lyricLongToneDetectionMode
        val longToneThresholdMs = MeloXSettingsRuntime.lyricLongToneThresholdMs
        val glyphTimings = remember(line, liftMode, longToneDetectionMode, longToneThresholdMs) {
            sourceGlyphTimings(
                line = line,
                liftMode = liftMode,
                longToneDetectionMode = longToneDetectionMode,
                longToneThresholdMs = longToneThresholdMs,
            )
        }
        val glyphPaint = remember(style, density, fontTypeface) {
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = with(density) { style.fontSize.toPx() }
                typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Typeface.create(fontTypeface, lyricWeight.weight, false)
                } else {
                    Typeface.create(fontTypeface, if (lyricWeight.weight >= 600) Typeface.BOLD else Typeface.NORMAL)
                }
            }
        }
        val blurMask = remember(softBlurDp, density) {
            val radiusPx = with(density) { softBlurDp.dp.toPx() }
            radiusPx.takeIf { it > .05f }?.let { BlurMaskFilter(it, BlurMaskFilter.Blur.NORMAL) }
        }
        // ★ 高光半径/蒙版提到绘制循环外 `remember`（2026-10-19）。
        // 它只取决于字号与渲染质量（都是稳定的），此前却**每个字形每一帧**新建一个
        // `BlurMaskFilter` —— 逐字歌词每帧几十次对象构造 + 蒙版重算，正是歌词页
        // `issue` 段偏高的来源之一。复用一个不可变实例即可（`BlurMaskFilter` 无状态）。
        //
        // ★★ 2026-10-24 修「长音发光是一圈正方形白光」：半径原为字号 30%（High）/
        //    18%（Balanced）。34sp 字号下 30% ≈ 30px，远大于笔画宽（~4px），而 CJK 汉字
        //    本身撑满 em 方框 ⇒ BlurMaskFilter 按字形 alpha 外糊时四角被 em 框撑满，
        //    糊出来就是一坨包住字的**白色方块**（真机截图实证，用户报障原话「高亮范围是
        //    周围正方形」）。AM 口径（2026-10-24 查证）：逐字高亮只是「唱到的字变白」
        //    （brightens the words as they're sung），至多一层极紧的辉光，没有方块光。
        //    ⇒ 收紧到 12% / 8%：辉光贴字形、不再撑成方块；想要更亮可调设置里的
        //      「发光强度」（alpha 0–1.5），半径不再提供。
        val glowRadiusPx = remember(style.fontSize, renderingQuality, density) {
            if (renderingQuality == MeloXLyricsRenderingQuality.Low) {
                0f
            } else {
                with(density) {
                    (style.fontSize.toPx() *
                        if (renderingQuality == MeloXLyricsRenderingQuality.High) .12f else .08f)
                        .coerceAtLeast(3.dp.toPx())
                }
            }
        }
        val glowBlurMask = remember(glowRadiusPx) {
            glowRadiusPx.takeIf { it > 0f }?.let { BlurMaskFilter(it, BlurMaskFilter.Blur.NORMAL) }
        }

        val animatesTiming = supportsTimedLyrics &&
            timingEffectsStrength > 0.001f &&
            line.text.isNotEmpty()
        if (!animatesTiming) {
            Canvas(Modifier.fillMaxWidth().height(height)) {
                val staticAlpha = 1f
                if (blurMask == null) {
                    drawText(layout, color = Color.White.copy(alpha = staticAlpha))
                } else {
                    // Blur glyph masks directly. Unlike RenderEffect this does
                    // not allocate a screen-sized offscreen texture per lyric
                    // row, and the positions still come from Compose's layout.
                    glyphPaint.alpha = (staticAlpha * 255f).roundToInt()
                    glyphPaint.maskFilter = blurMask
                    drawableGlyphs.forEach { glyph ->
                        drawContext.canvas.nativeCanvas.drawText(
                            glyph.text,
                            glyph.bounds.left,
                            glyph.baseline,
                            glyphPaint,
                        )
                    }
                    glyphPaint.maskFilter = null
                }
            }
        } else {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val playbackTimeMs = playbackTimeProvider()
            val effectsStrength = if (followOwnTiming) {
                timingEffectsStrength.coerceIn(0f, 1f)
            } else {
                sourceTimingEffectsStrength(
                    line = line,
                    playbackTimeMs = playbackTimeMs,
                    focusProgress = timingEffectsStrength,
                )
            }
            if (effectsStrength <= 0.0001f) {
                drawText(
                    layout,
                    color = Color.White.copy(alpha = unplayedAlpha),
                )
                return@Canvas
            }

            // The unrevealed layer must remain at the ordinary unplayed lyric
            // opacity. Interpolating it from fully white made the whole next
            // line flash before the first syllable started revealing.
            val unplayedAlpha = unplayedAlpha.coerceIn(0f, 1f)

            if ((renderingQuality != MeloXLyricsRenderingQuality.High || reduceMotion) &&
                (!MeloXSettingsRuntime.lyricWordByWordEnabled || reduceMotion)
            ) {
                // Low/Balanced render the complete shaped row twice and reveal
                // it with one row mask. This preserves ligatures and reduces a
                // CJK line from dozens of native drawText/clip calls to two.
                drawText(layout, color = Color.White.copy(alpha = unplayedAlpha))
                val first = line.syllables.minOfOrNull { it.startTimeMs } ?: line.timeMs
                val last = line.syllables.maxOfOrNull { it.endTimeMs }
                    ?: (line.timeMs + (line.durationMs ?: 2_000L))
                val rowReveal = ((playbackTimeMs - first).toFloat() / (last - first).coerceAtLeast(1L))
                    .coerceIn(0f, 1f)
                if (rowReveal > 0f) {
                    if (isRtl) {
                        clipRect(left = size.width * (1f - rowReveal)) {
                            drawText(layout, color = Color.White)
                        }
                    } else {
                        clipRect(right = size.width * rowReveal) {
                            drawText(layout, color = Color.White)
                        }
                    }
                }
                return@Canvas
            }

            for (glyph in drawableGlyphs) {
                val bounds = glyph.bounds
                val fx = glyphTimings.getOrNull(glyph.textOffset)?.let { timing ->
                    sourceGlyphVisual(
                        timing = timing,
                        playbackTimeMs = playbackTimeMs,
                        density = density.density,
                        reduceMotion = reduceMotion,
                        fontScale = fontScale,
                    )
                } ?: InactiveGlyphVisual

                withTransform({
                    translate(
                        left = fx.shakeXPx * effectsStrength,
                        top = -fx.liftPx * effectsStrength + fx.shakeYPx * effectsStrength,
                    )
                    val presentationScale = 1f + (fx.scale - 1f) * effectsStrength
                    scale(
                        scaleX = presentationScale,
                        scaleY = presentationScale,
                        pivot = bounds.center,
                    )
                }) {
                    fun drawGlyph(alpha: Float) {
                        // The measured position and baseline come from the
                        // complete Compose layout, but only this glyph is
                        // rasterized. Both layers share this transformed
                        // coordinate space, matching MeloX iOS's runContext.
                        glyphPaint.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
                        drawContext.canvas.nativeCanvas.drawText(
                            glyph.text,
                            glyph.bounds.left,
                            glyph.baseline,
                            glyphPaint,
                        )
                    }

                    // Draw the unplayed layer after applying the glyph's lift
                    // and expansion. Keeping it at the original line position
                    // left a gray duplicate below every lifted white glyph.
                    drawGlyph(unplayedAlpha)

                    val reveal = fx.reveal.coerceIn(0f, 1f)
                    // ★ 2026-10-24：`fx.glow` 已归一到「满档 = 1.0」，这里只乘设置值，
                    //   不再有额外的 `.648` 衰减 —— 设置显示 100% 时峰值就是满白。
                    val glow = (fx.glow * effectsStrength * MeloXSettingsRuntime.lyricGlowStrength)
                        .coerceIn(0f, 1f)
                    if (
                        reveal > 0f &&
                        glow > 0.001f &&
                        glowBlurMask != null &&
                        !reduceMotion
                    ) {
                        val glowRadius = glowRadiusPx
                        val revealFront = if (isRtl) {
                            bounds.right - bounds.width * reveal
                        } else {
                            bounds.left + bounds.width * reveal
                        }
                        clipRect(
                            left = if (isRtl) revealFront - glowRadius else bounds.left - glowRadius,
                            top = bounds.top - glowRadius,
                            right = if (isRtl) bounds.right + glowRadius else revealFront + glowRadius,
                            bottom = bounds.bottom + glowRadius,
                        ) {
                            glyphPaint.alpha = (glow * 255f).roundToInt()
                            glyphPaint.maskFilter = glowBlurMask
                            drawContext.canvas.nativeCanvas.drawText(
                                glyph.text,
                                glyph.bounds.left,
                                glyph.baseline,
                                glyphPaint,
                            )
                            glyphPaint.maskFilter = null
                        }
                    }

                    clipRect(
                        left = bounds.left,
                        top = bounds.top,
                        right = bounds.right,
                        bottom = bounds.bottom,
                    ) {
                        if (reveal <= 0f) return@clipRect

                        val feather = max(
                            bounds.width * MeloXSettingsRuntime.lyricHighlightGradientWidth,
                            1.5f * density.density,
                        )
                        val front = if (isRtl) {
                            bounds.right + feather - (bounds.width + feather) * reveal
                        } else {
                            bounds.left - feather + (bounds.width + feather) * reveal
                        }
                        val solidLeft = if (isRtl) max(front, bounds.left) else bounds.left
                        val solidRight = if (isRtl) bounds.right else min(front, bounds.right)
                        fun drawRevealed(alpha: Float) {
                            if (solidRight > solidLeft) {
                                clipRect(
                                    left = solidLeft,
                                    top = bounds.top,
                                    right = solidRight,
                                    bottom = bounds.bottom,
                                ) {
                                    drawGlyph(alpha)
                                }
                            }

                            val stopCount = when (renderingQuality) {
                                MeloXLyricsRenderingQuality.Low -> 1
                                MeloXLyricsRenderingQuality.Balanced -> 2
                                MeloXLyricsRenderingQuality.High -> 3
                            }
                            for (step in 0 until stopCount) {
                                val a = step.toFloat() / stopCount.toFloat()
                                val b = (step + 1).toFloat() / stopCount.toFloat()
                                val mid = (a + b) * .5f
                                val remaining = 1f - mid
                                val baseMask = remaining *
                                    (1f - MeloXSettingsRuntime.lyricHighlightGradientReduction * mid)
                                val maskAlpha = baseMask +
                                    (1f - baseMask) * glow.coerceIn(0f, 1f) * .14f
                                val left = if (isRtl) {
                                    max(front - feather * b, bounds.left)
                                } else {
                                    max(front + feather * a, bounds.left)
                                }
                                val right = if (isRtl) {
                                    min(front - feather * a, bounds.right)
                                } else {
                                    min(front + feather * b, bounds.right)
                                }
                                if (right > left) {
                                    clipRect(
                                        left = left,
                                        top = bounds.top,
                                        right = right,
                                        bottom = bounds.bottom,
                                    ) {
                                        drawGlyph(alpha * maskAlpha.coerceIn(0f, 1f))
                                    }
                                }
                            }
                        }

                        drawRevealed(1f)
                    }
                }
            }
        }
        }
        }
    }

private fun sourceGlyphTimings(
    line: LyricLine,
    liftMode: MeloXLyricsGroupingMode,
    longToneDetectionMode: MeloXLyricsGroupingMode,
    longToneThresholdMs: Int,
): List<MeloXGlyphTiming?> {
    val result = MutableList<MeloXGlyphTiming?>(line.text.length) { null }
    var searchFrom = 0
    for (syllable in line.syllables) {
        if (syllable.text.isEmpty()) continue
        val located = line.text.indexOf(syllable.text, startIndex = searchFrom)
        val startIndex = if (located >= 0) located else searchFrom.coerceAtMost(line.text.length)
        val count = syllable.text.length.coerceAtMost(line.text.length - startIndex)
        if (count <= 0) continue
        searchFrom = startIndex + count
        val syllableDuration = max(syllable.endTimeMs - syllable.startTimeMs, 0L).toFloat()
        val characterDuration = syllableDuration / count.toFloat()
        for (local in 0 until count) {
            val offset = startIndex + local
            val start = syllable.startTimeMs + characterDuration * local
            val end = if (local == count - 1) max(syllable.endTimeMs.toFloat(), start) else start + characterDuration
            result[offset] = MeloXGlyphTiming(
                textOffset = offset,
                start = start,
                end = end,
                liftStart = start,
                liftEnd = end + UpstreamLyrics.LIFT_CONTINUATION_MS,
                syllableStart = syllable.startTimeMs.toFloat(),
                syllableEnd = syllable.endTimeMs.toFloat(),
                characterIndex = local,
                characterCount = count,
                wordStart = start,
                wordEnd = end,
                wordCharacterIndex = 0,
                wordCharacterCount = 1,
                usesWordTimingForLongTone = false,
                longToneStart = syllable.startTimeMs.toFloat(),
                longToneDuration = syllableDuration,
                longToneCharacterIndex = local,
                longToneCharacterCount = count,
                isLongTone = false,
                expansionAmount = 0f,
                glowAmount = 0f,
            )
        }
    }

    // Match TimedLyricTextBuilder: highlights preserve individual character
    // timings, while a word block supplies the optional shared lift clock.
    sourceWordBlocks(line.text).forEach { block ->
        val timedOffsets = (block.start until block.end).filter { offset ->
            result.getOrNull(offset) != null && !line.text[offset].isWhitespace()
        }
        val wordStart = timedOffsets.minOfOrNull { result[it]!!.start } ?: return@forEach
        val wordEnd = timedOffsets.maxOfOrNull { result[it]!!.end } ?: return@forEach
        val positions = timedOffsets.withIndex().associate { it.value to it.index }
        val usesWordTimingForLongTone = timedOffsets.size > 1 && timedOffsets.all { offset ->
            line.text[offset].isAsciiLatinLetter()
        }
        for (offset in block.start until block.end) {
            result[offset] = result.getOrNull(offset)?.copy(
                wordStart = wordStart,
                wordEnd = wordEnd,
                wordCharacterIndex = positions[offset] ?: max(timedOffsets.size - 1, 0),
                wordCharacterCount = max(timedOffsets.size, 1),
                usesWordTimingForLongTone = usesWordTimingForLongTone,
            )
        }
    }

    return result.map { timing ->
        timing?.let {
            val liftStart = if (liftMode == MeloXLyricsGroupingMode.Word) it.wordStart else it.start
            val liftEnd = if (liftMode == MeloXLyricsGroupingMode.Word) it.wordEnd else it.end
            val usesWordGroup = it.usesWordTimingForLongTone ||
                longToneDetectionMode == MeloXLyricsGroupingMode.Word
            val toneStart = if (usesWordGroup) it.wordStart else it.syllableStart
            val toneEnd = if (usesWordGroup) it.wordEnd else it.syllableEnd
            val toneDuration = max(toneEnd - toneStart, 0f)
            val toneIndex = if (usesWordGroup) it.wordCharacterIndex else it.characterIndex
            val toneCount = if (usesWordGroup) it.wordCharacterCount else it.characterCount
            val longTone = !line.text[it.textOffset].isWhitespace() && toneDuration >= longToneThresholdMs
            val emphasisProgress = sourceSmootherStep(
                (toneDuration - longToneThresholdMs) / (2800f - longToneThresholdMs),
            )
            it.copy(
                liftStart = liftStart,
                liftEnd = liftEnd + UpstreamLyrics.LIFT_CONTINUATION_MS,
                longToneStart = toneStart,
                longToneDuration = toneDuration,
                longToneCharacterIndex = toneIndex,
                longToneCharacterCount = toneCount,
                isLongTone = longTone,
                expansionAmount = if (longTone) .7f + .3f * emphasisProgress else 0f,
                glowAmount = if (longTone) .32f + .38f * emphasisProgress else 0f,
            )
        }
    }
}

private fun sourceGlyphVisual(
    timing: MeloXGlyphTiming,
    playbackTimeMs: Long,
    density: Float,
    reduceMotion: Boolean,
    fontScale: Float,
): MeloXGlyphVisual {
    val duration = max(timing.end - timing.start, 0f)
    val raw = when {
        playbackTimeMs < timing.start -> 0f
        playbackTimeMs >= timing.end -> 1f
        duration <= 0f -> 1f
        else -> ((playbackTimeMs - timing.start) / duration).coerceIn(0f, 1f)
    }
    val reveal = sourceHighlightRevealProgress(
        playbackTimeMs.toFloat(),
        timing.start,
        timing.end,
        raw,
        timing.isLongTone,
    )
    val lift = if (playbackTimeMs <= timing.liftStart) 0f else sourceSmootherStep(
        (playbackTimeMs - timing.liftStart) / max(timing.liftEnd - timing.liftStart, 1f),
    )
    val risePx = if (reduceMotion) 0f else
        min(max(UpstreamLyrics.FONT_SIZE_SP * fontScale * .1f, 1.5f), 6f) * density
    val envelope = if (timing.isLongTone) sourceLongToneEnvelope(
        playbackTimeMs.toFloat(),
        timing.longToneStart,
        timing.longToneDuration,
        characterIndex = timing.longToneCharacterIndex,
        characterCount = timing.longToneCharacterCount,
    ) else 0f
    val longToneScale = if (reduceMotion) 1f else
        1f + (UpstreamLyrics.LONG_TONE_MAX_SCALE - 1f) * envelope * timing.expansionAmount *
            MeloXSettingsRuntime.lyricLongToneStrength
    // Word bounce: 1.0→1.2→1.0 when a word first becomes active (Apple Music style)
    val bounceScale = if (reduceMotion || timing.isLongTone || !MeloXSettingsRuntime.lyricWordBounceEnabled) 1f else {
        val bounceProgress = raw.coerceIn(0f, 1f)
        if (bounceProgress > 0f && bounceProgress < 0.5f) {
            1f + 0.2f * (bounceProgress / 0.5f)
        } else if (bounceProgress >= 0.5f && bounceProgress < 1f) {
            1.2f - 0.2f * ((bounceProgress - 0.5f) / 0.5f)
        } else {
            1f
        }
    }
    val scale = longToneScale * bounceScale
    val glow = if (reduceMotion || !MeloXSettingsRuntime.lyricGlowEnabled ||
        (MeloXSettingsRuntime.lyricGlowLongTonesOnly && !timing.isLongTone)
    ) 0f else {
        // 归一到「满档 = 1.0」：设置里的 100% 就是满强度（见 LONG_TONE_GLOW_PEAK 注释）。
        if (timing.isLongTone) {
            envelope * timing.glowAmount / UpstreamLyrics.LONG_TONE_GLOW_PEAK
        } else {
            sourceOrdinaryGlowStrength(
                playbackTimeMs = playbackTimeMs.toFloat(),
                endMs = timing.end,
                rawProgress = raw,
            ) / UpstreamLyrics.ORDINARY_GLOW_PEAK
        }
    }
    val shakeAmplitude = if (reduceMotion || !timing.isLongTone || raw <= 0f || raw >= 1f) 0f else {
        min(.58f * density, UpstreamLyrics.FONT_SIZE_SP * fontScale * .013f * density) *
            envelope * timing.expansionAmount * MeloXSettingsRuntime.lyricLongToneStrength
    }
    val shakePhase = (playbackTimeMs - timing.longToneStart).coerceAtLeast(0f)
    val shakeX = sin(shakePhase * .016f + timing.longToneCharacterIndex * 1.73f) * shakeAmplitude
    val shakeY = sin(shakePhase * .021f + timing.longToneCharacterIndex * .91f) * shakeAmplitude * .42f
    return MeloXGlyphVisual(
        reveal = reveal,
        liftPx = risePx * lift,
        scale = scale,
        glow = glow,
        shakeXPx = shakeX,
        shakeYPx = shakeY,
    )
}

private fun sourceOrdinaryGlowStrength(
    playbackTimeMs: Float,
    endMs: Float,
    rawProgress: Float,
): Float {
    if (playbackTimeMs <= endMs) {
        val attack = sourceSmootherStep(rawProgress / .24f)
        val breath = .82f + .18f * kotlin.math.sin(Math.PI.toFloat() * rawProgress)
        return attack * breath * .55f
    }
    val tail = ((playbackTimeMs - endMs) / UpstreamLyrics.GLOW_TAIL_MS).coerceIn(0f, 1f)
    return (1f - sourceSmootherStep(tail)) * .82f * .55f
}

private data class SourceTextBlock(val start: Int, val end: Int)

private fun sourceWordBlocks(text: String): List<SourceTextBlock> {
    if (text.isEmpty()) return emptyList()
    val tokens = mutableListOf<SourceTextBlock>()
    var phraseStart = -1
    for (index in 0..text.length) {
        val isWhitespace = index == text.length || text[index].isWhitespace()
        if (!isWhitespace && phraseStart < 0) phraseStart = index
        if (isWhitespace && phraseStart >= 0) {
            val phraseEnd = index
            val iterator = BreakIterator.getWordInstance(Locale.ROOT).apply {
                setText(text.substring(phraseStart, phraseEnd))
            }
            var tokenStart = iterator.first()
            var tokenEnd = iterator.next()
            var foundWord = false
            while (tokenEnd != BreakIterator.DONE) {
                val token = text.substring(phraseStart + tokenStart, phraseStart + tokenEnd)
                if (token.any { it.isLetterOrDigit() }) {
                    tokens += SourceTextBlock(phraseStart + tokenStart, phraseStart + tokenEnd)
                    foundWord = true
                }
                tokenStart = tokenEnd
                tokenEnd = iterator.next()
            }
            if (!foundWord) tokens += SourceTextBlock(phraseStart, phraseEnd)
            phraseStart = -1
        }
    }
    if (tokens.isEmpty()) return listOf(SourceTextBlock(0, text.length))
    val sorted = tokens.sortedBy { it.start }
    return buildList {
        sorted.forEachIndexed { index, token ->
            val start = if (index == 0) 0 else sorted[index - 1].end
            val end = if (index + 1 < sorted.size) sorted[index + 1].start else text.length
            if (start < end) add(SourceTextBlock(start, end))
        }
    }
}

private fun Char.isAsciiLatinLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'

private fun sourceTimingEffectsStrength(
    line: LyricLine,
    playbackTimeMs: Long,
    focusProgress: Float,
): Float {
    val focus = focusProgress.coerceIn(0f, 1f)
    if (focus <= 0f || line.syllables.isEmpty()) return 0f
    val firstSyllableStartMs = line.syllables.minOf { it.startTimeMs }
    val activation = sourceSmootherStep(
        (playbackTimeMs - firstSyllableStartMs).toFloat() /
            UpstreamLyrics.FOCUS_COLOR_DURATION_MS.toFloat(),
    )
    return min(focus, activation)
}

private fun sourceTimedAnnotatedString(line: LyricLine, playbackTimeMs: Long) =
    buildAnnotatedString {
        line.syllables.forEach { syllable ->
            val characters = syllable.text.toCharArray()
            if (characters.isEmpty()) return@forEach
            val syllableDuration = max(syllable.endTimeMs - syllable.startTimeMs, 0L).toFloat()
            val characterDuration = syllableDuration / characters.size.toFloat()
            characters.forEachIndexed { index, character ->
                val start = syllable.startTimeMs + characterDuration * index
                val end = if (index == characters.lastIndex) {
                    max(syllable.endTimeMs.toFloat(), start)
                } else {
                    start + characterDuration
                }
                val duration = max(end - start, 0f)
                val rawProgress = when {
                    playbackTimeMs < start -> 0f
                    playbackTimeMs >= end -> 1f
                    duration <= 0f -> 1f
                    else -> ((playbackTimeMs - start) / duration).coerceIn(0f, 1f)
                }
                val isLongTone = syllableDuration >= UpstreamLyrics.LONG_TONE_THRESHOLD_MS && !character.isWhitespace()
                val revealProgress = sourceHighlightRevealProgress(
                    playbackTimeMs.toFloat(), start, end, rawProgress, isLongTone,
                )
                val liftEnd = end + UpstreamLyrics.LIFT_CONTINUATION_MS
                val liftProgress = if (playbackTimeMs <= start) 0f else {
                    sourceSmootherStep((playbackTimeMs - start) / max(liftEnd - start, 1f))
                }
                val playedRise = min(max(UpstreamLyrics.FONT_SIZE_SP * 0.1f, 1.5f), 6f)
                val longEnvelope = if (isLongTone) {
                    sourceLongToneEnvelope(
                        playbackTimeMs.toFloat(),
                        syllable.startTimeMs.toFloat(),
                        syllableDuration,
                        index,
                        characters.size,
                    )
                } else 0f
                val expansionAmount = if (isLongTone) {
                    0.7f + 0.3f * sourceSmootherStep(
                        (syllableDuration - UpstreamLyrics.LONG_TONE_THRESHOLD_MS) /
                            (2800f - UpstreamLyrics.LONG_TONE_THRESHOLD_MS),
                    )
                } else 0f
                val glyphScale = 1f +
                    (UpstreamLyrics.LONG_TONE_MAX_SCALE - 1f) * longEnvelope * expansionAmount *
                        MeloXSettingsRuntime.lyricLongToneStrength
                val glowAmount = if (isLongTone) {
                    0.32f + 0.38f * sourceSmootherStep(
                        (syllableDuration - UpstreamLyrics.LONG_TONE_THRESHOLD_MS) /
                            (2800f - UpstreamLyrics.LONG_TONE_THRESHOLD_MS),
                    )
                } else 0f
                val glowStrength = if (MeloXSettingsRuntime.lyricGlowEnabled) {
                    // 与 Canvas 路径同口径：满档归一（去掉旧的 `.7425` 额外衰减），
                    // 设置显示 100% 即满强度，60%/140% 线性缩放。
                    longEnvelope * glowAmount / UpstreamLyrics.LONG_TONE_GLOW_PEAK *
                        MeloXSettingsRuntime.lyricGlowStrength
                } else 0f
                val opacity = MeloXSettingsRuntime.lyricInactiveOpacity +
                    (1f - MeloXSettingsRuntime.lyricInactiveOpacity) * revealProgress

                val startOffset = length
                append(character)
                addStyle(
                    SpanStyle(
                        color = Color.White.copy(alpha = opacity.coerceIn(0f, 1f)),
                        fontSize = (UpstreamLyrics.FONT_SIZE_SP * glyphScale).sp,
                        fontWeight = MeloXSettingsRuntime.lyricFontWeight.composeWeight,
                        baselineShift = BaselineShift(
                            (playedRise / UpstreamLyrics.FONT_SIZE_SP) * liftProgress,
                        ),
                        shadow = if (glowStrength > 0f) {
                            // ★ 2026-10-24：半径与 [MeloXGlyphLyricText] 的 glowRadiusPx 同口径
                            //   收紧到字号 12%（原 30% 会糊成包住字的白色方块，见那边的长注释）。
                            Shadow(
                                color = Color.White.copy(alpha = glowStrength.coerceIn(0f, 1f)),
                                blurRadius = UpstreamLyrics.FONT_SIZE_SP * 0.12f,
                            )
                        } else null,
                    ),
                    startOffset,
                    length,
                )
            }
        }
    }

private fun sourceHighlightRevealProgress(
    playbackTimeMs: Float,
    startMs: Float,
    endMs: Float,
    rawProgress: Float,
    isLongTone: Boolean,
): Float {
    val regular = sourceSmootherStep(rawProgress)
    val duration = endMs - startMs
    if (!isLongTone || duration <= 460f) return regular
    val elapsed = max(playbackTimeMs - startMs, 0f)
    val attack = sourceSmootherStep(elapsed / 300f)
    val releaseStart = endMs - 160f
    val release = sourceSmootherStep((playbackTimeMs - releaseStart) / 160f)
    return (0.82f * attack + 0.08f * rawProgress + 0.10f * release).coerceIn(0f, 1f)
}

private fun sourceLongToneEnvelope(
    playbackTimeMs: Float,
    groupStartMs: Float,
    durationMs: Float,
    characterIndex: Int,
    characterCount: Int,
): Float {
    val stagger = if (characterCount > 1) {
        durationMs * 0.55f * characterIndex.toFloat() / (characterCount - 1).toFloat()
    } else 0f
    val animationDuration = max(durationMs, 1000f)
    val progress = ((playbackTimeMs - groupStartMs - stagger) / animationDuration).coerceIn(0f, 1f)
    return if (progress <= 0.5f) {
        sourceSmootherStep(progress / 0.5f)
    } else {
        1f - sourceSmootherStep((progress - 0.5f) / 0.5f)
    }
}

private fun sourceConcurrentLyricIndexes(
    lines: List<LyricLine>,
    highlightedIndex: Int,
    toleranceMs: Long = 280L,
): IntRange {
    if (highlightedIndex !in lines.indices) return IntRange.EMPTY
    val activation = sourceLineActivationTimeMs(lines[highlightedIndex])
    var first = highlightedIndex
    var last = highlightedIndex
    while (first > 0 && activation - sourceLineActivationTimeMs(lines[first - 1]) <= toleranceMs) first--
    while (last < lines.lastIndex && sourceLineActivationTimeMs(lines[last + 1]) - activation <= toleranceMs) last++
    return first..last
}

private fun sourceFocusAnimationDurationMs(index: Int, lines: List<LyricLine>): Int {
    if (index !in lines.indices) return 300
    val available = if (index + 1 < lines.size) {
        sourceLineActivationTimeMs(lines[index + 1]) - sourceLineActivationTimeMs(lines[index])
    } else null
    if (available == null || available <= 0L) return 300
    return (available * 0.30f).coerceIn(50f, 240f).roundToInt()
}

private fun sourceRemainingFocusDurationMs(
    index: Int,
    playbackTimeMs: Long,
    lines: List<LyricLine>,
): Float? {
    if (index !in lines.indices || index + 1 >= lines.size) return null
    return max((sourceLineActivationTimeMs(lines[index + 1]) - playbackTimeMs).toFloat(), 0f)
}

private fun sourceLineActivationTimeMs(line: LyricLine): Long =
    line.syllables.minOfOrNull { it.startTimeMs } ?: line.timeMs

private data class SourceCascadeLineTiming(val delayMs: Float, val durationMs: Float)

private fun sourceCascadeLineTimings(
    maximumLineOrder: Int,
    animationDurationMs: Float,
): List<SourceCascadeLineTiming> {
    val catchUpCompletionTime = animationDurationMs * MeloXSettingsRuntime.lyricCascadeCatchUpRatio
    val minimumCatchUpDuration = min(180f, animationDurationMs * 0.5f)
    return (0..maximumLineOrder.coerceAtLeast(0)).map { order ->
        if (order == 0) {
            SourceCascadeLineTiming(0f, animationDurationMs)
        } else {
            val accumulatedIncrease = order.toFloat() * (order - 1).toFloat() / 2f
            val delay = MeloXSettingsRuntime.lyricCascadeFollowingDelayMs +
                order * MeloXSettingsRuntime.lyricCascadeDelayMs +
                accumulatedIncrease * MeloXSettingsRuntime.lyricCascadeDelayIncreaseMs
            SourceCascadeLineTiming(
                delayMs = delay,
                durationMs = max(catchUpCompletionTime - delay, minimumCatchUpDuration),
            )
        }
    }
}

private fun sourceCascadeBounce(chaseOrder: Int, maximumChaseOrder: Int): Float {
    if (!MeloXSettingsRuntime.lyricCascadeBounceEnabled) return 0f
    val count = max(maximumChaseOrder + 1, 1)
    val position = chaseOrder.coerceIn(0, maximumChaseOrder) + 1
    val normalized = position.toFloat() / count.toFloat()
    val bounceScale = 1f - (1f - normalized) * MeloXSettingsRuntime.lyricCascadeBounceGradient
    return MeloXSettingsRuntime.lyricCascadeBounce * bounceScale
}

private fun sourceDistanceBlurRadius(
    distancePx: Float,
    lyricStridePx: Float,
    intensity: Float,
    focusProgress: Float,
): Float {
    val lineDistance = distancePx / lyricStridePx
    val blurProgress = max(lineDistance - 1.35f, 0f)
    val baseRadius = min(blurProgress * 3.1f, 10f)
    return baseRadius * intensity * (1f - focusProgress.coerceIn(0f, 1f))
}

private fun sourceFocusBlurRadius(
    intensity: Float,
    preceding: Boolean,
    following: Boolean,
): Float = ((if (preceding) 2.4f else 0f) + (if (following) 0.7f else 0f)) * intensity

/**
 * 「边缘邻近模糊」半径（dp）：行中点越靠近视口上/下边缘，模糊越强。
 *
 * 与 [sourceDistanceBlurRadius]（按离焦点行的行距衰减）正交 —— 那条在焦点靠上时
 * （横屏 0.30）够不到边缘，所以边缘处必须按**行到边的像素距离**单独给。详见
 * [MeloXLandscapeLyricsEdgeBlurMax] 的注释。
 *
 * ⚠ 输入是行自身的 [rowMidYpx]（滚动时逐帧连续变化）⇒ 结果是连续函数，
 * **不会**出现「行滚进某个固定 y 区间突然变糊」的跳变。
 *
 * @param rowMidYpx 行中点在歌词视口内的 y（px）
 * @param viewportHeightPx 歌词视口高（px）
 * @param topBandPx 顶部渐隐带高（0 = 不参与）
 * @param bottomBandPx 底部渐隐带高（0 = 不参与）
 * @param maxBlurDp 贴到屏幕边时的模糊半径（dp）
 */
private fun sourceEdgeProximityBlur(
    rowMidYpx: Float,
    viewportHeightPx: Int,
    topBandPx: Float,
    bottomBandPx: Float,
    maxBlurDp: Float,
): Float {
    if (viewportHeightPx <= 0 || maxBlurDp <= 0f) return 0f
    val bottomBandStart = viewportHeightPx - bottomBandPx
    val t = when {
        topBandPx > 0f && rowMidYpx < topBandPx -> (topBandPx - rowMidYpx) / topBandPx
        bottomBandPx > 0f && rowMidYpx > bottomBandStart ->
            (rowMidYpx - bottomBandStart) / bottomBandPx
        else -> 0f
    }.coerceIn(0f, 1f)
    // 量化到 0.25dp：`MeloXGlyphLyricText` 是按 `remember(softBlurDp)` 重建模糊蒙版的，
    // 未量化的连续值会让滚动中的行**逐帧新建 BlurMaskFilter**。0.25dp 的台阶视觉上
    // 不可辨，却能把重建次数降一个数量级。（同理 distanceBlur 天然按行离散，才不会逐帧重建。）
    return (maxBlurDp * sourceSmootherStep(t) * 4f).roundToInt() / 4f
}

/**
 * 把内容在上下边缘 mask 到透明 —— 等价 Apple Music 的 `container edge fade`
 * （Web 版对应 CSS 变量 `--lyrics-linear-gradient`），**取代硬裁**：
 * 行不是"被切掉"，而是在抵达边界之前就淡成透明。
 *
 * ⚠ `BlendMode.DstIn` 只对**离屏图层内部**有意义，必须与
 * `CompositingStrategy.Offscreen` 配对使用 —— 否则 DstIn 会落到父级画布上、
 * 把整屏（含左侧大封面）一并挖空。调用点已按此设置 `compositingStrategy`。
 *
 * @param topFadePx 顶部渐隐带高度：0 = 面板顶，topFadePx = 完全显形处。
 * @param bottomFadePx 底部渐隐带高度（自**归零点**往上量）。
 * @param bottomTerminalPx 底部**归零点**距视口底的距离（0 = 归零点即视口底，旧行为）。
 *   该点以下恒透明。竖屏用它把"完全淡出"提前到**底部控件条的顶沿**：控件条本身是
 *   `meloXBackdropBlur`（仅 8% 黑 + 模糊），白色高亮歌词会从中渗出（真机截图实证
 *   「0:00 进度条那一带能看见 `(4665,777)` 的虚影」）⇒ 不能靠它"挡"，必须让歌词在
 *   该区间本来就透明。纵向渐隐是**线性**的：若把归零点留在视口底，控件条恰好落在
 *   渐隐带**起点**（alpha = 1），无论带宽多大都挡不住 —— 所以必须能上移归零点。
 */
private fun Modifier.lyricsEdgeFade(
    topFadePx: Float,
    bottomFadePx: Float,
    bottomTerminalPx: Float = 0f,
): Modifier =
    drawWithContent {
        drawContent()
        if (topFadePx <= 0f && bottomFadePx <= 0f) return@drawWithContent
        val h = size.height
        if (h <= 0f) return@drawWithContent
        val topStop = (topFadePx / h).coerceIn(0f, .48f)
        // bottomTerminalPx = 0 时两式与旧实现逐值相同（横屏走这条 ⇒ 观感零变化）。
        val bottomEnd = if (bottomTerminalPx > 0f) {
            (1f - bottomTerminalPx / h).coerceIn(.52f, 1f)
        } else {
            1f
        }
        val bottomStop = if (bottomTerminalPx > 0f) {
            (bottomEnd - bottomFadePx / h).coerceIn(0f, bottomEnd)
        } else {
            (1f - bottomFadePx / h).coerceIn(.52f, 1f)
        }
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

private fun sourceDistanceOpacity(
    distancePx: Float,
    lyricStridePx: Float,
    dimAmount: Float,
    focusProgress: Float,
): Float {
    val lineDistance = distancePx / lyricStridePx
    val base = when {
        lineDistance <= 1f -> 1f - lineDistance * 0.44f
        lineDistance <= 2f -> 0.56f - (lineDistance - 1f) * 0.22f
        else -> max(0.12f, 0.34f - (lineDistance - 2f) * 0.07f)
    }
    val distanceOpacity = 1f - (1f - base) * dimAmount
    return distanceOpacity + (1f - distanceOpacity) * focusProgress.coerceIn(0f, 1f)
}

private fun sourceEmphasis(focusProgress: Float, dimAmount: Float): Float {
    val unfocused = 1f - (1f - 0.52f) * dimAmount
    return unfocused + (1f - unfocused) * focusProgress.coerceIn(0f, 1f)
}

private fun sourceSmootherStep(value: Float): Float {
    val p = value.coerceIn(0f, 1f)
    return p * p * p * (p * (p * 6f - 15f) + 10f)
}

private object SourceSmoothStepEasing : Easing {
    override fun transform(fraction: Float): Float {
        val p = fraction.coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }
}

private class SourceSpringEasing(private val bounce: Float) : Easing {
    override fun transform(fraction: Float): Float {
        val t = fraction.coerceIn(0f, 1f)
        if (bounce <= 0.0001f) return SourceSmoothStepEasing.transform(t)
        val damping = (1f - bounce.coerceIn(0f, 0.95f) * 0.72f).coerceIn(0.18f, 0.999f)
        val omega = 9.5f
        val damped = omega * sqrt(max(1f - damping * damping, 0.0001f))
        val envelope = exp((-damping * omega * t).toDouble()).toFloat()
        val raw = 1f - envelope * (
            kotlin.math.cos((damped * t).toDouble()).toFloat() +
                (damping / sqrt(max(1f - damping * damping, 0.0001f))) *
                sin((damped * t).toDouble()).toFloat()
            )
        val endEnvelope = exp((-damping * omega).toDouble()).toFloat()
        val endRaw = 1f - endEnvelope * (
            kotlin.math.cos(damped.toDouble()).toFloat() +
                (damping / sqrt(max(1f - damping * damping, 0.0001f))) *
                sin(damped.toDouble()).toFloat()
            )
        return if (abs(endRaw) > 0.0001f) raw / endRaw else raw
    }
}
