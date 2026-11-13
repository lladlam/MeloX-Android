package com.lladlam.melox.ui.theme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXFontPolicyTest {
    @Test
    fun defaultScopeUsesTheSystemFontEverywhere() {
        assertEquals(MeloXFontPolicy.SCOPE_SYSTEM, MeloXFontPolicy.DEFAULT_SCOPE)
        assertTrue(MeloXFontPolicy.usesSystemUiFont(MeloXFontPolicy.DEFAULT_SCOPE))
        assertTrue(MeloXFontPolicy.usesSystemLyricFont(MeloXFontPolicy.DEFAULT_SCOPE))
        assertFalse(MeloXFontPolicy.requiresMiSans(MeloXFontPolicy.DEFAULT_SCOPE))
    }

    @Test
    fun unknownScopeFallsBackToTheSystemFont() {
        assertEquals(MeloXFontPolicy.SCOPE_SYSTEM, MeloXFontPolicy.normalize(null))
        assertEquals(MeloXFontPolicy.SCOPE_SYSTEM, MeloXFontPolicy.normalize(""))
        assertEquals(MeloXFontPolicy.SCOPE_SYSTEM, MeloXFontPolicy.normalize("MiLan"))
    }

    @Test
    fun miSansScopeUsesTheCustomFamilyForUiAndLyrics() {
        assertFalse(MeloXFontPolicy.usesSystemUiFont(MeloXFontPolicy.SCOPE_MI_SANS))
        assertFalse(MeloXFontPolicy.usesSystemLyricFont(MeloXFontPolicy.SCOPE_MI_SANS))
        assertTrue(MeloXFontPolicy.requiresMiSans(MeloXFontPolicy.SCOPE_MI_SANS))
    }

    @Test
    fun nonLyricsScopeKeepsMiSansForLyricsOnly() {
        assertTrue(MeloXFontPolicy.usesSystemUiFont(MeloXFontPolicy.SCOPE_SYSTEM_EXCEPT_LYRICS))
        assertFalse(MeloXFontPolicy.usesSystemLyricFont(MeloXFontPolicy.SCOPE_SYSTEM_EXCEPT_LYRICS))
        assertTrue(MeloXFontPolicy.requiresMiSans(MeloXFontPolicy.SCOPE_SYSTEM_EXCEPT_LYRICS))
    }

    @Test
    fun sha256HelperMatchesKnownVector() {
        val file = File.createTempFile("melox-font", ".bin")
        try {
            file.writeBytes("abc".toByteArray())
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                MeloXOnDemandFont.sha256(file),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun onDemandFontSourceIsPinnedToAGitHubRelease() {
        assertTrue(MeloXOnDemandFont.DownloadUrl.startsWith("https://github.com/"))
        assertTrue(MeloXOnDemandFont.DownloadUrl.contains("/releases/download/"))
    }
}
