package com.lladlam.melox.core.network

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NeteaseLyricsFallbackTest {
    @Test
    fun instrumentalResponseFallsBackToPublicLyricsWithoutCookies() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = client(requests) { request ->
            if (request.method == "POST") lyrics("[00:00.00]此歌曲为没有填词的纯音乐，请您欣赏")
            else lyrics("[00:01.00]First line\n[00:02.00]Second line")
        }

        val document = client.lyrics(2607641032L)

        assertEquals(listOf("First line", "Second line"), document.lines.map { it.text })
        assertEquals(2, requests.size)
        assertNotNull(requests[0].header("Cookie"))
        assertEquals("music.163.com", requests[1].url.host)
        assertEquals("2607641032", requests[1].url.queryParameter("id"))
        assertNull(requests[1].header("Cookie"))
    }

    @Test
    fun validPrimaryLyricsDoNotTriggerAnotherRequest() = runBlocking {
        val requests = mutableListOf<Request>()
        val document = client(requests) { lyrics("[00:01.00]Actual lyric") }.lyrics(1L)
        assertEquals("Actual lyric", document.lines.single().text)
        assertEquals(1, requests.size)
    }

    @Test
    fun primaryNetworkFailureStillUsesPublicEndpoint() = runBlocking {
        val requests = mutableListOf<Request>()
        val document = client(requests) {
            if (it.method == "POST") throw IOException("primary unavailable")
            lyrics("[00:01.00]Fallback lyric")
        }.lyrics(1L)
        assertEquals("Fallback lyric", document.lines.single().text)
        assertEquals(2, requests.size)
    }

    @Test
    fun bothEndpointsReturningPlaceholdersProduceNoLyrics() = runBlocking {
        val document = client(mutableListOf()) { lyrics("[00:00.00]纯音乐，请欣赏") }.lyrics(1L)
        assertTrue(document.lines.isEmpty())
    }

    @Test
    fun publicEndpointCanReturnUntimedWordsInTheLyricField() = runBlocking {
        val document = client(mutableListOf()) {
            if (it.method == "POST") lyrics("[00:00.00]纯音乐，请欣赏")
            else lyrics("First line\nSecond line")
        }.lyrics(2607641032L)
        assertTrue(document.isUnsynchronized)
        assertEquals(2, document.lines.size)
    }

    private fun lyrics(text: String): String = JSONObject()
        .put("code", 200)
        .put("lrc", JSONObject().put("lyric", text))
        .toString()

    private fun client(requests: MutableList<Request>, body: (Request) -> String): NeteaseSearchClient =
        NeteaseSearchClient(
            cookieProvider = { "MUSIC_U=test-session" },
            httpClient = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                requests += request
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .body(body(request).toResponseBody("application/json".toMediaType()))
                    .build()
            }.build(),
        )
}
