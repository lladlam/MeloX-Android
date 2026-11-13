package com.lladlam.melox.lan

import android.content.Context
import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Process-wide LAN hub. The playback service attaches the host implementation
 * and owns the server; UI code only needs this object.
 */
internal object MeloXLanRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var host: MeloXLanHost? = null
    @Volatile private var prefs: MeloXLanPreferences? = null
    @Volatile private var server: MeloXLanServer? = null
    @Volatile private var castSender: MeloXLanCastSender? = null
    @Volatile private var castClient: MeloXLanClient? = null
    @Volatile private var hostTogetherClient: MeloXLanClient? = null
    @Volatile private var hostTogetherJob: kotlinx.coroutines.Job? = null
    @Volatile private var audioProxy: MeloXLanAudioProxy? = null
    @Volatile private var activeRelayToken: String? = null

    private val serverListeners = CopyOnWriteArraySet<(Boolean) -> Unit>()
    internal var guestTogether by mutableStateOf(false)
        private set

    fun attach(context: Context, host: MeloXLanHost) {
        if (this.host === host && prefs != null) return
        detach()
        val app = context.applicationContext
        val identity = MeloXLanPreferences(app)
        this.host = host
        prefs = identity
        if (!identity.enabled || !MeloXLanPermissions.has(app)) return
        startServer(host, identity)
    }

    private fun startServer(host: MeloXLanHost, identity: MeloXLanPreferences) {
        if (server != null) return
        val candidate = MeloXLanServer(
            host = host,
            identity = identity,
            scope = scope,
            onTogetherEvent = { event -> handleTogetherEvent(event) },
            onTogetherChanged = { guestTogether = it },
        )
        if (candidate.start() < 0) {
            // Binding failed: never keep the dead server around, otherwise the
            // runtime would never retry even after the port conflict is gone.
            candidate.stop()
            return
        }
        server = candidate
    }

    fun ensureStarted(context: Context) {
        val h = host ?: return
        val identity = prefs ?: MeloXLanPreferences(context.applicationContext).also { prefs = it }
        if (!identity.enabled || !MeloXLanPermissions.has(context)) return
        startServer(h, identity)
    }

    fun detach(host: MeloXLanHost? = null) {
        if (host != null && this.host !== host) return
        stopCastSender()
        stopHostTogether()
        audioProxy?.stop()
        audioProxy = null
        guestTogether = false
        server?.stop()
        server = null
        prefs = null
        this.host = null
    }

    fun isAvailable(): Boolean = prefs != null && host != null

    fun isEnabled(context: Context): Boolean =
        (prefs ?: MeloXLanPreferences(context.applicationContext)).enabled

    fun setEnabled(context: Context, enabled: Boolean) {
        val identity = prefs ?: MeloXLanPreferences(context.applicationContext).also { prefs = it }
        identity.enabled = enabled
        if (enabled) {
            host?.let { h ->
                if (MeloXLanPermissions.has(context)) startServer(h, identity)
            }
        } else {
            server?.stop()
            server = null
            stopCastSender()
            // Disabling LAN must also terminate the account-backed audio relay:
            // otherwise a guest keeps receiving host audio for hours.
            revokeActiveRelay()
            audioProxy?.stop()
            audioProxy = null
            hostTogetherJob?.cancel()
            hostTogetherJob = null
            hostTogetherClient?.close()
            hostTogetherClient = null
        }
    }

    fun deviceId(context: Context): String =
        (prefs ?: MeloXLanPreferences(context.applicationContext)).deviceId

    fun deviceName(context: Context): String =
        (prefs ?: MeloXLanPreferences(context.applicationContext)).deviceName

    fun port(context: Context): Int =
        server?.port() ?: (prefs ?: MeloXLanPreferences(context.applicationContext)).port

    fun setPort(context: Context, port: Int): Int {
        val identity = prefs ?: MeloXLanPreferences(context.applicationContext).also { prefs = it }
        val wasRunning = server != null
        if (wasRunning) {
            server?.stop()
            server = null
        }
        identity.port = port
        if (wasRunning) {
            host?.let { h -> if (MeloXLanPermissions.has(context)) startServer(h, identity) }
        }
        return this.port(context)
    }

    fun pin(context: Context): String =
        (prefs ?: MeloXLanPreferences(context.applicationContext)).pin

    fun regeneratePin(context: Context): String =
        (prefs ?: MeloXLanPreferences(context.applicationContext)).regeneratePin()

    fun trustedDevices(context: Context): List<JSONObject> =
        (prefs ?: MeloXLanPreferences(context.applicationContext)).trustedDevices()

    fun recentPeers(context: Context): List<JSONObject> =
        (prefs ?: MeloXLanPreferences(context.applicationContext)).recentPeers()

    fun trustPeer(context: Context, deviceId: String, name: String, platform: String) {
        (prefs ?: MeloXLanPreferences(context.applicationContext))
            .addTrusted(deviceId, name, platform)
    }

    fun removeTrusted(context: Context, deviceId: String) {
        (prefs ?: MeloXLanPreferences(context.applicationContext)).removeTrusted(deviceId)
    }

    suspend fun scan(context: Context, timeoutMs: Long = 1_500L): List<MeloXLanDevice> {
        val identity = prefs ?: MeloXLanPreferences(context.applicationContext).also { prefs = it }
        return MeloXLanDiscovery.scan(identity, timeoutMs)
    }

    fun newClient(
        context: Context,
        device: MeloXLanDevice,
        onEvent: (JSONObject) -> Unit = {},
        onRequest: ((JSONObject) -> JSONObject?)? = null,
        onDisconnected: () -> Unit = {},
    ): MeloXLanClient {
        val identity = prefs ?: MeloXLanPreferences(context.applicationContext).also { prefs = it }
        return MeloXLanClient(device, identity, scope, onEvent, onRequest, onDisconnected)
    }

    fun activeSessionClient(): MeloXLanClient? = hostTogetherClient ?: castClient

    fun isCasting(): Boolean = castSender != null

    fun currentCastFormat(): MeloXLanCastFormat = MeloXCastAudioTap.format

    fun queueSnapshot(): List<MeloXLanTrack> = host?.queueSnapshot().orEmpty()

    fun currentTrack(): MeloXLanTrack? = host?.currentTrack()

    fun snapshot(): MeloXLanPlayback? = host?.snapshot()

    fun hostControl(command: String, positionMs: Long? = null): Boolean =
        host?.control(command, positionMs, null) == true

    suspend fun startCastSender(
        context: Context,
        device: MeloXLanDevice,
        handshake: MeloXLanCastHandshake,
        client: MeloXLanClient?,
    ): Boolean = withContext(Dispatchers.IO) {
        stopCastSender(restoreLocalOutput = false)
        lateinit var sender: MeloXLanCastSender
        sender = MeloXLanCastSender(device, handshake, scope) { reason ->
            if (castSender !== sender) return@MeloXLanCastSender
            val connection = castClient
            castSender = null
            castClient = null
            if (connection != null) {
                if (reason == "format_changed") {
                    scope.launch { MeloXLanSession.resumeCastAfterFormatChange(context.applicationContext, connection) }
                } else {
                    MeloXCastAudioTap.restoreLocalOutput()
                    MeloXLanSession.castStopped(connection)
                    scope.launch { runCatching { connection.stopCast() } }
                }
            }
            if (reason != null) Log.i("MeloXLanRuntime", "Cast stopped: $reason")
        }
        castSender = sender
        castClient = client
        if (!sender.start()) {
            castSender = null
            castClient = null
            MeloXCastAudioTap.restoreLocalOutput()
            return@withContext false
        }
        MeloXCastAudioTap.muteLocalOutput = true
        true
    }

    fun stopCastSender(notifyRemote: Boolean = true, restoreLocalOutput: Boolean = true) {
        if (restoreLocalOutput) MeloXCastAudioTap.restoreLocalOutput()
        val client = castClient
        castClient = null
        val sender = castSender
        castSender = null
        sender?.stop()
        if (notifyRemote && client != null) {
            scope.launch { runCatching { client.stopCast() } }
        }
    }

    fun beginHostTogether(
        client: MeloXLanClient,
        previewState: () -> JSONObject?,
    ) {
        hostTogetherJob?.cancel()
        hostTogetherClient?.takeUnless { it === client }?.let { old ->
            scope.launch { runCatching { old.stopTogether() } }
        }
        hostTogetherClient = client
        hostTogetherJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(1_000L)
                val state = previewState() ?: continue
                val sent = client.sendEvent(
                    JSONObject()
                        .put("type", "event")
                        .put("event", "together.state")
                        .put("state", state),
                )
                if (!sent) {
                    revokeActiveRelay()
                    break
                }
            }
        }
    }

    fun stopHostTogether(notifyRemote: Boolean = true) {
        MeloXLanTogetherCoordinator.stop(notifyRemote)
        hostTogetherJob?.cancel()
        hostTogetherJob = null
        val client = hostTogetherClient
        hostTogetherClient = null
        if (notifyRemote && client != null) scope.launch { runCatching { client.stopTogether() } }
        revokeActiveRelay()
    }

    suspend fun issueRelay(
        context: Context,
        guestAddress: String,
        queue: List<MeloXLanTrack>,
    ): MeloXLanRelay? = withContext(Dispatchers.IO) {
        revokeActiveRelay()
        val proxy = audioProxy ?: MeloXLanAudioProxy(context.applicationContext, scope).also {
            audioProxy = it
        }
        val relay = proxy.issue(guestAddress, queue) ?: return@withContext null
        activeRelayToken = relay.token
        relay
    }

    internal fun playbackHost(): MeloXLanHost? = host
    internal fun onPlaybackUpdated() {
        com.lladlam.melox.dlna.DlnaSession.localTrackChanged(host?.snapshot()?.trackId)
        MeloXLanTogetherCoordinator.changed()
    }
    internal fun onTogetherTrackEnded() { MeloXLanTogetherCoordinator.ended() }
    internal fun onTogetherSeek() { MeloXLanTogetherCoordinator.seek() }
    internal fun onPeerEvent(client: MeloXLanClient, event: JSONObject) {
        MeloXLanTogetherCoordinator.event(client, event)
    }
    internal suspend fun startTogetherSession(context: Context, client: MeloXLanClient, provideAudio: Boolean): Boolean =
        MeloXLanTogetherCoordinator.join(context, client, provideAudio)
    internal suspend fun issuePeerRelay(context: Context, address: String, queue: List<MeloXLanTrack>): MeloXLanRelay? =
        withContext(Dispatchers.IO) {
            val proxy = synchronized(this@MeloXLanRuntime) {
                audioProxy ?: MeloXLanAudioProxy(context.applicationContext, scope).also { audioProxy = it }
            }
            proxy.issue(address, queue)
        }
    internal fun revokePeerRelay(relay: MeloXLanRelay) { audioProxy?.revoke(relay.token) }

    fun revokeActiveRelay() {
        audioProxy?.revoke(activeRelayToken)
        activeRelayToken = null
    }

    fun sendGuestTogetherControl(context: Context, command: String, positionMs: Long? = null): Boolean =
        server?.sendTogetherControl(command, positionMs) == true

    fun isHostingTogether(): Boolean = hostTogetherClient != null || MeloXLanTogetherCoordinator.active

    fun isGuestTogether(): Boolean = guestTogether

    fun stopGuestTogether() { server?.stopTogetherSession() }

    fun addServerListener(listener: (Boolean) -> Unit) {
        serverListeners += listener
    }

    fun removeServerListener(listener: (Boolean) -> Unit) {
        serverListeners -= listener
    }

    private fun handleTogetherEvent(event: JSONObject) {
        if (event.optString("event") != "together.state") return
        val state = event.optJSONObject("state") ?: return
        host?.applyTogetherState(
            positionMs = state.optLong("positionMs", 0L),
            playing = state.optBoolean("playing"),
            index = state.optInt("index", -1),
        )
    }
}
