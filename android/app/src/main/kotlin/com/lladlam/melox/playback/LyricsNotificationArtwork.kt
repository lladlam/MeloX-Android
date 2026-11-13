package com.lladlam.melox.playback

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Retains only the current song's notification-sized artwork. Called on the service thread. */
internal class LyricsNotificationArtwork {
    private var source: ByteArray? = null
    private var bitmap: Bitmap? = null
    private var pending: ByteArray? = null
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "melox-notification-artwork").apply { isDaemon = true }
    }
    private val onReady = java.util.concurrent.atomic.AtomicReference<((Bitmap) -> Unit)?>(null)

    fun current(): Bitmap? = bitmap

    fun isCurrent(bytes: ByteArray): Boolean = source === bytes

    /** Decode once per distinct byte array; the queued poster coalesces lyric ticks. */
    fun enqueue(bytes: ByteArray, onDecoded: (Bitmap) -> Unit) {
        if (source === bytes || pending === bytes) return
        pending = bytes
        onReady.set(onDecoded)
        executor.execute {
            val decoded = runCatching { decode(bytes) }.getOrNull()
            if (pending !== bytes) return@execute
            if (decoded != null) {
                source = bytes
                bitmap = decoded
                pending = null
                onReady.getAndSet(null)?.invoke(decoded)
            } else {
                pending = null
            }
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = notificationArtworkSampleSize(bounds.outWidth, bounds.outHeight)
        })
    }

    fun clear() {
        source = null
        bitmap = null
        pending = null
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
