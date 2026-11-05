package com.lladlam.melox.core.music.provider

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
import com.lladlam.melox.core.music.model.SourceId
import com.lladlam.melox.core.music.model.PlaybackResolution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MusicCapability {
    Search,
    Playback,
    Lyrics,
    Library,
    Playlists,
    PlaylistWrite,
    Albums,
    Artists,
    Favorites,
    Comments,
    HomeRecommendations,
    DailyRecommendations,
    Rankings,
    Podcasts,
    CloudMusic,
    PrivateFm,
    HeartMode,
    ListenTogether,
    Messages,
    Recognition,
    Authentication,
    Download,
}

/**
 * A provider only declares identity/capabilities. Provider-specific product
 * features must not be forced into this interface just because NetEase has them.
 */
interface MusicProvider {
    /** Built-in providers override this; dynamic providers override [sourceId]. */
    val source: MusicSource
        get() = error("Dynamic music providers have no built-in MusicSource")
    val sourceId: SourceId
        get() = SourceId.fromBuiltIn(source)
    val displayName: String
    val capabilities: Set<MusicCapability>

    fun supports(capability: MusicCapability): Boolean = capability in capabilities
}

interface SearchCapability {
    suspend fun searchSongs(
        query: String,
        page: Int = 1,
        pageSize: Int = 30,
    ): MusicPage<MusicTrack>
}

/** Optional richer search surface. Providers only implement types their API actually exposes. */
interface CatalogSearchCapability {
    suspend fun searchPlaylists(
        query: String,
        page: Int = 1,
        pageSize: Int = 30,
    ): MusicPage<MusicPlaylistSummary>

    suspend fun searchAlbums(
        query: String,
        page: Int = 1,
        pageSize: Int = 30,
    ): MusicPage<MusicAlbumSummary>

    suspend fun searchArtists(
        query: String,
        page: Int = 1,
        pageSize: Int = 30,
    ): MusicPage<MusicArtistSummary>
}

interface LyricsCapability {
    suspend fun lyrics(track: MusicTrack): LyricsDocument
}

interface PlaybackCapability {
    suspend fun resolvePlayback(
        track: MusicTrack,
        quality: AudioQualityTier,
    ): PlaybackResolution
}

/** Provider-native download resolution. This is deliberately separate from playback fallback. */
interface DownloadCapability {
    suspend fun resolveDownload(
        track: MusicTrack,
        quality: AudioQualityTier,
    ): PlaybackResolution
}

/**
 * Optional account write capability for a provider's native "favorite/liked"
 * collection. Providers must only implement this when the platform exposes a
 * real authenticated write API with unambiguous semantics.
 */
interface FavoriteCapability {
    suspend fun setFavorite(
        track: MusicTrack,
        favorite: Boolean,
    )
}

/** Optional explicit state query; a write-only provider must not invent a favorite state. */
interface FavoriteStateCapability : FavoriteCapability {
    suspend fun isFavorite(track: MusicTrack): Boolean
}

interface AuthenticationCapability {
    suspend fun authenticationStatus(): com.lladlam.melox.core.music.model.MusicAuthenticationState
}

interface RankingCatalogCapability : RankingCapability {
    suspend fun rankings(page: Int = 1, pageSize: Int = 30): MusicPage<MusicRankingSummary>
}

/**
 * Optional playlist mutation surface. A provider returns only playlists for
 * which it has a reliable provider-native writable id; display/global ids must
 * never be guessed for writes.
 */
interface PlaylistWriteCapability {
    suspend fun writablePlaylists(
        page: Int = 1,
        pageSize: Int = 50,
    ): MusicPage<MusicPlaylistSummary>

    suspend fun addTrackToPlaylist(
        track: MusicTrack,
        playlist: MusicPlaylistSummary,
    )
}

/** Optional full playlist mutation surface for providers with native sync APIs. */
interface PlaylistSyncCapability {
    suspend fun createPlaylist(name: String): MusicPlaylistSummary
    suspend fun renamePlaylist(playlist: MusicPlaylistSummary, name: String)
    suspend fun removeTrackFromPlaylist(track: MusicTrack, playlist: MusicPlaylistSummary)
    suspend fun reorderPlaylistTrack(playlist: MusicPlaylistSummary, track: MusicTrack, newIndex: Int)

    /** False when the provider can edit membership but has no delete API. */
    val canDeletePlaylists: Boolean
        get() = false

    suspend fun deletePlaylist(playlist: MusicPlaylistSummary) {
        error("这个音源不能删除歌单")
    }
}

/** Home semantic feed. Providers return only the sections they actually expose. */
interface HomeFeedCapability {
    suspend fun homeFeed(
        playlistLimit: Int = 12,
        newSongLimit: Int = 12,
        rankingLimit: Int = 8,
    ): MusicHomeFeed
}

/** Logged-in account/library data. Credentials remain private to each provider. */
interface UserLibraryCapability {
    suspend fun accountSummary(): MusicAccountSummary?

    suspend fun userPlaylists(
        page: Int = 1,
        pageSize: Int = 30,
    ): MusicPage<MusicPlaylistSummary>
}

/**
 * Saved albums and followed artists. Separate from [UserLibraryCapability]
 * because a provider can list playlists without exposing either collection.
 */
interface LibraryCollectionCapability {
    suspend fun savedAlbums(page: Int = 1, pageSize: Int = 50): MusicPage<MusicAlbumSummary>
    suspend fun followedArtists(page: Int = 1, pageSize: Int = 50): MusicPage<MusicArtistSummary>
}

/** Optional read-only data surface used by local aggregation analysis. */
interface LocalAggregationCapability {
    suspend fun aggregationTracks(page: Int = 1, pageSize: Int = 100): MusicPage<MusicTrack>
}

/** Read-only playlist surface shared by provider-specific Experience UIs. */
interface PlaylistCapability {
    suspend fun playlistDetail(
        playlist: MusicPlaylistSummary,
        page: Int = 1,
        pageSize: Int = 100,
    ): MusicPlaylistDetail
}

/** Rankings are not forced into playlist semantics even if a provider implements them similarly. */
interface RankingCapability {
    suspend fun rankingTracks(
        ranking: MusicRankingSummary,
        page: Int = 1,
        pageSize: Int = 100,
    ): MusicPage<MusicTrack>
}

interface AlbumCapability {
    suspend fun albumDetail(
        album: MusicAlbumSummary,
        page: Int = 1,
        pageSize: Int = 100,
    ): MusicAlbumDetail
}

interface ArtistCapability {
    suspend fun artistDetail(
        artist: MusicArtistSummary,
        page: Int = 1,
        pageSize: Int = 100,
    ): MusicArtistDetail
}

/** Small registry used by repositories and the provider-aware UI layer. */
class MusicProviderRegistry(
    providers: Iterable<MusicProvider>,
    private val dynamic: DynamicMusicProviders = DynamicMusicProviders(),
    private val awaitDynamicReady: () -> Unit = {},
) {
    private val providersBySource = providers.associateBy(MusicProvider::source)

    init {
        require(providersBySource.isNotEmpty()) { "at least one music provider is required" }
        require(providersBySource.size == providers.count()) { "duplicate music provider source" }
    }

    val providers: List<MusicProvider>
        get() = providersBySource.values.toList()

    operator fun get(source: MusicSource): MusicProvider? = providersBySource[source]

    /** Unknown identities are unavailable, never mapped to a default provider. */
    operator fun get(source: SourceId): MusicProvider? = source.builtIn?.let(providersBySource::get)
        ?: dynamic[source]

    val generation: Long get() = dynamic.generation
    val changes: StateFlow<Long> get() = dynamic.changes
    fun awaitPluginRestore() = awaitDynamicReady()

    fun registerDynamic(provider: MusicProvider, enabled: Boolean = true) = dynamic.register(provider, enabled)

    fun setDynamicEnabled(source: SourceId, enabled: Boolean): Boolean = dynamic.setEnabled(source, enabled)

    fun unregisterDynamic(source: SourceId): Boolean = dynamic.unregister(source)

    fun replaceDynamic(provider: MusicProvider) = dynamic.replace(provider)

    /** Dynamic entries only; legacy callers of [providers] still see built-ins. */
    fun enabledDynamicProviders(): List<MusicProvider> = dynamic.enabledProviders()

    fun require(source: MusicSource): MusicProvider =
        providersBySource[source] ?: error("Music provider ${source.storageValue} is not registered")
}

/** Shared by the UI and playback registries; no provider is persisted here. */
class DynamicMusicProviders {
    private val lock = Any()
    @Volatile private var entries: Map<SourceId, Entry> = emptyMap()
    @Volatile var generation: Long = 0
        private set
    private val changesState = MutableStateFlow(0L)
    val changes: StateFlow<Long> = changesState.asStateFlow()

    private data class Entry(val provider: MusicProvider, val enabled: Boolean)

    operator fun get(source: SourceId): MusicProvider? = entries[source]?.takeIf { it.enabled }?.provider

    /** Atomically swaps an already enabled provider without a transient missing-source window. */
    fun replace(provider: MusicProvider) = synchronized(lock) {
        validate(provider)
        val old = entries[provider.sourceId] ?: error("plugin provider is not registered")
        entries = entries + (provider.sourceId to old.copy(provider = provider))
        generation++
        changesState.value = generation
    }

    private fun validate(provider: MusicProvider) {
        val id = provider.sourceId
        require(id.value.startsWith("plugin:") && id.builtIn == null) { "dynamic provider needs a plugin source id" }
        require(MusicCapability.Search !in provider.capabilities || provider is SearchCapability) { "missing search capability" }
        require(MusicCapability.Playback !in provider.capabilities || provider is PlaybackCapability) { "missing playback capability" }
        require(MusicCapability.Lyrics !in provider.capabilities || provider is LyricsCapability) { "missing lyrics capability" }
    }

    /** Register only an unused plugin identity; replacement requires explicit unregister. */
    fun register(provider: MusicProvider, enabled: Boolean = true) = synchronized(lock) {
        validate(provider)
        val id = provider.sourceId
        require(id.value.startsWith("plugin:") && id.builtIn == null) { "dynamic provider needs a plugin source id" }
        require(MusicCapability.Search !in provider.capabilities || provider is SearchCapability) { "missing search capability" }
        require(MusicCapability.Playback !in provider.capabilities || provider is PlaybackCapability) { "missing playback capability" }
        require(MusicCapability.Lyrics !in provider.capabilities || provider is LyricsCapability) { "missing lyrics capability" }
        require(id !in entries) { "duplicate dynamic provider source: ${id.value}" }
        entries = entries + (id to Entry(provider, enabled))
        generation++
        changesState.value = generation
    }

    fun setEnabled(source: SourceId, enabled: Boolean): Boolean = synchronized(lock) {
        val old = entries[source] ?: return@synchronized false
        if (old.enabled != enabled) {
            entries = entries + (source to old.copy(enabled = enabled))
            generation++
            changesState.value = generation
        }
        true
    }

    fun unregister(source: SourceId): Boolean = synchronized(lock) {
        if (source !in entries) return@synchronized false
        entries = entries - source
        generation++
        changesState.value = generation
        true
    }

    fun enabledProviders(): List<MusicProvider> = entries.values.filter { it.enabled }.map { it.provider }
}
