package com.example.downloader

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChunkState(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long = 0L,
    val isCompleted: Boolean = false
) {
    val totalBytes: Long
        get() = (endByte - startByte + 1L).coerceAtLeast(1L)

    val progressFraction: Float
        get() = if (isCompleted) 1f else (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

data class DownloadProgress(
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val speedBytesPerSec: Long = 0L,
    val chunks: List<ChunkState> = emptyList(),
    val isDownloading: Boolean = false,
    val isCompleted: Boolean = false,
    val statusMessage: String = "Hazır"
) {
    val overallFraction: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

interface DownloadListener {
    fun onProgress(progress: DownloadProgress)
    fun onComplete(outputFile: File)
    fun onError(errorMessage: String)
}

class MultiThreadChunkedDownloader(
    private val threadCount: Int = 4
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private val isCancelled = AtomicBoolean(false)

    private val _progressFlow = MutableStateFlow(DownloadProgress())
    val progressFlow: StateFlow<DownloadProgress> = _progressFlow.asStateFlow()

    fun cancel() {
        isCancelled.set(true)
        activeJob?.cancel()
        _progressFlow.value = _progressFlow.value.copy(
            isDownloading = false,
            statusMessage = "İndirme iptal edildi."
        )
    }

    fun startDownloadAsync(
        fileUrl: String,
        destinationFile: File,
        cookieHeader: String? = null,
        listener: DownloadListener? = null
    ) {
        activeJob?.cancel()
        activeJob = scope.launch {
            val result = downloadFileSuspend(fileUrl, destinationFile, cookieHeader, listener)
            result.onFailure { err ->
                if (err !is CancellationException) {
                    listener?.onError(err.localizedMessage ?: "İndirme başarısız oldu.")
                }
            }
        }
    }

    suspend fun downloadFileSuspend(
        fileUrl: String,
        destinationFile: File,
        cookieHeader: String? = null,
        listener: DownloadListener? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        isCancelled.set(false)
        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.part")
        if (tempFile.exists()) tempFile.delete()

        try {
            _progressFlow.value = DownloadProgress(
                isDownloading = true,
                statusMessage = "Sunucu akışı kontrol ediliyor..."
            )
            listener?.onProgress(_progressFlow.value)

            val probe = probeServerStream(fileUrl, cookieHeader)
            if (probe.contentType.contains("text/html", ignoreCase = true)) {
                throw IllegalStateException("Sunucu ses akışı yerine HTML sayfası döndürdü.")
            }

            if (probe.supportsRange && probe.contentLength > 256_000L && threadCount > 1) {
                downloadMultiThreaded(
                    url = probe.resolvedUrl,
                    tempFile = tempFile,
                    totalBytes = probe.contentLength,
                    cookieHeader = cookieHeader,
                    listener = listener
                )
            } else {
                downloadSingleStream(
                    url = probe.resolvedUrl,
                    tempFile = tempFile,
                    expectedTotalBytes = probe.contentLength,
                    cookieHeader = cookieHeader,
                    listener = listener
                )
            }

            if (isCancelled.get()) {
                tempFile.delete()
                throw CancellationException("İndirme kullanıcı tarafından iptal edildi.")
            }

            if (!tempFile.exists() || tempFile.length() < MediaMetadataManager.MIN_VALID_AUDIO_BYTES) {
                val len = if (tempFile.exists()) tempFile.length() else 0L
                tempFile.delete()
                throw IllegalStateException("İndirilen ses verisi eksik veya geçersiz ($len bayt).")
            }

            if (destinationFile.exists()) destinationFile.delete()
            val renamed = tempFile.renameTo(destinationFile)
            if (!renamed) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }

            val finalProgress = _progressFlow.value.copy(
                downloadedBytes = destinationFile.length(),
                totalBytes = destinationFile.length(),
                isDownloading = false,
                isCompleted = true,
                statusMessage = "İndirme tamamlandı (${destinationFile.length() / 1024} KB)"
            )
            _progressFlow.value = finalProgress
            listener?.onProgress(finalProgress)
            listener?.onComplete(destinationFile)
            Result.success(destinationFile)
        } catch (e: CancellationException) {
            tempFile.delete()
            val cancelledProgress = _progressFlow.value.copy(
                isDownloading = false,
                statusMessage = "İndirme iptal edildi."
            )
            _progressFlow.value = cancelledProgress
            Result.failure(e)
        } catch (e: Exception) {
            tempFile.delete()
            val errMsg = e.localizedMessage ?: "Ağ indirme hatası"
            val errorProgress = _progressFlow.value.copy(
                isDownloading = false,
                statusMessage = "Hata: $errMsg"
            )
            _progressFlow.value = errorProgress
            Result.failure(e)
        }
    }

    private data class StreamProbe(
        val resolvedUrl: String,
        val contentLength: Long,
        val supportsRange: Boolean,
        val contentType: String
    )

    private fun probeServerStream(url: String, cookieHeader: String?): StreamProbe {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Range", "bytes=0-1")
            if (!cookieHeader.isNullOrBlank()) {
                setRequestProperty("Cookie", cookieHeader)
            }
        }
        try {
            val code = conn.responseCode
            val resolvedUrl = conn.url?.toString() ?: url
            val contentType = conn.contentType.orEmpty()
            val acceptRanges = conn.getHeaderField("Accept-Ranges").orEmpty()
            val contentRange = conn.getHeaderField("Content-Range").orEmpty()

            var totalLength = -1L
            if (contentRange.contains("/")) {
                totalLength = contentRange.substringAfterLast("/").trim().toLongOrNull() ?: -1L
            }
            if (totalLength <= 0L) {
                totalLength = conn.contentLengthLong
            }

            val supportsRange = code == HttpURLConnection.HTTP_PARTIAL ||
                acceptRanges.equals("bytes", ignoreCase = true)

            return StreamProbe(
                resolvedUrl = resolvedUrl,
                contentLength = totalLength,
                supportsRange = supportsRange,
                contentType = contentType
            )
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun downloadMultiThreaded(
        url: String,
        tempFile: File,
        totalBytes: Long,
        cookieHeader: String?,
        listener: DownloadListener?
    ) = coroutineScope {
        RandomAccessFile(tempFile, "rw").use { raf ->
            raf.setLength(totalBytes)
        }

        val chunkSize = totalBytes / threadCount
        val chunkDownloaded = LongArray(threadCount)
        val chunkCompleted = BooleanArray(threadCount)
        val totalDownloadedAtomic = AtomicLong(0L)

        val initialChunks = (0 until threadCount).map { idx ->
            val start = idx * chunkSize
            val end = if (idx == threadCount - 1) totalBytes - 1L else (start + chunkSize - 1L)
            ChunkState(index = idx, startByte = start, endByte = end)
        }

        _progressFlow.value = DownloadProgress(
            downloadedBytes = 0L,
            totalBytes = totalBytes,
            chunks = initialChunks,
            isDownloading = true,
            statusMessage = "$threadCount paralel kanal ile indiriliyor..."
        )

        val monitorJob = launch {
            var lastBytes = 0L
            var lastTime = System.currentTimeMillis()
            while (isActive && !isCancelled.get()) {
                delay(200L)
                val now = System.currentTimeMillis()
                val currentTotal = totalDownloadedAtomic.get()
                val dtMs = (now - lastTime).coerceAtLeast(1L)
                val speed = ((currentTotal - lastBytes) * 1000L) / dtMs
                lastBytes = currentTotal
                lastTime = now

                val updatedChunks = initialChunks.mapIndexed { i, c ->
                    c.copy(
                        downloadedBytes = chunkDownloaded[i],
                        isCompleted = chunkCompleted[i]
                    )
                }
                val p = DownloadProgress(
                    downloadedBytes = currentTotal,
                    totalBytes = totalBytes,
                    speedBytesPerSec = speed.coerceAtLeast(0L),
                    chunks = updatedChunks,
                    isDownloading = true,
                    statusMessage = "İndiriliyor... %${(currentTotal * 100L / totalBytes).coerceIn(0L, 100L)}"
                )
                _progressFlow.value = p
                listener?.onProgress(p)
            }
        }

        try {
            val jobs = initialChunks.map { chunk ->
                async(Dispatchers.IO) {
                    downloadSingleChunkWithRetry(
                        url = url,
                        tempFile = tempFile,
                        chunk = chunk,
                        cookieHeader = cookieHeader,
                        onBytesRead = { delta ->
                            chunkDownloaded[chunk.index] += delta
                            totalDownloadedAtomic.addAndGet(delta)
                        },
                        onChunkReset = { previousBytes ->
                            chunkDownloaded[chunk.index] = 0L
                            totalDownloadedAtomic.addAndGet(-previousBytes)
                        }
                    )
                    chunkCompleted[chunk.index] = true
                }
            }
            jobs.awaitAll()
        } finally {
            monitorJob.cancel()
        }
    }

    private fun downloadSingleChunkWithRetry(
        url: String,
        tempFile: File,
        chunk: ChunkState,
        cookieHeader: String?,
        onBytesRead: (Long) -> Unit,
        onChunkReset: (Long) -> Unit
    ) {
        var attempt = 0
        var localDownloaded = 0L
        while (attempt < 3 && !isCancelled.get()) {
            attempt++
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Range", "bytes=${chunk.startByte}-${chunk.endByte}")
                    if (!cookieHeader.isNullOrBlank()) {
                        setRequestProperty("Cookie", cookieHeader)
                    }
                }
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_PARTIAL && code != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("HTTP $code")
                }

                conn.inputStream.use { input ->
                    RandomAccessFile(tempFile, "rw").use { raf ->
                        raf.seek(chunk.startByte)
                        val buffer = ByteArray(16 * 1024)
                        var remaining = chunk.totalBytes
                        while (remaining > 0L && !isCancelled.get()) {
                            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                            val read = input.read(buffer, 0, toRead)
                            if (read == -1) break
                            raf.write(buffer, 0, read)
                            remaining -= read
                            localDownloaded += read
                            onBytesRead(read.toLong())
                        }
                    }
                }
                if (localDownloaded >= chunk.totalBytes || isCancelled.get()) {
                    return
                }
            } catch (e: Exception) {
                if (attempt >= 3) throw e
                onChunkReset(localDownloaded)
                localDownloaded = 0L
            } finally {
                conn?.disconnect()
            }
        }
    }

    private fun downloadSingleStream(
        url: String,
        tempFile: File,
        expectedTotalBytes: Long,
        cookieHeader: String?,
        listener: DownloadListener?
    ) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("User-Agent", USER_AGENT)
            if (!cookieHeader.isNullOrBlank()) {
                setRequestProperty("Cookie", cookieHeader)
            }
        }

        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Sunucu HTTP $code yanıtı verdi.")
            }
            val total = if (expectedTotalBytes > 0L) expectedTotalBytes else conn.contentLengthLong
            var downloaded = 0L
            var lastReportTime = System.currentTimeMillis()
            var lastReportBytes = 0L

            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (!isCancelled.get()) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime >= 200L) {
                            val dt = (now - lastReportTime).coerceAtLeast(1L)
                            val speed = ((downloaded - lastReportBytes) * 1000L) / dt
                            lastReportTime = now
                            lastReportBytes = downloaded

                            val singleChunk = ChunkState(
                                index = 0,
                                startByte = 0L,
                                endByte = if (total > 0L) total - 1L else downloaded,
                                downloadedBytes = downloaded,
                                isCompleted = false
                            )
                            val p = DownloadProgress(
                                downloadedBytes = downloaded,
                                totalBytes = total,
                                speedBytesPerSec = speed.coerceAtLeast(0L),
                                chunks = listOf(singleChunk),
                                isDownloading = true,
                                statusMessage = if (total > 0L) {
                                    "İndiriliyor... %${(downloaded * 100L / total).coerceIn(0L, 100L)}"
                                } else {
                                    "İndiriliyor... ${downloaded / 1024} KB"
                                }
                            )
                            _progressFlow.value = p
                            listener?.onProgress(p)
                        }
                    }
                    output.flush()
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val USER_AGENT =
            "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    }
}
