package com.lladlam.melox.core.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteasePhoneAuthClientTest {
    @Test
    fun mergesSetCookieValuesAndRemovesExpiredValues() {
        val merged = mergeNeteaseResponseCookies(
            cookieHeader = "MUSIC_U=old; os=ios; stale=value",
            setCookieHeaders = listOf(
                "MUSIC_U=new-token; Path=/; HttpOnly",
                "__csrf=csrf-token; Path=/; Secure",
                "stale=; Max-Age=0; Path=/",
            ),
        )

        assertEquals("MUSIC_U=new-token; __csrf=csrf-token; os=ios", merged)
    }

    @Test
    fun recognizesRiskChallengeWithoutLeakingServerFields() {
        val message = neteasePhoneAuthError(
            JSONObject().put("code", 10002).put("redirectUrl", "https://example.invalid/check")
                .put("checkToken", "secret"),
        )

        assertTrue(message.orEmpty().contains("安全验证"))
        assertTrue(message.orEmpty().contains("网页登录"))
        assertTrue(!message.orEmpty().contains("secret"))

        val nestedMessage = neteasePhoneAuthError(
            JSONObject().put("code", 400).put("data", JSONObject().put("redirectUrl", "https://example.invalid/check")),
        )
        assertTrue(nestedMessage.orEmpty().contains("安全验证"))
    }

    @Test
    fun acceptsSuccessAndUsesOrdinaryServerError() {
        assertNull(neteasePhoneAuthError(JSONObject().put("code", 200)))
        assertEquals("验证码错误", neteasePhoneAuthError(JSONObject().put("code", 502).put("message", "验证码错误")))
    }
}
