package com.example.model

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translatedText: String? = null
)

object LrcParser {
    private val TIME_TAG_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(lrcContent: String): List<LyricLine> {
        if (lrcContent.isBlank()) return emptyList()
        val result = mutableListOf<LyricLine>()

        lrcContent.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return@forEach

            val matches = TIME_TAG_REGEX.findAll(trimmed).toList()
            if (matches.isEmpty()) return@forEach

            val rawLyricText = trimmed.replace(TIME_TAG_REGEX, "").trim()
            val trMarker = " • [TR: "
            val trIdx = rawLyricText.indexOf(trMarker)
            val lyricText: String
            val inlineTr: String?
            if (trIdx >= 0 && rawLyricText.endsWith("]")) {
                lyricText = rawLyricText.substring(0, trIdx).trim()
                inlineTr = rawLyricText.substring(trIdx + trMarker.length, rawLyricText.length - 1).trim().takeIf { it.isNotEmpty() }
            } else {
                lyricText = rawLyricText
                inlineTr = null
            }
            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fractionRaw = match.groupValues.getOrNull(3).orEmpty()
                val millis = when (fractionRaw.length) {
                    0 -> 0L
                    1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100L
                    2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10L
                    else -> fractionRaw.take(3).toLongOrNull() ?: 0L
                }
                val totalMs = (minutes * 60_000L) + (seconds * 1_000L) + millis
                result.add(
                    LyricLine(
                        timeMs = totalMs,
                        text = lyricText.ifEmpty { "♪" },
                        translatedText = inlineTr
                    )
                )
            }
        }
        return result.sortedBy { it.timeMs }
    }

    fun formatTimestamp(timeMs: Long): String {
        val safeMs = timeMs.coerceAtLeast(0L)
        val totalSeconds = safeMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format("%02d:%02d", minutes, seconds)
    }

    val SAMPLE_LRC = """
        [00:00.00]♪ Sakura Music Senkronize Sözler ♪
        [00:04.50]Kiraz çiçekleri gece rüzgarında süzülüyor
        [00:09.20]Her nota kalbin ritmiyle yeniden yankılanıyor
        [00:14.80]Uzak yıldızların altında sessiz bir melodi
        [00:20.10]Pembe yapraklar dökülürken zaman duruyor
        [00:26.00]Gözlerini kapat ve ritmin akışına bırak kendini
        [00:32.40]Sakura esintisiyle başlayan bu yolculukta
        [00:38.00]Ses dalgaları gecenin karanlığını aydınlatıyor
        [00:44.50]♪ Enstrümantal Ara ♪
    """.trimIndent()
}
