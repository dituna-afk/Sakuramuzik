package com.example.downloader

import com.example.model.CookieItem
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bağımsız (UI-Free) Yerel İndirme Motoru:
 * - Hiçbir Android UI bileşenine, diyaloga, WebView'a veya üçüncü parti eklentiye (Morphe/Scrobble vb.) bağımlı değildir.
 * - Saf Kotlin, Coroutines ve HttpURLConnection kullanarak:
 *   1) Tekil YouTube / YouTube Music URL veya videoId'lerini InnerTube Player API ile çözümler.
 *   2) YouTube Music Beğenilen Şarkılar (VLLM) listesini doğrudan `https://music.youtube.com/youtubei/v1/browse`
 *      üzerinden `clientName = "WEB_REMIX"` (Client ID: 67) ve `browseId = "VLLM"` ile sunucu tarafında
 *      filtrelenmiş saf müzik listesi olarak çeker.
 *   3) Çözümlenen ses akışını (`audio/mp4` / `audio/webm`) HttpURLConnection ile asenkron olarak diske yazar.
 */
class DownloadEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val parallelConnections: Int = 4
) {

    sealed class EngineState {
        data object Idle : EngineState()
        data class Resolving(val inputUrlOrId: String) : EngineState()
        data class Downloading(
            val videoId: String,
            val title: String,
            val downloadedBytes: Long,
            val totalBytes: Long,
            val progressPercent: Float,
            val speedBytesPerSec: Long
        ) : EngineState()
        data class Completed(val trackInfo: MediaTrackInfo, val file: File) : EngineState()
        data class Failed(val reason: String, val cause: Throwable? = null) : EngineState()
        data object Cancelled : EngineState()
    }

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
        _state.value = EngineState.Cancelled
    }

    fun reset() {
        cancelled.set(false)
        _state.value = EngineState.Idle
    }

    /**
     * Verilen URL veya Video ID'yi InnerTube üzerinden çözümleyip hedef klasöre asenkron olarak indirir.
     * İlerleme durumunu hem [state] StateFlow'una hem de opsiyonel [onProgress] callback'ine bildirir.
     */
    suspend fun downloadFromUrl(
        urlOrVideoId: String,
        outputDirectory: File,
        preferredFileName: String? = null,
        rawCookieHeader: String? = null,
        onProgress: ((Float, Long, Long) -> Unit)? = null
    ): Result<Pair<MediaTrackInfo, File>> = withContext(ioDispatcher) {
        cancelled.set(false)
        try {
            val cleanInput = urlOrVideoId.trim()
            if (cleanInput.isEmpty()) {
                throw IllegalArgumentException("İndirilecek URL veya video ID boş olamaz.")
            }

            _state.value = EngineState.Resolving(cleanInput)

            val videoId = extractVideoId(cleanInput)
                ?: throw IllegalArgumentException("Geçerli bir YouTube / YT Music video kimliği bulunamadı: $cleanInput")

            val trackInfo = resolveStreamViaInnerTube(videoId, rawCookieHeader)
                ?: throw IllegalStateException("InnerTube API üzerinden ses akışı çözümlenemedi ($videoId).")

            if (cancelled.get()) {
                _state.value = EngineState.Cancelled
                throw CancellationException("İndirme iptal edildi.")
            }

            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs()
            }

            val ext = when {
                trackInfo.mimeType.contains("webm", ignoreCase = true) ||
                    trackInfo.mimeType.contains("opus", ignoreCase = true) ||
                    trackInfo.mimeType.contains("ogg", ignoreCase = true) -> "opus"
                trackInfo.mimeType.contains("mpeg", ignoreCase = true) -> "mp3"
                else -> "m4a"
            }

            val safeBaseName = sanitizeFileName(
                preferredFileName?.takeIf { it.isNotBlank() }
                    ?: "${trackInfo.artist} - ${trackInfo.title} [${trackInfo.id}]"
            )
            val targetFile = File(outputDirectory, "$safeBaseName.$ext")

            val downloadedFile = writeStreamToDisk(
                streamUrl = trackInfo.streamUrl,
                destinationFile = targetFile,
                expectedBytes = trackInfo.contentLengthBytes,
                rawCookieHeader = rawCookieHeader,
                onProgress = { downloaded, total, speed ->
                    val pct = if (total > 0L) {
                        ((downloaded.toDouble() / total.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f)
                    } else 0f
                    _state.value = EngineState.Downloading(
                        videoId = trackInfo.id,
                        title = trackInfo.title,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        progressPercent = pct,
                        speedBytesPerSec = speed
                    )
                    onProgress?.invoke(pct, downloaded, total)
                }
            )

            val completedState = EngineState.Completed(trackInfo, downloadedFile)
            _state.value = completedState
            Result.success(trackInfo to downloadedFile)
        } catch (ce: CancellationException) {
            _state.value = EngineState.Cancelled
            Result.failure(ce)
        } catch (t: Throwable) {
            val msg = t.localizedMessage ?: "İndirme sırasında beklenmeyen hata oluştu."
            _state.value = EngineState.Failed(msg, t)
            Result.failure(t)
        }
    }

    /**
     * Flow tabanlı reaktif indirme: Her adımda [EngineState] yayınlar.
     */
    fun downloadAsFlow(
        urlOrVideoId: String,
        outputDirectory: File,
        preferredFileName: String? = null,
        rawCookieHeader: String? = null
    ): Flow<EngineState> = flow {
        emit(EngineState.Resolving(urlOrVideoId))
        val result = downloadFromUrl(
            urlOrVideoId = urlOrVideoId,
            outputDirectory = outputDirectory,
            preferredFileName = preferredFileName,
            rawCookieHeader = rawCookieHeader
        )
        result.fold(
            onSuccess = { (track, file) -> emit(EngineState.Completed(track, file)) },
            onFailure = { err ->
                if (err is CancellationException) {
                    emit(EngineState.Cancelled)
                } else {
                    emit(EngineState.Failed(err.localizedMessage ?: "İndirme başarısız", err))
                }
            }
        )
    }.flowOn(ioDispatcher)

    /**
     * YouTube Music Beğenilen Şarkılar (Liked Songs) listesini doğrudan Google sunucularında filtreleyerek çeker.
     *
     * Neden & Çözüm:
     * Standart YouTube API'sine (`www.youtube.com`) istek atıldığında oyun, vlog ve müzik videoları
     * aynı `LM` / `VLLM` ID'si altında karışık döner.
     * Bu metot doğrudan `https://music.youtube.com/youtubei/v1/browse` adresine:
     * - `browseId = "VLLM"`
     * - `clientName = "WEB_REMIX"`
     * - `X-YouTube-Client-Name: 67` (Client ID: 67)
     * parametreleriyle istek atar. Böylece Google sunucuları yalnızca saf YouTube Music şarkılarını döndürür.
     */
    suspend fun fetchPureYtMusicLikedSongs(
        rawCookieHeader: String,
        maxPages: Int = 35
    ): Result<PlaylistResolutionResult> = withContext(ioDispatcher) {
        runCatching {
            val cleanCookieHeader = rawCookieHeader.trim()
            require(cleanCookieHeader.isNotEmpty()) {
                "YouTube Music beğenilen şarkılarını çekmek için oturum çerezleri (SAPISID) gereklidir."
            }

            val sapisid = extractSapisidFromCookieHeader(cleanCookieHeader)
                ?: throw IllegalStateException("Çerezlerde SAPISID veya __Secure-3PAPISID bulunamadı.")

            val tracks = mutableListOf<MediaTrackInfo>()
            val seenVideoIds = HashSet<String>()
            var continuationToken: String? = null
            val visitedTokens = HashSet<String>()
            var resolvedTitle = "Beğenilen Şarkılar (YT Music)"
            var resolvedThumbnail: String? = null
            var currentPage = 0

            while (currentPage < maxPages && isActive && !cancelled.get()) {
                val sapisidHash = generateSapisidHash(sapisid, YT_MUSIC_ORIGIN)
                val endpoint = URL(YT_MUSIC_BROWSE_ENDPOINT)
                val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", WEB_REMIX_USER_AGENT)
                    setRequestProperty("Origin", YT_MUSIC_ORIGIN)
                    setRequestProperty("Referer", "$YT_MUSIC_ORIGIN/")
                    setRequestProperty("X-Origin", YT_MUSIC_ORIGIN)
                    setRequestProperty("X-Goog-AuthUser", "0")
                    setRequestProperty("X-Goog-Api-Format-Version", "2")
                    // Client ID 67 = WEB_REMIX (YouTube Music Web İstemcisi)
                    setRequestProperty("X-YouTube-Client-Name", WEB_REMIX_CLIENT_ID)
                    setRequestProperty("X-YouTube-Client-Version", WEB_REMIX_CLIENT_VERSION)
                    setRequestProperty("Cookie", cleanCookieHeader)
                    setRequestProperty("Authorization", sapisidHash)
                }

                val payload = buildWebRemixBrowsePayload(
                    browseId = "VLLM",
                    continuation = continuationToken
                )

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    val errorBody = runCatching {
                        conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    }.getOrDefault("")
                    conn.disconnect()
                    if (currentPage == 0) {
                        throw IllegalStateException(
                            "YT Music WEB_REMIX browse isteği başarısız oldu (HTTP $responseCode). $errorBody"
                        )
                    }
                    break
                }

                val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                    it.readText()
                }
                conn.disconnect()

                val root = JSONObject(responseText)
                if (currentPage == 0) {
                    val headerTitle = extractMusicHeaderTitle(root)
                    if (headerTitle.isNotBlank()) {
                        resolvedTitle = headerTitle
                    }
                    resolvedThumbnail = extractMusicHeaderThumbnail(root)
                }

                val beforeCount = tracks.size
                parseWebRemixTracks(
                    node = root,
                    albumLabel = resolvedTitle,
                    out = tracks,
                    seenIds = seenVideoIds,
                    expectedPlaylistId = "VLLM"
                )

                val nextContinuation = findContinuationToken(root)
                if (nextContinuation.isNullOrBlank() || !visitedTokens.add(nextContinuation)) {
                    break
                }
                if (currentPage > 1 && tracks.size == beforeCount) {
                    break
                }

                continuationToken = nextContinuation
                currentPage++
            }

            val finalCover = resolvedThumbnail ?: tracks.firstOrNull()?.thumbnailUrl
            val stampedTracks = tracks.map { track ->
                track.copy(
                    album = resolvedTitle,
                    playlistId = "VLLM",
                    playlistTitle = resolvedTitle,
                    playlistCoverUrl = finalCover
                )
            }

            PlaylistResolutionResult(
                playlistId = "VLLM",
                title = resolvedTitle,
                tracks = stampedTracks,
                thumbnailUrl = finalCover
            )
        }
    }

    /**
     * Herhangi bir YouTube Music çalma listesini (`VL...` veya `PL...` veya `VLLM`)
     * `music.youtube.com/youtubei/v1/browse` + `WEB_REMIX` (Client ID: 67) ile çözümler.
     */
    suspend fun fetchYtMusicPlaylist(
        playlistId: String,
        rawCookieHeader: String? = null,
        maxPages: Int = 35
    ): Result<PlaylistResolutionResult> = withContext(ioDispatcher) {
        runCatching {
            val cleanId = playlistId.trim()
            val normalizedBrowseId = when {
                cleanId.equals("LM", ignoreCase = true) ||
                    cleanId.equals("VLLM", ignoreCase = true) ||
                    cleanId.equals("FEmusic_liked_videos", ignoreCase = true) -> "VLLM"
                cleanId.startsWith("VL") -> cleanId
                else -> "VL$cleanId"
            }

            if (normalizedBrowseId == "VLLM" && !rawCookieHeader.isNullOrBlank()) {
                return@withContext fetchPureYtMusicLikedSongs(rawCookieHeader, maxPages)
            }

            val cookieStr = rawCookieHeader?.trim().orEmpty()
            val sapisid = if (cookieStr.isNotEmpty()) extractSapisidFromCookieHeader(cookieStr) else null

            val tracks = mutableListOf<MediaTrackInfo>()
            val seenVideoIds = HashSet<String>()
            var continuationToken: String? = null
            val visitedTokens = HashSet<String>()
            var resolvedTitle = "YouTube Music Listesi"
            var resolvedThumbnail: String? = null
            var currentPage = 0

            while (currentPage < maxPages && isActive && !cancelled.get()) {
                val endpoint = URL(YT_MUSIC_BROWSE_ENDPOINT)
                val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", WEB_REMIX_USER_AGENT)
                    setRequestProperty("Origin", YT_MUSIC_ORIGIN)
                    setRequestProperty("Referer", "$YT_MUSIC_ORIGIN/")
                    setRequestProperty("X-Origin", YT_MUSIC_ORIGIN)
                    setRequestProperty("X-Goog-AuthUser", "0")
                    setRequestProperty("X-Goog-Api-Format-Version", "2")
                    setRequestProperty("X-YouTube-Client-Name", WEB_REMIX_CLIENT_ID)
                    setRequestProperty("X-YouTube-Client-Version", WEB_REMIX_CLIENT_VERSION)
                    if (cookieStr.isNotEmpty()) {
                        setRequestProperty("Cookie", cookieStr)
                    }
                    if (!sapisid.isNullOrBlank()) {
                        setRequestProperty("Authorization", generateSapisidHash(sapisid, YT_MUSIC_ORIGIN))
                    }
                }

                val payload = buildWebRemixBrowsePayload(
                    browseId = normalizedBrowseId,
                    continuation = continuationToken
                )

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    conn.disconnect()
                    break
                }

                val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                    it.readText()
                }
                conn.disconnect()

                val root = JSONObject(responseText)
                if (currentPage == 0) {
                    val headerTitle = extractMusicHeaderTitle(root)
                    if (headerTitle.isNotBlank()) {
                        resolvedTitle = headerTitle
                    }
                    resolvedThumbnail = extractMusicHeaderThumbnail(root)
                }

                val beforeCount = tracks.size
                parseWebRemixTracks(
                    node = root,
                    albumLabel = resolvedTitle,
                    out = tracks,
                    seenIds = seenVideoIds,
                    expectedPlaylistId = cleanId
                )

                val nextContinuation = findContinuationToken(root)
                if (nextContinuation.isNullOrBlank() || !visitedTokens.add(nextContinuation)) {
                    break
                }
                if (currentPage > 1 && tracks.size == beforeCount) {
                    break
                }

                continuationToken = nextContinuation
                currentPage++
            }

            val finalCover = resolvedThumbnail ?: tracks.firstOrNull()?.thumbnailUrl
            val stampedTracks = tracks.map { track ->
                track.copy(
                    album = resolvedTitle,
                    playlistId = cleanId,
                    playlistTitle = resolvedTitle,
                    playlistCoverUrl = finalCover
                )
            }

            PlaylistResolutionResult(
                playlistId = cleanId,
                title = resolvedTitle,
                tracks = stampedTracks,
                thumbnailUrl = finalCover
            )
        }
    }

    /**
     * Kullanıcının YouTube Music kütüphanesindeki tüm oynatma listelerini (`FEmusic_liked_playlists` / `FEmusic_library_landing`)
     * tek tuşla çeker; her listenin adını, kaç şarkı içerdiğini ve kapak görselini döndürür.
     */
    suspend fun fetchUserLibraryPlaylists(
        rawCookieHeader: String
    ): Result<List<RemotePlaylistSummary>> = withContext(ioDispatcher) {
        runCatching {
            val cleanCookieHeader = rawCookieHeader.trim()
            require(cleanCookieHeader.isNotEmpty()) {
                "Kütüphanedeki oynatma listelerini görmek için YouTube Music oturum çerezleri gereklidir."
            }
            val sapisid = extractSapisidFromCookieHeader(cleanCookieHeader)
                ?: throw IllegalStateException("Çerezlerde SAPISID veya __Secure-3PAPISID bulunamadı.")

            val discovered = LinkedHashMap<String, RemotePlaylistSummary>()
            val browseEndpoints = listOf("FEmusic_liked_playlists", "FEmusic_library_landing")

            for (browseId in browseEndpoints) {
                val root = executeWebRemixBrowseRequest(
                    browseId = browseId,
                    continuation = null,
                    cleanCookieHeader = cleanCookieHeader,
                    sapisid = sapisid
                ) ?: continue

                val pageItems = mutableListOf<RemotePlaylistSummary>()
                parseLibraryPlaylistsFromNode(root, pageItems)
                for (item in pageItems) {
                    if (!discovered.containsKey(item.playlistId)) {
                        discovered[item.playlistId] = item
                    }
                }
                if (discovered.size >= 3) break
            }

            // Enrich playlists that don't have an explicit song count in the grid subtitle
            val enriched = coroutineScope {
                discovered.values.mapIndexed { index, summary ->
                    async(ioDispatcher) {
                        if (summary.trackCount != null && summary.trackCount > 0) {
                            summary
                        } else if (index < 18) {
                            enrichPlaylistTrackCount(summary, cleanCookieHeader, sapisid)
                        } else {
                            summary
                        }
                    }
                }.awaitAll()
            }

            enriched
        }
    }

    private fun executeWebRemixBrowseRequest(
        browseId: String,
        continuation: String?,
        cleanCookieHeader: String,
        sapisid: String
    ): JSONObject? {
        return try {
            val sapisidHash = generateSapisidHash(sapisid, YT_MUSIC_ORIGIN)
            val endpoint = URL(YT_MUSIC_BROWSE_ENDPOINT)
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", WEB_REMIX_USER_AGENT)
                setRequestProperty("Origin", YT_MUSIC_ORIGIN)
                setRequestProperty("Referer", "$YT_MUSIC_ORIGIN/")
                setRequestProperty("X-Origin", YT_MUSIC_ORIGIN)
                setRequestProperty("X-Goog-AuthUser", "0")
                setRequestProperty("X-Goog-Api-Format-Version", "2")
                setRequestProperty("X-YouTube-Client-Name", WEB_REMIX_CLIENT_ID)
                setRequestProperty("X-YouTube-Client-Version", WEB_REMIX_CLIENT_VERSION)
                setRequestProperty("Cookie", cleanCookieHeader)
                setRequestProperty("Authorization", sapisidHash)
            }
            val payload = buildWebRemixBrowsePayload(
                browseId = browseId,
                continuation = continuation
            )
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return null
            }
            val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }
            conn.disconnect()
            JSONObject(responseText)
        } catch (_: Throwable) {
            null
        }
    }

    private fun enrichPlaylistTrackCount(
        summary: RemotePlaylistSummary,
        cleanCookieHeader: String,
        sapisid: String
    ): RemotePlaylistSummary {
        val root = executeWebRemixBrowseRequest(
            browseId = summary.browseId,
            continuation = null,
            cleanCookieHeader = cleanCookieHeader,
            sapisid = sapisid
        ) ?: return summary

        val headerCount = extractMusicHeaderTrackCount(root)
        val parsedTracks = mutableListOf<MediaTrackInfo>()
        parseWebRemixTracks(
            node = root,
            albumLabel = summary.title,
            out = parsedTracks,
            seenIds = HashSet(),
            expectedPlaylistId = summary.playlistId
        )
        val hasMorePages = !findContinuationToken(root).isNullOrBlank()
        val finalCount = headerCount ?: parsedTracks.size.takeIf { it > 0 }
        val finalLabel = when {
            headerCount != null -> "$headerCount şarkı"
            parsedTracks.isNotEmpty() && hasMorePages -> "${parsedTracks.size}+ şarkı"
            parsedTracks.isNotEmpty() -> "${parsedTracks.size} şarkı"
            else -> summary.trackCountLabel
        }
        val finalThumb = summary.thumbnailUrl ?: extractMusicHeaderThumbnail(root) ?: parsedTracks.firstOrNull()?.thumbnailUrl
        return summary.copy(
            trackCount = finalCount,
            trackCountLabel = finalLabel,
            thumbnailUrl = finalThumb
        )
    }

    /**
     * InnerTube Player API'si üzerinden tekil videoId'nin en yüksek kaliteli ses akışını (`audio/mp4` / `audio/webm`) çözümler.
     */
    suspend fun resolveStreamViaInnerTube(
        videoId: String,
        rawCookieHeader: String? = null
    ): MediaTrackInfo? = withContext(ioDispatcher) {
        val cleanId = extractVideoId(videoId) ?: videoId.trim()
        if (cleanId.isBlank()) return@withContext null

        val cached = StreamCache.getTrack(cleanId)
        if (cached != null && cached.streamUrl.startsWith("http")) {
            return@withContext cached
        }

        val clients = listOf(
            PlayerClientConfig(
                endpointUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                clientName = "ANDROID_VR",
                clientVersion = "1.60.19",
                clientIdHeader = "28",
                userAgent = ANDROID_VR_USER_AGENT,
                androidSdkVersion = 32,
                origin = "https://www.youtube.com"
            ),
            PlayerClientConfig(
                endpointUrl = "https://music.youtube.com/youtubei/v1/player?prettyPrint=false",
                clientName = "ANDROID_MUSIC",
                clientVersion = "7.27.52",
                clientIdHeader = "21",
                userAgent = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 14) gzip",
                androidSdkVersion = 34,
                origin = YT_MUSIC_ORIGIN
            ),
            PlayerClientConfig(
                endpointUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                clientName = "IOS",
                clientVersion = "19.45.4",
                clientIdHeader = "5",
                userAgent = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1 like Mac OS X)",
                androidSdkVersion = null,
                origin = "https://www.youtube.com"
            )
        )

        val sapisid = rawCookieHeader?.let { extractSapisidFromCookieHeader(it) }

        for (client in clients) {
            try {
                val conn = (URL(client.endpointUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("User-Agent", client.userAgent)
                    setRequestProperty("X-Goog-Api-Format-Version", "2")
                    setRequestProperty("X-YouTube-Client-Name", client.clientIdHeader)
                    setRequestProperty("X-YouTube-Client-Version", client.clientVersion)
                    setRequestProperty("Origin", client.origin)
                    if (!rawCookieHeader.isNullOrBlank()) {
                        setRequestProperty("Cookie", rawCookieHeader)
                        if (!sapisid.isNullOrBlank()) {
                            setRequestProperty("Authorization", generateSapisidHash(sapisid, client.origin))
                            setRequestProperty("X-Goog-AuthUser", "0")
                        }
                    }
                }

                val clientJson = JSONObject().apply {
                    put("clientName", client.clientName)
                    put("clientVersion", client.clientVersion)
                    put("hl", "tr")
                    put("gl", "TR")
                    if (client.androidSdkVersion != null) {
                        put("androidSdkVersion", client.androidSdkVersion)
                    }
                }

                val payload = JSONObject().apply {
                    put("videoId", cleanId)
                    put("context", JSONObject().put("client", clientJson))
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
                    continue
                }

                val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                    it.readText()
                }
                conn.disconnect()

                val root = JSONObject(body)
                val status = root.optJSONObject("playabilityStatus")?.optString("status", "").orEmpty()
                if (status != "OK") continue

                val videoDetails = root.optJSONObject("videoDetails") ?: JSONObject()
                val title = videoDetails.optString("title", "Bilinmeyen Şarkı").ifBlank { "Bilinmeyen Şarkı" }
                val author = videoDetails.optString("author", "Bilinmeyen Sanatçı")
                    .removeSuffix(" - Topic")
                    .trim()
                    .ifBlank { "Bilinmeyen Sanatçı" }
                val durationSeconds = videoDetails.optString("lengthSeconds", "0").toLongOrNull() ?: 0L

                var thumbnailUrl = "https://i.ytimg.com/vi/$cleanId/hqdefault.jpg"
                val thumbs = videoDetails.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                if (thumbs != null && thumbs.length() > 0) {
                    val lastThumb = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "")
                    if (!lastThumb.isNullOrBlank()) {
                        thumbnailUrl = lastThumb
                    }
                }

                val streamingData = root.optJSONObject("streamingData") ?: continue
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

                var bestFormat: JSONObject? = null
                var bestScore = -1
                for (fmt in candidates) {
                    val url = fmt.optString("url", "")
                    if (url.isBlank()) continue
                    val mime = fmt.optString("mimeType", "")
                    val isAudio = mime.startsWith("audio/")
                    val isM4a = mime.contains("audio/mp4")
                    val bitrate = fmt.optInt("bitrate", 0)
                    val score = (if (isAudio) 1_000_000 else 0) +
                        (if (isM4a) 500_000 else 0) +
                        (bitrate / 100)
                    if (score > bestScore) {
                        bestScore = score
                        bestFormat = fmt
                    }
                }

                val chosen = bestFormat ?: continue
                val streamUrl = chosen.optString("url", "")
                if (streamUrl.isBlank()) continue

                val mimeType = chosen.optString("mimeType", "audio/mp4").substringBefore(";").trim()
                val bitrateKbps = (chosen.optInt("bitrate", 128_000) / 1000).coerceAtLeast(64)
                val contentLength = chosen.optString("contentLength", "-1").toLongOrNull() ?: -1L

                val track = MediaTrackInfo(
                    id = cleanId,
                    title = title,
                    artist = author,
                    album = "YouTube Music",
                    durationSeconds = durationSeconds,
                    thumbnailUrl = thumbnailUrl,
                    sourceUrl = "https://music.youtube.com/watch?v=$cleanId",
                    streamUrl = streamUrl,
                    mimeType = mimeType,
                    bitrateKbps = bitrateKbps,
                    contentLengthBytes = contentLength
                )
                StreamCache.putTrack(cleanId, track)
                return@withContext track
            } catch (_: Throwable) {
            }
        }
        null
    }

    /**
     * Saf HttpURLConnection kullanarak ses akışını diske yazar.
     * Sunucu `Range` destekliyorsa ve boyut biliniyorsa paralel parçalara bölerek,
     * aksi halde tekil BufferedInputStream -> BufferedOutputStream akışı ile indirir.
     */
    suspend fun writeStreamToDisk(
        streamUrl: String,
        destinationFile: File,
        expectedBytes: Long = -1L,
        rawCookieHeader: String? = null,
        onProgress: ((downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit)? = null
    ): File = withContext(ioDispatcher) {
        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.part")
        if (tempFile.exists()) tempFile.delete()

        try {
            val probe = probeStream(streamUrl, rawCookieHeader)
            val totalLength = if (probe.contentLength > 0L) probe.contentLength else expectedBytes

            if (probe.supportsRange && totalLength > 512_000L && parallelConnections > 1) {
                downloadRangeParallel(
                    url = probe.resolvedUrl,
                    tempFile = tempFile,
                    totalBytes = totalLength,
                    rawCookieHeader = rawCookieHeader,
                    onProgress = onProgress
                )
            } else {
                downloadSingleConnection(
                    url = probe.resolvedUrl,
                    tempFile = tempFile,
                    expectedTotalBytes = totalLength,
                    rawCookieHeader = rawCookieHeader,
                    onProgress = onProgress
                )
            }

            if (cancelled.get()) {
                tempFile.delete()
                throw CancellationException("İndirme kullanıcı tarafından iptal edildi.")
            }

            if (!tempFile.exists() || tempFile.length() < MIN_AUDIO_BYTES) {
                val badLen = if (tempFile.exists()) tempFile.length() else 0L
                tempFile.delete()
                throw IllegalStateException("İndirilen ses dosyası eksik veya bozuk ($badLen bayt).")
            }

            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }
            onProgress?.invoke(destinationFile.length(), destinationFile.length(), 0L)
            destinationFile
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        }
    }

    private data class ProbeInfo(
        val resolvedUrl: String,
        val contentLength: Long,
        val supportsRange: Boolean
    )

    private fun probeStream(url: String, rawCookieHeader: String?): ProbeInfo {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", ANDROID_VR_USER_AGENT)
            setRequestProperty("Range", "bytes=0-1")
            if (!rawCookieHeader.isNullOrBlank()) {
                setRequestProperty("Cookie", rawCookieHeader)
            }
        }
        return try {
            val code = conn.responseCode
            val finalUrl = conn.url?.toString() ?: url
            val contentType = conn.contentType.orEmpty()
            if (contentType.contains("text/html", ignoreCase = true)) {
                throw IllegalStateException("Sunucu ses akışı yerine HTML yanıtı döndürdü.")
            }
            val contentRange = conn.getHeaderField("Content-Range").orEmpty()
            val acceptRanges = conn.getHeaderField("Accept-Ranges").orEmpty()

            var total = -1L
            if (contentRange.contains("/")) {
                total = contentRange.substringAfterLast("/").trim().toLongOrNull() ?: -1L
            }
            if (total <= 0L) {
                total = conn.contentLengthLong
            }

            val rangeOk = code == HttpURLConnection.HTTP_PARTIAL ||
                acceptRanges.equals("bytes", ignoreCase = true)

            ProbeInfo(
                resolvedUrl = finalUrl,
                contentLength = total,
                supportsRange = rangeOk
            )
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun downloadRangeParallel(
        url: String,
        tempFile: File,
        totalBytes: Long,
        rawCookieHeader: String?,
        onProgress: ((Long, Long, Long) -> Unit)?
    ) = coroutineScope {
        RandomAccessFile(tempFile, "rw").use { raf ->
            raf.setLength(totalBytes)
        }

        val chunkSize = totalBytes / parallelConnections
        val downloadedAtomic = AtomicLong(0L)
        val startTime = System.currentTimeMillis()

        val tasks = (0 until parallelConnections).map { index ->
            val startByte = index * chunkSize
            val endByte = if (index == parallelConnections - 1) totalBytes - 1L else (startByte + chunkSize - 1L)
            async(ioDispatcher) {
                downloadChunkWithRetry(
                    url = url,
                    tempFile = tempFile,
                    startByte = startByte,
                    endByte = endByte,
                    rawCookieHeader = rawCookieHeader,
                    onBytesRead = { delta ->
                        val current = downloadedAtomic.addAndGet(delta)
                        val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                        val speed = (current * 1000L) / elapsedMs
                        onProgress?.invoke(current, totalBytes, speed)
                    },
                    onReset = { rollback ->
                        downloadedAtomic.addAndGet(-rollback)
                    }
                )
            }
        }
        tasks.awaitAll()
    }

    private fun downloadChunkWithRetry(
        url: String,
        tempFile: File,
        startByte: Long,
        endByte: Long,
        rawCookieHeader: String?,
        onBytesRead: (Long) -> Unit,
        onReset: (Long) -> Unit
    ) {
        val expectedChunkBytes = (endByte - startByte + 1L).coerceAtLeast(1L)
        var attempt = 0
        var writtenInChunk = 0L

        while (attempt < 3 && !cancelled.get()) {
            attempt++
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    setRequestProperty("User-Agent", ANDROID_VR_USER_AGENT)
                    setRequestProperty("Range", "bytes=$startByte-$endByte")
                    if (!rawCookieHeader.isNullOrBlank()) {
                        setRequestProperty("Cookie", rawCookieHeader)
                    }
                }

                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_PARTIAL && code != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("HTTP $code")
                }

                BufferedInputStream(conn.inputStream, 32 * 1024).use { input ->
                    RandomAccessFile(tempFile, "rw").use { raf ->
                        raf.seek(startByte)
                        val buffer = ByteArray(32 * 1024)
                        var remaining = expectedChunkBytes
                        while (remaining > 0L && !cancelled.get()) {
                            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                            val read = input.read(buffer, 0, toRead)
                            if (read == -1) break
                            raf.write(buffer, 0, read)
                            remaining -= read
                            writtenInChunk += read
                            onBytesRead(read.toLong())
                        }
                    }
                }

                if (writtenInChunk >= expectedChunkBytes || cancelled.get()) {
                    return
                }
            } catch (e: Exception) {
                if (attempt >= 3) throw e
                onReset(writtenInChunk)
                writtenInChunk = 0L
            } finally {
                conn?.disconnect()
            }
        }
    }

    private fun downloadSingleConnection(
        url: String,
        tempFile: File,
        expectedTotalBytes: Long,
        rawCookieHeader: String?,
        onProgress: ((Long, Long, Long) -> Unit)?
    ) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("User-Agent", ANDROID_VR_USER_AGENT)
            if (!rawCookieHeader.isNullOrBlank()) {
                setRequestProperty("Cookie", rawCookieHeader)
            }
        }

        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Sunucu HTTP $code yanıtı döndürdü.")
            }
            val total = if (expectedTotalBytes > 0L) expectedTotalBytes else conn.contentLengthLong
            var downloaded = 0L
            val startTime = System.currentTimeMillis()
            var lastEmit = 0L

            BufferedInputStream(conn.inputStream, 32 * 1024).use { input ->
                BufferedOutputStream(FileOutputStream(tempFile), 32 * 1024).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (!cancelled.get()) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 150L) {
                            lastEmit = now
                            val elapsedMs = (now - startTime).coerceAtLeast(1L)
                            val speed = (downloaded * 1000L) / elapsedMs
                            onProgress?.invoke(downloaded, total, speed)
                        }
                    }
                    output.flush()
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private data class PlayerClientConfig(
        val endpointUrl: String,
        val clientName: String,
        val clientVersion: String,
        val clientIdHeader: String,
        val userAgent: String,
        val androidSdkVersion: Int?,
        val origin: String
    )

    companion object {
        const val YT_MUSIC_ORIGIN = "https://music.youtube.com"
        const val YT_MUSIC_BROWSE_ENDPOINT = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
        const val YT_MUSIC_ACCOUNT_MENU_ENDPOINT = "https://music.youtube.com/youtubei/v1/account/account_menu?prettyPrint=false"

        /**
         * YouTube Music Web İstemcisi (WEB_REMIX) sabitleri:
         * Client ID 67 Google sunucularına isteğin doğrudan YT Music'ten geldiğini bildirir.
         */
        const val WEB_REMIX_CLIENT_NAME = "WEB_REMIX"
        const val WEB_REMIX_CLIENT_ID = "67"
        const val WEB_REMIX_CLIENT_VERSION = "1.20260101.01.00"

        private const val WEB_REMIX_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val ANDROID_VR_USER_AGENT =
            "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

        private const val MIN_AUDIO_BYTES = 16 * 1024L
        private val VALID_VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")

        /**
         * `music.youtube.com/youtubei/v1/browse` için `WEB_REMIX` (Client ID: 67) JSON gövdesi üretir.
         * İlk sayfada `browseId = "VLLM"`, devam sayfalarında `continuation` kullanılır.
         */
        fun buildWebRemixBrowsePayload(
            browseId: String = "VLLM",
            continuation: String? = null,
            hl: String = "tr",
            gl: String = "TR"
        ): JSONObject {
            val clientObj = JSONObject().apply {
                put("clientName", WEB_REMIX_CLIENT_NAME)
                put("clientVersion", WEB_REMIX_CLIENT_VERSION)
                put("hl", hl)
                put("gl", gl)
            }
            return JSONObject().apply {
                put("context", JSONObject().put("client", clientObj))
                if (continuation.isNullOrBlank()) {
                    put("browseId", browseId)
                } else {
                    put("continuation", continuation)
                }
            }
        }

        /**
         * Ham Cookie başlığından `SAPISID`, `__Secure-3PAPISID` veya `__Secure-1PAPISID` değerini ayıklar.
         */
        fun extractSapisidFromCookieHeader(rawCookieHeader: String): String? {
            var fallbackPapisid: String? = null
            for (segment in rawCookieHeader.split(";")) {
                val trimmed = segment.trim()
                val eq = trimmed.indexOf('=')
                if (eq <= 0) continue
                val key = trimmed.substring(0, eq).trim()
                val value = trimmed.substring(eq + 1).trim()
                if (value.isEmpty()) continue
                if (key == "SAPISID") {
                    return value
                }
                if (key == "__Secure-3PAPISID" || key == "__Secure-1PAPISID") {
                    fallbackPapisid = value
                }
            }
            return fallbackPapisid
        }

        /**
         * YouTube Music InnerTube API yetkilendirmesi için `SAPISIDHASH <timestamp>_<sha1>` üretir.
         */
        fun generateSapisidHash(sapisid: String, origin: String = YT_MUSIC_ORIGIN): String {
            val timestamp = System.currentTimeMillis() / 1000L
            val input = "$timestamp ${sapisid.trim()} $origin"
            val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
            val sha1Hex = digest.joinToString("") { "%02x".format(it) }
            return "SAPISIDHASH ${timestamp}_$sha1Hex"
        }

        fun extractVideoId(input: String): String? {
            val trimmed = input.trim()
            if (VALID_VIDEO_ID_REGEX.matches(trimmed)) {
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

        fun sanitizeFileName(raw: String): String {
            return raw
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(140)
                .ifBlank { "sakura_track" }
        }

        internal fun parseWebRemixTracks(
            node: Any?,
            albumLabel: String,
            out: MutableList<MediaTrackInfo>,
            seenIds: MutableSet<String>,
            expectedPlaylistId: String? = null
        ) {
            if (node == null) return
            val explicitContainers = mutableListOf<JSONObject>()
            collectExplicitPlaylistTrackContainers(node, explicitContainers)
            val normalizedExpectedId = expectedPlaylistId
                ?.trim()
                ?.removePrefix("VL")
                ?.takeIf { it.isNotBlank() }

            if (explicitContainers.isNotEmpty()) {
                for (container in explicitContainers) {
                    parseWebRemixTracksScoped(
                        node = container,
                        albumLabel = albumLabel,
                        out = out,
                        seenIds = seenIds,
                        normalizedExpectedPlaylistId = normalizedExpectedId,
                        insideExplicitPlaylistShelf = true
                    )
                }
            } else {
                parseWebRemixTracksScoped(
                    node = node,
                    albumLabel = albumLabel,
                    out = out,
                    seenIds = seenIds,
                    normalizedExpectedPlaylistId = normalizedExpectedId,
                    insideExplicitPlaylistShelf = false
                )
            }
        }

        private fun collectExplicitPlaylistTrackContainers(
            node: Any?,
            out: MutableList<JSONObject>
        ) {
            if (node == null) return
            when (node) {
                is JSONObject -> {
                    // Do not enter recommendation / carousel / grid / automix containers
                    if (isExcludedRecommendationKeyOrShelf(node)) return

                    val playlistShelf = node.optJSONObject("musicPlaylistShelfRenderer")
                        ?: node.optJSONObject("playlistVideoListRenderer")
                        ?: node.optJSONObject("musicPlaylistShelfContinuation")
                        ?: node.optJSONObject("playlistVideoListContinuation")
                        ?: node.optJSONObject("appendContinuationItemsAction")
                    if (playlistShelf != null) {
                        out.add(playlistShelf)
                        return
                    }

                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (isExcludedTraversalKey(k)) continue
                        collectExplicitPlaylistTrackContainers(node.opt(k), out)
                    }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) {
                        collectExplicitPlaylistTrackContainers(node.opt(i), out)
                    }
                }
            }
        }

        private fun isExcludedTraversalKey(key: String): Boolean {
            return key == "musicCarouselShelfRenderer" ||
                key == "automixPreviewVideoRenderer" ||
                key == "musicTwoRowItemRenderer" ||
                key == "gridRenderer" ||
                key == "relatedChipCloudRenderer" ||
                key == "musicCardShelfRenderer" ||
                key == "playerOverlayRenderer"
        }

        private fun isExcludedRecommendationKeyOrShelf(obj: JSONObject): Boolean {
            val shelf = obj.optJSONObject("musicShelfRenderer") ?: obj
            val headerTitle = extractRunsText(shelf.optJSONObject("title"))
                .ifBlank {
                    extractRunsText(
                        shelf.optJSONObject("header")
                            ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                            ?.optJSONObject("title")
                    )
                }
                .lowercase()
            if (headerTitle.isNotBlank()) {
                val suggestionKeywords = listOf(
                    "önerilen", "onerilen", "suggestion", "recommended",
                    "benzer", "similar", "automix", "you might also like",
                    "ilginizi çekebilecek", "radyo", "radio"
                )
                if (suggestionKeywords.any { headerTitle.contains(it) }) {
                    return true
                }
            }
            return false
        }

        private fun parseWebRemixTracksScoped(
            node: Any?,
            albumLabel: String,
            out: MutableList<MediaTrackInfo>,
            seenIds: MutableSet<String>,
            normalizedExpectedPlaylistId: String?,
            insideExplicitPlaylistShelf: Boolean
        ) {
            if (node == null) return
            when (node) {
                is JSONObject -> {
                    if (isExcludedRecommendationKeyOrShelf(node)) return

                    val mrlir = node.optJSONObject("musicResponsiveListItemRenderer")
                    if (mrlir != null) {
                        val displayPolicy = mrlir.optString("musicItemRendererDisplayPolicy", "")
                        val isGreyedOut = displayPolicy.contains("GREY_OUT", ignoreCase = true)

                        val watchEndpoint = mrlir.optJSONObject("overlay")
                            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                            ?.optJSONObject("content")
                            ?.optJSONObject("musicPlayButtonRenderer")
                            ?.optJSONObject("playNavigationEndpoint")
                            ?.optJSONObject("watchEndpoint")
                            ?: mrlir.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")

                        val playlistItemData = mrlir.optJSONObject("playlistItemData")
                        val playlistDataVideoId = playlistItemData?.optString("videoId", "").orEmpty().trim()
                        val watchVideoId = watchEndpoint?.optString("videoId", "").orEmpty().trim()
                        val videoId = playlistDataVideoId.ifBlank { watchVideoId }

                        val watchPlaylistId = watchEndpoint?.optString("playlistId", "").orEmpty()
                            .trim()
                            .removePrefix("VL")

                        val isAutomixWatchEndpoint = watchPlaylistId.startsWith("RDAMPL", ignoreCase = true) ||
                            watchPlaylistId.startsWith("RDMM", ignoreCase = true) ||
                            watchPlaylistId.startsWith("RDQM", ignoreCase = true)

                        val belongsToTargetPlaylist = when {
                            isAutomixWatchEndpoint && playlistDataVideoId.isBlank() -> false
                            !normalizedExpectedPlaylistId.isNullOrBlank() &&
                                normalizedExpectedPlaylistId != "LM" &&
                                normalizedExpectedPlaylistId != "LL" &&
                                watchPlaylistId.isNotBlank() &&
                                !watchPlaylistId.equals(normalizedExpectedPlaylistId, ignoreCase = true) &&
                                playlistDataVideoId.isBlank() -> false
                            !insideExplicitPlaylistShelf && playlistDataVideoId.isBlank() && watchPlaylistId.isBlank() -> false
                            else -> true
                        }

                        if (!isGreyedOut && belongsToTargetPlaylist && VALID_VIDEO_ID_REGEX.matches(videoId) && seenIds.add(videoId)) {
                            val flexCols = mrlir.optJSONArray("flexColumns")
                            var title = ""
                            var artist = ""
                            if (flexCols != null && flexCols.length() > 0) {
                                val col0 = flexCols.optJSONObject(0)
                                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                    ?.optJSONObject("text")
                                title = extractRunsText(col0)
                                if (flexCols.length() > 1) {
                                    val col1 = flexCols.optJSONObject(1)
                                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                        ?.optJSONObject("text")
                                    artist = extractRunsText(col1)
                                        .substringBefore("•")
                                        .removeSuffix(" - Topic")
                                        .trim()
                                }
                            }

                            val fixedCols = mrlir.optJSONArray("fixedColumns")
                            val durationSec = if (fixedCols != null && fixedCols.length() > 0) {
                                val durObj = fixedCols.optJSONObject(0)
                                    ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                                    ?.optJSONObject("text")
                                parseDurationText(extractRunsText(durObj))
                            } else 0L

                            var thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                            val thumbArr = mrlir.optJSONObject("thumbnail")
                                ?.optJSONObject("musicThumbnailRenderer")
                                ?.optJSONObject("thumbnail")
                                ?.optJSONArray("thumbnails")
                            if (thumbArr != null && thumbArr.length() > 0) {
                                val candidateThumb = thumbArr.optJSONObject(thumbArr.length() - 1)?.optString("url", "")
                                if (!candidateThumb.isNullOrBlank()) {
                                    thumbnailUrl = candidateThumb
                                }
                            }

                            if (!StrictMusicTrackValidator.isGhostOrDeletedTitle(title)) {
                                out.add(
                                    MediaTrackInfo(
                                        id = videoId,
                                        title = title.ifBlank { videoId },
                                        artist = artist.ifBlank { "YouTube Music" },
                                        album = albumLabel,
                                        durationSeconds = durationSec,
                                        thumbnailUrl = thumbnailUrl,
                                        sourceUrl = "https://music.youtube.com/watch?v=$videoId",
                                        streamUrl = ""
                                    )
                                )
                            }
                        }
                        return
                    }

                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (isExcludedTraversalKey(k)) continue
                        parseWebRemixTracksScoped(
                            node = node.opt(k),
                            albumLabel = albumLabel,
                            out = out,
                            seenIds = seenIds,
                            normalizedExpectedPlaylistId = normalizedExpectedPlaylistId,
                            insideExplicitPlaylistShelf = insideExplicitPlaylistShelf
                        )
                    }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) {
                        parseWebRemixTracksScoped(
                            node = node.opt(i),
                            albumLabel = albumLabel,
                            out = out,
                            seenIds = seenIds,
                            normalizedExpectedPlaylistId = normalizedExpectedPlaylistId,
                            insideExplicitPlaylistShelf = insideExplicitPlaylistShelf
                        )
                    }
                }
            }
        }

        internal fun extractMusicHeaderTitle(root: JSONObject): String {
            return try {
                val header = root.optJSONObject("header")
                if (header != null) {
                    val fromHeader = extractRunsText(
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
                    if (fromHeader.isNotBlank()) return fromHeader
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

                val microTitle = root.optJSONObject("microformat")
                    ?.optJSONObject("microformatDataRenderer")
                    ?.optString("title", "")
                    .orEmpty()
                    .removeSuffix(" - YouTube Music")
                    .removeSuffix(" - YouTube")
                    .trim()
                if (microTitle.isNotBlank()) return microTitle

                ""
            } catch (_: Throwable) {
                ""
            }
        }

        internal fun extractMusicHeaderThumbnail(root: JSONObject): String? {
            return try {
                val header = root.optJSONObject("header")
                if (header != null) {
                    extractLastThumbFromArr(
                        header.optJSONObject("musicDetailHeaderRenderer")
                            ?.optJSONObject("thumbnail")
                            ?.optJSONObject("croppedSquareThumbnailRenderer")
                            ?.optJSONObject("thumbnail")
                            ?.optJSONArray("thumbnails")
                            ?: header.optJSONObject("musicEditablePlaylistDetailHeaderRenderer")
                                ?.optJSONObject("header")
                                ?.optJSONObject("musicDetailHeaderRenderer")
                                ?.optJSONObject("thumbnail")
                                ?.optJSONObject("croppedSquareThumbnailRenderer")
                                ?.optJSONObject("thumbnail")
                                ?.optJSONArray("thumbnails")
                            ?: header.optJSONObject("musicResponsiveHeaderRenderer")
                                ?.optJSONObject("thumbnail")
                                ?.optJSONObject("musicThumbnailRenderer")
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
                                extractLastThumbFromArr(
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

                extractLastThumbFromArr(
                    root.optJSONObject("microformat")
                        ?.optJSONObject("microformatDataRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                )
            } catch (_: Throwable) {
                null
            }
        }

        private fun extractLastThumbFromArr(arr: JSONArray?): String? {
            if (arr == null || arr.length() == 0) return null
            for (i in arr.length() - 1 downTo 0) {
                val url = arr.optJSONObject(i)?.optString("url", "").orEmpty().trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return url
                }
            }
            return null
        }

        internal fun findContinuationToken(node: Any?): String? {
            if (node == null) return null
            val explicitContainers = mutableListOf<JSONObject>()
            collectExplicitPlaylistTrackContainers(node, explicitContainers)
            if (explicitContainers.isNotEmpty()) {
                for (container in explicitContainers) {
                    val token = findContinuationTokenScoped(container, allowSectionListContinuation = false)
                    if (!token.isNullOrBlank()) return token
                }
                return null
            }
            return findContinuationTokenScoped(node, allowSectionListContinuation = false)
        }

        private fun findContinuationTokenScoped(
            node: Any?,
            allowSectionListContinuation: Boolean
        ): String? {
            if (node == null) return null
            when (node) {
                is JSONObject -> {
                    if (isExcludedRecommendationKeyOrShelf(node)) return null

                    val nextCont = node.optJSONObject("nextContinuationData")?.optString("continuation", "")
                    if (!nextCont.isNullOrBlank()) return nextCont

                    val cmdCont = node.optJSONObject("continuationCommand")?.optString("token", "")
                    if (!cmdCont.isNullOrBlank()) return cmdCont

                    val reloadCont = node.optJSONObject("reloadContinuationData")?.optString("continuation", "")
                    if (!reloadCont.isNullOrBlank()) return reloadCont

                    val isSectionList = node.has("sectionListRenderer")
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key == "musicResponsiveListItemRenderer" ||
                            key == "playlistVideoRenderer" ||
                            isExcludedTraversalKey(key)
                        ) {
                            continue
                        }
                        if (!allowSectionListContinuation && key == "continuations" && isSectionList) {
                            continue
                        }
                        if (key == "sectionListRenderer") {
                            val secObj = node.optJSONObject(key) ?: continue
                            val contents = secObj.optJSONArray("contents")
                            val token = findContinuationTokenScoped(contents, allowSectionListContinuation = false)
                            if (!token.isNullOrBlank()) return token
                            continue
                        }
                        val token = findContinuationTokenScoped(node.opt(key), allowSectionListContinuation)
                        if (!token.isNullOrBlank()) return token
                    }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) {
                        val token = findContinuationTokenScoped(node.opt(i), allowSectionListContinuation)
                        if (!token.isNullOrBlank()) return token
                    }
                }
            }
            return null
        }

        internal fun extractMusicHeaderTrackCount(root: JSONObject): Int? {
            return try {
                val candidates = mutableListOf<String>()
                val header = root.optJSONObject("header")
                if (header != null) {
                    val detailHeader = header.optJSONObject("musicDetailHeaderRenderer")
                        ?: header.optJSONObject("musicEditablePlaylistDetailHeaderRenderer")
                            ?.optJSONObject("header")
                            ?.optJSONObject("musicDetailHeaderRenderer")
                    val responsiveHeader = header.optJSONObject("musicResponsiveHeaderRenderer")
                        ?: header.optJSONObject("musicEditablePlaylistDetailHeaderRenderer")
                            ?.optJSONObject("header")
                            ?.optJSONObject("musicResponsiveHeaderRenderer")
                    candidates.add(extractRunsText(detailHeader?.optJSONObject("secondSubtitle")))
                    candidates.add(extractRunsText(detailHeader?.optJSONObject("subtitle")))
                    candidates.add(extractRunsText(responsiveHeader?.optJSONObject("secondSubtitle")))
                    candidates.add(extractRunsText(responsiveHeader?.optJSONObject("subtitle")))
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
                                candidates.add(extractRunsText(headerObj.optJSONObject("secondSubtitle")))
                                candidates.add(extractRunsText(headerObj.optJSONObject("subtitle")))
                            }
                        }
                    }
                }

                for (text in candidates) {
                    parseTrackCountFromText(text)?.let { return it }
                }
                null
            } catch (_: Throwable) {
                null
            }
        }

        internal fun parseTrackCountFromText(text: String): Int? {
            if (text.isBlank()) return null
            val regex = Regex("""(\d[\d.,]*)\s*(?:şarkı|sarki|parça|parca|song|songs|track|tracks|video|videos)""", RegexOption.IGNORE_CASE)
            val match = regex.find(text) ?: return null
            val digits = match.groupValues[1].replace(".", "").replace(",", "").trim()
            return digits.toIntOrNull()
        }

        internal fun parseLibraryPlaylistsFromNode(
            node: Any?,
            out: MutableList<RemotePlaylistSummary>
        ) {
            if (node == null) return
            when (node) {
                is JSONObject -> {
                    val twoRow = node.optJSONObject("musicTwoRowItemRenderer")
                    if (twoRow != null) {
                        parseSingleLibraryPlaylistRenderer(
                            renderer = twoRow,
                            isTwoRow = true
                        )?.let { out.add(it) }
                        return
                    }

                    val listItem = node.optJSONObject("musicResponsiveListItemRenderer")
                    if (listItem != null) {
                        parseSingleLibraryPlaylistRenderer(
                            renderer = listItem,
                            isTwoRow = false
                        )?.let { out.add(it) }
                        return
                    }

                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        parseLibraryPlaylistsFromNode(node.opt(k), out)
                    }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) {
                        parseLibraryPlaylistsFromNode(node.opt(i), out)
                    }
                }
            }
        }

        private fun parseSingleLibraryPlaylistRenderer(
            renderer: JSONObject,
            isTwoRow: Boolean
        ): RemotePlaylistSummary? {
            val browseId = renderer.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId", "")
                .orEmpty()
                .trim()

            if (browseId.isBlank()) return null
            val isPlaylistBrowseId = browseId.startsWith("VL") ||
                browseId.startsWith("PL") ||
                browseId.equals("VLLM", ignoreCase = true) ||
                browseId.equals("FEmusic_liked_videos", ignoreCase = true)
            if (!isPlaylistBrowseId) return null

            // Exclude auto-generated radio/mix shelves from user playlists
            val cleanPlaylistId = when {
                browseId.equals("FEmusic_liked_videos", ignoreCase = true) ||
                    browseId.equals("VLLM", ignoreCase = true) -> "LM"
                browseId.startsWith("VL") -> browseId.removePrefix("VL")
                else -> browseId
            }
            if (cleanPlaylistId.startsWith("RDTMAK") ||
                cleanPlaylistId.startsWith("RDCLAK") ||
                cleanPlaylistId.startsWith("RDAM")
            ) {
                return null
            }

            val title: String
            val subtitle: String
            val thumbUrl: String?

            if (isTwoRow) {
                title = extractRunsText(renderer.optJSONObject("title")).trim()
                subtitle = extractRunsText(renderer.optJSONObject("subtitle")).trim()
                thumbUrl = extractLastThumbFromArr(
                    renderer.optJSONObject("thumbnailRenderer")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                )
            } else {
                val flexCols = renderer.optJSONArray("flexColumns")
                title = if (flexCols != null && flexCols.length() > 0) {
                    extractRunsText(
                        flexCols.optJSONObject(0)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")
                    ).trim()
                } else ""
                subtitle = if (flexCols != null && flexCols.length() > 1) {
                    extractRunsText(
                        flexCols.optJSONObject(1)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")
                    ).trim()
                } else ""
                thumbUrl = extractLastThumbFromArr(
                    renderer.optJSONObject("thumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                )
            }

            if (title.isBlank()) return null
            val parsedCount = parseTrackCountFromText(subtitle)
            val countLabel = when {
                parsedCount != null -> "$parsedCount şarkı"
                subtitle.isNotBlank() -> subtitle
                else -> "Oynatma listesi"
            }

            val normalizedBrowseId = when {
                cleanPlaylistId == "LM" -> "VLLM"
                browseId.startsWith("VL") -> browseId
                else -> "VL$cleanPlaylistId"
            }

            return RemotePlaylistSummary(
                playlistId = cleanPlaylistId,
                browseId = normalizedBrowseId,
                title = title,
                subtitle = subtitle,
                trackCount = parsedCount,
                trackCountLabel = countLabel,
                thumbnailUrl = thumbUrl
            )
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

        fun buildCookieHeaderFromItems(cookies: List<CookieItem>): String? {
            if (cookies.isEmpty()) return null
            val map = LinkedHashMap<String, String>()
            for (c in cookies) {
                val n = c.name.trim()
                val v = c.value.trim()
                if (n.isNotEmpty() && v.isNotEmpty()) {
                    map[n] = v
                }
            }
            if (map.isEmpty()) return null
            return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }
    }
}
