package dev.ytrear.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Keeps the allowlisted proxy MediaSession alive while the selected player is active.
 */
class RearControlService : Service() {

    private var observing = false

    private val mediaListener = MediaHub.Listener { state ->
        RearController.render(this, state)
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForeground(NOTIF_ID, foregroundNotification())
        beginObserving()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, foregroundNotification())
        beginObserving()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun beginObserving() {
        val attached = MediaHub.attach(this)
        if (!observing) {
            MediaHub.addListener(mediaListener)
            observing = true
        }
        Probe.log("CONTROL SERVICE: active; session access=$attached")
    }

    override fun onDestroy() {
        if (observing) {
            MediaHub.removeListener(mediaListener)
            observing = false
        }
        isRunning = false
        if (!MediaNotificationListener.isConnected) {
            NativeMediaProxy.clear(this)
            MediaHub.detach()
        }
        super.onDestroy()
    }

    private fun foregroundNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Rear controls service",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(
                "${PlayerSelection.selectedLabel(this) ?: "Selected player"} rear controller"
            )
            .setContentText("Allowlisted media proxy is active")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL = "rear_control_service"
        private const val NOTIF_ID = 76

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, RearControlService::class.java))
        }
    }
}
