package com.lladlam.melox.platform.xiaomi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiSuperIslandLyricLayoutTest {
    @Test
    fun cjkCharactersUseDoubleVisualWeight() {
        assertEquals(4, XiaomiSuperIslandLyricLayout.visualWeight("歌词"))
        assertEquals(3, XiaomiSuperIslandLyricLayout.visualWeight("abc"))
    }

    @Test
    fun splitFullLyricPreservesOrderWithoutOverflow() {
        val source = "愿所有的心声都在旋律里靠岸 tonight"
        val split = XiaomiSuperIslandLyricLayout.splitFullLyric(
            text = source,
            leftMaxWeight = 12,
            rightMaxWeight = 18,
        )
        assertTrue(split.left.isNotBlank())
        assertTrue(XiaomiSuperIslandLyricLayout.visualWeight(split.left) <= 12)
        assertTrue(XiaomiSuperIslandLyricLayout.visualWeight(split.right) <= 18)
        assertTrue(source.startsWith(split.left))
    }

    @Test
    fun emptyLyricFallsBackToMusicNote() {
        assertEquals(
            XiaomiSuperIslandLyricLayout.Split("♪", ""),
            XiaomiSuperIslandLyricLayout.splitFullLyric("   "),
        )
    }
}
