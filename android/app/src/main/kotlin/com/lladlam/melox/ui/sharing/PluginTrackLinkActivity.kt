package com.lladlam.melox.ui.sharing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lladlam.melox.core.plugin.PluginTrackLink
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.core.music.provider.MusicCapability
import com.lladlam.melox.playback.ProviderPlaybackCommands
import com.lladlam.melox.ui.MeloXPageHost
import com.lladlam.melox.ui.prepareMeloXPageWindow
import com.lladlam.melox.ui.theme.MeloXTheme
import com.lladlam.melox.ui.glass.MeloXIosTopBar
import com.lladlam.melox.ui.glass.MeloXIosGroupedList
import com.lladlam.melox.ui.glass.MeloXIosListRow

/** Deep links preserve identity and show an explicit native playback action; they never auto-install/enable. */
class PluginTrackLinkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val track = runCatching { PluginTrackLink.parse(intent?.dataString.orEmpty()) }.getOrNull()
        if (track == null) { finish(); return }
        enableEdgeToEdge(); prepareMeloXPageWindow()
        val registry = MeloXMusicProviders.create(applicationContext)
        setContent { MeloXPageHost { MeloXTheme {
            val generation by registry.changes.collectAsState()
            val provider = remember(generation) { registry[track.id.sourceId] }
            var message by remember { mutableStateOf<String?>(null) }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
                MeloXIosTopBar(title = "音乐源歌曲")
                MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
                    MeloXIosListRow(title = track.title, subtitle = "${provider?.displayName ?: "来源缺失或已禁用"}\n${track.id.sourceId.value}\n${track.id.value}", onClick = {})
                }
                Button(enabled = provider?.supports(MusicCapability.Playback) == true, onClick = {
                    val music = MusicTrack(track.id, track.title, listOf(MusicArtistRef(name = "未知歌手")))
                    ProviderPlaybackCommands.playQueue(this@PluginTrackLinkActivity, listOf(music), music.id, onFailure = { message = "无法播放，请检查音源或账号状态" })
                }) { Text("使用已安装音源播放") }
                message?.let { Text(it) }
                TextButton(onClick = ::finish) { Text("关闭") }
            }
        } } }
    }
}
