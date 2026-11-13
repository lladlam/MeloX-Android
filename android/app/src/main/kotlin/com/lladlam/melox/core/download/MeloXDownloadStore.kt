package com.lladlam.melox.core.download

import android.content.ContentValues
import android.content.Context
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.NeteaseQualityClient
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricSyllable
import com.lladlam.melox.core.lyrics.LyricAccompaniment
import com.lladlam.melox.core.lyrics.LyricAgent
import com.lladlam.melox.core.lyrics.LyricAgentAlignment
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class MeloXDownloadPlaylistRef(
    val id: Long,
    val name: String,
    val artworkUrl: String? = null,
)

data class MeloXDownloadedSong(
    val song: SearchSong,
    val quality: MusicQuality,
    val fileName: String,
    val byteCount: Long,
    val bitrate: Int?,
    val format: String?,
    val downloadedAt: Long,
    val artworkFileName: String? = null,
    val lyricsFileName: String? = null,
    val sourcePlaylists: List<MeloXDownloadPlaylistRef> = emptyList(),
)

data class MeloXDownloadedPlaylist(
    val playlist: MeloXDownloadPlaylistRef,
    val songs: List<MeloXDownloadedSong>,
)

data class MeloXStorageRepairResult(
    val missingRecordsRemoved: Int,
    val orphanFilesRemoved: Int,
    val recoveredBytes: Long,
)

data class MeloXActiveDownload(
    val song: SearchSong,
    val quality: MusicQuality,
    val receivedByteCount: Long = 0L,
    val expectedByteCount: Long? = null,
    val bytesPerSecond: Long = 0L,
) {
    val fractionCompleted: Float?
        get() = expectedByteCount?.takeIf { it > 0L }
            ?.let { (receivedByteCount.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat() }
}

/**
 * Android counterpart of upstream DownloadStore.
 *
 * Audio, artwork and optional lyrics live in app-private storage. Artwork is
 * exposed through FileProvider as a content:// URI so both Coil and Media3's
 * notification bitmap loader can use it without network access.
 */
class MeloXDownloadStore private constructor(private val context: Context) {
    private val app = context.applicationContext
    private val directory = File(app.filesDir, "melox_downloads").apply { mkdirs() }
    private val indexFile = File(directory, "index.json")
    private val http = com.lladlam.melox.core.network.MeloXHttpClient.shared
    private val qualityClient = NeteaseQualityClient(
        cookieProvider = { NeteaseSessionStore.readCookie(app) },
        httpClient = http,
    )
    private val searchClient = NeteaseSearchClient(
        httpClient = http,
        cookieProvider = { NeteaseSessionStore.readCookie(app) },
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val storageScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storageCommands = Channel<StorageCommand>(Channel.UNLIMITED)
    private val transferSlots = Semaphore(3)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val pendingPlaylistRefs = ConcurrentHashMap<Long, MutableList<MeloXDownloadPlaylistRef>>()
    private val downloadsBySongId = ConcurrentHashMap<Long, MeloXDownloadedSong>()

    val downloads = mutableStateListOf<MeloXDownloadedSong>()
    val activeDownloads = mutableStateMapOf<Long, MeloXActiveDownload>()
    /** Paused downloads keep their song/quality so the UI can offer a resume action. */
    val pausedDownloads = mutableStateMapOf<Long, MeloXActiveDownload>()
    var errorMessage: String? = null
        private set

    init {
        storageScope.launch {
            for (command in storageCommands) applyStorageCommand(command)
        }
        scope.launch {
            val restored = withContext(Dispatchers.IO) { readIndex() }
            restored.forEach { record ->
                if (downloadsBySongId.putIfAbsent(record.song.id, record) == null) downloads += record
            }
        }
    }

    val downloadedSongs: List<SearchSong>
        get() = downloads.map { it.song }

    val totalByteCount: Long
        get() = downloads.sumOf { it.byteCount }

    val aggregateDownloadBytesPerSecond: Long
        get() = activeDownloads.values.sumOf { it.bytesPerSecond }

    val downloadedPlaylists: List<MeloXDownloadedPlaylist>
        get() {
            val refs = linkedMapOf<Long, MeloXDownloadPlaylistRef>()
            downloads.forEach { record ->
                record.sourcePlaylists.forEach { ref -> refs.putIfAbsent(ref.id, ref) }
            }
            return refs.values.map { ref ->
                MeloXDownloadedPlaylist(
                    playlist = ref,
                    songs = downloads.filter { record ->
                        record.sourcePlaylists.any { it.id == ref.id }
                    },
                )
            }
        }

    fun contains(songId: Long): Boolean = downloadsBySongId.containsKey(songId)
    fun isDownloading(songId: Long): Boolean = activeDownloads.containsKey(songId)
    fun isPaused(songId: Long): Boolean = pausedDownloads.containsKey(songId)
    fun recordFor(songId: Long): MeloXDownloadedSong? = downloadsBySongId[songId]
    fun downloadedQuality(songId: Long): MusicQuality? = recordFor(songId)?.quality

    /** Counts actual player transitions and starts the same native download pipeline at the configured threshold. */
    fun recordPlayback(item: MediaItem) {
        if (!MeloXSettingsPreferences.boolean(app, "downloads_auto_cache", false)) return
        val songId = item.mediaId.toLongOrNull()?.takeIf { it > 0L } ?: return
        if (contains(songId) || isDownloading(songId)) return
        val counts = app.getSharedPreferences("melox_auto_cache_counts", Context.MODE_PRIVATE)
        val key = songId.toString()
        val count = counts.getInt(key, 0) + 1
        counts.edit().putInt(key, count).apply()
        val threshold = MeloXSettingsPreferences.int(app, "downloads_auto_cache_threshold", 3).coerceIn(2, 20)
        if (count < threshold) return
        val metadata = item.mediaMetadata
        val song = SearchSong(
            id = songId,
            name = metadata.title?.toString().orEmpty().ifBlank { "未知歌曲" },
            artists = metadata.artist?.toString().orEmpty().ifBlank { "未知歌手" },
            album = metadata.albumTitle?.toString().orEmpty(),
            albumId = item.mediaMetadata.extras?.getLong("melox.system.original_album_id")?.takeIf { it > 0L },
            artistId = item.mediaMetadata.extras?.getLong("melox.system.original_artist_id")?.takeIf { it > 0L },
            artworkUrl = metadata.artworkUri?.toString(),
            durationMs = 0L,
        )
        val quality = runCatching {
            MusicQuality.valueOf(MeloXSettingsPreferences.string(app, "downloads_auto_cache_quality", MusicQuality.Standard.name))
        }.getOrDefault(MusicQuality.Standard)
        start(song, quality)
    }

    fun resetAutomaticCacheHistory() {
        app.getSharedPreferences("melox_auto_cache_counts", Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun downloadedSongsForPlaylist(playlistId: Long): List<SearchSong> =
        downloadedPlaylists.firstOrNull { it.playlist.id == playlistId }
            ?.songs
            ?.map { it.song }
            .orEmpty()

    /** Local audio file used by the LAN audio proxy; null when not downloaded. */
    internal fun localAudioFile(songId: Long): File? {
        val record = recordFor(songId) ?: return null
        return File(directory, record.fileName).takeIf(File::isFile)
    }

    fun localArtworkUri(songId: Long): Uri? {
        val record = recordFor(songId) ?: return null
        val fileName = record.artworkFileName ?: return null
        val file = File(directory, fileName)
        return file.takeIf(File::isFile)?.let(::contentUriFor)
    }

    fun localPlaylistArtworkUri(playlistId: Long): Uri? {
        val file = File(directory, playlistArtworkFileName(playlistId))
        return file.takeIf(File::isFile)?.let(::contentUriFor)
    }

    fun localLyrics(songId: Long): LyricsDocument? {
        val record = recordFor(songId) ?: return null
        val fileName = record.lyricsFileName ?: return null
        val file = File(directory, fileName)
        if (!file.isFile) return null
        return runCatching { decodeLyrics(JSONObject(file.readText())) }.getOrNull()
    }

    fun localPlaybackUri(songId: Long): Uri? {
        val record = recordFor(songId) ?: return null
        val file = File(directory, record.fileName)
        if (!file.isFile) {
            scope.launch { removeMissingRecord(songId) }
            return null
        }
        return Uri.fromFile(file)
    }

    fun start(
        song: SearchSong,
        quality: MusicQuality,
        sourcePlaylist: MeloXDownloadPlaylistRef? = null,
    ) {
        sourcePlaylist?.let { rememberPendingPlaylist(song.id, it) }
        val existing = recordFor(song.id)
        if (existing != null) {
            sourcePlaylist?.let { associatePlaylist(existing, it) }
            pendingPlaylistRefs.remove(song.id)
            return
        }
        if (isDownloading(song.id) || jobs.containsKey(song.id)) return

        errorMessage = null
        pausedDownloads.remove(song.id)
        activeDownloads[song.id] = MeloXActiveDownload(song, quality)
        lateinit var job: Job
        job = scope.launch {
            transferSlots.withPermit {
                download(song, quality, job)
            }
        }
        jobs[song.id] = job
    }

    /**
     * Stops the current transfer but keeps a paused record so the downloads
     * screen can offer resume. Restarting re-downloads from the beginning
     * because partial files are not resumable across sessions.
     */
    fun pause(songId: Long) {
        val job = jobs[songId] ?: return
        val active = activeDownloads[songId] ?: return
        pausedDownloads[songId] = active.copy(bytesPerSecond = 0L)
        activeDownloads.remove(songId)
        pendingPlaylistRefs.remove(songId)
        // Keep the job slot until its own finally cleanup finishes. Otherwise an
        // immediate resume writes the same .part while the cancelled task deletes it.
        job.cancel()
    }

    fun resume(songId: Long) {
        val paused = pausedDownloads.remove(songId) ?: return
        start(paused.song, paused.quality)
    }

    fun cancel(songId: Long) {
        jobs[songId]?.cancel()
        activeDownloads.remove(songId)
        pausedDownloads.remove(songId)
        pendingPlaylistRefs.remove(songId)
        // The owning coroutine deletes its temporary file before releasing the slot.
    }

    fun remove(songId: Long) = removeMany(setOf(songId))

    fun removeMany(songIds: Set<Long>) {
        if (songIds.isEmpty()) return
        songIds.forEach(::cancel)
        val removed = downloads.filter { it.song.id in songIds }
        val fileNames = removed.flatMap { record ->
            listOfNotNull(record.fileName, record.artworkFileName, record.lyricsFileName)
        }.toSet()
        downloads.removeAll { it.song.id in songIds }
        songIds.forEach(downloadsBySongId::remove)
        enqueuePersist(fileNamesToDelete = fileNames, cleanupPlaylistArtwork = true)
    }

    fun removeAll() {
        jobs.keys.toList().forEach(::cancel)
        pausedDownloads.clear()
        pendingPlaylistRefs.clear()
        downloads.clear()
        downloadsBySongId.clear()
        storageCommands.trySend(StorageCommand.Reset)
    }

    fun exportToMusicLibrary(songIds: Set<Long>, onComplete: (Result<Int>) -> Unit = {}) {
        if (songIds.isEmpty()) return
        storageScope.launch {
            val result = runCatching {
                songIds.mapNotNull(downloadsBySongId::get).count { record ->
                    exportRecordToMediaStore(record)
                    true
                }
            }
            withContext(Dispatchers.Main) {
                result.exceptionOrNull()?.let { errorMessage = it.message ?: "导出到系统音乐库失败" }
                onComplete(result)
            }
        }
    }

    fun repairStorage(onComplete: (Result<MeloXStorageRepairResult>) -> Unit = {}) {
        storageScope.launch {
            val result = runCatching {
                val snapshot = downloadsBySongId.values.toList()
                val valid = snapshot.mapNotNull { record ->
                    val audio = File(directory, record.fileName)
                    record.takeIf { audio.isFile && audio.length() > 0L }
                        ?.copy(byteCount = audio.length())
                }
                val usedNames = valid.flatMap { record ->
                    listOfNotNull(record.fileName, record.artworkFileName, record.lyricsFileName)
                }.toMutableSet().apply {
                    add(indexFile.name)
                    valid.flatMap(MeloXDownloadedSong::sourcePlaylists)
                        .forEach { add(playlistArtworkFileName(it.id)) }
                }
                var orphanCount = 0
                var recoveredBytes = 0L
                directory.listFiles().orEmpty().forEach { file ->
                    if (file.name !in usedNames) {
                        recoveredBytes += file.length()
                        if (file.delete()) orphanCount++
                    }
                }
                writeIndex(valid.sortedByDescending(MeloXDownloadedSong::downloadedAt))
                MeloXStorageRepairResult(
                    missingRecordsRemoved = snapshot.size - valid.size,
                    orphanFilesRemoved = orphanCount,
                    recoveredBytes = recoveredBytes,
                ) to valid.sortedByDescending(MeloXDownloadedSong::downloadedAt)
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { (_, valid) ->
                    downloads.clear()
                    downloads.addAll(valid)
                    downloadsBySongId.clear()
                    valid.forEach { downloadsBySongId[it.song.id] = it }
                }.onFailure { errorMessage = it.message ?: "下载存储修复失败" }
                onComplete(result.map { it.first })
            }
        }
    }

    fun clearError() {
        errorMessage = null
    }

    private fun exportRecordToMediaStore(record: MeloXDownloadedSong): Uri {
        val source = File(directory, record.fileName)
        if (!source.isFile) throw IOException("《${record.song.name}》的本地文件已丢失")
        val extension = source.extension.lowercase().ifBlank { record.format.orEmpty().lowercase() }
        val mimeType = when (extension) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "m4a", "mp4", "aac" -> "audio/mp4"
            "ogg", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            else -> "audio/*"
        }
        val safeTitle = record.song.name.replace(Regex("[\\/:*?\"<>|]"), "_").ifBlank { record.song.id.toString() }
        val displayName = "${safeTitle}_${record.song.id}.${extension.ifBlank { "audio" }}"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.TITLE, record.song.name)
            put(MediaStore.Audio.Media.ARTIST, record.song.artists)
            put(MediaStore.Audio.Media.ALBUM, record.song.album)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/MeloX")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }
        val resolver = app.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val target = resolver.insert(collection, values)
            ?: throw IOException("系统音乐库拒绝创建《${record.song.name}》")
        try {
            resolver.openOutputStream(target, "w")?.use { output ->
                source.inputStream().buffered().use { input -> input.copyTo(output) }
            } ?: throw IOException("无法写入系统音乐库")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(target, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            }
            return target
        } catch (error: Throwable) {
            resolver.delete(target, null, null)
            throw error
        }
    }

    private suspend fun download(song: SearchSong, quality: MusicQuality, job: Job) {
        val temp = File(directory, "${song.id}.part")
        try {
            val resolvedSong = runCatching {
                if (song.artworkUrl.isNullOrBlank()) searchClient.ensureArtwork(song) else song
            }.getOrDefault(song)
            activeDownloads[song.id] = activeDownloads[song.id]?.copy(song = resolvedSong)
                ?: MeloXActiveDownload(resolvedSong, quality)

            val resolvedSource = withContext(Dispatchers.IO) {
                qualityClient.downloadSourceBlocking(song.id, quality)
            }
            val downloadUrl = resolvedSource.url.takeIf { value ->
                value.isNotBlank() &&
                    !value.equals("null", ignoreCase = true) &&
                    (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true))
            } ?: throw IOException("《${song.name}》没有返回可用的 HTTP 下载地址")
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
                .header("Referer", "https://music.163.com/")
                .build()

            val result = withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("下载失败：HTTP ${response.code}")
                    val body = response.body
                    val expected = body.contentLength().takeIf { it > 0L }
                    if (expected != null && expected > MAX_AUDIO_DOWNLOAD_BYTES) {
                        throw IOException("下载文件超过大小限制")
                    }
                    val startedAt = SystemClock.elapsedRealtime()
                    temp.outputStream().buffered().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                            var received = 0L
                            var lastPublished = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                received += count
                                if (received > MAX_AUDIO_DOWNLOAD_BYTES) {
                                    throw IOException("下载文件超过大小限制")
                                }
                                output.write(buffer, 0, count)
                                if (received - lastPublished >= 256L * 1024L) {
                                    lastPublished = received
                                    val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1L)
                                    val speed = received * 1_000L / elapsed
                                    withContext(Dispatchers.Main) {
                                        activeDownloads[song.id] = MeloXActiveDownload(
                                            song = resolvedSong,
                                            quality = quality,
                                            receivedByteCount = received,
                                            expectedByteCount = expected,
                                            bytesPerSecond = speed,
                                        )
                                    }
                                }
                            }
                            Triple(received, expected, resolvedSource)
                        }
                    }
                }
            }

            val received = result.first
            if (received <= 0L) throw IOException("下载得到空文件")
            val source = result.third
            val ext = source.format
                ?.lowercase()
                ?.replace(Regex("[^a-z0-9]"), "")
                ?.takeIf(String::isNotBlank)
                ?: "audio"
            val finalName = "${song.id}.$ext"
            val finalFile = File(directory, finalName)
            withContext(Dispatchers.IO) {
                finalFile.delete()
                if (!temp.renameTo(finalFile)) {
                    temp.copyTo(finalFile, overwrite = true)
                    temp.delete()
                }
            }

            val artworkFileName = downloadArtworkIfAvailable(resolvedSong)
            val lyricsFileName = if (MeloXSettingsPreferences.boolean(app, "download_lyrics", true)) {
                downloadLyricsIfEnabled(resolvedSong)
            } else {
                null
            }
            val sourcePlaylists = pendingPlaylistRefs.remove(song.id)
                ?.distinctBy { it.id }
                .orEmpty()
            sourcePlaylists.forEach { downloadPlaylistArtworkIfAvailable(it) }

            val record = MeloXDownloadedSong(
                song = resolvedSong,
                quality = source.quality ?: quality,
                fileName = finalName,
                byteCount = finalFile.length(),
                bitrate = source.bitrate,
                format = source.format,
                downloadedAt = System.currentTimeMillis(),
                artworkFileName = artworkFileName,
                lyricsFileName = lyricsFileName,
                sourcePlaylists = sourcePlaylists,
            )
            currentCoroutineContext().ensureActive()
            if (jobs[song.id] !== job) return
            downloads.removeAll { it.song.id == song.id }
            downloads.add(0, record)
            downloadsBySongId[song.id] = record
            activeDownloads.remove(song.id)
            jobs.remove(song.id, job)
            enqueuePersist()
        } catch (_: CancellationException) {
            withContext(NonCancellable) { temp.delete() }
            activeDownloads.remove(song.id)
            jobs.remove(song.id, job)
            pendingPlaylistRefs.remove(song.id)
        } catch (error: Throwable) {
            withContext(NonCancellable) { temp.delete() }
            activeDownloads.remove(song.id)
            jobs.remove(song.id, job)
            pendingPlaylistRefs.remove(song.id)
            errorMessage = "《${song.name}》下载失败：${error.message ?: error::class.java.simpleName}"
        }
    }

    private fun rememberPendingPlaylist(songId: Long, ref: MeloXDownloadPlaylistRef) {
        pendingPlaylistRefs.compute(songId) { _, current ->
            (current ?: mutableListOf()).apply {
                if (none { it.id == ref.id }) add(ref)
            }
        }
    }

    private fun associatePlaylist(record: MeloXDownloadedSong, ref: MeloXDownloadPlaylistRef) {
        if (record.sourcePlaylists.any { it.id == ref.id }) return
        val index = downloads.indexOf(record)
        if (index < 0) return
        val updated = record.copy(sourcePlaylists = record.sourcePlaylists + ref)
        downloads[index] = updated
        downloadsBySongId[record.song.id] = updated
        enqueuePersist()
        scope.launch { downloadPlaylistArtworkIfAvailable(ref) }
    }

    private suspend fun downloadArtworkIfAvailable(song: SearchSong): String? {
        val url = song.artworkUrl?.takeIf(String::isNotBlank) ?: return null
        val fileName = "${song.id}.jpg"
        return downloadArtwork(url, fileName)
    }

    private suspend fun downloadPlaylistArtworkIfAvailable(ref: MeloXDownloadPlaylistRef): String? {
        val url = ref.artworkUrl?.takeIf(String::isNotBlank) ?: return null
        return downloadArtwork(url, playlistArtworkFileName(ref.id))
    }

    private suspend fun downloadArtwork(url: String, fileName: String): String? {
        val target = File(directory, fileName)
        if (target.isFile && target.length() > 0L) return fileName
        return runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("封面下载失败：HTTP ${response.code}")
                    target.outputStream().buffered().use { output ->
                        response.body.byteStream().use { input -> input.copyTo(output) }
                    }
                }
            }
            fileName.takeIf { target.length() > 0L }
        }.onFailure {
            target.delete()
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

    private fun encodeLyrics(document: LyricsDocument): JSONObject = JSONObject()
        .put("isUnsynchronized", document.isUnsynchronized)
        .put("pseudoTimingAllowed", document.pseudoTimingAllowed).put(
        "lines",
        JSONArray().apply {
            document.lines.forEach { line ->
                put(
                    JSONObject()
                        .put("timeMs", line.timeMs)
                        .put("durationMs", line.durationMs ?: JSONObject.NULL)
                        .put("text", line.text)
                        .put("translation", line.translation ?: "")
                        .put("romanization", line.romanization ?: "")
                        .put("agentId", line.agent?.id ?: "")
                        .put("agentName", line.agent?.name ?: "")
                        .put("agentAlignment", line.agent?.alignment?.name ?: "")
                        .put(
                            "syllables",
                            JSONArray().apply {
                                line.syllables.forEach { syllable ->
                                    put(
                                        JSONObject()
                                            .put("text", syllable.text)
                                            .put("startTimeMs", syllable.startTimeMs)
                                            .put("endTimeMs", syllable.endTimeMs),
                                    )
                                }
                            },
                        )
                        .put(
                            "romanizationSyllables",
                            JSONArray().apply {
                                line.romanizationSyllables.forEach { syllable ->
                                    put(
                                        JSONObject()
                                            .put("text", syllable.text)
                                            .put("startTimeMs", syllable.startTimeMs)
                                            .put("endTimeMs", syllable.endTimeMs),
                                    )
                                }
                            },
                        )
                        .put(
                            "accompaniment",
                            JSONArray().apply {
                                line.accompaniment.forEach { vocal ->
                                    put(
                                        JSONObject()
                                            .put("timeMs", vocal.timeMs)
                                            .put("durationMs", vocal.durationMs ?: JSONObject.NULL)
                                            .put("text", vocal.text)
                                            .put("agentId", vocal.agent?.id ?: "")
                                            .put("agentName", vocal.agent?.name ?: "")
                                            .put("agentAlignment", vocal.agent?.alignment?.name ?: "")
                                            .put("syllables", JSONArray().apply {
                                                vocal.syllables.forEach { syllable ->
                                                    put(JSONObject().put("text", syllable.text).put("startTimeMs", syllable.startTimeMs).put("endTimeMs", syllable.endTimeMs))
                                                }
                                            }),
                                    )
                                }
                            },
                        ),
                )
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
                        add(
                            LyricSyllable(
                                text = item.optString("text"),
                                startTimeMs = item.optLong("startTimeMs"),
                                endTimeMs = item.optLong("endTimeMs"),
                            ),
                        )
                    }
                }
                val romanizationSyllablesArray = line.optJSONArray("romanizationSyllables") ?: JSONArray()
                val romanizationSyllables = buildList {
                    for (s in 0 until romanizationSyllablesArray.length()) {
                        val item = romanizationSyllablesArray.optJSONObject(s) ?: continue
                        add(
                            LyricSyllable(
                                text = item.optString("text"),
                                startTimeMs = item.optLong("startTimeMs"),
                                endTimeMs = item.optLong("endTimeMs"),
                            ),
                        )
                    }
                }
                fun decodeAgent(objectValue: JSONObject): LyricAgent? {
                    val id = objectValue.optString("agentId")
                    if (id.isBlank()) return null
                    val alignment = runCatching { LyricAgentAlignment.valueOf(objectValue.optString("agentAlignment")) }
                        .getOrDefault(LyricAgentAlignment.Normal)
                    return LyricAgent(id, objectValue.optString("agentName").ifBlank { id }, alignment)
                }
                val accompanimentArray = line.optJSONArray("accompaniment") ?: JSONArray()
                val accompaniment = buildList {
                    for (a in 0 until accompanimentArray.length()) {
                        val vocal = accompanimentArray.optJSONObject(a) ?: continue
                        val vocalSyllablesArray = vocal.optJSONArray("syllables") ?: JSONArray()
                        val vocalSyllables = buildList {
                            for (s in 0 until vocalSyllablesArray.length()) {
                                val item = vocalSyllablesArray.optJSONObject(s) ?: continue
                                add(LyricSyllable(item.optString("text"), item.optLong("startTimeMs"), item.optLong("endTimeMs")))
                            }
                        }
                        add(LyricAccompaniment(vocal.optLong("timeMs"), vocal.optLong("durationMs", -1L).takeIf { it >= 0L }, vocal.optString("text"), vocalSyllables, agent = decodeAgent(vocal)))
                    }
                }
                add(
                    LyricLine(
                        timeMs = line.optLong("timeMs"),
                        durationMs = line.optLong("durationMs", -1L).takeIf { it >= 0L },
                        text = line.optString("text"),
                        syllables = syllables,
                        translation = line.optString("translation").takeIf(String::isNotBlank),
                        romanization = line.optString("romanization").takeIf(String::isNotBlank),
                        romanizationSyllables = romanizationSyllables,
                        agent = decodeAgent(line),
                        accompaniment = accompaniment,
                    ),
                )
            }
        }
        return LyricsDocument(lines, isUnsynchronized = value.optBoolean("isUnsynchronized", false),
            pseudoTimingAllowed = value.optBoolean("pseudoTimingAllowed", false))
    }

    private fun readIndex(): List<MeloXDownloadedSong> {
        val raw = runCatching { indexFile.takeIf(File::isFile)?.readText() }.getOrNull().orEmpty()
        if (raw.isBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val restored = ArrayList<MeloXDownloadedSong>(array.length())
        for (i in 0 until array.length()) {
            val value = array.optJSONObject(i) ?: continue
            val song = SearchSong(
                id = value.optLong("id", -1L),
                name = value.optString("name"),
                artists = value.optString("artists"),
                album = value.optString("album"),
                artworkUrl = value.optString("artworkUrl").takeIf(String::isNotBlank),
                durationMs = value.optLong("durationMs", 0L),
            )
            if (song.id <= 0L) continue
            val fileName = value.optString("fileName")
            val file = File(directory, fileName)
            if (!file.isFile) continue
            val refsArray = value.optJSONArray("sourcePlaylists") ?: JSONArray()
            val refs = buildList {
                for (index in 0 until refsArray.length()) {
                    val ref = refsArray.optJSONObject(index) ?: continue
                    val id = ref.optLong("id", -1L)
                    if (id <= 0L) continue
                    add(
                        MeloXDownloadPlaylistRef(
                            id = id,
                            name = ref.optString("name").ifBlank { "已下载歌单" },
                            artworkUrl = ref.optString("artworkUrl").takeIf(String::isNotBlank),
                        ),
                    )
                }
            }
            restored += MeloXDownloadedSong(
                song = song,
                quality = MusicQuality.fromApiLevel(value.optString("quality")) ?: MusicQuality.Standard,
                fileName = fileName,
                byteCount = file.length(),
                bitrate = value.optInt("bitrate", -1).takeIf { it > 0 },
                format = value.optString("format").takeIf(String::isNotBlank),
                downloadedAt = value.optLong("downloadedAt", 0L),
                artworkFileName = value.optString("artworkFileName").takeIf(String::isNotBlank),
                lyricsFileName = value.optString("lyricsFileName").takeIf(String::isNotBlank),
                sourcePlaylists = refs,
            )
        }
        return restored
    }

    private fun enqueuePersist(
        fileNamesToDelete: Set<String> = emptySet(),
        cleanupPlaylistArtwork: Boolean = false,
    ) {
        storageCommands.trySend(
            StorageCommand.Persist(
                snapshot = downloads.toList(),
                fileNamesToDelete = fileNamesToDelete,
                cleanupPlaylistArtwork = cleanupPlaylistArtwork,
            ),
        )
    }

    private fun writeIndex(snapshot: List<MeloXDownloadedSong>) {
        val array = JSONArray()
        snapshot.forEach { record ->
            array.put(
                JSONObject()
                    .put("id", record.song.id)
                    .put("name", record.song.name)
                    .put("artists", record.song.artists)
                    .put("album", record.song.album)
                    .put("artworkUrl", record.song.artworkUrl ?: "")
                    .put("durationMs", record.song.durationMs)
                    .put("quality", record.quality.apiLevel)
                    .put("fileName", record.fileName)
                    .put("byteCount", record.byteCount)
                    .put("bitrate", record.bitrate ?: JSONObject.NULL)
                    .put("format", record.format ?: "")
                    .put("downloadedAt", record.downloadedAt)
                    .put("artworkFileName", record.artworkFileName ?: "")
                    .put("lyricsFileName", record.lyricsFileName ?: "")
                    .put(
                        "sourcePlaylists",
                        JSONArray().apply {
                            record.sourcePlaylists.forEach { ref ->
                                put(
                                    JSONObject()
                                        .put("id", ref.id)
                                        .put("name", ref.name)
                                        .put("artworkUrl", ref.artworkUrl ?: ""),
                                )
                            }
                        },
                    ),
            )
        }
        runCatching {
            directory.mkdirs()
            val temporary = File(directory, "index.json.tmp")
            temporary.writeText(array.toString())
            if (!temporary.renameTo(indexFile)) {
                temporary.copyTo(indexFile, overwrite = true)
                temporary.delete()
            }
        }
    }

    private fun removeMissingRecord(songId: Long) {
        downloads.removeAll { it.song.id == songId }
        downloadsBySongId.remove(songId)
        enqueuePersist(cleanupPlaylistArtwork = true)
    }

    private fun cleanupUnusedPlaylistArtwork(snapshot: List<MeloXDownloadedSong>) {
        val used = snapshot.flatMap { it.sourcePlaylists }.map { it.id }.toSet()
        directory.listFiles()?.forEach { file ->
            if (!file.name.startsWith("playlist_") || !file.name.endsWith(".jpg")) return@forEach
            val id = file.name.removePrefix("playlist_").removeSuffix(".jpg").toLongOrNull()
            if (id != null && id !in used) file.delete()
        }
    }

    private fun applyStorageCommand(command: StorageCommand) {
        when (command) {
            is StorageCommand.Delete -> command.fileNames.forEach { File(directory, it).delete() }
            is StorageCommand.Persist -> {
                command.fileNamesToDelete.forEach { File(directory, it).delete() }
                if (command.cleanupPlaylistArtwork) cleanupUnusedPlaylistArtwork(command.snapshot)
                writeIndex(command.snapshot)
            }
            StorageCommand.Reset -> {
                directory.listFiles()?.forEach { it.deleteRecursively() }
                directory.mkdirs()
                writeIndex(emptyList())
            }
        }
    }

    private fun contentUriFor(file: File): Uri =
        FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)

    private fun playlistArtworkFileName(playlistId: Long): String = "playlist_${playlistId}.jpg"

    private sealed interface StorageCommand {
        data class Delete(val fileNames: Set<String>) : StorageCommand
        data class Persist(
            val snapshot: List<MeloXDownloadedSong>,
            val fileNamesToDelete: Set<String>,
            val cleanupPlaylistArtwork: Boolean,
        ) : StorageCommand
        data object Reset : StorageCommand
    }

    companion object {
        private const val MAX_AUDIO_DOWNLOAD_BYTES = 512L * 1024L * 1024L
        @SuppressLint("StaticFieldLeak") // The store normalizes its field to applicationContext.
        @Volatile private var instance: MeloXDownloadStore? = null

        fun get(context: Context): MeloXDownloadStore =
            instance ?: synchronized(this) {
                instance ?: MeloXDownloadStore(context.applicationContext).also { instance = it }
            }
    }
}
