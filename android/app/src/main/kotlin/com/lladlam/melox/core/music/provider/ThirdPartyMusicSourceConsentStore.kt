package com.lladlam.melox.core.music.provider

import android.content.Context

/** Local consent gate for user-configured music sources. It is never remote-controlled. */
object ThirdPartyMusicSourceConsentStore {
    private const val PreferencesName = "melox_third_party_music_sources"
    private const val KeyEnabled = "enabled"
    private const val KeyAgreementVersion = "agreement_version"
    const val AgreementVersion = "1.0-2026-08-27"

    fun enabled(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        .getBoolean(KeyEnabled, false)

    fun accept(context: Context) {
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KeyEnabled, true)
            .putString(KeyAgreementVersion, AgreementVersion)
            .apply()
    }

    fun reject(context: Context) {
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KeyEnabled, false)
            .apply()
    }
}
