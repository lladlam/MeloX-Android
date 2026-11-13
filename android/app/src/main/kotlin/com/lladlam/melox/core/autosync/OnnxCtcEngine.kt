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
import java.util.Collections

/**
 * Local CTC acoustic engine backed by ONNX Runtime.
 *
 * This is the *only* place that touches the model: it feeds one line window of
 * raw 16 kHz PCM through the graph and returns frame-level log-probabilities for
 * [CtcForcedAligner]. The graph bakes in the feature normalisation, so no
 * preprocessing happens here on purpose.
 *
 * Inference never leaves the device and never touches the network.
 */
internal class OnnxCtcEngine private constructor(
    val manifest: AutoSyncManifest,
    private val session: OrtSession,
    private val environment: OrtEnvironment,
    private val modelBytes: Long,
) : AutoCloseable {

    /** Reused input buffer; grows to the longest window seen. */
    private var scratch: FloatBuffer = allocate(1)

    private fun allocate(samples: Int): FloatBuffer =
        ByteBuffer.allocateDirect(samples * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

    /**
     * Runs the graph for one window and returns window-relative CTC alignment.
     * The caller owns window/line semantics; this method only speaks frames.
     */
    @Synchronized
    fun alignWindow(pcm: FloatArray, tokenIds: List<Int>): CtcForcedAligner.Alignment {
        if (tokenIds.isEmpty()) return CtcForcedAligner.align(emptyArray(), emptyList(), manifest.blankId)
        if (scratch.capacity() < pcm.size) scratch = allocate(pcm.size)
        scratch.clear()
        scratch.put(pcm)
        scratch.rewind()
        scratch.limit(pcm.size)

        OnnxTensor.createTensor(
            environment,
            scratch,
            longArrayOf(1, pcm.size.toLong()),
        ).use { input ->
            session.run(Collections.singletonMap(manifest.inputName, input)).use { result ->
                val logProbs = readLogProbs(result)
                return CtcForcedAligner.align(logProbs, tokenIds, manifest.blankId)
            }
        }
    }

    private fun readLogProbs(result: OrtSession.Result): Array<DoubleArray> {
        val preferred = result.get(manifest.logProbOutputName).orElse(null)
        if (preferred is OnnxTensor) return flatten(preferred)
        val logits = manifest.logitsOutputName?.let { result.get(it).orElse(null) }
        if (logits is OnnxTensor) return flattenWithLogSoftmax(logits)
        error("model exposes neither ${manifest.logProbOutputName} nor logits")
    }

    private fun flatten(tensor: OnnxTensor): Array<DoubleArray> {
        val shape = tensor.info.shape
        require(shape.size == 3) { "expected [1, T, V] log-probs, got ${shape.toList()}" }
        val frames = shape[1].toInt()
        val symbols = shape[2].toInt()
        val flat = FloatArray(frames * symbols)
        tensor.floatBuffer.get(flat)
        return Array(frames) { frame ->
            DoubleArray(symbols) { symbol -> flat[frame * symbols + symbol].toDouble() }
        }
    }

    private fun flattenWithLogSoftmax(tensor: OnnxTensor): Array<DoubleArray> {
        val shape = tensor.info.shape
        require(shape.size == 3) { "expected [1, T, V] logits, got ${shape.toList()}" }
        val frames = shape[1].toInt()
        val symbols = shape[2].toInt()
        val flat = FloatArray(frames * symbols)
        tensor.floatBuffer.get(flat)
        val row = FloatArray(symbols)
        return Array(frames) { frame ->
            System.arraycopy(flat, frame * symbols, row, 0, symbols)
            CtcForcedAligner.logSoftmax(row)
        }
    }

    override fun close() {
        runCatching { session.close() }
    }

    val modelSizeBytes: Long get() = modelBytes
    val sourceModel: String get() = manifest.sourceModel

    companion object {
        private const val TAG = "MeloXAutoSync"

        private val cache = HashMap<String, OnnxCtcEngine>()
        private val lock = Any()

        /**
         * Only CTC logits models are usable today. The Chinese `fa-zh` contract
         * (us_alphas/us_peaks) has no Viterbi path yet, so it is reported as
         * unsupported instead of being fed to the wrong decoder.
         */
        fun isCtcCompatible(manifest: AutoSyncManifest): Boolean {
            if (manifest.tokenCountName != null) return false
            if (manifest.tokens.isEmpty()) return false
            val hasLogits = manifest.logitsOutputName != null
            return hasLogits || manifest.logProbOutputName == "log_probs"
        }

        /** Shared session per installed model; safe because ORT sessions are thread-safe. */
        fun forLanguage(context: Context, language: String): OnnxCtcEngine? {
            ZhAlignmentEngine.releaseAll()
            val manifest = AutoSyncModelStore.manifest(context, language) ?: return null
            if (!isCtcCompatible(manifest)) {
                Log.i(TAG, "language $language uses a non-CTC contract; skipped")
                return null
            }
            val path = AutoSyncModelStore.modelPath(context, language) ?: return null
            val key = "$language|$path|${File(path).lastModified()}"
            synchronized(lock) {
                cache[key]?.let { return it }
            }
            val loadStarted = android.os.SystemClock.elapsedRealtime()
            return runCatching {
                val environment = OrtEnvironment.getEnvironment()
                val engine = OrtSession.SessionOptions().use { options ->
                    options.setIntraOpNumThreads(2)
                    options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    val session = environment.createSession(path, options)
                    OnnxCtcEngine(manifest, session, environment, File(path).length())
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
                    Log.i(TAG, "model loaded language=$language loadMs=${android.os.SystemClock.elapsedRealtime() - loadStarted} rss=${rssBytes()}B")
                }
            }.onFailure { Log.w(TAG, "cannot open ONNX model for $language", it) }.getOrNull()
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
