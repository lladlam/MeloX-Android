package com.lladlam.melox.ui.settings

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.ui.animation.meloXPageEnter
import com.lladlam.melox.ui.animation.meloXPageExit
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import coil3.compose.AsyncImage
import com.lladlam.melox.R
import com.lladlam.melox.BuildConfig
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.diagnostics.MeloXLogExporter
import com.lladlam.melox.core.diagnostics.MeloXLogDeviceInfo
import com.lladlam.melox.core.provider.bilibili.BilibiliPlaybackAssociationStore
import com.lladlam.melox.core.music.provider.PlaybackAccountSlot
import com.lladlam.melox.core.music.provider.PlaybackAccountStore
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.core.music.provider.UserLibraryCapability
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.provider.qqmusic.QQMusicSessionStore
import com.lladlam.melox.core.provider.kugou.KugouSessionStore
import com.lladlam.melox.ui.account.QQMusicLoginScreen
import com.lladlam.melox.ui.account.KugouLoginScreen
import com.lladlam.melox.ui.account.NeteaseLoginScreen
import com.lladlam.melox.playback.ProviderPlaybackQualityRuntime
import com.lladlam.melox.playback.CrossProviderPlaybackPreferences
import com.lladlam.melox.playback.MeloXAudioAnalysisRuntime
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.network.MeloXMessageContact
import com.lladlam.melox.core.network.MeloXPrivateMessage
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.network.MeloXHttpClient
import com.lladlam.melox.core.network.MeloXGitHubRouting
import com.lladlam.melox.core.network.MeloXGitHubSource
import com.lladlam.melox.core.network.parseNeteaseListenTogetherInvitation
import com.lladlam.melox.core.recommendation.LocalAnalysisStage
import com.lladlam.melox.core.recommendation.LocalRecommendationEngine
import com.lladlam.melox.core.recommendation.LocalRecommendationStore
import com.lladlam.melox.core.recognition.SongRecognitionClient
import com.lladlam.melox.core.recognition.SongRecognitionResult
import com.lladlam.melox.core.update.MeloXRelease
import com.lladlam.melox.core.update.MeloXUpdateClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.playback.MeloXAutoMixFadeCurve
import com.lladlam.melox.playback.MeloXAutoMixDiagnostics
import com.lladlam.melox.playback.MeloXAutoMixFallback
import com.lladlam.melox.playback.MeloXAutoMixMode
import com.lladlam.melox.playback.MeloXAutoMixSettings
import com.lladlam.melox.playback.MeloXEqualizerController
import com.lladlam.melox.playback.MeloXPlaybackModePreferences
import com.lladlam.melox.playback.MeloXAudioAnalysisPreferences
import com.lladlam.melox.playback.MeloXPlaybackService
import com.lladlam.melox.playback.MeloXMediaCache
import com.lladlam.melox.playback.MeloXListenTogetherCoordinator
import com.lladlam.melox.platform.floating.MeloXFloatingLyricsService
import com.lladlam.melox.platform.xiaomi.HyperOsFocusBridge
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.MeloXPinkCat
import com.lladlam.melox.ui.glass.MeloXActionIcon
import com.lladlam.melox.ui.glass.MeloXGlassTextField
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassToggle
import com.lladlam.melox.ui.glass.MeloXSettingsDropdown
import com.lladlam.melox.ui.glass.MeloXShapes
import com.lladlam.melox.ui.glass.MeloXTypography
import com.lladlam.melox.ui.glass.MeloXIosGroupedList
import com.lladlam.melox.ui.glass.MeloXIosListRow
import com.lladlam.melox.ui.glass.MeloXIosTopBar
import com.lladlam.melox.ui.glass.MeloXPinnedListPage
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXSymbolVariant
import com.lladlam.melox.ui.glass.MeloXSystemColors
import com.lladlam.melox.ui.glass.meloXContentSurface
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.legal.MELOX_LEGAL_VERSION
import com.lladlam.melox.ui.legal.MeloXLegalDocument
import com.lladlam.melox.ui.legal.MeloXLegalDocumentDialog
import com.lladlam.melox.ui.legal.MeloXLegalLinks
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigRuntime
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigSource
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigConsent
import com.lladlam.melox.ui.legal.MeloXCloudControlConsentDialog
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

private enum class SettingsRoute(val title: String) {
    Playback("播放"),
    PlayerAppearance("外观"),
    Lyrics("歌词"),
    SystemPlayback("系统歌词显示"),
    SkylineLyrics("全屏天际歌词"),
    FloatingLyrics("悬浮窗歌词"),
    ContentFeatures("功能模块"),
    Recognition("听歌识曲"),
    Messages("私信与站内分享"),
    ListenTogether("一起听"),
    Content("内容"),
    Storage("存储管理"),
    TabLayout("页面与标签栏"),
    General("通用"),
    RemoteConfig("远程兼容性配置"),
    About("关于 MeloX"),
    Legal("隐私政策与免责声明"),
    Privacy("隐私与本地算法"),
    Developer("开发者选项"),
    Experimental("测试功能尝鲜"),
}

private data class SettingsItem(
    val route: SettingsRoute,
    val subtitle: String,
    val symbol: String,
    val keywords: String,
)

private data class SettingsSection(val title: String, val items: List<SettingsItem>)

private val SettingsSections = listOf(
    SettingsSection("应用", listOf(
        SettingsItem(SettingsRoute.General, "主题、启动行为与链接处理", "⚙", "主题 浅色 深色 跟随系统 默认页面 剪贴板"),
        SettingsItem(SettingsRoute.PlayerAppearance, "背景、封面动画与屏幕常亮", "✦", "模糊 色彩 饱和度 封面 自动锁屏"),
        SettingsItem(SettingsRoute.Content, "地区、歌单信息和发现内容", "▦", "华语 欧美 韩国 日本 播放量 内容"),
        SettingsItem(SettingsRoute.Playback, "音质、播放行为与自动混音", "♫", "高品质 无损 上一首 页面记忆 心动模式 交叉淡化 播放"),
        SettingsItem(SettingsRoute.Lyrics, "翻译、罗马音、逐字与歌词交互", "❞", "Apple Music EVA 文字PV 字体 YRC 翻译 罗马音 歌词"),
        SettingsItem(SettingsRoute.Storage, "空间统计、下载与缓存清理", "▰", "下载 存储 缓存 清理 数据库"),
    )),
    SettingsSection("扩展", listOf(
        SettingsItem(SettingsRoute.ContentFeatures, "播客、云盘、最近播放等模块", "☷", "播客 广播 云盘 最近播放 下载"),
        SettingsItem(SettingsRoute.Recognition, "麦克风音频指纹与持续识别", "⌁", "听歌识曲 麦克风 指纹 Shazam 持续识别"),
        SettingsItem(SettingsRoute.Messages, "联系人、会话历史与文字私信", "✉", "私信 联系人 会话 分享 网易云"),
        SettingsItem(SettingsRoute.ListenTogether, "创建、加入和管理网易云一起听房间", "◎", "一起听 房间 邀请 同步"),
        SettingsItem(SettingsRoute.TabLayout, "首页、标签栏与音乐库页面", "▥", "首页 标签栏 排序 推荐 歌单 历史"),
        SettingsItem(SettingsRoute.SystemPlayback, "通知、锁屏和系统媒体信息", "▣", "控制中心 通知 锁屏 Media3"),
        SettingsItem(SettingsRoute.SkylineLyrics, "横屏布局与动态背景歌词", "▱", "横屏 字号 背景歌词"),
        SettingsItem(SettingsRoute.FloatingLyrics, "Android 悬浮歌词能力与权限", "▤", "画中画 悬浮窗 其他应用"),
    )),
    SettingsSection("关于", listOf(
        SettingsItem(SettingsRoute.RemoteConfig, "签名配置状态、平台熔断声明与本地缓存", "⌁", "远程 配置 云控 签名 熔断 兼容 GitHub"),
        SettingsItem(SettingsRoute.About, "版本、项目主页与开源信息", "ⓘ", "GitHub 更新 开源 许可"),
        SettingsItem(SettingsRoute.Legal, "查看隐私政策、免责声明与同意版本", "▤", "隐私 政策 免责声明 法律 条款 数据"),
        SettingsItem(SettingsRoute.Developer, "播放器诊断与迁移状态", "⌘", "BeatNet 节拍 调试 日志"),
        SettingsItem(SettingsRoute.Experimental, "预览尚未稳定的新功能", "✦", "实验 测试 歌词 强绑定"),
    )),
)

@Composable
fun SettingsScreen(
    session: NeteaseSessionStore,
    source: MusicSource = MusicSource.Netease,
    onLogin: () -> Unit,
    onOpenAccount: (() -> Unit)? = null,
    onOpenServices: (() -> Unit)? = null,
    onOpenMessages: (() -> Unit)? = null,
    initialRouteRequest: String? = null,
    onInitialRouteConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    var route by remember { mutableStateOf<SettingsRoute?>(null) }
    val backProgress = remember { Animatable(0f) }
    var search by remember { mutableStateOf("") }
    // Keep the root ScrollState alive while a detail route is displayed.
    // Creating it inside the root-only branch reset Settings to y=0 on Back.
    val rootScrollState = rememberScrollState()

    LaunchedEffect(Unit) { MeloXSettingsPreferences.initialize(context) }
    LaunchedEffect(Unit) {
        if (LocalRecommendationStore.isAlgorithmEnabled(context) && LocalRecommendationStore.hasPersonalizationConsent(context)) {
            LocalRecommendationEngine.start(context)
        }
    }
    LaunchedEffect(initialRouteRequest) {
        initialRouteRequest?.let { requested ->
            route = SettingsRoute.entries.firstOrNull { it.name == requested }
            onInitialRouteConsumed()
        }
    }
    LaunchedEffect(session.cookie) {
        if (session.isLoggedIn) session.refreshProfile()
    }

    PredictiveBackHandler(enabled = route != null) {
        try {
            it.collect { event -> backProgress.snapTo(event.progress) }
            backProgress.animateTo(1f, tween(160))
            route = null
            backProgress.snapTo(0f)
        } catch (_: CancellationException) {
            backProgress.animateTo(0f)
        }
    }

    AnimatedVisibility(
        visible = route != null,
        enter = meloXPageEnter(fromRight = true),
        exit = meloXPageExit(toRight = true),
        modifier = Modifier.fillMaxSize().zIndex(1f),
    ) {
        route?.let { selectedRoute ->
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = size.width * backProgress.value
                        val scale = 1f - 0.08f * backProgress.value
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
            ) {
                SettingsDetailScreen(route = selectedRoute, source = source, session = session, onBack = { route = null })
            }
        }
    }
    AnimatedVisibility(
        // Keep the destination underneath the detail page so predictive back
        // reveals real Settings content instead of the Scaffold background.
        visible = true,
        enter = meloXPageEnter(fromRight = false),
        exit = meloXPageExit(toRight = false),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(0f)
            .then(if (route != null) Modifier.clearAndSetSemantics { } else Modifier),
    ) {
    val normalized = search.trim().lowercase()
    val visibleSections = SettingsSections.mapNotNull { section ->
        val filtered = section.items.filter { item ->
            normalized.isBlank() || listOf(item.route.title, item.subtitle, item.keywords)
                .joinToString(" ").lowercase().contains(normalized)
        }
        filtered.takeIf { it.isNotEmpty() }?.let { SettingsSection(section.title, it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rootScrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = MeloXBottomContentClearance),
    ) {
        MeloXIosTopBar(
            title = stringResource(R.string.tab_settings),
            modifier = Modifier.padding(horizontal = 0.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        )
        Spacer(Modifier.height(14.dp))
        SettingsSearchField(value = search, onValueChange = { search = it })
        Spacer(Modifier.height(20.dp))

        if (normalized.isBlank() || "网易云账号 登录 cookie 用户".contains(normalized)) {
            SettingsAccountCard(
                session = session,
                onLogin = onLogin,
                onOpenAccount = onOpenAccount,
                onOpenServices = onOpenServices,
            )
            Spacer(Modifier.height(24.dp))
        }

        visibleSections.forEach { section ->
            Text(
                section.title,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 8.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            SettingsSectionCard(section) { selected ->
                if (selected == SettingsRoute.Messages && onOpenMessages != null) {
                    onOpenMessages()
                } else {
                    route = selected
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        if (visibleSections.isEmpty() && normalized.isNotBlank()) {
            Text(
                "没有找到设置，换个关键词再试。",
                modifier = Modifier.padding(vertical = 36.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
            )
        }

        if (normalized.isBlank()) {
            SettingsResetCard()
            Spacer(Modifier.height(18.dp))
            if (session.isLoggedIn) {
                SettingsDangerButton("退出登录") { session.clear() }
            }
        }
    }
    }
}

@Composable
private fun SettingsSearchField(value: String, onValueChange: (String) -> Unit) {
    MeloXGlassTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        leadingContent = {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.Search,
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
            )
        },
        placeholder = { Text("搜索设置", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f), fontSize = 16.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            lineHeight = 21.sp,
        ),
    )
}

@Composable
private fun SettingsAccountCard(
    session: NeteaseSessionStore,
    onLogin: () -> Unit,
    onOpenAccount: (() -> Unit)?,
    onOpenServices: (() -> Unit)?,
) {
    val accent = com.lladlam.melox.ui.glass.MeloXSystemColors.Red
    Text(
        "账号",
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
    )
    MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
        when {
            session.isLoggedIn && session.profile != null -> {
                val profile = session.profile!!
                MeloXIosListRow(
                    title = profile.nickname,
                    leading = { AsyncImage(model = profile.avatarUrl, contentDescription = null, modifier = Modifier.size(30.dp).clip(CircleShape)) },
                    detail = "已登录",
                    chevronTint = accent,
                     onClick = onOpenAccount ?: onLogin,
                    showTopSeparator = false,
                )
            }
            session.isLoggedIn && session.isRefreshing -> MeloXIosListRow(
                title = "正在读取账号信息",
                leading = { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = accent) },
                onClick = onOpenServices,
                showTopSeparator = false,
            )
            else -> MeloXIosListRow(
                title = "登录网易云音乐",
                leading = { MeloXSymbolIcon(MeloXSymbol.Person, Modifier.size(30.dp), accent, MeloXSymbolVariant.Fill) },
                chevronTint = accent,
                 onClick = onOpenAccount ?: onLogin,
                showTopSeparator = false,
            )
        }
        if (onOpenServices != null) {
            MeloXIosListRow(
                title = "音乐服务",
                subtitle = "切换音乐源、登录账号与聚合设置",
                leading = { MeloXSymbolIcon(MeloXSymbol.MusicNote, Modifier.size(30.dp), accent) },
                chevronTint = accent,
                onClick = onOpenServices,
                showTopSeparator = true,
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(section: SettingsSection, onOpen: (SettingsRoute) -> Unit) {
    val accent = com.lladlam.melox.ui.glass.MeloXSystemColors.Red
    MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
        section.items.forEach { item ->
            MeloXIosListRow(
                title = item.route.title,
                leading = {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        MeloXActionIcon(item.symbol, Modifier.size(22.dp), accent)
                    }
                },
                chevronTint = accent,
                onClick = { onOpen(item.route) },
                showTopSeparator = item != section.items.first(),
            )
        }
    }
}

@Composable
private fun SettingsDetailScreen(route: SettingsRoute, source: MusicSource, session: NeteaseSessionStore, onBack: () -> Unit) {
    val context = LocalContext.current
    MeloXPinnedListPage(
        title = route.title,
        onNavigateBack = onBack,
        bottomPadding = MeloXBottomContentClearance,
    ) {
        item(key = "settings-detail:${route.name}") {
            Column {
                when (route) {
                    SettingsRoute.Playback -> PlaybackSettings(context)
                    SettingsRoute.PlayerAppearance -> PlayerAppearanceSettings(context)
                    SettingsRoute.Lyrics -> LyricsSettings(context)
                    SettingsRoute.SystemPlayback -> SystemPlaybackSettings(context)
                    SettingsRoute.SkylineLyrics -> SkylineLyricsSettings(context)
                    SettingsRoute.FloatingLyrics -> FloatingLyricsSettings(context)
                    SettingsRoute.ContentFeatures -> ContentFeatureSettings(context)
                    SettingsRoute.Recognition -> RecognitionSettings(context)
                    SettingsRoute.Messages -> MessagesSettings(context)
                    SettingsRoute.ListenTogether -> ListenTogetherSettings(context)
                    SettingsRoute.Content -> ContentSettings(context)
                    SettingsRoute.Storage -> StorageSettings(context)
                    SettingsRoute.TabLayout -> TabLayoutSettings(context)
                    SettingsRoute.General -> GeneralSettings(context)
                    SettingsRoute.RemoteConfig -> RemoteConfigSettings()
                    SettingsRoute.About -> AboutSettings(context)
                    SettingsRoute.Legal -> LegalSettings(context)
                    SettingsRoute.Privacy -> PrivacySettings(context)
                    SettingsRoute.Developer -> DeveloperSettings()
                    SettingsRoute.Experimental -> ExperimentalSettings(context, source, session)
                }
            }
        }
    }
}

@Composable
private fun LegalSettings(context: android.content.Context) {
    var selectedDocument by remember { mutableStateOf<MeloXLegalDocument?>(null) }
    var cloudControlEnabled by remember { mutableStateOf(MeloXRemoteConfigConsent.enabled(context)) }
    var showCloudControlConsent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val consentVersion = remember {
        MeloXSettingsPreferences.string(context, "legal_consent_version")
    }

    SettingsGlassGroup {
        Column(Modifier.padding(16.dp)) {
            Text("法律与隐私文件", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "当前文本版本：$MELOX_LEGAL_VERSION",
                modifier = Modifier.padding(top = 7.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Text(
                text = if (consentVersion.isBlank()) {
                    "此安装记录中没有首次启动同意版本；你仍可在此完整查看文件。"
                } else {
                    "已同意版本：$consentVersion"
                },
                modifier = Modifier.padding(top = 3.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
        MeloXIosListRow(
            title = "隐私政策",
            leading = {
                MeloXSymbolIcon(
                    MeloXSymbol.Info,
                    Modifier.size(24.dp),
                    MeloXSystemColors.Blue,
                )
            },
            onClick = { selectedDocument = MeloXLegalDocument.PrivacyPolicy },
            showTopSeparator = false,
        )
        MeloXIosListRow(
            title = "免责声明与使用须知",
            leading = {
                MeloXSymbolIcon(
                    MeloXSymbol.Book,
                    Modifier.size(24.dp),
                    MeloXSystemColors.Blue,
                )
            },
            onClick = { selectedDocument = MeloXLegalDocument.Disclaimer },
            showTopSeparator = true,
        )
        MeloXIosListRow(
            title = "云控隐私协议",
            leading = {
                MeloXSymbolIcon(
                    MeloXSymbol.Info,
                    Modifier.size(24.dp),
                    MeloXSystemColors.Blue,
                )
            },
            onClick = { selectedDocument = MeloXLegalDocument.CloudControlPrivacy },
            showTopSeparator = true,
        )
        MeloXIosListRow(
            title = "第三方音乐源使用协议",
            leading = {
                MeloXSymbolIcon(
                    MeloXSymbol.Book,
                    Modifier.size(24.dp),
                    MeloXSystemColors.Blue,
                )
            },
            onClick = { selectedDocument = MeloXLegalDocument.ThirdPartyMusicSources },
            showTopSeparator = true,
        )
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        SettingsExternalToggleRow(
            title = "允许远程兼容性配置",
            value = cloudControlEnabled,
            note = "启用后每次应用进入前台检查，并在持续使用期间每两小时检查一次；关闭后停止请求和应用远程配置。",
            grouped = true,
        ) { requested ->
            if (requested) {
                showCloudControlConsent = true
            } else {
                MeloXRemoteConfigConsent.reject(context)
                cloudControlEnabled = false
                scope.launch { MeloXRemoteConfigRuntime.clearCache(context) }
            }
        }
    }
    Text(
        "仅用于控制音乐源及其下属功能。启用后每次应用进入前台检查，并在前台持续运行期间每两小时检查一次。",
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
    )

    selectedDocument?.let { document ->
        MeloXLegalDocumentDialog(
            document = document,
            onDismiss = { selectedDocument = null },
        )
    }
    if (showCloudControlConsent) {
        MeloXCloudControlConsentDialog(
            onReject = { showCloudControlConsent = false },
            onAccept = {
                MeloXRemoteConfigConsent.accept(context)
                MeloXRemoteConfigRuntime.initializeAndRefresh(context, BuildConfig.VERSION_CODE, force = true)
                cloudControlEnabled = true
                showCloudControlConsent = false
            },
        )
    }
}

@Composable
private fun PrivacySettings(context: android.content.Context) {
    var algorithmEnabled by remember { mutableStateOf(LocalRecommendationStore.isAlgorithmEnabled(context)) }
    var consent by remember { mutableStateOf(LocalRecommendationStore.hasPersonalizationConsent(context)) }
    var showPolicy by remember { mutableStateOf(false) }
    var showClearPersonalization by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(10) }
    val progress by LocalRecommendationStore.progress

    LaunchedEffect(showPolicy) {
        if (!showPolicy) return@LaunchedEffect
        secondsLeft = 10
        while (secondsLeft > 0) { kotlinx.coroutines.delay(1_000L); secondsLeft-- }
    }

    SettingsGlassGroup {
        Column(Modifier.padding(16.dp)) {
            Text("本地数据与算法", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text("MeloX 不上传播放记录、收藏、推荐模型或账号凭据。规则推荐无需个性化同意；轻量模型只在你明确同意后读取本地行为数据。", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f), fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        SettingsExternalToggleRow("本地算法模式", algorithmEnabled, "开启后在后台并行运行规则推荐与本地轻量模型。") {
            algorithmEnabled = it
            LocalRecommendationStore.setAlgorithmEnabled(context, it)
            if (it && consent) LocalRecommendationEngine.start(context) else if (!it) LocalRecommendationEngine.stop()
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        Text("本地个性化推荐", modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(if (consent) "已同意，本地轻量模型可以读取本地播放行为。" else "未同意。请阅读隐私协议后开启。", modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
        SettingsActionButton(if (consent) "撤回同意并删除本地模型" else "阅读隐私协议并开启") {
            if (consent) {
                consent = false
                LocalRecommendationStore.clearPersonalization(context)
                LocalRecommendationEngine.stop()
            } else showPolicy = true
        }
        SettingsActionButton("清空全部个性化推荐数据") { showClearPersonalization = true }
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        Column(Modifier.padding(16.dp)) {
            Text("算法状态", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (progress.isFullAnalysis) "后台全量分析：${progress.stage} · ${progress.processed}/${progress.total}"
                else "快速更新：${progress.stage}",
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
            )
            Text("模型后端：${progress.modelBackend}", modifier = Modifier.padding(top = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
            progress.message?.let { Text(it, modifier = Modifier.padding(top = 5.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f)) }
        }
    }
    SettingsActionButton("立即开始后台全量分析") {
        if (algorithmEnabled && consent) LocalRecommendationEngine.startFullAnalysis(context)
    }
    if (showPolicy) {
        MeloXGlassDialog(visible = true, onDismiss = { if (secondsLeft == 0) showPolicy = false }) {
            Text("本地个性化推荐隐私协议", style = MaterialTheme.typography.titleMedium)
            Text("开启后，MeloX 将在本机读取播放次数、完成度、跳过记录、喜欢状态和来源偏好，用于训练与运行轻量推荐模型。数据不上传服务器，不读取密码、Cookie、私信或麦克风原始音频。你可以随时撤回同意并删除模型数据。", modifier = Modifier.padding(top = 10.dp), fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(16.dp))
            SettingsActionButton(if (secondsLeft > 0) "请阅读后等待 ${secondsLeft} 秒" else "同意并开启") {
                if (secondsLeft == 0) {
                    consent = true; showPolicy = false
                    LocalRecommendationStore.setPersonalizationConsent(context, true)
                    LocalRecommendationStore.setConsentAt(context, System.currentTimeMillis())
                    if (algorithmEnabled) LocalRecommendationEngine.start(context)
                }
            }
        }
    }
    if (showClearPersonalization) {
        MeloXGlassDialog(visible = true, onDismiss = { showClearPersonalization = false }) {
            Text("清空个性化推荐数据？", style = MaterialTheme.typography.titleMedium)
            Text("将销毁本地模型、歌曲特征、推荐索引、分析进度和隐私同意状态。平台登录、原始播放历史和收藏不会被删除。", modifier = Modifier.padding(top = 10.dp), fontSize = 13.sp, lineHeight = 19.sp)
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionButton("取消", Modifier.weight(1f)) { showClearPersonalization = false }
                SettingsDangerButton("清空", Modifier.weight(1f)) {
                    LocalRecommendationEngine.stop()
                    LocalRecommendationStore.clearPersonalization(context)
                    consent = false
                    showClearPersonalization = false
                }
            }
        }
    }
}

@Composable
private fun SystemPlaybackSettings(context: android.content.Context) {
    var systemLyrics by remember { mutableStateOf(MeloXSettingsRuntime.systemLyricsEnabled) }
    var notifications by remember { mutableStateOf(MeloXSettingsRuntime.lyricNotificationsEnabled) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifications = granted
        MeloXSettingsPreferences.setBoolean(context, "lyrics_notifications_enabled", granted)
    }

    SettingsGlassGroup {
        SettingsExternalToggleRow(
            title = "系统媒体信息显示歌词",
            value = systemLyrics,
            note = "播放时把当前歌词同步到 Media3 元数据；应用内仍显示原歌曲名和歌手。",
            grouped = true,
        ) {
            systemLyrics = it
            MeloXSettingsPreferences.setBoolean(context, "system_lyrics_enabled", it)
        }
        val protocol = remember { HyperOsFocusBridge.protocol(context) }
        MeloXSettingsDropdown(
            title = "系统媒体标题格式",
            selected = MeloXSettingsRuntime.systemLyricTitleMode,
            items = listOf(
                MeloXSystemLyricTitleMode.LyricFirst to "歌词作为标题",
                MeloXSystemLyricTitleMode.SongFirst to "歌曲作为标题",
            ),
            onSelected = { MeloXSettingsPreferences.setString(context, "system_lyrics_title_mode", it.name) },
            grouped = true,
        )
        SettingsExternalToggleRow(
            title = "独立歌词通知",
            value = notifications,
            note = "在通知栏和锁屏持续更新当前歌词；Android 13 及以上需要通知权限。",
            grouped = true,
        ) { enabled ->
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                notifications = enabled
                MeloXSettingsPreferences.setBoolean(context, "lyrics_notifications_enabled", enabled)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        SettingsToggleRow(context, "通知显示下一句", "lyrics_notification_next_line", false, grouped = true)
        SettingsToggleRow(context, "通知显示播放进度", "lyrics_notification_progress", true, grouped = true)
        SettingsToggleRow(context, "通知显示封面", "lyrics_notification_artwork", true, grouped = true)
        SettingsToggleRow(context, "仅在后台显示歌词通知", "lyrics_notification_background_only", false, grouped = true)
        SettingsToggleRow(context, "暂停时撤回歌词通知", "lyrics_notification_dismiss_paused", true, grouped = true)
    }
    NotificationTemplateField(context, "标题模板", "lyrics_notification_title_template", "{lyric}")
    NotificationTemplateField(context, "副标题模板", "lyrics_notification_subtitle_template", "{song} · {artist}")
    NotificationTemplateField(context, "无歌词回退", "lyrics_notification_fallback", "{song} · {artist}")
    Text(
        "可用变量：{lyric}、{song}、{artist}、{album}",
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f),
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 10.dp),
    )
    SettingsActionButton("发送测试歌词通知") {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("melox_lyrics", "歌词", NotificationManager.IMPORTANCE_LOW))
        manager.notify(
            10_043,
            NotificationCompat.Builder(context, "melox_lyrics")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("这是测试歌词")
                .setContentText("MeloX · 通知模板预览")
                .setStyle(NotificationCompat.BigTextStyle().bigText("这是测试歌词\n下一句歌词预览"))
                .setSilent(true)
                .build(),
        )
    }
}

@Composable
private fun ExperimentalSettings(context: android.content.Context, source: MusicSource, session: NeteaseSessionStore) {
    var playbackEnabled by remember { mutableStateOf(PlaybackAccountStore.isEnabled(context)) }
    var showNeteaseLogin by remember { mutableStateOf(false) }
    var showQQLogin by remember { mutableStateOf(false) }
    var showKugouLogin by remember { mutableStateOf(false) }
    var showClearPlaybackConfirmation by remember { mutableStateOf(false) }
    var showClearLyricBindingsConfirmation by remember { mutableStateOf(false) }
    var showClearBilibiliAssociationsConfirmation by remember { mutableStateOf(false) }
    var bilibiliLyricAlignment by remember(source) {
        mutableStateOf(MeloXSettingsPreferences.boolean(context, "bilibili_lyric_audio_alignment", false))
    }
    var refreshRevision by remember { mutableIntStateOf(0) }
    var persistentAnalysis by remember { mutableStateOf(MeloXAudioAnalysisPreferences.persistentEnabled(context)) }
    var independentAnalysis by remember { mutableStateOf(MeloXAudioAnalysisPreferences.independentLineEnabled(context)) }
    var showPersistentAnalysisConfirmation by remember { mutableStateOf(false) }
    var showIndependentAnalysisConfirmation by remember { mutableStateOf(false) }
    var showAnalysisPlaylistPicker by remember { mutableStateOf(false) }
    var analysisPlaylists by remember(source) { mutableStateOf<List<MusicPlaylistSummary>>(emptyList()) }
    var analysisPlaylistsLoading by remember(source) { mutableStateOf(false) }
    val analysisProgress by MeloXAudioAnalysisRuntime.progress.collectAsState()

    LaunchedEffect(showAnalysisPlaylistPicker, source, session.cookie, session.profile?.userId) {
        if (!showAnalysisPlaylistPicker) return@LaunchedEffect
        analysisPlaylistsLoading = true
        analysisPlaylists = runCatching {
            if (source == MusicSource.Netease) {
                val userId = session.profile?.userId ?: 0L
                if (userId <= 0L) emptyList() else {
                    NeteaseLibraryClient(
                        cookieProvider = { session.cookie },
                    ).snapshot(userId).playlists.map { playlist ->
                        MusicPlaylistSummary(
                            id = MusicResourceId(MusicSource.Netease, playlist.id.toString()),
                            title = playlist.name,
                            artworkUrl = playlist.coverUrl,
                            creatorName = playlist.creatorName,
                            description = playlist.description,
                            trackCount = playlist.trackCount,
                            playCount = playlist.playCount,
                        )
                    }
                }
            } else {
                val provider = MeloXMusicProviders.create(context.applicationContext).require(source)
                (provider as? UserLibraryCapability)?.userPlaylists(page = 1, pageSize = 100)?.items.orEmpty()
            }
        }.getOrDefault(emptyList())
        analysisPlaylistsLoading = false
    }

    SettingsGlassGroup {
        SettingsExternalToggleRow(
            title = "持久化音频分析缓存",
            value = persistentAnalysis,
            note = "保存 BPM、节拍、能量和边界信息，不保留分析用音频。",
            grouped = true,
        ) { enabled ->
            if (enabled) showPersistentAnalysisConfirmation = true else {
                persistentAnalysis = false
                MeloXAudioAnalysisPreferences.setPersistentEnabled(context, false)
            }
        }
    }
    if (persistentAnalysis) {
        Spacer(Modifier.height(10.dp))
        if (analysisProgress.total > 0) {
            val remaining = (analysisProgress.total - analysisProgress.completed).coerceAtLeast(0)
            SettingsGlassGroup {
                Text(
                    if (analysisProgress.running) {
                        "正在分析：已完成 ${analysisProgress.completed} 首，还剩 $remaining 首"
                    } else {
                        "分析完成：${analysisProgress.completed} 首，失败 ${analysisProgress.failed} 首"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    progress = {
                        if (analysisProgress.total == 0) 0f
                        else analysisProgress.completed.toFloat() / analysisProgress.total
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        SettingsActionButton("现在分析音频信息") { showAnalysisPlaylistPicker = true }
        Spacer(Modifier.height(10.dp))
        SettingsGlassGroup {
            SettingsExternalToggleRow(
                title = "使用独立线路分析音频",
                value = independentAnalysis,
                note = "新歌曲分析时优先获取标准音质，分析完成后删除临时音频。",
                grouped = true,
            ) { enabled ->
                if (enabled) showIndependentAnalysisConfirmation = true else {
                    independentAnalysis = false
                    MeloXAudioAnalysisPreferences.setIndependentLineEnabled(context, false)
                }
            }
        }
    }
    if (showPersistentAnalysisConfirmation) {
        MeloXGlassDialog(visible = true, onDismiss = { showPersistentAnalysisConfirmation = false }) {
            Text("打开后音频信息会缓存到本地，有助于更好的智能过渡，但是可能会占用部分空间，是否开启？")
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionButton("取消", Modifier.weight(1f)) { showPersistentAnalysisConfirmation = false }
                SettingsActionButton("同意", Modifier.weight(1f)) {
                    persistentAnalysis = true
                    MeloXAudioAnalysisPreferences.setPersistentEnabled(context, true)
                    showPersistentAnalysisConfirmation = false
                }
            }
        }
    }
    if (showIndependentAnalysisConfirmation) {
        MeloXGlassDialog(visible = true, onDismiss = { showIndependentAnalysisConfirmation = false }) {
            Text("打开此功能后，新音频分析更快，但会消耗少量流量，是否打开？")
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionButton("取消", Modifier.weight(1f)) { showIndependentAnalysisConfirmation = false }
                SettingsActionButton("同意", Modifier.weight(1f)) {
                    independentAnalysis = true
                    MeloXAudioAnalysisPreferences.setIndependentLineEnabled(context, true)
                    showIndependentAnalysisConfirmation = false
                }
            }
        }
    }
    if (showAnalysisPlaylistPicker) {
        MeloXGlassDialog(visible = true, onDismiss = { showAnalysisPlaylistPicker = false }) {
            Text("选择要分析的${source.displayName}歌单", style = MaterialTheme.typography.titleLarge)
            if (analysisProgress.total > 0) {
                val remaining = (analysisProgress.total - analysisProgress.completed).coerceAtLeast(0)
                Text(
                    if (analysisProgress.running) {
                        "已分析 ${analysisProgress.completed} 首，还剩 $remaining 首"
                    } else {
                        "分析完成：${analysisProgress.completed} 首，失败 ${analysisProgress.failed} 首"
                    },
                    Modifier.padding(top = 10.dp),
                )
            }
            if (analysisPlaylistsLoading) {
                CircularProgressIndicator(Modifier.padding(24.dp))
            } else if (analysisPlaylists.isEmpty()) {
                Text("当前音乐源没有可用歌单。", Modifier.padding(top = 12.dp))
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                    items(analysisPlaylists, key = { it.id.value }) { playlist ->
                        MeloXIosListRow(
                            title = playlist.title,
                            subtitle = "${playlist.trackCount ?: 0} 首歌曲",
                            onClick = {
                                context.startService(
                                    Intent(context, MeloXPlaybackService::class.java)
                                        .setAction(MeloXPlaybackService.ACTION_ANALYZE_PLAYLIST)
                                        .putExtra(MeloXPlaybackService.EXTRA_ANALYSIS_SOURCE, source.storageValue)
                                        .putExtra(MeloXPlaybackService.EXTRA_ANALYSIS_PLAYLIST_ID, playlist.id.value),
                                )
                                showAnalysisPlaylistPicker = false
                            },
                        )
                    }
                }
            }
        }
    }

    if (source == MusicSource.Bilibili) {
        SettingsGlassGroup {
            SettingsExternalToggleRow(
                title = "bilibili音源自动与歌词对齐",
                value = bilibiliLyricAlignment,
                grouped = true,
            ) { enabled ->
                bilibiliLyricAlignment = enabled
                MeloXSettingsPreferences.setBoolean(context, "bilibili_lyric_audio_alignment", enabled)
            }
        }
        SettingsInfoCard("开启开关可能会导致与实际音频不一致")
        Spacer(Modifier.height(10.dp))
        SettingsActionButton("清除 Bilibili 音频关联") { showClearBilibiliAssociationsConfirmation = true }
        Spacer(Modifier.height(10.dp))
    }

    if (showClearBilibiliAssociationsConfirmation) {
        MeloXGlassDialog(visible = true, onDismiss = { showClearBilibiliAssociationsConfirmation = false }) {
            Text("清除 Bilibili 音频关联？", style = MaterialTheme.typography.titleLarge)
            Text("已保存的原视频与替代音频对应关系会被删除。", Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionButton("取消", Modifier.weight(1f)) { showClearBilibiliAssociationsConfirmation = false }
                SettingsActionButton("清除", Modifier.weight(1f)) {
                    BilibiliPlaybackAssociationStore.clear(context)
                    showClearBilibiliAssociationsConfirmation = false
                }
            }
        }
    }

    if (showNeteaseLogin) {
        val session = remember { NeteaseSessionStore(context) }
        Dialog(
            onDismissRequest = { showNeteaseLogin = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            NeteaseLoginScreen(
                session = session,
                onDismiss = { showNeteaseLogin = false },
                onLoggedIn = { showNeteaseLogin = false; refreshRevision++ },
                targetSlot = PlaybackAccountSlot.Playback,
            )
        }
    }
    if (showQQLogin) {
        Dialog(
            onDismissRequest = { showQQLogin = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            QQMusicLoginScreen(
                onDismiss = { showQQLogin = false },
                onLoggedIn = { showQQLogin = false; refreshRevision++ },
                targetSlot = PlaybackAccountSlot.Playback,
            )
        }
    }
    if (showKugouLogin) {
        Dialog(
            onDismissRequest = { showKugouLogin = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            KugouLoginScreen(
                onDismiss = { showKugouLogin = false },
                onLoggedIn = { showKugouLogin = false; refreshRevision++ },
                targetSlot = PlaybackAccountSlot.Playback,
            )
        }
    }

    SettingsGlassGroup {
        SettingsExternalToggleRow(
            title = "使用第二者账号获取音频",
            value = playbackEnabled,
            grouped = true,
        ) { enabled ->
            playbackEnabled = enabled
            PlaybackAccountStore.setEnabled(context, enabled)
            if (!enabled) ProviderPlaybackQualityRuntime.clear()
        }
    }
    if (playbackEnabled) {
        Spacer(Modifier.height(10.dp))
        SettingsGlassGroup {
            SettingsInfoCard("以下账号仅用于播放音频 URL 和可用音质探测，不影响搜索、歌词、收藏、资料库、歌单、社交或下载。")
        }
        Spacer(Modifier.height(10.dp))

        val neteaseCookie = remember(refreshRevision) { NeteaseSessionStore.readPlaybackCookie(context) }
        val qqLoggedIn = remember(refreshRevision) { QQMusicSessionStore.read(context, playback = true).isLoggedIn }
        val kugouLoggedIn = remember(refreshRevision) { KugouSessionStore.read(context, playback = true).isLoggedIn }

        SettingsGlassGroup {
            MeloXIosListRow(
                title = "网易云音乐",
                subtitle = if (NeteaseSessionStore.containsMusicU(neteaseCookie)) "已登录" else "未登录",
                leading = { MeloXSymbolIcon(MeloXSymbol.MusicNote, Modifier.size(24.dp), MaterialTheme.colorScheme.primary) },
                onClick = { showNeteaseLogin = true },
                showTopSeparator = false,
            )
            MeloXIosListRow(
                title = "QQ音乐",
                subtitle = if (qqLoggedIn) "已登录" else "未登录",
                leading = { MeloXSymbolIcon(MeloXSymbol.MusicNote, Modifier.size(24.dp), MaterialTheme.colorScheme.primary) },
                onClick = { showQQLogin = true },
            )
            MeloXIosListRow(
                title = "酷狗音乐",
                subtitle = if (kugouLoggedIn) "已登录" else "未登录",
                leading = { MeloXSymbolIcon(MeloXSymbol.MusicNote, Modifier.size(24.dp), MaterialTheme.colorScheme.primary) },
                onClick = { showKugouLogin = true },
            )
            MeloXIosListRow(
                title = "Apple Music",
                subtitle = "当前版本暂不支持第二账号；全曲播放由 Apple 官方 DRM 管理",
                leading = { MeloXSymbolIcon(MeloXSymbol.MusicNote, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) },
                onClick = null,
            )
        }
        Spacer(Modifier.height(10.dp))
        SettingsDangerButton("清除所有第二者账号") { showClearPlaybackConfirmation = true }
        MeloXGlassDialog(
            visible = showClearPlaybackConfirmation,
            onDismiss = { showClearPlaybackConfirmation = false },
        ) {
            Text("是否清除所有第二者账号？", style = MaterialTheme.typography.titleMedium)
            Text(
                "此操作只清除用于获取音频的第二账号，不影响主账号登录状态。",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsActionButton("取消", Modifier.weight(1f)) { showClearPlaybackConfirmation = false }
                SettingsActionButton("清除", Modifier.weight(1f)) {
                    PlaybackAccountStore.clear(context)
                    ProviderPlaybackQualityRuntime.clear()
                    refreshRevision++
                    showClearPlaybackConfirmation = false
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    if (!MeloXSettingsRuntime.automaticLyricSelectionEnabled) {
        SettingsInfoCard("请先开启自动选择最合适的歌词")
        return
    }
    var enabled by remember {
        mutableStateOf(MeloXSettingsPreferences.boolean(context, "experimental_lyric_strong_binding", false))
    }
    SettingsGlassGroup {
        SettingsExternalToggleRow(
            title = "歌曲与自动选择的歌词强绑定",
            value = enabled,
            grouped = true,
        ) { value ->
            enabled = value
            MeloXSettingsPreferences.setBoolean(context, "experimental_lyric_strong_binding", value)
            if (!value) com.lladlam.melox.core.lyrics.LyricBindingStore.clear(context)
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsDangerButton("清除强绑定配置") {
        showClearLyricBindingsConfirmation = true
    }
    MeloXGlassDialog(
        visible = showClearLyricBindingsConfirmation,
        onDismiss = { showClearLyricBindingsConfirmation = false },
    ) {
        Text("清除所有歌词强绑定？", style = MaterialTheme.typography.titleMedium)
        Text(
            "此操作会删除歌曲与歌词来源之间保存的全部绑定。强绑定开关保持不变，之后播放歌曲时会重新自动选择并建立绑定。",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsActionButton("取消", Modifier.weight(1f)) {
                showClearLyricBindingsConfirmation = false
            }
            SettingsDangerButton("清除", Modifier.weight(1f)) {
                com.lladlam.melox.core.lyrics.LyricBindingStore.clear(context)
                showClearLyricBindingsConfirmation = false
            }
        }
    }
}

@Composable
private fun NotificationTemplateField(
    context: android.content.Context,
    title: String,
    key: String,
    default: String,
) {
    var value by remember(key) { mutableStateOf(MeloXSettingsPreferences.string(context, key, default)) }
    Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f), modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
    MeloXGlassTextField(
        value = value,
        onValueChange = {
            value = it.take(80)
            MeloXSettingsPreferences.setString(context, key, value)
        },
        modifier = Modifier.fillMaxWidth(),
        textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SkylineLyricsSettings(context: android.content.Context) {
    var enabled by remember { mutableStateOf(MeloXSettingsRuntime.skylineEnabled) }
    SettingsGlassGroup {
        SettingsExternalToggleRow(
            title = "横屏自动启用天际歌词",
            value = enabled,
            note = "播放器进入横屏时使用封面、主歌词和环境歌词的宽屏布局。",
            grouped = true,
        ) {
            enabled = it
            MeloXSettingsPreferences.setBoolean(context, "lyrics_skyline_enabled", it)
        }
        SettingsToggleRow(context, "显示封面与歌曲信息", "lyrics_skyline_song_info", true, grouped = true)
        SettingsToggleRow(context, "屏幕常亮", "lyrics_skyline_keep_awake", true, "仅在全屏天际歌词可见时阻止自动锁屏。", grouped = true)
        LyricsChoiceSetting(
            context,
            "环境歌词数量",
            "lyrics_skyline_ambient_lines",
            2,
            listOf(0, 1, 2, 3, 4),
            grouped = true,
        ) { if (it == 0) "关闭" else "$it 行" }
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        LyricsChoiceSetting(context, "单组最大字数", "lyrics_skyline_ambient_max_characters", 4, listOf(1, 2, 3, 4), grouped = true) { "$it 个字" }
        LyricsChoiceSetting(context, "同屏文字上限", "lyrics_skyline_ambient_max_visible", 16, listOf(4, 8, 12, 16, 20, 24), grouped = true) { "$it 组" }
    }
    Spacer(Modifier.height(10.dp))
    var currentFontSize by remember { mutableStateOf(MeloXSettingsRuntime.skylineCurrentFontSize) }
    var currentScale by remember { mutableStateOf(MeloXSettingsRuntime.skylineCurrentMaximumScale) }
    var currentWidth by remember { mutableStateOf(MeloXSettingsRuntime.skylineCurrentWidth) }
    var nextFontSize by remember { mutableStateOf(MeloXSettingsRuntime.skylineNextFontSize) }
    var nextOpacity by remember { mutableStateOf(MeloXSettingsRuntime.skylineNextOpacity) }
    var currentSpacing by remember { mutableStateOf(MeloXSettingsRuntime.skylineCurrentSpacing) }
    var ambientFontSize by remember { mutableStateOf(MeloXSettingsRuntime.skylineAmbientFontSize) }
    var ambientOpacity by remember { mutableStateOf(MeloXSettingsRuntime.skylineAmbientOpacity) }
    var ambientBlur by remember { mutableStateOf(MeloXSettingsRuntime.skylineAmbientBlur) }
    var ambientTilt by remember { mutableStateOf(MeloXSettingsRuntime.skylineAmbientMaximumTilt) }
    var ambientDrift by remember { mutableStateOf(MeloXSettingsRuntime.skylineAmbientDrift) }
    SettingsFloatSlider("当前歌词字号", currentFontSize, 36f..84f, 47, { "${it.toInt()} sp" }) {
        currentFontSize = it; MeloXSettingsPreferences.setFloat(context, "lyrics_skyline_current_font_size", it)
    }
    SettingsFloatSlider("逐字歌词最大缩放", currentScale, 1f..1.2f, 19, { "%.2f×".format(it) }) {
        currentScale = it; MeloXSettingsPreferences.setFloat(context, "lyrics_skyline_current_max_scale", it)
    }
    SettingsFloatSlider("中央显示宽度", currentWidth, .4f..82f / 100f, 20, { "${(it * 100).toInt()}%" }) {
        currentWidth = it; MeloXSettingsPreferences.setFloat(context, "lyrics_skyline_current_width", it)
    }
    SettingsFloatSlider("下一句字号", nextFontSize, 14f..44f, 29, { "${it.toInt()} sp" }) {
        nextFontSize = it; MeloXSettingsPreferences.setFloat(context, "lyrics_skyline_next_font_size", it)
    }
    SettingsFloatSlider("下一句亮度", nextOpacity, .2f..8f / 10f, 11, { "${(it * 100).toInt()}%" }) {
        nextOpacity = it; MeloXSettingsPreferences.setFloat(context, "lyrics_skyline_next_opacity", it)
    }
    SettingsFloatSlider("中央歌词间距", currentSpacing, 4f..36f, 31, { "${it.toInt()} dp" }) {
        currentSpacing = it; MeloXSettingsPreferences.setFloat(context, "lyrics_skyline_current_spacing", it)
    }
    SettingsFloatSlider("背景字号", ambientFontSize, 24f..72f, 47, { "${it.toInt()} sp" }) {
        ambientFontSize = it; MeloXSettingsPreferences.setFloat(context, "lyrics_skyline_ambient_font_size", it)
    }
    SettingsFloatSlider("背景字亮度", ambientOpacity, .4f..1.8f, 13, { "%.1f×".format(it) }) {
        ambientOpacity = it; MeloXSettingsPreferences.setFloat(context, "lyrics_skyline_ambient_opacity", it)
    }
    SettingsFloatSlider("背景字模糊", ambientBlur, 0f..2f, 19, { "%.1f×".format(it) }) {
        ambientBlur = it; MeloXSettingsPreferences.setFloat(context, "lyrics_skyline_ambient_blur", it)
    }
    SettingsFloatSlider("最大倾斜角度", ambientTilt, 0f..20f, 19, { "${it.toInt()}°" }) {
        ambientTilt = it; MeloXSettingsPreferences.setFloat(context, "lyrics_skyline_ambient_max_tilt", it)
    }
    SettingsFloatSlider("漂移幅度", ambientDrift, 0f..2f, 19, { "%.1f×".format(it) }) {
        ambientDrift = it; MeloXSettingsPreferences.setFloat(context, "lyrics_skyline_ambient_drift", it)
    }
    SettingsActionButton("恢复全屏天际歌词默认设置") {
        listOf(
            "lyrics_skyline_current_font_size" to 54f, "lyrics_skyline_current_max_scale" to 1.1f,
            "lyrics_skyline_next_font_size" to 24f, "lyrics_skyline_current_spacing" to 14f,
            "lyrics_skyline_current_width" to .64f, "lyrics_skyline_next_opacity" to .48f,
            "lyrics_skyline_ambient_font_size" to 44f, "lyrics_skyline_ambient_opacity" to 1f,
            "lyrics_skyline_ambient_blur" to 1f, "lyrics_skyline_ambient_max_tilt" to 8f,
            "lyrics_skyline_ambient_drift" to 1f,
        ).forEach { (key, value) -> MeloXSettingsPreferences.setFloat(context, key, value) }
        MeloXSettingsPreferences.setInt(context, "lyrics_skyline_ambient_max_characters", 4)
        MeloXSettingsPreferences.setInt(context, "lyrics_skyline_ambient_max_visible", 16)
        currentFontSize = 54f; currentScale = 1.1f; nextFontSize = 24f; currentSpacing = 14f
        currentWidth = .64f; nextOpacity = .48f; ambientFontSize = 44f; ambientOpacity = 1f
        ambientBlur = 1f; ambientTilt = 8f; ambientDrift = 1f
    }
}

@Composable
private fun FloatingLyricsSettings(context: android.content.Context) {
    var enabled by remember { mutableStateOf(MeloXSettingsRuntime.floatingLyricsEnabled) }
    var permissionGranted by remember { mutableStateOf(AndroidSettings.canDrawOverlays(context)) }

    fun startFloatingLyrics() {
        MeloXSettingsPreferences.setBoolean(context, "floating_lyrics_enabled", true)
        enabled = true
        ContextCompat.startForegroundService(context, Intent(context, MeloXFloatingLyricsService::class.java))
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        permissionGranted = AndroidSettings.canDrawOverlays(context)
        if (permissionGranted) startFloatingLyrics()
    }

    SettingsGlassGroup {
        SettingsExternalToggleRow(
            title = "显示悬浮歌词",
            value = enabled,
            note = "在其他应用上方显示当前歌词和翻译/罗马音，可直接拖动位置。",
            grouped = true,
        ) { shouldEnable ->
            if (!shouldEnable) {
                enabled = false
                MeloXSettingsPreferences.setBoolean(context, "floating_lyrics_enabled", false)
                context.stopService(Intent(context, MeloXFloatingLyricsService::class.java))
            } else if (AndroidSettings.canDrawOverlays(context)) {
                permissionGranted = true
                startFloatingLyrics()
            } else {
                overlayPermissionLauncher.launch(
                    Intent(
                        AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
        }
        MeloXSettingsDropdown(
            title = "副歌词内容",
            selected = MeloXSettingsRuntime.floatingSecondaryMode,
            items = listOf(
                MeloXSecondaryLyricMode.Auto to "自动（翻译/罗马音/下一句）",
                MeloXSecondaryLyricMode.Translation to "翻译",
                MeloXSecondaryLyricMode.Romanization to "罗马音",
                MeloXSecondaryLyricMode.NextLine to "下一句",
                MeloXSecondaryLyricMode.Hidden to "不显示",
            ),
            onSelected = { MeloXSettingsPreferences.setString(context, "floating_lyrics_secondary_mode", it.name) },
            grouped = true,
        )
        LyricsChoiceSetting(
            context,
            "主歌词字号",
            "floating_lyrics_font_size",
            18,
            listOf(14, 16, 18, 20, 24, 28),
            grouped = true,
        ) { "$it sp" }
        SettingsToggleRow(context, "高对比背景", "floating_lyrics_high_contrast", true, "重新开启悬浮歌词后生效。", grouped = true)
    }
}

@Composable
private fun PlaybackSettings(context: android.content.Context) {
    var quality by remember { mutableStateOf(MusicQualityPreferences.read(context)) }
    var volumeMode by remember { mutableStateOf(MeloXSettingsRuntime.volumeControlMode) }
    SettingsGlassGroup {
        MeloXSettingsDropdown(
            title = "播放音质",
            selected = quality,
            items = MusicQuality.entries.map { it to it.title },
            onSelected = { quality = it; PlaybackCommands.changeQuality(context, it) },
            grouped = true,
        )
        MeloXSettingsDropdown(
            title = "音量滑杆控制",
            selected = volumeMode,
            items = listOf(
                MeloXVolumeControlMode.System to "系统媒体音量",
                MeloXVolumeControlMode.Player to "播放器独立音量",
            ),
            onSelected = { volumeMode = it; MeloXSettingsPreferences.setString(context, "playback_volume_mode", it.name) },
            grouped = true,
        )
        SettingsToggleRow(
            context,
            "允许与其他应用同时播放",
            "playback_allow_other_apps",
            false,
            "开启后 MeloX 不会独占音频焦点，其他应用可以继续播放。",
            grouped = true,
        )
        SettingsToggleRow(context, "记住播放器上次页面", "playback_remember_page", true, grouped = true)
        SettingsToggleRow(
            context,
            "记录上次播放到哪个音乐",
            "playback_remember_last_song",
            true,
            "下次进入软件时显示上次播放的歌曲，但不会自动播放。",
            grouped = true,
        )
        SettingsToggleRow(context, "登录后以心动模式开始播放", "playback_heart_mode_on_launch", false, grouped = true)
        SettingsToggleRow(context, "播放超过 5 秒时上一首先回到开头", "playback_previous_restarts", true, grouped = true)
        LyricsChoiceSetting(
            context,
            "播放器展开/收回时长",
            "player_transition_duration_ms",
             360,
             listOf(240, 300, 360, 460, 600),
            grouped = true,
        ) { "${it}ms" }
    }
    Spacer(Modifier.height(10.dp))
    EqualizerSettings(context)
    Spacer(Modifier.height(10.dp))
    AutoMixSettings(context)
}

@Composable
private fun EqualizerSettings(context: android.content.Context) {
    var enabled by remember { mutableStateOf(MeloXSettingsPreferences.boolean(context, "equalizer_enabled", false)) }
    var preset by remember { mutableStateOf(MeloXSettingsPreferences.string(context, "equalizer_preset", "Flat")) }
    var preamp by remember { mutableStateOf(MeloXSettingsPreferences.number(context, "equalizer_preamp_db", 0f)) }
    SettingsExternalToggleRow("均衡器", enabled, "使用 Android 原生多频段 DSP，直接作用于当前播放器音频会话。") {
        enabled = it
        MeloXSettingsPreferences.setBoolean(context, "equalizer_enabled", it)
    }
    if (!enabled) return
    Text("预设", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        MeloXEqualizerController.PRESETS.keys.forEach { value ->
            val label = mapOf(
                "Flat" to "平直", "Bass" to "低频增强", "Vocal" to "人声", "Treble" to "高频增强",
                "Electronic" to "电子", "Rock" to "摇滚", "Classical" to "古典", "Custom" to "自定义",
            ).getValue(value)
            SettingsChoiceRow(label, preset == value) {
                preset = value
                MeloXSettingsPreferences.setString(context, "equalizer_preset", value)
            }
        }
    }
    if (preset == "Custom") {
        Spacer(Modifier.height(12.dp))
        listOf("31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz")
            .forEachIndexed { index, label ->
                var gain by remember(index) {
                    mutableStateOf(MeloXSettingsPreferences.number(context, "equalizer_custom_band_$index", 0f))
                }
                SettingsFloatSlider(label, gain, -12f..12f, 47, { value ->
                    val rounded = kotlin.math.round(value * 2f) / 2f
                    if (rounded > 0f) "+%.1f dB".format(rounded) else "%.1f dB".format(rounded)
                }) { value ->
                    gain = kotlin.math.round(value * 2f) / 2f
                    MeloXSettingsPreferences.setFloat(context, "equalizer_custom_band_$index", gain)
                }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("前级", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsFloatSlider("输入增益", preamp, -12f..12f, 47, { value ->
        val rounded = kotlin.math.round(value * 2f) / 2f
        if (rounded > 0f) "+%.1f dB".format(rounded) else "%.1f dB".format(rounded)
    }) { value ->
        preamp = kotlin.math.round(value * 2f) / 2f
        MeloXSettingsPreferences.setFloat(context, "equalizer_preamp_db", preamp)
    }
}

@Composable
private fun AutoMixSettings(context: android.content.Context) {
    var settings by remember { mutableStateOf(MeloXAutoMixSettings.read(context)) }
    fun refresh() { settings = MeloXAutoMixSettings.read(context) }

    val legacyAutoMix = remember { MeloXSettingsPreferences.boolean(context, "playback_auto_mix", false) }
    var autoMixEnabled by remember {
        mutableStateOf(MeloXPlaybackModePreferences.autoMix(context) || legacyAutoMix)
    }
    LaunchedEffect(legacyAutoMix) {
        if (legacyAutoMix) {
            MeloXPlaybackModePreferences.setAutoMix(context, true)
            MeloXSettingsPreferences.setBoolean(context, "playback_auto_mix", false)
        }
    }
    SettingsGlassGroup {
        SettingsExternalToggleRow(
            title = "自动混音",
            value = autoMixEnabled,
            note = "双播放器预载，20ms 包络更新；分析不可用时按下方策略平滑降级。",
            grouped = true,
        ) {
            autoMixEnabled = it
            MeloXPlaybackModePreferences.setAutoMix(context, it)
        }
        MeloXSettingsDropdown(
            title = "混音模式",
            selected = settings.mode,
            items = listOf(MeloXAutoMixMode.Smart to "智能", MeloXAutoMixMode.Fixed to "固定时长"),
            onSelected = { MeloXPlaybackModePreferences.setAutoMixString(context, "automix_mode", it.name); refresh() },
            grouped = true,
        )
        if (settings.mode == MeloXAutoMixMode.Smart) {
            SettingsExternalToggleRow("跳过安静开头", settings.skipQuietOpening, "从下一首的首个可听乐句开始交接。", grouped = true) {
                MeloXPlaybackModePreferences.setAutoMixBoolean(context, "automix_skip_quiet_opening", it)
                refresh()
            }
            SettingsExternalToggleRow("分析网络歌曲", settings.analyzeStreaming, "提前解码网络音频以生成节拍和频谱时间轴。", grouped = true) {
                MeloXPlaybackModePreferences.setAutoMixBoolean(context, "automix_analyze_streaming", it)
                refresh()
            }
            MeloXSettingsDropdown(
                title = "智能过渡长度",
                selected = settings.transitionBars,
                items = listOf(4, 8, 16).map { it to "$it 小节" },
                onSelected = { MeloXPlaybackModePreferences.setAutoMixInt(context, "automix_transition_bars", it); refresh() },
                grouped = true,
            )
            MeloXSettingsDropdown(
                title = "上一首结束位置",
                selected = settings.tailCutBars,
                items = listOf(0 to "保留至结尾", 2 to "提前 2 小节", 4 to "提前 4 小节", 8 to "提前 8 小节"),
                onSelected = { MeloXPlaybackModePreferences.setAutoMixInt(context, "automix_tail_cut_bars", it); refresh() },
                grouped = true,
            )
            val confidenceOptions = listOf(.30f, .42f, .55f, .70f)
            MeloXSettingsDropdown(
                title = "最低分析置信度",
                selected = confidenceOptions.minByOrNull { kotlin.math.abs(settings.minimumConfidence - it) } ?: .42f,
                items = confidenceOptions.map { it to "${(it * 100).toInt()}%" },
                onSelected = { MeloXPlaybackModePreferences.setAutoMixFloat(context, "automix_minimum_confidence", it); refresh() },
                grouped = true,
            )
        }
        val durationOptions = listOf(3_000L, 6_000L, 8_000L, 12_000L, 16_000L, 20_000L)
        MeloXSettingsDropdown(
            title = "交叉淡化时长",
            selected = durationOptions.minByOrNull { kotlin.math.abs(settings.fixedDurationMs - it) } ?: 6_000L,
            items = durationOptions.map { it to "${it / 1_000} 秒" },
            onSelected = { MeloXPlaybackModePreferences.setAutoMixLong(context, "automix_fixed_duration_ms", it); refresh() },
            grouped = true,
        )
        val preloadOptions = listOf(30_000L, 60_000L, 90_000L, 120_000L, 180_000L)
        MeloXSettingsDropdown(
            title = "预加载提前量",
            selected = preloadOptions.minByOrNull { kotlin.math.abs(settings.preloadLeadMs - it) } ?: 60_000L,
            items = preloadOptions.map { it to "${it / 1_000} 秒" },
            onSelected = { MeloXPlaybackModePreferences.setAutoMixLong(context, "automix_preload_lead_ms", it); refresh() },
            grouped = true,
        )
        MeloXSettingsDropdown(
            title = "淡化曲线",
            selected = settings.fadeCurve,
            items = listOf(
                MeloXAutoMixFadeCurve.EqualPower to "等功率",
                MeloXAutoMixFadeCurve.Smooth to "平滑",
                MeloXAutoMixFadeCurve.Linear to "线性",
            ),
            onSelected = { MeloXPlaybackModePreferences.setAutoMixString(context, "automix_fade_curve", it.name); refresh() },
            grouped = true,
        )
        MeloXSettingsDropdown(
            title = "分析失败时",
            selected = settings.fallback,
            items = listOf(
                MeloXAutoMixFallback.Crossfade to "使用所选时长",
                MeloXAutoMixFallback.ShortCrossfade to "短淡化（3 秒）",
                MeloXAutoMixFallback.Normal to "正常切歌",
            ),
            onSelected = { MeloXPlaybackModePreferences.setAutoMixString(context, "automix_fallback", it.name); refresh() },
            grouped = true,
        )
        SettingsExternalToggleRow("速度匹配", settings.tempoMatching, "有可靠 BPM 分析时平滑调整两台播放器速度。", grouped = true) {
            MeloXPlaybackModePreferences.setAutoMixBoolean(context, "automix_tempo_matching", it)
            refresh()
        }
        if (settings.tempoMatching) {
            val adjustmentOptions = listOf(.02f, .05f, .08f)
            MeloXSettingsDropdown(
                title = "最大速度调整",
                selected = adjustmentOptions.minByOrNull { kotlin.math.abs(settings.maxTempoAdjustment - it) } ?: .05f,
                items = adjustmentOptions.map { it to "${(it * 100).toInt()}%" },
                onSelected = { MeloXPlaybackModePreferences.setAutoMixFloat(context, "automix_max_tempo_adjustment", it); refresh() },
                grouped = true,
            )
        }
    }
}

@Composable
private fun PlayerAppearanceSettings(context: android.content.Context) {
    LyricsStringChoiceSetting(
        context,
        "播放器外观",
        "player_shell",
        MeloXSettingsRuntime.playerShell.name,
        com.lladlam.melox.ui.settings.MeloXPlayerShell.entries.map { it.name },
    ) {
        when (com.lladlam.melox.ui.settings.MeloXPlayerShell.valueOf(it)) {
            com.lladlam.melox.ui.settings.MeloXPlayerShell.AppleMusic -> "Apple Music"
            com.lladlam.melox.ui.settings.MeloXPlayerShell.Classic -> "经典播放器（手机 / 平板 / 横屏）"
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsToggleRow(
        context,
        "使用毛玻璃",
        "player_frosted_glass",
        false,
        "关闭液态玻璃的折射、色散和交互变形，改用更省性能的普通模糊。",
    )
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        MeloXSettingsDropdown(
            title = "播放器背景",
            selected = MeloXSettingsRuntime.playerBackgroundMode,
            items = listOf(
                MeloXPlayerBackgroundMode.FlowingLight to "取色流动光影（原版）",
                MeloXPlayerBackgroundMode.AppleLyrics to "Apple 三层歌词背景",
                MeloXPlayerBackgroundMode.BlurredArtwork to "静态模糊封面",
            ),
            onSelected = {
                MeloXSettingsPreferences.setString(context, "player_background_mode", it.name)
                MeloXSettingsRuntime.flowingBackdropEnabled = it != MeloXPlayerBackgroundMode.BlurredArtwork
            },
            grouped = true,
        )
        SettingsToggleRow(context, "流动光影背景", "player_flowing_backdrop", true, "关闭后使用模糊封面背景。", grouped = true)
        SettingsToggleRow(context, "播放器背景隔离", "player_background_isolation", true, "开启后播放器独立覆盖首页；关闭后恢复原始透明背景，可能透出下层页面。", grouped = true)
        LyricsChoiceSetting(context, "动态背景帧率", "lyrics_background_frame_rate", 60, listOf(15, 24, 30, 45, 60), grouped = true) { value ->
            when (value) {
                15 -> "15 FPS · 省电"
                24 -> "24 FPS · 省电"
                30 -> "30 FPS · 均衡"
                else -> "$value FPS · 推荐"
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        SettingsToggleRow(context, "减少动态效果", "reduce_motion", false, "减少页面位移、封面缩放、流动背景与弹性效果。", grouped = true)
        SettingsToggleRow(context, "封面播放动效", "player_artwork_motion", true, grouped = true)
        MeloXSettingsDropdown(
            title = "屏幕常亮范围",
            selected = MeloXSettingsRuntime.screenAwakeMode,
            items = listOf(
                MeloXScreenAwakeMode.Disabled to "关闭",
                MeloXScreenAwakeMode.Player to "播放器常亮",
                MeloXScreenAwakeMode.Lyrics to "歌词页常亮",
                MeloXScreenAwakeMode.HiddenLyricsInterface to "歌词页隐藏 UI 后常亮",
            ),
            onSelected = { MeloXSettingsPreferences.setString(context, "player_screen_awake_mode", it.name) },
            grouped = true,
        )
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        SettingsToggleRow(
            context,
            "沉浸式播放",
            "immersive_playback",
            false,
            "播放器全屏时自动隐藏顶部系统状态栏",
            grouped = true,
        )
    }
}

@Composable
private fun LyricsSettings(context: android.content.Context) {
    var lyricsStyle by remember { mutableStateOf(MeloXSettingsRuntime.lyricsStyle) }
    // Group 1: 歌词样式-歌词渲染质量-逐字歌词-普通LRC-点击跳转-长按分享-间奏倒计时-自动跟随-手动滚动恢复-减弱动画
    SettingsGlassGroup {
        MeloXSettingsDropdown(
            title = "歌词样式",
            selected = lyricsStyle,
            items = listOf(
                MeloXLyricsStyle.AppleMusic to "Apple Music",
                MeloXLyricsStyle.Eva to "EVA 动态排版",
                MeloXLyricsStyle.TextPV to "文字 PV",
            ),
            onSelected = { lyricsStyle = it; MeloXSettingsPreferences.setString(context, "lyrics_style", it.name) },
            grouped = true,
        )
        MeloXSettingsDropdown(
            title = "歌词渲染质量",
            selected = MeloXSettingsRuntime.lyricRenderingQuality,
            items = listOf(
                MeloXLyricsRenderingQuality.Low to "低 · 更省电",
                MeloXLyricsRenderingQuality.Balanced to "均衡",
                MeloXLyricsRenderingQuality.High to "高 · 推荐 / 完整 iOS 效果",
            ),
            onSelected = { MeloXSettingsPreferences.setString(context, "lyrics_rendering_quality", it.name) },
            grouped = true,
        )
        SettingsToggleRow(context, "自动选择最合适的歌词", "lyrics_auto_select", true, grouped = true)
        SettingsToggleRow(context, "逐字歌词（YRC）", "lyrics_word_by_word", true, grouped = true)
        SettingsToggleRow(context, "普通 LRC 生成逐字时间", "lyrics_pseudo_timing", true, "按 Unicode 字素分配行时长，不覆盖真实 YRC。", grouped = true)
        SettingsToggleRow(context, "点击歌词跳转进度", "lyrics_tap_seek", true, grouped = true)
        SettingsToggleRow(context, "长按歌词分享", "lyrics_long_press_share", true, grouped = true)
        SettingsToggleRow(context, "间奏倒计时", "lyrics_interlude_countdown", true, "歌词间隔至少 4 秒时显示三点倒计时。", grouped = true)
        SettingsToggleRow(context, "自动跟随当前歌词", "lyrics_auto_follow", true, grouped = true)
        LyricsChoiceSetting(context, "手动滚动后恢复跟随", "lyrics_follow_delay_ms", 3_000, listOf(1_500, 3_000, 5_000, 8_000), grouped = true) { "${it / 1_000f} 秒" }
        SettingsToggleRow(context, "减弱歌词动画", "lyrics_reduce_motion", false, "保留逐字高亮，关闭弹性、抬升与光晕。", grouped = true)
    }
    if (lyricsStyle == MeloXLyricsStyle.TextPV) {
        var pvStyle by remember { mutableStateOf(MeloXSettingsRuntime.textPVStyle) }
        var pvMotionIntensity by remember { mutableStateOf(MeloXSettingsRuntime.textPVMotionIntensity) }
        var pvAnimationSpeed by remember { mutableStateOf(MeloXSettingsRuntime.textPVAnimationSpeed) }
        Spacer(Modifier.height(10.dp))
        SettingsGlassGroup {
            Text("文字 PV 风格", modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
            listOf(
                MeloXTextPVStyle.BlueBold to "蓝色冲击",
                MeloXTextPVStyle.KineticSplit to "斩击",
                MeloXTextPVStyle.BluePlane to "蓝色构成",
                MeloXTextPVStyle.CyberGrunge to "赛博废墟",
                MeloXTextPVStyle.Geometric to "几何",
                MeloXTextPVStyle.RainCity to "黑客帝国",
                MeloXTextPVStyle.CyberpunkHUD to "夜之城监控",
                MeloXTextPVStyle.EmotionCinema to "情绪电影",
                MeloXTextPVStyle.HystericNight to "歇斯底里之夜",
                MeloXTextPVStyle.SpiderWeb to "蛛网",
                MeloXTextPVStyle.StaggeredText to "错落文字",
                MeloXTextPVStyle.CalmVillain to "冷静的反派",
                MeloXTextPVStyle.GirlyClouds to "少女云朵",
                MeloXTextPVStyle.SweetPink to "格子花边",
                MeloXTextPVStyle.FlyMeToTheMoon to "Fly Me to the Moon",
                MeloXTextPVStyle.KawaiiPixel to "Kawaii 像素",
                MeloXTextPVStyle.CrimeScene to "案发现场",
                MeloXTextPVStyle.Haruhikage to "春日影",
            ).forEach { (style, title) ->
                SettingsChoiceRow(title, pvStyle == style) {
                    MeloXSettingsPreferences.setString(context, "lyrics_text_pv_style", style.name)
                    pvStyle = style
                    pvAnimationSpeed = style.referenceAnimationSpeed
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SettingsFloatSlider("动效强度", pvMotionIntensity, 0f..2f, 19) {
            pvMotionIntensity = it; MeloXSettingsPreferences.setFloat(context, "lyrics_text_pv_motion_intensity", it)
        }
        SettingsFloatSlider("动画速度", pvAnimationSpeed, 0f..4f, 39) {
            pvAnimationSpeed = it; MeloXSettingsPreferences.setFloat(context, "lyrics_text_pv_animation_speed", it)
        }
        Spacer(Modifier.height(10.dp))
        SettingsActionButton("恢复文字 PV 默认设置") {
            MeloXSettingsPreferences.setString(context, "lyrics_text_pv_style", MeloXTextPVStyle.BlueBold.name)
            MeloXSettingsPreferences.setFloat(context, "lyrics_text_pv_motion_intensity", 1f)
            MeloXSettingsPreferences.setFloat(context, "lyrics_text_pv_animation_speed", 2f)
            pvStyle = MeloXTextPVStyle.BlueBold; pvMotionIntensity = 1f; pvAnimationSpeed = 2f
        }
    }

    // Group 2: 显示翻译-翻译歌词大小-翻译歌词亮度
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        SettingsToggleRow(context, "显示翻译", "lyrics_translation", true, grouped = true)
        LyricsFloatChoiceSetting(context, "翻译歌词大小", "lyrics_translation_font_scale", .65f, listOf(.5f, .55f, .6f, .65f, .7f, .75f, .8f), grouped = true) { "${(it * 100).toInt()}%" }
        LyricsFloatChoiceSetting(context, "翻译歌词亮度", "lyrics_translation_opacity", .9f, listOf(.4f, .5f, .6f, .7f, .8f, .9f), grouped = true) { "${(it * 100).toInt()}%" }
        LyricsStringChoiceSetting(
            context, "翻译显示范围", "lyrics_translation_display_mode",
            MeloXLyricAnnotationDisplayMode.AllLines.name, MeloXLyricAnnotationDisplayMode.entries.map { it.name }, grouped = true,
        ) { if (it == MeloXLyricAnnotationDisplayMode.FocusedLine.name) "仅当前播放行" else "全部歌词行" }
        SettingsToggleRow(context, "显示罗马音", "lyrics_romanization", false, grouped = true)
        LyricsStringChoiceSetting(
            context, "罗马音显示范围", "lyrics_romanization_display_mode",
            MeloXLyricAnnotationDisplayMode.FocusedLine.name, MeloXLyricAnnotationDisplayMode.entries.map { it.name }, grouped = true,
        ) { if (it == MeloXLyricAnnotationDisplayMode.FocusedLine.name) "仅当前播放行" else "全部歌词行" }
        LyricsFloatChoiceSetting(context, "罗马音大小", "lyrics_romanization_font_scale", .65f, listOf(.5f, .55f, .6f, .65f, .7f, .75f, .8f), grouped = true) { "${(it * 100).toInt()}%" }
        LyricsFloatChoiceSetting(context, "罗马音亮度", "lyrics_romanization_opacity", .9f, listOf(.4f, .5f, .6f, .7f, .8f, .9f), grouped = true) { "${(it * 100).toInt()}%" }
    }

    // Group 3: 歌词提前量-提前量同时应用于逐字高亮-歌词刷新率-歌词字号-歌词字重-抬升方式
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        LyricsChoiceSetting(context, "歌词提前量", "lyrics_advance_ms", 0, listOf(-1_000, -500, -200, 0, 200, 500, 1_000, 2_000, 5_000), grouped = true) { value ->
            if (value == 0) "同步" else if (value > 0) "提前 ${value}ms" else "延后 ${-value}ms"
        }
        SettingsToggleRow(context, "提前量同时应用于逐字高亮", "lyrics_advance_word_by_word", false, grouped = true)
        LyricsChoiceSetting(context, "歌词刷新率", "lyrics_refresh_rate", 60, listOf(30, 60, 90, 120), grouped = true) { "$it FPS" }
        LyricsFloatChoiceSetting(context, "歌词字号", "lyrics_font_scale", 1f, listOf(.85f, 1f, 1.12f, 1.25f), grouped = true) { "${(it * 100).toInt()}%" }
        LyricsStringChoiceSetting(
            context, "歌词字重", "lyrics_font_weight", MeloXLyricsFontWeight.Heavy.name,
            MeloXLyricsFontWeight.entries.map { it.name }, grouped = true,
        ) { value ->
            when (MeloXLyricsFontWeight.valueOf(value)) {
                MeloXLyricsFontWeight.Light -> "细体"
                MeloXLyricsFontWeight.Regular -> "常规"
                MeloXLyricsFontWeight.Medium -> "中等"
                MeloXLyricsFontWeight.SemiBold -> "半粗体"
                MeloXLyricsFontWeight.Bold -> "粗体"
                MeloXLyricsFontWeight.Heavy -> "特粗体"
            }
        }
        LyricsStringChoiceSetting(
            context, "抬升方式", "lyrics_lift_mode", MeloXLyricsGroupingMode.Character.name,
            MeloXLyricsGroupingMode.entries.map { it.name }, grouped = true,
        ) { if (it == MeloXLyricsGroupingMode.Word.name) "按词抬升" else "按字抬升" }
    }

    // Group 4: 长音识别方式-逐字歌词光效-仅长音显示光晕-逐字光晕-长音延展-长音判定时长-行间距-远近模糊
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        LyricsStringChoiceSetting(
            context, "长音识别方式", "lyrics_long_tone_detection", MeloXLyricsGroupingMode.Character.name,
            MeloXLyricsGroupingMode.entries.map { it.name }, grouped = true,
        ) { if (it == MeloXLyricsGroupingMode.Word.name) "按词识别" else "按字识别" }
        SettingsToggleRow(context, "逐字歌词光效", "lyrics_glow_enabled", true, grouped = true)
        SettingsToggleRow(context, "仅长音显示光晕", "lyrics_glow_long_tones_only", true, grouped = true)
        LyricsFloatChoiceSetting(context, "逐字光晕", "lyrics_glow_strength", 1f, listOf(0f, .6f, 1f, 1.4f), grouped = true) { if (it == 0f) "关闭" else "${(it * 100).toInt()}%" }
        LyricsFloatChoiceSetting(context, "长音延展", "lyrics_long_tone_strength", 1f, listOf(0f, .6f, 1f, 1.4f), grouped = true) { if (it == 0f) "关闭" else "${(it * 100).toInt()}%" }
        LyricsChoiceSetting(context, "长音判定时长", "lyrics_long_tone_threshold_ms", 950, listOf(300, 500, 700, 950, 1_200, 1_500), grouped = true) { "${it / 1000f} 秒" }
        LyricsFloatChoiceSetting(context, "行间距", "lyrics_spacing_scale", 1f, listOf(.8f, 1f, 1.2f, 1.4f), grouped = true) { "${(it * 100).toInt()}%" }
        LyricsFloatChoiceSetting(context, "远近模糊", "lyrics_blur_strength", 1f, listOf(0f, .5f, .8f, 1f), grouped = true) { if (it == 0f) "关闭" else "${(it * 100).toInt()}%" }
    }

    // Group 5: 当前行放大-未播放文字亮度-控制栏自动隐藏-滚动隐藏UI阈值
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        LyricsFloatChoiceSetting(context, "当前行放大", "lyrics_focus_scale", 1.02f, listOf(1f, 1.02f, 1.04f, 1.08f), grouped = true) { "${(it * 100).toInt()}%" }
        LyricsFloatChoiceSetting(context, "未播放文字亮度", "lyrics_inactive_opacity", .42f, listOf(.3f, .42f, .5f, .6f), grouped = true) { "${(it * 100).toInt()}%" }
        LyricsChoiceSetting(context, "控制栏自动隐藏", "lyrics_interface_auto_hide_ms", 5_000, (3..15).map { it * 1_000 }, grouped = true) { "${it / 1_000} 秒" }
        LyricsChoiceSetting(context, "滚动隐藏 UI 阈值", "lyrics_scroll_hide_threshold_dp", 200, listOf(40, 80, 120, 160, 200, 240), grouped = true) { "$it dp" }
    }

    // Group 6: 启用位移回弹-启用升格回弹-升格回弹时长-焦点回弹时长
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        SettingsToggleRow(context, "启用位移回弹", "lyrics_cascade_bounce_enabled", true, grouped = true)
        SettingsToggleRow(context, "启用升格回弹", "lyrics_scale_bounce_enabled", true, grouped = true)
        LyricsChoiceSetting(context, "升格回弹时长", "lyrics_scale_bounce_duration_ms", 580, listOf(150, 250, 350, 450, 580, 700, 800), grouped = true) { "${it}ms" }
        LyricsChoiceSetting(context, "焦点回弹时长", "lyrics_focus_color_lead_ms", 0, listOf(-300, -200, -100, -50, 0, 50, 100, 200, 300), grouped = true) { if (it == 0) "同步" else "${it}ms" }
    }

    // Group 7: 最大回弹弹性-回弹强度梯度-升格回弹弹性
    Spacer(Modifier.height(10.dp))
    PreferenceFloatSlider(context, "最大回弹弹性", "lyrics_cascade_bounce", .26f, 0f..8f / 10f, 79) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, "回弹强度梯度", "lyrics_cascade_bounce_gradient", .85f, 0f..1f, 99) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, "升格回弹弹性", "lyrics_scale_bounce", .32f, 0f..5f / 10f, 49) { "${(it * 100).toInt()}%" }

    // Group 8: 高光渐变宽度-渐变削减程度
    Spacer(Modifier.height(10.dp))
    PreferenceFloatSlider(context, "高光渐变宽度", "lyrics_highlight_gradient_width", .7f, .4f..3f, 25) { "%.1f 字宽".format(it) }
    PreferenceFloatSlider(context, "渐变削减程度", "lyrics_highlight_gradient_reduction", .65f, 0f..1f, 19) { "${(it * 100).toInt()}%" }

    // Group 9: 焦点垂直位置-默认逐句模糊加强-隐藏UI逐句模糊加强-非焦点歌词变暗
    Spacer(Modifier.height(10.dp))
    PreferenceFloatSlider(context, "焦点垂直位置", "lyrics_focus_position", .25f, .05f..8f / 10f, 74) { "距顶部 ${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, "默认逐句模糊加强", "lyrics_distance_blur_scale", 1.05f, 0f..1.5f, 29) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, "隐藏 UI 逐句模糊加强", "lyrics_hidden_blur_scale", .85f, 0f..1.5f, 29) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, "非焦点歌词变暗", "lyrics_dim_amount", 1f, 0f..1f, 49) { "${(it * 100).toInt()}%" }

    // Group 10: 基础拖尾延迟-逐句拖尾增量-后续歌词启动延迟-拖尾追赶节奏-追赶速度梯度-位移收束时长
    Spacer(Modifier.height(10.dp))
    PreferenceFloatSlider(context, "基础拖尾延迟", "lyrics_cascade_delay_ms", 21f, 0f..100f, 99) { "${it.toInt()} ms" }
    PreferenceFloatSlider(context, "逐句拖尾增量", "lyrics_cascade_delay_increase_ms", 5f, 0f..100f, 99) { "${it.toInt()} ms/句" }
    PreferenceFloatSlider(context, "后续歌词启动延迟", "lyrics_cascade_following_delay_ms", 30f, 0f..200f, 199) { "${it.toInt()} ms" }
    PreferenceFloatSlider(context, "拖尾追赶节奏", "lyrics_cascade_catch_up_ratio", .97f, .5f..1f, 49) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, "追赶速度梯度", "lyrics_cascade_chase_gradient", .70f, 0f..1f, 99) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, "位移收束时长", "lyrics_cascade_duration_ms", 740f, 200f..1_200f, 99) { "%.2f 秒".format(it / 1_000f) }

    // Group 11: 瞬移阈值
    Spacer(Modifier.height(10.dp))
    PreferenceFloatSlider(context, "瞬移阈值", "lyrics_snap_threshold_ms", 260f, 50f..500f, 89) { "${it.toInt()} ms" }
}

@Composable
private fun LyricsStringChoiceSetting(
    context: android.content.Context,
    title: String,
    key: String,
    default: String,
    values: List<String>,
    grouped: Boolean = false,
    label: (String) -> String,
) {
    val groupRowIndex = LocalSettingsGroupRowIndex.current
    val showSep = if (grouped) groupRowIndex.intValue++.let { it > 0 } else false
    var selected by remember(key) { mutableStateOf(MeloXSettingsPreferences.string(context, key, default)) }
    MeloXSettingsDropdown(
        title = title,
        selected = selected,
        items = values.map { it to label(it) },
        onSelected = {
            selected = it
            MeloXSettingsPreferences.setString(context, key, it)
        },
        grouped = grouped,
        showTopSeparator = showSep,
    )
    if (!grouped) Spacer(Modifier.height(10.dp))
}

@Composable
private fun LyricsChoiceSetting(
    context: android.content.Context,
    title: String,
    key: String,
    default: Int,
    values: List<Int>,
    grouped: Boolean = false,
    label: (Int) -> String,
) {
    val groupRowIndex = LocalSettingsGroupRowIndex.current
    val effectiveShowTopSeparator = if (grouped) groupRowIndex.intValue++.let { it > 0 } else false
    var selected by remember(key) { mutableStateOf(MeloXSettingsPreferences.int(context, key, default)) }
    MeloXSettingsDropdown(
        title = title,
        selected = selected,
        items = values.map { it to label(it) },
        onSelected = {
            selected = it
            MeloXSettingsPreferences.setInt(context, key, it)
        },
        grouped = grouped,
        showTopSeparator = effectiveShowTopSeparator,
    )
    if (!grouped) Spacer(Modifier.height(10.dp))
}

@Composable
private fun LyricsFloatChoiceSetting(
    context: android.content.Context,
    title: String,
    key: String,
    default: Float,
    values: List<Float>,
    grouped: Boolean = false,
    label: (Float) -> String,
) {
    val groupRowIndex = LocalSettingsGroupRowIndex.current
    val effectiveShowTopSeparator = if (grouped) groupRowIndex.intValue++.let { it > 0 } else false
    var selected by remember(key) { mutableStateOf(MeloXSettingsPreferences.float(context, key, default)) }
    val selectedValue = values.minByOrNull { kotlin.math.abs(selected - it) } ?: default
    MeloXSettingsDropdown(
        title = title,
        selected = selectedValue,
        items = values.map { it to label(it) },
        onSelected = {
            selected = it
            MeloXSettingsPreferences.setFloat(context, key, it)
        },
        grouped = grouped,
        showTopSeparator = effectiveShowTopSeparator,
    )
    if (!grouped) Spacer(Modifier.height(10.dp))
}

@Composable
private fun SettingsFloatSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: (Float) -> String = { "${(it * 100).toInt()}%" },
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        Text(label(value), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        steps = steps,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PreferenceFloatSlider(
    context: android.content.Context,
    title: String,
    key: String,
    default: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: (Float) -> String,
) {
    var value by remember(key) { mutableStateOf(MeloXSettingsPreferences.float(context, key, default)) }
    SettingsFloatSlider(title, value, range, steps, label) {
        value = it
        MeloXSettingsPreferences.setFloat(context, key, it)
    }
}

@Composable
private fun ContentFeatureSettings(context: android.content.Context) {
    SettingsGlassGroup {
        SettingsToggleRow(context, "播客", "feature_podcasts", true, grouped = true)
        SettingsToggleRow(context, "最近播放", "feature_history", true, grouped = true)
        SettingsToggleRow(context, "下载", "feature_downloads", true, "控制音乐库下载入口；已下载文件不会被删除。", grouped = true)
        SettingsToggleRow(context, "音乐云盘", "feature_cloud_music", true, "读取、搜索、上传、播放和删除网易云云盘歌曲。", grouped = true)
    }
}

@Composable
private fun MessagesSettings(context: android.content.Context) {
    val ops = remember(context) {
        NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
    }
    val account = remember(context) {
        NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
    }
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<MeloXMessageContact>>(emptyList()) }
    var selected by remember { mutableStateOf<MeloXMessageContact?>(null) }
    var messages by remember { mutableStateOf<List<MeloXPrivateMessage>>(emptyList()) }
    var currentUserId by remember { mutableStateOf(0L) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(selected?.id, reload) {
        busy = true
        error = null
        runCatching {
            val profile = account.accountProfile()
            currentUserId = profile.userId
            val contact = selected
            if (contact == null) {
                val recent = ops.privateMessageConversations(profile.userId)
                val follows = ops.messageContacts(profile.userId)
                contacts = (recent + follows).filter { it.id != profile.userId }.distinctBy(MeloXMessageContact::id)
            } else {
                messages = ops.privateMessageHistory(contact.id)
            }
        }.onFailure { error = it.message ?: "私信读取失败" }
        busy = false
    }

    if (selected == null) {
        Text("联系人与会话", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        Spacer(Modifier.height(8.dp))
        if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(10.dp))
            Text("正在读取私信", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
        LazyColumn(Modifier.fillMaxWidth().height(440.dp)) {
            items(contacts.take(100), key = MeloXMessageContact::id) { contact ->
                Row(
                    Modifier.fillMaxWidth().clickable { selected = contact }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(contact.avatarUrl, null, Modifier.size(42.dp).clip(CircleShape))
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(contact.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        if (contact.signature.isNotBlank()) Text(contact.signature, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
                    }
                    MeloXActionIcon("›", Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .3f))
                }
            }
        }
        return
    }

    val contact = selected!!
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SettingsRoundButton("‹") { selected = null; messages = emptyList(); draft = "" }
        Spacer(Modifier.size(12.dp))
        AsyncImage(contact.avatarUrl, null, Modifier.size(38.dp).clip(CircleShape))
        Spacer(Modifier.size(10.dp))
        Text(contact.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("刷新", Modifier.clickable { reload++ }.padding(8.dp), color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(14.dp))
    if (busy && messages.isEmpty()) CircularProgressIndicator(Modifier.size(24.dp))
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
    LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
        items(messages.takeLast(60), key = MeloXPrivateMessage::id) { message ->
            val outgoing = message.fromUserId == currentUserId
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                if (outgoing) Spacer(Modifier.weight(.2f))
                Text(
                    message.text,
                    modifier = Modifier.background(
                        if (outgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = .08f),
                        RoundedCornerShape(16.dp),
                    ).padding(horizontal = 12.dp, vertical = 9.dp).weight(.8f, fill = false),
                    color = if (outgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                )
                if (!outgoing) Spacer(Modifier.weight(.2f))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onBackground.copy(alpha = .06f), RoundedCornerShape(22.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (draft.isBlank()) Text("输入私信", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
                    inner()
                }
            },
        )
        Text(
            if (busy) "…" else "发送",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(enabled = !busy && draft.isNotBlank()) {
                val text = draft.trim()
                busy = true
                scope.launch {
                    runCatching { ops.sendPrivateText(text, contact.id) }
                        .onSuccess { draft = ""; messages = ops.privateMessageHistory(contact.id) }
                        .onFailure { error = it.message ?: "发送失败" }
                    busy = false
                }
            }.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ContentSettings(context: android.content.Context) {
    var area by remember { mutableStateOf(MeloXSettingsRuntime.musicArea) }
    var crossProviderFallback by remember {
        mutableStateOf(CrossProviderPlaybackPreferences.enabled(context))
    }
    var showCrossProviderFallbackNotice by remember { mutableStateOf(false) }
    SettingsGlassGroup {
        MeloXSettingsDropdown(
            title = "新碟与发现地区",
            selected = area,
            items = listOf("全部" to "全部", "华语" to "华语", "欧美" to "欧美", "日本" to "日本", "韩国" to "韩国"),
            onSelected = { area = it; MeloXSettingsPreferences.setString(context, "music_area", it) },
            grouped = true,
        )
        SettingsToggleRow(context, "发现页显示精品歌单", "content_high_quality_playlist", true, grouped = true)
        SettingsToggleRow(context, "显示歌单播放量", "content_playlist_play_count", true, grouped = true)
        SettingsExternalToggleRow(
            title = "不可用资源从其他平台获取",
            value = crossProviderFallback,
            grouped = true,
        ) { enabled ->
            if (enabled) {
                showCrossProviderFallbackNotice = true
            } else {
                crossProviderFallback = false
                CrossProviderPlaybackPreferences.setEnabled(context, false)
            }
        }
    }
    if (crossProviderFallback) {
        Spacer(Modifier.height(10.dp))
        SettingsInfoCard(
            "仅当网易云明确未返回可播放音频时，才会严格匹配 QQ音乐、酷狗音乐或 Bilibili 的完整音源。",
        )
    }
    if (showCrossProviderFallbackNotice) {
        MeloXGlassDialog(
            visible = true,
            onDismiss = { showCrossProviderFallbackNotice = false },
        ) {
            Text("启用跨平台资源匹配？", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "仅当网易云明确无法返回完整可播放音频时，MeloX 才会将当前曲目的标题、歌手和时长发送给 QQ音乐、酷狗音乐和 Bilibili 的搜索接口。",
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Text(
                text = "只有标题与主艺人一致、且时长在严格范围内的完整音源才会播放。MeloX 不使用试听片段，也不会绕过付费、地区、版权、DRM 或账号权限限制。实际平台、歌曲版本和音质可能与原条目不同；收藏、歌单身份和歌词仍保留原网易云条目。",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Text(
                text = "播放行为同时受实际音源平台的服务条款和隐私政策约束，你可以随时关闭此功能。",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            MeloXLegalLinks(modifier = Modifier.padding(top = 6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsActionButton("取消", Modifier.weight(1f)) {
                    showCrossProviderFallbackNotice = false
                }
                SettingsActionButton("了解并启用", Modifier.weight(1f)) {
                    crossProviderFallback = true
                    CrossProviderPlaybackPreferences.setEnabled(context, true)
                    showCrossProviderFallbackNotice = false
                }
            }
        }
    }
}

@Composable
private fun ListenTogetherSettings(context: android.content.Context) {
    val app = context.applicationContext
    val state by MeloXListenTogetherCoordinator.state(app).collectAsState()
    val room = state.room
    val scope = rememberCoroutineScope()
    val ops = remember(app) {
        NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(app) })
    }
    var invitation by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    SettingsInfoCard("一起听会在后台持续同步播放、暂停、切歌、拖动进度和播放队列；离开此页面不会中断房间。")
    Spacer(Modifier.height(12.dp))
    if (room == null) {
        SettingsActionButton("发起一起听") {
            if (!busy) {
                busy = true
                message = null
                scope.launch {
                    runCatching { ops.createListenTogetherRoom() }
                        .onSuccess { MeloXListenTogetherCoordinator.adoptRoom(app, it) }
                        .onFailure { message = it.message ?: "创建房间失败" }
                    busy = false
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        MeloXGlassTextField(
            value = invitation,
            onValueChange = { invitation = it },
            placeholder = { Text("粘贴一起听邀请链接", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        SettingsActionButton("加入邀请房间") {
            val parsed = parseNeteaseListenTogetherInvitation(invitation)
            if (parsed == null) {
                message = "邀请链接缺少房间或邀请人信息"
            } else if (!busy) {
                busy = true
                message = null
                scope.launch {
                    runCatching { ops.joinListenTogetherRoom(parsed.roomId, parsed.inviterId) }
                        .onSuccess { MeloXListenTogetherCoordinator.adoptRoom(app, it) }
                        .onFailure { message = it.message ?: "加入房间失败" }
                    busy = false
                }
            }
        }
    } else {
        SettingsGlassGroup {
            MeloXIosListRow("房间", detail = room.id, showTopSeparator = false)
            MeloXIosListRow("成员", detail = "${room.users.size.coerceAtLeast(1)} 人")
            room.users.forEach { user -> MeloXIosListRow(user.name) }
            MeloXIosListRow(
                "连接状态",
                detail = when (state.phase) {
                    MeloXListenTogetherCoordinator.Phase.Connected -> "已同步"
                    MeloXListenTogetherCoordinator.Phase.Reconnecting -> "重连中"
                    MeloXListenTogetherCoordinator.Phase.Idle -> "恢复中"
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        SettingsActionButton("分享房间邀请") {
            val inviter = room.users.firstOrNull()?.id ?: room.creatorId
            val url = "https://music.163.com/listen-together/share/?roomId=${room.id}&inviterId=$inviter"
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url),
                    "分享一起听邀请",
                ),
            )
        }
        Spacer(Modifier.height(10.dp))
        SettingsDangerButton("结束或退出房间") {
            if (!busy) {
                busy = true
                scope.launch {
                    runCatching { ops.endListenTogetherRoom(room.id) }
                        .onSuccess { MeloXListenTogetherCoordinator.clearRoom(app) }
                        .onFailure { message = it.message ?: "退出房间失败" }
                    busy = false
                }
            }
        }
    }
    if (busy) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    }
    (message ?: state.lastError)?.let {
        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun StorageSettings(context: android.content.Context) {
    var usage by remember { mutableStateOf(MeloXStorageUsage()) }
    var loading by remember { mutableStateOf(true) }
    var maintenanceMessage by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val downloads = remember(context) { MeloXDownloadStore.get(context) }
    suspend fun refresh() {
        loading = true
        usage = withContext(Dispatchers.IO) {
            val stats = StatFs(context.filesDir.path)
            MeloXStorageUsage(
                downloads = downloads.totalByteCount,
                networkCache = listOf("melox_http", "image_cache", "coil3_disk_cache")
                    .sumOf { context.cacheDir.resolve(it).treeByteCount() },
                playbackCache = context.cacheDir.resolve("melox_media").treeByteCount(),
                temporary = context.cacheDir.resolve("automix_analysis").treeByteCount(),
                localData = listOf(
                    context.filesDir.resolve("automix_analysis_index.json"),
                    context.filesDir.resolve("netease_library_cache"),
                ).sumOf { it.treeByteCount() },
                deviceTotal = stats.totalBytes,
                deviceAvailable = stats.availableBytes,
            )
        }
        loading = false
    }
    LaunchedEffect(Unit) { refresh() }

    var autoCache by remember { mutableStateOf(MeloXSettingsPreferences.boolean(context, "downloads_auto_cache", false)) }

    val deviceUsed = (usage.deviceTotal - usage.deviceAvailable).coerceAtLeast(0L)
    val deviceFraction = if (usage.deviceTotal > 0L) deviceUsed.toFloat() / usage.deviceTotal else 0f
    Box(Modifier.fillMaxWidth().meloXContentSurface(MeloXShapes.largeCard).padding(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            MeloXSymbolIcon(MeloXSymbol.Storage, Modifier.size(30.dp), MaterialTheme.colorScheme.onSurface)
            Text("MeloX 管理的内容", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            Text(if (loading) "计算中…" else formatBytes(usage.managed), fontSize = 34.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
                progress = { deviceFraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
            Text(
                "设备已使用 ${formatBytes(deviceUsed)} · 可用 ${formatBytes(usage.deviceAvailable)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .50f),
            )
            Text("可重新生成的缓存：${formatBytes(usage.reclaimable)}", fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(18.dp))
    Text("存储项目", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        StorageUsageRow(MeloXSymbol.Download, "下载与自动缓存", usage.downloads, false)
        StorageUsageRow(MeloXSymbol.Apps, "网络与图片缓存", usage.networkCache, true)
        StorageUsageRow(MeloXSymbol.AutoMix, "播放缓存", usage.playbackCache, true)
        StorageUsageRow(MeloXSymbol.RadioWaves, "临时分析文件", usage.temporary, true)
        StorageUsageRow(MeloXSymbol.Storage, "本地数据与分析索引", usage.localData, true)
    }
    Spacer(Modifier.height(18.dp))
    Text("缓存清理", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        StorageActionRow(MeloXSymbol.Trash, "清理所有可重建缓存", false) { confirmation = "all_cache" }
        StorageActionRow(MeloXSymbol.Apps, "清理网络与图片缓存", true) { confirmation = "network_cache" }
        StorageActionRow(MeloXSymbol.AutoMix, "清理播放缓存", true) { confirmation = "playback_cache" }
    }
    Spacer(Modifier.height(18.dp))

    SettingsGlassGroup {
        SettingsToggleRow(context, "下载歌词", "download_lyrics", true, "下载歌曲时同时保存歌词；默认开启，可在此关闭。封面始终随歌曲保存。", grouped = true)
        SettingsExternalToggleRow("按播放次数自动缓存", autoCache, "歌曲实际开始播放达到阈值后自动下载；不会重复下载。", grouped = true) {
            autoCache = it
            MeloXSettingsPreferences.setBoolean(context, "downloads_auto_cache", it)
        }
    }
    if (autoCache) {
        LyricsChoiceSetting(context, "触发次数", "downloads_auto_cache_threshold", 3, listOf(2, 3, 5, 8, 10)) { "$it 次" }
        var cacheQuality by remember { mutableStateOf(MeloXSettingsPreferences.string(context, "downloads_auto_cache_quality", MusicQuality.Standard.name)) }
        Text("自动缓存音质", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        Spacer(Modifier.height(8.dp))
        SettingsGlassGroup {
            MusicQuality.entries.forEach { value ->
                SettingsChoiceRow(value.title, cacheQuality == value.name) {
                    cacheQuality = value.name
                    MeloXSettingsPreferences.setString(context, "downloads_auto_cache_quality", value.name)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SettingsActionButton("重置自动缓存播放统计") { downloads.resetAutomaticCacheHistory() }
    }
    Spacer(Modifier.height(10.dp))

    if (downloads.activeDownloads.isNotEmpty()) {
        Text("正在下载", modifier = Modifier.padding(top=10.dp,bottom=8.dp), fontWeight=FontWeight.SemiBold)
        SettingsGlassGroup {
            downloads.activeDownloads.values.forEach { active ->
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(active.song.name, maxLines=1, overflow=TextOverflow.Ellipsis)
                        Text(active.fractionCompleted?.let { "${(it*100).toInt()}% · ${active.quality.title}" } ?: active.quality.title, color=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f), fontSize=11.sp)
                    }
                    Text("取消", color=MaterialTheme.colorScheme.error, modifier=Modifier.clickable { downloads.cancel(active.song.id) }.padding(8.dp))
                }
            }
        }
    }

    if (downloads.downloads.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        SettingsDangerButton("删除全部已下载歌曲") { downloads.removeAll() }
    }
    downloads.errorMessage?.let { Text(it, color=MaterialTheme.colorScheme.error, fontSize=12.sp, modifier=Modifier.padding(top=10.dp)) }
    maintenanceMessage?.let { Text(it, color=MaterialTheme.colorScheme.primary, fontSize=12.sp, modifier=Modifier.padding(top=10.dp)) }
    Spacer(Modifier.height(14.dp))
    SettingsActionButton("检查并修复下载存储") {
        downloads.repairStorage { result ->
            maintenanceMessage = result.fold(
                onSuccess = {
                    "已移除 ${it.missingRecordsRemoved} 条缺失记录、${it.orphanFilesRemoved} 个孤立文件，回收 ${formatBytes(it.recoveredBytes)}；索引已压缩。"
                },
                onFailure = { it.message ?: "修复失败" },
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton("清理临时缓存") {
        confirmation = "temporary"
    }
    confirmation?.let { action ->
        val (title, message) = when (action) {
            "all_cache" -> "清理所有可重建缓存？" to "将清除网络缓存和播放缓存，不会影响收藏、账号数据或已下载歌曲。"
            "network_cache" -> "清理网络与图片缓存？" to "网络响应和封面会在下次使用时重新获取。"
            "playback_cache" -> "清理播放缓存？" to "已缓存的流媒体音频会被移除，之后播放时会重新获取。"
            else -> "清理临时分析文件？" to "将移除当前未使用的临时分析文件，不会删除持久化分析索引。"
        }
        MeloXGlassDialog(visible = true, onDismiss = { confirmation = null }) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(message, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionButton("取消", Modifier.weight(1f)) { confirmation = null }
                SettingsActionButton("清理", Modifier.weight(1f)) {
                    confirmation = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            when (action) {
                                "all_cache" -> { MeloXHttpClient.clearCache(); MeloXMediaCache.clear(context) }
                                "network_cache" -> MeloXHttpClient.clearCache()
                                "playback_cache" -> MeloXMediaCache.clear(context)
                                else -> context.cacheDir.resolve("automix_analysis").deleteRecursively()
                            }
                        }
                        maintenanceMessage = "清理完成"
                        refresh()
                    }
                }
            }
        }
    }
}

private data class MeloXStorageUsage(
    val downloads: Long = 0L,
    val networkCache: Long = 0L,
    val playbackCache: Long = 0L,
    val temporary: Long = 0L,
    val localData: Long = 0L,
    val deviceTotal: Long = 0L,
    val deviceAvailable: Long = 0L,
) {
    val managed: Long get() = downloads + networkCache + playbackCache + temporary + localData
    val reclaimable: Long get() = networkCache + playbackCache + temporary
}

@Composable
private fun StorageUsageRow(symbol: MeloXSymbol, title: String, bytes: Long, showSeparator: Boolean) {
    MeloXIosListRow(
        title = title,
        detail = formatBytes(bytes),
        leading = { MeloXSymbolIcon(symbol, Modifier.size(22.dp), MeloXSystemColors.Red) },
        showTopSeparator = showSeparator,
    )
}

@Composable
private fun StorageActionRow(symbol: MeloXSymbol, title: String, showSeparator: Boolean, onClick: () -> Unit) {
    MeloXIosListRow(
        title = title,
        leading = { MeloXSymbolIcon(symbol, Modifier.size(22.dp), MeloXSystemColors.Red) },
        onClick = onClick,
        showTopSeparator = showSeparator,
    )
}

private fun java.io.File.treeByteCount(): Long = when {
    isFile -> length()
    isDirectory -> listFiles()?.sumOf { it.treeByteCount() } ?: 0L
    else -> 0L
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun TabLayoutSettings(context: android.content.Context) {
    SettingsGlassGroup {
        SettingsToggleRow(context, "首页", "tab_home", true, grouped = true)
        SettingsToggleRow(context, "发现", "tab_explore", true, grouped = true)
        SettingsToggleRow(context, "音乐库", "tab_library", true, grouped = true)
    }
    listOf(
        Triple("播客", "podcasts", MeloXSettingsRuntime.podcastsEnabled),
        Triple("下载", "downloads", MeloXSettingsRuntime.downloadsEnabled),
        Triple("云盘", "cloud", MeloXSettingsRuntime.cloudMusicEnabled),
    ).forEach { (title, key, enabled) ->
        Spacer(Modifier.height(10.dp))
        SettingsGlassGroup {
            Text(title, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
            SettingsToggleRow(context, "首页快捷入口", "placement_${key}_home", key == "podcasts", if (enabled) null else "请先在内容功能中启用$title。", grouped = true)
            SettingsToggleRow(context, "音乐库子页", "placement_${key}_library", true, grouped = true)
            SettingsToggleRow(context, "独立标签页", "placement_${key}_tab", false, grouped = true)
        }
    }
    Spacer(Modifier.height(10.dp))
    var order by remember { mutableStateOf(MeloXSettingsRuntime.tabOrder) }
    SettingsGlassGroup {
        Text("标签栏顺序", modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        order.forEachIndexed { index, page ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(when (page) { "Home" -> "首页"; "Explore" -> "发现"; "Library" -> "音乐库"; "Podcasts" -> "播客"; "Downloads" -> "下载"; "Cloud" -> "云盘"; else -> "设置" }, Modifier.weight(1f))
                MeloXActionIcon("↑", Modifier.size(18.dp).clickable(enabled = index > 0) {
                    order = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                    MeloXSettingsPreferences.setString(context, "tab_order", order.joinToString(","))
                }.padding(10.dp), MaterialTheme.colorScheme.primary.copy(alpha = if (index > 0) 1f else .25f))
                MeloXActionIcon("↓", Modifier.size(18.dp).clickable(enabled = index < order.lastIndex) {
                    order = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                    MeloXSettingsPreferences.setString(context, "tab_order", order.joinToString(","))
                }.padding(10.dp), MaterialTheme.colorScheme.primary.copy(alpha = if (index < order.lastIndex) 1f else .25f))
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    var homeOrder by remember { mutableStateOf(MeloXSettingsRuntime.homeSectionOrder) }
    SettingsGlassGroup {
        Text("首页区块", modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        SettingsToggleRow(context, "快捷入口", "home_quick_actions", true, grouped = true)
        SettingsToggleRow(context, "推荐歌单", "home_playlists", true, grouped = true)
        SettingsToggleRow(context, "推荐新歌", "home_new_songs", true, grouped = true)
        SettingsToggleRow(context, "记住音乐库子页面", "library_remember_page", true, grouped = true)
        homeOrder.forEachIndexed { index, section ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(when (section) { "QuickActions" -> "快捷入口"; "Playlists" -> "推荐歌单"; else -> "推荐新歌" }, Modifier.weight(1f))
                MeloXActionIcon("↑", Modifier.size(18.dp).clickable(enabled = index > 0) {
                    homeOrder = homeOrder.toMutableList().apply { add(index - 1, removeAt(index)) }
                    MeloXSettingsPreferences.setString(context, "home_section_order", homeOrder.joinToString(","))
                }.padding(10.dp), MaterialTheme.colorScheme.primary.copy(alpha = if (index > 0) 1f else .25f))
                MeloXActionIcon("↓", Modifier.size(18.dp).clickable(enabled = index < homeOrder.lastIndex) {
                    homeOrder = homeOrder.toMutableList().apply { add(index + 1, removeAt(index)) }
                    MeloXSettingsPreferences.setString(context, "home_section_order", homeOrder.joinToString(","))
                }.padding(10.dp), MaterialTheme.colorScheme.primary.copy(alpha = if (index < homeOrder.lastIndex) 1f else .25f))
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    var libraryPage by remember { mutableStateOf(MeloXSettingsRuntime.defaultLibraryPage) }
    SettingsGlassGroup {
        MeloXSettingsDropdown(
            title = "音乐库默认页",
            selected = libraryPage,
            items = listOf("Songs" to "歌曲", "Playlists" to "歌单", "Podcasts" to "播客", "Cloud" to "云盘", "History" to "最近播放", "Downloads" to "下载")
                .filter { (value, _) ->
                    (value != "Podcasts" || MeloXSettingsRuntime.podcastsEnabled) &&
                        (value != "Podcasts" || MeloXSettingsRuntime.podcastsLibraryPlacement) &&
                        (value != "Cloud" || MeloXSettingsRuntime.cloudMusicEnabled) &&
                        (value != "Cloud" || MeloXSettingsRuntime.cloudLibraryPlacement) &&
                        (value != "History" || MeloXSettingsRuntime.listeningHistoryEnabled) &&
                        (value != "Downloads" || MeloXSettingsRuntime.downloadsEnabled) &&
                        (value != "Downloads" || MeloXSettingsRuntime.downloadsLibraryPlacement)
                },
            onSelected = { libraryPage = it; MeloXSettingsPreferences.setString(context, "library_default_page", it) },
            grouped = true,
        )
        Text("搜索保持为独立的右侧 Liquid Glass 按钮。", modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f))
    }
}

@Composable
private fun GeneralSettings(context: android.content.Context) {
    var theme by remember { mutableStateOf(MeloXSettingsRuntime.themeMode) }
    var swipeFullAction by remember { mutableStateOf(MeloXSettingsRuntime.swipeFullAction) }
    SettingsGlassGroup {
        MeloXSettingsDropdown(
            title = "主题",
            selected = theme,
            items = listOf(
                MeloXThemeMode.System to "跟随系统",
                MeloXThemeMode.Light to "浅色",
                MeloXThemeMode.Dark to "深色",
            ),
            onSelected = { theme = it; MeloXSettingsPreferences.setString(context, "theme_mode", it.name) },
            grouped = true,
        )
        var defaultTab by remember { mutableStateOf(MeloXSettingsRuntime.defaultTab) }
        MeloXSettingsDropdown(
            title = "默认启动页",
            selected = defaultTab,
            items = listOf(
                "Home" to "首页",
                "Explore" to "发现",
                "Library" to "音乐库",
                "Settings" to "设置",
            ),
            onSelected = { defaultTab = it; MeloXSettingsPreferences.setString(context, "general_default_tab", it) },
            grouped = true,
        )
        SettingsToggleRow(context, "记住上次标签页", "general_remember_tab", true, grouped = true)
        MeloXSettingsDropdown(
            title = "歌曲右滑满滑操作",
            selected = swipeFullAction,
            items = listOf(
                MeloXSwipeFullAction.PlayNext to "下一首播放",
                MeloXSwipeFullAction.AddToQueue to "添加到队列",
            ),
            onSelected = {
                swipeFullAction = it
                MeloXSettingsPreferences.setString(context, "general_swipe_full_action", it.name)
            },
            grouped = true,
        )
        SettingsToggleRow(context, "识别剪贴板中的网易云链接", "general_clipboard_links", true, "每次回到前台只读取一次；识别歌曲或歌单后会先询问是否打开。", grouped = true)
        SettingsToggleRow(context, "触感", "general_haptic_feedback", true, grouped = true)
        SettingsToggleRow(
            context,
            "不自动缩小底栏",
            "general_disable_auto_tabbar_shrink",
            false,
            "向上滚动页面时，底部导航栏仍保持展开。",
            grouped = true,
        )
    }
}

@Composable
private fun RecognitionSettings(context: android.content.Context) {
    val client = remember(context) { SongRecognitionClient(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var duration by remember {
        mutableStateOf(MeloXSettingsPreferences.string(context, "recognition_duration", "6").toIntOrNull() ?: 6)
    }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("靠近声源后开始识别") }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<SongRecognitionResult>>(emptyList()) }
    var recognitionJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(client) {
        onDispose {
            recognitionJob?.cancel()
            client.close()
        }
    }

    fun startCapture() {
        recognitionJob?.cancel()
        recognitionJob = scope.launch {
            working = true
            error = null
            if (duration != 0) results = emptyList()
            try {
                if (duration == 0) {
                    status = "正在持续识别；点击停止结束"
                    while (isActive) {
                        val found = client.recognize(9)
                        if (found.isNotEmpty()) {
                            results = (found + results).distinctBy { it.song.id }.take(100)
                        }
                    }
                } else {
                    status = "正在聆听 ${duration} 秒…"
                    val found = client.recognize(duration)
                    results = found
                    status = if (found.isEmpty()) "没有识别到歌曲，请靠近声源后重试" else "识别完成"
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                status = if (results.isEmpty()) "识别已停止" else "已停止，保留识别结果"
            } catch (failure: Throwable) {
                error = failure.message ?: "听歌识曲失败"
                status = "无法完成识别"
            } finally {
                working = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startCapture() else error = "没有麦克风权限；请在系统设置中允许 MeloX 使用麦克风。"
    }

    Spacer(Modifier.height(14.dp))
    SettingsGlassGroup {
        listOf(3 to "3 秒 · 更快", 6 to "6 秒 · 推荐", 9 to "9 秒 · 嘈杂环境").forEach { (value, title) ->
            SettingsChoiceRow(title, duration == value) {
                if (!working) {
                    duration = value
                    MeloXSettingsPreferences.setString(context, "recognition_duration", value.toString())
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    SettingsActionButton(if (working) "停止识别" else "开始听歌识曲") {
        if (working) {
            recognitionJob?.cancel()
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startCapture()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (working) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(10.dp))
        }
        Text(status, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f), fontSize = 13.sp)
    }
    error?.let { message ->
        Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
    }
    if (results.isNotEmpty()) {
        Text("识别结果", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SettingsGlassGroup {
            Column {
                results.forEach { result ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            PlaybackCommands.playQueue(
                                context = context,
                                songs = results.map { it.song },
                                selectedSongId = result.song.id,
                                startPositionMs = result.startTimeMs,
                            )
                        }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(result.song.artworkUrl, null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)))
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(result.song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(result.song.artists, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                        }
                        if (result.startTimeMs > 0L) Text("${result.startTimeMs / 1000}s", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteConfigSettings() {
    val context = LocalContext.current.applicationContext
    val status by MeloXRemoteConfigRuntime.status.collectAsState()
    val scope = rememberCoroutineScope()
    val githubRouting = remember { MeloXGitHubRouting(context) }
    val consentEnabled = MeloXRemoteConfigConsent.enabled(context)
    val source = if (!consentEnabled) {
        "云控已拒绝或关闭"
    } else when (status.source) {
        MeloXRemoteConfigSource.BuiltIn -> "内置安全默认值"
        MeloXRemoteConfigSource.VerifiedRemote -> "已验证远程配置"
        MeloXRemoteConfigSource.VersionInapplicable -> "远程配置不适用于当前版本"
    }
    SettingsGlassGroup {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(source, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "签名配置仅控制已披露的音乐源登录、播放和跨平台回退能力。本地选择始终优先；授权后每次应用进入前台检查，并在前台持续运行期间每两小时检查一次。",
                modifier = Modifier.padding(top = 7.dp),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    SettingsGlassGroup {
        RemoteConfigStatusLine("配置版本", status.config.configVersion.toString())
        RemoteConfigStatusLine(
            "访问源",
            githubRouting.effectiveRoute()?.takeIf { githubRouting.selectedSource() == MeloXGitHubSource.Auto }
                ?.let { "${it.source.label}（${it.latencyMs}ms）" }
                ?: githubRouting.selectedSource().label,
        )
        RemoteConfigStatusLine("签名密钥", status.keyId ?: "内置")
        RemoteConfigStatusLine("最近检查", formatRemoteConfigTime(status.lastCheckedAtEpochMs))
        RemoteConfigStatusLine("最近更新", formatRemoteConfigTime(status.lastUpdatedAtEpochMs))
        RemoteConfigStatusLine(
            "声明熔断",
            status.config.disabledCapabilities.takeIf(Set<String>::isNotEmpty)?.joinToString("、") ?: "无",
        )
        RemoteConfigStatusLine("回退顺序", status.config.fallback.order.joinToString(" → "))
        RemoteConfigStatusLine("回退超时", "${status.config.fallback.timeoutMs} ms")
    }
    status.error?.let { error ->
        Spacer(Modifier.height(12.dp))
        SettingsInfoCard("最近检查失败：$error\n已继续使用上一次有效配置或内置默认值。")
    }
    Spacer(Modifier.height(12.dp))
    SettingsActionButton(
        when {
            !consentEnabled -> "请先在隐私协议中启用云控"
            status.refreshing -> "正在检查…"
            else -> "手动检查配置"
        },
    ) {
        if (consentEnabled && !status.refreshing) {
            scope.launch { MeloXRemoteConfigRuntime.refresh(force = true) }
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsDangerButton("清除缓存并恢复内置配置") {
        scope.launch { MeloXRemoteConfigRuntime.clearCache(context) }
    }
}

@Composable
private fun RemoteConfigStatusLine(title: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
        Text(value, modifier = Modifier.weight(1.4f), textAlign = TextAlign.End, fontSize = 13.sp)
    }
}

private fun formatRemoteConfigTime(value: Long): String = if (value <= 0L) {
    "尚未"
} else {
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))
}

@Composable
private fun AboutSettings(context: android.content.Context) {
    val githubRouting = remember { MeloXGitHubRouting(context) }
    val updateClient = remember { MeloXUpdateClient(context, routing = githubRouting) }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var exportingLogs by remember { mutableStateOf(false) }
    var showLogExportInfo by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<MeloXRelease?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var versionTapCount by remember { mutableIntStateOf(0) }
    var lastVersionTapAt by remember { mutableStateOf(0L) }
    var showCatEgg by remember { mutableStateOf(false) }
    var downloadSource by remember { mutableStateOf(githubRouting.selectedSource()) }
    val exportLogsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) {
            exportingLogs = false
        } else {
            scope.launch {
                runCatching {
                    MeloXLogExporter.exportRecentLogs(context, uri)
                }.onSuccess { result ->
                    updateStatus = "已导出 MeloX 当前进程日志（${result.lineCount} 行）"
                }.onFailure { error ->
                    updateStatus = error.message ?: "日志导出失败"
                }
                exportingLogs = false
            }
        }
    }
    Box(
        modifier = Modifier.pointerInput(Unit) {
            var distance = 0f
            detectVerticalDragGestures(
                onDragStart = { distance = 0f },
                onVerticalDrag = { _, dragAmount ->
                    distance += dragAmount
                    if (distance > 180f) {
                        showCatEgg = true
                        distance = 0f
                    }
                },
            )
        },
    ) {
        SettingsGlassGroup {
            Column(Modifier.padding(18.dp)) {
            Text("MeloX Android", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "版本 ${BuildConfig.VERSION_NAME} · MeloX 的 Android 原生迁移版。",
                modifier = Modifier
                    .padding(top = 7.dp)
                    .clickable {
                        val now = System.currentTimeMillis()
                        versionTapCount = if (now - lastVersionTapAt < 2_000L) versionTapCount + 1 else 1
                        lastVersionTapAt = now
                        if (versionTapCount >= 7) {
                            showCatEgg = true
                            versionTapCount = 0
                        }
                    },
                color = MaterialTheme.colorScheme.onSurface.copy(alpha=.62f),
            )
            Text("Android 原生迁移与维护：lladlam", modifier = Modifier.padding(top=14.dp), fontWeight=FontWeight.SemiBold)
            Text("上游 iOS 原生项目：youshen2/MeloX（SwiftUI）", modifier = Modifier.padding(top=5.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha=.58f))
            }
        }
    }
    if (showCatEgg) {
        MeloXGlassDialog(visible = true, onDismiss = { showCatEgg = false }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                MeloXPinkCat()
                Text("MeloX 小猫出现了", style = MaterialTheme.typography.titleLarge)
                Text("粉色的音乐守护猫。", modifier = Modifier.padding(top = 7.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                MeloXGlassButton(
                    onClick = { showCatEgg = false },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    style = MeloXGlassButtonStyle.BorderedProminent,
                ) { Text("收下这只猫") }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    SettingsToggleRow(context, "自动检查更新", "update_auto_check", true, "应用启动后检查 GitHub 正式版本；不会自动下载安装。")
    Spacer(Modifier.height(10.dp))
    MeloXSettingsDropdown(
        title = "GitHub 访问源",
        selected = downloadSource,
        items = listOf(
            MeloXGitHubSource.Auto to "自动选择",
            MeloXGitHubSource.GitHubDoh to "GitHub DoH",
            MeloXGitHubSource.GhFast to "GhFast",
            MeloXGitHubSource.GhProxy to "GhProxy",
            MeloXGitHubSource.GhProxyOrg to "GhProxy.org（备用）",
        ),
        onSelected = {
            downloadSource = it
            githubRouting.selectSource(it)
        },
    )
    Text(
        githubRouting.effectiveRoute()?.takeIf { downloadSource == MeloXGitHubSource.Auto }?.let {
            "自动测速当前选择 ${it.source.label}（${it.latencyMs}ms）；更新检查、APK 下载与已授权的签名配置共用此源。"
        } ?: "自动模式会并行测速 GitHub DoH、GhFast、GhProxy 与 GhProxy.org，并选择当前最快的可用源。",
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
    )
    SettingsActionButton(if (checking) "正在检查…" else "检查更新") {
        if (!checking) scope.launch {
            checking = true
            runCatching { updateClient.latestStableRelease(forceSourceBenchmark = true) }
                .onSuccess {
                    release = it
                    updateStatus = if (updateClient.isNewer(it.version, BuildConfig.VERSION_NAME)) {
                        "发现新版本 ${it.version}：${it.name}"
                    } else {
                        "当前已是最新版本（${BuildConfig.VERSION_NAME}）"
                    }
                }
                .onFailure { updateStatus = it.message ?: "更新检查失败" }
            checking = false
        }
    }
    updateStatus?.let { message ->
        Spacer(Modifier.height(10.dp))
        Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
    }
    release?.takeIf { updateClient.isNewer(it.version, BuildConfig.VERSION_NAME) }?.let { available ->
        Spacer(Modifier.height(10.dp))
        SettingsActionButton(if (available.apkUrl != null) "下载 ${available.version} APK" else "打开 ${available.version} 发布页") {
            scope.launch {
                val target = runCatching { updateClient.downloadUrl(available) }.getOrNull() ?: available.pageUrl
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                    .onFailure { updateStatus = it.message ?: "无法打开下载链接" }
            }
        }
        if (available.notes.isNotBlank()) {
            Text(available.notes.take(700), modifier = Modifier.padding(top = 10.dp), fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
        }
    }
    Spacer(Modifier.height(14.dp))
    Spacer(Modifier.height(10.dp))
    SettingsActionButton("恢复推荐的播放器设置") {
        MeloXSettingsPreferences.resetRecommendedPlayerSettings(context)
        updateStatus = "已恢复推荐播放器、歌词与 AutoMix 配置"
    }
    Spacer(Modifier.height(14.dp))
    SettingsGlassGroup {
        Column(Modifier.padding(16.dp)) {
            Text("项目与许可", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "MeloX 主体：GNU GPLv3\n" +
                    "qier222/YesPlayMusic：网易云接口与播放器实现参考（MIT）\n" +
                    "jayfunc/BetterLyrics：逐字歌词渲染、光效与动效参考\n" +
                    "WXRIW/Lyricify-Lyrics-Helper：网易云 YRC 解析参考\n" +
                    "neteasecloudmusicapienhanced/api-enhanced：听歌识曲与音频指纹运行时\n" +
                    "DanteAlighieri13210914/pv-tool：文字 PV 原始实现（Non-Commercial License）\n" +
                    "mjhydri/BeatNet：自动混音节拍/重拍/速度分析（CC BY 4.0）\n" +
                    "NEORUAA/MeiloX：基于 Mei 的仿 Apple Music 网易云音乐客户端，提供 UI 参考\n" +
                    "thlucas1/SpotifyWebApiPython：Spotify Web API 客户端，提供 Spotify API 参考\n" +
                    "bromothymolb/bilibili-api-zoku：Bilibili API 调用整合项目，提供 Bilibili API 参考\n" +
                    "Kyant0 AndroidLiquidGlass / Backdrop：Android 液态玻璃渲染基础",
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    SettingsActionButton("打开 MeloX Android GitHub") {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lladlam/MeloX-Android"))) }
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton("查看上游 iOS MeloX") {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/youshen2/MeloX"))) }
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton("查看上游项目与许可") {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/youshen2/MeloX/blob/main/MeloX/Features/Legal/ProjectLicensesView.swift"))) }
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton("加入QQ群") {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://qm.qq.com/q/wbhFQxj7mo"))) }
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton("赞助我") {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ifdian.net/a/lladlam"))) }
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton(if (exportingLogs) "正在导出日志…" else "导出 MeloX 全部日志") {
        if (!exportingLogs) showLogExportInfo = true
    }

    if (showLogExportInfo) {
        val deviceInfo: MeloXLogDeviceInfo = MeloXLogExporter.collectDeviceInfo(context)
        MeloXGlassDialog(
            visible = true,
            onDismiss = { showLogExportInfo = false },
        ) {
            Text("导出 MeloX 全部日志", style = MaterialTheme.typography.titleMedium)
            Text(
                "导出当前 MeloX 进程可读取的全部日志，不跳转到外部日志页面；并会附带这些设备与登录状态信息。",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .64f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .045f))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Android 版本：${deviceInfo.androidVersion}", fontSize = 13.sp)
                Text("手机型号：${deviceInfo.phoneModel}", fontSize = 13.sp)
                Text("系统版本：${deviceInfo.systemVersion}", fontSize = 13.sp)
                Text(
                    "已登录音乐源：${deviceInfo.loggedMusicSources.takeIf { it.isNotEmpty() }?.joinToString("、") ?: "无"}",
                    fontSize = 13.sp,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsActionButton("取消", Modifier.weight(1f)) { showLogExportInfo = false }
                SettingsActionButton("选择导出位置", Modifier.weight(1f)) {
                    showLogExportInfo = false
                    exportingLogs = true
                    exportLogsLauncher.launch("MeloX-logs-${System.currentTimeMillis()}.txt")
                }
            }
        }
    }
}

@Composable
private fun DeveloperSettings() {
    val context = LocalContext.current
    var diagnosticsVisible by remember {
        mutableStateOf(BuildConfig.DEBUG && MeloXSettingsPreferences.boolean(context, "developer_automix_diagnostics", false))
    }
    var diagnostics by remember { mutableStateOf(MeloXAutoMixDiagnostics.snapshot()) }
    if (BuildConfig.DEBUG) {
        SettingsToggleRow(
            context = context,
            title = "帧率与帧时间悬浮层",
            key = "developer_performance_overlay",
            default = false,
            note = "仅 Debug 构建可用；每 5 秒写入一次 melox_perf.log。",
        )
        SettingsExternalToggleRow(
            title = "AutoMix / Beat 分析诊断",
            value = diagnosticsVisible,
            note = "仅 Debug 构建显示分析数量、缓存与最近状态，不记录音频内容。",
        ) {
            diagnosticsVisible = it
            MeloXSettingsPreferences.setBoolean(context, "developer_automix_diagnostics", it)
        }
        if (diagnosticsVisible) {
            Spacer(Modifier.height(8.dp))
            SettingsActionButton("刷新分析状态") { diagnostics = MeloXAutoMixDiagnostics.snapshot() }
            Spacer(Modifier.height(10.dp))
        }
    }
    Spacer(Modifier.height(10.dp))
}

private val LocalSettingsGroupedRows = staticCompositionLocalOf { false }
internal val LocalSettingsGroupRowIndex = staticCompositionLocalOf { mutableIntStateOf(0) }

@Composable
private fun SettingsToggleRow(
    context: android.content.Context,
    title: String,
    key: String,
    default: Boolean,
    note: String? = null,
    grouped: Boolean = false,
    showTopSeparator: Boolean = false,
) {
    var value by remember(key) { mutableStateOf(MeloXSettingsPreferences.boolean(context, key, default)) }
    val groupRowIndex = LocalSettingsGroupRowIndex.current
    val effectiveShowTopSeparator = when {
        showTopSeparator -> true
        grouped -> groupRowIndex.intValue++.let { it > 0 }
        else -> false
    }
    @Composable fun row() {
        MeloXIosListRow(
            title = title,
            trailing = {
                MeloXGlassToggle(checked = value, onCheckedChange = {
                    value = it
                    MeloXSettingsPreferences.setBoolean(context, key, it)
                })
            },
            showTopSeparator = effectiveShowTopSeparator,
        )
    }
    if (grouped || LocalSettingsGroupedRows.current) row() else {
        SettingsGlassGroup { row() }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SettingsExternalToggleRow(
    title: String,
    value: Boolean,
    note: String? = null,
    grouped: Boolean = false,
    onValueChange: (Boolean) -> Unit,
) {
    val groupRowIndex = LocalSettingsGroupRowIndex.current
    val effectiveShowTopSeparator = if (grouped) groupRowIndex.intValue++.let { it > 0 } else false
    val row = @Composable {
        MeloXIosListRow(
            title = title,
            trailing = { MeloXGlassToggle(checked = value, onCheckedChange = onValueChange) },
            showTopSeparator = effectiveShowTopSeparator,
        )
    }
    if (grouped) {
        row()
    } else {
        SettingsGlassGroup { row() }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SettingsChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), fontSize = 16.sp)
        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsGlassGroup(content: @Composable ColumnScope.() -> Unit) {
    val rowIndex = remember { mutableIntStateOf(0) }
    rowIndex.intValue = 0
    CompositionLocalProvider(LocalSettingsGroupRowIndex provides rowIndex) {
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface, content = content)
    }
}

@Composable
private fun SettingsInfoCard(value: String) {
    SettingsGlassGroup {
        Text(
            value,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f),
        )
    }
}

@Composable
private fun SettingsActionButton(title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(50.dp).meloXLiquidButton(
            shape = RoundedCornerShape(25.dp),
            surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .06f),
        ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(title, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun SettingsRoundButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).meloXLiquidButton(shape = CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { MeloXActionIcon(text, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurface) }
}

@Composable
private fun SettingsResetCard() {
    val context = LocalContext.current
    SettingsDangerButton("恢复播放器默认设置") {
        MeloXSettingsPreferences.reset(context)
        MeloXPlaybackModePreferences.reset(context)
        PlaybackCommands.changeQuality(context, MusicQuality.Standard)
    }
}

@Composable
private fun SettingsDangerButton(title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(54.dp).meloXLiquidButton(
            shape = RoundedCornerShape(27.dp),
            tint = MaterialTheme.colorScheme.error.copy(alpha = .25f),
            surfaceColor = MaterialTheme.colorScheme.error.copy(alpha = .20f),
        ).clickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) { Text(title, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
}
