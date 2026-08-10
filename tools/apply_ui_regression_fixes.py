from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str):
    text = path.read_text()
    if old not in text:
        raise SystemExit(f"pattern not found in {path}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1))

# 1) Settings root scroll position must survive navigating into a detail page.
settings = ROOT / "android/app/src/main/kotlin/com/lladlam/melox/ui/settings/SettingsScreen.kt"
replace_once(
    settings,
    '    var search by remember { mutableStateOf("") }\n',
    '    var search by remember { mutableStateOf("") }\n'
    '    // Keep the root ScrollState alive while a detail route is displayed.\n'
    '    // Creating it inside the root-only branch reset Settings to y=0 on Back.\n'
    '    val rootScrollState = rememberScrollState()\n',
)
replace_once(
    settings,
    '            .verticalScroll(rememberScrollState())\n',
    '            .verticalScroll(rootScrollState)\n',
)

# 2) Hoist Library modal state to MeloXApp so same-window Liquid Glass sheets
# render above BottomChrome instead of being occluded by MiniPlayer/tab bar.
app = ROOT / "android/app/src/main/kotlin/com/lladlam/melox/ui/MeloXApp.kt"
replace_once(
    app,
    '    var scrollAccumulator by remember { mutableFloatStateOf(0f) }\n',
    '    var scrollAccumulator by remember { mutableFloatStateOf(0f) }\n'
    '    var libraryModalVisible by remember { mutableStateOf(false) }\n',
)
replace_once(
    app,
    '    LaunchedEffect(selectedTab) {\n        tabBarMinimized = false\n        scrollAccumulator = 0f\n    }\n',
    '    LaunchedEffect(selectedTab) {\n'
    '        tabBarMinimized = false\n'
    '        scrollAccumulator = 0f\n'
    '        if (selectedTab != AppTab.Library) libraryModalVisible = false\n'
    '    }\n',
)
replace_once(
    app,
    '                modifier = Modifier\n                    .fillMaxSize()\n                    .nestedScroll(tabBarMinimizeConnection)\n                    .layerBackdrop(bottomChromeBackdrop),\n',
    '                modifier = Modifier\n'
    '                    .fillMaxSize()\n'
    '                    .nestedScroll(tabBarMinimizeConnection)\n'
    '                    .layerBackdrop(bottomChromeBackdrop)\n'
    '                    // Library action sheets deliberately stay in the same Compose\n'
    '                    // window so Liquid Glass can sample the collection behind them.\n'
    '                    // Raise the whole screen while one is open; BottomChrome must\n'
    '                    // never paint over the modal scrim/sheet.\n'
    '                    .zIndex(if (libraryModalVisible && selectedTab == AppTab.Library && !fullPlayerVisible) 15f else 0f),\n',
)
replace_once(
    app,
    '                        AppTab.Library -> LibraryScreen(\n                            session = neteaseSession,\n                            playlistBackEnabled = !fullPlayerVisible,\n',
    '                        AppTab.Library -> LibraryScreen(\n'
    '                            session = neteaseSession,\n'
    '                            playlistBackEnabled = !fullPlayerVisible && !libraryModalVisible,\n'
    '                            onModalVisibilityChanged = { libraryModalVisible = it },\n',
)

library = ROOT / "android/app/src/main/kotlin/com/lladlam/melox/ui/library/LibraryScreen.kt"
replace_once(
    library,
    'import androidx.compose.runtime.Composable\n',
    'import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.DisposableEffect\n',
)
replace_once(
    library,
    'fun LibraryScreen(\n    session: NeteaseSessionStore,\n    onLogin: () -> Unit,\n    playlistBackEnabled: Boolean = true,\n) {\n',
    'fun LibraryScreen(\n'
    '    session: NeteaseSessionStore,\n'
    '    onLogin: () -> Unit,\n'
    '    playlistBackEnabled: Boolean = true,\n'
    '    onModalVisibilityChanged: (Boolean) -> Unit = {},\n'
    ') {\n',
)
replace_once(
    library,
    '                    animatedVisibilityScope = playlistTransitionVisibilityScope,\n                )\n',
    '                    animatedVisibilityScope = playlistTransitionVisibilityScope,\n'
    '                    onModalVisibilityChanged = onModalVisibilityChanged,\n'
    '                )\n',
)
replace_once(
    library,
    'private fun MeloXPlaylistDetailScreen(\n    initialPlaylist: NeteasePlaylistSummary,\n    client: NeteaseLibraryClient,\n    onBack: () -> Unit,\n    sharedTransitionScope: SharedTransitionScope,\n    animatedVisibilityScope: AnimatedVisibilityScope,\n) {\n',
    'private fun MeloXPlaylistDetailScreen(\n'
    '    initialPlaylist: NeteasePlaylistSummary,\n'
    '    client: NeteaseLibraryClient,\n'
    '    onBack: () -> Unit,\n'
    '    sharedTransitionScope: SharedTransitionScope,\n'
    '    animatedVisibilityScope: AnimatedVisibilityScope,\n'
    '    onModalVisibilityChanged: (Boolean) -> Unit,\n'
    ') {\n',
)
replace_once(
    library,
    '    var palette by remember(initialPlaylist.coverUrl) { mutableStateOf(MeloXDetailPalette.LightFallback) }\n\n    suspend fun refreshSavedState() {\n',
    '    var palette by remember(initialPlaylist.coverUrl) { mutableStateOf(MeloXDetailPalette.LightFallback) }\n\n'
    '    DisposableEffect(showPlaylistActions, selectedTrackAction) {\n'
    '        val visible = showPlaylistActions || selectedTrackAction != null\n'
    '        onModalVisibilityChanged(visible)\n'
    '        onDispose {\n'
    '            if (visible) onModalVisibilityChanged(false)\n'
    '        }\n'
    '    }\n\n'
    '    suspend fun refreshSavedState() {\n',
)

# Self-delete construction files so the final branch contains only product code.
for relative in [
    "tools/apply_ui_regression_fixes.py",
    ".github/workflows/apply-ui-regression-fixes.yml",
]:
    target = ROOT / relative
    if target.exists():
        target.unlink()
