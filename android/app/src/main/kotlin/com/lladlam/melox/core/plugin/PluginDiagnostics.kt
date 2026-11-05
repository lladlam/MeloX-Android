package com.lladlam.melox.core.plugin

import android.content.Context
import android.util.AtomicFile
import com.lladlam.melox.core.music.model.SourceId
import java.io.File
import java.io.FileNotFoundException
import org.json.JSONArray
import org.json.JSONObject

/** Bounded, per-source host events. No arguments, script text, log bodies, URLs or account data. */
internal class PluginDiagnostics(context: Context, source: SourceId) {
    init { require(source.builtIn == null) }
    private val root = File(context.applicationContext.noBackupFilesDir, "melox-plugin-diagnostics-v1")
    private val file = AtomicFile(File(root, PluginPackageTrust.sha256(source.value.toByteArray(Charsets.UTF_8)) + ".json"))
    private val methods = PluginCapability.entries.flatMap { it.methods }.toSet() + "__validate"

    fun record(method: String, code: String, elapsedMs: Long) = synchronized(lock) {
        require(PluginErrors.known(code))
        val old = read()
        val events = JSONArray()
        for (i in (old.length() - 99).coerceAtLeast(0) until old.length()) events.put(old.get(i))
        events.put(JSONObject().put("time", System.currentTimeMillis()).put("method", method.takeIf { it in methods } ?: "unknown")
            .put("code", code).put("elapsedMs", elapsedMs.coerceIn(0, 3_600_000)))
        check(root.isDirectory || root.mkdirs())
        val bytes = events.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES)
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Throwable) { file.failWrite(output); throw error }
    }

    fun lines(): List<String> = synchronized(lock) {
        val values = read()
        buildList {
            for (i in 0 until values.length()) {
                val value = values.getJSONObject(i)
                val method = value.getString("method").takeIf { it in methods } ?: "unknown"
                val code = value.getString("code").takeIf(PluginErrors::known) ?: "PLUGIN_EXECUTION_FAILED"
                val timestamp = value.getLong("time").coerceAtLeast(0)
                val elapsed = value.getLong("elapsedMs").coerceIn(0, 3_600_000)
                add("$timestamp · $method · $code · ${PluginErrors.message(code)} · ${elapsed}ms")
            }
        }
    }

    fun clear() = synchronized(lock) { file.delete() }

    private fun read(): JSONArray = try {
        file.openRead().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val n = input.read(buffer); if (n < 0) break
                require(output.size() + n <= MAX_BYTES) { "diagnostic file too large" }
                output.write(buffer, 0, n)
            }
            val raw = output.toString("UTF-8")
            PluginJson.check(raw, MAX_BYTES)
            JSONArray(raw).also { require(it.length() <= 100) }
        }
    } catch (_: FileNotFoundException) { JSONArray() }

    private companion object {
        const val MAX_BYTES = 32 * 1024
        val lock = Any()
    }
}
