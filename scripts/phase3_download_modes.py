from pathlib import Path
import re
ROOT=Path('android/app/src/main/kotlin/com/lladlam/melox')
def r(p): return p.read_text()
def w(p,s): p.write_text(s)
def one(s,a,b,label):
    if a not in s: raise SystemExit('missing '+label)
    return s.replace(a,b,1)

# Quality client: dedicated upstream download route with playback fallback.
p=ROOT/'core/audio/NeteaseQualityClient.kt'; s=r(p)
marker='''    private fun parseAvailability(song: JSONObject): SongAudioAvailability {\n'''
method='''    fun downloadSourceBlocking(\n        songId: Long,\n        requestedQuality: MusicQuality,\n    ): NeteasePlaybackSource {\n        val availability = audioAvailabilityBlocking(songId)\n        val loggedIn = NeteaseSessionStore.containsMusicU(cookieProvider())\n        var lastError: Throwable? = null\n        for (candidate in requestedQuality.playbackCandidates(availability)) {\n            try {\n                val payload = JSONObject()\n                    .put("id", songId)\n                    .put("level", candidate.apiLevel)\n                if (candidate.requiresImmersiveType) payload.put("immerseType", "c51")\n                val response = eapi(\n                    uri = "/api/song/enhance/download/url/v1",\n                    data = payload,\n                    authenticated = loggedIn,\n                    cookieHeaderOverride = cookieProvider().takeIf(String::isNotBlank),\n                )\n                val data = response.optJSONObject("data")\n                    ?: response.optJSONArray("data")?.optJSONObject(0)\n                    ?: throw IOException("download route returned no source")\n                val rawUrl = data.optString("url").takeIf(String::isNotBlank)\n                    ?: throw IOException("download route returned no URL")\n                val actual = MusicQuality.fromApiLevel(data.optString("level").takeIf(String::isNotBlank)) ?: candidate\n                return NeteasePlaybackSource(\n                    url = secureUrl(rawUrl),\n                    bitrate = data.optInt("br").takeIf { it > 0 },\n                    format = data.optString("type").takeIf(String::isNotBlank),\n                    quality = actual,\n                )\n            } catch (error: Throwable) {\n                lastError = error\n            }\n        }\n        // Upstream DownloadStore falls back to the ordinary playback source when\n        // the account-specific download route is unavailable.\n        return runCatching { playbackSourceBlocking(songId, requestedQuality) }\n            .getOrElse { throw IOException("无法取得下载音源", lastError ?: it) }\n    }\n\n'''
if marker not in s: raise SystemExit('quality parse marker')
s=s.replace(marker,method+marker,1); w(p,s)

# Resolver: prefer persistent local file, and use DefaultDataSource in service later.
p=ROOT/'playback/NeteasePlaybackResolver.kt'; s=r(p)
s=one(s,'''    @Suppress("UNUSED_PARAMETER")\n    private val client: NeteaseSearchClient = NeteaseSearchClient(cookieProvider = cookieProvider),\n) : ResolvingDataSource.Resolver {\n''','''    @Suppress("UNUSED_PARAMETER")\n    private val client: NeteaseSearchClient = NeteaseSearchClient(cookieProvider = cookieProvider),\n    private val localSourceProvider: (Long) -> Uri? = { null },\n) : ResolvingDataSource.Resolver {\n''','resolver local provider')
s=one(s,'''        val requestedQuality = MusicQuality.fromApiLevel(uri.getQueryParameter(QUALITY_QUERY))\n            ?: MusicQualityRuntime.selected\n''','''        localSourceProvider(songId)?.let { local ->\n            return dataSpec.withUri(local)\n        }\n        val requestedQuality = MusicQuality.fromApiLevel(uri.getQueryParameter(QUALITY_QUERY))\n            ?: MusicQualityRuntime.selected\n''','resolver local check')
s=one(s,'''        val songId = uri.lastPathSegment?.toLongOrNull() ?: return uri\n        val requestedQuality = MusicQuality.fromApiLevel(uri.getQueryParameter(QUALITY_QUERY))\n''','''        val songId = uri.lastPathSegment?.toLongOrNull() ?: return uri\n        localSourceProvider(songId)?.let { return it }\n        val requestedQuality = MusicQuality.fromApiLevel(uri.getQueryParameter(QUALITY_QUERY))\n''','resolver reported local')
w(p,s)

# Library client: upstream similar-song route for infinity/autoplay.
p=ROOT/'core/library/NeteaseLibraryClient.kt'; s=r(p)
marker='''    fun userPlaylistsBlocking(userId: Long, limit: Int = 2_000): List<NeteasePlaylistSummary> {\n'''
method='''    fun similarSongsBlocking(songId: Long, limit: Int = 50): List<SearchSong> {\n        if (songId <= 0L) return emptyList()\n        // Upstream uses /api/v1/discovery/simiSong. The direct EAPI transport is\n        // accepted by the same interface host and keeps Android on one client.\n        val response = eapi(\n            uri = "/api/v1/discovery/simiSong",\n            data = JSONObject().put("songid", songId).put("limit", limit.coerceIn(1, 50)),\n            authenticated = NeteaseSessionStore.containsMusicU(cookieProvider()),\n        )\n        val songs = response.optJSONArray("songs") ?: JSONArray()\n        return buildList {\n            for (index in 0 until songs.length()) parseSong(songs.optJSONObject(index))?.let(::add)\n        }\n    }\n\n'''
if marker not in s: raise SystemExit('library user marker')
s=s.replace(marker,method+marker,1); w(p,s)

# PlaybackCommands: expose media-item builder to service recommendation append.
p=ROOT/'playback/PlaybackCommands.kt'; s=r(p)
marker='''    private fun SearchSong.toMediaItem(quality: MusicQuality, queueOrigin: String): MediaItem {\n'''
method='''    internal fun mediaItemFor(\n        song: SearchSong,\n        quality: MusicQuality = MusicQualityRuntime.selected,\n        queueOrigin: String = QUEUE_ORIGIN_BASE,\n    ): MediaItem = song.toMediaItem(quality, queueOrigin)\n\n'''
if marker not in s: raise SystemExit('PlaybackCommands media marker')
s=s.replace(marker,method+marker,1); w(p,s)

# Playback UI mode toggles persisted and service-visible.
p=ROOT/'ui/player/MeloXPlayerUi.kt'; s=r(p)
s=one(s,'import android.content.ComponentName\n','import android.content.ComponentName\nimport android.content.Context\n','Context import')
s=one(s,'import com.lladlam.melox.playback.MeloXPlaybackService\n','import com.lladlam.melox.playback.MeloXPlaybackService\nimport com.lladlam.melox.playback.MeloXPlaybackModePreferences\n','mode pref import')
s=one(s,'class MeloXPlaybackUiState internal constructor() {','class MeloXPlaybackUiState internal constructor(private val appContext: Context) {','state context')
s=one(s,'''    var autoplayEnabled by mutableStateOf(false)\n        private set\n    var autoMixEnabled by mutableStateOf(false)\n        private set\n''','''    var autoplayEnabled by mutableStateOf(MeloXPlaybackModePreferences.autoplay(appContext))\n        private set\n    var autoMixEnabled by mutableStateOf(MeloXPlaybackModePreferences.autoMix(appContext))\n        private set\n''','initial mode prefs')
s=s.replace('fun toggleAutoplay() { autoplayEnabled = !autoplayEnabled }','''fun toggleAutoplay() {\n        autoplayEnabled = !autoplayEnabled\n        MeloXPlaybackModePreferences.setAutoplay(appContext, autoplayEnabled)\n    }''',1)
s=s.replace('fun toggleAutoMix() { autoMixEnabled = !autoMixEnabled }','''fun toggleAutoMix() {\n        autoMixEnabled = !autoMixEnabled\n        MeloXPlaybackModePreferences.setAutoMix(appContext, autoMixEnabled)\n    }''',1)
s=s.replace('val state = remember { MeloXPlaybackUiState() }','val state = remember(context) { MeloXPlaybackUiState(context) }',1)
w(p,s)

# Service: local-source DataSource, autoplay recommendation refill, real dual-player crossfade.
p=ROOT/'playback/MeloXPlaybackService.kt'; s=r(p)
# rewrite whole file for clarity
s=r'''package com.lladlam.melox.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lladlam.melox.MainActivity
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.network.NeteaseSearchClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class MeloXPlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var incomingPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private lateinit var mediaSourceFactory: DefaultMediaSourceFactory
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var recommendationJob: Job? = null
    private var recommendationSeed: Long? = null
    private var preparedMixSourceId: String? = null
    private var mixStartedAt = 0L
    private var mixBaseVolume = 1f

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Playback failed: code=${error.errorCodeName}, message=${error.message}", error)
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "isPlaying=$isPlaying, ongoing=${isPlaybackOngoing()}")
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) ensureAutoplayRecommendations(forceAdvance = true)
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            recommendationSeed = null
            cancelPreparedMix()
        }
    }

    private val modeMonitor = object : Runnable {
        override fun run() {
            val active = player
            if (active != null) {
                maybePrepareAutoplay(active)
                maybeRunAutoMix(active)
            }
            handler.postDelayed(this, 100L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36",
                    "Referer" to "https://music.163.com/",
                ),
            )
        val downloadStore = MeloXDownloadStore.get(this)
        val cookieProvider = { NeteaseSessionStore.readCookie(this@MeloXPlaybackService) }
        val resolving = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this, httpFactory),
            NeteasePlaybackResolver(
                cookieProvider = cookieProvider,
                client = NeteaseSearchClient(cookieProvider = cookieProvider),
                localSourceProvider = downloadStore::localPlaybackUri,
            ),
        )
        mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(resolving)

        val active = buildPlayer(managesAudioFocus = true)
        player = active
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_NOW_PLAYING
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this, 1001, sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, active)
            .setSessionActivity(sessionActivity)
            .build()
        handler.post(modeMonitor)
    }

    private fun buildPlayer(managesAudioFocus: Boolean): ExoPlayer =
        ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply {
                setAudioAttributes(audioAttributes, managesAudioFocus)
                setHandleAudioBecomingNoisy(managesAudioFocus)
                addListener(playerListener)
            }

    private fun maybePrepareAutoplay(active: ExoPlayer) {
        if (!MeloXPlaybackModePreferences.autoplay(this)) return
        if (active.mediaItemCount <= 0 || active.currentMediaItemIndex < 0) return
        val atTail = active.currentMediaItemIndex >= active.mediaItemCount - 1
        if (!atTail) return
        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return
        val remaining = duration - active.currentPosition
        if (remaining <= AUTOPLAY_PRELOAD_MS) ensureAutoplayRecommendations(forceAdvance = false)
    }

    private fun ensureAutoplayRecommendations(forceAdvance: Boolean) {
        if (!MeloXPlaybackModePreferences.autoplay(this)) return
        val active = player ?: return
        val seed = active.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        if (recommendationJob?.isActive == true || recommendationSeed == seed) {
            if (forceAdvance && active.playbackState == Player.STATE_ENDED && active.hasNextMediaItem()) {
                active.seekToNextMediaItem(); active.play()
            }
            return
        }
        recommendationSeed = seed
        recommendationJob = serviceScope.launch {
            val cookie = { NeteaseSessionStore.readCookie(this@MeloXPlaybackService) }
            val recommendations = withContext(Dispatchers.IO) {
                runCatching { NeteaseLibraryClient(cookieProvider = cookie).similarSongsBlocking(seed, 30) }
                    .getOrDefault(emptyList())
            }
            val existing = (0 until active.mediaItemCount).map { active.getMediaItemAt(it).mediaId }.toSet()
            val quality = MusicQualityPreferences.read(this@MeloXPlaybackService)
            recommendations
                .filterNot { it.id.toString() in existing }
                .take(20)
                .forEach { song ->
                    active.addMediaItem(PlaybackCommands.mediaItemFor(song, quality, PlaybackCommands.QUEUE_ORIGIN_BASE))
                }
            if (forceAdvance && active.playbackState == Player.STATE_ENDED && active.hasNextMediaItem()) {
                active.seekToNextMediaItem()
                active.prepare()
                active.play()
            }
            recommendationJob = null
        }
    }

    private fun maybeRunAutoMix(active: ExoPlayer) {
        if (!MeloXPlaybackModePreferences.autoMix(this)) {
            cancelPreparedMix()
            return
        }
        if (!active.isPlaying || active.repeatMode == Player.REPEAT_MODE_ONE || !active.hasNextMediaItem()) return
        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return
        val remaining = duration - active.currentPosition
        val sourceId = active.currentMediaItem?.mediaId ?: return
        if (incomingPlayer == null && remaining <= AUTOMIX_PRELOAD_MS) {
            prepareIncoming(active, sourceId)
        }
        val incoming = incomingPlayer ?: return
        if (preparedMixSourceId != sourceId) {
            cancelPreparedMix(); return
        }
        if (mixStartedAt == 0L && incoming.playbackState == Player.STATE_READY && remaining <= AUTOMIX_DURATION_MS) {
            mixBaseVolume = active.volume
            incoming.volume = 0f
            incoming.play()
            mixStartedAt = SystemClock.elapsedRealtime()
        }
        if (mixStartedAt > 0L) {
            val progress = ((SystemClock.elapsedRealtime() - mixStartedAt).toFloat() / AUTOMIX_DURATION_MS.toFloat()).coerceIn(0f, 1f)
            active.volume = mixBaseVolume * (1f - progress)
            incoming.volume = mixBaseVolume * progress
            if (progress >= 1f) completeAutoMix(active, incoming)
        }
    }

    private fun prepareIncoming(active: ExoPlayer, sourceId: String) {
        val nextIndex = active.currentMediaItemIndex + 1
        if (nextIndex !in 0 until active.mediaItemCount) return
        val incoming = buildPlayer(managesAudioFocus = false)
        val items = List(active.mediaItemCount) { active.getMediaItemAt(it) }
        incoming.setMediaItems(items, nextIndex, 0L)
        incoming.volume = 0f
        incoming.prepare()
        incomingPlayer = incoming
        preparedMixSourceId = sourceId
        mixStartedAt = 0L
    }

    private fun completeAutoMix(old: ExoPlayer, incoming: ExoPlayer) {
        incoming.volume = mixBaseVolume
        incoming.setAudioAttributes(audioAttributes, true)
        incoming.setHandleAudioBecomingNoisy(true)
        mediaSession?.setPlayer(incoming)
        player = incoming
        incomingPlayer = null
        preparedMixSourceId = null
        mixStartedAt = 0L
        old.removeListener(playerListener)
        old.pause()
        old.release()
    }

    private fun cancelPreparedMix() {
        val active = player
        if (mixStartedAt > 0L && active != null) active.volume = mixBaseVolume
        incomingPlayer?.run {
            removeListener(playerListener)
            pause()
            release()
        }
        incomingPlayer = null
        preparedMixSourceId = null
        mixStartedAt = 0L
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Log.d(TAG, "Controller connected: ${controllerInfo.packageName}")
        return mediaSession
    }

    override fun onDestroy() {
        handler.removeCallbacks(modeMonitor)
        recommendationJob?.cancel()
        serviceScope.cancel()
        cancelPreparedMix()
        mediaSession?.release(); mediaSession = null
        player?.removeListener(playerListener)
        player?.release(); player = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MeloXPlayback"
        const val AUTOPLAY_PRELOAD_MS = 15_000L
        const val AUTOMIX_PRELOAD_MS = 10_000L
        const val AUTOMIX_DURATION_MS = 6_000L
    }
}
'''
w(p,s)

# Song actions: real download/cancel/delete row.
p=ROOT/'ui/player/MeloXSongActionsOverlay.kt'; s=r(p)
s=one(s,'import com.lladlam.melox.core.account.NeteaseSessionStore\n','''import com.lladlam.melox.core.account.NeteaseSessionStore\nimport com.lladlam.melox.core.audio.MusicQualityPreferences\nimport com.lladlam.melox.core.download.MeloXDownloadStore\n''','download imports')
needle='''    val account = remember(app) { NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(app) }) }\n'''
insert='''    val account = remember(app) { NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(app) }) }\n    val downloads = remember(app) { MeloXDownloadStore.get(app) }\n'''
if needle not in s: raise SystemExit('actions account marker')
s=s.replace(needle,insert,1)
needle='''                            ActionItem("添加到播放队列", "+") { if(playbackState!=null)playbackState.addCurrentToQueue() else PlaybackCommands.addToQueue(context,song); onDismiss() }\n                            ActionItem("添加到歌单", "≡") { page=SongActionPage.AddToPlaylist; scope.launch { loadOwnedPlaylists() } }\n'''
insert='''                            ActionItem("添加到播放队列", "+") { if(playbackState!=null)playbackState.addCurrentToQueue() else PlaybackCommands.addToQueue(context,song); onDismiss() }\n                            when {\n                                downloads.contains(song.id) -> ActionItem("删除下载", "↓×") { downloads.remove(song.id) }\n                                downloads.isDownloading(song.id) -> ActionItem("取消下载", "↓×") { downloads.cancel(song.id) }\n                                else -> ActionItem("下载歌曲", "↓") { downloads.start(song, MusicQualityPreferences.read(app)) }\n                            }\n                            downloads.activeDownloads[song.id]?.let { active ->\n                                val percent = active.fractionCompleted?.let { (it * 100).toInt() }\n                                Text(percent?.let { "正在下载 $it%" } ?: "正在下载…", color=Color.White.copy(alpha=.52f), fontSize=11.sp, modifier=Modifier.padding(start=46.dp,bottom=4.dp))\n                            }\n                            downloads.errorMessage?.let { Text(it,color=Color(0xFFFF8A90),fontSize=11.sp,modifier=Modifier.padding(start=46.dp,bottom=4.dp)) }\n                            ActionItem("添加到歌单", "≡") { page=SongActionPage.AddToPlaylist; scope.launch { loadOwnedPlaylists() } }\n'''
if needle not in s: raise SystemExit('actions queue marker')
s=s.replace(needle,insert,1); w(p,s)

# Settings: downloads now real; storage exposes active/completed and playback/removal.
p=ROOT/'ui/settings/SettingsScreen.kt'; s=r(p)
s=one(s,'import com.lladlam.melox.core.audio.MusicQualityPreferences\n','import com.lladlam.melox.core.audio.MusicQualityPreferences\nimport com.lladlam.melox.core.download.MeloXDownloadStore\n','settings download import')
s=s.replace('SettingsToggleRow(context, "下载", "feature_downloads", false, "Android 下载管理尚未接入，关闭时不会展示伪下载入口。")','SettingsToggleRow(context, "下载", "feature_downloads", true, "歌曲更多菜单支持下载、取消和删除；播放时优先使用本地文件。")',1)
old=re.search(r'@Composable\nprivate fun StorageSettings\(context: android\.content\.Context\) \{.*?\n\}\n\n@Composable\nprivate fun TabLayoutSettings',s,re.S)
if not old: raise SystemExit('StorageSettings block missing')
new=r'''@Composable
private fun StorageSettings(context: android.content.Context) {
    var cacheSize by remember { mutableStateOf("计算中…") }
    val scope = rememberCoroutineScope()
    val downloads = remember(context) { MeloXDownloadStore.get(context) }
    suspend fun refresh() {
        val bytes = withContext(Dispatchers.IO) { context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
        cacheSize = formatBytes(bytes)
    }
    LaunchedEffect(Unit) { refresh() }

    SettingsInfoCard("临时缓存", cacheSize)
    Spacer(Modifier.height(10.dp))
    SettingsInfoCard("已下载歌曲", "${downloads.downloads.size} 首 · ${formatBytes(downloads.totalByteCount)}")
    Spacer(Modifier.height(14.dp))

    SettingsToggleRow(context, "按播放次数自动缓存", "downloads_auto_cache", false, "与上游 DownloadStore 对齐的自动缓存偏好；手动下载始终可用。")

    if (downloads.activeDownloads.isNotEmpty()) {
        Text("正在下载", modifier = Modifier.padding(top=10.dp,bottom=8.dp), fontWeight=FontWeight.SemiBold)
        SettingsGlassGroup {
            downloads.activeDownloads.values.forEach { active ->
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(active.song.name, maxLines=1, overflow=TextOverflow.Ellipsis)
                        Text(active.fractionCompleted?.let { "${(it*100).toInt()}% · ${active.quality.title}" } ?: active.quality.title, color=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f), fontSize=11.sp)
                    }
                    Text("取消", color=MaterialTheme.colorScheme.error, modifier=Modifier.clickable { downloads.cancel(active.song.id) }.padding(8.dp))
                }
            }
        }
    }

    if (downloads.downloads.isNotEmpty()) {
        Text("已下载", modifier = Modifier.padding(top=18.dp,bottom=8.dp), fontWeight=FontWeight.SemiBold)
        SettingsGlassGroup {
            downloads.downloads.forEach { item ->
                Row(Modifier.fillMaxWidth().clickable { PlaybackCommands.playQueue(context, downloads.downloadedSongs, item.song.id) }.padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.song.name, maxLines=1, overflow=TextOverflow.Ellipsis)
                        Text("${item.quality.title} · ${formatBytes(item.byteCount)}", color=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f), fontSize=11.sp)
                    }
                    Text("删除", color=MaterialTheme.colorScheme.error, modifier=Modifier.clickable { downloads.remove(item.song.id) }.padding(8.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SettingsDangerButton("删除全部已下载歌曲") { downloads.removeAll() }
    } else if (downloads.activeDownloads.isEmpty()) {
        SettingsInfoCard("下载", "还没有下载歌曲；可在歌曲的“更多”菜单中选择“下载歌曲”。")
    }

    downloads.errorMessage?.let { Text(it, color=MaterialTheme.colorScheme.error, fontSize=12.sp, modifier=Modifier.padding(top=10.dp)) }
    Spacer(Modifier.height(14.dp))
    SettingsActionButton("清理临时缓存") {
        scope.launch {
            withContext(Dispatchers.IO) { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
            refresh()
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun TabLayoutSettings'''
s=s[:old.start()]+new+s[old.end():]
# Fix About intro long-value row by replacing first card.
old_intro='''    SettingsInfoCard(\n        "MeloX Android",\n        "MeloX 的 Android 原生迁移版。\\n\\nAndroid 原生迁移与维护：lladlam\\n上游 iOS 原生项目：youshen2/MeloX（SwiftUI）",\n    )\n'''
new_intro='''    SettingsGlassGroup {\n        Column(Modifier.padding(18.dp)) {\n            Text("MeloX Android", fontSize = 22.sp, fontWeight = FontWeight.Bold)\n            Text("MeloX 的 Android 原生迁移版。", modifier = Modifier.padding(top=7.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha=.62f))\n            Text("Android 原生迁移与维护：lladlam", modifier = Modifier.padding(top=14.dp), fontWeight=FontWeight.SemiBold)\n            Text("上游 iOS 原生项目：youshen2/MeloX（SwiftUI）", modifier = Modifier.padding(top=5.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha=.58f))\n        }\n    }\n'''
if old_intro in s: s=s.replace(old_intro,new_intro,1)
w(p,s)
print('phase3 downloads/modes patch applied')
