package com.lladlam.melox.playback

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object MeloXPlaybackModeRuntime {
    var heartModeActive by mutableStateOf(false)
        internal set
    var shuffleEnabled by mutableStateOf(false)
        internal set
    var autoplayEnabled by mutableStateOf(false)
        internal set
    var autoMixEnabled by mutableStateOf(false)
        internal set
    var smartQueueEnabled by mutableStateOf(false)
        internal set
}

object MeloXPlaybackModePreferences {
    private const val NAME = "melox_playback_modes"
    private const val KEY_SHUFFLE = "shuffle"
    private const val KEY_AUTOPLAY = "autoplay"
    private const val KEY_AUTOMIX = "auto_mix"
    private const val KEY_SMART_QUEUE = "smart_queue"

    internal fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Loads the process snapshot once so playback hot paths never poll SharedPreferences. */
    fun initialize(context: Context) {
        val preferences = preferences(context)
        MeloXPlaybackModeRuntime.shuffleEnabled = preferences.getBoolean(KEY_SHUFFLE, false)
        MeloXPlaybackModeRuntime.autoplayEnabled = preferences.getBoolean(KEY_AUTOPLAY, false)
        MeloXPlaybackModeRuntime.autoMixEnabled = preferences.getBoolean(KEY_AUTOMIX, false)
        MeloXPlaybackModeRuntime.smartQueueEnabled = preferences.getBoolean(KEY_SMART_QUEUE, false)
    }

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
        MeloXPlaybackModeRuntime.shuffleEnabled = enabled
        preferences(context)
            .edit().putBoolean(KEY_SHUFFLE, enabled).apply()
    }

    fun setAutoplay(context: Context, enabled: Boolean) {
        MeloXPlaybackModeRuntime.autoplayEnabled = enabled
        preferences(context)
            .edit().putBoolean(KEY_AUTOPLAY, enabled).apply()
    }

    fun setAutoMix(context: Context, enabled: Boolean) {
        MeloXPlaybackModeRuntime.autoMixEnabled = enabled
        preferences(context)
            .edit().putBoolean(KEY_AUTOMIX, enabled).apply()
    }

    fun smartQueue(context: Context): Boolean =
        preferences(context)
            .getBoolean(KEY_SMART_QUEUE, false)

    fun setSmartQueue(context: Context, enabled: Boolean) {
        MeloXPlaybackModeRuntime.smartQueueEnabled = enabled
        preferences(context)
            .edit().putBoolean(KEY_SMART_QUEUE, enabled).apply()
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
        MeloXPlaybackModeRuntime.shuffleEnabled = false
        MeloXPlaybackModeRuntime.autoplayEnabled = false
        MeloXPlaybackModeRuntime.autoMixEnabled = false
        MeloXPlaybackModeRuntime.smartQueueEnabled = false
        preferences(context).edit().clear().apply()
    }
}
