package com.lladlam.melox.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight

enum class MeloXThemeMode { System, Light, Dark }
enum class MeloXLyricAnnotationDisplayMode { FocusedLine, AllLines }
enum class MeloXLyricsStyle { AppleMusic, Eva, TextPV }
enum class MeloXTextPVStyle {
    BlueBold, KineticSplit, BluePlane, CyberGrunge, Geometric, RainCity,
    CyberpunkHUD, EmotionCinema, HystericNight, SpiderWeb, StaggeredText,
    CalmVillain, GirlyClouds, SweetPink, FlyMeToTheMoon, KawaiiPixel,
    CrimeScene, Haruhikage,
    /** Compatibility values kept for installs that used the early Android preview. */
    Dynamic, Minimal, Cyber,
}

val MeloXTextPVStyle.referenceAnimationSpeed: Float
    get() = when (this) {
        MeloXTextPVStyle.StaggeredText -> 3.4f
        MeloXTextPVStyle.GirlyClouds -> 1.5f
        MeloXTextPVStyle.SweetPink, MeloXTextPVStyle.KawaiiPixel -> 1f
        MeloXTextPVStyle.FlyMeToTheMoon -> 3.7f
        MeloXTextPVStyle.CrimeScene -> 2.5f
        MeloXTextPVStyle.Haruhikage -> .8f
        else -> 2f
    }
enum class MeloXVolumeControlMode { System, Player }
enum class MeloXSecondaryLyricMode { Auto, Translation, Romanization, NextLine, Hidden }
enum class MeloXSystemLyricTitleMode { LyricFirst, SongFirst }
enum class MeloXScreenAwakeMode { Disabled, Player, Lyrics, HiddenLyricsInterface }
enum class MeloXLyricsGroupingMode { Word, Character }
enum class MeloXLyricsFontWeight(val composeWeight: FontWeight) {
    Light(FontWeight.Light), Regular(FontWeight.Normal), Medium(FontWeight.Medium),
    SemiBold(FontWeight.SemiBold), Bold(FontWeight.Bold), Heavy(FontWeight.Black),
}

/** Process-visible settings used by UI paths that need immediate recomposition. */
object MeloXSettingsRuntime {
    var themeMode by mutableStateOf(MeloXThemeMode.System)
        internal set
    var podcastsEnabled by mutableStateOf(true)
        internal set
    var listeningHistoryEnabled by mutableStateOf(true)
        internal set
    var downloadsEnabled by mutableStateOf(true)
        internal set
    var cloudMusicEnabled by mutableStateOf(true)
        internal set
    var flowingBackdropEnabled by mutableStateOf(true)
        internal set
    var artworkMotionEnabled by mutableStateOf(true)
        internal set
    var keepScreenOn by mutableStateOf(false)
        internal set
    var screenAwakeMode by mutableStateOf(MeloXScreenAwakeMode.Disabled)
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
    var lyricFocusPosition by mutableStateOf(.25f)
        internal set
    var lyricFontWeight by mutableStateOf(MeloXLyricsFontWeight.Heavy)
        internal set
    var lyricLiftMode by mutableStateOf(MeloXLyricsGroupingMode.Character)
        internal set
    var lyricLongToneDetectionMode by mutableStateOf(MeloXLyricsGroupingMode.Character)
        internal set
    var lyricGlowLongTonesOnly by mutableStateOf(true)
        internal set
    var lyricLongToneThresholdMs by mutableStateOf(950)
        internal set
    var lyricSpacingScale by mutableStateOf(1f)
        internal set
    var lyricBlurStrength by mutableStateOf(1f)
        internal set
    var lyricDistanceBlurScale by mutableStateOf(1.05f)
        internal set
    var lyricHiddenInterfaceBlurScale by mutableStateOf(.85f)
        internal set
    var lyricDimAmount by mutableStateOf(1f)
        internal set
    var lyricFocusScale by mutableStateOf(1.02f)
        internal set
    var lyricInactiveOpacity by mutableStateOf(.3f)
        internal set
    var lyricGlowStrength by mutableStateOf(1f)
        internal set
    var lyricGlowEnabled by mutableStateOf(true)
        internal set
    var lyricLongToneStrength by mutableStateOf(1f)
        internal set
    var lyricHighlightGradientWidth by mutableStateOf(.7f)
        internal set
    var lyricHighlightGradientReduction by mutableStateOf(.65f)
        internal set
    var lyricRomanizationFontScale by mutableStateOf(.65f)
        internal set
    var lyricRomanizationOpacity by mutableStateOf(.9f)
        internal set
    var lyricTranslationFontScale by mutableStateOf(.65f)
        internal set
    var lyricTranslationOpacity by mutableStateOf(.9f)
        internal set
    var lyricInterfaceAutoHideDelayMs by mutableStateOf(5_000)
        internal set
    var lyricScrollHideThresholdDp by mutableStateOf(200)
        internal set
    var lyricCascadeDelayMs by mutableStateOf(21f)
        internal set
    var lyricCascadeDelayIncreaseMs by mutableStateOf(5f)
        internal set
    var lyricCascadeFollowingDelayMs by mutableStateOf(30f)
        internal set
    var lyricCascadeCatchUpRatio by mutableStateOf(.97f)
        internal set
    var lyricCascadeChaseSpeedGradient by mutableStateOf(.70f)
        internal set
    var lyricCascadeDurationMs by mutableStateOf(740f)
        internal set
    var lyricSnapThresholdMs by mutableStateOf(260f)
        internal set
    var lyricCascadeBounceEnabled by mutableStateOf(true)
        internal set
    var lyricCascadeBounce by mutableStateOf(.26f)
        internal set
    var lyricCascadeBounceGradient by mutableStateOf(.85f)
        internal set
    var lyricScaleBounceEnabled by mutableStateOf(true)
        internal set
    var lyricScaleBounce by mutableStateOf(.32f)
        internal set
    var lyricScaleBounceDurationMs by mutableStateOf(580)
        internal set
    var lyricFocusColorLeadMs by mutableStateOf(0)
        internal set
    var lyricsStyle by mutableStateOf(MeloXLyricsStyle.AppleMusic)
        internal set
    var textPVStyle by mutableStateOf(MeloXTextPVStyle.BlueBold)
        internal set
    var textPVMotionIntensity by mutableStateOf(1f)
        internal set
    var textPVAnimationSpeed by mutableStateOf(2f)
        internal set
    var skylineEnabled by mutableStateOf(true)
        internal set
    var skylineShowSongInfo by mutableStateOf(true)
        internal set
    var skylineKeepsScreenAwake by mutableStateOf(true)
        internal set
    var skylineAmbientLines by mutableStateOf(2)
        internal set
    var skylineCurrentFontSize by mutableStateOf(54f)
        internal set
    var skylineCurrentMaximumScale by mutableStateOf(1.1f)
        internal set
    var skylineNextFontSize by mutableStateOf(24f)
        internal set
    var skylineCurrentSpacing by mutableStateOf(14f)
        internal set
    var skylineCurrentWidth by mutableStateOf(.64f)
        internal set
    var skylineNextOpacity by mutableStateOf(.48f)
        internal set
    var skylineAmbientFontSize by mutableStateOf(44f)
        internal set
    var skylineAmbientMaximumCharacters by mutableStateOf(4)
        internal set
    var skylineAmbientMaximumVisibleTexts by mutableStateOf(16)
        internal set
    var skylineAmbientOpacity by mutableStateOf(1f)
        internal set
    var skylineAmbientBlur by mutableStateOf(1f)
        internal set
    var skylineAmbientMaximumTilt by mutableStateOf(8f)
        internal set
    var skylineAmbientDrift by mutableStateOf(1f)
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
    var homeQuickActionsEnabled by mutableStateOf(true)
        internal set
    var homePlaylistsEnabled by mutableStateOf(true)
        internal set
    var homeNewSongsEnabled by mutableStateOf(true)
        internal set
    var homeSectionOrder by mutableStateOf(listOf("QuickActions", "Playlists", "NewSongs"))
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
    var startsHeartModeOnLaunch by mutableStateOf(false)
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
        downloadsEnabled = MeloXSettingsPreferences.boolean(app, "feature_downloads", true)
        cloudMusicEnabled = MeloXSettingsPreferences.boolean(app, "feature_cloud_music", true)
        flowingBackdropEnabled = MeloXSettingsPreferences.boolean(app, "player_flowing_backdrop", true)
        artworkMotionEnabled = MeloXSettingsPreferences.boolean(app, "player_artwork_motion", true)
        keepScreenOn = MeloXSettingsPreferences.boolean(app, "player_keep_screen_on", false)
        screenAwakeMode = runCatching {
            MeloXScreenAwakeMode.valueOf(
                MeloXSettingsPreferences.string(
                    app,
                    "player_screen_awake_mode",
                    if (keepScreenOn) MeloXScreenAwakeMode.Player.name else MeloXScreenAwakeMode.Disabled.name,
                ),
            )
        }.getOrDefault(MeloXScreenAwakeMode.Disabled)
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
        lyricFocusPosition = MeloXSettingsPreferences.float(app, "lyrics_focus_position", .25f).coerceIn(.05f, .8f)
        lyricFontWeight = runCatching {
            MeloXLyricsFontWeight.valueOf(MeloXSettingsPreferences.string(app, "lyrics_font_weight", MeloXLyricsFontWeight.Heavy.name))
        }.getOrDefault(MeloXLyricsFontWeight.Heavy)
        lyricLiftMode = runCatching {
            MeloXLyricsGroupingMode.valueOf(MeloXSettingsPreferences.string(app, "lyrics_lift_mode", MeloXLyricsGroupingMode.Character.name))
        }.getOrDefault(MeloXLyricsGroupingMode.Character)
        lyricLongToneDetectionMode = runCatching {
            MeloXLyricsGroupingMode.valueOf(MeloXSettingsPreferences.string(app, "lyrics_long_tone_detection", MeloXLyricsGroupingMode.Character.name))
        }.getOrDefault(MeloXLyricsGroupingMode.Character)
        lyricGlowLongTonesOnly = MeloXSettingsPreferences.boolean(app, "lyrics_glow_long_tones_only", true)
        lyricLongToneThresholdMs = MeloXSettingsPreferences.int(app, "lyrics_long_tone_threshold_ms", 950).coerceIn(300, 1_500)
        lyricSpacingScale = MeloXSettingsPreferences.float(app, "lyrics_spacing_scale", 1f).coerceIn(.7f, 1.5f)
        lyricBlurStrength = MeloXSettingsPreferences.float(app, "lyrics_blur_strength", 1f).coerceIn(0f, 1.5f)
        lyricDistanceBlurScale = MeloXSettingsPreferences.float(app, "lyrics_distance_blur_scale", 1.05f).coerceIn(0f, 1.5f)
        lyricHiddenInterfaceBlurScale = MeloXSettingsPreferences.float(app, "lyrics_hidden_blur_scale", .85f).coerceIn(0f, 1.5f)
        lyricDimAmount = MeloXSettingsPreferences.float(app, "lyrics_dim_amount", 1f).coerceIn(0f, 1f)
        lyricFocusScale = MeloXSettingsPreferences.float(app, "lyrics_focus_scale", 1.02f).coerceIn(1f, 1.08f)
        lyricInactiveOpacity = MeloXSettingsPreferences.float(app, "lyrics_inactive_opacity", .3f).coerceIn(.15f, .65f)
        lyricGlowStrength = MeloXSettingsPreferences.float(app, "lyrics_glow_strength", 1f).coerceIn(0f, 1.5f)
        lyricGlowEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_glow_enabled", true)
        lyricLongToneStrength = MeloXSettingsPreferences.float(app, "lyrics_long_tone_strength", 1f).coerceIn(0f, 1.5f)
        lyricHighlightGradientWidth = MeloXSettingsPreferences.float(app, "lyrics_highlight_gradient_width", .7f).coerceIn(.4f, 3f)
        lyricHighlightGradientReduction = MeloXSettingsPreferences.float(app, "lyrics_highlight_gradient_reduction", .65f).coerceIn(0f, 1f)
        lyricRomanizationFontScale = MeloXSettingsPreferences.float(app, "lyrics_romanization_font_scale", .65f).coerceIn(.5f, .8f)
        lyricRomanizationOpacity = MeloXSettingsPreferences.float(app, "lyrics_romanization_opacity", .9f).coerceIn(.4f, .9f)
        lyricTranslationFontScale = MeloXSettingsPreferences.float(app, "lyrics_translation_font_scale", .65f).coerceIn(.5f, .8f)
        lyricTranslationOpacity = MeloXSettingsPreferences.float(app, "lyrics_translation_opacity", .9f).coerceIn(.4f, .9f)
        lyricInterfaceAutoHideDelayMs = MeloXSettingsPreferences.int(app, "lyrics_interface_auto_hide_ms", 5_000).coerceIn(3_000, 15_000)
        lyricScrollHideThresholdDp = MeloXSettingsPreferences.int(app, "lyrics_scroll_hide_threshold_dp", 200).coerceIn(40, 240)
        lyricCascadeDelayMs = MeloXSettingsPreferences.float(app, "lyrics_cascade_delay_ms", 21f).coerceIn(0f, 100f)
        lyricCascadeDelayIncreaseMs = MeloXSettingsPreferences.float(app, "lyrics_cascade_delay_increase_ms", 5f).coerceIn(0f, 100f)
        lyricCascadeFollowingDelayMs = MeloXSettingsPreferences.float(app, "lyrics_cascade_following_delay_ms", 30f).coerceIn(0f, 200f)
        lyricCascadeCatchUpRatio = MeloXSettingsPreferences.float(app, "lyrics_cascade_catch_up_ratio", .97f).coerceIn(.5f, 1f)
        lyricCascadeChaseSpeedGradient = MeloXSettingsPreferences.float(app, "lyrics_cascade_chase_gradient", .70f).coerceIn(0f, 1f)
        lyricCascadeDurationMs = MeloXSettingsPreferences.float(app, "lyrics_cascade_duration_ms", 740f).coerceIn(200f, 1_200f)
        lyricSnapThresholdMs = MeloXSettingsPreferences.float(app, "lyrics_snap_threshold_ms", 260f).coerceIn(50f, 500f)
        lyricCascadeBounceEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_cascade_bounce_enabled", true)
        lyricCascadeBounce = MeloXSettingsPreferences.float(app, "lyrics_cascade_bounce", .26f).coerceIn(0f, .8f)
        lyricCascadeBounceGradient = MeloXSettingsPreferences.float(app, "lyrics_cascade_bounce_gradient", .85f).coerceIn(0f, 1f)
        lyricScaleBounceEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_scale_bounce_enabled", true)
        lyricScaleBounce = MeloXSettingsPreferences.float(app, "lyrics_scale_bounce", .32f).coerceIn(0f, .5f)
        lyricScaleBounceDurationMs = MeloXSettingsPreferences.int(app, "lyrics_scale_bounce_duration_ms", 580).coerceIn(150, 800)
        lyricFocusColorLeadMs = MeloXSettingsPreferences.int(app, "lyrics_focus_color_lead_ms", 0).coerceIn(-300, 300)
        lyricsStyle = runCatching {
            MeloXLyricsStyle.valueOf(MeloXSettingsPreferences.string(app, "lyrics_style", MeloXLyricsStyle.AppleMusic.name))
        }.getOrDefault(MeloXLyricsStyle.AppleMusic)
        textPVStyle = runCatching {
            MeloXTextPVStyle.valueOf(MeloXSettingsPreferences.string(app, "lyrics_text_pv_style", MeloXTextPVStyle.BlueBold.name))
        }.getOrDefault(MeloXTextPVStyle.BlueBold)
        textPVMotionIntensity = MeloXSettingsPreferences.float(app, "lyrics_text_pv_motion_intensity", 1f)
            .coerceIn(0f, 2f)
        textPVAnimationSpeed = MeloXSettingsPreferences.float(
            app,
            "lyrics_text_pv_animation_speed",
            textPVStyle.referenceAnimationSpeed,
        ).coerceIn(0f, 4f)
        skylineEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_skyline_enabled", true)
        skylineShowSongInfo = MeloXSettingsPreferences.boolean(app, "lyrics_skyline_song_info", true)
        skylineKeepsScreenAwake = MeloXSettingsPreferences.boolean(app, "lyrics_skyline_keep_awake", true)
        skylineAmbientLines = MeloXSettingsPreferences.int(app, "lyrics_skyline_ambient_lines", 2).coerceIn(0, 4)
        skylineCurrentFontSize = MeloXSettingsPreferences.float(app, "lyrics_skyline_current_font_size", 54f).coerceIn(36f, 84f)
        skylineCurrentMaximumScale = MeloXSettingsPreferences.float(app, "lyrics_skyline_current_max_scale", 1.1f).coerceIn(1f, 1.2f)
        skylineNextFontSize = MeloXSettingsPreferences.float(app, "lyrics_skyline_next_font_size", 24f).coerceIn(14f, 44f)
        skylineCurrentSpacing = MeloXSettingsPreferences.float(app, "lyrics_skyline_current_spacing", 14f).coerceIn(4f, 36f)
        skylineCurrentWidth = MeloXSettingsPreferences.float(app, "lyrics_skyline_current_width", .64f).coerceIn(.4f, .82f)
        skylineNextOpacity = MeloXSettingsPreferences.float(app, "lyrics_skyline_next_opacity", .48f).coerceIn(.2f, .8f)
        skylineAmbientFontSize = MeloXSettingsPreferences.float(app, "lyrics_skyline_ambient_font_size", 44f).coerceIn(24f, 72f)
        skylineAmbientMaximumCharacters = MeloXSettingsPreferences.int(app, "lyrics_skyline_ambient_max_characters", 4).coerceIn(1, 4)
        skylineAmbientMaximumVisibleTexts = MeloXSettingsPreferences.int(app, "lyrics_skyline_ambient_max_visible", 16).coerceIn(4, 24)
        skylineAmbientOpacity = MeloXSettingsPreferences.float(app, "lyrics_skyline_ambient_opacity", 1f).coerceIn(.4f, 1.8f)
        skylineAmbientBlur = MeloXSettingsPreferences.float(app, "lyrics_skyline_ambient_blur", 1f).coerceIn(0f, 2f)
        skylineAmbientMaximumTilt = MeloXSettingsPreferences.float(app, "lyrics_skyline_ambient_max_tilt", 8f).coerceIn(0f, 20f)
        skylineAmbientDrift = MeloXSettingsPreferences.float(app, "lyrics_skyline_ambient_drift", 1f).coerceIn(0f, 2f)
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
        homeQuickActionsEnabled = MeloXSettingsPreferences.boolean(app, "home_quick_actions", true)
        homePlaylistsEnabled = MeloXSettingsPreferences.boolean(app, "home_playlists", true)
        homeNewSongsEnabled = MeloXSettingsPreferences.boolean(app, "home_new_songs", true)
        homeSectionOrder = MeloXSettingsPreferences.string(app, "home_section_order", "QuickActions,Playlists,NewSongs")
            .split(',').filter { it in setOf("QuickActions", "Playlists", "NewSongs") }.distinct()
            .let { order -> (order + listOf("QuickActions", "Playlists", "NewSongs")).distinct() }
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
        startsHeartModeOnLaunch = MeloXSettingsPreferences.boolean(app, "playback_heart_mode_on_launch", false)
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
            "feature_downloads" -> MeloXSettingsRuntime.downloadsEnabled = value
            "feature_cloud_music" -> MeloXSettingsRuntime.cloudMusicEnabled = value
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
            "lyrics_glow_long_tones_only" -> MeloXSettingsRuntime.lyricGlowLongTonesOnly = value
            "lyrics_glow_enabled" -> MeloXSettingsRuntime.lyricGlowEnabled = value
            "lyrics_cascade_bounce_enabled" -> MeloXSettingsRuntime.lyricCascadeBounceEnabled = value
            "lyrics_scale_bounce_enabled" -> MeloXSettingsRuntime.lyricScaleBounceEnabled = value
            "lyrics_advance_word_by_word" -> MeloXSettingsRuntime.lyricAdvanceAppliesToWordByWord = value
            "lyrics_skyline_enabled" -> MeloXSettingsRuntime.skylineEnabled = value
            "lyrics_skyline_song_info" -> MeloXSettingsRuntime.skylineShowSongInfo = value
            "lyrics_skyline_keep_awake" -> MeloXSettingsRuntime.skylineKeepsScreenAwake = value
            "system_lyrics_enabled" -> MeloXSettingsRuntime.systemLyricsEnabled = value
            "lyrics_notifications_enabled" -> MeloXSettingsRuntime.lyricNotificationsEnabled = value
            "lyrics_notification_next_line" -> MeloXSettingsRuntime.lyricNotificationShowNextLine = value
            "lyrics_notification_progress" -> MeloXSettingsRuntime.lyricNotificationShowProgress = value
            "floating_lyrics_enabled" -> MeloXSettingsRuntime.floatingLyricsEnabled = value
            "floating_lyrics_high_contrast" -> MeloXSettingsRuntime.floatingHighContrast = value
            "tab_home" -> MeloXSettingsRuntime.homeTabEnabled = value
            "home_quick_actions" -> MeloXSettingsRuntime.homeQuickActionsEnabled = value
            "home_playlists" -> MeloXSettingsRuntime.homePlaylistsEnabled = value
            "home_new_songs" -> MeloXSettingsRuntime.homeNewSongsEnabled = value
            "tab_explore" -> MeloXSettingsRuntime.exploreTabEnabled = value
            "tab_library" -> MeloXSettingsRuntime.libraryTabEnabled = value
            "general_remember_tab" -> MeloXSettingsRuntime.rememberLastTab = value
            "library_remember_page" -> MeloXSettingsRuntime.rememberLibraryPage = value
            "download_lyrics" -> MeloXSettingsRuntime.downloadLyricsEnabled = value
            "content_playlist_play_count" -> MeloXSettingsRuntime.showPlaylistPlayCount = value
            "content_high_quality_playlist" -> MeloXSettingsRuntime.showHighQualityPlaylists = value
            "general_clipboard_links" -> MeloXSettingsRuntime.clipboardLinksEnabled = value
            "playback_previous_restarts" -> MeloXSettingsRuntime.previousRestartsAfterFiveSeconds = value
            "playback_heart_mode_on_launch" -> MeloXSettingsRuntime.startsHeartModeOnLaunch = value
        }
    }

    fun setInt(context: Context, key: String, value: Int) {
        prefs(context).edit().putInt(key, value).apply()
        when (key) {
            "lyrics_advance_ms" -> MeloXSettingsRuntime.lyricAdvanceMs = value.coerceIn(-5_000, 5_000)
            "lyrics_follow_delay_ms" -> MeloXSettingsRuntime.lyricFollowDelayMs = value.coerceIn(1_000, 8_000)
            "lyrics_refresh_rate" -> MeloXSettingsRuntime.lyricRefreshRate =
                value.takeIf { it in setOf(30, 60, 90, 120) } ?: 60
            "lyrics_long_tone_threshold_ms" -> MeloXSettingsRuntime.lyricLongToneThresholdMs = value.coerceIn(300, 1_500)
            "lyrics_interface_auto_hide_ms" -> MeloXSettingsRuntime.lyricInterfaceAutoHideDelayMs = value.coerceIn(3_000, 15_000)
            "lyrics_scroll_hide_threshold_dp" -> MeloXSettingsRuntime.lyricScrollHideThresholdDp = value.coerceIn(40, 240)
            "lyrics_scale_bounce_duration_ms" -> MeloXSettingsRuntime.lyricScaleBounceDurationMs = value.coerceIn(150, 800)
            "lyrics_focus_color_lead_ms" -> MeloXSettingsRuntime.lyricFocusColorLeadMs = value.coerceIn(-300, 300)
            "lyrics_skyline_ambient_lines" -> MeloXSettingsRuntime.skylineAmbientLines = value.coerceIn(0, 4)
            "lyrics_skyline_ambient_max_characters" -> MeloXSettingsRuntime.skylineAmbientMaximumCharacters = value.coerceIn(1, 4)
            "lyrics_skyline_ambient_max_visible" -> MeloXSettingsRuntime.skylineAmbientMaximumVisibleTexts = value.coerceIn(4, 24)
            "floating_lyrics_font_size" -> MeloXSettingsRuntime.floatingFontSizeSp = value.coerceIn(14, 28)
        }
    }

    fun setFloat(context: Context, key: String, value: Float) {
        prefs(context).edit().putFloat(key, value).apply()
        when (key) {
            "lyrics_font_scale" -> MeloXSettingsRuntime.lyricFontScale = value.coerceIn(.8f, 1.25f)
            "lyrics_focus_position" -> MeloXSettingsRuntime.lyricFocusPosition = value.coerceIn(.05f, .8f)
            "lyrics_spacing_scale" -> MeloXSettingsRuntime.lyricSpacingScale = value.coerceIn(.7f, 1.5f)
            "lyrics_blur_strength" -> MeloXSettingsRuntime.lyricBlurStrength = value.coerceIn(0f, 1.5f)
            "lyrics_distance_blur_scale" -> MeloXSettingsRuntime.lyricDistanceBlurScale = value.coerceIn(0f, 1.5f)
            "lyrics_hidden_blur_scale" -> MeloXSettingsRuntime.lyricHiddenInterfaceBlurScale = value.coerceIn(0f, 1.5f)
            "lyrics_dim_amount" -> MeloXSettingsRuntime.lyricDimAmount = value.coerceIn(0f, 1f)
            "lyrics_focus_scale" -> MeloXSettingsRuntime.lyricFocusScale = value.coerceIn(1f, 1.08f)
            "lyrics_inactive_opacity" -> MeloXSettingsRuntime.lyricInactiveOpacity = value.coerceIn(.15f, .65f)
            "lyrics_glow_strength" -> MeloXSettingsRuntime.lyricGlowStrength = value.coerceIn(0f, 1.5f)
            "lyrics_long_tone_strength" -> MeloXSettingsRuntime.lyricLongToneStrength = value.coerceIn(0f, 1.5f)
            "lyrics_highlight_gradient_width" -> MeloXSettingsRuntime.lyricHighlightGradientWidth = value.coerceIn(.4f, 3f)
            "lyrics_highlight_gradient_reduction" -> MeloXSettingsRuntime.lyricHighlightGradientReduction = value.coerceIn(0f, 1f)
            "lyrics_romanization_font_scale" -> MeloXSettingsRuntime.lyricRomanizationFontScale = value.coerceIn(.5f, .8f)
            "lyrics_romanization_opacity" -> MeloXSettingsRuntime.lyricRomanizationOpacity = value.coerceIn(.4f, .9f)
            "lyrics_translation_font_scale" -> MeloXSettingsRuntime.lyricTranslationFontScale = value.coerceIn(.5f, .8f)
            "lyrics_translation_opacity" -> MeloXSettingsRuntime.lyricTranslationOpacity = value.coerceIn(.4f, .9f)
            "lyrics_cascade_delay_ms" -> MeloXSettingsRuntime.lyricCascadeDelayMs = value.coerceIn(0f, 100f)
            "lyrics_cascade_delay_increase_ms" -> MeloXSettingsRuntime.lyricCascadeDelayIncreaseMs = value.coerceIn(0f, 100f)
            "lyrics_cascade_following_delay_ms" -> MeloXSettingsRuntime.lyricCascadeFollowingDelayMs = value.coerceIn(0f, 200f)
            "lyrics_cascade_catch_up_ratio" -> MeloXSettingsRuntime.lyricCascadeCatchUpRatio = value.coerceIn(.5f, 1f)
            "lyrics_cascade_chase_gradient" -> MeloXSettingsRuntime.lyricCascadeChaseSpeedGradient = value.coerceIn(0f, 1f)
            "lyrics_cascade_duration_ms" -> MeloXSettingsRuntime.lyricCascadeDurationMs = value.coerceIn(200f, 1_200f)
            "lyrics_snap_threshold_ms" -> MeloXSettingsRuntime.lyricSnapThresholdMs = value.coerceIn(50f, 500f)
            "lyrics_cascade_bounce" -> MeloXSettingsRuntime.lyricCascadeBounce = value.coerceIn(0f, .8f)
            "lyrics_cascade_bounce_gradient" -> MeloXSettingsRuntime.lyricCascadeBounceGradient = value.coerceIn(0f, 1f)
            "lyrics_scale_bounce" -> MeloXSettingsRuntime.lyricScaleBounce = value.coerceIn(0f, .5f)
            "lyrics_skyline_current_font_size" -> MeloXSettingsRuntime.skylineCurrentFontSize = value.coerceIn(36f, 84f)
            "lyrics_skyline_current_max_scale" -> MeloXSettingsRuntime.skylineCurrentMaximumScale = value.coerceIn(1f, 1.2f)
            "lyrics_skyline_next_font_size" -> MeloXSettingsRuntime.skylineNextFontSize = value.coerceIn(14f, 44f)
            "lyrics_skyline_current_spacing" -> MeloXSettingsRuntime.skylineCurrentSpacing = value.coerceIn(4f, 36f)
            "lyrics_skyline_current_width" -> MeloXSettingsRuntime.skylineCurrentWidth = value.coerceIn(.4f, .82f)
            "lyrics_skyline_next_opacity" -> MeloXSettingsRuntime.skylineNextOpacity = value.coerceIn(.2f, .8f)
            "lyrics_skyline_ambient_font_size" -> MeloXSettingsRuntime.skylineAmbientFontSize = value.coerceIn(24f, 72f)
            "lyrics_skyline_ambient_opacity" -> MeloXSettingsRuntime.skylineAmbientOpacity = value.coerceIn(.4f, 1.8f)
            "lyrics_skyline_ambient_blur" -> MeloXSettingsRuntime.skylineAmbientBlur = value.coerceIn(0f, 2f)
            "lyrics_skyline_ambient_max_tilt" -> MeloXSettingsRuntime.skylineAmbientMaximumTilt = value.coerceIn(0f, 20f)
            "lyrics_skyline_ambient_drift" -> MeloXSettingsRuntime.skylineAmbientDrift = value.coerceIn(0f, 2f)
            "lyrics_text_pv_motion_intensity" -> MeloXSettingsRuntime.textPVMotionIntensity = value.coerceIn(0f, 2f)
            "lyrics_text_pv_animation_speed" -> MeloXSettingsRuntime.textPVAnimationSpeed = value.coerceIn(0f, 4f)
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
            "home_section_order" -> MeloXSettingsRuntime.homeSectionOrder = value.split(',')
                .filter { it in setOf("QuickActions", "Playlists", "NewSongs") }.distinct()
                .let { order -> (order + listOf("QuickActions", "Playlists", "NewSongs")).distinct() }
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
            "lyrics_text_pv_style" -> {
                MeloXSettingsRuntime.textPVStyle = runCatching {
                    MeloXTextPVStyle.valueOf(value)
                }.getOrDefault(MeloXTextPVStyle.BlueBold)
                setFloat(
                    context,
                    "lyrics_text_pv_animation_speed",
                    MeloXSettingsRuntime.textPVStyle.referenceAnimationSpeed,
                )
            }
            "lyrics_font_weight" -> MeloXSettingsRuntime.lyricFontWeight = runCatching {
                MeloXLyricsFontWeight.valueOf(value)
            }.getOrDefault(MeloXLyricsFontWeight.Heavy)
            "lyrics_lift_mode" -> MeloXSettingsRuntime.lyricLiftMode = runCatching {
                MeloXLyricsGroupingMode.valueOf(value)
            }.getOrDefault(MeloXLyricsGroupingMode.Character)
            "lyrics_long_tone_detection" -> MeloXSettingsRuntime.lyricLongToneDetectionMode = runCatching {
                MeloXLyricsGroupingMode.valueOf(value)
            }.getOrDefault(MeloXLyricsGroupingMode.Character)
            "system_lyrics_title_mode" -> MeloXSettingsRuntime.systemLyricTitleMode = runCatching {
                MeloXSystemLyricTitleMode.valueOf(value)
            }.getOrDefault(MeloXSystemLyricTitleMode.LyricFirst)
            "floating_lyrics_secondary_mode" -> MeloXSettingsRuntime.floatingSecondaryMode = runCatching {
                MeloXSecondaryLyricMode.valueOf(value)
            }.getOrDefault(MeloXSecondaryLyricMode.Auto)
            "playback_volume_mode" -> MeloXSettingsRuntime.volumeControlMode = runCatching {
                MeloXVolumeControlMode.valueOf(value)
            }.getOrDefault(MeloXVolumeControlMode.System)
            "player_screen_awake_mode" -> MeloXSettingsRuntime.screenAwakeMode = runCatching {
                MeloXScreenAwakeMode.valueOf(value)
            }.getOrDefault(MeloXScreenAwakeMode.Disabled)
        }
    }

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
        MeloXSettingsRuntime.initialize(context, force = true)
    }
}
