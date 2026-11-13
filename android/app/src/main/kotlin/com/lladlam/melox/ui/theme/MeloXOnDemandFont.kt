package com.lladlam.melox.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface as ComposeTypeface
import com.lladlam.melox.core.network.MeloXHttpClient
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Optional MiSans variable font, fetched on demand from a GitHub release.
 *
 * The APK ships no CJK font: the system family is the global default and the
 * custom family is only materialised when the user explicitly asks for it.
 * Downloaded bytes are verified against pinned SHA-256 digests before use.
 */
internal object MeloXOnDemandFont {
    const val DownloadUrl =
        "https://github.com/adivenxnataly/MiSans/releases/download/1.0-1/MiSansVF.zip"
    private const val ArchiveSha256 =
        "cc45fe93780ac931d1a19156be5f5710f8b46990f1b221c7f1a2da98a12906f7"
    private const val FontSha256 =
        "5daf8d5447bfd423cfdec94a0e07c53b205892223ccc6ea21b7b8a37248b44d9"
    private const val FontEntry = "MiSans/MiSansVF.ttf"
    private const val FontFileName = "MiSansVF.ttf"
    const val DownloadBytes = 20_787_749L

    @Volatile private var familyCache: FontFamily? = null
    @Volatile private var typefaceCache: Typeface? = null

    /** Serialises downloads so two taps cannot write the same temporary file. */
    private val downloadMutex = Mutex()

    /** Bumped whenever the provisioned font changes so Compose can refresh. */
    var revision by mutableIntStateOf(0)
        private set

    /** Font file previously provisioned by [ensureDownloaded], if any. */
    fun fontFile(context: Context): File? {
        val file = File(fontDir(context), FontFileName)
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    /** Compose family for the provisioned font; null when it was never downloaded. */
    fun fontFamily(context: Context): FontFamily? {
        familyCache?.let { return it }
        val file = fontFile(context) ?: return null
        val typeface = runCatching { Typeface.createFromFile(file) }.getOrNull() ?: return null
        typefaceCache = typeface
        return runCatching { FontFamily(ComposeTypeface(typeface)) }
            .getOrNull()
            ?.also { familyCache = it }
    }

    /** Android typeface for Canvas based lyric rendering; null when unavailable. */
    fun typeface(context: Context): Typeface? {
        typefaceCache?.let { return it }
        val file = fontFile(context) ?: return null
        return runCatching { Typeface.createFromFile(file) }
            .getOrNull()
            ?.also { typefaceCache = it }
    }

    /**
     * Downloads and verifies the font archive once, then extracts the variable
     * font into the app-private font directory.
     */
    suspend fun ensureDownloaded(
        context: Context,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null,
    ): Result<File> {
        val result = downloadMutex.withLock { download(context, onProgress) }
        if (result.isSuccess) {
            // The caches are invalidated and the revision is bumped on the
            // caller's thread (the UI scope), so Compose observers are notified
            // on the main thread.
            familyCache = null
            typefaceCache = null
            revision++
        }
        return result
    }

    private suspend fun download(
        context: Context,
        onProgress: ((downloaded: Long, total: Long) -> Unit)?,
    ): Result<File> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        fontFile(app)?.let { return@withContext Result.success(it) }
        val dir = fontDir(app)
        if (!dir.exists() && !dir.mkdirs()) {
            return@withContext Result.failure(IllegalStateException("cannot create font directory"))
        }
        val archive = File(app.cacheDir, "MiSansVF.zip.part")
        val result = runCatching {
            val request = Request.Builder().url(DownloadUrl).build()
            MeloXHttpClient.shared.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body
                val total = body.contentLength().takeIf { it > 0L } ?: DownloadBytes
                body.byteStream().use { input ->
                    FileOutputStream(archive).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            onProgress?.invoke(copied, total)
                        }
                    }
                }
            }
            val archiveDigest = sha256(archive)
            if (archiveDigest != ArchiveSha256) {
                error("font archive checksum mismatch")
            }
            val extracted = File(dir, "$FontFileName.part")
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                var found = false
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == FontEntry && !entry.isDirectory) {
                        zip.copyTo(FileOutputStream(extracted))
                        found = true
                    }
                    zip.closeEntry()
                }
                if (!found) error("font archive does not contain $FontEntry")
            }
            val fontDigest = sha256(extracted)
            if (fontDigest != FontSha256) {
                extracted.delete()
                error("font checksum mismatch")
            }
            val target = File(dir, FontFileName)
            if (!extracted.renameTo(target)) {
                extracted.copyTo(target, overwrite = true)
                extracted.delete()
            }
            target
        }
        archive.delete()
        result
    }

    /** Removes the provisioned font; used when the user switches back to the system family. */
    fun delete(context: Context) {
        fontDir(context).listFiles()?.forEach { it.delete() }
        familyCache = null
        typefaceCache = null
        revision++
    }

    private fun fontDir(context: Context): File = File(context.applicationContext.filesDir, "fonts")

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
