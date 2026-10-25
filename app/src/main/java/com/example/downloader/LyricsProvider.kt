package com.example.downloader

import android.content.Context
import android.util.Base64
import android.util.LruCache
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class LyricsFetchResult(
    val lrcContent: String,
    val sourceLabel: String
)

object LyricsProvider {

    private val rawLrcMemoryCache = LruCache<String, String>(64)
    private val sourceMemoryCache = LruCache<String, String>(64)

    @Volatile
    private var persistentDir: File? = null

    @Volatile
    private var appContext: Context? = null

    private const val SOURCE_HEADER_PREFIX = "[by:SakuraSource="

    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        val dir = File(ctx.filesDir, "LyricsCache")
        if (!dir.exists()) {
            runCatching { dir.mkdirs() }
        }
        persistentDir = dir
    }

    private fun buildCacheKey(title: String, artist: String, localAudioFile: File?): String {
        if (localAudioFile != null) {
            return "file:${localAudioFile.absolutePath}"
        }
        return buildMetaKey(title, artist)
    }

    private fun buildMetaKey(title: String, artist: String): String {
        return "meta:${artist.lowercase(Locale.US).trim()}|${title.lowercase(Locale.US).trim()}"
    }

    private fun getPersistentFile(title: String, artist: String): File? {
        val dir = persistentDir ?: return null
        val safeArtist = artist.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "_").take(32)
        val safeTitle = title.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "_").take(48)
        return File(dir, "${safeArtist}_${safeTitle}.lrc")
    }

    private fun attachSourceMetadataHeader(lrcText: String, sourceLabel: String): String {
        val cleaned = stripSourceMetadataHeader(lrcText)
        return "$SOURCE_HEADER_PREFIX$sourceLabel]\n$cleaned"
    }

    private fun extractSourceFromLrcText(rawText: String): String? {
        val firstLine = rawText.lineSequence().firstOrNull()?.trim() ?: return null
        if (firstLine.startsWith(SOURCE_HEADER_PREFIX) && firstLine.endsWith("]")) {
            return firstLine.removePrefix(SOURCE_HEADER_PREFIX).removeSuffix("]").trim().takeIf { it.isNotEmpty() }
        }
        return null
    }

    fun stripSourceMetadataHeader(rawText: String): String {
        val lines = rawText.lines()
        if (lines.isNotEmpty() && lines.first().trim().startsWith(SOURCE_HEADER_PREFIX)) {
            return lines.drop(1).joinToString("\n").trim()
        }
        return rawText.trim()
    }

    fun getLastResolvedSourceLabel(title: String, artist: String, localAudioFile: File? = null): String? {
        val fileKey = buildCacheKey(title, artist, localAudioFile)
        val metaKey = buildMetaKey(title, artist)
        return sourceMemoryCache.get(fileKey) ?: sourceMemoryCache.get(metaKey)
    }

    fun clearCachedLyricsForTrack(title: String, artist: String, localAudioFile: File? = null) {
        val fileKey = buildCacheKey(title, artist, localAudioFile)
        val metaKey = buildMetaKey(title, artist)
        rawLrcMemoryCache.remove(fileKey)
        rawLrcMemoryCache.remove(metaKey)
        sourceMemoryCache.remove(fileKey)
        sourceMemoryCache.remove(metaKey)
        runCatching {
            if (localAudioFile != null && localAudioFile.exists()) {
                val sidecarLrc = File(localAudioFile.parentFile, "${localAudioFile.nameWithoutExtension}.lrc")
                if (sidecarLrc.exists()) sidecarLrc.delete()
            }
        }
        runCatching {
            val diskFile = getPersistentFile(title, artist)
            if (diskFile != null && diskFile.exists()) diskFile.delete()
        }
    }

    fun saveCustomLyrics(
        title: String,
        artist: String,
        lrcText: String,
        localAudioFile: File? = null,
        sourceLabel: String = "Yerel .LRC"
    ) {
        val cleanBody = stripSourceMetadataHeader(lrcText)
        if (cleanBody.isBlank()) return
        val fileKey = buildCacheKey(title, artist, localAudioFile)
        val metaKey = buildMetaKey(title, artist)

        rawLrcMemoryCache.put(fileKey, cleanBody)
        rawLrcMemoryCache.put(metaKey, cleanBody)
        sourceMemoryCache.put(fileKey, sourceLabel)
        sourceMemoryCache.put(metaKey, sourceLabel)

        val payloadWithHeader = attachSourceMetadataHeader(cleanBody, sourceLabel)

        // 1. Write sidecar .lrc beside local audio file
        if (localAudioFile != null && localAudioFile.exists()) {
            try {
                val sidecarLrc = File(localAudioFile.parentFile, "${localAudioFile.nameWithoutExtension}.lrc")
                sidecarLrc.writeText(payloadWithHeader, Charsets.UTF_8)
            } catch (_: Exception) {
            }
        }

        // 2. Write to persistent app LyricsCache directory
        try {
            val diskFile = getPersistentFile(title, artist)
            diskFile?.writeText(payloadWithHeader, Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    fun getCachedRawLyrics(title: String, artist: String, localAudioFile: File? = null): String? {
        val fileKey = buildCacheKey(title, artist, localAudioFile)
        val metaKey = buildMetaKey(title, artist)

        val memFile = rawLrcMemoryCache.get(fileKey)
        if (!memFile.isNullOrBlank()) return memFile

        val memMeta = rawLrcMemoryCache.get(metaKey)
        if (!memMeta.isNullOrBlank()) return memMeta

        // Check sidecar file
        if (localAudioFile != null && localAudioFile.exists()) {
            val sidecarLrc = File(localAudioFile.parentFile, "${localAudioFile.nameWithoutExtension}.lrc")
            if (sidecarLrc.exists() && sidecarLrc.length() > 0L) {
                val diskText = try {
                    sidecarLrc.readText(Charsets.UTF_8)
                } catch (_: Exception) {
                    null
                }
                if (!diskText.isNullOrBlank()) {
                    val detectedSource = extractSourceFromLrcText(diskText) ?: "Yerel .LRC Dosyası"
                    val stripped = stripSourceMetadataHeader(diskText)
                    if (stripped.isNotBlank()) {
                        rawLrcMemoryCache.put(fileKey, stripped)
                        rawLrcMemoryCache.put(metaKey, stripped)
                        sourceMemoryCache.put(fileKey, detectedSource)
                        sourceMemoryCache.put(metaKey, detectedSource)
                        return stripped
                    }
                }
            }
        }

        // Check persistent lyrics cache directory
        val diskFile = getPersistentFile(title, artist)
        if (diskFile != null && diskFile.exists() && diskFile.length() > 0L) {
            val persistentText = try {
                diskFile.readText(Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
            if (!persistentText.isNullOrBlank()) {
                val detectedSource = extractSourceFromLrcText(persistentText) ?: "Önbellek (.LRC)"
                val stripped = stripSourceMetadataHeader(persistentText)
                if (stripped.isNotBlank()) {
                    rawLrcMemoryCache.put(fileKey, stripped)
                    rawLrcMemoryCache.put(metaKey, stripped)
                    sourceMemoryCache.put(fileKey, detectedSource)
                    sourceMemoryCache.put(metaKey, detectedSource)
                    return stripped
                }
            }
        }

        return null
    }

    suspend fun fetchSyncedLyrics(
        title: String,
        artist: String,
        durationSeconds: Long,
        localAudioFile: File? = null,
        orderedSources: List<LyricsSource>? = null,
        forceNetworkRefresh: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        fetchSyncedLyricsWithSource(
            title = title,
            artist = artist,
            durationSeconds = durationSeconds,
            localAudioFile = localAudioFile,
            orderedSources = orderedSources,
            forceNetworkRefresh = forceNetworkRefresh
        )?.lrcContent
    }

    suspend fun fetchSyncedLyricsWithSource(
        title: String,
        artist: String,
        durationSeconds: Long,
        localAudioFile: File? = null,
        orderedSources: List<LyricsSource>? = null,
        forceNetworkRefresh: Boolean = false
    ): LyricsFetchResult? = withContext(Dispatchers.IO) {
        if (forceNetworkRefresh) {
            clearCachedLyricsForTrack(title, artist, localAudioFile)
        } else {
            val cachedDiskOrMem = getCachedRawLyrics(title, artist, localAudioFile)
            if (!cachedDiskOrMem.isNullOrBlank()) {
                val cachedSource = getLastResolvedSourceLabel(title, artist, localAudioFile) ?: "Yerel .LRC"
                return@withContext LyricsFetchResult(
                    lrcContent = cachedDiskOrMem,
                    sourceLabel = cachedSource
                )
            }
        }

        val cleanTitle = title
            .replace(Regex("\\((?i)(official|video|audio|lyrics|hd|4k|visualizer|mv|remastered|topic).*?\\)"), "")
            .replace(Regex("\\[(?i)(official|video|audio|lyrics|hd|4k|visualizer|mv|remastered|topic).*?]"), "")
            .replace(Regex("(?i)\\b(official\\s+music\\s+video|official\\s+video|official\\s+audio|lyric\\s+video)\\b"), "")
            .trim()
        val cleanArtist = artist
            .removeSuffix(" - Topic")
            .removeSuffix("VEVO")
            .trim()

        if (cleanTitle.isBlank()) return@withContext null

        val activePriorityList = orderedSources?.takeIf { it.isNotEmpty() }
            ?: appContext?.let { DownloadPreferencesManager(it).getOrderedActiveLyricsSources() }
            ?: LyricsSource.DEFAULT_ORDER

        for (source in activePriorityList) {
            val fetchedLrc = try {
                when (source) {
                    LyricsSource.LRCLIB -> queryLrclib(cleanTitle, cleanArtist, durationSeconds)
                    LyricsSource.YOUTUBE_MUSIC -> queryYoutubeMusicTimedText(cleanTitle, cleanArtist, durationSeconds, localAudioFile)
                    LyricsSource.NETEASE_LRC -> queryNetEaseLrc(cleanTitle, cleanArtist, durationSeconds)
                    LyricsSource.KUGOU_LRC -> queryKuGouLrc(cleanTitle, cleanArtist, durationSeconds)
                    LyricsSource.LYRICS_OVH_GENIUS -> queryLyricsOvhAndGenius(cleanTitle, cleanArtist, durationSeconds)
                }
            } catch (_: Throwable) {
                null
            }

            if (!fetchedLrc.isNullOrBlank()) {
                saveCustomLyrics(
                    title = title,
                    artist = artist,
                    lrcText = fetchedLrc,
                    localAudioFile = localAudioFile,
                    sourceLabel = source.shortBadge
                )
                return@withContext LyricsFetchResult(
                    lrcContent = stripSourceMetadataHeader(fetchedLrc),
                    sourceLabel = source.shortBadge
                )
            }
        }

        null
    }

    // 1. LRCLIB (Senkronize LRC Veritabanı)
    private fun queryLrclib(title: String, artist: String, durationSeconds: Long): String? {
        return try {
            val encodedQuery = URLEncoder.encode("$artist $title".trim(), "UTF-8")
            val endpoint = URL("https://lrclib.net/api/search?q=$encodedQuery")
            val body = executeHttpGet(endpoint) ?: return null

            val arr = JSONArray(body)
            var bestSynced: String? = null
            var bestPlainAsLrc: String? = null
            var smallestDurationDiff = Long.MAX_VALUE

            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val synced = item.optString("syncedLyrics", "").trim()
                val plain = item.optString("plainLyrics", "").trim()
                val itemDuration = item.optDouble("duration", 0.0).toLong()
                val diff = if (durationSeconds > 0L && itemDuration > 0L) {
                    kotlin.math.abs(durationSeconds - itemDuration)
                } else {
                    0L
                }

                if (synced.isNotEmpty() && diff < smallestDurationDiff) {
                    smallestDurationDiff = diff
                    bestSynced = synced
                } else if (bestPlainAsLrc == null && plain.isNotEmpty()) {
                    bestPlainAsLrc = convertPlainLyricsToTimedLrc(plain, durationSeconds)
                }
            }

            bestSynced ?: bestPlainAsLrc
        } catch (_: Exception) {
            null
        }
    }

    // 2. YouTube Music / Altyazı (TimedText & Piped/Invidious Caption LRC)
    private fun queryYoutubeMusicTimedText(
        title: String,
        artist: String,
        durationSeconds: Long,
        localAudioFile: File?
    ): String? {
        return try {
            // Check if yt-dlp wrote any .vtt / .srv3 / .ttml subtitle file next to the audio file first
            if (localAudioFile != null && localAudioFile.parentFile?.exists() == true) {
                val baseName = localAudioFile.nameWithoutExtension
                val subFile = localAudioFile.parentFile?.listFiles()?.firstOrNull { candidate ->
                    candidate.name.startsWith(baseName) &&
                        (candidate.extension.equals("vtt", ignoreCase = true) ||
                            candidate.extension.equals("ttml", ignoreCase = true) ||
                            candidate.extension.equals("srv3", ignoreCase = true))
                }
                if (subFile != null && subFile.length() > 0L) {
                    val converted = convertVttOrXmlToLrc(subFile.readText(Charsets.UTF_8), durationSeconds)
                    if (!converted.isNullOrBlank()) return converted
                }
            }

            // Query Piped YouTube Music search endpoint to resolve videoId & captions/timedtext
            val query = URLEncoder.encode("$artist $title".trim(), "UTF-8")
            val pipedInstances = listOf(
                "https://pipedapi.kavin.rocks",
                "https://pipedapi.tokhmi.xyz",
                "https://pipedapi.moomoo.me"
            )

            var videoId: String? = null
            for (instance in pipedInstances) {
                val searchBody = executeHttpGet(
                    URL("$instance/search?q=$query&filter=music_songs"),
                    timeoutMs = 4_500
                ) ?: continue
                val items = JSONObject(searchBody).optJSONArray("items") ?: continue
                if (items.length() > 0) {
                    val urlPath = items.optJSONObject(0)?.optString("url", "") ?: ""
                    val extracted = urlPath.substringAfter("v=", "").substringBefore("&").trim()
                    if (extracted.length >= 8) {
                        videoId = extracted
                        break
                    }
                }
            }

            if (!videoId.isNullOrBlank()) {
                // Attempt direct YouTube TimedText XML in common music subtitle languages
                val langCandidates = listOf("en", "tr", "ja", "ko", "es", "de")
                for (lang in langCandidates) {
                    val timedUrl = URL("https://www.youtube.com/api/timedtext?v=$videoId&lang=$lang&fmt=srv1")
                    val xml = executeHttpGet(timedUrl, timeoutMs = 4_000)
                    if (!xml.isNullOrBlank() && xml.contains("<text")) {
                        val parsedLrc = parseYoutubeTimedTextXml(xml)
                        if (!parsedLrc.isNullOrBlank()) return parsedLrc
                    }
                }

                // Fallback: Check Piped streams endpoint subtitles list
                for (instance in pipedInstances) {
                    val streamBody = executeHttpGet(URL("$instance/streams/$videoId"), timeoutMs = 4_500) ?: continue
                    val subtitles = JSONObject(streamBody).optJSONArray("subtitles") ?: continue
                    for (i in 0 until subtitles.length()) {
                        val subObj = subtitles.optJSONObject(i) ?: continue
                        val subUrl = subObj.optString("url", "")
                        if (subUrl.isNotBlank()) {
                            val vttBody = executeHttpGet(URL(subUrl), timeoutMs = 4_500)
                            if (!vttBody.isNullOrBlank()) {
                                val lrc = convertVttOrXmlToLrc(vttBody, durationSeconds)
                                if (!lrc.isNullOrBlank()) return lrc
                            }
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    // 3. NetEase Cloud Music (Senkronize LRC)
    private fun queryNetEaseLrc(title: String, artist: String, durationSeconds: Long): String? {
        return try {
            val encodedQuery = URLEncoder.encode("$artist $title".trim(), "UTF-8")
            val searchUrl = URL("https://music.163.com/api/search/get/web?s=$encodedQuery&type=1&offset=0&total=true&limit=5")
            val headers = mapOf(
                "Referer" to "https://music.163.com",
                "Origin" to "https://music.163.com"
            )
            val searchJson = executeHttpGet(searchUrl, extraHeaders = headers, timeoutMs = 5_500) ?: return null
            val songs = JSONObject(searchJson)
                .optJSONObject("result")
                ?.optJSONArray("songs") ?: return null

            var chosenSongId: Long = -1L
            var bestDiff = Long.MAX_VALUE
            for (i in 0 until songs.length()) {
                val song = songs.optJSONObject(i) ?: continue
                val id = song.optLong("id", -1L)
                if (id <= 0L) continue
                val songDurationSec = (song.optLong("duration", 0L) / 1000L)
                val diff = if (durationSeconds > 0L && songDurationSec > 0L) {
                    kotlin.math.abs(durationSeconds - songDurationSec)
                } else {
                    0L
                }
                if (diff < bestDiff) {
                    bestDiff = diff
                    chosenSongId = id
                }
            }

            if (chosenSongId <= 0L) return null

            val lyricUrl = URL("https://music.163.com/api/song/lyric?os=pc&id=$chosenSongId&lv=-1&kv=-1&tv=-1")
            val lyricJson = executeHttpGet(lyricUrl, extraHeaders = headers, timeoutMs = 5_500) ?: return null
            val root = JSONObject(lyricJson)
            val lrcText = root.optJSONObject("lrc")?.optString("lyric", "")?.trim().orEmpty()
            if (lrcText.isNotBlank()) {
                return if (lrcText.contains(Regex("\\[\\d{1,2}:\\d{2}"))) {
                    lrcText
                } else {
                    convertPlainLyricsToTimedLrc(lrcText, durationSeconds)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    // 4. KuGou LRC Veritabanı (Senkronize LRC)
    private fun queryKuGouLrc(title: String, artist: String, durationSeconds: Long): String? {
        return try {
            val encodedKeyword = URLEncoder.encode("$artist $title".trim(), "UTF-8")
            val durationMs = (durationSeconds.takeIf { it > 0L } ?: 200L) * 1000L
            val searchUrl = URL(
                "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=$encodedKeyword&duration=$durationMs&hash="
            )
            val searchBody = executeHttpGet(searchUrl, timeoutMs = 5_500) ?: return null
            val candidates = JSONObject(searchBody).optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null

            val firstCandidate = candidates.optJSONObject(0) ?: return null
            val candidateId = firstCandidate.optString("id", "")
            val accessKey = firstCandidate.optString("accesskey", "")
            if (candidateId.isBlank() || accessKey.isBlank()) return null

            val downloadUrl = URL(
                "https://lyrics.kugou.com/download?ver=1&client=pc&id=$candidateId&accesskey=$accessKey&fmt=lrc&charset=utf8"
            )
            val downloadBody = executeHttpGet(downloadUrl, timeoutMs = 5_500) ?: return null
            val base64Content = JSONObject(downloadBody).optString("content", "").trim()
            if (base64Content.isBlank()) return null

            val decodedBytes = Base64.decode(base64Content, Base64.DEFAULT)
            val decodedLrc = String(decodedBytes, Charsets.UTF_8).trim()
            if (decodedLrc.isNotBlank()) {
                return if (decodedLrc.contains(Regex("\\[\\d{1,2}:\\d{2}"))) {
                    decodedLrc
                } else {
                    convertPlainLyricsToTimedLrc(decodedLrc, durationSeconds)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    // 5. Lyrics.ovh & Genius / Lyrist (Düz Metin -> Otomatik Zamanlı LRC)
    private fun queryLyricsOvhAndGenius(title: String, artist: String, durationSeconds: Long): String? {
        // 5A: Try Lyrics.ovh first
        try {
            if (artist.isNotBlank() && title.isNotBlank()) {
                val encArtist = URLEncoder.encode(artist, "UTF-8").replace("+", "%20")
                val encTitle = URLEncoder.encode(title, "UTF-8").replace("+", "%20")
                val ovhUrl = URL("https://api.lyrics.ovh/v1/$encArtist/$encTitle")
                val ovhBody = executeHttpGet(ovhUrl, timeoutMs = 5_000)
                if (!ovhBody.isNullOrBlank()) {
                    val plain = JSONObject(ovhBody).optString("lyrics", "").trim()
                    if (plain.isNotBlank()) {
                        return convertPlainLyricsToTimedLrc(plain, durationSeconds)
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 5B: Fallback to Lyrist / Genius proxy archive
        return try {
            val encTitle = URLEncoder.encode(title, "UTF-8")
            val encArtist = URLEncoder.encode(artist, "UTF-8")
            val path = if (artist.isNotBlank()) "$encTitle/$encArtist" else encTitle
            val lyristUrl = URL("https://lyrist.vercel.app/api/$path")
            val body = executeHttpGet(lyristUrl, timeoutMs = 5_500) ?: return null
            val plain = JSONObject(body).optString("lyrics", "").trim()
            if (plain.isNotBlank()) {
                convertPlainLyricsToTimedLrc(plain, durationSeconds)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun executeHttpGet(
        url: URL,
        extraHeaders: Map<String, String> = emptyMap(),
        timeoutMs: Int = 6_000
    ): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; SakuraMusic/2.0) AppleWebKit/537.36"
                )
                setRequestProperty("Accept", "application/json, text/plain, */*")
                extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                return null
            }
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun parseYoutubeTimedTextXml(xml: String): String? {
        return try {
            val regex = Regex("""<text[^>]*start="([\d.]+)"[^>]*>(.*?)</text>""", RegexOption.DOT_MATCHES_ALL)
            val matches = regex.findAll(xml).toList()
            if (matches.isEmpty()) return null
            val sb = StringBuilder()
            for (match in matches) {
                val startSec = match.groupValues[1].toDoubleOrNull() ?: continue
                val rawContent = match.groupValues[2]
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&apos;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("\n", " ")
                    .trim()
                if (rawContent.isEmpty()) continue
                val totalMs = (startSec * 1000.0).toLong().coerceAtLeast(0L)
                val mins = totalMs / 60_000L
                val secs = (totalMs % 60_000L) / 1000L
                val hundredths = (totalMs % 1000L) / 10L
                sb.append(String.format(Locale.US, "[%02d:%02d.%02d]%s\n", mins, secs, hundredths, rawContent))
            }
            sb.toString().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun convertVttOrXmlToLrc(rawSub: String, durationSeconds: Long): String? {
        if (rawSub.contains("<text")) {
            return parseYoutubeTimedTextXml(rawSub)
        }
        val lines = rawSub.lines()
        val timeRegex = Regex("""(?:(\d{2}):)?(\d{2}):(\d{2})[.,](\d{3})\s*-->""")
        val sb = StringBuilder()
        var currentStamp: String? = null
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.equals("WEBVTT", ignoreCase = true)) continue
            val m = timeRegex.find(trimmed)
            if (m != null) {
                val hours = m.groupValues[1].toLongOrNull() ?: 0L
                val mins = (m.groupValues[2].toLongOrNull() ?: 0L) + (hours * 60L)
                val secs = m.groupValues[3].toLongOrNull() ?: 0L
                val millis = m.groupValues[4].toLongOrNull() ?: 0L
                val hundredths = millis / 10L
                currentStamp = String.format(Locale.US, "[%02d:%02d.%02d]", mins, secs, hundredths)
            } else if (currentStamp != null && !trimmed.all { it.isDigit() }) {
                val cleanLine = trimmed.replace(Regex("<[^>]+>"), "").trim()
                if (cleanLine.isNotEmpty()) {
                    sb.append(currentStamp).append(cleanLine).append("\n")
                    currentStamp = null
                }
            }
        }
        val result = sb.toString().trim()
        if (result.isNotBlank()) return result
        return null
    }

    private fun convertPlainLyricsToTimedLrc(plain: String, durationSeconds: Long): String {
        val lines = plain
            .replace("\r\n", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("Paroles de la chanson", ignoreCase = true) }
        if (lines.isEmpty()) return ""
        val totalMs = (durationSeconds.takeIf { it > 15L } ?: 180L) * 1000L
        val stepMs = (totalMs / (lines.size + 1)).coerceAtLeast(2_500L)
        val sb = StringBuilder()
        lines.forEachIndexed { idx, line ->
            val timeMs = idx * stepMs
            val mins = (timeMs / 60_000L)
            val secs = (timeMs % 60_000L) / 1000L
            val hundredths = (timeMs % 1000L) / 10L
            sb.append(String.format(Locale.US, "[%02d:%02d.%02d]%s\n", mins, secs, hundredths, line))
        }
        return sb.toString()
    }
}
