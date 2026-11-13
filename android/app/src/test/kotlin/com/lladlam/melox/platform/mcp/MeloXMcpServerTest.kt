package com.lladlam.melox.platform.mcp

import android.content.ContextWrapper
import java.io.IOException
import java.net.Socket
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MeloXMcpServerTest {
    private lateinit var server: MeloXMcpServer

    @Before
    fun setUp() {
        server = MeloXMcpServer(ContextWrapper(null), "test-token-0123456789abcdef")
        assertNull(server.start())
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun initializeAdvertisesTools() {
        val response = post("""
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
              "protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}
            }}
        """.trimIndent(), authorized = true)
        assertEquals("2.0", response.optString("jsonrpc"))
        assertEquals("2025-06-18", response.optJSONObject("result").optString("protocolVersion"))
        assertTrue(response.optJSONObject("result").optJSONObject("capabilities").has("tools"))
    }

    @Test
    fun toolsListContainsPlaybackStatus() {
        val response = post(
            """{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""",
            authorized = true,
        )
        val tools = response.optJSONObject("result").optJSONArray("tools")
        assertTrue((0 until tools.length()).any { tools.optJSONObject(it).optString("name") == "get_playback_status" })
    }

    @Test
    fun missingTokenIsRejectedBeforeJsonRpc() {
        val response = post(
            """{"jsonrpc":"2.0","id":3,"method":"tools/list"}""",
            authorized = false,
        )
        assertEquals("invalid MCP token", response.optString("error"))
    }

    @Test
    fun unknownMethodReturnsJsonRpcError() {
        val response = post(
            """{"jsonrpc":"2.0","id":4,"method":"not/a/method"}""",
            authorized = true,
        )
        assertEquals(-32601, response.optJSONObject("error").optInt("code"))
    }

    @Test
    fun stopTerminatesLiveClientConnections() {
        val socket = Socket("127.0.0.1", server.port)
        socket.soTimeout = 5_000
        try {
            socket.getOutputStream().apply {
                write("POST /mcp HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 10\r\n\r\n".toByteArray(Charsets.UTF_8))
                flush()
            }
            server.stop()
            // Disabling MCP must drop live sessions instead of letting them
            // stay usable for the remaining read timeout.
            val closed = try {
                socket.getInputStream().read() < 0
            } catch (_: IOException) {
                true
            }
            assertTrue("stop() should close accepted client sockets", closed)
        } finally {
            runCatching { socket.close() }
        }
    }

    @Test
    fun foreignOriginIsNotGrantedCorsAccess() {
        val raw = rawResponse(
            """{"jsonrpc":"2.0","id":9,"method":"ping"}""",
            origin = "https://evil.example",
        )
        assertFalse(raw.contains("Access-Control-Allow-Origin: *"))
        assertFalse(raw.contains("evil.example"))
    }

    @Test
    fun loopbackOriginIsEchoed() {
        val raw = rawResponse(
            """{"jsonrpc":"2.0","id":10,"method":"ping"}""",
            origin = "http://127.0.0.1:5173",
        )
        assertTrue(raw.contains("Access-Control-Allow-Origin: http://127.0.0.1:5173"))
    }

    @Test
    fun mutatedTokenIsRejected() {
        val raw = rawResponse(
            """{"jsonrpc":"2.0","id":11,"method":"ping"}""",
            token = "test-token-0123456789abcdee",
        )
        assertTrue(raw.startsWith("HTTP/1.1 401"))
    }

    @Test
    fun manyConcurrentConnectionsStayBounded() {
        val sockets = mutableListOf<Socket>()
        try {
            repeat(40) {
                val socket = Socket("127.0.0.1", server.port)
                socket.soTimeout = 2_000
                sockets += socket
                val body = """{"jsonrpc":"2.0","id":1,"method":"ping"}"""
                socket.getOutputStream().apply {
                    write(
                        ("POST /mcp?token=test-token-0123456789abcdef HTTP/1.1\r\n" +
                            "Host: 127.0.0.1\r\nContent-Type: application/json\r\n" +
                            "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n" +
                            body).toByteArray(Charsets.UTF_8),
                    )
                    flush()
                }
            }
            sockets.forEach { socket -> runCatching { socket.getInputStream().read() } }
        } finally {
            sockets.forEach { socket -> runCatching { socket.close() } }
        }
        // The server must still serve a normal request afterwards.
        var response: JSONObject? = null
        var attempt = 0
        while (response == null && attempt < 8) {
            response = runCatching {
                post("""{"jsonrpc":"2.0","id":12,"method":"ping"}""", authorized = true)
            }.getOrNull()
            if (response == null) Thread.sleep(250)
            attempt++
        }
        assertNotNull("server should stay responsive after a connection burst", response)
        assertEquals("2.0", response!!.optString("jsonrpc"))
    }

    private fun rawResponse(body: String, token: String? = null, origin: String? = null): String {
        Socket("127.0.0.1", server.port).use { socket ->
            socket.soTimeout = 5_000
            val bytes = body.toByteArray(Charsets.UTF_8)
            val path = if (token != null) "/mcp?token=$token" else "/mcp"
            val originHeader = if (origin != null) "Origin: $origin\r\n" else ""
            val headers = "POST $path HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: application/json\r\n" +
                originHeader +
                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().apply {
                write(headers.toByteArray(Charsets.UTF_8))
                write(bytes)
                flush()
            }
            return socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun post(body: String, authorized: Boolean): JSONObject {
        Socket("127.0.0.1", server.port).use { socket ->
            socket.soTimeout = 5_000
            val path = if (authorized) "/mcp?token=test-token-0123456789abcdef" else "/mcp"
            val bytes = body.toByteArray(Charsets.UTF_8)
            val headers = "POST $path HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().apply {
                write(headers.toByteArray(Charsets.UTF_8))
                write(bytes)
                flush()
            }
            val raw = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            return JSONObject(raw.substringAfter("\r\n\r\n"))
        }
    }
}
