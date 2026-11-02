package com.lladlam.melox.ui.song

import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeloXSongWikiIdTest {
    private val song = SearchSong(id = 123, name = "song", artists = "artist", album = "", artworkUrl = null)

    @Test
    fun selectsNativeNeteaseIdInsteadOfSyntheticUiId() {
        val track = MusicTrack(id = MusicResourceId(MusicSource.Netease, "456"), title = "song", artists = emptyList())
        assertEquals(456L, neteaseWikiSongId(song.copy(providerTrack = track)))
        assertEquals(123L, neteaseWikiSongId(song))
        assertNull(neteaseWikiSongId(song.copy(providerTrack = track.copy(id = MusicResourceId(MusicSource.QQMusic, "456")))))
        assertNull(neteaseWikiSongId(song.copy(providerTrack = track.copy(id = MusicResourceId(MusicSource.Netease, "invalid")))))
    }
}
