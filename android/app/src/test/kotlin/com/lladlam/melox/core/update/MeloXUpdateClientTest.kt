package com.lladlam.melox.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXUpdateClientTest {
    private val client = MeloXUpdateClient()

    @Test
    fun androidTagPrefixIsIgnored() {
        assertTrue(client.isNewer("android-v0.4.2-Dev", "0.4.1-Beta"))
    }

    @Test
    fun equalAndroidTagIsNotNewer() {
        assertFalse(client.isNewer("android-v0.4.2-Dev", "0.4.2-Dev"))
    }

    @Test
    fun regularVersionPrefixStillWorks() {
        assertTrue(client.isNewer("v0.5.0", "0.4.2-Dev"))
    }

    @Test
    fun staticManifestReplacesRateLimitedGitHubApi() {
        val release = client.parseManifest(
            """{
                "version":"0.5.0",
                "name":"MeloX 0.5.0",
                "notes":"notes",
                "pageUrl":"https://github.com/lladlam/MeloX-Android/releases/tag/0.5.0",
                "apkUrl":"https://github.com/lladlam/MeloX-Android/releases/download/0.5.0/MeloX.apk",
                "apkName":"MeloX.apk",
                "publishedAt":"2026-08-25T00:00:00Z"
            }""".trimIndent(),
        )

        assertEquals("0.5.0", release.version)
        assertEquals("MeloX.apk", release.apkName)
    }
}
