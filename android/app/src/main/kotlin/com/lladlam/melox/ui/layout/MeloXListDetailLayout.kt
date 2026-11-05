package com.lladlam.melox.ui.layout

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lladlam.melox.R
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
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.song.MeloXSongWikiActivity
import com.lladlam.melox.ui.song.MeloXSongWikiPage
import com.lladlam.melox.ui.cloud.MeloXCloudMusicScreen
import com.lladlam.melox.ui.library.LibraryScreen
import com.lladlam.melox.ui.podcast.MeloXPodcastScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Window aspect ratio, deliberately independent of device type and width classes. */
internal fun meloXUsesListDetail(size: IntSize, enabled: Boolean = true): Boolean =
    enabled && size.height > 0 && size.width > size.height

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
        )
    }
}

internal fun Context.openMeloXDetailPage(intent: Intent): Boolean {
    // Settings and its account/service pages share platform Activity transitions
    // in a single pane. Inline pages would also hide the shell's dock/miniplayer
    // until their exit animation finishes. Only split landscape uses the pane.
    val config = resources.configuration
    val settingsPage = when (intent.component?.className) {
        MeloXSettingsDetailActivity::class.java.name,
        MeloXAccountActivity::class.java.name,
        MeloXNeteaseLoginActivity::class.java.name,
        MeloXProviderServicesActivity::class.java.name -> true
        else -> false
    }
    if (settingsPage &&
        (!MeloXSettingsRuntime.splitScreenEnabled || config.screenWidthDp <= config.screenHeightDp)) return false
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
    val windowSize = LocalWindowInfo.current.containerSize
    val split = meloXUsesListDetail(windowSize, MeloXSettingsRuntime.splitScreenEnabled)
    val context = LocalContext.current
    val divider = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(Modifier.fillMaxSize()) {
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
                            Image(
                                painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier.size(104.dp),
                            )
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
        MeloXNeteaseLoginActivity::class.java.name -> NeteaseLoginScreen(
            session = session,
            onDismiss = onBack,
            onLoggedIn = onLoggedIn,
            targetSlot = intent.getStringExtra("target_slot")
                ?.let { runCatching { PlaybackAccountSlot.valueOf(it) }.getOrNull() } ?: PlaybackAccountSlot.Main,
        )
    }
}
