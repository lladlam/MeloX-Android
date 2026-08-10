from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text()

def write(path, text):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text)

def replace_once(path, old, new):
    text = read(path)
    if old not in text:
        raise RuntimeError(f"pattern not found in {path}: {old[:120]!r}")
    write(path, text.replace(old, new, 1))

# 1) Make the player a real modal input layer and move Back handling into the player host.
app = "android/app/src/main/kotlin/com/lladlam/melox/ui/MeloXApp.kt"
text = read(app)
text = text.replace("import androidx.activity.compose.BackHandler\n", "")
if "import androidx.compose.ui.zIndex\n" not in text:
    text = text.replace("import androidx.compose.ui.unit.sp\n", "import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.zIndex\n")
text = text.replace(
'''                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {''',
'''                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Explicit z-order is important here: this transparent hit-test
                        // shield must sit above Scaffold/BottomChrome but below NowPlaying.
                        .zIndex(10f)
                        .pointerInput(fullPlayerVisible) {''')
text = text.replace(
'''                modifier = Modifier.fillMaxSize(),
            ) {
                MeloXIOSNowPlayingSharedHost(''',
'''                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(20f),
            ) {
                MeloXIOSNowPlayingSharedHost(''')
text = text.replace(
'''
            BackHandler(enabled = fullPlayerVisible && !showNeteaseLogin) {
                closePlayer()
            }
''', "\n")
write(app, text)

host = "android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSNowPlayingSharedHost.kt"
text = read(host)
if "import androidx.activity.compose.BackHandler\n" not in text:
    text = text.replace("package com.lladlam.melox.ui.player\n\n", "package com.lladlam.melox.ui.player\n\nimport androidx.activity.compose.BackHandler\n")
text = text.replace(
'''    var showActions by remember(state.mediaId) { mutableStateOf(false) }
    var gestureCollapseProgress''',
'''    var showActions by remember(state.mediaId) { mutableStateOf(false) }
    var showQuality by remember(state.mediaId) { mutableStateOf(false) }
    var gestureCollapseProgress''')
text = text.replace(
'''    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),''',
'''    // NowPlaying owns the player-level Back handler. Child modal overlays are
    // composed later and temporarily disable this handler, so Back always unwinds
    // the topmost visual layer before the player itself is dismissed.
    BackHandler(enabled = !showActions && !showQuality) {
        onDismiss()
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),''', 1)
text = text.replace(
'''                            onShowActions = { showActions = true },
                            grabberDragModifier''',
'''                            onShowActions = {
                                showQuality = false
                                showActions = true
                            },
                            onShowQuality = {
                                showActions = false
                                showQuality = true
                            },
                            grabberDragModifier''')
text = text.replace(
'''            MeloXNowPlayingActionsSheet(
                state = state,
                visible = showActions,
                onDismiss = { showActions = false },
            )
        }
    }
}''',
'''            MeloXNowPlayingActionsSheet(
                state = state,
                visible = showActions,
                onDismiss = { showActions = false },
            )
            MeloXQualitySelectionOverlay(
                state = state,
                visible = showQuality,
                onDismiss = { showQuality = false },
            )
        }
    }
}''')
write(host, text)

scene = "android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSNowPlayingScene.kt"
text = read(scene)
text = text.replace(
'''    onPageChanged: (MeloXNowPlayingPage) -> Unit,
    onShowActions: () -> Unit,
    grabberDragModifier''',
'''    onPageChanged: (MeloXNowPlayingPage) -> Unit,
    onShowActions: () -> Unit,
    onShowQuality: () -> Unit,
    grabberDragModifier''')
text = text.replace(
'''        MeloXNowPlayingCoreControls(
            state = state,
            page = page,
            onPageSelected = { destination ->''',
'''        MeloXNowPlayingCoreControls(
            state = state,
            page = page,
            onShowQuality = onShowQuality,
            onPageSelected = { destination ->''')
# Keep the non-artwork placeholder geometry identical to the real shared artwork.
text = text.replace("minOf(maxWidth + 16.dp, maxHeight - 92.dp)", "minOf(maxWidth, maxHeight - 92.dp)")
write(scene, text)

# 2) Replace the system DropdownMenu quality picker with a same-window glass overlay.
core = "android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXNowPlayingCoreControls.kt"
text = read(core)
text = text.replace("import androidx.compose.material3.DropdownMenu\n", "")
text = text.replace("import androidx.compose.material3.DropdownMenuItem\n", "")
text = text.replace("import com.lladlam.melox.core.audio.NeteaseQualityClient\n", "")
text = text.replace("import com.lladlam.melox.core.audio.SongAudioAvailability\n", "")
text = text.replace(
'''internal fun MeloXNowPlayingCoreControls(
    state: MeloXPlaybackUiState,
    page: MeloXNowPlayingPage,
    onPageSelected: (MeloXNowPlayingPage) -> Unit,
) {''',
'''internal fun MeloXNowPlayingCoreControls(
    state: MeloXPlaybackUiState,
    page: MeloXNowPlayingPage,
    onPageSelected: (MeloXNowPlayingPage) -> Unit,
    onShowQuality: () -> Unit,
) {''')
text = text.replace("        SceneProgressControl(state)\n", "        SceneProgressControl(state, onShowQuality)\n")
text = text.replace(
'''private fun SceneProgressControl(state: MeloXPlaybackUiState) {''',
'''private fun SceneProgressControl(
    state: MeloXPlaybackUiState,
    onShowQuality: () -> Unit,
) {''')
text = text.replace(
'''            SceneQualityChip(
                state = state,
                modifier = Modifier.align(Alignment.Center),
            )''',
'''            SceneQualityChip(
                state = state,
                onShowQuality = onShowQuality,
                modifier = Modifier.align(Alignment.Center),
            )''')
text = text.replace(
'''private fun SceneQualityChip(
    state: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val qualityClient = remember(context) {
        NeteaseQualityClient(
            cookieProvider = { NeteaseSessionStore.readCookie(context) },
        )
    }
    var expanded by remember { mutableStateOf(false) }
    var selected by remember(context) {
        mutableStateOf(
            MusicQualityPreferences.read(context).also { MusicQualityRuntime.selected = it },
        )
    }
    var actual by remember(state.mediaId) {
        mutableStateOf(MusicQualityRuntime.actualFor(state.mediaId?.toLongOrNull()))
    }
    var availability by remember(state.mediaId) {
        mutableStateOf(SongAudioAvailability.Unknown)
    }

    LaunchedEffect(state.mediaId) {
        val songId = state.mediaId?.toLongOrNull() ?: return@LaunchedEffect
        availability = runCatching { qualityClient.audioAvailability(songId) }
            .getOrDefault(SongAudioAvailability.Unknown)
    }
    LaunchedEffect(state.mediaId, selected) {''',
'''private fun SceneQualityChip(
    state: MeloXPlaybackUiState,
    onShowQuality: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    var selected by remember(context) {
        mutableStateOf(
            MusicQualityPreferences.read(context).also { MusicQualityRuntime.selected = it },
        )
    }
    var actual by remember(state.mediaId) {
        mutableStateOf(MusicQualityRuntime.actualFor(state.mediaId?.toLongOrNull()))
    }

    LaunchedEffect(state.mediaId, selected) {''')
text = text.replace("                ) { expanded = true }", "                ) { onShowQuality() }")
menu_pattern = re.compile(r'''\n\s*DropdownMenu\(\n\s*expanded = expanded,\n\s*onDismissRequest = \{ expanded = false \},\n\s*\) \{.*?\n\s*\}\n\s*\}\n\}\n\n@Composable\nprivate fun SceneTransportControls''', re.S)
m = menu_pattern.search(text)
if not m:
    raise RuntimeError("quality DropdownMenu block not found")
replacement = "\n    }\n}\n\n@Composable\nprivate fun SceneTransportControls"
text = text[:m.start()] + replacement + text[m.end():]
write(core, text)

quality_overlay = r'''package com.lladlam.melox.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.MusicQualityPreferences
import com.lladlam.melox.core.audio.NeteaseQualityClient
import com.lladlam.melox.core.audio.SongAudioAvailability
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.glass.meloXLiquidButton
import kotlinx.coroutines.launch

/**
 * Same-window Liquid Glass quality chooser. Unlike Material DropdownMenu/Popup,
 * this remains in the recorded Now Playing window and therefore samples the
 * artwork-driven player scene instead of drawing an opaque platform menu.
 */
@Composable
internal fun MeloXQualitySelectionOverlay(
    state: MeloXPlaybackUiState,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val client = remember(context) {
        NeteaseQualityClient(
            cookieProvider = { NeteaseSessionStore.readCookie(context) },
        )
    }
    var selected by remember(context, visible) {
        mutableStateOf(MusicQualityPreferences.read(context))
    }
    var availability by remember(state.mediaId, visible) {
        mutableStateOf(SongAudioAvailability.Unknown)
    }
    var loading by remember(state.mediaId, visible) { mutableStateOf(false) }

    LaunchedEffect(visible, state.mediaId) {
        if (!visible) return@LaunchedEffect
        val songId = state.mediaId?.toLongOrNull() ?: return@LaunchedEffect
        loading = true
        availability = runCatching { client.audioAvailability(songId) }
            .getOrDefault(SongAudioAvailability.Unknown)
        loading = false
    }

    BackHandler(enabled = visible, onBack = onDismiss)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = 520f)) + scaleIn(initialScale = 0.96f),
        exit = fadeOut(spring(stiffness = 620f)) + scaleOut(targetScale = 0.97f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 34.dp)
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .meloXLiquidButton(
                        shape = RoundedCornerShape(30.dp),
                        tint = Color.White.copy(alpha = 0.08f),
                        surfaceColor = Color.Black.copy(alpha = 0.12f),
                        blurRadius = 16.dp,
                        lensRadius = 20.dp,
                        refractionHeight = 24.dp,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "音质",
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            state.title.ifBlank { "正在播放" },
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White.copy(alpha = 0.78f),
                            strokeWidth = 2.dp,
                        )
                    }
                }

                MusicQuality.entries.forEach { quality ->
                    val supported = availability.supports(quality.apiLevel) != false
                    val isSelected = quality == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .meloXLiquidButton(
                                shape = RoundedCornerShape(16.dp),
                                enabled = supported,
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                } else {
                                    Color.Transparent
                                },
                                surfaceColor = if (isSelected) {
                                    Color.White.copy(alpha = 0.08f)
                                } else {
                                    Color.Transparent
                                },
                                blurRadius = 10.dp,
                                lensRadius = 10.dp,
                                refractionHeight = 14.dp,
                            )
                            .clickable(enabled = supported) {
                                selected = quality
                                scope.launch {
                                    PlaybackCommands.changeQuality(context, quality)
                                }
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = quality.title,
                            modifier = Modifier.weight(1f),
                            color = Color.White.copy(alpha = if (supported) 0.94f else 0.30f),
                            fontSize = 17.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                        if (isSelected) {
                            Text("✓", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        } else if (!supported) {
                            Text("不可用", color = Color.White.copy(alpha = 0.30f), fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
'''
write("android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXQualitySelectionOverlay.kt", quality_overlay)

# 3) Actions sheet owns Back before the full player.
actions = "android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXNowPlayingActionsSheet.kt"
text = read(actions)
if "import androidx.activity.compose.BackHandler\n" not in text:
    text = text.replace("import android.net.Uri\n", "import android.net.Uri\nimport androidx.activity.compose.BackHandler\n")
text = text.replace(
'''    var page by remember(state.mediaId) { mutableStateOf(ActionPage.Main) }

    AnimatedVisibility(''',
'''    var page by remember(state.mediaId) { mutableStateOf(ActionPage.Main) }

    BackHandler(enabled = visible) {
        if (page != ActionPage.Main) {
            page = ActionPage.Main
        } else {
            onDismiss()
        }
    }

    AnimatedVisibility(''')
write(actions, text)

# 4) Personalized playlist responses use picUrl rather than coverImgUrl.
client = "android/app/src/main/kotlin/com/lladlam/melox/core/library/NeteaseLibraryClient.kt"
text = read(client)
old = '''            coverUrl = value.optString("coverImgUrl")
                .takeIf(String::isNotBlank)
                ?.let(::secureUrl),'''
new = '''            coverUrl = sequenceOf(
                value.optString("coverImgUrl"),
                value.optString("picUrl"),
                value.optString("coverUrl"),
            )
                .firstOrNull(String::isNotBlank)
                ?.let(::secureUrl),'''
if old not in text:
    raise RuntimeError("playlist cover parser pattern not found")
text = text.replace(old, new, 1)
write(client, text)

# Self-delete so the next push does not leave automation scaffolding in the branch.
(ROOT / "tools/one_shot_foundation_patch.py").unlink(missing_ok=True)
(ROOT / ".github/workflows/one-shot-foundation-patch.yml").unlink(missing_ok=True)
