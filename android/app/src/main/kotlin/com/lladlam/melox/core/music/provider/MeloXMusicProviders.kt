package com.lladlam.melox.core.music.provider

import android.content.Context
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.provider.applemusic.AppleMusicApiClient
import com.lladlam.melox.core.provider.applemusic.AppleMusicSessionStore
import com.lladlam.melox.core.provider.kugou.KugouLiteProvider
import com.lladlam.melox.core.provider.kugou.KugouSessionStore
import com.lladlam.melox.core.provider.kuwo.KuwoProvider
import com.lladlam.melox.core.provider.kuwo.KuwoSessionStore
import com.lladlam.melox.core.provider.netease.NeteaseProvider
import com.lladlam.melox.core.provider.qqmusic.QQMusicProvider
import com.lladlam.melox.core.provider.qqmusic.QQMusicSessionStore
import com.lladlam.melox.core.provider.bilibili.BilibiliProvider
import com.lladlam.melox.core.provider.bilibili.BilibiliSessionStore
import com.lladlam.melox.core.provider.bilibili.BilibiliPlaybackAssociationStore
import com.lladlam.melox.core.provider.bilibili.BilibiliApiCache
import com.lladlam.melox.core.provider.spotify.SpotifyClientConfig
import com.lladlam.melox.core.provider.spotify.SpotifyProvider
import com.lladlam.melox.core.provider.youtubemusic.YouTubeMusicProvider
import com.lladlam.melox.core.provider.jellyfin.JellyfinProvider
import com.lladlam.melox.core.provider.jellyfin.JellyfinSessionStore
import com.lladlam.melox.core.provider.local.LocalProvider
import com.lladlam.melox.core.network.MeloXHttpClient
import okhttp3.OkHttpClient
import com.lladlam.melox.core.provider.qishui.QishuiProvider
import com.lladlam.melox.core.provider.qishui.QishuiSessionStore

/** Creates provider instances that all read their authentication state locally. */
object MeloXMusicProviders {
    private val dynamicProviders = DynamicMusicProviders()
    @Volatile
    private var sharedRegistry: MusicProviderRegistry? = null

    fun create(
        context: Context,
        httpClient: OkHttpClient = MeloXHttpClient.shared,
    ): MusicProviderRegistry {
        val appContext = context.applicationContext
        if (httpClient !== MeloXHttpClient.shared) return buildRegistry(appContext, httpClient)
        return sharedRegistry ?: synchronized(this) {
            sharedRegistry ?: buildRegistry(appContext, httpClient).also { sharedRegistry = it }
        }
    }

    /** Registry reserved for URL resolution and quality probing. */
    fun createPlayback(
        context: Context,
        httpClient: OkHttpClient = MeloXHttpClient.shared,
    ): MusicProviderRegistry {
        val appContext = context.applicationContext
        val nativeProviders = listOf<MusicProvider>(
                NeteaseProvider({ PlaybackAccountStore.neteaseCookie(appContext) }, httpClient),
                QQMusicProvider({ PlaybackAccountStore.qqSession(appContext) }, httpClient),
                liteProvider(appContext, httpClient),
                QishuiProvider({ QishuiSessionStore.read(appContext) }, httpClient),
                KuwoProvider({ PlaybackAccountStore.kuwoSession(appContext) }, httpClient),
                AppleMusicApiClient({ AppleMusicSessionStore.read(appContext) }, httpClient),
                BilibiliProvider(
                    { BilibiliSessionStore.read(appContext) }, httpClient,
                    { bvid, cid -> BilibiliPlaybackAssociationStore.read(appContext, bvid, cid) },
                    BilibiliApiCache.shared(appContext),
                    { BilibiliSessionStore.revision(appContext) },
                ),
                JellyfinProvider({ JellyfinSessionStore.read(appContext) }, httpClient),
                LocalProvider(appContext),
                YouTubeMusicProvider(appContext, httpClient),
        )
        return MusicProviderRegistry(
                nativeProviders + SpotifyProvider(
                appContext,
                SpotifyClientConfig.effective(appContext),
                httpClient,
                playbackProviders = { nativeProviders },
            ),
            dynamic = dynamicProviders,
            awaitDynamicReady = { com.lladlam.melox.core.plugin.MeloXPlugins.awaitRestored() },
        )
    }

    private fun liteProvider(context: Context, httpClient: OkHttpClient): KugouLiteProvider =
        KugouLiteProvider(
            sessionProvider = { KugouSessionStore.readLite(context) },
            httpClient = httpClient,
            persistAuth = { auth -> KugouSessionStore.updateLiteEntitlements(context, auth = auth) },
            persistDfid = { dfid -> KugouSessionStore.updateLiteDfid(context, dfid) },
            onAuthExpired = { KugouSessionStore.clearLiteLogin(context) },
            persistVip = { vip ->
                KugouSessionStore.updateLiteEntitlements(
                    context = context,
                    vipType = vip.vipType,
                    vipExpiresAtEpochSeconds = vip.expiresAtEpochSeconds,
                    superVip = vip.superVip,
                    vipActive = vip.isVip,
                    freeListenEligible = vip.freeListen,
                )
            },
        )

    private fun buildRegistry(context: Context, httpClient: OkHttpClient): MusicProviderRegistry {
        val nativeProviders = listOf<MusicProvider>(
                NeteaseProvider(
                    cookieProvider = { NeteaseSessionStore.readCookie(context) },
                    httpClient = httpClient,
                ),
                QQMusicProvider(
                    sessionProvider = { QQMusicSessionStore.read(context) },
                    httpClient = httpClient,
                ),
                liteProvider(context, httpClient),
                QishuiProvider({ QishuiSessionStore.read(context) }, httpClient),
                KuwoProvider({ KuwoSessionStore.read(context) }, httpClient),
                AppleMusicApiClient(
                    sessionProvider = { AppleMusicSessionStore.read(context) },
                    httpClient = httpClient,
                ),
                BilibiliProvider(
                    sessionProvider = { BilibiliSessionStore.read(context) },
                    httpClient = httpClient,
                    associationProvider = { bvid, cid -> BilibiliPlaybackAssociationStore.read(context, bvid, cid) },
                    apiCache = BilibiliApiCache.shared(context),
                    sessionRevisionProvider = { BilibiliSessionStore.revision(context) },
                ),
                JellyfinProvider({ JellyfinSessionStore.read(context) }, httpClient),
                LocalProvider(context),
                YouTubeMusicProvider(context, httpClient),
        )
        return MusicProviderRegistry(
            nativeProviders + SpotifyProvider(
                context,
                SpotifyClientConfig.effective(context),
                httpClient,
                playbackProviders = { nativeProviders },
            ),
            dynamic = dynamicProviders,
            awaitDynamicReady = { com.lladlam.melox.core.plugin.MeloXPlugins.awaitRestored() },
        )
    }
}
