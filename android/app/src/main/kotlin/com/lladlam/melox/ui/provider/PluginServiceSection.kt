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
            if (selected.builtIn == null && (selected !in plugins || plugins[selected]?.manifest?.isMusicSource == false)) {
                MeloXIosListRow(title = "所选来源不可用", subtitle = selected.value, onClick = {})
            }
            val sourcePlugins = plugins.values.filter { it.manifest.isMusicSource }.sortedBy { it.manifest.name }
            sourcePlugins.forEachIndexed { index, state ->
                val manifest = state.manifest
                MeloXIosListRow(title = (if (selected == manifest.sourceId) "✓ " else "") + manifest.name,
                    subtitle = "${manifest.version} · ${state.status} · ${manifest.sourceId.value}",
                    onClick = {
                        if (state.status == PluginManager.Status.Enabled) MusicProviderSelectionStore.setSelectedSourceId(context, manifest.sourceId)
                        expanded = if (expanded == manifest.sourceId.value) null else manifest.sourceId.value
                    }, showTopSeparator = index > 0)
            }
            val resolverPlugins = plugins.values.filter {
                !it.manifest.isMusicSource && PluginCapability.Resolver in it.manifest.capabilities
            }.sortedBy { it.manifest.name }
            if (resolverPlugins.isNotEmpty()) {
                MeloXIosListRow(
                    title = "解析器插件",
                    subtitle = resolverPlugins.joinToString { it.manifest.name } +
                        " · 启用后在第三方音源回退阶段使用",
                    onClick = {
                        MeloXSettingsDetailActivity.launch(
                            context, SettingsRoute.Plugins,
                            MusicProviderSelectionStore.selectedSource(context),
                        )
                    },
                    showTopSeparator = sourcePlugins.isNotEmpty(),
                )
            }
            MeloXIosListRow(title = "管理音乐源插件", subtitle = "安装、权限、更新、诊断与账号",
                onClick = { MeloXSettingsDetailActivity.launch(context, SettingsRoute.Plugins, MusicProviderSelectionStore.selectedSource(context)) },
                showTopSeparator = sourcePlugins.isNotEmpty() || resolverPlugins.isNotEmpty())
        }
        plugins.values.firstOrNull { it.manifest.sourceId.value == expanded }?.let { PluginAccounts(it.manifest, manager) }
    }
}
