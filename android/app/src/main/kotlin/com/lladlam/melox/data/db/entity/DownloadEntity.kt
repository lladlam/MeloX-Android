package com.lladlam.melox.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val songId: Long,
    val name: String,
    val artists: String,
    val album: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val quality: String,
    val fileName: String,
    val byteCount: Long,
    val bitrate: Int?,
    val format: String?,
    val downloadedAt: Long,
    val artworkFileName: String?,
    val lyricsFileName: String?,
)
