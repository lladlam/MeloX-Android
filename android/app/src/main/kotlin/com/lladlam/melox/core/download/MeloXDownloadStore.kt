package com.lladlam.melox.core.download

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.NeteaseQualityClient
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricSyllable
import com.lladlam.melox.core.lyrics.LyricsDocument
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
)

data class MeloXActiveDownload(
    val song: SearchSong,
    val quality: MusicQuality,
    val receivedByteCount: Long = 0L,
    val expectedByteCount: Long? = null,
) {
    val fractionCompleted: Float?
        get() = expectedByteCount?.takeIf { it > 0L }
            ?.let { (receivedByteCount.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat() }
}

/**
 * Android counterpart of upstream DownloadStore.
 *
 * Files live in app-private storage so offline playback needs no storage
 * permission. Metadata is persisted as JSON; up to three transfers run in
 * parallel, matching MeloX's upstream concurrency limit.
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

    fun contains(songId: Long): Boolean = downloads.any { it.song.id == songId }
    fun isDownloading(songId: Long): Boolean = activeDownloads.containsKey(songId)

    fun localArtworkUri(songId: Long): Uri? {
        val record = downloads.firstOrNull { it.song.id == songId } ?: return null
        val fileName = record.artworkFileName ?: return null
        val file = File(directory, fileName)
        return file.takeIf(File::isFile)?.let(Uri::fromFile)
    }

    fun localLyrics(songId: Long): LyricsDocument? {
        val record = downloads.firstOrNull { it.song.id == songId } ?: return null
        val fileName = record.lyricsFileName ?: return null
        val file = File(directory, fileName)
        if (!file.isFile) return null
        return runCatching { decodeLyrics(JSONObject(file.readText())) }.getOrNull()
    }

    fun localPlaybackUri(songId: Long): Uri? {
        val record = downloads.firstOrNull { it.song.id == songId } ?: return null
        val file = File(directory, record.fileName)
        if (!file.isFile) {
            scope.launch { removeMissingRecord(songId) }
            return null
        }
        return Uri.fromFile(file)
    }

    fun start(song: SearchSong, quality: MusicQuality) {
        if (contains(song.id) || isDownloading(song.id) || jobs.containsKey(song.id)) return
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
        File(directory, "$songId.part").delete()
    }

    fun remove(songId: Long) {
        cancel(songId)
        val record = downloads.firstOrNull { it.song.id == songId } ?: return
        downloads.remove(record)
        File(directory, record.fileName).delete()
        record.artworkFileName?.let { File(directory, it).delete() }
        record.lyricsFileName?.let { File(directory, it).delete() }
        saveIndex()
    }

    fun removeAll() {
        jobs.keys.toList().forEach(::cancel)
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
                                    withContext(Dispatchers.Main) {
                                        activeDownloads[song.id] = MeloXActiveDownload(song, quality, received, expected)
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
            val artworkFileName = downloadArtworkIfAvailable(song)
            val lyricsFileName = if (MeloXSettingsPreferences.boolean(app, "download_lyrics", true)) {
                downloadLyricsIfEnabled(song)
            } else null
            val record = MeloXDownloadedSong(
                song = song,
                quality = source.quality ?: quality,
                fileName = finalName,
                byteCount = finalFile.length(),
                bitrate = source.bitrate,
                format = source.format,
                downloadedAt = System.currentTimeMillis(),
                artworkFileName = artworkFileName,
                lyricsFileName = lyricsFileName,
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
        } catch (error: Throwable) {
            temp.delete()
            activeDownloads.remove(song.id)
            jobs.remove(song.id)
            errorMessage = "《${song.name}》下载失败：${error.message ?: error::class.java.simpleName}"
        }
    }

    private suspend fun downloadArtworkIfAvailable(song: SearchSong): String? {
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
                    .put("lyricsFileName", record.lyricsFileName ?: ""),
            )
        }
        runCatching {
            directory.mkdirs()
            indexFile.writeText(array.toString())
        }
    }

    private fun removeMissingRecord(songId: Long) {
        downloads.removeAll { it.song.id == songId }
        saveIndex()
    }

    companion object {
        @Volatile private var instance: MeloXDownloadStore? = null

        fun get(context: Context): MeloXDownloadStore =
            instance ?: synchronized(this) {
                instance ?: MeloXDownloadStore(context.applicationContext).also { instance = it }
            }
    }
}
