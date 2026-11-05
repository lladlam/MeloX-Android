package com.lladlam.melox.ui.provider

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lladlam.melox.core.music.model.SourceId
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.playback.ProviderPlaybackCommands
import com.lladlam.melox.ui.search.PluginCatalogBrowser
import com.lladlam.melox.ui.search.ProviderSearchSongResults
import com.lladlam.melox.ui.glass.MeloXIosTopBar

/** Native tab shell, shared registry, collection navigation and the existing song rows/actions. */
@Composable
internal fun PluginSourcePage(source: SourceId, section: String) {
    val context = LocalContext.current
    val registry = remember(context) { MeloXMusicProviders.create(context) }
    val generation by registry.changes.collectAsState()
    var error by remember(source) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MeloXIosTopBar(title = registry[source]?.displayName ?: source.value)
        error?.let { Text(it, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error) }
        Box(Modifier.weight(1f)) {
            PluginCatalogBrowser(registry, source.value, "", generation, initialSection = section) { tracks ->
                ProviderSearchSongResults(values = tracks, failures = emptyList(), showSource = true, onPlay = { track ->
                    ProviderPlaybackCommands.playQueue(context, tracks, track.id, onFailure = { error = "播放失败" })
                })
            }
        }
    }
}
