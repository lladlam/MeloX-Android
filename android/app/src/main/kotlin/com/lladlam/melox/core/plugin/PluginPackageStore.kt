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
    fun publisherFingerprint(sourceId: SourceId): String? = null
    fun recordPublisher(plugin: PluginArchiveReader.Package) {}
    fun dismissDamaged(storageKey: String) { error("quarantine is unavailable") }
}

/** Stores validated archives privately. Installation does not execute or enable code. */
class PluginPackageStore(context: Context, private val checkpoint: (String) -> Unit = {}) : PluginRepository {
    private val directory = File(context.applicationContext.filesDir, "melox-plugin-packages-v1")

    /** New installations remain disabled until PluginManager successfully prepares a provider. */
    override fun install(archive: ByteArray): PluginManifest = synchronized(lock) {
        val parsed = PluginArchiveReader.read(archive)
        check(directory.isDirectory || directory.mkdirs()) { "cannot create plugin package directory" }
        recoverTransaction(parsed.manifest.sourceId)
        val file = fileFor(parsed.manifest.sourceId)
        require(!file.exists() && !File(file.path + ".bak").exists()) { "plugin already installed" }
        commitArchive(parsed, archive, null)
        parsed.manifest
    }

    /** Validate and prepare the candidate before committing it; AtomicFile retains the old bytes on failure. */
    override fun update(sourceId: SourceId, archive: ByteArray, prepare: (PluginArchiveReader.Package) -> Unit): PluginManifest = synchronized(lock) {
        val old = read(sourceId) ?: error("plugin is not installed")
        val candidate = PluginArchiveReader.read(archive)
        require(candidate.manifest.sourceId == sourceId) { "plugin identity cannot change during upgrade" }
        require(PluginVersion.compare(candidate.manifest.version, old.manifest.version) > 0) { "upgrade requires a newer version" }
        prepare(candidate)
        val previous = AtomicFile(fileFor(sourceId)).openRead().use { it.readBytesBounded(MAX_ARCHIVE_BYTES) }
        commitArchive(candidate, archive, previous)
        candidate.manifest
    }

    data class Installed(val sourceId: SourceId?, val manifest: PluginManifest?, val error: String?, val storageKey: String? = null)

    /** Corrupt packages are reported individually and never prevent restoring other plugins. */
    override fun installed(): List<Installed> = synchronized(lock) {
        // A first-install crash can leave a decision without an archive. Recover these too,
        // rather than waiting for a subsequent import to discover the unfinished decision.
        val interrupted = directory.listFiles().orEmpty().map { it.name.removeSuffix(".bak") }
            .filter { it.matches(Regex("[a-f0-9]{64}\\.plugin\\.transaction")) }.distinct().mapNotNull { name ->
                try {
                    val raw = AtomicFile(File(directory, name)).openRead().use { it.readBytesBounded(16 * 1024) }.toString(Charsets.UTF_8)
                    PluginJson.check(raw, 16 * 1024)
                    val source = requireNotNull(SourceId.parse(org.json.JSONObject(raw).getString("source")))
                    require(source.builtIn == null && transaction(source).baseFile.name == name)
                    recoverTransaction(source)
                    null
                } catch (error: Exception) { Installed(null, null, "TRANSACTION_${error.javaClass.simpleName}", name) }
            }
        val names = directory.listFiles().orEmpty().map { it.name.removeSuffix(".bak") }
            .filter { it.matches(Regex("[a-f0-9]{64}\\.plugin")) }.distinct().sorted()
        val result = names.map { name ->
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
                recoverTransaction(manifest.sourceId)
                Installed(manifest.sourceId, manifest, null)
            } catch (error: Exception) {
                val quarantine = File(directory, "quarantine").apply { mkdirs() }
                val original = File(directory, name)
                // Keep exact bytes for diagnosis/recovery, never execute a damaged package.
                if (original.exists()) check(original.renameTo(File(quarantine, name))) { "cannot quarantine damaged package" }
                Installed(null, null, error.javaClass.simpleName, name)
            }
        }
        val quarantined = File(directory, "quarantine").listFiles().orEmpty()
            .filter { it.name.matches(Regex("[a-f0-9]{64}\\.plugin")) }
            .map { Installed(null, null, "QUARANTINED", it.name) }
        (result + quarantined + interrupted).distinctBy { it.storageKey ?: it.sourceId?.value }
    }

    override fun dismissDamaged(storageKey: String): Unit = synchronized(lock) {
        require(storageKey.matches(Regex("[a-f0-9]{64}\\.plugin")))
        val file = File(File(directory, "quarantine"), storageKey)
        check(!file.exists() || file.delete()) { "cannot remove quarantined package" }
    }

    /** Local TOFU public-key record, retained across uninstall; never a global publisher endorsement. */
    override fun publisherFingerprint(sourceId: SourceId): String? = synchronized(lock) {
        recoverTransaction(sourceId)
        val file = AtomicFile(File(directory, "trust/${fileFor(sourceId).name}.json"))
        val raw = try { file.openRead().use { it.readBytesBounded(8192) } }
            catch (_: FileNotFoundException) { return@synchronized null }
        PluginJson.check(raw.toString(Charsets.UTF_8), 8192)
        val value = org.json.JSONObject(raw.toString(Charsets.UTF_8))
        require(value.getString("source") == sourceId.value)
        value.getString("fingerprint")
    }

    override fun recordPublisher(plugin: PluginArchiveReader.Package): Unit = synchronized(lock) {
        val parent = File(directory, "trust").apply { check(isDirectory || mkdirs()) }
        val file = AtomicFile(File(parent, "${fileFor(plugin.manifest.sourceId).name}.json"))
        val value = org.json.JSONObject().put("source", plugin.manifest.sourceId.value)
            .put("fingerprint", plugin.publisher?.fingerprint ?: PluginPackageTrust.UNSIGNED)
            .put("publicKey", plugin.publisher?.publicKey)
        val output = file.startWrite()
        try { output.write(value.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
        catch (error: Throwable) { file.failWrite(output); throw error }
    }

    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = read(buffer); if (count < 0) break
            require(output.size() + count <= limit); output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    override fun enabledSources(): Set<SourceId> = synchronized(lock) {
        val bytes = try { AtomicFile(File(directory, "enabled.json")).openRead().use { it.readBytesBounded(64 * 1024) } }
            catch (_: FileNotFoundException) { return@synchronized emptySet() }
        require(bytes.size <= 64 * 1024) { "plugin state too large" }
        PluginJson.check(bytes.toString(Charsets.UTF_8), 64 * 1024)
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
        recoverTransaction(sourceId)
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
        recoverTransaction(sourceId)
        AtomicFile(fileFor(sourceId)).delete()
    }

    /** Write-ahead decision couples an approved archive to its TOFU record after process death.
     * The journal contains no executable code or key material; the validated archive is authoritative.
     */
    private fun transaction(source: SourceId) = AtomicFile(File(directory, "${fileFor(source).name}.transaction"))
    private fun recoverTransaction(source: SourceId) {
        val journal = transaction(source)
        val raw = try { journal.openRead().use { it.readBytesBounded(16 * 1024) }.toString(Charsets.UTF_8) }
            catch (_: FileNotFoundException) { return }
        PluginJson.check(raw, 16 * 1024)
        val decision = org.json.JSONObject(raw)
        require(decision.getString("source") == source.value)
        val bytes = try { AtomicFile(fileFor(source)).openRead().use { it.readBytesBounded(MAX_ARCHIVE_BYTES) } }
            catch (_: FileNotFoundException) { null }
        val hash = bytes?.let(PluginPackageTrust::sha256)
        if (hash == decision.getString("next")) {
            val installed = PluginArchiveReader.read(requireNotNull(bytes))
            require(installed.manifest.sourceId == source)
            recordPublisher(installed)
        } else {
            require(hash == decision.optString("previous").takeIf(String::isNotEmpty)) { "package transaction cannot be recovered" }
            // Rollback may itself have been interrupted after restoring the old archive, but
            // before restoring its publisher. The journal retains that exact old TOFU record.
            if (decision.has("previousTrust")) {
                val trust = AtomicFile(File(directory, "trust/${fileFor(source).name}.json"))
                if (decision.isNull("previousTrust")) trust.delete()
                else atomicWrite(trust, decision.getString("previousTrust").toByteArray(Charsets.UTF_8))
            }
        }
        journal.delete()
    }

    private fun commitArchive(candidate: PluginArchiveReader.Package, bytes: ByteArray, previous: ByteArray?) {
        val source = candidate.manifest.sourceId
        val journal = transaction(source)
        val oldTrust = AtomicFile(File(directory, "trust/${fileFor(source).name}.json"))
        val trustBytes = try { oldTrust.openRead().use { it.readBytesBounded(8192) } } catch (_: FileNotFoundException) { null }
        val decision = org.json.JSONObject().put("source", source.value).put("next", candidate.archiveSha256)
            .put("previous", previous?.let(PluginPackageTrust::sha256).orEmpty())
            .put("previousTrust", trustBytes?.toString(Charsets.UTF_8) ?: org.json.JSONObject.NULL).toString().toByteArray(Charsets.UTF_8)
        require(decision.size <= 16 * 1024) { "package transaction too large" }
        atomicWrite(journal, decision)
        checkpoint("decision-durable")
        val archive = AtomicFile(fileFor(source))
        try {
            atomicWrite(archive, bytes)
            checkpoint("archive-durable")
            recordPublisher(candidate)
            checkpoint("publisher-durable")
            journal.delete()
        } catch (error: Throwable) {
            // A recoverable write failure leaves the old package AND publisher intact. If rollback
            // itself fails, retain the journal so restart can finish the authorized new decision.
            try {
                if (previous == null) archive.delete() else atomicWrite(archive, previous)
                checkpoint("rollback-archive-durable")
                if (trustBytes == null) oldTrust.delete() else atomicWrite(oldTrust, trustBytes)
                checkpoint("rollback-publisher-durable")
                journal.delete()
            } catch (rollback: Throwable) { error.addSuppressed(rollback) }
            throw error
        }
    }

    private fun atomicWrite(file: AtomicFile, bytes: ByteArray) {
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Throwable) { file.failWrite(output); throw error }
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
