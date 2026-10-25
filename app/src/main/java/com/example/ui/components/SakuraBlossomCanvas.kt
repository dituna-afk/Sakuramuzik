package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.SakuraTreeStyle
import com.example.ui.animation.LocalSakuraAnimationState
import com.example.ui.theme.AppTheme
import com.example.ui.theme.LocalAppThemePalette
import com.example.ui.theme.isSakuraTheme
import com.example.ui.theme.isSuperOptimizedTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class SakuraPetal(
    val xFraction: Float,
    val initialYFraction: Float,
    val sizeDp: Float,
    val speedFactor: Float,
    val swayAmplitude: Float,
    val swayPhase: Float,
    val rotationSpeed: Float,
    val alpha: Float,
    val useSecondaryColor: Boolean
)

private data class BurstPetal(
    val angleRad: Float,
    val distanceFactor: Float,
    val sizeDp: Float,
    val rotationDeg: Float,
    val useSecondaryColor: Boolean
)

private data class NoiseGrainDot(
    val xFrac: Float,
    val yFrac: Float,
    val radiusPx: Float,
    val alpha: Float,
    val isGoldTint: Boolean
)

private data class FixedSpaceStar(
    val xFraction: Float,
    val yFraction: Float,
    val sizeDp: Float,
    val twinkleSpeed: Float,
    val twinklePhase: Float,
    val fadeDelayFraction: Float,
    val isIceBlue: Boolean
)

@Composable
fun FloatingSakuraPetalsOverlay(
    modifier: Modifier = Modifier,
    petalCount: Int? = null,
    treeStyleOverride: SakuraTreeStyle? = null,
    isLoading: Boolean = false,
    isScrolling: Boolean = false,
    isPlaying: Boolean = false
) {
    val palette = LocalAppThemePalette.current
    val treeStyle = treeStyleOverride ?: com.example.data.LocalSakuraTreeStyle.current
    val animState = LocalSakuraAnimationState.current

    val isSakura = palette.theme.isSakuraTheme()
    val isCowboySpike = palette.theme == AppTheme.COWBOY_BEBOP_SPIKE

    // Berserk temalarında ekranda uçuşan parçacık veya flaş çakan efektler tamamen kaldırıldı
    if (!isSakura && !isCowboySpike) return

    // Cowboy Bebop: 96 sabit uzay yıldızı Modifier.drawWithCache ile önbelleklenir ve 31 bağımsız ayara uyar
    if (isCowboySpike) {
        val bebopConfig = com.example.ui.theme.LocalCowboyBebopConfig.current
        CowboyBebopSpaceAndHudOverlay(
            config = bebopConfig,
            isScrolling = isScrolling,
            isPlaying = isPlaying,
            isLoading = isLoading,
            modifier = modifier
        )
        return
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    if (!lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return

    if (!animState.config.masterEnabled || !animState.config.floatingPetalsEnabled) return

    val rawCount = petalCount ?: animState.config.effectiveAmbientPetalCount(
        animState.isSystemPowerSaveMode
    )
    val effectiveCount = rawCount
    if (effectiveCount <= 0) return

    val primaryPetalColor = if (treeStyle != SakuraTreeStyle.TRADITIONAL_SAKURA) {
        treeStyle.petalPrimary
    } else {
        palette.petalPrimary
    }

    val secondaryPetalColor = if (treeStyle != SakuraTreeStyle.TRADITIONAL_SAKURA) {
        treeStyle.petalSecondary
    } else {
        palette.petalSecondary
    }

    val petals = remember(effectiveCount, palette.theme) {
        val rng = Random(7721 + palette.theme.ordinal * 31)
        List(effectiveCount) {
            SakuraPetal(
                xFraction = rng.nextFloat(),
                initialYFraction = rng.nextFloat(),
                sizeDp = 7f + rng.nextFloat() * 9f,
                speedFactor = 0.45f + rng.nextFloat() * 0.85f,
                swayAmplitude = 16f + rng.nextFloat() * 32f,
                swayPhase = rng.nextFloat() * (2f * PI.toFloat()),
                rotationSpeed = (rng.nextFloat() - 0.5f) * 260f,
                alpha = 0.30f + rng.nextFloat() * 0.50f,
                useSecondaryColor = rng.nextBoolean()
            )
        }
    }

    // Kaydırma sırasında (isScrolling) sonsuz animasyon döngüsü çalıştırılmaz, son kare sabit tutulur
    var frozenProgress by remember { mutableFloatStateOf(0.25f) }
    val progress = if (isScrolling) {
        frozenProgress
    } else {
        val transition = rememberInfiniteTransition(label = "sakura_petals_fall")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 24_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "petal_progress"
        )
        frozenProgress = animated
        animated
    }

    val swayMult = animState.config.effectIntensity.windSwayMultiplier
    val reusablePetalPath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Yalnızca Sakura temalarında kiraz çiçeği yaprakları süzülür
        for (i in 0 until petals.size) {
            val p = petals[i]
            val yNorm = (p.initialYFraction + progress * p.speedFactor) % 1f
            val y = yNorm * (h + 60f) - 30f
            val sway = sin((progress * 2f * PI.toFloat() * p.speedFactor) + p.swayPhase) * p.swayAmplitude * swayMult
            val x = (p.xFraction * w + sway + w) % w
            val angle = (progress * p.rotationSpeed) % 360f
            val color = if (p.useSecondaryColor) secondaryPetalColor else primaryPetalColor

            drawSakuraPetalShape(
                center = Offset(x, y),
                radius = p.sizeDp.dp.toPx(),
                rotationDegrees = angle,
                color = color.copy(alpha = p.alpha),
                reusablePath = reusablePetalPath
            )
        }
    }
}

/**
 * 3. COWBOY BEBOP - FAYE VALENTINE:
 * Ekranın alt köşelerinden ağır ağır yükselen, kalın, kavisli (Bezier eğrileriyle çizilmiş)
 * ve loş ışıklı sigara dumanı. Duman yükseldikçe genişler ve saydamlaşır.
 */
internal fun DrawScope.drawJazzClubRisingSmokePlume(
    originX: Float,
    originY: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    progress: Float,
    directionSign: Float,
    reusablePath: Path
) {
    val bands = 7
    val maxRiseHeight = canvasHeight * 0.82f
    val phaseRad = progress * 2f * PI.toFloat()

    for (b in 0 until bands) {
        val t0 = b.toFloat() / bands.toFloat()
        val t1 = (b + 1).toFloat() / bands.toFloat()
        val tMid = (t0 + t1) * 0.5f

        val y0 = originY - t0 * maxRiseHeight
        val y1 = originY - t1 * maxRiseHeight
        val yCtrl1 = y0 - (y0 - y1) * 0.35f
        val yCtrl2 = y0 - (y0 - y1) * 0.70f

        // Yükseldikçe artan kavis salınımı
        val drift0 = directionSign * (t0 * canvasWidth * 0.18f) +
            sin(phaseRad + t0 * 4.2f) * (12.dp.toPx() + t0 * 34.dp.toPx())
        val drift1 = directionSign * (t1 * canvasWidth * 0.18f) +
            sin(phaseRad + t1 * 4.2f) * (12.dp.toPx() + t1 * 34.dp.toPx())
        val ctrlDrift1 = directionSign * (tMid * canvasWidth * 0.18f) +
            cos(phaseRad + t0 * 4.8f) * (18.dp.toPx() + tMid * 42.dp.toPx())
        val ctrlDrift2 = directionSign * (tMid * canvasWidth * 0.18f) -
            sin(phaseRad + t1 * 3.6f) * (18.dp.toPx() + tMid * 42.dp.toPx())

        reusablePath.reset()
        reusablePath.moveTo(originX + drift0, y0)
        reusablePath.cubicTo(
            originX + ctrlDrift1, yCtrl1,
            originX + ctrlDrift2, yCtrl2,
            originX + drift1, y1
        )

        // Duman yükseldikçe genişler (strokeWidth 7.dp -> 34.dp) ve saydamlaşır (alpha 0.28f -> 0.03f)
        val expandingWidth = (7f + tMid * 27f).dp.toPx()
        val fadingAlpha = ((1f - tMid * 0.88f) * 0.26f).coerceIn(0.02f, 0.28f)

        // Dış loş caz kulübü duman katmanı
        drawPath(
            path = reusablePath,
            color = Color(0xFFC7A882).copy(alpha = fadingAlpha),
            style = Stroke(width = expandingWidth, cap = StrokeCap.Round)
        )
        // İç yumuşak duman damarı
        drawPath(
            path = reusablePath,
            color = Color(0xFFE2E8F0).copy(alpha = fadingAlpha * 0.65f),
            style = Stroke(width = expandingWidth * 0.48f, cap = StrokeCap.Round)
        )
    }
}

/**
 * 2. COWBOY BEBOP - SPIKE SPIEGEL:
 * Şarkı yüklenirken (Loading), ekranın sağ alt köşesinde anlık parlayıp sönen sarı bir çakmak alevi belirir.
 * Ardından o noktadan sola doğru yatay eksende uzayan, incecik ve uçlara doğru hafifçe dalgalanarak
 * eriyen gri sigara dumanı (Path animasyonu) çizilir. (KIRMIZI KESİNLİKLE YASAK).
 */
internal fun DrawScope.drawSpikeLighterAndHorizontalSmoke(
    canvasWidth: Float,
    canvasHeight: Float,
    cycleProgress: Float,
    reusablePath: Path
) {
    val anchorX = canvasWidth - 26.dp.toPx()
    val anchorY = canvasHeight - 24.dp.toPx()

    // Aşama 1 (0.00 .. 0.28): Sağ alt köşede anlık parlayıp sönen sarı/kehribar çakmak alevi (Kırmızı yok!)
    if (cycleProgress <= 0.28f) {
        val flameNorm = (cycleProgress / 0.28f).coerceIn(0f, 1f)
        val flamePulse = sin(flameNorm * PI.toFloat()).coerceIn(0f, 1f)
        val flameHeight = (18.dp.toPx() * (0.65f + 0.35f * flamePulse))
        val flameWidth = (7.dp.toPx() * (0.75f + 0.25f * flamePulse))

        // Sarı/Kehribar dış çakmak parlaması
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFDE047).copy(alpha = 0.78f * flamePulse),
                    Color(0xFFF59E0B).copy(alpha = 0.35f * flamePulse),
                    Color.Transparent
                ),
                center = Offset(anchorX, anchorY - flameHeight * 0.4f),
                radius = flameHeight * 1.4f
            ),
            radius = flameHeight * 1.4f,
            center = Offset(anchorX, anchorY - flameHeight * 0.4f)
        )

        // Çakmak alevi damla formu (Sarı & Beyaz çekirdek)
        reusablePath.reset()
        reusablePath.moveTo(anchorX, anchorY)
        reusablePath.cubicTo(
            anchorX - flameWidth, anchorY - flameHeight * 0.35f,
            anchorX - flameWidth * 0.5f, anchorY - flameHeight * 0.85f,
            anchorX, anchorY - flameHeight
        )
        reusablePath.cubicTo(
            anchorX + flameWidth * 0.5f, anchorY - flameHeight * 0.85f,
            anchorX + flameWidth, anchorY - flameHeight * 0.35f,
            anchorX, anchorY
        )
        reusablePath.close()
        drawPath(
            path = reusablePath,
            color = Color(0xFFF59E0B).copy(alpha = 0.90f * flamePulse)
        )
        drawCircle(
            color = Color(0xFFFEF08A).copy(alpha = 0.95f * flamePulse),
            radius = flameWidth * 0.45f,
            center = Offset(anchorX, anchorY - flameHeight * 0.28f)
        )
    }

    // Aşama 2 (0.12 .. 1.00): Çakmak noktasından SOLA DOĞRU yatay eksende uzayan, incecik ve uçlara doğru dalgalanarak eriyen gri sigara dumanı
    if (cycleProgress >= 0.12f) {
        val smokeNorm = ((cycleProgress - 0.12f) / 0.88f).coerceIn(0f, 1f)
        val maxHorizontalSpan = (canvasWidth * 0.76f).coerceAtLeast(120.dp.toPx())
        val currentSpan = maxHorizontalSpan * smokeNorm
        val originSmokeY = anchorY - 10.dp.toPx()

        val segments = 6
        for (s in 0 until segments) {
            val f0 = s.toFloat() / segments.toFloat()
            val f1 = (s + 1).toFloat() / segments.toFloat()
            val fMid = (f0 + f1) * 0.5f

            val x0 = anchorX - f0 * currentSpan
            val x1 = anchorX - f1 * currentSpan
            val xCtrl1 = x0 - (x0 - x1) * 0.33f
            val xCtrl2 = x0 - (x0 - x1) * 0.66f

            // Uçlara (sola) doğru hafifçe artan dalgalanma
            val waveAmp0 = f0 * 11.dp.toPx()
            val waveAmp1 = f1 * 11.dp.toPx()
            val wavePhase = cycleProgress * 4f * PI.toFloat()

            val y0 = originSmokeY + sin(wavePhase + f0 * 6.5f) * waveAmp0 - (f0 * 4.dp.toPx())
            val y1 = originSmokeY + sin(wavePhase + f1 * 6.5f) * waveAmp1 - (f1 * 4.dp.toPx())
            val yCtrl1 = originSmokeY + cos(wavePhase + fMid * 6.5f) * ((waveAmp0 + waveAmp1) * 0.6f) - (fMid * 4.dp.toPx())
            val yCtrl2 = originSmokeY - sin(wavePhase + fMid * 6.5f) * ((waveAmp0 + waveAmp1) * 0.6f) - (fMid * 4.dp.toPx())

            reusablePath.reset()
            reusablePath.moveTo(x0, y0)
            reusablePath.cubicTo(xCtrl1, yCtrl1, xCtrl2, yCtrl2, x1, y1)

            // Uçlara doğru eriyen (saydamlaşan) ve incecik gri sigara dumanı
            val tipDissolve = (1f - fMid).coerceIn(0f, 1f)
            val lifeFade = if (smokeNorm > 0.75f) ((1f - smokeNorm) / 0.25f).coerceIn(0f, 1f) else 1f
            val segAlpha = (0.62f * tipDissolve * lifeFade).coerceIn(0f, 0.65f)
            val segStroke = (1.6f - fMid * 0.7f).coerceAtLeast(0.75f).dp.toPx()

            drawPath(
                path = reusablePath,
                color = Color(0xFF94A3B8).copy(alpha = segAlpha),
                style = Stroke(width = segStroke, cap = StrokeCap.Round)
            )
            // İkinci ince gümüş-gri duman teli
            drawPath(
                path = reusablePath,
                color = Color(0xFFCBD5E1).copy(alpha = segAlpha * 0.55f),
                style = Stroke(width = segStroke * 0.55f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun SakuraBurstEffect(
    triggerKey: Int,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    if (palette.theme == AppTheme.BERSERK_GRIFFITH || palette.theme.isSuperOptimizedTheme()) return
    val animState = LocalSakuraAnimationState.current
    val count = animState.config.effectiveBurstCount(animState.isSystemPowerSaveMode)
    if (triggerKey <= 0 || count <= 0) return

    var burstProgress by remember(triggerKey) { mutableFloatStateOf(0f) }
    val particles = remember(triggerKey, count) {
        val rng = Random(triggerKey * 131)
        List(count) { i ->
            val baseAngle = (2.0 * PI * i / count).toFloat()
            BurstPetal(
                angleRad = baseAngle + (rng.nextFloat() - 0.5f) * 0.35f,
                distanceFactor = 0.55f + rng.nextFloat() * 0.65f,
                sizeDp = 6f + rng.nextFloat() * 6f,
                rotationDeg = rng.nextFloat() * 360f,
                useSecondaryColor = rng.nextBoolean()
            )
        }
    }

    LaunchedEffect(triggerKey) {
        val startNanos = withFrameNanos { it }
        val durationNanos = 650_000_000L
        while (burstProgress < 1f) {
            val now = withFrameNanos { it }
            burstProgress = ((now - startNanos).toFloat() / durationNanos.toFloat()).coerceIn(0f, 1f)
        }
    }

    if (burstProgress >= 1f) return
    val reusablePetalPath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension * 0.46f
        val alpha = (1f - burstProgress).coerceIn(0f, 1f)

        for (i in 0 until particles.size) {
            val p = particles[i]
            val dist = maxRadius * p.distanceFactor * burstProgress
            val pos = Offset(
                x = center.x + cos(p.angleRad) * dist,
                y = center.y + sin(p.angleRad) * dist
            )
            val color = if (p.useSecondaryColor) palette.petalSecondary else palette.primaryAccent
            drawThemeParticleShape(
                theme = palette.theme,
                center = pos,
                radius = p.sizeDp.dp.toPx() * (1f - burstProgress * 0.35f),
                rotationDegrees = p.rotationDeg + burstProgress * 160f,
                color = color.copy(alpha = alpha),
                secondaryColor = palette.secondaryAccent,
                reusablePath = reusablePetalPath
            )
        }
    }
}

@Composable
fun SakuraFlowerEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    tint: Color? = null
) {
    val palette = LocalAppThemePalette.current
    val primaryColor = tint ?: palette.primaryAccent
    val centerColor = palette.secondaryAccent
    val reusablePetalPath = remember { Path() }

    Canvas(modifier = modifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val petalLen = this.size.minDimension * 0.36f
        for (i in 0 until 5) {
            val angle = i * 72f - 90f
            withTransform({
                rotate(degrees = angle, pivot = c)
            }) {
                drawSakuraPetalShape(
                    center = Offset(c.x, c.y - petalLen * 0.65f),
                    radius = petalLen * 0.58f,
                    rotationDegrees = 0f,
                    color = primaryColor,
                    reusablePath = reusablePetalPath
                )
            }
        }
        drawCircle(
            color = centerColor,
            radius = this.size.minDimension * 0.11f,
            center = c
        )
    }
}

@Composable
fun SakuraBranchHeaderDecoration(
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    val reusableBranchPath = remember { Path() }
    val reusablePetalPath = remember { Path() }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        reusableBranchPath.reset()
        reusableBranchPath.moveTo(w * 0.98f, h * 0.12f)
        reusableBranchPath.cubicTo(
            w * 0.82f, h * 0.25f,
            w * 0.68f, h * 0.18f,
            w * 0.52f, h * 0.42f
        )
        drawPath(
            path = reusableBranchPath,
            color = palette.primaryAccent.copy(alpha = 0.26f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        val blossomCenters = listOf(
            Offset(w * 0.88f, h * 0.20f),
            Offset(w * 0.74f, h * 0.24f),
            Offset(w * 0.60f, h * 0.34f)
        )
        blossomCenters.forEachIndexed { idx, pos ->
            drawSakuraPetalShape(
                center = pos,
                radius = (8 - idx).dp.toPx(),
                rotationDegrees = idx * 42f,
                color = palette.petalPrimary.copy(alpha = 0.45f),
                reusablePath = reusablePetalPath
            )
        }
    }
}

internal data class TouchBlossomParticle(
    val angleRad: Float,
    val speedPxPerSec: Float,
    val sizeDp: Float,
    val baseRotationDeg: Float,
    val spinDegPerSec: Float,
    val isFullFlower: Boolean,
    val useSecondaryColor: Boolean
)

internal data class TouchBlossomBurst(
    val id: Long,
    val originX: Float,
    val originY: Float,
    val startTimeNanos: Long,
    val particles: List<TouchBlossomParticle>
)

class TouchSakuraBurstController {
    internal val bursts = mutableStateListOf<TouchBlossomBurst>()
    private var nextId = 1L
    private var lastSpawnUptimeMs = 0L

    fun clear() {
        bursts.clear()
    }

    fun spawnAt(x: Float, y: Float, isDrag: Boolean = false) {
        if (isDrag) return
        val nowMs = android.os.SystemClock.uptimeMillis()
        if (nowMs - lastSpawnUptimeMs < 140L) return
        lastSpawnUptimeMs = nowMs

        val seed = (nextId * 997L) xor System.nanoTime()
        val rng = Random(seed)
        val count = 5
        val particleList = List(count) { idx ->
            val baseAngle = (2.0 * PI * idx / count).toFloat()
            TouchBlossomParticle(
                angleRad = baseAngle + (rng.nextFloat() - 0.5f) * 0.40f,
                speedPxPerSec = 70f + rng.nextFloat() * 110f,
                sizeDp = 6.5f + rng.nextFloat() * 4.0f,
                baseRotationDeg = rng.nextFloat() * 360f,
                spinDegPerSec = (rng.nextFloat() - 0.5f) * 240f,
                isFullFlower = (idx % 2 == 0),
                useSecondaryColor = rng.nextBoolean()
            )
        }

        if (bursts.size >= 3) {
            bursts.removeAt(0)
        }
        bursts.add(
            TouchBlossomBurst(
                id = nextId++,
                originX = x,
                originY = y,
                startTimeNanos = System.nanoTime(),
                particles = particleList
            )
        )
    }
}

@Composable
fun rememberTouchSakuraBurstController(): TouchSakuraBurstController {
    return remember { TouchSakuraBurstController() }
}

@Composable
fun TouchSakuraBlossomOverlay(
    controller: TouchSakuraBurstController,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    if (!enabled || palette.theme == AppTheme.BERSERK_GRIFFITH || palette.theme.isSuperOptimizedTheme()) {
        if (controller.bursts.isNotEmpty()) {
            controller.clear()
        }
        return
    }
    if (controller.bursts.isEmpty()) return

    val treeStyle = com.example.data.LocalSakuraTreeStyle.current
    val primaryColor = if (palette.theme.isSakuraTheme() && treeStyle != SakuraTreeStyle.TRADITIONAL_SAKURA) {
        treeStyle.petalPrimary
    } else {
        palette.petalPrimary
    }
    val secondaryColor = if (palette.theme.isSakuraTheme() && treeStyle != SakuraTreeStyle.TRADITIONAL_SAKURA) {
        treeStyle.petalSecondary
    } else {
        palette.petalSecondary
    }
    val centerStamenColor = palette.secondaryAccent

    var frameTimeNanos by remember { androidx.compose.runtime.mutableLongStateOf(System.nanoTime()) }

    LaunchedEffect(controller.bursts.size, enabled) {
        if (!enabled) {
            controller.clear()
            return@LaunchedEffect
        }
        val maxDurationNanos = 600_000_000L
        while (enabled && controller.bursts.isNotEmpty()) {
            withFrameNanos { now ->
                frameTimeNanos = now
                controller.bursts.removeAll { burst ->
                    (now - burst.startTimeNanos) >= maxDurationNanos
                }
            }
        }
    }

    val reusablePetalPath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val durationSec = 0.85f
        val gravityPx = 95.dp.toPx()

        for (bIdx in 0 until controller.bursts.size) {
            val burst = controller.bursts[bIdx]
            val elapsedSec = ((frameTimeNanos - burst.startTimeNanos).coerceAtLeast(0L)) / 1_000_000_000f
            val progress = (elapsedSec / durationSec).coerceIn(0f, 1f)
            if (progress >= 1f) continue

            val easeOut = 1f - (1f - progress) * (1f - progress)
            val alpha = (1f - progress * progress).coerceIn(0f, 1f)

            val centerScale = (0.35f + easeOut * 0.85f) * (1f - progress * 0.45f)
            val centerRadius = 12.dp.toPx() * centerScale
            drawThemeCentralBurst(
                theme = palette.theme,
                center = Offset(burst.originX, burst.originY),
                radius = centerRadius,
                rotationDeg = progress * 90f,
                primaryColor = primaryColor.copy(alpha = (alpha * 0.85f).coerceIn(0f, 1f)),
                secondaryColor = centerStamenColor.copy(alpha = alpha),
                reusablePath = reusablePetalPath
            )

            val particles = burst.particles
            for (pIdx in 0 until particles.size) {
                val p = particles[pIdx]
                val dist = p.speedPxPerSec * easeOut * durationSec
                val x = burst.originX + cos(p.angleRad) * dist
                val y = burst.originY + sin(p.angleRad) * dist + (gravityPx * progress * progress * 0.55f)
                val rot = p.baseRotationDeg + elapsedSec * p.spinDegPerSec
                val pColor = (if (p.useSecondaryColor) secondaryColor else primaryColor)
                    .copy(alpha = alpha)
                val radiusPx = p.sizeDp.dp.toPx() * (1f - progress * 0.3f)

                if (p.isFullFlower && palette.theme.isSakuraTheme()) {
                    drawFivePetalCherryBlossom(
                        center = Offset(x, y),
                        petalRadius = radiusPx * 0.65f,
                        rotationDeg = rot,
                        petalColor = pColor,
                        centerColor = centerStamenColor.copy(alpha = alpha * 0.9f),
                        reusablePath = reusablePetalPath
                    )
                } else {
                    drawThemeParticleShape(
                        theme = palette.theme,
                        center = Offset(x, y),
                        radius = radiusPx,
                        rotationDegrees = rot,
                        color = pColor,
                        secondaryColor = secondaryColor,
                        reusablePath = reusablePetalPath
                    )
                }
            }
        }
    }
}

internal fun DrawScope.drawThemeCentralBurst(
    theme: AppTheme,
    center: Offset,
    radius: Float,
    rotationDeg: Float,
    primaryColor: Color,
    secondaryColor: Color,
    reusablePath: Path
) {
    if (radius <= 0.5f) return
    if (theme.isSakuraTheme()) {
        drawFivePetalCherryBlossom(
            center = center,
            petalRadius = radius,
            rotationDeg = rotationDeg,
            petalColor = primaryColor,
            centerColor = secondaryColor,
            reusablePath = reusablePath
        )
    } else {
        drawThemeParticleShape(
            theme = theme,
            center = center,
            radius = radius * 1.35f,
            rotationDegrees = rotationDeg,
            color = primaryColor,
            secondaryColor = secondaryColor,
            reusablePath = reusablePath
        )
    }
}

internal fun DrawScope.drawThemeParticleShape(
    theme: AppTheme,
    center: Offset,
    radius: Float,
    rotationDegrees: Float,
    color: Color,
    secondaryColor: Color,
    reusablePath: Path
) {
    if (radius <= 0.5f) return
    when {
        theme.isSakuraTheme() -> {
            drawSakuraPetalShape(
                center = center,
                radius = radius,
                rotationDegrees = rotationDegrees,
                color = color,
                reusablePath = reusablePath
            )
        }
        theme == AppTheme.BERSERK_GUTS -> {
            // 1. BERSERK - GUTS: Kor halinde yanan kızıl-turuncu köz (ember) parçacığı
            rotate(degrees = rotationDegrees, pivot = center) {
                // Köz dış kızıl hale
                drawCircle(
                    color = Color(0xFFFF1E27).copy(alpha = (color.alpha * 0.35f).coerceIn(0f, 1f)),
                    radius = radius * 1.55f,
                    center = center
                )
                reusablePath.reset()
                val h = radius * 1.55f
                val w = radius * 0.72f
                reusablePath.moveTo(center.x, center.y - h)
                reusablePath.lineTo(center.x + w, center.y - h * 0.1f)
                reusablePath.lineTo(center.x + w * 0.45f, center.y + h * 0.7f)
                reusablePath.lineTo(center.x, center.y + h)
                reusablePath.lineTo(center.x - w * 0.45f, center.y + h * 0.7f)
                reusablePath.lineTo(center.x - w, center.y - h * 0.1f)
                reusablePath.close()
                drawPath(path = reusablePath, color = color)
                // Erimiş kor turuncu çekirdek
                drawCircle(
                    color = Color(0xFFFF7A00).copy(alpha = color.alpha),
                    radius = radius * 0.46f,
                    center = center
                )
                // Akkor sarı-beyaz kıvılcım merkezi
                drawCircle(
                    color = Color(0xFFFFF7ED).copy(alpha = (color.alpha * 0.92f).coerceIn(0f, 1f)),
                    radius = radius * 0.20f,
                    center = center
                )
            }
        }
        theme == AppTheme.BERSERK_GRIFFITH -> {
            // Berserk Griffith: Celestial white & silver falcon feather
            rotate(degrees = rotationDegrees, pivot = center) {
                reusablePath.reset()
                val len = radius * 1.8f
                val width = radius * 0.55f
                reusablePath.moveTo(center.x, center.y - len)
                reusablePath.cubicTo(
                    center.x + width, center.y - len * 0.5f,
                    center.x + width * 0.9f, center.y + len * 0.3f,
                    center.x, center.y + len
                )
                reusablePath.cubicTo(
                    center.x - width * 0.6f, center.y + len * 0.3f,
                    center.x - width * 0.8f, center.y - len * 0.5f,
                    center.x, center.y - len
                )
                reusablePath.close()
                drawPath(path = reusablePath, color = color)
                drawLine(
                    color = Color.White.copy(alpha = color.alpha * 0.9f),
                    start = Offset(center.x, center.y - len * 0.88f),
                    end = Offset(center.x, center.y + len * 0.92f),
                    strokeWidth = (radius * 0.14f).coerceAtLeast(1f)
                )
            }
        }
        theme == AppTheme.COWBOY_BEBOP_SPIKE -> {
            // 2. COWBOY BEBOP SPIKE: İrili ufaklı mavi ve beyaz yaşayan uzay yıldızları (KIRMIZI KESİNLİKLE YASAK)
            val isBlueStar = abs(rotationDegrees.toInt()) % 2 == 0
            val starColor = if (isBlueStar) Color(0xFF38BDF8) else Color(0xFFFFFFFF)
            val glowColor = if (isBlueStar) Color(0xFF7DD3FC) else Color(0xFFE2E8F0)

            // Yıldız twinkle halesi
            drawCircle(
                color = glowColor.copy(alpha = (color.alpha * 0.28f).coerceIn(0f, 1f)),
                radius = radius * 1.55f,
                center = center
            )

            rotate(degrees = rotationDegrees * 0.25f, pivot = center) {
                reusablePath.reset()
                val rOuter = radius * 1.45f
                val rInner = radius * 0.26f
                for (s in 0 until 4) {
                    val ang = (s * 90.0 * PI / 180.0).toFloat()
                    val pOutX = center.x + rOuter * cos(ang)
                    val pOutY = center.y + rOuter * sin(ang)
                    val pInX = center.x + rInner * cos(ang + 45f * PI.toFloat() / 180f)
                    val pInY = center.y + rInner * sin(ang + 45f * PI.toFloat() / 180f)
                    if (s == 0) reusablePath.moveTo(pOutX, pOutY) else reusablePath.lineTo(pOutX, pOutY)
                    reusablePath.lineTo(pInX, pInY)
                }
                reusablePath.close()
                drawPath(path = reusablePath, color = starColor.copy(alpha = color.alpha))
                drawCircle(
                    color = Color.White.copy(alpha = color.alpha.coerceIn(0.1f, 1f)),
                    radius = radius * 0.28f,
                    center = center
                )
            }
        }
        else -> {
            drawFivePetalCherryBlossom(
                center = center,
                petalRadius = radius,
                rotationDeg = rotationDegrees,
                petalColor = color,
                centerColor = secondaryColor,
                reusablePath = reusablePath
            )
        }
    }
}

private fun DrawScope.drawFivePetalCherryBlossom(
    center: Offset,
    petalRadius: Float,
    rotationDeg: Float,
    petalColor: Color,
    centerColor: Color,
    reusablePath: Path
) {
    if (petalRadius <= 0.5f) return
    for (i in 0 until 5) {
        val petalAngle = rotationDeg + i * 72f
        val rad = (petalAngle * PI / 180.0).toFloat()
        val offsetDist = petalRadius * 0.72f
        val petalCenter = Offset(
            x = center.x + cos(rad) * offsetDist,
            y = center.y + sin(rad) * offsetDist
        )
        drawSakuraPetalShape(
            center = petalCenter,
            radius = petalRadius * 0.62f,
            rotationDegrees = petalAngle + 90f,
            color = petalColor,
            reusablePath = reusablePath
        )
    }
    drawCircle(
        color = centerColor,
        radius = petalRadius * 0.22f,
        center = center
    )
}

private fun DrawScope.drawSakuraPetalShape(
    center: Offset,
    radius: Float,
    rotationDegrees: Float,
    color: Color,
    reusablePath: Path
) {
    rotate(degrees = rotationDegrees, pivot = center) {
        reusablePath.reset()
        reusablePath.moveTo(center.x, center.y + radius)
        reusablePath.cubicTo(
            center.x - radius * 0.95f, center.y + radius * 0.3f,
            center.x - radius * 0.85f, center.y - radius * 0.85f,
            center.x - radius * 0.22f, center.y - radius
        )
        reusablePath.lineTo(center.x, center.y - radius * 0.68f)
        reusablePath.lineTo(center.x + radius * 0.22f, center.y - radius)
        reusablePath.cubicTo(
            center.x + radius * 0.85f, center.y - radius * 0.85f,
            center.x + radius * 0.95f, center.y + radius * 0.3f,
            center.x, center.y + radius
        )
        reusablePath.close()
        drawPath(path = reusablePath, color = color)
    }
}
