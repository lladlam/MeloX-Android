package com.lladlam.melox.core.provider.thirdparty

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.music.model.SourceId
import org.junit.Assert.*
import org.junit.Test

class ResolverProtocolTest {
    private fun settings(kind: ResolverKind, endpoint: String = "https://resolver.test") =
        ResolverSettings(kind, endpoint, credential = "secret", enabled = true)

    private fun track(source: MusicSource = MusicSource.Netease, id: String = "123") =
        MusicTrack(MusicResourceId(source, id), "", emptyList())

    @Test fun presetTestSongWorksForEveryConfiguredResolver() {
        assertTrue(ResolverTestSongId > 0)
        val testTrack = track(id = ResolverTestSongId.toString()).copy(
            providerMetadata = ProviderTrackMetadata.Netease(ResolverTestSongId),
        )
        for (kind in ResolverKind.entries) {
            val url = resolverRequestUrl(settings(kind), testTrack, AudioQualityTier.High)!!
            assertEquals(ResolverTestSongId.toString(), url.queryParameter("id"))
        }
    }

    @Test fun metingPreservesFullEndpointAndExistingQuery() {
        for (path in listOf("/", "/api.php", "/meting/", "/api")) {
            val url = resolverRequestUrl(settings(ResolverKind.Meting, "https://resolver.test$path?tenant=one"),
                track(), AudioQualityTier.High)!!
            assertEquals(path, url.encodedPath)
            assertEquals("one", url.queryParameter("tenant"))
            assertEquals("netease", url.queryParameter("server"))
            assertEquals("url", url.queryParameter("type"))
            assertEquals("320", url.queryParameter("br"))
            assertEquals("exhigh", url.queryParameter("level"))
            assertEquals("secret", url.queryParameter("auth"))
        }
    }

    @Test fun idsAreNeverReusedAcrossPlatforms() {
        val qq = track(MusicSource.QQMusic, "generic").copy(providerMetadata = ProviderTrackMetadata.QQMusic("qqmid"))
        val meting = resolverRequestUrl(settings(ResolverKind.Meting), qq, AudioQualityTier.High)!!
        assertEquals("tencent", meting.queryParameter("server"))
        assertEquals("qqmid", meting.queryParameter("id"))
        val chksz = resolverRequestUrl(settings(ResolverKind.Chksz), qq, AudioQualityTier.High)!!
        assertEquals("/api/qq_music", chksz.encodedPath)
        assertEquals("qqmid", chksz.queryParameter("mid"))
        assertNull(resolverRequestUrl(settings(ResolverKind.Unm), qq, AudioQualityTier.High))
    }

    @Test fun chkszAndUnmDoNotDuplicateEndpointSuffixes() {
        for (base in listOf("https://resolver.test", "https://resolver.test/api/")) {
            val url = resolverRequestUrl(settings(ResolverKind.Chksz, base), track(), AudioQualityTier.High)!!
            assertEquals("/api/163_music", url.encodedPath)
            assertEquals("secret", url.queryParameter("apikey"))
        }
        for (base in listOf("http://resolver.test", "http://resolver.test/match")) {
            val url = resolverRequestUrl(settings(ResolverKind.Unm, base), track(), AudioQualityTier.High)!!
            assertEquals("/match", url.encodedPath)
            assertEquals("123", url.queryParameter("id"))
            assertEquals("kuwo,kugou,bilibili", url.queryParameter("server"))
        }
    }

    @Test fun unconfiguredDisabledAndPluginSourcesDoNotGenerateRequests() {
        assertNull(resolverRequestUrl(settings(ResolverKind.Meting).copy(enabled = false), track(), AudioQualityTier.High))
        assertNull(resolverRequestUrl(settings(ResolverKind.Meting).copy(endpoint = ""), track(), AudioQualityTier.High))
        assertNull(resolverRequestUrl(settings(ResolverKind.Chksz).copy(credential = ""), track(), AudioQualityTier.High))
        val plugin = MusicTrack(MusicResourceId(SourceId.parse("plugin:org.example.test")!!, "123"), "", emptyList())
        assertNull(resolverRequestUrl(settings(ResolverKind.Meting), plugin, AudioQualityTier.High))
        for (source in listOf(MusicSource.Local, MusicSource.Jellyfin, MusicSource.Bilibili)) {
            assertNull(resolverRequestUrl(settings(ResolverKind.Meting), track(source), AudioQualityTier.High))
        }
    }

    @Test fun jsonParserAcceptsExplicitAudioFieldsButNeverArtworkOrErrorLinks() {
        assertEquals("https://cdn.test/song.mp3", ResolverAudioUrl.parse("""{"data":[{"url":"https://cdn.test/song.mp3","pic":"https://cdn.test/cover.jpg"}]}"""))
        assertEquals("http://cdn.test/song.flac", ResolverAudioUrl.parse("""{"result":{"audioUrl":"http://cdn.test/song.flac"}}"""))
        assertEquals("https://cdn.test/song.mp3", ResolverAudioUrl.parse("https://cdn.test/song.mp3"))
        assertNull(ResolverAudioUrl.parse("""{"pic":"https://cdn.test/cover.jpg","share":"https://site.test/song"}"""))
        assertNull(ResolverAudioUrl.parse("""{"url":"https://cdn.test/cover.jpg"}"""))
        assertNull(ResolverAudioUrl.parse("""{"code":403,"data":{"url":"https://site.test/login"}}"""))
        assertNull(ResolverAudioUrl.parse("""{"success":false,"url":"https://site.test/error"}"""))
        assertNull(ResolverAudioUrl.parse("<html>https://site.test/error</html>"))
        assertNull(ResolverAudioUrl.parse("file:///sdcard/song.mp3"))
        assertNull(ResolverAudioUrl.parse("[".repeat(17) + "0" + "]".repeat(17)))
        assertEquals("https://cdn.test/song.mp3", ResolverAudioUrl.parse("""{"data":{"url":"https://cdn.test/song.mp3","name":"[song]"}}"""))
    }

    @Test fun credentialsDoNotLeakIntoCacheIdentities() {
        val one = settings(ResolverKind.Chksz)
        assertFalse(one.cacheIdentity().contains("secret"))
        assertNotEquals(one.cacheIdentity(), one.copy(credential = "other").cacheIdentity())
        assertFalse(validResolverEndpoint("https://user:password@resolver.test/"))
        assertFalse(validResolverEndpoint("file:///sdcard/music"))
    }
}
