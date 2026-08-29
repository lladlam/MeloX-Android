package com.lladlam.melox.core.music.provider

import android.content.Context
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.provider.applemusic.AppleMusicSessionStore
import com.lladlam.melox.core.provider.kugou.KugouSessionStore
import com.lladlam.melox.core.provider.kuwo.KuwoSessionStore
import com.lladlam.melox.core.provider.qqmusic.QQMusicSessionStore
import com.lladlam.melox.core.provider.bilibili.BilibiliSessionStore
import com.lladlam.melox.core.provider.spotify.SpotifySessionStore
import com.lladlam.melox.core.provider.jellyfin.JellyfinSessionStore

/**
 * Small provider-neutral account facade used by settings/experience UI.
 *
 * It intentionally handles only local session state. Login flows remain
 * provider-specific because QQ Music uses WebView auth while Kugou uses QR auth.
 */
class ProviderAccountManager(
    context: Context,
    private val neteaseSessionStore: NeteaseSessionStore? = null,
) {
    private val appContext = context.applicationContext

    data class AccountState(
        val source: MusicSource,
        val loggedIn: Boolean,
        val accountId: String? = null,
    )

    fun state(source: MusicSource): AccountState = when (source) {
        MusicSource.Netease -> {
            val cookie = neteaseSessionStore?.cookie ?: NeteaseSessionStore.readCookie(appContext)
            AccountState(
                source = source,
                loggedIn = cookie.isNotBlank(),
                accountId = neteaseSessionStore?.profile?.userId?.toString(),
            )
        }

        MusicSource.QQMusic -> {
            val session = QQMusicSessionStore.read(appContext)
            AccountState(
                source = source,
                loggedIn = session.isLoggedIn,
                accountId = session.uin.takeIf(String::isNotBlank),
            )
        }

        MusicSource.Kugou -> {
            val session = KugouSessionStore.read(appContext)
            AccountState(
                source = source,
                loggedIn = session.isLoggedIn,
                accountId = session.userId.takeIf { it > 0L }?.toString(),
            )
        }
        MusicSource.Kuwo -> {
            val session = KuwoSessionStore.read(appContext)
            AccountState(
                source = source,
                loggedIn = session.isLoggedIn,
                accountId = session.userId.takeIf(String::isNotBlank),
            )
        }

        MusicSource.AppleMusic -> {
            val session = AppleMusicSessionStore.read(appContext)
            AccountState(
                source = source,
                loggedIn = session.isConfigured,
                accountId = session.storefront.uppercase(),
            )
        }
        MusicSource.Bilibili -> BilibiliSessionStore.read(appContext).let { session ->
            AccountState(source, session.isLoggedIn, session.userId.takeIf(String::isNotBlank))
        }
        MusicSource.Spotify -> SpotifySessionStore.read(appContext).let { session ->
            AccountState(source, session.isLoggedIn, session.accountId.takeIf(String::isNotBlank))
        }
        MusicSource.Jellyfin -> JellyfinSessionStore.read(appContext).let { session ->
            AccountState(source, session.isLoggedIn, session.userName.takeIf(String::isNotBlank))
        }
    }

    fun allStates(): List<AccountState> = MusicSource.entries.map(::state)

    /**
     * Clears only the selected provider's local login state.
     * Kugou device identity deliberately survives logout.
     */
    fun logout(source: MusicSource) {
        when (source) {
            MusicSource.Netease -> {
                (neteaseSessionStore ?: NeteaseSessionStore(appContext)).clear()
            }

            MusicSource.QQMusic -> QQMusicSessionStore.clear(appContext, clearWebCookies = true)
            MusicSource.Kugou -> KugouSessionStore.clearLogin(appContext)
            MusicSource.Kuwo -> KuwoSessionStore.clear(appContext)
            MusicSource.AppleMusic -> AppleMusicSessionStore.clear(appContext)
            MusicSource.Bilibili -> BilibiliSessionStore.clear(appContext, clearWebCookies = true)
            MusicSource.Spotify -> SpotifySessionStore.clear(appContext)
            MusicSource.Jellyfin -> JellyfinSessionStore.clear(appContext)
        }
    }

    /**
     * Prepares a clean provider login flow. Kept separate from logout so UI can
     * distinguish "sign out" from "switch account" while using the same safe reset.
     */
    fun prepareAccountSwitch(source: MusicSource) = logout(source)
}
