package com.lladlam.melox.core.plugin

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicAuthenticationState
import com.lladlam.melox.core.music.model.PlaybackResolution
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** Executes the SDK-built CC0 catalog through the real worker, not Node vm or a stub invoker. */
internal object PluginRcCatalogChecks {
    fun run(test: Instrumentation, evidence: MutableList<String>) = runBlocking {
        val fixture = File(test.targetContext.cacheDir, "plugin-rc-catalog.melox-plugin")
        require(fixture.length() in 1..4 * 1024 * 1024)
        val pkg = PluginArchiveReader.read(fixture.readBytes())
        require(pkg.manifest.sourceId.value == "plugin:org.melox.catalog-demo" && pkg.manifest.capabilities.size == 12 && pkg.integrityChecked && pkg.publisher != null)
        val root = File(test.targetContext.cacheDir, "plugin-rc-catalog-${UUID.randomUUID()}").apply { mkdirs() }
        val sandbox = object : ContextWrapper(test.targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = root
        }
        try {
            PluginRuntime(sandbox, pkg).use { runtime ->
                val methods = JSONArray(runtime.invoke("__validate"))
                val actual = (0 until methods.length()).map { methods.getString(it) }.toSet()
                check(pkg.manifest.capabilities.flatMap { it.methods }.all { it in actual })
                val provider = PluginMusicProvider(pkg.manifest, runtime)
                val track = provider.searchSongs("tone", 1, 10).items.single()
                check(track.id.sourceId == pkg.manifest.sourceId && track.artists.single().name.isNotBlank())
                check(provider.searchSongs("tone", 2, 10).items.isEmpty())
                check(provider.resolvePlayback(track, AudioQualityTier.Standard) is PlaybackResolution.Playable)
                check(provider.resolveDownload(track, AudioQualityTier.Standard) is PlaybackResolution.Playable)
                check(provider.lyrics(track).lines.isNotEmpty())
                val playlist = provider.playlists(1, 10).items.single()
                check(provider.searchPlaylists("signals", 1, 10).items.single().id == playlist.id)
                provider.playlistDetail(playlist, 1, 10)
                val album = provider.searchAlbums("signals", 1, 10).items.single()
                provider.albumDetail(album, 1, 10)
                val artist = provider.searchArtists("example", 1, 10).items.single()
                provider.artistDetail(artist, 1, 10)
                val ranking = provider.rankings(1, 10).items.single()
                check(provider.rankingTracks(ranking, 1, 10).items.single().id == track.id)
                provider.homeFeed(1, 1, 1)
                check(provider.accountSummary() == null)
                check(provider.userPlaylists(1, 10).items.single().id == playlist.id)
                check(provider.savedAlbums(1, 10).items.single().id == album.id)
                check(provider.followedArtists(1, 10).items.single().id == artist.id)
                check(!provider.isFavorite(track)); provider.setFavorite(track, true)
                check(provider.isFavorite(track)); provider.setFavorite(track, false)
                check(!provider.isFavorite(track))
                check(provider.authenticationStatus() == MusicAuthenticationState.NotRequired)
            }
            evidence += "PASS SDK catalog archive integrity/signature, all 12 Capability Provider paths through isolated QuickJS, collections/details/pagination and cross-context favorite writes (not full UI/OAuth/download bytes)"
        } finally { root.deleteRecursively(); fixture.delete() }
    }
}
