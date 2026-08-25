package com.lladlam.melox.core.provider.spotify

import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SpotifyJsonMapperTest {
    @Test
    fun mapsTrackIdentityArtistsAlbumDurationAndIsrc() {
        val json = JSONObject(
            """{
              "id":"spotify-track","name":"Song","duration_ms":201234,"is_playable":true,
              "external_ids":{"isrc":"USRC17607839"},
              "artists":[{"id":"artist-id","name":"Artist"}],
              "album":{"id":"album-id","name":"Album","images":[{"url":"https://image"}]}
            }""",
        )
        val track = SpotifyJsonMapper.track(json)
        assertNotNull(track)
        track!!
        assertEquals(MusicSource.Spotify, track.id.source)
        assertEquals("spotify-track", track.id.value)
        assertEquals("Artist", track.artistText)
        assertEquals("Album", track.album?.name)
        assertEquals(201234L, track.durationMs)
        assertEquals("USRC17607839", (track.providerMetadata as ProviderTrackMetadata.Spotify).isrc)
    }

    @Test
    fun mapsPlaylistItemUsingCurrentItemField() {
        val wrapped = JSONObject(
            """{"item":{"id":"id","name":"Title","duration_ms":1000,"artists":[{"name":"Artist"}]}}""",
        )
        assertEquals("id", SpotifyJsonMapper.playlistItem(wrapped)?.id?.value)
    }
}
