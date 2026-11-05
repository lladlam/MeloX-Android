package com.lladlam.melox.playback

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QtiFlacDecoderWorkaroundTest {
    private val qti = "OMX.qti.audio.decoder.flac"
    private val google = "OMX.google.flac.decoder"
    private val thinkSmart = "ThinkSmart View"

    @Test
    fun hidesQtiOnlyOnAffectedModelAtApi27OrLower() {
        assertTrue(
            QtiFlacDecoderWorkaround.shouldHideQtiFlacDecoder(
                mimeType = MimeTypes.AUDIO_FLAC,
                sdkInt = 27,
                model = thinkSmart,
                decoderNames = listOf(qti),
            ),
        )
        assertTrue(
            QtiFlacDecoderWorkaround.shouldHideQtiFlacDecoder(
                mimeType = MimeTypes.AUDIO_FLAC,
                sdkInt = 24,
                model = "Lenovo ThinkSmart View",
                decoderNames = listOf(qti),
            ),
        )
    }

    @Test
    fun keepsQtiForOtherModelsAndNewerApis() {
        assertFalse(
            QtiFlacDecoderWorkaround.shouldHideQtiFlacDecoder(
                mimeType = MimeTypes.AUDIO_FLAC,
                sdkInt = 27,
                model = "Pixel 3",
                decoderNames = listOf(qti),
            ),
        )
        assertFalse(
            QtiFlacDecoderWorkaround.shouldHideQtiFlacDecoder(
                mimeType = MimeTypes.AUDIO_FLAC,
                sdkInt = 28,
                model = thinkSmart,
                decoderNames = listOf(qti),
            ),
        )
    }

    @Test
    fun keepsQtiWhenAnotherFlacDecoderExistsOrMimeIsDifferent() {
        assertFalse(
            QtiFlacDecoderWorkaround.shouldHideQtiFlacDecoder(
                mimeType = MimeTypes.AUDIO_FLAC,
                sdkInt = 27,
                model = thinkSmart,
                decoderNames = listOf(qti, google),
            ),
        )
        assertFalse(
            QtiFlacDecoderWorkaround.shouldHideQtiFlacDecoder(
                mimeType = MimeTypes.AUDIO_MPEG,
                sdkInt = 27,
                model = thinkSmart,
                decoderNames = listOf(qti),
            ),
        )
        assertFalse(
            QtiFlacDecoderWorkaround.shouldHideQtiFlacDecoder(
                mimeType = MimeTypes.AUDIO_FLAC,
                sdkInt = 27,
                model = thinkSmart,
                decoderNames = emptyList(),
            ),
        )
    }
}
