package com.example.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.DrawableRes
import com.example.R

/**
 * Defines all selectable application icons with strict separation between:
 * 1. [previewDrawableResId]: Clean, uncropped 1:1 raster PNG in `res/drawable/` used exclusively for in-app Settings UI previews.
 * 2. [launcherIconResId] & [launcherRoundIconResId]: Installed Android home-screen `<adaptive-icon>` / mipmap resources referenced by `<activity-alias>` in `AndroidManifest.xml`.
 * 3. [foregroundDrawableResId] & [backgroundDrawableResId]: Adaptive icon foreground (66dp safe-zone) and background layers.
 */
enum class AppIconOption(
    val id: String,
    val aliasName: String,
    val displayName: String,
    val description: String,
    @DrawableRes val previewDrawableResId: Int,
    @DrawableRes val launcherIconResId: Int,
    @DrawableRes val launcherRoundIconResId: Int,
    @DrawableRes val foregroundDrawableResId: Int,
    @DrawableRes val backgroundDrawableResId: Int,
    @DrawableRes val drawableResId: Int = previewDrawableResId
) {
    DEFAULT(
        id = "default",
        aliasName = "LauncherDefault",
        displayName = "Klasik Pembe Sakura (Varsayılan)",
        description = "Geleneksel pembe kiraz çiçeği launcher simgesi",
        previewDrawableResId = R.drawable.ic_preview_sakura_default,
        launcherIconResId = R.mipmap.ic_launcher,
        launcherRoundIconResId = R.mipmap.ic_launcher_round,
        foregroundDrawableResId = R.drawable.ic_launcher_foreground,
        backgroundDrawableResId = R.drawable.ic_launcher_background
    ),
    NEON(
        id = "neon",
        aliasName = "LauncherNeon",
        displayName = "Neon Cyberpunk Sakura",
        description = "Elektrik pembe ve neon mavi temalı simge",
        previewDrawableResId = R.drawable.ic_preview_sakura_neon,
        launcherIconResId = R.mipmap.ic_launcher_neon,
        launcherRoundIconResId = R.mipmap.ic_launcher_neon_round,
        foregroundDrawableResId = R.drawable.ic_launcher_neon_foreground,
        backgroundDrawableResId = R.drawable.ic_launcher_neon_background
    ),
    DARK(
        id = "dark",
        aliasName = "LauncherDark",
        displayName = "Yozakura Gece Mehtabı",
        description = "Koyu mor ve gümüş ay ışığı simgesi",
        previewDrawableResId = R.drawable.ic_preview_sakura_dark,
        launcherIconResId = R.mipmap.ic_launcher_dark,
        launcherRoundIconResId = R.mipmap.ic_launcher_dark_round,
        foregroundDrawableResId = R.drawable.ic_launcher_dark_foreground,
        backgroundDrawableResId = R.drawable.ic_launcher_dark_background
    ),
    GOLDEN(
        id = "golden",
        aliasName = "LauncherGolden",
        displayName = "Altın Sonbahar Sakura",
        description = "Kehribar ve somon tonlarında sonbahar simgesi",
        previewDrawableResId = R.drawable.ic_preview_sakura_golden,
        launcherIconResId = R.mipmap.ic_launcher_golden,
        launcherRoundIconResId = R.mipmap.ic_launcher_golden_round,
        foregroundDrawableResId = R.drawable.ic_launcher_golden_foreground,
        backgroundDrawableResId = R.drawable.ic_launcher_golden_background
    ),
    BERSERK_GUTS(
        id = "berserk_guts",
        aliasName = "LauncherBerserkGuts",
        displayName = "Berserk - Kara Kılıçlı (Guts)",
        description = "Kurban Mührü ve Ejderhakatan kılıcı simgesi (Güvenli Alanlı)",
        previewDrawableResId = R.drawable.ic_preview_anime_guts,
        launcherIconResId = R.mipmap.ic_launcher_guts,
        launcherRoundIconResId = R.mipmap.ic_launcher_guts_round,
        foregroundDrawableResId = R.drawable.ic_launcher_guts_foreground,
        backgroundDrawableResId = R.drawable.ic_launcher_guts_background
    ),
    BERSERK_GRIFFITH(
        id = "berserk_griffith",
        aliasName = "LauncherGriffith",
        displayName = "Berserk - Işığın Şahini (Griffith)",
        description = "Şahin kanatları ve gümüş zırh arması (Güvenli Alanlı)",
        previewDrawableResId = R.drawable.ic_preview_anime_griffith,
        launcherIconResId = R.mipmap.ic_launcher_griffith,
        launcherRoundIconResId = R.mipmap.ic_launcher_griffith_round,
        foregroundDrawableResId = R.drawable.ic_launcher_griffith_foreground,
        backgroundDrawableResId = R.drawable.ic_launcher_griffith_background
    ),
    EVANGELION_01(
        id = "evangelion_01",
        aliasName = "LauncherEvangelion",
        displayName = "Neon Genesis Evangelion (EVA-01)",
        description = "Mor, neon yeşil ve AT-Field mecha simgesi (Güvenli Alanlı)",
        previewDrawableResId = R.drawable.ic_preview_anime_eva,
        launcherIconResId = R.mipmap.ic_launcher_eva,
        launcherRoundIconResId = R.mipmap.ic_launcher_eva_round,
        foregroundDrawableResId = R.drawable.ic_launcher_eva_foreground,
        backgroundDrawableResId = R.drawable.ic_launcher_eva_background
    ),
    COWBOY_BEBOP(
        id = "cowboy_bebop",
        aliasName = "LauncherCowboyBebop",
        displayName = "Cowboy Bebop (Spike & Faye)",
        description = "Retro siberpunk ve Swordfish II yıldız simgesi (Güvenli Alanlı)",
        previewDrawableResId = R.drawable.ic_preview_anime_bebop,
        launcherIconResId = R.mipmap.ic_launcher_bebop,
        launcherRoundIconResId = R.mipmap.ic_launcher_bebop_round,
        foregroundDrawableResId = R.drawable.ic_launcher_bebop_foreground,
        backgroundDrawableResId = R.drawable.ic_launcher_bebop_background
    );

    companion object {
        fun fromId(id: String?): AppIconOption {
            return entries.find { it.id == id } ?: DEFAULT
        }
    }
}

object AppIconManager {
    private const val TAG = "AppIconManager"
    private const val PREFS_NAME = "sakura_app_icon_prefs"
    private const val KEY_ICON = "selected_app_icon_id"

    fun componentNameFor(context: Context, option: AppIconOption): ComponentName {
        return ComponentName(context.packageName, "com.example.${option.aliasName}")
    }

    fun isAliasCurrentlyEnabled(
        pm: PackageManager,
        pkgName: String,
        option: AppIconOption
    ): Boolean {
        val comp = ComponentName(pkgName, "com.example.${option.aliasName}")
        val state = pm.getComponentEnabledSetting(comp)
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (option == AppIconOption.DEFAULT && state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
    }

    private fun persistSelectedIconSync(context: Context, option: AppIconOption) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.edit().putString(KEY_ICON, option.id).commit()) {
                prefs.edit().putString(KEY_ICON, option.id).apply()
            }
        }
    }

    fun loadSelectedIcon(context: Context): AppIconOption {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_ICON, null)
            if (!raw.isNullOrBlank()) {
                return AppIconOption.fromId(raw)
            }
            // If preference is not yet written, inspect PackageManager for any active alias
            val pm = context.packageManager
            val pkgName = context.packageName
            val activeNonDefault = AppIconOption.entries.firstOrNull { opt ->
                opt != AppIconOption.DEFAULT && isAliasCurrentlyEnabled(pm, pkgName, opt)
            }
            val resolved = activeNonDefault ?: AppIconOption.DEFAULT
            persistSelectedIconSync(context, resolved)
            resolved
        } catch (_: Throwable) {
            AppIconOption.DEFAULT
        }
    }

    /**
     * Reconciles the persisted icon choice with the device's PackageManager alias states on startup.
     * Guarantees that the user's selected icon alias is enabled and that no zero-alias or multi-alias
     * conflict occurs across app restarts or updates.
     */
    fun ensureValidLauncherState(context: Context) {
        try {
            val pm = context.packageManager
            val pkgName = context.packageName
            val selected = loadSelectedIcon(context)
            val isTargetEnabled = isAliasCurrentlyEnabled(pm, pkgName, selected)
            val otherEnabledAliases = AppIconOption.entries.filter { opt ->
                opt != selected && isAliasCurrentlyEnabled(pm, pkgName, opt)
            }

            if (!isTargetEnabled || otherEnabledAliases.isNotEmpty()) {
                applyAppIcon(context, selected)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in ensureValidLauncherState: ${e.message}", e)
            runCatching {
                val defaultComp = ComponentName(context.packageName, "com.example.LauncherDefault")
                context.packageManager.setComponentEnabledSetting(
                    defaultComp,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }

    /**
     * Changes the launcher icon by switching activity-aliases safely.
     * 1. Persists selection synchronously via `.commit()` so restarts immediately reflect the choice.
     * 2. Enables the target alias FIRST (`DONT_KILL_APP`) so the app never has zero launcher entrypoints.
     * 3. Disables only other aliases that are not already disabled.
     */
    fun applyAppIcon(context: Context, option: AppIconOption) {
        persistSelectedIconSync(context, option)

        val pm = context.packageManager
        val pkgName = context.packageName

        // 1. Target component MUST be enabled FIRST
        val targetComponent = ComponentName(pkgName, "com.example.${option.aliasName}")
        try {
            if (!isAliasCurrentlyEnabled(pm, pkgName, option)) {
                pm.setComponentEnabledSetting(
                    targetComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                Log.i(TAG, "Successfully enabled target launcher alias: ${option.aliasName}")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to enable target alias ${option.aliasName}: ${e.message}", e)
            runCatching {
                pm.setComponentEnabledSetting(
                    ComponentName(pkgName, "com.example.LauncherDefault"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            return
        }

        // 2. Disable other aliases AFTER target is confirmed enabled
        AppIconOption.entries.filter { it != option }.forEach { opt ->
            val otherComponent = ComponentName(pkgName, "com.example.${opt.aliasName}")
            try {
                val currentState = pm.getComponentEnabledSetting(otherComponent)
                if (currentState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    pm.setComponentEnabledSetting(
                        otherComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not disable alias ${opt.aliasName}: ${e.message}")
            }
        }

        // Clean up legacy/removed aliases if they still exist in system package-restrictions
        runCatching {
            val legacyComp = ComponentName(pkgName, "com.example.LauncherChainsaw")
            if (pm.getComponentEnabledSetting(legacyComp) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(
                    legacyComp,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
