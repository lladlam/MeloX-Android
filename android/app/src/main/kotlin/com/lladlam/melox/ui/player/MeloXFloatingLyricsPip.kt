package com.lladlam.melox.ui.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.compose.AsyncImage
import com.google.common.util.concurrent.MoreExecutors
import com.lladlam.melox.playback.MeloXPlaybackService

/**
 * Bridge from the playback service's media-button action to the floating
 * lyrics picture-in-picture implementation in the activity layer.
 */
internal object MeloXFloatingLyricsPip {
    @Volatile
    private var handler: ((MeloXPlaybackService) -> Boolean)? = null

    fun install(requestHandler: ((MeloXPlaybackService) -> Boolean)?) {
        handler = requestHandler
    }

    /** An old Activity must not clear a replacement Activity's handler. */
    fun uninstall(requestHandler: (MeloXPlaybackService) -> Boolean) {
        synchronized(this) {
            if (handler === requestHandler) handler = null
        }
    }

    fun request(service: MeloXPlaybackService): Boolean = handler?.invoke(service) ?: false

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    fun params(context: Context, isPlaying: Boolean): PictureInPictureParams {
        fun action(requestCode: Int, serviceAction: String, iconRes: Int, title: String): RemoteAction {
            val intent = Intent(context, MeloXPlaybackService::class.java).setAction(serviceAction)
            val pendingIntent = PendingIntent.getService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteAction(Icon.createWithResource(context, iconRes), title, title, pendingIntent)
        }
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setSeamlessResizeEnabled(true)
            }
            .setActions(
                listOf(
                    action(701, MeloXPlaybackService.ACTION_PREVIOUS, android.R.drawable.ic_media_previous, "上一首"),
                    action(
                        702,
                        MeloXPlaybackService.ACTION_TOGGLE_PLAYBACK,
                        if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                        if (isPlaying) "暂停" else "播放",
                    ),
                    action(703, MeloXPlaybackService.ACTION_NEXT, android.R.drawable.ic_media_next, "下一首"),
                ),
            )
            .build()
    }
}

@Composable
internal fun MeloXFloatingLyricsPipScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val state = remember { MeloXPlaybackUiState(appContext) }
    DisposableEffect(Unit) {
        val token = SessionToken(appContext, ComponentName(appContext, MeloXPlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        var disposed = false
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { controller ->
                    if (disposed) controller.release() else state.bind(controller)
                }
            },
            MoreExecutors.directExecutor(),
        )
        onDispose {
            disposed = true
            MediaController.releaseFuture(future)
        }
    }

    val lyrics = rememberAlternativeLyrics(state, active = true)
    val index = if (lyrics.lines.isEmpty()) -1 else lyrics.index.coerceIn(lyrics.lines.indices)
    val current = lyrics.lines.getOrNull(index)
    val nextLine = if (index >= 0) lyrics.lines.getOrNull(index + 1) else null

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = state.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color.Black.copy(alpha = .84f), Color.Black.copy(alpha = .44f)),
                ),
            ),
        )
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                current?.text ?: "正在播放",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            current?.translation?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = .64f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            nextLine?.text?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = .5f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
