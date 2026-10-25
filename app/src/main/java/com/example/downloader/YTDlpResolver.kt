package com.example.downloader

import android.content.Context
import android.util.Log
import com.example.SakuraMusicApplication
import com.example.model.CookieItem
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class MediaSourcePlatform(val displayName: String) {
    YOUTUBE("YouTube / YouTube Music"),
    SOUNDCLOUD("SoundCloud (yt-dlp)"),
    VIMEO("Vimeo (yt-dlp)"),
    DIRECT_MEDIA("Doğrudan Medya Bağlantısı"),
    GENERIC_YTDLP("yt-dlp Destekli Platform"),
    UNSUPPORTED("Desteklenmeyen Kaynak")
}

data class PlatformSupportStatus(
    val platform: MediaSourcePlatform,
    val isSupported: Boolean,
    val reasonMessage: String
)

class YTDlpResolver(private val context: Context) {
    private val appContext = context.applicationContext

    fun isEngineReady(): Boolean {
        return SakuraMusicApplication.ensureYoutubeDLInitialized(appContext)
    }

    fun evaluateUrlSupport(rawInput: String): PlatformSupportStatus {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return PlatformSupportStatus(
                platform = MediaSourcePlatform.UNSUPPORTED,
                isSupported = false,
                reasonMessage = "Lütfen geçerli bir URL veya arama terimi girin."
            )
        }

        val ytdlpReady = isEngineReady()

        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return PlatformSupportStatus(
                platform = MediaSourcePlatform.YOUTUBE,
                isSupported = true,
                reasonMessage = if (ytdlpReady) {
                    "YouTube araması (yt-dlp + InnerTube aktif)"
                } else {
                    "YouTube araması (InnerTube aktif)"
                }
            )
        }

        return when {
            isYouTubeUrl(trimmed) -> PlatformSupportStatus(
                platform = MediaSourcePlatform.YOUTUBE,
                isSupported = true,
                reasonMessage = if (ytdlpReady) {
                    "YouTube / YouTube Music bağlantısı (yt-dlp + InnerTube destekli)"
                } else {
                    "YouTube / YouTube Music bağlantısı (InnerTube destekli)"
                }
            )

            isSoundCloudUrl(trimmed) -> {
                if (ytdlpReady) {
                    PlatformSupportStatus(
                        platform = MediaSourcePlatform.SOUNDCLOUD,
                        isSupported = true,
                        reasonMessage = "SoundCloud bağlantısı (ytdlnis / yt-dlp extractor ile destekleniyor)"
                    )
                } else {
                    PlatformSupportStatus(
                        platform = MediaSourcePlatform.SOUNDCLOUD,
                        isSupported = false,
                        reasonMessage = "SoundCloud bağlantıları yalnızca yerel yt-dlp motoru aktifken desteklenir (${SakuraMusicApplication.youtubeDLInitError ?: "yt-dlp başlatılamadı"})."
                    )
                }
            }

            isVimeoUrl(trimmed) -> {
                if (ytdlpReady) {
                    PlatformSupportStatus(
                        platform = MediaSourcePlatform.VIMEO,
                        isSupported = true,
                        reasonMessage = "Vimeo bağlantısı (ytdlnis / yt-dlp extractor ile destekleniyor)"
                    )
                } else {
                    PlatformSupportStatus(
                        platform = MediaSourcePlatform.VIMEO,
                        isSupported = false,
                        reasonMessage = "Vimeo bağlantıları yalnızca yerel yt-dlp motoru aktifken desteklenir (${SakuraMusicApplication.youtubeDLInitError ?: "yt-dlp başlatılamadı"})."
                    )
                }
            }

            isLikelyDirectMediaExtension(trimmed) -> PlatformSupportStatus(
                platform = MediaSourcePlatform.DIRECT_MEDIA,
                isSupported = true,
                reasonMessage = "Doğrudan medya dosyası bağlantısı (HTTP akış doğrulaması ve çoklu parça indirme destekli)"
            )

            else -> {
                if (ytdlpReady) {
                    PlatformSupportStatus(
                        platform = MediaSourcePlatform.GENERIC_YTDLP,
                        isSupported = true,
                        reasonMessage = "Genel bağlantı (yt-dlp / doğrudan medya çözümleyici ile denenecek)"
                    )
                } else {
                    PlatformSupportStatus(
                        platform = MediaSourcePlatform.DIRECT_MEDIA,
                        isSupported = true,
                        reasonMessage = "Doğrudan medya akışı kontrol edilecek (yt-dlp motoru devre dışı)"
                    )
                }
            }
        }
    }

    suspend fun extractSingleMetadata(
        url: String,
        cookies: List<CookieItem> = emptyList()
    ): Result<MediaTrackInfo> = withContext(Dispatchers.IO) {
        if (!isEngineReady()) {
            return@withContext Result.failure(
                IllegalStateException(
                    SakuraMusicApplication.youtubeDLInitError
                        ?: "yt-dlp motoru başlatılamadı."
                )
            )
        }

        return@withContext try {
            val activeCookies = NetscapeCookieHelper.resolveActiveCookies(appContext, cookies)
            val cookieFile = NetscapeCookieHelper.writeNetscapeCookieFile(appContext, activeCookies)

            val targetArg = if (url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true)
            ) {
                url
            } else {
                "ytsearch1:$url"
            }

            val streamedLines = Collections.synchronizedList(mutableListOf<String>())
            val request = YoutubeDLRequest(targetArg).apply {
                addOption("--dump-json")
                addOption("--no-playlist")
                addOption("--ignore-errors")
                addOption("--no-warnings")
                addOption("--ignore-config")
                addOption("--socket-timeout", "20")
                addOption("-f", "ba[ext=m4a]/ba[acodec=opus]/ba/b")
                if (cookieFile != null) {
                    addOption("--cookies", cookieFile.absolutePath)
                }
            }

            val response = try {
                YoutubeDL.getInstance().execute(request, null) { _, _, line ->
                    if (!line.isNullOrBlank()) {
                        streamedLines.add(line.trim())
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "yt-dlp execute warning for $url: ${t.message}")
                null
            }

            val combinedLines = buildList {
                if (response != null) {
                    addAll(response.out.orEmpty().lineSequence().map { it.trim() })
                }
                addAll(streamedLines)
            }

            val firstJsonLine = combinedLines.firstOrNull { it.startsWith("{") && it.endsWith("}") }

            if (firstJsonLine.isNullOrBlank()) {
                val errOut = response?.err.orEmpty().trim()
                return@withContext Result.failure(
                    IllegalStateException(
                        errOut.ifBlank { "yt-dlp medya bilgisi döndürmedi." }
                    )
                )
            }

            val json = JSONObject(firstJsonLine)
            val trackInfo = parseTrackInfoFromDumpJson(json, fallbackSourceUrl = url)
            Result.success(trackInfo)
        } catch (t: Throwable) {
            Log.w(TAG, "yt-dlp metadata extraction failed for $url: ${t.message}")
            Result.failure(if (t is Exception) t else IllegalStateException(t.message ?: "yt-dlp hatası", t))
        }
    }

    suspend fun extractPlaylistMetadata(
        playlistUrl: String,
        cookies: List<CookieItem> = emptyList(),
        maxItems: Int = 50
    ): Result<PlaylistResolutionResult> = withContext(Dispatchers.IO) {
        if (!isEngineReady()) {
            return@withContext Result.failure(
                IllegalStateException(
                    SakuraMusicApplication.youtubeDLInitError
                        ?: "yt-dlp motoru başlatılamadı."
                )
            )
        }

        val activeCookies = NetscapeCookieHelper.resolveActiveCookies(appContext, cookies)
        val cookieFile = NetscapeCookieHelper.writeNetscapeCookieFile(appContext, activeCookies)

        val lowerUrl = playlistUrl.lowercase(Locale.US)
        val isStrictMusicLiked = lowerUrl.contains("list=lm") ||
            lowerUrl.contains("vllm") ||
            lowerUrl.contains("femusic_liked")

        val candidateUrls = buildList {
            if (isStrictMusicLiked) {
                val musicLm = "https://music.youtube.com/playlist?list=LM"
                add(musicLm)
                if (playlistUrl !in this && !lowerUrl.contains("list=ll")) {
                    add(playlistUrl)
                }
            } else {
                add(playlistUrl)
                if (lowerUrl.contains("list=ll") || lowerUrl.contains("vlll")) {
                    val ytLl = "https://www.youtube.com/playlist?list=LL"
                    if (ytLl !in this) add(ytLl)
                }
            }
        }

        var lastError: Throwable? = null
        for (targetUrl in candidateUrls) {
            val singleAttempt = executePlaylistExtractionForUrl(
                targetUrl = targetUrl,
                cookieFile = cookieFile,
                maxItems = maxItems,
                isStrictMusicPlaylist = isStrictMusicLiked
            )
            if (singleAttempt.isSuccess) {
                return@withContext singleAttempt
            } else {
                lastError = singleAttempt.exceptionOrNull()
            }
        }

        Result.failure(
            lastError ?: IllegalStateException("ytdlnis çalma listesinde indirilebilir parça bulamadı.")
        )
    }

    private fun executePlaylistExtractionForUrl(
        targetUrl: String,
        cookieFile: java.io.File?,
        maxItems: Int,
        isStrictMusicPlaylist: Boolean = false
    ): Result<PlaylistResolutionResult> {
        return try {
            val streamedLines = Collections.synchronizedList(mutableListOf<String>())
            val request = YoutubeDLRequest(targetUrl).apply {
                addOption("--flat-playlist")
                addOption("--dump-single-json")
                addOption("--ignore-errors")
                addOption("--no-warnings")
                addOption("--ignore-config")
                addOption("--playlist-end", maxItems.coerceIn(1, 200).toString())
                addOption("--socket-timeout", "25")
                if (cookieFile != null && cookieFile.exists()) {
                    addOption("--cookies", cookieFile.absolutePath)
                }
            }

            val response = try {
                YoutubeDL.getInstance().execute(request, null) { _, _, line ->
                    if (!line.isNullOrBlank()) {
                        streamedLines.add(line.trim())
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "yt-dlp playlist partial exit for $targetUrl: ${t.message}")
                null
            }

            val allLines = buildList {
                if (response != null) {
                    addAll(response.out.orEmpty().lineSequence().map { it.trim() })
                }
                addAll(streamedLines)
            }.filter { it.startsWith("{") && it.endsWith("}") }

            if (allLines.isEmpty()) {
                return Result.failure(
                    IllegalStateException("ytdlnis çalma listesi JSON verisi döndürmedi.")
                )
            }

            val tracks = mutableListOf<MediaTrackInfo>()
            val seenIds = HashSet<String>()
            var resolvedPlaylistId = targetUrl.hashCode().toString()
            var resolvedPlaylistTitle = if (targetUrl.contains("list=LM", ignoreCase = true) ||
                targetUrl.contains("list=LL", ignoreCase = true)
            ) {
                "Beğenilen Şarkılar"
            } else {
                "Çalma Listesi"
            }
            var resolvedPlaylistThumbnail: String? = null

            for (line in allLines) {
                val obj = try {
                    JSONObject(line)
                } catch (_: Throwable) {
                    continue
                }

                val entries = obj.optJSONArray("entries")
                if (entries != null) {
                    val topId = obj.optString("id", "").ifBlank { obj.optString("playlist_id", "") }.trim()
                    if (topId.isNotBlank()) {
                        resolvedPlaylistId = topId
                    }
                    val topTitle = obj.optString("playlist_title", "")
                        .ifBlank { obj.optString("title", "") }
                        .ifBlank { obj.optString("playlist", "") }
                        .trim()
                    if (topTitle.isNotBlank()) {
                        resolvedPlaylistTitle = topTitle
                    }
                    if (resolvedPlaylistThumbnail.isNullOrBlank()) {
                        resolvedPlaylistThumbnail = extractBestThumbnailFromJson(obj)
                    }
                    val rootUploader = obj.optString("uploader", "Sakura Music")

                    for (i in 0 until entries.length()) {
                        val entry = entries.optJSONObject(i) ?: continue
                        if (resolvedPlaylistTitle == "Çalma Listesi") {
                            val entryPlTitle = entry.optString("playlist_title", "")
                                .ifBlank { entry.optString("playlist", "") }
                                .trim()
                            if (entryPlTitle.isNotBlank()) {
                                resolvedPlaylistTitle = entryPlTitle
                            }
                        }
                        parseFlatPlaylistEntry(
                            entry = entry,
                            index = tracks.size,
                            defaultUploader = rootUploader,
                            playlistTitle = resolvedPlaylistTitle,
                            fallbackPlaylistUrl = targetUrl,
                            isStrictMusicPlaylist = isStrictMusicPlaylist
                        )?.let { track ->
                            if (seenIds.add(track.id)) {
                                tracks.add(track)
                            }
                        }
                    }
                } else {
                    val entryPlId = obj.optString("playlist_id", "").trim()
                    if (entryPlId.isNotBlank()) {
                        resolvedPlaylistId = entryPlId
                    }
                    val entryPlTitle = obj.optString("playlist_title", "")
                        .ifBlank { obj.optString("playlist", "") }
                        .trim()
                    if (entryPlTitle.isNotBlank()) {
                        resolvedPlaylistTitle = entryPlTitle
                    }
                    parseFlatPlaylistEntry(
                        entry = obj,
                        index = tracks.size,
                        defaultUploader = "Sakura Music",
                        playlistTitle = resolvedPlaylistTitle,
                        fallbackPlaylistUrl = targetUrl,
                        isStrictMusicPlaylist = isStrictMusicPlaylist
                    )?.let { track ->
                        if (seenIds.add(track.id)) {
                            tracks.add(track)
                        }
                    }
                }
            }

            if (tracks.isEmpty()) {
                Result.failure(IllegalStateException("Çalma listesinde indirilebilir parça bulunamadı."))
            } else {
                val finalCover = resolvedPlaylistThumbnail ?: tracks.firstOrNull()?.thumbnailUrl
                val stampedTracks = tracks.map { track ->
                    track.copy(
                        album = resolvedPlaylistTitle,
                        playlistId = resolvedPlaylistId,
                        playlistTitle = resolvedPlaylistTitle,
                        playlistCoverUrl = finalCover
                    )
                }
                Result.success(
                    PlaylistResolutionResult(
                        playlistId = resolvedPlaylistId,
                        title = resolvedPlaylistTitle,
                        tracks = stampedTracks,
                        thumbnailUrl = finalCover
                    )
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "yt-dlp playlist extraction failed for $targetUrl: ${t.message}")
            Result.failure(if (t is Exception) t else IllegalStateException(t.message ?: "yt-dlp playlist hatası", t))
        }
    }

    private fun parseFlatPlaylistEntry(
        entry: JSONObject,
        index: Int,
        defaultUploader: String,
        playlistTitle: String,
        fallbackPlaylistUrl: String,
        isStrictMusicPlaylist: Boolean = false
    ): MediaTrackInfo? {
        val id = entry.optString("id", "").trim()
        if (id.isEmpty() || id == "LM" || id == "LL") return null

        val rawTitle = entry.optString("track", "")
            .ifBlank { entry.optString("title", "") }
            .trim()
        if (rawTitle == "[Deleted video]" || rawTitle == "[Private video]") return null

        val title = rawTitle.ifBlank { "Parça ${index + 1}" }
        val uploader = entry.optString("artist", "")
            .ifBlank { entry.optString("creator", "") }
            .ifBlank { entry.optString("uploader", "") }
            .ifBlank { entry.optString("channel", "") }
            .ifBlank { defaultUploader.ifBlank { "Sakura Music" } }
        val duration = entry.optDouble("duration", 0.0).toLong().coerceAtLeast(0L)

        if (isStrictMusicPlaylist) {
            if (!StrictMusicTrackValidator.isStrictMusicTrack(
                    title = title,
                    artist = uploader,
                    durationSeconds = duration,
                    musicVideoType = null,
                    enforceStrictFilter = true
                )
            ) {
                Log.d(TAG, "Filtered non-music entry from YT Music LM: $title ($duration s)")
                return null
            }
        }

        val entryUrl = if (id.length == 11) {
            "https://www.youtube.com/watch?v=$id"
        } else {
            entry.optString("url", "")
                .ifBlank { entry.optString("webpage_url", "") }
                .let { raw ->
                    if (raw.startsWith("http")) raw.substringBefore("&list=").substringBefore("?list=")
                    else fallbackPlaylistUrl
                }
        }

        val thumb = extractBestThumbnailFromJson(entry)
            ?: if (id.length == 11) "https://i.ytimg.com/vi/$id/hqdefault.jpg" else null

        return MediaTrackInfo(
            id = id,
            title = title,
            artist = uploader,
            album = playlistTitle,
            durationSeconds = duration,
            thumbnailUrl = thumb,
            sourceUrl = entryUrl,
            streamUrl = entryUrl,
            mimeType = "audio/mp4",
            bitrateKbps = 160,
            playlistTitle = playlistTitle
        )
    }

    suspend fun probeDirectMediaUrl(
        url: String,
        cookies: List<CookieItem> = emptyList()
    ): Result<MediaTrackInfo> = withContext(Dispatchers.IO) {
        return@withContext try {
            val parsedUrl = URL(url)
            val cookieHeader = NetscapeCookieHelper.buildCookieHeaderString(cookies, appContext)

            var contentType = ""
            var contentLength = -1L
            var finalUrl = url

            val headConn = (parsedUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                instanceFollowRedirects = true
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", USER_AGENT)
                if (!cookieHeader.isNullOrBlank()) {
                    setRequestProperty("Cookie", cookieHeader)
                }
            }

            try {
                val code = headConn.responseCode
                if (code in 200..399) {
                    contentType = headConn.contentType.orEmpty().lowercase(Locale.US)
                    contentLength = headConn.contentLengthLong
                    finalUrl = headConn.url?.toString() ?: url
                }
            } catch (_: Throwable) {
            } finally {
                headConn.disconnect()
            }

            if (contentType.isBlank() || (!contentType.startsWith("audio/") && !contentType.startsWith("video/") && !contentType.contains("octet-stream"))) {
                val getConn = (URL(finalUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Range", "bytes=0-1023")
                    if (!cookieHeader.isNullOrBlank()) {
                        setRequestProperty("Cookie", cookieHeader)
                    }
                }
                try {
                    val code = getConn.responseCode
                    if (code !in 200..299) {
                        return@withContext Result.failure(
                            IllegalStateException("Doğrudan medya bağlantısı HTTP $code yanıtı verdi.")
                        )
                    }
                    contentType = getConn.contentType.orEmpty().lowercase(Locale.US)
                    val contentRange = getConn.getHeaderField("Content-Range")
                    if (!contentRange.isNullOrBlank() && contentRange.contains("/")) {
                        contentLength = contentRange.substringAfterLast("/").trim().toLongOrNull() ?: -1L
                    } else if (contentLength <= 0L) {
                        contentLength = getConn.contentLengthLong
                    }
                    finalUrl = getConn.url?.toString() ?: finalUrl
                } finally {
                    getConn.disconnect()
                }
            }

            val isSupportedMediaMime = contentType.startsWith("audio/") ||
                contentType.startsWith("video/") ||
                (contentType.contains("application/octet-stream") && isLikelyDirectMediaExtension(finalUrl)) ||
                (contentType.contains("application/ogg"))

            if (!isSupportedMediaMime && !isLikelyDirectMediaExtension(finalUrl)) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Bu bağlantı doğrudan bir ses/video akışı değil (Content-Type: ${contentType.ifBlank { "bilinmiyor" }})."
                    )
                )
            }

            val cleanPath = URL(finalUrl).path.substringAfterLast('/').substringBefore('?')
            val rawName = java.net.URLDecoder.decode(cleanPath, "UTF-8")
                .substringBeforeLast('.')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .ifBlank { "Doğrudan Medya Akışı" }

            val resolvedMime = when {
                contentType.startsWith("audio/") || contentType.startsWith("video/") ->
                    contentType.substringBefore(';')
                finalUrl.lowercase(Locale.US).contains(".mp3") -> "audio/mpeg"
                finalUrl.lowercase(Locale.US).contains(".ogg") || finalUrl.lowercase(Locale.US).contains(".opus") -> "audio/ogg"
                finalUrl.lowercase(Locale.US).contains(".wav") -> "audio/wav"
                finalUrl.lowercase(Locale.US).contains(".flac") -> "audio/flac"
                finalUrl.lowercase(Locale.US).contains(".mp4") -> "video/mp4"
                else -> "audio/mp4"
            }

            Result.success(
                MediaTrackInfo(
                    id = "direct_${finalUrl.hashCode().toUInt().toString(16)}",
                    title = rawName,
                    artist = URL(finalUrl).host.removePrefix("www."),
                    album = "Doğrudan Medya",
                    durationSeconds = 0L,
                    thumbnailUrl = null,
                    sourceUrl = url,
                    streamUrl = finalUrl,
                    mimeType = resolvedMime,
                    bitrateKbps = 192,
                    contentLengthBytes = contentLength
                )
            )
        } catch (t: Throwable) {
            Result.failure(if (t is Exception) t else IllegalStateException(t.message ?: "Doğrudan medya hatası", t))
        }
    }

    private fun parseTrackInfoFromDumpJson(
        json: JSONObject,
        fallbackSourceUrl: String
    ): MediaTrackInfo {
        val id = json.optString("id", "").ifBlank {
            fallbackSourceUrl.hashCode().toUInt().toString(16)
        }
        val title = json.optString("track", "")
            .ifBlank { json.optString("title", "") }
            .ifBlank { "Bilinmeyen Parça" }
        val artist = json.optString("artist", "")
            .ifBlank { json.optString("creator", "") }
            .ifBlank { json.optString("uploader", "") }
            .ifBlank { json.optString("channel", "") }
            .ifBlank { json.optString("extractor_key", "Sakura Music") }
        val album = json.optString("album", "")
            .ifBlank { json.optString("playlist_title", "") }
            .ifBlank { "Sakura Music" }
        val durationSeconds = json.optDouble("duration", 0.0).toLong().coerceAtLeast(0L)
        val webpageUrl = json.optString("webpage_url", "")
            .ifBlank { json.optString("original_url", "") }
            .ifBlank { fallbackSourceUrl }

        var directStreamUrl = json.optString("url", "")
        var mimeType = "audio/mp4"
        var bitrateKbps = json.optDouble("abr", 0.0).toInt()
            .takeIf { it > 0 }
            ?: json.optDouble("tbr", 160.0).toInt().coerceAtLeast(128)
        var contentLengthBytes = json.optLong("filesize", -1L)
            .takeIf { it > 0L }
            ?: json.optLong("filesize_approx", -1L)

        val requestedDownloads = json.optJSONArray("requested_downloads")
        if (directStreamUrl.isBlank() && requestedDownloads != null && requestedDownloads.length() > 0) {
            val firstReq = requestedDownloads.optJSONObject(0)
            if (firstReq != null) {
                directStreamUrl = firstReq.optString("url", "")
                val ext = firstReq.optString("ext", "m4a")
                mimeType = extToMimeType(ext)
            }
        }

        if (directStreamUrl.isBlank()) {
            val formats = json.optJSONArray("formats")
            if (formats != null) {
                var bestAudioFormat: JSONObject? = null
                var bestAbr = -1.0
                for (i in 0 until formats.length()) {
                    val fmt = formats.optJSONObject(i) ?: continue
                    val fmtUrl = fmt.optString("url", "")
                    if (fmtUrl.isBlank()) continue
                    val acodec = fmt.optString("acodec", "none")
                    val vcodec = fmt.optString("vcodec", "none")
                    val protocol = fmt.optString("protocol", "")
                    if (acodec != "none" && !protocol.contains("m3u8")) {
                        val abr = fmt.optDouble("abr", fmt.optDouble("tbr", 0.0))
                        val bonus = if (vcodec == "none") 1000.0 else 0.0
                        if (abr + bonus > bestAbr) {
                            bestAbr = abr + bonus
                            bestAudioFormat = fmt
                        }
                    }
                }
                if (bestAudioFormat != null) {
                    directStreamUrl = bestAudioFormat.optString("url", "")
                    val ext = bestAudioFormat.optString("ext", "m4a")
                    mimeType = extToMimeType(ext)
                    bitrateKbps = bestAudioFormat.optDouble("abr", 160.0).toInt().coerceAtLeast(128)
                    val fSize = bestAudioFormat.optLong("filesize", -1L)
                    if (fSize > 0L) contentLengthBytes = fSize
                }
            }
        } else {
            val ext = json.optString("ext", "m4a")
            mimeType = extToMimeType(ext)
        }

        val thumbnailUrl = extractBestThumbnailFromJson(json)
            ?: if (isYouTubeUrl(webpageUrl) && id.length == 11) {
                "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            } else {
                null
            }

        return MediaTrackInfo(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = durationSeconds,
            thumbnailUrl = thumbnailUrl,
            sourceUrl = webpageUrl,
            streamUrl = directStreamUrl.ifBlank { webpageUrl },
            mimeType = mimeType,
            bitrateKbps = bitrateKbps,
            contentLengthBytes = contentLengthBytes
        )
    }

    private fun extractBestThumbnailFromJson(json: JSONObject): String? {
        val directThumb = json.optString("thumbnail", "").trim()
        if (directThumb.startsWith("http://") || directThumb.startsWith("https://")) {
            return directThumb
        }
        val thumbs = json.optJSONArray("thumbnails")
        if (thumbs != null && thumbs.length() > 0) {
            for (i in thumbs.length() - 1 downTo 0) {
                val item = thumbs.optJSONObject(i) ?: continue
                val url = item.optString("url", "").trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return url
                }
            }
        }
        return null
    }

    private fun extToMimeType(ext: String): String {
        return when (ext.lowercase(Locale.US)) {
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "opus", "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "mp4" -> "video/mp4"
            "webm" -> "audio/webm"
            else -> "audio/mp4"
        }
    }

    companion object {
        private const val TAG = "YTDlpResolver"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        fun isYouTubeUrl(url: String): Boolean {
            val lower = url.lowercase(Locale.US)
            return lower.contains("youtube.com/") ||
                lower.contains("youtu.be/") ||
                lower.contains("music.youtube.com/") ||
                lower.contains("youtube-nocookie.com/")
        }

        fun isSoundCloudUrl(url: String): Boolean {
            val lower = url.lowercase(Locale.US)
            return lower.contains("soundcloud.com/") || lower.contains("on.soundcloud.com/")
        }

        fun isVimeoUrl(url: String): Boolean {
            val lower = url.lowercase(Locale.US)
            return lower.contains("vimeo.com/") || lower.contains("player.vimeo.com/")
        }

        fun isLikelyDirectMediaExtension(url: String): Boolean {
            val clean = url.substringBefore('?').substringBefore('#').lowercase(Locale.US)
            return clean.endsWith(".mp3") ||
                clean.endsWith(".m4a") ||
                clean.endsWith(".aac") ||
                clean.endsWith(".opus") ||
                clean.endsWith(".ogg") ||
                clean.endsWith(".wav") ||
                clean.endsWith(".flac") ||
                clean.endsWith(".mp4") ||
                clean.endsWith(".webm") ||
                clean.endsWith(".mkv")
        }
    }
}
