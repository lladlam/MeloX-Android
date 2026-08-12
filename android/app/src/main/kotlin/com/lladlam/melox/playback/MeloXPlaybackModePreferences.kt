package com.lladlam.melox.playback

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object MeloXPlaybackModeRuntime {
    var heartModeActive by mutableStateOf(false)
        internal set
}

object MeloXPlaybackModePreferences {
    private const val NAME = "melox_playback_modes"
    private const val KEY_SHUFFLE = "shuffle"
    private const val KEY_AUTOPLAY = "autoplay"
    private const val KEY_AUTOMIX = "auto_mix"

    internal fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun shuffle(context: Context): Boolean =
        preferences(context)
            .getBoolean(KEY_SHUFFLE, false)

    fun autoplay(context: Context): Boolean =
        preferences(context)
            .getBoolean(KEY_AUTOPLAY, false)

    fun autoMix(context: Context): Boolean =
        preferences(context)
            .getBoolean(KEY_AUTOMIX, false)

    fun setShuffle(context: Context, enabled: Boolean) {
        preferences(context)
            .edit().putBoolean(KEY_SHUFFLE, enabled).apply()
    }

    fun setAutoplay(context: Context, enabled: Boolean) {
        preferences(context)
            .edit().putBoolean(KEY_AUTOPLAY, enabled).apply()
    }

    fun setAutoMix(context: Context, enabled: Boolean) {
        preferences(context)
            .edit().putBoolean(KEY_AUTOMIX, enabled).apply()
    }

    fun setAutoMixString(context: Context, key: String, value: String) {
        preferences(context).edit().putString(key, value).apply()
    }

    fun setAutoMixInt(context: Context, key: String, value: Int) {
        preferences(context).edit().putInt(key, value).apply()
    }

    fun setAutoMixLong(context: Context, key: String, value: Long) {
        preferences(context).edit().putLong(key, value).apply()
    }

    fun setAutoMixBoolean(context: Context, key: String, value: Boolean) {
        preferences(context).edit().putBoolean(key, value).apply()
    }

    fun setAutoMixFloat(context: Context, key: String, value: Float) {
        preferences(context).edit().putFloat(key, value).apply()
    }

    fun reset(context: Context) {
        preferences(context).edit().clear().apply()
    }
}
