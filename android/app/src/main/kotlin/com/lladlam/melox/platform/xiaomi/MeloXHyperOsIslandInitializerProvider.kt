package com.lladlam.melox.platform.xiaomi

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import androidx.core.content.ContextCompat

/**
 * Process initializer that keeps the HyperOS island service aligned with the existing
 * "歌词通知" preference without adding Xiaomi-specific logic to the canonical Settings UI.
 */
class MeloXHyperOsIslandInitializerProvider : ContentProvider(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var preferences: SharedPreferences? = null

    override fun onCreate(): Boolean {
        val app = context?.applicationContext ?: return false
        preferences = app.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).also {
            it.registerOnSharedPreferenceChangeListener(this)
        }
        refresh(app)
        return true
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key != MeloXHyperOsIslandLyricService.PREFERENCE_KEY) return
        context?.applicationContext?.let(::refresh)
    }

    private fun refresh(app: Context) {
        val enabled = preferences?.getBoolean(
            MeloXHyperOsIslandLyricService.PREFERENCE_KEY,
            false,
        ) == true
        if (enabled && HyperOsFocusBridge.supportsSuperIsland(app)) {
            runCatching {
                ContextCompat.startForegroundService(
                    app,
                    Intent(app, MeloXHyperOsIslandLyricService::class.java),
                )
            }
        } else {
            app.stopService(Intent(app, MeloXHyperOsIslandLyricService::class.java))
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private const val PREFERENCES_NAME = "melox_app_settings"
    }
}
