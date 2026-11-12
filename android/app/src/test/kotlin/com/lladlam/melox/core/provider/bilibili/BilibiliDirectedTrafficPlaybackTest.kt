package com.lladlam.melox.core.provider.bilibili

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 定向流量只影响「选哪条媒体地址」，不应改变请求身份（Cookie / Referer / User-Agent）。
 */
class BilibiliDirectedTrafficPlaybackTest {
    private val session = BilibiliSession(
        "SESSDATA=test; bili_jct=csrf; DedeUserID=123", "test", "csrf", "123", "", "",
    )

    private val pcdnUrl = "https://xy.mcdn.bilivideo.cn/upgcxcode/1/2/audio.m4s?deadline=4102444800"
    private val officialUrl = "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/1/2/audio.m4s?deadline=4102444800"

    private fun provider(directedTraffic: Boolean): BilibiliProvider {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = when (chain.request().url.encodedPath) {
                "/x/web-interface/nav" -> JSONObject().put("code", 0).put("data", JSONObject()
                    .put("wbi_img", JSONObject()
                        .put("img_url", "https://i0.hdslb.com/bfs/wbi/abcdefghijklmnopqrstuvwxyz.png")
                        .put("sub_url", "https://i0.hdslb.com/bfs/wbi/zyxwvutsrqponmlkjihgfedcba.png")))
                "/x/player/wbi/playurl" -> JSONObject().put("code", 0).put("data", JSONObject()
                    .put("dash", JSONObject().put("audio", JSONArray().put(JSONObject()
                        .put("id", 30232)
                        .put("base_url", pcdnUrl)
                        .put("backup_url", JSONArray().put(officialUrl))
                        .put("bandwidth", 192000)
                        .put("mime_type", "audio/mp4")))))
                else -> throw AssertionError("Unexpected endpoint: ${chain.request().url.encodedPath}")
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toString().toResponseBody()).build()
        }.build()
        return BilibiliProvider(
            sessionProvider = { session },
            httpClient = client,
            directedTrafficProvider = { directedTraffic },
        )
    }

    private val track = MusicTrack(
        MusicResourceId(MusicSource.Bilibili, "BVtest:101"),
        "歌曲",
        emptyList(),
        providerMetadata = ProviderTrackMetadata.Bilibili("BVtest", 101L),
    )

    @Test fun defaultKeepsTheServerPreferredAddress() = runBlocking {
        val result = provider(directedTraffic = false).resolvePlayback(track, AudioQualityTier.High)
        val playable = result as PlaybackResolution.Playable
        assertEquals(pcdnUrl, playable.url)
        assertEquals("https://www.bilibili.com/video/BVtest", playable.requestHeaders["Referer"])
        assertEquals(BilibiliProvider.UserAgent, playable.requestHeaders["User-Agent"])
        assertEquals(session.cookie, playable.requestHeaders["Cookie"])
    }

    @Test fun directedTrafficPrefersTheOfficialCdnBackup() = runBlocking {
        val result = provider(directedTraffic = true).resolvePlayback(track, AudioQualityTier.High)
        val playable = result as PlaybackResolution.Playable
        assertEquals(officialUrl, playable.url)
        // 请求身份保持不变。
        assertEquals("https://www.bilibili.com/video/BVtest", playable.requestHeaders["Referer"])
        assertEquals(BilibiliProvider.UserAgent, playable.requestHeaders["User-Agent"])
        assertEquals(session.cookie, playable.requestHeaders["Cookie"])
        assertTrue(playable.url.contains("bilivideo.com"))
    }
}
