package com.lladlam.melox.core.plugin

import android.app.Instrumentation
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lladlam.melox.core.music.model.SourceId
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.playback.MeloXPlaybackService
import com.lladlam.melox.playback.PlaybackTrackIdentity
import com.lladlam.melox.playback.ProviderPlaybackCommands
import java.util.concurrent.TimeUnit

/** Opt-in test of the NORMAL app service/controller/UI-state path, not a standalone ExoPlayer.
 * Requires the signed rc-app fixture imported/enabled by the user-flow test first.
 * Replaces the test installation's queue. Run only on a disposable RC installation.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object PluginRcAppServiceChecks {
    fun run(test: Instrumentation, evidence: MutableList<String>) {
        val context = test.targetContext
        val source = SourceId.parse("plugin:org.melox.rc-app")!!
        MeloXPlugins.awaitRestored()
        val provider = MeloXMusicProviders.create(context)[source] as? PluginMusicProvider ?: error("Import and enable signed rc-app fixture first")
        val tracks = kotlinx.coroutines.runBlocking { provider.searchSongs("RC", 1, 2).items }
        check(tracks.size == 2 && tracks.all { it.durationMs == 120_000L })
        test.startActivitySync(Intent().setComponent(ComponentName(context.packageName, "com.lladlam.melox.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        lateinit var future: com.google.common.util.concurrent.ListenableFuture<MediaController>
        test.runOnMainSync { future = MediaController.Builder(context, SessionToken(context, ComponentName(context, MeloXPlaybackService::class.java))).buildAsync() }
        val controller = future.get(20, TimeUnit.SECONDS)
        val ui = com.lladlam.melox.ui.player.MeloXPlaybackUiState(context)
        fun await(label: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
            val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                var matched = false
                var failure: Throwable? = null
                test.runOnMainSync { failure = controller.playerError; matched = condition() }
                failure?.let { throw it }
                if (matched) return
                Thread.sleep(100)
            }
            error("normal service condition timed out: $label")
        }
        try {
            test.runOnMainSync {
                ui.bind(controller)
                controller.setMediaItems(tracks.map { ProviderPlaybackCommands.mediaItemFor(it) })
                controller.prepare(); controller.play()
            }
            await("first track playing") { controller.isPlaying && controller.currentPosition > 500 }
            test.runOnMainSync {
                ui.refresh(rebuildQueue = true) // Regression: dynamic SourceId previously crashed here.
                check(ui.title == tracks.first().title && ui.queue.size == 2)
                check(PlaybackTrackIdentity.fromMediaItem(controller.currentMediaItem)?.sourceId == source)
                controller.pause()
            }
            await("pause") { !controller.isPlaying && !controller.playWhenReady }
            test.runOnMainSync { controller.seekTo(30_000); controller.play() }
            await("seek") { controller.isPlaying && controller.currentPosition in 29_500..40_000 }
            test.runOnMainSync { controller.seekToNextMediaItem() }
            await("next track") { controller.isPlaying && PlaybackTrackIdentity.fromMediaItem(controller.currentMediaItem) == tracks[1].id }
            val lyrics = kotlinx.coroutines.runBlocking { provider.lyrics(tracks[1]) }
            check(lyrics.lines.isNotEmpty())
            val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            check(notifications.activeNotifications.any { it.notification.category == Notification.CATEGORY_TRANSPORT }) { "media notification missing" }
            evidence += "PASS normal MeloXPlaybackService/MediaController, dynamic playback UI state and two-item queue, pause/seek/next, lyrics Provider and media notification"
            shell(test, "input keyevent KEYCODE_HOME")
            Thread.sleep(1500)
            await("background playback", 5000) { controller.isPlaying }
            shell(test, "input keyevent KEYCODE_SLEEP")
            Thread.sleep(1500)
            check(!(context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive)
            await("screen-off playback", 5000) { controller.isPlaying }
            val services = shell(test, "dumpsys activity services ${context.packageName}")
            check(services.contains("MeloXPlaybackService") && services.contains("isForeground=true")) { "foreground service not observed" }
            evidence += "PASS normal service remains playing in background/screen off with foreground-service flag"
        } finally {
            shell(test, "input keyevent KEYCODE_WAKEUP")
            test.runOnMainSync { controller.pause(); ui.unbind(releaseController = false); controller.release() }
        }
    }
    private fun shell(test: Instrumentation, command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(test.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
}
