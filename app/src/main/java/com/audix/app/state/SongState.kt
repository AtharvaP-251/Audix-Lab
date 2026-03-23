package com.audix.app.state

import kotlinx.coroutines.flow.MutableStateFlow

data class SongInfo(
    val title: String,
    val artist: String,
    val packageName: String = "",
    val genre: String? = null
)

object SongState {
    val currentSong = MutableStateFlow<SongInfo?>(null)
    val isPlaying = MutableStateFlow(false)
    val isServiceConnected = MutableStateFlow(false)
    val isDetectingGenre = MutableStateFlow(false)
}
