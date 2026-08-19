package com.lladlam.melox.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lladlam.melox.data.db.entity.PlaybackCountEntity

@Dao
interface PlaybackCountDao {
    @Query("SELECT * FROM playback_counts WHERE songId = :songId")
    suspend fun getBySongId(songId: Long): PlaybackCountEntity?

    @Query("SELECT * FROM playback_counts ORDER BY playCount DESC LIMIT :limit")
    suspend fun getTopPlayed(limit: Int = 50): List<PlaybackCountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(count: PlaybackCountEntity)

    @Query("UPDATE playback_counts SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE songId = :songId")
    suspend fun incrementPlayCount(songId: Long, timestamp: Long)

    @Query("DELETE FROM playback_counts")
    suspend fun deleteAll()
}
