from pathlib import Path
root=Path(__file__).resolve().parents[1]
def read(p): return (root/p).read_text()
def write(p,t): (root/p).write_text(t)

# Upstream subscription request includes the NetEase checkToken on subscribe.
ops='android/app/src/main/kotlin/com/lladlam/melox/core/network/NeteaseMusicOperationsClient.kt'
t=read(ops)
t=t.replace('''        val path = if (subscribed) "/api/playlist/subscribe" else "/api/playlist/unsubscribe"
        val data = JSONObject().put("id", playlistId)
        eapi(path, data, true)''','''        val path = if (subscribed) "/api/playlist/subscribe" else "/api/playlist/unsubscribe"
        val data = JSONObject().put("id", playlistId)
        if (subscribed) data.put("checkToken", NETEASE_CHECK_TOKEN)
        eapi(path, data, true)''')
if 'NETEASE_CHECK_TOKEN' not in t.split('private fun ensureLoggedIn()',1)[0]:
    t=t.replace('''    private fun ensureLoggedIn() {''','''    private companion object {
        const val NETEASE_CHECK_TOKEN = "9ca17ae2e6ffcda170e2e6ee8af14fbabdb988f225b3868eb2c15a879b9a83d274a790ac8ff54a97b889d5d42af0feaec3b92af58cff99c470a7eafd88f75e839a9ea7c14e909da883e83fb692a3abdb6b92adee9e"
    }

    private fun ensureLoggedIn() {''')
write(ops,t)

lib='android/app/src/main/kotlin/com/lladlam/melox/ui/library/LibraryScreen.kt'
t=read(lib)
if 'import com.lladlam.melox.core.network.NeteaseMusicOperationsClient' not in t:
    t=t.replace('''import com.lladlam.melox.core.model.SearchSong
''','''import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.core.network.NeteaseSearchClient
''')
# Add clients and save-state around playlist detail state.
t=t.replace('''    val cache = remember(appContext) { NeteaseLibraryCache(appContext) }
    var detail by remember(initialPlaylist.id) { mutableStateOf<NeteasePlaylistDetail?>(null) }''','''    val cache = remember(appContext) { NeteaseLibraryCache(appContext) }
    val accountClient = remember(appContext) {
        NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) })
    }
    val operationsClient = remember(appContext) {
        NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(appContext) })
    }
    var detail by remember(initialPlaylist.id) { mutableStateOf<NeteasePlaylistDetail?>(null) }''')
t=t.replace('''    var selectedTrackAction by remember(initialPlaylist.id) { mutableStateOf<SearchSong?>(null) }
    var palette''','''    var selectedTrackAction by remember(initialPlaylist.id) { mutableStateOf<SearchSong?>(null) }
    var isSaved by remember(initialPlaylist.id) { mutableStateOf<Boolean?>(null) }
    var savingPlaylist by remember(initialPlaylist.id) { mutableStateOf(false) }
    var palette''')
# Add saved-state loader before refresh function.
needle='''    suspend fun refreshPlaylist() {'''
insert='''    suspend fun refreshSavedState() {
        val cookie = NeteaseSessionStore.readCookie(appContext)
        if (!NeteaseSessionStore.containsMusicU(cookie)) {
            isSaved = null
            return
        }
        runCatching {
            val profile = accountClient.accountProfile(cookie)
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.userPlaylistsBlocking(profile.userId)
            }.any { it.id == initialPlaylist.id }
        }.onSuccess { isSaved = it }
    }

'''
if insert.strip() not in t:
    t=t.replace(needle,insert+needle,1)
# Add LaunchedEffect for saved state after existing playlist load effect block marker.
marker='''    val displayed = detail?.summary ?: initialPlaylist'''
if 'refreshSavedState()' not in t[t.find('LaunchedEffect(initialPlaylist.id)'):t.find(marker)]:
    t=t.replace(marker,'''    LaunchedEffect(initialPlaylist.id) {
        refreshSavedState()
    }

'''+marker,1)
# Replace hero onMore with saved action.
t=t.replace('''                        onMore = { showPlaylistActions = true },
                        sharedTransitionScope''','''                        isSaved = isSaved == true,
                        onToggleSaved = {
                            if (!savingPlaylist) {
                                val desired = isSaved != true
                                savingPlaylist = true
                                scope.launch {
                                    runCatching {
                                        operationsClient.setPlaylistSubscribed(displayed.id, desired)
                                    }.onSuccess {
                                        isSaved = desired
                                    }.onFailure {
                                        errorMessage = it.message ?: "歌单收藏操作失败"
                                    }
                                    savingPlaylist = false
                                }
                            }
                        },
                        sharedTransitionScope''',1)
# Hero signature.
t=t.replace('''    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onMore: () -> Unit,
    sharedTransitionScope''','''    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    isSaved: Boolean,
    onToggleSaved: () -> Unit,
    sharedTransitionScope''')
# Add description expansion state after BoxWithConstraints start.
t=t.replace('''    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val artworkSize = minOf(maxWidth * 0.68f, 300.dp)''','''    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val artworkSize = minOf(maxWidth * 0.68f, 300.dp)
        var descriptionExpanded by remember(playlist.id) { mutableStateOf(false) }''')
# Hero third action direct toggle and check mark.
t=t.replace('''                MeloXGlassCircleButton(
                    foreground = foreground,
                    size = 54.dp,
                    onClick = onMore,
                ) {
                    Text(
                        "+",
                        color = foreground,
                        fontSize = 34.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
        }
    }
}''','''                MeloXGlassCircleButton(
                    foreground = foreground,
                    size = 54.dp,
                    onClick = onToggleSaved,
                ) {
                    Text(
                        if (isSaved) "✓" else "+",
                        color = foreground,
                        fontSize = if (isSaved) 24.sp else 34.sp,
                        lineHeight = 34.sp,
                        fontWeight = if (isSaved) FontWeight.SemiBold else FontWeight.Light,
                    )
                }
            }

            playlist.description
                ?.takeIf(String::isNotBlank)
                ?.let { description ->
                    Text(
                        text = description,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 24.dp)
                            .clickable { descriptionExpanded = !descriptionExpanded },
                        color = secondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = if (descriptionExpanded) 12 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
        }
    }
}''',1)
write(lib,t)

(root/'tools/one_shot_final_semantics_patch.py').unlink(missing_ok=True)
(root/'.github/workflows/one-shot-final-semantics-patch.yml').unlink(missing_ok=True)
