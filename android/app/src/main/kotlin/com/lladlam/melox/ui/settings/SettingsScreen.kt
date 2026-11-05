package com.lladlam.melox.ui.settings

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.ui.animation.MeloXPageSpec
import com.lladlam.melox.ui.MeloXInterruptiblePage
import com.lladlam.melox.ui.meloXPageRecede
import androidx.compose.ui.input.pointer.pointerInput
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
import com.lladlam.melox.core.music.provider.MeloXUnifiedFavorites
import com.lladlam.melox.core.music.provider.ProviderAccountManager
import com.lladlam.melox.core.music.provider.UserLibraryCapability
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.provider.qqmusic.QQMusicSessionStore
import com.lladlam.melox.core.provider.kugou.KugouSessionStore
import com.lladlam.melox.ui.account.QQMusicLoginScreen
import com.lladlam.melox.ui.account.KugouLoginScreen
import com.lladlam.melox.ui.account.MeloXNeteaseLoginActivity
import com.lladlam.melox.playback.ProviderPlaybackQualityRuntime
import com.lladlam.melox.playback.CrossProviderPlaybackPreferences
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
import com.lladlam.melox.core.update.MeloXDevCommit
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
import com.lladlam.melox.ui.hotStringResource
import com.lladlam.melox.ui.hotText
import com.lladlam.melox.ui.glass.MeloXActionIcon
import com.lladlam.melox.ui.glass.MeloXGlassTextField
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassToggle
import com.lladlam.melox.ui.glass.MeloXLiquidSlider
import com.lladlam.melox.ui.glass.MeloXSettingsDropdown
import com.lladlam.melox.ui.glass.MeloXShapes
import com.lladlam.melox.ui.glass.MeloXTypography
import com.lladlam.melox.ui.glass.MeloXIosGroupedList
import com.lladlam.melox.ui.glass.MeloXIosListRow
import com.lladlam.melox.ui.glass.MeloXIosTopBar
import com.lladlam.melox.ui.glass.MeloXPinnedListPage
import com.lladlam.melox.ui.layout.rememberMeloXStableStatusBarHeight
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
import com.lladlam.melox.lan.MeloXLanPermissions
import com.lladlam.melox.lan.MeloXLanRuntime
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigSource
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigConsent
import com.lladlam.melox.ui.legal.MeloXCloudControlConsentDialog
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

// ⚠ 必须 public：`MeloXSettingsDetailActivity.launch(context, route, source)` 是 public 的
//   Activity 入口，签名里出现 internal 类型会直接编译不过（public 函数暴露 internal 类型）。
enum class SettingsRoute(private val fallbackTitle: String, private val titleRes: Int = 0) {
    Playback("播放", R.string.settings_route_playback),
    PlayerAppearance("外观", R.string.settings_route_appearance),
    Lyrics("歌词", R.string.settings_route_lyrics),
    SystemPlayback("系统歌词显示", R.string.settings_route_system_playback),
    SkylineLyrics("全屏天际歌词", R.string.settings_route_skyline),
    FloatingLyrics("悬浮窗歌词", R.string.settings_route_floating),
    RearDisplay("背屏控制"),
    Plugins("音乐源插件"),
    ContentFeatures("功能模块", R.string.settings_route_features),
    Blocklist("屏蔽管理"),
    Recognition("听歌识曲", R.string.settings_route_recognition),
    Messages("私信与站内分享", R.string.settings_route_messages),
    ListenTogether("一起听", R.string.settings_route_listen_together),
    KugouLiteVip("签到领会员"),
    Content("内容", R.string.settings_route_content),
    Storage("存储管理", R.string.settings_route_storage),
    TabLayout("页面与标签栏", R.string.settings_route_tabs),
    General("通用", R.string.settings_route_general),
    RemoteConfig("远程兼容性配置", R.string.settings_route_remote_config),
    About("关于 MeloX", R.string.settings_route_about),
    Legal("隐私政策与免责声明", R.string.settings_route_legal),
    Privacy("隐私与本地算法", R.string.settings_route_privacy),
    Developer("开发者选项", R.string.settings_route_developer),
    Experimental("测试功能尝鲜", R.string.settings_route_experimental);

    val title: String
        @Composable get() = if (titleRes == 0) fallbackTitle else stringResource(titleRes)
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
        SettingsItem(SettingsRoute.General, "语言、主题、启动行为与链接处理", "⚙", "语言 language 简体 繁體 English 日本語 Türkçe 主题 浅色 深色 跟随系统 默认页面 剪贴板 分屏 split screen 双栏 底部栏 导航栏 迷你播放器 miniplayer 左下 右下 左边 右边 左上 右上"),
        SettingsItem(SettingsRoute.PlayerAppearance, "背景、封面动画与屏幕常亮", "✦", "模糊 色彩 饱和度 封面 自动锁屏"),
        SettingsItem(SettingsRoute.Content, "地区、歌单信息和发现内容", "▦", "华语 欧美 韩国 日本 播放量 内容"),
        SettingsItem(SettingsRoute.Playback, "音质、播放行为与自动混音", "♫", "高品质 无损 上一首 页面记忆 心动模式 交叉淡化 播放"),
        SettingsItem(SettingsRoute.Lyrics, "翻译、罗马音、逐字与歌词交互", "❞", "Apple Music EVA 文字PV 字体 YRC 翻译 罗马音 歌词"),
        SettingsItem(SettingsRoute.Storage, "空间统计、下载与缓存清理", "▰", "下载 存储 缓存 清理 数据库"),
    )),
    SettingsSection("扩展", listOf(
        SettingsItem(SettingsRoute.Plugins, "导入和管理动态音乐源插件（开发中）", "⊞", "插件 plugin 音源 导入 SDK"),
        SettingsItem(SettingsRoute.ContentFeatures, "播客、云盘、最近播放等模块", "☷", "播客 广播 云盘 最近播放 下载"),
        SettingsItem(SettingsRoute.Recognition, "麦克风音频指纹与持续识别", "⌁", "听歌识曲 麦克风 指纹 Shazam 持续识别"),
        SettingsItem(SettingsRoute.Messages, "联系人、会话历史与文字私信", "✉", "私信 联系人 会话 分享 网易云"),
        SettingsItem(SettingsRoute.ListenTogether, "创建、加入和管理网易云一起听房间", "◎", "一起听 房间 邀请 同步"),
        SettingsItem(SettingsRoute.KugouLiteVip, "酷狗概念版每日签到与会员领取", "✦", "酷狗 概念版 签到 领取 会员 VIP 畅听"),
        SettingsItem(SettingsRoute.TabLayout, "首页、标签栏与音乐库页面", "▥", "首页 标签栏 排序 推荐 歌单 历史"),
        SettingsItem(SettingsRoute.SystemPlayback, "通知、锁屏和系统媒体信息", "▣", "控制中心 通知 锁屏 Media3"),
        SettingsItem(SettingsRoute.FloatingLyrics, "Android 悬浮歌词能力与权限", "▤", "画中画 悬浮窗 其他应用"),
        SettingsItem(SettingsRoute.RearDisplay, "下载实验性小米背屏控制应用", "▣", "小米 背屏 副屏 HyperOS Rear 下载"),
    )),
    SettingsSection("关于", listOf(
        SettingsItem(SettingsRoute.RemoteConfig, "签名配置状态、平台熔断声明与本地缓存", "⌁", "远程 配置 云控 签名 熔断 兼容 GitHub"),
        SettingsItem(SettingsRoute.About, "版本、项目主页与开源信息", "ⓘ", "GitHub 更新 开源 许可"),
        SettingsItem(SettingsRoute.Legal, "查看隐私政策、免责声明与同意版本", "▤", "隐私 政策 免责声明 法律 条款 数据"),
        SettingsItem(SettingsRoute.Developer, "播放器诊断与迁移状态", "⌘", "BeatNet 节拍 调试 日志"),
        SettingsItem(SettingsRoute.Experimental, "预览尚未稳定的新功能", "✦", "实验 测试 歌词 强绑定"),
    )),
)

/**
 * 设置**主页**（列表 + 搜索）。
 *
 * ⚠⚠ 2026-10-02：详情页**不再**在这里以覆盖层形式推出，改成启动
 * [MeloXSettingsDetailActivity]（真实 Activity）⇒ 整页推入/退回的转场由平台负责。
 * 原先那套（`MeloXInterruptiblePage` + `AnimatedContent` 弹簧位移 + `meloXPageRecede`
 * 让列表页退让 + 自研 `PredictiveBackHandler` + 延后一帧可见）**整块删掉**，原因见
 * `ui/MeloXPageTransitions.kt` 里 `MeloXPageHost` 的说明：应用内重放系统已经做得更好的事，
 * 结果是转场被组合/绘制帧牵连，快速连续打断必然顿挫。
 *
 * ⚠ 深链（原来靠 `initialRouteRequest` 传进来的那些）现在同样直接启动
 * [MeloXSettingsDetailActivity]，见 `MeloXApp` 里 `settingsRouteRequest` 的两个调用点。
 */
@Composable
fun SettingsScreen(
    session: NeteaseSessionStore,
    source: MusicSource = MusicSource.Netease,
    onLogin: () -> Unit,
    onOpenAccount: (() -> Unit)? = null,
    onOpenServices: (() -> Unit)? = null,
    onOpenMessages: (() -> Unit)? = null,
    sourceId: com.lladlam.melox.core.music.model.SourceId = com.lladlam.melox.core.music.model.SourceId.fromBuiltIn(source),
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    // 设置主页的滚动位置：详情页现在是另一个 Activity，本组合在它盖住期间仍然存活，
    // 退回时列表位置自然保持（`rememberScrollState` 由 `rememberSaveable` 兜底）。
    val rootScrollState = rememberScrollState()

    LaunchedEffect(Unit) { MeloXSettingsPreferences.initialize(context) }
    LaunchedEffect(Unit) {
        if (LocalRecommendationStore.isAlgorithmEnabled(context) && LocalRecommendationStore.hasPersonalizationConsent(context)) {
            LocalRecommendationEngine.start(context)
        }
    }
    LaunchedEffect(session.cookie) {
        if (session.isLoggedIn) session.refreshProfile()
    }

    val normalized = search.trim().lowercase()
    val visibleSections = SettingsSections.mapNotNull { section ->
        val filtered = section.items.filter { item ->
            val supported = when (item.route) {
                SettingsRoute.Messages, SettingsRoute.ListenTogether -> sourceId.builtIn == MusicSource.Netease
                SettingsRoute.KugouLiteVip -> sourceId.builtIn == MusicSource.KugouLite
                else -> true
            }
            supported && (normalized.isBlank() || listOf(item.route.title, item.subtitle, item.keywords)
                .joinToString(" ").lowercase().contains(normalized))
        }
        filtered.takeIf { it.isNotEmpty() }?.let { SettingsSection(section.title, it) }
    }

    // ⚠ 2026-09-29：状态栏避让必须放在 `verticalScroll` **之后**（即滚动内容的内边距里）。
    //   原来 `.statusBarsPadding()` 打在 `verticalScroll` 之前 ⇒ 滚动视口被压到状态栏下缘
    //   (41dp)，滚上去的内容一律在 y=41dp 被裁掉、**永远进不了状态栏/挖孔区域**。
    //   改成内边距后静止态位置不变（18+41），滚动时头部从状态栏底下穿出。
    val statusBarTop = rememberMeloXStableStatusBarHeight()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rootScrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp + statusBarTop, bottom = MeloXBottomContentClearance),
    ) {
        MeloXIosTopBar(
            title = stringResource(R.string.tab_settings),
            modifier = Modifier.padding(horizontal = 0.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        )
        Spacer(Modifier.height(14.dp))
        SettingsSearchField(value = search, onValueChange = { search = it })
        Spacer(Modifier.height(20.dp))

        if (normalized.isBlank() || stringResource(R.string.settings_account_keywords).contains(normalized)) {
            if (sourceId.builtIn == null) com.lladlam.melox.ui.provider.PluginServiceSection() else SettingsAccountCard(
                session = session,
                onLogin = onLogin,
                onOpenAccount = onOpenAccount,
                onOpenServices = onOpenServices,
            )
            Spacer(Modifier.height(24.dp))
        }

        visibleSections.forEach { section ->
            Text(
                stringResource(when (section.title) {
                    "应用" -> R.string.settings_section_app
                    "扩展" -> R.string.settings_section_more
                    else -> R.string.settings_section_about
                }),
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 8.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            SettingsSectionCard(section) { selected ->
                if (selected == SettingsRoute.Messages && onOpenMessages != null) {
                    onOpenMessages()
                } else {
                    // 详情页是**独立 Activity** ⇒ 整页推入转场由平台负责（可连续打断、
                    // 带系统屏幕圆角、支持预测式返回）。见 [MeloXSettingsDetailActivity]。
                    MeloXSettingsDetailActivity.launch(context, selected, source)
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        if (visibleSections.isEmpty() && normalized.isNotBlank()) {
            Text(
                stringResource(R.string.settings_search_empty),
                modifier = Modifier.padding(vertical = 36.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
            )
        }

        if (normalized.isBlank()) {
            SettingsResetCard()
            Spacer(Modifier.height(18.dp))
            if (session.isLoggedIn) {
                SettingsDangerButton(stringResource(R.string.settings_sign_out)) { session.clear() }
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
        placeholder = { Text(stringResource(R.string.settings_search_placeholder), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f), fontSize = 16.sp) },
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
        stringResource(R.string.accessibility_account),
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
                    detail = stringResource(R.string.provider_logged_in),
                    chevronTint = accent,
                     onClick = onOpenAccount ?: onLogin,
                    showTopSeparator = false,
                )
            }
            session.isLoggedIn && session.isRefreshing -> MeloXIosListRow(
                title = stringResource(R.string.settings_account_loading),
                leading = { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = accent) },
                onClick = onOpenServices,
                showTopSeparator = false,
            )
            else -> MeloXIosListRow(
                title = stringResource(R.string.app_login_netease),
                leading = { MeloXSymbolIcon(MeloXSymbol.Person, Modifier.size(30.dp), accent, MeloXSymbolVariant.Fill) },
                chevronTint = accent,
                 onClick = onOpenAccount ?: onLogin,
                showTopSeparator = false,
            )
        }
        if (onOpenServices != null) {
            MeloXIosListRow(
                title = stringResource(R.string.tab_services),
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
internal fun SettingsDetailScreen(route: SettingsRoute, source: MusicSource, session: NeteaseSessionStore, onBack: () -> Unit) {
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
                    SettingsRoute.FloatingLyrics -> FloatingLyricsSettings(context)
                    SettingsRoute.ContentFeatures -> ContentFeatureSettings(context)
                    SettingsRoute.Recognition -> MeloXRecognitionPanel(context)
                    SettingsRoute.Messages -> MessagesSettings(context)
                    SettingsRoute.ListenTogether -> ListenTogetherSettings(context)
                    SettingsRoute.KugouLiteVip -> if (source == MusicSource.KugouLite) KugouLiteVipSettings()
                    SettingsRoute.Content -> ContentSettings(context)
                    SettingsRoute.Storage -> StorageSettings(context)
                    SettingsRoute.TabLayout -> TabLayoutSettings(context)
                    SettingsRoute.General -> GeneralSettings(context)
                    SettingsRoute.RemoteConfig -> RemoteConfigSettings()
                    SettingsRoute.About -> AboutSettings(context)
                    SettingsRoute.RearDisplay -> RearDisplaySettings(context)
                    SettingsRoute.Plugins -> PluginSettings()
                    SettingsRoute.Legal -> LegalSettings(context)
                    SettingsRoute.Privacy -> PrivacySettings(context)
                    SettingsRoute.Developer -> DeveloperSettings()
                    SettingsRoute.Experimental -> ExperimentalSettings(context, source, session)
                    else -> {}
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
            Text(stringResource(R.string.settings_legal_documents), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "当前文本版本：$MELOX_LEGAL_VERSION",
                modifier = Modifier.padding(top = 7.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Text(
                text = if (consentVersion.isBlank()) {
                    stringResource(R.string.settings_legal_no_consent)
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
            title = stringResource(R.string.legal_privacy),
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
            title = stringResource(R.string.settings_legal_disclaimer),
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
            title = stringResource(R.string.legal_cloud),
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
            title = stringResource(R.string.settings_legal_third_party),
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
            title = stringResource(R.string.settings_legal_remote_toggle),
            value = cloudControlEnabled,
            note = stringResource(R.string.settings_legal_remote_note),
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
        stringResource(R.string.settings_legal_remote_footer),
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
            Text(stringResource(R.string.settings_privacy_data_title), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.settings_privacy_data_body), modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f), fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        SettingsExternalToggleRow(stringResource(R.string.settings_privacy_algorithm), algorithmEnabled, stringResource(R.string.settings_privacy_algorithm_note)) {
            algorithmEnabled = it
            LocalRecommendationStore.setAlgorithmEnabled(context, it)
            if (it && consent) LocalRecommendationEngine.start(context) else if (!it) LocalRecommendationEngine.stop()
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        Text(stringResource(R.string.settings_privacy_personalization), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(if (consent) stringResource(R.string.settings_privacy_consented) else stringResource(R.string.settings_privacy_not_consented), modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
        SettingsActionButton(if (consent) stringResource(R.string.settings_privacy_withdraw) else stringResource(R.string.settings_privacy_read_policy)) {
            if (consent) {
                consent = false
                LocalRecommendationStore.clearPersonalization(context)
                LocalRecommendationEngine.stop()
            } else showPolicy = true
        }
        SettingsActionButton(stringResource(R.string.settings_privacy_clear_all)) { showClearPersonalization = true }
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_privacy_status), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
    SettingsActionButton(stringResource(R.string.settings_privacy_start_full)) {
        if (algorithmEnabled && consent) LocalRecommendationEngine.startFullAnalysis(context)
    }
    if (showPolicy) {
        MeloXGlassDialog(visible = true, onDismiss = { if (secondsLeft == 0) showPolicy = false }) {
            Text(stringResource(R.string.settings_privacy_policy_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_privacy_policy_body), modifier = Modifier.padding(top = 10.dp), fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(16.dp))
            SettingsActionButton(if (secondsLeft > 0) "请阅读后等待 ${secondsLeft} 秒" else stringResource(R.string.legal_agree_enable)) {
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
            Text(stringResource(R.string.settings_privacy_clear_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_privacy_clear_body), modifier = Modifier.padding(top = 10.dp), fontSize = 13.sp, lineHeight = 19.sp)
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionButton(stringResource(R.string.action_cancel), Modifier.weight(1f)) { showClearPersonalization = false }
                SettingsDangerButton(stringResource(R.string.settings_privacy_clear_confirm), Modifier.weight(1f)) {
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
            title = stringResource(R.string.settings_system_lyrics),
            value = systemLyrics,
            note = stringResource(R.string.settings_system_lyrics_note),
            grouped = true,
        ) {
            systemLyrics = it
            MeloXSettingsPreferences.setBoolean(context, "system_lyrics_enabled", it)
        }
        val protocol = remember { HyperOsFocusBridge.protocol(context) }
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_system_title_format),
            selected = MeloXSettingsRuntime.systemLyricTitleMode,
            items = listOf(
                MeloXSystemLyricTitleMode.LyricFirst to stringResource(R.string.settings_system_title_lyric),
                MeloXSystemLyricTitleMode.SongFirst to stringResource(R.string.settings_system_title_song),
            ),
            onSelected = { MeloXSettingsPreferences.setString(context, "system_lyrics_title_mode", it.name) },
            grouped = true,
        )
        SettingsExternalToggleRow(
            title = stringResource(R.string.settings_lyric_notification),
            value = notifications,
            note = stringResource(R.string.settings_lyric_notification_note),
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
        val hyperIsland = remember { mutableStateOf(MeloXSettingsPreferences.boolean(context, "hyperos_super_island_enabled", false)) }
        SettingsExternalToggleRow(
            title = stringResource(R.string.settings_hyperos_island),
            value = hyperIsland.value,
            note = stringResource(R.string.settings_hyperos_island_note),
            grouped = true,
        ) { enabled ->
            hyperIsland.value = enabled
            MeloXSettingsPreferences.setBoolean(context, "hyperos_super_island_enabled", enabled)
            if (!enabled) {
                runCatching { HyperOsFocusBridge.clearSuperIsland(context) }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        val notificationActions = listOf(
            com.lladlam.melox.playback.MeloXNotificationAction.PlayMode to stringResource(R.string.notification_mode_shuffle),
            com.lladlam.melox.playback.MeloXNotificationAction.Favorite to stringResource(R.string.notification_action_favorite),
            com.lladlam.melox.playback.MeloXNotificationAction.FloatingLyrics to stringResource(R.string.notification_action_floating_lyrics),
            com.lladlam.melox.playback.MeloXNotificationAction.None to stringResource(R.string.settings_notification_none),
        )
        var leftAction by remember {
            mutableStateOf(
                com.lladlam.melox.playback.MeloXNotificationAction.fromPreference(
                    MeloXSettingsPreferences.string(context, "notification_left_action", "").takeIf { it.isNotBlank() },
                    com.lladlam.melox.playback.MeloXNotificationAction.PlayMode,
                ),
            )
        }
        var rightAction by remember {
            mutableStateOf(
                com.lladlam.melox.playback.MeloXNotificationAction.fromPreference(
                    MeloXSettingsPreferences.string(context, "notification_right_action", "").takeIf { it.isNotBlank() },
                    com.lladlam.melox.playback.MeloXNotificationAction.Favorite,
                ),
            )
        }
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_notification_left_action),
            selected = leftAction,
            items = notificationActions,
            onSelected = {
                leftAction = it
                MeloXSettingsPreferences.setString(context, "notification_left_action", it.name)
            },
            grouped = true,
        )
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_notification_right_action),
            selected = rightAction,
            items = notificationActions,
            onSelected = {
                rightAction = it
                MeloXSettingsPreferences.setString(context, "notification_right_action", it.name)
            },
            grouped = true,
        )
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        SettingsToggleRow(context, stringResource(R.string.settings_notify_next_line), "lyrics_notification_next_line", false, grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_notify_progress), "lyrics_notification_progress", true, grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_notify_artwork), "lyrics_notification_artwork", true, grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_notify_background_only), "lyrics_notification_background_only", false, grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_notify_dismiss_paused), "lyrics_notification_dismiss_paused", true, grouped = true)
    }
    NotificationTemplateField(context, stringResource(R.string.settings_notify_title_template), "lyrics_notification_title_template", "{lyric}")
    NotificationTemplateField(context, stringResource(R.string.settings_notify_subtitle_template), "lyrics_notification_subtitle_template", "{song} · {artist}")
    NotificationTemplateField(context, stringResource(R.string.settings_notify_fallback), "lyrics_notification_fallback", "{song} · {artist}")
    Text(
        stringResource(R.string.settings_notify_variables),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f),
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 10.dp),
    )
    SettingsActionButton(stringResource(R.string.settings_notify_test)) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel("melox_lyrics", context.getString(R.string.settings_notify_channel_lyrics), NotificationManager.IMPORTANCE_LOW))
        }
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
    Spacer(Modifier.height(10.dp))
    LanSettings(context)
}

@Composable
private fun LanSettings(context: android.content.Context) {
    var enabled by remember { mutableStateOf(MeloXLanRuntime.isEnabled(context)) }
    var port by remember { mutableStateOf(MeloXLanRuntime.port(context)) }
    val lanPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            enabled = true
            MeloXLanRuntime.setEnabled(context, true)
            port = MeloXLanRuntime.port(context)
        }
    }
    var pin by remember { mutableStateOf(MeloXLanRuntime.pin(context)) }
    var trusted by remember { mutableStateOf(MeloXLanRuntime.trustedDevices(context)) }
    var recent by remember { mutableStateOf(MeloXLanRuntime.recentPeers(context)) }
    var showPortDialog by remember { mutableStateOf(false) }
    var portText by remember { mutableStateOf(port.toString()) }
    SettingsGlassGroup {
        SettingsExternalToggleRow(
            title = stringResource(R.string.settings_lan),
            value = enabled,
            note = stringResource(R.string.settings_lan_note),
            grouped = true,
        ) { value ->
            if (value && !MeloXLanPermissions.has(context)) {
                enabled = false
                MeloXLanRuntime.setEnabled(context, false)
                lanPermissionLauncher.launch(MeloXLanPermissions.permission())
            } else {
                enabled = value
                MeloXLanRuntime.setEnabled(context, value)
                port = MeloXLanRuntime.port(context)
            }
        }
        MeloXIosListRow(
            title = stringResource(R.string.settings_lan_port),
            subtitle = port.toString(),
            onClick = {
                portText = port.toString()
                showPortDialog = true
            },
            showTopSeparator = true,
        )
        MeloXIosListRow(
            title = stringResource(R.string.settings_lan_pin),
            subtitle = pin,
            trailing = {
                Text(
                    stringResource(R.string.settings_lan_pin_refresh),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable {
                        pin = MeloXLanRuntime.regeneratePin(context)
                    },
                )
            },
            showTopSeparator = true,
        )
        if (trusted.isEmpty()) {
            MeloXIosListRow(
                title = stringResource(R.string.settings_lan_trusted),
                subtitle = stringResource(R.string.settings_lan_no_trusted),
                showTopSeparator = true,
            )
        } else {
            trusted.forEach { device ->
                val id = device.optString("deviceId")
                val name = device.optString("name").ifBlank { device.optString("platform") }
                MeloXIosListRow(
                    title = name.ifBlank { stringResource(R.string.settings_lan_trusted) },
                    subtitle = device.optString("platform"),
                    trailing = {
                        Text(
                            stringResource(R.string.settings_lan_remove_trusted),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                MeloXLanRuntime.removeTrusted(context, id)
                                trusted = MeloXLanRuntime.trustedDevices(context)
                            },
                        )
                    },
                    showTopSeparator = true,
                )
            }
        }
        val recentToAdd = recent.filter { peer ->
            trusted.none { it.optString("deviceId") == peer.optString("deviceId") }
        }
        if (recentToAdd.isNotEmpty()) {
            MeloXIosListRow(
                title = stringResource(R.string.settings_lan_recent),
                subtitle = null,
                showTopSeparator = true,
            )
            recentToAdd.forEach { peer ->
                val id = peer.optString("deviceId")
                val name = peer.optString("name").ifBlank { peer.optString("platform") }
                val platform = peer.optString("platform")
                MeloXIosListRow(
                    title = name.ifBlank { stringResource(R.string.settings_lan_recent) },
                    subtitle = platform,
                    trailing = {
                        Text(
                            stringResource(R.string.settings_lan_add_trusted),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                MeloXLanRuntime.trustPeer(context, id, name, platform)
                                trusted = MeloXLanRuntime.trustedDevices(context)
                                recent = MeloXLanRuntime.recentPeers(context)
                            },
                        )
                    },
                    showTopSeparator = true,
                )
            }
        }
    }
    if (showPortDialog) {
        MeloXGlassDialog(visible = true, onDismiss = { showPortDialog = false }) {
            Text(stringResource(R.string.settings_lan_port), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.settings_lan_port_note),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            MeloXGlassTextField(
                value = portText,
                onValueChange = { value -> portText = value.filter(Char::isDigit).take(5) },
                placeholder = { Text("47831") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionButton(stringResource(R.string.action_cancel), Modifier.weight(1f)) {
                    showPortDialog = false
                }
                SettingsActionButton(stringResource(R.string.action_done), Modifier.weight(1f)) {
                    val requested = portText.toIntOrNull()
                    if (requested != null && requested in 1024..65535) {
                        port = MeloXLanRuntime.setPort(context, requested)
                    }
                    showPortDialog = false
                }
            }
        }
    }
}

@Composable
private fun ExperimentalSettings(context: android.content.Context, source: MusicSource, session: NeteaseSessionStore) {
    var cliEnabled by remember { mutableStateOf(MeloXSettingsPreferences.boolean(context, "experimental_cli_enabled", false)) }
    SettingsGlassGroup {
        SettingsExternalToggleRow(
            title = "CLI 模式",
            value = cliEnabled,
            note = "通过电脑 ADB 命令查询状态、控制播放和搜索歌曲。默认关闭，仅允许 ADB shell 调用。",
            grouped = true,
        ) { enabled ->
            cliEnabled = enabled
            MeloXSettingsPreferences.setBoolean(context, "experimental_cli_enabled", enabled)
        }
    }
    if (cliEnabled) {
        SettingsInfoCard("电脑运行：python3 tools/melox_cli.py status\n支持 play / pause / next / previous / seek / queue / search / search-play；完整说明见 docs/CLI.md。")
    }
    Spacer(Modifier.height(10.dp))
    var playbackEnabled by remember { mutableStateOf(PlaybackAccountStore.isEnabled(context)) }
    var showQQLogin by remember { mutableStateOf(false) }
    var showClearPlaybackConfirmation by remember { mutableStateOf(false) }
    var showClearLyricBindingsConfirmation by remember { mutableStateOf(false) }
    var showClearBilibiliAssociationsConfirmation by remember { mutableStateOf(false) }
    var bilibiliLyricAlignment by remember(source) {
        mutableStateOf(MeloXSettingsPreferences.boolean(context, "bilibili_lyric_audio_alignment", false))
    }
    var refreshRevision by remember { mutableIntStateOf(0) }
    var autoSyncEnabled by remember(source) {
        mutableStateOf(MeloXSettingsRuntime.autoSyncWordLyricsEnabled)
    }
    SettingsGlassGroup {
        SettingsExternalToggleRow(
            title = hotStringResource(R.string.settings_autosync_word),
            value = autoSyncEnabled,
            note = hotStringResource(R.string.settings_autosync_word_note),
            grouped = true,
        ) { enabled ->
            autoSyncEnabled = enabled
            MeloXSettingsPreferences.setBoolean(context, "autosync_word_lyrics_enabled", enabled)
        }
    }
    Spacer(Modifier.height(10.dp))
    if (autoSyncEnabled) {
        SettingsGlassGroup { AutoSyncModelPanel(context) }
        Spacer(Modifier.height(10.dp))
    }

    val neteaseLoginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) refreshRevision++
    }

    if (source == MusicSource.Bilibili) {
        SettingsGlassGroup {
            SettingsExternalToggleRow(
                title = stringResource(R.string.settings_bilibili_align),
                value = bilibiliLyricAlignment,
                grouped = true,
            ) { enabled ->
                bilibiliLyricAlignment = enabled
                MeloXSettingsPreferences.setBoolean(context, "bilibili_lyric_audio_alignment", enabled)
            }
        }
        SettingsInfoCard(stringResource(R.string.settings_bilibili_align_warning))
        Spacer(Modifier.height(10.dp))
        SettingsActionButton(stringResource(R.string.settings_bilibili_clear)) { showClearBilibiliAssociationsConfirmation = true }
        Spacer(Modifier.height(10.dp))
    }

    if (showClearBilibiliAssociationsConfirmation) {
        MeloXGlassDialog(visible = true, onDismiss = { showClearBilibiliAssociationsConfirmation = false }) {
            Text(stringResource(R.string.settings_bilibili_clear_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.settings_bilibili_clear_body), Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionButton(stringResource(R.string.action_cancel), Modifier.weight(1f)) { showClearBilibiliAssociationsConfirmation = false }
                SettingsActionButton(stringResource(R.string.provider_clear), Modifier.weight(1f)) {
                    BilibiliPlaybackAssociationStore.clear(context)
                    showClearBilibiliAssociationsConfirmation = false
                }
            }
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

    SettingsGlassGroup {
        SettingsExternalToggleRow(
            title = stringResource(R.string.settings_playback_second_account),
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
            SettingsInfoCard(stringResource(R.string.settings_playback_second_account_note))
        }
        Spacer(Modifier.height(10.dp))

        val neteaseCookie = remember(refreshRevision) { NeteaseSessionStore.readPlaybackCookie(context) }
        val qqLoggedIn = remember(refreshRevision) { QQMusicSessionStore.read(context, playback = true).isLoggedIn }

        SettingsGlassGroup {
            MeloXIosListRow(
                title = stringResource(R.string.account_netease),
                subtitle = if (NeteaseSessionStore.containsMusicU(neteaseCookie)) stringResource(R.string.provider_logged_in) else stringResource(R.string.provider_not_signed_in),
                leading = { MeloXSymbolIcon(MeloXSymbol.MusicNote, Modifier.size(24.dp), MaterialTheme.colorScheme.primary) },
                onClick = { neteaseLoginLauncher.launch(MeloXNeteaseLoginActivity.createIntent(context, PlaybackAccountSlot.Playback)) },
                showTopSeparator = false,
            )
            MeloXIosListRow(
                title = stringResource(R.string.settings_qq_music),
                subtitle = if (qqLoggedIn) stringResource(R.string.provider_logged_in) else stringResource(R.string.provider_not_signed_in),
                leading = { MeloXSymbolIcon(MeloXSymbol.MusicNote, Modifier.size(24.dp), MaterialTheme.colorScheme.primary) },
                onClick = { showQQLogin = true },
            )
            MeloXIosListRow(
                title = stringResource(R.string.settings_apple_music),
                subtitle = stringResource(R.string.settings_apple_second_account),
                leading = { MeloXSymbolIcon(MeloXSymbol.MusicNote, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) },
                onClick = null,
            )
        }
        Spacer(Modifier.height(10.dp))
        SettingsDangerButton(stringResource(R.string.settings_clear_second_accounts)) { showClearPlaybackConfirmation = true }
        MeloXGlassDialog(
            visible = showClearPlaybackConfirmation,
            onDismiss = { showClearPlaybackConfirmation = false },
        ) {
            Text(stringResource(R.string.settings_clear_second_accounts_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_clear_second_accounts_body),
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsActionButton(stringResource(R.string.action_cancel), Modifier.weight(1f)) { showClearPlaybackConfirmation = false }
                SettingsActionButton(stringResource(R.string.provider_clear), Modifier.weight(1f)) {
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
        SettingsInfoCard(stringResource(R.string.settings_auto_lyrics_required))
        return
    }
    var enabled by remember {
        mutableStateOf(MeloXSettingsPreferences.boolean(context, "experimental_lyric_strong_binding", false))
    }
    SettingsGlassGroup {
        SettingsExternalToggleRow(
            title = stringResource(R.string.settings_lyric_binding),
            value = enabled,
            grouped = true,
        ) { value ->
            enabled = value
            MeloXSettingsPreferences.setBoolean(context, "experimental_lyric_strong_binding", value)
            if (!value) com.lladlam.melox.core.lyrics.LyricBindingStore.clear(context)
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsDangerButton(stringResource(R.string.settings_clear_lyric_bindings)) {
        showClearLyricBindingsConfirmation = true
    }
    MeloXGlassDialog(
        visible = showClearLyricBindingsConfirmation,
        onDismiss = { showClearLyricBindingsConfirmation = false },
    ) {
        Text(stringResource(R.string.settings_clear_lyric_bindings_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_clear_lyric_bindings_body),
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsActionButton(stringResource(R.string.action_cancel), Modifier.weight(1f)) {
                showClearLyricBindingsConfirmation = false
            }
            SettingsDangerButton(stringResource(R.string.provider_clear), Modifier.weight(1f)) {
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
private fun FloatingLyricsSettings(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        Text(
            text = stringResource(R.string.settings_floating_requires_android_8),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
        )
        return
    }
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
            title = stringResource(R.string.settings_floating_show),
            value = enabled,
            note = stringResource(R.string.settings_floating_show_note),
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
            title = stringResource(R.string.settings_floating_secondary),
            selected = MeloXSettingsRuntime.floatingSecondaryMode,
            items = listOf(
                MeloXSecondaryLyricMode.Auto to stringResource(R.string.settings_floating_secondary_auto),
                MeloXSecondaryLyricMode.Translation to stringResource(R.string.settings_floating_translation),
                MeloXSecondaryLyricMode.Romanization to stringResource(R.string.settings_floating_romanization),
                MeloXSecondaryLyricMode.NextLine to stringResource(R.string.settings_floating_next_line),
                MeloXSecondaryLyricMode.Hidden to stringResource(R.string.settings_floating_hidden),
            ),
            onSelected = { MeloXSettingsPreferences.setString(context, "floating_lyrics_secondary_mode", it.name) },
            grouped = true,
        )
        LyricsChoiceSetting(
            context,
            stringResource(R.string.settings_floating_font_size),
            "floating_lyrics_font_size",
            18,
            listOf(14, 16, 18, 20, 24, 28),
            grouped = true,
        ) { "$it sp" }
        SettingsToggleRow(context, stringResource(R.string.settings_floating_contrast), "floating_lyrics_high_contrast", true, stringResource(R.string.settings_floating_contrast_note), grouped = true)
    }
}

@Composable
private fun PlaybackSettings(context: android.content.Context) {
    var quality by remember { mutableStateOf(MusicQualityPreferences.read(context)) }
    var volumeMode by remember { mutableStateOf(MeloXSettingsRuntime.volumeControlMode) }
    SettingsGlassGroup {
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_playback_quality),
            selected = quality,
            items = MusicQuality.entries.map { it to it.title },
            onSelected = { quality = it; PlaybackCommands.changeQuality(context, it) },
            grouped = true,
        )
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_volume_slider),
            selected = volumeMode,
            items = listOf(
                MeloXVolumeControlMode.System to stringResource(R.string.settings_volume_system),
                MeloXVolumeControlMode.Player to stringResource(R.string.settings_volume_player),
            ),
            onSelected = { volumeMode = it; MeloXSettingsPreferences.setString(context, "playback_volume_mode", it.name) },
            grouped = true,
        )
        SettingsToggleRow(
            context,
            stringResource(R.string.settings_allow_other_apps),
            "playback_allow_other_apps",
            false,
            stringResource(R.string.settings_allow_other_apps_note),
            grouped = true,
        )
        SettingsToggleRow(context, stringResource(R.string.settings_remember_player_page), "playback_remember_page", true, grouped = true)
        SettingsToggleRow(
            context,
            stringResource(R.string.settings_save_queue_on_exit),
            "playback_save_queue",
            true,
            "重新打开软件时恢复退出前的播放列表、当前歌曲和进度（暂停状态，不会自动播放）。",
            grouped = true,
        )
        SettingsToggleRow(context, stringResource(R.string.settings_heart_mode_on_launch), "playback_heart_mode_on_launch", false, grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_previous_restarts), "playback_previous_restarts", true, grouped = true)
        SettingsToggleRow(
            context,
            stringResource(R.string.settings_cloud_shuffle),
            "playback_cloud_shuffle",
            true,
            stringResource(R.string.settings_cloud_shuffle_note),
            grouped = true,
        )
        SettingsToggleRow(
            context,
            stringResource(R.string.settings_skip_unavailable),
            "playback_skip_unavailable",
            true,
            stringResource(R.string.settings_skip_unavailable_note),
            grouped = true,
        )
        SettingsToggleRow(
            context,
            stringResource(R.string.settings_show_quality_tip),
            "player_show_quality_tip",
            true,
            stringResource(R.string.settings_show_quality_tip_note),
            grouped = true,
        )
        LyricsChoiceSetting(
            context,
            stringResource(R.string.settings_player_transition_duration),
            // ⚠⚠ 2026-10-03：这一项不能直接用 `MeloXSettingsPreferences.int(context, key, default)`
            //   —— 那是裸 `getInt`，**不做旧默认迁移**，而运行时的迁移在
            //   `playerTransitionDurationMs()` 里 ⇒ 两条路径不同源。
            //   正好会复现用户报障的「**不要实际变，设置显示没变**」（显示 360 / 实际 240）。
            //   ⇒ 走专用读取函数：读 + 迁移 + 写回同一处，保证「显示 == 实际」。
            key = MeloXSettingsPreferences.KEY_PLAYER_TRANSITION_DURATION_MS,
            selectedProvider = { MeloXSettingsPreferences.playerTransitionDurationMs(context) },
            values = listOf(240, 300, 360, 460, 600),
            grouped = true,
        ) { "${it}ms" }
    }
    Spacer(Modifier.height(10.dp))
    EqualizerSettings(context)
    Spacer(Modifier.height(10.dp))
    AutoMixSettings(context)
    Spacer(Modifier.height(10.dp))
    AnalysisCacheSettings(context)
}

@Composable
private fun EqualizerSettings(context: android.content.Context) {
    var enabled by remember { mutableStateOf(MeloXSettingsPreferences.boolean(context, "equalizer_enabled", false)) }
    var preset by remember { mutableStateOf(MeloXSettingsPreferences.string(context, "equalizer_preset", "Flat")) }
    var preamp by remember { mutableStateOf(MeloXSettingsPreferences.number(context, "equalizer_preamp_db", 0f)) }
    SettingsExternalToggleRow(stringResource(R.string.settings_equalizer), enabled, stringResource(R.string.settings_equalizer_note)) {
        enabled = it
        MeloXSettingsPreferences.setBoolean(context, "equalizer_enabled", it)
    }
    if (!enabled) return
    Text(stringResource(R.string.settings_eq_presets), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        MeloXEqualizerController.PRESETS.keys.forEach { value ->
            val label = mapOf(
                "Flat" to stringResource(R.string.settings_eq_flat), "Bass" to stringResource(R.string.settings_eq_bass), "Vocal" to stringResource(R.string.settings_eq_vocal), "Treble" to stringResource(R.string.settings_eq_treble),
                "Electronic" to "电子", "Rock" to "摇滚", "Classical" to "古典", "Custom" to stringResource(R.string.settings_eq_custom),
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
    Text(stringResource(R.string.settings_eq_preamp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsFloatSlider(stringResource(R.string.settings_eq_input_gain), preamp, -12f..12f, 47, { value ->
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
    var showSmartQueueIntro by remember { mutableStateOf(false) }

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
            title = stringResource(R.string.settings_automix),
            value = autoMixEnabled,
            note = stringResource(R.string.settings_automix_note),
            grouped = true,
        ) {
            autoMixEnabled = it
            MeloXPlaybackModePreferences.setAutoMix(context, it)
        }
        SettingsExternalToggleRow(
            title = stringResource(R.string.settings_transition_animation),
            value = MeloXSettingsRuntime.transitionUiEnabled,
            grouped = true,
        ) { enabled ->
            MeloXSettingsPreferences.setBoolean(context, "transition_ui_enabled", enabled)
            MeloXSettingsRuntime.transitionUiEnabled = enabled
        }
    }
    if (autoMixEnabled) {
        Spacer(Modifier.height(10.dp))
        if (settings.mode == MeloXAutoMixMode.Smart) SettingsGlassGroup {
            SettingsExternalToggleRow(
                title = stringResource(R.string.settings_smart_queue),
                value = MeloXSettingsRuntime.smartQueueEnabled,
                grouped = true,
            ) { enabled ->
                if (enabled && !MeloXSettingsPreferences.boolean(context, "smart_queue_intro_shown", false)) {
                    showSmartQueueIntro = true
                } else {
                    MeloXPlaybackModePreferences.setSmartQueue(context, enabled)
                    MeloXSettingsRuntime.smartQueueEnabled = enabled
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SettingsGlassGroup {
            MeloXSettingsDropdown(
            title = stringResource(R.string.settings_automix_mode),
            selected = settings.mode,
            items = listOf(MeloXAutoMixMode.Smart to stringResource(R.string.settings_automix_smart), MeloXAutoMixMode.Fixed to stringResource(R.string.settings_automix_fixed)),
            onSelected = { MeloXPlaybackModePreferences.setAutoMixString(context, "automix_mode", it.name); refresh() },
            grouped = true,
        )
        if (settings.mode == MeloXAutoMixMode.Smart) {
            SettingsExternalToggleRow(stringResource(R.string.settings_skip_quiet_opening), settings.skipQuietOpening, stringResource(R.string.settings_skip_quiet_opening_note), grouped = true) {
                MeloXPlaybackModePreferences.setAutoMixBoolean(context, "automix_skip_quiet_opening", it)
                refresh()
            }
            val limitOptions = listOf(
                -1 to "不限",
                20 to "前 20 首",
                40 to "前 40 首",
                60 to "前 60 首",
                100 to "前 100 首",
            )
            MeloXSettingsDropdown(
                title = "队列分析上限",
                selected = limitOptions.minByOrNull {
                    kotlin.math.abs(settings.smartQueueAnalysisLimit - it.first)
                }?.first ?: 60,
                items = limitOptions,
                onSelected = { MeloXPlaybackModePreferences.setAutoMixInt(context, "automix_smart_queue_analysis_limit", it); refresh() },
                grouped = true,
            )
            SettingsExternalToggleRow(stringResource(R.string.settings_analyze_streaming), settings.analyzeStreaming, stringResource(R.string.settings_analyze_streaming_note), grouped = true) {
                MeloXPlaybackModePreferences.setAutoMixBoolean(context, "automix_analyze_streaming", it)
                refresh()
            }
            MeloXSettingsDropdown(
                title = stringResource(R.string.settings_smart_transition_length),
                selected = settings.transitionBars,
                items = listOf(4, 8, 16).map { it to "$it 小节" },
                onSelected = { MeloXPlaybackModePreferences.setAutoMixInt(context, "automix_transition_bars", it); refresh() },
                grouped = true,
            )
            MeloXSettingsDropdown(
                title = stringResource(R.string.settings_tail_cut),
                selected = settings.tailCutBars,
                items = listOf(0 to stringResource(R.string.settings_tail_keep), 2 to "提前 2 小节", 4 to "提前 4 小节", 8 to "提前 8 小节"),
                onSelected = { MeloXPlaybackModePreferences.setAutoMixInt(context, "automix_tail_cut_bars", it); refresh() },
                grouped = true,
            )
            val confidenceOptions = listOf(.30f, .42f, .55f, .70f)
            MeloXSettingsDropdown(
                title = stringResource(R.string.settings_min_confidence),
                selected = confidenceOptions.minByOrNull { kotlin.math.abs(settings.minimumConfidence - it) } ?: .42f,
                items = confidenceOptions.map { it to "${(it * 100).toInt()}%" },
                onSelected = { MeloXPlaybackModePreferences.setAutoMixFloat(context, "automix_minimum_confidence", it); refresh() },
                grouped = true,
            )
        }
        val durationOptions = listOf(3_000L, 6_000L, 8_000L, 12_000L, 16_000L, 20_000L)
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_crossfade_duration),
            selected = durationOptions.minByOrNull { kotlin.math.abs(settings.fixedDurationMs - it) } ?: 6_000L,
            items = durationOptions.map { it to "${it / 1_000} 秒" },
            onSelected = { MeloXPlaybackModePreferences.setAutoMixLong(context, "automix_fixed_duration_ms", it); refresh() },
            grouped = true,
        )
        val preloadOptions = listOf(30_000L, 60_000L, 90_000L, 120_000L, 180_000L)
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_preload_lead),
            selected = preloadOptions.minByOrNull { kotlin.math.abs(settings.preloadLeadMs - it) } ?: 60_000L,
            items = preloadOptions.map { it to "${it / 1_000} 秒" },
            onSelected = { MeloXPlaybackModePreferences.setAutoMixLong(context, "automix_preload_lead_ms", it); refresh() },
            grouped = true,
        )
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_fade_curve),
            selected = settings.fadeCurve,
            items = listOf(
                MeloXAutoMixFadeCurve.EqualPower to stringResource(R.string.settings_fade_equal_power),
                MeloXAutoMixFadeCurve.Smooth to stringResource(R.string.settings_fade_smooth),
                MeloXAutoMixFadeCurve.Linear to stringResource(R.string.settings_fade_linear),
            ),
            onSelected = { MeloXPlaybackModePreferences.setAutoMixString(context, "automix_fade_curve", it.name); refresh() },
            grouped = true,
        )
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_analysis_fallback),
            selected = settings.fallback,
            items = listOf(
                MeloXAutoMixFallback.Crossfade to stringResource(R.string.settings_fallback_selected_duration),
                MeloXAutoMixFallback.ShortCrossfade to stringResource(R.string.settings_fallback_short_crossfade),
                MeloXAutoMixFallback.Normal to stringResource(R.string.settings_fallback_normal),
            ),
            onSelected = { MeloXPlaybackModePreferences.setAutoMixString(context, "automix_fallback", it.name); refresh() },
            grouped = true,
        )
        SettingsExternalToggleRow(stringResource(R.string.settings_tempo_matching), settings.tempoMatching, stringResource(R.string.settings_tempo_matching_note), grouped = true) {
            MeloXPlaybackModePreferences.setAutoMixBoolean(context, "automix_tempo_matching", it)
            refresh()
        }
        if (settings.tempoMatching) {
            val adjustmentOptions = listOf(.02f, .05f, .08f, .10f)
            MeloXSettingsDropdown(
                title = stringResource(R.string.settings_max_tempo_adjustment),
                selected = adjustmentOptions.minByOrNull { kotlin.math.abs(settings.maxTempoAdjustment - it) } ?: .05f,
                items = adjustmentOptions.map { it to "${(it * 100).toInt()}%" },
                onSelected = { MeloXPlaybackModePreferences.setAutoMixFloat(context, "automix_max_tempo_adjustment", it); refresh() },
                grouped = true,
            )
        }
    }
    }
    if (showSmartQueueIntro) {
        MeloXGlassDialog(visible = true, onDismiss = { showSmartQueueIntro = false }) {
            Text(stringResource(R.string.settings_smart_queue), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.settings_smart_queue_intro))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.settings_smart_queue_rules), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.settings_smart_queue_rule_closest))
            Text(stringResource(R.string.settings_smart_queue_rule_unique))
            Text(stringResource(R.string.settings_smart_queue_rule_background))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionButton(stringResource(R.string.action_cancel), Modifier.weight(1f)) { showSmartQueueIntro = false }
                SettingsActionButton(stringResource(R.string.settings_action_enable), Modifier.weight(1f)) {
                    MeloXPlaybackModePreferences.setSmartQueue(context, true)
                    MeloXSettingsRuntime.smartQueueEnabled = true
                    MeloXSettingsPreferences.setBoolean(context, "smart_queue_intro_shown", true)
                    showSmartQueueIntro = false
                }
            }
        }
    }
}

@Composable
private fun AnalysisCacheSettings(context: android.content.Context) {
    var showClearConfirmation by remember { mutableStateOf(false) }
    SettingsGlassGroup {
        SettingsActionButton(stringResource(R.string.settings_clear_analysis_cache)) { showClearConfirmation = true }
    }
    if (showClearConfirmation) {
        MeloXGlassDialog(visible = true, onDismiss = { showClearConfirmation = false }) {
            Text(stringResource(R.string.settings_clear_analysis_cache_confirm))
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionButton(stringResource(R.string.action_cancel), Modifier.weight(1f)) { showClearConfirmation = false }
                SettingsActionButton(stringResource(R.string.settings_privacy_clear_confirm), Modifier.weight(1f)) {
                    MeloXAudioAnalysisPreferences.clearAll(context)
                    showClearConfirmation = false
                }
            }
        }
    }
}

@Composable
private fun PlayerAppearanceSettings(context: android.content.Context) {
    val scope = rememberCoroutineScope()
    // Selecting "custom image" must not persist until an image actually
    // imports; otherwise cancelling the picker leaves an image-less mode.
    val restoreArtworkModeIfCustomImageEmpty: () -> Unit = {
        if (MeloXSettingsRuntime.playerBackgroundMode == MeloXPlayerBackgroundMode.CustomImage &&
            MeloXSettingsRuntime.customBackgroundPath == null
        ) {
            MeloXSettingsPreferences.setString(
                context,
                "player_background_mode",
                MeloXPlayerBackgroundMode.BlurredArtwork.name,
            )
            MeloXSettingsRuntime.flowingBackdropEnabled = false
        }
    }
    val pickCustomBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) {
            restoreArtworkModeIfCustomImageEmpty()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                MeloXCustomBackgroundStore.import(context, uri)
            }
            if (imported == null) {
                Toast.makeText(context, R.string.settings_custom_background_failed, Toast.LENGTH_SHORT).show()
                restoreArtworkModeIfCustomImageEmpty()
            } else {
                MeloXSettingsPreferences.setString(context, "player_custom_background_path", imported)
                MeloXSettingsPreferences.setString(
                    context,
                    "player_background_mode",
                    MeloXPlayerBackgroundMode.CustomImage.name,
                )
                MeloXSettingsRuntime.flowingBackdropEnabled = false
            }
        }
    }
    val pickCustomBackgroundImage: () -> Unit = {
        pickCustomBackground.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }
    LyricsStringChoiceSetting(
        context,
        stringResource(R.string.settings_player_shell),
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
        stringResource(R.string.settings_frosted_glass),
        "player_frosted_glass",
        false,
        stringResource(R.string.settings_frosted_glass_note),
    )
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_player_background),
            selected = MeloXSettingsRuntime.playerBackgroundMode,
            items = listOf(
                MeloXPlayerBackgroundMode.FlowingLight to stringResource(R.string.settings_bg_flowing_light),
                MeloXPlayerBackgroundMode.AppleLyrics to stringResource(R.string.settings_bg_apple_lyrics),
                MeloXPlayerBackgroundMode.BlurredArtwork to stringResource(R.string.settings_bg_blurred_artwork),
                MeloXPlayerBackgroundMode.CustomImage to stringResource(R.string.settings_bg_custom_image),
            ),
            onSelected = { mode ->
                if (mode == MeloXPlayerBackgroundMode.CustomImage &&
                    MeloXSettingsRuntime.customBackgroundPath == null
                ) {
                    // Persist the mode only after import succeeds (in the
                    // picker callback); cancelling keeps the old background.
                    pickCustomBackgroundImage()
                } else {
                    MeloXSettingsPreferences.setString(context, "player_background_mode", mode.name)
                    MeloXSettingsRuntime.flowingBackdropEnabled = mode.usesFlowingBackdrop
                }
            },
            grouped = true,
        )
        if (MeloXSettingsRuntime.playerBackgroundMode == MeloXPlayerBackgroundMode.CustomImage) {
            val customBackgroundPath = MeloXSettingsRuntime.customBackgroundPath
            MeloXIosListRow(
                title = stringResource(
                    if (customBackgroundPath == null) {
                        R.string.settings_custom_background_pick
                    } else {
                        R.string.settings_custom_background_change
                    },
                ),
                subtitle = stringResource(R.string.settings_custom_background_note),
                leading = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (customBackgroundPath == null) {
                            MeloXSymbolIcon(
                                MeloXSymbol.Plus,
                                Modifier.size(18.dp),
                                MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                            )
                        } else {
                            AsyncImage(
                                model = File(customBackgroundPath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                },
                onClick = pickCustomBackgroundImage,
            )
            if (customBackgroundPath != null) {
                MeloXIosListRow(
                    title = stringResource(R.string.settings_custom_background_remove),
                    onClick = {
                        MeloXCustomBackgroundStore.delete(context, customBackgroundPath)
                        MeloXSettingsPreferences.setString(context, "player_custom_background_path", "")
                        MeloXSettingsPreferences.setString(
                            context,
                            "player_background_mode",
                            MeloXPlayerBackgroundMode.BlurredArtwork.name,
                        )
                        MeloXSettingsRuntime.flowingBackdropEnabled = false
                    },
                )
            }
        }
        // 流光背景专属调参：只在「取色流动光影」模式下出现（上游 0.6.2 d9b0714，
        // 标签按本地惯例写死中文，不走 stringResource）。
        if (MeloXSettingsRuntime.playerBackgroundMode == MeloXPlayerBackgroundMode.FlowingLight) {
            PreferenceFloatSlider(
                context,
                stringResource(R.string.settings_flowing_speed),
                "player_flowing_speed",
                1f,
                .25f..2f,
                6,
            ) { "%.2fx".format(it) }
            PreferenceFloatSlider(
                context,
                stringResource(R.string.settings_flowing_saturation),
                "player_flowing_saturation",
                1f,
                0f..2f,
                19,
            ) { "${(it * 100).toInt()}%" }
            PreferenceFloatSlider(
                context,
                stringResource(R.string.settings_flowing_brightness),
                "player_flowing_brightness",
                1f,
                .4f..1.6f,
                11,
            ) { "${(it * 100).toInt()}%" }
        }
        SettingsToggleRow(context, stringResource(R.string.settings_flowing_backdrop), "player_flowing_backdrop", true, stringResource(R.string.settings_flowing_backdrop_note), grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_background_isolation), "player_background_isolation", true, stringResource(R.string.settings_background_isolation_note), grouped = true)
        LyricsChoiceSetting(context, stringResource(R.string.settings_background_fps), "lyrics_background_frame_rate", 24, listOf(15, 24, 30, 45, 60), grouped = true) { value ->
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
        SettingsToggleRow(context, stringResource(R.string.settings_reduce_motion), "reduce_motion", false, stringResource(R.string.settings_reduce_motion_note), grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_artwork_motion), "player_artwork_motion", true, grouped = true)
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_screen_awake_scope),
            selected = MeloXSettingsRuntime.screenAwakeMode,
            items = listOf(
                MeloXScreenAwakeMode.Disabled to stringResource(R.string.settings_skyline_off),
                MeloXScreenAwakeMode.Player to stringResource(R.string.settings_awake_player),
                MeloXScreenAwakeMode.Lyrics to stringResource(R.string.settings_awake_lyrics),
                MeloXScreenAwakeMode.HiddenLyricsInterface to stringResource(R.string.settings_awake_hidden_lyrics),
            ),
            onSelected = { MeloXSettingsPreferences.setString(context, "player_screen_awake_mode", it.name) },
            grouped = true,
        )
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        SettingsToggleRow(
            context,
            stringResource(R.string.settings_immersive_playback),
            "immersive_playback",
            false,
            "播放器全屏时自动隐藏顶部系统状态栏",
            grouped = true,
        )
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        SettingsToggleRow(
            context,
            "深色模式保持暗色底栏",
            "bottom_bar_lock_dark_in_dark_mode",
            true,
            "深色模式下底栏不再根据背后内容自适应翻成浅色；浅色模式不受影响。",
            grouped = true,
        )
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        SettingsToggleRow(
            context,
            "隐藏歌单搜索栏",
            "library_playlist_search_hidden",
            false,
            "开启后，固定搜索栏隐藏，并在顶部滑动拉出。关闭则恢复原始样式。",
            grouped = true,
        )
        SettingsToggleRow(
            context,
            "歌单展示封面",
            "library_playlist_show_artwork",
            false,
            "开启后，歌单内的歌曲展示微小封面而不是数字序号。关闭则全部展示数字。",
            grouped = true,
        )
    }
}

@Composable
private fun LyricsSettings(context: android.content.Context) {
    var lyricsStyle by remember { mutableStateOf(MeloXSettingsRuntime.lyricsStyle) }
    // Only the Apple Music style is offered for now. Other renderers stay in the
    // codebase; if one was selected before, move the stored preference back so
    // the visible selector matches what is actually rendered.
    LaunchedEffect(Unit) {
        if (lyricsStyle != MeloXLyricsStyle.AppleMusic) {
            lyricsStyle = MeloXLyricsStyle.AppleMusic
            MeloXSettingsPreferences.setString(context, "lyrics_style", MeloXLyricsStyle.AppleMusic.name)
        }
    }
    // Group 1: 歌词样式-歌词渲染质量-逐字歌词-普通LRC-点击跳转-长按分享-间奏倒计时-自动跟随-手动滚动恢复-减弱动画
    SettingsGlassGroup {
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_lyrics_style),
            selected = lyricsStyle,
            items = listOf(
                MeloXLyricsStyle.AppleMusic to stringResource(R.string.settings_apple_music),
            ),
            onSelected = { lyricsStyle = it; MeloXSettingsPreferences.setString(context, "lyrics_style", it.name) },
            grouped = true,
        )
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_lyrics_rendering),
            selected = MeloXSettingsRuntime.lyricRenderingQuality,
            items = listOf(
                MeloXLyricsRenderingQuality.Low to stringResource(R.string.settings_lyrics_quality_low),
                MeloXLyricsRenderingQuality.Balanced to stringResource(R.string.settings_lyrics_quality_balanced),
                MeloXLyricsRenderingQuality.High to stringResource(R.string.settings_lyrics_quality_high),
            ),
            onSelected = { MeloXSettingsPreferences.setString(context, "lyrics_rendering_quality", it.name) },
            grouped = true,
        )
        var lyricLanguage by remember { mutableStateOf(MeloXSettingsRuntime.lyricLanguage) }
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_lyrics_language),
            selected = lyricLanguage,
            items = listOf(
                "system" to stringResource(R.string.settings_lyrics_language_system),
                "zh-Hans" to stringResource(R.string.settings_lyrics_language_zh_hans),
                "zh-Hant" to stringResource(R.string.settings_lyrics_language_zh_hant),
                "en" to stringResource(R.string.settings_lyrics_language_en),
                "tr" to stringResource(R.string.settings_lyrics_language_tr),
            ),
            onSelected = {
                lyricLanguage = it
                MeloXSettingsPreferences.setString(context, "lyric_language", it)
            },
            grouped = true,
        )
        SettingsToggleRow(context, stringResource(R.string.settings_lyrics_auto_select), "lyrics_auto_select", true, grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_lyrics_word_by_word), "lyrics_word_by_word", true, grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_lyrics_pseudo_timing), "lyrics_pseudo_timing", true, stringResource(R.string.settings_lyrics_pseudo_timing_note), grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_lyrics_qq_bracket_backing), "lyrics_qq_bracket_backing", false, stringResource(R.string.settings_lyrics_qq_bracket_backing_note), grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_lyrics_tap_seek), "lyrics_tap_seek", true, grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_lyrics_long_press_share), "lyrics_long_press_share", true, grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_lyrics_interlude), "lyrics_interlude_countdown", true, stringResource(R.string.settings_lyrics_interlude_note), grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_lyrics_auto_follow), "lyrics_auto_follow", true, grouped = true)
        LyricsChoiceSetting(context, stringResource(R.string.settings_lyrics_follow_delay), "lyrics_follow_delay_ms", 3_000, listOf(1_500, 3_000, 5_000, 8_000), grouped = true) { "${it / 1_000f} 秒" }
        SettingsToggleRow(context, stringResource(R.string.settings_lyrics_reduce_motion), "lyrics_reduce_motion", false, stringResource(R.string.settings_lyrics_reduce_motion_note), grouped = true)
    }
    if (lyricsStyle == MeloXLyricsStyle.TextPV) {
        var pvStyle by remember { mutableStateOf(MeloXSettingsRuntime.textPVStyle) }
        var pvMotionIntensity by remember { mutableStateOf(MeloXSettingsRuntime.textPVMotionIntensity) }
        var pvAnimationSpeed by remember { mutableStateOf(MeloXSettingsRuntime.textPVAnimationSpeed) }
        Spacer(Modifier.height(10.dp))
        SettingsGlassGroup {
            Text(stringResource(R.string.settings_text_pv_style), modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
            listOf(
                MeloXTextPVStyle.BlueBold to stringResource(R.string.settings_pv_blue_bold),
                MeloXTextPVStyle.KineticSplit to stringResource(R.string.settings_pv_kinetic_split),
                MeloXTextPVStyle.BluePlane to stringResource(R.string.settings_pv_blue_plane),
                MeloXTextPVStyle.CyberGrunge to stringResource(R.string.settings_pv_cyber_grunge),
                MeloXTextPVStyle.Geometric to stringResource(R.string.settings_pv_geometric),
                MeloXTextPVStyle.RainCity to stringResource(R.string.settings_pv_rain_city),
                MeloXTextPVStyle.CyberpunkHUD to stringResource(R.string.settings_pv_cyberpunk_hud),
                MeloXTextPVStyle.EmotionCinema to stringResource(R.string.settings_pv_emotion_cinema),
                MeloXTextPVStyle.HystericNight to stringResource(R.string.settings_pv_hysteric_night),
                MeloXTextPVStyle.SpiderWeb to stringResource(R.string.settings_pv_spider_web),
                MeloXTextPVStyle.StaggeredText to stringResource(R.string.settings_pv_staggered),
                MeloXTextPVStyle.CalmVillain to stringResource(R.string.settings_pv_calm_villain),
                MeloXTextPVStyle.GirlyClouds to stringResource(R.string.settings_pv_girly_clouds),
                MeloXTextPVStyle.SweetPink to stringResource(R.string.settings_pv_sweet_pink),
                MeloXTextPVStyle.FlyMeToTheMoon to "Fly Me to the Moon",
                MeloXTextPVStyle.KawaiiPixel to stringResource(R.string.settings_pv_kawaii_pixel),
                MeloXTextPVStyle.CrimeScene to stringResource(R.string.settings_pv_crime_scene),
                MeloXTextPVStyle.Haruhikage to stringResource(R.string.settings_pv_haruhikage),
            ).forEach { (style, title) ->
                SettingsChoiceRow(title, pvStyle == style) {
                    MeloXSettingsPreferences.setString(context, "lyrics_text_pv_style", style.name)
                    pvStyle = style
                    pvAnimationSpeed = style.referenceAnimationSpeed
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SettingsFloatSlider(stringResource(R.string.settings_pv_motion_intensity), pvMotionIntensity, 0f..2f, 19) {
            pvMotionIntensity = it; MeloXSettingsPreferences.setFloat(context, "lyrics_text_pv_motion_intensity", it)
        }
        SettingsFloatSlider(stringResource(R.string.settings_pv_animation_speed), pvAnimationSpeed, 0f..4f, 39) {
            pvAnimationSpeed = it; MeloXSettingsPreferences.setFloat(context, "lyrics_text_pv_animation_speed", it)
        }
        Spacer(Modifier.height(10.dp))
        SettingsActionButton(stringResource(R.string.settings_pv_reset)) {
            MeloXSettingsPreferences.setString(context, "lyrics_text_pv_style", MeloXTextPVStyle.BlueBold.name)
            MeloXSettingsPreferences.setFloat(context, "lyrics_text_pv_motion_intensity", 1f)
            MeloXSettingsPreferences.setFloat(context, "lyrics_text_pv_animation_speed", 2f)
            pvStyle = MeloXTextPVStyle.BlueBold; pvMotionIntensity = 1f; pvAnimationSpeed = 2f
        }
    }

    // Group 2: 显示翻译-翻译歌词大小-翻译歌词亮度
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        SettingsToggleRow(context, stringResource(R.string.settings_show_translation), "lyrics_translation", true, grouped = true)
        LyricsFloatChoiceSetting(context, stringResource(R.string.settings_translation_size), "lyrics_translation_font_scale", .65f, listOf(.5f, .55f, .6f, .65f, .7f, .75f, .8f), grouped = true) { "${(it * 100).toInt()}%" }
        LyricsFloatChoiceSetting(context, stringResource(R.string.settings_translation_brightness), "lyrics_translation_opacity", .9f, listOf(.4f, .5f, .6f, .7f, .8f, .9f), grouped = true) { "${(it * 100).toInt()}%" }
        LyricsStringChoiceSetting(
            context, stringResource(R.string.settings_translation_scope), "lyrics_translation_display_mode",
            MeloXLyricAnnotationDisplayMode.AllLines.name, MeloXLyricAnnotationDisplayMode.entries.map { it.name }, grouped = true,
        ) { if (it == MeloXLyricAnnotationDisplayMode.FocusedLine.name) "仅当前播放行" else "全部歌词行" }
        SettingsToggleRow(context, stringResource(R.string.settings_show_romanization), "lyrics_romanization", false, grouped = true)
        LyricsStringChoiceSetting(
            context, stringResource(R.string.settings_romanization_scope), "lyrics_romanization_display_mode",
            MeloXLyricAnnotationDisplayMode.FocusedLine.name, MeloXLyricAnnotationDisplayMode.entries.map { it.name }, grouped = true,
        ) { if (it == MeloXLyricAnnotationDisplayMode.FocusedLine.name) "仅当前播放行" else "全部歌词行" }
        LyricsFloatChoiceSetting(context, stringResource(R.string.settings_romanization_size), "lyrics_romanization_font_scale", .65f, listOf(.5f, .55f, .6f, .65f, .7f, .75f, .8f), grouped = true) { "${(it * 100).toInt()}%" }
        LyricsFloatChoiceSetting(context, stringResource(R.string.settings_romanization_brightness), "lyrics_romanization_opacity", .9f, listOf(.4f, .5f, .6f, .7f, .8f, .9f), grouped = true) { "${(it * 100).toInt()}%" }
    }

    // Group 3: 歌词提前量-提前量同时应用于逐字高亮-歌词刷新率-歌词字号-歌词字重-抬升方式
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        LyricsChoiceSetting(context, stringResource(R.string.settings_lyrics_advance), "lyrics_advance_ms", 0, listOf(-1_000, -500, -200, 0, 200, 500, 1_000, 2_000, 5_000), grouped = true) { value ->
            if (value == 0) "同步" else if (value > 0) "提前 ${value}ms" else "延后 ${-value}ms"
        }
        SettingsToggleRow(context, stringResource(R.string.settings_lyrics_advance_words), "lyrics_advance_word_by_word", false, grouped = true)
        LyricsChoiceSetting(context, stringResource(R.string.settings_lyrics_refresh_rate), "lyrics_refresh_rate", 60, listOf(30, 60, 90, 120), grouped = true) { "$it FPS" }
        LyricsFloatChoiceSetting(context, stringResource(R.string.settings_lyrics_font_size), "lyrics_font_scale", 1f, listOf(.85f, 1f, 1.12f, 1.25f), grouped = true) { "${(it * 100).toInt()}%" }
        LyricsStringChoiceSetting(
            context, stringResource(R.string.settings_lyrics_font_weight), "lyrics_font_weight", MeloXLyricsFontWeight.Heavy.name,
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
            context, stringResource(R.string.settings_lyrics_lift), "lyrics_lift_mode", MeloXLyricsGroupingMode.Character.name,
            MeloXLyricsGroupingMode.entries.map { it.name }, grouped = true,
        ) { if (it == MeloXLyricsGroupingMode.Word.name) "按词抬升" else "按字抬升" }
    }

    // Group 4: 长音识别方式-逐字歌词光效-仅长音显示光晕-逐字光晕-长音延展-长音判定时长-行间距-远近模糊
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        LyricsStringChoiceSetting(
            context, stringResource(R.string.settings_long_tone_detection), "lyrics_long_tone_detection", MeloXLyricsGroupingMode.Character.name,
            MeloXLyricsGroupingMode.entries.map { it.name }, grouped = true,
        ) { if (it == MeloXLyricsGroupingMode.Word.name) "按词识别" else "按字识别" }
        SettingsToggleRow(context, stringResource(R.string.settings_lyrics_glow), "lyrics_glow_enabled", true, grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_glow_long_tones_only), "lyrics_glow_long_tones_only", true, grouped = true)
        LyricsFloatChoiceSetting(context, "逐字光晕", "lyrics_glow_strength", 1f, listOf(0f, .6f, 1f, 1.4f), grouped = true) { if (it == 0f) "关闭" else "${(it * 100).toInt()}%" }
        LyricsFloatChoiceSetting(context, "长音延展", "lyrics_long_tone_strength", 1f, listOf(0f, .6f, 1f, 1.4f), grouped = true) { if (it == 0f) "关闭" else "${(it * 100).toInt()}%" }
        LyricsChoiceSetting(context, stringResource(R.string.settings_long_tone_threshold), "lyrics_long_tone_threshold_ms", 950, listOf(300, 500, 700, 950, 1_200, 1_500), grouped = true) { "${it / 1000f} 秒" }
        LyricsFloatChoiceSetting(context, stringResource(R.string.settings_line_spacing), "lyrics_spacing_scale", 1f, listOf(.8f, 1f, 1.2f, 1.4f), grouped = true) { "${(it * 100).toInt()}%" }
        LyricsFloatChoiceSetting(context, "远近模糊", "lyrics_blur_strength", 1f, listOf(0f, .5f, .8f, 1f), grouped = true) { if (it == 0f) "关闭" else "${(it * 100).toInt()}%" }
    }

    // Group 5: 当前行放大-未播放文字亮度-控制栏自动隐藏-滚动隐藏UI阈值
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        LyricsFloatChoiceSetting(context, stringResource(R.string.settings_focus_scale), "lyrics_focus_scale", 1.02f, listOf(1f, 1.02f, 1.04f, 1.08f), grouped = true) { "${(it * 100).toInt()}%" }
        LyricsFloatChoiceSetting(context, stringResource(R.string.settings_inactive_opacity), "lyrics_inactive_opacity", .42f, listOf(.3f, .42f, .5f, .6f), grouped = true) { "${(it * 100).toInt()}%" }
        LyricsChoiceSetting(context, stringResource(R.string.settings_controls_auto_hide), "lyrics_interface_auto_hide_ms", 5_000, (3..15).map { it * 1_000 }, grouped = true) { "${it / 1_000} 秒" }
        LyricsChoiceSetting(context, stringResource(R.string.settings_scroll_hide_threshold), "lyrics_scroll_hide_threshold_dp", 200, listOf(40, 80, 120, 160, 200, 240), grouped = true) { "$it dp" }
    }

    // Group 6: 启用位移回弹-启用升格回弹-升格回弹时长-焦点回弹时长
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        SettingsToggleRow(context, stringResource(R.string.settings_cascade_bounce_enabled), "lyrics_cascade_bounce_enabled", true, grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_scale_bounce_enabled), "lyrics_scale_bounce_enabled", true, grouped = true)
        LyricsChoiceSetting(context, stringResource(R.string.settings_scale_bounce_duration), "lyrics_scale_bounce_duration_ms", 580, listOf(150, 250, 350, 450, 580, 700, 800), grouped = true) { "${it}ms" }
        LyricsChoiceSetting(context, "焦点回弹时长", "lyrics_focus_color_lead_ms", 0, listOf(-300, -200, -100, -50, 0, 50, 100, 200, 300), grouped = true) { if (it == 0) "同步" else "${it}ms" }
    }

    // Group 7: 最大回弹弹性-回弹强度梯度-升格回弹弹性
    Spacer(Modifier.height(10.dp))
    PreferenceFloatSlider(context, stringResource(R.string.settings_max_cascade_bounce), "lyrics_cascade_bounce", .26f, 0f..8f / 10f, 79) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, stringResource(R.string.settings_bounce_gradient), "lyrics_cascade_bounce_gradient", .85f, 0f..1f, 99) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, stringResource(R.string.settings_scale_bounce_amount), "lyrics_scale_bounce", .32f, 0f..5f / 10f, 49) { "${(it * 100).toInt()}%" }

    // Group 8: 高光渐变宽度-渐变削减程度
    Spacer(Modifier.height(10.dp))
    PreferenceFloatSlider(context, stringResource(R.string.settings_highlight_width), "lyrics_highlight_gradient_width", .7f, .4f..3f, 25) { "%.1f 字宽".format(it) }
    PreferenceFloatSlider(context, stringResource(R.string.settings_gradient_reduction), "lyrics_highlight_gradient_reduction", .65f, 0f..1f, 19) { "${(it * 100).toInt()}%" }

    // Group 9: 焦点垂直位置-默认逐句模糊加强-隐藏UI逐句模糊加强-非焦点歌词变暗
    Spacer(Modifier.height(10.dp))
    PreferenceFloatSlider(context, stringResource(R.string.settings_focus_position), "lyrics_focus_position", .25f, .05f..8f / 10f, 74) { "距顶部 ${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, stringResource(R.string.settings_distance_blur), "lyrics_distance_blur_scale", 1.05f, 0f..1.5f, 29) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, stringResource(R.string.settings_hidden_blur), "lyrics_hidden_blur_scale", .85f, 0f..1.5f, 29) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, stringResource(R.string.settings_dim_amount), "lyrics_dim_amount", 1f, 0f..1f, 49) { "${(it * 100).toInt()}%" }

    // Group 10: 基础拖尾延迟-逐句拖尾增量-后续歌词启动延迟-拖尾追赶节奏-追赶速度梯度-位移收束时长
    Spacer(Modifier.height(10.dp))
    PreferenceFloatSlider(context, stringResource(R.string.settings_cascade_delay), "lyrics_cascade_delay_ms", 21f, 0f..100f, 99) { "${it.toInt()} ms" }
    PreferenceFloatSlider(context, stringResource(R.string.settings_cascade_delay_increase), "lyrics_cascade_delay_increase_ms", 5f, 0f..100f, 99) { "${it.toInt()} ms/句" }
    PreferenceFloatSlider(context, stringResource(R.string.settings_following_delay), "lyrics_cascade_following_delay_ms", 30f, 0f..200f, 199) { "${it.toInt()} ms" }
    PreferenceFloatSlider(context, stringResource(R.string.settings_catch_up_ratio), "lyrics_cascade_catch_up_ratio", .97f, .5f..1f, 49) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, stringResource(R.string.settings_chase_gradient), "lyrics_cascade_chase_gradient", .70f, 0f..1f, 99) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, stringResource(R.string.settings_cascade_duration), "lyrics_cascade_duration_ms", 740f, 200f..1_200f, 99) { "%.2f 秒".format(it / 1_000f) }

    // Group 11: 瞬移阈值
    Spacer(Modifier.height(10.dp))
    PreferenceFloatSlider(context, stringResource(R.string.settings_snap_threshold), "lyrics_snap_threshold_ms", 260f, 50f..500f, 89) { "${it.toInt()} ms" }
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
    val showSep = remember(grouped) { if (grouped) groupRowIndex.intValue++.let { it > 0 } else false }
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
) = LyricsChoiceSetting(
    context = context,
    title = title,
    key = key,
    selectedProvider = { MeloXSettingsPreferences.int(context, key, default) },
    values = values,
    grouped = grouped,
    label = label,
)

/**
 * `selectedProvider` 重载（2026-10-03）。
 *
 * ⚠ 为什么要它：原先本函数内部固定用 `MeloXSettingsPreferences.int(context, key, default)`
 *   读取选中项，而 `int()` 是**裸 `getInt`、不做任何迁移**。
 *   当某项setting 的默认值需要跨版本迁移时（`player_transition_duration_ms`
 *   默认经历过 `360 → 240`（v1，已废弃）与 `240 → 360`（v2，当前）两轮），
 *   运行时的迁移在别处、设置页这条路径不迁移 ⇒
 *   **「实际时长已变、设置显示没变」**（用户报障原话）。
 *   ⇒ 让调用方注入 `selectedProvider`，把「读到的值」与「显示的值」**锁成同一个来源**。
 */
@Composable
private fun LyricsChoiceSetting(
    context: android.content.Context,
    title: String,
    key: String,
    selectedProvider: () -> Int,
    values: List<Int>,
    grouped: Boolean = false,
    label: (Int) -> String,
) {
    val groupRowIndex = LocalSettingsGroupRowIndex.current
    val effectiveShowTopSeparator = remember(grouped) { if (grouped) groupRowIndex.intValue++.let { it > 0 } else false }
    // ⚠ 不用 remember 缓存：`selectedProvider` 可能每次读取都做一次迁移+写回
    //   （那正是我们要的：进设置页即完成迁移，显示立刻是新值）。
    var selected by remember(key) { mutableStateOf(selectedProvider()) }
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
    val effectiveShowTopSeparator = remember(grouped) { if (grouped) groupRowIndex.intValue++.let { it > 0 } else false }
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
    // Show the value the finger is currently on while dragging; the setting is
    // still only committed once the drag ends.
    var draggingValue by remember { mutableStateOf<Float?>(null) }
    val shownValue = draggingValue ?: value
    Row(
        // Match MeloXIosListRow's 16dp gutter so the label lines up with the
        // neighbouring setting titles.
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        Text(label(shownValue), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
    }
    MeloXLiquidSlider(
        value = value,
        onValueChange = onValueChange,
        onTransientValueChange = { draggingValue = it },
        onValueChangeFinished = { draggingValue = null },
        valueRange = range,
        stepSize = if (steps > 0) (range.endInclusive - range.start) / (steps + 1) else 0f,
        visibilityThreshold = 0.001f,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp),
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
        SettingsToggleRow(context, stringResource(R.string.settings_feature_history), "feature_history", true, grouped = true)
        SettingsToggleRow(context, "下载", "feature_downloads", true, stringResource(R.string.settings_feature_downloads_note), grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_feature_cloud), "feature_cloud_music", true, stringResource(R.string.settings_feature_cloud_note), grouped = true)
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
        Text(stringResource(R.string.settings_messages_contacts), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        Spacer(Modifier.height(8.dp))
        if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(10.dp))
            Text(stringResource(R.string.settings_messages_loading), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
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
        Text(stringResource(R.string.settings_messages_refresh), Modifier.clickable { reload++ }.padding(8.dp), color = MaterialTheme.colorScheme.primary)
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
                    if (draft.isBlank()) Text(stringResource(R.string.settings_messages_compose), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
                    inner()
                }
            },
        )
        Text(
            if (busy) "…" else stringResource(R.string.settings_messages_send),
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
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val accountManager = remember(app) { ProviderAccountManager(app) }
    val providers = remember(app) { MeloXMusicProviders.create(app) }
    val migrationCandidates = remember(app) {
        accountManager.allStates().filter { state ->
            state.loggedIn &&
                state.source != MusicSource.Bilibili &&
                state.source != MusicSource.Jellyfin && (
                state.source == MusicSource.Netease ||
                    state.source == MusicSource.Local ||
                    providers[state.source] is UserLibraryCapability
                )
        }
    }
    var unifiedFavoritesEnabled by remember { mutableStateOf(MeloXUnifiedFavorites.enabled(app)) }
    var showUnifiedFavoritesSetup by remember { mutableStateOf(false) }
    var unifiedFavoritesBusy by remember { mutableStateOf(false) }
    var unifiedFavoritesNotice by remember { mutableStateOf<String?>(null) }
    var selectedMigrationSources by remember { mutableStateOf<Set<MusicSource>>(emptySet()) }
    val unifiedFavoritesRevision = MeloXUnifiedFavorites.revision
    val unifiedFavoritesCount = remember(unifiedFavoritesEnabled, unifiedFavoritesRevision) {
        if (unifiedFavoritesEnabled) MeloXUnifiedFavorites.tracks(app).size else 0
    }
    SettingsGlassGroup {
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_content_area),
            selected = area,
            items = listOf("全部" to "全部", "华语" to "华语", "欧美" to "欧美", "日本" to "日本", "韩国" to "韩国"),
            onSelected = { area = it; MeloXSettingsPreferences.setString(context, "music_area", it) },
            grouped = true,
        )
        SettingsToggleRow(context, stringResource(R.string.settings_content_hq_playlists), "content_high_quality_playlist", true, grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_content_play_count), "content_playlist_play_count", true, grouped = true)
        SettingsExternalToggleRow(
            title = stringResource(R.string.settings_content_cross_provider),
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
        SettingsExternalToggleRow(
            title = "弥乐「我喜欢」",
            value = unifiedFavoritesEnabled,
            note = "只在弥乐内保存；可从任意音源收藏，不写入音源账号。",
            grouped = true,
        ) { enabled ->
            when {
                !enabled -> {
                    unifiedFavoritesEnabled = false
                    MeloXUnifiedFavorites.setEnabled(app, false)
                }
                MeloXUnifiedFavorites.initialized(app) -> {
                    unifiedFavoritesEnabled = true
                    MeloXUnifiedFavorites.setEnabled(app, true)
                }
                else -> {
                    selectedMigrationSources = migrationCandidates.map { it.source }.toSet()
                    unifiedFavoritesNotice = null
                    showUnifiedFavoritesSetup = true
                }
            }
        }
    }
    if (unifiedFavoritesEnabled) {
        Spacer(Modifier.height(10.dp))
        SettingsInfoCard("弥乐「我喜欢」已启用 · $unifiedFavoritesCount 首。所有音源的爱心操作都会写入这个本地歌单。")
    }
    unifiedFavoritesNotice?.let {
        Spacer(Modifier.height(10.dp))
        SettingsInfoCard(it)
    }
    if (crossProviderFallback) {
        Spacer(Modifier.height(10.dp))
        SettingsInfoCard(
            stringResource(R.string.settings_content_cross_provider_active),
        )
    }
    if (showCrossProviderFallbackNotice) {
        MeloXGlassDialog(
            visible = true,
            onDismiss = { showCrossProviderFallbackNotice = false },
        ) {
            Text(stringResource(R.string.settings_content_cross_provider_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.settings_content_cross_provider_body),
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Text(
                text = stringResource(R.string.settings_content_cross_provider_match),
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Text(
                text = stringResource(R.string.settings_content_cross_provider_terms),
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
                SettingsActionButton(stringResource(R.string.action_cancel), Modifier.weight(1f)) {
                    showCrossProviderFallbackNotice = false
                }
                SettingsActionButton(stringResource(R.string.settings_content_cross_provider_enable), Modifier.weight(1f)) {
                    crossProviderFallback = true
                    CrossProviderPlaybackPreferences.setEnabled(context, true)
                    showCrossProviderFallbackNotice = false
                }
            }
        }
    }
    if (showUnifiedFavoritesSetup) {
        MeloXGlassDialog(
            visible = true,
            onDismiss = { if (!unifiedFavoritesBusy) showUnifiedFavoritesSetup = false },
        ) {
            Text("创建弥乐「我喜欢」", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "选择要迁移收藏的音乐源。创建后会得到一个只存在于弥乐内的「我喜欢」歌单；之后任意音源的歌曲都可以在本地收藏到这里。",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 6.dp),
            ) {
                if (migrationCandidates.isEmpty()) {
                    Text(
                        "当前没有已登录且可读取收藏的音乐源；也可以先创建空歌单，之后手动收藏。",
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
                migrationCandidates.forEach { state ->
                    SettingsChoiceRow(
                        title = state.source.displayName,
                        selected = state.source in selectedMigrationSources,
                        onClick = {
                            selectedMigrationSources = if (state.source in selectedMigrationSources) {
                                selectedMigrationSources - state.source
                            } else {
                                selectedMigrationSources + state.source
                            }
                        },
                    )
                }
            }
            if (unifiedFavoritesBusy) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("正在读取并整理收藏…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsActionButton(stringResource(R.string.action_cancel), Modifier.weight(1f)) {
                    if (!unifiedFavoritesBusy) showUnifiedFavoritesSetup = false
                }
                SettingsActionButton(
                    if (unifiedFavoritesBusy) "迁移中…" else "创建并迁移",
                    Modifier.weight(1f),
                ) {
                    if (unifiedFavoritesBusy) return@SettingsActionButton
                    unifiedFavoritesBusy = true
                    scope.launch {
                        runCatching {
                            MeloXUnifiedFavorites.initialize(app, selectedMigrationSources)
                        }.onSuccess { report ->
                            unifiedFavoritesEnabled = true
                            showUnifiedFavoritesSetup = false
                            unifiedFavoritesNotice = buildString {
                                append("已创建「我喜欢」，共 ${report.total} 首")
                                if (report.failures.isNotEmpty()) {
                                    append("。未迁移：")
                                    append(report.failures.joinToString("；") { failure ->
                                        "${failure.source.displayName}：${failure.message}"
                                    })
                                }
                            }
                        }.onFailure { failure ->
                            showUnifiedFavoritesSetup = false
                            unifiedFavoritesNotice = failure.message ?: "创建「我喜欢」失败"
                        }
                        unifiedFavoritesBusy = false
                    }
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

    SettingsInfoCard(stringResource(R.string.settings_listen_info))
    Spacer(Modifier.height(12.dp))
    if (room == null) {
        SettingsActionButton(stringResource(R.string.settings_listen_start)) {
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
            placeholder = { Text(stringResource(R.string.settings_listen_paste), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        SettingsActionButton(stringResource(R.string.settings_listen_join)) {
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
            MeloXIosListRow(stringResource(R.string.settings_listen_room), detail = room.id, showTopSeparator = false)
            MeloXIosListRow(stringResource(R.string.settings_listen_members), detail = "${room.users.size.coerceAtLeast(1)} 人")
            room.users.forEach { user -> MeloXIosListRow(user.name) }
            MeloXIosListRow(
                stringResource(R.string.settings_listen_connection),
                detail = when (state.phase) {
                    MeloXListenTogetherCoordinator.Phase.Connected -> stringResource(R.string.settings_listen_synced)
                    MeloXListenTogetherCoordinator.Phase.Reconnecting -> stringResource(R.string.settings_listen_reconnecting)
                    MeloXListenTogetherCoordinator.Phase.Idle -> stringResource(R.string.settings_listen_resuming)
                },
            )
            MeloXIosListRow(
                title = stringResource(R.string.settings_listen_solo),
                subtitle = stringResource(R.string.settings_listen_solo_note),
                leading = { MeloXSymbolIcon(MeloXSymbol.Person, Modifier.size(24.dp), MeloXSystemColors.Red) },
                trailing = {
                    MeloXGlassToggle(
                        checked = state.solo,
                        onCheckedChange = { MeloXListenTogetherCoordinator.setSolo(app, it) },
                    )
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        SettingsActionButton(stringResource(R.string.settings_listen_share)) {
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
        SettingsDangerButton(stringResource(R.string.settings_listen_end)) {
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
                    context.filesDir.resolve("automix_analysis"),
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
            Text(stringResource(R.string.settings_storage_managed), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            Text(if (loading) stringResource(R.string.settings_storage_calculating) else formatBytes(usage.managed), fontSize = 34.sp, fontWeight = FontWeight.Bold)
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
    Text(stringResource(R.string.settings_storage_items), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        StorageUsageRow(MeloXSymbol.Download, stringResource(R.string.settings_storage_downloads), usage.downloads, false)
        StorageUsageRow(MeloXSymbol.Apps, stringResource(R.string.settings_storage_network), usage.networkCache, true)
        StorageUsageRow(MeloXSymbol.AutoMix, stringResource(R.string.settings_storage_playback), usage.playbackCache, true)
        StorageUsageRow(MeloXSymbol.RadioWaves, stringResource(R.string.settings_storage_temporary), usage.temporary, true)
        StorageUsageRow(MeloXSymbol.Storage, stringResource(R.string.settings_storage_local), usage.localData, true)
    }
    Spacer(Modifier.height(14.dp))
    var playbackCacheLimit by remember {
        mutableStateOf(MeloXMediaCache.limitBytes(context))
    }
    Text(stringResource(R.string.settings_storage_playback_limit), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        listOf(256L, 512L, 1024L, 2048L).forEach { megabytes ->
            val bytes = megabytes * 1024L * 1024L
            val label = if (megabytes >= 1024L) "${megabytes / 1024L} GB" else "$megabytes MB"
            SettingsChoiceRow(label, playbackCacheLimit == bytes) {
                playbackCacheLimit = bytes
                MeloXSettingsPreferences.setLong(context, "playback_cache_limit_bytes", bytes)
                maintenanceMessage = "播放缓存上限已设为 $label，下次启动生效"
            }
        }
    }
    Text(
        stringResource(R.string.settings_storage_playback_limit_note),
        modifier = Modifier.padding(top = 6.dp),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .50f),
    )
    Spacer(Modifier.height(18.dp))
    Text(stringResource(R.string.settings_storage_cleanup), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        StorageActionRow(MeloXSymbol.Trash, stringResource(R.string.settings_storage_clear_all), false) { confirmation = "all_cache" }
        StorageActionRow(MeloXSymbol.Apps, stringResource(R.string.settings_storage_clear_network), true) { confirmation = "network_cache" }
        StorageActionRow(MeloXSymbol.AutoMix, stringResource(R.string.settings_storage_clear_playback), true) { confirmation = "playback_cache" }
    }
    Spacer(Modifier.height(18.dp))

    SettingsGlassGroup {
        SettingsToggleRow(context, stringResource(R.string.settings_storage_download_lyrics), "download_lyrics", true, stringResource(R.string.settings_storage_download_lyrics_note), grouped = true)
        SettingsExternalToggleRow(stringResource(R.string.settings_storage_auto_cache), autoCache, stringResource(R.string.settings_storage_auto_cache_note), grouped = true) {
            autoCache = it
            MeloXSettingsPreferences.setBoolean(context, "downloads_auto_cache", it)
        }
    }
    if (autoCache) {
        LyricsChoiceSetting(context, stringResource(R.string.settings_storage_threshold), "downloads_auto_cache_threshold", 3, listOf(2, 3, 5, 8, 10)) { "$it 次" }
        var cacheQuality by remember { mutableStateOf(MeloXSettingsPreferences.string(context, "downloads_auto_cache_quality", MusicQuality.Standard.name)) }
        Text(stringResource(R.string.settings_storage_auto_quality), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
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
        SettingsActionButton(stringResource(R.string.settings_storage_reset_stats)) { downloads.resetAutomaticCacheHistory() }
    }
    Spacer(Modifier.height(10.dp))

    if (downloads.activeDownloads.isNotEmpty() || downloads.pausedDownloads.isNotEmpty()) {
        Text(stringResource(R.string.settings_storage_downloading), modifier = Modifier.padding(top=10.dp,bottom=8.dp), fontWeight=FontWeight.SemiBold)
        SettingsGlassGroup {
            downloads.activeDownloads.values.forEach { active ->
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(active.song.name, maxLines=1, overflow=TextOverflow.Ellipsis)
                        Text(active.fractionCompleted?.let { "${(it*100).toInt()}% · ${active.quality.title}" } ?: active.quality.title, color=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f), fontSize=11.sp)
                    }
                    Text(stringResource(R.string.action_pause), color=MaterialTheme.colorScheme.primary, modifier=Modifier.clickable { downloads.pause(active.song.id) }.padding(8.dp))
                    Text(stringResource(R.string.action_cancel), color=MaterialTheme.colorScheme.error, modifier=Modifier.clickable { downloads.cancel(active.song.id) }.padding(8.dp))
                }
            }
            downloads.pausedDownloads.values.forEach { paused ->
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(paused.song.name, maxLines=1, overflow=TextOverflow.Ellipsis)
                        Text("${paused.fractionCompleted?.let { "${(it*100).toInt()}%" } ?: ""} · ${paused.quality.title}".trim(), color=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f), fontSize=11.sp)
                    }
                    Text(stringResource(R.string.action_resume), color=MaterialTheme.colorScheme.primary, modifier=Modifier.clickable { downloads.resume(paused.song.id) }.padding(8.dp))
                    Text(stringResource(R.string.action_cancel), color=MaterialTheme.colorScheme.error, modifier=Modifier.clickable { downloads.cancel(paused.song.id) }.padding(8.dp))
                }
            }
        }
    }

    if (downloads.downloads.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        SettingsDangerButton(stringResource(R.string.settings_storage_delete_all)) { downloads.removeAll() }
    }
    downloads.errorMessage?.let { Text(it, color=MaterialTheme.colorScheme.error, fontSize=12.sp, modifier=Modifier.padding(top=10.dp)) }
    maintenanceMessage?.let { Text(it, color=MaterialTheme.colorScheme.primary, fontSize=12.sp, modifier=Modifier.padding(top=10.dp)) }
    Spacer(Modifier.height(14.dp))
    SettingsActionButton(stringResource(R.string.settings_storage_repair)) {
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
    SettingsActionButton(stringResource(R.string.settings_storage_clear_temp)) {
        confirmation = "temporary"
    }
    confirmation?.let { action ->
        val (title, message) = when (action) {
            "all_cache" -> stringResource(R.string.settings_storage_confirm_all_title) to stringResource(R.string.settings_storage_confirm_all_body)
            "network_cache" -> stringResource(R.string.settings_storage_confirm_network_title) to stringResource(R.string.settings_storage_confirm_network_body)
            "playback_cache" -> stringResource(R.string.settings_storage_confirm_playback_title) to stringResource(R.string.settings_storage_confirm_playback_body)
            else -> stringResource(R.string.settings_storage_confirm_temp_title) to stringResource(R.string.settings_storage_confirm_temp_body)
        }
        MeloXGlassDialog(visible = true, onDismiss = { confirmation = null }) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(message, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionButton(stringResource(R.string.action_cancel), Modifier.weight(1f)) { confirmation = null }
                SettingsActionButton(stringResource(R.string.settings_storage_clear), Modifier.weight(1f)) {
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
        SettingsToggleRow(context, stringResource(R.string.settings_tabs_home), "tab_home", true, grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_tabs_explore), "tab_explore", true, grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_tabs_library), "tab_library", true, grouped = true)
    }
    listOf(
        Triple("播客", "podcasts", MeloXSettingsRuntime.podcastsEnabled),
        Triple("下载", "downloads", MeloXSettingsRuntime.downloadsEnabled),
        Triple("云盘", "cloud", MeloXSettingsRuntime.cloudMusicEnabled),
    ).forEach { (title, key, enabled) ->
        Spacer(Modifier.height(10.dp))
        SettingsGlassGroup {
            Text(title, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
            SettingsToggleRow(context, stringResource(R.string.settings_tabs_home_shortcut), "placement_${key}_home", key == "podcasts", if (enabled) null else "请先在内容功能中启用$title。", grouped = true)
            SettingsToggleRow(context, stringResource(R.string.settings_tabs_library_page), "placement_${key}_library", true, grouped = true)
            SettingsToggleRow(context, stringResource(R.string.settings_tabs_own_tab), "placement_${key}_tab", false, grouped = true)
        }
    }
    Spacer(Modifier.height(10.dp))
    var order by remember { mutableStateOf(MeloXSettingsRuntime.tabOrder) }
    SettingsGlassGroup {
        Text(stringResource(R.string.settings_tabs_order), modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        order.forEachIndexed { index, page ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(when (page) { "Home" -> stringResource(R.string.settings_tabs_home); "Explore" -> stringResource(R.string.settings_tabs_explore); "Library" -> stringResource(R.string.settings_tabs_library); "Podcasts" -> "播客"; "Downloads" -> "下载"; "Cloud" -> "云盘"; else -> stringResource(R.string.settings_tabs_settings) }, Modifier.weight(1f))
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
        Text(stringResource(R.string.settings_tabs_home_sections), modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        SettingsToggleRow(context, stringResource(R.string.settings_tabs_shortcuts), "home_quick_actions", true, grouped = true)
        SettingsToggleRow(context, "推荐歌单", "home_playlists", true, grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_tabs_new_songs), "home_new_songs", true, grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_tabs_remember_library), "library_remember_page", true, grouped = true)
        homeOrder.forEachIndexed { index, section ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(when (section) { "QuickActions" -> stringResource(R.string.settings_tabs_shortcuts); "Playlists" -> "推荐歌单"; else -> stringResource(R.string.settings_tabs_new_songs) }, Modifier.weight(1f))
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
            title = stringResource(R.string.settings_tabs_default_library),
            selected = libraryPage,
            items = listOf("Songs" to stringResource(R.string.settings_tabs_songs), "Playlists" to stringResource(R.string.settings_tabs_playlist_page), "Podcasts" to "播客", "Cloud" to "云盘", "History" to stringResource(R.string.settings_feature_history), "Downloads" to "下载")
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
        Text(stringResource(R.string.settings_tabs_search_note), modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f))
    }
}

@Composable
private fun GeneralSettings(context: android.content.Context) {
    var theme by remember { mutableStateOf(MeloXSettingsRuntime.themeMode) }
    var swipeFullAction by remember { mutableStateOf(MeloXSettingsRuntime.swipeFullAction) }
    SettingsGlassGroup {
        if (Build.VERSION.SDK_INT >= 33) {
            val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
            val selectedLanguage = localeManager.applicationLocales.toLanguageTags()
            MeloXSettingsDropdown(
                title = stringResource(R.string.settings_general_language),
                selected = selectedLanguage,
                items = listOf(
                    "" to stringResource(R.string.settings_general_language_system),
                    "zh-Hans" to "简体中文",
                    "zh-Hant" to "繁體中文",
                    "en" to "English",
                    "ja" to "日本語",
                    "tr" to "Türkçe",
                ),
                onSelected = { localeManager.applicationLocales = android.os.LocaleList.forLanguageTags(it) },
                grouped = true,
            )
        } else {
            MeloXIosListRow(
                title = stringResource(R.string.settings_general_language),
                subtitle = stringResource(R.string.settings_general_language_legacy),
                onClick = { context.startActivity(Intent(AndroidSettings.ACTION_LOCALE_SETTINGS)) },
                showTopSeparator = false,
            )
        }
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_general_theme),
            selected = theme,
            items = listOf(
                MeloXThemeMode.System to stringResource(R.string.settings_general_theme_system),
                MeloXThemeMode.Light to stringResource(R.string.settings_general_theme_light),
                MeloXThemeMode.Dark to stringResource(R.string.settings_general_theme_dark),
            ),
            onSelected = { theme = it; MeloXSettingsPreferences.setString(context, "theme_mode", it.name) },
            grouped = true,
        )
        SettingsToggleRow(
            context,
            stringResource(R.string.settings_split_screen),
            "general_split_screen",
            true,
            stringResource(R.string.settings_split_screen_hint),
            grouped = true,
        )
        if (MeloXSettingsRuntime.splitScreenEnabled) {
            val positions = listOf(
                MeloXSplitChromePosition.BottomLeft to stringResource(R.string.settings_position_bottom_left),
                MeloXSplitChromePosition.BottomRight to stringResource(R.string.settings_position_bottom_right),
                MeloXSplitChromePosition.Left to stringResource(R.string.settings_position_left),
                MeloXSplitChromePosition.Right to stringResource(R.string.settings_position_right),
                MeloXSplitChromePosition.TopLeft to stringResource(R.string.settings_position_top_left),
                MeloXSplitChromePosition.TopRight to stringResource(R.string.settings_position_top_right),
            )
            MeloXSettingsDropdown(
                title = stringResource(R.string.settings_split_navigation_position),
                selected = MeloXSettingsRuntime.splitNavigationPosition,
                items = positions,
                onSelected = { MeloXSettingsPreferences.setString(context, "general_split_navigation_position", it.name) },
                grouped = true,
            )
            MeloXSettingsDropdown(
                title = stringResource(R.string.settings_split_mini_player_position),
                selected = MeloXSettingsRuntime.splitMiniPlayerPosition,
                items = positions,
                onSelected = { MeloXSettingsPreferences.setString(context, "general_split_mini_player_position", it.name) },
                grouped = true,
            )
        }
        var defaultTab by remember { mutableStateOf(MeloXSettingsRuntime.defaultTab) }
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_general_launch),
            selected = defaultTab,
            items = listOf(
                "Home" to stringResource(R.string.settings_tabs_home),
                "Explore" to stringResource(R.string.settings_tabs_explore),
                "Library" to stringResource(R.string.settings_tabs_library),
                "Settings" to stringResource(R.string.settings_tabs_settings),
            ),
            onSelected = { defaultTab = it; MeloXSettingsPreferences.setString(context, "general_default_tab", it) },
            grouped = true,
        )
        SettingsToggleRow(context, stringResource(R.string.settings_general_remember_tab), "general_remember_tab", true, grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_artwork_tap_lyrics), "general_artwork_tap_lyrics", false, grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_artwork_swipe_tracks), "general_artwork_swipe_tracks", false, grouped = true)
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_general_swipe),
            selected = swipeFullAction,
            items = listOf(
                MeloXSwipeFullAction.PlayNext to stringResource(R.string.settings_general_play_next),
                MeloXSwipeFullAction.AddToQueue to stringResource(R.string.settings_general_add_queue),
            ),
            onSelected = {
                swipeFullAction = it
                MeloXSettingsPreferences.setString(context, "general_swipe_full_action", it.name)
            },
            grouped = true,
        )
        SettingsToggleRow(context, stringResource(R.string.settings_general_clipboard), "general_clipboard_links", true, stringResource(R.string.settings_general_clipboard_note), grouped = true)
        SettingsToggleRow(context, stringResource(R.string.settings_general_haptics), "general_haptic_feedback", true, grouped = true)
        MeloXSettingsDropdown(
            title = stringResource(R.string.settings_general_system_font),
            selected = MeloXSettingsRuntime.systemFontScope,
            items = listOf(
                "off" to "关闭",
                "nonlyrics" to "除歌词外",
                "all" to "全局",
            ),
            onSelected = { MeloXSettingsPreferences.setString(context, "system_font_scope", it) },
            grouped = true,
        )
        SettingsToggleRow(
            context,
            stringResource(R.string.settings_general_tabbar),
            "general_disable_auto_tabbar_shrink",
            false,
            stringResource(R.string.settings_general_tabbar_note),
            grouped = true,
        )
    }
}

@Composable
fun MeloXRecognitionPanel(context: android.content.Context) {
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
        listOf(3 to stringResource(R.string.settings_recognition_seconds_fast), 6 to stringResource(R.string.settings_recognition_seconds_recommended), 9 to stringResource(R.string.settings_recognition_seconds_noisy)).forEach { (value, title) ->
            SettingsChoiceRow(title, duration == value) {
                if (!working) {
                    duration = value
                    MeloXSettingsPreferences.setString(context, "recognition_duration", value.toString())
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    SettingsActionButton(if (working) stringResource(R.string.settings_recognition_stop) else stringResource(R.string.settings_recognition_start)) {
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
        Text(stringResource(R.string.settings_recognition_results), fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
        stringResource(R.string.settings_remote_declined)
    } else when (status.source) {
        MeloXRemoteConfigSource.BuiltIn -> stringResource(R.string.settings_remote_builtin)
        MeloXRemoteConfigSource.VerifiedRemote -> stringResource(R.string.settings_remote_verified)
        MeloXRemoteConfigSource.VersionInapplicable -> stringResource(R.string.settings_remote_inapplicable)
    }
    SettingsGlassGroup {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(source, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.settings_remote_summary),
                modifier = Modifier.padding(top = 7.dp),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    SettingsGlassGroup {
        RemoteConfigStatusLine(stringResource(R.string.settings_remote_version), status.config.configVersion.toString())
        RemoteConfigStatusLine(
            stringResource(R.string.settings_remote_access),
            githubRouting.effectiveRoute()?.takeIf { githubRouting.selectedSource() == MeloXGitHubSource.Auto }
                ?.let { "${it.source.label}（${it.latencyMs}ms）" }
                ?: githubRouting.selectedSource().label,
        )
        RemoteConfigStatusLine(stringResource(R.string.settings_remote_key), status.keyId ?: stringResource(R.string.settings_remote_builtin_key))
        RemoteConfigStatusLine(stringResource(R.string.settings_remote_last_check), formatRemoteConfigTime(status.lastCheckedAtEpochMs))
        RemoteConfigStatusLine(stringResource(R.string.settings_remote_last_update), formatRemoteConfigTime(status.lastUpdatedAtEpochMs))
        RemoteConfigStatusLine(
            stringResource(R.string.settings_remote_breakers),
            status.config.disabledCapabilities.takeIf(Set<String>::isNotEmpty)?.joinToString("、") ?: "无",
        )
        RemoteConfigStatusLine(stringResource(R.string.settings_remote_fallback_order), status.config.fallback.order.joinToString(" → "))
        RemoteConfigStatusLine(stringResource(R.string.settings_remote_timeout), "${status.config.fallback.timeoutMs} ms")
        RemoteConfigStatusLine("热更新", hotUpdateSummary())
    }
    status.error?.let { error ->
        Spacer(Modifier.height(12.dp))
        SettingsInfoCard("最近检查失败：$error\n已继续使用上一次有效配置或内置默认值。")
    }
    Spacer(Modifier.height(12.dp))
    SettingsActionButton(
        when {
            !consentEnabled -> stringResource(R.string.settings_remote_enable_first)
            status.refreshing -> stringResource(R.string.settings_about_checking)
            else -> stringResource(R.string.settings_remote_check)
        },
    ) {
        if (consentEnabled && !status.refreshing) {
            scope.launch { MeloXRemoteConfigRuntime.refresh(force = true) }
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsDangerButton(stringResource(R.string.settings_remote_clear)) {
        scope.launch { MeloXRemoteConfigRuntime.clearCache(context) }
    }
}

/**
 * Compact view of what the signed hot-update channel currently contributes, so
 * a maintainer can confirm a payload landed without digging through logs.
 */
@Composable
private fun hotUpdateSummary(): String {
    val text by com.lladlam.melox.core.remoteconfig.MeloXHotStrings.overrides.collectAsState()
    val colors by com.lladlam.melox.core.remoteconfig.MeloXHotStyles.colors.collectAsState()
    val scripts by com.lladlam.melox.core.remoteconfig.MeloXHotScripts.scripts.collectAsState()
    val rules by com.lladlam.melox.core.remoteconfig.MeloXHotRules.rules.collectAsState()
    val neteaseStrategy = com.lladlam.melox.core.remoteconfig.MeloXHotStrategies.value("netease_playback")
    return buildString {
        append("文案 ").append(text.values.sumOf { it.size })
        append(" · 颜色 ").append(colors.size)
        append(" · 脚本 ").append(scripts.size)
        append(" · 后缀 ").append(rules.searchTitleStripSuffixes.size)
        append(" · 网易云 ").append(neteaseStrategy)
        val overridden = text.values.sumOf { it.size } + colors.size + scripts.size +
            rules.searchTitleStripSuffixes.size
        if (overridden == 0 && neteaseStrategy == "v1") append("（未覆盖）")
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
    var showReleaseNotes by remember { mutableStateOf(false) }
    var showLogViewer by remember { mutableStateOf(false) }
    var logViewerLoading by remember { mutableStateOf(false) }
    var logViewerText by remember { mutableStateOf<String?>(null) }
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
            Text(hotText("about.maintainer", stringResource(R.string.settings_about_maintainer)), modifier = Modifier.padding(top=14.dp), fontWeight=FontWeight.SemiBold)
            Text(hotText("about.upstream", stringResource(R.string.settings_about_upstream)), modifier = Modifier.padding(top=5.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha=.58f))
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.settings_about_sponsors_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            listOf(
                stringResource(R.string.settings_about_sponsor_26d40) to stringResource(R.string.settings_about_sponsor_amount_50),
                stringResource(R.string.settings_about_sponsor_aec4c) to stringResource(R.string.settings_about_sponsor_amount_6),
                stringResource(R.string.settings_about_sponsor_bai) to stringResource(R.string.settings_about_sponsor_amount_5),
                stringResource(R.string.settings_about_sponsor_zhongmo) to stringResource(R.string.settings_about_sponsor_amount_8_88),
                stringResource(R.string.settings_about_sponsor_361ef) to stringResource(R.string.settings_about_sponsor_amount_20),
                stringResource(R.string.settings_about_sponsor_f8b67) to stringResource(R.string.settings_about_sponsor_amount_12_24),
            ).forEach { (name, amount) ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .78f))
                    Text(amount, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f), fontSize = 13.sp)
                }
            }
            Text(
                stringResource(R.string.settings_about_sponsors_note),
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
    if (showCatEgg) {
        MeloXGlassDialog(visible = true, onDismiss = { showCatEgg = false }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                MeloXPinkCat()
                Text(hotText("about.cat_title", stringResource(R.string.settings_about_cat_title)), style = MaterialTheme.typography.titleLarge)
                Text(hotText("about.cat_body", stringResource(R.string.settings_about_cat_body)), modifier = Modifier.padding(top = 7.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                MeloXGlassButton(
                    onClick = { showCatEgg = false },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    style = MeloXGlassButtonStyle.BorderedProminent,
                ) { Text(stringResource(R.string.settings_about_cat_accept)) }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    SettingsToggleRow(context, stringResource(R.string.settings_about_auto_update), "update_auto_check", true, stringResource(R.string.settings_about_auto_update_note))
    Spacer(Modifier.height(10.dp))
    MeloXSettingsDropdown(
        title = stringResource(R.string.settings_about_github_source),
        selected = downloadSource,
        items = listOf(
            MeloXGitHubSource.Auto to stringResource(R.string.settings_about_source_auto),
            MeloXGitHubSource.GitHubDoh to stringResource(R.string.settings_about_source_doh),
            MeloXGitHubSource.GhFast to stringResource(R.string.settings_about_source_ghfast),
            MeloXGitHubSource.GhProxy to stringResource(R.string.settings_about_source_ghproxy),
            MeloXGitHubSource.GhProxyOrg to stringResource(R.string.settings_about_source_ghproxy_org),
        ),
        onSelected = {
            downloadSource = it
            githubRouting.selectSource(it)
        },
    )
    Text(
        githubRouting.effectiveRoute()?.takeIf { downloadSource == MeloXGitHubSource.Auto }?.let {
            "自动测速当前选择 ${it.source.label}（${it.latencyMs}ms）；更新检查、APK 下载与已授权的签名配置共用此源。"
        } ?: stringResource(R.string.settings_about_source_auto_note),
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
    )
    SettingsActionButton(if (checking) stringResource(R.string.settings_about_checking) else stringResource(R.string.settings_about_check_update)) {
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
    val currentCommit = BuildConfig.GIT_SHA.take(7)
    if (currentCommit.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            text = "当前构建 commit $currentCommit（开发版）",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f),
        )
    }
    var devCommit by remember { mutableStateOf<MeloXDevCommit?>(null) }
    SettingsActionButton(stringResource(R.string.settings_about_check_dev)) {
        if (!checking) scope.launch {
            checking = true
            runCatching { updateClient.latestDevCommit() }
                .onSuccess { latest ->
                    devCommit = latest
                    updateStatus = if (latest.sha == BuildConfig.GIT_SHA) {
                        "已经是最新提交 ${latest.sha.take(7)}"
                    } else {
                        "main 有新提交 ${latest.sha.take(7)}：${latest.message}"
                    }
                }
                .onFailure { updateStatus = it.message ?: "检查最新提交失败" }
            checking = false
        }
    }
    devCommit?.takeIf { it.sha != BuildConfig.GIT_SHA }?.let { latest ->
        Spacer(Modifier.height(10.dp))
        SettingsActionButton(stringResource(R.string.settings_about_download_dev)) {
            scope.launch {
                val target = runCatching { updateClient.devBuildUrl() }.getOrNull()
                    ?: "https://github.com/lladlam/MeloX-Android/actions/workflows/build.yml"
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                    .onFailure { updateStatus = it.message ?: "无法打开下载页" }
            }
        }
        Text(
            text = "新提交 ${latest.sha.take(7)}（${latest.author}）：${latest.message}",
            modifier = Modifier.padding(top = 8.dp),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
        )
    }
    updateStatus?.let { message ->
        Spacer(Modifier.height(10.dp))
        Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
    }
    release?.takeIf { updateClient.isNewer(it.version, BuildConfig.VERSION_NAME) }?.let { available ->
        Spacer(Modifier.height(10.dp))
        var deltaPatch by remember(available.version) {
            mutableStateOf<com.lladlam.melox.core.remoteconfig.MeloXRemoteUpdatePatch?>(null)
        }
        LaunchedEffect(available.version) {
            deltaPatch = withContext(Dispatchers.IO) {
                val patch = com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigRuntime.status.value
                    .config.updatePatches.firstOrNull { it.toVersion == available.version }
                    ?: return@withContext null
                val installed = com.lladlam.melox.core.update.MeloXApkDelta.installedApkSha256(context)
                    ?: return@withContext null
                patch.takeIf { it.fromApkSha256.equals(installed, ignoreCase = true) }
            }
        }
        deltaPatch?.let { patch ->
            SettingsActionButton(
                "增量更新 ${available.version}（约 %.0f MB）".format(patch.patchBytes / 1024.0 / 1024.0),
            ) {
                scope.launch {
                    updateStatus = "正在下载增量补丁…"
                    val patchFile = updateClient.downloadPatch(context, patch)
                    if (patchFile == null) {
                        updateStatus = "增量补丁下载失败，可用整包下载"
                        return@launch
                    }
                    updateStatus = "正在重建安装包…"
                    val apk = withContext(Dispatchers.IO) {
                        com.lladlam.melox.core.update.MeloXApkDelta.reconstruct(
                            context = context,
                            patch = patchFile,
                            expectedApkSha256 = patch.apkSha256,
                            versionName = available.version,
                        )
                    }
                    if (apk == null) {
                        updateStatus = "增量重建校验失败，请用整包下载"
                        return@launch
                    }
                    updateStatus = if (com.lladlam.melox.core.update.MeloXApkDelta.launchInstaller(context, apk)) {
                        "已打开系统安装器"
                    } else {
                        "无法打开安装器，请用整包下载"
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
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
    SettingsActionButton(stringResource(R.string.settings_about_restore_player)) {
        MeloXSettingsPreferences.resetRecommendedPlayerSettings(context)
        updateStatus = "已恢复推荐播放器、歌词与 AutoMix 配置"
    }
    Spacer(Modifier.height(14.dp))
    SettingsGlassGroup {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_about_licenses_title), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "MeloX 主体：GNU GPLv3\n" +
                    "qier222/YesPlayMusic：网易云接口与播放器实现参考（MIT）\n" +
                    "jayfunc/BetterLyrics：逐字歌词渲染、光效与动效参考\n" +
                    "WXRIW/Lyricify-Lyrics-Helper：QQ 匿名搜索与歌词协议、纯音乐占位识别、网易云 YRC、Lyricify Lines / Syllable 格式参考（Apache-2.0）\n" +
                    "https://github.com/WXRIW/Lyricify-Lyrics-Helper\n" +
                    "megadonkatsu/YTRear：实验性独立背屏应用 MeloX Rear 的源码来源，上游未声明许可证\n" +
                    "https://github.com/megadonkatsu/YTRear\n" +
                    "neteasecloudmusicapienhanced/api-enhanced：听歌识曲与音频指纹运行时\n" +
                    "DanteAlighieri13210914/pv-tool：文字 PV 原始实现（Non-Commercial License）\n" +
                     "mjhydri/BeatNet：自动混音节拍/重拍/速度分析（CC BY 4.0）\n" +
                     "NEORUAA/MeiloX：基于 Mei 的仿 Apple Music 网易云音乐客户端，提供 UI 参考\n" +
                     "lladlam/Square：Spotify 与 YouTube Music 双后端架构参考\n" +
                     "YouTube Music：基于 Square 架构，使用 vendored Metrolist InnerTube（GPL-3.0）与 NewPipeExtractor\n" +
                     "Spotify：参考 Square Spotify backend，使用 librespot-java（Apache-2.0）\n" +
                     "MakcRe/KuGouMusicApi：酷狗概念版协议、签名与每日 VIP 领取／升级接口参考（MIT）\n" +
                     "guowenye/qishui-api：汽水音乐接口、扫码登录、目录、歌词与播放字段参考（MIT）\n" +
                     "MoeKoeMusic/MoeKoeMusic：概念版桌面账号与签到流程参考（GPL-2.0）\n" +
                     "MoeKoeMusic/MoeKoeMusic-mobile：移动端签到流程与结果处理参考（GPL-2.0）；MoeKoe 项目仅作行为参考，未复制代码或界面资源\n" +
                     "lladlam/MeloX-AutoSync：本地逐字歌词对齐与发音映射参考\n" +
                     "Microsoft ONNX Runtime：本地歌词对齐推理（MIT）\n" +
                     "AndroidX / Jetpack Compose / Media3：界面、生命周期与播放基础（Apache-2.0）\n" +
                     "Jellyfin androidx-media：预编译 Media3 FFmpeg 音频解码器，受影响 Android 8.1 设备的 FLAC 软件解码回退（GPL-3.0）\n" +
                     "Coil / OkHttp / kotlinx.coroutines：封面加载、网络与协程（Apache-2.0）\n" +
                     "HarlonWang/quickjs-wrapper：LX Music 兼容音源脚本隔离运行时（各组件保留原许可证）\n" +
                     "ZXing：二维码生成（Apache-2.0）\n" +
                     "Shizuku：可选的免 Root HyperOS 兼容接入（Apache-2.0）\n" +
                     "vivo OriginOS：自动尝试原子岛通知适配，不提供单独开关\n" +
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
    SettingsActionButton(stringResource(R.string.settings_about_release_notes)) {
        showReleaseNotes = true
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton(stringResource(R.string.settings_about_github)) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lladlam/MeloX-Android"))) }
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton(stringResource(R.string.settings_about_ios)) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/youshen2/MeloX"))) }
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton(stringResource(R.string.settings_about_upstream_licenses)) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/youshen2/MeloX/blob/main/MeloX/Features/Legal/ProjectLicensesView.swift"))) }
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton(stringResource(R.string.settings_about_qq_group)) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://qm.qq.com/q/wbhFQxj7mo"))) }
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton(stringResource(R.string.settings_about_sponsor)) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ifdian.net/a/lladlam"))) }
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton(stringResource(R.string.settings_about_view_logs)) {
        if (!logViewerLoading && !showLogViewer) {
            showLogViewer = true
            logViewerLoading = true
            logViewerText = null
            scope.launch {
                logViewerText = withContext(Dispatchers.IO) {
                    runCatching { MeloXLogExporter.previewRecentLogs(context) }
                        .getOrElse { error -> context.getString(R.string.settings_about_export_failed) + "：${error.message}" }
                }
                logViewerLoading = false
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton(if (exportingLogs) stringResource(R.string.settings_about_exporting) else stringResource(R.string.settings_about_export)) {
        if (!exportingLogs) showLogExportInfo = true
    }

    if (showLogExportInfo) {
        val deviceInfo: MeloXLogDeviceInfo = MeloXLogExporter.collectDeviceInfo(context)
        MeloXGlassDialog(
            visible = true,
            onDismiss = { showLogExportInfo = false },
        ) {
            Text(stringResource(R.string.settings_about_export), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_about_export_body),
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
                SettingsActionButton(stringResource(R.string.action_cancel), Modifier.weight(1f)) { showLogExportInfo = false }
                SettingsActionButton(stringResource(R.string.settings_about_choose_location), Modifier.weight(1f)) {
                    showLogExportInfo = false
                    exportingLogs = true
                    exportLogsLauncher.launch("MeloX-logs-${System.currentTimeMillis()}.txt")
                }
            }
        }
    }
    if (showReleaseNotes) {
        val notes = remember { MeloXReleaseNotesCatalog.current() }
        MeloXGlassDialog(visible = true, onDismiss = { showReleaseNotes = false }) {
            Text(stringResource(R.string.settings_release_notes_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.settings_release_notes_version, notes.version),
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                fontSize = 13.sp,
            )
            Column(
                Modifier
                    .padding(top = 12.dp)
                    .height(320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                notes.entries.forEach { entry ->
                    Text(
                        "• $entry",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .78f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
            SettingsActionButton(stringResource(R.string.action_done), Modifier.fillMaxWidth().padding(top = 12.dp)) {
                showReleaseNotes = false
            }
        }
    }
    if (showLogViewer) {
        MeloXGlassDialog(visible = true, onDismiss = { showLogViewer = false }) {
            Text(stringResource(R.string.settings_about_logs_title), style = MaterialTheme.typography.titleLarge)
            if (logViewerLoading) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.settings_about_exporting), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                }
            } else {
                Column(
                    Modifier
                        .padding(top = 12.dp)
                        .height(360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        logViewerText.orEmpty(),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .78f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
            }
            SettingsActionButton(stringResource(R.string.action_done), Modifier.fillMaxWidth().padding(top = 12.dp)) {
                showLogViewer = false
            }
        }
    }
}

@Composable
private fun RearDisplaySettings(context: android.content.Context) {
    var error by remember { mutableStateOf<String?>(null) }
    SettingsGlassGroup {
        Column(Modifier.padding(16.dp)) {
            Text("MeloX Rear · 实验版", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "独立背屏控制应用，基于 YTRear。安装后需授予通知读取权限并选择 MeloX；" +
                    "它会暂缓原播放器媒体通知，改由代理通知显示。\n\n" +
                    "此应用使用 com.luna.music 包名，不能与汽水音乐同时安装。" +
                    "HyperOS 4 与 MeloX 尚未真机验证；当前 APK 为 Debug 签名，后续版本可能无法覆盖安装。",
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    fun open(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { error = it.message ?: "无法打开下载链接" }
    }
    SettingsActionButton("下载 MeloX Rear 0.1.0 APK") {
        open("https://github.com/lladlam/YTRear/releases/download/melox-rear-v0.1.0/MeloX-Rear-0.1.0-debug.apk")
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton("查看背屏应用发布页") {
        open("https://github.com/lladlam/YTRear/releases")
    }
    error?.let { Text(it, modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.error) }
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
            title = stringResource(R.string.settings_dev_overlay),
            key = "developer_performance_overlay",
            default = false,
            note = stringResource(R.string.settings_dev_overlay_note),
        )
        SettingsExternalToggleRow(
            title = stringResource(R.string.settings_dev_automix),
            value = diagnosticsVisible,
            note = stringResource(R.string.settings_dev_automix_note),
        ) {
            diagnosticsVisible = it
            MeloXSettingsPreferences.setBoolean(context, "developer_automix_diagnostics", it)
        }
        if (diagnosticsVisible) {
            Spacer(Modifier.height(8.dp))
            SettingsActionButton(stringResource(R.string.settings_dev_refresh)) { diagnostics = MeloXAutoMixDiagnostics.snapshot() }
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
    val effectiveShowTopSeparator = remember(grouped, showTopSeparator) {
        when {
            showTopSeparator -> true
            grouped -> groupRowIndex.intValue++.let { it > 0 }
            else -> false
        }
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
    val effectiveShowTopSeparator = remember(grouped) { if (grouped) groupRowIndex.intValue++.let { it > 0 } else false }
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
    SettingsDangerButton(stringResource(R.string.settings_reset_player)) {
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
