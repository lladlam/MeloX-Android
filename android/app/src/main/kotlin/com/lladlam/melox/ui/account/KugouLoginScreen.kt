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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.lladlam.melox.core.provider.kugou.KugouLiteApiClient
import com.lladlam.melox.core.provider.kugou.KugouLoginClient
import com.lladlam.melox.core.provider.kugou.KugouPlatform
import com.lladlam.melox.core.provider.kugou.KugouQrLoginSession
import com.lladlam.melox.core.provider.kugou.KugouQrLoginState
import com.lladlam.melox.core.provider.kugou.KugouSessionStore
import com.lladlam.melox.core.music.provider.PlaybackAccountSlot
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.legal.MeloXLegalLinks
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

@Composable
fun KugouLoginScreen(
    onDismiss: () -> Unit,
    onLoggedIn: () -> Unit,
    targetSlot: PlaybackAccountSlot = PlaybackAccountSlot.Main,
    lite: Boolean = false,
) {
    val activityContext = LocalContext.current
    val context = activityContext.applicationContext
    val client = remember(lite) {
        KugouLoginClient(
            sessionProvider = {
                if (lite) KugouSessionStore.readLite(context) else KugouSessionStore.read(context)
            },
            platform = if (lite) KugouPlatform.Lite else KugouPlatform.Standard,
        )
    }
    var qrSession by remember { mutableStateOf<KugouQrLoginSession?>(null) }
    var stateText by remember { mutableStateOf(activityContext.getString(R.string.account_kugou_generating)) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableStateOf(0) }

    BackHandler(onBack = onDismiss)

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(refreshToken, resumed) {
        if (!resumed) return@LaunchedEffect
        error = null
        stateText = activityContext.getString(R.string.account_kugou_generating)
        val created = runCatching { client.createQrSession() }
            .onFailure { error = it.message ?: activityContext.getString(R.string.account_kugou_qr_failed) }
            .getOrNull() ?: return@LaunchedEffect
        qrSession = created
        stateText = activityContext.getString(R.string.account_kugou_scan)

        while (true) {
            delay(1_500)
            val state = runCatching { client.checkQrSession(created.key) }
                .onFailure { error = it.message ?: activityContext.getString(R.string.account_kugou_status_failed) }
                .getOrNull() ?: continue
            when (state) {
                KugouQrLoginState.Waiting -> stateText = activityContext.getString(R.string.account_waiting_scan)
                KugouQrLoginState.Scanned -> stateText = activityContext.getString(R.string.account_kugou_scanned)
                KugouQrLoginState.Expired -> {
                    stateText = activityContext.getString(R.string.account_qr_expired)
                    return@LaunchedEffect
                }
                is KugouQrLoginState.Authorized -> {
                    if (lite) {
                        KugouSessionStore.updateLiteLogin(
                            context = context,
                            token = state.token,
                            userId = state.userId,
                            vipToken = state.vipToken,
                            vipType = state.vipType,
                        )
                        // Fetch the account auth and concept-VIP state once so the
                        // first song can use the authorized URL path immediately.
                        runCatching {
                            KugouLiteApiClient(
                                sessionProvider = { KugouSessionStore.readLite(context) },
                                persistAuth = { auth ->
                                    KugouSessionStore.updateLiteEntitlements(context, auth = auth)
                                },
                                persistVip = { vip ->
                                    KugouSessionStore.updateLiteEntitlements(
                                        context = context,
                                        vipType = vip.vipType,
                                        vipExpiresAtEpochSeconds = vip.expiresAtEpochSeconds,
                                        superVip = vip.superVip,
                                        vipActive = vip.isVip,
                                        freeListenEligible = vip.freeListen,
                                    )
                                },
                            ).vipStatus(force = true)
                        }
                    } else {
                        KugouSessionStore.updateLogin(
                            context = context,
                            token = state.token,
                            userId = state.userId,
                            vipToken = state.vipToken,
                            vipType = state.vipType,
                            playback = targetSlot == PlaybackAccountSlot.Playback,
                        )
                    }
                    stateText = activityContext.getString(R.string.account_login_success)
                    onLoggedIn()
                    return@LaunchedEffect
                }
                is KugouQrLoginState.Unknown -> stateText = activityContext.getString(R.string.account_kugou_waiting_status, state.status)
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
                text = stringResource(R.string.action_cancel),
                modifier = Modifier
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
            Text(
                stringResource(if (lite) R.string.account_login_kugou_lite else R.string.account_login_kugou),
                fontSize = 17.sp,
            )
            Text(stringResource(R.string.action_cancel), modifier = Modifier.padding(8.dp), color = Color.Transparent, fontSize = 16.sp)
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
                        contentDescription = stringResource(R.string.account_kugou_qr),
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
                    text = stringResource(R.string.account_credentials_local),
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
                if (stateText == activityContext.getString(R.string.account_qr_expired) || error != null) {
                    Spacer(Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.account_regenerate),
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
