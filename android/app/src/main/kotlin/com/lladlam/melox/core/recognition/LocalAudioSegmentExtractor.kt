package com.lladlam.melox.core.recognition

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlinx.coroutines.ensureActive

/** Decodes a short mono 8 kHz segment from a local content/file URI. */
internal object LocalAudioSegmentExtractor {
    suspend fun extract(
        context: Context,
        uri: Uri,
        durationMs: Long,
        segmentMs: Long = 10_000L,
        segmentStartMs: Long? = null,
    ): FloatArray =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val extractor = MediaExtractor()
            var decoder: MediaCodec? = null
            try {
                extractor.setDataSource(context, uri, null)
                val track = (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: error("本地文件没有音轨")
                extractor.selectTrack(track)
                val inputFormat = extractor.getTrackFormat(track)
                val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("音频格式未知")
                val sourceDurationMs = durationMs.takeIf { it > 0L }
                    ?: (inputFormat.getLong(MediaFormat.KEY_DURATION) / 1_000L)
                val lengthMs = segmentMs.coerceAtMost(sourceDurationMs.coerceAtLeast(1L))
                val startMs = (segmentStartMs ?: ((sourceDurationMs - lengthMs) / 2L))
                    .coerceIn(0L, (sourceDurationMs - lengthMs).coerceAtLeast(0L))
                extractor.seekTo(startMs * 1_000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                val codec = MediaCodec.createDecoderByType(mime)
                decoder = codec
                codec.configure(inputFormat, null, null, 0)
                codec.start()
                val output = ArrayList<Float>(8_000 * (lengthMs / 1_000L).toInt())
                val info = MediaCodec.BufferInfo()
                var inputEnded = false
                var outputEnded = false
                var format = inputFormat
                while (!outputEnded) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (!inputEnded) {
                        val inputIndex = codec.dequeueInputBuffer(10_000L)
                        if (inputIndex >= 0) {
                            val buffer = codec.getInputBuffer(inputIndex) ?: continue
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0 || extractor.sampleTime >= (startMs + lengthMs) * 1_000L) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000L)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> format = codec.outputFormat
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (outputIndex >= 0) {
                            codec.getOutputBuffer(outputIndex)?.let { buffer ->
                                if (info.size > 0) {
                                    buffer.position(info.offset)
                                    buffer.limit(info.offset + info.size)
                                    appendMono8k(
                                        output,
                                        buffer.slice().order(ByteOrder.LITTLE_ENDIAN),
                                        format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                                        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                                        info.presentationTimeUs / 1_000L,
                                        startMs,
                                    )
                                }
                            }
                            outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
                output.toFloatArray()
            } finally {
                runCatching { decoder?.stop() }
                runCatching { decoder?.release() }
                extractor.release()
            }
        }

    private fun appendMono8k(
        output: MutableList<Float>,
        buffer: java.nio.ByteBuffer,
        sampleRate: Int,
        channels: Int,
        presentationTimeMs: Long,
        startMs: Long,
    ) {
        if (sampleRate <= 0 || channels <= 0) return
        val sampleCount = buffer.remaining() / 2 / channels
        val targetStart = ((presentationTimeMs - startMs).coerceAtLeast(0L) * 8_000L / 1_000L).toInt()
        while (output.size < targetStart) output += 0f
        for (sourceIndex in 0 until sampleCount) {
            var sum = 0
            repeat(channels) { sum += buffer.short.toInt() }
            val target = targetStart + (sourceIndex * 8_000.0 / sampleRate).roundToInt()
            while (output.size <= target) output += 0f
            output[target] = sum.toFloat() / channels / 32_768f
        }
    }
}
