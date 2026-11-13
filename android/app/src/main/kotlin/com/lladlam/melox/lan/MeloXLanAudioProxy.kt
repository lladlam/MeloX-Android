package com.lladlam.melox.lan

import android.content.Context
import android.net.Uri
import android.util.Log
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.network.BoundedLineResult
import com.lladlam.melox.core.network.MeloXHttpClient
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.network.readBoundedLineResult
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Request

private const val TAG = "MeloXLanAudioProxy"

/** Cap concurrent proxy connections so a LAN peer cannot exhaust file descriptors. */
private const val MAX_PROXY_CLIENTS = 8

/** Hard limit for the HTTP request line and each header line. */
private const val MAX_PROXY_REQUEST_BYTES = 8 * 1024

/**
 * Host-side, credential-free audio proxy for Together guests.
 *
 * The guest receives only an opaque token. The host resolves the track with its
 * own account (or serves a local download), and never exposes the NetEase
 * cookie. A token is bound to the guest address and the shared queue, expires
 * after 10 seconds if unused, and is revoked when the session ends.
 */
internal class MeloXLanAudioProxy(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val app = context.applicationContext
    private val downloads = MeloXDownloadStore.get(app)
    private val search = NeteaseSearchClient(
        httpClient = MeloXHttpClient.shared,
        cookieProvider = { NeteaseSessionStore.readCookie(app) },
    )
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val tokens = ConcurrentHashMap<String, RelayToken>()
    private val clients = CopyOnWriteArraySet<Socket>()
    private val clientCount = AtomicInteger(0)

    private data class RelayToken(
        val guestAddress: String,
        val songIds: Set<Long>,
        var used: Boolean = false,
        var expiresAt: Long = System.currentTimeMillis() + 10_000L,
        val revoked: AtomicBoolean = AtomicBoolean(false),
    )

    @Synchronized fun issue(guestAddress: String, queue: List<MeloXLanTrack>): MeloXLanRelay? {
        val ip = localIpv4Address() ?: return null
        val socket = ensureServer() ?: return null
        val songIds = queue.mapNotNull { it.id.toLongOrNull()?.takeIf { id -> id > 0L } }.toSet()
        if (songIds.isEmpty()) return null
        val token = ByteArray(24).also(SecureRandom()::nextBytes)
            .joinToString("") { "%02x".format(it) }
        tokens[token] = RelayToken(guestAddress, songIds)
        val base = "http://$ip:${socket.localPort}"
        // A token that is not used for 10 seconds is dead; the guest has to
        // connect with it immediately after Together starts.
        scope.launch {
            delay(10_000L)
            val current = tokens[token] ?: return@launch
            if (!current.used) tokens.remove(token)
        }
        return MeloXLanRelay(base, token)
    }

    fun revoke(token: String?) {
        token ?: return
        tokens.remove(token)?.revoked?.set(true)
    }

    fun stop() {
        tokens.values.forEach { it.revoked.set(true) }
        tokens.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        acceptThread?.join(500)
        acceptThread = null
    }

    private fun ensureServer(): ServerSocket? {
        serverSocket?.takeIf { !it.isClosed }?.let { return it }
        val socket = runCatching { ServerSocket(0) }.getOrElse {
            Log.w(TAG, "Unable to open audio proxy", it)
            return null
        }
        serverSocket = socket
        acceptThread = thread(name = "MeloXLanAudioProxy") {
            while (serverSocket === socket && !socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                if (clientCount.incrementAndGet() > MAX_PROXY_CLIENTS) {
                    clientCount.decrementAndGet()
                    runCatching { client.close() }
                    continue
                }
                clients += client
                scope.launch(Dispatchers.IO) {
                    try {
                        handle(client)
                    } finally {
                        clients -= client
                        clientCount.decrementAndGet()
                    }
                }
            }
        }
        return socket
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 10_000
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val requestLine = when (val line = reader.readBoundedLineResult(MAX_PROXY_REQUEST_BYTES)) {
                    is BoundedLineResult.Line -> line.value
                    is BoundedLineResult.TooLong -> {
                        runCatching { writeError(socket, 431, "Request line too long") }
                        return
                    }
                    BoundedLineResult.Eof -> return
                }
                val parts = requestLine.split(' ')
                if (parts.size < 2 || parts[0] != "GET") {
                    writeError(socket, 405, "Method Not Allowed")
                    return
                }
                val uri = Uri.parse("http://localhost${parts[1]}")
                if (uri.path != "/audio") {
                    writeError(socket, 404, "Not Found")
                    return
                }
                val tokenValue = uri.getQueryParameter("token").orEmpty()
                val token = tokens[tokenValue]
                if (token == null || token.revoked.get()) {
                    writeError(socket, 403, "Invalid token")
                    return
                }
                val remote = runCatching { socket.inetAddress.hostAddress.orEmpty() }.getOrDefault("")
                if (token.guestAddress.isNotBlank() && remote != token.guestAddress) {
                    writeError(socket, 403, "Token is bound to another device")
                    return
                }
                val now = System.currentTimeMillis()
                if (now > token.expiresAt) {
                    tokens.remove(tokenValue)
                    writeError(socket, 410, "Token expired")
                    return
                }
                val songId = uri.getQueryParameter("songId")?.toLongOrNull() ?: 0L
                if (songId <= 0L || songId !in token.songIds) {
                    writeError(socket, 403, "Song is not part of this session")
                    return
                }
                token.used = true
                token.expiresAt = now + 6 * 60 * 60 * 1000L
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = when (val header = reader.readBoundedLineResult(MAX_PROXY_REQUEST_BYTES)) {
                        is BoundedLineResult.Line -> header.value
                        is BoundedLineResult.TooLong -> {
                            runCatching { writeError(socket, 431, "Header line too long") }
                            return
                        }
                        BoundedLineResult.Eof -> break
                    }
                    if (line.isBlank()) break
                    val separator = line.indexOf(':')
                    if (separator > 0) {
                        headers[line.substring(0, separator).trim().lowercase()] =
                            line.substring(separator + 1).trim()
                    }
                }
                val local = downloads.localAudioFile(songId)
                if (local != null) {
                    serveFile(socket, local, headers["range"])
                } else {
                    serveRemote(socket, songId, headers["range"])
                }
            } catch (error: Exception) {
                Log.w(TAG, "Audio proxy request failed", error)
                runCatching { writeError(socket, 502, "Proxy error") }
            }
        }
    }

    private fun serveFile(socket: Socket, file: File, rangeHeader: String?) {
        val contentType = when (file.extension.lowercase()) {
            "flac" -> "audio/flac"
            "m4a", "aac" -> "audio/mp4"
            "ogg", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            else -> "audio/mpeg"
        }
        val total = file.length()
        val range = parseRange(rangeHeader, total)
        val start = range?.first ?: 0L
        val end = range?.second ?: (total - 1L)
        val length = (end - start + 1L).coerceAtLeast(0L)
        val output = DataOutputStream(socket.getOutputStream())
        val status = if (range != null) "206 Partial Content" else "200 OK"
        output.write("HTTP/1.1 $status\r\n".toByteArray())
        output.write("Content-Type: $contentType\r\n".toByteArray())
        output.write("Accept-Ranges: bytes\r\n".toByteArray())
        output.write("Content-Length: $length\r\n".toByteArray())
        if (range != null) output.write("Content-Range: bytes $start-$end/$total\r\n".toByteArray())
        output.write("Connection: close\r\n\r\n".toByteArray())
        RandomAccessFile(file, "r").use { input ->
            input.seek(start)
            val buffer = ByteArray(64 * 1024)
            var remaining = length
            while (remaining > 0L) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read <= 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
        }
        output.flush()
    }

    private fun serveRemote(socket: Socket, songId: Long, rangeHeader: String?) {
        val url = search.playbackUrlBlocking(songId)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
            .header("Referer", "https://music.163.com/")
            .apply { rangeHeader?.let { header("Range", it) } }
            .build()
        MeloXHttpClient.shared.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                writeError(socket, response.code, "Upstream error")
                return
            }
            val output = DataOutputStream(socket.getOutputStream())
            output.write("HTTP/1.1 ${response.code} ${response.message}\r\n".toByteArray())
            response.header("Content-Type")?.let { output.write("Content-Type: $it\r\n".toByteArray()) }
            response.header("Content-Length")?.let { output.write("Content-Length: $it\r\n".toByteArray()) }
            response.header("Content-Range")?.let { output.write("Content-Range: $it\r\n".toByteArray()) }
            response.header("Accept-Ranges")?.let { output.write("Accept-Ranges: $it\r\n".toByteArray()) }
            output.write("Connection: close\r\n\r\n".toByteArray())
            val input = response.body.byteStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
            }
            output.flush()
        }
    }

    private fun parseRange(value: String?, total: Long): Pair<Long, Long>? {
        value ?: return null
        if (!value.startsWith("bytes=")) return null
        val spec = value.removePrefix("bytes=").substringBefore(',')
        val start = spec.substringBefore('-').toLongOrNull() ?: return null
        val end = spec.substringAfter('-', "").toLongOrNull() ?: (total - 1L)
        if (start !in 0 until total || end < start) return null
        return start to end.coerceAtMost(total - 1L)
    }

    private fun writeError(socket: Socket, code: Int, message: String) {
        val body = message.toByteArray(Charsets.UTF_8)
        val output = DataOutputStream(socket.getOutputStream())
        output.write("HTTP/1.1 $code $message\r\n".toByteArray())
        output.write("Content-Type: text/plain; charset=utf-8\r\n".toByteArray())
        output.write("Content-Length: ${body.size}\r\n".toByteArray())
        output.write("Connection: close\r\n\r\n".toByteArray())
        output.write(body)
        output.flush()
    }
}
