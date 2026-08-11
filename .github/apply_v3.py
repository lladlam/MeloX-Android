from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    s = p.read_text()
    if old not in s:
        raise SystemExit(f"pattern not found in {path}: {old[:160]!r}")
    p.write_text(s.replace(old, new, 1))


def replace_between(path: str, start_marker: str, end_marker: str, replacement: str) -> None:
    p = Path(path)
    s = p.read_text()
    start = s.index(start_marker)
    end = s.index(end_marker, start)
    p.write_text(s[:start] + replacement + s[end:])


# ---------------------------------------------------------------------------
# 1. AutoMix: never recreate AudioTrack at the audible handoff, and update the
#    crossfade gain at frame cadence rather than 100 ms steps.
# ---------------------------------------------------------------------------
service = 'android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXPlaybackService.kt'
replace_once(
    service,
    'import android.content.Intent\n',
    'import android.content.Intent\nimport android.media.AudioFocusRequest\nimport android.media.AudioManager\n',
)
replace_once(
    service,
    '    private lateinit var downloadStore: MeloXDownloadStore\n',
    '''    private lateinit var downloadStore: MeloXDownloadStore
    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest
    private var resumeAfterFocusGain = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeAfterFocusGain = false
                player?.pause()
                incomingPlayer?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeAfterFocusGain = player?.playWhenReady == true || incomingPlayer?.playWhenReady == true
                player?.pause()
                incomingPlayer?.pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeAfterFocusGain) {
                    player?.play()
                    if (mixStartedAt > 0L) incomingPlayer?.play()
                    resumeAfterFocusGain = false
                }
            }
        }
    }
''',
)
replace_once(
    service,
    '''        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "isPlaying=$isPlaying, ongoing=${isPlaybackOngoing()}")
        }
''',
    '''        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "isPlaying=$isPlaying, ongoing=${isPlaybackOngoing()}")
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                requestPlaybackAudioFocus()
            } else if (incomingPlayer?.playWhenReady != true) {
                abandonPlaybackAudioFocus()
            }
        }
''',
)
replace_once(
    service,
    '''    private val modeMonitor = object : Runnable {
        override fun run() {
            val active = player
            if (active != null) {
                applyLocalArtworkMetadata(active)
                PlaybackCommands.prioritizeManualQueue(active)
                maybePrepareAutoplay(active)
                maybeRunAutoMix(active)
            }
            handler.postDelayed(this, 100L)
        }
    }
''',
    '''    private val modeMonitor = object : Runnable {
        override fun run() {
            val active = player
            if (active != null) {
                // Metadata/queue work does not belong in the 60 Hz crossfade loop.
                if (mixStartedAt == 0L) {
                    applyLocalArtworkMetadata(active)
                    PlaybackCommands.prioritizeManualQueue(active)
                    maybePrepareAutoplay(active)
                }
                maybeRunAutoMix(active)
            }
            handler.postDelayed(this, if (mixStartedAt > 0L) AUTOMIX_FRAME_MS else MODE_MONITOR_MS)
        }
    }
''',
)
replace_once(
    service,
    '''    override fun onCreate() {
        super.onCreate()
        val httpFactory = DefaultHttpDataSource.Factory()
''',
    '''    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        val platformAudioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(platformAudioAttributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()

        val httpFactory = DefaultHttpDataSource.Factory()
''',
)
replace_once(
    service,
    '''            .apply {
                setAudioAttributes(audioAttributes, managesAudioFocus)
                setHandleAudioBecomingNoisy(managesAudioFocus)
                if (observesSession) addListener(playerListener)
            }
''',
    '''            .apply {
                // All decks use identical attributes before playback starts. Audio
                // focus is owned by the service, so promoting the incoming deck does
                // not need setAudioAttributes() while it is already audible.
                setAudioAttributes(audioAttributes, false)
                setHandleAudioBecomingNoisy(observesSession)
                if (observesSession) addListener(playerListener)
            }
''',
)
replace_once(
    service,
    '        PlaybackCommands.prioritizeManualQueue(active)\n        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return\n',
    '        if (mixStartedAt == 0L) PlaybackCommands.prioritizeManualQueue(active)\n        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return\n',
)
replace_once(
    service,
    '''        incoming.volume = mixBaseVolume
        incoming.setAudioAttributes(audioAttributes, true)
        incoming.setHandleAudioBecomingNoisy(true)
''',
    '''        incoming.volume = mixBaseVolume
        // Do NOT change AudioAttributes here. Media3 may recreate AudioTrack when
        // attributes change during playback, which is an audible handoff gap.
        incoming.setHandleAudioBecomingNoisy(true)
''',
)
replace_once(
    service,
    '''    private fun cancelPreparedMix() {
''',
    '''    private fun requestPlaybackAudioFocus(): Boolean {
        if (!::audioManager.isInitialized || !::audioFocusRequest.isInitialized) return true
        return audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonPlaybackAudioFocus() {
        if (::audioManager.isInitialized && ::audioFocusRequest.isInitialized) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
        }
    }

    private fun cancelPreparedMix() {
''',
)
replace_once(
    service,
    '''        player?.release()
        player = null
        super.onDestroy()
''',
    '''        player?.release()
        player = null
        abandonPlaybackAudioFocus()
        super.onDestroy()
''',
)
replace_once(
    service,
    '''        const val AUTOMIX_HANDOFF_GUARD_MS = 700L
''',
    '''        const val AUTOMIX_HANDOFF_GUARD_MS = 700L
        const val AUTOMIX_FRAME_MS = 16L
        const val MODE_MONITOR_MS = 100L
''',
)

# ---------------------------------------------------------------------------
# 2. Lyrics: keep MeloX timing/appearance, but stop recomposing the whole lyric
#    tree at display cadence and stop doing unused cascade work.
# ---------------------------------------------------------------------------
lyrics = 'android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSLyricsPanel.kt'
replace_once(
    lyrics,
    'import androidx.compose.animation.core.Animatable\n',
    'import androidx.compose.animation.core.Animatable\nimport androidx.compose.animation.core.animateFloatAsState\n',
)
replace_once(
    lyrics,
    '    const val NON_YRC_ADVANCE_MS = 200L\n',
    '    const val NON_YRC_ADVANCE_MS = 200L\n    const val PANEL_TIMELINE_TICK_MS = 100L\n',
)
replace_once(
    lyrics,
    '''            delay(if (state.isPlaying) 16L else 200L)
''',
    '''            // The page only needs line/focus timing at a coarse cadence. The
            // active YRC line owns its own 16 ms draw clock below.
            delay(if (state.isPlaying) UpstreamLyrics.PANEL_TIMELINE_TICK_MS else 200L)
''',
)
replace_once(
    lyrics,
    '''    val movementOffsets = remember(document) {
        List(lines.size) { Animatable(0f) }
    }
    val focusProgress = remember(document) {
        List(lines.size) { Animatable(0f) }
    }
    val scaleProgress = remember(document) {
        List(lines.size) { Animatable(0f) }
    }
''',
    '',
)
replace_once(
    lyrics,
    '''    fun rowContentTop(index: Int, topPaddingPx: Float): Float {
        var result = topPaddingPx
        for (i in 0 until index) {
            result += estimatedHeight(i) + lineSpacingPx
        }
        return result
    }

    fun focusedFollowingOffset(index: Int, focusIndex: Int): Float {
        if (focusIndex !in lines.indices || index <= focusIndex) return 0f
        return max(estimatedHeight(focusIndex) * (UpstreamLyrics.CURRENT_LINE_SCALE - 1f), 0f)
    }

    suspend fun handOffFocusColor(nextIndex: Int) = coroutineScope {
        focusProgress.forEachIndexed { index, anim ->
            val target = if (index == nextIndex) 1f else 0f
            if (abs(anim.value - target) > 0.0001f) {
                launch {
                    anim.animateTo(
                        targetValue = target,
                        animationSpec = tween(
                            durationMillis = UpstreamLyrics.FOCUS_COLOR_DURATION_MS,
                            easing = SourceSmoothStepEasing,
                        ),
                    )
                }
            }
        }
    }

    suspend fun handOffFocusScale(previousIndex: Int, nextIndex: Int) = coroutineScope {
        if (previousIndex in scaleProgress.indices && previousIndex != nextIndex) {
            launch {
                scaleProgress[previousIndex].animateTo(
                    0f,
                    tween(
                        durationMillis = UpstreamLyrics.SCALE_BOUNCE_DURATION_MS,
                        easing = SourceSmoothStepEasing,
                    ),
                )
            }
        }
        if (nextIndex in scaleProgress.indices) {
            launch {
                scaleProgress[nextIndex].animateTo(
                    1f,
                    tween(
                        durationMillis = UpstreamLyrics.SCALE_BOUNCE_DURATION_MS,
                        easing = SourceSpringEasing(UpstreamLyrics.SCALE_BOUNCE),
                    ),
                )
            }
        }
    }
''',
    '''    // Cache cumulative row geometry. The previous rowContentTop() walked from
    // line zero for every rendered row, making a composition O(n²).
    val rowPrefixPx = remember(
        document,
        layoutRevision,
        lineSpacingPx,
        MeloXSettingsRuntime.showLyricRomanization,
        MeloXSettingsRuntime.showLyricTranslation,
    ) {
        FloatArray(lines.size + 1).also { prefix ->
            for (index in lines.indices) {
                prefix[index + 1] = prefix[index] + estimatedHeight(index) + lineSpacingPx
            }
        }
    }

    fun rowContentTop(index: Int, topPaddingPx: Float): Float =
        topPaddingPx + rowPrefixPx[index.coerceIn(0, lines.size)]
''',
)
replace_between(
    lyrics,
    '''        LaunchedEffect(
            highlightedIndex,
''',
    '''
        when {
''',
    '''        LaunchedEffect(
            highlightedIndex,
            playbackFocusGeneration,
            viewportHeightPx,
            layoutRevision,
            document,
        ) {
            val nextIndex = highlightedIndex
            if (nextIndex !in lines.indices || viewportHeightPx <= 0) return@LaunchedEffect
            if (isBrowsingLyrics) {
                visualFocusIndex = nextIndex
                return@LaunchedEffect
            }

            val targetScroll = targetScrollFor(nextIndex)
            val previousIndex = visualFocusIndex
            visualFocusIndex = nextIndex

            if (previousIndex !in lines.indices) {
                automaticScroll = true
                scrollState.scrollTo(targetScroll)
                automaticScroll = false
                return@LaunchedEffect
            }

            if (previousIndex == nextIndex) {
                if (abs(scrollState.value - targetScroll) > 2) {
                    automaticScroll = true
                    scrollState.scrollTo(targetScroll)
                    automaticScroll = false
                }
                return@LaunchedEffect
            }

            automaticScroll = true
            scrollState.animateScrollTo(
                targetScroll,
                tween(
                    durationMillis = sourceFocusAnimationDurationMs(nextIndex, lines).coerceIn(140, 300),
                    easing = SourceSmoothStepEasing,
                ),
            )
            automaticScroll = false
        }
''',
)
replace_once(
    lyrics,
    '''                val scrollValue = scrollState.value.toFloat()
                val focusAnchorY = viewportHeightPx * UpstreamLyrics.FOCUS_POSITION
                val annotationHeightPx =
''',
    '''                val annotationHeightPx =
''',
)
replace_once(
    lyrics,
    '''                        val height = estimatedHeight(index)
                        val visualOffset = 0f
                        val frameMinY = rowContentTop(index, topPaddingPx) - scrollValue
                        val visualMidY = frameMinY + visualOffset + height * 0.5f
                        val distance = abs(visualMidY - focusAnchorY)
                        val fp = focusProgress[index].value.coerceIn(0f, 1f)
                        val effectiveFocus = fp
''',
    '''                        val height = estimatedHeight(index)
                        val visualOffset = 0f
                        // Do not read scrollState.value here. Reading it in composition
                        // forces every lyric row to recompose on every scroll frame.
                        val lineDistance = if (visualFocusIndex in lines.indices) {
                            abs(index - visualFocusIndex).toFloat()
                        } else 0f
                        val distance = lineDistance * lyricStridePx
                        val effectiveFocus = if (index == visualFocusIndex) 1f else 0f
''',
)
replace_once(
    lyrics,
    '''                        val reveal = sourceBottomRevealOpacity(
                            frameMinY = frameMinY,
                            movementOffset = visualOffset,
                            frameHeight = height,
                            viewportHeight = viewportHeightPx.toFloat(),
                        )
                        val rowAlpha = (distanceOpacity * emphasis * reveal).coerceIn(0f, 1f)
                        val scale = 1f +
                            (UpstreamLyrics.CURRENT_LINE_SCALE - 1f) * scaleProgress[index].value
''',
    '''                        val rowAlpha = (distanceOpacity * emphasis).coerceIn(0f, 1f)
                        val scale = if (index == visualFocusIndex) UpstreamLyrics.CURRENT_LINE_SCALE else 1f
''',
)
replace_once(
    lyrics,
    '''                            positionMs = renderedPositionMs,
                            timed = hasSyllableSync && index == highlightedIndex,
''',
    '''                            positionMs = if (index == highlightedIndex) renderedPositionMs else line.timeMs,
                            isPlaying = state.isPlaying,
                            timed = hasSyllableSync && index == highlightedIndex,
''',
)
replace_once(
    lyrics,
    '''    positionMs: Long,
    timed: Boolean,
''',
    '''    positionMs: Long,
    isPlaying: Boolean,
    timed: Boolean,
''',
)
replace_once(
    lyrics,
    '''    val blurModifier = Modifier
        .blur(
            radius = max(distanceBlurDp, 0f).dp,
            edgeTreatment = BlurredEdgeTreatment.Unbounded,
        )
        .blur(
            radius = max(focusBlurDp, 0f).dp,
            edgeTreatment = BlurredEdgeTreatment.Unbounded,
        )
''',
    '''    val animatedScale by animateFloatAsState(
        targetValue = visualScale,
        animationSpec = tween(220, easing = SourceSmoothStepEasing),
        label = "lyric-row-scale-${line.timeMs}",
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = rowAlpha,
        animationSpec = tween(120, easing = SourceSmoothStepEasing),
        label = "lyric-row-alpha-${line.timeMs}",
    )
    val targetBlur = (max(distanceBlurDp, 0f) + max(focusBlurDp, 0f)).coerceAtMost(10f)
    val animatedBlur by animateFloatAsState(
        targetValue = targetBlur,
        animationSpec = tween(120, easing = SourceSmoothStepEasing),
        label = "lyric-row-blur-${line.timeMs}",
    )
    val blurModifier = if (animatedBlur > 0.05f) {
        Modifier.blur(
            radius = animatedBlur.dp,
            edgeTreatment = BlurredEdgeTreatment.Unbounded,
        )
    } else Modifier
''',
)
replace_once(
    lyrics,
    '''                scaleX = visualScale
                scaleY = visualScale
                alpha = rowAlpha
''',
    '''                scaleX = animatedScale
                scaleY = animatedScale
                alpha = animatedAlpha
''',
)
replace_once(
    lyrics,
    '''            playbackTimeMs = positionMs,
            timed = timed && line.syllables.isNotEmpty(),
''',
    '''            playbackTimeMs = positionMs,
            isPlaying = isPlaying,
            timed = timed && line.syllables.isNotEmpty(),
''',
)
replace_once(
    lyrics,
    '''    playbackTimeMs: Long,
    timed: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
''',
    '''    playbackTimeMs: Long,
    isPlaying: Boolean,
    timed: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    var frameAnchorMs by remember(line.timeMs) { mutableLongStateOf(playbackTimeMs) }
    var frameAnchorRealtimeMs by remember(line.timeMs) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var framePlaybackTimeMs by remember(line.timeMs) { mutableLongStateOf(playbackTimeMs) }

    LaunchedEffect(playbackTimeMs, timed, isPlaying) {
        frameAnchorMs = playbackTimeMs
        frameAnchorRealtimeMs = SystemClock.elapsedRealtime()
        framePlaybackTimeMs = playbackTimeMs
    }
    LaunchedEffect(timed, isPlaying, line.timeMs) {
        while (timed && isPlaying) {
            framePlaybackTimeMs = frameAnchorMs + (SystemClock.elapsedRealtime() - frameAnchorRealtimeMs)
            delay(16L)
        }
    }
    val drawPlaybackTimeMs = if (timed && isPlaying) framePlaybackTimeMs else playbackTimeMs
''',
)
replace_once(
    lyrics,
    '''        val visuals = remember(line, playbackTimeMs, timed, density.density) {
            if (timed) sourceGlyphVisuals(line, playbackTimeMs, density.density)
''',
    '''        val visuals = remember(line, drawPlaybackTimeMs, timed, density.density) {
            if (timed) sourceGlyphVisuals(line, drawPlaybackTimeMs, density.density)
''',
)
replace_once(
    lyrics,
    '''            // Render each timed character by clipping the *normally shaped full
            // TextLayoutResult*. This preserves fallback glyphs. getPathForRange()
            // is a selection/range enclosure path, not a glyph-outline API.
            for (offset in line.text.indices) {
''',
    '''            // Draw the unplayed layer once. The previous renderer redrew this
            // full layout once for every character.
            drawText(layout, color = Color.White.copy(alpha = UpstreamLyrics.UNPLAYED_OPACITY))

            // Render each timed character by clipping the normally-shaped layout.
            for (offset in line.text.indices) {
''',
)
replace_once(
    lyrics,
    '''                        // Upstream draws a complete unplayed layer first.
                        drawText(
                            layout,
                            color = Color.White.copy(alpha = UpstreamLyrics.UNPLAYED_OPACITY),
                        )

                        val reveal = fx.reveal.coerceIn(0f, 1f)
''',
    '''                        val reveal = fx.reveal.coerceIn(0f, 1f)
''',
)
replace_once(lyrics, '                            val stopCount = 8\n', '                            val stopCount = 3\n')
replace_once(
    lyrics,
    '''                        if (fx.glow > 0.001f) {
                            drawRevealed((fx.glow * .10f).coerceIn(0f, .24f))
                            drawRevealed((fx.glow * .18f).coerceIn(0f, .36f))
                        }
''',
    '''                        if (fx.glow > 0.001f) {
                            drawRevealed((fx.glow * .18f).coerceIn(0f, .36f))
                        }
''',
)

# ---------------------------------------------------------------------------
# 3. Full bottom chrome blur + blank-area touch shield in both full player and
#    library/list pages. Controls themselves stay interactive above the shield.
# ---------------------------------------------------------------------------
scene = 'android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSNowPlayingScene.kt'
replace_once(
    scene,
    '''            Box(modifier = controlsSurface) {
                MeloXNowPlayingCoreControls(
''',
    '''            Box(
                modifier = controlsSurface.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            ) {
                MeloXNowPlayingCoreControls(
''',
)

app = 'android/app/src/main/kotlin/com/lladlam/melox/ui/MeloXApp.kt'
replace_once(
    app,
    'import com.lladlam.melox.ui.glass.meloXLiquidBottomBar\n',
    'import com.lladlam.melox.ui.glass.meloXBackdropBlur\nimport com.lladlam.melox.ui.glass.meloXLiquidBottomBar\n',
)
replace_once(
    app,
    '''        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(chromeHeight),
        ) {
''',
    '''        val chromeShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(chromeHeight)
                .clip(chromeShape)
                .meloXBackdropBlur(
                    shape = chromeShape,
                    blurRadius = 28.dp,
                    surfaceColor = bottomGlassFallbackColor().copy(alpha = 0.10f),
                )
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
''',
)

# ---------------------------------------------------------------------------
# 4/5. Downloads: visible delete action in the selection toolbar, and always show
#      the downloaded-playlists entry (empty or not).
# ---------------------------------------------------------------------------
library = 'android/app/src/main/kotlin/com/lladlam/melox/ui/library/LibraryScreen.kt'
replace_once(
    library,
    '''                  if (selecting) {
                      Text(
                          if (selectedIds.size == completed.size) "取消全选" else "全选",
''',
    '''                  if (selecting) {
                      val canDeleteSelection = selectedIds.isNotEmpty()
                      Text(
                          if (canDeleteSelection) "删除(${selectedIds.size})" else "删除",
                          color = MaterialTheme.colorScheme.error.copy(alpha = if (canDeleteSelection) 1f else .35f),
                          fontWeight = FontWeight.SemiBold,
                          modifier = Modifier.clickable(enabled = canDeleteSelection) {
                              downloads.removeMany(selectedIds)
                              selectedIds = emptySet()
                              selecting = false
                          },
                      )
                      Text(
                          if (selectedIds.size == completed.size) "取消全选" else "全选",
''',
)
replace_once(
    library,
    '''  if (groups.isNotEmpty()) {
      item {
          DownloadNavigationCard(
              title = "已下载歌单",
              subtitle = "${groups.size} 个歌单",
              onClick = { page = MeloXDownloadsPage.Playlists },
          )
      }
  }
''',
    '''  item {
      DownloadNavigationCard(
          title = "已下载歌单",
          subtitle = if (groups.isEmpty()) "暂无已下载歌单" else "${groups.size} 个歌单",
          onClick = { page = MeloXDownloadsPage.Playlists },
      )
  }
''',
)
replace_once(
    library,
    '''  items(groups, key = { "download-playlist-${it.playlist.id}" }) { group ->
''',
    '''  if (groups.isEmpty()) {
      item {
          Text(
              "暂无已下载歌单",
              color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f),
              modifier = Modifier.padding(top = 24.dp),
          )
      }
  }
  items(groups, key = { "download-playlist-${it.playlist.id}" }) { group ->
''',
)

print('v3 patch applied')
