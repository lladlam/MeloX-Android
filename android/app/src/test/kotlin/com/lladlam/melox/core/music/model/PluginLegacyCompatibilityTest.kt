package com.lladlam.melox.core.music.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Keeps built-in IDs stable and checks the new codec no longer loses dynamic identities. */
class PluginLegacyCompatibilityTest {
    @Test fun futureEnumsAndTypeSpecificMetadataExtensionsSurviveKnownFieldEdits() {
        val original = JSONObject("""{"source":"netease","id":"1","title":"Before","artists":[{"name":"Artist"}],
            "availability":"FutureAvailability","bestQuality":"FutureQuality",
            "metadata":{"type":"netease","numericId":1,"hash":"future-netEase-extension"}}""")
        val track = requireNotNull(MeloXTrackJson.decode(original))
        val rewritten = MeloXTrackJson.encode(track.copy(title = "After"))
        assertEquals("After", rewritten.getString("title"))
        assertEquals("FutureAvailability", rewritten.getString("availability"))
        assertEquals("FutureQuality", rewritten.getString("bestQuality"))
        assertEquals("future-netEase-extension", rewritten.getJSONObject("metadata").getString("hash"))
        assertEquals(AudioQualityTier.Standard.name, MeloXTrackJson.encode(track.copy(bestQuality = AudioQualityTier.Standard)).getString("bestQuality"))
    }

    @Test fun builtInStorageIdsRemainStable() {
        val expected = listOf(
            "netease", "qq_music", "kugou", "kugou_lite", "kuwo", "apple_music",
            "bilibili", "spotify", "youtube_music", "jellyfin", "qishui", "local",
        )
        assertEquals(expected, MusicSource.entries.map { it.storageValue })
        MusicSource.entries.forEach { assertEquals(it, MusicSource.fromStorageValue(it.storageValue)) }
    }

    @Test fun legacyUnknownSourceHasDifferentFallbacksAtDifferentBoundaries() {
        val future = "plugin:example.catalog"
        assertEquals(MusicSource.Netease, MusicSource.fromStorageValue(future))
        assertEquals(SourceId.parse(future), MeloXTrackJson.decode(JSONObject().put("source", future).put("id", "x").put("title", "X"))?.id?.sourceId)
    }

    @Test fun codecRetainsDynamicTracksAndNestedResourceIdentities() {
        val known = JSONObject("""{"source":"qq_music","id":"same","title":"Known","artists":[{"name":"Artist","artistSource":"plugin:example.catalog","artistId":"a"}],"album":{"name":"Album","albumSource":"plugin:example.catalog","albumId":"b"},"metadata":{"type":"future","payload":"keep"},"futureField":"keep"}""")
        val unknown = JSONObject("""{"source":"plugin:example.catalog","id":"same","title":"Future","opaque":{"v":1}}""")
        val oldRecords = JSONArray().put(known).put(unknown)
        // Mirrors the codec/filter/re-encode stage of the old favorites store.
        val rewritten = JSONArray()
        for (index in 0 until oldRecords.length()) {
            MeloXTrackJson.decode(oldRecords.getJSONObject(index))?.let { rewritten.put(MeloXTrackJson.encode(it)) }
        }
        assertEquals(2, rewritten.length())
        val result = rewritten.getJSONObject(0)
        assertEquals("keep", result.getString("futureField"))
        assertEquals("plugin:example.catalog", result.getJSONArray("artists").getJSONObject(0).getString("artistSource"))
        assertEquals("plugin:example.catalog", result.getJSONObject("album").getString("albumSource"))
        assertEquals("future", result.getJSONObject("metadata").getString("type"))
    }
}
