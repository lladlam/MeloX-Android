package com.lladlam.melox.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lladlam.melox.core.plugin.MeloXPlugins
import com.lladlam.melox.core.plugin.PluginArchiveReader
import com.lladlam.melox.core.plugin.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings uses the same lifecycle owner and dynamic directory as search and playback. */
@Composable
internal fun PluginSettings() {
    val context = LocalContext.current
    val manager = remember(context) { MeloXPlugins.get(context) }
    val plugins by manager.plugins.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingArchive by remember { mutableStateOf<ByteArray?>(null) }
    var pendingManifest by remember { mutableStateOf<com.lladlam.melox.core.plugin.PluginManifest?>(null) }
    var pendingRemoval by remember { mutableStateOf<com.lladlam.melox.core.music.model.SourceId?>(null) }
    var diagnostics by remember { mutableStateOf<List<String>?>(null) }
    fun perform(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { error = "插件操作失败：${failure.javaClass.simpleName}" }
            finally { busy = false }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) perform {
            val archive = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 4 * 1024 * 1024) { "插件包不能超过 4 MiB" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("无法读取插件包")
            val manifest = PluginArchiveReader.read(archive).manifest
            withContext(Dispatchers.Main.immediate) { pendingArchive = archive; pendingManifest = manifest }
        }
    }
    pendingManifest?.let { manifest ->
        val previous = plugins[manifest.sourceId]?.manifest
        AlertDialog(onDismissRequest = { pendingManifest = null; pendingArchive = null },
            title = { Text(if (previous == null) "安装 ${manifest.name}" else "更新 ${manifest.name}") },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text("${manifest.sourceId.value}\n${previous?.version?.let { "$it → " }.orEmpty()}${manifest.version}")
                Text("作者：${manifest.author ?: "未声明"}\n来源：${manifest.homepage ?: "本地导入，发布者身份未验证"}")
                if (previous != null && com.lladlam.melox.core.plugin.PluginPermissions.expands(previous, manifest)) {
                    Text("权限发生变化，请核对后确认。旧账号不会自动授权新凭据策略。", color = MaterialTheme.colorScheme.error)
                    Text("原权限\n${com.lladlam.melox.core.plugin.PluginPermissions.describe(previous)}")
                }
                Text("本次权限\n${com.lladlam.melox.core.plugin.PluginPermissions.describe(manifest)}")
            } },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                val archive = pendingArchive ?: return@TextButton
                pendingManifest = null; pendingArchive = null
                perform {
                    if (previous == null) manager.install(archive) else manager.update(manifest.sourceId, archive, approvedManifest = manifest)
                }
            }) { Text(if (previous == null) "确认安装" else "确认更新") } },
            dismissButton = { TextButton(onClick = { pendingManifest = null; pendingArchive = null }) { Text("取消") } })
    }
    pendingRemoval?.let { id ->
        AlertDialog(onDismissRequest = { pendingRemoval = null }, title = { Text("卸载插件？") },
            text = { Text("${id.value}\n将停用并删除插件包，保留收藏、历史与账号数据。") },
            confirmButton = { TextButton(onClick = { pendingRemoval = null; perform { manager.uninstall(id) } }) { Text("卸载") } },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("取消") } })
    }
    diagnostics?.let { lines ->
        AlertDialog(onDismissRequest = { diagnostics = null }, title = { Text("插件诊断") },
            text = { Text(lines.joinToString("\n"), Modifier.heightIn(max = 420.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) },
            confirmButton = { TextButton(onClick = { diagnostics = null }) { Text("关闭") } })
    }
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("音乐源插件（开发中）", style = MaterialTheme.typography.titleLarge)
        Text("插件负责音乐源 API 适配。可按声明提供搜索、目录、音乐库、收藏、下载和 API Token 认证；当前实现尚未通过设备端集成验证。")
        Button(onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !busy) {
            Text("导入 / 更新插件包")
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        plugins.values.sortedBy { it.manifest.sourceId.value }.forEach { plugin ->
            val id = plugin.manifest.sourceId
            HorizontalDivider()
            Text("${plugin.manifest.name} · ${plugin.manifest.version}", style = MaterialTheme.typography.titleMedium)
            Text("${id.value} · ${plugin.status}")
            Text("作者：${plugin.manifest.author ?: "未声明"} · 来源：${plugin.manifest.homepage ?: "本地导入"}")
            Text(com.lladlam.melox.core.plugin.PluginPermissions.describe(plugin.manifest), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = plugin.status == PluginManager.Status.Enabled, onClick = {
                com.lladlam.melox.core.music.provider.MusicProviderSelectionStore.setSelectedSourceId(context, id)
            }) { Text("设为主音乐源") }
            plugin.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { diagnostics = manager.diagnostics(id) }) { Text("查看诊断（不包含凭据与脚本日志正文）") }
            PluginAccounts(plugin.manifest, manager)
            if (com.lladlam.melox.core.plugin.PluginCapability.Search in plugin.manifest.capabilities) {
                var aggregate by remember(plugin.manifest.sourceId) { mutableStateOf(
                    plugin.manifest.sourceId in com.lladlam.melox.core.music.provider.MusicProviderSelectionStore.unifiedSourceIds(context)) }
                Row {
                    Checkbox(checked = aggregate, onCheckedChange = {
                        com.lladlam.melox.core.music.provider.MusicProviderSelectionStore.setUnifiedPluginEnabled(context, plugin.manifest.sourceId, it)
                        aggregate = it
                    })
                    Text("加入跨来源搜索/推荐（还需启用统一搜索）")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = {
                    perform { if (plugin.status == PluginManager.Status.Enabled) manager.disable(id) else manager.enable(id) }
                }) { Text(if (plugin.status == PluginManager.Status.Enabled) "禁用" else "启用") }
                TextButton(enabled = !busy, onClick = { pendingRemoval = id }) { Text("卸载") }
            }
        }
    }
}
