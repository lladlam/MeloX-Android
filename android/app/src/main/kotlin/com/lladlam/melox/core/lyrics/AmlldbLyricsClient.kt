package com.lladlam.melox.core.lyrics

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** AMLL TTML database adapter used as the Apple Music-style lyric source. */
class AmlldbLyricsClient(
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    suspend fun lyrics(
        neteaseSongId: Long,
        script: MeloXLyricScript = MeloXLyricScript.Original,
    ): LyricsDocument = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://amlldb.bikonoo.com/ncm-lyrics/$neteaseSongId.ttml")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("AMLL TTML HTTP ${response.code}")
            val body = response.body.string()
            if (body.isBlank() || body.trim() == "歌词不存在") return@withContext LyricsDocument(emptyList())
            TtmlLyricsParser.parse(body, script)
        }
    }
}
