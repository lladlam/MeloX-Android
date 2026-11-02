package com.lladlam.melox.ui.song

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.R
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.provider.MeloXUnifiedFavorites
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.MeloXPageHost
import com.lladlam.melox.ui.finishMeloXPage
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassCard
import com.lladlam.melox.ui.glass.MeloXPinnedListPage
import com.lladlam.melox.ui.prepareMeloXPageWindow
import com.lladlam.melox.ui.startMeloXPage
import com.lladlam.melox.ui.theme.MeloXTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Standalone song information page, matching iOS `SongDetailView`. */
class MeloXSongDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val song = intent.toSong() ?: run {
            finish()
            return
        }
        enableEdgeToEdge()
        prepareMeloXPageWindow()
        setContent {
            MeloXPageHost {
                MeloXTheme {
                    MeloXSongDetailScreen(song, ::finishMeloXPage)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTISTS = "artists"
        private const val EXTRA_ALBUM = "album"
        private const val EXTRA_ALBUM_ID = "album_id"
        private const val EXTRA_ARTIST_ID = "artist_id"
        private const val EXTRA_ARTWORK = "artwork"
        private const val EXTRA_DURATION = "duration"

        fun launch(context: Context, song: SearchSong) {
            if (song.id <= 0L) return
            context.startMeloXPage(
                Intent(context, MeloXSongDetailActivity::class.java)
                    .putExtra(EXTRA_ID, song.id)
                    .putExtra(EXTRA_TITLE, song.name)
                    .putExtra(EXTRA_ARTISTS, song.artists)
                    .putExtra(EXTRA_ALBUM, song.album)
                    .putExtra(EXTRA_ALBUM_ID, song.albumId ?: -1L)
                    .putExtra(EXTRA_ARTIST_ID, song.artistId ?: -1L)
                    .putExtra(EXTRA_ARTWORK, song.artworkUrl)
                    .putExtra(EXTRA_DURATION, song.durationMs)
                    .apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            )
        }

        internal fun Intent.toSong(): SearchSong? {
            val id = getLongExtra(EXTRA_ID, -1L)
            if (id <= 0L) return null
            return SearchSong(
                id = id,
                name = getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "未知歌曲" },
                artists = getStringExtra(EXTRA_ARTISTS).orEmpty().ifBlank { "未知歌手" },
                album = getStringExtra(EXTRA_ALBUM).orEmpty(),
                albumId = getLongExtra(EXTRA_ALBUM_ID, -1L).takeIf { it > 0L },
                artistId = getLongExtra(EXTRA_ARTIST_ID, -1L).takeIf { it > 0L },
                artworkUrl = getStringExtra(EXTRA_ARTWORK),
                durationMs = getLongExtra(EXTRA_DURATION, 0L),
            )
        }
    }
}

@Composable
internal fun MeloXSongDetailScreen(song: SearchSong, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val operations = remember(app) {
        NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(app) })
    }
    var liked by remember(song.id) { mutableStateOf<Boolean?>(null) }
    var busy by remember(song.id) { mutableStateOf(false) }
    var error by remember(song.id) { mutableStateOf<String?>(null) }
    val unifiedFavoritesEnabled = MeloXUnifiedFavorites.enabled(app)
    val unifiedFavoritesRevision = MeloXUnifiedFavorites.revision

    LaunchedEffect(song.id, unifiedFavoritesEnabled, unifiedFavoritesRevision) {
        if (unifiedFavoritesEnabled) {
            liked = MeloXUnifiedFavorites.contains(app, MeloXUnifiedFavorites.fromNetease(song))
            return@LaunchedEffect
        }
        runCatching {
            withContext(Dispatchers.IO) {
                val session = NeteaseSessionStore(app)
                if (session.profile == null) session.refreshProfile()
                val userId = session.profile?.userId ?: return@withContext null
                val client = NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie(app) })
                client.likedSongIdsBlocking(userId).contains(song.id)
            }
        }.onSuccess { liked = it }
    }

    MeloXPinnedListPage(
        title = stringResource(R.string.song_detail_title),
        onNavigateBack = onBack,
        bottomPadding = MeloXBottomContentClearance,
    ) {
        item(key = "header") {
            MeloXGlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = song.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(112.dp).clip(RoundedCornerShape(14.dp)),
                    )
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(
                            song.name,
                            fontSize = 22.sp,
                            lineHeight = 27.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (song.artists.isNotBlank()) {
                            Text(
                                song.artists,
                                modifier = Modifier.padding(top = 6.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (song.album.isNotBlank()) {
                            Text(
                                song.album,
                                modifier = Modifier.padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        item(key = "actions") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MeloXGlassButton(
                    onClick = {
                        PlaybackCommands.playAll(context, listOf(song))
                    },
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.BorderedProminent,
                ) { Text(stringResource(R.string.song_detail_play)) }
                MeloXGlassButton(
                    onClick = {
                        if (busy) return@MeloXGlassButton
                        busy = true
                        if (unifiedFavoritesEnabled) {
                            liked = MeloXUnifiedFavorites.toggle(app, MeloXUnifiedFavorites.fromNetease(song))
                            busy = false
                            return@MeloXGlassButton
                        }
                        scope.launch {
                            val target = liked != true
                            runCatching { operations.setSongLiked(song.id, target) }
                                .onSuccess { liked = target }
                                .onFailure { error = it.message ?: "收藏失败" }
                            busy = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.Plain,
                ) {
                    Text(
                        if (liked == true) stringResource(R.string.song_detail_liked) else stringResource(R.string.song_detail_like),
                    )
                }
            }
        }
        item(key = "information") {
            MeloXGlassCard(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.song_detail_information),
                    fontWeight = FontWeight.SemiBold,
                )
                if (song.artists.isNotBlank()) {
                    InformationRow(stringResource(R.string.song_detail_artist), song.artists)
                }
                if (song.album.isNotBlank()) {
                    InformationRow(stringResource(R.string.song_detail_album), song.album)
                }
                if (song.durationMs > 0L) {
                    InformationRow(
                        stringResource(R.string.song_detail_duration),
                        formatDuration(song.durationMs),
                    )
                }
            }
        }
        if (neteaseWikiSongId(song) != null) {
            item(key = "wiki") {
                MeloXGlassButton(
                    onClick = { MeloXSongWikiActivity.launch(context, song) },
                    modifier = Modifier.fillMaxWidth(),
                    style = MeloXGlassButtonStyle.Plain,
                ) { Text(stringResource(R.string.song_detail_wiki)) }
            }
        }
        error?.let { message ->
            item(key = "error") {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun InformationRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), fontSize = 13.sp)
        Text(
            value,
            modifier = Modifier.weight(1f).padding(start = 14.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .82f),
            fontSize = 13.sp,
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1_000L
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
