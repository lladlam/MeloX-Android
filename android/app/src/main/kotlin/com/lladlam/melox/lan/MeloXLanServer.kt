package com.lladlam.melox.lan

import android.util.Log
import com.lladlam.melox.core.network.readBoundedLine
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "MeloXLanServer"

/**
 * The playback-related operations a LAN peer may invoke. Implemented by the
 * playback service so the network layer never touches ExoPlayer directly.
 */
internal interface MeloXLanHost {
    fun snapshot(): MeloXLanPlayback
    fun queueSnapshot(): List<MeloXLanTrack>
    fun currentTrack(): MeloXLanTrack?
    suspend fun dlnaSource(): com.lladlam.melox.dlna.DlnaMediaSource? = null
    fun control(command: String, positionMs: Long?, volume: Int?): Boolean
    fun transfer(track: MeloXLanTrack, queue: List<MeloXLanTrack>, positionMs: Long): Boolean
    fun startCast(format: MeloXLanCastFormat): MeloXLanCastHandshake?
    fun stopCast()
    fun startTogether(
        queue: List<MeloXLanTrack>,
        index: Int,
        positionMs: Long,
        playing: Boolean,
        relay: MeloXLanRelay?,
        sameDevice: Boolean = false,
    ): Boolean
    fun stopTogether()
    fun applyTogetherState(positionMs: Long, playing: Boolean, index: Int)
    suspend fun prepareTogetherRound(queue: List<MeloXLanTrack>?, index: Int, positionMs: Long, relay: MeloXLanRelay?, sameDevice: Boolean): Boolean
    fun commitTogetherRound(startAtLocalMs: Long, positionMs: Long, playing: Boolean)
    fun setTogetherBarrier(enabled: Boolean)
    fun correctTogether(positionMs: Long, playing: Boolean, index: Int, late: Boolean, forceSeek: Boolean = false) {
        applyTogetherState(positionMs, playing, index)
    }
}

internal class MeloXLanServer(
    private val host: MeloXLanHost,
    val identity: MeloXLanPreferences,
    private val scope: CoroutineScope,
    private val onTogetherEvent: ((JSONObject) -> Unit)? = null,
    private val onTogetherChanged: ((Boolean) -> Unit)? = null,
) {
    @Volatile private var controlPort: Int = identity.port
    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var eventJob: Job? = null
    private var discovery: MeloXLanDiscoveryResponder? = null
    private val clients = CopyOnWriteArraySet<Client>()
    private val pendingHandshakes = java.util.concurrent.atomic.AtomicInteger(0)
    private val pinFailures = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val pinLockedUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile private var togetherClient: Client? = null
    @Volatile private var togetherQueue: List<MeloXLanTrack> = emptyList()
    @Volatile private var togetherIndex: Int = 0
    @Volatile private var togetherSession = ""
    @Volatile private var togetherGeneration = -1L
    @Volatile private var togetherLate = false
    @Volatile private var togetherCommitted = false
    private var preparation: Job? = null

    fun start(): Int {
        if (running) return controlPort
        running = true
        val socket = bindControlSocket()
        if (socket == null) {
            running = false
            Log.w(TAG, "Unable to bind a LAN control port")
            return -1
        }
        controlPort = socket.localPort
        identity.port = controlPort
        serverSocket = socket
        acceptJob = scope.launch(Dispatchers.IO) { acceptLoop(socket) }
        discovery = MeloXLanDiscoveryResponder(
            identity = identity,
            controlPort = { controlPort },
            snapshot = { host.snapshot() },
            isTrusted = { identity.isTrusted(it) },
        ).also { it.start() }
        eventJob = scope.launch {
            while (isActive) {
                delay(200L)
                if (running && clients.isNotEmpty()) broadcastState()
            }
        }
        Log.i(TAG, "LAN server listening on $controlPort")
        return controlPort
    }

    fun stop() {
        running = false
        acceptJob?.cancel()
        acceptJob = null
        eventJob?.cancel()
        eventJob = null
        discovery?.stop()
        discovery = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        clients.toList().forEach { it.close() }
        clients.clear()
        if (togetherClient != null) {
            togetherClient = null
            onTogetherChanged?.invoke(false)
        }
    }

    fun port(): Int = controlPort

    fun hasTogether(): Boolean = togetherClient != null

    fun stopTogetherSession() {
        val client = togetherClient ?: return
        togetherClient = null
        preparation?.cancel()
        togetherSession = ""
        host.stopTogether()
        onTogetherChanged?.invoke(false)
        scope.launch(Dispatchers.IO) {
            client.send(JSONObject().put("type", "event").put("event", "together.stopped"))
        }
    }

    fun sendTogetherControl(command: String, positionMs: Long?): Boolean {
        val client = togetherClient ?: return false
        scope.launch(Dispatchers.IO) { client.sendRequest(
            JSONObject()
                .put("type", "request")
                .put("action", "control")
                .put("command", command)
                .put("positionMs", positionMs ?: 0L),
        ) }
        return true
    }

    private fun bindControlSocket(): ServerSocket? {
        for (offset in 0..9) {
            val candidate = identity.port + offset
            if (candidate > 65535) break
            val socket = runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(candidate))
                }
            }.getOrNull()
            if (socket != null) return socket
        }
        return null
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            val clientSocket = runCatching { socket.accept() }.getOrNull() ?: break
            if (clients.size >= MAX_LAN_CLIENTS || pendingHandshakes.get() >= MAX_PENDING_HANDSHAKES) {
                runCatching { clientSocket.close() }
                continue
            }
            val client = Client(clientSocket)
            pendingHandshakes.incrementAndGet()
            // The handshake runs off the accept thread: a peer that connects and
            // stays silent must not block every other peer from connecting.
            scope.launch(Dispatchers.IO) {
                try {
                    if (!client.handshake()) {
                        client.close()
                        return@launch
                    }
                    if (!running || clients.size >= MAX_LAN_CLIENTS) {
                        client.close()
                        return@launch
                    }
                    clients += client
                    client.startReader()
                    client.send(
                        JSONObject()
                            .put("type", "event")
                            .put("event", "state")
                            .put("state", host.snapshot().toJson()),
                    )
                } finally {
                    pendingHandshakes.decrementAndGet()
                }
            }
        }
    }

    private fun broadcastState() {
        val state = host.snapshot().toJson()
        clients.forEach { client ->
            client.send(
                JSONObject()
                    .put("type", "event")
                    .put("event", "state")
                    .put("state", state),
            )
        }
        togetherClient?.takeIf { togetherSession.isNotEmpty() }?.let { client ->
            client.send(
                JSONObject()
                    .put("type", "event")
                    .put("event", "together.status")
                    .put("sessionId", togetherSession).put("generation", togetherGeneration)
                    .put("state", state),
            )
        }
    }

    private inner class Client(private val socket: Socket) {
        private val closed = AtomicBoolean(false)
        private val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
        var deviceId: String = ""
            private set
        var deviceName: String = ""
            private set
        var platform: String = ""
            private set
        var trusted: Boolean = false
            private set

        fun handshake(): Boolean {
            socket.soTimeout = 10_000
            val remoteAddress = runCatching { socket.inetAddress.hostAddress.orEmpty() }.getOrDefault("")
            val lockedUntil = pinLockedUntil[remoteAddress] ?: 0L
            if (lockedUntil > System.currentTimeMillis()) {
                send(JSONObject().put("type", "auth_failed").put("reason", "locked"))
                close()
                return false
            }
            return try {
                var message = readJson() ?: return false
                if (message.optString("type") != "hello") return false
                deviceId = message.optString("deviceId")
                deviceName = message.optString("deviceName").ifBlank { "MeloX" }
                platform = message.optString("platform")
                if (deviceId.isBlank()) return false
                trusted = identity.isTrusted(deviceId)
                // Discovery broadcasts every device ID, so an ID alone is public
                // information and cannot authenticate a new socket. Trusted peers
                // skip the trust prompt, not the PIN proof.
                var authenticated = false
                var attempts = 0
                while (!authenticated && attempts < 5) {
                    if (message.optString("pin") == identity.pin) {
                        authenticated = true
                        break
                    }
                    send(
                        JSONObject()
                            .put("type", "auth_required")
                            .put("pinRequired", true)
                            .put("attemptsLeft", 4 - attempts),
                    )
                    attempts++
                    message = readJson() ?: return false
                    if (message.optString("type") != "hello") return false
                    deviceId = message.optString("deviceId")
                    deviceName = message.optString("deviceName").ifBlank { deviceName }
                    platform = message.optString("platform").ifBlank { platform }
                }
                if (!authenticated) {
                    val failures = pinFailures.merge(remoteAddress, 1, Int::plus) ?: 1
                    if (failures >= PIN_FAILURE_LIMIT) {
                        pinLockedUntil[remoteAddress] = System.currentTimeMillis() + PIN_LOCKOUT_MS
                        pinFailures.remove(remoteAddress)
                    }
                    send(JSONObject().put("type", "auth_failed").put("reason", "pin"))
                    close()
                    return false
                }
                identity.rememberPeer(
                    deviceId = deviceId,
                    name = deviceName,
                    platform = platform,
                    address = remoteAddress,
                )
                send(
                    JSONObject()
                        .put("type", "auth_ok")
                        .put("trusted", trusted)
                        .put("deviceId", identity.deviceId)
                        .put("deviceName", identity.deviceName)
                        .put("platform", "android")
                        .put("capabilities", JSONArray(listOf("control", "cast", "transfer", "together", MELOX_LAN_BARRIER)))
                        .put("port", controlPort),
                )
                socket.soTimeout = 0
                true
            } catch (error: Exception) {
                Log.w(TAG, "LAN handshake failed", error)
                close()
                false
            }
        }

        fun startReader() {
            scope.launch(Dispatchers.IO) {
                try {
                    while (running && !closed.get()) {
                        val request = readJson() ?: break
                        if (request.optString("type") == "event") {
                            if (togetherClient === this@Client) handleTogetherEvent(request)
                        } else {
                            handle(request)
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // Read timeout is only used during handshake.
                } catch (error: Exception) {
                    Log.w(TAG, "LAN client disconnected", error)
                } finally {
                    close()
                }
            }
        }

        private fun handle(request: JSONObject) {
            val id = request.optString("id")
            val type = request.optString("type")
            if (type == "response") return
            if (type != "request") return
            fun respond(builder: JSONObject.() -> Unit) {
                val response = JSONObject().put("type", "response").put("id", id).apply(builder)
                send(response)
            }
            when (val action = request.optString("action")) {
                "state" -> respond {
                    put("ok", true)
                    put("state", host.snapshot().toJson())
                }
                "control" -> {
                    val ok = host.control(
                        command = request.optString("command"),
                        positionMs = request.optLong("positionMs", -1L).takeIf { it >= 0L },
                        volume = request.optInt("volume", -1).takeIf { it in 0..100 },
                    )
                    respond { put("ok", ok) }
                    if (ok) broadcastState()
                }
                "transfer" -> {
                    val track = MeloXLanTrack.fromJson(request.optJSONObject("track"))
                    if (track == null) {
                        respond { put("ok", false).put("error", "invalid_track") }
                    } else {
                        val ok = host.transfer(
                            track = track,
                            queue = MeloXLanTrack.fromJsonArray(request.optJSONArray("queue")),
                            positionMs = request.optLong("positionMs", 0L).coerceAtLeast(0L),
                        )
                        respond { put("ok", ok).apply { if (!ok) put("error", "unsupported_track") } }
                        if (ok) broadcastState()
                    }
                }
                "cast.start" -> {
                    val handshake = host.startCast(
                        MeloXLanCastFormat.fromJson(request.optJSONObject("format")),
                    )
                    if (handshake == null) {
                        respond { put("ok", false).put("error", "cast_unavailable") }
                    } else {
                        respond {
                            put("ok", true)
                            put("castPort", handshake.port)
                            put("castToken", handshake.token)
                            put("format", handshake.format.toJson())
                        }
                        host.control("pause", null, null)
                        clients.forEach {
                            it.send(
                                JSONObject()
                                    .put("type", "event")
                                    .put("event", "cast.started")
                                    .put("port", handshake.port)
                                    .put("format", handshake.format.toJson()),
                            )
                        }
                    }
                }
                "cast.stop" -> {
                    host.stopCast()
                    respond { put("ok", true) }
                    clients.forEach {
                        it.send(JSONObject().put("type", "event").put("event", "cast.stopped"))
                    }
                }
                "together.start" -> {
                    preparation?.cancel()
                    togetherSession = ""
                    val queue = MeloXLanTrack.fromJsonArray(request.optJSONArray("queue"))
                    val index = request.optInt("index", 0)
                    val position = request.optLong("positionMs", 0L).coerceAtLeast(0L)
                    val ok = host.startTogether(
                        queue = queue,
                        index = index,
                        positionMs = position,
                        playing = request.optBoolean("playing", true),
                        relay = MeloXLanRelay.fromJson(request.optJSONObject("relay")),
                        sameDevice = socket.inetAddress.isLoopbackAddress || socket.inetAddress == socket.localAddress,
                    )
                    if (ok) {
                        togetherClient = this
                        togetherQueue = queue
                        togetherIndex = index
                        onTogetherChanged?.invoke(true)
                    }
                    respond {
                        put("ok", ok)
                        put("hostAddress", localIpv4Address().orEmpty())
                        put("hostPort", controlPort)
                        put("state", host.snapshot().toJson())
                    }
                    if (ok) broadcastState()
                }
                "together.prepare" -> {
                    val session = request.optString("sessionId")
                    val generation = request.optLong("generation", -1L)
                    val queue = MeloXLanTrack.fromJsonArray(request.optJSONArray("queue"))
                    if (session.isBlank() || generation < 0L || queue.isEmpty() ||
                        (session == togetherSession && generation <= togetherGeneration)) {
                        respond { put("ok", false).put("error", "stale_or_invalid_round") }
                        return
                    }
                    preparation?.cancel()
                    togetherSession = session
                    togetherGeneration = generation
                    togetherCommitted = false
                    togetherLate = false
                    togetherClient = this
                    togetherQueue = queue
                    togetherIndex = request.optInt("index", 0)
                    onTogetherChanged?.invoke(true)
                    preparation = scope.launch {
                        val ok = host.prepareTogetherRound(queue, togetherIndex, request.optLong("positionMs", 0L),
                            MeloXLanRelay.fromJson(request.optJSONObject("relay")),
                            socket.inetAddress.isLoopbackAddress || socket.inetAddress == socket.localAddress)
                        if (togetherSession == session && togetherGeneration == generation && togetherClient === this@Client) {
                            respond { put("ok", ok).put("sessionId", session).put("generation", generation) }
                        }
                    }
                }
                "together.stop" -> {
                    preparation?.cancel()
                    togetherSession = ""
                    host.stopTogether()
                    togetherClient = null
                    onTogetherChanged?.invoke(false)
                    respond { put("ok", true) }
                    broadcastState()
                }
                "ping" -> respond {
                    put("ok", true)
                    put("t0", request.optLong("t0", 0L))
                    put("serverTimeMs", System.currentTimeMillis())
                }
                else -> respond { put("ok", false).put("error", "unknown_action:$action") }
            }
        }

        private fun handleTogetherEvent(event: JSONObject) {
            if (togetherSession.isNotEmpty()) {
                if (event.optString("sessionId") != togetherSession || event.optLong("generation") != togetherGeneration) return
                when (event.optString("event")) {
                    "together.commit" -> {
                        togetherLate = event.optBoolean("late")
                        togetherCommitted = true
                        host.commitTogetherRound(event.optLong("startAtMs") - event.optLong("clockOffsetMs"),
                            event.optLong("positionMs"), event.optBoolean("playing"))
                    }
                    "together.state" -> {
                        if (!togetherCommitted) return
                        val state = event.optJSONObject("state") ?: event
                        val playing = state.optBoolean("playing")
                        val target = togetherTargetPosition(state.optLong("positionMs"), playing && state.optBoolean("ready", true),
                            state.optLong("serverTimeMs", event.optLong("serverTimeMs")), System.currentTimeMillis(), event.optLong("clockOffsetMs"))
                        host.correctTogether(target, playing, state.optInt("index"), togetherLate && !event.optBoolean("seek"), event.optBoolean("seek"))
                    }
                }
            } else {
                // v1 peers shipped both nested and flat state envelopes.
                val state = event.optJSONObject("state") ?: event
                onTogetherEvent?.invoke(JSONObject().put("event", event.optString("event")).put("state", state))
            }
        }

        fun sendRequest(value: JSONObject): String? {
            val id = value.optString("id").ifBlank { java.util.UUID.randomUUID().toString() }
            value.put("id", id).put("type", "request")
            return if (send(value)) id else null
        }

        fun send(value: JSONObject): Boolean = synchronized(writer) {
            if (closed.get()) return false
            runCatching {
                writer.write(value.toString())
                writer.write("\n")
                writer.flush()
                true
            }.getOrDefault(false)
        }

        private fun readJson(): JSONObject? {
            val line = runCatching { reader.readBoundedLine(MELOX_LAN_MAX_MESSAGE_BYTES) }.getOrNull() ?: return null
            if (line.length > MELOX_LAN_MAX_MESSAGE_BYTES) return null
            return runCatching { JSONObject(line) }.getOrNull()
        }

        fun close() {
            if (!closed.compareAndSet(false, true)) return
            clients.remove(this)
            if (togetherClient === this) {
                preparation?.cancel()
                togetherSession = ""
                togetherClient = null
                host.stopTogether()
                onTogetherChanged?.invoke(false)
            }
            runCatching { socket.close() }
        }
    }
}
