from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f'pattern not found in {path}: {old!r}')
    p.write_text(text.replace(old, new, 1))


# Keep the selected Now Playing sub-page stable across media-item transitions.
host = 'android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSNowPlayingSharedHost.kt'
replace_once(
    host,
    '    var page by remember(state.mediaId) { mutableStateOf(MeloXNowPlayingPage.Artwork) }\n',
    '    var page by remember { mutableStateOf(MeloXNowPlayingPage.Artwork) }\n',
)
replace_once(
    host,
    '''    var transitionSourcePage by remember(state.mediaId) {
        mutableStateOf(MeloXNowPlayingPage.Artwork)
    }
''',
    '''    var transitionSourcePage by remember {
        mutableStateOf(MeloXNowPlayingPage.Artwork)
    }
''',
)

# When online playback has actually resolved another quality, prefer that value
# over the existence of a local download when rendering the quality chip.
controls = 'android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXNowPlayingCoreControls.kt'
replace_once(
    controls,
    '    val displayQuality = downloadedQuality ?: actual ?: selected\n',
    '    val displayQuality = actual ?: downloadedQuality ?: selected\n',
)

# The expanded bottom chrome is 119dp plus system navigation inset. 146dp was
# only barely enough and left the final download rows trapped underneath the
# fixed MiniPlayer/Dock. Give every Downloads sub-page a real scroll-safe tail.
library = 'android/app/src/main/kotlin/com/lladlam/melox/ui/library/LibraryScreen.kt'
p = Path(library)
text = p.read_text()
marker = 'private enum class MeloXDownloadsPage { Root, Active, Playlists, PlaylistDetail }\n'
if marker not in text:
    raise SystemExit('downloads page marker not found')
if 'private val MeloXDownloadsBottomSafeArea' not in text:
    text = text.replace(
        marker,
        marker + 'private val MeloXDownloadsBottomSafeArea = 196.dp\n',
        1,
    )
count = text.count('bottom = 146.dp')
if count < 4:
    raise SystemExit(f'expected at least 4 download paddings, found {count}')
text = text.replace('bottom = 146.dp', 'bottom = MeloXDownloadsBottomSafeArea')
p.write_text(text)

print('v3c patch applied')
