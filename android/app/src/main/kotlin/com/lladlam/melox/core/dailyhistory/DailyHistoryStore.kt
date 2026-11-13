package com.lladlam.melox.core.dailyhistory

import android.content.Context
import com.lladlam.melox.core.model.SearchSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** One archived daily-recommendation snapshot for a single local day. */
data class DailyHistoryEntry(
    val date: String,
    val userId: Long?,
    val songs: List<SearchSong>,
)

/**
 * Directory key for an account. Only the cookie participates: the settings detail
 * page builds a fresh NeteaseSessionStore whose `profile` is initially null, so a
 * userId-derived key would split one account's history into two directories.
 * [userId] is accepted for call-site symmetry but deliberately not used.
 */
@Suppress("UNUSED_PARAMETER")
internal fun accountKeyOf(cookie: String, userId: Long? = null): String {
    val seed = cookie.ifBlank { "anonymous" }
    return MessageDigest.getInstance("SHA-256")
        .digest(seed.toByteArray(Charsets.UTF_8))
        .take(8)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

/** Date keys (ISO yyyy-MM-dd, descending) that must be deleted to keep at most [limit] records. */
internal fun pruneKeys(keys: List<String>, limit: Int = 30): Set<String> =
    keys.distinct().sortedDescending().drop(limit).toSet()

/** Visible date keys after writing [written]; mirrors the store's write-then-prune behaviour. */
internal fun retainedKeysAfterWrite(
    existing: List<String>,
    written: String,
    limit: Int = 30,
): List<String> = (existing + written).distinct().sortedDescending().take(limit)

/**
 * Persistent archive of daily recommendations.
 *
 * Layout: `<filesDir>/melox_daily_history/<accountKey>/<yyyy-MM-dd>.json`.
 * One file per day makes "last write wins" an atomic, idempotent overwrite, and
 * makes the 30-record cap a simple file deletion instead of a full index rewrite.
 */
object DailyHistoryStore {

    private const val DIRECTORY = "melox_daily_history"
    private const val LIMIT = 30
    private val dateFile = Regex("(\\d{4}-\\d{2}-\\d{2})\\.json")
    private val mutex = Mutex()

    /** Overwrites the record for [dateKey]. Empty lists are ignored so a failed load never erases a day. */
    suspend fun archive(
        context: Context,
        accountKey: String,
        dateKey: String,
        songs: List<SearchSong>,
        userId: Long? = null,
    ): Boolean {
        if (songs.isEmpty() || MeloXDailyClock.parseKey(dateKey) == null) return false
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val dir = accountDir(context, accountKey)
                    dir.mkdirs()
                    writeAtomic(File(dir, "$dateKey.json"), encodeEntry(dateKey, userId, songs))
                    prune(dir)
                }.isSuccess
            }
        }
    }

    /** All archived day keys for this account, newest first. */
    suspend fun listDateKeys(context: Context, accountKey: String): List<String> = mutex.withLock {
        withContext(Dispatchers.IO) { dateKeysIn(accountDir(context, accountKey)) }
    }

    suspend fun read(context: Context, accountKey: String, dateKey: String): DailyHistoryEntry? =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val file = File(accountDir(context, accountKey), "$dateKey.json")
                if (file.isFile) decodeEntry(file.readText()) else null
            }
        }

    private fun accountDir(context: Context, accountKey: String): File =
        File(context.applicationContext.filesDir, "$DIRECTORY/$accountKey")

    private fun dateKeysIn(dir: File): List<String> = dir.listFiles()
        ?.mapNotNull { file -> dateFile.matchEntire(file.name)?.groupValues?.get(1) }
        .orEmpty()
        .distinct()
        .sortedDescending()

    private fun prune(dir: File) {
        pruneKeys(dateKeysIn(dir), LIMIT).forEach { key ->
            File(dir, "$key.json").delete()
        }
    }

    private fun writeAtomic(file: File, encoded: String) {
        file.parentFile?.mkdirs()
        val temporary = File.createTempFile("${file.name}.", ".tmp", file.parentFile)
        try {
            temporary.writeText(encoded)
            // renameTo() replaces the destination on Android/Linux. If replacement
            // fails, keep the previous complete file instead of truncating it.
            if (!temporary.renameTo(file)) {
                throw IOException("cannot replace daily history: ${file.name}")
            }
        } finally {
            temporary.delete()
        }
    }

    private fun encodeEntry(date: String, userId: Long?, songs: List<SearchSong>): String =
        JSONObject()
            .put("date", date)
            .put("userId", userId ?: -1L)
            .put("songs", JSONArray().apply {
                songs.forEach { song ->
                    put(
                        JSONObject()
                            .put("id", song.id)
                            .put("name", song.name)
                            .put("artists", song.artists)
                            .put("album", song.album)
                            .put("albumId", song.albumId ?: -1L)
                            .put("artistId", song.artistId ?: -1L)
                            .put("artworkUrl", song.artworkUrl.orEmpty())
                            .put("durationMs", song.durationMs),
                    )
                }
            })
            .toString()

    private fun decodeEntry(raw: String): DailyHistoryEntry? = runCatching {
        val value = JSONObject(raw)
        val songs = value.optJSONArray("songs") ?: JSONArray()
        DailyHistoryEntry(
            date = value.optString("date"),
            userId = value.optLong("userId", -1L).takeIf { it > 0L },
            songs = buildList {
                for (index in 0 until songs.length()) {
                    val song = songs.optJSONObject(index) ?: continue
                    add(
                        SearchSong(
                            id = song.optLong("id"),
                            name = song.optString("name"),
                            artists = song.optString("artists"),
                            album = song.optString("album"),
                            albumId = song.optLong("albumId", -1L).takeIf { it > 0L },
                            artistId = song.optLong("artistId", -1L).takeIf { it > 0L },
                            artworkUrl = song.optString("artworkUrl").takeIf(String::isNotBlank),
                            durationMs = song.optLong("durationMs"),
                        ),
                    )
                }
            },
        )
    }.getOrNull()
}
