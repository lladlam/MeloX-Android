package com.lladlam.melox.core.autosync

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Collections

/**
 * Chinese `fa-zh` inference: raw PCM + text token count in, per-token times out.
 *
 * This is deliberately **not** [OnnxCtcEngine]: the Chinese pack is a
 * monotonic-alignment/CIF model, so its extra `token_num` input and its
 * `us_alphas` / `us_peaks` outputs are handled here and decoded by
 * [ZhCifPostprocess]. Feeding it to the CTC Viterbi would be meaningless.
 */
internal class ZhAlignmentEngine private constructor(
    val manifest: AutoSyncManifest,
    private val tokenNumName: String,
    private val session: OrtSession,
    private val environment: OrtEnvironment,
) : AutoCloseable {

    private var scratch: FloatBuffer = allocateFloats(1)

    private fun allocateFloats(samples: Int): FloatBuffer =
        ByteBuffer.allocateDirect(samples * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

    /**
     * @return one span per text token, or `null` when the model output cannot be
     *   turned into a usable timeline (the caller keeps line timing).
     */
    @Synchronized
    fun alignWindow(pcm: FloatArray, tokenIds: List<Int>): List<ZhCifPostprocess.TokenSpan>? {
        if (tokenIds.isEmpty() || pcm.isEmpty()) return null
        if (scratch.capacity() < pcm.size) scratch = allocateFloats(pcm.size)
        scratch.clear()
        scratch.put(pcm)
        scratch.rewind()
        scratch.limit(pcm.size)

        val tokenNum = LongBuffer.wrap(longArrayOf(tokenIds.size.toLong() + 1L))
        OnnxTensor.createTensor(
            environment,
            scratch,
            longArrayOf(1, pcm.size.toLong()),
        ).use { audioTensor ->
            OnnxTensor.createTensor(environment, tokenNum, longArrayOf(1)).use { countTensor ->
                val inputs = HashMap<String, OnnxTensor>(2)
                inputs[manifest.inputName] = audioTensor
                inputs[tokenNumName] = countTensor
                session.run(inputs).use { result ->
                    val alphas = readVector(result, "us_alphas") ?: return null
                    val peaks = readVector(result, "us_peaks") ?: return null
                    val spans = ZhCifPostprocess.tokens(alphas, peaks, tokenIds.size)
                    return spans.takeIf { it.isNotEmpty() }
                }
            }
        }
    }

    private fun readVector(result: OrtSession.Result, name: String): FloatArray? {
        val value = result.get(name).orElse(null) as? OnnxTensor ?: return null
        val shape = value.info.shape
        val length = shape.fold(1L) { acc, dim -> acc * dim }.toInt()
        val data = FloatArray(length)
        value.floatBuffer.get(data)
        return data
    }

    override fun close() {
        runCatching { session.close() }
    }

    companion object {
        private const val TAG = "MeloXAutoSync"

        private val cache = HashMap<String, ZhAlignmentEngine>()
        private val lock = Any()

        fun forLanguage(context: Context, language: String): ZhAlignmentEngine? {
            OnnxCtcEngine.releaseAll()
            val manifest = AutoSyncModelStore.manifest(context, language) ?: return null
            val tokenNumName = manifest.tokenCountName ?: return null
            if (manifest.tokens.isEmpty()) return null
            val path = AutoSyncModelStore.modelPath(context, language) ?: return null
            val key = "$language|$path|${File(path).lastModified()}"
            synchronized(lock) { cache[key]?.let { return it } }
            val loadStarted = android.os.SystemClock.elapsedRealtime()
            return runCatching {
                val environment = OrtEnvironment.getEnvironment()
                val engine = OrtSession.SessionOptions().use { options ->
                    options.setIntraOpNumThreads(2)
                    options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    ZhAlignmentEngine(
                        manifest = manifest,
                        tokenNumName = tokenNumName,
                        session = environment.createSession(path, options),
                        environment = environment,
                    )
                }
                engine.also {
                    synchronized(lock) {
                        cache.entries.removeAll { (existingKey, existing) ->
                            if (existingKey.startsWith("$language|")) {
                                runCatching { existing.close() }
                                true
                            } else {
                                false
                            }
                        }
                        cache[key] = engine
                    }
                    Log.i(TAG, "model loaded language=zh loadMs=${android.os.SystemClock.elapsedRealtime() - loadStarted} rss=${rssBytes()}B")
                }
            }.onFailure { Log.w(TAG, "cannot open Chinese model", it) }.getOrNull()
        }

        fun releaseAll() {
            synchronized(lock) {
                cache.values.forEach { runCatching { it.close() } }
                cache.clear()
            }
        }

        private fun rssBytes(): Long = runCatching {
            File("/proc/self/status").useLines { lines ->
                lines.first { it.startsWith("VmRSS:") }.substringAfter(':').trim().substringBefore(' ').toLong() * 1024L
            }
        }.getOrDefault(-1L)
    }
}

/**
 * Chinese text rules for AutoSync.
 *
 * The pack's own normalisation drops Latin characters, so a line that contains
 * them cannot be aligned "completely" — dropping the letters and still
 * presenting word-level timing for the whole line would misrepresent the
 * result. Those lines keep their provider line timing instead.
 */
internal object ZhText {

    private val LATIN = Regex("[A-Za-z]")

    /** True when every meaningful character is alignable by the Chinese pack. */
    fun isAlignableForChinese(text: String): Boolean {
        if (!AutoSyncText.isAlignable(text)) return false
        // Even a short "OK" would be silently deleted by this pack. Keep the
        // whole mixed-script line line-timed until a verified mixed route exists.
        return !LATIN.containsMatchIn(text)
    }

    /**
     * Maps a lyric line to the pack's token ids. Tokens are single characters;
     * anything outside the vocabulary (punctuation, spaces, dropped Latin) is
     * skipped exactly as the reference normalisation would.
     */
    fun tokenIds(normalized: String, manifest: AutoSyncManifest): List<Int> {
        if (manifest.tokens.isEmpty()) return emptyList()
        val lookup = HashMap<String, Int>(manifest.tokens.size * 2)
        manifest.tokens.forEachIndexed { index, token -> lookup.putIfAbsent(token, index) }
        val ids = ArrayList<Int>(normalized.length)
        for (character in normalized) {
            if (character.isWhitespace()) continue
            lookup[character.toString()]?.let(ids::add)
        }
        return ids
    }

    /** NFKC and keep only what the pack's vocabulary can represent. */
    fun normalize(text: String): String = AutoSyncText.normalizeZh(text)
}
