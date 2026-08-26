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
    fun supportedReleaseTagFormatsAreDetected() {
        listOf(
            "android-0.5.0-Beta",
            "0.5.0-Beta",
            "0.5.0",
            "android-0.5.0",
        ).forEach { latest ->
            assertTrue("Expected $latest to be detected as newer", client.isNewer(latest, "0.4.4-Beta"))
        }
    }

    @Test
    fun supportedFormatsCompareAsTheSameVersion() {
        listOf(
            "android-0.4.4-Beta",
            "0.4.4-Beta",
            "0.4.4",
            "android-0.4.4",
        ).forEach { latest ->
            assertFalse("Expected $latest to match the installed version", client.isNewer(latest, "0.4.4-Beta"))
        }
    }

    @Test
    fun malformedVersionsAreNotTreatedAsUpdates() {
        assertFalse(client.isNewer("android-latest", "0.4.4-Beta"))
        assertFalse(client.isNewer("0.5", "0.4.4-Beta"))
        assertFalse(client.isNewer("0.5.0", "unknown"))
    }

    @Test
    fun releasesSelectHighestSupportedAndroidVersion() {
        val release = client.parseReleases(
            """[
                {"tag_name":"unrelated","draft":false,"html_url":"https://github.com/lladlam/MeloX-Android/releases/tag/unrelated"},
                {"tag_name":"android-0.5.0-Beta","name":"Beta","body":"notes","draft":false,"prerelease":true,"published_at":"2026-08-26T00:00:00Z","html_url":"https://github.com/lladlam/MeloX-Android/releases/tag/android-0.5.0-Beta","assets":[{"name":"MeloX.apk","browser_download_url":"https://github.com/lladlam/MeloX-Android/releases/download/android-0.5.0-Beta/MeloX.apk"}]},
                {"tag_name":"0.4.9","draft":false,"published_at":"2026-08-27T00:00:00Z","html_url":"https://github.com/lladlam/MeloX-Android/releases/tag/0.4.9","assets":[]},
                {"tag_name":"android-9.0.0","draft":true,"html_url":"https://github.com/lladlam/MeloX-Android/releases/tag/android-9.0.0"}
            ]""".trimIndent(),
        )

        requireNotNull(release)
        assertEquals("android-0.5.0-Beta", release.version)
        assertEquals("MeloX.apk", release.apkName)
    }

    @Test
    fun releasesAcceptEverySupportedTagShape() {
        listOf("android-0.5.0-Beta", "0.5.0-Beta", "0.5.0", "android-0.5.0").forEach { tag ->
            val release = client.parseReleases(
                """[{"tag_name":"$tag","draft":false,"html_url":"https://github.com/lladlam/MeloX-Android/releases/tag/$tag","assets":[]}]""",
            )
            assertEquals(tag, release?.version)
        }
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
