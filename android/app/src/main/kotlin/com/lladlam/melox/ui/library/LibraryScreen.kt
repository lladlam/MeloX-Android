package com.lladlam.melox.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.download.MeloXDownloadPlaylistRef
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.library.NeteaseLibraryCache
import com.lladlam.melox.core.library.NeteaseLibrarySnapshot
import com.lladlam.melox.core.library.NeteasePlaylistDetail
import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.glass.meloXLiquidBottomBar
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.glass.meloXLiquidTabSelection
import com.lladlam.melox.ui.player.MeloXFlowingLightBackdrop
import com.lladlam.melox.ui.player.MeloXSongActionsOverlay
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import com.lladlam.melox.ui.podcast.MeloXPodcastScreen
import com.lladlam.melox.ui.cloud.MeloXCloudMusicScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class MeloXLibraryPage(val title: String) {
    Songs("歌曲"),
    Playlists("歌单"),
    Podcasts("播客"),
    Cloud("云盘"),
    History("最近播放"),
    Downloads("下载"),
}

private fun MeloXLibraryPage.isEnabled(): Boolean = when (this) {
    MeloXLibraryPage.Podcasts -> MeloXSettingsRuntime.podcastsEnabled
    MeloXLibraryPage.History -> MeloXSettingsRuntime.listeningHistoryEnabled
    MeloXLibraryPage.Cloud -> MeloXSettingsRuntime.cloudMusicEnabled
    MeloXLibraryPage.Downloads -> MeloXSettingsRuntime.downloadsEnabled
    else -> true
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    session: NeteaseSessionStore,
    onLogin: () -> Unit,
    playlistBackEnabled: Boolean = true,
    onModalVisibilityChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val client = remember(appContext) {
        NeteaseLibraryClient(
            cookieProvider = { NeteaseSessionStore.readCookie(appContext) },
        )
    }
    val cache = remember(appContext) { NeteaseLibraryCache(appContext) }
    val downloadStore = remember(appContext) { MeloXDownloadStore.get(appContext) }

    val initialLibraryPage = remember {
        val name = if (MeloXSettingsRuntime.rememberLibraryPage) {
            MeloXSettingsPreferences.string(appContext, "library_last_page", MeloXSettingsRuntime.defaultLibraryPage)
        } else MeloXSettingsRuntime.defaultLibraryPage
        runCatching { MeloXLibraryPage.valueOf(name) }
            .getOrDefault(MeloXLibraryPage.Songs)
            .takeIf { it.isEnabled() }
            ?: MeloXLibraryPage.Songs
    }
    var selectedPage by remember { mutableStateOf(initialLibraryPage) }
    var selectedPlaylist by remember(session.cookie) { mutableStateOf<NeteasePlaylistSummary?>(null) }
    var snapshot by remember(session.cookie) { mutableStateOf<NeteaseLibrarySnapshot?>(null) }
    var loading by remember(session.cookie) { mutableStateOf(false) }
    var errorMessage by remember(session.cookie) { mutableStateOf<String?>(null) }
    val playlistListState = rememberLazyListState()

    suspend fun refreshLibrary() {
        if (!session.isLoggedIn) return
        if (session.profile == null) session.refreshProfile(force = true)
        val userId = session.profile?.userId ?: return
        loading = true
        errorMessage = null
        runCatching { client.snapshot(userId) }
            .onSuccess {
                snapshot = it
                cache.saveSnapshot(userId, it)
            }
            .onFailure { errorMessage = it.message ?: "音乐库加载失败" }
        loading = false
    }

    LaunchedEffect(session.cookie, session.profile?.userId) {
        val userId = session.profile?.userId ?: return@LaunchedEffect
        cache.loadSnapshot(userId)?.let { snapshot = it }
        if (NeteaseLibraryCache.beginLibraryColdStartRefresh(userId)) {
            refreshLibrary()
        }
    }

    LaunchedEffect(selectedPage) {
        if (MeloXSettingsRuntime.rememberLibraryPage) {
            MeloXSettingsPreferences.setString(appContext, "library_last_page", selectedPage.name)
        }
    }

    LaunchedEffect(
        MeloXSettingsRuntime.podcastsEnabled,
        MeloXSettingsRuntime.listeningHistoryEnabled,
        MeloXSettingsRuntime.cloudMusicEnabled,
        MeloXSettingsRuntime.downloadsEnabled,
    ) {
        if (!selectedPage.isEnabled()) selectedPage = MeloXLibraryPage.Songs
    }

    BackHandler(enabled = playlistBackEnabled && selectedPlaylist != null) {
        selectedPlaylist = null
    }

    if (!session.isLoggedIn) {
        MeloXLibraryLoginUnavailable(onLogin)
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
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding(),
                ) {
                    Text(
                        text = "音乐库",
                        modifier = Modifier.padding(start = 20.dp, top = 46.dp),
                        fontSize = 36.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )

                    MeloXLibrarySegmentedPicker(
                        selected = selectedPage,
                        onSelected = { selectedPage = it },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 24.dp),
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
                                onHeartMode = {
                                    val seed = data.likedSongs.randomOrNull()
                                    val playlistId = data.playlists.firstOrNull()?.id
                                    if (seed != null && playlistId != null) scope.launch {
                                        runCatching { client.intelligenceModeSongs(seed.id, playlistId) }
                                            .onSuccess { songs -> songs.firstOrNull()?.let { PlaybackCommands.playQueue(context, songs, it.id, heartMode = true) } }
                                            .onFailure { errorMessage = it.message ?: "无法启动心动模式" }
                                    }
                                },
                            )

                            MeloXLibraryPage.Playlists -> MeloXLibraryPlaylistsPage(
                                playlists = data.playlists,
                                onPlaylistClick = { selectedPlaylist = it },
                                listState = playlistListState,
                                sharedTransitionScope = sharedScope,
                                animatedVisibilityScope = playlistTransitionVisibilityScope,
                            )

                            MeloXLibraryPage.Podcasts -> MeloXPodcastScreen(subscriptionsOnly = true)

                            MeloXLibraryPage.Cloud -> MeloXCloudMusicScreen()

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
}

private enum class MeloXDownloadsPage { Root, Active, Playlists, PlaylistDetail }

@Composable
private fun MeloXLibraryDownloadsPage(downloads: MeloXDownloadStore) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(MeloXDownloadsPage.Root) }
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val active = downloads.activeDownloads.values.toList()
    val completed = downloads.downloads.toList()
    val groups = downloads.downloadedPlaylists

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
  if (groups.isNotEmpty()) {
      item {
          DownloadNavigationCard(
              title = "已下载歌单",
              subtitle = "${groups.size} 个歌单",
              onClick = { page = MeloXDownloadsPage.Playlists },
          )
      }
  }
      items(completed, key = { "download-${it.song.id}" }) { item ->
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
              Box(
                  Modifier.fillMaxWidth().padding(top = 16.dp).height(48.dp)
                      .clip(RoundedCornerShape(18.dp))
                      .background(MaterialTheme.colorScheme.error.copy(alpha = if (canDelete) .14f else .05f))
                      .clickable(enabled = canDelete) {
                          downloads.removeMany(selectedIds)
                          selectedIds = emptySet()
                          selecting = false
                      },
                  contentAlignment = Alignment.Center,
              ) {
                  Text(
                      if (canDelete) "删除已选 ${selectedIds.size} 首" else "请选择歌曲",
                      color = MaterialTheme.colorScheme.error.copy(alpha = if (canDelete) 1f else .4f),
                      fontWeight = FontWeight.SemiBold,
                  )
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
          Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .4f))
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
  .clip(RoundedCornerShape(18.dp))
  .background(MaterialTheme.colorScheme.onBackground.copy(alpha = .055f))
  .clickable(onClick = onClick)
  .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
  Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
  Text(subtitle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .42f))
    }
}

@Composable
private fun DownloadsSubpageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("‹", fontSize = 30.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatDownloadSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1024L * 1024L -> "%.1f MB/s".format(bytesPerSecond / (1024.0 * 1024.0))
    bytesPerSecond >= 1024L -> "%.0f KB/s".format(bytesPerSecond / 1024.0)
    bytesPerSecond > 0L -> "$bytesPerSecond B/s"
    else -> "0 KB/s"
}

@Composable
private fun MeloXLibraryLoginUnavailable(onLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Text(
            "音乐库",
            modifier = Modifier.padding(top = 46.dp),
            fontSize = 36.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Bold,
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
                    "登录后可读取收藏歌曲、歌单和播放记录。",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.50f),
                    textAlign = TextAlign.Center,
                )
                Surface(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .meloXLiquidButton(
                            shape = RoundedCornerShape(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                            surfaceColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
                        )
                        .clickable(onClick = onLogin),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        "登录网易云音乐",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeloXLibrarySegmentedPicker(
    selected: MeloXLibraryPage,
    onSelected: (MeloXLibraryPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = MeloXLibraryPage.entries.filter { it.isEnabled() }
    val panelShape = RoundedCornerShape(16.dp)
    val lensShape = RoundedCornerShape(15.dp)
    val panelBackdrop = rememberLayerBackdrop()
    val dark = isSystemInDarkTheme()
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
                    Text("♥", color = Color(0xFFFF3147), fontSize = 23.sp)
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
                    .clickable { onPlaylistClick(playlist) }
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val sharedArtworkModifier = with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState(
                            key = playlistArtworkSharedKey(playlist.id),
                        ),
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }

                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = sharedArtworkModifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(7.dp)),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
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
                Text(
                    "›",
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.24f),
                )
            }
            MeloXInsetDivider(leading = 84.dp)
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MeloXPlaylistDetailScreen(
    initialPlaylist: NeteasePlaylistSummary,
    client: NeteaseLibraryClient,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onModalVisibilityChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val cache = remember(appContext) { NeteaseLibraryCache(appContext) }
    val downloadStore = remember(appContext) { MeloXDownloadStore.get(appContext) }
    val accountClient = remember(appContext) {
        NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) })
    }
    val operationsClient = remember(appContext) {
        NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) })
    }
    var detail by remember(initialPlaylist.id) { mutableStateOf<NeteasePlaylistDetail?>(null) }
    var loading by remember(initialPlaylist.id) { mutableStateOf(true) }
    var errorMessage by remember(initialPlaylist.id) { mutableStateOf<String?>(null) }
    var searchQuery by remember(initialPlaylist.id) { mutableStateOf("") }
    var showPlaylistActions by remember(initialPlaylist.id) { mutableStateOf(false) }
    var selectedTrackAction by remember(initialPlaylist.id) { mutableStateOf<SearchSong?>(null) }
    var isSaved by remember(initialPlaylist.id) { mutableStateOf<Boolean?>(null) }
    var savingPlaylist by remember(initialPlaylist.id) { mutableStateOf(false) }
    var palette by remember(initialPlaylist.coverUrl) { mutableStateOf(MeloXDetailPalette.LightFallback) }

    DisposableEffect(showPlaylistActions, selectedTrackAction) {
        val visible = showPlaylistActions || selectedTrackAction != null
        onModalVisibilityChanged(visible)
        onDispose {
            if (visible) onModalVisibilityChanged(false)
        }
    }

    suspend fun refreshSavedState() {
        val cookie = NeteaseSessionStore.readCookie(appContext)
        if (!NeteaseSessionStore.containsMusicU(cookie)) {
            isSaved = null
            return
        }
        runCatching {
            val profile = accountClient.accountProfile(cookie)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.userPlaylistsBlocking(profile.userId)
            }.any { it.id == initialPlaylist.id }
        }.onSuccess { isSaved = it }
    }

    suspend fun refreshPlaylist() {
        loading = true
        errorMessage = null
        runCatching { client.playlistDetail(initialPlaylist.id) }
            .onSuccess {
                detail = it
                cache.savePlaylistDetail(initialPlaylist.id, it)
            }
            .onFailure { errorMessage = it.message ?: "歌单加载失败" }
        loading = false
    }

    LaunchedEffect(initialPlaylist.id) {
        cache.loadPlaylistDetail(initialPlaylist.id)?.let { detail = it }
        loading = detail == null
        if (NeteaseLibraryCache.beginPlaylistColdStartRefresh(initialPlaylist.id)) {
            refreshPlaylist()
        }
    }

    LaunchedEffect(initialPlaylist.id) {
        refreshSavedState()
    }

    val displayed = detail?.summary ?: initialPlaylist
    val songs = detail?.songs.orEmpty()

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
        // Use the exact same artwork-driven background renderer as the full
        // now-playing artwork page: 160px artwork -> 3x3 palette -> flowing fields.
        MeloXFlowingLightBackdrop(
            artworkUrl = displayed.coverUrl,
            isPlaying = false,
            modifier = Modifier.fillMaxSize(),
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
                onMore = { showPlaylistActions = true },
            )
            MeloXPlaylistSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                foreground = foreground,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = MeloXBottomContentClearance),
            ) {
                item {
                    MeloXStandardPlaylistHero(
                        playlist = displayed,
                        tracks = songs,
                        foreground = foreground,
                        secondary = secondary,
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
                        onDownloadAll = {
                            val quality = MusicQualityPreferences.read(appContext)
                            val source = MeloXDownloadPlaylistRef(
                                id = displayed.id,
                                name = displayed.name,
                                artworkUrl = displayed.coverUrl,
                            )
                            songs.forEach { song ->
                                downloadStore.start(song, quality, source)
                            }
                        },
                        onToggleSaved = {
                            if (!savingPlaylist) {
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
                    else -> itemsIndexed(
                        items = filteredSongs,
                        key = { _, song -> song.id },
                    ) { index, song ->
                        MeloXPlaylistTrackRow(
                            song = song,
                            index = index,
                            foreground = foreground,
                            onClick = {
                                PlaybackCommands.playQueue(
                                    context = context,
                                    songs = filteredSongs,
                                    selectedSongId = song.id,
                                    onFailure = { errorMessage = it.message ?: "播放失败" },
                                )
                            },
                            onMore = { selectedTrackAction = song },
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

        MeloXPlaylistActionsOverlay(
            playlist = displayed,
            visible = showPlaylistActions,
            onDismiss = { showPlaylistActions = false },
            onRefresh = { scope.launch { refreshPlaylist() } },
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
            )
        }
    }
}

@Composable
private fun MeloXPlaylistToolbar(
    foreground: Color,
    onBack: () -> Unit,
    onShare: () -> Unit,
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
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    isSaved: Boolean,
    onDownloadAll: () -> Unit,
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
                text = playlist.creatorName.ifBlank { "网易云音乐" },
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

            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .width(148.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .meloXLiquidButton(
                        shape = RoundedCornerShape(21.dp),
                        enabled = tracks.isNotEmpty(),
                        tint = glassColor(foreground).copy(alpha = .10f),
                        surfaceColor = glassColor(foreground).copy(alpha = .46f),
                        lensRadius = 10.dp,
                        refractionHeight = 16.dp,
                    )
                    .clickable(enabled = tracks.isNotEmpty(), onClick = onDownloadAll),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "↓ 一键下载",
                    color = foreground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            playlist.description
                ?.takeIf(String::isNotBlank)
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
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
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
        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onMore,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "•••",
                color = foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
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
    runCatching {
        context.startActivity(
            android.content.Intent.createChooser(
                android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, "${playlist.name}\nhttps://music.163.com/playlist?id=${playlist.id}"),
                "分享歌单",
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
