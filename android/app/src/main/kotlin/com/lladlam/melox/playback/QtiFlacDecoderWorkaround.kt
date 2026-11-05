package com.lladlam.melox.playback

import android.os.Build
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Hides Qualcomm's `OMX.qti.audio.decoder.flac` on Android 8.1 and older, so
 * FLAC falls through to the bundled FFmpeg audio renderer.
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
 */
@UnstableApi
internal object QtiFlacDecoderWorkaround {
    private const val QTI_FLAC_DECODER = "OMX.qti.audio.decoder.flac"

    val mediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder,
        )
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1 && mimeType == MimeTypes.AUDIO_FLAC) {
            decoders.filterNot { it.name == QTI_FLAC_DECODER }
        } else {
            decoders
        }
    }
}
