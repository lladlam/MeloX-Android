package com.lladlam.melox.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lladlam.melox.data.db.entity.PlaybackHistoryEntity

@Dao
interface PlaybackHistoryDao {
    @Insert
    suspend fun insert(entry: PlaybackHistoryEntity)

    @Query("SELECT * FROM playback_history WHERE songId = :songId ORDER BY playedAt DESC LIMIT :limit")
    suspend fun getBySongId(songId: Long, limit: Int = 10): List<PlaybackHistoryEntity>

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<PlaybackHistoryEntity>

    @Query("DELETE FROM playback_history WHERE playedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int

    @Query("SELECT COUNT(*) FROM playback_history WHERE songId = :songId")
    suspend fun countBySongId(songId: Long): Int
}
