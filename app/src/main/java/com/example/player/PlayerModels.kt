package com.example.player

import com.example.downloader.SavedMediaResult

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

data class PlayerState(
    val queue: List<SavedMediaResult> = emptyList(),
    val currentIndex: Int = -1,
    val currentTrack: SavedMediaResult? = null,
    val isPlaying: Boolean = false,
    val isPrepared: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerRemainingMs: Long? = null,
    val errorMessage: String? = null
)
