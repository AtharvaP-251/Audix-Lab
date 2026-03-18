package com.audix.app.data

import androidx.room.Entity

@Entity(tableName = "song_cache", primaryKeys = ["title", "artist"])
data class SongCache(
    val title: String,
    val artist: String,
    val genre: String,
    val timestamp: Long
)
