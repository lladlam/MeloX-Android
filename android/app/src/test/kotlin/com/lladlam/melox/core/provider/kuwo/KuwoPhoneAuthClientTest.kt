package com.lladlam.melox.core.provider.kuwo

import java.math.BigInteger
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.json.JSONObject

class KuwoPhoneAuthClientTest {

    @Test
    fun signatureMatchesDocumentedAlgorithm() {
        // Reference implementation from the issue description.
        val mobile = "13800138000"
        val type = "1"
        val tm = "1234567890123"
        val expected = md5Hex(
            md5Hex("imbadboy@!153").uppercase() +
                md5Hex("$mobile$type$tm").uppercase(),
        ).uppercase()

        val actual = KuwoPhoneAuthClient.authParams(mobile, type, tm).secret

        assertEquals(expected, actual)
    }

    @Test
    fun loginAcceptsSuccessCallbackWithMinusOneStatus() {
        val response = JSONObject()
            .put("success", true)
            .put("status", "-1")
            .put("userInfo", JSONObject().put("uid", "123").put("sessionId", "sid-value"))

        val session = KuwoPhoneAuthClient().parseLoginResponse(response)

        assertEquals("123", session.userId)
        assertEquals("sid-value", session.token)
    }

    @Test
    fun minusOneWithoutSuccessIsRejected() {
        val response = JSONObject()
            .put("success", false)
            .put("status", "-1")
            .put("userInfo", JSONObject().put("uid", "123").put("sid", "sid-value"))

        try {
            KuwoPhoneAuthClient().parseLoginResponse(response)
            fail("Expected login failure")
        } catch (_: java.io.IOException) {
            // -1 is meaningful only on the successful callback branch.
        }
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return BigInteger(1, digest).toString(16).padStart(32, '0')
    }
}
