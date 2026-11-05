package com.lladlam.melox.core.music.provider

import com.lladlam.melox.core.library.NeteaseLibrarySnapshot
import com.lladlam.melox.core.library.NeteasePlaylistDetail
import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.model.MusicPlaylistDetail
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicTrack

/**
 * Compatibility bridge between the provider-neutral domain and the original
 * MeloX Android presentation models.
 *
 * The rule is intentional: provider code adapts into MeloX UI state; MeloX UI
 * must not adapt itself into provider-specific screens. That keeps animation,
 * flowing-light artwork backgrounds, shared-element transitions and future iOS
 * feature migrations in one presentation implementation.
 */
object MeloXLegacyUiBridge {
    fun track(track: MusicTrack): SearchSong = SearchSong(
        id = stableLegacyId("track:${track.id.sourceId.value}:${track.id.value}"),
        name = track.title,
        artists = track.artistText,
        album = track.album?.name.orEmpty(),
        albumId = track.album?.id?.value?.toLongOrNull(),
        artistId = track.artists.firstOrNull()?.id?.value?.toLongOrNull(),
        artworkUrl = track.artworkUrl,
        durationMs = track.durationMs ?: 0L,
        providerTrack = track,
    )

    fun playlist(playlist: MusicPlaylistSummary): NeteasePlaylistSummary = NeteasePlaylistSummary(
        id = stableLegacyId("playlist:${playlist.id.sourceId.value}:${playlist.id.value}"),
        name = playlist.title,
        coverUrl = playlist.artworkUrl,
        trackCount = playlist.trackCount ?: 0,
        creatorName = playlist.creatorName.orEmpty(),
        playCount = playlist.playCount ?: 0L,
        description = playlist.description,
        providerPlaylist = playlist,
    )

    fun playlistDetail(detail: MusicPlaylistDetail): NeteasePlaylistDetail =
        NeteasePlaylistDetail(
            summary = playlist(detail.summary),
            songs = detail.tracks.map(::track),
        )

    fun library(playlists: List<MusicPlaylistSummary>): NeteaseLibrarySnapshot =
        NeteaseLibrarySnapshot(
            playlists = playlists.map(::playlist),
            likedSongs = emptyList(),
            recentSongs = emptyList(),
            likedPlaylistId = null,
        )

    /**
     * UI-only ids must be stable but must never be sent to a provider API.
     * Provider-native ids remain in providerTrack/providerPlaylist.
     */
    private fun stableLegacyId(value: String): Long {
        var hash = -0x340d631b7bdddcdbL // FNV-1a 64 offset represented as signed Long
        value.forEach { char ->
            hash = hash xor char.code.toLong()
            hash *= 0x100000001b3L
        }
        // Keep provider bridge ids negative so an accidental NetEase API call is
        // obvious and cannot collide with normal positive NetEase ids.
        return when {
            hash == Long.MIN_VALUE -> Long.MIN_VALUE + 1L
            hash > 0L -> -hash
            hash == 0L -> -1L
            else -> hash
        }
    }
}
