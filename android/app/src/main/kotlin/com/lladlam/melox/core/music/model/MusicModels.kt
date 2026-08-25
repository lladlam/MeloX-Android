package com.lladlam.melox.core.music.model

/** Music service that owns a resource. IDs are only unique together with [MusicSource]. */
enum class MusicSource(
    val storageValue: String,
    val displayName: String,
) {
    Netease("netease", "网易云音乐"),
    QQMusic("qq_music", "QQ音乐"),
    Kugou("kugou", "酷狗音乐"),
    AppleMusic("apple_music", "Apple Music"),
    Bilibili("bilibili", "Bilibili"),
    Spotify("spotify", "Spotify");

    companion object {
        fun fromStorageValue(value: String?): MusicSource =
            entries.firstOrNull { it.storageValue == value } ?: Netease
    }
}

data class MusicResourceId(
    val source: MusicSource,
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "music resource id must not be blank" }
    }
}

data class MusicArtistRef(
    val id: MusicResourceId? = null,
    val name: String,
)

data class MusicAlbumRef(
    val id: MusicResourceId? = null,
    val name: String,
    val artworkUrl: String? = null,
)

data class MusicArtistSummary(
    val id: MusicResourceId,
    val name: String,
    val artworkUrl: String? = null,
    val description: String? = null,
    val songCount: Long? = null,
    val albumCount: Long? = null,
)

data class MusicArtistDetail(
    val summary: MusicArtistSummary,
    val tracks: List<MusicTrack>,
    val totalTracks: Long? = null,
)

data class MusicAlbumSummary(
    val id: MusicResourceId,
    val title: String,
    val artworkUrl: String? = null,
    val artists: List<MusicArtistRef> = emptyList(),
    val releaseDate: String? = null,
    val trackCount: Long? = null,
)

data class MusicAlbumDetail(
    val summary: MusicAlbumSummary,
    val tracks: List<MusicTrack>,
    val totalTracks: Long? = null,
)

enum class TrackAvailability {
    Unknown,
    Playable,
    PreviewOnly,
    LoginRequired,
    SubscriptionRequired,
    RegionRestricted,
    CopyrightRestricted,
    Unavailable,
}

/**
 * Provider-only identifiers that must survive mapping into the common model.
 * They intentionally never leak into Compose or Media3 APIs.
 */
sealed interface ProviderTrackMetadata {
    data object Empty : ProviderTrackMetadata

    data class Netease(
        val numericId: Long,
    ) : ProviderTrackMetadata

    data class QQMusic(
        val songMid: String,
        val mediaMid: String? = null,
        val numericSongId: Long? = null,
    ) : ProviderTrackMetadata

    data class Kugou(
        val hash: String,
        val albumAudioId: Long? = null,
        val albumId: String? = null,
    ) : ProviderTrackMetadata

    data class AppleMusic(
        val catalogId: String,
        val storefront: String,
        val previewUrl: String? = null,
    ) : ProviderTrackMetadata

    data class Bilibili(
        val bvid: String,
        val cid: Long,
        val aid: Long? = null,
        val page: Int = 1,
    ) : ProviderTrackMetadata

    data class Spotify(
        val trackId: String,
        val isrc: String? = null,
    ) : ProviderTrackMetadata
}

data class MusicTrack(
    val id: MusicResourceId,
    val title: String,
    val artists: List<MusicArtistRef>,
    val album: MusicAlbumRef? = null,
    val artworkUrl: String? = album?.artworkUrl,
    val durationMs: Long? = null,
    val availability: TrackAvailability = TrackAvailability.Unknown,
    val providerMetadata: ProviderTrackMetadata = ProviderTrackMetadata.Empty,
) {
    val artistText: String
        get() = artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" }
}

/** Provider-neutral playlist card used by Home and Library experiences. */
data class MusicPlaylistSummary(
    val id: MusicResourceId,
    val title: String,
    val artworkUrl: String? = null,
    val creatorName: String? = null,
    val description: String? = null,
    val trackCount: Int? = null,
    val playCount: Long? = null,
)

data class MusicPlaylistDetail(
    val summary: MusicPlaylistSummary,
    val tracks: List<MusicTrack>,
    val total: Long? = null,
)

/** A ranking is intentionally not modelled as a playlist: some services expose different semantics. */
data class MusicRankingSummary(
    val id: MusicResourceId,
    val title: String,
    val artworkUrl: String? = null,
    val subtitle: String? = null,
    val previewTracks: List<MusicTrack> = emptyList(),
)

/** Minimal account information that can be displayed without leaking provider-specific credentials. */
data class MusicAccountSummary(
    val source: MusicSource,
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val subtitle: String? = null,
)

/**
 * Common semantic feed. Empty lists are meaningful: a provider may simply not
 * expose one of these sections. Experience decides which non-empty sections to render.
 */
data class MusicHomeFeed(
    val recommendedPlaylists: List<MusicPlaylistSummary> = emptyList(),
    val newSongs: List<MusicTrack> = emptyList(),
    val rankings: List<MusicRankingSummary> = emptyList(),
)

data class MusicPage<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val total: Long? = null,
    val hasMore: Boolean = total?.let { page.toLong() * pageSize < it } ?: (items.size >= pageSize),
)

enum class AudioQualityTier {
    Standard,
    High,
    Lossless,
    HiResolution,
    Immersive,
    Master,
}

sealed interface PlaybackResolution {
    data class Playable(
        val url: String,
        val requestHeaders: Map<String, String> = emptyMap(),
        val requestedQuality: AudioQualityTier,
        val actualQuality: AudioQualityTier? = null,
        val bitrate: Int? = null,
        val format: String? = null,
        val expiresAtEpochMs: Long? = null,
    ) : PlaybackResolution

    data class Preview(
        val url: String,
        val durationMs: Long? = null,
    ) : PlaybackResolution

    data object LoginRequired : PlaybackResolution
    data object SubscriptionRequired : PlaybackResolution
    data object RegionRestricted : PlaybackResolution
    data object CopyrightRestricted : PlaybackResolution

    data class Unavailable(
        val reason: String? = null,
    ) : PlaybackResolution
}
