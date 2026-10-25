package com.example.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DynamicSakuraPaletteExtractor {

    private val paletteCache = LruCache<String, AppThemePalette>(48)

    private fun cacheKey(imageUrl: String, baseTheme: AppTheme): String {
        return "${baseTheme.name}|$imageUrl"
    }

    @Composable
    fun rememberDynamicSakuraPalette(
        imageUrl: String?,
        baseTheme: AppTheme,
        enabled: Boolean = true
    ): State<AppThemePalette?> {
        val initialCached = remember(imageUrl, baseTheme, enabled) {
            if (!enabled || imageUrl.isNullOrBlank()) null
            else paletteCache.get(cacheKey(imageUrl, baseTheme))
        }
        val state = remember(baseTheme) { mutableStateOf<AppThemePalette?>(initialCached) }

        LaunchedEffect(imageUrl, baseTheme, enabled) {
            if (!enabled || imageUrl.isNullOrBlank()) {
                state.value = null
                return@LaunchedEffect
            }
            val key = cacheKey(imageUrl, baseTheme)
            val cached = paletteCache.get(key)
            if (cached != null) {
                state.value = cached
                return@LaunchedEffect
            }
            val extracted = withContext(Dispatchers.IO) {
                extractPaletteFromArtwork(imageUrl, baseTheme)
            }
            if (extracted != null) {
                paletteCache.put(key, extracted)
            }
            state.value = extracted
        }

        return state
    }

    suspend fun prewarmPalette(imageUrl: String?, baseTheme: AppTheme) {
        if (imageUrl.isNullOrBlank()) return
        val key = cacheKey(imageUrl, baseTheme)
        if (paletteCache.get(key) != null) return
        withContext(Dispatchers.IO) {
            val extracted = extractPaletteFromArtwork(imageUrl, baseTheme)
            if (extracted != null) {
                paletteCache.put(key, extracted)
            }
        }
    }

    private fun extractPaletteFromArtwork(
        imageUrl: String,
        baseTheme: AppTheme
    ): AppThemePalette? {
        val bmp = loadSmallBitmap(imageUrl) ?: return null
        return try {
            val dominantColor = computeAverageVibrantColor(bmp) ?: return null
            val basePalette = baseTheme.toPalette()

            val blendedAccentArgb = ColorUtils.blendARGB(
                basePalette.primaryAccent.toArgb(),
                dominantColor.toArgb(),
                0.62f
            )
            val blendedSecondaryArgb = ColorUtils.blendARGB(
                basePalette.secondaryAccent.toArgb(),
                dominantColor.toArgb(),
                0.48f
            )
            val blendedTertiaryArgb = ColorUtils.blendARGB(
                basePalette.tertiaryAccent.toArgb(),
                dominantColor.toArgb(),
                0.42f
            )
            val blendedGlowArgb = ColorUtils.blendARGB(
                basePalette.haloGlowPrimary.toArgb(),
                dominantColor.toArgb(),
                0.68f
            )
            val blendedSurfaceArgb = ColorUtils.blendARGB(
                basePalette.surface.toArgb(),
                dominantColor.toArgb(),
                if (baseTheme.isDark) 0.16f else 0.10f
            )
            val blendedSurfaceContainerArgb = ColorUtils.blendARGB(
                basePalette.surfaceContainer.toArgb(),
                dominantColor.toArgb(),
                if (baseTheme.isDark) 0.18f else 0.12f
            )
            val blendedPetalPrimaryArgb = ColorUtils.blendARGB(
                basePalette.petalPrimary.toArgb(),
                dominantColor.toArgb(),
                0.55f
            )
            val blendedPetalSecondaryArgb = ColorUtils.blendARGB(
                basePalette.petalSecondary.toArgb(),
                dominantColor.toArgb(),
                0.45f
            )

            val newPrimary = Color(blendedAccentArgb)
            val onPrimary = if (newPrimary.luminance() > 0.45f) Color(0xFF220510) else Color.White

            basePalette.copy(
                primaryAccent = newPrimary,
                secondaryAccent = Color(blendedSecondaryArgb),
                tertiaryAccent = Color(blendedTertiaryArgb),
                petalPrimary = Color(blendedPetalPrimaryArgb),
                petalSecondary = Color(blendedPetalSecondaryArgb),
                haloGlowPrimary = Color(blendedGlowArgb),
                surface = Color(blendedSurfaceArgb),
                surfaceContainer = Color(blendedSurfaceContainerArgb),
                onPrimary = onPrimary
            )
        } catch (_: Exception) {
            null
        } finally {
            bmp.recycle()
        }
    }

    private fun loadSmallBitmap(pathOrUrl: String): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 8
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
                val conn = (URL(pathOrUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4_000
                    readTimeout = 4_000
                }
                conn.inputStream.use { BitmapFactory.decodeStream(it, null, opts) }
            } else {
                val file = File(pathOrUrl)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath, opts) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun computeAverageVibrantColor(bitmap: Bitmap): Color? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0L
        val hsl = FloatArray(3)

        val stepX = (width / 14).coerceAtLeast(1)
        val stepY = (height / 14).coerceAtLeast(1)

        for (y in 0 until height step stepY) {
            for (x in 0 until width step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                ColorUtils.RGBToHSL(r, g, b, hsl)
                if (hsl[1] >= 0.18f && hsl[2] in 0.15f..0.85f) {
                    rSum += r
                    gSum += g
                    bSum += b
                    count++
                }
            }
        }

        if (count == 0L) return null
        return Color(
            red = (rSum / count).toInt().coerceIn(0, 255),
            green = (gSum / count).toInt().coerceIn(0, 255),
            blue = (bSum / count).toInt().coerceIn(0, 255)
        )
    }
}
