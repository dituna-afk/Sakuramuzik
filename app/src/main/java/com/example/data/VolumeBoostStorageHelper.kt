package com.example.data

import android.content.Context

class VolumeBoostStorageHelper(context: Context) {
    private val prefs = context.getSharedPreferences("sakura_volume_boost_prefs", Context.MODE_PRIVATE)

    fun getVolumeBoostPercent(): Int {
        val raw = prefs.getInt("key_volume_boost_percent", 160).coerceIn(100, 300)
        // If booster is enabled but stored value was legacy 100%, upgrade to 160% so boost is audible immediately
        return if (isVolumeBoosterEnabled() && raw <= 100) 160 else raw
    }

    fun saveVolumeBoostPercent(percent: Int) {
        prefs.edit().putInt("key_volume_boost_percent", percent.coerceIn(100, 300)).apply()
    }

    fun isVolumeBoosterEnabled(): Boolean {
        return prefs.getBoolean("key_volume_booster_enabled", false)
    }

    fun saveVolumeBoosterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("key_volume_booster_enabled", enabled).apply()
    }
}
