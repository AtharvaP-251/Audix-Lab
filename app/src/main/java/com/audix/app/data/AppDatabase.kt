package com.audix.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SongCache::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songCacheDao(): SongCacheDao
}
