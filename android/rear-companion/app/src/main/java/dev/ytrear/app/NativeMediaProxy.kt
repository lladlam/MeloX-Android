package dev.ytrear.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.graphics.get

/**
 * Presents the selected media player as an active session owned by the allowlisted application id.
 *
 * Xiaomi's native rear MAML player ignores unsupported packages but accepts this app's allowlisted
 * `com.luna.music` identity. The native widget talks to this MediaSession; callbacks are forwarded
 * to the selected app's real controller held by [MediaHub].
 */
object NativeMediaProxy {

    private const val CHANNEL = "proxy_media"
    const val NOTIF_ID = 78
    private const val OPEN_YOUTUBE_MUSIC_REQUEST_CODE = 79
    private const val MAX_ART_EDGE = 320
    private const val COMMAND_RANK_PULSE_DELAY_MS = 45L
    private const val COMMAND_RANK_PULSE_COUNT = 4
    private const val TRACK_CHANGE_RANK_PULSE_DELAY_MS = 80L
    private const val TRACK_CHANGE_RANK_PULSE_COUNT = 40
    // SystemUI rebuilds a card's MediaData — artwork included — only when its notification is
    // posted, so a track change has to repost notification 78 even though the session metadata
    // is already current. Collect the metadata burst that follows a skip into one post and never
    // post faster than the enqueue-rate limiter tolerates.
    private const val NOTIFICATION_REFRESH_DELAY_MS = 350L
    private const val NOTIFICATION_MIN_INTERVAL_MS = 600L
    // SystemUI replays the snapshot it holds for this session on every ranking pulse, and that
    // snapshot is the card posted before the current one. A single repost is therefore painted
    // over by the previous track's artwork for the rest of the transition lease; posting the same
    // card a second time displaces the replayed snapshot, so the fresh artwork survives.
    private const val NOTIFICATION_CONFIRM_POSTS = 1

    private val ACTIONS =
        PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SEEK_TO
    // HyperOS's MediaTimeoutListener ignores position-only state refreshes, but it dispatches a
    // MediaData update when the action mask changes. SET_RATING is not rendered by its standard
    // transport controls, making it a safe one-bit ranking pulse.
    private const val RANKING_PULSE_ACTION = PlaybackState.ACTION_SET_RATING

    private data class Payload(
        val title: String,
        val artist: String,
        val duration: Long,
        val artFingerprint: Long,
        val artWidth: Int,
        val artHeight: Int
    )

    /** The subset of [Payload] that notification 78 actually renders. */
    private data class NotificationPayload(
        val title: String,
        val artist: String,
        val artFingerprint: Long
    )

    private data class PlaybackPayload(
        val playing: Boolean,
        val position: Long,
        val positionAt: Long,
        val speed: Float
    )

    private val main = Handler(Looper.getMainLooper())
    private var session: MediaSession? = null
    private var lastPayload: Payload? = null
    private var lastPlaybackPayload: PlaybackPayload? = null
    private var customWidgetCleared = false
    private var notificationPublished = false
    private var lastNotificationPayload: NotificationPayload? = null
    private var lastNotificationPostAt = 0L
    private var lastArt: Bitmap? = null
    private var refreshContext: Context? = null
    private var confirmPostsLeft = 0
    private var notificationWhen = 0L
    private var rankingPulseEnabled = false
    private var remainingCommandRankPulses = 0
    private var commandRankPulseDelayMs = COMMAND_RANK_PULSE_DELAY_MS

    private val commandRankPulse = object : Runnable {
        override fun run() {
            if (remainingCommandRankPulses <= 0) return
            val reasserted = reassertRanking()
            remainingCommandRankPulses -= 1
            if (reasserted && remainingCommandRankPulses > 0) {
                main.postDelayed(this, commandRankPulseDelayMs)
            } else if (reasserted) {
                normalizeRankingActions()
            }
        }
    }

    @Volatile
    var active = false
        private set

    @Synchronized
    fun update(
        context: Context,
        state: MediaHub.NowPlaying?,
        force: Boolean = false,
        promote: Boolean = force
    ) {
        val app = context.applicationContext
        clearCustomWidgetOnce(app)

        if (state == null) {
            clear(app)
            return
        }

        val playerName = PlayerSelection.selectedLabel(app) ?: "Selected player"
        val title = state.title.ifBlank { playerName }
        val artist = state.artist.ifBlank { "Now playing" }
        val payload = Payload(
            title = title,
            artist = artist,
            duration = state.duration,
            artFingerprint = artworkFingerprint(state.art),
            artWidth = state.art?.width ?: 0,
            artHeight = state.art?.height ?: 0
        )
        val playbackPayload = PlaybackPayload(
            playing = state.playing,
            position = state.position.coerceAtLeast(0L),
            positionAt = state.positionAt,
            speed = if (state.playing) state.speed else 0f
        )
        val displayChanged = payload != lastPayload
        val playbackChanged = playbackPayload != lastPlaybackPayload
        if (!force && !displayChanged && !playbackChanged && !promote && notificationPublished) {
            return
        }

        // Record the accepted snapshot before changing our own MediaSession. HyperOS reports
        // that session change back through MediaSessionManager; marking it first prevents the
        // resulting callback from becoming a notification-update feedback loop.
        lastPayload = payload
        lastPlaybackPayload = playbackPayload

        val proxy = ensureSession(app)
        proxy.setSessionActivity(targetPlayerPendingIntent(app))
        var art: Bitmap? = null
        if (displayChanged || force || promote || !notificationPublished) {
            art = scaleArtwork(state.art)
            lastArt = art
        }
        if (displayChanged || force) {
            proxy.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                    .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, playerName)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, state.duration)
                    .apply {
                        if (art != null) {
                            putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                            putBitmap(MediaMetadata.METADATA_KEY_ART, art)
                        }
                    }
                    .build()
            )
        }
        if (playbackChanged || force) {
            rankingPulseEnabled = false
            proxy.setPlaybackState(buildPlaybackState(playbackPayload))
        }
        proxy.isActive = true
        active = true
        if (displayChanged || force) {
            Probe.log("PROXY SESSION: mirrored '$title' / '$artist'; playing=${state.playing}")
        }
        if (promote || !notificationPublished) {
            publishNotification(app, proxy, payload, art, state.playing)
            Probe.log(
                if (promote) "PROXY NOTIFICATION: promoted current session"
                else "PROXY NOTIFICATION: published initial session"
            )
        } else if (notificationPayload(payload) != lastNotificationPayload) {
            // The rear MAML widget reads this session's metadata directly, but SystemUI's
            // main-display media card keeps the artwork it captured from notification 78.
            // Without this refresh the card shows the previous track's album art forever.
            scheduleNotificationRefresh(app)
        }
    }

    /**
     * Reposts notification 78 once a track's metadata burst has settled.
     *
     * The repost is delayed rather than immediate so the player's staged title/artwork
     * updates cost one post instead of three. [refreshNotification] then repeats the post, since
     * SystemUI keeps replaying the snapshot it holds for this session until a second post has
     * displaced it.
     */
    @Synchronized
    private fun scheduleNotificationRefresh(context: Context) {
        refreshContext = context.applicationContext
        main.removeCallbacks(notificationRefresh)
        main.postDelayed(notificationRefresh, NOTIFICATION_REFRESH_DELAY_MS)
    }

    private val notificationRefresh = Runnable { refreshNotification() }

    @Synchronized
    private fun refreshNotification() {
        val app = refreshContext ?: return
        val proxy = session ?: return
        val payload = lastPayload ?: return
        if (!active || !notificationPublished) return

        val fresh = notificationPayload(payload) != lastNotificationPayload
        if (!fresh && confirmPostsLeft <= 0) {
            refreshContext = null
            return
        }

        // Android sheds a package that enqueues too quickly; notification 78 is the card the
        // rear widget depends on, so never risk losing it to the rate limiter.
        val now = SystemClock.elapsedRealtime()
        val earliest = lastNotificationPostAt + NOTIFICATION_MIN_INTERVAL_MS
        if (now < earliest) {
            main.postDelayed(notificationRefresh, earliest - now)
            return
        }

        val title = payload.title
        publishNotification(app, proxy, payload, lastArt, lastPlaybackPayload?.playing ?: true)
        confirmPostsLeft = if (fresh) NOTIFICATION_CONFIRM_POSTS else confirmPostsLeft - 1
        Probe.log(
            if (fresh) "PROXY NOTIFICATION: artwork refreshed for '$title'"
            else "PROXY NOTIFICATION: artwork post confirmed for '$title'"
        )
        if (confirmPostsLeft > 0) main.postDelayed(notificationRefresh, NOTIFICATION_MIN_INTERVAL_MS)
        else refreshContext = null
    }

    private fun notificationPayload(payload: Payload) = NotificationPayload(
        title = payload.title,
        artist = payload.artist,
        artFingerprint = payload.artFingerprint
    )

    private fun publishNotification(
        context: Context,
        proxy: MediaSession,
        payload: Payload,
        art: Bitmap?,
        playing: Boolean
    ) {
        postMediaNotification(context, proxy, payload.title, payload.artist, art, playing)
        notificationPublished = true
        lastNotificationPayload = notificationPayload(payload)
        lastNotificationPostAt = SystemClock.elapsedRealtime()
    }

    /**
     * Makes the existing proxy MediaData the newest local player without touching its
     * notification. Xiaomi sorts equal active/local/playing cards by MediaData update time, so
     * a fresh PlaybackState is enough to keep `com.luna.music` on top. Reposting notification 78
     * instead causes subscreencenter to animate the rear card out and back in.
     */
    @Synchronized
    fun reassertRanking(): Boolean {
        val proxy = session ?: return false
        val previous = lastPlaybackPayload ?: return false
        if (!active || !notificationPublished) return false

        val now = SystemClock.elapsedRealtime()
        val position = if (previous.playing) {
            val elapsed = (now - previous.positionAt).coerceAtLeast(0L)
            (previous.position + (elapsed * previous.speed).toLong()).coerceAtLeast(0L)
        } else {
            previous.position
        }
        val refreshed = previous.copy(position = position, positionAt = now)
        rankingPulseEnabled = !rankingPulseEnabled
        val actions = if (rankingPulseEnabled) ACTIONS or RANKING_PULSE_ACTION else ACTIONS

        // Store first because SystemUI's controller callback can synchronously feed another
        // session-list update back into this process.
        lastPlaybackPayload = refreshed
        proxy.setPlaybackState(buildPlaybackState(refreshed, actions))
        proxy.isActive = true
        Probe.log("PROXY SESSION: ranking reasserted without notification")
        return true
    }

    /** Restores the advertised transport capabilities after a ranking pulse train. */
    @Synchronized
    fun normalizeRankingActions() {
        if (!rankingPulseEnabled) return
        val proxy = session ?: return
        val playback = lastPlaybackPayload ?: return
        rankingPulseEnabled = false
        proxy.setPlaybackState(buildPlaybackState(playback))
        Probe.log("PROXY SESSION: ranking actions normalized")
    }

    /**
     * Starts the extended lease from the real player's first metadata callback as well as from
     * a proxy skip command. Natural transitions and some player callback orderings can
     * publish metadata before the command-side render path observes the new track.
     */
    @Synchronized
    fun defendTrackTransition() {
        if (!reassertRanking()) return
        scheduleCommandRankPulses(trackChange = true)
        Probe.log(
            "PROXY SESSION: metadata transition rank lease started; " +
                "leaseMs=${commandRankPulseDelayMs * remainingCommandRankPulses}"
        )
    }

    @Synchronized
    fun clear(context: Context) {
        session?.apply {
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(ACTIONS)
                    .setState(PlaybackState.STATE_NONE, 0L, 0f)
                    .build()
            )
            isActive = false
        }
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        if (active) Probe.log("PROXY SESSION: inactive; no selected-player session")
        active = false
        lastPayload = null
        lastPlaybackPayload = null
        notificationPublished = false
        lastNotificationPayload = null
        lastArt = null
        refreshContext = null
        confirmPostsLeft = 0
        notificationWhen = 0L
        rankingPulseEnabled = false
        remainingCommandRankPulses = 0
        main.removeCallbacks(commandRankPulse)
        main.removeCallbacks(notificationRefresh)
    }

    /** Keeps the in-memory notification state honest after a user/SystemUI dismissal. */
    @Synchronized
    fun onNotificationRemoved() {
        notificationPublished = false
        lastNotificationPayload = null
        confirmPostsLeft = 0
        main.removeCallbacks(notificationRefresh)
    }

    private fun ensureSession(context: Context): MediaSession =
        session ?: MediaSession(context, "YTRearProxy").apply {
            setCallback(callback, main)
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setSessionActivity(
                targetPlayerPendingIntent(context)
            )
            session = this
            Probe.log("PROXY SESSION: created as ${context.packageName}")
        }

    private val callback = object : MediaSession.Callback() {
        override fun onPlay() {
            primeRankingForCommand(playingOverride = true)
            report("play", MediaHub.play())
        }

        override fun onPause() {
            primeRankingForCommand()
            report("pause", MediaHub.pause())
        }

        override fun onStop() {
            primeRankingForCommand()
            report("stop/pause", MediaHub.pause())
        }

        override fun onSkipToNext() {
            primeRankingForCommand(trackChange = true)
            report("next", MediaHub.next())
        }

        override fun onSkipToPrevious() {
            primeRankingForCommand(trackChange = true)
            report("previous", MediaHub.previous())
        }

        override fun onSeekTo(pos: Long) {
            primeRankingForCommand()
            report("seekTo($pos)", MediaHub.seekTo(pos))
        }
    }

    /**
     * Starts the rank pulse before the real player can publish its command response.
     *
     * Xiaomi only gives an unsupported top-player removal a 150 ms cancellation delay when
     * top-media callbacks keep arriving less than 200 ms apart. A skip can publish its final
     * player state more than a second after the button press, so keep track changes warm
     * for the full transition. Each 80 ms proxy callback both wins the media sort and ensures any
     * transient unsupported-player removal is delayed long enough for the next callback to
     * cancel it. Play/pause retains the short lease because it has no metadata transition.
     */
    @Synchronized
    private fun primeRankingForCommand(
        playingOverride: Boolean? = null,
        trackChange: Boolean = false
    ) {
        val proxy = session ?: return
        val previous = lastPlaybackPayload ?: return
        if (!active || !notificationPublished) return

        val now = SystemClock.elapsedRealtime()
        val position = if (previous.playing) {
            val elapsed = (now - previous.positionAt).coerceAtLeast(0L)
            (previous.position + (elapsed * previous.speed).toLong()).coerceAtLeast(0L)
        } else {
            previous.position
        }
        val playing = playingOverride ?: previous.playing
        val refreshed = previous.copy(
            playing = playing,
            position = position,
            positionAt = now,
            speed = if (playing) previous.speed.takeIf { it > 0f } ?: 1f else 0f
        )
        rankingPulseEnabled = !rankingPulseEnabled
        val actions = if (rankingPulseEnabled) ACTIONS or RANKING_PULSE_ACTION else ACTIONS
        lastPlaybackPayload = refreshed
        proxy.setPlaybackState(buildPlaybackState(refreshed, actions))
        scheduleCommandRankPulses(trackChange)
        Probe.log(
            "PROXY SESSION: command rank primed; " +
                "playing=$playing; trackChange=$trackChange; " +
                "leaseMs=${commandRankPulseDelayMs * remainingCommandRankPulses}"
        )
    }

    private fun scheduleCommandRankPulses(trackChange: Boolean) {
        main.removeCallbacks(commandRankPulse)
        commandRankPulseDelayMs =
            if (trackChange) TRACK_CHANGE_RANK_PULSE_DELAY_MS else COMMAND_RANK_PULSE_DELAY_MS
        remainingCommandRankPulses =
            if (trackChange) TRACK_CHANGE_RANK_PULSE_COUNT else COMMAND_RANK_PULSE_COUNT
        main.postDelayed(commandRankPulse, commandRankPulseDelayMs)
    }

    private fun report(command: String, sent: Boolean) {
        val target = MediaHub.targetPackage ?: "selected player"
        Probe.log(
            if (sent) "PROXY CONTROL: sent $command to $target"
            else "PROXY CONTROL: ignored $command; no session for $target"
        )
    }

    private fun buildPlaybackState(
        payload: PlaybackPayload,
        actions: Long = ACTIONS
    ): PlaybackState =
        PlaybackState.Builder()
            .setActions(actions)
            .setState(
                if (payload.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                payload.position,
                payload.speed,
                payload.positionAt
            )
            .build()

    private fun postMediaNotification(
        context: Context,
        proxy: MediaSession,
        title: String,
        artist: String,
        art: Bitmap?,
        playing: Boolean
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Proxy media session",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        fun button(which: String) = PendingIntent.getBroadcast(
            context,
            which.hashCode(),
            Intent(context, RearButtonReceiver::class.java)
                .setAction(RearNotification.ACTION_BUTTON)
                .putExtra(RearButtonReceiver.EXTRA_BUTTON, which),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val open = targetPlayerPendingIntent(context)
        val style = Notification.MediaStyle()
            .setMediaSession(proxy.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        // Keep the original post time. Notification.Builder stamps `when` at build time, and a
        // moving timestamp makes SystemUI re-sort the card, which collapses it in the shade.
        if (notificationWhen == 0L) notificationWhen = System.currentTimeMillis()

        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setWhen(notificationWhen)
            .setShowWhen(false)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(art)
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setStyle(style)
            .addAction(
                Notification.Action.Builder(
                    R.drawable.ic_prev,
                    "Previous",
                    button(RearButtonReceiver.BUTTON_PREVIOUS)
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                    if (playing) "Pause" else "Play",
                    button(RearButtonReceiver.BUTTON_PLAY_PAUSE)
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    R.drawable.ic_next,
                    "Next",
                    button(RearButtonReceiver.BUTTON_NEXT)
                ).build()
            )
            .build()

        manager.notify(NOTIF_ID, notification)
    }

    /** Opens the real player from both SystemUI media surfaces backed by this proxy session. */
    private fun targetPlayerPendingIntent(context: Context): PendingIntent {
        val target = PlayerSelection.selectedPackage(context)
        val launchIntent = target?.let(context.packageManager::getLaunchIntentForPackage)
            ?: Intent(context, MainActivity::class.java)
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )
        return PendingIntent.getActivity(
            context,
            OPEN_YOUTUBE_MUSIC_REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun clearCustomWidgetOnce(context: Context) {
        if (customWidgetCleared) return
        RearNotification.cancel(context)
        customWidgetCleared = true
    }

    private fun scaleArtwork(source: Bitmap?): Bitmap? {
        if (source == null || source.isRecycled || source.width <= 0 || source.height <= 0) {
            return null
        }
        if (source.width <= MAX_ART_EDGE && source.height <= MAX_ART_EDGE) return source
        val scale = minOf(
            MAX_ART_EDGE.toFloat() / source.width,
            MAX_ART_EDGE.toFloat() / source.height
        )
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    /**
     * Bitmap.generationId is local to a Bitmap instance and can change when identical album art
     * crosses a binder boundary. Use a small content sample instead so an unchanged track does
     * not continuously repost notification 78 and trigger Android's enqueue-rate limiter.
     */
    private fun artworkFingerprint(source: Bitmap?): Long {
        if (source == null || source.isRecycled || source.width <= 0 || source.height <= 0) {
            return 0L
        }

        var hash = 17L
        hash = hash * 31 + source.width
        hash = hash * 31 + source.height
        val xs = intArrayOf(0, source.width / 4, source.width / 2, source.width * 3 / 4, source.width - 1)
        val ys = intArrayOf(0, source.height / 4, source.height / 2, source.height * 3 / 4, source.height - 1)
        try {
            for (y in ys) {
                for (x in xs) {
                    hash = hash * 31 + (source[x, y].toLong() and 0xffffffffL)
                }
            }
        } catch (_: RuntimeException) {
            // Hardware bitmaps can reject pixel access. Dimensions and byte count still give a
            // stable fallback; title/artist/duration distinguish normal track transitions.
            hash = hash * 31 + source.byteCount
        }
        return hash
    }
}
