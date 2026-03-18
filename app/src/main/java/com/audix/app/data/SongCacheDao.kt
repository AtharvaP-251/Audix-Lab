package com.audix.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SongCacheDao {
    @Query("SELECT genre FROM song_cache WHERE title = :title AND artist = :artist LIMIT 1")
    suspend fun getGenreForSong(title: String, artist: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(songCache: SongCache)
}
