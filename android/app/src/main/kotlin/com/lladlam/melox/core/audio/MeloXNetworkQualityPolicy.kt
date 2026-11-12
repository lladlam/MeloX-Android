package com.lladlam.melox.core.audio

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Optional network-aware playback quality. The user's explicit quality remains
 * the stored default; this policy only supplies the effective tier used while
 * enqueueing/resolving playback.
 */
object MeloXNetworkQualityPolicy {
    private const val PREFERENCES = "melox_network_quality"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_WIFI = "wifi_quality"
    private const val KEY_MOBILE = "mobile_quality"

    fun isEnabled(context: Context): Boolean = preferences(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun wifiQuality(context: Context): MusicQuality =
        quality(context, KEY_WIFI) ?: MusicQualityPreferences.read(context)

    fun mobileQuality(context: Context): MusicQuality =
        quality(context, KEY_MOBILE) ?: MusicQualityPreferences.read(context)

    fun setWifiQuality(context: Context, quality: MusicQuality) {
        preferences(context).edit().putString(KEY_WIFI, quality.apiLevel).apply()
    }

    fun setMobileQuality(context: Context, quality: MusicQuality) {
        preferences(context).edit().putString(KEY_MOBILE, quality.apiLevel).apply()
    }

    /** Effective playback tier; falls back to the user's explicit choice when disabled or offline. */
    fun effectiveQuality(context: Context): MusicQuality {
        return resolveEffectiveQuality(
            enabled = isEnabled(context),
            cellular = isCellular(context),
            wifi = wifiQuality(context),
            mobile = mobileQuality(context),
            fallback = MusicQualityPreferences.read(context),
        )
    }

    /** null when the active transport cannot be classified. */
    fun isCellular(context: Context, allowMeteredFallback: Boolean = true): Boolean? {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return null
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return null
        val wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return classifyTransport(
            wifi = wifi,
            cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
            meteredFallback = allowMeteredFallback && manager.isActiveNetworkMetered,
        )
    }

    internal fun classifyTransport(wifi: Boolean, cellular: Boolean, meteredFallback: Boolean): Boolean? {
        if (wifi) return false
        if (cellular) return true
        // VPNs may hide the underlying transport; metered is the safest proxy.
        return if (meteredFallback) true else null
    }

    private fun quality(context: Context, key: String): MusicQuality? =
        MusicQuality.fromApiLevel(preferences(context).getString(key, null))

    internal fun resolveEffectiveQuality(
        enabled: Boolean,
        cellular: Boolean?,
        wifi: MusicQuality,
        mobile: MusicQuality,
        fallback: MusicQuality,
    ): MusicQuality = when {
        !enabled -> fallback
        cellular == true -> mobile
        cellular == false -> wifi
        else -> fallback
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
