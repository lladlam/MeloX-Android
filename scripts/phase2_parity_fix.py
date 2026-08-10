from pathlib import Path
import re

ROOT=Path('android/app/src/main/kotlin/com/lladlam/melox')
def r(p): return p.read_text()
def w(p,s): p.write_text(s)
def one(s,a,b,label):
    if a not in s: raise SystemExit('missing '+label)
    return s.replace(a,b,1)

# ---------------------------------------------------------------------
# Custom stable lyric renderer using TextMeasurer + glyph paths.
p=ROOT/'ui/player/MeloXIOSLyricsPanel.kt'; s=r(p)
s=one(s,'import androidx.compose.foundation.clickable\n','import androidx.compose.foundation.Canvas\nimport androidx.compose.foundation.clickable\n','Canvas import')
s=one(s,'import androidx.compose.ui.graphics.Shadow\n','import androidx.compose.ui.graphics.Shadow\nimport androidx.compose.ui.graphics.drawscope.Stroke\nimport androidx.compose.ui.graphics.drawscope.withTransform\n','draw imports')
s=one(s,'import androidx.compose.ui.text.AnnotatedString\n','import androidx.compose.ui.text.AnnotatedString\nimport androidx.compose.ui.text.TextStyle\nimport androidx.compose.ui.text.drawText\nimport androidx.compose.ui.text.rememberTextMeasurer\n','text measurer imports')
s=one(s,'import androidx.compose.ui.unit.dp\n','import androidx.compose.ui.unit.Constraints\nimport androidx.compose.ui.unit.dp\n','constraints import')
old='''        Text(\n            text = if (timed && line.syllables.isNotEmpty()) {\n                sourceTimedAnnotatedString(line, positionMs)\n            } else {\n                AnnotatedString(line.text)\n            },\n            modifier = Modifier.fillMaxWidth(),\n            color = Color.White,\n            textAlign = TextAlign.Start,\n            fontSize = UpstreamLyrics.FONT_SIZE_SP.sp,\n            lineHeight = UpstreamLyrics.LINE_HEIGHT_SP.sp,\n            fontWeight = FontWeight.Black,\n        )\n'''
new='''        MeloXGlyphLyricText(\n            line = line,\n            playbackTimeMs = positionMs,\n            timed = timed && line.syllables.isNotEmpty(),\n            modifier = Modifier.fillMaxWidth(),\n        )\n'''
if old not in s: raise SystemExit('missing primary lyric Text')
s=s.replace(old,new,1)
# Insert custom renderer before old annotated-string helper. Keep helper for fallback/reference.
marker='''private fun sourceTimedAnnotatedString(line: LyricLine, playbackTimeMs: Long) =\n'''
renderer=r'''private data class MeloXGlyphVisual(
    val opacity: Float,
    val liftPx: Float,
    val scale: Float,
    val glow: Float,
)

/**
 * Compose counterpart of upstream LyricGlowTextRenderer.
 *
 * Layout is measured once at the promoted line geometry. Timing effects are
 * applied only during Canvas drawing using TextLayoutResult glyph paths. This is
 * the important invariant from the SwiftUI TextRenderer implementation: lift,
 * long-tone expansion and glow never reflow the line or move the focus anchor.
 */
@Composable
private fun MeloXGlyphLyricText(
    line: LyricLine,
    playbackTimeMs: Long,
    timed: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val style = TextStyle(
            color = Color.White,
            fontSize = UpstreamLyrics.FONT_SIZE_SP.sp,
            lineHeight = UpstreamLyrics.LINE_HEIGHT_SP.sp,
            fontWeight = FontWeight.Black,
        )
        val layout = remember(line.text, widthPx, style) {
            textMeasurer.measure(
                text = AnnotatedString(line.text),
                style = style,
                constraints = Constraints(maxWidth = widthPx),
                softWrap = true,
            )
        }
        val height = with(density) { layout.size.height.toDp() }
        val visuals = remember(line, playbackTimeMs, timed, density.density) {
            if (timed) sourceGlyphVisuals(line, playbackTimeMs, density.density)
            else List(line.text.length) { MeloXGlyphVisual(1f, 0f, 1f, 0f) }
        }

        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (!timed || line.text.isEmpty()) {
                drawText(layout, color = Color.White)
                return@Canvas
            }

            // Glyph paths come from the full measured paragraph, so kerning,
            // wrapping and baseline geometry stay identical for every frame.
            for (offset in line.text.indices) {
                val ch = line.text[offset]
                if (ch == '\n' || ch == '\r') continue
                val path = runCatching { layout.getPathForRange(offset, offset + 1) }.getOrNull() ?: continue
                val bounds = runCatching { layout.getBoundingBox(offset) }.getOrNull() ?: continue
                val fx = visuals.getOrElse(offset) { MeloXGlyphVisual(UpstreamLyrics.UNPLAYED_OPACITY, 0f, 1f, 0f) }
                withTransform({
                    translate(left = 0f, top = -fx.liftPx)
                    scale(scaleX = fx.scale, scaleY = fx.scale, pivot = bounds.center)
                }) {
                    if (fx.glow > 0.001f) {
                        drawPath(
                            path = path,
                            color = Color.White.copy(alpha = (fx.glow * 0.22f).coerceIn(0f, .42f)),
                            style = Stroke(width = max(1f, UpstreamLyrics.FONT_SIZE_SP * density.density * .075f)),
                        )
                    }
                    drawPath(path = path, color = Color.White.copy(alpha = fx.opacity.coerceIn(0f, 1f)))
                }
            }
        }
    }
}

private fun sourceGlyphVisuals(
    line: LyricLine,
    playbackTimeMs: Long,
    density: Float,
): List<MeloXGlyphVisual> {
    val result = MutableList(line.text.length) {
        MeloXGlyphVisual(UpstreamLyrics.UNPLAYED_OPACITY, 0f, 1f, 0f)
    }
    var searchFrom = 0
    for (syllable in line.syllables) {
        if (syllable.text.isEmpty()) continue
        val located = line.text.indexOf(syllable.text, startIndex = searchFrom)
        val startIndex = if (located >= 0) located else searchFrom.coerceAtMost(line.text.length)
        val count = syllable.text.length.coerceAtMost(line.text.length - startIndex)
        if (count <= 0) continue
        searchFrom = startIndex + count
        val syllableDuration = max(syllable.endTimeMs - syllable.startTimeMs, 0L).toFloat()
        val characterDuration = syllableDuration / count.toFloat()
        for (local in 0 until count) {
            val offset = startIndex + local
            val start = syllable.startTimeMs + characterDuration * local
            val end = if (local == count - 1) max(syllable.endTimeMs.toFloat(), start) else start + characterDuration
            val duration = max(end - start, 0f)
            val raw = when {
                playbackTimeMs < start -> 0f
                playbackTimeMs >= end -> 1f
                duration <= 0f -> 1f
                else -> ((playbackTimeMs - start) / duration).coerceIn(0f, 1f)
            }
            val char = line.text[offset]
            val longTone = syllableDuration >= UpstreamLyrics.LONG_TONE_THRESHOLD_MS && !char.isWhitespace()
            val reveal = sourceHighlightRevealProgress(playbackTimeMs.toFloat(), start, end, raw, longTone)
            val liftEnd = end + UpstreamLyrics.LIFT_CONTINUATION_MS
            val lift = if (playbackTimeMs <= start) 0f else sourceSmootherStep(
                ((playbackTimeMs - start) / max(liftEnd - start, 1f)).toFloat(),
            )
            val risePx = min(max(UpstreamLyrics.FONT_SIZE_SP * .1f, 1.5f), 6f) * density
            val envelope = if (longTone) sourceLongToneEnvelope(
                playbackTimeMs.toFloat(), syllable.startTimeMs.toFloat(), syllableDuration, local, count,
            ) else 0f
            val expansionAmount = if (longTone) {
                .7f + .3f * sourceSmootherStep(
                    (syllableDuration - UpstreamLyrics.LONG_TONE_THRESHOLD_MS) /
                        (2800f - UpstreamLyrics.LONG_TONE_THRESHOLD_MS),
                )
            } else 0f
            val scale = 1f + (UpstreamLyrics.LONG_TONE_MAX_SCALE - 1f) * envelope * expansionAmount
            val glowAmount = if (longTone) .32f + .38f * sourceSmootherStep(
                (syllableDuration - UpstreamLyrics.LONG_TONE_THRESHOLD_MS) /
                    (2800f - UpstreamLyrics.LONG_TONE_THRESHOLD_MS),
            ) else 0f
            result[offset] = MeloXGlyphVisual(
                opacity = UpstreamLyrics.UNPLAYED_OPACITY + (1f - UpstreamLyrics.UNPLAYED_OPACITY) * reveal,
                liftPx = risePx * lift,
                scale = scale,
                glow = envelope * glowAmount,
            )
        }
    }
    return result
}

'''
if marker not in s: raise SystemExit('missing timed helper marker')
s=s.replace(marker,renderer+marker,1)
w(p,s)

# ---------------------------------------------------------------------
# About: correct authorship and upstream open-source acknowledgements.
p=ROOT/'ui/settings/SettingsScreen.kt'; s=r(p)
old=re.search(r'@Composable\nprivate fun AboutSettings\(context: android\.content\.Context\) \{.*?\n\}\n\n@Composable\nprivate fun DeveloperSettings',s,re.S)
if not old: raise SystemExit('AboutSettings block missing')
new=r'''@Composable
private fun AboutSettings(context: android.content.Context) {
    SettingsInfoCard(
        "MeloX Android",
        "MeloX 的 Android 原生迁移版。\n\nAndroid 原生迁移与维护：lladlam\n上游 iOS 原生项目：youshen2/MeloX（SwiftUI）",
    )
    Spacer(Modifier.height(14.dp))
    SettingsGlassGroup {
        Column(Modifier.padding(16.dp)) {
            Text("项目与许可", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "MeloX 主体：GNU GPLv3\n" +
                    "qier222/YesPlayMusic：网易云接口与播放器实现参考（MIT）\n" +
                    "jayfunc/BetterLyrics：逐字歌词渲染、光效与动效参考\n" +
                    "WXRIW/Lyricify-Lyrics-Helper：网易云 YRC 解析参考\n" +
                    "neteasecloudmusicapienhanced/api-enhanced：听歌识曲与音频指纹运行时\n" +
                    "DanteAlighieri13210914/pv-tool：文字 PV 原始实现（Non-Commercial License）\n" +
                    "mjhydri/BeatNet：自动混音节拍/重拍/速度分析（CC BY 4.0）\n" +
                    "Kyant0 AndroidLiquidGlass / Backdrop：Android 液态玻璃渲染基础",
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    SettingsActionButton("打开 MeloX Android GitHub") {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lladlam/MeloX-Android"))) }
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton("查看上游 iOS MeloX") {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/youshen2/MeloX"))) }
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton("查看上游项目与许可") {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/youshen2/MeloX/blob/main/MeloX/Features/Legal/ProjectLicensesView.swift"))) }
    }
}

@Composable
private fun DeveloperSettings'''
s=s[:old.start()]+new+s[old.end():]
w(p,s)

# ---------------------------------------------------------------------
# Bottom Dock selection lens: keep refracting capsule away from clipping edges.
p=ROOT/'ui/MeloXApp.kt'; s=r(p)
s=one(s,'import androidx.compose.ui.input.pointer.pointerInput\n','import androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.ui.platform.LocalDensity\n','LocalDensity import')
needle='''                val tabBarMaxWidthPx = constraints.maxWidth\n                Box(Modifier.fillMaxSize()) {\n'''
insert='''                val tabBarMaxWidthPx = constraints.maxWidth\n                val density = LocalDensity.current\n                val selectionEdgeInset = 5.dp\n                val selectionEdgeInsetPx = with(density) { selectionEdgeInset.toPx() }\n                val selectionTravelWidthPx = (tabBarMaxWidthPx - selectionEdgeInsetPx * 2f).coerceAtLeast(1f)\n                val selectionSegmentPx = selectionTravelWidthPx / 4f\n                val selectionWidth = (maxWidth - selectionEdgeInset * 2f) / 4f\n                Box(Modifier.fillMaxSize()) {\n'''
if needle not in s: raise SystemExit('dock geometry marker missing')
s=s.replace(needle,insert,1)
s=s.replace('''                            .fillMaxWidth(0.25f)\n                            .fillMaxHeight()\n                            .offset {\n                                IntOffset(\n                                    x = (\n                                        lensPosition * tabBarMaxWidthPx / 4f\n                                        ).roundToInt(),\n                                    y = 0,\n                                )\n                            }\n''','''                            .width(selectionWidth)\n                            .fillMaxHeight()\n                            .offset {\n                                IntOffset(\n                                    x = (selectionEdgeInsetPx + lensPosition * selectionSegmentPx).roundToInt(),\n                                    y = 0,\n                                )\n                            }\n''',1)
w(p,s)

# ---------------------------------------------------------------------
# Discovery/Home playlists open a real detail scene instead of immediately playing.
p=ROOT/'ui/discovery/MeloXDiscoveryScreens.kt'; s=r(p)
# imports
s=one(s,'import androidx.compose.foundation.layout.height\n','import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.statusBarsPadding\n','status bar import')
s=one(s,'import androidx.compose.runtime.mutableStateOf\n','import androidx.compose.runtime.mutableStateOf\nimport androidx.activity.compose.BackHandler\n','backhandler import')
# home selected state and route
s=one(s,'    var error by remember { mutableStateOf<String?>(null) }\n\n    fun refresh() {','''    var error by remember { mutableStateOf<String?>(null) }\n    var selectedPlaylist by remember { mutableStateOf<NeteasePlaylistSummary?>(null) }\n\n    selectedPlaylist?.let { playlist ->\n        DiscoveryPlaylistDetail(playlist = playlist, onBack = { selectedPlaylist = null })\n        return\n    }\n\n    fun refresh() {''','home selected state')
s=s.replace('item { PlaylistRow(value.playlists, context) }','item { PlaylistRow(value.playlists) { selectedPlaylist = it } }',1)
# explore selected state
s=one(s,'    var error by remember { mutableStateOf<String?>(null) }\n\n    fun refresh() {','''    var error by remember { mutableStateOf<String?>(null) }\n    var selectedPlaylist by remember { mutableStateOf<NeteasePlaylistSummary?>(null) }\n\n    selectedPlaylist?.let { playlist ->\n        DiscoveryPlaylistDetail(playlist = playlist, onBack = { selectedPlaylist = null })\n        return\n    }\n\n    fun refresh() {''','explore selected state')
s=s.replace('if (playlists.isEmpty()) EmptyOrLoading(refreshing, error) else PlaylistGrid(playlists, context)','if (playlists.isEmpty()) EmptyOrLoading(refreshing, error) else PlaylistGrid(playlists) { selectedPlaylist = it }',1)
# replace row/grid funcs
s=re.sub(r'''@Composable\nprivate fun PlaylistRow\(values: List<NeteasePlaylistSummary>, context: android\.content\.Context\) \{.*?\n\}\n\n@Composable\nprivate fun PlaylistGrid\(values: List<NeteasePlaylistSummary>, context: android\.content\.Context\) \{.*?\n\}\n''',r'''@Composable
private fun PlaylistRow(values: List<NeteasePlaylistSummary>, onSelect: (NeteasePlaylistSummary) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(values, key = { it.id }) { playlist -> PlaylistCard(playlist, Modifier.width(174.dp)) { onSelect(playlist) } }
    }
}

@Composable
private fun PlaylistGrid(values: List<NeteasePlaylistSummary>, onSelect: (NeteasePlaylistSummary) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 146.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) { items(values, key = { it.id }) { playlist -> PlaylistCard(playlist, Modifier.fillMaxWidth()) { onSelect(playlist) } } }
}
''',s,count=1,flags=re.S)
# append detail before EmptyOrLoading
marker='''@Composable\nprivate fun EmptyOrLoading'''
detail=r'''@Composable
private fun DiscoveryPlaylistDetail(
    playlist: NeteasePlaylistSummary,
    onBack: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val client = remember(context) { NeteaseLibraryClient { NeteaseSessionStore.readCookie(context) } }
    var detail by remember(playlist.id) { mutableStateOf<com.lladlam.melox.core.library.NeteasePlaylistDetail?>(null) }
    var error by remember(playlist.id) { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    LaunchedEffect(playlist.id) {
        runCatching { client.playlistDetail(playlist.id) }
            .onSuccess { detail = it }
            .onFailure { error = it.message ?: "歌单加载失败" }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 146.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹", fontSize = 44.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 10.dp))
                Text(playlist.name, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(playlist.coverUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(156.dp).clip(RoundedCornerShape(22.dp)))
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (playlist.creatorName.isNotBlank()) Text(playlist.creatorName, modifier = Modifier.padding(top = 7.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f), fontSize = 13.sp)
                    val songs = detail?.songs.orEmpty()
                    if (songs.isNotEmpty()) {
                        Text(
                            "▶  播放全部",
                            modifier = Modifier.padding(top = 15.dp).clip(RoundedCornerShape(22.dp)).background(Accent).clickable { PlaybackCommands.playQueue(context, songs, songs.first().id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = Color.White, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        playlist.description?.takeIf(String::isNotBlank)?.let { description ->
            item { Text(description, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f), fontSize = 13.sp, lineHeight = 19.sp) }
        }
        val value = detail
        when {
            value != null -> items(value.songs, key = { it.id }) { song ->
                SongRow(song) { PlaybackCommands.playQueue(context, value.songs, song.id) }
            }
            error != null -> item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            else -> item { Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) } }
        }
    }
}

@Composable
private fun EmptyOrLoading'''
if marker not in s: raise SystemExit('EmptyOrLoading marker missing')
s=s.replace(marker,detail,1)
w(p,s)

print('phase2 parity patch applied')
