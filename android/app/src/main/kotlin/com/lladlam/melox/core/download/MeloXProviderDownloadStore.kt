package com.lladlam.melox.core.download

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.music.provider.DownloadCapability
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class MeloXProviderDownloadedSong(
    val track: MusicTrack,
    val quality: AudioQualityTier,
    val fileName: String,
    val byteCount: Long,
    val format: String? = null,
    val bitrate: Int? = null,
    val downloadedAt: Long,
)

/** Persistent download store for provider-native IDs. Legacy NetEase downloads stay untouched. */
class MeloXProviderDownloadStore private constructor(context: Context) {
    private val app = context.applicationContext
    private val directory = File(app.filesDir, "melox_provider_downloads").apply { mkdirs() }
    private val indexFile = File(directory, "index.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val recordsByKey = ConcurrentHashMap<String, MeloXProviderDownloadedSong>()

    val downloads = mutableStateListOf<MeloXProviderDownloadedSong>()
    val activeDownloads = mutableStateMapOf<String, Boolean>()

    init {
        scope.launch {
            val restored = withContext(Dispatchers.IO) { readIndex() }
            restored.forEach { record ->
                if (recordsByKey.putIfAbsent(key(record.track.id), record) == null) downloads += record
            }
        }
    }

    fun isDownloaded(id: MusicResourceId): Boolean = recordsByKey[key(id)]?.let { record ->
        File(directory, record.fileName).isFile
    } == true

    fun isDownloading(id: MusicResourceId): Boolean = activeDownloads[key(id)] == true

    fun recordFor(id: MusicResourceId): MeloXProviderDownloadedSong? = recordsByKey[key(id)]

    fun remove(id: MusicResourceId) {
        val idKey = key(id)
        jobs.remove(idKey)?.cancel()
        recordsByKey.remove(idKey)?.let { record ->
            File(directory, record.fileName).delete()
            downloads.removeAll { key(it.track.id) == idKey }
            scope.launch(Dispatchers.IO) { writeIndex(recordsByKey.values.toList()) }
        }
    }

    fun localPlaybackUri(id: MusicResourceId): Uri? = recordsByKey[key(id)]
        ?.let { File(directory, it.fileName) }
        ?.takeIf(File::isFile)
        ?.let(Uri::fromFile)

    fun start(track: MusicTrack, quality: AudioQualityTier = AudioQualityTier.Standard) {
        val idKey = key(track.id)
        if (isDownloaded(track.id) || jobs.containsKey(idKey)) return
        val provider = MeloXMusicProviders.create(app).require(track.id.source)
        val downloader = provider as? DownloadCapability ?: return
        activeDownloads[idKey] = true
        jobs[idKey] = scope.launch(Dispatchers.IO) {
            try {
                val resolution = downloader.resolveDownload(track, quality)
                val playable = resolution as? PlaybackResolution.Playable
                    ?: error("${track.id.source.displayName} 当前没有可下载音频")
                val extension = extensionFor(playable.format)
                val fileName = "${digest(idKey)}.$extension"
                val destination = File(directory, fileName)
                download(playable, destination)
                val record = MeloXProviderDownloadedSong(
                    track = track,
                    quality = quality,
                    fileName = fileName,
                    byteCount = destination.length(),
                    format = playable.format,
                    bitrate = playable.bitrate,
                    downloadedAt = System.currentTimeMillis(),
                )
                withContext(Dispatchers.Main.immediate) {
                    recordsByKey[idKey] = record
                    downloads.removeAll { key(it.track.id) == idKey }
                    downloads += record
                }
                withContext(Dispatchers.IO) { writeIndex(recordsByKey.values.toList()) }
            } finally {
                withContext(Dispatchers.Main.immediate) { activeDownloads.remove(idKey) }
                jobs.remove(idKey)
            }
        }
    }

    private fun download(resolution: PlaybackResolution.Playable, destination: File) {
        destination.parentFile?.mkdirs()
        val uri = Uri.parse(resolution.url)
        if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).inputStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        val request = Request.Builder().url(resolution.url).apply {
            resolution.requestHeaders.forEach(::header)
        }.build()
        com.lladlam.melox.core.network.MeloXHttpClient.shared.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "下载失败: HTTP ${response.code}" }
            val body = requireNotNull(response.body) { "下载响应为空" }
            body.byteStream().use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
        }
    }

    private fun readIndex(): List<MeloXProviderDownloadedSong> = runCatching {
        if (!indexFile.isFile) return emptyList()
        val array = JSONArray(indexFile.readText())
        buildList {
            for (index in 0 until array.length()) {
                val json = array.getJSONObject(index)
                val source = MusicSource.fromStorageValue(json.optString("source"))
                val id = json.optString("id").takeIf(String::isNotBlank) ?: continue
                val fileName = json.optString("fileName").takeIf(String::isNotBlank) ?: continue
                val track = MusicTrack(
                    id = MusicResourceId(source, id),
                    title = json.optString("title", "未知歌曲"),
                    artists = json.optString("artists").split(" / ").filter(String::isNotBlank)
                        .map { name -> MusicArtistRef(name = name) },
                    album = json.optString("album").takeIf(String::isNotBlank)
                        ?.let { name -> MusicAlbumRef(name = name) },
                    artworkUrl = json.optString("artworkUrl").takeIf(String::isNotBlank),
                    durationMs = json.optLong("durationMs").takeIf { it > 0L },
                    providerMetadata = if (source == MusicSource.Spotify) {
                        ProviderTrackMetadata.Spotify(id)
                    } else ProviderTrackMetadata.Empty,
                )
                val file = File(directory, fileName)
                if (file.isFile) add(
                    MeloXProviderDownloadedSong(
                        track = track,
                        quality = AudioQualityTier.entries.firstOrNull { it.name == json.optString("quality") }
                            ?: AudioQualityTier.Standard,
                        fileName = fileName,
                        byteCount = file.length(),
                        format = json.optString("format").takeIf(String::isNotBlank),
                        bitrate = json.optInt("bitrate").takeIf { it > 0 },
                        downloadedAt = json.optLong("downloadedAt"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun writeIndex(records: List<MeloXProviderDownloadedSong>) {
        val array = JSONArray()
        records.forEach { record ->
            val track = record.track
            array.put(JSONObject().apply {
                put("source", track.id.source.storageValue)
                put("id", track.id.value)
                put("title", track.title)
                put("artists", track.artistText)
                put("album", track.album?.name.orEmpty())
                put("artworkUrl", track.artworkUrl.orEmpty())
                put("durationMs", track.durationMs ?: 0L)
                put("quality", record.quality.name)
                put("fileName", record.fileName)
                put("byteCount", record.byteCount)
                put("format", record.format.orEmpty())
                put("bitrate", record.bitrate ?: 0)
                put("downloadedAt", record.downloadedAt)
            })
        }
        indexFile.writeText(array.toString())
    }

    private fun extensionFor(format: String?): String = when (format?.lowercase()) {
        "mp3" -> "mp3"
        "m4a", "mp4" -> "m4a"
        "opus", "ogg" -> "ogg"
        else -> "audio"
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun key(id: MusicResourceId): String = "${id.source.storageValue}:${id.value}"

    companion object {
        @Volatile private var instance: MeloXProviderDownloadStore? = null

        fun get(context: Context): MeloXProviderDownloadStore =
            instance ?: synchronized(this) {
                instance ?: MeloXProviderDownloadStore(context).also { instance = it }
            }
    }
}
