from pathlib import Path
import re

ROOT = Path('android/app/src/main/kotlin/com/lladlam/melox')

def read(path): return path.read_text()
def write(path, text): path.write_text(text)
def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing pattern: {label}')
    return text.replace(old, new, 1)

# 1) Playback queue origin metadata -------------------------------------------------
p = ROOT / 'playback/PlaybackCommands.kt'
s = read(p)
s = replace_once(s, 'import android.net.Uri\n', 'import android.net.Uri\nimport android.os.Bundle\n', 'PlaybackCommands Bundle import')
s = s.replace('.map { song -> song.toMediaItem(quality) }', '.map { song -> song.toMediaItem(quality, QUEUE_ORIGIN_BASE) }')
s = s.replace('controller.addMediaItem(song.toMediaItem(quality))', 'controller.addMediaItem(song.toMediaItem(quality, QUEUE_ORIGIN_MANUAL))')
s = s.replace('controller.addMediaItem(insertion, song.toMediaItem(quality))', 'controller.addMediaItem(insertion, song.toMediaItem(quality, QUEUE_ORIGIN_MANUAL))')
s = replace_once(s,
'''    private fun SearchSong.toMediaItem(quality: MusicQuality): MediaItem {\n        val metadata = MediaMetadata.Builder()\n            .setTitle(name)\n            .setArtist(artists)\n            .setAlbumTitle(album)\n            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)\n''',
'''    private fun SearchSong.toMediaItem(quality: MusicQuality, queueOrigin: String): MediaItem {\n        val metadata = MediaMetadata.Builder()\n            .setTitle(name)\n            .setArtist(artists)\n            .setAlbumTitle(album)\n            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)\n            .setExtras(Bundle().apply { putString(QUEUE_ORIGIN_KEY, queueOrigin) })\n''', 'toMediaItem origin')
s = replace_once(s, 'object PlaybackCommands {\n    private const val TAG = "MeloXPlayback"\n', '''object PlaybackCommands {\n    private const val TAG = "MeloXPlayback"\n    const val QUEUE_ORIGIN_KEY = "melox.queue.origin"\n    const val QUEUE_ORIGIN_BASE = "base"\n    const val QUEUE_ORIGIN_MANUAL = "manual"\n''', 'queue constants')
write(p, s)

# 2) UI queue state keeps origin ----------------------------------------------------
p = ROOT / 'ui/player/MeloXPlayerUi.kt'
s = read(p)
s = replace_once(s, 'import com.lladlam.melox.playback.MeloXPlaybackService\n', 'import com.lladlam.melox.playback.MeloXPlaybackService\nimport com.lladlam.melox.playback.PlaybackCommands\n', 'player ui PlaybackCommands import')
s = replace_once(s,
'''data class MeloXQueueEntry(\n    val index: Int,\n    val mediaId: String,\n    val title: String,\n    val artist: String,\n    val artworkUrl: String?,\n)\n''',
'''enum class MeloXQueueOrigin { Base, Manual }\n\ndata class MeloXQueueEntry(\n    val index: Int,\n    val mediaId: String,\n    val title: String,\n    val artist: String,\n    val artworkUrl: String?,\n    val origin: MeloXQueueOrigin = MeloXQueueOrigin.Base,\n)\n''', 'queue entry origin')
s = replace_once(s,
'''                artworkUrl = metadata.artworkUri?.toString(),\n            )\n''',
'''                artworkUrl = metadata.artworkUri?.toString(),\n                origin = if (metadata.extras?.getString(PlaybackCommands.QUEUE_ORIGIN_KEY) == PlaybackCommands.QUEUE_ORIGIN_MANUAL) {\n                    MeloXQueueOrigin.Manual\n                } else {\n                    MeloXQueueOrigin.Base\n                },\n            )\n''', 'build queue origin')
s = replace_once(s,
'''    fun addCurrentToQueue() {\n        val player = controller ?: return\n        val item = player.currentMediaItem ?: return\n        player.addMediaItem(item)\n        refresh()\n    }\n''',
'''    fun addCurrentToQueue() {\n        val player = controller ?: return\n        val item = player.currentMediaItem ?: return\n        val extras = (item.mediaMetadata.extras ?: android.os.Bundle()).let { android.os.Bundle(it) }.apply {\n            putString(PlaybackCommands.QUEUE_ORIGIN_KEY, PlaybackCommands.QUEUE_ORIGIN_MANUAL)\n        }\n        val copied = item.buildUpon()\n            .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build())\n            .build()\n        player.addMediaItem(copied)\n        refresh()\n    }\n''', 'manual current queue origin')
write(p, s)

# 3) Queue page: history above current, explicit manual Queue section ---------------
p = ROOT / 'ui/player/MeloXQueuePanel.kt'
new_queue = r'''package com.lladlam.melox.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

    // History lives physically above the current-song/header anchor. Opening the
    // page starts at the anchor, so an upward browse naturally reveals previously
    // played items without mixing them into "继续播放".
    LaunchedEffect(state.mediaId, history.size, showSongHeader) {
        val anchor = history.size + if (history.isNotEmpty()) 1 else 0
        runCatching { listState.scrollToItem(anchor.coerceAtLeast(0)) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (history.isNotEmpty()) {
            item(key = "history-title") { QueueSectionTitle("历史记录", subdued = true) }
            items(history, key = { "history-${it.index}-${it.mediaId}" }) { entry ->
                QueueRow(entry, state)
            }
        }

        item(key = "current-header") {
            if (showSongHeader) {
                currentEntry?.let { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Artwork(entry.artworkUrl, Modifier.size(68.dp).clip(RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.artist, color = Color.White.copy(alpha = .64f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            } else {
                Spacer(Modifier.fillMaxWidth().height(72.dp))
            }
        }

        item(key = "mode-controls") {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                QueueModeButton("↝", state.shuffleEnabled, onClick = state::toggleShuffle, modifier = Modifier.weight(1f))
                QueueModeButton(if (state.repeatMode == Player.REPEAT_MODE_ONE) "↻1" else "↻", state.repeatMode != Player.REPEAT_MODE_OFF, onClick = state::cycleRepeatMode, modifier = Modifier.weight(1f))
                QueueModeButton("∞", state.autoplayEnabled, onClick = state::toggleAutoplay, modifier = Modifier.weight(1f))
                QueueModeButton("◎", state.autoMixEnabled, onClick = state::toggleAutoMix, modifier = Modifier.weight(1f))
            }
        }

        if (manualQueue.isNotEmpty()) {
            item(key = "manual-title") { QueueSectionTitle("队列") }
            items(manualQueue, key = { "manual-${it.index}-${it.mediaId}" }) { entry -> QueueRow(entry, state) }
        }

        item(key = "continue-title") { QueueSectionTitle("继续播放") }
        if (continuing.isEmpty()) {
            item(key = "continue-empty") {
                Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                    Text("没有待播放歌曲", color = Color.White.copy(alpha = .55f), fontSize = 15.sp)
                }
            }
        } else {
            items(continuing, key = { "continue-${it.index}-${it.mediaId}" }) { entry -> QueueRow(entry, state) }
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
        modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
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
            .meloXLiquidButton(
                shape = RoundedCornerShape(22.dp),
                enabled = enabled,
                surfaceColor = if (selected) Color.White.copy(alpha = .62f) else Color.White.copy(alpha = .10f),
                lensRadius = if (selected) 11.dp else 8.dp,
                refractionHeight = 16.dp,
            )
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
'''
write(p, new_queue)

# 4) lightweight mode state for buttons; engine hookup can be expanded independently ----
p = ROOT / 'ui/player/MeloXPlayerUi.kt'
s = read(p)
s = replace_once(s, '    var shuffleEnabled by mutableStateOf(false)\n        private set\n', '''    var shuffleEnabled by mutableStateOf(false)\n        private set\n    var autoplayEnabled by mutableStateOf(false)\n        private set\n    var autoMixEnabled by mutableStateOf(false)\n        private set\n''', 'mode state')
s = replace_once(s, '    fun changeVolume(value: Float) {\n', '''    fun toggleAutoplay() { autoplayEnabled = !autoplayEnabled }\n\n    fun toggleAutoMix() { autoMixEnabled = !autoMixEnabled }\n\n    fun changeVolume(value: Float) {\n''', 'mode toggles')
write(p, s)

# 5) Stable glass shell around action-page animation ----------------------------------
p = ROOT / 'ui/player/MeloXSongActionsOverlay.kt'
s = read(p)
old = '''            AnimatedContent(\n                targetState=page,\n                transitionSpec={ (fadeIn(spring(stiffness=520f))+scaleIn(initialScale=.96f)) togetherWith (fadeOut(spring(stiffness=620f))+scaleOut(targetScale=.96f)) },\n                modifier=Modifier.fillMaxWidth().padding(bottom=18.dp).meloXLiquidButton(\n                    shape=RoundedCornerShape(30.dp),tint=Color.White.copy(alpha=.08f),surfaceColor=Color.Black.copy(alpha=.12f),blurRadius=14.dp,lensRadius=20.dp,refractionHeight=22.dp,\n                ).clickable(interactionSource=remember{MutableInteractionSource()},indication=null,onClick={}),\n                label="song-action-page",\n            ) { target ->\n                Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=18.dp)) {\n'''
new = '''            Box(\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .padding(bottom = 18.dp)\n                    // Keep one stable Backdrop consumer alive while sub-pages swap.\n                    // Putting Liquid Glass directly on AnimatedContent caused its\n                    // transient old/new children to enter the capture lifecycle and\n                    // could leave a recursively blurred frame after navigating back.\n                    .meloXLiquidButton(\n                        shape = RoundedCornerShape(30.dp),\n                        tint = Color.White.copy(alpha = .08f),\n                        surfaceColor = Color.Black.copy(alpha = .12f),\n                        blurRadius = 14.dp,\n                        lensRadius = 20.dp,\n                        refractionHeight = 22.dp,\n                    )\n                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),\n            ) {\n                AnimatedContent(\n                    targetState = page,\n                    transitionSpec = { (fadeIn(spring(stiffness=520f)) + scaleIn(initialScale=.96f)) togetherWith (fadeOut(spring(stiffness=620f)) + scaleOut(targetScale=.96f)) },\n                    modifier = Modifier.fillMaxWidth(),\n                    label = "song-action-page",\n                ) { target ->\n                    Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=18.dp)) {\n'''
if old not in s: raise SystemExit('missing action AnimatedContent block')
s = s.replace(old, new, 1)
# close the new outer Box after AnimatedContent. The existing tail has three closes: Column, AnimatedContent, Box(content alignment).
needle = '''                }\n            }\n        }\n    }\n}\n\n@Composable private fun ActionHeader'''
replacement = '''                    }\n                }\n            }\n        }\n    }\n}\n\n@Composable private fun ActionHeader'''
if needle not in s: raise SystemExit('missing action overlay closing block')
s = s.replace(needle, replacement, 1)
write(p, s)

# 6) Lyrics panel: user-input-only browsing, fixed geometry, UI callbacks ---------------
p = ROOT / 'ui/player/MeloXIOSLyricsPanel.kt'
s = read(p)
s = s.replace('import androidx.compose.runtime.snapshotFlow\n', '')
s = s.replace('import kotlinx.coroutines.flow.collectLatest\n', '')
s = replace_once(s, 'import androidx.compose.ui.graphics.graphicsLayer\n', 'import androidx.compose.ui.geometry.Offset\nimport androidx.compose.ui.graphics.graphicsLayer\nimport androidx.compose.ui.input.nestedscroll.NestedScrollConnection\nimport androidx.compose.ui.input.nestedscroll.NestedScrollSource\nimport androidx.compose.ui.input.nestedscroll.nestedScroll\n', 'nested scroll imports')
s = replace_once(s, 'import androidx.compose.runtime.rememberCoroutineScope\n', 'import androidx.compose.runtime.rememberCoroutineScope\nimport androidx.compose.runtime.rememberUpdatedState\n', 'rememberUpdatedState import')
s = replace_once(s,
'''fun MeloXIOSLyricsPanel(\n    state: MeloXPlaybackUiState,\n    modifier: Modifier = Modifier,\n) {\n''',
'''fun MeloXIOSLyricsPanel(\n    state: MeloXPlaybackUiState,\n    modifier: Modifier = Modifier,\n    isInterfaceHidden: Boolean = false,\n    onInterfaceInteraction: () -> Unit = {},\n    onInterfaceVisibilityChange: (Boolean) -> Unit = {},\n) {\n''', 'lyrics panel signature')
# add manual gesture state after existing state declarations
needle = '''    var playbackFocusGeneration by remember(document) { mutableIntStateOf(0) }\n\n    val lineSpacingPx ='''
insert = '''    var playbackFocusGeneration by remember(document) { mutableIntStateOf(0) }\n    var browseGeneration by remember(document) { mutableIntStateOf(0) }\n    var scrollHideDistancePx by remember(document) { mutableStateOf(0f) }\n    val latestInterfaceHidden = rememberUpdatedState(isInterfaceHidden)\n    val latestAutomaticScroll = rememberUpdatedState(automaticScroll)\n    val latestVisibilityCallback = rememberUpdatedState(onInterfaceVisibilityChange)\n    val latestInteractionCallback = rememberUpdatedState(onInterfaceInteraction)\n\n    val lineSpacingPx ='''
if needle not in s: raise SystemExit('missing lyrics state insertion')
s = s.replace(needle, insert, 1)
# replace old scroll-in-progress collector
old = '''    // Manual browsing suspends playback following and resumes after the same\n    // 3-second default delay as AppSettings.lyricsFollowDelay upstream.\n    LaunchedEffect(scrollState, document) {\n        snapshotFlow { scrollState.isScrollInProgress }.collectLatest { scrolling ->\n            if (scrolling && !automaticScroll) {\n                isBrowsingLyrics = true\n            } else if (!scrolling && isBrowsingLyrics && !automaticScroll) {\n                delay(UpstreamLyrics.FOLLOW_DELAY_MS)\n                isBrowsingLyrics = false\n                playbackFocusGeneration += 1\n            }\n        }\n    }\n\n'''
new = '''    // Only real pointer/nested-scroll input enters browsing mode. Programmatic\n    // scrollTo/animateScrollTo must never disable its own lyric following.\n    val scrollHideThresholdPx = with(density) { 200.dp.toPx() }\n    val lyricInteractionConnection = remember(document, scrollHideThresholdPx) {\n        object : NestedScrollConnection {\n            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {\n                if (source != NestedScrollSource.UserInput || latestAutomaticScroll.value) return Offset.Zero\n                val offsetDelta = -available.y // match SwiftUI contentOffset delta\n                if (kotlin.math.abs(offsetDelta) < 0.01f) return Offset.Zero\n\n                isBrowsingLyrics = true\n                browseGeneration += 1\n                latestInteractionCallback.value.invoke()\n\n                if (offsetDelta < 0f) {\n                    // Scrolling back toward previous lyrics immediately restores UI.\n                    scrollHideDistancePx = 0f\n                    if (latestInterfaceHidden.value) latestVisibilityCallback.value.invoke(true)\n                } else if (!latestInterfaceHidden.value) {\n                    scrollHideDistancePx += offsetDelta\n                    if (scrollHideDistancePx >= scrollHideThresholdPx) {\n                        scrollHideDistancePx = 0f\n                        latestVisibilityCallback.value.invoke(false)\n                    }\n                }\n                return Offset.Zero\n            }\n        }\n    }\n\n    LaunchedEffect(browseGeneration, document) {\n        if (browseGeneration <= 0) return@LaunchedEffect\n        delay(UpstreamLyrics.FOLLOW_DELAY_MS)\n        isBrowsingLyrics = false\n        playbackFocusGeneration += 1\n    }\n\n'''
if old not in s: raise SystemExit('missing old browsing collector')
s = s.replace(old, new, 1)
# attach nested scroll to actual scroll container
s = replace_once(s, '''                    modifier = Modifier\n                        .fillMaxSize()\n                        .verticalScroll(scrollState),\n''', '''                    modifier = Modifier\n                        .fillMaxSize()\n                        .nestedScroll(lyricInteractionConnection)\n                        .verticalScroll(scrollState),\n''', 'lyrics nested scroll modifier')
# pass reserve flags + interaction reset
s = replace_once(s, '''                            showTranslation = MeloXSettingsRuntime.showLyricTranslation &&\n                                !line.translation.isNullOrBlank() &&\n                                index == visualFocusIndex,\n                            showRomanization = MeloXSettingsRuntime.showLyricRomanization &&\n                                !line.romanization.isNullOrBlank(),\n''', '''                            showTranslation = MeloXSettingsRuntime.showLyricTranslation &&\n                                !line.translation.isNullOrBlank() &&\n                                index == visualFocusIndex,\n                            showRomanization = MeloXSettingsRuntime.showLyricRomanization &&\n                                !line.romanization.isNullOrBlank(),\n                            reserveTranslation = MeloXSettingsRuntime.showLyricTranslation && !line.translation.isNullOrBlank(),\n                            reserveRomanization = MeloXSettingsRuntime.showLyricRomanization && !line.romanization.isNullOrBlank(),\n''', 'reserve annotation args')
s = s.replace('                            onClick = { state.seekTo(line.timeMs) },', '                            onClick = { onInterfaceInteraction(); state.seekTo(line.timeMs) },', 1)
# signature of line renderer
s = replace_once(s, '''    showTranslation: Boolean,\n    showRomanization: Boolean,\n    onMeasured: (Int) -> Unit,\n''', '''    showTranslation: Boolean,\n    showRomanization: Boolean,\n    reserveTranslation: Boolean,\n    reserveRomanization: Boolean,\n    onMeasured: (Int) -> Unit,\n''', 'line renderer signature')
# reserve romanization/translation layout
roman = '''        if (showRomanization) {\n            Text(\n                text = line.romanization.orEmpty(),\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .padding(top = UpstreamLyrics.ANNOTATION_SPACING_DP.dp),\n                color = Color.White.copy(alpha = UpstreamLyrics.ANNOTATION_OPACITY),\n                textAlign = TextAlign.Start,\n                fontSize = max(\n                    UpstreamLyrics.FONT_SIZE_SP * UpstreamLyrics.ROMANIZATION_FONT_SCALE,\n                    13f,\n                ).sp,\n                lineHeight = max(\n                    UpstreamLyrics.FONT_SIZE_SP * UpstreamLyrics.ROMANIZATION_FONT_SCALE,\n                    13f,\n                ).sp * 1.2f,\n                fontWeight = FontWeight.Black,\n            )\n        }\n\n        if (showTranslation) {\n'''
roman_new = '''        val romanSize = max(UpstreamLyrics.FONT_SIZE_SP * UpstreamLyrics.ROMANIZATION_FONT_SCALE, 13f)\n        if (showRomanization) {\n            Text(\n                text = line.romanization.orEmpty(),\n                modifier = Modifier.fillMaxWidth().padding(top = UpstreamLyrics.ANNOTATION_SPACING_DP.dp),\n                color = Color.White.copy(alpha = UpstreamLyrics.ANNOTATION_OPACITY),\n                textAlign = TextAlign.Start, fontSize = romanSize.sp, lineHeight = (romanSize * 1.2f).sp, fontWeight = FontWeight.Black,\n            )\n        } else if (reserveRomanization) {\n            Spacer(Modifier.height((romanSize * 1.2f).dp + UpstreamLyrics.ANNOTATION_SPACING_DP.dp))\n        }\n\n        val translationSize = max(UpstreamLyrics.FONT_SIZE_SP * UpstreamLyrics.TRANSLATION_FONT_SCALE, 13f)\n        if (showTranslation) {\n'''
if roman not in s: raise SystemExit('missing romanization block')
s = s.replace(roman, roman_new, 1)
translation_tail = '''                fontWeight = FontWeight.Black,\n            )\n        }\n    }\n}\n\nprivate fun sourceTimedAnnotatedString'''
translation_new = '''                fontWeight = FontWeight.Black,\n            )\n        } else if (reserveTranslation) {\n            Spacer(Modifier.height((translationSize * 1.2f).dp + UpstreamLyrics.ANNOTATION_SPACING_DP.dp))\n        }\n    }\n}\n\nprivate fun sourceTimedAnnotatedString'''
if translation_tail not in s: raise SystemExit('missing translation tail')
s = s.replace(translation_tail, translation_new, 1)
write(p, s)

# 7) Scene controls become overlay + upstream 5s auto hide -----------------------------
p = ROOT / 'ui/player/MeloXIOSNowPlayingScene.kt'
s = read(p)
s = replace_once(s, 'import androidx.compose.animation.core.FastOutSlowInEasing\n', 'import androidx.compose.animation.AnimatedVisibility\nimport androidx.compose.animation.fadeIn\nimport androidx.compose.animation.fadeOut\nimport androidx.compose.animation.slideInVertically\nimport androidx.compose.animation.slideOutVertically\nimport androidx.compose.animation.core.FastOutSlowInEasing\n', 'scene animation imports')
s = replace_once(s, 'import androidx.compose.runtime.Composable\n', 'import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect\nimport androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableIntStateOf\nimport androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.remember\nimport androidx.compose.runtime.setValue\n', 'scene runtime imports')
# remove duplicate getValue if exists
s = s.replace('import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.getValue\n', 'import androidx.compose.runtime.getValue\n')
s = replace_once(s, 'import androidx.compose.ui.zIndex\n', 'import androidx.compose.ui.zIndex\nimport kotlinx.coroutines.delay\n', 'scene delay import')
needle = '''    val queueVisible = page == MeloXNowPlayingPage.Queue\n\n    val artworkAlpha by animateFloatAsState'''
insert = '''    val queueVisible = page == MeloXNowPlayingPage.Queue\n    var showsLyricsControls by remember(state.mediaId) { mutableStateOf(true) }\n    var lyricsControlsActivityGeneration by remember(state.mediaId) { mutableIntStateOf(0) }\n\n    fun setLyricsControlsVisible(visible: Boolean) {\n        showsLyricsControls = visible\n        if (visible) lyricsControlsActivityGeneration += 1\n    }\n\n    LaunchedEffect(page) {\n        showsLyricsControls = true\n        if (page == MeloXNowPlayingPage.Lyrics) lyricsControlsActivityGeneration += 1\n    }\n    LaunchedEffect(page, showsLyricsControls, lyricsControlsActivityGeneration) {\n        if (page != MeloXNowPlayingPage.Lyrics || !showsLyricsControls) return@LaunchedEffect\n        delay(5_000L) // upstream defaultAppleMusicLyricsInterfaceAutoHideDelay\n        showsLyricsControls = false\n    }\n\n    val artworkAlpha by animateFloatAsState'''
if needle not in s: raise SystemExit('missing scene state insertion')
s = s.replace(needle, insert, 1)
# root Column -> Box + inner Column
old_root = '''    Column(\n        modifier = Modifier\n            .fillMaxSize()\n            .statusBarsPadding()\n            .padding(horizontal = 32.dp),\n    ) {\n        SceneGrabber(\n'''
new_root = '''    Box(modifier = Modifier.fillMaxSize()) {\n        Column(\n            modifier = Modifier\n                .fillMaxSize()\n                .statusBarsPadding()\n                .padding(horizontal = 32.dp),\n        ) {\n        SceneGrabber(\n'''
if old_root not in s: raise SystemExit('missing scene root column')
s = s.replace(old_root, new_root, 1)
# lyrics panel callback args
s = replace_once(s, '''                MeloXIOSLyricsPanel(\n                    state = state,\n                    modifier = Modifier.fillMaxSize(),\n                )\n''', '''                MeloXIOSLyricsPanel(\n                    state = state,\n                    modifier = Modifier.fillMaxSize(),\n                    isInterfaceHidden = !showsLyricsControls,\n                    onInterfaceInteraction = { setLyricsControlsVisible(true) },\n                    onInterfaceVisibilityChange = { setLyricsControlsVisible(it) },\n                )\n''', 'scene lyrics callback')
# replace tail controls with overlay
old_tail = '''        MeloXNowPlayingCoreControls(\n            state = state,\n            page = page,\n            onShowQuality = onShowQuality,\n            onPageSelected = { destination ->\n                onPageChanged(\n                    if (page == destination) {\n                        MeloXNowPlayingPage.Artwork\n                    } else {\n                        destination\n                    },\n                )\n            },\n        )\n    }\n}\n'''
new_tail = '''        }\n\n        AnimatedVisibility(\n            visible = page != MeloXNowPlayingPage.Lyrics || showsLyricsControls,\n            modifier = Modifier\n                .align(Alignment.BottomCenter)\n                .padding(horizontal = 32.dp),\n            enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) +\n                slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 8 },\n            exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) +\n                slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it / 8 },\n        ) {\n            MeloXNowPlayingCoreControls(\n                state = state,\n                page = page,\n                onShowQuality = onShowQuality,\n                onPageSelected = { destination ->\n                    setLyricsControlsVisible(true)\n                    onPageChanged(\n                        if (page == destination) MeloXNowPlayingPage.Artwork else destination,\n                    )\n                },\n            )\n        }\n    }\n}\n'''
if old_tail not in s: raise SystemExit('missing scene controls tail')
s = s.replace(old_tail, new_tail, 1)
write(p, s)

print('phase1 patch applied')
