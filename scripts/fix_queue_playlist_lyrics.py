from pathlib import Path
import re

ROOT = Path('android/app/src/main/kotlin/com/lladlam/melox')


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing pattern: {label}')
    return text.replace(old, new, 1)

# ---------------------------------------------------------------------------
# Queue: fixed song header + fixed mode controls, scroll content only below it.
# History remains physically above the normal queue anchor so swiping upward
# reveals it, but it never pushes the four mode buttons away.
# ---------------------------------------------------------------------------
queue_path = ROOT / 'ui/player/MeloXQueuePanel.kt'
queue_path.write_text(r'''package com.lladlam.melox.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.lladlam.melox.ui.glass.meloXLiquidButton

@Composable
fun MeloXQueuePanel(
    state: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
    showSongHeader: Boolean = true,
) {
    val currentEntry = state.queue.getOrNull(state.currentIndex)
    val history = if (state.currentIndex > 0) state.queue.take(state.currentIndex) else emptyList()
    val upcoming = if (state.currentIndex >= 0 && state.queue.isNotEmpty()) {
        state.queue.drop(state.currentIndex + 1)
    } else emptyList()
    val manualQueue = upcoming.filter { it.origin == MeloXQueueOrigin.Manual }
    val continuing = upcoming.filter { it.origin != MeloXQueueOrigin.Manual }
    val listState = rememberLazyListState()
    val historyItemCount = if (history.isEmpty()) 0 else history.size + 1

    // The normal resting point is immediately after history. History therefore
    // lives above the viewport and appears only when the user deliberately
    // scrolls upward, matching the intended queue interaction.
    LaunchedEffect(state.mediaId, historyItemCount) {
        runCatching { listState.scrollToItem(historyItemCount) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (showSongHeader && currentEntry != null) {
            QueueSongHeaderSurface(currentEntry)
        }

        QueueModeControlsSurface(state)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (history.isNotEmpty()) {
                item(key = "history-title") { QueueSectionTitle("历史记录", subdued = true) }
                items(history, key = { "history-${it.index}-${it.mediaId}" }) { entry ->
                    QueueRow(entry, state)
                }
            }

            if (manualQueue.isNotEmpty()) {
                item(key = "manual-title") { QueueSectionTitle("队列") }
                items(manualQueue, key = { "manual-${it.index}-${it.mediaId}" }) { entry ->
                    QueueRow(entry, state)
                }
            }

            item(key = "continue-title") { QueueSectionTitle("继续播放") }
            if (continuing.isEmpty()) {
                item(key = "continue-empty") {
                    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                        Text("没有待播放歌曲", color = Color.White.copy(alpha = .55f), fontSize = 15.sp)
                    }
                }
            } else {
                items(continuing, key = { "continue-${it.index}-${it.mediaId}" }) { entry ->
                    QueueRow(entry, state)
                }
            }
        }
    }
}

@Composable
private fun QueueSongHeaderSurface(entry: MeloXQueueEntry) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .meloXLiquidButton(
                shape = shape,
                tint = Color.White.copy(alpha = .035f),
                surfaceColor = Color.Black.copy(alpha = .10f),
                blurRadius = 20.dp,
                lensRadius = 14.dp,
                refractionHeight = 18.dp,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(entry.artworkUrl, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.artist, color = Color.White.copy(alpha = .62f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun QueueModeControlsSurface(state: MeloXPlaybackUiState) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(shape)
            .meloXLiquidButton(
                shape = shape,
                tint = Color.White.copy(alpha = .035f),
                surfaceColor = Color.Black.copy(alpha = .095f),
                blurRadius = 20.dp,
                lensRadius = 14.dp,
                refractionHeight = 18.dp,
            )
            .padding(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QueueModeButton("↝", state.shuffleEnabled, onClick = state::toggleShuffle, modifier = Modifier.weight(1f))
            QueueModeButton(if (state.repeatMode == Player.REPEAT_MODE_ONE) "↻1" else "↻", state.repeatMode != Player.REPEAT_MODE_OFF, onClick = state::cycleRepeatMode, modifier = Modifier.weight(1f))
            QueueModeButton("∞", state.autoplayEnabled, onClick = state::toggleAutoplay, modifier = Modifier.weight(1f))
            QueueModeButton("◎", state.autoMixEnabled, onClick = state::toggleAutoMix, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun QueueSectionTitle(title: String, subdued: Boolean = false) {
    Text(
        title,
        color = Color.White.copy(alpha = if (subdued) .62f else 1f),
        fontSize = if (subdued) 18.sp else 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun QueueRow(entry: MeloXQueueEntry, state: MeloXPlaybackUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { state.playQueueIndex(entry.index) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(entry.artworkUrl, Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.artist, color = Color.White.copy(alpha = .58f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun QueueModeButton(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = if (selected) .70f else .12f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = when {
                !enabled -> Color.White.copy(alpha = .25f)
                selected -> Color.Black.copy(alpha = .62f)
                else -> Color.White.copy(alpha = .86f)
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
''')

# ---------------------------------------------------------------------------
# Now-playing scene: queue song information is a fixed glass header. Lyrics
# bottom controls already gained a glass shell in the previous patch.
# ---------------------------------------------------------------------------
scene_path = ROOT / 'ui/player/MeloXIOSNowPlayingScene.kt'
s = scene_path.read_text()
old = '''            Row(\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .height(72.dp)\n                    .zIndex(3f)\n                    .graphicsLayer {\n                        alpha = headerAlpha\n                        translationY = headerOffset.toPx()\n                    }\n                    .padding(start = 84.dp),'''
new = '''            val songHeaderShape = RoundedCornerShape(20.dp)\n            Row(\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .height(72.dp)\n                    .zIndex(3f)\n                    .graphicsLayer {\n                        alpha = headerAlpha\n                        translationY = headerOffset.toPx()\n                    }\n                    .then(\n                        if (queueVisible) {\n                            Modifier\n                                .clip(songHeaderShape)\n                                .meloXLiquidButton(\n                                    shape = songHeaderShape,\n                                    tint = Color.White.copy(alpha = .035f),\n                                    surfaceColor = Color.Black.copy(alpha = .10f),\n                                    blurRadius = 20.dp,\n                                    lensRadius = 14.dp,\n                                    refractionHeight = 18.dp,\n                                )\n                        } else Modifier\n                    )\n                    .padding(start = 84.dp, end = 8.dp),'''
s = replace_once(s, old, new, 'queue song header glass')
scene_path.write_text(s)

# ---------------------------------------------------------------------------
# Lyrics: keep true shaped text (previous patch) but stop using the inaccurate
# estimated-row inverse translation cascade. It is the source of multiline/CJK
# rows visually collapsing into each other. Programmatic scrolling remains
# smooth and keeps the playback line at the upstream 25% focus anchor.
# ---------------------------------------------------------------------------
lyrics_path = ROOT / 'ui/player/MeloXIOSLyricsPanel.kt'
s = lyrics_path.read_text()
s = replace_once(
    s,
    'val visualOffset = movementOffsets[index].value',
    'val visualOffset = 0f',
    'disable unsafe row translation',
)
s = replace_once(
    s,
    '''            val carried = movementOffsets.map { it.value }\n            scrollState.scrollTo(targetScroll)\n            movementOffsets.forEachIndexed { index, anim ->\n                anim.snapTo(movementDistance + carried[index])\n            }\n            visualFocusIndex = nextIndex\n''',
    '''            val carried = movementOffsets.map { it.value }\n            scrollState.animateScrollTo(\n                targetScroll,\n                tween(\n                    durationMillis = max(cascadeDurationMs, 1f).roundToInt(),\n                    easing = SourceSmoothStepEasing,\n                ),\n            )\n            movementOffsets.forEachIndexed { index, anim ->\n                anim.snapTo(carried[index])\n            }\n            visualFocusIndex = nextIndex\n''',
    'smooth adjacent focus scroll',
)
lyrics_path.write_text(s)

# ---------------------------------------------------------------------------
# Playlist: make the more button unmistakable and add a real one-tap full
# playlist download entry. This mirrors upstream PlaylistDetailView's download
# action while using Android MeloXDownloadStore.
# ---------------------------------------------------------------------------
lib_path = ROOT / 'ui/library/LibraryScreen.kt'
s = lib_path.read_text()
if 'import com.lladlam.melox.core.audio.MusicQualityPreferences\n' not in s:
    s = s.replace(
        'import com.lladlam.melox.core.account.NeteaseSessionStore\n',
        'import com.lladlam.melox.core.account.NeteaseSessionStore\nimport com.lladlam.melox.core.audio.MusicQualityPreferences\n',
        1,
    )

s = replace_once(
    s,
    'val cache = remember(appContext) { NeteaseLibraryCache(appContext) }\n    val accountClient = remember(appContext) {',
    'val cache = remember(appContext) { NeteaseLibraryCache(appContext) }\n    val downloadStore = remember(appContext) { MeloXDownloadStore.get(appContext) }\n    val accountClient = remember(appContext) {',
    'playlist download store',
)

s = replace_once(
    s,
    '''                        isSaved = isSaved == true,\n                        onToggleSaved = {''',
    '''                        isSaved = isSaved == true,\n                        onDownloadAll = {\n                            val quality = MusicQualityPreferences.read(appContext)\n                            songs.forEach { song ->\n                                if (!downloadStore.contains(song.id) && !downloadStore.isDownloading(song.id)) {\n                                    downloadStore.start(song, quality)\n                                }\n                            }\n                        },\n                        onToggleSaved = {''',
    'hero download callback',
)

s = replace_once(
    s,
    '''    isSaved: Boolean,\n    onToggleSaved: () -> Unit,\n    sharedTransitionScope: SharedTransitionScope,''',
    '''    isSaved: Boolean,\n    onDownloadAll: () -> Unit,\n    onToggleSaved: () -> Unit,\n    sharedTransitionScope: SharedTransitionScope,''',
    'hero download signature',
)

marker = '''            playlist.description\n                ?.takeIf(String::isNotBlank)'''
download_button = '''            Box(\n                modifier = Modifier\n                    .padding(top = 14.dp)\n                    .width(148.dp)\n                    .height(42.dp)\n                    .clip(RoundedCornerShape(21.dp))\n                    .meloXLiquidButton(\n                        shape = RoundedCornerShape(21.dp),\n                        enabled = tracks.isNotEmpty(),\n                        tint = glassColor(foreground).copy(alpha = .10f),\n                        surfaceColor = glassColor(foreground).copy(alpha = .46f),\n                        lensRadius = 10.dp,\n                        refractionHeight = 16.dp,\n                    )\n                    .clickable(enabled = tracks.isNotEmpty(), onClick = onDownloadAll),\n                contentAlignment = Alignment.Center,\n            ) {\n                Text(\n                    "↓ 一键下载",\n                    color = foreground,\n                    fontSize = 16.sp,\n                    fontWeight = FontWeight.SemiBold,\n                )\n            }\n\n'''
s = replace_once(s, marker, download_button + marker, 'playlist download all button')

# Replace the combined share/more capsule with two explicit glass circles so
# the ellipsis can never disappear into the shared capsule geometry.
old_toolbar = '''        Row(\n            modifier = Modifier\n                .height(44.dp)\n                .clip(RoundedCornerShape(22.dp))\n                .meloXLiquidBottomBar(\n                    shape = RoundedCornerShape(22.dp),\n                    tint = glassColor(foreground).copy(alpha = 0.18f),\n                    surfaceColor = glassColor(foreground).copy(alpha = 0.42f),\n                )\n                .padding(horizontal = 4.dp),\n            verticalAlignment = Alignment.CenterVertically,\n        ) {\n            Box(\n                modifier = Modifier\n                    .size(40.dp)\n                    .clickable(\n                        interactionSource = remember { MutableInteractionSource() },\n                        indication = null,\n                        onClick = onShare,\n                    ),\n                contentAlignment = Alignment.Center,\n            ) {\n                MeloXShareGlyph(Modifier.size(22.dp), Color(0xFFFF3147))\n            }\n            Box(\n                modifier = Modifier\n                    .size(40.dp)\n                    .clickable(\n                        interactionSource = remember { MutableInteractionSource() },\n                        indication = null,\n                        onClick = onMore,\n                    ),\n                contentAlignment = Alignment.Center,\n            ) {\n                Text(\n                    "•••",\n                    color = Color(0xFFFF3147),\n                    fontSize = 15.sp,\n                    fontWeight = FontWeight.Bold,\n                    letterSpacing = 1.sp,\n                )\n            }\n        }'''
new_toolbar = '''        Row(\n            verticalAlignment = Alignment.CenterVertically,\n            horizontalArrangement = Arrangement.spacedBy(8.dp),\n        ) {\n            MeloXGlassCircleButton(\n                foreground = foreground,\n                size = 44.dp,\n                onClick = onShare,\n            ) {\n                MeloXShareGlyph(Modifier.size(22.dp), Color(0xFFFF3147))\n            }\n            MeloXGlassCircleButton(\n                foreground = foreground,\n                size = 44.dp,\n                onClick = onMore,\n            ) {\n                Text(\n                    "•••",\n                    color = Color(0xFFFF3147),\n                    fontSize = 15.sp,\n                    fontWeight = FontWeight.Bold,\n                    letterSpacing = 1.sp,\n                )\n            }\n        }'''
s = replace_once(s, old_toolbar, new_toolbar, 'explicit playlist more button')
lib_path.write_text(s)
