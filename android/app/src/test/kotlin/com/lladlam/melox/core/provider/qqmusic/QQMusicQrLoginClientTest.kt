package com.lladlam.melox.core.provider.qqmusic

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QQMusicQrLoginClientTest {
    @Test
    fun parsesQrLoginStates() {
        assertEquals(
            QQMusicQrLoginEvent.Waiting,
            parseQQMusicPtuiCallback("ptuiCB('66','0','','0','二维码未失效。','')").event,
        )
        assertEquals(
            QQMusicQrLoginEvent.Scanned,
            parseQQMusicPtuiCallback("ptuiCB('67','0','','0','二维码认证中。','')").event,
        )

        val connected = parseQQMusicPtuiCallback(
            "ptuiCB('0','0','https://graph.qq.com/oauth2.0/login_jump?ptsigx=sig-value&uin=12345678','0','登录成功！','')",
        )
        assertEquals(QQMusicQrLoginEvent.Connected, connected.event)
        assertEquals("12345678", connected.uin)
        assertEquals("sig-value", connected.sigX)
    }

    @Test
    fun parsesWechatQrPageAndPollingStates() {
        assertEquals(
            "wechat-uuid",
            parseQQMusicWechatQrUuid(
                """<img class="qrcode" src="/connect/qrcode/wechat-uuid"/>""",
            ),
        )
        assertEquals(
            QQMusicQrLoginEvent.Waiting,
            parseQQMusicWechatPoll("window.wx_errcode=408; window.wx_code='';").event,
        )
        assertEquals(
            QQMusicQrLoginEvent.Scanned,
            parseQQMusicWechatPoll("window.wx_errcode=404; window.wx_code='';").event,
        )
        val connected = parseQQMusicWechatPoll(
            "window.wx_errcode=405; window.wx_code='wechat-code';",
        )
        assertEquals(QQMusicQrLoginEvent.Connected, connected.event)
        assertEquals("wechat-code", connected.authorizationCode)
        assertEquals(
            QQMusicQrLoginEvent.Rejected,
            parseQQMusicWechatPoll("window.wx_errcode=403;").event,
        )
        assertEquals(
            QQMusicQrLoginEvent.Expired,
            parseQQMusicWechatPoll("window.wx_errcode=402;").event,
        )
    }

    @Test
    fun buildsOfficialWechatCodeExchangeShape() {
        val payload = buildQQMusicWechatLoginPayload("wechat-code")
        assertEquals("qqmusic", payload.getJSONObject("comm").getString("tmeAppID"))
        assertEquals("1", payload.getJSONObject("comm").getString("tmeLoginType"))
        val request = payload.getJSONObject("req")
        assertEquals("music.login.LoginServer", request.getString("module"))
        assertEquals("Login", request.getString("method"))
        assertEquals("wx48db31d50e334801", request.getJSONObject("param").getString("strAppid"))
        assertEquals("wechat-code", request.getJSONObject("param").getString("code"))
    }

    @Test
    fun hash33MatchesQqLoginProtocol() {
        assertEquals(1_968_809_247, qqMusicHash33("example-qrsig", 0))
        assertEquals(193_496_974, qqMusicHash33("key", 5381))
    }

    @Test
    fun credentialCreatesExistingSessionCookieShape() {
        val credential = parseQQMusicQrCredential(
            JSONObject()
                .put("musicid", 12_345_678)
                .put("musickey", "play-key")
                .put("openid", "open-id")
                .put("loginType", 2),
        )

        val cookie = buildQQMusicQrCredentialCookie(credential)
        val session = QQMusicSessionStore.parse(cookie)

        assertEquals("12345678", session.uin)
        assertEquals("play-key", session.musicKey)
        assertTrue(cookie.contains("psrf_qqopenid=open-id"))
        assertTrue(session.isLoggedIn)
    }
}
