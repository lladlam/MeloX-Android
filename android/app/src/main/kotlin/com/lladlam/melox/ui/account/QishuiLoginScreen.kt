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
import androidx.compose.ui.res.stringResource
import com.lladlam.melox.R
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
    val loadingText = stringResource(R.string.qishui_qr_loading)
    var status by remember { mutableStateOf(loadingText) }
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
        qr = null; error = null; status = loadingText
        try {
            val created = api.createQr()
            qr = created
            status = context.getString(R.string.qishui_qr_scan_instruction)
            repeat(100) {
                delay(2_000)
                val state = api.pollQr(created.token)
                if (state.sessionId.isNotBlank()) {
                    val temporary = QishuiSessionStore.read(context).copy(
                        sessionId = state.sessionId,
                        cookie = state.cookie,
                    )
                    val profile = QishuiApiClient({ temporary }).profile() ?: throw IllegalStateException(context.getString(R.string.qishui_profile_missing))
                    coroutineContext.ensureActive()
                    QishuiSessionStore.save(context, state.sessionId, state.cookie, profile.id, profile.displayName)
                    latestOnLoggedIn()
                    return@LaunchedEffect
                }
                when (state.status.lowercase()) {
                    "expired", "cancelled", "canceled", "refused" -> {
                        status = context.getString(R.string.qishui_qr_expired)
                        return@LaunchedEffect
                    }
                    "scanned", "scaned" -> status = context.getString(R.string.qishui_qr_scanned)
                    "confirmed" -> status = context.getString(R.string.qishui_qr_confirmed)
                }
            }
            status = context.getString(R.string.qishui_qr_timeout)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: context.getString(R.string.qishui_login_failed)
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
    MeloXPinnedListPage(title = stringResource(R.string.qishui_login_title), onNavigateBack = onDismiss, bottomPadding = MeloXBottomContentClearance) {
        item {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(stringResource(R.string.qishui_account_title), style = MaterialTheme.typography.titleLarge)
                if (bitmap != null) Image(bitmap.asImageBitmap(), stringResource(R.string.qishui_qr_content_description), Modifier.size(240.dp))
                else if (error == null && qr == null) CircularProgressIndicator()
                Text(error ?: status, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f))
                Text(stringResource(R.string.qishui_qr_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
                MeloXGlassButton(onClick = { refresh++ }, style = MeloXGlassButtonStyle.BorderedProminent) { Text(stringResource(R.string.qishui_refresh_qr)) }
                MeloXLegalLinks()
            }
        }
    }
}
