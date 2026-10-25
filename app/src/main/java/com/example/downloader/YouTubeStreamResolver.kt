package com.example.downloader

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.example.model.CookieItem
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object StreamCache {
    private const val MAX_TRACK_ENTRIES = 250
    private const val MAX_PLAYLIST_ENTRIES = 50
    private const val CACHE_EXPIRY_MS = 60 * 60 * 1000L // 1 hour

    private data class CacheValue<T>(
        val data: T,
        val timestamp: Long
    )

    private val trackCache = LruCache<String, CacheValue<MediaTrackInfo>>(MAX_TRACK_ENTRIES)
    private val playlistCache = LruCache<String, CacheValue<PlaylistResolutionResult>>(MAX_PLAYLIST_ENTRIES)

    @Synchronized
    fun getTrack(key: String): MediaTrackInfo? {
        if (key.isBlank()) return null
        val entry = trackCache.get(key) ?: return null
        if (System.currentTimeMillis() - entry.timestamp > CACHE_EXPIRY_MS) {
            trackCache.remove(key)
            return null
        }
        val track = entry.data
        // Never return a track from single-track cache if it lacks a real streamUrl or has a placeholder title
        if (track.streamUrl.isBlank() ||
            track.streamUrl.contains("youtube.com/watch") ||
            track.title.startsWith("Parça ")
        ) {
            trackCache.remove(key)
            return null
        }
        return track
    }

    @Synchronized
    fun putTrack(key: String, trackInfo: MediaTrackInfo) {
        if (key.isBlank()) return
        // Only cache tracks that have a resolved stream URL and real title
        if (trackInfo.streamUrl.isBlank() ||
            trackInfo.streamUrl.contains("youtube.com/watch") ||
            trackInfo.title.startsWith("Parça ")
        ) {
            return
        }
        trackCache.put(key, CacheValue(trackInfo, System.currentTimeMillis()))
        if (trackInfo.id.isNotBlank()) {
            trackCache.put(trackInfo.id, CacheValue(trackInfo, System.currentTimeMillis()))
        }
        if (trackInfo.sourceUrl.isNotBlank()) {
            trackCache.put(trackInfo.sourceUrl, CacheValue(trackInfo, System.currentTimeMillis()))
        }
    }

    @Synchronized
    fun getPlaylist(key: String): PlaylistResolutionResult? {
        if (key.isBlank()) return null
        val entry = playlistCache.get(key) ?: return null
        if (System.currentTimeMillis() - entry.timestamp > CACHE_EXPIRY_MS) {
            playlistCache.remove(key)
            return null
        }
        // Invalidate old cached playlists if they contained "Parça 1" placeholders
        if (entry.data.tracks.any { it.title.startsWith("Parça ") }) {
            playlistCache.remove(key)
            return null
        }
        return entry.data
    }

    @Synchronized
    fun putPlaylist(key: String, playlist: PlaylistResolutionResult) {
        if (key.isBlank()) return
        playlistCache.put(key, CacheValue(playlist, System.currentTimeMillis()))
        if (playlist.playlistId.isNotBlank()) {
            playlistCache.put(playlist.playlistId, CacheValue(playlist, System.currentTimeMillis()))
        }
        for (track in playlist.tracks) {
            putTrack(track.id, track)
        }
    }

    @Synchronized
    fun clearAll() {
        trackCache.evictAll()
        playlistCache.evictAll()
    }
}

sealed class StreamResolutionResult {
    data class Success(val trackInfo: MediaTrackInfo) : StreamResolutionResult()
    data class Error(val message: String) : StreamResolutionResult()
}

data class PlaylistResolutionResult(
    val playlistId: String,
    val title: String,
    val tracks: List<MediaTrackInfo>,
    val thumbnailUrl: String? = null
)

@androidx.compose.runtime.Immutable
data class RemotePlaylistSummary(
    val playlistId: String,
    val browseId: String,
    val title: String,
    val subtitle: String,
    val trackCount: Int?,
    val trackCountLabel: String,
    val thumbnailUrl: String? = null
) {
    val authorOrSubtitle: String
        get() = subtitle
}

class YouTubeStreamResolver(context: Context? = null) {
    private val appContext: Context? = context?.applicationContext
    private val ytdlpResolver: YTDlpResolver? = appContext?.let { YTDlpResolver(it) }
    private val downloadEngine = DownloadEngine()

    internal fun parseWebRemixTracks(
        node: Any?,
        playlistId: String,
        playlistTitle: String,
        playlistCoverUrl: String?,
        outList: MutableList<MediaTrackInfo>,
        seenIds: MutableSet<String>,
        insideValidPlaylistContainer: Boolean = false
    ) {
        val beforeSize = outList.size
        DownloadEngine.parseWebRemixTracks(
            node = node,
            albumLabel = playlistTitle,
            out = outList,
            seenIds = seenIds,
            expectedPlaylistId = playlistId
        )
        if (!playlistCoverUrl.isNullOrBlank()) {
            for (i in beforeSize until outList.size) {
                if (outList[i].thumbnailUrl.isNullOrBlank()) {
                    outList[i] = outList[i].copy(thumbnailUrl = playlistCoverUrl)
                }
            }
        }
    }

    internal fun findContinuationToken(
        node: Any?,
        insideValidPlaylistContainer: Boolean = false
    ): String? {
        return DownloadEngine.findContinuationToken(node)
    }

    internal fun parseLibraryPlaylistsFromNode(
        node: Any?,
        out: MutableList<RemotePlaylistSummary>,
        seenIds: MutableSet<String> = mutableSetOf()
    ) {
        val temp = mutableListOf<RemotePlaylistSummary>()
        DownloadEngine.parseLibraryPlaylistsFromNode(node, temp)
        for (item in temp) {
            if (seenIds.add(item.playlistId)) {
                out.add(item)
            }
        }
    }

    suspend fun fetchUserLibraryPlaylists(
        cookies: List<CookieItem> = emptyList()
    ): Result<List<RemotePlaylistSummary>> = withContext(Dispatchers.IO) {
        val cookieHeader = NetscapeCookieHelper.buildCookieHeaderString(cookies, appContext)
        if (cookieHeader.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Kütüphanedeki oynatma listelerini çekmek için YouTube Music oturumu (çerez) gereklidir.")
            )
        }
        downloadEngine.fetchUserLibraryPlaylists(cookieHeader)
    }

    fun evaluatePlatformSupport(urlOrQuery: String): PlatformSupportStatus? {
        return ytdlpResolver?.evaluateUrlSupport(urlOrQuery)
    }

    suspend fun resolveInput(
        input: String,
        cookies: List<CookieItem> = emptyList()
    ): StreamResolutionResult = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return@withContext StreamResolutionResult.Error("Lütfen bir bağlantı veya şarkı adı girin.")
        }

        val activeCookies = appContext?.let {
            NetscapeCookieHelper.resolveActiveCookies(it, cookies)
        } ?: cookies

        // 0. Check in-memory fast resolution cache (<1ms response)
        val cached = StreamCache.getTrack(trimmed)
        if (cached != null) {
            Log.d(TAG, "Cache hit for input: $trimmed")
            return@withContext StreamResolutionResult.Success(cached)
        }

        val isHttpUrl = trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)

        // 1. Check for direct media URL extensions (.mp3, .m4a, etc.)
        if (isHttpUrl && YTDlpResolver.isLikelyDirectMediaExtension(trimmed)) {
            val directProbe = ytdlpResolver?.probeDirectMediaUrl(trimmed, activeCookies)
            if (directProbe != null && directProbe.isSuccess) {
                val track = directProbe.getOrThrow()
                StreamCache.putTrack(trimmed, track)
                return@withContext StreamResolutionResult.Success(track)
            }
        }

        // 2. Check for SoundCloud / Vimeo
        if (isHttpUrl && (YTDlpResolver.isSoundCloudUrl(trimmed) || YTDlpResolver.isVimeoUrl(trimmed))) {
            val resolver = ytdlpResolver
            if (resolver == null || !resolver.isEngineReady()) {
                val platformName = if (YTDlpResolver.isSoundCloudUrl(trimmed)) "SoundCloud" else "Vimeo"
                return@withContext StreamResolutionResult.Error(
                    "$platformName bağlantıları yalnızca yt-dlp (ytdlnis) motoru aktif olduğunda desteklenir."
                )
            }
            val result = resolver.extractSingleMetadata(trimmed, activeCookies)
            return@withContext result.fold(
                onSuccess = {
                    StreamCache.putTrack(trimmed, it)
                    StreamResolutionResult.Success(it)
                },
                onFailure = { err ->
                    val platformName = if (YTDlpResolver.isSoundCloudUrl(trimmed)) "SoundCloud" else "Vimeo"
                    StreamResolutionResult.Error(
                        "$platformName akışı yt-dlp ile çözümlenemedi: ${err.localizedMessage ?: "Bilinmeyen hata"}"
                    )
                }
            )
        }

        // 3. YouTube URL or Video ID or Query: Try YouTube video ID extraction first
        val videoId = if (isHttpUrl) {
            extractVideoId(trimmed)
        } else {
            searchYouTubeFirstVideoId(trimmed, activeCookies)
        }

        if (!videoId.isNullOrBlank()) {
            val res = resolveVideoById(videoId, activeCookies)
            if (res is StreamResolutionResult.Success) {
                StreamCache.putTrack(trimmed, res.trackInfo)
            }
            return@withContext res
        }

        // 4. Fallback for other arbitrary URLs with yt-dlp
        if (ytdlpResolver != null && ytdlpResolver.isEngineReady()) {
            val ytdlpResult = ytdlpResolver.extractSingleMetadata(trimmed, activeCookies)
            if (ytdlpResult.isSuccess) {
                val info = ytdlpResult.getOrThrow()
                if (info.streamUrl.startsWith("http")) {
                    StreamCache.putTrack(trimmed, info)
                    return@withContext StreamResolutionResult.Success(info)
                }
            }
        }

        if (isHttpUrl && !YTDlpResolver.isYouTubeUrl(trimmed)) {
            val directProbe = ytdlpResolver?.probeDirectMediaUrl(trimmed, activeCookies)
            if (directProbe != null && directProbe.isSuccess) {
                val info = directProbe.getOrThrow()
                StreamCache.putTrack(trimmed, info)
                return@withContext StreamResolutionResult.Success(info)
            }
            return@withContext StreamResolutionResult.Error(
                "Bağlantı çözümlenemedi. Bu adres geçerli bir doğrudan medya akışı değil veya yt-dlp tarafından desteklenmiyor."
            )
        }

        StreamResolutionResult.Error("YouTube video kimliği çözümlenemedi: $trimmed")
    }

    suspend fun searchCatalog(
        query: String,
        cookies: List<CookieItem> = emptyList(),
        maxResults: Int = 12
    ): List<MediaTrackInfo> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val activeCookies = appContext?.let {
            NetscapeCookieHelper.resolveActiveCookies(it, cookies)
        } ?: cookies
        searchYouTubeCatalogViaInnerTube(trimmed, activeCookies, maxResults)
    }

    suspend fun resolvePlaylist(
        playlistIdOrUrl: String,
        cookies: List<CookieItem> = emptyList()
    ): PlaylistResolutionResult = withContext(Dispatchers.IO) {
        val trimmed = playlistIdOrUrl.trim()
        val cleanPlaylistId = extractPlaylistId(trimmed) ?: trimmed
        val activeCookies = appContext?.let {
            NetscapeCookieHelper.resolveActiveCookies(it, cookies)
        } ?: cookies

        val isLikedMusic = cleanPlaylistId.equals("LM", ignoreCase = true) ||
            cleanPlaylistId.equals("VLLM", ignoreCase = true) ||
            cleanPlaylistId.equals("FEmusic_liked_videos", ignoreCase = true)
        val isLikedVideo = cleanPlaylistId.equals("LL", ignoreCase = true) ||
            cleanPlaylistId.equals("VLLL", ignoreCase = true)
        val isLikedPlaylist = isLikedMusic || isLikedVideo

        val fullUrl = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            isLikedVideo -> "https://www.youtube.com/playlist?list=LL"
            isLikedMusic -> "https://music.youtube.com/playlist?list=LM"
            else -> "https://www.youtube.com/playlist?list=$cleanPlaylistId"
        }

        // Check playlist cache (<1ms response)
        val cachedPlaylist = StreamCache.getPlaylist(trimmed) ?: StreamCache.getPlaylist(cleanPlaylistId)
        if (cachedPlaylist != null && cachedPlaylist.tracks.isNotEmpty()) {
            Log.d(TAG, "Cache hit for playlist: $trimmed")
            return@withContext cachedPlaylist
        }

        val cookieHeader = NetscapeCookieHelper.buildCookieHeaderString(activeCookies, appContext)

        // 1. For YouTube Music Liked Songs (LM / VLLM), strictly use music.youtube.com/youtubei/v1/browse
        // with browseId = "VLLM" and clientName = "WEB_REMIX" (Client ID: 67) so Google's servers filter out non-music videos directly.
        if (isLikedMusic) {
            if (!cookieHeader.isNullOrBlank()) {
                val pureMusicResult = downloadEngine.fetchPureYtMusicLikedSongs(cookieHeader)
                val playlist = pureMusicResult.getOrNull()
                if (playlist != null && playlist.tracks.isNotEmpty()) {
                    StreamCache.putPlaylist(trimmed, playlist)
                    return@withContext playlist
                }
            }
            val authBrowsePlaylist = resolveAuthenticatedMusicPlaylistViaInnerTube("VLLM", activeCookies)
            if (authBrowsePlaylist.tracks.isNotEmpty()) {
                StreamCache.putPlaylist(trimmed, authBrowsePlaylist)
                return@withContext authBrowsePlaylist
            }
            return@withContext PlaylistResolutionResult(
                playlistId = "VLLM",
                title = "Beğenilen Şarkılar (YT Music)",
                tracks = emptyList()
            )
        }

        // 1b. For Liked Videos (LL) or custom authenticated playlists
        if (isLikedPlaylist || NetscapeCookieHelper.hasYouTubeAuthCookies(activeCookies)) {
            if (!isLikedVideo && !cookieHeader.isNullOrBlank()) {
                val webRemixPlaylist = downloadEngine.fetchYtMusicPlaylist(cleanPlaylistId, cookieHeader).getOrNull()
                if (webRemixPlaylist != null && webRemixPlaylist.tracks.isNotEmpty()) {
                    StreamCache.putPlaylist(trimmed, webRemixPlaylist)
                    return@withContext webRemixPlaylist
                }
            }

            val authBrowsePlaylist = resolveAuthenticatedMusicPlaylistViaInnerTube(cleanPlaylistId, activeCookies)
            if (authBrowsePlaylist.tracks.isNotEmpty()) {
                StreamCache.putPlaylist(trimmed, authBrowsePlaylist)
                return@withContext authBrowsePlaylist
            }

            if (ytdlpResolver != null && ytdlpResolver.isEngineReady()) {
                val ytdlpRes = ytdlpResolver.extractPlaylistMetadata(fullUrl, activeCookies, maxItems = 100)
                if (ytdlpRes.isSuccess) {
                    val playlist = ytdlpRes.getOrThrow()
                    if (playlist.tracks.isNotEmpty()) {
                        StreamCache.putPlaylist(trimmed, playlist)
                        return@withContext playlist
                    }
                }
            }
        }

        // 2. Try fast InnerTube HTML extraction (~200ms) for standard public YouTube playlists
        val innerTubePlaylist = resolveYouTubePlaylistViaInnerTube(cleanPlaylistId, activeCookies)
        if (innerTubePlaylist.tracks.isNotEmpty()) {
            StreamCache.putPlaylist(trimmed, innerTubePlaylist)
            return@withContext innerTubePlaylist
        }

        // 3. Fallback to yt-dlp playlist extraction with cookies
        if (ytdlpResolver != null && ytdlpResolver.isEngineReady()) {
            val ytdlpPlaylist = ytdlpResolver.extractPlaylistMetadata(fullUrl, activeCookies, maxItems = 100)
            if (ytdlpPlaylist.isSuccess) {
                val res = ytdlpPlaylist.getOrThrow()
                if (res.tracks.isNotEmpty()) {
                    StreamCache.putPlaylist(trimmed, res)
                    return@withContext res
                }
            }
        }

        // 4. Final fallback to authenticated InnerTube browse if not tried yet
        val fallbackBrowse = resolveAuthenticatedMusicPlaylistViaInnerTube(cleanPlaylistId, activeCookies)
        if (fallbackBrowse.tracks.isNotEmpty()) {
            StreamCache.putPlaylist(trimmed, fallbackBrowse)
            return@withContext fallbackBrowse
        }

        innerTubePlaylist
    }

    suspend fun resolveVideoById(
        videoId: String,
        cookies: List<CookieItem> = emptyList()
    ): StreamResolutionResult = withContext(Dispatchers.IO) {
        val cleanId = extractVideoId(videoId) ?: videoId.trim()
        if (cleanId.isBlank()) {
            return@withContext StreamResolutionResult.Error("Geçersiz video kimliği.")
        }

        val activeCookies = appContext?.let {
            NetscapeCookieHelper.resolveActiveCookies(it, cookies)
        } ?: cookies

        // Check cache first
        val cached = StreamCache.getTrack(cleanId)
        if (cached != null) {
            Log.d(TAG, "Cache hit for video ID: $cleanId")
            return@withContext StreamResolutionResult.Success(cached)
        }

        // FAST PATH 1: Standalone DownloadEngine InnerTube resolution
        val cookieHeader = NetscapeCookieHelper.buildCookieHeaderString(activeCookies, appContext)
        val engineResolved = downloadEngine.resolveStreamViaInnerTube(cleanId, cookieHeader)
        if (engineResolved != null) {
            StreamCache.putTrack(cleanId, engineResolved)
            return@withContext StreamResolutionResult.Success(engineResolved)
        }

        // FAST PATH 2: Try additional InnerTube API clients (~100-300ms)
        val clients = listOf(
            InnerTubeClient(
                clientName = "ANDROID_VR",
                clientVersion = "1.60.19",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                androidSdkVersion = 32
            ),
            InnerTubeClient(
                clientName = "ANDROID",
                clientVersion = "19.44.38",
                userAgent = "com.google.android.youtube/19.44.38 (Linux; U; Android 14) gzip",
                androidSdkVersion = 34
            ),
            InnerTubeClient(
                clientName = "IOS",
                clientVersion = "19.45.4",
                userAgent = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1 like Mac OS X)",
                androidSdkVersion = null
            )
        )

        var lastError = "YouTube ses akışı alınamadı."
        for (client in clients) {
            try {
                val track = fetchInnerTubePlayer(cleanId, client, activeCookies)
                if (track != null) {
                    StreamCache.putTrack(cleanId, track)
                    return@withContext StreamResolutionResult.Success(track)
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: lastError
                Log.w(TAG, "InnerTube client ${client.clientName} failed for $cleanId: $lastError")
            }
        }

        // FALLBACK PATH: Try yt-dlp process execution with cookies if InnerTube API requests failed
        val canonicalUrl = "https://www.youtube.com/watch?v=$cleanId"
        if (ytdlpResolver != null && ytdlpResolver.isEngineReady()) {
            val ytdlpInfo = ytdlpResolver.extractSingleMetadata(canonicalUrl, activeCookies).getOrNull()
            if (ytdlpInfo != null && ytdlpInfo.streamUrl.startsWith("http")) {
                StreamCache.putTrack(cleanId, ytdlpInfo)
                return@withContext StreamResolutionResult.Success(ytdlpInfo)
            }
        }

        StreamResolutionResult.Error(lastError)
    }

    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            return trimmed
        }
        val patterns = listOf(
            "(?:v=|/v/|youtu\\.be/|/embed/|/shorts/|/live/)([a-zA-Z0-9_-]{11})",
            "^([a-zA-Z0-9_-]{11})$"
        )
        for (pattern in patterns) {
            val matcher = Pattern.compile(pattern).matcher(trimmed)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        val matcher = Pattern.compile("[?&]list=([a-zA-Z0-9_-]+)").matcher(trimmed)
        if (matcher.find()) {
            val listId = matcher.group(1)
            if (!listId.isNullOrBlank()) return listId
        }
        if (trimmed.equals("LM", ignoreCase = true) ||
            trimmed.equals("LL", ignoreCase = true) ||
            trimmed.equals("VLLM", ignoreCase = true) ||
            trimmed.equals("FEmusic_liked_videos", ignoreCase = true) ||
            trimmed.startsWith("PL") ||
            trimmed.startsWith("OLAK") ||
            trimmed.startsWith("UU") ||
            trimmed.startsWith("RD") ||
            trimmed.startsWith("VL")
        ) {
            return trimmed
        }
        return null
    }

    private data class InnerTubeClient(
        val clientName: String,
        val clientVersion: String,
        val userAgent: String,
        val androidSdkVersion: Int?
    )

    private fun fetchInnerTubePlayer(
        videoId: String,
        client: InnerTubeClient,
        cookies: List<CookieItem>
    ): MediaTrackInfo? {
        val endpoint = URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", client.userAgent)
            setRequestProperty("X-Goog-Api-Format-Version", "2")
            NetscapeCookieHelper.buildCookieHeaderString(cookies)?.let {
                setRequestProperty("Cookie", it)
            }
        }

        val clientObj = JSONObject().apply {
            put("clientName", client.clientName)
            put("clientVersion", client.clientVersion)
            put("hl", "tr")
            put("gl", "TR")
            if (client.androidSdkVersion != null) {
                put("androidSdkVersion", client.androidSdkVersion)
            }
        }

        val payload = JSONObject().apply {
            put("videoId", videoId)
            put("context", JSONObject().put("client", clientObj))
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload.toString())
            writer.flush()
        }

        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("InnerTube HTTP $code")
        }

        val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
            it.readText()
        }
        conn.disconnect()

        val root = JSONObject(responseText)
        val playabilityStatus = root.optJSONObject("playabilityStatus")
        val status = playabilityStatus?.optString("status", "") ?: ""
        if (status != "OK") {
            val reason = playabilityStatus?.optString("reason", "Video oynatılamıyor ($status)")
            throw IllegalStateException(reason ?: "Video kullanılamıyor")
        }

        val videoDetails = root.optJSONObject("videoDetails") ?: JSONObject()
        val title = videoDetails.optString("title", "Bilinmeyen Şarkı").ifBlank { "Bilinmeyen Şarkı" }
        val author = videoDetails.optString("author", "Bilinmeyen Sanatçı")
            .removeSuffix(" - Topic")
            .trim()
            .ifBlank { "Bilinmeyen Sanatçı" }
        val durationSeconds = videoDetails.optString("lengthSeconds", "0").toLongOrNull() ?: 0L

        var thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        val thumbArr = videoDetails.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        if (thumbArr != null && thumbArr.length() > 0) {
            val bestThumb = thumbArr.optJSONObject(thumbArr.length() - 1)?.optString("url", "")
            if (!bestThumb.isNullOrBlank()) {
                thumbnailUrl = bestThumb
            }
        }

        val streamingData = root.optJSONObject("streamingData")
            ?: throw IllegalStateException("StreamingData bulunamadı")

        val candidates = mutableListOf<JSONObject>()
        streamingData.optJSONArray("adaptiveFormats")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { candidates.add(it) }
            }
        }
        streamingData.optJSONArray("formats")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { candidates.add(it) }
            }
        }

        var selectedFormat: JSONObject? = null
        var bestScore = -1
        for (fmt in candidates) {
            val url = fmt.optString("url", "")
            if (url.isBlank()) continue
            val mime = fmt.optString("mimeType", "")
            val isAudioOnly = mime.startsWith("audio/")
            val isMp4Audio = mime.contains("audio/mp4")
            val bitrate = fmt.optInt("bitrate", 0)
            val score = (if (isAudioOnly) 1_000_000 else 0) +
                (if (isMp4Audio) 500_000 else 0) +
                (bitrate / 100)
            if (score > bestScore) {
                bestScore = score
                selectedFormat = fmt
            }
        }

        val chosen = selectedFormat ?: return null
        val streamUrl = chosen.optString("url", "")
        if (streamUrl.isBlank()) return null

        val rawMime = chosen.optString("mimeType", "audio/mp4").substringBefore(";").trim()
        val bitrateKbps = (chosen.optInt("bitrate", 128_000) / 1000).coerceAtLeast(64)
        val contentLength = chosen.optString("contentLength", "-1").toLongOrNull() ?: -1L

        return MediaTrackInfo(
            id = videoId,
            title = title,
            artist = author,
            album = "Sakura Music",
            durationSeconds = durationSeconds,
            thumbnailUrl = thumbnailUrl,
            sourceUrl = "https://www.youtube.com/watch?v=$videoId",
            streamUrl = streamUrl,
            mimeType = rawMime,
            bitrateKbps = bitrateKbps,
            contentLengthBytes = contentLength
        )
    }

    private fun searchYouTubeFirstVideoId(
        query: String,
        cookies: List<CookieItem>
    ): String? {
        return searchYouTubeCatalogViaInnerTube(query, cookies, maxResults = 1)
            .firstOrNull()
            ?.id
    }

    private fun searchYouTubeCatalogViaInnerTube(
        query: String,
        cookies: List<CookieItem>,
        maxResults: Int
    ): List<MediaTrackInfo> {
        return try {
            val endpoint = URL("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                NetscapeCookieHelper.buildCookieHeaderString(cookies)?.let {
                    setRequestProperty("Cookie", it)
                }
            }

            val payload = JSONObject().apply {
                put("query", query)
                put(
                    "context",
                    JSONObject().put(
                        "client",
                        JSONObject().apply {
                            put("clientName", "WEB")
                            put("clientVersion", "2.20241114.01.00")
                            put("hl", "tr")
                            put("gl", "TR")
                        }
                    )
                )
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }
            conn.disconnect()

            val results = mutableListOf<MediaTrackInfo>()
            val seenIds = mutableSetOf<String>()
            val root = JSONObject(responseText)
            collectVideoRenderers(root, results, seenIds, maxResults)

            if (results.isEmpty()) {
                val regex = Regex(""""videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"""")
                for (match in regex.findAll(responseText)) {
                    val vId = match.groupValues[1]
                    if (seenIds.add(vId)) {
                        results.add(
                            MediaTrackInfo(
                                id = vId,
                                title = query,
                                artist = "YouTube Music",
                                durationSeconds = 0L,
                                thumbnailUrl = "https://i.ytimg.com/vi/$vId/hqdefault.jpg",
                                sourceUrl = "https://www.youtube.com/watch?v=$vId",
                                streamUrl = "https://www.youtube.com/watch?v=$vId"
                            )
                        )
                        if (results.size >= maxResults) break
                    }
                }
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun collectVideoRenderers(
        node: Any?,
        out: MutableList<MediaTrackInfo>,
        seenIds: MutableSet<String>,
        maxResults: Int
    ) {
        if (out.size >= maxResults || node == null) return
        when (node) {
            is JSONObject -> {
                val vr = node.optJSONObject("videoRenderer")
                if (vr != null) {
                    val videoId = vr.optString("videoId", "")
                    if (videoId.length == 11 && seenIds.add(videoId)) {
                        val title = extractRunsText(vr.optJSONObject("title")).ifBlank { "YouTube Parçası" }
                        val artist = extractRunsText(vr.optJSONObject("ownerText"))
                            .ifBlank { extractRunsText(vr.optJSONObject("longBylineText")) }
                            .removeSuffix(" - Topic")
                            .ifBlank { "YouTube Sanatçısı" }
                        val durationText = vr.optJSONObject("lengthText")?.optString("simpleText", "")
                            ?: extractRunsText(vr.optJSONObject("lengthText"))
                        val durationSec = parseDurationText(durationText)
                        out.add(
                            MediaTrackInfo(
                                id = videoId,
                                title = title,
                                artist = artist,
                                album = "YouTube Music",
                                durationSeconds = durationSec,
                                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                sourceUrl = "https://www.youtube.com/watch?v=$videoId",
                                streamUrl = "https://www.youtube.com/watch?v=$videoId"
                            )
                        )
                    }
                }
                val keys = node.keys()
                while (keys.hasNext() && out.size < maxResults) {
                    val key = keys.next()
                    collectVideoRenderers(node.opt(key), out, seenIds, maxResults)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    if (out.size >= maxResults) break
                    collectVideoRenderers(node.opt(i), out, seenIds, maxResults)
                }
            }
        }
    }

    private fun resolveYouTubePlaylistViaInnerTube(
        playlistId: String,
        cookies: List<CookieItem>
    ): PlaylistResolutionResult {
        return try {
            val url = URL("https://www.youtube.com/playlist?list=${URLEncoder.encode(playlistId, "UTF-8")}&hl=tr&gl=TR")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
                NetscapeCookieHelper.buildCookieHeaderString(cookies)?.let {
                    setRequestProperty("Cookie", it)
                }
            }
            val html = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }
            conn.disconnect()

            val tracks = mutableListOf<MediaTrackInfo>()
            val seenIds = mutableSetOf<String>()
            var resolvedPlaylistTitle = "YouTube Çalma Listesi"
            var resolvedPlaylistThumbnail: String? = null

            // 1. Parse ytInitialData JSON from HTML to get exact song titles, artists, durations, playlist title, and cover
            val jsonStr = extractYtInitialDataJson(html)
            if (!jsonStr.isNullOrBlank()) {
                try {
                    val root = JSONObject(jsonStr)
                    val extractedTitle = extractPlaylistHeaderTitle(root)
                    if (extractedTitle.isNotBlank()) {
                        resolvedPlaylistTitle = extractedTitle
                    }
                    resolvedPlaylistThumbnail = extractPlaylistHeaderThumbnail(root)
                    collectPlaylistVideoRenderers(
                        node = root,
                        playlistTitle = resolvedPlaylistTitle,
                        out = tracks,
                        seenIds = seenIds,
                        isStrictMusicPlaylist = false,
                        expectedPlaylistId = playlistId
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse ytInitialData JSON for playlist $playlistId: ${e.message}")
                }
            }

            if (resolvedPlaylistTitle == "YouTube Çalma Listesi" || resolvedPlaylistTitle == "Çalma Listesi") {
                val htmlTitle = extractPlaylistTitleFromHtml(html)
                if (!htmlTitle.isNullOrBlank()) {
                    resolvedPlaylistTitle = htmlTitle
                }
            }
            if (resolvedPlaylistThumbnail.isNullOrBlank()) {
                resolvedPlaylistThumbnail = extractPlaylistThumbnailFromHtml(html)
            }

            // 2. Fallback: If ytInitialData parsing yielded no tracks, scan HTML blocks and resolve real titles via oEmbed
            if (tracks.isEmpty()) {
                val regex = Regex(""""playlistVideoRenderer"\s*:\s*\{(.*?)"videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"""")
                for (match in regex.findAll(html)) {
                    val vId = match.groupValues[2]
                    if (seenIds.add(vId)) {
                        val (realTitle, realArtist) = fetchRealTrackTitleAndArtist(vId, cookies)
                        tracks.add(
                            MediaTrackInfo(
                                id = vId,
                                title = realTitle,
                                artist = realArtist,
                                album = resolvedPlaylistTitle,
                                durationSeconds = 0L,
                                thumbnailUrl = "https://i.ytimg.com/vi/$vId/hqdefault.jpg",
                                sourceUrl = "https://www.youtube.com/watch?v=$vId",
                                streamUrl = "",
                                playlistId = playlistId,
                                playlistTitle = resolvedPlaylistTitle,
                                playlistCoverUrl = resolvedPlaylistThumbnail
                            )
                        )
                    }
                }
            }

            val finalCover = resolvedPlaylistThumbnail ?: tracks.firstOrNull()?.thumbnailUrl
            val stampedTracks = tracks.map { track ->
                track.copy(
                    album = resolvedPlaylistTitle,
                    playlistId = playlistId,
                    playlistTitle = resolvedPlaylistTitle,
                    playlistCoverUrl = finalCover
                )
            }

            PlaylistResolutionResult(
                playlistId = playlistId,
                title = resolvedPlaylistTitle,
                tracks = stampedTracks,
                thumbnailUrl = finalCover
            )
        } catch (_: Exception) {
            PlaylistResolutionResult(playlistId = playlistId, title = "Çalma Listesi", tracks = emptyList())
        }
    }

    private fun resolveAuthenticatedMusicPlaylistViaInnerTube(
        playlistId: String,
        cookies: List<CookieItem>
    ): PlaylistResolutionResult {
        val cookieHeader = NetscapeCookieHelper.buildCookieHeaderString(cookies, appContext)
        if (cookieHeader.isNullOrBlank()) {
            return PlaylistResolutionResult(playlistId = playlistId, title = "Beğenilen Şarkılar", tracks = emptyList())
        }

        val isLikedMusic = playlistId.equals("LM", ignoreCase = true) ||
            playlistId.equals("VLLM", ignoreCase = true) ||
            playlistId.equals("FEmusic_liked_videos", ignoreCase = true)
        val isLikedVideo = playlistId.equals("LL", ignoreCase = true) ||
            playlistId.equals("VLLL", ignoreCase = true)
        val isLiked = isLikedMusic || isLikedVideo

        val browseTargets = when {
            isLikedVideo -> listOf(
                Triple("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false", "VLLL", "WEB"),
                Triple("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false", "VLLM", "WEB_REMIX")
            )
            isLikedMusic -> listOf(
                Triple("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false", "VLLM", "WEB_REMIX"),
                Triple("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false", "FEmusic_liked_videos", "WEB_REMIX")
            )
            else -> {
                val normalizedBrowseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
                listOf(
                    Triple("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false", normalizedBrowseId, "WEB_REMIX"),
                    Triple("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false", normalizedBrowseId, "WEB")
                )
            }
        }

        for ((endpointUrl, browseId, clientName) in browseTargets) {
            try {
                val isMusicOrigin = endpointUrl.contains("music.youtube.com")
                val origin = if (isMusicOrigin) "https://music.youtube.com" else "https://www.youtube.com"
                val clientVersion = if (clientName == "WEB_REMIX") "1.20260101.01.00" else "2.20260101.01.00"
                val clientNameHeaderId = if (clientName == "WEB_REMIX") "67" else "1"

                val tracks = mutableListOf<MediaTrackInfo>()
                val seenIds = mutableSetOf<String>()
                var resolvedTitle = if (isLiked) "Beğenilen Şarkılar" else "Çalma Listesi"
                var resolvedThumbnail: String? = null
                var continuationToken: String? = null
                val visitedContinuations = mutableSetOf<String>()
                var page = 0
                val maxPages = 35

                while (page < maxPages && tracks.size < 1500) {
                    val sapisidHash = NetscapeCookieHelper.buildSapisidHashHeader(cookies, origin, appContext)
                    val conn = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 15_000
                        readTimeout = 20_000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        setRequestProperty(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                        )
                        setRequestProperty("Cookie", cookieHeader)
                        setRequestProperty("Origin", origin)
                        setRequestProperty("Referer", "$origin/")
                        setRequestProperty("X-Origin", origin)
                        setRequestProperty("X-Goog-AuthUser", "0")
                        setRequestProperty("X-YouTube-Client-Name", clientNameHeaderId)
                        setRequestProperty("X-YouTube-Client-Version", clientVersion)
                        if (!sapisidHash.isNullOrBlank()) {
                            setRequestProperty("Authorization", sapisidHash)
                        }
                    }

                    val payload = JSONObject().apply {
                        put(
                            "context",
                            JSONObject().put(
                                "client",
                                JSONObject().apply {
                                    put("clientName", clientName)
                                    put("clientVersion", clientVersion)
                                    put("hl", "tr")
                                    put("gl", "TR")
                                }
                            )
                        )
                        if (page == 0 || continuationToken.isNullOrBlank()) {
                            put("browseId", browseId)
                        } else {
                            put("continuation", continuationToken)
                        }
                    }

                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(payload.toString())
                        writer.flush()
                    }

                    val code = conn.responseCode
                    if (code !in 200..299) {
                        conn.disconnect()
                        break
                    }

                    val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                        it.readText()
                    }
                    conn.disconnect()

                    val root = JSONObject(body)
                    if (page == 0) {
                        val extractedHeaderTitle = extractPlaylistHeaderTitle(root)
                        if (extractedHeaderTitle.isNotBlank() && !isLiked) {
                            resolvedTitle = extractedHeaderTitle
                        } else if (extractedHeaderTitle.isNotBlank() && isLiked && resolvedTitle == "Beğenilen Şarkılar") {
                            resolvedTitle = extractedHeaderTitle
                        }
                        resolvedThumbnail = extractPlaylistHeaderThumbnail(root)
                    }

                    val countBefore = tracks.size
                    collectPlaylistVideoRenderers(
                        node = root,
                        playlistTitle = resolvedTitle,
                        out = tracks,
                        seenIds = seenIds,
                        isStrictMusicPlaylist = isLikedMusic,
                        expectedPlaylistId = playlistId
                    )

                    val nextToken = extractContinuationToken(root)
                    if (nextToken.isNullOrBlank() || !visitedContinuations.add(nextToken)) {
                        break
                    }
                    // Stop if a continuation page yielded 0 tracks and no new items
                    if (page > 2 && tracks.size == countBefore) {
                        break
                    }
                    continuationToken = nextToken
                    page++
                }

                if (tracks.isNotEmpty()) {
                    val finalCover = resolvedThumbnail ?: tracks.firstOrNull()?.thumbnailUrl
                    val stampedTracks = tracks.map { track ->
                        track.copy(
                            album = resolvedTitle,
                            playlistId = playlistId,
                            playlistTitle = resolvedTitle,
                            playlistCoverUrl = finalCover
                        )
                    }
                    Log.d(TAG, "Morphe InnerTube browse resolved ${stampedTracks.size} tracks across ${page + 1} pages for $browseId")
                    return PlaylistResolutionResult(
                        playlistId = playlistId,
                        title = resolvedTitle,
                        tracks = stampedTracks,
                        thumbnailUrl = finalCover
                    )
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Authenticated browse failed for $browseId: ${e.message}")
            }
        }

        return PlaylistResolutionResult(
            playlistId = playlistId,
            title = if (isLiked) "Beğenilen Şarkılar" else "Çalma Listesi",
            tracks = emptyList()
        )
    }

    internal fun extractPlaylistHeaderTitle(root: JSONObject): String {
        try {
            val metaTitle = root.optJSONObject("metadata")
                ?.optJSONObject("playlistMetadataRenderer")
                ?.optString("title", "")
                .orEmpty()
                .trim()
            if (metaTitle.isNotBlank()) return metaTitle

            val header = root.optJSONObject("header")
            if (header != null) {
                val detailTitle = extractRunsText(
                    header.optJSONObject("musicDetailHeaderRenderer")?.optJSONObject("title")
                        ?: header.optJSONObject("musicEditablePlaylistDetailHeaderRenderer")
                            ?.optJSONObject("header")
                            ?.optJSONObject("musicDetailHeaderRenderer")
                            ?.optJSONObject("title")
                        ?: header.optJSONObject("musicEditablePlaylistDetailHeaderRenderer")
                            ?.optJSONObject("header")
                            ?.optJSONObject("musicResponsiveHeaderRenderer")
                            ?.optJSONObject("title")
                        ?: header.optJSONObject("musicResponsiveHeaderRenderer")?.optJSONObject("title")
                        ?: header.optJSONObject("playlistHeaderRenderer")?.optJSONObject("title")
                )
                if (detailTitle.isNotBlank()) return detailTitle

                val pageHeaderTitle = header.optJSONObject("pageHeaderRenderer")
                    ?.optString("pageTitle", "")
                    .orEmpty()
                    .ifBlank {
                        header.optJSONObject("pageHeaderRenderer")
                            ?.optJSONObject("content")
                            ?.optJSONObject("pageHeaderViewModel")
                            ?.optJSONObject("title")
                            ?.optJSONObject("dynamicTextViewModel")
                            ?.optJSONObject("text")
                            ?.optString("content", "")
                            .orEmpty()
                    }
                    .trim()
                if (pageHeaderTitle.isNotBlank()) return pageHeaderTitle
            }

            // Modern YT Music WEB_REMIX twoColumnBrowseResultsRenderer header location
            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
            if (tabs != null) {
                for (i in 0 until tabs.length()) {
                    val sectionContents = tabs.optJSONObject(i)
                        ?.optJSONObject("tabRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("sectionListRenderer")
                        ?.optJSONArray("contents") ?: continue
                    for (j in 0 until sectionContents.length()) {
                        val sec = sectionContents.optJSONObject(j) ?: continue
                        val respTitle = extractRunsText(
                            sec.optJSONObject("musicResponsiveHeaderRenderer")?.optJSONObject("title")
                                ?: sec.optJSONObject("musicEditablePlaylistDetailHeaderRenderer")
                                    ?.optJSONObject("header")
                                    ?.optJSONObject("musicResponsiveHeaderRenderer")
                                    ?.optJSONObject("title")
                                ?: sec.optJSONObject("musicEditablePlaylistDetailHeaderRenderer")
                                    ?.optJSONObject("header")
                                    ?.optJSONObject("musicDetailHeaderRenderer")
                                    ?.optJSONObject("title")
                        )
                        if (respTitle.isNotBlank()) return respTitle
                    }
                }
            }

            // Sidebar primary info renderer fallback
            val sidebarItems = root.optJSONObject("sidebar")
                ?.optJSONObject("playlistSidebarRenderer")
                ?.optJSONArray("items")
            if (sidebarItems != null && sidebarItems.length() > 0) {
                val sidebarTitle = extractRunsText(
                    sidebarItems.optJSONObject(0)
                        ?.optJSONObject("playlistSidebarPrimaryInfoRenderer")
                        ?.optJSONObject("title")
                )
                if (sidebarTitle.isNotBlank()) return sidebarTitle
            }

            val microTitle = root.optJSONObject("microformat")
                ?.optJSONObject("microformatDataRenderer")
                ?.optString("title", "")
                .orEmpty()
                .removeSuffix(" - YouTube")
                .removeSuffix(" - YouTube Music")
                .trim()
            if (microTitle.isNotBlank()) return microTitle
        } catch (_: Exception) {
        }
        return ""
    }

    internal fun extractPlaylistHeaderThumbnail(root: JSONObject): String? {
        try {
            val header = root.optJSONObject("header")
            if (header != null) {
                extractLastThumbnailUrl(
                    header.optJSONObject("musicDetailHeaderRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONObject("croppedSquareThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                )?.let { return it }

                extractLastThumbnailUrl(
                    header.optJSONObject("musicEditablePlaylistDetailHeaderRenderer")
                        ?.optJSONObject("header")
                        ?.optJSONObject("musicDetailHeaderRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONObject("croppedSquareThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                )?.let { return it }

                extractLastThumbnailUrl(
                    header.optJSONObject("musicResponsiveHeaderRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                )?.let { return it }

                extractLastThumbnailUrl(
                    header.optJSONObject("playlistHeaderRenderer")
                        ?.optJSONObject("playlistHeaderBanner")
                        ?.optJSONObject("heroPlaylistThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                )?.let { return it }
            }

            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
            if (tabs != null) {
                for (i in 0 until tabs.length()) {
                    val sectionContents = tabs.optJSONObject(i)
                        ?.optJSONObject("tabRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("sectionListRenderer")
                        ?.optJSONArray("contents") ?: continue
                    for (j in 0 until sectionContents.length()) {
                        val sec = sectionContents.optJSONObject(j) ?: continue
                        val headerObj = sec.optJSONObject("musicResponsiveHeaderRenderer")
                            ?: sec.optJSONObject("musicEditablePlaylistDetailHeaderRenderer")
                                ?.optJSONObject("header")
                                ?.optJSONObject("musicResponsiveHeaderRenderer")
                            ?: sec.optJSONObject("musicEditablePlaylistDetailHeaderRenderer")
                                ?.optJSONObject("header")
                                ?.optJSONObject("musicDetailHeaderRenderer")
                        if (headerObj != null) {
                            extractLastThumbnailUrl(
                                headerObj.optJSONObject("thumbnail")
                                    ?.optJSONObject("musicThumbnailRenderer")
                                    ?.optJSONObject("thumbnail")
                                    ?.optJSONArray("thumbnails")
                                    ?: headerObj.optJSONObject("thumbnail")
                                        ?.optJSONObject("croppedSquareThumbnailRenderer")
                                        ?.optJSONObject("thumbnail")
                                        ?.optJSONArray("thumbnails")
                            )?.let { return it }
                        }
                    }
                }
            }

            extractLastThumbnailUrl(
                root.optJSONObject("microformat")
                    ?.optJSONObject("microformatDataRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
            )?.let { return it }

            val sidebarItems = root.optJSONObject("sidebar")
                ?.optJSONObject("playlistSidebarRenderer")
                ?.optJSONArray("items")
            if (sidebarItems != null && sidebarItems.length() > 0) {
                val thumbRenderer = sidebarItems.optJSONObject(0)
                    ?.optJSONObject("playlistSidebarPrimaryInfoRenderer")
                    ?.optJSONObject("thumbnailRenderer")
                extractLastThumbnailUrl(
                    thumbRenderer?.optJSONObject("playlistVideoThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                        ?: thumbRenderer?.optJSONObject("playlistCustomThumbnailRenderer")
                            ?.optJSONObject("thumbnail")
                            ?.optJSONArray("thumbnails")
                )?.let { return it }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun extractLastThumbnailUrl(thumbnails: JSONArray?): String? {
        if (thumbnails == null || thumbnails.length() == 0) return null
        for (i in thumbnails.length() - 1 downTo 0) {
            val url = thumbnails.optJSONObject(i)?.optString("url", "").orEmpty().trim()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return url
            }
        }
        return null
    }

    private fun extractPlaylistTitleFromHtml(html: String): String? {
        val ogMatch = Regex("""<meta\s+property=["']og:title["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
        if (!ogMatch.isNullOrBlank()) {
            val clean = decodeBasicHtmlEntities(ogMatch)
                .removeSuffix(" - YouTube")
                .removeSuffix(" - YouTube Music")
                .trim()
            if (clean.isNotBlank() && !clean.equals("YouTube", ignoreCase = true)) {
                return clean
            }
        }
        val titleMatch = Regex("""<title>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.getOrNull(1)
        if (!titleMatch.isNullOrBlank()) {
            val clean = decodeBasicHtmlEntities(titleMatch)
                .removeSuffix(" - YouTube")
                .removeSuffix(" - YouTube Music")
                .trim()
            if (clean.isNotBlank() && !clean.equals("YouTube", ignoreCase = true)) {
                return clean
            }
        }
        return null
    }

    private fun extractPlaylistThumbnailFromHtml(html: String): String? {
        val ogImage = Regex("""<meta\s+property=["']og:image["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.trim()
        if (!ogImage.isNullOrBlank() && (ogImage.startsWith("http://") || ogImage.startsWith("https://"))) {
            return decodeBasicHtmlEntities(ogImage)
        }
        return null
    }

    private fun decodeBasicHtmlEntities(raw: String): String {
        return raw
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    private fun extractContinuationToken(node: Any?): String? {
        return DownloadEngine.findContinuationToken(node)
    }

    private fun extractYtInitialDataJson(html: String): String? {
        val markers = listOf("var ytInitialData = ", "window[\"ytInitialData\"] = ", "ytInitialData = ")
        for (marker in markers) {
            val startIdx = html.indexOf(marker)
            if (startIdx >= 0) {
                val jsonStart = html.indexOf('{', startIdx + marker.length)
                if (jsonStart >= 0) {
                    var depth = 0
                    var inString = false
                    var escaped = false
                    for (i in jsonStart until html.length) {
                        val c = html[i]
                        if (inString) {
                            if (escaped) {
                                escaped = false
                            } else if (c == '\\') {
                                escaped = true
                            } else if (c == '"') {
                                inString = false
                            }
                        } else {
                            when (c) {
                                '"' -> inString = true
                                '{' -> depth++
                                '}' -> {
                                    depth--
                                    if (depth == 0) {
                                        return html.substring(jsonStart, i + 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    internal fun collectPlaylistVideoRenderers(
        node: Any?,
        playlistTitle: String,
        out: MutableList<MediaTrackInfo>,
        seenIds: MutableSet<String>,
        isStrictMusicPlaylist: Boolean = false,
        expectedPlaylistId: String? = null
    ) {
        if (node == null || out.size >= 1500) return
        // First extract WEB_REMIX tracks using strict playlist shelf scoping
        val webRemixCandidates = mutableListOf<MediaTrackInfo>()
        DownloadEngine.parseWebRemixTracks(
            node = node,
            albumLabel = playlistTitle,
            out = webRemixCandidates,
            seenIds = HashSet(seenIds),
            expectedPlaylistId = expectedPlaylistId
        )
        for (candidate in webRemixCandidates) {
            if (out.size >= 1500) break
            val passesFilter = StrictMusicTrackValidator.isStrictMusicTrack(
                title = candidate.title,
                artist = candidate.artist,
                durationSeconds = candidate.durationSeconds,
                musicVideoType = null,
                enforceStrictFilter = isStrictMusicPlaylist
            )
            if (passesFilter && seenIds.add(candidate.id)) {
                out.add(candidate)
            }
        }

        // Also support standard WEB playlistVideoRenderer items (excluding recommendation shelves)
        collectWebPlaylistVideoRenderersScoped(
            node = node,
            playlistTitle = playlistTitle,
            out = out,
            seenIds = seenIds,
            isStrictMusicPlaylist = isStrictMusicPlaylist
        )
    }

    private fun collectWebPlaylistVideoRenderersScoped(
        node: Any?,
        playlistTitle: String,
        out: MutableList<MediaTrackInfo>,
        seenIds: MutableSet<String>,
        isStrictMusicPlaylist: Boolean
    ) {
        if (node == null || out.size >= 1500) return
        when (node) {
            is JSONObject -> {
                val pvr = node.optJSONObject("playlistVideoRenderer")
                    ?: node.optJSONObject("playlistPanelVideoRenderer")
                if (pvr != null) {
                    val videoId = pvr.optString("videoId", "").trim()
                    val isPlayable = pvr.optBoolean("isPlayable", true)
                    if (isPlayable && StrictMusicTrackValidator.isValidVideoId(videoId) && !seenIds.contains(videoId)) {
                        val rawTitle = extractRunsText(pvr.optJSONObject("title")).trim()
                        val rawArtist = extractRunsText(pvr.optJSONObject("shortBylineText"))
                            .ifBlank { extractRunsText(pvr.optJSONObject("longBylineText")) }
                            .removeSuffix(" - Topic")
                            .trim()
                        val durationSec = pvr.optString("lengthSeconds", "").toLongOrNull()
                            ?: parseDurationText(extractRunsText(pvr.optJSONObject("lengthText")))

                        if (!StrictMusicTrackValidator.isGhostOrDeletedTitle(rawTitle)) {
                            val finalTitle = rawTitle.ifBlank { videoId }
                            val finalArtist = rawArtist.ifBlank { "YouTube Music" }

                            val passesFilter = StrictMusicTrackValidator.isStrictMusicTrack(
                                title = finalTitle,
                                artist = finalArtist,
                                durationSeconds = durationSec,
                                musicVideoType = null,
                                enforceStrictFilter = isStrictMusicPlaylist
                            )
                            if (passesFilter && seenIds.add(videoId)) {
                                out.add(
                                    MediaTrackInfo(
                                        id = videoId,
                                        title = finalTitle,
                                        artist = finalArtist,
                                        album = playlistTitle,
                                        durationSeconds = durationSec,
                                        thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                        sourceUrl = "https://www.youtube.com/watch?v=$videoId",
                                        streamUrl = ""
                                    )
                                )
                            }
                        }
                    }
                    return
                }

                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k == "musicCarouselShelfRenderer" ||
                        k == "automixPreviewVideoRenderer" ||
                        k == "musicTwoRowItemRenderer" ||
                        k == "gridRenderer" ||
                        k == "relatedChipCloudRenderer" ||
                        k == "secondaryResults" ||
                        k == "watchNextSecondaryResultsRenderer"
                    ) {
                        continue
                    }
                    collectWebPlaylistVideoRenderersScoped(node.opt(k), playlistTitle, out, seenIds, isStrictMusicPlaylist)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectWebPlaylistVideoRenderersScoped(node.opt(i), playlistTitle, out, seenIds, isStrictMusicPlaylist)
                }
            }
        }
    }

    private fun fetchRealTrackTitleAndArtist(videoId: String, cookies: List<CookieItem>): Pair<String, String> {
        try {
            val oembedUrl = URL("https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json")
            val conn = (oembedUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
            }
            if (conn.responseCode in 200..299) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                conn.disconnect()
                val json = JSONObject(body)
                val title = json.optString("title", "").trim()
                val author = json.optString("author_name", "").removeSuffix(" - Topic").trim()
                if (title.isNotBlank()) {
                    return title to author.ifBlank { "YouTube Music" }
                }
            } else {
                conn.disconnect()
            }
        } catch (_: Exception) {
        }
        return videoId to "YouTube Music"
    }

    private fun extractRunsText(obj: JSONObject?): String {
        if (obj == null) return ""
        val simple = obj.optString("simpleText", "")
        if (simple.isNotBlank()) return simple
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text", "").orEmpty())
        }
        return sb.toString().trim()
    }

    private fun parseDurationText(text: String): Long {
        if (text.isBlank()) return 0L
        val parts = text.trim().split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60L + parts[1]
            3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
            else -> 0L
        }
    }

    companion object {
        private const val TAG = "YouTubeStreamResolver"
    }
}

/**
 * 4-Katmanlı Agresif YouTube Music (LM) Doğrulayıcısı:
 * YouTube Music Beğenilenler (LM / VLLM) listesi taranırken araya karışan
 * müzik dışı videoları (vlog, podcast, kesit, yayın, shorts, fragman vb.) eler.
 */
object StrictMusicTrackValidator {
    private val VALID_VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

    private val GHOST_OR_DELETED_TITLES = listOf(
        "deleted video",
        "[deleted video]",
        "private video",
        "[private video]",
        "silinmiş video",
        "gizli video",
        "unavailable video",
        "[unavailable video]"
    )

    private val ALLOWED_MUSIC_VIDEO_TYPES = setOf(
        "MUSIC_VIDEO_TYPE_ATV",
        "MUSIC_VIDEO_TYPE_OMV",
        "MUSIC_VIDEO_TYPE_OFFICIAL_SOURCE_MUSIC"
    )

    private val NON_MUSIC_BLACKLIST_KEYWORDS = listOf(
        "#shorts",
        "#short",
        "podcast",
        "vlog",
        "gameplay",
        "let's play",
        "lets play",
        "walkthrough",
        "bölüm",
        "fragman",
        "kesit",
        "canlı yayın",
        "canli yayin",
        "inceleme",
        "reaction",
        "röportaj",
        "roportaj",
        "full izle",
        "episode",
        "trailer",
        "teaser",
        "interview",
        "highlights",
        "maç özeti",
        "mac ozeti",
        "konuk",
        "sohbet",
        "belgesel",
        "unboxing",
        "kutu açılımı",
        "haber bülteni"
    )

    fun isValidVideoId(videoId: String?): Boolean {
        if (videoId.isNullOrBlank()) return false
        return VALID_VIDEO_ID_REGEX.matches(videoId.trim())
    }

    fun isGhostOrDeletedTitle(title: String?): Boolean {
        if (title.isNullOrBlank()) return true
        val lower = title.trim().lowercase(java.util.Locale.getDefault())
        return GHOST_OR_DELETED_TITLES.any { lower == it || lower.startsWith(it) }
    }

    fun isStrictMusicTrack(
        title: String,
        artist: String,
        durationSeconds: Long,
        musicVideoType: String?,
        enforceStrictFilter: Boolean
    ): Boolean {
        if (isGhostOrDeletedTitle(title)) return false
        if (!enforceStrictFilter) return true

        // Katman 2: musicVideoType kontrolü (WEB_REMIX ATV / OMV doğrulaması)
        if (!musicVideoType.isNullOrBlank()) {
            if (musicVideoType == "MUSIC_VIDEO_TYPE_PODCAST_EPISODE") {
                return false
            }
            if (musicVideoType !in ALLOWED_MUSIC_VIDEO_TYPES &&
                musicVideoType != "MUSIC_VIDEO_TYPE_UGC"
            ) {
                return false
            }
        }

        // Katman 3: Süre Sınırı (60 saniye - 10 dakika [600 saniye] kuralı)
        if (durationSeconds > 0L && (durationSeconds < 60L || durationSeconds > 600L)) {
            return false
        }

        // Katman 4: Kategori ve Başlık Kara Listesi
        val combinedLower = "${title.lowercase(java.util.Locale.getDefault())} ${artist.lowercase(java.util.Locale.getDefault())}"
        for (keyword in NON_MUSIC_BLACKLIST_KEYWORDS) {
            if (combinedLower.contains(keyword)) {
                return false
            }
        }

        // Shorts kelimesi kelime sınırında kontrol edilir
        if (Regex("""\bshorts?\b""", RegexOption.IGNORE_CASE).containsMatchIn(title)) {
            return false
        }

        return true
    }
}

