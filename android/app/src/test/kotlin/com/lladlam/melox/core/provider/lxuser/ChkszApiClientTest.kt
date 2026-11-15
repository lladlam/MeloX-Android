package com.lladlam.melox.core.provider.lxuser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChkszApiClientTest {
    @Test
    fun selectsExactTitleAndArtistAcrossPunctuation() {
        val items = listOf(
            ChkszSearchItem("wrong", "今天不是明天", "其他歌手"),
            ChkszSearchItem("right", "今天不是明天 (feat. 兰音Reine)", "电音联盟 / 兰音Reine"),
        )

        assertEquals("right", selectChkszMatch("今天不是明天 (feat. 兰音Reine)", "电音联盟 / 兰音Reine", items)?.id)
    }

    @Test
    fun rejectsSameTitleByAnotherArtist() {
        val items = listOf(ChkszSearchItem("wrong", "去明天", "其他歌手"))

        assertNull(selectChkszMatch("去明天", "周深", items))
    }

    @Test
    fun rejectsUnrelatedResults() {
        val items = listOf(ChkszSearchItem("wrong", "完全不同", "周深"))

        assertNull(selectChkszMatch("去明天", "周深", items))
    }
}
