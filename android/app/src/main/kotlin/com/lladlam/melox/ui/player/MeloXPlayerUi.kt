package com.lladlam.melox.ui.player

import com.lladlam.melox.dlna.DlnaSession
import com.lladlam.melox.miplay.MiPlaySession
import com.lladlam.melox.usb.MeloXUsbAudioSession

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.lladlam.melox.R
import com.lladlam.melox.playback.MeloXPlaybackService
import com.lladlam.melox.core.recommendation.LocalRecommendationEngine
import com.lladlam.melox.core.recommendation.LocalRecommendationStore
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.playback.MeloXPlaybackModePreferences
import com.lladlam.melox.playback.MeloXAutoMixTransitionRuntime
import com.lladlam.melox.playback.MeloXPlaybackModeRuntime
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.playback.PlaybackTrackIdentity
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import com.lladlam.melox.ui.settings.MeloXVolumeControlMode
import com.lladlam.melox.ui.animation.meloXContentEnter
import com.lladlam.melox.ui.animation.meloXContentExit
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXSymbolVariant
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

enum class MeloXNowPlayingPage {
    Artwork,
    Lyrics,
    Queue,
}

/**
 * 天际歌词此刻**能不能呈现** —— 也就是「有没有一枚可用的封面源/目标」。
 *
 * `np-skyline-artwork` 的**源端**不是场景自绘的副本，而是 SharedHost 那枚**持久封面本体**
 * （见 `MeloXIOSNowPlayingSharedHost` 的 `skylineSourceModifier`；2026-10-06「双封面」修复
 * 把场景层等位副本删掉了）。源注册与否直接决定了"哪些页面能进天际"：
 *
 * · **歌词页**：任何布局都行（手机横屏的入口就挂在右列曲名栏上）。持久封面停在左列、
 *   天际开启期间**位置不动** ⇒ 封面从大封面直接飞进天际那枚 132dp。
 * · **平板双栏的封面态**（`Artwork`）：左列整块**居中**、右列空着。这枚持久封面同样
 *   **一动不动** ⇒ 从屏幕正中直接飞入天际，比"先把左列收回分栏、再飞"少一段会跟 morph
 *   抢起点的并发位移动画（2026-10-07 把入口挂到左列「更多」时定稿）。
 * · **队列页**：`Queue` 没有对应的封面源 ⇒ 永不允许（入口会先把页面切到歌词页再开天际）。
 *
 * ⚠ 手机 / 竖屏（`twoPane == false`）**不能进 `Artwork` 分支**：那条路径下本判据与
 *   `page == Lyrics` 等价，放开只会得到一个"没有源"的天际 ⇒ 封面凭空出现。
 *   ⇒ 本函数是**唯一**判据，四处（host 源注册 / host 常亮 / 场景内容淡出 / 天际层可见性）
 *   必须全部走它，任何一处写死 `page == Lyrics` 都会退回"没有源"的旧行为。
 */
internal fun meloXSkylineCanRender(page: MeloXNowPlayingPage, twoPane: Boolean): Boolean = when (page) {
    MeloXNowPlayingPage.Lyrics -> true
    MeloXNowPlayingPage.Artwork -> twoPane
    MeloXNowPlayingPage.Queue -> false
}

enum class MeloXQueueOrigin { Base, Manual }

data class MeloXQueueEntry(
    val index: Int,
    val entryId: String,
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long = 0L,
    val origin: MeloXQueueOrigin = MeloXQueueOrigin.Base,
)

@Stable
class MeloXPlaybackUiState internal constructor(private val appContext: Context) {
    var showOutputPicker by mutableStateOf(false)
    private var controller: MediaController? = null
    private val downloadStore by lazy(LazyThreadSafetyMode.NONE) { MeloXDownloadStore.get(appContext) }
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private var pendingMediaClearRunnable: Runnable? = null
    private var pendingQueueRebuildRunnable: Runnable? = null
    private var recordedMediaId: String? = null
    private var recordedCompletion = false
    private val artworkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
    var shuffleEnabled by mutableStateOf(MeloXPlaybackModePreferences.shuffle(appContext))
        private set
    var autoplayEnabled by mutableStateOf(MeloXPlaybackModePreferences.autoplay(appContext))
        private set
    var autoMixEnabled by mutableStateOf(MeloXPlaybackModePreferences.autoMix(appContext))
        private set
    var smartQueueEnabled by mutableStateOf(MeloXPlaybackModePreferences.smartQueue(appContext))
        private set
    var isInTransition by mutableStateOf(false)
        internal set
    var transitionProgress by mutableFloatStateOf(0f)
        internal set
    var transitionHandedOff by mutableStateOf(false)
        internal set
    var outgoingArtworkUrl by mutableStateOf<String?>(null)
        internal set
    var incomingPositionMs by mutableLongStateOf(0L)
        internal set
    var incomingDurationMs by mutableLongStateOf(0L)
        internal set
    var incomingTitle by mutableStateOf("")
        internal set
    var incomingArtist by mutableStateOf("")
        internal set
    var incomingArtworkUrl by mutableStateOf<String?>(null)
        internal set
    var volume by mutableFloatStateOf(1f)
        private set
    var sleepTimerEndRealtimeMs by mutableLongStateOf(0L)
        private set

    // ★ 系统音量实时同步（2026-10-06）：volumeControlMode == System 时，用户用硬件音量键 /
    //   系统音量条调 STREAM_MUSIC，本 receiver 捕获 VOLUME_CHANGED_ACTION 并立即把最新
    //   系统音量写回 `volume`，播放页滑块随即实时跟随（否则只有 refresh() 才更新，会滞后）。
    //   ⚠ 用字符串字面量而非 AudioManager.VOLUME_CHANGED_ACTION —— 后者是 @SystemApi 隐藏常量，
    //     直接引用在公开 SDK 下编不过。
    //   ⚠ VOLUME_CHANGED_ACTION 携带 EXTRA_VOLUME_STREAM，需 == STREAM_MUSIC 才同步，
    //     避免其它音频流（闹钟/通话）变化误触发。
    private var systemVolumeReceiver: BroadcastReceiver? = null

    val hasMedia: Boolean
        get() = mediaId != null

    val repeatModeTitle: String
        get() = when (repeatMode) {
            Player.REPEAT_MODE_ALL -> appContext.getString(R.string.player_repeat_all)
            Player.REPEAT_MODE_ONE -> appContext.getString(R.string.player_repeat_one)
            else -> appContext.getString(R.string.player_repeat_off)
        }

    /**
     * UI 口径的「在播」——**播放意图**，而不是 Media3 的「正在出声」。
     *
     * `Player.isPlaying` 只在 `STATE_READY && playWhenReady && 未抑制` 时为 true。
     * 切歌（`seekToNextMediaItem()`）时 ExoPlayer 会短暂落到 `STATE_BUFFERING`，
     * `isPlaying` 于是掉一帧 false（网络曲源能掉几百毫秒），带来一串可见抖动：
     *   · 迷你播放栏 / 全屏播放页的**暂停键闪成播放键**再闪回来（用户说的「抽搐」）；
     *   · dancing bars 冻住；动态封面 `1f → 0.74f` 缩一下再弹回来；
     *   · 歌词自动滚动被判定为「已暂停」而停一拍。
     *
     * Media3 官方也建议播放/暂停键用 `playWhenReady` 驱动：用户按了播放、没播完、
     * 也没被音频焦点抑制，键位就该一直显示「暂停」。
     */
    private fun Player.isPlaybackActiveForUi(): Boolean =
        playWhenReady &&
            playbackState != Player.STATE_ENDED &&
            playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val queueDirty = events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)
            // ⚠⚠⚠ 2026-10-16 修「队列页点模式键后立刻退出会卡住」：随机播放的一次开关会
            //   经 `reorderFutureInPlace` 发出**每首一首一条** moveMediaItem ⇒ 每条都回抛
            //   EVENT_TIMELINE_CHANGED ⇒ 原实现每条都全量 `buildQueue` + 队列 LazyColumn
            //   全量重组 = O(N²) 主线程风暴；风暴窗口内点「接下来播放」退出列表页，
            //   切页动画/点击全被压死（用户观感 = 卡住）。
            //   ⇒ 当前曲目元数据照旧**立刻**刷新（rebuildQueue=false，便宜；尺寸变化时
            //     它内部仍会即时重建一次）；队列全量重建合并为 120ms 去抖单次 ——
            //     一轮 shuffle 重排只付一次 buildQueue。
            refresh(rebuildQueue = false)
            if (queueDirty) scheduleQueueRebuild(player)
        }
    }

    internal fun bind(newController: MediaController) {
        controller?.removeListener(listener)
        controller = newController
        newController.addListener(listener)
        // ⚠ 冷启动队列恢复已收归 Service（`MeloXPlaybackQueueStore` + `restorePersistedQueue`），
        // 这里不再自行 `setMediaItems`——否则会和 Service 的恢复竞争，把整条队列
        // 覆盖成 1 首 / 或卡在 `mediaItemCount>0` 短路掉全量恢复。
        refresh(rebuildQueue = true)
    }

    internal fun unbind(releaseController: Boolean = true) {
        cancelPendingMediaClear()
        cancelPendingQueueRebuild()
        val previous = controller
        controller = null
        try {
            previous?.removeListener(listener)
        } finally {
            if (releaseController) previous?.release()
        }
    }

    internal fun refresh(rebuildQueue: Boolean = false) {
        val player = controller ?: return
        val item = player.currentMediaItem
        if (item == null) {
            // MediaSession.setPlayer() briefly exposes an empty controller while
            // AutoMix promotes the already-playing incoming deck. Clearing the UI
            // in that gap collapses Now Playing and strands its back gesture.
            if (mediaId != null) scheduleDeferredMediaClear(player) else clearMediaState(player)
            return
        }
        cancelPendingMediaClear()
        MeloXPlaybackModeRuntime.heartModeActive = item.mediaMetadata.extras
            ?.getBoolean(PlaybackCommands.HEART_MODE_KEY, false) == true
        // ⚠⚠⚠ 2026-10-06 修「迷你栏横滑切歌后抽搐闪回**上一首**」——**真根因就在这里**：
        //
        //   原来以 **`player.mediaMetadata`**（= MediaController 最近一次收到的
        //   `PlaybackInfo.mediaMetadata`）**优先**，而 `mediaId` / `currentIndex` 取自
        //   `player.currentMediaItem`（**当拍就是新歌**）。切歌那一拍两条信息**不同源**：
        //     · `EVENT_MEDIA_ITEM_TRANSITION` 先到 ⇒ `currentMediaItem` 已切到新歌；
        //     · `EVENT_MEDIA_METADATA_CHANGED` 后到 ⇒ `mediaMetadata` **还是上一首的**。
        //   ⇒ 于是有若干帧是「`mediaId` = 新歌，`title`/`artist` = **上一首**」。
        //   迷你栏主行显示的正是 `state.title` ⇒ 用户看到「抽搐一下、闪回上一首歌」。
        //
        //   ⚠ 为什么诊断日志看着是"干净"的：埋点只记了**索引**（`mini.sidx/cmt/swc` 全部自洽，
        //     `mini.shown` 恒 -1）——错配发生在**文字**上，索引根本无法暴露它。
        //   ⚠ 为什么只有"落定后"才闪：滑动中主行显示的 `state` 本来就是**旧歌**（正确），
        //     预览行取自 `buildQueue()` 的 `item.mediaMetadata`（**本来就对**）⇒ 差异只在
        //     交接那一拍之后的主行。
        //
        //   ⇒ 修法：**以当前 media item 自己的 metadata 为准**（同一拍内自洽），
        //     只在它为空时才回退到 player 级。稳态下两者本就相同 ⇒ 行为不变。
        val metadata = item.mediaMetadata.takeUnless { it == MediaMetadata.EMPTY }
            ?: player.mediaMetadata
        val itemExtras = item.mediaMetadata.extras
        val extras = metadata.extras ?: itemExtras
        val providerSource = PlaybackTrackIdentity.fromMediaItem(item)?.sourceId?.builtIn

        mediaId = item?.mediaId
        val originalTitle = extras?.getString("melox.system.original_title")
        val originalArtist = extras?.getString("melox.system.original_artist")
        val rawTitle = originalTitle ?: metadata.title?.toString().orEmpty()
            .ifBlank { extras?.getString(PlaybackTrackIdentity.TitleExtra).orEmpty() }
        val rawArtist = originalArtist ?: metadata.artist?.toString().orEmpty()
            .ifBlank { extras?.getString(PlaybackTrackIdentity.ArtistExtra).orEmpty() }
        val recovered = if (providerSource == MusicSource.Kugou &&
            (rawArtist.isBlank() || rawArtist == "未知歌手") &&
            " - " in rawTitle
        ) {
            rawTitle.split(" - ", limit = 2).let { it[0] to it.getOrElse(1) { rawTitle } }
        } else rawArtist to rawTitle
        artist = recovered.first
        title = recovered.second
        album = metadata.albumTitle?.toString().orEmpty()
            .ifBlank { extras?.getString(PlaybackTrackIdentity.AlbumExtra).orEmpty() }
        val currentSongId = item?.mediaId?.toLongOrNull()
        artworkUrl = currentSongId?.let(downloadStore::localArtworkUri)?.toString()
            ?: metadata.artworkUri?.toString()
            ?: extras?.getString(PlaybackTrackIdentity.ArtworkExtra)?.takeIf(String::isNotBlank)
        isPlaying = player.isPlaybackActiveForUi()
        positionMs = player.currentPosition.coerceAtLeast(0L)
        durationMs = player.duration
            .takeUnless { it == C.TIME_UNSET || it < 0L }
            ?: 0L
        // ⚠ 本地推荐记录**不在**这里落库了（2026-09-28 修「无音源的歌被记进历史」）：
        //   `refresh()` 在 `onMediaItemTransition` 的同批事件里就被调用，此时只说明
        //   「切过去了」，数据源还没打开 —— 一首无音源的 VIP 歌同样会走到这里，于是
        //   playCount +1、偏好向量 +0.25，可它一个字节都没出声。
        //   ⇒ 挪到 `refreshProgress()`，并要求 `isPlaying`（真的在播）才记。
        if (recordedMediaId != item.mediaId) {
            recordedCompletion = false
        }
        hasPrevious = player.hasPreviousMediaItem()
        hasNext = player.hasNextMediaItem()
        currentIndex = player.currentMediaItemIndex
        repeatMode = player.repeatMode
        shuffleEnabled = MeloXPlaybackModePreferences.shuffle(appContext)
        autoplayEnabled = MeloXPlaybackModePreferences.autoplay(appContext)
        autoMixEnabled = MeloXPlaybackModePreferences.autoMix(appContext)
        smartQueueEnabled = MeloXPlaybackModePreferences.smartQueue(appContext)
        volume = if (MeloXSettingsRuntime.volumeControlMode == MeloXVolumeControlMode.Player) {
            player.volume
        } else {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()
        }
        sleepTimerEndRealtimeMs = com.lladlam.melox.ui.settings.MeloXSettingsPreferences.long(
            appContext,
            SLEEP_TIMER_END_KEY,
            0L,
        ).takeIf { it > System.currentTimeMillis() } ?: 0L
        if (rebuildQueue || queue.size != player.mediaItemCount) {
            queue = buildQueue(player)
        }
        applyDlnaState()
    }

    internal fun refreshCurrentLocalMetadata() {
        val active = controller ?: return
        val item = active.currentMediaItem ?: return
        val identity = PlaybackTrackIdentity.fromMediaItem(item) ?: return
        if (identity.sourceId.builtIn != MusicSource.Local) return
        val record = com.lladlam.melox.core.provider.local.LocalMusicRepository(appContext)
            .track(identity.value) ?: return
        val title = record.recognizedTitle ?: record.title.ifBlank { record.displayName }
        val artist = record.recognizedArtist ?: record.artist.ifBlank { appContext.getString(R.string.player_unknown_artist) }
        val album = record.recognizedAlbum ?: record.album
        val artwork = record.recognizedArtworkUrl ?: record.artworkUri
        val metadata = item.mediaMetadata.buildUpon()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .apply { artwork?.let { setArtworkUri(Uri.parse(it)) } }
            .build()
        active.replaceMediaItem(
            active.currentMediaItemIndex,
            item.buildUpon().setMediaMetadata(metadata).build(),
        )
        refresh(rebuildQueue = true)
    }

    /**
     * The playback clock is the only state that needs polling. Queue and metadata
     * updates arrive through Player.Events; rebuilding them here made every clock
     * tick walk both the Media3 timeline and the Compose download list.
     */
    internal fun refreshProgress() {
        val player = controller ?: return
        if (player.currentMediaItem == null) return
        isPlaying = player.isPlaybackActiveForUi()
        positionMs = player.currentPosition.coerceAtLeast(0L)
        durationMs = player.duration
            .takeUnless { it == C.TIME_UNSET || it < 0L }
            ?: 0L
        applyDlnaState()
        // 「播过这首」的落库点：**只在真的在播时**才记（无音源的歌 `isPlaying`
        // 恒 false，永远进不来 ⇒ 不会被算进本地推荐记录）。
        // recordPlayback() 内部会 readFeatures()（最多 2000 条 JSON 反序列化）→ 改 →
        // writeFeatures()（重新序列化 + prefs apply 全量重写 local_recommendation.xml），
        // 整段跑在主线程会卡 100~250ms ⇒ 一律挪到 IO 线程。
        val currentMediaId = player.currentMediaItem?.mediaId
        if (isPlaying && recordedMediaId != currentMediaId) {
            recordedMediaId = currentMediaId
            val recordTitle = title
            val recordArtist = artist
            if (currentMediaId != null) {
                artworkScope.launch {
                    runCatching {
                        LocalRecommendationStore.recordPlayback(
                            appContext,
                            currentMediaId,
                            recordTitle,
                            recordArtist,
                        )
                        if (LocalRecommendationStore.isAlgorithmEnabled(appContext) &&
                            LocalRecommendationStore.hasPersonalizationConsent(appContext)
                        ) {
                            LocalRecommendationEngine.start(appContext)
                        }
                    }
                }
            }
        }
        if (!recordedCompletion && durationMs > 0L && positionMs >= (durationMs * .85f).toLong()) {
            recordedCompletion = true
            mediaId?.let { key ->
                val recordTitle = title
                val recordArtist = artist
                // 进度 tick 也在主线程 → 存储写入挪到 IO。
                artworkScope.launch {
                    runCatching {
                        LocalRecommendationStore.recordPlayback(appContext, key, recordTitle, recordArtist, completed = true)
                        if (LocalRecommendationStore.isAlgorithmEnabled(appContext) &&
                            LocalRecommendationStore.hasPersonalizationConsent(appContext)
                        ) {
                            LocalRecommendationEngine.start(appContext)
                        }
                    }
                }
            }
        }
    }

    private fun scheduleDeferredMediaClear(player: Player) {
        if (pendingMediaClearRunnable != null) return
        val runnable = Runnable {
            pendingMediaClearRunnable = null
            val active = controller
            if (active === player && active.currentMediaItem == null) {
                clearMediaState(active)
            } else {
                refresh(rebuildQueue = true)
            }
        }
        pendingMediaClearRunnable = runnable
        sleepTimerHandler.postDelayed(runnable, MEDIA_CLEAR_GRACE_MS)
    }

    private fun cancelPendingMediaClear() {
        pendingMediaClearRunnable?.let(sleepTimerHandler::removeCallbacks)
        pendingMediaClearRunnable = null
    }

    /** 时间线/元数据驱动的队列全量重建去抖：风暴窗口内的 N 条事件只付 1 次 buildQueue。 */
    private fun scheduleQueueRebuild(player: Player) {
        if (pendingQueueRebuildRunnable != null) return
        val runnable = Runnable {
            pendingQueueRebuildRunnable = null
            if (controller === player) refresh(rebuildQueue = true)
        }
        pendingQueueRebuildRunnable = runnable
        sleepTimerHandler.postDelayed(runnable, QUEUE_REBUILD_COALESCE_MS)
    }

    private fun cancelPendingQueueRebuild() {
        pendingQueueRebuildRunnable?.let(sleepTimerHandler::removeCallbacks)
        pendingQueueRebuildRunnable = null
    }

    private fun clearMediaState(player: Player) {
        cancelPendingMediaClear()
        MeloXPlaybackModeRuntime.heartModeActive = false
        mediaId = null
        title = ""
        artist = ""
        album = ""
        artworkUrl = null
        isPlaying = false
        positionMs = 0L
        durationMs = 0L
        hasPrevious = false
        hasNext = false
        currentIndex = player.currentMediaItemIndex
        queue = buildQueue(player)
    }

    private fun buildQueue(player: Player): List<MeloXQueueEntry> {
        // The same track can legitimately sit in the queue more than once (queued
        // twice by hand, or re-added by the smart queue), which repeats the stored
        // entry id. LazyColumn keys must be unique or Compose throws at draw time,
        // so a repeated id is suffixed with its position.
        val usedEntryIds = HashSet<String>()
        return List(player.mediaItemCount) { index ->
            val item = player.getMediaItemAt(index)
            val metadata = item.mediaMetadata
            val rawEntryId = metadata.extras?.getString(PlaybackCommands.QUEUE_ENTRY_ID_KEY)
                ?: "${item.mediaId}@$index"
            val entryId = if (usedEntryIds.add(rawEntryId)) rawEntryId else "$rawEntryId@$index"
            MeloXQueueEntry(
                index = index,
                entryId = entryId,
                mediaId = item.mediaId,
                title = metadata.extras?.getString("melox.system.original_title")
                    ?: metadata.title?.toString().orEmpty().ifBlank { appContext.getString(R.string.player_unknown_song) },
                artist = metadata.extras?.getString("melox.system.original_artist")
                    ?: metadata.artist?.toString().orEmpty(),
                artworkUrl = item.mediaId.toLongOrNull()?.let(downloadStore::localArtworkUri)?.toString()
                    ?: metadata.artworkUri?.toString(),
                durationMs = com.lladlam.melox.playback.normalizedQueueDurationMs(
                    metadata.extras?.getLong(PlaybackTrackIdentity.DurationMsExtra, 0L),
                ),
                origin = if (metadata.extras?.getString(PlaybackCommands.QUEUE_ORIGIN_KEY) == PlaybackCommands.QUEUE_ORIGIN_MANUAL) {
                    MeloXQueueOrigin.Manual
                } else {
                    MeloXQueueOrigin.Base
                },
            )
        }
    }

    private fun applyDlnaState() {
        if (MeloXUsbAudioSession.active) volume = MeloXUsbAudioSession.volume
        if (MiPlaySession.active) MiPlaySession.volume?.let { volume = it }
        if (!DlnaSession.active) return
        isPlaying = DlnaSession.playing
        positionMs = DlnaSession.displayedPosition()
        durationMs = DlnaSession.durationMs
        DlnaSession.volume?.let { volume = it }
    }

    fun togglePlayPause() {
        if (DlnaSession.active) { DlnaSession.toggle(); return }
        controller?.let { player ->
            // 用 UI 口径（播放意图）判断：缓冲期间 `player.isPlaying` 是 false，
            // 若照它判断，此时点暂停会变成又一次 `play()`（点了没反应）。
            if (player.isPlaybackActiveForUi()) player.pause() else player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        if (DlnaSession.active) { DlnaSession.seek(positionMs); return }
        controller?.seekTo(positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L)))
    }

    fun previous() {
        if (DlnaSession.active) { DlnaSession.previous(); return }
        controller?.let { player ->
            if (MeloXSettingsRuntime.previousRestartsAfterFiveSeconds && player.currentPosition >= 5_000L) {
                player.seekTo(0L)
            } else {
                player.seekToPreviousMediaItem()
            }
        }
    }

    fun previousFromMiniPlayer() {
        controller?.let { player ->
            if (player.mediaItemCount > 1 && player.currentMediaItemIndex <= 0) {
                playQueueIndex(player.mediaItemCount - 1)
            } else {
                previous()
            }
        }
    }

    /**
     * 迷你栏**横滑**专用只读预判：朝 [direction] 滑动是否**真的会切换到另一首曲目**。
     *
     * ⚠ 横滑的视觉（当前曲滑出 → 目标曲预览滑入 → 落位）必须与「真的会换曲」对齐。
     *   若 UI 一律假设"会换"，而实际命令只是**重播当前曲**，就会出现
     *   「滑出「上一首」预览 → 曲目根本没变 → 1.5s 超时后再滑回来」的抽搐闪回
     *   （用户报障 2026-10-06「横滑切歌后抽搐闪回上一首」）。
     *
     * 判据与 [`nextFromMiniPlayer`] / [`previousFromMiniPlayer`] 逐条对齐：
     * · 单曲（`mediaItemCount <= 1`）：两条路径都无目标 ⇒ 恒 `false`。
     * · `direction < 0`（下一首）：末位回绕到 0 ⇒ 恒会换。
     * · `direction > 0`（上一首）：首位回绕到末位 ⇒ 会换；其余走 [`previous`]，
     *   而它命中「已播 ≥5s 且开启重播设置」时**只 `seekTo(0)`、曲目不变** ⇒ `false`。
     *
     * **只读，无任何副作用**（不改队列、不 seek、不刷新）。
     */
    fun miniSwipeChangesTrack(direction: Int): Boolean {
        val player = controller ?: return false
        if (player.mediaItemCount <= 1) return false
        return when {
            direction < 0 -> true
            direction > 0 -> if (player.currentMediaItemIndex <= 0) {
                true // `previousFromMiniPlayer` 回绕到最后一首
            } else {
                // ⚠ 与 `previous()` 的重播分支逐字同义，否则 UI 会以为"会换曲"。
                !(MeloXSettingsRuntime.previousRestartsAfterFiveSeconds && player.currentPosition >= 5_000L)
            }
            else -> false
        }
    }

    fun next() {
        if (DlnaSession.active) { DlnaSession.next(); return }
        controller?.let { player ->
            // ⚠ 多一个 `recordedMediaId == mediaId` 判据（2026-09-28 修）：
            //   `recordedMediaId` 只在「真的在播」时才被置上。切到一首无音源的歌后
            //   它根本没出声 ⇒ 这里不会给它记 skipCount（-1.5 的负向权重），
            //   也不会把 playCount 又 +1 —— 没播过的歌不该产生任何推荐信号。
            if (!recordedCompletion && recordedMediaId == mediaId && player.currentPosition < 30_000L) {
                mediaId?.let { id ->
                    val recordTitle = title
                    val recordArtist = artist
                    // 与 refresh 同理：存储写入别压主线程（切歌要立刻响应）。
                    artworkScope.launch {
                        runCatching {
                            LocalRecommendationStore.recordPlayback(appContext, id, recordTitle, recordArtist, skipped = true)
                        }
                    }
                }
            }
            PlaybackCommands.prioritizeManualQueue(player)
            player.seekToNextMediaItem()
        }
    }

    fun nextFromMiniPlayer() {
        controller?.let { player ->
            if (player.mediaItemCount > 1 && player.currentMediaItemIndex >= player.mediaItemCount - 1) {
                playQueueIndex(0)
            } else {
                next()
            }
        }
    }

    fun playQueueIndex(index: Int) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount) return
        player.seekToDefaultPosition(index)
        player.play()
    }

    fun toggleShuffle() {
        controller?.let { player ->
            val next = !shuffleEnabled
            PlaybackCommands.setExplicitShuffle(appContext, player, next)
            shuffleEnabled = next
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

    fun toggleSmartQueue() {
        smartQueueEnabled = !smartQueueEnabled
        MeloXPlaybackModePreferences.setSmartQueue(appContext, smartQueueEnabled)
    }

    // ★ 拖动期主线程零 binder（2026-10-06，用户报「手动滑动偶尔闪烁」）：
    //   maxVolume 与当前档位都**缓存成普通字段**，拖动每像素不再打 AudioManager。
    //   原实现每像素做 getStreamMaxVolume + getStreamVolume + setStreamVolume 三次同步
    //   binder IPC ⇒ 阻塞主线程、丢帧 ⇒ 音量条「偶尔闪烁」。
    private var cachedMaxVolume: Int = 0
    private var cachedStep: Int = -1

    /** 取 STREAM_MUSIC 最大档位（带缓存；缓存无效时才真正问一次 AudioManager）。 */
    private fun maxVolumeCached(audioManager: AudioManager): Int {
        if (cachedMaxVolume <= 0) {
            cachedMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        }
        return cachedMaxVolume
    }

    fun changeVolume(value: Float) {
        if (MeloXUsbAudioSession.active) { MeloXUsbAudioSession.changeVolume(value); return }
        if (MiPlaySession.active) { MiPlaySession.changeVolume(value); return }
        if (DlnaSession.active) { DlnaSession.changeVolume(value); return }
        if (MeloXSettingsRuntime.volumeControlMode == MeloXVolumeControlMode.Player) {
            controller?.volume = value.coerceIn(0f, 1f)
            volume = controller?.volume ?: value.coerceIn(0f, 1f)
            return
        }
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = maxVolumeCached(audioManager)
        val target = (value.coerceIn(0f, 1f) * maxVolume).toInt().coerceIn(0, maxVolume)
        // ★ 只在**跨格**时才真正下发 setStreamVolume（2026-10-06）：
        //   拖动时 Compose 每像素都会回调 onValueChange，若每像素都 setStreamVolume，
        //   会高频打系统音量服务 → 系统音量 HUD 反复弹出 + 回读抖动 ⇒ 滑块「抽搐」。
        //   系统音量本身是**离散档位**，同格内重复下发无意义。
        // ⚠ 判据用**本地缓存的档位**（cachedStep），不再 binder 回读 getStreamVolume。
        if (cachedStep != target) {
            cachedStep = target
            // setStreamVolume 也是同步 binder，丢到 IO 线程，绝不阻塞拖动手势。
            artworkScope.launch {
                runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
            }
        }
        // 同样只在跨格时写 volume（同格内不触发 UI 重组 / 动画重启）。
        val fresh = target.toFloat() / maxVolume.toFloat()
        if (fresh != volume) volume = fresh
    }

    // ★ 拖拽抑制（2026-10-06）：用户正在拖本滑块时，系统音量回灌（广播 / 轮询）**不写** [volume]。
    //   否则 setStreamVolume 的整数化回读会与手指位置打架（一个连续、一个跳格）⇒ 滑块抽搐。
    private var volumeDragging = false

    internal fun beginVolumeDrag() {
        volumeDragging = true
    }

    internal fun endVolumeDrag() {
        volumeDragging = false
    }

    /**
     * 立即把当前系统媒体音量同步进 [volume]（用于 receiver 回调、轮询与注册瞬间首读）。
     * 只在 System 模式生效；Player 模式（独立音量）下系统音量变化与本滑块无关，跳过。
     * 拖拽中直接跳过（见 [volumeDragging]）。
     */
    internal fun syncSystemVolume() {
        syncSystemVolumeNow()
    }

    /**
     * 立即把当前系统媒体音量同步进 [volume]，并**返回档位是否真的变了**。
     *
     * 返回值专供实体音量键那条通道（`MeloXVolumeKeyBridge`）：`false` 就代表"这一按没能再
     * 抬高/压低" ⇒ 已经顶到最大/最小了（**越界按**），播放页据此给出"过冲拉长"的反馈。
     * 轮询与广播这两条路忽略返回值（它们只关心同步本身）。
     *
     * ⚠ 注意 `false` 的三种含义要分清：非 System 模式 / 正在拖拽 / 真的顶到头了。
     *   调用方（音量条）必须先自行排除前两种，才能把 `false` 当成越界。
     */
    internal fun syncSystemVolumeNow(): Boolean {
        if (MiPlaySession.active || DlnaSession.active || MeloXUsbAudioSession.active) return false
        if (MeloXSettingsRuntime.volumeControlMode != MeloXVolumeControlMode.System) return false
        if (volumeDragging) return false
        val maxVolume: Int
        val current: Int
        try {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            maxVolume = maxVolumeCached(audioManager)
            current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        } catch (error: RuntimeException) {
            // This is also called from startup and the polling coroutine. A
            // temporarily unavailable OEM audio service must not kill that scope.
            return false
        }
        // 外部（音量键）改了音量时同步本地缓存，避免下次拖动误判成"跨格"。
        cachedStep = current
        val fresh = current.toFloat() / maxVolume.toFloat()
        // ⚠ 值没变就**不写**（2026-10-06 用户连续报「不丝滑」）：
        //   150ms 轮询每拍都会读回同一个值；若无条件写 `volume`，UI 侧跟随动画的
        //   target 每拍被"重新赋值" ⇒ 动画被反复重启 ⇒ 顿挫。音量没变就跳过写入。
        if (fresh == volume) return false
        volume = fresh
        return true
    }

    /** 注册系统音量变化监听（仅 System 模式、且尚未注册时）。 */
    internal fun registerSystemVolumeSync() {
        if (MeloXSettingsRuntime.volumeControlMode != MeloXVolumeControlMode.System) return
        if (systemVolumeReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // ⚠ EXTRA_VOLUME_STREAM 判据不可靠：部分 ROM（含 vivo）发这个广播时**不带**该 extra
                //   （实测硬件音量键调节收不到回调），所以这里不作为过滤条件——
                //   改为「只要是音量变化广播就同步」，非音乐流的音量写入 STREAM_MUSIC 后
                //   回读仍是音乐流音量，值本身自带正确性，不会串到别的流。
                syncSystemVolume()
            }
        }
        // Volume broadcasts normally have no data URI. Requiring content://media
        // here prevents those broadcasts from matching the filter.
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        // VOLUME_CHANGED_ACTION 是系统有序广播，动态注册即可收到（无需 manifest）。
        // RECEIVER_NOT_EXPORTED：本 receiver 不对外暴露，仅接收本应用注册的意图。
        val registered = runCatching {
            ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.onFailure { android.util.Log.w("MeloXPlayerUi", "System volume receiver unavailable; using polling", it) }
            .isSuccess
        if (registered) systemVolumeReceiver = receiver
        // 注册瞬间首读一次，确保进入播放页时滑块已对齐当前系统音量。
        syncSystemVolume()
    }

    /** 反注册系统音量监听。 */
    internal fun unregisterSystemVolumeSync() {
        systemVolumeReceiver?.let { receiver ->
            runCatching { appContext.unregisterReceiver(receiver) }
                .onFailure { android.util.Log.w("MeloXPlayerUi", "Unable to unregister system volume receiver", it) }
        }
        systemVolumeReceiver = null
    }

    fun moveQueueItem(from: Int, to: Int) {
        val player = controller ?: return
        if (from == player.currentMediaItemIndex || from !in 0 until player.mediaItemCount) return
        val safeTarget = to.coerceIn(player.currentMediaItemIndex + 1, player.mediaItemCount - 1)
        if (from != safeTarget) player.moveMediaItem(from, safeTarget)
        refresh(rebuildQueue = true)
    }

    fun addCurrentToQueue() {
    val player = controller ?: return
    val item = player.currentMediaItem ?: return
    val extras = (item.mediaMetadata.extras ?: android.os.Bundle()).let { android.os.Bundle(it) }.apply {
        putString(PlaybackCommands.QUEUE_ORIGIN_KEY, PlaybackCommands.QUEUE_ORIGIN_MANUAL)
        putString(PlaybackCommands.QUEUE_ENTRY_ID_KEY, java.util.UUID.randomUUID().toString())
    }
    val copied = item.buildUpon()
        .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build())
        .build()
    PlaybackCommands.prioritizeManualQueue(player)
    val current = player.currentMediaItemIndex
    val manualCount = ((current + 1) until player.mediaItemCount).count { index ->
        player.getMediaItemAt(index).mediaMetadata.extras
            ?.getString(PlaybackCommands.QUEUE_ORIGIN_KEY) == PlaybackCommands.QUEUE_ORIGIN_MANUAL
    }
    val insertion = (current + 1 + manualCount).coerceIn(0, player.mediaItemCount)
    player.addMediaItem(insertion, copied)
    refresh(rebuildQueue = true)
}

// 冷启动队列恢复已收归 Service 侧 `MeloXPlaybackQueueStore` + `restorePersistedQueue`。
// 此处旧版 `MeloXLastPlaybackStore`/`queue_v1` 整块删除：它从 `melox_last_playback` 自行
// `setMediaItems` 恢复，会与 Service 的恢复竞争、把整条队列覆盖成 1 首或卡在
// `mediaItemCount>0` 短路掉全量恢复。


    fun setSleepTimer(minutes: Int) {
        if (minutes <= 0) return
        val delayMillis = minutes * 60_000L
        sleepTimerEndRealtimeMs = System.currentTimeMillis() + delayMillis
        com.lladlam.melox.ui.settings.MeloXSettingsPreferences.setLong(
            appContext,
            SLEEP_TIMER_END_KEY,
            sleepTimerEndRealtimeMs,
        )
    }

    fun cancelSleepTimer() {
        sleepTimerEndRealtimeMs = 0L
        com.lladlam.melox.ui.settings.MeloXSettingsPreferences.setLong(appContext, SLEEP_TIMER_END_KEY, 0L)
    }

    private companion object {
        const val MEDIA_CLEAR_GRACE_MS = 500L
        const val QUEUE_REBUILD_COALESCE_MS = 120L
        const val SLEEP_TIMER_END_KEY = "playback_sleep_timer_end_epoch_ms"
    }
}

@Composable
fun rememberMeloXPlaybackUiState(
    transitionActive: Boolean = false,
    connectionEnabled: Boolean = true,
): MeloXPlaybackUiState {
    val context = LocalContext.current.applicationContext
    val state = remember(context) { MeloXPlaybackUiState(context) }

    DisposableEffect(context, connectionEnabled) {
        if (!connectionEnabled) return@DisposableEffect onDispose {}
        val future = runCatching {
            val token = SessionToken(context, ComponentName(context, MeloXPlaybackService::class.java))
            MediaController.Builder(context, token).buildAsync()
        }.getOrElse {
            android.util.Log.w("MeloXPlayerUi", "Unable to connect to playback service", it)
            return@DisposableEffect onDispose {}
        }
        val handler = Handler(Looper.getMainLooper())
        var disposed = false
        var released = false
        fun releaseConnection() {
            if (!released) {
                released = true
                runCatching { MediaController.releaseFuture(future) }
                    .onFailure { android.util.Log.w("MeloXPlayerUi", "Unable to release playback connection", it) }
            }
        }

        future.addListener(
            {
                if (!disposed) {
                    runCatching { state.bind(future.get()) }
                        .onFailure {
                            runCatching { state.unbind(releaseController = false) }
                            releaseConnection()
                            android.util.Log.w("MeloXPlayerUi", "Unable to initialize playback controller", it)
                        }
                }
            },
            { command -> handler.post(command) },
        )

        onDispose {
            disposed = true
            runCatching { state.unbind(releaseController = false) }
                .onFailure { android.util.Log.w("MeloXPlayerUi", "Unable to unbind playback controller", it) }
            // releaseFuture also handles a controller that connects after
            // disposal; canceling/ignoring the future can leak that binding.
            releaseConnection()
        }
    }

    // ★ 系统音量实时同步（2026-10-06）：注册系统音量变化监听，播放页滑块实时跟随
    //   硬件音量键 / 系统音量条调节。仅 System 模式真正注册（register 内部按 mode 判），
    //   随 volumeControlMode 与连接状态重建 —— 设置里切到 System 立即生效，切走则注销。
    DisposableEffect(context, connectionEnabled, MeloXSettingsRuntime.volumeControlMode) {
        if (!connectionEnabled) return@DisposableEffect onDispose {}
        state.registerSystemVolumeSync()
        onDispose { state.unregisterSystemVolumeSync() }
    }

    // ★ 轮询兜底（2026-10-06，用户实测「实体音量键调节不实时跟随」后加）：
    //   vivo 等 ROM 上 VOLUME_CHANGED_ACTION 广播**不可靠**（硬件音量键调节收不到回调），
    //   因此再加一条低频轮询直接读 STREAM_MUSIC 音量写入 `volume`，
    //   保证实体音量键 / 系统音量条任何路径下都能实时跟得上。
    //   syncSystemVolume 内部只在 System 模式且**非拖拽中**才写，拖拽时轮询自动让位，
    //   不会与手指位置打架。轮询值与广播值走同一个 setter，天然幂等。
    //
    // ⚠⚠ 2026-10-07 两处修正（用户续报「物理音量键调节时音量移动还是不平滑」）：
    //   ① **周期 150ms → 60ms**。硬件音量键现在已有专属的即时通道
    //      （`MeloXVolumeKeyBridge` + `MainActivity.onKeyDown`，延迟 ≈1 帧），轮询只负责
    //      "系统音量 HUD 拖动"这条路径 —— 而 150ms 的采样间隔对连续拖动来说太稀，
    //      页面看到的目标是每 150ms 跳一格 ⇒ 条只能一格一格追 ⇒ 不平滑。
    //   ② **调用搬到 `Dispatchers.Default`**。`syncSystemVolume` 里是一次同步
    //      `getStreamVolume` binder 往返；原先 `LaunchedEffect` 体默认跑在**主线程**上
    //      ⇒ 每 150ms 就往主线程塞一次 binder（个别时刻足以掉一帧）。周期还会缩短到 60ms，
    //      更不能留在主线程。
    //      ⚠ Compose state 允许从后台线程写（快照机制本身线程安全），写完全局失效照常生效。
    LaunchedEffect(context, connectionEnabled, MeloXSettingsRuntime.volumeControlMode) {
        if (!connectionEnabled) return@LaunchedEffect
        if (MeloXSettingsRuntime.volumeControlMode != MeloXVolumeControlMode.System) return@LaunchedEffect
        while (true) {
            withContext(Dispatchers.Default) { state.syncSystemVolume() }
            delay(60L)
        }
    }

    LaunchedEffect(state.isPlaying, state.mediaId, transitionActive) {
        while (true) {
            if (!transitionActive) {
                state.refreshProgress()
            }
            delay(if (transitionActive) 300L else if (state.isPlaying) 500L else 1_000L)
        }
    }

    val mixActive = MeloXAutoMixTransitionRuntime.active
    val mixProgress = MeloXAutoMixTransitionRuntime.progress
    val mixHandedOff = MeloXAutoMixTransitionRuntime.handedOff
    val mixOutgoingArtwork = MeloXAutoMixTransitionRuntime.outgoingArtworkUrl
    val mixIncomingPosition = MeloXAutoMixTransitionRuntime.incomingPositionMs
    val mixIncomingDuration = MeloXAutoMixTransitionRuntime.incomingDurationMs
    val mixIncomingTitle = MeloXAutoMixTransitionRuntime.incomingTitle
    val mixIncomingArtist = MeloXAutoMixTransitionRuntime.incomingArtist
    val mixIncomingArtwork = MeloXAutoMixTransitionRuntime.incomingArtworkUrl
    LaunchedEffect(
        mixActive,
        mixProgress,
        mixHandedOff,
        mixOutgoingArtwork,
        mixIncomingTitle,
        mixIncomingArtist,
        mixIncomingArtwork,
        mixIncomingPosition,
        mixIncomingDuration,
    ) {
        state.isInTransition = mixActive
        state.transitionProgress = mixProgress
        state.transitionHandedOff = mixHandedOff
        state.outgoingArtworkUrl = mixOutgoingArtwork
        state.incomingTitle = mixIncomingTitle
        state.incomingArtist = mixIncomingArtist
        state.incomingArtworkUrl = mixIncomingArtwork
        state.incomingPositionMs = mixIncomingPosition
        state.incomingDurationMs = mixIncomingDuration
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
                    text = state.title.ifBlank { stringResource(R.string.player_now_playing) },
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
                .meloXPlayerStatusBarsPadding()
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
                    meloXContentEnter() togetherWith meloXContentExit()
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
                text = state.title.ifBlank { stringResource(R.string.player_now_playing) },
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
                    text = stringResource(R.string.player_quality_standard),
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
            symbol = MeloXSymbol.Previous,
            enabled = state.hasPrevious || state.repeatMode == Player.REPEAT_MODE_ALL,
            onClick = state::previous,
        )
        MeloXTransportButton(
            symbol = if (state.isPlaying) MeloXSymbol.Pause else MeloXSymbol.Play,
            enabled = true,
            onClick = state::togglePlayPause,
        )
        MeloXTransportButton(
            symbol = MeloXSymbol.Next,
            enabled = state.hasNext || state.repeatMode == Player.REPEAT_MODE_ALL,
            onClick = state::next,
        )
    }
}

@Composable
private fun MeloXTransportButton(
    symbol: MeloXSymbol,
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
        MeloXSymbolIcon(
            symbol = symbol,
            modifier = Modifier.size(42.dp),
            color = Color.White.copy(alpha = if (enabled) 1f else 0.28f),
            variant = if (symbol == MeloXSymbol.Play || symbol == MeloXSymbol.Pause) MeloXSymbolVariant.Fill else MeloXSymbolVariant.Regular,
            iconSize = if (symbol == MeloXSymbol.Play || symbol == MeloXSymbol.Pause) 56.sp else 42.sp,
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
        MeloXSymbolIcon(MeloXSymbol.Volume, Modifier.size(18.dp), Color.White.copy(alpha = 0.62f), iconSize = 16.sp)
        Slider(
            value = state.volume,
            // ⚠ 过渡期禁用（收起动画中/收尾瞬间音量仍可被点到，用户报障）。
            enabled = LocalMeloXPlayerControlsEnabled.current,
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
        MeloXSymbolIcon(MeloXSymbol.Volume, Modifier.size(20.dp), Color.White.copy(alpha = 0.62f), iconSize = 18.sp)
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
            symbol = MeloXSymbol.Lyrics,
            selected = page == MeloXNowPlayingPage.Lyrics,
            onClick = { onPageSelected(MeloXNowPlayingPage.Lyrics) },
        )

        MeloXPageButton(
            symbol = MeloXSymbol.Landscape,
            selected = false,
            enabled = false,
            onClick = {},
        )

        Box {
            MeloXPageButton(
                symbol = MeloXSymbol.Queue,
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
                    MeloXSymbolIcon(
                        symbol = when {
                            state.shuffleEnabled -> MeloXSymbol.Shuffle
                            state.repeatMode == Player.REPEAT_MODE_ONE -> MeloXSymbol.Repeat
                            else -> MeloXSymbol.Repeat
                        },
                        modifier = Modifier.size(11.dp),
                        color = Color.Black.copy(alpha = 0.74f),
                        iconSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeloXPageButton(
    label: String? = null,
    symbol: MeloXSymbol? = null,
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
        symbol?.let {
            MeloXSymbolIcon(
                symbol = it,
                modifier = Modifier.size(22.dp),
                color = when {
                    !enabled -> Color.White.copy(alpha = 0.26f)
                    selected -> Color.Black.copy(alpha = 0.68f)
                    else -> Color.White.copy(alpha = 0.72f)
                },
                iconSize = 20.sp,
            )
        } ?: Text(
            text = label.orEmpty(),
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
    model: Any? = url,
) {
    Box(
        modifier = modifier.background(Color.White.copy(alpha = 0.07f)),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null && (model !is String || model.isNotBlank())) {
            AsyncImage(
                model = model,
                contentDescription = stringResource(R.string.player_artwork),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.MusicNote,
                modifier = Modifier.size(44.dp),
                color = Color.White.copy(alpha = 0.24f),
                iconSize = 42.sp,
                contentDescription = stringResource(R.string.player_artwork_default),
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
