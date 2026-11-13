package com.lladlam.melox.core.autosync

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoSyncTimelineSerializationTest {
    @Test
    fun audioIdentitySurvivesPersistenceRoundTrip() {
        val original = AutoSyncTimeline(
            trackKey = "netease:1",
            sourceHash = "lyrics-hash",
            language = "en",
            engine = "en:test",
            lines = emptyList(),
            createdAtMs = 1L,
            modelKey = "en:test",
            audioKey = "cached.mp3:1234",
        )

        assertEquals(original, AutoSyncTimeline.fromJson(original.toJson()))
    }
}
