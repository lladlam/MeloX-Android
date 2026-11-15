package com.lladlam.melox.core.provider.thirdparty

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSource
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ResolverApiClientTest {
    private val track = MusicTrack(MusicResourceId(MusicSource.Netease, "123"), "", emptyList())
    private val settings = ResolverSettings(ResolverKind.Meting, "https://resolver.test/api.php", enabled = true)

    private fun client(code: Int, body: () -> ResponseBody, requests: AtomicInteger): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code)
                .message("fixture").body(body()).build()
        }.build()

    @Test fun positiveAndNegativeCachesAreBoundedInTime() {
        var now = 10_000L
        val requests = AtomicInteger()
        val api = ResolverApiClient(client(200, { """{"url":"https://cdn.test/a.mp3"}""".toResponseBody("application/json".toMediaType()) }, requests), { now })
        repeat(3) { assertNotNull(api.resolve(settings, track, AudioQualityTier.High)) }
        assertEquals(1, requests.get())
        now += 120_001
        api.resolve(settings, track, AudioQualityTier.High)
        assertEquals(2, requests.get())

        val missing = AtomicInteger()
        val negative = ResolverApiClient(client(200, { """{"url":null}""".toResponseBody("application/json".toMediaType()) }, missing), { now })
        repeat(3) { assertNull(negative.resolve(settings, track, AudioQualityTier.High)) }
        assertEquals(1, missing.get())
        now += 30_001
        assertNull(negative.resolve(settings, track, AudioQualityTier.High))
        assertEquals(2, missing.get())
    }

    @Test fun rateLimitedServiceIsNotHammeredForEveryTrack() {
        var now = 10_000L
        val requests = AtomicInteger()
        val api = ResolverApiClient(client(429, { "{}".toResponseBody("application/json".toMediaType()) }, requests), { now })
        repeat(5) { index -> api.resolve(settings, track.copy(id = MusicResourceId(MusicSource.Netease, "$index")), AudioQualityTier.High) }
        assertEquals(1, requests.get())
        now += 60_001
        api.resolve(settings, track, AudioQualityTier.High)
        assertEquals(2, requests.get())
    }

    @Test fun audioResponseBodyIsNeverReadAsText() {
        val requests = AtomicInteger()
        val api = ResolverApiClient(client(200, { object : ResponseBody() {
            override fun contentType() = "audio/mpeg".toMediaType()
            override fun contentLength() = 100_000_000L
            override fun source(): BufferedSource = throw AssertionError("Audio must not be read in resolver")
            override fun close() = Unit
        } }, requests))
        assertEquals("https://resolver.test/api.php?server=netease&type=url&id=123&br=320&level=exhigh",
            api.resolve(settings, track, AudioQualityTier.High))
        assertEquals("https://resolver.test/api.php?server=netease&type=url&id=123&br=320&level=exhigh",
            api.test(settings, track, AudioQualityTier.High).url)
        assertEquals(2, requests.get())
    }

    @Test fun failedTestsExposeHttpStatusAndResponseBody() {
        for ((status, body) in listOf(200 to "<html>Song unavailable</html>", 403 to """{"error":"Access denied"}""")) {
            val api = ResolverApiClient(client(status, { body.toResponseBody("text/html".toMediaType()) }, AtomicInteger()))
            val result = api.test(settings, track, AudioQualityTier.High)
            assertNull(result.url)
            assertEquals(status, result.httpStatus)
            assertEquals("text/html", result.contentType)
            assertEquals(body, result.responseBody)
            assertFalse(result.responseTruncated)
        }
    }

    @Test fun emptyResponseIsKeptDistinctFromUnreadBinaryContent() {
        val empty = ResolverApiClient(client(200, { "".toResponseBody("text/html".toMediaType()) }, AtomicInteger()))
            .test(settings, track, AudioQualityTier.High)
        assertNull(empty.url)
        assertEquals("", empty.responseBody)
        val image = ResolverApiClient(client(200, { "image".toResponseBody("image/png".toMediaType()) }, AtomicInteger()))
            .test(settings, track, AudioQualityTier.High)
        assertNull(image.url)
        assertNull(image.responseBody)
        assertEquals("image/png", image.contentType)
    }

    @Test fun responsePreviewRedactsRawEncodedAndJsonEscapedCredentials() {
        val credential = "private+key/\"one"
        val encoded = URLEncoder.encode(credential, "UTF-8")
        val escaped = JSONObject.quote(credential).removeSurrounding("\"")
        val body = "raw=$credential; encoded=$encoded; escaped=$escaped"
        val api = ResolverApiClient(client(401, { body.toResponseBody("text/plain".toMediaType()) }, AtomicInteger()))
        val result = api.test(settings.copy(credential = credential), track, AudioQualityTier.High)
        val displayed = result.responseBody!!
        assertNull(result.url)
        assertFalse(displayed.contains(credential))
        assertFalse(displayed.contains(encoded))
        assertFalse(displayed.contains(escaped))
        assertEquals("raw=[redacted]; encoded=[redacted]; escaped=[redacted]", result.responseBody)
    }

    @Test fun responsePreviewsStayBoundedAndMarkTruncation() {
        for (length in listOf(8 * 1024, 70 * 1024)) {
            val api = ResolverApiClient(client(500, { "x".repeat(length).toResponseBody("text/plain".toMediaType()) }, AtomicInteger()))
            val result = api.test(settings, track, AudioQualityTier.High)
            assertNull(result.url)
            assertTrue(result.responseTruncated)
            assertEquals(ResolverResponsePreviewChars, result.responseBody!!.length)
        }
    }

    @Test fun credentialsEmbeddedInEndpointQueryAreAlsoRedacted() {
        val api = ResolverApiClient(client(401, { "token=endpoint-key".toResponseBody("text/plain".toMediaType()) }, AtomicInteger()))
        val result = api.test(settings.copy(endpoint = "https://resolver.test/api.php?token=endpoint-key"), track, AudioQualityTier.High)
        assertEquals("token=[redacted]", result.responseBody)
    }

    @Test fun explicitTestsBypassPlaybackFailureCacheAndNeverLogResponseBodies() {
        val requests = AtomicInteger()
        val logs = mutableListOf<String>()
        val api = ResolverApiClient(client(200, {
            if (requests.get() == 1) "no source".toResponseBody("text/plain".toMediaType())
            else """{"url":"https://cdn.test/fresh.mp3"}""".toResponseBody("application/json".toMediaType())
        }, requests), report = { logs += it })
        assertNull(api.resolve(settings, track, AudioQualityTier.High))
        assertEquals("https://cdn.test/fresh.mp3", api.test(settings, track, AudioQualityTier.High).url)
        assertEquals(2, requests.get())
        assertTrue(logs.isEmpty())
    }

    @Test fun requestErrorsAreShownWithoutLeakingCredentials() {
        val http = OkHttpClient.Builder().addInterceptor { throw IOException("Failed auth=private-key") }.build()
        val result = ResolverApiClient(http).test(settings.copy(credential = "private-key"), track, AudioQualityTier.High)
        assertNull(result.url)
        assertNull(result.httpStatus)
        assertNull(result.responseBody)
        assertEquals("IOException: Failed auth=[redacted]", result.requestError)
    }

    @Test fun hugeAndHtmlResponsesAreRejected() {
        for ((type, body) in listOf("application/json" to "x".repeat(70 * 1024), "text/html" to "<html>https://site.test/not-a-song</html>")) {
            val api = ResolverApiClient(client(200, { body.toResponseBody(type.toMediaType()) }, AtomicInteger()))
            assertNull(api.resolve(settings, track, AudioQualityTier.High))
        }
    }

    @Test fun phpDefaultMimeDoesNotHideValidJson() {
        val api = ResolverApiClient(client(200, {
            """{"data":{"url":"https://cdn.test/song.mp3"}}""".toResponseBody("text/html".toMediaType())
        }, AtomicInteger()))
        assertEquals("https://cdn.test/song.mp3", api.resolve(settings, track, AudioQualityTier.High))
    }

    @Test fun simultaneousRequestsAreCoalesced() {
        val requests = AtomicInteger()
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val http = client(200, {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            "https://cdn.test/a.mp3".toResponseBody("text/plain".toMediaType())
        }, requests)
        val api = ResolverApiClient(http)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<String?> { api.resolve(settings, track, AudioQualityTier.High) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val second = executor.submit<String?> { api.resolve(settings, track, AudioQualityTier.High) }
            release.countDown()
            assertEquals("https://cdn.test/a.mp3", first.get(5, TimeUnit.SECONDS))
            assertEquals("https://cdn.test/a.mp3", second.get(5, TimeUnit.SECONDS))
            assertEquals(1, requests.get())
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun realHttpRedirectReturnsFinalAudioUrlWithoutDownloadingSong() {
        ServerSocket(0, 2, InetAddress.getByName("127.0.0.1")).use { server ->
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            server.soTimeout = 5_000
            val task = executor.submit {
                repeat(2) { index -> server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    val response = if (index == 0) "HTTP/1.1 302 Found\r\nLocation: /audio\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        else "HTTP/1.1 200 OK\r\nContent-Type: audio/mpeg\r\nContent-Length: 100000000\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().write(response.toByteArray())
                    socket.getOutputStream().flush()
                } }
            }
            try {
                val base = "http://127.0.0.1:${server.localPort}"
                val http = com.lladlam.melox.core.network.RestrictedHttpClient.create(OkHttpClient.Builder().build())
                assertEquals("$base/audio", ResolverApiClient(http).resolve(settings.copy(endpoint = "$base/meting/"), track, AudioQualityTier.High))
                task.get(5, TimeUnit.SECONDS)
            } finally { executor.shutdownNow() }
        }
    }
}
