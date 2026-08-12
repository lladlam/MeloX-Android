package com.lladlam.melox.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.download.MeloXDownloadPlaylistRef
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXMusicComment
import com.lladlam.melox.core.network.MeloXListenTogetherRoom
import com.lladlam.melox.core.network.MeloXSearchKind
import com.lladlam.melox.core.network.MeloXWikiSection
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.glass.meloXLiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SongActionPage { Main, Sleep, AddToPlaylist, Comments, Wiki, ListenTogether }

@Composable
fun MeloXSongActionsOverlay(
    song: SearchSong,
    queue: List<SearchSong>,
    visible: Boolean,
    onDismiss: () -> Unit,
    playbackState: MeloXPlaybackUiState? = null,
    onNavigateSearch: ((String, MeloXSearchKind) -> Unit)? = null,
    sourcePlaylist: MeloXDownloadPlaylistRef? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val library = remember(app) { NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie(app) }) }
    val ops = remember(app) { NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(app) }) }
    val account = remember(app) { NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(app) }) }
    val downloads = remember(app) { MeloXDownloadStore.get(app) }
    var page by remember(song.id, visible) { mutableStateOf(SongActionPage.Main) }
    var busy by remember(song.id, visible) { mutableStateOf(false) }
    var message by remember(song.id, visible) { mutableStateOf<String?>(null) }
    var liked by remember(song.id, visible) { mutableStateOf<Boolean?>(null) }
    var writablePlaylists by remember(song.id, visible) { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var comments by remember(song.id, visible) { mutableStateOf<List<MeloXMusicComment>>(emptyList()) }
    var wiki by remember(song.id, visible) { mutableStateOf<List<MeloXWikiSection>>(emptyList()) }
    var listenRoom by remember(visible) { mutableStateOf<MeloXListenTogetherRoom?>(null) }
    var invitationText by remember(visible) { mutableStateOf("") }

    LaunchedEffect(visible, song.id) {
        if (!visible) return@LaunchedEffect
        runCatching {
            val profile = account.accountProfile()
            val ids = withContext(Dispatchers.IO) { library.likedSongIdsBlocking(profile.userId) }
            liked = song.id in ids
        }
    }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        runCatching { ops.listenTogetherRoomStatus() }.onSuccess { listenRoom = it }
    }
    LaunchedEffect(page, listenRoom?.id, playbackState?.mediaId, playbackState?.isPlaying) {
        val room = listenRoom ?: return@LaunchedEffect
        val state = playbackState ?: return@LaunchedEffect
        if (page != SongActionPage.ListenTogether) return@LaunchedEffect
        while (isActive) {
            runCatching {
                ops.sendListenTogetherHeartbeat(
                    roomId = room.id,
                    songId = state.mediaId?.toLongOrNull() ?: song.id,
                    isPlaying = state.isPlaying,
                    progressMs = state.positionMs,
                )
            }
            delay(5_000L)
        }
    }

    suspend fun loadOwnedPlaylists() {
        busy = true; message = null
        runCatching {
            val profile = account.accountProfile()
            withContext(Dispatchers.IO) { library.userPlaylistsBlocking(profile.userId) }
                .filter { it.creatorUserId == profile.userId }
        }.onSuccess { writablePlaylists = it }
            .onFailure { message = it.message ?: "歌单读取失败" }
        busy = false
    }

    suspend fun loadComments() {
        busy = true; message = null
        runCatching { ops.songComments(song.id) }.onSuccess { comments = it }.onFailure { message = it.message ?: "评论加载失败" }
        busy = false
    }
    suspend fun loadWiki() {
        busy = true; message = null
        runCatching { ops.songWiki(song.id) }.onSuccess { wiki = it }.onFailure { message = it.message ?: "百科加载失败" }
        busy = false
    }

    BackHandler(enabled = visible) {
        if (page != SongActionPage.Main) page = SongActionPage.Main else onDismiss()
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(spring(stiffness=520f)), exit = fadeOut(spring(stiffness=620f))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha=.22f))
                .clickable(interactionSource=remember{MutableInteractionSource()},indication=null,onClick=onDismiss)
                .padding(horizontal=18.dp).navigationBarsPadding(),
            contentAlignment=Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
                    // Keep one stable Backdrop consumer alive while sub-pages swap.
                    // Putting Liquid Glass directly on AnimatedContent caused its
                    // transient old/new children to enter the capture lifecycle and
                    // could leave a recursively blurred frame after navigating back.
                    .meloXLiquidButton(
                        shape = RoundedCornerShape(30.dp),
                        tint = Color.White.copy(alpha = .08f),
                        surfaceColor = Color.Black.copy(alpha = .12f),
                        blurRadius = 14.dp,
                        lensRadius = 20.dp,
                        refractionHeight = 22.dp,
                    )
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { (fadeIn(spring(stiffness=520f)) + scaleIn(initialScale=.96f)) togetherWith (fadeOut(spring(stiffness=620f)) + scaleOut(targetScale=.96f)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = "song-action-page",
                ) { target ->
                    Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=18.dp)) {
                    ActionHeader(song, when(target){SongActionPage.Main->"歌曲操作";SongActionPage.Sleep->"定时关闭";SongActionPage.AddToPlaylist->"添加到歌单";SongActionPage.Comments->"评论";SongActionPage.Wiki->"歌曲百科";SongActionPage.ListenTogether->"一起听"})
                    message?.let { Text(it,color=Color(0xFFFF8A90),fontSize=12.sp,modifier=Modifier.padding(bottom=6.dp)) }
                    when(target) {
                        SongActionPage.Main -> {
                            playbackState?.let { ActionItem("定时关闭", "◷") { page=SongActionPage.Sleep } }
                            if (playbackState == null) ActionItem("下一首播放", "⇥") { PlaybackCommands.playNext(context,song); onDismiss() }
                            ActionItem("添加到播放队列", "+") { if(playbackState!=null)playbackState.addCurrentToQueue() else PlaybackCommands.addToQueue(context,song); onDismiss() }
                            when {
                                downloads.contains(song.id) -> ActionItem("删除下载", "↓×") { downloads.remove(song.id) }
                                downloads.isDownloading(song.id) -> ActionItem("取消下载", "↓×") { downloads.cancel(song.id) }
                                else -> ActionItem("下载歌曲", "↓") { downloads.start(song, MusicQualityPreferences.read(app), sourcePlaylist) }
                            }
                            downloads.activeDownloads[song.id]?.let { active ->
                                val percent = active.fractionCompleted?.let { (it * 100).toInt() }
                                Text(percent?.let { "正在下载 $it%" } ?: "正在下载…", color=Color.White.copy(alpha=.52f), fontSize=11.sp, modifier=Modifier.padding(start=46.dp,bottom=4.dp))
                            }
                            downloads.errorMessage?.let { Text(it,color=Color(0xFFFF8A90),fontSize=11.sp,modifier=Modifier.padding(start=46.dp,bottom=4.dp)) }
                            ActionItem("添加到歌单", "≡") { page=SongActionPage.AddToPlaylist; scope.launch { loadOwnedPlaylists() } }
                            ActionItem(if(liked==true) "取消喜爱" else "喜爱", if(liked==true) "♥" else "♡") {
                                val desired=liked!=true; busy=true
                                scope.launch { runCatching{ops.setSongLiked(song.id,desired)}.onSuccess{liked=desired}.onFailure{message=it.message}; busy=false }
                            }
                            ActionItem("分享", "↗") { shareSong(context,song); onDismiss() }
                            ActionItem("查看评论", "◌") { page=SongActionPage.Comments; scope.launch{loadComments()} }
                            ActionItem("歌曲百科", "i") { page=SongActionPage.Wiki; scope.launch{loadWiki()} }
                            playbackState?.let { ActionItem("一起听", "◎") { page=SongActionPage.ListenTogether } }
                            if (song.album.isNotBlank() && onNavigateSearch!=null) ActionItem("前往专辑：${song.album}", "▣") { onDismiss(); onNavigateSearch(song.album,MeloXSearchKind.Albums) }
                            if (song.artists.isNotBlank() && onNavigateSearch!=null) ActionItem("前往艺人：${song.artists}", "♬") { onDismiss(); onNavigateSearch(song.artists.substringBefore(" / "),MeloXSearchKind.Artists) }
                        }
                        SongActionPage.Sleep -> {
                            val state=playbackState
                            if(state!=null) {
                                listOf(15,30,45,60).forEach { m->ActionItem("$m 分钟后","◷"){state.setSleepTimer(m);onDismiss()} }
                                if(state.sleepTimerEndRealtimeMs>0L) ActionItem("取消定时","×"){state.cancelSleepTimer();onDismiss()}
                            }
                            ActionItem("返回","‹"){page=SongActionPage.Main}
                        }
                        SongActionPage.AddToPlaylist -> {
                            if(busy) LoadingRow("正在读取歌单")
                            writablePlaylists.forEach { p ->
                                Row(Modifier.fillMaxWidth().height(58.dp).clickable(enabled=!busy){
                                    busy=true; scope.launch{runCatching{ops.addSongToPlaylist(song.id,p.id)}.onSuccess{onDismiss()}.onFailure{message=it.message};busy=false}
                                },verticalAlignment=Alignment.CenterVertically) {
                                    AsyncImage(p.coverUrl,null,contentScale=ContentScale.Crop,modifier=Modifier.size(46.dp).padding(2.dp))
                                    Spacer(Modifier.size(10.dp)); Column(Modifier.weight(1f)){Text(p.name,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${p.trackCount} 首歌曲",color=Color.White.copy(alpha=.5f),fontSize=12.sp)}
                                }
                            }
                            if(!busy&&writablePlaylists.isEmpty()&&message==null) Text("没有可写入的自建歌单。",color=Color.White.copy(alpha=.55f),modifier=Modifier.padding(12.dp))
                            ActionItem("返回","‹"){page=SongActionPage.Main}
                        }
                        SongActionPage.Comments -> {
                            if(busy) LoadingRow("正在读取评论")
                            LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                                items(comments,key={it.id}) { c -> Column(Modifier.fillMaxWidth().padding(vertical=9.dp)){Text(c.user,color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=13.sp);Text(c.content,color=Color.White.copy(alpha=.9f),fontSize=14.sp,modifier=Modifier.padding(top=3.dp));if(c.timeText.isNotBlank())Text("${c.timeText} · ♡ ${c.likedCount}",color=Color.White.copy(alpha=.42f),fontSize=11.sp,modifier=Modifier.padding(top=4.dp))} }
                            }
                            ActionItem("返回","‹"){page=SongActionPage.Main}
                        }
                        SongActionPage.Wiki -> {
                            if(busy) LoadingRow("正在读取百科")
                            LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                                items(wiki,key={it.title+it.lines.hashCode()}) { section -> Column(Modifier.fillMaxWidth().padding(vertical=9.dp)){Text(section.title,color=Color.White,fontWeight=FontWeight.Bold,fontSize=16.sp);section.lines.forEach{line->Text(line,color=Color.White.copy(alpha=.70f),fontSize=13.sp,modifier=Modifier.padding(top=4.dp))}} }
                            }
                            if(!busy&&wiki.isEmpty()&&message==null) Text("暂无百科资料",color=Color.White.copy(alpha=.5f),modifier=Modifier.padding(12.dp))
                            ActionItem("返回","‹"){page=SongActionPage.Main}
                        }
                        SongActionPage.ListenTogether -> {
                            val room = listenRoom
                            if (room == null) {
                                Text("发起房间后会同步当前队列，并持续上报播放进度。也可以粘贴网易云一起听邀请链接加入。",color=Color.White.copy(alpha=.62f),fontSize=13.sp,modifier=Modifier.padding(6.dp,6.dp,6.dp,12.dp))
                                ActionItem("发起一起听", "◎") {
                                    if (!busy) {
                                        busy = true; message = null
                                        scope.launch {
                                            runCatching {
                                                val created = ops.createListenTogetherRoom()
                                                val profile = account.accountProfile()
                                                ops.reportListenTogetherPlaylist(created.id, profile.userId, queue.map { it.id }, 1)
                                                created
                                            }.onSuccess { listenRoom = it }
                                                .onFailure { message = it.message ?: "创建房间失败" }
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
                                        Box(Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=6.dp).background(Color.White.copy(alpha=.08f),RoundedCornerShape(14.dp)).padding(horizontal=12.dp,vertical=11.dp)) {
                                            if(invitationText.isBlank()) Text("粘贴一起听邀请链接",color=Color.White.copy(alpha=.38f),fontSize=14.sp)
                                            inner()
                                        }
                                    },
                                )
                                ActionItem("加入邀请房间", "→") {
                                    val parsed = parseListenTogetherInvitation(invitationText)
                                    if (parsed == null) message = "邀请链接缺少 roomId 或 inviterId"
                                    else if (!busy) {
                                        busy = true; message = null
                                        scope.launch {
                                            runCatching { ops.joinListenTogetherRoom(parsed.first, parsed.second) }
                                                .onSuccess { listenRoom = it }
                                                .onFailure { message = it.message ?: "加入房间失败" }
                                            busy = false
                                        }
                                    }
                                }
                            } else {
                                Text("房间 ${room.id} · ${room.users.size.coerceAtLeast(1)} 位成员",color=Color.White.copy(alpha=.72f),fontSize=13.sp,modifier=Modifier.padding(6.dp,6.dp,6.dp,6.dp))
                                room.users.forEach { member -> Text("• ${member.name}",color=Color.White.copy(alpha=.58f),fontSize=12.sp,modifier=Modifier.padding(horizontal=10.dp,vertical=2.dp)) }
                                ActionItem("立即同步房间播放", "↻") {
                                    if (!busy) {
                                        busy = true; message = null
                                        scope.launch {
                                            runCatching {
                                                val remote = ops.listenTogetherPlayback(room.id)
                                                val ids = remote.songIds.ifEmpty { listOfNotNull(remote.targetSongId) }
                                                val songs = withContext(Dispatchers.IO) { library.songDetailsBlocking(ids) }
                                                val target = remote.targetSongId ?: songs.firstOrNull()?.id
                                                if (songs.isNotEmpty() && target != null) {
                                                    PlaybackCommands.playQueue(context, songs, target)
                                                    delay(350L)
                                                    playbackState?.seekTo(remote.progressMs)
                                                }
                                            }.onFailure { message = it.message ?: "同步播放失败" }
                                            busy = false
                                        }
                                    }
                                }
                                ActionItem("分享房间邀请", "↗") { shareListenTogether(context, song.id, room) }
                                ActionItem("结束/退出房间", "×") {
                                    if (!busy) {
                                        busy = true
                                        scope.launch {
                                            runCatching { ops.endListenTogetherRoom(room.id) }
                                                .onSuccess { listenRoom = null }
                                                .onFailure { message = it.message ?: "结束房间失败" }
                                            busy = false
                                        }
                                    }
                                }
                            }
                            if (busy) LoadingRow("正在连接一起听")
                            ActionItem("返回","‹"){page=SongActionPage.Main}
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable private fun ActionHeader(song:SearchSong,title:String){Text(title,color=Color.White.copy(alpha=.58f),fontSize=13.sp,fontWeight=FontWeight.SemiBold);Text(song.name,color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=3.dp,bottom=10.dp))}
@Composable private fun ActionItem(title:String,symbol:String,onClick:()->Unit){Row(Modifier.fillMaxWidth().height(46.dp).clickable(onClick=onClick).padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Box(Modifier.size(28.dp),contentAlignment=Alignment.Center){Text(symbol,color=Color.White,fontSize=18.sp,fontWeight=FontWeight.SemiBold)};Text(title,color=Color.White,fontSize=15.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis);Spacer(Modifier.weight(1f))}}
@Composable private fun LoadingRow(text:String){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(20.dp),color=Color.White,strokeWidth=2.dp);Spacer(Modifier.size(10.dp));Text(text,color=Color.White.copy(alpha=.7f))}}
private fun shareSong(context:Context,song:SearchSong){val url="https://music.163.com/song?id=${song.id}";runCatching{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"${song.name} - ${song.artists}\n$url"),"分享歌曲").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}}
private fun parseListenTogetherInvitation(value:String):Pair<String,String>?=runCatching{val uri=Uri.parse(value.trim());val room=uri.getQueryParameter("roomId").orEmpty();val inviter=uri.getQueryParameter("inviterId").orEmpty();if(room.isBlank()||inviter.isBlank())null else room to inviter}.getOrNull()
private fun shareListenTogether(context:Context,songId:Long,room:MeloXListenTogetherRoom){val inviter=room.creatorId.ifBlank{room.users.firstOrNull()?.id.orEmpty()};val url="https://st.music.163.com/listen-together/share/?songId=$songId&roomId=${Uri.encode(room.id)}&inviterId=${Uri.encode(inviter)}";runCatching{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,url),"分享一起听邀请").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}}
