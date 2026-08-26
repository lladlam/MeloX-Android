package com.lladlam.melox.core.provider.kuwo

import java.math.BigInteger
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

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

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return BigInteger(1, digest).toString(16).padStart(32, '0')
    }
}
