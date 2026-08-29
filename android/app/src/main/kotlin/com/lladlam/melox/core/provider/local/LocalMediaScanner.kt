package com.lladlam.melox.core.provider.local

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.io.File

class LocalMediaScanner(
    private val context: Context,
    private val repository: LocalMusicRepository = LocalMusicRepository(context),
) {
    suspend fun scanAll(): List<LocalTrackRecord> = withContext(Dispatchers.IO) {
        val records = buildList {
            addAll(scanMediaStore())
            repository.scanRoots().forEach { root -> addAll(scanTree(root)) }
        }.distinctBy(LocalTrackRecord::fileKey)
        repository.replaceTracks(records)
        records
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

    private fun scanTree(root: LocalScanRoot): List<LocalTrackRecord> {
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
        val result = mutableListOf<LocalTrackRecord>()
        val resolver = context.contentResolver
        runCatching {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val documentId = cursor.string(projection, DocumentsContract.Document.COLUMN_DOCUMENT_ID) ?: continue
                    val name = cursor.string(projection, DocumentsContract.Document.COLUMN_DISPLAY_NAME).orEmpty()
                    val mime = cursor.string(projection, DocumentsContract.Document.COLUMN_MIME_TYPE).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        result += scanTree(LocalScanRoot(
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString(),
                            root.persistedFlags,
                        ))
                    } else if (isAudio(name, mime)) {
                        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                        readMetadata(documentUri, name, mime, root.uri)?.let(result::add)
                    }
                }
            }
        }
        return result
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
                    artworkUri = readEmbeddedArtwork(contentUri, contentUri.toString()),
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
                artworkUri = readEmbeddedArtwork(uri, uri.toString()),
            )
        }.getOrNull().also { retriever.release() }
    }

    private fun isAudio(name: String, mimeType: String): Boolean =
        mimeType.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in setOf("mp3", "m4a", "flac", "ogg", "opus", "wav", "aac", "ape", "amr")

    private fun readEmbeddedArtwork(uri: Uri, key: String): String? {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return@runCatching null
            val file = File(context.filesDir, "local-artwork-${stableKey(key)}.jpg")
            file.outputStream().use { it.write(bytes) }
            Uri.fromFile(file).toString()
        }.getOrNull().also { retriever.release() }
    }

    private fun stableKey(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun Cursor.string(projection: Array<String>, column: String): String? =
        getColumnIndex(column).takeIf { it >= 0 }?.let(::getString)

    private fun Cursor.long(projection: Array<String>, column: String): Long =
        getColumnIndex(column).takeIf { it >= 0 }?.let(::getLong) ?: 0L
}
