package com.lladlam.melox.core.provider.youtubemusic

import android.content.Context
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Square-compatible YouTube Music session: cookie plus visitor/data-sync context. */
data class YouTubeSession(
    val cookie: String = "",
    val visitorData: String = "",
    val dataSyncId: String = "",
    val pageId: String = "",
    val accountName: String = "",
    val authUser: String = "0",
) {
    /** Square treats a stored cookie as signed in. `dataSyncId` only picks a channel. */
    val isLoggedIn: Boolean get() = hasYouTubeAuthCookie(cookie)
}

object YouTubeSessionStore {
    private val revisionState = MutableStateFlow(0L)
    val revision = revisionState.asStateFlow()
    private val expiredState = MutableStateFlow(false)
    val expired = expiredState.asStateFlow()
    private val accountMutex = Mutex()
    private const val PreferencesName = "melox_youtube_music_session"
    private const val Cookie = "cookie"
    private const val VisitorData = "visitor_data"
    private const val DataSyncId = "data_sync_id"
    private const val PageId = "page_id"
    private const val AccountName = "account_name"
    private const val AuthUser = "auth_user"

    @Volatile private var appliedFingerprint: String? = null

    fun read(context: Context): YouTubeSession = preferences(context).let {
        YouTubeSession(
            cookie = it.getString(Cookie, "").orEmpty(),
            visitorData = it.getString(VisitorData, "").orEmpty(),
            dataSyncId = it.getString(DataSyncId, "").orEmpty(),
            pageId = it.getString(PageId, "").orEmpty(),
            accountName = it.getString(AccountName, "").orEmpty(),
            authUser = it.getString(AuthUser, "0").orEmpty(),
        )
    }

    fun apply(context: Context, force: Boolean = false): YouTubeSession = read(context).also {
        // Language changes do not change the auth fingerprint. Refresh this
        // independently so a regionless app language never sends an empty gl.
        YouTube.locale = com.metrolist.innertube.models.YouTubeLocale.fromLocale(
            context.resources.configuration.locales[0],
        )
        if (force) appliedFingerprint = null
        applyToInnerTube(it)
    }

    fun write(context: Context, session: YouTubeSession) {
        preferences(context).edit()
            .putString(Cookie, session.cookie)
            .putString(VisitorData, session.visitorData)
            .putString(DataSyncId, session.dataSyncId)
            .putString(PageId, session.pageId)
            .putString(AccountName, session.accountName)
            .putString(AuthUser, session.authUser)
            .apply()
        applyToInnerTube(session)
        expiredState.value = false
        revisionState.value += 1
    }

    fun clear(context: Context) {
        val savedCookie = read(context).cookie
        preferences(context).edit().clear().apply()
        YouTube.cookie = null
        YouTube.visitorData = null
        YouTube.dataSyncId = null
        YouTube.pageId = null
        YouTube.authUser = "0"
        appliedFingerprint = null
        expiredState.value = false
        revisionState.value += 1
        runCatching {
            val manager = android.webkit.CookieManager.getInstance()
            val names = savedCookie.split(';')
                .mapNotNull { it.trim().substringBefore('=', "").ifBlank { null } }
            for (domain in listOf("https://music.youtube.com", "https://www.youtube.com", "https://accounts.google.com")) {
                for (name in names) {
                    manager.setCookie(domain, "$name=; Max-Age=0; Path=/; SameSite=Lax")
                    manager.setCookie(domain, "$name=; Max-Age=0; Path=/; Domain=.youtube.com; SameSite=Lax")
                }
            }
            manager.flush()
        }
    }

    fun authFingerprint(context: Context): String {
        val session = read(context)
        val raw = listOf(session.cookie, session.visitorData, session.dataSyncId, session.pageId, session.authUser).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /** A failed network request is not a logout. Keep the saved cookie intact. */
    suspend fun revalidate(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        accountMutex.withLock {
            val saved = apply(context)
            if (!saved.isLoggedIn) return@withLock Result.success(Unit)
            val info = YouTube.accountInfo()
            info.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            if (read(context) == saved) {
                info.onSuccess {
                    expiredState.value = false
                    if (it.name != saved.accountName) write(context, saved.copy(accountName = it.name))
                }.onFailure {
                    expiredState.value = Regex("\\b(401|403)\\b").containsMatchIn(it.message.orEmpty())
                }
            }
            info.map { Unit }
        }
    }

    suspend fun channels(context: Context): List<YouTubeChannel> = withContext(Dispatchers.IO) {
        accountMutex.withLock {
            if (!apply(context).isLoggedIn) return@withLock emptyList()
            YouTube.accounts().getOrThrow()
        }
    }

    /** Validate the page/data-sync pair before persisting it, as Square does. */
    suspend fun useChannel(context: Context, channel: YouTubeChannel) = withContext(Dispatchers.IO) {
        accountMutex.withLock {
            val saved = apply(context)
            check(saved.isLoggedIn)
            val candidate = saved.copy(
                pageId = channel.pageId.orEmpty(),
                dataSyncId = channel.dataSyncId.orEmpty(),
                accountName = channel.name,
            )
            try {
                applyToInnerTube(candidate)
                YouTube.accountInfo().getOrThrow()
                check(read(context) == saved) { "YouTube Music session changed" }
                write(context, candidate)
            } finally {
                // Includes cancellation, rejection, and logout during validation.
                apply(context, force = true)
            }
        }
    }

    private fun applyToInnerTube(session: YouTubeSession) {
        val fingerprint = listOf(session.cookie, session.visitorData, session.dataSyncId, session.pageId, session.authUser).joinToString("|")
        if (fingerprint == appliedFingerprint) return
        YouTube.cookie = session.cookie.takeIf(String::isNotBlank)
        YouTube.visitorData = session.visitorData.takeIf(String::isNotBlank)
        YouTube.dataSyncId = session.dataSyncId.takeIf(String::isNotBlank)
        YouTube.pageId = session.pageId.takeIf(String::isNotBlank)
        YouTube.authUser = session.authUser
        appliedFingerprint = fingerprint
    }

    private fun preferences(context: Context) =
        com.lladlam.melox.core.account.SecureSessionPreferences.openEncrypted(context, PreferencesName)
}
