package com.lladlam.melox.core.music.model

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PluginDataUpgradeTest {
    @Test fun unknownNestedExtensionsAndMissingSourceSurviveReadModifyWriteAndRollbackRead() {
        val previous = JSONObject("""{"source":"plugin:org.example.removed","id":"opaque","title":"Old","future":{"flag":true},
          "artists":[{"name":"Artist","artistSource":"plugin:org.example.removed","artistId":"artist","futureArtist":{"n":1}}],
          "album":{"name":"Album","albumSource":"plugin:org.example.removed","albumId":"album","futureAlbum":[1,2]},
          "metadata":{"type":"future-plugin-metadata","opaque":{"key":"value"}}}""")
        val upgraded = requireNotNull(MeloXTrackJson.decode(previous))
        val saved = MeloXTrackJson.encode(upgraded.copy(title = "Edited"))
        assertEquals("Edited", saved.getString("title"))
        assertEquals(previous.getJSONObject("future").toString(), saved.getJSONObject("future").toString())
        assertEquals(1, saved.getJSONArray("artists").getJSONObject(0).getJSONObject("futureArtist").getInt("n"))
        assertEquals("[1,2]", saved.getJSONObject("album").getJSONArray("futureAlbum").toString())
        assertEquals("future-plugin-metadata", saved.getJSONObject("metadata").getString("type"))
        val rollbackRead = requireNotNull(MeloXTrackJson.decode(saved))
        assertEquals(upgraded.id, rollbackRead.id)
        assertEquals(upgraded.album?.id, rollbackRead.album?.id)
        assertEquals(upgraded.artists.single().id, rollbackRead.artists.single().id)
    }

    @Test fun legacyBuiltinDataStillReadsWithoutExtensionState() {
        val previous = JSONObject("""{"source":"netease","id":"123","title":"Legacy","artists":[{"name":"Artist"}],"metadata":{"type":"netease","numericId":123}}""")
        val track = requireNotNull(MeloXTrackJson.decode(previous))
        assertEquals(MusicSource.Netease, track.id.sourceId.builtIn)
        assertNull(track.persistenceExtras)
        assertEquals(track.id, MeloXTrackJson.decode(MeloXTrackJson.encode(track))?.id)
    }
}
