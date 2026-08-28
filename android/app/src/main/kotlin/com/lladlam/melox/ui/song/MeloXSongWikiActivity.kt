package com.lladlam.melox.ui.song

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXWikiSection
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.finishMeloXPage
import com.lladlam.melox.ui.startMeloXPage
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassCard
import com.lladlam.melox.ui.glass.MeloXPinnedListPage
import com.lladlam.melox.ui.glass.MeloXShapes
import com.lladlam.melox.ui.theme.MeloXTheme

class MeloXSongWikiActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val song = intent.toSong() ?: run {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            MeloXTheme {
                MeloXSongWikiScreen(song, ::finishMeloXPage)
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTISTS = "artists"
        private const val EXTRA_ALBUM = "album"
        private const val EXTRA_ARTWORK = "artwork"
        private const val EXTRA_DURATION = "duration"

        fun launch(context: Context, song: SearchSong) {
            if (song.id <= 0L || song.providerTrack != null) return
            context.startMeloXPage(
                Intent(context, MeloXSongWikiActivity::class.java)
                    .putExtra(EXTRA_ID, song.id)
                    .putExtra(EXTRA_TITLE, song.name)
                    .putExtra(EXTRA_ARTISTS, song.artists)
                    .putExtra(EXTRA_ALBUM, song.album)
                    .putExtra(EXTRA_ARTWORK, song.artworkUrl)
                    .putExtra(EXTRA_DURATION, song.durationMs)
                    .apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            )
        }

        private fun Intent.toSong(): SearchSong? {
            val id = getLongExtra(EXTRA_ID, -1L)
            if (id <= 0L) return null
            return SearchSong(
                id = id,
                name = getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "未知歌曲" },
                artists = getStringExtra(EXTRA_ARTISTS).orEmpty().ifBlank { "未知歌手" },
                album = getStringExtra(EXTRA_ALBUM).orEmpty(),
                artworkUrl = getStringExtra(EXTRA_ARTWORK),
                durationMs = getLongExtra(EXTRA_DURATION, 0L),
            )
        }
    }
}

@Composable
private fun MeloXSongWikiScreen(song: SearchSong, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext
    val client = remember(app) {
        NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(app) })
    }
    var sections by remember(song.id) { mutableStateOf<List<MeloXWikiSection>>(emptyList()) }
    var loading by remember(song.id) { mutableStateOf(true) }
    var error by remember(song.id) { mutableStateOf<String?>(null) }
    var refreshKey by remember(song.id) { mutableIntStateOf(0) }

    LaunchedEffect(song.id, refreshKey) {
        loading = true
        error = null
        runCatching { client.songWiki(song.id) }
            .onSuccess { sections = it }
            .onFailure { error = it.message ?: "歌曲百科加载失败" }
        loading = false
    }

    MeloXPinnedListPage(
        title = "歌曲百科",
        onNavigateBack = onBack,
        bottomPadding = MeloXBottomContentClearance,
    ) {
        item(key = "song") {
            MeloXGlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = song.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(82.dp).clip(RoundedCornerShape(16.dp)),
                    )
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(song.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(song.artists, modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        song.album.takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        when {
            loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            error != null -> item(key = "error") {
                MeloXGlassCard(Modifier.fillMaxWidth()) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    MeloXGlassButton(
                        onClick = { refreshKey++ },
                        modifier = Modifier.fillMaxWidth(),
                        style = MeloXGlassButtonStyle.BorderedProminent,
                        shape = MeloXShapes.capsule,
                    ) { Text("重试") }
                }
            }
            sections.isEmpty() -> item(key = "empty") {
                MeloXGlassCard(Modifier.fillMaxWidth()) {
                    Text("暂时没有这首歌曲的百科内容", fontWeight = FontWeight.SemiBold)
                    Text("网易云尚未返回音乐记忆、标签或相关推荐。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> sections.forEachIndexed { index, section ->
                item(key = "section-$index-${section.title}") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(section.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                        MeloXGlassCard(Modifier.fillMaxWidth()) {
                            section.lines.forEachIndexed { lineIndex, line ->
                                Text(
                                    line,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                    color = if (lineIndex == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
