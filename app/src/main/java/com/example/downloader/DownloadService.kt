package com.example.downloader

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.CookieStorageHelper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DownloadService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    private val binder = LocalBinder()
    private val serviceExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("DownloadService", "Unhandled coroutine exception: ${throwable.message}", throwable)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + serviceExceptionHandler)

    private lateinit var notificationHelper: DownloadNotificationHelper
    private lateinit var metadataManager: MediaMetadataManager
    private lateinit var streamResolver: YouTubeStreamResolver
    private lateinit var preferencesManager: DownloadPreferencesManager
    private lateinit var cookieStorageHelper: CookieStorageHelper
    lateinit var downloadManager: YoutubeDLDownloadManager
        private set

    private var activeDownloadJob: Job? = null

    private val _serviceDownloadState =
        MutableStateFlow<YoutubeDLDownloadState>(YoutubeDLDownloadState.Idle)
    val serviceDownloadState: StateFlow<YoutubeDLDownloadState> =
        _serviceDownloadState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        notificationHelper = DownloadNotificationHelper(this)
        metadataManager = MediaMetadataManager(this)
        streamResolver = YouTubeStreamResolver(this)
        preferencesManager = DownloadPreferencesManager(this)
        cookieStorageHelper = CookieStorageHelper(this)
        downloadManager = YoutubeDLDownloadManager(
            context = this,
            metadataManager = metadataManager,
            streamResolver = streamResolver,
            preferencesManager = preferencesManager
        )

        serviceScope.launch {
            downloadManager.downloadState.collect { state ->
                _serviceDownloadState.value = state
                when (state) {
                    is YoutubeDLDownloadState.Initializing -> {
                        notificationHelper.notifyProgress(
                            title = "Sakura Music",
                            progressPercent = 0,
                            statusLine = state.message,
                            indeterminate = true
                        )
                    }
                    is YoutubeDLDownloadState.FetchingInfo -> {
                        notificationHelper.notifyProgress(
                            title = "Medya Çözümleniyor",
                            progressPercent = 0,
                            statusLine = state.message,
                            indeterminate = true
                        )
                    }
                    is YoutubeDLDownloadState.Downloading -> {
                        notificationHelper.notifyProgress(
                            title = state.title,
                            progressPercent = state.progressPercent.toInt(),
                            statusLine = state.statusLine,
                            indeterminate = false
                        )
                    }
                    is YoutubeDLDownloadState.SavingMetadata -> {
                        notificationHelper.notifyProgress(
                            title = state.title,
                            progressPercent = 99,
                            statusLine = state.message,
                            indeterminate = true
                        )
                    }
                    is YoutubeDLDownloadState.Success -> {
                        notificationHelper.notifyCompleted(
                            title = state.savedMedia.trackInfo.title,
                            artist = state.savedMedia.trackInfo.artist
                        )
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                    is YoutubeDLDownloadState.Error -> {
                        notificationHelper.notifyError(
                            title = "İndirme Hatası",
                            reason = state.message
                        )
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                    is YoutubeDLDownloadState.Cancelled -> {
                        notificationHelper.cancelProgressNotification()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                    is YoutubeDLDownloadState.Idle -> Unit
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForegroundImmediate()
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL).orEmpty()
                val formatName = intent.getStringExtra(EXTRA_FORMAT_NAME)
                val formatOption = DownloadFormatOption.fromName(formatName)
                if (url.isNotBlank()) {
                    startDownloadInternal(url, formatOption)
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                downloadManager.cancelActiveDownload()
                activeDownloadJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun ensureForegroundImmediate() {
        try {
            val notification = notificationHelper.buildProgressNotification(
                title = "Sakura Music İndiriliyor",
                progressPercent = 0,
                statusLine = "Hazırlanıyor...",
                indeterminate = true
            )
            val fgType = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            androidx.core.app.ServiceCompat.startForeground(
                this,
                DownloadNotificationHelper.PROGRESS_NOTIFICATION_ID,
                notification,
                fgType
            )
        } catch (_: Throwable) {
            try {
                startForeground(
                    DownloadNotificationHelper.PROGRESS_NOTIFICATION_ID,
                    notificationHelper.buildProgressNotification(
                        title = "Sakura Music İndiriliyor",
                        progressPercent = 0,
                        statusLine = "Hazırlanıyor...",
                        indeterminate = true
                    )
                )
            } catch (_: Throwable) {
            }
        }
    }

    private fun startDownloadInternal(url: String, formatOption: DownloadFormatOption) {
        ensureForegroundImmediate()

        activeDownloadJob?.cancel()
        activeDownloadJob = serviceScope.launch {
            val (_, savedCookies) = cookieStorageHelper.loadCookies()
            val activeCookies = NetscapeCookieHelper.resolveActiveCookies(applicationContext, savedCookies)
            downloadManager.downloadTrack(
                url = url,
                formatOption = formatOption,
                cookies = activeCookies
            )
        }
    }

    override fun onDestroy() {
        activeDownloadJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_DOWNLOAD = "com.example.downloader.ACTION_START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.example.downloader.ACTION_CANCEL_DOWNLOAD"
        const val EXTRA_URL = "extra_download_url"
        const val EXTRA_FORMAT_NAME = "extra_format_name"

        fun startDownload(
            context: Context,
            url: String,
            formatOption: DownloadFormatOption = DownloadFormatOption.AUDIO_M4A_HIGH
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FORMAT_NAME, formatOption.name)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Throwable) {
                try {
                    context.startService(intent)
                } catch (_: Throwable) {
                }
            }
        }
    }
}
