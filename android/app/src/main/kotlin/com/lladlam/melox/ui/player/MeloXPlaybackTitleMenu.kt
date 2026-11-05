package com.lladlam.melox.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lladlam.melox.R
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXSearchKind
import com.lladlam.melox.core.network.MeloXSearchMediaItem
import com.lladlam.melox.core.network.NeteaseUniversalSearchClient
import com.lladlam.melox.playback.MeloXPlaybackModeRuntime
import com.lladlam.melox.playback.PlaybackTrackIdentity
import com.lladlam.melox.ui.collection.MeloXCollectionDetailActivity
import com.lladlam.melox.ui.glass.MeloXGlassCard
import com.lladlam.melox.ui.glass.MeloXGlassMaterial
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import kotlinx.coroutines.launch

private class MeloXTitleMenuPositionProvider(
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left.coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8))
        val below = anchorBounds.bottom + gapPx
        val y = if (below + popupContentSize.height <= windowSize.height - 8) {
            below
        } else {
            (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(8)
        }
        return IntOffset(x, y)
    }
}

/**
 * Tap on the now-playing title opens the iOS-style liquid-glass menu:
 * artist / album / current queue. The three destinations use the original
 * NetEase ids (resolved lazily) instead of search strings.
 */
@Composable
internal fun MeloXPlaybackTitleMenu(
    state: MeloXPlaybackUiState,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 20.sp,
    titleLineHeight: TextUnit = 24.sp,
    artistSize: TextUnit = 18.sp,
    artistLineHeight: TextUnit = 22.sp,
    showHeartModeLabel: Boolean = false,
    horizontalPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val client = remember(app) {
        NeteaseUniversalSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(app) })
    }
    var expanded by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf<IntRect?>(null) }
    var detail by remember(state.mediaId) { mutableStateOf<SearchSong?>(null) }
    var resolving by remember(state.mediaId) { mutableStateOf(false) }
    var failed by remember(state.mediaId) { mutableStateOf(false) }
    val menuAlpha by animateFloatAsState(if (expanded) 1f else 0f, tween(150), label = "title-menu-alpha")

    val neteaseId = remember(state.mediaId) {
        PlaybackTrackIdentity.decode(state.mediaId.orEmpty())
            ?.takeIf { it.sourceId.builtIn == com.lladlam.melox.core.music.model.MusicSource.Netease }
            ?.value?.toLongOrNull()
    }

    suspend fun resolvedDetail(): SearchSong? {
        detail?.let { return it }
        val id = neteaseId ?: return null
        resolving = true
        val loaded = runCatching { client.songDetail(id) }.getOrNull()
        detail = loaded
        failed = loaded == null
        resolving = false
        return loaded
    }

    LaunchedEffect(expanded, neteaseId) {
        if (expanded && neteaseId != null && detail == null) resolvedDetail()
    }

    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates: LayoutCoordinates ->
                    anchor = coordinates.boundsInWindow().let {
                        IntRect(
                            left = it.left.toInt(),
                            top = it.top.toInt(),
                            right = it.right.toInt(),
                            bottom = it.bottom.toInt(),
                        )
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = neteaseId != null || state.queue.isNotEmpty(),
                ) { expanded = true }
                .padding(horizontal = horizontalPadding),
        ) {
            if (showHeartModeLabel && MeloXPlaybackModeRuntime.heartModeActive) {
                Text("心动模式", color = Color(0xFFFF7BA5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = if (state.isInTransition) {
                    state.incomingTitle.ifBlank { state.title.ifBlank { "正在播放" } }
                } else {
                    state.title.ifBlank { "正在播放" }
                },
                color = Color.White,
                fontSize = titleSize,
                lineHeight = titleLineHeight,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (state.isInTransition) state.incomingArtist else state.artist,
                color = Color.White.copy(alpha = .64f),
                fontSize = artistSize,
                lineHeight = artistLineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (expanded && anchor != null) {
            Popup(
                popupPositionProvider = remember(anchor) {
                    MeloXTitleMenuPositionProvider(with(density) { 10.dp.roundToPx() })
                },
                onDismissRequest = { expanded = false },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
            ) {
                MeloXGlassCard(
                    modifier = Modifier
                        .width(300.dp)
                        .graphicsLayer {
                            alpha = menuAlpha
                            scaleX = .94f + .06f * menuAlpha
                            scaleY = .94f + .06f * menuAlpha
                        },
                    shape = RoundedCornerShape(28.dp),
                    material = MeloXGlassMaterial.Clear,
                    surfaceColor = Color.Black.copy(alpha = .22f),
                ) {
                    TitleMenuRow(
                        symbol = MeloXSymbol.Microphone,
                        title = stringResource(R.string.player_go_artist_short),
                        subtitle = detail?.artists?.takeIf(String::isNotBlank) ?: state.artist,
                        enabled = neteaseId != null,
                    ) {
                        scope.launch {
                            val target = resolvedDetail() ?: return@launch
                            val artistId = target.artistId ?: return@launch
                            expanded = false
                            MeloXCollectionDetailActivity.launch(
                                context,
                                MeloXSearchMediaItem(
                                    id = artistId,
                                    kind = MeloXSearchKind.Artists,
                                    title = target.artists,
                                    artworkUrl = target.artworkUrl,
                                ),
                            )
                        }
                    }
                    TitleMenuRow(
                        symbol = MeloXSymbol.List,
                        title = stringResource(R.string.player_go_album_short),
                        subtitle = detail?.album?.takeIf(String::isNotBlank) ?: state.album,
                        enabled = neteaseId != null,
                    ) {
                        scope.launch {
                            val target = resolvedDetail() ?: return@launch
                            val albumId = target.albumId ?: return@launch
                            expanded = false
                            MeloXCollectionDetailActivity.launch(
                                context,
                                MeloXSearchMediaItem(
                                    id = albumId,
                                    kind = MeloXSearchKind.Albums,
                                    title = target.album,
                                    subtitle = target.artists,
                                    artworkUrl = target.artworkUrl,
                                ),
                            )
                        }
                    }
                    TitleMenuRow(
                        symbol = MeloXSymbol.Queue,
                        title = stringResource(R.string.player_go_playlist_short),
                        subtitle = stringResource(R.string.player_queue_count, state.queue.size),
                        enabled = true,
                    ) {
                        expanded = false
                        onOpenQueue()
                    }
                    if (failed && !resolving) {
                        Text(
                            stringResource(R.string.player_title_menu_failed),
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            color = Color.White.copy(alpha = .48f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleMenuRow(
    symbol: MeloXSymbol,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MeloXSymbolIcon(
            symbol,
            Modifier.size(22.dp),
            if (enabled) Color.White.copy(alpha = .9f) else Color.White.copy(alpha = .35f),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) Color.White else Color.White.copy(alpha = .4f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = .48f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text("›", color = Color.White.copy(alpha = .38f), fontSize = 20.sp)
    }
}
