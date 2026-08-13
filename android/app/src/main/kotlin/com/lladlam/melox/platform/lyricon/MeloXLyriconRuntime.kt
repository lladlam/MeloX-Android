package com.lladlam.melox.platform.lyricon

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.provider.LyricsCapability
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.playback.MeloXPlaybackService
import com.lladlam.melox.playback.PlaybackTrackIdentity
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Process-scoped Lyricon Provider integration.
 *
 * MeloX is a player, so it acts as a Lyricon Provider: current media metadata, real line/word
 * timing, translation, romanization, playback state and position are pushed to Lyricon's
 * central service. The Subscriber API is intentionally not used because it consumes another
 * player's state instead of publishing MeloX's own state.
 */
internal object MeloXLyriconRuntime {
    private const val TAG = "MeloXLyricon"
    private const val UPDATE_INTERVAL_MS = 250L
    private const val ORIGINAL_TITLE_KEY = "melox.system.original_title"
    private const val ORIGINAL_ARTIST_KEY = "melox.system.original_artist"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var controller: MediaController? = null
    private var provider: LyriconProvider? = null
    private var lyricJob: Job? = null
    private var currentTrackKey: String? = null
    private var lastPositionDispatchMs = 0L
    private var lastPlaying: Boolean? = null
    private var lastTranslation: Boolean? = null
    private var lastRomanization: Boolean? = null
    private var started = false

    private var registry: com.lladlam.melox.core.music.provider.MusicProviderRegistry? = null
    private var downloads: MeloXDownloadStore? = null

    fun start(context: Context) {
        if (started) return
        val app = context.applicationContext
        started = true
        appContext = app
        registry = MeloXMusicProviders.create(app)
        downloads = MeloXDownloadStore.get(app)

        provider = LyriconFactory.createProvider(
            context = app,
            providerPackageName = app.packageName,
            playerPackageName = app.packageName,
        ).apply {
            autoSync = true
            register()
            player.setPositionUpdateInterval(UPDATE_INTERVAL_MS.toInt())
        }

        val token = SessionToken(app, ComponentName(app, MeloXPlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        val executor = Executor { command -> app.mainExecutor.execute(command) }
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess {
                        controller = it
                        handler.post(updater)
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Unable to connect to MeloX MediaSession", error)
                    }
            },
            executor,
        )
    }

    private val updater = object : Runnable {
        override fun run() {
            runCatching { publishCurrentState() }
                .onFailure { Log.w(TAG, "Lyricon state update failed", it) }
            if (started) handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    private fun publishCurrentState() {
        val remote = provider?.player ?: return
        val active = controller ?: return
        val item = active.currentMediaItem
        if (item == null) {
            if (currentTrackKey != null) {
                currentTrackKey = null
                remote.setSong(null)
            }
            return
        }

        val identity = PlaybackTrackIdentity.fromMediaItem(item) ?: return
        val key = PlaybackTrackIdentity.encode(identity)
        if (key != currentTrackKey) {
            currentTrackKey = key
            lyricJob?.cancel()
            val metadata = stableMetadata(item.mediaMetadata)
            remote.setSong(
                Song(
                    id = key,
                    name = metadata.title?.toString(),
                    artist = metadata.artist?.toString(),
                    duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L,
                ),
            )
            lyricJob = scope.launch {
                val document = withContext(Dispatchers.IO) {
                    runCatching { loadLyrics(item, identity.source) }
                        .onFailure { Log.w(TAG, "Unable to load lyrics for $key", it) }
                        .getOrNull()
                }
                if (currentTrackKey == key && document != null) {
                    val current = controller
                    val stable = stableMetadata(item.mediaMetadata)
                    remote.setSong(
                        Song(
                            id = key,
                            name = stable.title?.toString(),
                            artist = stable.artist?.toString(),
                            duration = current?.duration?.takeIf { it != C.TIME_UNSET && it > 0L }
                                ?: 0L,
                            lyrics = document.toLyriconLines(),
                        ),
                    )
                    current?.currentPosition?.coerceAtLeast(0L)?.let(remote::setPosition)
                }
                lyricJob = null
            }
        }

        val playing = active.isPlaying
        if (playing != lastPlaying) {
            remote.setPlaybackState(playing)
            lastPlaying = playing
        }

        val context = appContext ?: return
        val translation = MeloXSettingsPreferences.boolean(context, "lyrics_translation", true)
        if (translation != lastTranslation) {
            remote.setDisplayTranslation(translation)
            lastTranslation = translation
        }
        val romanization = MeloXSettingsPreferences.boolean(context, "lyrics_romanization", true)
        if (romanization != lastRomanization) {
            remote.setDisplayRoma(romanization)
            lastRomanization = romanization
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastPositionDispatchMs >= UPDATE_INTERVAL_MS) {
            remote.setPosition(active.currentPosition.coerceAtLeast(0L))
            lastPositionDispatchMs = now
        }
    }

    private suspend fun loadLyrics(item: MediaItem, source: MusicSource): LyricsDocument {
        val context = appContext ?: return LyricsDocument(emptyList())
        val identity = PlaybackTrackIdentity.fromMediaItem(item)
            ?: return LyricsDocument(emptyList())
        if (source == MusicSource.Netease) {
            val songId = identity.value.toLongOrNull() ?: return LyricsDocument(emptyList())
            return downloads?.localLyrics(songId)
                ?: NeteaseSearchClient(
                    cookieProvider = { NeteaseSessionStore.readCookie(context) },
                ).lyrics(songId)
        }

        val capability = registry?.require(source) as? LyricsCapability
            ?: return LyricsDocument(emptyList())
        val metadata = stableMetadata(item.mediaMetadata)
        val artistRefs = metadata.artist?.toString().orEmpty()
            .split(Regex("\\s*(?:、|/|&|,|;|；)\\s*"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .ifEmpty { listOf("未知歌手") }
            .map { MusicArtistRef(name = it) }
        val albumName = metadata.albumTitle?.toString().orEmpty()
        val track = MusicTrack(
            id = identity,
            title = metadata.title?.toString().orEmpty().ifBlank { "未知歌曲" },
            artists = artistRefs,
            album = albumName.takeIf(String::isNotBlank)?.let { name ->
                MusicAlbumRef(name = name, artworkUrl = metadata.artworkUri?.toString())
            },
            artworkUrl = metadata.artworkUri?.toString(),
            durationMs = controller?.duration?.takeIf { it != C.TIME_UNSET && it > 0L },
        )
        return capability.lyrics(track)
    }

    private fun stableMetadata(metadata: MediaMetadata): MediaMetadata {
        val extras = metadata.extras
        val originalTitle = extras?.getString(ORIGINAL_TITLE_KEY)
        val originalArtist = extras?.getString(ORIGINAL_ARTIST_KEY)
        if (originalTitle.isNullOrBlank() && originalArtist.isNullOrBlank()) return metadata
        return metadata.buildUpon()
            .setTitle(originalTitle?.takeIf(String::isNotBlank) ?: metadata.title)
            .setArtist(originalArtist?.takeIf(String::isNotBlank) ?: metadata.artist)
            .setAlbumTitle(originalArtist?.takeIf(String::isNotBlank) ?: metadata.albumTitle)
            .build()
    }

    private fun LyricsDocument.toLyriconLines(): List<RichLyricLine> =
        lines.mapIndexedNotNull { index, line -> line.toLyriconLine(lines.getOrNull(index + 1)) }

    private fun LyricLine.toLyriconLine(next: LyricLine?): RichLyricLine? {
        val beginMs = timeMs.coerceAtLeast(0L)
        val inferredEnd = durationMs?.let { beginMs + it }
            ?: next?.timeMs
            ?: (beginMs + 3_000L)
        val endMs = inferredEnd.coerceAtLeast(beginMs + 1L)
        val primaryText = text.trim().takeIf(String::isNotBlank) ?: return null
        val words = syllables
            .mapNotNull { syllable ->
                val wordText = syllable.text.takeIf(String::isNotEmpty) ?: return@mapNotNull null
                val wordBegin = syllable.startTimeMs.coerceIn(beginMs, endMs - 1L)
                val wordEnd = syllable.endTimeMs.coerceIn(wordBegin + 1L, endMs)
                LyricWord(
                    begin = wordBegin,
                    end = wordEnd,
                    text = wordText,
                )
            }
            .takeIf(List<LyricWord>::isNotEmpty)
        return RichLyricLine(
            begin = beginMs,
            end = endMs,
            text = primaryText,
            words = words,
            translation = translation?.trim()?.takeIf(String::isNotBlank),
            roma = romanization?.trim()?.takeIf(String::isNotBlank),
        )
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(updater)
        lyricJob?.cancel()
        lyricJob = null
        runCatching { provider?.player?.setSong(null) }
        runCatching { provider?.unregister() }
        runCatching { provider?.destroy() }
        provider = null
        controller?.release()
        controller = null
        currentTrackKey = null
        lastPlaying = null
        lastTranslation = null
        lastRomanization = null
        registry = null
        downloads = null
        appContext = null
    }
}
