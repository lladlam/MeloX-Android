package com.lladlam.melox.core.provider.local

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.io.File

class LocalMediaScanner(
    private val context: Context,
    private val repository: LocalMusicRepository = LocalMusicRepository(context),
) {
    private val artworkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun scanAll(): List<LocalTrackRecord> {
        val records = coroutineScope {
            val mediaStore = async(Dispatchers.IO) { scanMediaStore() }
            val trees = repository.scanRoots().map { root ->
                async(Dispatchers.IO) { scanTree(root) }
            }
            (listOf(mediaStore.await()) + trees.awaitAll()).flatten().distinctBy(LocalTrackRecord::fileKey)
        }
        repository.replaceTracks(records)
        artworkScope.launch { extractMissingArtwork(records) }
        return records
    }

    private fun scanMediaStore(): List<LocalTrackRecord> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        return queryRecords(uri, projection, selection, null, null, null)
    }

    private suspend fun scanTree(root: LocalScanRoot, depth: Int = 0): List<LocalTrackRecord> = coroutineScope {
        if (depth > 8) return@coroutineScope emptyList()
        val treeUri = Uri.parse(root.uri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val directories = mutableListOf<LocalScanRoot>()
        val files = mutableListOf<Triple<Uri, String, String>>()
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val documentId = cursor.string(projection, DocumentsContract.Document.COLUMN_DOCUMENT_ID) ?: continue
                    val name = cursor.string(projection, DocumentsContract.Document.COLUMN_DISPLAY_NAME).orEmpty()
                    val mime = cursor.string(projection, DocumentsContract.Document.COLUMN_MIME_TYPE).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        directories += LocalScanRoot(
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString(),
                            root.persistedFlags,
                        )
                    } else if (isAudio(name, mime)) {
                        files += Triple(DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId), name, mime)
                    }
                }
            }
        }
        val fileSlots = Semaphore(4)
        val nested = directories.map { child ->
            async(Dispatchers.IO) { scanTree(child, depth + 1) }
        }
        val current = files.map { (uri, name, mime) ->
            async(Dispatchers.IO) { fileSlots.withPermit { readMetadata(uri, name, mime, root.uri) } }
        }
        current.awaitAll().filterNotNull() + nested.awaitAll().flatten()
    }

    private fun queryRecords(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        rootUri: String?,
    ): List<LocalTrackRecord> {
        val result = mutableListOf<LocalTrackRecord>()
        context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.string(projection, MediaStore.Audio.Media._ID) ?: continue
                val contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(id).build()
                val name = cursor.string(projection, MediaStore.Audio.Media.DISPLAY_NAME).orEmpty()
                result += LocalTrackRecord(
                    fileKey = stableKey(contentUri.toString()),
                    contentUri = contentUri.toString(),
                    displayName = name,
                    title = cursor.string(projection, MediaStore.Audio.Media.TITLE).orEmpty().ifBlank { name.substringBeforeLast('.') },
                    artist = cursor.string(projection, MediaStore.Audio.Media.ARTIST).orEmpty().ifBlank { "未知歌手" },
                    album = cursor.string(projection, MediaStore.Audio.Media.ALBUM).orEmpty(),
                    durationMs = cursor.long(projection, MediaStore.Audio.Media.DURATION),
                    mimeType = cursor.string(projection, MediaStore.Audio.Media.MIME_TYPE),
                    sizeBytes = cursor.long(projection, MediaStore.Audio.Media.SIZE),
                    lastModifiedMs = cursor.long(projection, MediaStore.Audio.Media.DATE_MODIFIED) * 1_000L,
                    sourceRootUri = rootUri,
                    artworkUri = null,
                )
            }
        }
        return result
    }

    private fun readMetadata(uri: Uri, displayName: String, mimeType: String, rootUri: String): LocalTrackRecord? {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, uri)
            LocalTrackRecord(
                fileKey = stableKey(uri.toString()),
                contentUri = uri.toString(),
                displayName = displayName,
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf(String::isNotBlank) ?: displayName.substringBeforeLast('.'),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf(String::isNotBlank) ?: "未知歌手",
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                mimeType = mimeType,
                sizeBytes = 0L,
                lastModifiedMs = 0L,
                sourceRootUri = rootUri,
                artworkUri = null,
            )
        }.getOrNull().also { runCatching { retriever.release() } }
    }

    private fun isAudio(name: String, mimeType: String): Boolean =
        mimeType.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in setOf("mp3", "m4a", "flac", "ogg", "opus", "wav", "aac", "ape", "amr")

    private suspend fun extractMissingArtwork(records: List<LocalTrackRecord>) {
        val missing = records.filter { it.artworkUri.isNullOrBlank() }
        if (missing.isEmpty()) return
        val slots = Semaphore(3)
        val extracted = coroutineScope {
            missing.map { record ->
                async(Dispatchers.IO) {
                    slots.withPermit {
                        val uri = runCatching { Uri.parse(record.contentUri) }.getOrNull() ?: return@withPermit null
                        readEmbeddedArtwork(uri, record.fileKey)?.let { record.fileKey to it }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (extracted.isNotEmpty()) repository.updateArtwork(extracted.toMap())
    }

    private fun readEmbeddedArtwork(uri: Uri, key: String): String? {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return@runCatching null
            val file = File(context.filesDir, "local-artwork-${stableKey(key)}.jpg")
            file.outputStream().use { it.write(bytes) }
            Uri.fromFile(file).toString()
        }.getOrNull().also { runCatching { retriever.release() } }
    }

    private fun stableKey(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun Cursor.string(projection: Array<String>, column: String): String? =
        getColumnIndex(column).takeIf { it >= 0 }?.let(::getString)

    private fun Cursor.long(projection: Array<String>, column: String): Long =
        getColumnIndex(column).takeIf { it >= 0 }?.let(::getLong) ?: 0L
}
