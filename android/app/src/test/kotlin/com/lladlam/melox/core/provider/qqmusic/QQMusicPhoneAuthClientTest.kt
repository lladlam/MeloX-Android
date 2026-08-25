package com.lladlam.melox.core.provider.qqmusic

import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QQMusicPhoneAuthClientTest {
    @Test
    fun sendPayloadMatchesLoginServerContract() {
        val payload = buildQQMusicSendCodePayload("13800138000")
        val comm = payload.getJSONObject("comm")
        val request = payload.getJSONObject("music.login.LoginServer")
        val param = request.getJSONObject("param")

        assertEquals(24, comm.getInt("ct"))
        assertEquals(20050009, comm.getInt("cv"))
        assertEquals(20050009, comm.getInt("v"))
        assertEquals("json", comm.getString("format"))
        assertEquals("music.login.LoginServer", request.getString("module"))
        assertEquals("SendPhoneAuthCode", request.getString("method"))
        assertEquals("qqmusic", param.getString("tmeAppid"))
        assertEquals("13800138000", param.getString("phoneNo"))
        assertEquals("86", param.getString("areaCode"))
    }

    @Test
    fun loginPayloadMatchesLoginServerContract() {
        val request = buildQQMusicPhoneLoginPayload("13800138000", "123456")
            .getJSONObject("music.login.LoginServer")
        val param = request.getJSONObject("param")

        assertEquals("Login", request.getString("method"))
        assertEquals("123456", param.getString("code"))
        assertEquals("13800138000", param.getString("phoneNo"))
        assertEquals(1, param.getInt("loginMode"))
        assertFalse(param.has("areaCode"))
    }

    @Test
    fun parsesSuccessfulSessionAndLimitedCaseVariants() {
        val response = JSONObject(
            """{"CODE":0,"data":{"result":{"Music.Login.LoginServer":{"Code":0,"Data":{"MusicId":"12345","MusicKey":"key-value","refresh_Key":"refresh-value"}}}}}""",
        )

        val cookie = parseQQMusicPhoneAuthResponse(response, requireSession = true)

        assertEquals("key-value", QQMusicSessionStore.parse(cookie).musicKey)
        assertEquals("12345", QQMusicSessionStore.parse(cookie).uin)
        assertTrue(cookie.contains("refresh_key=refresh-value"))
    }

    @Test
    fun mergesSetCookieAndRemovesExpiredCookie() {
        val response = successfulResponse(JSONObject().put("str_musicid", "24680").put("musickey", "body-key"))
        val cookie = parseQQMusicPhoneAuthResponse(
            response = response,
            existingCookie = "stale=value; token=old",
            setCookieHeaders = listOf(
                "qm_keyst=cookie-key; Path=/; Secure; HttpOnly",
                "token=new; Path=/",
                "stale=; Max-Age=0; Path=/",
            ),
            requireSession = true,
        )

        assertEquals("cookie-key", QQMusicSessionStore.parse(cookie).musicKey)
        assertEquals("24680", QQMusicSessionStore.parse(cookie).uin)
        assertTrue(cookie.contains("token=new"))
        assertFalse(cookie.contains("stale="))
    }

    @Test
    fun rejectsOuterAndBusinessErrorsUsingServerMessages() {
        val outer = assertThrows(IOException::class.java) {
            parseQQMusicPhoneAuthResponse(
                JSONObject().put("code", 100).put("message", "请求无效"),
                requireSession = false,
            )
        }
        assertEquals("请求无效", outer.message)

        val business = assertThrows(IOException::class.java) {
            parseQQMusicPhoneAuthResponse(
                JSONObject().put("code", 0).put(
                    "music.login.LoginServer",
                    JSONObject().put("code", 1).put("data", JSONObject().put("errTip", "验证码错误")),
                ),
                requireSession = false,
            )
        }
        assertEquals("验证码错误", business.message)
    }

    @Test
    fun exposesSecurityUrlOnlyOnDedicatedException() {
        val error = assertThrows(QQMusicSecurityChallengeException::class.java) {
            parseQQMusicPhoneAuthResponse(
                JSONObject().put("code", 0).put(
                    "music.login.LoginServer",
                    JSONObject().put("code", 20276).put("errMsg", "robot defense").put(
                        "data",
                        JSONObject().put("securityURL", "https://security.example.test/challenge?token=secret"),
                    ),
                ),
                requireSession = false,
            )
        }

        assertTrue(error.securityUrl.startsWith("https://security.example.test/"))
        assertFalse(error.message.orEmpty().contains("secret"))
    }

    @Test
    fun rejectsLoginWithoutMusicKey() {
        val error = assertThrows(IOException::class.java) {
            parseQQMusicPhoneAuthResponse(
                successfulResponse(JSONObject().put("musicid", 12345)),
                requireSession = true,
            )
        }

        assertTrue(error.message.orEmpty().contains("音乐密钥"))
        assertTrue(error.message.orEmpty().contains("网页登录"))
    }

    private fun successfulResponse(data: JSONObject): JSONObject = JSONObject()
        .put("code", 0)
        .put(
            "music.login.LoginServer",
            JSONObject().put("code", 0).put("data", data),
        )
}
