package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.downloader.SavedMediaResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TopPlayedItem(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val playCount: Int,
    val listeningSeconds: Long = 0L,
    val savedMedia: SavedMediaResult? = null,
    val streamUrl: String? = null
)

class PlayCountManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sakura_play_counts", Context.MODE_PRIVATE)

    private val _playCountsFlow = MutableStateFlow<Map<String, Int>>(loadAllCounts())
    val playCountsFlow: StateFlow<Map<String, Int>> = _playCountsFlow.asStateFlow()

    private val _listeningMsFlow = MutableStateFlow<Map<String, Long>>(loadAllListeningMs())
    val listeningMsFlow: StateFlow<Map<String, Long>> = _listeningMsFlow.asStateFlow()

    private val _totalListeningMsFlow = MutableStateFlow<Long>(prefs.getLong("total_listening_ms", 0L))
    val totalListeningMsFlow: StateFlow<Long> = _totalListeningMsFlow.asStateFlow()

    private fun loadAllCounts(): Map<String, Int> {
        val all = prefs.all
        val map = mutableMapOf<String, Int>()
        for ((k, v) in all) {
            if (k.startsWith("play_count_") && v is Int) {
                val trackId = k.removePrefix("play_count_")
                map[trackId] = v
            }
        }
        return map
    }

    private fun loadAllListeningMs(): Map<String, Long> {
        val all = prefs.all
        val map = mutableMapOf<String, Long>()
        for ((k, v) in all) {
            if (k.startsWith("listening_ms_") && v is Long) {
                val trackId = k.removePrefix("listening_ms_")
                map[trackId] = v
            }
        }
        return map
    }

    fun getAllPlayCounts(): Map<String, Int> = _playCountsFlow.value
    fun getAllListeningMs(): Map<String, Long> = _listeningMsFlow.value

    fun getPlayCount(trackId: String, defaultInitial: Int = 0): Int {
        val current = prefs.getInt("play_count_$trackId", -1)
        if (current >= 0) return current
        return defaultInitial
    }

    fun getTrackListeningSeconds(trackId: String): Long {
        val ms = _listeningMsFlow.value[trackId] ?: prefs.getLong("listening_ms_$trackId", 0L)
        return ms / 1000L
    }

    fun getTotalListeningSeconds(): Long {
        return _totalListeningMsFlow.value / 1000L
    }

    fun recordPlay(trackId: String) {
        if (trackId.isBlank()) return
        val current = getPlayCount(trackId, 0)
        val newCount = current + 1
        prefs.edit().putInt("play_count_$trackId", newCount).apply()
        _playCountsFlow.value = loadAllCounts()
    }

    @Synchronized
    fun addListeningTime(trackId: String, deltaMs: Long) {
        if (deltaMs <= 0L) return
        val currentTotalMs = prefs.getLong("total_listening_ms", 0L)
        val newTotalMs = currentTotalMs + deltaMs

        val trackMsKey = "listening_ms_$trackId"
        val currentTrackMs = if (trackId.isNotBlank()) prefs.getLong(trackMsKey, 0L) else 0L
        val newTrackMs = currentTrackMs + deltaMs

        val editor = prefs.edit().putLong("total_listening_ms", newTotalMs)
        if (trackId.isNotBlank()) {
            editor.putLong(trackMsKey, newTrackMs)
        }
        editor.apply()

        if (newTotalMs / 1000L != _totalListeningMsFlow.value / 1000L) {
            _totalListeningMsFlow.value = newTotalMs
        }
        if (trackId.isNotBlank()) {
            val prevPublishedMs = _listeningMsFlow.value[trackId] ?: 0L
            if (newTrackMs / 1000L != prevPublishedMs / 1000L || prevPublishedMs == 0L) {
                val updatedMap = _listeningMsFlow.value.toMutableMap()
                updatedMap[trackId] = newTrackMs
                _listeningMsFlow.value = updatedMap
            }
        }
    }

    fun getTopPlayedTracks(
        downloadedTracks: List<SavedMediaResult>,
        maxLimit: Int = 15
    ): List<TopPlayedItem> {
        if (downloadedTracks.isEmpty()) return emptyList()
        val countsSnapshot = _playCountsFlow.value
        val listeningSnapshot = _listeningMsFlow.value
        val result = ArrayList<TopPlayedItem>(downloadedTracks.size)

        for (saved in downloadedTracks) {
            val id = saved.trackInfo.id.ifBlank { saved.audioFile.name }
            val storedCount = countsSnapshot[id] ?: 0
            val listeningMs = listeningSnapshot[id] ?: 0L
            result.add(
                TopPlayedItem(
                    id = id,
                    title = saved.trackInfo.title,
                    artist = saved.trackInfo.artist,
                    thumbnailUrl = saved.coverFile?.absolutePath ?: saved.trackInfo.thumbnailUrl,
                    playCount = storedCount,
                    listeningSeconds = listeningMs / 1000L,
                    savedMedia = saved,
                    streamUrl = saved.trackInfo.sourceUrl
                )
            )
        }

        result.sortWith(compareByDescending<TopPlayedItem> { it.listeningSeconds }.thenByDescending { it.playCount })
        return if (maxLimit > 0 && result.size > maxLimit) {
            result.subList(0, maxLimit).toList()
        } else {
            result
        }
    }

    companion object {
        fun formatDuration(totalSeconds: Long): String {
            if (totalSeconds <= 0L) return "0 sn"
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L

            return when {
                hours > 0 -> {
                    if (minutes > 0 || seconds > 0) {
                        "$hours sa $minutes dk $seconds sn"
                    } else {
                        "$hours sa"
                    }
                }
                minutes > 0 -> {
                    if (seconds > 0) {
                        "$minutes dk $seconds sn"
                    } else {
                        "$minutes dk"
                    }
                }
                else -> "$seconds sn"
            }
        }
    }
}

