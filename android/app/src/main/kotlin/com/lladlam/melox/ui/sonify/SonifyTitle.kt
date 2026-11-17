/*
 * Sonify large-title page scaffold (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/widgets/basic/Title.kt (master @ 209e1aa).
 * The structure, collapse animation, Mochi index bar hookup, overscroll and
 * title bar are preserved. Two upstream integrations are intentionally
 * MeloX-native:
 *   - titlebar buttons use MeloX's Material icon set instead of Sonify drawables;
 *   - the liquid-glass button swaps in a stable translucent circle so it can
 *     live inside the recorded page layer without self-sampling recursion.
 */
package com.lladlam.melox.ui.sonify

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInCirc
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import com.lladlam.melox.ui.sonify.artist.LiquidTopBarButton
import com.lladlam.melox.ui.sonify.artist.titleBarProgressiveBlur
import kotlinx.coroutines.launch

/**
 * Page background behind the transparent list. The titlebar blur samples this
 * colour as its seed, so it must match the page's actual background.
 */
val LocalTitlePageColor = compositionLocalOf<Color?> { null }

/** Optional extracted-colour tint for the glass titlebar buttons. */
val LocalTitleBarAccentTint = compositionLocalOf<Color?> { null }

/** Page recording layer exposed to glass consumers. */
val LocalTitlePageBackdrop = compositionLocalOf<LayerBackdrop?> { null }

@Stable
class TitleOverlaySlot {
    internal val content = mutableStateOf<(@Composable () -> Unit)?>(null)
}

@Stable
class TitleOverlayHost {
    internal val slots = mutableStateMapOf<Any, TitleOverlaySlot>()

    fun set(key: Any, content: @Composable () -> Unit) {
        val slot = slots[key] ?: TitleOverlaySlot().also { slots[key] = it }
        slot.content.value = content
    }

    fun remove(key: Any) {
        slots.remove(key)
    }
}

val LocalTitleOverlayHost = compositionLocalOf<TitleOverlayHost?> { null }

@Composable
internal fun TitleOverlayHostSlot(host: TitleOverlayHost) {
    Box(Modifier.fillMaxSize()) {
        host.slots.forEach { (slotKey, slot) ->
            key(slotKey) { slot.content.value?.invoke() }
        }
    }
}

@Composable
private fun titlePageColor(): Color {
    val fallback = LocalTitlePageColor.current ?: (Color.White withNight Color.Black)
    return LocalPageGradient.current?.averageColor(fallback) ?: fallback
}

/**
 * Recording layer used by the titlebar. [pageColorOverride] lets detail pages
 * create it before providing LocalTitlePageColor.
 */
@Composable
fun rememberTitleBackdrop(
    pageColorOverride: Color? = null,
    blit: Boolean = false,
    gradient: PageGradient? = null,
): LayerBackdrop {
    val pageColor = pageColorOverride
        ?: LocalTitlePageColor.current
        ?: (Color.White withNight Color.Black)
    // The 2.0.0 backdrop artifact draws the recorded content itself; the page
    // background is painted inside the layer by the caller. Named onDraw is
    // avoided here so this source also builds against the Maven backdrop ABI.
    return rememberLayerBackdrop()
}

@Composable
fun Title(
    title: String,
    subTitle: String? = null,
    onBack: (() -> Unit)? = null,
    rightIcon: ImageVector? = null,
    onRightIcon: (() -> Unit)? = null,
    rightBarIcon: @Composable (RowScope.() -> Unit)? = null,
    bottomPadding: Dp = 134.dp,
    extraTopPadding: Dp = 0.dp,
    topRightIcon: ImageVector? = null,
    onTopRightIcon: (() -> Unit)? = null,
    titleHorizontalPadding: Dp = 20.dp,
    revealIndex: Int? = null,
    liquidBackButton: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    indexBarLabels: List<String> = emptyList(),
    onIndexSelect: ((Int) -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val collapse = rememberTitleCollapse(listState, extraTopPadding)
    val showIndexBar = indexBarLabels.isNotEmpty() && onIndexSelect != null
    val effectiveRightBarIcon: @Composable (RowScope.() -> Unit)? =
        rightBarIcon ?: if (topRightIcon != null && onTopRightIcon != null) {
            { TitleBarIcon(icon = topRightIcon, onBack = onTopRightIcon) }
        } else null

    val pageColor = titlePageColor()
    val titleBackdrop = rememberTitleBackdrop()
    val overlayHost = remember { TitleOverlayHost() }

    CompositionLocalProvider(
        LocalTitlePageBackdrop provides titleBackdrop,
        LocalTitleOverlayHost provides overlayHost,
    ) {
        Box(Modifier.fillMaxSize().background(pageColor)) {
            val listContent: @Composable () -> Unit = {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(titleBackdrop)
                        .background(pageColor)
                        .titleCollapseTouchTracker(collapse)
                        .overScrollVertical(),
                    flingBehavior = rememberOverscrollFlingBehavior { listState },
                    contentPadding = PaddingValues(
                        top = 54.dp + extraTopPadding,
                        end = if (showIndexBar) 18.dp else 0.dp,
                    ),
                ) {
                    item("title") {
                        Column {
                            Spacer(modifier = Modifier.sonifyStatusBarsHeight())
                            TitleItem(
                                title = title,
                                subTitle = subTitle,
                                rightIcon = rightIcon,
                                onRightIcon = onRightIcon,
                                alpha = collapse.largeTitleAlpha,
                                titleHorizontalPadding = titleHorizontalPadding,
                            )
                        }
                    }
                    content()
                    item("navbar") {
                        Spacer(modifier = Modifier.sonifyNavigationBarsHeight(bottomPadding))
                    }
                }
            }

            if (onRefresh != null) {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) { listContent() }
            } else {
                listContent()
            }

            TitleBar(
                title = title,
                onBack = onBack,
                showSmallTitle = collapse.showSmallTitle,
                backdrop = titleBackdrop,
                barBackgroundVisible = collapse.showBarBackground,
                rightBarIcon = effectiveRightBarIcon,
                liquidBackButton = liquidBackButton,
            )

            if (showIndexBar) {
                MochiIndexBar(
                    labels = indexBarLabels,
                    onSelect = onIndexSelect!!,
                    onDragActiveChange = { active -> collapse.touchActive.value = active },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .sonifyStatusBarsPadding()
                        .sonifyNavigationBarsPadding()
                        .padding(top = 54.dp, bottom = 110.dp, end = 20.dp),
                )
            }

            TitleOverlayHostSlot(overlayHost)
        }
    }
}

@Composable
fun TitleWithLazyVerticalGrid(
    title: String,
    subTitle: String? = null,
    onBack: (() -> Unit)? = null,
    rightIcon: ImageVector? = null,
    onRightIcon: (() -> Unit)? = null,
    rightBarIcon: @Composable (RowScope.() -> Unit)? = null,
    minItemSize: Dp = 100.dp,
    extraTopPadding: Dp = 0.dp,
    topRightIcon: ImageVector? = null,
    onTopRightIcon: (() -> Unit)? = null,
    gridState: LazyGridState = rememberLazyGridState(),
    liquidBackButton: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    indexBarLabels: List<String> = emptyList(),
    onIndexSelect: ((Int) -> Unit)? = null,
    content: LazyGridScope.() -> Unit,
) {
    val collapse = rememberTitleCollapse(gridState, extraTopPadding)
    val showIndexBar = indexBarLabels.isNotEmpty() && onIndexSelect != null
    val effectiveRightBarIcon: @Composable (RowScope.() -> Unit)? =
        rightBarIcon ?: if (topRightIcon != null && onTopRightIcon != null) {
            { TitleBarIcon(icon = topRightIcon, onBack = onTopRightIcon) }
        } else null
    val pageColor = titlePageColor()
    val titleBackdrop = rememberTitleBackdrop()
    val overlayHost = remember { TitleOverlayHost() }

    CompositionLocalProvider(
        LocalTitlePageBackdrop provides titleBackdrop,
        LocalTitleOverlayHost provides overlayHost,
    ) {
        Box(Modifier.fillMaxSize().background(pageColor)) {
            val gridContent: @Composable () -> Unit = {
                LazyVerticalGrid(
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(titleBackdrop)
                        .background(pageColor)
                        .titleCollapseTouchTracker(collapse)
                        .overScrollVertical(),
                    flingBehavior = rememberOverscrollFlingBehavior { gridState },
                    columns = GridCells.Adaptive(minSize = minItemSize),
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = if (showIndexBar) 24.dp else 18.dp,
                        top = 54.dp + extraTopPadding,
                    ),
                ) {
                    item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Spacer(modifier = Modifier.sonifyStatusBarsHeight())
                            TitleItem(
                                title = title,
                                subTitle = subTitle,
                                rightIcon = rightIcon,
                                onRightIcon = onRightIcon,
                                alpha = collapse.largeTitleAlpha,
                                grid = true,
                            )
                        }
                    }
                    content()
                    item("navbar", span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.sonifyNavigationBarsHeight(134.dp))
                    }
                }
            }
            if (onRefresh != null) {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) { gridContent() }
            } else {
                gridContent()
            }
            TitleBar(
                title = title,
                onBack = onBack,
                showSmallTitle = collapse.showSmallTitle,
                backdrop = titleBackdrop,
                barBackgroundVisible = collapse.showBarBackground,
                rightBarIcon = effectiveRightBarIcon,
                liquidBackButton = liquidBackButton,
            )
            if (showIndexBar) {
                MochiIndexBar(
                    labels = indexBarLabels,
                    onSelect = onIndexSelect!!,
                    onDragActiveChange = { active -> collapse.touchActive.value = active },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .sonifyStatusBarsPadding()
                        .sonifyNavigationBarsPadding()
                        .padding(top = 54.dp, bottom = 110.dp, end = 20.dp),
                )
            }
            TitleOverlayHostSlot(overlayHost)
        }
    }
}

class TitleCollapseState(
    val largeTitleAlpha: State<Float>,
    val showBarBackground: State<Boolean>,
    val showSmallTitle: State<Boolean>,
    internal val touchActive: MutableState<Boolean> = mutableStateOf(false),
)

fun Modifier.titleCollapseTouchTracker(state: TitleCollapseState): Modifier =
    pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            state.touchActive.value = true
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.all { !it.pressed }) break
            }
            state.touchActive.value = false
        }
    }

@Composable
fun rememberTitleCollapse(state: LazyListState, extraTopPadding: Dp = 0.dp): TitleCollapseState =
    rememberTitleCollapseImpl(
        scrollable = state,
        firstItemInfo = {
            state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
                ?.let { Pair(it.offset, it.size) }
        },
        isLayoutReady = { state.layoutInfo.totalItemsCount > 0 },
        extraTopPadding = extraTopPadding,
    )

@Composable
fun rememberTitleCollapse(state: LazyGridState, extraTopPadding: Dp = 0.dp): TitleCollapseState =
    rememberTitleCollapseImpl(
        scrollable = state,
        firstItemInfo = {
            state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
                ?.let { Pair(it.offset.y, it.size.height) }
        },
        isLayoutReady = { state.layoutInfo.totalItemsCount > 0 },
        extraTopPadding = extraTopPadding,
    )

@Composable
private fun rememberTitleCollapseImpl(
    scrollable: ScrollableState,
    firstItemInfo: () -> Pair<Int, Int>?,
    isLayoutReady: () -> Boolean,
    extraTopPadding: Dp,
): TitleCollapseState {
    val density = LocalDensity.current
    val statusPx = rawStatusBarsTopPx()
    val extraPx = with(density) { extraTopPadding.roundToPx() }
    val currentFirstItem by rememberUpdatedState(firstItemInfo)
    val currentLayoutReady by rememberUpdatedState(isLayoutReady)
    val touchActive = remember { mutableStateOf(false) }

    val states = remember(scrollable, extraPx, statusPx) {
        val uiState = derivedStateOf {
            calculateTitleCollapse(
                firstItemInfo = currentFirstItem(),
                isLayoutReady = currentLayoutReady(),
                extraPx = extraPx,
                statusPx = statusPx,
            )
        }
        Triple(
            derivedStateOf { uiState.value.alpha },
            derivedStateOf { uiState.value.bar },
            derivedStateOf { uiState.value.small },
        )
    }
    val largeTitleAlpha = states.first
    val showBarBackground = states.second
    val showSmallTitle = states.third

    LaunchedEffect(scrollable, extraPx, statusPx) {
        var snapJob: Job? = null
        var snapTarget = Float.NaN
        val fingerDown = touchActive

        fun evaluate() {
            val info = currentFirstItem()
            if (info == null) {
                snapJob?.cancel(); snapJob = null; snapTarget = Float.NaN
                return
            }
            val zone = (extraPx + info.second - statusPx).coerceAtLeast(1)
            val raw = -info.first.toFloat() / zone
            if (raw <= 0.005f || raw >= 1f) {
                snapJob?.cancel(); snapJob = null; snapTarget = Float.NaN
                return
            }
            val target = if (raw < 0.5f) 0f else -zone.toFloat()
            if (snapJob != null && snapTarget == target) return
            snapJob?.cancel()
            snapTarget = target
            snapJob = launch {
                try {
                    val start = info.first.toFloat()
                    val duration = 180_000_000L
                    var t = withFrameNanos { it }
                    val t0 = t
                    var last = start
                    while (t - t0 < duration) {
                        t = withFrameNanos { it }
                        val p = ((t - t0).toFloat() / duration).coerceIn(0f, 1f)
                        val eased = 1f - (1f - p) * (1f - p) * (1f - p)
                        val pos = start + (target - start) * eased
                        scrollable.dispatchRawDelta(last - pos)
                        last = pos
                    }
                    scrollable.dispatchRawDelta(last - target)
                } finally {
                    snapTarget = Float.NaN
                }
            }
        }

        var stable: Job? = null
        launch {
            snapshotFlow { currentFirstItem()?.first }
                .collect {
                    stable?.cancel()
                    stable = launch {
                        delay(50)
                        if (!fingerDown.value) evaluate()
                    }
                }
        }
        snapshotFlow { scrollable.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling) evaluate()
            }
    }
    return TitleCollapseState(largeTitleAlpha, showBarBackground, showSmallTitle, touchActive)
}

private data class TitleCollapseValues(val alpha: Float, val bar: Boolean, val small: Boolean)

/**
 * Geometry: the first item = status-bar spacer + large title. Once its bottom
 * crosses the titlebar lower edge the large title dissolves; when it is fully
 * gone the small title is shown.
 */
private fun calculateTitleCollapse(
    firstItemInfo: Pair<Int, Int>?,
    isLayoutReady: Boolean,
    extraPx: Int,
    statusPx: Int,
): TitleCollapseValues {
    val info = firstItemInfo
    if (!isLayoutReady || info == null) return TitleCollapseValues(1f, false, false)
    val (offset, size) = info
    val zone = (extraPx + size - statusPx).coerceAtLeast(1)
    val raw = (-offset.toFloat() / zone).coerceIn(0f, 1f)
    val alpha = (1f - raw).coerceIn(0f, 1f)
    val bar = offset < 0
    val small = raw >= 1f
    return TitleCollapseValues(alpha, bar, small)
}

@Composable
fun rememberAlpha(state: LazyListState): State<Float> = remember("BaseTitle_alpha") {
    derivedStateOf {
        val currentOffsetY = state.layoutInfo.visibleItemsInfo.find { it.index == 0 }?.offset ?: -1
        val height = state.layoutInfo.visibleItemsInfo.find { it.index == 0 }?.size ?: -1
        when {
            currentOffsetY + height <= 0 -> 0f
            else -> (1f + ((currentOffsetY.toFloat() / height) * 1.8f)).coerceAtLeast(0f)
        }
    }
}

@Composable
fun rememberShowSmallTitle(alpha: State<Float>, state: LazyListState): State<Boolean> =
    remember("BaseTitle_showSmallTitle") {
        derivedStateOf { alpha.value <= 0f && state.layoutInfo.visibleItemsInfo.isNotEmpty() }
    }

@Composable
fun TitleBarIcon(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onBack: (() -> Unit)? = null,
    contentColor: Color? = null,
) {
    if (icon == null) return
    Box(
        modifier = modifier
            .padding(end = 10.dp)
            .size(38.dp)
            .background(
                (Color.White withNight Color(0xFF1C1C1E)).copy(alpha = 0.28f),
                CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onBack?.invoke() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = contentColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Sonify titlebar: progressive blur material, back button, small title and
 * optional right-side actions. The blur is drawn with MeloX's kyant backdrop
 * artifact, which is API-compatible with the upstream material.
 */
@Composable
fun TitleBar(
    title: String,
    onBack: (() -> Unit)?,
    rightBarIcon: @Composable (RowScope.() -> Unit)? = null,
    showSmallTitle: State<Boolean>,
    backdrop: LayerBackdrop,
    barBackgroundVisible: State<Boolean>? = null,
    backIconTint: Color? = null,
    liquidBackButton: Boolean = false,
) {
    val barVisible = barBackgroundVisible ?: showSmallTitle
    val pageColor = titlePageColor()
    val barSurfaceBackdrop = rememberLayerBackdrop()
    val barGlassBackdrop = rememberCombinedBackdrop(backdrop, barSurfaceBackdrop)
    Box(Modifier.fillMaxWidth().clickable(enabled = false, onClick = {})) {
        AnimatedVisibility(
            visible = barVisible.value,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(120)),
        ) {
            Spacer(
                Modifier
                    .then(
                        if (liquidBackButton) {
                            titleBarProgressiveBlur(
                                backdrop = backdrop,
                                tintColor = pageColor,
                                exportedBackdrop = barSurfaceBackdrop,
                            )
                        } else {
                            Modifier.drawPlainBackdrop(
                                backdrop = backdrop,
                                shape = { RectangleShape },
                                effects = { blur(14.dp.toPx()) },
                                onDrawSurface = { drawRect(pageColor.copy(alpha = 0.7f)) },
                            )
                        }
                    )
                    .fillMaxSize(),
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .sonifyStatusBarsPadding()
                .height(54.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 5.dp)) {
                Box(Modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
                    if (onBack != null) {
                        Box(Modifier.padding(start = 16.dp)) {
                            if (liquidBackButton) {
                                LiquidTopBarButton(
                                    onClick = onBack,
                                    backdrop = barGlassBackdrop,
                                    icon = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                                    contentDescription = "返回",
                                    iconSize = 18.dp,
                                    iconOffset = DpOffset((-2).dp, 0.dp),
                                    backdropAlpha = { if (barVisible.value) 1f else 0f },
                                    shadowAlpha = { if (barVisible.value) 1f else 0f },
                                    idleIconTint = backIconTint ?: MaterialTheme.colorScheme.onSurface,
                                    draggable = true,
                                )
                            } else {
                                SonifyGlassCircleButton(
                                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = backIconTint ?: MaterialTheme.colorScheme.onSurface,
                                    onClick = onBack,
                                )
                            }
                        }
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 70.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        AnimatedVisibility(
                            visible = showSmallTitle.value,
                            enter = fadeIn(animationSpec = tween(120, easing = EaseOut)) +
                                expandVertically(
                                    expandFrom = Alignment.Top,
                                    clip = false,
                                    animationSpec = tween(120, easing = EaseOut),
                                ),
                            exit = fadeOut(animationSpec = tween(120, easing = EaseInCirc)) +
                                shrinkVertically(
                                    shrinkTowards = Alignment.Top,
                                    clip = false,
                                    animationSpec = tween(120, easing = EaseInCirc),
                                ),
                        ) {
                            Text(text = title, fontSize = 18.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (rightBarIcon != null) {
                        Row(
                            Modifier.fillMaxSize().padding(end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                        ) {
                            rightBarIcon()
                        }
                    }
                }
                AnimatedVisibility(
                    visible = barVisible.value,
                    enter = fadeIn(animationSpec = tween(120, easing = EaseOut)),
                    exit = fadeOut(animationSpec = tween(120, easing = EaseInCirc)),
                ) {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .alpha(0.12f)
                            .height(1.dp)
                            .background(Color.Black withNight Color.White),
                    )
                }
            }
        }
    }
}

/**
 * Stable translucent circle used both by the titlebar and the artist action
 * row. It deliberately does not sample the page backdrop, so it can be placed
 * inside the recorded layer without creating a self-sampling RenderNode.
 */
@Composable
fun SonifyGlassCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    enabled: Boolean = true,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
    containerColor: Color? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .background(
                containerColor ?: (Color.White withNight Color(0xFF1C1C1E)).copy(alpha = 0.22f),
                CircleShape,
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize).alpha(if (enabled) 1f else 0.45f),
            tint = tint,
        )
    }
}

@Composable
private fun TitleItem(
    title: String,
    subTitle: String?,
    rightIcon: ImageVector?,
    onRightIcon: (() -> Unit)?,
    alpha: State<Float>,
    grid: Boolean = false,
    titleHorizontalPadding: Dp = 20.dp,
) {
    Row(
        Modifier
            .padding(horizontal = if (grid) 0.dp else titleHorizontalPadding)
            .padding(bottom = if (grid) 0.dp else 12.dp, top = 8.dp)
            .alpha(alpha.value),
    ) {
        Column(Modifier.fillMaxWidth().weight(1f)) {
            Text(
                text = title,
                fontSize = 35.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 40.sp,
            )
            if (subTitle != null) {
                Text(
                    text = subTitle,
                    modifier = Modifier.alpha(0.5f).padding(horizontal = 2.5.dp),
                )
            }
        }
        if (rightIcon != null) {
            Column(
                Modifier.fillMaxHeight().align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            enabled = onRightIcon != null,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onRightIcon ?: {},
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = rightIcon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
fun rememberIndexScrollOffsetPx(): Int = rawStatusBarsTopPx()

suspend fun LazyListState.scrollToIndexedItem(itemIndex: Int, topBarHeightPx: Int) {
    val safeIndex = itemIndex.coerceAtLeast(0)
    if (safeIndex == 0) scrollToItem(0) else scrollToItem(safeIndex, scrollOffset = -topBarHeightPx)
}

suspend fun LazyGridState.scrollToIndexedItem(itemIndex: Int, topBarHeightPx: Int) {
    val safeIndex = itemIndex.coerceAtLeast(0)
    if (safeIndex == 0) scrollToItem(0) else scrollToItem(safeIndex, scrollOffset = -topBarHeightPx)
}
