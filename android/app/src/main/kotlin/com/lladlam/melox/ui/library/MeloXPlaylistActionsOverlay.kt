package com.lladlam.melox.ui.library

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
    val client=remember(app){NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie(app) })}
    val ops=remember(app){NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(app) })}
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
