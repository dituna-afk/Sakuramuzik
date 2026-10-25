package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class WallpaperTargetScreen(val key: String, val displayName: String) {
    HOME("home", "Ana Ekran & Genel Arka Plan (GIF / Görsel)"),
    NOW_PLAYING("player", "Tam Ekran Müzik Çalar (GIF / Görsel)"),
    MINI_PLAYER("mini_player", "Alt Mini Oynatıcı (GIF / Görsel)"),
    HEADER_BANNER("header_banner", "Liste & Kitaplık Üst Başlığı (GIF / Görsel)")
}

data class CustomMinimalThemeConfig(
    val backgroundHex: String = "#0B0E14",
    val surfaceHex: String = "#151A24",
    val accentHex: String = "#38BDF8",
    val textHex: String = "#F8FAFC",
    val homeWallpaperPath: String? = null,
    val homeWallpaperOpacity: Float = 0.35f,
    val playerWallpaperPath: String? = null,
    val playerWallpaperOpacity: Float = 0.40f,
    val miniPlayerWallpaperPath: String? = null,
    val miniPlayerWallpaperOpacity: Float = 0.45f,
    val headerBannerWallpaperPath: String? = null,
    val headerBannerWallpaperOpacity: Float = 0.55f,
    val customFontPath: String? = null,
    val customFontName: String? = null
)

val LocalCustomMinimalThemeConfig = staticCompositionLocalOf {
    CustomMinimalThemeConfig()
}

object CustomMinimalThemeManager {
    private const val PREFS_NAME = "sakura_sade_tema_prefs"
    private const val KEY_BG_HEX = "bg_hex"
    private const val KEY_SURFACE_HEX = "surface_hex"
    private const val KEY_ACCENT_HEX = "accent_hex"
    private const val KEY_TEXT_HEX = "text_hex"
    private const val KEY_HOME_WP_PATH = "home_wp_path"
    private const val KEY_HOME_WP_OPACITY = "home_wp_opacity"
    private const val KEY_PLAYER_WP_PATH = "player_wp_path"
    private const val KEY_PLAYER_WP_OPACITY = "player_wp_opacity"
    private const val KEY_MINI_WP_PATH = "mini_wp_path"
    private const val KEY_MINI_WP_OPACITY = "mini_wp_opacity"
    private const val KEY_HEADER_WP_PATH = "header_wp_path"
    private const val KEY_HEADER_WP_OPACITY = "header_wp_opacity"
    private const val KEY_CUSTOM_FONT_PATH = "custom_font_path"
    private const val KEY_CUSTOM_FONT_NAME = "custom_font_name"

    private val _configFlow = MutableStateFlow(CustomMinimalThemeConfig())
    val configFlow: StateFlow<CustomMinimalThemeConfig> = _configFlow.asStateFlow()

    @Volatile
    private var isInitialized = false

    @Volatile
    private var cachedFontPath: String? = null

    @Volatile
    private var cachedFontFamily: FontFamily? = null

    fun ensureInitialized(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _configFlow.value = CustomMinimalThemeConfig(
                backgroundHex = prefs.getString(KEY_BG_HEX, "#0B0E14") ?: "#0B0E14",
                surfaceHex = prefs.getString(KEY_SURFACE_HEX, "#151A24") ?: "#151A24",
                accentHex = prefs.getString(KEY_ACCENT_HEX, "#38BDF8") ?: "#38BDF8",
                textHex = prefs.getString(KEY_TEXT_HEX, "#F8FAFC") ?: "#F8FAFC",
                homeWallpaperPath = prefs.getString(KEY_HOME_WP_PATH, null)?.takeIf { File(it).exists() },
                homeWallpaperOpacity = prefs.getFloat(KEY_HOME_WP_OPACITY, 0.35f).coerceIn(0f, 1f),
                playerWallpaperPath = prefs.getString(KEY_PLAYER_WP_PATH, null)?.takeIf { File(it).exists() },
                playerWallpaperOpacity = prefs.getFloat(KEY_PLAYER_WP_OPACITY, 0.40f).coerceIn(0f, 1f),
                miniPlayerWallpaperPath = prefs.getString(KEY_MINI_WP_PATH, null)?.takeIf { File(it).exists() },
                miniPlayerWallpaperOpacity = prefs.getFloat(KEY_MINI_WP_OPACITY, 0.45f).coerceIn(0f, 1f),
                headerBannerWallpaperPath = prefs.getString(KEY_HEADER_WP_PATH, null)?.takeIf { File(it).exists() },
                headerBannerWallpaperOpacity = prefs.getFloat(KEY_HEADER_WP_OPACITY, 0.55f).coerceIn(0f, 1f),
                customFontPath = prefs.getString(KEY_CUSTOM_FONT_PATH, null)?.takeIf { File(it).exists() },
                customFontName = prefs.getString(KEY_CUSTOM_FONT_NAME, null)
            )
            isInitialized = true
        }
    }

    fun updateConfig(context: Context, transform: (CustomMinimalThemeConfig) -> CustomMinimalThemeConfig) {
        ensureInitialized(context)
        val updated = transform(_configFlow.value)
        _configFlow.value = updated
        saveToPrefs(context, updated)
    }

    private fun saveToPrefs(context: Context, cfg: CustomMinimalThemeConfig) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_BG_HEX, cfg.backgroundHex)
            .putString(KEY_SURFACE_HEX, cfg.surfaceHex)
            .putString(KEY_ACCENT_HEX, cfg.accentHex)
            .putString(KEY_TEXT_HEX, cfg.textHex)
            .putString(KEY_HOME_WP_PATH, cfg.homeWallpaperPath)
            .putFloat(KEY_HOME_WP_OPACITY, cfg.homeWallpaperOpacity.coerceIn(0f, 1f))
            .putString(KEY_PLAYER_WP_PATH, cfg.playerWallpaperPath)
            .putFloat(KEY_PLAYER_WP_OPACITY, cfg.playerWallpaperOpacity.coerceIn(0f, 1f))
            .putString(KEY_MINI_WP_PATH, cfg.miniPlayerWallpaperPath)
            .putFloat(KEY_MINI_WP_OPACITY, cfg.miniPlayerWallpaperOpacity.coerceIn(0f, 1f))
            .putString(KEY_HEADER_WP_PATH, cfg.headerBannerWallpaperPath)
            .putFloat(KEY_HEADER_WP_OPACITY, cfg.headerBannerWallpaperOpacity.coerceIn(0f, 1f))
            .putString(KEY_CUSTOM_FONT_PATH, cfg.customFontPath)
            .putString(KEY_CUSTOM_FONT_NAME, cfg.customFontName)
            .apply()
    }

    /**
     * Imports static images, animated GIFs, or animated WebP files from gallery without losing animation frames.
     */
    suspend fun importWallpaperFromUri(
        context: Context,
        targetScreen: WallpaperTargetScreen,
        uri: Uri
    ): String? = withContext(Dispatchers.IO) {
        try {
            val appCtx = context.applicationContext
            val mimeType = appCtx.contentResolver.getType(uri)?.lowercase(Locale.US).orEmpty()
            val displayName = resolveDisplayName(appCtx, uri)?.lowercase(Locale.US).orEmpty()
            val ext = when {
                mimeType.contains("gif") || displayName.endsWith(".gif") -> "gif"
                mimeType.contains("webp") || displayName.endsWith(".webp") -> "webp"
                mimeType.contains("png") || displayName.endsWith(".png") -> "png"
                else -> "jpg"
            }

            val dir = File(appCtx.filesDir, "sade_tema_assets").apply { mkdirs() }
            val destFile = File(dir, "wp_${targetScreen.key}_${System.currentTimeMillis()}.$ext")
            appCtx.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0L) {
                val path = destFile.absolutePath
                updateConfig(appCtx) { cur ->
                    when (targetScreen) {
                        WallpaperTargetScreen.HOME -> cur.copy(homeWallpaperPath = path)
                        WallpaperTargetScreen.NOW_PLAYING -> cur.copy(playerWallpaperPath = path)
                        WallpaperTargetScreen.MINI_PLAYER -> cur.copy(miniPlayerWallpaperPath = path)
                        WallpaperTargetScreen.HEADER_BANNER -> cur.copy(headerBannerWallpaperPath = path)
                    }
                }
                path
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun clearWallpaper(context: Context, targetScreen: WallpaperTargetScreen) {
        updateConfig(context) { cur ->
            when (targetScreen) {
                WallpaperTargetScreen.HOME -> cur.copy(homeWallpaperPath = null)
                WallpaperTargetScreen.NOW_PLAYING -> cur.copy(playerWallpaperPath = null)
                WallpaperTargetScreen.MINI_PLAYER -> cur.copy(miniPlayerWallpaperPath = null)
                WallpaperTargetScreen.HEADER_BANNER -> cur.copy(headerBannerWallpaperPath = null)
            }
        }
    }

    suspend fun importCustomFontFromUri(
        context: Context,
        uri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val appCtx = context.applicationContext
            val displayName = resolveDisplayName(appCtx, uri) ?: "OzelFont.ttf"
            val ext = if (displayName.endsWith(".otf", ignoreCase = true)) "otf" else "ttf"
            val dir = File(appCtx.filesDir, "sade_tema_assets").apply { mkdirs() }
            val destFile = File(dir, "custom_font_${System.currentTimeMillis()}.$ext")
            appCtx.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 128L) {
                val testFamily = FontFamily(
                    Font(file = destFile, weight = FontWeight.Normal),
                    Font(file = destFile, weight = FontWeight.Medium),
                    Font(file = destFile, weight = FontWeight.SemiBold),
                    Font(file = destFile, weight = FontWeight.Bold)
                )
                cachedFontPath = destFile.absolutePath
                cachedFontFamily = testFamily
                updateConfig(appCtx) { cur ->
                    cur.copy(
                        customFontPath = destFile.absolutePath,
                        customFontName = displayName
                    )
                }
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun clearCustomFont(context: Context) {
        cachedFontPath = null
        cachedFontFamily = null
        updateConfig(context) { cur ->
            cur.copy(customFontPath = null, customFontName = null)
        }
    }

    fun resolveCustomFontFamily(fontPath: String?): FontFamily? {
        if (fontPath.isNullOrBlank()) return null
        if (cachedFontPath == fontPath && cachedFontFamily != null) {
            return cachedFontFamily
        }
        return try {
            val file = File(fontPath)
            if (!file.exists() || file.length() <= 128L) return null
            val family = FontFamily(
                Font(file = file, weight = FontWeight.Normal),
                Font(file = file, weight = FontWeight.Medium),
                Font(file = file, weight = FontWeight.SemiBold),
                Font(file = file, weight = FontWeight.Bold)
            )
            cachedFontPath = fontPath
            cachedFontFamily = family
            family
        } catch (_: Throwable) {
            null
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun parseHexColor(hexString: String, fallback: Color): Color {
        val cleaned = hexString.trim().removePrefix("#")
        if (cleaned.length != 6 && cleaned.length != 8) return fallback
        return try {
            val argbLong = if (cleaned.length == 6) {
                ("FF$cleaned").toLong(16)
            } else {
                cleaned.toLong(16)
            }
            Color(argbLong)
        } catch (_: Exception) {
            fallback
        }
    }
}
