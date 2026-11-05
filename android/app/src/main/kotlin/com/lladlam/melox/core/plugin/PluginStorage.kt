package com.lladlam.melox.core.plugin

import android.content.Context
import android.util.AtomicFile
import com.lladlam.melox.core.music.model.SourceId
import java.io.File
import java.io.FileNotFoundException
import org.json.JSONObject

/** Non-secret plugin/account storage. Credentials belong in PluginAccountVault. */
class PluginStorage(context: Context, source: SourceId, accountId: String? = null) {
    init { require(source.builtIn == null) }
    private val base = context.applicationContext.noBackupFilesDir
    private val root = File(base, "melox-plugin-storage-v2/${digest(source.value)}")
    private val file = AtomicFile(File(root, "${digest(accountId ?: "anonymous")}.json"))
    private val legacy = AtomicFile(File(base, "melox-plugin-storage-v1/${digest(source.value + (accountId?.let { ":account:$it" } ?: ""))}.json"))

    fun execute(operation: String, key: String, value: String? = null): Any = synchronized(lock) {
        require(key.matches(Regex("[A-Za-z0-9_.-]{1,128}"))) { "invalid storage key" }
        val previous = try {
            val input = try { file.openRead() } catch (_: FileNotFoundException) { legacy.openRead() }
            input.use {
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 64 * 1024) { "storage quota exceeded" }
                    output.write(buffer, 0, count)
                }
                val raw = output.toString("UTF-8")
                PluginJson.check(raw, 64 * 1024)
                JSONObject(raw)
            }
        } catch (_: FileNotFoundException) { JSONObject() }
        require(previous.length() <= 64) { "storage quota exceeded" }
        previous.keys().forEach { storedKey ->
            require(storedKey.matches(Regex("[A-Za-z0-9_.-]{1,128}")) && previous.opt(storedKey) is String) { "invalid stored value" }
            require(previous.getString(storedKey).toByteArray(Charsets.UTF_8).size <= 16 * 1024) { "storage value too large" }
        }
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
        legacy.delete() // Only after the new atomic write succeeds.
        JSONObject.NULL
    }

    companion object {
        private val lock = Any()
        private fun digest(value: String) = PluginPackageTrust.sha256(value.toByteArray(Charsets.UTF_8))
        fun clearAccount(context: Context, source: SourceId, accountId: String) = synchronized(lock) {
            val storage = PluginStorage(context, source, accountId)
            storage.file.delete(); storage.legacy.delete()
        }
        fun clear(context: Context, source: SourceId, accountIds: List<String>) = synchronized(lock) {
            require(source.builtIn == null)
            val storage = PluginStorage(context, source)
            check(!storage.root.exists() || storage.root.deleteRecursively()) { "cannot clear plugin storage" }
            (listOf<String?>(null) + accountIds).forEach { account -> PluginStorage(context, source, account).legacy.delete() }
        }
    }
}
