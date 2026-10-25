package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.example.data.BebopAnimationIntensity
import com.example.data.BerserkAnimMode
import com.example.data.CowboyBebopThemeConfig
import com.example.ui.theme.AppTheme
import com.example.ui.theme.LocalAppThemePalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Berserk - Griffith teması için estetik, kısa ve okunabilir sözler.
 */
object GriffithThemeQuotes {
    val HEADER_AND_PLAYER_QUOTES = listOf(
        "Kendi kanatlarıyla uçmayan biri, başkasının gökyüzünde yalnızca bir gölgedir.",
        "Bir hayalin peşinden yürümek, kaderin sessizliğine meydan okumaktır.",
        "Yüksekteki kale, yalnızca gözlerini karanlıktan ayırmayanlara görünür.",
        "Işığın Şahini gökyüzünü yardığında, tüm fırtınalar tek bir ufukta birleşir.",
        "Gerçek bir dost, kendi hayali uğruna senin bile karşında durabilen kişidir."
    )

    val RANDOM_TRANSITION_LINES = listOf(
        "Şahin kanatlarını açtı; yeni bir melodi ufukta yükseliyor.",
        "Beyaz şahinin gölgesi sessizliği yarıyor.",
        "Rüzgâr, uzaktaki krallığın çanlarını taşıyor.",
        "Kaderin tüyü suyun yüzeyine dokundu.",
        "Gümüş kanatlar altında yankılanan son nota."
    )

    fun quoteForKey(seedKey: String?): String {
        if (seedKey.isNullOrBlank()) return HEADER_AND_PLAYER_QUOTES.first()
        val idx = (seedKey.hashCode() and Int.MAX_VALUE) % HEADER_AND_PLAYER_QUOTES.size
        return HEADER_AND_PLAYER_QUOTES[idx]
    }

    fun transitionLineForKey(seedKey: String?): String {
        if (seedKey.isNullOrBlank()) return RANDOM_TRANSITION_LINES.first()
        val idx = (seedKey.hashCode() and Int.MAX_VALUE) % RANDOM_TRANSITION_LINES.size
        return RANDOM_TRANSITION_LINES[idx]
    }

    fun randomLineForContext(seedKey: String?): String = transitionLineForKey(seedKey)
}

@Composable
fun rememberIsLifecycleResumed(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    return lifecycleState.isAtLeast(Lifecycle.State.STARTED)
}

/**
 * 2. Işığın Şahini (Falcon of Light) Sabit Amblemi:
 * Griffith temasına özel, bağımsız olarak açılıp kapatılabilen asil gümüş-buz mavisi şahin silüeti.
 */
@Composable
fun FalconOfLightEmblemCanvas(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    tint: Color = Color(0xFF70D6FF),
    secondaryTint: Color = Color(0xFFE2E8F0),
    primaryColor: Color = secondaryTint,
    secondaryColor: Color = tint
) {
    val reusablePath = remember { Path() }
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val cx = w * 0.5f
        val cy = h * 0.52f

        // Dış asil hale
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondaryColor.copy(alpha = 0.28f),
                    primaryColor.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = w * 0.52f
            ),
            radius = w * 0.52f,
            center = Offset(cx, cy)
        )

        drawFalconOfLightSilhouette(
            center = Offset(cx, cy),
            wingspan = w * 0.84f,
            bodyHeight = h * 0.72f,
            wingFlapFactor = 0.15f,
            primaryColor = primaryColor,
            accentColor = secondaryColor,
            reusablePath = reusablePath
        )
    }
}

/**
 * 3. Gerçek Uçan Şahin Animasyonu (Flying Falcon of Light):
 * Sabit amblemden tamamen bağımsız bir özelliktir.
 * Etkinleştirildiğinde yalnızca Berserk - Griffith temasında ve ana ekran görünürken
 * ara sıra şarkı kartlarının üzerinden zarifçe süzülerek geçer.
 * Dokunma ve kaydırma etkileşimlerini asla engellemez (pointerInput içermez).
 * Uygulama arka plana alındığında veya ayar kapatıldığında animasyon döngüsü tamamen durur.
 */
@Composable
fun FlyingFalconOfLightOverlay(
    enabled: Boolean,
    isScreenVisible: Boolean = true,
    isScrolling: Boolean = false,
    isScrollInProgress: Boolean = isScrolling,
    primaryColor: Color = Color(0xFFF8FAFC),
    secondaryColor: Color = Color(0xFF70D6FF),
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    if (!enabled || !isScreenVisible || palette.theme != AppTheme.BERSERK_GRIFFITH) {
        return
    }

    val isLifecycleStarted = rememberIsLifecycleResumed()
    if (!isLifecycleStarted) return

    var flightProgress by remember { mutableFloatStateOf(-1f) }
    var wingBeatPhase by remember { mutableFloatStateOf(0f) }
    var flightPassIndex by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    LaunchedEffect(enabled, isScreenVisible, isLifecycleStarted, isScrollInProgress) {
        if (!enabled || !isScreenVisible || !isLifecycleStarted) {
            flightProgress = -1f
            return@LaunchedEffect
        }
        // Kaydırma sırasında ağır çizim yapmamak için kaydırma bitene kadar bekle
        if (isScrollInProgress) {
            return@LaunchedEffect
        }

        // İlk zarif geçiş için kısa bekleme, sonraki geçişler arası dinlenme
        delay(if (flightPassIndex == 0) 900L else 11_500L)

        while (isActive && enabled && isScreenVisible && isLifecycleStarted && !isScrollInProgress) {
            val flightDurationNanos = 4_200_000_000L
            val startNanos = withFrameNanos { it }
            var currentProgress = 0f
            while (isActive && currentProgress < 1f && !isScrollInProgress) {
                val now = withFrameNanos { it }
                currentProgress = ((now - startNanos).toFloat() / flightDurationNanos.toFloat()).coerceIn(0f, 1f)
                flightProgress = currentProgress
                wingBeatPhase = (currentProgress * 4.5f * PI.toFloat())
            }
            flightProgress = -1f
            flightPassIndex++
            delay(13_000L)
        }
    }

    if (flightProgress !in 0f..1f) return

    val reusablePath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val p = flightProgress.coerceIn(0f, 1f)
        val envelopeAlpha = when {
            p < 0.15f -> (p / 0.15f).coerceIn(0f, 1f)
            p > 0.82f -> ((1f - p) / 0.18f).coerceIn(0f, 1f)
            else -> 1f
        }

        val startX = -w * 0.12f
        val endX = w * 1.12f
        val curX = lerpFloat(startX, endX, p)
        val arcY = h * (0.28f - 0.09f * sin(p * PI.toFloat()))
        val flap = sin(wingBeatPhase) * 0.45f

        // Süzülme izi (Işık tüyü parıltısı)
        for (trailIdx in 1..4) {
            val trailP = (p - trailIdx * 0.032f).coerceIn(0f, 1f)
            val tx = lerpFloat(startX, endX, trailP)
            val ty = h * (0.28f - 0.09f * sin(trailP * PI.toFloat())) + trailIdx * 3.dp.toPx()
            val trailAlpha = (envelopeAlpha * (0.28f - trailIdx * 0.055f)).coerceAtLeast(0f)
            drawCircle(
                color = secondaryColor.copy(alpha = trailAlpha),
                radius = (8 - trailIdx).dp.toPx(),
                center = Offset(tx, ty)
            )
        }

        withTransform({
            rotate(degrees = -8f + cos(p * PI.toFloat()) * 10f, pivot = Offset(curX, arcY))
        }) {
            drawFalconOfLightSilhouette(
                center = Offset(curX, arcY),
                wingspan = 84.dp.toPx(),
                bodyHeight = 46.dp.toPx(),
                wingFlapFactor = flap,
                primaryColor = primaryColor.copy(alpha = (0.86f * envelopeAlpha).coerceIn(0f, 1f)),
                accentColor = secondaryColor.copy(alpha = (0.72f * envelopeAlpha).coerceIn(0f, 1f)),
                reusablePath = reusablePath
            )
        }
    }
}

internal fun DrawScope.drawFalconOfLightSilhouette(
    center: Offset,
    wingspan: Float,
    bodyHeight: Float,
    wingFlapFactor: Float,
    primaryColor: Color,
    accentColor: Color,
    reusablePath: Path
) {
    val halfWing = wingspan * 0.5f
    val halfBody = bodyHeight * 0.5f
    val wingTipLift = -halfBody * (0.42f + wingFlapFactor * 0.55f)

    // İnce semavi ışık halesi
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accentColor.copy(alpha = accentColor.alpha * 0.38f),
                Color.Transparent
            ),
            center = center,
            radius = halfWing * 0.95f
        ),
        radius = halfWing * 0.95f,
        center = center
    )

    reusablePath.reset()
    // Baş / Gaga ucu
    reusablePath.moveTo(center.x, center.y - halfBody * 0.82f)
    // Sağ iç omuz -> Sağ kanat ucu
    reusablePath.cubicTo(
        center.x + halfWing * 0.22f, center.y - halfBody * 0.45f,
        center.x + halfWing * 0.64f, center.y + wingTipLift - halfBody * 0.25f,
        center.x + halfWing, center.y + wingTipLift
    )
    // Sağ kanat telek kıvrımı -> Kuyruk sağ
    reusablePath.cubicTo(
        center.x + halfWing * 0.68f, center.y + wingTipLift + halfBody * 0.48f,
        center.x + halfWing * 0.28f, center.y + halfBody * 0.22f,
        center.x + halfWing * 0.14f, center.y + halfBody * 0.85f
    )
    // Kuyruk merkezi
    reusablePath.lineTo(center.x, center.y + halfBody * 0.62f)
    // Kuyruk sol
    reusablePath.lineTo(center.x - halfWing * 0.14f, center.y + halfBody * 0.85f)
    // Sol kanat alt kıvrımı -> Sol kanat ucu
    reusablePath.cubicTo(
        center.x - halfWing * 0.28f, center.y + halfBody * 0.22f,
        center.x - halfWing * 0.68f, center.y + wingTipLift + halfBody * 0.48f,
        center.x - halfWing, center.y + wingTipLift
    )
    // Sol kanat üst kıvrımı -> Baş
    reusablePath.cubicTo(
        center.x - halfWing * 0.64f, center.y + wingTipLift - halfBody * 0.25f,
        center.x - halfWing * 0.22f, center.y - halfBody * 0.45f,
        center.x, center.y - halfBody * 0.82f
    )
    reusablePath.close()

    drawPath(path = reusablePath, color = primaryColor)
    drawPath(
        path = reusablePath,
        color = accentColor,
        style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/**
 * H. COWBOY BEBOP — Bağımsız Uzay, Yıldız Alanı, Nebula ve Retro-Fütüristik HUD Katmanı:
 * - 96 sabit uzay yıldızı Modifier.drawWithCache ile önbelleğe alınır (her karede yeniden hesaplanmaz).
 * - Tüm 31 bağımsız CowboyBebopThemeConfig ayarına (yıldız alanı, kayan yıldız, nebula, uzay tozu,
 *   arka plan ışıltısı, derinlik paralaksı, retro HUD çizgileri, teknik göstergeler, düşük efekt modu,
 *   kaydırma ve arka plan duraklatması) birebir uyar.
 */
private data class CachedBebopStar(
    val xFrac: Float,
    val yFrac: Float,
    val radiusDp: Float,
    val baseAlpha: Float,
    val isAmberGold: Boolean,
    val isPlumTint: Boolean
)

@Composable
fun CowboyBebopSpaceAndHudOverlay(
    config: CowboyBebopThemeConfig,
    isScrolling: Boolean = false,
    isPlaying: Boolean = false,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    if (palette.theme != AppTheme.COWBOY_BEBOP_SPIKE) return

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val isLifecycleStarted = lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    if (config.cbPauseEffectsInBackgroundEnabled && !isLifecycleStarted) {
        return
    }

    val animationsAllowed = config.cbMasterAnimationsEnabled &&
        config.cbAnimationIntensity != BebopAnimationIntensity.OFF &&
        (!config.cbPauseHeavyEffectsOnScrollEnabled || !isScrolling)

    val intensityScale = if (config.cbLowEffectModeEnabled) {
        0.35f
    } else {
        config.cbAnimationIntensity.multiplier.coerceAtLeast(0.25f)
    }

    val brightnessFactor = (0.45f + (config.cbVisualBrightnessPercent.coerceIn(0, 100) / 100f) * 0.70f)

    // 96 Sabit Yıldız ve Uzay Tozu Koordinatları (Bellekte tek sefer üretilir)
    val staticStars = remember {
        val rng = Random(9928)
        List(96) { idx ->
            CachedBebopStar(
                xFrac = 0.02f + rng.nextFloat() * 0.96f,
                yFrac = 0.02f + rng.nextFloat() * 0.94f,
                radiusDp = 0.45f + rng.nextFloat() * 0.90f,
                baseAlpha = (0.18f + rng.nextFloat() * 0.58f),
                isAmberGold = idx % 5 == 0,
                isPlumTint = idx % 7 == 0
            )
        }
    }

    val spaceDustPoints = remember {
        val rng = Random(4419)
        List(42) {
            Triple(rng.nextFloat(), rng.nextFloat(), 0.04f + rng.nextFloat() * 0.09f)
        }
    }

    // Yalnızca animasyonlu bir alt özellik açıksa ve duraklatılmamışsa zamanlayıcı çalışır
    val needsLoop = animationsAllowed && (
        config.cbShootingStarsEnabled ||
            config.cbBackgroundShimmerEnabled ||
            config.cbDepthParallaxEnabled ||
            isLoading
        )

    var animPhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(needsLoop, config.cbAnimationSpeed, config.cbHighRefreshOptimizationEnabled) {
        if (!needsLoop) return@LaunchedEffect
        val cycleDurationNanos = (9_000_000_000L / config.cbAnimationSpeed.speedMultiplier.coerceAtLeast(0.5f)).toLong()
        val startNanos = withFrameNanos { it }
        while (isActive && needsLoop) {
            val now = withFrameNanos { it }
            animPhase = (((now - startNanos) % cycleDurationNanos).toFloat() / cycleDurationNanos.toFloat()).coerceIn(0f, 1f)
        }
    }

    val reusablePath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height

                // Nebula bulut fırçaları (Faye Valentine koyu erik #3A1F47, dumanlı mor #58356E, mat kehribar #D4A338)
                val plumNebulaBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF58356E).copy(alpha = (0.20f * brightnessFactor * intensityScale).coerceIn(0f, 0.32f)),
                        Color(0xFF3A1F47).copy(alpha = (0.11f * brightnessFactor * intensityScale).coerceIn(0f, 0.20f)),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.24f, h * 0.28f),
                    radius = (w * 0.68f).coerceAtLeast(1f)
                )
                val amberNebulaBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFD4A338).copy(alpha = (0.12f * brightnessFactor * intensityScale).coerceIn(0f, 0.22f)),
                        Color(0xFFB88728).copy(alpha = (0.06f * brightnessFactor * intensityScale).coerceIn(0f, 0.14f)),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.78f, h * 0.66f),
                    radius = (w * 0.58f).coerceAtLeast(1f)
                )

                val starPositions = staticStars.map { star ->
                    Offset(star.xFrac * w, star.yFrac * h)
                }

                onDrawBehind {
                    if (w <= 0f || h <= 0f) return@onDrawBehind

                    val parallaxShiftX = if (config.cbDepthParallaxEnabled && animationsAllowed) {
                        sin(animPhase * 2f * PI.toFloat()) * 6.dp.toPx() * intensityScale
                    } else 0f
                    val parallaxShiftY = if (config.cbDepthParallaxEnabled && animationsAllowed) {
                        cos(animPhase * 2f * PI.toFloat()) * 4.dp.toPx() * intensityScale
                    } else 0f

                    // 1. Nebula Bulutları (cbNebulaCloudsEnabled)
                    if (config.cbNebulaCloudsEnabled && !config.cbLowEffectModeEnabled) {
                        drawCircle(
                            brush = plumNebulaBrush,
                            radius = (w * 0.68f).coerceAtLeast(1f),
                            center = Offset(w * 0.24f + parallaxShiftX, h * 0.28f + parallaxShiftY)
                        )
                        drawCircle(
                            brush = amberNebulaBrush,
                            radius = (w * 0.58f).coerceAtLeast(1f),
                            center = Offset(w * 0.78f - parallaxShiftX, h * 0.66f - parallaxShiftY)
                        )
                    }

                    // 2. 96 Sabit Uzay Yıldızı (cbStarfieldEnabled - Donanım Önbellekli)
                    if (config.cbStarfieldEnabled) {
                        val shimmerMult = if (config.cbBackgroundShimmerEnabled && animationsAllowed) {
                            0.82f + 0.18f * sin(animPhase * 2f * PI.toFloat())
                        } else 1f

                        val starLimit = if (config.cbLowEffectModeEnabled || config.cbReduceIdleDecorationsEnabled && !isPlaying) {
                            48
                        } else {
                            staticStars.size
                        }

                        for (idx in 0 until starLimit) {
                            val star = staticStars[idx]
                            val basePos = starPositions[idx]
                            val pos = if (parallaxShiftX != 0f || parallaxShiftY != 0f) {
                                Offset(basePos.x + parallaxShiftX * 0.35f, basePos.y + parallaxShiftY * 0.35f)
                            } else basePos

                            val starColor = when {
                                star.isAmberGold -> Color(0xFFD4A338)
                                star.isPlumTint -> Color(0xFF9E7BB5)
                                else -> Color(0xFFEAE4F2)
                            }
                            val alpha = (star.baseAlpha * brightnessFactor * shimmerMult).coerceIn(0.05f, 0.85f)
                            val rPx = star.radiusDp.dp.toPx()

                            drawCircle(
                                color = starColor.copy(alpha = alpha),
                                radius = rPx,
                                center = pos
                            )
                        }
                    }

                    // 3. Uzay Tozu / Parçacıkları (cbSpaceDustEnabled)
                    if (config.cbSpaceDustEnabled && !config.cbLowEffectModeEnabled) {
                        for ((xf, yf, a) in spaceDustPoints) {
                            drawCircle(
                                color = Color(0xFFD4A338).copy(alpha = (a * brightnessFactor * intensityScale).coerceIn(0.02f, 0.18f)),
                                radius = 1.2.dp.toPx(),
                                center = Offset(xf * w - parallaxShiftX * 0.5f, yf * h - parallaxShiftY * 0.5f)
                            )
                        }
                    }

                    // 4. Kayan Yıldız Efekti (cbShootingStarsEnabled)
                    if (config.cbShootingStarsEnabled && animationsAllowed && !config.cbLowEffectModeEnabled) {
                        val shootWindow = (animPhase * 3f) % 1f
                        if (shootWindow <= 0.24f) {
                            val t = (shootWindow / 0.24f).coerceIn(0f, 1f)
                            val sx = w * (0.82f - t * 0.48f)
                            val sy = h * (0.14f + t * 0.22f)
                            val tailX = sx + 38.dp.toPx()
                            val tailY = sy - 18.dp.toPx()
                            val fade = sin(t * PI.toFloat()).coerceIn(0f, 1f) * intensityScale
                            drawLine(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFD4A338).copy(alpha = (0.75f * fade).coerceIn(0f, 1f)),
                                        Color(0xFF58356E).copy(alpha = (0.25f * fade).coerceIn(0f, 1f)),
                                        Color.Transparent
                                    ),
                                    start = Offset(sx, sy),
                                    end = Offset(tailX, tailY)
                                ),
                                start = Offset(sx, sy),
                                end = Offset(tailX, tailY),
                                strokeWidth = 1.6.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // 5. Retro-Fütüristik HUD Çizgileri (cbRetroHudLinesEnabled)
                    if (config.cbRetroHudLinesEnabled) {
                        val hudColor = Color(0xFFD4A338).copy(alpha = (0.24f * brightnessFactor).coerceIn(0.08f, 0.42f))
                        val hudPurple = Color(0xFF58356E).copy(alpha = (0.28f * brightnessFactor).coerceIn(0.08f, 0.45f))
                        val stroke = 1.dp.toPx()
                        val cornerLen = 22.dp.toPx()
                        val pad = 10.dp.toPx()

                        // Sol üst HUD braketi
                        drawLine(hudColor, Offset(pad, pad), Offset(pad + cornerLen, pad), stroke)
                        drawLine(hudColor, Offset(pad, pad), Offset(pad, pad + cornerLen), stroke)
                        // Sağ üst HUD braketi
                        drawLine(hudColor, Offset(w - pad - cornerLen, pad), Offset(w - pad, pad), stroke)
                        drawLine(hudColor, Offset(w - pad, pad), Offset(w - pad, pad + cornerLen), stroke)
                        // İnce yatay telemetri çizgisi
                        drawLine(
                            color = hudPurple,
                            start = Offset(pad + cornerLen + 8.dp.toPx(), pad + 2.dp.toPx()),
                            end = Offset(w - pad - cornerLen - 8.dp.toPx(), pad + 2.dp.toPx()),
                            strokeWidth = stroke * 0.75f
                        )
                    }

                    // 6. İnce Teknik Göstergeler ve Çizgisel Detaylar (cbTechnicalReadoutsEnabled)
                    if (config.cbTechnicalReadoutsEnabled) {
                        val tickColor = Color(0xFFB88728).copy(alpha = (0.28f * brightnessFactor).coerceIn(0.08f, 0.45f))
                        val rightX = w - 14.dp.toPx()
                        val startY = h * 0.32f
                        val tickSpacing = 10.dp.toPx()
                        for (tIdx in 0 until 6) {
                            val ty = startY + tIdx * tickSpacing
                            val tickW = if (tIdx % 2 == 0) 8.dp.toPx() else 4.dp.toPx()
                            drawLine(
                                color = tickColor,
                                start = Offset(rightX - tickW, ty),
                                end = Offset(rightX, ty),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }

                    if (isLoading && animationsAllowed) {
                        drawSpikeLighterAndHorizontalSmoke(
                            canvasWidth = w,
                            canvasHeight = h,
                            cycleProgress = animPhase,
                            reusablePath = reusablePath
                        )
                    }
                }
            }
    )
}

/**
 * BERSERK - GUTS Tam Ekran Albüm Kapağı Behelit Katmanı:
 * Uygulama arka plana alındığında (Lifecycle < STARTED) animasyon döngüsü duraklatılır.
 */
@Composable
fun BerserkStageOverlayCanvas(
    mode: BerserkAnimMode,
    isPlaying: Boolean,
    fftMagnitudes: FloatArray,
    swingAngleDeg: Float,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    if (!lifecycleState.isAtLeast(Lifecycle.State.STARTED)) return

    val awakeningAnim = remember { Animatable(0f) }
    LaunchedEffect(mode) {
        awakeningAnim.snapTo(0f)
        awakeningAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 7000, easing = FastOutSlowInEasing)
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "berserk_stage_fx")
    val tearFlowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "behelit_tear_flow"
    )
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "berserk_pulse_phase"
    )
    val pendulumPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "behelit_long_cord_pendulum"
    )

    val bassEnergy = remember(fftMagnitudes, isPlaying) {
        if (!isPlaying || fftMagnitudes.isEmpty()) 0.25f
        else {
            val n = minOf(6, fftMagnitudes.size)
            var sum = 0f
            for (i in 0 until n) sum += fftMagnitudes[i]
            (sum / n).coerceIn(0.15f, 1f)
        }
    }

    val reusablePath = remember { Path() }
    val cordPath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val center = Offset(w * 0.5f, h * 0.5f)
        val coverHalf = 118.dp.toPx()

        val awake = awakeningAnim.value.coerceIn(0f, 1f)
        val coverTopRightAnchor = Offset(
            x = (center.x + coverHalf - 8.dp.toPx()).coerceAtMost(w * 0.80f),
            y = (center.y - coverHalf + 4.dp.toPx()).coerceAtLeast(14.dp.toPx())
        )
        val screenRightEdgeAnchor = Offset(
            x = w - 2.dp.toPx(),
            y = (center.y - coverHalf - 22.dp.toPx()).coerceAtLeast(8.dp.toPx())
        )

        val continuousPendulumDeg = sin(pendulumPhase) * (14f + 8f * bassEnergy) + swingAngleDeg * 0.85f
        val swayRad = (continuousPendulumDeg * PI / 180.0).toFloat()
        val cordLengthPx = 78.dp.toPx()
        val horizontalAnchorMidX = (coverTopRightAnchor.x + screenRightEdgeAnchor.x) * 0.5f

        val bailCenter = Offset(
            x = (horizontalAnchorMidX + sin(swayRad) * 26.dp.toPx()).coerceIn(coverTopRightAnchor.x - 16.dp.toPx(), w - 36.dp.toPx()),
            y = coverTopRightAnchor.y + cos(swayRad) * cordLengthPx
        )

        drawCircle(
            color = Color(0xFFD4AF37),
            radius = 4.5.dp.toPx(),
            center = coverTopRightAnchor,
            style = Stroke(width = 2.dp.toPx())
        )

        val sagOffset = (18.dp.toPx() + 6.dp.toPx() * cos(swayRad))
        val swayCurveShift = sin(swayRad) * 14.dp.toPx()

        cordPath.reset()
        cordPath.moveTo(coverTopRightAnchor.x, coverTopRightAnchor.y)
        cordPath.quadraticTo(
            (coverTopRightAnchor.x + bailCenter.x) * 0.5f + swayCurveShift * 0.6f,
            (coverTopRightAnchor.y + bailCenter.y) * 0.5f + sagOffset,
            bailCenter.x - 3.dp.toPx(),
            bailCenter.y
        )
        cordPath.moveTo(bailCenter.x + 3.dp.toPx(), bailCenter.y)
        cordPath.quadraticTo(
            (bailCenter.x + screenRightEdgeAnchor.x) * 0.5f + swayCurveShift * 0.6f,
            (bailCenter.y + screenRightEdgeAnchor.y) * 0.5f + sagOffset * 1.15f,
            screenRightEdgeAnchor.x,
            screenRightEdgeAnchor.y
        )

        drawPath(
            path = cordPath,
            color = Color(0xFF1F110B),
            style = Stroke(width = 4.8.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = cordPath,
            color = Color(0xFF7C4A2D),
            style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = cordPath,
            color = Color(0xFFC28E5C).copy(alpha = 0.70f),
            style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round)
        )

        withTransform({
            rotate(degrees = continuousPendulumDeg * 0.55f, pivot = bailCenter)
        }) {
            drawEclipseCryingBehelit(
                bailCenter = bailCenter,
                eggWidth = 64.dp.toPx(),
                eggHeight = 80.dp.toPx(),
                awakeningProgress = awake,
                tearFlowPhase = tearFlowPhase,
                pulsePhase = pulsePhase,
                reusablePath = reusablePath
            )
        }
    }
}

internal fun DrawScope.drawEclipseCryingBehelit(
    bailCenter: Offset,
    eggWidth: Float,
    eggHeight: Float,
    awakeningProgress: Float,
    tearFlowPhase: Float,
    pulsePhase: Float,
    reusablePath: Path
) {
    val awake = awakeningProgress.coerceIn(0f, 1f)
    val tiltDeg = (1f - awake) * -24f
    val eggTop = bailCenter.y + 5.dp.toPx()
    val eggCenter = Offset(bailCenter.x, eggTop + eggHeight * 0.5f)

    withTransform({
        rotate(degrees = tiltDeg, pivot = bailCenter)
    }) {
        val pulseGlow = 0.45f + 0.35f * sin(pulsePhase) + 0.25f * awake
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF1E27).copy(alpha = (0.58f * pulseGlow).coerceIn(0f, 0.85f)),
                    Color(0xFF6B040C).copy(alpha = (0.34f * pulseGlow).coerceIn(0f, 0.55f)),
                    Color.Transparent
                ),
                center = eggCenter,
                radius = eggHeight * 0.95f
            ),
            radius = eggHeight * 0.95f,
            center = eggCenter
        )

        drawCircle(
            color = Color(0xFFD4AF37),
            radius = 5.dp.toPx(),
            center = bailCenter,
            style = Stroke(width = 2.2.dp.toPx())
        )
        val capWidth = eggWidth * 0.38f
        val capHeight = 8.dp.toPx()
        drawOval(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF785312), Color(0xFFFFDF73), Color(0xFF8C6218)),
                startX = bailCenter.x - capWidth * 0.5f,
                endX = bailCenter.x + capWidth * 0.5f
            ),
            topLeft = Offset(bailCenter.x - capWidth * 0.5f, eggTop - 3.dp.toPx()),
            size = Size(capWidth, capHeight)
        )

        val eggLeft = bailCenter.x - eggWidth * 0.5f
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFE32431),
                    Color(0xFFA80B18),
                    Color(0xFF59040B),
                    Color(0xFF2B0104)
                ),
                center = Offset(bailCenter.x - eggWidth * 0.14f, eggTop + eggHeight * 0.32f),
                radius = eggHeight * 0.68f
            ),
            topLeft = Offset(eggLeft, eggTop),
            size = Size(eggWidth, eggHeight)
        )
        drawOval(
            color = Color(0xFF240104),
            topLeft = Offset(eggLeft, eggTop),
            size = Size(eggWidth, eggHeight),
            style = Stroke(width = 1.6.dp.toPx())
        )

        val alignFactor = ((awake - 0.05f) / 0.75f).coerceIn(0f, 1f)
        val eyeOpenFactor = ((awake - 0.35f) / 0.50f).coerceIn(0f, 1f)
        val cryingFactor = ((awake - 0.55f) / 0.45f).coerceIn(0f, 1f)

        val leftEyeCenter = Offset(
            x = lerpFloat(eggCenter.x - eggWidth * 0.27f, eggCenter.x - eggWidth * 0.22f, alignFactor),
            y = lerpFloat(eggTop + eggHeight * 0.27f, eggTop + eggHeight * 0.38f, alignFactor)
        )
        val rightEyeCenter = Offset(
            x = lerpFloat(eggCenter.x + eggWidth * 0.28f, eggCenter.x + eggWidth * 0.22f, alignFactor),
            y = lerpFloat(eggTop + eggHeight * 0.53f, eggTop + eggHeight * 0.38f, alignFactor)
        )
        val noseCenter = Offset(
            x = lerpFloat(eggCenter.x + eggWidth * 0.14f, eggCenter.x, alignFactor),
            y = lerpFloat(eggTop + eggHeight * 0.35f, eggTop + eggHeight * 0.52f, alignFactor)
        )
        val mouthCenter = Offset(
            x = lerpFloat(eggCenter.x - eggWidth * 0.12f, eggCenter.x, alignFactor),
            y = lerpFloat(eggTop + eggHeight * 0.68f, eggTop + eggHeight * 0.74f, alignFactor)
        )

        val eyeW = eggWidth * 0.25f
        val eyeH = eggHeight * (0.035f + 0.12f * eyeOpenFactor)

        listOf(leftEyeCenter, rightEyeCenter).forEachIndexed { idx, eyePos ->
            drawOval(
                color = Color(0xFF210104),
                topLeft = Offset(eyePos.x - eyeW * 0.58f, eyePos.y - eyeH * 0.65f),
                size = Size(eyeW * 1.16f, eyeH * 1.30f)
            )
            if (eyeOpenFactor > 0.08f) {
                drawOval(
                    color = Color(0xFFBAE6FD).copy(alpha = eyeOpenFactor),
                    topLeft = Offset(eyePos.x - eyeW * 0.50f, eyePos.y - eyeH * 0.50f),
                    size = Size(eyeW, eyeH)
                )
                val irisRadius = (eyeH * 0.46f).coerceAtLeast(2.4.dp.toPx())
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00B4FF).copy(alpha = eyeOpenFactor),
                            Color(0xFF0066FF).copy(alpha = eyeOpenFactor),
                            Color(0xFF002B80).copy(alpha = eyeOpenFactor)
                        ),
                        center = eyePos,
                        radius = irisRadius
                    ),
                    radius = irisRadius,
                    center = eyePos
                )
                drawCircle(
                    color = Color(0xFF020617).copy(alpha = eyeOpenFactor),
                    radius = irisRadius * 0.42f,
                    center = eyePos
                )
                drawCircle(
                    color = Color.White.copy(alpha = eyeOpenFactor),
                    radius = irisRadius * 0.28f,
                    center = Offset(eyePos.x - irisRadius * 0.32f, eyePos.y - irisRadius * 0.32f)
                )
                drawCircle(
                    color = Color(0xFF7DD3FC).copy(alpha = eyeOpenFactor * 0.9f),
                    radius = irisRadius * 0.16f,
                    center = Offset(eyePos.x + irisRadius * 0.28f, eyePos.y + irisRadius * 0.25f)
                )
            } else {
                drawLine(
                    color = Color(0xFF240104),
                    start = Offset(eyePos.x - eyeW * 0.48f, eyePos.y),
                    end = Offset(eyePos.x + eyeW * 0.48f, eyePos.y + (if (idx == 0) 2f else -2f)),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        reusablePath.reset()
        reusablePath.moveTo(noseCenter.x, noseCenter.y - eggHeight * 0.08f)
        reusablePath.lineTo(noseCenter.x - eggWidth * 0.08f, noseCenter.y + eggHeight * 0.04f)
        reusablePath.lineTo(noseCenter.x + eggWidth * 0.08f, noseCenter.y + eggHeight * 0.04f)
        reusablePath.close()
        drawPath(
            path = reusablePath,
            color = Color(0xFF3D0207)
        )
        drawCircle(
            color = Color(0xFF1A0003),
            radius = 1.8.dp.toPx(),
            center = Offset(noseCenter.x - eggWidth * 0.04f, noseCenter.y + eggHeight * 0.03f)
        )
        drawCircle(
            color = Color(0xFF1A0003),
            radius = 1.8.dp.toPx(),
            center = Offset(noseCenter.x + eggWidth * 0.04f, noseCenter.y + eggHeight * 0.03f)
        )

        val mouthW = eggWidth * (0.34f + 0.14f * eyeOpenFactor)
        val mouthH = eggHeight * (0.05f + 0.17f * eyeOpenFactor)
        val mouthTopLeft = Offset(mouthCenter.x - mouthW * 0.5f, mouthCenter.y - mouthH * 0.5f)

        drawOval(
            color = Color(0xFF1F0004),
            topLeft = mouthTopLeft,
            size = Size(mouthW, mouthH)
        )
        if (eyeOpenFactor > 0.2f) {
            drawOval(
                color = Color(0xFF991B1B).copy(alpha = eyeOpenFactor),
                topLeft = Offset(mouthCenter.x - mouthW * 0.30f, mouthCenter.y + mouthH * 0.05f),
                size = Size(mouthW * 0.60f, mouthH * 0.40f)
            )
            val teethCount = 5
            val toothW = (mouthW * 0.72f) / teethCount
            val startToothX = mouthCenter.x - (mouthW * 0.36f)
            for (t in 0 until teethCount) {
                val tx = startToothX + t * toothW
                drawRoundRect(
                    color = Color(0xFFF1F5F9).copy(alpha = eyeOpenFactor * 0.92f),
                    topLeft = Offset(tx + 0.8f, mouthTopLeft.y + 1.5.dp.toPx()),
                    size = Size(toothW - 1.6f, mouthH * 0.24f),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFFE2E8F0).copy(alpha = eyeOpenFactor * 0.85f),
                    topLeft = Offset(tx + 0.8f, mouthTopLeft.y + mouthH * 0.72f),
                    size = Size(toothW - 1.6f, mouthH * 0.22f),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
            }
        }
        drawOval(
            color = Color(0xFF4C0309),
            topLeft = mouthTopLeft,
            size = Size(mouthW, mouthH),
            style = Stroke(width = 2.dp.toPx())
        )

        if (cryingFactor > 0.02f) {
            val eggBottomY = eggTop + eggHeight
            val streamLengthLeft = (eggBottomY - leftEyeCenter.y + 18.dp.toPx()) * cryingFactor
            val streamLengthRight = (eggBottomY - rightEyeCenter.y + 18.dp.toPx()) * cryingFactor

            val eyeTearOrigins = listOf(
                leftEyeCenter.copy(y = leftEyeCenter.y + eyeH * 0.42f) to streamLengthLeft,
                rightEyeCenter.copy(y = rightEyeCenter.y + eyeH * 0.42f) to streamLengthRight
            )

            eyeTearOrigins.forEachIndexed { eyeIdx, (origin, maxLen) ->
                drawOval(
                    color = Color(0xFF8B0000).copy(alpha = cryingFactor),
                    topLeft = Offset(origin.x - eyeW * 0.42f, origin.y - 2.dp.toPx()),
                    size = Size(eyeW * 0.84f, 5.5.dp.toPx())
                )

                for (branch in 0..1) {
                    val branchOffset = if (branch == 0) 0f else (if (eyeIdx == 0) -4.dp.toPx() else 4.dp.toPx())
                    val strokeW = if (branch == 0) 4.2.dp.toPx() else 2.2.dp.toPx()
                    val curLen = if (branch == 0) maxLen else maxLen * 0.78f

                    reusablePath.reset()
                    reusablePath.moveTo(origin.x + branchOffset, origin.y)

                    val segments = 5
                    for (seg in 1..segments) {
                        val frac = seg.toFloat() / segments.toFloat()
                        val py = origin.y + curLen * frac
                        val curveToChin = sin(frac * PI.toFloat()) * (if (eyeIdx == 0) -3.dp.toPx() else 3.dp.toPx()) +
                            sin(tearFlowPhase * 2f * PI.toFloat() + frac * 4f + eyeIdx) * 1.4.dp.toPx()
                        val px = origin.x + branchOffset + curveToChin
                        reusablePath.lineTo(px, py)
                    }

                    drawPath(
                        path = reusablePath,
                        color = Color(0xFF4A0004).copy(alpha = cryingFactor),
                        style = Stroke(width = strokeW + 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    drawPath(
                        path = reusablePath,
                        color = Color(0xFFD90416).copy(alpha = cryingFactor),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    drawPath(
                        path = reusablePath,
                        color = Color(0xFFFF8A95).copy(alpha = cryingFactor * 0.70f),
                        style = Stroke(width = strokeW * 0.32f, cap = StrokeCap.Round)
                    )
                }

                for (dropIdx in 0..2) {
                    val dropProgress = ((tearFlowPhase + dropIdx * 0.33f + eyeIdx * 0.17f) % 1f)
                    val dropY = origin.y + dropProgress * (maxLen + 16.dp.toPx())
                    val dropX = origin.x + sin(dropProgress * PI.toFloat()) * (if (eyeIdx == 0) -2.dp.toPx() else 2.dp.toPx())
                    val dropRadius = (2.8f + (1f - dropProgress) * 1.8f).dp.toPx() * cryingFactor

                    drawOval(
                        color = Color(0xFF6B0006).copy(alpha = cryingFactor),
                        topLeft = Offset(dropX - dropRadius, dropY - dropRadius * 1.3f),
                        size = Size(dropRadius * 2f, dropRadius * 2.6f)
                    )
                    drawOval(
                        color = Color(0xFFFF1E27).copy(alpha = cryingFactor),
                        topLeft = Offset(dropX - dropRadius * 0.75f, dropY - dropRadius),
                        size = Size(dropRadius * 1.5f, dropRadius * 2.0f)
                    )
                    drawCircle(
                        color = Color(0xFFFFB3B8).copy(alpha = cryingFactor * 0.85f),
                        radius = dropRadius * 0.35f,
                        center = Offset(dropX - dropRadius * 0.25f, dropY - dropRadius * 0.3f)
                    )
                }
            }

            val poolCenterY = eggBottomY + 20.dp.toPx()
            for (rIdx in 0..3) {
                val ripplePhase = ((tearFlowPhase + rIdx * 0.25f) % 1f)
                val rippleW = (eggWidth * (0.45f + ripplePhase * 0.85f)) * cryingFactor
                val rippleY = poolCenterY + rIdx * 4.5.dp.toPx()
                val rippleAlpha = ((1f - ripplePhase) * 0.75f * cryingFactor).coerceIn(0f, 0.8f)

                drawLine(
                    color = Color(0xFFFF1E27).copy(alpha = rippleAlpha),
                    start = Offset(bailCenter.x - rippleW * 0.5f, rippleY),
                    end = Offset(bailCenter.x + rippleW * 0.5f, rippleY),
                    strokeWidth = (2.2f - rIdx * 0.35f).dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction.coerceIn(0f, 1f)
}
