package com.lladlam.melox.core.library

import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.music.model.MusicPlaylistSummary

data class NeteasePlaylistSummary(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
    /** 服务端歌单类型；`5` 即「我喜欢的音乐」。字段缺失时为空（历史/非标准接口返回）。 */
    val specialType: Int? = null,
    val creatorName: String,
    val creatorUserId: Long? = null,
    val playCount: Long = 0L,
    val description: String? = null,
    /**
     * Optional provider-neutral backing playlist. Keeping this on the legacy UI
     * model lets the existing MeloX playlist list/detail animation renderer stay
     * the single renderer for NetEase, QQ Music and Kugou.
     */
    val providerPlaylist: MusicPlaylistSummary? = null,
    /** App-owned collection (currently the unified favorites playlist), not a NetEase playlist. */
    val isMeloXLocal: Boolean = false,
)

data class NeteasePlaylistDetail(
    val summary: NeteasePlaylistSummary,
    val songs: List<SearchSong>,
)

data class NeteaseLibrarySnapshot(
    val playlists: List<NeteasePlaylistSummary>,
    val likedSongs: List<SearchSong>,
    val recentSongs: List<SearchSong>,
    val likedPlaylistId: Long? = null,
)
