package com.lladlam.melox.core.lyrics

import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Synced lyrics from LrcLib, for catalogues that do not ship their own.
 *
 * YouTube Music only returns lyrics to a Premium account, and Spotify's Web API
 * does not expose the line-synced lyric document at all. LrcLib is an open,
 * unauthenticated database keyed on title, artist and duration, so a miss is the
 * ordinary case and this answers an empty document rather than raising.
 *
 * Ported from Square's `LrcLib`.
 */
class LrcLibLyricsClient(
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    suspend fun lyrics(title: String, artist: String, durationMs: Long): LyricsDocument =
        withContext(Dispatchers.IO) {
            val cleaned = title.substringBefore(" (").substringBefore("（").trim().ifBlank { title }
            val body = get(url(cleaned, artist, durationMs))
                ?: get(url(cleaned, artist, durationMs = null))
                ?: return@withContext LyricsDocument(emptyList())
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: return@withContext LyricsDocument(emptyList())
            json.optString("syncedLyrics").takeIf(String::isNotBlank)?.let(::parseLrc)
                ?: json.optString("plainLyrics").takeIf(String::isNotBlank)?.let { plain ->
                    LyricsDocument(
                        lines = plain.lines().filter(String::isNotBlank)
                            .map {
                                LyricLine(
                                    timeMs = 0L,
                                    text = it.trim(),
                                    timingKind = LyricTimingKind.LineSynchronized,
                                )
                            },
                        source = LyricSource.Local,
                        quality = LyricQuality.Fallback,
                        pseudoTimingAllowed = false,
                        isUnsynchronized = true,
                    )
                }
                ?: LyricsDocument(emptyList())
        }

    private fun url(title: String, artist: String, durationMs: Long?): String = buildString {
        append("https://lrclib.net/api/get?track_name=")
        append(URLEncoder.encode(title, Charsets.UTF_8.name()))
        append("&artist_name=")
        append(URLEncoder.encode(artist, Charsets.UTF_8.name()))
        if (durationMs != null && durationMs > 0) append("&duration=").append(durationMs / 1000)
    }

    /** Null on anything but a 200, which includes LrcLib's 404 for "no match". */
    private fun get(url: String): String? = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body.string()
        }
    }.getOrNull()

    /**
     * `[mm:ss.xx] words` per line. Blank timed lines are kept: they are the
     * instrumental gaps, and dropping them holds the previous line through a
     * stretch where nothing is being sung.
     */
    private fun parseLrc(raw: String): LyricsDocument {
        val lines = raw.lines().mapNotNull { line ->
            val match = LRC_LINE.find(line) ?: return@mapNotNull null
            val minutes = match.groupValues[1].toLong()
            val seconds = match.groupValues[2].toLong()
            val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLong()
            LyricLine(
                timeMs = minutes * 60_000 + seconds * 1_000 + fraction,
                text = match.groupValues[4].trim(),
                timingKind = LyricTimingKind.LineSynchronized,
            )
        }
        return LyricsDocument(
            lines = lines,
            source = LyricSource.Local,
            quality = if (lines.isEmpty()) LyricQuality.Fallback else LyricQuality.LineSynchronized,
            pseudoTimingAllowed = false,
        )
    }

    private companion object {
        val LRC_LINE = Regex("\\[(\\d{1,2}):(\\d{2})[.:](\\d{2,3})](.*)")
        const val USER_AGENT = "MeloX (https://github.com/lladlam/MeloX-Android)"
    }
}
