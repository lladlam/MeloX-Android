package com.lladlam.melox.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.network.MeloXListenTogetherCommandType
import com.lladlam.melox.core.network.MeloXListenTogetherRoom
import com.lladlam.melox.core.network.MeloXListenTogetherSnapshot
import com.lladlam.melox.core.network.NeteaseListenTogetherTransport
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.core.network.NeteaseSearchClient
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Process-lifetime Listen Together coordinator.
 *
 * The coordinator owns a MediaController rather than borrowing a Compose screen's
 * player reference, so a Together room keeps syncing after the actions sheet or
 * Activity is dismissed while the MediaSessionService remains alive.
 */
object MeloXListenTogetherCoordinator {
    enum class Phase { Idle, Connected, Reconnecting }

    data class State(
        val phase: Phase = Phase.Idle,
        val room: MeloXListenTogetherRoom? = null,
        val consecutiveFailures: Int = 0,
        val lastError: String? = null,
    )

    @Volatile
    private var runtime: Runtime? = null

    fun ensureStarted(context: Context) {
        if (runtime != null) return
        synchronized(this) {
            if (runtime == null) runtime = Runtime(context.applicationContext)
        }
    }

    fun state(context: Context): StateFlow<State> { ensureStarted(context); return runtime!!.state }
    fun adoptRoom(context: Context, room: MeloXListenTogetherRoom) { ensureStarted(context); runtime!!.adoptRoom(room) }
    fun clearRoom(context: Context) { ensureStarted(context); runtime!!.clearRoom() }

    private class Runtime(private val appContext: Context) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val mainHandler = Handler(Looper.getMainLooper())
        private val mainExecutor = Executor { command -> mainHandler.post(command) }
        private val cookieProvider = { NeteaseSessionStore.readCookie(appContext) }
        private val ops = NeteaseMusicOperationsClient(cookieProvider = cookieProvider)
        private val transport = NeteaseListenTogetherTransport(cookieProvider = cookieProvider)
        private val library = NeteaseLibraryClient(cookieProvider = cookieProvider)
        private val account = NeteaseSearchClient(cookieProvider = cookieProvider)
        private val commandSequence = AtomicInteger(1)
        private val commandMutex = Mutex()
        private val playlistMutex = Mutex()
        private val mutableState = MutableStateFlow(State())
        val state: StateFlow<State> = mutableState.asStateFlow()

        private var controller: MediaController? = null
        private var room: MeloXListenTogetherRoom? = null
        private var cachedUserId: Long? = null
        private var lastCookie: String? = null
        private var lastKnownSongId: Long? = null
        private var lastRemoteQueueSignature: String? = null
        private var lastRemoteCommandSignature: String? = null
        private var playlistVersion = 1
        private var suppressLocalUntilRealtime = 0L
        private var queueReportJob: Job? = null
        private var failures = 0
        private var heartbeatTick = 0
        private var statusTick = 0
        private var firstSyncForRoom = true
        private var controllerConnectionPending = false

        private val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (localEventsSuppressed()) return
                val active = controller ?: return
                // Media3 temporarily reports isPlaying=false while it is buffering
                // with playWhenReady=true. Treating that transition as PAUSE makes
                // every network stall pause the other listener as well.
                if (!isPlaying && active.playWhenReady && active.playbackState == Player.STATE_BUFFERING) return
                val songId = active.currentMediaItem?.mediaId?.toLongOrNull() ?: return
                reportCommand(
                    if (isPlaying) MeloXListenTogetherCommandType.Play else MeloXListenTogetherCommandType.Pause,
                    targetSongId = songId,
                    formerSongId = lastKnownSongId ?: songId,
                )
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val target = mediaItem?.mediaId?.toLongOrNull()
                val former = lastKnownSongId
                lastKnownSongId = target
                if (localEventsSuppressed() || target == null) return
                reportCommand(MeloXListenTogetherCommandType.GoTo, target, former)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason != Player.DISCONTINUITY_REASON_SEEK || localEventsSuppressed()) return
                val active = controller ?: return
                val target = active.currentMediaItem?.mediaId?.toLongOrNull() ?: return
                reportCommand(MeloXListenTogetherCommandType.Progress, target, lastKnownSongId ?: target)
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (localEventsSuppressed()) return
                scheduleQueueReport()
            }
        }

        init { scope.launch { monitorLoop() } }
        fun adoptRoom(latest: MeloXListenTogetherRoom) {
            room = latest
            failures = 0
            firstSyncForRoom = true
            lastRemoteQueueSignature = null
            lastRemoteCommandSignature = null
            playlistVersion = 1
            heartbeatTick = HEARTBEAT_EVERY_TICKS
            statusTick = 0
            mutableState.value = State(Phase.Connected, latest)
            connectController()
        }
        fun clearRoom() = resetRoom()

        private fun connectController() {
            if (controller != null || controllerConnectionPending || room == null) return
            controllerConnectionPending = true
            val token = SessionToken(appContext, ComponentName(appContext, MeloXPlaybackService::class.java))
            val future = MediaController.Builder(appContext, token).buildAsync()
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { connected ->
                            controllerConnectionPending = false
                            if (room == null) {
                                connected.release()
                                return@onSuccess
                            }
                            controller?.removeListener(listener)
                            controller = connected
                            connected.addListener(listener)
                            lastKnownSongId = connected.currentMediaItem?.mediaId?.toLongOrNull()
                        }
                        .onFailure { error ->
                            controllerConnectionPending = false
                            Log.w(TAG, "Together MediaController unavailable", error)
                        }
                },
                mainExecutor,
            )
        }

        private suspend fun monitorLoop() {
            while (scope.isActive) {
                val cookie = cookieProvider()
                if (cookie != lastCookie) {
                    // A MUSIC_U/account switch invalidates both the cached uid and
                    // the room identity. Re-discover the room under the new account.
                    lastCookie = cookie
                    cachedUserId = null
                    commandSequence.set(1)
                    resetRoom()
                }

                if (!NeteaseSessionStore.containsMusicU(cookie)) {
                    resetRoom()
                    delay(IDLE_POLL_MS)
                    continue
                }

                statusTick++
                if (room == null || statusTick >= STATUS_EVERY_TICKS) {
                    statusTick = 0
                    refreshRoomStatus()
                }

                if (room != null && controller == null && !controllerConnectionPending) connectController()
                val activeRoom = room
                if (activeRoom == null) {
                    // Do not hit room-status once per second when the user is not
                    // actually in a Together room.
                    delay(IDLE_POLL_MS)
                    continue
                }

                runCatching { transport.playback(activeRoom.id) }
                    .onSuccess { snapshot ->
                        failures = 0
                        mutableState.value = State(Phase.Connected, activeRoom)
                        synchronizeRemote(activeRoom, snapshot)
                    }
                    .onFailure(::recordFailure)

                heartbeatTick++
                if (heartbeatTick >= HEARTBEAT_EVERY_TICKS) {
                    heartbeatTick = 0
                    sendHeartbeat(activeRoom)
                }
                delay(SYNC_INTERVAL_MS)
            }
        }

        private suspend fun refreshRoomStatus() {
            runCatching { ops.listenTogetherRoomStatus() }
                .onSuccess { latest ->
                    if (latest == null) {
                        resetRoom()
                        return@onSuccess
                    }
                    val changedRoom = room?.id != latest.id
                    room = latest
                    if (changedRoom) {
                        firstSyncForRoom = true
                        lastRemoteQueueSignature = null
                        lastRemoteCommandSignature = null
                        playlistVersion = 1
                        heartbeatTick = HEARTBEAT_EVERY_TICKS
                    }
                    connectController()
                    mutableState.value = State(
                        phase = if (failures > 0) Phase.Reconnecting else Phase.Connected,
                        room = latest,
                        consecutiveFailures = failures,
                    )
                }
                .onFailure { if (room != null) recordFailure(it) }
        }

        private suspend fun synchronizeRemote(
            activeRoom: MeloXListenTogetherRoom,
            snapshot: MeloXListenTogetherSnapshot,
        ) {
            val selfId = currentUserId()
            val isCreator = selfId != null && activeRoom.creatorId == selfId.toString()
            val active = controller ?: return

            if (firstSyncForRoom) {
                firstSyncForRoom = false
                if (isCreator && active.mediaItemCount > 0) {
                    reportQueueNow(activeRoom)
                } else {
                    applyRemoteSnapshot(snapshot, force = true)
                }
                return
            }

            val queueSignature = buildString {
                append(snapshot.playMode.orEmpty()).append('|')
                append(snapshot.displaySongIds.joinToString(",")).append('|')
                append(snapshot.randomSongIds.joinToString(","))
            }
            if (queueSignature != lastRemoteQueueSignature) {
                lastRemoteQueueSignature = queueSignature
                applyRemoteQueue(snapshot)
            }

            val commandSignature = listOf(
                snapshot.serverSequence,
                snapshot.clientSequence,
                snapshot.commandUserId.orEmpty(),
                snapshot.targetSongId ?: -1L,
                snapshot.progressMs,
                snapshot.isPlaying,
            ).joinToString(":")
            val commandFromSelf = selfId != null && snapshot.commandUserId == selfId.toString()
            if (!commandFromSelf && commandSignature != lastRemoteCommandSignature) {
                lastRemoteCommandSignature = commandSignature
                applyRemoteCommand(snapshot)
            }
        }

        private suspend fun applyRemoteSnapshot(snapshot: MeloXListenTogetherSnapshot, force: Boolean) {
            applyRemoteQueue(snapshot)
            if (force || snapshot.commandUserId != currentUserId()?.toString()) applyRemoteCommand(snapshot)
        }

        private suspend fun applyRemoteQueue(snapshot: MeloXListenTogetherSnapshot) {
            val active = controller ?: return
            val remoteIds = snapshot.playbackSongIds
            if (remoteIds.isEmpty()) return
            val localIds = mediaIds(active)
            applyRemoteMode(snapshot, active)
            if (remoteIds == localIds) return

            val songs = withContext(Dispatchers.IO) { library.songDetailsBlocking(remoteIds) }
            val byId = songs.associateBy { it.id }
            val ordered = remoteIds.mapNotNull(byId::get)
            if (ordered.isEmpty()) return
            val target = snapshot.targetSongId?.takeIf { id -> ordered.any { it.id == id } } ?: ordered.first().id
            val targetIndex = ordered.indexOfFirst { it.id == target }.coerceAtLeast(0)
            val items = ordered.mapIndexed { index, song ->
                PlaybackCommands.mediaItemFor(
                    song = song,
                    queueOrigin = PlaybackCommands.QUEUE_ORIGIN_BASE,
                    originalIndex = index,
                )
            }
            suppressLocalEvents()
            active.shuffleModeEnabled = false
            active.setMediaItems(items, targetIndex, snapshot.progressMs.coerceAtLeast(0L))
            active.prepare()
            if (snapshot.isPlaying) active.play() else active.pause()
            lastKnownSongId = target
        }

        private fun applyRemoteMode(snapshot: MeloXListenTogetherSnapshot, active: MediaController) {
            val mode = snapshot.playMode?.uppercase() ?: return
            val shuffle = mode.contains("RANDOM") || mode.contains("SHUFFLE")
            MeloXPlaybackModePreferences.setShuffle(appContext, shuffle)
            // The Together randomList is already a concrete playback order, so
            // enabling Media3's own shuffle on top of it would randomize twice.
            active.shuffleModeEnabled = false
            active.repeatMode = when {
                mode.contains("ONE") || mode.contains("SINGLE") -> Player.REPEAT_MODE_ONE
                mode.contains("LOOP") || mode.contains("ALL") -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
        }

        private fun applyRemoteCommand(snapshot: MeloXListenTogetherSnapshot) {
            val active = controller ?: return
            val targetId = snapshot.targetSongId
            suppressLocalEvents()
            if (targetId != null) {
                val targetIndex = (0 until active.mediaItemCount).firstOrNull {
                    active.getMediaItemAt(it).mediaId.toLongOrNull() == targetId
                }
                if (targetIndex != null && targetIndex != active.currentMediaItemIndex) {
                    active.seekTo(targetIndex, snapshot.progressMs.coerceAtLeast(0L))
                    lastKnownSongId = targetId
                } else if (targetIndex != null) {
                    val drift = kotlin.math.abs(active.currentPosition - snapshot.progressMs)
                    if (drift >= DRIFT_CORRECTION_MS) active.seekTo(snapshot.progressMs.coerceAtLeast(0L))
                }
            }
            if (snapshot.isPlaying) active.play() else active.pause()
        }

        private fun reportCommand(
            type: MeloXListenTogetherCommandType,
            targetSongId: Long,
            formerSongId: Long?,
        ) {
            val activeRoom = room ?: return
            val active = controller ?: return
            scope.launch {
                commandMutex.withLock {
                    runCatching {
                        transport.reportCommand(
                            roomId = activeRoom.id,
                            commandType = type,
                            progressMs = active.currentPosition.coerceAtLeast(0L),
                            isPlaying = active.isPlaying,
                            formerSongId = formerSongId,
                            targetSongId = targetSongId,
                            clientSequence = commandSequence.getAndIncrement(),
                        )
                    }.onFailure(::recordFailure)
                }
            }
        }

        private fun scheduleQueueReport() {
            if (room == null) return
            queueReportJob?.cancel()
            queueReportJob = scope.launch {
                delay(QUEUE_DEBOUNCE_MS)
                room?.let { reportQueueNow(it) }
            }
        }

        private suspend fun reportQueueNow(activeRoom: MeloXListenTogetherRoom) {
            val active = controller ?: return
            val selfId = currentUserId() ?: return
            val actual = mediaIds(active)
            if (actual.isEmpty()) return
            val shuffle = MeloXPlaybackModePreferences.shuffle(appContext)
            val display = if (shuffle) displayOrderIds(active).ifEmpty { actual } else actual
            val random = if (shuffle) actual else display
            playlistMutex.withLock {
                runCatching {
                    transport.reportPlaylist(
                        roomId = activeRoom.id,
                        userId = selfId,
                        version = playlistVersion++,
                        displaySongIds = display,
                        randomSongIds = random,
                    )
                }.onFailure(::recordFailure)
            }
        }

        private suspend fun sendHeartbeat(activeRoom: MeloXListenTogetherRoom) {
            val active = controller ?: return
            val songId = active.currentMediaItem?.mediaId?.toLongOrNull() ?: return
            runCatching {
                transport.heartbeat(
                    roomId = activeRoom.id,
                    songId = songId,
                    isPlaying = active.isPlaying,
                    progressMs = active.currentPosition.coerceAtLeast(0L),
                )
            }.onFailure(::recordFailure)
        }

        private suspend fun currentUserId(): Long? {
            cachedUserId?.let { return it }
            val resolved = runCatching { account.accountProfile().userId }.getOrNull()
            if (resolved != null && resolved > 0L) cachedUserId = resolved
            return resolved
        }

        private fun mediaIds(player: Player): List<Long> = buildList {
            for (index in 0 until player.mediaItemCount) {
                player.getMediaItemAt(index).mediaId.toLongOrNull()?.takeIf { it > 0L }?.let(::add)
            }
        }

        private fun displayOrderIds(player: Player): List<Long> {
            data class Entry(val index: Int, val original: Int, val id: Long)
            val entries = buildList {
                for (index in 0 until player.mediaItemCount) {
                    val item = player.getMediaItemAt(index)
                    val id = item.mediaId.toLongOrNull() ?: continue
                    val original = item.mediaMetadata.extras?.getInt(
                        PlaybackCommands.QUEUE_ORIGINAL_INDEX_KEY,
                        PlaybackCommands.QUEUE_ORIGINAL_INDEX_UNSET,
                    ) ?: PlaybackCommands.QUEUE_ORIGINAL_INDEX_UNSET
                    add(Entry(index, original, id))
                }
            }
            val base = entries.filter { it.original >= 0 }.sortedBy { it.original }
            val manual = entries.filter { it.original < 0 }.sortedBy { it.index }
            return (base + manual).map { it.id }
        }

        private fun suppressLocalEvents() {
            suppressLocalUntilRealtime = SystemClock.elapsedRealtime() + REMOTE_SUPPRESSION_MS
        }

        private fun localEventsSuppressed(): Boolean =
            room == null || SystemClock.elapsedRealtime() < suppressLocalUntilRealtime

        private fun recordFailure(error: Throwable) {
            failures = (failures + 1).coerceAtMost(99)
            mutableState.value = State(
                phase = if (room == null) Phase.Idle else Phase.Reconnecting,
                room = room,
                consecutiveFailures = failures,
                lastError = error.message,
            )
            Log.w(TAG, "Together sync failure #$failures", error)
        }

        private fun resetRoom() {
            room = null
            failures = 0
            heartbeatTick = 0
            statusTick = 0
            firstSyncForRoom = true
            lastRemoteQueueSignature = null
            lastRemoteCommandSignature = null
            queueReportJob?.cancel()
            queueReportJob = null
            controller?.removeListener(listener)
            controller?.release()
            controller = null
            mutableState.value = State()
        }
    }

    private const val TAG = "MeloXTogether"
    private const val SYNC_INTERVAL_MS = 1_000L
    private const val IDLE_POLL_MS = 60_000L
    private const val STATUS_EVERY_TICKS = 5
    private const val HEARTBEAT_EVERY_TICKS = 5
    private const val QUEUE_DEBOUNCE_MS = 350L
    private const val REMOTE_SUPPRESSION_MS = 900L
    private const val DRIFT_CORRECTION_MS = 1_200L
}
