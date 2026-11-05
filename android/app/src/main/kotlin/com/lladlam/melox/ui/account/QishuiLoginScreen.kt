package com.lladlam.melox.ui.account

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lladlam.melox.core.provider.qishui.QishuiApiClient
import com.lladlam.melox.core.provider.qishui.QishuiSessionStore
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXPinnedListPage
import com.lladlam.melox.ui.legal.MeloXLegalLinks
import com.lladlam.melox.ui.MeloXBottomContentClearance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

@Composable
fun QishuiLoginScreen(onDismiss: () -> Unit, onLoggedIn: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val api = remember { QishuiApiClient({ QishuiSessionStore.read(context) }) }
    var refresh by remember { mutableIntStateOf(0) }
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var qr by remember { mutableStateOf<QishuiApiClient.Qr?>(null) }
    var status by remember { mutableStateOf("正在获取二维码…") }
    var error by remember { mutableStateOf<String?>(null) }
    val latestOnLoggedIn by rememberUpdatedState(onLoggedIn)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(refresh, resumed) {
        if (!resumed) return@LaunchedEffect
        qr = null; error = null; status = "正在获取二维码…"
        try {
            val created = api.createQr()
            qr = created
            status = "请使用已登录的汽水音乐 App 扫码，并在手机上确认"
            repeat(100) {
                delay(2_000)
                val state = api.pollQr(created.token)
                if (state.sessionId.isNotBlank()) {
                    val temporary = QishuiSessionStore.read(context).copy(
                        sessionId = state.sessionId,
                        cookie = state.cookie,
                    )
                    val profile = QishuiApiClient({ temporary }).profile() ?: throw IllegalStateException("汽水音乐没有返回账号资料")
                    coroutineContext.ensureActive()
                    QishuiSessionStore.save(context, state.sessionId, state.cookie, profile.id, profile.displayName)
                    latestOnLoggedIn()
                    return@LaunchedEffect
                }
                when (state.status.lowercase()) {
                    "expired", "cancelled", "canceled", "refused" -> {
                        status = "二维码已失效或已取消，请刷新后重试"
                        return@LaunchedEffect
                    }
                    "scanned", "scaned" -> status = "已扫码，请在汽水音乐 App 中确认登录"
                    "confirmed" -> status = "已确认，正在等待汽水音乐登录会话…"
                }
            }
            status = "二维码已超时，请刷新后重试"
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "汽水音乐登录失败，请重试"
        }
    }
    val bitmap = remember(qr) { qr?.let { value ->
        runCatching {
            if (value.image.startsWith("data:image/")) {
                val bytes = Base64.decode(value.image.substringAfter(','), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else {
                val content = value.link.ifBlank { value.image }
                require(content.isNotBlank())
                val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 640, 640)
                Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).apply {
                    setPixels(IntArray(640 * 640) { index -> if (matrix[index % 640, index / 640]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }, 0, 640, 0, 0, 640, 640)
                }
            }
        }.getOrNull()
    } }
    MeloXPinnedListPage(title = "登录汽水音乐", onNavigateBack = onDismiss, bottomPadding = MeloXBottomContentClearance) {
        item {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("汽水音乐账号", style = MaterialTheme.typography.titleLarge)
                if (bitmap != null) Image(bitmap.asImageBitmap(), "汽水音乐登录二维码", Modifier.size(240.dp))
                else if (error == null && qr == null) CircularProgressIndicator()
                Text(error ?: status, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f))
                Text("请使用汽水音乐手机客户端扫码。抖音扫码会打开已失效的旧页面并提示\"无法访问页面\"；二维码过期时点下方刷新。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
                MeloXGlassButton(onClick = { refresh++ }, style = MeloXGlassButtonStyle.BorderedProminent) { Text("刷新二维码") }
                MeloXLegalLinks()
            }
        }
    }
}
