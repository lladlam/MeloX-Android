package com.lladlam.melox.core.remoteconfig

import android.content.Context
import java.time.LocalDate

object MeloXRemoteNoticeStore {
    private const val PreferencesName = "melox_remote_notices"

    fun shouldShow(
        context: Context,
        notice: MeloXRemoteNotice,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
    ): Boolean {
        val preferences = context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        return shouldShowRemoteNotice(
            frequency = notice.frequency,
            shownOnce = preferences.getBoolean("once:${notice.id}", false),
            lastDailyEpochDay = preferences.getLong("daily:${notice.id}", Long.MIN_VALUE),
            todayEpochDay = todayEpochDay,
        )
    }

    fun markShown(
        context: Context,
        notice: MeloXRemoteNotice,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
    ) {
        val editor = context.applicationContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
        when (notice.frequency) {
            "daily" -> editor.putLong("daily:${notice.id}", todayEpochDay)
            else -> editor.putBoolean("once:${notice.id}", true)
        }
        editor.apply()
    }
}

internal fun shouldShowRemoteNotice(
    frequency: String,
    shownOnce: Boolean,
    lastDailyEpochDay: Long,
    todayEpochDay: Long,
): Boolean = when (frequency) {
    "daily" -> lastDailyEpochDay != todayEpochDay
    else -> !shownOnce
}
