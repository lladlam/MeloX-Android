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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lladlam.melox.R
import com.lladlam.melox.core.music.provider.PlaybackAccountSlot
import com.lladlam.melox.core.provider.qqmusic.QQMusicApiClient
import com.lladlam.melox.core.provider.qqmusic.QQMusicQrLoginClient
import com.lladlam.melox.core.provider.qqmusic.QQMusicQrLoginMethod
import com.lladlam.melox.core.provider.qqmusic.QQMusicQrLoginSession
import com.lladlam.melox.core.provider.qqmusic.QQMusicQrLoginState
import com.lladlam.melox.core.provider.qqmusic.QQMusicSessionStore
import com.lladlam.melox.ui.glass.MeloXShapes
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXIosTopBar
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.legal.MeloXLegalLinks
import com.lladlam.melox.ui.theme.isMeloXDarkTheme
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
    val activityContext = LocalContext.current
    val context = activityContext.applicationContext
    val client = remember { QQMusicQrLoginClient() }
    val scope = rememberCoroutineScope()
    var methodName by rememberSaveable { mutableStateOf(QQMusicQrLoginMethod.QQ.name) }
    val method = QQMusicQrLoginMethod.valueOf(methodName)
    var qrSession by remember { mutableStateOf<QQMusicQrLoginSession?>(null) }
    var stateText by remember { mutableStateOf(method.loadingText(activityContext)) }
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
                actionMessage = activityContext.getString(R.string.account_qr_saved, location)
            }.onFailure { failure ->
                actionFailed = true
                actionMessage = failure.message ?: activityContext.getString(R.string.account_qr_save_failed)
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
            actionMessage = activityContext.getString(R.string.account_storage_denied)
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(method, refreshToken, resumed) {
        if (!resumed) return@LaunchedEffect
        sessionLoop@ while (true) {
            qrSession = null
            error = null
            actionMessage = null
            stateText = method.loadingText(activityContext)
            val created = runCatchingCancellable { client.createSession(method) }
                .onFailure { error = qrErrorMessage(activityContext, it, activityContext.getString(R.string.account_qr_create_failed, method.displayName(activityContext))) }
                .getOrNull() ?: return@LaunchedEffect
            qrSession = created
            stateText = method.waitingText(activityContext)

            delay(500)
            while (true) {
                val state = runCatchingCancellable { client.checkSession(created) }
                    .onFailure { error = qrErrorMessage(activityContext, it, activityContext.getString(R.string.account_qr_status_failed, method.displayName(activityContext))) }
                    .getOrNull() ?: return@LaunchedEffect
                when (state) {
                    QQMusicQrLoginState.Waiting -> stateText = method.waitingText(activityContext)
                    QQMusicQrLoginState.Scanned -> stateText = activityContext.getString(R.string.account_qr_scanned_confirm, method.displayName(activityContext))
                    QQMusicQrLoginState.Expired -> {
                        qrSession = null
                        stateText = method.loadingText(activityContext)
                        delay(250)
                        continue@sessionLoop
                    }
                    QQMusicQrLoginState.Rejected -> {
                        stateText = activityContext.getString(R.string.account_auth_cancelled)
                        return@LaunchedEffect
                    }
                    is QQMusicQrLoginState.Authorized -> {
                        stateText = activityContext.getString(R.string.account_qq_verifying)
                        val session = QQMusicSessionStore.parse(state.cookie)
                        val verified = runCatchingCancellable {
                            QQMusicApiClient(sessionProvider = { session }).accountProfile(session)
                        }.onFailure {
                            error = it.message ?: activityContext.getString(R.string.account_qq_verify_failed)
                        }.isSuccess
                        if (!verified) return@LaunchedEffect
                        QQMusicSessionStore.write(
                            context = context,
                            cookie = state.cookie,
                            playback = targetSlot == PlaybackAccountSlot.Playback,
                        )
                        stateText = activityContext.getString(R.string.account_login_success)
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
        MeloXIosTopBar(
            title = stringResource(R.string.account_login_qq),
            contentPadding = PaddingValues(horizontal = 10.dp),
            navigation = {
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.ChevronLeft,
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = onDismiss),
                    color = MaterialTheme.colorScheme.onBackground,
                    iconSize = 24.sp,
                    contentDescription = stringResource(R.string.player_back),
                )
            },
        )

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
                            contentDescription = stringResource(R.string.account_qq_qr, method.displayName(activityContext)),
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
                        label = stringResource(R.string.account_refresh_qr),
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
                        label = if (saving) stringResource(R.string.account_saving) else stringResource(R.string.account_save_gallery),
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
                    text = method.instructionText(activityContext),
                    modifier = Modifier.heightIn(min = 40.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.account_credentials_local),
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
                    stateText == activityContext.getString(R.string.account_auth_cancelled) ||
                    error != null
                ) {
                    Spacer(Modifier.size(18.dp))
                    QrActionButton(
                        onClick = { refreshToken += 1 },
                        icon = MeloXSymbol.Refresh,
                        label = stringResource(R.string.account_regenerate_qr),
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
    val activityContext = LocalContext.current
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
                val labelColor = if (isMeloXDarkTheme()) Color.White else Color.Black
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
                        text = stringResource(R.string.account_qr_scan_label, method.displayName(activityContext)),
                        color = labelColor,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** 二维码页的「刷新/保存」玻璃按钮。QQ 音乐与网易云扫码页共用同一套样式。 */
@Composable
internal fun QrActionButton(
    onClick: () -> Unit,
    icon: MeloXSymbol,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * 次级入口：同一套玻璃语言，但去掉强调色、表面更淡 ——
     * 让「逃生通道」类按钮不跟主操作（刷新 / 保存）抢视觉权重。
     */
    secondary: Boolean = false,
) {
    val controlColor = if (isMeloXDarkTheme()) Color.White else Color.Black
    val accent = if (secondary) MaterialTheme.colorScheme.onSurfaceVariant else tint
    val contentAlpha = if (!enabled) 0.42f else if (secondary) 0.72f else 1f
    Row(
        modifier = modifier
            // 胶囊：与全站控件条 / 底栏同一套几何（MeloXShapes.capsule）。
            // 高度固定 50dp 而不是 heightIn(min) —— 胶囊的圆角半径由实际高度决定，
            // 高度随内容浮动会让同一排按钮圆角不一致。
            .height(50.dp)
            .meloXLiquidButton(
                shape = MeloXShapes.capsule,
                enabled = enabled,
                tint = accent,
                surfaceColor = accent.copy(alpha = if (secondary) 0.05f else 0.08f),
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
            color = controlColor.copy(alpha = contentAlpha),
            iconSize = 16.sp,
            contentDescription = label,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            color = controlColor.copy(alpha = contentAlpha),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun QQMusicQrLoginMethod.displayName(context: Context): String = when (this) {
    QQMusicQrLoginMethod.QQ -> "QQ"
    QQMusicQrLoginMethod.WeChat -> context.getString(R.string.account_wechat)
}

private fun QQMusicQrLoginMethod.loadingText(context: Context): String =
    context.getString(R.string.account_qr_loading, displayName(context))

private fun QQMusicQrLoginMethod.waitingText(context: Context): String = when (this) {
    QQMusicQrLoginMethod.QQ -> context.getString(R.string.account_qq_scan)
    QQMusicQrLoginMethod.WeChat -> context.getString(R.string.account_wechat_scan)
}

private fun QQMusicQrLoginMethod.instructionText(context: Context): String = when (this) {
    QQMusicQrLoginMethod.QQ -> context.getString(R.string.account_qq_instruction)
    QQMusicQrLoginMethod.WeChat -> context.getString(R.string.account_wechat_instruction)
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

private fun qrErrorMessage(context: Context, error: Throwable, fallback: String): String {
    val message = error.message?.trim().orEmpty()
    return when {
        message.equals("timeout", ignoreCase = true) -> context.getString(R.string.account_timeout_refresh)
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
            ?: error(context.getString(R.string.account_image_create_failed))
        runCatching {
            resolver.openOutputStream(target)?.use { output -> output.write(session.imageBytes) }
                ?: error(context.getString(R.string.account_image_write_failed))
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
        check(directory.exists() || directory.mkdirs()) { context.getString(R.string.account_folder_create_failed) }
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
