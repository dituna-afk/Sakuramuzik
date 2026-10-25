package com.example.downloader

import android.content.Context
import com.example.SakuraMusicApplication
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class YoutubeDLUpdateResult {
    data class Updated(val version: String, val message: String) : YoutubeDLUpdateResult()
    data class AlreadyUpToDate(val version: String, val message: String) : YoutubeDLUpdateResult()
    data class Error(val errorMessage: String) : YoutubeDLUpdateResult()
}

class YoutubeDLUpdater(private val context: Context) {
    private val appContext = context.applicationContext

    suspend fun getCurrentVersion(): String = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!SakuraMusicApplication.ensureYoutubeDLInitialized(appContext)) {
                return@withContext "Başlatılamadı"
            }
            YoutubeDL.getInstance().version(appContext) ?: "Bilinmiyor"
        } catch (_: Throwable) {
            "Bilinmiyor"
        }
    }

    suspend fun updateEngine(
        useNightlyChannel: Boolean = false
    ): YoutubeDLUpdateResult = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!SakuraMusicApplication.ensureYoutubeDLInitialized(appContext)) {
                return@withContext YoutubeDLUpdateResult.Error(
                    SakuraMusicApplication.youtubeDLInitError
                        ?: "yt-dlp motoru başlatılamadığı için güncellenemedi."
                )
            }

            val channel = if (useNightlyChannel) {
                YoutubeDL.UpdateChannel._NIGHTLY
            } else {
                YoutubeDL.UpdateChannel._STABLE
            }

            val status = YoutubeDL.getInstance().updateYoutubeDL(appContext, channel)
            val currentVersion = getCurrentVersion()

            when (status) {
                YoutubeDL.UpdateStatus.DONE -> YoutubeDLUpdateResult.Updated(
                    version = currentVersion,
                    message = "yt-dlp motoru başarıyla güncellendi (Sürüm: $currentVersion)."
                )
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> YoutubeDLUpdateResult.AlreadyUpToDate(
                    version = currentVersion,
                    message = "yt-dlp motoru zaten güncel (Sürüm: $currentVersion)."
                )
                else -> YoutubeDLUpdateResult.AlreadyUpToDate(
                    version = currentVersion,
                    message = "yt-dlp sürüm kontrolü tamamlandı ($currentVersion)."
                )
            }
        } catch (e: Throwable) {
            YoutubeDLUpdateResult.Error(
                "yt-dlp güncelleme hatası: ${e.localizedMessage ?: "Ağ veya ikili dosya hatası"}"
            )
        }
    }
}
