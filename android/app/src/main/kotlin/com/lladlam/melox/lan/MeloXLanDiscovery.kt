package com.lladlam.melox.lan

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.UUID
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "MeloXLan"

internal fun localIpv4Address(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList().asSequence() }
        .filterIsInstance<java.net.Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress
}.getOrNull()

/**
 * Answers discovery probes on UDP 47830. One responder lives as long as the
 * playback service; stopping it closes the port immediately.
 */
internal class MeloXLanDiscoveryResponder(
    private val identity: MeloXLanPreferences,
    private val controlPort: () -> Int,
    private val snapshot: () -> MeloXLanPlayback,
    private val isTrusted: (String) -> Boolean,
) {
    @Volatile private var running = false
    private var socket: DatagramSocket? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = thread(name = "MeloXLanDiscovery") {
            val udp = runCatching {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(MELOX_LAN_DISCOVERY_PORT))
                }
            }.onFailure { Log.w(TAG, "Discovery bind failed", it) }.getOrNull()
            if (udp == null) {
                running = false
                return@thread
            }
            socket = udp
            try {
                val buffer = ByteArray(8 * 1024)
                while (running) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    runCatching { udp.receive(packet) }
                        .onFailure { if (running) Log.w(TAG, "Discovery receive failed", it) }
                        .onSuccess { reply(udp, packet) }
                }
            } finally {
                runCatching { udp.close() }
                socket = null
            }
        }
    }

    private fun reply(socket: DatagramSocket, packet: DatagramPacket) {
        val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (message.optString("magic") != MELOX_LAN_MAGIC) return
        if (message.optInt("v", 0) != MELOX_LAN_VERSION) return
        if (message.optString("type") != "discover" && message.optString("type") != "announce") return
        val peerId = message.optString("deviceId")
        val trusted = isTrusted(peerId)
        val reply = JSONObject()
            .put("magic", MELOX_LAN_MAGIC)
            .put("v", MELOX_LAN_VERSION)
            .put("type", "announce")
            .put("deviceId", identity.deviceId)
            .put("name", identity.deviceName)
            .put("platform", "android")
            .put("model", android.os.Build.MODEL.orEmpty())
            .put("appVersion", identity.appVersion)
            .put("port", controlPort())
            .put("pinRequired", !trusted)
            .put("trusted", trusted)
            .put("capabilities", meloXJsonStringArray(listOf("control", "cast", "transfer", "together", MELOX_LAN_BARRIER)))
            .put("address", localIpv4Address().orEmpty())
            .put("nonce", message.optString("nonce").ifBlank { UUID.randomUUID().toString() })
        // Playback state (title/artist/position) is personal data and is only
        // announced to devices the user has explicitly paired with.
        if (trusted) reply.put("state", snapshot().toJson())
        val bytes = reply.toString().toByteArray(Charsets.UTF_8)
        runCatching {
            socket.send(DatagramPacket(bytes, bytes.size, packet.address, packet.port))
        }.onFailure { Log.w(TAG, "Discovery reply failed", it) }
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        socket = null
        thread?.join(500)
        thread = null
    }
}

internal object MeloXLanDiscovery {
    suspend fun scan(
        identity: MeloXLanPreferences,
        timeoutMs: Long = 1_500L,
    ): List<MeloXLanDevice> = withContext(Dispatchers.IO) {
        val devices = linkedMapOf<String, MeloXLanDevice>()
        val udp = DatagramSocket().apply { broadcast = true }
        try {
            val request = JSONObject()
                .put("magic", MELOX_LAN_MAGIC)
                .put("v", MELOX_LAN_VERSION)
                .put("type", "discover")
                .put("deviceId", identity.deviceId)
                .put("nonce", UUID.randomUUID().toString())
            val bytes = request.toString().toByteArray(Charsets.UTF_8)
            val broadcast = runCatching { InetAddress.getByName("255.255.255.255") }.getOrNull()
            if (broadcast != null) {
                runCatching {
                    udp.send(DatagramPacket(bytes, bytes.size, broadcast, MELOX_LAN_DISCOVERY_PORT))
                }
            }
            // Some Android builds drop global broadcast; also send to the
            // site-local interface broadcast address when available.
            localIpv4Address()?.let { local ->
                val parts = local.split('.')
                if (parts.size == 4) {
                    val directed = runCatching {
                        InetAddress.getByName("${parts[0]}.${parts[1]}.${parts[2]}.255")
                    }.getOrNull()
                    if (directed != null) {
                        runCatching {
                            udp.send(DatagramPacket(bytes, bytes.size, directed, MELOX_LAN_DISCOVERY_PORT))
                        }
                    }
                }
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(16 * 1024)
            while (System.currentTimeMillis() < deadline) {
                udp.soTimeout = (deadline - System.currentTimeMillis()).coerceAtMost(250L).toInt().coerceAtLeast(1)
                val packet = DatagramPacket(buffer, buffer.size)
                val received = runCatching { udp.receive(packet) }.isSuccess
                if (!received) continue
                val message = runCatching {
                    JSONObject(String(packet.data, packet.offset, packet.length, Charsets.UTF_8))
                }.getOrNull() ?: continue
                if (message.optString("magic") != MELOX_LAN_MAGIC) continue
                if (message.optString("type") != "announce") continue
                val deviceId = message.optString("deviceId")
                if (deviceId.isBlank() || deviceId == identity.deviceId) continue
                val address = message.optString("address").ifBlank { packet.address.hostAddress.orEmpty() }
                val port = message.optInt("port", MELOX_LAN_DEFAULT_PORT)
                devices[deviceId] = MeloXLanDevice(
                    deviceId = deviceId,
                    name = message.optString("name").ifBlank { "MeloX" },
                    platform = message.optString("platform"),
                    model = message.optString("model"),
                    appVersion = message.optString("appVersion"),
                    address = address,
                    port = port,
                    pinRequired = message.optBoolean("pinRequired", true),
                    trusted = message.optBoolean("trusted"),
                    capabilities = message.optJSONArray("capabilities")?.let { array ->
                        (0 until array.length()).mapNotNull { array.optString(it) }.toSet()
                    }.orEmpty(),
                    playback = MeloXLanPlayback.fromJson(message.optJSONObject("state")),
                )
            }
        } finally {
            runCatching { udp.close() }
        }
        devices.values.toList()
    }
}
