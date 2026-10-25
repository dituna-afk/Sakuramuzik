package com.example.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.ui.components.drawEclipseCryingBehelit
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.downloader.MediaTrackInfo
import com.example.player.PlayerState
import com.example.ui.components.drawSpikeLighterAndHorizontalSmoke
import com.example.ui.theme.AnimeArtRegistry
import com.example.ui.theme.AppTheme
import com.example.ui.theme.LocalAppThemePalette
import com.example.ui.theme.getCustomFontFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun YoutubeDLStreamMiniPlayer(
    track: MediaTrackInfo,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    errorMessage: String?,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    val safeDuration = durationMs.coerceAtLeast(1L)
    val sliderFraction = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(palette.surfaceContainer, palette.surfaceVariant)
                )
            )
            .border(1.dp, palette.primaryAccent.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.primaryAccent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!track.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = track.thumbnailUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = palette.primaryAccent
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CANLI AKIM (ExoPlayer)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.primaryAccent
                        )
                        if (isBuffering) {
                            Spacer(modifier = Modifier.width(6.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.8.dp,
                                color = palette.primaryAccent
                            )
                        }
                    }
                    Text(
                        text = track.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = palette.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        fontSize = 12.sp,
                        color = palette.onSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedPlayPauseButton(
                    isPlaying = isPlaying,
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(40.dp)
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Akımı Kapat",
                        tint = palette.onSurfaceMuted
                    )
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                fontSize = 11.sp,
                color = palette.tertiaryAccent
            )
        }

        Slider(
            value = sliderFraction,
            onValueChange = { frac ->
                onSeekTo((frac * safeDuration).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = palette.primaryAccent,
                activeTrackColor = palette.primaryAccent,
                inactiveTrackColor = palette.surface
            ),
            modifier = Modifier.height(24.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDurationMs(currentPositionMs),
                fontSize = 11.sp,
                color = palette.onSurfaceMuted
            )
            Text(
                text = formatDurationMs(durationMs),
                fontSize = 11.sp,
                color = palette.onSurfaceMuted
            )
        }
    }
}

@Composable
fun MiniPlayerBar(
    track: MediaTrackInfo,
    isPlaying: Boolean,
    progressFraction: Float,
    isLoading: Boolean = false,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    val animeMeta = remember(palette.theme) { AnimeArtRegistry.getMetadata(palette.theme) }
    val customFontFamily = remember(palette.theme) { palette.theme.getCustomFontFamily(true) }
    val isBerserkGuts = palette.theme == AppTheme.BERSERK_GUTS
    val isBerserkGriffith = palette.theme == AppTheme.BERSERK_GRIFFITH
    val isCowboySpike = palette.theme == AppTheme.COWBOY_BEBOP_SPIKE
    val isCowboyFaye = false
    val isEvangelion = false
    var horizontalDragOffset by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = horizontalDragOffset,
        animationSpec = tween(durationMillis = 150),
        label = "mini_player_swipe_offset"
    )

    val bebopConfig = com.example.ui.theme.LocalCowboyBebopConfig.current
    val isLifecycleResumed = com.example.ui.components.rememberIsLifecycleResumed()
    val shouldRunBebopMiniFx = isCowboySpike &&
        bebopConfig.cbMasterAnimationsEnabled &&
        bebopConfig.cbMiniPlayerGlowHighlightEnabled &&
        !bebopConfig.cbLowEffectModeEnabled &&
        (!bebopConfig.cbPauseEffectsInBackgroundEnabled || isLifecycleResumed)
    val shouldRunGutsMiniFx = isBerserkGuts && isLifecycleResumed

    // Sadece gerçekten animasyon kullanan ve görünür/etkin temalarda InfiniteTransition çalıştırılır
    val cardiacPhase: Float
    val behelitSwingAngle: Float
    val lighterSmokePhase: Float
    if (shouldRunGutsMiniFx || shouldRunBebopMiniFx) {
        val infiniteTransition = rememberInfiniteTransition(label = "mini_player_anime_fx")
        cardiacPhase = if (shouldRunGutsMiniFx) {
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = (2f * PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2300, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "berserk_cardiac_phase"
            ).value
        } else 0f

        behelitSwingAngle = if (shouldRunGutsMiniFx) {
            infiniteTransition.animateFloat(
                initialValue = -13f,
                targetValue = 13f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "behelit_swing"
            ).value
        } else 0f

        lighterSmokePhase = if (shouldRunBebopMiniFx) {
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "spike_lighter_smoke"
            ).value
        } else 0f
    } else {
        cardiacPhase = 0f
        behelitSwingAngle = 0f
        lighterSmokePhase = 0f
    }

    val fayeNoiseDots = remember {
        val rng = Random(4412)
        List(48) {
            Triple(rng.nextFloat(), rng.nextFloat(), 0.05f + rng.nextFloat() * 0.08f)
        }
    }

    // GENEL KURAL: Yüzen (Floating) Mini Oynatıcı ekranın en altına yapışık olmayacak.
    // Sağ, sol ve alttan 12dp boşluklu, köşeleri yuvarlatılmış (RoundedCornerShape) ve gölgeli bir "kart" formunda olacak.
    val miniCardShape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
    ) {
        // 1. BERSERK - GUTS (Mini Oynatıcı Aura):
        // Mini oynatıcının arkasına Brush.radialGradient ile koyu kızıl, etsi bir sis çizilir.
        // Yaşayan tekinsiz bir form gibi düzensiz aralıklarla kalp gibi atar (çift atımlı sistol/diyastol + tekinsiz titreme).
        if (isBerserkGuts) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { clip = false }
            ) {
                if (size.width <= 0f || size.height <= 0f) return@Canvas
                val systole1 = max(0f, sin(cardiacPhase)).pow(4f)
                val systole2 = max(0f, sin(cardiacPhase + 0.88f)).pow(6f) * 0.68f
                val irregularTremor = 0.5f + 0.5f * sin(cardiacPhase * 3.0f + 1.4f)
                val heartbeatPulse = (0.30f + systole1 * 0.52f + systole2 * 0.38f + irregularTremor * 0.14f)
                    .coerceIn(0.25f, 1.15f)

                val centerMain = Offset(
                    x = size.width * (0.48f + 0.04f * sin(cardiacPhase * 2f)),
                    y = size.height * 0.52f
                )
                val mistRadiusMain = (size.width * (0.58f + 0.22f * heartbeatPulse)).coerceAtLeast(1f)

                // Ana koyu kızıl & etsi sis katmanı
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF990A17).copy(alpha = (0.72f * heartbeatPulse).coerceIn(0f, 0.92f)),
                            Color(0xFF5E0611).copy(alpha = (0.52f * heartbeatPulse).coerceIn(0f, 0.78f)),
                            Color(0xFF2B0318).copy(alpha = (0.32f * heartbeatPulse).coerceIn(0f, 0.55f)),
                            Color.Transparent
                        ),
                        center = centerMain,
                        radius = mistRadiusMain
                    ),
                    radius = mistRadiusMain,
                    center = centerMain
                )

                // İkinci asimetrik yaşayan etsi nabız lobu
                val lobeLeft = Offset(
                    x = size.width * (0.24f - 0.03f * cos(cardiacPhase)),
                    y = size.height * (0.45f + 0.08f * sin(cardiacPhase * 2f))
                )
                val lobeRight = Offset(
                    x = size.width * (0.78f + 0.03f * sin(cardiacPhase)),
                    y = size.height * (0.56f - 0.08f * cos(cardiacPhase * 2f))
                )
                val lobeRadius = (size.width * (0.36f + 0.16f * systole1)).coerceAtLeast(1f)
                val lobeRightRadius = (lobeRadius * 1.1f).coerceAtLeast(1f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF1E27).copy(alpha = (0.42f * heartbeatPulse).coerceIn(0f, 0.65f)),
                            Color(0xFF4A0E4E).copy(alpha = (0.28f * heartbeatPulse).coerceIn(0f, 0.48f)),
                            Color.Transparent
                        ),
                        center = lobeLeft,
                        radius = lobeRadius
                    ),
                    radius = lobeRadius,
                    center = lobeLeft
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF7A0916).copy(alpha = (0.48f * heartbeatPulse).coerceIn(0f, 0.70f)),
                            Color(0xFF3B0726).copy(alpha = (0.25f * heartbeatPulse).coerceIn(0f, 0.45f)),
                            Color.Transparent
                        ),
                        center = lobeRight,
                        radius = lobeRightRadius
                    ),
                    radius = lobeRightRadius,
                    center = lobeRight
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isBerserkGuts) 22.dp else 14.dp,
                    shape = miniCardShape,
                    ambientColor = if (isBerserkGuts) Color(0xFFFF1E27) else palette.primaryAccent,
                    spotColor = if (isBerserkGuts) Color(0xFFFF1E27) else palette.primaryAccent
                )
                .clip(miniCardShape)
                .background(
                    when {
                        // 1. BERSERK - GUTS: Tam OLED Siyahı (#000000) zemin + etsi kızıl alt yansıma
                        isBerserkGuts -> Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF000000),
                                Color(0xFF090104),
                                Color(0xFF000000)
                            )
                        )
                        // 2. COWBOY BEBOP: Gece dostu mor-kehribar zemin (Faye Valentine estetiği)
                        isCowboySpike -> Brush.horizontalGradient(
                            colors = listOf(
                                palette.surface,
                                palette.surfaceContainer,
                                palette.surfaceVariant
                            )
                        )
                        // 3. COWBOY BEBOP - FAYE: Gece moru zemin
                        isCowboyFaye -> Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF12091C),
                                Color(0xFF1D102D),
                                Color(0xFF27153D)
                            )
                        )
                        else -> Brush.horizontalGradient(
                            colors = listOf(palette.surfaceContainer, palette.surfaceVariant)
                        )
                    }
                )
                .border(
                    width = if (isEvangelion) 1.8.dp else 1.2.dp,
                    color = when {
                        isBerserkGuts -> Color(0xFFFF1E27).copy(alpha = 0.55f)
                        isCowboySpike -> if (bebopConfig.cbMiniPlayerGlowHighlightEnabled) {
                            palette.primaryAccent.copy(alpha = 0.65f)
                        } else {
                            palette.dividerOrBorder
                        }
                        isCowboyFaye -> Color(0xFFFFD700).copy(alpha = 0.65f) // Kumarhane altını
                        isEvangelion -> Color(0xFF00FF66).copy(alpha = 0.80f) // Toksik neon yeşili
                        else -> palette.dividerOrBorder
                    },
                    shape = miniCardShape
                )
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (horizontalDragOffset < -90f) {
                                onSkipNext()
                            } else if (horizontalDragOffset > 90f) {
                                onSkipPrevious()
                            }
                            horizontalDragOffset = 0f
                        },
                        onDragCancel = { horizontalDragOffset = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            horizontalDragOffset = (horizontalDragOffset + dragAmount).coerceIn(-160f, 160f)
                        }
                    )
                }
                .clickable(onClick = onExpandClick)
        ) {
            val sadeConfig = com.example.data.LocalCustomMinimalThemeConfig.current

            Box(modifier = Modifier.fillMaxWidth()) {
                // Sade Tema / Özel Mini Oynatıcı Duvar Kağıdı (Griffith temasında duvar kağıdı tamamen devre dışıdır)
                if (!isBerserkGriffith && !sadeConfig.miniPlayerWallpaperPath.isNullOrBlank()) {
                    AsyncImage(
                        model = java.io.File(sadeConfig.miniPlayerWallpaperPath),
                        contentDescription = "Özel Mini Oynatıcı Duvar Kağıdı",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                alpha = sadeConfig.miniPlayerWallpaperOpacity.coerceIn(0f, 1f)
                            }
                    )
                }

                // Karakter arka plan görseli (Griffith ve Cowboy Bebop Spike temalarında tamamen kaldırıldı)
                if (animeMeta?.systemVectorDrawableRes != null && !isCowboySpike && !isBerserkGriffith) {
                    Image(
                        painter = painterResource(id = animeMeta.systemVectorDrawableRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = if (isBerserkGuts) 0.14f else 0.18f }
                    )
                }

                // 3. COWBOY BEBOP - FAYE VALENTINE: Hafif kumlu (noise) gece moru doku
                if (isCowboyFaye) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        for ((xf, yf, a) in fayeNoiseDots) {
                            drawCircle(
                                color = Color(0xFFFFD700).copy(alpha = a),
                                radius = 1.3.dp.toPx(),
                                center = Offset(xf * size.width, yf * size.height)
                            )
                        }
                    }
                }

                // 4. EVANGELION UNIT-01: Oynatıcı zeminine alpha 0.15 değerinde pembe retro ızgara (CRT grid)
                if (isEvangelion) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val step = 14.dp.toPx()
                        val crtGridColor = Color(0xFFFF2A85).copy(alpha = 0.15f)
                        var x = 0f
                        while (x <= size.width) {
                            drawLine(crtGridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                            x += step
                        }
                        var y = 0f
                        while (y <= size.height) {
                            drawLine(crtGridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                            y += step
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .graphicsLayer { translationX = animatedOffset * 0.25f },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Albüm kapağı & Berserk Behelit Kolyesi (Cowboy Bebop dönen plak tamamen kaldırıldı!)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isCowboySpike) Color(0xFF0A1220)
                                        else palette.primaryAccent.copy(alpha = 0.2f)
                                    )
                                    .border(
                                        width = 1.4.dp,
                                        color = when {
                                            isBerserkGuts -> Color(0xFFFF1E27)
                                            isCowboySpike -> Color(0xFFFFB300) // Retro kehribar çerçeve
                                            isCowboyFaye -> Color(0xFFFFD700) // Kumarhane altını
                                            isEvangelion -> Color(0xFF39FF14) // Neon yeşil
                                            else -> palette.primaryAccent.copy(alpha = 0.45f)
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!track.thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = track.thumbnailUrl,
                                        contentDescription = track.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = if (isCowboySpike) Color(0xFFFFB300) else palette.primaryAccent
                                    )
                                }
                            }

                            // 1. BERSERK - GUTS: Mini oynatıcıda yanıp sönen, yüz hatları henüz dağınık (uykuda - Görsel 1) Behelit kolyesi
                            if (isBerserkGuts) {
                                SwingingBehelitNecklaceCanvas(
                                    swingAngleDeg = behelitSwingAngle,
                                    pulsePhase = cardiacPhase,
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .width(28.dp)
                                        .height(48.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // 1. BERSERK - GUTS: Saf beyaz (#FFFFFF) ana metin + kan kırmızısı (#FF1E27) parlama ve vurgu
                        val titleGlowStyle = if (isBerserkGuts) {
                            TextStyle(
                                shadow = Shadow(
                                    color = Color(0xFFFF1E27),
                                    offset = Offset(0f, 0f),
                                    blurRadius = 18f
                                )
                            )
                        } else TextStyle()

                        val artistGlowStyle = if (isBerserkGuts) {
                            TextStyle(
                                shadow = Shadow(
                                    color = Color(0xFF991B1B),
                                    offset = Offset(0f, 0f),
                                    blurRadius = 12f
                                )
                            )
                        } else TextStyle()

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontWeight = FontWeight.Bold,
                                fontFamily = customFontFamily,
                                fontSize = 14.sp,
                                color = when {
                                    isBerserkGuts -> Color(0xFFFFFFFF) // Saf Beyaz
                                    isCowboySpike -> Color(0xFFF8FAFC)
                                    isCowboyFaye -> Color(0xFFFFFDF7)
                                    isEvangelion -> Color(0xFFFFFFFF)
                                    else -> palette.onSurface
                                },
                                style = titleGlowStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${track.artist} • Kaydır: Önceki/Sonraki",
                                fontSize = 11.sp,
                                fontFamily = customFontFamily,
                                color = when {
                                    isBerserkGuts -> Color(0xFFFF1E27) // Kan Kırmızısı alt vurgu
                                    isCowboySpike -> Color(0xFF94A3B8) // Soğuk gümüş
                                    isCowboyFaye -> Color(0xFFFFD700) // Kumarhane altını
                                    isEvangelion -> Color(0xFF39FF14) // Toksik neon yeşil
                                    else -> palette.onSurfaceMuted
                                },
                                style = artistGlowStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Kontroller
                    val buttonTint = when {
                        isBerserkGuts -> Color(0xFFFFFFFF)
                        isCowboyFaye -> Color(0xFFFFD700)
                        isCowboySpike -> Color(0xFFE2E8F0)
                        isEvangelion -> Color(0xFF39FF14)
                        else -> palette.onSurface
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onSkipPrevious) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Önceki",
                                tint = buttonTint
                            )
                        }
                        AnimatedPlayPauseButton(
                            isPlaying = isPlaying,
                            onClick = onTogglePlayPause,
                            modifier = Modifier.size(40.dp)
                        )
                        IconButton(onClick = onSkipNext) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Sonraki",
                                tint = buttonTint
                            )
                        }
                        IconButton(onClick = onExpandClick) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Tam Ekran Aç",
                                tint = when {
                                    isBerserkGuts -> Color(0xFFFF1E27)
                                    isCowboyFaye -> Color(0xFFFFD700)
                                    isCowboySpike -> Color(0xFFFFB300)
                                    isEvangelion -> Color(0xFF39FF14)
                                    else -> palette.primaryAccent
                                }
                            )
                        }
                    }
                }

                // 2. COWBOY BEBOP: Mini oynatıcı özel parıltı/duman vurgusu (cbMiniPlayerGlowHighlightEnabled açıkken)
                if (shouldRunBebopMiniFx) {
                    val smokePath = remember { Path() }
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                    ) {
                        drawSpikeLighterAndHorizontalSmoke(
                            canvasWidth = size.width,
                            canvasHeight = size.height,
                            cycleProgress = lighterSmokePhase,
                            reusablePath = smokePath
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = when {
                    isBerserkGuts -> Color(0xFFFF1E27)
                    isCowboyFaye -> Color(0xFFFFD700)
                    isCowboySpike -> palette.primaryAccent
                    isEvangelion -> Color(0xFF39FF14)
                    else -> palette.primaryAccent
                },
                trackColor = palette.surface
            )
        }
    }
}

/**
 * 1. BERSERK - GUTS (Mini Oynatıcıdaki Uykuda / Dağınık Yüzlü Behelit Kolyesi - Görsel 1):
 * Mini oynatıcıda aşağıda kızıl nabızla yanıp söner, yüz hatları dağınıktır.
 * Tam ekrana geçildiğinde ise yavaşça dönüp yüzü hizalanarak kan ağlamaya başlar.
 */
@Composable
fun SwingingBehelitNecklaceCanvas(
    swingAngleDeg: Float,
    pulsePhase: Float = 0f,
    modifier: Modifier = Modifier
) {
    val cordPath = remember { Path() }
    val reusablePath = remember { Path() }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val pivot = Offset(w * 0.5f, 0f)

        withTransform({
            rotate(degrees = swingAngleDeg * 0.45f, pivot = pivot)
        }) {
            val bailY = h * 0.28f
            val bailX = w * 0.5f

            val leftAnchorX = w * 0.04f
            val rightAnchorX = w * 0.96f

            cordPath.reset()
            cordPath.moveTo(leftAnchorX, 0f)
            cordPath.quadraticTo(
                w * 0.24f, bailY * 0.60f,
                bailX - 1.5.dp.toPx(), bailY
            )
            cordPath.moveTo(bailX + 1.5.dp.toPx(), bailY)
            cordPath.quadraticTo(
                w * 0.76f, bailY * 0.60f,
                rightAnchorX, 0f
            )

            drawPath(
                path = cordPath,
                color = Color(0xFF24120B),
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                path = cordPath,
                color = Color(0xFF8C5832),
                style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Mini oynatıcıda uykuda (awakeningProgress = 0f) dağınık yüzlü ve kızıl nabızlı Behelit
            drawEclipseCryingBehelit(
                bailCenter = Offset(bailX, bailY),
                eggWidth = (w * 0.76f).coerceAtLeast(16.dp.toPx()),
                eggHeight = (h * 0.58f).coerceAtLeast(22.dp.toPx()),
                awakeningProgress = 0f,
                tearFlowPhase = 0f,
                pulsePhase = pulsePhase,
                reusablePath = reusablePath
            )
        }
    }
}

@Composable
fun MiniPlayer(
    state: PlayerState,
    volumeBoostPercent: Int = 100,
    isVolumeBoosterEnabled: Boolean = false,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetSleepTimer: (Int?) -> Unit
) {
    val palette = LocalAppThemePalette.current
    val currentTrack = state.currentTrack

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surfaceContainer)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentTrack?.trackInfo?.title ?: "Çevrimdışı Oynatıcı Hazır",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = palette.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isVolumeBoosterEnabled && volumeBoostPercent > 100) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.primaryAccent.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = palette.primaryAccent,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "%$volumeBoostPercent",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.primaryAccent
                            )
                        }
                    }
                }
                Text(
                    text = currentTrack?.trackInfo?.artist ?: "İndirilen bir şarkı seçin",
                    fontSize = 12.sp,
                    color = palette.onSurfaceMuted,
                    maxLines = 1
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSkipPrevious, enabled = state.queue.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Önceki",
                        tint = palette.onSurface
                    )
                }

                AnimatedPlayPauseButton(
                    isPlaying = state.isPlaying,
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(42.dp)
                )

                IconButton(onClick = onSkipNext, enabled = state.queue.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Sonraki",
                        tint = palette.onSurface
                    )
                }
            }
        }

        val safeDuration = state.durationMs.coerceAtLeast(1L)
        val sliderPos = (state.currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

        Slider(
            value = sliderPos,
            onValueChange = { fraction ->
                onSeekTo((fraction * safeDuration).toLong())
            },
            enabled = currentTrack != null,
            colors = SliderDefaults.colors(
                thumbColor = palette.primaryAccent,
                activeTrackColor = palette.primaryAccent,
                inactiveTrackColor = palette.surfaceVariant
            ),
            modifier = Modifier.height(22.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                    val selected = state.playbackSpeed == speed
                    Button(
                        onClick = { onSetPlaybackSpeed(speed) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) palette.primaryAccent else palette.surface,
                            contentColor = if (selected) palette.onPrimary else palette.onSurfaceMuted
                        ),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text(text = "${speed}x", fontSize = 10.sp)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val timerActive = state.sleepTimerRemainingMs != null
                Button(
                    onClick = {
                        if (timerActive) onSetSleepTimer(null) else onSetSleepTimer(15)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (timerActive) palette.secondaryAccent else palette.surface,
                        contentColor = if (timerActive) Color.Black else palette.onSurfaceMuted
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    val label = if (timerActive) {
                        val remMin = ((state.sleepTimerRemainingMs ?: 0L) / 60000L) + 1L
                        "Uyku: ${remMin}dk"
                    } else {
                        "Uyku: Kapalı"
                    }
                    Text(text = label, fontSize = 10.sp)
                }
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%02d:%02d".format(min, sec)
}
