package com.lladlam.melox.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class MeloXThemeMode { System, Light, Dark }

/** Process-visible settings used by UI paths that need immediate recomposition. */
object MeloXSettingsRuntime {
    var themeMode by mutableStateOf(MeloXThemeMode.System)
        internal set
    var podcastsEnabled by mutableStateOf(true)
        internal set
    var listeningHistoryEnabled by mutableStateOf(true)
        internal set
    var flowingBackdropEnabled by mutableStateOf(true)
        internal set
    var artworkMotionEnabled by mutableStateOf(true)
        internal set
    var keepScreenOn by mutableStateOf(false)
        internal set
    var showLyricTranslation by mutableStateOf(true)
        internal set
    var showLyricRomanization by mutableStateOf(true)
        internal set
    var downloadLyricsEnabled by mutableStateOf(true)
        internal set
    var musicArea by mutableStateOf("全部")
        internal set
    var beatNetDebugEnabled by mutableStateOf(false)
        internal set

    private var initialized = false

    fun initialize(context: Context, force: Boolean = false) {
        if (initialized && !force) return
        initialized = true
        val app = context.applicationContext
        themeMode = runCatching {
            MeloXThemeMode.valueOf(MeloXSettingsPreferences.string(app, "theme_mode", MeloXThemeMode.System.name))
        }.getOrDefault(MeloXThemeMode.System)
        podcastsEnabled = MeloXSettingsPreferences.boolean(app, "feature_podcasts", true)
        listeningHistoryEnabled = MeloXSettingsPreferences.boolean(app, "feature_history", true)
        flowingBackdropEnabled = MeloXSettingsPreferences.boolean(app, "player_flowing_backdrop", true)
        artworkMotionEnabled = MeloXSettingsPreferences.boolean(app, "player_artwork_motion", true)
        keepScreenOn = MeloXSettingsPreferences.boolean(app, "player_keep_screen_on", false)
        showLyricTranslation = MeloXSettingsPreferences.boolean(app, "lyrics_translation", true)
        showLyricRomanization = MeloXSettingsPreferences.boolean(app, "lyrics_romanization", true)
        downloadLyricsEnabled = MeloXSettingsPreferences.boolean(app, "download_lyrics", true)
        musicArea = MeloXSettingsPreferences.string(app, "music_area", "全部")
        beatNetDebugEnabled = MeloXSettingsPreferences.boolean(app, "developer_beatnet", false)
    }
}

object MeloXSettingsPreferences {
    private const val NAME = "melox_app_settings"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun initialize(context: Context) = MeloXSettingsRuntime.initialize(context)

    fun boolean(context: Context, key: String, default: Boolean = false): Boolean =
        prefs(context).getBoolean(key, default)

    fun string(context: Context, key: String, default: String = ""): String =
        prefs(context).getString(key, default) ?: default

    fun setBoolean(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
        when (key) {
            "feature_podcasts" -> MeloXSettingsRuntime.podcastsEnabled = value
            "feature_history" -> MeloXSettingsRuntime.listeningHistoryEnabled = value
            "player_flowing_backdrop" -> MeloXSettingsRuntime.flowingBackdropEnabled = value
            "player_artwork_motion" -> MeloXSettingsRuntime.artworkMotionEnabled = value
            "player_keep_screen_on" -> MeloXSettingsRuntime.keepScreenOn = value
            "lyrics_translation" -> MeloXSettingsRuntime.showLyricTranslation = value
            "lyrics_romanization" -> MeloXSettingsRuntime.showLyricRomanization = value
            "download_lyrics" -> MeloXSettingsRuntime.downloadLyricsEnabled = value
            "developer_beatnet" -> MeloXSettingsRuntime.beatNetDebugEnabled = value
        }
    }

    fun setString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
        when (key) {
            "theme_mode" -> MeloXSettingsRuntime.themeMode = runCatching {
                MeloXThemeMode.valueOf(value)
            }.getOrDefault(MeloXThemeMode.System)
            "music_area" -> MeloXSettingsRuntime.musicArea = value
        }
    }

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
        MeloXSettingsRuntime.initialize(context, force = true)
    }
}
