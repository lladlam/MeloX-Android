package com.lladlam.melox.ui.sonify

import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.ui.discovery.DiscoveryTrack

internal fun DiscoveryTrack.toMusicTrack(): MusicTrack? = when (this) {
    is DiscoveryTrack.Netease -> song.providerTrack ?: MusicTrack(
        MusicResourceId(MusicSource.Netease, song.id.toString()),
        song.name,
        listOf(MusicArtistRef(name = song.artists)),
        durationMs = song.durationMs,
        artworkUrl = song.artworkUrl,
    )
    is DiscoveryTrack.Provider -> track
}
