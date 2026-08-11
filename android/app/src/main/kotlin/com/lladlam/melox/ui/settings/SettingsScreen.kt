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
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.playback.MeloXAutoMixFadeCurve
import com.lladlam.melox.playback.MeloXAutoMixFallback
import com.lladlam.melox.playback.MeloXAutoMixMode
import com.lladlam.melox.playback.MeloXAutoMixSettings
import com.lladlam.melox.playback.MeloXPlaybackModePreferences
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
            SettingsRoute.Developer -> DeveloperSettings()
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
    SettingsInfoCard("耳机断开时暂停", "已由 Media3 播放服务启用")
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
    }
    Text("交叉淡化时长", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        listOf(3_000L, 6_000L, 8_000L, 12_000L).forEach { duration ->
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
        listOf(30_000L, 60_000L, 90_000L, 120_000L).forEach { lead ->
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
    SettingsExternalToggleRow("速度匹配", settings.tempoMatching, "有可靠 BPM 分析时，最多调整 5%。") {
        MeloXPlaybackModePreferences.setAutoMixBoolean(context, "automix_tempo_matching", it)
        refresh()
    }
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
    SettingsToggleRow(context, "普通 LRC 生成逐字时间", "lyrics_pseudo_timing", true, "按 Unicode 字素分配行时长，不覆盖真实 YRC。")
    SettingsToggleRow(context, "点击歌词跳转进度", "lyrics_tap_seek", true)
    SettingsToggleRow(context, "自动跟随当前歌词", "lyrics_auto_follow", true)
    SettingsToggleRow(context, "减弱歌词动画", "lyrics_reduce_motion", false, "保留逐字高亮，关闭弹性、抬升与光晕。")

    LyricsChoiceSetting(context, "歌词时间偏移", "lyrics_advance_ms", 0, listOf(-400, -200, 0, 200, 400)) { value ->
        if (value == 0) "同步" else if (value > 0) "提前 ${value}ms" else "延后 ${-value}ms"
    }
    LyricsChoiceSetting(context, "手动滚动后恢复跟随", "lyrics_follow_delay_ms", 3_000, listOf(1_500, 3_000, 5_000, 8_000)) { "${it / 1_000f} 秒" }
    LyricsFloatChoiceSetting(context, "歌词字号", "lyrics_font_scale", 1f, listOf(.85f, 1f, 1.12f, 1.25f)) { "${(it * 100).toInt()}%" }
    LyricsFloatChoiceSetting(context, "行间距", "lyrics_spacing_scale", 1f, listOf(.8f, 1f, 1.2f, 1.4f)) { "${(it * 100).toInt()}%" }
    LyricsFloatChoiceSetting(context, "远近模糊", "lyrics_blur_strength", 1f, listOf(0f, .6f, 1f, 1.4f)) { if (it == 0f) "关闭" else "${(it * 100).toInt()}%" }
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
private fun ContentFeatureSettings(context: android.content.Context) {
    SettingsToggleRow(context, "播客", "feature_podcasts", true)
    SettingsToggleRow(context, "最近播放", "feature_history", true)
    SettingsInfoCard("下载", "已启用 · 更多菜单可下载，播放优先本地文件")
    Spacer(Modifier.height(10.dp))
    SettingsInfoCard("云盘", "尚未迁移，暂不显示无效开关")
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
    SettingsInfoCard("内容展示", "播放量与精品歌单开关尚未接入数据层，暂不提供无效选项")
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

    SettingsInfoCard("自动缓存", "尚未迁移播放次数策略；手动下载始终可用")
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
    SettingsInfoCard("标签栏", "页面开关立即生效；设置与搜索始终保留")
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
    SettingsInfoCard("剪贴板链接识别", "尚未迁移 Android 前台生命周期处理，暂不提供无效开关")
}

@Composable
private fun AboutSettings(context: android.content.Context) {
    SettingsGlassGroup {
        Column(Modifier.padding(18.dp)) {
            Text("MeloX Android", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("MeloX 的 Android 原生迁移版。", modifier = Modifier.padding(top=7.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha=.62f))
            Text("Android 原生迁移与维护：lladlam", modifier = Modifier.padding(top=14.dp), fontWeight=FontWeight.SemiBold)
            Text("上游 iOS 原生项目：youshen2/MeloX（SwiftUI）", modifier = Modifier.padding(top=5.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha=.58f))
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
    SettingsInfoCard("BeatNet", "上游 CoreML 模型不能直接在 Android 运行；当前智能模式会使用可用分析，否则按降级策略切歌")
    Spacer(Modifier.height(10.dp))
    SettingsInfoCard("播放器与网络日志", "使用 Logcat 的 MeloXPlayback / 网络标签；不再保存无效偏好")
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
