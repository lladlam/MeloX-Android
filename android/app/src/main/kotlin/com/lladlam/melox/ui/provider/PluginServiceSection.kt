package com.lladlam.melox.ui.provider

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lladlam.melox.core.plugin.*
import com.lladlam.melox.core.music.provider.MusicProviderSelectionStore
import com.lladlam.melox.ui.glass.MeloXIosGroupedList
import com.lladlam.melox.ui.glass.MeloXIosListRow
import com.lladlam.melox.ui.settings.PluginAccounts
import com.lladlam.melox.ui.settings.MeloXSettingsDetailActivity
import com.lladlam.melox.ui.settings.SettingsRoute

/** Shared service/account center content; uses the app's existing grouped Liquid Glass rows. */
@Composable
internal fun PluginServiceSection() {
    val context = LocalContext.current
    val manager = remember(context) { MeloXPlugins.get(context) }
    val plugins by manager.plugins.collectAsState()
    val revision by MusicProviderSelectionStore.changes.collectAsState()
    val selected = remember(revision) { MusicProviderSelectionStore.selectedSourceId(context) }
    var expanded by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            if (selected.builtIn == null && selected !in plugins) {
                MeloXIosListRow(title = "所选来源缺失", subtitle = selected.value, onClick = {})
            }
            plugins.values.sortedBy { it.manifest.name }.forEachIndexed { index, state ->
                val manifest = state.manifest
                MeloXIosListRow(title = (if (selected == manifest.sourceId) "✓ " else "") + manifest.name,
                    subtitle = "${manifest.version} · ${state.status} · ${manifest.sourceId.value}",
                    onClick = {
                        if (state.status == PluginManager.Status.Enabled) MusicProviderSelectionStore.setSelectedSourceId(context, manifest.sourceId)
                        expanded = if (expanded == manifest.sourceId.value) null else manifest.sourceId.value
                    }, showTopSeparator = index > 0)
            }
            MeloXIosListRow(title = "管理音乐源插件", subtitle = "安装、权限、更新、诊断与账号",
                onClick = { MeloXSettingsDetailActivity.launch(context, SettingsRoute.Plugins, MusicProviderSelectionStore.selectedSource(context)) }, showTopSeparator = plugins.isNotEmpty())
        }
        plugins.values.firstOrNull { it.manifest.sourceId.value == expanded }?.let { PluginAccounts(it.manifest, manager) }
    }
}
