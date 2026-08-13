package com.lladlam.melox.platform.lyricon

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/** Starts the lightweight Lyricon Provider bridge with the MeloX application process. */
class MeloXLyriconInitializerProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext ?: return false
        MeloXLyriconRuntime.start(app)
        return true
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
}
