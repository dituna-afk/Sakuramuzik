package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import com.example.downloader.SavedMediaResult
import com.example.ui.components.OptimizedThumbnailImage
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.AppIconOption
import com.example.data.CustomMinimalThemeManager
import com.example.data.EffectIntensityLevel
import com.example.data.FeatureTogglesConfig
import com.example.data.NowPlayingSceneMode
import com.example.data.PetalDensityLevel
import com.example.data.SakuraAnimationConfig
import com.example.data.SakuraAnimationProfile
import com.example.data.SakuraTreeForm
import com.example.data.SakuraTreeStyle
import com.example.data.TransitionSpeedLevel
import com.example.data.WallpaperTargetScreen
import com.example.downloader.DownloadFormatOption
import com.example.downloader.LikedSongsSourceType
import com.example.downloader.LyricsSource
import com.example.downloader.TranslationEngine
import com.example.player.StudioDspConfig
import com.example.ui.player.StudioDspDialog
import com.example.ui.theme.AnimeArtRegistry
import com.example.ui.theme.AppTheme
import com.example.ui.theme.LocalAppThemePalette
import com.example.ui.theme.backgroundGradientColors
import com.example.ui.theme.isSakuraTheme
import com.example.ui.theme.isSuperOptimizedTheme
import com.example.ui.theme.toPalette
import kotlinx.coroutines.launch

enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tag: String
) {
    SUPER_OPTIMIZED(
        title = "Süper Optimize",
        subtitle = "120 FPS Sıfır Parçacıklı Temalar, GIF & HEX Stüdyosu",
        icon = Icons.Default.Bolt,
        tag = "tab_super_optimized"
    ),
    QUEUE(
        title = "Kuyruk",
        subtitle = "Mevcut Oynatma Kuyruğu & Parça Sırası",
        icon = Icons.AutoMirrored.Filled.QueueMusic,
        tag = "tab_queue"
    ),
    APPEARANCE(
        title = "Görünüm",
        subtitle = "Sakura Temaları, Anime & Ağaç Özelleştirme",
        icon = Icons.Default.Palette,
        tag = "tab_appearance"
    ),
    AUDIO(
        title = "Ses",
        subtitle = "Stüdyo DSP, EQ & +20dB Gain",
        icon = Icons.Default.GraphicEq,
        tag = "tab_audio"
    ),
    PLAYER(
        title = "Oynatıcı",
        subtitle = "Sahneler, Sözler & Türkçe Çeviri",
        icon = Icons.Default.Album,
        tag = "tab_player"
    ),
    SYSTEM(
        title = "Sistem",
        subtitle = "Wi-Fi Oto-İndirme, Hesap, İkon & Motor",
        icon = Icons.Default.Settings,
        tag = "tab_system"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    selectedTheme: AppTheme,
    selectedTreeStyle: SakuraTreeStyle,
    selectedTreeForm: SakuraTreeForm,
    animationConfig: SakuraAnimationConfig,
    youtubeDLVersion: String,
    isYtdlpEngineReady: Boolean,
    isFFmpegReady: Boolean,
    isAria2cReady: Boolean,
    isUpdatingYoutubeDL: Boolean,
    autoTranslateLyricsToTurkish: Boolean,
    translationEngine: TranslationEngine = TranslationEngine.GOOGLE_TRANSLATE,
    selectedAppIcon: AppIconOption = AppIconOption.DEFAULT,
    volumeBoostPercent: Int = 100,
    isVolumeBoosterEnabled: Boolean = false,
    featureToggles: FeatureTogglesConfig = FeatureTogglesConfig(),
    studioDspConfig: StudioDspConfig = StudioDspConfig(),
    isBatchFetchingLyrics: Boolean = false,
    batchLyricsCompletedCount: Int = 0,
    batchLyricsTotalCount: Int = 0,
    isLikedSongsWifiAutoSyncEnabled: Boolean = true,
    onBatchFetchAndTranslateAllLyrics: () -> Unit = {},
    onSetLikedSongsWifiAutoSyncEnabled: (Boolean) -> Unit = {},
    onUpdateFeatureToggles: ((FeatureTogglesConfig) -> FeatureTogglesConfig) -> Unit = {},
    onUpdateStudioDspConfig: ((StudioDspConfig) -> StudioDspConfig) -> Unit = {},
    onSelectTheme: (AppTheme) -> Unit,
    onSelectTreeStyle: (SakuraTreeStyle) -> Unit,
    onSelectTreeForm: (SakuraTreeForm) -> Unit,
    onSelectAppIcon: (AppIconOption) -> Unit = {},
    selectedDownloadFormat: DownloadFormatOption = DownloadFormatOption.AUDIO_M4A_HIGH,
    onSelectDownloadFormat: (DownloadFormatOption) -> Unit = {},
    onDownloadAccountLibraryWithCookies: (LikedSongsSourceType) -> Unit = {},
    onSelectAnimationProfile: (SakuraAnimationProfile) -> Unit,
    onSetAnimationsMasterEnabled: (Boolean) -> Unit,
    onSelectPetalDensity: (PetalDensityLevel) -> Unit,
    onSelectTransitionSpeed: (TransitionSpeedLevel) -> Unit,
    onSelectEffectIntensity: (EffectIntensityLevel) -> Unit,
    onSetFloatingPetalsEnabled: (Boolean) -> Unit,
    onSetAudioReactiveWavesEnabled: (Boolean) -> Unit,
    onSetInteractiveBurstEnabled: (Boolean) -> Unit,
    onSetScreenTransitionsEnabled: (Boolean) -> Unit,
    onSetPowerSaveAutoReduce: (Boolean) -> Unit,
    onSetAutoTranslateLyricsToTurkish: (Boolean) -> Unit,
    onSelectTranslationEngine: (TranslationEngine) -> Unit,
    lyricsSourceOrder: List<LyricsSource> = LyricsSource.DEFAULT_ORDER,
    enabledLyricsSources: Set<LyricsSource> = LyricsSource.DEFAULT_ORDER.toSet(),
    onMoveLyricsSourceUp: (LyricsSource) -> Unit = {},
    onMoveLyricsSourceDown: (LyricsSource) -> Unit = {},
    onToggleLyricsSourceEnabled: (LyricsSource, Boolean) -> Unit = { _, _ -> },
    onResetLyricsSourceOrder: () -> Unit = {},
    onSetVolumeBoost: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateYoutubeDL: (Boolean) -> Unit,
    playbackQueue: List<SavedMediaResult> = emptyList(),
    currentQueueIndex: Int = -1,
    onRemoveFromQueue: (Int) -> Unit = {},
    onClearQueue: () -> Unit = {},
    onPlayQueueIndex: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sadeConfig by CustomMinimalThemeManager.configFlow.collectAsState()

    var activeCategory by rememberSaveable { mutableStateOf(SettingsCategory.SUPER_OPTIMIZED) }
    var showStudioDspDialog by remember { mutableStateOf(false) }
    var showSakuraTreeSheet by rememberSaveable { mutableStateOf(false) }
    var showClearQueueConfirmationDialog by remember { mutableStateOf(false) }

    var pendingWallpaperTarget by remember { mutableStateOf(WallpaperTargetScreen.HOME) }
    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val target = pendingWallpaperTarget
            coroutineScope.launch {
                CustomMinimalThemeManager.importWallpaperFromUri(context, target, uri)
            }
        }
    }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                CustomMinimalThemeManager.importCustomFontFromUri(context, uri)
            }
        }
    }

    if (showStudioDspDialog) {
        StudioDspDialog(
            dspConfig = studioDspConfig,
            featureToggles = featureToggles,
            onUpdateDspConfig = onUpdateStudioDspConfig,
            onUpdateFeatureToggles = onUpdateFeatureToggles,
            onDismiss = { showStudioDspDialog = false }
        )
    }

    if (showSakuraTreeSheet) {
        SakuraTreeCustomizationBottomSheet(
            selectedTreeForm = selectedTreeForm,
            selectedTreeStyle = selectedTreeStyle,
            onSelectTreeForm = onSelectTreeForm,
            onSelectTreeStyle = onSelectTreeStyle,
            onDismiss = { showSakuraTreeSheet = false }
        )
    }

    if (showClearQueueConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showClearQueueConfirmationDialog = false },
            title = {
                Text("Oynatma Kuyruğunu Temizle", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(
                    text = "Mevcut oynatma kuyruğundaki tüm şarkılar sıradan çıkarılacak ve çalma durdurulacaktır. İndirilmiş müzik dosyalarınız kesinlikle silinmez.",
                    fontSize = 13.sp,
                    color = palette.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearQueue()
                        showClearQueueConfirmationDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.tertiaryAccent),
                    modifier = Modifier.testTag("confirm_clear_queue_button")
                ) {
                    Text("Temizle", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearQueueConfirmationDialog = false },
                    modifier = Modifier.testTag("cancel_clear_queue_button")
                ) {
                    Text("İptal")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Üst Başlık
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ayarlar & Özelleştirme",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.onBackground
                )
                Text(
                    text = activeCategory.subtitle,
                    fontSize = 11.sp,
                    color = palette.onSurfaceMuted
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.primaryAccent.copy(alpha = 0.16f))
                    .border(1.dp, palette.primaryAccent.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "v3.0 • 120 FPS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryAccent
                )
            }
        }

        // ÜST 5 KATEGORİ SEKMESİ (Süper Optimize | Görünüm | Ses | Oynatıcı | Sistem)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .clip(RoundedCornerShape(16.dp))
                .background(palette.surfaceContainer)
                .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SettingsCategory.entries.forEach { category ->
                val isSelected = activeCategory == category
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) palette.primaryAccent
                            else Color.Transparent
                        )
                        .clickable { activeCategory = category }
                        .padding(vertical = 9.dp, horizontal = 11.dp)
                        .testTag(category.tag),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = category.title,
                        tint = if (isSelected) palette.onPrimary else palette.onSurfaceMuted,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = category.title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (isSelected) palette.onPrimary else palette.onSurfaceMuted,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val settingsListState = rememberLazyListState()

        // Seçili Kategori İçeriği
        LazyColumn(
            state = settingsListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 115.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (activeCategory) {
                // =====================================================================
                // KATEGORİ: OYNATMA KUYRUĞU YÖNETİMİ
                // =====================================================================
                SettingsCategory.QUEUE -> {
                    item(key = "queue_header_summary") {
                        val currentPlayingTrack = if (currentQueueIndex in playbackQueue.indices) playbackQueue[currentQueueIndex] else null
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(palette.surfaceContainer)
                                .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(18.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(palette.primaryAccent.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                            contentDescription = null,
                                            tint = palette.primaryAccent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Oynatma Kuyruğu (${playbackQueue.size} Parça)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = palette.onSurface
                                        )
                                        Text(
                                            text = if (currentPlayingTrack != null) {
                                                "Çalıyor: ${currentPlayingTrack.trackInfo.title}"
                                            } else if (playbackQueue.isNotEmpty()) {
                                                "Sırada bekleyen ${playbackQueue.size} şarkı var"
                                            } else {
                                                "Kuyruk boş"
                                            },
                                            fontSize = 11.5.sp,
                                            color = palette.onSurfaceMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (playbackQueue.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { showClearQueueConfirmationDialog = true },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("queue_clear_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            tint = palette.tertiaryAccent,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Temizle",
                                            fontSize = 12.sp,
                                            color = palette.tertiaryAccent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (playbackQueue.isEmpty()) {
                        item(key = "queue_empty_state") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(palette.surfaceContainer)
                                    .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(18.dp))
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(palette.primaryAccent.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = palette.primaryAccent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Text(
                                    text = "Oynatma Kuyruğu Boş",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.onSurface
                                )
                                Text(
                                    text = "Şu anda sırada bekleyen herhangi bir şarkı yok. Kütüphanenizden veya çalma listelerinizden şarkı seçerek hemen oynatmaya başlayabilirsiniz.",
                                    fontSize = 12.sp,
                                    color = palette.onSurfaceMuted,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = playbackQueue,
                            key = { index, item -> "${item.audioFile.absolutePath}_${index}" }
                        ) { index, item ->
                            val isCurrentPlaying = (index == currentQueueIndex)
                            val track = item.trackInfo
                            val thumbModel = remember(item) {
                                item.coverFile?.takeIf { it.exists() } ?: track.thumbnailUrl
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isCurrentPlaying) palette.primaryAccent.copy(alpha = 0.14f)
                                        else palette.surfaceContainer
                                    )
                                    .border(
                                        width = if (isCurrentPlaying) 1.5.dp else 1.dp,
                                        color = if (isCurrentPlaying) palette.primaryAccent else palette.dividerOrBorder,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onPlayQueueIndex(index) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("queue_track_item_$index"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isCurrentPlaying) palette.primaryAccent
                                            else palette.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCurrentPlaying) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Çalıyor",
                                            tint = palette.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.onSurfaceMuted
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                OptimizedThumbnailImage(
                                    model = thumbModel,
                                    contentDescription = track.title,
                                    isWithinActiveWindow = true,
                                    fallbackIcon = Icons.Default.MusicNote,
                                    fallbackTint = palette.primaryAccent,
                                    backgroundColor = palette.surfaceVariant,
                                    iconSize = 20.dp,
                                    targetPixelSize = 128,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isCurrentPlaying) palette.primaryAccent else palette.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isCurrentPlaying) {
                                            Text(
                                                text = "Şu Anda Çalıyor • ",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = palette.primaryAccent
                                            )
                                        }
                                        Text(
                                            text = track.artist.ifBlank { "Bilinmeyen Sanatçı" },
                                            fontSize = 11.sp,
                                            color = palette.onSurfaceMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onRemoveFromQueue(index) },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("remove_queue_track_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Kuyruktan Kaldır",
                                        tint = palette.onSurfaceMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // =====================================================================
                // KATEGORİ 0: SÜPER OPTİMİZE TEMALAR & GELİŞMİŞ KİŞİSELLEŞTİRME (GIF & HEX)
                // =====================================================================
                SettingsCategory.SUPER_OPTIMIZED -> {
                    // 1. 6+1 SÜPER OPTİMİZE TEMA KARTI (Sıfır Parçacık, Saf Gradyan & 120 FPS)
                    item(key = "super_opt_themes_list") {
                        val superThemes = remember {
                            listOf(
                                AppTheme.YT_MUSIC_PINK,
                                AppTheme.AMOLED_NIGHT,
                                AppTheme.EMERALD_DEPTH,
                                AppTheme.SAPPHIRE_OCEAN,
                                AppTheme.CRIMSON_VELVET,
                                AppTheme.ROYAL_AMETHYST,
                                AppTheme.SADE_TEMA
                            )
                        }
                        SettingsGroupCard(
                            title = "⚡ Süper Optimize Temalar (Sıfır Parçacık • 120 FPS)",
                            subtitle = "Arka plan çizimi ve parçacık yükü içermeyen pürüzsüz gradyan temalar",
                            icon = Icons.Default.Bolt,
                            testTag = "card_super_optimized_themes"
                        ) {
                            superThemes.forEach { theme ->
                                val themePalette = remember(theme, sadeConfig) { theme.toPalette() }
                                val gradColors = remember(theme, themePalette.background) {
                                    theme.backgroundGradientColors(themePalette.background)
                                }
                                val isSelected = theme == selectedTheme

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isSelected) palette.primaryAccent.copy(alpha = 0.16f)
                                            else palette.surface
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) palette.primaryAccent else palette.dividerOrBorder,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { onSelectTheme(theme) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("theme_item_${theme.name}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Brush.verticalGradient(gradColors))
                                            .border(1.5.dp, themePalette.primaryAccent, RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(themePalette.primaryAccent)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = theme.displayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = palette.onSurface
                                        )
                                        Text(
                                            text = theme.subtitle,
                                            fontSize = 11.sp,
                                            color = palette.onSurfaceMuted
                                        )
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Seçili",
                                            tint = palette.primaryAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. ANİMASYON PROFİLLERİ (YAYLI, SİBER, YUMUŞAK, MİNİMALİST)
                    item(key = "super_opt_animation_profiles") {
                        val coreProfiles = remember {
                            listOf(
                                SakuraAnimationProfile.SPRING,
                                SakuraAnimationProfile.CYBER,
                                SakuraAnimationProfile.SMOOTH,
                                SakuraAnimationProfile.MINIMAL
                            )
                        }
                        SettingsGroupCard(
                            title = "🚀 Ekran Geçiş & Etkileşim Animasyon Profilleri",
                            subtitle = "Seçili Profil: ${animationConfig.profile.displayName}",
                            icon = Icons.Default.AutoAwesome,
                            testTag = "card_super_opt_anim_profiles"
                        ) {
                            Text(
                                text = "Parçacık efektlerinden bağımsız olarak uygulama içi sekme ve oynatıcı geçiş hızını/karakterini belirler:",
                                fontSize = 11.sp,
                                color = palette.onSurfaceMuted
                            )
                            coreProfiles.forEach { profile ->
                                val isSelected = animationConfig.profile == profile
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) palette.primaryAccent.copy(alpha = 0.16f)
                                            else palette.surface
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) palette.primaryAccent else palette.dividerOrBorder,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onSelectAnimationProfile(profile) }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = profile.displayName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.onSurface
                                        )
                                        Text(
                                            text = profile.description,
                                            fontSize = 10.sp,
                                            color = palette.onSurfaceMuted
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Seçili",
                                            tint = palette.primaryAccent
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. HAREKETLİ GIF & GÖRSEL STÜDYOSU (Coil GIF Desteği: Arka Plan, Oynatıcı, Mini Oynatıcı, Liste Başlığı)
                    item(key = "super_opt_gif_wallpaper_studio") {
                        SettingsGroupCard(
                            title = "🖼️ Hareketli GIF & Görsel Özelleştirme (Coil GIF)",
                            subtitle = "Uygulama Arka Planı, Oynatıcı, Mini Oynatıcı ve Liste Başlıklarına GIF/Resim ekleyin",
                            icon = Icons.Default.Tune,
                            testTag = "card_gif_wallpaper_studio"
                        ) {
                            Text(
                                text = "Cihazınızdan hareketli .GIF, .WEBP veya yüksek çözünürlüklü görsel seçebilirsiniz. Tüm temalarda aktif çalışır.",
                                fontSize = 11.sp,
                                color = palette.onSurfaceMuted
                            )

                            WallpaperTargetScreen.entries.forEach { screenTarget ->
                                val currentPath = when (screenTarget) {
                                    WallpaperTargetScreen.HOME -> sadeConfig.homeWallpaperPath
                                    WallpaperTargetScreen.NOW_PLAYING -> sadeConfig.playerWallpaperPath
                                    WallpaperTargetScreen.MINI_PLAYER -> sadeConfig.miniPlayerWallpaperPath
                                    WallpaperTargetScreen.HEADER_BANNER -> sadeConfig.headerBannerWallpaperPath
                                }
                                val currentOpacity = when (screenTarget) {
                                    WallpaperTargetScreen.HOME -> sadeConfig.homeWallpaperOpacity
                                    WallpaperTargetScreen.NOW_PLAYING -> sadeConfig.playerWallpaperOpacity
                                    WallpaperTargetScreen.MINI_PLAYER -> sadeConfig.miniPlayerWallpaperOpacity
                                    WallpaperTargetScreen.HEADER_BANNER -> sadeConfig.headerBannerWallpaperOpacity
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(palette.surface)
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = screenTarget.displayName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = palette.onSurface
                                            )
                                            if (currentPath != null) {
                                                Text(
                                                    text = "Aktif: ${currentPath.substringAfterLast('/')}",
                                                    fontSize = 10.sp,
                                                    color = palette.primaryAccent
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    pendingWallpaperTarget = screenTarget
                                                    wallpaperPickerLauncher.launch("image/*")
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    if (currentPath != null) "GIF / Görsel Değiştir" else "GIF / Görsel Seç",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (currentPath != null) {
                                                TextButton(
                                                    onClick = { CustomMinimalThemeManager.clearWallpaper(context, screenTarget) },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Kaldır", fontSize = 11.sp, color = palette.tertiaryAccent)
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Opaklık: %${(currentOpacity * 100).toInt()}",
                                            fontSize = 11.sp,
                                            color = palette.onSurfaceMuted,
                                            modifier = Modifier.width(88.dp)
                                        )
                                        Slider(
                                            value = currentOpacity,
                                            onValueChange = { newAlpha ->
                                                CustomMinimalThemeManager.updateConfig(context) { cur ->
                                                    when (screenTarget) {
                                                        WallpaperTargetScreen.HOME -> cur.copy(homeWallpaperOpacity = newAlpha)
                                                        WallpaperTargetScreen.NOW_PLAYING -> cur.copy(playerWallpaperOpacity = newAlpha)
                                                        WallpaperTargetScreen.MINI_PLAYER -> cur.copy(miniPlayerWallpaperOpacity = newAlpha)
                                                        WallpaperTargetScreen.HEADER_BANNER -> cur.copy(headerBannerWallpaperOpacity = newAlpha)
                                                    }
                                                }
                                            },
                                            valueRange = 0.05f..1.0f,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(
                                                thumbColor = palette.primaryAccent,
                                                activeTrackColor = palette.primaryAccent
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. ÖZEL HEX RENK SEÇİCİ & .TTF FONT YÜKLEYİCİ
                    item(key = "super_opt_hex_and_font") {
                        var bgHexInput by remember(sadeConfig.backgroundHex) { mutableStateOf(sadeConfig.backgroundHex) }
                        var surfaceHexInput by remember(sadeConfig.surfaceHex) { mutableStateOf(sadeConfig.surfaceHex) }
                        var accentHexInput by remember(sadeConfig.accentHex) { mutableStateOf(sadeConfig.accentHex) }
                        var textHexInput by remember(sadeConfig.textHex) { mutableStateOf(sadeConfig.textHex) }

                        val quickAccentPresets = remember {
                            listOf(
                                "#FF2A85" to "YT Pembe",
                                "#10B981" to "Zümrüt",
                                "#38BDF8" to "Safir",
                                "#E11D48" to "Yakut",
                                "#A855F7" to "Ametist",
                                "#F59E0B" to "Kehribar"
                            )
                        }

                        SettingsGroupCard(
                            title = "🎨 Özel HEX Renk Seçici & Yazı Tipi (.TTF) Stüdyosu",
                            subtitle = "Kendi HEX renk paletinizi ve özel yazı tipinizi oluşturun",
                            icon = Icons.Default.Palette,
                            testTag = "card_sade_tema"
                        ) {
                            Text(
                                text = "Hızlı Vurgu Rengi Seçimi:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.primaryAccent
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickAccentPresets.forEach { (hex, name) ->
                                    val c = CustomMinimalThemeManager.parseHexColor(hex, palette.primaryAccent)
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(palette.surface)
                                            .border(1.dp, c, RoundedCornerShape(10.dp))
                                            .clickable {
                                                accentHexInput = hex
                                                CustomMinimalThemeManager.updateConfig(context) { cur -> cur.copy(accentHex = hex) }
                                                onSelectTheme(AppTheme.SADE_TEMA)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(c)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.onSurface)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = bgHexInput,
                                    onValueChange = {
                                        bgHexInput = it
                                        if (it.trim().removePrefix("#").length == 6) {
                                            CustomMinimalThemeManager.updateConfig(context) { c -> c.copy(backgroundHex = it.trim()) }
                                            onSelectTheme(AppTheme.SADE_TEMA)
                                        }
                                    },
                                    label = { Text("Arka Plan HEX", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = surfaceHexInput,
                                    onValueChange = {
                                        surfaceHexInput = it
                                        if (it.trim().removePrefix("#").length == 6) {
                                            CustomMinimalThemeManager.updateConfig(context) { c -> c.copy(surfaceHex = it.trim()) }
                                            onSelectTheme(AppTheme.SADE_TEMA)
                                        }
                                    },
                                    label = { Text("Kart Yüzeyi HEX", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = accentHexInput,
                                    onValueChange = {
                                        accentHexInput = it
                                        if (it.trim().removePrefix("#").length == 6) {
                                            CustomMinimalThemeManager.updateConfig(context) { c -> c.copy(accentHex = it.trim()) }
                                            onSelectTheme(AppTheme.SADE_TEMA)
                                        }
                                    },
                                    label = { Text("Ana Vurgu HEX", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = textHexInput,
                                    onValueChange = {
                                        textHexInput = it
                                        if (it.trim().removePrefix("#").length == 6) {
                                            CustomMinimalThemeManager.updateConfig(context) { c -> c.copy(textHex = it.trim()) }
                                            onSelectTheme(AppTheme.SADE_TEMA)
                                        }
                                    },
                                    label = { Text("Yazı / Font HEX", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Button(
                                onClick = {
                                    CustomMinimalThemeManager.updateConfig(context) { cur ->
                                        cur.copy(
                                            backgroundHex = bgHexInput.trim(),
                                            surfaceHex = surfaceHexInput.trim(),
                                            accentHex = accentHexInput.trim(),
                                            textHex = textHexInput.trim()
                                        )
                                    }
                                    onSelectTheme(AppTheme.SADE_TEMA)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Özel HEX Renklerini Uygula", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.surface)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sadeConfig.customFontName ?: "Varsayılan Sistem / Tema Fontu",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.onSurface
                                    )
                                    Text(
                                        text = "Cihazınızdan .ttf veya .otf uzantılı yazı tipi seçin",
                                        fontSize = 10.sp,
                                        color = palette.onSurfaceMuted
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { fontPickerLauncher.launch("*/*") },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("Font Yükle", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (sadeConfig.customFontPath != null) {
                                        OutlinedButton(
                                            onClick = { CustomMinimalThemeManager.clearCustomFont(context) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text("Sıfırla", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // =====================================================================
                // KATEGORİ 1: GÖRÜNÜM (Sakura Temaları, Sakura Ağacı BottomSheet, Anime)
                // =====================================================================
                SettingsCategory.APPEARANCE -> {
                    // 1. SAKURA TEMALARI & AĞAÇ ÖZELLEŞTİRME KARTI
                    item(key = "appearance_sakura_themes") {
                        val sakuraThemes = remember { AppTheme.entries.filter { it.isSakuraTheme() } }
                        SettingsGroupCard(
                            title = "🌸 Sakura Temaları & Ağaç Özelleştirme",
                            subtitle = "Ağaç: ${selectedTreeForm.displayName} • ${selectedTreeStyle.displayName}",
                            icon = Icons.Default.Palette,
                            testTag = "card_sakura_themes",
                            headerAction = {
                                Button(
                                    onClick = { showSakuraTreeSheet = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.primaryAccent,
                                        contentColor = palette.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("open_sakura_tree_sheet_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Ağacı Özelleştir",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        ) {
                            sakuraThemes.forEach { theme ->
                                val themePalette = remember(theme) { theme.toPalette() }
                                val isSelected = theme == selectedTheme

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isSelected) palette.primaryAccent.copy(alpha = 0.16f)
                                            else palette.surface
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) palette.primaryAccent else palette.dividerOrBorder,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            if (isSelected) {
                                                showSakuraTreeSheet = true
                                            } else {
                                                onSelectTheme(theme)
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("theme_item_${theme.name}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(themePalette.background)
                                                .border(1.dp, themePalette.primaryAccent, CircleShape)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(themePalette.primaryAccent)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(themePalette.petalPrimary)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = theme.displayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = palette.onSurface
                                        )
                                        Text(
                                            text = theme.subtitle,
                                            fontSize = 11.sp,
                                            color = palette.onSurfaceMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    OutlinedButton(
                                        onClick = {
                                            if (!isSelected) {
                                                onSelectTheme(theme)
                                            }
                                            showSakuraTreeSheet = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .testTag("customize_tree_${theme.name}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = "Ağacı Özelleştir",
                                            tint = palette.primaryAccent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Özelleştir",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.primaryAccent
                                        )
                                    }

                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Seçili",
                                            tint = palette.primaryAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. ÖZEL ANİME TEMALARI KARTI (Berserk Guts, Berserk Griffith - Sıfır Parçacık Asil Palet, Cowboy Bebop Spike)
                    item(key = "appearance_anime_themes") {
                        val animeThemes = remember {
                            listOf(
                                AppTheme.BERSERK_GUTS,
                                AppTheme.BERSERK_GRIFFITH,
                                AppTheme.COWBOY_BEBOP_SPIKE
                            )
                        }

                        SettingsGroupCard(
                            title = "⚔️ Özel Anime Temaları",
                            subtitle = "Berserk (Guts & Asil Griffith) ve Cowboy Bebop (Spike Spiegel)",
                            icon = Icons.Default.AutoAwesome,
                            testTag = "card_anime_themes"
                        ) {
                            animeThemes.forEach { theme ->
                                val themePalette = remember(theme) { theme.toPalette() }
                                val isSelected = theme == selectedTheme
                                val animeMeta = remember(theme) { AnimeArtRegistry.getMetadata(theme) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isSelected) palette.primaryAccent.copy(alpha = 0.16f)
                                            else palette.surface
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) palette.primaryAccent else palette.dividerOrBorder,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { onSelectTheme(theme) }
                                        .padding(12.dp)
                                        .testTag("theme_item_${theme.name}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (animeMeta?.systemVectorDrawableRes != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(themePalette.background)
                                                .border(1.5.dp, themePalette.primaryAccent, RoundedCornerShape(10.dp))
                                                .padding(2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                painter = painterResource(id = animeMeta.systemVectorDrawableRes),
                                                contentDescription = animeMeta.characterName,
                                                contentScale = ContentScale.Fit,
                                                alignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    } else if (animeMeta?.primaryAssetPath != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(themePalette.background)
                                                .border(1.5.dp, themePalette.primaryAccent, RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = animeMeta.primaryAssetPath,
                                                contentDescription = animeMeta.characterName,
                                                contentScale = ContentScale.Fit,
                                                alignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = theme.displayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = palette.onSurface
                                        )
                                        Text(
                                            text = theme.subtitle,
                                            fontSize = 11.sp,
                                            color = palette.onSurfaceMuted
                                        )
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Seçili",
                                            tint = palette.primaryAccent
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            SettingToggleItem(
                                label = "🩸 Albüm Kapağı Üstü Anime Süslemesi (Yalnızca Berserk Behelit)",
                                checked = featureToggles.animeCoverDecorationsEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(animeCoverDecorationsEnabled = enabled) }
                                }
                            )

                            SettingToggleItem(
                                label = "🖼️ Anime Karakter Duvar Kağıtları & Posterler",
                                checked = featureToggles.animeArtworkBackgroundEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(animeArtworkBackgroundEnabled = enabled) }
                                }
                            )

                            SettingToggleItem(
                                label = "🔤 Temaya Özel Anime Yazı Tipleri (Berserk & Bebop)",
                                checked = featureToggles.animeThemeFontsEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(animeThemeFontsEnabled = enabled) }
                                }
                            )

                            SettingToggleItem(
                                label = "🎨 Şarkı Kapağına Göre Dinamik Renk Değişimi",
                                checked = featureToggles.dynamicCoverPaletteEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(dynamicCoverPaletteEnabled = enabled) }
                                }
                            )
                        }
                    }

                    // 2B. BERSERK - GRIFFITH (IŞIĞIN ŞAHİNİ) 4 BAĞIMSIZ ÖZELLEŞTİRME KARTI
                    item(key = "appearance_griffith_customization") {
                        SettingsGroupCard(
                            title = "🦅 Berserk - Griffith (Işığın Şahini) Özelleştirme",
                            subtitle = "Griffith Sözleri, Sabit Şahin Amblemi, Uçan Şahin ve Rastgele Replikler",
                            icon = Icons.Default.AutoAwesome,
                            testTag = "card_griffith_customization"
                        ) {
                            Text(
                                text = "Bu 4 özellik birbirinden tamamen bağımsız çalışır ve yalnızca Berserk - Griffith teması seçiliyken görünür. Ana ekran ve mini oynatıcı arka plan görselleri kalıcı olarak kaldırılmıştır.",
                                fontSize = 11.sp,
                                color = palette.onSurfaceMuted
                            )

                            SettingToggleItem(
                                label = "1. Griffith Sözleri (Üst Başlık ve Tam Ekran Oynatıcıda Estetik Kısa Sözler)",
                                checked = featureToggles.griffithQuotesEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(griffithQuotesEnabled = enabled) }
                                }
                            )

                            SettingToggleItem(
                                label = "2. Işığın Şahini (Falcon of Light) Sabit Vektörel Amblemi",
                                checked = featureToggles.griffithFalconEmblemEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(griffithFalconEmblemEnabled = enabled) }
                                }
                            )

                            SettingToggleItem(
                                label = "3. Gerçek Uçan Şahin Animasyonu (Ana Ekranda Şarkı Kartları Üzerinde Süzülür)",
                                checked = featureToggles.griffithFlyingFalconEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(griffithFlyingFalconEnabled = enabled) }
                                }
                            )

                            SettingToggleItem(
                                label = "4. Rastgele Griffith Replikleri (Şarkı Geçişlerinde ve Boş Liste Durumunda)",
                                checked = featureToggles.griffithRandomLinesEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(griffithRandomLinesEnabled = enabled) }
                                }
                            )
                        }
                    }

                    // 2C. COWBOY BEBOP — BAĞIMSIZ TEMA ÖZELLEŞTİRME SİSTEMİ (31 AYAR + ÖN AYARLAR + SIFIRLAMA)
                    item(key = "appearance_cowboy_bebop_customization") {
                        val bebopConfig by com.example.data.CowboyBebopThemeManager.configFlow.collectAsState()
                        SettingsGroupCard(
                            title = "🎷 Cowboy Bebop Özelleştirme (Faye Valentine Mor-Kehribar)",
                            subtitle = "Gece Dostu Mor-Sarı Palet, 31 Bağımsız Ayar, 3 Ön Ayar Profili",
                            icon = Icons.Default.Tune,
                            testTag = "card_cowboy_bebop_customization",
                            headerAction = {
                                OutlinedButton(
                                    onClick = {
                                        com.example.data.CowboyBebopThemeManager.resetToDefaults(context)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .testTag("cb_reset_defaults_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Bebop Sıfırla", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        ) {
                            Text(
                                text = "Hızlı Ön Ayar Profilleri (Sadece Cowboy Bebop temasını etkiler):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.primaryAccent
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                com.example.data.BebopPresetProfile.entries.forEach { preset ->
                                    OutlinedButton(
                                        onClick = {
                                            com.example.data.CowboyBebopThemeManager.applyPreset(context, preset)
                                            if (selectedTheme != AppTheme.COWBOY_BEBOP_SPIKE) {
                                                onSelectTheme(AppTheme.COWBOY_BEBOP_SPIKE)
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("cb_preset_${preset.name}")
                                    ) {
                                        Column {
                                            Text(preset.displayName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(preset.subtitle, fontSize = 9.sp, color = palette.onSurfaceMuted)
                                        }
                                    }
                                }
                            }

                            // Alt Bölüm 1: Renk ve Görünüm (Ayar 1–7)
                            Text(
                                text = "1. Renk ve Görünüm (Faye Valentine Gece Dostu Mor-Sarı)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.primaryAccent
                            )
                            SettingToggleItem(
                                label = "1. Faye Valentine Mor-Sarı Renk Paleti",
                                checked = bebopConfig.cbFayePurpleYellowPaletteEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbFayePurpleYellowPaletteEnabled = v)
                                    }
                                }
                            )
                            SettingToggleItem(
                                label = "2. Gece Dostu Düşük Parlaklık Modu",
                                checked = bebopConfig.cbNightFriendlyLowBrightnessEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbNightFriendlyLowBrightnessEnabled = v)
                                    }
                                }
                            )
                            BebopSliderRow(
                                label = "3. Mor Ton Yoğunluğu (%${(bebopConfig.cbPurpleToneIntensity * 100).toInt()})",
                                value = bebopConfig.cbPurpleToneIntensity,
                                valueRange = 0f..1f,
                                onValueChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbPurpleToneIntensity = v)
                                    }
                                }
                            )
                            BebopSliderRow(
                                label = "4. Sarı / Kehribar Vurgu Yoğunluğu (%${(bebopConfig.cbAmberYellowAccentIntensity * 100).toInt()})",
                                value = bebopConfig.cbAmberYellowAccentIntensity,
                                valueRange = 0f..1f,
                                onValueChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbAmberYellowAccentIntensity = v)
                                    }
                                }
                            )
                            BebopSliderRow(
                                label = "5. Arka Plan Koyuluk Seviyesi (%${(bebopConfig.cbBackgroundDarknessLevel * 100).toInt()})",
                                value = bebopConfig.cbBackgroundDarknessLevel,
                                valueRange = 0f..1f,
                                onValueChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbBackgroundDarknessLevel = v)
                                    }
                                }
                            )
                            BebopSliderRow(
                                label = "6. Kart Yüzey Saydamlığı (%${(bebopConfig.cbCardSurfaceOpacity * 100).toInt()})",
                                value = bebopConfig.cbCardSurfaceOpacity,
                                valueRange = 0.30f..1f,
                                onValueChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbCardSurfaceOpacity = v)
                                    }
                                }
                            )
                            SettingToggleItem(
                                label = "7. Metin Kontrast Güçlendirme",
                                checked = bebopConfig.cbTextContrastBoostEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbTextContrastBoostEnabled = v)
                                    }
                                }
                            )

                            // Alt Bölüm 2: Uzay ve Arka Plan Efektleri (Ayar 8–14)
                            Text(
                                text = "2. Uzay ve Arka Plan Efektleri (Önbellekli Statik Katman)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.primaryAccent
                            )
                            SettingToggleItem(
                                label = "8. Statik Yıldız Alanı (96 Yıldız Önbellekli Katman)",
                                checked = bebopConfig.cbStaticStarfieldEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbStaticStarfieldEnabled = v)
                                    }
                                }
                            )
                            BebopSliderRow(
                                label = "9. Yıldız Yoğunluğu (%${(bebopConfig.cbStarDensity * 100).toInt()})",
                                value = bebopConfig.cbStarDensity,
                                valueRange = 0.10f..1f,
                                onValueChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbStarDensity = v)
                                    }
                                }
                            )
                            BebopSliderRow(
                                label = "10. Yıldız Parlaklığı (%${(bebopConfig.cbStarBrightness * 100).toInt()})",
                                value = bebopConfig.cbStarBrightness,
                                valueRange = 0.10f..1f,
                                onValueChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbStarBrightness = v)
                                    }
                                }
                            )
                            SettingToggleItem(
                                label = "11. Hafif Uzay Sisi / Nebula",
                                checked = bebopConfig.cbSpaceMistNebulaEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbSpaceMistNebulaEnabled = v)
                                    }
                                }
                            )
                            BebopSliderRow(
                                label = "12. Nebula Yoğunluğu (%${(bebopConfig.cbNebulaIntensity * 100).toInt()})",
                                value = bebopConfig.cbNebulaIntensity,
                                valueRange = 0f..1f,
                                onValueChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbNebulaIntensity = v)
                                    }
                                }
                            )
                            SettingToggleItem(
                                label = "13. İnce Film Grain / Retro Toz Dokusu",
                                checked = bebopConfig.cbFilmGrainDustEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbFilmGrainDustEnabled = v)
                                    }
                                }
                            )
                            SettingToggleItem(
                                label = "14. Kenar Karartma (Vignette)",
                                checked = bebopConfig.cbVignetteEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbVignetteEnabled = v)
                                    }
                                }
                            )

                            // Alt Bölüm 3: Animasyonlar (Ayar 15–20)
                            Text(
                                text = "3. Animasyonlar (Kaydırma ve Arka Planda Otomatik Durur)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.primaryAccent
                            )
                            SettingToggleItem(
                                label = "15. Cowboy Bebop Animasyonları Ana Anahtar",
                                checked = bebopConfig.cbMasterAnimationsEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbMasterAnimationsEnabled = v)
                                    }
                                }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("16. Animasyon Yoğunluğu:", fontSize = 11.sp, color = palette.onSurface)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    com.example.data.BebopAnimationIntensity.entries.forEach { level ->
                                        FilterChip(
                                            selected = bebopConfig.cbAnimationIntensity == level,
                                            onClick = {
                                                com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                                    it.copy(cbAnimationIntensity = level)
                                                }
                                            },
                                            label = { Text(level.displayName, fontSize = 10.sp) }
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("17. Animasyon Hızı:", fontSize = 11.sp, color = palette.onSurface)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    com.example.data.BebopAnimationSpeed.entries.forEach { speed ->
                                        FilterChip(
                                            selected = bebopConfig.cbAnimationSpeed == speed,
                                            onClick = {
                                                com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                                    it.copy(cbAnimationSpeed = speed)
                                                }
                                            },
                                            label = { Text(speed.displayName, fontSize = 10.sp) }
                                        )
                                    }
                                }
                            }
                            SettingToggleItem(
                                label = "18. Hafif Yıldız Parıltı Değişimi (Kaydırırken Duraklatılır)",
                                checked = bebopConfig.cbStarTwinkleEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbStarTwinkleEnabled = v)
                                    }
                                }
                            )
                            SettingToggleItem(
                                label = "19. Yumuşak Uzay Işığı Geçişi",
                                checked = bebopConfig.cbSpaceLightTransitionEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbSpaceLightTransitionEnabled = v)
                                    }
                                }
                            )
                            SettingToggleItem(
                                label = "20. Ekran Geçişlerinde Tema Efekti",
                                checked = bebopConfig.cbScreenTransitionEffectsEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbScreenTransitionEffectsEnabled = v)
                                    }
                                }
                            )

                            // Alt Bölüm 4: Retro-Fütüristik Arayüz ve Oynatıcı (Ayar 21–27)
                            Text(
                                text = "4. Retro-Fütüristik Arayüz ve Oynatıcı Detayları",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.primaryAccent
                            )
                            SettingToggleItem(
                                label = "21. Retro HUD Çizgileri",
                                checked = bebopConfig.cbRetroHudLinesEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbRetroHudLinesEnabled = v)
                                    }
                                }
                            )
                            SettingToggleItem(
                                label = "22. İnce Kart Çerçevesi",
                                checked = bebopConfig.cbThinCardBorderEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbThinCardBorderEnabled = v)
                                    }
                                }
                            )
                            SettingToggleItem(
                                label = "23. Hafif Kart Parıltısı",
                                checked = bebopConfig.cbSubtleCardGlowEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbSubtleCardGlowEnabled = v)
                                    }
                                }
                            )
                            SettingToggleItem(
                                label = "24. Mini Oynatıcı Özel Vurgu Işığı",
                                checked = bebopConfig.cbMiniPlayerGlowHighlightEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbMiniPlayerGlowHighlightEnabled = v)
                                    }
                                }
                            )
                            SettingToggleItem(
                                label = "25. Tam Ekran Oynatıcı Uzay Atmosferi",
                                checked = bebopConfig.cbFullPlayerSpaceAtmosphereEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbFullPlayerSpaceAtmosphereEnabled = v)
                                    }
                                }
                            )
                            SettingToggleItem(
                                label = "26. İlerleme Çubuğu Özel Parıltısı",
                                checked = bebopConfig.cbSeekbarSpecialGlowEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbSeekbarSpecialGlowEnabled = v)
                                    }
                                }
                            )
                            SettingToggleItem(
                                label = "27. Oynatıcı Ses Dalgası / Görselleştirici Efekti",
                                checked = bebopConfig.cbPlayerVisualizerWaveEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbPlayerVisualizerWaveEnabled = v)
                                    }
                                }
                            )

                            // Alt Bölüm 5: Performans, Detay ve Yaşam Döngüsü (Ayar 28–31)
                            Text(
                                text = "5. Performans, Detay ve Yaşam Döngüsü",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.primaryAccent
                            )
                            SettingToggleItem(
                                label = "28. Köşe Vurgu İşaretleri",
                                checked = bebopConfig.cbCornerAccentMarksEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbCornerAccentMarksEnabled = v)
                                    }
                                }
                            )
                            SettingToggleItem(
                                label = "29. Liste Başlıklarında Özel Stil",
                                checked = bebopConfig.cbSpecialListTitleStyleEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbSpecialListTitleStyleEnabled = v)
                                    }
                                }
                            )
                            SettingToggleItem(
                                label = "30. Albüm Kapağı Çevresi Yumuşak Parıltı",
                                checked = bebopConfig.cbAlbumArtGlowFrameEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbAlbumArtGlowFrameEnabled = v)
                                    }
                                }
                            )
                            SettingToggleItem(
                                label = "31. Arka Planda Efektleri Otomatik Durdur",
                                checked = bebopConfig.cbPauseEffectsInBackgroundEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbPauseEffectsInBackgroundEnabled = v)
                                    }
                                }
                            )
                            SettingToggleItem(
                                label = "32. Düşük Efekt / Pil Dostu Mod",
                                checked = bebopConfig.cbLowEffectModeEnabled,
                                onCheckedChange = { v ->
                                    com.example.data.CowboyBebopThemeManager.updateConfig(context) {
                                        it.copy(cbLowEffectModeEnabled = v)
                                    }
                                }
                            )
                        }
                    }

                    // 3. SAKURA ANİMASYON & DOKUNUŞ SİSTEMİ KARTI
                    item(key = "appearance_animations") {
                        val touchBurstStatusText = if (animationConfig.interactiveBurstEnabled) "Açık" else "Kapalı"
                        SettingsGroupCard(
                            title = "✨ Sakura Animasyon & Dokunuş Efektleri",
                            subtitle = if (animationConfig.masterEnabled) {
                                "${animationConfig.profile.displayName} • Dokunma Çiçekleri: $touchBurstStatusText"
                            } else "Tüm Efektler Kapalı",
                            icon = Icons.Default.AutoAwesome,
                            testTag = "section_animations"
                        ) {
                            SettingToggleItem(
                                label = "Tüm Sakura Animasyonlarını Etkinleştir (Ana Şalter)",
                                checked = animationConfig.masterEnabled,
                                onCheckedChange = onSetAnimationsMasterEnabled
                            )

                            SettingToggleItem(
                                label = "Dokunmada Kiraz Çiçeği Açma (Touch Burst)",
                                checked = animationConfig.interactiveBurstEnabled,
                                onCheckedChange = onSetInteractiveBurstEnabled
                            )

                            Text(
                                text = "Performans & Yoğunluk Profili:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.onSurface
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SakuraAnimationProfile.entries.forEach { profile ->
                                    FilterChip(
                                        selected = animationConfig.profile == profile,
                                        onClick = { onSelectAnimationProfile(profile) },
                                        label = { Text(profile.displayName, fontSize = 11.sp) }
                                    )
                                }
                            }

                            Text(
                                text = "Yaprak Yoğunluğu:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.onSurface
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PetalDensityLevel.entries.forEach { d ->
                                    FilterChip(
                                        selected = animationConfig.petalDensity == d,
                                        onClick = { onSelectPetalDensity(d) },
                                        label = { Text(d.displayName, fontSize = 11.sp) }
                                    )
                                }
                            }

                            Text(
                                text = "Geçiş Hızı & Efekt Şiddeti:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.onSurface
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TransitionSpeedLevel.entries.forEach { s ->
                                    FilterChip(
                                        selected = animationConfig.transitionSpeed == s,
                                        onClick = { onSelectTransitionSpeed(s) },
                                        label = { Text(s.displayName, fontSize = 11.sp) }
                                    )
                                }
                                EffectIntensityLevel.entries.forEach { i ->
                                    FilterChip(
                                        selected = animationConfig.effectIntensity == i,
                                        onClick = { onSelectEffectIntensity(i) },
                                        label = { Text(i.displayName, fontSize = 11.sp) }
                                    )
                                }
                            }

                            SettingToggleItem(
                                label = "Süzülen Kiraz Çiçeği Yaprakları",
                                checked = animationConfig.floatingPetalsEnabled,
                                onCheckedChange = onSetFloatingPetalsEnabled
                            )
                            SettingToggleItem(
                                label = "Ses Reaktif Halo & Ritim Dalgaları",
                                checked = animationConfig.audioReactiveWavesEnabled,
                                onCheckedChange = onSetAudioReactiveWavesEnabled
                            )
                            SettingToggleItem(
                                label = "Akıcı Ekran Geçiş Animasyonları",
                                checked = animationConfig.screenTransitionsEnabled,
                                onCheckedChange = onSetScreenTransitionsEnabled
                            )
                            SettingToggleItem(
                                label = "Pil Tasarrufunda Otomatik Azalt",
                                checked = animationConfig.powerSaveAutoReduce,
                                onCheckedChange = onSetPowerSaveAutoReduce
                            )
                        }
                    }
                }

                // =====================================================================
                // KATEGORİ 2: SES (Stüdyo DSP Modülleri & +20 dB Ses Yükseltici)
                // =====================================================================
                SettingsCategory.AUDIO -> {
                    item(key = "audio_studio_dsp") {
                        SettingsGroupCard(
                            title = "🎚️ Stüdyo & DSP Ses Efekt Modülleri",
                            subtitle = "5-Bant Ekolayzer, Slowed+Reverb, 8D Uzamsal Ses, Karaoke & Ambiyans",
                            icon = Icons.Default.GraphicEq,
                            testTag = "section_studio_dsp"
                        ) {
                            SettingToggleItem(
                                label = "5-Bant Grafik Ekolayzer & Bass Boost Modülü",
                                checked = featureToggles.equalizerEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(equalizerEnabled = enabled) }
                                }
                            )
                            if (featureToggles.equalizerEnabled) {
                                SettingToggleItem(
                                    label = "   ↳ Ekolayzer Efektini Aktif Et (${studioDspConfig.eqPresetName})",
                                    checked = studioDspConfig.isEqualizerActive,
                                    onCheckedChange = { active ->
                                        onUpdateStudioDspConfig { it.copy(isEqualizerActive = active) }
                                    }
                                )
                            }

                            SettingToggleItem(
                                label = "Slowed + Reverb & Nightcore (Pitch/Tempo Modülü)",
                                checked = featureToggles.pitchAndReverbEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(pitchAndReverbEnabled = enabled) }
                                }
                            )
                            if (featureToggles.pitchAndReverbEnabled) {
                                SettingToggleItem(
                                    label = "   ↳ Slowed + Reverb / Nightcore Aktif Et (${studioDspConfig.pitchReverbPreset})",
                                    checked = studioDspConfig.isPitchReverbActive,
                                    onCheckedChange = { active ->
                                        onUpdateStudioDspConfig { it.copy(isPitchReverbActive = active) }
                                    }
                                )
                            }

                            SettingToggleItem(
                                label = "8D / 360° Uzamsal Ses (Spatial Panning Modülü)",
                                checked = featureToggles.spatial8DAudioEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(spatial8DAudioEnabled = enabled) }
                                }
                            )
                            if (featureToggles.spatial8DAudioEnabled) {
                                SettingToggleItem(
                                    label = "   ↳ 8D 360° Dönen Sahne Efektini Aktif Et",
                                    checked = studioDspConfig.isSpatial8DActive,
                                    onCheckedChange = { active ->
                                        onUpdateStudioDspConfig { it.copy(isSpatial8DActive = active) }
                                    }
                                )
                            }

                            SettingToggleItem(
                                label = "Karaoke / Vokal Azaltıcı Mod (Enstrümantal Odak)",
                                checked = featureToggles.vocalReducerEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(vocalReducerEnabled = enabled) }
                                }
                            )
                            if (featureToggles.vocalReducerEnabled) {
                                SettingToggleItem(
                                    label = "   ↳ Karaoke / Vokal Baskılama Aktif Et (%${studioDspConfig.vocalReductionPercent})",
                                    checked = studioDspConfig.isVocalReducerActive,
                                    onCheckedChange = { active ->
                                        onUpdateStudioDspConfig { it.copy(isVocalReducerActive = active) }
                                    }
                                )
                            }

                            SettingToggleItem(
                                label = "Arka Plan Ambiyans & Plak Çıtırtısı Mikseri",
                                checked = featureToggles.ambientMixerEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(ambientMixerEnabled = enabled) }
                                }
                            )
                            if (featureToggles.ambientMixerEnabled) {
                                SettingToggleItem(
                                    label = "   ↳ Yağmur / Plak Çıtırtısı Mikserini Aktif Et",
                                    checked = studioDspConfig.isAmbientMixerActive,
                                    onCheckedChange = { active ->
                                        onUpdateStudioDspConfig { it.copy(isAmbientMixerActive = active) }
                                    }
                                )
                            }

                            Button(
                                onClick = { showStudioDspDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_studio_dsp_dialog_button")
                            ) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Stüdyo FX & Detaylı DSP Kontrol Panelini Aç", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    item(key = "audio_volume_booster") {
                        SettingsGroupCard(
                            title = "🔊 Şarkı Ses Seviyesi Yükseltici (+20 dB)",
                            subtitle = if (isVolumeBoosterEnabled) "Aktif: %$volumeBoostPercent Amplifikasyon" else "Kapalı (%100 Standart Ses)",
                            icon = Icons.Default.VolumeUp,
                            testTag = "section_volume_booster"
                        ) {
                            Text(
                                text = "Kulaklık ve hoparlörlerde ses seviyesini standart %100 sınırının %300 üzerine kadar donanım DSP + yazılım ön-kazanç (pre-gain) ile yükseltir.",
                                fontSize = 11.sp,
                                color = palette.onSurfaceMuted
                            )

                            SettingToggleItem(
                                label = "Ses Yükselticiyi Etkinleştir",
                                checked = isVolumeBoosterEnabled,
                                onCheckedChange = { enabled ->
                                    onSetVolumeBoost(volumeBoostPercent, enabled)
                                }
                            )

                            if (isVolumeBoosterEnabled) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Amplifikasyon Seviyesi:",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.onSurface
                                    )
                                    Text(
                                        text = "%$volumeBoostPercent (+${(volumeBoostPercent - 100) / 10} dB)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.primaryAccent
                                    )
                                }

                                Slider(
                                    value = volumeBoostPercent.toFloat(),
                                    onValueChange = { newPct ->
                                        onSetVolumeBoost(newPct.toInt(), true)
                                    },
                                    valueRange = 100f..300f,
                                    steps = 19,
                                    colors = SliderDefaults.colors(
                                        thumbColor = palette.primaryAccent,
                                        activeTrackColor = palette.primaryAccent,
                                        inactiveTrackColor = palette.surfaceVariant
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    listOf(100, 150, 200, 250, 300).forEach { preset ->
                                        val isSelected = volumeBoostPercent == preset
                                        Button(
                                            onClick = { onSetVolumeBoost(preset, true) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) palette.primaryAccent else palette.surface,
                                                contentColor = if (isSelected) palette.onPrimary else palette.onSurface
                                            ),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("%$preset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // =====================================================================
                // KATEGORİ 3: OYNATICI (Kapak Sahneleri, Şarkı Sözleri & Akıllı Araçlar)
                // =====================================================================
                SettingsCategory.PLAYER -> {
                    item(key = "player_visuals_card") {
                        SettingsGroupCard(
                            title = "💿 Şarkı Kapağı Sahneleri & Oynatıcı Kontrolleri",
                            subtitle = "Seçili Sahne: ${featureToggles.selectedPlayerScene.displayName}",
                            icon = Icons.Default.Album,
                            testTag = "section_player_visuals"
                        ) {
                            SettingToggleItem(
                                label = "Şarkı Kapağı Arkası Görsel Sahneleri (Aç / Kapa)",
                                checked = featureToggles.playerScenesEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(playerScenesEnabled = enabled) }
                                }
                            )

                            if (featureToggles.playerScenesEnabled) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(palette.surface)
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Tam Ekran Şarkı Kapağı Sahnesi Seçin:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.primaryAccent
                                    )
                                    NowPlayingSceneMode.entries.forEach { scene ->
                                        val isSelected = featureToggles.selectedPlayerScene == scene
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isSelected) palette.primaryAccent.copy(alpha = 0.16f)
                                                    else palette.surfaceContainer
                                                )
                                                .border(
                                                    width = if (isSelected) 1.5.dp else 1.dp,
                                                    color = if (isSelected) palette.primaryAccent else palette.dividerOrBorder,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .clickable {
                                                    onUpdateFeatureToggles { it.copy(selectedPlayerScene = scene) }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = scene.displayName,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = palette.onSurface
                                                )
                                                Text(
                                                    text = scene.description,
                                                    fontSize = 10.sp,
                                                    color = palette.onSurfaceMuted
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Seçili",
                                                    tint = palette.primaryAccent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            SettingToggleItem(
                                label = "İnteraktif Canlı Ses Dalgası Çubuğu (Waveform Seekbar)",
                                checked = featureToggles.waveformSeekbarEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(waveformSeekbarEnabled = enabled) }
                                }
                            )

                            SettingToggleItem(
                                label = "Şarkı Kapağına Çift Dokunarak ±10 Sn İleri/Geri Sarma",
                                checked = featureToggles.doubleTapSeekEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(doubleTapSeekEnabled = enabled) }
                                }
                            )

                            SettingToggleItem(
                                label = "A-B Nakarat Döngüsü (Bölüm Tekrarlama Modülü)",
                                checked = featureToggles.abLoopEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(abLoopEnabled = enabled) }
                                }
                            )
                        }
                    }

                    item(key = "player_lyrics_translation") {
                        SettingsGroupCard(
                            title = "📝 5 Kaynaklı Şarkı Sözü & Türkçe Çeviri Motoru",
                            subtitle = "Çeviri: ${translationEngine.displayName}",
                            icon = Icons.Default.Translate,
                            testTag = "section_translation",
                            headerAction = {
                                TextButton(
                                    onClick = onResetLyricsSourceOrder,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Sırayı Sıfırla",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.primaryAccent
                                    )
                                }
                            }
                        ) {
                            // Tüm Şarkılara Söz Bul & Çevir Butonu (İndirme Motorundan Bağımsız Arka Plan İş Parçacığı)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.surface)
                                    .border(1.dp, palette.primaryAccent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (isBatchFetchingLyrics) {
                                        "Arka planda taranıyor ve Türkçeye çevriliyor: $batchLyricsCompletedCount / $batchLyricsTotalCount"
                                    } else {
                                        "Kitaplıktaki sözü olmayan tüm şarkıları (LRCLIB & YT Music) tarar ve Türkçeye çevirip .LRC kaydeder:"
                                    },
                                    fontSize = 11.sp,
                                    color = palette.onSurface
                                )

                                Button(
                                    onClick = onBatchFetchAndTranslateAllLyrics,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isBatchFetchingLyrics) palette.tertiaryAccent else palette.primaryAccent,
                                        contentColor = palette.onPrimary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_batch_lyrics_translate_button")
                                ) {
                                    Icon(
                                        imageVector = if (isBatchFetchingLyrics) Icons.Default.Close else Icons.Default.Translate,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isBatchFetchingLyrics) "Söz Tarama & Çeviriyi Durdur" else "Tüm Şarkılara Söz Bul & Çevir",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (isBatchFetchingLyrics && batchLyricsTotalCount > 0) {
                                    val progress = (batchLyricsCompletedCount.toFloat() / batchLyricsTotalCount.toFloat()).coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = palette.primaryAccent,
                                        trackColor = palette.surfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = "En üstteki kaynaktan aramaya başlar; bulamazsa sırayla alttakine geçer ve bulduğu sözü otomatik .LRC olarak kaydeder.",
                                fontSize = 11.sp,
                                color = palette.onSurfaceMuted
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                lyricsSourceOrder.forEachIndexed { index, source ->
                                    val isEnabled = source in enabledLyricsSources
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isEnabled) palette.surface
                                                else palette.surface.copy(alpha = 0.5f)
                                            )
                                            .border(
                                                width = if (index == 0 && isEnabled) 1.5.dp else 1.dp,
                                                color = if (index == 0 && isEnabled) palette.primaryAccent else palette.dividerOrBorder,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isEnabled) palette.primaryAccent.copy(alpha = 0.22f)
                                                    else palette.surfaceVariant
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isEnabled) palette.primaryAccent else palette.onSurfaceMuted
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = source.displayName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isEnabled) palette.onSurface else palette.onSurfaceMuted
                                            )
                                            Text(
                                                text = source.subtitle,
                                                fontSize = 10.sp,
                                                color = palette.onSurfaceMuted
                                            )
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        if (index > 0) palette.primaryAccent.copy(alpha = 0.16f)
                                                        else Color.Transparent
                                                    )
                                                    .clickable(enabled = index > 0) { onMoveLyricsSourceUp(source) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ExpandLess,
                                                    contentDescription = "Yukarı Taşı",
                                                    tint = if (index > 0) palette.primaryAccent else palette.onSurfaceMuted.copy(alpha = 0.35f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        if (index < lyricsSourceOrder.lastIndex) palette.primaryAccent.copy(alpha = 0.16f)
                                                        else Color.Transparent
                                                    )
                                                    .clickable(enabled = index < lyricsSourceOrder.lastIndex) {
                                                        onMoveLyricsSourceDown(source)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ExpandMore,
                                                    contentDescription = "Aşağı Taşı",
                                                    tint = if (index < lyricsSourceOrder.lastIndex) palette.primaryAccent else palette.onSurfaceMuted.copy(alpha = 0.35f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Switch(
                                            checked = isEnabled,
                                            onCheckedChange = { checked -> onToggleLyricsSourceEnabled(source, checked) }
                                        )
                                    }
                                }
                            }

                            SettingToggleItem(
                                label = "Şarkı Sözlerini Otomatik Türkçe'ye Çevir",
                                checked = autoTranslateLyricsToTurkish,
                                onCheckedChange = onSetAutoTranslateLyricsToTurkish
                            )

                            if (autoTranslateLyricsToTurkish) {
                                TranslationEngine.entries.forEach { engine ->
                                    val isSelected = engine == translationEngine
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) palette.primaryAccent.copy(alpha = 0.16f) else palette.surface)
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) palette.primaryAccent else palette.dividerOrBorder,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { onSelectTranslationEngine(engine) }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = engine.displayName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = palette.onSurface
                                            )
                                            Text(
                                                text = engine.subtitle,
                                                fontSize = 10.sp,
                                                color = palette.onSurfaceMuted
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Seçili",
                                                tint = palette.primaryAccent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "player_library_tools") {
                        SettingsGroupCard(
                            title = "🧰 Akıllı Kütüphane & Paylaşım Araçları",
                            subtitle = "Söz Kartı, ID3 Etiket Düzenleyici, Kütüphane Doktoru & Auto-Play",
                            icon = Icons.Default.LibraryMusic,
                            testTag = "section_library_tools"
                        ) {
                            SettingToggleItem(
                                label = "Şarkı Sözü Kartı Oluşturucu (Üst Bar Paylaşım İkonu)",
                                checked = featureToggles.lyricCardGeneratorEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(lyricCardGeneratorEnabled = enabled) }
                                }
                            )

                            SettingToggleItem(
                                label = "Çevrimdışı ID3 Etiket & Kapak Düzenleyici",
                                checked = featureToggles.tagEditorEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(tagEditorEnabled = enabled) }
                                }
                            )

                            SettingToggleItem(
                                label = "Kütüphane Doktoru (Kopya & Bozuk Dosya Temizleyici)",
                                checked = featureToggles.libraryDoctorEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(libraryDoctorEnabled = enabled) }
                                }
                            )

                            SettingToggleItem(
                                label = "Akıllı Otomatik Sıradaki Şarkı (Smart Auto-Play)",
                                checked = featureToggles.smartAutoplayEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(smartAutoplayEnabled = enabled) }
                                }
                            )

                            SettingToggleItem(
                                label = "Müzik Dinleme İstatistikleri & Özet (Wrapped)",
                                checked = featureToggles.musicStatsEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateFeatureToggles { it.copy(musicStatsEnabled = enabled) }
                                }
                            )
                        }
                    }
                }

                // =====================================================================
                // KATEGORİ 4: SİSTEM (İndirme Formatı, Wi-Fi VLLM Oto-Senkronizasyonu, İkon, yt-dlp)
                // =====================================================================
                SettingsCategory.SYSTEM -> {
                    item(key = "system_download_format") {
                        SettingsGroupCard(
                            title = "📥 Varsayılan İndirme Türü & Format Tercihi",
                            subtitle = "Seçili: ${selectedDownloadFormat.displayName}",
                            icon = Icons.Default.Verified,
                            testTag = "section_system_and_engine"
                        ) {
                            DownloadFormatOption.entries.forEach { formatOpt ->
                                val isSelected = formatOpt == selectedDownloadFormat
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) palette.primaryAccent.copy(alpha = 0.16f)
                                            else palette.surface
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) palette.primaryAccent else palette.dividerOrBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onSelectDownloadFormat(formatOpt) }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = formatOpt.displayName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = palette.onSurface
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        if (formatOpt.isAudioExtraction) palette.primaryAccent.copy(alpha = 0.2f)
                                                        else palette.secondaryAccent.copy(alpha = 0.2f)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (formatOpt.isAudioExtraction) "Sadece Ses" else "Video + Ses",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (formatOpt.isAudioExtraction) palette.primaryAccent else palette.secondaryAccent
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${formatOpt.description} (${formatOpt.expectedExtension.uppercase()})",
                                            fontSize = 10.sp,
                                            color = palette.onSurfaceMuted
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Seçili Format",
                                            tint = palette.primaryAccent
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "system_account_sync") {
                        SettingsGroupCard(
                            title = "☁️ YT Music Beğenilenler (VLLM) & Wi-Fi Oto-Senkronizasyon",
                            subtitle = if (isLikedSongsWifiAutoSyncEnabled) "Wi-Fi Otomatik İndirme: Aktif (WEB_REMIX • Client 67)" else "Wi-Fi Otomatik İndirme: Kapalı",
                            icon = Icons.Default.MusicNote,
                            testTag = "card_account_sync"
                        ) {
                            SettingToggleItem(
                                label = "YT Music Beğenilenlerim (VLLM) Wi-Fi Bağlandığında Otomatik İndir",
                                checked = isLikedSongsWifiAutoSyncEnabled,
                                onCheckedChange = onSetLikedSongsWifiAutoSyncEnabled
                            )

                            Text(
                                text = "Wi-Fi (UNMETERED) bağlı olduğunda CookieManager çerezleri ve SAPISIDHASH yetkilendirmesiyle doğrudan WEB_REMIX (Client ID: 67) üzerinden VLLM listesini tarar ve kütüphanede olmayan yeni beğendiğiniz şarkıları arka planda sessizce indirir.",
                                fontSize = 11.sp,
                                color = palette.onSurfaceMuted
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onDownloadAccountLibraryWithCookies(LikedSongsSourceType.YOUTUBE_MUSIC) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.primaryAccent)
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("YT Müzik (LM)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { onDownloadAccountLibraryWithCookies(LikedSongsSourceType.YOUTUBE_VIDEOS) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.secondaryAccent)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("YT Videolar (LL)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    item(key = "system_app_icon") {
                        SettingsGroupCard(
                            title = "📱 Uygulama Simgesi Değiştirme (Safe-Zone Optimize)",
                            subtitle = "Seçili Simge: ${selectedAppIcon.displayName}",
                            icon = Icons.Default.Palette,
                            testTag = "card_app_icons"
                        ) {
                            AppIconOption.entries.forEach { iconOpt ->
                                val isSelected = iconOpt == selectedAppIcon
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) palette.primaryAccent.copy(alpha = 0.16f)
                                            else palette.surface
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) palette.primaryAccent else palette.dividerOrBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onSelectAppIcon(iconOpt) }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val (previewBg, previewFg, previewGlyph) = remember(iconOpt) {
                                        when (iconOpt) {
                                            AppIconOption.DEFAULT -> Triple(Color(0xFF141018), Color(0xFFFF4081), "🌸")
                                            AppIconOption.NEON -> Triple(Color(0xFF060B18), Color(0xFF00F0FF), "⚡")
                                            AppIconOption.DARK -> Triple(Color(0xFF10071E), Color(0xFFD4AF37), "🌙")
                                            AppIconOption.GOLDEN -> Triple(Color(0xFF160B04), Color(0xFFFFB300), "🍂")
                                            AppIconOption.BERSERK_GUTS -> Triple(Color(0xFF0A0406), Color(0xFFFF1E27), "⚔")
                                            AppIconOption.BERSERK_GRIFFITH -> Triple(Color(0xFF081C4A), Color(0xFF70D6FF), "𓅃")
                                            AppIconOption.EVANGELION_01 -> Triple(Color(0xFF160829), Color(0xFF39FF14), "01")
                                            AppIconOption.COWBOY_BEBOP -> Triple(Color(0xFF060B1E), Color(0xFFFFD700), "✦")
                                        }
                                    }
                                    val previewResId = iconOpt.previewDrawableResId
                                    val previewBitmap = remember(context, previewResId) {
                                        runCatching {
                                            val opts = android.graphics.BitmapFactory.Options().apply {
                                                inSampleSize = 2
                                                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                                            }
                                            android.graphics.BitmapFactory.decodeResource(context.resources, previewResId, opts)
                                                ?.asImageBitmap()
                                        }.getOrNull()
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(previewBg)
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) palette.primaryAccent else previewFg.copy(alpha = 0.55f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (previewBitmap != null) {
                                            Image(
                                                bitmap = previewBitmap,
                                                contentDescription = iconOpt.displayName,
                                                contentScale = ContentScale.Fit,
                                                alignment = Alignment.Center,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(10.dp))
                                            )
                                        } else {
                                            Text(
                                                text = previewGlyph,
                                                fontSize = 15.sp,
                                                color = previewFg,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = iconOpt.displayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = palette.onSurface
                                        )
                                        Text(
                                            text = iconOpt.description,
                                            fontSize = 10.sp,
                                            color = palette.onSurfaceMuted
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Seçili",
                                            tint = palette.primaryAccent
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "system_ytdlp_engine") {
                        SettingsGroupCard(
                            title = "🚀 ytdlnis / yt-dlp İndirme Motoru Durumu",
                            subtitle = "Çekirdek: $youtubeDLVersion",
                            icon = Icons.Default.Refresh,
                            testTag = "card_ytdlp_engine"
                        ) {
                            Text(
                                text = "• yt-dlp Çekirdek Sürümü: $youtubeDLVersion (${if (isYtdlpEngineReady) "Aktif" else "Devre Dışı"})\n" +
                                    "• FFmpeg Post-Processing: ${if (isFFmpegReady) "Aktif" else "Devre Dışı"}\n" +
                                    "• Aria2c Çoklu Bağlantı: ${if (isAria2cReady) "Aktif" else "Devre Dışı"}",
                                fontSize = 12.sp,
                                color = palette.onSurfaceMuted
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onUpdateYoutubeDL(false) },
                                    enabled = !isUpdatingYoutubeDL,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Kararlı Güncelle", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { onUpdateYoutubeDL(true) },
                                    enabled = !isUpdatingYoutubeDL,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Nightly Güncelle", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sakura Ağacı Gövde Formu & Çiçek Renk Stili Özelleştirme Alt Penceresi (BottomSheet).
 * Sıfır kasma (60 FPS) için ana listeden bağımsız çalışır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SakuraTreeCustomizationBottomSheet(
    selectedTreeForm: SakuraTreeForm,
    selectedTreeStyle: SakuraTreeStyle,
    onSelectTreeForm: (SakuraTreeForm) -> Unit,
    onSelectTreeStyle: (SakuraTreeStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalAppThemePalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surfaceContainer,
        contentColor = palette.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .testTag("sakura_tree_bottom_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🌸 Sakura Ağacı Özelleştirme",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.primaryAccent
                    )
                    Text(
                        text = "Gövde/Dal Çizim Mimarisi ve Özel Çiçek Renk Stili",
                        fontSize = 12.sp,
                        color = palette.onSurfaceMuted
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = palette.onSurface
                    )
                }
            }

            // 1. Sakura Ağaç Formu (Gövde & Dal Çizim Mimarisi)
            Text(
                text = "1) Sakura Ağaç Gövde Formu (4 Mimari Form):",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = palette.primaryAccent
            )

            SakuraTreeForm.entries.forEach { form ->
                val isFormSelected = form == selectedTreeForm
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isFormSelected) palette.primaryAccent.copy(alpha = 0.18f)
                            else palette.surface
                        )
                        .border(
                            width = if (isFormSelected) 1.5.dp else 1.dp,
                            color = if (isFormSelected) palette.primaryAccent else palette.dividerOrBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectTreeForm(form) }
                        .padding(12.dp)
                        .testTag("tree_form_${form.name}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = form.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = palette.onSurface
                        )
                        Text(
                            text = form.description,
                            fontSize = 11.sp,
                            color = palette.onSurfaceMuted
                        )
                    }
                    if (isFormSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Seçili",
                            tint = palette.primaryAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Sakura Ağacı Özel Renk Stili
            Text(
                text = "2) Sakura Ağacı Çiçek Renk Stili (5 Stil):",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = palette.primaryAccent
            )

            SakuraTreeStyle.entries.forEach { style ->
                val isStyleSelected = style == selectedTreeStyle
                val previewPrimary = if (style == SakuraTreeStyle.TRADITIONAL_SAKURA) palette.petalPrimary else style.petalPrimary
                val previewSecondary = if (style == SakuraTreeStyle.TRADITIONAL_SAKURA) palette.petalSecondary else style.petalSecondary
                val previewAccent = if (style == SakuraTreeStyle.TRADITIONAL_SAKURA) palette.primaryAccent else style.primaryAccent

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isStyleSelected) palette.primaryAccent.copy(alpha = 0.18f)
                            else palette.surface
                        )
                        .border(
                            width = if (isStyleSelected) 1.5.dp else 1.dp,
                            color = if (isStyleSelected) palette.primaryAccent else palette.dividerOrBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectTreeStyle(style) }
                        .padding(12.dp)
                        .testTag("tree_style_${style.name}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(previewPrimary)
                                .border(1.dp, style.barkMidColor, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(previewSecondary)
                        )
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(previewAccent)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = style.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = palette.onSurface
                        )
                        Text(
                            text = style.description,
                            fontSize = 11.sp,
                            color = palette.onSurfaceMuted
                        )
                    }
                    if (isStyleSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Seçili",
                            tint = palette.primaryAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tamam", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalAppThemePalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surfaceContainer)
            .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.primaryAccent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = palette.primaryAccent,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = palette.onSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (headerAction != null) {
                Spacer(modifier = Modifier.width(8.dp))
                headerAction()
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun SettingToggleItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalAppThemePalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface.copy(alpha = 0.65f))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = palette.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BebopSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val palette = LocalAppThemePalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface.copy(alpha = 0.65f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = palette.onSurface
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = palette.primaryAccent,
                activeTrackColor = palette.primaryAccent,
                inactiveTrackColor = palette.surfaceVariant
            )
        )
    }
}
