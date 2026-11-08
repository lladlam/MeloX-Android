package com.lladlam.melox.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.tween

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
// ── 底部 chrome 明暗切换（2026-09-25）─────────────────────────────────
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.lerp
// ★ 底栏亮度采样源（2026-10-19）：录制层 + 换取采样源用的 provider。
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.SideEffect
import com.lladlam.melox.ui.glass.BottomBarToneTheme
import com.lladlam.melox.ui.glass.LocalBottomBarLuminanceLayer
import com.lladlam.melox.ui.glass.LocalBottomBarTone
import com.lladlam.melox.ui.glass.bottomBarTone
import com.lladlam.melox.ui.glass.darkness
import com.lladlam.melox.ui.glass.rememberBottomBarToneState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.zIndex
import com.lladlam.melox.core.account.rememberNeteaseSessionStore
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.BuildConfig
import com.lladlam.melox.R
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.provider.MusicProviderSelectionStore
import com.lladlam.melox.ui.account.MeloXAccountActivity
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.lladlam.melox.ui.library.LibraryScreen
import com.lladlam.melox.core.provider.kugou.KugouLiteDailyCheckIn
import com.lladlam.melox.ui.legal.MELOX_LEGAL_VERSION
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigConsent
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigSource
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigRuntime
import com.lladlam.melox.core.remoteconfig.MeloXRemoteNotice
import com.lladlam.melox.core.remoteconfig.MeloXRemoteNoticeStore
import com.lladlam.melox.ui.legal.MeloXRemoteNoticeDialog
import com.lladlam.melox.ui.messages.MeloXMessagesActivity
import com.lladlam.melox.ui.podcast.MeloXPodcastScreen
import com.lladlam.melox.ui.cloud.MeloXCloudMusicScreen
import com.lladlam.melox.ui.discovery.MeloXExploreScreen
import com.lladlam.melox.ui.discovery.MeloXHomeScreen
import com.lladlam.melox.ui.glass.LocalMeloXBackdrop
import com.lladlam.melox.ui.glass.MeloXGlassPipelinePrewarm
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXSymbolVariant
import com.lladlam.melox.ui.glass.MeloXSystemColors
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.theme.isMeloXDarkTheme
import com.lladlam.melox.ui.animation.BottomBarCollapseSpec
import com.lladlam.melox.ui.animation.BottomBarExpandSpec
import com.lladlam.melox.ui.animation.MeloXSprings
import com.lladlam.melox.ui.animation.NavExpandLeftShare
import com.lladlam.melox.ui.animation.meloXContentEnter
import com.lladlam.melox.ui.animation.meloXContentExit
import com.lladlam.melox.ui.animation.MeloXPlayerRecede
import com.lladlam.melox.ui.animation.meloXPageEnter
import com.lladlam.melox.ui.animation.meloXPageExit
import com.lladlam.melox.ui.animation.MeloXSystemScale
import com.lladlam.melox.ui.animation.sprungFrac
import com.lladlam.melox.ui.glass.BOTTOM_GLASS_SURFACE_ALPHA
import com.lladlam.melox.ui.glass.bottomGlassFallbackColor
import com.lladlam.melox.ui.glass.bottomGlassSurfaceColor
import com.lladlam.melox.ui.glass.bottomLiquidGlassTint
import com.lladlam.melox.ui.glass.finiteOrZero
import com.lladlam.melox.ui.glass.meloXLiquidBottomBar
import com.lladlam.melox.ui.glass.meloXLiquidCaptureLayer
import com.lladlam.melox.ui.glass.meloXLiquidTabSelection
import com.lladlam.melox.ui.glass.meloXGlassBackdrop
import com.lladlam.melox.ui.glass.publicdemo.PublicDampedDragAnimation
import com.lladlam.melox.ui.glass.publicdemo.PublicInteractiveHighlight
import com.lladlam.melox.ui.layout.rememberMeloXWindowInfo
import com.lladlam.melox.ui.layout.MeloXDetailPageOwner
import com.lladlam.melox.ui.layout.MeloXDetailPaneState
import com.lladlam.melox.ui.layout.MeloXListDetailLayout
import com.lladlam.melox.ui.layout.MeloXPositionedChrome
import com.lladlam.melox.ui.settings.meloXChromeScrollCoupled
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import com.lladlam.melox.ui.layout.meloXUsesListDetail
import androidx.compose.ui.platform.LocalWindowInfo
import com.lladlam.melox.ui.player.MeloXImmersivePlaybackEffect
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.lladlam.melox.ui.player.MeloXIOSMiniPlayer
import com.lladlam.melox.ui.player.MeloXProviderLyricsLoader
import com.lladlam.melox.ui.player.MeloXIOSNowPlayingSharedHost
import com.lladlam.melox.ui.player.meloXPlayerRecedeSpec
import com.lladlam.melox.ui.player.meloXPlayerTweenDurationMillis
import com.lladlam.melox.ui.player.rememberMeloXDisplayCornerPx
import com.lladlam.melox.ui.player.rememberMeloXDisplayCornerInfo
import com.lladlam.melox.ui.player.MeloXSmoothCorner
import com.lladlam.melox.ui.player.MeloXPlayerRecedeCompatFallbackDp
import com.lladlam.melox.ui.player.meloXPlayerTransitionDurationMillis
import com.lladlam.melox.ui.player.rememberMeloXPlaybackUiState
import com.lladlam.melox.ui.player.MeloXAudioOutputDialog
import com.lladlam.melox.ui.player.MeloXChromeOcclusionGate
import com.lladlam.melox.ui.player.meloXChromeRecedeLayer
import com.lladlam.melox.ui.player.meloXRecedeScaleX
import com.lladlam.melox.ui.player.meloXRecedeScaleY
import com.lladlam.melox.ui.player.meloXRecedeSinkPx
import com.lladlam.melox.ui.player.MeloXShellGeom
import com.lladlam.melox.ui.provider.ProviderExploreScreen
import com.lladlam.melox.ui.provider.ProviderHomeScreen
import com.lladlam.melox.ui.provider.ProviderLibraryScreen
import com.lladlam.melox.ui.provider.ProviderSearchScreen
import com.lladlam.melox.ui.provider.ProviderSettingsHub
import com.lladlam.melox.ui.recognition.MeloXRecognitionOverlay
import com.lladlam.melox.ui.provider.MeloXProviderServicesActivity
import com.lladlam.melox.ui.search.SearchScreen
import com.lladlam.melox.ui.search.MeloXSearchLaunchBus
import com.lladlam.melox.ui.settings.MeloXSettingsDetailActivity
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.core.network.MeloXSearchKind
import com.lladlam.melox.core.network.NeteaseClipboardLink
import com.lladlam.melox.core.network.NeteaseClipboardTarget
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.update.MeloXRelease
import com.lladlam.melox.core.update.MeloXUpdateClient
import com.lladlam.melox.playback.PlaybackCommands
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

enum class AppTab(@StringRes val titleRes: Int) {
    Home(R.string.tab_home),
    Explore(R.string.tab_explore),
    Library(R.string.tab_library),
    Podcasts(R.string.tab_podcasts),
    Downloads(R.string.tab_downloads),
    Cloud(R.string.tab_cloud),
    Settings(R.string.tab_settings),
    Search(R.string.tab_search),
}


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MeloXApp(
    openNowPlayingRequest: Int = 0,
    clipboardLinkRequest: String? = null,
    onClipboardLinkConsumed: () -> Unit = {},
    playbackConnectionEnabled: Boolean = true,
) {
    val hostContext = LocalContext.current
    DisposableEffect(hostContext) {
        val hostId = System.identityHashCode(hostContext)
        if (com.lladlam.melox.BuildConfig.DEBUG) {
            android.util.Log.i("MeloXLifecycle", "MeloXApp mounted host=$hostId")
        }
        onDispose {
            if (com.lladlam.melox.BuildConfig.DEBUG) {
                android.util.Log.i("MeloXLifecycle", "MeloXApp disposed host=$hostId")
            }
        }
    }
    val context = hostContext.applicationContext
    val detailPaneState = (hostContext as? MeloXDetailPageOwner)?.detailPaneState
        ?: remember { MeloXDetailPaneState() }
    // ★ 2026-10-27：判据从 `LocalWindowInfo.current.containerSize` 改为 `LocalConfiguration`
    //   （**必须**与 `MeloXListDetailLayout` 内同名函数同源）。转屏时 containerSize 由
    //   WindowManager metrics 异步回填、可能晚 1~2 帧，而布局约束当帧生效 ⇒ 会出现
    //   「页面已分栏、底栏仍按整屏宽铺开」的一帧 —— 用户报的「竖转横底栏长变短」。
    //   推导见 `meloXUsesListDetail(Configuration)` 的 kdoc。
    val windowConfiguration = LocalConfiguration.current
    val listDetailMode = meloXUsesListDetail(windowConfiguration, MeloXSettingsRuntime.splitScreenEnabled)
    var selectedSource by remember {
        mutableStateOf(MusicProviderSelectionStore.selectedSource(context))
    }
    val sourceSelectionRevision by MusicProviderSelectionStore.changes.collectAsState()
    var selectedSourceId by remember { mutableStateOf(MusicProviderSelectionStore.selectedSourceId(context)) }
    LaunchedEffect(sourceSelectionRevision) {
        selectedSourceId = MusicProviderSelectionStore.selectedSourceId(context)
        selectedSourceId.builtIn?.let { selectedSource = it }
    }
    val initialTab = remember(context) {
        runCatching {
            AppTab.valueOf(
                if (MeloXSettingsRuntime.rememberLastTab) {
                    MeloXSettingsPreferences.string(context, "general_last_tab", MeloXSettingsRuntime.defaultTab)
                } else MeloXSettingsRuntime.defaultTab,
            )
        }.getOrDefault(AppTab.Home)
    }
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    var recognitionVisible by rememberSaveable { mutableStateOf(false) }
    fun launchNeteaseLogin(returnTab: AppTab) {
        detailPaneState.openLogin(hostContext) { selectedTab = returnTab }
    }
    var tabBarMinimized by rememberSaveable { mutableStateOf(false) }
    var scrollAccumulator by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    // 底栏 tab / 搜索键 / 收缩态展开：选中类触感（轻 tick）。
    val tabHaptic = meloXHaptic()
    LaunchedEffect(tabBarMinimized) {
        if (com.lladlam.melox.ui.settings.MeloXSettingsRuntime.hapticFeedbackEnabled)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(MeloXSettingsRuntime.disableAutomaticTabBarShrink) {
        if (MeloXSettingsRuntime.disableAutomaticTabBarShrink) tabBarMinimized = false
    }
    var libraryModalVisible by remember { mutableStateOf(false) }
    var onboardingLoginRequested by rememberSaveable { mutableStateOf(false) }
    var onboardingPage by rememberSaveable {
        mutableStateOf(if (MeloXSettingsPreferences.boolean(context, "onboarding_completed", false)) -1 else 0)
    }
    var availableUpdate by remember { mutableStateOf<MeloXRelease?>(null) }
    val remoteConfigStatus by MeloXRemoteConfigRuntime.status.collectAsState()
    var pendingRemoteNotice by remember { mutableStateOf<MeloXRemoteNotice?>(null) }
    var cloudControlChoicePending by remember {
        mutableStateOf(!MeloXRemoteConfigConsent.choiceMade(context))
    }
    LaunchedEffect(remoteConfigStatus, cloudControlChoicePending) {
        pendingRemoteNotice = remoteConfigStatus.config.notice?.takeIf {
            !cloudControlChoicePending &&
                MeloXRemoteConfigConsent.enabled(context) &&
                remoteConfigStatus.source == MeloXRemoteConfigSource.VerifiedRemote &&
                MeloXRemoteNoticeStore.shouldShow(context, it)
        }
    }
    LaunchedEffect(Unit) {
        if (MeloXSettingsPreferences.boolean(context, "onboarding_completed", false)) {
            withContext(Dispatchers.IO) {
                runCatching { KugouLiteDailyCheckIn.run(context) }
            }
        }
    }
    var heartModeLaunchAttempted by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        selectedSourceId = MusicProviderSelectionStore.selectedSourceId(context)
        val persistedSource = MusicProviderSelectionStore.selectedSource(context)
        if (persistedSource != selectedSource) {
            selectedSource = persistedSource
            tabBarMinimized = false
            libraryModalVisible = false
            heartModeLaunchAttempted = false
        }
    }
    val playbackState = rememberMeloXPlaybackUiState(connectionEnabled = playbackConnectionEnabled)
    val playerTransitionState = rememberSaveable(
        saver = androidx.compose.runtime.saveable.Saver<SeekableTransitionState<Boolean>, Boolean>(
            save = { it.targetState },
            restore = { SeekableTransitionState(it) },
        ),
    ) { SeekableTransitionState(false) }
    val playerTransition = rememberTransition(
        transitionState = playerTransitionState,
        label = "melox-player-transition",
    )
    // ── 后方页面下沉的主时间线（Apple Music 口径，2026-09-28）────────────────
    //   与全屏播放器的外壳 morph / 内容层**同源**（同一个 SeekableTransitionState、
    //   同一条 `tween(D, MeloXPlayerAppleEasing)`）⇒ 三者全为**单层**缓动、
    //   逐帧严格相等；手势 seekTo 拖拽时页面缩放与外壳 morph 也严格同拍。
    //   只喂 graphicsLayer（draw 期），缩放不触发重排。
    //   ⚠⚠ 2026-10-04 两次教训（详见 `MeloXPlayerTransitionSpec.kt` 文件头
    //   「三条时间线的同源铁律」）：本 spec 与 master / 几何 spec **必须同族**。
    //   中间试过把三处一起换成 ζ=1 弹簧（想「统一」）⇒ 反而让**全链双层**、
    //   95% 提前到 101ms、尾段空转 139ms，用户报「效果更差」，已整条回退到线性 tween。
    val playerRootFraction by playerTransition.animateFloat(
        transitionSpec = { meloXPlayerRecedeSpec(initialState, targetState) },
        label = "melox-player-root-recede",
    ) { open -> if (open) 1f else 0f }

    // ★★ 2026-11-06（无真机轮 · 第 3 轮）：播放页是否**已经铺满整屏**（底页 100% 被盖住）。
    //   用途见 `AnimatedContent` 的 modifier：为 true 时底页整棵停止绘制。
    //   ⚠ 必须包 `derivedStateOf` —— `playerRootFraction` 在整段 morph 里逐帧变化，
    //     直接 `>= 1f` 会把整个 `MeloXApp` 拖成每帧重组（本项目屡次踩过的坑）。
    //   ⚠ 判据不能用 `targetState`：收起刚启动时 target 已翻 false 但外壳还铺满，
    //     用 target 会提前把底页画出来（虽然被盖住看不见，但白付一整页 replay）。
    val playerFullyCoversPage by remember {
        derivedStateOf { playerRootFraction >= 1f }
    }

    // ★ 播放器下沉弹簧 —— **双拍节奏**（2026-10-06，与底栏 tab 同款表现形式）：
    //   拍 1（按下/松手瞬间，`Confirm` 中等）："啪"——确认已激活下沉；
    //   拍 2（弹簧落定，`LongPress` 最重）："咚"——重重落定。
    //   ⚠ 只改表现形式，不改强度；两条下沉路径（系统返回 / 下拉手势收起）共用。
    //   master spec 是 `playerSettleMasterSpec()`（spring ζ=1，stiffness 由设置时长反解），
    //   ζ=1 = 临界阻尼 ⇒ 无过冲，fraction 单调趋近 target；落定 ≈ 设置时长，故延迟该时长后拍 2。
    val playerSpringThumpEngage = rememberSpringThump(HapticFeedbackType.Confirm)
    val playerSpringThumpLand = rememberSpringThump(HapticFeedbackType.LongPress)
    // ★ 打开播放页的触感 = **连续双拍**（2026-10-15 第二轮定案）。
    //
    // 原版的「双拍」真身：**`MeloXIOSLyricsPanel` 首次挂载**时，列表边沿观察者
    //   `snapshotFlow{ canScrollForward to canScrollBackward }` 对**两个条件各震一记**
    //   `LongPress`；挂载瞬间列表停在 (0,0)、两者皆 `false` ⇒ **同一帧连发两记**
    //   ⇒ 系统顺序播放出两下 = 用户听到的「双拍」。
    //   面板在旧口径下于**转场开始**就组合 ⇒ 这两下恰好落在点击瞬间；
    //   2026-10-15「常驻重层延后组合」把面板首帧组合推到转场结束 ⇒ 整体后移
    //   ⇒ 用户报「点击瞬间有、性能版打开完之后才有」。
    //
    // ⚠⚠ 修法要点（踩过两次）：
    //   原版这两下是**紧挨着**的（同帧 / 相隔一两帧，用户听成「连续的」），
    //   **不是**「点击一拍 + 展开落定一拍」——落定在数百毫秒之后，用户会直接判成「不连续」。
    //   ⇒ 这里显式复刻：两记 `LongPress` 都钉在**点击帧**、只隔约 60ms，
    //     不再依赖歌词面板的挂载时机与它的条件个数。力度沿用 `LongPress`（与原版一致，不改成 Confirm）。
    //   误触发已在 `MeloXIOSLyricsPanel` 侧去掉（初值 `true`→`null`、跳过首次发射），保证不叠成三下。
    val playerOpenThump = rememberSpringThump(HapticFeedbackType.LongPress)

    // 下沉进度（0 静止 / 1 完全沉下）。**只在 draw 期调用**（graphicsLayer /
    // drawBehind / drawWithContent 的 lambda 里读 playerRootFraction）⇒ 每帧只重绘，
    // 不重组 MeloXApp 这棵大树、也不重排页面。
    // ⚠ **线性映射，不再套 smoothStep**（2026-09-28 三次定档）：套窗口会让下沉提前
    //   14% 停住、而外壳还在动 ⇒ 用户报障「没有跟动画同步」。线性 + [ScaleStart]=0 /
    //   [ScaleEnd]=1 时 f ≡ playerRootFraction，与外壳 morph 是**同一条** master 曲线。
    //   ⚠ 关闭方向的过冲回弹现在由同一条 master spec（iOS 回弹曲线）直接给出，f 会略为负。
    val recedeMasterFraction: () -> Float = {
        if (MeloXSettingsRuntime.reduceMotion) {
            0f
        } else {
            val span = (MeloXPlayerRecede.ScaleEnd - MeloXPlayerRecede.ScaleStart)
                .takeIf { it > 0f } ?: 1f
                    // ⚠ 只裁上界（1）：关闭过冲时 f<0（页面缩放 >1、被屏裁掉）必须放行，正是回弹来源。
                    ((playerRootFraction - MeloXPlayerRecede.ScaleStart) / span).coerceAtMost(1f)
        }
    }
    var playerArtworkPageUsesScale by remember { mutableStateOf(false) }
    // ── 系统屏幕圆角（Apple Music 口径，2026-09-28 十次定档）────────────────
    //   页面与播放器外壳**共用同一个值**（`rememberMeloXDisplayCornerInfo()`，内部轮询到
    //   真取到为止）。用户要求「再次确认获取系统圆角」⇒ debug 包在屏幕上打读数
    //   （runtime = WindowInsets 原始读数，used = 实际渲染采用值），release 不打。
    //   真值参考（iQOO 15）：`dumpsys window` 的 `mRoundedCorners radius=184`（四角同值）。
    val density = LocalDensity.current
    val displayCorner = rememberMeloXDisplayCornerInfo()
    val displayCornerPx = displayCorner.usedPx
    // ★★ 2026-10-26（打开动画性能，B 方案）：「外壳是否已完全盖住底部 chrome」的唯一判定器。
    //   读者见 `MeloXChromeOcclusion.kt` 文件头：外壳把底栏整块压在下面时，底栏那批液态玻璃
    //   仍在每帧整屏采样背板 + 跑 blur/lens，`bottomChromeBackdrop` 录制点也仍在每帧整屏重录
    //   （18.25MB/帧上传）—— 全部为零可见收益的支出。
    //   ⚠ 判据是**几何**的（外壳真实 bounds vs chrome 玻璃面），不是进度阈值：
    //     外壳起点随底栏设置变（竖屏 / 横屏 / 双栏 / 侧栏 / 内联迷你栏），写死阈值必然在
    //     某种配置下早判 ⇒ 玻璃先关掉、外壳还没盖上去 ⇒ 露馅。
    val chromeOcclusion = remember { MeloXChromeOcclusionGate() }
    // ★★ 2026-10-26 第二次实测修正：**判据的驱动点必须是 bounds 每帧求值处**。
    //   原来挂在「画黑底那个 Box」的 `drawBehind` 上（靠它每帧读 `recedeFraction()` 失效），
    //   真机实测**整段转场只跑了 2 次**（mxdiag 里 `chromeOcclProbe` 只有 2 行）⇒ 闸门
    //   既翻不早、收起方向也一次都没翻回 false（靠 `reset()` 兜到最后）。
    //   ⇒ 改为由 `MeloXShellGeom.record()`（`sharedBounds` 每帧求 bounds 的唯一位置）直接
    //     回调。那里是**确定性每帧**的，与任何绘制/失效优化无关。
    //   ⚠ 代价：`evaluate()` 在 layout 期写 `occluded`（翻 true 时触发一次重组）。
    //     一次开合最多 2 次，可接受；稳态不动 ⇒ 零开销。
    DisposableEffect(chromeOcclusion) {
        MeloXShellGeom.onFrame = { chromeOcclusion.evaluate() }
        onDispose { MeloXShellGeom.onFrame = null }
    }
    val windowSizePx = LocalWindowInfo.current.containerSize
    SideEffect {
        // 幂等写普通字段（不是 State）：窗口尺寸 / density / 系统圆角都只在配置变化时变。
        // 放在 `SideEffect` 而不是组合体里，是为了不与「组合期禁副作用」那条纪律打架 ——
        // 这三行本来就只影响绘制期判据，不参与任何布局/内容。
        chromeOcclusion.windowSize = windowSizePx
        chromeOcclusion.density = density.density
        chromeOcclusion.peakCornerPx = displayCornerPx
    }
    val playerScope = rememberCoroutineScope()
    var playerTransitionJob by remember { mutableStateOf<Job?>(null) }
    // ★ 2026-10-15 配套「转场可打断」：**关闭落定那记「咚」的待发任务**。
    //   关闭若被「点按抢回播放页」（或任何新的主状态变更）打断，这条延迟任务**必须撤销** ——
    //   否则它会带着旧计时在**打开途中**炸出一记无主的「咚」（听感上就是打开时莫名多了一下）。
    var playerSettleThumpJob by remember { mutableStateOf<Job?>(null) }
    // ⚠⚠⚠ 2026-10-04 修「下沉回弹偶尔消失」：`BoundsTransform` 生成 spec 时会**实时读取**
    //   方向判据（`targetState == PostExit`），而读的那一刻与动画起跑的那个合成帧之间并没有
    //   顺序保证 ⇒ 偶尔读到未翻转的值 ⇒ 那一轮 keyframes 被退化成普通 tween ⇒ **回弹丢失**。
    //   ⇒ 这里在**动画启动之前**同步写下一个方向 intent（「本次主状态推进是关闭方向吗」），
    //     保证任何一次 bounds spec 求值都能读到确定的方向 —— 不依赖 targetState 的翻转时机。
    //   ⚠ 必须在**每个**主状态变更点都写（openPlayer / closePlayer / intent 打开 /
    //     手势 seek 收起 / 手势释放 settle），漏一处就会留一条漏回弹的路径。
    var playerClosingIntent by remember { mutableStateOf(false) }
    // ★ 2026-10-15 修「打断动画期间点底栏误触抢回」：mini 播放器在 window 中的命中矩形，
    //   由 `MeloXIOSMiniPlayer` 实时上报、供 `MeloXIOSNowPlayingSharedHost` 限定 reopen 触发范围。
    val miniPlayerHitRect = remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // ⚠⚠⚠ 2026-10-06：`expansionProgress`（内容层进度）的**动画目标值**镜像 ——
    //   「本次推进的**目标方向**是打开吗」。**必须在 `animateTo / seekTo` 之前同步写**。
    //   为什么不能直接用 `playerTransitionState.targetState`：它的更新晚于动画启动
    //   （实测 settle 后内容层仍朝关闭侧跑了约 **77ms**：p 0.804 → 0.661 → 回升，
    //     而同期 bounds 的 f 已从 0 正常爬回 1）⇒ 那记反向就是用户看到的「下沉」。
    //   为什么不能直接用 `closingIntent`：拖拽期间它恒为 false（不变化）⇒ 动画被判
    //   「已完成」⇒ **不参与 `SeekableTransitionState` 的 seek** ⇒ 拖拽完全不跟手
    //   （v1 实测 p 恒 1.000）。所以拖拽时这里必须是 **false**（相对上一稳态的 true
    //   产生一次目标变化，动画才会保持活跃、才会被 seek 驱动）。
    //   取值：
    //   · 拖拽中   → false（朝关闭；同时提供那次"目标变化"以保住 seek 跟手）
    //   · 回弹/打开 → true （目标恒 1f ⇒ 松手后不再朝 0 跑）
    //   · 松手收起 → false（目标 0f）
    var playerTransitionTargetOpen by remember { mutableStateOf(true) }
    val clipboardTarget = remember(clipboardLinkRequest, selectedSource) {
        if (selectedSource == MusicSource.Netease) {
            clipboardLinkRequest?.let(NeteaseClipboardLink::parse)
        } else {
            null
        }
    }
    val openPlayer: () -> Unit = {
        if (playbackState.hasMedia) {
            // ★ 打开触感 = **连续双拍**（复刻原版「歌词面板首次挂载时同帧连发两记 LongPress」）：
            //   两记都钉在**点击瞬间**、只隔约 60ms —— ⚠ 不是「拍 1 在点击、拍 2 在落定」。
            //   说明见上方 `playerOpenThump` 声明处。
            //   60ms = 干净的双击节奏（≲20ms 会被听成一记，间隔过大则成「不连续」）。
            playerOpenThump()                     // 双拍 ①
            playerScope.launch {
                kotlinx.coroutines.delay(60L)
                playerOpenThump()                 // 双拍 ②（紧接，连续）
            }
            playerClosingIntent = false
            playerTransitionTargetOpen = true
            // ★ 转场可打断：本次是「打开」（含关闭途中被点按抢回）⇒ 撤销关闭那条尚未发出的
            //   落定「咚」，避免它在打开途中多震一记。
            playerSettleThumpJob?.cancel()
            playerSettleThumpJob = null
            playerTransitionJob?.cancel()
            playerTransitionJob = playerScope.launch {
                playerTransitionState.animateTo(
                    targetState = true,
                    // ⚠ 2026-10-04 二次回退：与关闭**同一函数**（线性 tween master）⇒
                    //   打开/关闭互为时间镜像，且全链只有**一层** `MeloXPlayerAppleEasing`。
                    //   试过改成 ζ=1 弹簧（当时为修「打开太线性」）⇒ 几何/圆角/内容/背景页
                    //   全部变成双层，95% 提前到 101ms、尾段空转 139ms ⇒ 用户报「效果更差」。
                    animationSpec = playerOpenFractionSpec(),
                )
            }
        }
    }
    val closePlayer: () -> Unit = {
        playerClosingIntent = true
        playerTransitionTargetOpen = false
        playerTransitionJob?.cancel()
        // ★ 双拍下沉触感（与底栏同款）：拍 1 立即「啪」激活，拍 2 落定时「咚」。
        //   单独开一条 coroutine 跑延迟的拍 2，不阻塞动画启动。
        //   spring ζ=1，99% 落定 ≈ 设置时长。延迟设置时长后震落定拍。
        playerSpringThumpEngage()                  // 拍 1：返回/关闭瞬间「啪」——激活下沉
        playerSettleThumpJob?.cancel()
        playerSettleThumpJob = playerScope.launch {
            // 🔴🔴 2026-10-29 00:3x：曲线整文件换回 fork/0.7.0 版后，这两个符号已不存在
            //   ⇒ `settleMs` 回到 0.7.0 原样 `meloXPlayerTweenDurationMillis()`（唯一时长出口）。
            val settleMs = meloXPlayerTweenDurationMillis().toLong()
            kotlinx.coroutines.delay(settleMs)
            playerSpringThumpLand()                // 拍 2：弹簧落定「咚」——落定
        }
        playerTransitionJob = playerScope.launch {
            playerTransitionState.animateTo(
                targetState = false,
                // ⚠⚠ 2026-10-06 **当前口径**：master = [playerSettleMasterSpec]
                //   （spring ζ=1，stiffness 由**设置时长**反解）—— 四条路径同源，
                //   理由与历史两次回退的辨析见 [playerOpenFractionSpec] 的 KDoc。
                //   ⚠ **别写死 stiffness**：`spring` 不读设置时长 ⇒ 时长设置会**完全失效**
                //     （用户报障「播放页打开动画设置…调节没反应」）。
                //   ⚠ 关闭尾段的下沉窗口长度仍由 `MeloXPlayerSinkWindowStart` 决定
                //     （纯常量），**不要**再拿 master 曲线去补偿。
                animationSpec = playerCloseFractionSpec(),
            )
        }
    }
    // ★ 帧率：播放页转场（morph）**全程锁定高刷**（2026-10-16 修「120-60-120」）。
    //   症状：点开 / 收起播放页，帧率 120→60→120，中段掉到 60Hz。
    //   根因（已定位）：morph 在 `SharedTransitionLayout` 的 overlay 里飞行，主 decor
    //   那段不合成，面板会把 `setFrameRate(120)` 请求丢回 60Hz；而 `applyIntent` 有
    //   「意图不变则不下发」早退 —— 意图一直 High ⇒ 早退把重钉请求吞了，轮询再也钉不回去，
    //   只能等意图先掉 Low 再回 High 才重新生效 ⇒ 120-60-120。
    //   修法：转场进行中（`currentState != targetState`）置控制器级
    //   `playerTransitionActive=true` —— 强制 `desiredIntent=High` 且**绕过早退**每 tick
    //   重发 `setFrameRate(120)`；落定即关，交回常规判据。循环内每拍重断言 true，
    //   抗「快速重开导致旧 effect finally 把标志误关」的竞态。
    //   ⚠ 只读布尔 `currentState/targetState`，**绝不读 `.fraction`**（根 composable
    //   读 fraction 会触发全树重组抖动 —— 见文件头铁律）。
    val neteaseSession = rememberNeteaseSessionStore()
    // ⚠ 2026-10-16：登录是在**独立 Activity**（`MeloXNeteaseLoginActivity`）里完成的，
    //   它只把 cookie 写进加密存储；而这里的 store 实例只在构造时读过一次 cookie ⇒ 登录
    //   返回后设置页的账号卡仍显示「登录」，要等进程重启才刷新。回到前台时把持久化的
    //   cookie 拉回（幂等），账号状态随即由 `LaunchedEffect(neteaseSession.cookie)` 补拉资料。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        neteaseSession.reloadFromStorage()
    }
    val rootPageState = rememberSaveableStateHolder()
    // Page glass samples a stable background layer. The page itself is
    // recorded separately for the bottom chrome, so neither layer samples
    // its own controls and HWUI never enters a recursive RenderNode graph.
    val pageBackdrop = rememberLayerBackdrop()
    val bottomChromeBackdrop = rememberLayerBackdrop()
    // ⚠ 全屏播放器「封面背景」背板（2026-09-30）：声明在 MeloXApp 层，由
    //   MeloXIOSNowPlayingSharedHost 录制（其内 .layerBackdrop 节点）、供迷你栏采样。
    //   关闭播放页时迷你栏玻璃按 expansionProgress 在「封面背板 ↔ 主页背板」间交叉淡入，
    //   消除关闭末帧「封面色 → 液态玻璃」硬切。详见 MeloXIOSMiniPlayer。
    val playerControlBackdrop = rememberLayerBackdrop()

    LaunchedEffect(playbackConnectionEnabled) {
        if (!playbackConnectionEnabled) return@LaunchedEffect
        if (MeloXSettingsPreferences.boolean(context, "update_auto_check", true)) {
            val now = System.currentTimeMillis()
            val last = MeloXSettingsPreferences.string(context, "update_last_check_ms", "0").toLongOrNull() ?: 0L
            if (now - last >= 24L * 60L * 60L * 1000L) {
                val client = MeloXUpdateClient(context)
                runCatching { client.latestStableRelease() }.getOrNull()?.let { release ->
                    MeloXSettingsPreferences.setString(context, "update_last_check_ms", now.toString())
                    if (client.isNewer(release.version, BuildConfig.VERSION_NAME)) availableUpdate = release
                }
            }
        }
    }

    val currentListDetailMode by rememberUpdatedState(listDetailMode)
    val chromeConfiguration = "$listDetailMode:${MeloXSettingsRuntime.splitNavigationPosition.name}:" +
        "${MeloXSettingsRuntime.splitMiniPlayerPosition.name}:${MeloXSettingsRuntime.disableAutomaticTabBarShrink}"
    var previousChromeConfiguration by rememberSaveable { mutableStateOf(chromeConfiguration) }
    LaunchedEffect(chromeConfiguration) {
        if (previousChromeConfiguration != chromeConfiguration) {
            tabBarMinimized = false
            scrollAccumulator = 0f
        }
        previousChromeConfiguration = chromeConfiguration
    }
    val tabBarMinimizeConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (MeloXSettingsRuntime.disableAutomaticTabBarShrink || !meloXChromeScrollCoupled(
                        currentListDetailMode, MeloXSettingsRuntime.splitNavigationPosition, MeloXSettingsRuntime.splitMiniPlayerPosition,
                    )) {
                    tabBarMinimized = false
                    scrollAccumulator = 0f
                    return Offset.Zero
                }

                if (available.y < 0f) {
                    if (scrollAccumulator > 0f) scrollAccumulator = 0f
                    scrollAccumulator += available.y
                    if (scrollAccumulator <= -18f) {
                        tabBarMinimized = true
                        scrollAccumulator = 0f
                    }
                } else if (available.y > 0f) {
                    if (scrollAccumulator < 0f) scrollAccumulator = 0f
                    scrollAccumulator += available.y
                    if (scrollAccumulator >= 18f) {
                        tabBarMinimized = false
                        scrollAccumulator = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(openNowPlayingRequest, playbackState.hasMedia) {
        if (openNowPlayingRequest > 0 && playbackState.hasMedia) {
            playerClosingIntent = false
            playerTransitionTargetOpen = true
            // ★ 转场可打断：同 `openPlayer` —— 撤销关闭那条待发的落定「咚」。
            playerSettleThumpJob?.cancel()
            playerSettleThumpJob = null
            playerTransitionState.animateTo(
                targetState = true,
                animationSpec = playerOpenFractionSpec(),
            )
        }
    }

    LaunchedEffect(clipboardLinkRequest, clipboardTarget, selectedSource) {
        if (clipboardLinkRequest != null && clipboardTarget == null) onClipboardLinkConsumed()
    }

    var hasConnectedMedia by remember { mutableStateOf(false) }
    LaunchedEffect(playbackState.hasMedia) {
        if (playbackState.hasMedia) hasConnectedMedia = true
        // Initial service reconnection has an empty UI snapshot. It must not
        // close a restored player before the real queue/metadata arrives.
        if (!playbackState.hasMedia && hasConnectedMedia) {
            playerTransitionState.snapTo(false)
        }
    }

    LaunchedEffect(
        playbackState.currentIndex,
        playbackState.queue.map { it.mediaId },
        MeloXSettingsRuntime.automaticLyricSelectionEnabled,
    ) {
        if (playbackState.hasMedia) {
            MeloXProviderLyricsLoader.preloadQueue(context, playbackState, count = 2)
        }
    }

    LaunchedEffect(neteaseSession.cookie, playbackConnectionEnabled) {
        if (playbackConnectionEnabled && neteaseSession.isLoggedIn) {
            neteaseSession.refreshProfile()
        }
    }

    LaunchedEffect(
        selectedSource,
        neteaseSession.cookie,
        onboardingPage,
        MeloXSettingsRuntime.startsHeartModeOnLaunch,
        playbackState.hasMedia,
        playbackConnectionEnabled,
    ) {
        if (!playbackConnectionEnabled || selectedSource != MusicSource.Netease || heartModeLaunchAttempted ||
            onboardingPage >= 0 || playbackState.hasMedia ||
            !MeloXSettingsRuntime.startsHeartModeOnLaunch || !neteaseSession.isLoggedIn
        ) return@LaunchedEffect
        heartModeLaunchAttempted = true
        if (neteaseSession.profile == null) neteaseSession.refreshProfile(force = true)
        val userId = neteaseSession.profile?.userId ?: return@LaunchedEffect
        val client = NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
        val songs = runCatching {
            val snapshot = client.snapshot(userId)
            val seed = snapshot.likedSongs.randomOrNull() ?: error("收藏歌曲为空")
            val playlist = snapshot.playlists.firstOrNull() ?: error("没有可用歌单")
            client.intelligenceModeSongs(seed.id, playlist.id)
        }.getOrNull().orEmpty()
        PlaybackCommands.playAll(context, songs, heartMode = true)
    }

    var previousSelectedTab by rememberSaveable { mutableStateOf(selectedTab) }
    LaunchedEffect(selectedTab) {
        if (previousSelectedTab != selectedTab) {
            tabBarMinimized = false
            scrollAccumulator = 0f
        }
        previousSelectedTab = selectedTab
        if (selectedTab != AppTab.Library) libraryModalVisible = false
        if (MeloXSettingsRuntime.rememberLastTab) {
            MeloXSettingsPreferences.setString(context, "general_last_tab", selectedTab.name)
        }
    }

    val visibleRootTabs = (if (selectedSource == MusicSource.Bilibili) {
        listOf(AppTab.Library, AppTab.Settings)
    } else MeloXSettingsRuntime.tabOrder.mapNotNull { runCatching { AppTab.valueOf(it) }.getOrNull() })
        .filter {
            when (it) {
                AppTab.Home -> MeloXSettingsRuntime.homeTabEnabled
                AppTab.Explore -> MeloXSettingsRuntime.exploreTabEnabled
                AppTab.Library -> MeloXSettingsRuntime.libraryTabEnabled
                AppTab.Podcasts -> MeloXSettingsRuntime.podcastsEnabled && MeloXSettingsRuntime.podcastsTabPlacement
                AppTab.Downloads -> MeloXSettingsRuntime.downloadsEnabled && MeloXSettingsRuntime.downloadsTabPlacement
                AppTab.Cloud -> MeloXSettingsRuntime.cloudMusicEnabled && MeloXSettingsRuntime.cloudTabPlacement
                AppTab.Settings -> true
                AppTab.Search -> false
            }
        }.let { if (AppTab.Settings in it) it else it + AppTab.Settings }
    LaunchedEffect(visibleRootTabs, selectedTab) {
        if (selectedTab !in visibleRootTabs && selectedTab != AppTab.Search) {
            selectedTab = visibleRootTabs.first()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ★ 2026-10-26（打开动画性能 B 方案）：覆盖判据把「外壳 bounds 空间」直接当成
            //   窗口空间来用（`sharedBounds` 的 bounds 是相对本布局根的）。这只有在根
            //   **贴窗口原点**时才成立 —— edge-to-edge 全屏下就是，但分屏 / 画中画 /
            //   刘海补偿一旦让根产生偏移，两套坐标就错位 ⇒ 判据可能多算一截覆盖。
            //   ⇒ 每个布局周期实测一次；不是 (0,0) 就把整条闸门关掉（宁可不省，不许露馅）。
            .onGloballyPositioned { coords ->
                val origin = coords.positionInWindow()
                chromeOcclusion.originVerified = origin.x == 0f && origin.y == 0f
            },
    ) {
      // This is intentionally a small, stable source layer. It gives every
      // page control the same optical backdrop without recording the control
      // into the layer it samples.
      Box(
          modifier = Modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .layerBackdrop(pageBackdrop),
      )
      CompositionLocalProvider(LocalMeloXBackdrop provides pageBackdrop) {
        CompositionLocalProvider(LocalMeloXBackdrop provides playerControlBackdrop) {
            MeloXAudioOutputDialog(
                visible = playbackState.showOutputPicker,
                onDismiss = { playbackState.showOutputPicker = false },
                state = playbackState,
            )
        }
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            val sharedScope = this
            val fullPlayerVisible = playbackState.hasMedia &&
                (playerTransitionState.currentState || playerTransitionState.targetState)
            // ★ 2026-10-26 功耗：把「播放页/输出面板是否可见」写回 state，只用于给系统音量
            //   轮询选周期（60ms / 500ms）。这里是**唯一**写者。
            //   · 用 `LaunchedEffect` 而不是 `SideEffect`：两个 key 都是**离散布尔**，
            //     key 变化才写一次；`SideEffect` 每趟重组都会跑（本页重组很频繁）。
            //   · ⛔ 不读 `fraction`（根作用域读逐帧量会全树重组）。
            LaunchedEffect(fullPlayerVisible, playbackState.showOutputPicker) {
                playbackState.playerVisible =
                    fullPlayerVisible || playbackState.showOutputPicker
            }
            // ★ 2026-10-26（打开动画性能 B 方案）：**兜底复位**。
            //   判据的主要驱动是上面兄弟 Box 的 `drawBehind`（转场期每帧调 `evaluate()`）。
            //   若某次转场里最后几帧没跑到（比如这一帧整体没有被绘制：熄屏 / 后台 / 被上层
            //   对话框完全遮挡），`occluded` 可能残留 true ⇒ 底栏玻璃永久停在"不透明实心"档，
            //   那就是事故。
            //   ⇒ 播放页**真的不可见**时无条件复位。时机安全：`fullPlayerVisible` 要等
            //     `currentState` 与 `targetState` 都离开 Visible 才为 false —— 也就是外壳
            //     已经整块消失之后，此刻底栏本来就该恢复玻璃。
            LaunchedEffect(fullPlayerVisible) {
                if (!fullPlayerVisible) chromeOcclusion.reset()
            }

            // ── 下沉进度（2026-09-29 改用 iOS 曲线，外壳与背景同拍）──────────────
            //   打开 / 拖拽跟手 / 关闭：**全部吃同一条 master 时间线**
            //   （`meloXPlayerRecedeSpec`：打开 = iOS present 曲线，关闭 = iOS 回弹曲线
            //   easeOutBack）。⇒ 播放器外壳 morph 与后方页面下沉**严格同拍**，关闭过冲
            //   （进度短暂为负、页面缩放 >1 被屏裁掉）也由同一条 spec 自然给出，不再用
            //   单独弹簧（那会让外壳与背景在关闭方向不同步）。
            //   ⚠ 这里**不能 coerce 到 [0,1]**：关闭过冲时 f 略小于 0（见
            //     `recedeMasterFraction`），裁掉即可，正是「页面往边缘扩的过冲回弹」。
            val recedeFraction: () -> Float = {
                if (MeloXSettingsRuntime.reduceMotion) 0f else recedeMasterFraction()
            }

            // ⚠ 退让层的圆角**必须走实例缓存**，不能每帧 `MeloSmoothCornerShape(...)`：
            //   `graphicsLayer` 的 lambda **每帧执行**，而该 Shape 没实现 `equals`
            //   ⇒ 每帧喂进去的都是"新"形状 ⇒ `RenderNode.setOutline` 按引用判等必然标脏
            //   ⇒ 这一层全屏的路径裁剪每帧重建重栅格化（`MeloXPageTransitions` 里
            //   `MeloXSlideShapeCache` 的注释给的是同一套论证，这里只是把它补到
            //   当时没覆盖到的调用点上）。本处是 **sx ≠ sy** 的分轴缩放 ⇒ 用 `ofXY`。
            val recedeShapeCache = remember { MeloXSlideShapeCache() }

            // 横屏播放器默认沉浸：隐藏状态栏 + 底部导航小白条（systemBars）；
            // 竖屏只藏顶栏（statusBars），且仍走「沉浸式播放」设置开关，底栏保持可见。
            val playerWindow = rememberMeloXWindowInfo()
            MeloXImmersivePlaybackEffect(
                enabled = fullPlayerVisible &&
                    (MeloXSettingsRuntime.immersivePlaybackEnabled || playerWindow.isLandscape),
                type = if (playerWindow.isLandscape) {
                    WindowInsetsCompat.Type.systemBars()
                } else {
                    WindowInsetsCompat.Type.statusBars()
                },
            )

            // ── 后页面下沉（Apple Music 口径，2026-09-28 三次定档）──────────────
            //   ① 下沉舞台：页面缩小后露出的外圈**必须是黑色**（用户报障：「我要的是下沉
            //      的背景页外面是黑色而不是白色」）。浅色主题下铺 `colorScheme.background`
            //      （白）会让整块下沉根本读不出来 —— 白页缩在白底上等于没动。
            //      做法：先铺主题背景色（静止时零观感变化），再按下沉进度把黑压上去，
            //      且**快速到位**（smoothStep t:0→0.35 就全黑）—— 空隙刚开始露出来时
            //      它就已经是黑的了，不会看到「灰→黑」的过渡灰边。
            //   ② 页面向中心缩小 + 下沉，四角浮出与播放器**同一条曲线**的系统圆角，
            //      再压暗一档。全部只走 draw 期（drawBehind / graphicsLayer），
            //      每帧只重绘、不重组、不重排。
            //   ⚠ 只缩页面层，不缩底部 chrome —— chrome 里挂着 MiniPlayer 的
            //     sharedBounds 源，祖先缩放会把 morph 几何带歪。
            val recedeBackdropColor = MaterialTheme.colorScheme.background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val t = recedeFraction()
                        // ⚠ **页面外侧全程纯黑**（用户报障：「背景页最靠近屏幕时，边缘会变白，
                        //   正确应该是背景页外面全程是黑的」）。
                        //   之前是 `lerp(背景色, 黑, smoothStep(t, 0, 0.35))` —— 前 35% 其实是
                        //   「半黑的浅色」，而页面刚离开满屏那一刻露出的缝正是这一段 ⇒ 发白。
                        //   现在只在**完全没有下沉**时铺主题背景色（此刻页面满屏、舞台不可见，
                        //   免得切换 tab 时 AnimatedContent 的淡入淡出透出黑底），
                        //   一旦 t>0 立刻切纯黑。
                        // ★ 2026-10-24 修「转屏时下半区漏出首页」：只要**全屏播放页可见**
                        //   （打开 / 转屏 / 塌缩全程，`fullPlayerVisible`），退让层外圈强制纯黑，
                        //   不再依赖 recede 进度是否恰好到位。转屏瞬间 recede 进度可能有 1 帧抖动，
                        //   或退让页与播放页的 `BoxWithConstraints` 约束存在 1 帧错位，会让本该是
                        //   黑的外圈短暂露出首页内容。把判据从「recede 进度」扩到「播放页可见」，
                        //   从根上堵死透出首页的路径。无播放页时（`!fullPlayerVisible`）仍走原逻辑
                        //   （铺主题背景色，避免切 tab 透出黑底）。
                        drawRect(if (t <= 0.0005f && !fullPlayerVisible) recedeBackdropColor else Color.Black)
                        // ⚠ 覆盖判据**不在这里求值**（2026-10-26 第二次实测）：本条绘制链的
                        //   失效时机与 bounds 动画不同源 —— 真机实测整段转场它只跑了 2 次
                        //   （于是闸门既不提前、收起方向也从不翻回 false）。
                        //   现在由 `MeloXShellGeom.record()` 每帧回调驱动，见
                        //   `chromeOcclusion` 上方 `DisposableEffect` 的说明。
                    },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (libraryModalVisible && selectedTab == AppTab.Library && !fullPlayerVisible) 15f else 0f)
                    // ══ 底栏玻璃背板的**录制点**（2026-09-30 六次修，本轮定档）══════════════
                    //   `bottomChromeBackdrop` 只给底栏胶囊 / 迷你栏 / 搜索键 / 水珠采样。
                    //   录制节点（`Modifier.layerBackdrop`）就是**这个 Box**，
                    //   且**必须**挂在 recede `graphicsLayer` **之前**（= 外层）。
                    //
                    //   ── 唯一不变式（记住这一条就够）─────────────────────────────────
                    //   录制内容所在的**空间** 必须 = 采样映射所在的**空间**。
                    //   采样映射由 `LayerBackdrop.drawBackdrop` 给出（反编译 2.0.0）：
                    //     `p = layerCoordinates.localPositionOf(drawingCoords, Zero)`
                    //     → `translate(-p.x, -p.y)` → `drawLayer(graphicsLayer)`
                    //   也就是「把录制节点的本地原点摆回它在屏幕上的物理位置」。
                    //   而 `recordLayer(...)` 录进去的，是 `drawContent()` 在该节点
                    //   **本地空间**里的绘制结果。
                    //   ⇒ 只有让录制点坐在所有变换的**外面**，本地空间才等于屏幕空间，
                    //     「录到的内容」与「采样查表的位置」才指向同一张图。
                    //
                    //   ── 挂在里面（旧做法：挂在 Scaffold 上）为什么会**渲染内容跳变** ──
                    //   `graphicsLayer` 的缩放/圆角/裁切只作用在**它自己的 RenderNode** 上，
                    //   节点本地空间的绘制结果**不含**这个变换：
                    //     · 录制内容 = **未缩放**的页面；
                    //     · 而 `p` 一旦沿路穿过 recede 层，描述的就是**屏幕上**的位置。
                    //   ⇒ 玻璃显示的永远是「未缩放页面」的一个切片，与它背后真实（缩放过的）
                    //     画面错开，错位量 ∝ 当前 recede 进度（底栏离屏幕中心 ~1400px，
                    //     f=1 时纵向差 50~70px、横向在屏幕左右缘可达 80px）。
                    //   ⇒ 而**录制何时刷新**只取决于该 draw 节点是否失效 ⇒ 与进度无关。
                    //     每次刷新都会把玻璃内容从「上一次刷新的错位切片」切成「这一次的」，
                    //     于是表现为**内容整体跳变**（用户报障线：「不是颜色问题…是渲染的内容跳变」）。
                    //   真机 60fps 逐帧取证（`fix1.mp4`，3 次开合循环）× 观感一致：
                    //     · 跳变只发生在**有结构的区域** —— 底栏下缘 y≈2940~3066、
                    //       横向 x89~518 与 x1168~1327（首页胶囊 / 搜索键，背后压着页面大字），
                    //       而 x519~1167（平滑区）与胶囊上半**逐像素为 0**；
                    //     · 同一帧内**几何完全没动**（平移搜索 best dx=dy=0）、
                    //       红/白前景峰值不变、平滑区亮度不变
                    //     ⇒ 铁证：不是底栏自身的材质/高光/几何在动（那样四个 tab 会一起动），
                    //       而是**玻璃采样到的背板内容**在动。
                    //     · 时刻：关闭动画结束（f481）后 ~5 帧（f486，t≈8.11s）——
                    //       即 `fullPlayerVisible` 翻转引发全量重绘、录制终于刷新那一帧。
                    //   ⇒ 与用户现场口径一致：「信息在底栏的上下边缘会出现跳变渲染，
                    //     在底栏的中间就没事」—— 中间背后是平滑区，错位看不出来。
                    //
                    //   ── 修法 ──────────────────────────────────────────────────────────
                    //   把录制点搬到 recede `graphicsLayer` **外面**（本 Box）。
                    //   此时 `p` 的计算路径上**不再有任何变换层**（recede 层在录制点内侧，
                    //   与玻璃是兄弟子树）⇒ 采样空间 ≡ 屏幕空间 ≡ 录制空间。
                    //   ⇒ 录制内容 = 屏幕上**真实看到的合成画面**，玻璃在任何进度、任何刷新
                    //     时机采到的都与背后画面逐帧一致 —— **内容跳变在结构上不可能发生**
                    //     （刷新从"事件"退化成"无观感差异的幂等操作"）。
                    //   附带修好三个老毛病：
                    //     ① 底栏 chrome 在**这个 Box 之外**（depth 与 recede Box 平级）
                    //        ⇒ 录制内容**不再包含玻璃自己**，去掉「玻璃采样含自身」的自反馈；
                    //     ② 页面压暗 scrim 在**这个 Box 之内** ⇒ 玻璃能正确显示转场中
                    //        压暗过的页面（以前录不到，玻璃偏亮）；
                    //     ③ 同一个 backdrop **只此一处**录制点（Scaffold 上那份已删）——
                    //        `LayerBackdropNode.onGloballyPositioned` 是**无条件覆盖**
                    //        `layerCoordinates`，挂两处 = 竞态。
                    //   ⚠ `drawBehind { recedeFraction() }` 在最外层：转场期间进度每帧变
                    //     ⇒ 这条 draw 链每帧失效 ⇒ 外层重跑 ⇒ 每帧重新 `recordLayer`
                    //     （静止时进度不变 ⇒ 不失效 ⇒ 零额外开销）。
                    //   ⚠ 透明边：f 较大时缩放后的页面盖不满屏（左右各 ~62px 空隙），
                    //     那一段录成透明 —— 但此时底栏已被不透明的全屏播放器完全盖住，
                    //     且 f 越小空隙越小，故无可见回归。
                    //   ★★ 2026-10-26（打开动画性能 B 方案）：在「外壳已经把底部 chrome
                    //     整块不透明盖住」期间**停止逐帧强制重录**。
                    //     · 判据 `occluded` 是**离散 State**（只在穿越时翻转）⇒ 覆盖期本块
                    //       不再读到会变的量 ⇒ 不再失效 ⇒ 录制**冻结**。
                    //       此时录制的内容（页面 + 压暗）被外壳整块压住，一个像素都看不见，
                    //       但每帧仍要付一次整屏离屏重绘 + 18.25MB 纹理上传（真机实测口径）。
                    //     · 未覆盖时照旧读 `recedeFraction()` ⇒ 保持原来的每帧重录，
                    //       玻璃显示的就是当帧真画面，观感一字不变。
                    //     · ⚠ 两条必须一起停（见 `LocalMeloXBackdrop provides` 的门控）：
                    //       只冻结录制而底栏玻璃还在采样，会看到**陈旧内容**；
                    //       只停玻璃而录制还在重录，则白付一次整屏 recordLayer。
                    //     · ⚠ 解冻不靠轮询：`occluded` 翻转 ⇒ 本块失效一次 ⇒ 当帧就读到新值
                    //       走另一条分支（draw 期读 State 会注册快照观察）。反向（退出播放页）
                    //       由上面兄弟 Box 的 `drawBehind` 里每帧驱动的 `evaluate()` 保证
                    //       —— 那里读 `recedeFraction()`，转场期每帧都跑。
                    .drawBehind {
                        if (!chromeOcclusion.occluded.value) recedeFraction()
                    }
                    .layerBackdrop(bottomChromeBackdrop)
                    .graphicsLayer {
                        val f = recedeFraction()
                        // ★ 2026-10-26：缩放的**唯一实现**搬到了 `MeloXChromeRecede.kt`
                        //   （`meloXRecedeScaleX/Y`），因为底部 chrome 现在也要跟着缩
                        //   （用户口径「底栏跟着背景页一起下沉缩小、相对位置不变」）。
                        //   两处必须逐字同参，否则底栏会相对背景页漂移 ⇒ 公式只能有一个出处。
                        val sx = meloXRecedeScaleX(f)
                        // 纵向比例默认由横向内缩反推：只有**可见内缩在 x/y 上相等**，
                        // 与屏幕同心/相切的圆弧才能同时贴住上下左右四条边、黑边才等宽。
                        // ⚠ 这里的「等宽」只管**缩放**：上下等宽随后被 PageSinkDp 的**平移**
                        //    刻意打破（AM 下沉口径，见 MeloXMotion 文档）——平移不改圆弧贴合，
                        //    两者不矛盾，别看到上下不等宽就回来"修"这里。
                        // ⚠ `PageScaleYOverride > 0` 时改用它：加大纵向内缩 ⇒ 底边额外上移
                        //    （用户报障「背景下沉的底部几乎不上移」），且 `PageSinkDp` 一行未动。
                        val sy = meloXRecedeScaleY(f, size.width, size.height)
                        scaleX = sx
                        scaleY = sy
                        // 向下沉降（AM 口径）：与缩放读**同一个** f ⇒ 与卡片推进严格同拍。
                        // ★ 2026-10-27：位移从固定 30dp 改为**按纵向可缩余量的比例**
                        //   （`meloXRecedeSinkPx`）—— 横屏时屏幕高只有一半，固定 30dp 会把
                        //   卡片底边推出屏幕 26px（用户报「下半页面在动画里被截断」）。
                        //   竖屏结果仍是 120.1px，与旧值逐像素等同。推导见该函数 kdoc。
                        translationY = meloXRecedeSinkPx(f, size.height, sy)
                        // 🔬 2026-10-27 取证对照：与底部 chrome 那层同款落盘（tag 不同）。
                        //   两条 `size` 相等 ⇒ 两层缩放中心都在屏幕中心 ⇒ 底栏与背景页
                        //   的相对位置恒定。（当时用诊断包逐帧核对；诊断器已于 2026-10-30 移除。）
                        // 圆角 = **系统屏幕圆角**（全程恒定、不收），且用**平滑圆角**
                        // （Lamé 超椭圆、连续曲率）而不是正圆弧 —— 后者曲率在直边处跳变，
                        // 肉眼就是「圆弧生硬」。分轴补偿保证可见半径在 x/y 上都是 displayCornerPx。
                        // ⚠ 缩放先吸附到 1/64 步长（= ±1.6px @184px，肉眼不可辨）再进缓存：
                        //   否则 sx/sy 每帧都是新值，缓存永远 miss，等于没加。
                        shape = recedeShapeCache.ofXY(
                            displayCornerPx,
                            meloXQuantizedScale(sx),
                            meloXQuantizedScale(sy),
                        )
                        clip = f > 0.0005f
                    },
            ) {
            // ⚠⚠ 2026-09-29：**不再消费 status bar inset**，让每屏内容真正铺到 y=0
            //   （状态栏下直接透出页面内容，而不是那 46dp 的纯背景「白条」）。
            //
            // 改法说明：
            //   旧做法 `contentWindowInsets = recedeStableStatusInsets` 让 Scaffold 给**整个**
            //   子树顶推 46dp，各屏自己画的背景/渐变/hero 图起于 y=46dp，上方那 46dp 只剩
            //   Scaffold 的 containerColor（纯页背景色）—— 视觉上就是「状态栏处一条白/黑遮罩带」。
            //   现在：
            //     1. `contentWindowInsets = WindowInsets()`  ⇒ 壳**不加**顶部 padding；
            //     2. 不再 `consumeWindowInsets` 状态栏 ⇒ 各屏自己的 `statusBarsPadding()`
            //        重新拿到真实值，只垫它自己的文字/控件，而**背景（fillMaxSize + background）
            //        顶到 y=0**，内容从状态栏底下穿出来。
            //   净位移不变：原来「壳垫 46dp + 子级 padding 被吃成 0」= 46dp；
            //   现在「壳垫 0 + 子级 padding 46dp」= 46dp ⇒ 文字控件位置不回归，只多出背景露出。
            //
            // ⚠ 回归风险：任何**根节点没有** `statusBarsPadding()` 的屏幕，其内容会直接顶到
            //   y=0、撞进状态栏图标。改完需逐屏验收（tab 页 + overlay）。
            // ⚠ 底栏背板录制节点**不在这里**（2026-09-30 六次修定档）：
            //   `bottomChromeBackdrop` 的录制点已搬到**外层 recede Box** 上
            //   （见上方 `Box(...)` 处的长注释）。这里只剩"页面本体"，
            //   所以 Scaffold 上**不再挂** `layerBackdrop` / `drawBehind{recedeFraction()}`。
            //   ⚠ 千万**不要**同一个 backdrop 挂两处 `Modifier.layerBackdrop`：
            //     `LayerBackdropNode.onGloballyPositioned` 会 `setLayerCoordinates$backdrop(coordinates)`
            //     无条件覆盖 —— 谁最后被定位谁生效（Compose 的定位回调顺序不保证），
            //     等于把「录制点在哪一层」变成了竞态 ⇒ 采样原点会在两处之间跳。
            Scaffold(
                modifier = Modifier
                    .fillMaxSize(),
                contentWindowInsets = WindowInsets(),
                // ⚠ **不能改成 `Color.Transparent`**（2026-10-19 回退）：
                //   这层 fills 也在 `bottomChromeBackdrop` 的**录制内容**里（recede Box 的
                //   `Modifier.layerBackdrop` 包着整个 Scaffold）。底栏玻璃采样的是那个
                //   录制层，少一层不透明底色 ⇒ 该区域在采样层里是**透明**的 ⇒
                //   玻璃只剩 52% 涂层，看起来就是「半透明」。
                //   （当初想省一次全屏填充降 overdraw，但 `debug.hwui.overdraw` 实测
                //     改成透明后仍是 3 层 —— 收益为零，风险实打实，所以退回。）
                containerColor = MaterialTheme.colorScheme.background,
                ) { innerPadding ->
                    Box(Modifier.fillMaxSize().padding(innerPadding)) {
                    MeloXListDetailLayout(
                        state = detailPaneState,
                        session = neteaseSession,
                        hasMiniPlayer = playbackState.hasMedia,
                        primaryModifier = Modifier.nestedScroll(tabBarMinimizeConnection),
                        onSourceSelected = { source ->
                            selectedSource = source
                            MusicProviderSelectionStore.setSelectedSource(context, source)
                            libraryModalVisible = false
                            heartModeLaunchAttempted = false
                        },
                    ) {
                    val searchTabBackClear = remember { mutableStateOf(com.lladlam.melox.ui.search.SearchBackAction.SwitchToHome) }
                    var searchTabAction by remember { mutableStateOf(com.lladlam.melox.ui.search.SearchBackAction.SwitchToHome) }
                    // ⚠ 覆盖层优先于切 tab：覆盖层（听歌识曲 / 私信 / 云控同意 / 更新 / 剪贴板）
                    // 可在任意 tab 上打开。原写法先判 `selectedTab != AppTab.Home`，于是浮在非主页
                    // 上的覆盖层第一次返回被「切回主页」吞掉、第二次才关 —— 用户感知即「退两次、
                    // 且直接跳了两级（tab + 覆盖层）」。改为先关覆盖层，再处理切 tab / 退出确认。
                    val anyOverlayVisible = recognitionVisible ||
                        cloudControlChoicePending || availableUpdate != null || clipboardTarget != null
                    val exitConfirmThresholdMs = 2_000L
                    var pendingExitAtMs by remember { mutableStateOf(0L) }
                    BackHandler {
                        if (anyOverlayVisible) {
                            // 覆盖层优先关闭，不被切 tab 抢走返回事件。
                            when {
                                recognitionVisible -> recognitionVisible = false
                                cloudControlChoicePending -> cloudControlChoicePending = false
                                availableUpdate != null -> availableUpdate = null
                                clipboardTarget != null -> onClipboardLinkConsumed()
                            }
                            return@BackHandler
                        }
                        if (selectedTab != AppTab.Home) {
                            if (selectedTab == AppTab.Search) {
                                if (searchTabAction != com.lladlam.melox.ui.search.SearchBackAction.SwitchToHome) {
                                    searchTabBackClear.value = searchTabAction
                                    return@BackHandler
                                }
                            }
                            // Any non-home root page returns to the default launch
                            // page first. System back from the search page or any
                            // other tab no longer exits the app directly.
                            tabBarMinimized = false
                            selectedTab = AppTab.Home
                            return@BackHandler
                        }
                        val now = System.currentTimeMillis()
                        if (now - pendingExitAtMs < exitConfirmThresholdMs) {
                            (hostContext as? Activity)?.finish()
                        } else {
                            pendingExitAtMs = now
                            Toast.makeText(hostContext, hostContext.getString(R.string.app_exit_confirm), Toast.LENGTH_SHORT).show()
                        }
                    }
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { meloXContentEnter() togetherWith meloXContentExit() },
                        modifier = Modifier
                            .fillMaxSize()
                            // ★★ 2026-11-06（无真机轮 · 第 3 轮）：**播放页完全覆盖时，整页停止绘制**。
                            //
                            //   动机（结构实测，不是猜）：播放页是同一个 composition 里 zIndex=20 的
                            //   全屏覆盖层，而 HWUI **没有遮挡剔除** —— 底下那一页即使一个像素都看不见，
                            //   仍然被逐帧 replay。模拟器 atrace 实证（`tmp/ab0`、`tmp/ab_setting`、
                            //   `tmp/t_app2`，都是「播放页已展开、无任何操作」的稳定态）：
                            //   每帧仍有 `alpha caused saveLayer 66x66` ×1，且每 ~500ms 再补一组
                            //   `904x335 / 904x448 / 904x521 / 904x222`（合计 ≈6.3 Mpx/次）
                            //   —— 这些尺寸**在本页任何播放页节点上都量不到**（真机实测：
                            //   `controls 904x767` / `artwork 904x2077` / `progress 904x143` /
                            //   `volume 904x116` / `pageSel 904x137`），换成「设置」tab 打开播放页
                            //   尺寸族照样出现且高度不同 ⇒ 来源就是**底下那一页**。
                            //   真机 1440×3168 上这相当于每帧多 replay 一整页（含若干满宽离屏）。
                            //
                            //   做法：`playerRootFraction ≥ 1`（外壳已铺满整屏、底页 100% 被盖住）时
                            //   用一个空 `drawWithContent` 掐掉 `drawContent()` —— 子树**一笔都不画**，
                            //   不建图层、不依赖 HWUI 的 alpha==0 短路。
                            //   ⚠ 判据必须是 `≥ 1f`：底页在展开/收起 morph 期间**是给人看的**
                            //     （recede 下沉就是这一层），提前隐藏会直接改变观感。
                            //   ⚠ 用 `derivedStateOf` 包一层：`playerRootFraction` 在整段动画里每帧都变，
                            //     直接 `>= 1f` 读会让本函数每帧重组；包一层只在**布尔翻转**时重组。
                            .then(
                                if (playerFullyCoversPage) {
                                    Modifier.drawWithContent { /* 播放页已铺满：底页零绘制 */ }
                                } else {
                                    Modifier
                                },
                            ),
                        label = "melox-page-transition",
                    ) { tab ->
                    rootPageState.SaveableStateProvider(tab.name) { when (tab) {
                         AppTab.Search -> if (selectedSourceId.builtIn == null) SearchScreen(pluginSourceId = selectedSourceId.value)
                         else if (selectedSource == MusicSource.Netease) SearchScreen(
                             backClearSignal = searchTabBackClear,
                             onSearchBackState = { searchTabAction = it },
                              onSearchExit = {
                                  detailPaneState.clearPages()
                                 tabBarMinimized = false
                                 selectedTab = AppTab.Home
                             },
                             onOpenRecognition = { recognitionVisible = true },
                         ) else ProviderSearchScreen(selectedSource)
                         AppTab.Home -> if (selectedSourceId.builtIn == null) com.lladlam.melox.ui.provider.PluginSourcePage(selectedSourceId, "home") else MeloXHomeScreen(
                            source = selectedSource,
                            onOpenTool = { route ->
                                when (route) {
                                     "Podcasts", "Downloads", "Cloud" -> detailPaneState.openTool(route)
                                     else -> {
                                         when (route) {
                                             "Recognition" -> recognitionVisible = true
                                             "Messages" -> MeloXMessagesActivity.launch(hostContext)
                                             else -> MeloXSettingsDetailActivity.launch(hostContext, route, selectedSource)
                                         }
                                     }
                                 }
                            },
                        )
                         AppTab.Explore -> if (selectedSourceId.builtIn == null) com.lladlam.melox.ui.provider.PluginSourcePage(selectedSourceId, "rankings") else if (selectedSource == MusicSource.Netease) MeloXExploreScreen() else ProviderExploreScreen(selectedSource)
                         AppTab.Library -> if (selectedSourceId.builtIn == null) com.lladlam.melox.ui.provider.PluginSourcePage(selectedSourceId, "library") else if (selectedSource == MusicSource.Netease) {
                            LibraryScreen(
                                session = neteaseSession,
                                playlistBackEnabled = !fullPlayerVisible && !libraryModalVisible,
                                onModalVisibilityChanged = { libraryModalVisible = it },
                            onLogin = { launchNeteaseLogin(AppTab.Library) },
                            )
                        } else {
                            ProviderLibraryScreen(selectedSource)
                        }
                        AppTab.Podcasts -> MeloXPodcastScreen(applyStatusBarPadding = true)
                        AppTab.Downloads -> LibraryScreen(
                            session = neteaseSession,
                            onLogin = { launchNeteaseLogin(AppTab.Downloads) },
                            forcedPageName = "Downloads",
                        )
                        AppTab.Cloud -> MeloXCloudMusicScreen()
                         AppTab.Settings -> ProviderSettingsHub(
                             currentSource = selectedSource,
                             currentSourceId = selectedSourceId,
                             onSourceSelected = { source ->
                                 detailPaneState.clearPages()
                                 selectedSource = source
                                MusicProviderSelectionStore.setSelectedSource(context, source)
                                tabBarMinimized = false
                                libraryModalVisible = false
                                heartModeLaunchAttempted = false
                                // Provider switching changes only the backing data source.
                                // Stay on the current Settings route and preserve all MeloX settings UI/state.
                            },
                            neteaseSession = neteaseSession,
                            onNeteaseLogin = { launchNeteaseLogin(AppTab.Settings) },
                            onOpenAccount = {
                                neteaseSession.profile?.userId?.let { userId ->
                                    MeloXAccountActivity.launch(hostContext, userId)
                                } ?: run {
                                    launchNeteaseLogin(AppTab.Settings)
                                }
                            },
                            onOpenServices = { MeloXProviderServicesActivity.launch(hostContext) },
                             onOpenMessages = { MeloXMessagesActivity.launch(hostContext) },
                         )
                     } }
                     }
                    }
                 }
            }
                // 下沉时的压暗层：只罩住被缩小的页面本身，底部 chrome 与播放器不受影响。
                // 与缩放窗口同源（同一条 recedeFraction），保证「缩多少 = 暗多少」。
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            val t = recedeFraction()
                            if (t <= 0.001f) return@drawBehind
                            drawRect(Color.Black.copy(alpha = MeloXPlayerRecede.PageScrimAlpha * t))
                        },
                )
            }

            // ══ 底部 chrome 明暗切换状态（2026-09-25）════════════════════════════
            // ══ 底部 chrome 明暗切换状态（2026-09-25）════════════════════════════
            //   `isMeloXDarkTheme()` **只能在这里读一次**，作为打底值；之后全部走 `bottomBarTone`。
            val systemDark = isMeloXDarkTheme()
            val toneState = rememberBottomBarToneState(
                systemDark,
                paused = fullPlayerVisible,
                lockDarkInDarkMode = MeloXSettingsRuntime.bottomBarLockDarkInDarkMode,
            )
            DisposableEffect(toneState) {
                onDispose { toneState.release() }
            }
            val bottomBarTone = toneState.darkness(systemDark)

            // ══ 亮度采样源图层（2026-10-19，换掉 PixelCopy）══════════════════════════
            //   导航胶囊在 `onDrawBackdrop` 里把**背板内容**顺手录进这一层（等比缩到 96px 宽），
            //   采样循环再 `toImageBitmap()` 读回来算均值。
            //   ⇒ ① 录到的内容天然不含我们自己的 vibrancy/涂层/高光（它们挂在 kyant0 那层的
            //        `renderEffect` 与 `onDrawSurface` 上），所以「背景」口径是**结构上**成立的；
            //     ② 不再有 `PixelCopy` 在 RenderThread 上同步等当前帧 GPU 完成的开销
            //        （真机 trace 实测单次 3~16ms，任何动画都会被它顶掉一帧）。
            //   ⚠ 只有导航胶囊传它，搜索键/水珠/迷你栏都不传 —— 多个面 record 会互相覆盖。
            val bottomBarLuminanceLayer = rememberGraphicsLayer()
            // `SideEffect` 而不是在循环里读 CompositionLocal：采样循环活在协程里，
            // 拿不到 Composable 作用域；这里在每次成功组合后把最新层交过去。
            SideEffect { toneState.luminanceLayer = bottomBarLuminanceLayer }

            // ★★ 2026-10-27（第三次修「竖转横底栏长变短」）：底栏的 split 判据改走
            //   **布局真值**（`meloXUsesListDetail(Constraints)`），不再用配置口径。
            //   机理：转屏时「窗口 resize」与「配置派发」是两条独立的主线程消息，谁先到
            //   不保证。只要判据比约束晚，就会出现若干帧「窗口已经横屏、split 还是 false」
            //   ⇒ `MeloXPositionedChrome` 走非分栏分支（`minWidth = maxWidth = 整屏宽`）
            //   ⇒ 胶囊铺满全屏（长），下一帧才收成半屏（短）—— 用户报的那一下；因为跨了
            //   多帧，所以是「一个过程」而不是一闪。
            //   `MeloXListDetailLayout` 同样改走 `BoxWithConstraints` 的约束 ⇒ 两侧都是
            //   **本次 measure 的输入**，同帧同源，跨帧在结构上不可能。
            //   ⚠ 用 `BoxWithConstraints` 而不是 `Box`：subcompose 在 measure 阶段执行，
            //     内容在同一帧就拿到新约束（`onSizeChanged` / State 回填都晚一帧）。
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .meloXChromeRecedeLayer(recedeFraction),
            ) {
                val chromeSplit = meloXUsesListDetail(constraints, MeloXSettingsRuntime.splitScreenEnabled)
                // ⚠ 闸门也必须用**同一个** `chromeSplit`（不是 `listDetailMode`）：否则转屏那
                //   几帧里底栏会被整体卸载再重挂，等于凭空闪一下。
                if (detailPaneState.pages.isEmpty() || chromeSplit) CompositionLocalProvider(
                // ⚠ 底栏玻璃**恒**采样 bottomChromeBackdrop（底栏 / 迷你栏 / 搜索键 / 水珠统一）。
                //   它的录制节点在**外层 recede Box** 上（`graphicsLayer` 之外，唯一一处平行层）：
                //   **恒定挂载 + 每帧跟着转场进度重录**
                //   （`drawBehind { recedeFraction() }` 在 `layerBackdrop` 外层强制失效）。
                //   ★ 两个都不能省：① 绝不按条件卸载 —— 录制节点 detach 后 kyant0
                //     `drawBackdrop` 会**直接 return 什么都不画**（反编译确认），玻璃只剩底色
                //     = 半透明；② 光挂着也不行 —— 祖先 graphicsLayer 只改变换时子树不重绘，
                //     录制内容会冻结成旧快照。完整推导见 recede Box 处注释。
                LocalMeloXBackdrop provides bottomChromeBackdrop,
                // ── 底部 chrome 明暗切换（2026-09-25）─────────────────────────────
                //   Apple 口径：navbars / tabbars 属于「会按背后内容在明暗之间整体翻转」
                //   那一档（WWDC25 Session 219 @15:16）。用户拍板：**系统主题打底 +
                //   同时真实采样底栏背后像素** 覆盖。
                //   ⚠ 必须**整个底部 chrome 共用同一个 d**（nav / 搜索键 / 播放栏 /
                //   水珠 / 前景字形全部在内），否则会出现「底栏亮、搜索键暗」的碎裂感。
                LocalBottomBarTone provides bottomBarTone,
                LocalBottomBarLuminanceLayer provides bottomBarLuminanceLayer,
            ) {
                // ══ 明暗两种模式的底栏互相切换（方案 A，2026-09-25 用户选定）══════════
                //   整棵子树改用「LightColors ↔ DarkColors 插值出来的配色」重builder一个
                //   MaterialTheme。 ⇒ 所有读 `MaterialTheme.colorScheme.*` 的组件自动跟着过渡，
                //   **不需要**在底栏里手写任何颜色端点。
                //   ⚠ `isMeloXDarkTheme()` 也是读 `colorScheme.background.luminance()`，
                //     所以 subtree 里的布尔判定同样会平滑地跟着翻。
                BottomBarToneTheme(darkness = bottomBarTone) {
                // ★★ 2026-10-26（用户口径）：**底栏跟着背景页一起下沉 + 缩小，相对位置不变**。
                //   外层 `BoxWithConstraints` 满屏 ⇒ `graphicsLayer` 绕**屏幕中心**缩放，
                //   与背景页那层同一个中心（详见 `MeloXChromeRecede.kt`）。
                MeloXBottomChrome(
                    selectedTab = selectedTab,
                    source = selectedSource,
                    onSelect = { tab ->
                        // ★ tab 点击触感（2026-10-06）：原先是 `meloXSelectionHaptic`（SegmentTick，最轻），
                        //   用户反馈太弱 → 先换 Confirm（中等）仍嫌弱 → 换 LongPress（最重可用类型），
                        //   明确强感、与收起落定的落定拍同款力度。只在 tab 真正切换时响
                        //   （维持展开态的重复点击不响、与 tabBarMinimized 变化的 LongPress 不叠加成双 buzz）。
                        if (tab != selectedTab) tabHaptic(HapticFeedbackType.LongPress)
                        if (tab != selectedTab) {
                            detailPaneState.clearPages()
                            recognitionVisible = false
                        }
                        tabBarMinimized = false
                        selectedTab = tab
                    },
                    hasMedia = playbackState.hasMedia,
                    minimized = tabBarMinimized,
                    visibleRootTabs = visibleRootTabs,
                    split = chromeSplit,
                    // ⚠ 2026-10-19：这里**不再**用 `onGloballyPositioned` 把底栏矩形喂给采样器 ——
                    //   换源后采的是导航胶囊自己录下的背板图层，不需要知道窗口坐标
                    //   （原来那套「底栏上沿往上 8dp 的带子」就是为绕开 PixelCopy 抓到的
                    //   自身玻璃才存在的）。
                    modifier = Modifier
                        .align(if (chromeSplit) Alignment.BottomStart else Alignment.BottomCenter)
                        .then(if (chromeSplit) Modifier.fillMaxSize() else Modifier.fillMaxWidth()),
                    // ★ 2026-10-26（打开动画性能 B 方案）：见 `MeloXChromeOcclusion.kt`。
                    chromeOcclusion = chromeOcclusion,
                    miniPlayer = { compactProgress ->
                        playerTransition.AnimatedVisibility(
                            // ⚠⚠ 2026-10-06 取证埋点：记录本 lambda 每帧收到的 `value`
                            //   （= `SeekableTransitionState` 给 `Transition` 的哪一个状态）。
                            //   「轻微下拉下沉」的候选机制之一就是它 —— 若回弹（target=打开）
                            //   期间该值仍是"收起侧"，迷你栏就会被判为可见 ⇒ 它的外壳
                            //   （共享元素 overlay，zIndex 100f，浮在播放页之上）会从
                            //   full 一路收缩到 mini，而 `exit = None` 不淡出 ⇒ 看得见的"下沉"。
                            //   `sample` 只在值变化时落一行，故不会刷爆文件。
                            // ⚠⚠⚠ 2026-10-06 结论：这里的判定**必须继续用 lambda 的 `value`**，
                            //   不能改成只读 `playerTransitionState.targetState`（试过 ⇒ 撤回）。
                            //   原因：`AnimatedVisibility` 的 `currentlyVisible = visible(currentState)`。
                            //   `SeekableTransitionState` 在拖拽/收起期间 `currentState` 仍是"打开侧"
                            //   ⇒ 原写法让 `currentlyVisible = true` ⇒ **内容保持挂载**。
                            //   若改成读 `targetState`，收起时两次求值都是 false ⇒ 被判定"当前不可见"
                            //   ⇒ 收起过程中内容就被卸载（播放页提前消失）。
                            //   `value` 仍照旧参与判定，并额外采样用于取证。
                            visible = { value ->
                                !value
                            },
                            enter = EnterTransition.None,
                            exit = ExitTransition.None,
                        ) {
                            MeloXIOSMiniPlayer(
                                state = playbackState,
                                onExpand = openPlayer,
                                compactProgress = compactProgress,
                                vertical = chromeSplit && MeloXSettingsRuntime.splitMiniPlayerPosition.isSideRail,
                                 dynamicGlassEnabled = true,
                                 sharedTransitionScope = sharedScope,
                                 animatedVisibilityScope = this,
                                 applyPlayerArtworkScale = playerArtworkPageUsesScale,
                                 // ⚠⚠ 2026-10-06：下沉回弹的方向判据，**必须**与 host 侧同源。
                                 //   mini 侧原来硬编码 `{ true }` ⇒ 回弹/打开时也沉 87px
                                 //   （「下沉抽搐一下再往上」「点击很难看」）。详见
                                 //   `MeloXIOSMiniPlayer` 里 `sharedSinkBoundsTransform` 处的说明。
                                 closingIntent = playerClosingIntent,
                                miniPlayerHitRect = miniPlayerHitRect,
                                // ★★ 2026-11-02：把**稳态那份背板**显式喂给迷你栏玻璃。
                                //   转场期这一份会被渲进共享元素 overlay（独立 subcomposition，
                                //   `LocalMeloXBackdrop` 退回外层的 pageBackdrop），而稳态节点
                                //   采的是 `bottomChromeBackdrop` ⇒ 转场结束那一帧换源，
                                //   底色从专辑橙一帧跳成主页深灰（用户：「闪一下」）。
                                //   显式传同一个对象后，两个渲染位置逐帧采同一份内容 ⇒
                                //   **换源事件在结构上不存在**，播放页一个像素都不用淡。
                                //   ⚠ 仍保留遮挡门控：外壳把底栏整块盖住期间传 null ⇒
                                //     材质走不透明回落，不白付整屏采样（收起方向该门控
                                //     已被 MeloXChromeOcclusion 主动放行，恒为 false）。
                                // ★★ 2026-11-02（第二次）：**恒定传同一个对象**，不再因遮挡变 null。
                                //   原来 `occluded` 翻转会把采样源从 `bottomChromeBackdrop`
                                //   换成 `meloXGlassBackdrop()`（或反之）⇒ 换源那一帧要重新采样、
                                //   甚至重建材质节点，用户感受是「在动态背景和玻璃之间切换会顿一下」。
                                //   现在玻璃可见性完全由 `miniSurfaceVisible`（p < 0.52）门控：
                                //   p ≥ 0.52 时材质整块不挂，那时 `occluded` 早已为 true，本来也看不见
                                //   ⇒ 这个额外的遮挡判定只会制造换源，不再有任何省电收益。
                                glassBackdrop = bottomChromeBackdrop,
                            )
                        }
                    },
                )
                }
                }
            }

            // 「听歌识曲」就地弹层：与私信同一层，但不切 tab —— 关闭即退回打开它的页面。
            // 它是一整页（自绘不透明背板），所以走满宽推入/弹出；纯淡入淡出在这层读起来
            // 就是「啪一下换掉了」，等于没有动画。
            AnimatedVisibility(
                visible = recognitionVisible,
                enter = meloXPageEnter(veilColor = MaterialTheme.colorScheme.background),
                exit = meloXPageExit(),
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(if (listDetailMode) 0.5f else 1f)
                    .align(Alignment.TopEnd)
                    .zIndex(15f),
            ) {
                Box(
                    Modifier.fillMaxSize().meloXPageSlideGeometry(
                        settled = transition.currentState == transition.targetState,
                    ),
                ) {
                    BackHandler { recognitionVisible = false }
                    MeloXRecognitionOverlay(onBack = { recognitionVisible = false })
                }
            }

            playerTransition.AnimatedVisibility(
                // ⚠⚠ 2026-10-06 取证埋点：与 mini 侧同一目的的对照采样（见那里说明）。
                //   ⚠ 判定仍用 `value`（理由见 mini 侧：改读 targetState 会让内容在收起中提前卸载）。
                visible = { value ->
                    value
                },
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(20f),
            ) {
                val fullPlayerAnimatedVisibilityScope = this
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    MeloXIOSNowPlayingSharedHost(
                        state = playbackState,
                        onDismiss = closePlayer,
                        // ★ 转场可打断（2026-10-15）：关闭动画进行中，在播放面上点按一次
                        //   = 抢回播放页。与点迷你栏**同一条链路**（`openPlayer`）
                        //   ⇒ 动画从当前 fraction 连续回 1f，不跳变。
                        onReopen = openPlayer,
                        miniPlayerHitRect = miniPlayerHitRect,
                        onNavigateSearch = { query, kind ->
                            detailPaneState.clearPages()

                            if (selectedSource == MusicSource.Netease) {
                                MeloXSearchLaunchBus.post(query, kind)
                            }
                            selectedTab = AppTab.Search
                            closePlayer()
                        },
                        onOpenPlaybackSettings = {
                            selectedTab = AppTab.Settings
                            closePlayer()
                            MeloXSettingsDetailActivity.launch(hostContext, "Playback", selectedSource)
                        },
                        onLocalMetadataChanged = { playbackState.refreshCurrentLocalMetadata() },
                        onSeekCollapse = { fraction ->
                            // ⚠⚠⚠ 2026-10-06（用户诊断 + 真机探针实证）：这里**不能**写 `true`。
                            //   下沉（`meloXPlayerSinkFraction`）的语义是「**落定到迷你栏**的那一记
                            //   落地回弹」——它属于「完全收起」这个动作的收尾。而拖拽 / seek 阶段
                            //   **尚不知道**本次会不会走到完全收起：要等松手时
                            //   `settleFromCurrent` 的 `shouldCollapse`（距离 ≥42% 或速度 ≥1200）
                            //   才定得下来。
                            //   原实现同步写 `true` ⇒ 手指一旦拖过 f=0.62 就提前把下沉施加出来，
                            //   而松手**回弹**时 `onSettleCollapse(false)` 立刻把它撤掉 ⇒
                            //   播放器先被压下去 22dp 又被瞬间提回 ⇒ 用户报障
                            //   「下拉一点松手后，播放器会下沉抽搐一下再往上」、
                            //   「半程取消还会下沉（因为完全收起就有下沉）」。
                            //   ⇒ 拖拽阶段一律**不沉**（`false`）。只有两条**确定收起**的路径置 `true`：
                            //     · `closePlayer()` —— 点击关闭 / 系统返回（f 从 0 完整跑到 1，下沉相位完整）
                            //     · `onSettleCollapse(true)` —— 手势松手判定为收起
                            //   ⚠ 副作用（已知并接受）：若手指已拖过 f=0.62 才松手收起，那一趟的下沉
                            //     会从 `f_start` 处接上（不是从 0 渐入），幅度偏小但**方向与手指一致**
                            //     （本来就在往下落），实测不违和。
                            playerClosingIntent = false
                            // ⚠ 拖拽中必须写 false：既表达"此刻朝关闭"（内容层目标 0f），
                            //   又借这次目标变化让该动画保持活跃 ⇒ 才会被 seek 驱动 ⇒ 跟手。
                            playerTransitionTargetOpen = false
                            playerTransitionJob?.cancelAndJoin()
                            playerTransitionJob = null
                            // ★ 转场可打断：拖拽开始 ⇒ 撤销上一条待发的落定「咚」。
                            playerSettleThumpJob?.cancel()
                            playerSettleThumpJob = null
                            playerTransitionState.seekTo(
                                fraction = fraction.coerceIn(0f, 0.999f),
                                targetState = false,
                            )
                            // ⚠⚠ 2026-10-06 取证埋点（去重，值不变不写）：拖拽 seek 之后
                            //   transition 的 currentState / targetState。
                            //   ★ 2026-10-26 功耗：实参在门控内求值（拖拽每帧都会走到这里）。
                        },
                        onSettleCollapse = { collapse ->
                            // ★ 2026-10-26 功耗：整套取证埋点（`event` + settle 后 6 帧逐帧
                            //   `event`）在门控内。未 bind 时 `event()` 直接 return，但那 6 次
                            //   `withFrameNanos` 申请、字符串拼接、以及 6 帧的帧循环**照样发生**
                            //   —— 诊断器已于 2026-10-30 整体移除（见 LOCAL-CUSTOMIZATION.md）⇒ 此处无残留开销。
                            // `collapse == true` ⇒ 目标是 false（收起）⇒ 该趟仍属关闭方向。
                            playerClosingIntent = collapse
                            // ⚠⚠ 关键：**在 `animateTo` 之前同步写**，让内容层目标立刻变成
                            //   正确方向 —— 回弹（collapse=false）⇒ true ⇒ 目标 1f ⇒ 不再反向；
                            //   收起（collapse=true）⇒ false ⇒ 目标 0f。
                            //   用 `playerTransitionState.targetState` 会晚约 77ms（实测反向来源）。
                            playerTransitionTargetOpen = !collapse
                            playerTransitionJob?.cancelAndJoin()
                            playerTransitionJob = null
                            // ★ 转场可打断：上一条待发的落定「咚」先撤销（回弹 collapse=false
                            //   时更不能让它残留着、在回弹途中炸出来）。
                            playerSettleThumpJob?.cancel()
                            playerSettleThumpJob = null
                            // ★ 双拍下沉触感（与底栏同款）：只在 collapse=true（确定收起）时震。
                            //   拍 1 松手瞬间「啪」激活，拍 2 弹簧落定「咚」落定。
                            if (collapse) {
                                playerSpringThumpEngage()       // 拍 1：松手瞬间「啪」——激活下沉
                                playerSettleThumpJob = playerScope.launch {
                                    val settleMs = meloXPlayerTweenDurationMillis().toLong()
                                    kotlinx.coroutines.delay(settleMs)
                                    playerSpringThumpLand()     // 拍 2：弹簧落定「咚」——落定
                                }
                            }
                            if (collapse) {
                                // 收起方向：拖拽期 `onSeekCollapse` 一直用
                                // `seekTo(p, targetState = false)` 把 targetState 钉在 false
                                // ⇒ 这里新目标与它**相同** ⇒ `animateTo` 走"不重置"那条分支，
                                // fraction 从当前 p 连续跑到 1，与手指同向。
                                // 收起方向一直是对的（用户口径「下拉关闭的，那个是对的」），别动。
                                playerTransitionState.animateTo(
                                    targetState = false,
                                    animationSpec = playerSettleMasterSpec(),
                                )
                            } else {
                                // 回弹方向：⛔ 绝不能写 `animateTo(targetState = true)` ——
                                // 那会先"朝迷你栏沉一下再弹回"，就是用户报的抽搐。
                                // 反编译实证的完整根因见 [reboundPlayerToOpen] 的 KDoc。
                                reboundPlayerToOpen(playerTransitionState)
                            }
                        },
                        sharedTransitionScope = sharedScope,
                        animatedVisibilityScope = fullPlayerAnimatedVisibilityScope,
                        playerControlBackdrop = playerControlBackdrop,
                        closingIntent = playerClosingIntent,
                        // ⚠⚠ 2026-10-06：内容层 `expansionProgress` 的动画目标值镜像 ——
                        //   由 `MeloXApp` 在上述**每个主状态变更点**同步写入（早于 `animateTo`）。
                        //   详见 `playerTransitionTargetOpen` 声明处的说明（含两次踩坑记录）。
                        //   ⚠ 不要改成 `playerTransitionState.targetState`（晚 ~77ms ⇒ 会反向），
                        //     也不要改成 `closingIntent`（拖拽期不变 ⇒ 动画被判完成 ⇒ 不跟手）。
                        transitionTargetOpen = playerTransitionTargetOpen,
                        // ⛔⛔ 2026-10-06 已回滚：这里曾传 `playerTransitionState.fraction` 给 host
                        //   让 `expansionProgress` 直算 —— **在根组合体读 `fraction` = 整个 App 订阅
                        //   转场动画的每一帧** ⇒ 一次点击即"全树每帧重组" ⇒ **App 卡死**（用户报
                        //   「一点击播放键就卡住」）。**严禁在此处（或任何外层 composable）读
                        //   `SeekableTransitionState.fraction`**；如确需直算，用 `() -> Float`
                        //   lambda 把读取限制在 host 内部。
                        onArtworkPageChanged = { playerArtworkPageUsesScale = it },
                    )
                 }
             }

         }

        // 网易云登录已改为独立 Activity（MeloXNeteaseLoginActivity），走系统原生转场，
        // 与「设置 → 详情」这类页面完全一致；此处不再内嵌覆盖层。
        // 全局弹窗独立于左右栏导航，保持覆盖整个窗口。

        clipboardTarget?.let { target ->
            MeloXAppDialog(
                title = stringResource(R.string.app_clipboard_title),
                message = if (target is NeteaseClipboardTarget.Song) {
                    stringResource(R.string.app_clipboard_song)
                } else {
                    stringResource(R.string.app_clipboard_playlist)
                },
                onDismiss = onClipboardLinkConsumed,
                onConfirm = {
                    onClipboardLinkConsumed()
                    playerScope.launch {
                        val client = NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
                        val songs = withContext(Dispatchers.IO) {
                            runCatching {
                                when (target) {
                                    is NeteaseClipboardTarget.Song -> client.songDetailsBlocking(listOf(target.id))
                                    is NeteaseClipboardTarget.Playlist -> client.playlistDetailBlocking(target.id).songs
                                }
                            }.getOrDefault(emptyList())
                        }
                        PlaybackCommands.playAll(context, songs)
                    }
                },
            )
        }
        if (onboardingPage >= 0) {
            fun finishOnboarding() {
                MeloXSettingsPreferences.setBoolean(context, "onboarding_completed", true)
                onboardingPage = -1
                if (onboardingLoginRequested) launchNeteaseLogin(AppTab.Settings)
            }
            when (onboardingPage) {
                2 -> MeloXOnboardingLegalPage(
                    onAgree = {
                        MeloXSettingsPreferences.setString(context, "legal_consent_version", MELOX_LEGAL_VERSION)
                        MeloXSettingsPreferences.setLong(context, "legal_consent_at", System.currentTimeMillis())
                        if (cloudControlChoicePending) onboardingPage = 3 else finishOnboarding()
                    },
                    onDecline = { (hostContext as? Activity)?.finish() },
                    onOpenProject = {
                        runCatching {
                            hostContext.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lladlam/MeloX-Android")),
                            )
                        }
                    },
                )
                0 -> MeloXOnboardingWelcomePage(
                    onContinue = { onboardingPage = 1 },
                    onLicenses = {
                        runCatching {
                            hostContext.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/lladlam/MeloX-Android/blob/main/README.md#credits"),
                                ),
                            )
                        }
                    },
                )
                1 -> MeloXOnboardingAccountPage(
                    isLoggedIn = neteaseSession.isLoggedIn,
                    nickname = neteaseSession.profile?.nickname,
                    avatarUrl = neteaseSession.profile?.avatarUrl,
                    onLogin = {
                        onboardingLoginRequested = true
                        onboardingPage = 2
                    },
                    onFinish = {
                        onboardingLoginRequested = false
                        onboardingPage = 2
                    },
                )
                else -> MeloXOnboardingCloudPage(
                    onReject = {
                        MeloXRemoteConfigConsent.reject(context)
                        playerScope.launch { MeloXRemoteConfigRuntime.clearCache(context) }
                        cloudControlChoicePending = false
                        finishOnboarding()
                    },
                    onAccept = {
                        MeloXRemoteConfigConsent.accept(context)
                        MeloXRemoteConfigRuntime.initializeAndRefresh(context, BuildConfig.VERSION_CODE, force = true)
                        cloudControlChoicePending = false
                        finishOnboarding()
                    },
                )
            }
        }
        if (onboardingPage < 0 && cloudControlChoicePending) {
            MeloXOnboardingCloudPage(
                onReject = {
                    MeloXRemoteConfigConsent.reject(context)
                    playerScope.launch { MeloXRemoteConfigRuntime.clearCache(context) }
                    cloudControlChoicePending = false
                },
                onAccept = {
                    MeloXRemoteConfigConsent.accept(context)
                    MeloXRemoteConfigRuntime.initializeAndRefresh(context, BuildConfig.VERSION_CODE, force = true)
                    cloudControlChoicePending = false
                },
            )
        }
        availableUpdate?.takeIf {
            onboardingPage < 0 && !cloudControlChoicePending && pendingRemoteNotice == null
        }?.let { release ->
            MeloXUpdateDialog(
                release = release,
                downloadAvailable = release.apkUrl != null,
                onLater = { availableUpdate = null },
                onConfirm = {
                    availableUpdate = null
                    playerScope.launch {
                        val target = runCatching { MeloXUpdateClient(context).downloadUrl(release) }.getOrNull()
                            ?: release.pageUrl
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                    }
                },
            )
        }
        pendingRemoteNotice?.takeIf {
            onboardingPage < 0 && !cloudControlChoicePending
        }?.let { notice ->
            LaunchedEffect(notice.id, notice.frequency) {
                MeloXRemoteNoticeStore.markShown(context, notice)
            }
            MeloXRemoteNoticeDialog(
                notice = notice,
                onAcknowledge = { pendingRemoteNotice = null },
            )
        }
        // ── 冷启动管线预热（2026-10-19）────────────────────────────────────────
        //   清数据冷启动后**第一次**开播放页会掉 1~3 帧，真凶是 Adreno 驱动在
        //   RenderThread 上同步编译新 pipeline（日志：142 次 `Pipeline create failed`，
        //   UI 线程 lateness p50 0.25ms 完全无辜）。这里在首屏落定后用一小块不可见
        //   玻璃把最贵的那几族 pipeline 提前编掉。详见 `MeloXGlassPipelinePrewarm`。
        //   ⚠ 放在兄弟序列**最后** = z 序最上：HWUI 不做遮挡剔除，但不必赌。
        MeloXGlassPipelinePrewarm()
        if (BuildConfig.DEBUG && MeloXSettingsRuntime.performanceOverlayEnabled) {
            MeloXPerformanceOverlay()
        }
      }
    }
}

/**
 * 弹窗动作按钮的最小宽度。内容自适应宽度时用它保底，让「取消/确定」这类两字标签
 * 仍然看起来是一对等宽按钮，而不是缩成两个小圆点。
 *
 * ⚠ 2026-09-26 由 112dp 缩到 96dp：320dp 屏上「稍后再说 / 登录网易云音乐」这对
 *   组合内容宽度约 250dp（7 字 CJK ≈ 105dp），加 10dp 间距后总宽 250dp；
 *   若按钮保底 112dp，Row 就超出内容区（320−40−36=244dp）⇒ Text 被迫换行。
 *   缩到 96dp 之后总宽 222dp 稳落进 244dp，两个按钮各自再按内容撑开，视觉不变。
 */
private val DialogActionButtonMinWidth = 96.dp

@Composable
private fun MeloXAppDialog(
    title: String,
    message: String,
    dismissLabel: String = stringResource(R.string.action_cancel),
    confirmLabel: String = stringResource(R.string.app_confirm),
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MeloXGlassDialog(visible = true, onDismiss = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            message,
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
        )
        // ⚠ 不能平分：平分后每个按钮只有 ~129dp（内容区 97dp），6 个 CJK 字的标签
        //   （「登录网易云音乐」17sp ≈ 102dp）会被挤成两行。改为按各自文字自适应宽度、
        //   整组居中，widthIn 只保底 —— 「取消/确定」「稍后/下载 APK」这类短标签仍是等宽。
        // ⚠ 2026-09-26 追加跨设备防护：Text 强制单行 + Ellipsis。不同 CJK 字体
        //   （Noto Sans CJK vs MiSans vs 厂商字）对同一字符串的 em 宽度可能差 5~8%，
        //   保底宽度再紧就会换行。单行 + 溢出省略号是**跨字体一致**的降级 ——
        //   比「悄悄换两行、把 Row 撑高、破坏面板高度」的默认行为可预测得多。
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 18.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            MeloXGlassButton(
                onClick = onDismiss,
                modifier = Modifier.widthIn(min = DialogActionButtonMinWidth),
                style = MeloXGlassButtonStyle.Plain,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) { Text(dismissLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.width(10.dp))
            MeloXGlassButton(
                onClick = onConfirm,
                modifier = Modifier.widthIn(min = DialogActionButtonMinWidth),
                style = MeloXGlassButtonStyle.BorderedProminent,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) { Text(confirmLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun MeloXUpdateDialog(
    release: MeloXRelease,
    downloadAvailable: Boolean,
    onLater: () -> Unit,
    onConfirm: () -> Unit,
) {
    // Capped to the safe drawing area: a long release note scrolls inside the
    // panel instead of pushing the buttons off screen.
    MeloXGlassDialog(visible = true, onDismiss = onLater, maxHeightFraction = 0.92f) {
        Text(
            stringResource(R.string.app_update_found, release.version),
            style = MaterialTheme.typography.titleLarge,
        )
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(top = 10.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (release.name.isNotBlank()) {
                Text(
                    release.name,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (release.notes.isNotBlank()) {
                Text(
                    release.notes,
                    modifier = Modifier.padding(top = if (release.name.isNotBlank()) 8.dp else 0.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .64f),
                    lineHeight = 21.sp,
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            MeloXGlassButton(
                onClick = onLater,
                modifier = Modifier.widthIn(min = DialogActionButtonMinWidth),
                style = MeloXGlassButtonStyle.Plain,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) { Text(stringResource(R.string.app_update_later), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.width(10.dp))
            MeloXGlassButton(
                onClick = onConfirm,
                modifier = Modifier.widthIn(min = DialogActionButtonMinWidth),
                style = MeloXGlassButtonStyle.BorderedProminent,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    if (downloadAvailable) stringResource(R.string.app_update_download) else stringResource(R.string.app_update_view),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MeloXSectionShell(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 48.dp),
    ) {
        Text(
            text = title,
            fontSize = 36.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = subtitle,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
        )
    }
}

/**
 * 展开态下底栏 nav 胶囊的高度与搜索键边长 —— 同一个设计基准（360dp 屏）。
 * 收缩态对应 [ChromeCompactSize]。两处必须同源，否则展开态会出现
 * 「胶囊比邻居高一截」的错位。
 */
private val ChromeExpandedSize = 57.dp

/** 收缩态下底栏 nav 胶囊的高度与搜索键边长。 */
private val ChromeCompactSize = 46.dp

/**
 * 横屏双栏「底部档」（左下/右下）时 nav 胶囊的固定高度 —— **等于迷你栏高度**。
 *
 * 2026-10-20 用户指令：横屏分栏、底栏位置选左下/右下（`vertical==1`）时，把导航胶囊
 * 高度**减到跟迷你播放栏一样**（迷你栏非竖向高 = 48dp，见 `MeloXIOSMiniPlayer` 的
 * `miniHeight = if (vertical) … else 48.dp`），随后两者**垂直中心重合**。
 * `MeloXPositionedChrome` 的「中心线对齐」已按胶囊中心为共同中心摆迷你栏（两者都停在
 * 底部那档）；这里把胶囊高度也压到同一 48dp ⇒ 胶囊中心与迷你栏中心**完全重合**。
 *
 * ⚠ 只作用于「横屏 + 分栏 + 底部档」这一种几何：单栏竖屏、顶部/侧栏档位的
 *   [ChromeExpandedSize]/[ChromeCompactSize] 弹簧一律不动。
 */
private val SplitBottomDockChromeHeight = 48.dp

/**
 * ⚠ **2026-09-25 一笔勾销**：本常量（及其自研「实心着色胶囊」Layer 2 方案）已随
 *   一比一复刻 BiliNext `LiquidGlassTabsBar.kt` 全部删除 —— Layer 2 现在是一块
 *   **真玻璃**（`meloXLiquidCaptureLayer`），不再是本项目自创的着色胶囊。
 *   保留此墓碑仅为阻止后来者重新发明它。
 */

@Composable
private fun MeloXBottomChrome(
    selectedTab: AppTab,
    source: MusicSource,
    onSelect: (AppTab) -> Unit,
    hasMedia: Boolean,
    minimized: Boolean,
    visibleRootTabs: List<AppTab>,
    split: Boolean = false,
    navigationModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    /**
     * 底部 chrome 玻璃面的覆盖门控（2026-10-26，打开动画性能 B 方案）。
     *
     * 本函数只做两件事：
     *  ① 把 nav 胶囊 / 搜索键两个玻璃面的**窗口矩形**写进门控（普通字段，不触发重组）；
     *  ② 门控判定「外壳已完全盖住」时，把 `LocalMeloXBackdrop` 换成 `null` —— 于是
     *     `meloXGlassAvailable()` 为 false、`meloXGlassBackdrop()` 返回 null，每个材质
     *     函数走各自写好的**不透明** `background(stableSurface, shape)` 回落分支，
     *     `drawBackdrop`（整屏纹理采样 + blur/lens AGSL）一次都不跑。
     * ⚠ 只包 nav / 搜索键 / L2 / L3，**不包迷你栏**：迷你栏有自己的 `miniSurfaceVisible`
     *   闸门，且它是 sharedBounds 的源，不值得在这里多牵一条依赖。
     */
    chromeOcclusion: MeloXChromeOcclusionGate,
    miniPlayer: @Composable (compactProgress: Float) -> Unit,
) {
    val tabsBackdrop = rememberLayerBackdrop()
    val dockScope = rememberCoroutineScope()
    val coupled = meloXChromeScrollCoupled(split, MeloXSettingsRuntime.splitNavigationPosition, MeloXSettingsRuntime.splitMiniPlayerPosition)
    val inlineMini = !split || (coupled && !MeloXSettingsRuntime.splitNavigationPosition.isSideRail)
    val rawProgress by animateFloatAsState(
        targetValue = if (minimized && coupled) 1f else 0f,
        // 展开 / 收缩各用**一条完整弹簧**（方向不同、手感不同）：
        //   · 展开 bounce 0.245 → ζ=0.755，过冲 2.68% → 外扩 6.02dp（左 2.01 / 右 4.01dp，
        //     与搜索键的 7dp 缝留 3.00dp 视觉余量）—— 空间受限，所以收着弹、且**偏右**落；
        //   · 收缩 bounce 0.300 → ζ=0.700，过冲 4.60% → 10.30dp —— 四周空出来了，放开弹。
        // ⚠ 别让两个方向共用 spec：共用时保护邻居只剩「对展开方向事后压缩」一条路，
        //   而压缩后的曲线不再是弹簧（阻尼包络仍是 0.30 的），手感与收缩方向不对等。
        //   target 与 spec 在同一帧一起算 —— 方向就是 targetValue 本身。
        animationSpec = if (minimized) BottomBarCollapseSpec else BottomBarExpandSpec,
        label = "melox-tab-minimize-progress",
    )

    // ★ 底栏弹簧触感 —— **双拍节奏**（2026-10-06 优化）：
    //   不改强度，改「表现形式」。单记 LongPress 太单调，改成两拍：
    //     · 拍 1（按下瞬间，`Confirm` 中等）："啪"——确认已激活、开始运动；
    //     · 拍 2（弹簧落定，「LongPress」最重）："咚"——重重落定。
    //   两拍形成「激活 → 落定」的弹簧回弹节奏，交互反馈明显更立体。
    //   弹簧 stiffness=250 ⇒ response≈0.4s，落定约在切换后 450~500ms。
    //   延迟量 = 99% 落定时间 ≈ 6.2 / ω = 6.2 / √250 ≈ 392ms，取 450ms 留余量。
    //   ⚠ 快速来回切换时：新一轮 `LaunchedEffect` 启动会把上一轮的 450ms 延迟取消，
    //     故只有最终静止态的那一记落定会响，不会叠出双落定。
    val springHapticEngage = rememberSpringThump(HapticFeedbackType.Confirm)
    val springHapticLand = rememberSpringThump(HapticFeedbackType.LongPress)
    LaunchedEffect(minimized) {
        springHapticEngage()          // 拍 1：按下瞬间「啪」——激活
        kotlinx.coroutines.delay(450L) // 等弹簧落定
        springHapticLand()            // 拍 2：重重「咚」——落定
    }
    // 语义值（alpha / 图层门控 / 选中态判定）必须夹在 [0,1]，否则负 alpha 会炸。
    val progress = rawProgress.coerceIn(0f, 1f)

    // 几何值 = 弹簧本身（带符号限幅）。两侧对称、不过任何窗口 —— 详见 sprungFrac 注释：
    // 旧的窗口 [0.20,1.00] 在展开方向把几何钉死在弹簧峰值速度处，随后空转 246ms。
    val bounceFrac = sprungFrac(rawProgress)

    val labelStage = smoothStep(progress, 0.00f, 0.32f)   // alpha 用，保持夹住
    val dropStage = smoothStep(progress, 0.78f, 1.00f)    // 容器高度，不参与过冲

    // 横屏分栏「底部档」**且与迷你栏不同边**（如 nav 左下 / 迷你栏右下）：
    //   nav 胶囊高度固定压到 [SplitBottomDockChromeHeight]=48dp，与迷你栏同高，
    //   两者由 `MeloXPositionedChrome` 的「中心线对齐」摆成垂直中心重合。
    // ⚠ 2026-10-20 用户裁定：**同边时（nav 与迷你栏同在左下/右下）逻辑必须跟竖屏完全一样** ——
    //   那时走的是 inline 路径（`inlineMini=true`，迷你栏坐在胶囊上方、66dp lift），
    //   胶囊照旧 57↔46dp 弹簧、不缩、不特殊处理。
    //   ⇒ 这里必须额外要求「与迷你栏不同边」，只把独立摆放那一档压到 48dp。
    // 其它档位/单栏仍走 57↔46 的弹簧（展开↔收缩）。
    val splitBottomDock = split &&
        !MeloXSettingsRuntime.splitNavigationPosition.isSideRail &&
        MeloXSettingsRuntime.splitNavigationPosition.vertical == 1 &&
        !coupled
    val navHeight = lerpDpBouncy(
        if (splitBottomDock) SplitBottomDockChromeHeight else ChromeExpandedSize,
        if (splitBottomDock) SplitBottomDockChromeHeight else ChromeCompactSize,
        bounceFrac,
    )
    // ⚠ f 的**负方向**才是「展开外扩」（f=0 是展开稳态、f=1 是收缩稳态）。
    // 这里只夹下界：搜索键右对齐，若跟着外扩会吃掉与 nav 之间的固有间隙，
    // 而它自身的外扩量只有 0.9dp、肉眼不可见 —— 整段间隙留给 nav 的过冲更划算。
    // ★ 横屏分栏「底部档」时 nav 胶囊已被压到 [SplitBottomDockChromeHeight]=48dp
    //   （见下面 navHeight 的分支），搜索键必须**跟着一起缩到同一 48dp**，
    //   否则会出现「底栏矮了、搜索键还是 46~57dp」的比例失调（用户 2026-10-20 报障）。
    val searchSize = if (splitBottomDock) {
        SplitBottomDockChromeHeight
    } else {
        lerpDpBouncy(ChromeExpandedSize, ChromeCompactSize, bounceFrac.coerceAtLeast(0f))
    }
    val mediaReveal by animateFloatAsState(
        targetValue = if (hasMedia && inlineMini) 1f else 0f,
        // 下方是 miniLift × mediaReveal 的纯乘子，不经过 lerpDp 的夹取，
        // 所以过冲天然可见 —— 播放栏出现时会先"弹过"再落位。
        animationSpec = spring(
            dampingRatio = MeloXSprings.dampingRatio(MeloXSprings.MiniPlayerRevealBounce),
            stiffness = MeloXSprings.MiniPlayerRevealStiffness,
        ),
        label = "melox-mini-player-reveal",
    )
    // 收缩态容器高度（动画目标）。
    // ⚠ 横屏分栏「底部档且与迷你栏不同边」时 nav 胶囊被钉成
    //   [SplitBottomDockChromeHeight]=48dp（见下面 navHeight 分支），而外层 Column 还有
    //   top/bottom 各 4dp 的 padding（L1753-1756，上下对称）。
    //   `meloXChromeOffsets` 的中心线对齐是按 **navigation Box 的中心**摆迷你栏的 ⇒
    //   必须让「Box 中心 == 胶囊中心」，否则迷你栏会与胶囊错开。
    //   padding 上下各 4dp 已对称，所以只要内容区高度 == 胶囊高度(48dp) 即重合
    //   ⇒ `chromeHeight` 直接取 48dp，**不要**再 +8dp（那会让 Box 中心比胶囊中心高 4dp，
    //   表现为「迷你栏比胶囊高 4dp」—— 2026-10-20 用户报障「没有垂直中心对齐」）。
    //   收缩动画终点（`dropStage`=1）同步取 48dp ⇒ 两态都不错位。
    val expandedChromeHeight by animateDpAsState(
        targetValue = when {
            splitBottomDock -> SplitBottomDockChromeHeight
            hasMedia && inlineMini -> 124.dp
            else -> 64.dp
        },
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 360f),
        label = "melox-chrome-height",
    )
    val chromeHeight = if (splitBottomDock) {
        SplitBottomDockChromeHeight
    } else {
        lerpDp(expandedChromeHeight, 56.dp, dropStage)
    }
    val labelAlpha = 1f - labelStage
    val expandedLayerAlpha = 1f - smoothStep(progress, 0.43f, 0.72f)
    val compactLayerAlpha = smoothStep(progress, 0.52f, 0.82f)

    MeloXPositionedChrome(
        split = split,
        hasMedia = hasMedia && !inlineMini,
        coupledProgress = if (coupled) progress else 0f,
        navigationPosition = MeloXSettingsRuntime.splitNavigationPosition,
        miniPlayerPosition = MeloXSettingsRuntime.splitMiniPlayerPosition,
        modifier = modifier.then(if (split) Modifier.stableNavigationBarsPadding() else Modifier),
        miniPlayer = {
            Box(
                Modifier
                    .fillMaxWidth()
                    // ★ 双栏且迷你栏不在侧栏时，**外缘留白必须与 nav 那一侧同口径**：
                    //   `meloXChromeArea` 里 nav 的左/右缘取 `safeLeft/safeRight`
                    //   （= `gap` 8dp，见 MeloXPositionedChrome 的 controlConstraints）。
                    //   这里原来写死 12dp ⇒ 迷你栏距屏幕外缘 12dp、nav 距屏幕外缘 8dp，
  //   差 4dp，读起来就是「迷你栏比底栏长一截 / 两边不等距」（用户 2026-10-20 报障）。
                    //   ⇒ 统一取 8dp（= `gap`），两侧外间距完全相等。
                    .padding(
                        horizontal = if (MeloXSettingsRuntime.splitMiniPlayerPosition.isSideRail) 4.dp else 8.dp,
                        // 与左侧底栏同口径：双栏时下沿只留 2dp（贴底），单栏仍是 8dp。
                        vertical = if (split) 2.dp else 8.dp,
                    ),
            ) { miniPlayer(if (coupled) progress else 0f) }
        },
    ) {
    // ★★ 2026-10-26（打开动画性能 B 方案）：底部 chrome 的**玻璃材质总闸**。
    //   外壳把底栏整块不透明盖住期间，这里把 `LocalMeloXBackdrop` 换成 null ⇒
    //   `meloXGlassAvailable()` 为 false ⇒ `meloXGlassBackdrop()` 返回 null ⇒
    //   每个材质函数走各自写好的 `background(stableSurface, shape)` 回落分支
    //   （`compositeOver` 出来的**完全不透明**色，不是半透明），`drawBackdrop`、
    //   blur/lens 的 AGSL、整屏背板纹理采样**一次都不跑**。
    //   · `occluded` 是离散 State（一次开合最多翻 2 次）⇒ 重组最多 2 次，不逐帧。
    //   · 观感：这些面此刻 100% 被外壳压住，看不见 —— 判据严格到「外壳比底栏
    //     多盖住 4dp」才允许停（见 `MeloXChromeOcclusionGate` 的四道保险）。
    //   · ⚠ 只包这一层（nav / L2 / L3 / 搜索键）。迷你栏走它自己的 `miniSurfaceVisible`
    //     闸门，不在这里多牵依赖。
    val chromeGlassBackdrop =
        if (chromeOcclusion.occluded.value) null else LocalMeloXBackdrop.current
    CompositionLocalProvider(LocalMeloXBackdrop provides chromeGlassBackdrop) {
    if (split && MeloXSettingsRuntime.splitNavigationPosition.isSideRail) {
        // ⚠ 2026-10-26：**侧栏档没有走下面那套 nav 胶囊 / 搜索键**，所以本帧不会有人
        //   上报矩形 —— 若不在这里清空，`navRect` 会残留「上一次竖屏布局」的值，
        //   判据就会拿一个早已不存在的矩形去比 ⇒ 可能误判「已覆盖」而把玻璃停掉。
        //   用 `SideEffect`（apply 阶段）而不是组合体直写：本分支切进来时它先清空，
        //   切出去时下面 `onGloballyPositioned`（layout 阶段，晚于 apply）再写新值 ——
        //   同一帧内顺序天然正确。
        SideEffect {
            chromeOcclusion.navRect = null
            chromeOcclusion.searchRect = null
        }
        MeloXVerticalNavigation(selectedTab, source, visibleRootTabs, onSelect, navigationModifier, if (coupled) progress else 0f, hasMedia && coupled)
    } else {
    Column(
        modifier = navigationModifier
            .fillMaxWidth()
            // ⚠ 用 ViewCompat 稳定取数，不用 `navigationBarsPadding()`：edge-to-edge 后
            //   这条 Compose insets 链在本机读到 0，胶囊会直接压到系统手势条上（见
            //   stableNavigationBarsPadding 的说明）。
            .then(if (split) Modifier else Modifier.stableNavigationBarsPadding())
            // Mei keeps the 64dp navigation capsule 8dp above the gesture
            // inset, giving the dock the same breathing room as iOS.
            // ★ 双栏下沿再压：单栏仍是 8dp；双栏时几何侧已把下沿留白缩到 4dp
            //   （`SplitChromeBottomMargin`），这里同步收到 4dp ⇒ 合计 8dp，
            //   比原来的 16dp 更贴底（用户 2026-10-18「还可以再靠近底部一点」）。
            // ★ 双栏上下对称 4dp：`meloXChromeOffsets` 的**中心线对齐**是按两个 Box 的
            //   中心算的，只有上下内边距对称时「Box 中心」才等于「胶囊/迷你栏的视觉中心」。
            .padding(
                top = if (split) 4.dp else 0.dp,
                bottom = if (split) 4.dp else 8.dp,
            ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(chromeHeight),
        ) {
            // Issue #94: the transparent gaps between the dock, search button and
            // mini player were click-through to the page underneath. Keep one
            // blocker behind the controls so those gaps consume taps, while the
            // controls (later siblings, drawn on top) keep their own handlers.
            Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    detectTapGestures { }
                },
            )
            val horizontalMargin = 12.dp
            // ⚠⚠ 2026-10-26 用户口径：**收缩态那两个圆必须等大** ——
            //   右圆 = 搜索键，收缩态 `searchSize` = [ChromeCompactSize] = 46dp（46×46 正圆）；
            //   左圆 = nav 胶囊，收缩态高度本来就是 [ChromeCompactSize] = 46dp，
            //   所以只要把**收缩宽度**也取同一个常量，左圆就变成 46×46，与右圆逐像素等大
            //   （两者 shape 都是 Capsule，正方形上即为正圆）。
            //   ⚠ 旧值 48.dp 是「nav 比搜索键宽 2dp」的历史遗留 —— 它同时也是「迷你栏离
            //   右圆更远」（左缝 8dp / 右缝 10dp）的成因之一，别再改回去。
            //   ⚠ 只影响收缩态宽度：展开态走 `expandedNavWidth`，与这里无关。
            val compactSize = ChromeCompactSize
            // nav 胶囊右缘 ↔ 搜索键左缘的固有间隙（展开态）。
            // 官方截图实测 = **10px = 7.12dp**（rim 峰法：nav 右缘 472 → 搜索键左缘 483，
            // 阈值 140~205 全区间稳定），取 7dp。旧值 12dp 是「先加大缝好让过冲有地方去」的临时口径。
            // ⚠ 这个缝同时是展开方向弹簧的约束：它定出右缘可用外扩 = 7 − 3（视觉余量）= 4dp，
            //   再按 NavExpandLeftShare(左1:右2) 反推出总外扩 6dp 与 BottomBarExpandBounce 0.245。
            //   改这个值必须回头重算那个常数。
            val searchGap = 7.dp
            val compactGap = 8.dp
            val expandedNavWidth =
                maxWidth - horizontalMargin * 2 - searchGap - ChromeExpandedSize
            // 宽度就是弹簧本身：lerpDpBouncy 不夹取，展开方向 f<0 会让胶囊长到
            // 272 + 6.02 = 278.02dp —— **不需要按预算压缩、也不需要限幅**，因为展开那条
            // 弹簧（BottomBarExpandBounce）本来就是照着这点余地定出来的。
            val navWidth = lerpDpBouncy(expandedNavWidth, compactSize, bounceFrac)
            // 外扩 = 胶囊变大，多出来的宽度往哪边落由 NavExpandLeftShare 决定：
            // 现取 **左 1 : 右 2（偏右）** —— 右侧固有缝只有 7dp，那点余量必须花在
            // 「朝搜索键弹过去」这个方向上；左侧是 12dp 屏幕边距、没有邻居，让出 2dp 很安全。
            // 代价：nav 中心在峰值轻微右漂 1dp（148 → 149），这是「右边弹得更多」的必然结果，
            // 不是 bug —— 想消除就只能回到 1/2 等分，而那正是被否掉的「对称」。
            // 收缩方向 f>0 宽度不超稳态，coerceAtLeast(0.dp) 让越界量归零 → 退回纯边距，
            // 也就是收缩天然是**左锚定**（左缘恒 12dp、右缘 284 → 60dp）。
            // 旧的「先喂右边、余量给左边」+「等比压缩」两层都被这一行替掉了。
            val navOffsetX = horizontalMargin -
                (navWidth - expandedNavWidth).coerceAtLeast(0.dp) * NavExpandLeftShare
            val navShape = Capsule()
            val primaryTabs = visibleRootTabs.map { it to it.rootGlyph() }

            // ⚠⚠ 2026-10-26 修「收缩态迷你栏离右圆比离左圆远」——
            //   收缩态一行三件套 = 【左圆 | 迷你栏 | 右圆】，两个圆是**两个不同的东西**：
            //     · 左圆 = nav 胶囊收缩态，宽度 = `compactSize`；
            //     · 右圆 = 搜索键，宽度 = `compactSearchSize`（收缩态 = [ChromeCompactSize]）。
            //   两者现在**已同为 46dp**（用户要求等大），但这里**仍分别引用**而不是写
            //   `compactSize * 2`：旧写法假设「两边都是 48dp」，而搜索键只有 46dp ⇒ 迷你栏
            //   右缘少算 2dp ⇒ 左缝 8dp / 右缝 10dp。真机像素实证（360dp 宽 / density 4，
            //   y 取底栏中心行）：左圆右缘 240px → 迷你栏左缘 272px = **32px(8dp)**，
            //   迷你栏右缘 1168px → 右圆左缘 1208px = **40px(10dp)**。
            //   分别扣 ⇒ 两个缝都精确等于 `compactGap`(8dp)，且任一侧尺寸再变也不会回归。
            //   ⚠ `splitBottomDock` 档搜索键被钉到 `SplitBottomDockChromeHeight`(48dp)，
            //     但那一档 `inlineMini=false` ⇒ 本段（含 `miniWrapperWidth`）根本不执行，
            //     故这里不必分支。
            val compactSearchSize = ChromeCompactSize
            val desiredCompactMiniVisibleWidth =
                (maxWidth - horizontalMargin * 2 - compactSize - compactSearchSize - compactGap * 2)
                    .coerceAtLeast(80.dp)
            val compactMiniWrapperWidth = desiredCompactMiniVisibleWidth
            val compactMiniWrapperX = horizontalMargin + compactSize + compactGap
            val miniWrapperWidth = lerpDpBouncy(maxWidth - horizontalMargin * 2, compactMiniWrapperWidth, bounceFrac)
            val miniWrapperX = lerpDpBouncy(horizontalMargin, compactMiniWrapperX, bounceFrac)
            // ⚠ 这里**不能**给 `splitBottomDock`（分栏 + 底部档 + 不同边）开特例：
            //   那一档 `inlineMini=false`，本 Box 整段不执行（迷你栏由 `MeloXPositionedChrome`
            //   独立摆放并按中心线对齐）。而同边时 `splitBottomDock` 已为 false
            //   ⇒ 66↔0 的 lift 一律保持原语义，与竖屏完全一致（2026-10-20 用户裁定）。
            val miniLift = lerpDpBouncy(66.dp, 0.dp, bounceFrac) * mediaReveal
            // 收缩态下播放栏需与两侧底栏按钮/搜索键共中心线。
            // 实测播放栏整体比按钮高 1dp（top/bottom 各差 4px），
            // 这里随收缩进度补一个 1dp 下移，把两者垂直中心压平。
            val miniCenterAlignNudge = lerpDpBouncy(0.dp, 1.dp, bounceFrac) * mediaReveal

            if (hasMedia && inlineMini) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(
                            x = miniWrapperX,
                            y = -miniLift + miniCenterAlignNudge,
                        )
                        .width(miniWrapperWidth),
                ) {
                    miniPlayer(progress)
                }
            }

            // ════════════════════════════════════════════════════════════════════════
            //  一比一复刻 BiliNext `LiquidGlassTabsBar.kt`（2026-09-25 用户指令）
            //
            //  权威源：D:/bilinext/app/src/main/java/com/android/bilinext/ui/
            //          components/LiquidGlassTabsBar.kt   （Apache-2.0 → GPL-3.0 允许）
            //
            //  BiliNext 三层结构（原值，**不再自创**）：
            //    L1 可见面板   : height = maxHeight(= navHeight)
            //                    + padding(horizontal = 10dp)
            //                    + vibrancy + blur(8dp) + lens(24dp·aspectScale, 24dp)
            //                    + layerBlock { scaleX = 1; scaleY = 1 }   ← 不随按压缩放
            //                    + Highlight(0.55·2·rim) / InnerShadow / Shadow
            //                    + onDrawSurface = drawRect(containerColor)
            //    L2 隐形捕获层 : height = DropletHeight = **navHeight − 8dp**
            //                    + padding(horizontal = 10dp)
            //                    + 同一套 vibrancy/blur/lens 但 **H、A 都 ×p**
            //                    + highlight ×p；**无 shadow / innerShadow**
            //                    + onDrawSurface = drawRect(containerColor)
            //                    + 内含**同一份 tab 内容**（图标用 accent 色染）
            //    L3 水珠       : height = DropletHeight（**与 L2 等高**）
            //                    width  = tabWidth × 1.15 + 2dp
            //                    + lens(8.4dp·p, 11.8dp·p, chromatic)   // 10/14 × 47/56
            //                    + Highlight/Shadow/InnerShadow(8dp·p) 全部 ×p
            //                    + layerBlock { scaleX = damped.scaleX; … 速度修正 }
            //                      ← **挂 dampedDragAnimation.modifier ⇒ 缩放真的生效**
            //                    + onDrawSurface = 0.22 黑/白 ×(1−p) + 0.03 黑 ×p
            //    ⇒ 源码 L101-102 的恒等式：
            //       「水珠 = Layer 2 高度，且都小于 Layer 1，让水珠边缘恰好触及 Layer 2 边界」
            //
            //  ⚠ 本项目的两处**保留差异**（用户口径优先，不跟 BiliNext 改）：
            //    ① 指示器颜色 = 黑/白（MeloX 用户拍板「指示器是黑白，不是蓝色」），
            //       BiliNext 的 accent 蓝只染 L2 的 tab 图标。
            //    ② 选中 tab 图标用红色（MeloX 既有主题口径），非 BiliNext 的 accent 蓝。
            // ════════════════════════════════════════════════════════════════════════
            // ⚠ 这里**不能再读 `isMeloXDarkTheme()`** —— 必须接同一个 `LocalBottomBarTone`，
            //   否则在 d 处于中间态时，容器色 / 指示器色会与别层的进度脱节。
            val tone = bottomBarTone()
            val frostedGlass = MeloXSettingsRuntime.frostedGlassEnabled
            // BiliNext L131-133：containerColor = 0xFAFAFA/0x121212 @ 0.35
            val containerColor = lerp(
                Color(0xFFFAFAFA).copy(0.35f),
                Color(0xFF121212).copy(0.35f),
                tone,
            )
            // 指示器本体涂层（黑白）。2026-10-02 定档：**黑白分档**——
            //   浅色（黑涂层）0.18（用户定档；黑涂层直接封顶内容红明度，终色 R = 1−a = 0.82）；
            //   深色（白涂层）0.25（用户终定：0.3 回试后改回 0.25；A/B 结论白侧 0.25 更优）。
            // 与 accentColor 联动：终色 = (1−a)×源 + a×涂层；源色纯红 ⇒
            //   黑涂层下极限红 0xFFD10000；白涂层 0.25 下终色 = 0xFFFF4000（R=1, G=0.25）。
            val selectionTint = lerp(
                Color.Black.copy(0.18f),
                Color.White.copy(0.25f),
                tone,
            )


            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = navOffsetX, y = 0.dp)
                    .width(navWidth)
                    .height(navHeight)
                    // ★ 2026-10-26（打开动画性能 B 方案）：把 **nav 胶囊玻璃面的窗口矩形**
                    //   交给覆盖门控。写的是普通字段（不是 State）⇒ 转场/收缩期这个回调
                    //   每帧都触发（navWidth 是弹簧），但零重组、零失效，只有一次赋值。
                    //   ⚠ 上报的是**这个 Box**（= 玻璃面的真实矩形），不是整个底栏容器：
                    //     容器是 fillMaxWidth，左下角永远落在系统屏幕圆角外面，拿它判会让
                    //     闸门恒为 false、白做。
                    .onGloballyPositioned { chromeOcclusion.navRect = it.boundsInWindow() },
                contentAlignment = Alignment.CenterStart,
            ) {
                val density = LocalDensity.current
                val tabCount = primaryTabs.size.coerceAtLeast(1)

                // BiliNext L152-156：tabWidth = (maxWidth − 20dp) / n（= 两侧 10dp padding）
                val tabWidth = (maxWidth - 20.dp) / tabCount
                val tabWidthPx = with(density) { tabWidth.toPx() }

                // ★ 核心恒等式：水珠与 Layer 2 **等高**，两者都比 Layer 1 矮 8dp
                //   （BiliNext：L1=55dp / L2=DropletHeight=47dp / L3=47dp）
                val dropletHeight = (navHeight - 8.dp).coerceAtLeast(1.dp)
                // 🔴 2026-10-27 横屏修（用户报「横屏长按指示器折射范围错了、折进 tab」）：
                //   折射深度必须按**实际水珠/胶囊高度**换算，而不是写死竖屏的绝对值。
                //   竖屏展开 49dp ⇒ 1f（逐像素不变）；横屏 splitBottomDock 40dp ⇒ 40/49≈0.816。
                //   ⛔ 不转换 ⇒ 24dp/10dp 的绝对深度套到更矮的胶囊上 ⇒ 相对过深 ⇒ 长按时
                //   整条折射带折进 tab 内、只剩被放大的图标内容。
                val pressLensHeightRatio = (dropletHeight.value / 49f).coerceIn(0.5f, 1.2f)
                // ⚠ `dropletWidth` 必须夹取，`dropletHeight` 同理（上面已夹）。
                //   `navWidth` 是**不夹取的弹簧**（见 `lerpDpBouncy` 的注释），收缩/回弹过冲的
                //   某一帧会跌破 `compactSize`(48dp)；而 `tabWidth = (navWidth − 20dp)/tabCount`
                //   在 navWidth < 20dp 时变负 ⇒ `dropletWidth` 跟着变 0 / 负。
                //   水珠是 `drawBackdrop` 节点，0 或负尺寸会让 `GraphicsLayer.record()` 拿到
                //   空/非法尺寸并在 draw 阶段直接抛（不在任何 try/catch 内）。
                //   你 vivo（360dp 宽 / 固定 4 tab）过冲后仍为正所以没触发；
                //   **窄屏（320~340dp）、折叠屏外屏、开「显示大小」放大**的机型会。
                //   夹到 1.dp 只在退化帧生效，正常区间数值完全不变。
                // ★ 边缘 tab（value=0 / value=tabCount−1）时水珠左/右缘必须离胶囊外缘
                //   至少 `capsuleEdgeInset`。水珠中心在 value=0 处 = `edgePadding + 0.5·tabWidth`
                //   （edgePadding=10dp 是 tab 行内边距，见 L1807 的 translationX 起点）：
                //     dropletLeft = edgePadding + 0.5·tabWidth − dropletWidth/2 ≥ capsuleEdgeInset
                //   ⇒ dropletWidth ≤ tabWidth + 2·(edgePadding − capsuleEdgeInset)。
                //   `capsuleEdgeInset = 4.dp` 刻意 = 上下 inset（水珠与 L2 等高 = navHeight − 8dp，
                //   在 CenterStart 的 Box 里垂直居中 ⇒ 上下各 4dp），保证**左右不比上下少**。
                //   正常 4 tab 竖屏 tabWidth≈63dp，1.15×公式(≈74dp) 远小于上限(≈75dp) 不触发；
                //   **只有 tab 少(2 tab)或横屏 tabWidth 变大时 1.15×公式才超上限**，此时夹到上限
                //   ⇒ 边缘水珠恰离胶囊缘 4dp（与上下一致），不再溢出（修复真机「横屏/2-tab 指示器超长」）。
                //   `coerceAtMost` 只在水珠本就会溢出时收缩，正常区间数值恒等、观感零变化。
                val capsuleEdgeInset = 4.dp
                val edgePadding = 10.dp
                val dropletWidth = (tabWidth * 1.15f + 2.dp)
                    .coerceAtMost(tabWidth + (edgePadding - capsuleEdgeInset) * 2f)
                    .coerceAtLeast(1.dp)

                val selectedIndex = primaryTabs.indexOfFirst { it.first == selectedTab }
                val dockExpanded = progress < 0.56f
                val currentProgress by rememberUpdatedState(progress)
                val currentSelectedTab by rememberUpdatedState(selectedTab)
                val currentPrimaryTabs by rememberUpdatedState(primaryTabs)
                val currentTabWidthPx by rememberUpdatedState(tabWidthPx)
                val currentOnSelect by rememberUpdatedState(onSelect)
                // ⚠ **必须走 rememberUpdatedState**（2026-09-25 修 bug）。
                //   `dampedDock` 是 `remember(tabCount)` —— 只有 tab 数变化才会重建，
                //   所以它内部所有 lambda 会在**首次组合**时把当时的局部值**捕获死**。
                //   `selectedIndex` 若不是 State 包装，`onDragStopped` 里那句
                //   `if (target != selectedIndex)` 就永远在跟「App 启动时的选中下标」比：
                //   启动在首页(0) ⇒ 拖动指示器回首页时 `0 != 0` 为假 ⇒ **不触发切换**
                //   （真机症状：「拖到首页 tab 没反应，只有点击才行」）。
                val currentSelectedIndex by rememberUpdatedState(selectedIndex)
                val currentDropletWidthPx by rememberUpdatedState(with(density) { dropletWidth.toPx() })

                // BiliNext L158-166 面板 recoil：拖动时整条面板轻微反向偏移
                val panelOffsetAnim = remember { Animatable(0f) }
                val panelOffset by remember(density) {
                    derivedStateOf {
                        val fraction = (panelOffsetAnim.value / constraints.maxWidth)
                            .coerceIn(-1f, 1f)
                        val sign = if (fraction >= 0f) 1f else -1f
                        with(density) {
                            4.dp.toPx() * sign * EaseOut.transform(abs(fraction))
                        }
                    }
                }

                val dampedDock = remember(tabCount) {
                    PublicDampedDragAnimation(
                        animationScope = dockScope,
                        initialValue = selectedIndex.coerceAtLeast(0).toFloat(),
                        valueRange = 0f..(tabCount - 1).toFloat(),
                        visibilityThreshold = 0.001f,
                        initialScale = 1f,
                        // BiliNext L185：pressedScale = 78/56
                        pressedScale = 78f / 56f,
                        onTap = { position ->
                            // ⚠ position.x 是**水珠局部**坐标（手势挂在 L3 水珠 Box 上，
                            //   宽仅 ~1.15 个 tab）。旧算法把它当面板坐标直接除 tabWidthPx
                            //   ⇒ 恒落在 0/1 ⇒ 「点水珠盖着的 tab 必跳首页」。
                            //   换算：水珠中心当前停在 value 格，落点 = value + 局部位移/tabWidth。
                            val index = (value +
                                (position.x - currentDropletWidthPx / 2f) /
                                currentTabWidthPx.coerceAtLeast(1f))
                                .roundToInt()
                                .coerceIn(0, tabCount - 1)
                            if (currentProgress < 0.56f) {
                                currentOnSelect(currentPrimaryTabs[index].first)
                            } else if (currentProgress >= 0.68f) {
                                currentOnSelect(currentSelectedTab)
                            }
                        },
                        onDragStopped = {
                            val target = targetValue.roundToInt().coerceIn(0, tabCount - 1)
                            animateToValue(target.toFloat())
                            // ⚠ 用 `currentSelectedIndex`（rememberUpdatedState）而不是捕获死的
                            //   `selectedIndex` —— 见上面 currentSelectedIndex 的长注释。
                            if (target != currentSelectedIndex) {
                                currentOnSelect(currentPrimaryTabs[target].first)
                            }
                            dockScope.launch {
                                panelOffsetAnim.animateTo(
                                    0f,
                                    spring(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = 0.5f),
                                )
                            }
                        },
                        onDrag = { _, dragAmount ->
                            updateValue(
                                (targetValue + dragAmount.x / currentTabWidthPx.coerceAtLeast(1f))
                                    .coerceIn(0f, (tabCount - 1).toFloat()),
                            )
                            dockScope.launch {
                                panelOffsetAnim.snapTo(panelOffsetAnim.value + dragAmount.x)
                            }
                        },
                    )
                }
                // 「呼出」判定：上一个选中是搜索 ⇒ 这次是从搜索返回，水珠要 Q 弹落格
                // （animateToValueBouncy 带过冲）；普通 tab 切换/拖拽回位仍走临界阻尼。
                var prevSelectedTab by remember { mutableStateOf(selectedTab) }
                LaunchedEffect(selectedIndex, dockExpanded, selectedTab) {
                    if (dockExpanded && selectedIndex >= 0) {
                        if (prevSelectedTab == AppTab.Search && selectedTab != AppTab.Search) {
                            dampedDock.animateToValueBouncy(selectedIndex.toFloat())
                        } else {
                            dampedDock.animateToValue(selectedIndex.toFloat())
                        }
                    }
                    prevSelectedTab = selectedTab
                }
                val dockHighlight = remember(dockScope, dampedDock) {
                    PublicInteractiveHighlight(
                        animationScope = dockScope,
                        position = { size, _ ->
                            // BiliNext：(value + 0.5) × tabWidthPx + panelOffset
                            Offset(
                                (dampedDock.value + 0.5f) * currentTabWidthPx + panelOffset,
                                size.height / 2f,
                            )
                        },
                    )
                }

                val tabsBackdrop = rememberLayerBackdrop()
                // BiliNext L245：L2 的 tab 缩放（按压时图标放大 1.2×，只作用于捕获层内容）
                val tabScale: () -> Float = { lerp(1f, 1.2f, dampedDock.pressProgress) }
                // BiliNext L130：`accentColor = if (isDarkTheme) TabAccentDark else TabAccentLight`。
                // MeloX 保留自己的口径 —— 强调色 = 主题红（与选中 tab / 收缩态圆心标一致）。
                // 2026-10-02 终版：**极限红方案**（用户拍板「不要求 iOS 红，红色够明显即可，
                // 暗红也没关系」）。源色 = 纯红 0xFFFF0000，G/B 归零 ⇒ 混色后饱和度最大：
                //   浅色（黑涂层 0.18）: 终色 = 0.82×(1,0,0) = **0xFFD10000** —— 深红、饱和度拉满。
                //   深色（白涂层 0.25）: 终色 = 0xFFFF4000 —— 比 0.3 档（0xFFFF4D4D）更纯。
                // ⚠ 与 selectionTint 的 alpha 绑定（终色 = (1−a)×源 + a×涂层）。
                // 「涂料下沉」试验（tint 搬进捕获层、真机无差异）已回退。
                // 只影响 L2 捕获层（水珠透出的红）；毛玻璃模式 / 收缩态圆心标仍用
                // MeloXSystemColors.Red。
                val accentColor = Color(0xFFFF0000)

                // ══ Layer 1：可见面板（BiliNext 原样）════════════════════════════
                // ⚠ **收缩态必须把 tab 行淡出**（2026-09-25 修 bug）：
                //   `navWidth` 会从展开 272dp 弹簧到 `compactSize` 48dp，而本 Row 里是
                //   `primaryTabs.forEach { RootTabButton(weight(1f)) }` —— 4 个 tab 在
                //   48dp − 20dp(padding) = 28dp 里各分 7dp，24dp 图标互相重叠、挤出容器，
                //   真机表现为**左侧一个糊成一团的畸形图标**（截图 tmp/mx_col2_bar.png）。
                //   ⇒ 用既有的 `expandedLayerAlpha`（progress 0.43→0.72 之间淡出）把
                //     **图标那层**隐去，收缩态改由下面的 compact 层渲染单个圆心标。
                //   ⚠ 淡出必须只作用在**内容**上，不能连 `meloXLiquidBottomBar` 一起淡 ——
                //     面板玻璃在收缩全程都得在（否则收缩中胶囊会闪一下消失）。
                Row(
                    modifier = Modifier
                        .graphicsLayer { translationX = panelOffset }
                        .meloXLiquidBottomBar(
                            shape = navShape,
                            tint = bottomLiquidGlassTint(),
                            surfaceColor = bottomGlassSurfaceColor(),
                            // 官方 LiquidBottomTabs L174-179：面板随水珠按压整体膨胀
                            // （scale = 1 + 16dp/width · p）。原先写死 0f，长按无反馈。
                            pressProgress = dampedDock.pressProgress,
                            // ★ 唯一的亮度采样录制点（2026-10-19）。搜索键/水珠/
                            //   迷你栏都**不能**传，否则互相覆盖。
                            luminanceLayer = LocalBottomBarLuminanceLayer.current,
                        )
                        .then(dockHighlight.modifier)
                        .height(navHeight)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 内容层：随收缩进度淡出（不含面板材质）
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = expandedLayerAlpha }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        primaryTabs.forEach { (tab, glyph) ->
                            RootTabButton(
                                title = tab.titleFor(source),
                                glyph = glyph,
                                // BiliNext L364：可见面板的图标**永远用未激活色**，
                                // 强调色只来自 L2 捕获层透出（`colorFilterTint = null` ⇒
                                // 这里的 `selected` 已无着色作用，仅用于关掉点击后的 shape 语义）。
                                selected = false,
                                // ⚠ **毛玻璃模式的红色唯一来源**：毛玻璃下 L3 水珠整段不挂
                                //   （见下面 `if (indicatorAlpha > 0.001f && !frostedGlass)`），
                                //   L2 那张「全染强调色」的捕获层也就无从被采样 ⇒ 强调色彻底失去出口，
                                //   选中 tab 会退化成和未选中完全一样。⇒ 让 L1 自己上色。
                                //   与 `selected` 解耦的原因见 `RootTabButton` 的参数注释。
                                selectedTint = frostedGlass && tab == selectedTab,
                                contentColor = if (tab == selectedTab) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f) else null,
                                labelAlpha = labelAlpha,
                                interactive = dockExpanded,
                                compactMetrics = splitBottomDock,
                                onClick = { onSelect(tab) },
                            )
                        }
                    }
                }

                // ══ 收缩态圆心标（MeloX 既有惯例，BiliNext 的「末位 tab 居中」等价物）════
                //   收缩后胶囊只剩 48dp 宽，4 个 tab 放不下 ⇒ 只留**当前选中 tab**的图标，
                //   居中、红色、25dp。`expandedLayerAlpha`/`compactLayerAlpha` 是一条交叉淡入
                //   淡出（0.43~0.72 淡出旧层 / 0.52~0.82 淡入新层），中间有重叠区，避免闪空。
                if (compactLayerAlpha > 0.001f) {
                    // ⚠ 收缩态「当前选中」图标：选中搜索时搜索键是独立的右侧按钮，
                    //   左侧收缩胶囊没有搜索格可显示 ⇒ 默认回落首页房子图标（与上面
                    //   「选搜索时左侧指示器消失」同一个语义：搜索不算左侧 nav 的一员）。
                    //   选搜索时图标**不着红色**——红色是「左侧 nav 当前选中」的语义，
                    //   回落的房子只是占位，用未激活色（与右侧搜索键未选中态同口径）。
                    val compactGlyph =
                        if (selectedTab == AppTab.Search) RootGlyph.Home else selectedTab.rootGlyph()
                    // ⚠ 收缩态点击展开（2026-09-25 修 bug）：
                    //   展开的手势只挂在 L3 水珠上（dampedDock.modifier @ L1404），而水珠在收缩态
                    //   被定位到胶囊最左缘（(value+0.5)*tabWidth，tabWidth 仅 7dp），并不在用户看到的
                    //   居中红图标下方；L2 捕获层又 alpha(0f) 铺满整条并 onClick={} 拦截 ⇒
                    //   点中间的「左边按钮」命中不到展开手势，无法展开。
                    //   ⇒ 让收缩态这块居中红图标本身可点：zIndex 抬到最上层（盖过 L2/L3），
                    //      **仅在收缩态**（compactLayerAlpha>0.5f）挂 clickable 调 onSelect(selectedTab)
                    //     （onSelect 在调用方会把 tabBarMinimized=false ⇒ 展开）。
                    //   ⚠ 展开态不挂 clickable，否则会抢掉 L1 tab 的点击 / 水珠拖拽。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = compactLayerAlpha }
                            .zIndex(2f)
                            .then(
                                if (compactLayerAlpha > 0.5f) {
                                    Modifier.clickable { onSelect(selectedTab) }
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        RootGlyphIcon(
                            glyph = compactGlyph,
                            modifier = Modifier.size(25.dp),
                            color = if (selectedTab == AppTab.Search) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MeloXSystemColors.Red
                            },
                            selected = selectedTab != AppTab.Search,
                        )
                    }
                }

                // ══ Layer 2：隐形捕获层（BiliNext 原样，**一块真玻璃**）══════════
                CompositionLocalProvider(LocalGlassTabScale provides tabScale) {
                    Row(
                        modifier = Modifier
                            .clearAndSetSemantics { }
                            .alpha(0f)
                            .layerBackdrop(tabsBackdrop)
                            .graphicsLayer { translationX = panelOffset }
                            .meloXLiquidCaptureLayer(
                                shape = Capsule(),
                                surfaceColor = bottomGlassSurfaceColor(),
                                pressProgress = dampedDock.pressProgress,
                                // 🔴 横屏修：按实际胶囊高度换算折射深度（竖屏=1f 不变）。
                                lensScale = pressLensHeightRatio,
                            )
                            .then(dockHighlight.modifier)
                            .height(dropletHeight)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        primaryTabs.forEach { (tab, glyph) ->
                            RootTabButton(
                                title = tab.titleFor(source),
                                glyph = glyph,
                                // ⚠ 捕获层里 `selected` 必须传 false、改用 colorFilterTint ——
                                //   见 RootTabButton 里 colorFilterTint 的长注释。
                                //   传 true 会让「只有选中格有色」，水珠划过别的格就没有变色效果。
                                selected = false,
                                labelAlpha = labelAlpha,
                                interactive = false,
                                // 必须与 L1 可见面板同源，否则水珠采样出来的那一格
                                // 图标/字号与旁边面板不一致（像素级错位）。
                                compactMetrics = splitBottomDock,
                                // kyant0 / BiliNext 的「划过才变色」核心：
                                //   整层 tab 全染强调色（层 alpha=0 看不见），
                                //   水珠一采样 ⇒ 只把它盖住那一格"照"出来。
                                colorFilterTint = accentColor,
                                onClick = {},
                            )
                        }
                    }
                }

                // ══ Layer 3：水珠指示器（BiliNext 原样，**缩放真的生效**）════════
                // BiliNext L444-448：alpha 走弹簧（不是二值开关），退出搜索时同步淡出
                // ⚠ 选中搜索键时左侧指示器消失：搜索不在 primaryTabs 里、没有对应水珠格，
                //   仍挂着会停在上一格导致「选中态错位」。所以选搜索时 target 直接归 0。
                val indicatorAlpha by animateFloatAsState(
                    targetValue = if (dockExpanded && selectedTab != AppTab.Search) 1f else 0f,
                    animationSpec = spring(stiffness = 180f, dampingRatio = 0.7f),
                    label = "melox-indicator-alpha",
                )
                if (indicatorAlpha > 0.001f && !frostedGlass) {
                    // ⚠ 原先这里建了一个 `rememberCombinedBackdrop(...)` 赋给 `indicatorBackdrop`
                    //   但**从未被使用**（真正生效的是下面 `meloXLiquidTabSelection` 内部按
                    //   `(LocalMeloXBackdrop, panelBackdrop = tabsBackdrop)` 自己建的那一枚）。
                    //   它白白多占一整张 GPU 图层，且因为挂在 `if (indicatorAlpha > 0.001f && ...)`
                    //   这个条件分支里，水珠每次显隐都会重新分配/释放一次 —— 低端机上等于
                    //   反复抖动一大块显存。已删除；组合关系由 `meloXLiquidTabSelection` 内部负责。
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                // BiliNext L489-494：
                                //   tx = 10dp + (value + 0.5)·tabWidthPx − size.width/2 + panelOffset
                                translationX = with(density) { 10.dp.toPx() } +
                                    (dampedDock.value + 0.5f) * tabWidthPx -
                                    size.width / 2f +
                                    panelOffset
                                alpha = indicatorAlpha
                            }
                            .padding(horizontal = 10.dp)
                            .then(dockHighlight.gestureModifier)
                            .then(dampedDock.modifier)
                            .meloXLiquidTabSelection(
                                shape = Capsule(),
                                selected = true,
                                tint = selectionTint,
                                panelBackdrop = tabsBackdrop,
                                pressProgress = dampedDock.pressProgress,
                                // 🔴 横屏修：把**实际水珠高度**传进去，lens 换算系数按它现算
                                //   （竖屏 49dp→49/56=0.875 不变；横屏 40dp→40/56≈0.714）。
                                dropletHeight = dropletHeight,
                                // 🔴 2026-11-07 横屏修（用户报「横屏小底栏按住 tab 的水珠折射
                                //   范围过大、垂直同时折到图标和文字」）：横屏 splitBottomDock
                                //   水珠仅 40dp 高，且 compactMetrics 图标+文字挤得更满，
                                //   即便折射已按 40/56 等比换算，同样的相对深度在矮而满的
                                //   水珠里也容易刮到文字行。⇒ 横屏档额外收紧折射到 0.7×，
                                //   让折射带只碰图标、不再折进文字；竖屏保持 1f 不变。
                                lensDepthScale = if (splitBottomDock) 0.7f else 1f,
                                // ★ BiliNext L534-541：缩放**照抄**，因为水珠挂了
                                //   dampedDock.modifier ⇒ press() 对整个子树生效，
                                //   scaleX/scaleY 会真的走到 pressedScale = 78/56。
                                layerBlock = {
                                    // ⚠ 这段是**裸变换**，直接写进 HWUI RenderNode 的变换矩阵。
                                    //   `dampedDock.scaleX/velocity` 由弹簧驱动，必须保证
                                    //   NaN / ±Inf 不进矩阵（矩阵不可逆 ⇒ 绘制结果未定义，
                                    //   在能跑玻璃的机型上就是一次「自身原因」的异常）。
                                    //   正常区间数值恒等，观感零变化。
                                    scaleX = dampedDock.scaleX.finiteOrZero()
                                    scaleY = dampedDock.scaleY.finiteOrZero()
                                    val velocity = (dampedDock.velocity / 10f).finiteOrZero()
                                    scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                                    scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                                    scaleX = scaleX.finiteOrZero()
                                    scaleY = scaleY.finiteOrZero()
                                },
                            )
                            .height(dropletHeight)
                            .width(dropletWidth),
                        contentAlignment = Alignment.Center,
                    ) {
                    }
                }
            }

            val searchContentDescription = stringResource(R.string.tab_search)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = -horizontalMargin, y = 0.dp)
                    .size(searchSize)
                    // ★ 2026-10-26（打开动画性能 B 方案）：搜索键玻璃面的窗口矩形。
                    .onGloballyPositioned { chromeOcclusion.searchRect = it.boundsInWindow() }
                    // 搜索键材质与底栏 nav 胶囊完全同源：同样走
                    // meloXLiquidBottomBar（vibrancy + blur + lens，且只叠
                    // surfaceColor、不额外加 Screen 白纱与 Hue tint），
                    // 避免观感与左侧底栏对不上。
                    .meloXLiquidBottomBar(
                        shape = Capsule(),
                        tint = bottomLiquidGlassTint(),
                        surfaceColor = bottomGlassSurfaceColor(),
                        refractionHeight = lerpDp(10.dp, 8.dp, bounceFrac),
                    )
                    .clickable(role = Role.Button) { onSelect(AppTab.Search) }
                    .semantics {
                        contentDescription = searchContentDescription
                    },
                contentAlignment = Alignment.Center,
            ) {
                RootGlyphIcon(
                    glyph = RootGlyph.Search,
                    // ⚠ 图标跟着按键一起缩：splitBottomDock 下按键被钉到 48dp，
                    //   仍用 28dp 会显得按键里塞满（那是 57dp 按键的比例）。
                    //   28/57 ≈ 26/48 ⇒ 取 26dp 留出呼吸感。
                    modifier = Modifier.size(
                        if (splitBottomDock) 26.dp else lerpDp(28.dp, 27.dp, bounceFrac)
                    ),
                    // ⚠ 搜索键的字形同样不能写死 —— 它读的是同一个被插值的 scheme。
                    color = if (selectedTab == AppTab.Search) {
                        MeloXSystemColors.Red
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    selected = selectedTab == AppTab.Search,
                )
            }
        }
    }
    }
    }
}

}

@Composable
private fun MeloXVerticalNavigation(
    selectedTab: AppTab,
    source: MusicSource,
    tabs: List<AppTab>,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    compactProgress: Float = 0f,
    reserveMiniSlot: Boolean = false,
) {
    Box(modifier.fillMaxWidth().animateContentSize().padding(horizontal = 4.dp, vertical = 8.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .meloXLiquidBottomBar(
                    shape = Capsule(),
                    tint = bottomLiquidGlassTint(),
                    surfaceColor = bottomGlassSurfaceColor(),
                    // ★ 双栏（侧栏导航）时导航胶囊不在 1975 那个分支里，录制点搬到这里
                    //   —— 两个分支互斥（见 `MeloXBottomChrome` 的 split/sideRail 判定），
                    //   所以任意时刻只有一个面在 record。
                    luminanceLayer = LocalBottomBarLuminanceLayer.current,
                )
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            (if (compactProgress > .5f) listOf(
                if (selectedTab == AppTab.Search) tabs.firstOrNull() ?: AppTab.Settings else selectedTab,
                AppTab.Search,
            ) else tabs + AppTab.Search).distinct().forEach { tab ->
                if (tab == AppTab.Search && compactProgress > .5f && reserveMiniSlot) Spacer(Modifier.height(56.dp))
                val active = tab == selectedTab
                val color = if (active) MeloXSystemColors.Red else MaterialTheme.colorScheme.onSurface
                Column(
                    Modifier.fillMaxWidth().height(56.dp)
                        .semantics { selected = active }
                        .clickable(role = Role.Tab, onClick = { onSelect(tab) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    RootGlyphIcon(tab.rootGlyph(), Modifier.size(24.dp), color, selected = active)
                    Text(tab.titleFor(source), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = color)
                }
            }
        }
    }
}

@Composable
private fun RowScope.RootTabButton(
    title: String,
    glyph: RootGlyph,
    selected: Boolean,
    // ⚠ **只上强调色，不动字形/语义** —— 专给「毛玻璃模式下没有水珠指示器」用。
    //   为什么不直接复用 `selected`：`selected` 是**三件事捆在一起**的开关 ——
    //     ① `desiredForeground` 的强调红；② `RootGlyphIcon` 的 Fill/Regular 字形；
    //     ③ 语义 `selected`。
    //   而液态模式透过水珠看到的那张图来自 **L2 捕获层**，那里是恒 `selected = false`
    //   ⇒ 折射出来的是 **Regular 轮廓 + 强调红 + SemiBold 标签**。
    //   若毛玻璃模式直接传 `selected = true`，字形会跳成 Fill ⇒ 同一个 tab 在两种
    //   模式下的**图标形状**都对不上（「毛玻璃模式的房子是实心的，玻璃模式是空心的」）。
    //   ⇒ 拆出这个参数，让毛玻璃模式与液态模式的观感逐项相等。
    selectedTint: Boolean = false,
    labelAlpha: Float,
    // 原先这里有个 `dark: Boolean` 且完全没人用。改成「整棵子树插值配色」后，
    // 前景直接读 `colorScheme.onSurface` 就会跟着动 ⇒ 该参数连调用点一并删掉。
    interactive: Boolean = true,
    // ⚠ **「指示器划过才变色」的全部机关就在这一个参数**（BiliNext `GlassTabItem.colorFilterTint`）：
    //   非 null ⇒ 该 tab 无条件用这个颜色（`selected` 被无视），用于 **L2 隐形捕获层**，
    //   把整层 tab 都染成强调色；层本身 `alpha(0f)` 看不见，**只有水珠采样到的那一格会被"照"出来**
    //   ⇒ 水珠移到哪，哪格就亮成强调色 —— 不需要任何逐 tab 动画。
    //   L1 可见面板传 null ⇒ `selected` 生效（MeloX 保留红色口径）。
    colorFilterTint: Color? = null,
    // 2026-10-02 新增：iOS 风格「感知对比度增强」的内容颜色补偿。
    //   非 null 时覆盖默认的 `onSurface.copy(0.78f)`，用于 L1 可见面板的选中 tab。
    //   优先级低于 `colorFilterTint`（L2 捕获层）和 `selectedTint`（毛玻璃模式），
    //   所以只在 L1 选中 tab 且算法激活时生效。
    contentColor: Color? = null,
    // 横屏分栏「底部档」时 nav 胶囊只有 48dp 高（SplitBottomDockChromeHeight），
    // 图标/字号若仍用 57dp 胶囊的比例（24dp 图标 + 11sp 文字）会在竖向挤爆。
    // true ⇒ 图标 20（占位盒 + iconSize 同值）、字号 10sp、上下 padding 2dp，
    // 与 48dp 高度成比例。
    compactMetrics: Boolean = false,
    onClick: () -> Unit,
) {
    val desiredForeground = when {
        colorFilterTint != null -> colorFilterTint
        // `selectedTint`（毛玻璃模式的强调色）与 `selected` 同色、同动画路径。
        selected || selectedTint -> MeloXSystemColors.Red
        // 算法补偿色：iOS 风格「内容穿透指示器」的可读性补偿。
        contentColor != null -> contentColor
        // 回到 `colorScheme.onSurface` —— 外层 `BottomBarToneTheme` 已经把整套配色
        // 插值过，这个读值自己就会跟着动，**不需要**再在这里写端点。
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    }
    val foreground by animateColorAsState(
        targetValue = desiredForeground,
        // ⚠ 2026-11-07 修「底栏变色慢」：原 `spring(0.84, 480)` 刚度太低，
        //   切 tab 时颜色从灰缓"爬"到红、还带轻微过冲，肉眼觉得迟钝。
        //   ⇒ 抬到临界阻尼 + 高刚度（与 MeloXNowPlayingCoreControls 快速变色
        //   口径  spring(1.0, 1500) 同档），颜色瞬时跟手，无颜色过冲晃动。
        animationSpec = spring(dampingRatio = 1f, stiffness = 1200f),
        label = "melox-tab-foreground",
    )
    // BiliNext `GlassTabItem(scaleOverride = tabScale)` 的等价物：
    // Layer 2 捕获层里的 tab 内容随按压放大（1 → 1.2×），只影响**被水珠折射的那张图**。
    // 可见面板（Layer 1）不提供 LocalGlassTabScale ⇒ 默认恒 1f，不受影响。
    val tabScale = LocalGlassTabScale.current()
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = tabScale
                scaleY = tabScale
            }
            // ⚠ 2026-10-27 修「横屏分栏底部档时 tab 文字被截断」：
            //   48dp 胶囊（`SplitBottomDockChromeHeight`）里上下各 4dp padding 只剩 40dp，
            //   而「21dp 图标 + 12sp 行高」= 33dp 已经顶到边——真机放大图能看到
            //   齿轮图标上缘、选中 tab 文字下沿各被裁 1~2px（用户报「设置文字被截断」）。
            //   ⇒ compact 档把上下 padding 收到 2dp（省 4dp），给内容留出真实余量。
            //   竖屏（非 compact）仍是 4dp，观感零变化。
            .padding(horizontal = 4.dp, vertical = if (compactMetrics) 2.dp else 4.dp)
            .then(
                if (interactive) Modifier
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                    .semantics {
                        contentDescription = title
                        role = Role.Tab
                        this.selected = selected
                    }
                else Modifier.clearAndSetSemantics { },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // ★ 2026-10-27：占位盒与 `iconSize` 必须**同一个数**（见上方 kdoc）。
        // 2026-11-06：竖屏档 24 → 22sp，底栏四图标整体缩小一点点（用户要求），
        //   占位盒 + iconSize 仍同值，口径统一不变。
        val glyphSize = if (compactMetrics) 20.sp else 22.sp
        RootGlyphIcon(
            glyph = glyph,
            // 底栏所有 tab 现在都走 `MeloXSymbolIcon`（Home 已于 2026-10-25 迁入它的
            // 矢量分支，见 `MeloXSymbol.Home`），**不再有绕过统一口径的特例**。
            //   · Settings / Home：品牌矢量，内部用 `meloXVectorRenderSize(iconSize)`
            //     ⇒ 绘制盒由 `iconSize` 推导（占位盒再与之相交，两者同值 ⇒ 等价）。
            //   · 其余 tab：SF 字形，按 `iconSize`(sp) 渲染，**不受占位盒约束**
            //     （Canvas 不裁剪，墨迹直接溢出）。
            // ⇒ 统一给 20/24（占位盒 + iconSize 同值）。旧版单独给 Home 23.6dp 的补偿，
            //   前提是全局 0.83 还在（23.6 × 0.825 ≈ 19.5dp 去对齐当时的 Library）；
            //   0.83 撤销后两者同比放大，该补偿已无意义并删除。
            modifier = Modifier.size(glyphSize.value.dp),
            // ⚠⚠ 2026-10-27 修「横屏底栏 tab 图标没优化、大到顶文字」：
            //   上一版只把**占位盒**压到 20dp，却漏了 [MeloXSymbolIcon] 的
            //   `iconSize`（默认 24.sp）⇒
            //     · SF 字形那 4~5 个 tab：`SfGlyphIcon` 的 `textSize` 仍按 24sp 算，
            //       墨迹 ≈ 23~24dp 画在 20dp 的 Canvas 里，**Canvas 不裁剪** ⇒ 上下各
            //       溢出 1.5~2dp，正好顶到下面 10sp 的文字 —— 就是用户看到的「顶」；
            //     · Home / Settings 走矢量分支，盒子由 `iconSize` 推导 ⇒ 真缩到 20dp。
            //   ⇒ 同一行出现「矢量 20dp / 字形 24dp」两种尺寸，且字形偏大。
            //   现在 `iconSize` 与占位盒同值 ⇒ 6 条绘制路径一起缩，口径统一。
            iconSize = glyphSize,
            color = foreground,
            selected = selected,
        )
        // compact 档再给图标/文字之间留 2dp 呼吸（48dp 胶囊里实测仍有余量：
        // 2dp padding + 20dp 图标 + 2dp + 11sp 行高 ≈ 35dp < 48dp）。
        if (compactMetrics) {
            Spacer(Modifier.height(2.dp))
        }
        Text(
            text = title,
            modifier = Modifier.graphicsLayer { alpha = labelAlpha },
            fontSize = if (compactMetrics) 10.sp else 11.sp,
            lineHeight = if (compactMetrics) 11.sp else 13.sp,
            // BiliNext L704：`colorFilterTint != null` 也算「强调」→ SemiBold
            // ⚠ `selectedTint` 也算：液态模式下透过水珠看到的标签就是 SemiBold
            //   （L2 恒 `colorFilterTint != null`）⇒ 毛玻璃模式必须同字重，否则
            //   「切到毛玻璃标签变细」。
            fontWeight = if (selected || selectedTint || colorFilterTint != null) {
                FontWeight.SemiBold
            } else {
                FontWeight.Medium
            },
            color = foreground,
        )
    }
}

private enum class RootGlyph { Home, Explore, Library, Podcasts, Downloads, Cloud, Settings, Search }

@Composable
@Suppress("UNUSED_PARAMETER")
private fun AppTab.titleFor(source: MusicSource): String = stringResource(titleRes)

private fun AppTab.rootGlyph(): RootGlyph = when (this) {
    AppTab.Home -> RootGlyph.Home
    AppTab.Explore -> RootGlyph.Explore
    AppTab.Library -> RootGlyph.Library
    AppTab.Podcasts -> RootGlyph.Podcasts
    AppTab.Downloads -> RootGlyph.Downloads
    AppTab.Cloud -> RootGlyph.Cloud
    AppTab.Settings -> RootGlyph.Settings
    AppTab.Search -> RootGlyph.Search
}

@Composable
private fun RootGlyphIcon(
    glyph: RootGlyph,
    modifier: Modifier,
    color: Color,
    // ⚠ 2026-10-27：这是**墨迹字号**，不是占位盒尺寸。
    //   · SF 字形：按它算 `textSize` ⇒ 墨迹 = 本值 × ink_h/em，**与占位盒无关**。
    //   · 矢量（Home/Settings/…）：绘制盒由它推导（[meloXVectorRenderSize]）。
    //   ⇒ 调用方想改图标大小，**两个都要给**（同值），只改 modifier 对 SF 字形无效。
    iconSize: TextUnit = 24.sp,
    selected: Boolean = false,
) {
    MeloXSymbolIcon(
        symbol = when (glyph) {
            RootGlyph.Home -> MeloXSymbol.Home
            RootGlyph.Explore -> MeloXSymbol.Explore
            RootGlyph.Library -> MeloXSymbol.Library
            RootGlyph.Podcasts -> MeloXSymbol.RadioWaves
            RootGlyph.Downloads -> MeloXSymbol.Download
            RootGlyph.Cloud -> MeloXSymbol.Storage
            RootGlyph.Settings -> MeloXSymbol.Settings
            RootGlyph.Search -> MeloXSymbol.Search
        },
        modifier = modifier,
        color = color,
        iconSize = iconSize,
        variant = if (selected) MeloXSymbolVariant.Fill else MeloXSymbolVariant.Regular,
    )
}

// ⚠⚠ 2026-10-03：时长改走 `meloXPlayerTweenDurationMillis()`（**唯一时长出口**）。
//   Compose 的 `tween(D)` 会被 `MotionDurationScale` 自动乘 `ANIMATOR_DURATION_SCALE`
//   （本机实测三键全 1.35 ⇒ 设 360ms 实跑 486ms，用户报障「速度跟设置对不上」）。
//   除以系统缩放后，**墙钟恰好等于设置值**，且与 `MeloXIOSNowPlayingSharedHost` /
//   `MeloXIOSMiniPlayer` / `MeloXPlayerRecedeSpec` 三处**同一口径**
//   （它们都读同一个函数，见 `MeloXPlayerTransitionSpec`）。
//   ⚠ 本函数是驱动 `SeekableTransitionState` 的那条 master，**漏这一处 = 整体错拍**。
//
// ⚠⚠⚠ 2026-10-03 回退记录（**别再试**）：曾被改成 `MeloXPlayerAppleEasing`，并把几何 /
//   圆角 / 内容 / recede **全部改成线性跟随 fraction**（把「形状」统一到 fraction 一处），
//   目的是把尾段下沉窗口在墙钟上从 ~89ms 铺开到 ~156ms。**用户实测「反而比上版本效果差」**
//   ⇒ 已整条回退。
//
// ⚠⚠ 2026-10-06 更新：本函数**当前已不再被 `playerOpen/CloseFractionSpec` 调用** ——
//   那两条已统一到用户认可的 [playerSettleMasterSpec]。这里**特意保留**，专供一行回退：
//   把 `playerOpenFractionSpec` / `playerCloseFractionSpec` 改回本函数即可。
//   ⚠ 上面那条「fraction 保持线性」的历史结论，针对的是「把几何/圆角/内容/recede
//     **全部**改成线性跟随 fraction」；与 2026-10-06 这次「**只换 master 这一条曲线、
//     几何层原样不动**」是两件事，不要混为一谈。
@Suppress("unused")
private fun playerAutomaticFractionSpec() = tween<Float>(
    durationMillis = meloXPlayerTweenDurationMillis(),
    easing = LinearEasing,
)

/**
 * 播放器**四条路径共用**的那条 master（点迷你栏打开 / 点击关闭 / 系统返回 / 下拉手势）：
 * ζ=1 临界阻尼弹簧。
 *
 * ⚠⚠⚠ 2026-10-06：`stiffness` **必须由「设置时长」反解，绝不能写死**。
 *   用户报障：「播放页打开动画设置…**调节没反应**」。根因：`spring` **完全不读**
 *   `MotionDurationScale`、也不读设置时长 —— 之前写死 `stiffness = 420f` ⇒
 *   拖动「播放器展开/收回时长」对整条动画**毫无作用**（只有几何 bounds 那一层读设置，
 *   观感上几乎看不出来）。用户口径：「**把动画速度跟设置数值对应**」。
 *   ⇒ 反解：ζ=1 时 99% 落定 ≈ `k / ω`（k = [PLAYER_SETTLE_99_K]）⇒ `ω = k / D`
 *     ⇒ `stiffness = ω²`。这样「设置里选的毫秒数」就是真实落定时间。
 *   ⚠ 用 [meloXPlayerTweenDurationMillis]（**已抵消系统动画缩放**）而不是裸设置值：
 *     与几何 bounds 的 `durationMillis` 逐字同源，系统缩放 ≠ 1 时两者仍严格同步。
 *
 * ⚠ 与 2026-10-04 那次「master 换弹簧 ⇒ 效果更差」的区别（**别再混淆**）：
 *   那次 ω 由 **240ms** 反解 ⇒ `stiffness = (6.2/0.24)² = 667`（很硬），
 *   把前段推到「100ms 就走完 95%」才被否。**公式相同，但现在默认时长是 360ms**
 *   ⇒ ω ≈ 17.2、`stiffness ≈ 297`（比 420 软、比 667 软得多）。
 *   ⚠ 用户此前认可的手势那条 `420` ≈ **302ms 档**的反解值 ⇒ 设置调到 300ms 即与之几乎一致。
 */
// ⚠⚠ 2026-10-26：**6.2 保持不变**（曾试 4.5 想给打开动画降速，真机被否「还是慢了」）。
//   现在**只服务手势路径**（`MeloXApp.kt:1385` 下拉松手收起 / `reboundPlayerToOpen`），
//   用户明令「下拉关闭那个是对的，别动」⇒ 此值恢复原值，勿再拿它调打开动画。
//   打开/关闭的 master 已改回线性 tween（硬收尾 = 设置值），与此常量无关。
private const val PLAYER_SETTLE_99_K = 6.2f

// ── 转场期底栏玻璃「录制降频」（2026-10-26 **已整体回退**）──────────────────────
// 用户 10:35 口径：「**改底栏帧率之前，完全是正常和符合我心意的**」⇒ 连同当天所有
// 播放器动画改动一起回退。**本轮不再保留任何降频代码**（tick / derivedStateOf /
// 切 draw 失效源的常量全部删除），`drawBehind` 恢复成逐帧读 `recedeFraction()`。
//
// 情报留档（以后要重做请重新设计，别照抄，尤其**别和动画改动混在同一个包里验证**）：
// · 录制点是 recede Box 上的 `layerBackdrop`（`graphicsLayer` 之外，唯一一处平行层）；
//   转场期靠 `drawBehind { recedeFraction() }` 每帧强制失效 ⇒ 每帧整屏 `recordLayer`
//   + **18.25MB** 纹理上传，是 morph 期 RenderThread `issue` 13~17ms 的最大一块。
// · 当时做法：把失效源换成 16ms 低频 tick ⇒ 上传降到 ≈9.1MB/帧、玻璃滞后 ≤16ms；
//   录制节点**永不 detach**（detach 后 kyant `drawBackdrop` 直接 return ⇒ 玻璃只剩
//   底色 = 半透明，用户明令不许再出现）⇒ 结构上不可能半透明。
// · ⚠ 副作用未定论：帧率提升本身可能让人**主观觉得动画变快**（更顺 = 更快）。
//   这就是为什么它必须单独出一个包验证，不能和 master 曲线改动一起上。

private fun playerSettleMasterSpec(): FiniteAnimationSpec<Float> {
    // ★★★ 2026-10-27 六次定档：`durationSec` 用**墙钟**（保持 12:39 口径），
    //   只把 `visibilityThreshold` 从 0.001 放宽到 **0.01**（用户：「240 整个动画很怪」）。
    //
    // 🔴 为什么 `durationSec` 要除以 `MeloXSystemScale.animatorScale`：
    //   2026-10-28 19:00 真机 `mxdiag` 逐帧实测给出定论 —— `tween` 与 `spring`（含本处
    //   ζ=1）**净效果完全相同**，都会被系统 `MotionDurationScale`(=ANIMATOR_DURATION_SCALE)
    //   拉长成「设置值 × animatorScale」（本机设置 360 ⇒ 实跑 ~486~520ms）。
    //   同一条关闭时间线里的 tween 分支（外壳 morph / 内容层）已用 `fixedDurationMillis`
    //   把固有时长除以 animatorScale 抵消 ⇒ 墙钟 = 设置值（360ms）。
    //   ⇒ 若 master 弹簧不折算，它就比外壳多跑 ~1.35× ⇒ 整段关闭偏慢、收尾错拍
    //     （即用户报的「不丝滑 / 收尾底栏回弹卡卡」）。把 `durationSec` 也除以
    //     animatorScale ⇒ 弹簧墙钟 = 设置值，与外壳 / 0.6.5 严格同源同速。
    //   （⚠ 旧注释称「spring 不吃缩放」已被上述实测推翻；按墙钟不折算才会跑到 ~520ms。）
    //
    // 🔴 「240 很怪」的真凶仍是**阈值**：0.001 要求弹簧跑到 z = 9.23 才算收敛
    //   （`[1−(1+z)e^−z] < 0.001`），而 0.01 只要 z = 6.64 —— 光阈值就多跑 39%。
    //   240 档（折算前 ω = 6.2/0.24 = 25.8）：
    //     · 阈值 0.001 ⇒ 收敛 **358ms**（折算前墙钟），子动画 240ms 就跑完 ⇒ **118ms 空窗**；
    //     · 阈值 0.01  ⇒ 收敛 **257ms**，只晚 17ms ⇒ 正常。
    //   ⚠ 手势回弹 `reboundPlayerToOpen()` 走自己的时长，未受影响。
    // 🔴🔴 2026-10-29 00:2x **回退到 0.7.0 原值**（真机 A/B 证明 0.7.0 才是手感基准）：
    //   ① `durationSec` 走 `meloXPlayerTweenDurationMillis()`（= **唯一时长出口**，已抵消
    //      系统缩放），⛔ 不是「裸设置值 ÷ animatorScale」——后者是 10-28 我自己加的，
    //      0.7.0 HEAD 里没有，语义也不等价（设置值可被用户改，tween 出口才是 spec 口径）。
    //   ② `visibilityThreshold` **0.01 → 0.001**（0.7.0 原值）。
    //      ⚠ 这是「回弹不对 / 硬归位」的真凶：阈值 0.01 让弹簧 z=6.64 就收敛、
    //      0.001 要跑到 z=9.23 ⇒ **0.01 比 0.001 早 39% 收尾**。而外壳 morph 是固定
    //      360ms 墙钟 tween、不受阈值影响 ⇒ master（内容层/背景页）提前坐定、外壳还在动
    //      = 收尾错拍。这正是前面记过的「master 早于子动画 ⇒ 在途动画被吸附到终点」。
    val durationSec = (meloXPlayerTweenDurationMillis().coerceAtLeast(80)) / 1000f
    val omega = PLAYER_SETTLE_99_K / durationSec
    return spring(
        dampingRatio = 1.0f,
        stiffness = omega * omega,
        visibilityThreshold = 0.001f,
    )
}

/**
 * 下拉手势**松手回弹**：把 [SeekableTransitionState.fraction] 从当前收起进度弹回 0
 * （= 完全展开），**全程不改 `targetState`**。
 *
 * ⚠⚠⚠ 2026-10-19 用户报障「播放页下拉松手会自动往下移的抽搐动画，正常应该是松手就
 *   上移归位」的**真根因**（反编译 `animation-core-1.12.0` 的
 *   `SeekableTransitionState$animateTo$2$1` 逐行实证，不是推测）：
 *
 *   `animateTo(targetState)` 在**新目标 ≠ 当前 `targetState`** 时走的是重置分支：
 *   ```
 *   moveAnimationToInitialState()
 *   setFraction(0f)                    // fraction 归零
 *   transition.updateTarget(newTarget)
 *   setPlayTimeNanos(0)
 *   setCurrentState(oldTargetState)    // ← currentState 被设成**旧目标**（= 收起）
 *   setTargetState(newTarget)
 *   ```
 *   而拖拽期间 [onSeekCollapse 对应的那边] 一直用 `seekTo(p, targetState = false)`
 *   把 `targetState` 钉在 `false` ⇒ 松手回弹调 `animateTo(true)` **必然**命中这条重置
 *   分支：`currentState` 变成 `false`（收起态）、fraction 归零 ⇒ 视觉基准瞬间改成
 *   **完全收起**，再由 fraction 0→1 拉回展开。叠上 `moveAnimationToInitialState()`
 *   留在队列里的那批"初始动画"（它们仍在朝收起方向跑），屏幕上就是
 *   **先朝迷你栏沉一下再弹回** —— 89fps 录屏实测：反向点在 t≈3.99s，
 *   随后 7 帧（≈80ms）继续收缩，之后才回弹。那记"自动往下移"就是它。
 *
 *   反过来，「松手收起」调 `animateTo(false)` 时新目标与当前 `targetState` **相同**
 *   ⇒ 走另一条分支（fraction 从 p 连续跑到 1，不重置）⇒ 收起方向一直是对的。
 *   这就是为什么只有回弹抽搐、而「下拉关闭」用户一直说没问题。
 *
 * 正确做法：`targetState` 保持 `false` 不动，只让 `fraction` 从 p 回到 0 ——
 *   语义上是"沿拖拽那条通道原路退回"，不发生任何状态翻转，也就不存在重置。
 *   推进用与 [playerSettleMasterSpec] **同一条 ω 的临界阻尼解析解**
 *   `y(t) = y₀·(1 + ωt)·e^(−ωt)`（与音量条橡皮筋同一套做法），
 *   松手瞬间速度为 0 —— 用户口径就是"松手**就**上移"，不保留向下的惯性；
 *   99% 落定后 `snapTo(true)` 把状态机归位成"展开 + 静止"。
 *
 * ⚠ **只接管「拖拽中途」这一种状态**（`currentState == true` 且 `targetState == false`）：
 *   其余情况（真正从迷你栏打开时 `currentState == false`）必须仍走 `animateTo` ——
 *   那时重置 fraction 才是正确的（起点本来就是收起态）。判错会把正常打开变成瞬移。
 * ⚠ 逐帧 `seekTo` **不是新机制**：拖拽期的 `onSeekCollapse` 就是每帧 `seekTo`
 *   同一条通道，这里只是把驱动源从手指换成解析解。
 */
private suspend fun reboundPlayerToOpen(state: SeekableTransitionState<Boolean>) {
    // 非"拖拽中途"状态（正常打开 / 已经在展开态）⇒ 交回常规路径，行为逐字不变。
    if (state.currentState != true || state.targetState != false) {
        state.animateTo(targetState = true, animationSpec = playerSettleMasterSpec())
        return
    }
    val start = state.fraction.coerceIn(0f, 1f)
    if (start <= 1e-3f) {
        // 还没真的拖动过（或已回到 0）：状态机直接归位，零视觉变化。
        state.snapTo(true)
        return
    }
    val durationSec = (meloXPlayerTweenDurationMillis().coerceAtLeast(80)) / 1000f
    val omega = PLAYER_SETTLE_99_K / durationSec
    var firstNanos = 0L
    var frames = 0
    while (true) {
        val now = withFrameNanos { it }
        if (firstNanos == 0L) firstNanos = now
        val t = (now - firstNanos) / 1_000_000_000f
        // ζ=1、初速 0 的解析解；t=0 时 y=y₀ 且 y'=0（不朝收起方向多走一步）。
        val y = start * (1f + omega * t) * exp(-omega * t)
        // targetState 保持 false：只在"收起进度"轴上回退，不做任何状态翻转。
        state.seekTo(fraction = y.coerceIn(0f, 1f), targetState = false)
        // 99% 落定（残量 ≤1%）即收。
        // ⚠ 另外两道兜底都是**防死循环**用的（宁可跳一帧，也不能把主线程挂在动画里）：
        //   `t` 上限防帧时钟异常不前进；`frames` 上限防时钟被暂停而帧仍然到来。
        if (y <= start * 0.01f || y <= 1e-3f || t > durationSec * 3f) break
        if (++frames > 600) break
    }
    // 归位到"展开 + 静止"的稳态（此时 fraction 已≈0，视觉零变化）。
    state.snapTo(true)
}

/**
 * **打开方向**（点迷你栏 / 冷启 intent / 任何 `openPlayer()`）的 master。
 *
 * ⚠⚠⚠ 2026-10-06：与 [playerCloseFractionSpec] **一起**改成与「下拉手势」同一条
 *   [playerSettleMasterSpec]（spring ζ=1，stiffness 由**设置时长**反解）。
 *   用户口径：「迷你栏点击**和**通过手势退出的动画不对劲，很难看。**只有通过手来下滑的
 *   返回动画符合丝滑的理念**」+「下拉关闭的，那个是对的」。
 *   ⇒ 四条路径里只有「下拉手势」那条是 spring，其余（点击打开 / 点击关闭 /
 *     系统返回）都是 `tween(D, LinearEasing)` —— **差别只有这一条 master**。
 *   ⇒ 统一到同一条 spring：临界阻尼软起软收，取代原来
 *     50%@127ms、95%@338ms、**尾段空转 123ms** 的线性 tween。
 *
 * ⚠ 同一天**第二次**收口（用户报「设置调节**没反应**」）：原先写成 `stiffness = 420f`
 *   硬编码 ⇒ `spring` 不读设置 ⇒ 时长设置对动画完全无效。
 *   现改为 `stiffness = (k/D)²`（D 取设置时长），**「设置里选的毫秒数」= 真实落定时间**。
 *   ⚠ 在默认 360ms 档下 stiffness ≈ 297；420 恰好是 **302ms 档**的反解值
 *     ⇒ 想要回原来那种手感，把设置调到 300ms 即可。
 *
 * ⚠ 与 2026-10-04 那两次「master 换弹簧 ⇒ 效果更差」的区别（**别再混淆**）：
 *   那两次用的是 `stiffness = (6.2/D)²` 且 **D = 240ms** ⇒ **667**（很硬），
 *   把前段推到「100ms 走完 95%、尾段空转 139ms」⇒ 被否。
 *   **公式相同**，现在默认 360ms ⇒ ω ≈ 17.2、stiffness ≈ **297**，软得多。
 *
 * ⚠ 回退只需一行：把本函数与 [playerCloseFractionSpec] 都改回
 *   `playerAutomaticFractionSpec()`（保留未删，专为回退）。
 *
 * ⚠⚠ 2026-10-15 回退记录：曾按用户口径 "damping 0.75，有轻微回弹"把**打开方向**
 *   单独换成 underdamped 弹簧（ζ=0.75，过冲 ~2.8%；recede / 内容层 / MiniPlayer
 *   三处同换 + recedeMasterFraction 上界钳制放开），用户实测「**效果太差**」⇒
 *   整条回退到本函数（ζ=1）。当时的新增 spec / 常量 / import 已全部删除。
 *   教训：本项目播放器动画的「弹」已由关闭方向 + 迷你栏尾段 bounce 提供，
 *   打开方向再加过冲是**双重回弹**，与 AM 手感相反。
 */
// ★★★ 2026-10-26 全部回退（用户口径：「改底栏帧率之前完全正常、符合心意」）★★★
//   本函数回到 **弹簧 master**，与 `MeloXPlayerTransitionSpec` 三处 `MeloXPlayerAppleEasing`
//   + `MeloXPlayerSpringK = 6f` + `MeloXPlayerSinkWindowStart = 0.62f` **成对**，即
//   2026-10-06 起、今天开工之前的**确切原样**。
//
//   ══ 当天四次试错为什么全错（记下来，别再走）══
//   | 形态 | 前段(30%) | 感知到位 | 用户 |
//   |---|---|---|---|
//   | **弹簧 k=6.2 + 通道 AE（原样）** | 86% | **204ms** | ✅「完全正常、符合心意」 |
//   | 弹簧 + 通道 LinearEasing | 86% | 518ms | 下沉被摊平「直接没了」 |
//   | 线性 tween + AE(k=6) | 54.7% | 359ms | 「十分缓慢 + 下沉差版本」 |
//   | 弹簧 k=4.5 + AE | 69% | 281ms | 「还是慢了」 |
//   | 线性 tween + AE(k=8) | 69% | 359ms | 「过慢」 |
//
//   🔴 **真正的根因（用户 10:35 的追问逼出来的）**：
//   `meloXPlayerTweenDurationMillis()` = `360 / 1.35` = **266**，是**给 tween 用的**
//   （Compose 会再乘回 1.35 ⇒ 墙钟 359ms）。
//   但 **`spring` 完全不吃 `MotionDurationScale`** ⇒ `stiffness = (k / 0.266)²`
//   直接按 **266ms** 跑 ⇒ 弹簧形态的感知到位 ≈ 204ms，比设置值快 35%。
//   ⇒ **用户认可的观感就是这 204ms**。任何"把时长补回 360ms"的改动（线性 tween）
//     都会比它慢 76% ⇒ 必然被判「过慢」。
//   ⇒ 若真要让「设置 360 = 实际 360」，只能让**用户自己把设置调到 ~486**
//     （486/1.35=360 ⇒ 弹簧按 360 跑），**不能改代码**。
//
// ══ 🔴🔴 2026-10-27 实测推翻上面那段结论（用户「还是差点感觉，要更丝滑更 iOS」）══
//
// **「spring 不吃 `MotionDurationScale`」是错的。** 19:00 `mxdiag` 逐帧 F 行实测：
//   设置 240 ⇒ 实跑 **324ms**；设置 360 ⇒ **496/504ms** —— 都是 **设置值 × 1.35**。
//   spring 与 tween 一样吃缩放，只是链路不同（tween 靠 `durationMillis` 缩放，
//   spring 靠时间轴被拉长），**净效果完全相同**。
//   ⇒ 上面那张表里「弹簧 204ms / 线性 359ms」的对比前提不成立：弹簧是 **~520ms**，
//     不是 204ms。当时被判「过慢」的线性 tween 其实**比弹簧还快 31%**。
//
// **真正的病是双层。** master 是弹簧、子动画又套 `MeloXPlayerAppleEasing` ⇒
//   实际输出 `AppleEasing ∘ spring`。设置 360 的实测剖面：
//     · 半程（260ms）走完 **94.6%**，**后 20% 时间只走 0.2%** ⇒ 画面静止 **104ms** 等动画结束；
//     · 峰值速度 **3.95×** 均速 @15% 时间，而**起步速度 = 0**（要憋 78ms 才起来）。
//   这就是「不丝滑」的定义：中段猛甩 + 尾巴死掉。本文件 10-04 的注释早就记过
//   「双层 ⇒ 内容层超前 62~75ms，用户报效果更差」，10-26 又把它换回来了。
//
// ✅ 改回 `playerAutomaticFractionSpec()`（线性 tween）⇒ 全链恢复**单层**：
//     · 总墙钟 **359ms = 设置值**（520ms 那个 1.35 倍是 bug，不是手感）；
//     · 后 20% 时间仍走 3.6%（尾巴活着）；
//     · 起步速度由 `MeloXPlayerAppleInitialVelocityRatio`（α=0.40）单独提供。
//   ⚠ 只改**点击路径**（打开 / 关闭）；手势松手回弹仍用 `playerSettleMasterSpec()`
//     —— 用户明令「下拉关闭那个是对的，别动」。两条路径的 master 曲线本就不同源，
//     这不是不一致：拖拽那条是「沿手势通道原路退回」，语义不同。
// 🔴🔴 2026-10-29 00:5x：**打开 master 回到 fork/0.7.0 原样 `playerSettleMasterSpec()`**。
//   上面那段 10-2x 的论证（「双层是病 ⇒ 改线性 tween」）是**被真机否掉的本地方案**：
//   0.7.0 本身就是 `AppleEasing ∘ spring`，用户真机 A/B 确认 0.7.0 的打开手感是对的，
//   而换成线性 tween 后用户报「打开动画变得十分缓慢」。⇒ 以 fork 原版为准，
//   `playerAutomaticFractionSpec()`（线性 tween）保留未删，仅供日后取证。
private fun playerOpenFractionSpec(): FiniteAnimationSpec<Float> = playerSettleMasterSpec()

/**
 * **关闭方向**（点击关闭 / 系统返回 / 任何 `closePlayer()`）的 master。
 *
 * ★ 2026-10-28：**不再与打开互为镜像** —— 关闭时长 = 设置值 × [MeloXPlayerCloseDurationScale]
 *   （默认 360 ⇒ 540ms）。曲线形状（[MeloXPlayerAppleEasing] 那一层）**完全不变**：
 *   `k` 由固定 360ms 参照算定 ⇒ 形状与时长解耦，只是把同一条曲线铺开到更长的时间上。
 *
 * ### 为什么只拉长关闭
 * 治「下沉归位生硬」，而归位的柔度只能靠时间（`1 − smoothstep` 已是峰值斜率下界）。
 * 0.6.5 的下沉窗口其实是 328ms（它的 `0.62` 是弹簧 fraction ⇒ 只在墙钟 33.8%），
 * 单帧 8.9px；线性 master + 360ms 下只剩 137ms ⇒ 13.8px。540ms 补回到 270ms / 8.9px。
 * 详细换算见 [MeloXPlayerCloseDurationScale]。
 *
 * ### 为什么不是 spring
 * ⛔ 换回弹簧会让整条链重新变成 `AppleEasing ∘ spring` 双层（半程走 94.6%、后 20%
 *   只走 0.2%、尾巴静止 104ms），且墙钟被 `MotionDurationScale` 放大 1.35 倍。
 *   历史两次回退记录见 [playerOpenFractionSpec] 的说明。
 */
// 🔴🔴 2026-10-29 00:3x：回到 0.7.0 原样 = `playerSettleMasterSpec()`
//   （此前被我改成线性 tween + 关闭专属时长，属「本地方案」，已弃用）。
private fun playerCloseFractionSpec(): FiniteAnimationSpec<Float> = playerSettleMasterSpec()

private fun smoothStep(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun lerpDp(start: Dp, end: Dp, progress: Float): Dp =
    (start.value + (end.value - start.value) * progress.coerceIn(0f, 1f)).dp

/**
 * 与 [lerpDp] 相同，但**不夹** fraction —— 专供带过冲的几何。
 *
 * ⚠ 必须用这个版本，否则弹簧的过冲会在最后一步被 [lerpDp] 吃掉，Q 弹永远看不见。
 * fraction 由 [sprungFrac] 产出，已限幅，不会把布局撑爆；但**外扩方向**是否安全
 * 取决于该几何周围有没有邻居 —— 现在这件事交给**弹簧参数自己**，不再做事后压缩：
 * nav 展开方向的越界量由 `MeloXSprings.BottomBarExpandBounce` 定死在 6.02dp
 * （右预算 4dp + 左侧让出 2dp），再按 `NavExpandLeftShare` 落到两端；
 * 收缩方向由 0.30 那条管着（10.30dp，四周没有邻居）。
 */
private fun lerpDpBouncy(start: Dp, end: Dp, fraction: Float): Dp =
    (start.value + (end.value - start.value) * fraction).dp

/**
 * BiliNext `LiquidGlassTabsBar.kt` L99 的等价物：让 Layer 2（隐形捕获层）里的
 * tab 内容在按压时整体放大（`lerp(1f, 1.2f, pressProgress)`）。
 *
 * ⚠ 它**只影响捕获层的背板内容**（= 被水珠折射的那张图），不影响可见面板 ——
 * 所以「按压时图标放大」这件事在本项目只表现为折射内容的呼吸感，1:1 复刻 BiliNext。
 */
private val LocalGlassTabScale: ProvidableCompositionLocal<() -> Float> =
    staticCompositionLocalOf { { 1f } }

/**
 * 恒定的 status bar 高度 [WindowInsets]，**不受系统栏 hide/show 影响**。
 *
 * ⚠ 为什么不用 `WindowInsets.statusBars.ignoringVisibility()`：Compose 的
 * `ignoringVisibility()` 扩展在当前 BOM 版本里不可用（unresolved reference）。
 * 这里改用 Android ViewCompat 取 `getInsetsIgnoringVisibility` 的真实高度，
 * 与 [com.lladlam.melox.ui.player.meloXPlayerStatusBarsPadding] 同一取数路径。
 *
 * 用途：给 recede 背景页 Scaffold 当 `contentWindowInsets`。沉浸播放时状态栏
 * 被 `hide()`，可见 inset 归 0，普通 `statusBars` 会塌陷、返回时弹回真实高度
 * ⇒ 背景页顶部跳变（抽搐）。用本函数返回稳定高度让 padding 恒定；调用方再用
 * `consumeWindowInsets(WindowInsets.statusBars)` 让子级拿到的 statusBars 归零，
 * 避免它们的 `statusBarsPadding()` 在这份稳定 padding 之上重复叠加。
 */
@Composable
private fun stableStatusBarsInsets(): WindowInsets {
    val view = LocalView.current
    val density = LocalDensity.current
    var stableTopPx by remember { mutableIntStateOf(0) }

    // ⚠ 缓存**只在拿到 >0 的值时写入**，隐藏期间保留上一次真实高度（真正的稳定）。
    //   部分 ROM 在 `hide(statusBars)` 后连 `getInsetsIgnoringVisibility` 也返 0 ⇒ 照单写入
    //   会让 padding 随显隐 0↔真实高度跳，退出沉浸时页面下移。
    //   ⚠⚠**不要用 `insets.isVisible(statusBars)` 当守卫**：vivo 上它可能恒为 false（或首帧
    //   insets 尚未派发），加了就导致缓存**一次都没被写入**、恒为 0 ⇒ 内容顶到状态栏底下
    //   （真机症状：歌单页按钮顶到最上面）。只看 `v > 0` 即可：隐藏时自然为 0、保持旧值。
    DisposableEffect(view) {
        ViewCompat.getRootWindowInsets(view)?.let { insets ->
            val v = maxOf(
                insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top,
                insets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
            )
            if (v > 0) stableTopPx = v
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val v = maxOf(
                insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top,
                insets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
            )
            if (v > 0) stableTopPx = v
            insets
        }
        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
        }
    }

    return remember(stableTopPx, density) {
        WindowInsets(0.dp, with(density) { stableTopPx.toDp() }, 0.dp, 0.dp)
    }
}

/**
 * 底部 chrome 的**稳定**底部内边距：走 ViewCompat 直接读 `navigationBars` 的
 * **忽略可见性**真实高度，而不是用 `Modifier.navigationBarsPadding()`。
 *
 * 为什么（2026-09-29 真机回归）：edge-to-edge（`setDecorFitsSystemWindows(false)`）之后
 * content frame 铺满整屏，底栏胶囊的抬高就**全靠** Compose 的 `navigationBars` inset。
 * 真机 iQOO 15 竖屏实测底栏内容底沿 = 屏底 − 8dp，正好少了 72px = 导航栏高度
 * ⇒ 这条传播链读到 0，胶囊直接压到系统手势条（小白条）上。
 *
 * 改用与 [stableStatusBarsInsets] 同源的取数路径（`getRootWindowInsets` +
 * `getInsetsIgnoringVisibility`）：既不依赖 insets 传播链，也不会随系统栏 hide/show 跳变。
 *
 * ⚠ **不注册** `setOnApplyWindowInsetsListener`（它是**覆盖式**的，会和
 *   [stableStatusBarsInsets] / [meloXPlayerStatusBarsPadding] 互踩），改为轮询到拿到为止；
 *   转屏后按 screenWidthDp/screenHeightDp 重新读取。
 */
@Composable
private fun Modifier.stableNavigationBarsPadding(): Modifier = composed {
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var navBottomPx by remember(view, configuration.screenWidthDp, configuration.screenHeightDp) {
        mutableIntStateOf(
            ViewCompat.getRootWindowInsets(view)
                ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
                ?.bottom ?: 0,
        )
    }
    LaunchedEffect(view, configuration.screenWidthDp, configuration.screenHeightDp) {
        repeat(20) {
            if (navBottomPx > 0) return@LaunchedEffect
            delay(50L)
            ViewCompat.getRootWindowInsets(view)
                ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
                ?.let { insets -> navBottomPx = insets.bottom }
        }
    }
    Modifier.padding(bottom = with(density) { navBottomPx.toDp() })
}
