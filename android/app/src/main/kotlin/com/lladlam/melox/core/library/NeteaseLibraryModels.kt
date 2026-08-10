package com.lladlam.melox.core.library

import com.lladlam.melox.core.model.SearchSong

data class NeteasePlaylistSummary(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
    val creatorName: String,
    val creatorUserId: Long? = null,
    val playCount: Long = 0L,
    val description: String? = null,
)

data class NeteasePlaylistDetail(
    val summary: NeteasePlaylistSummary,
    val songs: List<SearchSong>,
)

data class NeteaseLibrarySnapshot(
    val playlists: List<NeteasePlaylistSummary>,
    val likedSongs: List<SearchSong>,
    val recentSongs: List<SearchSong>,
)
