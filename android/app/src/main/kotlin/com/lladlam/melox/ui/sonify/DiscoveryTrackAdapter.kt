package com.lladlam.melox.ui.sonify

import com.lladlam.melox.core.model.SearchSong
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

/**
 * SearchSong is still the display model used by Sonify's song rows. The
 * providerTrack keeps provider-backed items playable without teaching the
 * Sonify UI about QQ/KuGou/YouTube.
 */
internal fun DiscoveryTrack.toSearchSong(): SearchSong = when (this) {
    is DiscoveryTrack.Netease -> song
    is DiscoveryTrack.Provider -> SearchSong(
        id = track.id.value.toLongOrNull() ?: (track.id.value.hashCode().toLong() and 0x7fffffff),
        name = track.title,
        artists = track.artistText,
        album = track.album?.name.orEmpty(),
        artworkUrl = track.artworkUrl,
        durationMs = track.durationMs ?: 0L,
        providerTrack = track,
    )
}
