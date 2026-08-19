package com.lladlam.melox.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_lyrics")
data class CachedLyricEntity(
    @PrimaryKey val songId: Long,
    val content: String,
    val sourceName: String,
    val parserType: String?,
    val cachedAt: Long,
)
