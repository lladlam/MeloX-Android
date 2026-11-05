package com.lladlam.melox.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lladlam.melox.core.music.model.*
import com.lladlam.melox.core.music.provider.MusicProviderRegistry
import com.lladlam.melox.core.plugin.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Source-neutral collection navigation inside the existing Search screen. */
@Composable
internal fun PluginCatalogBrowser(
    registry: MusicProviderRegistry,
    sourceValue: String,
    query: String,
    generation: Long,
    initialSection: String = "songs",
    trackContent: @Composable (List<MusicTrack>) -> Unit,
) {
    val provider = SourceId.parse(sourceValue)?.let { registry[it] } as? PluginMusicProvider
    key(sourceValue, provider) {
        PluginCatalogContent(registry, sourceValue, query, generation, initialSection, provider, trackContent)
    }
}

@Composable
private fun PluginCatalogContent(
    registry: MusicProviderRegistry,
    sourceValue: String,
    query: String,
    generation: Long,
    initialSection: String,
    provider: PluginMusicProvider?,
    trackContent: @Composable (List<MusicTrack>) -> Unit,
) {
    var section by remember(sourceValue, initialSection) { mutableStateOf(initialSection) }
    var collection by remember(sourceValue, section, query) { mutableStateOf<Any?>(null) }
    var page by remember(sourceValue, section, query, collection) { mutableIntStateOf(1) }
    var rows by remember { mutableStateOf<List<Any>>(emptyList()) }
    var tracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var accountName by remember { mutableStateOf<String?>(null) }
    val sections = buildList {
        fun addIf(capability: PluginCapability, key: String, label: String) {
            if (provider?.manifest?.capabilities?.contains(capability) == true) add(key to label)
        }
        addIf(PluginCapability.Search, "songs", "歌曲")
        addIf(PluginCapability.Playlists, "playlists", "歌单")
        addIf(PluginCapability.Albums, "albums", "专辑")
        addIf(PluginCapability.Artists, "artists", "歌手")
        addIf(PluginCapability.Rankings, "rankings", "榜单")
        addIf(PluginCapability.HomeFeed, "home", "推荐")
        addIf(PluginCapability.UserLibrary, "library", "我的歌单")
        addIf(PluginCapability.UserLibrary, "savedAlbums", "收藏专辑")
        addIf(PluginCapability.UserLibrary, "followedArtists", "关注歌手")
    }
    LaunchedEffect(sourceValue, generation) {
        if (sections.none { it.first == section }) section = sections.firstOrNull()?.first ?: "songs"
    }
    LaunchedEffect(sourceValue, query, generation, section, collection, page) {
        rows = emptyList(); tracks = emptyList(); error = null; hasMore = false; accountName = null
        if (provider == null) { error = "音源已禁用或卸载：$sourceValue"; return@LaunchedEffect }
        if (sections.none { it.first == section }) return@LaunchedEffect
        loading = true
        try {
            delay(250)
            val size = 30
            fun <T> apply(result: MusicPage<T>) {
                hasMore = result.hasMore
                val values = result.items
                if (values.all { it is MusicTrack }) tracks = values.filterIsInstance<MusicTrack>()
                else rows = values.filterNotNull()
            }
            when (val selected = collection) {
                is MusicPlaylistSummary -> provider.playlistDetail(selected, page, size).let {
                    tracks = it.tracks; hasMore = it.hasMore ?: (it.total?.let { total -> page.toLong() * size < total } ?: (tracks.size == size))
                }
                is MusicAlbumSummary -> provider.albumDetail(selected, page, size).let {
                    tracks = it.tracks; hasMore = it.hasMore ?: (it.totalTracks?.let { total -> page.toLong() * size < total } ?: (tracks.size == size))
                }
                is MusicArtistSummary -> provider.artistDetail(selected, page, size).let {
                    tracks = it.tracks; hasMore = it.hasMore ?: (it.totalTracks?.let { total -> page.toLong() * size < total } ?: (tracks.size == size))
                }
                is MusicRankingSummary -> apply(provider.rankingTracks(selected, page, size))
                else -> when (section) {
                    "songs" -> if (query.isNotBlank()) apply(provider.searchSongs(query.trim(), page, size))
                    "playlists" -> apply(if (query.isBlank()) provider.playlists(page, size) else provider.searchPlaylists(query.trim(), page, size))
                    "albums" -> apply(provider.searchAlbums(query.trim(), page, size))
                    "artists" -> apply(provider.searchArtists(query.trim(), page, size))
                    "rankings" -> apply(provider.rankings(page, size))
                    "home" -> provider.homeFeed(30, 30, 30).let { rows = it.recommendedPlaylists + it.rankings; tracks = it.newSongs }
                    "library" -> { accountName = provider.accountSummary()?.displayName; apply(provider.userPlaylists(page, size)) }
                    "savedAlbums" -> apply(provider.savedAlbums(page, size))
                    "followedArtists" -> apply(provider.followedArtists(page, size))
                }
            }
            if (registry.generation != generation || registry[provider.sourceId] !== provider) {
                rows = emptyList(); tracks = emptyList(); error = "音源状态已变化"
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = com.lladlam.melox.core.plugin.PluginErrors.describe(failure) }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
            items(sections) { (key, label) -> TextButton(onClick = { section = key }) { Text(if (section == key) "✓ $label" else label) } }
        }
        if (collection != null) TextButton(onClick = { collection = null }) { Text("返回列表") }
        accountName?.let { Text(it, Modifier.padding(horizontal = 20.dp)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = page > 1 && !loading, onClick = { page-- }) { Text("上一页") }
            Text("第 $page 页", Modifier.padding(12.dp))
            TextButton(enabled = hasMore && !loading, onClick = { page++ }) { Text("下一页") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error) }
        if (rows.isNotEmpty()) LazyColumn(Modifier.weight(if (tracks.isEmpty()) 1f else .45f)) {
            items(rows) { value ->
                val title = when (value) {
                        is MusicPlaylistSummary -> "歌单 · ${value.title}"
                        is MusicAlbumSummary -> "专辑 · ${value.title}"
                        is MusicArtistSummary -> "歌手 · ${value.name}"
                        is MusicRankingSummary -> "榜单 · ${value.title}"
                        else -> ""
                }
                val artwork = when (value) {
                    is MusicPlaylistSummary -> value.artworkUrl
                    is MusicAlbumSummary -> value.artworkUrl
                    is MusicArtistSummary -> value.artworkUrl
                    is MusicRankingSummary -> value.artworkUrl
                    else -> null
                }
                com.lladlam.melox.ui.glass.MeloXIosListRow(title = title, onClick = { collection = value }, leading = {
                    if (artwork != null) coil3.compose.AsyncImage(model = artwork, contentDescription = null, modifier = Modifier.size(44.dp))
                })
            }
        }
        if (!loading && error == null && rows.isEmpty() && tracks.isEmpty()) Text("暂无内容", Modifier.padding(20.dp))
        if (tracks.isNotEmpty()) Box(Modifier.weight(1f)) { trackContent(tracks) }
    }
}
