package com.lladlam.melox.ui.player

import android.content.Context
import android.graphics.Bitmap
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pre-blur a bitmap using a simple box blur (average filter).
 *
 * The bitmap is first down-sampled to [maxDimension] on its longest side
 * (preserving aspect ratio) so the blur runs on far fewer pixels.
 * The blur is applied [iterations] times for a smoother result.
 *
 * Must be called from a background thread.
 */
fun preBlurBitmap(
    context: Context,
    bitmap: Bitmap,
    radius: Float,
    maxDimension: Int = 256,
    iterations: Int = 2,
): Bitmap {
    val r = radius.roundToInt().coerceIn(1, 25)

    val scale = min(
        maxDimension.toFloat() / bitmap.width.coerceAtLeast(1),
        maxDimension.toFloat() / bitmap.height.coerceAtLeast(1),
    ).coerceAtMost(1f)
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
    }

    val w = scaled.width
    val h = scaled.height
    val pixels = IntArray(w * h)
    scaled.getPixels(pixels, 0, w, 0, 0, w, h)

    repeat(iterations) {
        boxBlur(pixels, w, h, r)
    }

    scaled.setPixels(pixels, 0, w, 0, 0, w, h)
    return scaled
}

/**
 * Single-pass box blur using cumulative sums (integral image).
 * O(n) per pixel regardless of radius.
 */
private fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int) {
    if (radius < 1 || w == 0 || h == 0) return

    val result = IntArray(w * h)

    // Horizontal pass
    for (y in 0 until h) {
        var rSum = 0; var gSum = 0; var bSum = 0; var aSum = 0
        var count = 0

        // Initialize window for x = 0
        for (dx in -radius..radius) {
            val x = dx.coerceIn(0, w - 1)
            val p = pixels[y * w + x]
            rSum += (p shr 16) and 0xFF
            gSum += (p shr 8) and 0xFF
            bSum += p and 0xFF
            aSum += (p shr 24) and 0xFF
            count++
        }

        for (x in 0 until w) {
            result[y * w + x] = (aSum / count shl 24) or
                (rSum / count shl 16) or
                (gSum / count shl 8) or
                (bSum / count)

            // Slide window: remove left edge, add right edge
            val removeX = (x - radius).coerceIn(0, w - 1)
            val addX = (x + radius + 1).coerceIn(0, w - 1)
            val removeP = pixels[y * w + removeX]
            val addP = pixels[y * w + addX]
            rSum += (addP shr 16 and 0xFF) - (removeP shr 16 and 0xFF)
            gSum += (addP shr 8 and 0xFF) - (removeP shr 8 and 0xFF)
            bSum += (addP and 0xFF) - (removeP and 0xFF)
            aSum += (addP shr 24 and 0xFF) - (removeP shr 24 and 0xFF)
        }
    }

    // Vertical pass on horizontal result
    for (x in 0 until w) {
        var rSum = 0; var gSum = 0; var bSum = 0; var aSum = 0
        var count = 0

        for (dy in -radius..radius) {
            val y = dy.coerceIn(0, h - 1)
            val p = result[y * w + x]
            rSum += (p shr 16) and 0xFF
            gSum += (p shr 8) and 0xFF
            bSum += p and 0xFF
            aSum += (p shr 24) and 0xFF
            count++
        }

        for (y in 0 until h) {
            pixels[y * w + x] = (aSum / count shl 24) or
                (rSum / count shl 16) or
                (gSum / count shl 8) or
                (bSum / count)

            val removeY = (y - radius).coerceIn(0, h - 1)
            val addY = (y + radius + 1).coerceIn(0, h - 1)
            val removeP = result[removeY * w + x]
            val addP = result[addY * w + x]
            rSum += (addP shr 16 and 0xFF) - (removeP shr 16 and 0xFF)
            gSum += (addP shr 8 and 0xFF) - (removeP shr 8 and 0xFF)
            bSum += (addP and 0xFF) - (removeP and 0xFF)
            aSum += (addP shr 24 and 0xFF) - (removeP shr 24 and 0xFF)
        }
    }
}
