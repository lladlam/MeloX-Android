package com.lladlam.melox.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.library.NeteaseLibrarySnapshot
import com.lladlam.melox.core.library.NeteasePlaylistDetail
import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.playback.PlaybackCommands
import kotlinx.coroutines.launch

private enum class MeloXLibraryPage(val title: String) {
    Songs("歌曲"),
    Playlists("歌单"),
    History("最近播放"),
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    session: NeteaseSessionStore,
    onLogin: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val client = remember(appContext) {
        NeteaseLibraryClient(
            cookieProvider = { NeteaseSessionStore.readCookie(appContext) },
        )
    }

    var selectedPage by remember { mutableStateOf(MeloXLibraryPage.Songs) }
    var selectedPlaylist by remember(session.cookie) { mutableStateOf<NeteasePlaylistSummary?>(null) }
    var snapshot by remember(session.cookie) { mutableStateOf<NeteaseLibrarySnapshot?>(null) }
    var loading by remember(session.cookie) { mutableStateOf(false) }
    var errorMessage by remember(session.cookie) { mutableStateOf<String?>(null) }
    val playlistListState = rememberLazyListState()

    suspend fun refreshLibrary() {
        if (!session.isLoggedIn) return
        if (session.profile == null) session.refreshProfile(force = true)
        val userId = session.profile?.userId ?: return
        loading = true
        errorMessage = null
        runCatching { client.snapshot(userId) }
            .onSuccess { snapshot = it }
            .onFailure { errorMessage = it.message ?: "音乐库加载失败" }
        loading = false
    }

    LaunchedEffect(session.cookie, session.profile?.userId) {
        if (session.isLoggedIn && snapshot == null && !loading) {
            refreshLibrary()
        }
    }

    BackHandler(enabled = selectedPlaylist != null) {
        selectedPlaylist = null
    }

    if (!session.isLoggedIn) {
        MeloXLibraryLoginUnavailable(onLogin)
        return
    }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        val sharedScope = this

        AnimatedContent(
            targetState = selectedPlaylist,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(
                    animationSpec = tween(
                        durationMillis = 320,
                        delayMillis = 55,
                        easing = FastOutSlowInEasing,
                    ),
                ) togetherWith fadeOut(
                    animationSpec = tween(
                        durationMillis = 240,
                        easing = FastOutSlowInEasing,
                    ),
                )
            },
            label = "library-playlist-detail-transition",
        ) { targetPlaylist ->
            val playlistTransitionVisibilityScope = this
            if (targetPlaylist != null) {
                MeloXPlaylistDetailScreen(
                    initialPlaylist = targetPlaylist,
                    client = client,
                    onBack = { selectedPlaylist = null },
                    sharedTransitionScope = sharedScope,
                    animatedVisibilityScope = this,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding(),
                ) {
                    Text(
                        text = "音乐库",
                        modifier = Modifier.padding(start = 20.dp, top = 46.dp),
                        fontSize = 36.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )

                    MeloXLibrarySegmentedPicker(
                        selected = selectedPage,
                        onSelected = { selectedPage = it },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 24.dp),
                    )

                    if (errorMessage != null && snapshot == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = errorMessage.orEmpty(),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = "重新载入",
                                    modifier = Modifier
                                        .padding(top = 12.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable { scope.launch { refreshLibrary() } }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    } else if (loading && snapshot == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val data = snapshot ?: NeteaseLibrarySnapshot(emptyList(), emptyList(), emptyList())
                        when (selectedPage) {
                            MeloXLibraryPage.Songs -> MeloXLibrarySongsPage(
                                songs = data.likedSongs,
                                onPlay = { song ->
                                    PlaybackCommands.playQueue(
                                        context = context,
                                        songs = data.likedSongs,
                                        selectedSongId = song.id,
                                        onFailure = { errorMessage = it.message ?: "播放失败" },
                                    )
                                },
                                onPlayAll = {
                                    data.likedSongs.firstOrNull()?.let { first ->
                                        PlaybackCommands.playQueue(
                                            context = context,
                                            songs = data.likedSongs,
                                            selectedSongId = first.id,
                                            onFailure = { errorMessage = it.message ?: "播放失败" },
                                        )
                                    }
                                },
                            )

                            MeloXLibraryPage.Playlists -> MeloXLibraryPlaylistsPage(
                                playlists = data.playlists,
                                onPlaylistClick = { selectedPlaylist = it },
                                listState = playlistListState,
                                sharedTransitionScope = sharedScope,
                                animatedVisibilityScope = playlistTransitionVisibilityScope,
                            )

                            MeloXLibraryPage.History -> MeloXLibrarySongsPage(
                                songs = data.recentSongs,
                                onPlay = { song ->
                                    PlaybackCommands.playQueue(
                                        context = context,
                                        songs = data.recentSongs,
                                        selectedSongId = song.id,
                                        onFailure = { errorMessage = it.message ?: "播放失败" },
                                    )
                                },
                                onPlayAll = {
                                    data.recentSongs.firstOrNull()?.let { first ->
                                        PlaybackCommands.playQueue(
                                            context = context,
                                            songs = data.recentSongs,
                                            selectedSongId = first.id,
                                            onFailure = { errorMessage = it.message ?: "播放失败" },
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeloXLibraryLoginUnavailable(onLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Text(
            "音乐库",
            modifier = Modifier.padding(top = 46.dp),
            fontSize = 36.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("需要登录", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "登录后可读取收藏歌曲、歌单和播放记录。",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.50f),
                    textAlign = TextAlign.Center,
                )
                Surface(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .clickable(onClick = onLogin),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        "登录网易云音乐",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeloXLibrarySegmentedPicker(
    selected: MeloXLibraryPage,
    onSelected: (MeloXLibraryPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.075f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeloXLibraryPage.entries.forEach { page ->
            val isSelected = page == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .padding(horizontal = 1.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surface
                        else Color.Transparent,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelected(page) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = page.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun MeloXLibrarySongsPage(
    songs: List<SearchSong>,
    onPlay: (SearchSong) -> Unit,
    onPlayAll: () -> Unit,
) {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "暂无歌曲",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                fontSize = 17.sp,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        item {
            MeloXPlayAllRow(onPlayAll)
            MeloXInsetDivider(leading = 68.dp)
        }
        items(songs, key = { it.id }) { song ->
            MeloXLibraryTrackRow(song = song, onClick = { onPlay(song) })
            MeloXInsetDivider(leading = 68.dp)
        }
    }
}

@Composable
private fun MeloXPlayAllRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MeloXPlayGlyph(
            modifier = Modifier.size(28.dp),
            color = Color(0xFFFF3147),
        )
        Text(
            text = "播放全部",
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun MeloXLibraryTrackRow(
    song: SearchSong,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = song.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = song.name,
                fontSize = 17.sp,
                lineHeight = 21.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = song.artists,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.46f),
            )
        }
        if (song.durationMs > 0L) {
            Text(
                text = formatDuration(song.durationMs),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.46f),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MeloXLibraryPlaylistsPage(
    playlists: List<NeteasePlaylistSummary>,
    onPlaylistClick: (NeteasePlaylistSummary) -> Unit,
    listState: LazyListState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    if (playlists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "还没有收藏歌单",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                fontSize = 17.sp,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        item {
            Text(
                text = "歌单",
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.50f),
            )
        }
        items(playlists, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlaylistClick(playlist) }
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val sharedArtworkModifier = with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState(
                            key = playlistArtworkSharedKey(playlist.id),
                        ),
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }

                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = sharedArtworkModifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(7.dp)),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        playlist.name,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${playlist.trackCount} 首歌曲",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                    )
                }
                Text(
                    "›",
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.24f),
                )
            }
            MeloXInsetDivider(leading = 84.dp)
        }
    }
}

@Composable
private fun MeloXInsetDivider(leading: androidx.compose.ui.unit.Dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = leading, end = 18.dp),
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MeloXPlaylistDetailScreen(
    initialPlaylist: NeteasePlaylistSummary,
    client: NeteaseLibraryClient,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember(initialPlaylist.id) { mutableStateOf<NeteasePlaylistDetail?>(null) }
    var loading by remember(initialPlaylist.id) { mutableStateOf(true) }
    var errorMessage by remember(initialPlaylist.id) { mutableStateOf<String?>(null) }
    var searchQuery by remember(initialPlaylist.id) { mutableStateOf("") }
    var palette by remember(initialPlaylist.coverUrl) { mutableStateOf(MeloXDetailPalette.LightFallback) }

    LaunchedEffect(initialPlaylist.id) {
        loading = true
        errorMessage = null
        runCatching { client.playlistDetail(initialPlaylist.id) }
            .onSuccess { detail = it }
            .onFailure { errorMessage = it.message ?: "歌单加载失败" }
        loading = false
    }

    val displayed = detail?.summary ?: initialPlaylist
    val songs = detail?.songs.orEmpty()

    LaunchedEffect(displayed.coverUrl) {
        palette = MeloXDetailPaletteProvider.paletteFor(displayed.coverUrl)
    }

    val foreground = if (palette.prefersDarkAppearance) Color.White else Color.Black
    val secondary = foreground.copy(alpha = 0.48f)
    val filteredSongs = remember(songs, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) songs else songs.filter { song ->
            song.name.lowercase().contains(query) ||
                song.artists.lowercase().contains(query) ||
                song.album.lowercase().contains(query)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        if (!displayed.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = optimized160Artwork(displayed.coverUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(
                        radius = 18.dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded,
                    )
                    .background(Color.Transparent),
                alpha = 0.22f,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        if (palette.prefersDarkAppearance) {
                            listOf(
                                Color.Black.copy(alpha = 0.08f),
                                Color.Black.copy(alpha = 0.24f),
                                Color.Black.copy(alpha = 0.40f),
                            )
                        } else {
                            listOf(
                                Color.White.copy(alpha = 0.06f),
                                Color.White.copy(alpha = 0.16f),
                                Color.White.copy(alpha = 0.30f),
                            )
                        },
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            MeloXPlaylistToolbar(
                foreground = foreground,
                onBack = onBack,
            )
            MeloXPlaylistSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                foreground = foreground,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item {
                    MeloXStandardPlaylistHero(
                        playlist = displayed,
                        tracks = songs,
                        foreground = foreground,
                        secondary = secondary,
                        onPlay = {
                            songs.firstOrNull()?.let { first ->
                                PlaybackCommands.playQueue(
                                    context = context,
                                    songs = songs,
                                    selectedSongId = first.id,
                                    onFailure = { errorMessage = it.message ?: "播放失败" },
                                )
                            }
                        },
                        onShuffle = {
                            val shuffled = songs.shuffled()
                            shuffled.firstOrNull()?.let { first ->
                                PlaybackCommands.playQueue(
                                    context = context,
                                    songs = shuffled,
                                    selectedSongId = first.id,
                                    onFailure = { errorMessage = it.message ?: "播放失败" },
                                )
                            }
                        },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }

                when {
                    loading && songs.isEmpty() -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = foreground)
                        }
                    }
                    errorMessage != null && songs.isEmpty() -> item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                errorMessage.orEmpty(),
                                color = secondary,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "重试",
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .clickable {
                                        scope.launch {
                                            loading = true
                                            errorMessage = null
                                            runCatching { client.playlistDetail(initialPlaylist.id) }
                                                .onSuccess { detail = it }
                                                .onFailure { errorMessage = it.message ?: "歌单加载失败" }
                                            loading = false
                                        }
                                    }
                                    .padding(8.dp),
                                color = foreground,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    filteredSongs.isEmpty() -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("暂无歌曲", color = secondary)
                        }
                    }
                    else -> itemsIndexed(
                        items = filteredSongs,
                        key = { _, song -> song.id },
                    ) { index, song ->
                        MeloXPlaylistTrackRow(
                            song = song,
                            index = index,
                            foreground = foreground,
                            onClick = {
                                PlaybackCommands.playQueue(
                                    context = context,
                                    songs = filteredSongs,
                                    selectedSongId = song.id,
                                    onFailure = { errorMessage = it.message ?: "播放失败" },
                                )
                            },
                        )
                        if (song.id != filteredSongs.lastOrNull()?.id) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 66.dp, end = 20.dp),
                                thickness = 0.6.dp,
                                color = foreground.copy(alpha = 0.12f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeloXPlaylistToolbar(
    foreground: Color,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MeloXGlassCircleButton(
            foreground = foreground,
            size = 44.dp,
            onClick = onBack,
        ) {
            MeloXBackGlyph(Modifier.size(22.dp), foreground)
        }

        Row(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(glassColor(foreground))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
                contentAlignment = Alignment.Center,
            ) {
                MeloXShareGlyph(Modifier.size(22.dp), Color(0xFFFF3147))
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "•••",
                    color = Color(0xFFFF3147),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

@Composable
private fun MeloXPlaylistSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(glassColor(foreground)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeloXSearchGlyph(
            modifier = Modifier
                .padding(start = 14.dp)
                .size(20.dp),
            color = foreground,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    "在歌单中搜索",
                    color = foreground.copy(alpha = 0.46f),
                    fontSize = 17.sp,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = foreground,
                    fontSize = 17.sp,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MeloXStandardPlaylistHero(
    playlist: NeteasePlaylistSummary,
    tracks: List<SearchSong>,
    foreground: Color,
    secondary: Color,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val artworkSize = minOf(maxWidth * 0.68f, 300.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 26.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val sharedArtworkModifier = with(sharedTransitionScope) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(
                        key = playlistArtworkSharedKey(playlist.id),
                    ),
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }

            AsyncImage(
                model = playlist.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = sharedArtworkModifier
                    .size(artworkSize)
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(12.dp),
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.18f),
                        spotColor = Color.Black.copy(alpha = 0.18f),
                    )
                    .clip(RoundedCornerShape(12.dp)),
            )

            Text(
                text = playlist.name,
                modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp),
                color = foreground,
                fontSize = 22.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = playlist.creatorName.ifBlank { "网易云音乐" },
                modifier = Modifier.padding(top = 8.dp),
                color = foreground,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "${if (playlist.trackCount > 0) playlist.trackCount else tracks.size} 首歌曲 · ${compactPlayCount(playlist.playCount)} 次播放",
                modifier = Modifier.padding(top = 7.dp),
                color = secondary,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )

            Row(
                modifier = Modifier.padding(top = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MeloXGlassCircleButton(
                    foreground = foreground,
                    size = 54.dp,
                    enabled = tracks.isNotEmpty(),
                    onClick = onShuffle,
                ) {
                    MeloXShuffleGlyph(Modifier.size(26.dp), foreground)
                }

                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(if (foreground == Color.White) Color.White else Color.Black)
                        .clickable(enabled = tracks.isNotEmpty(), onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        MeloXPlayGlyph(
                            Modifier.size(19.dp),
                            if (foreground == Color.White) Color.Black else Color.White,
                        )
                        Text(
                            "播放",
                            color = if (foreground == Color.White) Color.Black else Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                MeloXGlassCircleButton(
                    foreground = foreground,
                    size = 54.dp,
                    onClick = {},
                ) {
                    Text(
                        "+",
                        color = foreground,
                        fontSize = 34.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeloXPlaylistTrackRow(
    song: SearchSong,
    index: Int,
    foreground: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "${index + 1}",
                modifier = Modifier.width(40.dp),
                color = foreground.copy(alpha = 0.48f),
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = song.name,
                modifier = Modifier.weight(1f),
                color = foreground,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "•••",
                color = foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

@Composable
private fun MeloXGlassCircleButton(
    foreground: Color,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(glassColor(foreground))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun glassColor(foreground: Color): Color =
    if (foreground == Color.White) Color.Black.copy(alpha = 0.22f)
    else Color.White.copy(alpha = 0.64f)

@Composable
private fun MeloXPlayGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.24f, size.height * 0.12f)
            lineTo(size.width * 0.86f, size.height * 0.50f)
            lineTo(size.width * 0.24f, size.height * 0.88f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun MeloXBackGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.14f
        val p = Path().apply {
            moveTo(size.width * 0.67f, size.height * 0.14f)
            lineTo(size.width * 0.32f, size.height * 0.50f)
            lineTo(size.width * 0.67f, size.height * 0.86f)
        }
        drawPath(p, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun MeloXSearchGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.11f
        drawCircle(
            color = color,
            radius = size.minDimension * 0.30f,
            center = Offset(size.width * 0.42f, size.height * 0.40f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.62f, size.height * 0.61f),
            end = Offset(size.width * 0.86f, size.height * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun MeloXShareGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.09f
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.20f, size.height * 0.40f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.60f, size.height * 0.50f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.08f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.50f, size.height * 0.63f),
            end = Offset(size.width * 0.50f, size.height * 0.12f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        val arrow = Path().apply {
            moveTo(size.width * 0.34f, size.height * 0.28f)
            lineTo(size.width * 0.50f, size.height * 0.11f)
            lineTo(size.width * 0.66f, size.height * 0.28f)
        }
        drawPath(arrow, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun MeloXShuffleGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.095f
        val top = Path().apply {
            moveTo(size.width * 0.10f, size.height * 0.28f)
            cubicTo(
                size.width * 0.34f, size.height * 0.28f,
                size.width * 0.54f, size.height * 0.72f,
                size.width * 0.78f, size.height * 0.72f,
            )
        }
        val bottom = Path().apply {
            moveTo(size.width * 0.10f, size.height * 0.72f)
            cubicTo(
                size.width * 0.34f, size.height * 0.72f,
                size.width * 0.54f, size.height * 0.28f,
                size.width * 0.78f, size.height * 0.28f,
            )
        }
        drawPath(top, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawPath(bottom, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        val a1 = Path().apply {
            moveTo(size.width * 0.70f, size.height * 0.17f)
            lineTo(size.width * 0.89f, size.height * 0.28f)
            lineTo(size.width * 0.70f, size.height * 0.39f)
        }
        val a2 = Path().apply {
            moveTo(size.width * 0.70f, size.height * 0.61f)
            lineTo(size.width * 0.89f, size.height * 0.72f)
            lineTo(size.width * 0.70f, size.height * 0.83f)
        }
        drawPath(a1, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawPath(a2, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

private fun playlistArtworkSharedKey(playlistId: Long): String =
    "library-playlist-artwork-$playlistId"

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private fun compactPlayCount(value: Long): String = when {
    value >= 100_000_000L -> "%.1f 亿".format(value / 100_000_000.0)
    value >= 10_000L -> "%.1f 万".format(value / 10_000.0)
    else -> value.toString()
}

private fun optimized160Artwork(url: String?): String? {
    val source = url?.takeIf(String::isNotBlank) ?: return null
    if (!source.contains(".music.126.net")) return source
    val separator = if (source.contains('?')) '&' else '?'
    return if (source.contains("param=")) source else "$source${separator}param=160y160"
}
