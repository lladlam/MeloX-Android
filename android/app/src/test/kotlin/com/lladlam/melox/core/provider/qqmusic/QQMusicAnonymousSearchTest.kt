package com.lladlam.melox.core.provider.qqmusic

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QQMusicAnonymousSearchTest {
    @Test
    fun desktopFailureFallsBackToAnonymousMobileAndExpandsGroupedTracks() = runBlocking {
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests++
            val body = if (requests == 1) {
                """{"req_0":{"code":2001}}"""
            } else {
                assertEquals("u6.y.qq.com", request.url.host)
                assertNull(request.header("Cookie"))
                assertEquals("QQMusic 20080508(android)", request.header("User-Agent"))
                val raw = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                val payload = JSONObject(raw)
                assertFalse(payload.getJSONObject("comm").has("cv"))
                val key = "music.search.SearchCgiService.DoSearchForQQMusicMobile"
                assertEquals("", payload.getJSONObject(key).getJSONObject("param").getString("searchid"))
                val grouped = song("alternate", "非常简单 feat.榛葉nami")
                val primary = song("primary", "非常简单").put("grp", JSONArray().put(grouped))
                JSONObject().put("code", 0).put(key, JSONObject().put("code", 0)
                    .put("data", JSONObject().put("body", JSONObject()
                        .put("item_song", JSONArray().put(primary))))).toString()
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val result = QQMusicCatalogClient({ QQMusicSession("", "", "") }, client)
            .searchTracks("非常简单", 1, 10)
        assertEquals(2, requests)
        assertEquals(listOf("primary", "alternate"), result.items.map { it.id.value })
    }

    private fun song(mid: String, title: String) = JSONObject()
        .put("id", 123L).put("mid", mid).put("name", title).put("title", title).put("interval", 202)
        .put("singer", JSONArray().put(JSONObject().put("name", "iKz")))
}
