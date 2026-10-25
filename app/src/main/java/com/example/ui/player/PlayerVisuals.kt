package com.example.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LocalSakuraTreeStyle
import com.example.data.NowPlayingSceneMode
import com.example.data.SakuraTreeStyle
import com.example.model.LrcParser
import com.example.model.LyricLine
import com.example.ui.animation.LocalSakuraAnimationState
import com.example.ui.animation.sakuraPressScale
import com.example.ui.components.SakuraBurstEffect
import com.example.ui.theme.LocalAppThemePalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class StageFallingPetal(
    val xRatio: Float,
    val yRatio: Float,
    val sizeDp: Float,
    val speed: Float,
    val swayAmp: Float,
    val swayPhase: Float,
    val rotSpeed: Float,
    val alpha: Float,
    val isSecondary: Boolean
)

private data class StarFirefly(
    val xRatio: Float,
    val yRatio: Float,
    val radiusDp: Float,
    val speed: Float,
    val phase: Float,
    val isWarmSakura: Boolean
)

private data class FloatingLantern(
    val xRatio: Float,
    val yRatio: Float,
    val widthDp: Float,
    val heightDp: Float,
    val speed: Float,
    val swayPhase: Float,
    val isAccentTinted: Boolean
)

@Composable
fun NowPlayingStageVisual(
    sceneMode: NowPlayingSceneMode,
    isPlaying: Boolean,
    fftMagnitudes: FloatArray = FloatArray(0),
    modifier: Modifier = Modifier
) {
    when (sceneMode) {
        NowPlayingSceneMode.DEFAULT_CLASSIC -> {
            // Varsayılan modda kapak etrafında hiçbir ekstra çizim/efekt gösterilmez (Sade Kapak)
        }
        NowPlayingSceneMode.SAKURA_TREE_STAGE -> {
            SakuraTreeBehindCoverScene(
                isPlaying = isPlaying,
                fftMagnitudes = fftMagnitudes,
                modifier = modifier
            )
        }
        NowPlayingSceneMode.ZEN_LOTUS_RIPPLES -> {
            ZenLotusRipplesScene(
                isPlaying = isPlaying,
                fftMagnitudes = fftMagnitudes,
                modifier = modifier
            )
        }
        NowPlayingSceneMode.PAPER_LANTERNS_FIREFLIES -> {
            PaperLanternsFirefliesScene(
                isPlaying = isPlaying,
                fftMagnitudes = fftMagnitudes,
                modifier = modifier
            )
        }
        NowPlayingSceneMode.KITSUNE_TORII_FIRE -> {
            KitsuneToriiFireScene(
                isPlaying = isPlaying,
                fftMagnitudes = fftMagnitudes,
                modifier = modifier
            )
        }
    }
}

@Composable
fun SakuraTreeBehindCoverScene(
    isPlaying: Boolean,
    fftMagnitudes: FloatArray = FloatArray(0),
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    val treeStyle = LocalSakuraTreeStyle.current

    val useThemePalette = (treeStyle == SakuraTreeStyle.TRADITIONAL_SAKURA)
    val effectivePetalPrimary = if (useThemePalette) palette.petalPrimary else treeStyle.petalPrimary
    val effectivePetalSecondary = if (useThemePalette) palette.petalSecondary else treeStyle.petalSecondary
    val effectiveAccent = if (useThemePalette) palette.primaryAccent else treeStyle.primaryAccent
    val effectiveGlow = if (useThemePalette) palette.haloGlowPrimary else treeStyle.glowColor

    val infiniteTransition = rememberInfiniteTransition(label = "sakura_cover_stage")
    val windSway by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stage_wind_sway"
    )
    val petalProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "stage_petal_fall"
    )

    val bassEnergy = remember(fftMagnitudes, isPlaying) {
        if (!isPlaying || fftMagnitudes.isEmpty()) 0f
        else fftMagnitudes.take(8).average().toFloat().coerceIn(0f, 1f)
    }
    val smoothBass by animateFloatAsState(
        targetValue = bassEnergy,
        animationSpec = tween(120),
        label = "stage_bass"
    )

    val fallingPetals = remember {
        val rng = Random(9012)
        List(18) {
            StageFallingPetal(
                xRatio = rng.nextFloat(),
                yRatio = rng.nextFloat(),
                sizeDp = 5.5f + rng.nextFloat() * 6.0f,
                speed = 0.65f + rng.nextFloat() * 0.85f,
                swayAmp = 14f + rng.nextFloat() * 20f,
                swayPhase = rng.nextFloat() * (2f * PI.toFloat()),
                rotSpeed = (rng.nextFloat() - 0.5f) * 320f,
                alpha = 0.45f + rng.nextFloat() * 0.45f,
                isSecondary = rng.nextBoolean()
            )
        }
    }

    val reusableBranchPath = remember { Path() }
    val reusablePetalPath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val center = Offset(w / 2f, h / 2f)
        val isDark = palette.theme.isDark

        // 1. Radiant Sakura Aura Halo behind the cover (Theme-synced!)
        val auraRadius = (size.minDimension * (0.50f + smoothBass * 0.08f)).coerceAtLeast(1f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    effectiveGlow.copy(alpha = if (isPlaying) 0.52f else 0.34f),
                    effectivePetalPrimary.copy(alpha = 0.22f),
                    Color.Transparent
                ),
                center = center,
                radius = auraRadius
            ),
            radius = auraRadius,
            center = center
        )

        val barkDark = treeStyle.barkDarkColor
        val barkMid = treeStyle.barkMidColor
        val barkHighlight = if (useThemePalette) effectivePetalPrimary.copy(alpha = 0.42f) else treeStyle.barkHighlight
        val swayPx = windSway * 8.dp.toPx()

        // 2. Ancient Sakura Trunk & Sweeping Branches framing around the album cover
        reusableBranchPath.reset()
        reusableBranchPath.moveTo(w * 0.86f, h * 0.96f)
        reusableBranchPath.cubicTo(
            w * 0.94f, h * 0.72f,
            w * 0.95f + swayPx * 0.4f, h * 0.42f,
            w * 0.86f + swayPx * 0.8f, h * 0.14f
        )
        drawPath(
            path = reusableBranchPath,
            color = barkDark,
            style = Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = reusableBranchPath,
            color = barkMid,
            style = Stroke(width = 8.5.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = reusableBranchPath,
            color = barkHighlight,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        )

        // Upper-right to upper-left canopy arch over the album cover
        reusableBranchPath.reset()
        reusableBranchPath.moveTo(w * 0.88f + swayPx * 0.6f, h * 0.24f)
        reusableBranchPath.cubicTo(
            w * 0.68f, h * 0.04f + swayPx * 0.3f,
            w * 0.34f, h * 0.03f - swayPx * 0.3f,
            w * 0.10f + swayPx, h * 0.16f
        )
        drawPath(
            path = reusableBranchPath,
            color = barkMid,
            style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = reusableBranchPath,
            color = barkHighlight,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // Left-side weeping/bonsai branch curving down around the left edge of the cover
        reusableBranchPath.reset()
        reusableBranchPath.moveTo(w * 0.14f + swayPx * 0.7f, h * 0.14f)
        reusableBranchPath.cubicTo(
            w * 0.04f + swayPx, h * 0.38f,
            w * 0.05f + swayPx * 0.8f, h * 0.62f,
            w * 0.14f + swayPx * 0.6f, h * 0.78f
        )
        drawPath(
            path = reusableBranchPath,
            color = barkMid,
            style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
        )

        // Lower-left blossom twig
        reusableBranchPath.reset()
        reusableBranchPath.moveTo(w * 0.22f, h * 0.92f)
        reusableBranchPath.cubicTo(
            w * 0.12f, h * 0.86f,
            w * 0.08f + swayPx * 0.5f, h * 0.76f,
            w * 0.12f + swayPx * 0.8f, h * 0.64f
        )
        drawPath(
            path = reusableBranchPath,
            color = barkMid,
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
        )

        // 3. Lush Sakura Blossom Clusters framing the outer ring of the album cover
        val pulseScale = 1.0f + (if (isPlaying) 0.06f + smoothBass * 0.14f else 0.0f)
        val clusterPositions = arrayOf(
            Offset(w * 0.14f + swayPx, h * 0.14f),
            Offset(w * 0.34f + swayPx * 0.7f, h * 0.07f),
            Offset(w * 0.64f + swayPx * 0.7f, h * 0.07f),
            Offset(w * 0.86f + swayPx * 0.8f, h * 0.14f),
            Offset(w * 0.93f + swayPx * 0.5f, h * 0.36f),
            Offset(w * 0.92f + swayPx * 0.4f, h * 0.64f),
            Offset(w * 0.86f + swayPx * 0.3f, h * 0.86f),
            Offset(w * 0.07f + swayPx * 0.9f, h * 0.38f),
            Offset(w * 0.08f + swayPx * 0.7f, h * 0.64f),
            Offset(w * 0.16f + swayPx * 0.5f, h * 0.86f)
        )

        for (i in clusterPositions.indices) {
            val pos = clusterPositions[i]
            val clusterScale = pulseScale * (if (i % 2 == 0) 1.08f else 0.94f)
            scale(clusterScale, pivot = pos) {
                drawStageBlossomCluster(
                    center = pos,
                    seed = i * 37 + 19,
                    primaryColor = effectivePetalPrimary,
                    secondaryColor = effectivePetalSecondary,
                    accentColor = effectiveAccent,
                    isDark = isDark,
                    reusablePath = reusablePetalPath
                )
            }
        }

        // 4. 60 FPS Falling Sakura Petals (Hana-fubuki) around the cover
        for (i in 0 until fallingPetals.size) {
            val p = fallingPetals[i]
            val yNorm = (p.yRatio + petalProgress * p.speed) % 1f
            val y = yNorm * (h + 30f) - 15f
            val sway = sin((petalProgress * 2f * PI.toFloat() * p.speed) + p.swayPhase) * p.swayAmp
            val x = (p.xRatio * w + sway + w) % w
            val angle = (petalProgress * p.rotSpeed) % 360f
            val color = if (p.isSecondary) effectivePetalSecondary else effectivePetalPrimary

            drawStagePetal(
                center = Offset(x, y),
                radius = p.sizeDp.dp.toPx(),
                rotation = angle,
                color = color.copy(alpha = p.alpha),
                reusablePath = reusablePetalPath
            )
        }
    }
}

private fun DrawScope.drawStageBlossomCluster(
    center: Offset,
    seed: Int,
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    isDark: Boolean,
    reusablePath: Path
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = if (isDark) 0.42f else 0.30f),
                secondaryColor.copy(alpha = if (isDark) 0.18f else 0.12f),
                Color.Transparent
            ),
            center = center,
            radius = 28.dp.toPx()
        ),
        radius = 28.dp.toPx(),
        center = center
    )

    val flowerCount = 3
    for (fIdx in 0 until flowerCount) {
        val offsetDist = (fIdx * 9.5f).dp.toPx()
        val angle = ((seed * 29 + fIdx * 118) % 360) * (PI.toFloat() / 180f)
        val flowerCenter = Offset(
            x = center.x + cos(angle) * offsetDist,
            y = center.y + sin(angle) * offsetDist
        )
        val flowerRadius = (8.5f + (fIdx % 2) * 2.5f).dp.toPx()
        val baseRot = ((seed * 17 + fIdx * 43) % 72).toFloat()

        for (p in 0 until 5) {
            val petalAngle = baseRot + p * 72f
            rotate(degrees = petalAngle, pivot = flowerCenter) {
                val petalPos = Offset(flowerCenter.x, flowerCenter.y - flowerRadius * 0.70f)
                val pColor = if (p % 2 == 0) primaryColor else secondaryColor
                drawStagePetal(
                    center = petalPos,
                    radius = flowerRadius * 0.62f,
                    rotation = 0f,
                    color = pColor.copy(alpha = if (isDark) 0.92f else 0.84f),
                    reusablePath = reusablePath
                )
            }
        }

        drawCircle(
            color = accentColor,
            radius = flowerRadius * 0.22f,
            center = flowerCenter
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = flowerRadius * 0.09f,
            center = flowerCenter
        )
    }
}

private fun DrawScope.drawStagePetal(
    center: Offset,
    radius: Float,
    rotation: Float,
    color: Color,
    reusablePath: Path
) {
    rotate(degrees = rotation, pivot = center) {
        reusablePath.reset()
        reusablePath.moveTo(center.x, center.y + radius)
        reusablePath.cubicTo(
            center.x - radius * 0.95f, center.y + radius * 0.30f,
            center.x - radius * 0.85f, center.y - radius * 0.82f,
            center.x - radius * 0.22f, center.y - radius
        )
        reusablePath.lineTo(center.x, center.y - radius * 0.66f)
        reusablePath.lineTo(center.x + radius * 0.22f, center.y - radius)
        reusablePath.cubicTo(
            center.x + radius * 0.85f, center.y - radius * 0.82f,
            center.x + radius * 0.95f, center.y + radius * 0.30f,
            center.x, center.y + radius
        )
        reusablePath.close()
        drawPath(path = reusablePath, color = color)
    }
}

@Composable
fun ZenLotusRipplesScene(
    isPlaying: Boolean,
    fftMagnitudes: FloatArray = FloatArray(0),
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    val infiniteTransition = rememberInfiniteTransition(label = "zen_lotus_ripples")

    val rippleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "zen_ripple_progress"
    )
    val lotusOrbit by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "zen_lotus_orbit"
    )

    val bassEnergy = remember(fftMagnitudes, isPlaying) {
        if (!isPlaying || fftMagnitudes.isEmpty()) 0f
        else fftMagnitudes.take(8).average().toFloat().coerceIn(0f, 1f)
    }
    val smoothBass by animateFloatAsState(
        targetValue = bassEnergy,
        animationSpec = tween(110),
        label = "zen_bass"
    )

    val reusablePetalPath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = (size.minDimension * 0.50f).coerceAtLeast(1f)

        // 1. Multi-Layered Calm Zen Pond Glow & Moonlight Reflection
        val pondRadius = (maxRadius * 1.12f).coerceAtLeast(1f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.primaryAccent.copy(alpha = 0.34f + smoothBass * 0.18f),
                    palette.petalSecondary.copy(alpha = 0.18f),
                    Color(0xFF38BDF8).copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = center,
                radius = pondRadius
            ),
            radius = pondRadius,
            center = center
        )

        // 2. 6 Concentric Dual-Tone Suikinkutsu Water Ripple Rings expanding outward around the cover
        val minRingRadius = maxRadius * 0.62f
        val maxRingRadius = maxRadius * 1.06f
        for (r in 0 until 6) {
            val phase = (rippleProgress + r * (1f / 6f)) % 1f
            val currentR = minRingRadius + (maxRingRadius - minRingRadius) * phase + (smoothBass * 10.dp.toPx())
            val alpha = (1f - phase) * (if (isPlaying) 0.68f else 0.34f)
            // Soft outer shimmer ring
            drawCircle(
                color = palette.haloGlowPrimary.copy(alpha = alpha * 0.35f),
                radius = currentR + 2.dp.toPx(),
                center = center,
                style = Stroke(width = (4.2f - phase * 2.2f).dp.toPx())
            )
            // Crisp inner water ripple crest
            drawCircle(
                color = if (r % 2 == 0) palette.petalPrimary.copy(alpha = alpha) else palette.primaryAccent.copy(alpha = alpha),
                radius = currentR,
                center = center,
                style = Stroke(width = (2.2f - phase * 1.2f).dp.toPx())
            )
        }

        // 3. 8 Floating Zen Lotus Flowers, Notched Emerald Lily Pads & Dewdrop Sparkles
        val orbitRadius = maxRadius * 0.89f
        val lotusCount = 8
        for (i in 0 until lotusCount) {
            val angleDeg = lotusOrbit + (i * (360f / lotusCount))
            val angleRad = angleDeg * (PI.toFloat() / 180f)
            val lotusCenter = Offset(
                x = center.x + cos(angleRad) * orbitRadius,
                y = center.y + sin(angleRad) * orbitRadius
            )
            val lotusSize = (11.5f.dp.toPx() * (1f + smoothBass * 0.20f))

            // Emerald-tinted lily pad halo under the lotus
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF10B981).copy(alpha = 0.28f),
                        palette.secondaryAccent.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = lotusCenter,
                    radius = lotusSize * 1.65f
                ),
                radius = lotusSize * 1.65f,
                center = lotusCenter
            )

            // Outer 6-petal layer
            for (p in 0 until 6) {
                rotate(degrees = angleDeg + p * 60f, pivot = lotusCenter) {
                    val petalPos = Offset(lotusCenter.x, lotusCenter.y - lotusSize * 0.68f)
                    drawStagePetal(
                        center = petalPos,
                        radius = lotusSize * 0.60f,
                        rotation = 0f,
                        color = if (p % 2 == 0) palette.petalPrimary.copy(alpha = 0.92f)
                        else palette.petalSecondary.copy(alpha = 0.84f),
                        reusablePath = reusablePetalPath
                    )
                }
            }
            // Inner 6-petal crown layer rotated 30 degrees for depth
            for (p in 0 until 6) {
                rotate(degrees = angleDeg + 30f + p * 60f, pivot = lotusCenter) {
                    val petalPos = Offset(lotusCenter.x, lotusCenter.y - lotusSize * 0.42f)
                    drawStagePetal(
                        center = petalPos,
                        radius = lotusSize * 0.38f,
                        rotation = 0f,
                        color = Color.White.copy(alpha = 0.78f),
                        reusablePath = reusablePetalPath
                    )
                }
            }
            // Golden stamen center
            drawCircle(
                color = Color(0xFFFFF59D),
                radius = lotusSize * 0.24f,
                center = lotusCenter
            )
        }
    }
}

@Composable
fun PaperLanternsFirefliesScene(
    isPlaying: Boolean,
    fftMagnitudes: FloatArray = FloatArray(0),
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    val infiniteTransition = rememberInfiniteTransition(label = "paper_lanterns_scene")

    val riseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lantern_rise_progress"
    )
    val swayPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lantern_sway_phase"
    )

    val bassEnergy = remember(fftMagnitudes, isPlaying) {
        if (!isPlaying || fftMagnitudes.isEmpty()) 0f
        else fftMagnitudes.take(8).average().toFloat().coerceIn(0f, 1f)
    }
    val smoothBass by animateFloatAsState(
        targetValue = bassEnergy,
        animationSpec = tween(120),
        label = "lantern_bass"
    )

    val lanterns = remember {
        listOf(
            // Left column lanterns framing the cover
            FloatingLantern(0.08f, 0.14f, 19f, 25f, 0.55f, 0.2f, true),
            FloatingLantern(0.14f, 0.48f, 15f, 21f, 0.70f, 1.8f, false),
            FloatingLantern(0.07f, 0.82f, 18f, 24f, 0.60f, 3.4f, true),
            // Right column lanterns framing the cover
            FloatingLantern(0.92f, 0.22f, 19f, 25f, 0.62f, 1.1f, false),
            FloatingLantern(0.86f, 0.58f, 15f, 21f, 0.75f, 2.7f, true),
            FloatingLantern(0.93f, 0.90f, 17f, 23f, 0.52f, 4.2f, false)
        )
    }

    val fireflies = remember {
        val rng = Random(4412)
        List(24) {
            StarFirefly(
                xRatio = rng.nextFloat(),
                yRatio = rng.nextFloat(),
                radiusDp = 2.0f + rng.nextFloat() * 3.0f,
                speed = 0.45f + rng.nextFloat() * 0.65f,
                phase = rng.nextFloat() * (2f * PI.toFloat()),
                isWarmSakura = rng.nextBoolean()
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val center = Offset(w / 2f, h / 2f)
        val festivalRadius = (size.minDimension * 0.52f).coerceAtLeast(1f)

        // 1. Multi-Layered Warm Festival Evening Aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.primaryAccent.copy(alpha = 0.32f + smoothBass * 0.16f),
                    Color(0xFFFFB74D).copy(alpha = 0.18f),
                    Color.Transparent
                ),
                center = center,
                radius = festivalRadius
            ),
            radius = festivalRadius,
            center = center
        )

        // 2. Rising Japanese Paper Lanterns (Chōchin / Tōrō) with Bamboo Ribs & Hanging Tassels
        for (i in lanterns.indices) {
            val l = lanterns[i]
            val yNorm = 1f - ((l.yRatio + riseProgress * l.speed) % 1f)
            val y = yNorm * (h + 44f) - 22f
            val xSway = sin(swayPhase + l.swayPhase) * 8.dp.toPx()
            val x = l.xRatio * w + xSway

            val lw = (l.widthDp.dp.toPx() * (1f + smoothBass * 0.12f)).coerceAtLeast(2f)
            val lh = (l.heightDp.dp.toPx() * (1f + smoothBass * 0.12f)).coerceAtLeast(2f)
            val lanternColor = if (l.isAccentTinted) palette.petalPrimary else Color(0xFFFFCC80)
            val coreColor = Color(0xFFFFF8E1)
            val lanternGlowR = (lh * 1.35f).coerceAtLeast(1f)

            // Outer warm lantern glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        lanternColor.copy(alpha = 0.52f),
                        Color(0xFFFFB300).copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(x, y),
                    radius = lanternGlowR
                ),
                radius = lanternGlowR,
                center = Offset(x, y)
            )

            // Silk tassel hanging below the lantern
            val tasselTopY = y + lh / 2f
            val tasselSway = cos(swayPhase * 1.5f + l.swayPhase) * 3.dp.toPx()
            drawLine(
                color = Color(0xFFFFB300).copy(alpha = 0.82f),
                start = Offset(x, tasselTopY),
                end = Offset(x + tasselSway, tasselTopY + 7.dp.toPx()),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Lantern paper body
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lanternColor.copy(alpha = 0.94f),
                        coreColor.copy(alpha = 0.98f),
                        palette.primaryAccent.copy(alpha = 0.90f)
                    ),
                    startY = y - lh / 2f,
                    endY = y + lh / 2f
                ),
                topLeft = Offset(x - lw / 2f, y - lh / 2f),
                size = Size(lw, lh),
                cornerRadius = CornerRadius(lw * 0.45f, lw * 0.45f)
            )

            // Delicate horizontal bamboo ribs across the Chōchin paper body
            for (rib in -1..1) {
                val ribY = y + rib * (lh * 0.24f)
                drawLine(
                    color = Color(0xFF4E342E).copy(alpha = 0.36f),
                    start = Offset(x - lw * 0.44f, ribY),
                    end = Offset(x + lw * 0.44f, ribY),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Top & bottom wooden caps of the Japanese lantern
            val capW = lw * 0.60f
            val capH = 2.8.dp.toPx()
            drawRoundRect(
                color = Color(0xFF3E2723),
                topLeft = Offset(x - capW / 2f, y - lh / 2f - capH * 0.6f),
                size = Size(capW, capH),
                cornerRadius = CornerRadius(2f, 2f)
            )
            drawRoundRect(
                color = Color(0xFF3E2723),
                topLeft = Offset(x - capW / 2f, y + lh / 2f - capH * 0.4f),
                size = Size(capW, capH),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }

        // 3. Dual-Layer Glowing Fireflies (Hotaru) dancing around the cover
        for (i in 0 until fireflies.size) {
            val f = fireflies[i]
            val yNorm = 1f - ((f.yRatio + riseProgress * f.speed) % 1f)
            val xSway = sin(swayPhase + f.phase) * 14.dp.toPx()
            val x = (f.xRatio * w + xSway + w) % w
            val y = yNorm * h
            val twinkle = (0.35f + 0.65f * abs(sin(swayPhase * 1.4f + f.phase))).coerceIn(0.2f, 1f)
            val color = if (f.isWarmSakura) palette.petalPrimary else Color(0xFFFFF59D)
            val coreR = f.radiusDp.dp.toPx() * (1f + smoothBass * 0.28f)

            drawCircle(
                color = color.copy(alpha = twinkle * 0.32f),
                radius = coreR * 2.2f,
                center = Offset(x, y)
            )
            drawCircle(
                color = color.copy(alpha = twinkle * 0.90f),
                radius = coreR,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * 8. Kitsune Ruh Ateşi & Torii Kapısı (Kitsunebi) — Multi-layered Shrine & Fox-Fire Stage
 */
@Composable
fun KitsuneToriiFireScene(
    isPlaying: Boolean,
    fftMagnitudes: FloatArray = FloatArray(0),
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "kitsune_torii_fire")
    val orbOrbit by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8_500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "kitsune_orb_orbit"
    )

    val bassEnergy = remember(fftMagnitudes, isPlaying) {
        if (!isPlaying || fftMagnitudes.isEmpty()) 0f
        else fftMagnitudes.take(8).average().toFloat().coerceIn(0f, 1f)
    }
    val smoothBass by animateFloatAsState(
        targetValue = bassEnergy,
        animationSpec = tween(110),
        label = "kitsune_bass"
    )

    val flamePath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val center = Offset(w / 2f, h / 2f)
        val shrineRadius = (size.minDimension * 0.50f).coerceAtLeast(1f)

        // 1. Sacred Shrine Vermilion & Spirit Cyan Multi-Layered Aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF3D00).copy(alpha = 0.32f + smoothBass * 0.16f),
                    Color(0xFF00E5FF).copy(alpha = 0.22f),
                    Color(0xFFFFD54F).copy(alpha = 0.10f),
                    Color.Transparent
                ),
                center = center,
                radius = shrineRadius
            ),
            radius = shrineRadius,
            center = center
        )

        // 2. Traditional Vermilion Torii Gate Silhouette with Shimenawa Sacred Rope framing the album cover
        val vermilion = Color(0xFFD84315)
        val darkLacquer = Color(0xFF212121)
        val gateWidth = w * 0.84f
        val gateLeft = (w - gateWidth) / 2f
        val gateRight = gateLeft + gateWidth
        val topKasagiY = h * 0.08f
        val nukiY = h * 0.16f
        val pillarBottomY = h * 0.92f
        val leftPillarX = gateLeft + gateWidth * 0.14f
        val rightPillarX = gateRight - gateWidth * 0.14f
        val pillarW = 7.5f.dp.toPx()

        // Two main Torii pillars (Hashira) with black base shoes (Nemaki)
        drawRoundRect(
            color = vermilion.copy(alpha = 0.88f),
            topLeft = Offset(leftPillarX - pillarW / 2f, topKasagiY),
            size = Size(pillarW, pillarBottomY - topKasagiY),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = vermilion.copy(alpha = 0.88f),
            topLeft = Offset(rightPillarX - pillarW / 2f, topKasagiY),
            size = Size(pillarW, pillarBottomY - topKasagiY),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // Lower horizontal tie beam (Nuki)
        drawRoundRect(
            color = vermilion.copy(alpha = 0.92f),
            topLeft = Offset(gateLeft + gateWidth * 0.05f, nukiY),
            size = Size(gateWidth * 0.90f, 6.dp.toPx()),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // Center tablet plaque (Gakuzuka) between Kasagi and Nuki
        drawRoundRect(
            color = darkLacquer,
            topLeft = Offset(w * 0.5f - 6.dp.toPx(), topKasagiY + 2.dp.toPx()),
            size = Size(12.dp.toPx(), nukiY - topKasagiY + 2.dp.toPx()),
            cornerRadius = CornerRadius(3f, 3f)
        )

        // Curved Top Lintel (Kasagi) with upturned ends
        flamePath.reset()
        flamePath.moveTo(gateLeft, topKasagiY - 5.dp.toPx())
        flamePath.quadraticTo(
            w * 0.5f,
            topKasagiY + 8.dp.toPx(),
            gateRight,
            topKasagiY - 5.dp.toPx()
        )
        drawPath(
            path = flamePath,
            color = darkLacquer,
            style = Stroke(width = 8.5f.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = flamePath,
            color = vermilion,
            style = Stroke(width = 4.8f.dp.toPx(), cap = StrokeCap.Round)
        )

        // 3. 10 Floating Kitsunebi (Blue & Gold Fox Spirit Fire Orbs) with swaying flame tails
        val orbCount = 10
        val orbitR = size.minDimension * 0.44f
        for (i in 0 until orbCount) {
            val angle = orbOrbit + (i * (2f * PI.toFloat() / orbCount))
            val orbX = center.x + cos(angle) * orbitR
            val orbY = center.y + sin(angle) * (orbitR * 0.88f) + sin(orbOrbit * 2f + i) * 7.dp.toPx()
            val isCyanSpirit = (i % 2 == 0)
            val outerFlameColor = if (isCyanSpirit) Color(0xFF00E5FF) else Color(0xFFFFD54F)
            val coreFlameColor = Color(0xFFFFFFFF)
            val orbR = (7.2f.dp.toPx() * (1f + smoothBass * 0.26f)).coerceAtLeast(2f)
            val orbGlowR = (orbR * 2.6f).coerceAtLeast(1f)

            // Outer spirit glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        outerFlameColor.copy(alpha = 0.68f),
                        Color.Transparent
                    ),
                    center = Offset(orbX, orbY),
                    radius = orbGlowR
                ),
                radius = orbGlowR,
                center = Offset(orbX, orbY)
            )

            // Wispy upward fox-fire flame teardrop
            flamePath.reset()
            flamePath.moveTo(orbX, orbY - orbR * 2.25f)
            flamePath.quadraticTo(orbX + orbR * 1.25f, orbY - orbR * 0.3f, orbX, orbY + orbR)
            flamePath.quadraticTo(orbX - orbR * 1.25f, orbY - orbR * 0.3f, orbX, orbY - orbR * 2.25f)
            flamePath.close()
            drawPath(path = flamePath, color = outerFlameColor.copy(alpha = 0.88f))

            // Bright spirit core
            drawCircle(
                color = coreFlameColor,
                radius = orbR * 0.50f,
                center = Offset(orbX, orbY)
            )
        }
    }
}

@Composable
fun HaloGlowBackground(
    isPlaying: Boolean,
    fftMagnitudes: FloatArray = FloatArray(0),
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    val animState = LocalSakuraAnimationState.current

    val infiniteTransition = rememberInfiniteTransition(label = "halo_glow")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_pulse"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "halo_rotation"
    )

    val bassEnergy = remember(fftMagnitudes, isPlaying) {
        if (!isPlaying || fftMagnitudes.isEmpty()) 0f
        else fftMagnitudes.take(8).average().toFloat().coerceIn(0f, 1f)
    }
    val smoothBass by animateFloatAsState(
        targetValue = bassEnergy,
        animationSpec = tween(160),
        label = "bass_energy"
    )

    val glowMult = animState.config.effectIntensity.glowAlphaMultiplier

    Canvas(modifier = modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = (size.minDimension * 0.44f).coerceAtLeast(1f)
        val dynamicScale = if (isPlaying) pulse + smoothBass * 0.18f else 0.92f
        val outerRadius = (baseRadius * dynamicScale).coerceAtLeast(10f)
        val haloGradientRadius = (outerRadius * 1.25f).coerceAtLeast(1f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.haloGlowPrimary.copy(alpha = (0.42f * glowMult).coerceIn(0f, 0.85f)),
                    palette.haloGlowSecondary.copy(alpha = (0.20f * glowMult).coerceIn(0f, 0.65f)),
                    Color.Transparent
                ),
                center = center,
                radius = haloGradientRadius
            ),
            radius = haloGradientRadius,
            center = center
        )

        if (animState.config.masterEnabled && animState.config.audioReactiveWavesEnabled && isPlaying) {
            rotate(degrees = rotation, pivot = center) {
                val barCount = 24
                val ringRadius = baseRadius * 0.78f
                for (i in 0 until barCount) {
                    val angle = (2.0 * PI * i / barCount).toFloat()
                    val mag = if (fftMagnitudes.isNotEmpty()) {
                        fftMagnitudes[i % fftMagnitudes.size]
                    } else {
                        0.25f
                    }
                    val barLen = (10.dp.toPx() + mag * 26.dp.toPx())
                    val start = Offset(
                        x = center.x + cos(angle) * ringRadius,
                        y = center.y + sin(angle) * ringRadius
                    )
                    val end = Offset(
                        x = center.x + cos(angle) * (ringRadius + barLen),
                        y = center.y + sin(angle) * (ringRadius + barLen)
                    )
                    drawLine(
                        color = palette.primaryAccent.copy(alpha = 0.45f),
                        start = start,
                        end = end,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            drawCircle(
                color = palette.secondaryAccent.copy(alpha = 0.25f),
                radius = baseRadius * (0.82f + smoothBass * 0.14f),
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

@Composable
fun AnimatedPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 68.dp,
    iconSize: Dp = 34.dp
) {
    val palette = LocalAppThemePalette.current
    var burstTrigger by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier.size(buttonSize + 32.dp),
        contentAlignment = Alignment.Center
    ) {
        SakuraBurstEffect(
            triggerKey = burstTrigger,
            modifier = Modifier.fillMaxSize()
        )

        val isCowboyFaye = false

        if (isCowboyFaye) {
            Box(
                modifier = Modifier
                    .size(buttonSize + 16.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFFFFD700).copy(alpha = 0.50f),
                                Color(0xFFF59E0B).copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(
                    if (isCowboyFaye) {
                        Brush.linearGradient(
                            listOf(Color(0xFFFFD700), Color(0xFFF59E0B), Color(0xFFFFC000))
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(palette.primaryAccent, palette.tertiaryAccent)
                        )
                    }
                )
                .then(
                    if (isCowboyFaye) {
                        Modifier.border(2.dp, Color(0xFFFFF275), CircleShape)
                    } else Modifier
                )
                .sakuraPressScale()
                .clickable {
                    burstTrigger++
                    onClick()
                }
                .testTag("play_pause_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                tint = if (isCowboyFaye) Color(0xFF1A0E28) else palette.onPrimary,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun InteractiveWaveformSeekbar(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    fftMagnitudes: FloatArray,
    trackSeed: String,
    abLoopStartMs: Long? = null,
    abLoopEndMs: Long? = null,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    val safeDuration = durationMs.coerceAtLeast(1L)
    val progress = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    val barCount = 52
    val staticProfile = remember(trackSeed) {
        val hash = trackSeed.hashCode()
        FloatArray(barCount) { idx ->
            val wave1 = abs(sin((idx * 0.37f) + (hash % 17)))
            val wave2 = abs(cos((idx * 0.19f) + (hash % 31)))
            (0.24f + 0.72f * (wave1 * 0.6f + wave2 * 0.4f)).coerceIn(0.22f, 0.98f)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val livePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveform_phase"
    )

    val loopStartFrac = abLoopStartMs?.let { (it.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f) }
    val loopEndFrac = abLoopEndMs?.let { (it.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .pointerInput(safeDuration) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    onSeekTo((frac * safeDuration).toLong())
                }
            }
            .pointerInput(safeDuration) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val frac = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    onSeekTo((frac * safeDuration).toLong())
                }
            }
            .testTag("interactive_waveform_seekbar")
    ) {
        val totalWidth = size.width
        val totalHeight = size.height
        if (totalWidth <= 0f || totalHeight <= 0f) return@Canvas
        val slotWidth = totalWidth / barCount
        val barWidth = (slotWidth * 0.62f).coerceAtLeast(2.dp.toPx())

        for (i in 0 until barCount) {
            val barFrac = i.toFloat() / barCount.toFloat()
            val isPlayed = barFrac <= progress
            val inAbLoop = loopStartFrac != null &&
                (if (loopEndFrac != null) barFrac in loopStartFrac..loopEndFrac else abs(barFrac - loopStartFrac) < 0.02f)

            val fftBoost = if (isPlaying && fftMagnitudes.isNotEmpty()) {
                fftMagnitudes[i % fftMagnitudes.size] * 0.25f
            } else if (isPlaying) {
                abs(sin(livePhase + i * 0.35f)) * 0.14f
            } else {
                0f
            }

            val amp = (staticProfile[i] + fftBoost).coerceIn(0.20f, 1f)
            val barHeight = (totalHeight * amp).coerceAtLeast(6.dp.toPx())
            val topY = (totalHeight - barHeight) / 2f
            val leftX = i * slotWidth + (slotWidth - barWidth) / 2f

            val barColor = when {
                inAbLoop -> palette.tertiaryAccent
                isPlayed -> palette.primaryAccent
                else -> palette.surfaceVariant.copy(alpha = 0.85f)
            }

            drawRoundRect(
                color = barColor,
                topLeft = Offset(leftX, topY),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }

        // Playhead indicator line
        val playheadX = (progress * totalWidth).coerceIn(0f, totalWidth)
        drawCircle(
            color = palette.primaryAccent,
            radius = 5.dp.toPx(),
            center = Offset(playheadX, totalHeight / 2f)
        )
    }
}

@Composable
fun DoubleTapSeekRippleOverlay(
    seekDeltaSeconds: Int?,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    AnimatedVisibility(
        visible = seekDeltaSeconds != null,
        enter = scaleIn(initialScale = 0.7f, animationSpec = tween(180)) + fadeIn(tween(150)),
        exit = scaleOut(targetScale = 1.15f, animationSpec = tween(220)) + fadeOut(tween(220)),
        modifier = modifier
    ) {
        val delta = seekDeltaSeconds ?: 0
        val isForward = delta >= 0
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(palette.surface.copy(alpha = 0.88f))
                .border(1.5.dp, palette.primaryAccent, RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isForward) Icons.Default.Forward10 else Icons.Default.Replay10,
                    contentDescription = null,
                    tint = palette.primaryAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isForward) "+10 sn" else "-10 sn",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
            }
        }
    }
}

@Composable
fun AbLoopControlBar(
    pointAMs: Long?,
    pointBMs: Long?,
    onSetPointA: () -> Unit,
    onSetPointB: () -> Unit,
    onClearAbLoop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    val isLoopActive = pointAMs != null && pointBMs != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isLoopActive) palette.tertiaryAccent.copy(alpha = 0.18f)
                else palette.surfaceContainer.copy(alpha = 0.78f)
            )
            .border(
                width = 1.dp,
                color = if (isLoopActive) palette.tertiaryAccent else palette.dividerOrBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Repeat,
                contentDescription = null,
                tint = if (isLoopActive) palette.tertiaryAccent else palette.primaryAccent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "A-B Döngü:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (pointAMs != null) palette.primaryAccent else palette.surfaceVariant)
                    .clickable { onSetPointA() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (pointAMs != null) "A: ${LrcParser.formatTimestamp(pointAMs)}" else "A Seç",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (pointAMs != null) palette.onPrimary else palette.onSurface
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            pointBMs != null -> palette.tertiaryAccent
                            pointAMs != null -> palette.primaryAccent.copy(alpha = 0.25f)
                            else -> palette.surfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                    .clickable(enabled = pointAMs != null) { onSetPointB() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (pointBMs != null) "B: ${LrcParser.formatTimestamp(pointBMs)}" else "B Seç",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (pointBMs != null) palette.onPrimary else palette.onSurface
                )
            }

            if (pointAMs != null || pointBMs != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(palette.surfaceVariant)
                        .clickable { onClearAbLoop() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "A-B Döngüsünü Temizle",
                        tint = palette.onSurface,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LyricsPanel(
    lyrics: List<LyricLine>,
    activeIndex: Int,
    rawLrcText: String,
    isFetchingLyrics: Boolean = false,
    activeLyricsSourceLabel: String? = null,
    onRefetchLyrics: (() -> Unit)? = null,
    onSeekToLine: (Long) -> Unit,
    onUpdateRawLrc: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    val listState = rememberLazyListState()
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeIndex) {
        if (activeIndex in lyrics.indices) {
            listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Senkronize Şarkı Sözleri (.LRC)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                    if (isFetchingLyrics) {
                        Spacer(modifier = Modifier.size(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = palette.primaryAccent
                        )
                    }
                }
                if (!activeLyricsSourceLabel.isNullOrBlank()) {
                    Text(
                        text = "Kaynak: $activeLyricsSourceLabel • Otomatik .LRC Kaydedildi",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primaryAccent
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onRefetchLyrics != null) {
                    OutlinedButton(
                        onClick = onRefetchLyrics,
                        enabled = !isFetchingLyrics,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("5 Kaynaktan Ara", fontSize = 11.sp)
                    }
                }
                OutlinedButton(
                    onClick = { showEditDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "LRC Düzenle", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Düzenle", fontSize = 11.sp)
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.surfaceContainer.copy(alpha = 0.72f))
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isActive = index == activeIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isActive) palette.primaryAccent.copy(alpha = 0.22f)
                            else Color.Transparent
                        )
                        .clickable { onSeekToLine(line.timeMs) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = LrcParser.formatTimestamp(line.timeMs),
                        fontSize = 11.sp,
                        color = if (isActive) palette.primaryAccent else palette.onSurfaceMuted,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = line.text,
                            fontSize = if (isActive) 17.sp else 14.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive) palette.onSurface else palette.onSurfaceMuted,
                            textAlign = TextAlign.Start
                        )
                        if (!line.translatedText.isNullOrBlank()) {
                            Text(
                                text = line.translatedText,
                                fontSize = if (isActive) 13.sp else 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = if (isActive) palette.primaryAccent else palette.primaryAccent.copy(alpha = 0.70f),
                                textAlign = TextAlign.Start,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        var editedText by remember(rawLrcText) { mutableStateOf(rawLrcText) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("LRC Şarkı Sözlerini Düzenle") },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateRawLrc(editedText)
                        showEditDialog = false
                    }
                ) {
                    Text("Uygula")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}

