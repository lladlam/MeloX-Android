from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path): return (ROOT / path).read_text()
def write(path, text):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text)

def replace_once(path, old, new):
    text = read(path)
    if old not in text:
        raise RuntimeError(f"pattern missing in {path}: {old[:100]!r}")
    write(path, text.replace(old, new, 1))

prefs = r'''package com.lladlam.melox.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class MeloXThemeMode { System, Light, Dark }

/** Process-visible settings used by UI paths that need immediate recomposition. */
object MeloXSettingsRuntime {
    var themeMode by mutableStateOf(MeloXThemeMode.System)
        internal set
    var podcastsEnabled by mutableStateOf(true)
        internal set
    var listeningHistoryEnabled by mutableStateOf(true)
        internal set
    var flowingBackdropEnabled by mutableStateOf(true)
        internal set
    var artworkMotionEnabled by mutableStateOf(true)
        internal set
    var keepScreenOn by mutableStateOf(false)
        internal set
    var showLyricTranslation by mutableStateOf(true)
        internal set
    var showLyricRomanization by mutableStateOf(true)
        internal set
    var musicArea by mutableStateOf("全部")
        internal set
    var beatNetDebugEnabled by mutableStateOf(false)
        internal set

    private var initialized = false

    fun initialize(context: Context, force: Boolean = false) {
        if (initialized && !force) return
        initialized = true
        val app = context.applicationContext
        themeMode = runCatching {
            MeloXThemeMode.valueOf(MeloXSettingsPreferences.string(app, "theme_mode", MeloXThemeMode.System.name))
        }.getOrDefault(MeloXThemeMode.System)
        podcastsEnabled = MeloXSettingsPreferences.boolean(app, "feature_podcasts", true)
        listeningHistoryEnabled = MeloXSettingsPreferences.boolean(app, "feature_history", true)
        flowingBackdropEnabled = MeloXSettingsPreferences.boolean(app, "player_flowing_backdrop", true)
        artworkMotionEnabled = MeloXSettingsPreferences.boolean(app, "player_artwork_motion", true)
        keepScreenOn = MeloXSettingsPreferences.boolean(app, "player_keep_screen_on", false)
        showLyricTranslation = MeloXSettingsPreferences.boolean(app, "lyrics_translation", true)
        showLyricRomanization = MeloXSettingsPreferences.boolean(app, "lyrics_romanization", true)
        musicArea = MeloXSettingsPreferences.string(app, "music_area", "全部")
        beatNetDebugEnabled = MeloXSettingsPreferences.boolean(app, "developer_beatnet", false)
    }
}

object MeloXSettingsPreferences {
    private const val NAME = "melox_app_settings"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun initialize(context: Context) = MeloXSettingsRuntime.initialize(context)

    fun boolean(context: Context, key: String, default: Boolean = false): Boolean =
        prefs(context).getBoolean(key, default)

    fun string(context: Context, key: String, default: String = ""): String =
        prefs(context).getString(key, default) ?: default

    fun setBoolean(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
        when (key) {
            "feature_podcasts" -> MeloXSettingsRuntime.podcastsEnabled = value
            "feature_history" -> MeloXSettingsRuntime.listeningHistoryEnabled = value
            "player_flowing_backdrop" -> MeloXSettingsRuntime.flowingBackdropEnabled = value
            "player_artwork_motion" -> MeloXSettingsRuntime.artworkMotionEnabled = value
            "player_keep_screen_on" -> MeloXSettingsRuntime.keepScreenOn = value
            "lyrics_translation" -> MeloXSettingsRuntime.showLyricTranslation = value
            "lyrics_romanization" -> MeloXSettingsRuntime.showLyricRomanization = value
            "developer_beatnet" -> MeloXSettingsRuntime.beatNetDebugEnabled = value
        }
    }

    fun setString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
        when (key) {
            "theme_mode" -> MeloXSettingsRuntime.themeMode = runCatching {
                MeloXThemeMode.valueOf(value)
            }.getOrDefault(MeloXThemeMode.System)
            "music_area" -> MeloXSettingsRuntime.musicArea = value
        }
    }

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
        MeloXSettingsRuntime.initialize(context, force = true)
    }
}
'''
write("android/app/src/main/kotlin/com/lladlam/melox/ui/settings/MeloXSettingsPreferences.kt", prefs)

settings = r'''package com.lladlam.melox.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import coil3.compose.AsyncImage
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.glass.meloXLiquidButton
import kotlinx.coroutines.Dispatchers
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
        SettingsItem(SettingsRoute.Content, "地区、歌单信息和发现内容", "▦", "华语 欧美 韩国 日本 播放量"),
        SettingsItem(SettingsRoute.Storage, "空间统计与缓存清理", "▰", "缓存 存储 清理 数据库"),
    )),
    SettingsSection("界面与应用", listOf(
        SettingsItem(SettingsRoute.TabLayout, "首页、标签栏与音乐库页面", "▥", "首页 标签栏 排序 推荐 歌单 历史"),
        SettingsItem(SettingsRoute.General, "主题、启动行为与链接处理", "⚙", "主题 浅色 深色 跟随系统 默认页面 剪贴板"),
    )),
    SettingsSection("关于与开发", listOf(
        SettingsItem(SettingsRoute.About, "版本、项目主页与开源信息", "ⓘ", "GitHub 更新 开源 许可"),
        SettingsItem(SettingsRoute.Developer, "BeatNet 与播放器调试工具", "⌘", "BeatNet 节拍 调试"),
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
            .verticalScroll(rememberScrollState())
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
            SettingsRoute.SystemPlayback -> InformationalSettings(
                "Android 系统播放",
                listOf(
                    "Media3 媒体通知、锁屏进度与耳机控制已由播放服务接管。",
                    "iOS 的 Live Activity / 灵动岛属于平台专属能力，Android 不创建无效开关。",
                ),
            )
            SettingsRoute.SkylineLyrics -> InformationalSettings(
                "全屏天际歌词",
                listOf("横屏歌词页面保留为后续 Android 专属显示模式；当前播放器歌词页继续使用逐字 YRC 渲染。"),
            )
            SettingsRoute.FloatingLyrics -> InformationalSettings(
                "悬浮窗歌词",
                listOf("Android 需要系统悬浮窗权限或画中画服务。这里保留上游入口，但不会伪造一个无法生效的开关。"),
            )
            SettingsRoute.ContentFeatures -> ContentFeatureSettings(context)
            SettingsRoute.Content -> ContentSettings(context)
            SettingsRoute.Storage -> StorageSettings(context)
            SettingsRoute.TabLayout -> TabLayoutSettings(context)
            SettingsRoute.General -> GeneralSettings(context)
            SettingsRoute.About -> AboutSettings(context)
            SettingsRoute.Developer -> DeveloperSettings(context)
        }
    }
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
    SettingsToggleRow(context, "耳机断开时暂停", "playback_pause_disconnect", true)
    SettingsToggleRow(context, "自动混音", "playback_auto_mix", false, "当前 Media3 队列暂不执行交叉淡化；偏好会保留。")
}

@Composable
private fun PlayerAppearanceSettings(context: android.content.Context) {
    SettingsToggleRow(context, "流光背景", "player_flowing_backdrop", true)
    SettingsToggleRow(context, "封面播放动效", "player_artwork_motion", true)
    SettingsToggleRow(context, "播放页保持屏幕常亮", "player_keep_screen_on", false)
}

@Composable
private fun LyricsSettings(context: android.content.Context) {
    SettingsToggleRow(context, "显示翻译", "lyrics_translation", true)
    SettingsToggleRow(context, "显示罗马音", "lyrics_romanization", true)
    SettingsToggleRow(context, "逐字歌词（YRC）", "lyrics_word_by_word", true)
    SettingsToggleRow(context, "点击歌词跳转进度", "lyrics_tap_seek", true)
}

@Composable
private fun ContentFeatureSettings(context: android.content.Context) {
    SettingsToggleRow(context, "播客", "feature_podcasts", true)
    SettingsToggleRow(context, "最近播放", "feature_history", true)
    SettingsToggleRow(context, "云盘", "feature_cloud", true)
    SettingsToggleRow(context, "下载", "feature_downloads", false, "Android 下载管理尚未接入，关闭时不会展示伪下载入口。")
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
    suspend fun refresh() {
        val bytes = withContext(Dispatchers.IO) {
            context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
        cacheSize = when {
            bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
            bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
    LaunchedEffect(Unit) { refresh() }
    SettingsInfoCard("缓存空间", cacheSize)
    Spacer(Modifier.height(14.dp))
    SettingsActionButton("清理临时缓存") {
        scope.launch {
            withContext(Dispatchers.IO) { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
            refresh()
        }
    }
    Spacer(Modifier.height(10.dp))
    Text("音乐库元数据缓存保存在应用 files 目录，不会被“清理临时缓存”误删。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f))
}

@Composable
private fun TabLayoutSettings(context: android.content.Context) {
    SettingsToggleRow(context, "首页", "tab_home", true)
    SettingsToggleRow(context, "发现", "tab_explore", true)
    SettingsToggleRow(context, "音乐库", "tab_library", true)
    SettingsToggleRow(context, "记住音乐库页面", "library_remember_page", true)
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
    SettingsToggleRow(context, "识别剪贴板中的网易云链接", "general_clipboard_links", true)
    SettingsToggleRow(context, "记住上次标签页", "general_remember_tab", true)
}

@Composable
private fun AboutSettings(context: android.content.Context) {
    SettingsInfoCard("MeloX Android", "iOS MeloX 的 Android 原生迁移版")
    Spacer(Modifier.height(14.dp))
    SettingsActionButton("打开 GitHub 项目") {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lladlam/MeloX-Android")))
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton("查看上游 MeloX") {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/youshen2/MeloX")))
        }
    }
}

@Composable
private fun DeveloperSettings(context: android.content.Context) {
    SettingsToggleRow(context, "BeatNet 调试入口", "developer_beatnet", false)
    SettingsToggleRow(context, "显示播放器调试信息", "developer_player_debug", false)
    SettingsToggleRow(context, "记录网络请求错误", "developer_network_log", true)
}

@Composable
private fun InformationalSettings(title: String, lines: List<String>) {
    SettingsGlassGroup {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            lines.forEach { line ->
                Text(line, modifier = Modifier.padding(top = 9.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), fontSize = 14.sp)
            }
        }
    }
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
'''
write("android/app/src/main/kotlin/com/lladlam/melox/ui/settings/SettingsScreen.kt", settings)

universal = r'''package com.lladlam.melox.core.network

import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.model.SearchSong
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

enum class MeloXSearchKind(val apiType: Int, val title: String) {
    Songs(1, "歌曲"), Albums(10, "专辑"), Artists(100, "歌手"), Playlists(1000, "歌单"), Podcasts(1009, "播客")
}

data class MeloXSearchMediaItem(
    val id: Long,
    val kind: MeloXSearchKind,
    val title: String,
    val subtitle: String = "",
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
)

/** Search routes mirrored from upstream MeloX SearchView/NeteaseAPI. */
class NeteaseUniversalSearchClient(
    private val cookieProvider: () -> String,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val syntheticDeviceId = randomHex(26).uppercase()

    suspend fun searchMedia(keywords: String, kind: MeloXSearchKind, limit: Int = 30): List<MeloXSearchMediaItem> = withContext(Dispatchers.IO) {
        if (kind == MeloXSearchKind.Songs) return@withContext emptyList()
        val query = keywords.trim()
        if (query.isEmpty()) return@withContext emptyList()
        val response = eapi(
            "/api/search/get",
            JSONObject().put("s", query).put("type", kind.apiType).put("limit", limit.coerceIn(1, 50)).put("offset", 0),
        )
        val result = response.optJSONObject("result") ?: return@withContext emptyList()
        val values = when (kind) {
            MeloXSearchKind.Albums -> result.optJSONArray("albums")
            MeloXSearchKind.Artists -> result.optJSONArray("artists")
            MeloXSearchKind.Playlists -> result.optJSONArray("playlists")
            MeloXSearchKind.Podcasts -> result.optJSONArray("djRadios") ?: result.optJSONArray("radios")
            else -> null
        } ?: JSONArray()
        buildList {
            for (i in 0 until values.length()) {
                val value = values.optJSONObject(i) ?: continue
                val id = value.optLong("id", -1L)
                if (id <= 0L) continue
                when (kind) {
                    MeloXSearchKind.Albums -> add(MeloXSearchMediaItem(
                        id, kind,
                        value.optString("name").ifBlank { "未命名专辑" },
                        value.optJSONObject("artist")?.optString("name").orEmpty(),
                        secure(value.optString("picUrl").takeIf(String::isNotBlank)),
                        value.optInt("size", 0),
                    ))
                    MeloXSearchKind.Artists -> add(MeloXSearchMediaItem(
                        id, kind,
                        value.optString("name").ifBlank { "未知歌手" },
                        buildList {
                            val aliases = value.optJSONArray("alias") ?: JSONArray()
                            for (j in 0 until aliases.length()) aliases.optString(j).takeIf(String::isNotBlank)?.let(::add)
                        }.joinToString(" / "),
                        secure(value.optString("picUrl").takeIf(String::isNotBlank) ?: value.optString("img1v1Url").takeIf(String::isNotBlank)),
                    ))
                    MeloXSearchKind.Playlists -> add(MeloXSearchMediaItem(
                        id, kind,
                        value.optString("name").ifBlank { "未命名歌单" },
                        value.optJSONObject("creator")?.optString("nickname").orEmpty(),
                        secure(value.optString("coverImgUrl").takeIf(String::isNotBlank) ?: value.optString("picUrl").takeIf(String::isNotBlank)),
                        value.optInt("trackCount", 0),
                    ))
                    MeloXSearchKind.Podcasts -> add(MeloXSearchMediaItem(
                        id, kind,
                        value.optString("name").ifBlank { "未命名播客" },
                        value.optJSONObject("dj")?.optString("nickname").orEmpty(),
                        secure(value.optString("picUrl").takeIf(String::isNotBlank)),
                        value.optInt("programCount", 0),
                    ))
                    else -> Unit
                }
            }
        }
    }

    suspend fun songDetail(songId: Long): SearchSong? = withContext(Dispatchers.IO) {
        val arr = JSONArray().put(JSONObject().put("id", songId))
        val result = eapi("/api/v3/song/detail", JSONObject().put("c", arr.toString()))
        parseSong(result.optJSONArray("songs")?.optJSONObject(0))
    }

    suspend fun collectionSongs(item: MeloXSearchMediaItem): List<SearchSong> = withContext(Dispatchers.IO) {
        val values = when (item.kind) {
            MeloXSearchKind.Albums -> eapi("/api/v1/album/${item.id}", JSONObject()).optJSONArray("songs")
            MeloXSearchKind.Artists -> eapi("/api/v1/artist/${item.id}", JSONObject()).optJSONArray("hotSongs")
            MeloXSearchKind.Podcasts -> {
                val response = eapi(
                    "/api/dj/program/byradio",
                    JSONObject().put("radioId", item.id).put("limit", 100).put("offset", 0).put("asc", false),
                )
                val programs = response.optJSONArray("programs") ?: JSONArray()
                return@withContext buildList {
                    for (i in 0 until programs.length()) {
                        val program = programs.optJSONObject(i) ?: continue
                        parseSong(program.optJSONObject("mainSong"))?.let(::add)
                    }
                }
            }
            else -> JSONArray()
        } ?: JSONArray()
        buildList {
            for (i in 0 until values.length()) parseSong(values.optJSONObject(i))?.let(::add)
        }
    }

    private fun parseSong(value: JSONObject?): SearchSong? {
        value ?: return null
        val id = value.optLong("id", -1L)
        if (id <= 0L) return null
        val artistArray = value.optJSONArray("ar") ?: value.optJSONArray("artists") ?: JSONArray()
        val artists = buildList {
            for (i in 0 until artistArray.length()) artistArray.optJSONObject(i)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
        }.joinToString(" / ")
        val album = value.optJSONObject("al") ?: value.optJSONObject("album")
        return SearchSong(
            id = id,
            name = value.optString("name").ifBlank { "未知歌曲" },
            artists = artists.ifBlank { "未知歌手" },
            album = album?.optString("name").orEmpty(),
            artworkUrl = secure(album?.optString("picUrl")?.takeIf(String::isNotBlank) ?: album?.optString("blurPicUrl")?.takeIf(String::isNotBlank)),
            durationMs = value.optLong("dt", value.optLong("duration", 0L)).coerceAtLeast(0L),
        )
    }

    private fun eapi(uri: String, data: JSONObject): JSONObject {
        val now = System.currentTimeMillis()
        val cookieHeader = cookieProvider()
        val cookies = NeteaseSessionStore.parseCookie(cookieHeader)
        val authenticated = NeteaseSessionStore.containsMusicU(cookieHeader)
        val header = if (authenticated) authenticatedHeader(cookies, now) else JSONObject()
            .put("os", "ios").put("appver", "9.0.90").put("osver", "18.0")
            .put("buildver", (now / 1000L).toString()).put("channel", "distribution")
            .put("requestId", "${now}_0000").put("__csrf", "")
        val requestData = JSONObject(data.toString()).put("header", header).put("e_r", false)
        val json = requestData.toString()
        val digest = md5Hex("nobody${uri}use${json}md5forencrypt")
        val encrypted = "$uri-36cd479b6b5-$json-36cd479b6b5-$digest"
        val params = aes(encrypted.toByteArray(Charsets.UTF_8), "e82ckenh8dichen8".toByteArray()).toHex()
        val builder = Request.Builder()
            .url("https://interface.music.163.com${uri.replace("/api/", "/eapi/")}")
            .header("Accept", "*/*")
            .header("User-Agent", if (authenticated) "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)" else "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148")
        if (authenticated) builder.header("Cookie", encodedCookie(header))
        val request = builder.post(FormBody.Builder().add("params", params).build()).build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw IOException("网易云请求失败：HTTP ${response.code}")
            if (body.isBlank()) throw IOException("网易云返回了空响应")
            val result = JSONObject(body)
            val code = result.optInt("code", response.code)
            if (code !in 200..299) throw IOException(result.optString("message").ifBlank { result.optString("msg") }.ifBlank { "请求失败（$code）" })
            return result
        }
    }

    private fun authenticatedHeader(cookies: Map<String, String>, now: Long) = JSONObject()
        .put("osver", cookies["osver"] ?: "16.2")
        .put("deviceId", cookies["deviceId"] ?: syntheticDeviceId)
        .put("os", cookies["os"] ?: "iPhone OS")
        .put("appver", cookies["appver"] ?: "9.0.90")
        .put("versioncode", cookies["versioncode"] ?: "140")
        .put("mobilename", cookies["mobilename"] ?: "")
        .put("buildver", cookies["buildver"] ?: (now / 1000L).toString())
        .put("resolution", cookies["resolution"] ?: "1170x2532")
        .put("__csrf", cookies["__csrf"] ?: "")
        .put("channel", cookies["channel"] ?: "distribution")
        .put("requestId", "${now}_${randomDigits(4)}")
        .apply { cookies["MUSIC_U"]?.takeIf(String::isNotBlank)?.let { put("MUSIC_U", it) } }

    private fun encodedCookie(values: JSONObject): String = buildList {
        val it = values.keys(); while (it.hasNext()) add(it.next())
    }.sorted().joinToString("; ") { key -> "${enc(key)}=${enc(values.optString(key))}" }

    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    private fun secure(url: String?): String? = url?.let { if (it.startsWith("http://", true)) "https://${it.substringAfter("://")}" else it }
    private fun randomHex(n: Int): String { val b = ByteArray(n); SecureRandom().nextBytes(b); return b.joinToString("") { "%02x".format(it) } }
    private fun randomDigits(n: Int) = buildString(n) { repeat(n) { append(('0'.code + SecureRandom().nextInt(10)).toChar()) } }
    private fun md5Hex(v: String) = MessageDigest.getInstance("MD5").digest(v.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun aes(data: ByteArray, key: ByteArray): ByteArray = Cipher.getInstance("AES/ECB/PKCS5Padding").run { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")); doFinal(data) }
    private fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }
}
'''
write("android/app/src/main/kotlin/com/lladlam/melox/core/network/NeteaseUniversalSearchClient.kt", universal)

search = r'''package com.lladlam.melox.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.library.NeteasePlaylistDetail
import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXSearchKind
import com.lladlam.melox.core.network.MeloXSearchMediaItem
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.network.NeteaseUniversalSearchClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SearchAccent = Color(0xFFFF3147)
private val SearchCategories = listOf("排行榜", "播客", "华语", "欧美", "日语", "韩语", "粤语", "流行", "摇滚", "民谣", "电子", "说唱", "R&B/Soul", "古典", "ACG", "影视原声", "学习", "工作", "放松", "夜晚")

@Composable
fun SearchScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val songClient = remember(appContext) { NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) }) }
    val universal = remember(appContext) { NeteaseUniversalSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) }) }
    val library = remember(appContext) { NeteaseLibraryClient({ NeteaseSessionStore.readCookie(appContext) }) }

    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(MeloXSearchKind.Songs) }
    var songs by remember { mutableStateOf<List<SearchSong>>(emptyList()) }
    var media by remember { mutableStateOf<List<MeloXSearchMediaItem>>(emptyList()) }
    var recommendations by remember { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var categoryTitle by remember { mutableStateOf<String?>(null) }
    var categoryPlaylists by remember { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var selectedMedia by remember { mutableStateOf<MeloXSearchMediaItem?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { library.explorePlaylists("推荐歌单", 10) }.onSuccess { recommendations = it }
    }

    LaunchedEffect(query, kind) {
        val keyword = query.trim()
        if (keyword.isBlank()) {
            songs = emptyList(); media = emptyList(); error = null; loading = false
            return@LaunchedEffect
        }
        delay(350)
        loading = true; error = null
        val linkedId = parseSongLink(keyword)
        if (linkedId != null) {
            runCatching { universal.songDetail(linkedId) }
                .onSuccess { songs = listOfNotNull(it); media = emptyList(); kind = MeloXSearchKind.Songs }
                .onFailure { error = it.message ?: "无法读取歌曲链接" }
            loading = false
            return@LaunchedEffect
        }
        if (kind == MeloXSearchKind.Songs) {
            runCatching { songClient.ensureArtwork(songClient.searchSongs(keyword)) }
                .onSuccess { songs = it; media = emptyList() }
                .onFailure { error = it.message ?: "搜索失败" }
        } else {
            runCatching { universal.searchMedia(keyword, kind) }
                .onSuccess { media = it; songs = emptyList() }
                .onFailure { error = it.message ?: "搜索失败" }
        }
        loading = false
    }

    BackHandler(enabled = selectedMedia != null || categoryTitle != null) {
        if (selectedMedia != null) selectedMedia = null else { categoryTitle = null; categoryPlaylists = emptyList() }
    }

    selectedMedia?.let { destination ->
        SearchCollectionDetail(destination, universal, library) { selectedMedia = null }
        return
    }

    categoryTitle?.let { title ->
        SearchCategoryPage(title, categoryPlaylists, loading, error, onBack = {
            categoryTitle = null; categoryPlaylists = emptyList(); error = null
        }, onPlaylist = { selectedMedia = it.asSearchItem() })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 26.dp),
    ) {
        Text(
            "搜索",
            modifier = Modifier.padding(horizontal = 20.dp),
            fontSize = 40.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        SearchField(query, { query = it })
        if (query.isNotBlank()) {
            SearchScopes(kind = kind, onKind = { kind = it })
        }

        Box(Modifier.weight(1f)) {
            when {
                query.isBlank() -> SearchDiscovery(
                    recommendations = recommendations,
                    onPlaylist = { selectedMedia = it.asSearchItem() },
                    onCategory = { category ->
                        categoryTitle = category
                        loading = true; error = null
                        scope.launch {
                            runCatching {
                                if (category == "播客") emptyList() else library.explorePlaylists(category, 50)
                            }.onSuccess { categoryPlaylists = it }
                                .onFailure { error = it.message ?: "类别加载失败" }
                            loading = false
                        }
                    },
                )
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SearchAccent) }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
                kind == MeloXSearchKind.Songs -> SearchSongResults(songs) { song ->
                    PlaybackCommands.playQueue(context, songs, song.id)
                }
                else -> SearchMediaResults(media) { selectedMedia = it }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(50.dp)
            .meloXLiquidButton(
                shape = RoundedCornerShape(25.dp),
                surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .055f),
                lensRadius = 9.dp,
                refractionHeight = 16.dp,
            )
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("⌕", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isBlank()) Text("音乐内容或网易云链接", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotBlank()) Text("×", modifier = Modifier.clickable { onValueChange("") }.padding(5.dp), fontSize = 22.sp)
    }
}

@Composable
private fun SearchScopes(kind: MeloXSearchKind, onKind: (MeloXSearchKind) -> Unit) {
    val values = MeloXSearchKind.entries.filter { it != MeloXSearchKind.Podcasts || MeloXSettingsRuntime.podcastsEnabled }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(values) { item ->
            Box(
                Modifier.height(34.dp).meloXLiquidButton(
                    shape = RoundedCornerShape(17.dp),
                    tint = if (item == kind) SearchAccent.copy(alpha = .30f) else Color.Transparent,
                    surfaceColor = if (item == kind) SearchAccent.copy(alpha = .16f) else MaterialTheme.colorScheme.onBackground.copy(alpha = .045f),
                ).clickable { onKind(item) }.padding(horizontal = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.title, color = if (item == kind) SearchAccent else MaterialTheme.colorScheme.onSurface, fontWeight = if (item == kind) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SearchDiscovery(
    recommendations: List<NeteasePlaylistSummary>,
    onPlaylist: (NeteasePlaylistSummary) -> Unit,
    onCategory: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MeloXBottomContentClearance),
    ) {
        if (recommendations.isNotEmpty()) {
            item { Text("热门推荐", modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(recommendations, key = { it.id }) { p ->
                        Column(Modifier.width(160.dp).clickable { onPlaylist(p) }) {
                            AsyncImage(p.coverUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(160.dp).clip(RoundedCornerShape(14.dp)))
                            Text(p.name, modifier = Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        item { Text("浏览类别", modifier = Modifier.padding(start = 20.dp, top = 26.dp, bottom = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        items(SearchCategories.filter { it != "播客" || MeloXSettingsRuntime.podcastsEnabled }.chunked(2)) { pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { index, category -> SearchCategoryCard(category, Modifier.weight(1f)) { onCategory(category) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SearchCategoryCard(title: String, modifier: Modifier, onClick: () -> Unit) {
    val tint = when (title.hashCode().mod(5)) {
        0 -> Color(0xFFE76F51); 1 -> Color(0xFF7B61FF); 2 -> Color(0xFF2A9D8F); 3 -> Color(0xFFE84A8A); else -> Color(0xFF3A86FF)
    }
    Box(
        modifier.height(92.dp).clip(RoundedCornerShape(15.dp)).background(tint).clickable(onClick = onClick).padding(14.dp),
        contentAlignment = Alignment.BottomStart,
    ) { Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SearchSongResults(values: List<SearchSong>, onPlay: (SearchSong) -> Unit) {
    if (values.isEmpty()) { SearchEmpty("没有找到歌曲"); return }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = MeloXBottomContentClearance)) {
        items(values, key = { it.id }) { song ->
            Row(Modifier.fillMaxWidth().clickable { onPlay(song) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(song.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text("${song.artists}${song.album.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
        }
    }
}

@Composable
private fun SearchMediaResults(values: List<MeloXSearchMediaItem>, onOpen: (MeloXSearchMediaItem) -> Unit) {
    if (values.isEmpty()) { SearchEmpty("没有找到内容"); return }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = MeloXBottomContentClearance)) {
        items(values, key = { "${it.kind}-${it.id}" }) { item ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(item) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(item.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(54.dp).clip(if (item.kind == MeloXSearchKind.Artists) CircleShape else RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp)
                    Text(item.subtitle.ifBlank { if (item.trackCount > 0) "${item.trackCount} 首" else item.kind.title }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), fontSize = 13.sp)
                }
                Text("›", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .3f))
            }
        }
    }
}

@Composable
private fun SearchCategoryPage(
    title: String,
    values: List<NeteasePlaylistSummary>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onPlaylist: (NeteasePlaylistSummary) -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 16.dp)) {
        SearchDetailHeader(title, onBack)
        when {
            title == "播客" -> SearchEmpty("播客请使用上方搜索框切换到“播客”范围进行搜索。")
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> SearchEmpty(error)
            values.isEmpty() -> SearchEmpty("暂无内容")
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp).let { PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = MeloXBottomContentClearance) }) {
                items(values, key = { it.id }) { p ->
                    Row(Modifier.fillMaxWidth().clickable { onPlaylist(p) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(p.coverUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(58.dp).clip(RoundedCornerShape(9.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text("${p.trackCount} 首歌曲", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCollectionDetail(
    item: MeloXSearchMediaItem,
    universal: NeteaseUniversalSearchClient,
    library: NeteaseLibraryClient,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var songs by remember(item.id, item.kind) { mutableStateOf<List<SearchSong>>(emptyList()) }
    var loading by remember(item.id, item.kind) { mutableStateOf(true) }
    var error by remember(item.id, item.kind) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.id, item.kind) {
        loading = true; error = null
        runCatching {
            if (item.kind == MeloXSearchKind.Playlists) library.playlistDetail(item.id).songs else universal.collectionSongs(item)
        }.onSuccess { songs = it }.onFailure { error = it.message ?: "内容加载失败" }
        loading = false
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 16.dp)) {
        SearchDetailHeader(item.title, onBack)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = MeloXBottomContentClearance)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(item.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(210.dp).clip(if (item.kind == MeloXSearchKind.Artists) CircleShape else RoundedCornerShape(15.dp)))
                    Text(item.title, modifier = Modifier.padding(top = 16.dp), fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.subtitle.isNotBlank()) Text(item.subtitle, modifier = Modifier.padding(top = 5.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SearchPlayButton("随机") {
                            val shuffled = songs.shuffled(); shuffled.firstOrNull()?.let { PlaybackCommands.playQueue(context, shuffled, it.id) }
                        }
                        SearchPlayButton("播放") { songs.firstOrNull()?.let { PlaybackCommands.playQueue(context, songs, it.id) } }
                    }
                }
            }
            when {
                loading -> item { Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                error != null -> item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
                else -> items(songs, key = { it.id }) { song ->
                    Row(Modifier.fillMaxWidth().clickable { PlaybackCommands.playQueue(context, songs, song.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(song.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(7.dp)))
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artists, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchDetailHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).meloXLiquidButton(shape = CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Text("‹", fontSize = 30.sp) }
        Spacer(Modifier.width(12.dp))
        Text(title, Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchPlayButton(title: String, onClick: () -> Unit) {
    Box(Modifier.height(44.dp).width(120.dp).meloXLiquidButton(shape = RoundedCornerShape(22.dp), surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .08f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SearchEmpty(message: String) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) { Text(message, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)) }
}

private fun parseSongLink(value: String): Long? {
    val patterns = listOf(Regex("[?&]id=(\\d+)"), Regex("/song/(\\d+)"))
    return patterns.firstNotNullOfOrNull { it.find(value)?.groupValues?.getOrNull(1)?.toLongOrNull() }
}

private fun NeteasePlaylistSummary.asSearchItem() = MeloXSearchMediaItem(
    id = id,
    kind = MeloXSearchKind.Playlists,
    title = name,
    subtitle = creatorName,
    artworkUrl = coverUrl,
    trackCount = trackCount,
)
'''
write("android/app/src/main/kotlin/com/lladlam/melox/ui/search/SearchScreen.kt", search)

# Wire runtime settings into the app theme and active player.
main = "android/app/src/main/kotlin/com/lladlam/melox/MainActivity.kt"
text = read(main)
if "MeloXSettingsPreferences" not in text:
    text = text.replace("import com.lladlam.melox.ui.theme.MeloXTheme\n", "import com.lladlam.melox.ui.theme.MeloXTheme\nimport com.lladlam.melox.ui.settings.MeloXSettingsPreferences\n")
text = text.replace("        consumePlaybackIntent(intent)\n\n        setContent {", "        consumePlaybackIntent(intent)\n        MeloXSettingsPreferences.initialize(this)\n\n        setContent {")
write(main, text)

theme = "android/app/src/main/kotlin/com/lladlam/melox/ui/theme/MeloXTheme.kt"
text = read(theme)
if "MeloXSettingsRuntime" not in text:
    text = text.replace("import androidx.compose.ui.text.font.FontFamily\n", "import androidx.compose.ui.text.font.FontFamily\nimport com.lladlam.melox.ui.settings.MeloXSettingsRuntime\nimport com.lladlam.melox.ui.settings.MeloXThemeMode\n")
text = text.replace(
'''fun MeloXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {''',
'''fun MeloXTheme(
    darkTheme: Boolean = when (MeloXSettingsRuntime.themeMode) {
        MeloXThemeMode.System -> isSystemInDarkTheme()
        MeloXThemeMode.Light -> false
        MeloXThemeMode.Dark -> true
    },
    content: @Composable () -> Unit,
) {''')
write(theme, text)

host = "android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSNowPlayingSharedHost.kt"
text = read(host)
if "import androidx.compose.runtime.DisposableEffect\n" not in text:
    text = text.replace("import androidx.compose.runtime.CompositionLocalProvider\n", "import androidx.compose.runtime.CompositionLocalProvider\nimport androidx.compose.runtime.DisposableEffect\n")
if "import androidx.compose.ui.platform.LocalView\n" not in text:
    text = text.replace("import androidx.compose.ui.input.nestedscroll.nestedScroll\n", "import androidx.compose.ui.input.nestedscroll.nestedScroll\nimport androidx.compose.ui.platform.LocalView\n")
if "import com.lladlam.melox.ui.settings.MeloXSettingsRuntime\n" not in text:
    text = text.replace("import com.lladlam.melox.ui.glass.LocalMeloXBackdrop\n", "import com.lladlam.melox.ui.glass.LocalMeloXBackdrop\nimport com.lladlam.melox.ui.settings.MeloXSettingsRuntime\n")
if "import androidx.compose.foundation.background\n" not in text:
    text = text.replace("import androidx.compose.foundation.gestures.Orientation\n", "import androidx.compose.foundation.background\nimport androidx.compose.foundation.gestures.Orientation\n")
text = text.replace(
'''    val scope = rememberCoroutineScope()

    // Two distinct scenes''',
'''    val scope = rememberCoroutineScope()
    val hostView = LocalView.current
    DisposableEffect(MeloXSettingsRuntime.keepScreenOn) {
        val previous = hostView.keepScreenOn
        hostView.keepScreenOn = MeloXSettingsRuntime.keepScreenOn
        onDispose { hostView.keepScreenOn = previous }
    }

    // Two distinct scenes''')
text = text.replace(
'''                    MeloXFlowingLightBackdrop(
                        artworkUrl = state.artworkUrl,
                        isPlaying = state.isPlaying,
                    )''',
'''                    if (MeloXSettingsRuntime.flowingBackdropEnabled) {
                        MeloXFlowingLightBackdrop(
                            artworkUrl = state.artworkUrl,
                            isPlaying = state.isPlaying,
                        )
                    } else {
                        Box(Modifier.fillMaxSize().background(Color(0xFF15171B)))
                    }''')
text = text.replace(
'''    val playbackScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.74f,''',
'''    val playbackScale by animateFloatAsState(
        targetValue = if (!MeloXSettingsRuntime.artworkMotionEnabled || state.isPlaying) 1f else 0.74f,''')
write(host, text)

lyrics = "android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSLyricsPanel.kt"
text = read(lyrics)
if "import com.lladlam.melox.ui.settings.MeloXSettingsRuntime\n" not in text:
    text = text.replace("import com.lladlam.melox.core.network.NeteaseSearchClient\n", "import com.lladlam.melox.core.network.NeteaseSearchClient\nimport com.lladlam.melox.ui.settings.MeloXSettingsRuntime\n")
old_translation = '''        line.translation
            ?.takeIf(String::isNotBlank)
            ?.let { translation ->
                Text(
                    text = translation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp),
                    textAlign = TextAlign.Start,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = Color.White.copy(
                        alpha = lerp(0.28f, 0.68f, focusColorProgress),
                    ),
                )
            }

        line.romanization
            ?.takeIf(String::isNotBlank)
            ?.let { romanization ->
                Text(
                    text = romanization,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp),
                    textAlign = TextAlign.Start,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color.White.copy(
                        alpha = lerp(0.22f, 0.50f, focusColorProgress),
                    ),
                )
            }'''
new_translation = '''        if (MeloXSettingsRuntime.showLyricTranslation) {
            line.translation
                ?.takeIf(String::isNotBlank)
                ?.let { translation ->
                    Text(
                        text = translation,
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                        textAlign = TextAlign.Start,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        color = Color.White.copy(alpha = lerp(0.28f, 0.68f, focusColorProgress)),
                    )
                }
        }

        if (MeloXSettingsRuntime.showLyricRomanization) {
            line.romanization
                ?.takeIf(String::isNotBlank)
                ?.let { romanization ->
                    Text(
                        text = romanization,
                        modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                        textAlign = TextAlign.Start,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = Color.White.copy(alpha = lerp(0.22f, 0.50f, focusColorProgress)),
                    )
                }
        }'''
if old_translation not in text:
    raise RuntimeError("lyrics translation block not found")
text = text.replace(old_translation, new_translation, 1)
write(lyrics, text)

(ROOT / "tools/one_shot_settings_search_patch.py").unlink(missing_ok=True)
(ROOT / ".github/workflows/one-shot-settings-search-patch.yml").unlink(missing_ok=True)
