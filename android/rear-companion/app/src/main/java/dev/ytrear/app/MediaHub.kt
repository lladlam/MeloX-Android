package dev.ytrear.app

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Watches the system's active media sessions and exposes the user-selected app. Reading sessions
 * requires notification-listener access, which is why [MediaNotificationListener] exists — its
 * ComponentName is the key we hand to MediaSessionManager.
 */
object MediaHub {

    data class NowPlaying(
        val pkg: String,
        val title: String,
        val artist: String,
        val art: Bitmap?,
        val playing: Boolean,
        val position: Long,
        val duration: Long,
        val positionAt: Long,
        val speed: Float,
        val actions: Long
    ) {
        /** PlaybackState only reports position at a point in time; extrapolate from there. */
        fun currentPosition(): Long {
            if (position < 0) return 0
            if (!playing) return position
            val drift = ((SystemClock.elapsedRealtime() - positionAt) * speed).toLong()
            val now = position + drift
            return if (duration > 0) now.coerceIn(0, duration) else now.coerceAtLeast(0)
        }

        fun can(action: Long) = actions and action != 0L
    }

    fun interface Listener {
        fun onChanged(state: NowPlaying?)
    }

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Listener>()

    private var manager: MediaSessionManager? = null
    private var component: ComponentName? = null
    private var controller: MediaController? = null

    @Volatile
    var targetPackage: String? = null
        private set

    @Volatile
    var state: NowPlaying? = null
        private set

    /** True once we've successfully read the session list at least once. */
    @Volatile
    var connected = false
        private set

    fun addListener(l: Listener) {
        listeners.add(l)
        l.onChanged(state)
    }

    fun removeListener(l: Listener) = listeners.remove(l)

    fun attach(context: Context): Boolean {
        targetPackage = PlayerSelection.selectedPackage(context)
        val m = manager ?: context.applicationContext
            .getSystemService(MediaSessionManager::class.java) ?: return false
        manager = m
        val cn = ComponentName(context.applicationContext, MediaNotificationListener::class.java)
        component = cn
        return try {
            m.removeOnActiveSessionsChangedListener(sessionsChanged)
            m.addOnActiveSessionsChangedListener(sessionsChanged, cn, main)
            bind(pick(m.getActiveSessions(cn)))
            connected = true
            true
        } catch (e: SecurityException) {
            // Notification access not granted (yet).
            connected = false
            false
        }
    }

    fun detach() {
        manager?.removeOnActiveSessionsChangedListener(sessionsChanged)
        bind(null)
        connected = false
    }

    /** Drops the previous controller and immediately resolves the newly persisted selection. */
    fun targetChanged(context: Context): Boolean {
        targetPackage = PlayerSelection.selectedPackage(context)
        bind(null)
        return attach(context.applicationContext)
    }

    private val sessionsChanged =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> bind(pick(list.orEmpty())) }

    private fun pick(list: List<MediaController>): MediaController? {
        val target = targetPackage ?: return null
        if (list.isEmpty()) return null
        fun playing(c: MediaController) = c.playbackState?.state == PlaybackState.STATE_PLAYING
        return list.firstOrNull { it.packageName == target && playing(it) }
            ?: list.firstOrNull { it.packageName == target }
    }

    private fun bind(next: MediaController?) {
        val current = controller
        if (current?.sessionToken == next?.sessionToken) {
            publish(next?.let(::snapshot))
            return
        }
        current?.unregisterCallback(callback)
        controller = next
        next?.registerCallback(callback, main)
        publish(next?.let(::snapshot))
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (metadata != null) NativeMediaProxy.defendTrackTransition()
            republish()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) = republish()
        override fun onSessionDestroyed() {
            controller?.unregisterCallback(this)
            controller = null
            publish(null)
            main.post { refreshSessions() }
        }
    }

    private fun refreshSessions() {
        val m = manager ?: return
        val cn = component ?: return
        try {
            bind(pick(m.getActiveSessions(cn)))
            connected = true
        } catch (_: SecurityException) {
            bind(null)
            connected = false
        }
    }

    private fun republish() = publish(controller?.let(::snapshot))

    private fun publish(next: NowPlaying?) {
        state = next
        main.post { listeners.forEach { it.onChanged(next) } }
    }

    private fun snapshot(c: MediaController): NowPlaying {
        val md = c.metadata
        val ps = c.playbackState
        return NowPlaying(
            pkg = c.packageName,
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty(),
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty(),
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            playing = ps?.state == PlaybackState.STATE_PLAYING,
            position = ps?.position ?: 0L,
            duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            positionAt = ps?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            actions = ps?.actions ?: 0L
        )
    }

    // --- transport ---------------------------------------------------------

    fun togglePlayPause(): Boolean {
        val c = controller ?: return false
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) {
            RearController.cancelTrackTransition()
            c.transportControls.pause()
        } else {
            c.transportControls.play()
        }
        return true
    }

    fun play(): Boolean = controller?.let {
        it.transportControls.play()
        true
    } ?: false

    fun pause(): Boolean = controller?.let {
        RearController.cancelTrackTransition()
        it.transportControls.pause()
        true
    } ?: false

    fun next(): Boolean = controller?.let {
        RearController.expectTrackTransition()
        it.transportControls.skipToNext()
        true
    } ?: false

    fun previous(): Boolean = controller?.let {
        RearController.expectTrackTransition()
        it.transportControls.skipToPrevious()
        true
    } ?: false

    fun seekTo(ms: Long): Boolean = controller?.let {
        it.transportControls.seekTo(ms)
        true
    } ?: false
}
