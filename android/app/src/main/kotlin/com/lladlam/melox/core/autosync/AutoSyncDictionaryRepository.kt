package com.lladlam.melox.core.autosync

import android.content.Context
import android.util.Log
import com.lladlam.melox.core.network.MeloXGitHubRouting
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Installs the OpenJTalk NAIST dictionary used by the Japanese G2P frontend.
 *
 * The dictionary is far too large to bundle (~24 MB compressed, ~102 MB on
 * disk) and is licensed separately by NAIST, so it is downloaded only after an
 * explicit user confirmation, from a pinned GitHub release, and published with
 * the same atomic swap used for model packs: a failed, cancelled or truncated
 * download can never damage an existing installation.
 */
internal class AutoSyncDictionaryRepository(
    private val context: Context,
    private val routing: MeloXGitHubRouting,
) {

    data class Spec(
        val name: String,
        val url: String,
        val bytes: Long,
        val sha256: String,
        val archiveRoot: String,
        val installedBytes: Long,
        val license: String,
        val licenseUrl: String,
    )

    suspend fun install(onProgress: ((Float) -> Unit)? = null): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val root = root(context)
            val staging = File(root, "g2p.staging")
            staging.deleteRecursively()
            root.mkdirs()
            check(staging.mkdirs()) { "cannot create staging directory" }
            try {
                checkFreeSpace(root, archive.bytes + archive.installedBytes)
                val tarball = File(staging, "dict.tar.gz")
                downloadVerified(tarball, onProgress)
                val dictionary = File(staging, archive.archiveRoot)
                extract(tarball, staging)
                tarball.delete()
                check(dictionary.isDirectory) { "archive did not contain ${archive.archiveRoot}" }
                verifyDictionary(dictionary)
                val marker = File(staging, "install.json")
                marker.writeText(
                    buildString {
                        append("{\"version\":\"").append(archive.name).append("\",")
                        append("\"sha256\":\"").append(archive.sha256).append("\",")
                        append("\"bytes\":").append(archive.installedBytes).append(",")
                        append("\"license\":\"").append(archive.license.replace("\"", "'")).append("\",")
                        append("\"licenseUrl\":\"").append(archive.licenseUrl).append("\"}")
                    },
                )
                publish(staging)
                onProgress?.invoke(1f)
                dictionaryDirectory(context)
                    ?: throw IOException("dictionary missing after publish")
            } catch (error: Throwable) {
                staging.deleteRecursively()
                throw error
            }
        }.onFailure { Log.w(TAG, "dictionary install failed", it) }
    }

    fun isInstalled(): Boolean = dictionaryDirectory(context) != null

    fun delete() {
        // Only the dictionary lives under these names. The parent directory
        // also holds downloaded model packs and must not be wiped.
        val root = root(context)
        listOf("g2p", "g2p.old", "g2p.staging").forEach { name ->
            File(root, name).deleteRecursively()
        }
    }

    fun licenseNotice(): String? {
        val directory = dictionaryDirectory(context) ?: return null
        val copying = File(directory, "COPYING")
        return runCatching { copying.readText() }.getOrNull()?.take(4_000)
    }

    private suspend fun downloadVerified(target: File, onProgress: ((Float) -> Unit)?) {
        val spec = archive
        var lastError: Throwable? = null
        for (source in routing.candidates()) {
            currentCoroutineContext().ensureActive()
            if (target.isFile) target.delete()
            val result = runCatching {
                val request = Request.Builder()
                    .url(routing.routedUrl(source, spec.url))
                    .header("User-Agent", "MeloX-Android")
                    .build()
                routing.client(source).newBuilder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .callTimeout(600, TimeUnit.SECONDS)
                    .build()
                    .newCall(request).execute().use { response ->
                        check(response.isSuccessful) { "HTTP ${response.code}" }
                        val digest = MessageDigest.getInstance("SHA-256")
                        var written = 0L
                        response.body.byteStream().use { input ->
                            target.outputStream().buffered(1 shl 16).use { output ->
                                val buffer = ByteArray(1 shl 16)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    written += read
                                    check(written <= spec.bytes) { "archive larger than declared" }
                                    digest.update(buffer, 0, read)
                                    output.write(buffer, 0, read)
                                    onProgress?.invoke((written.toFloat() / spec.bytes).coerceIn(0f, 1f))
                                }
                            }
                        }
                        check(written == spec.bytes) { "archive size mismatch: $written != ${spec.bytes}" }
                        val actual = digest.digest().joinToString("") { "%02x".format(it) }
                        check(actual.equals(spec.sha256, ignoreCase = true)) { "dictionary sha256 mismatch" }
                    }
            }
            result.onSuccess { return }
            lastError = result.exceptionOrNull()
            if (lastError is kotlinx.coroutines.CancellationException) throw lastError
            Log.i(TAG, "dictionary download via $source failed: ${lastError?.message}")
        }
        throw lastError ?: IOException("no GitHub route available for the dictionary")
    }

    private suspend fun extract(archive: File, staging: File) =
        AutoSyncTarExtractor.extract(archive, staging)

    private fun verifyDictionary(directory: File) {
        listOf("sys.dic", "matrix.bin", "char.bin", "unk.dic").forEach { required ->
            check(File(directory, required).isFile) { "dictionary is missing $required" }
        }
        check(File(directory, "COPYING").isFile) { "dictionary licence file is missing" }
        val total = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        check(total >= archive.installedBytes / 2) { "dictionary looks truncated ($total bytes)" }
    }

    private fun checkFreeSpace(directory: File, needed: Long) {
        val usable = runCatching { directory.usableSpace }.getOrDefault(Long.MAX_VALUE)
        check(usable > needed + 64L * 1024 * 1024) {
            "not enough free space for the dictionary: need about ${needed / 1024 / 1024} MB"
        }
    }

    /** Atomic swap: the previous dictionary stays until the new one is complete. */
    private fun publish(staging: File) {
        val root = root(context)
        val live = File(root, "g2p")
        val previous = File(root, "g2p.old")
        previous.deleteRecursively()
        if (live.exists()) check(live.renameTo(previous)) { "cannot park previous dictionary" }
        if (!staging.renameTo(live)) {
            previous.renameTo(live)
            error("cannot publish dictionary")
        }
        previous.deleteRecursively()
    }

    companion object {
        private const val TAG = "MeloXAutoSync"

        /** Pinned OpenJTalk dictionary, NAIST modified BSD (see [licenseUrl]). */
        val archive = Spec(
            name = "open_jtalk_dic_utf_8-1.11",
            url = "https://github.com/r9y9/open_jtalk/releases/download/v1.11.1/open_jtalk_dic_utf_8-1.11.tar.gz",
            bytes = 23_646_843L,
            sha256 = "fe6ba0e43542cef98339abdffd903e062008ea170b04e7e2a35da805902f382a",
            archiveRoot = "open_jtalk_dic_utf_8-1.11",
            installedBytes = 107_300_000L,
            license = "NAIST modified BSD (Open JTalk dictionary)",
            licenseUrl = "https://github.com/r9y9/open_jtalk",
        )

        private fun root(context: Context): File = File(context.filesDir, "autosync")

        /** Installed dictionary directory, or `null` when it is not usable. */
        fun dictionaryDirectory(context: Context): File? {
            val directory = File(File(root(context), "g2p"), archive.archiveRoot)
            val complete = File(directory, "sys.dic").isFile && File(directory, "matrix.bin").isFile &&
                File(directory, "char.bin").isFile && File(directory, "unk.dic").isFile
            return directory.takeIf { complete }
        }

        /** Approximate on-disk size of an installed dictionary, for the UI. */
        fun installedBytes(context: Context): Long {
            val directory = dictionaryDirectory(context) ?: return 0L
            return directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }
}
