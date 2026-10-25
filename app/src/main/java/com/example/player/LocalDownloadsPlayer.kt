package com.example.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.downloader.SavedMediaResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocalDownloadsPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var playbackService: OfflinePlaybackService? = null
    private var isBound = false
    private var pendingAction: ((OfflinePlaybackService) -> Unit)? = null
    private var playerStateJob: kotlinx.coroutines.Job? = null
    private var fftJob: kotlinx.coroutines.Job? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _fftMagnitudes = MutableStateFlow(FloatArray(32) { 0.05f })
    val fftMagnitudes: StateFlow<FloatArray> = _fftMagnitudes.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? OfflinePlaybackService.LocalBinder ?: return
            val boundService = binder.getService()
            playbackService = boundService
            isBound = true

            playerStateJob?.cancel()
            playerStateJob = scope.launch {
                boundService.playerState.collect { state ->
                    _playerState.value = state
                }
            }
            fftJob?.cancel()
            fftJob = scope.launch {
                boundService.fftMagnitudes.collect { fft ->
                    _fftMagnitudes.value = fft
                }
            }

            pendingAction?.invoke(boundService)
            pendingAction = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerStateJob?.cancel()
            playerStateJob = null
            fftJob?.cancel()
            fftJob = null
            playbackService = null
            isBound = false
        }
    }

    init {
        bindServiceIfNeeded()
    }

    private fun bindServiceIfNeeded() {
        if (isBound && playbackService != null) return
        try {
            val intent = Intent(appContext, OfflinePlaybackService::class.java)
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (_: Exception) {
        }
    }

    private fun withService(action: (OfflinePlaybackService) -> Unit) {
        val current = playbackService
        if (current != null) {
            action(current)
        } else {
            pendingAction = action
            bindServiceIfNeeded()
        }
    }

    fun playQueue(queue: List<SavedMediaResult>, startIndex: Int) {
        try {
            val intent = Intent(appContext, OfflinePlaybackService::class.java)
            appContext.startService(intent)
        } catch (_: Throwable) {
            try {
                val intent = Intent(appContext, OfflinePlaybackService::class.java)
                ContextCompat.startForegroundService(appContext, intent)
            } catch (_: Throwable) {
            }
        }
        withService { it.setQueueAndPlay(queue, startIndex) }
    }

    fun syncQueue(updatedQueue: List<SavedMediaResult>) {
        withService { it.updateQueuePreservingCurrent(updatedQueue) }
    }

    fun removeTrackFromQueue(index: Int) {
        withService { it.removeTrackFromQueue(index) }
    }

    fun removeTrackFromQueueByTrackId(trackId: String) {
        withService { it.removeTrackFromQueueByTrackId(trackId) }
    }

    fun clearQueue() {
        withService { it.clearQueue() }
    }

    fun playTrackFromQueue(index: Int) {
        withService { it.playTrackAtIndex(index) }
    }

    fun togglePlayPause() {
        withService { it.togglePlayPause() }
    }

    fun stopPlayback() {
        withService { it.pause() }
    }

    fun seekTo(positionMs: Long) {
        withService { it.seekTo(positionMs) }
    }

    fun skipToNext() {
        withService { it.skipToNext() }
    }

    fun skipToPrevious() {
        withService { it.skipToPrevious() }
    }

    fun toggleShuffle() {
        withService { it.toggleShuffle() }
    }

    fun cycleRepeatMode() {
        withService { it.cycleRepeatMode() }
    }

    fun setPlaybackSpeed(speed: Float) {
        withService { it.setPlaybackSpeed(speed) }
    }

    fun setSleepTimer(minutes: Int?) {
        withService { it.setSleepTimer(minutes) }
    }

    fun setVolumeBoost(percent: Int, enabled: Boolean) {
        withService { it.updateVolumeBoost(percent, enabled) }
    }

    fun release() {
        if (isBound) {
            try {
                appContext.unbindService(connection)
            } catch (_: Exception) {
            }
            isBound = false
        }
        scope.cancel()
    }
}
