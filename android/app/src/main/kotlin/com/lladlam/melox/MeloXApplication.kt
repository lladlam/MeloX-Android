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

    /** 小于这个间隔的「离开再回来」只当抖动（通知中心、输入法），不开结算窗口。 */
    private const val RETURN_GAP_MS = 3_000L

    @Volatile
    private var startedActivities = 0
    @Volatile
    private var foregroundSession = 0L
    private val foregroundState = androidx.compose.runtime.mutableStateOf(false)

    val isForeground: Boolean get() = foregroundState.value
    val foregroundSessionId: Long get() = foregroundSession

    /**
     * 前台状态的 [kotlinx.coroutines.flow.Flow] 视图，供协程「退后台就挂起、回前台再继续」用。
     *
     * ★ 2026-10-26 功耗：给音量轮询之类的常驻循环一个**零帧申请**的等待点
     *   （`first { it }` 直接挂起，不像 `delay(500)` 那样每 500ms 醒一次）。
     *   与 [isForeground] 读的是同一个 `mutableStateOf`，语义天然一致。
     */
    val foregroundStateFlow: kotlinx.coroutines.flow.Flow<Boolean> =
        androidx.compose.runtime.snapshotFlow { foregroundState.value }

    override fun onActivityStarted(activity: Activity) {
        traceActivity(activity, "started")
        if (startedActivities == 0) {
            foregroundSession++
            // ★ 2026-10-22 第四轮：回前台「结算窗口」的唯一写者（见 MeloXUiMotionRuntime）。
            //   用户口径：「退后台一段时间，打开迷你栏动画直接卡炸，第二次不卡」——
            //   坏窗口在**回前台后那 1~1.4s**，不在动画本身。
            //   ⚠ 用持久化的 `lastBackgroundedAtMs` 而不是 `foregroundSessionId`：
            //     用户那个场景里进程**很可能已经被杀掉**，此时回到前台是一次**冷启动**
            //     （session 恒为 1），只看会话号会漏掉恰恰最该盖住的那一次。
            maybeEnterSettleWindow(activity)
        }
        startedActivities++
        foregroundState.value = startedActivities > 0
    }
    override fun onActivityStopped(activity: Activity) {
        traceActivity(activity, "stopped")
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        foregroundState.value = startedActivities > 0
        if (startedActivities == 0) {
            recordBackgrounded(activity)
        }
    }

    /** Wall-clock ms of the most recent moment the app dropped fully to background.
     *  Persisted to disk so a process-death cold start can still recognise itself as a return. */
    fun lastBackgroundedAtMs(context: Context): Long {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_BACKGROUND_MS, 0L)
        } catch (_: Throwable) { 0L }
    }

    /**
     * 这次 `onActivityStarted` 是不是「退后台归来」，是则开运行期结算窗口。
     *
     * 见 [com.lladlam.melox.ui.MeloXUiMotionRuntime] 末尾那段：窗口内流光网格与管线预热
     * 主动退到最低档，把回前台后那 1~1.4s 的帧让给用户真正要看的那一下。
     */
    private fun maybeEnterSettleWindow(activity: Activity) {
        val lastBackgrounded = lastBackgroundedAtMs(activity)
        if (lastBackgrounded <= 0L) return
        if (System.currentTimeMillis() - lastBackgrounded < RETURN_GAP_MS) return
        com.lladlam.melox.ui.MeloXUiMotionRuntime.markForegroundReturn()
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
