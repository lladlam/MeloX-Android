package com.lladlam.melox.core.provider.qqmusic

import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.lyrics.QQMusicQrcLyricsParser
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** QRC/translation/romanization request path used before the legacy LRC fallback. */
class QQMusicRichLyricsClient(
    private val sessionProvider: () -> QQMusicSession,
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    private data class SongIdentity(
        val songMid: String,
        val songId: Long?,
        val songType: Int,
    )

    /** One profile/download attempt with its own cost, so the caller can rank and log. */
    private data class Attempt(
        val label: String,
        val document: LyricsDocument?,
        val elapsedMs: Long,
        val error: String? = null,
    )

    private enum class RequestProfile {
        Mei,
        Android,
        Web,
        Desktop,
    }

    /**
     * songMid -> identity. The identity endpoint sits on the critical path before any
     * profile can be requested, so replaying a track must not pay for it again.
     */
    private val identityCache = object : LinkedHashMap<String, SongIdentity>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SongIdentity>?): Boolean =
            size > IdentityCacheSize
    }

    suspend fun lyrics(track: MusicTrack): LyricsDocument = withContext(Dispatchers.IO) {
        require(track.id.source == MusicSource.QQMusic)
        val metadata = track.providerMetadata as? ProviderTrackMetadata.QQMusic
        val songMid = metadata?.songMid?.takeIf(String::isNotBlank) ?: track.id.value
        val session = sessionProvider()
        val startedAt = SystemClock.elapsedRealtime()
        val identity = resolveSongIdentity(
            songMid = songMid,
            knownSongId = metadata?.numericSongId?.takeIf { it > 0L },
            session = session,
        )
        val identityMs = SystemClock.elapsedRealtime() - startedAt

        var best: LyricsDocument? = null
        val failures = mutableListOf<String>()

        // Phase 1 — Mei alone. It is the only profile that ever carries translations, so a
        // hit here keeps the original one-request fast path for songs that have them.
        val mei = attempt("Mei") { requestPlayLyricInfo(track, identity, session, RequestProfile.Mei) }
        mei.error?.let { failures += "Mei: $it" }
        val meiParsed = mei.document
        if (meiParsed != null && meiParsed.lines.any { it.syllables.isNotEmpty() }) {
            if (meiParsed.lines.any { !it.translation.isNullOrBlank() }) {
                Log.d(
                    TAG,
                    "lyrics fastPath=mei lines=${meiParsed.lines.size} " +
                        "identity=${identityMs}ms mei=${mei.elapsedMs}ms",
                )
                return@withContext meiParsed
            }
            if (best == null) best = meiParsed
        }

        // Phase 2 — the remaining profiles plus the download endpoint run together. They are
        // independent requests for the same payload, so the wall time is the slowest single
        // one instead of their sum; selection follows exactly the previous order.
        val phaseStarted = SystemClock.elapsedRealtime()
        val phase2 = coroutineScope {
            val jobs = mutableListOf<Deferred<Attempt>>()
            for (profile in listOf(RequestProfile.Android, RequestProfile.Web, RequestProfile.Desktop)) {
                jobs += async {
                    attempt(profile.name) { requestPlayLyricInfo(track, identity, session, profile) }
                }
            }
            jobs += async { attempt("Download") { requestDownloadedLyrics(identity, session) } }
            jobs.awaitAll()
        }
        for (result in phase2) {
            if (result.document == null) {
                failures += "${result.label}: ${result.error ?: "no usable timeline"} (${result.elapsedMs}ms)"
                continue
            }
            Log.d(
                TAG,
                "profile=${result.label} ${result.elapsedMs}ms lines=${result.document.lines.size} " +
                    "trans=${result.document.lines.count { !it.translation.isNullOrBlank() }}",
            )
            if (result.document.lines.any { it.syllables.isNotEmpty() }) {
                if (result.document.lines.any { !it.translation.isNullOrBlank() }) {
                    return@withContext result.document
                }
                if (best == null) best = result.document
            }
        }
        Log.d(
            TAG,
            "lyrics total=${SystemClock.elapsedRealtime() - startedAt}ms " +
                "identity=${identityMs}ms phase2=${SystemClock.elapsedRealtime() - phaseStarted}ms " +
                "failures=${failures.size}",
        )

        best ?: throw IOException(
            "QQ音乐没有返回可用的逐字歌词；${failures.joinToString("；")}",
        )
    }

    private suspend fun attempt(label: String, block: () -> LyricsDocument): Attempt {
        val began = SystemClock.elapsedRealtime()
        return try {
            Attempt(label, block(), SystemClock.elapsedRealtime() - began)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Attempt(
                label = label,
                document = null,
                elapsedMs = SystemClock.elapsedRealtime() - began,
                error = error.message ?: error::class.java.simpleName,
            )
        }
    }

    private fun requestPlayLyricInfo(
        track: MusicTrack,
        identity: SongIdentity,
        session: QQMusicSession,
        profile: RequestProfile,
    ): LyricsDocument {
        val lyricParam = JSONObject()
            .put("crypt", 1)
            .put("lrc_t", 0)
            .put("qrc", 1)
            .put("qrc_t", 0)
            .put("trans", 1)
            .put("trans_t", 0)
            .put("roma", 1)
            .put("roma_t", 0)
            .put("needSingingAnnotations", false)
            .put("type", if (profile == RequestProfile.Mei) 0 else identity.songType)
        if (profile == RequestProfile.Mei) {
            val songId = identity.songId ?: throw IOException("Mei QRC 协议需要 QQ 数字歌曲 ID")
            lyricParam
                .put("albumName", base64(track.album?.name.orEmpty()))
                .put("singerName", base64(track.artistText))
                .put("songName", base64(track.title))
                .put("interval", ((track.durationMs ?: 0L) / 1_000L).coerceAtLeast(0L))
                .put("ct", 19)
                .put("cv", 2111)
                .put("songID", songId)
        } else {
            identity.songId?.let { lyricParam.put("songId", it) }
                ?: lyricParam.put("songMid", identity.songMid)
        }

        val requestKey = if (profile == RequestProfile.Mei) MeiLyricRequestKey else "req_0"

        val payload = JSONObject()
            .put("comm", commonParams(profile, session))
            .put(
                requestKey,
                JSONObject()
                    .put("module", "music.musichallSong.PlayLyricInfo")
                    .put("method", "GetPlayLyricInfo")
                    .put("param", lyricParam),
            )

        val request = Request.Builder()
            .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .header("User-Agent", userAgent(profile))
            .header("Accept", "application/json, text/plain, */*")
            .apply {
                if (profile != RequestProfile.Android) header("Referer", "https://y.qq.com/")
                if (session.cookie.isNotBlank()) header("Cookie", session.cookie)
            }
            .post(payload.toString().toRequestBody(JsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw IOException("QQ音乐逐字歌词请求失败：HTTP ${response.code}")
            if (body.isBlank()) throw IOException("QQ音乐逐字歌词返回空响应")
            val root = JSONObject(body)
            val req = root.optJSONObject(requestKey)
                ?: root.optJSONObject("req_0")
                ?: throw IOException("QQ音乐逐字歌词响应缺少 $requestKey")
            val code = req.optInt("code", 0)
            if (code != 0) {
                throw IOException(
                    req.optString("message")
                        .ifBlank { req.optString("msg") }
                        .ifBlank { "QQ音乐逐字歌词错误码 $code" },
                )
            }
            val data = req.optJSONObject("data") ?: JSONObject()
            val lyric = data.optString("lyric")
            if (lyric.isBlank()) throw IOException("QQ音乐没有返回 QRC")
            val parsed = QQMusicQrcLyricsParser.parseEncrypted(
                qrcHex = lyric,
                translationHex = data.optString("trans"),
                romanizationHex = data.optString("roma"),
            )
            if (parsed.lines.none { it.syllables.isNotEmpty() }) {
                throw IOException("QQ音乐 QRC 解码后没有有效逐字时间轴")
            }
            return parsed
        }
    }

    private fun commonParams(profile: RequestProfile, session: QQMusicSession): JSONObject {
        return when (profile) {
            RequestProfile.Mei -> JSONObject()
                .put("_channelid", "")
                .put("_os_version", "6.2.9200-2")
                .put("authst", "")
                .put("ct", 11)
                .put("cv", "1003006")
                .put("patch", "118")
                .put("psrf_access_token_expiresAt", 0)
                .put("psrf_qqaccess_token", "")
                .put("psrf_qqopenid", "")
                .put("psrf_qqunionid", "")
                .put("tmeAppID", "qqmusiclight")
                .put("tmeLoginType", 0)
                .put("uin", "")
                .put("wid", "")

            RequestProfile.Android -> JSONObject()
                .put("ct", AndroidClientType)
                .put("cv", AndroidClientVersion)
                .put("v", AndroidClientVersion)
                .put("chid", "10003505")
                .put("qq", session.uin)
                .put("authst", session.musicKey)
                .put("tmeAppID", "qqmusic")
                .put("tmeLoginType", qqMusicLoginType(session.musicKey))
                .put("format", "json")

            RequestProfile.Web -> {
                val gtk = hash33(session.musicKey)
                JSONObject()
                    .put("ct", 24)
                    .put("cv", 4_747_474)
                    .put("platform", "yqq.json")
                    .put("chid", "0")
                    .put("uin", session.uin.toLongOrNull() ?: 0L)
                    .put("g_tk", gtk)
                    .put("g_tk_new_20200303", gtk)
                    .put("format", "json")
                    .put("inCharset", "utf-8")
                    .put("outCharset", "utf-8")
                    .put("notice", 0)
                    .put("need_new_code", 1)
            }

            RequestProfile.Desktop -> JSONObject()
                .put("ct", 19)
                .put("cv", 2201)
                .put("chid", "0")
                .put("uin", session.uin.toLongOrNull() ?: 0L)
                .put("g_tk", hash33(session.musicKey))
                .put("format", "json")
        }
    }

    private fun userAgent(profile: RequestProfile): String = when (profile) {
        RequestProfile.Mei -> MeiDesktopUserAgent
        RequestProfile.Android -> "QQMusic $AndroidClientVersion(android 15)"
        RequestProfile.Web,
        RequestProfile.Desktop -> DesktopUserAgent
    }

    private fun requestDownloadedLyrics(
        identity: SongIdentity,
        session: QQMusicSession,
    ): LyricsDocument {
        val songId = identity.songId ?: throw IOException("QQ音乐无法解析歌曲数字 ID")
        val body = FormBody.Builder()
            .add("version", "15")
            .add("miniversion", "82")
            .add("lrctype", "4")
            .add("musicid", songId.toString())
            .build()
        val request = Request.Builder()
            .url("https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg")
            .header("User-Agent", DesktopUserAgent)
            .header("Referer", "https://c.y.qq.com/")
            .apply { if (session.cookie.isNotBlank()) header("Cookie", session.cookie) }
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("QQ音乐歌词下载失败：HTTP ${response.code}")
            val xml = response.body.string()
                .replace("<!--", "")
                .replace("-->", "")
            val primary = extractXmlText(xml, "content")
            val translation = extractXmlText(xml, "contentts")
            val romanization = extractXmlText(xml, "contentroma")
            if (primary.isBlank()) throw IOException("QQ音乐歌词下载没有返回原文")
            val parsed = QQMusicQrcLyricsParser.parseEncrypted(
                qrcHex = primary,
                translationHex = translation,
                romanizationHex = romanization,
            )
            if (parsed.lines.none { it.syllables.isNotEmpty() }) {
                throw IOException("QQ音乐下载歌词没有有效逐字时间轴")
            }
            return parsed
        }
    }

    private fun resolveSongIdentity(
        songMid: String,
        knownSongId: Long?,
        session: QQMusicSession,
    ): SongIdentity {
        synchronized(identityCache) { identityCache[songMid] }?.let { return it }
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("c.y.qq.com")
            .addPathSegments("v8/fcg-bin/fcg_play_single_song.fcg")
            .addQueryParameter("songmid", songMid)
            .addQueryParameter("tpl", "yqq_song_detail")
            .addQueryParameter("format", "json")
            .addQueryParameter("g_tk", "5381")
            .addQueryParameter("loginUin", session.uin.ifBlank { "0" })
            .addQueryParameter("hostUin", "0")
            .addQueryParameter("outCharset", "utf8")
            .addQueryParameter("notice", "0")
            .addQueryParameter("platform", "yqq")
            .addQueryParameter("needNewCode", "0")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DesktopUserAgent)
            .header("Referer", "https://y.qq.com/")
            .apply { if (session.cookie.isNotBlank()) header("Cookie", session.cookie) }
            .get()
            .build()
        val song = runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = runCatching { JSONObject(response.body.string()) }.getOrNull() ?: return@use null
                root.optJSONArray("data")?.optJSONObject(0)
            }
        }.getOrNull()
        val songId = knownSongId
            ?: song?.optLong("songid", 0L)?.takeIf { it > 0L }
            ?: song?.optLong("id", 0L)?.takeIf { it > 0L }
        val songType = sequenceOf("type", "songtype", "songType")
            .mapNotNull { key ->
                when (val raw = song?.opt(key)) {
                    is Number -> raw.toInt()
                    is String -> raw.toIntOrNull()
                    else -> null
                }
            }
            .firstOrNull()
            ?.takeIf { it >= 0 }
            ?: 1
        val identity = SongIdentity(songMid = songMid, songId = songId, songType = songType)
        // Only memoize a resolution that actually knows the numeric id; a failed lookup
        // must not poison later attempts for the same song.
        if (songId != null) {
            synchronized(identityCache) { identityCache[songMid] = identity }
        }
        return identity
    }

    private fun extractXmlText(xml: String, tag: String): String {
        val pattern = Regex(
            "<$tag(?:\\s[^>]*)?>(.*?)</$tag>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        return pattern.find(xml)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .trim()
            .removePrefix("<![CDATA[")
            .removeSuffix("]]>")
            .trim()
    }

    private fun hash33(value: String): Int {
        var hash = 5381L
        value.forEach { char -> hash += (hash shl 5) + char.code }
        return (hash and 0x7fffffffL).toInt()
    }

    private fun base64(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private companion object {
        const val TAG = "MeloXQQLyrics"
        const val IdentityCacheSize = 32
        const val AndroidClientType = 11
        const val AndroidClientVersion = 14_090_008
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
        const val DesktopUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val MeiDesktopUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/91.0.4472.164 Safari/537.36"
        const val MeiLyricRequestKey = "music.musichallSong.PlayLyricInfo.GetPlayLyricInfo"
    }
}
