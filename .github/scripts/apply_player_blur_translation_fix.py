from pathlib import Path

ROOT = Path('.')

def load(path): return (ROOT / path).read_text()
def save(path, text): (ROOT / path).write_text(text)
def rep(text, old, new, label):
    if old not in text:
        raise RuntimeError(f'missing {label}')
    return text.replace(old, new, 1)

# --- pure backdrop blur modifier: intentionally no lens/refraction/vibrancy ---
p='android/app/src/main/kotlin/com/lladlam/melox/ui/glass/MeloXBackdropComponents.kt'
s=load(p)
anchor='''\n/** Official LiquidBottomTabs-style outer panel. */'''
insert='''\n/**\n * Plain background blur. Unlike Liquid Glass this applies no lens, refraction\n * or vibrancy; it only blurs the recorded scene and optionally lays a tint.\n */\n@Composable\nfun Modifier.meloXBackdropBlur(\n    shape: Shape,\n    blurRadius: Dp = 20.dp,\n    surfaceColor: Color = Color.Transparent,\n): Modifier {\n    val backdrop = LocalMeloXBackdrop.current\n    if (backdrop == null) return background(surfaceColor, shape)\n    return drawBackdrop(\n        backdrop = backdrop,\n        shape = { shape },\n        effects = { blur(blurRadius.toPx()) },\n        highlight = null,\n        shadow = null,\n        innerShadow = null,\n        onDrawSurface = {\n            if (surfaceColor != Color.Transparent) drawRect(surfaceColor)\n        },\n    )\n}\n\n/** Official LiquidBottomTabs-style outer panel. */'''
s=rep(s,anchor,insert,'blur helper anchor')
save(p,s)

# --- lyrics: translations on every line + no brightness trough during handoff ---
p='android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSLyricsPanel.kt'
s=load(p)
s=rep(s,
'''                        val fp = focusProgress[index].value.coerceIn(0f, 1f)\n                        val distanceBlur = sourceDistanceBlurRadius(''',
'''                        val fp = focusProgress[index].value.coerceIn(0f, 1f)\n                        // The incoming line starts the 120 ms colour handoff before\n                        // its timestamp, then keeps full emphasis until the normal\n                        // focus Animatable catches up. This prevents the one-frame\n                        // dark trough that used to happen at sentence boundaries.\n                        val incomingLead = when {\n                            index == highlightedIndex + 1 -> sourceSmootherStep(\n                                ((effectivePositionMs - (line.timeMs - UpstreamLyrics.FOCUS_COLOR_DURATION_MS)) /\n                                    UpstreamLyrics.FOCUS_COLOR_DURATION_MS.toFloat()).coerceIn(0f, 1f),\n                            )\n                            index == highlightedIndex &&\n                                effectivePositionMs - line.timeMs in 0..UpstreamLyrics.FOCUS_COLOR_DURATION_MS.toLong() -> 1f\n                            else -> 0f\n                        }\n                        val effectiveFocus = max(fp, incomingLead)\n                        val distanceBlur = sourceDistanceBlurRadius(''',
'focus bridge')
s=s.replace('focusProgress = fp,\n                        )\n                        val preceding', 'focusProgress = effectiveFocus,\n                        )\n                        val preceding', 1)
s=rep(s,
'''                        val focusBlur = sourceFocusBlurRadius(\n                            UpstreamLyrics.BLUR_INTENSITY,\n                            preceding,\n                            following,\n                        )''',
'''                        val focusBlur = sourceFocusBlurRadius(\n                            UpstreamLyrics.BLUR_INTENSITY,\n                            preceding,\n                            following,\n                        ) * (1f - effectiveFocus)''',
'focus blur bridge')
s=s.replace('''                            fp,\n                        )\n                        val emphasis = sourceEmphasis(fp, UpstreamLyrics.DIM_AMOUNT)''',
'''                            effectiveFocus,\n                        )\n                        val emphasis = sourceEmphasis(effectiveFocus, UpstreamLyrics.DIM_AMOUNT)''',1)
s=rep(s,
'''                            focusProgress = fp,''',
'''                            focusProgress = effectiveFocus,''',
'line focus param')
s=rep(s,
'''                            showTranslation = MeloXSettingsRuntime.showLyricTranslation &&\n                                !line.translation.isNullOrBlank() &&\n                                index == visualFocusIndex,''',
'''                            showTranslation = MeloXSettingsRuntime.showLyricTranslation &&\n                                !line.translation.isNullOrBlank(),''',
'all-line translation')
s=s.replace('''        // Romanization defaults to all-lines upstream; translation defaults to\n        // focused-line. Hidden translation is accounted for in promoted layout\n        // estimation so focus changes do not reflow the scroll geometry.''',
'''        // When the user enables translation, every source line that has a\n        // translation keeps it directly underneath. Keeping annotations resident\n        // also prevents focus changes from reflowing the scroll geometry.''')
save(p,s)

# --- Now Playing scene: plain blur only, queue controls below fixed header ---
p='android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSNowPlayingScene.kt'
s=load(p)
s=s.replace('import com.lladlam.melox.ui.glass.meloXLiquidButton','import com.lladlam.melox.ui.glass.meloXBackdropBlur')
s=rep(s,
'''                    .graphicsLayer {\n                        alpha = queueAlpha\n                        translationY = queueOffset.toPx()\n                        scaleX = queueScale\n                        scaleY = queueScale\n                    },''',
'''                    .graphicsLayer {\n                        alpha = queueAlpha\n                        translationY = queueOffset.toPx()\n                        scaleX = queueScale\n                        scaleY = queueScale\n                    }\n                    .padding(top = 80.dp),''',
'queue header clearance')
s=rep(s,
'''                            Modifier\n                                .clip(songHeaderShape)\n                                .meloXLiquidButton(\n                                    shape = songHeaderShape,\n                                    tint = Color.White.copy(alpha = .035f),\n                                    surfaceColor = Color.Black.copy(alpha = .10f),\n                                    blurRadius = 20.dp,\n                                    lensRadius = 14.dp,\n                                    refractionHeight = 18.dp,\n                                )''',
'''                            Modifier\n                                .clip(songHeaderShape)\n                                .meloXBackdropBlur(\n                                    shape = songHeaderShape,\n                                    blurRadius = 20.dp,\n                                    surfaceColor = Color.Black.copy(alpha = .10f),\n                                )''',
'queue song header blur')
s=rep(s,
'''                Modifier\n                    .fillMaxWidth()\n                    .clip(controlsShape)\n                    .meloXLiquidButton(\n                        shape = controlsShape,\n                        tint = Color.White.copy(alpha = .035f),\n                        surfaceColor = Color.Black.copy(alpha = .10f),\n                        blurRadius = 20.dp,\n                        lensRadius = 14.dp,\n                        refractionHeight = 18.dp,\n                    )''',
'''                Modifier\n                    .fillMaxWidth()\n                    .clip(controlsShape)\n                    .meloXBackdropBlur(\n                        shape = controlsShape,\n                        blurRadius = 20.dp,\n                        surfaceColor = Color.Black.copy(alpha = .10f),\n                    )''',
'lyrics controls blur')
save(p,s)

# --- queue fixed information/mode surfaces are blur-only, never Liquid Glass ---
p='android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXQueuePanel.kt'
s=load(p)
s=s.replace('import com.lladlam.melox.ui.glass.meloXLiquidButton','import com.lladlam.melox.ui.glass.meloXBackdropBlur')
s=s.replace('''.meloXLiquidButton(\n                shape = shape,\n                tint = Color.White.copy(alpha = .035f),\n                surfaceColor = Color.Black.copy(alpha = .10f),\n                blurRadius = 20.dp,\n                lensRadius = 14.dp,\n                refractionHeight = 18.dp,\n            )''','''.meloXBackdropBlur(\n                shape = shape,\n                blurRadius = 20.dp,\n                surfaceColor = Color.Black.copy(alpha = .10f),\n            )''',1)
s=s.replace('''.meloXLiquidButton(\n                shape = shape,\n                tint = Color.White.copy(alpha = .035f),\n                surfaceColor = Color.Black.copy(alpha = .095f),\n                blurRadius = 20.dp,\n                lensRadius = 14.dp,\n                refractionHeight = 18.dp,\n            )''','''.meloXBackdropBlur(\n                shape = shape,\n                blurRadius = 20.dp,\n                surfaceColor = Color.Black.copy(alpha = .095f),\n            )''',1)
save(p,s)

# --- shared artwork destination: controls were already excluded from the Box; do not subtract them twice ---
p='android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSNowPlayingSharedHost.kt'
s=load(p)
s=rep(s,
'''            val artworkFooterHeight = 78.dp + MeloXNowPlayingControlsHeight.dp''',
'''            // The Box already excludes MeloXNowPlayingControlsHeight via the\n            // Spacer below it. Subtracting controls here again pushed artwork far\n            // too high and separated it from the metadata placeholder.\n            val artworkFooterHeight = 78.dp''',
'artwork footer double subtraction')
save(p,s)

# --- compact MiniPlayer: keep pointer hitboxes in real layout, away from shared overlay ---
p='android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSMiniPlayer.kt'
s=load(p)
outer='''.padding(horizontal = 16.dp, vertical = 3.dp)\n            .pointerInput(state.mediaId) {\n                detectHorizontalDragGestures(\n                    onDragStart = { accumulatedDrag = 0f },\n                    onHorizontalDrag = { _, dragAmount -> accumulatedDrag += dragAmount },\n                    onDragEnd = {\n                        when {\n                            accumulatedDrag <= -48f -> state.next()\n                            accumulatedDrag >= 48f -> state.previous()\n                        }\n                        accumulatedDrag = 0f\n                    },\n                    onDragCancel = { accumulatedDrag = 0f },\n                )\n            },'''
s=rep(s,outer,'''.padding(horizontal = 16.dp, vertical = 3.dp),''','remove global mini drag')
left='''.weight(1f)\n                    .clickable(onClick = onExpand),'''
left_new='''.weight(1f)\n                    .pointerInput(state.mediaId) {\n                        detectHorizontalDragGestures(\n                            onDragStart = { accumulatedDrag = 0f },\n                            onHorizontalDrag = { _, dragAmount -> accumulatedDrag += dragAmount },\n                            onDragEnd = {\n                                when {\n                                    accumulatedDrag <= -48f -> state.next()\n                                    accumulatedDrag >= 48f -> state.previous()\n                                }\n                                accumulatedDrag = 0f\n                            },\n                            onDragCancel = { accumulatedDrag = 0f },\n                        )\n                    }\n                    .clickable(onClick = onExpand),'''
s=rep(s,left,left_new,'mini drag on identity area')
s=s.replace('''.align(Alignment.CenterStart)\n                        .then(chromeOverlayModifier),''','''.align(Alignment.CenterStart),''',1)
s=s.replace('''.align(Alignment.CenterEnd)\n                        .then(chromeOverlayModifier),''','''.align(Alignment.CenterEnd),''',1)
save(p,s)

# Self-remove construction files before committing.
Path('.github/scripts/apply_player_blur_translation_fix.py').unlink(missing_ok=True)
Path('.github/workflows/apply-player-blur-translation-fix.yml').unlink(missing_ok=True)
