package com.lladlam.melox.data.db.migration

import android.content.Context
import android.util.Log
import com.lladlam.melox.data.db.MeloXDatabase
import com.lladlam.melox.data.db.entity.DownloadEntity
import com.lladlam.melox.data.db.entity.DownloadPlaylistEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * One-shot migration from the legacy index.json file into Room.
 *
 * Call [migrateIfNeeded] early in app startup (e.g. from Application.onCreate or a Hilt initializer).
 * The migration is idempotent: if the Room database already contains data or the index file is
 * missing, it returns immediately.
 */
object StorageMigration {
    private const val TAG = "StorageMigration"

    suspend fun migrateIfNeeded(context: Context) {
        withContext(Dispatchers.IO) {
            val db = MeloXDatabase.create(context)
            val downloadDao = db.downloadDao()

            // Skip if Room already has data (idempotent).
            if (downloadDao.getAll().isNotEmpty()) {
                db.close()
                return@withContext
            }

            val directory = File(context.filesDir, "melox_downloads")
            val indexFile = File(directory, "index.json")
            if (!indexFile.isFile) {
                db.close()
                return@withContext
            }

            val raw = runCatching { indexFile.readText() }.getOrNull().orEmpty()
            if (raw.isBlank()) {
                db.close()
                return@withContext
            }

            runCatching {
                val array = JSONArray(raw)
                val downloads = mutableListOf<DownloadEntity>()
                val playlistRefs = mutableListOf<DownloadPlaylistEntity>()

                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val songId = obj.optLong("songId", -1L)
                    if (songId <= 0L) continue

                    downloads += DownloadEntity(
                        songId = songId,
                        name = obj.optString("name"),
                        artists = obj.optString("artists"),
                        album = obj.optString("album"),
                        artworkUrl = obj.optString("artworkUrl").takeIf(String::isNotBlank),
                        durationMs = obj.optLong("durationMs", 0L),
                        quality = obj.optString("quality", "Standard"),
                        fileName = obj.optString("fileName"),
                        byteCount = obj.optLong("byteCount", 0L),
                        bitrate = obj.optInt("bitrate", 0).takeIf { it > 0 },
                        format = obj.optString("format").takeIf(String::isNotBlank),
                        downloadedAt = obj.optLong("downloadedAt", 0L),
                        artworkFileName = obj.optString("artworkFileName").takeIf(String::isNotBlank),
                        lyricsFileName = obj.optString("lyricsFileName").takeIf(String::isNotBlank),
                    )

                    val refsArray = obj.optJSONArray("sourcePlaylists") ?: JSONArray()
                    for (j in 0 until refsArray.length()) {
                        val ref = refsArray.optJSONObject(j) ?: continue
                        playlistRefs += DownloadPlaylistEntity(
                            playlistId = ref.optLong("id", -1L),
                            songId = songId,
                            playlistName = ref.optString("name"),
                            playlistArtworkUrl = ref.optString("artworkUrl").takeIf(String::isNotBlank),
                        )
                    }
                }

                downloadDao.insertAll(downloads)
                if (playlistRefs.isNotEmpty()) {
                    downloadDao.insertPlaylistRefs(playlistRefs)
                }

                // Rename index file as backup (not delete — safety net).
                val backup = File(directory, "index.json.migrated_to_room")
                if (backup.exists()) backup.delete()
                indexFile.renameTo(backup)

                Log.i(TAG, "Migrated ${downloads.size} downloads, ${playlistRefs.size} playlist refs from index.json to Room")
            }.onFailure { error ->
                Log.e(TAG, "Failed to migrate index.json to Room", error)
            }

            db.close()
        }
    }
}
