from pathlib import Path
p=Path('android/app/src/main/kotlin/com/lladlam/melox/ui/library/LibraryScreen.kt')
s=p.read_text()

def one(old,new,label):
    global s
    if old not in s: raise SystemExit('missing '+label)
    s=s.replace(old,new,1)

one('import com.lladlam.melox.core.library.NeteaseLibraryClient\n','import com.lladlam.melox.core.download.MeloXDownloadStore\nimport com.lladlam.melox.core.library.NeteaseLibraryClient\n','download import')
one('''private enum class MeloXLibraryPage(val title: String) {\n    Songs("歌曲"),\n    Playlists("歌单"),\n    History("最近播放"),\n}\n''','''private enum class MeloXLibraryPage(val title: String) {\n    Songs("歌曲"),\n    Playlists("歌单"),\n    History("最近播放"),\n    Downloads("下载"),\n}\n''','library enum')
one('''    val cache = remember(appContext) { NeteaseLibraryCache(appContext) }\n\n    var selectedPage''','''    val cache = remember(appContext) { NeteaseLibraryCache(appContext) }\n    val downloadStore = remember(appContext) { MeloXDownloadStore.get(appContext) }\n\n    var selectedPage''','download store')
one('''                            MeloXLibraryPage.History -> MeloXLibrarySongsPage(\n                                songs = data.recentSongs,\n                                onPlay = { song ->\n                                    PlaybackCommands.playQueue(\n                                        context = context,\n                                        songs = data.recentSongs,\n                                        selectedSongId = song.id,\n                                        onFailure = { errorMessage = it.message ?: "播放失败" },\n                                    )\n                                },\n                                onPlayAll = {\n                                    data.recentSongs.firstOrNull()?.let { first ->\n                                        PlaybackCommands.playQueue(\n                                            context = context,\n                                            songs = data.recentSongs,\n                                            selectedSongId = first.id,\n                                            onFailure = { errorMessage = it.message ?: "播放失败" },\n                                        )\n                                    }\n                                },\n                            )\n''','''                            MeloXLibraryPage.History -> MeloXLibrarySongsPage(\n                                songs = data.recentSongs,\n                                onPlay = { song ->\n                                    PlaybackCommands.playQueue(\n                                        context = context, songs = data.recentSongs, selectedSongId = song.id,\n                                        onFailure = { errorMessage = it.message ?: "播放失败" },\n                                    )\n                                },\n                                onPlayAll = {\n                                    data.recentSongs.firstOrNull()?.let { first ->\n                                        PlaybackCommands.playQueue(\n                                            context = context, songs = data.recentSongs, selectedSongId = first.id,\n                                            onFailure = { errorMessage = it.message ?: "播放失败" },\n                                        )\n                                    }\n                                },\n                            )\n\n                            MeloXLibraryPage.Downloads -> MeloXLibraryDownloadsPage(downloadStore)\n''','downloads when')
marker='''@Composable\nprivate fun MeloXLibraryLoginUnavailable'''
page=r'''@Composable
private fun MeloXLibraryDownloadsPage(downloads: MeloXDownloadStore) {
    val context = LocalContext.current
    val active = downloads.activeDownloads.values.toList()
    val completed = downloads.downloads.toList()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 146.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (active.isNotEmpty()) {
            item { Text("正在下载", fontSize=20.sp, fontWeight=FontWeight.Bold, modifier=Modifier.padding(top=6.dp,bottom=8.dp)) }
            items(active, key={ "active-${it.song.id}" }) { item ->
                Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment=Alignment.CenterVertically) {
                    AsyncImage(item.song.artworkUrl,null,contentScale=ContentScale.Crop,modifier=Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)))
                    Column(Modifier.weight(1f).padding(start=12.dp)) {
                        Text(item.song.name,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)
                        Text(item.fractionCompleted?.let { "${(it*100).toInt()}% · ${item.quality.title}" } ?: item.quality.title,color=MaterialTheme.colorScheme.onBackground.copy(alpha=.48f),fontSize=12.sp)
                    }
                    Text("取消",color=MaterialTheme.colorScheme.error,modifier=Modifier.clickable { downloads.cancel(item.song.id) }.padding(10.dp))
                }
            }
        }
        if (completed.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(top=14.dp,bottom=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text("已下载",fontSize=20.sp,fontWeight=FontWeight.Bold)
                    Text("播放全部",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold,modifier=Modifier.clickable {
                        downloads.downloadedSongs.firstOrNull()?.let { PlaybackCommands.playQueue(context,downloads.downloadedSongs,it.id) }
                    })
                }
            }
            items(completed,key={ "download-${it.song.id}" }) { item ->
                Row(
                    Modifier.fillMaxWidth().height(62.dp).clickable { PlaybackCommands.playQueue(context,downloads.downloadedSongs,item.song.id) },
                    verticalAlignment=Alignment.CenterVertically,
                ) {
                    AsyncImage(item.song.artworkUrl,null,contentScale=ContentScale.Crop,modifier=Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)))
                    Column(Modifier.weight(1f).padding(start=12.dp)) {
                        Text(item.song.name,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)
                        Text("${item.song.artists} · ${item.quality.title}",maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onBackground.copy(alpha=.48f),fontSize=12.sp)
                    }
                    Text("删除",color=MaterialTheme.colorScheme.error,modifier=Modifier.clickable { downloads.remove(item.song.id) }.padding(10.dp))
                }
            }
        }
        if (active.isEmpty() && completed.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().height(260.dp),contentAlignment=Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("还没有下载歌曲",fontSize=19.sp,fontWeight=FontWeight.SemiBold)
                        Text("在歌曲的更多操作菜单中选择“下载歌曲”。",modifier=Modifier.padding(top=7.dp),color=MaterialTheme.colorScheme.onBackground.copy(alpha=.48f),fontSize=13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MeloXLibraryLoginUnavailable'''
if marker not in s: raise SystemExit('library login marker')
s=s.replace(marker,page,1)
p.write_text(s)
print('library downloads page applied')
