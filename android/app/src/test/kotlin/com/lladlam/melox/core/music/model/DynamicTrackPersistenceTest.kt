package com.lladlam.melox.core.music.model

import org.junit.Assert.*
import org.junit.Test

class DynamicTrackPersistenceTest {
    @Test fun pluginAndMissingSourcesRoundTripWithoutNeteaseFallback() {
        listOf("plugin:org.example.music", "future_provider").forEach { value ->
            val id = MusicResourceId(SourceId.parse(value)!!, "opaque/id:123")
            val track = MusicTrack(id, "Test", listOf(MusicArtistRef(name = "Artist")))
            val restored = MeloXTrackJson.decode(MeloXTrackJson.encode(track))!!
            assertEquals(track.id, restored.id)
            assertNull(restored.id.sourceId.builtIn)
            assertThrows(IllegalStateException::class.java) { restored.id.source }
        }
    }
}
