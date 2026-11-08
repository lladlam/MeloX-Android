package com.lladlam.melox.ui.podcast

import androidx.compose.runtime.saveable.rememberSaveable
import com.lladlam.melox.ui.state.rememberRetainedPageState

import com.lladlam.melox.ui.layout.LocalMeloXListDetail
import com.lladlam.melox.ui.layout.rememberMeloXDetailSelection

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.network.MeloXPodcast
import com.lladlam.melox.core.network.MeloXPodcastCategory
import com.lladlam.melox.core.network.MeloXPodcastProgram
import com.lladlam.melox.core.network.NeteaseUniversalSearchClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassCard
import com.lladlam.melox.ui.glass.MeloXPinnedListPage
import com.lladlam.melox.ui.layout.rememberMeloXStableStatusBarHeight
import com.lladlam.melox.ui.glass.MeloXShapes
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.animation.meloXCategoryEnter
import com.lladlam.melox.ui.animation.meloXCategoryExit
import com.lladlam.melox.ui.animation.MeloXPageBoundsTransform
import com.lladlam.melox.ui.MeloXInterruptiblePage
import com.lladlam.melox.ui.meloXPageRecede
import com.lladlam.melox.ui.animation.MeloXPageSpec
import com.lladlam.melox.ui.meloXEdgeOverscrollHaptic
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lladlam.melox.R
import androidx.compose.ui.res.stringResource

// 分区前缀必须进 key：同一个播客可能同时出现在「为你推荐」和「精选播客」，
// 同一作用域里两个同 key 的共享元素会直接崩，光靠 "prefix + id" 隔不开。
private const val PodcastArtworkKeyPrefix = "podcast-artwork-"

// 与「精选播客」并列的主分区（为你推荐/分类/我的订阅）——三个互斥，共用一个前缀。
private const val PodcastPrimarySectionKey = "primary"
private const val PodcastFeaturedSectionKey = "featured"

private fun podcastArtworkKey(section: String, podcastId: Long): String =
    PodcastArtworkKeyPrefix + section + "-" + podcastId

/**
 * 把播客卡封面挂到宿主的 SharedTransitionLayout 上，使点卡进详情时封面能从卡片位置
 * 一镜到底 morph 到详情 hero。scope 为空（未接入共享元素的场景）时原样返回，
 * 不影响常规渲染。
 */
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun Modifier.meloxPodcastSharedArtwork(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    key: String,
): Modifier {
    if (LocalMeloXListDetail.current || sharedTransitionScope == null || animatedVisibilityScope == null) return this
    return with(sharedTransitionScope) {
        this@meloxPodcastSharedArtwork.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope,
            // ⚠ 必须显式传：默认是渐近弹簧，会「追动靶」并在整页滑入结束的瞬间被硬切补齐
            //   —— 用户报的「最后一帧横移调整位置」。见 [MeloXPageBoundsTransform] 的完整推导。
            boundsTransform = MeloXPageBoundsTransform,
            // 过渡期间封面留在共享 overlay 层，避免被列表/详情的裁剪吃掉。
            renderInOverlayDuringTransition = true,
            zIndexInOverlay = 1f,
        )
    }
}

@Composable
fun MeloXPodcastScreen(
    modifier: Modifier = Modifier,
    subscriptionsOnly: Boolean = false,
    initialPodcastId: Long? = null,
    onExit: (() -> Unit)? = null,
    bottomPadding: Dp = MeloXBottomContentClearance,
    // 只有作为底部导航的一级页时才自己让出状态栏高度；从搜索/发现/云音乐/歌单
    // 推入时父容器已吃过 statusBarsPadding，这里再留一次会把首屏整体下压一截。
    applyStatusBarPadding: Boolean = false,
    // 从别的页面「推入」本页时传 PageEnter 时长：先把首屏（加载态）组合出来让滑动跑满帧，
    // 滑动结束再拉数据。不延迟的话，首屏列表/玻璃卡/图片会在滑动起步那一帧一起组合，
    // UI 线程被占住 ⇒ 观感是「点下去先卡一下，然后才开始滑」。
    deferLoadMillis: Long = 0,
) {
    val onDetailSelected = rememberMeloXDetailSelection()
    val context = LocalContext.current.applicationContext
    val client = remember(context) {
        NeteaseUniversalSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
    }
    // 可见性态与内容态必须拆开：返回只清可见性态，内容态留到退出过渡跑完。
    // 同一 state 既驱动 visible 又 let 取内容 ⇒ 返回瞬间详情子树被拆，
    // sharedElement 就只剩进入没有返回。
    //
    // 卡片可见性（selectedCardKey）同样必须跟着清：它驱动每张卡的
    // PodcastCardVisibility visible。不清的话卡片在详情打开期间一直是
    // visible=false（已退出过渡），返回时不会重新 enter —— 详情 hero 的 exit
    // 找不到配对对象，morph 回卡片就没了。搜索页用 selectedDetail?.key 驱动卡片
    // visible，selectedDetail 返回时清零，所以卡片会重新 enter 配上。
    var selectedPodcastId by rememberSaveable(initialPodcastId) {
        mutableStateOf(initialPodcastId)
    }
    var selectedCardKey by rememberSaveable { mutableStateOf<String?>(null) }
    // 详情端的内容态：podcast 对象 + 配对 key。返回时都不清，退出过渡里详情
    // hero 才有东西画、才有 key 配对。
    var detailPodcast by rememberRetainedPageState(initialPodcastId) {
        mutableStateOf(initialPodcastId?.let { MeloXPodcast(id = it, name = "播客") })
    }
    var detailArtworkKey by rememberSaveable(initialPodcastId) { mutableStateOf<String?>(null) }
    var subscriptionGeneration by rememberSaveable { mutableIntStateOf(0) }

    BackHandler(enabled = selectedPodcastId != null) {
        if (initialPodcastId != null) onExit?.invoke() else selectedPodcastId = null
        selectedCardKey = null
    }

    // 有 hero 的详情是「纯覆盖」（主页不做视差退让），跟搜索页有 hero 的详情同款动作。
    // 首页永远渲染：卡片封面的 sharedElement 一直活着，返回时才有配对对象可 morph 回。
    // AnimatedContent 的 if/else 互斥会让首页在详情显示期间被 dispose，返回时卡片是全新
    // 组合实例、配对窗口已错过 —— 那就是「只有进入没有返回」的根因。
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        val sharedScope = this
        // ⚠ hero push 时旧页也左移让出 1/3（与首页/发现同款）：与推入页走同一条弹簧
        //   （[MeloXPageSpec]），两页严格互补，观感与「无 hero 的整页推入」一致，
        //   区别只是多一个封面 morph。旧页位移走 [meloXPageRecede] 的**布局期** place：
        //   共享元素 morph 的 bounds 取自 lookahead 布局坐标，走 graphicsLayer 封面不跟随、半路脱开。
        val podcastRecede = remember { Animatable(0f) }
        val podcastOpen = selectedPodcastId != null
        LaunchedEffect(podcastOpen) {
            val target = if (podcastOpen) 1f else 0f
            if (podcastRecede.value == target) return@LaunchedEffect
            podcastRecede.animateTo(target, MeloXPageSpec)
        }
        Box(Modifier.fillMaxSize().meloXPageRecede { podcastRecede.value }) {
            PodcastHome(
                client = client,
                subscriptionsOnly = subscriptionsOnly,
                reloadToken = subscriptionGeneration,
                bottomPadding = bottomPadding,
                applyStatusBarPadding = applyStatusBarPadding,
                deferLoadMillis = deferLoadMillis,
                sharedTransitionScope = sharedScope,
                selectedArtworkKey = selectedCardKey,
                onPodcast = { podcast, artworkKey ->
                    onDetailSelected()
                    selectedPodcastId = podcast.id
                    selectedCardKey = artworkKey
                    detailPodcast = podcast
                    detailArtworkKey = artworkKey
                },
            )
        }
        // 详情覆盖层：从右满宽滑入盖住首页，返回时向右滑出露出首页。
        // 必须补不透明背板——MeloX 页面本身透明底，不补背板滑入全程会把下层首页
        // 透出来，观感是撕裂而不是覆盖。
        MeloXInterruptiblePage(
            visible = selectedPodcastId != null,
            retreatProgress = { 0f },
            modifier = Modifier.fillMaxSize().zIndex(1f),
        ) {
            val detailVisibilityScope = this
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                detailPodcast?.let { podcast ->
                    PodcastDetail(
                        initialPodcast = podcast,
                        client = client,
                        bottomPadding = bottomPadding,
                        onBack = {
                            if (initialPodcastId != null) onExit?.invoke() else selectedPodcastId = null
                            selectedCardKey = null
                        },
                        onSubscriptionChanged = { subscriptionGeneration++ },
                        sharedTransitionScope = sharedScope,
                        animatedVisibilityScope = detailVisibilityScope,
                        artworkSharedKey = detailArtworkKey,
                    )
                }
            }
        }
    }
}

@Composable
private fun PodcastHome(
    client: NeteaseUniversalSearchClient,
    subscriptionsOnly: Boolean,
    reloadToken: Int,
    bottomPadding: Dp,
    applyStatusBarPadding: Boolean = false,
    deferLoadMillis: Long = 0,
    // 播客首页→详情的一镜到底作用域。由宿主透传；没有（直进详情/深链）时卡片按普通渲染。
    // 卡片封面的 animatedVisibilityScope 不从这里传——每张卡自己包一层
    // [PodcastCardVisibility]（见下），只有被点的那张卡在详情打开期间走 exit 过渡，
    // sharedElement 才配得上。
    sharedTransitionScope: SharedTransitionScope? = null,
    // 当前被点开的封面 key。非 null 时对应卡片 visible=false（退出过渡）。
    selectedArtworkKey: String? = null,
    // 回传卡所属分区的 key：同一播客可能同时出现在两个分区，key 必须带上区分前缀。
    onPodcast: (MeloXPodcast, String) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var payload by rememberRetainedPageState(subscriptionsOnly) { mutableStateOf(PodcastHomePayload()) }
    var selectedCategory by rememberRetainedPageState { mutableStateOf<MeloXPodcastCategory?>(null) }
    var categoryPodcasts by rememberRetainedPageState(selectedCategory?.id) { mutableStateOf<List<MeloXPodcast>>(emptyList()) }
    var loading by remember { mutableStateOf(payload.isEmpty) }
    var categoryLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var manualReload by rememberSaveable { mutableIntStateOf(0) }
    var homeLoaded by rememberRetainedPageState(reloadToken, manualReload, subscriptionsOnly) { mutableStateOf(false) }

    suspend fun loadHome() {
        loading = true
        error = null
        runCatching {
            coroutineScope {
                if (subscriptionsOnly) {
                    val loggedIn = NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie(context))
                    PodcastHomePayload(
                        subscriptions = if (loggedIn) client.subscribedPodcasts(limit = 100).values else emptyList(),
                    )
                } else {
                    val featured = async { client.featuredPodcasts() }
                    val personalized = async { client.personalizedPodcasts(12) }
                    val categories = async { client.podcastCategories() }
                    PodcastHomePayload(
                        featured = featured.await(),
                        personalized = personalized.await(),
                        categories = categories.await(),
                    )
                }
            }
        }.onSuccess { payload = it; homeLoaded = true }
            .onFailure { error = it.message ?: "播客加载失败" }
        loading = false
    }

    LaunchedEffect(reloadToken, manualReload, subscriptionsOnly) {
        if (homeLoaded) return@LaunchedEffect
        // 推入动画期间只画加载态，数据等滑动跑完再拉（详见 deferLoadMillis 注释）。
        if (deferLoadMillis > 0) delay(deferLoadMillis)
        loadHome()
    }
    LaunchedEffect(selectedCategory?.id) {
        val category = selectedCategory ?: run {
            categoryPodcasts = emptyList()
            return@LaunchedEffect
        }
        if (categoryPodcasts.isNotEmpty()) return@LaunchedEffect
        categoryLoading = true
        runCatching { client.podcastsByCategory(category.id, offset = 0, limit = 30).values }
            .onSuccess { loaded -> if (selectedCategory?.id == category.id) categoryPodcasts = loaded }
            .onFailure { error = it.message ?: "分类加载失败" }
        categoryLoading = false
    }

    // ⚠ 2026-09-29：状态栏避让不再打在 LazyColumn 的 modifier 上（那会把滚动视口压到
    //   状态栏下缘、内容被裁在 y=41dp），改为加进**顶部 contentPadding**：静止态位置不变，
    //   滚动时标题/卡片从状态栏底下穿出。`applyStatusBarPadding=false`（从别处推入、父容器
    //   已吃过）时保持 0，语义与原先一致。
    val statusBarTopIfNeeded = if (applyStatusBarPadding) rememberMeloXStableStatusBarHeight() else 0.dp

    PullToRefreshBox(
        isRefreshing = loading,
        onRefresh = { manualReload++ },
        modifier = Modifier.fillMaxSize(),
    ) {
        val podcastListState = rememberLazyListState()
        when {
            loading && payload.isEmpty -> PodcastLoadingState()
            error != null && payload.isEmpty -> PodcastErrorState(error.orEmpty()) { scope.launch { manualReload++ } }
            else -> LazyColumn(
                state = podcastListState,
                // 触顶/触底橡胶回弹触感。
                modifier = Modifier.fillMaxSize().meloXEdgeOverscrollHaptic(podcastListState),
                contentPadding = PaddingValues(
                    top = 22.dp + statusBarTopIfNeeded,
                    bottom = bottomPadding + 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item(key = "title") {
                    Text(
                        if (subscriptionsOnly) "订阅播客" else "播客",
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (!subscriptionsOnly && payload.categories.isNotEmpty()) {
                    item(key = "categories") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item(key = "all") {
                                PodcastCategoryButton("为你推荐", selectedCategory == null) { selectedCategory = null }
                            }
                            items(payload.categories, key = MeloXPodcastCategory::id) { category ->
                                PodcastCategoryButton(category.name, selectedCategory?.id == category.id) {
                                    selectedCategory = category
                                }
                            }
                        }
                    }
                }

                if (subscriptionsOnly) {
                    if (payload.subscriptions.isNotEmpty()) {
                        item(key = "subscriptions") {
                            PodcastSection(
                                "我的订阅",
                                payload.subscriptions,
                                PodcastPrimarySectionKey,
                                onPodcast,
                                sharedTransitionScope,
                                selectedArtworkKey,
                            )
                        }
                    } else if (!loading) {
                        item(key = "empty-subscriptions") {
                            PodcastEmptyState(
                                if (NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie(context))) {
                                    stringResource(R.string.podcast_none_subscribed)
                                } else {
                                    stringResource(R.string.podcast_login_subscriptions)
                                },
                            )
                        }
                    }
                } else {
                    item(key = "primary-section") {
                        PodcastPrimarySection(
                            category = selectedCategory,
                            personalized = payload.personalized,
                            categoryPodcasts = categoryPodcasts,
                            categoryLoading = categoryLoading,
                            onPodcast = onPodcast,
                            sharedTransitionScope = sharedTransitionScope,
                            selectedArtworkKey = selectedArtworkKey,
                        )
                    }
                    if (payload.featured.isNotEmpty()) {
                        item(key = "featured") {
                            PodcastSection(
                                "精选播客",
                                payload.featured,
                                PodcastFeaturedSectionKey,
                                onPodcast,
                                sharedTransitionScope,
                                selectedArtworkKey,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class PodcastHomePayload(
    val featured: List<MeloXPodcast> = emptyList(),
    val personalized: List<MeloXPodcast> = emptyList(),
    val categories: List<MeloXPodcastCategory> = emptyList(),
    val subscriptions: List<MeloXPodcast> = emptyList(),
) {
    val isEmpty: Boolean
        get() = featured.isEmpty() && personalized.isEmpty() && categories.isEmpty() && subscriptions.isEmpty()
}

@Composable
private fun PodcastCategoryButton(title: String, selected: Boolean, onClick: () -> Unit) {
    MeloXGlassButton(
        onClick = onClick,
        modifier = Modifier.height(44.dp),
        style = if (selected) MeloXGlassButtonStyle.BorderedProminent else MeloXGlassButtonStyle.Bordered,
        shape = MeloXShapes.capsule,
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 9.dp),
    ) { Text(title, maxLines = 1) }
}

@Composable
private fun PodcastSection(
    title: String,
    podcasts: List<MeloXPodcast>,
    sectionKey: String,
    onPodcast: (MeloXPodcast, String) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    selectedArtworkKey: String? = null,
    loading: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // loading 指示器挂在标题行右侧，不额外占布局高度：独立占位的 120dp
        // 会把下方「精选播客」整段顶下去再顶回来，那本身就是另一种闪。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                modifier = Modifier.padding(start = 20.dp),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (loading) {
                Spacer(Modifier.weight(1f))
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 20.dp).size(16.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        if (podcasts.isEmpty()) {
            PodcastEmptyState("暂无播客")
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(podcasts, key = MeloXPodcast::id) { podcast ->
                    val artworkKey = podcastArtworkKey(sectionKey, podcast.id)
                    PodcastCardVisibility(
                        artworkKey = artworkKey,
                        selectedArtworkKey = selectedArtworkKey,
                        modifier = Modifier.animateItem(),
                    ) {
                        val cardVisibilityScope = this
                        MeloXGlassCard(
                            modifier = Modifier.size(width = 176.dp, height = 244.dp),
                            onClick = { onPodcast(podcast, artworkKey) },
                        ) {
                            // 封面单独包一层 Box 承载 sharedElement：size/clip 放在
                            // sharedElement 之后，阴影之类的外部效果严禁进这条链。
                            Box(
                                Modifier
                                    .meloxPodcastSharedArtwork(
                                        sharedTransitionScope,
                                        cardVisibilityScope,
                                        artworkKey,
                                    )
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(18.dp)),
                            ) {
                                AsyncImage(
                                    model = podcast.artworkUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Text(
                                podcast.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                podcast.recommendation ?: podcast.host?.nickname.orEmpty(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 */
@Composable
private fun podcastSectionTitle(categoryName: String): String {
    // 分类名末尾已含「播客」时不再拼接，避免出现「音乐播客播客」。
    val localized = stringResource(R.string.podcast_category_title, categoryName)
    return if (categoryName.endsWith("播客")) categoryName else localized
}

/**
 * 「为你推荐 / 分类播客」分区。
 *
 * 用 [AnimatedContent] 在两个数据源之间过渡，而不是靠外层 `when` 换整个分支：
 * `item` 的 key 恒定时，`when` 三分支会让旧内容整块 dispose、新内容整块出现
 * （硬切），中间那个 120dp 的加载占位还会把下方「精选播客」整段顶下去再顶回来。
 *
 * 过渡是「双向滑动 1/4 宽 + 淡入淡出」：纯淡出会露出下层，观感像闪烁；
 * 纯滑动又太像翻页。`contentAlignment = TopStart` 保证顶对齐，标题行不跳。
 *
 * 同一分类内的数据到达（刷新）不触发过渡——`targetState` 没变，LazyRow 靠稳定
 * key + `animateItem()` 对真正变化的卡逐项进出，没变的卡完全不动，比整块交叉淡出
 * 更安静。切到新分类时短暂显示上一个分类的数据（stale-while-revalidate），由标题行
 * 的 spinner 表明正在加载；就绪提交后由 `animateItem` 做卡片级的进出，不留空窗。
 */
@Composable
private fun PodcastPrimarySection(
    category: MeloXPodcastCategory?,
    personalized: List<MeloXPodcast>,
    categoryPodcasts: List<MeloXPodcast>,
    categoryLoading: Boolean,
    onPodcast: (MeloXPodcast, String) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    selectedArtworkKey: String?,
) {
    // 0L = 为你推荐。只有切分类时 targetState 才变化、才走过渡。
    AnimatedContent(
        targetState = category?.id ?: 0L,
        label = "podcast-primary-section",
        contentAlignment = Alignment.TopStart,
        transitionSpec = {
            // dir 跟随分类 id 先后：往后切向右滑、往回切向左滑，同一套手感。
            val dir = if (targetState > initialState) 1 else -1
            meloXCategoryEnter(dir) togetherWith meloXCategoryExit(dir)
        },
    ) { _ ->
        PodcastSection(
            title = if (category == null) stringResource(R.string.podcast_for_you) else podcastSectionTitle(category.name),
            podcasts = if (category == null) personalized else categoryPodcasts,
            sectionKey = PodcastPrimarySectionKey,
            onPodcast = onPodcast,
            sharedTransitionScope = sharedTransitionScope,
            selectedArtworkKey = selectedArtworkKey,
            loading = categoryLoading,
        )
    }
}

/**
 * 被点中的那张卡必须真的进入 exit 过渡，共享元素才会配对：
 * 恒可见（`AnimatedVisibility(visible = true)`）是配不上的。其他卡片保持可见、
 * 不做过渡，所以这里只对被点中的卡切 visible。
 */
@Composable
private fun PodcastCardVisibility(
    artworkKey: String,
    selectedArtworkKey: String?,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = LocalMeloXListDetail.current || artworkKey != selectedArtworkKey,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier,
        label = "podcast-card-$artworkKey",
        content = content,
    )
}

@Composable
private fun PodcastDetail(
    initialPodcast: MeloXPodcast,
    client: NeteaseUniversalSearchClient,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onSubscriptionChanged: () -> Unit,
    // 一镜到底：宿主透传作用域与配对 key。都不传（深链直进详情）时 hero 按普通渲染。
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    artworkSharedKey: String? = null,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    // 这些文案会在 onClick / 协程里用到，必须在这里先解析。
    val loginSubscribeMessage = stringResource(R.string.podcast_login_subscribe)
    val subscribeFailedMessage = stringResource(R.string.podcast_subscribe_failed)
    val programsFailedMessage = stringResource(R.string.podcast_episodes_failed)
    val subscribedLabel = stringResource(R.string.podcast_subscribed)
    val subscribeLabel = stringResource(R.string.podcast_subscribe)
    var podcast by rememberRetainedPageState(initialPodcast.id) { mutableStateOf(initialPodcast) }
    // 兜底 key 用 initialPodcast.id：详情数据被接口刷新替换后 podcast 会变，id 不变。
    val artworkKey = artworkSharedKey ?: podcastArtworkKey(PodcastPrimarySectionKey, initialPodcast.id)
    var programs by rememberRetainedPageState(initialPodcast.id) { mutableStateOf<List<MeloXPodcastProgram>>(emptyList()) }
    var loading by remember(initialPodcast.id) { mutableStateOf(programs.isEmpty()) }
    var subscribing by remember(initialPodcast.id) { mutableStateOf(false) }
    var error by remember(initialPodcast.id) { mutableStateOf<String?>(null) }
    var reloadKey by rememberSaveable(initialPodcast.id) { mutableIntStateOf(0) }
    var programsLoaded by rememberRetainedPageState(initialPodcast.id, reloadKey) { mutableStateOf(false) }

    LaunchedEffect(initialPodcast.id, reloadKey) {
        if (programsLoaded) return@LaunchedEffect
        loading = true
        error = null
        runCatching {
            coroutineScope {
                val detail = async { client.podcastDetail(initialPodcast.id) }
                val episodes = async { client.podcastPrograms(initialPodcast.id, offset = 0, limit = 100, ascending = false) }
                detail.await() to episodes.await().values
            }
        }.onSuccess { (loadedPodcast, loadedPrograms) ->
            loadedPodcast?.let { podcast = it }
            programs = loadedPrograms
            programsLoaded = true
        }.onFailure { error = it.message ?: programsFailedMessage }
        loading = false
    }

    MeloXPinnedListPage(
        title = podcast.name,
        subtitle = podcast.host?.nickname,
        onNavigateBack = onBack,
        bottomPadding = bottomPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        actions = {
            MeloXGlassButton(
                onClick = {
                    if (!NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie(context))) {
                        error = loginSubscribeMessage
                    } else if (!subscribing) {
                        val desired = !podcast.subscribed
                        subscribing = true
                        scope.launch {
                            runCatching { client.setPodcastSubscribed(podcast.id, desired) }
                                .onSuccess {
                                    podcast = podcast.copy(subscribed = desired)
                                    onSubscriptionChanged()
                                }
                                .onFailure { error = it.message ?: subscribeFailedMessage }
                            subscribing = false
                        }
                    }
                },
                enabled = !subscribing,
                style = if (podcast.subscribed) MeloXGlassButtonStyle.BorderedProminent else MeloXGlassButtonStyle.Bordered,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) { Text(if (podcast.subscribed) subscribedLabel else subscribeLabel, maxLines = 1) }
        },
    ) {
        item(key = "podcast-hero") {
            MeloXGlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = podcast.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // 与卡片封面共享同一个 key ⇒ 一镜到底。size/clip 保持在共享元素之后。
                        modifier = Modifier
                            .meloxPodcastSharedArtwork(
                                sharedTransitionScope,
                                animatedVisibilityScope,
                                artworkKey,
                            )
                            .size(112.dp)
                            .clip(RoundedCornerShape(22.dp)),
                    )
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            podcast.host?.nickname ?: podcast.category.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            podcast.description.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (loading) item(key = "loading") { PodcastLoadingInline() }
        error?.let { message ->
            item(key = "error") {
                MeloXGlassCard(Modifier.fillMaxWidth()) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    MeloXGlassButton(
                        onClick = { reloadKey++ },
                        modifier = Modifier.fillMaxWidth(),
                        style = MeloXGlassButtonStyle.BorderedProminent,
                    ) { Text("重试") }
                }
            }
        }
        if (!loading && programs.isEmpty() && error == null) item(key = "empty") { PodcastEmptyState("暂无节目") }
        programs.forEach { program ->
            item(key = "program-${program.id}") {
                PodcastProgramRow(program) {
                    val songs = programs.mapNotNull(MeloXPodcastProgram::playbackSong)
                    program.playbackSong?.let { selected ->
                        PlaybackCommands.playQueue(context, songs, selected.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastProgramRow(program: MeloXPodcastProgram, onClick: () -> Unit) {
    MeloXGlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = program.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    program.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (program.durationMs > 0L) "${program.durationMs / 60_000L} 分钟" else "时长未知",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            MeloXSymbolIcon(MeloXSymbol.Play, Modifier.size(22.dp), MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun PodcastLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun PodcastLoadingInline() {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun PodcastErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            MeloXGlassButton(onRetry, style = MeloXGlassButtonStyle.BorderedProminent) { Text("重试") }
        }
    }
}

@Composable
private fun PodcastEmptyState(message: String) {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
