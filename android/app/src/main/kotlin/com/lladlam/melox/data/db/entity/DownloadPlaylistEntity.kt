package com.lladlam.melox.data.db.entity

import androidx.room.Entity

@Entity(
    tableName = "download_playlists",
    primaryKeys = ["playlistId", "songId"],
)
data class DownloadPlaylistEntity(
    val playlistId: Long,
    val songId: Long,
    val playlistName: String,
    val playlistArtworkUrl: String?,
)
