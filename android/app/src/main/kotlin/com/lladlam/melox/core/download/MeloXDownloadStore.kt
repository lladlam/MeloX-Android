package com.lladlam.melox.core.download

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.NeteaseQualityClient
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricSyllable
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
import kotlinx.coroutines.SupervisorJob
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
    private val http = OkHttpClient()
    private val qualityClient = NeteaseQualityClient(
        cookieProvider = { NeteaseSessionStore.readCookie(app) },
        httpClient = http,
    )
    private val searchClient = NeteaseSearchClient(
        httpClient = http,
        cookieProvider = { NeteaseSessionStore.readCookie(app) },
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val transferSlots = Semaphore(3)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val pendingPlaylistRefs = ConcurrentHashMap<Long, MutableList<MeloXDownloadPlaylistRef>>()

    val downloads = mutableStateListOf<MeloXDownloadedSong>()
    val activeDownloads = mutableStateMapOf<Long, MeloXActiveDownload>()
    var errorMessage: String? = null
        private set

    init {
        loadIndex()
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

    fun contains(songId: Long): Boolean = downloads.any { it.song.id == songId }
    fun isDownloading(songId: Long): Boolean = activeDownloads.containsKey(songId)
    fun recordFor(songId: Long): MeloXDownloadedSong? = downloads.firstOrNull { it.song.id == songId }
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
        activeDownloads[song.id] = MeloXActiveDownload(song, quality)
        jobs[song.id] = scope.launch {
            transferSlots.withPermit {
                download(song, quality)
            }
        }
    }

    fun cancel(songId: Long) {
        jobs.remove(songId)?.cancel()
        activeDownloads.remove(songId)
        pendingPlaylistRefs.remove(songId)
        File(directory, "$songId.part").delete()
    }

    fun remove(songId: Long) = removeMany(setOf(songId))

    fun removeMany(songIds: Set<Long>) {
        if (songIds.isEmpty()) return
        songIds.forEach(::cancel)
        val removed = downloads.filter { it.song.id in songIds }
        removed.forEach { record ->
            File(directory, record.fileName).delete()
            record.artworkFileName?.let { File(directory, it).delete() }
            record.lyricsFileName?.let { File(directory, it).delete() }
        }
        downloads.removeAll { it.song.id in songIds }
        cleanupUnusedPlaylistArtwork()
        saveIndex()
    }

    fun removeAll() {
        jobs.keys.toList().forEach(::cancel)
        pendingPlaylistRefs.clear()
        downloads.clear()
        directory.listFiles()?.forEach { it.deleteRecursively() }
        directory.mkdirs()
        saveIndex()
    }

    fun clearError() {
        errorMessage = null
    }

    private suspend fun download(song: SearchSong, quality: MusicQuality) {
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
            val request = Request.Builder()
                .url(resolvedSource.url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
                .header("Referer", "https://music.163.com/")
                .build()

            val result = withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("下载失败：HTTP ${response.code}")
                    val body = response.body
                    val expected = body.contentLength().takeIf { it > 0L }
                    val startedAt = SystemClock.elapsedRealtime()
                    temp.outputStream().buffered().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                            var received = 0L
                            var lastPublished = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                received += count
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
            downloads.removeAll { it.song.id == song.id }
            downloads.add(0, record)
            activeDownloads.remove(song.id)
            jobs.remove(song.id)
            saveIndex()
        } catch (_: CancellationException) {
            temp.delete()
            activeDownloads.remove(song.id)
            jobs.remove(song.id)
            pendingPlaylistRefs.remove(song.id)
        } catch (error: Throwable) {
            temp.delete()
            activeDownloads.remove(song.id)
            jobs.remove(song.id)
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
        downloads[index] = record.copy(sourcePlaylists = record.sourcePlaylists + ref)
        saveIndex()
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

    private fun encodeLyrics(document: LyricsDocument): JSONObject = JSONObject().put(
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
                add(
                    LyricLine(
                        timeMs = line.optLong("timeMs"),
                        durationMs = line.optLong("durationMs", -1L).takeIf { it >= 0L },
                        text = line.optString("text"),
                        syllables = syllables,
                        translation = line.optString("translation").takeIf(String::isNotBlank),
                        romanization = line.optString("romanization").takeIf(String::isNotBlank),
                        romanizationSyllables = romanizationSyllables,
                    ),
                )
            }
        }
        return LyricsDocument(lines)
    }

    private fun loadIndex() {
        val raw = runCatching { indexFile.takeIf(File::isFile)?.readText() }.getOrNull().orEmpty()
        if (raw.isBlank()) return
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return
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
            downloads += MeloXDownloadedSong(
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
    }

    private fun saveIndex() {
        val array = JSONArray()
        downloads.forEach { record ->
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
            indexFile.writeText(array.toString())
        }
    }

    private fun removeMissingRecord(songId: Long) {
        downloads.removeAll { it.song.id == songId }
        cleanupUnusedPlaylistArtwork()
        saveIndex()
    }

    private fun cleanupUnusedPlaylistArtwork() {
        val used = downloads.flatMap { it.sourcePlaylists }.map { it.id }.toSet()
        directory.listFiles()?.forEach { file ->
            if (!file.name.startsWith("playlist_") || !file.name.endsWith(".jpg")) return@forEach
            val id = file.name.removePrefix("playlist_").removeSuffix(".jpg").toLongOrNull()
            if (id != null && id !in used) file.delete()
        }
    }

    private fun contentUriFor(file: File): Uri =
        FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)

    private fun playlistArtworkFileName(playlistId: Long): String = "playlist_${playlistId}.jpg"

    companion object {
        @Volatile private var instance: MeloXDownloadStore? = null

        fun get(context: Context): MeloXDownloadStore =
            instance ?: synchronized(this) {
                instance ?: MeloXDownloadStore(context.applicationContext).also { instance = it }
            }
    }
}
