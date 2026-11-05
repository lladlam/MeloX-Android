package com.lladlam.melox.core.music.provider

import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteUnknownSourceTest {
    @Test fun editingKnownFavoritesRetainsUnknownAndMalformedRecords() {
        val unknown = JSONObject("""{"source":"plugin:example.music","id":"track/1","title":"Song","extra":{"future":42}}""")
        val previous = JSONArray().put(unknown).put("future-record")
        val track = MusicTrack(MusicResourceId(MusicSource.Netease, "123"), "Known", listOf(MusicArtistRef(name = "Artist")))
        val updated = mergeFavoriteRecords(previous, listOf(track))
        assertEquals(3, updated.length())
        assertEquals("netease", updated.getJSONObject(0).getString("source"))
        assertEquals("plugin:example.music", updated.getJSONObject(1).getString("source"))
        assertEquals(42, updated.getJSONObject(1).getJSONObject("extra").getInt("future"))
        assertEquals("future-record", updated.getString(2))
    }
}
