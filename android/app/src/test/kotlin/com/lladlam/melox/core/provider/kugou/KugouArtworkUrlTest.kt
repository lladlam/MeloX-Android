package com.lladlam.melox.core.provider.kugou

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KugouArtworkUrlTest {
    @Test
    fun extractsArtworkFromStringTransParam() {
        val item = JSONObject().put(
            "trans_param",
            JSONObject().put("album_img", "http://imge.kugou.com/stdmusic/{size}/cover.jpg").toString(),
        )
        assertEquals(
            "https://imge.kugou.com/stdmusic/400/cover.jpg",
            kugouArtworkUrl(item),
        )
    }

    @Test
    fun prefersDirectArtworkAndSupportsObjectTransParam() {
        assertEquals(
            "https://img.example/direct.jpg",
            kugouArtworkUrl(JSONObject().put("image", "https://img.example/direct.jpg").put("trans_param", JSONObject().put("image", "https://img.example/nested.jpg"))),
        )
    }

    @Test
    fun ignoresInvalidTransParam() {
        assertNull(kugouArtworkUrl(JSONObject().put("trans_param", "not-json")))
    }
}
