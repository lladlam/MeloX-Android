package com.lladlam.melox.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lladlam.melox.data.db.entity.DownloadEntity
import com.lladlam.melox.data.db.entity.DownloadPlaylistEntity

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    suspend fun getAll(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE songId = :songId")
    suspend fun getBySongId(songId: Long): DownloadEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE songId = :songId)")
    suspend fun contains(songId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(downloads: List<DownloadEntity>)

    @Query("DELETE FROM downloads WHERE songId = :songId")
    suspend fun deleteBySongId(songId: Long)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    @Query("SELECT * FROM downloads WHERE songId IN (SELECT songId FROM download_playlists WHERE playlistId = :playlistId)")
    suspend fun getByPlaylist(playlistId: Long): List<DownloadEntity>

    @Query("SELECT DISTINCT playlistId, playlistName, playlistArtworkUrl FROM download_playlists")
    suspend fun getDownloadedPlaylists(): List<DownloadPlaylistSummary>

    @Query("SELECT quality, COUNT(*) as count FROM downloads GROUP BY quality")
    suspend fun statsByQuality(): List<QualityStat>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistRefs(refs: List<DownloadPlaylistEntity>)

    @Query("DELETE FROM download_playlists WHERE songId = :songId")
    suspend fun deletePlaylistRefsBySongId(songId: Long)
}

data class QualityStat(
    val quality: String,
    val count: Int,
)

data class DownloadPlaylistSummary(
    val playlistId: Long,
    val playlistName: String,
    val playlistArtworkUrl: String?,
)
