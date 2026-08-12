package com.lladlam.melox.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.lladlam.melox.BuildConfig
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.network.MeloXMessageContact
import com.lladlam.melox.core.network.MeloXPrivateMessage
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.recognition.SongRecognitionClient
import com.lladlam.melox.core.recognition.SongRecognitionResult
import com.lladlam.melox.core.update.MeloXRelease
import com.lladlam.melox.core.update.MeloXUpdateClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.playback.MeloXAutoMixFadeCurve
import com.lladlam.melox.playback.MeloXAutoMixFallback
import com.lladlam.melox.playback.MeloXAutoMixMode
import com.lladlam.melox.playback.MeloXAutoMixSettings
import com.lladlam.melox.playback.MeloXEqualizerController
import com.lladlam.melox.playback.MeloXPlaybackModePreferences
import com.lladlam.melox.platform.floating.MeloXFloatingLyricsService
import com.lladlam.melox.platform.xiaomi.HyperOsFocusBridge
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.glass.meloXLiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsRoute(val title: String) {
    Playback("播放与音频"),
    PlayerAppearance("播放器外观"),
    Lyrics("歌词"),
    SystemPlayback("系统歌词显示"),
    SkylineLyrics("全屏天际歌词"),
    FloatingLyrics("悬浮窗歌词"),
    ContentFeatures("功能模块"),
    Recognition("听歌识曲"),
    Messages("私信与站内分享"),
    Content("发现内容"),
    Storage("存储管理"),
    TabLayout("页面与标签栏"),
    General("通用"),
    About("关于 MeloX"),
    Developer("开发者选项"),
}

private data class SettingsItem(
    val route: SettingsRoute,
    val subtitle: String,
    val symbol: String,
    val keywords: String,
)

private data class SettingsSection(val title: String, val items: List<SettingsItem>)

private val SettingsSections = listOf(
    SettingsSection("播放与声音", listOf(
        SettingsItem(SettingsRoute.Playback, "音质、播放行为与自动混音", "♫", "高品质 无损 上一首 页面记忆 心动模式 交叉淡化"),
        SettingsItem(SettingsRoute.PlayerAppearance, "背景、封面动画与屏幕常亮", "✦", "模糊 色彩 饱和度 封面 自动锁屏"),
    )),
    SettingsSection("歌词与显示", listOf(
        SettingsItem(SettingsRoute.Lyrics, "翻译、罗马音、逐字与歌词交互", "❞", "Apple Music EVA 文字PV 字体 YRC 翻译 罗马音"),
        SettingsItem(SettingsRoute.SystemPlayback, "通知、锁屏和系统媒体信息", "▣", "控制中心 通知 锁屏 Media3"),
        SettingsItem(SettingsRoute.SkylineLyrics, "横屏布局与动态背景歌词", "▱", "横屏 字号 背景歌词"),
        SettingsItem(SettingsRoute.FloatingLyrics, "Android 悬浮歌词能力与权限", "▤", "画中画 悬浮窗 其他应用"),
    )),
    SettingsSection("内容与存储", listOf(
        SettingsItem(SettingsRoute.ContentFeatures, "播客、云盘、最近播放等模块", "☷", "播客 广播 云盘 最近播放 下载"),
        SettingsItem(SettingsRoute.Recognition, "麦克风音频指纹与持续识别", "⌁", "听歌识曲 麦克风 指纹 Shazam 持续识别"),
        SettingsItem(SettingsRoute.Messages, "联系人、会话历史与文字私信", "✉", "私信 联系人 会话 分享 网易云"),
        SettingsItem(SettingsRoute.Content, "地区、歌单信息和发现内容", "▦", "华语 欧美 韩国 日本 播放量"),
        SettingsItem(SettingsRoute.Storage, "空间统计与缓存清理", "▰", "缓存 存储 清理 数据库"),
    )),
    SettingsSection("界面与应用", listOf(
        SettingsItem(SettingsRoute.TabLayout, "首页、标签栏与音乐库页面", "▥", "首页 标签栏 排序 推荐 歌单 历史"),
        SettingsItem(SettingsRoute.General, "主题、启动行为与链接处理", "⚙", "主题 浅色 深色 跟随系统 默认页面 剪贴板"),
    )),
    SettingsSection("关于与开发", listOf(
        SettingsItem(SettingsRoute.About, "版本、项目主页与开源信息", "ⓘ", "GitHub 更新 开源 许可"),
        SettingsItem(SettingsRoute.Developer, "播放器诊断与迁移状态", "⌘", "BeatNet 节拍 调试 日志"),
    )),
)

@Composable
fun SettingsScreen(
    session: NeteaseSessionStore,
    onLogin: () -> Unit,
) {
    val context = LocalContext.current
    var route by remember { mutableStateOf<SettingsRoute?>(null) }
    var search by remember { mutableStateOf("") }
    // Keep the root ScrollState alive while a detail route is displayed.
    // Creating it inside the root-only branch reset Settings to y=0 on Back.
    val rootScrollState = rememberScrollState()

    LaunchedEffect(Unit) { MeloXSettingsPreferences.initialize(context) }
    LaunchedEffect(session.cookie) {
        if (session.isLoggedIn) session.refreshProfile()
    }

    BackHandler(enabled = route != null) { route = null }

    if (route != null) {
        SettingsDetailScreen(route = route!!, onBack = { route = null })
        return
    }

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
            .padding(top = 34.dp, bottom = MeloXBottomContentClearance),
    ) {
        Text("设置", fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        SettingsSearchField(search, { search = it })
        Spacer(Modifier.height(22.dp))

        if (normalized.isBlank() || "网易云账号 登录 cookie 用户".contains(normalized)) {
            SettingsAccountCard(session = session, onLogin = onLogin)
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
            SettingsSectionCard(section) { route = it }
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

@Composable
private fun SettingsSearchField(value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .meloXLiquidButton(
                shape = RoundedCornerShape(24.dp),
                surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.055f),
                lensRadius = 9.dp,
                refractionHeight = 15.dp,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("⌕", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isBlank()) Text("搜索设置", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingsAccountCard(session: NeteaseSessionStore, onLogin: () -> Unit) {
    Text(
        "网易云音乐账号",
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .meloXLiquidButton(
                shape = RoundedCornerShape(28.dp),
                enabled = !session.isLoggedIn,
                surfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                lensRadius = 10.dp,
                refractionHeight = 18.dp,
            )
            .clickable(enabled = !session.isLoggedIn, onClick = onLogin),
        shape = RoundedCornerShape(28.dp), color = Color.Transparent,
    ) {
        when {
            session.isLoggedIn && session.profile != null -> {
                val profile = session.profile!!
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = profile.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(58.dp).clip(CircleShape),
                    )
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.nickname, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            "用户 ID ${profile.userId} · 账号信息与同步",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("›", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }
            session.isLoggedIn && session.isRefreshing -> Row(
                Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(32.dp))
                Spacer(Modifier.size(14.dp))
                Text("正在读取账号信息")
            }
            else -> Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) { Text("＋", fontSize = 30.sp) }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("登录网易云音乐", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("同步收藏、歌单与播放记录", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                }
                Text("›", fontSize = 28.sp)
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(section: SettingsSection, onOpen: (SettingsRoute) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .meloXLiquidButton(
                shape = RoundedCornerShape(26.dp),
                surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.045f),
                lensRadius = 10.dp,
                refractionHeight = 17.dp,
            )
            .padding(vertical = 5.dp),
    ) {
        section.items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(item.route) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Text(item.symbol, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.route.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(item.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f), maxLines = 2)
                }
                Text("›", fontSize = 25.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .28f))
            }
        }
    }
}

@Composable
private fun SettingsDetailScreen(route: SettingsRoute, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = MeloXBottomContentClearance),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsRoundButton("‹", onBack)
            Spacer(Modifier.size(14.dp))
            Text(route.title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
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
            SettingsRoute.Content -> ContentSettings(context)
            SettingsRoute.Storage -> StorageSettings(context)
            SettingsRoute.TabLayout -> TabLayoutSettings(context)
            SettingsRoute.General -> GeneralSettings(context)
            SettingsRoute.About -> AboutSettings(context)
            SettingsRoute.Developer -> DeveloperSettings()
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

    SettingsExternalToggleRow(
        title = "系统媒体信息显示歌词",
        value = systemLyrics,
        note = "播放时把当前歌词同步到 Media3 元数据；应用内仍显示原歌曲名和歌手。",
    ) {
        systemLyrics = it
        MeloXSettingsPreferences.setBoolean(context, "system_lyrics_enabled", it)
    }
    SettingsExternalToggleRow(
        title = "独立歌词通知",
        value = notifications,
        note = "在通知栏和锁屏持续更新当前歌词；Android 13 及以上需要通知权限。",
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
    val protocol = remember { HyperOsFocusBridge.protocol(context) }
    SettingsInfoCard(
        "HyperOS",
        if (protocol == HyperOsFocusBridge.Protocol.Unsupported) {
            "当前系统未公开焦点通知协议；继续使用标准 Media3 通知。"
        } else {
            "已检测到 HyperOS ${protocol.version} 焦点通知协议，歌词通知会附带实时播放负载。"
        },
    )
    LyricsStringChoiceSetting(
        context,
        "系统媒体标题格式",
        "system_lyrics_title_mode",
        MeloXSystemLyricTitleMode.LyricFirst.name,
        MeloXSystemLyricTitleMode.entries.map { it.name },
    ) { if (it == MeloXSystemLyricTitleMode.LyricFirst.name) "歌词作为标题" else "歌曲作为标题" }
    SettingsToggleRow(context, "通知显示下一句", "lyrics_notification_next_line", false)
    SettingsToggleRow(context, "通知显示播放进度", "lyrics_notification_progress", true)
}

@Composable
private fun SkylineLyricsSettings(context: android.content.Context) {
    var enabled by remember { mutableStateOf(MeloXSettingsRuntime.skylineEnabled) }
    SettingsExternalToggleRow(
        title = "横屏自动启用天际歌词",
        value = enabled,
        note = "播放器进入横屏时使用封面、主歌词和环境歌词的宽屏布局。",
    ) {
        enabled = it
        MeloXSettingsPreferences.setBoolean(context, "lyrics_skyline_enabled", it)
    }
    SettingsToggleRow(context, "屏幕常亮", "lyrics_skyline_keep_awake", true, "仅在全屏天际歌词可见时阻止自动锁屏。")
    SettingsToggleRow(context, "显示封面与歌曲信息", "lyrics_skyline_song_info", true)
    LyricsChoiceSetting(
        context,
        "环境歌词数量",
        "lyrics_skyline_ambient_lines",
        2,
        listOf(0, 1, 2, 3, 4),
    ) { if (it == 0) "关闭" else "$it 行" }
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
    LyricsChoiceSetting(context, "单组最大字数", "lyrics_skyline_ambient_max_characters", 4, listOf(1, 2, 3, 4)) { "$it 个字" }
    LyricsChoiceSetting(context, "同屏文字上限", "lyrics_skyline_ambient_max_visible", 16, listOf(4, 8, 12, 16, 20, 24)) { "$it 组" }
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
    SettingsInfoCard("显示条件", "仅在播放器歌词页横屏时切换；竖屏继续使用歌词设置中选择的渲染器。")
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

    SettingsExternalToggleRow(
        title = "显示悬浮歌词",
        value = enabled,
        note = "在其他应用上方显示当前歌词和翻译/罗马音，可直接拖动位置。",
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
    SettingsInfoCard(
        "悬浮窗权限",
        if (permissionGranted) "已允许；关闭开关或通知中的“停止”即可结束服务。" else "未允许；开启时会跳转到系统授权页。",
    )
    LyricsStringChoiceSetting(
        context,
        "副歌词内容",
        "floating_lyrics_secondary_mode",
        MeloXSecondaryLyricMode.Auto.name,
        MeloXSecondaryLyricMode.entries.map { it.name },
    ) {
        when (MeloXSecondaryLyricMode.valueOf(it)) {
            MeloXSecondaryLyricMode.Auto -> "自动（翻译/罗马音/下一句）"
            MeloXSecondaryLyricMode.Translation -> "翻译"
            MeloXSecondaryLyricMode.Romanization -> "罗马音"
            MeloXSecondaryLyricMode.NextLine -> "下一句"
            MeloXSecondaryLyricMode.Hidden -> "不显示"
        }
    }
    LyricsChoiceSetting(
        context,
        "主歌词字号",
        "floating_lyrics_font_size",
        18,
        listOf(14, 16, 18, 20, 24, 28),
    ) { "$it sp" }
    SettingsToggleRow(context, "高对比背景", "floating_lyrics_high_contrast", true, "重新开启悬浮歌词后生效。")
}

@Composable
private fun PlaybackSettings(context: android.content.Context) {
    Text("播放音质", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    var quality by remember { mutableStateOf(MusicQualityPreferences.read(context)) }
    SettingsGlassGroup {
        MusicQuality.entries.forEach { item ->
            SettingsChoiceRow(item.title, item == quality) {
                quality = item
                PlaybackCommands.changeQuality(context, item)
            }
        }
    }
    Spacer(Modifier.height(22.dp))
    SettingsToggleRow(context, "记住播放器上次页面", "playback_remember_page", true)
    SettingsToggleRow(context, "播放超过 5 秒时上一首先回到开头", "playback_previous_restarts", true)
    SettingsToggleRow(context, "登录后以心动模式开始播放", "playback_heart_mode_on_launch", false, "仅在启动时没有现有播放队列时执行。")
    SettingsInfoCard("耳机断开时暂停", "已由 Media3 播放服务启用")
    Spacer(Modifier.height(10.dp))
    var volumeMode by remember { mutableStateOf(MeloXSettingsRuntime.volumeControlMode) }
    Text("音量滑杆控制", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        listOf(MeloXVolumeControlMode.System to "系统媒体音量", MeloXVolumeControlMode.Player to "播放器独立音量").forEach { (mode, label) ->
            SettingsChoiceRow(label, volumeMode == mode) {
                volumeMode = mode
                MeloXSettingsPreferences.setString(context, "playback_volume_mode", mode.name)
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    EqualizerSettings(context)
    Spacer(Modifier.height(10.dp))
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
    SettingsExternalToggleRow(
        title = "自动混音",
        value = autoMixEnabled,
        note = "双播放器预载，20ms 包络更新；分析不可用时按下方策略平滑降级。",
    ) {
        autoMixEnabled = it
        MeloXPlaybackModePreferences.setAutoMix(context, it)
    }
    if (autoMixEnabled) AutoMixSettings(context)
}

@Composable
private fun EqualizerSettings(context: android.content.Context) {
    var enabled by remember { mutableStateOf(MeloXSettingsPreferences.boolean(context, "equalizer_enabled", false)) }
    var preset by remember { mutableStateOf(MeloXSettingsPreferences.string(context, "equalizer_preset", "Flat")) }
    var preamp by remember { mutableStateOf(MeloXSettingsPreferences.int(context, "equalizer_preamp_db", 0)) }
    SettingsExternalToggleRow("均衡器", enabled, "使用 Android 原生多频段 DSP，直接作用于当前播放器音频会话。") {
        enabled = it
        MeloXSettingsPreferences.setBoolean(context, "equalizer_enabled", it)
    }
    if (!enabled) return
    Text("预设", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        MeloXEqualizerController.PRESETS.keys.forEach { value ->
            val label = mapOf("Flat" to "平直", "Bass" to "低频增强", "Vocal" to "人声", "Treble" to "高频增强", "Electronic" to "电子", "Custom" to "自定义").getValue(value)
            SettingsChoiceRow(label, preset == value) {
                preset = value
                MeloXSettingsPreferences.setString(context, "equalizer_preset", value)
            }
        }
    }
    if (preset == "Custom") {
        Spacer(Modifier.height(12.dp))
        listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz").forEachIndexed { index, label ->
            LyricsChoiceSetting(
                context,
                label,
                "equalizer_custom_band_$index",
                0,
                listOf(-6, -3, 0, 3, 6),
            ) { if (it > 0) "+$it dB" else "$it dB" }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("前级", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        listOf(-6, -3, 0, 3, 6).forEach { value ->
            SettingsChoiceRow(if (value > 0) "+$value dB" else "$value dB", preamp == value) {
                preamp = value
                MeloXSettingsPreferences.setInt(context, "equalizer_preamp_db", value)
            }
        }
    }
}

@Composable
private fun AutoMixSettings(context: android.content.Context) {
    var settings by remember { mutableStateOf(MeloXAutoMixSettings.read(context)) }
    fun refresh() { settings = MeloXAutoMixSettings.read(context) }

    Text("混音模式", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        listOf(MeloXAutoMixMode.Smart to "智能", MeloXAutoMixMode.Fixed to "固定时长").forEach { (mode, title) ->
            SettingsChoiceRow(title, settings.mode == mode) {
                MeloXPlaybackModePreferences.setAutoMixString(context, "automix_mode", mode.name)
                refresh()
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    if (settings.mode == MeloXAutoMixMode.Smart) {
        Text("智能过渡长度", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        Spacer(Modifier.height(8.dp))
        SettingsGlassGroup {
            listOf(4, 8, 16).forEach { bars ->
                SettingsChoiceRow("$bars 小节", settings.transitionBars == bars) {
                    MeloXPlaybackModePreferences.setAutoMixInt(context, "automix_transition_bars", bars)
                    refresh()
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("上一首结束位置", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        Spacer(Modifier.height(8.dp))
        SettingsGlassGroup {
            listOf(0 to "保留至结尾", 2 to "提前 2 小节", 4 to "提前 4 小节", 8 to "提前 8 小节").forEach { (bars, title) ->
                SettingsChoiceRow(title, settings.tailCutBars == bars) {
                    MeloXPlaybackModePreferences.setAutoMixInt(context, "automix_tail_cut_bars", bars)
                    refresh()
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsExternalToggleRow("跳过安静开头", settings.skipQuietOpening, "从下一首的首个可听乐句开始交接。") {
            MeloXPlaybackModePreferences.setAutoMixBoolean(context, "automix_skip_quiet_opening", it)
            refresh()
        }
        SettingsExternalToggleRow("分析网络歌曲", settings.analyzeStreaming, "提前解码网络音频以生成节拍和频谱时间轴。") {
            MeloXPlaybackModePreferences.setAutoMixBoolean(context, "automix_analyze_streaming", it)
            refresh()
        }
        Text("最低分析置信度", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        Spacer(Modifier.height(8.dp))
        SettingsGlassGroup {
            listOf(.30f, .42f, .55f, .70f).forEach { confidence ->
                SettingsChoiceRow("${(confidence * 100).toInt()}%", kotlin.math.abs(settings.minimumConfidence - confidence) < .001) {
                    MeloXPlaybackModePreferences.setAutoMixFloat(context, "automix_minimum_confidence", confidence)
                    refresh()
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
    Text("交叉淡化时长", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        listOf(3_000L, 6_000L, 8_000L, 12_000L, 16_000L, 20_000L).forEach { duration ->
            SettingsChoiceRow("${duration / 1_000} 秒", settings.fixedDurationMs == duration) {
                MeloXPlaybackModePreferences.setAutoMixLong(context, "automix_fixed_duration_ms", duration)
                refresh()
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text("预加载提前量", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        listOf(30_000L, 60_000L, 90_000L, 120_000L, 180_000L).forEach { lead ->
            SettingsChoiceRow("${lead / 1_000} 秒", settings.preloadLeadMs == lead) {
                MeloXPlaybackModePreferences.setAutoMixLong(context, "automix_preload_lead_ms", lead)
                refresh()
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text("淡化曲线", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        listOf(
            MeloXAutoMixFadeCurve.EqualPower to "等功率",
            MeloXAutoMixFadeCurve.Smooth to "平滑",
            MeloXAutoMixFadeCurve.Linear to "线性",
        ).forEach { (curve, title) ->
            SettingsChoiceRow(title, settings.fadeCurve == curve) {
                MeloXPlaybackModePreferences.setAutoMixString(context, "automix_fade_curve", curve.name)
                refresh()
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text("分析失败时", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        listOf(
            MeloXAutoMixFallback.Crossfade to "使用所选时长",
            MeloXAutoMixFallback.ShortCrossfade to "短淡化（3 秒）",
            MeloXAutoMixFallback.Normal to "正常切歌",
        ).forEach { (fallback, title) ->
            SettingsChoiceRow(title, settings.fallback == fallback) {
                MeloXPlaybackModePreferences.setAutoMixString(context, "automix_fallback", fallback.name)
                refresh()
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    SettingsExternalToggleRow("速度匹配", settings.tempoMatching, "有可靠 BPM 分析时平滑调整两台播放器速度。") {
        MeloXPlaybackModePreferences.setAutoMixBoolean(context, "automix_tempo_matching", it)
        refresh()
    }
    if (settings.tempoMatching) {
        Text("最大速度调整", modifier = Modifier.padding(top = 10.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        Spacer(Modifier.height(8.dp))
        SettingsGlassGroup {
            listOf(.02f, .05f, .08f).forEach { adjustment ->
                SettingsChoiceRow("${(adjustment * 100).toInt()}%", kotlin.math.abs(settings.maxTempoAdjustment - adjustment) < .001) {
                    MeloXPlaybackModePreferences.setAutoMixFloat(context, "automix_max_tempo_adjustment", adjustment)
                    refresh()
                }
            }
        }
    }
}

@Composable
private fun PlayerAppearanceSettings(context: android.content.Context) {
    SettingsToggleRow(context, "流动光影背景", "player_flowing_backdrop", true, "关闭后使用模糊封面背景。")
    SettingsToggleRow(context, "封面播放动效", "player_artwork_motion", true)
    LyricsStringChoiceSetting(
        context,
        "屏幕常亮范围",
        "player_screen_awake_mode",
        MeloXScreenAwakeMode.Disabled.name,
        MeloXScreenAwakeMode.entries.map { it.name },
    ) {
        when (MeloXScreenAwakeMode.valueOf(it)) {
            MeloXScreenAwakeMode.Disabled -> "关闭"
            MeloXScreenAwakeMode.Player -> "播放器常亮"
            MeloXScreenAwakeMode.Lyrics -> "歌词页常亮"
            MeloXScreenAwakeMode.HiddenLyricsInterface -> "歌词页隐藏 UI 后常亮"
        }
    }
}

@Composable
private fun LyricsSettings(context: android.content.Context) {
    var lyricsStyle by remember { mutableStateOf(MeloXSettingsRuntime.lyricsStyle) }
    Text("歌词样式", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        listOf(
            MeloXLyricsStyle.AppleMusic to "Apple Music",
            MeloXLyricsStyle.Eva to "EVA 动态排版",
            MeloXLyricsStyle.TextPV to "文字 PV",
        ).forEach { (style, title) ->
            SettingsChoiceRow(title, lyricsStyle == style) {
                MeloXSettingsPreferences.setString(context, "lyrics_style", style.name)
                lyricsStyle = style
            }
        }
    }
    if (lyricsStyle == MeloXLyricsStyle.TextPV) {
        var pvStyle by remember { mutableStateOf(MeloXSettingsRuntime.textPVStyle) }
        var pvMotionIntensity by remember { mutableStateOf(MeloXSettingsRuntime.textPVMotionIntensity) }
        var pvAnimationSpeed by remember { mutableStateOf(MeloXSettingsRuntime.textPVAnimationSpeed) }
        Text("文字 PV 风格", modifier = Modifier.padding(top = 14.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        Spacer(Modifier.height(8.dp))
        SettingsGlassGroup {
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
        SettingsFloatSlider(
            "动效强度",
            pvMotionIntensity,
            0f..2f,
            19,
        ) {
            pvMotionIntensity = it
            MeloXSettingsPreferences.setFloat(context, "lyrics_text_pv_motion_intensity", it)
        }
        SettingsFloatSlider(
            "动画速度",
            pvAnimationSpeed,
            0f..4f,
            39,
        ) {
            pvAnimationSpeed = it
            MeloXSettingsPreferences.setFloat(context, "lyrics_text_pv_animation_speed", it)
        }
        SettingsActionButton("恢复文字 PV 默认设置") {
            MeloXSettingsPreferences.setString(context, "lyrics_text_pv_style", MeloXTextPVStyle.BlueBold.name)
            MeloXSettingsPreferences.setFloat(context, "lyrics_text_pv_motion_intensity", 1f)
            MeloXSettingsPreferences.setFloat(context, "lyrics_text_pv_animation_speed", 2f)
            pvStyle = MeloXTextPVStyle.BlueBold
            pvMotionIntensity = 1f
            pvAnimationSpeed = 2f
        }
    }
    Spacer(Modifier.height(16.dp))
    SettingsToggleRow(context, "显示翻译", "lyrics_translation", true)
    SettingsToggleRow(context, "显示罗马音", "lyrics_romanization", true)
    LyricsStringChoiceSetting(
        context,
        "罗马音显示范围",
        "lyrics_romanization_display_mode",
        MeloXLyricAnnotationDisplayMode.FocusedLine.name,
        MeloXLyricAnnotationDisplayMode.entries.map { it.name },
    ) { if (it == MeloXLyricAnnotationDisplayMode.FocusedLine.name) "仅当前播放行" else "全部歌词行" }
    PreferenceFloatSlider(context, "罗马音大小", "lyrics_romanization_font_scale", .65f, .5f..8f / 10f, 5) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, "罗马音亮度", "lyrics_romanization_opacity", .9f, .4f..9f / 10f, 9) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, "翻译歌词大小", "lyrics_translation_font_scale", .65f, .5f..8f / 10f, 5) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, "翻译歌词亮度", "lyrics_translation_opacity", .9f, .4f..9f / 10f, 9) { "${(it * 100).toInt()}%" }
    LyricsStringChoiceSetting(
        context,
        "翻译显示范围",
        "lyrics_translation_display_mode",
        MeloXLyricAnnotationDisplayMode.FocusedLine.name,
        MeloXLyricAnnotationDisplayMode.entries.map { it.name },
    ) { if (it == MeloXLyricAnnotationDisplayMode.FocusedLine.name) "仅当前播放行" else "全部歌词行" }
    SettingsToggleRow(context, "逐字歌词（YRC）", "lyrics_word_by_word", true)
    SettingsToggleRow(context, "普通 LRC 生成逐字时间", "lyrics_pseudo_timing", true, "按 Unicode 字素分配行时长，不覆盖真实 YRC。")
    SettingsToggleRow(context, "点击歌词跳转进度", "lyrics_tap_seek", true)
    SettingsToggleRow(context, "长按歌词分享", "lyrics_long_press_share", true)
    SettingsToggleRow(context, "间奏倒计时", "lyrics_interlude_countdown", true, "歌词间隔至少 4 秒时显示三点倒计时。")
    SettingsToggleRow(context, "自动跟随当前歌词", "lyrics_auto_follow", true)
    SettingsToggleRow(context, "减弱歌词动画", "lyrics_reduce_motion", false, "保留逐字高亮，关闭弹性、抬升与光晕。")

    LyricsChoiceSetting(context, "歌词提前量", "lyrics_advance_ms", 0, listOf(-1_000, -500, -200, 0, 200, 500, 1_000, 2_000, 5_000)) { value ->
        if (value == 0) "同步" else if (value > 0) "提前 ${value}ms" else "延后 ${-value}ms"
    }
    SettingsToggleRow(context, "提前量同时应用于逐字高亮", "lyrics_advance_word_by_word", false)
    LyricsChoiceSetting(context, "歌词刷新率", "lyrics_refresh_rate", 60, listOf(30, 60, 90, 120)) { "$it FPS" }
    LyricsChoiceSetting(context, "手动滚动后恢复跟随", "lyrics_follow_delay_ms", 3_000, listOf(1_500, 3_000, 5_000, 8_000)) { "${it / 1_000f} 秒" }
    LyricsFloatChoiceSetting(context, "歌词字号", "lyrics_font_scale", 1f, listOf(.85f, 1f, 1.12f, 1.25f)) { "${(it * 100).toInt()}%" }
    LyricsStringChoiceSetting(
        context, "歌词字重", "lyrics_font_weight", MeloXLyricsFontWeight.Heavy.name,
        MeloXLyricsFontWeight.entries.map { it.name },
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
        MeloXLyricsGroupingMode.entries.map { it.name },
    ) { if (it == MeloXLyricsGroupingMode.Word.name) "按词抬升" else "按字抬升" }
    PreferenceFloatSlider(context, "高光渐变宽度", "lyrics_highlight_gradient_width", .7f, .4f..3f, 25) { "%.1f 字宽".format(it) }
    PreferenceFloatSlider(context, "渐变削减程度", "lyrics_highlight_gradient_reduction", .65f, 0f..1f, 19) { "${(it * 100).toInt()}%" }
    LyricsStringChoiceSetting(
        context, "长音识别方式", "lyrics_long_tone_detection", MeloXLyricsGroupingMode.Character.name,
        MeloXLyricsGroupingMode.entries.map { it.name },
    ) { if (it == MeloXLyricsGroupingMode.Word.name) "按词识别" else "按字识别" }
    SettingsToggleRow(context, "逐字歌词光效", "lyrics_glow_enabled", true)
    SettingsToggleRow(context, "仅长音显示光晕", "lyrics_glow_long_tones_only", true)
    LyricsChoiceSetting(context, "长音判定时长", "lyrics_long_tone_threshold_ms", 950, listOf(300, 500, 700, 950, 1_200, 1_500)) { "${it / 1000f} 秒" }
    LyricsFloatChoiceSetting(context, "行间距", "lyrics_spacing_scale", 1f, listOf(.8f, 1f, 1.2f, 1.4f)) { "${(it * 100).toInt()}%" }
    LyricsFloatChoiceSetting(context, "远近模糊", "lyrics_blur_strength", 1f, listOf(0f, .6f, 1f, 1.4f)) { if (it == 0f) "关闭" else "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, "焦点垂直位置", "lyrics_focus_position", .25f, .05f..8f / 10f, 74) { "距顶部 ${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, "默认逐句模糊加强", "lyrics_distance_blur_scale", 1.05f, 0f..1.5f, 29) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, "隐藏 UI 逐句模糊加强", "lyrics_hidden_blur_scale", .85f, 0f..1.5f, 29) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, "非焦点歌词变暗", "lyrics_dim_amount", 1f, 0f..1f, 9) { "${(it * 100).toInt()}%" }
    LyricsFloatChoiceSetting(context, "当前行放大", "lyrics_focus_scale", 1.02f, listOf(1f, 1.02f, 1.04f, 1.08f)) { "${(it * 100).toInt()}%" }
    LyricsFloatChoiceSetting(context, "未播放文字亮度", "lyrics_inactive_opacity", .3f, listOf(.2f, .3f, .45f, .6f)) { "${(it * 100).toInt()}%" }
    LyricsFloatChoiceSetting(context, "逐字光晕", "lyrics_glow_strength", 1f, listOf(0f, .6f, 1f, 1.4f)) { if (it == 0f) "关闭" else "${(it * 100).toInt()}%" }
    LyricsFloatChoiceSetting(context, "长音延展", "lyrics_long_tone_strength", 1f, listOf(0f, .6f, 1f, 1.4f)) { if (it == 0f) "关闭" else "${(it * 100).toInt()}%" }
    LyricsChoiceSetting(context, "控制栏自动隐藏", "lyrics_interface_auto_hide_ms", 5_000, (3..15).map { it * 1_000 }) { "${it / 1_000} 秒" }
    LyricsChoiceSetting(context, "滚动隐藏 UI 阈值", "lyrics_scroll_hide_threshold_dp", 200, listOf(40, 80, 120, 160, 200, 240)) { "$it dp" }

    Text("动画与性能", modifier = Modifier.padding(top = 16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    PreferenceFloatSlider(context, "基础拖尾延迟", "lyrics_cascade_delay_ms", 21f, 0f..100f, 99) { "${it.toInt()} ms" }
    PreferenceFloatSlider(context, "逐句拖尾增量", "lyrics_cascade_delay_increase_ms", 5f, 0f..100f, 99) { "${it.toInt()} ms/句" }
    PreferenceFloatSlider(context, "后续歌词启动延迟", "lyrics_cascade_following_delay_ms", 30f, 0f..200f, 199) { "${it.toInt()} ms" }
    PreferenceFloatSlider(context, "拖尾追赶节奏", "lyrics_cascade_catch_up_ratio", .97f, .5f..1f, 49) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, "追赶速度梯度", "lyrics_cascade_chase_gradient", .70f, 0f..1f, 99) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, "位移收束时长", "lyrics_cascade_duration_ms", 740f, 200f..1_200f, 99) { "%.2f 秒".format(it / 1_000f) }
    PreferenceFloatSlider(context, "瞬移阈值", "lyrics_snap_threshold_ms", 260f, 50f..500f, 89) { "${it.toInt()} ms" }
    SettingsToggleRow(context, "启用位移回弹", "lyrics_cascade_bounce_enabled", true)
    PreferenceFloatSlider(context, "最大回弹弹性", "lyrics_cascade_bounce", .26f, 0f..8f / 10f, 79) { "${(it * 100).toInt()}%" }
    PreferenceFloatSlider(context, "回弹强度梯度", "lyrics_cascade_bounce_gradient", .85f, 0f..1f, 99) { "${(it * 100).toInt()}%" }
    SettingsToggleRow(context, "启用升格回弹", "lyrics_scale_bounce_enabled", true)
    PreferenceFloatSlider(context, "升格回弹弹性", "lyrics_scale_bounce", .32f, 0f..5f / 10f, 49) { "${(it * 100).toInt()}%" }
    LyricsChoiceSetting(context, "升格回弹时长", "lyrics_scale_bounce_duration_ms", 580, listOf(150, 250, 350, 450, 580, 700, 800)) { "${it}ms" }
    LyricsChoiceSetting(context, "焦点颜色提前", "lyrics_focus_color_lead_ms", 0, listOf(-300, -200, -100, -50, 0, 50, 100, 200, 300)) { if (it == 0) "同步" else "${it}ms" }
}

@Composable
private fun LyricsStringChoiceSetting(
    context: android.content.Context,
    title: String,
    key: String,
    default: String,
    values: List<String>,
    label: (String) -> String,
) {
    var selected by remember(key) { mutableStateOf(MeloXSettingsPreferences.string(context, key, default)) }
    Text(title, modifier = Modifier.padding(top = 8.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        values.forEach { value ->
            SettingsChoiceRow(label(value), selected == value) {
                selected = value
                MeloXSettingsPreferences.setString(context, key, value)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun LyricsChoiceSetting(
    context: android.content.Context,
    title: String,
    key: String,
    default: Int,
    values: List<Int>,
    label: (Int) -> String,
) {
    var selected by remember(key) { mutableStateOf(MeloXSettingsPreferences.int(context, key, default)) }
    Text(title, modifier = Modifier.padding(top = 8.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        values.forEach { value ->
            SettingsChoiceRow(label(value), selected == value) {
                selected = value
                MeloXSettingsPreferences.setInt(context, key, value)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun LyricsFloatChoiceSetting(
    context: android.content.Context,
    title: String,
    key: String,
    default: Float,
    values: List<Float>,
    label: (Float) -> String,
) {
    var selected by remember(key) { mutableStateOf(MeloXSettingsPreferences.float(context, key, default)) }
    Text(title, modifier = Modifier.padding(top = 8.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        values.forEach { value ->
            SettingsChoiceRow(label(value), kotlin.math.abs(selected - value) < .001f) {
                selected = value
                MeloXSettingsPreferences.setFloat(context, key, value)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
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
    SettingsToggleRow(context, "播客", "feature_podcasts", true)
    SettingsToggleRow(context, "最近播放", "feature_history", true)
    SettingsToggleRow(context, "下载", "feature_downloads", true, "控制音乐库下载入口；已下载文件不会被删除。")
    SettingsToggleRow(context, "音乐云盘", "feature_cloud_music", true, "读取、搜索、上传、播放和删除网易云云盘歌曲。")
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
                val recent = ops.privateMessageConversations()
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
        SettingsGlassGroup {
            contacts.take(100).forEach { contact ->
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
                    Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .3f))
                }
            }
        }
        if (!busy && contacts.isEmpty() && error == null) SettingsInfoCard("暂无联系人", "关注网易云用户后，可在这里发起站内私信。")
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
    messages.takeLast(60).forEach { message ->
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
    Text("新碟与发现地区", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        listOf("全部", "华语", "欧美", "日本", "韩国").forEach { value ->
            SettingsChoiceRow(value, area == value) {
                area = value
                MeloXSettingsPreferences.setString(context, "music_area", value)
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    SettingsToggleRow(context, "显示歌单播放量", "content_playlist_play_count", true)
    SettingsToggleRow(context, "发现页显示精品歌单", "content_high_quality_playlist", true)
}

@Composable
private fun StorageSettings(context: android.content.Context) {
    var cacheSize by remember { mutableStateOf("计算中…") }
    val scope = rememberCoroutineScope()
    val downloads = remember(context) { MeloXDownloadStore.get(context) }
    suspend fun refresh() {
        val bytes = withContext(Dispatchers.IO) { context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
        cacheSize = formatBytes(bytes)
    }
    LaunchedEffect(Unit) { refresh() }

    SettingsInfoCard("临时缓存", cacheSize)
    Spacer(Modifier.height(10.dp))
    SettingsInfoCard("已下载歌曲", "${downloads.downloads.size} 首 · ${formatBytes(downloads.totalByteCount)}")
    Spacer(Modifier.height(14.dp))

    var autoCache by remember { mutableStateOf(MeloXSettingsPreferences.boolean(context, "downloads_auto_cache", false)) }
    SettingsExternalToggleRow("按播放次数自动缓存", autoCache, "歌曲实际开始播放达到阈值后自动下载；不会重复下载。") {
        autoCache = it
        MeloXSettingsPreferences.setBoolean(context, "downloads_auto_cache", it)
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
    SettingsToggleRow(context, "下载歌词", "download_lyrics", true, "下载歌曲时同时保存歌词；默认开启，可在此关闭。封面始终随歌曲保存。")

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
        Text("已下载", modifier = Modifier.padding(top=18.dp,bottom=8.dp), fontWeight=FontWeight.SemiBold)
        SettingsGlassGroup {
            downloads.downloads.forEach { item ->
                Row(Modifier.fillMaxWidth().clickable { PlaybackCommands.playQueue(context, downloads.downloadedSongs, item.song.id) }.padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.song.name, maxLines=1, overflow=TextOverflow.Ellipsis)
                        Text("${item.quality.title} · ${formatBytes(item.byteCount)}", color=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f), fontSize=11.sp)
                    }
                    Text("删除", color=MaterialTheme.colorScheme.error, modifier=Modifier.clickable { downloads.remove(item.song.id) }.padding(8.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SettingsDangerButton("删除全部已下载歌曲") { downloads.removeAll() }
    } else if (downloads.activeDownloads.isEmpty()) {
        SettingsInfoCard("下载", "还没有下载歌曲；可在歌曲的“更多”菜单中选择“下载歌曲”。")
    }

    downloads.errorMessage?.let { Text(it, color=MaterialTheme.colorScheme.error, fontSize=12.sp, modifier=Modifier.padding(top=10.dp)) }
    Spacer(Modifier.height(14.dp))
    SettingsActionButton("清理临时缓存") {
        scope.launch {
            withContext(Dispatchers.IO) { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
            refresh()
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun TabLayoutSettings(context: android.content.Context) {
    SettingsToggleRow(context, "首页", "tab_home", true)
    SettingsToggleRow(context, "发现", "tab_explore", true)
    SettingsToggleRow(context, "音乐库", "tab_library", true)
    Spacer(Modifier.height(14.dp))
    var order by remember { mutableStateOf(MeloXSettingsRuntime.tabOrder) }
    Text("标签栏顺序", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        order.forEachIndexed { index, page ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(when (page) { "Home" -> "首页"; "Explore" -> "发现"; "Library" -> "音乐库"; else -> "设置" }, Modifier.weight(1f))
                Text("↑", modifier = Modifier.clickable(enabled = index > 0) {
                    order = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                    MeloXSettingsPreferences.setString(context, "tab_order", order.joinToString(","))
                }.padding(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = if (index > 0) 1f else .25f))
                Text("↓", modifier = Modifier.clickable(enabled = index < order.lastIndex) {
                    order = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                    MeloXSettingsPreferences.setString(context, "tab_order", order.joinToString(","))
                }.padding(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = if (index < order.lastIndex) 1f else .25f))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    SettingsInfoCard("标签栏", "页面开关和排序立即生效；设置与搜索始终保留")
    Spacer(Modifier.height(18.dp))
    Text("首页区块", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsToggleRow(context, "快捷入口", "home_quick_actions", true)
    SettingsToggleRow(context, "推荐歌单", "home_playlists", true)
    SettingsToggleRow(context, "推荐新歌", "home_new_songs", true)
    var homeOrder by remember { mutableStateOf(MeloXSettingsRuntime.homeSectionOrder) }
    SettingsGlassGroup {
        homeOrder.forEachIndexed { index, section ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(when (section) { "QuickActions" -> "快捷入口"; "Playlists" -> "推荐歌单"; else -> "推荐新歌" }, Modifier.weight(1f))
                Text("↑", modifier = Modifier.clickable(enabled = index > 0) {
                    homeOrder = homeOrder.toMutableList().apply { add(index - 1, removeAt(index)) }
                    MeloXSettingsPreferences.setString(context, "home_section_order", homeOrder.joinToString(","))
                }.padding(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = if (index > 0) 1f else .25f))
                Text("↓", modifier = Modifier.clickable(enabled = index < homeOrder.lastIndex) {
                    homeOrder = homeOrder.toMutableList().apply { add(index + 1, removeAt(index)) }
                    MeloXSettingsPreferences.setString(context, "home_section_order", homeOrder.joinToString(","))
                }.padding(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = if (index < homeOrder.lastIndex) 1f else .25f))
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    SettingsToggleRow(context, "记住音乐库子页面", "library_remember_page", true)
    var libraryPage by remember { mutableStateOf(MeloXSettingsRuntime.defaultLibraryPage) }
    Text("音乐库默认页", modifier = Modifier.padding(top = 12.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        listOf("Songs" to "歌曲", "Playlists" to "歌单", "Podcasts" to "播客", "Cloud" to "云盘", "History" to "最近播放", "Downloads" to "下载")
            .filter { (value, _) ->
                (value != "Podcasts" || MeloXSettingsRuntime.podcastsEnabled) &&
                    (value != "Cloud" || MeloXSettingsRuntime.cloudMusicEnabled) &&
                    (value != "History" || MeloXSettingsRuntime.listeningHistoryEnabled) &&
                    (value != "Downloads" || MeloXSettingsRuntime.downloadsEnabled)
            }.forEach { (value, title) ->
            SettingsChoiceRow(title, libraryPage == value) {
                libraryPage = value
                MeloXSettingsPreferences.setString(context, "library_default_page", value)
            }
        }
    }
    Text("搜索保持为独立的右侧 Liquid Glass 按钮。", modifier = Modifier.padding(top = 12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f))
}

@Composable
private fun GeneralSettings(context: android.content.Context) {
    var theme by remember { mutableStateOf(MeloXSettingsRuntime.themeMode) }
    Text("主题", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        listOf(
            MeloXThemeMode.System to "跟随系统",
            MeloXThemeMode.Light to "浅色",
            MeloXThemeMode.Dark to "深色",
        ).forEach { (mode, title) ->
            SettingsChoiceRow(title, theme == mode) {
                theme = mode
                MeloXSettingsPreferences.setString(context, "theme_mode", mode.name)
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    SettingsToggleRow(context, "记住上次标签页", "general_remember_tab", true)
    var defaultTab by remember { mutableStateOf(MeloXSettingsRuntime.defaultTab) }
    Text("默认启动页", modifier = Modifier.padding(top = 14.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        listOf("Home" to "首页", "Explore" to "发现", "Library" to "音乐库", "Settings" to "设置").forEach { (value, title) ->
            SettingsChoiceRow(title, defaultTab == value) {
                defaultTab = value
                MeloXSettingsPreferences.setString(context, "general_default_tab", value)
            }
        }
    }
    SettingsToggleRow(context, "识别剪贴板中的网易云链接", "general_clipboard_links", true, "每次回到前台只读取一次；识别歌曲或歌单后会先询问是否打开。")
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

    SettingsInfoCard("网易云音频指纹", "音频只在设备上转为指纹；匹配请求发送指纹，不上传原始录音。持续识别会每 9 秒追加一次结果。")
    Spacer(Modifier.height(14.dp))
    Text("识别时长", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        listOf(3 to "3 秒 · 更快", 6 to "6 秒 · 推荐", 9 to "9 秒 · 嘈杂环境", 0 to "持续识别").forEach { (value, title) ->
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
        SettingsInfoCard("识别失败", message)
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
private fun AboutSettings(context: android.content.Context) {
    val updateClient = remember { MeloXUpdateClient() }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<MeloXRelease?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    SettingsGlassGroup {
        Column(Modifier.padding(18.dp)) {
            Text("MeloX Android", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("版本 ${BuildConfig.VERSION_NAME} · MeloX 的 Android 原生迁移版。", modifier = Modifier.padding(top=7.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha=.62f))
            Text("Android 原生迁移与维护：lladlam", modifier = Modifier.padding(top=14.dp), fontWeight=FontWeight.SemiBold)
            Text("上游 iOS 原生项目：youshen2/MeloX（SwiftUI）", modifier = Modifier.padding(top=5.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha=.58f))
        }
    }
    Spacer(Modifier.height(14.dp))
    SettingsToggleRow(context, "自动检查更新", "update_auto_check", true, "应用启动后检查 GitHub 正式版本；不会自动下载安装。")
    SettingsActionButton(if (checking) "正在检查…" else "检查更新") {
        if (!checking) scope.launch {
            checking = true
            runCatching { updateClient.latestStableRelease() }
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
        SettingsInfoCard("更新状态", message)
    }
    release?.takeIf { updateClient.isNewer(it.version, BuildConfig.VERSION_NAME) }?.let { available ->
        Spacer(Modifier.height(10.dp))
        SettingsActionButton(if (available.apkUrl != null) "下载 ${available.version} APK" else "打开 ${available.version} 发布页") {
            val target = available.apkUrl ?: available.pageUrl
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
        }
        if (available.notes.isNotBlank()) {
            Text(available.notes.take(700), modifier = Modifier.padding(top = 10.dp), fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
        }
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
}

@Composable
private fun DeveloperSettings() {
    SettingsInfoCard("AutoMix 分析", "Android 原生 MediaCodec 整曲解码，生成节拍、重拍、乐句、能量和频谱时间轴；分析失败时才使用所选降级策略。")
    Spacer(Modifier.height(10.dp))
    SettingsInfoCard("播放器与网络日志", "使用 Logcat 的 MeloXPlayback / 网络标签；不再保存无效偏好")
}

@Composable
private fun SettingsToggleRow(
    context: android.content.Context,
    title: String,
    key: String,
    default: Boolean,
    note: String? = null,
) {
    var value by remember(key) { mutableStateOf(MeloXSettingsPreferences.boolean(context, key, default)) }
    SettingsGlassGroup {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                note?.let { Text(it, modifier = Modifier.padding(top = 3.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f)) }
            }
            Switch(checked = value, onCheckedChange = {
                value = it
                MeloXSettingsPreferences.setBoolean(context, key, it)
            })
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SettingsExternalToggleRow(
    title: String,
    value: Boolean,
    note: String? = null,
    onValueChange: (Boolean) -> Unit,
) {
    SettingsGlassGroup {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                note?.let { Text(it, modifier = Modifier.padding(top = 3.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f)) }
            }
            Switch(checked = value, onCheckedChange = onValueChange)
        }
    }
    Spacer(Modifier.height(10.dp))
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
private fun SettingsGlassGroup(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().meloXLiquidButton(
            shape = RoundedCornerShape(24.dp),
            surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .045f),
            lensRadius = 9.dp,
            refractionHeight = 15.dp,
        ),
    ) { content() }
}

@Composable
private fun SettingsInfoCard(title: String, value: String) {
    SettingsGlassGroup {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(value, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
        }
    }
}

@Composable
private fun SettingsActionButton(title: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(50.dp).meloXLiquidButton(
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
    ) { Text(text, fontSize = 30.sp) }
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
private fun SettingsDangerButton(title: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(54.dp).meloXLiquidButton(
            shape = RoundedCornerShape(27.dp),
            tint = MaterialTheme.colorScheme.error.copy(alpha = .25f),
            surfaceColor = MaterialTheme.colorScheme.error.copy(alpha = .20f),
        ).clickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) { Text(title, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
}
