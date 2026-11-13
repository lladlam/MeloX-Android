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
import com.lladlam.melox.core.plugin.PluginErrors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings uses the same lifecycle owner and dynamic directory as search and playback. */
@Composable
internal fun PluginSettings() {
    val context = LocalContext.current
    val manager = remember(context) { MeloXPlugins.get(context) }
    val plugins by manager.plugins.collectAsState()
    val damaged by manager.damagedPackages.collectAsState()
    val recoveryError by manager.recoveryError.collectAsState()
    val selectionRevision by com.lladlam.melox.core.music.provider.MusicProviderSelectionStore.changes.collectAsState()
    val selectedSource = remember(selectionRevision) { com.lladlam.melox.core.music.provider.MusicProviderSelectionStore.selectedSourceId(context) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingArchive by remember { mutableStateOf<ByteArray?>(null) }
    var pendingManifest by remember { mutableStateOf<com.lladlam.melox.core.plugin.PluginManifest?>(null) }
    var pendingPackage by remember { mutableStateOf<PluginArchiveReader.Package?>(null) }
    var publisherChangeApproved by remember { mutableStateOf(false) }
    var rememberedPublisher by remember { mutableStateOf<String?>(null) }
    var cleanPrivateData by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<com.lladlam.melox.core.music.model.SourceId?>(null) }
    var diagnostics by remember { mutableStateOf<List<String>?>(null) }
    var diagnosticSource by remember { mutableStateOf<com.lladlam.melox.core.music.model.SourceId?>(null) }
    fun perform(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { error = PluginErrors.describe(failure) }
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
            val parsed = PluginArchiveReader.read(archive)
            val remembered = manager.publisherFingerprint(parsed.manifest.sourceId)
            withContext(Dispatchers.Main.immediate) { pendingArchive = archive; pendingManifest = parsed.manifest; pendingPackage = parsed; publisherChangeApproved = false; rememberedPublisher = remembered }
        }
    }
    pendingManifest?.let { manifest ->
        val previous = plugins[manifest.sourceId]?.manifest
        val previousPublisher = plugins[manifest.sourceId]?.let { it.publisher?.fingerprint ?: "unsigned" } ?: rememberedPublisher
        val newPublisher = pendingPackage?.publisher?.fingerprint ?: "unsigned"
        val changedPublisher = previousPublisher != null && previousPublisher != newPublisher
        AlertDialog(onDismissRequest = { pendingManifest = null; pendingArchive = null },
            title = { Text(if (previous == null) "安装 ${manifest.name}" else "更新 ${manifest.name}") },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text("${manifest.sourceId.value}\n${previous?.version?.let { "$it → " }.orEmpty()}${manifest.version}")
                Text("作者：${manifest.author ?: "未声明"}\n来源：${manifest.homepage ?: "本地导入，发布者身份未验证"}")
                Text("包 SHA-256：${pendingPackage?.archiveSha256}")
                Text(if (newPublisher != "unsigned") "已验证包签名，公钥指纹：$newPublisher\n首次信任请通过独立渠道核对指纹；签名不代表插件安全。"
                    else "无签名社区插件：无法验证发布者身份，仅从可信渠道安装。", color = MaterialTheme.colorScheme.error)
                if (changedPublisher) {
                    Text("发布者密钥变化\n原：${previousPublisher ?: "unsigned"}\n新：${newPublisher ?: "unsigned"}", color = MaterialTheme.colorScheme.error)
                    Row { Checkbox(checked = publisherChangeApproved, onCheckedChange = { publisherChangeApproved = it }); Text("我已核对并授权发布者变化") }
                }
                if (previous != null && com.lladlam.melox.core.plugin.PluginPermissions.expands(previous, manifest)) {
                    Text("权限发生变化，请核对后确认。旧账号不会自动授权新凭据策略。", color = MaterialTheme.colorScheme.error)
                    Text("原权限\n${com.lladlam.melox.core.plugin.PluginPermissions.describe(previous)}")
                }
                Text("本次权限\n${com.lladlam.melox.core.plugin.PluginPermissions.describe(manifest)}")
            } },
            confirmButton = { TextButton(enabled = !busy && (!changedPublisher || publisherChangeApproved), onClick = {
                val archive = pendingArchive ?: return@TextButton
                val approvedHash = if (changedPublisher && publisherChangeApproved) pendingPackage?.archiveSha256 else null
                pendingManifest = null; pendingArchive = null
                perform {
                    if (previous == null) manager.install(archive, approvedHash) else manager.update(manifest.sourceId, archive, approvedManifest = manifest, approvedPublisherArchive = approvedHash)
                }
            }) { Text(if (previous == null) "确认安装" else "确认更新") } },
            dismissButton = { TextButton(onClick = { pendingManifest = null; pendingArchive = null }) { Text("取消") } })
    }
    pendingRemoval?.let { id ->
        AlertDialog(onDismissRequest = { pendingRemoval = null }, title = { Text("卸载插件？") },
            text = { Column {
                Text("${id.value}\n将停用并删除插件包。收藏、历史、下载与发布者信任记录保留；插件媒体和图片没有共享缓存。")
                Text("可清除新版中该插件的全部普通存储，以及旧版当前可识别账号的存储。旧版已删除账号留下的无归属哈希文件无法可靠识别，会保留以免误删其他来源。", style = MaterialTheme.typography.bodySmall)
                Row { Checkbox(checked = cleanPrivateData, onCheckedChange = { cleanPrivateData = it }); Text("同时清除插件凭据与普通存储") }
            } },
            confirmButton = { TextButton(onClick = {
                val clean = cleanPrivateData
                pendingRemoval = null
                perform {
                    // Clear private data first: if it fails, the plugin remains
                    // installed and the user can retry from the same entry.
                    if (clean) {
                        val vault = com.lladlam.melox.core.plugin.PluginAccountVault(context, id)
                        val accounts = vault.accounts().map { it.id }
                        com.lladlam.melox.core.plugin.PluginStorage.clear(context, id, accounts)
                        vault.clear()
                    }
                    manager.uninstall(id)
                }
            }) { Text("卸载") } },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("取消") } })
    }
    diagnostics?.let { lines ->
        AlertDialog(onDismissRequest = { diagnostics = null }, title = { Text("插件诊断") },
            text = { Text(lines.joinToString("\n"), Modifier.heightIn(max = 420.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) },
            confirmButton = { TextButton(onClick = { diagnostics = null }) { Text("关闭") } },
            dismissButton = { TextButton(enabled = !busy && diagnosticSource != null, onClick = {
                diagnosticSource?.let { source -> perform {
                    manager.clearDiagnostics(source)
                    val lines = manager.diagnostics(source)
                    withContext(Dispatchers.Main.immediate) { diagnostics = lines }
                } }
            }) { Text("清除诊断记录") } })
    }
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("音乐源插件（开发中）", style = MaterialTheme.typography.titleLarge)
        Text("插件负责音乐源 API 适配。可按声明提供搜索、目录、音乐库、收藏、下载和宿主认证。RC 验收中，尚未完成全部应用和设备兼容验证。")
        Button(onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !busy) {
            Text("导入 / 更新插件包")
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        recoveryError?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
            TextButton(enabled = !busy, onClick = { perform { manager.resetEnabledState() } }) { Text("重置启用状态（保留插件包和数据）") }
        }
        damaged.forEach { item ->
            if (item.storageKey?.endsWith(".transaction") == true) {
                Text("插件更新恢复失败：${item.storageKey}\n${item.error}。恢复记录已保留，插件不会自动执行。", color = MaterialTheme.colorScheme.error)
            } else {
                Text("已隔离损坏包：${item.storageKey}\n${item.error}。重新导入可信原包可恢复，隔离副本保留。", color = MaterialTheme.colorScheme.error)
                TextButton(enabled = !busy, onClick = { item.storageKey?.let { key -> perform { manager.dismissDamaged(key) } } }) { Text("删除隔离副本") }
            }
        }
        plugins.values.sortedBy { it.manifest.sourceId.value }.forEach { plugin ->
            val id = plugin.manifest.sourceId
            HorizontalDivider()
            Text("${plugin.manifest.name} · ${plugin.manifest.version}", style = MaterialTheme.typography.titleMedium)
            Text("${id.value} · ${plugin.status}")
            Text("发布者公钥：${plugin.publisher?.fingerprint ?: "无签名（未验证发布者）"}\n完整性清单：${plugin.integrityChecked}\nSHA-256：${plugin.archiveSha256}", style = MaterialTheme.typography.bodySmall)
            Text("作者：${plugin.manifest.author ?: "未声明"} · 来源：${plugin.manifest.homepage ?: "本地导入"}")
            Text(com.lladlam.melox.core.plugin.PluginPermissions.describe(plugin.manifest), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = plugin.status == PluginManager.Status.Enabled, onClick = {
                com.lladlam.melox.core.music.provider.MusicProviderSelectionStore.setSelectedSourceId(context, id)
            }) { Text(if (selectedSource == id) "✓ 当前主音乐源" else "设为主音乐源") }
            plugin.error?.let { Text(PluginErrors.message(it), color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !busy, onClick = { perform {
                val lines = manager.diagnostics(id)
                withContext(Dispatchers.Main.immediate) { diagnosticSource = id; diagnostics = lines }
            } }) { Text("查看诊断（不包含凭据与脚本日志正文）") }
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
                TextButton(enabled = !busy, onClick = { cleanPrivateData = false; pendingRemoval = id }) { Text("卸载") }
            }
        }
    }
}
