package com.lladlam.melox.core.provider.youtubemusic

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.metrolist.innertube.YouTube
import java.security.MessageDigest

/** Square-compatible YouTube Music session: cookie plus visitor/data-sync context. */
data class YouTubeSession(
    val cookie: String = "",
    val visitorData: String = "",
    val dataSyncId: String = "",
    val pageId: String = "",
    val accountName: String = "",
) {
    val isLoggedIn: Boolean get() = cookie.isNotBlank() && dataSyncId.isNotBlank()
}

object YouTubeSessionStore {
    private const val PreferencesName = "melox_youtube_music_session"
    private const val Cookie = "cookie"
    private const val VisitorData = "visitor_data"
    private const val DataSyncId = "data_sync_id"
    private const val PageId = "page_id"
    private const val AccountName = "account_name"

    @Volatile private var appliedFingerprint: String? = null

    fun read(context: Context): YouTubeSession = preferences(context).let {
        YouTubeSession(
            cookie = it.getString(Cookie, "").orEmpty(),
            visitorData = it.getString(VisitorData, "").orEmpty(),
            dataSyncId = it.getString(DataSyncId, "").orEmpty(),
            pageId = it.getString(PageId, "").orEmpty(),
            accountName = it.getString(AccountName, "").orEmpty(),
        )
    }

    fun apply(context: Context): YouTubeSession = read(context).also(::applyToInnerTube)

    fun write(context: Context, session: YouTubeSession) {
        preferences(context).edit()
            .putString(Cookie, session.cookie)
            .putString(VisitorData, session.visitorData)
            .putString(DataSyncId, session.dataSyncId)
            .putString(PageId, session.pageId)
            .putString(AccountName, session.accountName)
            .apply()
        applyToInnerTube(session)
    }

    fun clear(context: Context) {
        preferences(context).edit().clear().apply()
        YouTube.cookie = null
        YouTube.visitorData = null
        YouTube.dataSyncId = null
        YouTube.pageId = null
        appliedFingerprint = null
        runCatching { android.webkit.CookieManager.getInstance().removeAllCookies(null) }
    }

    fun authFingerprint(context: Context): String {
        val session = read(context)
        val raw = listOf(session.cookie, session.visitorData, session.dataSyncId, session.pageId).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun applyToInnerTube(session: YouTubeSession) {
        val fingerprint = listOf(session.cookie, session.visitorData, session.dataSyncId, session.pageId).joinToString("|")
        if (fingerprint == appliedFingerprint) return
        YouTube.cookie = session.cookie.takeIf(String::isNotBlank)
        YouTube.visitorData = session.visitorData.takeIf(String::isNotBlank)
        YouTube.dataSyncId = session.dataSyncId.takeIf(String::isNotBlank)
        YouTube.pageId = session.pageId.takeIf(String::isNotBlank)
        appliedFingerprint = fingerprint
    }

    private fun preferences(context: Context) = EncryptedSharedPreferences.create(
        context.applicationContext,
        PreferencesName,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}
