package com.lladlam.melox.dlna

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import com.lladlam.melox.core.network.BoundedLineResult
import com.lladlam.melox.core.network.MeloXHttpClient
import com.lladlam.melox.core.network.readBoundedLineResult
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Request

/** Maximum accepted size of the DLNA request line. */
private const val MAX_DLNA_REQUEST_LINE_BYTES = 8 * 1024

/** Maximum accepted size of a single DLNA header line (and their sum). */
private const val MAX_DLNA_HEADER_BYTES = 16 * 1024


@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class DlnaHttpServer(
    private val context: Context, private val scope: CoroutineScope,
    private val source: DlnaMediaSource, private val peer: InetAddress,
) : AutoCloseable {
    private val server = ServerSocket(0)
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val path = "/cast/${UUID.randomUUID()}.${when (source.mimeType) {
        "audio/flac" -> "flac"; "audio/mp4" -> "m4a"; "audio/wav" -> "wav"; else -> "mp3"
    }}"
    private var job: Job? = null
    @Volatile var requests = 0
        private set
    @Volatile var failure: String? = null
        private set

    fun start(): String {
        // Match the interface used to reach this renderer, including multi-homed phones.
        val localAddress = java.net.DatagramSocket().use {
            it.connect(peer, 1900); it.localAddress
        } ?: error("No LAN address")
        val local = localAddress.hostAddress
        job = scope.launch(Dispatchers.IO) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                if (client.inetAddress != peer || clients.size >= 8) { client.close(); continue }
                clients.add(client)
                launch {
                    try {
                        client.use { runCatching { serve(it) } }
                    } finally {
                        clients.remove(client)
                    }
                }
            }
        }
        return "http://$local:${server.localPort}$path"
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = 10_000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
        val requestLine = when (val line = reader.readBoundedLineResult(MAX_DLNA_REQUEST_LINE_BYTES)) {
            is BoundedLineResult.Line -> line.value
            is BoundedLineResult.TooLong -> { error(socket, 414); return }
            BoundedLineResult.Eof -> return
        }
        val first = requestLine.split(' ')
        val method = first.firstOrNull()
        if (method !in listOf("GET", "HEAD") || first.getOrNull(1) != path) { error(socket, 404); return }
        val headers = mutableMapOf<String, String>()
        var length = 0
        while (true) {
            val line = when (val header = reader.readBoundedLineResult(MAX_DLNA_HEADER_BYTES)) {
                is BoundedLineResult.Line -> header.value
                is BoundedLineResult.TooLong -> { error(socket, 400); return }
                BoundedLineResult.Eof -> return
            }
            length += line.length
            if (length > MAX_DLNA_HEADER_BYTES) { error(socket, 400); return }
            if (line.isBlank()) break
            headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
        }
        requests++
        if (source.uri.startsWith("http://") || source.uri.startsWith("https://")) {
            val request = Request.Builder().url(source.uri)
                .apply {
                    source.headers.forEach { (name, value) -> header(name, value) }
                    header("Accept-Encoding", "identity")
                    headers["range"]?.let { header("Range", it) }
                    if (method == "HEAD") head()
                }.build()
            MeloXHttpClient.thirdPartyAndMedia.newCall(request).execute().use { upstream ->
                failure = if (upstream.isSuccessful) null else "音频源返回 HTTP ${upstream.code}"
                val forwarded = listOf("Content-Length", "Content-Range", "Accept-Ranges").mapNotNull { name ->
                    upstream.header(name)?.let { name to it }
                }.toMap()
                response(socket, upstream.code, upstream.header("Content-Type") ?: source.mimeType, forwarded)
                if (method != "HEAD") upstream.body.byteStream().copyTo(socket.getOutputStream())
            }
        } else {
            val uri = Uri.parse(source.uri)
            val total = if (uri.scheme == "file") File(checkNotNull(uri.path)).length() else {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            }
            if (total <= 0L) { failure = "无法读取本地音频"; error(socket, 502); return }
            val range = byteRange(headers["range"], total)
            if (headers.containsKey("range") && range == null) {
                response(socket, 416, source.mimeType, mapOf("Content-Range" to "bytes */$total", "Content-Length" to "0")); return
            }
            val start = range?.first ?: 0L
            val end = range?.last ?: total - 1L
            val count = end - start + 1
            val extra = mutableMapOf("Content-Length" to count.toString(), "Accept-Ranges" to "bytes")
            if (range != null) extra["Content-Range"] = "bytes $start-$end/$total"
            response(socket, if (range == null) 200 else 206, source.mimeType, extra)
            if (method != "HEAD") {
                val input = DefaultDataSource.Factory(context).createDataSource()
                try {
                    input.open(DataSpec.Builder().setUri(uri).setPosition(start).setLength(count).build())
                    var remaining = count
                    val buffer = ByteArray(32 * 1024)
                    while (remaining > 0 && !socket.isClosed) {
                        val read = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                        if (read <= 0) break
                        socket.getOutputStream().write(buffer, 0, read); remaining -= read
                    }
                } finally { input.close() }
            }
        }
        socket.getOutputStream().flush()
    }

    private fun error(socket: Socket, code: Int) = response(socket, code, "text/plain", mapOf("Content-Length" to "0"))
    private fun response(socket: Socket, code: Int, mime: String, extra: Map<String, String>) {
        val headers = buildString {
            append("HTTP/1.1 $code ${if (code in 200..299) "OK" else "Error"}\r\nContent-Type: $mime\r\nConnection: close\r\n")
            append("transferMode.dlna.org: Streaming\r\ncontentFeatures.dlna.org: DLNA.ORG_OP=01;DLNA.ORG_CI=0\r\n")
            extra.forEach { (key, value) -> append("$key: $value\r\n") }
            append("\r\n")
        }
        socket.getOutputStream().write(headers.toByteArray(Charsets.US_ASCII))
    }
    override fun close() {
        runCatching { server.close() }; job?.cancel()
        clients.forEach { runCatching { it.close() } }; clients.clear()
    }
    companion object {
        fun byteRange(value: String?, total: Long): LongRange? {
            if (value == null || total <= 0 || !value.startsWith("bytes=") || value.contains(',')) return null
            val parts = value.removePrefix("bytes=").split('-')
            if (parts.size != 2) return null
            if (parts[0].isEmpty()) {
                val suffix = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
                return (total - suffix).coerceAtLeast(0L)..total - 1
            }
            val start = parts[0].toLongOrNull() ?: return null
            val end = if (parts[1].isEmpty()) total - 1 else parts[1].toLongOrNull() ?: return null
            return if (start in 0 until total && end >= start) start..minOf(end, total - 1) else null
        }
    }
}
