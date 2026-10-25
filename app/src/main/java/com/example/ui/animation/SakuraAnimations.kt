package com.example.ui.animation

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.example.data.SakuraAnimationConfig

private val EmphasizedDecelerateEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
private val EmphasizedAccelerateEasing = CubicBezierEasing(0.32f, 0.0f, 0.67f, 0.0f)

data class SakuraAnimationRuntimeState(
    val config: SakuraAnimationConfig = SakuraAnimationConfig(),
    val isSystemPowerSaveMode: Boolean = false
) {
    val areAnimationsActive: Boolean
        get() = config.masterEnabled && !(config.powerSaveAutoReduce && isSystemPowerSaveMode && config.petalDensity.ambientPetalCount == 0)
}

val LocalSakuraAnimationState = staticCompositionLocalOf {
    SakuraAnimationRuntimeState()
}

@Composable
fun rememberSystemPowerSaveMode(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isPowerSaveMode == true
        } catch (_: Exception) {
            false
        }
    }
}

fun buildSakuraTabTransition(
    config: SakuraAnimationConfig,
    forward: Boolean = true
): ContentTransform {
    if (!config.masterEnabled || !config.screenTransitionsEnabled) {
        return (fadeIn(tween(60)) togetherWith fadeOut(tween(50))).apply {
            targetContentZIndex = if (forward) 1f else 0f
        }
    }
    val duration = (config.transitionSpeed.baseDurationMs * 0.45f).toInt().coerceIn(90, 150)
    val slideOffset: (Int) -> Int = { fullWidth ->
        val dist = (fullWidth * 0.03f).toInt()
        if (forward) dist else -dist
    }
    val exitOffset: (Int) -> Int = { fullWidth ->
        val dist = (fullWidth * 0.025f).toInt()
        if (forward) -dist else dist
    }

    return ContentTransform(
        targetContentEnter = fadeIn(tween(duration, easing = LinearOutSlowInEasing)) +
            slideInHorizontally(tween(duration, easing = FastOutSlowInEasing), initialOffsetX = slideOffset),
        initialContentExit = fadeOut(tween((duration * 0.6f).toInt(), easing = FastOutLinearInEasing)) +
            slideOutHorizontally(tween((duration * 0.6f).toInt(), easing = FastOutSlowInEasing), targetOffsetX = exitOffset),
        targetContentZIndex = if (forward) 1f else 0f,
        sizeTransform = androidx.compose.animation.SizeTransform(clip = false)
    )
}

fun buildFullPlayerEnterTransition(
    config: SakuraAnimationConfig
): EnterTransition {
    val enterDuration = if (config.masterEnabled && config.screenTransitionsEnabled) {
        (config.transitionSpeed.baseDurationMs * 1.35f).toInt().coerceIn(260, 520)
    } else {
        220
    }
    val fadeDuration = (enterDuration * 0.65f).toInt().coerceAtLeast(140)

    return slideInVertically(
        animationSpec = tween(durationMillis = enterDuration, easing = EmphasizedDecelerateEasing),
        initialOffsetY = { fullHeight -> fullHeight }
    ) + fadeIn(
        animationSpec = tween(durationMillis = fadeDuration, easing = LinearOutSlowInEasing)
    ) + scaleIn(
        animationSpec = tween(durationMillis = enterDuration, easing = EmphasizedDecelerateEasing),
        initialScale = 0.94f,
        transformOrigin = TransformOrigin(0.5f, 1.0f)
    )
}

fun buildFullPlayerExitTransition(
    config: SakuraAnimationConfig
): ExitTransition {
    val exitDuration = if (config.masterEnabled && config.screenTransitionsEnabled) {
        (config.transitionSpeed.baseDurationMs * 1.1f).toInt().coerceIn(220, 420)
    } else {
        190
    }
    val fadeDuration = (exitDuration * 0.75f).toInt().coerceAtLeast(130)

    return slideOutVertically(
        animationSpec = tween(durationMillis = exitDuration, easing = FastOutSlowInEasing),
        targetOffsetY = { fullHeight -> fullHeight }
    ) + fadeOut(
        animationSpec = tween(durationMillis = fadeDuration, easing = FastOutLinearInEasing)
    ) + scaleOut(
        animationSpec = tween(durationMillis = exitDuration, easing = EmphasizedAccelerateEasing),
        targetScale = 0.94f,
        transformOrigin = TransformOrigin(0.5f, 1.0f)
    )
}

fun buildMiniPlayerEnterTransition(
    config: SakuraAnimationConfig
): EnterTransition {
    val duration = if (config.masterEnabled && config.screenTransitionsEnabled) {
        config.transitionSpeed.baseDurationMs.coerceIn(200, 380)
    } else {
        160
    }
    return slideInVertically(
        animationSpec = tween(durationMillis = duration, easing = EmphasizedDecelerateEasing),
        initialOffsetY = { fullHeight -> fullHeight }
    ) + fadeIn(
        animationSpec = tween(durationMillis = (duration * 0.8f).toInt(), easing = LinearOutSlowInEasing)
    ) + scaleIn(
        animationSpec = tween(durationMillis = duration, easing = EmphasizedDecelerateEasing),
        initialScale = 0.96f,
        transformOrigin = TransformOrigin(0.5f, 1.0f)
    )
}

fun buildMiniPlayerExitTransition(
    config: SakuraAnimationConfig
): ExitTransition {
    val duration = if (config.masterEnabled && config.screenTransitionsEnabled) {
        (config.transitionSpeed.baseDurationMs * 0.75f).toInt().coerceIn(150, 280)
    } else {
        130
    }
    return slideOutVertically(
        animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
        targetOffsetY = { fullHeight -> -fullHeight / 2 }
    ) + fadeOut(
        animationSpec = tween(durationMillis = duration, easing = FastOutLinearInEasing)
    ) + scaleOut(
        animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
        targetScale = 0.97f,
        transformOrigin = TransformOrigin(0.5f, 0.0f)
    )
}

fun Modifier.sakuraPressScale(
    pressedScale: Float = 0.95f,
    enabled: Boolean = true
): Modifier = composed {
    val animState = LocalSakuraAnimationState.current
    if (!enabled || !animState.config.masterEnabled) return@composed this

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "sakura_press_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    waitForUpOrCancellation()
                    isPressed = false
                }
            }
        }
}
