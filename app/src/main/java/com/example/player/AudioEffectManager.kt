package com.example.player

import android.media.audiofx.LoudnessEnhancer
import android.util.Log

object AudioEffectManager {
    private const val TAG = "AudioEffectManager"
    private var onlineLoudnessEnhancer: LoudnessEnhancer? = null
    private var onlineSessionId: Int = 0

    private var offlineLoudnessEnhancer: LoudnessEnhancer? = null
    private var offlineSessionId: Int = 0

    @Synchronized
    fun applyOnlineVolumeBoost(
        audioSessionId: Int,
        percent: Int,
        enabled: Boolean,
        forceRecreate: Boolean = false
    ) {
        StudioDspController.updateVolumeBoostState(percent, enabled)
        if (audioSessionId <= 0) return
        val boostPercent = if (enabled && percent <= 100) 160 else percent.coerceIn(100, 300)
        val gainMb = if (enabled && boostPercent > 100) {
            ((boostPercent - 100) * 15).coerceIn(0, 3000)
        } else 0

        try {
            if (forceRecreate || onlineSessionId != audioSessionId) {
                releaseOnlineEffectsInternal()
                onlineSessionId = audioSessionId
            }
            if (onlineLoudnessEnhancer == null && gainMb > 0) {
                onlineLoudnessEnhancer = LoudnessEnhancer(audioSessionId)
            }
            val enhancer = onlineLoudnessEnhancer
            if (enhancer != null) {
                if (gainMb > 0) {
                    enhancer.setTargetGain(gainMb)
                    enhancer.enabled = true
                } else {
                    enhancer.setTargetGain(0)
                    enhancer.enabled = false
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Online LoudnessEnhancer fallback to EQ pre-amp: ${e.message}")
            releaseOnlineEffectsInternal()
        }
    }

    @Synchronized
    fun applyOfflineVolumeBoost(
        audioSessionId: Int,
        percent: Int,
        enabled: Boolean,
        forceRecreate: Boolean = false
    ) {
        StudioDspController.updateVolumeBoostState(percent, enabled)
        if (audioSessionId <= 0) return
        val boostPercent = if (enabled && percent <= 100) 160 else percent.coerceIn(100, 300)
        val gainMb = if (enabled && boostPercent > 100) {
            ((boostPercent - 100) * 15).coerceIn(0, 3000)
        } else 0

        try {
            if (forceRecreate || offlineSessionId != audioSessionId) {
                releaseOfflineEffectsInternal()
                offlineSessionId = audioSessionId
            }
            if (offlineLoudnessEnhancer == null && gainMb > 0) {
                offlineLoudnessEnhancer = LoudnessEnhancer(audioSessionId)
            }
            val enhancer = offlineLoudnessEnhancer
            if (enhancer != null) {
                if (gainMb > 0) {
                    enhancer.setTargetGain(gainMb)
                    enhancer.enabled = true
                } else {
                    enhancer.setTargetGain(0)
                    enhancer.enabled = false
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Offline LoudnessEnhancer fallback to EQ pre-amp: ${e.message}")
            releaseOfflineEffectsInternal()
        }
    }

    @Synchronized
    fun releaseOnlineEffects() {
        releaseOnlineEffectsInternal()
    }

    private fun releaseOnlineEffectsInternal() {
        try {
            onlineLoudnessEnhancer?.setTargetGain(0)
            onlineLoudnessEnhancer?.enabled = false
            onlineLoudnessEnhancer?.release()
        } catch (_: Throwable) {
        }
        onlineLoudnessEnhancer = null
        onlineSessionId = 0
    }

    @Synchronized
    fun releaseOfflineEffects() {
        releaseOfflineEffectsInternal()
    }

    private fun releaseOfflineEffectsInternal() {
        try {
            offlineLoudnessEnhancer?.setTargetGain(0)
            offlineLoudnessEnhancer?.enabled = false
            offlineLoudnessEnhancer?.release()
        } catch (_: Throwable) {
        }
        offlineLoudnessEnhancer = null
        offlineSessionId = 0
    }
}
