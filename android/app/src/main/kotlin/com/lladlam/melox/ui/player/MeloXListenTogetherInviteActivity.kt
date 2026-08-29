package com.lladlam.melox.ui.player
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lladlam.melox.ui.MeloXPredictiveBackPage
import com.lladlam.melox.ui.prepareMeloXPagePredictiveBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.playback.MeloXListenTogetherCoordinator
 import com.lladlam.melox.ui.theme.MeloXTheme
import kotlinx.coroutines.launch
class MeloXListenTogetherInviteActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); prepareMeloXPagePredictiveBack(); val room = intent.getStringExtra("room").orEmpty(); val inviter = intent.getStringExtra("inviter").orEmpty(); if (room.isBlank() || inviter.isBlank()) { finish(); return }; setContent { MeloXPredictiveBackPage(onBack = ::finish) { MeloXTheme { val context = LocalContext.current; val scope = rememberCoroutineScope(); var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; AlertDialog(onDismissRequest = { if (!busy) finish() }, title = { Text("发现一起听邀请") }, text = { if (busy) CircularProgressIndicator() else Text(error ?: "房间 $room\n是否加入？") }, dismissButton = { TextButton(onClick = ::finish, enabled = !busy) { Text("取消") } }, confirmButton = { TextButton(enabled = !busy, onClick = { busy = true; scope.launch { val ops = NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(context.applicationContext) }); runCatching { ops.joinListenTogetherRoom(room, inviter) }.onSuccess { MeloXListenTogetherCoordinator.adoptRoom(context.applicationContext, it); finish() }.onFailure { error = it.message ?: "加入房间失败" }; busy = false } }) { Text("加入") } }) } } } }
 companion object { fun launch(context: Context, room: String, inviter: String) { context.startActivity(Intent(context, MeloXListenTogetherInviteActivity::class.java).putExtra("room", room).putExtra("inviter", inviter).apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } }
}
