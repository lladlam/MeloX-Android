/*
 * Sonify BitmapResolver (GPL-3.0), adapted for MeloX.
 * Upstream: yos/music/player/code/utils/others/BitmapResolver.kt @ 209e1aa.
 */
package com.lladlam.melox.ui.sonify

import android.graphics.Bitmap

object BitmapResolver {
    fun bitmapCompress(bitmap: Bitmap, lowQuality: Boolean = false): Bitmap {
        val px = if (lowQuality) 4 else if (SonifySettings.NowplayingBackgroundEffect) 96 else 32
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var compressedBitmap = bitmap
        val size = minOf(originalWidth, originalHeight)
        val xOffset = (originalWidth - size) / 2
        val yOffset = (originalHeight - size) / 2
        val squareBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)
        if (size > px) {
            val scaleFactor = size / px
            val scaledSize = size / scaleFactor
            compressedBitmap = Bitmap.createScaledBitmap(squareBitmap, scaledSize, scaledSize, true)
        }
        return compressedBitmap.copy(Bitmap.Config.RGB_565, false)
    }
}
