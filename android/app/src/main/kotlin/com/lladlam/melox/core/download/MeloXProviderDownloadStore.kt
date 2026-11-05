package com.lladlam.melox.core.download

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.ensureActive
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
    private val taskStore = ProviderDownloadTaskStore(directory)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val pluginDownloadSlots = kotlinx.coroutines.sync.Semaphore(2)
    private val recordsByKey = ConcurrentHashMap<String, MeloXProviderDownloadedSong>()
    private val indexLock = Any()
    private val removedIndexKeys = ConcurrentHashMap.newKeySet<String>()
    private val dismissedTaskRevisions = ConcurrentHashMap.newKeySet<String>()
    private val restoreReady = kotlinx.coroutines.CompletableDeferred<Unit>()

    val downloads = mutableStateListOf<MeloXProviderDownloadedSong>()
    val activeDownloads = mutableStateMapOf<String, Boolean>()
    val downloadErrors = mutableStateMapOf<String, String>()
    data class Attempt(val track: MusicTrack, val quality: AudioQualityTier, val revision: String = java.util.UUID.randomUUID().toString(),
        val accountScope: String? = null)
    /** Durable retry descriptions; resolved download URLs and credentials are never saved here. */
    val attempts = mutableStateMapOf<String, Attempt>()
    var taskRecoveryError by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    fun retry(id: MusicResourceId) { attempts[key(id)]?.let { start(it.track, it.quality, it.accountScope) } }
    fun cancel(id: MusicResourceId) { jobs[key(id)]?.cancel() }
    fun dismissError(id: MusicResourceId) {
        val idKey = key(id)
        if (jobs.containsKey(idKey)) return
        val attempt = attempts.remove(idKey)
        attempt?.let { dismissedTaskRevisions.add(it.revision) }
        downloadErrors.remove(idKey)
        if (attempt != null) {
            val cleanup = scope.launch(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
                try { taskStore.remove(id, attempt.revision); clearPartialFiles(idKey) }
                catch (_: Exception) { withContext(Dispatchers.Main.immediate) {
                    if (attempts[idKey] == null) attempts[idKey] = attempt
                    downloadErrors[idKey] = com.lladlam.melox.core.plugin.PluginErrors.message("PERSISTENCE_FAILED")
                } }
                finally { jobs.remove(idKey) }
            }
            jobs[idKey] = cleanup
            cleanup.start()
        }
    }

    init {
        scope.launch {
            val restored = withContext(Dispatchers.IO) { readIndex() }
            restored.forEach { record ->
                val idKey = key(record.track.id)
                if (idKey !in removedIndexKeys && recordsByKey.putIfAbsent(idKey, record) == null) downloads += record
            }
            try {
                val savedTasks = withContext(Dispatchers.IO) { taskStore.load() }
                for (task in savedTasks) {
                    val idKey = key(task.track.id)
                    if (isDownloaded(task.track.id)) {
                        withContext(Dispatchers.IO) { taskStore.remove(task.track.id, task.revision) }
                    } else if (idKey !in attempts && idKey !in removedIndexKeys && task.revision !in dismissedTaskRevisions) {
                        attempts[idKey] = Attempt(task.track, task.quality, task.revision, task.accountScope)
                        downloadErrors[idKey] = com.lladlam.melox.core.plugin.PluginErrors.message(task.errorCode ?: "DOWNLOAD_INTERRUPTED")
                    }
                }
            } catch (_: Exception) {
                taskRecoveryError = "下载任务无法恢复，原任务文件已保留。请检查存储空间后重启应用。"
            }
        }.invokeOnCompletion { restoreReady.complete(Unit) }
    }

    fun isDownloaded(id: MusicResourceId): Boolean = recordsByKey[key(id)]?.let { record ->
        File(directory, record.fileName).isFile
    } == true

    fun isDownloading(id: MusicResourceId): Boolean = activeDownloads[key(id)] == true
    fun errorFor(id: MusicResourceId): String? = downloadErrors[key(id)]

    fun recordFor(id: MusicResourceId): MeloXProviderDownloadedSong? = recordsByKey[key(id)]

    fun remove(id: MusicResourceId) {
        val idKey = key(id)
        removedIndexKeys.add(idKey)
        // Keep the slot until cancellation cleanup finishes: a retry must not race the old
        // download's temporary file or have its Job removed by the old coroutine's finally.
        jobs[idKey]?.cancel()
        if (!jobs.containsKey(idKey)) dismissError(id)
        if (!jobs.containsKey(idKey)) {
            val cleanup = scope.launch(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
                try { clearPartialFiles(idKey) }
                catch (_: Exception) { withContext(Dispatchers.Main.immediate) {
                    taskRecoveryError = "下载临时文件清理失败，请检查存储空间后重试。"
                } }
                finally { jobs.remove(idKey) }
            }
            jobs[idKey] = cleanup
            cleanup.start()
        }
        recordsByKey.remove(idKey)?.let { record ->
            File(directory, record.fileName).delete()
            downloads.removeAll { key(it.track.id) == idKey }
        }
        scope.launch(Dispatchers.IO) {
            try { restoreReady.await(); writeIndex() }
            catch (_: Exception) { withContext(Dispatchers.Main.immediate) {
                taskRecoveryError = "下载索引更新失败，删除状态尚未保存；请检查存储空间后重试。"
            } }
        }
    }

    fun localPlaybackUri(id: MusicResourceId): Uri? = recordsByKey[key(id)]
        ?.let { File(directory, it.fileName) }
        ?.takeIf(File::isFile)
        ?.let(Uri::fromFile)

    fun start(track: MusicTrack, quality: AudioQualityTier = AudioQualityTier.Standard) = start(track, quality, null)

    private fun start(track: MusicTrack, quality: AudioQualityTier, expectedAccountScope: String?) {
        val idKey = key(track.id)
        if (isDownloaded(track.id) || jobs.containsKey(idKey)) return
        val attempt = Attempt(track, quality, accountScope = expectedAccountScope)
        attempts[idKey] = attempt
        activeDownloads[idKey] = true
        downloadErrors.remove(idKey)
        val entered = java.util.concurrent.atomic.AtomicBoolean()
        val downloadJob = scope.launch(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
            entered.set(true)
            var acquired = false
            var saved = false
            var publishedRecord: MeloXProviderDownloadedSong? = null
            var indexCommitted = false
            var accountScope = expectedAccountScope
            try {
                restoreReady.await()
                if (isDownloaded(track.id)) {
                    withContext(Dispatchers.Main.immediate) { attempts.remove(idKey) }
                    return@launch
                }
                val currentAccountScope = downloadAccountScope(track.id)
                accountScope = expectedAccountScope ?: currentAccountScope
                taskStore.save(ProviderDownloadTaskStore.Task(track, quality, attempt.revision, accountScope = accountScope))
                saved = true
                withContext(Dispatchers.Main.immediate) { attempts[idKey] = attempt.copy(accountScope = accountScope) }
                check(expectedAccountScope == null || expectedAccountScope == currentAccountScope) { "DOWNLOAD_ACCOUNT_CHANGED" }
                val provider = MeloXMusicProviders.create(app)[track.id.sourceId] ?: error("SOURCE_UNAVAILABLE")
                if (track.id.sourceId.builtIn == null && !provider.supports(com.lladlam.melox.core.music.provider.MusicCapability.Download)) error("UNSUPPORTED_OPERATION")
                val downloader = provider as? DownloadCapability ?: error("UNSUPPORTED_OPERATION")
                if (provider is com.lladlam.melox.core.plugin.PluginMusicProvider) {
                    pluginDownloadSlots.acquire(); acquired = true
                }
                val (playable, destination) = resolveAndDownload(track, quality, provider, downloader, accountScope)
                val fileName = destination.name
                val record = MeloXProviderDownloadedSong(
                    track = track,
                    quality = quality,
                    fileName = fileName,
                    byteCount = destination.length(),
                    format = playable.format,
                    bitrate = playable.bitrate,
                    downloadedAt = System.currentTimeMillis(),
                )
                val owner = kotlinx.coroutines.currentCoroutineContext()[Job]!!
                // Once publication begins, finish (or roll back) the durable index before
                // cancellation cleanup decides whether the promoted audio file is committed.
                withContext(kotlinx.coroutines.NonCancellable) {
                    withContext(Dispatchers.Main.immediate) {
                        owner.ensureActive()
                        publishedRecord = record
                        removedIndexKeys.remove(idKey)
                        recordsByKey[idKey] = record
                        downloads.removeAll { key(it.track.id) == idKey }
                        downloads += record
                    }
                    writeIndex()
                    indexCommitted = true
                }
                val removed = runCatching { taskStore.remove(track.id, attempt.revision) }.isSuccess
                if (!removed) withContext(Dispatchers.Main.immediate) { taskRecoveryError = "下载已保存，但任务记录清理失败；下次启动会重新核对。" }
                withContext(Dispatchers.Main.immediate) { if (attempts[idKey]?.revision == attempt.revision) attempts.remove(idKey) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                withContext(kotlinx.coroutines.NonCancellable) {
                    clearPartialFiles(idKey)
                    if (!indexCommitted) withContext(Dispatchers.Main.immediate) {
                        publishedRecord?.let { record ->
                            if (recordsByKey.remove(idKey, record)) downloads.removeAll { key(it.track.id) == idKey }
                        }
                    }
                    if (!indexCommitted) clearUncommittedFiles(idKey)
                    val removed = runCatching { taskStore.remove(track.id, attempt.revision) }.isSuccess
                    withContext(Dispatchers.Main.immediate) {
                        if (removed) { attempts.remove(idKey); downloadErrors.remove(idKey) }
                        else downloadErrors[idKey] = com.lladlam.melox.core.plugin.PluginErrors.message("PERSISTENCE_FAILED")
                    }
                }
                throw cancelled
            }
            catch (failure: Exception) {
                if (!indexCommitted) withContext(Dispatchers.Main.immediate) {
                    publishedRecord?.let { record ->
                        if (recordsByKey.remove(idKey, record)) downloads.removeAll { key(it.track.id) == idKey }
                    }
                }
                if (!indexCommitted) clearUncommittedFiles(idKey)
                val code = if (saved) com.lladlam.melox.core.plugin.PluginErrors.code(failure) else "PERSISTENCE_FAILED"
                val persisted = runCatching { taskStore.save(ProviderDownloadTaskStore.Task(track, quality, attempt.revision, code, accountScope)) }.isSuccess
                withContext(Dispatchers.Main.immediate) {
                    downloadErrors[idKey] = com.lladlam.melox.core.plugin.PluginErrors.message(if (persisted) code else "PERSISTENCE_FAILED")
                }
            } finally {
                if (acquired) pluginDownloadSlots.release()
                if (idKey in removedIndexKeys) clearPartialFiles(idKey)
            }
        }
        jobs[idKey] = downloadJob
        downloadJob.invokeOnCompletion {
            // Completion handlers also run if cancellation wins before the lazy body starts.
            scope.launch(Dispatchers.Main.immediate) {
                if (!entered.get()) {
                    try {
                        restoreReady.await()
                        withContext(Dispatchers.IO) {
                            // The new revision may never have reached disk. The resource slot
                            // stays occupied during cleanup, so no later retry can be removed.
                            taskStore.load().filter { it.track.id == track.id }.forEach {
                                taskStore.remove(it.track.id, it.revision)
                            }
                            clearPartialFiles(idKey)
                        }
                        if (attempts[idKey]?.revision == attempt.revision) attempts.remove(idKey)
                    } catch (_: Exception) {
                        downloadErrors[idKey] = com.lladlam.melox.core.plugin.PluginErrors.message("PERSISTENCE_FAILED")
                    }
                }
                if (jobs.remove(idKey, downloadJob)) {
                    activeDownloads.remove(idKey)
                }
            }
        }
        downloadJob.start()
    }

    private fun clearPartialFiles(idKey: String) {
        for (extension in listOf("mp3", "m4a", "ogg", "audio")) PluginDownloadTransfer.clear(File(directory, "${digest(idKey)}.$extension"))
    }

    private fun clearUncommittedFiles(idKey: String) {
        val committed = recordsByKey[idKey]?.fileName
        for (extension in listOf("mp3", "m4a", "ogg", "audio")) {
            val file = File(directory, "${digest(idKey)}.$extension")
            if (file.name != committed) file.delete()
        }
    }

    private suspend fun resolveAndDownload(track: MusicTrack, quality: AudioQualityTier,
        provider: com.lladlam.melox.core.music.provider.MusicProvider, downloader: DownloadCapability, accountScope: String?): Pair<PlaybackResolution.Playable, File> {
        var retried = false
        while (true) {
            check(downloadAccountScope(track.id) == accountScope) { "DOWNLOAD_ACCOUNT_CHANGED" }
            val resolution = downloader.resolveDownload(track, quality)
            check(downloadAccountScope(track.id) == accountScope) { "DOWNLOAD_ACCOUNT_CHANGED" }
            val playable = resolution as? PlaybackResolution.Playable ?: error(
                if (resolution == PlaybackResolution.LoginRequired) "LOGIN_REQUIRED" else "DOWNLOAD_FAILED")
            val destination = File(directory, "${digest(key(track.id))}.${extensionFor(playable.format)}")
            val job = kotlinx.coroutines.currentCoroutineContext()[Job]!!
            try {
                kotlinx.coroutines.runInterruptible { download(playable, destination, provider, quality, accountScope) { job.isActive } }
                return playable to destination
            } catch (rejected: PluginDownloadTransfer.AccessRejected) {
                val plugin = provider as? com.lladlam.melox.core.plugin.PluginMusicProvider ?: throw rejected
                if (retried || plugin.manifest.authentication?.type != "oauth2Pkce" || playable.credentialReference != "active") throw rejected
                plugin.requestAuthorizationRefresh()
                retried = true
            }
        }
    }

    private fun downloadAccountScope(id: MusicResourceId): String? = if (id.sourceId.builtIn != null) null else {
        val selected = com.lladlam.melox.core.plugin.PluginAccountVault(app, id.sourceId).selected()
        digest(id.sourceId.value + "\n" + selected.orEmpty())
    }

    private fun download(resolution: PlaybackResolution.Playable, destination: File, provider: com.lladlam.melox.core.music.provider.MusicProvider, quality: AudioQualityTier, accountScope: String?, isActive: () -> Boolean) {
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
        val broker = plugin?.mediaBroker(accountScope)
        try {
        broker?.authorizeMediaUrl(resolution.url)
        val registry = MeloXMusicProviders.create(app)
        val generation = registry.generation
        val client = broker?.mediaClient(resolution.credentialReference == "active") ?: com.lladlam.melox.core.network.MeloXHttpClient.shared
        if (plugin != null) {
            PluginDownloadTransfer.run(client, request, destination,
                scope = { plugin.downloadResumeScope()?.let { digest(it + ":" + quality.name) } },
                active = { isActive() && registry.generation == generation && registry[provider.sourceId] === provider },
                discard = { !isActive() })
            return
        }
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
        val array = synchronized(indexLock) { readRawIndex() }
        buildList {
            for (index in 0 until array.length()) {
                try {
                val json = array.getJSONObject(index)
                val source = com.lladlam.melox.core.music.model.SourceId.parse(json.optString("source")) ?: continue
                val id = json.optString("id").takeIf(String::isNotBlank) ?: continue
                val fileName = json.optString("fileName").takeIf(String::isNotBlank) ?: continue
                if (!fileName.matches(Regex("[a-f0-9]{64}\\.(mp3|m4a|ogg|audio)"))) continue
                val track = json.optJSONObject("track")?.let { com.lladlam.melox.core.music.model.MeloXTrackJson.decode(it) } ?: MusicTrack(
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
                if (track.id != MusicResourceId(source, id)) continue
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
                } catch (_: Exception) { /* Preserve opaque row in readRawIndex; other records remain usable. */ }
            }
        }
    }.getOrDefault(emptyList())

    private fun writeIndex() = synchronized(indexLock) {
        val array = JSONArray()
        recordsByKey.values.forEach { record ->
            val track = record.track
            array.put(JSONObject().apply {
                put("source", track.id.sourceId.value)
                put("id", track.id.value)
                put("track", com.lladlam.melox.core.music.model.MeloXTrackJson.encode(track))
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
        val merged = ProviderDownloadIndexCodec.merge(readRawIndex(), array, removedIndexKeys.toSet())
        val atomic = android.util.AtomicFile(indexFile)
        val output = atomic.startWrite()
        try { output.write(merged.toString().toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
        catch (error: Throwable) { atomic.failWrite(output); throw error }
    }

    private fun readRawIndex(): JSONArray = try {
        android.util.AtomicFile(indexFile).openRead().use { input ->
            val bytes = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                require(bytes.size() + count <= 16 * 1024 * 1024) { "download index too large" }
                bytes.write(buffer, 0, count)
            }
            JSONArray(bytes.toString("UTF-8"))
        }
    } catch (_: java.io.FileNotFoundException) { JSONArray() }

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
