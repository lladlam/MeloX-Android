package com.lladlam.melox.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lladlam.melox.core.plugin.*
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
        busy = true; error = null; status = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    manager.changeAccount(manifest.sourceId, manifest, block)
                }
                revision++
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = PluginErrors.describe(failure); revision++ }
            finally { busy = false }
        }
    }
    fun authorize(replace: Boolean) {
        if (busy) return
        busy = true; error = null; status = null
        scope.launch {
            try {
                if (replace) {
                    val previous = withContext(Dispatchers.IO) { vault.lease() ?: error("LOGIN_REQUIRED") }
                    PluginOAuth.beginReplacement(context, manifest, previous)
                } else PluginOAuth.begin(context, manifest, label)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = PluginErrors.describe(failure) }
            finally { busy = false }
        }
    }
    LaunchedEffect(manifest.sourceId, revision, oauthStatus[manifest.sourceId], generation) {
        try {
            val (loadedAccounts, activeAccount) = withContext(Dispatchers.IO) { vault.accounts() to vault.selected() }
            accounts = loadedAccounts; selected = activeAccount
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = "无法解密账号，原数据已保留：${failure.javaClass.simpleName}" }
    }
    deleting?.let { account ->
        AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("删除插件账号") }, text = {
            Column {
                Text("删除 ${account.displayName} 的本地凭据。收藏、历史和下载保留。")
                Row {
                    Checkbox(checked = clearStorage, onCheckedChange = { clearStorage = it }, enabled = !busy)
                    Text("同时清除此账号的插件普通存储")
                }
            }
        }, confirmButton = {
            TextButton(enabled = !busy, onClick = {
                val removeStorage = clearStorage
                deleting = null
                action {
                    vault.remove(account.id)
                    if (removeStorage) PluginStorage.clearAccount(context, manifest.sourceId, account.id)
                }
            }) { Text("删除账号") }
        }, dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text("取消") } })
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("插件独立账号", style = MaterialTheme.typography.titleSmall)
        Text(selected?.let { id -> "当前账号：${accounts.firstOrNull { it.id == id }?.displayName ?: id}（会话有效性需检查）" } ?: "未选择账号")
        Text("凭据只允许注入到：${authentication.domains.joinToString()}。不会直接传给插件 JS 或其他音源。")
        OutlinedTextField(label, { label = it }, label = { Text("账号名称") }, enabled = !busy)
        if (authentication.type == "apiToken") {
        OutlinedTextField(token, { token = it }, label = { Text("API Token") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), enabled = !busy)
        Button(enabled = !busy && label.isNotBlank() && token.isNotBlank(), onClick = {
            val name = label; val credential = token; token = ""
            action { vault.importTokenAndActivate(name, credential, requireNotNull(manifest.authentication)) }
        }) { Text("导入并使用凭据") }
        } else {
            Text("通过系统浏览器进行 OAuth2 / PKCE 授权，回调：${PluginOAuth.REDIRECT_URI}")
            Button(enabled = !busy && label.isNotBlank() && registry[manifest.sourceId] != null,
                onClick = { authorize(replace = false) }) { Text("浏览器授权登录（新账号）") }
            TextButton(enabled = !busy && selected != null && registry[manifest.sourceId] != null,
                onClick = { authorize(replace = true) }) { Text("重新授权当前账号（保留账号数据）") }
            Text("重新授权会替换当前本地账号的凭据，保留其插件存储；请在浏览器选择对应账号。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { PluginOAuth.cancel(manifest.sourceId) }) { Text("取消登录") }
            TextButton(enabled = !busy && selected != null, onClick = {
                action {
                    val lease = vault.lease() ?: error("LOGIN_REQUIRED")
                    PluginHttpBroker(manifest, lease) { vault.sessionStamp() == lease.selectionStamp }.use { broker ->
                        vault.replaceTokens(lease, PluginOAuth.refresh(manifest, lease, broker))
                    }
                }
            }) { Text("刷新当前会话") }
            oauthStatus[manifest.sourceId]?.let { Text(it) }
        }
        accounts.forEach { account ->
            Row(Modifier.fillMaxWidth()) {
                TextButton(enabled = !busy, onClick = { action { vault.activate(account.id) } }) {
                    Text(if (selected == account.id) "✓ ${account.displayName}" else account.displayName)
                }
                TextButton(enabled = !busy, onClick = { clearStorage = false; deleting = account }) { Text("删除账号") }
            }
        }
        TextButton(enabled = !busy, onClick = { PluginOAuth.cancel(manifest.sourceId); action { vault.activate(null) } }) { Text("注销当前账号（保留凭据）") }
        TextButton(enabled = !busy && manager.plugins.value[manifest.sourceId]?.status == PluginManager.Status.Enabled, onClick = {
            busy = true
            scope.launch {
                try {
                    val provider = com.lladlam.melox.core.music.provider.MeloXMusicProviders.create(context)[manifest.sourceId] as? PluginMusicProvider
                    status = when (val state = provider?.authenticationStatus()) {
                        com.lladlam.melox.core.music.model.MusicAuthenticationState.NotRequired -> "此插件无需登录"
                        com.lladlam.melox.core.music.model.MusicAuthenticationState.SignedOut -> "尚未登录，请选择账号或授权登录"
                        is com.lladlam.melox.core.music.model.MusicAuthenticationState.SignedIn -> "已登录：${state.account.displayName}"
                        is com.lladlam.melox.core.music.model.MusicAuthenticationState.Expired -> "授权已过期，请重新授权当前账号"
                        null -> "音源不可用"
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = PluginErrors.describe(failure) }
                finally { busy = false }
            }
        }) { Text("检查会话状态") }
        status?.let { Text(it) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
