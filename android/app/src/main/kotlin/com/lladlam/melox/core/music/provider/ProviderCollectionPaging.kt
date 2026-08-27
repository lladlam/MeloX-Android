package com.lladlam.melox.core.music.provider

import com.lladlam.melox.core.music.model.MusicPlaylistDetail
import com.lladlam.melox.core.music.model.MusicPlaylistSummary

/** Loads all available pages without assuming a provider's first-page limit is the total. */
suspend fun PlaylistCapability.loadAllPlaylistTracks(
    playlist: MusicPlaylistSummary,
    pageSize: Int = 200,
): MusicPlaylistDetail {
    val first = playlistDetail(playlist, page = 1, pageSize = pageSize)
    val tracks = first.tracks.toMutableList()
    val target = maxOf(
        first.total ?: 0L,
        first.summary.trackCount?.toLong() ?: 0L,
        tracks.size.toLong(),
    )
    var page = 2
    while (tracks.size < target && page <= 100) {
        val before = tracks.size
        val next = playlistDetail(playlist, page = page, pageSize = pageSize)
        tracks += next.tracks
        val distinct = tracks.distinctBy { it.id.value }
        tracks.clear()
        tracks += distinct
        if (tracks.size == before || next.tracks.isEmpty()) break
        page++
    }
    return first.copy(
        summary = first.summary.copy(trackCount = tracks.size),
        tracks = tracks,
        total = maxOf(first.total ?: 0L, tracks.size.toLong()).takeIf { it > 0L },
    )
}
