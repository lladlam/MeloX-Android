package com.lladlam.melox.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lladlam.melox.core.plugin.PluginAccountVault
import com.lladlam.melox.core.plugin.PluginErrors
import com.lladlam.melox.core.plugin.PluginHttpBroker
import com.lladlam.melox.core.plugin.PluginManifest
import com.lladlam.melox.core.plugin.PluginManager
import com.lladlam.melox.core.plugin.PluginOAuth
import com.lladlam.melox.core.plugin.PluginStorage
import com.lladlam.melox.core.plugin.PluginMusicProvider
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXGlassTextField
import com.lladlam.melox.ui.glass.MeloXGlassToggle
import com.lladlam.melox.ui.glass.MeloXIosGroupedList
import com.lladlam.melox.ui.glass.MeloXIosListRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PluginAccounts(manifest: PluginManifest, manager: PluginManager) {
    key(manifest.sourceId, manifest.authentication) { PluginAccountContent(manifest, manager) }
}

@Composable
private fun PluginAccountContent(manifest: PluginManifest, manager: PluginManager) {
    if (manifest.authentication?.type !in setOf("apiToken", "oauth2Pkce")) return
    val authentication = requireNotNull(manifest.authentication)
    val context = LocalContext.current
    val vault = remember(manifest.sourceId) { PluginAccountVault(context, manifest.sourceId) }
    val scope = rememberCoroutineScope()
    val registry = remember(context) { com.lladlam.melox.core.music.provider.MeloXMusicProviders.create(context) }
    val generation by registry.changes.collectAsState()
    val oauthStatus by PluginOAuth.status.collectAsState()
    var accounts by remember { mutableStateOf(emptyList<PluginAccountVault.Account>()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var label by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var deleting by remember(manifest.sourceId) { mutableStateOf<PluginAccountVault.Account?>(null) }
    var clearStorage by remember(manifest.sourceId) { mutableStateOf(false) }

    fun action(block: () -> Unit) {
        if (busy) return
        busy = true
        error = null
        status = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    manager.changeAccount(manifest.sourceId, manifest, block)
                }
                revision++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = PluginErrors.describe(failure)
                revision++
            } finally {
                busy = false
            }
        }
    }

    fun authorize(replace: Boolean) {
        if (busy) return
        busy = true
        error = null
        status = null
        scope.launch {
            try {
                if (replace) {
                    val previous = withContext(Dispatchers.IO) { vault.lease() ?: error("LOGIN_REQUIRED") }
                    PluginOAuth.beginReplacement(context, manifest, previous)
                } else {
                    PluginOAuth.begin(context, manifest, label)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = PluginErrors.describe(failure)
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(manifest.sourceId, revision, oauthStatus[manifest.sourceId], generation) {
        try {
            val (loadedAccounts, activeAccount) = withContext(Dispatchers.IO) {
                vault.accounts() to vault.selected()
            }
            accounts = loadedAccounts
            selected = activeAccount
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "无法解密账号，原数据已保留：${failure.javaClass.simpleName}"
        }
    }

    deleting?.let { account ->
        MeloXGlassDialog(
            visible = true,
            onDismiss = { if (!busy) deleting = null },
        ) {
            Text("删除插件账号", style = MaterialTheme.typography.titleLarge)
            Text(
                "删除 ${account.displayName} 的本地凭据。收藏、历史和下载保留。",
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
            )
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("同时清除此账号的插件普通存储", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall)
                MeloXGlassToggle(
                    checked = clearStorage,
                    onCheckedChange = { clearStorage = it },
                    enabled = !busy,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MeloXGlassButton(
                    onClick = { deleting = null },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.Plain,
                ) { Text("取消") }
                MeloXGlassButton(
                    onClick = {
                        val removeStorage = clearStorage
                        deleting = null
                        action {
                            vault.remove(account.id)
                            if (removeStorage) PluginStorage.clearAccount(context, manifest.sourceId, account.id)
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.Destructive,
                ) { Text("删除账号") }
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    PluginSectionLabel("插件账号")
    MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
        MeloXIosListRow(
            title = "当前账号",
            subtitle = selected?.let { id ->
                accounts.firstOrNull { it.id == id }?.displayName ?: id
            } ?: "未选择账号",
            detail = if (selected == null) "未选择" else "已选择",
            showTopSeparator = false,
        )
        MeloXIosListRow(
            title = "凭据范围",
            subtitle = authentication.domains.joinToString(),
            subtitleMaxLines = 4,
            showTopSeparator = true,
        )
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MeloXGlassTextField(
                label,
                { label = it },
                Modifier.fillMaxWidth(),
                placeholder = { Text("账号名称") },
                enabled = !busy,
            )
            if (authentication.type == "apiToken") {
                MeloXGlassTextField(
                    token,
                    { token = it },
                    Modifier.fillMaxWidth(),
                    placeholder = { Text("API Token") },
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                )
                MeloXGlassButton(
                    onClick = {
                        val name = label
                        val credential = token
                        token = ""
                        action {
                            vault.importTokenAndActivate(name, credential, requireNotNull(manifest.authentication))
                        }
                    },
                    enabled = !busy && label.isNotBlank() && token.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    style = MeloXGlassButtonStyle.BorderedProminent,
                ) { Text("导入并使用凭据") }
            } else {
                Text(
                    "通过系统浏览器进行 OAuth2 / PKCE 授权，回调：${PluginOAuth.REDIRECT_URI}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                )
                MeloXGlassButton(
                    onClick = { authorize(replace = false) },
                    enabled = !busy && label.isNotBlank() && registry[manifest.sourceId] != null,
                    modifier = Modifier.fillMaxWidth(),
                    style = MeloXGlassButtonStyle.BorderedProminent,
                ) { Text("浏览器授权登录（新账号）") }
                MeloXGlassButton(
                    onClick = { authorize(replace = true) },
                    enabled = !busy && selected != null && registry[manifest.sourceId] != null,
                    modifier = Modifier.fillMaxWidth(),
                    style = MeloXGlassButtonStyle.Plain,
                ) { Text("重新授权当前账号（保留账号数据）") }
                Text(
                    "重新授权会替换当前本地账号的凭据，保留其插件存储；请在浏览器选择对应账号。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MeloXGlassButton(
                        onClick = {
                            action {
                                val lease = vault.lease() ?: error("LOGIN_REQUIRED")
                                PluginHttpBroker(manifest, lease) { vault.sessionStamp() == lease.selectionStamp }.use { broker ->
                                    vault.replaceTokens(lease, PluginOAuth.refresh(manifest, lease, broker))
                                }
                            }
                        },
                        enabled = !busy && selected != null,
                        modifier = Modifier.weight(1f),
                        style = MeloXGlassButtonStyle.Plain,
                    ) { Text("刷新当前会话") }
                    MeloXGlassButton(
                        onClick = { PluginOAuth.cancel(manifest.sourceId) },
                        modifier = Modifier.weight(1f),
                        style = MeloXGlassButtonStyle.Plain,
                    ) { Text("取消登录") }
                }
                oauthStatus[manifest.sourceId]?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                }
            }
        }
        accounts.forEach { account ->
            MeloXIosListRow(
                title = account.displayName,
                subtitle = if (selected == account.id) "当前账号" else null,
                detail = if (selected == account.id) "已选择" else "切换",
                onClick = if (busy) null else ({ action { vault.activate(account.id) } }),
                trailing = {
                    MeloXGlassButton(
                        onClick = { clearStorage = false; deleting = account },
                        enabled = !busy,
                        style = MeloXGlassButtonStyle.Plain,
                    ) { Text("删除") }
                },
                showTopSeparator = true,
            )
        }
        if (accounts.isEmpty()) {
            MeloXIosListRow(
                title = "暂无已保存账号",
                subtitle = "导入凭据或完成浏览器授权后会显示在这里",
                showTopSeparator = true,
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MeloXGlassButton(
                onClick = {
                    PluginOAuth.cancel(manifest.sourceId)
                    action { vault.activate(null) }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                style = MeloXGlassButtonStyle.Plain,
            ) { Text("注销当前账号（保留凭据）") }
            MeloXGlassButton(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val provider = com.lladlam.melox.core.music.provider.MeloXMusicProviders
                                .create(context)[manifest.sourceId] as? PluginMusicProvider
                            status = when (val state = provider?.authenticationStatus()) {
                                com.lladlam.melox.core.music.model.MusicAuthenticationState.NotRequired ->
                                    "此插件无需登录"
                                com.lladlam.melox.core.music.model.MusicAuthenticationState.SignedOut ->
                                    "尚未登录，请选择账号或授权登录"
                                is com.lladlam.melox.core.music.model.MusicAuthenticationState.SignedIn ->
                                    "已登录：${state.account.displayName}"
                                is com.lladlam.melox.core.music.model.MusicAuthenticationState.Expired ->
                                    "授权已过期，请重新授权当前账号"
                                null -> "音源不可用"
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = PluginErrors.describe(failure)
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && manager.plugins.value[manifest.sourceId]?.status == PluginManager.Status.Enabled,
                modifier = Modifier.fillMaxWidth(),
                style = MeloXGlassButtonStyle.Bordered,
            ) { Text("检查会话状态") }
            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
