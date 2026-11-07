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
    var heartModeEnabled by mutableStateOf(false)
        internal set
    var smartQueueEnabled by mutableStateOf(false)
        internal set
}

object MeloXPlaybackModePreferences {
    private const val NAME = "melox_playback_modes"
    private const val KEY_SHUFFLE = "shuffle"
    private const val KEY_AUTOPLAY = "autoplay"
    private const val KEY_AUTOMIX = "auto_mix"
    private const val KEY_HEART = "heart_mode"
    private const val KEY_SMART_QUEUE = "smart_queue"

    internal fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Loads the process snapshot once so playback hot paths never poll SharedPreferences. */
    fun initialize(context: Context) {
        val preferences = preferences(context)
        MeloXPlaybackModeRuntime.shuffleEnabled = preferences.getBoolean(KEY_SHUFFLE, false)
        MeloXPlaybackModeRuntime.autoplayEnabled = preferences.getBoolean(KEY_AUTOPLAY, false)
        MeloXPlaybackModeRuntime.autoMixEnabled = preferences.getBoolean(KEY_AUTOMIX, false)
        MeloXPlaybackModeRuntime.heartModeEnabled = preferences.getBoolean(KEY_HEART, false)
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

    fun heartMode(context: Context): Boolean =
        preferences(context)
            .getBoolean(KEY_HEART, false)

    fun setHeartMode(context: Context, enabled: Boolean) {
        MeloXPlaybackModeRuntime.heartModeEnabled = enabled
        preferences(context)
            .edit().putBoolean(KEY_HEART, enabled).apply()
    }

    /**
     * 退出心动档位（心动 + 无尽一起关）。
     *
     * 心动模式绑定的是**心动队列本身**，不是一道全局开关：用户去别的歌单 / 搜索结果里
     * 点了首歌，时间线就被整体替换成普通队列，再亮着 ♥ 就是撒谎（按钮亮、队里却全是
     * 普通歌）。网易云原版也是这个口径——心动模式一被别的播放动作打断就退回列表循环。
     *
     * 一并关掉「无尽播放」而不是退回 ∞ 档：队列页那颗按钮是三态（关 → ∞ → ♥），
     * 只清 heart 会留下一个半亮的 ∞，用户看到的就是「取消了但还亮着」。
     */
    fun exitHeartMode(context: Context) {
        // 「档位」才是队列页按钮的状态（♥ 高亮 + 无尽播放绑定）；heartModeActive 只是
        // 当前时间线是不是心动队列。首页自动启动 / 资料库入口的 `playAll(heartMode = true)`
        // 只翻 active 不写档位，这种情形只清 active，别顺手把用户的「无尽播放」关掉。
        val heartTier = MeloXPlaybackModeRuntime.heartModeEnabled || heartMode(context)
        if (!heartTier && !MeloXPlaybackModeRuntime.heartModeActive) return
        setHeartMode(context, false)
        if (heartTier) setAutoplay(context, false)
        MeloXPlaybackModeRuntime.heartModeActive = false
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
        MeloXPlaybackModeRuntime.heartModeEnabled = false
        MeloXPlaybackModeRuntime.smartQueueEnabled = false
        preferences(context).edit().clear().apply()
    }
}
