package com.lladlam.melox.platform.xiaomi

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.Keep
import rikka.shizuku.Shizuku

/**
 * Long-lived Shizuku user-service binder.
 *
 * HyperOS drops the client registration after a cold start unless a daemon user service
 * stays bound. This service does no privileged work; the firewall calls remain in-process
 * through wrapped system binders. Permission is never requested from this path.
 */
@Keep
class ShizukuKeepAliveService(@Suppress("UNUSED_PARAMETER") context: Context) : Binder() {
    companion object {
        private const val TAG = "MeloXShizukuIsland"
        private const val RETRY_MS = 5_000L

        private val handler = Handler(Looper.getMainLooper())
        private val retry = Runnable { ensureBound(boundContext) }

        @Volatile
        private var boundContext: Context? = null

        @Volatile
        private var bound = false

        private val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                bound = true
                Log.i(TAG, "Shizuku user service connected")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                bound = false
                Log.w(TAG, "Shizuku user service disconnected")
                handler.removeCallbacks(retry)
                handler.postDelayed(retry, RETRY_MS)
            }
        }

        fun ensureBound(context: Context?) {
            val appContext = context?.applicationContext ?: return
            boundContext = appContext
            handler.removeCallbacks(retry)
            if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
                handler.postDelayed(retry, RETRY_MS)
                return
            }
            val granted = runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            if (!granted) {
                handler.postDelayed(retry, RETRY_MS)
                return
            }
            if (bound) return
            val args = Shizuku.UserServiceArgs(
                ComponentName(appContext.packageName, ShizukuKeepAliveService::class.java.name),
            )
                .tag("melox_keepalive")
                .daemon(true)
                .processNameSuffix("shizuku")
                .debuggable(false)
                .version(1)
            runCatching { Shizuku.bindUserService(args, connection) }
                .onFailure {
                    bound = false
                    Log.w(TAG, "Unable to bind Shizuku keepalive", it)
                    handler.postDelayed(retry, RETRY_MS)
                }
        }
    }
}
