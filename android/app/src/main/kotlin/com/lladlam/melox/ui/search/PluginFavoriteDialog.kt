package com.lladlam.melox.ui.search

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.plugin.PluginMusicProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PluginFavoriteDialog(provider: PluginMusicProvider, track: MusicTrack, dismiss: () -> Unit) {
    key(provider, track.id) { PluginFavoriteContent(provider, track, dismiss) }
}

@Composable
private fun PluginFavoriteContent(provider: PluginMusicProvider, track: MusicTrack, dismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var favorite by remember(track.id) { mutableStateOf<Boolean?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(provider, track.id) {
        try { favorite = provider.isFavorite(track) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = com.lladlam.melox.core.plugin.PluginErrors.describe(failure) }
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text(track.title) },
        text = { Text(error ?: favorite?.let { if (it) "已收藏到 ${provider.displayName}" else "尚未收藏到 ${provider.displayName}" } ?: "正在读取收藏状态…") },
        confirmButton = { TextButton(enabled = favorite != null && !busy, onClick = {
            busy = true
            scope.launch {
                try { provider.setFavorite(track, !favorite!!); favorite = !favorite!!; error = null }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = com.lladlam.melox.core.plugin.PluginErrors.describe(failure) }
                finally { busy = false }
            }
        }) { Text(if (favorite == true) "取消音源收藏" else "收藏到音源") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("关闭") } })
}
