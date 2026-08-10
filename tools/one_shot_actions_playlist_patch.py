from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
def read(p): return (ROOT/p).read_text()
def write(p,t):
    q=ROOT/p; q.parent.mkdir(parents=True,exist_ok=True); q.write_text(t)

# Extend playlist model with owner identity so Add-to-Playlist only offers writable playlists.
models="android/app/src/main/kotlin/com/lladlam/melox/core/library/NeteaseLibraryModels.kt"
t=read(models)
t=t.replace('''    val creatorName: String,
    val playCount: Long = 0L,''','''    val creatorName: String,
    val creatorUserId: Long? = null,
    val playCount: Long = 0L,''')
write(models,t)

cache="android/app/src/main/kotlin/com/lladlam/melox/core/library/NeteaseLibraryCache.kt"
t=read(cache)
t=t.replace('''.put("creatorName", value.creatorName)
    .put("playCount", value.playCount)''','''.put("creatorName", value.creatorName)
    .put("creatorUserId", value.creatorUserId)
    .put("playCount", value.playCount)''')
t=t.replace('''    creatorName = value.optString("creatorName"),
    playCount = value.optLong("playCount"),''','''    creatorName = value.optString("creatorName"),
    creatorUserId = value.optLong("creatorUserId", -1L).takeIf { it > 0L },
    playCount = value.optLong("playCount"),''')
write(cache,t)

client="android/app/src/main/kotlin/com/lladlam/melox/core/library/NeteaseLibraryClient.kt"
t=read(client)
t=t.replace('''        val desiredIds = buildList {
            for (index in 0 until minOf(trackIds.length(), 100)) {''','''        val desiredIds = buildList {
            for (index in 0 until trackIds.length()) {''')
t=t.replace('''        if (missing.isNotEmpty()) {
            songDetailsBlocking(missing).forEach { byId[it.id] = it }
        }''','''        if (missing.isNotEmpty()) {
            missing.chunked(100).forEach { page ->
                songDetailsBlocking(page).forEach { byId[it.id] = it }
            }
        }''')
t=t.replace('''            creatorName = value.optJSONObject("creator")
                ?.optString("nickname")
                .orEmpty(),
            playCount = value.optLong("playCount", 0L).coerceAtLeast(0L),''','''            creatorName = value.optJSONObject("creator")
                ?.optString("nickname")
                .orEmpty(),
            creatorUserId = value.optJSONObject("creator")
                ?.optLong("userId", -1L)
                ?.takeIf { it > 0L },
            playCount = value.optLong("playCount", 0L).coerceAtLeast(0L),''')
write(client,t)

# Playback queue mutations used by upstream playlist/NowPlaying actions.
play="android/app/src/main/kotlin/com/lladlam/melox/playback/PlaybackCommands.kt"
t=read(play)
insert='''
    fun addToQueue(context: Context, song: SearchSong) {
        val quality = MusicQualityPreferences.read(context.applicationContext)
        val controller = activeController
        if (controller == null) {
            playQueue(context, listOf(song), song.id)
            return
        }
        controller.addMediaItem(song.toMediaItem(quality))
    }

    fun playNext(context: Context, song: SearchSong) {
        val quality = MusicQualityPreferences.read(context.applicationContext)
        val controller = activeController
        if (controller == null) {
            playQueue(context, listOf(song), song.id)
            return
        }
        val insertion = (controller.currentMediaItemIndex + 1).coerceIn(0, controller.mediaItemCount)
        controller.addMediaItem(insertion, song.toMediaItem(quality))
    }
'''
marker='''    /**
     * Persist a MeloX quality choice'''
if insert.strip() not in t:
    t=t.replace(marker,insert+'\n'+marker)
write(play,t)

ops=r'''package com.lladlam.melox.core.network

import com.lladlam.melox.core.account.NeteaseSessionStore
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class MeloXMusicComment(
    val id: Long,
    val user: String,
    val avatarUrl: String?,
    val content: String,
    val likedCount: Long,
    val timeText: String,
)

data class MeloXWikiSection(val title: String, val lines: List<String>)

/** Authenticated write/comment/wiki routes mirrored from upstream MeloX NeteaseAPI. */
class NeteaseMusicOperationsClient(
    private val cookieProvider: () -> String,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val syntheticDeviceId = randomHex(26).uppercase()

    suspend fun setSongLiked(songId: Long, liked: Boolean) = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        eapi(
            "/api/radio/like",
            JSONObject().put("alg", "itembased").put("trackId", songId).put("like", liked).put("time", "3"),
            true,
        )
        Unit
    }

    suspend fun addSongToPlaylist(songId: Long, playlistId: Long) = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val path = "/api/v1/playlist/manipulate/tracks"
        val id = songId.toString()
        val first = runCatching {
            eapi(path, JSONObject().put("op", "add").put("pid", playlistId)
                .put("trackIds", "[\"$id\"]").put("imme", "true"), true)
        }
        if (first.isFailure) {
            eapi(path, JSONObject().put("op", "add").put("pid", playlistId)
                .put("trackIds", "[\"$id\",\"$id\"]").put("imme", "true"), true)
        }
        Unit
    }

    suspend fun setPlaylistSubscribed(playlistId: Long, subscribed: Boolean) = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val path = if (subscribed) "/api/playlist/subscribe" else "/api/playlist/unsubscribe"
        val data = JSONObject().put("id", playlistId)
        eapi(path, data, true)
        Unit
    }

    suspend fun songComments(songId: Long, limit: Int = 40): List<MeloXMusicComment> = withContext(Dispatchers.IO) {
        val result = eapi(
            "/api/v1/resource/comments/R_SO_4_$songId",
            JSONObject().put("rid", songId).put("limit", limit).put("offset", 0).put("beforeTime", 0),
            NeteaseSessionStore.containsMusicU(cookieProvider()),
        )
        val hot = result.optJSONArray("hotComments") ?: JSONArray()
        val normal = result.optJSONArray("comments") ?: JSONArray()
        val seen = mutableSetOf<Long>()
        buildList {
            fun addArray(values: JSONArray) {
                for (i in 0 until values.length()) {
                    val c = values.optJSONObject(i) ?: continue
                    val id = c.optLong("commentId", -1L)
                    if (id <= 0L || !seen.add(id)) continue
                    val user = c.optJSONObject("user")
                    add(MeloXMusicComment(
                        id = id,
                        user = user?.optString("nickname").orEmpty().ifBlank { "网易云用户" },
                        avatarUrl = secure(user?.optString("avatarUrl")?.takeIf(String::isNotBlank)),
                        content = c.optString("content").ifBlank { "…" },
                        likedCount = c.optLong("likedCount", 0L),
                        timeText = c.optString("timeStr").ifBlank { "" },
                    ))
                }
            }
            addArray(hot); addArray(normal)
        }
    }

    suspend fun songWiki(songId: Long): List<MeloXWikiSection> = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val result = eapi(
            "/api/song/play/about/block/page",
            JSONObject().put("songId", songId),
            true,
        )
        val blocks = result.optJSONObject("data")?.optJSONArray("blocks") ?: JSONArray()
        buildList {
            for (i in 0 until blocks.length()) {
                val block = blocks.optJSONObject(i) ?: continue
                val title = block.optJSONObject("uiElement")?.optJSONObject("mainTitle")?.optString("title")
                    ?.takeIf(String::isNotBlank) ?: when (block.optString("code")) {
                        "SONG_PLAY_ABOUT_MUSIC_MEMORY" -> "音乐记忆"
                        "SONG_PLAY_ABOUT_SIMILAR_SONG" -> "相似歌曲"
                        "SONG_PLAY_ABOUT_RELATED_PLAYLIST" -> "相关歌单"
                        else -> "歌曲百科"
                    }
                val lines = mutableListOf<String>()
                val creatives = block.optJSONArray("creatives") ?: JSONArray()
                for (j in 0 until creatives.length()) {
                    val creative = creatives.optJSONObject(j) ?: continue
                    val ui = creative.optJSONObject("uiElement")
                    ui?.optJSONObject("mainTitle")?.optString("title")?.takeIf(String::isNotBlank)?.let(lines::add)
                    val resources = creative.optJSONArray("resources") ?: JSONArray()
                    for (k in 0 until resources.length()) {
                        val rui = resources.optJSONObject(k)?.optJSONObject("uiElement") ?: continue
                        rui.optJSONObject("mainTitle")?.optString("title")?.takeIf(String::isNotBlank)?.let(lines::add)
                        val descriptions = rui.optJSONArray("descriptions") ?: JSONArray()
                        for (x in 0 until descriptions.length()) {
                            descriptions.optJSONObject(x)?.optString("description")?.takeIf(String::isNotBlank)?.let(lines::add)
                        }
                        val subtitles = rui.optJSONArray("subTitles") ?: JSONArray()
                        for (x in 0 until subtitles.length()) {
                            subtitles.optJSONObject(x)?.optString("title")?.takeIf(String::isNotBlank)?.let(lines::add)
                        }
                    }
                }
                val directResources = block.optJSONArray("resources") ?: JSONArray()
                for (j in 0 until directResources.length()) {
                    val r = directResources.optJSONObject(j) ?: continue
                    r.optString("resourceType").takeIf(String::isNotBlank)?.let { type ->
                        val ext = r.optJSONObject("extensionInfo")
                        if (type.equals("FIRST_LISTEN", true)) {
                            ext?.optJSONObject("musicFirstListen")?.optString("date")?.takeIf(String::isNotBlank)?.let { lines.add("第一次听：$it") }
                        }
                        if (type.equals("TOTAL_PLAY", true)) {
                            ext?.optJSONObject("musicTotalPlay")?.let { total ->
                                val count = total.optLong("playCount", 0L)
                                if (count > 0) lines.add("累计播放：$count 次")
                            }
                        }
                    }
                }
                val unique = lines.map(String::trim).filter(String::isNotBlank).distinct()
                if (unique.isNotEmpty()) add(MeloXWikiSection(title, unique))
            }
        }
    }

    private fun ensureLoggedIn() {
        if (!NeteaseSessionStore.containsMusicU(cookieProvider())) throw IOException("请先登录网易云音乐")
    }

    private fun eapi(uri: String, data: JSONObject, authenticated: Boolean): JSONObject {
        val now=System.currentTimeMillis(); val cookie=cookieProvider(); val cookies=NeteaseSessionStore.parseCookie(cookie)
        val header=if(authenticated) authenticatedHeader(cookies,now) else JSONObject().put("os","ios").put("appver","9.0.90").put("osver","18.0").put("requestId","${now}_0000")
        val payload=JSONObject(data.toString()).put("header",header).put("e_r",false); val json=payload.toString()
        val digest=md5Hex("nobody${uri}use${json}md5forencrypt"); val encrypted="$uri-36cd479b6b5-$json-36cd479b6b5-$digest"
        val params=aes(encrypted.toByteArray(),"e82ckenh8dichen8".toByteArray()).toHex()
        val b=Request.Builder().url("https://interface.music.163.com${uri.replace("/api/","/eapi/")}")
            .header("Accept","*/*").header("User-Agent",if(authenticated) "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)" else "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148")
        if(authenticated)b.header("Cookie",encodedCookie(header))
        val req=b.post(FormBody.Builder().add("params",params).build()).build()
        httpClient.newCall(req).execute().use { response ->
            val body=response.body.string(); if(!response.isSuccessful)throw IOException("网易云请求失败：HTTP ${response.code}")
            if(body.isBlank())throw IOException("网易云返回了空响应"); val result=JSONObject(body); val code=result.optInt("code",response.code)
            if(code !in 200..299)throw IOException(result.optString("message").ifBlank{result.optString("msg")}.ifBlank{"请求失败（$code）"}); return result
        }
    }
    private fun authenticatedHeader(c:Map<String,String>,now:Long)=JSONObject().put("osver",c["osver"]?:"16.2").put("deviceId",c["deviceId"]?:syntheticDeviceId).put("os",c["os"]?:"iPhone OS").put("appver",c["appver"]?:"9.0.90").put("versioncode",c["versioncode"]?:"140").put("buildver",c["buildver"]?:(now/1000L).toString()).put("resolution",c["resolution"]?:"1170x2532").put("__csrf",c["__csrf"]?:"").put("channel",c["channel"]?:"distribution").put("requestId","${now}_${randomDigits(4)}").apply{c["MUSIC_U"]?.takeIf(String::isNotBlank)?.let{put("MUSIC_U",it)}}
    private fun encodedCookie(v:JSONObject)=buildList{val it=v.keys();while(it.hasNext())add(it.next())}.sorted().joinToString("; "){k->"${enc(k)}=${enc(v.optString(k))}"}
    private fun enc(v:String)=URLEncoder.encode(v,Charsets.UTF_8.name()).replace("+","%20")
    private fun secure(v:String?)=v?.let{if(it.startsWith("http://",true))"https://${it.substringAfter("://")}" else it}
    private fun randomHex(n:Int):String{val b=ByteArray(n);SecureRandom().nextBytes(b);return b.joinToString(""){"%02x".format(it)}}
    private fun randomDigits(n:Int)=buildString(n){repeat(n){append(('0'.code+SecureRandom().nextInt(10)).toChar())}}
    private fun md5Hex(v:String)=MessageDigest.getInstance("MD5").digest(v.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun aes(d:ByteArray,k:ByteArray)=Cipher.getInstance("AES/ECB/PKCS5Padding").run{init(Cipher.ENCRYPT_MODE,SecretKeySpec(k,"AES"));doFinal(d)}
    private fun ByteArray.toHex()=joinToString(""){"%02X".format(it)}
}
'''
write("android/app/src/main/kotlin/com/lladlam/melox/core/network/NeteaseMusicOperationsClient.kt",ops)

# Generic Liquid Glass song action overlay shared by Now Playing and playlist track rows.
overlay=r'''package com.lladlam.melox.ui.player

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
    val library = remember(app) { NeteaseLibraryClient { NeteaseSessionStore.readCookie(app) } }
    val ops = remember(app) { NeteaseMusicOperationsClient { NeteaseSessionStore.readCookie(app) } }
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
'''
write("android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXSongActionsOverlay.kt",overlay)

# Now Playing wrapper around the shared action overlay.
now=r'''package com.lladlam.melox.ui.player

import androidx.compose.runtime.Composable
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXSearchKind

@Composable
fun MeloXNowPlayingActionsSheet(
    state: MeloXPlaybackUiState,
    visible: Boolean,
    onDismiss: () -> Unit,
    onNavigateSearch: ((String, MeloXSearchKind) -> Unit)? = null,
) {
    val id = state.mediaId?.toLongOrNull() ?: -1L
    val song = SearchSong(
        id = id,
        name = state.title.ifBlank { "正在播放" },
        artists = state.artist,
        album = state.album,
        artworkUrl = state.artworkUrl,
        durationMs = state.durationMs,
    )
    MeloXSongActionsOverlay(
        song = song,
        queue = emptyList(),
        visible = visible && id > 0L,
        onDismiss = onDismiss,
        playbackState = state,
        onNavigateSearch = onNavigateSearch,
    )
}
'''
write("android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXNowPlayingActionsSheet.kt",now)

# Search launch bus so NowPlaying can route album/artist actions to the native Search tab.
search="android/app/src/main/kotlin/com/lladlam/melox/ui/search/SearchScreen.kt"
t=read(search)
if 'object MeloXSearchLaunchBus' not in t:
    t=t.replace('''private val SearchAccent = Color(0xFFFF3147)''','''data class MeloXSearchLaunch(val query: String, val kind: MeloXSearchKind, val nonce: Long = System.nanoTime())
object MeloXSearchLaunchBus {
    var request by mutableStateOf<MeloXSearchLaunch?>(null)
        private set
    fun post(query: String, kind: MeloXSearchKind) { request = MeloXSearchLaunch(query, kind) }
    fun consume(request: MeloXSearchLaunch) { if (this.request == request) this.request = null }
}

private val SearchAccent = Color(0xFFFF3147)''')
    t=t.replace('''    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {''','''    var error by remember { mutableStateOf<String?>(null) }
    val launchRequest = MeloXSearchLaunchBus.request

    LaunchedEffect(launchRequest) {
        launchRequest?.let { request ->
            query = request.query
            kind = request.kind
            MeloXSearchLaunchBus.consume(request)
        }
    }

    LaunchedEffect(Unit) {''')
write(search,t)

# Wire the native Search navigation callback from NowPlaying to app root.
app="android/app/src/main/kotlin/com/lladlam/melox/ui/MeloXApp.kt"
t=read(app)
if 'MeloXSearchLaunchBus' not in t:
    t=t.replace('''import com.lladlam.melox.ui.search.SearchScreen
''','''import com.lladlam.melox.ui.search.SearchScreen
import com.lladlam.melox.ui.search.MeloXSearchLaunchBus
''')
if 'MeloXSearchKind' not in t:
    t=t.replace('''import com.lladlam.melox.ui.settings.SettingsScreen
''','''import com.lladlam.melox.ui.settings.SettingsScreen
import com.lladlam.melox.core.network.MeloXSearchKind
''')
t=t.replace('''                    state = playbackState,
                    onDismiss = closePlayer,''','''                    state = playbackState,
                    onDismiss = closePlayer,
                    onNavigateSearch = { query, kind ->
                        MeloXSearchLaunchBus.post(query, kind)
                        selectedTab = AppTab.Search
                        closePlayer()
                    },''')
write(app,t)

host="android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSNowPlayingSharedHost.kt"
t=read(host)
if 'MeloXSearchKind' not in t:
    t=t.replace('''import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
''','''import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.core.network.MeloXSearchKind
''')
t=t.replace('''    state: MeloXPlaybackUiState,
    onDismiss: () -> Unit,
    onSeekCollapse:''','''    state: MeloXPlaybackUiState,
    onDismiss: () -> Unit,
    onNavigateSearch: (String, MeloXSearchKind) -> Unit = { _, _ -> },
    onSeekCollapse:''')
t=t.replace('''                visible = showActions,
                onDismiss = { showActions = false },
            )''','''                visible = showActions,
                onDismiss = { showActions = false },
                onNavigateSearch = onNavigateSearch,
            )''')
write(host,t)

# Playlist actions overlay.
playlist_overlay=r'''package com.lladlam.melox.ui.library

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.ui.glass.meloXLiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MeloXPlaylistActionsOverlay(
    playlist: NeteasePlaylistSummary,
    visible: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context=LocalContext.current; val app=context.applicationContext; val scope=rememberCoroutineScope()
    val client=remember(app){NeteaseLibraryClient{NeteaseSessionStore.readCookie(app)}}
    val ops=remember(app){NeteaseMusicOperationsClient{NeteaseSessionStore.readCookie(app)}}
    val account=remember(app){NeteaseSearchClient(cookieProvider={NeteaseSessionStore.readCookie(app)})}
    var subscribed by remember(playlist.id,visible){mutableStateOf<Boolean?>(null)}
    var busy by remember(playlist.id,visible){mutableStateOf(false)}
    var message by remember(playlist.id,visible){mutableStateOf<String?>(null)}
    LaunchedEffect(visible,playlist.id){if(!visible)return@LaunchedEffect;runCatching{val p=account.accountProfile();withContext(Dispatchers.IO){client.userPlaylistsBlocking(p.userId)}.any{it.id==playlist.id}}.onSuccess{subscribed=it}}
    BackHandler(enabled=visible,onBack=onDismiss)
    AnimatedVisibility(visible=visible,enter=fadeIn(spring(stiffness=520f)),exit=fadeOut(spring(stiffness=620f))){
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.20f)).clickable(interactionSource=remember{MutableInteractionSource()},indication=null,onClick=onDismiss).padding(horizontal=18.dp).navigationBarsPadding(),contentAlignment=Alignment.BottomCenter){
            Column(Modifier.fillMaxWidth().padding(bottom=18.dp).meloXLiquidButton(shape=RoundedCornerShape(30.dp),tint=Color.White.copy(alpha=.08f),surfaceColor=Color.Black.copy(alpha=.12f),blurRadius=14.dp,lensRadius=20.dp,refractionHeight=22.dp).clickable(interactionSource=remember{MutableInteractionSource()},indication=null,onClick={}).padding(horizontal=18.dp,vertical=18.dp)){
                Text("歌单操作",color=Color.White.copy(alpha=.58f),fontSize=13.sp);Text(playlist.name,color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=3.dp,bottom=10.dp))
                message?.let{Text(it,color=Color(0xFFFF8A90),fontSize=12.sp)}
                PAction("分享歌单","↗"){sharePlaylist(context,playlist);onDismiss()}
                PAction(if(subscribed==true)"取消收藏歌单" else "收藏歌单",if(subscribed==true)"✓" else "+"){
                    if(busy)return@PAction;val desired=subscribed!=true;busy=true;scope.launch{runCatching{ops.setPlaylistSubscribed(playlist.id,desired)}.onSuccess{subscribed=desired}.onFailure{message=it.message};busy=false}
                }
                PAction("刷新","↻"){onRefresh();onDismiss()}
                if(busy)Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(18.dp),color=Color.White,strokeWidth=2.dp);Spacer(Modifier.size(10.dp));Text("正在处理",color=Color.White.copy(alpha=.6f))}
            }
        }
    }
}
@Composable private fun PAction(title:String,symbol:String,onClick:()->Unit){Row(Modifier.fillMaxWidth().height(48.dp).clickable(onClick=onClick).padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Box(Modifier.size(28.dp),contentAlignment=Alignment.Center){Text(symbol,color=Color.White,fontSize=19.sp)};Text(title,color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Medium)}}
private fun sharePlaylist(context:Context,p:NeteasePlaylistSummary){runCatching{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"${p.name}\nhttps://music.163.com/playlist?id=${p.id}"),"分享歌单").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}}
'''
write("android/app/src/main/kotlin/com/lladlam/melox/ui/library/MeloXPlaylistActionsOverlay.kt",playlist_overlay)

# Targeted LibraryScreen wiring: toolbar/hero/track ellipsis and overlays.
lib="android/app/src/main/kotlin/com/lladlam/melox/ui/library/LibraryScreen.kt"
t=read(lib)
if 'import com.lladlam.melox.ui.player.MeloXSongActionsOverlay' not in t:
    t=t.replace('''import com.lladlam.melox.ui.player.MeloXFlowingLightBackdrop
''','''import com.lladlam.melox.ui.player.MeloXFlowingLightBackdrop
import com.lladlam.melox.ui.player.MeloXSongActionsOverlay
''')
# state vars
t=t.replace('''    var searchQuery by remember(initialPlaylist.id) { mutableStateOf("") }
    var palette''','''    var searchQuery by remember(initialPlaylist.id) { mutableStateOf("") }
    var showPlaylistActions by remember(initialPlaylist.id) { mutableStateOf(false) }
    var selectedTrackAction by remember(initialPlaylist.id) { mutableStateOf<SearchSong?>(null) }
    var palette''')
# toolbar call
t=t.replace('''            MeloXPlaylistToolbar(
                foreground = foreground,
                onBack = onBack,
            )''','''            MeloXPlaylistToolbar(
                foreground = foreground,
                onBack = onBack,
                onShare = { sharePlaylistFromDetail(context, displayed) },
                onMore = { showPlaylistActions = true },
            )''')
# hero add callback
t=t.replace('''                        onShuffle = {
                            val shuffled = songs.shuffled()''','''                        onShuffle = {
                            val shuffled = songs.shuffled()''')
t=t.replace('''                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )''','''                        onMore = { showPlaylistActions = true },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )''',1)
# track row onMore
t=t.replace('''                            onClick = {
                                PlaybackCommands.playQueue(
                                    context = context,
                                    songs = filteredSongs,
                                    selectedSongId = song.id,
                                    onFailure = { errorMessage = it.message ?: "播放失败" },
                                )
                            },
                        )''','''                            onClick = {
                                PlaybackCommands.playQueue(
                                    context = context,
                                    songs = filteredSongs,
                                    selectedSongId = song.id,
                                    onFailure = { errorMessage = it.message ?: "播放失败" },
                                )
                            },
                            onMore = { selectedTrackAction = song },
                        )''')
# append overlays after Column inside PullToRefresh Box
needle='''            }
        }
    }
}

@Composable
private fun MeloXPlaylistToolbar('''
replacement='''            }
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
            )
        }
    }
}

@Composable
private fun MeloXPlaylistToolbar('''
if needle not in t: raise RuntimeError('playlist overlay insertion point missing')
t=t.replace(needle,replacement,1)
# toolbar signature and noops
t=t.replace('''private fun MeloXPlaylistToolbar(
    foreground: Color,
    onBack: () -> Unit,
) {''','''private fun MeloXPlaylistToolbar(
    foreground: Color,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
) {''')
# first two clickable {} in toolbar exact
t=t.replace('''.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
                contentAlignment = Alignment.Center,
            ) {
                MeloXShareGlyph''','''.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onShare,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                MeloXShareGlyph''',1)
t=t.replace('''.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "•••",''','''.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onMore,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "•••",''',1)
# hero signature + plus
t=t.replace('''    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    sharedTransitionScope''','''    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onMore: () -> Unit,
    sharedTransitionScope''')
t=t.replace('''                MeloXGlassCircleButton(
                    foreground = foreground,
                    size = 54.dp,
                    onClick = {},
                ) {''','''                MeloXGlassCircleButton(
                    foreground = foreground,
                    size = 54.dp,
                    onClick = onMore,
                ) {''',1)
# track row signature and click
t=t.replace('''private fun MeloXPlaylistTrackRow(
    song: SearchSong,
    index: Int,
    foreground: Color,
    onClick: () -> Unit,
) {''','''private fun MeloXPlaylistTrackRow(
    song: SearchSong,
    index: Int,
    foreground: Color,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {''')
t=t.replace('''.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "•••",''','''.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onMore,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "•••",''',1)
# helper share
if 'private fun sharePlaylistFromDetail' not in t:
    t += '''\nprivate fun sharePlaylistFromDetail(context: android.content.Context, playlist: NeteasePlaylistSummary) {\n    runCatching {\n        context.startActivity(\n            android.content.Intent.createChooser(\n                android.content.Intent(android.content.Intent.ACTION_SEND)\n                    .setType("text/plain")\n                    .putExtra(android.content.Intent.EXTRA_TEXT, "${playlist.name}\\nhttps://music.163.com/playlist?id=${playlist.id}"),\n                "分享歌单",\n            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),\n        )\n    }\n}\n'''
write(lib,t)

(ROOT/"tools/one_shot_actions_playlist_patch.py").unlink(missing_ok=True)
(ROOT/".github/workflows/one-shot-actions-playlist-patch.yml").unlink(missing_ok=True)
