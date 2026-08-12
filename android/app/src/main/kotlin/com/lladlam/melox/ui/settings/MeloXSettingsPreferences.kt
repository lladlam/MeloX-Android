package com.lladlam.melox.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class MeloXThemeMode { System, Light, Dark }
enum class MeloXLyricAnnotationDisplayMode { FocusedLine, AllLines }
enum class MeloXLyricsStyle { AppleMusic, Eva, TextPV }
enum class MeloXTextPVStyle { Dynamic, Minimal, Cyber }
enum class MeloXVolumeControlMode { System, Player }
enum class MeloXSecondaryLyricMode { Auto, Translation, Romanization, NextLine, Hidden }
enum class MeloXSystemLyricTitleMode { LyricFirst, SongFirst }

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
    var lyricWordByWordEnabled by mutableStateOf(true)
        internal set
    var lyricPseudoTimingEnabled by mutableStateOf(true)
        internal set
    var lyricTapSeekEnabled by mutableStateOf(true)
        internal set
    var lyricLongPressShareEnabled by mutableStateOf(true)
        internal set
    var lyricInterludeCountdownEnabled by mutableStateOf(true)
        internal set
    var lyricAutoFollowEnabled by mutableStateOf(true)
        internal set
    var lyricReduceMotion by mutableStateOf(false)
        internal set
    var lyricAdvanceMs by mutableStateOf(0)
        internal set
    var lyricAdvanceAppliesToWordByWord by mutableStateOf(false)
        internal set
    var lyricRefreshRate by mutableStateOf(60)
        internal set
    var lyricRomanizationDisplayMode by mutableStateOf(MeloXLyricAnnotationDisplayMode.FocusedLine)
        internal set
    var lyricTranslationDisplayMode by mutableStateOf(MeloXLyricAnnotationDisplayMode.FocusedLine)
        internal set
    var lyricFollowDelayMs by mutableStateOf(3_000)
        internal set
    var lyricFontScale by mutableStateOf(1f)
        internal set
    var lyricSpacingScale by mutableStateOf(1f)
        internal set
    var lyricBlurStrength by mutableStateOf(1f)
        internal set
    var lyricFocusScale by mutableStateOf(1.02f)
        internal set
    var lyricInactiveOpacity by mutableStateOf(.3f)
        internal set
    var lyricGlowStrength by mutableStateOf(1f)
        internal set
    var lyricLongToneStrength by mutableStateOf(1f)
        internal set
    var lyricsStyle by mutableStateOf(MeloXLyricsStyle.AppleMusic)
        internal set
    var textPVStyle by mutableStateOf(MeloXTextPVStyle.Dynamic)
        internal set
    var skylineEnabled by mutableStateOf(true)
        internal set
    var skylineShowSongInfo by mutableStateOf(true)
        internal set
    var skylineAmbientLines by mutableStateOf(2)
        internal set
    var skylineMainFontScale by mutableStateOf(1f)
        internal set
    var systemLyricsEnabled by mutableStateOf(false)
        internal set
    var lyricNotificationsEnabled by mutableStateOf(false)
        internal set
    var systemLyricTitleMode by mutableStateOf(MeloXSystemLyricTitleMode.LyricFirst)
        internal set
    var lyricNotificationShowNextLine by mutableStateOf(false)
        internal set
    var lyricNotificationShowProgress by mutableStateOf(true)
        internal set
    var floatingLyricsEnabled by mutableStateOf(false)
        internal set
    var floatingSecondaryMode by mutableStateOf(MeloXSecondaryLyricMode.Auto)
        internal set
    var floatingFontSizeSp by mutableStateOf(18)
        internal set
    var floatingHighContrast by mutableStateOf(true)
        internal set
    var homeTabEnabled by mutableStateOf(true)
        internal set
    var exploreTabEnabled by mutableStateOf(true)
        internal set
    var libraryTabEnabled by mutableStateOf(true)
        internal set
    var rememberLastTab by mutableStateOf(true)
        internal set
    var tabOrder by mutableStateOf(listOf("Home", "Explore", "Library", "Settings"))
        internal set
    var defaultTab by mutableStateOf("Home")
        internal set
    var rememberLibraryPage by mutableStateOf(true)
        internal set
    var defaultLibraryPage by mutableStateOf("Songs")
        internal set
    var downloadLyricsEnabled by mutableStateOf(true)
        internal set
    var musicArea by mutableStateOf("全部")
        internal set
    var showPlaylistPlayCount by mutableStateOf(true)
        internal set
    var showHighQualityPlaylists by mutableStateOf(true)
        internal set
    var clipboardLinksEnabled by mutableStateOf(true)
        internal set
    var previousRestartsAfterFiveSeconds by mutableStateOf(true)
        internal set
    var volumeControlMode by mutableStateOf(MeloXVolumeControlMode.System)
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
        lyricWordByWordEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_word_by_word", true)
        lyricPseudoTimingEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_pseudo_timing", true)
        lyricTapSeekEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_tap_seek", true)
        lyricLongPressShareEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_long_press_share", true)
        lyricInterludeCountdownEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_interlude_countdown", true)
        lyricAutoFollowEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_auto_follow", true)
        lyricReduceMotion = MeloXSettingsPreferences.boolean(app, "lyrics_reduce_motion", false)
        lyricAdvanceMs = MeloXSettingsPreferences.int(app, "lyrics_advance_ms", 0).coerceIn(-5_000, 5_000)
        lyricAdvanceAppliesToWordByWord = MeloXSettingsPreferences.boolean(app, "lyrics_advance_word_by_word", false)
        lyricRefreshRate = MeloXSettingsPreferences.int(app, "lyrics_refresh_rate", 60)
            .takeIf { it in setOf(30, 60, 90, 120) } ?: 60
        lyricRomanizationDisplayMode = annotationMode(app, "lyrics_romanization_display_mode")
        lyricTranslationDisplayMode = annotationMode(app, "lyrics_translation_display_mode")
        lyricFollowDelayMs = MeloXSettingsPreferences.int(app, "lyrics_follow_delay_ms", 3_000).coerceIn(1_000, 8_000)
        lyricFontScale = MeloXSettingsPreferences.float(app, "lyrics_font_scale", 1f).coerceIn(.8f, 1.25f)
        lyricSpacingScale = MeloXSettingsPreferences.float(app, "lyrics_spacing_scale", 1f).coerceIn(.7f, 1.5f)
        lyricBlurStrength = MeloXSettingsPreferences.float(app, "lyrics_blur_strength", 1f).coerceIn(0f, 1.5f)
        lyricFocusScale = MeloXSettingsPreferences.float(app, "lyrics_focus_scale", 1.02f).coerceIn(1f, 1.08f)
        lyricInactiveOpacity = MeloXSettingsPreferences.float(app, "lyrics_inactive_opacity", .3f).coerceIn(.15f, .65f)
        lyricGlowStrength = MeloXSettingsPreferences.float(app, "lyrics_glow_strength", 1f).coerceIn(0f, 1.5f)
        lyricLongToneStrength = MeloXSettingsPreferences.float(app, "lyrics_long_tone_strength", 1f).coerceIn(0f, 1.5f)
        lyricsStyle = runCatching {
            MeloXLyricsStyle.valueOf(MeloXSettingsPreferences.string(app, "lyrics_style", MeloXLyricsStyle.AppleMusic.name))
        }.getOrDefault(MeloXLyricsStyle.AppleMusic)
        textPVStyle = runCatching {
            MeloXTextPVStyle.valueOf(MeloXSettingsPreferences.string(app, "lyrics_text_pv_style", MeloXTextPVStyle.Dynamic.name))
        }.getOrDefault(MeloXTextPVStyle.Dynamic)
        skylineEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_skyline_enabled", true)
        skylineShowSongInfo = MeloXSettingsPreferences.boolean(app, "lyrics_skyline_song_info", true)
        skylineAmbientLines = MeloXSettingsPreferences.int(app, "lyrics_skyline_ambient_lines", 2).coerceIn(0, 4)
        skylineMainFontScale = MeloXSettingsPreferences.float(app, "lyrics_skyline_font_scale", 1f).coerceIn(.8f, 1.3f)
        systemLyricsEnabled = MeloXSettingsPreferences.boolean(app, "system_lyrics_enabled", false)
        lyricNotificationsEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_notifications_enabled", false)
        systemLyricTitleMode = runCatching {
            MeloXSystemLyricTitleMode.valueOf(
                MeloXSettingsPreferences.string(app, "system_lyrics_title_mode", MeloXSystemLyricTitleMode.LyricFirst.name),
            )
        }.getOrDefault(MeloXSystemLyricTitleMode.LyricFirst)
        lyricNotificationShowNextLine = MeloXSettingsPreferences.boolean(app, "lyrics_notification_next_line", false)
        lyricNotificationShowProgress = MeloXSettingsPreferences.boolean(app, "lyrics_notification_progress", true)
        floatingLyricsEnabled = MeloXSettingsPreferences.boolean(app, "floating_lyrics_enabled", false)
        floatingSecondaryMode = runCatching {
            MeloXSecondaryLyricMode.valueOf(
                MeloXSettingsPreferences.string(app, "floating_lyrics_secondary_mode", MeloXSecondaryLyricMode.Auto.name),
            )
        }.getOrDefault(MeloXSecondaryLyricMode.Auto)
        floatingFontSizeSp = MeloXSettingsPreferences.int(app, "floating_lyrics_font_size", 18).coerceIn(14, 28)
        floatingHighContrast = MeloXSettingsPreferences.boolean(app, "floating_lyrics_high_contrast", true)
        homeTabEnabled = MeloXSettingsPreferences.boolean(app, "tab_home", true)
        exploreTabEnabled = MeloXSettingsPreferences.boolean(app, "tab_explore", true)
        libraryTabEnabled = MeloXSettingsPreferences.boolean(app, "tab_library", true)
        rememberLastTab = MeloXSettingsPreferences.boolean(app, "general_remember_tab", true)
        tabOrder = MeloXSettingsPreferences.string(app, "tab_order", "Home,Explore,Library,Settings")
            .split(',').filter { it in setOf("Home", "Explore", "Library", "Settings") }.distinct()
            .let { order -> (order + listOf("Home", "Explore", "Library", "Settings")).distinct() }
        defaultTab = MeloXSettingsPreferences.string(app, "general_default_tab", "Home")
        rememberLibraryPage = MeloXSettingsPreferences.boolean(app, "library_remember_page", true)
        defaultLibraryPage = MeloXSettingsPreferences.string(app, "library_default_page", "Songs")
        downloadLyricsEnabled = MeloXSettingsPreferences.boolean(app, "download_lyrics", true)
        musicArea = MeloXSettingsPreferences.string(app, "music_area", "全部")
        showPlaylistPlayCount = MeloXSettingsPreferences.boolean(app, "content_playlist_play_count", true)
        showHighQualityPlaylists = MeloXSettingsPreferences.boolean(app, "content_high_quality_playlist", true)
        clipboardLinksEnabled = MeloXSettingsPreferences.boolean(app, "general_clipboard_links", true)
        previousRestartsAfterFiveSeconds = MeloXSettingsPreferences.boolean(app, "playback_previous_restarts", true)
        volumeControlMode = runCatching {
            MeloXVolumeControlMode.valueOf(MeloXSettingsPreferences.string(app, "playback_volume_mode", MeloXVolumeControlMode.System.name))
        }.getOrDefault(MeloXVolumeControlMode.System)
    }

    private fun annotationMode(context: Context, key: String): MeloXLyricAnnotationDisplayMode = runCatching {
        MeloXLyricAnnotationDisplayMode.valueOf(
            MeloXSettingsPreferences.string(context, key, MeloXLyricAnnotationDisplayMode.FocusedLine.name),
        )
    }.getOrDefault(MeloXLyricAnnotationDisplayMode.FocusedLine)
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

    fun int(context: Context, key: String, default: Int = 0): Int =
        prefs(context).getInt(key, default)

    fun float(context: Context, key: String, default: Float = 0f): Float =
        prefs(context).getFloat(key, default)

    fun long(context: Context, key: String, default: Long = 0L): Long =
        prefs(context).getLong(key, default)

    fun setLong(context: Context, key: String, value: Long) {
        prefs(context).edit().putLong(key, value).apply()
    }

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
            "lyrics_word_by_word" -> MeloXSettingsRuntime.lyricWordByWordEnabled = value
            "lyrics_pseudo_timing" -> MeloXSettingsRuntime.lyricPseudoTimingEnabled = value
            "lyrics_tap_seek" -> MeloXSettingsRuntime.lyricTapSeekEnabled = value
            "lyrics_long_press_share" -> MeloXSettingsRuntime.lyricLongPressShareEnabled = value
            "lyrics_interlude_countdown" -> MeloXSettingsRuntime.lyricInterludeCountdownEnabled = value
            "lyrics_auto_follow" -> MeloXSettingsRuntime.lyricAutoFollowEnabled = value
            "lyrics_reduce_motion" -> MeloXSettingsRuntime.lyricReduceMotion = value
            "lyrics_advance_word_by_word" -> MeloXSettingsRuntime.lyricAdvanceAppliesToWordByWord = value
            "lyrics_skyline_enabled" -> MeloXSettingsRuntime.skylineEnabled = value
            "lyrics_skyline_song_info" -> MeloXSettingsRuntime.skylineShowSongInfo = value
            "system_lyrics_enabled" -> MeloXSettingsRuntime.systemLyricsEnabled = value
            "lyrics_notifications_enabled" -> MeloXSettingsRuntime.lyricNotificationsEnabled = value
            "lyrics_notification_next_line" -> MeloXSettingsRuntime.lyricNotificationShowNextLine = value
            "lyrics_notification_progress" -> MeloXSettingsRuntime.lyricNotificationShowProgress = value
            "floating_lyrics_enabled" -> MeloXSettingsRuntime.floatingLyricsEnabled = value
            "floating_lyrics_high_contrast" -> MeloXSettingsRuntime.floatingHighContrast = value
            "tab_home" -> MeloXSettingsRuntime.homeTabEnabled = value
            "tab_explore" -> MeloXSettingsRuntime.exploreTabEnabled = value
            "tab_library" -> MeloXSettingsRuntime.libraryTabEnabled = value
            "general_remember_tab" -> MeloXSettingsRuntime.rememberLastTab = value
            "library_remember_page" -> MeloXSettingsRuntime.rememberLibraryPage = value
            "download_lyrics" -> MeloXSettingsRuntime.downloadLyricsEnabled = value
            "content_playlist_play_count" -> MeloXSettingsRuntime.showPlaylistPlayCount = value
            "content_high_quality_playlist" -> MeloXSettingsRuntime.showHighQualityPlaylists = value
            "general_clipboard_links" -> MeloXSettingsRuntime.clipboardLinksEnabled = value
            "playback_previous_restarts" -> MeloXSettingsRuntime.previousRestartsAfterFiveSeconds = value
        }
    }

    fun setInt(context: Context, key: String, value: Int) {
        prefs(context).edit().putInt(key, value).apply()
        when (key) {
            "lyrics_advance_ms" -> MeloXSettingsRuntime.lyricAdvanceMs = value.coerceIn(-5_000, 5_000)
            "lyrics_follow_delay_ms" -> MeloXSettingsRuntime.lyricFollowDelayMs = value.coerceIn(1_000, 8_000)
            "lyrics_refresh_rate" -> MeloXSettingsRuntime.lyricRefreshRate =
                value.takeIf { it in setOf(30, 60, 90, 120) } ?: 60
            "lyrics_skyline_ambient_lines" -> MeloXSettingsRuntime.skylineAmbientLines = value.coerceIn(0, 4)
            "floating_lyrics_font_size" -> MeloXSettingsRuntime.floatingFontSizeSp = value.coerceIn(14, 28)
        }
    }

    fun setFloat(context: Context, key: String, value: Float) {
        prefs(context).edit().putFloat(key, value).apply()
        when (key) {
            "lyrics_font_scale" -> MeloXSettingsRuntime.lyricFontScale = value.coerceIn(.8f, 1.25f)
            "lyrics_spacing_scale" -> MeloXSettingsRuntime.lyricSpacingScale = value.coerceIn(.7f, 1.5f)
            "lyrics_blur_strength" -> MeloXSettingsRuntime.lyricBlurStrength = value.coerceIn(0f, 1.5f)
            "lyrics_focus_scale" -> MeloXSettingsRuntime.lyricFocusScale = value.coerceIn(1f, 1.08f)
            "lyrics_inactive_opacity" -> MeloXSettingsRuntime.lyricInactiveOpacity = value.coerceIn(.15f, .65f)
            "lyrics_glow_strength" -> MeloXSettingsRuntime.lyricGlowStrength = value.coerceIn(0f, 1.5f)
            "lyrics_long_tone_strength" -> MeloXSettingsRuntime.lyricLongToneStrength = value.coerceIn(0f, 1.5f)
            "lyrics_skyline_font_scale" -> MeloXSettingsRuntime.skylineMainFontScale = value.coerceIn(.8f, 1.3f)
        }
    }

    fun setString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
        when (key) {
            "theme_mode" -> MeloXSettingsRuntime.themeMode = runCatching {
                MeloXThemeMode.valueOf(value)
            }.getOrDefault(MeloXThemeMode.System)
            "music_area" -> MeloXSettingsRuntime.musicArea = value
            "tab_order" -> MeloXSettingsRuntime.tabOrder = value.split(',')
                .filter { it in setOf("Home", "Explore", "Library", "Settings") }.distinct()
                .let { order -> (order + listOf("Home", "Explore", "Library", "Settings")).distinct() }
            "general_default_tab" -> MeloXSettingsRuntime.defaultTab = value
            "library_default_page" -> MeloXSettingsRuntime.defaultLibraryPage = value
            "lyrics_romanization_display_mode" -> MeloXSettingsRuntime.lyricRomanizationDisplayMode = runCatching {
                MeloXLyricAnnotationDisplayMode.valueOf(value)
            }.getOrDefault(MeloXLyricAnnotationDisplayMode.FocusedLine)
            "lyrics_translation_display_mode" -> MeloXSettingsRuntime.lyricTranslationDisplayMode = runCatching {
                MeloXLyricAnnotationDisplayMode.valueOf(value)
            }.getOrDefault(MeloXLyricAnnotationDisplayMode.FocusedLine)
            "lyrics_style" -> MeloXSettingsRuntime.lyricsStyle = runCatching {
                MeloXLyricsStyle.valueOf(value)
            }.getOrDefault(MeloXLyricsStyle.AppleMusic)
            "lyrics_text_pv_style" -> MeloXSettingsRuntime.textPVStyle = runCatching {
                MeloXTextPVStyle.valueOf(value)
            }.getOrDefault(MeloXTextPVStyle.Dynamic)
            "system_lyrics_title_mode" -> MeloXSettingsRuntime.systemLyricTitleMode = runCatching {
                MeloXSystemLyricTitleMode.valueOf(value)
            }.getOrDefault(MeloXSystemLyricTitleMode.LyricFirst)
            "floating_lyrics_secondary_mode" -> MeloXSettingsRuntime.floatingSecondaryMode = runCatching {
                MeloXSecondaryLyricMode.valueOf(value)
            }.getOrDefault(MeloXSecondaryLyricMode.Auto)
            "playback_volume_mode" -> MeloXSettingsRuntime.volumeControlMode = runCatching {
                MeloXVolumeControlMode.valueOf(value)
            }.getOrDefault(MeloXVolumeControlMode.System)
        }
    }

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
        MeloXSettingsRuntime.initialize(context, force = true)
    }
}
