package com.example.data

import android.content.Context

enum class SakuraAnimationProfile(
    val displayName: String,
    val description: String,
    val defaultDensity: PetalDensityLevel,
    val defaultSpeed: TransitionSpeedLevel,
    val defaultIntensity: EffectIntensityLevel
) {
    SPRING(
        displayName = "Yaylanma (Spring)",
        description = "Doğal yay fiziğiyle esnek kart geçişleri, dengeli parçacık akışı ve canlı tepkisellik.",
        defaultDensity = PetalDensityLevel.MEDIUM,
        defaultSpeed = TransitionSpeedLevel.NORMAL,
        defaultIntensity = EffectIntensityLevel.BALANCED
    ),
    CYBER(
        displayName = "Siber Hız (Cyber)",
        description = "Ultra keskin ve yüksek hızlı geçişler, dinamik ses tepkisi ve sıfır gecikmeli tepki.",
        defaultDensity = PetalDensityLevel.LOW,
        defaultSpeed = TransitionSpeedLevel.FAST,
        defaultIntensity = EffectIntensityLevel.VIVID
    ),
    SMOOTH(
        displayName = "Yumuşak (Smooth)",
        description = "Sinematik ve ipeksi ekran geçişleri, rüya gibi süzülen efektler ve yumuşak eğriler.",
        defaultDensity = PetalDensityLevel.HIGH,
        defaultSpeed = TransitionSpeedLevel.DREAMY,
        defaultIntensity = EffectIntensityLevel.BALANCED
    ),
    MINIMAL(
        displayName = "Minimal (Sade)",
        description = "Minimum GPU/pil kullanımı, ekstra parçacık olmadan sadece saf ve hızlı arayüz geçişleri.",
        defaultDensity = PetalDensityLevel.OFF,
        defaultSpeed = TransitionSpeedLevel.FAST,
        defaultIntensity = EffectIntensityLevel.SUBTLE
    );

    companion object {
        val MINIMAL_SAKURA = MINIMAL
        val BALANCED_SAKURA = SPRING
        val VIVID_BLOSSOM = SMOOTH

        fun fromName(name: String?): SakuraAnimationProfile {
            return when (name) {
                "MINIMAL_SAKURA" -> MINIMAL
                "BALANCED_SAKURA" -> SPRING
                "VIVID_BLOSSOM" -> SMOOTH
                else -> entries.find { it.name == name } ?: SPRING
            }
        }
    }
}

enum class PetalDensityLevel(
    val displayName: String,
    val ambientPetalCount: Int,
    val playerPetalCount: Int,
    val burstParticleCount: Int
) {
    OFF("Kapalı", 0, 0, 0),
    LOW("Düşük", 10, 8, 8),
    MEDIUM("Orta", 20, 14, 14),
    HIGH("Yüksek", 32, 22, 22);

    companion object {
        fun fromName(name: String?): PetalDensityLevel {
            return entries.find { it.name == name } ?: MEDIUM
        }
    }
}

enum class TransitionSpeedLevel(
    val displayName: String,
    val durationMultiplier: Float,
    val baseDurationMs: Int
) {
    FAST("Hızlı (200ms)", 0.68f, 200),
    NORMAL("Normal (320ms)", 1.0f, 320),
    DREAMY("Yumuşak (460ms)", 1.35f, 460);

    companion object {
        fun fromName(name: String?): TransitionSpeedLevel {
            return entries.find { it.name == name } ?: NORMAL
        }
    }
}

enum class EffectIntensityLevel(
    val displayName: String,
    val glowAlphaMultiplier: Float,
    val scaleAmplitude: Float,
    val windSwayMultiplier: Float
) {
    SUBTLE("Hafif", 0.55f, 0.65f, 0.7f),
    BALANCED("Dengeli", 1.0f, 1.0f, 1.0f),
    VIVID("Yoğun", 1.35f, 1.3f, 1.35f);

    companion object {
        fun fromName(name: String?): EffectIntensityLevel {
            return entries.find { it.name == name } ?: BALANCED
        }
    }
}

data class SakuraAnimationConfig(
    val masterEnabled: Boolean = true,
    val profile: SakuraAnimationProfile = SakuraAnimationProfile.SPRING,
    val petalDensity: PetalDensityLevel = PetalDensityLevel.MEDIUM,
    val transitionSpeed: TransitionSpeedLevel = TransitionSpeedLevel.NORMAL,
    val effectIntensity: EffectIntensityLevel = EffectIntensityLevel.BALANCED,
    val floatingPetalsEnabled: Boolean = true,
    val audioReactiveWavesEnabled: Boolean = true,
    val interactiveBurstEnabled: Boolean = false,
    val screenTransitionsEnabled: Boolean = true,
    val powerSaveAutoReduce: Boolean = true
) {
    fun effectiveAmbientPetalCount(isSystemPowerSave: Boolean): Int {
        if (!masterEnabled || !floatingPetalsEnabled || profile == SakuraAnimationProfile.MINIMAL) return 0
        val base = petalDensity.ambientPetalCount
        return if (powerSaveAutoReduce && isSystemPowerSave) (base / 2).coerceAtLeast(4) else base
    }

    fun effectivePlayerPetalCount(isSystemPowerSave: Boolean): Int {
        if (!masterEnabled || !floatingPetalsEnabled || profile == SakuraAnimationProfile.MINIMAL) return 0
        val base = petalDensity.playerPetalCount
        return if (powerSaveAutoReduce && isSystemPowerSave) (base / 2).coerceAtLeast(4) else base
    }

    fun effectiveBurstCount(isSystemPowerSave: Boolean): Int {
        if (!masterEnabled || !interactiveBurstEnabled || profile == SakuraAnimationProfile.MINIMAL) return 0
        val base = petalDensity.burstParticleCount
        return if (powerSaveAutoReduce && isSystemPowerSave) (base / 2).coerceAtLeast(4) else base
    }
}

class AnimationPreferencesManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadConfig(): SakuraAnimationConfig {
        val profile = SakuraAnimationProfile.fromName(prefs.getString(KEY_PROFILE, null))
        return SakuraAnimationConfig(
            masterEnabled = prefs.getBoolean(KEY_MASTER_ENABLED, true),
            profile = profile,
            petalDensity = PetalDensityLevel.fromName(
                prefs.getString(KEY_PETAL_DENSITY, profile.defaultDensity.name)
            ),
            transitionSpeed = TransitionSpeedLevel.fromName(
                prefs.getString(KEY_TRANSITION_SPEED, profile.defaultSpeed.name)
            ),
            effectIntensity = EffectIntensityLevel.fromName(
                prefs.getString(KEY_EFFECT_INTENSITY, profile.defaultIntensity.name)
            ),
            floatingPetalsEnabled = prefs.getBoolean(KEY_FLOATING_PETALS, true),
            audioReactiveWavesEnabled = prefs.getBoolean(KEY_AUDIO_REACTIVE, true),
            interactiveBurstEnabled = prefs.getBoolean(KEY_INTERACTIVE_BURST, false),
            screenTransitionsEnabled = prefs.getBoolean(KEY_SCREEN_TRANSITIONS, true),
            powerSaveAutoReduce = prefs.getBoolean(KEY_POWER_SAVE_REDUCE, true)
        )
    }

    fun saveConfig(config: SakuraAnimationConfig) {
        prefs.edit()
            .putBoolean(KEY_MASTER_ENABLED, config.masterEnabled)
            .putString(KEY_PROFILE, config.profile.name)
            .putString(KEY_PETAL_DENSITY, config.petalDensity.name)
            .putString(KEY_TRANSITION_SPEED, config.transitionSpeed.name)
            .putString(KEY_EFFECT_INTENSITY, config.effectIntensity.name)
            .putBoolean(KEY_FLOATING_PETALS, config.floatingPetalsEnabled)
            .putBoolean(KEY_AUDIO_REACTIVE, config.audioReactiveWavesEnabled)
            .putBoolean(KEY_INTERACTIVE_BURST, config.interactiveBurstEnabled)
            .putBoolean(KEY_SCREEN_TRANSITIONS, config.screenTransitionsEnabled)
            .putBoolean(KEY_POWER_SAVE_REDUCE, config.powerSaveAutoReduce)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "sakura_animation_prefs"
        private const val KEY_MASTER_ENABLED = "master_enabled"
        private const val KEY_PROFILE = "animation_profile"
        private const val KEY_PETAL_DENSITY = "petal_density"
        private const val KEY_TRANSITION_SPEED = "transition_speed"
        private const val KEY_EFFECT_INTENSITY = "effect_intensity"
        private const val KEY_FLOATING_PETALS = "floating_petals_enabled"
        private const val KEY_AUDIO_REACTIVE = "audio_reactive_waves_enabled"
        private const val KEY_INTERACTIVE_BURST = "interactive_burst_enabled"
        private const val KEY_SCREEN_TRANSITIONS = "screen_transitions_enabled"
        private const val KEY_POWER_SAVE_REDUCE = "power_save_auto_reduce"
    }
}
