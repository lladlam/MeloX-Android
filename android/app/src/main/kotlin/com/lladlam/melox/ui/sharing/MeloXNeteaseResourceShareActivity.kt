package com.lladlam.melox.ui.sharing

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.network.MeloXMessageContact
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.network.NeteaseSocialExtrasClient
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.glass.MeloXActionIcon
import com.lladlam.melox.ui.prepareMeloXPagePredictiveBack
import com.lladlam.melox.ui.theme.MeloXTheme
import kotlinx.coroutines.launch

private data class ShareResource(val type: String, val id: Long, val title: String, val url: String) {
    val supportsTimeline: Boolean get() = type == "song" || type == "playlist"
    val kindTitle: String get() = when (type) { "song" -> "歌曲"; "playlist" -> "歌单"; "album" -> "专辑"; else -> "内容" }
}

class MeloXNeteaseResourceShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(); prepareMeloXPagePredictiveBack()
        val direct = intent.getStringExtra(EXTRA_TYPE)?.let { type ->
            val id = intent.getLongExtra(EXTRA_ID, -1L); if (id > 0L) ShareResource(type, id, intent.getStringExtra(EXTRA_TITLE).orEmpty(), intent.getStringExtra(EXTRA_URL).orEmpty()) else null
        }
        val incoming = if (intent.action == Intent.ACTION_SEND) parseText(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()) else null
        val resource = direct ?: incoming
        if (resource == null || resource.type !in setOf("song", "playlist", "album")) { finish(); return }
        setContent { MeloXTheme { ShareScreen(resource, ::finish) } }
    }

    companion object {
        private const val EXTRA_TYPE = "resource_type"; private const val EXTRA_ID = "resource_id"; private const val EXTRA_TITLE = "resource_title"; private const val EXTRA_URL = "resource_url"
        fun launch(context: Context, type: String, id: Long, title: String, url: String) {
            if (type !in setOf("song", "playlist", "album") || id <= 0L) return
            context.startActivity(Intent(context, MeloXNeteaseResourceShareActivity::class.java).putExtra(EXTRA_TYPE, type).putExtra(EXTRA_ID, id).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_URL, url).apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
        private fun parseText(text: String): ShareResource? {
            val candidates = listOf("song" to Regex("(?:song(?:/|\\?id=)|songId=)(\\d+)", RegexOption.IGNORE_CASE), "playlist" to Regex("(?:playlist(?:/|\\?id=)|playlistId=)(\\d+)", RegexOption.IGNORE_CASE), "album" to Regex("(?:album(?:/|\\?id=)|albumId=)(\\d+)", RegexOption.IGNORE_CASE))
            for ((type, regex) in candidates) regex.find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }?.let { id -> return ShareResource(type, id, text.lineSequence().firstOrNull().orEmpty(), "https://music.163.com/$type?id=$id") }
            return null
        }
    }
}

@Composable private fun ShareScreen(resource: ShareResource, onBack: () -> Unit) {
    val context = LocalContext.current; val app = context.applicationContext; val cookieProvider = remember(app) { { NeteaseSessionStore.readCookie(app) } }; val ops = remember(app) { NeteaseMusicOperationsClient(cookieProvider = cookieProvider) }; val social = remember(app) { NeteaseSocialExtrasClient(cookieProvider = cookieProvider) }; val account = remember(app) { NeteaseSearchClient(cookieProvider = cookieProvider) }; val scope = rememberCoroutineScope()
    var contacts by remember(resource.id) { mutableStateOf<List<MeloXMessageContact>>(emptyList()) }; var loading by remember(resource.id) { mutableStateOf(true) }; var busy by remember(resource.id) { mutableStateOf(false) }; var message by remember(resource.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(resource.id) { val cookie = NeteaseSessionStore.readCookie(app); if (!NeteaseSessionStore.containsMusicU(cookie)) { message = "登录网易云音乐后可发送给好友或分享到动态。"; loading = false; return@LaunchedEffect }; runCatching { val profile = account.accountProfile(); ops.messageContacts(profile.userId) }.onSuccess { contacts = it }.onFailure { message = it.message ?: "联系人加载失败" }; loading = false }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(20.dp, 14.dp, 20.dp, 36.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).meloXLiquidButton(shape = CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { MeloXActionIcon("‹", Modifier.size(20.dp), MaterialTheme.colorScheme.onSurface) }; Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("分享${resource.kindTitle}", fontSize = 25.sp, fontWeight = FontWeight.Bold); Text(resource.title.ifBlank { "网易云音乐" }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f)) } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ShareAction("系统分享", Modifier.weight(1f), enabled = !busy) { val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${resource.title}\n${resource.url}"); val chooser = Intent.createChooser(send, "系统分享").putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MeloXNeteaseResourceShareActivity::class.java))); context.startActivity(chooser) }; if (resource.supportsTimeline) ShareAction("分享到动态", Modifier.weight(1f), enabled = !busy && NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie(app))) { busy = true; scope.launch { runCatching { social.shareResourceToTimeline(resource.type, resource.id) }.onSuccess { message = "已分享到网易云动态" }.onFailure { message = it.message ?: "动态分享失败" }; busy = false } } } }
        message?.let { item { Text(it, color = if (it.startsWith("已")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), fontSize = 13.sp) } }
        if (loading) item { Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        if (contacts.isNotEmpty()) item { Text("发送给网易云好友", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        items(contacts, key = { "contact-${it.id}" }) { contact -> Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { busy = true; scope.launch { runCatching { social.sendResourceToUser(resource.type, resource.id, contact.id) }.onSuccess { message = "已发送给 ${contact.name}" }.onFailure { message = it.message ?: "发送失败" }; busy = false } }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(contact.avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(CircleShape)); Column(Modifier.weight(1f).padding(start = 11.dp)) { Text(contact.name, fontWeight = FontWeight.SemiBold); if (contact.signature.isNotBlank()) Text(contact.signature, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f), fontSize = 12.sp) }; Text("发送", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) } }
    }
}
@Composable private fun ShareAction(title: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) = Box(modifier.height(46.dp).meloXLiquidButton(shape = RoundedCornerShape(20.dp), enabled = enabled).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) { Text(title, fontWeight = FontWeight.SemiBold) }
