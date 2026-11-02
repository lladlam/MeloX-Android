package com.lladlam.melox.core.provider.jellyfin

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Collections
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class JellyfinHttpTest {
    @Test
    fun authorizedHttpLoginAndSubsequentSessionRequestsUseOriginalHost() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = Collections.synchronizedList(mutableListOf<Triple<String, String?, String?>>())
        server.createContext("/") { exchange ->
            requests.add(Triple(exchange.requestURI.path, exchange.requestHeaders.getFirst("Host"), exchange.requestHeaders.getFirst("X-Emby-Token")))
            exchange.requestBody.readBytes()
            val body = when (exchange.requestURI.path) {
                "/Users/AuthenticateByName" -> """{"User":{"Id":"user","Name":"test"},"AccessToken":"token"}"""
                "/System/Info/Public" -> """{"Id":"server","ServerName":"Jellyfin"}"""
                else -> """{"Items":[]}"""
            }.toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val base = OkHttpClient.Builder().dns(Dns { listOf(InetAddress.getByName("127.0.0.1")) }).build()
            val url = "http://jellyfin.test:${server.address.port}"
            val session = JellyfinApiClient(base, trustUserServer = true).authenticate(url, "test", "password")
            assertTrue(session.trustUserServer)
            JellyfinApiClient(base).get(session, "/Users/user/Items")
            assertEquals(3, requests.size)
            assertTrue(requests.all { it.second == "jellyfin.test:${server.address.port}" })
            assertEquals("token", requests.last().third)
            assertTrue(runCatching { JellyfinApiClient(base).authenticate(url, "test", "password") }.isFailure)
        } finally {
            server.stop(0)
        }
    }
}
