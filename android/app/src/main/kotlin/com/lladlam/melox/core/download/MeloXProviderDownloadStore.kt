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
    private val pluginDownloadSlots = kotlinx.coroutines.sync.Semaphore(2)
    private val recordsByKey = ConcurrentHashMap<String, MeloXProviderDownloadedSong>()

    val downloads = mutableStateListOf<MeloXProviderDownloadedSong>()
    val activeDownloads = mutableStateMapOf<String, Boolean>()
    val downloadErrors = mutableStateMapOf<String, String>()
    data class Attempt(val track: MusicTrack, val quality: AudioQualityTier)
    /** Session-only task descriptions; never retain resolved URLs or credentials for retries. */
    val attempts = mutableStateMapOf<String, Attempt>()

    fun retry(id: MusicResourceId) { attempts[key(id)]?.let { start(it.track, it.quality) } }
    fun cancel(id: MusicResourceId) { jobs[key(id)]?.cancel() }
    fun dismissError(id: MusicResourceId) {
        val idKey = key(id)
        if (jobs.containsKey(idKey)) return
        attempts.remove(idKey); downloadErrors.remove(idKey)
    }

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
    fun errorFor(id: MusicResourceId): String? = downloadErrors[key(id)]

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
        attempts[idKey] = Attempt(track, quality)
        val provider = MeloXMusicProviders.create(app)[track.id.sourceId]
        if (provider == null) { downloadErrors[idKey] = "来源缺失或已禁用：${track.id.sourceId.value}"; return }
        if (track.id.sourceId.builtIn == null && !provider.supports(com.lladlam.melox.core.music.provider.MusicCapability.Download)) {
            downloadErrors[idKey] = "来源未声明下载能力"; return
        }
        val downloader = provider as? DownloadCapability ?: run { downloadErrors[idKey] = "来源不支持下载"; return }
        activeDownloads[idKey] = true
        downloadErrors.remove(idKey)
        jobs[idKey] = scope.launch(Dispatchers.IO) {
            var acquired = false
            try {
                if (provider is com.lladlam.melox.core.plugin.PluginMusicProvider) {
                    pluginDownloadSlots.acquire(); acquired = true
                }
                val resolution = downloader.resolveDownload(track, quality)
                val playable = resolution as? PlaybackResolution.Playable
                    ?: error("${track.id.sourceId.displayName} 当前没有可下载音频")
                val extension = extensionFor(playable.format)
                val fileName = "${digest(idKey)}.$extension"
                val destination = File(directory, fileName)
                val job = kotlinx.coroutines.currentCoroutineContext()[Job]!!
                kotlinx.coroutines.runInterruptible { download(playable, destination, provider) { job.isActive } }
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
                    attempts.remove(idKey)
                }
                withContext(Dispatchers.IO) { writeIndex(recordsByKey.values.toList()) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main.immediate) { attempts.remove(idKey); downloadErrors.remove(idKey) }
                throw cancelled
            }
            catch (failure: Exception) {
                withContext(Dispatchers.Main.immediate) { downloadErrors[idKey] = "下载失败：${failure.javaClass.simpleName}" }
            } finally {
                if (acquired) pluginDownloadSlots.release()
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main.immediate) { activeDownloads.remove(idKey) }
                jobs.remove(idKey)
            }
        }
    }

    private fun download(resolution: PlaybackResolution.Playable, destination: File, provider: com.lladlam.melox.core.music.provider.MusicProvider, isActive: () -> Boolean) {
        destination.parentFile?.mkdirs()
        val uri = Uri.parse(resolution.url)
        val plugin = provider as? com.lladlam.melox.core.plugin.PluginMusicProvider
        if (uri.scheme == "file" && plugin == null) {
            File(requireNotNull(uri.path)).inputStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        val request = Request.Builder().url(resolution.url).apply {
            resolution.requestHeaders.forEach(::header)
        }.build()
        val broker = plugin?.mediaBroker()
        try {
        broker?.authorizeMediaUrl(resolution.url)
        val registry = MeloXMusicProviders.create(app)
        val generation = registry.generation
        val client = broker?.mediaClient(resolution.credentialReference == "active") ?: com.lladlam.melox.core.network.MeloXHttpClient.shared
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "下载失败: HTTP ${response.code}" }
            val body = requireNotNull(response.body) { "下载响应为空" }
            body.byteStream().use { input -> destination.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    check(isActive() && !Thread.currentThread().isInterrupted) { "download cancelled" }
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (plugin != null) {
                        total += count
                        check(total <= 512L * 1024 * 1024) { "plugin download quota exceeded" }
                        check(registry.generation == generation && registry[provider.sourceId] === provider) { "plugin changed during download" }
                    }
                    output.write(buffer, 0, count)
                }
                if (plugin != null) check(total > 0 && registry.generation == generation && registry[provider.sourceId] === provider) { "invalid or revoked download" }
            } }
        }
        } catch (error: Throwable) { if (plugin != null) destination.delete(); throw error }
        finally { broker?.close() }
    }

    private fun readIndex(): List<MeloXProviderDownloadedSong> = runCatching {
        if (!indexFile.isFile) return emptyList()
        val array = JSONArray(indexFile.readText())
        buildList {
            for (index in 0 until array.length()) {
                val json = array.getJSONObject(index)
                val source = com.lladlam.melox.core.music.model.SourceId.parse(json.optString("source")) ?: continue
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
                    providerMetadata = if (source.builtIn == MusicSource.Spotify) {
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
                put("source", track.id.sourceId.value)
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

    private fun key(id: MusicResourceId): String = "${id.sourceId.value}:${id.value}"

    companion object {
        @Volatile private var instance: MeloXProviderDownloadStore? = null

        fun get(context: Context): MeloXProviderDownloadStore =
            instance ?: synchronized(this) {
                instance ?: MeloXProviderDownloadStore(context).also { instance = it }
            }
    }
}
