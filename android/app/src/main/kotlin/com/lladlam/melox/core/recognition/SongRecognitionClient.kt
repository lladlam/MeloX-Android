package com.lladlam.melox.core.recognition

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lladlam.melox.core.model.SearchSong
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class SongRecognitionResult(
    val song: SearchSong,
    val startTimeMs: Long,
)

/** Android port of MeloX's NetEase audio-fingerprint recognition pipeline. */
class SongRecognitionClient(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    companion object {
        const val SampleRate = 8_000
    }

    suspend fun recognize(durationSeconds: Int): List<SongRecognitionResult> {
        require(durationSeconds in 3..15)
        val samples = capture(durationSeconds)
        val fingerprint = FingerprintRuntime(context).generate(samples)
        return match(fingerprint, durationSeconds)
    }

    @SuppressLint("MissingPermission")
    private suspend fun capture(durationSeconds: Int): FloatArray = withContext(Dispatchers.IO) {
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minimum = AudioRecord.getMinBufferSize(SampleRate, channel, encoding)
        if (minimum <= 0) throw IOException("当前设备不支持听歌识曲所需的 8 kHz 录音")
        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SampleRate)
                    .setChannelMask(channel)
                    .setEncoding(encoding)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minimum * 2, 4_096))
            .build()
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IOException("麦克风初始化失败")
        }

        val target = durationSeconds * SampleRate
        val pcm = ShortArray(target)
        var offset = 0
        try {
            recorder.startRecording()
            while (offset < target) {
                val count = recorder.read(pcm, offset, minOf(2_048, target - offset), AudioRecord.READ_BLOCKING)
                if (count < 0) throw IOException("录音读取失败：$count")
                if (count == 0) continue
                offset += count
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        if (offset < SampleRate) throw IOException("没有收到足够的麦克风音频")
        FloatArray(offset) { pcm[it] / 32768f }
    }

    private suspend fun match(fingerprint: String, durationSeconds: Int): List<SongRecognitionResult> =
        withContext(Dispatchers.IO) {
            if (fingerprint.isBlank()) throw IOException("音频指纹为空")
            val url = "https://interface.music.163.com/api/music/audio/match".toHttpUrl()
                .newBuilder()
                .addQueryParameter("sessionId", "0123456789abcdef")
                .addQueryParameter("algorithmCode", "shazam_v2")
                .addQueryParameter("duration", durationSeconds.toString())
                .addQueryParameter("rawdata", fingerprint)
                .addQueryParameter("times", "1")
                .addQueryParameter("decrypt", "1")
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) MeloX/1.0")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("听歌识曲请求失败：HTTP ${response.code}")
                val root = JSONObject(response.body.string())
                if (root.optInt("code", 200) != 200) {
                    throw IOException(root.optString("message").ifBlank { "网易云听歌识曲服务返回错误" })
                }
                val candidates = root.optJSONObject("data")?.optJSONArray("result") ?: JSONArray()
                buildList {
                    val seen = mutableSetOf<Long>()
                    for (index in 0 until candidates.length()) {
                        val candidate = candidates.optJSONObject(index) ?: continue
                        val song = parseSong(candidate.optJSONObject("song")) ?: continue
                        if (!seen.add(song.id)) continue
                        add(SongRecognitionResult(song, candidate.optLong("startTime", 0L).coerceAtLeast(0L)))
                    }
                }
            }
        }

    private fun parseSong(value: JSONObject?): SearchSong? {
        value ?: return null
        val id = value.optLong("id", -1L)
        if (id <= 0L) return null
        val album = value.optJSONObject("al") ?: value.optJSONObject("album")
        val artistArray = value.optJSONArray("ar") ?: value.optJSONArray("artists") ?: JSONArray()
        val artists = buildList {
            for (index in 0 until artistArray.length()) {
                artistArray.optJSONObject(index)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
            }
        }.joinToString(" / ").ifBlank { "未知歌手" }
        return SearchSong(
            id = id,
            name = value.optString("name").ifBlank { "未知歌曲" },
            artists = artists,
            album = album?.optString("name").orEmpty(),
            artworkUrl = album?.optString("picUrl")?.takeIf(String::isNotBlank)?.replace("http://", "https://"),
            durationMs = value.optLong("dt", value.optLong("duration", 0L)).coerceAtLeast(0L),
        )
    }
}

private class FingerprintRuntime(private val context: Context) {
    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    suspend fun generate(samples: FloatArray): String = withContext(Dispatchers.Main) {
        val bytes = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach(bytes::putFloat)
        val pcm = Base64.encodeToString(bytes.array(), Base64.NO_WRAP)
        suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            val webView = WebView(context)
            fun finish(result: Result<String>) {
                if (!completed.compareAndSet(false, true)) return
                webView.post { webView.stopLoading(); webView.destroy() }
                result.onSuccess(continuation::resume).onFailure(continuation::resumeWithException)
            }
            continuation.invokeOnCancellation {
                if (completed.compareAndSet(false, true)) webView.post { webView.stopLoading(); webView.destroy() }
            }
            webView.settings.javaScriptEnabled = true
            webView.settings.allowFileAccess = true
            webView.addJavascriptInterface(object {
                @JavascriptInterface fun onFingerprint(value: String) = finish(
                    if (value.isBlank()) Result.failure(IOException("音频指纹生成失败")) else Result.success(value),
                )
                @JavascriptInterface fun onError(message: String) = finish(
                    Result.failure(IOException("音频指纹生成失败：$message")),
                )
            }, "MeloXNative")
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val script = """
                        (async function() {
                          try {
                            const bytes = Uint8Array.from(atob('$pcm'), c => c.charCodeAt(0));
                            const input = new Float32Array(bytes.buffer, bytes.byteOffset, Math.floor(bytes.byteLength / 4));
                            const result = await GenerateFP(input);
                            MeloXNative.onFingerprint(String(result || ''));
                          } catch (error) {
                            MeloXNative.onError(String(error && (error.stack || error.message) || error));
                          }
                        })();
                    """.trimIndent()
                    view.evaluateJavascript(script, null)
                }
            }
            webView.loadUrl("file:///android_asset/AudioFingerprint/runtime.html")
        }
    }
}
