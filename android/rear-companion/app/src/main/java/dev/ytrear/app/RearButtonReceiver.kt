package dev.ytrear.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives the immutable PendingIntents embedded in the rear RemoteViews. */
class RearButtonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RearNotification.ACTION_BUTTON) return

        val button = intent.getStringExtra(EXTRA_BUTTON) ?: return
        if (!MediaHub.attach(context.applicationContext)) {
            Probe.log("REAR BUTTON: $button ignored; notification access is missing")
            return
        }

        val sent = when (button) {
            BUTTON_PREVIOUS -> MediaHub.previous()
            BUTTON_PLAY_PAUSE -> MediaHub.togglePlayPause()
            BUTTON_NEXT -> MediaHub.next()
            else -> false
        }

        Probe.log(
            if (sent) "REAR BUTTON: sent $button to ${MediaHub.targetPackage}"
            else "REAR BUTTON: $button ignored; no selected-player session"
        )
    }

    companion object {
        const val EXTRA_BUTTON = "which"
        const val BUTTON_PREVIOUS = "prev"
        const val BUTTON_PLAY_PAUSE = "playpause"
        const val BUTTON_NEXT = "next"
    }
}
