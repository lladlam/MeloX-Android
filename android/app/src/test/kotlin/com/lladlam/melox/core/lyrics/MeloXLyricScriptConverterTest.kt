package com.lladlam.melox.core.lyrics

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MeloXLyricScriptConverterTest {
    @Test
    fun followsSystemChineseScript() {
        assertEquals(MeloXLyricScript.Traditional, MeloXLyricScript.fromSystem(Locale.TAIWAN))
        assertEquals(MeloXLyricScript.Simplified, MeloXLyricScript.fromSystem(Locale.CHINA))
        assertEquals(MeloXLyricScript.Original, MeloXLyricScript.fromSystem(Locale.ENGLISH))
    }

    @Test
    fun originalScriptLeavesLyricsUnchanged() {
        val document = LyricsDocument(listOf(LyricLine(0L, text = "愛", translation = "爱")))
        assertEquals(document, MeloXLyricScriptConverter.convert(document, MeloXLyricScript.Original))
    }
}
