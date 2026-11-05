package com.lladlam.melox.core.plugin

import android.app.Instrumentation
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.PlaybackResolution
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Same full-field fixture as JVM/Schema tests, executed in actual isolated QuickJS contexts. */
internal object PluginRcWireChecks {
    fun run(test: Instrumentation, evidence: MutableList<String>) = runBlocking {
        val file = File(test.targetContext.cacheDir, "plugin-rc-wire-cases.json")
        require(file.length() in 1..64 * 1024)
        val fixture = JSONObject(file.readText())
        val models = fixture.getJSONObject("models")
        val values = JSONObject(models.toString())
        fun page(model: String) = JSONObject().put("items", JSONArray().put(models.getJSONObject(model)))
            .put("page", 1).put("pageSize", 10).put("total", 1).put("hasMore", false)
        values.put("searchPage", page("track"))
        for (model in listOf("playlist", "album", "artist", "ranking")) values.put(model + "Page", page(model))
        for (model in listOf("playlist", "album", "artist")) values.put(model + "Detail", JSONObject().put("summary", models.getJSONObject(model)).put("tracks", page("track")))
        values.put("homeFeed", JSONObject().put("recommendedPlaylists", JSONArray().put(models.getJSONObject("playlist")))
            .put("newSongs", JSONArray().put(models.getJSONObject("track"))).put("rankings", JSONArray().put(models.getJSONObject("ranking"))))
        values.put("accountSummary", models.getJSONObject("account"))
        values.put("authenticationStatus", JSONObject().put("type", "signedIn").put("account", models.getJSONObject("account")))
        values.put("favoriteState", true).put("writeAcknowledgement", JSONObject.NULL)
        val methods = fixture.getJSONObject("methods")
        check(methods.keys().asSequence().toSet() == PluginCapability.entries.flatMap { it.methods }.toSet())
        val manifest = PluginManifestParser.parse(JSONObject().put("id", "org.melox.rc.wire-fields." + java.util.UUID.randomUUID().toString().replace("-", "")).put("name", "Wire fields")
            .put("version", "1.0.0").put("apiVersion", 1).put("entry", "index.js")
            .put("capabilities", JSONArray(PluginCapability.entries.map { it.wireName })).put("networkDomains", JSONArray().put("example.com"))
            .put("authentication", JSONObject().put("type", "apiToken").put("header", "Authorization").put("prefix", "Bearer ")
                .put("domains", JSONArray().put("example.com"))).toString())
        val script = "const values=$values;const methods=$methods;globalThis.meloxPlugin={};" +
            "Object.keys(methods).forEach(method=>{meloxPlugin[method]=async()=>values[methods[method]]});"
        val root = File(test.targetContext.cacheDir, "rc-wire-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val sandbox = object : android.content.ContextWrapper(test.targetContext) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getNoBackupFilesDir(): File = root
        }
        val vault = PluginAccountVault(sandbox, manifest.sourceId)
        try {
            val account = vault.importToken("Synthetic RC", "synthetic-wire-fixture", requireNotNull(manifest.authentication))
            vault.activate(account.id)
            PluginRuntime(sandbox, PluginArchiveReader.Package(manifest, script)).use { runtime ->
                for (method in methods.keys()) {
                    val raw = runtime.invoke(method)
                    PluginWireContract.check(method, raw)
                    // Wrap primitives too, so strings/null/numbers are compared as JSON values.
                    val actual = JSONArray("[$raw]").get(0)
                    equal(values.get(methods.getString(method)), actual)
                }
                val provider = PluginMusicProvider(manifest, runtime)
                val track = provider.searchSongs("Title", 1, 10).items.single()
                check(track.title == "Title" && track.durationMs == 120_000L && track.bestBitrate == 1_000_000)
                check(track.artists.single().id?.value == "artist" && track.album?.id?.value == "album")
                check(track.id.sourceId == manifest.sourceId && track.album?.id?.sourceId == manifest.sourceId)
                val playlist = provider.playlists(1, 10).items.single()
                check(playlist.description == "Description" && playlist.creatorName == "Creator")
                check(provider.searchPlaylists("Playlist", 1, 10).items.single().id == playlist.id)
                check(provider.playlistDetail(playlist, 1, 10).tracks.single().id == track.id)
                val album = provider.searchAlbums("Album", 1, 10).items.single()
                check(album.releaseDate == "2026-11-05" && album.artists.single().id?.value == "artist")
                check(provider.albumDetail(album, 1, 10).tracks.single().id == track.id)
                val artist = provider.searchArtists("Artist", 1, 10).items.single()
                check(artist.description == "Description")
                check(provider.artistDetail(artist, 1, 10).tracks.single().id == track.id)
                val ranking = provider.rankings(1, 10).items.single()
                check(provider.rankingTracks(ranking, 1, 10).items.single().id == track.id)
                provider.homeFeed(1, 1, 1)
                check(provider.accountSummary()?.id == "account")
                check(provider.userPlaylists(1, 10).items.single().id == playlist.id)
                check(provider.savedAlbums(1, 10).items.single().id == album.id)
                check(provider.followedArtists(1, 10).items.single().id == artist.id)
                check(provider.isFavorite(track)); provider.setFavorite(track, false)
                provider.authenticationStatus()
                for (resolution in listOf(provider.resolvePlayback(track, AudioQualityTier.Lossless), provider.resolveDownload(track, AudioQualityTier.Lossless))) {
                    val playable = resolution as? PlaybackResolution.Playable ?: error("not playable")
                    check(playable.actualQuality == AudioQualityTier.Lossless && playable.bitrate == 1_000_000 && playable.format == "flac")
                }
                val lyric = provider.lyrics(track).lines.single()
                check(lyric.text == "Text" && lyric.translation == "Translation" && lyric.romanization == "Romanization")
            }
            evidence += "PASS shared full-field fixture: all API-v1 methods preserve raw fields through isolated QuickJS and all 12 Capability Provider mappings execute (not UI/network acceptance)"
        } finally { vault.clear(); root.deleteRecursively(); file.delete() }
    }

    private fun equal(expected: Any, actual: Any) {
        when (expected) {
            is JSONObject -> {
                check(actual is JSONObject)
                check(expected.keys().asSequence().toSet() == actual.keys().asSequence().toSet())
                expected.keys().forEach { equal(expected.get(it), actual.get(it)) }
            }
            is JSONArray -> {
                check(actual is JSONArray && actual.length() == expected.length())
                for (i in 0 until expected.length()) equal(expected.get(i), actual.get(i))
            }
            is Number -> check(actual is Number && actual.toDouble() == expected.toDouble())
            else -> check(expected == actual)
        }
    }
}
