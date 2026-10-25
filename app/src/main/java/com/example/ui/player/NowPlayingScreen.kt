package com.example.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.FeatureTogglesConfig
import com.example.data.NowPlayingSceneMode
import com.example.data.UserPlaylist
import com.example.downloader.DownloadProgress
import com.example.downloader.MediaTrackInfo
import com.example.downloader.SavedMediaResult
import com.example.ui.theme.getCustomFontFamily
import com.example.model.LrcParser
import com.example.model.LyricLine
import com.example.player.PlayerState
import com.example.player.RepeatMode
import com.example.player.StudioDspConfig
import com.example.ui.animation.LocalSakuraAnimationState
import com.example.ui.components.ChunkedDownloaderCard
import com.example.ui.components.FloatingSakuraPetalsOverlay
import com.example.ui.components.SakuraFlowerEmblem
import com.example.ui.theme.AnimeArtRegistry
import com.example.ui.theme.AppTheme
import com.example.ui.theme.DynamicSakuraPaletteExtractor
import com.example.ui.theme.LocalAppThemePalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class NowPlayingSubTab(val title: String) {
    COVER("Kapak"),
    LYRICS("Şarkı Sözleri"),
    QUEUE("Sıradakiler")
}

@Composable
fun NowPlayingScreen(
    track: MediaTrackInfo?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    offlinePlayerState: PlayerState,
    fftMagnitudes: FloatArray,
    lyrics: List<LyricLine>,
    activeLyricIndex: Int,
    rawLrcText: String,
    isFetchingLyrics: Boolean,
    downloadProgress: DownloadProgress,
    playlists: List<UserPlaylist>,
    lastSavedMedia: SavedMediaResult?,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    volumeBoostPercent: Int = 100,
    isVolumeBoosterEnabled: Boolean = false,
    featureToggles: FeatureTogglesConfig = FeatureTogglesConfig(),
    studioDspConfig: StudioDspConfig = StudioDspConfig(),
    baseTheme: AppTheme = AppTheme.SAKURA_DARK,
    abLoopPointAMs: Long? = null,
    abLoopPointBMs: Long? = null,
    onSetAbLoopA: () -> Unit = {},
    onSetAbLoopB: () -> Unit = {},
    onClearAbLoop: () -> Unit = {},
    onUpdateFeatureToggles: ((FeatureTogglesConfig) -> FeatureTogglesConfig) -> Unit = {},
    onUpdateStudioDspConfig: ((StudioDspConfig) -> StudioDspConfig) -> Unit = {},
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onSetVolumeBoost: (Int, Boolean) -> Unit = { _, _ -> },
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onUpdateRawLrc: (String) -> Unit,
    activeLyricsSourceLabel: String? = null,
    onRefetchLyrics: (() -> Unit)? = null,
    onAddTrackToPlaylist: (String, SavedMediaResult) -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    val resolvedArtworkSource = remember(lastSavedMedia, track?.thumbnailUrl) {
        lastSavedMedia?.coverFile?.takeIf { it.exists() }?.absolutePath
            ?: track?.thumbnailUrl?.takeIf { it.isNotBlank() }
    }

    CompositionLocalProvider(LocalAppThemePalette provides palette) {
    val animState = LocalSakuraAnimationState.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var activeTab by remember { mutableStateOf(NowPlayingSubTab.COVER) }
    var tabTransitionForward by remember { mutableStateOf(true) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showVolumeBoostDialog by remember { mutableStateOf(false) }
    var showStudioDspDialog by remember { mutableStateOf(false) }
    var showLyricCardDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showDownloaderDialog by remember { mutableStateOf(false) }

    var doubleTapFeedbackDirection by remember { mutableStateOf<Int?>(null) }
    var doubleTapTriggerKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(doubleTapTriggerKey) {
        if (doubleTapFeedbackDirection != null) {
            delay(650L)
            doubleTapFeedbackDirection = null
        }
    }

    val dragOffsetY = remember { Animatable(0f) }
    val collapseThresholdPx = with(density) { 130.dp.toPx() }

    fun switchTab(newTab: NowPlayingSubTab) {
        if (newTab != activeTab) {
            tabTransitionForward = newTab.ordinal > activeTab.ordinal
            activeTab = newTab
        }
    }

    fun nextTab() {
        val nextIdx = (activeTab.ordinal + 1) % NowPlayingSubTab.entries.size
        switchTab(NowPlayingSubTab.entries[nextIdx])
    }

    fun prevTab() {
        val prevIdx = if (activeTab.ordinal - 1 < 0) NowPlayingSubTab.entries.size - 1 else activeTab.ordinal - 1
        switchTab(NowPlayingSubTab.entries[prevIdx])
    }

    val displayTitle = track?.title ?: "Şarkı Seçilmedi"
    val displayArtist = track?.artist ?: "Sakura Music"
    val safeDuration = durationMs.coerceAtLeast(1L)
    val latestPositionMs by rememberUpdatedState(currentPositionMs)
    val latestSafeDuration by rememberUpdatedState(safeDuration)
    val latestOnSeekTo by rememberUpdatedState(onSeekTo)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val currentDragY = dragOffsetY.value
                translationY = currentDragY
                val dragProgress = (currentDragY / (collapseThresholdPx * 3f)).coerceIn(0f, 1f)
                val dynamicScale = 1f - (dragProgress * 0.05f)
                scaleX = dynamicScale
                scaleY = dynamicScale
                if (currentDragY > 2f) {
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    clip = true
                }
            }
            .background(
                when {
                    palette.theme == com.example.ui.theme.AppTheme.BERSERK_GUTS -> Brush.verticalGradient(
                        colors = listOf(Color(0xFF000000), Color(0xFF060003), Color(0xFF000000))
                    )
                    palette.theme == com.example.ui.theme.AppTheme.COWBOY_BEBOP_SPIKE -> Brush.verticalGradient(
                        colors = listOf(palette.surface, palette.background, Color(0xFF05060A))
                    )
                    palette.theme == com.example.ui.theme.AppTheme.SADE_TEMA -> Brush.verticalGradient(
                        colors = listOf(palette.background, palette.background)
                    )
                    palette.theme == com.example.ui.theme.AppTheme.BERSERK_GRIFFITH ||
                        with(com.example.ui.theme.AppTheme) {
                            palette.theme.let {
                                it == com.example.ui.theme.AppTheme.YT_MUSIC_PINK ||
                                    it == com.example.ui.theme.AppTheme.AMOLED_NIGHT ||
                                    it == com.example.ui.theme.AppTheme.EMERALD_DEPTH ||
                                    it == com.example.ui.theme.AppTheme.SAPPHIRE_OCEAN ||
                                    it == com.example.ui.theme.AppTheme.CRIMSON_VELVET ||
                                    it == com.example.ui.theme.AppTheme.ROYAL_AMETHYST
                            }
                        } -> Brush.verticalGradient(
                        colors = listOf(palette.surface, palette.background, Color(0xFF000000))
                    )
                    else -> Brush.verticalGradient(
                        colors = listOf(palette.surface, palette.background, palette.background)
                    )
                }
            )
    ) {
        val sadeConfig = com.example.data.LocalCustomMinimalThemeConfig.current
        val bebopConfig = com.example.ui.theme.LocalCowboyBebopConfig.current
        val isBerserkGriffith = palette.theme == com.example.ui.theme.AppTheme.BERSERK_GRIFFITH
        if (!sadeConfig.playerWallpaperPath.isNullOrBlank() && !isBerserkGriffith) {
            AsyncImage(
                model = java.io.File(sadeConfig.playerWallpaperPath),
                contentDescription = "Özel Müzik Çalar Duvar Kağıdı",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = sadeConfig.playerWallpaperOpacity.coerceIn(0f, 1f)
                    }
            )
        }

        val isBerserkGuts = palette.theme == com.example.ui.theme.AppTheme.BERSERK_GUTS
        val isEvangelion = false
        val isCowboyFaye = false
        val isCowboySpike = palette.theme == com.example.ui.theme.AppTheme.COWBOY_BEBOP_SPIKE

        val behelitSwingAngle = if (isBerserkGuts) {
            val npInfiniteTransition = rememberInfiniteTransition(label = "now_playing_anime_fx")
            val anim by npInfiniteTransition.animateFloat(
                initialValue = -18f,
                targetValue = 18f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "np_behelit_swing"
            )
            anim
        } else 0f
        // Evangelion Unit-01: Çok düşük opaklıklı (0.07f), göz yormayan sabit taktik CRT ızgara
        if (isEvangelion) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridSpacing = 32.dp.toPx()
                val gridColor = Color(0xFFFF2A85).copy(alpha = 0.07f)
                val stroke = 1.dp.toPx()
                var curX = 0f
                while (curX <= size.width) {
                    drawLine(
                        color = gridColor,
                        start = Offset(curX, 0f),
                        end = Offset(curX, size.height),
                        strokeWidth = stroke
                    )
                    curX += gridSpacing
                }
                var curY = 0f
                while (curY <= size.height) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, curY),
                        end = Offset(size.width, curY),
                        strokeWidth = stroke
                    )
                    curY += gridSpacing
                }
            }
        }

        val animeMeta = remember(baseTheme) { AnimeArtRegistry.getMetadata(baseTheme) }
        val customFontFamily = remember(baseTheme, featureToggles.animeThemeFontsEnabled, sadeConfig.customFontPath) {
            baseTheme.getCustomFontFamily(featureToggles.animeThemeFontsEnabled)
        }
        if (featureToggles.animeArtworkBackgroundEnabled && animeMeta != null && !isCowboySpike) {
            if (animeMeta.systemVectorDrawableRes != null) {
                Image(
                    painter = painterResource(id = animeMeta.systemVectorDrawableRes),
                    contentDescription = animeMeta.characterName,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.65f },
                    contentScale = ContentScale.Crop
                )
            } else if (animeMeta.primaryAssetPath != null) {
                AsyncImage(
                    model = animeMeta.primaryAssetPath,
                    contentDescription = animeMeta.characterName,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.52f },
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                palette.background.copy(alpha = 0.25f),
                                palette.background.copy(alpha = 0.55f),
                                palette.background.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
        }

        if (palette.theme != com.example.ui.theme.AppTheme.SADE_TEMA &&
            !isBerserkGriffith &&
            (!isCowboySpike || bebopConfig.cbFullPlayerSpaceAtmosphereEnabled)
        ) {
            FloatingSakuraPetalsOverlay(
                petalCount = animState.config.effectivePlayerPetalCount(animState.isSystemPowerSaveMode),
                isLoading = isFetchingLyrics || durationMs <= 0L || currentPositionMs <= 300L
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag Down Bar Indicator & Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (dragOffsetY.value > collapseThresholdPx) {
                                        onCollapse()
                                    } else {
                                        dragOffsetY.animateTo(0f, spring())
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch { dragOffsetY.animateTo(0f, spring()) }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val newY = (dragOffsetY.value + dragAmount.y).coerceAtLeast(0f)
                                scope.launch { dragOffsetY.snapTo(newY) }
                            }
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(palette.onSurfaceMuted.copy(alpha = 0.4f))
                        .padding(bottom = 6.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Daralt",
                            tint = palette.onSurface,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isBerserkGriffith && featureToggles.griffithFalconEmblemEnabled) {
                            com.example.ui.components.FalconOfLightEmblemCanvas(
                                size = 22.dp,
                                primaryColor = palette.primaryAccent,
                                secondaryColor = palette.secondaryAccent,
                                modifier = Modifier.testTag("griffith_falcon_emblem_player")
                            )
                        } else {
                            SakuraFlowerEmblem(size = 20.dp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (animeMeta != null) animeMeta.japaneseTitle else "ŞİMDİ ÇALIYOR",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFontFamily,
                            color = palette.primaryAccent
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (featureToggles.lyricCardGeneratorEnabled) {
                            IconButton(onClick = { showLyricCardDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Söz Kartı Oluştur & Paylaş",
                                    tint = palette.primaryAccent
                                )
                            }
                        }
                        IconButton(onClick = {
                            onStartDownload()
                            showDownloaderDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Şarkıyı İndir",
                                tint = palette.primaryAccent
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // SubTabs Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surfaceContainer)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NowPlayingSubTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) palette.primaryAccent else Color.Transparent)
                            .clickable { switchTab(tab) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) palette.onPrimary else palette.onSurfaceMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Content Area with Animated Content Transitions & Drag/Swipe Gestures
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        if (tabTransitionForward) {
                            (slideInHorizontally(animationSpec = tween(260)) { width -> width } + fadeIn(animationSpec = tween(260)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(260)) { width -> -width } + fadeOut(animationSpec = tween(260)))
                        } else {
                            (slideInHorizontally(animationSpec = tween(260)) { width -> -width } + fadeIn(animationSpec = tween(260)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(260)) { width -> width } + fadeOut(animationSpec = tween(260)))
                        }
                    },
                    label = "now_playing_tab_anim"
                ) { tab ->
                    when (tab) {
                        NowPlayingSubTab.COVER -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        var totalX = 0f
                                        var totalY = 0f
                                        detectDragGestures(
                                            onDragStart = {
                                                totalX = 0f
                                                totalY = 0f
                                            },
                                            onDragEnd = {
                                                scope.launch {
                                                    if (dragOffsetY.value > collapseThresholdPx) {
                                                        onCollapse()
                                                    } else {
                                                        dragOffsetY.animateTo(0f, spring())
                                                    }
                                                }
                                                if (totalX < -65f) {
                                                    nextTab()
                                                } else if (totalX > 65f) {
                                                    prevTab()
                                                }
                                            },
                                            onDragCancel = {
                                                scope.launch { dragOffsetY.animateTo(0f, spring()) }
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                totalX += dragAmount.x
                                                totalY += dragAmount.y
                                                if (totalY > 0f && kotlin.math.abs(totalY) > kotlin.math.abs(totalX)) {
                                                    val newY = (dragOffsetY.value + dragAmount.y).coerceAtLeast(0f)
                                                    scope.launch { dragOffsetY.snapTo(newY) }
                                                }
                                            }
                                        )
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val effectiveScene = if (featureToggles.playerScenesEnabled) {
                                    featureToggles.selectedPlayerScene
                                } else {
                                    NowPlayingSceneMode.DEFAULT_CLASSIC
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .fillMaxWidth()
                                        .height(308.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (effectiveScene != NowPlayingSceneMode.DEFAULT_CLASSIC) {
                                        NowPlayingStageVisual(
                                            sceneMode = effectiveScene,
                                            isPlaying = isPlaying,
                                            fftMagnitudes = fftMagnitudes,
                                            modifier = Modifier.size(304.dp)
                                        )
                                    }

                                    // Cowboy Bebop Spike'daki dönen plak/kaset tamamen kaldırıldı!
                                    val isRoundCover = effectiveScene == NowPlayingSceneMode.ZEN_LOTUS_RIPPLES
                                    val coverShape = when {
                                        isEvangelion -> CutCornerShape(20.dp)
                                        isCowboySpike -> RoundedCornerShape(16.dp)
                                        isRoundCover -> CircleShape
                                        else -> RoundedCornerShape(24.dp)
                                    }
                                    // Berserk dahil tüm temalarda kapak tam merkezde, simetrik boyutta durur (sola kaydırılmaz!)
                                    val coverSize = when {
                                        isBerserkGuts -> 236.dp
                                        isCowboySpike || isCowboyFaye || isEvangelion -> 224.dp
                                        effectiveScene == NowPlayingSceneMode.DEFAULT_CLASSIC -> 244.dp
                                        else -> 218.dp
                                    }

                                    // 3. COWBOY BEBOP - FAYE VALENTINE: Art Nouveau floral altın ayna çerçevesi
                                    if (isCowboyFaye) {
                                        ArtNouveauFloralGoldFrame(
                                            modifier = Modifier.size(286.dp)
                                        )
                                    }

                                    // ORTALANMIŞ ALBÜM KAPAĞI (Sola kaydırma veya dönen plak yok!)
                                    Box(
                                        modifier = Modifier
                                            .size(coverSize)
                                            .clip(coverShape)
                                            .background(palette.surfaceContainer)
                                            .border(
                                                width = when {
                                                    isEvangelion -> 3.5.dp
                                                    isCowboyFaye -> 2.2.dp
                                                    isBerserkGuts -> 2.2.dp
                                                    isCowboySpike -> if (bebopConfig.cbAlbumArtGlowFrameEnabled) 2.2.dp else 1.dp
                                                    effectiveScene == NowPlayingSceneMode.DEFAULT_CLASSIC -> 1.dp
                                                    else -> 2.dp
                                                },
                                                color = when {
                                                    isEvangelion -> Color(0xFF39FF14)
                                                    isCowboyFaye -> Color(0xFFFFD700)
                                                    isBerserkGuts -> Color(0xFFFF1E27)
                                                    isCowboySpike -> if (bebopConfig.cbAlbumArtGlowFrameEnabled) palette.primaryAccent else palette.dividerOrBorder
                                                    effectiveScene == NowPlayingSceneMode.DEFAULT_CLASSIC -> palette.primaryAccent.copy(alpha = 0.35f)
                                                    else -> palette.primaryAccent.copy(alpha = 0.65f)
                                                },
                                                shape = coverShape
                                            )
                                            .pointerInput(featureToggles.doubleTapSeekEnabled) {
                                                if (featureToggles.doubleTapSeekEnabled) {
                                                    detectTapGestures(
                                                        onDoubleTap = { offset ->
                                                            val isLeft = offset.x < size.width / 2f
                                                            val deltaMs = if (isLeft) -10_000L else 10_000L
                                                            val targetMs = (latestPositionMs + deltaMs).coerceIn(0L, latestSafeDuration)
                                                            latestOnSeekTo(targetMs)
                                                            doubleTapFeedbackDirection = if (isLeft) -1 else 1
                                                            doubleTapTriggerKey++
                                                        }
                                                    )
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!resolvedArtworkSource.isNullOrBlank()) {
                                            AsyncImage(
                                                model = resolvedArtworkSource,
                                                contentDescription = displayTitle,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = palette.primaryAccent,
                                                modifier = Modifier.size(72.dp)
                                            )
                                        }

                                        if (featureToggles.doubleTapSeekEnabled) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                DoubleTapSeekRippleOverlay(
                                                    seekDeltaSeconds = doubleTapFeedbackDirection?.let { it * 10 }
                                                )
                                            }
                                        }

                                        // 4. EVANGELION UNIT-01: Mecha zırh köşeleri & HUD nişangahları
                                        if (isEvangelion) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(coverShape)
                                            ) {
                                                Canvas(modifier = Modifier.fillMaxSize()) {
                                                    val bracketLen = 24.dp.toPx()
                                                    val bracketColor = Color(0xFF39FF14)
                                                    val hazardColor = Color(0xFFFF6D00)
                                                    val stroke = 2.4.dp.toPx()

                                                    drawLine(bracketColor, Offset(14.dp.toPx(), 6.dp.toPx()), Offset(14.dp.toPx() + bracketLen, 6.dp.toPx()), stroke)
                                                    drawLine(bracketColor, Offset(6.dp.toPx(), 14.dp.toPx()), Offset(6.dp.toPx(), 14.dp.toPx() + bracketLen), stroke)

                                                    drawLine(bracketColor, Offset(size.width - 14.dp.toPx() - bracketLen, 6.dp.toPx()), Offset(size.width - 14.dp.toPx(), 6.dp.toPx()), stroke)
                                                    drawLine(bracketColor, Offset(size.width - 6.dp.toPx(), 14.dp.toPx()), Offset(size.width - 6.dp.toPx(), 14.dp.toPx() + bracketLen), stroke)

                                                    drawLine(hazardColor, Offset(size.width / 2f - 26.dp.toPx(), size.height - 5.dp.toPx()), Offset(size.width / 2f + 26.dp.toPx(), size.height - 5.dp.toPx()), 2.8.dp.toPx())
                                                }
                                            }
                                        }
                                    }

                                    // Berserk Guts teması dışındaki temalarda kapak üzerine ekstra nesne/çerçeve eklenmez
                                    if (isBerserkGuts && featureToggles.animeCoverDecorationsEnabled) {
                                        com.example.ui.components.BerserkStageOverlayCanvas(
                                            mode = featureToggles.berserkAnimMode,
                                            isPlaying = isPlaying,
                                            fftMagnitudes = fftMagnitudes,
                                            swingAngleDeg = behelitSwingAngle,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }

                        NowPlayingSubTab.LYRICS -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        var totalX = 0f
                                        detectHorizontalDragGestures(
                                            onDragStart = { totalX = 0f },
                                            onDragEnd = {
                                                if (totalX < -65f) {
                                                    nextTab()
                                                } else if (totalX > 65f) {
                                                    prevTab()
                                                }
                                            },
                                            onDragCancel = { totalX = 0f },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                totalX += dragAmount
                                            }
                                        )
                                    }
                            ) {
                                LyricsPanel(
                                    lyrics = lyrics,
                                    activeIndex = activeLyricIndex,
                                    rawLrcText = rawLrcText,
                                    isFetchingLyrics = isFetchingLyrics,
                                    activeLyricsSourceLabel = activeLyricsSourceLabel,
                                    onRefetchLyrics = onRefetchLyrics,
                                    onSeekToLine = onSeekTo,
                                    onUpdateRawLrc = onUpdateRawLrc,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        NowPlayingSubTab.QUEUE -> {
                            val queue = offlinePlayerState.queue
                            var showFullQueue by remember { mutableStateOf(false) }
                            val displayedQueueIndices = remember(queue, offlinePlayerState.currentIndex, showFullQueue) {
                                if (showFullQueue || queue.size <= 5) {
                                    queue.indices.toList()
                                } else {
                                    val start = offlinePlayerState.currentIndex.coerceAtLeast(0)
                                    (start until (start + 5).coerceAtMost(queue.size)).toList()
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        var totalX = 0f
                                        detectHorizontalDragGestures(
                                            onDragStart = { totalX = 0f },
                                            onDragEnd = {
                                                if (totalX < -65f) {
                                                    nextTab()
                                                } else if (totalX > 65f) {
                                                    prevTab()
                                                }
                                            },
                                            onDragCancel = { totalX = 0f },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                totalX += dragAmount
                                            }
                                        )
                                    },
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(palette.surfaceContainer)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Akıllı Otomatik Sıradaki Şarkı (Smart Auto-Play)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.onSurface
                                        )
                                        Text(
                                            text = if (featureToggles.smartAutoplayEnabled) {
                                                "Açık • Sıradaki maks 5 şarkı gösteriliyor"
                                            } else {
                                                "Kapalı • Kuyruk bitince çalma durur"
                                            },
                                            fontSize = 10.sp,
                                            color = if (featureToggles.smartAutoplayEnabled) palette.primaryAccent else palette.onSurfaceMuted
                                        )
                                    }
                                    Switch(
                                        checked = featureToggles.smartAutoplayEnabled,
                                        onCheckedChange = { enabled ->
                                            onUpdateFeatureToggles { it.copy(smartAutoplayEnabled = enabled) }
                                        }
                                    )
                                }

                                if (queue.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Çevrimdışı çalma kuyruğu boş.",
                                            color = palette.onSurfaceMuted
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        itemsIndexed(
                                            items = displayedQueueIndices,
                                            key = { _, qIdx -> queue[qIdx].audioFile.absolutePath }
                                        ) { _, idx ->
                                            val item = queue[idx]
                                            val isCurrent = idx == offlinePlayerState.currentIndex
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(
                                                        if (isCurrent) palette.primaryAccent.copy(alpha = 0.2f)
                                                        else palette.surfaceContainer
                                                    )
                                                    .clickable { onSelectQueueIndex(idx) }
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${idx + 1}.",
                                                    fontWeight = FontWeight.Bold,
                                                    color = palette.primaryAccent,
                                                    modifier = Modifier.width(28.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.trackInfo.title,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = palette.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = item.trackInfo.artist,
                                                        fontSize = 12.sp,
                                                        color = palette.onSurfaceMuted,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                        if (queue.size > 5) {
                                            item(key = "queue_toggle_more") {
                                                TextButton(
                                                    onClick = { showFullQueue = !showFullQueue },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = if (showFullQueue) "Sadece 5 Şarkı Göster (Optimize Mod)"
                                                        else "Tüm Kuyruğu Göster (${queue.size} Şarkı)",
                                                        color = palette.primaryAccent,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Track Meta Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val npTitleStyle = if (isBerserkGuts) {
                    TextStyle(
                        shadow = Shadow(
                            color = Color(0xFFFF1E27),
                            offset = Offset(0f, 0f),
                            blurRadius = 20f
                        )
                    )
                } else TextStyle()
                val npArtistStyle = if (isBerserkGuts) {
                    TextStyle(
                        shadow = Shadow(
                            color = Color(0xFF991B1B),
                            offset = Offset(0f, 0f),
                            blurRadius = 14f
                        )
                    )
                } else TextStyle()

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = customFontFamily,
                        color = if (isBerserkGuts) Color(0xFFFFFFFF) else palette.onSurface,
                        style = npTitleStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = displayArtist,
                        fontSize = 14.sp,
                        fontWeight = if (isBerserkGuts) FontWeight.SemiBold else FontWeight.Normal,
                        fontFamily = customFontFamily,
                        color = if (isBerserkGuts) Color(0xFFFF1E27) else palette.onSurfaceMuted,
                        style = npArtistStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isBerserkGriffith) {
                        if (featureToggles.griffithQuotesEnabled) {
                            Spacer(modifier = Modifier.height(2.dp))
                            val griffithPlayerQuote = remember(track?.id) {
                                com.example.ui.components.GriffithThemeQuotes.quoteForKey(track?.id)
                            }
                            Text(
                                text = "“$griffithPlayerQuote”",
                                fontSize = 11.sp,
                                fontFamily = customFontFamily,
                                fontWeight = FontWeight.Medium,
                                color = palette.primaryAccent.copy(alpha = 0.92f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("griffith_player_quote")
                            )
                        }
                    } else if (animeMeta != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "“${animeMeta.quote}”",
                            fontSize = 11.sp,
                            fontFamily = customFontFamily,
                            fontWeight = FontWeight.Medium,
                            color = palette.primaryAccent.copy(alpha = 0.90f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (lastSavedMedia != null) {
                    IconButton(onClick = { showAddToPlaylistDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Listeye Ekle",
                            tint = palette.primaryAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Seek Bar: Interactive Live Waveform OR Classic Slider (Toggleable + Cowboy Bebop cbPlayerVisualizerWaveEnabled)
            val showWaveformSeekbar = featureToggles.waveformSeekbarEnabled &&
                (!isCowboySpike || bebopConfig.cbPlayerVisualizerWaveEnabled)
            if (showWaveformSeekbar) {
                InteractiveWaveformSeekbar(
                    currentPositionMs = currentPositionMs,
                    durationMs = safeDuration,
                    isPlaying = isPlaying,
                    fftMagnitudes = fftMagnitudes,
                    trackSeed = track?.id ?: displayTitle,
                    abLoopStartMs = if (featureToggles.abLoopEnabled) abLoopPointAMs else null,
                    abLoopEndMs = if (featureToggles.abLoopEnabled) abLoopPointBMs else null,
                    onSeekTo = onSeekTo
                )
            } else {
                val seekActiveColor = if (isCowboySpike && !bebopConfig.cbSeekbarSpecialGlowEnabled) {
                    palette.onSurfaceMuted
                } else {
                    palette.primaryAccent
                }
                Slider(
                    value = currentPositionMs.toFloat().coerceIn(0f, safeDuration.toFloat()),
                    onValueChange = { onSeekTo(it.toLong()) },
                    valueRange = 0f..safeDuration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = seekActiveColor,
                        activeTrackColor = seekActiveColor,
                        inactiveTrackColor = palette.surfaceVariant
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = LrcParser.formatTimestamp(currentPositionMs),
                    fontSize = 12.sp,
                    color = palette.onSurfaceMuted
                )
                if (featureToggles.doubleTapSeekEnabled) {
                    Text(
                        text = "Kapağa çift dokun: ⏪ -10sn | +10sn ⏩",
                        fontSize = 10.sp,
                        color = palette.onSurfaceMuted.copy(alpha = 0.75f)
                    )
                }
                Text(
                    text = LrcParser.formatTimestamp(durationMs),
                    fontSize = 12.sp,
                    color = palette.onSurfaceMuted
                )
            }

            // A-B Chorus Loop Control Bar (when enabled in toggles)
            if (featureToggles.abLoopEnabled) {
                Spacer(modifier = Modifier.height(6.dp))
                AbLoopControlBar(
                    pointAMs = abLoopPointAMs,
                    pointBMs = abLoopPointBMs,
                    onSetPointA = onSetAbLoopA,
                    onSetPointB = onSetAbLoopB,
                    onClearAbLoop = onClearAbLoop
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Playback Controls (Faye Valentine için butonlar kumarhane altını #FFD700)
            val mainControlColor = if (isCowboyFaye) Color(0xFFFFD700) else palette.onSurface
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Karıştır",
                        tint = when {
                            offlinePlayerState.isShuffleEnabled -> palette.primaryAccent
                            isCowboyFaye -> Color(0xFFFFD700).copy(alpha = 0.72f)
                            else -> palette.onSurfaceMuted
                        }
                    )
                }

                IconButton(onClick = onSkipPrevious) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Önceki",
                        tint = mainControlColor,
                        modifier = Modifier.size(34.dp)
                    )
                }

                AnimatedPlayPauseButton(
                    isPlaying = isPlaying,
                    onClick = onTogglePlayPause
                )

                IconButton(onClick = onSkipNext) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Sonraki",
                        tint = mainControlColor,
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        imageVector = if (offlinePlayerState.repeatMode == RepeatMode.ONE) {
                            Icons.Default.RepeatOne
                        } else {
                            Icons.Default.Repeat
                        },
                        contentDescription = "Tekrarla",
                        tint = when {
                            offlinePlayerState.repeatMode != RepeatMode.OFF -> palette.primaryAccent
                            isCowboyFaye -> Color(0xFFFFD700).copy(alpha = 0.72f)
                            else -> palette.onSurfaceMuted
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Buttons (Stüdyo FX, Volume Boost, Speed, Sleep Timer)
            val activeDspCount = listOf(
                featureToggles.equalizerEnabled && studioDspConfig.isEqualizerActive,
                featureToggles.pitchAndReverbEnabled && studioDspConfig.isPitchReverbActive,
                featureToggles.spatial8DAudioEnabled && studioDspConfig.isSpatial8DActive,
                featureToggles.vocalReducerEnabled && studioDspConfig.isVocalReducerActive,
                featureToggles.ambientMixerEnabled && studioDspConfig.isAmbientMixerActive
            ).count { it }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = { showStudioDspDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (activeDspCount > 0) palette.primaryAccent else palette.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (activeDspCount > 0) "Stüdyo FX ($activeDspCount)" else "Stüdyo FX",
                        fontSize = 11.sp,
                        fontWeight = if (activeDspCount > 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (activeDspCount > 0) palette.primaryAccent else palette.onSurface
                    )
                }

                OutlinedButton(
                    onClick = { showVolumeBoostDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isVolumeBoosterEnabled && volumeBoostPercent > 100) "+${volumeBoostPercent - 100}%" else "Ses+",
                        fontSize = 11.sp,
                        fontWeight = if (isVolumeBoosterEnabled) FontWeight.Bold else FontWeight.Normal
                    )
                }

                OutlinedButton(
                    onClick = { showSpeedDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${offlinePlayerState.playbackSpeed}x", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { showTimerDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    val sleepMs = offlinePlayerState.sleepTimerRemainingMs
                    Text(
                        text = if (sleepMs != null) LrcParser.formatTimestamp(sleepMs) else "Uyku",
                        fontSize = 11.sp
                    )
                }
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

    if (showLyricCardDialog && featureToggles.lyricCardGeneratorEnabled) {
        val cardLyrics = if (lyrics.isNotEmpty()) {
            lyrics
        } else {
            listOf(
                LyricLine(0L, displayTitle, null),
                LyricLine(5000L, displayArtist, "Sakura Music Koleksiyonu")
            )
        }
        LyricCardGeneratorDialog(
            track = track,
            lyrics = cardLyrics,
            activeLyricIndex = activeLyricIndex,
            onDisableModule = {
                onUpdateFeatureToggles { it.copy(lyricCardGeneratorEnabled = false) }
            },
            onDismiss = { showLyricCardDialog = false }
        )
    }

    // Volume Boost Dialog
    if (showVolumeBoostDialog) {
        var tempEnabled by remember { mutableStateOf(isVolumeBoosterEnabled) }
        var tempPercent by remember { mutableStateOf(volumeBoostPercent.toFloat()) }

        AlertDialog(
            onDismissRequest = { showVolumeBoostDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = palette.primaryAccent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Şarkı Ses Seviyesi Yükseltici")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DSP Gain Yükseltici",
                            fontWeight = FontWeight.Bold,
                            color = palette.onSurface
                        )
                        Switch(
                            checked = tempEnabled,
                            onCheckedChange = {
                                tempEnabled = it
                                onSetVolumeBoost(tempPercent.toInt(), tempEnabled)
                            }
                        )
                    }

                    Text(
                        text = "Ses seviyesini varsayılan %100 sınırının üzerine (%300 / +20 dB gain) çıkararak donanım tabanlı ses güçlendirmesi sağlar.",
                        fontSize = 12.sp,
                        color = palette.onSurfaceMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Yükseltme Seviyesi:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.onSurface
                        )
                        Text(
                            text = "%${tempPercent.toInt()} (+${(tempPercent.toInt() - 100) / 10} dB)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.primaryAccent
                        )
                    }

                    Slider(
                        value = tempPercent,
                        onValueChange = {
                            tempPercent = it
                            if (tempEnabled) {
                                onSetVolumeBoost(tempPercent.toInt(), true)
                            }
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
                            val isSelected = tempPercent.toInt() == preset
                            Button(
                                onClick = {
                                    tempPercent = preset.toFloat()
                                    tempEnabled = true
                                    onSetVolumeBoost(preset, true)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) palette.primaryAccent else palette.surfaceContainer,
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
            },
            confirmButton = {
                Button(onClick = {
                    onSetVolumeBoost(tempPercent.toInt(), tempEnabled)
                    showVolumeBoostDialog = false
                }) {
                    Text("Tamam")
                }
            }
        )
    }

    if (showSpeedDialog) {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Oynatma Hızı") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    speeds.forEach { spd ->
                        Button(
                            onClick = {
                                onSetPlaybackSpeed(spd)
                                showSpeedDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (offlinePlayerState.playbackSpeed == spd) {
                                    palette.primaryAccent
                                } else {
                                    palette.surfaceContainer
                                },
                                contentColor = if (offlinePlayerState.playbackSpeed == spd) {
                                    palette.onPrimary
                                } else {
                                    palette.onSurface
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${spd}x")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }

    if (showTimerDialog) {
        val options = listOf(null to "Kapalı", 15 to "15 Dakika", 30 to "30 Dakika", 60 to "60 Dakika")
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = { Text("Uyku Zamanlayıcısı") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { (mins, label) ->
                        Button(
                            onClick = {
                                onSetSleepTimer(mins)
                                showTimerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimerDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }

    if (showAddToPlaylistDialog && lastSavedMedia != null) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = { Text("Çalma Listesine Ekle") },
            text = {
                if (playlists.isEmpty()) {
                    Text("Henüz oluşturulmuş bir çalma listeniz yok.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        playlists.forEach { pl ->
                            Button(
                                onClick = {
                                    onAddTrackToPlaylist(pl.id, lastSavedMedia)
                                    showAddToPlaylistDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(pl.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddToPlaylistDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }

    if (showDownloaderDialog) {
        AlertDialog(
            onDismissRequest = { showDownloaderDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = palette.primaryAccent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Şarkı İndirici",
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                ChunkedDownloaderCard(
                    progress = downloadProgress,
                    onStartDownload = onStartDownload,
                    onCancelDownload = onCancelDownload
                )
            },
            confirmButton = {
                TextButton(onClick = { showDownloaderDialog = false }) {
                    Text("Kapat", color = palette.primaryAccent, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = palette.surfaceContainer
        )
    }
    }
}

/**
 * Step 5: Art Nouveau luxury floral gold vector border for Cowboy Bebop - Faye Valentine.
 * Features organic sweeping S-curves, whiplash tendrils, golden leaves, and ornate jewels.
 */
@Composable
private fun ArtNouveauFloralGoldFrame(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val goldPrimary = Color(0xFFFFD700)
        val goldHighlight = Color(0xFFFFF275)
        val goldDeep = Color(0xFFB8860B)
        val goldAmber = Color(0xFFF59E0B)

        val path = Path()

        // 1. Dual ornate concentric borders with concave notched corners
        val inset1 = 12.dp.toPx()
        val inset2 = 18.dp.toPx()

        drawRoundRect(
            color = goldPrimary.copy(alpha = 0.85f),
            topLeft = Offset(inset1, inset1),
            size = androidx.compose.ui.geometry.Size(w - inset1 * 2, h - inset1 * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
            style = Stroke(width = 1.6.dp.toPx())
        )

        drawRoundRect(
            color = goldAmber.copy(alpha = 0.55f),
            topLeft = Offset(inset2, inset2),
            size = androidx.compose.ui.geometry.Size(w - inset2 * 2, h - inset2 * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )

        // 2. Art Nouveau floral whiplash corner flourishes (Mucha style organic S-curves and petals)
        val cornerOffsets = listOf(
            Offset(inset1, inset1) to 0f,
            Offset(w - inset1, inset1) to 90f,
            Offset(w - inset1, h - inset1) to 180f,
            Offset(inset1, h - inset1) to 270f
        )

        for ((corner, rotation) in cornerOffsets) {
            withTransform({
                rotate(degrees = rotation, pivot = corner)
            }) {
                path.reset()
                // Sweeping S-curve vine
                path.moveTo(corner.x, corner.y + 44.dp.toPx())
                path.cubicTo(
                    corner.x + 8.dp.toPx(), corner.y + 24.dp.toPx(),
                    corner.x + 24.dp.toPx(), corner.y + 8.dp.toPx(),
                    corner.x + 44.dp.toPx(), corner.y
                )
                path.cubicTo(
                    corner.x + 28.dp.toPx(), corner.y + 14.dp.toPx(),
                    corner.x + 14.dp.toPx(), corner.y + 28.dp.toPx(),
                    corner.x, corner.y + 44.dp.toPx()
                )
                drawPath(
                    path = path,
                    color = goldPrimary.copy(alpha = 0.90f),
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )

                // Organic whiplash loop tendril
                path.reset()
                path.moveTo(corner.x + 10.dp.toPx(), corner.y + 36.dp.toPx())
                path.cubicTo(
                    corner.x + 30.dp.toPx(), corner.y + 30.dp.toPx(),
                    corner.x + 30.dp.toPx(), corner.y + 12.dp.toPx(),
                    corner.x + 18.dp.toPx(), corner.y + 18.dp.toPx()
                )
                drawPath(
                    path = path,
                    color = goldHighlight.copy(alpha = 0.85f),
                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                )

                // Stylized golden floral bud / leaf
                path.reset()
                path.moveTo(corner.x + 22.dp.toPx(), corner.y + 22.dp.toPx())
                path.cubicTo(
                    corner.x + 16.dp.toPx(), corner.y + 8.dp.toPx(),
                    corner.x + 8.dp.toPx(), corner.y + 16.dp.toPx(),
                    corner.x + 22.dp.toPx(), corner.y + 22.dp.toPx()
                )
                drawPath(path = path, color = goldDeep.copy(alpha = 0.75f))
                drawPath(
                    path = path,
                    color = goldHighlight,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Corner gold jewel
                drawCircle(
                    color = goldHighlight,
                    radius = 3.dp.toPx(),
                    center = Offset(corner.x + 6.dp.toPx(), corner.y + 6.dp.toPx())
                )
                drawCircle(
                    color = goldAmber,
                    radius = 4.5.dp.toPx(),
                    center = Offset(corner.x + 6.dp.toPx(), corner.y + 6.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        // Cardinal midpoint floral medallions (top, bottom, left, right)
        val cardinalPoints = listOf(
            Offset(w / 2f, inset1),
            Offset(w / 2f, h - inset1),
            Offset(inset1, h / 2f),
            Offset(w - inset1, h / 2f)
        )
        for (cp in cardinalPoints) {
            drawCircle(
                color = goldHighlight,
                radius = 3.5.dp.toPx(),
                center = cp
            )
            drawCircle(
                color = goldAmber,
                radius = 5.5.dp.toPx(),
                center = cp,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}


