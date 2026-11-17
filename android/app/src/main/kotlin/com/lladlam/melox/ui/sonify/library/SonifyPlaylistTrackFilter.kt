/*
 * Sonify detail-page local filtering (GPL-3.0), adapted for MeloX.
 *
 * Upstream: ui/pages/library/playlists/PlaylistTrackFilter.kt (master @
 * 209e1aa). Kept the "original index + item" result so playback always uses
 * the complete queue rather than the filtered display position.
 */
package com.lladlam.melox.ui.sonify.library

internal fun String?.containsSonifyQuery(keyword: String): Boolean =
    this != null && keyword.isNotEmpty() && contains(keyword, ignoreCase = true)

internal fun <T> sonifyFilterTracks(
    tracks: List<T>,
    query: String,
    name: (T) -> String?,
    artist: (T) -> String?,
): List<Pair<Int, T>> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return List(tracks.size) { index -> index to tracks[index] }
    val result = ArrayList<Pair<Int, T>>()
    tracks.forEachIndexed { index, track ->
        if (name(track).containsSonifyQuery(keyword) || artist(track).containsSonifyQuery(keyword)) {
            result += index to track
        }
    }
    return result
}
