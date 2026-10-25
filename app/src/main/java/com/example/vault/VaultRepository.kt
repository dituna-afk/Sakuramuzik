package com.example.vault

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import com.example.downloader.MediaMetadataManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class VaultItem(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val filePath: String,
    val fileSizeBytes: Long,
    val isVideo: Boolean,
    val formatExtension: String,
    val resolutionOrBitrate: String,
    val thumbnailPath: String?,
    val addedTimestamp: Long
)

class VaultRepository(private val context: Context) {

    fun getVaultRootDirectory(): File {
        return MediaMetadataManager(context).getAppMusicDirectory()
    }

    suspend fun loadVaultItems(): List<VaultItem> = withContext(Dispatchers.IO) {
        val rootDir = getVaultRootDirectory()
        val coversDir = File(rootDir, "Covers")
        val allFiles = rootDir.listFiles().orEmpty()

        val supportedAudioExtensions = setOf("m4a", "mp3", "opus", "ogg", "wav", "flac", "aac")
        val supportedVideoExtensions = setOf("mp4", "mkv", "webm", "mov")

        val items = mutableListOf<VaultItem>()
        val activePaths = HashSet<String>(allFiles.size)

        for (file in allFiles) {
            if (!file.isFile) continue
            if (file.name.endsWith(".part", ignoreCase = true) ||
                file.name.endsWith(".tmp", ignoreCase = true) ||
                file.name.endsWith(".ytdl", ignoreCase = true) ||
                file.name.endsWith(".meta.json", ignoreCase = true) ||
                file.length() < MediaMetadataManager.MIN_VALID_AUDIO_BYTES
            ) {
                continue
            }

            val ext = file.extension.lowercase()
            val isAudio = ext in supportedAudioExtensions
            val isVideo = ext in supportedVideoExtensions
            if (!isAudio && !isVideo) continue

            val absPath = file.absolutePath
            activePaths.add(absPath)
            val sidecarFile = File(rootDir, "${file.nameWithoutExtension}.meta.json")
            val signature = file.lastModified() xor (file.length() shl 16) xor (if (sidecarFile.exists()) sidecarFile.lastModified() else 0L)
            val cached = vaultItemCache[absPath]
            if (cached != null && cached.first == signature) {
                items.add(cached.second)
                continue
            }

            var title = file.nameWithoutExtension.substringBeforeLast(" [").trim()
            var artist = "Sakura Vault"
            var durationMs = 0L
            var resolutionOrBitrate = if (isVideo) "1080p MP4" else "${ext.uppercase()} Audio"
            var thumbnailPath: String? = null

            if (sidecarFile.exists()) {
                try {
                    val json = JSONObject(sidecarFile.readText())
                    title = json.optString("title", title).ifBlank { title }
                    artist = json.optString("artist", artist).ifBlank { artist }
                    val durationSec = json.optLong("durationSeconds", 0L)
                    if (durationSec > 0L) durationMs = durationSec * 1000L
                    val bitrate = json.optInt("bitrateKbps", 0)
                    if (bitrate > 0 && !isVideo) {
                        resolutionOrBitrate = "${bitrate}kbps ${ext.uppercase()}"
                    }
                    val savedCoverPath = json.optString("coverFilePath", "")
                    if (savedCoverPath.isNotBlank() && File(savedCoverPath).exists()) {
                        thumbnailPath = savedCoverPath
                    }
                } catch (_: Exception) {
                }
            }

            if (durationMs <= 0L || artist == "Sakura Vault") {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(absPath)
                    val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    val metaDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val metaBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
                    val metaWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val metaHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0

                    if (!metaTitle.isNullOrBlank() && sidecarFile.exists().not()) {
                        title = metaTitle
                    }
                    if (!metaArtist.isNullOrBlank() && artist == "Sakura Vault") {
                        artist = metaArtist
                    }
                    if (metaDuration > 0L) {
                        durationMs = metaDuration
                    }
                    if (isVideo && metaWidth > 0 && metaHeight > 0) {
                        resolutionOrBitrate = "${metaHeight}p ${ext.uppercase()}"
                    } else if (!isVideo && metaBitrate > 0) {
                        resolutionOrBitrate = "${metaBitrate / 1000}kbps ${ext.uppercase()}"
                    }
                } catch (_: Exception) {
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }
            }

            if (thumbnailPath == null && coversDir.exists()) {
                val exactCover = File(coversDir, "${file.nameWithoutExtension}_cover.jpg")
                if (exactCover.exists() && exactCover.length() > 256L) {
                    thumbnailPath = exactCover.absolutePath
                } else {
                    val basePrefix = file.nameWithoutExtension.substringBeforeLast(" [").trim()
                    val matched = coversDir.listFiles()?.firstOrNull {
                        it.isFile && it.length() > 256L && it.name.startsWith(basePrefix, ignoreCase = true)
                    }
                    if (matched != null) {
                        thumbnailPath = matched.absolutePath
                    }
                }
            }

            val builtItem = VaultItem(
                id = absPath,
                title = title.ifBlank { file.nameWithoutExtension },
                artist = artist,
                durationMs = durationMs,
                filePath = absPath,
                fileSizeBytes = file.length(),
                isVideo = isVideo,
                formatExtension = ext.uppercase(),
                resolutionOrBitrate = resolutionOrBitrate,
                thumbnailPath = thumbnailPath,
                addedTimestamp = file.lastModified()
            )
            vaultItemCache[absPath] = signature to builtItem
            items.add(builtItem)
        }

        val iterator = vaultItemCache.keys.iterator()
        while (iterator.hasNext()) {
            val k = iterator.next()
            if (k !in activePaths) {
                iterator.remove()
            }
        }

        items.sortedByDescending { it.addedTimestamp }
    }

    companion object {
        private val vaultItemCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, VaultItem>>()
    }

    suspend fun deleteVaultItem(item: VaultItem): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val file = File(item.filePath)
            val rootDir = file.parentFile
            if (rootDir != null) {
                val sidecar = File(rootDir, "${file.nameWithoutExtension}.meta.json")
                if (sidecar.exists()) sidecar.delete()
            }
            item.thumbnailPath?.let { path ->
                val thumb = File(path)
                if (thumb.exists()) thumb.delete()
            }
            file.delete()
        } catch (_: Exception) {
            false
        }
    }
}
