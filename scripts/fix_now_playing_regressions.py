from pathlib import Path
import re

ROOT = Path('android/app/src/main/kotlin/com/lladlam/melox')


def read(path):
    return path.read_text()


def write(path, text):
    path.write_text(text)


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing pattern: {label}')
    return text.replace(old, new, 1)


# 1) Lyrics: never use TextLayoutResult.getPathForRange as a glyph outline.
# Android documents it as a path enclosing a text range, so using it as the
# actual font outline produces rectangular/tofu blocks for CJK/fallback fonts.
p = ROOT / 'ui/player/MeloXIOSLyricsPanel.kt'
s = read(p)
s = replace_once(
    s,
    'timed = hasSyllableSync && fp > 0.0001f,',
    'timed = hasSyllableSync && index == highlightedIndex,',
    'current line timed renderer',
)

start = s.find('@Composable\nprivate fun MeloXGlyphLyricText(')
end = s.find('\nprivate fun sourceGlyphVisuals(', start)
if start < 0 or end < 0:
    raise SystemExit('missing MeloXGlyphLyricText block')

renderer = r'''@Composable
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
                // drawText keeps Compose/Android's normal shaping and font fallback,
                // including Japanese/CJK/emoji fallback fonts.
                drawText(layout, color = Color.White)
                return@Canvas
            }

            // Render each timed character by clipping the *normally shaped full
            // TextLayoutResult*. This preserves fallback glyphs. getPathForRange()
            // is a selection/range enclosure path, not a glyph-outline API.
            for (offset in line.text.indices) {
                val ch = line.text[offset]
                if (ch == '\n' || ch == '\r' || Character.isLowSurrogate(ch)) continue
                val bounds = runCatching { layout.getBoundingBox(offset) }.getOrNull() ?: continue
                if (!bounds.width.isFinite() || !bounds.height.isFinite() || bounds.width <= 0f || bounds.height <= 0f) continue
                val fx = visuals.getOrElse(offset) { MeloXGlyphVisual(0f, 0f, 1f, 0f) }

                withTransform({
                    translate(left = 0f, top = -fx.liftPx)
                    scale(scaleX = fx.scale, scaleY = fx.scale, pivot = bounds.center)
                }) {
                    clipRect(
                        left = bounds.left,
                        top = bounds.top,
                        right = bounds.right,
                        bottom = bounds.bottom,
                    ) {
                        // Upstream draws a complete unplayed layer first.
                        drawText(
                            layout,
                            color = Color.White.copy(alpha = UpstreamLyrics.UNPLAYED_OPACITY),
                        )

                        val reveal = fx.reveal.coerceIn(0f, 1f)
                        if (reveal <= 0f) return@clipRect

                        val feather = max(
                            bounds.width * UpstreamLyrics.HIGHLIGHT_GRADIENT_WIDTH,
                            1.5f * density.density,
                        )
                        val front = bounds.left - feather + (bounds.width + feather) * reveal
                        val solidRight = min(front, bounds.right)

                        fun drawRevealed(alpha: Float) {
                            if (solidRight > bounds.left) {
                                clipRect(
                                    left = bounds.left,
                                    top = bounds.top,
                                    right = solidRight,
                                    bottom = bounds.bottom,
                                ) {
                                    drawText(layout, color = Color.White.copy(alpha = alpha))
                                }
                            }

                            val stopCount = 8
                            for (step in 0 until stopCount) {
                                val a = step.toFloat() / stopCount.toFloat()
                                val b = (step + 1).toFloat() / stopCount.toFloat()
                                val mid = (a + b) * .5f
                                val remaining = 1f - mid
                                val maskAlpha = remaining *
                                    (1f - UpstreamLyrics.HIGHLIGHT_GRADIENT_REDUCTION * mid)
                                val left = max(front + feather * a, bounds.left)
                                val right = min(front + feather * b, bounds.right)
                                if (right > left) {
                                    clipRect(
                                        left = left,
                                        top = bounds.top,
                                        right = right,
                                        bottom = bounds.bottom,
                                    ) {
                                        drawText(
                                            layout,
                                            color = Color.White.copy(
                                                alpha = alpha * maskAlpha.coerceIn(0f, 1f),
                                            ),
                                        )
                                    }
                                }
                            }
                        }

                        // Keep the upstream long-tone envelope without relying on
                        // vector glyph extraction. Two low-opacity passes provide
                        // the bloom while the final pass is the actual played text.
                        if (fx.glow > 0.001f) {
                            drawRevealed((fx.glow * .10f).coerceIn(0f, .24f))
                            drawRevealed((fx.glow * .18f).coerceIn(0f, .36f))
                        }
                        drawRevealed(1f)
                    }
                }
            }
        }
    }
}
'''
s = s[:start] + renderer + s[end:]
write(p, s)


# 2) Artwork page: bottom controls are an overlay. Reserve their height so the
# artwork footer (title/artist/more) stays between artwork and progress instead of
# being covered and visually ending up below the volume control.
p = ROOT / 'ui/player/MeloXIOSNowPlayingScene.kt'
s = read(p)
s = replace_once(
    s,
    'import androidx.compose.foundation.shape.CircleShape\n',
    'import androidx.compose.foundation.shape.CircleShape\nimport androidx.compose.foundation.shape.RoundedCornerShape\n',
    'RoundedCornerShape import',
)
s = replace_once(
    s,
    'import androidx.compose.ui.zIndex\n',
    'import androidx.compose.ui.zIndex\nimport com.lladlam.melox.ui.glass.meloXLiquidButton\n',
    'glass import',
)

pattern = re.compile(
    r'(private fun ArtworkDetailsWithoutArtwork\(.*?\n)(.*?)(\n            Spacer\(Modifier\.height\(8\.dp\)\)\n        }\n    }\n})',
    re.S,
)
match = pattern.search(s)
if not match:
    raise SystemExit('missing ArtworkDetailsWithoutArtwork footer')
body = match.group(2)
replacement_tail = '''\n            Spacer(Modifier.height(8.dp))\n            Spacer(Modifier.height(MeloXNowPlayingControlsHeight.dp))\n        }\n    }\n}'''
s = s[:match.start()] + match.group(1) + body + replacement_tail + s[match.end():]

old_controls = '''        ) {\n            MeloXNowPlayingCoreControls(\n                state = state,\n                page = page,\n                onShowQuality = onShowQuality,\n                onPageSelected = { destination ->\n                    setLyricsControlsVisible(true)\n                    onPageChanged(\n                        if (page == destination) MeloXNowPlayingPage.Artwork else destination,\n                    )\n                },\n            )\n        }\n'''
new_controls = '''        ) {\n            val controlsShape = RoundedCornerShape(28.dp)\n            val controlsSurface = if (page == MeloXNowPlayingPage.Lyrics) {\n                Modifier\n                    .fillMaxWidth()\n                    .clip(controlsShape)\n                    .meloXLiquidButton(\n                        shape = controlsShape,\n                        tint = Color.White.copy(alpha = .035f),\n                        surfaceColor = Color.Black.copy(alpha = .10f),\n                        blurRadius = 20.dp,\n                        lensRadius = 14.dp,\n                        refractionHeight = 18.dp,\n                    )\n            } else {\n                Modifier.fillMaxWidth()\n            }\n            Box(modifier = controlsSurface) {\n                MeloXNowPlayingCoreControls(\n                    state = state,\n                    page = page,\n                    onShowQuality = onShowQuality,\n                    onPageSelected = { destination ->\n                        setLyricsControlsVisible(true)\n                        onPageChanged(\n                            if (page == destination) MeloXNowPlayingPage.Artwork else destination,\n                        )\n                    },\n                )\n            }\n        }\n'''
s = replace_once(s, old_controls, new_controls, 'lyrics controls blur shell')
write(p, s)


# 3) Shared persistent artwork must use the same bottom-controls reservation as
# ArtworkDetailsWithoutArtwork so the artwork and metadata footer stay aligned.
p = ROOT / 'ui/player/MeloXIOSNowPlayingSharedHost.kt'
s = read(p)
s = replace_once(
    s,
    'val artworkFooterHeight = 78.dp',
    'val artworkFooterHeight = 78.dp + MeloXNowPlayingControlsHeight.dp',
    'shared artwork footer reserve',
)
write(p, s)
