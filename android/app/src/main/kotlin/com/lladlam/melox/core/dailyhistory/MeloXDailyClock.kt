package com.lladlam.melox.core.dailyhistory

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * The single source of truth for "which day" throughout daily-recommendation history.
 *
 * Daily recommendations themselves carry no date (the upstream response has no such
 * field), so the day a list is shown is defined here, at the moment the archive hook
 * runs. Archiving, calendar dots, "today" highlight and empty-state decisions must
 * all go through this object — never through a second epochDay/millis conversion.
 *
 * Matches the project's existing daily semantics: local natural day at the system
 * default zone, 00:00 boundary, ISO `yyyy-MM-dd` (see MeloXRemoteNoticeStore and
 * KugouLiteDailyReward).
 */
object MeloXDailyClock {

    /** Local natural day in the system default zone. */
    fun today(): LocalDate = LocalDate.now()

    /** Key used as the archive file name and the JSON `date` field. */
    fun todayKey(): String = today().toString()

    fun archiveKeyFor(date: LocalDate): String = date.toString()

    /** Inverse of [archiveKeyFor]; returns null for anything that is not ISO `yyyy-MM-dd`. */
    fun parseKey(key: String): LocalDate? = runCatching { LocalDate.parse(key) }.getOrNull()

    fun markedDates(keys: List<String>): Set<LocalDate> =
        keys.mapNotNull { parseKey(it) }.toSet()

    /**
     * Fixed 6 x 7 grid for a month view. `null` means a leading/trailing placeholder.
     * A constant 42 cells keeps the calendar height stable across months.
     */
    fun monthCells(
        yearMonth: YearMonth,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ): List<LocalDate?> {
        val first = yearMonth.atDay(1)
        val leading = (first.dayOfWeek.value - weekStart.value + 7) % 7
        val days = yearMonth.lengthOfMonth()
        return (0 until 42).map { index ->
            val day = index - leading + 1
            if (day in 1..days) yearMonth.atDay(day) else null
        }
    }

    fun isEmptyState(dateKeys: List<String>, selectedKey: String?): EmptyState = when {
        dateKeys.isEmpty() -> EmptyState.NO_HISTORY
        selectedKey == null || selectedKey !in dateKeys -> EmptyState.NO_RECORD_THAT_DAY
        else -> EmptyState.HAS_RECORD
    }
}

enum class EmptyState {
    /** Nothing has ever been archived. */
    NO_HISTORY,

    /** There is history, but not for the selected day. */
    NO_RECORD_THAT_DAY,

    HAS_RECORD,
}
