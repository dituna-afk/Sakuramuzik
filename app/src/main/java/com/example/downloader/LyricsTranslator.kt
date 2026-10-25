package com.example.downloader

import android.util.LruCache
import com.example.model.LyricLine
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object LyricsTranslator {

    private val translatedCache = LruCache<String, List<LyricLine>>(48)

    private fun buildCacheKey(lines: List<LyricLine>, engine: TranslationEngine): String {
        val signature = lines.fold(17) { acc, line -> 31 * acc + line.text.hashCode() }
        return "${engine.name}|${lines.size}|$signature"
    }

    fun getCachedTranslation(lines: List<LyricLine>, engine: TranslationEngine): List<LyricLine>? {
        if (lines.isEmpty()) return null
        return translatedCache.get(buildCacheKey(lines, engine))
    }

    suspend fun translateLyricLinesToTurkish(
        lines: List<LyricLine>,
        engine: TranslationEngine = TranslationEngine.GOOGLE_TRANSLATE
    ): List<LyricLine> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext lines

        val key = buildCacheKey(lines, engine)
        val cached = translatedCache.get(key)
        if (cached != null) {
            return@withContext cached
        }

        // Prepare text lines for batch translation
        val textLines = lines.map { it.text }
        val joinedText = textLines.joinToString("\n")

        val translatedBlock = when (engine) {
            TranslationEngine.DEEPL -> translateTextToTurkishDeepL(joinedText)
                ?: translateTextToTurkishGoogle(joinedText)
            TranslationEngine.GOOGLE_TRANSLATE -> translateTextToTurkishGoogle(joinedText)
        }

        if (translatedBlock.isNullOrBlank()) {
            return@withContext lines
        }

        val translatedLines = translatedBlock.lines()

        val result = lines.mapIndexed { idx, original ->
            val trText = translatedLines.getOrNull(idx)?.trim()
            if (!trText.isNullOrBlank() && trText != original.text) {
                original.copy(translatedText = trText)
            } else {
                original
            }
        }
        translatedCache.put(key, result)
        result
    }

    private fun translateTextToTurkishGoogle(text: String): String? {
        if (text.isBlank()) return null
        return try {
            val encodedQuery = URLEncoder.encode(text, "UTF-8")
            val endpoint = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=tr&dt=t&q=$encodedQuery")
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6_000
                readTimeout = 6_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }

            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }
            conn.disconnect()

            val jsonArray = JSONArray(body)
            val sentencesArray = jsonArray.optJSONArray(0) ?: return null
            val sb = StringBuilder()
            for (i in 0 until sentencesArray.length()) {
                val sentence = sentencesArray.optJSONArray(i) ?: continue
                val translatedChunk = sentence.optString(0, "")
                sb.append(translatedChunk)
            }
            sb.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun translateTextToTurkishDeepL(text: String): String? {
        if (text.isBlank()) return null
        return try {
            val endpoint = URL("https://api-free.deepl.com/v2/translate")
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6_000
                readTimeout = 6_000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            }
            val postData = "target_lang=TR&text=${URLEncoder.encode(text, "UTF-8")}"
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(postData)
                writer.flush()
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }

            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }
            conn.disconnect()

            val json = JSONObject(body)
            val translations = json.optJSONArray("translations") ?: return null
            val sb = StringBuilder()
            for (i in 0 until translations.length()) {
                val item = translations.optJSONObject(i) ?: continue
                sb.append(item.optString("text", ""))
            }
            val result = sb.toString()
            if (result.isNotBlank()) result else null
        } catch (_: Exception) {
            null
        }
    }
}
