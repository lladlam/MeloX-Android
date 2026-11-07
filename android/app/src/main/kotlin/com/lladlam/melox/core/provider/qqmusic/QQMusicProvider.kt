package com.lladlam.melox.core.provider.qqmusic

import com.lladlam.melox.core.lyrics.withoutPlaceholderLyrics

import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicAccountSummary
import com.lladlam.melox.core.music.model.MusicAlbumDetail
import com.lladlam.melox.core.music.model.MusicAlbumSummary
import com.lladlam.melox.core.music.model.MusicArtistDetail
import com.lladlam.melox.core.music.model.MusicArtistSummary
import com.lladlam.melox.core.music.model.MusicHomeFeed
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicPlaylistDetail
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicRankingSummary
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.provider.AlbumCapability
import com.lladlam.melox.core.music.provider.ArtistCapability
import com.lladlam.melox.core.music.provider.CatalogSearchCapability
import com.lladlam.melox.core.music.provider.FavoriteCapability
import com.lladlam.melox.core.music.provider.HomeFeedCapability
import com.lladlam.melox.core.music.provider.LyricsCapability
import com.lladlam.melox.core.music.provider.LocalAggregationCapability
import com.lladlam.melox.core.music.provider.MusicCapability
import com.lladlam.melox.core.music.provider.MusicProvider
import com.lladlam.melox.core.music.provider.PlaybackCapability
import com.lladlam.melox.core.music.provider.PlaylistCapability
import com.lladlam.melox.core.music.provider.RankingCapability
import com.lladlam.melox.core.music.provider.SearchCapability
import com.lladlam.melox.core.music.provider.UserLibraryCapability
import okhttp3.OkHttpClient

class QQMusicProvider(
    sessionProvider: () -> QQMusicSession = { QQMusicSession("", "", "") },
    httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) : MusicProvider,
    SearchCapability,
    CatalogSearchCapability,
    LyricsCapability,
    PlaybackCapability,
    FavoriteCapability,
    HomeFeedCapability,
    UserLibraryCapability,
    PlaylistCapability,
    RankingCapability,
    AlbumCapability,
    ArtistCapability,
    LocalAggregationCapability {
    override val source: MusicSource = MusicSource.QQMusic
    override val displayName: String = source.displayName
    override val capabilities: Set<MusicCapability> = setOf(
        MusicCapability.Search,
        MusicCapability.Playback,
        MusicCapability.Lyrics,
        MusicCapability.Library,
        MusicCapability.Playlists,
        MusicCapability.Albums,
        MusicCapability.Artists,
        MusicCapability.Favorites,
        MusicCapability.HomeRecommendations,
        MusicCapability.Rankings,
    )

    private val api = QQMusicApiClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )
    private val richLyrics = QQMusicRichLyricsClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )
    private val playback = QQMusicPlaybackVkeyClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )
    private val favorites = QQMusicFavoriteClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )
    private val playlists = QQMusicPlaylistClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )
    private val rankings = QQMusicRankingClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )
    private val catalog = QQMusicCatalogClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> =
        catalog.searchTracks(query, page, pageSize)

    override suspend fun searchPlaylists(query: String, page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        catalog.searchPlaylists(query, page, pageSize)

    override suspend fun searchAlbums(query: String, page: Int, pageSize: Int): MusicPage<MusicAlbumSummary> =
        catalog.searchAlbums(query, page, pageSize)

    override suspend fun searchArtists(query: String, page: Int, pageSize: Int): MusicPage<MusicArtistSummary> =
        catalog.searchArtists(query, page, pageSize)

    override suspend fun lyrics(track: MusicTrack): LyricsDocument {
        val rich = runCatching { richLyrics.lyrics(track) }
            .onFailure {
                android.util.Log.w("MeloXLyricsAuto", "QQ QRC unavailable mid=${track.id.value}: ${it.message}")
            }.getOrNull()
            ?: return api.lyrics(track).withoutPlaceholderLyrics()
        val needsTranslation = rich.lines.none { !it.translation.isNullOrBlank() }
        val needsRomanization = rich.lines.none { !it.romanization.isNullOrBlank() }
        if (!needsTranslation && !needsRomanization) return rich
        // QQ 的 trans/roma 只服务外语歌曲：实测《红尘客栈》四个 profile 的 trans/roma
        // 长度都是 0，中文歌这次 legacy 往返必然空手而归，却要白等一整个 round-trip。
        if (isChineseLyric(rich)) return rich

        // QQ occasionally serves genuine QRC timing while the rich translation
        // field is empty. The ordinary lyric endpoint can still contain the
        // translation. Keep the QRC syllable timeline and only fill missing
        // annotations from the line-timed response.
        val fallback = runCatching { api.lyrics(track) }.getOrNull() ?: return rich
        return mergeFallbackAnnotations(rich, fallback)
    }

    /**
     * 歌词正文是否以中文为主（CJK 表意文字多于其他字母）。
     *
     * 中文歌在 QQ 侧没有翻译/音译数据，[lyrics] 里那次 legacy 合并可以直接跳过。
     */
    private fun isChineseLyric(document: LyricsDocument): Boolean {
        var cjk = 0
        var otherLetters = 0
        for (line in document.lines) {
            for (character in line.text) {
                when {
                    character.code in CJK_UNIFIED_IDEOGRAPHS || character.code in CJK_EXTENSION_A -> cjk++
                    character.isLetter() -> otherLetters++
                }
            }
        }
        return cjk > 0 && cjk > otherLetters
    }

    override suspend fun resolvePlayback(
        track: MusicTrack,
        quality: AudioQualityTier,
    ): PlaybackResolution = playback.resolve(track, quality)

    override suspend fun setFavorite(track: MusicTrack, favorite: Boolean) {
        favorites.setFavorite(track, favorite)
    }

    override suspend fun homeFeed(
        playlistLimit: Int,
        newSongLimit: Int,
        rankingLimit: Int,
    ): MusicHomeFeed = api.homeFeed(playlistLimit, newSongLimit, rankingLimit)

    override suspend fun accountSummary(): MusicAccountSummary? = api.accountSummary()

    override suspend fun userPlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        api.userPlaylists(page, pageSize)

    override suspend fun aggregationTracks(page: Int, pageSize: Int): MusicPage<MusicTrack> {
        val playlists = api.userPlaylists(page = 1, pageSize = 20).items
        val tracks = playlists.flatMap { playlist ->
            runCatching { this@QQMusicProvider.playlistDetail(playlist, page = 1, pageSize = pageSize).tracks }
                .getOrDefault(emptyList())
        }.distinctBy { it.id.value }
        return MusicPage(tracks, page, pageSize, tracks.size.toLong())
    }

    override suspend fun playlistDetail(
        playlist: MusicPlaylistSummary,
        page: Int,
        pageSize: Int,
    ): MusicPlaylistDetail = playlists.detail(playlist, page, pageSize)

    override suspend fun rankingTracks(
        ranking: MusicRankingSummary,
        page: Int,
        pageSize: Int,
    ): MusicPage<MusicTrack> = rankings.tracks(ranking, page, pageSize)

    override suspend fun albumDetail(
        album: MusicAlbumSummary,
        page: Int,
        pageSize: Int,
    ): MusicAlbumDetail = catalog.albumDetail(album, page, pageSize)

    override suspend fun artistDetail(
        artist: MusicArtistSummary,
        page: Int,
        pageSize: Int,
    ): MusicArtistDetail = catalog.artistDetail(artist, page, pageSize)

    private fun mergeFallbackAnnotations(
        rich: LyricsDocument,
        fallback: LyricsDocument,
    ): LyricsDocument {
        if (fallback.lines.isEmpty()) return rich
        return rich.copy(
            lines = rich.lines.map { line ->
                val annotation = fallback.lines
                    .minByOrNull { candidate -> kotlin.math.abs(candidate.timeMs - line.timeMs) }
                    ?.takeIf { candidate -> kotlin.math.abs(candidate.timeMs - line.timeMs) <= AnnotationToleranceMs }
                line.copy(
                    translation = line.translation
                        ?: annotation?.translation?.takeIf(String::isNotBlank),
                    romanization = line.romanization
                        ?: annotation?.romanization?.takeIf(String::isNotBlank),
                    romanizationSyllables = line.romanizationSyllables.ifEmpty {
                        annotation?.romanizationSyllables.orEmpty()
                    },
                )
            },
        )
    }

    private companion object {
        const val AnnotationToleranceMs = 1_500L

        /** CJK 统一表意文字（基本区 + 扩展 A）。 */
        val CJK_UNIFIED_IDEOGRAPHS = 0x4E00..0x9FFF
        val CJK_EXTENSION_A = 0x3400..0x4DBF
    }
}
