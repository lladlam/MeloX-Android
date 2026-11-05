package com.lladlam.melox.core.download

import android.util.AtomicFile
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MeloXTrackJson
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.plugin.PluginErrors
import java.io.File
import java.io.FileNotFoundException
import org.json.JSONArray
import org.json.JSONObject

/** Saves retry intent only. Resolved URLs/headers, credentials and automatic network work are absent. */
internal class ProviderDownloadTaskStore(directory: File) {
    data class Task(val track: MusicTrack, val quality: AudioQualityTier, val revision: String, val errorCode: String? = null,
        val accountScope: String? = null)
    private val file = AtomicFile(File(directory, "tasks-v1.json"))

    fun load(): List<Task> = synchronized(lock) {
        val rows = read()
        buildList {
            for (i in 0 until rows.length()) {
                // An unknown/future row survives subsequent writes even if this build cannot display it.
                val task = runCatching {
                    val value = rows.getJSONObject(i)
                    val track = requireNotNull(MeloXTrackJson.decode(value.getJSONObject("track")))
                    require(ProviderDownloadIndexCodec.identity(value) == key(track.id))
                    val quality = AudioQualityTier.entries.first { it.name == value.getString("quality") }
                    val revision = value.getString("revision").also { require(it.matches(REVISION)) }
                    require(value.getString("state") in setOf("pending", "failed"))
                    val code = if (value.getString("state") == "pending") "DOWNLOAD_INTERRUPTED" else
                        value.getString("errorCode").takeIf(PluginErrors::known) ?: "DOWNLOAD_FAILED"
                    val accountScope = if (value.has("accountScope") && !value.isNull("accountScope"))
                        value.getString("accountScope").also { require(it.matches(Regex("[a-f0-9]{64}"))) } else null
                    Task(track, quality, revision, code, accountScope)
                }.getOrNull()
                if (task != null) add(task)
            }
        }
    }

    fun save(task: Task) = synchronized(lock) {
        require(task.revision.matches(REVISION))
        require(task.errorCode == null || PluginErrors.known(task.errorCode))
        require(task.accountScope == null || task.accountScope.matches(Regex("[a-f0-9]{64}")))
        val update = JSONObject().put("source", task.track.id.sourceId.value).put("id", task.track.id.value)
            .put("track", MeloXTrackJson.encode(task.track)).put("quality", task.quality.name).put("revision", task.revision)
            .put("state", if (task.errorCode == null) "pending" else "failed")
            .put("errorCode", task.errorCode ?: JSONObject.NULL)
            .put("accountScope", task.accountScope ?: JSONObject.NULL)
        write(ProviderDownloadIndexCodec.merge(read(), JSONArray().put(update), emptySet()))
    }

    /** Old cancellation/dismissal must never delete a newer retry for the same resource. */
    fun remove(id: MusicResourceId, revision: String) = synchronized(lock) {
        val rows = read()
        val remaining = JSONArray()
        for (i in 0 until rows.length()) {
            val row = rows.opt(i)
            if (row is JSONObject && ProviderDownloadIndexCodec.identity(row) == key(id) && row.optString("revision") == revision) continue
            remaining.put(row)
        }
        write(remaining)
    }

    private fun read(): JSONArray = try {
        file.openRead().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer); if (n < 0) break
                require(output.size() + n <= MAX_BYTES) { "download task index too large" }
                output.write(buffer, 0, n)
            }
            val raw = output.toString("UTF-8")
            com.lladlam.melox.core.plugin.PluginJson.check(raw, MAX_BYTES)
            JSONArray(raw).also { require(it.length() <= MAX_TASKS) }
        }
    } catch (_: FileNotFoundException) { JSONArray() }

    private fun write(rows: JSONArray) {
        require(rows.length() <= MAX_TASKS) { "download task quota exceeded" }
        val bytes = rows.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "download task index too large" }
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Throwable) { file.failWrite(output); throw error }
    }

    private fun key(id: MusicResourceId) = "${id.sourceId.value}:${id.value}"
    private companion object {
        val lock = Any()
        val REVISION = Regex("[a-f0-9-]{36}")
        const val MAX_TASKS = 100
        const val MAX_BYTES = 1024 * 1024
    }
}
