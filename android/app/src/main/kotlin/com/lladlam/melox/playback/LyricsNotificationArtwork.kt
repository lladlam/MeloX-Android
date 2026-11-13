package com.lladlam.melox.playback

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Retains only the current song's notification-sized artwork. Called on the service thread. */
internal class LyricsNotificationArtwork {
    private var source: ByteArray? = null
    private var bitmap: Bitmap? = null

    fun get(bytes: ByteArray?): Bitmap? {
        if (bytes == null) {
            clear()
            return null
        }
        if (source === bytes) return bitmap
        source = bytes
        bitmap = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                inSampleSize = notificationArtworkSampleSize(bounds.outWidth, bounds.outHeight)
            })
        }.getOrNull()
        return bitmap
    }

    fun clear() {
        source = null
        bitmap = null
    }
}

/** Power-of-two sampling prevents full-resolution artwork being decoded for a small icon. */
internal fun notificationArtworkSampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (width / sample > 256 || height / sample > 256) {
        if (sample >= 1 shl 28) break
        sample *= 2
    }
    return sample
}
