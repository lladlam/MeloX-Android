package com.lladlam.melox.core.provider.local

import android.content.Context
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.music.provider.MusicCapability
import com.lladlam.melox.core.music.provider.MusicProvider
import com.lladlam.melox.core.music.provider.LocalAggregationCapability
import com.lladlam.melox.core.music.provider.PlaybackCapability
import com.lladlam.melox.core.music.provider.SearchCapability
import com.lladlam.melox.core.music.provider.FavoriteCapability
import com.lladlam.melox.core.music.provider.PlaylistCapability
import com.lladlam.melox.core.music.provider.PlaylistWriteCapability
import com.lladlam.melox.core.music.provider.PlaylistSyncCapability
import com.lladlam.melox.core.music.provider.LyricsCapability
import com.lladlam.melox.core.lyrics.AmlldbLyricsClient
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.MusicPlaylistDetail
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import java.util.UUID

class LocalProvider(context: Context) : MusicProvider, SearchCapability, PlaybackCapability,
    LocalAggregationCapability, FavoriteCapability, PlaylistCapability, PlaylistWriteCapability, PlaylistSyncCapability, LyricsCapability {
    private val repository = LocalMusicRepository(context)

    override val source: MusicSource = MusicSource.Local
    override val displayName: String = source.displayName
    override val capabilities: Set<MusicCapability> = setOf(
        MusicCapability.Search,
        MusicCapability.Playback,
        MusicCapability.Library,
        MusicCapability.Playlists,
        MusicCapability.PlaylistWrite,
        MusicCapability.Favorites,
        MusicCapability.Lyrics,
    )

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> {
        val normalized = query.trim().lowercase()
        val all = repository.tracks().filter { record ->
            normalized.isBlank() || listOf(record.title, record.displayName, record.artist, record.album)
                .any { it.lowercase().contains(normalized) }
        }
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceAtLeast(1)
        val from = ((safePage - 1) * safeSize).coerceAtMost(all.size)
        return MusicPage(
            items = all.drop(from).take(safeSize).map { it.toMusicTrack() },
            page = safePage,
            pageSize = safeSize,
            total = all.size.toLong(),
        )
    }

    override suspend fun aggregationTracks(page: Int, pageSize: Int): MusicPage<MusicTrack> =
        searchSongs("", page, pageSize)

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution {
        val metadata = track.providerMetadata as? ProviderTrackMetadata.Local
            ?: repository.track(track.id.value)?.let { ProviderTrackMetadata.Local(it.contentUri, it.fileKey) }
            ?: return PlaybackResolution.Unavailable("本地歌曲记录不存在")
        if (metadata.contentUri.isBlank()) return PlaybackResolution.Unavailable("本地歌曲 URI 为空")
        return PlaybackResolution.Playable(
            url = metadata.contentUri,
            requestedQuality = quality,
            actualQuality = quality,
        )
    }

    override suspend fun setFavorite(track: MusicTrack, favorite: Boolean) {
        repository.setFavorite(track.id.value, favorite)
    }

    override suspend fun lyrics(track: MusicTrack): LyricsDocument {
        val local = repository.track(track.id.value) ?: return LyricsDocument(emptyList())
        local.cachedLyrics?.let { return it }
        val id = local.recognizedNeteaseId
            ?: return LyricsDocument(emptyList())
        return AmlldbLyricsClient().lyrics(id).also { repository.updateLyrics(track.id.value, it) }
    }

    override suspend fun writablePlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceAtLeast(1)
        val all = repository.playlists().map { playlist ->
            MusicPlaylistSummary(
                id = MusicResourceId(source, playlist.id),
                title = playlist.name,
                trackCount = playlist.trackKeys.size,
            )
        }
        val from = ((safePage - 1) * safeSize).coerceAtMost(all.size)
        return MusicPage(all.drop(from).take(safeSize), safePage, safeSize, all.size.toLong())
    }

    override suspend fun playlistDetail(
        playlist: MusicPlaylistSummary,
        page: Int,
        pageSize: Int,
    ): MusicPlaylistDetail {
        val local = repository.playlists().firstOrNull { it.id == playlist.id.value }
        val tracks = local?.trackKeys.orEmpty()
            .mapNotNull(repository::track)
            .map { it.toMusicTrack() }
        return MusicPlaylistDetail(playlist.copy(trackCount = tracks.size), tracks, tracks.size.toLong())
    }

    override suspend fun addTrackToPlaylist(track: MusicTrack, playlist: MusicPlaylistSummary) {
        val current = repository.playlists().firstOrNull { it.id == playlist.id.value }
            ?: LocalPlaylist(playlist.id.value, playlist.title)
        repository.savePlaylist(current.copy(trackKeys = (current.trackKeys + track.id.value).distinct()))
    }

    override suspend fun createPlaylist(name: String): MusicPlaylistSummary {
        val playlist = LocalPlaylist(UUID.randomUUID().toString(), name.ifBlank { "本地歌单" })
        repository.savePlaylist(playlist)
        return MusicPlaylistSummary(MusicResourceId(source, playlist.id), playlist.name, trackCount = 0)
    }

    override suspend fun renamePlaylist(playlist: MusicPlaylistSummary, name: String) {
        repository.playlists().firstOrNull { it.id == playlist.id.value }?.let {
            repository.savePlaylist(it.copy(name = name.ifBlank { it.name }))
        }
    }

    override suspend fun removeTrackFromPlaylist(track: MusicTrack, playlist: MusicPlaylistSummary) {
        repository.playlists().firstOrNull { it.id == playlist.id.value }?.let {
            repository.savePlaylist(it.copy(trackKeys = it.trackKeys.filterNot { key -> key == track.id.value }))
        }
    }

    override suspend fun reorderPlaylistTrack(playlist: MusicPlaylistSummary, track: MusicTrack, newIndex: Int) {
        repository.playlists().firstOrNull { it.id == playlist.id.value }?.let {
            val keys = it.trackKeys.filterNot { key -> key == track.id.value }.toMutableList()
            keys.add(newIndex.coerceIn(0, keys.size), track.id.value)
            repository.savePlaylist(it.copy(trackKeys = keys))
        }
    }

    private fun LocalTrackRecord.toMusicTrack(): MusicTrack = MusicTrack(
        id = MusicResourceId(MusicSource.Local, fileKey),
        title = recognizedTitle ?: title.ifBlank { displayName },
        artists = listOf(MusicArtistRef(name = recognizedArtist ?: artist.ifBlank { "未知歌手" })),
        album = (recognizedAlbum ?: album).takeIf(String::isNotBlank)?.let { MusicAlbumRef(name = it, artworkUrl = recognizedArtworkUrl ?: artworkUri) },
        artworkUrl = recognizedArtworkUrl ?: artworkUri,
        durationMs = durationMs.takeIf { it > 0L },
        availability = com.lladlam.melox.core.music.model.TrackAvailability.Playable,
        providerMetadata = ProviderTrackMetadata.Local(contentUri = contentUri, fileKey = fileKey),
    )
}
