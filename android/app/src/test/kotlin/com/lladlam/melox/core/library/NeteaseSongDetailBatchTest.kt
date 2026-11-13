package com.lladlam.melox.core.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseSongDetailBatchTest {
    @Test
    fun pageSizeNeverExposesSongsToServerTruncation() {
        assertTrue(SONG_DETAIL_PAGE_SIZE <= SONG_DETAIL_RESPONSE_CAP)
    }

    @Test
    fun fullResponseAtServerCapIsTruncated() {
        assertTrue(isTruncatedSongDetailResponse(requested = 250, returned = 100))
    }

    @Test
    fun completeResponseIsNotTruncated() {
        assertFalse(isTruncatedSongDetailResponse(requested = 100, returned = 100))
    }

    @Test
    fun deletedSongsBelowServerCapAreNotRetriedIndefinitely() {
        assertFalse(isTruncatedSongDetailResponse(requested = 100, returned = 98))
        assertFalse(isTruncatedSongDetailResponse(requested = 1, returned = 0))
    }
}
