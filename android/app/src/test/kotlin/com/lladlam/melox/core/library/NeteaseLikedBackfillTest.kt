package com.lladlam.melox.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseLikedBackfillTest {
    @Test
    fun backfillTriggersWhenLikeEndpointIsShortOfPlaylistCount() {
        assertTrue(
            needsLikedPlaylistBackfill(
                likedTrackCount = 660,
                likeIds = (1L..659L).toList(),
                resolvedDetails = 659,
            ),
        )
    }

    @Test
    fun backfillTriggersWhenSongDetailDropsAnId() {
        assertTrue(
            needsLikedPlaylistBackfill(
                likedTrackCount = 660,
                likeIds = (1L..660L).toList(),
                resolvedDetails = 659,
            ),
        )
    }

    @Test
    fun backfillStaysOffForAHealthySnapshot() {
        assertFalse(
            needsLikedPlaylistBackfill(
                likedTrackCount = 660,
                likeIds = (1L..660L).toList(),
                resolvedDetails = 660,
            ),
        )
    }

    @Test
    fun missingPlaylistCountDoesNotForceAnExtraRequest() {
        assertFalse(
            needsLikedPlaylistBackfill(
                likedTrackCount = 0,
                likeIds = (1L..660L).toList(),
                resolvedDetails = 660,
            ),
        )
    }

    @Test
    fun playlistOrderWinsAndLikeOnlyIdsAreKept() {
        assertEquals(
            listOf(9L, 8L, 7L, 6L, 5L),
            mergeLikedSongIds(
                likeIds = listOf(8L, 7L, 6L, 5L),
                playlistIds = listOf(9L, 8L, 7L),
            ),
        )
    }
}
