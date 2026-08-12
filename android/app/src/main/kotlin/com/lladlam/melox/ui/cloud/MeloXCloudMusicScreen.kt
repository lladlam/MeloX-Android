package com.lladlam.melox.ui.cloud

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.network.MeloXCloudSong
import com.lladlam.melox.core.network.NeteaseUniversalSearchClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.glass.meloXLiquidButton
import kotlinx.coroutines.launch

@Composable
fun MeloXCloudMusicScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext
    val client = remember(app) {
        NeteaseUniversalSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(app) })
    }
    val scope = rememberCoroutineScope()
    var values by remember { mutableStateOf<List<MeloXCloudSong>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var quota by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<MeloXCloudSong?>(null) }
    var uploading by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        error = null
        runCatching { client.cloudSongs() }
            .onSuccess { page ->
                values = page.values
                quota = if (page.maxBytes > 0L) {
                    "${formatBytes(page.usedBytes)} / ${formatBytes(page.maxBytes)} · ${page.totalCount} 首"
                } else "${page.totalCount} 首"
            }
            .onFailure { error = it.message ?: "云盘加载失败" }
        loading = false
    }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            error = null
            runCatching { client.uploadCloudSong(app, uri) }
                .onSuccess { refresh() }
                .onFailure { error = it.message ?: "云盘上传失败" }
            uploading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("从音乐云盘删除？") },
            text = { Text("将从网易云音乐账号中删除《${target.song.name}》。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        runCatching { client.deleteCloudSong(target.id) }
                            .onSuccess { values = values.filterNot { it.id == target.id } }
                            .onFailure { error = it.message ?: "云盘删除失败" }
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }

    val displayed = remember(values, query) {
        val normalized = query.trim()
        if (normalized.isBlank()) values else values.filter {
            listOf(it.song.name, it.song.artists, it.song.album).any { text ->
                text.contains(normalized, ignoreCase = true)
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("音乐云盘", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(quota, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
            }
            Text(
                if (uploading) "上传中…" else "上传",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(enabled = !uploading) { uploadLauncher.launch("audio/*") }.padding(10.dp),
            )
            Text("刷新", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { scope.launch { refresh() } }.padding(10.dp))
        }
        Box(
            Modifier.padding(horizontal = 18.dp).fillMaxWidth().meloXLiquidButton(
                shape = RoundedCornerShape(22.dp),
                surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .055f),
            ).padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            if (query.isBlank()) Text("搜索全部云盘歌曲", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f))
            BasicTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        when {
            loading && values.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null && values.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = MeloXBottomContentClearance),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(displayed, key = { it.id }) { item ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                            PlaybackCommands.playQueue(context, displayed.map(MeloXCloudSong::song), item.song.id)
                        }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(item.song.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text("${item.song.artists} · ${formatBytes(item.fileSize)}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                        }
                        Text("删除", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.clickable { pendingDelete = item }.padding(10.dp))
                    }
                }
                if (displayed.isEmpty()) item { Text("音乐云盘是空的", modifier = Modifier.fillMaxWidth().padding(36.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)) }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) } }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
