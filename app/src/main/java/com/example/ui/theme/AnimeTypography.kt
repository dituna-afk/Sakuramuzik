package com.example.ui.theme

import androidx.compose.ui.text.font.FontFamily
import com.example.data.CustomMinimalThemeManager

val OswaldFontFamily: FontFamily = FontFamily.SansSerif
val CinzelFontFamily: FontFamily = FontFamily.Serif
val RighteousFontFamily: FontFamily = FontFamily.Monospace

fun AppTheme.getCustomFontFamily(enabled: Boolean = true): FontFamily? {
    val userFontPath = CustomMinimalThemeManager.configFlow.value.customFontPath
    val userUploadedFont = CustomMinimalThemeManager.resolveCustomFontFamily(userFontPath)
    if (this == AppTheme.SADE_TEMA && userUploadedFont != null) {
        return userUploadedFont
    }
    if (!enabled) return userUploadedFont
    return when (this) {
        AppTheme.SADE_TEMA -> userUploadedFont
        AppTheme.BERSERK_GUTS, AppTheme.BERSERK_GRIFFITH -> userUploadedFont ?: CinzelFontFamily
        AppTheme.COWBOY_BEBOP_SPIKE -> userUploadedFont ?: RighteousFontFamily
        else -> userUploadedFont
    }
}
