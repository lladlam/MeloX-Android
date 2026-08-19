package com.lladlam.melox.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lladlam.melox.data.db.entity.CachedLyricEntity

@Dao
interface CachedLyricDao {
    @Query("SELECT * FROM cached_lyrics WHERE songId = :songId")
    suspend fun getBySongId(songId: Long): CachedLyricEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lyric: CachedLyricEntity)

    @Query("DELETE FROM cached_lyrics WHERE songId = :songId")
    suspend fun deleteBySongId(songId: Long)

    @Query("DELETE FROM cached_lyrics WHERE cachedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int

    @Query("DELETE FROM cached_lyrics")
    suspend fun deleteAll()
}
