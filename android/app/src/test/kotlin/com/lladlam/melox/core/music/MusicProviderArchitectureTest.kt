package com.lladlam.melox.core.music

import com.lladlam.melox.core.music.experience.CanonicalMeloXTabs
import com.lladlam.melox.core.music.experience.ExperienceTabId
import com.lladlam.melox.core.music.experience.MusicExperiences
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.provider.AlbumCapability
import com.lladlam.melox.core.music.provider.ArtistCapability
import com.lladlam.melox.core.music.provider.CatalogSearchCapability
import com.lladlam.melox.core.music.provider.MusicCapability
import com.lladlam.melox.core.music.provider.MusicProvider
import com.lladlam.melox.core.music.provider.MusicProviderRegistry
import com.lladlam.melox.core.provider.kugou.KugouProvider
import com.lladlam.melox.core.provider.kugou.KugouSession
import com.lladlam.melox.core.provider.qqmusic.QQMusicProvider
import com.lladlam.melox.core.provider.qqmusic.QQMusicSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicProviderArchitectureTest {
    @Test
    fun resourceIdsAreNamespacedByProvider() {
        val netease = MusicResourceId(MusicSource.Netease, "123")
        val qq = MusicResourceId(MusicSource.QQMusic, "123")
        assertNotEquals(netease, qq)
    }

    @Test
    fun everyProviderUsesCanonicalMeloXRootPresentation() {
        val experiences = listOf(
            MusicExperiences.netease,
            MusicExperiences.qqMusic,
            MusicExperiences.kugou,
        )

        experiences.forEach { experience ->
            assertEquals(CanonicalMeloXTabs, experience.tabs)
            assertEquals("首页", experience.tabs.single { it.id == ExperienceTabId.Home }.title)
            assertEquals("发现", experience.tabs.single { it.id == ExperienceTabId.Explore }.title)
            assertEquals("音乐库", experience.tabs.single { it.id == ExperienceTabId.Library }.title)
        }

        // Product capabilities are still allowed to differ. Only presentation is
        // canonical, so future iOS UI migrations do not require provider forks.
        assertTrue(MusicExperiences.netease.providerNativeCapabilities.isNotEmpty())
        assertNotEquals(MusicExperiences.netease.homeSections, MusicExperiences.kugou.homeSections)
    }

    @Test
    fun qqAndKugouExposeTheSameCommonCatalogSurfaceWithoutSharingAuthModels() {
        val qq = QQMusicProvider(
            sessionProvider = { QQMusicSession(cookie = "", uin = "", musicKey = "") },
        )
        val kugou = KugouProvider(
            sessionProvider = {
                KugouSession(
                    token = "",
                    userId = 0L,
                    vipToken = "",
                    vipType = 0,
                    dfid = "-",
                    mid = "test-mid",
                    guid = "test-guid",
                    dev = "test-device",
                    mac = "00:00:00:00:00:00",
                    webGl = "test-webgl",
                )
            },
        )

        listOf(qq, kugou).forEach { provider ->
            assertTrue(provider is CatalogSearchCapability)
            assertTrue(provider is AlbumCapability)
            assertTrue(provider is ArtistCapability)
            assertTrue(MusicCapability.Albums in provider.capabilities)
            assertTrue(MusicCapability.Artists in provider.capabilities)
        }
    }

    @Test
    fun spotifySourceRoundTripsAndCanBeRegistered() {
        assertEquals(MusicSource.Spotify, MusicSource.fromStorageValue("spotify"))
        val spotify = object : MusicProvider {
            override val source = MusicSource.Spotify
            override val displayName = source.displayName
            override val capabilities = setOf(MusicCapability.Search, MusicCapability.Playback)
        }
        val registry = MusicProviderRegistry(listOf(spotify))
        assertEquals(spotify, registry.require(MusicSource.Spotify))
    }
}
