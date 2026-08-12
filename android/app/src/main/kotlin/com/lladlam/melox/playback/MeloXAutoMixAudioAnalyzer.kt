package com.lladlam.melox.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class MeloXAutoMixFrame(
    val timeMs: Long,
    val energy: Float,
    val lowRatio: Float,
    val midRatio: Float,
    val highRatio: Float,
    val novelty: Float,
    val onset: Float,
)

data class MeloXAutoMixTrackAnalysis(
    val bpm: Double,
    val confidence: Double,
    val firstAudibleMs: Long,
    val lastAudibleMs: Long,
    val beatTimesMs: LongArray,
    val downbeatTimesMs: LongArray,
    val phraseBoundariesMs: LongArray,
    val frames: List<MeloXAutoMixFrame>,
) {
    fun frameAt(timeMs: Long): MeloXAutoMixFrame? {
        if (frames.isEmpty()) return null
        val index = frames.binarySearchBy(timeMs) { it.timeMs }
        return frames[if (index >= 0) index else (-index - 1).coerceIn(0, frames.lastIndex)]
    }

    fun plannerAnalysis(): MeloXAutoMixAnalysis = MeloXAutoMixAnalysis(
        bpm = bpm,
        confidence = confidence,
        firstAudibleMs = firstAudibleMs,
        lastAudibleMs = lastAudibleMs,
    )
}

/**
 * Native Android equivalent of MeloX's BeatNet analysis pipeline. Android
 * cannot execute the upstream CoreML package, so this implementation decodes
 * the real playback source and derives a deterministic beat timeline from an
 * onset envelope, FFT band energy and spectral novelty. It analyses the full
 * track, works for local/content/HTTP sources, and caches by song + source URI.
 */
class MeloXAutoMixAudioAnalyzer(private val context: Context) {
    private val cache = ConcurrentHashMap<String, MeloXAutoMixTrackAnalysis>()
    // Allow the outgoing and incoming analysis to progress together while
    // preventing unrelated visual analysis from spawning an unbounded decoder set.
    private val decodePermits = Semaphore(2)

    suspend fun analyze(songId: Long, uri: Uri): MeloXAutoMixTrackAnalysis =
        withContext(Dispatchers.IO) {
            val key = "$songId|$uri"
            cache[key] ?: decodePermits.withPermit {
                cache[key] ?: decode(uri).also { cache[key] = it }
            }
        }

    fun clear() = cache.clear()

    private suspend fun decode(uri: Uri): MeloXAutoMixTrackAnalysis {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            if (uri.scheme == "http" || uri.scheme == "https") {
                extractor.setDataSource(
                    uri.toString(),
                    mapOf(
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36",
                        "Referer" to "https://music.163.com/",
                    ),
                )
            } else {
                extractor.setDataSource(context, uri, null)
            }

            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("AutoMix: source has no audio track")
            extractor.selectTrack(track)
            val inputFormat = extractor.getTrackFormat(track)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: error("AutoMix: audio MIME is unavailable")
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val accumulator = FeatureAccumulator()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var outputFormat = inputFormat
            while (!outputEnded) {
                // MediaCodec work is native and may otherwise keep running
                // after the planner is cancelled at the transition boundary.
                // Releasing it before the standby ExoPlayer starts avoids a
                // short-lived three-decoder spike on constrained devices.
                currentCoroutineContext().ensureActive()
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inputIndex) ?: continue
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = decoder.outputFormat
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        decoder.getOutputBuffer(outputIndex)?.let { buffer ->
                            if (info.size > 0) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                accumulator.consume(
                                    buffer.slice().order(ByteOrder.nativeOrder()),
                                    sampleRate = outputFormat.intOr(
                                        MediaFormat.KEY_SAMPLE_RATE,
                                        inputFormat.intOr(MediaFormat.KEY_SAMPLE_RATE, 44_100),
                                    ),
                                    channelCount = outputFormat.intOr(
                                        MediaFormat.KEY_CHANNEL_COUNT,
                                        inputFormat.intOr(MediaFormat.KEY_CHANNEL_COUNT, 2),
                                    ),
                                    pcmEncoding = outputFormat.intOr(
                                        MediaFormat.KEY_PCM_ENCODING,
                                        AudioFormat.ENCODING_PCM_16BIT,
                                    ),
                                )
                            }
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            return accumulator.finish()
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun MediaFormat.intOr(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback

    private class FeatureAccumulator {
        private val window = FloatArray(FFT_SIZE)
        private var windowCount = 0
        private var sourcePhase = 0
        private var emittedSamples = 0L
        private var previousEnergy = 0f
        private var previousSpectrum = FloatArray(FFT_SIZE / 2)
        private val rawFrames = ArrayList<RawFrame>()

        fun consume(buffer: ByteBuffer, sampleRate: Int, channelCount: Int, pcmEncoding: Int) {
            val channels = channelCount.coerceAtLeast(1)
            val bytesPerSample = when (pcmEncoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> 4
                AudioFormat.ENCODING_PCM_8BIT -> 1
                else -> 2
            }
            val frameBytes = bytesPerSample * channels
            while (buffer.remaining() >= frameBytes) {
                var mono = 0f
                repeat(channels) {
                    mono += when (pcmEncoding) {
                        AudioFormat.ENCODING_PCM_FLOAT -> buffer.getFloat().coerceIn(-1f, 1f)
                        AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 0xff) - 128) / 128f
                        else -> buffer.getShort() / 32768f
                    }
                }
                mono /= channels
                sourcePhase += TARGET_SAMPLE_RATE
                while (sourcePhase >= sampleRate) {
                    sourcePhase -= sampleRate
                    append(mono)
                }
            }
        }

        private fun append(sample: Float) {
            window[windowCount++] = sample
            emittedSamples++
            if (windowCount < FFT_SIZE) return
            calculateFrame()
            window.copyInto(window, destinationOffset = 0, startIndex = HOP_SIZE, endIndex = FFT_SIZE)
            windowCount = FFT_SIZE - HOP_SIZE
        }

        private fun calculateFrame() {
            val real = FloatArray(FFT_SIZE)
            val imaginary = FloatArray(FFT_SIZE)
            var squareSum = 0.0
            for (index in window.indices) {
                val value = window[index]
                squareSum += value * value
                real[index] = value * (0.5f - 0.5f * cos((2.0 * PI * index) / (FFT_SIZE - 1)).toFloat())
            }
            fft(real, imaginary)
            val magnitudes = FloatArray(FFT_SIZE / 2)
            var low = 0.0
            var mid = 0.0
            var high = 0.0
            var novelty = 0.0
            for (bin in magnitudes.indices) {
                val magnitude = sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin])
                magnitudes[bin] = magnitude
                novelty += max(magnitude - previousSpectrum[bin], 0f)
                when (bin * TARGET_SAMPLE_RATE / FFT_SIZE) {
                    in 0..249 -> low += magnitude
                    in 250..2_499 -> mid += magnitude
                    else -> high += magnitude
                }
            }
            val total = (low + mid + high).coerceAtLeast(1e-8)
            val energy = sqrt(squareSum / FFT_SIZE).toFloat()
            val flux = (novelty / (magnitudes.sum().coerceAtLeast(1e-6f))).toFloat()
            val onset = max(energy - previousEnergy, 0f) * 0.62f + flux * 0.38f
            val frameTimeMs = ((emittedSamples - FFT_SIZE).coerceAtLeast(0L) * 1_000L) / TARGET_SAMPLE_RATE
            rawFrames += RawFrame(
                timeMs = frameTimeMs,
                energy = energy,
                low = (low / total).toFloat(),
                mid = (mid / total).toFloat(),
                high = (high / total).toFloat(),
                novelty = flux,
                onset = onset,
            )
            previousEnergy = energy
            previousSpectrum = magnitudes
        }

        fun finish(): MeloXAutoMixTrackAnalysis {
            require(rawFrames.size >= MIN_ANALYSIS_FRAMES) { "AutoMix: decoded audio is too short" }
            val energyScale = percentile(rawFrames.map { it.energy }, .95f).coerceAtLeast(1e-5f)
            val noveltyScale = percentile(rawFrames.map { it.novelty }, .95f).coerceAtLeast(1e-5f)
            val onsetScale = percentile(rawFrames.map { it.onset }, .95f).coerceAtLeast(1e-5f)
            val frames = rawFrames.map {
                MeloXAutoMixFrame(
                    timeMs = it.timeMs,
                    energy = (it.energy / energyScale).coerceIn(0f, 1.5f),
                    lowRatio = it.low,
                    midRatio = it.mid,
                    highRatio = it.high,
                    novelty = (it.novelty / noveltyScale).coerceIn(0f, 1.5f),
                    onset = (it.onset / onsetScale).coerceIn(0f, 1.5f),
                )
            }
            val audible = frames.indices.filter { frames[it].energy >= AUDIBLE_THRESHOLD }
            val firstAudible = audible.firstOrNull()?.let { frames[it].timeMs } ?: frames.first().timeMs
            val lastAudible = audible.lastOrNull()?.let { frames[it].timeMs + FRAME_DURATION_MS }
                ?: frames.last().timeMs + FRAME_DURATION_MS
            val tempo = estimateTempo(frames)
            val beats = beatTimeline(frames, tempo.bpm)
            val downbeats = downbeatTimeline(frames, beats)
            val phrases = phraseTimeline(frames, downbeats, firstAudible, lastAudible)
            return MeloXAutoMixTrackAnalysis(
                bpm = tempo.bpm,
                confidence = tempo.confidence,
                firstAudibleMs = firstAudible,
                lastAudibleMs = lastAudible,
                beatTimesMs = beats,
                downbeatTimesMs = downbeats,
                phraseBoundariesMs = phrases,
                frames = frames,
            )
        }

        private data class RawFrame(
            val timeMs: Long,
            val energy: Float,
            val low: Float,
            val mid: Float,
            val high: Float,
            val novelty: Float,
            val onset: Float,
        )
    }

    private data class TempoEstimate(val bpm: Double, val confidence: Double)

    companion object {
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val TARGET_SAMPLE_RATE = 11_025
        private const val FFT_SIZE = 1_024
        private const val HOP_SIZE = 512
        private const val FRAME_DURATION_MS = HOP_SIZE * 1_000L / TARGET_SAMPLE_RATE
        private const val MIN_ANALYSIS_FRAMES = 32
        private const val AUDIBLE_THRESHOLD = .035f

        private fun estimateTempo(frames: List<MeloXAutoMixFrame>): TempoEstimate {
            val signal = frames.map { (it.onset * .72f + it.novelty * .18f + it.energy * .10f).toDouble() }
            val frameRate = TARGET_SAMPLE_RATE.toDouble() / HOP_SIZE
            val minLag = (frameRate * 60.0 / 190.0).toInt().coerceAtLeast(2)
            val maxLag = (frameRate * 60.0 / 65.0).toInt().coerceAtMost(signal.size / 2)
            val scores = ArrayList<Pair<Int, Double>>()
            for (lag in minLag..maxLag) {
                var score = 0.0
                var weight = 0.0
                for (index in lag until signal.size) {
                    val activity = max(signal[index], signal[index - lag])
                    score += signal[index] * signal[index - lag] * (0.35 + activity * .65)
                    weight += activity
                }
                scores += lag to if (weight > 0) score / weight else 0.0
            }
            val ranked = scores.sortedByDescending { it.second }
            val best = ranked.firstOrNull() ?: return TempoEstimate(120.0, 0.0)
            val second = ranked.firstOrNull { abs(it.first - best.first) > 1 }?.second ?: 0.0
            var bpm = 60.0 * frameRate / best.first
            while (bpm < 80.0) bpm *= 2.0
            while (bpm > 170.0) bpm /= 2.0
            val contrast = if (best.second > 0) ((best.second - second) / best.second).coerceIn(0.0, 1.0) else 0.0
            val activity = signal.count { it > .22 }.toDouble() / signal.size.coerceAtLeast(1)
            return TempoEstimate(bpm, (contrast * .62 + min(activity * 2.4, 1.0) * .38).coerceIn(0.0, 1.0))
        }

        private fun beatTimeline(frames: List<MeloXAutoMixFrame>, bpm: Double): LongArray {
            val frameRate = TARGET_SAMPLE_RATE.toDouble() / HOP_SIZE
            val period = (frameRate * 60.0 / bpm).coerceAtLeast(2.0)
            val candidatePeriod = period.toInt().coerceAtLeast(2)
            val phase = (0 until candidatePeriod).maxByOrNull { offset ->
                var score = 0.0
                var index = offset
                while (index < frames.size) {
                    score += frames[index].onset + frames[index].novelty * .3
                    index += candidatePeriod
                }
                score
            } ?: 0
            val beatMs = 60_000.0 / bpm
            val startMs = frames[phase.coerceIn(0, frames.lastIndex)].timeMs.toDouble()
            return buildList {
                var time = startMs
                val end = frames.last().timeMs + FRAME_DURATION_MS
                while (time <= end) {
                    add(time.toLong())
                    time += beatMs
                }
            }.toLongArray()
        }

        private fun downbeatTimeline(frames: List<MeloXAutoMixFrame>, beats: LongArray): LongArray {
            if (beats.isEmpty()) return longArrayOf()
            val phase = (0..3).maxByOrNull { candidate ->
                beats.indices.filter { it % 4 == candidate }.sumOf { index ->
                    val frame = nearestFrame(frames, beats[index])
                    (frame?.onset ?: 0f).toDouble() + (frame?.lowRatio ?: 0f) * .18
                }
            } ?: 0
            return beats.filterIndexed { index, _ -> index % 4 == phase }.toLongArray()
        }

        private fun phraseTimeline(
            frames: List<MeloXAutoMixFrame>,
            downbeats: LongArray,
            firstAudibleMs: Long,
            lastAudibleMs: Long,
        ): LongArray {
            val bases = downbeats.filterIndexed { index, _ -> index % 4 == 0 }
            if (bases.isEmpty()) return longArrayOf(firstAudibleMs, lastAudibleMs)
            return buildList {
                add(firstAudibleMs)
                bases.forEach { base ->
                    val nearby = frames.filter { abs(it.timeMs - base) <= 350L }
                    val best = nearby.maxByOrNull { it.novelty * .60f + it.onset * .40f }
                    val time = best?.timeMs ?: base
                    if (time in (firstAudibleMs + 500)..(lastAudibleMs - 500)) add(time)
                }
                add(lastAudibleMs)
            }.distinct().sorted().toLongArray()
        }

        private fun nearestFrame(frames: List<MeloXAutoMixFrame>, timeMs: Long): MeloXAutoMixFrame? {
            if (frames.isEmpty()) return null
            val index = frames.binarySearchBy(timeMs) { it.timeMs }
            return frames[if (index >= 0) index else (-index - 1).coerceIn(0, frames.lastIndex)]
        }

        private fun percentile(values: List<Float>, percentile: Float): Float {
            if (values.isEmpty()) return 0f
            val sorted = values.sorted()
            return sorted[((sorted.lastIndex) * percentile.coerceIn(0f, 1f)).toInt()]
        }

        private fun fft(real: FloatArray, imaginary: FloatArray) {
            val size = real.size
            var target = 0
            for (index in 1 until size) {
                var bit = size shr 1
                while (target and bit != 0) {
                    target = target xor bit
                    bit = bit shr 1
                }
                target = target xor bit
                if (index < target) {
                    val realTemp = real[index]
                    real[index] = real[target]
                    real[target] = realTemp
                    val imaginaryTemp = imaginary[index]
                    imaginary[index] = imaginary[target]
                    imaginary[target] = imaginaryTemp
                }
            }
            var length = 2
            while (length <= size) {
                val angle = -2.0 * PI / length
                val wLengthReal = cos(angle).toFloat()
                val wLengthImaginary = sin(angle).toFloat()
                var start = 0
                while (start < size) {
                    var wReal = 1f
                    var wImaginary = 0f
                    for (offset in 0 until length / 2) {
                        val even = start + offset
                        val odd = even + length / 2
                        val oddReal = real[odd] * wReal - imaginary[odd] * wImaginary
                        val oddImaginary = real[odd] * wImaginary + imaginary[odd] * wReal
                        real[odd] = real[even] - oddReal
                        imaginary[odd] = imaginary[even] - oddImaginary
                        real[even] += oddReal
                        imaginary[even] += oddImaginary
                        val nextReal = wReal * wLengthReal - wImaginary * wLengthImaginary
                        wImaginary = wReal * wLengthImaginary + wImaginary * wLengthReal
                        wReal = nextReal
                    }
                    start += length
                }
                length = length shl 1
            }
        }
    }
}

/** Scores phrase-aligned pairs using energy continuity and spectral crowding. */
object MeloXAutoMixTransitionScorer {
    fun plan(
        settings: MeloXAutoMixSettings,
        outgoing: MeloXAutoMixTrackAnalysis,
        incoming: MeloXAutoMixTrackAnalysis,
    ): MeloXAutoMixPlan? {
        val confidence = min(outgoing.confidence, incoming.confidence)
        if (confidence < settings.minimumConfidence || outgoing.bpm <= 0.0 || incoming.bpm <= 0.0) return null
        val rates = tempoRates(outgoing.bpm, incoming.bpm, settings)
        val outgoingBeatMs = 60_000.0 / outgoing.bpm
        val incomingBeatMs = 60_000.0 / rates.alignedIncomingBpm
        val requestedDurationMs = (settings.transitionBars * 4 * (outgoingBeatMs + incomingBeatMs) / 2.0).toLong()
        val tailCutMs = (settings.tailCutBars * 4 * outgoingBeatMs).toLong()
        val desiredOutgoingEnd = (outgoing.lastAudibleMs - tailCutMs)
            .coerceIn(outgoing.firstAudibleMs + 1_000L, outgoing.lastAudibleMs)
        val maximumDuration = min(
            32_000L,
            min(
                (desiredOutgoingEnd - outgoing.firstAudibleMs - 500L).coerceAtLeast(0L),
                (incoming.lastAudibleMs - incoming.firstAudibleMs - 500L).coerceAtLeast(0L),
            ),
        )
        val durationMs = requestedDurationMs
            .coerceAtLeast(3_000L)
            .coerceAtMost(maximumDuration)
        if (durationMs < MeloXAutoMixPlanner.MIN_DURATION_MS) return null

        val targetOutgoingStart = desiredOutgoingEnd - durationMs
        val earliestOutgoingStart = max(
            outgoing.firstAudibleMs,
            max((outgoing.lastAudibleMs * .52).toLong(), targetOutgoingStart - max(durationMs * 2, 24_000L)),
        )
        var outgoingCandidates = outgoing.beatTimesMs.withIndex()
            .filter { it.value in earliestOutgoingStart..(targetOutgoingStart + 80L) }
            .takeLast(48)
        if (outgoingCandidates.isEmpty()) {
            outgoingCandidates = outgoing.beatTimesMs.withIndex()
                .filter { it.value <= targetOutgoingStart + 80L }
                .takeLast(48)
        }
        val latestIncomingStart = min(
            (incoming.lastAudibleMs * .25).toLong(),
            min(48_000L, incoming.lastAudibleMs - durationMs - 250L),
        ).coerceAtLeast(incoming.firstAudibleMs)
        var incomingCandidates = incoming.beatTimesMs.withIndex()
            .filter { it.value in incoming.firstAudibleMs..(latestIncomingStart + 80L) }
            .take(48)
        if (incomingCandidates.isEmpty() && incoming.firstAudibleMs <= latestIncomingStart) {
            incomingCandidates = listOf(IndexedValue(0, incoming.firstAudibleMs))
        }
        if (outgoingCandidates.isEmpty() || incomingCandidates.isEmpty()) return null

        var best: Candidate? = null
        outgoingCandidates.forEach { outgoingBeat ->
            incomingCandidates.forEach { incomingBeat ->
                val score = candidatePenalty(
                    outgoingStart = outgoingBeat.value,
                    incomingStart = incomingBeat.value,
                    outgoingBeatIndex = outgoingBeat.index,
                    incomingBeatIndex = incomingBeat.index,
                    targetOutgoingStart = targetOutgoingStart,
                    durationMs = durationMs,
                    outgoingRate = rates.outgoingEnd,
                    incomingRate = rates.incomingStart,
                    skipsQuietOpening = settings.skipQuietOpening,
                    outgoing = outgoing,
                    incoming = incoming,
                )
                if (best == null || score < best!!.score) {
                    best = Candidate(score, outgoingBeat.value, incomingBeat.value)
                }
            }
        }
        val selected = best ?: return null
        return MeloXAutoMixPlan(
            durationMs = durationMs,
            outgoingStartMs = selected.outgoingStartMs,
            incomingStartMs = if (settings.skipQuietOpening) {
                max(selected.incomingStartMs, incoming.firstAudibleMs)
            } else selected.incomingStartMs,
            outgoingStartRate = 1f,
            outgoingEndRate = rates.outgoingEnd,
            incomingStartRate = rates.incomingStart,
            incomingEndRate = 1f,
            usedSmartAnalysis = true,
        )
    }

    private fun candidatePenalty(
        outgoingStart: Long,
        incomingStart: Long,
        outgoingBeatIndex: Int,
        incomingBeatIndex: Int,
        targetOutgoingStart: Long,
        durationMs: Long,
        outgoingRate: Float,
        incomingRate: Float,
        skipsQuietOpening: Boolean,
        outgoing: MeloXAutoMixTrackAnalysis,
        incoming: MeloXAutoMixTrackAnalysis,
    ): Double {
        val timing = (abs(outgoingStart - targetOutgoingStart).toDouble() /
            max(durationMs * 1.75, 12_000.0)).coerceIn(0.0, 1.0)
        val phrase = (phrasePenalty(outgoingBeatIndex) + phrasePenalty(incomingBeatIndex)) / 2.0
        val boundary = 1.0 - (
            boundaryStrength(outgoing, outgoingStart) + boundaryStrength(incoming, incomingStart)
            ) / 2.0
        val outgoingContour = contourPenalty(outgoing, outgoingStart, expectsRise = false)
        val incomingContour = contourPenalty(incoming, incomingStart, expectsRise = true)
        val quietIncoming = if (skipsQuietOpening) {
            ((.20 - meanEnergy(incoming, incomingStart, 1_500L)) / .20).coerceIn(0.0, 1.0)
        } else 0.0
        val quietOutgoing = ((.12 - meanEnergy(outgoing, outgoingStart, 1_500L)) / .12).coerceIn(0.0, 1.0)
        val overlap = overlapPenalty(
            outgoingStart, incomingStart, durationMs, outgoingRate, incomingRate, outgoing, incoming,
        )
        val tempo = (abs(incomingRate - 1f) / .08f).coerceIn(0f, 1f)
        return timing * .25 + phrase * .08 + boundary * .19 +
            (outgoingContour + incomingContour) / 2.0 * .13 + quietIncoming * .09 +
            quietOutgoing * .05 + overlap * .17 + tempo * .04
    }

    private fun overlapPenalty(
        outgoingStart: Long,
        incomingStart: Long,
        durationMs: Long,
        outgoingRate: Float,
        incomingRate: Float,
        outgoing: MeloXAutoMixTrackAnalysis,
        incoming: MeloXAutoMixTrackAnalysis,
    ): Double {
        val sampleCount = 16
        val outgoingReference = meanEnergy(outgoing, outgoingStart, 1_000L)
        val incomingEnd = incomingStart + contentOffset(durationMs, incomingRate, 1f, 1.0)
        val incomingReference = meanEnergy(incoming, max(incomingEnd - 1_000L, incomingStart), 1_000L)
        var continuity = 0.0
        var lowCrowding = 0.0
        var midCrowding = 0.0
        var spectralMismatch = 0.0
        for (sample in 0..sampleCount) {
            val progress = sample.toDouble() / sampleCount
            val outgoingTime = outgoingStart + contentOffset(durationMs, 1f, outgoingRate, progress)
            val incomingTime = incomingStart + contentOffset(durationMs, incomingRate, 1f, progress)
            val outgoingFrame = outgoing.frameAt(outgoingTime) ?: continue
            val incomingFrame = incoming.frameAt(incomingTime) ?: continue
            val gains = MeloXAutoMixEnvelope.gains(progress, MeloXAutoMixFadeCurve.EqualPower)
            val combined = outgoingFrame.energy * gains.outgoing + incomingFrame.energy * gains.incoming
            val expected = outgoingReference + (incomingReference - outgoingReference) * progress
            continuity += abs(combined - expected).coerceAtMost(1.0)
            lowCrowding += min(
                outgoingFrame.lowRatio * gains.outgoing,
                incomingFrame.lowRatio * gains.incoming,
            )
            midCrowding += min(
                max(outgoingFrame.midRatio - .3f, 0f) * outgoingFrame.energy * gains.outgoing,
                max(incomingFrame.midRatio - .3f, 0f) * incomingFrame.energy * gains.incoming,
            )
            spectralMismatch += (
                abs(outgoingFrame.lowRatio - incomingFrame.lowRatio) +
                    abs(outgoingFrame.midRatio - incomingFrame.midRatio) +
                    abs(outgoingFrame.highRatio - incomingFrame.highRatio)
                ) * min(gains.outgoing, gains.incoming)
        }
        val divisor = (sampleCount + 1).toDouble()
        return (continuity / divisor * .43 + lowCrowding / divisor * .24 +
            midCrowding / divisor * .19 + spectralMismatch / divisor * .14).coerceIn(0.0, 1.0)
    }

    private fun contentOffset(durationMs: Long, startRate: Float, endRate: Float, progress: Double): Long {
        val p = progress.coerceIn(0.0, 1.0)
        val rateAtProgress = MeloXAutoMixEnvelope.rate(startRate, endRate, p)
        return (durationMs * p * (startRate + rateAtProgress) / 2.0).toLong()
    }

    private fun meanEnergy(analysis: MeloXAutoMixTrackAnalysis, startMs: Long, durationMs: Long): Double {
        val end = startMs + durationMs
        val samples = analysis.frames.asSequence().filter { it.timeMs in startMs..end }.map { it.energy.toDouble() }.toList()
        return if (samples.isEmpty()) analysis.frameAt(startMs)?.energy?.toDouble() ?: 0.0 else samples.average()
    }

    private fun boundaryStrength(analysis: MeloXAutoMixTrackAnalysis, timeMs: Long): Double {
        val frame = analysis.frameAt(timeMs) ?: return 0.0
        return (frame.novelty * .58f + frame.onset * .42f).coerceIn(0f, 1f).toDouble()
    }

    private fun contourPenalty(analysis: MeloXAutoMixTrackAnalysis, timeMs: Long, expectsRise: Boolean): Double {
        val before = meanEnergy(analysis, (timeMs - 1_500L).coerceAtLeast(0L), 1_200L)
        val after = meanEnergy(analysis, timeMs + 300L, 1_200L)
        val slope = after - before
        return if (expectsRise) (-slope * 2.5 + .35).coerceIn(0.0, 1.0)
        else (slope * 2.5 + .35).coerceIn(0.0, 1.0)
    }

    private fun phrasePenalty(beatIndex: Int): Double = when (beatIndex.mod(16)) {
        0 -> 0.0
        4, 8, 12 -> .35
        else -> .85
    }

    private fun tempoRates(outgoingBpm: Double, incomingBpm: Double, settings: MeloXAutoMixSettings): TempoRates {
        val alignedIncoming = listOf(incomingBpm / 2, incomingBpm, incomingBpm * 2)
            .minBy { abs(it - outgoingBpm) }
        if (!settings.tempoMatching) return TempoRates(alignedIncoming, 1f, 1f)
        val incomingStart = outgoingBpm / alignedIncoming
        if (abs(incomingStart - 1.0) > settings.maxTempoAdjustment) {
            return TempoRates(alignedIncoming, 1f, 1f)
        }
        return TempoRates(
            alignedIncomingBpm = alignedIncoming,
            outgoingEnd = (alignedIncoming / outgoingBpm).coerceIn(.92, 1.08).toFloat(),
            incomingStart = incomingStart.coerceIn(.92, 1.08).toFloat(),
        )
    }

    private data class TempoRates(
        val alignedIncomingBpm: Double,
        val outgoingEnd: Float,
        val incomingStart: Float,
    )

    private data class Candidate(
        val score: Double,
        val outgoingStartMs: Long,
        val incomingStartMs: Long,
    )
}
