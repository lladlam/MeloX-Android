package com.lladlam.melox.core.provider.kugou

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KugouArtworkUrlTest {
    @Test
    fun replacesSizeTemplateAndUpgradesHttp() {
        assertEquals(
            "https://imge.kugou.com/stdmusic/400/cover.jpg",
            normalizeKugouArtworkUrl("http://imge.kugou.com/stdmusic/{size}/cover.jpg"),
        )
    }

    @Test
    fun supportsProtocolRelativeUrl() {
        assertEquals(
            "https://imgessl.kugou.com/stdmusic/400/cover.jpg",
            normalizeKugouArtworkUrl("//imgessl.kugou.com/stdmusic/{SIZE}/cover.jpg"),
        )
    }

    @Test
    fun rejectsBlankValue() {
        assertNull(normalizeKugouArtworkUrl("  "))
    }
}
