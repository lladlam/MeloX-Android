package com.lladlam.melox.core.provider.qishui

import com.lladlam.melox.core.music.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QishuiProviderTest {
    private val trackId = "7079108541549643812"
    private val trackJson = """{"id":"$trackId","name":"测试歌曲","duration":297240,"artists":[{"id":"a1","name":"歌手"}],"album":{"id":"b1","name":"专辑","url_cover":{"urls":["https://example.com/"],"uri":"cover.jpg"}}}"""
    private val session = QishuiSession(sessionId = "session-test", userId = "u1", nickname = "用户", deviceId = "1234567890123456", installId = "2345678901234567")
    private fun client(body: (Request) -> String) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body(chain.request()).toResponseBody()).build()
    }.build()

    private fun seo(duration: Double = 30.0, encrypted: Boolean = false): JSONObject = JSONObject().put("seo_track", JSONObject().put("track", JSONObject(trackJson)))
        .put("track_player", JSONObject().put("expire_at", 1793000000L).put("video_model", JSONObject()
            .put("video_duration", duration).apply { if (encrypted) put("spade_a", "encrypted") }
            .put("video_list", org.json.JSONArray("""[{"main_url":"https://audio.douyinvod.com/test.m4a","video_meta":{"bitrate":128000,"vtype":"m4a"}}]""")).toString()))

    @Test fun unwrapsSearchFeedWithoutLosingLongIdsOrMilliseconds() {
        val resource = JSONObject("""{"entity":{"track_wrapper":{"track":$trackJson}}}""")
        val track = checkNotNull(QishuiParsing.track(resource))
        assertEquals(MusicResourceId(MusicSource.Qishui, trackId), track.id)
        assertEquals(297240L, track.durationMs)
        assertEquals("歌手", track.artistText)
        assertEquals("https://example.com/cover.jpg", track.artworkUrl)
    }

    @Test fun h5PreviewIsNeverAdvertisedAsFullPlayback() = runBlocking {
        val track = checkNotNull(QishuiParsing.track(JSONObject(trackJson)))
        val result = QishuiProvider({ session }, client { seo().toString() }).resolvePlayback(track, AudioQualityTier.High)
        assertTrue(result is PlaybackResolution.Preview)
        assertEquals(30000L, (result as PlaybackResolution.Preview).durationMs)
    }

    @Test fun fullPlaybackDoesNotForwardSessionToCdn() = runBlocking {
        val provider = QishuiProvider({ session }, client { request ->
            assertEquals("sessionid=session-test", request.header("Cookie"))
            assertEquals("/luna/media-player", request.url.encodedPath)
            val buffer = okio.Buffer()
            request.body!!.writeTo(buffer)
            assertEquals(trackId, JSONObject(buffer.readUtf8()).getString("media_id"))
            JSONObject().put("player_infos", org.json.JSONArray().put(
                seo(297.24).getJSONObject("track_player").put("media_id", trackId),
            )).toString()
        })
        val result = provider.resolvePlayback(checkNotNull(QishuiParsing.track(JSONObject(trackJson))), AudioQualityTier.Lossless)
        assertTrue(result is PlaybackResolution.Playable)
        result as PlaybackResolution.Playable
        assertNull(result.requestHeaders["Cookie"])
        assertEquals(AudioQualityTier.Standard, result.actualQuality)
        assertEquals(1793000000000L, result.expiresAtEpochMs)
    }

    @Test fun encryptedOrUntrustedAudioIsNotResolved() {
        assertNull(QishuiParsing.audio(seo(encrypted = true), 297240L, AudioQualityTier.Standard))
        assertFalse(QishuiParsing.isAudioUrl("https://douyinvod.com.attacker.invalid/a.m4a"))
        assertFalse(QishuiParsing.isAudioUrl("http://audio.douyinvod.com/a.m4a"))
        assertFalse(QishuiParsing.isAudioUrl("https://user:secret@audio.douyinvod.com/a.m4a"))
    }

    @Test fun searchUsesOpaqueUpstreamCursorAndPreservesAllResults() = runBlocking {
        val api = QishuiApiClient({ session }, client { request ->
            val cursor = request.url.queryParameter("cursor")
            assertTrue(cursor == "0" || cursor == "next-token")
            """{"result_groups":[{"data":[{"entity":{"track":$trackJson}}]}],"has_more":${cursor == "0"},"next_cursor":"next-token"}"""
        })
        val first = api.search("测试", 1, 30)
        assertEquals(trackId, first.items.single().id.value)
        assertTrue(first.hasMore)
        assertFalse(api.search("测试", 2, 30).hasMore)
    }

    @Test fun anonymousLibraryRequiresLogin() {
        val api = QishuiApiClient({ QishuiSession() }, client { fail("Must not send signed-out library request"); "{}" })
        assertThrows(java.io.IOException::class.java) { runBlocking { api.userPlaylists(1, 30) } }
    }

    @Test fun storageIdentityRoundTrips() {
        assertEquals(MusicSource.Qishui, MusicSource.fromStorageValue("qishui"))
        assertNotEquals(MusicResourceId(MusicSource.Netease, trackId), MusicResourceId(MusicSource.Qishui, trackId))
    }

    @Test fun qrFlowKeepsCreationCookiesAndExtractsOnlyTheConfirmedSession() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val creating = chain.request().url.encodedPath.endsWith("get_qrcode/")
            if (!creating) assertEquals("csrf=qr-csrf", chain.request().header("Cookie"))
            else assertNull(chain.request().header("Cookie"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .addHeader("Set-Cookie", if (creating) "csrf=qr-csrf; Path=/; Secure" else "sessionid=confirmed-session; Path=/; Secure")
                .body((if (creating) """{"data":{"token":"qr-token","qrcode":"data:image/png;base64,AA==","qrcode_index_url":"https://bff-pc.qishui.com/ucenter_web/app/sdk-next?token=qr-token&uc_sdk=scan-auth"}}"""
                    else """{"data":{"status":"confirmed"}}""").toResponseBody()).build()
        }.build()
        val api = QishuiApiClient({ session }, client)
        val qr = api.createQr()
        assertEquals("https://bff-pc.qishui.com/light/invoke/scan_login?token=qr-token&os=Windows&computer_name=MeloX", qr.link)
        assertEquals("", qr.image)
        val authorized = api.pollQr(qr.token)
        assertEquals("confirmed", authorized.status)
        assertEquals("confirmed-session", authorized.sessionId)
    }

    @Test fun scanLoginUrlUsesTheLiveDesktopEndpoint() {
        val index = "https://bff-pc.qishui.com/ucenter_web/app/sdk-next?next_url=x&qr_source_aid=386088&token=abc_123&uc_sdk=scan-auth"
        assertEquals(
            "https://bff-pc.qishui.com/light/invoke/scan_login?token=abc_123&os=Windows&computer_name=Pixel%208",
            scanLoginUrl(index, "Pixel 8"),
        )
        assertNull(scanLoginUrl("https://example.com/without-token", "Pixel"))
        assertNull(scanLoginUrl("", "Pixel"))
    }

    @Test fun qrConfirmationKeepsFullPassportCookieAndAcceptsRedirect() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val creating = chain.request().url.encodedPath.endsWith("get_qrcode/")
            if (creating) {
                assertNull(chain.request().header("Cookie"))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .addHeader("Set-Cookie", "sessionid=qr-session; Path=/; Domain=.douyin.com")
                    .addHeader("Set-Cookie", "passport_csrf_token=csrf-token; Path=/")
                    .body("""{"data":{"token":"qr-token","qrcode":"data:image/png;base64,AA=="}}""".toResponseBody())
                    .build()
            } else {
                val cookie = chain.request().header("Cookie").orEmpty()
                assertTrue(cookie.contains("sessionid=qr-session"))
                assertTrue(cookie.contains("passport_csrf_token=csrf-token"))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(302).message("Found")
                    .addHeader("Location", "https://api.qishui.com/")
                    .addHeader("Set-Cookie", "sessionid=confirmed-session; Path=/")
                    .addHeader("Set-Cookie", "sid_tt=tt-session; Path=/")
                    .body("".toResponseBody())
                    .build()
            }
        }.build()
        val api = QishuiApiClient({ QishuiSession() }, client)
        val qr = api.createQr()
        val authorized = api.pollQr(qr.token)
        assertEquals("confirmed-session", authorized.sessionId)
        assertTrue(authorized.cookie.contains("sessionid=confirmed-session"))
        assertTrue(authorized.cookie.contains("sid_tt=tt-session"))
    }
}
