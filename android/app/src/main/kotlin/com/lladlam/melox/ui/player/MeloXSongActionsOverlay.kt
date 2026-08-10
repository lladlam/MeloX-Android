package com.lladlam.melox.ui.player

import android.content.Context
import android.content.Intent
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
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXMusicComment
import com.lladlam.melox.core.network.MeloXSearchKind
import com.lladlam.melox.core.network.MeloXWikiSection
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.glass.meloXLiquidButton
import kotlinx.coroutines.Dispatchers
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
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val library = remember(app) { NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie(app) }) }
    val ops = remember(app) { NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(app) }) }
    val account = remember(app) { NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(app) }) }
    var page by remember(song.id, visible) { mutableStateOf(SongActionPage.Main) }
    var busy by remember(song.id, visible) { mutableStateOf(false) }
    var message by remember(song.id, visible) { mutableStateOf<String?>(null) }
    var liked by remember(song.id, visible) { mutableStateOf<Boolean?>(null) }
    var writablePlaylists by remember(song.id, visible) { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var comments by remember(song.id, visible) { mutableStateOf<List<MeloXMusicComment>>(emptyList()) }
    var wiki by remember(song.id, visible) { mutableStateOf<List<MeloXWikiSection>>(emptyList()) }

    LaunchedEffect(visible, song.id) {
        if (!visible) return@LaunchedEffect
        runCatching {
            val profile = account.accountProfile()
            val ids = withContext(Dispatchers.IO) { library.likedSongIdsBlocking(profile.userId) }
            liked = song.id in ids
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
            AnimatedContent(
                targetState=page,
                transitionSpec={ (fadeIn(spring(stiffness=520f))+scaleIn(initialScale=.96f)) togetherWith (fadeOut(spring(stiffness=620f))+scaleOut(targetScale=.96f)) },
                modifier=Modifier.fillMaxWidth().padding(bottom=18.dp).meloXLiquidButton(
                    shape=RoundedCornerShape(30.dp),tint=Color.White.copy(alpha=.08f),surfaceColor=Color.Black.copy(alpha=.12f),blurRadius=14.dp,lensRadius=20.dp,refractionHeight=22.dp,
                ).clickable(interactionSource=remember{MutableInteractionSource()},indication=null,onClick={}),
                label="song-action-page",
            ) { target ->
                Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=18.dp)) {
                    ActionHeader(song, when(target){SongActionPage.Main->"歌曲操作";SongActionPage.Sleep->"定时关闭";SongActionPage.AddToPlaylist->"添加到歌单";SongActionPage.Comments->"评论";SongActionPage.Wiki->"歌曲百科";SongActionPage.ListenTogether->"一起听"})
                    message?.let { Text(it,color=Color(0xFFFF8A90),fontSize=12.sp,modifier=Modifier.padding(bottom=6.dp)) }
                    when(target) {
                        SongActionPage.Main -> {
                            playbackState?.let { ActionItem("定时关闭", "◷") { page=SongActionPage.Sleep } }
                            if (playbackState == null) ActionItem("下一首播放", "⇥") { PlaybackCommands.playNext(context,song); onDismiss() }
                            ActionItem("添加到播放队列", "+") { if(playbackState!=null)playbackState.addCurrentToQueue() else PlaybackCommands.addToQueue(context,song); onDismiss() }
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
                                items(wiki,key={it.title+it.lines.hashCode()}) { section -> Column(Modifier.fillMaxWidth().padding(vertical=9.dp)){Text(section.title,color=Color.White,fontWeight=FontWeight.Bold,fontSize=16.sp);section.lines.take(12).forEach{line->Text(line,color=Color.White.copy(alpha=.70f),fontSize=13.sp,modifier=Modifier.padding(top=4.dp))}} }
                            }
                            if(!busy&&wiki.isEmpty()&&message==null) Text("暂无百科资料",color=Color.White.copy(alpha=.5f),modifier=Modifier.padding(12.dp))
                            ActionItem("返回","‹"){page=SongActionPage.Main}
                        }
                        SongActionPage.ListenTogether -> {
                            Text("上游 MeloX 的“一起听”包含房间同步协议。Android 当前先提供可用的邀请分享入口；房间级进度同步不会伪装成已完成。",color=Color.White.copy(alpha=.62f),fontSize=13.sp,modifier=Modifier.padding(6.dp,6.dp,6.dp,12.dp))
                            ActionItem("分享当前歌曲邀请", "↗") { shareSong(context,song); onDismiss() }
                            ActionItem("返回","‹"){page=SongActionPage.Main}
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
