package com.lladlam.melox.core.music.provider

import android.content.Context

/** Local consent gate for user-configured music sources. It is never remote-controlled. */
object ThirdPartyMusicSourceConsentStore {
    private const val PreferencesName = "melox_third_party_music_sources"
    private const val KeyEnabled = "enabled"
    private const val KeyAgreementVersion = "agreement_version"
    private const val KeyMembershipFallbackOnly = "membership_fallback_only"
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

    fun membershipFallbackOnly(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        .getBoolean(KeyMembershipFallbackOnly, false)

    fun setMembershipFallbackOnly(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit().putBoolean(KeyMembershipFallbackOnly, enabled).apply()
    }
}
