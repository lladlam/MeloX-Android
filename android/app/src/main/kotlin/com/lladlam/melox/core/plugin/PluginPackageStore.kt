package com.lladlam.melox.core.plugin

import android.content.Context
import android.util.AtomicFile
import com.lladlam.melox.core.music.model.SourceId
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

interface PluginRepository {
    fun install(archive: ByteArray): PluginManifest
    fun read(sourceId: SourceId): PluginArchiveReader.Package?
    fun update(sourceId: SourceId, archive: ByteArray, prepare: (PluginArchiveReader.Package) -> Unit): PluginManifest
    fun installed(): List<PluginPackageStore.Installed>
    fun enabledSources(): Set<SourceId>
    fun setEnabledSources(sources: Set<SourceId>)
    fun uninstall(sourceId: SourceId)
}

/** Stores validated archives privately. Installation does not execute or enable code. */
class PluginPackageStore(context: Context) : PluginRepository {
    private val directory = File(context.applicationContext.filesDir, "melox-plugin-packages-v1")

    /** New installations remain disabled until PluginManager successfully prepares a provider. */
    override fun install(archive: ByteArray): PluginManifest = synchronized(lock) {
        val parsed = PluginArchiveReader.read(archive)
        check(directory.isDirectory || directory.mkdirs()) { "cannot create plugin package directory" }
        val file = fileFor(parsed.manifest.sourceId)
        require(!file.exists() && !File(file.path + ".bak").exists()) { "plugin already installed" }
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(archive)
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
        parsed.manifest
    }

    /** Validate and prepare the candidate before committing it; AtomicFile retains the old bytes on failure. */
    override fun update(sourceId: SourceId, archive: ByteArray, prepare: (PluginArchiveReader.Package) -> Unit): PluginManifest = synchronized(lock) {
        val old = read(sourceId) ?: error("plugin is not installed")
        val candidate = PluginArchiveReader.read(archive)
        require(candidate.manifest.sourceId == sourceId) { "plugin identity cannot change during upgrade" }
        require(PluginVersion.compare(candidate.manifest.version, old.manifest.version) > 0) { "upgrade requires a newer version" }
        prepare(candidate)
        val atomic = AtomicFile(fileFor(sourceId))
        val output = atomic.startWrite()
        try {
            output.write(archive)
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
        candidate.manifest
    }

    data class Installed(val sourceId: SourceId?, val manifest: PluginManifest?, val error: String?)

    /** Corrupt packages are reported individually and never prevent restoring other plugins. */
    override fun installed(): List<Installed> = synchronized(lock) {
        val names = directory.listFiles().orEmpty().map { it.name.removeSuffix(".bak") }
            .filter { it.matches(Regex("[a-f0-9]{64}\\.plugin")) }.distinct().sorted()
        names.map { name ->
            try {
                val bytes = AtomicFile(File(directory, name)).openRead().use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(out.size() + count <= MAX_ARCHIVE_BYTES) { "stored package too large" }
                        out.write(buffer, 0, count)
                    }
                    out.toByteArray()
                }
                val manifest = PluginArchiveReader.read(bytes).manifest
                require(fileFor(manifest.sourceId).name == name) { "stored plugin identity mismatch" }
                Installed(manifest.sourceId, manifest, null)
            } catch (error: Exception) {
                Installed(null, null, error.javaClass.simpleName)
            }
        }
    }

    override fun enabledSources(): Set<SourceId> = synchronized(lock) {
        val bytes = try { AtomicFile(File(directory, "enabled.json")).readFully() }
            catch (_: FileNotFoundException) { return@synchronized emptySet() }
        require(bytes.size <= 64 * 1024) { "plugin state too large" }
        val values = org.json.JSONArray(bytes.toString(Charsets.UTF_8))
        buildSet {
            for (index in 0 until values.length()) {
                val source = SourceId.parse(values.getString(index)) ?: error("invalid stored source")
                require(source.value.startsWith("plugin:")) { "invalid stored plugin source" }
                add(source)
            }
        }
    }

    override fun setEnabledSources(sources: Set<SourceId>): Unit = synchronized(lock) {
        require(sources.all { it.value.startsWith("plugin:") }) { "invalid plugin sources" }
        check(directory.isDirectory || directory.mkdirs()) { "cannot create plugin package directory" }
        val bytes = org.json.JSONArray(sources.map { it.value }.sorted()).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 64 * 1024) { "plugin state too large" }
        val atomic = AtomicFile(File(directory, "enabled.json"))
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }

    /** Revalidates stored content before exposing it, including after process restart. */
    override fun read(sourceId: SourceId): PluginArchiveReader.Package? = synchronized(lock) {
        require(sourceId.value.startsWith("plugin:")) { "not a plugin source" }
        val atomic = AtomicFile(fileFor(sourceId))
        val bytes = try {
            atomic.openRead().use { input ->
                // Keep even locally corrupted packages within the archive size limit.
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_ARCHIVE_BYTES) { "stored package too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (_: FileNotFoundException) {
            return@synchronized null
        }
        PluginArchiveReader.read(bytes).also {
            require(it.manifest.sourceId == sourceId) { "stored plugin identity mismatch" }
        }
    }

    /** Removes only the installed package, not the user's songs or listening history. */
    override fun uninstall(sourceId: SourceId): Unit = synchronized(lock) {
        require(sourceId.value.startsWith("plugin:")) { "not a plugin source" }
        AtomicFile(fileFor(sourceId)).delete()
    }

    private fun fileFor(sourceId: SourceId): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(sourceId.value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(directory, "$digest.plugin")
    }

    private companion object {
        const val MAX_ARCHIVE_BYTES = 4 * 1024 * 1024
        val lock = Any()
    }
}
