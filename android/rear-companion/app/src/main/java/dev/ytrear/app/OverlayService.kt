package dev.ytrear.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * The real-world configuration: the rear-screen window has to survive with our app in the
 * background, which means owning it from a foreground service rather than an Activity.
 */
class OverlayService : Service() {

    private val rear by lazy { RearWindow(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, notification())
        when (intent?.action) {
            ACTION_SHOW -> show()
            ACTION_HIDE -> {
                hide()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun show() {
        val display = Displays.pickRear(this)
        if (display == null) {
            Probe.log("SERVICE: no secondary display found")
            return
        }
        Probe.log("SERVICE: targeting display ${Displays.describe(display)}")
        rear.show(display)
    }

    private fun hide() {
        rear.hide()
    }

    override fun onDestroy() {
        hide()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Rear screen", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("YTRear probe")
            .setContentText("Holding a window on the rear display")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_SHOW = "dev.ytrear.app.SHOW"
        const val ACTION_HIDE = "dev.ytrear.app.HIDE"
        private const val CHANNEL = "rear_screen"
        private const val NOTIF_ID = 42

        fun send(context: Context, action: String) {
            val i = Intent(context, OverlayService::class.java).setAction(action)
            context.startForegroundService(i)
        }
    }
}
