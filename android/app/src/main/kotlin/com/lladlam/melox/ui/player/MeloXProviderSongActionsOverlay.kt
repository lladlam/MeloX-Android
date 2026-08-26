package com.lladlam.melox.ui.player

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.provider.FavoriteCapability
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.core.music.provider.PlaylistWriteCapability
import com.lladlam.melox.core.music.provider.ProviderAccountManager
import com.lladlam.melox.core.network.MeloXSearchKind
import com.lladlam.melox.core.provider.bilibili.BilibiliLyricOffsetStore
import com.lladlam.melox.ui.glass.MeloXActionIcon
import com.lladlam.melox.ui.glass.MeloXIosGroupedList
import com.lladlam.melox.ui.glass.MeloXIosListRow
import com.lladlam.melox.ui.glass.MeloXLiquidSlider
import com.lladlam.melox.ui.search.MeloXSearchLaunchBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ProviderSongActionPage {
    Main,
    Sleep,
    Playlists,
}

/**
 * Actions valid for non-NetEase provider tracks. Remote writes only appear when
 * the active provider exposes an explicit capability backed by a real platform
 * API. QQ uses FavoriteCapability; Kugou uses PlaylistWriteCapability.
 */
@Composable
internal fun MeloXProviderSongActionsOverlay(
    state: MeloXPlaybackUiState,
    identity: MusicResourceId,
    visible: Boolean,
    onDismiss: () -> Unit,
    onNavigateSearch: ((String, MeloXSearchKind) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val provider = remember(identity.source) {
        MeloXMusicProviders.create(context).require(identity.source)
    }
    val favoriteCapability = provider as? FavoriteCapability
    val playlistWriteCapability = provider as? PlaylistWriteCapability
    val accountManager = remember { ProviderAccountManager(context) }
    val providerLoggedIn = remember(identity.source, visible) {
        accountManager.state(identity.source).loggedIn
    }
    val actionTrack = remember(identity, state.title, state.artist, state.album, state.durationMs) {
        MusicTrack(
            id = identity,
            title = state.title.ifBlank { "未知歌曲" },
            artists = listOf(
                MusicArtistRef(name = state.artist.ifBlank { "未知歌手" }),
            ),
            durationMs = state.durationMs.takeIf { it > 0L },
        )
    }

    var page by remember(identity, visible) { mutableStateOf(ProviderSongActionPage.Main) }
    var favoriteKnownState by remember(identity) { mutableStateOf<Boolean?>(null) }
    var favoriteWorking by remember(identity) { mutableStateOf(false) }
    var writablePlaylists by remember(identity) { mutableStateOf<List<MusicPlaylistSummary>>(emptyList()) }
    var playlistsLoading by remember(identity) { mutableStateOf(false) }
    var playlistWriteWorking by remember(identity) { mutableStateOf(false) }
    var actionStatus by remember(identity) { mutableStateOf<String?>(null) }
    var actionError by remember(identity) { mutableStateOf<String?>(null) }
    val bilibiliOffsetState = if (identity.source == MusicSource.Bilibili) {
        BilibiliLyricOffsetStore.state(context, identity.value)
    } else null

    BackHandler(enabled = visible) {
        if (page == ProviderSongActionPage.Main) onDismiss() else page = ProviderSongActionPage.Main
    }

    if (visible) ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(width = 58.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .24f), RoundedCornerShape(99.dp)),
                )
            }
        },
    ) {
        AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        (fadeIn(spring(stiffness = 520f)) + scaleIn(initialScale = 0.96f)) togetherWith
                            (fadeOut(spring(stiffness = 620f)) + scaleOut(targetScale = 0.96f))
                    },
            modifier = Modifier.fillMaxWidth(),
            label = "provider-song-action-page",
        ) { target ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        ProviderActionHeader(
                            state = state,
                            subtitle = when (target) {
                                ProviderSongActionPage.Main -> "${identity.source.displayName} · 歌曲操作"
                                ProviderSongActionPage.Sleep -> "定时关闭"
                                ProviderSongActionPage.Playlists -> "选择目标歌单"
                            },
                        )

                        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        when (target) {
                            ProviderSongActionPage.Main -> {
                                if (favoriteCapability != null) {
                                    ProviderActionItem(
                                        title = when {
                                            !providerLoggedIn -> "登录 ${identity.source.displayName} 后可使用我喜欢"
                                            favoriteWorking -> "正在更新我喜欢…"
                                            favoriteKnownState == true -> "从我喜欢移除"
                                            else -> "添加到我喜欢"
                                        },
                                        symbol = if (favoriteKnownState == true) "♥" else "♡",
                                        enabled = providerLoggedIn && !favoriteWorking,
                                    ) {
                                        val targetFavorite = favoriteKnownState != true
                                        favoriteWorking = true
                                        actionStatus = null
                                        actionError = null
                                        scope.launch {
                                            runCatching {
                                                favoriteCapability.setFavorite(actionTrack, targetFavorite)
                                            }.onSuccess {
                                                favoriteKnownState = targetFavorite
                                                actionStatus = if (targetFavorite) {
                                                    "已添加到 ${identity.source.displayName} 我喜欢"
                                                } else {
                                                    "已从 ${identity.source.displayName} 我喜欢移除"
                                                }
                                            }.onFailure { failure ->
                                                actionError = failure.message ?: "我喜欢操作失败"
                                            }
                                            favoriteWorking = false
                                        }
                                    }
                                }

                                if (playlistWriteCapability != null) {
                                    ProviderActionItem(
                                        title = if (providerLoggedIn) "添加到歌单" else "登录 ${identity.source.displayName} 后可添加到歌单",
                                        symbol = "＋",
                                        enabled = providerLoggedIn && !playlistsLoading,
                                    ) {
                                        page = ProviderSongActionPage.Playlists
                                        playlistsLoading = true
                                        actionStatus = null
                                        actionError = null
                                        scope.launch {
                                            runCatching {
                                                withContext(Dispatchers.IO) {
                                                    playlistWriteCapability.writablePlaylists(page = 1, pageSize = 50).items
                                                }
                                            }.onSuccess { playlists ->
                                                writablePlaylists = playlists
                                                if (playlists.isEmpty()) actionError = "没有返回可写入的用户歌单"
                                            }.onFailure { failure ->
                                                writablePlaylists = emptyList()
                                                actionError = failure.message ?: "无法加载可写歌单"
                                            }
                                            playlistsLoading = false
                                        }
                                    }
                                }

                                ProviderActionItem("定时关闭", "◷") { page = ProviderSongActionPage.Sleep }
                                ProviderActionItem("添加到播放队列", "+") {
                                    state.addCurrentToQueue()
                                    onDismiss()
                                }
                                ProviderActionItem("系统分享", "↗") {
                                    shareProviderSong(context, state, identity)
                                    onDismiss()
                                }
                                if (identity.source != MusicSource.Bilibili && state.album.isNotBlank() && onNavigateSearch != null) {
                                    ProviderActionItem("前往专辑：${state.album}", "▣") {
                                        val target = state.album
                                        MeloXSearchLaunchBus.post(target, MeloXSearchKind.Albums)
                                        onDismiss()
                                        onNavigateSearch(target, MeloXSearchKind.Albums)
                                    }
                                }
                                if (identity.source != MusicSource.Bilibili && state.artist.isNotBlank() && onNavigateSearch != null) {
                                    ProviderActionItem("前往艺人：${state.artist}", "♬") {
                                        val target = state.artist.substringBefore(" /")
                                        MeloXSearchLaunchBus.post(target, MeloXSearchKind.Artists)
                                        onDismiss()
                                        onNavigateSearch(target, MeloXSearchKind.Artists)
                                    }
                                }

                            }

                            ProviderSongActionPage.Sleep -> {
                                listOf(15, 30, 45, 60).forEach { minutes ->
                                    ProviderActionItem("$minutes 分钟后", "◷") {
                                        state.setSleepTimer(minutes)
                                        onDismiss()
                                    }
                                }
                                if (state.sleepTimerEndRealtimeMs > 0L) {
                                    ProviderActionItem("取消定时", "×") {
                                        state.cancelSleepTimer()
                                        onDismiss()
                                    }
                                }
                                ProviderActionItem("返回", "‹") { page = ProviderSongActionPage.Main }
                            }

                            ProviderSongActionPage.Playlists -> {
                                when {
                                    playlistsLoading -> ProviderActionItem("正在加载可写歌单…", "…", enabled = false) {}
                                    writablePlaylists.isEmpty() -> ProviderActionItem("没有可写歌单", "—", enabled = false) {}
                                    else -> writablePlaylists.forEach { playlist ->
                                        ProviderActionItem(
                                            title = playlist.title,
                                            symbol = "▣",
                                            enabled = !playlistWriteWorking,
                                        ) {
                                            val capability = playlistWriteCapability ?: return@ProviderActionItem
                                            playlistWriteWorking = true
                                            actionError = null
                                            actionStatus = null
                                            scope.launch {
                                                runCatching {
                                                    capability.addTrackToPlaylist(actionTrack, playlist)
                                                }.onSuccess {
                                                    actionStatus = "已添加到歌单「${playlist.title}」"
                                                    page = ProviderSongActionPage.Main
                                                }.onFailure { failure ->
                                                    actionError = failure.message ?: "添加到歌单失败"
                                                }
                                                playlistWriteWorking = false
                                            }
                                        }
                                    }
                                }
                                ProviderActionItem("返回", "‹") { page = ProviderSongActionPage.Main }
                            }
                        }
                        }
                        if (target == ProviderSongActionPage.Main && identity.source == MusicSource.Bilibili) {
                            val persistedOffset = bilibiliOffsetState?.value ?: 0
                            var displayedOffset by remember(identity.value, persistedOffset) {
                                mutableIntStateOf(persistedOffset)
                            }
                            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("歌词调试延迟", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        when {
                                            displayedOffset == 0 -> "同步"
                                            displayedOffset > 0 -> "+${displayedOffset} ms · 歌词提前"
                                            else -> "$displayedOffset ms · 歌词延后"
                                        },
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                                        fontSize = 12.sp,
                                    )
                                }
                                MeloXLiquidSlider(
                                    value = displayedOffset.toFloat(),
                                    onTransientValueChange = { raw ->
                                        displayedOffset = raw.toInt()
                                    },
                                    onValueChange = { quantized ->
                                        displayedOffset = quantized.toInt()
                                        BilibiliLyricOffsetStore.write(context, identity.value, quantized.toInt())
                                    },
                                    valueRange = -5_000f..5_000f,
                                    stepSize = 100f,
                                    visibilityThreshold = 1f,
                                    contentDescription = "歌词调试延迟",
                                )
                            }
                        }
                        ProviderActionStatus(actionStatus, actionError)
        }
    }
}

}

@Composable
private fun ProviderActionStatus(
    status: String?,
    error: String?,
) {
    status?.let { message ->
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
    error?.let { message ->
        Text(
            message,
            color = Color(0xFFFF8A80),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ProviderActionHeader(
    state: MeloXPlaybackUiState,
    subtitle: String,
) {
    val foreground = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = state.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                state.title.ifBlank { "正在播放" },
                color = foreground,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state.artist.ifBlank { subtitle },
                color = foreground.copy(alpha = 0.58f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = foreground.copy(alpha = 0.38f),
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProviderActionItem(
    title: String,
    symbol: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val foreground = MaterialTheme.colorScheme.onSurface
    MeloXIosListRow(
        title = title,
        leading = {
            MeloXActionIcon(
                token = symbol,
                color = foreground.copy(alpha = if (enabled) .82f else .31f),
                enabled = enabled,
                modifier = Modifier.size(22.dp),
            )
        },
        onClick = if (enabled) onClick else null,
        showTopSeparator = true,
    )
}

private fun shareProviderSong(
    context: Context,
    state: MeloXPlaybackUiState,
    identity: MusicResourceId,
) {
    val providerUrl = when (identity.source) {
        MusicSource.QQMusic -> "https://y.qq.com/n/ryqq/songDetail/${identity.value}"
        MusicSource.Kugou,
        MusicSource.Kuwo,
        MusicSource.Netease -> null
        MusicSource.AppleMusic -> "https://music.apple.com/song/${identity.value}"
        MusicSource.Bilibili -> identity.value.substringBefore(':').takeIf(String::isNotBlank)?.let {
            "https://www.bilibili.com/video/$it"
        }
        MusicSource.Spotify -> "https://open.spotify.com/track/${identity.value}"
    }
    val text = buildString {
        append(state.title.ifBlank { "正在播放" })
        if (state.artist.isNotBlank()) append(" · ").append(state.artist)
        providerUrl?.let { append('\n').append(it) }
    }
    val intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        "分享歌曲",
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
