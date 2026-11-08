package com.lladlam.melox.ui.layout

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.music.provider.PlaybackAccountSlot
import com.lladlam.melox.ui.MeloXInterruptiblePage
import com.lladlam.melox.ui.account.AccountHomeScreen
import com.lladlam.melox.ui.account.MeloXAccountActivity
import com.lladlam.melox.ui.account.MeloXNeteaseLoginActivity
import com.lladlam.melox.ui.account.NeteaseLoginScreen
import com.lladlam.melox.ui.collection.MeloXCollectionDetailActivity
import com.lladlam.melox.ui.collection.MeloXCollectionDetailPage
import com.lladlam.melox.ui.provider.MeloXProviderServicesActivity
import com.lladlam.melox.ui.provider.ProviderServicesPage
import com.lladlam.melox.ui.settings.MeloXSettingsDetailActivity
import com.lladlam.melox.ui.settings.MeloXSettingsDetailPage
import com.lladlam.melox.ui.theme.MeloXBrandLogo
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.song.MeloXSongWikiActivity
import com.lladlam.melox.ui.song.MeloXSongWikiPage
import com.lladlam.melox.ui.cloud.MeloXCloudMusicScreen
import com.lladlam.melox.ui.library.LibraryScreen
import com.lladlam.melox.ui.messages.MessagesScreen
import com.lladlam.melox.ui.messages.MeloXMessagesActivity
import com.lladlam.melox.ui.podcast.MeloXPodcastScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Window aspect ratio, deliberately independent of device type and width classes. */
internal fun meloXUsesListDetail(size: IntSize, enabled: Boolean = true): Boolean =
    enabled && size.height > 0 && size.width > size.height

/**
 * 配置口径的同一个判据（2026-10-27 新增）。
 *
 * ## 为什么不能再用 `LocalWindowInfo.current.containerSize`
 * 旋转时本 App 声明了 `configChanges="orientation|screenSize"` ⇒ Activity 不重建，
 * 转屏是**同一棵树内的尺寸变化**。此时两条时间线并不同源：
 *  - 新尺寸（布局约束）= 本次 traversal 的 measure 输入，**当帧生效**；
 *  - `containerSize` 来自 WindowManager metrics 的异步回填，可能**晚 1 帧甚至 2 帧**。
 *
 * 于是竖→横的第一帧会出现「页面已按横屏分栏（`MeloXListDetailLayout` 的
 * `BoxWithConstraints` 拿到的是真约束）」但「`MeloXApp.listDetailMode` 仍是 false
 * ⇒ 底栏按整屏宽铺开」⇒ 下一帧才收成半宽 —— 就是用户报的**「底栏长变短」**。
 *
 * `LocalConfiguration` 由 `AndroidComposeView.dispatchConfigurationChanged` 更新，
 * 与窗口 resize 在**同一次 `performTraversals`** 内、且早于 measure ⇒ 与布局约束
 * 同帧同源。底栏与页面共用这一个判据后，两者不可能再跨帧。
 *
 * @see meloXUsesListDetail 的 IntSize 版本（布局真值口径，二者同义）
 */
internal fun meloXUsesListDetail(configuration: Configuration, enabled: Boolean = true): Boolean =
    enabled && configuration.screenWidthDp > configuration.screenHeightDp

/**
 * **布局真值**口径（2026-10-27 定案，优先级高于上面两个口径）。
 *
 * ## 为什么最终必须用 `Constraints`
 * 上面两条（[IntSize] / [Configuration]）都还是「描述性输入」：前者是 WindowManager
 * 异步回填的 metrics，后者是 `Resources` 的配置派发 —— 两者都**可能比本次 measure 的
 * 约束晚 1~N 帧**（转屏时 WM 先 resize 窗口、配置派发走另一条主线程消息，谁先到不保证）。
 * 只要判据比约束晚，就会有一帧「窗口已经横了、判据还说是竖屏」⇒ 底栏按整屏宽铺开
 * ⇒ 下一帧才收成半宽 —— 用户报的**「竖转横底栏长变短」**（改 `Configuration` 口径后
 * 仍偶发，说明配置派发同样会滞后）。
 *
 * 而 `Constraints` 是**本次 measure 的输入本身**：`BoxWithConstraints` 在 measure 阶段
 * 做 subcompose ⇒ 内容在同一帧就拿到新约束 ⇒ 与它旁边的兄弟（`MeloXPositionedChrome`
 * 的 measure lambda 里的 `constraints`）**天然同源同帧**，不存在跨帧的可能。
 *
 * ⚠ 判据只能有一个出处，且**必须是离布局最近的那一个**：`MeloXListDetailLayout` 用
 *   `BoxWithConstraints` 的 `constraints`，底栏用 `MeloXPositionedChrome` 的 `constraints`。
 *   两者都是满屏节点 ⇒ 同一个值。
 */
internal fun meloXUsesListDetail(constraints: Constraints, enabled: Boolean = true): Boolean =
    enabled && constraints.hasBoundedWidth && constraints.hasBoundedHeight &&
        constraints.maxWidth > constraints.maxHeight

internal val LocalMeloXPaneSize = staticCompositionLocalOf<IntSize?> { null }
internal val LocalMeloXListDetail = staticCompositionLocalOf { false }
internal val LocalMeloXInDetailPane = staticCompositionLocalOf { false }
private val LocalDetailPaneState = staticCompositionLocalOf<MeloXDetailPaneState?> { null }

/** Implemented by the main window; other activities retain their native navigation. */
internal interface MeloXDetailPageOwner {
    val detailPaneState: MeloXDetailPaneState
    val detailParent: Any? get() = null
}

class MeloXDetailPaneState {
    internal class Slot(
        val key: String,
        val visible: State<Boolean>,
        val onDismiss: State<(() -> Unit)?>,
        val content: @Composable () -> Unit,
    )
    internal class Page(val intent: Intent, val onSuccess: (() -> Unit)? = null) {
        val key: String = intent.getStringExtra("melox_page_key") ?: java.util.UUID.randomUUID().toString().also {
            intent.putExtra("melox_page_key", it)
        }
    }
    internal val slots = mutableStateListOf<Slot>()
    internal val pages = mutableStateListOf<Page>()

    internal fun open(intent: Intent, parent: Any?, onSuccess: (() -> Unit)? = null): Boolean {
        val supportedPage = intent.component?.className?.let { it in pageClasses } == true
        if (!supportedPage && intent.action != ToolPageAction) return false
        // A choice on the left starts a new detail path. A choice inside a detail
        // pushes onto that path, so Back returns through the right-hand history.
        val parentIndex = pages.indexOfFirst { it === parent }
        if (parent == null) slots.filter { it.visible.value }.forEach { it.onDismiss.value?.invoke() }
        if (parentIndex < 0) pages.clear()
        else while (pages.lastIndex > parentIndex) pages.removeAt(pages.lastIndex)
        pages.add(Page(Intent(intent), onSuccess))
        return true
    }

    internal fun pop(page: Page) {
        val index = pages.indexOf(page)
        if (index >= 0) while (pages.size > index) pages.removeAt(pages.lastIndex)
    }

    fun clearPages() = pages.clear()

    internal fun savePages(): ArrayList<Intent> = ArrayList(pages.map { Intent(it.intent) })

    internal fun restorePages(intents: List<Intent>) {
        pages.clear()
        intents.filter { it.component?.className in pageClasses || it.action == ToolPageAction }
            .forEach { pages.add(Page(Intent(it))) }
    }

    internal fun openTool(route: String) {
        open(Intent(ToolPageAction).putExtra("route", route), parent = null)
    }

    internal fun openLogin(context: Context, onLoggedIn: () -> Unit) {
        open(MeloXNeteaseLoginActivity.createIntent(context), parent = null, onSuccess = onLoggedIn)
    }

    private companion object {
        const val ToolPageAction = "com.lladlam.melox.DETAIL_TOOL"
        val pageClasses = setOf(
            MeloXSettingsDetailActivity::class.java.name,
            MeloXCollectionDetailActivity::class.java.name,
            MeloXAccountActivity::class.java.name,
            MeloXNeteaseLoginActivity::class.java.name,
            MeloXProviderServicesActivity::class.java.name,
            MeloXSongWikiActivity::class.java.name,
            // 2026-10-18：私信页迁 Activity；横屏分屏仍走内嵌右栏（与设置详情同规则），
            // 竖屏由 openMeloXDetailPage 的 singlePane 排除走平台转场。
            MeloXMessagesActivity::class.java.name,
        )
    }
}

internal fun Context.openMeloXDetailPage(intent: Intent): Boolean {
// Settings and its account/service pages share platform Activity transitions
    // in a single pane. Inline pages would also hide the shell's dock/miniplayer
    // until their exit animation finishes. Only split landscape uses the pane.
    //
    // ⚠⚠ 2026-10-18：账号页（MeloXAccountActivity）、私信页（MeloXMessagesActivity）与
    // 音乐服务页（MeloXProviderServicesActivity）同样只在「分屏 + 横屏」走内嵌面板；
    // 单面板（竖屏手机）一律 return false 走平台 Activity 转场。此前这些页面没有这条排除
    // ⇒ 竖屏下打开它们走的是 App 内 `AdaptiveActivityPage`（`visible = true` 是常量 ⇒
    // AnimatedContent 初始即目标态，**入场动画根本不播**，打开即现身）—— 与其他设置
    // 详情页（平台窗口转场）不同款，用户报「动画不完整 / 没有动画」。
    //
    // ★ 2026-10-25 同步上游 0.7.0：上游把这段改成 `when` 判据并新增网易云登录页
    //   （MeloXNeteaseLoginActivity）；本地把「私信页」那一项保留在同一个 `when` 里。
    val config = resources.configuration
    val singlePane = !MeloXSettingsRuntime.splitScreenEnabled || config.screenWidthDp <= config.screenHeightDp
    val settingsPage = when (intent.component?.className) {
        MeloXSettingsDetailActivity::class.java.name,
        MeloXAccountActivity::class.java.name,
        MeloXNeteaseLoginActivity::class.java.name,
        MeloXMessagesActivity::class.java.name,
        MeloXProviderServicesActivity::class.java.name -> true
        else -> false
    }
    if (singlePane && settingsPage) return false
    var current = this
    while (true) {
        if (current is MeloXDetailPageOwner) {
            return current.detailPaneState.open(intent, current.detailParent)
        }
        current = (current as? ContextWrapper)?.baseContext ?: return false
    }
}

private class DetailContext(
    base: Context,
    override val detailPaneState: MeloXDetailPaneState,
    override val detailParent: Any,
) : ContextWrapper(base), MeloXDetailPageOwner

/**
 * An inline child page can move to the secondary pane without discarding its
 * remembered state. Nested child pages already inside that pane stay there.
 */
@Composable
internal fun MeloXAdaptiveDetail(
    visible: Boolean,
    onDismiss: (() -> Unit)? = null,
    content: @Composable (Boolean) -> Unit,
) {
    val state = LocalDetailPaneState.current
    if (state == null || LocalMeloXInDetailPane.current) {
        content(visible)
        return
    }
    val currentContent by rememberUpdatedState(content)
    val currentVisible = rememberUpdatedState(visible)
    val currentDismiss = rememberUpdatedState(onDismiss)
    val movable = remember {
        movableContentOf<Boolean> { shown ->
            CompositionLocalProvider(LocalMeloXInDetailPane provides true) { currentContent(shown) }
        }
    }
    val slotKey = rememberSaveable { java.util.UUID.randomUUID().toString() }
    val slot = remember { MeloXDetailPaneState.Slot(slotKey, currentVisible, currentDismiss) { movable(currentVisible.value) } }
    DisposableEffect(state, slot) {
        state.slots.add(slot)
        onDispose { state.slots.remove(slot) }
    }
    var previouslyVisible by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible && !previouslyVisible) state.clearPages()
        previouslyVisible = visible
    }
    if (!LocalMeloXListDetail.current) movable(visible)
}

/** Starting another child from the left replaces an existing right-hand path. */
@Composable
internal fun rememberMeloXDetailSelection(): () -> Unit {
    val state = LocalDetailPaneState.current
    val inDetail = LocalMeloXInDetailPane.current
    return remember(state, inDetail) { { if (!inDetail) state?.clearPages(); Unit } }
}

/** Both pane nodes stay at the same composition positions when the window resizes. */
@Composable
internal fun MeloXListDetailLayout(
    state: MeloXDetailPaneState,
    session: NeteaseSessionStore,
    onSourceSelected: (MusicSource) -> Unit = {},
    hasMiniPlayer: Boolean = false,
    primaryModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val divider = MaterialTheme.colorScheme.outlineVariant
    // ★ 2026-10-27（第二次）：判据改用 **BoxWithConstraints 的布局真值**
    //   （`meloXUsesListDetail(Constraints)`，见其 kdoc）。
    //   上一轮换成 `LocalConfiguration` 后仍偶发「底栏长变短」⇒ 证明配置派发同样会
    //   晚于窗口 resize；只有 measure 的 `Constraints` 与布局**天然同帧**。
    //   ⚠ 必须写在 `BoxWithConstraints` **内部**：`constraints` 只在那个 scope 里存在，
    //     而且 subcompose 是在 measure 阶段跑的 ⇒ 同一帧拿到新约束。
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val split = meloXUsesListDetail(constraints, MeloXSettingsRuntime.splitScreenEnabled)
        val density = LocalDensity.current
        val size = with(density) { IntSize(maxWidth.roundToPx(), maxHeight.roundToPx()) }
        val leftWidth = if (split) size.width / 2 else size.width
        val rightSize = IntSize(if (split) size.width - leftWidth else size.width, size.height)
        fun topClearance(right: Boolean) = if (!split) 0.dp else {
            val nav = MeloXSettingsRuntime.splitNavigationPosition
            val mini = MeloXSettingsRuntime.splitMiniPlayerPosition
            (if (nav.right == right && nav.vertical == -1) 80.dp else 0.dp) +
                (if (hasMiniPlayer && mini.right == right && mini.vertical == -1) 80.dp else 0.dp)
        }
        val leftTop = topClearance(false)
        val rightTop = topClearance(true)
        fun sideClearance(right: Boolean) = if (!split) 0.dp else {
            val nav = MeloXSettingsRuntime.splitNavigationPosition
            val mini = MeloXSettingsRuntime.splitMiniPlayerPosition
            (if (nav.right == right && nav.isSideRail) 80.dp else 0.dp) +
                (if (hasMiniPlayer && mini.right == right && mini.isSideRail) 80.dp else 0.dp)
        }
        val leftSide = sideClearance(false)
        val rightSide = sideClearance(true)
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Box(
                    Modifier.fillMaxSize().clipToBounds().padding(top = leftTop).absolutePadding(left = leftSide).then(primaryModifier).then(
                        if (!split && state.pages.isNotEmpty()) Modifier.clearAndSetSemantics { } else Modifier,
                    ),
                ) {
                    CompositionLocalProvider(
                        LocalDetailPaneState provides state,
                        LocalMeloXListDetail provides split,
                        LocalMeloXPaneSize provides IntSize((leftWidth - with(density) { leftSide.roundToPx() }).coerceAtLeast(0), (size.height - with(density) { leftTop.roundToPx() }).coerceAtLeast(0)),
                    ) { content() }
                }
                Box(Modifier.fillMaxSize().clipToBounds().padding(top = rightTop).absolutePadding(right = rightSide)) {
                    if (split) {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center,
                        ) {
                            MeloXBrandLogo(104.dp)
                        }
                    }
                    CompositionLocalProvider(
                        LocalDetailPaneState provides state,
                        LocalMeloXListDetail provides split,
                        LocalMeloXInDetailPane provides true,
                        LocalMeloXPaneSize provides IntSize((rightSize.width - with(density) { rightSide.roundToPx() }).coerceAtLeast(0), (rightSize.height - with(density) { rightTop.roundToPx() }).coerceAtLeast(0)),
                    ) {
                        if (split) state.slots.forEach { slot ->
                            key(slot.key) {
                                val detailContext = remember(context, state, slot) { DetailContext(context, state, slot) }
                                CompositionLocalProvider(LocalContext provides detailContext) { slot.content() }
                            }
                        }
                        state.pages.forEach { page ->
                            key(page.key) {
                                val detailContext = remember(context, state, page) { DetailContext(context, state, page) }
                                CompositionLocalProvider(LocalContext provides detailContext) {
                                    AdaptiveActivityPage(
                                        isTopPage = state.pages.lastOrNull() === page,
                                        onBack = { state.pop(page) },
                                    ) { close ->
                                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                            DetailPage(
                                                page.intent,
                                                session = session,
                                                onBack = close,
                                                onSourceSelected = onSourceSelected,
                                                onLoggedIn = { state.pop(page); page.onSuccess?.invoke() },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Box(Modifier.background(divider))
            },
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val left = if (split) width / 2 else width
            val primary = measurables[0].measure(Constraints.fixed(left, height))
            val secondary = measurables[1].measure(Constraints.fixed(if (split) width - left else width, height))
            val line = measurables[2].measure(Constraints.fixed(if (split) 1 else 0, height))
            layout(width, height) {
                primary.place(0, 0)
                secondary.place(if (split) left else 0, 0)
                line.place(left, 0)
            }
        }
    }
}

/** Pane navigation owns its gesture; nested dialogs can register their own back handler. */
@Composable
private fun AdaptiveActivityPage(
    isTopPage: Boolean,
    onBack: () -> Unit,
    content: @Composable (() -> Unit) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(1f, tween(160))
                onBack()
            }
        }
    }
    MeloXInterruptiblePage(visible = true, retreatProgress = { progress.value }) {
        PredictiveBackHandler(enabled = isTopPage && !closing) { events ->
            try {
                events.collect { progress.snapTo(it.progress) }
                closing = true
                progress.animateTo(1f, tween(160))
                onBack()
            } catch (error: CancellationException) {
                closing = false
                progress.animateTo(0f, tween(180))
            }
        }
        BackHandler(enabled = isTopPage && closing) { }
        content(close)
    }
}

@Composable
private fun DetailPage(
    intent: Intent,
    session: NeteaseSessionStore,
    onBack: () -> Unit,
    onSourceSelected: (MusicSource) -> Unit,
    onLoggedIn: () -> Unit,
) {
    when (intent.getStringExtra("route")) {
        "Podcasts" -> MeloXPodcastScreen(applyStatusBarPadding = true, onExit = onBack)
        "Downloads" -> LibraryScreen(session = session, onLogin = null, forcedPageName = "Downloads")
        "Cloud" -> MeloXCloudMusicScreen()
    }
    when (intent.component?.className) {
        MeloXSettingsDetailActivity::class.java.name -> MeloXSettingsDetailPage(intent, onBack, session)
        MeloXCollectionDetailActivity::class.java.name -> MeloXCollectionDetailPage(intent, onBack)
        MeloXAccountActivity::class.java.name -> AccountHomeScreen(intent.getLongExtra("user_id", -1L), onBack)
        MeloXProviderServicesActivity::class.java.name -> ProviderServicesPage(onBack, onSourceSelected, session)
        MeloXSongWikiActivity::class.java.name -> MeloXSongWikiPage(intent, onBack)
        MeloXMessagesActivity::class.java.name -> MessagesScreen(onBack = onBack)
        MeloXNeteaseLoginActivity::class.java.name -> NeteaseLoginScreen(
            session = session,
            onDismiss = onBack,
            onLoggedIn = onLoggedIn,
            targetSlot = intent.getStringExtra("target_slot")
                ?.let { runCatching { PlaybackAccountSlot.valueOf(it) }.getOrNull() } ?: PlaybackAccountSlot.Main,
        )
    }
}
