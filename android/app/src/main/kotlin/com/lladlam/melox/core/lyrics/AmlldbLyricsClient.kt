package com.lladlam.melox.core.lyrics

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * AMLL TTML adapter.
 *
 * The former bikonoo mirror now fails the TLS handshake, so lookup goes through
 * the official API first and then through still-reachable raw mirrors.
 */
class AmlldbLyricsClient(
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    suspend fun lyrics(
        neteaseSongId: Long,
        script: MeloXLyricScript = MeloXLyricScript.Original,
    ): LyricsDocument = lyrics(AmlldbLyricQuery.Netease(neteaseSongId), script)

    suspend fun lyrics(
        query: AmlldbLyricQuery,
        script: MeloXLyricScript = MeloXLyricScript.Original,
    ): LyricsDocument = withContext(Dispatchers.IO) {
        val body = fetch(query) ?: return@withContext LyricsDocument(emptyList())
        TtmlLyricsParser.parse(body, script)
    }

    private fun fetch(query: AmlldbLyricQuery): String? {
        runCatching { fetchOfficial(query) }.getOrNull()?.let { return it }
        query.rawPaths().forEach { path ->
            rawMirrors.forEach { mirror ->
                runCatching { fetchRaw("$mirror$path") }.getOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun fetchOfficial(query: AmlldbLyricQuery): String? {
        val url = "https://api.amll.dev/api/v1/lyrics/get".toHttpUrl().newBuilder()
        query.officialParameter()?.let { (name, value) -> url.addQueryParameter(name, value) }
            ?: return null
        val request = Request.Builder()
            .url(url.build())
            .header("Accept", "application/json")
            .header("User-Agent", "MeloX-Android")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("AMLL API HTTP ${response.code}")
            val payload = JSONObject(response.body.string())
            val lyrics = payload.optJSONObject("data")?.optString("lyrics").orEmpty()
            return lyrics.takeIf(::usableTtml)
        }
    }

    private fun fetchRaw(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/xml,text/xml,*/*")
            .header("User-Agent", "MeloX-Android")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("AMLL TTML HTTP ${response.code}")
            return response.body.string().takeIf(::usableTtml)
        }
    }

    private fun usableTtml(body: String): Boolean =
        body.isNotBlank() && body.trim() != "歌词不存在" && body.contains("http://www.w3.org/ns/ttml")

    private companion object {
        val rawMirrors = listOf(
            "https://amll.mirror.dimeta.top/api/db/",
            "https://amll-ttml-db.gbclstudio.cn/",
            "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/refs/heads/main/",
            "https://amll-ttml-db.stevexmh.net/",
        )
    }
}

sealed interface AmlldbLyricQuery {
    data class Netease(val songId: Long) : AmlldbLyricQuery
    data class QQ(val songId: String) : AmlldbLyricQuery
    data class Spotify(val trackId: String) : AmlldbLyricQuery
    data class AppleMusic(val songId: String) : AmlldbLyricQuery

    fun officialParameter(): Pair<String, String>? = when (this) {
        is Netease -> "ncmMusicId" to songId.toString()
        is QQ -> songId.takeIf(String::isNotBlank)?.let { "qqMusicId" to it }
        is Spotify -> trackId.takeIf(String::isNotBlank)?.let { "spotifyId" to it }
        is AppleMusic -> songId.takeIf(String::isNotBlank)?.let { "appleMusicId" to it }
    }

    fun bindingValue(): String = when (this) {
        is Netease -> songId.toString()
        is QQ -> "qq:$songId"
        is Spotify -> "spotify:$trackId"
        is AppleMusic -> "apple:$songId"
    }

    fun rawPaths(): List<String> = when (this) {
        is Netease -> listOf("ncm-lyrics/$songId.ttml")
        is QQ -> songId.takeIf(String::isNotBlank)?.let { listOf("qq-lyrics/$it.ttml", "qq-lyrics/$it.qrc") }.orEmpty()
        is Spotify -> trackId.takeIf(String::isNotBlank)?.let { listOf("spotify-lyrics/$it.ttml") }.orEmpty()
        is AppleMusic -> songId.takeIf(String::isNotBlank)?.let { listOf("am-lyrics/$it.ttml") }.orEmpty()
    }
}
