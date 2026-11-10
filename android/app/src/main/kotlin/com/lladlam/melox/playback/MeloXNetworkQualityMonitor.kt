package com.lladlam.melox.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.lladlam.melox.core.audio.MeloXNetworkQualityPolicy
import com.lladlam.melox.core.audio.MusicQualityRuntime

/** Applies the Wi-Fi/mobile preset when the active transport changes. */
object MeloXNetworkQualityMonitor {
    private const val TAG = "MeloXNetworkQuality"
    private var registered = false
    private var callback: ConnectivityManager.NetworkCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Synchronized
    fun start(context: Context) {
        if (registered) return
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        val next = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh(appContext)
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                refresh(appContext)
            override fun onLost(network: Network) = refresh(appContext)
        }
        runCatching {
            manager.registerDefaultNetworkCallback(next)
            callback = next
            registered = true
            refresh(appContext)
        }.onFailure { Log.w(TAG, "Unable to register network quality monitor", it) }
    }

    /** Call after the setting is toggled or a preset changes. */
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        mainHandler.post { apply(appContext) }
    }

    private fun apply(context: Context) {
        val quality = if (MeloXNetworkQualityPolicy.isEnabled(context)) {
            MeloXNetworkQualityPolicy.effectiveQuality(context)
        } else {
            com.lladlam.melox.core.audio.MusicQualityPreferences.read(context)
        }
        if (quality == MusicQualityRuntime.selected) return
        MusicQualityRuntime.selected = quality
        MusicQualityRuntime.clear()
        ProviderPlaybackQualityRuntime.clear()
        runCatching { PlaybackCommands.applyNetworkQuality(context, quality) }
            .onFailure { Log.w(TAG, "Unable to apply network quality $quality", it) }
    }
}
