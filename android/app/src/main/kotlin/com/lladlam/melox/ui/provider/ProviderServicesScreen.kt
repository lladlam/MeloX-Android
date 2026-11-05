package com.lladlam.melox.ui.provider

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lladlam.melox.R
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.provider.MusicProviderSelectionStore
import com.lladlam.melox.core.music.provider.ProviderAccountManager
import com.lladlam.melox.core.music.provider.ThirdPartyMusicSourceConsentStore
import com.lladlam.melox.core.provider.lxuser.LxUserSourceStore
import com.lladlam.melox.core.provider.lxuser.ChkszApiKeyStore
import com.lladlam.melox.core.provider.jellyfin.JellyfinApiClient
import com.lladlam.melox.core.provider.jellyfin.JellyfinSessionStore
import com.lladlam.melox.core.provider.local.LocalMediaScanner
import com.lladlam.melox.core.provider.local.LocalMusicRepository
import com.lladlam.melox.core.provider.local.LocalScanRoot
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.account.KugouLoginScreen
import com.lladlam.melox.ui.account.KuwoLoginScreen
import com.lladlam.melox.ui.account.QQMusicLoginScreen
import com.lladlam.melox.ui.account.AppleMusicLoginScreen
import com.lladlam.melox.ui.account.BilibiliLoginScreen
import com.lladlam.melox.ui.account.SpotifyLoginScreen
import com.lladlam.melox.ui.account.YouTubeLoginScreen
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
import com.lladlam.melox.ui.glass.MeloXGlassTextField
import com.lladlam.melox.ui.legal.MeloXLegalDocument
import com.lladlam.melox.ui.legal.MeloXLegalDocumentDialog
import com.lladlam.melox.ui.legal.MeloXThirdPartyMusicSourceConsentDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val sourceRevision by MusicProviderSelectionStore.changes.collectAsState()
    val selectedId = remember(sourceRevision) { MusicProviderSelectionStore.selectedSourceId(context) }
    val accountManager = remember(neteaseSession) {
        ProviderAccountManager(context, neteaseSessionStore = neteaseSession)
    }
    var showQQLogin by remember(currentSource) { mutableStateOf(false) }
    var showQishuiLogin by remember(currentSource) { mutableStateOf(false) }
    var showKugouLiteLogin by remember(currentSource) { mutableStateOf(false) }
    var showKuwoLogin by remember(currentSource) { mutableStateOf(false) }
    var showAppleMusicLogin by remember(currentSource) { mutableStateOf(false) }
    var showBilibiliLogin by remember(currentSource) { mutableStateOf(false) }
    var showSpotifyLogin by remember(currentSource) { mutableStateOf(false) }
    var showYouTubeLogin by remember(currentSource) { mutableStateOf(false) }
    var loginRevision by remember(currentSource) { mutableStateOf(0) }
    var pendingAction by remember { mutableStateOf<Pair<MusicSource, ServicesAccountAction>?>(null) }
    var unifiedEnabled by remember { mutableStateOf(MusicProviderSelectionStore.unifiedEnabled(context)) }
    var unifiedSources by remember { mutableStateOf(MusicProviderSelectionStore.unifiedSources(context)) }
    var thirdPartySourcesEnabled by remember { mutableStateOf(ThirdPartyMusicSourceConsentStore.enabled(context)) }
    var membershipFallbackOnly by remember { mutableStateOf(ThirdPartyMusicSourceConsentStore.membershipFallbackOnly(context)) }
    var showThirdPartySourceConsent by remember { mutableStateOf(false) }
    var showThirdPartySourceAgreement by remember { mutableStateOf(false) }
    var lxSources by remember { mutableStateOf(LxUserSourceStore.list(context)) }
    var showLxImportDialog by remember { mutableStateOf(false) }
    var lxImportUrl by remember { mutableStateOf("") }
    var lxImportError by remember { mutableStateOf<String?>(null) }
    var chkszApiKey by remember { mutableStateOf(ChkszApiKeyStore.read(context)) }
    var showChkszKeyDialog by remember { mutableStateOf(false) }
    var showJellyfinDialog by remember { mutableStateOf(false) }
    var jellyfinServerUrl by remember { mutableStateOf(JellyfinSessionStore.read(context).serverUrl) }
    var jellyfinUsername by remember { mutableStateOf(JellyfinSessionStore.read(context).userName) }
    var jellyfinPassword by remember { mutableStateOf("") }
    var jellyfinError by remember { mutableStateOf<String?>(null) }
    var jellyfinBusy by remember { mutableStateOf(false) }
    var jellyfinTrustServer by remember { mutableStateOf(false) }
    val localRepository = remember(context) { LocalMusicRepository(context) }
    var localTrackCount by remember { mutableStateOf(localRepository.tracks().size) }
    var localScanBusy by remember { mutableStateOf(false) }
    var localScanMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val localScanner = remember(context) { LocalMediaScanner(context, localRepository) }
    fun scanLocalMusic() {
        if (localScanBusy) return
        localScanBusy = true
        localScanMessage = null
        scope.launch {
            runCatching { localScanner.scanAll() }
                .onSuccess { records ->
                    localTrackCount = records.size
                    localScanMessage = context.getString(R.string.provider_scanned_count, records.size)
                }
                .onFailure { localScanMessage = it.message ?: context.getString(R.string.provider_scan_failed) }
            localScanBusy = false
        }
    }
    val localPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) scanLocalMusic() else localScanMessage = context.getString(R.string.provider_audio_permission)
    }
    val localTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val saved = runCatching {
            val flags = persistableTreeFlags(context, uri)
            localRepository.addScanRoot(LocalScanRoot(uri.toString(), flags))
        }.onFailure { localScanMessage = it.message ?: context.getString(R.string.provider_folder_auth_failed) }
        if (saved.isSuccess) scanLocalMusic()
    }
    fun requestLocalScan() {
        if (Build.VERSION.SDK_INT >= 33) {
            localPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            localPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val lxFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val failures = mutableListOf<String>()
            var imported = 0
            uris.forEachIndexed { index, uri ->
                runCatching {
                    val script = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error(context.getString(R.string.provider_source_unreadable))
                    }
                    withContext(Dispatchers.IO) { LxUserSourceStore.import(context, script) }
                }.onSuccess {
                    imported++
                }.onFailure {
                    failures += context.getString(R.string.provider_file_import_failed, index + 1, it.message ?: context.getString(R.string.provider_import_generic_failed))
                }
            }
            lxSources = LxUserSourceStore.list(context)
            lxImportError = when {
                failures.isEmpty() -> context.getString(R.string.provider_imported_count, imported)
                imported > 0 -> context.getString(R.string.provider_imported_partial, imported, failures.size, failures.joinToString("\n"))
                else -> failures.joinToString("\n")
            }
            showLxImportDialog = true
        }
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
            onLoggedIn = { showQQLogin = false; loginRevision++ },
        )
        return
    }
    if (showKugouLiteLogin && currentSource == MusicSource.KugouLite) {
        KugouLoginScreen(
            lite = true,
            onDismiss = { showKugouLiteLogin = false },
            onLoggedIn = { showKugouLiteLogin = false; loginRevision++ },
        )
        return
    }
    if (showKuwoLogin && currentSource == MusicSource.Kuwo) {
        KuwoLoginScreen(
            onDismiss = { showKuwoLogin = false },
            onLoggedIn = { showKuwoLogin = false; loginRevision++ },
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
            onLoggedIn = { showYouTubeLogin = false; loginRevision++ },
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
            title = stringResource(R.string.provider_music_services),
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

        ServicesSectionLabel(stringResource(R.string.provider_sources))
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            MusicProviderSelectionStore.visibleSources().forEachIndexed { index, source ->
                val account = accountManager.state(source)
                MeloXIosListRow(
                    title = source.displayName,
                    subtitle = when {
                        source == selectedId.builtIn && account.loggedIn -> stringResource(R.string.provider_source_logged_in)
                        source == selectedId.builtIn -> stringResource(R.string.provider_source_logged_out)
                        account.loggedIn -> stringResource(R.string.provider_logged_in)
                        else -> stringResource(R.string.provider_not_signed_in)
                    },
                    leading = {
                        MeloXSymbolIcon(
                            MeloXSymbol.MusicNote,
                            Modifier.size(25.dp),
                            if (source == selectedId.builtIn) MeloXSystemColors.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = .70f),
                        )
                    },
                    trailing = if (source == selectedId.builtIn) {
                        { MeloXSymbolIcon(MeloXSymbol.Check, Modifier.size(21.dp), MeloXSystemColors.Red) }
                    } else null,
                    onClick = { onSourceSelected(source) },
                    showTopSeparator = index > 0,
                )
            }
        }

        Spacer(Modifier.size(24.dp))
        ServicesSectionLabel(stringResource(R.string.provider_current_account))
        PluginServiceSection()
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
                MeloXIosListRow(
                    title = currentSource.displayName,
                    subtitle = when {
                        currentSource == MusicSource.Local -> stringResource(R.string.provider_local_no_login)
                        currentAccount.loggedIn && !currentAccount.accountId.isNullOrBlank() -> stringResource(R.string.provider_logged_in_id, currentAccount.accountId!!)
                    currentAccount.loggedIn -> stringResource(R.string.provider_logged_in)
                    else -> stringResource(R.string.provider_logged_out_tap)
                },
                leading = { MeloXSymbolIcon(MeloXSymbol.Person, Modifier.size(25.dp), MeloXSystemColors.Red) },
                onClick = if (currentAccount.loggedIn) null else {
                    {
                        when (currentSource) {
                            MusicSource.Netease -> onNeteaseLogin()
                            MusicSource.QQMusic -> showQQLogin = true
                            MusicSource.Qishui -> showQishuiLogin = true
                            MusicSource.Kugou -> Unit // Retired source; retained only for stored identities.
                            MusicSource.KugouLite -> showKugouLiteLogin = true
                            MusicSource.Kuwo -> showKuwoLogin = true
                            MusicSource.AppleMusic -> showAppleMusicLogin = true
                            MusicSource.Bilibili -> showBilibiliLogin = true
                            MusicSource.Spotify -> showSpotifyLogin = true
                            MusicSource.YouTubeMusic -> showYouTubeLogin = true
                            MusicSource.Jellyfin -> { jellyfinError = null; showJellyfinDialog = true }
                            MusicSource.Local -> Unit
                        }
                    }
                },
                showTopSeparator = false,
            )
            if (currentAccount.loggedIn) {
                MeloXIosListRow(
                    title = stringResource(R.string.provider_switch_account),
                    subtitle = stringResource(R.string.provider_switch_relogin_subtitle),
                    leading = { MeloXSymbolIcon(MeloXSymbol.Refresh, Modifier.size(24.dp), MeloXSystemColors.Red) },
                    onClick = { pendingAction = currentSource to ServicesAccountAction.Switch },
                )
                MeloXIosListRow(
                    title = stringResource(R.string.provider_logout_title, currentSource.displayName),
                    subtitle = stringResource(R.string.provider_logout_this),
                    leading = { MeloXSymbolIcon(MeloXSymbol.Xmark, Modifier.size(24.dp), MeloXSystemColors.Red) },
                    onClick = { pendingAction = currentSource to ServicesAccountAction.Logout },
                )
            }
        }

        Spacer(Modifier.size(24.dp))
        if (currentSource == MusicSource.YouTubeMusic && currentAccount.loggedIn) {
            YouTubeAccountControls(onRelogin = { showYouTubeLogin = true })
            Spacer(Modifier.size(24.dp))
        }
        if (currentSource == MusicSource.Local) {
            ServicesSectionLabel(stringResource(R.string.provider_local_library))
            MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
                MeloXIosListRow(
                    title = if (localScanBusy) stringResource(R.string.provider_scanning_local) else stringResource(R.string.provider_scan_device),
                    subtitle = stringResource(R.string.provider_scan_subtitle, localTrackCount),
                    leading = { MeloXSymbolIcon(MeloXSymbol.Search, Modifier.size(24.dp), MeloXSystemColors.Red) },
                    onClick = { requestLocalScan() },
                    showTopSeparator = false,
                )
                MeloXIosListRow(
                    title = stringResource(R.string.provider_add_folder),
                    subtitle = stringResource(R.string.provider_add_folder_subtitle),
                    leading = { MeloXSymbolIcon(MeloXSymbol.Storage, Modifier.size(24.dp), MeloXSystemColors.Red) },
                    onClick = { localTreeLauncher.launch(null) },
                    showTopSeparator = true,
                )
                localScanMessage?.let { message ->
                    MeloXIosListRow(
                        title = message,
                        subtitle = stringResource(R.string.provider_local_stays_local),
                        leading = { Spacer(Modifier.width(25.dp)) },
                        onClick = null,
                        showTopSeparator = true,
                    )
                }
                localRepository.scanRoots().forEach { root ->
                    MeloXIosListRow(
                        title = stringResource(R.string.provider_authorized_folder),
                        subtitle = root.uri,
                        leading = { Spacer(Modifier.width(25.dp)) },
                        detail = stringResource(R.string.provider_remove),
                        onClick = {
                            localRepository.removeScanRoot(root.uri)
                            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(root.uri), root.persistedFlags) }
                            scanLocalMusic()
                        },
                        showTopSeparator = true,
                    )
                }
            }
            Spacer(Modifier.size(24.dp))
        }
        ServicesSectionLabel(stringResource(R.string.provider_third_party))
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            MeloXIosListRow(
                title = "Jellyfin",
                subtitle = if (accountManager.state(MusicSource.Jellyfin).loggedIn) stringResource(R.string.provider_jellyfin_connected) else stringResource(R.string.provider_jellyfin_connect_hint),
                leading = { MeloXSymbolIcon(MeloXSymbol.Devices, Modifier.size(24.dp), MeloXSystemColors.Red) },
                onClick = { jellyfinError = null; showJellyfinDialog = true },
                showTopSeparator = false,
            )
            MeloXIosListRow(
                title = stringResource(R.string.provider_enable_third_party),
                subtitle = stringResource(R.string.provider_third_party_consent_hint),
                leading = { MeloXSymbolIcon(MeloXSymbol.Info, Modifier.size(24.dp), MeloXSystemColors.Red) },
                trailing = {
                    MeloXGlassToggle(
                        checked = thirdPartySourcesEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) showThirdPartySourceConsent = true
                            else {
                                ThirdPartyMusicSourceConsentStore.reject(context)
                                thirdPartySourcesEnabled = false
                            }
                        },
                    )
                },
                showTopSeparator = true,
            )
            if (thirdPartySourcesEnabled) {
                MeloXIosListRow(
                    title = stringResource(R.string.provider_membership_fallback),
                    subtitle = stringResource(R.string.provider_membership_fallback_subtitle),
                    leading = { Spacer(Modifier.width(25.dp)) },
                    trailing = {
                        MeloXGlassToggle(
                            checked = membershipFallbackOnly,
                            onCheckedChange = {
                                membershipFallbackOnly = it
                                ThirdPartyMusicSourceConsentStore.setMembershipFallbackOnly(context, it)
                            },
                        )
                    },
                    showTopSeparator = false,
                )
                MeloXIosListRow(
                    title = stringResource(R.string.provider_chksz),
                    subtitle = if (chkszApiKey.isBlank()) stringResource(R.string.provider_chksz_missing) else stringResource(R.string.provider_chksz_configured),
                    leading = { Spacer(Modifier.width(25.dp)) },
                    onClick = { showChkszKeyDialog = true },
                )
                MeloXIosListRow(
                    title = stringResource(R.string.provider_view_third_party_terms),
                    subtitle = stringResource(R.string.provider_third_party_terms_subtitle),
                    leading = { Spacer(Modifier.width(25.dp)) },
                    onClick = { showThirdPartySourceAgreement = true },
                )
            }
        }

        if (thirdPartySourcesEnabled) {
            Spacer(Modifier.size(24.dp))
            ServicesSectionLabel(stringResource(R.string.provider_added_sources))
            MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
                MeloXIosListRow(
                    title = stringResource(R.string.provider_add_source),
                    subtitle = stringResource(R.string.provider_add_source_subtitle),
                    leading = { MeloXSymbolIcon(MeloXSymbol.Plus, Modifier.size(24.dp), MeloXSystemColors.Red) },
                    onClick = { showLxImportDialog = true; lxImportError = null },
                    showTopSeparator = false,
                )
                lxSources.forEach { source ->
                    MeloXIosListRow(
                        title = source.metadata.name ?: source.id,
                        subtitle = listOfNotNull(
                            source.metadata.version?.let { "v$it" },
                            source.metadata.author,
                            source.metadata.expirationTime?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.provider_expires, it) },
                        ).joinToString(" · "),
                        detail = stringResource(R.string.provider_delete),
                        leading = { Spacer(Modifier.width(25.dp)) },
                        onClick = {
                            LxUserSourceStore.remove(context, source.id)
                            lxSources = LxUserSourceStore.list(context)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.size(24.dp))
        ServicesSectionLabel(stringResource(R.string.provider_cross_search))
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            MeloXIosListRow(
                title = stringResource(R.string.provider_unified_title),
                subtitle = stringResource(R.string.provider_unified_subtitle),
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
                        subtitle = if (account.loggedIn) stringResource(R.string.provider_unified_participates) else stringResource(R.string.provider_unified_skipped),
                        detail = if (source in unifiedSources) stringResource(R.string.provider_enabled) else "",
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
            Text(if (isLogout) stringResource(R.string.provider_logout_confirm, source.displayName) else stringResource(R.string.provider_switch_confirm, source.displayName), style = MaterialTheme.typography.titleLarge)
            Text(
                if (isLogout) stringResource(R.string.provider_logout_short_body) else stringResource(R.string.provider_switch_short_body),
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
            )
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MeloXGlassButton(onClick = { pendingAction = null }, modifier = Modifier.weight(1f), style = MeloXGlassButtonStyle.Plain) { Text(stringResource(R.string.action_cancel)) }
                MeloXGlassButton(
                    onClick = {
                        if (isLogout) accountManager.logout(source) else accountManager.prepareAccountSwitch(source)
                        loginRevision++
                        pendingAction = null
                        if (!isLogout) {
                            when (source) {
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
                    style = if (isLogout) MeloXGlassButtonStyle.Destructive else MeloXGlassButtonStyle.BorderedProminent,
                ) { Text(if (isLogout) stringResource(R.string.provider_logout) else stringResource(R.string.provider_continue)) }
            }
        }
    }

    if (showThirdPartySourceConsent) {
        MeloXThirdPartyMusicSourceConsentDialog(
            onReject = { showThirdPartySourceConsent = false },
            onAccept = {
                ThirdPartyMusicSourceConsentStore.accept(context)
                thirdPartySourcesEnabled = true
                showThirdPartySourceConsent = false
            },
        )
    }
    if (showThirdPartySourceAgreement) {
        MeloXLegalDocumentDialog(
            document = MeloXLegalDocument.ThirdPartyMusicSources,
            onDismiss = { showThirdPartySourceAgreement = false },
        )
    }
    if (showLxImportDialog) {
        MeloXGlassDialog(visible = true, onDismiss = { showLxImportDialog = false }) {
            Text(stringResource(R.string.provider_import_lx), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.provider_import_lx_hint),
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            MeloXGlassButton(
                // Android file providers often label .js as application/javascript,
                // octet-stream, or provide no MIME type at all.
                onClick = { lxFileLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                style = MeloXGlassButtonStyle.BorderedProminent,
            ) { Text(stringResource(R.string.provider_import_local)) }
            MeloXGlassTextField(
                value = lxImportUrl,
                onValueChange = { lxImportUrl = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                placeholder = { Text("https://.../source.js") },
                singleLine = true,
            )
            lxImportError?.let {
                Text(it, modifier = Modifier.padding(top = 7.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MeloXGlassButton(
                    onClick = { showLxImportDialog = false },
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.Plain,
                ) { Text(stringResource(R.string.action_cancel)) }
                MeloXGlassButton(
                    onClick = {
                        val url = lxImportUrl.trim()
                        scope.launch {
                            runCatching {
                                require(url.startsWith("https://") || url.startsWith("http://")) { context.getString(R.string.provider_invalid_http) }
                                val script = withContext(Dispatchers.IO) {
                                    val request = okhttp3.Request.Builder().url(url).build()
                                    val client = com.lladlam.melox.core.network.MeloXHttpClient.thirdPartyAndMedia
                                        .newBuilder().callTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build()
                                    client.newCall(request).execute().use { response ->
                                        if (!response.isSuccessful) error(context.getString(R.string.provider_download_failed, response.code))
                                        require(response.body.contentLength() <= LxUserSourceStore.MaxScriptBytes &&
                                            !response.body.source().request(LxUserSourceStore.MaxScriptBytes + 1)) {
                                            "音乐源脚本过大（最大 9 MB）"
                                        }
                                        response.body.string()
                                    }
                                }
                                withContext(Dispatchers.IO) { LxUserSourceStore.import(context, script) }
                            }.onSuccess {
                                lxSources = LxUserSourceStore.list(context)
                                lxImportUrl = ""
                                showLxImportDialog = false
                            }.onFailure { lxImportError = it.message ?: context.getString(R.string.provider_import_failed) }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.BorderedProminent,
                ) { Text(stringResource(R.string.provider_import)) }
            }
        }
    }
    if (showJellyfinDialog) {
        MeloXGlassDialog(visible = true, onDismiss = { if (!jellyfinBusy) showJellyfinDialog = false }) {
            Text(stringResource(R.string.provider_connect_jellyfin), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.provider_jellyfin_hint), modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f), fontSize = 13.sp, lineHeight = 19.sp)
            MeloXGlassTextField(jellyfinServerUrl, { jellyfinServerUrl = it }, Modifier.fillMaxWidth().padding(top = 12.dp), placeholder = { Text("https://music.example.com") }, singleLine = true)
            MeloXGlassTextField(jellyfinUsername, { jellyfinUsername = it }, Modifier.fillMaxWidth().padding(top = 10.dp), placeholder = { Text(stringResource(R.string.provider_username)) }, singleLine = true)
            MeloXGlassTextField(jellyfinPassword, { jellyfinPassword = it }, Modifier.fillMaxWidth().padding(top = 10.dp), placeholder = { Text(stringResource(R.string.provider_password)) }, singleLine = true)
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).clickable { jellyfinTrustServer = !jellyfinTrustServer },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MeloXGlassToggle(checked = jellyfinTrustServer, onCheckedChange = { jellyfinTrustServer = it })
                Text(
                    stringResource(R.string.provider_jellyfin_insecure),
                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
            jellyfinError?.let { Text(it, Modifier.padding(top = 7.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MeloXGlassButton(onClick = { JellyfinSessionStore.clear(context); showJellyfinDialog = false }, modifier = Modifier.weight(1f), style = MeloXGlassButtonStyle.Plain) { Text(stringResource(R.string.provider_logout)) }
                MeloXGlassButton(
                    onClick = {
                        jellyfinBusy = true
                        scope.launch {
                            runCatching {
                                require(jellyfinServerUrl.trim().startsWith("http://") || jellyfinServerUrl.trim().startsWith("https://")) { context.getString(R.string.provider_invalid_server) }
                                require(jellyfinUsername.isNotBlank()) { context.getString(R.string.provider_username_required) }
                                withContext(Dispatchers.IO) {
                                    JellyfinApiClient(
                                        com.lladlam.melox.core.network.MeloXHttpClient.shared,
                                        trustUserServer = jellyfinTrustServer,
                                    ).authenticate(jellyfinServerUrl.trim(), jellyfinUsername.trim(), jellyfinPassword)
                                        .also { JellyfinSessionStore.write(context, it) }
                                }
                            }.onSuccess { jellyfinPassword = ""; jellyfinBusy = false; loginRevision++; showJellyfinDialog = false }
                                .onFailure { jellyfinBusy = false; jellyfinError = it.message ?: context.getString(R.string.provider_jellyfin_failed) }
                        }
                    },
                    modifier = Modifier.weight(1f), style = MeloXGlassButtonStyle.BorderedProminent,
                ) { Text(if (jellyfinBusy) stringResource(R.string.provider_connecting) else stringResource(R.string.provider_connect)) }
            }
        }
    }
    if (showChkszKeyDialog) {
        MeloXGlassDialog(visible = true, onDismiss = { showChkszKeyDialog = false }) {
            Text(stringResource(R.string.provider_chksz_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.provider_chksz_body),
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            MeloXGlassTextField(
                value = chkszApiKey,
                onValueChange = { chkszApiKey = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                placeholder = { Text(stringResource(R.string.provider_api_key_placeholder)) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MeloXGlassButton(
                    onClick = {
                        ChkszApiKeyStore.clear(context)
                        chkszApiKey = ""
                        showChkszKeyDialog = false
                    },
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.Plain,
                ) { Text(stringResource(R.string.provider_clear)) }
                MeloXGlassButton(
                    onClick = {
                        ChkszApiKeyStore.write(context, chkszApiKey)
                        chkszApiKey = ChkszApiKeyStore.read(context)
                        showChkszKeyDialog = false
                    },
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.BorderedProminent,
                ) { Text(stringResource(R.string.provider_save)) }
            }
        }
    }
}

private fun persistableTreeFlags(context: Context, uri: Uri): Int {
    val resolver = context.contentResolver
    val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    val readOnly = Intent.FLAG_GRANT_READ_URI_PERMISSION
    return runCatching {
        resolver.takePersistableUriPermission(uri, readWrite)
        readWrite
    }.getOrElse {
        resolver.takePersistableUriPermission(uri, readOnly)
        readOnly
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
