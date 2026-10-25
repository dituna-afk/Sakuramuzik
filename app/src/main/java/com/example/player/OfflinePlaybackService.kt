package com.example.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.MainActivity
import com.example.R
import com.example.downloader.SavedMediaResult
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.hypot
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OfflinePlaybackService : MediaBrowserServiceCompat() {

    inner class LocalBinder : Binder() {
        fun getService(): OfflinePlaybackService = this@OfflinePlaybackService
    }

    private val binder = LocalBinder()
    private val serviceExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("OfflinePlaybackService", "Unhandled coroutine exception: ${throwable.message}", throwable)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + serviceExceptionHandler)

    private val audioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SakuraAudioEngine").apply {
            priority = Thread.MAX_PRIORITY
        }
    }
    private val audioDispatcher = audioExecutor.asCoroutineDispatcher()

    private var mediaSession: MediaSessionCompat? = null

    @Volatile
    private var mediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private var equalizer: Equalizer? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    @Volatile
    private var currentArtworkBitmap: Bitmap? = null
    private val artworkMemoryCache = LruCache<String, Bitmap>(28)
    private val playGeneration = AtomicLong(0L)

    @Volatile
    private var lastFftEmitUptimeMs: Long = 0L

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _fftMagnitudes = MutableStateFlow(FloatArray(32) { 0.05f })
    val fftMagnitudes: StateFlow<FloatArray> = _fftMagnitudes.asStateFlow()

    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val volumeBoostStorage by lazy {
        com.example.data.VolumeBoostStorageHelper(applicationContext)
    }

    private val dspStorage by lazy {
        StudioDspStorageHelper(applicationContext)
    }

    private val togglesStorage by lazy {
        com.example.data.FeatureTogglesStorageHelper(applicationContext)
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (_playerState.value.isPlaying) {
                    pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                serviceScope.launch(audioDispatcher) {
                    try {
                        mediaPlayer?.setVolume(0.25f, 0.25f)
                    } catch (_: Exception) {
                    }
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                serviceScope.launch(audioDispatcher) {
                    try {
                        mediaPlayer?.setVolume(1.0f, 1.0f)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private val audioFocusRequest: AudioFocusRequest? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
        } else {
            null
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (SERVICE_INTERFACE == intent?.action) {
            return super.onBind(intent)
        }
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_STOP) {
            startForegroundWithNotification()
        }
        when (intent?.action) {
            ACTION_TOGGLE_PLAY -> togglePlayPause()
            ACTION_NEXT -> skipToNext()
            ACTION_PREV -> skipToPrevious()
            ACTION_STOP -> {
                startForegroundWithNotification()
                pause()
                serviceScope.launch(audioDispatcher) {
                    releaseMediaPlayerInternal()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationManager.cancel(NOTIFICATION_ID)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot("sakura_root", null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        result.sendResult(mutableListOf())
    }

    private fun initMediaSession() {
        val session = MediaSessionCompat(this, "SakuraOfflinePlaybackSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onSkipToNext() = skipToNext()
                override fun onSkipToPrevious() = skipToPrevious()
                override fun onSeekTo(pos: Long) = seekTo(pos)
                override fun onStop() = pause()
            })
            isActive = true
        }
        mediaSession = session
        sessionToken = session.sessionToken
    }

    fun setQueueAndPlay(queue: List<SavedMediaResult>, startIndex: Int) {
        if (queue.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, queue.lastIndex)
        val currentState = _playerState.value
        val target = queue[safeIndex]

        // If the exact same track is already prepared and playing, just update queue without restarting audio
        if (currentState.isPrepared &&
            currentState.currentTrack?.audioFile?.absolutePath == target.audioFile.absolutePath &&
            mediaPlayer != null
        ) {
            _playerState.update {
                it.copy(
                    queue = queue,
                    currentIndex = safeIndex,
                    currentTrack = target,
                    errorMessage = null
                )
            }
            if (!currentState.isPlaying) {
                resume()
            }
            return
        }

        _playerState.update {
            it.copy(
                queue = queue,
                currentIndex = safeIndex,
                currentTrack = target,
                errorMessage = null
            )
        }
        playTrackAtIndex(safeIndex)
    }

    fun updateQueuePreservingCurrent(updatedQueue: List<SavedMediaResult>) {
        val currentPath = _playerState.value.currentTrack?.audioFile?.absolutePath
        if (currentPath == null) {
            _playerState.update { it.copy(queue = updatedQueue) }
            return
        }
        val newIndex = updatedQueue.indexOfFirst { it.audioFile.absolutePath == currentPath }
        _playerState.update {
            it.copy(
                queue = updatedQueue,
                currentIndex = if (newIndex >= 0) newIndex else it.currentIndex,
                currentTrack = if (newIndex >= 0) updatedQueue[newIndex] else it.currentTrack
            )
        }
    }

    fun removeTrackFromQueue(index: Int) {
        val currentQueue = _playerState.value.queue
        if (index !in currentQueue.indices) return

        val removingCurrent = (index == _playerState.value.currentIndex)
        val wasPlaying = _playerState.value.isPlaying
        val newQueue = currentQueue.toMutableList().apply { removeAt(index) }

        if (newQueue.isEmpty()) {
            clearQueue()
            return
        }

        if (removingCurrent) {
            val nextIndex = if (index <= newQueue.lastIndex) index else 0
            _playerState.update {
                it.copy(
                    queue = newQueue,
                    currentIndex = nextIndex,
                    currentTrack = newQueue[nextIndex]
                )
            }
            if (wasPlaying) {
                playTrackAtIndex(nextIndex)
            } else {
                stopProgressUpdater()
                serviceScope.launch(audioDispatcher) {
                    try {
                        mediaPlayer?.reset()
                    } catch (_: Exception) {
                    }
                }
                _playerState.update {
                    it.copy(
                        isPlaying = false,
                        isPrepared = false,
                        currentPositionMs = 0L,
                        durationMs = (newQueue[nextIndex].trackInfo.durationSeconds * 1000L).coerceAtLeast(1L)
                    )
                }
            }
        } else {
            val oldCurrentIndex = _playerState.value.currentIndex
            val newCurrentIndex = if (index < oldCurrentIndex) oldCurrentIndex - 1 else oldCurrentIndex
            _playerState.update {
                it.copy(
                    queue = newQueue,
                    currentIndex = newCurrentIndex,
                    currentTrack = if (newCurrentIndex in newQueue.indices) newQueue[newCurrentIndex] else it.currentTrack
                )
            }
        }
        updateMediaSessionState()
        updateNotification()
    }

    fun removeTrackFromQueueByTrackId(trackId: String) {
        val queue = _playerState.value.queue
        val index = queue.indexOfFirst { it.trackInfo.id == trackId || it.audioFile.absolutePath == trackId }
        if (index >= 0) {
            removeTrackFromQueue(index)
        }
    }

    fun clearQueue() {
        stopProgressUpdater()
        serviceScope.launch(audioDispatcher) {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.reset()
            } catch (_: Throwable) {
            }
        }
        _playerState.update {
            it.copy(
                queue = emptyList(),
                currentIndex = -1,
                currentTrack = null,
                isPlaying = false,
                isPrepared = false,
                currentPositionMs = 0L,
                durationMs = 0L,
                errorMessage = null
            )
        }
        updateMediaSessionState()
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    fun playTrackAtIndex(index: Int) {
        val queue = _playerState.value.queue
        if (index !in queue.indices) return
        val target = queue[index]
        val audioFile = target.audioFile
        if (!audioFile.exists() || audioFile.length() <= 0L) {
            _playerState.update {
                it.copy(
                    isPlaying = false,
                    isPrepared = false,
                    errorMessage = "Ses dosyası bulunamadı veya bozuk: ${audioFile.name}"
                )
            }
            return
        }

        val gen = playGeneration.incrementAndGet()
        val estimatedDurationMs = (target.trackInfo.durationSeconds * 1000L).coerceAtLeast(1L)

        // Instant 0ms optimistic UI transition so cover, title, and seekbar switch immediately
        stopProgressUpdater()
        val cachedArt = getArtworkCacheKey(target)?.let { artworkMemoryCache.get(it) }
        currentArtworkBitmap = cachedArt

        _playerState.update {
            it.copy(
                currentIndex = index,
                currentTrack = target,
                isPlaying = true,
                isPrepared = false,
                currentPositionMs = 0L,
                durationMs = estimatedDurationMs,
                errorMessage = null
            )
        }

        val targetSpeed = _playerState.value.playbackSpeed

        // Perform all blocking MediaPlayer teardown, preparation, and hardware effect binding off the Main Thread
        serviceScope.launch(audioDispatcher) {
            if (gen != playGeneration.get()) return@launch

            requestAudioFocus()
            releaseMediaPlayerInternal()

            if (gen != playGeneration.get()) return@launch

            try {
                val mp = MediaPlayer()
                mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                mp.setDataSource(audioFile.absolutePath)
                mp.setOnCompletionListener {
                    serviceScope.launch {
                        if (gen == playGeneration.get()) {
                            handleTrackCompletion()
                        }
                    }
                }
                mp.setOnErrorListener { _, what, extra ->
                    if (gen == playGeneration.get()) {
                        _playerState.update {
                            it.copy(
                                isPlaying = false,
                                isPrepared = false,
                                errorMessage = "Oynatma hatası ($what / $extra)"
                            )
                        }
                    }
                    true
                }

                // Since audioDispatcher is a dedicated background thread and audioFile is local on disk,
                // synchronous prepare() on audioDispatcher is ultra-fast (~3-8ms) and avoids Main-thread callbacks!
                mp.prepare()

                if (gen != playGeneration.get()) {
                    try {
                        mp.release()
                    } catch (_: Exception) {
                    }
                    return@launch
                }

                val duration = mp.duration.toLong().coerceAtLeast(estimatedDurationMs)
                applyPlaybackSpeedInternal(mp, targetSpeed)
                mp.start()
                mediaPlayer = mp

                _playerState.update {
                    it.copy(
                        currentIndex = index,
                        currentTrack = target,
                        isPlaying = true,
                        isPrepared = true,
                        currentPositionMs = 0L,
                        durationMs = duration,
                        errorMessage = null
                    )
                }

                serviceScope.launch {
                    if (gen == playGeneration.get()) {
                        startProgressUpdater()
                        updateMediaSessionState()
                        startForegroundWithNotification()
                        loadArtworkAsync(target, index, queue)
                    }
                }

                // Initialize audio effects & visualizer on audioDispatcher AFTER playback has already started
                setupAudioEffectsInternal(mp, mp.audioSessionId, gen)
            } catch (e: Exception) {
                if (gen == playGeneration.get()) {
                    _playerState.update {
                        it.copy(
                            isPlaying = false,
                            isPrepared = false,
                            errorMessage = "Yerel oynatıcı başlatılamadı: ${e.localizedMessage}"
                        )
                    }
                }
            }
        }
    }

    fun togglePlayPause() {
        val state = _playerState.value
        if (!state.isPrepared) {
            if (state.currentIndex in state.queue.indices) {
                playTrackAtIndex(state.currentIndex)
            } else if (state.queue.isNotEmpty()) {
                playTrackAtIndex(0)
            }
            return
        }
        if (state.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun resume() {
        val state = _playerState.value
        if (!state.isPrepared) return
        _playerState.update { it.copy(isPlaying = true) }
        serviceScope.launch(audioDispatcher) {
            val mp = mediaPlayer ?: return@launch
            try {
                if (!mp.isPlaying && requestAudioFocus()) {
                    mp.start()
                    visualizer?.enabled = true
                }
            } catch (_: Exception) {
            }
        }
        updateMediaSessionState()
        startProgressUpdater()
        startForegroundWithNotification()
    }

    fun pause() {
        stopProgressUpdater()
        _playerState.update { it.copy(isPlaying = false) }
        serviceScope.launch(audioDispatcher) {
            val mp = mediaPlayer ?: return@launch
            try {
                visualizer?.enabled = false
                if (mp.isPlaying) {
                    mp.pause()
                }
                val pos = mp.currentPosition.toLong().coerceAtLeast(0L)
                _playerState.update { it.copy(currentPositionMs = pos) }
            } catch (_: Exception) {
            }
        }
        updateMediaSessionState()
        updateNotification()
    }

    fun seekTo(positionMs: Long) {
        if (!_playerState.value.isPrepared) return
        val safePos = positionMs.coerceIn(0L, _playerState.value.durationMs.coerceAtLeast(1L))
        _playerState.update { it.copy(currentPositionMs = safePos) }
        serviceScope.launch(audioDispatcher) {
            try {
                mediaPlayer?.seekTo(safePos.toInt())
            } catch (_: Exception) {
            }
        }
        updateMediaSessionState()
    }

    fun skipToNext() {
        val state = _playerState.value
        val queue = state.queue
        if (queue.isEmpty()) return

        val nextIndex = if (state.isShuffleEnabled && queue.size > 1) {
            val candidates = queue.indices.filter { it != state.currentIndex }
            candidates.random()
        } else {
            val candidate = state.currentIndex + 1
            if (candidate <= queue.lastIndex) {
                candidate
            } else {
                0
            }
        }
        playTrackAtIndex(nextIndex)
    }

    fun skipToPrevious() {
        val state = _playerState.value
        val queue = state.queue
        if (queue.isEmpty()) return

        if (state.currentPositionMs > 3_500L) {
            seekTo(0L)
            return
        }

        val prevIndex = if (state.currentIndex > 0) {
            state.currentIndex - 1
        } else {
            queue.lastIndex
        }
        playTrackAtIndex(prevIndex)
    }

    fun toggleShuffle() {
        _playerState.update { it.copy(isShuffleEnabled = !it.isShuffleEnabled) }
    }

    fun cycleRepeatMode() {
        val nextMode = when (_playerState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playerState.update { it.copy(repeatMode = nextMode) }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _playerState.update { it.copy(playbackSpeed = clamped) }
        StudioDspController.setBasePlaybackSpeed(clamped)
        serviceScope.launch(audioDispatcher) {
            val mp = mediaPlayer ?: return@launch
            if (_playerState.value.isPrepared) {
                try {
                    val wasPlaying = mp.isPlaying
                    mp.playbackParams = mp.playbackParams.setSpeed(clamped)
                    if (!wasPlaying && mp.isPlaying) {
                        mp.pause()
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun applyPlaybackSpeedInternal(mp: MediaPlayer, speed: Float) {
        if ((speed - 1.0f) in -0.001f..0.001f) return
        try {
            mp.playbackParams = mp.playbackParams.setSpeed(speed)
        } catch (_: Exception) {
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _playerState.update { it.copy(sleepTimerRemainingMs = null) }
            return
        }
        val totalMs = minutes * 60_000L
        _playerState.update { it.copy(sleepTimerRemainingMs = totalMs) }
        sleepTimerJob = serviceScope.launch {
            var remaining = totalMs
            while (isActive && remaining > 0L) {
                delay(1_000L)
                remaining = (remaining - 1_000L).coerceAtLeast(0L)
                _playerState.update { it.copy(sleepTimerRemainingMs = remaining) }
            }
            if (remaining <= 0L) {
                _playerState.update { it.copy(sleepTimerRemainingMs = null) }
                pause()
            }
        }
    }

    private fun handleTrackCompletion() {
        val state = _playerState.value
        when (state.repeatMode) {
            RepeatMode.ONE -> playTrackAtIndex(state.currentIndex)
            RepeatMode.ALL -> skipToNext()
            RepeatMode.OFF -> {
                if (state.currentIndex < state.queue.lastIndex || state.isShuffleEnabled) {
                    skipToNext()
                } else if (togglesStorage.loadConfig().smartAutoplayEnabled && state.queue.size > 1) {
                    val currentArtist = state.currentTrack?.trackInfo?.artist.orEmpty()
                    val candidateIndices = state.queue.indices.filter { it != state.currentIndex }
                    val smartIndex = candidateIndices.firstOrNull { idx ->
                        state.queue[idx].trackInfo.artist.equals(currentArtist, ignoreCase = true)
                    } ?: candidateIndices.randomOrNull() ?: 0
                    playTrackAtIndex(smartIndex)
                } else {
                    _playerState.update {
                        it.copy(
                            isPlaying = false,
                            currentPositionMs = it.durationMs
                        )
                    }
                    stopProgressUpdater()
                    updateMediaSessionState()
                    updateNotification()
                }
            }
        }
    }

    fun updateVolumeBoost(percent: Int, enabled: Boolean) {
        serviceScope.launch(audioDispatcher) {
            val session = mediaPlayer?.audioSessionId ?: 0
            AudioEffectManager.applyOfflineVolumeBoost(session, percent, enabled, forceRecreate = false)
        }
    }

    private fun setupAudioEffectsInternal(mp: MediaPlayer, audioSessionId: Int, gen: Long) {
        if (audioSessionId == 0 || gen != playGeneration.get()) return
        try {
            AudioEffectManager.applyOfflineVolumeBoost(
                audioSessionId = audioSessionId,
                percent = volumeBoostStorage.getVolumeBoostPercent(),
                enabled = volumeBoostStorage.isVolumeBoosterEnabled(),
                forceRecreate = true
            )
        } catch (_: Throwable) {
        }

        try {
            StudioDspController.updateState(dspStorage.loadConfig(), togglesStorage.loadConfig())
            StudioDspController.bindMediaPlayer(mp, audioSessionId)
        } catch (_: Throwable) {
        }

        if (gen != playGeneration.get()) return

        try {
            visualizer?.release()
            val captureSizeRange = Visualizer.getCaptureSizeRange()
            val captureSize = captureSizeRange.getOrElse(1) { 256 }.coerceAtMost(256)
            visualizer = Visualizer(audioSessionId).apply {
                this.captureSize = captureSize
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) = Unit

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (fft == null || fft.size < 64 || !_playerState.value.isPlaying) return
                            val now = SystemClock.uptimeMillis()
                            if (now - lastFftEmitUptimeMs < 75L) return
                            lastFftEmitUptimeMs = now

                            val bins = FloatArray(32)
                            for (i in 0 until 32) {
                                val real = fft[i * 2].toInt()
                                val imag = fft[i * 2 + 1].toInt()
                                val magnitude = hypot(real.toDouble(), imag.toDouble()).toFloat()
                                bins[i] = (magnitude / 128f).coerceIn(0.05f, 1.0f)
                            }
                            _fftMagnitudes.value = bins
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    false,
                    true
                )
                enabled = true
            }
        } catch (_: Throwable) {
        }
    }

    private fun startProgressUpdater() {
        progressJob?.cancel()
        progressJob = serviceScope.launch(audioDispatcher) {
            while (isActive && _playerState.value.isPlaying) {
                val mp = mediaPlayer
                if (mp != null && _playerState.value.isPrepared) {
                    val pos = try {
                        mp.currentPosition.toLong().coerceAtLeast(0L)
                    } catch (_: Exception) {
                        _playerState.value.currentPositionMs
                    }
                    if (kotlin.math.abs(pos - _playerState.value.currentPositionMs) >= 200L) {
                        _playerState.update { it.copy(currentPositionMs = pos) }
                    }
                }
                delay(300L)
            }
        }
    }

    private fun stopProgressUpdater() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun getArtworkCacheKey(track: SavedMediaResult): String? {
        val localCover = track.coverFile
        if (localCover != null && localCover.exists()) {
            return localCover.absolutePath
        }
        return track.trackInfo.thumbnailUrl?.takeIf { it.isNotBlank() }
    }

    private fun decodeArtworkBitmap(track: SavedMediaResult): Bitmap? {
        val key = getArtworkCacheKey(track) ?: return null
        val cached = artworkMemoryCache.get(key)
        if (cached != null && !cached.isRecycled) return cached

        val opts = BitmapFactory.Options().apply {
            inSampleSize = 2
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = try {
            val localCover = track.coverFile
            if (localCover != null && localCover.exists()) {
                BitmapFactory.decodeFile(localCover.absolutePath, opts)
            } else {
                val thumb = track.trackInfo.thumbnailUrl
                if (!thumb.isNullOrBlank()) {
                    if (File(thumb).exists()) {
                        BitmapFactory.decodeFile(thumb, opts)
                    } else if (thumb.startsWith("http")) {
                        val conn = (URL(thumb).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 4_000
                            readTimeout = 4_000
                        }
                        conn.inputStream.use { BitmapFactory.decodeStream(it, null, opts) }
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
        if (decoded != null) {
            artworkMemoryCache.put(key, decoded)
        }
        return decoded
    }

    private fun loadArtworkAsync(
        track: SavedMediaResult,
        currentIndex: Int,
        queue: List<SavedMediaResult>
    ) {
        val key = getArtworkCacheKey(track)
        val cached = key?.let { artworkMemoryCache.get(it) }
        if (cached != null && !cached.isRecycled) {
            currentArtworkBitmap = cached
            updateMediaSessionState()
            updateNotification()
        } else {
            serviceScope.launch(Dispatchers.IO) {
                val bmp = decodeArtworkBitmap(track)
                if (_playerState.value.currentTrack?.audioFile?.absolutePath == track.audioFile.absolutePath) {
                    currentArtworkBitmap = bmp
                    launch(Dispatchers.Main) {
                        updateMediaSessionState()
                        updateNotification()
                    }
                }
            }
        }

        // Pre-warm next track's artwork in background so next track transition is 0ms
        if (queue.size > 1) {
            val nextIdx = (currentIndex + 1) % queue.size
            val nextTrack = queue.getOrNull(nextIdx)
            if (nextTrack != null) {
                serviceScope.launch(Dispatchers.IO) {
                    decodeArtworkBitmap(nextTrack)
                }
            }
        }
    }

    private fun updateMediaSessionState() {
        val session = mediaSession ?: return
        val state = _playerState.value
        val track = state.currentTrack

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_STOP
            )
            .setState(
                if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                state.currentPositionMs,
                state.playbackSpeed
            )
            .build()
        session.setPlaybackState(playbackState)

        if (track != null) {
            val metadataBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.trackInfo.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.trackInfo.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.trackInfo.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.durationMs)
            currentArtworkBitmap?.let {
                if (!it.isRecycled) {
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                }
            }
            session.setMetadata(metadataBuilder.build())
        }
    }

    private fun buildNotification(): Notification {
        val state = _playerState.value
        val track = state.currentTrack

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevPending = PendingIntent.getService(
            this,
            1,
            Intent(this, OfflinePlaybackService::class.java).setAction(ACTION_PREV),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPausePending = PendingIntent.getService(
            this,
            2,
            Intent(this, OfflinePlaybackService::class.java).setAction(ACTION_TOGGLE_PLAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextPending = PendingIntent.getService(
            this,
            3,
            Intent(this, OfflinePlaybackService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPending = PendingIntent.getService(
            this,
            4,
            Intent(this, OfflinePlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(track?.trackInfo?.title ?: "Sakura Music")
            .setContentText(track?.trackInfo?.artist ?: "Çevrimdışı Oynatıcı")
            .setLargeIcon(currentArtworkBitmap?.takeIf { !it.isRecycled })
            .setContentIntent(openAppIntent)
            .setDeleteIntent(stopPending)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(state.isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Önceki", prevPending)
            .addAction(
                if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.isPlaying) "Duraklat" else "Oynat",
                playPausePending
            )
            .addAction(android.R.drawable.ic_media_next, "Sonraki", nextPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Kapat", stopPending)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun startForegroundWithNotification() {
        try {
            val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), fgType)
        } catch (_: Throwable) {
            try {
                startForeground(NOTIFICATION_ID, buildNotification())
            } catch (_: Throwable) {
            }
        }
    }

    private fun updateNotification() {
        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sakura Music Oynatıcı",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Medya oynatma kontrolleri"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = audioFocusRequest ?: return true
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusChangeListener)
            }
        } catch (_: Throwable) {
        }
    }

    private fun releaseMediaPlayerInternal() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Throwable) {
        }
        visualizer = null
        try {
            equalizer?.release()
        } catch (_: Throwable) {
        }
        equalizer = null
        try {
            AudioEffectManager.releaseOfflineEffects()
        } catch (_: Throwable) {
        }
        val oldMp = mediaPlayer
        mediaPlayer = null
        try {
            StudioDspController.unbindMediaPlayer(oldMp)
        } catch (_: Throwable) {
        }
        if (oldMp != null) {
            try {
                oldMp.reset()
            } catch (_: Exception) {
            }
            try {
                oldMp.release()
            } catch (_: Exception) {
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            artworkMemoryCache.evictAll()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        artworkMemoryCache.evictAll()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        pause()
        abandonAudioFocus()
        serviceScope.launch(audioDispatcher) {
            releaseMediaPlayerInternal()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        sleepTimerJob?.cancel()
        stopProgressUpdater()
        abandonAudioFocus()
        artworkMemoryCache.evictAll()
        try {
            audioExecutor.execute {
                releaseMediaPlayerInternal()
            }
            audioExecutor.shutdown()
        } catch (_: Exception) {
        }
        mediaSession?.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "sakura_offline_playback_channel"
        private const val NOTIFICATION_ID = 4102
        const val ACTION_TOGGLE_PLAY = "com.example.player.ACTION_TOGGLE_PLAY"
        const val ACTION_NEXT = "com.example.player.ACTION_NEXT"
        const val ACTION_PREV = "com.example.player.ACTION_PREV"
        const val ACTION_STOP = "com.example.player.ACTION_STOP"
    }
}
