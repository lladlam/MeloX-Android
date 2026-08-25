package com.lladlam.melox.core.remoteconfig

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXRemoteNoticeStoreTest {
    @Test
    fun onceNoticeStopsAfterFirstDisplay() {
        assertTrue(shouldShowRemoteNotice("once", shownOnce = false, lastDailyEpochDay = 0L, todayEpochDay = 10L))
        assertFalse(shouldShowRemoteNotice("once", shownOnce = true, lastDailyEpochDay = 0L, todayEpochDay = 10L))
    }

    @Test
    fun dailyNoticeReturnsOnNextLocalDay() {
        assertFalse(shouldShowRemoteNotice("daily", shownOnce = false, lastDailyEpochDay = 10L, todayEpochDay = 10L))
        assertTrue(shouldShowRemoteNotice("daily", shownOnce = false, lastDailyEpochDay = 10L, todayEpochDay = 11L))
    }
}
