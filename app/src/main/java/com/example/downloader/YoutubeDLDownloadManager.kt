package com.example.downloader

import android.content.Context
import android.util.Log
import com.example.SakuraMusicApplication
import com.example.model.CookieItem
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class YoutubeDLDownloadManager(
    private val context: Context,
    private val metadataManager: MediaMetadataManager,
    private val streamResolver: YouTubeStreamResolver,
    private val preferencesManager: DownloadPreferencesManager
) {
    private val appContext = context.applicationContext
    private val ytdlpResolver = YTDlpResolver(appContext)
    val downloadEngine = DownloadEngine(parallelConnections = 4)
    private val fallbackChunkDownloader = MultiThreadChunkedDownloader(threadCount = 4)

    private val _downloadState = MutableStateFlow<YoutubeDLDownloadState>(YoutubeDLDownloadState.Idle)
    val downloadState: StateFlow<YoutubeDLDownloadState> = _downloadState.asStateFlow()

    @Volatile
    private var activeProcessId: String? = null
    private val isCancelled = AtomicBoolean(false)

    fun resetState() {
        downloadEngine.reset()
        _downloadState.value = YoutubeDLDownloadState.Idle
    }

    fun cancelActiveDownload(): Boolean {
        isCancelled.set(true)
        downloadEngine.cancel()
        fallbackChunkDownloader.cancel()
        val pid = activeProcessId
        val destroyed = if (!pid.isNullOrBlank()) {
            try {
                YoutubeDL.getInstance().destroyProcessById(pid)
            } catch (_: Throwable) {
                false
            }
        } else {
            false
        }
        activeProcessId = null
        _downloadState.value = YoutubeDLDownloadState.Cancelled()
        return destroyed
    }

    suspend fun downloadTrack(
        url: String,
        formatOption: DownloadFormatOption = DownloadFormatOption.AUDIO_M4A_HIGH,
        cookies: List<CookieItem> = emptyList(),
        existingTrackInfo: MediaTrackInfo? = null,
        onProgressCallback: ((Float, Long, String) -> Unit)? = null
    ): YoutubeDLDownloadState = withContext(Dispatchers.IO) {
        isCancelled.set(false)
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) {
            val err = YoutubeDLDownloadState.Error("İndirilecek bağlantı boş olamaz.")
            _downloadState.value = err
            return@withContext err
        }

        _downloadState.value = YoutubeDLDownloadState.Initializing()
        val engineReady = SakuraMusicApplication.ensureYoutubeDLInitialized(appContext)
        val supportStatus = ytdlpResolver.evaluateUrlSupport(trimmedUrl)

        if (!supportStatus.isSupported) {
            val err = YoutubeDLDownloadState.Error(supportStatus.reasonMessage)
            _downloadState.value = err
            return@withContext err
        }

        _downloadState.value = YoutubeDLDownloadState.FetchingInfo(trimmedUrl)
        val activeCookies = NetscapeCookieHelper.resolveActiveCookies(appContext, cookies)

        val isDirectAudioStream = existingTrackInfo?.streamUrl?.startsWith("http") == true &&
            !YTDlpResolver.isYouTubeUrl(existingTrackInfo.streamUrl) &&
            !existingTrackInfo.streamUrl.contains("youtube.com") &&
            !existingTrackInfo.streamUrl.contains("youtu.be")

        val isPlaceholderExistingTitle = existingTrackInfo == null ||
            existingTrackInfo.title.isBlank() ||
            existingTrackInfo.title.startsWith("Parça ") ||
            existingTrackInfo.title == existingTrackInfo.id ||
            existingTrackInfo.artist == "YouTube Playlist"

        val resolvedInfo: MediaTrackInfo = if (existingTrackInfo != null &&
            !isPlaceholderExistingTitle &&
            isDirectAudioStream
        ) {
            val effPlaylist = existingTrackInfo.effectivePlaylistName()
            if (effPlaylist != null) {
                existingTrackInfo.copy(
                    album = effPlaylist,
                    playlistTitle = effPlaylist
                )
            } else {
                existingTrackInfo
            }
        } else {
            when (val res = streamResolver.resolveInput(trimmedUrl, activeCookies)) {
                is StreamResolutionResult.Success -> {
                    val fresh = res.trackInfo
                    val bestTitle = if (fresh.title.isNotBlank() &&
                        !fresh.title.startsWith("Parça ") &&
                        fresh.title != "Bilinmeyen Şarkı"
                    ) {
                        fresh.title
                    } else if (existingTrackInfo != null && !isPlaceholderExistingTitle) {
                        existingTrackInfo.title
                    } else {
                        fresh.title
                    }

                    val bestArtist = if (fresh.artist.isNotBlank() &&
                        fresh.artist != "YouTube Playlist" &&
                        fresh.artist != "Bilinmeyen Sanatçı"
                    ) {
                        fresh.artist
                    } else if (existingTrackInfo != null &&
                        existingTrackInfo.artist.isNotBlank() &&
                        existingTrackInfo.artist != "YouTube Playlist"
                    ) {
                        existingTrackInfo.artist
                    } else {
                        fresh.artist
                    }

                    val preservedPlaylistTitle = existingTrackInfo?.effectivePlaylistName()
                        ?: fresh.effectivePlaylistName()
                    val preservedPlaylistId = existingTrackInfo?.playlistId?.takeIf { it.isNotBlank() }
                        ?: fresh.playlistId
                    val preservedPlaylistCoverUrl = existingTrackInfo?.playlistCoverUrl?.takeIf { it.isNotBlank() }
                        ?: fresh.playlistCoverUrl
                    val bestAlbum = preservedPlaylistTitle
                        ?: existingTrackInfo?.album?.takeIf { MediaTrackInfo.isMeaningfulPlaylistName(it) }
                        ?: fresh.album.takeIf { it.isNotBlank() }
                        ?: existingTrackInfo?.album?.takeIf { it.isNotBlank() }
                        ?: "Sakura Music"

                    fresh.copy(
                        title = bestTitle,
                        artist = bestArtist,
                        album = bestAlbum,
                        playlistId = preservedPlaylistId,
                        playlistTitle = preservedPlaylistTitle,
                        playlistCoverUrl = preservedPlaylistCoverUrl
                    )
                }
                is StreamResolutionResult.Error -> {
                    existingTrackInfo ?: run {
                        val err = YoutubeDLDownloadState.Error(
                            message = res.message
                        )
                        _downloadState.value = err
                        return@withContext err
                    }
                }
            }
        }

        if (isCancelled.get()) {
            val cancelled = YoutubeDLDownloadState.Cancelled()
            _downloadState.value = cancelled
            return@withContext cancelled
        }

        // Reuse existing valid audio file on disk if already downloaded (prevents accidental overwrite & speeds up resume)
        val existingSavedOnDisk = metadataManager.findAndAssociateExistingTrackIfPresent(resolvedInfo)
        if (existingSavedOnDisk != null &&
            existingSavedOnDisk.audioFile.extension.equals(formatOption.expectedExtension, ignoreCase = true)
        ) {
            val successState = YoutubeDLDownloadState.Success(existingSavedOnDisk)
            _downloadState.value = successState
            return@withContext successState
        }

        val isDirectStreamOnly = supportStatus.platform == MediaSourcePlatform.DIRECT_MEDIA
        val isSoundCloudOrVimeo = supportStatus.platform == MediaSourcePlatform.SOUNDCLOUD ||
            supportStatus.platform == MediaSourcePlatform.VIMEO

        val musicDir = metadataManager.getAppMusicDirectory()
        val baseFileName = metadataManager.buildCollisionSafeBaseFileName(resolvedInfo)
        cleanupIncompleteArtifacts(musicDir, baseFileName)

        val processId = "sakura_dl_${UUID.randomUUID().toString().take(8)}"
        activeProcessId = processId

        var downloadedFile: File? = null
        var ytdlpErrorDetails: String? = null

        val prefersFast4SegmentAudio = !isSoundCloudOrVimeo &&
            (formatOption == DownloadFormatOption.AUDIO_M4A_HIGH || formatOption == DownloadFormatOption.AUDIO_OPUS_BEST || isDirectStreamOnly)

        if (prefersFast4SegmentAudio) {
            val fastStreamUrl = if (resolvedInfo.streamUrl.startsWith("http") &&
                (!YTDlpResolver.isYouTubeUrl(resolvedInfo.streamUrl) || resolvedInfo.streamUrl.contains("googlevideo.com"))
            ) {
                resolvedInfo.streamUrl
            } else {
                when (val refreshed = streamResolver.resolveVideoById(resolvedInfo.id, activeCookies)) {
                    is StreamResolutionResult.Success -> refreshed.trackInfo.streamUrl
                    is StreamResolutionResult.Error -> ""
                }
            }

            if (fastStreamUrl.startsWith("http")) {
                val ext = when {
                    resolvedInfo.mimeType.contains("ogg") || resolvedInfo.mimeType.contains("opus") -> "opus"
                    resolvedInfo.mimeType.contains("mpeg") -> "mp3"
                    else -> "m4a"
                }
                val fastDest = File(musicDir, "$baseFileName.$ext")
                val cookieHeader = NetscapeCookieHelper.buildCookieHeaderString(activeCookies, appContext)

                val fastRes = runCatching {
                    downloadEngine.writeStreamToDisk(
                        streamUrl = fastStreamUrl,
                        destinationFile = fastDest,
                        expectedBytes = resolvedInfo.contentLengthBytes,
                        rawCookieHeader = cookieHeader,
                        onProgress = { downloadedBytes, totalBytes, speedBytesPerSec ->
                            val pct = if (totalBytes > 0L) {
                                (downloadedBytes.toFloat() / totalBytes.toFloat() * 100f).coerceIn(0f, 100f)
                            } else 0f
                            val eta = if (speedBytesPerSec > 0L && totalBytes > downloadedBytes) {
                                (totalBytes - downloadedBytes) / speedBytesPerSec
                            } else 0L
                            val speedKb = speedBytesPerSec / 1024L
                            val statusMsg = "DownloadEngine • %${pct.toInt()} ($speedKb KB/sn)"
                            _downloadState.value = YoutubeDLDownloadState.Downloading(
                                processId = processId,
                                title = resolvedInfo.title,
                                progressPercent = pct,
                                etaSeconds = eta,
                                statusLine = statusMsg,
                                formatOption = formatOption
                            )
                            onProgressCallback?.invoke(pct, eta, statusMsg)
                        }
                    )
                }
                val candidate = fastRes.getOrNull()
                if (candidate != null && metadataManager.isValidAudioFile(candidate)) {
                    downloadedFile = candidate
                }
            }
        }

        if ((downloadedFile == null || !metadataManager.isValidAudioFile(downloadedFile)) && engineReady && !isDirectStreamOnly) {
            try {
                downloadedFile = executeYtdlnisRequest(
                    targetUrl = resolvedInfo.sourceUrl.ifBlank { trimmedUrl },
                    baseFileName = baseFileName,
                    musicDir = musicDir,
                    trackInfo = resolvedInfo,
                    formatOption = formatOption,
                    cookies = activeCookies,
                    processId = processId,
                    onProgressCallback = onProgressCallback
                )
            } catch (cancelledEx: YoutubeDL.CanceledException) {
                activeProcessId = null
                cleanupIncompleteArtifacts(musicDir, baseFileName)
                val cancelled = YoutubeDLDownloadState.Cancelled()
                _downloadState.value = cancelled
                return@withContext cancelled
            } catch (ce: CancellationException) {
                activeProcessId = null
                cleanupIncompleteArtifacts(musicDir, baseFileName)
                throw ce
            } catch (t: Throwable) {
                ytdlpErrorDetails = t.localizedMessage ?: t.javaClass.simpleName
                Log.w(TAG, "yt-dlp execution failed for $trimmedUrl: $ytdlpErrorDetails")
            }
        }

        if (isCancelled.get()) {
            activeProcessId = null
            cleanupIncompleteArtifacts(musicDir, baseFileName)
            val cancelled = YoutubeDLDownloadState.Cancelled()
            _downloadState.value = cancelled
            return@withContext cancelled
        }

        if (downloadedFile == null || !metadataManager.isValidAudioFile(downloadedFile)) {
            if (isSoundCloudOrVimeo) {
                activeProcessId = null
                val platformLabel = supportStatus.platform.displayName
                val err = YoutubeDLDownloadState.Error(
                    message = "$platformLabel üzerinden ses dosyası indirilemedi.",
                    technicalDetails = ytdlpErrorDetails ?: "yt-dlp geçerli bir ses dosyası üretemedi."
                )
                _downloadState.value = err
                return@withContext err
            }

            val directStreamUrl = if (resolvedInfo.streamUrl.startsWith("http") &&
                (!YTDlpResolver.isYouTubeUrl(resolvedInfo.streamUrl) || resolvedInfo.streamUrl.contains("googlevideo.com"))
            ) {
                resolvedInfo.streamUrl
            } else {
                when (val refreshed = streamResolver.resolveVideoById(resolvedInfo.id, activeCookies)) {
                    is StreamResolutionResult.Success -> refreshed.trackInfo.streamUrl
                    is StreamResolutionResult.Error -> ""
                }
            }

            if (directStreamUrl.startsWith("http")) {
                val ext = when {
                    formatOption == DownloadFormatOption.VIDEO_MP4_1080P -> "mp4"
                    resolvedInfo.mimeType.contains("mpeg") -> "mp3"
                    resolvedInfo.mimeType.contains("ogg") || resolvedInfo.mimeType.contains("opus") -> "opus"
                    else -> "m4a"
                }
                val fallbackDest = File(musicDir, "$baseFileName.$ext")
                val cookieHeader = NetscapeCookieHelper.buildCookieHeaderString(activeCookies, appContext)

                val fallbackRes = fallbackChunkDownloader.downloadFileSuspend(
                    fileUrl = directStreamUrl,
                    destinationFile = fallbackDest,
                    cookieHeader = cookieHeader,
                    listener = object : DownloadListener {
                        override fun onProgress(progress: DownloadProgress) {
                            val pct = (progress.overallFraction * 100f).coerceIn(0f, 100f)
                            val eta = if (progress.speedBytesPerSec > 0 && progress.totalBytes > progress.downloadedBytes) {
                                (progress.totalBytes - progress.downloadedBytes) / progress.speedBytesPerSec
                            } else 0L
                            _downloadState.value = YoutubeDLDownloadState.Downloading(
                                processId = processId,
                                title = resolvedInfo.title,
                                progressPercent = pct,
                                etaSeconds = eta,
                                statusLine = progress.statusMessage,
                                formatOption = formatOption
                            )
                            onProgressCallback?.invoke(pct, eta, progress.statusMessage)
                        }

                        override fun onComplete(outputFile: File) = Unit
                        override fun onError(errorMessage: String) = Unit
                    }
                )
                downloadedFile = fallbackRes.getOrNull()
                if (fallbackRes.isFailure && ytdlpErrorDetails == null) {
                    ytdlpErrorDetails = fallbackRes.exceptionOrNull()?.localizedMessage
                }
            }
        }

        activeProcessId = null

        if (isCancelled.get()) {
            cleanupIncompleteArtifacts(musicDir, baseFileName)
            val cancelled = YoutubeDLDownloadState.Cancelled()
            _downloadState.value = cancelled
            return@withContext cancelled
        }

        if (downloadedFile == null || !metadataManager.isValidAudioFile(downloadedFile)) {
            cleanupIncompleteArtifacts(musicDir, baseFileName)
            val err = YoutubeDLDownloadState.Error(
                message = "Gerçek ses dosyası indirilemedi veya doğrulamadan geçemedi.",
                technicalDetails = ytdlpErrorDetails
            )
            _downloadState.value = err
            return@withContext err
        }

        _downloadState.value = YoutubeDLDownloadState.SavingMetadata(
            title = resolvedInfo.title,
            message = "Ses dosyası, kapak görseli ve metadata kaydediliyor..."
        )

        val saveResult = metadataManager.finalizeDownloadedMedia(
            downloadedFile = downloadedFile,
            trackInfo = resolvedInfo,
            callback = object : MediaSaveCallback {
                override fun onStageChanged(stageMessage: String) {
                    _downloadState.value = YoutubeDLDownloadState.SavingMetadata(
                        title = resolvedInfo.title,
                        message = stageMessage
                    )
                }

                override fun onSaved(result: SavedMediaResult) = Unit
                override fun onSaveError(errorMessage: String) = Unit
            }
        )

        return@withContext saveResult.fold(
            onSuccess = { saved ->
                val successState = YoutubeDLDownloadState.Success(saved)
                _downloadState.value = successState
                successState
            },
            onFailure = { err ->
                val errorState = YoutubeDLDownloadState.Error(
                    message = err.localizedMessage ?: "Dosya kaydedilirken hata oluştu.",
                    technicalDetails = ytdlpErrorDetails
                )
                _downloadState.value = errorState
                errorState
            }
        )
    }

    private fun executeYtdlnisRequest(
        targetUrl: String,
        baseFileName: String,
        musicDir: File,
        trackInfo: MediaTrackInfo,
        formatOption: DownloadFormatOption,
        cookies: List<CookieItem>,
        processId: String,
        onProgressCallback: ((Float, Long, String) -> Unit)?
    ): File? {
        val outputTemplate = File(musicDir, "$baseFileName.%(ext)s").absolutePath
        val request = YoutubeDLRequest(targetUrl).apply {
            addOption("--newline")
            addOption("--no-playlist")
            addOption("--ignore-config")
            addOption("--no-mtime")
            addOption("--socket-timeout", "25")
            addOption("-N", preferencesManager.concurrentFragments.coerceIn(1, 16).toString())
            addOption("-f", formatOption.formatSelector)

            if (formatOption.isAudioExtraction) {
                if (SakuraMusicApplication.isFFmpegInitialized) {
                    addOption("-x")
                    formatOption.audioFormatArg?.let { addOption("--audio-format", it) }
                    formatOption.audioQualityArg?.let { addOption("--audio-quality", it) }
                }
            } else if (SakuraMusicApplication.isFFmpegInitialized) {
                addOption("--merge-output-format", formatOption.expectedExtension)
            }

            if (preferencesManager.embedMetadataAndThumb && SakuraMusicApplication.isFFmpegInitialized) {
                addOption("--embed-metadata")
                addOption("--write-thumbnail")
                addOption("--convert-thumbnails", "jpg")
            }

            if (preferencesManager.useSponsorBlock && YTDlpResolver.isYouTubeUrl(targetUrl)) {
                val cats = preferencesManager.sponsorBlockCategories.trim()
                if (cats.isNotEmpty() && SakuraMusicApplication.isFFmpegInitialized) {
                    addOption("--sponsorblock-remove", cats)
                }
            }

            if (preferencesManager.useAria2c && SakuraMusicApplication.isAria2cInitialized) {
                addOption("--downloader", "libaria2c.so")
                addOption("--external-downloader-args", "aria2c:\"-x 8 -s 8 -k 1M\"")
            }

            if (preferencesManager.writeAutoSubtitlesLrc) {
                addOption("--write-subs")
                addOption("--write-auto-subs")
                addOption("--sub-langs", "tr.*,en.*")
                if (SakuraMusicApplication.isFFmpegInitialized) {
                    addOption("--convert-subs", "lrc")
                }
            }

            NetscapeCookieHelper.writeNetscapeCookieFile(appContext, cookies)?.let { cookieFile ->
                addOption("--cookies", cookieFile.absolutePath)
            }

            addOption("-o", outputTemplate)
        }

        _downloadState.value = YoutubeDLDownloadState.Downloading(
            processId = processId,
            title = trackInfo.title,
            progressPercent = 0f,
            etaSeconds = 0L,
            statusLine = "yt-dlp indirme işlemi başlatıldı...",
            formatOption = formatOption
        )

        YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
            val safeProgress = progress.coerceIn(0f, 100f)
            val safeEta = etaInSeconds.coerceAtLeast(0L)
            val statusText = line?.trim()?.takeIf { it.isNotEmpty() }
                ?: "İndiriliyor... %${safeProgress.toInt()}"

            _downloadState.value = YoutubeDLDownloadState.Downloading(
                processId = processId,
                title = trackInfo.title,
                progressPercent = safeProgress,
                etaSeconds = safeEta,
                statusLine = statusText,
                formatOption = formatOption
            )
            onProgressCallback?.invoke(safeProgress, safeEta, statusText)
        }

        return locateCompletedOutputFile(musicDir, baseFileName, formatOption.expectedExtension)
    }

    private fun locateCompletedOutputFile(
        musicDir: File,
        baseFileName: String,
        preferredExt: String
    ): File? {
        val exact = File(musicDir, "$baseFileName.$preferredExt")
        if (metadataManager.isValidAudioFile(exact)) {
            return exact
        }

        val allowedExts = setOf("m4a", "mp3", "opus", "ogg", "aac", "wav", "flac", "mp4", "webm", "mkv")
        return musicDir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.nameWithoutExtension == baseFileName &&
                    file.extension.lowercase() in allowedExts &&
                    !file.name.endsWith(".part", ignoreCase = true) &&
                    !file.name.endsWith(".ytdl", ignoreCase = true) &&
                    metadataManager.isValidAudioFile(file)
            }
            ?.maxByOrNull { it.length() }
    }

    private fun cleanupIncompleteArtifacts(musicDir: File, baseFileName: String) {
        try {
            musicDir.listFiles()?.forEach { f ->
                if (f.name.startsWith(baseFileName) &&
                    (f.name.endsWith(".part") || f.name.endsWith(".ytdl") || f.name.endsWith(".tmp"))
                ) {
                    f.delete()
                }
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "YoutubeDLDownloadMgr"
    }
}
