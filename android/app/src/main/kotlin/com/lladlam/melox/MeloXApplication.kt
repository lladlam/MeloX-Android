package com.lladlam.melox

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import com.lladlam.melox.core.network.MeloXHttpClient
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.audio.MusicQualityRuntime
import com.lladlam.melox.ui.player.ArtworkDynamicPaletteProvider

class MeloXApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MusicQualityRuntime.selected = MusicQualityPreferences.read(this)
        MeloXHttpClient.initialize(this)
        registerActivityLifecycleCallbacks(MeloXAppVisibility)
        registerComponentCallbacks(MeloXMemoryCallbacks)
    }
}

object MeloXAppVisibility : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var startedActivities = 0
    private val foregroundState = androidx.compose.runtime.mutableStateOf(false)

    val isForeground: Boolean get() = foregroundState.value

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
        foregroundState.value = startedActivities > 0
    }
    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        foregroundState.value = startedActivities > 0
    }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
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
