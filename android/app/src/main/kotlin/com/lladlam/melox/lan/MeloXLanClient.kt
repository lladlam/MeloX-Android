package com.lladlam.melox.lan

import android.util.Log
import com.lladlam.melox.core.network.readBoundedLine
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "MeloXLanClient"

internal enum class MeloXLanConnectResult { Connected, PinRequired, Failed }

/**
 * A single authenticated control connection. Requests can be sent in both
 * directions: the client normally sends control requests, while a Together
 * guest may receive them on the same socket and answer on its own player.
 */
internal class MeloXLanClient(
    val device: MeloXLanDevice,
    private val identity: MeloXLanPreferences,
    private val scope: CoroutineScope,
    private val onEvent: (JSONObject) -> Unit = {},
    private val onRequest: ((JSONObject) -> JSONObject?)? = null,
    private val onDisconnected: () -> Unit = {},
) {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    @Volatile var isConnected: Boolean = false
        private set
    @Volatile var trusted: Boolean = false
        private set
    @Volatile var remoteDeviceId: String = ""
        private set
    @Volatile var capabilities: Set<String> = device.capabilities
        private set

    suspend fun connect(pin: String? = null): MeloXLanConnectResult = withContext(Dispatchers.IO) {
        close()
        val created = runCatching {
            Socket().apply {
                connect(InetSocketAddress(device.address, device.port), 3_000)
                soTimeout = 5_000
            }
        }.getOrElse {
            Log.w(TAG, "Connect to ${device.address}:${device.port} failed", it)
            return@withContext MeloXLanConnectResult.Failed
        }
        socket = created
        reader = BufferedReader(InputStreamReader(created.getInputStream(), Charsets.UTF_8))
        writer = BufferedWriter(OutputStreamWriter(created.getOutputStream(), Charsets.UTF_8))
        val result = sendHello(pin)
        if (result != MeloXLanConnectResult.Connected) {
            close()
            return@withContext result
        }
        created.soTimeout = 0
        isConnected = true
        scope.launch(Dispatchers.IO) { readLoop() }
        MeloXLanConnectResult.Connected
    }

    private fun sendHello(pin: String?): MeloXLanConnectResult {
        val hello = JSONObject()
            .put("type", "hello")
            .put("v", MELOX_LAN_VERSION)
            .put("deviceId", identity.deviceId)
            .put("deviceName", identity.deviceName)
            .put("platform", "android")
            .put("appVersion", identity.appVersion)
        pin?.takeIf { it.isNotBlank() }?.let { hello.put("pin", it) }
        if (!send(hello)) return MeloXLanConnectResult.Failed
        val response = readJson() ?: return MeloXLanConnectResult.Failed
        return when (response.optString("type")) {
            "auth_ok" -> {
                trusted = response.optBoolean("trusted")
                remoteDeviceId = response.optString("deviceId")
                response.optJSONArray("capabilities")?.let { values ->
                    capabilities = (0 until values.length()).map { values.optString(it) }.toSet()
                }
                MeloXLanConnectResult.Connected
            }
            "auth_required" -> MeloXLanConnectResult.PinRequired
            else -> MeloXLanConnectResult.Failed
        }
    }

    fun close() {
        val wasConnected = isConnected
        isConnected = false
        pending.values.forEach { it.cancel() }
        pending.clear()
        runCatching { socket?.close() }
        socket = null
        reader = null
        writer = null
        if (wasConnected) {
            Log.i(TAG, "LAN connection closed: ${device.address}:${device.port}")
            onDisconnected()
        }
    }

    fun sendEvent(value: JSONObject): Boolean = send(value)

    fun sendResponse(id: String?, value: JSONObject): Boolean = send(
        value.put("type", "response").apply { id?.let { put("id", it) } },
    )

    suspend fun request(
        action: String,
        payload: JSONObject = JSONObject(),
        timeoutMs: Long = 5_000L,
    ): JSONObject? = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val request = JSONObject()
            .put("id", id)
            .put("type", "request")
            .put("action", action)
            .apply { payload.keys().forEach { key -> put(key, payload.opt(key)) } }
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred
        if (!send(request)) {
            pending.remove(id)
            Log.w(TAG, "LAN request $action could not be sent")
            return@withContext null
        }
        try {
            withTimeout(timeoutMs) { deferred.await() }.also { response ->
                if (response.has("ok") && !response.optBoolean("ok")) {
                    Log.w(TAG, "LAN request $action rejected: ${response.optString("error")}")
                }
            }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "LAN request $action timed out")
            null
        } catch (error: CancellationException) {
            throw error
        } finally {
            pending.remove(id)
        }
    }

    suspend fun control(command: String, positionMs: Long? = null, volume: Int? = null): Boolean {
        val payload = JSONObject().put("command", command)
        positionMs?.let { payload.put("positionMs", it) }
        volume?.let { payload.put("volume", it) }
        return request("control", payload)?.optBoolean("ok") == true
    }

    suspend fun transfer(
        track: MeloXLanTrack,
        queue: List<MeloXLanTrack>,
        positionMs: Long,
    ): Boolean = request(
        "transfer",
        JSONObject()
            .put("track", track.toJson())
            .put("queue", JSONArray(queue.map { it.toJson() }))
            .put("positionMs", positionMs),
    )?.optBoolean("ok") == true

    suspend fun startCast(format: MeloXLanCastFormat = MeloXCastAudioTap.format): MeloXLanCastHandshake? {
        val response = request("cast.start", JSONObject().put("format", format.toJson())) ?: return null
        if (!response.optBoolean("ok")) return null
        val port = response.optInt("castPort", 0)
        if (port !in 1..65535) return null
        val negotiated = MeloXLanCastFormat.fromJson(response.optJSONObject("format"))
        if (negotiated != format || negotiated.codec != "pcm_s16le") {
            Log.w(TAG, "Cast format mismatch: requested=$format received=$negotiated")
            stopCast()
            return null
        }
        val token = response.optString("castToken").takeIf { it.length in 16..128 } ?: run {
            Log.w(TAG, "Cast receiver did not provide an authentication token")
            stopCast()
            return null
        }
        Log.i(TAG, "Cast receiver ready: ${device.address}:$port $negotiated")
        return MeloXLanCastHandshake(port, negotiated, token)
    }

    suspend fun stopCast(): Boolean = request("cast.stop")?.optBoolean("ok") == true

    suspend fun startTogether(
        queue: List<MeloXLanTrack>,
        index: Int,
        positionMs: Long,
        playing: Boolean,
        relay: MeloXLanRelay? = null,
    ): JSONObject? = request(
        "together.start",
        JSONObject()
            .put("queue", JSONArray(queue.map { it.toJson() }))
            .put("index", index)
            .put("positionMs", positionMs)
            .put("playing", playing)
            .apply { relay?.let { put("relay", it.toJson()) } },
    )

    suspend fun stopTogether(): Boolean = request("together.stop")?.optBoolean("ok") == true

    suspend fun ping(): Long? {
        val t0 = System.currentTimeMillis()
        val response = request("ping", JSONObject().put("t0", t0)) ?: return null
        val t1 = response.optLong("serverTimeMs", -1L)
        val t3 = System.currentTimeMillis()
        if (t1 <= 0L) return null
        // Approximate one-way half-RTT offset; the server timestamp is taken
        // at request handling time, so this is enough for sub-second sync.
        return t1 - (t0 + t3) / 2L
    }

    private fun readLoop() {
        try {
            while (isConnected) {
                val message = readJson() ?: break
                when (message.optString("type")) {
                    "response" -> {
                        val deferred = pending.remove(message.optString("id")) ?: continue
                        deferred.complete(message)
                    }
                    "event" -> onEvent(message)
                    "request" -> {
                        val response = onRequest?.invoke(message)
                        sendResponse(
                            message.optString("id"),
                            response ?: JSONObject().put("ok", false).put("error", "unsupported"),
                        )
                    }
                    "auth_failed" -> close()
                }
            }
        } catch (error: Exception) {
            if (isConnected) Log.w(TAG, "LAN client read loop ended", error)
        } finally {
            close()
        }
    }

    private fun readJson(): JSONObject? {
        val line = runCatching { reader?.readBoundedLine(MELOX_LAN_MAX_MESSAGE_BYTES) }.getOrNull() ?: return null
        if (line.length > MELOX_LAN_MAX_MESSAGE_BYTES) return null
        return runCatching { JSONObject(line) }.getOrNull()
    }

    private fun send(value: JSONObject): Boolean = synchronized(this) {
        val out = writer ?: return false
        runCatching {
            out.write(value.toString())
            out.write("\n")
            out.flush()
            true
        }.onFailure { Log.w(TAG, "LAN socket write failed", it) }.getOrDefault(false)
    }
}
