package com.lladlam.melox.core.provider.lxuser

import android.content.Context

object ChkszApiKeyStore {
    private const val PreferencesName = "melox_chksz_api"
    private const val KeyApiKey = "apikey"

    fun read(context: Context): String = context.applicationContext
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        .getString(KeyApiKey, "").orEmpty()

    fun write(context: Context, value: String) {
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit().putString(KeyApiKey, value.trim()).apply()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit().remove(KeyApiKey).apply()
    }
}
