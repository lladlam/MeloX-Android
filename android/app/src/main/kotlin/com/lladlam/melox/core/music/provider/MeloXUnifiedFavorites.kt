package com.lladlam.melox.core.music.provider

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MeloXTrackJson
import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.music.model.TrackAvailability
import com.lladlam.melox.core.music.model.rank
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.provider.local.LocalMusicRepository
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * App-owned "我喜欢" playlist. It is intentionally independent of every
 * provider account: an item can be added from any source and is persisted with
 * its full [MusicTrack] metadata so playback still works after a restart.
 */
object MeloXUnifiedFavorites {
    const val PlaylistId = "melox_unified_favorites"
    /** Stable negative id used by the legacy Library UI, which keys rows by Long. */
    const val LegacyPlaylistId: Long = -0x4D454C4F58L

    private const val PreferencesName = "melox_unified_favorites"
    private const val KeyEnabled = "enabled"
    private const val KeyInitialized = "initialized"
    private const val KeyTracks = "tracks"
    private const val KeyTracksCorruptBackup = "tracks_corrupt_backup"
    private const val DurationToleranceMs = 10_000L
    private const val PageSize = 100

    private val revisionState = mutableIntStateOf(0)
    private val writeLock = Any()
    private val snapshotCache = java.util.WeakHashMap<Context, FavoriteSnapshot>()

    private data class FavoriteSnapshot(
        val raw: String,
        val tracks: List<MusicTrack>,
        val index: FavoriteTrackIndex,
    )

    /** Observable for Compose screens that need to refresh their copy. */
    val revision: Int get() = revisionState.intValue

    data class Failure(val source: MusicSource, val message: String)
    data class Report(val total: Int, val failures: List<Failure>)

    fun enabled(context: Context): Boolean =
        initialized(context) && prefs(context).getBoolean(KeyEnabled, false)

    fun initialized(context: Context): Boolean =
        prefs(context).getBoolean(KeyInitialized, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        if (enabled && !initialized(context)) return
        prefs(context).edit().putBoolean(KeyEnabled, enabled).apply()
        bumpRevision()
    }

    fun playlistSummary(context: Context): MusicPlaylistSummary? {
        if (!enabled(context)) return null
        val tracks = tracks(context)
        return MusicPlaylistSummary(
            id = MusicResourceId(MusicSource.Local, PlaylistId),
            title = "我喜欢",
            artworkUrl = tracks.firstNotNullOfOrNull { it.artworkUrl?.takeIf(String::isNotBlank) },
            creatorName = "弥乐",
            description = "弥乐本地收藏，不依赖任何音源账号",
            trackCount = tracks.size,
            isEditable = false,
        )
    }

    fun tracks(context: Context): List<MusicTrack> = readTracks(context)

    fun contains(context: Context, track: MusicTrack): Boolean =
        readSnapshot(context).index.contains(track)

    /** Toggles one logical song and returns its new favorite state. */
    fun toggle(context: Context, track: MusicTrack): Boolean = synchronized(writeLock) {
        val favorite = !contains(context, track)
        set(context, track, favorite)
        favorite
    }

    fun set(context: Context, track: MusicTrack, favorite: Boolean) = synchronized(writeLock) {
        val current = readTracks(context)
        val updated = if (favorite) {
            val index = current.indexOfFirst { sameSong(it, track) }
            if (index < 0) {
                current + track
            } else {
                current.toMutableList().also { list ->
                    // A later add from another source is an explicit choice:
                    // keep that source, but never downgrade an existing higher
                    // quality copy of the same logical song.
                    val keepExisting = track.id.sourceId != list[index].id.sourceId &&
                        qualityRank(track) < qualityRank(list[index])
                    // Keep the existing higher-quality source intact. Copying only
                    // display fields would pair a new title/art with stale quality.
                    if (!keepExisting) list[index] = track
                }
            }
        } else {
            current.filterNot { sameSong(it, track) }
        }
        writeTracks(context, updated, removed = current.map { it.id }.toSet() - updated.map { it.id }.toSet())
    }

    fun remove(context: Context, track: MusicTrack) = set(context, track, false)

    fun reorder(context: Context, track: MusicTrack, newIndex: Int) = synchronized(writeLock) {
        val current = readTracks(context)
        val index = current.indexOfFirst { sameSong(it, track) }
        if (index < 0) return@synchronized
        val updated = current.toMutableList()
        val item = updated.removeAt(index)
        updated.add(newIndex.coerceIn(0, updated.size), item)
        writeTracks(context, updated)
    }

    /**
     * Creates the playlist and migrates favorites from [sources]. Failures are
     * reported per source; successfully loaded sources are still kept.
     */
    suspend fun initialize(context: Context, sources: Set<MusicSource>): Report {
        val report = withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val existing = readTracks(app)
            val migrated = mutableListOf<MusicTrack>()
            val failures = mutableListOf<Failure>()
            for (source in sources) {
                runCatching { loadFavorites(app, source) }
                    .onSuccess { migrated += it }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        failures += Failure(source, error.message ?: "${source.displayName} 收藏读取失败")
                    }
            }
            val merged = TrackAggregation.aggregate(existing + migrated)
                .mapNotNull { it.recommendation?.track }
            writeTracks(app, merged, notify = false)
            prefs(app).edit()
                .putBoolean(KeyInitialized, true)
                .putBoolean(KeyEnabled, true)
                .apply()
            Report(merged.size, failures)
        }
        bumpRevision()
        return report
    }

    fun fromNetease(song: SearchSong): MusicTrack = MusicTrack(
        id = MusicResourceId(MusicSource.Netease, song.id.toString()),
        title = song.name,
        artists = song.artists.split(" / ")
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { MusicArtistRef(name = it) }
            .ifEmpty { listOf(MusicArtistRef(name = "未知歌手")) },
        album = song.album.takeIf(String::isNotBlank)?.let {
            MusicAlbumRef(name = it, artworkUrl = song.artworkUrl)
        },
        artworkUrl = song.artworkUrl,
        durationMs = song.durationMs.takeIf { it > 0L },
        availability = TrackAvailability.Playable,
        providerMetadata = ProviderTrackMetadata.Netease(song.id),
        bestQuality = song.bestQuality,
        bestBitrate = song.bestBitrate,
    )

    private suspend fun loadFavorites(context: Context, source: MusicSource): List<MusicTrack> = when (source) {
        MusicSource.Netease -> loadNeteaseFavorites(context)
        MusicSource.Local -> LocalMusicRepository(context).tracks()
            .filter { it.isFavorite }
            .map(::fromLocal)
        else -> loadProviderFavorites(context, source)
    }

    private suspend fun loadNeteaseFavorites(context: Context): List<MusicTrack> {
        val cookie = NeteaseSessionStore.readCookie(context)
        if (!NeteaseSessionStore.containsMusicU(cookie)) error("请先登录网易云音乐")
        val client = NeteaseLibraryClient(cookieProvider = { cookie })
        val profile = NeteaseSearchClient(cookieProvider = { cookie }).accountProfile()
        val ids = client.likedSongIdsBlocking(profile.userId)
        return ids.chunked(100)
            .flatMap { chunk -> client.songDetailsBlocking(chunk) }
            .map(::fromNetease)
    }

    private suspend fun loadProviderFavorites(context: Context, source: MusicSource): List<MusicTrack> {
        val provider = MeloXMusicProviders.create(context)[source]
            ?: error("${source.displayName} 当前不可用")
        val library = provider as? UserLibraryCapability
            ?: error("${source.displayName} 当前不提供个人音乐库")
        val playlists = provider as? PlaylistCapability
            ?: error("${source.displayName} 当前不提供歌单读取")
        val all = loadAllMusicPages(key = { it.id }) { page ->
            library.userPlaylists(page = page, pageSize = PageSize)
        }
        val liked = all.firstOrNull { isLikedPlaylist(it.title) }
            ?: error("未找到「我喜欢」或「Liked songs」歌单")
        return playlists.loadAllPlaylistTracks(liked, pageSize = 200).tracks
    }

    private fun fromLocal(record: com.lladlam.melox.core.provider.local.LocalTrackRecord): MusicTrack =
        MusicTrack(
            id = MusicResourceId(MusicSource.Local, record.fileKey),
            title = record.recognizedTitle ?: record.title.ifBlank { record.displayName },
            artists = listOf(
                MusicArtistRef(
                    name = record.recognizedArtist ?: record.artist.ifBlank { "未知歌手" },
                ),
            ),
            album = (record.recognizedAlbum ?: record.album)
                .takeIf(String::isNotBlank)
                ?.let { MusicAlbumRef(name = it, artworkUrl = record.recognizedArtworkUrl ?: record.artworkUri) },
            artworkUrl = record.recognizedArtworkUrl ?: record.artworkUri,
            durationMs = record.durationMs.takeIf { it > 0L },
            availability = TrackAvailability.Playable,
            providerMetadata = ProviderTrackMetadata.Local(
                contentUri = record.contentUri,
                fileKey = record.fileKey,
            ),
        )

    private fun isLikedPlaylist(title: String): Boolean {
        val value = title.trim().lowercase()
        return value in setOf(
            "我喜欢",
            "我喜欢的音乐",
            "喜欢的音乐",
            "我的喜欢",
            "我的收藏",
            "liked songs",
            "liked",
            "favorites",
            "favourites",
            "my favorites",
            "favorite songs",
        ) || value.contains("liked songs") || value.contains("我喜欢的音乐")
    }

    private fun sameSong(left: MusicTrack, right: MusicTrack): Boolean {
        if (TrackAggregation.keyOf(left) != TrackAggregation.keyOf(right)) return false
        val leftDuration = left.durationMs?.takeIf { it > 0L }
        val rightDuration = right.durationMs?.takeIf { it > 0L }
        return leftDuration == null || rightDuration == null ||
            abs(leftDuration - rightDuration) <= DurationToleranceMs
    }

    private fun qualityRank(track: MusicTrack): Int =
        (track.bestQuality ?: AudioQualityTier.Standard).rank()

    private fun readTracks(context: Context): List<MusicTrack> = readSnapshot(context).tracks

    private fun readSnapshot(context: Context): FavoriteSnapshot = synchronized(writeLock) {
        val appContext = context.applicationContext
        val raw = prefs(appContext).getString(KeyTracks, null).orEmpty()
        snapshotCache[appContext]?.takeIf { it.raw == raw }?.let { return@synchronized it }
        val array = if (raw.isBlank()) JSONArray() else runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val tracks = buildList {
            for (index in 0 until array.length()) {
                val value = array.optJSONObject(index) ?: continue
                MeloXTrackJson.decode(value)?.let(::add)
            }
        }
        FavoriteSnapshot(raw, tracks, FavoriteTrackIndex(tracks)).also { snapshotCache[appContext] = it }
    }

    private fun writeTracks(context: Context, tracks: List<MusicTrack>, notify: Boolean = true, removed: Set<MusicResourceId> = emptySet()) {
        synchronized(writeLock) {
            val raw = prefs(context).getString(KeyTracks, null).orEmpty()
            // A corrupt payload must never crash a favorite toggle, and must not be
            // silently destroyed either: keep a recoverable copy before starting over.
            val previous = if (raw.isBlank()) {
                JSONArray()
            } else {
                runCatching { JSONArray(raw) }.getOrElse {
                    prefs(context).edit().putString(KeyTracksCorruptBackup, raw).apply()
                    JSONArray()
                }
            }
            val array = mergeFavoriteRecords(previous, tracks, removed)
            prefs(context).edit().putString(KeyTracks, array.toString()).apply()
            snapshotCache.remove(context.applicationContext)
        }
        if (notify) bumpRevision()
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    private fun bumpRevision() {
        revisionState.intValue++
    }
}

/** Index only narrows candidates; duration matching retains the existing favorite semantics. */
internal class FavoriteTrackIndex(tracks: List<MusicTrack>) {
    private val byKey = tracks.groupBy(TrackAggregation::keyOf)

    fun contains(track: MusicTrack): Boolean {
        val duration = track.durationMs?.takeIf { it > 0L }
        return byKey[TrackAggregation.keyOf(track)].orEmpty().any { candidate ->
            val candidateDuration = candidate.durationMs?.takeIf { it > 0L }
            duration == null || candidateDuration == null || abs(duration - candidateDuration) <= 10_000L
        }
    }
}

/** Keep entries this version cannot decode when a known favorite is edited. */
internal fun mergeFavoriteRecords(previous: JSONArray, tracks: List<MusicTrack>, removed: Set<MusicResourceId> = emptySet()): JSONArray {
    val result = JSONArray()
    val oldRecords = (0 until previous.length()).mapNotNull { index ->
        previous.optJSONObject(index)?.let { record -> MeloXTrackJson.decode(record)?.let { it.id to record } }
    }.toMap()
    val retained = tracks.distinctBy { it.id }
    val retainedIds = retained.map { it.id }.toSet()
    retained.forEach { track ->
        val encoded = MeloXTrackJson.encode(track)
        oldRecords[track.id]?.let { old ->
            // Preserve opaque top-level extension data, but don't resurrect removed standard fields.
            val known = setOf("source", "id", "title", "artists", "album", "artwork", "durationMs", "availability", "bestQuality", "bestBitrate", "metadata")
            old.keys().forEach { key -> if (key !in known) encoded.put(key, old.get(key)) }
            val metadata = old.optJSONObject("metadata")
            if (track.providerMetadata == ProviderTrackMetadata.Empty && metadata?.optString("type") !in setOf(null, "empty")) {
                encoded.put("metadata", metadata)
            }
        }
        result.put(encoded)
    }
    for (index in 0 until previous.length()) {
        val entry = previous.opt(index)
        val decoded = (entry as? org.json.JSONObject)?.let(MeloXTrackJson::decode)
        if (decoded == null || decoded.id.sourceId.builtIn == null && decoded.id !in retainedIds && decoded.id !in removed) {
            result.put(entry)
        }
    }
    return result
}
