package com.lladlam.melox.core.provider.bilibili

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BilibiliDirectedTrafficPolicyTest {

    @Test
    fun `official hosts are recognised`() {
        assertTrue(BilibiliDirectedTrafficPolicy.isOfficialCdnHost("upos-sz-mirrorali.bilivideo.com"))
        assertTrue(BilibiliDirectedTrafficPolicy.isOfficialCdnHost("bilivideo.com"))
        assertTrue(BilibiliDirectedTrafficPolicy.isOfficialCdnHost("cn-bilivideo.bilibili.com"))
        assertFalse(BilibiliDirectedTrafficPolicy.isOfficialCdnHost("example.com"))
        assertFalse(BilibiliDirectedTrafficPolicy.isOfficialCdnHost(""))
    }

    @Test
    fun `pcdn and third party hosts are recognised`() {
        assertTrue(BilibiliDirectedTrafficPolicy.isPcdnHost("xy123.mcdn.bilivideo.cn"))
        assertTrue(BilibiliDirectedTrafficPolicy.isPcdnHost("cn-gotcha204.bilivideo.com"))
        assertTrue(BilibiliDirectedTrafficPolicy.isPcdnHost("192.168.1.10"))
        assertTrue(BilibiliDirectedTrafficPolicy.isPcdnHost("pcdn.szbdyd.com"))
        assertTrue(BilibiliDirectedTrafficPolicy.isPcdnHost("upos.mountaintoys.cn"))
        assertFalse(BilibiliDirectedTrafficPolicy.isPcdnHost("upos-sz-mirrorali.bilivideo.com"))
    }

    @Test
    fun `inactive policy keeps the original server order`() {
        val pcdn = "https://xy.mcdn.bilivideo.cn/upgcxcode/123/45/flac?index=1"
        val official = "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/123/45/flac?index=1"

        assertEquals(
            pcdn,
            BilibiliDirectedTrafficPolicy.selectMediaUrl(listOf(pcdn, official), directedTrafficActive = false),
        )
    }

    @Test
    fun `active policy prefers the official cdn address`() {
        val pcdn = "https://xy.mcdn.bilivideo.cn/upgcxcode/123/45/flac?index=1"
        val official = "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/123/45/flac?index=1"

        assertEquals(
            official,
            BilibiliDirectedTrafficPolicy.selectMediaUrl(listOf(pcdn, official), directedTrafficActive = true),
        )
    }

    @Test
    fun `active policy rewrites the host when every candidate is pcdn`() {
        val risky = "https://pcdn.szbdyd.com/upgcxcode/99/88/audio.m4s?expires=123"

        val selected = BilibiliDirectedTrafficPolicy.selectMediaUrl(listOf(risky), directedTrafficActive = true)
        assertTrue(selected!!.startsWith("https://upos-sz-mirrorali.bilivideo.com/upgcxcode/"))
        assertTrue(selected.contains("expires=123"))
    }

    @Test
    fun `host specific paths are never rewritten`() {
        val risky = "https://pcdn.szbdyd.com/some/other/path/audio.m4s?expires=123"

        assertNull(BilibiliDirectedTrafficPolicy.rewriteToOfficialHost(risky))
        assertEquals(
            risky,
            BilibiliDirectedTrafficPolicy.selectMediaUrl(listOf(risky), directedTrafficActive = true),
        )
    }

    @Test
    fun `all pcdn candidates try rewritable backups in order`() {
        val hostSpecific = "https://pcdn.szbdyd.com/host-specific/audio.m4s"
        val backup = "https://xy.mcdn.bilivideo.cn/upgcxcode/99/audio.m4s?deadline=123"
        assertEquals(
            "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/99/audio.m4s?deadline=123",
            BilibiliDirectedTrafficPolicy.selectMediaUrl(listOf(hostSpecific, backup), true),
        )
    }

    @Test
    fun `rewrite rejects misleading paths and nonstandard authorities`() {
        val urls = listOf(
            "https://pcdn.szbdyd.com/other/upgcxcode/99/audio.m4s",
            "https://pcdn.szbdyd.com/v1/resourceful/audio.m4s",
            "ftp://pcdn.szbdyd.com/upgcxcode/99/audio.m4s",
            "https://user@pcdn.szbdyd.com/upgcxcode/99/audio.m4s",
            "https://pcdn.szbdyd.com:8443/upgcxcode/99/audio.m4s",
        )
        urls.forEach { assertNull(BilibiliDirectedTrafficPolicy.rewriteToOfficialHost(it)) }
    }

    @Test
    fun `v1 resource paths can be rewritten`() {
        val rewritten = BilibiliDirectedTrafficPolicy.rewriteToOfficialHost(
            "http://192.168.1.50/v1/resource/abcdef?wsSecret=1",
        )
        assertEquals(
            "http://upos-sz-mirrorali.bilivideo.com/v1/resource/abcdef?wsSecret=1",
            rewritten,
        )
    }

    @Test
    fun `empty candidate list yields no url`() {
        assertNull(BilibiliDirectedTrafficPolicy.selectMediaUrl(emptyList(), directedTrafficActive = true))
        assertNull(BilibiliDirectedTrafficPolicy.selectMediaUrl(listOf("", "  "), directedTrafficActive = false))
    }

    @Test
    fun `official candidate detection ignores pcdn hosts`() {
        val pcdn = "https://xy.mcdn.bilivideo.cn/upgcxcode/1/2/3"
        val official = "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/1/2/3"

        assertFalse(BilibiliDirectedTrafficPolicy.hasOfficialCandidate(listOf(pcdn)))
        assertTrue(BilibiliDirectedTrafficPolicy.hasOfficialCandidate(listOf(pcdn, official)))
    }
}
