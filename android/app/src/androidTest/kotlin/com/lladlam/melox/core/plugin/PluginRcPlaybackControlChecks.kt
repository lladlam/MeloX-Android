package com.lladlam.melox.core.plugin

import android.app.Instrumentation
import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lladlam.melox.core.music.model.SourceId
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.playback.MeloXPlaybackService
import com.lladlam.melox.playback.PlaybackTrackIdentity
import com.lladlam.melox.playback.ProviderPlaybackCommands
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import java.util.concurrent.TimeUnit

/**
 * MediaController checks for the system "previous" command.
 *
 * These exercise the real [MeloXPlaybackService] session callback, not the UI
 * state helper: notification, Bluetooth and system taskbar previous all arrive
 * as `COMMAND_SEEK_TO_PREVIOUS` / `COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM`.
 * Run only on a disposable test installation with the signed rc-app fixture
 * imported and enabled.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object PluginRcPlaybackControlChecks {
    fun run(test: Instrumentation, evidence: MutableList<String>) {
        val context = test.targetContext
        val source = SourceId.parse("plugin:org.melox.rc-app")
            ?: error("rc-app source id unavailable")
        MeloXPlugins.awaitRestored()
        val provider = MeloXMusicProviders.create(context)[source] as? PluginMusicProvider
            ?: error("Import and enable signed rc-app fixture first")
        val tracks = kotlinx.coroutines.runBlocking { provider.searchSongs("RC", 1, 2).items }
        check(tracks.size == 2) { "expected two rc-app tracks" }

        lateinit var future: com.google.common.util.concurrent.ListenableFuture<MediaController>
        test.startActivitySync(
            android.content.Intent()
                .setComponent(ComponentName(context.packageName, "com.lladlam.melox.MainActivity"))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        Thread.sleep(2_000)
        test.runOnMainSync {
            future = MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, MeloXPlaybackService::class.java)),
            ).buildAsync()
        }
        val controller = future.get(20, TimeUnit.SECONDS)

        fun <T> onMain(block: () -> T): T {
            var result: T? = null
            test.runOnMainSync { result = block() }
            @Suppress("UNCHECKED_CAST")
            return result as T
        }

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
            error("media controller condition timed out: $label")
        }

        fun currentId() = onMain { PlaybackTrackIdentity.fromMediaItem(controller.currentMediaItem) }
        fun position() = onMain { controller.currentPosition }

        val previousValue = MeloXSettingsRuntime.previousRestartsAfterFiveSeconds
        try {
            test.runOnMainSync {
                controller.setMediaItems(tracks.map { ProviderPlaybackCommands.mediaItemFor(it) })
                controller.prepare()
                controller.play()
            }
            await("first track playing") { controller.isPlaying }

            // Setting ON, position >= 5s: previous must restart the same item.
            test.runOnMainSync {
                MeloXSettingsRuntime.previousRestartsAfterFiveSeconds = true
                controller.seekTo(12_000)
            }
            await("seeked past five seconds") { controller.currentPosition >= 11_000 }
            val playingId = currentId()
            test.runOnMainSync { controller.seekToPrevious() }
            await("restart current item") { controller.currentPosition < 2_000 }
            check(currentId() == playingId) { "previous past five seconds changed track" }

            // Setting ON, position < 5s: previous must move to the previous item.
            test.runOnMainSync {
                controller.seekToNextMediaItem()
                controller.seekTo(1_000)
            }
            await("second item") { PlaybackTrackIdentity.fromMediaItem(controller.currentMediaItem) == tracks[1].id }
            test.runOnMainSync { controller.seekToPrevious() }
            await("previous item under five seconds") {
                PlaybackTrackIdentity.fromMediaItem(controller.currentMediaItem) == tracks[0].id &&
                    controller.currentPosition < 2_000
            }
            check(currentId() == tracks[0].id && position() < 2_000) {
                "previous under five seconds did not move to the previous item"
            }

            // Setting OFF, position >= 5s: previous must still change item.
            test.runOnMainSync {
                MeloXSettingsRuntime.previousRestartsAfterFiveSeconds = false
                controller.seekToNextMediaItem()
            }
            await("second item again") { PlaybackTrackIdentity.fromMediaItem(controller.currentMediaItem) == tracks[1].id }
            test.runOnMainSync {
                controller.seekTo(12_000)
                controller.seekToPrevious()
            }
            await("previous item with preference off") {
                PlaybackTrackIdentity.fromMediaItem(controller.currentMediaItem) == tracks[0].id &&
                    controller.currentPosition < 2_000
            }
            check(currentId() == tracks[0].id) { "previous with preference off did not change track" }
            evidence += "PASS MediaSession previous obeys 5s restart preference (restart past 5s, previous item under 5s and with preference off)"
        } finally {
            MeloXSettingsRuntime.previousRestartsAfterFiveSeconds = previousValue
            test.runOnMainSync {
                controller.pause()
                controller.release()
            }
        }
    }
}
