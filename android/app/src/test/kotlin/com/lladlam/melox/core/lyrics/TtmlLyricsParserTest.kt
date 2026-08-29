package com.lladlam.melox.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtmlLyricsParserTest {
    @Test
    fun parsesTimedSyllablesTranslationsRomanizationAgentsAndBackground() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:itunes="http://music.apple.com/lyric-ttml-internal">
              <head><metadata><ttm:agent xml:id="v1"/><ttm:agent xml:id="v2"/></metadata></head>
              <body><div>
                <p begin="00:00:01.000" end="00:00:03.000" ttm:agent="v2" itunes:key="L1">
                  <span begin="00:00:01.000" end="00:00:01.500">你</span><span begin="00:00:01.500" end="00:00:02.000">好</span>
                  <span ttm:role="x-translation" xml:lang="zh-CN">你好（Hello）</span>
                  <span ttm:role="x-roman">ni hao</span>
                  <span ttm:role="x-bg" begin="00:00:02.000" end="00:00:02.800"><span begin="00:00:02.000" end="00:00:02.400">和</span><span begin="00:00:02.400" end="00:00:02.800">声</span></span>
                </p>
              </div></body>
            </tt>
        """.trimIndent()

        val document = TtmlLyricsParser.parse(ttml)
        val line = document.lines.single()
        assertEquals("你好", line.text)
        assertEquals(2, line.syllables.size)
        assertEquals("你好（Hello）", line.translation)
        assertEquals("ni hao", line.romanization)
        assertEquals(LyricAgentAlignment.Flipped, line.agent?.alignment)
        assertEquals("和声", line.accompaniment.single().text)
        assertEquals(LyricQuality.Authored, document.quality)
        assertTrue(!document.pseudoTimingAllowed)
    }

}
