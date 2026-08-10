package com.lladlam.melox.ui.settings

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
