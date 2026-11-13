package com.lladlam.melox.platform.mcp

import android.content.Context
import android.util.Log
import com.lladlam.melox.BuildConfig
import com.lladlam.melox.platform.cli.MeloCliCommands
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "MeloXMcp"
private const val PREFERENCES = "melox_mcp"
private const val KEY_ENABLED = "enabled"
private const val KEY_TOKEN = "token"
private const val DEFAULT_PORT = 8765
private const val MAX_BODY_BYTES = 1_048_576
private const val MAX_CLIENT_WORKERS = 8

/** Process-local lifecycle for the opt-in loopback MCP endpoint. */
object MeloXMcpRuntime {
    private var server: MeloXMcpServer? = null

    fun isEnabled(context: Context): Boolean = preferences(context).getBoolean(KEY_ENABLED, false)

    fun port(): Int = server?.port ?: DEFAULT_PORT

    fun token(context: Context): String {
        val preferences = preferences(context)
        preferences.getString(KEY_TOKEN, null)?.takeIf { it.length >= 32 }?.let { return it }
        val generated = ByteArray(24).also(SecureRandom()::nextBytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        preferences.edit().putString(KEY_TOKEN, generated).apply()
        return generated
    }

    fun endpoint(context: Context): String =
        "http://127.0.0.1:${port()}/mcp?token=${token(context)}"

    /** Returns null on success, otherwise a human-readable failure reason. */
    @Synchronized
    fun setEnabled(context: Context, enabled: Boolean): String? {
        val app = context.applicationContext
        if (!enabled) {
            preferences(app).edit().putBoolean(KEY_ENABLED, false).apply()
            stop()
            return null
        }
        val error = start(app)
        // Keep the persisted flag in sync with reality: a failed start must not
        // leave the settings switch claiming that the server is running.
        preferences(app).edit().putBoolean(KEY_ENABLED, error == null).apply()
        return error
    }

    @Synchronized
    fun startIfEnabled(context: Context) {
        val app = context.applicationContext
        if (!isEnabled(app)) return
        val error = start(app)
        if (error != null) {
            Log.w(TAG, "MCP server failed to start: $error")
            preferences(app).edit().putBoolean(KEY_ENABLED, false).apply()
        }
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
    }

    private fun start(context: Context): String? {
        if (server?.isRunning == true) return null
        server?.stop()
        server = null
        val token = token(context)
        val next = MeloXMcpServer(context, token)
        val error = next.start()
        if (error != null) {
            Log.w(TAG, "MCP server failed to start: $error")
            return error
        }
        server = next
        Log.i(TAG, "MCP server listening on 127.0.0.1:${next.port}")
        return null
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}

internal class MeloXMcpServer(
    private val context: Context,
    private val token: String,
) {
    private val running = AtomicBoolean(false)
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private var socket: ServerSocket? = null
    private var workers: ThreadPoolExecutor? = null
    var port: Int = DEFAULT_PORT
        private set

    val isRunning: Boolean get() = running.get()

    fun start(): String? {
        val server = (DEFAULT_PORT until DEFAULT_PORT + 10).firstNotNullOfOrNull { candidate ->
            runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), candidate))
                }
            }.getOrNull()
        } ?: return "no free loopback port in $DEFAULT_PORT..${DEFAULT_PORT + 9}"
        socket = server
        port = server.localPort
        running.set(true)
        // Bounded worker pool: a client must not be able to spawn an unbounded
        // number of platform threads by opening connections.
        val executor = ThreadPoolExecutor(
            1,
            MAX_CLIENT_WORKERS,
            30L,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(16),
        ) { runnable ->
            Thread(runnable, "melox-mcp-client").apply { isDaemon = true }
        }
        workers = executor
        thread(name = "melox-mcp-accept", isDaemon = true) {
            while (running.get()) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                clients.add(client)
                try {
                    executor.execute {
                        runCatching { handle(client) }.onFailure {
                            Log.w(TAG, "MCP request failed", it)
                        }
                        clients.remove(client)
                        runCatching { client.close() }
                    }
                } catch (rejected: RejectedExecutionException) {
                    clients.remove(client)
                    runCatching { client.close() }
                    Log.w(TAG, "MCP connection rejected: too many concurrent clients")
                }
            }
        }
        return null
    }

    fun stop() {
        running.set(false)
        runCatching { socket?.close() }
        socket = null
        workers?.shutdownNow()
        workers = null
        // Accepted clients keep their socket open for up to the read timeout.
        // Close them so that disabling MCP really terminates live sessions.
        clients.forEach { runCatching { it.close() } }
        clients.clear()
    }

    private fun handle(client: Socket) {
        if (!running.get()) return
        client.soTimeout = 10_000
        val input = BufferedInputStream(client.getInputStream())
        val header = readHeader(input) ?: return
        if (!running.get()) return
        val lines = header.split("\r\n")
        val request = lines.firstOrNull().orEmpty().split(' ')
        if (request.size < 2) return
        val method = request[0]
        val target = request[1]
        val path = target.substringBefore('?')
        val queryToken = target.substringAfter('?', "")
            .split('&')
            .mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) null else part.substring(0, index) to part.substring(index + 1)
            }
            .toMap()["token"]
        val headers = lines.drop(1).mapNotNull { line ->
            val index = line.indexOf(':')
            if (index <= 0) null else line.substring(0, index).trim().lowercase() to
                line.substring(index + 1).trim()
        }.toMap()
        // The endpoint is loopback-only: only loopback browser origins may read
        // responses. Any other Origin receives no CORS grant at all.
        val origin = corsOrigin(headers["origin"])
        val length = headers["content-length"]?.toIntOrNull()?.coerceIn(0, MAX_BODY_BYTES) ?: 0
        val body = readBody(input, length)

        if (method == "OPTIONS") {
            write(client, 204, "", "application/json", origin)
            return
        }
        if (path == "/health") {
            write(client, 200, JSONObject().put("ok", true).put("name", "melox-mcp").toString(), "application/json", origin)
            return
        }
        if (path != "/mcp") {
            write(client, 404, JSONObject().put("error", "not found").toString(), "application/json", origin)
            return
        }
        if (method != "POST") {
            write(client, 405, JSONObject().put("error", "POST required").toString(), "application/json", origin)
            return
        }
        if (!authorized(headers, queryToken)) {
            write(client, 401, JSONObject().put("error", "invalid MCP token").toString(), "application/json", origin)
            return
        }
        if (!running.get()) return
        val reply = runCatching { handleJson(body) }
            .getOrElse { error ->
                Log.w(TAG, "MCP JSON-RPC failure", error)
                JSONObject().put("jsonrpc", "2.0").put("id", JSONObject.NULL)
                    .put("error", JSONObject().put("code", -32603).put("message", error.message ?: "internal error"))
                    .toString()
            }
        if (reply == null) {
            write(client, 202, "", "application/json", origin)
        } else {
            write(client, 200, reply, "application/json", origin)
        }
    }

    private fun authorized(headers: Map<String, String>, queryToken: String?): Boolean {
        val header = headers["authorization"].orEmpty()
        val headerOk = constantTimeEquals(header, "Bearer $token")
        val queryOk = queryToken != null && constantTimeEquals(queryToken, token)
        return headerOk || queryOk
    }

    /** Constant-time comparison so a rejected token leaks no timing signal. */
    private fun constantTimeEquals(actual: String, expected: String): Boolean =
        MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8),
        )

    /** Only loopback browser origins may read MCP responses. */
    private fun corsOrigin(origin: String?): String? {
        val value = origin?.trim().orEmpty()
        if (value.isEmpty()) return null
        return value.takeIf { isLoopbackOrigin(it) }
    }

    private fun isLoopbackOrigin(origin: String): Boolean {
        val authority = origin.substringAfter("://", origin).substringBefore('/')
        val host = when {
            authority.startsWith("[") -> authority.substringBefore(']').removePrefix("[")
            else -> authority.substringBefore(':')
        }
        return host.equals("127.0.0.1", ignoreCase = true) ||
            host.equals("localhost", ignoreCase = true) ||
            host == "::1"
    }

    private fun handleJson(raw: String): String? {
        val value = raw.trim()
        if (value.startsWith("[")) {
            val input = JSONArray(value)
            if (input.length() == 0) {
                return JSONObject().put("jsonrpc", "2.0").put("id", JSONObject.NULL)
                    .put("error", JSONObject().put("code", -32600).put("message", "Invalid Request"))
                    .toString()
            }
            val output = JSONArray()
            for (index in 0 until input.length()) {
                input.optJSONObject(index)?.let { request ->
                    handleOne(request)?.let { output.put(JSONObject(it)) }
                }
            }
            return if (output.length() == 0) null else output.toString()
        }
        val request = JSONObject(value)
        return handleOne(request)?.let { JSONObject(it).toString() }
    }

    /** Returns a JSON-RPC response, or null for a notification. */
    private fun handleOne(request: JSONObject): String? {
        val method = request.optString("method")
        val id = request.opt("id")
        val isNotification = id == null || id == JSONObject.NULL
        if (method == "notifications/initialized" || method == "notifications/cancelled") return null
        val result: JSONObject? = when (method) {
            "initialize" -> JSONObject()
                .put("protocolVersion", requestedProtocolVersion(request))
                .put("capabilities", JSONObject().put("tools", JSONObject().put("listChanged", false)))
                .put("serverInfo", JSONObject().put("name", "melox").put("version", BuildConfig.VERSION_NAME))
            "ping" -> JSONObject()
            "tools/list" -> JSONObject().put("tools", toolDefinitions())
            "tools/call" -> callTool(request)
            "resources/list" -> JSONObject().put("resources", JSONArray())
            "prompts/list" -> JSONObject().put("prompts", JSONArray())
            "logging/setLevel" -> JSONObject()
            else -> null
        }
        if (result == null && method !in setOf(
                "initialize", "ping", "tools/list", "tools/call",
                "resources/list", "prompts/list", "logging/setLevel",
            )
        ) {
            if (isNotification) return null
            return JSONObject().put("jsonrpc", "2.0").put("id", id)
                .put("error", JSONObject().put("code", -32601).put("message", "method not found: $method"))
                .toString()
        }
        if (isNotification) return null
        return JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result ?: JSONObject()).toString()
    }

    private fun requestedProtocolVersion(request: JSONObject): String {
        val requested = request.optJSONObject("params")?.optString("protocolVersion").orEmpty()
        return requested.takeIf { it in setOf("2024-11-05", "2025-03-26", "2025-06-18") }
            ?: "2024-11-05"
    }

    private fun callTool(request: JSONObject): JSONObject {
        val params = request.optJSONObject("params") ?: JSONObject()
        val name = params.optString("name")
        val arguments = params.optJSONObject("arguments") ?: JSONObject()
        val command = when (name) {
            "get_playback_status" -> "status"
            "get_queue" -> "queue"
            "play" -> "play"
            "pause" -> "pause"
            "toggle_playback" -> "toggle"
            "next_track" -> "next"
            "previous_track" -> "previous"
            "stop_playback" -> "stop"
            "seek" -> "seek"
            "set_volume" -> "volume"
            "set_shuffle" -> "shuffle"
            "set_repeat" -> "repeat"
            "search" -> "search"
            "search_and_play" -> "search-play"
            else -> null
        }
        if (command == null) {
            return errorContent("unknown tool: $name")
        }
        val args = JSONObject(arguments.toString())
        when (name) {
            "seek" -> args.put("value", arguments.optLong("positionMs", -1L))
            "set_volume" -> if (arguments.has("volume")) {
                args.put("value", arguments.optDouble("volume"))
            }
            "set_shuffle" -> args.put("value", if (arguments.optBoolean("enabled", false)) "on" else "off")
            "set_repeat" -> args.put("value", arguments.optString("mode", "off"))
            "search_and_play" -> args.put("index", arguments.optInt("index", 0))
        }
        return runCatching {
            val payload = runBlocking {
                kotlinx.coroutines.withTimeout(15_000L) {
                    MeloCliCommands.execute(context, command, args)
                }
            }
            JSONObject()
                .put("content", JSONArray().put(JSONObject()
                    .put("type", "text")
                    .put("text", payload.toString())))
                .put("structuredContent", payload)
                .put("isError", false)
        }.getOrElse { error ->
            errorContent(error.message ?: error.javaClass.simpleName)
        }
    }

    private fun errorContent(message: String): JSONObject = JSONObject()
        .put("content", JSONArray().put(JSONObject()
            .put("type", "text")
            .put("text", message)))
        .put("isError", true)

    private fun toolDefinitions(): JSONArray = JSONArray().apply {
        put(tool("get_playback_status", "Get current playback status: playing state, media, position, volume, shuffle and repeat."))
        put(tool("get_queue", "Get the current playback queue."))
        put(tool("play", "Start or resume playback."))
        put(tool("pause", "Pause playback."))
        put(tool("toggle_playback", "Toggle play and pause."))
        put(tool("next_track", "Skip to the next track."))
        put(tool("previous_track", "Go to the previous track."))
        put(tool("stop_playback", "Stop playback."))
        put(tool("seek", "Seek the current track.", objectSchema(
            "positionMs" to propertySchema("integer", "Target position in milliseconds", minimum = 0),
        ), listOf("positionMs")))
        put(tool("set_volume", "Set player volume (0–1).", objectSchema(
            "volume" to propertySchema("number", "Volume from 0 to 1", minimum = 0, maximum = 1),
        ), listOf("volume")))
        put(tool("set_shuffle", "Enable or disable shuffle.", objectSchema(
            "enabled" to propertySchema("boolean", "Whether shuffle is enabled"),
        ), listOf("enabled")))
        put(tool("set_repeat", "Set repeat mode.", objectSchema(
            "mode" to JSONObject().put("type", "string").put("enum", JSONArray(listOf("off", "one", "all"))),
        ), listOf("mode")))
        put(tool("search", "Search songs from a music source.", objectSchema(
            "query" to propertySchema("string", "Search query"),
            "source" to propertySchema("string", "Source storage id, default netease"),
        ), listOf("query")))
        put(tool("search_and_play", "Search a source and replace the queue with the results.", objectSchema(
            "query" to propertySchema("string", "Search query"),
            "source" to propertySchema("string", "Source storage id, default netease"),
            "index" to propertySchema("integer", "Zero-based result index", minimum = 0),
        ), listOf("query")))
    }

    private fun tool(
        name: String,
        description: String,
        inputSchema: JSONObject = objectSchema(),
        required: List<String> = emptyList(),
    ): JSONObject {
        if (required.isNotEmpty()) inputSchema.put("required", JSONArray(required))
        return JSONObject().put("name", name).put("description", description).put("inputSchema", inputSchema)
    }

    private fun objectSchema(vararg properties: Pair<String, JSONObject>): JSONObject {
        val values = JSONObject()
        properties.forEach { (name, schema) -> values.put(name, schema) }
        return JSONObject().put("type", "object").put("properties", values).put("additionalProperties", false)
    }

    private fun propertySchema(
        type: String,
        description: String,
        minimum: Number? = null,
        maximum: Number? = null,
    ): JSONObject = JSONObject().put("type", type).put("description", description).apply {
        minimum?.let { put("minimum", it) }
        maximum?.let { put("maximum", it) }
    }

    private fun readHeader(input: InputStream): String? {
        val output = ByteArrayOutputStream()
        var matched = 0
        val terminator = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        while (output.size() < 64 * 1024) {
            val value = input.read()
            if (value < 0) return null
            output.write(value)
            if (value.toByte() == terminator[matched]) {
                matched++
                if (matched == terminator.size) return output.toString(Charsets.UTF_8.name())
            } else {
                matched = if (value.toByte() == terminator[0]) 1 else 0
            }
        }
        return null
    }

    private fun readBody(input: InputStream, length: Int): String {
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(bytes, read, length - read)
            if (count <= 0) break
            read += count
        }
        return String(bytes, 0, read, Charsets.UTF_8)
    }

    private fun write(client: Socket, status: Int, body: String, contentType: String, origin: String? = null) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            202 -> "Accepted"
            204 -> "No Content"
            401 -> "Unauthorized"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Error"
        }
        val header = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Connection: close\r\n")
            if (origin != null) {
                append("Access-Control-Allow-Origin: ").append(origin).append("\r\n")
                append("Vary: Origin\r\n")
            }
            append("Access-Control-Allow-Headers: authorization, content-type\r\n")
            append("Access-Control-Allow-Methods: POST, GET, OPTIONS\r\n")
            append("\r\n")
        }
        client.getOutputStream().apply {
            write(header.toByteArray(Charsets.UTF_8))
            if (bytes.isNotEmpty()) write(bytes)
            flush()
        }
    }
}

@Suppress("unused")
private fun Context.mcpPreferences() =
    applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
