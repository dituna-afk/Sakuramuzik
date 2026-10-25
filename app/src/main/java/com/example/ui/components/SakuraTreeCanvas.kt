package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.example.data.SakuraTreeForm
import com.example.data.SakuraTreeStyle
import com.example.ui.theme.LocalAppThemePalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class TreeFallingPetal(
    val xRatio: Float,
    val yRatio: Float,
    val size: Float,
    val speed: Float,
    val swayAmp: Float,
    val swayPhase: Float,
    val rotSpeed: Float,
    val alpha: Float,
    val isSecondary: Boolean
)

@Composable
fun SakuraTreeCanvas(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isScrolling: Boolean = false,
    treeStyle: SakuraTreeStyle = SakuraTreeStyle.TRADITIONAL_SAKURA,
    treeForm: SakuraTreeForm = SakuraTreeForm.CLASSIC_SWEEPING
) {
    val palette = LocalAppThemePalette.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    if (!lifecycleState.isAtLeast(Lifecycle.State.STARTED)) return

    val animState = com.example.ui.animation.LocalSakuraAnimationState.current
    val masterAnimEnabled = animState.config.masterEnabled && !isScrolling
    val petalsEnabled = masterAnimEnabled && animState.config.floatingPetalsEnabled

    var lastWindSway by remember { mutableFloatStateOf(0f) }
    var lastPetalProgress by remember { mutableFloatStateOf(0f) }
    var lastMusicPulse by remember { mutableFloatStateOf(1f) }

    val windSway: Float
    val petalProgress: Float
    val musicPulse: Float

    if (masterAnimEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "sakura_tree_motion")

        val animatedWind by infiniteTransition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "tree_wind_sway"
        )

        val animatedPetal by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 18_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "falling_petals_progress"
        )

        val animatedPulse by infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = if (isPlaying) 1.08f else 1.02f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = if (isPlaying) 900 else 2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "tree_blossom_pulse"
        )

        lastWindSway = animatedWind
        lastPetalProgress = animatedPetal
        lastMusicPulse = animatedPulse

        windSway = animatedWind
        petalProgress = animatedPetal
        musicPulse = animatedPulse
    } else {
        windSway = lastWindSway
        petalProgress = lastPetalProgress
        musicPulse = lastMusicPulse
    }

    val ambientPetals = remember(petalsEnabled) {
        if (!petalsEnabled) emptyList()
        else {
            val rng = Random(42)
            List(10) {
                TreeFallingPetal(
                    xRatio = rng.nextFloat(),
                    yRatio = rng.nextFloat(),
                    size = 6.5f + rng.nextFloat() * 8.5f,
                    speed = 0.55f + rng.nextFloat() * 0.75f,
                    swayAmp = 14f + rng.nextFloat() * 20f,
                    swayPhase = rng.nextFloat() * (2f * PI.toFloat()),
                    rotSpeed = (rng.nextFloat() - 0.5f) * 220f,
                    alpha = 0.28f + rng.nextFloat() * 0.35f,
                    isSecondary = rng.nextBoolean()
                )
            }
        }
    }

    val reusablePetalPath = remember { Path() }

    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val isDark = palette.theme.isDark
        val useThemePalette = (treeStyle == SakuraTreeStyle.TRADITIONAL_SAKURA)
        val effectivePetalPrimary = if (useThemePalette) palette.petalPrimary else treeStyle.petalPrimary
        val effectivePetalSecondary = if (useThemePalette) palette.petalSecondary else treeStyle.petalSecondary
        val effectiveAccent = if (useThemePalette) palette.primaryAccent else treeStyle.primaryAccent
        val effectiveGlowColor = if (useThemePalette) palette.haloGlowPrimary else treeStyle.glowColor

        // 1. Ambient Sakura Glow
        val glowCenter = when (treeForm) {
            SakuraTreeForm.CLASSIC_SWEEPING -> Offset(w * 0.72f, h * 0.38f)
            SakuraTreeForm.MAJESTIC_VERTICAL -> Offset(w * 0.50f, h * 0.30f)
            SakuraTreeForm.ANCIENT_BONSAI -> Offset(w * 0.40f, h * 0.48f)
            SakuraTreeForm.TWIN_TRUNKS -> Offset(w * 0.60f, h * 0.35f)
        }
        val auraAlphaMult = if (isPlaying) 1.4f else 1.0f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    effectiveGlowColor.copy(alpha = (treeStyle.auraAlpha * auraAlphaMult).coerceAtMost(0.70f)),
                    effectivePetalPrimary.copy(alpha = 0.14f),
                    Color.Transparent
                ),
                center = glowCenter,
                radius = (w * 0.85f).coerceAtLeast(1f)
            )
        )

        val barkDarkColor = treeStyle.barkDarkColor
        val barkMidColor = treeStyle.barkMidColor
        val barkHighlight = if (useThemePalette) effectivePetalPrimary.copy(alpha = 0.38f) else treeStyle.barkHighlight

        val clusterNodes = mutableListOf<Pair<Offset, Float>>()

        // 2. Render Trunk & Branches Based on SakuraTreeForm
        when (treeForm) {
            SakuraTreeForm.CLASSIC_SWEEPING -> {
                val trunkBaseX = w * 0.88f
                val trunkBaseY = h * 1.02f
                val trunkForkX = w * 0.72f + (windSway * 3.5f)
                val trunkForkY = h * 0.52f

                val trunkPath = Path().apply {
                    moveTo(trunkBaseX - 34.dp.toPx(), trunkBaseY)
                    cubicTo(
                        trunkBaseX - 18.dp.toPx(), h * 0.82f,
                        trunkForkX + 22.dp.toPx(), h * 0.65f,
                        trunkForkX + 10.dp.toPx(), trunkForkY
                    )
                    lineTo(trunkForkX - 12.dp.toPx(), trunkForkY)
                    cubicTo(
                        trunkForkX - 6.dp.toPx(), h * 0.66f,
                        trunkBaseX - 60.dp.toPx(), h * 0.84f,
                        trunkBaseX - 58.dp.toPx(), trunkBaseY
                    )
                    close()
                }
                drawPath(
                    path = trunkPath,
                    brush = Brush.linearGradient(
                        colors = listOf(barkDarkColor, barkMidColor),
                        start = Offset(trunkBaseX - 60.dp.toPx(), trunkBaseY),
                        end = Offset(trunkForkX, trunkForkY)
                    )
                )
                drawPath(path = trunkPath, color = barkHighlight, style = Stroke(width = 1.5.dp.toPx()))

                val branch1End = Offset(w * 0.22f + (windSway * 14f), h * 0.24f + (windSway * 6f))
                val b1Path = Path().apply {
                    moveTo(trunkForkX - 4.dp.toPx(), trunkForkY)
                    cubicTo(trunkForkX - 80.dp.toPx(), trunkForkY - 40.dp.toPx(), w * 0.44f, h * 0.36f + (windSway * 8f), branch1End.x, branch1End.y)
                }
                drawPath(path = b1Path, color = barkMidColor, style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path = b1Path, color = barkHighlight, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

                val b1SubEnd = Offset(w * 0.16f + (windSway * 10f), h * 0.38f + (windSway * 5f))
                val b1SubPath = Path().apply {
                    moveTo(w * 0.38f, h * 0.33f)
                    cubicTo(w * 0.28f, h * 0.32f, w * 0.22f, h * 0.35f, b1SubEnd.x, b1SubEnd.y)
                }
                drawPath(path = b1SubPath, color = barkMidColor, style = Stroke(width = 5.5.dp.toPx(), cap = StrokeCap.Round))

                val branch2End = Offset(w * 0.88f + (windSway * 10f), h * 0.18f + (windSway * 5f))
                val b2Path = Path().apply {
                    moveTo(trunkForkX + 4.dp.toPx(), trunkForkY)
                    cubicTo(w * 0.76f, h * 0.38f, w * 0.82f, h * 0.28f, branch2End.x, branch2End.y)
                }
                drawPath(path = b2Path, color = barkMidColor, style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path = b2Path, color = barkHighlight, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))

                val branch3End = Offset(w * 0.42f + (windSway * 12f), h * 0.46f + (windSway * 7f))
                val b3Path = Path().apply {
                    moveTo(trunkForkX - 2.dp.toPx(), trunkForkY + 14.dp.toPx())
                    cubicTo(w * 0.62f, h * 0.54f, w * 0.52f, h * 0.48f, branch3End.x, branch3End.y)
                }
                drawPath(path = b3Path, color = barkMidColor, style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))

                clusterNodes.addAll(
                    listOf(
                        branch1End to 1.15f,
                        Offset(branch1End.x + 36.dp.toPx(), branch1End.y - 18.dp.toPx()) to 0.95f,
                        Offset(branch1End.x - 28.dp.toPx(), branch1End.y + 12.dp.toPx()) to 0.90f,
                        b1SubEnd to 1.1f,
                        Offset(b1SubEnd.x + 30.dp.toPx(), b1SubEnd.y - 12.dp.toPx()) to 0.85f,
                        Offset(w * 0.36f + (windSway * 8f), h * 0.31f) to 1.05f,
                        Offset(w * 0.48f + (windSway * 9f), h * 0.28f) to 1.0f,
                        branch2End to 1.1f,
                        Offset(branch2End.x - 30.dp.toPx(), branch2End.y - 16.dp.toPx()) to 0.92f,
                        Offset(w * 0.80f + (windSway * 6f), h * 0.25f) to 0.95f,
                        branch3End to 1.12f,
                        Offset(branch3End.x + 32.dp.toPx(), branch3End.y - 16.dp.toPx()) to 0.88f,
                        Offset(trunkForkX, trunkForkY - 8.dp.toPx()) to 1.0f
                    )
                )
            }

            SakuraTreeForm.MAJESTIC_VERTICAL -> {
                val trunkBaseX = w * 0.54f
                val trunkBaseY = h * 1.02f
                val trunkForkX = w * 0.50f + (windSway * 2.5f)
                val trunkForkY = h * 0.42f

                val trunkPath = Path().apply {
                    moveTo(trunkBaseX - 30.dp.toPx(), trunkBaseY)
                    cubicTo(trunkBaseX - 10.dp.toPx(), h * 0.78f, trunkForkX - 16.dp.toPx(), h * 0.58f, trunkForkX - 10.dp.toPx(), trunkForkY)
                    lineTo(trunkForkX + 10.dp.toPx(), trunkForkY)
                    cubicTo(trunkForkX + 16.dp.toPx(), h * 0.58f, trunkBaseX + 30.dp.toPx(), h * 0.78f, trunkBaseX + 26.dp.toPx(), trunkBaseY)
                    close()
                }
                drawPath(path = trunkPath, brush = Brush.linearGradient(listOf(barkDarkColor, barkMidColor), start = Offset(trunkBaseX, trunkBaseY), end = Offset(trunkForkX, trunkForkY)))
                drawPath(path = trunkPath, color = barkHighlight, style = Stroke(width = 1.5.dp.toPx()))

                // 4 Grand Symmetrical Canopy Branches
                val leftEnd = Offset(w * 0.14f + (windSway * 10f), h * 0.28f)
                val topLeftEnd = Offset(w * 0.32f + (windSway * 8f), h * 0.16f)
                val topRightEnd = Offset(w * 0.68f + (windSway * 8f), h * 0.16f)
                val rightEnd = Offset(w * 0.86f + (windSway * 10f), h * 0.28f)

                val bLeft = Path().apply { moveTo(trunkForkX - 8.dp.toPx(), trunkForkY); cubicTo(w * 0.35f, h * 0.38f, w * 0.22f, h * 0.32f, leftEnd.x, leftEnd.y) }
                val bTopLeft = Path().apply { moveTo(trunkForkX - 4.dp.toPx(), trunkForkY); cubicTo(w * 0.42f, h * 0.32f, w * 0.36f, h * 0.24f, topLeftEnd.x, topLeftEnd.y) }
                val bTopRight = Path().apply { moveTo(trunkForkX + 4.dp.toPx(), trunkForkY); cubicTo(w * 0.58f, h * 0.32f, w * 0.64f, h * 0.24f, topRightEnd.x, topRightEnd.y) }
                val bRight = Path().apply { moveTo(trunkForkX + 8.dp.toPx(), trunkForkY); cubicTo(w * 0.65f, h * 0.38f, w * 0.78f, h * 0.32f, rightEnd.x, rightEnd.y) }

                listOf(bLeft, bTopLeft, bTopRight, bRight).forEach { p ->
                    drawPath(path = p, color = barkMidColor, style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(path = p, color = barkHighlight, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
                }

                clusterNodes.addAll(
                    listOf(
                        leftEnd to 1.1f, Offset(leftEnd.x + 30.dp.toPx(), leftEnd.y - 12.dp.toPx()) to 0.9f,
                        topLeftEnd to 1.2f, Offset(topLeftEnd.x - 24.dp.toPx(), topLeftEnd.y + 14.dp.toPx()) to 0.95f,
                        Offset(w * 0.50f + (windSway * 6f), h * 0.12f) to 1.25f,
                        topRightEnd to 1.2f, Offset(topRightEnd.x + 24.dp.toPx(), topRightEnd.y + 14.dp.toPx()) to 0.95f,
                        rightEnd to 1.1f, Offset(rightEnd.x - 30.dp.toPx(), rightEnd.y - 12.dp.toPx()) to 0.9f,
                        Offset(w * 0.25f, h * 0.22f) to 1.0f,
                        Offset(w * 0.75f, h * 0.22f) to 1.0f
                    )
                )
            }

            SakuraTreeForm.ANCIENT_BONSAI -> {
                val trunkBaseX = w * 0.82f
                val trunkBaseY = h * 1.02f
                val kneeX = w * 0.48f + (windSway * 4f)
                val kneeY = h * 0.62f
                val armEnd = Offset(w * 0.18f + (windSway * 12f), h * 0.45f)

                val trunkPath = Path().apply {
                    moveTo(trunkBaseX - 38.dp.toPx(), trunkBaseY)
                    cubicTo(trunkBaseX - 10.dp.toPx(), h * 0.84f, kneeX + 40.dp.toPx(), kneeY + 20.dp.toPx(), kneeX, kneeY)
                    cubicTo(w * 0.35f, h * 0.58f, w * 0.26f, h * 0.50f, armEnd.x, armEnd.y)
                }
                drawPath(path = trunkPath, color = barkDarkColor, style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path = trunkPath, color = barkMidColor, style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path = trunkPath, color = barkHighlight, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

                val subBranch1 = Offset(w * 0.38f + (windSway * 8f), h * 0.32f)
                val sb1Path = Path().apply { moveTo(kneeX, kneeY); cubicTo(w * 0.42f, h * 0.48f, w * 0.40f, h * 0.38f, subBranch1.x, subBranch1.y) }
                drawPath(path = sb1Path, color = barkMidColor, style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))

                val subBranch2 = Offset(w * 0.65f + (windSway * 6f), h * 0.38f)
                val sb2Path = Path().apply { moveTo(trunkBaseX - 18.dp.toPx(), h * 0.80f); cubicTo(w * 0.75f, h * 0.60f, w * 0.70f, h * 0.48f, subBranch2.x, subBranch2.y) }
                drawPath(path = sb2Path, color = barkMidColor, style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round))

                clusterNodes.addAll(
                    listOf(
                        armEnd to 1.25f, Offset(armEnd.x + 35.dp.toPx(), armEnd.y - 15.dp.toPx()) to 1.0f,
                        subBranch1 to 1.15f, Offset(subBranch1.x - 28.dp.toPx(), subBranch1.y + 12.dp.toPx()) to 0.95f,
                        subBranch2 to 1.1f, Offset(subBranch2.x + 28.dp.toPx(), subBranch2.y - 14.dp.toPx()) to 0.9f,
                        Offset(kneeX - 10.dp.toPx(), kneeY - 30.dp.toPx()) to 1.05f
                    )
                )
            }

            SakuraTreeForm.TWIN_TRUNKS -> {
                val baseRootX = w * 0.80f
                val baseRootY = h * 1.02f

                // Main Trunk
                val mainForkX = w * 0.48f + (windSway * 8f)
                val mainForkY = h * 0.45f
                val mainPath = Path().apply {
                    moveTo(baseRootX - 30.dp.toPx(), baseRootY)
                    cubicTo(w * 0.70f, h * 0.75f, w * 0.58f, h * 0.58f, mainForkX, mainForkY)
                }
                drawPath(path = mainPath, color = barkDarkColor, style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path = mainPath, color = barkMidColor, style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path = mainPath, color = barkHighlight, style = Stroke(width = 1.8.dp.toPx()))

                // Twin Trunk (Splits right)
                val twinForkX = w * 0.85f + (windSway * 6f)
                val twinForkY = h * 0.32f
                val twinPath = Path().apply {
                    moveTo(baseRootX - 10.dp.toPx(), h * 0.88f)
                    cubicTo(w * 0.82f, h * 0.65f, w * 0.88f, h * 0.48f, twinForkX, twinForkY)
                }
                drawPath(path = twinPath, color = barkDarkColor, style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path = twinPath, color = barkMidColor, style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path = twinPath, color = barkHighlight, style = Stroke(width = 1.5.dp.toPx()))

                // Main Canopy Endpoints
                val mainLeftEnd = Offset(w * 0.22f + (windSway * 10f), h * 0.25f)
                val mainTopEnd = Offset(w * 0.42f + (windSway * 8f), h * 0.18f)
                val bML = Path().apply { moveTo(mainForkX, mainForkY); cubicTo(w * 0.35f, h * 0.38f, w * 0.28f, h * 0.30f, mainLeftEnd.x, mainLeftEnd.y) }
                val bMT = Path().apply { moveTo(mainForkX, mainForkY); cubicTo(w * 0.46f, h * 0.32f, w * 0.44f, h * 0.24f, mainTopEnd.x, mainTopEnd.y) }

                // Twin Canopy Endpoints
                val twinTopEnd = Offset(w * 0.72f + (windSway * 8f), h * 0.18f)
                val twinRightEnd = Offset(w * 0.92f + (windSway * 10f), h * 0.22f)
                val bTT = Path().apply { moveTo(twinForkX, twinForkY); cubicTo(w * 0.80f, h * 0.26f, w * 0.76f, h * 0.20f, twinTopEnd.x, twinTopEnd.y) }
                val bTR = Path().apply { moveTo(twinForkX, twinForkY); cubicTo(w * 0.88f, h * 0.28f, w * 0.90f, h * 0.24f, twinRightEnd.x, twinRightEnd.y) }

                listOf(bML, bMT, bTT, bTR).forEach { p ->
                    drawPath(path = p, color = barkMidColor, style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(path = p, color = barkHighlight, style = Stroke(width = 1.5.dp.toPx()))
                }

                clusterNodes.addAll(
                    listOf(
                        mainLeftEnd to 1.15f, mainTopEnd to 1.20f,
                        Offset(w * 0.32f, h * 0.22f) to 1.0f,
                        twinTopEnd to 1.15f, twinRightEnd to 1.10f,
                        Offset(w * 0.82f, h * 0.22f) to 1.0f,
                        Offset(mainForkX, mainForkY - 10.dp.toPx()) to 0.95f
                    )
                )
            }
        }

        // 3. Render Blossom Clusters
        for (i in 0 until clusterNodes.size) {
            val (pos, scaleMult) = clusterNodes[i]
            scale(scaleMult * musicPulse, pivot = pos) {
                drawBlossomCluster(
                    center = pos,
                    seed = i * 41 + 17,
                    primaryColor = effectivePetalPrimary,
                    secondaryColor = effectivePetalSecondary,
                    accentColor = effectiveAccent,
                    isDark = isDark,
                    reusablePath = reusablePetalPath
                )
            }
        }

        // 4. Ambient Falling Petals (Hana-fubuki)
        for (i in 0 until ambientPetals.size) {
            val p = ambientPetals[i]
            val yNorm = (p.yRatio + petalProgress * p.speed) % 1f
            val y = yNorm * (h + 50f) - 20f
            val sway = sin((petalProgress * 2f * PI.toFloat() * p.speed) + p.swayPhase) * p.swayAmp
            val x = (p.xRatio * w + sway + w) % w
            val angle = (petalProgress * p.rotSpeed) % 360f

            val petalColor = if (p.isSecondary) effectivePetalSecondary else effectivePetalPrimary
            drawTreePetal(
                center = Offset(x, y),
                radius = p.size.dp.toPx(),
                rotation = angle,
                color = petalColor.copy(alpha = p.alpha),
                reusablePath = reusablePetalPath
            )
        }
    }
}

private fun DrawScope.drawBlossomCluster(
    center: Offset,
    seed: Int,
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    isDark: Boolean,
    reusablePath: Path
) {
    val rng = Random(seed)
    val flowerCount = 3 + (seed % 3)

    // Soft background aura for the blossom cluster
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = if (isDark) 0.40f else 0.28f),
                secondaryColor.copy(alpha = if (isDark) 0.18f else 0.10f),
                Color.Transparent
            ),
            center = center,
            radius = 36.dp.toPx()
        )
    )

    repeat(flowerCount) { fIdx ->
        val offsetDist = (fIdx * 12f).dp.toPx()
        val angle = (fIdx * 110f + rng.nextFloat() * 30f) * (PI.toFloat() / 180f)
        val flowerCenter = Offset(
            x = center.x + cos(angle) * offsetDist,
            y = center.y + sin(angle) * offsetDist
        )
        val flowerRadius = (10f + rng.nextFloat() * 5f).dp.toPx()
        val baseRot = rng.nextFloat() * 72f

        // 5 sakura petals around flower center
        for (p in 0 until 5) {
            val petalAngle = baseRot + p * 72f
            rotate(degrees = petalAngle, pivot = flowerCenter) {
                val petalPos = Offset(flowerCenter.x, flowerCenter.y - flowerRadius * 0.72f)
                val pColor = if (p % 2 == 0) primaryColor else secondaryColor
                drawTreePetal(
                    center = petalPos,
                    radius = flowerRadius * 0.65f,
                    rotation = 0f,
                    color = pColor.copy(alpha = if (isDark) 0.88f else 0.78f),
                    reusablePath = reusablePath
                )
            }
        }

        // Flower pistil/stamen center
        drawCircle(
            color = accentColor.copy(alpha = 0.95f),
            radius = flowerRadius * 0.20f,
            center = flowerCenter
        )
        drawCircle(
            color = Color.White.copy(alpha = if (isDark) 0.85f else 0.65f),
            radius = flowerRadius * 0.08f,
            center = flowerCenter
        )
    }
}

private fun DrawScope.drawTreePetal(
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
            center.x - radius * 0.96f, center.y + radius * 0.32f,
            center.x - radius * 0.86f, center.y - radius * 0.82f,
            center.x - radius * 0.22f, center.y - radius
        )
        // Characteristic Sakura petal notch
        reusablePath.lineTo(center.x, center.y - radius * 0.66f)
        reusablePath.lineTo(center.x + radius * 0.22f, center.y - radius)
        reusablePath.cubicTo(
            center.x + radius * 0.86f, center.y - radius * 0.82f,
            center.x + radius * 0.96f, center.y + radius * 0.32f,
            center.x, center.y + radius
        )
        reusablePath.close()
        drawPath(path = reusablePath, color = color)
    }
}
