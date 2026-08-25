package com.lladlam.melox.core.provider.spotify

import android.content.Context
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicAccountSummary
import com.lladlam.melox.core.music.model.MusicAlbumDetail
import com.lladlam.melox.core.music.model.MusicAlbumSummary
import com.lladlam.melox.core.music.model.MusicArtistDetail
import com.lladlam.melox.core.music.model.MusicArtistSummary
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicPlaylistDetail
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.provider.AlbumCapability
import com.lladlam.melox.core.music.provider.ArtistCapability
import com.lladlam.melox.core.music.provider.CatalogSearchCapability
import com.lladlam.melox.core.music.provider.FavoriteCapability
import com.lladlam.melox.core.music.provider.MusicCapability
import com.lladlam.melox.core.music.provider.MusicProvider
import com.lladlam.melox.core.music.provider.PlaybackCapability
import com.lladlam.melox.core.music.provider.PlaylistCapability
import com.lladlam.melox.core.music.provider.PlaylistWriteCapability
import com.lladlam.melox.core.music.provider.SearchCapability
import com.lladlam.melox.core.music.provider.UserLibraryCapability
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient

class SpotifyProvider(
    context: Context,
    clientId: String,
    httpClient: OkHttpClient,
    private val playbackProviders: () -> List<MusicProvider>,
) : MusicProvider, SearchCapability, CatalogSearchCapability, PlaybackCapability,
    FavoriteCapability, UserLibraryCapability, PlaylistCapability, PlaylistWriteCapability,
    AlbumCapability, ArtistCapability {
    private val api = SpotifyApiClient(context, clientId, httpClient)

    override val source = MusicSource.Spotify
    override val displayName = source.displayName
    override val capabilities = setOf(
        MusicCapability.Search,
        MusicCapability.Playback,
        MusicCapability.Library,
        MusicCapability.Playlists,
        MusicCapability.PlaylistWrite,
        MusicCapability.Albums,
        MusicCapability.Artists,
        MusicCapability.Favorites,
    )

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int) = api.searchSongs(query, page, pageSize)
    override suspend fun searchPlaylists(query: String, page: Int, pageSize: Int) = api.searchPlaylists(query, page, pageSize)
    override suspend fun searchAlbums(query: String, page: Int, pageSize: Int) = api.searchAlbums(query, page, pageSize)
    override suspend fun searchArtists(query: String, page: Int, pageSize: Int) = api.searchArtists(query, page, pageSize)
    override suspend fun accountSummary(): MusicAccountSummary? = api.accountSummary()
    override suspend fun userPlaylists(page: Int, pageSize: Int) = api.userPlaylists(page, pageSize)
    override suspend fun writablePlaylists(page: Int, pageSize: Int) = api.writablePlaylists(page, pageSize)
    override suspend fun playlistDetail(playlist: MusicPlaylistSummary, page: Int, pageSize: Int): MusicPlaylistDetail =
        api.playlistDetail(playlist, page, pageSize)
    override suspend fun albumDetail(album: MusicAlbumSummary, page: Int, pageSize: Int): MusicAlbumDetail =
        api.albumDetail(album, page, pageSize)
    override suspend fun artistDetail(artist: MusicArtistSummary, page: Int, pageSize: Int): MusicArtistDetail =
        api.artistDetail(artist, page, pageSize)
    override suspend fun setFavorite(track: MusicTrack, favorite: Boolean) = api.setFavorite(track, favorite)
    override suspend fun addTrackToPlaylist(track: MusicTrack, playlist: MusicPlaylistSummary) =
        api.addTrackToPlaylist(track, playlist)

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution = coroutineScope {
        if (track.title.isBlank() || track.artists.isEmpty()) {
            return@coroutineScope PlaybackResolution.Unavailable("Spotify fallback 缺少曲目标题或艺人信息")
        }
        val query = "${track.title} ${track.artists.first().name}"
        val providers = playbackProviders().filter {
            it.source != MusicSource.Spotify && it is SearchCapability && it is PlaybackCapability
        }
        val candidates = providers.map { provider ->
            async {
                val search = provider as? SearchCapability ?: return@async emptyList<MusicTrack>()
                runCatching { search.searchSongs(query, 1, 10).items }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
        val ranked = SpotifyTrackMatcher.rank(track, candidates)
        for (match in ranked) {
            val provider = providers.firstOrNull { it.source == match.candidate.id.source }
            val playback = provider as? PlaybackCapability ?: continue
            when (val resolution = runCatching { playback.resolvePlayback(match.candidate, quality) }.getOrNull()) {
                is PlaybackResolution.Playable, is PlaybackResolution.Preview -> return@coroutineScope resolution
                else -> Unit
            }
        }
        PlaybackResolution.Unavailable("未找到可可靠匹配的非 Spotify 音源")
    }
}
