package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PlayCountManager
import com.example.data.TopPlayedItem
import com.example.downloader.DownloadFormatOption
import com.example.downloader.LikedSongsSourceType
import com.example.downloader.MediaTrackInfo
import com.example.downloader.PlatformSupportStatus
import com.example.downloader.SavedMediaResult
import com.example.downloader.YoutubeDLDownloadState
import com.example.ui.animation.sakuraPressScale
import com.example.ui.components.OptimizedThumbnailImage
import com.example.ui.components.SakuraFlowerEmblem
import com.example.ui.components.SakuraTreeCanvas
import com.example.ui.components.rememberProgressiveWindowState
import com.example.ui.theme.LocalAppThemePalette
import com.example.ui.theme.AnimeArtRegistry
import com.example.ui.theme.isSakuraTheme
import com.example.ui.theme.getCustomFontFamily
import com.example.data.FeatureTogglesStorageHelper
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage

import com.example.data.SakuraTreeForm
import com.example.data.SakuraTreeStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    youtubeUrlInput: String,
    isResolvingStream: Boolean,
    selectedFormat: DownloadFormatOption,
    youtubeDLState: YoutubeDLDownloadState,
    youtubeDLVersion: String,
    isUpdatingYoutubeDL: Boolean,
    isYtdlpEngineReady: Boolean,
    isFFmpegReady: Boolean,
    isAria2cReady: Boolean,
    currentUrlSupportStatus: PlatformSupportStatus?,
    useAria2c: Boolean,
    useSponsorBlock: Boolean,
    embedMetadataAndThumb: Boolean,
    writeAutoSubtitlesLrc: Boolean,
    resolvedPlaylistTitle: String?,
    resolvedPlaylistTracks: List<MediaTrackInfo>,
    isBatchDownloadingPlaylist: Boolean,
    batchDownloadCompletedCount: Int,
    batchDownloadTotalCount: Int,
    downloadedTracks: List<SavedMediaResult>,
    playCountManager: PlayCountManager,
    onUrlInputChange: (String) -> Unit,
    onSelectFormat: (DownloadFormatOption) -> Unit,
    onSetUseAria2c: (Boolean) -> Unit,
    onSetUseSponsorBlock: (Boolean) -> Unit,
    onSetEmbedMetadata: (Boolean) -> Unit,
    onSetWriteAutoSubs: (Boolean) -> Unit,
    onResolveAndPlay: () -> Unit,
    onStartYoutubeDLDownload: () -> Unit,
    onCancelYoutubeDLDownload: () -> Unit,
    onUpdateYoutubeDLBinary: () -> Unit,
    onDownloadAllPlaylistTracks: () -> Unit,
    onClearResolvedPlaylist: () -> Unit,
    onDownloadSinglePlaylistTrack: (MediaTrackInfo) -> Unit,
    onPlaySavedTrack: (SavedMediaResult) -> Unit,
    onPlayTopPlayedTrack: (TopPlayedItem) -> Unit,
    onOpenSubscriptionsClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onOpenCookiesClick: () -> Unit = {},
    selectedPlaylistTrackIds: Set<String> = emptySet(),
    onTogglePlaylistTrackSelection: (String) -> Unit = {},
    onSelectAllPlaylistTracks: (Boolean) -> Unit = {},
    onDownloadAccountLibraryWithCookies: (LikedSongsSourceType) -> Unit = {},
    downloadQueue: List<MediaTrackInfo> = emptyList(),
    onRemoveFromDownloadQueue: (String) -> Unit = {},
    onRemoveMultipleFromDownloadQueue: (Collection<String>) -> Unit = {},
    onMoveToTopOfDownloadQueue: (String) -> Unit = {},
    onClearDownloadQueue: () -> Unit = {},
    isPlaying: Boolean = false,
    currentPlayingTrackId: String? = null,
    treeStyle: SakuraTreeStyle = SakuraTreeStyle.TRADITIONAL_SAKURA,
    treeForm: SakuraTreeForm = SakuraTreeForm.CLASSIC_SWEEPING,
    featureToggles: com.example.data.FeatureTogglesConfig = com.example.data.FeatureTogglesConfig(),
    onOpenQueueCenterClick: () -> Unit = {},
    onOpenLibraryPlaylistsInQueueCenter: () -> Unit = {},
    homeListState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    val bebopConfig = com.example.ui.theme.LocalCowboyBebopConfig.current
    val isGriffithTheme = palette.theme == com.example.ui.theme.AppTheme.BERSERK_GRIFFITH
    val isBebopTheme = palette.theme == com.example.ui.theme.AppTheme.COWBOY_BEBOP_SPIKE
    var showDownloadSheet by remember { mutableStateOf(false) }
    val playCounts by playCountManager.playCountsFlow.collectAsState()
    val listeningMs by playCountManager.listeningMsFlow.collectAsState()

    // Ana sayfada maksimum 5 şarkı gösterilir (Maksimum akıcılık, bellek ve GPU optimizasyonu)
    val topPlayedTracks = remember(downloadedTracks, playCounts, listeningMs) {
        playCountManager.getTopPlayedTracks(downloadedTracks, maxLimit = 5)
    }

    val windowState = rememberProgressiveWindowState(
        listState = homeListState,
        totalItemsCount = topPlayedTracks.size,
        headerItemsOffset = 1,
        initialBatchSize = 5,
        stepBatchSize = 5,
        activeWindowBuffer = 1
    )

    val displayedHomeTracks = remember(topPlayedTracks, windowState.loadedCount) {
        topPlayedTracks.take(windowState.loadedCount.coerceAtMost(5))
    }

    // Rank rozet fırçaları her satırda yeniden oluşturulmasın diye tek sefer önbelleğe alınır
    val goldRankBrush = remember { Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500))) }
    val silverRankBrush = remember { Brush.linearGradient(listOf(Color(0xFFE0E0E0), Color(0xFFB0B0B0))) }
    val bronzeRankBrush = remember { Brush.linearGradient(listOf(Color(0xFFCD7F32), Color(0xFF8B4513))) }
    val defaultRankBrush = remember(palette.surfaceVariant) {
        Brush.linearGradient(
            listOf(
                palette.surfaceVariant.copy(alpha = 0.7f),
                palette.surfaceVariant.copy(alpha = 0.4f)
            )
        )
    }

    val isDownloadingActive = youtubeDLState is YoutubeDLDownloadState.Downloading ||
        youtubeDLState is YoutubeDLDownloadState.Initializing ||
        youtubeDLState is YoutubeDLDownloadState.FetchingInfo ||
        youtubeDLState is YoutubeDLDownloadState.SavingMetadata ||
        isBatchDownloadingPlaylist

    val sadeConfig = com.example.data.LocalCustomMinimalThemeConfig.current
    val animeMeta = remember(palette.theme) { AnimeArtRegistry.getMetadata(palette.theme) }

    val customFontFamily = remember(palette.theme, featureToggles.animeThemeFontsEnabled) {
        palette.theme.getCustomFontFamily(featureToggles.animeThemeFontsEnabled)
    }

    val isListScrolling = homeListState.isScrollInProgress

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Background Layer:
        // Sakura ağacı animasyonu yalnızca Sakura temasında geçerlidir ve kaydırma sırasında duraklatılır.
        if (palette.theme.isSakuraTheme()) {
            SakuraTreeCanvas(
                modifier = Modifier.fillMaxSize(),
                isPlaying = isPlaying,
                isScrolling = isListScrolling,
                treeStyle = treeStyle,
                treeForm = treeForm
            )
        }

        // 2. Foreground Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Top App Bar / Header: Logo + Title on left; Çerezler (Cookies), Ayarlar (Settings), Kuyruk Merkezi, İndirme on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    when {
                        palette.theme.isSakuraTheme() -> {
                            SakuraFlowerEmblem(size = 32.dp)
                        }
                        isGriffithTheme && featureToggles.griffithFalconEmblemEnabled -> {
                            com.example.ui.components.FalconOfLightEmblemCanvas(
                                size = 34.dp,
                                primaryColor = palette.primaryAccent,
                                secondaryColor = palette.secondaryAccent,
                                modifier = Modifier.testTag("griffith_falcon_emblem_home")
                            )
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(palette.primaryAccent.copy(alpha = 0.20f))
                                    .border(1.dp, palette.primaryAccent.copy(alpha = 0.60f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (palette.theme) {
                                        com.example.ui.theme.AppTheme.BERSERK_GUTS -> "⚔"
                                        com.example.ui.theme.AppTheme.BERSERK_GRIFFITH -> "✦"
                                        com.example.ui.theme.AppTheme.COWBOY_BEBOP_SPIKE -> "✦"
                                        else -> "★"
                                    },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primaryAccent
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (palette.theme.isSakuraTheme()) "Sakura Music" else (animeMeta?.seriesTitle?.substringBefore(" (") ?: "Anime Sound"),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFontFamily,
                            color = palette.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (palette.theme.isSakuraTheme()) "En Çok Dinlenen 5 Şarkı" else (animeMeta?.characterName ?: "Müzik Çalar"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.primaryAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isGriffithTheme && featureToggles.griffithQuotesEnabled) {
                            val headerQuote = remember(currentPlayingTrackId) {
                                com.example.ui.components.GriffithThemeQuotes.quoteForKey(currentPlayingTrackId)
                            }
                            Text(
                                text = "“$headerQuote”",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.secondaryAccent.copy(alpha = 0.92f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("griffith_header_quote")
                            )
                        }
                    }
                }

                // Header Action Buttons: Çerezler, Kuyruk & Liste Merkezi, Ayarlar, İndirme
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Çerez Yöneticisi (Cookies) Butonu
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(palette.surfaceContainer.copy(alpha = 0.65f))
                            .border(1.dp, palette.dividerOrBorder.copy(alpha = 0.5f), CircleShape)
                            .clickable(onClick = onOpenCookiesClick)
                            .testTag("header_cookies_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cookie,
                            contentDescription = "Çerez Yöneticisi",
                            tint = palette.primaryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Bağımsız Tam Ekran İndirme & Kuyruk Merkezi Butonu
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (downloadQueue.isNotEmpty() || resolvedPlaylistTracks.isNotEmpty()) {
                                    palette.primaryAccent.copy(alpha = 0.24f)
                                } else {
                                    palette.surfaceContainer.copy(alpha = 0.65f)
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (downloadQueue.isNotEmpty() || resolvedPlaylistTracks.isNotEmpty()) {
                                    palette.primaryAccent
                                } else {
                                    palette.dividerOrBorder.copy(alpha = 0.5f)
                                },
                                shape = CircleShape
                            )
                            .clickable(onClick = onOpenQueueCenterClick)
                            .testTag("header_queue_center_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = "İndirme ve Kuyruk Merkezi",
                            tint = if (downloadQueue.isNotEmpty() || resolvedPlaylistTracks.isNotEmpty()) {
                                palette.primaryAccent
                            } else {
                                palette.onSurface
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Ayarlar (Settings) Butonu
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(palette.surfaceContainer.copy(alpha = 0.65f))
                            .border(1.dp, palette.dividerOrBorder.copy(alpha = 0.5f), CircleShape)
                            .clickable(onClick = onOpenSettingsClick)
                            .testTag("header_settings_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ayarlar",
                            tint = palette.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // İndirme İşlemleri Butonu
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDownloadingActive) {
                                    palette.primaryAccent.copy(alpha = 0.28f)
                                } else {
                                    palette.surfaceContainer.copy(alpha = 0.65f)
                                }
                            )
                            .border(
                                width = if (isDownloadingActive) 1.5.dp else 1.dp,
                                color = if (isDownloadingActive) palette.primaryAccent else palette.dividerOrBorder.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .clickable { showDownloadSheet = true }
                            .testTag("header_download_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDownloadingActive) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = palette.primaryAccent
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "İndirme İşlemleri",
                            tint = if (isDownloadingActive) palette.primaryAccent else palette.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Griffith Rastgele Replik Satırı (Şarkı geçişlerinde tetiklenir, sürekli recomposition üretmez)
            if (isGriffithTheme && featureToggles.griffithRandomLinesEnabled && currentPlayingTrackId != null) {
                val trackTransitionLine = remember(currentPlayingTrackId) {
                    com.example.ui.components.GriffithThemeQuotes.randomLineForContext(currentPlayingTrackId)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surfaceContainer.copy(alpha = 0.65f))
                        .border(1.dp, palette.primaryAccent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("griffith_random_line_banner"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✦ $trackTransitionLine",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.onSurface.copy(alpha = 0.90f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Hafif İndirme ve Kuyruk Merkezi Durum Çubuğu (Ağır iç içe listeler ana ekrandan kaldırıldı)
            AnimatedVisibility(
                visible = isDownloadingActive || downloadQueue.isNotEmpty() || resolvedPlaylistTracks.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(tween(200)),
                exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(tween(160)),
                label = "lightweight_queue_center_bar"
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.primaryAccent.copy(alpha = 0.18f))
                        .border(1.dp, palette.primaryAccent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenQueueCenterClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("home_lightweight_queue_bar"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isDownloadingActive) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = palette.primaryAccent
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = palette.primaryAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                youtubeDLState is YoutubeDLDownloadState.Downloading ->
                                    "İndiriliyor: %${youtubeDLState.progressPercent.toInt()} • Sırada: ${downloadQueue.size}"
                                resolvedPlaylistTracks.isNotEmpty() ->
                                    "${resolvedPlaylistTitle ?: "Liste"} (${resolvedPlaylistTracks.size} şarkı hazır)"
                                downloadQueue.isNotEmpty() ->
                                    "İndirme Kuyruğu: ${downloadQueue.size} şarkı sırada ($batchDownloadCompletedCount/$batchDownloadTotalCount)"
                                else -> "İndirme ve Kuyruk Merkezi Aktif"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Merkezi Aç →",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryAccent
                    )
                }
            }

            // En Çok Dinlenen Şarkılar Listesi (Maks 5 Şarkı)
            LazyColumn(
                state = homeListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "home_top_header", contentType = "header") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        val showHeaderWallpaper = !sadeConfig.headerBannerWallpaperPath.isNullOrBlank() &&
                            palette.theme != com.example.ui.theme.AppTheme.BERSERK_GRIFFITH
                        if (showHeaderWallpaper) {
                            AsyncImage(
                                model = java.io.File(sadeConfig.headerBannerWallpaperPath),
                                contentDescription = "Liste Başlığı GIF/Görsel",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .matchParentSize()
                                    .graphicsLayer {
                                        alpha = sadeConfig.headerBannerWallpaperOpacity.coerceIn(0.05f, 1f)
                                    }
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (showHeaderWallpaper) 10.dp else 0.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Headphones,
                                    contentDescription = null,
                                    tint = palette.primaryAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isBebopTheme && bebopConfig.cbSpecialListTitleStyleEnabled) {
                                        "SESSION // TREND & EN ÇOK DİNLENENLER (MAKS 5)"
                                    } else {
                                        "Trend & En Çok Dinlenenler (Maks 5)"
                                    },
                                    fontSize = if (isBebopTheme && bebopConfig.cbSpecialListTitleStyleEnabled) 13.sp else 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = if (isBebopTheme && bebopConfig.cbSpecialListTitleStyleEnabled) 0.8.sp else 0.sp,
                                    color = if (isBebopTheme && bebopConfig.cbSpecialListTitleStyleEnabled) palette.primaryAccent else palette.onBackground
                                )
                            }
                            Text(
                                text = "${displayedHomeTracks.size}/5 aktif",
                                fontSize = 12.sp,
                                color = palette.onSurfaceMuted
                            )
                        }
                    }
                }

                if (topPlayedTracks.isEmpty()) {
                    item(key = "home_empty_state", contentType = "empty") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(palette.surfaceContainer.copy(alpha = 0.55f))
                                .border(1.dp, palette.dividerOrBorder.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = palette.primaryAccent,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Henüz İndirilen Şarkı Yok",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Sağ üstteki İndirme veya Kuyruk Merkezi butonuna tıklayarak şarkılarınızı ve tüm oynatma listelerinizi indirebilirsiniz.",
                                fontSize = 12.sp,
                                color = palette.onSurfaceMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (isGriffithTheme && featureToggles.griffithRandomLinesEnabled) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "“${com.example.ui.components.GriffithThemeQuotes.randomLineForContext("empty_home")}”",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.primaryAccent,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.testTag("griffith_empty_state_line")
                                )
                            }
                        }
                    }
                }

                itemsIndexed(
                    items = displayedHomeTracks,
                    key = { _, track -> track.id },
                    contentType = { _, _ -> "top_played_track_row" }
                ) { index, trackItem ->
                    val isCurrentTrack = currentPlayingTrackId == trackItem.id
                    val isInActiveWindow = windowState.isTrackInActiveMemoryWindow(index)

                    val pulseScale = if (isCurrentTrack && isPlaying) {
                        val infinitePulse = rememberInfiniteTransition(label = "equalizer_pulse")
                        infinitePulse.animateFloat(
                            initialValue = 0.93f,
                            targetValue = 1.07f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "equalizer_pulse_scale"
                        ).value
                    } else {
                        1f
                    }

                    // Saydam (Translucent) Glassmorphic Card - Cowboy Bebop ince kart çerçevesi, parıltı ve köşe işaretlerini destekler
                    val showBebopThinBorder = !isBebopTheme || bebopConfig.cbThinCardBorderEnabled
                    val showBebopGlow = isBebopTheme && bebopConfig.cbSubtleCardGlowEnabled && !bebopConfig.cbLowEffectModeEnabled
                    val showBebopCornerMarks = isBebopTheme && bebopConfig.cbCornerAccentMarksEnabled
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                when {
                                    isCurrentTrack -> palette.primaryAccent.copy(alpha = 0.22f)
                                    showBebopGlow -> palette.surfaceContainer.copy(alpha = 0.78f)
                                    else -> palette.surfaceContainer.copy(alpha = 0.52f)
                                }
                            )
                            .then(
                                if (showBebopThinBorder) {
                                    Modifier.border(
                                        width = if (isBebopTheme && showBebopGlow) 1.3.dp else 1.dp,
                                        color = when {
                                            isCurrentTrack -> palette.primaryAccent.copy(alpha = 0.75f)
                                            showBebopGlow -> palette.primaryAccent.copy(alpha = 0.52f)
                                            else -> palette.dividerOrBorder.copy(alpha = 0.38f)
                                        },
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                } else Modifier
                            )
                            .sakuraPressScale()
                            .clickable { onPlayTopPlayedTrack(trackItem) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rank Badge (#1, #2, #3...)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when (index) {
                                        0 -> goldRankBrush
                                        1 -> silverRankBrush
                                        2 -> bronzeRankBrush
                                        else -> defaultRankBrush
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (showBebopCornerMarks) "⌜${index + 1}⌟" else "${index + 1}",
                                fontSize = if (showBebopCornerMarks) 10.sp else 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (index < 3) Color.Black else palette.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Cover Thumbnail (Aktif pencere dışına kaydırıldığında bellekten silinir/unutulur)
                        OptimizedThumbnailImage(
                            model = trackItem.thumbnailUrl,
                            contentDescription = trackItem.title,
                            isWithinActiveWindow = isInActiveWindow,
                            fallbackIcon = Icons.Default.MusicNote,
                            fallbackTint = palette.primaryAccent,
                            backgroundColor = palette.surfaceVariant.copy(alpha = 0.7f),
                            iconSize = 24.dp,
                            targetPixelSize = 120,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Title & Artist & Play Count
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = trackItem.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrentTrack) palette.primaryAccent else palette.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = trackItem.artist,
                                    fontSize = 11.sp,
                                    color = palette.onSurfaceMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text(
                                    text = "•",
                                    fontSize = 10.sp,
                                    color = palette.onSurfaceMuted
                                )
                                Text(
                                    text = if (trackItem.listeningSeconds > 0) {
                                        "${trackItem.playCount} dinleme • ${PlayCountManager.formatDuration(trackItem.listeningSeconds)}"
                                    } else {
                                        "${trackItem.playCount} dinleme"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.primaryAccent.copy(alpha = 0.90f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Play / Equalizer Icon
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .graphicsLayer {
                                    if (isCurrentTrack && isPlaying) {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                    }
                                }
                                .clip(CircleShape)
                                .background(
                                    if (isCurrentTrack && isPlaying) {
                                        palette.primaryAccent
                                    } else {
                                        palette.primaryAccent.copy(alpha = 0.16f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isCurrentTrack && isPlaying) Icons.Default.Equalizer else Icons.Default.PlayArrow,
                                contentDescription = "Oynat",
                                tint = if (isCurrentTrack && isPlaying) palette.onPrimary else palette.primaryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2B. Berserk - Griffith Uçan Işığın Şahini Animasyonu (Sabit amblemden bağımsız, dokunmayı engellemez)
        if (isGriffithTheme && featureToggles.griffithFlyingFalconEnabled) {
            com.example.ui.components.FlyingFalconOfLightOverlay(
                enabled = true,
                isScrolling = isListScrolling,
                primaryColor = palette.primaryAccent,
                secondaryColor = palette.secondaryAccent,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("griffith_flying_falcon_overlay")
            )
        }

        // 3. Download Operations Modal Bottom Sheet (Triggered by the download button to the right of Settings)
        if (showDownloadSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showDownloadSheet = false },
                sheetState = sheetState,
                containerColor = palette.surfaceContainer.copy(alpha = 0.96f),
                tonalElevation = 6.dp
            ) {
                DownloadOperationsContent(
                    youtubeUrlInput = youtubeUrlInput,
                    isResolvingStream = isResolvingStream,
                    selectedFormat = selectedFormat,
                    youtubeDLState = youtubeDLState,
                    youtubeDLVersion = youtubeDLVersion,
                    isUpdatingYoutubeDL = isUpdatingYoutubeDL,
                    isYtdlpEngineReady = isYtdlpEngineReady,
                    isFFmpegReady = isFFmpegReady,
                    isAria2cReady = isAria2cReady,
                    currentUrlSupportStatus = currentUrlSupportStatus,
                    useAria2c = useAria2c,
                    useSponsorBlock = useSponsorBlock,
                    embedMetadataAndThumb = embedMetadataAndThumb,
                    writeAutoSubtitlesLrc = writeAutoSubtitlesLrc,
                    onUrlInputChange = onUrlInputChange,
                    onSelectFormat = onSelectFormat,
                    onSetUseAria2c = onSetUseAria2c,
                    onSetUseSponsorBlock = onSetUseSponsorBlock,
                    onSetEmbedMetadata = onSetEmbedMetadata,
                    onSetWriteAutoSubs = onSetWriteAutoSubs,
                    onResolveAndPlay = onResolveAndPlay,
                    onStartYoutubeDLDownload = onStartYoutubeDLDownload,
                    onCancelYoutubeDLDownload = onCancelYoutubeDLDownload,
                    onUpdateYoutubeDLBinary = onUpdateYoutubeDLBinary,
                    onPlaySavedTrack = onPlaySavedTrack,
                    onOpenSubscriptionsClick = onOpenSubscriptionsClick,
                    onDownloadAccountLibraryWithCookies = onDownloadAccountLibraryWithCookies,
                    onOpenQueueCenterClick = {
                        showDownloadSheet = false
                        onOpenQueueCenterClick()
                    },
                    onOpenLibraryPlaylistsInQueueCenter = {
                        showDownloadSheet = false
                        onOpenLibraryPlaylistsInQueueCenter()
                    },
                    downloadQueue = downloadQueue,
                    completedCount = batchDownloadCompletedCount,
                    totalCount = batchDownloadTotalCount,
                    onRemoveFromDownloadQueue = onRemoveFromDownloadQueue,
                    onRemoveMultipleFromDownloadQueue = onRemoveMultipleFromDownloadQueue,
                    onMoveToTopOfDownloadQueue = onMoveToTopOfDownloadQueue,
                    onClearDownloadQueue = onClearDownloadQueue,
                    onCloseSheet = { showDownloadSheet = false }
                )
            }
        }
    }
}

@Composable
private fun DownloadOperationsContent(
    youtubeUrlInput: String,
    isResolvingStream: Boolean,
    selectedFormat: DownloadFormatOption,
    youtubeDLState: YoutubeDLDownloadState,
    youtubeDLVersion: String,
    isUpdatingYoutubeDL: Boolean,
    isYtdlpEngineReady: Boolean,
    isFFmpegReady: Boolean,
    isAria2cReady: Boolean,
    currentUrlSupportStatus: PlatformSupportStatus?,
    useAria2c: Boolean,
    useSponsorBlock: Boolean,
    embedMetadataAndThumb: Boolean,
    writeAutoSubtitlesLrc: Boolean,
    onUrlInputChange: (String) -> Unit,
    onSelectFormat: (DownloadFormatOption) -> Unit,
    onSetUseAria2c: (Boolean) -> Unit,
    onSetUseSponsorBlock: (Boolean) -> Unit,
    onSetEmbedMetadata: (Boolean) -> Unit,
    onSetWriteAutoSubs: (Boolean) -> Unit,
    onResolveAndPlay: () -> Unit,
    onStartYoutubeDLDownload: () -> Unit,
    onCancelYoutubeDLDownload: () -> Unit,
    onUpdateYoutubeDLBinary: () -> Unit,
    onPlaySavedTrack: (SavedMediaResult) -> Unit,
    onOpenSubscriptionsClick: () -> Unit,
    onDownloadAccountLibraryWithCookies: (LikedSongsSourceType) -> Unit = {},
    onOpenQueueCenterClick: () -> Unit = {},
    onOpenLibraryPlaylistsInQueueCenter: () -> Unit = {},
    downloadQueue: List<MediaTrackInfo> = emptyList(),
    completedCount: Int = 0,
    totalCount: Int = 0,
    onRemoveFromDownloadQueue: (String) -> Unit = {},
    onRemoveMultipleFromDownloadQueue: (Collection<String>) -> Unit = {},
    onMoveToTopOfDownloadQueue: (String) -> Unit = {},
    onClearDownloadQueue: () -> Unit = {},
    onCloseSheet: () -> Unit
) {
    val palette = LocalAppThemePalette.current
    var showAdvancedOptions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sheet Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SakuraFlowerEmblem(size = 26.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "İndirme İşlemleri & ytdlnis",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                    Text(
                        text = "yt-dlp ($youtubeDLVersion) • FFmpeg ${if (isFFmpegReady) "Aktif" else "Devre Dışı"}",
                        fontSize = 11.sp,
                        color = palette.onSurfaceMuted
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onUpdateYoutubeDLBinary,
                    enabled = !isUpdatingYoutubeDL
                ) {
                    if (isUpdatingYoutubeDL) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "yt-dlp Güncelle",
                            tint = palette.primaryAccent
                        )
                    }
                }
                IconButton(onClick = onCloseSheet) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat")
                }
            }
        }

        // Bağımsız Tam Ekran İndirme & Kuyruk Merkezi + Tek Tuşla Kütüphane Listeleri Butonları
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOpenLibraryPlaylistsInQueueCenter,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primaryAccent,
                    contentColor = palette.onPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("sheet_fetch_all_library_playlists_button")
            ) {
                Icon(Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tüm Kütüphane Listeleri", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onOpenQueueCenterClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("sheet_open_queue_center_button")
            ) {
                Text(
                    text = if (downloadQueue.isNotEmpty()) "Kuyruk Merkezi (${downloadQueue.size})" else "Aralıklı İndirme & Kuyruk",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Platform badges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EngineCapabilityBadge("YouTube / YT Music", supported = true)
            EngineCapabilityBadge("SoundCloud", supported = isYtdlpEngineReady)
            EngineCapabilityBadge("Vimeo", supported = isYtdlpEngineReady)
            EngineCapabilityBadge("Doğrudan Medya", supported = true)
        }

        // URL Input
        OutlinedTextField(
            value = youtubeUrlInput,
            onValueChange = onUrlInputChange,
            placeholder = {
                Text(
                    text = "YouTube, SoundCloud, Vimeo, doğrudan medya bağlantısı...",
                    fontSize = 12.sp
                )
            },
            trailingIcon = {
                if (youtubeUrlInput.isNotEmpty()) {
                    IconButton(onClick = { onUrlInputChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Temizle")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("download_sheet_url_input")
        )

        // URL Support Status if any
        if (currentUrlSupportStatus != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (currentUrlSupportStatus.isSupported) {
                            palette.primaryAccent.copy(alpha = 0.15f)
                        } else {
                            palette.tertiaryAccent.copy(alpha = 0.18f)
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (currentUrlSupportStatus.isSupported) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (currentUrlSupportStatus.isSupported) palette.primaryAccent else palette.tertiaryAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = currentUrlSupportStatus.reasonMessage,
                    fontSize = 11.sp,
                    color = palette.onSurface
                )
            }
        }

        // Format selector chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DownloadFormatOption.entries.forEach { opt ->
                val isSelected = opt == selectedFormat
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectFormat(opt) },
                    label = { Text(opt.displayName, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.primaryAccent,
                        selectedLabelColor = palette.onPrimary
                    )
                )
            }
        }

        // Advanced Options Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedFormat.description,
                fontSize = 11.sp,
                color = palette.onSurfaceMuted,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showAdvancedOptions = !showAdvancedOptions }) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Gelişmiş Seçenekler",
                    tint = palette.primaryAccent
                )
            }
        }

        if (showAdvancedOptions) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surface)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EngineSwitchRow("Kapak Fotoğrafı ve Metadata Göm", embedMetadataAndThumb, onSetEmbedMetadata)
                EngineSwitchRow("SponsorBlock Bölümlerini Kaldır", useSponsorBlock, onSetUseSponsorBlock)
                EngineSwitchRow("Aria2c Harici İndirici Kullan", useAria2c, onSetUseAria2c)
                EngineSwitchRow("Otomatik Altyazıları .LRC İndir", writeAutoSubtitlesLrc, onSetWriteAutoSubs)
            }
        }

        // Action Buttons: Çözümle & Dinle / Kalıcı İndir
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    onResolveAndPlay()
                    onCloseSheet()
                },
                enabled = !isResolvingStream,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.secondaryAccent,
                    contentColor = palette.onPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .sakuraPressScale()
            ) {
                if (isResolvingStream) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Çözümle & Dinle", fontSize = 12.sp)
                }
            }

            Button(
                onClick = onStartYoutubeDLDownload,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primaryAccent,
                    contentColor = palette.onPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .sakuraPressScale()
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Kalıcı İndir", fontSize = 12.sp)
            }
        }

        // Active State Card
        if (youtubeDLState !is YoutubeDLDownloadState.Idle) {
            YoutubeDLActiveStateCard(
                state = youtubeDLState,
                onCancel = onCancelYoutubeDLDownload,
                onPlaySaved = {
                    onPlaySavedTrack(it)
                    onCloseSheet()
                }
            )
        }

        // Çerezler ile Beğenilenler (YT Müzik vs YT Videoları)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surface.copy(alpha = 0.65f))
                .border(1.dp, palette.dividerOrBorder.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Çerezler İle Beğenilenler Listesini Çek:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onDownloadAccountLibraryWithCookies(LikedSongsSourceType.YOUTUBE_MUSIC)
                        onCloseSheet()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("YT Müzik (LM)", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = {
                        onDownloadAccountLibraryWithCookies(LikedSongsSourceType.YOUTUBE_VIDEOS)
                        onCloseSheet()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("YT Videolar (LL)", fontSize = 11.sp)
                }
            }
        }

        // Subscriptions / Sync Shortcut
        OutlinedButton(
            onClick = {
                onOpenSubscriptionsClick()
                onCloseSheet()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Oto-Senkronizasyon ve Kanal Abonelikleri", fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun EngineCapabilityBadge(
    label: String,
    supported: Boolean
) {
    val palette = LocalAppThemePalette.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (supported) palette.primaryAccent.copy(alpha = 0.16f)
                else palette.surfaceVariant.copy(alpha = 0.6f)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (supported) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (supported) palette.primaryAccent else palette.onSurfaceMuted,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (supported) palette.onSurface else palette.onSurfaceMuted
        )
    }
}

@Composable
private fun EngineSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalAppThemePalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            color = palette.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun YoutubeDLActiveStateCard(
    state: YoutubeDLDownloadState,
    onCancel: () -> Unit,
    onPlaySaved: (SavedMediaResult) -> Unit
) {
    val palette = LocalAppThemePalette.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.dp, palette.primaryAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (state) {
            is YoutubeDLDownloadState.Initializing -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(state.message, color = palette.onSurface, fontSize = 12.sp)
                }
            }
            is YoutubeDLDownloadState.FetchingInfo -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(state.message, color = palette.onSurface, fontSize = 12.sp)
                }
            }
            is YoutubeDLDownloadState.Downloading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = palette.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${state.formatOption.displayName} • %${state.progressPercent.toInt()} • Kalan: ${state.etaSeconds}sn",
                            fontSize = 11.sp,
                            color = palette.primaryAccent
                        )
                    }
                    OutlinedButton(onClick = onCancel) {
                        Text("İptal", fontSize = 11.sp)
                    }
                }
                LinearProgressIndicator(
                    progress = { (state.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = palette.primaryAccent,
                    trackColor = palette.surfaceVariant
                )
                Text(
                    text = state.statusLine,
                    fontSize = 10.sp,
                    color = palette.onSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            is YoutubeDLDownloadState.SavingMetadata -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(state.title, fontWeight = FontWeight.Bold, color = palette.onSurface, fontSize = 12.sp)
                        Text(state.message, fontSize = 11.sp, color = palette.onSurfaceMuted)
                    }
                }
            }
            is YoutubeDLDownloadState.Success -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "İndirme Tamamlandı!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = palette.primaryAccent
                        )
                        Text(
                            text = "${state.savedMedia.trackInfo.artist} - ${state.savedMedia.trackInfo.title}",
                            fontSize = 12.sp,
                            color = palette.onSurface
                        )
                    }
                    Button(onClick = { onPlaySaved(state.savedMedia) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dinle", fontSize = 11.sp)
                    }
                }
            }
            is YoutubeDLDownloadState.Cancelled -> {
                Text(state.message, fontSize = 12.sp, color = palette.onSurfaceMuted)
            }
            is YoutubeDLDownloadState.Error -> {
                Column {
                    Text(
                        text = "İndirme Hatası: ${state.message}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = palette.tertiaryAccent
                    )
                    if (!state.technicalDetails.isNullOrBlank()) {
                        Text(
                            text = state.technicalDetails,
                            fontSize = 10.sp,
                            color = palette.onSurfaceMuted,
                            maxLines = 2
                        )
                    }
                }
            }
            is YoutubeDLDownloadState.Idle -> Unit
        }
    }
}

@Composable
private fun PendingDownloadQueueCard(
    downloadQueue: List<MediaTrackInfo>,
    completedCount: Int,
    totalCount: Int,
    onMoveToTop: (String) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onRemoveMultiple: (Collection<String>) -> Unit = {},
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    var isQueueExpanded by remember { mutableStateOf(true) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedQueueIds by remember { mutableStateOf(setOf<String>()) }

    val currentQueueIds = remember(downloadQueue) { downloadQueue.map { it.id }.toSet() }
    val validSelectedIds = remember(selectedQueueIds, currentQueueIds) {
        selectedQueueIds.filter { it in currentQueueIds }.toSet()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surfaceContainer.copy(alpha = 0.92f))
            .border(1.dp, palette.primaryAccent.copy(alpha = 0.50f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable { isQueueExpanded = !isQueueExpanded }
            ) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = palette.primaryAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Sıradaki İndirmeler Kuyruğu (${downloadQueue.size} Şarkı)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                    if (totalCount > 0) {
                        Text(
                            text = "Tamamlanan: $completedCount / $totalCount • Bekleyen: ${downloadQueue.size}",
                            fontSize = 10.sp,
                            color = palette.primaryAccent
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (downloadQueue.size > 1) {
                    TextButton(
                        onClick = {
                            if (isMultiSelectMode) {
                                isMultiSelectMode = false
                                selectedQueueIds = emptySet()
                            } else {
                                isMultiSelectMode = true
                                isQueueExpanded = true
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("toggle_multi_select_queue_button")
                    ) {
                        Text(
                            text = if (isMultiSelectMode) "Vazgeç" else "Çoklu Seç",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primaryAccent
                        )
                    }
                }

                if (isMultiSelectMode && validSelectedIds.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            onRemoveMultiple(validSelectedIds)
                            selectedQueueIds = emptySet()
                            isMultiSelectMode = false
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("remove_selected_from_queue_button")
                    ) {
                        Text(
                            text = "Seçileni Kaldır (${validSelectedIds.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.tertiaryAccent
                        )
                    }
                } else {
                    TextButton(
                        onClick = onClearQueue,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("clear_download_queue_button")
                    ) {
                        Text(
                            text = "Kuyruğu Temizle",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.tertiaryAccent
                        )
                    }
                }

                IconButton(
                    onClick = { isQueueExpanded = !isQueueExpanded },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isQueueExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isQueueExpanded) "Daralt" else "Genişlet",
                        tint = palette.onSurfaceMuted
                    )
                }
            }
        }

        if (isQueueExpanded) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 210.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.surface.copy(alpha = 0.65f))
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(
                    items = downloadQueue,
                    key = { index, track -> track.id.ifBlank { "${track.sourceUrl}_$index" } },
                    contentType = { _, _ -> "queue_row" }
                ) { index, track ->
                    val isSelected = track.id in validSelectedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isMultiSelectMode) {
                                selectedQueueIds = if (isSelected) {
                                    selectedQueueIds - track.id
                                } else {
                                    selectedQueueIds + track.id
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isMultiSelectMode) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedQueueIds = if (checked) {
                                        selectedQueueIds + track.id
                                    } else {
                                        selectedQueueIds - track.id
                                    }
                                },
                                modifier = Modifier
                                    .size(26.dp)
                                    .testTag("queue_select_${track.id}"),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = palette.primaryAccent,
                                    uncheckedColor = palette.onSurfaceMuted
                                )
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (index == 0) palette.primaryAccent.copy(alpha = 0.25f)
                                        else palette.surfaceVariant.copy(alpha = 0.6f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (index == 0) palette.primaryAccent else palette.onSurfaceMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontSize = 12.sp,
                                fontWeight = if (index == 0 && !isMultiSelectMode) FontWeight.Bold else FontWeight.Medium,
                                color = palette.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (index == 0 && !isMultiSelectMode) "Sıradaki İlk Şarkı • ${track.artist}" else track.artist,
                                fontSize = 10.sp,
                                color = if (index == 0 && !isMultiSelectMode) palette.primaryAccent else palette.onSurfaceMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!isMultiSelectMode && index > 0) {
                            IconButton(
                                onClick = { onMoveToTop(track.id) },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("queue_move_top_${track.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "En Üste Taşı",
                                    tint = palette.primaryAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { onRemoveTrack(track.id) },
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("queue_remove_${track.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kuyruktan Çıkar",
                                tint = palette.onSurfaceMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResolvedPlaylistSelectionCard(
    playlistTitle: String,
    tracks: List<MediaTrackInfo>,
    selectedTrackIds: Set<String>,
    selectedFormat: DownloadFormatOption,
    isDownloading: Boolean,
    completedCount: Int,
    totalCount: Int,
    onToggleTrack: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onDownloadSelected: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalAppThemePalette.current
    var isExpanded by remember { mutableStateOf(true) }
    val selectedCount = selectedTrackIds.size
    val allSelected = selectedCount == tracks.size && tracks.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surfaceContainer.copy(alpha = 0.90f))
            .border(1.dp, palette.primaryAccent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Kart Başlığı & Hızlı Eylemler
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = palette.primaryAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = playlistTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Seçili: $selectedCount / ${tracks.size} Parça • ${selectedFormat.displayName}",
                        fontSize = 11.sp,
                        color = if (selectedCount > 0) palette.primaryAccent else palette.tertiaryAccent
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Daralt" else "Genişlet",
                        tint = palette.onSurfaceMuted
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Listeyi Kapat",
                        tint = palette.onSurfaceMuted
                    )
                }
            }
        }

        if (isDownloading) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { if (totalCount > 0) (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = palette.primaryAccent,
                    trackColor = palette.surfaceVariant
                )
                Text(
                    text = "İndiriliyor: $completedCount / $totalCount tamamlandı...",
                    fontSize = 11.sp,
                    color = palette.primaryAccent,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Genişletilebilir Parça Seçim ve Eleme Listesi
        if (isExpanded) {
            // Morphe Yama Paketi: Hızlı Aralık Seçici (İlk 25, 26-50, 51-75, 76-100, Son 25, Son 50, Son 100, Tümü)
            val rangeOptions = remember(tracks.size) {
                buildList {
                    add(Triple("Tümü (${tracks.size})", 0, tracks.size))
                    if (tracks.size > 1) {
                        add(Triple("İlk 25", 0, minOf(25, tracks.size)))
                    }
                    if (tracks.size > 25) {
                        add(Triple("26 - 50", 25, minOf(50, tracks.size)))
                    }
                    if (tracks.size > 50) {
                        add(Triple("51 - 75", 50, minOf(75, tracks.size)))
                    }
                    if (tracks.size > 75) {
                        add(Triple("76 - 100", 75, minOf(100, tracks.size)))
                    }
                    if (tracks.size > 25) {
                        add(Triple("Son 25", (tracks.size - 25).coerceAtLeast(0), tracks.size))
                    }
                    if (tracks.size > 50) {
                        add(Triple("Son 50", (tracks.size - 50).coerceAtLeast(0), tracks.size))
                    }
                    if (tracks.size > 100) {
                        add(Triple("Son 100", (tracks.size - 100).coerceAtLeast(0), tracks.size))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Hızlı Aralık Seçimi (Morphe Yama Modülü):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryAccent
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rangeOptions.forEach { (label, startIdx, endIdx) ->
                        val rangeIds = remember(tracks, startIdx, endIdx) {
                            tracks.subList(startIdx.coerceIn(0, tracks.size), endIdx.coerceIn(0, tracks.size))
                                .map { it.id }
                                .toSet()
                        }
                        val isRangeActive = selectedTrackIds == rangeIds && rangeIds.isNotEmpty()
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isRangeActive) palette.primaryAccent
                                    else palette.surface.copy(alpha = 0.75f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isRangeActive) palette.primaryAccent else palette.dividerOrBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (startIdx == 0 && endIdx == tracks.size) {
                                        onSelectAll(true)
                                    } else {
                                        onSelectAll(false)
                                        rangeIds.forEach { id -> onToggleTrack(id) }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRangeActive) palette.onPrimary else palette.onSurface
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "İstemediğiniz parçayı tek dokunuşla eleyin:",
                    fontSize = 11.sp,
                    color = palette.onSurfaceMuted
                )
                TextButton(
                    onClick = { onSelectAll(!allSelected) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (allSelected) "Seçimi Bırak" else "Tümünü Seç", fontSize = 11.sp)
                }
            }

            // Şarkı Listesi (Gerçek LazyColumn ile sadece ekranda görünen satırlar belleğe yüklenir)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.surface.copy(alpha = 0.55f))
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(
                    items = tracks,
                    key = { index, track -> "${track.id}_$index" },
                    contentType = { _, _ -> "resolved_playlist_row" }
                ) { index, track ->
                    val isSelected = track.id in selectedTrackIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) palette.primaryAccent.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .clickable { onToggleTrack(track.id) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) palette.primaryAccent else palette.onSurfaceMuted,
                            modifier = Modifier.width(24.dp)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                        ) {
                            Text(
                                text = track.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) palette.onSurface else palette.onSurfaceMuted.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (track.artist.isNotBlank()) {
                                Text(
                                    text = track.artist,
                                    fontSize = 10.sp,
                                    color = palette.onSurfaceMuted.copy(alpha = if (isSelected) 0.85f else 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Seçili" else "Elenmiş",
                            tint = if (isSelected) palette.primaryAccent else palette.onSurfaceMuted.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Alt İndir Butonu
        Button(
            onClick = onDownloadSelected,
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedCount > 0 && !isDownloading,
            colors = ButtonDefaults.buttonColors(containerColor = palette.primaryAccent)
        ) {
            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (selectedCount > 0) "Seçili $selectedCount / ${tracks.size} Şarkıyı İndir" else "Hiçbir Şarkı Seçilmedi",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
