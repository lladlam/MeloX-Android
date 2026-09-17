package com.lladlam.melox.core.provider.spotify

import android.content.Context
import com.lladlam.melox.BuildConfig

/**
 * Spotify Client ID comes from the build when one is baked in; otherwise the user
 * supplies it in the app. The customer-facing ID is not a secret, and Chrome Login is
 * what OAuth uses here, so a plain preference is the right shape.
 */
object SpotifyClientConfig {
    private const val PreferencesName = "melox_spotify_client_config"
    private const val KeyClientId = "client_id"

    fun effective(context: Context): String =
        userValue(context) ?: BuildConfig.SPOTIFY_CLIENT_ID

    fun read(context: Context): String =
        userValue(context).orEmpty()

    fun isConfigured(context: Context): Boolean = effective(context).isNotBlank()

    fun write(context: Context, value: String) {
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit().putString(KeyClientId, value.trim()).apply()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit().remove(KeyClientId).apply()
    }

    private fun userValue(context: Context): String? = context.applicationContext
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        .getString(KeyClientId, null)
        ?.takeIf(String::isNotBlank)
}
