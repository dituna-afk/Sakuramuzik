package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log
import com.example.data.FeatureTogglesConfig
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class StudioEqPreset(
    val name: String,
    val bandsDb: List<Float>, // 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz (-12..+12 dB)
    val bassStrength: Int
)

val DEFAULT_EQ_PRESETS = listOf(
    StudioEqPreset("Dengeli (Flat)", listOf(0f, 0f, 0f, 0f, 0f), 0),
    StudioEqPreset("Sakura Bass+", listOf(7f, 5f, 0f, 2f, 4f), 650),
    StudioEqPreset("Rock & Metal", listOf(5f, 3f, -2f, 4f, 6f), 450),
    StudioEqPreset("Pop & Dans", listOf(4f, 2f, 1f, 3f, 5f), 500),
    StudioEqPreset("Net Vokal", listOf(-2f, 1f, 5f, 4f, 2f), 150),
    StudioEqPreset("Lo-Fi Gece", listOf(4f, 3f, -1f, -4f, -6f), 400)
)

data class StudioDspConfig(
    // 1. 5-Band Equalizer & Bass Boost
    val isEqualizerActive: Boolean = false,
    val eqPresetName: String = "Sakura Bass+",
    val band0Db: Float = 7f, // 60 Hz
    val band1Db: Float = 5f, // 230 Hz
    val band2Db: Float = 0f, // 910 Hz
    val band3Db: Float = 2f, // 3.6 kHz
    val band4Db: Float = 4f, // 14 kHz
    val bassBoostStrength: Int = 650, // 0..1000

    // 2. Slowed + Reverb & Nightcore (Pitch & Tempo)
    val isPitchReverbActive: Boolean = false,
    val pitchReverbPreset: String = "Slowed + Reverb",
    val tempoSpeed: Float = 0.84f, // 0.5f..2.0f
    val pitchFactor: Float = 0.86f, // 0.5f..2.0f
    val reverbLevel: Int = 3, // 0=Kapalı, 1=Küçük Oda, 2=Orta Salon, 3=Geniş Konser Salonu, 4=Stüdyo Plate

    // 3. 8D / 360° Spatial Audio Panning
    val isSpatial8DActive: Boolean = false,
    val spatialRotationSeconds: Float = 8.0f, // 3f..16f per full 360 rotation
    val spatialDepthStrength: Int = 750, // 0..1000

    // 4. Karaoke / Vocal Reducer Mode
    val isVocalReducerActive: Boolean = false,
    val vocalReductionPercent: Int = 75, // 20..100%

    // 5. Background Ambient Sound & Vinyl Crackle Mixer
    val isAmbientMixerActive: Boolean = false,
    val rainEnabled: Boolean = true,
    val rainVolume: Float = 0.35f,
    val vinylCrackleEnabled: Boolean = true,
    val vinylCrackleVolume: Float = 0.30f,
    val nightWindEnabled: Boolean = false,
    val nightWindVolume: Float = 0.25f,
    val campfireEnabled: Boolean = false,
    val campfireVolume: Float = 0.25f
) {
    fun bandList(): List<Float> = listOf(band0Db, band1Db, band2Db, band3Db, band4Db)
}

class StudioDspStorageHelper(context: Context) {
    private val prefs = context.getSharedPreferences("sakura_studio_dsp_prefs", Context.MODE_PRIVATE)

    fun loadConfig(): StudioDspConfig {
        return StudioDspConfig(
            isEqualizerActive = prefs.getBoolean("is_eq_active", false),
            eqPresetName = prefs.getString("eq_preset_name", "Sakura Bass+") ?: "Sakura Bass+",
            band0Db = prefs.getFloat("band_0_db", 7f),
            band1Db = prefs.getFloat("band_1_db", 5f),
            band2Db = prefs.getFloat("band_2_db", 0f),
            band3Db = prefs.getFloat("band_3_db", 2f),
            band4Db = prefs.getFloat("band_4_db", 4f),
            bassBoostStrength = prefs.getInt("bass_boost_strength", 650),
            isPitchReverbActive = prefs.getBoolean("is_pitch_reverb_active", false),
            pitchReverbPreset = prefs.getString("pitch_reverb_preset", "Slowed + Reverb") ?: "Slowed + Reverb",
            tempoSpeed = prefs.getFloat("tempo_speed", 0.84f),
            pitchFactor = prefs.getFloat("pitch_factor", 0.86f),
            reverbLevel = prefs.getInt("reverb_level", 3),
            isSpatial8DActive = prefs.getBoolean("is_spatial_8d_active", false),
            spatialRotationSeconds = prefs.getFloat("spatial_rotation_sec", 8.0f),
            spatialDepthStrength = prefs.getInt("spatial_depth_strength", 750),
            isVocalReducerActive = prefs.getBoolean("is_vocal_reducer_active", false),
            vocalReductionPercent = prefs.getInt("vocal_reduction_pct", 75),
            isAmbientMixerActive = prefs.getBoolean("is_ambient_mixer_active", false),
            rainEnabled = prefs.getBoolean("ambient_rain_enabled", true),
            rainVolume = prefs.getFloat("ambient_rain_vol", 0.35f),
            vinylCrackleEnabled = prefs.getBoolean("ambient_vinyl_enabled", true),
            vinylCrackleVolume = prefs.getFloat("ambient_vinyl_vol", 0.30f),
            nightWindEnabled = prefs.getBoolean("ambient_wind_enabled", false),
            nightWindVolume = prefs.getFloat("ambient_wind_vol", 0.25f),
            campfireEnabled = prefs.getBoolean("ambient_fire_enabled", false),
            campfireVolume = prefs.getFloat("ambient_fire_vol", 0.25f)
        )
    }

    fun saveConfig(config: StudioDspConfig) {
        prefs.edit()
            .putBoolean("is_eq_active", config.isEqualizerActive)
            .putString("eq_preset_name", config.eqPresetName)
            .putFloat("band_0_db", config.band0Db)
            .putFloat("band_1_db", config.band1Db)
            .putFloat("band_2_db", config.band2Db)
            .putFloat("band_3_db", config.band3Db)
            .putFloat("band_4_db", config.band4Db)
            .putInt("bass_boost_strength", config.bassBoostStrength)
            .putBoolean("is_pitch_reverb_active", config.isPitchReverbActive)
            .putString("pitch_reverb_preset", config.pitchReverbPreset)
            .putFloat("tempo_speed", config.tempoSpeed)
            .putFloat("pitch_factor", config.pitchFactor)
            .putInt("reverb_level", config.reverbLevel)
            .putBoolean("is_spatial_8d_active", config.isSpatial8DActive)
            .putFloat("spatial_rotation_sec", config.spatialRotationSeconds)
            .putInt("spatial_depth_strength", config.spatialDepthStrength)
            .putBoolean("is_vocal_reducer_active", config.isVocalReducerActive)
            .putInt("vocal_reduction_pct", config.vocalReductionPercent)
            .putBoolean("is_ambient_mixer_active", config.isAmbientMixerActive)
            .putBoolean("ambient_rain_enabled", config.rainEnabled)
            .putFloat("ambient_rain_vol", config.rainVolume)
            .putBoolean("ambient_vinyl_enabled", config.vinylCrackleEnabled)
            .putFloat("ambient_vinyl_vol", config.vinylCrackleVolume)
            .putBoolean("ambient_wind_enabled", config.nightWindEnabled)
            .putFloat("ambient_wind_vol", config.nightWindVolume)
            .putBoolean("ambient_fire_enabled", config.campfireEnabled)
            .putFloat("ambient_fire_vol", config.campfireVolume)
            .apply()
    }
}

object StudioDspController {
    private const val TAG = "StudioDspController"

    // Dedicated serialized single thread for hardware AudioEffects to prevent concurrent native HAL conflicts
    private val dspControlDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SakuraDspControlThread").apply { priority = Thread.NORM_PRIORITY + 1 }
    }.asCoroutineDispatcher()

    private val dspControlScope = CoroutineScope(SupervisorJob() + dspControlDispatcher)
    private val dspWorkerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var currentConfig = StudioDspConfig()

    @Volatile
    private var currentToggles = FeatureTogglesConfig()

    @Volatile
    private var volumeBoostPercent: Int = 160

    @Volatile
    private var isVolumeBoosterEnabled: Boolean = false

    @Volatile
    private var basePlaybackSpeed: Float = 1.0f

    private var activeSessionId: Int = 0
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null

    private var spatialPanningJob: Job? = null
    private var ambientSynthJob: Job? = null
    private var ambientAudioTrack: AudioTrack? = null

    @Volatile
    private var activeMediaPlayerRef: MediaPlayer? = null

    @Volatile
    private var wasPitchReverbApplied: Boolean = false

    fun bindMediaPlayer(player: MediaPlayer?, sessionId: Int) {
        dspControlScope.launch {
            val isDifferentPlayer = activeMediaPlayerRef !== player
            activeMediaPlayerRef = player
            if (isDifferentPlayer || (sessionId > 0 && sessionId != activeSessionId)) {
                releaseHardwareEffectsInternal()
                activeSessionId = sessionId
                wasPitchReverbApplied = false
            }
            applyAllEffectsInternal()
        }
    }

    fun unbindMediaPlayer(player: MediaPlayer?) {
        dspControlScope.launch {
            if (player == null || activeMediaPlayerRef === player) {
                activeMediaPlayerRef = null
                releaseHardwareEffectsInternal()
                wasPitchReverbApplied = false
            }
        }
    }

    fun setBasePlaybackSpeed(speed: Float) {
        basePlaybackSpeed = speed.coerceIn(0.5f, 2.0f)
        dspControlScope.launch {
            applyAllEffectsInternal()
        }
    }

    fun updateVolumeBoostState(percent: Int, enabled: Boolean) {
        val safePct = if (enabled && percent <= 100) 160 else percent.coerceIn(100, 300)
        volumeBoostPercent = safePct
        isVolumeBoosterEnabled = enabled
        dspControlScope.launch {
            applyAllEffectsInternal()
        }
    }

    fun updateState(config: StudioDspConfig, toggles: FeatureTogglesConfig) {
        currentConfig = config
        currentToggles = toggles
        dspControlScope.launch {
            applyAllEffectsInternal()
        }
    }

    private fun ensureEqualizer(sessionId: Int): Equalizer? {
        if (sessionId <= 0) return null
        if (equalizer == null) {
            try {
                equalizer = Equalizer(0, sessionId)
            } catch (e: Throwable) {
                Log.w(TAG, "Equalizer init failed: ${e.message}")
            }
        }
        return equalizer
    }

    private fun ensureBassBoost(sessionId: Int): BassBoost? {
        if (sessionId <= 0) return null
        if (bassBoost == null) {
            try {
                bassBoost = BassBoost(0, sessionId)
            } catch (e: Throwable) {
                Log.w(TAG, "BassBoost init failed: ${e.message}")
            }
        }
        return bassBoost
    }

    private fun ensureVirtualizer(sessionId: Int): Virtualizer? {
        if (sessionId <= 0) return null
        if (virtualizer == null) {
            try {
                virtualizer = Virtualizer(0, sessionId)
            } catch (e: Throwable) {
                Log.w(TAG, "Virtualizer init failed: ${e.message}")
            }
        }
        return virtualizer
    }

    private fun ensurePresetReverb(sessionId: Int): PresetReverb? {
        if (sessionId <= 0) return null
        if (presetReverb == null) {
            try {
                presetReverb = PresetReverb(0, sessionId)
            } catch (e: Throwable) {
                Log.w(TAG, "PresetReverb init failed: ${e.message}")
            }
        }
        return presetReverb
    }

    fun applyAllEffects() {
        dspControlScope.launch {
            applyAllEffectsInternal()
        }
    }

    private fun applyAllEffectsInternal() {
        val cfg = currentConfig
        val toggles = currentToggles
        val mp = activeMediaPlayerRef
        val sessionId = activeSessionId

        val eqEffective = toggles.equalizerEnabled && cfg.isEqualizerActive
        val vocalReducerEffective = toggles.vocalReducerEnabled && cfg.isVocalReducerActive
        val pitchReverbEffective = toggles.pitchAndReverbEnabled && cfg.isPitchReverbActive
        val spatial8DEffective = toggles.spatial8DAudioEnabled && cfg.isSpatial8DActive
        val ambientEffective = toggles.ambientMixerEnabled && cfg.isAmbientMixerActive
        val volBoostEffective = isVolumeBoosterEnabled && volumeBoostPercent > 100

        // 1, 4 & Volume Booster Pre-Amp: Equalizer + BassBoost + Vocal Reducer + Volume Pre-Amp
        val shouldEnableEq = eqEffective ||
            vocalReducerEffective ||
            volBoostEffective ||
            (pitchReverbEffective && cfg.reverbLevel > 0)

        if (shouldEnableEq || equalizer != null) {
            try {
                val eq = if (shouldEnableEq) ensureEqualizer(sessionId) else equalizer
                if (eq != null) {
                    eq.enabled = shouldEnableEq
                    if (shouldEnableEq) {
                        val numBands = eq.numberOfBands.toInt().coerceAtLeast(1)
                        val range = eq.bandLevelRange
                        val minMb = range.getOrElse(0) { -1500 }.toInt()
                        val maxMb = range.getOrElse(1) { 1500 }.toInt()

                        val volPreAmpDb = if (volBoostEffective) {
                            ((volumeBoostPercent - 100) / 200f) * 9.5f
                        } else 0f

                        val userBands = cfg.bandList()
                        for (b in 0 until numBands) {
                            val mappedIdx = ((b.toFloat() / numBands.toFloat()) * userBands.size)
                                .toInt()
                                .coerceIn(0, userBands.lastIndex)
                            var targetDb = if (eqEffective) userBands[mappedIdx] else 0f

                            // Apply Volume Booster hardware EQ pre-amp across all bands
                            targetDb += volPreAmpDb

                            if (vocalReducerEffective) {
                                val reductionRatio = (cfg.vocalReductionPercent / 100f).coerceIn(0.2f, 1.0f)
                                when (mappedIdx) {
                                    2 -> targetDb -= 12.0f * reductionRatio
                                    3 -> targetDb -= 10.0f * reductionRatio
                                    1 -> targetDb -= 5.0f * reductionRatio
                                    0 -> targetDb += 3.0f * reductionRatio
                                    4 -> targetDb += 3.0f * reductionRatio
                                }
                            }

                            if (pitchReverbEffective && cfg.reverbLevel > 0) {
                                if (mappedIdx == 0 || mappedIdx == 4) {
                                    targetDb += cfg.reverbLevel * 0.9f
                                }
                            }

                            val targetMb = (targetDb * 100f).toInt().coerceIn(minMb, maxMb).toShort()
                            eq.setBandLevel(b.toShort(), targetMb)
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to apply Equalizer: ${e.message}")
            }
        }

        val shouldEnableBass = (eqEffective && cfg.bassBoostStrength > 0) || (volBoostEffective && volumeBoostPercent >= 150)
        if (shouldEnableBass || bassBoost != null) {
            try {
                val bb = if (shouldEnableBass) ensureBassBoost(sessionId) else bassBoost
                if (bb != null) {
                    bb.enabled = shouldEnableBass
                    if (bb.strengthSupported && shouldEnableBass) {
                        val eqBass = if (eqEffective) cfg.bassBoostStrength else 0
                        val boostBass = if (volBoostEffective) ((volumeBoostPercent - 100) * 2).coerceIn(0, 400) else 0
                        bb.setStrength(maxOf(eqBass, boostBass).coerceIn(0, 1000).toShort())
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to apply BassBoost: ${e.message}")
            }
        }

        // 2: Slowed + Reverb & Nightcore (PlaybackParams Speed & Pitch + PresetReverb)
        if (mp != null) {
            if (pitchReverbEffective) {
                try {
                    val targetSpeed = cfg.tempoSpeed.coerceIn(0.5f, 2.0f)
                    val targetPitch = cfg.pitchFactor.coerceIn(0.5f, 2.0f)
                    val wasPlaying = try { mp.isPlaying } catch (_: Exception) { false }
                    val params = PlaybackParams().apply {
                        speed = targetSpeed
                        pitch = targetPitch
                    }
                    mp.playbackParams = params
                    wasPitchReverbApplied = true
                    if (!wasPlaying && mp.isPlaying) {
                        mp.pause()
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to apply Pitch/Tempo PlaybackParams: ${e.message}")
                }
            } else if (wasPitchReverbApplied) {
                // Reset back to normal base speed and 1.0f pitch when Slowed/Nightcore is turned OFF
                try {
                    val wasPlaying = try { mp.isPlaying } catch (_: Exception) { false }
                    val params = PlaybackParams().apply {
                        speed = basePlaybackSpeed.coerceIn(0.5f, 2.0f)
                        pitch = 1.0f
                    }
                    mp.playbackParams = params
                    wasPitchReverbApplied = false
                    if (!wasPlaying && mp.isPlaying) {
                        mp.pause()
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to reset Pitch/Tempo PlaybackParams: ${e.message}")
                }
            }
        }

        val activeReverb = pitchReverbEffective && cfg.reverbLevel > 0
        if (activeReverb || presetReverb != null) {
            try {
                val rev = if (activeReverb) ensurePresetReverb(sessionId) else presetReverb
                if (rev != null) {
                    if (activeReverb) {
                        val presetShort = when (cfg.reverbLevel) {
                            1 -> PresetReverb.PRESET_SMALLROOM
                            2 -> PresetReverb.PRESET_MEDIUMHALL
                            3 -> PresetReverb.PRESET_LARGEHALL
                            4 -> PresetReverb.PRESET_PLATE
                            else -> PresetReverb.PRESET_NONE
                        }
                        rev.preset = presetShort
                        rev.enabled = true
                    } else {
                        rev.preset = PresetReverb.PRESET_NONE
                        rev.enabled = false
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to apply PresetReverb: ${e.message}")
            }
        }

        // 3: 8D / 360° Spatial Audio Panning & Virtualizer
        val shouldVirt = spatial8DEffective || vocalReducerEffective
        if (shouldVirt || virtualizer != null) {
            try {
                val virt = if (shouldVirt) ensureVirtualizer(sessionId) else virtualizer
                if (virt != null) {
                    virt.enabled = shouldVirt
                    if (virt.strengthSupported && shouldVirt) {
                        val strength = if (spatial8DEffective) cfg.spatialDepthStrength else 650
                        virt.setStrength(strength.coerceIn(0, 1000).toShort())
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to apply Virtualizer: ${e.message}")
            }
        }

        if (spatial8DEffective) {
            startSpatialPanningLoopIfNeeded()
        } else {
            val hadPanning = spatialPanningJob != null
            stopSpatialPanningLoop()
            if (hadPanning || mp != null) {
                try {
                    mp?.setVolume(1.0f, 1.0f)
                } catch (_: Throwable) {
                }
            }
        }

        // 5: Background Ambient Sound & Vinyl Crackle Mixer
        if (ambientEffective) {
            startAmbientSynthesizerIfNeeded()
        } else {
            stopAmbientSynthesizer()
        }
    }

    private fun startSpatialPanningLoopIfNeeded() {
        if (spatialPanningJob?.isActive == true) return
        spatialPanningJob = dspWorkerScope.launch {
            var angleRad = 0.0
            val stepMs = 40L
            while (isActive) {
                val cfg = currentConfig
                val toggles = currentToggles
                if (!toggles.spatial8DAudioEnabled || !cfg.isSpatial8DActive) break

                val periodSec = cfg.spatialRotationSeconds.coerceIn(3f, 16f)
                val deltaRad = (2.0 * PI) * (stepMs / (periodSec * 1000.0))
                angleRad = (angleRad + deltaRad) % (2.0 * PI)

                val pan = sin(angleRad).toFloat() // -1.0 (Full Left) .. +1.0 (Full Right)
                val depthFactor = (cfg.spatialDepthStrength / 1000f).coerceIn(0.30f, 1.0f)
                val distanceEnvelope = 0.88f + 0.12f * cos(angleRad * 2.0).toFloat()
                val leftGain = ((1f - pan * depthFactor * 0.82f).coerceIn(0.14f, 1.0f) * distanceEnvelope).coerceIn(0.12f, 1.0f)
                val rightGain = ((1f + pan * depthFactor * 0.82f).coerceIn(0.14f, 1.0f) * distanceEnvelope).coerceIn(0.12f, 1.0f)

                try {
                    val mp = activeMediaPlayerRef
                    if (mp != null && mp.isPlaying) {
                        mp.setVolume(leftGain, rightGain)
                    }
                } catch (_: Throwable) {
                }

                delay(stepMs)
            }
        }
    }

    private fun stopSpatialPanningLoop() {
        spatialPanningJob?.cancel()
        spatialPanningJob = null
    }

    private fun startAmbientSynthesizerIfNeeded() {
        if (ambientSynthJob?.isActive == true) return
        ambientSynthJob = dspWorkerScope.launch {
            val sampleRate = 22050
            val bufferSamples = 2048
            val minBufBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(bufferSamples * 4)

            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (e: Throwable) {
                Log.w(TAG, "Ambient AudioTrack init failed: ${e.message}")
                null
            } ?: return@launch

            ambientAudioTrack = track
            try {
                track.play()
                val pcmBuffer = ShortArray(bufferSamples * 2) // Stereo interleaved
                var pinkB0 = 0f
                var pinkB1 = 0f
                var pinkB2 = 0f
                var windLP = 0f
                var windPhase = 0.0
                var fireEnvelope = 0f

                while (isActive) {
                    val cfg = currentConfig
                    val toggles = currentToggles
                    if (!toggles.ambientMixerEnabled || !cfg.isAmbientMixerActive) break

                    val rainVol = if (cfg.rainEnabled) cfg.rainVolume.coerceIn(0f, 1f) else 0f
                    val vinylVol = if (cfg.vinylCrackleEnabled) cfg.vinylCrackleVolume.coerceIn(0f, 1f) else 0f
                    val windVol = if (cfg.nightWindEnabled) cfg.nightWindVolume.coerceIn(0f, 1f) else 0f
                    val fireVol = if (cfg.campfireEnabled) cfg.campfireVolume.coerceIn(0f, 1f) else 0f

                    if (rainVol <= 0.001f && vinylVol <= 0.001f && windVol <= 0.001f && fireVol <= 0.001f) {
                        delay(150L)
                        continue
                    }

                    for (i in 0 until bufferSamples) {
                        val white = Random.nextFloat() * 2f - 1f

                        // 1. Rain (Pink-filtered soft rainfall + micro droplets)
                        pinkB0 = 0.997f * pinkB0 + white * 0.029f
                        pinkB1 = 0.985f * pinkB1 + white * 0.032f
                        pinkB2 = 0.950f * pinkB2 + white * 0.048f
                        val pinkNoise = (pinkB0 + pinkB1 + pinkB2 + white * 0.018f)
                        val rainSample = pinkNoise * rainVol * 0.44f

                        // 2. Vinyl Crackle (Warm needle hiss + random micro vinyl pops)
                        var vinylSample = white * 0.035f * vinylVol
                        if (vinylVol > 0f && Random.nextFloat() < 0.0026f) {
                            vinylSample += (Random.nextFloat() * 1.4f - 0.5f) * vinylVol * 0.62f
                        }

                        // 3. Night Wind (Slowly modulated low-pass sweeping breeze)
                        windPhase += 1.0 / sampleRate
                        val windMod = (0.55f + 0.45f * sin(windPhase * 0.85 * PI).toFloat())
                        windLP = windLP * 0.985f + white * 0.015f
                        val windSample = windLP * windMod * windVol * 1.25f

                        // 4. Campfire (Warm low rumble + crackling embers)
                        fireEnvelope = (fireEnvelope * 0.992f).coerceAtLeast(0f)
                        if (fireVol > 0f && Random.nextFloat() < 0.0038f) {
                            fireEnvelope = Random.nextFloat() * 0.90f
                        }
                        val fireSample = (windLP * 0.38f + white * fireEnvelope * 0.48f) * fireVol

                        val mixedLeft = (rainSample + vinylSample + windSample + fireSample).coerceIn(-0.92f, 0.92f)
                        val mixedRight = (rainSample * 0.96f + vinylSample * 1.04f + windSample + fireSample * 0.95f).coerceIn(-0.92f, 0.92f)

                        pcmBuffer[i * 2] = (mixedLeft * Short.MAX_VALUE).toInt().toShort()
                        pcmBuffer[i * 2 + 1] = (mixedRight * Short.MAX_VALUE).toInt().toShort()
                    }

                    track.write(pcmBuffer, 0, pcmBuffer.size)
                }
            } catch (_: Throwable) {
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: Throwable) {
                }
                if (ambientAudioTrack === track) {
                    ambientAudioTrack = null
                }
            }
        }
    }

    private fun stopAmbientSynthesizer() {
        ambientSynthJob?.cancel()
        ambientSynthJob = null
        try {
            ambientAudioTrack?.stop()
            ambientAudioTrack?.release()
        } catch (_: Throwable) {
        }
        ambientAudioTrack = null
    }

    fun releaseHardwareEffects() {
        dspControlScope.launch {
            releaseHardwareEffectsInternal()
        }
    }

    private fun releaseHardwareEffectsInternal() {
        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (_: Throwable) {
        }
        equalizer = null

        try {
            bassBoost?.enabled = false
            bassBoost?.release()
        } catch (_: Throwable) {
        }
        bassBoost = null

        try {
            virtualizer?.enabled = false
            virtualizer?.release()
        } catch (_: Throwable) {
        }
        virtualizer = null

        try {
            presetReverb?.enabled = false
            presetReverb?.release()
        } catch (_: Throwable) {
        }
        presetReverb = null
        activeSessionId = 0
    }
}
