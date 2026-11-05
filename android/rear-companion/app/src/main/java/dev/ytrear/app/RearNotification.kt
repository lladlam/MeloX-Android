package dev.ytrear.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.RemoteViews

/**
 * Renders a rear-screen widget by way of MIUI's focus-notification pipeline.
 *
 * SystemUI's FocusUtils.isFocusNotification() qualifies a notification on either
 * "miui.focus.isFocus" being true or "miui.focus.rv" holding a RemoteViews. Only then does
 * FocusCoordinator hand it to SubScreenNotificationController, which forwards it to
 * subscreencenter. The focus layer defaults to permitted, while subscreencenter separately
 * requires the installed package identity to be in Xiaomi's compiled music allowlist.
 */
object RearNotification {

    private const val CHANNEL = "rear_widget"
    const val NOTIF_ID = 77
    const val ACTION_BUTTON = "dev.ytrear.app.REAR_BUTTON"

    private const val MAX_ART_EDGE = 144

    private data class Payload(
        val title: String,
        val artist: String,
        val playing: Boolean,
        val business: String,
        val artGeneration: Int,
        val artWidth: Int,
        val artHeight: Int
    )

    @Volatile
    private var lastPayload: Payload? = null

    fun post(
        context: Context,
        title: String,
        artist: String,
        art: Bitmap? = null,
        playing: Boolean = true,
        business: String = "music",
        force: Boolean = false
    ): Boolean {
        val payload = Payload(
            title = title,
            artist = artist,
            playing = playing,
            business = business,
            artGeneration = art?.generationId ?: 0,
            artWidth = art?.width ?: 0,
            artHeight = art?.height ?: 0
        )
        if (!force && lastPayload == payload) return false

        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Rear widget", NotificationManager.IMPORTANCE_LOW)
            )
        }

        fun pending(which: String) = PendingIntent.getBroadcast(
            context,
            which.hashCode(),
            Intent(context, RearButtonReceiver::class.java)
                .setAction(ACTION_BUTTON)
                .putExtra(RearButtonReceiver.EXTRA_BUTTON, which),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val scaledArt = scaleArtwork(art)
        val remoteViews = RemoteViews(context.packageName, R.layout.rear_widget).apply {
            setTextViewText(R.id.title, title)
            setTextViewText(R.id.artist, artist)
            if (scaledArt != null) setImageViewBitmap(R.id.art, scaledArt)
            setImageViewResource(
                R.id.playpause,
                if (playing) R.drawable.ic_pause else R.drawable.ic_play
            )
            setOnClickPendingIntent(R.id.prev, pending("prev"))
            setOnClickPendingIntent(R.id.playpause, pending("playpause"))
            setOnClickPendingIntent(R.id.next, pending("next"))
        }

        // The business declaration is required after the earlier exact package allowlist gate.
        val rearParam = """
        {
          "business": "$business",
          "index": 0,
          "priority": 500,
          "enableFloat": true,
          "show_time_tip": true,
          "disable_popup": false
        }
        """.trimIndent()

        val extras = Bundle().apply {
            putString("miui.rear.param", rearParam)
            // Either of these qualifies us as a focus notification.
            putBoolean("miui.focus.isFocus", true)
            putParcelable("miui.focus.rv", remoteViews)
            putParcelable("miui.focus.rvNight", remoteViews)
            putParcelable("miui.focus.rv.tiny", remoteViews)
            putParcelable("miui.focus.rv.tinyNight", remoteViews)
            putParcelable("miui.focus.rv.fullAod", remoteViews)
            // Rear-screen variants read by subscreencenter.
            putParcelable("miui.rear.rv", remoteViews)
            putParcelable("miui.rear.rvAOD", remoteViews)
            putString("miui.focus.ticker", "$title — $artist")
        }

        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(artist)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .addExtras(extras)
            .build()

        nm.notify(NOTIF_ID, n)
        lastPayload = payload
        Probe.log("REAR NOTIF: posted business=$business isFocus=true +rv ('$title' / '$artist')")
        return true
    }

    private fun scaleArtwork(source: Bitmap?): Bitmap? {
        if (source == null || source.isRecycled || source.width <= 0 || source.height <= 0) {
            return null
        }
        if (source.width <= MAX_ART_EDGE && source.height <= MAX_ART_EDGE) return source

        val scale = minOf(
            MAX_ART_EDGE.toFloat() / source.width,
            MAX_ART_EDGE.toFloat() / source.height
        )
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        lastPayload = null
        Probe.log("REAR NOTIF: cancelled")
    }
}
