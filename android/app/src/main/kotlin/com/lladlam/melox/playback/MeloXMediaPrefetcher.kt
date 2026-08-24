package com.lladlam.melox.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Fills the playback cache and materializes the same bytes for native analysis. */
@OptIn(UnstableApi::class)
internal class MeloXMediaPrefetcher(
    private val analysisDirectory: File,
    private val dataSourceFactory: CacheDataSource.Factory,
    private val cache: SimpleCache,
) {
    init {
        analysisDirectory.mkdirs()
    }

    fun cache(mediaItem: MediaItem) {
        CacheWriter(
            dataSourceFactory.createDataSource(),
            dataSpec(mediaItem),
            null,
            null,
        ).cache()
    }

    suspend fun materialize(mediaItem: MediaItem): File {
        val target = analysisFile(mediaItem)
        if (target.isFile && target.length() > 0L) {
            target.setLastModified(System.currentTimeMillis())
            return target
        }
        val temporary = File(target.parentFile, "${target.name}.part")
        temporary.delete()
        val source = dataSourceFactory.createDataSource()
        try {
            source.open(dataSpec(mediaItem))
            temporary.outputStream().buffered().use { output ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer, 0, buffer.size)
                    if (read == C.RESULT_END_OF_INPUT) break
                    if (read > 0) output.write(buffer, 0, read)
                }
            }
        } finally {
            source.close()
        }
        check(temporary.length() > 0L) { "AutoMix: cached source is empty" }
        if (!temporary.renameTo(target)) {
            target.delete()
            check(temporary.renameTo(target)) { "AutoMix: unable to publish cached analysis source" }
        }
        trimAnalysisFiles(keep = 6)
        return target
    }

    fun clearAnalysisFiles() {
        analysisDirectory.listFiles()?.forEach(File::delete)
    }

    fun remove(mediaItem: MediaItem) {
        cache.removeResource(dataSpec(mediaItem).key ?: dataSpec(mediaItem).uri.toString())
    }

    private fun dataSpec(mediaItem: MediaItem): DataSpec {
        val configuration = requireNotNull(mediaItem.localConfiguration) {
            "AutoMix: media item has no local configuration"
        }
        return DataSpec.Builder()
            .setUri(configuration.uri)
            .setKey(configuration.customCacheKey ?: configuration.uri.toString())
            .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
            .build()
    }

    private fun analysisFile(mediaItem: MediaItem): File {
        val configuration = requireNotNull(mediaItem.localConfiguration)
        val identity = "${mediaItem.mediaId}|${configuration.uri}|${configuration.customCacheKey.orEmpty()}"
        return File(analysisDirectory, "${identity.hashCode().toUInt().toString(16)}.media")
    }

    private fun trimAnalysisFiles(keep: Int) {
        analysisDirectory.listFiles()
            ?.filter(File::isFile)
            ?.sortedByDescending(File::lastModified)
            ?.drop(keep)
            ?.forEach(File::delete)
    }
}
