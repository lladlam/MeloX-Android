from pathlib import Path
import re

ROOT = Path('.')

def load(path):
    p = ROOT / path
    return p, p.read_text(encoding='utf-8')

def save(path, text):
    p = ROOT / path
    p.write_text(text, encoding='utf-8')

def rep(path, old, new, count=1):
    p, s = load(path)
    if old not in s:
        raise SystemExit(f'pattern not found in {path}: {old[:120]!r}')
    s2 = s.replace(old, new, count)
    p.write_text(s2, encoding='utf-8')

# 1) Persist explicit shuffle mode alongside autoplay/AutoMix.
path = 'android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXPlaybackModePreferences.kt'
rep(path,
'''    private const val KEY_AUTOPLAY = "autoplay"\n    private const val KEY_AUTOMIX = "auto_mix"\n''',
'''    private const val KEY_SHUFFLE = "shuffle"\n    private const val KEY_AUTOPLAY = "autoplay"\n    private const val KEY_AUTOMIX = "auto_mix"\n''')
rep(path,
'''    fun autoplay(context: Context): Boolean =\n''',
'''    fun shuffle(context: Context): Boolean =\n        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)\n            .getBoolean(KEY_SHUFFLE, false)\n\n    fun autoplay(context: Context): Boolean =\n''')
rep(path,
'''    fun setAutoplay(context: Context, enabled: Boolean) {\n''',
'''    fun setShuffle(context: Context, enabled: Boolean) {\n        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)\n            .edit().putBoolean(KEY_SHUFFLE, enabled).apply()\n    }\n\n    fun setAutoplay(context: Context, enabled: Boolean) {\n''')

# 2) PlaybackCommands: explicit physical queue order + local artwork.
path = 'android/app/src/main/kotlin/com/lladlam/melox/playback/PlaybackCommands.kt'
p, s = load(path)
s = s.replace('import androidx.media3.common.MediaMetadata\n', 'import androidx.media3.common.MediaMetadata\nimport androidx.media3.common.Player\n')
s = s.replace('import com.lladlam.melox.core.audio.MusicQualityRuntime\n', 'import com.lladlam.melox.core.audio.MusicQualityRuntime\nimport com.lladlam.melox.core.download.MeloXDownloadStore\n')
s = s.replace('    const val QUEUE_ORIGIN_BASE = "base"\n    const val QUEUE_ORIGIN_MANUAL = "manual"\n', '    const val QUEUE_ORIGIN_BASE = "base"\n    const val QUEUE_ORIGIN_MANUAL = "manual"\n    const val QUEUE_ORIGINAL_INDEX_KEY = "melox.queue.original_index"\n    const val QUEUE_ORIGINAL_INDEX_UNSET = -1\n')
old = '''                    val queue = songs\n                        .ifEmpty { return@addListener }\n                        .map { song -> song.toMediaItem(quality, QUEUE_ORIGIN_BASE) }\n                    val startIndex = songs.indexOfFirst { it.id == selectedSongId }\n                        .takeIf { it >= 0 }\n                        ?: 0\n\n                    activeController?.takeIf { it !== controller }?.release()\n                    activeController = controller\n\n                    controller.setMediaItems(queue, startIndex, C.TIME_UNSET)\n                    controller.prepare()\n                    controller.play()\n'''
new = '''                    val sourceSongs = songs.ifEmpty { return@addListener }\n                    val downloads = MeloXDownloadStore.get(appContext)\n                    val originalQueue = sourceSongs.mapIndexed { index, song ->\n                        song.toMediaItem(\n                            quality = quality,\n                            queueOrigin = QUEUE_ORIGIN_BASE,\n                            originalIndex = index,\n                            artworkOverride = downloads.localArtworkUri(song.id),\n                        )\n                    }\n                    val sourceStartIndex = sourceSongs.indexOfFirst { it.id == selectedSongId }\n                        .takeIf { it >= 0 } ?: 0\n                    val useShuffle = MeloXPlaybackModePreferences.shuffle(appContext)\n                    val queue = if (useShuffle) {\n                        val selected = originalQueue[sourceStartIndex]\n                        listOf(selected) + originalQueue\n                            .filterIndexed { index, _ -> index != sourceStartIndex }\n                            .shuffled()\n                    } else originalQueue\n                    val startIndex = if (useShuffle) 0 else sourceStartIndex\n\n                    activeController?.takeIf { it !== controller }?.release()\n                    activeController = controller\n\n                    // MeloX uses the physical pending-play list as the source of truth.\n                    // Keep Media3's opaque shuffle order disabled so UI, next(), and AutoMix\n                    // all observe exactly the same order.\n                    controller.shuffleModeEnabled = false\n                    controller.setMediaItems(queue, startIndex, C.TIME_UNSET)\n                    controller.prepare()\n                    controller.play()\n'''
if old not in s: raise SystemExit('playQueue block not found')
s = s.replace(old, new, 1)
s = s.replace('controller.addMediaItem(song.toMediaItem(quality, QUEUE_ORIGIN_MANUAL))', 'controller.addMediaItem(song.toMediaItem(quality, QUEUE_ORIGIN_MANUAL, QUEUE_ORIGINAL_INDEX_UNSET, MeloXDownloadStore.get(context.applicationContext).localArtworkUri(song.id)))')
s = s.replace('controller.addMediaItem(insertion, song.toMediaItem(quality, QUEUE_ORIGIN_MANUAL))', 'controller.addMediaItem(insertion, song.toMediaItem(quality, QUEUE_ORIGIN_MANUAL, QUEUE_ORIGINAL_INDEX_UNSET, MeloXDownloadStore.get(context.applicationContext).localArtworkUri(song.id)))')
old = '''    internal fun mediaItemFor(\n        song: SearchSong,\n        quality: MusicQuality = MusicQualityRuntime.selected,\n        queueOrigin: String = QUEUE_ORIGIN_BASE,\n    ): MediaItem = song.toMediaItem(quality, queueOrigin)\n\n    private fun SearchSong.toMediaItem(quality: MusicQuality, queueOrigin: String): MediaItem {\n        val metadata = MediaMetadata.Builder()\n'''
new = '''    fun setExplicitShuffle(\n        context: Context,\n        player: Player,\n        enabled: Boolean,\n    ) {\n        val count = player.mediaItemCount\n        if (count <= 0 || player.currentMediaItemIndex !in 0 until count) {\n            MeloXPlaybackModePreferences.setShuffle(context, enabled)\n            player.shuffleModeEnabled = false\n            return\n        }\n        val currentIndex = player.currentMediaItemIndex\n        val currentPosition = player.currentPosition.coerceAtLeast(0L)\n        val resume = player.playWhenReady\n        val items = List(count) { player.getMediaItemAt(it) }\n        val historyAndCurrent = items.take(currentIndex + 1)\n        val future = items.drop(currentIndex + 1)\n        val manual = future.filter { it.queueOrigin() == QUEUE_ORIGIN_MANUAL }\n        val base = future.filter { it.queueOrigin() != QUEUE_ORIGIN_MANUAL }\n        val orderedBase = if (enabled) {\n            base.shuffled()\n        } else {\n            base.sortedWith(compareBy<MediaItem> { item ->\n                item.mediaMetadata.extras?.getInt(QUEUE_ORIGINAL_INDEX_KEY, QUEUE_ORIGINAL_INDEX_UNSET)\n                    ?.takeIf { it >= 0 } ?: Int.MAX_VALUE\n            }.thenBy { it.mediaId })\n        }\n        val rebuilt = historyAndCurrent + manual + orderedBase\n        player.shuffleModeEnabled = false\n        player.setMediaItems(rebuilt, currentIndex.coerceIn(0, rebuilt.lastIndex), currentPosition)\n        player.prepare()\n        if (resume) player.play()\n        MeloXPlaybackModePreferences.setShuffle(context, enabled)\n    }\n\n    internal fun mediaItemFor(\n        song: SearchSong,\n        quality: MusicQuality = MusicQualityRuntime.selected,\n        queueOrigin: String = QUEUE_ORIGIN_BASE,\n        originalIndex: Int = QUEUE_ORIGINAL_INDEX_UNSET,\n        artworkOverride: Uri? = null,\n    ): MediaItem = song.toMediaItem(quality, queueOrigin, originalIndex, artworkOverride)\n\n    private fun MediaItem.queueOrigin(): String =\n        mediaMetadata.extras?.getString(QUEUE_ORIGIN_KEY) ?: QUEUE_ORIGIN_BASE\n\n    private fun SearchSong.toMediaItem(\n        quality: MusicQuality,\n        queueOrigin: String,\n        originalIndex: Int = QUEUE_ORIGINAL_INDEX_UNSET,\n        artworkOverride: Uri? = null,\n    ): MediaItem {\n        val metadata = MediaMetadata.Builder()\n'''
if old not in s: raise SystemExit('mediaItemFor block not found')
s = s.replace(old, new, 1)
s = s.replace('            .setExtras(Bundle().apply { putString(QUEUE_ORIGIN_KEY, queueOrigin) })\n', '            .setExtras(Bundle().apply {\n                putString(QUEUE_ORIGIN_KEY, queueOrigin)\n                putInt(QUEUE_ORIGINAL_INDEX_KEY, originalIndex)\n            })\n')
s = s.replace('                artworkUrl\n                    ?.takeIf(String::isNotBlank)\n                    ?.let { setArtworkUri(Uri.parse(it)) }\n', '                artworkOverride?.let(::setArtworkUri) ?: artworkUrl\n                    ?.takeIf(String::isNotBlank)\n                    ?.let { setArtworkUri(Uri.parse(it)) }\n')
save(path, s)

# 3) UI state: custom shuffle + system media volume, never AutoMix gain.
path = 'android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXPlayerUi.kt'
p, s = load(path)
s = s.replace('import android.content.Context\n', 'import android.content.Context\nimport android.media.AudioManager\n')
s = s.replace('    var shuffleEnabled by mutableStateOf(false)\n', '    var shuffleEnabled by mutableStateOf(MeloXPlaybackModePreferences.shuffle(appContext))\n')
s = s.replace('        shuffleEnabled = player.shuffleModeEnabled\n        volume = player.volume\n', '''        shuffleEnabled = MeloXPlaybackModePreferences.shuffle(appContext)\n        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager\n        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)\n        volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()\n''')
s = s.replace('''    fun toggleShuffle() {\n        controller?.let { player ->\n            player.shuffleModeEnabled = !player.shuffleModeEnabled\n            refresh()\n        }\n    }\n''', '''    fun toggleShuffle() {\n        controller?.let { player ->\n            val next = !shuffleEnabled\n            PlaybackCommands.setExplicitShuffle(appContext, player, next)\n            shuffleEnabled = next\n            refresh()\n        }\n    }\n''')
s = s.replace('''    fun changeVolume(value: Float) {\n        controller?.let { player ->\n            player.volume = value.coerceIn(0f, 1f)\n            volume = player.volume\n        }\n    }\n''', '''    fun changeVolume(value: Float) {\n        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager\n        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)\n        val target = (value.coerceIn(0f, 1f) * maxVolume).toInt().coerceIn(0, maxVolume)\n        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)\n        volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()\n    }\n''')
save(path, s)

# 4) AutoMix: complete before outgoing auto-advances, never cancel active handoff.
path = 'android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXPlaybackService.kt'
p, s = load(path)
s = s.replace('    private var mixStartedAt = 0L\n    private var mixBaseVolume = 1f\n', '    private var mixStartedAt = 0L\n    private var mixDurationMs = 0L\n    private var mixBaseVolume = 1f\n')
s = s.replace('''        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {\n            recommendationSeed = null\n            cancelPreparedMix()\n        }\n''', '''        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {\n            recommendationSeed = null\n            // During an active crossfade the outgoing deck may report an end/transition\n            // before the 100 ms monitor tick performs the handoff. Do not tear down the\n            // prepared incoming deck in that tiny window; the next tick promotes it at\n            // the already-heard position instead of replaying the overlap from zero.\n            if (mixStartedAt == 0L) cancelPreparedMix()\n        }\n        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {\n            if (mixStartedAt == 0L) cancelPreparedMix()\n        }\n''')
old = '''        val sourceId = active.currentMediaItem?.mediaId ?: return\n        if (incomingPlayer == null && remaining <= AUTOMIX_PRELOAD_MS) {\n            prepareIncoming(active, sourceId)\n        }\n        val incoming = incomingPlayer ?: return\n        if (preparedMixSourceId != sourceId) {\n            cancelPreparedMix(); return\n        }\n        if (mixStartedAt == 0L && incoming.playbackState == Player.STATE_READY && remaining <= AUTOMIX_DURATION_MS) {\n            mixBaseVolume = active.volume\n            incoming.volume = 0f\n            incoming.play()\n            mixStartedAt = SystemClock.elapsedRealtime()\n        }\n        if (mixStartedAt > 0L) {\n            val progress = ((SystemClock.elapsedRealtime() - mixStartedAt).toFloat() / AUTOMIX_DURATION_MS.toFloat()).coerceIn(0f, 1f)\n            active.volume = mixBaseVolume * (1f - progress)\n            incoming.volume = mixBaseVolume * progress\n            if (progress >= 1f) completeAutoMix(active, incoming)\n        }\n'''
new = '''        val sourceId = active.currentMediaItem?.mediaId ?: return\n        if (incomingPlayer == null && remaining <= AUTOMIX_PRELOAD_MS) {\n            prepareIncoming(active, sourceId)\n        }\n        val incoming = incomingPlayer ?: return\n        if (preparedMixSourceId != sourceId) {\n            if (mixStartedAt > 0L) {\n                completeAutoMix(active, incoming)\n            } else {\n                cancelPreparedMix()\n            }\n            return\n        }\n        if (mixStartedAt == 0L && incoming.playbackState == Player.STATE_READY && remaining <= AUTOMIX_DURATION_MS) {\n            mixBaseVolume = active.volume.coerceIn(0f, 1f)\n            mixDurationMs = minOf(\n                AUTOMIX_DURATION_MS,\n                (remaining - AUTOMIX_HANDOFF_GUARD_MS).coerceAtLeast(MIN_AUTOMIX_DURATION_MS),\n            )\n            incoming.volume = 0f\n            incoming.play()\n            mixStartedAt = SystemClock.elapsedRealtime()\n        }\n        if (mixStartedAt > 0L) {\n            val elapsed = SystemClock.elapsedRealtime() - mixStartedAt\n            val durationMs = mixDurationMs.coerceAtLeast(1L)\n            val progress = (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)\n            active.volume = mixBaseVolume * (1f - progress)\n            incoming.volume = mixBaseVolume * progress\n            if (progress >= 1f || remaining <= AUTOMIX_HANDOFF_GUARD_MS) {\n                completeAutoMix(active, incoming)\n            }\n        }\n'''
if old not in s: raise SystemExit('automix loop block not found')
s = s.replace(old, new, 1)
s = s.replace('''    private fun completeAutoMix(old: ExoPlayer, incoming: ExoPlayer) {\n        incoming.volume = mixBaseVolume\n''', '''    private fun completeAutoMix(old: ExoPlayer, incoming: ExoPlayer) {\n        val heardPosition = (SystemClock.elapsedRealtime() - mixStartedAt).coerceAtLeast(0L)\n        if (heardPosition > 0L && kotlin.math.abs(incoming.currentPosition - heardPosition) > 300L) {\n            incoming.seekTo(heardPosition)\n        }\n        incoming.volume = mixBaseVolume\n''')
s = s.replace('''        mixStartedAt = 0L\n        old.removeListener(playerListener)\n''', '''        mixStartedAt = 0L\n        mixDurationMs = 0L\n        old.removeListener(playerListener)\n''', 1)
s = s.replace('''        mixStartedAt = 0L\n    }\n\n    override fun onGetSession''', '''        mixStartedAt = 0L\n        mixDurationMs = 0L\n    }\n\n    override fun onGetSession''', 1)
s = s.replace('''        const val AUTOMIX_PRELOAD_MS = 10_000L\n        const val AUTOMIX_DURATION_MS = 6_000L\n''', '''        const val AUTOMIX_PRELOAD_MS = 10_000L\n        const val AUTOMIX_DURATION_MS = 6_000L\n        const val MIN_AUTOMIX_DURATION_MS = 1_500L\n        const val AUTOMIX_HANDOFF_GUARD_MS = 350L\n''')
save(path, s)

# 5) Download bundle: cover + optional lyrics + local lookup.
path = 'android/app/src/main/kotlin/com/lladlam/melox/core/download/MeloXDownloadStore.kt'
p, s = load(path)
s = s.replace('import com.lladlam.melox.core.model.SearchSong\n', 'import com.lladlam.melox.core.model.SearchSong\nimport com.lladlam.melox.core.lyrics.LyricLine\nimport com.lladlam.melox.core.lyrics.LyricSyllable\nimport com.lladlam.melox.core.lyrics.LyricsDocument\nimport com.lladlam.melox.core.network.NeteaseSearchClient\nimport com.lladlam.melox.ui.settings.MeloXSettingsPreferences\n')
s = s.replace('''    val format: String?,\n    val downloadedAt: Long,\n)\n''', '''    val format: String?,\n    val downloadedAt: Long,\n    val artworkFileName: String? = null,\n    val lyricsFileName: String? = null,\n)\n''')
s = s.replace('''    private val qualityClient = NeteaseQualityClient(\n        cookieProvider = { NeteaseSessionStore.readCookie(app) },\n        httpClient = http,\n    )\n''', '''    private val qualityClient = NeteaseQualityClient(\n        cookieProvider = { NeteaseSessionStore.readCookie(app) },\n        httpClient = http,\n    )\n    private val searchClient = NeteaseSearchClient(\n        httpClient = http,\n        cookieProvider = { NeteaseSessionStore.readCookie(app) },\n    )\n''')
s = s.replace('''    fun localPlaybackUri(songId: Long): Uri? {\n''', '''    fun localArtworkUri(songId: Long): Uri? {\n        val record = downloads.firstOrNull { it.song.id == songId } ?: return null\n        val fileName = record.artworkFileName ?: return null\n        val file = File(directory, fileName)\n        return file.takeIf(File::isFile)?.let(Uri::fromFile)\n    }\n\n    fun localLyrics(songId: Long): LyricsDocument? {\n        val record = downloads.firstOrNull { it.song.id == songId } ?: return null\n        val fileName = record.lyricsFileName ?: return null\n        val file = File(directory, fileName)\n        if (!file.isFile) return null\n        return runCatching { decodeLyrics(JSONObject(file.readText())) }.getOrNull()\n    }\n\n    fun localPlaybackUri(songId: Long): Uri? {\n''')
s = s.replace('''        File(directory, record.fileName).delete()\n        saveIndex()\n''', '''        File(directory, record.fileName).delete()\n        record.artworkFileName?.let { File(directory, it).delete() }\n        record.lyricsFileName?.let { File(directory, it).delete() }\n        saveIndex()\n''')
old = '''            val record = MeloXDownloadedSong(\n                song = song,\n                quality = source.quality ?: quality,\n                fileName = finalName,\n                byteCount = finalFile.length(),\n                bitrate = source.bitrate,\n                format = source.format,\n                downloadedAt = System.currentTimeMillis(),\n            )\n'''
new = '''            val artworkFileName = downloadArtworkIfAvailable(song)\n            val lyricsFileName = if (MeloXSettingsPreferences.boolean(app, "download_lyrics", true)) {\n                downloadLyricsIfEnabled(song)\n            } else null\n            val record = MeloXDownloadedSong(\n                song = song,\n                quality = source.quality ?: quality,\n                fileName = finalName,\n                byteCount = finalFile.length(),\n                bitrate = source.bitrate,\n                format = source.format,\n                downloadedAt = System.currentTimeMillis(),\n                artworkFileName = artworkFileName,\n                lyricsFileName = lyricsFileName,\n            )\n'''
if old not in s: raise SystemExit('download record block not found')
s = s.replace(old, new, 1)
s = s.replace('''                downloadedAt = value.optLong("downloadedAt", 0L),\n            )\n''', '''                downloadedAt = value.optLong("downloadedAt", 0L),\n                artworkFileName = value.optString("artworkFileName").takeIf(String::isNotBlank),\n                lyricsFileName = value.optString("lyricsFileName").takeIf(String::isNotBlank),\n            )\n''')
s = s.replace('''                    .put("downloadedAt", record.downloadedAt),\n''', '''                    .put("downloadedAt", record.downloadedAt)\n                    .put("artworkFileName", record.artworkFileName ?: "")\n                    .put("lyricsFileName", record.lyricsFileName ?: ""),\n''')
insert_before = '''    private fun loadIndex() {\n'''
helpers = r'''    private suspend fun downloadArtworkIfAvailable(song: SearchSong): String? {
        val url = song.artworkUrl?.takeIf(String::isNotBlank) ?: return null
        val fileName = "${song.id}.cover"
        val target = File(directory, fileName)
        if (target.isFile && target.length() > 0L) return fileName
        return runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("封面下载失败：HTTP ${response.code}")
                    target.outputStream().buffered().use { output -> response.body.byteStream().use { it.copyTo(output) } }
                }
            }
            fileName.takeIf { target.length() > 0L }
        }.getOrNull()
    }

    private suspend fun downloadLyricsIfEnabled(song: SearchSong): String? {
        val fileName = "${song.id}.lyrics.json"
        val target = File(directory, fileName)
        return runCatching {
            val document = searchClient.lyrics(song.id)
            if (document.lines.isEmpty()) return@runCatching null
            withContext(Dispatchers.IO) { target.writeText(encodeLyrics(document).toString()) }
            fileName
        }.getOrNull()
    }

    private fun encodeLyrics(document: LyricsDocument): JSONObject = JSONObject().put(
        "lines",
        JSONArray().apply {
            document.lines.forEach { line ->
                put(JSONObject()
                    .put("timeMs", line.timeMs)
                    .put("durationMs", line.durationMs ?: JSONObject.NULL)
                    .put("text", line.text)
                    .put("translation", line.translation ?: "")
                    .put("romanization", line.romanization ?: "")
                    .put("syllables", JSONArray().apply {
                        line.syllables.forEach { syllable ->
                            put(JSONObject()
                                .put("text", syllable.text)
                                .put("startTimeMs", syllable.startTimeMs)
                                .put("endTimeMs", syllable.endTimeMs))
                        }
                    }))
            }
        },
    )

    private fun decodeLyrics(value: JSONObject): LyricsDocument {
        val array = value.optJSONArray("lines") ?: JSONArray()
        val lines = buildList {
            for (index in 0 until array.length()) {
                val line = array.optJSONObject(index) ?: continue
                val syllablesArray = line.optJSONArray("syllables") ?: JSONArray()
                val syllables = buildList {
                    for (s in 0 until syllablesArray.length()) {
                        val item = syllablesArray.optJSONObject(s) ?: continue
                        add(LyricSyllable(
                            text = item.optString("text"),
                            startTimeMs = item.optLong("startTimeMs"),
                            endTimeMs = item.optLong("endTimeMs"),
                        ))
                    }
                }
                add(LyricLine(
                    timeMs = line.optLong("timeMs"),
                    durationMs = line.optLong("durationMs", -1L).takeIf { it >= 0L },
                    text = line.optString("text"),
                    syllables = syllables,
                    translation = line.optString("translation").takeIf(String::isNotBlank),
                    romanization = line.optString("romanization").takeIf(String::isNotBlank),
                ))
            }
        }
        return LyricsDocument(lines)
    }

'''
if insert_before not in s: raise SystemExit('loadIndex marker not found')
s = s.replace(insert_before, helpers + insert_before, 1)
save(path, s)

# 6) Settings: lyrics download is user-controllable, default on.
path = 'android/app/src/main/kotlin/com/lladlam/melox/ui/settings/MeloXSettingsPreferences.kt'
p, s = load(path)
s = s.replace('''    var showLyricRomanization by mutableStateOf(true)\n        internal set\n''', '''    var showLyricRomanization by mutableStateOf(true)\n        internal set\n    var downloadLyricsEnabled by mutableStateOf(true)\n        internal set\n''')
s = s.replace('''        showLyricRomanization = MeloXSettingsPreferences.boolean(app, "lyrics_romanization", true)\n''', '''        showLyricRomanization = MeloXSettingsPreferences.boolean(app, "lyrics_romanization", true)\n        downloadLyricsEnabled = MeloXSettingsPreferences.boolean(app, "download_lyrics", true)\n''')
s = s.replace('''            "lyrics_romanization" -> MeloXSettingsRuntime.showLyricRomanization = value\n''', '''            "lyrics_romanization" -> MeloXSettingsRuntime.showLyricRomanization = value\n            "download_lyrics" -> MeloXSettingsRuntime.downloadLyricsEnabled = value\n''')
save(path, s)

path = 'android/app/src/main/kotlin/com/lladlam/melox/ui/settings/SettingsScreen.kt'
rep(path,
'''    SettingsToggleRow(context, "按播放次数自动缓存", "downloads_auto_cache", false, "与上游 DownloadStore 对齐的自动缓存偏好；手动下载始终可用。")\n''',
'''    SettingsToggleRow(context, "按播放次数自动缓存", "downloads_auto_cache", false, "与上游 DownloadStore 对齐的自动缓存偏好；手动下载始终可用。")\n    SettingsToggleRow(context, "下载歌词", "download_lyrics", true, "下载歌曲时同时保存歌词；默认开启，可在此关闭。封面始终随歌曲保存。")\n''')

# 7) Lyrics prefer downloaded document, remove pre-whitening hack, stop layout-measure re-scroll jitter.
path = 'android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSLyricsPanel.kt'
p, s = load(path)
s = s.replace('import com.lladlam.melox.core.lyrics.LyricsDocument\n', 'import com.lladlam.melox.core.lyrics.LyricsDocument\nimport com.lladlam.melox.core.download.MeloXDownloadStore\n')
s = s.replace('''        runCatching { client.lyrics(songId) }\n            .onSuccess { lyrics = it }\n''', '''        val downloaded = MeloXDownloadStore.get(context).localLyrics(songId)\n        if (downloaded != null) {\n            lyrics = downloaded\n        } else runCatching { client.lyrics(songId) }\n            .onSuccess { lyrics = it }\n''')
# Remove layoutRevision from focus effect keys to avoid repeated scroll corrections as wrapped CJK rows report size.
s = s.replace('''            layoutRevision,\n            document,\n''', '''            document,\n''')
# Remove the incomingLead=max(...) pre-white bridge; upstream snapshots presentation progress and eases from it.
pattern = re.compile(r'''\s*// The incoming line starts the 120 ms colour handoff before.*?val effectiveFocus = max\(fp, incomingLead\)\n''', re.S)
s2, n = pattern.subn('\n                        val effectiveFocus = fp\n', s, count=1)
if n != 1: raise SystemExit(f'lyrics incomingLead block not found: {n}')
s = s2
# Smooth geometry correction instead of hard scrollTo.
s = s.replace('''                    automaticScroll = true\n                    scrollState.scrollTo(targetScroll)\n                    automaticScroll = false\n''', '''                    automaticScroll = true\n                    scrollState.animateScrollTo(\n                        targetScroll,\n                        tween(120, easing = SourceSmoothStepEasing),\n                    )\n                    automaticScroll = false\n''', 1)
save(path, s)

# 8) MiniPlayer compact hit target: never overlap invisible Next with Play.
path = 'android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSMiniPlayer.kt'
p, s = load(path)
s = s.replace('import androidx.compose.ui.text.font.FontWeight\n', 'import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.zIndex\n')
s = s.replace('val controlStageWidth = lerpDp(82.dp, 36.dp, smoothStep(compact, 0.08f, 0.84f))', 'val controlStageWidth = lerpDp(82.dp, 44.dp, smoothStep(compact, 0.08f, 0.84f))')
s = s.replace('''                modifier = Modifier\n                    .width(controlStageWidth)\n                    .height(40.dp),\n''', '''                modifier = Modifier\n                    .width(controlStageWidth)\n                    .height(44.dp)\n                    .zIndex(8f),\n''')
s = s.replace('''                    modifier = Modifier\n                        .align(Alignment.CenterStart),\n''', '''                    modifier = Modifier\n                        .align(if (compact > 0.55f) Alignment.Center else Alignment.CenterStart)\n                        .zIndex(10f),\n''', 1)
old = '''                MiniVectorButton(\n                    kind = MiniGlyph.Forward,\n                    enabled = state.hasNext || state.repeatMode != 0,\n                    onClick = state::next,\n                    modifier = Modifier\n                        .align(Alignment.CenterEnd),\n                    visualAlpha = miniChromeAlpha * compactNextAlpha,\n                )\n'''
new = '''                if (compactNextAlpha > 0.05f) {\n                    MiniVectorButton(\n                        kind = MiniGlyph.Forward,\n                        enabled = state.hasNext || state.repeatMode != 0,\n                        onClick = state::next,\n                        modifier = Modifier\n                            .align(Alignment.CenterEnd)\n                            .zIndex(9f),\n                        visualAlpha = miniChromeAlpha * compactNextAlpha,\n                    )\n                }\n'''
if old not in s: raise SystemExit('mini next block not found')
s = s.replace(old, new, 1)
s = s.replace('.size(36.dp)\n            .clip(CircleShape)', '.size(44.dp)\n            .clip(CircleShape)', 1)
save(path, s)

# 9) Blur the whole controls region on Lyrics/Queue, not only individual controls.
path = 'android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSNowPlayingScene.kt'
p, s = load(path)
old = '''            val controlsShape = RoundedCornerShape(28.dp)\n            val controlsSurface = if (page == MeloXNowPlayingPage.Lyrics) {\n                Modifier\n                    .fillMaxWidth()\n                    .clip(controlsShape)\n                    .meloXBackdropBlur(\n                        shape = controlsShape,\n                        blurRadius = 20.dp,\n                        surfaceColor = Color.Black.copy(alpha = .10f),\n                    )\n            } else {\n                Modifier.fillMaxWidth()\n            }\n'''
new = '''            val controlsShape = RoundedCornerShape(28.dp)\n            val controlsSurface = if (page != MeloXNowPlayingPage.Artwork) {\n                Modifier\n                    .fillMaxWidth()\n                    .height(MeloXNowPlayingControlsHeight.dp)\n                    .clip(controlsShape)\n                    .meloXBackdropBlur(\n                        shape = controlsShape,\n                        blurRadius = 24.dp,\n                        surfaceColor = Color.Black.copy(alpha = .08f),\n                    )\n            } else {\n                Modifier.fillMaxWidth()\n            }\n'''
if old not in s: raise SystemExit('controlsSurface block not found')
s = s.replace(old, new, 1)
save(path, s)

# Remove inspector workflow after patch; build workflow remains the only CI surface.
for temp in ['.github/workflows/_inspect_player_fix.yml', 'tools/_repair_player_pipeline.py', '.github/workflows/_repair_player_pipeline.yml']:
    p = ROOT / temp
    if p.exists(): p.unlink()

print('player pipeline repair applied')
