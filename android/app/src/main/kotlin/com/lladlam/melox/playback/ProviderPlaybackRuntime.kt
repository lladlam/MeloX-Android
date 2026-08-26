package com.lladlam.melox.playback

import android.content.Context
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.core.music.provider.PlaybackAccountStore
import com.lladlam.melox.core.music.provider.MusicProviderRegistry
import com.lladlam.melox.core.provider.applemusic.AppleMusicSessionStore
import com.lladlam.melox.core.provider.kugou.KugouSessionStore
import com.lladlam.melox.core.provider.qqmusic.QQMusicSessionStore
import com.lladlam.melox.core.provider.bilibili.BilibiliSessionStore
import com.lladlam.melox.core.provider.bilibili.BilibiliPlaybackAssociationStore
import com.lladlam.melox.core.provider.spotify.SpotifySessionStore
import java.security.MessageDigest

/**
 * Process-local bridge used by Media3's synchronous Resolver callback. No
 * credentials are embedded into MediaItems or custom URIs.
 */
object ProviderPlaybackRuntime {
    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var currentRegistry: MusicProviderRegistry? = null

    fun initialize(context: Context) {
        val application = context.applicationContext
        if (appContext === application && currentRegistry != null) return
        synchronized(this) {
            if (appContext === application && currentRegistry != null) return
            appContext = application
            currentRegistry = MeloXMusicProviders.createPlayback(application)
        }
    }

    fun registryOrNull(): MusicProviderRegistry? = currentRegistry

    fun authKey(source: MusicSource): String {
        val context = appContext ?: return ""
        val credential = when (source) {
            MusicSource.Netease -> PlaybackAccountStore.neteaseCookie(context)
            MusicSource.QQMusic -> PlaybackAccountStore.qqSession(context).cookie
            MusicSource.Kugou -> PlaybackAccountStore.kugouSession(context).let { session ->
                listOf(session.userId, session.token, session.vipToken, session.dfid).joinToString("|")
            }
            MusicSource.AppleMusic -> AppleMusicSessionStore.read(context).let { session ->
                listOf(session.developerToken, session.musicUserToken, session.storefront).joinToString("|")
            }
            MusicSource.Bilibili -> BilibiliSessionStore.read(context).cookie + "|" +
                BilibiliPlaybackAssociationStore.revision(context)
            MusicSource.Spotify -> SpotifySessionStore.read(context).let { session ->
                listOf(session.accountId, session.expiresAtEpochMs).joinToString("|")
            }
            MusicSource.Kuwo -> ""
        }
        return MessageDigest.getInstance("SHA-256").digest(credential.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
