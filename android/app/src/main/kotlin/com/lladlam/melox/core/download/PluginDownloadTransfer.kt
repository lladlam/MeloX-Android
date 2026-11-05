package com.lladlam.melox.core.download

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Progressive downloads only. Resume requires an unchanged host scope and a strong HTTP ETag.
 * Only the ETag, expected length and opaque scope digest persist; never resolved URLs or headers.
 */
internal object PluginDownloadTransfer {
    class AccessRejected(val status: Int) : IOException("AUTH_EXPIRED")
    private const val MAX_BYTES = 512L * 1024 * 1024
    private const val MAX_STAGING_BYTES = 1024L * 1024 * 1024
    private val budgetLock = Any()
    private val reservations = mutableMapOf<String, Long>()
    private data class Resume(val scope: String, val etag: String, val total: Long)

    fun run(client: OkHttpClient, request: Request, destination: File, scope: () -> String?, active: () -> Boolean, discard: () -> Boolean) {
        val part = File(destination.path + ".part")
        val metadata = AtomicFile(File(destination.path + ".resume.json"))
        val initialScope = scope()
        val saved = if (initialScope == null) null else read(metadata)?.takeIf {
            it.scope == initialScope && it.total in 1..MAX_BYTES && part.isFile && part.length() in 1L until it.total
        }
        if (saved == null) { check(!part.exists() || part.delete()); metadata.delete() }
        val offset = if (saved == null) 0 else part.length()
        val builder = request.newBuilder().removeHeader("Range").removeHeader("If-Range").header("Accept-Encoding", "identity")
        if (saved != null) builder.header("Range", "bytes=$offset-").header("If-Range", saved.etag)
        var resumable = false
        var reserved = false
        try {
            check(active() && scope() == initialScope) { "SOURCE_UNAVAILABLE" }
            client.newCall(builder.build()).execute().use { response ->
                if (response.code in setOf(401, 403)) throw AccessRejected(response.code)
                if (response.code !in setOf(200, 206)) throw IOException("NETWORK_REQUEST_FAILED")
                require(response.header("Content-Encoding").let { it == null || it.equals("identity", true) }) { "INVALID_ARGUMENT" }
                val body = requireNotNull(response.body)
                val etag = response.header("ETag")?.takeIf(::strongEtag)
                val total: Long
                val append: Boolean
                if (response.code == 206) {
                    require(saved != null && etag == saved.etag) { "INVALID_ARGUMENT" }
                    val range = requireNotNull(Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(response.header("Content-Range").orEmpty())) { "INVALID_ARGUMENT" }
                    val start = range.groupValues[1].toLong()
                    val end = range.groupValues[2].toLong()
                    total = range.groupValues[3].toLong()
                    require(start == offset && total == saved.total && end == total - 1 && start <= end) { "INVALID_ARGUMENT" }
                    require(body.contentLength() == -1L || body.contentLength() == total - offset) { "INVALID_ARGUMENT" }
                    append = true
                } else {
                    // If-Range failed or the server ignores Range: start over, never concatenate.
                    total = body.contentLength()
                    append = false
                }
                require(total == -1L || total in 1..MAX_BYTES) { "INVALID_ARGUMENT" }
                synchronized(budgetLock) {
                    require(part.absolutePath !in reservations) { "RATE_LIMITED" }
                    val retained = part.parentFile!!.listFiles().orEmpty().asSequence()
                        .filter { it.isFile && it.name.endsWith(".part") && it.absolutePath != part.absolutePath && it.absolutePath !in reservations }
                        .sumOf { it.length() }
                    val needed = if (total > 0) total else MAX_BYTES
                    require(retained + reservations.values.sum() + needed <= MAX_STAGING_BYTES) { "DOWNLOAD_STAGING_FULL" }
                    reservations[part.absolutePath] = needed
                    reserved = true
                }
                var received = if (append) offset else 0L
                body.byteStream().use { input ->
                    java.io.FileOutputStream(part, append).use { output ->
                        // A 200 replacement must durably discard the old bytes BEFORE recording
                        // its new validator; death between these operations cannot mix versions.
                        if (!append) output.fd.sync()
                        if (initialScope != null && etag != null && total > 0) {
                            write(metadata, Resume(initialScope, etag, total))
                            resumable = true
                        } else metadata.delete()
                        val buffer = ByteArray(8192)
                        while (true) {
                            check(active() && scope() == initialScope && !Thread.currentThread().isInterrupted) { "SOURCE_UNAVAILABLE" }
                            val count = input.read(buffer); if (count < 0) break
                            received += count
                            require(received <= MAX_BYTES && (total < 0 || received <= total)) { "INVALID_ARGUMENT" }
                            output.write(buffer, 0, count)
                        }
                        require(received > 0 && (total < 0 || received == total)) { "INVALID_ARGUMENT" }
                        output.fd.sync()
                    }
                }
                check(active() && scope() == initialScope) { "SOURCE_UNAVAILABLE" }
                check(part.renameTo(destination)) { "PERSISTENCE_FAILED" }
                metadata.delete()
            }
        } catch (error: Throwable) {
            // Policy/shape failures invalidate the partial representation. Network interruption may
            // retain it, but a later response must revalidate both scope and exact byte range/ETag.
            if (!resumable || error is IllegalArgumentException || runCatching { scope() }.getOrNull() != initialScope || discard()) {
                part.delete(); metadata.delete()
            }
            throw error
        } finally {
            if (reserved) synchronized(budgetLock) { reservations.remove(part.absolutePath) }
        }
    }

    fun clear(destination: File) {
        File(destination.path + ".part").delete()
        AtomicFile(File(destination.path + ".resume.json")).delete()
    }

    private fun strongEtag(value: String) = value.length in 2..512 && value.first() == '"' && value.last() == '"' &&
        value.substring(1, value.lastIndex).all { it.code in 0x21..0x7e && it != '"' }

    private fun read(file: AtomicFile): Resume? = runCatching {
        file.openRead().use { input ->
            val bytes = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                require(bytes.size() + count <= 4096)
                bytes.write(buffer, 0, count)
            }
            val raw = bytes.toString("UTF-8")
            com.lladlam.melox.core.plugin.PluginJson.check(raw, 4096)
            val value = JSONObject(raw)
            Resume(value.getString("scope"), value.getString("etag").also { require(strongEtag(it)) }, value.getLong("total"))
        }
    }.getOrNull()

    private fun write(file: AtomicFile, resume: Resume) {
        val bytes = JSONObject().put("scope", resume.scope).put("etag", resume.etag).put("total", resume.total).toString().toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Throwable) { file.failWrite(output); throw error }
    }
}
