package com.lladlam.melox.ui.sonify.artist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Keeps filtering tied to one source snapshot. Source updates cancel a pending query,
 * while clearing the query restores the new source immediately.
 */
@Composable
internal fun rememberFilteredArtists(
    source: List<String>,
    query: String
): List<String> {
    var filteredArtists by remember { mutableStateOf(source) }

    LaunchedEffect(source, query) {
        if (query.isEmpty()) {
            filteredArtists = source
            return@LaunchedEffect
        }

        delay(250)
        val result = withContext(Dispatchers.Default) {
            source.filter { artist ->
                artist.contains(query, ignoreCase = true)
            }
        }
        // LaunchedEffect resumes on Main after the Default calculation.
        filteredArtists = result
    }

    return if (query.isEmpty()) source else filteredArtists
}
