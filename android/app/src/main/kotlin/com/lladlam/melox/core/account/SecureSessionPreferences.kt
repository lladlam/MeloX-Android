package com.lladlam.melox.core.account

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

internal object SecureSessionPreferences {
    private val instances = mutableMapOf<String, SharedPreferences>()

    @Synchronized
    fun open(context: Context, legacyName: String): SharedPreferences {
        val app = context.applicationContext
        val cacheKey = "${app.packageName}:$legacyName"
        instances[cacheKey]?.let { return it }
        val encrypted = EncryptedSharedPreferences.create(
            app,
            "${legacyName}_encrypted_v1",
            MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        val legacy = app.getSharedPreferences(legacyName, Context.MODE_PRIVATE)
        // A committed marker prevents stale plaintext from replacing newer credentials
        // if the process exits between the encrypted commit and legacy cleanup.
        if (!encrypted.getBoolean("_migration_complete", false)) {
            val editor = encrypted.edit()
            legacy.all.forEach { (key, value) ->
                if (!encrypted.contains(key)) when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> {
                        require(value.all { it is String })
                        editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                    null -> Unit
                    else -> error("Unsupported session preference type")
                }
            }
            check(editor.putBoolean("_migration_complete", true).commit()) {
                "Unable to persist encrypted session migration"
            }
        }
        check(legacy.edit().clear().commit()) { "Unable to clear migrated session data" }
        instances[cacheKey] = encrypted
        return encrypted
    }
}
