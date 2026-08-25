package com.lladlam.melox.core.provider.qqmusic

import org.junit.Assert.assertEquals
import org.junit.Test

class QQMusicSearchArtistParserTest {
    @Test
    fun splitsCombinedSemicolonSingerCredit() {
        assertEquals(
            listOf("电音联盟", "音波狂潮", "V-Festival", "兰音Reine", "PIKASONIC"),
            splitQQMusicSingerName("电音联盟;音波狂潮;V-Festival;兰音Reine;PIKASONIC"),
        )
    }

    @Test
    fun preservesOrdinarySingerName() {
        assertEquals(listOf("The Artist"), splitQQMusicSingerName("The Artist"))
    }
}
