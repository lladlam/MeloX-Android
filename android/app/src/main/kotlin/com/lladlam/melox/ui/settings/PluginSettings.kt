package com.lladlam.melox.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lladlam.melox.core.music.model.SourceId
import com.lladlam.melox.core.music.provider.MusicProviderSelectionStore
import com.lladlam.melox.core.plugin.MeloXPlugins
import com.lladlam.melox.core.plugin.PluginArchiveReader
import com.lladlam.melox.core.plugin.PluginCapability
import com.lladlam.melox.core.plugin.PluginErrors
import com.lladlam.melox.core.plugin.PluginManager
import com.lladlam.melox.core.plugin.PluginManifest
import com.lladlam.melox.core.plugin.PluginPermissions
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXGlassToggle
import com.lladlam.melox.ui.glass.MeloXIosGroupedList
import com.lladlam.melox.ui.glass.MeloXIosListRow
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
    val selectionRevision by MusicProviderSelectionStore.changes.collectAsState()
    val selectedSource = remember(selectionRevision) { MusicProviderSelectionStore.selectedSourceId(context) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingArchive by remember { mutableStateOf<ByteArray?>(null) }
    var pendingManifest by remember { mutableStateOf<PluginManifest?>(null) }
    var pendingPackage by remember { mutableStateOf<PluginArchiveReader.Package?>(null) }
    var publisherChangeApproved by remember { mutableStateOf(false) }
    var rememberedPublisher by remember { mutableStateOf<String?>(null) }
    var cleanPrivateData by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<SourceId?>(null) }
    var diagnostics by remember { mutableStateOf<List<String>?>(null) }
    var diagnosticSource by remember { mutableStateOf<SourceId?>(null) }
    var selectedPluginId by remember { mutableStateOf<SourceId?>(null) }

    fun perform(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = PluginErrors.describe(failure)
            } finally {
                busy = false
            }
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
            withContext(Dispatchers.Main.immediate) {
                pendingArchive = archive
                pendingManifest = parsed.manifest
                pendingPackage = parsed
                publisherChangeApproved = false
                rememberedPublisher = remembered
            }
        }
    }

    pendingManifest?.let { manifest ->
        val previous = plugins[manifest.sourceId]?.manifest
        val previousPublisher = plugins[manifest.sourceId]?.let { it.publisher?.fingerprint ?: "unsigned" }
            ?: rememberedPublisher
        val newPublisher = pendingPackage?.publisher?.fingerprint ?: "unsigned"
        val changedPublisher = previousPublisher != null && previousPublisher != newPublisher
        MeloXGlassDialog(
            visible = true,
            onDismiss = {
                if (!busy) {
                    pendingManifest = null
                    pendingArchive = null
                    pendingPackage = null
                }
            },
            maxHeightFraction = .92f,
        ) {
            Text(
                if (previous == null) "安装 ${manifest.name}" else "更新 ${manifest.name}",
                style = MaterialTheme.typography.titleLarge,
            )
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${manifest.sourceId.value}\n${previous?.version?.let { "$it → " }.orEmpty()}${manifest.version}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "作者：${manifest.author ?: "未声明"}\n来源：${manifest.homepage ?: "本地导入，发布者身份未验证"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "包 SHA-256：${pendingPackage?.archiveSha256}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                Text(
                    if (newPublisher != "unsigned") {
                        "已验证包签名，公钥指纹：$newPublisher\n首次信任请通过独立渠道核对指纹；签名不代表插件安全。"
                    } else {
                        "无签名社区插件：无法验证发布者身份，仅从可信渠道安装。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (newPublisher == "unsigned") MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (changedPublisher) {
                    Text(
                        "发布者密钥变化\n原：$previousPublisher\n新：$newPublisher",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MeloXGlassToggle(
                            checked = publisherChangeApproved,
                            onCheckedChange = { publisherChangeApproved = it },
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("我已核对并授权发布者变化", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (previous != null && PluginPermissions.expands(previous, manifest)) {
                    Text(
                        "权限发生变化，请核对后确认。旧账号不会自动授权新凭据策略。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "原权限\n${PluginPermissions.describe(previous)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("本次权限", style = MaterialTheme.typography.bodySmall)
                Text(PluginPermissions.describe(manifest), style = MaterialTheme.typography.bodySmall)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MeloXGlassButton(
                    onClick = {
                        pendingManifest = null
                        pendingArchive = null
                        pendingPackage = null
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.Plain,
                ) { Text("取消") }
                MeloXGlassButton(
                    onClick = {
                        val archive = pendingArchive
                        if (archive != null) {
                            val approvedHash = if (changedPublisher && publisherChangeApproved) {
                                pendingPackage?.archiveSha256
                            } else null
                            pendingManifest = null
                            pendingArchive = null
                            pendingPackage = null
                            perform {
                                if (previous == null) manager.install(archive, approvedHash)
                                else manager.update(manifest.sourceId, archive, approvedManifest = manifest,
                                    approvedPublisherArchive = approvedHash)
                            }
                        }
                    },
                    enabled = !busy && (!changedPublisher || publisherChangeApproved),
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.BorderedProminent,
                ) { Text(if (previous == null) "确认安装" else "确认更新") }
            }
        }
    }

    pendingRemoval?.let { id ->
        MeloXGlassDialog(
            visible = true,
            onDismiss = { if (!busy) pendingRemoval = null },
        ) {
            Text("卸载插件？", style = MaterialTheme.typography.titleLarge)
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(id.value, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "将停用并删除插件包。收藏、历史、下载与发布者信任记录保留；插件媒体和图片没有共享缓存。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "可清除新版中该插件的全部普通存储，以及旧版当前可识别账号的存储。旧版已删除账号留下的无归属哈希文件无法可靠识别，会保留以免误删其他来源。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("同时清除插件凭据与普通存储", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    MeloXGlassToggle(checked = cleanPrivateData, onCheckedChange = { cleanPrivateData = it })
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MeloXGlassButton(
                    onClick = { pendingRemoval = null },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.Plain,
                ) { Text("取消") }
                MeloXGlassButton(
                    onClick = {
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
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.Destructive,
                ) { Text("卸载") }
            }
        }
    }

    diagnostics?.let { lines ->
        MeloXGlassDialog(
            visible = true,
            onDismiss = { diagnostics = null },
            maxHeightFraction = .85f,
        ) {
            Text("插件诊断", style = MaterialTheme.typography.titleLarge)
            SelectionContainer(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 10.dp),
            ) {
                Text(
                    lines.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MeloXGlassButton(
                    onClick = {
                        diagnosticSource?.let { source ->
                            perform {
                                manager.clearDiagnostics(source)
                                val refreshed = manager.diagnostics(source)
                                withContext(Dispatchers.Main.immediate) { diagnostics = refreshed }
                            }
                        }
                    },
                    enabled = !busy && diagnosticSource != null,
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.Plain,
                ) { Text("清除记录") }
                MeloXGlassButton(
                    onClick = { diagnostics = null },
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.Bordered,
                ) { Text("关闭") }
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            MeloXIosListRow(
                title = "导入 / 更新插件包",
                detail = if (busy) "处理中…" else "选择文件",
                onClick = if (busy) null else ({ importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }),
                showTopSeparator = false,
            )
            if (plugins.isEmpty()) {
                MeloXIosListRow(
                    title = "还没有安装插件",
                    showTopSeparator = true,
                )
            } else {
                plugins.values.sortedBy { it.manifest.sourceId.value }.forEach { plugin ->
                    MeloXIosListRow(
                        title = plugin.manifest.name,
                        onClick = { selectedPluginId = plugin.manifest.sourceId },
                        showTopSeparator = true,
                    )
                }
            }
        }
        if (busy) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        error?.let {
            Text(
                it,
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        recoveryError?.let { message ->
            Spacer(Modifier.height(12.dp))
            MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
                MeloXIosListRow(
                    title = "启用状态恢复失败",
                    showTopSeparator = false,
                )
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(message, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                    MeloXGlassButton(
                        onClick = { perform { manager.resetEnabledState() } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        style = MeloXGlassButtonStyle.Bordered,
                    ) { Text("重置启用状态（保留插件包和数据）") }
                }
            }
        }
        damaged.forEach { item ->
            Spacer(Modifier.height(12.dp))
            MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
                MeloXIosListRow(
                    title = if (item.storageKey?.endsWith(".transaction") == true) {
                        "插件更新恢复失败"
                    } else "已隔离损坏包",
                    showTopSeparator = false,
                )
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "${item.storageKey}\n${item.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (item.storageKey?.endsWith(".transaction") != true) {
                        MeloXGlassButton(
                            onClick = {
                                item.storageKey?.let { key -> perform { manager.dismissDamaged(key) } }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            style = MeloXGlassButtonStyle.Plain,
                        ) { Text("删除隔离副本") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    val selectedPlugin = selectedPluginId?.let { plugins[it] }
    if (selectedPlugin != null) {
        PluginDetailDialog(
            plugin = selectedPlugin,
            manager = manager,
            selectedSource = selectedSource,
            busy = busy,
            onDismiss = { selectedPluginId = null },
            onSetMainSource = {
                MusicProviderSelectionStore.setSelectedSourceId(context, selectedPlugin.manifest.sourceId)
            },
            onToggleEnabled = {
                perform {
                    if (selectedPlugin.status == PluginManager.Status.Enabled) {
                        manager.disable(selectedPlugin.manifest.sourceId)
                    } else {
                        manager.enable(selectedPlugin.manifest.sourceId)
                    }
                }
            },
            onDiagnostics = {
                perform {
                    val lines = manager.diagnostics(selectedPlugin.manifest.sourceId)
                    withContext(Dispatchers.Main.immediate) {
                        diagnosticSource = selectedPlugin.manifest.sourceId
                        diagnostics = lines
                    }
                }
            },
            onUninstall = {
                selectedPluginId = null
                cleanPrivateData = false
                pendingRemoval = selectedPlugin.manifest.sourceId
            },
        )
    }
}

@Composable
private fun PluginDetailDialog(
    plugin: PluginManager.State,
    manager: PluginManager,
    selectedSource: SourceId,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSetMainSource: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDiagnostics: () -> Unit,
    onUninstall: () -> Unit,
) {
    val context = LocalContext.current
    val manifest = plugin.manifest
    val statusText = when (plugin.status) {
        PluginManager.Status.Enabled -> "已启用"
        PluginManager.Status.Disabled -> "已禁用"
        PluginManager.Status.Failed -> "启用失败"
    }
    var aggregate by remember(manifest.sourceId) {
        mutableStateOf(manifest.sourceId in MusicProviderSelectionStore.unifiedSourceIds(context))
    }
    MeloXGlassDialog(
        visible = true,
        onDismiss = { if (!busy) onDismiss() },
        maxHeightFraction = .92f,
    ) {
        Text(manifest.name, style = MaterialTheme.typography.titleLarge)
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            MeloXIosListRow(
                title = "状态",
                detail = statusText,
                showTopSeparator = false,
            )
            MeloXIosListRow(
                title = "版本",
                detail = manifest.version,
                showTopSeparator = true,
            )
            MeloXIosListRow(
                title = "用途",
                detail = if (manifest.isMusicSource) "音乐源" else "第三方回退",
                showTopSeparator = true,
            )
            MeloXIosListRow(
                title = "主音乐源",
                detail = when {
                    !manifest.isMusicSource -> "第三方回退"
                    selectedSource == manifest.sourceId -> "当前"
                    else -> "设为"
                },
                onClick = if (manifest.isMusicSource && !busy) onSetMainSource else null,
                showTopSeparator = true,
            )
            if (PluginCapability.Search in manifest.capabilities) {
                MeloXIosListRow(
                    title = "跨来源搜索",
                    trailing = {
                        MeloXGlassToggle(
                            checked = aggregate,
                            onCheckedChange = { enabled ->
                                MusicProviderSelectionStore.setUnifiedPluginEnabled(context, manifest.sourceId, enabled)
                                aggregate = enabled
                            },
                            enabled = !busy,
                        )
                    },
                    showTopSeparator = true,
                )
            }
            plugin.error?.let { error ->
                MeloXIosListRow(
                    title = "运行状态",
                    detail = PluginErrors.message(error),
                    showTopSeparator = true,
                )
            }
            PluginDetailSection("插件 ID", manifest.sourceId.value)
            PluginDetailSection("作者", manifest.author ?: "未声明")
            PluginDetailSection("来源", manifest.homepage ?: "本地导入")
            PluginDetailSection("发布者", if (plugin.publisher != null) "已签名" else "未签名")
            plugin.publisher?.let { publisher ->
                PluginDetailSection("发布者指纹", publisher.fingerprint)
            }
            PluginDetailSection("包完整性", if (plugin.integrityChecked) "已验证" else "未验证")
            PluginDetailSection("SHA-256", plugin.archiveSha256, monospace = true)
            PluginDetailSection("权限", PluginPermissions.describe(manifest))
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MeloXGlassButton(
                        onClick = onDiagnostics,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        style = MeloXGlassButtonStyle.Plain,
                    ) { Text("诊断") }
                    MeloXGlassButton(
                        onClick = onToggleEnabled,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        style = MeloXGlassButtonStyle.Bordered,
                    ) { Text(if (plugin.status == PluginManager.Status.Enabled) "禁用" else "启用") }
                }
                MeloXGlassButton(
                    onClick = onUninstall,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    style = MeloXGlassButtonStyle.Destructive,
                ) { Text("卸载插件") }
            }
            PluginAccounts(manifest, manager)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            MeloXGlassButton(
                onClick = onDismiss,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                style = MeloXGlassButtonStyle.Bordered,
            ) { Text("关闭") }
        }
    }
}

@Composable
private fun PluginDetailSection(
    title: String,
    value: String,
    monospace: Boolean = false,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
