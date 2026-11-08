package com.lladlam.melox.core.dailyhistory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pure-logic coverage for the daily-recommendation archive.
 *
 * The JVM unit-test setup uses `unitTests.isReturnDefaultValues = true`, so
 * org.json / Android framework calls return defaults. File IO and JSON codecs are
 * intentionally not exercised here; only pure Kotlin + java.time + MessageDigest.
 */
class DailyHistoryLogicTest {

    @Test
    fun archiveKeyUsesIsoLocalDate() {
        assertEquals("2026-11-06", MeloXDailyClock.archiveKeyFor(LocalDate.of(2026, 11, 6)))
        assertEquals("2026-01-05", MeloXDailyClock.archiveKeyFor(LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun viewingAcrossMidnightArchivesBothDays() {
        val yesterday = MeloXDailyClock.archiveKeyFor(LocalDate.of(2026, 11, 6))
        val today = MeloXDailyClock.archiveKeyFor(LocalDate.of(2026, 11, 7))
        assertNotEquals(yesterday, today)
        // A cached list shown after midnight is still archived under the new day.
        assertEquals(
            listOf(today, yesterday),
            retainedKeysAfterWrite(existing = listOf(yesterday), written = today),
        )
    }

    @Test
    fun calendarDotsUseTheSameDateFormatAsArchiving() {
        val date = LocalDate.of(2026, 11, 6)
        val key = MeloXDailyClock.archiveKeyFor(date)
        assertEquals(date, MeloXDailyClock.parseKey(key))
        assertNull(MeloXDailyClock.parseKey("not-a-date"))
        assertNull(MeloXDailyClock.parseKey("2026-11-06.json"))
        assertEquals(
            setOf(date, LocalDate.of(2026, 11, 5)),
            MeloXDailyClock.markedDates(listOf(key, "2026-11-05", "bad-key")),
        )
    }

    @Test
    fun repeatedViewOnSameDayKeepsOneRecord() {
        val day = "2026-11-06"
        val retained = retainedKeysAfterWrite(
            existing = listOf(day, "2026-11-05"),
            written = day,
        )
        assertEquals(2, retained.size)
        assertEquals(1, retained.count { it == day })
        assertTrue(pruneKeys(retained).isEmpty())
    }

    @Test
    fun pruneKeepsExactlyThirtyMostRecentDays() {
        val keys = (0 until 31).map { offset ->
            LocalDate.of(2026, 1, 1).plusDays(offset.toLong()).toString()
        }
        assertTrue(pruneKeys(keys.take(30)).isEmpty())
        assertEquals(setOf(keys.first()), pruneKeys(keys))
        // Input order must not matter.
        assertEquals(setOf(keys.first()), pruneKeys(keys.reversed()))
        assertEquals(30, keys.size - pruneKeys(keys).size)
    }

    @Test
    fun writingAnOlderDayIsImmediatelyTrimmedWhenFull() {
        val existing = (1..30).map { day ->
            LocalDate.of(2026, 10, day).toString()
        }
        val older = "2026-09-30"
        val retained = retainedKeysAfterWrite(existing = existing, written = older)
        assertEquals(30, retained.size)
        assertFalse(retained.contains(older))
        assertEquals(existing.max(), retained.first())
    }

    @Test
    fun accountKeysAreIsolatedPerCookie() {
        assertNotEquals(accountKeyOf("cookie-A"), accountKeyOf("cookie-B"))
        assertEquals(accountKeyOf(""), accountKeyOf(""))
    }

    @Test
    fun accountKeyIgnoresUserIdAndHidesCookie() {
        val cookie = "MUSIC_U=super-secret-value"
        val key = accountKeyOf(cookie, userId = 12345L)
        assertEquals(key, accountKeyOf(cookie, userId = null))
        assertEquals(16, key.length)
        assertTrue(key.all { it in '0'..'9' || it in 'a'..'f' })
        assertFalse(key.contains("secret"))
    }

    @Test
    fun monthGridHasStableSixRowsAndCorrectDayCount() {
        val november = MeloXDailyClock.monthCells(YearMonth.of(2026, 11))
        assertEquals(42, november.size)
        assertEquals(30, november.count { it != null })
        assertEquals(12, november.count { it == null })
        assertEquals(LocalDate.of(2026, 11, 1), november.filterNotNull().first())
        assertEquals(LocalDate.of(2026, 11, 30), november.filterNotNull().last())

        val leapFebruary = MeloXDailyClock.monthCells(YearMonth.of(2024, 2))
        assertEquals(42, leapFebruary.size)
        assertEquals(29, leapFebruary.count { it != null })
    }

    @Test
    fun monthPagingKeepsSelectionInsideTheVisibleMonth() {
        val today = LocalDate.of(2026, 11, 9)
        val november = YearMonth.of(2026, 11)
        val october = YearMonth.of(2026, 10)

        // A selection in the target month stays put.
        assertEquals(
            "2026-10-03",
            MeloXDailyClock.selectionForMonth(october, "2026-10-03", today, listOf("2026-10-03")),
        )
        // Moving away from the current month selects the newest archive in that month.
        assertEquals(
            "2026-10-20",
            MeloXDailyClock.selectionForMonth(
                target = october,
                selectedKey = "2026-11-09",
                today = today,
                dateKeys = listOf("2026-10-20", "2026-10-03", "2026-09-30"),
            ),
        )
        // Returning to the current month selects today even without an archive.
        assertEquals(
            "2026-11-09",
            MeloXDailyClock.selectionForMonth(november, "2026-10-20", today, emptyList()),
        )
        // No archive and not the current month: clear selection instead of showing stale content.
        assertNull(MeloXDailyClock.selectionForMonth(YearMonth.of(2026, 8), "2026-10-20", today, emptyList()))
    }

    @Test
    fun emptyStatesCoverNoHistoryAndNoRecordThatDay() {
        val keys = listOf("2026-11-06")
        assertEquals(EmptyState.NO_HISTORY, MeloXDailyClock.isEmptyState(emptyList(), "2026-11-06"))
        assertEquals(EmptyState.NO_RECORD_THAT_DAY, MeloXDailyClock.isEmptyState(keys, "2026-11-05"))
        assertEquals(EmptyState.NO_SELECTION, MeloXDailyClock.isEmptyState(keys, null))
        assertEquals(EmptyState.HAS_RECORD, MeloXDailyClock.isEmptyState(keys, "2026-11-06"))
    }
}
