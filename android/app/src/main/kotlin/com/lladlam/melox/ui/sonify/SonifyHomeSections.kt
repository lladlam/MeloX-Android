/*
 * Data model for the migrated Sonify home feed (GPL-3.0).
 *
 * Sonify's Discovery.kt reads KugouRepository/DiscoveryObject directly. MeloX
 * builds the same visual sections from HomeBlock, so the UI receives a small
 * presentation model instead of importing the discovery data layer.
 */
package com.lladlam.melox.ui.sonify

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.model.MusicTrack

internal sealed interface SonifyHomeSection {
    data class Recommend(
        val title: String,
        val subtitle: String,
        val tracks: List<MusicTrack>,
        val onPlay: (MusicTrack, List<MusicTrack>) -> Unit,
    ) : SonifyHomeSection

    data class Songs(
        val title: String,
        val songs: List<SearchSong>,
        val onPlay: (Int) -> Unit,
    ) : SonifyHomeSection

    data class Collections(
        val title: String,
        val items: List<SonifyHomeCollection>,
    ) : SonifyHomeSection

    data class Podcasts(
        val title: String,
        val items: List<SonifyHomePodcast>,
    ) : SonifyHomeSection
}

internal data class SonifyHomeCollection(
    val key: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val onClick: () -> Unit,
)

internal data class SonifyHomePodcast(
    val key: String,
    val title: String,
    val artworkUrl: String?,
    val onClick: () -> Unit,
)

/**
 * Sonify keeps its Home recommendation list in ImageViewModel so the same five
 * shuffled library songs survive tab switches and recompositions. MeloX uses
 * this process-scoped store for the same behaviour.
 */
internal object SonifyHomeRecommendationStore {
    var tracks by mutableStateOf<List<MusicTrack>>(emptyList())
        private set

    fun ensure(candidates: List<MusicTrack>) {
        if (tracks.isNotEmpty()) return
        val unique = candidates.distinctBy { it.id }
        if (unique.isEmpty()) return
        tracks = unique.shuffled().take(5)
    }
}
