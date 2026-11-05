package com.lladlam.melox.playback

import android.os.Build
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Hides Qualcomm's `OMX.qti.audio.decoder.flac` on the affected device family
 * so FLAC falls through to the bundled FFmpeg audio renderer.
 *
 * On a Lenovo ThinkSmart View (msm8953, Android 8.1) this decoder reports a
 * correct 16-bit stereo output format but delivers only half of the PCM data.
 * ACodec logs "BAD CODEC: Output format changed unexpectedly" (pcm-encoding
 * 2 -> 100). Media3 then sees buffer timestamps run 2x ahead of the audio
 * ("Unexpected audio track timestamp discontinuity" every few hundred ms), the
 * position runs at 2x, the track never reaches STATE_ENDED and the
 * stuck-playing watchdog fails it after 60 s. MP3/AAC are not affected. The
 * ROM's other FLAC decoder (`OMX.google.flac.decoder`) is disabled and cannot
 * be used: Qualcomm's ExtendedACodec rejects it during configure.
 *
 * Scope is deliberately narrow: Android 8.1 or older **and** a model in
 * [AFFECTED_MODEL_MARKERS] **and** the QTI decoder is the only listed FLAC
 * decoder. Widening this to every API <= 27 device would force software FLAC
 * decoding on devices whose hardware decoder works. Add a marker only after
 * reproducing the half-PCM / 2x-timestamp bug on that device.
 */
@UnstableApi
internal object QtiFlacDecoderWorkaround {
    private const val QTI_FLAC_DECODER = "OMX.qti.audio.decoder.flac"

    private val AFFECTED_MODEL_MARKERS = listOf("ThinkSmart")

    internal fun isAffectedModel(model: String): Boolean =
        AFFECTED_MODEL_MARKERS.any { model.contains(it, ignoreCase = true) }

    /**
     * Pure predicate so the decision can be covered by a JVM test.
     *
     * @param decoderNames names returned by [MediaCodecSelector.DEFAULT] for [mimeType].
     */
    internal fun shouldHideQtiFlacDecoder(
        mimeType: String?,
        sdkInt: Int,
        model: String,
        decoderNames: List<String>,
    ): Boolean = sdkInt <= Build.VERSION_CODES.O_MR1 &&
        mimeType == MimeTypes.AUDIO_FLAC &&
        isAffectedModel(model) &&
        decoderNames.isNotEmpty() &&
        decoderNames.all { it == QTI_FLAC_DECODER }

    val mediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder,
        )
        if (
            shouldHideQtiFlacDecoder(
                mimeType = mimeType,
                sdkInt = Build.VERSION.SDK_INT,
                model = Build.MODEL.orEmpty(),
                decoderNames = decoders.map { it.name },
            )
        ) {
            // No platform decoder remains; the FFmpeg renderer handles FLAC.
            emptyList()
        } else {
            decoders
        }
    }
}
