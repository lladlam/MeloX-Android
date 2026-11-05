package com.lladlam.melox

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.lladlam.melox.core.diagnostics.MeloXCrashStore
import com.lladlam.melox.core.network.MeloXHttpClient
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.audio.MusicQualityRuntime
import com.lladlam.melox.core.diagnostics.MeloXCrashRecorder
import com.lladlam.melox.ui.animation.MeloXSystemScale
import com.lladlam.melox.ui.player.ArtworkDynamicPaletteProvider

class MeloXApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Isolated plugin workers must not initialize app accounts, caches, diagnostics or UI.
        if (android.os.Process.myUid() != applicationInfo.uid) return
        // 崩溃现场记录：尽早接管，保证第一次 UI 帧之后的任何崩溃都能落到 filesDir/crash/。
        // ⚠ 只记录、不降级 —— 玻璃是否继续渲染与它无关（见 MeloXCrashRecorder 说明）。
        MeloXCrashRecorder.install(this)
        // 上游 0.6.2（806a23b）：持久化上次崩溃。
        // ⚠ 同一提交里的 `ShizukuXmsfNetworkHelper.installHiddenApiExemptions()` 未合：
        //    它随小米超级岛那批（b67a94c）一起改，与本地播放器 UI 冲突太大，单独摘会
        //    缺方法定义。需要小米适配时再连同 b67a94c 一起合。
        MeloXCrashStore.install(this)
        MusicQualityRuntime.selected = MusicQualityPreferences.read(this)
        MeloXHttpClient.initialize(this)
        coil3.SingletonImageLoader.setSafe { context -> coil3.ImageLoader.Builder(context).components {
            add(com.lladlam.melox.core.plugin.PluginArtworkFetcher.Guard(context))
            add(com.lladlam.melox.core.plugin.PluginArtworkFetcher.Factory(context))
        }.build() }
        com.lladlam.melox.core.plugin.MeloXPlugins.start(this)
        // 让 Compose 侧的整页转场（首页 / 搜索 / 资料库 / 播客的一镜到底）跟随**与系统
        // Activity 转场同一个**动画缩放键（TRANSITION_ANIMATION_SCALE）：否则在「过渡动画
        // 缩放」上调速度时，只有 Activity 页变、Compose 页纹丝不动。见 MeloXSystemScale。
        MeloXSystemScale.install(this)
        registerActivityLifecycleCallbacks(MeloXAppVisibility)
        registerComponentCallbacks(MeloXMemoryCallbacks)
    }
}

object MeloXAppVisibility : Application.ActivityLifecycleCallbacks {
    private const val PREFS_NAME = "melox_visibility"
    private const val KEY_LAST_BACKGROUND_MS = "last_background_ms"

    @Volatile
    private var startedActivities = 0
    @Volatile
    private var foregroundSession = 0L
    private val foregroundState = androidx.compose.runtime.mutableStateOf(false)

    val isForeground: Boolean get() = foregroundState.value
    val foregroundSessionId: Long get() = foregroundSession

    override fun onActivityStarted(activity: Activity) {
        traceActivity(activity, "started")
        if (startedActivities == 0) foregroundSession++
        startedActivities++
        foregroundState.value = startedActivities > 0
    }
    override fun onActivityStopped(activity: Activity) {
        traceActivity(activity, "stopped")
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        foregroundState.value = startedActivities > 0
        if (startedActivities == 0) recordBackgrounded(activity)
    }

    /** Wall-clock ms of the most recent moment the app dropped fully to background.
     *  Persisted to disk so a process-death cold start can still recognise itself as a return. */
    fun lastBackgroundedAtMs(context: Context): Long {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_BACKGROUND_MS, 0L)
        } catch (_: Throwable) { 0L }
    }

    private fun recordBackgrounded(activity: Activity) {
        try {
            activity.application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_BACKGROUND_MS, System.currentTimeMillis())
                .apply()
        } catch (_: Throwable) { }
    }
    private fun traceActivity(activity: Activity, event: String) {
        if (!BuildConfig.DEBUG) return
        android.util.Log.i(
            "MeloXLifecycle",
            "${activity.javaClass.simpleName}@${System.identityHashCode(activity)} " +
                "$event task=${activity.taskId} finishing=${activity.isFinishing} " +
                "changingConfig=${activity.isChangingConfigurations}",
        )
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        traceActivity(activity, "created restored=${savedInstanceState != null}")
    }
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        traceActivity(activity, "saved")
    }
    override fun onActivityDestroyed(activity: Activity) {
        traceActivity(activity, "destroyed")
    }
}

private object MeloXMemoryCallbacks : ComponentCallbacks2 {
    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            ArtworkDynamicPaletteProvider.clearMemoryCache()
        }
    }

    override fun onLowMemory() = ArtworkDynamicPaletteProvider.clearMemoryCache()

    override fun onConfigurationChanged(newConfig: Configuration) = Unit
}
