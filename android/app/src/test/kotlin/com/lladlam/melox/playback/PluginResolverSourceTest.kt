package com.lladlam.melox.playback

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.plugin.PluginCapability
import com.lladlam.melox.core.plugin.PluginInvoker
import com.lladlam.melox.core.plugin.PluginManifest
import com.lladlam.melox.core.plugin.PluginMusicProvider
import com.lladlam.melox.core.music.model.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginResolverSourceTest {
    private val source = SourceId.parse("plugin:org.example.resolver")!!
    private val foreign = MusicTrack(
        MusicResourceId(MusicSource.QQMusic, "003abc"),
        "A Song",
        listOf(MusicArtistRef(name = "The Artist")),
        durationMs = 200_000L,
    )

    private fun plugin(version: String = "1.0.0", resolver: Boolean = true): PluginMusicProvider {
        val capabilities = buildSet {
            add(PluginCapability.Resolver)
            if (!resolver) remove(PluginCapability.Resolver)
        }
        return PluginMusicProvider(
            PluginManifest(source, "Resolver", version, 1, "index.js", capabilities, setOf("api.example.com")),
            PluginInvoker { method, _ ->
                require(method == "resolveFallback")
                """{"type":"playable","url":"http://cdn.example/audio.mp3","headers":{"X-Test":"1"},"actualQuality":"high","expiresAtEpochMs":${System.currentTimeMillis() + 60_000}}"""
            },
        )
    }

    @Test
    fun resolverPluginRunsBeforeLxSourcesAndMapsHeadersAndExpiry() {
        val calls = mutableListOf<String>()
        val pluginSource = PluginResolverSource({ listOf(plugin()) })
        val resolver = ThirdPartyPlaybackResolver(
            enabledProvider = { true },
            membershipOnlyProvider = { false },
            sources = listOf(
                ThirdPartySource { _, _ -> calls += "configured"; null },
                pluginSource,
                ThirdPartySource { _, _ -> calls += "lx"; ThirdPartyTrack("https://lx.example/audio", sourceLabel = "lx:test") },
            ),
        )

        val result = resolver.resolve(foreign, AudioQualityTier.High, membershipFailure = true)

        assertEquals("http://cdn.example/audio.mp3", result?.url)
        assertEquals("1", result?.requestHeaders?.get("X-Test"))
        assertEquals("plugin:org.example.resolver", result?.sourceLabel)
        assertTrue((result?.expiresAtEpochMs ?: 0L) > System.currentTimeMillis())
        assertEquals(listOf("configured"), calls)
    }

    @Test
    fun cacheIdentityIncludesThePluginVersion() {
        assertEquals(
            "plugin:org.example.resolver@1.0.0",
            PluginResolverSource({ listOf(plugin()) }).cacheIdentity(),
        )
        assertEquals(
            "plugin:org.example.resolver@1.0.1",
            PluginResolverSource({ listOf(plugin("1.0.1")) }).cacheIdentity(),
        )
    }

    @Test
    fun nonResolverPluginsAreIgnored() {
        val noResolver = PluginMusicProvider(
            PluginManifest(source, "Normal", "1.0.0", 1, "index.js",
                setOf(PluginCapability.Search), setOf("api.example.com")),
            PluginInvoker { _, _ -> error("must not run") },
        )
        assertNull(PluginResolverSource({ listOf(noResolver) }).resolve(foreign, AudioQualityTier.High))
    }
}
