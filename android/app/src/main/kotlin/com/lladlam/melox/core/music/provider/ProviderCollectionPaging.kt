package com.lladlam.melox.core.music.provider

import com.lladlam.melox.core.music.model.MusicPlaylistDetail
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicAlbumSummary
import com.lladlam.melox.core.music.model.MusicAlbumDetail
import com.lladlam.melox.core.music.model.MusicArtistSummary
import com.lladlam.melox.core.music.model.MusicArtistDetail
import com.lladlam.melox.core.music.model.ProviderTrackMetadata

internal suspend fun ArtistCapability.loadAllArtistTracks(artist: MusicArtistSummary, pageSize: Int = 100): MusicArtistDetail {
    val first = artistDetail(artist, page = 1, pageSize = pageSize)
    val tracks = loadAllMusicPages(key = { it.id }) { page ->
        val detail = if (page == 1) first else artistDetail(artist, page = page, pageSize = pageSize)
        MusicPage(detail.tracks, page, pageSize, total = detail.totalTracks ?: detail.summary.songCount)
    }
    return first.copy(tracks = tracks)
}

internal suspend fun AlbumCapability.loadAllAlbumTracks(album: MusicAlbumSummary, pageSize: Int = 150): MusicAlbumDetail {
    val first = albumDetail(album, page = 1, pageSize = pageSize)
    val tracks = loadAllMusicPages(key = { it.id }) { page ->
        val detail = if (page == 1) first else albumDetail(album, page = page, pageSize = pageSize)
        MusicPage(detail.tracks, page, pageSize, total = detail.totalTracks ?: detail.summary.trackCount)
    }
    return first.copy(tracks = tracks)
}

/** Respect hasMore, preserve source-qualified identities, stop APIs that repeat page one. */
internal suspend fun <T, K> loadAllMusicPages(
    key: (T) -> K,
    load: suspend (Int) -> MusicPage<T>,
): List<T> {
    val values = LinkedHashMap<K, T>()
    for (page in 1..100) {
        val next = load(page)
        val before = values.size
        next.items.forEach { values.putIfAbsent(key(it), it) }
        if (!next.hasMore || next.items.isEmpty() || values.size == before) break
    }
    return values.values.toList()
}

/** Loads all available pages without assuming a provider's first-page limit is the total. */
suspend fun PlaylistCapability.loadAllPlaylistTracks(
    playlist: MusicPlaylistSummary,
    pageSize: Int = 200,
): MusicPlaylistDetail {
    val first = playlistDetail(playlist, page = 1, pageSize = pageSize)
    val tracks = first.tracks.toMutableList()
    var target = maxOf(
        first.total ?: 0L,
        first.summary.trackCount?.toLong() ?: 0L,
        tracks.size.toLong(),
    )
    var page = 2
    var hasMore = first.hasMore
    while ((hasMore ?: (tracks.size < target)) && page <= 100) {
        val before = tracks.size
        val next = playlistDetail(playlist, page = page, pageSize = pageSize)
        hasMore = next.hasMore
        target = maxOf(target, next.total ?: 0L, next.summary.trackCount?.toLong() ?: 0L)
        tracks += next.tracks
        val distinct = tracks.distinctBy {
            val membership = (it.providerMetadata as? ProviderTrackMetadata.YouTube)?.setVideoId
            membership?.let { setId -> "${it.id.sourceId.value}:member:$setId" } ?: it.id.toString()
        }
        tracks.clear()
        tracks += distinct
        if ((tracks.size == before || next.tracks.isEmpty()) && hasMore != true) break
        page++
    }
    return first.copy(
        summary = first.summary.copy(trackCount = tracks.size),
        tracks = tracks,
        total = maxOf(first.total ?: 0L, tracks.size.toLong()).takeIf { it > 0L },
        hasMore = hasMore,
    )
}
