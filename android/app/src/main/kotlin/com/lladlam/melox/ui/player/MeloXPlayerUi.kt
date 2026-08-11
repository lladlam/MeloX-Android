package com.lladlam.melox.ui.player

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.compose.AsyncImage
import com.lladlam.melox.playback.MeloXPlaybackService
import com.lladlam.melox.playback.MeloXPlaybackModePreferences
import com.lladlam.melox.playback.PlaybackCommands
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

enum class MeloXNowPlayingPage {
    Artwork,
    Lyrics,
    Queue,
}

enum class MeloXQueueOrigin { Base, Manual }

data class MeloXQueueEntry(
    val index: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val origin: MeloXQueueOrigin = MeloXQueueOrigin.Base,
)

@Stable
class MeloXPlaybackUiState internal constructor(private val appContext: Context) {
    private var controller: MediaController? = null
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private var sleepTimerRunnable: Runnable? = null

    var mediaId by mutableStateOf<String?>(null)
        private set
    var title by mutableStateOf("")
        private set
    var artist by mutableStateOf("")
        private set
    var album by mutableStateOf("")
        private set
    var artworkUrl by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var hasPrevious by mutableStateOf(false)
        private set
    var hasNext by mutableStateOf(false)
        private set
    var queue by mutableStateOf<List<MeloXQueueEntry>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(-1)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var shuffleEnabled by mutableStateOf(false)
        private set
    var autoplayEnabled by mutableStateOf(MeloXPlaybackModePreferences.autoplay(appContext))
        private set
    var autoMixEnabled by mutableStateOf(MeloXPlaybackModePreferences.autoMix(appContext))
        private set
    var volume by mutableFloatStateOf(1f)
        private set
    var sleepTimerEndRealtimeMs by mutableLongStateOf(0L)
        private set

    val hasMedia: Boolean
        get() = mediaId != null

    val repeatModeTitle: String
        get() = when (repeatMode) {
            Player.REPEAT_MODE_ALL -> "列表循环"
            Player.REPEAT_MODE_ONE -> "单曲循环"
            else -> "循环关闭"
        }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh()
        }
    }

    internal fun bind(newController: MediaController) {
        controller?.removeListener(listener)
        controller = newController
        newController.addListener(listener)
        refresh()
    }

    internal fun unbind() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        cancelSleepTimer()
    }

    internal fun refresh() {
        val player = controller ?: return
        val item = player.currentMediaItem
        val metadata = player.mediaMetadata.takeUnless { it == MediaMetadata.EMPTY }
            ?: item?.mediaMetadata
            ?: MediaMetadata.EMPTY

        mediaId = item?.mediaId
        title = metadata.title?.toString().orEmpty()
        artist = metadata.artist?.toString().orEmpty()
        album = metadata.albumTitle?.toString().orEmpty()
        artworkUrl = metadata.artworkUri?.toString()
        isPlaying = player.isPlaying
        positionMs = player.currentPosition.coerceAtLeast(0L)
        durationMs = player.duration
            .takeUnless { it == C.TIME_UNSET || it < 0L }
            ?: 0L
        hasPrevious = player.hasPreviousMediaItem()
        hasNext = player.hasNextMediaItem()
        currentIndex = player.currentMediaItemIndex
        repeatMode = player.repeatMode
        shuffleEnabled = player.shuffleModeEnabled
        volume = player.volume
        queue = buildQueue(player)
    }

    private fun buildQueue(player: Player): List<MeloXQueueEntry> =
        List(player.mediaItemCount) { index ->
            val item = player.getMediaItemAt(index)
            val metadata = item.mediaMetadata
            MeloXQueueEntry(
                index = index,
                mediaId = item.mediaId,
                title = metadata.title?.toString().orEmpty().ifBlank { "未知歌曲" },
                artist = metadata.artist?.toString().orEmpty(),
                artworkUrl = metadata.artworkUri?.toString(),
                origin = if (metadata.extras?.getString(PlaybackCommands.QUEUE_ORIGIN_KEY) == PlaybackCommands.QUEUE_ORIGIN_MANUAL) {
                    MeloXQueueOrigin.Manual
                } else {
                    MeloXQueueOrigin.Base
                },
            )
        }

    fun togglePlayPause() {
        controller?.let { player ->
            if (player.isPlaying) player.pause() else player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L)))
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun playQueueIndex(index: Int) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount) return
        player.seekToDefaultPosition(index)
        player.play()
    }

    fun toggleShuffle() {
        controller?.let { player ->
            player.shuffleModeEnabled = !player.shuffleModeEnabled
            refresh()
        }
    }

    fun cycleRepeatMode() {
        controller?.let { player ->
            player.repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            refresh()
        }
    }

    fun toggleAutoplay() {
        autoplayEnabled = !autoplayEnabled
        MeloXPlaybackModePreferences.setAutoplay(appContext, autoplayEnabled)
    }

    fun toggleAutoMix() {
        autoMixEnabled = !autoMixEnabled
        MeloXPlaybackModePreferences.setAutoMix(appContext, autoMixEnabled)
    }

    fun changeVolume(value: Float) {
        controller?.let { player ->
            player.volume = value.coerceIn(0f, 1f)
            volume = player.volume
        }
    }

    fun addCurrentToQueue() {
        val player = controller ?: return
        val item = player.currentMediaItem ?: return
        val extras = (item.mediaMetadata.extras ?: android.os.Bundle()).let { android.os.Bundle(it) }.apply {
            putString(PlaybackCommands.QUEUE_ORIGIN_KEY, PlaybackCommands.QUEUE_ORIGIN_MANUAL)
        }
        val copied = item.buildUpon()
            .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build())
            .build()
        player.addMediaItem(copied)
        refresh()
    }

    fun setSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return
        val delayMillis = minutes * 60_000L
        sleepTimerEndRealtimeMs = android.os.SystemClock.elapsedRealtime() + delayMillis
        val runnable = Runnable {
            controller?.pause()
            sleepTimerEndRealtimeMs = 0L
            sleepTimerRunnable = null
        }
        sleepTimerRunnable = runnable
        sleepTimerHandler.postDelayed(runnable, delayMillis)
    }

    fun cancelSleepTimer() {
        sleepTimerRunnable?.let(sleepTimerHandler::removeCallbacks)
        sleepTimerRunnable = null
        sleepTimerEndRealtimeMs = 0L
    }
}

@Composable
fun rememberMeloXPlaybackUiState(): MeloXPlaybackUiState {
    val context = LocalContext.current.applicationContext
    val state = remember(context) { MeloXPlaybackUiState(context) }

    DisposableEffect(context) {
        val token = SessionToken(
            context,
            ComponentName(context, MeloXPlaybackService::class.java),
        )
        val future = MediaController.Builder(context, token).buildAsync()
        val handler = Handler(Looper.getMainLooper())
        var disposed = false

        future.addListener(
            {
                if (!disposed) {
                    runCatching { future.get() }
                        .onSuccess(state::bind)
                }
            },
            { command -> handler.post(command) },
        )

        onDispose {
            disposed = true
            if (!future.isDone) future.cancel(true)
            state.unbind()
        }
    }

    LaunchedEffect(state.isPlaying, state.mediaId) {
        while (true) {
            state.refresh()
            delay(if (state.isPlaying) 500L else 1_000L)
        }
    }

    return state
}

@Composable
fun MeloXMiniPlayer(
    state: MeloXPlaybackUiState,
    onExpand: () -> Unit,
) {
    if (!state.hasMedia) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clickable(onClick = onExpand),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Artwork(
                url = state.artworkUrl,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(15.dp)),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title.ifBlank { "正在播放" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { state.togglePlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (state.isPlaying) "Ⅱ" else "▶",
                    fontSize = if (state.isPlaying) 22.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun MeloXNowPlaying(
    state: MeloXPlaybackUiState,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(MeloXNowPlayingPage.Artwork) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        MeloXNowPlayingBackground(state.artworkUrl)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 32.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(width = 60.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.52f)),
                )
            }

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "melox-now-playing-page",
            ) { selectedPage ->
                when (selectedPage) {
                    MeloXNowPlayingPage.Artwork -> MeloXArtworkPage(state)
                    MeloXNowPlayingPage.Lyrics -> MeloXLyricsPanel(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                    )
                    MeloXNowPlayingPage.Queue -> MeloXQueuePanel(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            MeloXBottomControls(
                state = state,
                page = page,
                onPageSelected = { destination ->
                    page = if (page == destination) {
                        MeloXNowPlayingPage.Artwork
                    } else {
                        destination
                    }
                },
            )
        }
    }
}

@Composable
private fun MeloXNowPlayingBackground(artworkUrl: String?) {
    Box(Modifier.fillMaxSize()) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.35f)
                    .blur(
                        radius = 46.dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded,
                    ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.12f)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.04f),
                            Color.Black.copy(alpha = 0.14f),
                            Color.Black.copy(alpha = 0.50f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun MeloXArtworkPage(state: MeloXPlaybackUiState) {
    val artworkScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.74f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 210f),
        label = "melox-artwork-scale",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.34f))

        Artwork(
            url = state.artworkUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .scale(artworkScale)
                .shadow(
                    elevation = if (state.isPlaying) 26.dp else 14.dp,
                    shape = RoundedCornerShape(12.dp),
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.34f),
                    spotColor = Color.Black.copy(alpha = 0.34f),
                )
                .clip(RoundedCornerShape(12.dp)),
        )

        Spacer(Modifier.weight(0.18f))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = state.title.ifBlank { "正在播放" },
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = state.artist,
                color = Color.White.copy(alpha = 0.64f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 19.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun MeloXBottomControls(
    state: MeloXPlaybackUiState,
    page: MeloXNowPlayingPage,
    onPageSelected: (MeloXNowPlayingPage) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(279.dp),
    ) {
        MeloXProgressControl(state)
        Spacer(Modifier.height(19.dp))
        MeloXTransportControls(state)
        Spacer(Modifier.height(31.dp))
        MeloXVolumeControl(state)
        Spacer(Modifier.height(3.dp))
        MeloXPageSelector(
            state = state,
            page = page,
            onPageSelected = onPageSelected,
        )
    }
}

@Composable
private fun MeloXProgressControl(state: MeloXPlaybackUiState) {
    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Slider(
            value = progress,
            onValueChange = { value ->
                if (state.durationMs > 0L) {
                    state.seekTo((state.durationMs * value).roundToLong())
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
            ),
            thumb = { Box(Modifier.size(1.dp)) },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(state.positionMs),
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "标准",
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "−${formatDuration((state.durationMs - state.positionMs).coerceAtLeast(0L))}",
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun MeloXTransportControls(state: MeloXPlaybackUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeloXTransportButton(
            label = "◀◀",
            fontSize = 30.sp,
            enabled = state.hasPrevious || state.repeatMode == Player.REPEAT_MODE_ALL,
            onClick = state::previous,
        )
        MeloXTransportButton(
            label = if (state.isPlaying) "Ⅱ" else "▶",
            fontSize = if (state.isPlaying) 44.sp else 40.sp,
            enabled = true,
            onClick = state::togglePlayPause,
        )
        MeloXTransportButton(
            label = "▶▶",
            fontSize = 30.sp,
            enabled = state.hasNext || state.repeatMode == Player.REPEAT_MODE_ALL,
            onClick = state::next,
        )
    }
}

@Composable
private fun MeloXTransportButton(
    label: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.28f),
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MeloXVolumeControl(state: MeloXPlaybackUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("🔈", fontSize = 12.sp, color = Color.White.copy(alpha = 0.62f))
        Slider(
            value = state.volume,
            onValueChange = state::changeVolume,
            modifier = Modifier
                .weight(1f)
                .height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.78f),
                inactiveTrackColor = Color.White.copy(alpha = 0.20f),
            ),
        )
        Text("🔊", fontSize = 14.sp, color = Color.White.copy(alpha = 0.62f))
    }
}

@Composable
private fun MeloXPageSelector(
    state: MeloXPlaybackUiState,
    page: MeloXNowPlayingPage,
    onPageSelected: (MeloXNowPlayingPage) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeloXPageButton(
            label = "词",
            selected = page == MeloXNowPlayingPage.Lyrics,
            onClick = { onPageSelected(MeloXNowPlayingPage.Lyrics) },
        )

        MeloXPageButton(
            label = "浮",
            selected = false,
            enabled = false,
            onClick = {},
        )

        Box {
            MeloXPageButton(
                label = "≡",
                selected = page == MeloXNowPlayingPage.Queue,
                onClick = { onPageSelected(MeloXNowPlayingPage.Queue) },
            )
            if (page != MeloXNowPlayingPage.Queue && (state.shuffleEnabled || state.repeatMode != Player.REPEAT_MODE_OFF)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.82f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            state.shuffleEnabled -> "↝"
                            state.repeatMode == Player.REPEAT_MODE_ONE -> "1"
                            else -> "↻"
                        },
                        color = Color.Black.copy(alpha = 0.74f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeloXPageButton(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) Color.White.copy(alpha = 0.68f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> Color.White.copy(alpha = 0.26f)
                selected -> Color.Black.copy(alpha = 0.68f)
                else -> Color.White.copy(alpha = 0.72f)
            },
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun Artwork(
    url: String?,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.background(Color.White.copy(alpha = 0.07f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = "专辑封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = "♪",
                fontSize = 36.sp,
                color = Color.White.copy(alpha = 0.24f),
            )
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val minutes = seconds / 60L
    val remainder = seconds % 60L
    return "%d:%02d".format(minutes, remainder)
}
