package com.lladlam.melox.ui.provider

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.provider.MusicProviderSelectionStore
import com.lladlam.melox.core.music.provider.ProviderAccountManager
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.account.KugouLoginScreen
import com.lladlam.melox.ui.account.QQMusicLoginScreen
import com.lladlam.melox.ui.account.AppleMusicLoginScreen
import com.lladlam.melox.ui.account.BilibiliLoginScreen
import com.lladlam.melox.ui.account.SpotifyLoginScreen
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXGlassToggle
import com.lladlam.melox.ui.glass.MeloXIosGroupedList
import com.lladlam.melox.ui.glass.MeloXIosListRow
import com.lladlam.melox.ui.glass.MeloXIosTopBar
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXSystemColors

private enum class ServicesAccountAction { Logout, Switch }

@Composable
fun ProviderServicesScreen(
    currentSource: MusicSource,
    onSourceSelected: (MusicSource) -> Unit,
    neteaseSession: NeteaseSessionStore,
    onNeteaseLogin: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val accountManager = remember(neteaseSession) {
        ProviderAccountManager(context, neteaseSessionStore = neteaseSession)
    }
    var showQQLogin by remember(currentSource) { mutableStateOf(false) }
    var showKugouLogin by remember(currentSource) { mutableStateOf(false) }
    var showAppleMusicLogin by remember(currentSource) { mutableStateOf(false) }
    var showBilibiliLogin by remember(currentSource) { mutableStateOf(false) }
    var showSpotifyLogin by remember(currentSource) { mutableStateOf(false) }
    var loginRevision by remember(currentSource) { mutableStateOf(0) }
    var pendingAction by remember { mutableStateOf<Pair<MusicSource, ServicesAccountAction>?>(null) }
    var unifiedEnabled by remember { mutableStateOf(MusicProviderSelectionStore.unifiedEnabled(context)) }
    var unifiedSources by remember { mutableStateOf(MusicProviderSelectionStore.unifiedSources(context)) }

    BackHandler(onBack = onBack)

    if (showQQLogin && currentSource == MusicSource.QQMusic) {
        QQMusicLoginScreen(
            onDismiss = { showQQLogin = false },
            onLoggedIn = { showQQLogin = false; loginRevision++ },
        )
        return
    }
    if (showKugouLogin && currentSource == MusicSource.Kugou) {
        KugouLoginScreen(
            onDismiss = { showKugouLogin = false },
            onLoggedIn = { showKugouLogin = false; loginRevision++ },
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

    val currentAccount = remember(loginRevision, currentSource) {
        accountManager.state(currentSource)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = MeloXBottomContentClearance),
    ) {
        MeloXIosTopBar(
            title = "音乐服务",
            contentPadding = PaddingValues(horizontal = 0.dp),
            navigation = {
                Box(
                    Modifier
                        .size(44.dp)
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    MeloXSymbolIcon(MeloXSymbol.ChevronLeft, Modifier.size(28.dp), MaterialTheme.colorScheme.onBackground, iconSize = 24.sp)
                }
            },
        )
        Spacer(Modifier.size(26.dp))

        ServicesSectionLabel("音乐源")
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            MusicProviderSelectionStore.visibleSources().forEachIndexed { index, source ->
                val account = accountManager.state(source)
                MeloXIosListRow(
                    title = source.displayName,
                    subtitle = when {
                        source == currentSource && account.loggedIn -> "当前音乐源 · 已登录"
                        source == currentSource -> "当前音乐源 · 未登录"
                        account.loggedIn -> "已登录"
                        else -> "未登录"
                    },
                    leading = {
                        MeloXSymbolIcon(
                            MeloXSymbol.MusicNote,
                            Modifier.size(25.dp),
                            if (source == currentSource) MeloXSystemColors.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = .70f),
                        )
                    },
                    trailing = if (source == currentSource) {
                        { MeloXSymbolIcon(MeloXSymbol.Check, Modifier.size(21.dp), MeloXSystemColors.Red) }
                    } else null,
                    onClick = { if (source != currentSource) onSourceSelected(source) },
                    showTopSeparator = index > 0,
                )
            }
        }

        Spacer(Modifier.size(24.dp))
        ServicesSectionLabel("当前账号")
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            MeloXIosListRow(
                title = currentSource.displayName,
                subtitle = when {
                    currentAccount.loggedIn && !currentAccount.accountId.isNullOrBlank() -> "已登录 · ${currentAccount.accountId}"
                    currentAccount.loggedIn -> "已登录"
                    else -> "未登录 · 点击登录"
                },
                leading = { MeloXSymbolIcon(MeloXSymbol.Person, Modifier.size(25.dp), MeloXSystemColors.Red) },
                onClick = if (currentAccount.loggedIn) null else {
                    {
                        when (currentSource) {
                            MusicSource.Netease -> onNeteaseLogin()
                            MusicSource.QQMusic -> showQQLogin = true
                            MusicSource.Kugou -> showKugouLogin = true
                            MusicSource.AppleMusic -> showAppleMusicLogin = true
                            MusicSource.Bilibili -> showBilibiliLogin = true
                            MusicSource.Spotify -> showSpotifyLogin = true
                        }
                    }
                },
                showTopSeparator = false,
            )
            if (currentAccount.loggedIn) {
                MeloXIosListRow(
                    title = "切换 / 重新登录账号",
                    subtitle = "清除当前服务登录态后重新登录",
                    leading = { MeloXSymbolIcon(MeloXSymbol.Refresh, Modifier.size(24.dp), MeloXSystemColors.Red) },
                    onClick = { pendingAction = currentSource to ServicesAccountAction.Switch },
                )
                MeloXIosListRow(
                    title = "退出 ${currentSource.displayName}",
                    subtitle = "只清除这个服务的本机登录态",
                    leading = { MeloXSymbolIcon(MeloXSymbol.Xmark, Modifier.size(24.dp), MeloXSystemColors.Red) },
                    onClick = { pendingAction = currentSource to ServicesAccountAction.Logout },
                )
            }
        }

        Spacer(Modifier.size(24.dp))
        ServicesSectionLabel("跨平台搜索")
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            MeloXIosListRow(
                title = "跨平台音乐聚合",
                subtitle = "默认关闭；只请求你明确勾选的平台",
                leading = { MeloXSymbolIcon(MeloXSymbol.Apps, Modifier.size(24.dp), MeloXSystemColors.Red) },
                trailing = {
                    MeloXGlassToggle(
                        checked = unifiedEnabled,
                        onCheckedChange = {
                            unifiedEnabled = it
                            MusicProviderSelectionStore.setUnifiedEnabled(context, it)
                            unifiedSources = MusicProviderSelectionStore.unifiedSources(context)
                        },
                    )
                },
                showTopSeparator = false,
            )
            if (unifiedEnabled) {
                MusicProviderSelectionStore.visibleSources().forEach { source ->
                    val account = accountManager.state(source)
                    MeloXIosListRow(
                        title = source.displayName,
                        subtitle = if (account.loggedIn) "已登录 · 参与聚合搜索" else "未登录 · 不参与请求",
                        detail = if (source in unifiedSources) "已启用" else "",
                        leading = { Spacer(Modifier.width(25.dp)) },
                        onClick = {
                            unifiedSources = MusicProviderSelectionStore.setUnifiedSourceEnabled(
                                context, source, source !in unifiedSources,
                            )
                        },
                    )
                }
            }
        }
    }

    pendingAction?.let { (source, action) ->
        val isLogout = action == ServicesAccountAction.Logout
        MeloXGlassDialog(visible = true, onDismiss = { pendingAction = null }) {
            Text(if (isLogout) "退出 ${source.displayName}？" else "切换 ${source.displayName} 账号？", style = MaterialTheme.typography.titleLarge)
            Text(
                if (isLogout) "只会清除 MeloX 本机保存的该平台登录态。" else "会先清除当前账号，再重新打开登录流程。",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
            )
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MeloXGlassButton(onClick = { pendingAction = null }, modifier = Modifier.weight(1f), style = MeloXGlassButtonStyle.Plain) { Text("取消") }
                MeloXGlassButton(
                    onClick = {
                        if (isLogout) accountManager.logout(source) else accountManager.prepareAccountSwitch(source)
                        loginRevision++
                        pendingAction = null
                        if (!isLogout) {
                            when (source) {
                                MusicSource.Netease -> onNeteaseLogin()
                                MusicSource.QQMusic -> showQQLogin = true
                                MusicSource.Kugou -> showKugouLogin = true
                                MusicSource.AppleMusic -> showAppleMusicLogin = true
                                MusicSource.Bilibili -> showBilibiliLogin = true
                                MusicSource.Spotify -> showSpotifyLogin = true
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    style = if (isLogout) MeloXGlassButtonStyle.Destructive else MeloXGlassButtonStyle.BorderedProminent,
                ) { Text(if (isLogout) "退出" else "继续") }
            }
        }
    }
}

@Composable
private fun ServicesSectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}
