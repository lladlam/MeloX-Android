package com.lladlam.melox.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.ui.text.font.FontWeight
import com.lladlam.melox.R
import com.lladlam.melox.playback.MeloXPlaybackModePreferences
import com.lladlam.melox.ui.theme.MeloXFontPolicy

enum class MeloXThemeMode { System, Light, Dark }
enum class MeloXSwipeFullAction { PlayNext, AddToQueue }
enum class MeloXLyricAnnotationDisplayMode { FocusedLine, AllLines }
enum class MeloXLyricsStyle { AppleMusic, Eva, TextPV }
enum class MeloXLyricsRenderingQuality { Low, Balanced, High }
enum class MeloXPlayerBackgroundMode { FlowingLight, AppleLyrics, BlurredArtwork, CustomImage }

/** Only the two procedural modes use the flowing-light shader path. */
internal val MeloXPlayerBackgroundMode.usesFlowingBackdrop: Boolean
    get() = this != MeloXPlayerBackgroundMode.BlurredArtwork && this != MeloXPlayerBackgroundMode.CustomImage
enum class MeloXPlayerShell { AppleMusic, Classic }

/**
 * 播放器「展开 / 收回」时长的**五档**（2026-10-29 重划）。
 *
 * ### ⚠⚠ 为什么把「毫秒值」和「显示文案」绑在**同一个枚举**里
 *
 * 设置页显示的是**文字档位**（不再显示 ms 数值），而运行时消费的是**毫秒数**。
 * 这两者一旦分家，就会复现本文件历史上最典型的那个 bug：
 * **「不要实际变，设置显示没变」**（显示 360 / 实际 240）。
 * ⇒ 档位值与档位文案**必须同源**：本枚举是唯一真源，
 *   设置页下拉（`SettingsScreen`）与运行时迁移（`playerTransitionDurationMs`）
 *   都从这里取，任何一档改动都不会出现「文案与数值错位」。
 *
 * ### 分档依据
 *
 * · **460ms 居中为「标准」档**（用户 2026-10-29 拍板：以最新版的 460ms 为中间标准值）。
 * · 五档按**≈1.3× 等比**向下 / 向上展开：`270 / 360 / 460 / 600 / 780`。
 *   等比而非等差，是因为人对**时长**的感知是对数的 —— 等差档位在快端挤成一团、
 *   慢端又拉得太开；等比档位的手感间隔才均匀。
 * · `360` 保留为「快」档：`k=3.56 / α=0.39` 这条 AM 真机拟合曲线下
 *   360ms 的**视觉完成度**已明显高于旧 `k=6 / α=0.15`（用户反馈旧版
 *   「360 特别快」正对应旧曲线：`k=6/α=0.15` 下 90% 视觉完成只在标称 59.5% 处）。
 *   ⇒ 360 现在只比 460 快一档，不再是「快到变形」。
 *
 * ⚠ 顺序即档位顺序（**从快到慢**）—— 设置页下拉直接按本枚举顺序渲染。
 */
enum class MeloXPlayerTransitionDurationTier(
    /** 该档的墙钟时长（毫秒）。已抵消系统动画缩放，由 [meloXPlayerTweenDurationMillis] 落地。 */
    val millis: Int,
    /** 该档在设置页显示的文字（**不再显示 ms 数值**）。 */
    @StringRes val labelRes: Int,
) {
    VeryFast(270, R.string.settings_transition_duration_very_fast),
    Fast(360, R.string.settings_transition_duration_fast),
    Standard(460, R.string.settings_transition_duration_standard),
    Slow(600, R.string.settings_transition_duration_slow),
    VerySlow(780, R.string.settings_transition_duration_very_slow),
    ;

    companion object {
        /** 默认档 = 中间的「标准」档（460ms）。 */
        val Default: MeloXPlayerTransitionDurationTier = Standard

        /** 全部档位值（升序），供设置页下拉与迁移归一使用。 */
        val allMillis: List<Int> = entries.map { it.millis }

        /**
         * 把任意毫秒值**吸附**到最近的合法档位。
         *
         * ⚠ 用于「读到非法值」的兜底（**旧档位表**里已不存在的 `240` / `300`，
         *   或用户手改 prefs）：下拉框按**值**匹配选中项（见 `MeloXSettingsDropdown`
         *   的 `items.firstOrNull { it.first == selected }`），值不在集合里
         *   ⇒ 选中项**匹配不到 ⇒ 显示空白**。所以必须先吸附成合法档位。
         *
         * ⚠ 吸附是**就近**（不是向下取整）：旧表残留的 `240 → 270`（差 30）、
         *   `300 → 270`（差 30，比到 `360` 的 60 更近）⇒ 保证老设备残留在
         *   **数值上**最接近的新档位，不会凭空掉一档。
         */
        fun snapToNearest(millis: Int): Int =
            entries.minBy { kotlin.math.abs(it.millis - millis) }.millis
    }
}
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
    var splitScreenEnabled by mutableStateOf(true)
        internal set
    var splitNavigationPosition by mutableStateOf(MeloXSplitChromePosition.BottomLeft)
        internal set
    var splitMiniPlayerPosition by mutableStateOf(MeloXSplitChromePosition.BottomLeft)
        internal set
    var performanceOverlayEnabled by mutableStateOf(false)
        internal set
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
    var podcastsHomePlacement by mutableStateOf(true)
        internal set
    var podcastsTabPlacement by mutableStateOf(false)
        internal set
    var podcastsLibraryPlacement by mutableStateOf(true)
        internal set
    var downloadsHomePlacement by mutableStateOf(false)
        internal set
    var downloadsTabPlacement by mutableStateOf(false)
        internal set
    var downloadsLibraryPlacement by mutableStateOf(true)
        internal set
    var cloudHomePlacement by mutableStateOf(false)
        internal set
    var cloudTabPlacement by mutableStateOf(false)
        internal set
    var cloudLibraryPlacement by mutableStateOf(true)
        internal set
    var flowingLightSpeed by mutableStateOf(1f)
        internal set
    var flowingLightSaturation by mutableStateOf(1f)
        internal set
    var flowingLightBrightness by mutableStateOf(1f)
        internal set
    /**
     * AutoMix boost: while two covers are being cross-faded, ramp the flowing
     * background's saturation and speed up to their maximum (2.00x), then ease
     * them back down once the mix hands off.
     */
    var flowingLightMixBoostEnabled by mutableStateOf(false)
        internal set
    var playerBackgroundMode by mutableStateOf(MeloXPlayerBackgroundMode.FlowingLight)
        internal set
    /** Absolute path of the imported image used by [MeloXPlayerBackgroundMode.CustomImage]. */
    var customBackgroundPath by mutableStateOf<String?>(null)
        internal set
    var playerShell by mutableStateOf(MeloXPlayerShell.AppleMusic)
        internal set
    var artworkMotionEnabled by mutableStateOf(true)
        internal set
    var reduceMotion by mutableStateOf(false)
        internal set
    // ⚠⚠⚠ 2026-10-29：默认值 **360 → 460**（用户拍板「以最新版的 460ms 为中间标准值」，
    //   并把五档重划为 `270/360/460/600/780`，460 居中为「标准」档）。
    //   历史：360 正式默认（2026-10-06 起，v2 迁移把老设备的 240 纠正回 360）。
    //   ⚠ 光改这一行**不够**：`SharedPreferences` 是持久化的，设备里存的旧值会盖住默认值
    //   ⇒ 必须配合 `playerTransitionDurationMs(context)` 里的**一次性 v3 迁移**
    //     （**无条件归一为 460**，只做一次），才能让「设置页显示」与「实际」**同时**立刻变成 460。
    var playerTransitionDurationMs by mutableStateOf(
        MeloXPlayerTransitionDurationTier.Default.millis,
    )
        internal set
    var playerBackgroundIsolationEnabled by mutableStateOf(true)
        internal set
    var frostedGlassEnabled by mutableStateOf(false)
        internal set
    /**
     * 深色模式下**锁死暗色底栏**：关闭底栏按背后内容自适应翻成浅色的逻辑。
     *
     * ⚠ 只锁深色：浅色模式下自适应（暗背景→暗底栏）照常，不在此开关范围内。
     * 默认开启 —— 用户口径「新增机制，深色模式底栏不自适应变色到浅色底栏」。
     */
    var bottomBarLockDarkInDarkMode by mutableStateOf(true)
        internal set
    var keepScreenOn by mutableStateOf(false)
        internal set
    var screenAwakeMode by mutableStateOf(MeloXScreenAwakeMode.Disabled)
        internal set
    var immersivePlaybackEnabled by mutableStateOf(false)
        internal set
    var showLyricTranslation by mutableStateOf(true)
        internal set
    var automaticLyricSelectionEnabled by mutableStateOf(true)
        internal set
    var lyricStrongBindingEnabled by mutableStateOf(false)
        internal set
    var showLyricRomanization by mutableStateOf(true)
        internal set
    var lyricWordByWordEnabled by mutableStateOf(true)
        internal set
    var lyricPseudoTimingEnabled by mutableStateOf(false)
    var qqBracketBackingVocals by mutableStateOf(false)
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
    var lyricBackgroundFrameRate by mutableStateOf(24)
        internal set
    var lyricRenderingQuality by mutableStateOf(MeloXLyricsRenderingQuality.High)
        internal set
    var lyricRomanizationDisplayMode by mutableStateOf(MeloXLyricAnnotationDisplayMode.FocusedLine)
        internal set
    var lyricTranslationDisplayMode by mutableStateOf(MeloXLyricAnnotationDisplayMode.AllLines)
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
    var lyricInactiveOpacity by mutableStateOf(.42f)
        internal set
    var lyricGlowStrength by mutableStateOf(1f)
        internal set
    var lyricGlowEnabled by mutableStateOf(true)
        internal set
    var lyricLongToneStrength by mutableStateOf(1f)
        internal set
    var lyricWordBounceEnabled by mutableStateOf(false)
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
    /** 歌词翻译语言：`system` 或具体语言标签（zh-Hans/zh-Hant/en/tr…）。 */
    var lyricLanguage by mutableStateOf("system")
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
    var lyricNotificationShowArtwork by mutableStateOf(true)
        internal set
    var lyricNotificationBackgroundOnly by mutableStateOf(false)
        internal set
    var lyricNotificationDismissWhenPaused by mutableStateOf(true)
        internal set
    var lyricNotificationTitleTemplate by mutableStateOf("{lyric}")
        internal set
    var lyricNotificationSubtitleTemplate by mutableStateOf("{song} · {artist}")
        internal set
    var lyricNotificationFallback by mutableStateOf("{song} · {artist}")
        internal set
    var floatingLyricsEnabled by mutableStateOf(false)
        internal set
    var floatingSecondaryMode by mutableStateOf(MeloXSecondaryLyricMode.Auto)
        internal set
    var floatingFontSizeSp by mutableStateOf(18)
        internal set
    var floatingHighContrast by mutableStateOf(true)
        internal set
    /**
     * AutoSync: generate a real word-level timeline locally (ONNX) when a song
     * only has line-timed lyrics. Off by default — it is an experimental path
     * that spends CPU and battery on the current track.
     */
    var autoSyncWordLyricsEnabled by mutableStateOf(false)
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
    var artworkTapLyrics by mutableStateOf(false)
    var artworkSwipeTracks by mutableStateOf(false)
        internal set
    var disableAutomaticTabBarShrink by mutableStateOf(false)
        internal set
    var tabOrder by mutableStateOf(listOf("Home", "Explore", "Library", "Podcasts", "Downloads", "Cloud", "Settings"))
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
    var hapticFeedbackEnabled by mutableStateOf(true)
        internal set
    var swipeFullAction by mutableStateOf(MeloXSwipeFullAction.PlayNext)
        internal set
    var previousRestartsAfterFiveSeconds by mutableStateOf(true)
        internal set
    var startsHeartModeOnLaunch by mutableStateOf(false)
        internal set
    var volumeControlMode by mutableStateOf(MeloXVolumeControlMode.System)
        internal set
    var showPlayerQualityTip by mutableStateOf(true)
        internal set
    var systemFontScope by mutableStateOf(MeloXFontPolicy.DEFAULT_SCOPE)
        internal set
    val systemFontEnabled: Boolean get() = MeloXFontPolicy.usesSystemUiFont(systemFontScope)
    val lyricSystemFontEnabled: Boolean get() = MeloXFontPolicy.usesSystemLyricFont(systemFontScope)
    var smartQueueEnabled by mutableStateOf(false)
        internal set
    var transitionUiEnabled by mutableStateOf(false)
        internal set
    var playlistSearchBarHidden by mutableStateOf(false)
        internal set
    var playlistShowArtwork by mutableStateOf(false)
        internal set

    private var initialized = false

    private fun loadSplitScreenSettings(context: Context) {
        splitScreenEnabled = MeloXSettingsPreferences.boolean(context, "general_split_screen", true)
        splitNavigationPosition = MeloXSplitChromePosition.fromPreference(
            MeloXSettingsPreferences.string(context, "general_split_navigation_position", "BottomLeft"),
        )
        splitMiniPlayerPosition = MeloXSplitChromePosition.fromPreference(
            MeloXSettingsPreferences.string(context, "general_split_mini_player_position", "BottomLeft"),
        )
    }

    /** Reads only state required to draw the first root frame. The remaining settings load after setContent. */
    fun initializeCritical(context: Context) {
        val app = context.applicationContext
        loadSplitScreenSettings(app)
        themeMode = runCatching {
            MeloXThemeMode.valueOf(MeloXSettingsPreferences.string(app, "theme_mode", MeloXThemeMode.System.name))
        }.getOrDefault(MeloXThemeMode.System)
        homeTabEnabled = MeloXSettingsPreferences.boolean(app, "tab_home", true)
        exploreTabEnabled = MeloXSettingsPreferences.boolean(app, "tab_explore", true)
        libraryTabEnabled = MeloXSettingsPreferences.boolean(app, "tab_library", true)
        podcastsTabPlacement = MeloXSettingsPreferences.boolean(app, "placement_podcasts_tab", false)
        downloadsTabPlacement = MeloXSettingsPreferences.boolean(app, "placement_downloads_tab", false)
        cloudTabPlacement = MeloXSettingsPreferences.boolean(app, "placement_cloud_tab", false)
        rememberLastTab = MeloXSettingsPreferences.boolean(app, "general_remember_tab", true)
        artworkTapLyrics = MeloXSettingsPreferences.boolean(app, "general_artwork_tap_lyrics", false)
        artworkSwipeTracks = MeloXSettingsPreferences.boolean(app, "general_artwork_swipe_tracks", false)
        disableAutomaticTabBarShrink = MeloXSettingsPreferences.boolean(app, "general_disable_auto_tabbar_shrink", false)
        tabOrder = MeloXSettingsPreferences.string(app, "tab_order", "Home,Explore,Library,Podcasts,Downloads,Cloud,Settings")
            .split(',').filter { it in setOf("Home", "Explore", "Library", "Podcasts", "Downloads", "Cloud", "Settings") }.distinct()
            .let { order -> (order + listOf("Home", "Explore", "Library", "Podcasts", "Downloads", "Cloud", "Settings")).distinct() }
        defaultTab = MeloXSettingsPreferences.string(app, "general_default_tab", "Home")
        rememberLibraryPage = MeloXSettingsPreferences.boolean(app, "library_remember_page", true)
        defaultLibraryPage = MeloXSettingsPreferences.string(app, "library_default_page", "Songs")
        clipboardLinksEnabled = MeloXSettingsPreferences.boolean(app, "general_clipboard_links", true)
        systemFontScope = MeloXFontPolicy.normalize(
            MeloXSettingsPreferences.string(app, "system_font_scope", MeloXFontPolicy.DEFAULT_SCOPE),
        )
    }

    fun initialize(context: Context, force: Boolean = false) {
        if (initialized && !force) return
        initialized = true
        val app = context.applicationContext
        loadSplitScreenSettings(app)
        performanceOverlayEnabled = MeloXSettingsPreferences.boolean(app, "developer_performance_overlay", false)
        themeMode = runCatching {
            MeloXThemeMode.valueOf(MeloXSettingsPreferences.string(app, "theme_mode", MeloXThemeMode.System.name))
        }.getOrDefault(MeloXThemeMode.System)
        podcastsEnabled = MeloXSettingsPreferences.boolean(app, "feature_podcasts", true)
        listeningHistoryEnabled = MeloXSettingsPreferences.boolean(app, "feature_history", true)
        downloadsEnabled = MeloXSettingsPreferences.boolean(app, "feature_downloads", true)
        cloudMusicEnabled = MeloXSettingsPreferences.boolean(app, "feature_cloud_music", true)
        podcastsHomePlacement = MeloXSettingsPreferences.boolean(app, "placement_podcasts_home", true)
        podcastsTabPlacement = MeloXSettingsPreferences.boolean(app, "placement_podcasts_tab", false)
        podcastsLibraryPlacement = MeloXSettingsPreferences.boolean(app, "placement_podcasts_library", true)
        downloadsHomePlacement = MeloXSettingsPreferences.boolean(app, "placement_downloads_home", false)
        downloadsTabPlacement = MeloXSettingsPreferences.boolean(app, "placement_downloads_tab", false)
        downloadsLibraryPlacement = MeloXSettingsPreferences.boolean(app, "placement_downloads_library", true)
        cloudHomePlacement = MeloXSettingsPreferences.boolean(app, "placement_cloud_home", false)
        cloudTabPlacement = MeloXSettingsPreferences.boolean(app, "placement_cloud_tab", false)
        cloudLibraryPlacement = MeloXSettingsPreferences.boolean(app, "placement_cloud_library", true)
        val legacyFlowingBackdropEnabled = MeloXSettingsPreferences.boolean(app, "player_flowing_backdrop", true)
        playerBackgroundMode = MeloXSettingsPreferences.string(app, "player_background_mode", "")
            .takeIf { it.isNotBlank() }
            ?.let { value -> runCatching { MeloXPlayerBackgroundMode.valueOf(value) }.getOrNull() }
            ?: if (legacyFlowingBackdropEnabled) {
                MeloXPlayerBackgroundMode.FlowingLight
            } else {
                MeloXPlayerBackgroundMode.BlurredArtwork
            }
        flowingLightSpeed = MeloXSettingsPreferences.float(app, "player_flowing_speed", 1f).coerceIn(.25f, 2f)
        flowingLightSaturation = MeloXSettingsPreferences.float(app, "player_flowing_saturation", 1f).coerceIn(0f, 2f)
        flowingLightBrightness = MeloXSettingsPreferences.float(app, "player_flowing_brightness", 1f).coerceIn(.4f, 1.6f)
        flowingLightMixBoostEnabled = MeloXSettingsPreferences.boolean(app, "player_flowing_mix_boost", false)
        customBackgroundPath = MeloXSettingsPreferences.string(app, "player_custom_background_path", "")
            .takeIf { it.isNotBlank() && java.io.File(it).isFile }
        // Interrupted imports can leave orphan files behind; neither of them is
        // referenced by preferences anymore, so clean them once at startup.
        runCatching { MeloXCustomBackgroundStore.prune(app, customBackgroundPath) }
        playerShell = runCatching {
            MeloXPlayerShell.valueOf(MeloXSettingsPreferences.string(app, "player_shell", MeloXPlayerShell.AppleMusic.name))
        }.getOrDefault(MeloXPlayerShell.AppleMusic)
        artworkMotionEnabled = MeloXSettingsPreferences.boolean(app, "player_artwork_motion", true)
        reduceMotion = MeloXSettingsPreferences.boolean(app, "reduce_motion", false)
        // ⚠⚠ 2026-10-29：**正式默认 = 460**（档位表重划为 270/360/460/600/780，
        //   460 居中为「标准」档；360 是 2026-10-06~10-29 的正式默认，
        //   240 则是 2026-10-03~10-06 的测试期口径）。
        // ⚠⚠⚠ 默认值迁移（v1 的 360→240、v2 的 240→360、v3 的 360→460）**都不在这里做** ——
        //   本函数只被 `MeloXSettingsRuntime` 读，而设置页走
        //   `MeloXSettingsPreferences.playerTransitionDurationMs(...)`，
        //   两条路径不同源 ⇒ 迁移放这里会导致「显示 / 实际」不一致。
        //   统一走 [playerTransitionDurationMs]（读 + 迁移 + 写回同一处）。
        // ⚠⚠⚠ 必须带 `MeloXSettingsPreferences.` 前缀：本函数在 `MeloXSettingsRuntime`
        //   内部，而 `MeloXSettingsRuntime` 也有同名属性 `playerTransitionDurationMs`
        //   ⇒ 裸写会被解析成**属性自赋值**，编译报 `Unresolved reference`。
        playerTransitionDurationMs =
            MeloXSettingsPreferences.playerTransitionDurationMs(app)
        playerBackgroundIsolationEnabled = MeloXSettingsPreferences.boolean(app, "player_background_isolation", true)
        frostedGlassEnabled = MeloXSettingsPreferences.boolean(app, "player_frosted_glass", false)
        bottomBarLockDarkInDarkMode = MeloXSettingsPreferences.boolean(app, "bottom_bar_lock_dark_in_dark_mode", true)
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
        immersivePlaybackEnabled = MeloXSettingsPreferences.boolean(app, "immersive_playback", false)
        showPlayerQualityTip = MeloXSettingsPreferences.boolean(app, "player_show_quality_tip", true)
        systemFontScope = MeloXFontPolicy.normalize(
            MeloXSettingsPreferences.string(app, "system_font_scope", MeloXFontPolicy.DEFAULT_SCOPE),
        )
        smartQueueEnabled = MeloXPlaybackModePreferences.smartQueue(app)
        transitionUiEnabled = MeloXSettingsPreferences.boolean(app, "transition_ui_enabled", false)
        playlistSearchBarHidden = MeloXSettingsPreferences.boolean(app, "library_playlist_search_hidden", false)
        playlistShowArtwork = MeloXSettingsPreferences.boolean(app, "library_playlist_show_artwork", false)
        showLyricTranslation = MeloXSettingsPreferences.boolean(app, "lyrics_translation", true)
        automaticLyricSelectionEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_auto_select", true)
        lyricStrongBindingEnabled = MeloXSettingsPreferences.boolean(app, "experimental_lyric_strong_binding", false)
        showLyricRomanization = MeloXSettingsPreferences.boolean(app, "lyrics_romanization", false)
        lyricWordByWordEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_word_by_word", true)
        lyricPseudoTimingEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_pseudo_timing", false)
        qqBracketBackingVocals = MeloXSettingsPreferences.boolean(app, "lyrics_qq_bracket_backing", false)
        lyricTapSeekEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_tap_seek", true)
        lyricLongPressShareEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_long_press_share", true)
        lyricInterludeCountdownEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_interlude_countdown", true)
        lyricAutoFollowEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_auto_follow", true)
        lyricReduceMotion = MeloXSettingsPreferences.boolean(app, "lyrics_reduce_motion", false)
        lyricAdvanceMs = MeloXSettingsPreferences.int(app, "lyrics_advance_ms", 0).coerceIn(-5_000, 5_000)
        lyricAdvanceAppliesToWordByWord = MeloXSettingsPreferences.boolean(app, "lyrics_advance_word_by_word", false)
        lyricLanguage = MeloXSettingsPreferences.string(app, "lyric_language", "system")
        lyricRefreshRate = MeloXSettingsPreferences.int(app, "lyrics_refresh_rate", 60)
            .takeIf { it in setOf(30, 60, 90, 120) } ?: 60
        lyricBackgroundFrameRate = MeloXSettingsPreferences.int(app, "lyrics_background_frame_rate", 24)
            .takeIf { it in setOf(15, 24, 30, 45, 60) } ?: 24
        lyricRenderingQuality = runCatching {
            MeloXLyricsRenderingQuality.valueOf(
                MeloXSettingsPreferences.string(
                    app,
                    "lyrics_rendering_quality",
                    MeloXLyricsRenderingQuality.High.name,
                ),
            )
        }.getOrDefault(MeloXLyricsRenderingQuality.High)
        lyricRomanizationDisplayMode = annotationMode(app, "lyrics_romanization_display_mode", MeloXLyricAnnotationDisplayMode.FocusedLine)
        lyricTranslationDisplayMode = annotationMode(app, "lyrics_translation_display_mode", MeloXLyricAnnotationDisplayMode.AllLines)
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
        lyricInactiveOpacity = MeloXSettingsPreferences.float(app, "lyrics_inactive_opacity", .42f).coerceIn(.15f, .65f)
        lyricGlowStrength = MeloXSettingsPreferences.float(app, "lyrics_glow_strength", 1f).coerceIn(0f, 1.5f)
        lyricGlowEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_glow_enabled", true)
        lyricLongToneStrength = MeloXSettingsPreferences.float(app, "lyrics_long_tone_strength", 1f).coerceIn(0f, 1.5f)
        lyricWordBounceEnabled = MeloXSettingsPreferences.boolean(app, "lyrics_word_bounce_enabled", false)
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
        lyricNotificationShowArtwork = MeloXSettingsPreferences.boolean(app, "lyrics_notification_artwork", true)
        lyricNotificationBackgroundOnly = MeloXSettingsPreferences.boolean(app, "lyrics_notification_background_only", false)
        lyricNotificationDismissWhenPaused = MeloXSettingsPreferences.boolean(app, "lyrics_notification_dismiss_paused", true)
        lyricNotificationTitleTemplate = MeloXSettingsPreferences.string(app, "lyrics_notification_title_template", "{lyric}")
        lyricNotificationSubtitleTemplate = MeloXSettingsPreferences.string(app, "lyrics_notification_subtitle_template", "{song} · {artist}")
        lyricNotificationFallback = MeloXSettingsPreferences.string(app, "lyrics_notification_fallback", "{song} · {artist}")
        floatingLyricsEnabled = MeloXSettingsPreferences.boolean(app, "floating_lyrics_enabled", false)
        floatingSecondaryMode = runCatching {
            MeloXSecondaryLyricMode.valueOf(
                MeloXSettingsPreferences.string(app, "floating_lyrics_secondary_mode", MeloXSecondaryLyricMode.Auto.name),
            )
        }.getOrDefault(MeloXSecondaryLyricMode.Auto)
        floatingFontSizeSp = MeloXSettingsPreferences.int(app, "floating_lyrics_font_size", 18).coerceIn(14, 28)
        floatingHighContrast = MeloXSettingsPreferences.boolean(app, "floating_lyrics_high_contrast", true)
        autoSyncWordLyricsEnabled = MeloXSettingsPreferences.boolean(app, "autosync_word_lyrics_enabled", false)
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
        artworkTapLyrics = MeloXSettingsPreferences.boolean(app, "general_artwork_tap_lyrics", false)
        artworkSwipeTracks = MeloXSettingsPreferences.boolean(app, "general_artwork_swipe_tracks", false)
        disableAutomaticTabBarShrink = MeloXSettingsPreferences.boolean(app, "general_disable_auto_tabbar_shrink", false)
        tabOrder = MeloXSettingsPreferences.string(app, "tab_order", "Home,Explore,Library,Podcasts,Downloads,Cloud,Settings")
            .split(',').filter { it in setOf("Home", "Explore", "Library", "Podcasts", "Downloads", "Cloud", "Settings") }.distinct()
            .let { order -> (order + listOf("Home", "Explore", "Library", "Podcasts", "Downloads", "Cloud", "Settings")).distinct() }
        defaultTab = MeloXSettingsPreferences.string(app, "general_default_tab", "Home")
        rememberLibraryPage = MeloXSettingsPreferences.boolean(app, "library_remember_page", true)
        defaultLibraryPage = MeloXSettingsPreferences.string(app, "library_default_page", "Songs")
        downloadLyricsEnabled = MeloXSettingsPreferences.boolean(app, "download_lyrics", true)
        musicArea = MeloXSettingsPreferences.string(app, "music_area", "全部")
        showPlaylistPlayCount = MeloXSettingsPreferences.boolean(app, "content_playlist_play_count", true)
        showHighQualityPlaylists = MeloXSettingsPreferences.boolean(app, "content_high_quality_playlist", true)
        clipboardLinksEnabled = MeloXSettingsPreferences.boolean(app, "general_clipboard_links", true)
        hapticFeedbackEnabled = MeloXSettingsPreferences.boolean(app, "general_haptic_feedback", true)
        swipeFullAction = runCatching {
            MeloXSwipeFullAction.valueOf(
                MeloXSettingsPreferences.string(app, "general_swipe_full_action", MeloXSwipeFullAction.PlayNext.name),
            )
        }.getOrDefault(MeloXSwipeFullAction.PlayNext)
        previousRestartsAfterFiveSeconds = MeloXSettingsPreferences.boolean(app, "playback_previous_restarts", true)
        startsHeartModeOnLaunch = MeloXSettingsPreferences.boolean(app, "playback_heart_mode_on_launch", false)
        volumeControlMode = runCatching {
            MeloXVolumeControlMode.valueOf(MeloXSettingsPreferences.string(app, "playback_volume_mode", MeloXVolumeControlMode.System.name))
        }.getOrDefault(MeloXVolumeControlMode.System)
    }

    private fun annotationMode(
        context: Context,
        key: String,
        default: MeloXLyricAnnotationDisplayMode,
    ): MeloXLyricAnnotationDisplayMode = runCatching {
        MeloXLyricAnnotationDisplayMode.valueOf(
            MeloXSettingsPreferences.string(context, key, default.name),
        )
    }.getOrDefault(default)
}

object MeloXSettingsPreferences {
    private const val NAME = "melox_app_settings"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun initialize(context: Context) = MeloXSettingsRuntime.initialize(context)

    fun initializeCritical(context: Context) = MeloXSettingsRuntime.initializeCritical(context)

    fun boolean(context: Context, key: String, default: Boolean = false): Boolean =
        prefs(context).getBoolean(key, default)

    fun string(context: Context, key: String, default: String = ""): String =
        prefs(context).getString(key, default) ?: default

    fun int(context: Context, key: String, default: Int = 0): Int =
        prefs(context).getInt(key, default)

    /**
     * 读取 `player_transition_duration_ms`，并做**一次性**的旧默认迁移。
     *
     * ### ⚠⚠ 迁移史（三次**方向相反**，别搞混）
     * · **v1**（2026-10-03，**已废弃**）：默认 `360 → 240`，一次性标记键
     *   `..._migrated_v1`。当时口径是「对齐 Apple Music 参考 ~200ms」。
     * · **v2**（2026-10-06，**已废弃**）：确认 **240 只是测试期口径**，正式默认回到 **360**
     *   → 把老设备里残留的 240 纠正回 360，标记键 `..._migrated_v2`。
     * · **v3**（2026-10-29，**当前**）：用户拍板「以**最新版**的 **460ms** 为**中间标准值**，
     *   重新划分五个档位」⇒ 档位表重划为 `270/360/460/600/780`（460 居中），
     *   正式默认 `360 → 460`，标记键 `..._migrated_v3`。
     *   ⚠⚠ **必须用新键**：老设备的 v1 / v2 标记都已是 `true`，复用旧键会让纠正**永不执行**
     *     （正是「改了默认值但设备上没变」的经典坑）。
     *
     * ⚠⚠⚠ 为什么迁移必须落在**这个被两边共用的读取函数**上：
     *   设置页那项走 `selectedProvider = { playerTransitionDurationMs(context) }`
     *   （见 `SettingsScreen` 的「播放器展开/收回时长」），而运行时
     *   `MeloXSettingsRuntime.load()` 也调本函数 ⇒ 只有在这里迁移，
     *   「**显示 == 实际**」才成立。只在 `load()` 里迁 ⇒ 设置页仍显示旧值
     *   （历史报障原文：「**不要实际变，设置显示没变**」）。
     *
     * ⚠ v3 迁移是**无条件归一为 460**（不是「只把某个旧值改成 460」）：用户要的是
     *   「默认值**和设置显示**都回到 460」，而持久化值会盖住默认值，设备上还可能残留
     *   旧档位表里的 `240/300/360/600` ⇒ 只有无条件归一才能让设置页**立刻**显示 460。
     *   **只做一次**，之后 `270/360/460/600/780` 五档全部自由可调、正常持久化。
     *
     * ⚠⚠ **返回值一律吸附到合法档位**（`snapToNearest`）：下拉框按**值**匹配选中项，
     *   值不在档位集合里 ⇒ 选中项匹配不到 ⇒ **显示空白**。所以老设备残留的
     *   `240/300/600` 必须先吸附成 `270/360/600` 之一再返回。
     */
    fun playerTransitionDurationMs(context: Context): Int {
        val store = prefs(context)
        val migrated = store.getBoolean(KEY_PLAYER_TRANSITION_DURATION_MIGRATED_V3, false)
        if (!migrated) {
            // ⚠⚠ 2026-10-29（用户口径：「以**最新版**的 **460ms** 为**中间标准值**，
            //   重新划分五个档位」）：
            //   这是**档位表重划**（不只是默认值纠正），所以这里**无条件**归一到
            //   新默认档 460（= `MeloXPlayerTransitionDurationTier.Default.millis`），
            //   而不是「只把某个旧值改成 460」——
            //   · 用户要的是「**设置显示**也立刻是 460」，只改默认值 / 只挑某个旧值改
            //     都盖不住持久化值（设备上可能残留旧档位表的 240/300/360/600）；
            //   · **只做一次**（v3 标记），此后 270/360/460/600/780 五档全部自由可调、
            //     正常持久化，用户选什么就是什么。
            //   ⚠ 无存储值的新装设备走同一个分支 ⇒ 同样得到 460（正式默认）。
            val defaultMillis = MeloXPlayerTransitionDurationTier.Default.millis
            store.edit()
                .putInt(KEY_PLAYER_TRANSITION_DURATION_MS, defaultMillis)
                .putBoolean(KEY_PLAYER_TRANSITION_DURATION_MIGRATED_V3, true)
                .apply()
            return defaultMillis
        }
        val stored = store.getInt(
            KEY_PLAYER_TRANSITION_DURATION_MS,
            MeloXPlayerTransitionDurationTier.Default.millis,
        )
        // ⚠ 兜底：旧档位表的 240/300 在新表里**不存在** ⇒ 必须吸附，否则设置页显示空白。
        return MeloXPlayerTransitionDurationTier.snapToNearest(stored)
    }

    const val KEY_PLAYER_TRANSITION_DURATION_MS = "player_transition_duration_ms"

    /**
     * v3 一次性标记：「档位表重划（240/300/360/460/600 → 270/360/460/600/780）」+
     * 正式默认 `360 → 460`。
     *
     * ⚠ 用**独立键**（不复用 v1 / v2 的 `..._migrated_v1` / `_v2`）—— 老设备那些标记
     *   已为 true，复用会导致纠正永不执行。
     * ⚠ 也不能用「值等于某个旧档就算没选过」的判据单独做：那会让**用户手动选的那个档**
     *   每次读取都被拉回 460（该档位不可持久化）。所以照样只在首读时做一次。
     */
    private const val KEY_PLAYER_TRANSITION_DURATION_MIGRATED_V3 =
        "player_transition_duration_ms__migrated_v3"

    fun float(context: Context, key: String, default: Float = 0f): Float =
        prefs(context).getFloat(key, default)

    fun number(context: Context, key: String, default: Float = 0f): Float =
        when (val value = prefs(context).all[key]) {
            is Float -> value
            is Int -> value.toFloat()
            is Long -> value.toFloat()
            is Double -> value.toFloat()
            else -> default
        }

    fun long(context: Context, key: String, default: Long = 0L): Long =
        prefs(context).getLong(key, default)

    fun setLong(context: Context, key: String, value: Long) {
        prefs(context).edit().putLong(key, value).apply()
    }

    fun setBoolean(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
        if (key == "lyrics_auto_select" && !value) {
            prefs(context).edit().putBoolean("experimental_lyric_strong_binding", false).apply()
            MeloXSettingsRuntime.lyricStrongBindingEnabled = false
            com.lladlam.melox.core.lyrics.LyricBindingStore.clear(context)
        }
        if (key == "experimental_lyric_strong_binding" && !value) {
            com.lladlam.melox.core.lyrics.LyricBindingStore.clear(context)
        }
        when (key) {
            "general_split_screen" -> MeloXSettingsRuntime.splitScreenEnabled = value
            "developer_performance_overlay" -> MeloXSettingsRuntime.performanceOverlayEnabled = value
            "feature_podcasts" -> MeloXSettingsRuntime.podcastsEnabled = value
            "feature_history" -> MeloXSettingsRuntime.listeningHistoryEnabled = value
            "feature_downloads" -> MeloXSettingsRuntime.downloadsEnabled = value
            "feature_cloud_music" -> MeloXSettingsRuntime.cloudMusicEnabled = value
            "placement_podcasts_home" -> MeloXSettingsRuntime.podcastsHomePlacement = value
            "placement_podcasts_tab" -> MeloXSettingsRuntime.podcastsTabPlacement = value
            "placement_podcasts_library" -> MeloXSettingsRuntime.podcastsLibraryPlacement = value
            "placement_downloads_home" -> MeloXSettingsRuntime.downloadsHomePlacement = value
            "placement_downloads_tab" -> MeloXSettingsRuntime.downloadsTabPlacement = value
            "placement_downloads_library" -> MeloXSettingsRuntime.downloadsLibraryPlacement = value
            "placement_cloud_home" -> MeloXSettingsRuntime.cloudHomePlacement = value
            "placement_cloud_tab" -> MeloXSettingsRuntime.cloudTabPlacement = value
            "placement_cloud_library" -> MeloXSettingsRuntime.cloudLibraryPlacement = value
            "player_artwork_motion" -> MeloXSettingsRuntime.artworkMotionEnabled = value
            "player_flowing_mix_boost" -> MeloXSettingsRuntime.flowingLightMixBoostEnabled = value
            "reduce_motion" -> MeloXSettingsRuntime.reduceMotion = value
            "player_background_isolation" -> MeloXSettingsRuntime.playerBackgroundIsolationEnabled = value
            "player_frosted_glass" -> MeloXSettingsRuntime.frostedGlassEnabled = value
            "bottom_bar_lock_dark_in_dark_mode" -> MeloXSettingsRuntime.bottomBarLockDarkInDarkMode = value
            "player_keep_screen_on" -> MeloXSettingsRuntime.keepScreenOn = value
            "lyrics_translation" -> MeloXSettingsRuntime.showLyricTranslation = value
            "lyrics_auto_select" -> MeloXSettingsRuntime.automaticLyricSelectionEnabled = value
            "experimental_lyric_strong_binding" -> MeloXSettingsRuntime.lyricStrongBindingEnabled = value
            "lyrics_romanization" -> MeloXSettingsRuntime.showLyricRomanization = value
            "lyrics_word_by_word" -> MeloXSettingsRuntime.lyricWordByWordEnabled = value
            "lyrics_pseudo_timing" -> MeloXSettingsRuntime.lyricPseudoTimingEnabled = value
            "lyrics_qq_bracket_backing" -> MeloXSettingsRuntime.qqBracketBackingVocals = value
            "lyrics_tap_seek" -> MeloXSettingsRuntime.lyricTapSeekEnabled = value
            "lyrics_long_press_share" -> MeloXSettingsRuntime.lyricLongPressShareEnabled = value
            "lyrics_interlude_countdown" -> MeloXSettingsRuntime.lyricInterludeCountdownEnabled = value
            "lyrics_auto_follow" -> MeloXSettingsRuntime.lyricAutoFollowEnabled = value
            "lyrics_reduce_motion" -> MeloXSettingsRuntime.lyricReduceMotion = value
            "lyrics_glow_long_tones_only" -> MeloXSettingsRuntime.lyricGlowLongTonesOnly = value
            "lyrics_glow_enabled" -> MeloXSettingsRuntime.lyricGlowEnabled = value
            "lyrics_word_bounce_enabled" -> MeloXSettingsRuntime.lyricWordBounceEnabled = value
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
            "lyrics_notification_artwork" -> MeloXSettingsRuntime.lyricNotificationShowArtwork = value
            "lyrics_notification_background_only" -> MeloXSettingsRuntime.lyricNotificationBackgroundOnly = value
            "lyrics_notification_dismiss_paused" -> MeloXSettingsRuntime.lyricNotificationDismissWhenPaused = value
            "floating_lyrics_enabled" -> MeloXSettingsRuntime.floatingLyricsEnabled = value
            "floating_lyrics_high_contrast" -> MeloXSettingsRuntime.floatingHighContrast = value
            "autosync_word_lyrics_enabled" -> MeloXSettingsRuntime.autoSyncWordLyricsEnabled = value
            "tab_home" -> MeloXSettingsRuntime.homeTabEnabled = value
            "home_quick_actions" -> MeloXSettingsRuntime.homeQuickActionsEnabled = value
            "home_playlists" -> MeloXSettingsRuntime.homePlaylistsEnabled = value
            "home_new_songs" -> MeloXSettingsRuntime.homeNewSongsEnabled = value
            "tab_explore" -> MeloXSettingsRuntime.exploreTabEnabled = value
            "tab_library" -> MeloXSettingsRuntime.libraryTabEnabled = value
            "general_remember_tab" -> MeloXSettingsRuntime.rememberLastTab = value
            "general_artwork_tap_lyrics" -> MeloXSettingsRuntime.artworkTapLyrics = value
            "general_artwork_swipe_tracks" -> MeloXSettingsRuntime.artworkSwipeTracks = value
            "general_disable_auto_tabbar_shrink" -> MeloXSettingsRuntime.disableAutomaticTabBarShrink = value
            "library_remember_page" -> MeloXSettingsRuntime.rememberLibraryPage = value
            "download_lyrics" -> MeloXSettingsRuntime.downloadLyricsEnabled = value
            "content_playlist_play_count" -> MeloXSettingsRuntime.showPlaylistPlayCount = value
            "content_high_quality_playlist" -> MeloXSettingsRuntime.showHighQualityPlaylists = value
            "general_clipboard_links" -> MeloXSettingsRuntime.clipboardLinksEnabled = value
            "general_haptic_feedback" -> MeloXSettingsRuntime.hapticFeedbackEnabled = value
            "playback_previous_restarts" -> MeloXSettingsRuntime.previousRestartsAfterFiveSeconds = value
            "playback_heart_mode_on_launch" -> MeloXSettingsRuntime.startsHeartModeOnLaunch = value
            "immersive_playback" -> MeloXSettingsRuntime.immersivePlaybackEnabled = value
            "player_show_quality_tip" -> MeloXSettingsRuntime.showPlayerQualityTip = value
            "library_playlist_search_hidden" -> MeloXSettingsRuntime.playlistSearchBarHidden = value
            "library_playlist_show_artwork" -> MeloXSettingsRuntime.playlistShowArtwork = value

        }
    }

    fun setInt(context: Context, key: String, value: Int) {
        // ⚠ 时长档位：写盘前就**吸附到合法档位**（而非写原值、只在运行时吸附）——
        //   否则「持久化值」与「运行时值」会不一致，下一次冷启动读取时又会被吸附
        //   （值虽然一样，但写盘值非法 ⇒ 设置页下拉匹配不到 ⇒ 显示空白）。
        val effective = if (key == KEY_PLAYER_TRANSITION_DURATION_MS) {
            MeloXPlayerTransitionDurationTier.snapToNearest(value)
        } else {
            value
        }
        prefs(context).edit().putInt(key, effective).apply()
        when (key) {
            "lyrics_advance_ms" -> MeloXSettingsRuntime.lyricAdvanceMs = value.coerceIn(-5_000, 5_000)
            "lyrics_follow_delay_ms" -> MeloXSettingsRuntime.lyricFollowDelayMs = value.coerceIn(1_000, 8_000)
            "lyrics_refresh_rate" -> MeloXSettingsRuntime.lyricRefreshRate =
                value.takeIf { it in setOf(30, 60, 90, 120) } ?: 60
            "lyrics_background_frame_rate" -> MeloXSettingsRuntime.lyricBackgroundFrameRate =
                value.takeIf { it in setOf(15, 24, 30, 45, 60) } ?: 24
            // ⚠ 写盘值已在 `setInt` 入口**吸附到合法档位**（而非 `coerceIn`）：
            //   非法值 ⇒ 设置页下拉匹配不到选中项 ⇒ 显示**空白**。
            "player_transition_duration_ms" -> MeloXSettingsRuntime.playerTransitionDurationMs =
                effective
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
            "player_flowing_speed" -> MeloXSettingsRuntime.flowingLightSpeed = value.coerceIn(.25f, 2f)
            "player_flowing_saturation" -> MeloXSettingsRuntime.flowingLightSaturation = value.coerceIn(0f, 2f)
            "player_flowing_brightness" -> MeloXSettingsRuntime.flowingLightBrightness = value.coerceIn(.4f, 1.6f)
        }
    }

    fun setString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
        when (key) {
            "general_split_navigation_position" -> MeloXSettingsRuntime.splitNavigationPosition = MeloXSplitChromePosition.fromPreference(value)
            "general_split_mini_player_position" -> MeloXSettingsRuntime.splitMiniPlayerPosition = MeloXSplitChromePosition.fromPreference(value)
            "theme_mode" -> MeloXSettingsRuntime.themeMode = runCatching {
                MeloXThemeMode.valueOf(value)
            }.getOrDefault(MeloXThemeMode.System)
            "music_area" -> MeloXSettingsRuntime.musicArea = value
            "tab_order" -> MeloXSettingsRuntime.tabOrder = value.split(',')
                .filter { it in setOf("Home", "Explore", "Library", "Podcasts", "Downloads", "Cloud", "Settings") }.distinct()
                .let { order -> (order + listOf("Home", "Explore", "Library", "Podcasts", "Downloads", "Cloud", "Settings")).distinct() }
            "home_section_order" -> MeloXSettingsRuntime.homeSectionOrder = value.split(',')
                .filter { it in setOf("QuickActions", "Playlists", "NewSongs") }.distinct()
                .let { order -> (order + listOf("QuickActions", "Playlists", "NewSongs")).distinct() }
            "general_default_tab" -> MeloXSettingsRuntime.defaultTab = value
            "system_font_scope" -> MeloXSettingsRuntime.systemFontScope = value
            "general_swipe_full_action" -> MeloXSettingsRuntime.swipeFullAction = runCatching {
                MeloXSwipeFullAction.valueOf(value)
            }.getOrDefault(MeloXSwipeFullAction.PlayNext)
            "library_default_page" -> MeloXSettingsRuntime.defaultLibraryPage = value
            "lyrics_romanization_display_mode" -> MeloXSettingsRuntime.lyricRomanizationDisplayMode = runCatching {
                MeloXLyricAnnotationDisplayMode.valueOf(value)
            }.getOrDefault(MeloXLyricAnnotationDisplayMode.FocusedLine)
            "lyrics_translation_display_mode" -> MeloXSettingsRuntime.lyricTranslationDisplayMode = runCatching {
                MeloXLyricAnnotationDisplayMode.valueOf(value)
            }.getOrDefault(MeloXLyricAnnotationDisplayMode.AllLines)
            "lyrics_rendering_quality" -> MeloXSettingsRuntime.lyricRenderingQuality = runCatching {
                MeloXLyricsRenderingQuality.valueOf(value)
            }.getOrDefault(MeloXLyricsRenderingQuality.High)
            "lyrics_style" -> MeloXSettingsRuntime.lyricsStyle = runCatching {
                MeloXLyricsStyle.valueOf(value)
            }.getOrDefault(MeloXLyricsStyle.AppleMusic)
            "lyric_language" -> MeloXSettingsRuntime.lyricLanguage = value
            "player_background_mode" -> MeloXSettingsRuntime.playerBackgroundMode = runCatching {
                MeloXPlayerBackgroundMode.valueOf(value)
            }.getOrDefault(MeloXPlayerBackgroundMode.FlowingLight)
            "player_custom_background_path" -> MeloXSettingsRuntime.customBackgroundPath =
                value.takeIf { it.isNotBlank() }
            "player_shell" -> MeloXSettingsRuntime.playerShell = runCatching {
                MeloXPlayerShell.valueOf(value)
            }.getOrDefault(MeloXPlayerShell.AppleMusic)
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
            "lyrics_notification_title_template" -> MeloXSettingsRuntime.lyricNotificationTitleTemplate = value
            "lyrics_notification_subtitle_template" -> MeloXSettingsRuntime.lyricNotificationSubtitleTemplate = value
            "lyrics_notification_fallback" -> MeloXSettingsRuntime.lyricNotificationFallback = value
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

    fun resetRecommendedPlayerSettings(context: Context) {
        prefs(context).edit()
            .remove("player_background_mode")
            .remove("player_shell")
            .remove("player_flowing_backdrop")
            .remove("player_artwork_motion")
            .remove("player_background_isolation")
            .remove("player_frosted_glass")
            .remove(KEY_PLAYER_TRANSITION_DURATION_MS)
            // ⚠ 2026-10-06：连**迁移标记**一起清掉，否则「恢复默认」后标记仍在
            //   ⇒ 迁移逻辑不再跑。清掉后下次读取走首读分支 ⇒ 回到**正式默认**。
            //   ⚠ 当前生效的是 **v3** 标记（档位表重划 270/360/460/600/780，
            //     默认 460）；v2 标记已废弃，一并清掉以免残留。
            .remove(KEY_PLAYER_TRANSITION_DURATION_MIGRATED_V3)
            .remove("lyrics_style")
            .remove("lyrics_rendering_quality")
            .remove("lyrics_text_pv_style")
            .remove("lyrics_text_pv_motion_intensity")
            .remove("lyrics_text_pv_animation_speed")
            .apply()
        MeloXPlaybackModePreferences.reset(context)
        MeloXSettingsRuntime.initialize(context.applicationContext, force = true)
    }
}
