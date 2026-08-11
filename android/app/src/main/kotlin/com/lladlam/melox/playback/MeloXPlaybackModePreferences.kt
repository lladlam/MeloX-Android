package com.lladlam.melox.playback

import android.content.Context

object MeloXPlaybackModePreferences {
    private const val NAME = "melox_playback_modes"
    private const val KEY_SHUFFLE = "shuffle"
    private const val KEY_AUTOPLAY = "autoplay"
    private const val KEY_AUTOMIX = "auto_mix"

    fun shuffle(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHUFFLE, false)

    fun autoplay(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTOPLAY, false)

    fun autoMix(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTOMIX, false)

    fun setShuffle(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHUFFLE, enabled).apply()
    }

    fun setAutoplay(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTOPLAY, enabled).apply()
    }

    fun setAutoMix(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTOMIX, enabled).apply()
    }
}
