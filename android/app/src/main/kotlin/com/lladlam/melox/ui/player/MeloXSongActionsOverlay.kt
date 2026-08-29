package com.lladlam.melox.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.download.MeloXDownloadPlaylistRef
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXCommentRepliesPage
import com.lladlam.melox.core.network.MeloXListenTogetherRoom
import com.lladlam.melox.core.network.MeloXMusicComment
import com.lladlam.melox.core.network.MeloXSearchKind
import com.lladlam.melox.core.network.MeloXUserPlayRecord
import com.lladlam.melox.core.network.MeloXUserPlayRecordPeriod
import com.lladlam.melox.ui.song.MeloXSongWikiActivity
import com.lladlam.melox.core.network.MeloXWikiSection
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.network.NeteaseSocialExtrasClient
import com.lladlam.melox.playback.MeloXListenTogetherCoordinator
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.glass.MeloXActionIcon
import com.lladlam.melox.ui.glass.MeloXIosGroupedList
import com.lladlam.melox.ui.glass.MeloXIosListRow
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.animation.meloXPanelEnter
import com.lladlam.melox.ui.animation.meloXPanelExit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SongActionPage {
    Main,
    Sleep,
    AddToPlaylist,
    CreatePlaylist,
    Comments,
    CommentReplies,
    ShareContacts,
    ListeningRank,
    Wiki,
    ListenTogether,
}

private data class MeloXMoreAction(
    val title: String,
    val symbol: MeloXSymbol,
    val onClick: () -> Unit,
)

@Composable
fun MeloXSongActionsOverlay(
    song: SearchSong,
    queue: List<SearchSong>,
    visible: Boolean,
    onDismiss: () -> Unit,
    playbackState: MeloXPlaybackUiState? = null,
    onNavigateSearch: ((String, MeloXSearchKind) -> Unit)? = null,
    sourcePlaylist: MeloXDownloadPlaylistRef? = null,
    sourceOwnedPlaylistId: Long? = null,
    onSourcePlaylistChanged: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val cookieProvider = remember(app) { { NeteaseSessionStore.readCookie(app) } }
    val library = remember(app) { NeteaseLibraryClient(cookieProvider = cookieProvider) }
    val ops = remember(app) { NeteaseMusicOperationsClient(cookieProvider = cookieProvider) }
    val social = remember(app) { NeteaseSocialExtrasClient(cookieProvider = cookieProvider) }
    val account = remember(app) { NeteaseSearchClient(cookieProvider = cookieProvider) }
    val downloads = remember(app) { MeloXDownloadStore.get(app) }

    var page by remember(song.id, visible) { mutableStateOf(SongActionPage.Main) }
    var busy by remember(song.id, visible) { mutableStateOf(false) }
    var message by remember(song.id, visible) { mutableStateOf<String?>(null) }
    var liked by remember(song.id, visible) { mutableStateOf<Boolean?>(null) }
    var writablePlaylists by remember(song.id, visible) { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var newPlaylistName by remember(song.id, visible) { mutableStateOf("") }
    var newPlaylistPrivate by remember(song.id, visible) { mutableStateOf(false) }
    var hotComments by remember(song.id, visible) { mutableStateOf<List<MeloXMusicComment>>(emptyList()) }
    var comments by remember(song.id, visible) { mutableStateOf<List<MeloXMusicComment>>(emptyList()) }
    var commentsPage by remember(song.id, visible) { mutableStateOf<com.lladlam.melox.core.network.MeloXCommentsPage?>(null) }
    var selectedComment by remember(song.id, visible) { mutableStateOf<MeloXMusicComment?>(null) }
    var repliesPage by remember(song.id, visible) { mutableStateOf<MeloXCommentRepliesPage?>(null) }
    var shareContacts by remember(song.id, visible) { mutableStateOf<List<com.lladlam.melox.core.network.MeloXMessageContact>>(emptyList()) }
    var playRecordPeriod by remember(song.id, visible) { mutableStateOf(MeloXUserPlayRecordPeriod.Week) }
    var playRecords by remember(song.id, visible) { mutableStateOf<List<MeloXUserPlayRecord>>(emptyList()) }
    var wiki by remember(song.id, visible) { mutableStateOf<List<MeloXWikiSection>>(emptyList()) }
    val togetherState by MeloXListenTogetherCoordinator.state(app).collectAsState()
    val listenRoom = togetherState.room
    var invitationText by remember(visible) { mutableStateOf("") }

    LaunchedEffect(visible, song.id) {
        if (!visible) return@LaunchedEffect
        runCatching {
            val profile = account.accountProfile()
            val ids = withContext(Dispatchers.IO) { library.likedSongIdsBlocking(profile.userId) }
            liked = song.id in ids
        }
    }
    LaunchedEffect(visible, playbackState != null) { if (!visible) return@LaunchedEffect; if (playbackState != null) MeloXListenTogetherCoordinator.ensureStarted(app) }

    suspend fun loadOwnedPlaylists() {
        busy = true
        message = null
        runCatching {
            val profile = account.accountProfile()
            withContext(Dispatchers.IO) { library.userPlaylistsBlocking(profile.userId) }
                .filter { it.creatorUserId == profile.userId }
        }.onSuccess { writablePlaylists = it }
            .onFailure { message = it.message ?: "歌单读取失败" }
        busy = false
    }

    suspend fun loadComments(append: Boolean = false) { busy = true; message = null; val current = commentsPage; val offset = if (append) current?.nextOffset ?: comments.size else 0; val before = if (append) current?.beforeTime ?: 0L else 0L; runCatching { social.songComments(song.id, offset, before) }.onSuccess { loaded -> if (append) comments = (comments + loaded.comments).distinctBy(MeloXMusicComment::id) else { hotComments = loaded.hotComments; comments = loaded.comments }; commentsPage = loaded.copy(comments = comments, nextOffset = comments.size) }.onFailure { message = it.message ?: "评论加载失败" }; busy = false }
    suspend fun loadShareContacts() { busy = true; message = null; runCatching { val profile = account.accountProfile(); ops.messageContacts(profile.userId) }.onSuccess { shareContacts = it }.onFailure { message = it.message ?: "联系人加载失败" }; busy = false }

    suspend fun loadReplies(parent: MeloXMusicComment, append: Boolean) {
        busy = true
        message = null
        val current = repliesPage
        val requestTime = if (append) current?.nextTime ?: -1L else -1L
        runCatching { social.songCommentReplies(song.id, parent.id, requestTime) }
            .onSuccess { loaded ->
                repliesPage = if (!append || current == null) {
                    loaded
                } else {
                    loaded.copy(
                        ownerComment = loaded.ownerComment ?: current.ownerComment,
                        replies = (current.replies + loaded.replies).distinctBy(MeloXMusicComment::id),
                        totalCount = maxOf(current.totalCount, loaded.totalCount),
                    )
                }
            }
            .onFailure { message = it.message ?: "评论回复加载失败" }
        busy = false
    }

    suspend fun loadPlayRecords(period: MeloXUserPlayRecordPeriod) {
        busy = true
        message = null
        playRecordPeriod = period
        runCatching {
            val profile = account.accountProfile()
            social.userPlayRecords(profile.userId, period)
        }.onSuccess { playRecords = it }
            .onFailure { message = it.message ?: "听歌排行加载失败" }
        busy = false
    }

    suspend fun loadWiki() {
        busy = true
        message = null
        runCatching { ops.songWiki(song.id) }
            .onSuccess { wiki = it }
            .onFailure { message = it.message ?: "百科加载失败" }
        busy = false
    }

    BackHandler(enabled = visible) {
        page = when (page) {
            SongActionPage.CommentReplies -> SongActionPage.Comments
            SongActionPage.Main -> {
                onDismiss()
                SongActionPage.Main
            }
            else -> SongActionPage.Main
        }
    }

    if (visible) ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp),
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().height(18.dp),
                contentAlignment = Alignment.Center,
            ) {
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
                        meloXPanelEnter() togetherWith meloXPanelExit()
                    },
            modifier = Modifier.fillMaxWidth(),
            label = "song-action-page",
        ) { target ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                    ) {
                        ActionHeader(
                            song,
                            when (target) {
                                SongActionPage.Main -> "歌曲操作"
                                SongActionPage.Sleep -> "定时关闭"
                                SongActionPage.AddToPlaylist -> "添加到歌单"
                                SongActionPage.CreatePlaylist -> "创建歌单"
                                SongActionPage.Comments -> "评论"
                                SongActionPage.CommentReplies -> "评论回复"
                                SongActionPage.ShareContacts -> "发送给网易云好友"
                                SongActionPage.ListeningRank -> "我的听歌排行"
                                SongActionPage.Wiki -> "歌曲百科"
                                SongActionPage.ListenTogether -> "一起听"
                            },
                        )
                        message?.let {
                            Text(
                                it,
                                color = Color(0xFFFF8A90),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }

                        when (target) {
                            SongActionPage.Main -> {
                                Text("更多操作", Modifier.padding(bottom = 10.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                val actions = listOf(
                                    MeloXMoreAction("定时关闭", MeloXSymbol.Moon) { page = SongActionPage.Sleep },
                                    MeloXMoreAction("下一首播放", MeloXSymbol.Next) {
                                        if (playbackState == null) PlaybackCommands.playNext(context, song)
                                        onDismiss()
                                    },
                                    MeloXMoreAction("添加到播放队列", MeloXSymbol.AddToPlaylist) {
                                        if (playbackState != null) playbackState.addCurrentToQueue() else PlaybackCommands.addToQueue(context, song)
                                        onDismiss()
                                    },
                                    MeloXMoreAction(if (downloads.contains(song.id)) "删除下载" else "下载歌曲", if (downloads.contains(song.id)) MeloXSymbol.Trash else MeloXSymbol.Download) {
                                        if (downloads.contains(song.id)) downloads.remove(song.id) else downloads.start(song, MusicQualityPreferences.read(app), sourcePlaylist)
                                    },
                                    MeloXMoreAction("添加到歌单", MeloXSymbol.AddToPlaylist) {
                                        page = SongActionPage.AddToPlaylist
                                        scope.launch { loadOwnedPlaylists() }
                                    },
                                    MeloXMoreAction(if (liked == true) "取消喜爱" else "喜爱", MeloXSymbol.Heart) {
                                        val desired = liked != true
                                        busy = true
                                        scope.launch {
                                            runCatching { ops.setSongLiked(song.id, desired) }
                                                .onSuccess { liked = desired }
                                                .onFailure { message = it.message }
                                            busy = false
                                        }
                                    },
                                    MeloXMoreAction("系统分享", MeloXSymbol.Share) { shareSong(context, song); onDismiss() },
                                    MeloXMoreAction("发送给网易云好友", MeloXSymbol.Mail) { page = SongActionPage.ShareContacts; scope.launch { loadShareContacts() } },
                                    MeloXMoreAction("分享到网易云动态", MeloXSymbol.Message) {
                                        if (!busy) {
                                            busy = true
                                            scope.launch {
                                                runCatching { social.shareSongToTimeline(song.id) }
                                                    .onSuccess { message = "已分享到网易云动态" }
                                                    .onFailure { message = it.message ?: "动态分享失败" }
                                                busy = false
                                            }
                                        }
                                    },
                                    MeloXMoreAction("查看评论", MeloXSymbol.Comment) { page = SongActionPage.Comments; scope.launch { loadComments(false) } },
                                    MeloXMoreAction("我的听歌排行", MeloXSymbol.Clock) { page = SongActionPage.ListeningRank; scope.launch { loadPlayRecords(MeloXUserPlayRecordPeriod.Week) } },
                                    MeloXMoreAction("歌曲百科", MeloXSymbol.Book) {
                                        MeloXSongWikiActivity.launch(context, song)
                                        onDismiss()
                                    },
                                )
                                sourceOwnedPlaylistId?.let { playlistId ->
                                    ActionItem("从当前歌单移除", "−") {
                                        if (!busy) {
                                            busy = true
                                            scope.launch {
                                                runCatching { ops.removeSongFromPlaylist(song.id, playlistId) }
                                                    .onSuccess { onSourcePlaylistChanged?.invoke(); onDismiss() }
                                                    .onFailure { message = it.message ?: "移除歌曲失败" }
                                                busy = false
                                            }
                                        }
                                    }
                                }
                                if (playbackState != null) {
                                    ActionItem("一起听", "◎") { page = SongActionPage.ListenTogether }
                                }
                                if (song.album.isNotBlank() && onNavigateSearch != null) {
                                    ActionItem("前往专辑：${song.album}", "▣") { onDismiss(); onNavigateSearch(song.album, MeloXSearchKind.Albums) }
                                }
                                if (song.artists.isNotBlank() && onNavigateSearch != null) {
                                    ActionItem("前往艺人：${song.artists}", "♬") { onDismiss(); onNavigateSearch(song.artists.substringBefore(" / "), MeloXSearchKind.Artists) }
                                }
                                MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                    actions.forEachIndexed { index, action ->
                                        MeloXIosListRow(
                                            title = action.title,
                                            leading = { MeloXSymbolIcon(action.symbol, Modifier.size(22.dp), MaterialTheme.colorScheme.onSurface, iconSize = 21.sp) },
                                            showTopSeparator = index > 0,
                                            onClick = action.onClick,
                                        )
                                    }
                                }
                            }

                            SongActionPage.Sleep -> {
                                val state = playbackState
                                if (state != null) {
                                    listOf(15, 30, 45, 60).forEach { minutes ->
                                        ActionItem("$minutes 分钟后", "◷") { state.setSleepTimer(minutes); onDismiss() }
                                    }
                                    if (state.sleepTimerEndRealtimeMs > 0L) {
                                        ActionItem("取消定时", "×") { state.cancelSleepTimer(); onDismiss() }
                                    }
                                }
                                ActionItem("返回", "‹") { page = SongActionPage.Main }
                            }

                            SongActionPage.AddToPlaylist -> {
                                if (busy) LoadingRow("正在读取歌单")
                                ActionItem("新建歌单", "+") {
                                    message = null
                                    page = SongActionPage.CreatePlaylist
                                }
                                writablePlaylists.forEach { playlist ->
                                    Row(
                                        Modifier.fillMaxWidth().height(58.dp).clickable(enabled = !busy) {
                                            busy = true
                                            scope.launch {
                                                runCatching { ops.addSongToPlaylist(song.id, playlist.id) }
                                                    .onSuccess { onDismiss() }
                                                    .onFailure { message = it.message }
                                                busy = false
                                            }
                                        },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AsyncImage(
                                            playlist.coverUrl,
                                            null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(46.dp).padding(2.dp),
                                        )
                                        Spacer(Modifier.size(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(playlist.name, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${playlist.trackCount} 首歌曲", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), fontSize = 12.sp)
                                        }
                                    }
                                }
                                if (!busy && writablePlaylists.isEmpty() && message == null) {
                                    Text("没有可写入的自建歌单。", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), modifier = Modifier.padding(12.dp))
                                }
                                ActionItem("返回", "‹") { page = SongActionPage.Main }
                            }

                            SongActionPage.CreatePlaylist -> {
                                Text(
                                    "创建歌单",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), RoundedCornerShape(14.dp))
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (newPlaylistName.isBlank()) {
                                        Text("歌单名称", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f), fontSize = 16.sp)
                                    }
                                    BasicTextField(
                                        value = newPlaylistName,
                                        onValueChange = { newPlaylistName = it.take(40) },
                                        enabled = !busy,
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                ActionItem(
                                    if (newPlaylistPrivate) "私密歌单" else "公开歌单",
                                    if (newPlaylistPrivate) "▣" else "◎",
                                ) { newPlaylistPrivate = !newPlaylistPrivate }
                                ActionItem("创建并添加歌曲", "✓") {
                                    if (!busy && newPlaylistName.isNotBlank()) {
                                        busy = true
                                        message = null
                                        scope.launch {
                                            runCatching {
                                                val created = ops.createPlaylist(newPlaylistName, newPlaylistPrivate)
                                                ops.addSongToPlaylist(song.id, created.id)
                                                created
                                            }.onSuccess {
                                                onSourcePlaylistChanged?.invoke()
                                                onDismiss()
                                            }.onFailure { message = it.message ?: "歌单创建失败" }
                                            busy = false
                                        }
                                    }
                                }
                                ActionItem("返回歌单列表", "‹") { page = SongActionPage.AddToPlaylist }
                            }

                            SongActionPage.Comments -> {
                                if (busy && comments.isEmpty()) LoadingRow("正在读取评论")
                                if (hotComments.isNotEmpty()) Text("热门评论", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 12.sp)
                                LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                                    items(hotComments, key = { "hot-${it.id}" }) { c -> Column(Modifier.fillMaxWidth().clickable { selectedComment = c; repliesPage = null; page = SongActionPage.CommentReplies; scope.launch { loadReplies(c, false) } }.padding(vertical = 9.dp)) { CommentRow(c) } }
                                    if (comments.isNotEmpty()) item { Text("最新评论 · ${commentsPage?.totalCount ?: comments.size}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 12.sp) }
                                    items(comments, key = { "latest-${it.id}" }) { c -> Column(Modifier.fillMaxWidth().clickable { selectedComment = c; repliesPage = null; page = SongActionPage.CommentReplies; scope.launch { loadReplies(c, false) } }.padding(vertical = 9.dp)) { CommentRow(c) } }
                                }
                                if (commentsPage?.hasMore == true) ActionItem("加载更多评论", "+") { if (!busy) scope.launch { loadComments(true) } }
                                ActionItem("返回", "‹") { page = SongActionPage.Main }
                            }
                            SongActionPage.ShareContacts -> {
                                if (busy && shareContacts.isEmpty()) LoadingRow("正在读取联系人")
                                LazyColumn(Modifier.fillMaxWidth().height(350.dp)) { items(shareContacts, key = { "share-${it.id}" }) { contact -> Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { busy = true; scope.launch { runCatching { social.sendSongToUser(song.id, contact.id) }.onSuccess { message = "已发送给 ${contact.name}"; page = SongActionPage.Main }.onFailure { message = it.message ?: "发送失败" }; busy = false } }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(contact.avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp)); Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(contact.name, color = Color.White); if (contact.signature.isNotBlank()) Text(contact.signature, color = Color.White.copy(alpha = .45f), fontSize = 11.sp, maxLines = 1) } } } }
                                ActionItem("返回", "‹") { page = SongActionPage.Main }
                            }
                            SongActionPage.CommentReplies -> {
                                val parent = selectedComment
                                val replies = repliesPage
                                if (parent != null) {
                                        Text("原评论", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), fontSize = 12.sp)
                                    CommentRow(replies?.ownerComment ?: parent)
                                }
                                if (busy && replies == null) LoadingRow("正在读取回复")
                                LazyColumn(Modifier.fillMaxWidth().height(280.dp)) {
                                    items(replies?.replies.orEmpty(), key = { it.id }) { reply -> CommentRow(reply) }
                                }
                                replies?.let {
                                    Text(
                                        if (it.totalCount > 0) "全部回复 · ${it.totalCount}" else "暂无回复",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    )
                                    if (it.hasMore && parent != null) {
                                        ActionItem("加载更多回复", "+") {
                                            if (!busy) scope.launch { loadReplies(parent, append = true) }
                                        }
                                    }
                                }
                                ActionItem("返回评论", "‹") { page = SongActionPage.Comments }
                            }

                            SongActionPage.ListeningRank -> {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RankPeriodButton(
                                        title = "最近一周",
                                        selected = playRecordPeriod == MeloXUserPlayRecordPeriod.Week,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        if (!busy) scope.launch { loadPlayRecords(MeloXUserPlayRecordPeriod.Week) }
                                    }
                                    RankPeriodButton(
                                        title = "所有时间",
                                        selected = playRecordPeriod == MeloXUserPlayRecordPeriod.AllTime,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        if (!busy) scope.launch { loadPlayRecords(MeloXUserPlayRecordPeriod.AllTime) }
                                    }
                                }
                                if (busy) LoadingRow("正在读取听歌排行")
                                LazyColumn(Modifier.fillMaxWidth().height(330.dp)) {
                                    items(playRecords, key = { it.song.id }) { record ->
                                        Row(
                                            Modifier.fillMaxWidth().clickable {
                                                val songs = playRecords.map(MeloXUserPlayRecord::song)
                                                PlaybackCommands.playQueue(context, songs, record.song.id)
                                                onDismiss()
                                            }.padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            AsyncImage(
                                                record.song.artworkUrl,
                                                null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(44.dp).padding(2.dp),
                                            )
                                            Spacer(Modifier.size(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(record.song.name, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(record.song.artists, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Text("${record.playCount} 次", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), fontSize = 12.sp)
                                        }
                                    }
                                }
                                if (!busy && playRecords.isEmpty() && message == null) {
                                    Text("暂无听歌排行数据", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), modifier = Modifier.padding(12.dp))
                                }
                                ActionItem("返回", "‹") { page = SongActionPage.Main }
                            }

                            SongActionPage.Wiki -> {
                                if (busy) LoadingRow("正在读取百科")
                                LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                                    items(wiki, key = { it.title + it.lines.hashCode() }) { section ->
                                        Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                                            Text(section.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            section.lines.forEach { line ->
                                                Text(line, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .70f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                                            }
                                        }
                                    }
                                }
                                if (!busy && wiki.isEmpty() && message == null) {
                                    Text("暂无百科资料", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), modifier = Modifier.padding(12.dp))
                                }
                                ActionItem("返回", "‹") { page = SongActionPage.Main }
                            }

                            SongActionPage.ListenTogether -> {
                                val room = listenRoom
                                if (room == null) {
                                    Text(
                                        "一起听会在后台持续同步播放/暂停、切歌、拖动进度和队列；关闭这个面板不会中断会话。",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(6.dp, 6.dp, 6.dp, 12.dp),
                                    )
                                    ActionItem("发起一起听", "◎") {
                                        if (!busy) {
                                            busy = true
                                            message = null
                                            scope.launch {
                                                runCatching {
                                                    val created = ops.createListenTogetherRoom()
                                                    val profile = account.accountProfile()
                                                    val ids = queue.map(SearchSong::id).filter { it > 0L }.ifEmpty { listOf(song.id) }
                                                    ops.reportListenTogetherPlaylist(created.id, profile.userId, ids, 1)
                                                    created
                                                }.onSuccess { MeloXListenTogetherCoordinator.adoptRoom(app, it) }.onFailure { message = it.message ?: "创建房间失败" }
                                                busy = false
                                            }
                                        }
                                    }
                                    BasicTextField(
                                        value = invitationText,
                                        onValueChange = { invitationText = it },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                                        decorationBox = { inner ->
                                            Box(
                                                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)
                                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), RoundedCornerShape(14.dp))
                                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                            ) {
                                                if (invitationText.isBlank()) {
                                                    Text("粘贴一起听邀请链接", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f), fontSize = 14.sp)
                                                }
                                                inner()
                                            }
                                        },
                                    )
                                    ActionItem("加入邀请房间", "→") {
                                        val parsed = parseListenTogetherInvitation(invitationText)
                                        if (parsed == null) message = "邀请链接缺少 roomId 或 inviterId/inviterUid"
                                        else if (!busy) {
                                            busy = true
                                            message = null
                                            scope.launch {
                                                runCatching { ops.joinListenTogetherRoom(parsed.first, parsed.second) }
                                                    .onSuccess { MeloXListenTogetherCoordinator.adoptRoom(app, it) }
                                                    .onFailure { message = it.message ?: "加入房间失败" }
                                                busy = false
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        "房间 ${room.id} · ${room.users.size.coerceAtLeast(1)} 位成员",
                                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(6.dp),
                                    )
                                    room.users.forEach { member ->
                                        Text("• ${member.name}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
                                    }
                                    Text(
                                        when (togetherState.phase) { MeloXListenTogetherCoordinator.Phase.Reconnecting -> "正在重新连接 · ${togetherState.consecutiveFailures} 次失败"; MeloXListenTogetherCoordinator.Phase.Connected -> "后台自动同步已启用 · 1 秒状态同步 · 5 秒心跳"; MeloXListenTogetherCoordinator.Phase.Idle -> "正在恢复房间状态" },
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                    ActionItem("分享房间邀请", "↗") { shareListenTogether(context, song.id, room) }
                                    ActionItem("结束/退出房间", "×") {
                                        if (!busy) {
                                            busy = true
                                            scope.launch {
                                                runCatching { ops.endListenTogetherRoom(room.id) }
                                                    .onSuccess { MeloXListenTogetherCoordinator.clearRoom(app) }
                                                    .onFailure { message = it.message ?: "结束房间失败" }
                                                busy = false
                                            }
                                        }
                                    }
                                }
                                if (busy) LoadingRow("正在连接一起听")
                                ActionItem("返回", "‹") { page = SongActionPage.Main }
                            }
                        }
        }
    }

}

}

@Composable
private fun ActionHeader(song: SearchSong, title: String) {
    val foreground = MaterialTheme.colorScheme.onSurface
    Text(title, color = foreground.copy(alpha = .58f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    Text(
        song.name,
        color = foreground,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 3.dp, bottom = 10.dp),
    )
}

@Composable
private fun ActionItem(title: String, symbol: String, onClick: () -> Unit) {
    val foreground = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().height(46.dp).clickable(onClick = onClick).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            MeloXActionIcon(
                token = symbol,
                modifier = Modifier.size(20.dp),
                color = foreground.copy(alpha = 0.86f),
            )
        }
        Text(title, color = foreground, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun CommentRow(comment: MeloXMusicComment) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 6.dp)) {
        Text(comment.user, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(comment.content, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .9f), fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp))
        if (comment.timeText.isNotBlank() || comment.likedCount > 0L) {
            Text(
                listOf(comment.timeText.takeIf(String::isNotBlank), "♡ ${comment.likedCount}").filterNotNull().joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun RankPeriodButton(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) .18f else .07f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else .6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
        Spacer(Modifier.size(10.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
    }
}

private fun shareSong(context: Context, song: SearchSong) {
    val url = "https://music.163.com/song?id=${song.id}"
    runCatching {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${song.name} - ${song.artists}\n$url"),
                "分享歌曲",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun parseListenTogetherInvitation(value: String): Pair<String, String>? =
    com.lladlam.melox.core.network.parseNeteaseListenTogetherInvitation(value)
        ?.let { it.roomId to it.inviterId }

private fun shareListenTogether(context: Context, songId: Long, room: MeloXListenTogetherRoom) {
    val inviter = room.creatorId.ifBlank { room.users.firstOrNull()?.id.orEmpty() }
    val url = "https://st.music.163.com/listen-together/share/?songId=$songId&roomId=${Uri.encode(room.id)}&inviterId=${Uri.encode(inviter)}"
    runCatching {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url),
                "分享一起听邀请",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
