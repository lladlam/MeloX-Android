package com.lladlam.melox.platform.xiaomi

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Publishes the HyperOS Focus notification from its own special-use foreground service.
 *
 * HyperOS treats a Focus payload attached through an ordinary notify() as a regular
 * notification. The payload can also carry artwork, so it stays in-process instead of
 * crossing a binder via the start intent.
 */
class XiaomiSuperIslandLyricService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                pendingNotification = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                val notification = pendingNotification
                if (notification == null) {
                    running = false
                    stopSelf()
                    return START_NOT_STICKY
                }
                val started = runCatching { startWithFocus(notification) }
                    .onFailure { Log.w(TAG, "Unable to start Super Island foreground service", it) }
                    .isSuccess
                running = started
                if (!started) stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    private fun startWithFocus(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.i(TAG, "Super Island lyric foreground service started with Focus payload")
    }

    companion object {
        private const val TAG = "MeloXSuperIsland"
        const val NOTIFICATION_ID = 1703
        private const val ACTION_PUBLISH = "com.lladlam.melox.xiaomi.SUPER_ISLAND_PUBLISH"
        private const val ACTION_STOP = "com.lladlam.melox.xiaomi.SUPER_ISLAND_STOP"

        @Volatile
        private var pendingNotification: Notification? = null

        @Volatile
        private var running = false

        fun publish(context: Context, notification: Notification) {
            pendingNotification = notification
            val appContext = context.applicationContext
            val intent = Intent(appContext, XiaomiSuperIslandLyricService::class.java)
                .setAction(ACTION_PUBLISH)
            val updated = if (running) {
                runCatching { appContext.startService(intent) }.isSuccess
            } else {
                false
            }
            if (!updated) {
                val foreground = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appContext.startForegroundService(intent)
                    } else {
                        appContext.startService(intent)
                    }
                }.onFailure { Log.w(TAG, "Unable to start Super Island lyric service", it) }
                    .isSuccess
                if (!foreground) running = false
            }
        }

        fun stop(context: Context) {
            pendingNotification = null
            running = false
            val appContext = context.applicationContext
            val intent = Intent(appContext, XiaomiSuperIslandLyricService::class.java)
                .setAction(ACTION_STOP)
            runCatching { appContext.startService(intent) }
                .onFailure {
                    runCatching {
                        appContext.getSystemService(android.app.NotificationManager::class.java)
                            .cancel(NOTIFICATION_ID)
                    }
                }
        }
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }
}
