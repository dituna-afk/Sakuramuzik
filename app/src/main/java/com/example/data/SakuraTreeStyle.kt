package com.example.data

import androidx.compose.ui.graphics.Color

enum class SakuraTreeStyle(
    val id: String,
    val displayName: String,
    val description: String,
    // Blossom Petal Primary & Secondary colors
    val petalPrimary: Color,
    val petalSecondary: Color,
    val primaryAccent: Color,
    // Bark / Trunk Colors
    val barkDarkColor: Color,
    val barkMidColor: Color,
    val barkHighlight: Color,
    // Ambient Glow Colors
    val glowColor: Color,
    val auraAlpha: Float = 0.25f,
    val isNeonCyberpunk: Boolean = false,
    val isCrystalIce: Boolean = false
) {
    TRADITIONAL_SAKURA(
        id = "traditional_sakura",
        displayName = "Temayla Uyumlu Sakura (Otomatik Renk)",
        description = "Ağaç ve çiçek renkleri seçtiğiniz Sakura temasına (veya şarkı kapağı rengine) göre otomatik değişir",
        petalPrimary = Color(0xFFFFB7C5),
        petalSecondary = Color(0xFFFF8DA1),
        primaryAccent = Color(0xFFFF4081),
        barkDarkColor = Color(0xFF2A1B28),
        barkMidColor = Color(0xFF4A2F3B),
        barkHighlight = Color(0xFFFF8DA1).copy(alpha = 0.35f),
        glowColor = Color(0xFFFF4081),
        auraAlpha = 0.22f
    ),

    NEON_CYBERPUNK(
        id = "neon_cyberpunk",
        displayName = "Neon Pembe Sakura (Cyberpunk)",
        description = "Canlı neon pembe ve magenta kiraz çiçekleri, neon siyan çizgili gövde ve elektrik pembe aura",
        petalPrimary = Color(0xFFFF1493), // Deep Hot Pink
        petalSecondary = Color(0xFFFF69B4), // Bright Hot Pink
        primaryAccent = Color(0xFFFF007F), // Electric Neon Pink Accent
        barkDarkColor = Color(0xFF140722), // Deep Cyber Indigo
        barkMidColor = Color(0xFF2D0E45), // Dark Neon Violet
        barkHighlight = Color(0xFF00FFFF).copy(alpha = 0.85f), // Electric Cyan Neon Contour
        glowColor = Color(0xFFFF007F), // Electric Neon Pink Glow
        auraAlpha = 0.45f,
        isNeonCyberpunk = true
    ),

    YOZAKURA_NIGHT(
        id = "yozakura_night",
        displayName = "Gece Mehtabı Sakura (Yozakura)",
        description = "Derin Mor ve Gümüş Mavisi yapraklar, gece ay ışığı aurası",
        petalPrimary = Color(0xFFC7B8EA),
        petalSecondary = Color(0xFF8C73B8),
        primaryAccent = Color(0xFFB388FF),
        barkDarkColor = Color(0xFF0F0B1E),
        barkMidColor = Color(0xFF1D1736),
        barkHighlight = Color(0xFFB388FF).copy(alpha = 0.45f),
        glowColor = Color(0xFF7C4DFF),
        auraAlpha = 0.30f
    ),

    GOLDEN_SUNSET(
        id = "golden_sunset",
        displayName = "Altın Sonbahar Sakura",
        description = "Sıcak Kehribar ve Somon yapraklar, maun ahşap gövde",
        petalPrimary = Color(0xFFFFD54F),
        petalSecondary = Color(0xFFFF8A65),
        primaryAccent = Color(0xFFFF6D00),
        barkDarkColor = Color(0xFF23120B),
        barkMidColor = Color(0xFF422115),
        barkHighlight = Color(0xFFFFD54F).copy(alpha = 0.40f),
        glowColor = Color(0xFFFF9100),
        auraAlpha = 0.32f
    ),

    CRYSTAL_ICE(
        id = "crystal_ice",
        displayName = "Kristal Buz Sakura",
        description = "Buz Mavisi ve Lavanta yapraklar, donmuş kış gövdesi ve elmas ışıltı",
        petalPrimary = Color(0xFFE0F7FA),
        petalSecondary = Color(0xFF80DEEA),
        primaryAccent = Color(0xFF00E5FF),
        barkDarkColor = Color(0xFF102A38),
        barkMidColor = Color(0xFF1F485B),
        barkHighlight = Color(0xFFE0F7FA).copy(alpha = 0.55f),
        glowColor = Color(0xFF80DEEA),
        auraAlpha = 0.35f,
        isCrystalIce = true
    );

    companion object {
        val DEFAULT = TRADITIONAL_SAKURA

        fun fromId(id: String?): SakuraTreeStyle {
            return entries.find { it.id == id } ?: DEFAULT
        }
    }
}

val LocalSakuraTreeStyle = androidx.compose.runtime.staticCompositionLocalOf {
    SakuraTreeStyle.TRADITIONAL_SAKURA
}

enum class SakuraTreeForm(
    val id: String,
    val displayName: String,
    val description: String
) {
    CLASSIC_SWEEPING(
        id = "classic_sweeping",
        displayName = "Klasik Kavisli Gövde (Varsayılan)",
        description = "Sağ taraftan zarifçe süzülüp sola uzanan geleneksel Sakura kıvrımı"
    ),
    MAJESTIC_VERTICAL(
        id = "majestic_vertical",
        displayName = "Dikey Görkemli Gövde",
        description = "Merkezden göğe yükselen geniş simetrik taçlı görkemli gövde"
    ),
    ANCIENT_BONSAI(
        id = "ancient_bonsai",
        displayName = "Kadim Bonsai Formu",
        description = "Kıvrımlı, yaşlı ahşap ve yatay katmanlar halinde yayılan yoğun çiçek bulutları"
    ),
    TWIN_TRUNKS(
        id = "twin_trunks",
        displayName = "İkiz Gövdeli Uyum",
        description = "Kökten yükselip birbirine sarılan iki gövdenin oluşturduğu çifte çiçek tacı"
    );

    companion object {
        val DEFAULT = CLASSIC_SWEEPING

        fun fromId(id: String?): SakuraTreeForm {
            return entries.find { it.id == id } ?: DEFAULT
        }
    }
}
