package com.lladlam.melox.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lladlam.melox.data.db.dao.CachedLyricDao
import com.lladlam.melox.data.db.dao.DownloadDao
import com.lladlam.melox.data.db.dao.PlaybackCountDao
import com.lladlam.melox.data.db.dao.PlaybackHistoryDao
import com.lladlam.melox.data.db.entity.CachedLyricEntity
import com.lladlam.melox.data.db.entity.DownloadEntity
import com.lladlam.melox.data.db.entity.DownloadPlaylistEntity
import com.lladlam.melox.data.db.entity.PlaybackCountEntity
import com.lladlam.melox.data.db.entity.PlaybackHistoryEntity

@Database(
    entities = [
        DownloadEntity::class,
        DownloadPlaylistEntity::class,
        CachedLyricEntity::class,
        PlaybackHistoryEntity::class,
        PlaybackCountEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class MeloXDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun cachedLyricDao(): CachedLyricDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun playbackCountDao(): PlaybackCountDao

    companion object {
        private const val DATABASE_NAME = "melox_room.db"

        fun create(context: Context): MeloXDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MeloXDatabase::class.java,
                DATABASE_NAME,
            ).build()
        }
    }
}
