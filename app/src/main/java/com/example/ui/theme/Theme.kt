package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.data.CowboyBebopThemeConfig
import com.example.data.CowboyBebopThemeManager
import com.example.data.CustomMinimalThemeConfig
import com.example.data.CustomMinimalThemeManager
import com.example.data.LocalCustomMinimalThemeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AppTheme(
    val displayName: String,
    val subtitle: String,
    val isDark: Boolean
) {
    // 6 Super Optimized Themes (Zero particle lag, GPU-friendly vertical gradient + vibrant accent)
    YT_MUSIC_PINK(
        displayName = "YT Müzik Pembe Tema",
        subtitle = "Derin fuşya-bordo (#24071C → #090208) geçişli zemin ve canlı #FF2A70 pembe vurgular",
        isDark = true
    ),
    AMOLED_NIGHT(
        displayName = "AMOLED Gece",
        subtitle = "Ultra derin karbon siyah (#0A0A0C → #000000) zemin, #FF2A70 pembe ve #00E5FF siber mavi",
        isDark = true
    ),
    EMERALD_DEPTH(
        displayName = "Zümrüt Derinliği",
        subtitle = "Gece zümrüdü (#07261E → #020A08) dikey gradyan ve parlak #10B981 zümrüt yeşili",
        isDark = true
    ),
    SAPPHIRE_OCEAN(
        displayName = "Safir Okyanus",
        subtitle = "Derin okyanus laciverti (#0A1931 → #030710) zemin ve #3B82F6 safir mavisi vurgular",
        isDark = true
    ),
    CRIMSON_VELVET(
        displayName = "Kızıl Kadife",
        subtitle = "Asil bordo-kadife (#3B0711 → #0A0203) zemin ve #F43F5E yakut kırmızısı",
        isDark = true
    ),
    ROYAL_AMETHYST(
        displayName = "Asil Ametist",
        subtitle = "Gece moru (#240E3B → #08030F) dikey gradyan ve #A855F7 ametist eflatun",
        isDark = true
    ),

    // Custom Minimal & Personalized Theme
    SADE_TEMA(
        displayName = "Özel HEX & GIF Kişiselleştirme",
        subtitle = "Kendi HEX renklerinizi, ekran bazlı GIF/görsel arka planlarınızı ve özel fontunuzu kullanın",
        isDark = true
    ),

    // Sakura Themes
    SAKURA_LIGHT(
        displayName = "Sakura Açık (Klasik)",
        subtitle = "Zarif krem zemin, kiraz çiçeği pembesi ve sıcak gül vurgular",
        isDark = false
    ),
    SAKURA_DARK(
        displayName = "Sakura Koyu (Gece Çiçeği)",
        subtitle = "Derin gece antrasiti üzerinde parlak sakura pembesi detaylar",
        isDark = true
    ),
    ROSE_QUARTZ(
        displayName = "Gül Kuvars (Romantik)",
        subtitle = "Yumuşak pastel gül tonları ve ipeksi geçişler",
        isDark = false
    ),
    CRIMSON_BLOSSOM(
        displayName = "Kızıl Kiraz (Gece Yarısı)",
        subtitle = "Koyu bordo-siyah atmosfer ve canlı mercan-pembe vurgular",
        isDark = true
    ),
    AMOLED_SAKURA(
        displayName = "Saf Siyah AMOLED Sakura",
        subtitle = "#000000 OLED pil tasarruflu zemin ve neon sakura kontrastı",
        isDark = true
    ),

    // Anime Themes
    BERSERK_GUTS(
        displayName = "Berserk - Guts (Kurban Damgası)",
        subtitle = "Karanlık obsidyen çelik, ejderha katili kılıcı ve kan kızılı",
        isDark = true
    ),
    BERSERK_GRIFFITH(
        displayName = "Berserk - Griffith (Işığın Şahini)",
        subtitle = "Mat gece siyahı (#0B0D13), soğuk çelik arduvaz (#121622) ve asil gümüş-buz mavisi (#C8D6E5)",
        isDark = true
    ),
    COWBOY_BEBOP_SPIKE(
        displayName = "Cowboy Bebop — Faye & Spike (Gece Mor-Kehribar)",
        subtitle = "Gece dostu koyu lacivert/kömür zemin, dumanlı erik moru (#58356E) ve mat kehribar altın (#D4A338)",
        isDark = true
    );

    companion object {
        fun fromName(name: String?): AppTheme {
            if (name == "COWBOY_BEBOP" || name == "COWBOY_BEBOP_FAYE") {
                return COWBOY_BEBOP_SPIKE
            }
            return entries.find { it.name == name } ?: YT_MUSIC_PINK
        }

        fun superOptimizedThemes(): List<AppTheme> = listOf(
            YT_MUSIC_PINK,
            AMOLED_NIGHT,
            EMERALD_DEPTH,
            SAPPHIRE_OCEAN,
            CRIMSON_VELVET,
            ROYAL_AMETHYST
        )
    }
}

fun AppTheme.isSuperOptimizedTheme(): Boolean {
    return this == AppTheme.YT_MUSIC_PINK ||
        this == AppTheme.AMOLED_NIGHT ||
        this == AppTheme.EMERALD_DEPTH ||
        this == AppTheme.SAPPHIRE_OCEAN ||
        this == AppTheme.CRIMSON_VELVET ||
        this == AppTheme.ROYAL_AMETHYST
}

fun AppTheme.isSakuraTheme(): Boolean {
    return this == AppTheme.SAKURA_LIGHT ||
        this == AppTheme.SAKURA_DARK ||
        this == AppTheme.ROSE_QUARTZ ||
        this == AppTheme.CRIMSON_BLOSSOM ||
        this == AppTheme.AMOLED_SAKURA
}

fun AppTheme.backgroundGradientColors(fallbackBg: Color): List<Color> {
    return when (this) {
        AppTheme.YT_MUSIC_PINK -> listOf(Color(0xFF24071C), Color(0xFF140410), Color(0xFF090208))
        AppTheme.AMOLED_NIGHT -> listOf(Color(0xFF0A0A0C), Color(0xFF040406), Color(0xFF000000))
        AppTheme.EMERALD_DEPTH -> listOf(Color(0xFF07261E), Color(0xFF041511), Color(0xFF020A08))
        AppTheme.SAPPHIRE_OCEAN -> listOf(Color(0xFF0A1931), Color(0xFF060E1E), Color(0xFF030710))
        AppTheme.CRIMSON_VELVET -> listOf(Color(0xFF3B0711), Color(0xFF1C0408), Color(0xFF0A0203))
        AppTheme.ROYAL_AMETHYST -> listOf(Color(0xFF240E3B), Color(0xFF130721), Color(0xFF08030F))
        AppTheme.BERSERK_GRIFFITH -> listOf(Color(0xFF121622), Color(0xFF0E111A), Color(0xFF0B0D13))
        AppTheme.COWBOY_BEBOP_SPIKE -> {
            val cb = CowboyBebopThemeManager.currentConfig
            val p = buildCowboyBebopPalette(cb)
            if (cb.cbDynamicColorTransitions) {
                listOf(p.surfaceContainer, p.surface, p.background)
            } else {
                listOf(p.background, p.background)
            }
        }
        else -> listOf(fallbackBg, fallbackBg)
    }
}

fun AppTheme.backgroundBrush(fallbackBg: Color): Brush {
    return Brush.verticalGradient(backgroundGradientColors(fallbackBg))
}

data class AppThemePalette(
    val theme: AppTheme,
    val background: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceVariant: Color,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val tertiaryAccent: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val onPrimary: Color,
    val dividerOrBorder: Color,
    val haloGlowPrimary: Color,
    val haloGlowSecondary: Color,
    val petalPrimary: Color,
    val petalSecondary: Color
)

fun buildSadeTemaPalette(cfg: CustomMinimalThemeConfig): AppThemePalette {
    val bg = CustomMinimalThemeManager.parseHexColor(cfg.backgroundHex, Color(0xFF0B0E14))
    val surf = CustomMinimalThemeManager.parseHexColor(cfg.surfaceHex, Color(0xFF151A24))
    val accent = CustomMinimalThemeManager.parseHexColor(cfg.accentHex, Color(0xFF38BDF8))
    val txt = CustomMinimalThemeManager.parseHexColor(cfg.textHex, Color(0xFFF8FAFC))

    return AppThemePalette(
        theme = AppTheme.SADE_TEMA,
        background = bg,
        surface = surf,
        surfaceContainer = surf.copy(alpha = 0.92f),
        surfaceVariant = surf.copy(alpha = 0.78f),
        primaryAccent = accent,
        secondaryAccent = accent.copy(alpha = 0.82f),
        tertiaryAccent = accent.copy(alpha = 0.65f),
        onBackground = txt,
        onSurface = txt,
        onSurfaceMuted = txt.copy(alpha = 0.68f),
        onPrimary = bg,
        dividerOrBorder = accent.copy(alpha = 0.32f),
        haloGlowPrimary = accent,
        haloGlowSecondary = accent.copy(alpha = 0.5f),
        petalPrimary = accent,
        petalSecondary = txt
    )
}

fun AppTheme.toPalette(): AppThemePalette {
    return when (this) {
        AppTheme.YT_MUSIC_PINK -> AppThemePalette(
            theme = this,
            background = Color(0xFF090208),
            surface = Color(0xFF140410),
            surfaceContainer = Color(0xFF24071C),
            surfaceVariant = Color(0xFF300B26),
            primaryAccent = Color(0xFFFF2A70),
            secondaryAccent = Color(0xFFFF6E9E),
            tertiaryAccent = Color(0xFFD81B60),
            onBackground = Color(0xFFFFF5F8),
            onSurface = Color(0xFFFFECF2),
            onSurfaceMuted = Color(0xFFD4A3B8),
            onPrimary = Color(0xFFFFFFFF),
            dividerOrBorder = Color(0xFF421235),
            haloGlowPrimary = Color(0xFFFF2A70),
            haloGlowSecondary = Color(0xFFFF6E9E),
            petalPrimary = Color(0xFFFF2A70),
            petalSecondary = Color(0xFFFF6E9E)
        )

        AppTheme.AMOLED_NIGHT -> AppThemePalette(
            theme = this,
            background = Color(0xFF000000),
            surface = Color(0xFF040406),
            surfaceContainer = Color(0xFF0A0A0C),
            surfaceVariant = Color(0xFF141418),
            primaryAccent = Color(0xFFFF2A70),
            secondaryAccent = Color(0xFF00E5FF),
            tertiaryAccent = Color(0xFFFF2A70),
            onBackground = Color(0xFFFFFFFF),
            onSurface = Color(0xFFF8FAFC),
            onSurfaceMuted = Color(0xFF94A3B8),
            onPrimary = Color(0xFFFFFFFF),
            dividerOrBorder = Color(0xFF1E2028),
            haloGlowPrimary = Color(0xFFFF2A70),
            haloGlowSecondary = Color(0xFF00E5FF),
            petalPrimary = Color(0xFFFF2A70),
            petalSecondary = Color(0xFF00E5FF)
        )

        AppTheme.EMERALD_DEPTH -> AppThemePalette(
            theme = this,
            background = Color(0xFF020A08),
            surface = Color(0xFF041511),
            surfaceContainer = Color(0xFF07261E),
            surfaceVariant = Color(0xFF0C382C),
            primaryAccent = Color(0xFF10B981),
            secondaryAccent = Color(0xFF34D399),
            tertiaryAccent = Color(0xFF059669),
            onBackground = Color(0xFFECFDF5),
            onSurface = Color(0xFFD1FAE5),
            onSurfaceMuted = Color(0xFF86EFAC),
            onPrimary = Color(0xFF02120E),
            dividerOrBorder = Color(0xFF134E3E),
            haloGlowPrimary = Color(0xFF10B981),
            haloGlowSecondary = Color(0xFF34D399),
            petalPrimary = Color(0xFF10B981),
            petalSecondary = Color(0xFF34D399)
        )

        AppTheme.SAPPHIRE_OCEAN -> AppThemePalette(
            theme = this,
            background = Color(0xFF030710),
            surface = Color(0xFF060E1E),
            surfaceContainer = Color(0xFF0A1931),
            surfaceVariant = Color(0xFF12284C),
            primaryAccent = Color(0xFF3B82F6),
            secondaryAccent = Color(0xFF60A5FA),
            tertiaryAccent = Color(0xFF2563EB),
            onBackground = Color(0xFFEFF6FF),
            onSurface = Color(0xFFDBEAFE),
            onSurfaceMuted = Color(0xFF93C5FD),
            onPrimary = Color(0xFFFFFFFF),
            dividerOrBorder = Color(0xFF1E3A6D),
            haloGlowPrimary = Color(0xFF3B82F6),
            haloGlowSecondary = Color(0xFF60A5FA),
            petalPrimary = Color(0xFF3B82F6),
            petalSecondary = Color(0xFF60A5FA)
        )

        AppTheme.CRIMSON_VELVET -> AppThemePalette(
            theme = this,
            background = Color(0xFF0A0203),
            surface = Color(0xFF1C0408),
            surfaceContainer = Color(0xFF3B0711),
            surfaceVariant = Color(0xFF4C0A17),
            primaryAccent = Color(0xFFF43F5E),
            secondaryAccent = Color(0xFFFB7185),
            tertiaryAccent = Color(0xFFE11D48),
            onBackground = Color(0xFFFFF1F2),
            onSurface = Color(0xFFFFE4E6),
            onSurfaceMuted = Color(0xFFFDA4AF),
            onPrimary = Color(0xFFFFFFFF),
            dividerOrBorder = Color(0xFF661020),
            haloGlowPrimary = Color(0xFFF43F5E),
            haloGlowSecondary = Color(0xFFFB7185),
            petalPrimary = Color(0xFFF43F5E),
            petalSecondary = Color(0xFFFB7185)
        )

        AppTheme.ROYAL_AMETHYST -> AppThemePalette(
            theme = this,
            background = Color(0xFF08030F),
            surface = Color(0xFF130721),
            surfaceContainer = Color(0xFF240E3B),
            surfaceVariant = Color(0xFF341554),
            primaryAccent = Color(0xFFA855F7),
            secondaryAccent = Color(0xFFC084FC),
            tertiaryAccent = Color(0xFF9333EA),
            onBackground = Color(0xFFFAF5FF),
            onSurface = Color(0xFFF3E8FF),
            onSurfaceMuted = Color(0xFFD8B4FE),
            onPrimary = Color(0xFFFFFFFF),
            dividerOrBorder = Color(0xFF481E73),
            haloGlowPrimary = Color(0xFFA855F7),
            haloGlowSecondary = Color(0xFFC084FC),
            petalPrimary = Color(0xFFA855F7),
            petalSecondary = Color(0xFFC084FC)
        )

        AppTheme.SADE_TEMA -> buildSadeTemaPalette(CustomMinimalThemeManager.configFlow.value)

        AppTheme.SAKURA_LIGHT -> AppThemePalette(
            theme = this,
            background = Color(0xFFFFF8FA),
            surface = Color(0xFFFFF0F5),
            surfaceContainer = Color(0xFFFFE5EE),
            surfaceVariant = Color(0xFFFAD8E4),
            primaryAccent = Color(0xFFD84B7E),
            secondaryAccent = Color(0xFFF078A0),
            tertiaryAccent = Color(0xFFB53665),
            onBackground = Color(0xFF2B141D),
            onSurface = Color(0xFF331823),
            onSurfaceMuted = Color(0xFF7D5263),
            onPrimary = Color(0xFFFFFFFF),
            dividerOrBorder = Color(0xFFF2C2D4),
            haloGlowPrimary = Color(0xFFFF8CB6),
            haloGlowSecondary = Color(0xFFFFC1D8),
            petalPrimary = Color(0xFFFF9EBF),
            petalSecondary = Color(0xFFFFC8DD)
        )

        AppTheme.SAKURA_DARK -> AppThemePalette(
            theme = this,
            background = Color(0xFF130B10),
            surface = Color(0xFF1E121A),
            surfaceContainer = Color(0xFF291823),
            surfaceVariant = Color(0xFF382130),
            primaryAccent = Color(0xFFFF7AA8),
            secondaryAccent = Color(0xFFFFA3C4),
            tertiaryAccent = Color(0xFFE0487E),
            onBackground = Color(0xFFFFEEF4),
            onSurface = Color(0xFFFFE6EF),
            onSurfaceMuted = Color(0xFFC99BAE),
            onPrimary = Color(0xFF280614),
            dividerOrBorder = Color(0xFF472A3C),
            haloGlowPrimary = Color(0xFFFF6B9E),
            haloGlowSecondary = Color(0xFFB8396A),
            petalPrimary = Color(0xFFFF85B1),
            petalSecondary = Color(0xFFFFB5D0)
        )

        AppTheme.ROSE_QUARTZ -> AppThemePalette(
            theme = this,
            background = Color(0xFFFDF6F8),
            surface = Color(0xFFF8EBEF),
            surfaceContainer = Color(0xFFF2DDE4),
            surfaceVariant = Color(0xFFE8CCD6),
            primaryAccent = Color(0xFFC4517A),
            secondaryAccent = Color(0xFFDE7C9F),
            tertiaryAccent = Color(0xFF9C375B),
            onBackground = Color(0xFF2D1720),
            onSurface = Color(0xFF361C26),
            onSurfaceMuted = Color(0xFF7A5564),
            onPrimary = Color(0xFFFFFFFF),
            dividerOrBorder = Color(0xFFE3BDCB),
            haloGlowPrimary = Color(0xFFE886A9),
            haloGlowSecondary = Color(0xFFF5B8CE),
            petalPrimary = Color(0xFFF098B7),
            petalSecondary = Color(0xFFFAD0DF)
        )

        AppTheme.CRIMSON_BLOSSOM -> AppThemePalette(
            theme = this,
            background = Color(0xFF17080D),
            surface = Color(0xFF240D15),
            surfaceContainer = Color(0xFF32131E),
            surfaceVariant = Color(0xFF451B2A),
            primaryAccent = Color(0xFFFF5C8A),
            secondaryAccent = Color(0xFFFF8FA3),
            tertiaryAccent = Color(0xFFC9184A),
            onBackground = Color(0xFFFFEDF2),
            onSurface = Color(0xFFFFE3EC),
            onSurfaceMuted = Color(0xFFD193A6),
            onPrimary = Color(0xFF2B030E),
            dividerOrBorder = Color(0xFF592336),
            haloGlowPrimary = Color(0xFFFF4D7D),
            haloGlowSecondary = Color(0xFFA4133C),
            petalPrimary = Color(0xFFFF7597),
            petalSecondary = Color(0xFFFFB3C6)
        )

        AppTheme.AMOLED_SAKURA -> AppThemePalette(
            theme = this,
            background = Color(0xFF000000),
            surface = Color(0xFF0D070A),
            surfaceContainer = Color(0xFF180D13),
            surfaceVariant = Color(0xFF26151F),
            primaryAccent = Color(0xFFFF80B0),
            secondaryAccent = Color(0xFFFFAED0),
            tertiaryAccent = Color(0xFFE84A86),
            onBackground = Color(0xFFFFF2F7),
            onSurface = Color(0xFFFFEBF3),
            onSurfaceMuted = Color(0xFFBFA0AE),
            onPrimary = Color(0xFF240310),
            dividerOrBorder = Color(0xFF331C29),
            haloGlowPrimary = Color(0xFFFF70A6),
            haloGlowSecondary = Color(0xFF9E2A5B),
            petalPrimary = Color(0xFFFF8AB6),
            petalSecondary = Color(0xFFFFC2DA)
        )

        AppTheme.BERSERK_GUTS -> AppThemePalette(
            theme = this,
            background = Color(0xFF000000),
            surface = Color(0xFF070204),
            surfaceContainer = Color(0xFF110306),
            surfaceVariant = Color(0xFF1C050A),
            primaryAccent = Color(0xFFFF1E27),
            secondaryAccent = Color(0xFFDC2626),
            tertiaryAccent = Color(0xFF991B1B),
            onBackground = Color(0xFFFFFFFF),
            onSurface = Color(0xFFFFFFFF),
            onSurfaceMuted = Color(0xFFE2E8F0),
            onPrimary = Color(0xFFFFFFFF),
            dividerOrBorder = Color(0xFF7F1D1D),
            haloGlowPrimary = Color(0xFFFF1E27),
            haloGlowSecondary = Color(0xFF450A0A),
            petalPrimary = Color(0xFFFF1E27),
            petalSecondary = Color(0xFFFFFFFF)
        )

        // Redesigned BERSERK_GRIFFITH: Noble dark night black (#0B0D13), dark slate (#121622),
        // silver hair (#C8D6E5) and cold ice-steel blue (#8395A7) — zero floating particles/smoke!
        AppTheme.BERSERK_GRIFFITH -> AppThemePalette(
            theme = this,
            background = Color(0xFF0B0D13),
            surface = Color(0xFF121622),
            surfaceContainer = Color(0xFF121622),
            surfaceVariant = Color(0xFF181E2C),
            primaryAccent = Color(0xFFC8D6E5),
            secondaryAccent = Color(0xFF8395A7),
            tertiaryAccent = Color(0xFF576574),
            onBackground = Color(0xFFF1F5F9),
            onSurface = Color(0xFFE2E8F0),
            onSurfaceMuted = Color(0xFF94A3B8),
            onPrimary = Color(0xFF0B0D13),
            dividerOrBorder = Color(0xFF222B3B),
            haloGlowPrimary = Color(0xFFC8D6E5),
            haloGlowSecondary = Color(0xFF8395A7),
            petalPrimary = Color(0xFFC8D6E5),
            petalSecondary = Color(0xFF8395A7)
        )

        AppTheme.COWBOY_BEBOP_SPIKE -> buildCowboyBebopPalette(CowboyBebopThemeManager.currentConfig)
    }
}

private fun blendChannel(a: Float, b: Float, fraction: Float): Float {
    val f = fraction.coerceIn(0f, 1f)
    return (a + (b - a) * f).coerceIn(0f, 1f)
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = blendChannel(start.red, end.red, f),
        green = blendChannel(start.green, end.green, f),
        blue = blendChannel(start.blue, end.blue, f),
        alpha = blendChannel(start.alpha, end.alpha, f)
    )
}

private fun scaleColorLuminance(color: Color, factor: Float): Color {
    val clamped = factor.coerceIn(0.35f, 1.45f)
    return Color(
        red = (color.red * clamped).coerceIn(0f, 1f),
        green = (color.green * clamped).coerceIn(0f, 1f),
        blue = (color.blue * clamped).coerceIn(0f, 1f),
        alpha = color.alpha
    )
}

/**
 * Cowboy Bebop — Faye Valentine mor-sarı gece dostu palet hesaplayıcısı.
 * Görsel parlaklık (%0..%100), mor yoğunluğu (%0..%100), sarı/altın yoğunluğu (%0..%100)
 * ve arka plan karanlığı (%0..%100) birbirinden bağımsız katmanlar olarak uygulanır.
 * Sistem parlaklığına dokunmaz ve metin okunabilirliğini daima korur.
 */
fun buildCowboyBebopPalette(cfg: CowboyBebopThemeConfig): AppThemePalette {
    val c = cfg.validated()
    // 0% -> 0.72f, 50% (default) -> 1.0f, 100% -> 1.28f (theme-internal visual brightness only)
    val brightnessFactor = 0.72f + (c.cbVisualBrightness / 100f) * 0.56f
    val purpleFrac = (c.cbPurpleIntensity / 100f).coerceIn(0f, 1f)
    val amberFrac = (c.cbAmberGoldIntensity / 100f).coerceIn(0f, 1f)
    val darknessFrac = (c.cbBackgroundDarkness / 100f).coerceIn(0f, 1f)

    // Background layer: night-friendly dark navy (#0A0D16), charcoal (#07080D), and deep purple (#110B1A)
    val baseCharcoal = if (c.cbNightFriendlyPalette) Color(0xFF07080D) else Color(0xFF0C0F1A)
    val baseNavy = if (c.cbNightFriendlyPalette) Color(0xFF0A0D16) else Color(0xFF121728)
    val deepPlumBg = if (c.cbFayePurpleGoldStyle) Color(0xFF140C1F) else Color(0xFF0D1322)

    val rawBg = lerpColor(
        lerpColor(baseNavy, deepPlumBg, purpleFrac * 0.55f),
        baseCharcoal,
        darknessFrac * 0.75f
    )
    val background = scaleColorLuminance(rawBg, 0.80f + (1f - darknessFrac) * 0.25f * brightnessFactor)

    // Surface & container layers: dark plum (#3A1F47) and smoky purple (#58356E) blended with night navy
    val neutralSurface = Color(0xFF101522)
    val plumSurface = if (c.cbFayePurpleGoldStyle) Color(0xFF23142E) else Color(0xFF161E31)
    val smokyPurpleContainer = if (c.cbFayePurpleGoldStyle) Color(0xFF3A1F47) else Color(0xFF1E2942)
    val smokyPurpleVariant = if (c.cbFayePurpleGoldStyle) Color(0xFF58356E) else Color(0xFF2B3B5E)

    val surface = scaleColorLuminance(
        lerpColor(neutralSurface, plumSurface, purpleFrac),
        brightnessFactor * (1.08f - darknessFrac * 0.22f)
    )
    val surfaceContainer = scaleColorLuminance(
        lerpColor( Color(0xFF161C2E), smokyPurpleContainer, purpleFrac),
        brightnessFactor * (1.10f - darknessFrac * 0.20f)
    )
    val surfaceVariant = scaleColorLuminance(
        lerpColor(Color(0xFF202940), smokyPurpleVariant, purpleFrac),
        brightnessFactor * (1.12f - darknessFrac * 0.18f)
    )

    // Primary Accent (Amber/Gold layer): dark gold (#B88728) to matte amber (#D4A338) to soft gold (#E5BA54)
    val mutedGold = Color(0xFF9E762A)
    val matteAmber = if (c.cbNightFriendlyPalette) Color(0xFFD4A338) else Color(0xFFE5B53B)
    val warmGold = if (c.cbNightFriendlyPalette) Color(0xFFE2B64E) else Color(0xFFF2C94C)
    val rawPrimaryAmber = if (amberFrac <= 0.5f) {
        lerpColor(mutedGold, matteAmber, amberFrac * 2f)
    } else {
        lerpColor(matteAmber, warmGold, (amberFrac - 0.5f) * 2f)
    }
    val primaryAccent = scaleColorLuminance(rawPrimaryAmber, 0.88f + (c.cbVisualBrightness / 100f) * 0.24f)

    // Secondary Accent (Smoky Purple / Faye Violet layer)
    val mutedViolet = Color(0xFF6E5A7F)
    val fayeSmokyPurple = if (c.cbFayePurpleGoldStyle) Color(0xFF9B6BB8) else Color(0xFF6488B8)
    val richOrchid = if (c.cbFayePurpleGoldStyle) Color(0xFFB583D1) else Color(0xFF7FA6D9)
    val rawSecondaryPurple = if (purpleFrac <= 0.5f) {
        lerpColor(mutedViolet, fayeSmokyPurple, purpleFrac * 2f)
    } else {
        lerpColor(fayeSmokyPurple, richOrchid, (purpleFrac - 0.5f) * 2f)
    }
    val secondaryAccent = scaleColorLuminance(rawSecondaryPurple, 0.88f + (c.cbVisualBrightness / 100f) * 0.24f)

    val tertiaryAccent = lerpColor(Color(0xFFB88728), secondaryAccent, 0.45f)

    // Readable text colors preserved regardless of darkness/brightness extremes
    val onBackground = lerpColor(Color(0xFFE6E2EC), Color(0xFFF7F4FA), (c.cbVisualBrightness / 100f))
    val onSurface = lerpColor(Color(0xFFDFDAE8), Color(0xFFF3EFF8), (c.cbVisualBrightness / 100f))
    val onSurfaceMuted = lerpColor(Color(0xFF9E94AF), Color(0xFFC2B7D4), (purpleFrac * 0.5f + 0.25f))

    val borderBase = lerpColor(Color(0xFF282036), Color(0xFF4B3061), purpleFrac)
    val dividerOrBorder = lerpColor(borderBase, primaryAccent.copy(alpha = 0.45f), amberFrac * 0.25f)

    val glowAlpha = if (c.cbGlowEffectsEnabled) 0.85f else 0.35f

    return AppThemePalette(
        theme = AppTheme.COWBOY_BEBOP_SPIKE,
        background = background,
        surface = surface,
        surfaceContainer = surfaceContainer,
        surfaceVariant = surfaceVariant,
        primaryAccent = primaryAccent,
        secondaryAccent = secondaryAccent,
        tertiaryAccent = tertiaryAccent,
        onBackground = onBackground,
        onSurface = onSurface,
        onSurfaceMuted = onSurfaceMuted,
        onPrimary = Color(0xFF120C18),
        dividerOrBorder = dividerOrBorder,
        haloGlowPrimary = primaryAccent.copy(alpha = glowAlpha),
        haloGlowSecondary = secondaryAccent.copy(alpha = glowAlpha),
        petalPrimary = primaryAccent,
        petalSecondary = secondaryAccent
    )
}

val LocalAppThemePalette = compositionLocalOf {
    AppTheme.YT_MUSIC_PINK.toPalette()
}

val LocalCowboyBebopConfig = compositionLocalOf {
    CowboyBebopThemeConfig.DEFAULT
}

val Typography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun MyApplicationTheme(
    appTheme: AppTheme = AppTheme.YT_MUSIC_PINK,
    dynamicPaletteOverride: AppThemePalette? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        withContext(Dispatchers.IO) {
            CustomMinimalThemeManager.ensureInitialized(context)
        }
    }
    val sadeConfig by CustomMinimalThemeManager.configFlow.collectAsState()
    val cowboyBebopConfig by CowboyBebopThemeManager.configFlow.collectAsState()

    val targetPalette = remember(appTheme, dynamicPaletteOverride, sadeConfig, cowboyBebopConfig) {
        when {
            appTheme == AppTheme.SADE_TEMA -> buildSadeTemaPalette(sadeConfig)
            appTheme == AppTheme.COWBOY_BEBOP_SPIKE -> buildCowboyBebopPalette(cowboyBebopConfig)
            dynamicPaletteOverride != null -> dynamicPaletteOverride
            else -> appTheme.toPalette()
        }
    }

    val colorScheme = remember(targetPalette, appTheme.isDark) {
        if (appTheme.isDark) {
            darkColorScheme(
                primary = targetPalette.primaryAccent,
                onPrimary = targetPalette.onPrimary,
                secondary = targetPalette.secondaryAccent,
                tertiary = targetPalette.tertiaryAccent,
                background = targetPalette.background,
                onBackground = targetPalette.onBackground,
                surface = targetPalette.surface,
                onSurface = targetPalette.onSurface,
                surfaceVariant = targetPalette.surfaceVariant,
                onSurfaceVariant = targetPalette.onSurfaceMuted,
                outline = targetPalette.dividerOrBorder
            )
        } else {
            lightColorScheme(
                primary = targetPalette.primaryAccent,
                onPrimary = targetPalette.onPrimary,
                secondary = targetPalette.secondaryAccent,
                tertiary = targetPalette.tertiaryAccent,
                background = targetPalette.background,
                onBackground = targetPalette.onBackground,
                surface = targetPalette.surface,
                onSurface = targetPalette.onSurface,
                surfaceVariant = targetPalette.surfaceVariant,
                onSurfaceVariant = targetPalette.onSurfaceMuted,
                outline = targetPalette.dividerOrBorder
            )
        }
    }

    val customFont = remember(appTheme, sadeConfig.customFontPath) {
        appTheme.getCustomFontFamily(enabled = true)
    }
    val typography = remember(customFont) {
        if (customFont != null) {
            Typography(
                headlineMedium = TextStyle(fontFamily = customFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
                titleLarge = TextStyle(fontFamily = customFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
                titleMedium = TextStyle(fontFamily = customFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
                bodyLarge = TextStyle(fontFamily = customFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
                bodyMedium = TextStyle(fontFamily = customFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
                labelMedium = TextStyle(fontFamily = customFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
            )
        } else {
            Typography
        }
    }

    CompositionLocalProvider(
        LocalAppThemePalette provides targetPalette,
        LocalCustomMinimalThemeConfig provides sadeConfig,
        LocalCowboyBebopConfig provides cowboyBebopConfig
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
