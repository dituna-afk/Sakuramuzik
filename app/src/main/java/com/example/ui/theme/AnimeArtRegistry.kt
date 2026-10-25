package com.example.ui.theme

import com.example.R

data class AnimeThemeMetadata(
    val themeKey: String,
    val seriesTitle: String,
    val characterName: String,
    val japaneseTitle: String,
    val primaryAssetPath: String?,
    val secondaryAssetPath: String?,
    val systemVectorDrawableRes: Int?,
    val quote: String
)

object AnimeArtRegistry {
    const val ASSET_BERSERK_GUTS = "file:///android_asset/anime/berserk_guts_poster.jpg"
    const val ASSET_BERSERK_BEHELIT = "file:///android_asset/anime/berserk_behelit.jpg"
    const val ASSET_BERSERK_GRIFFITH_PORTRAIT = "file:///android_asset/anime/berserk_griffith_portrait.webp"
    const val ASSET_BERSERK_GRIFFITH_ARMOR = "file:///android_asset/anime/berserk_griffith_armor.png"
    const val ASSET_BEBOP_FAYE_YELLOW = "file:///android_asset/anime/bebop_faye_yellow.jpg"
    const val ASSET_BEBOP_FAYE_RETRO = "file:///android_asset/anime/bebop_faye_retro.jpg"

    fun getMetadata(theme: AppTheme): AnimeThemeMetadata? {
        return when (theme.name) {
            "BERSERK_GUTS" -> AnimeThemeMetadata(
                themeKey = "BERSERK_GUTS",
                seriesTitle = "Berserk (ベルセルク)",
                characterName = "Guts • The Black Swordsman",
                japaneseTitle = "黒い剣士 ガッツ",
                primaryAssetPath = ASSET_BERSERK_GUTS,
                secondaryAssetPath = ASSET_BERSERK_BEHELIT,
                systemVectorDrawableRes = R.drawable.ic_anime_hero_berserk,
                quote = "Kaderine boyun eğme; kılıcını savurmaya devam et."
            )
            "BERSERK_GRIFFITH" -> AnimeThemeMetadata(
                themeKey = "BERSERK_GRIFFITH",
                seriesTitle = "Berserk (ベルセルク)",
                characterName = "Griffith • Falcon of Light",
                japaneseTitle = "光の鷹 グリフィス",
                primaryAssetPath = null,
                secondaryAssetPath = null,
                systemVectorDrawableRes = null,
                quote = "Bir hayali olan insan asla durdurulamaz."
            )
            "COWBOY_BEBOP_SPIKE" -> AnimeThemeMetadata(
                themeKey = "COWBOY_BEBOP_SPIKE",
                seriesTitle = "Cowboy Bebop (カウボーイビバップ)",
                characterName = "Spike Spiegel • Swordfish II",
                japaneseTitle = "スパイク・スピーゲル",
                primaryAssetPath = ASSET_BEBOP_FAYE_RETRO,
                secondaryAssetPath = ASSET_BEBOP_FAYE_YELLOW,
                systemVectorDrawableRes = R.drawable.ic_anime_hero_bebop,
                quote = "See You Space Cowboy..."
            )
            else -> null
        }
    }

    fun isAnimeTheme(theme: AppTheme): Boolean = getMetadata(theme) != null
}
