package com.lladlam.melox.core.plugin

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.model.SourceId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PluginResolverProviderTest {
    private val source = SourceId.parse("plugin:org.example.resolver")!!
    private val foreign = MusicTrack(
        MusicResourceId(MusicSource.Netease, "2142857204"),
        "雑踏、僕らの街",
        listOf(MusicArtistRef(name = "トゲナシトゲアリ")),
        durationMs = 184_293L,
    )

    private fun manifest(vararg capabilities: PluginCapability) = PluginManifest(
        source, "Resolver", "1.0.0", 1, "index.js", capabilities.toSet(), setOf("api.example.com"),
    )

    @Test
    fun resolverAcceptsForeignTrackAndExternalHttpAudio() = runBlocking {
        var invoked = 0
        val plugin = PluginMusicProvider(manifest(PluginCapability.Resolver), PluginInvoker { method, args ->
            invoked++
            assertEquals("resolveFallback", method)
            val track = args.getJSONObject(0)
            assertEquals("netease", track.getString("source"))
            assertEquals("2142857204", track.getString("id"))
            assertEquals("high", args.getString(1))
            """{"type":"playable","url":"http://cdn.example/audio.mp3","actualQuality":"high","expiresAtEpochMs":${System.currentTimeMillis() + 60_000}}"""
        })

        val result = plugin.resolveFallback(foreign, AudioQualityTier.High)

        assertEquals(1, invoked)
        assertTrue(result is PlaybackResolution.Playable)
        result as PlaybackResolution.Playable
        assertEquals("http://cdn.example/audio.mp3", result.url)
        assertEquals(AudioQualityTier.High, result.actualQuality)
        assertTrue(result.expiresAtEpochMs!! > System.currentTimeMillis())
    }

    @Test
    fun resolverCannotUseHostCredentialsOrNonHttpAudio() = runBlocking {
        val credentialPlugin = PluginMusicProvider(manifest(PluginCapability.Resolver), PluginInvoker { _, _ ->
            """{"type":"playable","url":"http://cdn.example/audio.mp3","credential":"active"}"""
        })
        try {
            credentialPlugin.resolveFallback(foreign, AudioQualityTier.High)
            fail("resolver credential accepted")
        } catch (_: IllegalArgumentException) { }

        val ftpPlugin = PluginMusicProvider(manifest(PluginCapability.Resolver), PluginInvoker { _, _ ->
            """{"type":"playable","url":"ftp://cdn.example/audio.mp3"}"""
        })
        try {
            ftpPlugin.resolveFallback(foreign, AudioQualityTier.High)
            fail("non-HTTP resolver audio accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun resolverCapabilityIsRequiredAndDoesNotReplaceOwnedPlayback() = runBlocking {
        var invoked = 0
        val noResolver = PluginMusicProvider(manifest(PluginCapability.Search), PluginInvoker { _, _ -> invoked++; "null" })
        try {
            noResolver.resolveFallback(foreign, AudioQualityTier.High)
            fail("undeclared resolver invoked")
        } catch (_: IllegalArgumentException) { }
        assertEquals(0, invoked)

        val full = PluginMusicProvider(manifest(PluginCapability.Playback, PluginCapability.Resolver), PluginInvoker { _, _ ->
            invoked++
            """{"type":"playable","url":"https://api.example.com/audio.mp3"}"""
        })
        try {
            full.resolvePlayback(foreign, AudioQualityTier.High)
            fail("foreign track accepted by owned playback")
        } catch (_: IllegalArgumentException) { }
        assertEquals(0, invoked)
    }

    @Test
    fun resolverOnlyManifestIsNotAMusicSource() {
        assertFalse(manifest(PluginCapability.Resolver).isMusicSource)
        assertFalse(manifest(PluginCapability.Resolver, PluginCapability.Authentication).isMusicSource)
        assertTrue(manifest(PluginCapability.Search, PluginCapability.Resolver).isMusicSource)
    }
}
