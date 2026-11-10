package com.lladlam.melox.platform.mcp

import android.content.ContextWrapper
import java.net.Socket
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
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
