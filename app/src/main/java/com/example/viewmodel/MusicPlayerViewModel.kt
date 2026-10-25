package com.example.viewmodel

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SakuraMusicApplication
import com.example.data.AnimationPreferencesManager
import com.example.data.CookieStorageHelper
import com.example.data.EffectIntensityLevel
import com.example.data.OfflinePlaylistRepository
import com.example.data.PetalDensityLevel
import com.example.data.PlayCountManager
import com.example.data.SakuraAnimationConfig
import com.example.data.SakuraAnimationProfile
import com.example.data.SakuraTreeForm
import com.example.data.SakuraTreeStyle
import com.example.data.ThemeStorageHelper
import com.example.data.TopPlayedItem
import com.example.data.TransitionSpeedLevel
import com.example.data.UserPlaylist
import com.example.downloader.DownloadFormatOption
import com.example.downloader.LikedSongsSourceType
import com.example.downloader.DownloadListener
import com.example.downloader.DownloadPreferencesManager
import com.example.downloader.DownloadProgress
import com.example.downloader.LyricsProvider
import com.example.downloader.LyricsTranslator
import com.example.downloader.MediaMetadataManager
import com.example.downloader.MediaTrackInfo
import com.example.downloader.MultiThreadChunkedDownloader
import com.example.downloader.NetscapeCookieHelper
import com.example.downloader.PlatformSupportStatus
import com.example.downloader.SavedMediaResult
import com.example.downloader.StreamResolutionResult
import com.example.downloader.YTDlpResolver
import com.example.downloader.YouTubeStreamResolver
import com.example.downloader.YoutubeDLDownloadManager
import com.example.downloader.YoutubeDLDownloadState
import com.example.downloader.YoutubeDLUpdateResult
import com.example.downloader.YoutubeDLUpdater
import com.example.model.LrcParser
import com.example.model.LyricLine
import com.example.player.LocalDownloadsPlayer
import com.example.player.PlayerState
import com.example.sync.AutoSyncScheduler
import com.example.sync.SubscriptionSource
import com.example.sync.SubscriptionStorage
import com.example.ui.theme.AppTheme
import com.example.vault.VaultItem
import com.example.vault.VaultRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import com.example.downloader.LyricsSource
import com.example.downloader.TranslationEngine
import kotlinx.coroutines.CoroutineExceptionHandler

enum class YtMusicTab {
    HOME,
    SEARCH,
    LIBRARY,
    PLAYLISTS,
    VAULT,
    COOKIES,
    SETTINGS
}

data class MusicUiState(
    val selectedTab: YtMusicTab = YtMusicTab.HOME,
    val selectedTheme: AppTheme = AppTheme.SAKURA_DARK,
    val animationConfig: SakuraAnimationConfig = SakuraAnimationConfig(),
    val youtubeUrlInput: String = "",
    val isResolvingStream: Boolean = false,
    val isFetchingLyrics: Boolean = false,
    val currentTrack: MediaTrackInfo? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val downloadProgress: DownloadProgress = DownloadProgress(),
    val youtubeDLState: YoutubeDLDownloadState = YoutubeDLDownloadState.Idle,
    val selectedDownloadFormat: DownloadFormatOption = DownloadFormatOption.AUDIO_M4A_HIGH,
    val isUpdatingYoutubeDL: Boolean = false,
    val youtubeDLVersion: String = "Yükleniyor...",
    val isYtdlpEngineReady: Boolean = false,
    val isFFmpegReady: Boolean = false,
    val isAria2cReady: Boolean = false,
    val currentUrlSupportStatus: PlatformSupportStatus? = null,
    val useAria2c: Boolean = false,
    val useSponsorBlock: Boolean = false,
    val embedMetadataAndThumb: Boolean = true,
    val writeAutoSubtitlesLrc: Boolean = true,
    val concurrentFragments: Int = 4,
    val lastSavedMedia: SavedMediaResult? = null,
    val downloadedTracks: List<SavedMediaResult> = emptyList(),
    val playlists: List<UserPlaylist> = emptyList(),
    val selectedPlaylistId: String? = null,
    val isPlaylistsGridView: Boolean = false,
    val searchQuery: String = "",
    val isSearchingCatalog: Boolean = false,
    val catalogSearchResults: List<MediaTrackInfo> = emptyList(),
    val resolvedPlaylistTitle: String? = null,
    val resolvedPlaylistSourceId: String? = null,
    val resolvedPlaylistCoverUrl: String? = null,
    val resolvedPlaylistTracks: List<MediaTrackInfo> = emptyList(),
    val selectedPlaylistTrackIds: Set<String> = emptySet(),
    val activeLikedSongsSource: LikedSongsSourceType = LikedSongsSourceType.YOUTUBE_MUSIC,
    val isBatchDownloadingPlaylist: Boolean = false,
    val batchDownloadCompletedCount: Int = 0,
    val batchDownloadTotalCount: Int = 0,
    val downloadQueue: List<MediaTrackInfo> = emptyList(),
    val currentDownloadingTrack: MediaTrackInfo? = null,
    val lrcRawText: String = LrcParser.SAMPLE_LRC,
    val parsedLyrics: List<LyricLine> = LrcParser.parse(LrcParser.SAMPLE_LRC),
    val activeLyricIndex: Int = -1,
    val isNowPlayingExpanded: Boolean = false,
    val statusBannerMessage: String? = null,
    val errorMessage: String? = null,
    val vaultItems: List<VaultItem> = emptyList(),
    val activeVaultItemForPlayback: VaultItem? = null,
    val subscriptions: List<SubscriptionSource> = emptyList(),
    val isAutoSyncWifiOnly: Boolean = true,
    val autoSyncIntervalHours: Long = 6L,
    val autoSyncIntervalMinutes: Long = 360L,
    val selectedTreeStyle: SakuraTreeStyle = SakuraTreeStyle.TRADITIONAL_SAKURA,
    val selectedTreeForm: SakuraTreeForm = SakuraTreeForm.CLASSIC_SWEEPING,
    val autoTranslateLyricsToTurkish: Boolean = true,
    val translationEngine: TranslationEngine = TranslationEngine.GOOGLE_TRANSLATE,
    val lyricsSourceOrder: List<LyricsSource> = LyricsSource.DEFAULT_ORDER,
    val enabledLyricsSources: Set<LyricsSource> = LyricsSource.DEFAULT_ORDER.toSet(),
    val activeLyricsSourceLabel: String? = null,
    val selectedAppIcon: com.example.data.AppIconOption = com.example.data.AppIconOption.DEFAULT,
    val volumeBoostPercent: Int = 100,
    val isVolumeBoosterEnabled: Boolean = false,
    val featureToggles: com.example.data.FeatureTogglesConfig = com.example.data.FeatureTogglesConfig(),
    val studioDspConfig: com.example.player.StudioDspConfig = com.example.player.StudioDspConfig(),
    val abLoopPointAMs: Long? = null,
    val abLoopPointBMs: Long? = null,
    val isBatchFetchingLyrics: Boolean = false,
    val batchLyricsCompletedCount: Int = 0,
    val batchLyricsTotalCount: Int = 0,
    val isLikedSongsWifiAutoSyncEnabled: Boolean = true,
    val isDownloadQueueCenterOpen: Boolean = false,
    val isDownloadQueuePaused: Boolean = false,
    val failedQueueTracks: List<MediaTrackInfo> = emptyList(),
    val remoteLibraryPlaylists: List<com.example.downloader.RemotePlaylistSummary> = emptyList(),
    val isLoadingRemoteLibraryPlaylists: Boolean = false,
    val activeIntervalCountdownSeconds: Int = 0
)

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val themeStorage = ThemeStorageHelper(appContext)
    private val animationPrefs = AnimationPreferencesManager(appContext)
    private val cookieStorage = CookieStorageHelper(appContext)
    private val metadataManager = MediaMetadataManager(appContext)
    private val ytdlpResolver = YTDlpResolver(appContext)
    private val streamResolver = YouTubeStreamResolver(appContext)
    private val downloadPrefs = DownloadPreferencesManager(appContext)
    private val youtubeDLDownloadManager = YoutubeDLDownloadManager(
        context = appContext,
        metadataManager = metadataManager,
        streamResolver = streamResolver,
        preferencesManager = downloadPrefs
    )
    private val youtubeDLUpdater = YoutubeDLUpdater(appContext)
    private val chunkedDownloader = MultiThreadChunkedDownloader(threadCount = 4)
    private val playlistRepository = OfflinePlaylistRepository(appContext)
    private val vaultRepository = VaultRepository(appContext)
    private val subscriptionStorage = SubscriptionStorage(appContext)
    val playCountManager = PlayCountManager(appContext)
    val localDownloadsPlayer = LocalDownloadsPlayer(appContext)

    private var onlineMediaPlayer: MediaPlayer? = null
    private var onlineProgressJob: Job? = null
    private var catalogSearchJob: Job? = null
    private var initJob: Job? = null
    private var activeDownloadJob: Job? = null
    private var activeStreamResolutionJob: Job? = null
    private var activeLyricsJob: Job? = null
    private var batchLyricsJob: Job? = null
    private var prewarmNextTrackJob: Job? = null
    private var refreshTracksJob: Job? = null
    private var lastTracksRefreshUptimeMs: Long = 0L
    private var lastObservedTrackId: String? = null
    private var countedPlayForTrackId: String? = null
    private var lastOnlineTickUptimeMs: Long = 0L
    private var lastOfflineTickUptimeMs: Long = 0L
    private val cancelledOrRemovedQueueTrackIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private fun checkAndRecordPlayCount(trackId: String, currentPositionMs: Long) {
        if (trackId.isBlank()) return
        if (currentPositionMs >= 3_000L && countedPlayForTrackId != trackId) {
            countedPlayForTrackId = trackId
            playCountManager.recordPlay(trackId)
        }
    }

    private val volumeBoostStorage = com.example.data.VolumeBoostStorageHelper(appContext)
    private val featureTogglesStorage = com.example.data.FeatureTogglesStorageHelper(appContext)
    private val studioDspStorage = com.example.player.StudioDspStorageHelper(appContext)

    private val vmExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("MusicPlayerVM", "Unhandled coroutine exception: ${throwable.message}", throwable)
    }

    // Cold Start Optimizasyonu: Main Thread üzerinde hiçbir SharedPreferences veya disk okuması yapılmaz.
    // Tüm başlangıç tercihleri ve Room veritabanı önbelleği Dispatchers.IO üzerinde asenkron yüklenir.
    private val _uiState = MutableStateFlow(
        MusicUiState(
            selectedAppIcon = com.example.data.AppIconManager.loadSelectedIcon(appContext)
        )
    )
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    fun setPlaylistsGridView(isGrid: Boolean) {
        downloadPrefs.isPlaylistsGridLayout = isGrid
        _uiState.update { it.copy(isPlaylistsGridView = isGrid) }
    }

    fun updateFeatureToggles(transform: (com.example.data.FeatureTogglesConfig) -> com.example.data.FeatureTogglesConfig) {
        val oldToggles = _uiState.value.featureToggles
        val updated = transform(oldToggles)
        featureTogglesStorage.saveConfig(updated)

        var dsp = _uiState.value.studioDspConfig
        var dspChanged = false
        if (updated.equalizerEnabled && !oldToggles.equalizerEnabled && !dsp.isEqualizerActive) {
            dsp = dsp.copy(isEqualizerActive = true)
            dspChanged = true
        }
        if (updated.pitchAndReverbEnabled && !oldToggles.pitchAndReverbEnabled && !dsp.isPitchReverbActive) {
            dsp = if (dsp.pitchReverbPreset == "Normal") {
                dsp.copy(
                    isPitchReverbActive = true,
                    pitchReverbPreset = "Slowed + Reverb",
                    tempoSpeed = 0.84f,
                    pitchFactor = 0.86f,
                    reverbLevel = 3
                )
            } else {
                dsp.copy(isPitchReverbActive = true)
            }
            dspChanged = true
        }
        if (updated.spatial8DAudioEnabled && !oldToggles.spatial8DAudioEnabled && !dsp.isSpatial8DActive) {
            dsp = dsp.copy(isSpatial8DActive = true)
            dspChanged = true
        }
        if (updated.vocalReducerEnabled && !oldToggles.vocalReducerEnabled && !dsp.isVocalReducerActive) {
            dsp = dsp.copy(isVocalReducerActive = true)
            dspChanged = true
        }
        if (updated.ambientMixerEnabled && !oldToggles.ambientMixerEnabled && !dsp.isAmbientMixerActive) {
            dsp = dsp.copy(isAmbientMixerActive = true)
            dspChanged = true
        }
        if (dspChanged) {
            studioDspStorage.saveConfig(dsp)
        }

        _uiState.update { state ->
            state.copy(
                featureToggles = updated,
                studioDspConfig = dsp,
                abLoopPointAMs = if (!updated.abLoopEnabled) null else state.abLoopPointAMs,
                abLoopPointBMs = if (!updated.abLoopEnabled) null else state.abLoopPointBMs
            )
        }
        com.example.player.StudioDspController.updateState(dsp, updated)
    }

    fun setGriffithQuotesEnabled(enabled: Boolean) {
        updateFeatureToggles { it.copy(griffithQuotesEnabled = enabled) }
    }

    fun setGriffithFalconEmblemEnabled(enabled: Boolean) {
        updateFeatureToggles { it.copy(griffithFalconEmblemEnabled = enabled) }
    }

    fun setGriffithFlyingFalconEnabled(enabled: Boolean) {
        updateFeatureToggles { it.copy(griffithFlyingFalconEnabled = enabled) }
    }

    fun setGriffithRandomLinesEnabled(enabled: Boolean) {
        updateFeatureToggles { it.copy(griffithRandomLinesEnabled = enabled) }
    }

    fun setDownloadIntervalDelaySeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, 120)
        updateFeatureToggles { it.copy(downloadIntervalDelaySeconds = clamped) }
        _uiState.update {
            it.copy(
                statusBannerMessage = if (clamped == 0) {
                    "Aralıklı indirme bekleme süresi kapatıldı (Anında indirme)."
                } else {
                    "Şarkılar arası bekleme süresi $clamped saniye olarak ayarlandı."
                }
            )
        }
    }

    fun updateCowboyBebopConfig(
        transform: (com.example.data.CowboyBebopThemeConfig) -> com.example.data.CowboyBebopThemeConfig
    ) {
        val current = _uiState.value.featureToggles.cowboyBebopConfig
        val updatedBebop = transform(current).validated()
        featureTogglesStorage.saveCowboyBebopConfig(updatedBebop)
        _uiState.update { state ->
            state.copy(
                featureToggles = state.featureToggles.copy(cowboyBebopConfig = updatedBebop)
            )
        }
    }

    fun applyCowboyBebopPreset(profile: com.example.data.BebopPresetProfile) {
        val presetConfig = com.example.data.CowboyBebopThemeConfig.fromPreset(profile)
        featureTogglesStorage.saveCowboyBebopConfig(presetConfig)
        _uiState.update { state ->
            state.copy(
                featureToggles = state.featureToggles.copy(cowboyBebopConfig = presetConfig),
                statusBannerMessage = "Cowboy Bebop '${profile.displayName}' profili uygulandı. Ayarları bağımsız olarak düzenleyebilirsiniz."
            )
        }
    }

    fun resetCowboyBebopConfigToDefaults() {
        val defaults = featureTogglesStorage.resetCowboyBebopConfigToDefaults()
        _uiState.update { state ->
            state.copy(
                featureToggles = state.featureToggles.copy(cowboyBebopConfig = defaults),
                statusBannerMessage = "Cowboy Bebop özelleştirme ayarları varsayılanlara döndürüldü."
            )
        }
    }

    fun openDownloadQueueCenter() {
        _uiState.update { it.copy(isDownloadQueueCenterOpen = true) }
    }

    fun closeDownloadQueueCenter() {
        _uiState.update { it.copy(isDownloadQueueCenterOpen = false) }
    }

    fun updateStudioDspConfig(transform: (com.example.player.StudioDspConfig) -> com.example.player.StudioDspConfig) {
        val updated = transform(_uiState.value.studioDspConfig)
        studioDspStorage.saveConfig(updated)
        _uiState.update { it.copy(studioDspConfig = updated) }
        com.example.player.StudioDspController.updateState(updated, _uiState.value.featureToggles)
    }

    fun setAbLoopPointA() {
        val pos = _uiState.value.currentPositionMs.coerceAtLeast(0L)
        _uiState.update {
            it.copy(
                abLoopPointAMs = pos,
                abLoopPointBMs = if (it.abLoopPointBMs != null && it.abLoopPointBMs <= pos) null else it.abLoopPointBMs,
                statusBannerMessage = "A-B Döngüsü A noktası ayarlandı (${LrcParser.formatTimestamp(pos)})."
            )
        }
    }

    fun setAbLoopPointB() {
        val pointA = _uiState.value.abLoopPointAMs ?: return
        val pos = _uiState.value.currentPositionMs.coerceAtLeast(pointA + 1000L)
        _uiState.update {
            it.copy(
                abLoopPointBMs = pos,
                statusBannerMessage = "A-B Döngüsü aktif: ${LrcParser.formatTimestamp(pointA)} - ${LrcParser.formatTimestamp(pos)}"
            )
        }
    }

    fun clearAbLoop() {
        _uiState.update {
            it.copy(
                abLoopPointAMs = null,
                abLoopPointBMs = null,
                statusBannerMessage = "A-B Nakarat Döngüsü temizlendi."
            )
        }
    }

    fun setVolumeBoost(percent: Int, enabled: Boolean) {
        val effectivePercent = if (enabled && percent <= 100) 160 else percent.coerceIn(100, 300)
        volumeBoostStorage.saveVolumeBoostPercent(effectivePercent)
        volumeBoostStorage.saveVolumeBoosterEnabled(enabled)

        _uiState.update {
            it.copy(
                volumeBoostPercent = effectivePercent,
                isVolumeBoosterEnabled = enabled,
                statusBannerMessage = if (enabled) "Ses yükseltici +${effectivePercent - 100}% ($effectivePercent%) olarak aktif edildi." else "Ses yükseltici kapatıldı."
            )
        }

        com.example.player.StudioDspController.updateVolumeBoostState(effectivePercent, enabled)
        onlineMediaPlayer?.audioSessionId?.let { session ->
            com.example.player.AudioEffectManager.applyOnlineVolumeBoost(session, effectivePercent, enabled)
        }
        localDownloadsPlayer.setVolumeBoost(effectivePercent, enabled)
    }

    fun selectAppIcon(option: com.example.data.AppIconOption) {
        com.example.data.AppIconManager.applyAppIcon(appContext, option)
        _uiState.update {
            it.copy(
                selectedAppIcon = option,
                statusBannerMessage = "Uygulama simgesi '${option.displayName}' olarak değiştirildi."
            )
        }
    }

    val offlinePlayerState: StateFlow<PlayerState> = localDownloadsPlayer.playerState
    val fftMagnitudes: StateFlow<FloatArray> = localDownloadsPlayer.fftMagnitudes

    init {
        // Sıralı (Staggered) Başlangıç Yükleme Hattı: Tüm I/O işlemleri Dispatchers.IO üzerinde çalışır
        initJob = viewModelScope.launch(Dispatchers.IO) {
            // 0. Aşama: Tercihleri ve Room Database içindeki yerel şarkı önbelleğini anında yükle (Sıfır disk tarama gecikmesi)
            val loadedTheme = themeStorage.loadTheme()
            val loadedTreeStyle = themeStorage.loadTreeStyle()
            val loadedTreeForm = themeStorage.loadTreeForm()
            val loadedAnim = animationPrefs.loadConfig()
            val loadedSubs = subscriptionStorage.loadSubscriptions()
            val loadedIcon = com.example.data.AppIconManager.loadSelectedIcon(appContext)
            com.example.data.AppIconManager.ensureValidLauncherState(appContext)
            val loadedBoostPct = volumeBoostStorage.getVolumeBoostPercent()
            val loadedBoostEnabled = volumeBoostStorage.isVolumeBoosterEnabled()
            val loadedToggles = featureTogglesStorage.loadConfig()
            val loadedDsp = studioDspStorage.loadConfig()
            val roomTracks = metadataManager.loadCachedTracksFromRoom()

            if (roomTracks.isNotEmpty()) {
                localDownloadsPlayer.syncQueue(roomTracks)
            }

            _uiState.update {
                it.copy(
                    selectedTheme = loadedTheme,
                    selectedTreeStyle = loadedTreeStyle,
                    selectedTreeForm = loadedTreeForm,
                    animationConfig = loadedAnim,
                    useAria2c = downloadPrefs.useAria2c,
                    useSponsorBlock = downloadPrefs.useSponsorBlock,
                    embedMetadataAndThumb = downloadPrefs.embedMetadataAndThumb,
                    writeAutoSubtitlesLrc = downloadPrefs.writeAutoSubtitlesLrc,
                    concurrentFragments = downloadPrefs.concurrentFragments,
                    autoTranslateLyricsToTurkish = downloadPrefs.autoTranslateLyricsToTurkish,
                    translationEngine = downloadPrefs.translationEngine,
                    lyricsSourceOrder = downloadPrefs.lyricsSourceOrder,
                    enabledLyricsSources = downloadPrefs.enabledLyricsSources,
                    subscriptions = loadedSubs,
                    isAutoSyncWifiOnly = subscriptionStorage.isAutoSyncWifiOnly(),
                    autoSyncIntervalHours = subscriptionStorage.getSyncIntervalHours(),
                    autoSyncIntervalMinutes = subscriptionStorage.getSyncIntervalMinutes(),
                    isLikedSongsWifiAutoSyncEnabled = subscriptionStorage.isLikedSongsWifiAutoSyncEnabled(),
                    selectedAppIcon = loadedIcon,
                    volumeBoostPercent = loadedBoostPct,
                    isVolumeBoosterEnabled = loadedBoostEnabled,
                    featureToggles = loadedToggles,
                    studioDspConfig = loadedDsp,
                    selectedDownloadFormat = downloadPrefs.defaultDownloadFormat,
                    isPlaylistsGridView = downloadPrefs.isPlaylistsGridLayout,
                    downloadedTracks = roomTracks
                )
            }

            // 1. Aşama: Arka planda dosya sistemini Room veritabanıyla senkronize et
            val savedList = metadataManager.loadSavedDownloads()
            localDownloadsPlayer.syncQueue(savedList)
            _uiState.update { it.copy(downloadedTracks = savedList) }

            // 2. Aşama (+120ms): Çalma listeleri ve kasa öğelerini yükle
            delay(120L)
            playlistRepository.initializeAndMigrateIfNeeded()
            playlistRepository.reconcileDownloadedTracksWithPlaylists(savedList)
            val vaultList = vaultRepository.loadVaultItems()
            lastTracksRefreshUptimeMs = android.os.SystemClock.elapsedRealtime()
            _uiState.update { it.copy(vaultItems = vaultList) }

            // Yarıda kalan oynatma listesi indirme kuyruğu varsa geri yükle ve kaldığı yerden devam et
            val pendingQueue = downloadPrefs.loadPendingDownloadQueue()
            if (pendingQueue != null && pendingQueue.tracks.isNotEmpty()) {
                val downloadedIds = savedList
                    .filter { it.audioFile.exists() && it.audioFile.length() >= MediaMetadataManager.MIN_VALID_AUDIO_BYTES }
                    .map { it.trackInfo.id }
                    .toSet()
                val remainingTracks = pendingQueue.tracks.filter { it.id !in downloadedIds && it.id !in cancelledOrRemovedQueueTrackIds }
                if (remainingTracks.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            resolvedPlaylistTitle = pendingQueue.playlistTitle ?: it.resolvedPlaylistTitle,
                            resolvedPlaylistSourceId = pendingQueue.playlistId ?: it.resolvedPlaylistSourceId,
                            resolvedPlaylistCoverUrl = pendingQueue.playlistCoverUrl ?: it.resolvedPlaylistCoverUrl
                        )
                    }
                    enqueueTracksForDownload(remainingTracks)
                } else {
                    downloadPrefs.clearPendingDownloadQueue()
                }
            }

            // 3. Aşama (+350ms): ytdlnis / yt-dlp / FFmpeg native motor durumunu kontrol et
            delay(350L)
            val engineReady = SakuraMusicApplication.ensureYoutubeDLInitialized(appContext)
            val version = youtubeDLUpdater.getCurrentVersion()
            _uiState.update {
                it.copy(
                    isYtdlpEngineReady = engineReady,
                    isFFmpegReady = SakuraMusicApplication.isFFmpegInitialized,
                    isAria2cReady = SakuraMusicApplication.isAria2cInitialized,
                    youtubeDLVersion = version
                )
            }
        }

        viewModelScope.launch {
            var lastDownloadingEmitMs = 0L
            var lastDownloadingPercent = -1f
            youtubeDLDownloadManager.downloadState.collect { ytdlState ->
                if (ytdlState is YoutubeDLDownloadState.Downloading) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    val pctDelta = kotlin.math.abs(ytdlState.progressPercent - lastDownloadingPercent)
                    if (now - lastDownloadingEmitMs < 250L && pctDelta < 1.0f && ytdlState.progressPercent < 99.9f) {
                        return@collect
                    }
                    lastDownloadingEmitMs = now
                    lastDownloadingPercent = ytdlState.progressPercent
                } else {
                    lastDownloadingEmitMs = 0L
                    lastDownloadingPercent = -1f
                }
                _uiState.update { it.copy(youtubeDLState = ytdlState) }
                if (ytdlState is YoutubeDLDownloadState.Success) {
                    refreshDownloadedTracks()
                }
            }
        }

        viewModelScope.launch {
            var lastChunkEmitMs = 0L
            var lastChunkPercent = -1
            chunkedDownloader.progressFlow.collect { progress ->
                if (progress.isDownloading) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    val currentPct = (progress.overallFraction * 100f).toInt().coerceIn(0, 100)
                    val pctDelta = kotlin.math.abs(currentPct - lastChunkPercent)
                    if (now - lastChunkEmitMs < 250L && pctDelta < 1 && currentPct < 100) {
                        return@collect
                    }
                    lastChunkEmitMs = now
                    lastChunkPercent = currentPct
                } else {
                    lastChunkEmitMs = 0L
                    lastChunkPercent = -1
                }
                _uiState.update { it.copy(downloadProgress = progress) }
            }
        }

        viewModelScope.launch {
            playlistRepository.playlistsFlow.collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }

        viewModelScope.launch {
            localDownloadsPlayer.playerState.collect { pState ->
                val savedMedia = pState.currentTrack
                val offlineTrack = savedMedia?.trackInfo
                if (savedMedia != null && offlineTrack != null) {
                    val currentId = offlineTrack.id.ifBlank { savedMedia.audioFile.absolutePath }
                    val isTrackChanged = (currentId != lastObservedTrackId)

                    if (pState.isPlaying) {
                        checkAndRecordPlayCount(currentId, pState.currentPositionMs)
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (lastOfflineTickUptimeMs > 0L) {
                            val deltaMs = (now - lastOfflineTickUptimeMs).coerceIn(0L, 2000L)
                            if (deltaMs > 0L) {
                                playCountManager.addListeningTime(currentId, deltaMs)
                            }
                        }
                        lastOfflineTickUptimeMs = now
                    } else {
                        lastOfflineTickUptimeMs = 0L
                    }

                    if (isTrackChanged) {
                        lastObservedTrackId = currentId
                        countedPlayForTrackId = null
                        _uiState.update {
                            it.copy(
                                currentTrack = offlineTrack,
                                lastSavedMedia = savedMedia,
                                isPlaying = pState.isPlaying,
                                isBuffering = false,
                                currentPositionMs = pState.currentPositionMs,
                                durationMs = pState.durationMs,
                                abLoopPointAMs = null,
                                abLoopPointBMs = null
                            )
                        }
                        fetchLyricsForTrack(offlineTrack, savedMedia.audioFile)
                        prewarmNextQueueTrack(pState)
                    } else {
                        val curState = _uiState.value
                        val pointA = curState.abLoopPointAMs
                        val pointB = curState.abLoopPointBMs
                        if (curState.featureToggles.abLoopEnabled && pointA != null && pointB != null && pState.currentPositionMs >= pointB) {
                            localDownloadsPlayer.seekTo(pointA)
                        }
                        val lyricIdx = if (curState.isNowPlayingExpanded) {
                            findActiveLyricIndex(curState.parsedLyrics, pState.currentPositionMs)
                        } else {
                            curState.activeLyricIndex
                        }
                        val posDelta = kotlin.math.abs(curState.currentPositionMs - pState.currentPositionMs)
                        if (curState.isPlaying != pState.isPlaying ||
                            posDelta >= 200L ||
                            curState.durationMs != pState.durationMs ||
                            curState.activeLyricIndex != lyricIdx
                        ) {
                            _uiState.update {
                                it.copy(
                                    currentTrack = offlineTrack,
                                    lastSavedMedia = savedMedia,
                                    isPlaying = pState.isPlaying,
                                    currentPositionMs = pState.currentPositionMs,
                                    durationMs = pState.durationMs,
                                    activeLyricIndex = lyricIdx
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun selectTab(tab: YtMusicTab) {
        if (_uiState.value.selectedTab == tab) return
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == YtMusicTab.LIBRARY || tab == YtMusicTab.VAULT) {
            val now = android.os.SystemClock.elapsedRealtime()
            val hasLoadedOnce = lastTracksRefreshUptimeMs > 0L && _uiState.value.downloadedTracks.isNotEmpty()
            if (!hasLoadedOnce || (now - lastTracksRefreshUptimeMs) > 60_000L) {
                refreshDownloadedTracks(force = false)
            }
        }
    }

    fun selectTheme(theme: AppTheme) {
        themeStorage.saveTheme(theme)
        // Tema değiştirildiğinde ağacın altın renginde veya eski stilde takılı kalmaması için otomatik temayla uyumlu moda geçirilir
        themeStorage.saveTreeStyle(SakuraTreeStyle.TRADITIONAL_SAKURA)
        _uiState.update {
            it.copy(
                selectedTheme = theme,
                selectedTreeStyle = SakuraTreeStyle.TRADITIONAL_SAKURA,
                statusBannerMessage = "Tema '${theme.displayName}' olarak güncellendi (Ağaç rengi temayla eşitlendi)."
            )
        }
    }

    fun selectTreeStyle(treeStyle: SakuraTreeStyle) {
        themeStorage.saveTreeStyle(treeStyle)
        _uiState.update {
            it.copy(
                selectedTreeStyle = treeStyle,
                statusBannerMessage = "Sakura Renk Stili '${treeStyle.displayName}' olarak ayarlandı."
            )
        }
    }

    fun selectTreeForm(treeForm: SakuraTreeForm) {
        themeStorage.saveTreeForm(treeForm)
        _uiState.update {
            it.copy(
                selectedTreeForm = treeForm,
                statusBannerMessage = "Sakura Ağaç Formu '${treeForm.displayName}' olarak ayarlandı."
            )
        }
    }

    fun selectAnimationProfile(profile: SakuraAnimationProfile) {
        val current = _uiState.value.animationConfig
        val updated = current.copy(
            profile = profile,
            petalDensity = profile.defaultDensity,
            transitionSpeed = profile.defaultSpeed,
            effectIntensity = profile.defaultIntensity
        )
        animationPrefs.saveConfig(updated)
        _uiState.update { it.copy(animationConfig = updated) }
    }

    fun setAnimationsMasterEnabled(enabled: Boolean) {
        val updated = _uiState.value.animationConfig.copy(masterEnabled = enabled)
        animationPrefs.saveConfig(updated)
        _uiState.update { it.copy(animationConfig = updated) }
    }

    fun selectPetalDensity(density: PetalDensityLevel) {
        val updated = _uiState.value.animationConfig.copy(petalDensity = density)
        animationPrefs.saveConfig(updated)
        _uiState.update { it.copy(animationConfig = updated) }
    }

    fun selectTransitionSpeed(speed: TransitionSpeedLevel) {
        val updated = _uiState.value.animationConfig.copy(transitionSpeed = speed)
        animationPrefs.saveConfig(updated)
        _uiState.update { it.copy(animationConfig = updated) }
    }

    fun selectEffectIntensity(intensity: EffectIntensityLevel) {
        val updated = _uiState.value.animationConfig.copy(effectIntensity = intensity)
        animationPrefs.saveConfig(updated)
        _uiState.update { it.copy(animationConfig = updated) }
    }

    fun setFloatingPetalsEnabled(enabled: Boolean) {
        val updated = _uiState.value.animationConfig.copy(floatingPetalsEnabled = enabled)
        animationPrefs.saveConfig(updated)
        _uiState.update { it.copy(animationConfig = updated) }
    }

    fun setAudioReactiveWavesEnabled(enabled: Boolean) {
        val updated = _uiState.value.animationConfig.copy(audioReactiveWavesEnabled = enabled)
        animationPrefs.saveConfig(updated)
        _uiState.update { it.copy(animationConfig = updated) }
    }

    fun setInteractiveBurstEnabled(enabled: Boolean) {
        val updated = _uiState.value.animationConfig.copy(interactiveBurstEnabled = enabled)
        animationPrefs.saveConfig(updated)
        _uiState.update { it.copy(animationConfig = updated) }
    }

    fun setScreenTransitionsEnabled(enabled: Boolean) {
        val updated = _uiState.value.animationConfig.copy(screenTransitionsEnabled = enabled)
        animationPrefs.saveConfig(updated)
        _uiState.update { it.copy(animationConfig = updated) }
    }

    fun setPowerSaveAutoReduce(enabled: Boolean) {
        val updated = _uiState.value.animationConfig.copy(powerSaveAutoReduce = enabled)
        animationPrefs.saveConfig(updated)
        _uiState.update { it.copy(animationConfig = updated) }
    }

    fun updateYoutubeUrlInput(input: String) {
        val support = if (input.isNotBlank()) ytdlpResolver.evaluateUrlSupport(input) else null
        _uiState.update {
            it.copy(
                youtubeUrlInput = input,
                currentUrlSupportStatus = support,
                errorMessage = null
            )
        }
    }

    fun selectDownloadFormat(format: DownloadFormatOption) {
        downloadPrefs.defaultDownloadFormat = format
        _uiState.update { it.copy(selectedDownloadFormat = format) }
    }

    fun setUseAria2c(enabled: Boolean) {
        downloadPrefs.useAria2c = enabled
        _uiState.update { it.copy(useAria2c = enabled) }
    }

    fun setUseSponsorBlock(enabled: Boolean) {
        downloadPrefs.useSponsorBlock = enabled
        _uiState.update { it.copy(useSponsorBlock = enabled) }
    }

    fun setEmbedMetadataAndThumb(enabled: Boolean) {
        downloadPrefs.embedMetadataAndThumb = enabled
        _uiState.update { it.copy(embedMetadataAndThumb = enabled) }
    }

    fun setWriteAutoSubtitlesLrc(enabled: Boolean) {
        downloadPrefs.writeAutoSubtitlesLrc = enabled
        _uiState.update { it.copy(writeAutoSubtitlesLrc = enabled) }
    }

    fun setConcurrentFragments(count: Int) {
        val clamped = count.coerceIn(1, 16)
        downloadPrefs.concurrentFragments = clamped
        _uiState.update { it.copy(concurrentFragments = clamped) }
    }

    fun updateYoutubeDLBinary(useNightly: Boolean = false) {
        if (_uiState.value.isUpdatingYoutubeDL) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUpdatingYoutubeDL = true,
                    statusBannerMessage = "yt-dlp motoru güncelleniyor..."
                )
            }
            when (val res = youtubeDLUpdater.updateEngine(useNightly)) {
                is YoutubeDLUpdateResult.Updated -> {
                    _uiState.update {
                        it.copy(
                            isUpdatingYoutubeDL = false,
                            youtubeDLVersion = res.version,
                            statusBannerMessage = res.message
                        )
                    }
                }
                is YoutubeDLUpdateResult.AlreadyUpToDate -> {
                    _uiState.update {
                        it.copy(
                            isUpdatingYoutubeDL = false,
                            youtubeDLVersion = res.version,
                            statusBannerMessage = res.message
                        )
                    }
                }
                is YoutubeDLUpdateResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isUpdatingYoutubeDL = false,
                            errorMessage = res.errorMessage
                        )
                    }
                }
            }
        }
    }

    fun handleIncomingSharedUrl(sharedText: String, autoDownload: Boolean = true) {
        val extractedUrl = extractFirstHttpUrl(sharedText) ?: sharedText.trim()
        if (extractedUrl.isBlank()) return
        updateYoutubeUrlInput(extractedUrl)
        _uiState.update {
            it.copy(
                selectedTab = YtMusicTab.HOME,
                statusBannerMessage = "Paylaşılan bağlantı algılandı: $extractedUrl"
            )
        }
        if (autoDownload) {
            startYoutubeDLDownload(extractedUrl)
        } else {
            resolveAndPlayYouTubeInput(autoDownload = false)
        }
    }

    private fun extractFirstHttpUrl(text: String): String? {
        val regex = Regex("""https?://[^\s"'<>]+""")
        return regex.find(text)?.value
    }

    fun resolveAndPlayYouTubeInput(
        rawInput: String = "",
        autoDownload: Boolean = false,
        existingTrack: MediaTrackInfo? = null
    ) {
        stopAllPlayback()

        val targetUrl = (rawInput.ifBlank { _uiState.value.youtubeUrlInput }).trim()
        if (targetUrl.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Lütfen bir bağlantı veya şarkı adı girin.") }
            return
        }

        val initialTrack = existingTrack ?: MediaTrackInfo(
            id = streamResolver.extractVideoId(targetUrl) ?: targetUrl,
            title = "Medya Yükleniyor...",
            artist = "Sakura Player",
            durationSeconds = 0,
            thumbnailUrl = null,
            sourceUrl = targetUrl,
            streamUrl = ""
        )

        _uiState.update {
            it.copy(
                youtubeUrlInput = targetUrl,
                currentTrack = initialTrack,
                isBuffering = true,
                isPlaying = false,
                currentPositionMs = 0L,
                lrcRawText = "",
                parsedLyrics = emptyList(),
                activeLyricIndex = -1,
                errorMessage = null,
                statusBannerMessage = "Çözümleniyor..."
            )
        }

        val support = ytdlpResolver.evaluateUrlSupport(targetUrl)
        if (!support.isSupported) {
            _uiState.update {
                it.copy(
                    isBuffering = false,
                    errorMessage = support.reasonMessage
                )
            }
            return
        }

        val playlistId = streamResolver.extractPlaylistId(targetUrl)
        val videoId = streamResolver.extractVideoId(targetUrl)
        if (!playlistId.isNullOrBlank() && videoId.isNullOrBlank()) {
            resolvePlaylistInput(targetUrl)
            return
        }

        activeStreamResolutionJob?.cancel()
        activeStreamResolutionJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isResolvingStream = true,
                    errorMessage = null,
                    statusBannerMessage = "${support.platform.displayName} çözümleniyor..."
                )
            }
            val (_, savedCookies) = cookieStorage.loadCookies()
            val cookies = NetscapeCookieHelper.resolveActiveCookies(appContext, savedCookies)
            when (val res = streamResolver.resolveInput(targetUrl, cookies)) {
                is StreamResolutionResult.Success -> {
                    val track = res.trackInfo
                    _uiState.update {
                        it.copy(
                            isResolvingStream = false,
                            currentTrack = track,
                            statusBannerMessage = "Hazır: ${track.artist} - ${track.title}"
                        )
                    }
                    fetchLyricsForTrack(track, localFile = null)
                    if (autoDownload) {
                        startYoutubeDLDownload(
                            customUrl = track.sourceUrl.ifBlank { targetUrl },
                            existingTrack = track
                        )
                    } else {
                        playOnlineStream(track)
                    }
                }
                is StreamResolutionResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isResolvingStream = false,
                            isBuffering = false,
                            errorMessage = res.message
                        )
                    }
                }
            }
        }
    }

    private fun resolvePlaylistInput(playlistUrlOrId: String) {
        activeStreamResolutionJob?.cancel()
        activeStreamResolutionJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isResolvingStream = true,
                    errorMessage = null,
                    statusBannerMessage = "Çalma listesi (ytdlnis + çerezler ile) çözümleniyor..."
                )
            }
            val (_, savedCookies) = cookieStorage.loadCookies()
            val cookies = NetscapeCookieHelper.resolveActiveCookies(appContext, savedCookies)
            NetscapeCookieHelper.writeNetscapeCookieFile(appContext, cookies)
            val result = streamResolver.resolvePlaylist(playlistUrlOrId, cookies)
            if (result.tracks.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isResolvingStream = false,
                        errorMessage = "Çalma listesinde indirilebilir parça bulunamadı. Özel/Beğenilenler listesi ise Çerez Yöneticisi'nden oturum açtığınızdan emin olun."
                    )
                }
            } else {
                val cleanTitle = result.title.trim().ifBlank { "Oynatma Listesi" }
                val coverUrl = result.thumbnailUrl ?: result.tracks.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
                val stampedTracks = result.tracks.map { track ->
                    track.copy(
                        album = cleanTitle,
                        playlistId = result.playlistId,
                        playlistTitle = cleanTitle,
                        playlistCoverUrl = coverUrl
                    )
                }
                _uiState.update {
                    it.copy(
                        isResolvingStream = false,
                        resolvedPlaylistTitle = cleanTitle,
                        resolvedPlaylistSourceId = result.playlistId,
                        resolvedPlaylistCoverUrl = coverUrl,
                        resolvedPlaylistTracks = stampedTracks,
                        selectedPlaylistTrackIds = stampedTracks.map { t -> t.id }.toSet(),
                        statusBannerMessage = "'$cleanTitle' listesinden ${stampedTracks.size} parça bulundu. İstemediğiniz şarkıları eleyip indirmeyi başlatabilirsiniz."
                    )
                }
            }
        }
    }

    fun clearResolvedPlaylist() {
        _uiState.update {
            it.copy(
                resolvedPlaylistTitle = null,
                resolvedPlaylistSourceId = null,
                resolvedPlaylistCoverUrl = null,
                resolvedPlaylistTracks = emptyList(),
                selectedPlaylistTrackIds = emptySet(),
                isBatchDownloadingPlaylist = false
            )
        }
    }

    fun togglePlaylistTrackSelection(trackId: String) {
        _uiState.update { current ->
            val updated = current.selectedPlaylistTrackIds.toMutableSet()
            if (updated.contains(trackId)) {
                updated.remove(trackId)
            } else {
                updated.add(trackId)
            }
            current.copy(selectedPlaylistTrackIds = updated)
        }
    }

    fun selectAllPlaylistTracks(selectAll: Boolean) {
        _uiState.update { current ->
            val updated = if (selectAll) {
                current.resolvedPlaylistTracks.map { it.id }.toSet()
            } else {
                emptySet()
            }
            current.copy(selectedPlaylistTrackIds = updated)
        }
    }

    fun selectPlaylistTrackRange(startIndex: Int, endIndexExclusive: Int?) {
        _uiState.update { current ->
            val tracks = current.resolvedPlaylistTracks
            if (tracks.isEmpty()) return@update current
            val safeStart = startIndex.coerceIn(0, tracks.size)
            val safeEnd = (endIndexExclusive ?: tracks.size).coerceIn(safeStart, tracks.size)
            val selectedSlice = tracks.subList(safeStart, safeEnd).map { it.id }.toSet()
            current.copy(selectedPlaylistTrackIds = selectedSlice)
        }
    }

    /**
     * 1-tabanlı özel aralık seçimi (Örn: 15..60 -> 15. şarkıdan 60. şarkıya kadar dahil).
     * Sınır dışı, ters veya boş aralıkları güvenli biçimde doğrular; çökme oluşturmaz.
     */
    fun selectCustomPlaylistRange(startOneBased: Int, endOneBased: Int): Boolean {
        val tracks = _uiState.value.resolvedPlaylistTracks
        if (tracks.isEmpty()) {
            _uiState.update {
                it.copy(errorMessage = "Özel aralık seçmek için önce bir oynatma listesi yükleyin.")
            }
            return false
        }
        val total = tracks.size
        if (startOneBased < 1 || endOneBased < 1) {
            _uiState.update {
                it.copy(errorMessage = "Başlangıç ve bitiş değerleri en az 1 olmalıdır (1–$total).")
            }
            return false
        }
        if (startOneBased > endOneBased) {
            _uiState.update {
                it.copy(errorMessage = "Geçersiz aralık: Başlangıç ($startOneBased) bitiş değerinden ($endOneBased) büyük olamaz.")
            }
            return false
        }
        if (startOneBased > total) {
            _uiState.update {
                it.copy(errorMessage = "Başlangıç değeri ($startOneBased) listedeki toplam şarkı sayısını ($total) aşıyor.")
            }
            return false
        }
        val clampedEnd = endOneBased.coerceIn(startOneBased, total)
        val selectedIds = tracks.subList(startOneBased - 1, clampedEnd).map { it.id }.toSet()
        _uiState.update {
            it.copy(
                selectedPlaylistTrackIds = selectedIds,
                errorMessage = null,
                statusBannerMessage = "$startOneBased–$clampedEnd aralığındaki ${selectedIds.size} şarkı seçildi."
            )
        }
        return true
    }

    /**
     * Tek tuşla kullanıcının YouTube Music kütüphanesindeki tüm oynatma listelerini
     * (Adı + Kaç Şarkı Olduğu ile birlikte) getirir.
     */
    fun fetchUserLibraryPlaylistsWithCookies() {
        if (_uiState.value.isLoadingRemoteLibraryPlaylists) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingRemoteLibraryPlaylists = true,
                    errorMessage = null,
                    statusBannerMessage = "Kütüphanenizdeki tüm oynatma listeleri ve şarkı sayıları taranıyor..."
                )
            }
            val (_, savedCookies) = cookieStorage.loadCookies()
            val cookies = NetscapeCookieHelper.resolveActiveCookies(appContext, savedCookies)
            val cookieHeader = NetscapeCookieHelper.buildCookieHeaderString(cookies, appContext)
            if (cookies.isEmpty() && cookieHeader.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isLoadingRemoteLibraryPlaylists = false,
                        errorMessage = "Kütüphanenizdeki oynatma listelerini görmek için lütfen önce Çerezler sekmesinden YouTube hesabınıza giriş yapın."
                    )
                }
                return@launch
            }

            val result = streamResolver.fetchUserLibraryPlaylists(cookies)
            result.fold(
                onSuccess = { playlists ->
                    _uiState.update {
                        it.copy(
                            isLoadingRemoteLibraryPlaylists = false,
                            remoteLibraryPlaylists = playlists,
                            isDownloadQueueCenterOpen = true,
                            statusBannerMessage = if (playlists.isNotEmpty()) {
                                "Kütüphanenizde ${playlists.size} oynatma listesi bulundu. Bir liste seçerek şarkılarını görüntüleyebilirsiniz."
                            } else {
                                "Kütüphanenizde kayıtlı oynatma listesi bulunamadı."
                            }
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingRemoteLibraryPlaylists = false,
                            errorMessage = err.localizedMessage ?: "Oynatma listeleri alınamadı."
                        )
                    }
                }
            )
        }
    }

    fun selectRemoteLibraryPlaylist(summary: com.example.downloader.RemotePlaylistSummary) {
        if (summary.playlistId.equals("LM", ignoreCase = true) ||
            summary.browseId.equals("VLLM", ignoreCase = true)
        ) {
            downloadAccountLibraryWithCookies(LikedSongsSourceType.YOUTUBE_MUSIC)
        } else {
            val targetUrl = "https://music.youtube.com/playlist?list=${summary.playlistId.removePrefix("VL")}"
            updateYoutubeUrlInput(targetUrl)
            resolvePlaylistInput(targetUrl)
        }
    }

    fun downloadAccountLibraryWithCookies(sourceType: LikedSongsSourceType = LikedSongsSourceType.YOUTUBE_MUSIC) {
        activeStreamResolutionJob?.cancel()
        activeStreamResolutionJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isResolvingStream = true,
                    errorMessage = null,
                    activeLikedSongsSource = sourceType,
                    statusBannerMessage = "Morphe CookieBridge ve çerezler ile ${sourceType.displayName} listesi taranıyor..."
                )
            }
            val (_, savedCookies) = cookieStorage.loadCookies()
            val cookies = NetscapeCookieHelper.resolveActiveCookies(appContext, savedCookies)
            val cookieHeader = NetscapeCookieHelper.buildCookieHeaderString(cookies, appContext)
            if (cookies.isEmpty() && cookieHeader.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isResolvingStream = false,
                        errorMessage = "Beğenilenler listesini görmek için lütfen önce Çerezler sekmesinden YouTube hesabınıza giriş yapın veya çerez yapıştırın."
                    )
                }
                return@launch
            }

            if (cookies.isNotEmpty()) {
                NetscapeCookieHelper.writeNetscapeCookieFile(appContext, cookies)
            }
            com.example.downloader.StreamCache.clearAll()

            val combinedTracks = mutableListOf<MediaTrackInfo>()
            val seenIds = mutableSetOf<String>()

            val playlistTargets = if (sourceType == LikedSongsSourceType.YOUTUBE_MUSIC) {
                listOf(
                    "VLLM",
                    "FEmusic_liked_videos",
                    "https://music.youtube.com/playlist?list=LM"
                )
            } else {
                listOf(
                    "VLLL",
                    "https://www.youtube.com/playlist?list=LL"
                )
            }

            val isStrictMusic = sourceType == LikedSongsSourceType.YOUTUBE_MUSIC
            var resolvedHeaderTitle: String? = null
            var resolvedHeaderId: String? = null
            var resolvedHeaderCover: String? = null
            for (target in playlistTargets) {
                val res = streamResolver.resolvePlaylist(target, cookies)
                if (resolvedHeaderTitle.isNullOrBlank() && res.title.isNotBlank()) {
                    resolvedHeaderTitle = res.title
                }
                if (resolvedHeaderId.isNullOrBlank() && res.playlistId.isNotBlank()) {
                    resolvedHeaderId = res.playlistId
                }
                if (resolvedHeaderCover.isNullOrBlank() && !res.thumbnailUrl.isNullOrBlank()) {
                    resolvedHeaderCover = res.thumbnailUrl
                }
                for (track in res.tracks) {
                    val isMusicValid = com.example.downloader.StrictMusicTrackValidator.isStrictMusicTrack(
                        title = track.title,
                        artist = track.artist,
                        durationSeconds = track.durationSeconds,
                        musicVideoType = null,
                        enforceStrictFilter = isStrictMusic
                    )
                    if (isMusicValid && seenIds.add(track.id)) {
                        combinedTracks.add(track)
                    }
                }
                if (combinedTracks.isNotEmpty()) break
            }

            if (combinedTracks.isNotEmpty()) {
                val realPlaylistName = resolvedHeaderTitle?.takeIf { it.isNotBlank() } ?: sourceType.displayName
                val playlistId = resolvedHeaderId ?: if (sourceType == LikedSongsSourceType.YOUTUBE_MUSIC) "VLLM" else "VLLL"
                val coverUrl = resolvedHeaderCover ?: combinedTracks.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
                val stampedTracks = combinedTracks.map { track ->
                    track.copy(
                        album = realPlaylistName,
                        playlistId = playlistId,
                        playlistTitle = realPlaylistName,
                        playlistCoverUrl = coverUrl
                    )
                }
                _uiState.update {
                    it.copy(
                        isResolvingStream = false,
                        resolvedPlaylistTitle = realPlaylistName,
                        resolvedPlaylistSourceId = playlistId,
                        resolvedPlaylistCoverUrl = coverUrl,
                        resolvedPlaylistTracks = stampedTracks,
                        selectedPlaylistTrackIds = stampedTracks.map { t -> t.id }.toSet(),
                        statusBannerMessage = "$realPlaylistName (${stampedTracks.size} Parça) hazırlandı. Aralık seçebilir veya 'Seçilenleri İndir' butonuna dokunabilirsiniz."
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isResolvingStream = false,
                        errorMessage = "${sourceType.displayName} listesinde içerik bulunamadı. Lütfen Çerezler sekmesinden 'Oturumu Doğrula' veya 'Çerez Yapıştır' seçeneğini kontrol edin."
                    )
                }
            }
        }
    }

    private fun persistCurrentDownloadQueue() {
        val state = _uiState.value
        val activeAndPending = buildList {
            state.currentDownloadingTrack?.let { current ->
                if (current.id !in cancelledOrRemovedQueueTrackIds) {
                    add(current)
                }
            }
            addAll(state.downloadQueue.filterNot { it.id in cancelledOrRemovedQueueTrackIds })
        }
        if (activeAndPending.isEmpty()) {
            downloadPrefs.clearPendingDownloadQueue()
            return
        }
        val firstPlaylistTrack = activeAndPending.firstOrNull { it.effectivePlaylistName() != null }
        val playlistTitle = firstPlaylistTrack?.effectivePlaylistName() ?: state.resolvedPlaylistTitle
        val playlistId = firstPlaylistTrack?.playlistId ?: state.resolvedPlaylistSourceId
        val playlistCoverUrl = firstPlaylistTrack?.playlistCoverUrl ?: state.resolvedPlaylistCoverUrl
        downloadPrefs.savePendingDownloadQueue(
            tracks = activeAndPending,
            playlistTitle = playlistTitle,
            playlistId = playlistId,
            playlistCoverUrl = playlistCoverUrl,
            totalCount = state.batchDownloadTotalCount,
            completedCount = state.batchDownloadCompletedCount
        )
    }

    fun removeTrackFromDownloadQueue(trackId: String) {
        if (trackId.isBlank()) return
        cancelledOrRemovedQueueTrackIds.add(trackId)

        val isCurrentlyDownloading = _uiState.value.currentDownloadingTrack?.id == trackId
        if (isCurrentlyDownloading) {
            youtubeDLDownloadManager.cancelActiveDownload()
        }

        _uiState.update { state ->
            val updatedQueue = state.downloadQueue.filterNot { it.id == trackId }
            val removedCount = (state.downloadQueue.size - updatedQueue.size) + (if (isCurrentlyDownloading) 1 else 0)
            state.copy(
                currentDownloadingTrack = if (isCurrentlyDownloading) null else state.currentDownloadingTrack,
                downloadQueue = updatedQueue,
                batchDownloadTotalCount = (state.batchDownloadTotalCount - removedCount).coerceAtLeast(state.batchDownloadCompletedCount),
                statusBannerMessage = if (isCurrentlyDownloading) {
                    "İndirilmekte olan şarkı iptal edilip kuyruktan çıkarıldı."
                } else {
                    "Parça sıradaki indirme kuyruğundan çıkarıldı."
                }
            )
        }
        persistCurrentDownloadQueue()
    }

    fun removeTracksFromDownloadQueue(trackIds: Collection<String>) {
        val targetIds = trackIds.filter { it.isNotBlank() }.toSet()
        if (targetIds.isEmpty()) return
        cancelledOrRemovedQueueTrackIds.addAll(targetIds)

        val activeTrack = _uiState.value.currentDownloadingTrack
        val isCurrentlyDownloading = activeTrack != null && activeTrack.id in targetIds
        if (isCurrentlyDownloading) {
            youtubeDLDownloadManager.cancelActiveDownload()
        }

        _uiState.update { state ->
            val updatedQueue = state.downloadQueue.filterNot { it.id in targetIds }
            val removedCount = (state.downloadQueue.size - updatedQueue.size) + (if (isCurrentlyDownloading) 1 else 0)
            state.copy(
                currentDownloadingTrack = if (isCurrentlyDownloading) null else state.currentDownloadingTrack,
                downloadQueue = updatedQueue,
                batchDownloadTotalCount = (state.batchDownloadTotalCount - removedCount).coerceAtLeast(state.batchDownloadCompletedCount),
                statusBannerMessage = "$removedCount şarkı indirme kuyruğundan çıkarıldı."
            )
        }
        persistCurrentDownloadQueue()
    }

    fun moveTrackToTopOfDownloadQueue(trackId: String) {
        _uiState.update { state ->
            val target = state.downloadQueue.firstOrNull { it.id == trackId } ?: return@update state
            val remaining = state.downloadQueue.filterNot { it.id == trackId }
            state.copy(
                downloadQueue = listOf(target) + remaining,
                statusBannerMessage = "'${target.title}' sıradaki ilk indirme olarak en üste taşındı."
            )
        }
        persistCurrentDownloadQueue()
    }

    fun clearDownloadQueue(cancelActiveTrack: Boolean = false) {
        val currentActive = _uiState.value.currentDownloadingTrack
        val queueIds = _uiState.value.downloadQueue.map { it.id }.toSet()
        cancelledOrRemovedQueueTrackIds.addAll(queueIds)

        if (cancelActiveTrack && currentActive != null) {
            cancelledOrRemovedQueueTrackIds.add(currentActive.id)
            youtubeDLDownloadManager.cancelActiveDownload()
        }

        _uiState.update { state ->
            val clearedCount = state.downloadQueue.size
            state.copy(
                downloadQueue = emptyList(),
                currentDownloadingTrack = if (cancelActiveTrack) null else state.currentDownloadingTrack,
                batchDownloadTotalCount = if (cancelActiveTrack) 0 else (state.batchDownloadTotalCount - clearedCount).coerceAtLeast(state.batchDownloadCompletedCount),
                statusBannerMessage = "Sıradaki $clearedCount bekleyen indirme kuyruktan temizlendi."
            )
        }
        if (cancelActiveTrack || _uiState.value.currentDownloadingTrack == null) {
            downloadPrefs.clearPendingDownloadQueue()
        } else {
            persistCurrentDownloadQueue()
        }
    }

    fun downloadSelectedPlaylistTracks() {
        val state = _uiState.value
        val allTracks = state.resolvedPlaylistTracks
        val selectedIds = state.selectedPlaylistTrackIds
        val rawSelected = allTracks.filter { it.id in selectedIds }
        if (rawSelected.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Lütfen indirmek için listeden en az bir şarkı seçin.") }
            return
        }

        val resolvedTitle = state.resolvedPlaylistTitle?.takeIf { MediaTrackInfo.isMeaningfulPlaylistName(it) }
        val resolvedId = state.resolvedPlaylistSourceId
        val resolvedCover = state.resolvedPlaylistCoverUrl
        val tracksToDownload = rawSelected.map { track ->
            val pName = track.effectivePlaylistName() ?: resolvedTitle
            if (!pName.isNullOrBlank()) {
                track.copy(
                    album = pName,
                    playlistTitle = pName,
                    playlistId = track.playlistId ?: resolvedId,
                    playlistCoverUrl = track.playlistCoverUrl ?: resolvedCover
                )
            } else {
                track
            }
        }

        enqueueTracksForDownload(tracksToDownload)
    }

    private fun enqueueTracksForDownload(tracksToEnqueue: List<MediaTrackInfo>) {
        if (tracksToEnqueue.isEmpty()) return

        val newIds = tracksToEnqueue.map { it.id }.toSet()
        cancelledOrRemovedQueueTrackIds.removeAll(newIds)

        val isRunnerActive = activeDownloadJob?.isActive == true
        if (isRunnerActive) {
            _uiState.update { state ->
                val existingIds = state.downloadQueue.map { it.id }.toSet()
                val activeId = state.currentDownloadingTrack?.id
                val newItems = tracksToEnqueue.filter { it.id !in existingIds && it.id != activeId }
                state.copy(
                    isBatchDownloadingPlaylist = true,
                    downloadQueue = state.downloadQueue + newItems,
                    batchDownloadTotalCount = state.batchDownloadTotalCount + newItems.size,
                    statusBannerMessage = "${newItems.size} yeni parça indirme kuyruğuna eklendi (Bekleyen: ${state.downloadQueue.size + newItems.size})."
                )
            }
            persistCurrentDownloadQueue()
            return
        }

        val format = _uiState.value.selectedDownloadFormat
        val isPlaylistDownload = tracksToEnqueue.size > 1 || tracksToEnqueue.any { it.effectivePlaylistName() != null }
        _uiState.update {
            it.copy(
                isBatchDownloadingPlaylist = isPlaylistDownload,
                batchDownloadCompletedCount = 0,
                batchDownloadTotalCount = tracksToEnqueue.size,
                downloadQueue = tracksToEnqueue,
                statusBannerMessage = if (tracksToEnqueue.size > 1) {
                    "Seçili ${tracksToEnqueue.size} parça sırayla indiriliyor (${format.displayName})..."
                } else {
                    "İndirme başlatıldı: ${tracksToEnqueue.first().title}"
                }
            )
        }
        persistCurrentDownloadQueue()

        startQueueConsumerJob()
    }

    fun pauseDownloadQueue() {
        _uiState.update {
            it.copy(
                isDownloadQueuePaused = true,
                activeIntervalCountdownSeconds = 0,
                statusBannerMessage = "İndirme kuyruğu duraklatıldı. İstediğiniz zaman devam ettirebilirsiniz."
            )
        }
        persistCurrentDownloadQueue()
    }

    fun resumeDownloadQueue() {
        val wasPaused = _uiState.value.isDownloadQueuePaused
        _uiState.update {
            it.copy(
                isDownloadQueuePaused = false,
                statusBannerMessage = "İndirme kuyruğu devam ettiriliyor..."
            )
        }
        if (wasPaused && activeDownloadJob?.isActive != true && _uiState.value.downloadQueue.isNotEmpty()) {
            startQueueConsumerJob()
        }
    }

    fun retryFailedQueueTracks() {
        val failed = _uiState.value.failedQueueTracks
        if (failed.isEmpty()) return
        _uiState.update {
            it.copy(
                failedQueueTracks = emptyList(),
                isDownloadQueuePaused = false
            )
        }
        enqueueTracksForDownload(failed)
    }

    private fun startQueueConsumerJob() {
        activeDownloadJob?.cancel()
        activeDownloadJob = viewModelScope.launch {
            val (_, savedCookies) = cookieStorage.loadCookies()
            val cookies = NetscapeCookieHelper.resolveActiveCookies(appContext, savedCookies)
            NetscapeCookieHelper.writeNetscapeCookieFile(appContext, cookies)

            // Aynı isimli çalma listeleri için çakışma yönetimini baştan çözümle ve kapak görselini indir
            val resolvedPlaylistMap = mutableMapOf<String, UserPlaylist>()
            val initialQueue = _uiState.value.downloadQueue
            val distinctPlaylists = initialQueue
                .mapNotNull { track ->
                    val pTitle = track.effectivePlaylistName() ?: return@mapNotNull null
                    Triple(
                        pTitle,
                        track.playlistId ?: _uiState.value.resolvedPlaylistSourceId,
                        track.playlistCoverUrl ?: _uiState.value.resolvedPlaylistCoverUrl ?: track.thumbnailUrl
                    )
                }
                .distinctBy { "${it.first.lowercase()}|${it.second.orEmpty()}" }

            for ((rawTitle, sourceId, coverUrl) in distinctPlaylists) {
                val key = "${rawTitle.lowercase()}|${sourceId.orEmpty()}"
                val ensured = playlistRepository.ensurePlaylistForDownload(
                    rawPlaylistTitle = rawTitle,
                    sourcePlaylistId = sourceId,
                    coverUrl = coverUrl
                )
                if (ensured != null) {
                    resolvedPlaylistMap[key] = ensured
                }
            }

            var completed = _uiState.value.batchDownloadCompletedCount.coerceAtLeast(0)
            var processedInThisRun = 0

            while (isActive) {
                // Duraklatma kontrolü: Kullanıcı kuyruğu duraklattıysa bekle
                while (isActive && _uiState.value.isDownloadQueuePaused) {
                    delay(250L)
                }
                if (!isActive) break

                // 1. Kuyruktan çıkarılmış parçaları atla ve ilk geçerli parçayı al
                val rawNextTrack = _uiState.value.downloadQueue.firstOrNull { it.id !in cancelledOrRemovedQueueTrackIds } ?: break

                // Aralıklı indirme beklemesi: Yalnızca ardışık kuyruk indirmeleri arasında uygulanır (tekli indirmeyi geciktirmez)
                val intervalDelaySec = _uiState.value.featureToggles.downloadIntervalDelaySeconds.coerceIn(0, 120)
                if (processedInThisRun > 0 && intervalDelaySec > 0) {
                    var remainingMs = intervalDelaySec * 1000L
                    while (remainingMs > 0L && isActive && !_uiState.value.isDownloadQueuePaused) {
                        if (rawNextTrack.id in cancelledOrRemovedQueueTrackIds || _uiState.value.downloadQueue.isEmpty()) {
                            break
                        }
                        val secLeft = ((remainingMs + 999L) / 1000L).toInt()
                        if (_uiState.value.activeIntervalCountdownSeconds != secLeft) {
                            _uiState.update { cur ->
                                cur.copy(
                                    activeIntervalCountdownSeconds = secLeft,
                                    statusBannerMessage = "Aralıklı indirme: Sıradaki şarkı ('${rawNextTrack.title}') için ${secLeft} sn bekleniyor..."
                                )
                            }
                        }
                        val step = minOf(250L, remainingMs)
                        delay(step)
                        remainingMs -= step
                    }
                    _uiState.update { it.copy(activeIntervalCountdownSeconds = 0) }
                    if (!isActive) break
                    if (_uiState.value.isDownloadQueuePaused) continue
                }

                // Kuyrukta önceden iptal edilmiş/çıkarılmış artık parça varsa temizle
                if (_uiState.value.downloadQueue.any { it.id in cancelledOrRemovedQueueTrackIds }) {
                    _uiState.update { cur ->
                        cur.copy(downloadQueue = cur.downloadQueue.filterNot { it.id in cancelledOrRemovedQueueTrackIds })
                    }
                }

                val rawPlaylistName = rawNextTrack.effectivePlaylistName()
                val rawPlaylistId = rawNextTrack.playlistId ?: _uiState.value.resolvedPlaylistSourceId
                val rawPlaylistCover = rawNextTrack.playlistCoverUrl ?: _uiState.value.resolvedPlaylistCoverUrl
                val mapKey = rawPlaylistName?.let { "${it.lowercase()}|${rawPlaylistId.orEmpty()}" }
                val ensuredPlaylist = mapKey?.let { resolvedPlaylistMap[it] }
                val finalPlaylistName = ensuredPlaylist?.name ?: rawPlaylistName

                val nextTrack = if (!finalPlaylistName.isNullOrBlank()) {
                    rawNextTrack.copy(
                        album = finalPlaylistName,
                        playlistTitle = finalPlaylistName,
                        playlistId = rawPlaylistId,
                        playlistCoverUrl = rawPlaylistCover
                    )
                } else {
                    rawNextTrack
                }

                // ID bazlı çıkarma: Kesin olarak bu parçayı kuyruktan çıkar
                _uiState.update { state ->
                    val remaining = state.downloadQueue.filterNot { it.id == rawNextTrack.id || it.id in cancelledOrRemovedQueueTrackIds }
                    state.copy(
                        currentDownloadingTrack = nextTrack,
                        downloadQueue = remaining
                    )
                }
                persistCurrentDownloadQueue()

                // İndirmeye başlamadan önce iptal/çıkarılma kontrolü
                if (nextTrack.id in cancelledOrRemovedQueueTrackIds || !isActive) {
                    _uiState.update { cur ->
                        if (cur.currentDownloadingTrack?.id == nextTrack.id) {
                            cur.copy(currentDownloadingTrack = null)
                        } else cur
                    }
                    persistCurrentDownloadQueue()
                    continue
                }

                val format = _uiState.value.selectedDownloadFormat
                val rawUrl = nextTrack.sourceUrl.ifBlank { "https://www.youtube.com/watch?v=${nextTrack.id}" }
                val cleanUrl = if (nextTrack.id.length == 11) {
                    "https://www.youtube.com/watch?v=${nextTrack.id}"
                } else if (rawUrl.contains("watch?v=") && rawUrl.contains("&list=")) {
                    rawUrl.substringBefore("&list=")
                } else {
                    rawUrl
                }

                val state = youtubeDLDownloadManager.downloadTrack(
                    url = cleanUrl,
                    formatOption = format,
                    cookies = cookies,
                    existingTrackInfo = nextTrack
                )
                processedInThisRun++

                if (state is YoutubeDLDownloadState.Cancelled) {
                    if (nextTrack.id in cancelledOrRemovedQueueTrackIds) {
                        _uiState.update { cur ->
                            if (cur.currentDownloadingTrack?.id == nextTrack.id) {
                                cur.copy(
                                    currentDownloadingTrack = null,
                                    statusBannerMessage = "'${nextTrack.title}' indirmesi iptal edildi. Sıradaki parçaya geçiliyor..."
                                )
                            } else cur
                        }
                        persistCurrentDownloadQueue()
                        continue
                    } else {
                        break
                    }
                }

                when (state) {
                    is YoutubeDLDownloadState.Success -> {
                        completed++
                        val saved = state.savedMedia
                        if (!finalPlaylistName.isNullOrBlank()) {
                            playlistRepository.registerDownloadedPlaylistTrack(
                                saved = saved,
                                explicitPlaylistTitle = finalPlaylistName,
                                explicitPlaylistId = rawPlaylistId,
                                explicitPlaylistCoverUrl = rawPlaylistCover
                            )
                        }
                        if (nextTrack.album.contains("Beğenilen", ignoreCase = true) ||
                            _uiState.value.resolvedPlaylistTitle?.contains("Beğenilen", ignoreCase = true) == true
                        ) {
                            autoAddSavedTrackToFavoritesPlaylist(saved)
                        }
                        refreshDownloadedTracks()
                        val actualExt = saved.audioFile.extension.uppercase()
                        val opusFallbackNotice = if (format == DownloadFormatOption.AUDIO_OPUS_BEST &&
                            !saved.audioFile.extension.equals("opus", ignoreCase = true)
                        ) {
                            " • Bilgi: Bu videoda doğal Opus bulunamadı, en iyi $actualExt akışına geçildi."
                        } else ""

                        _uiState.update { cur ->
                            cur.copy(
                                batchDownloadCompletedCount = completed,
                                lastSavedMedia = saved,
                                failedQueueTracks = cur.failedQueueTracks.filterNot { f -> f.id == nextTrack.id },
                                statusBannerMessage = if (cur.downloadQueue.isEmpty() && cur.batchDownloadTotalCount <= 1) {
                                    "İndirildi: ${saved.trackInfo.artist} - ${saved.trackInfo.title} ($actualExt)$opusFallbackNotice"
                                } else {
                                    val listBadge = finalPlaylistName?.let { " [$it]" } ?: ""
                                    "İndirildi ($completed/${cur.batchDownloadTotalCount})$listBadge: ${saved.trackInfo.title} • Sırada: ${cur.downloadQueue.size}"
                                }
                            )
                        }
                        if (_uiState.value.batchDownloadTotalCount <= 1) {
                            fetchLyricsForTrack(saved.trackInfo, saved.audioFile)
                        }
                    }
                    is YoutubeDLDownloadState.Error -> {
                        _uiState.update { cur ->
                            val updatedFailed = if (cur.failedQueueTracks.none { f -> f.id == nextTrack.id }) {
                                cur.failedQueueTracks + nextTrack
                            } else {
                                cur.failedQueueTracks
                            }
                            cur.copy(
                                failedQueueTracks = updatedFailed,
                                errorMessage = if (cur.batchDownloadTotalCount <= 1) {
                                    state.message + (state.technicalDetails?.let { d -> "\nDetay: $d" } ?: "")
                                } else {
                                    cur.errorMessage
                                }
                            )
                        }
                    }
                    else -> Unit
                }
            }

            if (_uiState.value.downloadQueue.isEmpty()) {
                downloadPrefs.clearPendingDownloadQueue()
            } else {
                persistCurrentDownloadQueue()
            }
            refreshDownloadedTracks()
            val total = _uiState.value.batchDownloadTotalCount
            _uiState.update {
                it.copy(
                    isBatchDownloadingPlaylist = it.downloadQueue.isNotEmpty(),
                    currentDownloadingTrack = null,
                    activeIntervalCountdownSeconds = 0,
                    statusBannerMessage = if (total > 1 && it.downloadQueue.isEmpty()) {
                        "Toplu indirme tamamlandı: $completed / $total parça cihazınıza kaydedildi."
                    } else {
                        it.statusBannerMessage
                    }
                )
            }
        }
    }

    private suspend fun autoAddSavedTrackToFavoritesPlaylist(saved: SavedMediaResult) {
        try {
            var currentPlaylists = playlistRepository.loadPlaylists()
            var favPlaylist = currentPlaylists.firstOrNull {
                it.name.equals("Favoriler", ignoreCase = true) ||
                    it.name.equals("Beğenilenler", ignoreCase = true)
            }
            if (favPlaylist == null) {
                currentPlaylists = playlistRepository.createPlaylist(
                    name = "Favoriler",
                    description = "YouTube Music Beğenilen Şarkılar otomatik listesi",
                    coverImageUri = saved.coverFile?.absolutePath ?: saved.trackInfo.thumbnailUrl
                )
                favPlaylist = currentPlaylists.firstOrNull { it.name.equals("Favoriler", ignoreCase = true) }
            }
            if (favPlaylist != null) {
                val updated = playlistRepository.addTrackToPlaylist(favPlaylist.id, saved)
                _uiState.update { it.copy(playlists = updated) }
            }
        } catch (_: Throwable) {
        }
    }

    fun downloadAllResolvedPlaylistTracks() = downloadSelectedPlaylistTracks()

    fun startYoutubeDLDownload(
        customUrl: String? = null,
        existingTrack: MediaTrackInfo? = null
    ) {
        val targetUrl = (customUrl ?: _uiState.value.youtubeUrlInput.ifBlank {
            _uiState.value.currentTrack?.sourceUrl.orEmpty()
        }).trim()

        if (targetUrl.isEmpty()) {
            _uiState.update {
                it.copy(errorMessage = "İndirmek için önce bir bağlantı girin veya bir şarkı seçin.")
            }
            return
        }

        val playlistId = streamResolver.extractPlaylistId(targetUrl)
        val videoId = streamResolver.extractVideoId(targetUrl)
        val isPurePlaylist = (!playlistId.isNullOrBlank() && videoId.isNullOrBlank()) ||
            targetUrl.contains("playlist?list=") ||
            targetUrl.contains("music.youtube.com/playlist")

        if (isPurePlaylist) {
            activeStreamResolutionJob?.cancel()
            activeStreamResolutionJob = viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isResolvingStream = true,
                        statusBannerMessage = "Çalma listesi indirilmek üzere çözümleniyor..."
                    )
                }
                val (_, savedCookies) = cookieStorage.loadCookies()
                val cookies = NetscapeCookieHelper.resolveActiveCookies(appContext, savedCookies)
                NetscapeCookieHelper.writeNetscapeCookieFile(appContext, cookies)
                val result = streamResolver.resolvePlaylist(targetUrl, cookies)
                if (result.tracks.isNotEmpty()) {
                    val cleanTitle = result.title.trim().ifBlank { "Oynatma Listesi" }
                    val coverUrl = result.thumbnailUrl ?: result.tracks.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
                    val stampedTracks = result.tracks.map { track ->
                        track.copy(
                            album = cleanTitle,
                            playlistId = result.playlistId,
                            playlistTitle = cleanTitle,
                            playlistCoverUrl = coverUrl
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isResolvingStream = false,
                            resolvedPlaylistTitle = cleanTitle,
                            resolvedPlaylistSourceId = result.playlistId,
                            resolvedPlaylistCoverUrl = coverUrl,
                            resolvedPlaylistTracks = stampedTracks,
                            selectedPlaylistTrackIds = stampedTracks.map { t -> t.id }.toSet()
                        )
                    }
                    downloadAllResolvedPlaylistTracks()
                } else {
                    _uiState.update {
                        it.copy(
                            isResolvingStream = false,
                            errorMessage = "Çalma listesindeki parçalar çözümlenemedi."
                        )
                    }
                }
            }
            return
        }

        val cleanSingleUrl = if (targetUrl.contains("watch?v=") && targetUrl.contains("&list=")) {
            targetUrl.substringBefore("&list=")
        } else targetUrl

        val trackToQueue = existingTrack ?: _uiState.value.currentTrack?.takeIf {
            it.sourceUrl == targetUrl || it.id == targetUrl || it.id == videoId
        } ?: MediaTrackInfo(
            id = videoId ?: cleanSingleUrl,
            title = cleanSingleUrl,
            artist = "Sakura İndirici",
            durationSeconds = 0L,
            thumbnailUrl = videoId?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" },
            sourceUrl = cleanSingleUrl,
            streamUrl = cleanSingleUrl
        )

        enqueueTracksForDownload(listOf(trackToQueue))
    }

    fun cancelYoutubeDLDownload() {
        val currentActive = _uiState.value.currentDownloadingTrack
        if (currentActive != null) {
            cancelledOrRemovedQueueTrackIds.add(currentActive.id)
        }
        cancelledOrRemovedQueueTrackIds.addAll(_uiState.value.downloadQueue.map { it.id })
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        activeStreamResolutionJob?.cancel()
        activeStreamResolutionJob = null
        youtubeDLDownloadManager.cancelActiveDownload()
        chunkedDownloader.cancel()
        downloadPrefs.clearPendingDownloadQueue()
        _uiState.update {
            it.copy(
                isResolvingStream = false,
                isBatchDownloadingPlaylist = false,
                currentDownloadingTrack = null,
                downloadQueue = emptyList(),
                isBuffering = false,
                statusBannerMessage = "İndirme ve bekleyen kuyruk iptal edildi."
            )
        }
    }

    fun startCurrentTrackDownload() {
        val current = _uiState.value.currentTrack
        if (current != null) {
            startYoutubeDLDownload(
                customUrl = current.sourceUrl.ifBlank { "https://www.youtube.com/watch?v=${current.id}" },
                existingTrack = current
            )
        } else {
            startYoutubeDLDownload()
        }
    }

    fun cancelActiveDownload() {
        cancelYoutubeDLDownload()
    }

    fun startSampleDownload() {
        val current = _uiState.value.currentTrack
        if (current == null || !current.streamUrl.startsWith("http")) {
            startCurrentTrackDownload()
            return
        }

        viewModelScope.launch {
            val (_, cookies) = cookieStorage.loadCookies()
            val cookieHeader = NetscapeCookieHelper.buildCookieHeaderString(cookies)
            val musicDir = metadataManager.getAppMusicDirectory()
            val baseName = metadataManager.buildTrackBaseFileName(current)
            val destFile = File(musicDir, "$baseName.m4a")

            val result = chunkedDownloader.downloadFileSuspend(
                fileUrl = current.streamUrl,
                destinationFile = destFile,
                cookieHeader = cookieHeader,
                listener = object : DownloadListener {
                    override fun onProgress(progress: DownloadProgress) {
                        _uiState.update { it.copy(downloadProgress = progress) }
                    }

                    override fun onComplete(outputFile: File) = Unit
                    override fun onError(errorMessage: String) {
                        _uiState.update { it.copy(errorMessage = errorMessage) }
                    }
                }
            )

            result.onSuccess { file ->
                val savedRes = metadataManager.finalizeDownloadedMedia(file, current)
                savedRes.onSuccess { saved ->
                    refreshDownloadedTracks()
                    _uiState.update {
                        it.copy(
                            lastSavedMedia = saved,
                            statusBannerMessage = "Çoklu kanal indirmesi tamamlandı: ${saved.audioFile.name}"
                        )
                    }
                }
            }
        }
    }

    private fun stopOnlinePlaybackOnly() {
        onlineProgressJob?.cancel()
        onlineProgressJob = null
        val oldMp = onlineMediaPlayer
        onlineMediaPlayer = null
        if (oldMp != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    com.example.player.AudioEffectManager.releaseOnlineEffects()
                } catch (_: Throwable) {
                }
                try {
                    com.example.player.StudioDspController.unbindMediaPlayer(oldMp)
                } catch (_: Throwable) {
                }
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
    }

    fun stopAllPlayback() {
        countedPlayForTrackId = null
        stopOnlinePlaybackOnly()

        try {
            localDownloadsPlayer.stopPlayback()
        } catch (_: Exception) {
        }

        _uiState.update {
            it.copy(
                isPlaying = false,
                isBuffering = false,
                currentPositionMs = 0L,
                lrcRawText = "",
                parsedLyrics = emptyList(),
                activeLyricIndex = -1
            )
        }
    }

    fun playSavedTrack(saved: SavedMediaResult) {
        stopOnlinePlaybackOnly()
        val currentId = saved.trackInfo.id.ifBlank { saved.audioFile.absolutePath }
        lastObservedTrackId = currentId
        countedPlayForTrackId = null

        val allDownloads = _uiState.value.downloadedTracks
        val idx = allDownloads.indexOfFirst {
            it.audioFile.absolutePath == saved.audioFile.absolutePath
        }
        if (idx >= 0) {
            localDownloadsPlayer.playQueue(allDownloads, idx)
        } else {
            localDownloadsPlayer.playQueue(listOf(saved), 0)
        }
        _uiState.update {
            it.copy(
                currentTrack = saved.trackInfo,
                lastSavedMedia = saved,
                isPlaying = true,
                isBuffering = false,
                currentPositionMs = 0L,
                durationMs = (saved.trackInfo.durationSeconds * 1000L).coerceAtLeast(1L),
                abLoopPointAMs = null,
                abLoopPointBMs = null,
                errorMessage = null
            )
        }
        fetchLyricsForTrack(saved.trackInfo, saved.audioFile)
    }

    fun playTopPlayedTrack(item: TopPlayedItem) {
        val saved = item.savedMedia
        if (saved != null) {
            playSavedTrack(saved)
        } else if (!item.streamUrl.isNullOrBlank()) {
            val streamTrack = MediaTrackInfo(
                id = item.id,
                title = item.title,
                artist = item.artist,
                durationSeconds = 180,
                thumbnailUrl = item.thumbnailUrl,
                streamUrl = item.streamUrl,
                sourceUrl = item.streamUrl
            )
            playOnlineStream(streamTrack)
        } else {
            val streamTrack = MediaTrackInfo(
                id = item.id,
                title = item.title,
                artist = item.artist,
                durationSeconds = 180,
                thumbnailUrl = item.thumbnailUrl,
                sourceUrl = "https://www.youtube.com/watch?v=${item.id}",
                streamUrl = ""
            )
            resolveAndPlayYouTubeInput(rawInput = streamTrack.sourceUrl, autoDownload = false, existingTrack = streamTrack)
        }
    }

    fun playPlaylistQueue(tracks: List<SavedMediaResult>, startIndex: Int) {
        if (tracks.isEmpty()) return
        stopOnlinePlaybackOnly()
        val safeIdx = startIndex.coerceIn(0, tracks.lastIndex)
        val chosen = tracks[safeIdx]
        val currentId = chosen.trackInfo.id.ifBlank { chosen.audioFile.absolutePath }
        lastObservedTrackId = currentId
        countedPlayForTrackId = null

        localDownloadsPlayer.playQueue(tracks, safeIdx)
        _uiState.update {
            it.copy(
                currentTrack = chosen.trackInfo,
                lastSavedMedia = chosen,
                isPlaying = true,
                isBuffering = false,
                currentPositionMs = 0L,
                durationMs = (chosen.trackInfo.durationSeconds * 1000L).coerceAtLeast(1L),
                abLoopPointAMs = null,
                abLoopPointBMs = null,
                errorMessage = null
            )
        }
        fetchLyricsForTrack(chosen.trackInfo, chosen.audioFile)
    }

    fun playCatalogTrack(track: MediaTrackInfo, autoDownload: Boolean = false) {
        val existingLocal = _uiState.value.downloadedTracks.firstOrNull {
            it.trackInfo.id == track.id && it.audioFile.exists() && it.audioFile.length() >= MediaMetadataManager.MIN_VALID_AUDIO_BYTES
        }
        if (existingLocal != null && !autoDownload) {
            playSavedTrack(existingLocal)
            return
        }

        stopAllPlayback()
        val url = track.sourceUrl.ifBlank { "https://www.youtube.com/watch?v=${track.id}" }
        _uiState.update {
            it.copy(
                youtubeUrlInput = url,
                currentTrack = track,
                isBuffering = true
            )
        }
        if (autoDownload) {
            startYoutubeDLDownload(customUrl = url, existingTrack = track)
        } else {
            resolveAndPlayYouTubeInput(rawInput = url, autoDownload = false, existingTrack = track)
        }
    }

    private fun playOnlineStream(track: MediaTrackInfo) {
        val streamUrl = track.streamUrl
        if (!streamUrl.startsWith("http")) {
            _uiState.update {
                it.copy(errorMessage = "Çevrimiçi ses akışı adresi geçerli değil.")
            }
            return
        }

        stopAllPlayback()

        _uiState.update {
            it.copy(
                currentTrack = track,
                isBuffering = true,
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = track.durationSeconds * 1000L,
                errorMessage = null
            )
        }

        try {
            val (_, savedCookies) = cookieStorage.loadCookies()
            val activeCookies = NetscapeCookieHelper.resolveActiveCookies(appContext, savedCookies)
            val cookieHeader = NetscapeCookieHelper.buildCookieHeaderString(activeCookies, appContext)
            val headers = mutableMapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
                "Origin" to "https://music.youtube.com",
                "Referer" to "https://music.youtube.com/"
            )
            if (!cookieHeader.isNullOrBlank()) {
                headers["Cookie"] = cookieHeader
            }

            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(appContext, android.net.Uri.parse(streamUrl), headers)
                setOnPreparedListener { player ->
                    if (onlineMediaPlayer !== player) {
                        try {
                            player.release()
                        } catch (_: Throwable) {
                        }
                        return@setOnPreparedListener
                    }
                    try {
                        val dur = player.duration.toLong().coerceAtLeast(track.durationSeconds * 1000L)
                        com.example.player.AudioEffectManager.applyOnlineVolumeBoost(
                            audioSessionId = player.audioSessionId,
                            percent = _uiState.value.volumeBoostPercent,
                            enabled = _uiState.value.isVolumeBoosterEnabled,
                            forceRecreate = true
                        )
                        com.example.player.StudioDspController.updateState(
                            _uiState.value.studioDspConfig,
                            _uiState.value.featureToggles
                        )
                        com.example.player.StudioDspController.bindMediaPlayer(player, player.audioSessionId)
                        player.start()
                        _uiState.update {
                            it.copy(
                                isBuffering = false,
                                isPlaying = true,
                                durationMs = dur
                            )
                        }
                        startOnlineProgressLoop()
                    } catch (_: Throwable) {
                    }
                }
                setOnCompletionListener {
                    _uiState.update {
                        it.copy(
                            isPlaying = false,
                            currentPositionMs = it.durationMs
                        )
                    }
                    onlineProgressJob?.cancel()
                    if (_uiState.value.featureToggles.smartAutoplayEnabled) {
                        triggerSmartAutoPlayAfterTrack(track)
                    }
                }
                setOnErrorListener { _, what, extra ->
                    _uiState.update {
                        it.copy(
                            isBuffering = false,
                            isPlaying = false,
                            errorMessage = "Çevrimiçi akış hatası ($what / $extra). İndirerek çevrimdışı dinleyebilirsiniz."
                        )
                    }
                    true
                }
                prepareAsync()
            }
            onlineMediaPlayer = mp
        } catch (e: Throwable) {
            _uiState.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    errorMessage = "Çevrimiçi oynatıcı başlatılamadı: ${e.localizedMessage}"
                )
            }
        }
    }

    private fun triggerSmartAutoPlayAfterTrack(finishedTrack: MediaTrackInfo) {
        viewModelScope.launch {
            val allDownloads = _uiState.value.downloadedTracks
            val candidateOffline = allDownloads.filter { it.trackInfo.id != finishedTrack.id }
            val sameArtistLocal = candidateOffline.firstOrNull {
                it.trackInfo.artist.equals(finishedTrack.artist, ignoreCase = true)
            } ?: candidateOffline.randomOrNull()

            if (sameArtistLocal != null) {
                _uiState.update {
                    it.copy(statusBannerMessage = "Akıllı Otomatik Oynatma: ${sameArtistLocal.trackInfo.artist} - ${sameArtistLocal.trackInfo.title}")
                }
                playSavedTrack(sameArtistLocal)
                return@launch
            }

            val (_, savedCookies) = cookieStorage.loadCookies()
            val cookies = NetscapeCookieHelper.resolveActiveCookies(appContext, savedCookies)
            val query = "${finishedTrack.artist} mix"
            val catalogCandidates = streamResolver.searchCatalog(query, cookies, maxResults = 6)
                .filter { it.id != finishedTrack.id }
            val nextOnline = catalogCandidates.firstOrNull()
            if (nextOnline != null) {
                _uiState.update {
                    it.copy(statusBannerMessage = "Akıllı Otomatik Oynatma: ${nextOnline.artist} - ${nextOnline.title}")
                }
                playCatalogTrack(nextOnline, autoDownload = false)
            }
        }
    }

    private fun startOnlineProgressLoop() {
        onlineProgressJob?.cancel()
        lastOnlineTickUptimeMs = android.os.SystemClock.elapsedRealtime()
        onlineProgressJob = viewModelScope.launch {
            while (isActive && onlineMediaPlayer != null) {
                val mp = onlineMediaPlayer
                if (mp != null) {
                    try {
                        if (mp.isPlaying) {
                            val now = android.os.SystemClock.elapsedRealtime()
                            val deltaMs = (now - lastOnlineTickUptimeMs).coerceIn(0L, 2000L)
                            lastOnlineTickUptimeMs = now

                            val pos = mp.currentPosition.toLong().coerceAtLeast(0L)
                            val curState = _uiState.value
                            val currentTrackId = curState.currentTrack?.id.orEmpty()

                            if (deltaMs > 0L) {
                                playCountManager.addListeningTime(currentTrackId, deltaMs)
                            }

                            val pointA = curState.abLoopPointAMs
                            val pointB = curState.abLoopPointBMs
                            if (curState.featureToggles.abLoopEnabled && pointA != null && pointB != null && pos >= pointB) {
                                mp.seekTo(pointA.toInt())
                            }
                            checkAndRecordPlayCount(currentTrackId, pos)
                            val lyricIdx = if (curState.isNowPlayingExpanded) {
                                findActiveLyricIndex(curState.parsedLyrics, pos)
                            } else {
                                curState.activeLyricIndex
                            }
                            _uiState.update {
                                it.copy(
                                    currentPositionMs = pos,
                                    activeLyricIndex = lyricIdx
                                )
                            }
                        } else {
                            lastOnlineTickUptimeMs = android.os.SystemClock.elapsedRealtime()
                        }
                    } catch (_: Throwable) {
                    }
                }
                delay(250L)
            }
        }
    }

    private fun releaseOnlineMediaPlayer() {
        stopAllPlayback()
    }

    fun togglePlayPause() {
        val mp = onlineMediaPlayer
        if (mp != null) {
            try {
                if (mp.isPlaying) {
                    mp.pause()
                    _uiState.update { it.copy(isPlaying = false) }
                } else {
                    mp.start()
                    _uiState.update { it.copy(isPlaying = true) }
                    startOnlineProgressLoop()
                }
            } catch (_: Throwable) {
                stopOnlinePlaybackOnly()
            }
            return
        }
        localDownloadsPlayer.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        val mp = onlineMediaPlayer
        if (mp != null) {
            try {
                val safe = positionMs.coerceIn(0L, _uiState.value.durationMs.coerceAtLeast(1L))
                mp.seekTo(safe.toInt())
                val lyricIdx = findActiveLyricIndex(_uiState.value.parsedLyrics, safe)
                _uiState.update {
                    it.copy(
                        currentPositionMs = safe,
                        activeLyricIndex = lyricIdx
                    )
                }
            } catch (_: Throwable) {
            }
            return
        }
        localDownloadsPlayer.seekTo(positionMs)
    }

    fun skipToNext() {
        localDownloadsPlayer.skipToNext()
    }

    fun skipToPrevious() {
        localDownloadsPlayer.skipToPrevious()
    }

    fun toggleShuffle() {
        localDownloadsPlayer.toggleShuffle()
    }

    fun cycleRepeatMode() {
        localDownloadsPlayer.cycleRepeatMode()
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        com.example.player.StudioDspController.setBasePlaybackSpeed(clamped)
        onlineMediaPlayer?.let { mp ->
            try {
                val wasPlaying = mp.isPlaying
                mp.playbackParams = mp.playbackParams.setSpeed(clamped)
                if (!wasPlaying && mp.isPlaying) {
                    mp.pause()
                }
            } catch (_: Exception) {
            }
        }
        localDownloadsPlayer.setPlaybackSpeed(clamped)
    }

    fun setSleepTimer(minutes: Int?) {
        localDownloadsPlayer.setSleepTimer(minutes)
        _uiState.update {
            it.copy(
                statusBannerMessage = if (minutes != null) {
                    "Uyku zamanlayıcısı $minutes dakikaya ayarlandı."
                } else {
                    "Uyku zamanlayıcısı kapatıldı."
                }
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        catalogSearchJob?.cancel()
        if (query.trim().length >= 2) {
            catalogSearchJob = viewModelScope.launch {
                delay(450L)
                triggerCatalogSearch(query)
            }
        } else {
            _uiState.update { it.copy(catalogSearchResults = emptyList(), isSearchingCatalog = false) }
        }
    }

    fun triggerCatalogSearch(customQuery: String? = null) {
        val q = (customQuery ?: _uiState.value.searchQuery).trim()
        if (q.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingCatalog = true) }
            val (_, cookies) = cookieStorage.loadCookies()
            val results = streamResolver.searchCatalog(q, cookies, maxResults = 15)
            _uiState.update {
                it.copy(
                    isSearchingCatalog = false,
                    catalogSearchResults = results
                )
            }
        }
    }

    fun refreshDownloadedTracks(force: Boolean = true) {
        if (!force && refreshTracksJob?.isActive == true) return
        refreshTracksJob?.cancel()
        refreshTracksJob = viewModelScope.launch(Dispatchers.IO + vmExceptionHandler) {
            val savedList = metadataManager.loadSavedDownloads()
            playlistRepository.reconcileDownloadedTracksWithPlaylists(savedList)
            val vaultList = vaultRepository.loadVaultItems()
            lastTracksRefreshUptimeMs = android.os.SystemClock.elapsedRealtime()
            localDownloadsPlayer.syncQueue(savedList)
            _uiState.update {
                it.copy(
                    downloadedTracks = savedList,
                    vaultItems = vaultList
                )
            }
        }
    }

    fun updateSavedTrackMetadata(
        saved: SavedMediaResult,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newCoverUrl: String?
    ) {
        viewModelScope.launch {
            val res = metadataManager.updateTrackMetadataAndRename(
                saved = saved,
                newTitle = newTitle,
                newArtist = newArtist,
                newAlbum = newAlbum,
                newCoverUrl = newCoverUrl
            )
            res.onSuccess { updated ->
                refreshDownloadedTracks()
                _uiState.update {
                    it.copy(
                        currentTrack = if (it.currentTrack?.id == saved.trackInfo.id) updated.trackInfo else it.currentTrack,
                        statusBannerMessage = "Etiket ve dosya adı güncellendi: ${updated.trackInfo.artist} - ${updated.trackInfo.title}"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(errorMessage = "Etiket güncellenemedi: ${err.localizedMessage}")
                }
            }
        }
    }

    fun autoFetchYoutubeTitleForTrack(
        saved: SavedMediaResult,
        onResult: (String, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val idCandidate = saved.trackInfo.id.takeIf { it.length == 11 }
                ?: Regex("""\(([a-zA-Z0-9_-]{11})\)""").find(saved.trackInfo.title)?.groupValues?.getOrNull(1)
                ?: Regex("""\[([a-zA-Z0-9_-]{11})]""").find(saved.audioFile.name)?.groupValues?.getOrNull(1)

            val pair = if (idCandidate != null) {
                metadataManager.fetchOembedTitleAndArtist(idCandidate)
            } else {
                null
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (pair != null) {
                    onResult(pair.first, pair.second)
                } else {
                    onResult("", "")
                    _uiState.update {
                        it.copy(statusBannerMessage = "Bu dosya için YouTube kimliği bulunamadı, ismi elle düzenleyebilirsiniz.")
                    }
                }
            }
        }
    }

    fun runLibraryDoctor() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(statusBannerMessage = "Kütüphane Doktoru taraması ve isim onarımı başlatıldı...")
            }
            val report = metadataManager.runLibraryDoctorCleanup()
            refreshDownloadedTracks()
            val freedKb = report.freedBytes / 1024L
            _uiState.update {
                it.copy(
                    statusBannerMessage = "Kütüphane Doktoru tamamlandı: ${report.repairedTitlesCount} isimsiz parça onarıldı, ${report.removedDuplicateCount} kopya ve ${report.removedCorruptedCount} bozuk dosya temizlendi ($freedKb KB boşaltıldı)."
                )
            }
        }
    }

    fun deleteDownloadedTrack(saved: SavedMediaResult) {
        viewModelScope.launch {
            val deleted = metadataManager.deleteSavedTrack(saved)
            if (deleted) {
                refreshDownloadedTracks()
                _uiState.update {
                    it.copy(statusBannerMessage = "'${saved.trackInfo.title}' silindi.")
                }
            }
        }
    }

    fun deleteVaultItem(item: VaultItem) {
        viewModelScope.launch {
            val deleted = vaultRepository.deleteVaultItem(item)
            if (deleted) {
                refreshDownloadedTracks()
                _uiState.update {
                    it.copy(
                        activeVaultItemForPlayback = if (it.activeVaultItemForPlayback?.id == item.id) null else it.activeVaultItemForPlayback,
                        statusBannerMessage = "'${item.title}' kasadan silindi."
                    )
                }
            }
        }
    }

    fun openVaultItemInExoPlayer(item: VaultItem) {
        releaseOnlineMediaPlayer()
        if (localDownloadsPlayer.playerState.value.isPlaying) {
            localDownloadsPlayer.togglePlayPause()
        }
        _uiState.update { it.copy(activeVaultItemForPlayback = item) }
    }

    fun closeVaultExoPlayer() {
        _uiState.update { it.copy(activeVaultItemForPlayback = null) }
    }

    fun removeTrackFromPlaybackQueue(index: Int) {
        val currentQueue = offlinePlayerState.value.queue
        val removed = currentQueue.getOrNull(index)
        localDownloadsPlayer.removeTrackFromQueue(index)
        if (removed != null) {
            _uiState.update {
                it.copy(
                    statusBannerMessage = "'${removed.trackInfo.title}' oynatma kuyruğundan kaldırıldı."
                )
            }
        }
    }

    fun removeTrackFromPlaybackQueueByTrackId(trackId: String) {
        val currentQueue = offlinePlayerState.value.queue
        val removed = currentQueue.firstOrNull { it.trackInfo.id == trackId || it.audioFile.absolutePath == trackId }
        localDownloadsPlayer.removeTrackFromQueueByTrackId(trackId)
        if (removed != null) {
            _uiState.update {
                it.copy(
                    statusBannerMessage = "'${removed.trackInfo.title}' oynatma kuyruğundan kaldırıldı."
                )
            }
        }
    }

    fun clearPlaybackQueue() {
        localDownloadsPlayer.clearQueue()
        _uiState.update {
            it.copy(
                statusBannerMessage = "Oynatma kuyruğu temizlendi."
            )
        }
    }

    fun playTrackFromPlaybackQueue(index: Int) {
        localDownloadsPlayer.playTrackFromQueue(index)
    }

    fun createPlaylist(name: String, description: String, coverUri: String?) {
        viewModelScope.launch {
            val updated = playlistRepository.createPlaylist(name, description, coverUri)
            _uiState.update {
                it.copy(
                    playlists = updated,
                    statusBannerMessage = "'$name' çalma listesi oluşturuldu."
                )
            }
        }
    }

    fun updatePlaylistCover(playlistId: String, coverUri: String?) {
        viewModelScope.launch {
            val updated = playlistRepository.updatePlaylistCover(playlistId, coverUri)
            _uiState.update {
                it.copy(
                    playlists = updated,
                    statusBannerMessage = "Çalma listesi kapağı güncellendi."
                )
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            val updated = playlistRepository.deletePlaylist(playlistId)
            _uiState.update {
                it.copy(
                    playlists = updated,
                    selectedPlaylistId = if (it.selectedPlaylistId == playlistId) null else it.selectedPlaylistId,
                    statusBannerMessage = "Çalma listesi silindi."
                )
            }
        }
    }

    fun openPlaylistDetail(playlistId: String) {
        _uiState.update { it.copy(selectedPlaylistId = playlistId) }
    }

    fun closePlaylistDetail() {
        _uiState.update { it.copy(selectedPlaylistId = null) }
    }

    fun addTrackToPlaylist(playlistId: String, track: SavedMediaResult) {
        viewModelScope.launch {
            val updated = playlistRepository.addTrackToPlaylist(playlistId, track)
            _uiState.update {
                it.copy(
                    playlists = updated,
                    statusBannerMessage = "'${track.trackInfo.title}' listeye eklendi."
                )
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, audioFilePath: String) {
        viewModelScope.launch {
            val updated = playlistRepository.removeTrackFromPlaylist(playlistId, audioFilePath)
            _uiState.update {
                it.copy(
                    playlists = updated,
                    statusBannerMessage = "Şarkı listeden çıkarıldı."
                )
            }
        }
    }

    fun addSubscription(
        title: String,
        url: String,
        formatOption: DownloadFormatOption,
        targetPlaylistTitle: String? = null,
        targetPlaylistId: String? = null
    ) {
        val updated = subscriptionStorage.addSubscription(
            title = title,
            url = url,
            formatName = formatOption.name,
            targetPlaylistTitle = targetPlaylistTitle,
            targetPlaylistId = targetPlaylistId
        )
        AutoSyncScheduler.schedulePeriodicSync(appContext)
        val targetMsg = if (!targetPlaylistTitle.isNullOrBlank()) " (Hedef: $targetPlaylistTitle)" else ""
        _uiState.update {
            it.copy(
                subscriptions = updated,
                statusBannerMessage = "'$title' otomatik senkronizasyona eklendi$targetMsg."
            )
        }
    }

    fun updateSubscriptionTarget(id: String, targetPlaylistTitle: String?, targetPlaylistId: String?) {
        val updated = subscriptionStorage.updateSubscriptionTarget(id, targetPlaylistTitle, targetPlaylistId)
        val targetName = targetPlaylistTitle?.takeIf { it.isNotBlank() } ?: "Varsayılan Kitaplık"
        _uiState.update {
            it.copy(
                subscriptions = updated,
                statusBannerMessage = "Kayıt hedefi güncellendi: $targetName"
            )
        }
    }

    fun removeSubscription(id: String) {
        val updated = subscriptionStorage.removeSubscription(id)
        _uiState.update {
            it.copy(
                subscriptions = updated,
                statusBannerMessage = "Abonelik kaldırıldı."
            )
        }
    }

    fun toggleSubscription(id: String, enabled: Boolean) {
        val updated = subscriptionStorage.toggleSubscription(id, enabled)
        _uiState.update { it.copy(subscriptions = updated) }
    }

    fun setAutoSyncWifiOnly(wifiOnly: Boolean) {
        subscriptionStorage.setAutoSyncWifiOnly(wifiOnly)
        AutoSyncScheduler.schedulePeriodicSync(appContext)
        _uiState.update { it.copy(isAutoSyncWifiOnly = wifiOnly) }
    }

    fun setAutoSyncIntervalMinutes(minutes: Long) {
        val safeMinutes = minutes.coerceAtLeast(15L)
        subscriptionStorage.setSyncIntervalMinutes(safeMinutes)
        AutoSyncScheduler.schedulePeriodicSync(appContext)
        _uiState.update {
            it.copy(
                autoSyncIntervalMinutes = safeMinutes,
                autoSyncIntervalHours = (safeMinutes / 60L).coerceAtLeast(1L)
            )
        }
    }

    fun setAutoSyncIntervalHours(hours: Long) {
        setAutoSyncIntervalMinutes(hours * 60L)
    }

    fun triggerImmediateSubscriptionSync() {
        AutoSyncScheduler.triggerImmediateSync(appContext)
        AutoSyncScheduler.triggerImmediateLikedSongsWifiSync(appContext)
        _uiState.update {
            it.copy(statusBannerMessage = "Arka plan senkronizasyonu (ve Wi-Fi Beğenilenler kontrolü) başlatıldı.")
        }
    }

    fun setLikedSongsWifiAutoSyncEnabled(enabled: Boolean) {
        subscriptionStorage.setLikedSongsWifiAutoSyncEnabled(enabled)
        AutoSyncScheduler.scheduleLikedSongsWifiSync(appContext)
        if (enabled) {
            AutoSyncScheduler.triggerImmediateLikedSongsWifiSync(appContext)
        }
        _uiState.update {
            it.copy(
                isLikedSongsWifiAutoSyncEnabled = enabled,
                statusBannerMessage = if (enabled) {
                    "YT Music Beğenilenler (VLLM) Wi-Fi Otomatik İndirme aktif edildi. Wi-Fi bağlı olduğunda yeni beğenilen şarkılar arka planda sessizce indirilir."
                } else {
                    "YT Music Beğenilenler (VLLM) Wi-Fi Otomatik İndirme kapatıldı."
                }
            )
        }
    }

    /**
     * Çekirdek indirme motoruna (DownloadEngine) dokunmadan, ayrı bir arka plan iş parçacığında
     * (Dispatchers.IO) tüm yerel kütüphanedeki şarkıların sözlerini (LRCLIB & YT Music) bulur,
     * Türkçeye çevirir ve kalıcı .LRC olarak diske kaydeder.
     */
    fun batchFetchAndTranslateAllLyrics() {
        if (_uiState.value.isBatchFetchingLyrics) {
            batchLyricsJob?.cancel()
            batchLyricsJob = null
            _uiState.update {
                it.copy(
                    isBatchFetchingLyrics = false,
                    statusBannerMessage = "Toplu şarkı sözü bulma ve Türkçe çeviri işlemi durduruldu."
                )
            }
            return
        }

        val tracks = _uiState.value.downloadedTracks
        if (tracks.isEmpty()) {
            _uiState.update {
                it.copy(statusBannerMessage = "Kitaplığınızda henüz yerel şarkı bulunmuyor.")
            }
            return
        }

        batchLyricsJob?.cancel()
        batchLyricsJob = viewModelScope.launch(Dispatchers.IO + vmExceptionHandler) {
            _uiState.update {
                it.copy(
                    isBatchFetchingLyrics = true,
                    batchLyricsCompletedCount = 0,
                    batchLyricsTotalCount = tracks.size,
                    statusBannerMessage = "Tüm şarkılar için söz bulma ve Türkçe çeviri başlatıldı (0/${tracks.size})..."
                )
            }

            val orderedSources = downloadPrefs.getOrderedActiveLyricsSources()
            val engine = downloadPrefs.translationEngine
            var foundAndTranslatedCount = 0

            tracks.forEachIndexed { index, saved ->
                if (!isActive) return@launch
                val track = saved.trackInfo
                val audioFile = saved.audioFile

                try {
                    var rawLrc = LyricsProvider.getCachedRawLyrics(track.title, track.artist, audioFile)
                    if (rawLrc.isNullOrBlank()) {
                        val fetched = LyricsProvider.fetchSyncedLyricsWithSource(
                            title = track.title,
                            artist = track.artist,
                            durationSeconds = track.durationSeconds,
                            localAudioFile = audioFile,
                            orderedSources = orderedSources,
                            forceNetworkRefresh = false
                        )
                        rawLrc = fetched?.lrcContent
                    }

                    if (!rawLrc.isNullOrBlank()) {
                        val parsed = LrcParser.parse(rawLrc)
                        if (parsed.isNotEmpty()) {
                            val translated = LyricsTranslator.translateLyricLinesToTurkish(parsed, engine)
                            // Çevrilen sözleri çift dilli / Türkçe destekli kalıcı .LRC olarak kaydet
                            val formattedLrc = buildString {
                                translated.forEach { line ->
                                    val totalSec = (line.timeMs / 1000L).coerceAtLeast(0L)
                                    val mm = totalSec / 60L
                                    val ss = totalSec % 60L
                                    val cs = (line.timeMs % 1000L) / 10L
                                    val trSuffix = line.translatedText?.takeIf {
                                        it.isNotBlank() && !it.equals(line.text, ignoreCase = true)
                                    }?.let { " • [TR: $it]" } ?: ""
                                    append("[%02d:%02d.%02d]%s%s\n".format(mm, ss, cs, line.text, trSuffix))
                                }
                            }
                            LyricsProvider.saveCustomLyrics(
                                title = track.title,
                                artist = track.artist,
                                lrcText = formattedLrc.ifBlank { rawLrc },
                                localAudioFile = audioFile
                            )
                            foundAndTranslatedCount++

                            // Eğer şu an çalan şarkı buysa, ekrandaki sözleri de anında güncelle
                            if (_uiState.value.currentTrack?.id == track.id) {
                                _uiState.update { state ->
                                    state.copy(
                                        lrcRawText = rawLrc,
                                        parsedLyrics = translated,
                                        activeLyricIndex = findActiveLyricIndex(translated, state.currentPositionMs)
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {
                }

                val done = index + 1
                _uiState.update {
                    it.copy(
                        batchLyricsCompletedCount = done,
                        statusBannerMessage = "Söz Bul & Çevir: $done/${tracks.size} tarandı ($foundAndTranslatedCount şarkı çevrildi)..."
                    )
                }
            }

            _uiState.update {
                it.copy(
                    isBatchFetchingLyrics = false,
                    statusBannerMessage = "Tamamlandı: ${tracks.size} şarkı tarandı, $foundAndTranslatedCount şarkının sözleri bulunup Türkçeye çevrildi ve kaydedildi."
                )
            }
        }
    }

    private fun prewarmNextQueueTrack(pState: PlayerState) {
        val queue = pState.queue
        if (queue.size <= 1) return
        val nextIdx = (pState.currentIndex + 1) % queue.size
        val nextSaved = queue.getOrNull(nextIdx) ?: return

        prewarmNextTrackJob?.cancel()
        prewarmNextTrackJob = viewModelScope.launch(Dispatchers.IO) {
            delay(350L)
            val curState = _uiState.value
            if (curState.featureToggles.dynamicCoverPaletteEnabled) {
                com.example.ui.theme.DynamicSakuraPaletteExtractor.prewarmPalette(
                    imageUrl = nextSaved.coverFile?.absolutePath ?: nextSaved.trackInfo.thumbnailUrl,
                    baseTheme = curState.selectedTheme
                )
            }
            val nextLrc = LyricsProvider.fetchSyncedLyrics(
                title = nextSaved.trackInfo.title,
                artist = nextSaved.trackInfo.artist,
                durationSeconds = nextSaved.trackInfo.durationSeconds,
                localAudioFile = nextSaved.audioFile
            )
            if (!nextLrc.isNullOrBlank() && downloadPrefs.autoTranslateLyricsToTurkish) {
                val parsed = LrcParser.parse(nextLrc)
                LyricsTranslator.translateLyricLinesToTurkish(parsed, downloadPrefs.translationEngine)
            }
        }
    }

    private fun fetchLyricsForTrack(track: MediaTrackInfo, localFile: File?, forceRefresh: Boolean = false) {
        activeLyricsJob?.cancel()

        // Fast-path: 0ms in-memory cache check before launching network/disk work
        if (!forceRefresh) {
            val cachedRaw = LyricsProvider.getCachedRawLyrics(track.title, track.artist, localFile)
            if (!cachedRaw.isNullOrBlank()) {
                val cachedSource = LyricsProvider.getLastResolvedSourceLabel(track.title, track.artist, localFile) ?: "Yerel .LRC"
                val baseParsed = LrcParser.parse(cachedRaw)
                val cachedTranslated = if (downloadPrefs.autoTranslateLyricsToTurkish) {
                    LyricsTranslator.getCachedTranslation(baseParsed, downloadPrefs.translationEngine)
                } else null

                val immediateLines = cachedTranslated ?: baseParsed
                _uiState.update {
                    it.copy(
                        isFetchingLyrics = false,
                        lrcRawText = cachedRaw,
                        parsedLyrics = immediateLines,
                        activeLyricsSourceLabel = cachedSource,
                        activeLyricIndex = findActiveLyricIndex(immediateLines, it.currentPositionMs)
                    )
                }
                if (downloadPrefs.autoTranslateLyricsToTurkish && cachedTranslated == null) {
                    activeLyricsJob = viewModelScope.launch(Dispatchers.IO + vmExceptionHandler) {
                        val translated = LyricsTranslator.translateLyricLinesToTurkish(baseParsed, downloadPrefs.translationEngine)
                        _uiState.update { state ->
                            if (state.currentTrack?.id == track.id) {
                                state.copy(
                                    parsedLyrics = translated,
                                    activeLyricIndex = findActiveLyricIndex(translated, state.currentPositionMs)
                                )
                            } else state
                        }
                    }
                }
                return
            }
        }

        activeLyricsJob = viewModelScope.launch(Dispatchers.IO + vmExceptionHandler) {
            _uiState.update {
                it.copy(
                    isFetchingLyrics = true,
                    lrcRawText = "",
                    parsedLyrics = emptyList(),
                    activeLyricsSourceLabel = null,
                    activeLyricIndex = -1
                )
            }
            val orderedActive = downloadPrefs.getOrderedActiveLyricsSources()
            val fetchResult = LyricsProvider.fetchSyncedLyricsWithSource(
                title = track.title,
                artist = track.artist,
                durationSeconds = track.durationSeconds,
                localAudioFile = localFile,
                orderedSources = orderedActive,
                forceNetworkRefresh = forceRefresh
            )
            if (!isActive) return@launch

            val lrc = fetchResult?.lrcContent
            val resolvedSourceLabel = fetchResult?.sourceLabel
            if (!lrc.isNullOrBlank()) {
                val initialParsed = LrcParser.parse(lrc)
                val cachedTr = if (downloadPrefs.autoTranslateLyricsToTurkish) {
                    LyricsTranslator.getCachedTranslation(initialParsed, downloadPrefs.translationEngine)
                } else null

                val firstStageLines = cachedTr ?: initialParsed
                // Stage 1: Show synced lyrics immediately without waiting for network translation
                _uiState.update { state ->
                    if (state.currentTrack?.id == track.id) {
                        state.copy(
                            isFetchingLyrics = false,
                            lrcRawText = lrc,
                            parsedLyrics = firstStageLines,
                            activeLyricsSourceLabel = resolvedSourceLabel,
                            activeLyricIndex = findActiveLyricIndex(firstStageLines, state.currentPositionMs),
                            statusBannerMessage = if (forceRefresh && resolvedSourceLabel != null) {
                                "Şarkı sözü '$resolvedSourceLabel' kaynağından indirildi ve .LRC olarak kaydedildi."
                            } else state.statusBannerMessage
                        )
                    } else state
                }

                // Stage 2: Stream in Turkish translation asynchronously if not already cached
                if (downloadPrefs.autoTranslateLyricsToTurkish && cachedTr == null && isActive) {
                    val translated = LyricsTranslator.translateLyricLinesToTurkish(
                        initialParsed,
                        downloadPrefs.translationEngine
                    )
                    if (isActive) {
                        _uiState.update { state ->
                            if (state.currentTrack?.id == track.id) {
                                state.copy(
                                    parsedLyrics = translated,
                                    activeLyricIndex = findActiveLyricIndex(translated, state.currentPositionMs)
                                )
                            } else state
                        }
                    }
                }
            } else {
                _uiState.update { state ->
                    if (state.currentTrack?.id == track.id) {
                        state.copy(
                            isFetchingLyrics = false,
                            lrcRawText = "Söz bulunamadı.",
                            parsedLyrics = emptyList(),
                            activeLyricsSourceLabel = null,
                            activeLyricIndex = -1
                        )
                    } else state
                }
            }
        }
    }

    fun moveLyricsSourceUp(source: LyricsSource) {
        val currentOrder = _uiState.value.lyricsSourceOrder.toMutableList()
        val idx = currentOrder.indexOf(source)
        if (idx > 0) {
            val prev = currentOrder[idx - 1]
            currentOrder[idx - 1] = source
            currentOrder[idx] = prev
            downloadPrefs.lyricsSourceOrder = currentOrder
            _uiState.update {
                it.copy(
                    lyricsSourceOrder = currentOrder,
                    statusBannerMessage = "Şarkı sözü önceliği güncellendi: 1. sırada ${currentOrder.first().shortBadge}"
                )
            }
        }
    }

    fun moveLyricsSourceDown(source: LyricsSource) {
        val currentOrder = _uiState.value.lyricsSourceOrder.toMutableList()
        val idx = currentOrder.indexOf(source)
        if (idx in 0 until currentOrder.lastIndex) {
            val next = currentOrder[idx + 1]
            currentOrder[idx + 1] = source
            currentOrder[idx] = next
            downloadPrefs.lyricsSourceOrder = currentOrder
            _uiState.update {
                it.copy(
                    lyricsSourceOrder = currentOrder,
                    statusBannerMessage = "Şarkı sözü önceliği güncellendi: 1. sırada ${currentOrder.first().shortBadge}"
                )
            }
        }
    }

    fun toggleLyricsSourceEnabled(source: LyricsSource, enabled: Boolean) {
        val currentSet = _uiState.value.enabledLyricsSources.toMutableSet()
        if (enabled) {
            currentSet.add(source)
        } else if (currentSet.size > 1) {
            currentSet.remove(source)
        } else {
            _uiState.update {
                it.copy(statusBannerMessage = "En az 1 şarkı sözü kaynağı aktif kalmalıdır.")
            }
            return
        }
        downloadPrefs.enabledLyricsSources = currentSet
        _uiState.update {
            it.copy(enabledLyricsSources = currentSet)
        }
    }

    fun resetLyricsSourceOrder() {
        val defOrder = LyricsSource.DEFAULT_ORDER
        val defEnabled = defOrder.toSet()
        downloadPrefs.lyricsSourceOrder = defOrder
        downloadPrefs.enabledLyricsSources = defEnabled
        _uiState.update {
            it.copy(
                lyricsSourceOrder = defOrder,
                enabledLyricsSources = defEnabled,
                statusBannerMessage = "5 şarkı sözü kaynağı varsayılan sıraya sıfırlandı."
            )
        }
    }

    fun refetchCurrentTrackLyrics() {
        val currentTrack = _uiState.value.currentTrack ?: return
        val currentMedia = _uiState.value.lastSavedMedia ?: _uiState.value.downloadedTracks.firstOrNull {
            it.trackInfo.id == currentTrack.id ||
                (it.trackInfo.title.equals(currentTrack.title, ignoreCase = true) &&
                    it.trackInfo.artist.equals(currentTrack.artist, ignoreCase = true))
        }
        fetchLyricsForTrack(currentTrack, currentMedia?.audioFile, forceRefresh = true)
    }

    fun setAutoTranslateLyricsToTurkish(enabled: Boolean) {
        downloadPrefs.autoTranslateLyricsToTurkish = enabled
        _uiState.update {
            it.copy(
                autoTranslateLyricsToTurkish = enabled,
                statusBannerMessage = if (enabled) "Şarkı sözleri Türkçe'ye çevrilecek." else "Otomatik çeviri kapatıldı."
            )
        }
        val currentTrack = _uiState.value.currentTrack
        val currentMedia = _uiState.value.lastSavedMedia
        if (currentTrack != null) {
            fetchLyricsForTrack(currentTrack, currentMedia?.audioFile)
        }
    }

    fun setTranslationEngine(engine: TranslationEngine) {
        downloadPrefs.translationEngine = engine
        _uiState.update {
            it.copy(
                translationEngine = engine,
                statusBannerMessage = "Çeviri motoru '${engine.displayName}' olarak seçildi."
            )
        }
        val currentTrack = _uiState.value.currentTrack
        val currentMedia = _uiState.value.lastSavedMedia
        if (currentTrack != null) {
            fetchLyricsForTrack(currentTrack, currentMedia?.audioFile)
        }
    }

    fun updateLrcText(rawLrc: String) {
        val currentTrack = _uiState.value.currentTrack
        val currentMedia = _uiState.value.lastSavedMedia ?: _uiState.value.downloadedTracks.firstOrNull {
            it.trackInfo.id == currentTrack?.id ||
                (it.trackInfo.title.equals(currentTrack?.title, ignoreCase = true) &&
                    it.trackInfo.artist.equals(currentTrack?.artist, ignoreCase = true))
        }
        val audioFile = currentMedia?.audioFile

        if (currentTrack != null) {
            LyricsProvider.saveCustomLyrics(
                title = currentTrack.title,
                artist = currentTrack.artist,
                lrcText = rawLrc,
                localAudioFile = audioFile
            )
        }

        val parsed = LrcParser.parse(rawLrc)
        _uiState.update {
            it.copy(
                lrcRawText = rawLrc,
                parsedLyrics = parsed,
                activeLyricIndex = findActiveLyricIndex(parsed, it.currentPositionMs),
                statusBannerMessage = "Şarkı sözleri kalıcı olarak kaydedildi."
            )
        }

        if (downloadPrefs.autoTranslateLyricsToTurkish && currentTrack != null) {
            activeLyricsJob?.cancel()
            activeLyricsJob = viewModelScope.launch(Dispatchers.IO) {
                val translated = LyricsTranslator.translateLyricLinesToTurkish(parsed, downloadPrefs.translationEngine)
                _uiState.update { state ->
                    if (state.currentTrack?.id == currentTrack.id) {
                        state.copy(
                            parsedLyrics = translated,
                            activeLyricIndex = findActiveLyricIndex(translated, state.currentPositionMs)
                        )
                    } else state
                }
            }
        }
    }

    private fun findActiveLyricIndex(lyrics: List<LyricLine>, positionMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        var low = 0
        var high = lyrics.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lyrics[mid].timeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _uiState.update { state ->
            val updatedIndex = if (expanded) {
                findActiveLyricIndex(state.parsedLyrics, state.currentPositionMs)
            } else {
                state.activeLyricIndex
            }
            state.copy(
                isNowPlayingExpanded = expanded,
                activeLyricIndex = updatedIndex
            )
        }
    }

    fun dismissMessages() {
        _uiState.update {
            it.copy(
                statusBannerMessage = null,
                errorMessage = null
            )
        }
    }

    fun setDownloadQueueCenterOpen(open: Boolean) {
        if (open) openDownloadQueueCenter() else closeDownloadQueueCenter()
    }

    fun selectPlaylistRangeOneBased(startOneBased: Int, endOneBased: Int) {
        _uiState.update { current ->
            val tracks = current.resolvedPlaylistTracks
            if (tracks.isEmpty()) return@update current
            val total = tracks.size
            val normalizedStart = minOf(startOneBased, endOneBased).coerceIn(1, total)
            val normalizedEnd = maxOf(startOneBased, endOneBased).coerceIn(normalizedStart, total)
            val selectedSlice = tracks.subList(normalizedStart - 1, normalizedEnd).map { it.id }.toSet()
            current.copy(
                selectedPlaylistTrackIds = selectedSlice,
                errorMessage = null,
                statusBannerMessage = "$normalizedStart–$normalizedEnd aralığındaki ${selectedSlice.size} şarkı seçildi."
            )
        }
    }

    fun fetchAllUserLibraryPlaylists() {
        fetchUserLibraryPlaylistsWithCookies()
    }

    fun resolveRemoteLibraryPlaylist(summary: com.example.downloader.RemotePlaylistSummary) {
        selectRemoteLibraryPlaylist(summary)
    }

    fun cancelBackgroundJobsForTest() {
        initJob?.cancel()
        refreshTracksJob?.cancel()
        activeDownloadJob?.cancel()
        activeStreamResolutionJob?.cancel()
    }

    override fun onCleared() {
        initJob?.cancel()
        activeDownloadJob?.cancel()
        refreshTracksJob?.cancel()
        activeLyricsJob?.cancel()
        batchLyricsJob?.cancel()
        prewarmNextTrackJob?.cancel()
        catalogSearchJob?.cancel()
        activeStreamResolutionJob?.cancel()
        releaseOnlineMediaPlayer()
        localDownloadsPlayer.release()
        super.onCleared()
    }
}
