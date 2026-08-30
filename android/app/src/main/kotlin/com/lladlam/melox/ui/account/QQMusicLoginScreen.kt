package com.lladlam.melox.ui.account

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.lladlam.melox.core.music.provider.PlaybackAccountSlot
import com.lladlam.melox.core.provider.qqmusic.QQMusicApiClient
import com.lladlam.melox.core.provider.qqmusic.QQMusicQrLoginClient
import com.lladlam.melox.core.provider.qqmusic.QQMusicQrLoginMethod
import com.lladlam.melox.core.provider.qqmusic.QQMusicQrLoginSession
import com.lladlam.melox.core.provider.qqmusic.QQMusicQrLoginState
import com.lladlam.melox.core.provider.qqmusic.QQMusicSessionStore
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.legal.MeloXLegalLinks
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun QQMusicLoginScreen(
    onDismiss: () -> Unit,
    onLoggedIn: () -> Unit,
    targetSlot: PlaybackAccountSlot = PlaybackAccountSlot.Main,
) {
    val context = LocalContext.current.applicationContext
    val client = remember { QQMusicQrLoginClient() }
    val scope = rememberCoroutineScope()
    var methodName by rememberSaveable { mutableStateOf(QQMusicQrLoginMethod.QQ.name) }
    val method = QQMusicQrLoginMethod.valueOf(methodName)
    var qrSession by remember { mutableStateOf<QQMusicQrLoginSession?>(null) }
    var stateText by remember { mutableStateOf(method.loadingText()) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var actionFailed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableStateOf(0) }

    BackHandler(onBack = onDismiss)

    fun saveCurrentQr() {
        val current = qrSession ?: return
        saving = true
        actionMessage = null
        scope.launch {
            runCatchingCancellable {
                withContext(Dispatchers.IO) { saveQrImageToGallery(context, current) }
            }.onSuccess { location ->
                actionFailed = false
                actionMessage = "二维码已保存到 $location"
            }.onFailure { failure ->
                actionFailed = true
                actionMessage = failure.message ?: "二维码保存失败，请稍后重试"
            }
            saving = false
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            saveCurrentQr()
        } else {
            actionFailed = true
            actionMessage = "未获得存储权限，无法保存二维码"
        }
    }

    LaunchedEffect(method, refreshToken) {
        sessionLoop@ while (true) {
            qrSession = null
            error = null
            actionMessage = null
            stateText = method.loadingText()
            val created = runCatchingCancellable { client.createSession(method) }
                .onFailure { error = qrErrorMessage(it, "获取${method.displayName()}登录二维码失败") }
                .getOrNull() ?: return@LaunchedEffect
            qrSession = created
            stateText = method.waitingText()

            delay(500)
            while (true) {
                val state = runCatchingCancellable { client.checkSession(created) }
                    .onFailure { error = qrErrorMessage(it, "检查${method.displayName()}扫码状态失败") }
                    .getOrNull() ?: return@LaunchedEffect
                when (state) {
                    QQMusicQrLoginState.Waiting -> stateText = method.waitingText()
                    QQMusicQrLoginState.Scanned -> stateText = "已扫码，请在${method.displayName()}中确认"
                    QQMusicQrLoginState.Expired -> {
                        qrSession = null
                        stateText = method.loadingText()
                        delay(250)
                        continue@sessionLoop
                    }
                    QQMusicQrLoginState.Rejected -> {
                        stateText = "你已取消授权"
                        return@LaunchedEffect
                    }
                    is QQMusicQrLoginState.Authorized -> {
                        stateText = "正在验证 QQ音乐登录状态…"
                        val session = QQMusicSessionStore.parse(state.cookie)
                        val verified = runCatchingCancellable {
                            QQMusicApiClient(sessionProvider = { session }).accountProfile(session)
                        }.onFailure {
                            error = it.message ?: "QQ音乐登录状态验证失败"
                        }.isSuccess
                        if (!verified) return@LaunchedEffect
                        QQMusicSessionStore.write(
                            context = context,
                            cookie = state.cookie,
                            playback = targetSlot == PlaybackAccountSlot.Playback,
                        )
                        stateText = "登录成功"
                        onLoggedIn()
                        return@LaunchedEffect
                    }
                }
                delay(1_100)
            }
        }
    }

    LaunchedEffect(actionMessage) {
        if (actionMessage == null) return@LaunchedEffect
        delay(4_000)
        actionMessage = null
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
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "取消",
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .meloXLiquidButton(
                        shape = RoundedCornerShape(24.dp),
                        tint = Color(0xFFFF3147),
                        surfaceColor = Color(0xFFFF3147).copy(alpha = 0.08f),
                        lensRadius = 7.dp,
                        refractionHeight = 11.dp,
                    )
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 10.dp, vertical = 13.dp),
                color = Color(0xFFFF3147),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "登录 QQ音乐",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.size(48.dp))
        }

        QQMusicLoginMethodSelector(
            selectedMethod = method,
            onSelect = { selected ->
                if (selected != method) methodName = selected.name
            },
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val currentSession = qrSession
                if (currentSession == null) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    val bitmap = remember(currentSession) {
                        BitmapFactory.decodeByteArray(
                            currentSession.imageBytes,
                            0,
                            currentSession.imageBytes.size,
                        )
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QQ音乐${method.displayName()}登录二维码",
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White)
                                .padding(12.dp),
                        )
                    }
                }

                Spacer(Modifier.size(16.dp))
                val actionsEnabled = currentSession != null
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QrActionButton(
                        onClick = { refreshToken += 1 },
                        icon = MeloXSymbol.Refresh,
                        label = "刷新二维码",
                        tint = method.accentColor(),
                        modifier = Modifier.weight(1f),
                    )
                    QrActionButton(
                        onClick = {
                            if (
                                Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                saveCurrentQr()
                            }
                        },
                        icon = MeloXSymbol.Download,
                        label = if (saving) "正在保存…" else "保存到相册",
                        tint = method.accentColor(),
                        enabled = actionsEnabled && !saving,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.size(20.dp))
                Text(
                    text = stateText,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = method.instructionText(),
                    modifier = Modifier.heightIn(min = 40.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "登录凭证仅保存在本机，不会上传到 MeloX 服务器。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
                actionMessage?.let { message ->
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = message,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = if (actionFailed) MaterialTheme.colorScheme.error else Color(0xFF168A4A),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                error?.let { message ->
                    Spacer(Modifier.size(14.dp))
                    Text(
                        text = message,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                if (
                    stateText == "你已取消授权" ||
                    error != null
                ) {
                    Spacer(Modifier.size(18.dp))
                    QrActionButton(
                        onClick = { refreshToken += 1 },
                        icon = MeloXSymbol.Refresh,
                        label = "重新生成二维码",
                        tint = method.accentColor(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        MeloXLegalLinks(
            modifier = Modifier.padding(vertical = 6.dp),
            tint = Color(0xFF20C573),
        )
    }
}

@Composable
private fun QQMusicLoginMethodSelector(
    selectedMethod: QQMusicQrLoginMethod,
    onSelect: (QQMusicQrLoginMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .meloXLiquidButton(
                shape = RoundedCornerShape(24.dp),
                surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.08f),
                lensRadius = 12.dp,
                refractionHeight = 14.dp,
            )
            .padding(4.dp),
    ) {
        val segmentWidth = maxWidth / 2
        val indicatorOffset by animateDpAsState(
            targetValue = if (selectedMethod == QQMusicQrLoginMethod.QQ) 0.dp else segmentWidth,
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            label = "QQMusicLoginMethodIndicatorOffset",
        )
        val indicatorColor by animateColorAsState(
            targetValue = selectedMethod.accentColor(),
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            label = "QQMusicLoginMethodIndicatorColor",
        )

        Box(
            modifier = Modifier
                .width(segmentWidth)
                .fillMaxHeight()
                .graphicsLayer { translationX = indicatorOffset.toPx() }
                .meloXLiquidButton(
                    shape = RoundedCornerShape(20.dp),
                    tint = indicatorColor,
                    surfaceColor = indicatorColor.copy(alpha = 0.10f),
                    lensRadius = 8.dp,
                    refractionHeight = 10.dp,
                )
        )

        Row(modifier = Modifier.fillMaxSize()) {
            QQMusicQrLoginMethod.entries.forEach { method ->
                val selected = method == selectedMethod
                val labelColor = Color.Black
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(method) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${method.displayName()}扫码",
                        color = labelColor,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun QrActionButton(
    onClick: () -> Unit,
    icon: MeloXSymbol,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .meloXLiquidButton(
                shape = RoundedCornerShape(18.dp),
                enabled = enabled,
                tint = tint,
                surfaceColor = tint.copy(alpha = 0.08f),
                lensRadius = 9.dp,
                refractionHeight = 12.dp,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeloXSymbolIcon(
            symbol = icon,
            modifier = Modifier.size(16.dp),
            color = tint.copy(alpha = if (enabled) 1f else 0.42f),
            iconSize = 16.sp,
            contentDescription = label,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            color = tint.copy(alpha = if (enabled) 1f else 0.42f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun QQMusicQrLoginMethod.displayName(): String = when (this) {
    QQMusicQrLoginMethod.QQ -> "QQ"
    QQMusicQrLoginMethod.WeChat -> "微信"
}

private fun QQMusicQrLoginMethod.loadingText(): String = "正在获取${displayName()}登录二维码…"

private fun QQMusicQrLoginMethod.waitingText(): String = when (this) {
    QQMusicQrLoginMethod.QQ -> "请使用手机 QQ 扫码"
    QQMusicQrLoginMethod.WeChat -> "请使用微信扫一扫扫码"
}

private fun QQMusicQrLoginMethod.instructionText(): String = when (this) {
    QQMusicQrLoginMethod.QQ -> "扫码并在 QQ 中确认即可登录。也可以保存二维码，再从 QQ 扫一扫的相册中选择。"
    QQMusicQrLoginMethod.WeChat -> "扫码并在微信中确认即可登录。也可以保存二维码，再从微信扫一扫的相册中选择。"
}

private fun QQMusicQrLoginMethod.accentColor(): Color = when (this) {
    QQMusicQrLoginMethod.QQ -> Color(0xFF1096C2)
    QQMusicQrLoginMethod.WeChat -> Color(0xFF168A4A)
}

private suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Throwable) {
    Result.failure(failure)
}

private fun qrErrorMessage(error: Throwable, fallback: String): String {
    val message = error.message?.trim().orEmpty()
    return when {
        message.equals("timeout", ignoreCase = true) -> "网络请求超时，请点击刷新二维码重试"
        message.isBlank() -> fallback
        else -> message
    }
}

private fun saveQrImageToGallery(
    context: Context,
    session: QQMusicQrLoginSession,
): String {
    val extension = if (session.imageMimeType == "image/jpeg") "jpg" else "png"
    val methodName = if (session.method == QQMusicQrLoginMethod.WeChat) "WeChat" else "QQ"
    val fileName = "MeloX-QQMusic-$methodName-${System.currentTimeMillis()}.$extension"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, session.imageMimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MeloX")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建系统图片")
        runCatching {
            resolver.openOutputStream(target)?.use { output -> output.write(session.imageBytes) }
                ?: error("无法写入系统图片")
            resolver.update(
                target,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
        }.getOrElse { failure ->
            resolver.delete(target, null, null)
            throw failure
        }
    } else {
        @Suppress("DEPRECATION")
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "MeloX",
        )
        check(directory.exists() || directory.mkdirs()) { "无法创建 Pictures/MeloX 文件夹" }
        val target = File(directory, fileName)
        FileOutputStream(target).use { output -> output.write(session.imageBytes) }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(target.absolutePath),
            arrayOf(session.imageMimeType),
            null,
        )
    }
    return "Pictures/MeloX"
}
