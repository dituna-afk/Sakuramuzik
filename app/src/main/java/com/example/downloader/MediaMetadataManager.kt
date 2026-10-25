package com.example.downloader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.local.LocalTrackDatabase
import com.example.data.local.LocalTrackEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MediaMetadataManager(private val context: Context) {

    private val trackDao by lazy {
        LocalTrackDatabase.getInstance(context.applicationContext).localTrackDao()
    }

    fun getAppMusicDirectory(): File {
        val internalDir = File(context.filesDir, "SakuraDownloads")
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
        ensureNoMediaMarker(internalDir)
        migrateLegacyExternalFilesIfNeeded(internalDir)
        return internalDir
    }

    fun getCoversDirectory(): File {
        val dir = File(getAppMusicDirectory(), "Covers")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        ensureNoMediaMarker(dir)
        return dir
    }

    fun getPlaylistCoversDirectory(): File {
        val dir = File(context.filesDir, "playlist_covers")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        ensureNoMediaMarker(dir)
        return dir
    }

    private fun ensureNoMediaMarker(dir: File) {
        try {
            val noMedia = File(dir, ".nomedia")
            if (!noMedia.exists()) {
                noMedia.createNewFile()
            }
        } catch (_: Exception) {
        }
    }

    private fun migrateLegacyExternalFilesIfNeeded(internalDir: File) {
        if (hasCheckedLegacyMigration) return
        synchronized(migrationLock) {
            if (hasCheckedLegacyMigration) return
            hasCheckedLegacyMigration = true
            try {
                // 1. Move any legacy files from external app dir into strictly private internal filesDir
                val externalBase = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                if (externalBase != null && externalBase.absolutePath != context.filesDir.absolutePath) {
                    val legacyDir = File(externalBase, "SakuraDownloads")
                    if (legacyDir.exists() && legacyDir.isDirectory) {
                        val internalCoversDir = File(internalDir, "Covers").apply { mkdirs() }
                        legacyDir.listFiles()?.forEach { file ->
                            if (file.isDirectory && file.name.equals("Covers", ignoreCase = true)) {
                                file.listFiles()?.forEach { cover ->
                                    val destCover = File(internalCoversDir, cover.name)
                                    if (!destCover.exists()) {
                                        if (!cover.renameTo(destCover)) {
                                            runCatching { cover.copyTo(destCover, overwrite = true) }
                                        }
                                    }
                                    runCatching { cover.delete() }
                                }
                                runCatching { file.delete() }
                            } else if (file.isFile) {
                                val destFile = File(internalDir, file.name)
                                if (!destFile.exists()) {
                                    if (!file.renameTo(destFile)) {
                                        runCatching { file.copyTo(destFile, overwrite = true) }
                                    }
                                }
                                runCatching { file.delete() }
                            }
                        }
                        runCatching { legacyDir.deleteRecursively() }
                    }
                }

                // 2. Purge any previously exported public MediaStore entries under Music/SakuraMusic
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching {
                        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        context.contentResolver.delete(
                            collection,
                            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?",
                            arrayOf("%SakuraMusic%")
                        )
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Legacy external cleanup note: ${e.message}")
            }
        }
    }

    fun sanitizeFileName(raw: String): String {
        return raw
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
            .take(110)
            .ifBlank { "sakura_track" }
    }

    fun buildTrackBaseFileName(trackInfo: MediaTrackInfo): String {
        val safeArtist = sanitizeFileName(trackInfo.artist.ifBlank { "Sakura Music" })
        val safeTitle = sanitizeFileName(trackInfo.title.ifBlank { "Track" })
        val safeId = sanitizeFileName(trackInfo.id.takeLast(16).ifBlank { "audio" })
        return "$safeArtist - $safeTitle [$safeId]"
    }

    fun buildCollisionSafeBaseFileName(trackInfo: MediaTrackInfo): String {
        val primaryBase = buildTrackBaseFileName(trackInfo)
        val musicDir = getAppMusicDirectory()
        if (!hasConflictingTrackFile(musicDir, primaryBase, trackInfo.id)) {
            return primaryBase
        }
        var suffix = 2
        while (suffix <= 999) {
            val candidateBase = "$primaryBase ($suffix)"
            if (!hasConflictingTrackFile(musicDir, candidateBase, trackInfo.id)) {
                return candidateBase
            }
            suffix++
        }
        return "${primaryBase}_${System.currentTimeMillis() % 10000}"
    }

    private fun hasConflictingTrackFile(musicDir: File, baseName: String, targetTrackId: String): Boolean {
        val validExtensions = setOf("m4a", "mp3", "opus", "ogg", "wav", "flac", "aac", "mp4", "webm")
        val existingAudioFiles = musicDir.listFiles()?.filter { f ->
            f.isFile &&
                f.nameWithoutExtension == baseName &&
                f.extension.lowercase(Locale.US) in validExtensions &&
                isValidAudioFile(f)
        }.orEmpty()
        if (existingAudioFiles.isEmpty()) return false

        val cleanTargetId = targetTrackId.trim()
        val sidecar = File(musicDir, "$baseName.meta.json")
        if (sidecar.exists()) {
            val sidecarTrackId = runCatching {
                JSONObject(sidecar.readText(Charsets.UTF_8)).optString("id", "").trim()
            }.getOrDefault("")
            if (cleanTargetId.isNotBlank() && sidecarTrackId.isNotBlank()) {
                return !sidecarTrackId.equals(cleanTargetId, ignoreCase = true)
            }
        }

        val bracketId = baseName.substringAfterLast("[", "").substringBeforeLast("]", "").trim()
        if (cleanTargetId.isNotBlank() && bracketId.isNotBlank()) {
            return !cleanTargetId.endsWith(bracketId, ignoreCase = true)
        }
        return true
    }

    suspend fun findAndAssociateExistingTrackIfPresent(
        incomingTrackInfo: MediaTrackInfo
    ): SavedMediaResult? = withContext(Dispatchers.IO) {
        val targetId = incomingTrackInfo.id.trim()
        if (targetId.isBlank()) return@withContext null

        val allSaved = loadSavedDownloads()
        val existingSaved = allSaved.firstOrNull { saved ->
            saved.trackInfo.id.equals(targetId, ignoreCase = true) && isValidAudioFile(saved.audioFile)
        } ?: return@withContext null

        val incomingPlaylistName = incomingTrackInfo.effectivePlaylistName()
        if (incomingPlaylistName.isNullOrBlank()) {
            return@withContext existingSaved
        }

        val currentEffectivePlaylist = existingSaved.trackInfo.effectivePlaylistName()
        val updatedAlbum = if (currentEffectivePlaylist.isNullOrBlank()) {
            incomingPlaylistName
        } else {
            existingSaved.trackInfo.album
        }
        val updatedTrackInfo = existingSaved.trackInfo.copy(
            album = updatedAlbum,
            playlistId = incomingTrackInfo.playlistId ?: existingSaved.trackInfo.playlistId,
            playlistTitle = incomingPlaylistName,
            playlistCoverUrl = incomingTrackInfo.playlistCoverUrl ?: existingSaved.trackInfo.playlistCoverUrl
        )

        writeSidecarMetadata(existingSaved.audioFile, updatedTrackInfo, existingSaved.coverFile)
        val updatedResult = existingSaved.copy(trackInfo = updatedTrackInfo)
        val fileMod = existingSaved.audioFile.lastModified()
        val fileLen = existingSaved.audioFile.length()
        val sidecarMod = File(
            existingSaved.audioFile.parentFile,
            "${existingSaved.audioFile.nameWithoutExtension}.meta.json"
        ).lastModified()

        fileMetadataCache[existingSaved.audioFile.absolutePath] = CachedSavedTrack(
            lastModified = fileMod,
            fileLength = fileLen,
            sidecarLastModified = sidecarMod,
            result = updatedResult
        )
        runCatching {
            trackDao.upsertTrack(
                LocalTrackEntity.fromSavedMediaResult(
                    saved = updatedResult,
                    lastModified = fileMod,
                    fileLength = fileLen,
                    sidecarLastModified = sidecarMod
                )
            )
        }
        updatedResult
    }

    fun isValidAudioFile(file: File?): Boolean {
        if (file == null || !file.exists() || !file.isFile) return false
        if (file.length() < MIN_VALID_AUDIO_BYTES) return false

        return try {
            val header = ByteArray(64)
            val bytesRead = FileInputStream(file).use { it.read(header) }
            if (bytesRead < 12) return false
            val ascii = String(header, 0, bytesRead, Charsets.ISO_8859_1).trimStart().lowercase(Locale.US)
            if (ascii.startsWith("<!doctype") ||
                ascii.startsWith("<html") ||
                ascii.startsWith("{\"error\"") ||
                ascii.startsWith("access denied")
            ) {
                return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun downloadAndSaveCoverArt(
        trackInfo: MediaTrackInfo,
        baseFileName: String
    ): File? = withContext(Dispatchers.IO) {
        val coversDir = getCoversDirectory()
        val targetCoverFile = File(coversDir, "${baseFileName}_cover.jpg")
        if (targetCoverFile.exists() && targetCoverFile.length() > 512L) {
            return@withContext targetCoverFile
        }

        val existingEmbeddedSidecar = findEmbeddedOrAdjacentThumbnail(baseFileName)
        if (existingEmbeddedSidecar != null) {
            return@withContext existingEmbeddedSidecar
        }

        val candidateUrls = buildThumbnailCandidates(trackInfo)
        for (url in candidateUrls) {
            val bitmap = downloadBitmapFromUrl(url)
            if (bitmap != null) {
                val tempCover = File(coversDir, "${baseFileName}_cover.tmp")
                try {
                    FileOutputStream(tempCover).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        out.flush()
                    }
                    if (tempCover.exists() && tempCover.length() > 512L) {
                        if (targetCoverFile.exists()) targetCoverFile.delete()
                        if (tempCover.renameTo(targetCoverFile)) {
                            return@withContext targetCoverFile
                        } else {
                            tempCover.copyTo(targetCoverFile, overwrite = true)
                            tempCover.delete()
                            return@withContext targetCoverFile
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed saving cover bitmap for $baseFileName: ${e.message}")
                    tempCover.delete()
                } finally {
                    try {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    } catch (_: Throwable) {
                    }
                }
            }
        }
        return@withContext null
    }

    suspend fun downloadAndSavePlaylistCover(
        playlistName: String,
        playlistId: String?,
        coverUrl: String?
    ): File? = withContext(Dispatchers.IO) {
        val trimmedUrl = coverUrl?.trim().orEmpty()
        if (trimmedUrl.isBlank()) return@withContext null

        val localFileCandidate = File(trimmedUrl)
        if (localFileCandidate.exists() && localFileCandidate.isFile && localFileCandidate.length() > 512L) {
            return@withContext localFileCandidate
        }

        if (!trimmedUrl.startsWith("http://", ignoreCase = true) &&
            !trimmedUrl.startsWith("https://", ignoreCase = true)
        ) {
            return@withContext null
        }

        return@withContext try {
            val playlistCoversDir = getPlaylistCoversDirectory()
            val safeKey = sanitizeFileName(
                if (!playlistId.isNullOrBlank()) "${playlistName}_${playlistId.takeLast(16)}"
                else playlistName
            ).replace(" ", "_")
            val targetCoverFile = File(playlistCoversDir, "pl_${safeKey}_cover.jpg")
            if (targetCoverFile.exists() && targetCoverFile.length() > 512L) {
                return@withContext targetCoverFile
            }

            val bitmap = downloadBitmapFromUrl(trimmedUrl) ?: return@withContext null
            val tempCover = File(playlistCoversDir, "pl_${safeKey}_cover.tmp")
            try {
                FileOutputStream(tempCover).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    out.flush()
                }
                if (tempCover.exists() && tempCover.length() > 512L) {
                    if (targetCoverFile.exists()) targetCoverFile.delete()
                    if (tempCover.renameTo(targetCoverFile)) {
                        targetCoverFile
                    } else {
                        tempCover.copyTo(targetCoverFile, overwrite = true)
                        tempCover.delete()
                        targetCoverFile
                    }
                } else {
                    tempCover.delete()
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed saving playlist cover for $playlistName: ${e.message}")
                tempCover.delete()
                null
            } finally {
                try {
                    if (!bitmap.isRecycled) bitmap.recycle()
                } catch (_: Throwable) {
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Playlist cover download skipped for $playlistName: ${e.message}")
            null
        }
    }

    private fun findEmbeddedOrAdjacentThumbnail(baseFileName: String): File? {
        val musicDir = getAppMusicDirectory()
        val coversDir = getCoversDirectory()
        val targetCoverFile = File(coversDir, "${baseFileName}_cover.jpg")

        val possibleThumbExtensions = listOf("jpg", "jpeg", "png", "webp")
        for (ext in possibleThumbExtensions) {
            val candidate = File(musicDir, "$baseFileName.$ext")
            if (candidate.exists() && candidate.length() > 512L) {
                var bmp: Bitmap? = null
                return try {
                    bmp = BitmapFactory.decodeFile(candidate.absolutePath)
                    if (bmp != null) {
                        FileOutputStream(targetCoverFile).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        }
                        candidate.delete()
                        targetCoverFile.takeIf { it.exists() && it.length() > 512L }
                    } else null
                } catch (_: Exception) {
                    null
                } finally {
                    try {
                        if (bmp != null && !bmp.isRecycled) bmp.recycle()
                    } catch (_: Throwable) {
                    }
                }
            }
        }
        return null
    }

    private fun buildThumbnailCandidates(trackInfo: MediaTrackInfo): List<String> {
        val candidates = mutableListOf<String>()
        val primary = trackInfo.thumbnailUrl?.trim().orEmpty()
        if (primary.startsWith("http://") || primary.startsWith("https://")) {
            candidates.add(primary)
        }
        val id = trackInfo.id.trim()
        if (id.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            candidates.add("https://i.ytimg.com/vi/$id/maxresdefault.jpg")
            candidates.add("https://i.ytimg.com/vi/$id/sddefault.jpg")
            candidates.add("https://i.ytimg.com/vi/$id/hqdefault.jpg")
            candidates.add("https://i.ytimg.com/vi/$id/mqdefault.jpg")
        }
        return candidates.distinct()
    }

    private fun downloadBitmapFromUrl(urlString: String): Bitmap? {
        return try {
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return null
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            if (bytes.size < 512) return null
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            var sampleSize = 1
            val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            while (maxDim / sampleSize > 1440) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun finalizeDownloadedMedia(
        downloadedFile: File,
        trackInfo: MediaTrackInfo,
        callback: MediaSaveCallback? = null
    ): Result<SavedMediaResult> = withContext(Dispatchers.IO) {
        try {
            callback?.onStageChanged("Ses dosyası bütünlüğü doğrulanıyor...")
            if (!isValidAudioFile(downloadedFile)) {
                val len = if (downloadedFile.exists()) downloadedFile.length() else 0L
                val msg = "İndirilen dosya geçersiz veya tamamlanmamış ($len bayt)."
                callback?.onSaveError(msg)
                return@withContext Result.failure(IllegalStateException(msg))
            }

            val musicDir = getAppMusicDirectory()
            val ext = downloadedFile.extension.lowercase(Locale.US).ifBlank {
                when {
                    trackInfo.mimeType.contains("mpeg") -> "mp3"
                    trackInfo.mimeType.contains("ogg") || trackInfo.mimeType.contains("opus") -> "opus"
                    trackInfo.mimeType.contains("video/mp4") -> "mp4"
                    else -> "m4a"
                }
            }

            val baseFileName = if (downloadedFile.parentFile?.absolutePath == musicDir.absolutePath &&
                !hasConflictingTrackFile(musicDir, downloadedFile.nameWithoutExtension, trackInfo.id)
            ) {
                downloadedFile.nameWithoutExtension
            } else {
                buildCollisionSafeBaseFileName(trackInfo)
            }

            val finalFile = File(musicDir, "$baseFileName.$ext")
            if (downloadedFile.absolutePath != finalFile.absolutePath) {
                if (finalFile.exists() && !hasConflictingTrackFile(musicDir, baseFileName, trackInfo.id)) {
                    finalFile.delete()
                }
                val moved = downloadedFile.renameTo(finalFile)
                if (!moved) {
                    downloadedFile.copyTo(finalFile, overwrite = true)
                    downloadedFile.delete()
                }
            }

            if (!isValidAudioFile(finalFile)) {
                val msg = "Kalıcı ses dosyası oluşturulamadı."
                callback?.onSaveError(msg)
                return@withContext Result.failure(IllegalStateException(msg))
            }

            callback?.onStageChanged("Kapak fotoğrafı indiriliyor ve eşleştiriliyor...")
            var coverFile = downloadAndSaveCoverArt(trackInfo, baseFileName)
            if (coverFile == null) {
                coverFile = extractEmbeddedPictureIfPresent(finalFile, baseFileName)
            }

            var resolvedDurationSeconds = trackInfo.durationSeconds
            if (resolvedDurationSeconds <= 0L) {
                resolvedDurationSeconds = extractDurationSeconds(finalFile)
            }

            val effectivePlTitle = trackInfo.effectivePlaylistName()
            val resolvedAlbum = effectivePlTitle ?: trackInfo.album.ifBlank { "Sakura Music" }

            val fileLength = finalFile.length()
            val updatedTrackInfo = trackInfo.copy(
                album = resolvedAlbum,
                durationSeconds = resolvedDurationSeconds,
                thumbnailUrl = coverFile?.absolutePath ?: trackInfo.thumbnailUrl,
                contentLengthBytes = fileLength,
                playlistTitle = effectivePlTitle ?: trackInfo.playlistTitle
            )

            writeSidecarMetadata(finalFile, updatedTrackInfo, coverFile)

            callback?.onStageChanged("Uygulama içi özel kasaya kaydediliyor...")
            val internalUri = finalFile.toURI().toString()

            val savedResult = SavedMediaResult(
                audioFile = finalFile,
                coverFile = coverFile,
                mediaStoreUri = internalUri,
                trackInfo = updatedTrackInfo
            )
            val fileMod = finalFile.lastModified()
            val sidecarMod = File(finalFile.parentFile, "${finalFile.nameWithoutExtension}.meta.json").lastModified()
            fileMetadataCache[finalFile.absolutePath] = CachedSavedTrack(
                lastModified = fileMod,
                fileLength = fileLength,
                sidecarLastModified = sidecarMod,
                result = savedResult
            )
            runCatching {
                trackDao.upsertTrack(
                    LocalTrackEntity.fromSavedMediaResult(
                        saved = savedResult,
                        lastModified = fileMod,
                        fileLength = fileLength,
                        sidecarLastModified = sidecarMod
                    )
                )
            }
            callback?.onSaved(savedResult)
            Result.success(savedResult)
        } catch (e: Exception) {
            val msg = "Dosya kaydetme hatası: ${e.localizedMessage ?: "Bilinmeyen hata"}"
            callback?.onSaveError(msg)
            Result.failure(e)
        }
    }

    private fun extractEmbeddedPictureIfPresent(audioFile: File, baseFileName: String): File? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(audioFile.absolutePath)
            val embeddedBytes = retriever.embeddedPicture ?: return null
            if (embeddedBytes.size < 512) return null
            val coverFile = File(getCoversDirectory(), "${baseFileName}_cover.jpg")
            FileOutputStream(coverFile).use { it.write(embeddedBytes) }
            coverFile.takeIf { it.exists() && it.length() > 512L }
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun extractDurationSeconds(audioFile: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(audioFile.absolutePath)
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            (durMs / 1000L).coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun writeSidecarMetadata(
        audioFile: File,
        trackInfo: MediaTrackInfo,
        coverFile: File?
    ) {
        try {
            val sidecar = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.meta.json")
            val effectivePlaylistTitle = trackInfo.effectivePlaylistName()
            val effectiveAlbum = effectivePlaylistTitle ?: trackInfo.album
            val json = JSONObject().apply {
                put("id", trackInfo.id)
                put("title", trackInfo.title)
                put("artist", trackInfo.artist)
                put("album", effectiveAlbum)
                put("durationSeconds", trackInfo.durationSeconds)
                put("thumbnailUrl", trackInfo.thumbnailUrl ?: "")
                put("coverFilePath", coverFile?.absolutePath ?: "")
                put("sourceUrl", trackInfo.sourceUrl)
                put("streamUrl", trackInfo.streamUrl)
                put("mimeType", trackInfo.mimeType)
                put("bitrateKbps", trackInfo.bitrateKbps)
                put("contentLengthBytes", audioFile.length())
                put("playlistId", trackInfo.playlistId ?: "")
                put("playlistTitle", effectivePlaylistTitle ?: "")
                put("playlistCoverUrl", trackInfo.playlistCoverUrl ?: "")
                put("savedAt", System.currentTimeMillis())
            }
            sidecar.writeText(json.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Failed writing sidecar metadata: ${e.message}")
        }
    }

    /**
     * Ultra-fast Cold Start loader: reads indexed metadata directly from Room SQLite
     * without scanning the filesystem or parsing JSON sidecars.
     */
    suspend fun loadCachedTracksFromRoom(): List<SavedMediaResult> = withContext(Dispatchers.IO) {
        return@withContext try {
            val entities = trackDao.getAllTracksOrdered()
            entities.map { entity ->
                val result = entity.toSavedMediaResult()
                fileMetadataCache.putIfAbsent(
                    entity.audioFilePath,
                    CachedSavedTrack(
                        lastModified = entity.lastModified,
                        fileLength = entity.fileLength,
                        sidecarLastModified = entity.sidecarLastModified,
                        result = result
                    )
                )
                result
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Room fast load note: ${e.message}")
            emptyList()
        }
    }

    suspend fun loadSavedDownloads(): List<SavedMediaResult> = withContext(Dispatchers.IO) {
        // Hydrate in-memory cache from Room first if empty so we never re-parse unchanged sidecars
        if (fileMetadataCache.isEmpty()) {
            runCatching {
                trackDao.getAllTracksOrdered().forEach { entity ->
                    fileMetadataCache.putIfAbsent(
                        entity.audioFilePath,
                        CachedSavedTrack(
                            lastModified = entity.lastModified,
                            fileLength = entity.fileLength,
                            sidecarLastModified = entity.sidecarLastModified,
                            result = entity.toSavedMediaResult()
                        )
                    )
                }
            }
        }

        val dir = getAppMusicDirectory()
        val coversDir = getCoversDirectory()
        val files = dir.listFiles().orEmpty()
        val validExtensions = setOf("m4a", "mp3", "opus", "ogg", "wav", "flac", "aac", "mp4", "webm")

        val results = ArrayList<SavedMediaResult>(files.size)
        val activePaths = HashSet<String>(files.size)
        val newOrUpdatedEntities = ArrayList<LocalTrackEntity>()

        for (file in files) {
            if (!file.isFile) continue
            val name = file.name
            if (name.endsWith(".part", ignoreCase = true) ||
                name.endsWith(".tmp", ignoreCase = true) ||
                name.endsWith(".ytdl", ignoreCase = true) ||
                name.endsWith(".meta.json", ignoreCase = true) ||
                name.endsWith(".lrc", ignoreCase = true)
            ) {
                continue
            }
            val ext = file.extension.lowercase(Locale.US)
            if (ext !in validExtensions) continue

            val absPath = file.absolutePath
            val fileLen = file.length()
            if (fileLen < MIN_VALID_AUDIO_BYTES) continue
            val fileMod = file.lastModified()
            val baseName = file.nameWithoutExtension
            val sidecar = File(dir, "$baseName.meta.json")
            val sidecarMod = if (sidecar.exists()) sidecar.lastModified() else 0L

            activePaths.add(absPath)

            // Instant cache hit if neither the audio file nor its sidecar changed
            val cached = fileMetadataCache[absPath]
            if (cached != null &&
                cached.lastModified == fileMod &&
                cached.fileLength == fileLen &&
                cached.sidecarLastModified == sidecarMod &&
                !cached.result.trackInfo.title.startsWith("Parça ")
            ) {
                results.add(cached.result)
                continue
            }

            if (!isValidAudioFile(file)) continue

            var id = baseName.substringAfterLast("[", "").substringBeforeLast("]", "").ifBlank {
                file.name.hashCode().toString()
            }
            var title = baseName.substringBeforeLast(" [").substringAfter(" - ", baseName.substringBeforeLast(" [")).trim()
            var artist = baseName.substringBefore(" - ", "Sakura Music").trim()
            var album = "Sakura Music"
            var durationSeconds = 0L
            var sourceUrl = absPath
            var mimeType = if (ext == "mp3") "audio/mpeg" else if (ext == "mp4") "video/mp4" else "audio/mp4"
            var bitrateKbps = 160
            var coverFile: File? = null
            var playlistId: String? = null
            var playlistTitle: String? = null
            var playlistCoverUrl: String? = null

            if (sidecarMod > 0L) {
                try {
                    val json = JSONObject(sidecar.readText(Charsets.UTF_8))
                    id = json.optString("id", id).ifBlank { id }
                    title = json.optString("title", title).ifBlank { title }
                    artist = json.optString("artist", artist).ifBlank { artist }
                    album = json.optString("album", album).ifBlank { album }
                    durationSeconds = json.optLong("durationSeconds", durationSeconds)
                    sourceUrl = json.optString("sourceUrl", sourceUrl).ifBlank { sourceUrl }
                    mimeType = json.optString("mimeType", mimeType).ifBlank { mimeType }
                    bitrateKbps = json.optInt("bitrateKbps", bitrateKbps)
                    playlistId = json.optString("playlistId", "").trim().ifBlank { null }
                    playlistTitle = json.optString("playlistTitle", "").trim().ifBlank { null }
                    playlistCoverUrl = json.optString("playlistCoverUrl", "").trim().ifBlank { null }
                    if (!playlistTitle.isNullOrBlank() && !MediaTrackInfo.isMeaningfulPlaylistName(album)) {
                        album = playlistTitle
                    } else if (playlistTitle.isNullOrBlank() && MediaTrackInfo.isMeaningfulPlaylistName(album)) {
                        playlistTitle = album
                    }
                    val savedCoverPath = json.optString("coverFilePath", "")
                    if (savedCoverPath.isNotBlank() && File(savedCoverPath).exists()) {
                        coverFile = File(savedCoverPath)
                    }
                } catch (_: Exception) {
                }
            }

            if (coverFile == null) {
                val exactCover = File(coversDir, "${baseName}_cover.jpg")
                if (exactCover.exists() && exactCover.length() > 512L) {
                    coverFile = exactCover
                }
            }

            val trackInfo = MediaTrackInfo(
                id = id,
                title = title.ifBlank { baseName },
                artist = artist.ifBlank { "Sakura Music" },
                album = album,
                durationSeconds = durationSeconds,
                thumbnailUrl = coverFile?.absolutePath,
                sourceUrl = sourceUrl,
                streamUrl = absPath,
                mimeType = mimeType,
                bitrateKbps = bitrateKbps,
                contentLengthBytes = fileLen,
                playlistId = playlistId,
                playlistTitle = playlistTitle,
                playlistCoverUrl = playlistCoverUrl
            )

            val savedItem = SavedMediaResult(
                audioFile = file,
                coverFile = coverFile,
                mediaStoreUri = file.toURI().toString(),
                trackInfo = trackInfo
            )
            val updatedSidecarMod = if (sidecar.exists()) sidecar.lastModified() else 0L
            fileMetadataCache[absPath] = CachedSavedTrack(
                lastModified = fileMod,
                fileLength = fileLen,
                sidecarLastModified = updatedSidecarMod,
                result = savedItem
            )
            newOrUpdatedEntities.add(
                LocalTrackEntity.fromSavedMediaResult(
                    saved = savedItem,
                    lastModified = fileMod,
                    fileLength = fileLen,
                    sidecarLastModified = updatedSidecarMod
                )
            )
            results.add(savedItem)
        }

        // Prune deleted files from cache and Room
        val removedPaths = mutableListOf<String>()
        val iterator = fileMetadataCache.keys.iterator()
        while (iterator.hasNext()) {
            val k = iterator.next()
            if (k !in activePaths) {
                removedPaths.add(k)
                iterator.remove()
            }
        }

        runCatching {
            if (newOrUpdatedEntities.isNotEmpty()) {
                trackDao.upsertTracks(newOrUpdatedEntities)
            }
            if (activePaths.isEmpty()) {
                trackDao.clearAll()
            } else if (removedPaths.isNotEmpty()) {
                removedPaths.forEach { trackDao.deleteByPath(it) }
            }
        }

        results.sortedByDescending { fileMetadataCache[it.audioFile.absolutePath]?.lastModified ?: it.audioFile.lastModified() }
    }

    private fun extractYoutubeIdFromText(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) return trimmed
        val parenMatch = Regex("""\(([a-zA-Z0-9_-]{11})\)""").find(trimmed)
        if (parenMatch != null) return parenMatch.groupValues[1]
        val bracketMatch = Regex("""\[([a-zA-Z0-9_-]{11})]""").find(trimmed)
        if (bracketMatch != null) return bracketMatch.groupValues[1]
        return null
    }

    fun fetchOembedTitleAndArtist(videoId: String): Pair<String, String>? {
        return try {
            val oembedUrl = URL("https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json")
            val conn = (oembedUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                conn.disconnect()
                val json = JSONObject(body)
                val rawTitle = json.optString("title", "").trim()
                val rawAuthor = json.optString("author_name", "").removeSuffix(" - Topic").trim()
                if (rawTitle.isNotBlank()) {
                    rawTitle to rawAuthor.ifBlank { "YouTube Music" }
                } else null
            } else {
                conn.disconnect()
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun updateTrackMetadataAndRename(
        saved: SavedMediaResult,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newCoverUrl: String? = null
    ): Result<SavedMediaResult> = withContext(Dispatchers.IO) {
        try {
            val oldAudioFile = saved.audioFile
            if (!oldAudioFile.exists()) {
                return@withContext Result.failure(IllegalStateException("Ses dosyası bulunamadı."))
            }
            val musicDir = getAppMusicDirectory()
            val oldBase = oldAudioFile.nameWithoutExtension
            val oldSidecar = File(musicDir, "$oldBase.meta.json")
            val oldLrc = File(musicDir, "$oldBase.lrc")

            val updatedInfo = saved.trackInfo.copy(
                title = newTitle.trim().ifBlank { saved.trackInfo.title },
                artist = newArtist.trim().ifBlank { saved.trackInfo.artist },
                album = newAlbum.trim().ifBlank { saved.trackInfo.album },
                thumbnailUrl = if (!newCoverUrl.isNullOrBlank()) newCoverUrl.trim() else saved.trackInfo.thumbnailUrl
            )

            val newBase = buildTrackBaseFileName(updatedInfo)
            val ext = oldAudioFile.extension.ifBlank { "m4a" }
            val newAudioFile = File(musicDir, "$newBase.$ext")

            if (oldAudioFile.absolutePath != newAudioFile.absolutePath) {
                if (newAudioFile.exists()) newAudioFile.delete()
                if (!oldAudioFile.renameTo(newAudioFile)) {
                    oldAudioFile.copyTo(newAudioFile, overwrite = true)
                    oldAudioFile.delete()
                }
                if (oldSidecar.exists()) oldSidecar.delete()
                if (oldLrc.exists()) {
                    val newLrc = File(musicDir, "$newBase.lrc")
                    oldLrc.renameTo(newLrc)
                }
            }

            var updatedCoverFile = saved.coverFile
            if (!newCoverUrl.isNullOrBlank() && (newCoverUrl.startsWith("http://") || newCoverUrl.startsWith("https://"))) {
                val downloadedCover = downloadAndSaveCoverArt(updatedInfo, newBase)
                if (downloadedCover != null) {
                    updatedCoverFile = downloadedCover
                }
            } else if (updatedCoverFile != null && updatedCoverFile.exists()) {
                val targetCover = File(getCoversDirectory(), "${newBase}_cover.jpg")
                if (updatedCoverFile.absolutePath != targetCover.absolutePath) {
                    if (updatedCoverFile.renameTo(targetCover)) {
                        updatedCoverFile = targetCover
                    }
                }
            }

            val fileLength = newAudioFile.length()
            val finalInfo = updatedInfo.copy(
                streamUrl = newAudioFile.absolutePath,
                thumbnailUrl = updatedCoverFile?.absolutePath ?: updatedInfo.thumbnailUrl,
                contentLengthBytes = fileLength
            )
            writeSidecarMetadata(newAudioFile, finalInfo, updatedCoverFile)

            fileMetadataCache.remove(oldAudioFile.absolutePath)
            runCatching { trackDao.deleteByPath(oldAudioFile.absolutePath) }

            val updatedSaved = SavedMediaResult(
                audioFile = newAudioFile,
                coverFile = updatedCoverFile,
                mediaStoreUri = newAudioFile.toURI().toString(),
                trackInfo = finalInfo
            )
            val fileMod = newAudioFile.lastModified()
            val sidecarMod = File(musicDir, "$newBase.meta.json").lastModified()
            fileMetadataCache[newAudioFile.absolutePath] = CachedSavedTrack(
                lastModified = fileMod,
                fileLength = fileLength,
                sidecarLastModified = sidecarMod,
                result = updatedSaved
            )
            runCatching {
                trackDao.upsertTrack(
                    LocalTrackEntity.fromSavedMediaResult(
                        saved = updatedSaved,
                        lastModified = fileMod,
                        fileLength = fileLength,
                        sidecarLastModified = sidecarMod
                    )
                )
            }

            Result.success(updatedSaved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class LibraryDoctorResult(
        val removedCorruptedCount: Int,
        val removedDuplicateCount: Int,
        val repairedTitlesCount: Int,
        val freedBytes: Long
    )

    suspend fun runLibraryDoctorCleanup(): LibraryDoctorResult = withContext(Dispatchers.IO) {
        val dir = getAppMusicDirectory()
        val allFiles = dir.listFiles().orEmpty()
        var corruptedCount = 0
        var duplicateCount = 0
        var repairedCount = 0
        var freedBytes = 0L

        // 1. Remove leftover .part, .tmp, .ytdl or corrupted <16KB audio files
        val audioExts = setOf("m4a", "mp3", "opus", "ogg", "wav", "flac", "aac", "mp4", "webm")
        for (f in allFiles) {
            if (!f.isFile) continue
            val isTemp = f.name.endsWith(".part", true) ||
                f.name.endsWith(".tmp", true) ||
                f.name.endsWith(".ytdl", true)
            val isInvalidAudio = f.extension.lowercase(Locale.US) in audioExts && !isValidAudioFile(f)
            if (isTemp || isInvalidAudio) {
                val len = f.length()
                if (f.delete()) {
                    corruptedCount++
                    freedBytes += len
                }
            }
        }

        // 2. Load valid tracks, repair any "Parça X" titles, and remove duplicates
        val loaded = loadSavedDownloads()
        val seenKeys = mutableSetOf<String>()
        for (item in loaded) {
            var currentItem = item
            if (currentItem.trackInfo.title.startsWith("Parça ") || currentItem.trackInfo.artist == "YouTube Playlist") {
                val vid = extractYoutubeIdFromText(currentItem.trackInfo.id)
                    ?: extractYoutubeIdFromText(currentItem.trackInfo.title)
                if (vid != null) {
                    val pair = fetchOembedTitleAndArtist(vid)
                    if (pair != null) {
                        val renamed = updateTrackMetadataAndRename(
                            saved = currentItem,
                            newTitle = pair.first,
                            newArtist = pair.second,
                            newAlbum = currentItem.trackInfo.album
                        ).getOrNull()
                        if (renamed != null) {
                            currentItem = renamed
                            repairedCount++
                        }
                    }
                }
            }

            val dedupKey = if (currentItem.trackInfo.id.length == 11) {
                "id:${currentItem.trackInfo.id}"
            } else {
                "meta:${currentItem.trackInfo.artist.lowercase(Locale.US).trim()}_${currentItem.trackInfo.title.lowercase(Locale.US).trim()}"
            }

            if (!seenKeys.add(dedupKey)) {
                val len = currentItem.trackInfo.contentLengthBytes.takeIf { it > 0L } ?: currentItem.audioFile.length()
                if (deleteSavedTrack(currentItem)) {
                    duplicateCount++
                    freedBytes += len
                }
            }
        }

        LibraryDoctorResult(
            removedCorruptedCount = corruptedCount,
            removedDuplicateCount = duplicateCount,
            repairedTitlesCount = repairedCount,
            freedBytes = freedBytes
        )
    }

    suspend fun deleteSavedTrack(saved: SavedMediaResult): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val audioFile = saved.audioFile
            fileMetadataCache.remove(audioFile.absolutePath)
            runCatching { trackDao.deleteByPath(audioFile.absolutePath) }
            val sidecar = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.meta.json")
            if (sidecar.exists()) sidecar.delete()
            val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
            if (lrcFile.exists()) lrcFile.delete()
            saved.coverFile?.let { cover ->
                if (cover.exists()) cover.delete()
            }
            audioFile.delete()
        } catch (_: Exception) {
            false
        }
    }

    private data class CachedSavedTrack(
        val lastModified: Long,
        val fileLength: Long,
        val sidecarLastModified: Long,
        val result: SavedMediaResult
    )

    companion object {
        private const val TAG = "MediaMetadataManager"
        const val MIN_VALID_AUDIO_BYTES = 16_384L
        private val fileMetadataCache = ConcurrentHashMap<String, CachedSavedTrack>()
        private val migrationLock = Any()
        @Volatile
        private var hasCheckedLegacyMigration = false
    }
}
