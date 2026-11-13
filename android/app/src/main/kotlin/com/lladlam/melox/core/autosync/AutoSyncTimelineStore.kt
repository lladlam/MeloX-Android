package com.lladlam.melox.core.autosync

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Persists generated word timelines so a song is aligned at most once.
 *
 * The cache key is the *track*, but every entry also records a hash of the
 * line-level lyric it was generated from. A different lyric source, a re-timed
 * LRC or a re-fetched document therefore invalidates the entry instead of
 * showing timing that belongs to other text.
 */
object AutoSyncTimelineStore {

    private const val TAG = "MeloXAutoSync"
    private const val DIR = "autosync/timelines"

    fun load(context: Context, trackKey: String): AutoSyncTimeline? {
        val file = fileFor(context, trackKey)
        if (!file.isFile) return null
        return runCatching { AutoSyncTimeline.fromJson(JSONObject(file.readText())) }
            .onFailure { Log.w(TAG, "timeline unreadable for $trackKey", it) }
            .getOrNull()
    }

    fun save(context: Context, timeline: AutoSyncTimeline) {
        runCatching {
            val file = fileFor(context, timeline.trackKey)
            file.parentFile?.mkdirs()
            val temporary = File.createTempFile("${file.name}.", ".part", file.parentFile)
            try {
                temporary.writeText(timeline.toJson().toString())
                if (!temporary.renameTo(file)) {
                    file.delete()
                    check(temporary.renameTo(file)) { "unable to publish AutoSync timeline" }
                }
            } finally {
                temporary.delete()
            }
        }.onFailure { Log.w(TAG, "cannot store timeline for ${timeline.trackKey}", it) }
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, DIR).listFiles()?.forEach(File::delete) }
    }

    /**
     * Removes only timelines generated with [language], leaving other
     * languages' cached alignments intact.
     */
    fun clearLanguage(context: Context, language: String) {
        if (language.isBlank()) return
        runCatching {
            File(context.filesDir, DIR).listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach
                val stored = runCatching { JSONObject(file.readText()).optString("lang") }.getOrNull()
                if (stored == language) file.delete()
            }
        }
    }

    /** Identity of the exact line-level document a timeline was generated from. */
    fun sourceHash(document: com.lladlam.melox.core.lyrics.LyricsDocument): String {
        val builder = StringBuilder(document.lines.size * 24)
        document.lines.forEach { line ->
            builder.append(line.timeMs).append('|').append(line.text).append('\n')
        }
        return MessageDigest.getInstance("SHA-256").digest(builder.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun fileFor(context: Context, trackKey: String): File {
        val name = MessageDigest.getInstance("SHA-256").digest(trackKey.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, DIR), "$name.json")
    }
}
