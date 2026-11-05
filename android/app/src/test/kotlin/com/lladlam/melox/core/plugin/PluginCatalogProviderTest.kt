package com.lladlam.melox.core.plugin

import com.lladlam.melox.core.music.model.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PluginCatalogProviderTest {
    private val source = SourceId.parse("plugin:org.example.catalog")!!
    private val track = """{"id":"track/1","title":"Test","artists":[{"name":"Author"}]}"""
    private fun page(item: String) = """{"items":[$item],"page":1,"pageSize":30,"total":1,"hasMore":false}"""
    private fun provider(capabilities: Set<PluginCapability> = PluginCapability.entries.toSet(), invoke: suspend (String) -> String): PluginMusicProvider =
        PluginMusicProvider(PluginManifest(source, "Catalog", "1.0.0", 1, "index.js", capabilities, setOf("example.com")),
            PluginInvoker { method, _ -> invoke(method) })

    @Test fun catalogDetailsAndPagesKeepPluginIdentityAndTotals() = runBlocking {
        val playlist = """{"id":"p","title":"Playlist","trackCount":1}"""
        val album = """{"id":"a","title":"Album","artists":[{"id":"artist","name":"Author"}]}"""
        val artist = """{"id":"artist","name":"Author"}"""
        val ranking = """{"id":"r","title":"Ranking"}"""
        val plugin = provider { method -> when (method) {
            "playlists", "searchPlaylists", "userPlaylists" -> page(playlist)
            "searchAlbums", "savedAlbums" -> page(album)
            "searchArtists", "followedArtists" -> page(artist)
            "rankings" -> page(ranking)
            "playlistDetail" -> """{"summary":$playlist,"tracks":${page(track)}}"""
            "albumDetail" -> """{"summary":$album,"tracks":${page(track)}}"""
            "artistDetail" -> """{"summary":$artist,"tracks":${page(track)}}"""
            "rankingTracks", "search" -> page(track)
            "homeFeed" -> """{"recommendedPlaylists":[$playlist],"newSongs":[$track],"rankings":[$ranking]}"""
            "accountSummary" -> """{"id":"account","displayName":"User"}"""
            else -> error(method)
        } }
        val p = plugin.playlists(1, 30).items.single()
        val a = plugin.searchAlbums("", 1, 30).items.single()
        val ar = plugin.searchArtists("", 1, 30).items.single()
        val r = plugin.rankings(1, 30).items.single()
        assertEquals(source, p.id.sourceId)
        assertEquals(source, a.artists.single().id?.sourceId)
        assertEquals(source, plugin.playlistDetail(p, 1, 30).tracks.single().id.sourceId)
        assertEquals(1L, plugin.albumDetail(a, 1, 30).totalTracks)
        assertEquals(1L, plugin.artistDetail(ar, 1, 30).totalTracks)
        assertEquals(1L, plugin.rankingTracks(r, 1, 30).total)
        assertEquals(1L, plugin.searchSongs("test", 1, 30).total)
        assertEquals(source, plugin.accountSummary()?.sourceId)
        assertEquals(p.id, plugin.userPlaylists(1, 30).items.single().id)
        assertEquals(a.id, plugin.savedAlbums(1, 30).items.single().id)
        assertEquals(ar.id, plugin.followedArtists(1, 30).items.single().id)
        assertEquals(p.id, plugin.homeFeed(1, 1, 1).recommendedPlaylists.single().id)
    }

    @Test fun missingCapabilityNeverInvokesScriptAndCrossSourceInputIsRejected() = runBlocking {
        var calls = 0
        val plugin = provider(emptySet()) { calls++; "null" }
        try { plugin.playlists(1, 30); fail("unsupported operation succeeded") } catch (_: IllegalArgumentException) { }
        assertEquals(0, calls)
        val full = provider { calls++; "null" }
        val foreign = MusicTrack(MusicResourceId(MusicSource.Netease, "1"), "Test", emptyList())
        try { full.setFavorite(foreign, true); fail("foreign track accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(0, calls)
    }

    @Test fun favoritesRequireBooleanStateAndVoidAcknowledgement() = runBlocking {
        var favorite = false
        val plugin = provider { method -> when (method) {
            "isFavorite" -> favorite.toString()
            "setFavorite" -> { favorite = true; "null" }
            else -> error(method)
        } }
        val input = MusicTrack(MusicResourceId(source, "1"), "Test", emptyList())
        assertFalse(plugin.isFavorite(input)); plugin.setFavorite(input, true); assertTrue(plugin.isFavorite(input))
        val invalid = provider { "\"true\"" }
        try { invalid.isFavorite(input); fail("coerced favorite state") } catch (_: IllegalArgumentException) { }
    }
}
