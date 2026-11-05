package com.lladlam.melox.ui.collection

import androidx.compose.runtime.saveable.rememberSaveable
import com.lladlam.melox.ui.state.rememberRetainedPageState

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import kotlinx.coroutines.CancellationException
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import com.lladlam.melox.ui.library.MeloXUnifiedAlbumDetailScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.lladlam.melox.R
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXAlbumSummary
import com.lladlam.melox.core.network.MeloXArtistDetail
import com.lladlam.melox.core.network.NeteaseCollectionDetailsClient
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.ui.glass.MeloXActionIcon
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassIconButton
import com.lladlam.melox.ui.glass.MeloXShapes
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.player.MeloXSongActionsOverlay
import com.lladlam.melox.ui.search.MeloXSearchLaunchBus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeloXArtistDetailScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val client = remember(app) { NeteaseCollectionDetailsClient(cookieProvider = { NeteaseSessionStore.readCookie(app) }) }
    val scope = rememberCoroutineScope()
    var detail by rememberRetainedPageState(id) { mutableStateOf<MeloXArtistDetail?>(null) }
    var loading by remember(id) { mutableStateOf(detail == null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var followed by rememberSaveable(id) { mutableStateOf<Boolean?>(null) }
    var followBusy by remember(id) { mutableStateOf(false) }
    var selectedSong by remember(id) { mutableStateOf<SearchSong?>(null) }
    var allSongs by rememberRetainedPageState(id) { mutableStateOf<List<SearchSong>>(emptyList()) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var expandedSection by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var selectedAlbumId by rememberSaveable(id) { mutableStateOf<Long?>(null) }

    val expandedSectionBackProgress = remember { Animatable(0f) }
    PredictiveBackHandler(enabled = expandedSection != null && selectedAlbumId == null) {
        try {
            it.collect { event -> expandedSectionBackProgress.snapTo(event.progress) }
            expandedSectionBackProgress.animateTo(1f, tween(160))
            expandedSection = null
            expandedSectionBackProgress.snapTo(0f)
        } catch (_: CancellationException) {
            expandedSectionBackProgress.animateTo(0f)
        }
    }
    BackHandler(enabled = expandedSection != null && selectedAlbumId == null) {
        scope.launch {
            if (expandedSectionBackProgress.value < 1f) {
                expandedSectionBackProgress.animateTo(1f, tween(160))
            }
            expandedSection = null
            expandedSectionBackProgress.snapTo(0f)
        }
    }

    val selectedAlbumBackProgress = remember { Animatable(0f) }
    PredictiveBackHandler(enabled = selectedAlbumId != null) {
        try {
            it.collect { event -> selectedAlbumBackProgress.snapTo(event.progress) }
            selectedAlbumBackProgress.animateTo(1f, tween(160))
            selectedAlbumId = null
            selectedAlbumBackProgress.snapTo(0f)
        } catch (_: CancellationException) {
            selectedAlbumBackProgress.animateTo(0f)
        }
    }
    BackHandler(enabled = selectedAlbumId != null) {
        scope.launch {
            if (selectedAlbumBackProgress.value < 1f) {
                selectedAlbumBackProgress.animateTo(1f, tween(160))
            }
            selectedAlbumId = null
            selectedAlbumBackProgress.snapTo(0f)
        }
    }

    LaunchedEffect(id, detail) {
        if (detail == null || allSongs.isNotEmpty()) return@LaunchedEffect
        val collected = mutableListOf<SearchSong>()
        var offset = 0
        while (offset < 500) {
            val page = runCatching { client.artistSongs(id, 100, offset) }.getOrDefault(emptyList())
            if (page.isEmpty()) break
            collected += page
            if (page.size < 100) break
            offset += page.size
        }
        allSongs = collected
    }

    LaunchedEffect(id) {
        if (detail != null) return@LaunchedEffect
        loading = true
        runCatching { client.artistDetail(id) }
            .onSuccess {
                detail = it
                followed = it.followed
            }
            .onFailure { error = it.message ?: "歌手加载失败" }
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        BackHandler(enabled = selectedAlbumId != null || expandedSection != null) {
            scope.launch {
                if (selectedAlbumId != null) {
                    if (selectedAlbumBackProgress.value < 1f) {
                        selectedAlbumBackProgress.animateTo(1f, tween(160))
                    }
                    selectedAlbumId = null
                    selectedAlbumBackProgress.snapTo(0f)
                } else if (expandedSection != null) {
                    if (expandedSectionBackProgress.value < 1f) {
                        expandedSectionBackProgress.animateTo(1f, tween(160))
                    }
                    expandedSection = null
                    expandedSectionBackProgress.snapTo(0f)
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = MeloXBottomContentClearance + 80.dp), // Extra padding for mini player
        ) {
            detail?.let { artist ->
                item(key = "artist-hero") {
                    ArtistHero(artist = artist)
                }

                if (artist.hotSongs.isNotEmpty()) {
                    item(key = "songs-title") {
                        ArtistSectionTitle("歌曲排行", onClick = null) // 热门歌曲一般横滑就够了，这里先不加全列表跳转
                    }
                    item(key = "hot-songs-matrix") {
                        val chunks = artist.hotSongs.take(20).chunked(5)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(chunks.size, key = { it }) { columnIndex ->
                                val chunk = chunks[columnIndex]
                                Column(Modifier.fillParentMaxWidth(0.9f)) {
                                    for (song in chunk) {
                                        ArtistTopSongRow(
                                            song = song,
                                            onPlay = { PlaybackCommands.playQueue(context, artist.hotSongs, song.id) },
                                            onMore = { selectedSong = song }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (artist.albums.isNotEmpty()) {
                    val isLive: (MeloXAlbumSummary) -> Boolean = { it.type == "Live" || (it.type == "专辑" && (it.name.contains("Live", ignoreCase = true) || it.name.contains("演唱会") || it.name.contains("巡回"))) }
                    val isCollab: (MeloXAlbumSummary) -> Boolean = { it.artistText.contains(" / ") }

                    val studioAlbums = artist.albums.filter { it.type == "专辑" && !isLive(it) && !isCollab(it) }
                    val epsAndSingles = artist.albums.filter { (it.type == "EP" || it.type == "Single") && !isLive(it) && !isCollab(it) }
                    val collabs = artist.albums.filter { it.type == "专辑" && isCollab(it) && !isLive(it) }
                    val compilations = artist.albums.filter { isLive(it) || (it.type != "专辑" && it.type != "EP" && it.type != "Single" && !isCollab(it)) }

                    if (studioAlbums.isNotEmpty()) {
                        item(key = "studio-albums-title") {
                            ArtistSectionTitle("专辑", onClick = { expandedSection = "专辑" })
                        }
                        item(key = "studio-albums") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(studioAlbums.take(7), key = { "artist-album-${it.id}" }) { album ->
                                    ArtistAlbumCard(album) { selectedAlbumId = album.id }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    if (epsAndSingles.isNotEmpty()) {
                        item(key = "eps-albums-title") {
                            ArtistSectionTitle("单曲与 EP", onClick = { expandedSection = "单曲与 EP" })
                        }
                        item(key = "eps-albums") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(epsAndSingles.take(7), key = { "artist-album-${it.id}" }) { album ->
                                    ArtistAlbumCard(album) { selectedAlbumId = album.id }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    if (collabs.isNotEmpty()) {
                        item(key = "collab-albums-title") {
                            ArtistSectionTitle("多人合作专辑", onClick = { expandedSection = "多人合作专辑" })
                        }
                        item(key = "collab-albums") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(collabs.take(7), key = { "artist-album-${it.id}" }) { album ->
                                    ArtistAlbumCard(album) { selectedAlbumId = album.id }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    if (compilations.isNotEmpty()) {
                        item(key = "compilations-title") {
                            ArtistSectionTitle("现场与精选集", onClick = { expandedSection = "现场与精选集" })
                        }
                        item(key = "compilations") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(compilations.take(7), key = { "artist-album-${it.id}" }) { album ->
                                    ArtistAlbumCard(album) { selectedAlbumId = album.id }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }

            if (loading) item(key = "loading") {
                Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            error?.let { message ->
                item(key = "error") {
                    Text(message, modifier = Modifier.fillMaxWidth().padding(20.dp), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.size(44.dp).meloXLiquidButton(shape = CircleShape).clickable(onClick = onBack, interactionSource = remember { MutableInteractionSource() }, indication = null), contentAlignment = Alignment.Center) {
                MeloXActionIcon("‹", Modifier.size(20.dp), MaterialTheme.colorScheme.onSurface)
            }
            Box(Modifier.size(44.dp).meloXLiquidButton(shape = CircleShape).clickable(onClick = { showInfoSheet = true }, interactionSource = remember { MutableInteractionSource() }, indication = null), contentAlignment = Alignment.Center) {
                MeloXActionIcon("•••", Modifier.size(20.dp), MaterialTheme.colorScheme.onSurface)
            }
        }

        // Full screen overlay for expanded albums section
        if (expandedSection != null && detail != null) {
            val albumsToShow = when (expandedSection) {
                "专辑" -> detail!!.albums.filter { it.type == "专辑" && !(it.name.contains("Live", ignoreCase = true) || it.name.contains("演唱会") || it.name.contains("巡回")) && !it.artistText.contains(" / ") }
                "单曲与 EP" -> detail!!.albums.filter { (it.type == "EP" || it.type == "Single") && !it.artistText.contains(" / ") }
                "多人合作专辑" -> detail!!.albums.filter { it.type == "专辑" && it.artistText.contains(" / ") && !(it.name.contains("Live", ignoreCase = true) || it.name.contains("演唱会") || it.name.contains("巡回")) }
                "现场与精选集" -> detail!!.albums.filter { it.type == "Live" || (it.type == "专辑" && (it.name.contains("Live", ignoreCase = true) || it.name.contains("演唱会") || it.name.contains("巡回"))) || (it.type != "专辑" && it.type != "EP" && it.type != "Single" && !it.artistText.contains(" / ")) }
                else -> emptyList()
            }

            // BackHandler is handled at root box

            val displayExpandedSection = remember { mutableStateOf(expandedSection) }
            if (expandedSection != null) displayExpandedSection.value = expandedSection

            if (expandedSection != null || expandedSectionBackProgress.value > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(10f)
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .graphicsLayer {
                        translationX = size.width * expandedSectionBackProgress.value
                        val scale = 1f - 0.08f * expandedSectionBackProgress.value
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
            ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MeloXGlassIconButton(MeloXSymbol.ChevronLeft, onClick = {
                        scope.launch {
                            if (expandedSectionBackProgress.value < 1f) {
                                expandedSectionBackProgress.animateTo(1f, tween(160))
                            }
                            expandedSection = null
                            expandedSectionBackProgress.snapTo(0f)
                        }
                    })
                    Spacer(Modifier.width(12.dp))
                    Text(displayExpandedSection.value.orEmpty(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, MeloXBottomContentClearance + 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(albumsToShow, key = { "expanded-album-${it.id}" }) { album ->
                        ArtistAlbumCard(album, fillWidth = true) { selectedAlbumId = album.id }
                    }
                }
            }
        }
        }
    }

        val displaySelectedAlbumId = remember { mutableStateOf(selectedAlbumId) }
        if (selectedAlbumId != null) displaySelectedAlbumId.value = selectedAlbumId

        if (selectedAlbumId != null || selectedAlbumBackProgress.value > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(20f)
                    .background(MaterialTheme.colorScheme.background)
                    .graphicsLayer {
                        translationX = size.width * selectedAlbumBackProgress.value
                        val scale = 1f - 0.08f * selectedAlbumBackProgress.value
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
            ) {
                displaySelectedAlbumId.value?.let { albumId ->
                    MeloXUnifiedAlbumDetailScreen(
                        albumId = albumId,
                        onBack = {
                            scope.launch {
                                if (selectedAlbumBackProgress.value < 1f) {
                                    selectedAlbumBackProgress.animateTo(1f, tween(160))
                                }
                                selectedAlbumId = null
                                selectedAlbumBackProgress.snapTo(0f)
                            }
                        }
                    )
                }
            }
        }

        selectedSong?.let { song ->
            MeloXSongActionsOverlay(
                song = song,
                queue = allSongs.ifEmpty { detail?.hotSongs.orEmpty() },
                visible = true,
                onDismiss = { selectedSong = null },
            )
        }
    } // End of root Box

    if (showInfoSheet && detail != null) {
        ArtistInfoSheet(
            artist = detail!!,
            followed = followed,
            followBusy = followBusy,
            onFollow = {
                val target = followed != true
                followBusy = true
                scope.launch {
                    runCatching { client.setArtistFollowed(id, target) }
                        .onSuccess { followed = target }
                        .onFailure { error = it.message ?: "关注操作失败" }
                    followBusy = false
                }
            },
            onDismiss = { showInfoSheet = false }
        )
    }
}

@Composable
private fun ArtistHero(artist: MeloXArtistDetail) {
    val background = MaterialTheme.colorScheme.background
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            AsyncImage(
                model = artist.coverUrl ?: artist.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = .16f),
                        .42f to Color.Transparent,
                        .78f to background.copy(alpha = .86f),
                        1f to background,
                    ),
                ),
            )
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    AsyncImage(
                        model = optimized500Artwork(artist.artworkUrl),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(artist.name, fontSize = 27.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (artist.aliases.isNotEmpty()) {
                            Text(artist.aliases.joinToString(" · "), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .54f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistInfoSheet(
    artist: MeloXArtistDetail,
    followed: Boolean?,
    followBusy: Boolean,
    onFollow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 48.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(artist.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)

            Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                ArtistMetric(artist.musicSize, "单曲")
                ArtistMetric(artist.albumSize, "专辑")
                ArtistMetric(artist.mvSize, "MV")
            }

            Spacer(Modifier.height(24.dp))

            followed?.let {
                MeloXGlassButton(
                    onClick = onFollow,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !followBusy,
                    style = if (it) MeloXGlassButtonStyle.BorderedProminent else MeloXGlassButtonStyle.Bordered,
                    shape = MeloXShapes.capsule,
                ) { Text(if (it) "已关注" else "关注", fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(24.dp))
            }

            artist.description?.takeIf(String::isNotBlank)?.let { description ->
                Text(
                    description,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun ArtistMetric(value: Int, label: String) {
    Column {
        Text(value.toString(), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f))
    }
}

@Composable
private fun ArtistSectionTitle(title: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = onClick != null,
                onClick = { onClick?.invoke() },
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (onClick != null) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.ChevronRight,
                modifier = Modifier.padding(start = 6.dp).size(20.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ArtistTopSongRow(song: SearchSong, onPlay: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(song.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(9.dp)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(song.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artists, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MeloXSymbolIcon(
            symbol = MeloXSymbol.MoreVertical,
            modifier = Modifier
                .size(32.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onMore
                )
                .padding(4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun optimized500Artwork(url: String?): String? {
    val source = url?.takeIf(String::isNotBlank) ?: return null
    if (!source.contains(".music.126.net")) return source
    val separator = if (source.contains('?')) '&' else '?'
    return if (source.contains("param=")) source else "$source${separator}param=500y500"
}

@Composable
private fun ArtistAlbumCard(album: MeloXAlbumSummary, fillWidth: Boolean = false, onClick: () -> Unit) {
    val modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(116.dp)
    val imageModifier = if (fillWidth) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.size(116.dp)

    Column(modifier.clickable(onClick = onClick)) {
        AsyncImage(
            model = optimized500Artwork(album.artworkUrl),
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = imageModifier.clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.height(7.dp))
        Text(album.name, fontWeight = FontWeight.Medium, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
        Text(album.artistText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
