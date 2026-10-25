package com.example.data

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NowPlayingSceneMode(
    val id: String,
    val shortTitle: String,
    val displayName: String,
    val description: String
) {
    DEFAULT_CLASSIC(
        id = "default_classic",
        shortTitle = "Sade Kapak",
        displayName = "1. Yok (Sade Kapak - Varsayılan)",
        description = "Kapak etrafında hiçbir ekstra çizim veya efekt olmaz; en sade ve akıcı görünüm"
    ),
    SAKURA_TREE_STAGE(
        id = "sakura_tree_stage",
        shortTitle = "🌸 Sakura Ağacı",
        displayName = "2. Albüm Arkası Canlı Sakura Ağacı",
        description = "Albüm kapağının arkasından uzanan, seçili temayla renk değiştiren rüzgarlı Sakura dalları ve dökülen yapraklar"
    ),
    ZEN_LOTUS_RIPPLES(
        id = "zen_lotus_ripples",
        shortTitle = "🪷 Zen Su & Nilüfer",
        displayName = "3. Zen Su Halkaları & Nilüfer (Suikinkutsu)",
        description = "Kapağın etrafında müziğin ritmiyle yayılan çok katmanlı duru su halkaları ve ışıklı nilüfer çiçekleri"
    ),
    PAPER_LANTERNS_FIREFLIES(
        id = "paper_lanterns_fireflies",
        shortTitle = "🏮 Japon Fenerleri",
        displayName = "4. Japon Kağıt Fenerleri & Ateşböceği (Tōrō)",
        description = "Kapağın yanlarından göğe süzülen sıcak Japon dilek fenerleri (Chōchin) ve ritimle parıldayan ateşböcekleri"
    ),
    KITSUNE_TORII_FIRE(
        id = "kitsune_torii_fire",
        shortTitle = "🦊 Kitsune & Torii",
        displayName = "8. Kitsune Ruh Ateşi & Torii Kapısı (Kitsunebi)",
        description = "Kapak çerçevesinde geleneksel kızıl Torii tapınak kapısı silüeti ve süzülen mavi-altın tilki ateşi küreleri"
    );

    companion object {
        val DEFAULT = DEFAULT_CLASSIC

        fun fromId(id: String?): NowPlayingSceneMode {
            return entries.find { it.id == id } ?: DEFAULT
        }
    }
}

enum class BerserkAnimMode(
    val id: String,
    val shortTitle: String,
    val description: String
) {
    BEHELIT_ECLIPSE(
        id = "behelit_eclipse",
        shortTitle = "🩸 Behelit Kan Ağlama (7sn Uyanış & Masmavi Gözler)",
        description = "Uzun sallanan çift deri ipli Behelit kolyesi 7 saniyede yüzünü toparlayıp masmavi gözlerinden kan ağlar"
    );

    companion object {
        fun fromId(id: String?): BerserkAnimMode = BEHELIT_ECLIPSE
    }
}

enum class SpikeAnimMode(
    val id: String,
    val shortTitle: String,
    val description: String
) {
    SPACE_COWBOY_STARS(
        id = "space_cowboy_stars",
        shortTitle = "✨ Sabit Yıldız & Duman",
        description = "Sabit noktada sönümlenen final yıldızları + Zippo çakmak ve yatay duman"
    );

    companion object {
        fun fromId(id: String?): SpikeAnimMode = SPACE_COWBOY_STARS
    }
}

enum class FayeAnimMode(
    val id: String,
    val shortTitle: String,
    val description: String
) {
    ART_NOUVEAU_VU_METER(
        id = "art_nouveau_vu",
        shortTitle = "🪞 Altın Ayna & VU Metre",
        description = "Art Nouveau floral altın ayna çerçevesi"
    );

    companion object {
        fun fromId(id: String?): FayeAnimMode = ART_NOUVEAU_VU_METER
    }
}

enum class EvaAnimMode(
    val id: String,
    val shortTitle: String,
    val description: String
) {
    MAGI_SYNC_HARMONIC(
        id = "magi_sync_harmonic",
        shortTitle = "🧬 MAGI Senkron (%400)",
        description = "NERV kesik zırh çerçevesi"
    );

    companion object {
        fun fromId(id: String?): EvaAnimMode = MAGI_SYNC_HARMONIC
    }
}

enum class BebopAnimationIntensity(
    val id: String,
    val displayName: String,
    val multiplier: Float
) {
    OFF("off", "Kapalı", 0.0f),
    LOW("low", "Düşük", 0.45f),
    MEDIUM("medium", "Orta", 0.75f),
    HIGH("high", "Yüksek", 1.0f);

    companion object {
        fun fromId(id: String?): BebopAnimationIntensity {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: LOW
        }
    }
}

enum class BebopAnimationSpeed(
    val id: String,
    val displayName: String,
    val speedFactor: Float
) {
    SLOW("slow", "Yavaş", 0.65f),
    NORMAL("normal", "Normal", 1.0f),
    FAST("fast", "Hızlı", 1.35f);

    val speedMultiplier: Float
        get() = speedFactor

    companion object {
        fun fromId(id: String?): BebopAnimationSpeed {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: NORMAL
        }
    }
}

enum class BebopPresetProfile(
    val id: String,
    val displayName: String,
    val description: String
) {
    NIGHT("night", "Gece", "Maksimum göz konforu, koyu arka plan ve minimum hareketli efekt"),
    BALANCED("balanced", "Dengeli", "Faye mor-kehribar dengesi, %50 görsel parlaklık ve ölçülü uzay efektleri"),
    CINEMATIC("cinematic", "Sinematik", "Zengin nebula, kayan yıldızlar, paralaks ve retro HUD atmosferi");

    val subtitle: String
        get() = description
}

/**
 * Cowboy Bebop temasına özel 31+ bağımsız ve kalıcı özelleştirme ayarı.
 * Her özellik birbirinden bağımsızdır ve yalnızca Cowboy Bebop temasını etkiler.
 */
@Immutable
data class CowboyBebopThemeConfig(
    // A. Renk ve Görünüm (1..8)
    val cbFayePurpleYellowPaletteEnabled: Boolean = true,
    val cbNightFriendlyLowBrightnessEnabled: Boolean = true,
    val cbPurpleToneIntensity: Float = 0.60f,
    val cbAmberYellowAccentIntensity: Float = 0.55f,
    val cbBackgroundDarknessLevel: Float = 0.75f,
    val cbCardSurfaceOpacity: Float = 0.82f,
    val cbTextContrastBoostEnabled: Boolean = true,
    val cbNightFriendlyPalette: Boolean = cbNightFriendlyLowBrightnessEnabled,
    val cbVisualBrightness: Int = if (cbNightFriendlyLowBrightnessEnabled) 50 else 75,
    val cbPurpleIntensity: Int = (cbPurpleToneIntensity * 100f).toInt(),
    val cbAmberGoldIntensity: Int = (cbAmberYellowAccentIntensity * 100f).toInt(),
    val cbBackgroundDarkness: Int = (cbBackgroundDarknessLevel * 100f).toInt(),
    val cbGlowEffectsEnabled: Boolean = false,
    val cbDynamicColorTransitions: Boolean = true,
    val cbFayePurpleGoldStyle: Boolean = cbFayePurpleYellowPaletteEnabled,

    // B. Uzay ve Arka Plan Efektleri (9..15)
    val cbStaticStarfieldEnabled: Boolean = true,
    val cbStarDensity: Float = 0.65f,
    val cbStarBrightness: Float = 0.55f,
    val cbSpaceMistNebulaEnabled: Boolean = true,
    val cbNebulaIntensity: Float = 0.50f,
    val cbFilmGrainDustEnabled: Boolean = false,
    val cbVignetteEnabled: Boolean = true,
    val cbStarfieldEnabled: Boolean = cbStaticStarfieldEnabled,
    val cbShootingStarsEnabled: Boolean = false,
    val cbNebulaCloudsEnabled: Boolean = cbSpaceMistNebulaEnabled,
    val cbSpaceDustEnabled: Boolean = cbFilmGrainDustEnabled,
    val cbBackgroundShimmerEnabled: Boolean = false,
    val cbDepthParallaxEnabled: Boolean = false,

    // C. Animasyonlar (16..22)
    val cbMasterAnimationsEnabled: Boolean = true,
    val cbAnimationIntensity: BebopAnimationIntensity = BebopAnimationIntensity.LOW,
    val cbAnimationSpeed: BebopAnimationSpeed = BebopAnimationSpeed.NORMAL,
    val cbStarTwinkleEnabled: Boolean = false,
    val cbSpaceLightTransitionEnabled: Boolean = true,
    val cbScreenTransitionEffectsEnabled: Boolean = true,
    val cbTrackChangeAnimationEnabled: Boolean = true,
    val cbScrollAnimationEnabled: Boolean = false,
    val cbMiniPlayerAnimationsEnabled: Boolean = true,
    val cbArtworkTransitionAnimationsEnabled: Boolean = true,

    // D. Retro-Fütüristik Arayüz ve Oynatıcı (23..30)
    val cbRetroHudLinesEnabled: Boolean = true,
    val cbThinCardBorderEnabled: Boolean = true,
    val cbSubtleCardGlowEnabled: Boolean = false,
    val cbMiniPlayerGlowHighlightEnabled: Boolean = true,
    val cbFullPlayerSpaceAtmosphereEnabled: Boolean = true,
    val cbSeekbarSpecialGlowEnabled: Boolean = true,
    val cbPlayerVisualizerWaveEnabled: Boolean = true,
    val cbCornerAccentMarksEnabled: Boolean = true,
    val cbSpecialListTitleStyleEnabled: Boolean = true,
    val cbAlbumArtGlowFrameEnabled: Boolean = true,
    val cbTechnicalReadoutsEnabled: Boolean = true,
    val cbWaveformVisualizerEnabled: Boolean = cbPlayerVisualizerWaveEnabled,
    val cbBeatReactiveVisualsEnabled: Boolean = false,
    val cbFullPlayerSpecialEffectsEnabled: Boolean = cbFullPlayerSpaceAtmosphereEnabled,
    val cbMiniPlayerThemeEffectsEnabled: Boolean = cbMiniPlayerGlowHighlightEnabled,

    // E. Performans ve Yaşam Döngüsü (31..35)
    val cbLowEffectModeEnabled: Boolean = false,
    val cbReduceOnBatterySaverEnabled: Boolean = true,
    val cbPauseEffectsInBackgroundEnabled: Boolean = true,
    val cbPauseHeavyEffectsOnScrollEnabled: Boolean = true,
    val cbHighRefreshOptimizationEnabled: Boolean = true,
    val cbReduceIdleDecorationsEnabled: Boolean = cbLowEffectModeEnabled
) {
    val cbVisualBrightnessPercent: Int
        get() = cbVisualBrightness.coerceIn(0, 100)

    fun validated(): CowboyBebopThemeConfig {
        return copy(
            cbPurpleToneIntensity = cbPurpleToneIntensity.coerceIn(0f, 1f),
            cbAmberYellowAccentIntensity = cbAmberYellowAccentIntensity.coerceIn(0f, 1f),
            cbBackgroundDarknessLevel = cbBackgroundDarknessLevel.coerceIn(0f, 1f),
            cbCardSurfaceOpacity = cbCardSurfaceOpacity.coerceIn(0.3f, 1f),
            cbStarDensity = cbStarDensity.coerceIn(0f, 1f),
            cbStarBrightness = cbStarBrightness.coerceIn(0f, 1f),
            cbNebulaIntensity = cbNebulaIntensity.coerceIn(0f, 1f),
            cbVisualBrightness = cbVisualBrightness.coerceIn(0, 100),
            cbPurpleIntensity = cbPurpleIntensity.coerceIn(0, 100),
            cbAmberGoldIntensity = cbAmberGoldIntensity.coerceIn(0, 100),
            cbBackgroundDarkness = cbBackgroundDarkness.coerceIn(0, 100)
        )
    }

    companion object {
        val DEFAULT = CowboyBebopThemeConfig()

        fun fromPreset(profile: BebopPresetProfile): CowboyBebopThemeConfig {
            return when (profile) {
                BebopPresetProfile.NIGHT -> DEFAULT.copy(
                    cbFayePurpleYellowPaletteEnabled = true,
                    cbNightFriendlyLowBrightnessEnabled = true,
                    cbPurpleToneIntensity = 0.50f,
                    cbAmberYellowAccentIntensity = 0.42f,
                    cbBackgroundDarknessLevel = 0.90f,
                    cbCardSurfaceOpacity = 0.88f,
                    cbNightFriendlyPalette = true,
                    cbVisualBrightness = 35,
                    cbPurpleIntensity = 50,
                    cbAmberGoldIntensity = 42,
                    cbBackgroundDarkness = 90,
                    cbGlowEffectsEnabled = false,
                    cbSubtleCardGlowEnabled = false,
                    cbStaticStarfieldEnabled = true,
                    cbStarDensity = 0.45f,
                    cbStarBrightness = 0.40f,
                    cbSpaceMistNebulaEnabled = false,
                    cbFilmGrainDustEnabled = false,
                    cbStarTwinkleEnabled = false,
                    cbStarfieldEnabled = true,
                    cbShootingStarsEnabled = false,
                    cbNebulaCloudsEnabled = false,
                    cbSpaceDustEnabled = false,
                    cbBackgroundShimmerEnabled = false,
                    cbDepthParallaxEnabled = false,
                    cbMasterAnimationsEnabled = false,
                    cbAnimationIntensity = BebopAnimationIntensity.OFF,
                    cbAnimationSpeed = BebopAnimationSpeed.SLOW,
                    cbLowEffectModeEnabled = true,
                    cbPauseHeavyEffectsOnScrollEnabled = true
                )
                BebopPresetProfile.BALANCED -> DEFAULT
                BebopPresetProfile.CINEMATIC -> DEFAULT.copy(
                    cbFayePurpleYellowPaletteEnabled = true,
                    cbNightFriendlyLowBrightnessEnabled = true,
                    cbPurpleToneIntensity = 0.78f,
                    cbAmberYellowAccentIntensity = 0.72f,
                    cbBackgroundDarknessLevel = 0.65f,
                    cbCardSurfaceOpacity = 0.80f,
                    cbNightFriendlyPalette = true,
                    cbVisualBrightness = 65,
                    cbPurpleIntensity = 78,
                    cbAmberGoldIntensity = 72,
                    cbBackgroundDarkness = 65,
                    cbGlowEffectsEnabled = true,
                    cbSubtleCardGlowEnabled = true,
                    cbDynamicColorTransitions = true,
                    cbFayePurpleGoldStyle = true,
                    cbStaticStarfieldEnabled = true,
                    cbStarDensity = 0.85f,
                    cbStarBrightness = 0.75f,
                    cbSpaceMistNebulaEnabled = true,
                    cbNebulaIntensity = 0.75f,
                    cbFilmGrainDustEnabled = true,
                    cbVignetteEnabled = true,
                    cbStarfieldEnabled = true,
                    cbShootingStarsEnabled = true,
                    cbNebulaCloudsEnabled = true,
                    cbSpaceDustEnabled = true,
                    cbBackgroundShimmerEnabled = true,
                    cbDepthParallaxEnabled = true,
                    cbMasterAnimationsEnabled = true,
                    cbAnimationIntensity = BebopAnimationIntensity.MEDIUM,
                    cbAnimationSpeed = BebopAnimationSpeed.NORMAL,
                    cbStarTwinkleEnabled = true,
                    cbSpaceLightTransitionEnabled = true,
                    cbScreenTransitionEffectsEnabled = true,
                    cbRetroHudLinesEnabled = true,
                    cbThinCardBorderEnabled = true,
                    cbMiniPlayerGlowHighlightEnabled = true,
                    cbFullPlayerSpaceAtmosphereEnabled = true,
                    cbSeekbarSpecialGlowEnabled = true,
                    cbPlayerVisualizerWaveEnabled = true,
                    cbCornerAccentMarksEnabled = true,
                    cbSpecialListTitleStyleEnabled = true,
                    cbAlbumArtGlowFrameEnabled = true,
                    cbTechnicalReadoutsEnabled = true,
                    cbWaveformVisualizerEnabled = true,
                    cbBeatReactiveVisualsEnabled = true,
                    cbFullPlayerSpecialEffectsEnabled = true,
                    cbMiniPlayerThemeEffectsEnabled = true,
                    cbLowEffectModeEnabled = false
                )
            }
        }
    }
}

object CowboyBebopThemeManager {
    private val _configFlow = MutableStateFlow(CowboyBebopThemeConfig.DEFAULT)
    val configFlow: StateFlow<CowboyBebopThemeConfig> = _configFlow.asStateFlow()

    val currentConfig: CowboyBebopThemeConfig
        get() = _configFlow.value

    fun updateInMemory(config: CowboyBebopThemeConfig) {
        _configFlow.value = config.validated()
    }

    fun loadFromPrefs(context: Context): CowboyBebopThemeConfig {
        return FeatureTogglesStorageHelper(context).loadCowboyBebopConfig()
    }

    fun updateConfig(
        context: Context,
        transform: (CowboyBebopThemeConfig) -> CowboyBebopThemeConfig
    ): CowboyBebopThemeConfig {
        val helper = FeatureTogglesStorageHelper(context)
        val current = helper.loadCowboyBebopConfig()
        val updated = transform(current).validated()
        helper.saveCowboyBebopConfig(updated)
        return updated
    }

    fun applyPreset(context: Context, profile: BebopPresetProfile): CowboyBebopThemeConfig {
        val preset = CowboyBebopThemeConfig.fromPreset(profile)
        FeatureTogglesStorageHelper(context).saveCowboyBebopConfig(preset)
        return preset
    }

    fun resetToDefaults(context: Context): CowboyBebopThemeConfig {
        return FeatureTogglesStorageHelper(context).resetCowboyBebopConfigToDefaults()
    }
}

@Immutable
data class FeatureTogglesConfig(
    val waveformSeekbarEnabled: Boolean = true,
    val doubleTapSeekEnabled: Boolean = true,
    val dynamicCoverPaletteEnabled: Boolean = false,
    val abLoopEnabled: Boolean = false,
    val playerScenesEnabled: Boolean = true,
    val selectedPlayerScene: NowPlayingSceneMode = NowPlayingSceneMode.DEFAULT_CLASSIC,
    val animeCoverDecorationsEnabled: Boolean = true,
    val berserkAnimMode: BerserkAnimMode = BerserkAnimMode.BEHELIT_ECLIPSE,
    val spikeAnimMode: SpikeAnimMode = SpikeAnimMode.SPACE_COWBOY_STARS,
    val fayeAnimMode: FayeAnimMode = FayeAnimMode.ART_NOUVEAU_VU_METER,
    val evaAnimMode: EvaAnimMode = EvaAnimMode.MAGI_SYNC_HARMONIC,
    val equalizerEnabled: Boolean = true,
    val pitchAndReverbEnabled: Boolean = false,
    val spatial8DAudioEnabled: Boolean = false,
    val ambientMixerEnabled: Boolean = false,
    val vocalReducerEnabled: Boolean = false,
    val lyricCardGeneratorEnabled: Boolean = true,
    val tagEditorEnabled: Boolean = true,
    val libraryDoctorEnabled: Boolean = true,
    val smartAutoplayEnabled: Boolean = true,
    val musicStatsEnabled: Boolean = true,
    val animeArtworkBackgroundEnabled: Boolean = true,
    val animeThemeFontsEnabled: Boolean = true,
    // 4 Independent Berserk - Griffith Settings
    val griffithQuotesEnabled: Boolean = true,
    val griffithFalconEmblemEnabled: Boolean = true,
    val griffithFlyingFalconEnabled: Boolean = true,
    val griffithRandomLinesEnabled: Boolean = true,
    // Staggered / Interval Download Delay in seconds (0 = Immediate)
    val downloadIntervalDelaySeconds: Int = 0,
    // Cowboy Bebop 31-Setting Independent Customization Config
    val cowboyBebopConfig: CowboyBebopThemeConfig = CowboyBebopThemeConfig.DEFAULT
)

class FeatureTogglesStorageHelper(context: Context) {
    private val prefs = context.getSharedPreferences("sakura_feature_toggles", Context.MODE_PRIVATE)

    private fun safeBoolean(key: String, default: Boolean): Boolean {
        return try {
            prefs.getBoolean(key, default)
        } catch (_: Throwable) {
            default
        }
    }

    private fun safeInt(key: String, default: Int, min: Int, max: Int): Int {
        return try {
            prefs.getInt(key, default).coerceIn(min, max)
        } catch (_: Throwable) {
            default.coerceIn(min, max)
        }
    }

    private fun safeString(key: String, default: String): String {
        return try {
            prefs.getString(key, default) ?: default
        } catch (_: Throwable) {
            default
        }
    }

    private fun safeFloat(key: String, default: Float, min: Float, max: Float): Float {
        return try {
            prefs.getFloat(key, default).coerceIn(min, max)
        } catch (_: Throwable) {
            default.coerceIn(min, max)
        }
    }

    fun loadCowboyBebopConfig(): CowboyBebopThemeConfig {
        val d = CowboyBebopThemeConfig.DEFAULT
        val fayeEnabled = safeBoolean("cb_faye_purple_yellow_palette", d.cbFayePurpleYellowPaletteEnabled)
        val nightLowBrightness = safeBoolean("cb_night_friendly_low_brightness", d.cbNightFriendlyLowBrightnessEnabled)
        val purpleTone = safeFloat("cb_purple_tone_intensity", d.cbPurpleToneIntensity, 0f, 1f)
        val amberTone = safeFloat("cb_amber_yellow_accent_intensity", d.cbAmberYellowAccentIntensity, 0f, 1f)
        val bgDarknessLevel = safeFloat("cb_background_darkness_level", d.cbBackgroundDarknessLevel, 0f, 1f)
        val cardOpacity = safeFloat("cb_card_surface_opacity", d.cbCardSurfaceOpacity, 0.3f, 1f)
        val textContrast = safeBoolean("cb_text_contrast_boost", d.cbTextContrastBoostEnabled)
        val staticStarfield = safeBoolean("cb_static_starfield_enabled", d.cbStaticStarfieldEnabled)
        val starDensity = safeFloat("cb_star_density", d.cbStarDensity, 0f, 1f)
        val starBrightness = safeFloat("cb_star_brightness", d.cbStarBrightness, 0f, 1f)
        val spaceMist = safeBoolean("cb_space_mist_nebula", d.cbSpaceMistNebulaEnabled)
        val nebulaIntensity = safeFloat("cb_nebula_intensity", d.cbNebulaIntensity, 0f, 1f)
        val filmGrain = safeBoolean("cb_film_grain_dust", d.cbFilmGrainDustEnabled)
        val vignette = safeBoolean("cb_vignette_enabled", d.cbVignetteEnabled)
        val starTwinkle = safeBoolean("cb_star_twinkle_enabled", d.cbStarTwinkleEnabled)
        val spaceLightTransition = safeBoolean("cb_space_light_transition", d.cbSpaceLightTransitionEnabled)
        val screenTransitions = safeBoolean("cb_screen_transition_effects", d.cbScreenTransitionEffectsEnabled)
        val thinBorder = safeBoolean("cb_thin_card_border", d.cbThinCardBorderEnabled)
        val subtleGlow = safeBoolean("cb_subtle_card_glow", d.cbSubtleCardGlowEnabled)
        val miniPlayerGlow = safeBoolean("cb_mini_player_glow_highlight", d.cbMiniPlayerGlowHighlightEnabled)
        val fullPlayerSpace = safeBoolean("cb_full_player_space_atmosphere", d.cbFullPlayerSpaceAtmosphereEnabled)
        val seekbarGlow = safeBoolean("cb_seekbar_special_glow", d.cbSeekbarSpecialGlowEnabled)
        val playerVisualizer = safeBoolean("cb_player_visualizer_wave", d.cbPlayerVisualizerWaveEnabled)
        val cornerMarks = safeBoolean("cb_corner_accent_marks", d.cbCornerAccentMarksEnabled)
        val specialListTitle = safeBoolean("cb_special_list_title_style", d.cbSpecialListTitleStyleEnabled)
        val albumArtGlow = safeBoolean("cb_album_art_glow_frame", d.cbAlbumArtGlowFrameEnabled)

        val loaded = CowboyBebopThemeConfig(
            cbFayePurpleYellowPaletteEnabled = fayeEnabled,
            cbNightFriendlyLowBrightnessEnabled = nightLowBrightness,
            cbPurpleToneIntensity = purpleTone,
            cbAmberYellowAccentIntensity = amberTone,
            cbBackgroundDarknessLevel = bgDarknessLevel,
            cbCardSurfaceOpacity = cardOpacity,
            cbTextContrastBoostEnabled = textContrast,
            cbNightFriendlyPalette = safeBoolean(KEY_CB_NIGHT_FRIENDLY_PALETTE, nightLowBrightness),
            cbVisualBrightness = safeInt(KEY_CB_VISUAL_BRIGHTNESS, if (nightLowBrightness) 50 else 75, 0, 100),
            cbPurpleIntensity = safeInt(KEY_CB_PURPLE_INTENSITY, (purpleTone * 100f).toInt(), 0, 100),
            cbAmberGoldIntensity = safeInt(KEY_CB_AMBER_GOLD_INTENSITY, (amberTone * 100f).toInt(), 0, 100),
            cbBackgroundDarkness = safeInt(KEY_CB_BACKGROUND_DARKNESS, (bgDarknessLevel * 100f).toInt(), 0, 100),
            cbGlowEffectsEnabled = safeBoolean(KEY_CB_GLOW_EFFECTS, subtleGlow),
            cbDynamicColorTransitions = safeBoolean(KEY_CB_DYNAMIC_COLOR_TRANSITIONS, spaceLightTransition),
            cbFayePurpleGoldStyle = safeBoolean(KEY_CB_FAYE_PURPLE_GOLD_STYLE, fayeEnabled),
            cbStaticStarfieldEnabled = staticStarfield,
            cbStarDensity = starDensity,
            cbStarBrightness = starBrightness,
            cbSpaceMistNebulaEnabled = spaceMist,
            cbNebulaIntensity = nebulaIntensity,
            cbFilmGrainDustEnabled = filmGrain,
            cbVignetteEnabled = vignette,
            cbStarfieldEnabled = safeBoolean(KEY_CB_STARFIELD, staticStarfield),
            cbShootingStarsEnabled = safeBoolean(KEY_CB_SHOOTING_STARS, starTwinkle),
            cbNebulaCloudsEnabled = safeBoolean(KEY_CB_NEBULA_CLOUDS, spaceMist),
            cbSpaceDustEnabled = safeBoolean(KEY_CB_SPACE_DUST, filmGrain),
            cbBackgroundShimmerEnabled = safeBoolean(KEY_CB_BACKGROUND_SHIMMER, starTwinkle),
            cbDepthParallaxEnabled = safeBoolean(KEY_CB_DEPTH_PARALLAX, d.cbDepthParallaxEnabled),
            cbMasterAnimationsEnabled = safeBoolean(KEY_CB_MASTER_ANIMATIONS, d.cbMasterAnimationsEnabled),
            cbAnimationIntensity = BebopAnimationIntensity.fromId(
                safeString(KEY_CB_ANIMATION_INTENSITY, d.cbAnimationIntensity.id)
            ),
            cbAnimationSpeed = BebopAnimationSpeed.fromId(
                safeString(KEY_CB_ANIMATION_SPEED, d.cbAnimationSpeed.id)
            ),
            cbStarTwinkleEnabled = starTwinkle,
            cbSpaceLightTransitionEnabled = spaceLightTransition,
            cbScreenTransitionEffectsEnabled = screenTransitions,
            cbTrackChangeAnimationEnabled = safeBoolean(KEY_CB_TRACK_CHANGE_ANIM, d.cbTrackChangeAnimationEnabled),
            cbScrollAnimationEnabled = safeBoolean(KEY_CB_SCROLL_ANIM, d.cbScrollAnimationEnabled),
            cbMiniPlayerAnimationsEnabled = safeBoolean(KEY_CB_MINI_PLAYER_ANIM, d.cbMiniPlayerAnimationsEnabled),
            cbArtworkTransitionAnimationsEnabled = safeBoolean(KEY_CB_ARTWORK_TRANSITION_ANIM, d.cbArtworkTransitionAnimationsEnabled),
            cbRetroHudLinesEnabled = safeBoolean(KEY_CB_RETRO_HUD_LINES, d.cbRetroHudLinesEnabled),
            cbThinCardBorderEnabled = thinBorder,
            cbSubtleCardGlowEnabled = subtleGlow,
            cbMiniPlayerGlowHighlightEnabled = miniPlayerGlow,
            cbFullPlayerSpaceAtmosphereEnabled = fullPlayerSpace,
            cbSeekbarSpecialGlowEnabled = seekbarGlow,
            cbPlayerVisualizerWaveEnabled = playerVisualizer,
            cbCornerAccentMarksEnabled = cornerMarks,
            cbSpecialListTitleStyleEnabled = specialListTitle,
            cbAlbumArtGlowFrameEnabled = albumArtGlow,
            cbTechnicalReadoutsEnabled = safeBoolean(KEY_CB_TECHNICAL_READOUTS, cornerMarks),
            cbWaveformVisualizerEnabled = safeBoolean(KEY_CB_WAVEFORM_VISUALIZER, playerVisualizer),
            cbBeatReactiveVisualsEnabled = safeBoolean(KEY_CB_BEAT_REACTIVE, d.cbBeatReactiveVisualsEnabled),
            cbFullPlayerSpecialEffectsEnabled = safeBoolean(KEY_CB_FULL_PLAYER_EFFECTS, fullPlayerSpace),
            cbMiniPlayerThemeEffectsEnabled = safeBoolean(KEY_CB_MINI_PLAYER_THEME_EFFECTS, miniPlayerGlow),
            cbLowEffectModeEnabled = safeBoolean(KEY_CB_LOW_EFFECT_MODE, d.cbLowEffectModeEnabled),
            cbReduceOnBatterySaverEnabled = safeBoolean(KEY_CB_REDUCE_BATTERY_SAVER, d.cbReduceOnBatterySaverEnabled),
            cbPauseEffectsInBackgroundEnabled = safeBoolean(KEY_CB_PAUSE_IN_BACKGROUND, d.cbPauseEffectsInBackgroundEnabled),
            cbPauseHeavyEffectsOnScrollEnabled = safeBoolean(KEY_CB_PAUSE_ON_SCROLL, d.cbPauseHeavyEffectsOnScrollEnabled),
            cbHighRefreshOptimizationEnabled = safeBoolean("cb_high_refresh_opt", d.cbHighRefreshOptimizationEnabled),
            cbReduceIdleDecorationsEnabled = safeBoolean("cb_reduce_idle_decorations", d.cbReduceIdleDecorationsEnabled)
        ).validated()
        CowboyBebopThemeManager.updateInMemory(loaded)
        return loaded
    }

    fun saveCowboyBebopConfig(config: CowboyBebopThemeConfig) {
        val c = config.validated()
        prefs.edit()
            .putBoolean("cb_faye_purple_yellow_palette", c.cbFayePurpleYellowPaletteEnabled)
            .putBoolean("cb_night_friendly_low_brightness", c.cbNightFriendlyLowBrightnessEnabled)
            .putFloat("cb_purple_tone_intensity", c.cbPurpleToneIntensity)
            .putFloat("cb_amber_yellow_accent_intensity", c.cbAmberYellowAccentIntensity)
            .putFloat("cb_background_darkness_level", c.cbBackgroundDarknessLevel)
            .putFloat("cb_card_surface_opacity", c.cbCardSurfaceOpacity)
            .putBoolean("cb_text_contrast_boost", c.cbTextContrastBoostEnabled)
            .putBoolean("cb_static_starfield_enabled", c.cbStaticStarfieldEnabled)
            .putFloat("cb_star_density", c.cbStarDensity)
            .putFloat("cb_star_brightness", c.cbStarBrightness)
            .putBoolean("cb_space_mist_nebula", c.cbSpaceMistNebulaEnabled)
            .putFloat("cb_nebula_intensity", c.cbNebulaIntensity)
            .putBoolean("cb_film_grain_dust", c.cbFilmGrainDustEnabled)
            .putBoolean("cb_vignette_enabled", c.cbVignetteEnabled)
            .putBoolean("cb_star_twinkle_enabled", c.cbStarTwinkleEnabled)
            .putBoolean("cb_space_light_transition", c.cbSpaceLightTransitionEnabled)
            .putBoolean("cb_screen_transition_effects", c.cbScreenTransitionEffectsEnabled)
            .putBoolean("cb_thin_card_border", c.cbThinCardBorderEnabled)
            .putBoolean("cb_subtle_card_glow", c.cbSubtleCardGlowEnabled)
            .putBoolean("cb_mini_player_glow_highlight", c.cbMiniPlayerGlowHighlightEnabled)
            .putBoolean("cb_full_player_space_atmosphere", c.cbFullPlayerSpaceAtmosphereEnabled)
            .putBoolean("cb_seekbar_special_glow", c.cbSeekbarSpecialGlowEnabled)
            .putBoolean("cb_player_visualizer_wave", c.cbPlayerVisualizerWaveEnabled)
            .putBoolean("cb_corner_accent_marks", c.cbCornerAccentMarksEnabled)
            .putBoolean("cb_special_list_title_style", c.cbSpecialListTitleStyleEnabled)
            .putBoolean("cb_album_art_glow_frame", c.cbAlbumArtGlowFrameEnabled)
            .putBoolean("cb_high_refresh_opt", c.cbHighRefreshOptimizationEnabled)
            .putBoolean("cb_reduce_idle_decorations", c.cbReduceIdleDecorationsEnabled)
            .putBoolean(KEY_CB_NIGHT_FRIENDLY_PALETTE, c.cbNightFriendlyPalette)
            .putInt(KEY_CB_VISUAL_BRIGHTNESS, c.cbVisualBrightness)
            .putInt(KEY_CB_PURPLE_INTENSITY, c.cbPurpleIntensity)
            .putInt(KEY_CB_AMBER_GOLD_INTENSITY, c.cbAmberGoldIntensity)
            .putInt(KEY_CB_BACKGROUND_DARKNESS, c.cbBackgroundDarkness)
            .putBoolean(KEY_CB_GLOW_EFFECTS, c.cbGlowEffectsEnabled)
            .putBoolean(KEY_CB_DYNAMIC_COLOR_TRANSITIONS, c.cbDynamicColorTransitions)
            .putBoolean(KEY_CB_FAYE_PURPLE_GOLD_STYLE, c.cbFayePurpleGoldStyle)
            .putBoolean(KEY_CB_STARFIELD, c.cbStarfieldEnabled)
            .putBoolean(KEY_CB_SHOOTING_STARS, c.cbShootingStarsEnabled)
            .putBoolean(KEY_CB_NEBULA_CLOUDS, c.cbNebulaCloudsEnabled)
            .putBoolean(KEY_CB_SPACE_DUST, c.cbSpaceDustEnabled)
            .putBoolean(KEY_CB_BACKGROUND_SHIMMER, c.cbBackgroundShimmerEnabled)
            .putBoolean(KEY_CB_DEPTH_PARALLAX, c.cbDepthParallaxEnabled)
            .putBoolean(KEY_CB_MASTER_ANIMATIONS, c.cbMasterAnimationsEnabled)
            .putString(KEY_CB_ANIMATION_INTENSITY, c.cbAnimationIntensity.id)
            .putString(KEY_CB_ANIMATION_SPEED, c.cbAnimationSpeed.id)
            .putBoolean(KEY_CB_TRACK_CHANGE_ANIM, c.cbTrackChangeAnimationEnabled)
            .putBoolean(KEY_CB_SCROLL_ANIM, c.cbScrollAnimationEnabled)
            .putBoolean(KEY_CB_MINI_PLAYER_ANIM, c.cbMiniPlayerAnimationsEnabled)
            .putBoolean(KEY_CB_ARTWORK_TRANSITION_ANIM, c.cbArtworkTransitionAnimationsEnabled)
            .putBoolean(KEY_CB_RETRO_HUD_LINES, c.cbRetroHudLinesEnabled)
            .putBoolean(KEY_CB_TECHNICAL_READOUTS, c.cbTechnicalReadoutsEnabled)
            .putBoolean(KEY_CB_WAVEFORM_VISUALIZER, c.cbWaveformVisualizerEnabled)
            .putBoolean(KEY_CB_BEAT_REACTIVE, c.cbBeatReactiveVisualsEnabled)
            .putBoolean(KEY_CB_FULL_PLAYER_EFFECTS, c.cbFullPlayerSpecialEffectsEnabled)
            .putBoolean(KEY_CB_MINI_PLAYER_THEME_EFFECTS, c.cbMiniPlayerThemeEffectsEnabled)
            .putBoolean(KEY_CB_LOW_EFFECT_MODE, c.cbLowEffectModeEnabled)
            .putBoolean(KEY_CB_REDUCE_BATTERY_SAVER, c.cbReduceOnBatterySaverEnabled)
            .putBoolean(KEY_CB_PAUSE_IN_BACKGROUND, c.cbPauseEffectsInBackgroundEnabled)
            .putBoolean(KEY_CB_PAUSE_ON_SCROLL, c.cbPauseHeavyEffectsOnScrollEnabled)
            .apply()
        CowboyBebopThemeManager.updateInMemory(c)
    }

    /**
     * Yalnızca Cowboy Bebop'un 31 ayarını varsayılanlara döndürür;
     * Griffith, Sakura veya diğer uygulama tercihlerine kesinlikle dokunmaz.
     */
    fun resetCowboyBebopConfigToDefaults(): CowboyBebopThemeConfig {
        val defaults = CowboyBebopThemeConfig.DEFAULT
        saveCowboyBebopConfig(defaults)
        return defaults
    }

    fun loadConfig(): FeatureTogglesConfig {
        val bebopConfig = loadCowboyBebopConfig()
        return FeatureTogglesConfig(
            waveformSeekbarEnabled = safeBoolean(KEY_WAVEFORM_SEEKBAR, true),
            doubleTapSeekEnabled = safeBoolean(KEY_DOUBLE_TAP_SEEK, true),
            dynamicCoverPaletteEnabled = safeBoolean(KEY_DYNAMIC_COVER_PALETTE, false),
            abLoopEnabled = safeBoolean(KEY_AB_LOOP, false),
            playerScenesEnabled = safeBoolean(KEY_PLAYER_SCENES_ENABLED, true),
            selectedPlayerScene = NowPlayingSceneMode.fromId(
                safeString(KEY_SELECTED_PLAYER_SCENE, NowPlayingSceneMode.DEFAULT_CLASSIC.id)
            ),
            animeCoverDecorationsEnabled = safeBoolean(KEY_ANIME_COVER_DECORATIONS, true),
            berserkAnimMode = BerserkAnimMode.fromId(
                safeString(KEY_BERSERK_ANIM_MODE, BerserkAnimMode.BEHELIT_ECLIPSE.id)
            ),
            spikeAnimMode = SpikeAnimMode.fromId(
                safeString(KEY_SPIKE_ANIM_MODE, SpikeAnimMode.SPACE_COWBOY_STARS.id)
            ),
            fayeAnimMode = FayeAnimMode.fromId(
                safeString(KEY_FAYE_ANIM_MODE, FayeAnimMode.ART_NOUVEAU_VU_METER.id)
            ),
            evaAnimMode = EvaAnimMode.fromId(
                safeString(KEY_EVA_ANIM_MODE, EvaAnimMode.MAGI_SYNC_HARMONIC.id)
            ),
            equalizerEnabled = safeBoolean(KEY_EQUALIZER, true),
            pitchAndReverbEnabled = safeBoolean(KEY_PITCH_REVERB, false),
            spatial8DAudioEnabled = safeBoolean(KEY_SPATIAL_8D, false),
            ambientMixerEnabled = safeBoolean(KEY_AMBIENT_MIXER, false),
            vocalReducerEnabled = safeBoolean(KEY_VOCAL_REDUCER, false),
            lyricCardGeneratorEnabled = safeBoolean(KEY_LYRIC_CARD, true),
            tagEditorEnabled = safeBoolean(KEY_TAG_EDITOR, true),
            libraryDoctorEnabled = safeBoolean(KEY_LIBRARY_DOCTOR, true),
            smartAutoplayEnabled = safeBoolean(KEY_SMART_AUTOPLAY, true),
            musicStatsEnabled = safeBoolean(KEY_MUSIC_STATS, true),
            animeArtworkBackgroundEnabled = safeBoolean(KEY_ANIME_ARTWORK_BG, true),
            animeThemeFontsEnabled = safeBoolean(KEY_ANIME_THEME_FONTS, true),
            griffithQuotesEnabled = safeBoolean(KEY_GRIFFITH_QUOTES, true),
            griffithFalconEmblemEnabled = safeBoolean(KEY_GRIFFITH_FALCON_EMBLEM, true),
            griffithFlyingFalconEnabled = safeBoolean(KEY_GRIFFITH_FLYING_FALCON, true),
            griffithRandomLinesEnabled = safeBoolean(KEY_GRIFFITH_RANDOM_LINES, true),
            downloadIntervalDelaySeconds = safeInt(KEY_DOWNLOAD_INTERVAL_SECONDS, 0, 0, 120),
            cowboyBebopConfig = bebopConfig
        )
    }

    fun saveConfig(config: FeatureTogglesConfig) {
        val safeDelay = config.downloadIntervalDelaySeconds.coerceIn(0, 120)
        prefs.edit()
            .putBoolean(KEY_WAVEFORM_SEEKBAR, config.waveformSeekbarEnabled)
            .putBoolean(KEY_DOUBLE_TAP_SEEK, config.doubleTapSeekEnabled)
            .putBoolean(KEY_DYNAMIC_COVER_PALETTE, config.dynamicCoverPaletteEnabled)
            .putBoolean(KEY_AB_LOOP, config.abLoopEnabled)
            .putBoolean(KEY_PLAYER_SCENES_ENABLED, config.playerScenesEnabled)
            .putString(KEY_SELECTED_PLAYER_SCENE, config.selectedPlayerScene.id)
            .putBoolean(KEY_ANIME_COVER_DECORATIONS, config.animeCoverDecorationsEnabled)
            .putString(KEY_BERSERK_ANIM_MODE, config.berserkAnimMode.id)
            .putString(KEY_SPIKE_ANIM_MODE, config.spikeAnimMode.id)
            .putString(KEY_FAYE_ANIM_MODE, config.fayeAnimMode.id)
            .putString(KEY_EVA_ANIM_MODE, config.evaAnimMode.id)
            .putBoolean(KEY_EQUALIZER, config.equalizerEnabled)
            .putBoolean(KEY_PITCH_REVERB, config.pitchAndReverbEnabled)
            .putBoolean(KEY_SPATIAL_8D, config.spatial8DAudioEnabled)
            .putBoolean(KEY_AMBIENT_MIXER, config.ambientMixerEnabled)
            .putBoolean(KEY_VOCAL_REDUCER, config.vocalReducerEnabled)
            .putBoolean(KEY_LYRIC_CARD, config.lyricCardGeneratorEnabled)
            .putBoolean(KEY_TAG_EDITOR, config.tagEditorEnabled)
            .putBoolean(KEY_LIBRARY_DOCTOR, config.libraryDoctorEnabled)
            .putBoolean(KEY_SMART_AUTOPLAY, config.smartAutoplayEnabled)
            .putBoolean(KEY_MUSIC_STATS, config.musicStatsEnabled)
            .putBoolean(KEY_ANIME_ARTWORK_BG, config.animeArtworkBackgroundEnabled)
            .putBoolean(KEY_ANIME_THEME_FONTS, config.animeThemeFontsEnabled)
            .putBoolean(KEY_GRIFFITH_QUOTES, config.griffithQuotesEnabled)
            .putBoolean(KEY_GRIFFITH_FALCON_EMBLEM, config.griffithFalconEmblemEnabled)
            .putBoolean(KEY_GRIFFITH_FLYING_FALCON, config.griffithFlyingFalconEnabled)
            .putBoolean(KEY_GRIFFITH_RANDOM_LINES, config.griffithRandomLinesEnabled)
            .putInt(KEY_DOWNLOAD_INTERVAL_SECONDS, safeDelay)
            .apply()
        saveCowboyBebopConfig(config.cowboyBebopConfig)
    }

    companion object {
        private const val KEY_WAVEFORM_SEEKBAR = "waveform_seekbar_enabled"
        private const val KEY_DOUBLE_TAP_SEEK = "double_tap_seek_enabled"
        private const val KEY_DYNAMIC_COVER_PALETTE = "dynamic_cover_palette_v27_enabled"
        private const val KEY_AB_LOOP = "ab_loop_v27_enabled"
        private const val KEY_PLAYER_SCENES_ENABLED = "player_scenes_v27_enabled"
        private const val KEY_SELECTED_PLAYER_SCENE = "selected_player_scene_v27"
        private const val KEY_ANIME_COVER_DECORATIONS = "anime_cover_decorations_enabled_v29"
        private const val KEY_BERSERK_ANIM_MODE = "berserk_anim_mode_v28"
        private const val KEY_SPIKE_ANIM_MODE = "spike_anim_mode_v28"
        private const val KEY_FAYE_ANIM_MODE = "faye_anim_mode_v28"
        private const val KEY_EVA_ANIM_MODE = "eva_anim_mode_v28"
        private const val KEY_EQUALIZER = "equalizer_enabled"
        private const val KEY_PITCH_REVERB = "pitch_reverb_enabled"
        private const val KEY_SPATIAL_8D = "spatial_8d_enabled"
        private const val KEY_AMBIENT_MIXER = "ambient_mixer_enabled"
        private const val KEY_VOCAL_REDUCER = "vocal_reducer_enabled"
        private const val KEY_LYRIC_CARD = "lyric_card_enabled"
        private const val KEY_TAG_EDITOR = "tag_editor_enabled"
        private const val KEY_LIBRARY_DOCTOR = "library_doctor_enabled"
        private const val KEY_SMART_AUTOPLAY = "smart_autoplay_enabled"
        private const val KEY_MUSIC_STATS = "music_stats_enabled"
        private const val KEY_ANIME_ARTWORK_BG = "anime_artwork_bg_enabled"
        private const val KEY_ANIME_THEME_FONTS = "anime_theme_fonts_enabled"

        // 4 Independent Berserk - Griffith Keys
        const val KEY_GRIFFITH_QUOTES = "griffith_quotes_enabled_v1"
        const val KEY_GRIFFITH_FALCON_EMBLEM = "griffith_falcon_emblem_enabled_v1"
        const val KEY_GRIFFITH_FLYING_FALCON = "griffith_flying_falcon_enabled_v1"
        const val KEY_GRIFFITH_RANDOM_LINES = "griffith_random_lines_enabled_v1"

        // Staggered Download Interval Key
        const val KEY_DOWNLOAD_INTERVAL_SECONDS = "download_interval_delay_seconds_v1"

        // 31 Independent Cowboy Bebop Customization Keys
        const val KEY_CB_NIGHT_FRIENDLY_PALETTE = "cb_night_friendly_palette"
        const val KEY_CB_VISUAL_BRIGHTNESS = "cb_visual_brightness"
        const val KEY_CB_PURPLE_INTENSITY = "cb_purple_intensity"
        const val KEY_CB_AMBER_GOLD_INTENSITY = "cb_amber_gold_intensity"
        const val KEY_CB_BACKGROUND_DARKNESS = "cb_background_darkness"
        const val KEY_CB_GLOW_EFFECTS = "cb_glow_effects_enabled"
        const val KEY_CB_DYNAMIC_COLOR_TRANSITIONS = "cb_dynamic_color_transitions"
        const val KEY_CB_FAYE_PURPLE_GOLD_STYLE = "cb_faye_purple_gold_style"

        const val KEY_CB_STARFIELD = "cb_starfield_enabled"
        const val KEY_CB_SHOOTING_STARS = "cb_shooting_stars_enabled"
        const val KEY_CB_NEBULA_CLOUDS = "cb_nebula_clouds_enabled"
        const val KEY_CB_SPACE_DUST = "cb_space_dust_enabled"
        const val KEY_CB_BACKGROUND_SHIMMER = "cb_background_shimmer_enabled"
        const val KEY_CB_DEPTH_PARALLAX = "cb_depth_parallax_enabled"

        const val KEY_CB_MASTER_ANIMATIONS = "cb_master_animations_enabled"
        const val KEY_CB_ANIMATION_INTENSITY = "cb_animation_intensity"
        const val KEY_CB_ANIMATION_SPEED = "cb_animation_speed"
        const val KEY_CB_TRACK_CHANGE_ANIM = "cb_track_change_animation_enabled"
        const val KEY_CB_SCROLL_ANIM = "cb_scroll_animation_enabled"
        const val KEY_CB_MINI_PLAYER_ANIM = "cb_mini_player_animations_enabled"
        const val KEY_CB_ARTWORK_TRANSITION_ANIM = "cb_artwork_transition_animations_enabled"

        const val KEY_CB_RETRO_HUD_LINES = "cb_retro_hud_lines_enabled"
        const val KEY_CB_TECHNICAL_READOUTS = "cb_technical_readouts_enabled"
        const val KEY_CB_WAVEFORM_VISUALIZER = "cb_waveform_visualizer_enabled"
        const val KEY_CB_BEAT_REACTIVE = "cb_beat_reactive_visuals_enabled"
        const val KEY_CB_FULL_PLAYER_EFFECTS = "cb_full_player_special_effects_enabled"
        const val KEY_CB_MINI_PLAYER_THEME_EFFECTS = "cb_mini_player_theme_effects_enabled"

        const val KEY_CB_LOW_EFFECT_MODE = "cb_low_effect_mode_enabled"
        const val KEY_CB_REDUCE_BATTERY_SAVER = "cb_reduce_on_battery_saver_enabled"
        const val KEY_CB_PAUSE_IN_BACKGROUND = "cb_pause_effects_in_background_enabled"
        const val KEY_CB_PAUSE_ON_SCROLL = "cb_pause_heavy_effects_on_scroll_enabled"
    }
}
