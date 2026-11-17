package com.lladlam.melox.core.recommendation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack

/**
 * Process-scoped pool behind the Sonify-style "为你推荐" card.
 *
 * It starts with the active source's home/library tracks. Every queue that is
 * played through the app is appended as well, so a playlist or album started
 * with "play" / "shuffle" contributes all of its songs, not just the tapped
 * track.
 */
object MeloXHomeRecommendationPool {
    private const val RecommendationSize = 5

    private var sourceKey: String? = null
    private var pool: List<MusicTrack> = emptyList()

    var tracks by mutableStateOf<List<MusicTrack>>(emptyList())
        private set

    fun ensureSource(source: String, seeds: List<MusicTrack>) {
        val uniqueSeeds = seeds.distinctBy { it.id }
        if (uniqueSeeds.isNotEmpty()) {
            pool = (pool + uniqueSeeds).distinctBy { it.id }
        }
        if (sourceKey == source && tracks.isNotEmpty()) return
        sourceKey = source
        if (pool.isNotEmpty()) tracks = pool.shuffled().take(RecommendationSize)
    }

    fun appendTracks(tracks: List<MusicTrack>) {
        if (tracks.isEmpty()) return
        val additions = tracks.distinctBy { it.id }
            .filter { candidate -> pool.none { it.id == candidate.id } }
        if (additions.isEmpty()) return
        pool = (pool + additions).distinctBy { it.id }
        // Surface newly played material immediately, while keeping the five-card
        // pager stable when nothing new was played.
        val fresh = additions.shuffled().take(RecommendationSize)
        this.tracks = (fresh + this.tracks).distinctBy { it.id }.take(RecommendationSize)
    }

    fun appendSongs(songs: List<SearchSong>) {
        appendTracks(songs.mapNotNull(SearchSong::toRecommendationTrack))
    }
}

private fun SearchSong.toRecommendationTrack(): MusicTrack? {
    providerTrack?.let { return it }
    if (id <= 0L) return null
    return MusicTrack(
        id = MusicResourceId(MusicSource.Netease, id.toString()),
        title = name,
        artists = listOf(MusicArtistRef(name = artists)),
        album = null,
        artworkUrl = artworkUrl,
        durationMs = durationMs.takeIf { it > 0L },
    )
}
