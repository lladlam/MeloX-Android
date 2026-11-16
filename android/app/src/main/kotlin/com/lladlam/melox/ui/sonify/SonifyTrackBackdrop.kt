/*
 * Sonify TrackBackdrop facade (GPL-3.0).
 *
 * Upstream TrackBackdrop lives in ui/navigation/TrackBackdrop.kt. Its actual
 * implementation and the cross-fade driver are in SonifyPageGradient.kt next
 * to PageGradient; this facade keeps the upstream object name available to
 * future MeloX player integration without duplicating the sampling code.
 */
package com.lladlam.melox.ui.sonify

import androidx.compose.runtime.Composable

object TrackBackdrop {
    @Composable
    fun rememberGradient(artworkUrl: String? = LocalSonifyArtworkUrl.current): TrackBackdropGradient? =
        SonifyTrackBackdrop.rememberGradient(artworkUrl)

    @Composable
    fun rememberBlendedGradient(artworkUrl: String? = LocalSonifyArtworkUrl.current): TrackBackdropGradient? =
        SonifyTrackBackdrop.rememberBlendedGradient(artworkUrl)
}
