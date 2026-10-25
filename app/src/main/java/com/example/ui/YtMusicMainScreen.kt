package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.animation.LocalSakuraAnimationState
import com.example.ui.animation.SakuraAnimationRuntimeState
import com.example.ui.animation.buildFullPlayerEnterTransition
import com.example.ui.animation.buildFullPlayerExitTransition
import com.example.ui.animation.buildMiniPlayerEnterTransition
import com.example.ui.animation.buildMiniPlayerExitTransition
import com.example.ui.animation.buildSakuraTabTransition
import com.example.ui.animation.rememberSystemPowerSaveMode
import com.example.ui.components.FloatingSakuraPetalsOverlay
import com.example.ui.components.TouchSakuraBlossomOverlay
import com.example.ui.components.rememberTouchSakuraBurstController
import com.example.ui.navigation.YtBottomNavigationBar
import com.example.ui.player.MiniPlayerBar
import com.example.ui.player.NowPlayingScreen
import com.example.ui.player.VaultExoPlayerScreen
import com.example.ui.screens.CookieInspectorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SubscriptionManagerScreen
import com.example.ui.screens.VaultScreen
import androidx.compose.ui.graphics.Brush
import com.example.ui.theme.AppTheme
import com.example.ui.theme.LocalAppThemePalette
import com.example.ui.theme.backgroundGradientColors
import com.example.ui.theme.isSuperOptimizedTheme
import com.example.viewmodel.CookieViewModel
import com.example.viewmodel.MusicPlayerViewModel
import com.example.viewmodel.YtMusicTab

@Composable
fun YtMusicMainScreen(
    musicViewModel: MusicPlayerViewModel,
    cookieViewModel: CookieViewModel,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    // 200ms'lik currentPositionMs ve activeLyricIndex tiklerinin tüm ana ekranı ve aktif sekmeyi yeniden çizmesini (recompose) engeller
    val mainUiStateFlow = remember(musicViewModel) {
        musicViewModel.uiState.distinctUntilChanged { old, new ->
            old.copy(currentPositionMs = new.currentPositionMs, activeLyricIndex = new.activeLyricIndex) == new
        }
    }
    val state by mainUiStateFlow.collectAsState(initial = musicViewModel.uiState.value)
    val offlinePlayerState by musicViewModel.offlinePlayerState.collectAsState()
    val isPowerSave = rememberSystemPowerSaveMode()
    var showSubscriptionScreen by remember { mutableStateOf(false) }
    val tabSaveableStateHolder = rememberSaveableStateHolder()

    val animRuntimeState = remember(state.animationConfig, isPowerSave) {
        SakuraAnimationRuntimeState(
            config = state.animationConfig,
            isSystemPowerSaveMode = isPowerSave
        )
    }

    val backgroundDepthProgress by animateFloatAsState(
        targetValue = if (state.isNowPlayingExpanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (state.animationConfig.masterEnabled && state.animationConfig.screenTransitionsEnabled) {
                state.animationConfig.transitionSpeed.baseDurationMs.coerceIn(220, 420)
            } else {
                180
            },
            easing = FastOutSlowInEasing
        ),
        label = "main_scaffold_depth"
    )

    val homeListState = androidx.compose.foundation.lazy.rememberLazyListState()

    BackHandler(
        enabled = state.activeVaultItemForPlayback != null ||
            state.isNowPlayingExpanded ||
            state.isDownloadQueueCenterOpen ||
            showSubscriptionScreen ||
            state.selectedTab != YtMusicTab.HOME
    ) {
        when {
            state.activeVaultItemForPlayback != null -> musicViewModel.closeVaultExoPlayer()
            state.isNowPlayingExpanded -> musicViewModel.setNowPlayingExpanded(false)
            state.isDownloadQueueCenterOpen -> musicViewModel.setDownloadQueueCenterOpen(false)
            showSubscriptionScreen -> showSubscriptionScreen = false
            state.selectedTab != YtMusicTab.HOME -> musicViewModel.selectTab(YtMusicTab.HOME)
        }
    }

    CompositionLocalProvider(
        LocalSakuraAnimationState provides animRuntimeState,
        com.example.data.LocalSakuraTreeStyle provides state.selectedTreeStyle
    ) {
        val activeVaultItem = state.activeVaultItemForPlayback
        if (activeVaultItem != null) {
            VaultExoPlayerScreen(
                item = activeVaultItem,
                onClose = musicViewModel::closeVaultExoPlayer
            )
            return@CompositionLocalProvider
        }

        val isZeroParticleTheme = palette.theme.isSuperOptimizedTheme() || palette.theme == AppTheme.BERSERK_GRIFFITH

        val rootBgBrush = remember(palette.theme, palette.background) {
            Brush.verticalGradient(palette.theme.backgroundGradientColors(palette.background))
        }

        val rootModifier = modifier
            .fillMaxSize()
            .background(rootBgBrush)

        val isAnyTrackLoading = state.isResolvingStream ||
            state.isFetchingLyrics ||
            state.isSearchingCatalog ||
            (state.currentTrack != null && state.durationMs <= 0L)

        val sadeConfig = com.example.data.LocalCustomMinimalThemeConfig.current

        Box(
            modifier = rootModifier
        ) {
            // Griffith temasında ana ekran duvar kağıdı tamamen kaldırılmıştır; sadece mevcut tema yüzeyi/gradyanı kullanılır
            if (palette.theme != AppTheme.BERSERK_GRIFFITH && !sadeConfig.homeWallpaperPath.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = java.io.File(sadeConfig.homeWallpaperPath),
                    contentDescription = "Özel Ana Ekran Duvar Kağıdı",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = sadeConfig.homeWallpaperOpacity.coerceIn(0f, 1f)
                        }
                )
            }

            if (!isZeroParticleTheme &&
                !state.isNowPlayingExpanded &&
                !state.isDownloadQueueCenterOpen &&
                state.animationConfig.masterEnabled &&
                state.animationConfig.floatingPetalsEnabled
            ) {
                FloatingSakuraPetalsOverlay(
                    isLoading = isAnyTrackLoading,
                    isScrolling = homeListState.isScrollInProgress
                )
            }

            Scaffold(
                modifier = Modifier.graphicsLayer {
                    val scale = 1f - (backgroundDepthProgress * 0.035f)
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - (backgroundDepthProgress * 0.25f)
                },
                containerColor = Color.Transparent,
                bottomBar = {
                    Column {
                        val currentTrack = state.currentTrack
                        AnimatedVisibility(
                            visible = currentTrack != null && !state.isNowPlayingExpanded && !state.isDownloadQueueCenterOpen,
                            enter = buildMiniPlayerEnterTransition(state.animationConfig),
                            exit = buildMiniPlayerExitTransition(state.animationConfig),
                            label = "mini_player_visibility"
                        ) {
                            if (currentTrack != null) {
                                MiniPlayerProgressHost(
                                    musicViewModel = musicViewModel,
                                    track = currentTrack,
                                    isPlaying = state.isPlaying,
                                    isLoading = isAnyTrackLoading,
                                    onExpandClick = { musicViewModel.setNowPlayingExpanded(true) }
                                )
                            }
                        }

                        if (!state.isDownloadQueueCenterOpen) {
                            YtBottomNavigationBar(
                                selectedTab = state.selectedTab,
                                onTabSelected = { tab ->
                                    showSubscriptionScreen = false
                                    musicViewModel.selectTab(tab)
                                }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                ) {
                    val bannerMsg = state.errorMessage ?: state.statusBannerMessage
                    if (!bannerMsg.isNullOrBlank()) {
                        val isError = state.errorMessage != null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isError) palette.tertiaryAccent.copy(alpha = 0.22f)
                                    else palette.primaryAccent.copy(alpha = 0.18f)
                                )
                                .clickable { musicViewModel.dismissMessages() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = bannerMsg,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = musicViewModel::dismissMessages) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Kapat",
                                    tint = palette.onSurface
                                )
                            }
                        }
                    }

                    if (state.isDownloadQueueCenterOpen) {
                        com.example.ui.screens.DownloadQueueCenterScreen(
                            youtubeDLState = state.youtubeDLState,
                            selectedFormat = state.selectedDownloadFormat,
                            downloadIntervalDelaySeconds = state.featureToggles.downloadIntervalDelaySeconds,
                            activeIntervalCountdownSeconds = state.activeIntervalCountdownSeconds,
                            isDownloadQueuePaused = state.isDownloadQueuePaused,
                            downloadQueue = state.downloadQueue,
                            failedQueueTracks = state.failedQueueTracks,
                            batchDownloadCompletedCount = state.batchDownloadCompletedCount,
                            batchDownloadTotalCount = state.batchDownloadTotalCount,
                            resolvedPlaylistTitle = state.resolvedPlaylistTitle,
                            resolvedPlaylistTracks = state.resolvedPlaylistTracks,
                            selectedPlaylistTrackIds = state.selectedPlaylistTrackIds,
                            remoteLibraryPlaylists = state.remoteLibraryPlaylists,
                            isLoadingRemoteLibraryPlaylists = state.isLoadingRemoteLibraryPlaylists,
                            isResolvingStream = state.isResolvingStream,
                            onSelectFormat = musicViewModel::selectDownloadFormat,
                            onSelectIntervalDelaySeconds = musicViewModel::setDownloadIntervalDelaySeconds,
                            onFetchRemoteLibraryPlaylists = musicViewModel::fetchUserLibraryPlaylistsWithCookies,
                            onSelectRemoteLibraryPlaylist = musicViewModel::selectRemoteLibraryPlaylist,
                            onDownloadAccountLibraryWithCookies = musicViewModel::downloadAccountLibraryWithCookies,
                            onTogglePlaylistTrackSelection = musicViewModel::togglePlaylistTrackSelection,
                            onSelectAllPlaylistTracks = musicViewModel::selectAllPlaylistTracks,
                            onSelectCustomPlaylistRange = musicViewModel::selectPlaylistRangeOneBased,
                            onDownloadAllSelectedPlaylistTracks = musicViewModel::downloadAllResolvedPlaylistTracks,
                            onClearResolvedPlaylist = musicViewModel::clearResolvedPlaylist,
                            onPauseQueue = musicViewModel::pauseDownloadQueue,
                            onResumeQueue = musicViewModel::resumeDownloadQueue,
                            onRetryFailedTracks = musicViewModel::retryFailedQueueTracks,
                            onCancelActiveDownload = musicViewModel::cancelYoutubeDLDownload,
                            onMoveToTopOfQueue = musicViewModel::moveTrackToTopOfDownloadQueue,
                            onRemoveFromQueue = musicViewModel::removeTrackFromDownloadQueue,
                            onClearQueue = musicViewModel::clearDownloadQueue,
                            onBack = { musicViewModel.closeDownloadQueueCenter() }
                        )
                    } else if (showSubscriptionScreen) {
                        SubscriptionManagerScreen(
                            subscriptions = state.subscriptions,
                            isWifiOnly = state.isAutoSyncWifiOnly,
                            intervalHours = state.autoSyncIntervalHours,
                            intervalMinutes = state.autoSyncIntervalMinutes,
                            availablePlaylists = state.playlists,
                            onAddSubscription = { title, url, fmt, targetTitle, targetId ->
                                musicViewModel.addSubscription(title, url, fmt, targetTitle, targetId)
                            },
                            onUpdateSubscriptionTarget = musicViewModel::updateSubscriptionTarget,
                            onRemoveSubscription = musicViewModel::removeSubscription,
                            onToggleSubscription = musicViewModel::toggleSubscription,
                            onSetWifiOnly = musicViewModel::setAutoSyncWifiOnly,
                            onSetIntervalHours = musicViewModel::setAutoSyncIntervalHours,
                            onSetIntervalMinutes = musicViewModel::setAutoSyncIntervalMinutes,
                            onTriggerImmediateSync = musicViewModel::triggerImmediateSubscriptionSync,
                            onBack = { showSubscriptionScreen = false }
                        )
                    } else {
                        AnimatedContent(
                            targetState = state.selectedTab,
                            transitionSpec = {
                                val forward = targetState.ordinal >= initialState.ordinal
                                buildSakuraTabTransition(state.animationConfig, forward)
                            },
                            label = "sakura_tab_content"
                        ) { targetTab ->
                            tabSaveableStateHolder.SaveableStateProvider(targetTab.name) {
                            when (targetTab) {
                                YtMusicTab.HOME -> HomeScreen(
                                    youtubeUrlInput = state.youtubeUrlInput,
                                    isResolvingStream = state.isResolvingStream,
                                    selectedFormat = state.selectedDownloadFormat,
                                    youtubeDLState = state.youtubeDLState,
                                    youtubeDLVersion = state.youtubeDLVersion,
                                    isUpdatingYoutubeDL = state.isUpdatingYoutubeDL,
                                    isYtdlpEngineReady = state.isYtdlpEngineReady,
                                    isFFmpegReady = state.isFFmpegReady,
                                    isAria2cReady = state.isAria2cReady,
                                    currentUrlSupportStatus = state.currentUrlSupportStatus,
                                    useAria2c = state.useAria2c,
                                    useSponsorBlock = state.useSponsorBlock,
                                    embedMetadataAndThumb = state.embedMetadataAndThumb,
                                    writeAutoSubtitlesLrc = state.writeAutoSubtitlesLrc,
                                    resolvedPlaylistTitle = state.resolvedPlaylistTitle,
                                    resolvedPlaylistTracks = state.resolvedPlaylistTracks,
                                    isBatchDownloadingPlaylist = state.isBatchDownloadingPlaylist,
                                    batchDownloadCompletedCount = state.batchDownloadCompletedCount,
                                    batchDownloadTotalCount = state.batchDownloadTotalCount,
                                    downloadedTracks = state.downloadedTracks,
                                    playCountManager = musicViewModel.playCountManager,
                                    onUrlInputChange = musicViewModel::updateYoutubeUrlInput,
                                    onSelectFormat = musicViewModel::selectDownloadFormat,
                                    onSetUseAria2c = musicViewModel::setUseAria2c,
                                    onSetUseSponsorBlock = musicViewModel::setUseSponsorBlock,
                                    onSetEmbedMetadata = musicViewModel::setEmbedMetadataAndThumb,
                                    onSetWriteAutoSubs = musicViewModel::setWriteAutoSubtitlesLrc,
                                    onResolveAndPlay = { musicViewModel.resolveAndPlayYouTubeInput(autoDownload = false) },
                                    onStartYoutubeDLDownload = { musicViewModel.startYoutubeDLDownload() },
                                    onCancelYoutubeDLDownload = musicViewModel::cancelYoutubeDLDownload,
                                    onUpdateYoutubeDLBinary = { musicViewModel.updateYoutubeDLBinary(false) },
                                    onDownloadAllPlaylistTracks = musicViewModel::downloadAllResolvedPlaylistTracks,
                                    onClearResolvedPlaylist = musicViewModel::clearResolvedPlaylist,
                                    onDownloadSinglePlaylistTrack = { track ->
                                        musicViewModel.startYoutubeDLDownload(
                                            customUrl = track.sourceUrl.ifBlank { "https://www.youtube.com/watch?v=${track.id}" },
                                            existingTrack = track
                                        )
                                    },
                                    onPlaySavedTrack = musicViewModel::playSavedTrack,
                                    onPlayTopPlayedTrack = musicViewModel::playTopPlayedTrack,
                                    onOpenSubscriptionsClick = { showSubscriptionScreen = true },
                                    onOpenSettingsClick = { musicViewModel.selectTab(YtMusicTab.SETTINGS) },
                                    onOpenCookiesClick = { musicViewModel.selectTab(YtMusicTab.COOKIES) },
                                    selectedPlaylistTrackIds = state.selectedPlaylistTrackIds,
                                    onTogglePlaylistTrackSelection = musicViewModel::togglePlaylistTrackSelection,
                                    onSelectAllPlaylistTracks = musicViewModel::selectAllPlaylistTracks,
                                    onDownloadAccountLibraryWithCookies = musicViewModel::downloadAccountLibraryWithCookies,
                                    downloadQueue = state.downloadQueue,
                                    onRemoveFromDownloadQueue = musicViewModel::removeTrackFromDownloadQueue,
                                    onRemoveMultipleFromDownloadQueue = musicViewModel::removeTracksFromDownloadQueue,
                                    onMoveToTopOfDownloadQueue = musicViewModel::moveTrackToTopOfDownloadQueue,
                                    onClearDownloadQueue = musicViewModel::clearDownloadQueue,
                                    isPlaying = state.isPlaying && !state.isNowPlayingExpanded,
                                    currentPlayingTrackId = state.currentTrack?.id,
                                    treeStyle = state.selectedTreeStyle,
                                    treeForm = state.selectedTreeForm,
                                    featureToggles = state.featureToggles,
                                    onOpenQueueCenterClick = { musicViewModel.setDownloadQueueCenterOpen(true) },
                                    onOpenLibraryPlaylistsInQueueCenter = { musicViewModel.fetchAllUserLibraryPlaylists() },
                                    homeListState = homeListState
                                )

                                YtMusicTab.SEARCH -> SearchScreen(
                                    searchQuery = state.searchQuery,
                                    isSearchingCatalog = state.isSearchingCatalog,
                                    catalogResults = state.catalogSearchResults,
                                    downloadedTracks = state.downloadedTracks,
                                    onSearchQueryChange = musicViewModel::updateSearchQuery,
                                    onTriggerSearch = { musicViewModel.triggerCatalogSearch() },
                                    onPlayCatalogTrack = { musicViewModel.playCatalogTrack(it, autoDownload = false) },
                                    onDownloadCatalogTrack = { musicViewModel.playCatalogTrack(it, autoDownload = true) },
                                    onPlaySavedTrack = musicViewModel::playSavedTrack
                                )

                                YtMusicTab.LIBRARY -> LibraryScreen(
                                    downloadedTracks = state.downloadedTracks,
                                    playlists = state.playlists,
                                    currentPlayingTrackId = state.currentTrack?.id,
                                    featureToggles = state.featureToggles,
                                    playCountManager = musicViewModel.playCountManager,
                                    isBatchFetchingLyrics = state.isBatchFetchingLyrics,
                                    batchLyricsCompletedCount = state.batchLyricsCompletedCount,
                                    batchLyricsTotalCount = state.batchLyricsTotalCount,
                                    onBatchFetchAndTranslateAllLyrics = musicViewModel::batchFetchAndTranslateAllLyrics,
                                    onUpdateFeatureToggles = musicViewModel::updateFeatureToggles,
                                    onPlaySavedTrack = musicViewModel::playSavedTrack,
                                    onDeleteSavedTrack = musicViewModel::deleteDownloadedTrack,
                                    onAddTrackToPlaylist = musicViewModel::addTrackToPlaylist,
                                    onUpdateTrackMetadata = musicViewModel::updateSavedTrackMetadata,
                                    onAutoFetchYoutubeTitle = musicViewModel::autoFetchYoutubeTitleForTrack,
                                    onRunLibraryDoctor = musicViewModel::runLibraryDoctor,
                                    onRefreshLibrary = musicViewModel::refreshDownloadedTracks,
                                    onDownloadAccountLibraryWithCookies = musicViewModel::downloadAccountLibraryWithCookies
                                )

                                YtMusicTab.PLAYLISTS -> PlaylistsScreen(
                                    playlists = state.playlists,
                                    selectedPlaylistId = state.selectedPlaylistId,
                                    downloadedTracks = state.downloadedTracks,
                                    isGridViewSaved = state.isPlaylistsGridView,
                                    onToggleGridView = musicViewModel::setPlaylistsGridView,
                                    onCreatePlaylist = musicViewModel::createPlaylist,
                                    onDeletePlaylist = musicViewModel::deletePlaylist,
                                    onOpenPlaylistDetail = musicViewModel::openPlaylistDetail,
                                    onClosePlaylistDetail = musicViewModel::closePlaylistDetail,
                                    onUpdatePlaylistCover = musicViewModel::updatePlaylistCover,
                                    onAddTrackToPlaylist = musicViewModel::addTrackToPlaylist,
                                    onRemoveTrackFromPlaylist = musicViewModel::removeTrackFromPlaylist,
                                    onPlayPlaylistQueue = musicViewModel::playPlaylistQueue
                                )

                                YtMusicTab.VAULT -> VaultScreen(
                                    vaultItems = state.vaultItems,
                                    onPlayVaultItem = musicViewModel::openVaultItemInExoPlayer,
                                    onDeleteVaultItem = musicViewModel::deleteVaultItem,
                                    onRefreshVault = musicViewModel::refreshDownloadedTracks
                                )

                                YtMusicTab.COOKIES -> CookieInspectorScreen(
                                    cookieViewModel = cookieViewModel
                                )

                                YtMusicTab.SETTINGS -> SettingsScreen(
                                    selectedTheme = state.selectedTheme,
                                    selectedTreeStyle = state.selectedTreeStyle,
                                    selectedTreeForm = state.selectedTreeForm,
                                    animationConfig = state.animationConfig,
                                    youtubeDLVersion = state.youtubeDLVersion,
                                    isYtdlpEngineReady = state.isYtdlpEngineReady,
                                    isFFmpegReady = state.isFFmpegReady,
                                    isAria2cReady = state.isAria2cReady,
                                    isUpdatingYoutubeDL = state.isUpdatingYoutubeDL,
                                    autoTranslateLyricsToTurkish = state.autoTranslateLyricsToTurkish,
                                    translationEngine = state.translationEngine,
                                    selectedAppIcon = state.selectedAppIcon,
                                    volumeBoostPercent = state.volumeBoostPercent,
                                    isVolumeBoosterEnabled = state.isVolumeBoosterEnabled,
                                    featureToggles = state.featureToggles,
                                    studioDspConfig = state.studioDspConfig,
                                    isBatchFetchingLyrics = state.isBatchFetchingLyrics,
                                    batchLyricsCompletedCount = state.batchLyricsCompletedCount,
                                    batchLyricsTotalCount = state.batchLyricsTotalCount,
                                    isLikedSongsWifiAutoSyncEnabled = state.isLikedSongsWifiAutoSyncEnabled,
                                    onBatchFetchAndTranslateAllLyrics = musicViewModel::batchFetchAndTranslateAllLyrics,
                                    onSetLikedSongsWifiAutoSyncEnabled = musicViewModel::setLikedSongsWifiAutoSyncEnabled,
                                    onUpdateFeatureToggles = musicViewModel::updateFeatureToggles,
                                    onUpdateStudioDspConfig = musicViewModel::updateStudioDspConfig,
                                    onSelectTheme = musicViewModel::selectTheme,
                                    onSelectTreeStyle = musicViewModel::selectTreeStyle,
                                    onSelectTreeForm = musicViewModel::selectTreeForm,
                                    onSelectAppIcon = musicViewModel::selectAppIcon,
                                    selectedDownloadFormat = state.selectedDownloadFormat,
                                    onSelectDownloadFormat = musicViewModel::selectDownloadFormat,
                                    onDownloadAccountLibraryWithCookies = musicViewModel::downloadAccountLibraryWithCookies,
                                    onSelectAnimationProfile = musicViewModel::selectAnimationProfile,
                                    onSetAnimationsMasterEnabled = musicViewModel::setAnimationsMasterEnabled,
                                    onSelectPetalDensity = musicViewModel::selectPetalDensity,
                                    onSelectTransitionSpeed = musicViewModel::selectTransitionSpeed,
                                    onSelectEffectIntensity = musicViewModel::selectEffectIntensity,
                                    onSetFloatingPetalsEnabled = musicViewModel::setFloatingPetalsEnabled,
                                    onSetAudioReactiveWavesEnabled = musicViewModel::setAudioReactiveWavesEnabled,
                                    onSetInteractiveBurstEnabled = musicViewModel::setInteractiveBurstEnabled,
                                    onSetScreenTransitionsEnabled = musicViewModel::setScreenTransitionsEnabled,
                                    onSetPowerSaveAutoReduce = musicViewModel::setPowerSaveAutoReduce,
                                    onSetAutoTranslateLyricsToTurkish = musicViewModel::setAutoTranslateLyricsToTurkish,
                                    onSelectTranslationEngine = musicViewModel::setTranslationEngine,
                                    lyricsSourceOrder = state.lyricsSourceOrder,
                                    enabledLyricsSources = state.enabledLyricsSources,
                                    onMoveLyricsSourceUp = musicViewModel::moveLyricsSourceUp,
                                    onMoveLyricsSourceDown = musicViewModel::moveLyricsSourceDown,
                                    onToggleLyricsSourceEnabled = musicViewModel::toggleLyricsSourceEnabled,
                                    onResetLyricsSourceOrder = musicViewModel::resetLyricsSourceOrder,
                                    onSetVolumeBoost = musicViewModel::setVolumeBoost,
                                    onUpdateYoutubeDL = musicViewModel::updateYoutubeDLBinary,
                                    playbackQueue = offlinePlayerState.queue,
                                    currentQueueIndex = offlinePlayerState.currentIndex,
                                    onRemoveFromQueue = musicViewModel::removeTrackFromPlaybackQueue,
                                    onClearQueue = musicViewModel::clearPlaybackQueue,
                                    onPlayQueueIndex = musicViewModel::playTrackFromPlaybackQueue
                                )
                            }
                            }
                        }
                    }
                }
            }

            if (backgroundDepthProgress > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = backgroundDepthProgress * 0.45f
                        }
                        .background(Color.Black)
                )
            }

            AnimatedVisibility(
                visible = state.isNowPlayingExpanded,
                enter = buildFullPlayerEnterTransition(state.animationConfig),
                exit = buildFullPlayerExitTransition(state.animationConfig),
                label = "full_player_slide_transition"
            ) {
                ExpandedNowPlayingHost(musicViewModel = musicViewModel)
            }
        }
    }
}

@Composable
private fun MiniPlayerProgressHost(
    musicViewModel: MusicPlayerViewModel,
    track: com.example.downloader.MediaTrackInfo,
    isPlaying: Boolean,
    isLoading: Boolean,
    onExpandClick: () -> Unit
) {
    val state by musicViewModel.uiState.collectAsState()
    val progressFraction = if (state.durationMs > 0L) {
        (state.currentPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    MiniPlayerBar(
        track = track,
        isPlaying = isPlaying,
        progressFraction = progressFraction,
        isLoading = isLoading,
        onTogglePlayPause = musicViewModel::togglePlayPause,
        onSkipNext = musicViewModel::skipToNext,
        onSkipPrevious = musicViewModel::skipToPrevious,
        onExpandClick = onExpandClick
    )
}

@Composable
private fun ExpandedNowPlayingHost(
    musicViewModel: MusicPlayerViewModel
) {
    val state by musicViewModel.uiState.collectAsState()
    val offlinePlayerState by musicViewModel.offlinePlayerState.collectAsState()
    val fftMagnitudes by musicViewModel.fftMagnitudes.collectAsState()

    NowPlayingScreen(
        track = state.currentTrack,
        isPlaying = state.isPlaying,
        currentPositionMs = state.currentPositionMs,
        durationMs = state.durationMs,
        offlinePlayerState = offlinePlayerState,
        fftMagnitudes = fftMagnitudes,
        lyrics = state.parsedLyrics,
        activeLyricIndex = state.activeLyricIndex,
        rawLrcText = state.lrcRawText,
        isFetchingLyrics = state.isFetchingLyrics,
        downloadProgress = state.downloadProgress,
        playlists = state.playlists,
        lastSavedMedia = state.lastSavedMedia,
        onTogglePlayPause = musicViewModel::togglePlayPause,
        onSeekTo = musicViewModel::seekTo,
        onSkipNext = musicViewModel::skipToNext,
        onSkipPrevious = musicViewModel::skipToPrevious,
        onToggleShuffle = musicViewModel::toggleShuffle,
        onCycleRepeat = musicViewModel::cycleRepeatMode,
        volumeBoostPercent = state.volumeBoostPercent,
        isVolumeBoosterEnabled = state.isVolumeBoosterEnabled,
        featureToggles = state.featureToggles,
        studioDspConfig = state.studioDspConfig,
        baseTheme = state.selectedTheme,
        abLoopPointAMs = state.abLoopPointAMs,
        abLoopPointBMs = state.abLoopPointBMs,
        onSetAbLoopA = musicViewModel::setAbLoopPointA,
        onSetAbLoopB = musicViewModel::setAbLoopPointB,
        onClearAbLoop = musicViewModel::clearAbLoop,
        onUpdateFeatureToggles = musicViewModel::updateFeatureToggles,
        onUpdateStudioDspConfig = musicViewModel::updateStudioDspConfig,
        onSetPlaybackSpeed = musicViewModel::setPlaybackSpeed,
        onSetSleepTimer = musicViewModel::setSleepTimer,
        onSetVolumeBoost = musicViewModel::setVolumeBoost,
        onStartDownload = musicViewModel::startCurrentTrackDownload,
        onCancelDownload = musicViewModel::cancelActiveDownload,
        onUpdateRawLrc = musicViewModel::updateLrcText,
        activeLyricsSourceLabel = state.activeLyricsSourceLabel,
        onRefetchLyrics = musicViewModel::refetchCurrentTrackLyrics,
        onAddTrackToPlaylist = musicViewModel::addTrackToPlaylist,
        onSelectQueueIndex = { idx ->
            val q = offlinePlayerState.queue
            if (idx in q.indices) {
                musicViewModel.playPlaylistQueue(q, idx)
            }
        },
        onCollapse = { musicViewModel.setNowPlayingExpanded(false) }
    )
}


