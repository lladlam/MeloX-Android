package com.lladlam.melox.core.provider.kuwo

import android.util.Log
import com.lladlam.melox.core.lyrics.LrcLyricsParser
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import okhttp3.OkHttpClient

class KuwoLyricsClient(
    httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    private val requests = KuwoRequestClient(httpClient)

    suspend fun lyrics(track: MusicTrack): LyricsDocument {
        require(track.id.source == MusicSource.Kuwo) { "track must belong to Kuwo" }
        val mid = track.id.value.toLongOrNull()
            ?: throw IOException("Invalid Kuwo track ID: ${track.id.value}")

        val params = buildLyricParams(mid)
        return runCatching {
            val bytes = requests.getBytes(
                baseUrl = "https://newlyric.kuwo.cn",
                path = "/newlyric.lrc?$params",
            )
            decodeLyrics(bytes)
        }.getOrElse { error ->
            Log.w(TAG, "Lyrics fetch failed: mid=$mid", error)
            LyricsDocument(emptyList())
        }
    }

    private fun buildLyricParams(mid: Long): String {
        val payload = "user=12345,web,web,web&requester=localhost&req=1&rid=MUSIC_$mid&lrcx=1"
        val key = "yeelion".toByteArray(Charsets.UTF_8)
        val data = payload.toByteArray(Charsets.UTF_8)
        val output = ByteArray(data.size) { index ->
            (data[index].toInt() xor key[index % key.size].toInt()).toByte()
        }
        return Base64.getEncoder().encodeToString(output)
    }

    internal fun decodeLyrics(response: ByteArray): LyricsDocument {
        if (response.size < 10 || !response.startsWith("tp=content")) {
            Log.w(TAG, "Unexpected lyric response prefix: ${response.take(50).toByteArray().toString(Charsets.UTF_8)}")
            return LyricsDocument(emptyList())
        }

        val separator = "\r\n\r\n".toByteArray(Charsets.UTF_8)
        val headerEnd = response.indexOf(separator)
        if (headerEnd == -1) {
            Log.w(TAG, "Lyric response missing header/body separator")
            return LyricsDocument(emptyList())
        }
        val compressed = response.copyOfRange(headerEnd + separator.size, response.size)
        val inflated = inflate(compressed)
        Log.d(TAG, "Lyric inflated size=${inflated.size}")

        val base64Text = inflated.toString(Charsets.UTF_8).trim()
        if (base64Text.isBlank()) {
            Log.w(TAG, "Lyric inflated payload is blank")
            return LyricsDocument(emptyList())
        }

        val xorDecoded = base64Text.base64Decode().xorWith("yeelion")
        val lrcText = xorDecoded.toString(GB18030)
        val cleaned = lrcText.replace(lyricxWordTiming, "")
        val document = LrcLyricsParser.parse(cleaned)
        Log.d(TAG, "Lyric parsed lines=${document.lines.size}")
        return document
    }

    private fun inflate(input: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(input)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            val count = try {
                inflater.inflate(buffer)
            } catch (_: DataFormatException) {
                0
            }
            if (count <= 0) break
            output.write(buffer, 0, count)
        }
        inflater.end()
        return output.toByteArray()
    }

    private fun ByteArray.startsWith(prefix: String): Boolean {
        val prefixBytes = prefix.toByteArray(Charsets.UTF_8)
        if (size < prefixBytes.size) return false
        return copyOfRange(0, prefixBytes.size).contentEquals(prefixBytes)
    }

    private fun ByteArray.indexOf(pattern: ByteArray): Int {
        for (i in 0..size - pattern.size) {
            if (copyOfRange(i, i + pattern.size).contentEquals(pattern)) return i
        }
        return -1
    }

    private fun String.base64Decode(): ByteArray =
        Base64.getDecoder().decode(this)

    private fun ByteArray.xorWith(key: String): ByteArray {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        return ByteArray(size) { index ->
            (this[index].toInt() xor keyBytes[index % keyBytes.size].toInt()).toByte()
        }
    }

    private companion object {
        const val TAG = "KuwoLyricsClient"
        val GB18030: Charset = Charset.forName("GB18030")
        val lyricxWordTiming = Regex("""<-?\d+,-?\d+>""")
    }
}
