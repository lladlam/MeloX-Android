package com.lladlam.melox.ui.account

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.lladlam.melox.core.provider.kugou.KugouLoginClient
import com.lladlam.melox.core.provider.kugou.KugouQrLoginSession
import com.lladlam.melox.core.provider.kugou.KugouQrLoginState
import com.lladlam.melox.core.provider.kugou.KugouSessionStore
import com.lladlam.melox.core.music.provider.PlaybackAccountSlot
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.legal.MeloXLegalLinks
import kotlinx.coroutines.delay

@Composable
fun KugouLoginScreen(
    onDismiss: () -> Unit,
    onLoggedIn: () -> Unit,
    targetSlot: PlaybackAccountSlot = PlaybackAccountSlot.Main,
) {
    val context = LocalContext.current.applicationContext
    val client = remember {
        KugouLoginClient(
            sessionProvider = { KugouSessionStore.read(context) },
        )
    }
    var qrSession by remember { mutableStateOf<KugouQrLoginSession?>(null) }
    var stateText by remember { mutableStateOf("正在生成登录二维码…") }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableStateOf(0) }

    BackHandler(onBack = onDismiss)

    LaunchedEffect(refreshToken) {
        error = null
        stateText = "正在生成登录二维码…"
        val created = runCatching { client.createQrSession() }
            .onFailure { error = it.message ?: "生成酷狗登录二维码失败" }
            .getOrNull() ?: return@LaunchedEffect
        qrSession = created
        stateText = "请使用酷狗音乐 App 扫码登录"

        while (true) {
            delay(1_500)
            val state = runCatching { client.checkQrSession(created.key) }
                .onFailure { error = it.message ?: "检查酷狗登录状态失败" }
                .getOrNull() ?: continue
            when (state) {
                KugouQrLoginState.Waiting -> stateText = "等待扫码…"
                KugouQrLoginState.Scanned -> stateText = "已扫码，请在酷狗音乐中确认登录"
                KugouQrLoginState.Expired -> {
                    stateText = "二维码已过期"
                    return@LaunchedEffect
                }
                is KugouQrLoginState.Authorized -> {
                    KugouSessionStore.updateLogin(
                        context = context,
                        token = state.token,
                        userId = state.userId,
                        vipToken = state.vipToken,
                        vipType = state.vipType,
                        playback = targetSlot == PlaybackAccountSlot.Playback,
                    )
                    stateText = "登录成功"
                    onLoggedIn()
                    return@LaunchedEffect
                }
                is KugouQrLoginState.Unknown -> stateText = "等待酷狗音乐确认（${state.status}）"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "取消",
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .meloXLiquidButton(
                        shape = RoundedCornerShape(18.dp),
                        tint = Color(0xFFFF3147),
                        surfaceColor = Color(0xFFFF3147).copy(alpha = 0.08f),
                        lensRadius = 7.dp,
                        refractionHeight = 11.dp,
                    )
                    .clickable(onClick = onDismiss)
                    .padding(8.dp),
                color = Color(0xFFFF3147),
                fontSize = 16.sp,
            )
            Text("登录酷狗音乐", fontSize = 17.sp)
            Text("取消", modifier = Modifier.padding(8.dp), color = Color.Transparent, fontSize = 16.sp)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val currentQr = qrSession
                if (currentQr == null) {
                    CircularProgressIndicator()
                } else {
                    val bitmap = remember(currentQr.qrContentUrl) {
                        createQrBitmap(currentQr.qrContentUrl, 720)
                    }
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "酷狗音乐登录二维码",
                        modifier = Modifier
                            .size(260.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .padding(12.dp),
                    )
                }
                Spacer(Modifier.size(22.dp))
                Text(
                    text = stateText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "登录凭证仅保存在本机，不会上传到 MeloX 服务器。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                    textAlign = TextAlign.Center,
                )
                error?.let { message ->
                    Spacer(Modifier.size(14.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                if (stateText == "二维码已过期" || error != null) {
                    Spacer(Modifier.size(18.dp))
                    Text(
                        text = "重新生成",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .meloXLiquidButton(
                                shape = RoundedCornerShape(20.dp),
                                surfaceColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            )
                            .clickable {
                                qrSession = null
                                error = null
                                refreshToken += 1
                            }
                            .padding(horizontal = 20.dp, vertical = 11.dp),
                    )
                }
            }
        }
        MeloXLegalLinks(
            modifier = Modifier.padding(vertical = 6.dp),
            tint = Color(0xFF16A9FF),
        )
    }
}

private fun createQrBitmap(content: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        ),
    )
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
