package dev.ytrear.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.lang.ref.WeakReference

/** Mirrors the selected player into the allowlisted proxy session used by Xiaomi's native widget. */
object RearController {

    private const val UPDATE_DEBOUNCE_MS = 150L
    // SystemUI ranks otherwise-equivalent local media cards by their most recent MediaData
    // update. Reassert the proxy session just after the selected player's notification instead of
    // reposting notification 78; reposting makes the rear host remove and re-add the card.
    private const val COMPETING_MEDIA_REASSERT_DELAY_MS = 40L
    private const val RANK_REASSERT_FOLLOWUP_MS = 120L
    private const val RANK_REASSERT_FOLLOWUP_COUNT = 6
    private const val TRACK_TRANSITION_GRACE_MS = 2_500L
    private const val INFERRED_TRANSITION_WINDOW_MS = 3_000L

    private data class TrackKey(
        val title: String,
        val artist: String,
        val duration: Long
    )

    private val main = Handler(Looper.getMainLooper())
    private var pendingContext: WeakReference<Context>? = null
    private var pendingState: MediaHub.NowPlaying? = null
    private var pendingForce = false
    private var latestContext: WeakReference<Context>? = null

    private var reassertContext: WeakReference<Context>? = null
    private var lastCompetingNotificationAt = 0L
    private var competingNotificationCount = 0
    private var remainingRankFollowups = 0

    private var lastTrackKey: TrackKey? = null
    private var lastConfirmedPlayingAt = 0L
    private var preservePlayingUntil = 0L
    private var transitionOriginTrack: TrackKey? = null

    private val flush: Runnable = Runnable {
        val context = pendingContext?.get()
        val state = pendingState
        val force = pendingForce
        pendingContext = null
        pendingState = null
        pendingForce = false
        if (context != null) {
            NativeMediaProxy.update(
                context = context,
                state = state,
                force = force,
                promote = force
            )
        }
    }

    private val finishTrackTransition: Runnable = Runnable {
        clearTrackTransition()
        val context = latestContext?.get()
        if (context != null) render(context, MediaHub.state)
        schedulePendingReassertion()
    }

    private val reassertProxy: Runnable = Runnable {
        val context = reassertContext?.get()
        val state = MediaHub.state
        if (context == null || state == null) {
            clearPendingReassertion()
            return@Runnable
        }

        val now = SystemClock.elapsedRealtime()
        val quietUntil = lastCompetingNotificationAt + COMPETING_MEDIA_REASSERT_DELAY_MS
        if (now < quietUntil) {
            main.postDelayed(reassertProxy, quietUntil - now)
            return@Runnable
        }
        if (!state.playing && now < preservePlayingUntil) {
            main.postDelayed(reassertProxy, preservePlayingUntil - now)
            return@Runnable
        }

        val updateCount = competingNotificationCount
        clearPendingReassertion()
        val sessionOnly = NativeMediaProxy.reassertRanking()
        if (!sessionOnly) {
            // The proxy notification may have been dismissed or the process may be rebuilding.
            // In that case update() publishes the missing initial card once.
            NativeMediaProxy.update(context, state)
        } else {
            // SystemUI parses media notifications and session callbacks on different queues.
            // Hold a short session-only ranking lease so late queue deliveries cannot leave
            // the selected player on top long enough for the rear card's exit animation to render.
            main.removeCallbacks(reassertProxyFollowup)
            remainingRankFollowups = RANK_REASSERT_FOLLOWUP_COUNT
            main.postDelayed(reassertProxyFollowup, RANK_REASSERT_FOLLOWUP_MS)
        }
        Probe.log(
            "CONTROLLER: proxy rank reasserted; " +
                "targetUpdates=$updateCount; playing=${state.playing}; sessionOnly=$sessionOnly"
        )
    }

    private val reassertProxyFollowup: Runnable = Runnable {
        if (remainingRankFollowups <= 0) return@Runnable
        val reasserted = NativeMediaProxy.reassertRanking()
        remainingRankFollowups -= 1
        if (reasserted && remainingRankFollowups == 0) {
            NativeMediaProxy.normalizeRankingActions()
            Probe.log("CONTROLLER: proxy ranking lease complete; sessionOnly=true")
        }
        if (reasserted && remainingRankFollowups > 0) {
            main.postDelayed(reassertProxyFollowup, RANK_REASSERT_FOLLOWUP_MS)
        }
    }

    fun render(
        context: Context,
        state: MediaHub.NowPlaying?,
        force: Boolean = false
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { render(context.applicationContext, state, force) }
            return
        }

        val app = context.applicationContext
        latestContext = WeakReference(app)
        val effectiveState = stabilizeTrackTransition(state)

        if (state == null) {
            clearPendingReassertion()
            clearTrackTransition()
            lastTrackKey = null
            lastConfirmedPlayingAt = 0L
        } else if (state.playing && reassertContext != null) {
            // A skip has reached its stable playing state. Refresh the proxy's rank as soon as
            // the latest selected-player notification has entered SystemUI.
            schedulePendingReassertion()
        }

        pendingContext = WeakReference(app)
        pendingState = effectiveState
        pendingForce = pendingForce || force
        main.removeCallbacks(flush)
        main.postDelayed(flush, if (pendingForce) 0L else UPDATE_DEBOUNCE_MS)
    }

    /** Called for an actual competing MediaStyle notification from the selected player. */
    fun onTargetMediaNotification(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { onTargetMediaNotification(context.applicationContext) }
            return
        }

        reassertContext = WeakReference(context.applicationContext)
        lastCompetingNotificationAt = SystemClock.elapsedRealtime()
        competingNotificationCount += 1

        // Defend on the leading edge as well as after the burst. Waiting only for a quiet window
        // lets the rear host begin its exit animation before we reclaim top-media rank.
        val sessionOnly = NativeMediaProxy.reassertRanking()
        if (!sessionOnly) {
            MediaHub.state?.let { NativeMediaProxy.update(context, it) }
        }
        schedulePendingReassertion()
    }

    /** Keeps a short false/paused state during next/previous from churning the native card. */
    fun expectTrackTransition() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { expectTrackTransition() }
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (MediaHub.state?.playing == true || now - lastConfirmedPlayingAt <= 500L) {
            armTrackTransition(now)
        }
    }

    /** A deliberate pause must not be mistaken for a skip's transient paused state. */
    fun cancelTrackTransition() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { cancelTrackTransition() }
            return
        }
        clearTrackTransition()
        lastConfirmedPlayingAt = 0L
    }

    private fun stabilizeTrackTransition(state: MediaHub.NowPlaying?): MediaHub.NowPlaying? {
        if (state == null) return null
        val now = SystemClock.elapsedRealtime()
        val trackKey = TrackKey(state.title, state.artist, state.duration)
        val trackChanged = lastTrackKey != null && trackKey != lastTrackKey

        if (state.playing) {
            lastConfirmedPlayingAt = now
            if (
                now >= preservePlayingUntil ||
                (preservePlayingUntil > 0L && trackKey != transitionOriginTrack)
            ) {
                clearTrackTransition()
            }
        } else if (
            trackChanged &&
            now - lastConfirmedPlayingAt <= INFERRED_TRANSITION_WINDOW_MS
        ) {
            armTrackTransition(now)
        }
        lastTrackKey = trackKey

        if (state.playing || now >= preservePlayingUntil) return state
        return state.copy(
            playing = true,
            positionAt = now,
            speed = state.speed.takeIf { it > 0f } ?: 1f
        )
    }

    private fun armTrackTransition(now: Long) {
        if (preservePlayingUntil <= now) transitionOriginTrack = lastTrackKey
        preservePlayingUntil = now + TRACK_TRANSITION_GRACE_MS
        main.removeCallbacks(finishTrackTransition)
        main.postDelayed(finishTrackTransition, TRACK_TRANSITION_GRACE_MS)
    }

    private fun clearTrackTransition() {
        preservePlayingUntil = 0L
        transitionOriginTrack = null
        main.removeCallbacks(finishTrackTransition)
    }

    private fun schedulePendingReassertion() {
        if (reassertContext == null) return
        val now = SystemClock.elapsedRealtime()
        var dueAt = maxOf(now, lastCompetingNotificationAt + COMPETING_MEDIA_REASSERT_DELAY_MS)
        if (MediaHub.state?.playing != true && now < preservePlayingUntil) {
            dueAt = maxOf(dueAt, preservePlayingUntil)
        }
        main.removeCallbacks(reassertProxyFollowup)
        remainingRankFollowups = 0
        main.removeCallbacks(reassertProxy)
        main.postDelayed(reassertProxy, (dueAt - now).coerceAtLeast(0L))
    }

    private fun clearPendingReassertion() {
        main.removeCallbacks(reassertProxy)
        main.removeCallbacks(reassertProxyFollowup)
        remainingRankFollowups = 0
        reassertContext = null
        competingNotificationCount = 0
    }
}
