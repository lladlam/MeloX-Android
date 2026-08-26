package com.lladlam.melox.ui.provider

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.provider.MusicProviderSelectionStore
import com.lladlam.melox.core.music.provider.ProviderAccountManager
import com.lladlam.melox.ui.account.KugouLoginScreen
import com.lladlam.melox.ui.account.KuwoLoginScreen
import com.lladlam.melox.ui.account.QQMusicLoginScreen
import com.lladlam.melox.ui.account.AppleMusicLoginScreen
import com.lladlam.melox.ui.account.BilibiliLoginScreen
import com.lladlam.melox.ui.account.SpotifyLoginScreen
import com.lladlam.melox.ui.glass.meloXContentSurface
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassSheet
import com.lladlam.melox.ui.settings.SettingsScreen

private enum class ProviderAccountAction {
    Logout,
    SwitchAccount,
}

private data class PendingProviderAccountAction(
    val source: MusicSource,
    val action: ProviderAccountAction,
)

/**
 * Music services are a data/account concern, not a settings-presentation concern.
 *
 * The canonical MeloX SettingsScreen is always rendered regardless of the selected
 * provider. Switching NetEase/QQ/Kugou therefore never swaps out playback, lyrics,
 * appearance, tab-layout or general settings. This wrapper only owns the small
 * service/account control surface layered above the canonical settings page.
 */
@Composable
fun ProviderSettingsHub(
    currentSource: MusicSource,
    onSourceSelected: (MusicSource) -> Unit,
    neteaseSession: NeteaseSessionStore,
    onNeteaseLogin: () -> Unit,
    onOpenServices: () -> Unit,
    onOpenMessages: () -> Unit,
    initialRouteRequest: String? = null,
    onInitialRouteConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val accountManager = remember(neteaseSession) {
        ProviderAccountManager(context, neteaseSessionStore = neteaseSession)
    }

    var showServiceDialog by remember { mutableStateOf(false) }
    var showQQLogin by remember(currentSource) { mutableStateOf(false) }
    var showKugouLogin by remember(currentSource) { mutableStateOf(false) }
    var showKuwoLogin by remember(currentSource) { mutableStateOf(false) }
    var showAppleMusicLogin by remember(currentSource) { mutableStateOf(false) }
    var showBilibiliLogin by remember(currentSource) { mutableStateOf(false) }
    var showSpotifyLogin by remember(currentSource) { mutableStateOf(false) }
    var loginRevision by remember(currentSource) { mutableStateOf(0) }
    var pendingAccountAction by remember { mutableStateOf<PendingProviderAccountAction?>(null) }

    var unifiedEnabled by remember {
        mutableStateOf(MusicProviderSelectionStore.unifiedEnabled(context))
    }
    var unifiedSources by remember {
        mutableStateOf(MusicProviderSelectionStore.unifiedSources(context))
    }

    // Automatic playback source fallback stays disabled until the resolver has a
    // rights-aware implementation. This is independent from the canonical settings.
    LaunchedEffect(Unit) {
        MusicProviderSelectionStore.setAutomaticFallbackEnabled(context, false)
    }

    if (showQQLogin && currentSource == MusicSource.QQMusic) {
        QQMusicLoginScreen(
            onDismiss = { showQQLogin = false },
            onLoggedIn = {
                showQQLogin = false
                loginRevision += 1
            },
        )
        return
    }
    if (showKugouLogin && currentSource == MusicSource.Kugou) {
        KugouLoginScreen(
            onDismiss = { showKugouLogin = false },
            onLoggedIn = {
                showKugouLogin = false
                loginRevision += 1
            },
        )
        return
    }
    if (showKuwoLogin && currentSource == MusicSource.Kuwo) {
        KuwoLoginScreen(
            onDismiss = { showKuwoLogin = false },
            onLoggedIn = {
                showKuwoLogin = false
                loginRevision += 1
            },
        )
        return
    }
    if (showAppleMusicLogin && currentSource == MusicSource.AppleMusic) {
        AppleMusicLoginScreen(
            onDismiss = { showAppleMusicLogin = false },
            onLoggedIn = { showAppleMusicLogin = false; loginRevision++ },
        )
        return
    }
    if (showBilibiliLogin && currentSource == MusicSource.Bilibili) {
        BilibiliLoginScreen(
            onDismiss = { showBilibiliLogin = false },
            onLoggedIn = { showBilibiliLogin = false; loginRevision++ },
        )
        return
    }
    if (showSpotifyLogin && currentSource == MusicSource.Spotify) {
        SpotifyLoginScreen(
            onDismiss = { showSpotifyLogin = false },
            onLoggedIn = { showSpotifyLogin = false; loginRevision++ },
        )
        return
    }

    // Music-service navigation belongs to the account row.  Keeping it there
    // avoids a second floating control competing with the canonical Settings UI.
    SettingsScreen(
        session = neteaseSession,
        source = currentSource,
        onLogin = onNeteaseLogin,
        onOpenServices = onOpenServices,
        onOpenMessages = onOpenMessages,
        initialRouteRequest = initialRouteRequest,
        onInitialRouteConsumed = onInitialRouteConsumed,
    )

    if (showServiceDialog) {
        val currentAccount = remember(loginRevision, currentSource, showServiceDialog) {
            accountManager.state(currentSource)
        }
        MeloXGlassSheet(
            visible = true,
            onDismiss = { showServiceDialog = false },
            modifier = Modifier.fillMaxHeight(0.88f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("音乐服务", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                    Text(
                        "只切换数据源。MeloX 的播放、歌词、外观、动画、背景和页面设置共用同一份配置。",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    )
                    Spacer(Modifier.height(14.dp))

                    MusicProviderSelectionStore.visibleSources().forEach { source ->
                        ProviderSourceSelectionRow(
                            source = source,
                            selected = source == currentSource,
                            accountState = accountManager.state(source),
                            onClick = {
                                if (source != currentSource) onSourceSelected(source)
                                showServiceDialog = false
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "当前账号",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                    )
                    Spacer(Modifier.height(7.dp))

                    ProviderSimpleCard(
                        currentSource.displayName,
                        when {
                            currentAccount.loggedIn && !currentAccount.accountId.isNullOrBlank() ->
                                "已登录 · ${currentAccount.accountId}"
                            currentAccount.loggedIn -> "已登录"
                            else -> "未登录 · 点击登录"
                        },
                        onClick = if (currentAccount.loggedIn) null else {
                            {
                                showServiceDialog = false
                                when (currentSource) {
                                    MusicSource.Netease -> onNeteaseLogin()
                                    MusicSource.QQMusic -> showQQLogin = true
                                    MusicSource.Kugou -> showKugouLogin = true
                                    MusicSource.Kuwo -> showKuwoLogin = true
                                    MusicSource.AppleMusic -> showAppleMusicLogin = true
                                    MusicSource.Bilibili -> showBilibiliLogin = true
                                    MusicSource.Spotify -> showSpotifyLogin = true
                                }
                            }
                        },
                    )

                    if (currentAccount.loggedIn) {
                        Spacer(Modifier.height(8.dp))
                        ProviderSimpleCard(
                            "切换 / 重新登录账号",
                            when (currentSource) {
                                MusicSource.Netease -> "清除当前网易云登录态后重新登录"
                                MusicSource.QQMusic -> "只清除 QQ音乐登录态后重新打开登录页"
                                MusicSource.Kugou -> "保留 MID / GUID，只清除用户登录态后重新扫码"
                                MusicSource.Kuwo -> "只清除酷我音乐登录态后重新手机号登录"
                                MusicSource.AppleMusic -> "重新配置 Developer Token / Music User Token"
                                MusicSource.Bilibili -> "清除当前 Bilibili 登录态后重新登录"
                                MusicSource.Spotify -> "清除 OAuth token 后重新在浏览器授权"
                            },
                            onClick = {
                                showServiceDialog = false
                                pendingAccountAction = PendingProviderAccountAction(
                                    source = currentSource,
                                    action = ProviderAccountAction.SwitchAccount,
                                )
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        ProviderSimpleCard(
                            "退出 ${currentSource.displayName}",
                            "只清除这个音乐服务的本机登录态，不影响其他服务。",
                            onClick = {
                                showServiceDialog = false
                                pendingAccountAction = PendingProviderAccountAction(
                                    source = currentSource,
                                    action = ProviderAccountAction.Logout,
                                )
                            },
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        "跨平台搜索",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                    )
                    Spacer(Modifier.height(7.dp))
                    ProviderSettingToggle(
                        title = "跨平台音乐聚合",
                        subtitle = "默认关闭；开启后也只请求你明确勾选的平台",
                        checked = unifiedEnabled,
                        onCheckedChange = { enabled ->
                            unifiedEnabled = enabled
                            MusicProviderSelectionStore.setUnifiedEnabled(context, enabled)
                            unifiedSources = MusicProviderSelectionStore.unifiedSources(context)
                        },
                    )

                    if (unifiedEnabled) {
                        Spacer(Modifier.height(8.dp))
                        MusicProviderSelectionStore.visibleSources().forEach { source ->
                            val account = accountManager.state(source)
                            ProviderSettingToggle(
                                title = source.displayName,
                                subtitle = when {
                                    source == currentSource && account.loggedIn -> "当前音乐源 · 已登录"
                                    source == currentSource -> "当前音乐源 · 未登录"
                                    account.loggedIn -> "已登录 · 参与聚合搜索"
                                    else -> "未登录 · 仅在你主动勾选后请求"
                                },
                                checked = source in unifiedSources,
                                onCheckedChange = { enabled ->
                                    unifiedSources = MusicProviderSelectionStore.setUnifiedSourceEnabled(
                                        context = context,
                                        source = source,
                                        enabled = enabled,
                                    )
                                },
                            )
                            Spacer(Modifier.height(7.dp))
                        }
                    }
            }
            MeloXGlassButton(
                onClick = { showServiceDialog = false },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                style = MeloXGlassButtonStyle.BorderedProminent,
            ) { Text("完成") }
        }
    }

    pendingAccountAction?.let { pending ->
        val actionTitle = when (pending.action) {
            ProviderAccountAction.Logout -> "退出 ${pending.source.displayName}？"
            ProviderAccountAction.SwitchAccount -> "切换 ${pending.source.displayName} 账号？"
        }
        val actionBody = when (pending.action) {
            ProviderAccountAction.Logout -> "只会清除 MeloX 本机保存的该平台登录态，其他音乐服务不会受影响。"
            ProviderAccountAction.SwitchAccount -> "会先清除当前账号的本机登录态，然后重新打开该平台登录流程。"
        }
        MeloXGlassDialog(
            visible = true,
            onDismiss = { pendingAccountAction = null },
        ) {
            Text(actionTitle, style = MaterialTheme.typography.titleLarge)
            Text(
                actionBody,
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MeloXGlassButton(
                    onClick = { pendingAccountAction = null },
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.Plain,
                ) { Text("取消") }
                MeloXGlassButton(
                    onClick = {
                        when (pending.action) {
                            ProviderAccountAction.Logout -> accountManager.logout(pending.source)
                            ProviderAccountAction.SwitchAccount -> accountManager.prepareAccountSwitch(pending.source)
                        }
                        loginRevision += 1
                        pendingAccountAction = null
                        if (pending.action == ProviderAccountAction.SwitchAccount) {
                            when (pending.source) {
                                MusicSource.Netease -> onNeteaseLogin()
                                MusicSource.QQMusic -> showQQLogin = true
                                MusicSource.Kugou -> showKugouLogin = true
                                MusicSource.Kuwo -> showKuwoLogin = true
                                MusicSource.AppleMusic -> showAppleMusicLogin = true
                                MusicSource.Bilibili -> showBilibiliLogin = true
                                MusicSource.Spotify -> showSpotifyLogin = true
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    style = if (pending.action == ProviderAccountAction.Logout) {
                        MeloXGlassButtonStyle.Destructive
                    } else {
                        MeloXGlassButtonStyle.BorderedProminent
                    },
                ) { Text(if (pending.action == ProviderAccountAction.Logout) "退出" else "继续") }
            }
        }
    }
}

@Composable
private fun ProviderSourceSelectionRow(
    source: MusicSource,
    selected: Boolean,
    accountState: ProviderAccountManager.AccountState,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .meloXContentSurface(
                shape = RoundedCornerShape(20.dp),
                surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.045f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(source.displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    selected && accountState.loggedIn -> "当前音乐源 · 已登录"
                    selected -> "当前音乐源"
                    accountState.loggedIn -> "已登录"
                    else -> "未登录"
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
            )
        }
        Text(
            if (selected) "✓" else "",
            color = com.lladlam.melox.ui.glass.MeloXSystemColors.Red,
            fontSize = 19.sp,
        )
    }
}
