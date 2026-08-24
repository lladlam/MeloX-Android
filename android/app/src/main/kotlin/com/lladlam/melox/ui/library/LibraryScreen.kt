package com.lladlam.melox.ui.library

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.R
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.download.MeloXDownloadPlaylistRef
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.library.NeteaseLibraryCache
import com.lladlam.melox.core.library.NeteaseLibrarySnapshot
import com.lladlam.melox.core.library.NeteasePlaylistDetail
import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.core.recommendation.LocalRecommendationStore
import com.lladlam.melox.playback.ProviderPlaybackCommands
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.model.MusicAccountSummary
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.provider.MeloXLegacyUiBridge
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.core.music.provider.PlaylistCapability
import com.lladlam.melox.core.music.provider.UserLibraryCapability
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.glass.meloXLiquidBottomBar
import com.lladlam.melox.ui.glass.MeloXActionIcon
import com.lladlam.melox.ui.glass.MeloXSwipeAction
import com.lladlam.melox.ui.glass.MeloXSwipeActionRow
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXShapes
import com.lladlam.melox.ui.glass.MeloXTypography
import com.lladlam.melox.ui.glass.meloXContentSurface
import com.lladlam.melox.ui.glass.MeloXIosTopBar
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.glass.meloXLiquidTabSelection
import com.lladlam.melox.ui.player.MeloXFlowingLightBackdrop
import com.lladlam.melox.ui.player.MeloXSongActionsOverlay
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXSwipeFullAction
import com.lladlam.melox.ui.layout.rememberMeloXWindowInfo
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import com.lladlam.melox.ui.theme.isMeloXDarkTheme
import com.lladlam.melox.ui.podcast.MeloXPodcastScreen
import com.lladlam.melox.ui.cloud.MeloXCloudMusicScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class MeloXLibraryPage(val title: String) {
    Songs("歌曲"),
    Playlists("歌单"),
    Podcasts("播客"),
    Cloud("云盘"),
    History("最近播放"),
    Downloads("下载"),
}

/**
 * Presentation capability gate only. Provider-specific differences are kept out
 * of the renderer: unsupported product sections are simply absent while the
 * same MeloX transitions/backgrounds remain active.
 */
private fun MeloXLibraryPage.isEnabled(source: MusicSource): Boolean = when {
    source == MusicSource.Bilibili -> this == MeloXLibraryPage.Playlists || this == MeloXLibraryPage.Downloads
    source != MusicSource.Netease -> this == MeloXLibraryPage.Playlists
    this == MeloXLibraryPage.Podcasts -> MeloXSettingsRuntime.podcastsEnabled && MeloXSettingsRuntime.podcastsLibraryPlacement
    this == MeloXLibraryPage.History -> MeloXSettingsRuntime.listeningHistoryEnabled
    this == MeloXLibraryPage.Cloud -> MeloXSettingsRuntime.cloudMusicEnabled && MeloXSettingsRuntime.cloudLibraryPlacement
    this == MeloXLibraryPage.Downloads -> MeloXSettingsRuntime.downloadsEnabled && MeloXSettingsRuntime.downloadsLibraryPlacement
    else -> true
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    session: NeteaseSessionStore,
    onLogin: (() -> Unit)?,
    source: MusicSource = MusicSource.Netease,
    forcedPageName: String? = null,
    playlistBackEnabled: Boolean = true,
    onModalVisibilityChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val window = rememberMeloXWindowInfo()
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val client = remember(appContext) {
        NeteaseLibraryClient(
            cookieProvider = { NeteaseSessionStore.readCookie(appContext) },
        )
    }
    val provider = remember(source, appContext) {
        if (source == MusicSource.Netease) null else MeloXMusicProviders.create(appContext).require(source)
    }
    val providerLibrary = provider as? UserLibraryCapability
    val cache = remember(appContext) { NeteaseLibraryCache(appContext) }
    val downloadStore = remember(appContext) { MeloXDownloadStore.get(appContext) }
    val libraryPagePreferenceKey = if (source == MusicSource.Netease) {
        "library_last_page"
    } else {
        "library_last_page_${source.storageValue}"
    }

    val initialLibraryPage = remember(source, forcedPageName) {
        val fallback = if (source == MusicSource.Netease) MeloXLibraryPage.Songs else MeloXLibraryPage.Playlists
        val name = forcedPageName ?: if (MeloXSettingsRuntime.rememberLibraryPage) {
            MeloXSettingsPreferences.string(appContext, libraryPagePreferenceKey, fallback.name)
        } else fallback.name
        runCatching { MeloXLibraryPage.valueOf(name) }
            .getOrDefault(fallback)
            .takeIf { forcedPageName != null || it.isEnabled(source) }
            ?: fallback
    }
    var selectedPage by remember(source, forcedPageName) { mutableStateOf(initialLibraryPage) }
    var selectedPlaylist by remember(source, session.cookie) { mutableStateOf<NeteasePlaylistSummary?>(null) }
    var showLocalRecommendations by remember(source) { mutableStateOf(false) }
    var snapshot by remember(source, session.cookie) { mutableStateOf<NeteaseLibrarySnapshot?>(null) }
    var providerAccount by remember(source) { mutableStateOf<MusicAccountSummary?>(null) }
    var loading by remember(source, session.cookie) { mutableStateOf(source != MusicSource.Netease) }
    var errorMessage by remember(source, session.cookie) { mutableStateOf<String?>(null) }
    val playlistListState = rememberLazyListState()

    suspend fun refreshLibrary() {
        loading = true
        errorMessage = null
        if (source == MusicSource.Netease) {
            if (!session.isLoggedIn) {
                loading = false
                return
            }
            if (session.profile == null) session.refreshProfile(force = true)
            val userId = session.profile?.userId
            if (userId == null) {
                loading = false
                return
            }
            runCatching { client.snapshot(userId) }
                .onSuccess {
                    snapshot = it
                    cache.saveSnapshot(userId, it)
                }
                .onFailure { errorMessage = it.message ?: "音乐库加载失败" }
        } else {
            val capability = providerLibrary
            if (capability == null) {
                providerAccount = null
                snapshot = null
                errorMessage = "${source.displayName} 当前没有提供个人音乐库能力"
                loading = false
                return
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val account = capability.accountSummary()
                    val playlists = if (account != null && provider is PlaylistCapability) {
                        capability.userPlaylists(page = 1, pageSize = 100).items
                    } else {
                        emptyList()
                    }
                    account to playlists
                }
            }.onSuccess { (account, playlists) ->
                providerAccount = account
                snapshot = if (account == null) null else MeloXLegacyUiBridge.library(playlists)
            }.onFailure { failure ->
                providerAccount = null
                snapshot = null
                errorMessage = failure.message ?: "${source.displayName} 音乐库加载失败"
            }
        }
        loading = false
    }

    LaunchedEffect(source, session.cookie, session.profile?.userId) {
        if (source == MusicSource.Netease) {
            val userId = session.profile?.userId ?: return@LaunchedEffect
            cache.loadSnapshot(userId)?.let { snapshot = it }
            if (NeteaseLibraryCache.beginLibraryColdStartRefresh(userId)) {
                refreshLibrary()
            }
        } else {
            refreshLibrary()
        }
    }

    LaunchedEffect(source, selectedPage) {
        if (forcedPageName == null && MeloXSettingsRuntime.rememberLibraryPage) {
            MeloXSettingsPreferences.setString(appContext, libraryPagePreferenceKey, selectedPage.name)
        }
    }

    LaunchedEffect(
        source,
        MeloXSettingsRuntime.podcastsEnabled,
        MeloXSettingsRuntime.listeningHistoryEnabled,
        MeloXSettingsRuntime.cloudMusicEnabled,
        MeloXSettingsRuntime.downloadsEnabled,
    ) {
        if (forcedPageName == null && !selectedPage.isEnabled(source)) {
            selectedPage = if (source == MusicSource.Netease) MeloXLibraryPage.Songs else MeloXLibraryPage.Playlists
        }
    }

    BackHandler(enabled = playlistBackEnabled && selectedPlaylist != null) {
        selectedPlaylist = null
    }

    if (source == MusicSource.Netease && !session.isLoggedIn) {
        MeloXLibraryLoginUnavailable(onLogin, source)
        return
    }
    if (source != MusicSource.Netease && !loading && providerAccount == null && errorMessage == null) {
        MeloXLibraryLoginUnavailable(onLogin, source)
        return
    }

    PullToRefreshBox(
        isRefreshing = loading && snapshot != null,
        onRefresh = { scope.launch { refreshLibrary() } },
        modifier = Modifier.fillMaxSize(),
    ) {
      SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
          val sharedScope = this

        AnimatedContent(
            targetState = selectedPlaylist,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val openingDetail = targetState != null
                (
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = 320,
                            delayMillis = 55,
                            easing = FastOutSlowInEasing,
                        ),
                    ) togetherWith fadeOut(
                        animationSpec = tween(
                            durationMillis = 360,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                ).apply {
                    // The newest target must stay above any retained outgoing
                    // playlist content while an interrupted reverse animation finishes.
                    targetContentZIndex = if (openingDetail) 2f else 0f
                }
            },
            contentKey = { playlist -> playlist?.id ?: Long.MIN_VALUE },
            label = "library-playlist-detail-transition",
        ) { targetPlaylist ->
            val playlistTransitionVisibilityScope = this
            if (targetPlaylist != null) {
                MeloXPlaylistDetailScreen(
                    initialPlaylist = targetPlaylist,
                    client = client,
                    onBack = { selectedPlaylist = null },
                    sharedTransitionScope = sharedScope,
                    animatedVisibilityScope = playlistTransitionVisibilityScope,
                    onModalVisibilityChanged = onModalVisibilityChanged,
                    onSongLikeChanged = { song, liked ->
                        val current = snapshot ?: return@MeloXPlaylistDetailScreen
                        val updated = current.withSongLiked(song, liked)
                        snapshot = updated
                        session.profile?.userId?.let { userId ->
                            scope.launch { cache.saveSnapshot(userId, updated) }
                        }
                    },
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .padding(horizontal = if (window.supportsTwoPane) window.gutter else 0.dp),
                ) {
                    MeloXIosTopBar(
                        title = stringResource(R.string.tab_library),
                    )

                    MeloXLibrarySegmentedPicker(
                        selected = selectedPage,
                        onSelected = { selectedPage = it },
                        source = source,
                        forcedPageName = forcedPageName,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )

                    if (errorMessage != null && snapshot == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = errorMessage.orEmpty(),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = "重新载入",
                                    modifier = Modifier
                                        .padding(top = 12.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable { scope.launch { refreshLibrary() } }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    } else if (loading && snapshot == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val data = snapshot ?: NeteaseLibrarySnapshot(emptyList(), emptyList(), emptyList())
                        when (selectedPage) {
                            MeloXLibraryPage.Songs -> MeloXLibrarySongsPage(
                                songs = data.likedSongs,
                                onPlay = { song ->
                                    PlaybackCommands.playQueue(
                                        context = context,
                                        songs = data.likedSongs,
                                        selectedSongId = song.id,
                                        onFailure = { errorMessage = it.message ?: "播放失败" },
                                    )
                                },
                                onPlayAll = {
                                    data.likedSongs.firstOrNull()?.let { first ->
                                        PlaybackCommands.playQueue(
                                            context = context,
                                            songs = data.likedSongs,
                                            selectedSongId = first.id,
                                            onFailure = { errorMessage = it.message ?: "播放失败" },
                                        )
                                    }
                                },
                                onHeartMode = if (source == MusicSource.Netease) {
                                    {
                                        val seed = data.likedSongs.randomOrNull()
                                        val playlistId = data.likedPlaylistId
                                        if (seed != null && playlistId != null) scope.launch {
                                            runCatching { client.intelligenceModeSongs(seed.id, playlistId) }
                                                .onSuccess { songs -> songs.firstOrNull()?.let { PlaybackCommands.playQueue(context, songs, it.id, heartMode = true) } }
                                                .onFailure { errorMessage = it.message ?: "无法启动心动模式" }
                                        }
                                    }
                                } else null,
                            )

                            MeloXLibraryPage.Playlists -> MeloXLibraryPlaylistsPage(
                                playlists = data.playlists,
                                localRecommendations = LocalRecommendationStore.readRecommendedTracks(context),
                                onLocalRecommendationsClick = { showLocalRecommendations = true },
                                onPlaylistClick = { selectedPlaylist = it },
                                listState = playlistListState,
                                sharedTransitionScope = sharedScope,
                                animatedVisibilityScope = playlistTransitionVisibilityScope,
                            )

                            MeloXLibraryPage.Podcasts -> MeloXPodcastScreen(subscriptionsOnly = true)

                            MeloXLibraryPage.Cloud -> Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            ) {
                                MeloXCloudMusicScreen(embedded = true)
                            }

                            MeloXLibraryPage.History -> MeloXLibrarySongsPage(
                                songs = data.recentSongs,
                                onPlay = { song ->
                                    PlaybackCommands.playQueue(
                                        context = context, songs = data.recentSongs, selectedSongId = song.id,
                                        onFailure = { errorMessage = it.message ?: "播放失败" },
                                    )
                                },
                                onPlayAll = {
                                    data.recentSongs.firstOrNull()?.let { first ->
                                        PlaybackCommands.playQueue(
                                            context = context, songs = data.recentSongs, selectedSongId = first.id,
                                            onFailure = { errorMessage = it.message ?: "播放失败" },
                                        )
                                    }
                                },
                            )

                            MeloXLibraryPage.Downloads -> MeloXLibraryDownloadsPage(downloadStore)
                        }
                    }
                }
            }
        }
      }
    }
    if (showLocalRecommendations) {
        LocalRecommendationPlaylistScreen(
            onBack = { showLocalRecommendations = false },
        )
    }
}

private enum class MeloXDownloadsPage { Root, Active, Playlists, PlaylistDetail }
private enum class MeloXLocalBrowseMode(val title: String) {
    Songs("歌曲"), Artists("艺术家"), Albums("专辑"), Folders("文件夹")
}

@Composable
private fun MeloXLibraryDownloadsPage(downloads: MeloXDownloadStore) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(MeloXDownloadsPage.Root) }
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var browseMode by remember { mutableStateOf(MeloXLocalBrowseMode.Songs) }
    var browseGroup by remember { mutableStateOf<String?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    val active = downloads.activeDownloads.values.toList()
    val completed = downloads.downloads.toList()
    val groups = downloads.downloadedPlaylists
    val browseGroups = remember(completed, browseMode) {
        when (browseMode) {
            MeloXLocalBrowseMode.Songs -> emptyMap()
            MeloXLocalBrowseMode.Artists -> completed.groupBy { it.song.artists.ifBlank { "未知艺术家" } }
            MeloXLocalBrowseMode.Albums -> completed.groupBy { it.song.album.ifBlank { "未知专辑" } }
            MeloXLocalBrowseMode.Folders -> mapOf("Music/MeloX" to completed)
        }.toSortedMap()
    }
    val visibleCompleted = remember(completed, browseMode, browseGroup, browseGroups) {
        if (browseMode == MeloXLocalBrowseMode.Songs) completed
        else browseGroup?.let { browseGroups[it].orEmpty() }.orEmpty()
    }

    fun exportSelected() {
        if (selectedIds.isEmpty()) return
        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            (context as? Activity)?.let {
                ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 4104)
            }
            exportMessage = "请授予存储权限后再次导出"
            return
        }
        downloads.exportToMusicLibrary(selectedIds) { result ->
            exportMessage = result.fold(
                onSuccess = { "已导出 $it 首到 Music/MeloX" },
                onFailure = { it.message ?: "导出失败" },
            )
        }
    }

    BackHandler(enabled = page != MeloXDownloadsPage.Root) {
        page = if (page == MeloXDownloadsPage.PlaylistDetail) MeloXDownloadsPage.Playlists else MeloXDownloadsPage.Root
        if (page != MeloXDownloadsPage.PlaylistDetail) selectedPlaylistId = null
    }

    when (page) {
        MeloXDownloadsPage.Root -> LazyColumn(
  modifier = Modifier.fillMaxSize(),
  contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 146.dp),
  verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
  if (active.isNotEmpty()) {
      item {
          DownloadNavigationCard(
              title = "正在下载",
              subtitle = "${formatDownloadSpeed(downloads.aggregateDownloadBytesPerSecond)} · 剩余 ${active.size} 首未完成",
              onClick = { page = MeloXDownloadsPage.Active },
          )
      }
  }
  if (completed.isNotEmpty()) {
      item {
          Row(
              Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
              Text("已下载", fontSize = 20.sp, fontWeight = FontWeight.Bold)
              Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                  if (selecting) {
                      Text(
                          if (selectedIds.size == completed.size) "取消全选" else "全选",
                          color = MaterialTheme.colorScheme.primary,
                          fontWeight = FontWeight.SemiBold,
                          modifier = Modifier.clickable {
                              selectedIds = if (selectedIds.size == completed.size) emptySet()
                                  else completed.map { it.song.id }.toSet()
                          },
                      )
                      Text(
                          "取消",
                          color = MaterialTheme.colorScheme.primary,
                          fontWeight = FontWeight.SemiBold,
                          modifier = Modifier.clickable {
                              selecting = false
                              selectedIds = emptySet()
                          },
                      )
                  } else {
                      Text(
                          "多选",
                          color = MaterialTheme.colorScheme.primary,
                          fontWeight = FontWeight.SemiBold,
                          modifier = Modifier.clickable { selecting = true },
                      )
                      Text(
                          "播放全部",
                          color = MaterialTheme.colorScheme.primary,
                          fontWeight = FontWeight.SemiBold,
                          modifier = Modifier.clickable {
                              downloads.downloadedSongs.firstOrNull()?.let {
                                  PlaybackCommands.playQueue(context, downloads.downloadedSongs, it.id)
                              }
                          },
                      )
                  }
              }
          }
      }
      item {
          Row(
              Modifier.fillMaxWidth().padding(bottom = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
              MeloXLocalBrowseMode.entries.forEach { mode ->
                  Text(
                      mode.title,
                      color = if (browseMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                      fontWeight = if (browseMode == mode) FontWeight.Bold else FontWeight.Medium,
                      modifier = Modifier
                          .clip(RoundedCornerShape(14.dp))
                          .background(MaterialTheme.colorScheme.onBackground.copy(alpha = if (browseMode == mode) .10f else .04f))
                          .clickable {
                              browseMode = mode
                              browseGroup = null
                              selectedIds = emptySet()
                          }
                          .padding(horizontal = 12.dp, vertical = 7.dp),
                  )
              }
          }
      }
      exportMessage?.let { value ->
          item { Text(value, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)) }
      }
  if (groups.isNotEmpty()) {
      item {
          DownloadNavigationCard(
              title = "已下载歌单",
              subtitle = "${groups.size} 个歌单",
              onClick = { page = MeloXDownloadsPage.Playlists },
          )
      }
  }
      if (browseMode != MeloXLocalBrowseMode.Songs && browseGroup == null) {
          items(browseGroups.entries.toList(), key = { "browse-${browseMode.name}-${it.key}" }) { group ->
              DownloadNavigationCard(
                  title = group.key,
                  subtitle = "${group.value.size} 首歌曲",
                  onClick = { browseGroup = group.key },
              )
          }
      } else if (browseMode != MeloXLocalBrowseMode.Songs) {
          item { DownloadsSubpageHeader(browseGroup.orEmpty()) { browseGroup = null } }
      }
      items(visibleCompleted, key = { "download-${it.song.id}" }) { item ->
          val checked = item.song.id in selectedIds
          Row(
              Modifier
                  .fillMaxWidth()
                  .height(62.dp)
                  .clickable {
                      if (selecting) {
                          selectedIds = if (checked) selectedIds - item.song.id else selectedIds + item.song.id
                      } else {
                          PlaybackCommands.playQueue(context, downloads.downloadedSongs, item.song.id)
                      }
                  },
              verticalAlignment = Alignment.CenterVertically,
          ) {
              AsyncImage(
                  model = downloads.localArtworkUri(item.song.id) ?: item.song.artworkUrl,
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)),
              )
              Column(Modifier.weight(1f).padding(start = 12.dp)) {
                  Text(item.song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                  Text(
                      "${item.song.artists} · ${item.quality.title}",
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f),
                      fontSize = 12.sp,
                  )
              }
              if (selecting) {
                  Text(if (checked) "✓" else "○", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, modifier = Modifier.padding(10.dp))
              } else {
                  Text("删除", color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { downloads.remove(item.song.id) }.padding(10.dp))
              }
          }
      }
      if (selecting) {
          item {
              val canDelete = selectedIds.isNotEmpty()
              Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  Box(
                      Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(18.dp))
                          .background(MaterialTheme.colorScheme.primary.copy(alpha = if (canDelete) .14f else .05f))
                          .clickable(enabled = canDelete) { exportSelected() },
                      contentAlignment = Alignment.Center,
                  ) {
                      Text("导出已选", color = MaterialTheme.colorScheme.primary.copy(alpha = if (canDelete) 1f else .4f), fontWeight = FontWeight.SemiBold)
                  }
                  Box(
                      Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(18.dp))
                          .background(MaterialTheme.colorScheme.error.copy(alpha = if (canDelete) .14f else .05f))
                          .clickable(enabled = canDelete) {
                              downloads.removeMany(selectedIds)
                              selectedIds = emptySet()
                              selecting = false
                          },
                      contentAlignment = Alignment.Center,
                  ) {
                      Text(
                          if (canDelete) "删除 ${selectedIds.size} 首" else "请选择歌曲",
                          color = MaterialTheme.colorScheme.error.copy(alpha = if (canDelete) 1f else .4f),
                          fontWeight = FontWeight.SemiBold,
                      )
                  }
              }
          }
      }
  }

  if (active.isEmpty() && completed.isEmpty()) {
      item {
          Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text("还没有下载歌曲", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                  Text(
                      "在歌曲的更多操作菜单中选择“下载歌曲”。",
                      modifier = Modifier.padding(top = 7.dp),
                      color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f),
                      fontSize = 13.sp,
                  )
              }
          }
      }
  }
        }

        MeloXDownloadsPage.Active -> LazyColumn(
  modifier = Modifier.fillMaxSize(),
  contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 146.dp),
  verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
  item { DownloadsSubpageHeader("正在下载") { page = MeloXDownloadsPage.Root } }
  item {
      Text(
          "${formatDownloadSpeed(downloads.aggregateDownloadBytesPerSecond)} · 剩余 ${active.size} 首未完成",
          color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
          fontSize = 13.sp,
          modifier = Modifier.padding(bottom = 10.dp),
      )
  }
  items(active, key = { "active-${it.song.id}" }) { item ->
      Row(Modifier.fillMaxWidth().height(66.dp), verticalAlignment = Alignment.CenterVertically) {
          AsyncImage(
              model = item.song.artworkUrl,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)),
          )
          Column(Modifier.weight(1f).padding(start = 12.dp)) {
              Text(item.song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
              val progress = item.fractionCompleted?.let { "${(it * 100).toInt()}%" } ?: "准备中"
              Text(
                  "$progress · ${formatDownloadSpeed(item.bytesPerSecond)} · ${item.quality.title}",
                  color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f),
                  fontSize = 12.sp,
              )
          }
          Text("取消", color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { downloads.cancel(item.song.id) }.padding(10.dp))
      }
  }
  if (active.isEmpty()) {
      item { Text("当前没有正在下载的歌曲", color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f), modifier = Modifier.padding(top = 24.dp)) }
  }
        }

        MeloXDownloadsPage.Playlists -> LazyColumn(
  modifier = Modifier.fillMaxSize(),
  contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 146.dp),
  verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
  item { DownloadsSubpageHeader("已下载歌单") { page = MeloXDownloadsPage.Root } }
  items(groups, key = { "download-playlist-${it.playlist.id}" }) { group ->
      Row(
          Modifier.fillMaxWidth().height(68.dp).clickable {
              selectedPlaylistId = group.playlist.id
              page = MeloXDownloadsPage.PlaylistDetail
          },
          verticalAlignment = Alignment.CenterVertically,
      ) {
          AsyncImage(
              model = downloads.localPlaylistArtworkUri(group.playlist.id) ?: group.playlist.artworkUrl,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
          )
          Column(Modifier.weight(1f).padding(start = 12.dp)) {
              Text(group.playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
              Text("已下载 ${group.songs.size} 首", color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f), fontSize = 12.sp)
          }
          MeloXActionIcon("›", Modifier.size(18.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .4f))
      }
  }
        }

        MeloXDownloadsPage.PlaylistDetail -> {
  val group = groups.firstOrNull { it.playlist.id == selectedPlaylistId }
  val songs = group?.songs?.map { it.song }.orEmpty()
  LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 146.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
      item { DownloadsSubpageHeader(group?.playlist?.name ?: "已下载歌单") { page = MeloXDownloadsPage.Playlists } }
      group?.let { existing ->
          item {
              Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                  AsyncImage(
                      model = downloads.localPlaylistArtworkUri(existing.playlist.id) ?: existing.playlist.artworkUrl,
                      contentDescription = null,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                  )
                  Column(Modifier.weight(1f).padding(start = 12.dp)) {
                      Text(existing.playlist.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      Text("${songs.size} 首已下载歌曲", color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f), fontSize = 12.sp)
                  }
                  Text(
                      "播放全部",
                      color = MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.SemiBold,
                      modifier = Modifier.clickable {
                          songs.firstOrNull()?.let { PlaybackCommands.playQueue(context, songs, it.id) }
                      }.padding(8.dp),
                  )
              }
          }
      }
      items(group?.songs.orEmpty(), key = { "playlist-song-${it.song.id}" }) { item ->
          Row(
              Modifier.fillMaxWidth().height(62.dp).clickable {
                  PlaybackCommands.playQueue(context, songs, item.song.id)
              },
              verticalAlignment = Alignment.CenterVertically,
          ) {
              AsyncImage(
                  model = downloads.localArtworkUri(item.song.id) ?: item.song.artworkUrl,
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)),
              )
              Column(Modifier.weight(1f).padding(start = 12.dp)) {
                  Text(item.song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                  Text(item.song.artists, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f), fontSize = 12.sp)
              }
          }
      }
  }
        }
    }
}

@Composable
private fun DownloadNavigationCard(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).height(66.dp)
  .meloXContentSurface(
      shape = MeloXShapes.card,
      surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .055f),
  )
  .clickable(onClick = onClick)
  .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
  Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
  Text(subtitle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        MeloXActionIcon("›", Modifier.size(18.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .42f))
    }
}

@Composable
private fun DownloadsSubpageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            MeloXActionIcon("‹", Modifier.size(20.dp), MaterialTheme.colorScheme.onBackground)
        }
        Text(title, style = MeloXTypography.title2)
    }
}

private fun formatDownloadSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1024L * 1024L -> "%.1f MB/s".format(bytesPerSecond / (1024.0 * 1024.0))
    bytesPerSecond >= 1024L -> "%.0f KB/s".format(bytesPerSecond / 1024.0)
    bytesPerSecond > 0L -> "$bytesPerSecond B/s"
    else -> "0 KB/s"
}

@Composable
private fun MeloXLibraryLoginUnavailable(
    onLogin: (() -> Unit)?,
    source: MusicSource,
    forcedPageName: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        MeloXIosTopBar(
            title = stringResource(R.string.tab_library),
            contentPadding = PaddingValues(horizontal = 0.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("需要登录", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (source == MusicSource.Netease) {
                        "登录后可读取收藏歌曲、歌单和播放记录。"
                    } else {
                        "请先在设置中登录 ${source.displayName}，登录后可读取该平台提供的音乐库内容。"
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.50f),
                    textAlign = TextAlign.Center,
                )
                if (onLogin != null) {
                    MeloXGlassButton(
                        onClick = onLogin,
                        modifier = Modifier.padding(top = 18.dp),
                        style = MeloXGlassButtonStyle.BorderedProminent,
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Text(
                            if (source == MusicSource.Netease) "登录网易云音乐" else "前往登录 ${source.displayName}",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MeloXLibrarySegmentedPicker(
    selected: MeloXLibraryPage,
    onSelected: (MeloXLibraryPage) -> Unit,
    source: MusicSource,
    forcedPageName: String? = null,
    modifier: Modifier = Modifier,
) {
    val pages = MeloXLibraryPage.entries.filter { it.isEnabled(source) || it.name == forcedPageName }
    val panelShape = MeloXShapes.compact
    val lensShape = RoundedCornerShape(15.dp)
    val panelBackdrop = rememberLayerBackdrop()
    val dark = isMeloXDarkTheme()
    val selectedIndex = pages.indexOf(selected).coerceAtLeast(0)
    val lensPosition by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 1f,
            stiffness = 460f,
            visibilityThreshold = 0.001f,
        ),
        label = "library-segment-lens-position",
    )
    val panelTint = MaterialTheme.colorScheme.surface.copy(alpha = 0.10f)
    val panelSurface = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.055f)
    val selectionTint = if (dark) {
        Color.White.copy(alpha = 0.22f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(panelShape)
            .meloXLiquidBottomBar(
                shape = panelShape,
                tint = panelTint,
                surfaceColor = panelSurface,
            ),
    ) {
        val panelWidthPx = constraints.maxWidth

        Row(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0f)
                .layerBackdrop(panelBackdrop)
                .meloXLiquidBottomBar(
                    shape = panelShape,
                    tint = panelTint,
                    surfaceColor = panelSurface,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.forEach { page ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(page.title, fontSize = 13.sp)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(1f / pages.size)
                .fillMaxHeight()
                .offset {
                    IntOffset(
                        x = (lensPosition * panelWidthPx / pages.size).roundToInt(),
                        y = 0,
                    )
                }
                .padding(horizontal = 1.dp, vertical = 1.dp)
                .meloXLiquidTabSelection(
                    shape = lensShape,
                    selected = true,
                    tint = selectionTint,
                    panelBackdrop = panelBackdrop,
                ),
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.forEach { page ->
                val isSelected = page == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelected(page) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = page.title,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeloXLibrarySongsPage(
    songs: List<SearchSong>,
    onPlay: (SearchSong) -> Unit,
    onPlayAll: () -> Unit,
    onHeartMode: (() -> Unit)? = null,
) {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "暂无歌曲",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                fontSize = 17.sp,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MeloXBottomContentClearance),
    ) {
        item {
            MeloXPlayAllRow(onPlayAll)
            onHeartMode?.let { action ->
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clickable(onClick = action).padding(start = 20.dp, end = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    MeloXActionIcon("♥", Modifier.size(22.dp), Color(0xFFFF3B30))
                    Text("心动模式", fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            MeloXInsetDivider(leading = 68.dp)
        }
        items(songs, key = { it.id }) { song ->
            MeloXLibraryTrackRow(song = song, onClick = { onPlay(song) })
            MeloXInsetDivider(leading = 68.dp)
        }
    }
}

@Composable
private fun MeloXPlayAllRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MeloXPlayGlyph(
            modifier = Modifier.size(28.dp),
            color = Color(0xFFFF3147),
        )
        Text(
            text = "播放全部",
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun MeloXLibraryTrackRow(
    song: SearchSong,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = song.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = song.name,
                fontSize = 17.sp,
                lineHeight = 21.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = song.artists,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.46f),
            )
        }
        if (song.durationMs > 0L) {
            Text(
                text = formatDuration(song.durationMs),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.46f),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MeloXLibraryPlaylistsPage(
    playlists: List<NeteasePlaylistSummary>,
    localRecommendations: List<com.lladlam.melox.core.music.model.MusicTrack> = emptyList(),
    onLocalRecommendationsClick: () -> Unit = {},
    onPlaylistClick: (NeteasePlaylistSummary) -> Unit,
    listState: LazyListState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    if (playlists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "还没有收藏歌单",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                fontSize = 17.sp,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MeloXBottomContentClearance),
    ) {
        if (localRecommendations.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(66.dp).clickable(onClick = onLocalRecommendationsClick).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AsyncImage(localRecommendations.firstOrNull()?.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)))
                    Column(Modifier.weight(1f)) {
                        Text("MeloX 为你推荐", fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("本地算法 · ${localRecommendations.size} 首 · 只读", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f))
                    }
                    MeloXActionIcon("›", Modifier.size(18.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .4f))
                }
                MeloXInsetDivider(leading = 74.dp)
            }
        }
        item {
            Text(
                text = "歌单",
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.50f),
            )
        }
        items(playlists, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp)
                    .clickable { onPlaylistClick(playlist) }
                    .padding(start = 18.dp, end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val sharedArtworkModifier = with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState(
                            key = playlistArtworkSharedKey(playlist.id),
                        ),
                        animatedVisibilityScope = animatedVisibilityScope,
                        // The cover itself must stay in the shared overlay for
                        // the row -> detail flight. The detail screen clips
                        // its settled cover; only the transition uses this
                        // elevated layer.
                        renderInOverlayDuringTransition = true,
                        zIndexInOverlay = 1f,
                    )
                }

                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = sharedArtworkModifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        playlist.name,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${playlist.trackCount} 首歌曲",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                    )
                }
                MeloXActionIcon(
                    "›",
                    Modifier.size(18.dp),
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.40f),
                )
            }
            MeloXInsetDivider(leading = 84.dp)
        }
    }
}

@Composable
private fun LocalRecommendationPlaylistScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val tracks = LocalRecommendationStore.readRecommendedTracks(context)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        MeloXPlaylistToolbar(
            foreground = MaterialTheme.colorScheme.onBackground,
            onBack = onBack,
            onShare = {},
            showMore = false,
            onMore = {},
        )
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text("MeloX 为你推荐", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("本地规则与轻量模型生成 · 只读内部歌单 · ${tracks.size} 首", modifier = Modifier.padding(top = 5.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .54f))
        }
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无达到相似度阈值的推荐歌曲\n请继续播放、收藏或完成歌曲后重新分析", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
            }
            return
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = MeloXBottomContentClearance)) {
            itemsIndexed(tracks, key = { _, track -> "local-recommendation-${track.id.source.storageValue}-${track.id.value}" }) { index, track ->
                Row(Modifier.fillMaxWidth().clickable { ProviderPlaybackCommands.playQueue(context, tracks, track.id) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", Modifier.width(38.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .45f), textAlign = TextAlign.Center)
                    AsyncImage(track.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text("${track.artistText} · ${track.id.source.displayName}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MeloXInsetDivider(leading: androidx.compose.ui.unit.Dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = leading, end = 18.dp),
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
    )
}

/** Canonical playlist detail used by Library, Home, Explore, Search and account entry points. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MeloXUnifiedPlaylistDetailScreen(
    playlist: NeteasePlaylistSummary,
    onBack: () -> Unit,
    onModalVisibilityChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current.applicationContext
    val client = remember(context) {
        NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
    }
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = true) {
            MeloXPlaylistDetailScreen(
                initialPlaylist = playlist,
                client = client,
                onBack = onBack,
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this,
                onModalVisibilityChanged = onModalVisibilityChanged,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MeloXPlaylistDetailScreen(
    initialPlaylist: NeteasePlaylistSummary,
    client: NeteaseLibraryClient,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onModalVisibilityChanged: (Boolean) -> Unit,
    onSongLikeChanged: (SearchSong, Boolean) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val detailWindow = rememberMeloXWindowInfo()
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val cache = remember(appContext) { NeteaseLibraryCache(appContext) }
    val accountClient = remember(appContext) {
        NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) })
    }
    val operationsClient = remember(appContext) {
        NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) })
    }
    val providerPlaylist = initialPlaylist.providerPlaylist
    val providerPlaylistCapability = remember(providerPlaylist?.id?.source, appContext) {
        providerPlaylist?.let { backing ->
            MeloXMusicProviders.create(appContext).require(backing.id.source) as? PlaylistCapability
        }
    }
    val isProviderPlaylist = providerPlaylist != null
    var detail by remember(initialPlaylist.id) { mutableStateOf<NeteasePlaylistDetail?>(null) }
    var loading by remember(initialPlaylist.id) { mutableStateOf(true) }
    var errorMessage by remember(initialPlaylist.id) { mutableStateOf<String?>(null) }
    var searchQuery by remember(initialPlaylist.id) { mutableStateOf("") }
    var showPlaylistActions by remember(initialPlaylist.id) { mutableStateOf(false) }
    var showBatchDownload by remember(initialPlaylist.id) { mutableStateOf(false) }
    var selectedTrackAction by remember(initialPlaylist.id) { mutableStateOf<SearchSong?>(null) }
    var isSaved by remember(initialPlaylist.id) { mutableStateOf<Boolean?>(null) }
    var currentUserId by remember(initialPlaylist.id) { mutableStateOf<Long?>(null) }
    var savingPlaylist by remember(initialPlaylist.id) { mutableStateOf(false) }
    var palette by remember(initialPlaylist.coverUrl) { mutableStateOf(MeloXDetailPalette.LightFallback) }

    DisposableEffect(showPlaylistActions, showBatchDownload, selectedTrackAction) {
        val visible = !isProviderPlaylist && (showPlaylistActions || showBatchDownload || selectedTrackAction != null)
        onModalVisibilityChanged(visible)
        onDispose {
            if (visible) onModalVisibilityChanged(false)
        }
    }

    suspend fun refreshSavedState() {
        if (isProviderPlaylist) {
            isSaved = null
            return
        }
        val cookie = NeteaseSessionStore.readCookie(appContext)
        if (!NeteaseSessionStore.containsMusicU(cookie)) {
            isSaved = null
            return
        }
        runCatching {
            val profile = accountClient.accountProfile(cookie)
            currentUserId = profile.userId
            withContext(Dispatchers.IO) {
                client.userPlaylistsBlocking(profile.userId)
            }.any { it.id == initialPlaylist.id }
        }.onSuccess { isSaved = it }
    }

    suspend fun refreshPlaylist() {
        loading = true
        errorMessage = null
        if (providerPlaylist != null) {
            val capability = providerPlaylistCapability
            if (capability == null) {
                errorMessage = "${providerPlaylist.id.source.displayName} 当前不提供歌单详情能力"
                loading = false
                return
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    capability.playlistDetail(providerPlaylist, page = 1, pageSize = 150)
                }
            }.onSuccess { providerDetail ->
                detail = MeloXLegacyUiBridge.playlistDetail(providerDetail)
            }.onFailure { errorMessage = it.message ?: "歌单加载失败" }
        } else {
            runCatching { client.playlistDetail(initialPlaylist.id) }
                .onSuccess {
                    detail = it
                    cache.savePlaylistDetail(initialPlaylist.id, it)
                }
                .onFailure { errorMessage = it.message ?: "歌单加载失败" }
        }
        loading = false
    }

    LaunchedEffect(initialPlaylist.id, providerPlaylist?.id) {
        if (providerPlaylist != null) {
            refreshPlaylist()
        } else {
            cache.loadPlaylistDetail(initialPlaylist.id)?.let { detail = it }
            loading = detail == null
            if (NeteaseLibraryCache.beginPlaylistColdStartRefresh(initialPlaylist.id)) {
                refreshPlaylist()
            }
        }
    }

    LaunchedEffect(initialPlaylist.id, isProviderPlaylist) {
        refreshSavedState()
    }

    val displayed = detail?.summary ?: initialPlaylist
    val songs = detail?.songs.orEmpty()
    val ownedPlaylistId = displayed.id.takeIf {
        displayed.creatorUserId != null && displayed.creatorUserId == currentUserId
    }

    LaunchedEffect(displayed.coverUrl) {
        palette = MeloXDetailPaletteProvider.paletteFor(displayed.coverUrl)
    }

    val foreground = if (palette.prefersDarkAppearance) Color.White else Color.Black
    val secondary = foreground.copy(alpha = 0.48f)
    val filteredSongs = remember(songs, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) songs else songs.filter { song ->
            song.name.lowercase().contains(query) ||
                song.artists.lowercase().contains(query) ||
                song.album.lowercase().contains(query)
        }
    }

    PullToRefreshBox(
        isRefreshing = loading && detail != null,
        onRefresh = { scope.launch { refreshPlaylist() } },
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Keep the exact MeloX artwork-driven background renderer for every source.
        MeloXFlowingLightBackdrop(
            artworkUrl = displayed.coverUrl,
            isPlaying = false,
            modifier = Modifier.fillMaxSize(),
        )
        // The animated backdrop can drift darker or lighter than the source
        // artwork used by the palette sampler. Add Apple's legibility layer so
        // the chosen foreground remains readable throughout that motion.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (palette.prefersDarkAppearance) {
                        Color.Black.copy(alpha = 0.26f)
                    } else {
                        Color.White.copy(alpha = 0.46f)
                    },
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            MeloXPlaylistToolbar(
                foreground = foreground,
                onBack = onBack,
                onShare = { sharePlaylistFromDetail(context, displayed) },
                showMore = !isProviderPlaylist,
                onMore = { showPlaylistActions = true },
            )
            MeloXPlaylistSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                foreground = foreground,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(if (detailWindow.supportsTwoPane) 2 else 1),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = if (detailWindow.supportsTwoPane) detailWindow.gutter else 0.dp,
                    end = if (detailWindow.supportsTwoPane) detailWindow.gutter else 0.dp,
                    bottom = MeloXBottomContentClearance,
                ),
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    MeloXStandardPlaylistHero(
                        playlist = displayed,
                        tracks = songs,
                        foreground = foreground,
                        secondary = secondary,
                        sourceLabel = displayed.providerPlaylist?.id?.source?.displayName ?: "网易云音乐",
                        onPlay = {
                            songs.firstOrNull()?.let { first ->
                                PlaybackCommands.playQueue(
                                    context = context,
                                    songs = songs,
                                    selectedSongId = first.id,
                                    onFailure = { errorMessage = it.message ?: "播放失败" },
                                )
                            }
                        },
                        onShuffle = {
                            val shuffled = songs.shuffled()
                            shuffled.firstOrNull()?.let { first ->
                                PlaybackCommands.playQueue(
                                    context = context,
                                    songs = shuffled,
                                    selectedSongId = first.id,
                                    onFailure = { errorMessage = it.message ?: "播放失败" },
                                )
                            }
                        },
                        isSaved = isSaved == true,
                        showSaveAction = !isProviderPlaylist,
                        onToggleSaved = {
                            if (!isProviderPlaylist && !savingPlaylist) {
                                val desired = isSaved != true
                                savingPlaylist = true
                                scope.launch {
                                    runCatching {
                                        operationsClient.setPlaylistSubscribed(displayed.id, desired)
                                    }.onSuccess {
                                        isSaved = desired
                                    }.onFailure {
                                        errorMessage = it.message ?: "歌单收藏操作失败"
                                    }
                                    savingPlaylist = false
                                }
                            }
                        },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }

                when {
                    loading && songs.isEmpty() -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = foreground)
                        }
                    }
                    errorMessage != null && songs.isEmpty() -> item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                errorMessage.orEmpty(),
                                color = secondary,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "重试",
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .clickable {
                                        scope.launch {
                                            refreshPlaylist()
                                        }
                                    }
                                    .padding(8.dp),
                                color = foreground,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    filteredSongs.isEmpty() -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("暂无歌曲", color = secondary)
                        }
                    }
                    else -> gridItemsIndexed(
                        items = filteredSongs,
                        key = { _, song -> song.id },
                    ) { index, song ->
                        MeloXPlaylistTrackRow(
                            song = song,
                            index = index,
                            foreground = foreground,
                            showMore = !isProviderPlaylist,
                            onClick = {
                                PlaybackCommands.playQueue(
                                    context = context,
                                    songs = filteredSongs,
                                    selectedSongId = song.id,
                                    onFailure = { errorMessage = it.message ?: "播放失败" },
                                )
                            },
                            onMore = { selectedTrackAction = song },
                            onPlayNext = { PlaybackCommands.playNext(context, song) },
                            onPlayLast = { PlaybackCommands.addToQueue(context, song) },
                            endAction = if (isProviderPlaylist) null else if (ownedPlaylistId != null) {
                                MeloXSwipeAction("从歌单移除", MeloXSymbol.Trash, Color(0xFFFF3B30)) {
                                    scope.launch {
                                        runCatching { operationsClient.removeSongFromPlaylist(song.id, ownedPlaylistId) }
                                            .onSuccess { refreshPlaylist() }
                                            .onFailure { errorMessage = it.message ?: "移除歌曲失败" }
                                    }
                                }
                            } else {
                                MeloXSwipeAction("添加到资料库", MeloXSymbol.Heart, Color(0xFFFF3B30)) {
                                    scope.launch {
                                        runCatching { operationsClient.setSongLiked(song.id, true) }
                                            .onSuccess {
                                                currentUserId?.let { userId ->
                                                    cache.loadSnapshot(userId)?.let { cached ->
                                                        cache.saveSnapshot(userId, cached.withSongLiked(song, true))
                                                    }
                                                }
                                                onSongLikeChanged(song, true)
                                            }
                                            .onFailure { errorMessage = it.message ?: "添加到资料库失败" }
                                    }
                                }
                            },
                        )
                        if (song.id != filteredSongs.lastOrNull()?.id) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 66.dp, end = 20.dp),
                                thickness = 0.6.dp,
                                color = foreground.copy(alpha = 0.12f),
                            )
                        }
                    }
                }
            }
        }

        if (!isProviderPlaylist) {
            MeloXPlaylistActionsOverlay(
                playlist = displayed,
                visible = showPlaylistActions,
                onDismiss = { showPlaylistActions = false },
                onRefresh = { scope.launch { refreshPlaylist() } },
                onBatchDownload = { showBatchDownload = true },
            )
            MeloXBatchDownloadSheet(
                songs = songs,
                sourcePlaylist = MeloXDownloadPlaylistRef(
                    id = displayed.id,
                    name = displayed.name,
                    artworkUrl = displayed.coverUrl,
                ),
                visible = showBatchDownload,
                onDismiss = { showBatchDownload = false },
            )
            val actionSong = selectedTrackAction
            if (actionSong != null) {
                MeloXSongActionsOverlay(
                    song = actionSong,
                    queue = songs,
                    visible = true,
                    onDismiss = { selectedTrackAction = null },
                    sourcePlaylist = MeloXDownloadPlaylistRef(
                        id = displayed.id,
                        name = displayed.name,
                        artworkUrl = displayed.coverUrl,
                    ),
                    sourceOwnedPlaylistId = ownedPlaylistId,
                    onSourcePlaylistChanged = {
                        selectedTrackAction = null
                        scope.launch { refreshPlaylist() }
                    },
                )
            }
        }
    }
}

private fun NeteaseLibrarySnapshot.withSongLiked(song: SearchSong, liked: Boolean): NeteaseLibrarySnapshot =
    copy(
        likedSongs = if (liked) {
            listOf(song) + likedSongs.filterNot { it.id == song.id }
        } else {
            likedSongs.filterNot { it.id == song.id }
        },
    )

@Composable
private fun MeloXPlaylistToolbar(
    foreground: Color,
    onBack: () -> Unit,
    onShare: () -> Unit,
    showMore: Boolean = true,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MeloXGlassCircleButton(
            foreground = foreground,
            size = 44.dp,
            onClick = onBack,
        ) {
            MeloXBackGlyph(Modifier.size(22.dp), foreground)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MeloXGlassCircleButton(
                foreground = foreground,
                size = 44.dp,
                onClick = onShare,
            ) {
                MeloXShareGlyph(Modifier.size(22.dp), Color(0xFFFF3147))
            }
            if (showMore) {
                MeloXGlassCircleButton(
                    foreground = foreground,
                    size = 44.dp,
                    onClick = onMore,
                ) {
                    Text(
                        "•••",
                        color = Color(0xFFFF3147),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeloXPlaylistSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(glassColor(foreground)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeloXSearchGlyph(
            modifier = Modifier
                .padding(start = 14.dp)
                .size(20.dp),
            color = foreground,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    "在歌单中搜索",
                    color = foreground.copy(alpha = 0.46f),
                    fontSize = 17.sp,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = foreground,
                    fontSize = 17.sp,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MeloXStandardPlaylistHero(
    playlist: NeteasePlaylistSummary,
    tracks: List<SearchSong>,
    foreground: Color,
    secondary: Color,
    sourceLabel: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    isSaved: Boolean,
    showSaveAction: Boolean,
    onToggleSaved: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val artworkSize = minOf(maxWidth * 0.68f, 300.dp)
        var descriptionExpanded by remember(playlist.id) { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 26.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val sharedArtworkModifier = with(sharedTransitionScope) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(
                        key = playlistArtworkSharedKey(playlist.id),
                    ),
                    animatedVisibilityScope = animatedVisibilityScope,
                    renderInOverlayDuringTransition = true,
                    zIndexInOverlay = 1f,
                )
            }

            AsyncImage(
                model = playlist.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = sharedArtworkModifier
                    .size(artworkSize)
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(12.dp),
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.18f),
                        spotColor = Color.Black.copy(alpha = 0.18f),
                    )
                    .clip(RoundedCornerShape(12.dp)),
            )

            Text(
                text = playlist.name,
                modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp),
                color = foreground,
                fontSize = 22.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = playlist.creatorName.ifBlank { sourceLabel },
                modifier = Modifier.padding(top = 8.dp),
                color = foreground,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = buildString {
                    append("${if (playlist.trackCount > 0) playlist.trackCount else tracks.size} 首歌曲")
                    if (MeloXSettingsRuntime.showPlaylistPlayCount && playlist.playCount > 0) append(" · ${compactPlayCount(playlist.playCount)} 次播放")
                },
                modifier = Modifier.padding(top = 7.dp),
                color = secondary,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )

            Row(
                modifier = Modifier.padding(top = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MeloXGlassCircleButton(
                    foreground = foreground,
                    size = 54.dp,
                    enabled = tracks.isNotEmpty(),
                    onClick = onShuffle,
                ) {
                    MeloXShuffleGlyph(Modifier.size(26.dp), foreground)
                }

                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .meloXLiquidButton(
                            shape = RoundedCornerShape(25.dp),
                            enabled = tracks.isNotEmpty(),
                            tint = if (foreground == Color.White) Color.White else Color.Black,
                            surfaceColor = if (foreground == Color.White) {
                                Color.White.copy(alpha = 0.82f)
                            } else {
                                Color.Black.copy(alpha = 0.82f)
                            },
                            lensRadius = 12.dp,
                            refractionHeight = 20.dp,
                        )
                        .clickable(enabled = tracks.isNotEmpty(), onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        MeloXPlayGlyph(
                            Modifier.size(19.dp),
                            if (foreground == Color.White) Color.Black else Color.White,
                        )
                        Text(
                            "播放",
                            color = if (foreground == Color.White) Color.Black else Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (showSaveAction) {
                    MeloXGlassCircleButton(
                        foreground = foreground,
                        size = 54.dp,
                        onClick = onToggleSaved,
                    ) {
                        Text(
                            if (isSaved) "✓" else "+",
                            color = foreground,
                            fontSize = if (isSaved) 24.sp else 34.sp,
                            lineHeight = 34.sp,
                            fontWeight = if (isSaved) FontWeight.SemiBold else FontWeight.Light,
                        )
                    }
                }
            }

            playlist.description
                ?.takeUnless { description ->
                    description.isBlank() || description.equals("null", ignoreCase = true)
                }
                ?.let { description ->
                    Text(
                        text = description,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 24.dp)
                            .clickable { descriptionExpanded = !descriptionExpanded },
                        color = secondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = if (descriptionExpanded) 12 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
        }
    }
}

@Composable
private fun MeloXPlaylistTrackRow(
    song: SearchSong,
    index: Int,
    foreground: Color,
    showMore: Boolean = true,
    onClick: () -> Unit,
    onMore: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayLast: () -> Unit,
    endAction: MeloXSwipeAction?,
) {
    MeloXSwipeActionRow(
        startActions = listOf(
            MeloXSwipeAction("下一首播放", MeloXSymbol.Next, Color(0xFF8E5AF7), onPlayNext),
            MeloXSwipeAction("稍后播放", MeloXSymbol.Queue, Color(0xFFFF9F0A), onPlayLast),
        ),
        endActions = listOfNotNull(endAction),
        startFullSwipeActionIndex = if (MeloXSettingsRuntime.swipeFullAction == MeloXSwipeFullAction.AddToQueue) 1 else 0,
        onClick = onClick,
        onLongClick = if (showMore) onMore else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "${index + 1}",
                    modifier = Modifier.width(40.dp),
                    color = foreground.copy(alpha = 0.48f),
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Text(
                    text = song.name,
                    modifier = Modifier.weight(1f),
                    color = foreground,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MeloXGlassCircleButton(
    foreground: Color,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .meloXLiquidButton(
                shape = CircleShape,
                enabled = enabled,
                surfaceColor = glassColor(foreground).copy(alpha = 0.48f),
                lensRadius = 11.dp,
                refractionHeight = 18.dp,
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun glassColor(foreground: Color): Color =
    if (foreground == Color.White) Color.Black.copy(alpha = 0.22f)
    else Color.White.copy(alpha = 0.64f)

@Composable
private fun MeloXPlayGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.24f, size.height * 0.12f)
            lineTo(size.width * 0.86f, size.height * 0.50f)
            lineTo(size.width * 0.24f, size.height * 0.88f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun MeloXBackGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.14f
        val p = Path().apply {
            moveTo(size.width * 0.67f, size.height * 0.14f)
            lineTo(size.width * 0.32f, size.height * 0.50f)
            lineTo(size.width * 0.67f, size.height * 0.86f)
        }
        drawPath(p, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun MeloXSearchGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.11f
        drawCircle(
            color = color,
            radius = size.minDimension * 0.30f,
            center = Offset(size.width * 0.42f, size.height * 0.40f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.62f, size.height * 0.61f),
            end = Offset(size.width * 0.86f, size.height * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun MeloXShareGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.09f
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.20f, size.height * 0.40f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.60f, size.height * 0.50f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.08f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.50f, size.height * 0.63f),
            end = Offset(size.width * 0.50f, size.height * 0.12f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        val arrow = Path().apply {
            moveTo(size.width * 0.34f, size.height * 0.28f)
            lineTo(size.width * 0.50f, size.height * 0.11f)
            lineTo(size.width * 0.66f, size.height * 0.28f)
        }
        drawPath(arrow, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun MeloXShuffleGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.095f
        val top = Path().apply {
            moveTo(size.width * 0.10f, size.height * 0.28f)
            cubicTo(
                size.width * 0.34f, size.height * 0.28f,
                size.width * 0.54f, size.height * 0.72f,
                size.width * 0.78f, size.height * 0.72f,
            )
        }
        val bottom = Path().apply {
            moveTo(size.width * 0.10f, size.height * 0.72f)
            cubicTo(
                size.width * 0.34f, size.height * 0.72f,
                size.width * 0.54f, size.height * 0.28f,
                size.width * 0.78f, size.height * 0.28f,
            )
        }
        drawPath(top, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawPath(bottom, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        val a1 = Path().apply {
            moveTo(size.width * 0.70f, size.height * 0.17f)
            lineTo(size.width * 0.89f, size.height * 0.28f)
            lineTo(size.width * 0.70f, size.height * 0.39f)
        }
        val a2 = Path().apply {
            moveTo(size.width * 0.70f, size.height * 0.61f)
            lineTo(size.width * 0.89f, size.height * 0.72f)
            lineTo(size.width * 0.70f, size.height * 0.83f)
        }
        drawPath(a1, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawPath(a2, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

private fun playlistArtworkSharedKey(playlistId: Long): String =
    "library-playlist-artwork-$playlistId"

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private fun compactPlayCount(value: Long): String = when {
    value >= 100_000_000L -> "%.1f 亿".format(value / 100_000_000.0)
    value >= 10_000L -> "%.1f 万".format(value / 10_000.0)
    else -> value.toString()
}

private fun optimized160Artwork(url: String?): String? {
    val source = url?.takeIf(String::isNotBlank) ?: return null
    if (!source.contains(".music.126.net")) return source
    val separator = if (source.contains('?')) '&' else '?'
    return if (source.contains("param=")) source else "$source${separator}param=160y160"
}

private fun sharePlaylistFromDetail(context: android.content.Context, playlist: NeteasePlaylistSummary) {
    val providerPlaylist = playlist.providerPlaylist
    if (providerPlaylist == null) {
        com.lladlam.melox.ui.sharing.MeloXNeteaseResourceShareActivity.launch(
            context = context,
            type = "playlist",
            id = playlist.id,
            title = playlist.name,
            url = "https://music.163.com/playlist?id=${playlist.id}",
        )
        return
    }

    val text = buildString {
        append(providerPlaylist.title)
        if (!providerPlaylist.creatorName.isNullOrBlank()) append(" · ").append(providerPlaylist.creatorName)
        append('\n').append(providerPlaylist.id.source.displayName)
    }
    val intent = android.content.Intent.createChooser(
        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        "分享歌单",
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
