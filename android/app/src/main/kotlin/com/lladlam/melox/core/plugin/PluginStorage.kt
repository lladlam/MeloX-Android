package com.lladlam.melox.core.plugin

import android.content.Context
import android.util.AtomicFile
import com.lladlam.melox.core.music.model.SourceId
import java.io.File
import java.io.FileNotFoundException
import org.json.JSONObject

/** Non-secret plugin/account storage. Credentials belong in PluginAccountVault. */
class PluginStorage(context: Context, source: SourceId, accountId: String? = null) {
    private val root = File(context.applicationContext.noBackupFilesDir, "melox-plugin-storage-v1")
    private val file = AtomicFile(File(root, java.security.MessageDigest.getInstance("SHA-256")
        .digest((source.value + (accountId?.let { ":account:$it" } ?: "")).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) } + ".json"))

    fun execute(operation: String, key: String, value: String? = null): Any = synchronized(lock) {
        require(key.matches(Regex("[A-Za-z0-9_.-]{1,128}"))) { "invalid storage key" }
        val previous = try {
            file.openRead().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 64 * 1024) { "storage quota exceeded" }
                    output.write(buffer, 0, count)
                }
                JSONObject(output.toString("UTF-8"))
            }
        } catch (_: FileNotFoundException) { JSONObject() }
        when (operation) {
            "storage.get" -> return@synchronized if (previous.has(key)) previous.getString(key) else JSONObject.NULL
            "storage.set" -> {
                require(value != null && value.toByteArray(Charsets.UTF_8).size <= 16 * 1024) { "storage value too large" }
                previous.put(key, value)
            }
            "storage.remove" -> previous.remove(key)
            else -> error("unsupported storage operation")
        }
        val bytes = previous.toString().toByteArray(Charsets.UTF_8)
        require(previous.length() <= 64 && bytes.size <= 64 * 1024) { "storage quota exceeded" }
        check(root.isDirectory || root.mkdirs()) { "cannot create plugin storage" }
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Throwable) { file.failWrite(output); throw error }
        JSONObject.NULL
    }

    private companion object { val lock = Any() }
}
