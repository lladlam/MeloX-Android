package com.lladlam.melox.core.remoteconfig

import android.content.Context

object MeloXRemoteConfigConsent {
    const val PolicyVersion = "1.2-2026-08-25"
    private const val PreferencesName = "melox_remote_config_consent"
    private const val ChoiceMadeKey = "choice_made"
    private const val EnabledKey = "enabled"
    private const val VersionKey = "policy_version"
    private const val DecidedAtKey = "decided_at"

    fun choiceMade(context: Context): Boolean = preferences(context).getBoolean(ChoiceMadeKey, false) &&
        preferences(context).getString(VersionKey, null) == PolicyVersion

    fun enabled(context: Context): Boolean = preferences(context).getBoolean(EnabledKey, false) &&
        preferences(context).getString(VersionKey, null) == PolicyVersion

    fun acceptedVersion(context: Context): String? = preferences(context).getString(VersionKey, null)

    fun accept(context: Context) {
        preferences(context).edit()
            .putBoolean(ChoiceMadeKey, true)
            .putBoolean(EnabledKey, true)
            .putString(VersionKey, PolicyVersion)
            .putLong(DecidedAtKey, System.currentTimeMillis())
            .apply()
    }

    fun reject(context: Context) {
        preferences(context).edit()
            .putBoolean(ChoiceMadeKey, true)
            .putBoolean(EnabledKey, false)
            .putString(VersionKey, PolicyVersion)
            .putLong(DecidedAtKey, System.currentTimeMillis())
            .apply()
    }

    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
}
