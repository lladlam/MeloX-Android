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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.R
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.provider.MusicProviderSelectionStore
import com.lladlam.melox.core.music.provider.ProviderAccountManager
import com.lladlam.melox.core.provider.kugou.KugouLiteProvider
import com.lladlam.melox.core.provider.kugou.KugouSessionStore
import com.lladlam.melox.ui.account.KugouLoginScreen
import com.lladlam.melox.ui.account.KuwoLoginScreen
import com.lladlam.melox.ui.account.QQMusicLoginScreen
import com.lladlam.melox.ui.account.AppleMusicLoginScreen
import com.lladlam.melox.ui.account.BilibiliLoginScreen
import com.lladlam.melox.ui.account.SpotifyLoginScreen
import com.lladlam.melox.ui.account.YouTubeLoginScreen
import com.lladlam.melox.ui.glass.meloXContentSurface
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassSheet
import com.lladlam.melox.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

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
    onOpenAccount: () -> Unit,
    onOpenServices: () -> Unit,
    onOpenMessages: () -> Unit,
    currentSourceId: com.lladlam.melox.core.music.model.SourceId = com.lladlam.melox.core.music.model.SourceId.fromBuiltIn(currentSource),
) {
    val context = LocalContext.current
    val accountManager = remember(neteaseSession) {
        ProviderAccountManager(context, neteaseSessionStore = neteaseSession)
    }

    var showServiceDialog by remember { mutableStateOf(false) }
    var showQQLogin by remember(currentSource) { mutableStateOf(false) }
    var showQishuiLogin by remember(currentSource) { mutableStateOf(false) }
    var showKugouLiteLogin by remember(currentSource) { mutableStateOf(false) }
    var showKuwoLogin by remember(currentSource) { mutableStateOf(false) }
    var showAppleMusicLogin by remember(currentSource) { mutableStateOf(false) }
    var showBilibiliLogin by remember(currentSource) { mutableStateOf(false) }
    var showSpotifyLogin by remember(currentSource) { mutableStateOf(false) }
    var showYouTubeLogin by remember(currentSource) { mutableStateOf(false) }
    var loginRevision by remember(currentSource) { mutableStateOf(0) }
    var pendingAccountAction by remember { mutableStateOf<PendingProviderAccountAction?>(null) }
    var showLiteVipClaim by remember { mutableStateOf(false) }
    var liteVipClaimMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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

    if (showQishuiLogin && currentSource == MusicSource.Qishui) {
        com.lladlam.melox.ui.account.QishuiLoginScreen(
            onDismiss = { showQishuiLogin = false },
            onLoggedIn = { showQishuiLogin = false; loginRevision++ },
        )
        return
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
    if (showKugouLiteLogin && currentSource == MusicSource.KugouLite) {
        KugouLoginScreen(
            lite = true,
            onDismiss = { showKugouLiteLogin = false },
            onLoggedIn = {
                showKugouLiteLogin = false
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
    if (showYouTubeLogin && currentSource == MusicSource.YouTubeMusic) {
        YouTubeLoginScreen(
            onDismiss = { showYouTubeLogin = false },
            onLoggedIn = { showYouTubeLogin = false; loginRevision += 1 },
        )
        return
    }

    // Music-service navigation belongs to the account row.  Keeping it there
    // avoids a second floating control competing with the canonical Settings UI.
    SettingsScreen(
        session = neteaseSession,
        source = currentSource,
        sourceId = currentSourceId,
        onLogin = onNeteaseLogin,
        onOpenAccount = onOpenAccount,
        onOpenServices = onOpenServices,
        onOpenMessages = onOpenMessages,
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
                Text(stringResource(R.string.provider_music_services), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.provider_switch_source_hint),
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
                        stringResource(R.string.provider_current_account),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                    )
                    Spacer(Modifier.height(7.dp))

                    ProviderSimpleCard(
                        currentSource.displayName,
                        when {
                            currentSource == MusicSource.Local -> stringResource(R.string.provider_local_no_login)
                            currentAccount.loggedIn && !currentAccount.accountId.isNullOrBlank() ->
                                stringResource(R.string.provider_logged_in_id, currentAccount.accountId!!)
                            currentAccount.loggedIn -> stringResource(R.string.provider_logged_in)
                            else -> stringResource(R.string.provider_logged_out_tap)
                        },
                        onClick = if (currentAccount.loggedIn) null else {
                            {
                                showServiceDialog = false
                                when (currentSource) {
                                    MusicSource.Netease -> onNeteaseLogin()
                                    MusicSource.QQMusic -> showQQLogin = true
                                    MusicSource.Qishui -> showQishuiLogin = true
                                    MusicSource.Kugou -> Unit
                                    MusicSource.KugouLite -> showKugouLiteLogin = true
                                    MusicSource.Kuwo -> showKuwoLogin = true
                                    MusicSource.AppleMusic -> showAppleMusicLogin = true
                                    MusicSource.Bilibili -> showBilibiliLogin = true
                                     MusicSource.Spotify -> showSpotifyLogin = true
                                     MusicSource.YouTubeMusic -> showYouTubeLogin = true
                                     MusicSource.Jellyfin -> Unit
                                    MusicSource.Local -> Unit
                                }
                            }
                        },
                    )

                    if (currentAccount.loggedIn) {
                        if (currentSource == MusicSource.KugouLite) {
                            Spacer(Modifier.height(8.dp))
                            ProviderSimpleCard(
                                stringResource(R.string.provider_kugou_lite_daily_vip),
                                stringResource(R.string.provider_kugou_lite_daily_vip_subtitle),
                                onClick = {
                                    showServiceDialog = false
                                    liteVipClaimMessage = null
                                    showLiteVipClaim = true
                                },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        ProviderSimpleCard(
                            stringResource(R.string.provider_switch_account),
                            when (currentSource) {
                                MusicSource.Netease -> stringResource(R.string.provider_switch_netease)
                                MusicSource.QQMusic -> stringResource(R.string.provider_switch_qq)
                                MusicSource.Kugou -> stringResource(R.string.provider_switch_kugou)
                                MusicSource.KugouLite -> stringResource(R.string.provider_switch_kugou_lite)
                                MusicSource.Kuwo -> stringResource(R.string.provider_switch_kuwo)
                                MusicSource.AppleMusic -> stringResource(R.string.provider_switch_apple)
                                MusicSource.Bilibili -> stringResource(R.string.provider_switch_bilibili)
                                MusicSource.Spotify -> stringResource(R.string.provider_switch_spotify)
                                MusicSource.YouTubeMusic -> stringResource(R.string.provider_switch_youtube)
                                MusicSource.Jellyfin -> stringResource(R.string.provider_switch_jellyfin)
                                MusicSource.Local -> stringResource(R.string.provider_switch_local)
                                MusicSource.Qishui -> "清除汽水音乐登录状态后，使用抖音重新扫码"
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
                            stringResource(R.string.provider_logout_title, currentSource.displayName),
                            stringResource(R.string.provider_logout_subtitle),
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
                    if (currentSource == MusicSource.YouTubeMusic && currentAccount.loggedIn) {
                        YouTubeAccountControls(onRelogin = {
                            showServiceDialog = false
                            showYouTubeLogin = true
                        })
                        Spacer(Modifier.height(16.dp))
                    }
                    Text(
                        stringResource(R.string.provider_cross_search),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                    )
                    Spacer(Modifier.height(7.dp))
                    ProviderSettingToggle(
                        title = stringResource(R.string.provider_unified_title),
                        subtitle = stringResource(R.string.provider_unified_subtitle_hub),
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
                                    source == currentSource && account.loggedIn -> stringResource(R.string.provider_source_logged_in)
                                    source == currentSource -> stringResource(R.string.provider_source_logged_out)
                                    account.loggedIn -> stringResource(R.string.provider_unified_logged_in)
                                    else -> stringResource(R.string.provider_unified_logged_out)
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
            ) { Text(stringResource(R.string.action_done)) }
        }
    }

    pendingAccountAction?.let { pending ->
        val actionTitle = when (pending.action) {
            ProviderAccountAction.Logout -> stringResource(R.string.provider_logout_confirm, pending.source.displayName)
            ProviderAccountAction.SwitchAccount -> stringResource(R.string.provider_switch_confirm, pending.source.displayName)
        }
        val actionBody = when (pending.action) {
            ProviderAccountAction.Logout -> stringResource(R.string.provider_logout_body)
            ProviderAccountAction.SwitchAccount -> stringResource(R.string.provider_switch_body)
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
                ) { Text(stringResource(R.string.action_cancel)) }
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
                                MusicSource.Qishui -> showQishuiLogin = true
                                MusicSource.Kugou -> Unit
                                MusicSource.KugouLite -> showKugouLiteLogin = true
                                MusicSource.Kuwo -> showKuwoLogin = true
                                MusicSource.AppleMusic -> showAppleMusicLogin = true
                                MusicSource.Bilibili -> showBilibiliLogin = true
                                MusicSource.Spotify -> showSpotifyLogin = true
                                MusicSource.YouTubeMusic -> showYouTubeLogin = true
                                MusicSource.Jellyfin -> Unit
                                MusicSource.Local -> Unit
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    style = if (pending.action == ProviderAccountAction.Logout) {
                        MeloXGlassButtonStyle.Destructive
                    } else {
                        MeloXGlassButtonStyle.BorderedProminent
                    },
                ) { Text(if (pending.action == ProviderAccountAction.Logout) stringResource(R.string.provider_logout) else stringResource(R.string.provider_continue)) }
            }
        }
    }

    if (showLiteVipClaim) {
        MeloXGlassDialog(visible = true, onDismiss = { showLiteVipClaim = false }) {
            Text(stringResource(R.string.provider_kugou_lite_daily_vip), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.provider_kugou_lite_daily_vip_body),
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
            )
            liteVipClaimMessage?.let {
                Text(it, modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.primary)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MeloXGlassButton(
                    onClick = { showLiteVipClaim = false },
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.Plain,
                ) { Text(stringResource(R.string.action_cancel)) }
                MeloXGlassButton(
                    onClick = {
                        scope.launch {
                            val provider = KugouLiteProvider(
                                sessionProvider = { KugouSessionStore.readLite(context) },
                                persistAuth = { auth -> KugouSessionStore.updateLiteEntitlements(context, auth = auth) },
                                persistDfid = { dfid -> KugouSessionStore.updateLiteDfid(context, dfid) },
                                persistVip = { vip ->
                                    KugouSessionStore.updateLiteEntitlements(
                                        context = context,
                                        vipType = vip.vipType,
                                        vipExpiresAtEpochSeconds = vip.expiresAtEpochSeconds,
                                        superVip = vip.superVip,
                                        vipActive = vip.isVip,
                                        freeListenEligible = vip.freeListen,
                                    )
                                },
                            )
                            liteVipClaimMessage = runCatching { provider.claimDailyVip() }
                                .fold(
                                    onSuccess = { context.getString(R.string.provider_kugou_lite_daily_vip_done) },
                                    onFailure = { it.message ?: context.getString(R.string.provider_kugou_lite_daily_vip_failed) },
                                )
                            loginRevision += 1
                        }
                    },
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.BorderedProminent,
                ) { Text(stringResource(R.string.provider_kugou_lite_daily_vip_claim)) }
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
                    selected && accountState.loggedIn -> stringResource(R.string.provider_source_logged_in)
                    selected -> stringResource(R.string.provider_source_current)
                    accountState.loggedIn -> stringResource(R.string.provider_logged_in)
                    else -> stringResource(R.string.provider_not_signed_in)
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
