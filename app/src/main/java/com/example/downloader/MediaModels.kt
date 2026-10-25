package com.example.downloader

import java.io.File

data class MediaTrackInfo(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "Sakura Music",
    val durationSeconds: Long,
    val thumbnailUrl: String?,
    val sourceUrl: String,
    val streamUrl: String,
    val mimeType: String = "audio/mp4",
    val bitrateKbps: Int = 128,
    val contentLengthBytes: Long = -1L,
    val playlistId: String? = null,
    val playlistTitle: String? = null,
    val playlistCoverUrl: String? = null
) {
    fun effectivePlaylistName(): String? {
        val explicit = playlistTitle?.trim().orEmpty()
        if (isMeaningfulPlaylistName(explicit)) return explicit
        val candidateAlbum = album.trim()
        if (isMeaningfulPlaylistName(candidateAlbum)) return candidateAlbum
        return null
    }

    companion object {
        private val GENERIC_ALBUM_LABELS = setOf(
            "",
            "sakura music",
            "sakura müzik",
            "sakura indirici",
            "youtube music",
            "youtube",
            "youtube video",
            "youtube playlist",
            "doğrudan medya",
            "dogrudan medya",
            "bilinmeyen albüm",
            "unknown album",
            "single",
            "track"
        )

        fun isMeaningfulPlaylistName(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val normalized = name.trim().lowercase(java.util.Locale.ROOT)
            return normalized !in GENERIC_ALBUM_LABELS
        }
    }
}

data class SavedMediaResult(
    val audioFile: File,
    val coverFile: File?,
    val mediaStoreUri: String?,
    val trackInfo: MediaTrackInfo
)

interface MediaSaveCallback {
    fun onStageChanged(stageMessage: String)
    fun onSaved(result: SavedMediaResult)
    fun onSaveError(errorMessage: String)
}

enum class DownloadFormatOption(
    val displayName: String,
    val description: String,
    val formatSelector: String,
    val audioFormatArg: String?,
    val audioQualityArg: String?,
    val expectedExtension: String,
    val isAudioExtraction: Boolean
) {
    AUDIO_M4A_HIGH(
        displayName = "En İyi Ses (M4A / AAC)",
        description = "Yüksek kaliteli M4A ses akışı ve kapak fotoğrafı gömme",
        formatSelector = "ba[ext=m4a]/ba/b",
        audioFormatArg = "m4a",
        audioQualityArg = "0",
        expectedExtension = "m4a",
        isAudioExtraction = true
    ),
    AUDIO_MP3_320(
        displayName = "MP3 (320 kbps)",
        description = "FFmpeg ile 320kbps MP3 dönüştürme ve ID3v2 etiketleme",
        formatSelector = "ba/b",
        audioFormatArg = "mp3",
        audioQualityArg = "320K",
        expectedExtension = "mp3",
        isAudioExtraction = true
    ),
    AUDIO_OPUS_BEST(
        displayName = "Opus (En Yüksek Kalite)",
        description = "YouTube/SoundCloud doğal yüksek bit hızlı Opus ses akışı",
        formatSelector = "ba[acodec=opus]/ba/b",
        audioFormatArg = "opus",
        audioQualityArg = "0",
        expectedExtension = "opus",
        isAudioExtraction = true
    ),
    VIDEO_MP4_1080P(
        displayName = "Video (1080p MP4)",
        description = "Full HD MP4 video + AAC ses birleştirme (FFmpeg)",
        formatSelector = "bv*[height<=1080][ext=mp4]+ba[ext=m4a]/b[height<=1080][ext=mp4]/b",
        audioFormatArg = null,
        audioQualityArg = null,
        expectedExtension = "mp4",
        isAudioExtraction = false
    );

    companion object {
        fun fromName(name: String?): DownloadFormatOption {
            return entries.find { it.name == name } ?: AUDIO_M4A_HIGH
        }
    }
}

sealed class YoutubeDLDownloadState {
    data object Idle : YoutubeDLDownloadState()

    data class Initializing(
        val message: String = "yt-dlp ve FFmpeg motoru hazırlanıyor..."
    ) : YoutubeDLDownloadState()

    data class FetchingInfo(
        val url: String,
        val message: String = "Medya ve akış bilgileri çözümleniyor..."
    ) : YoutubeDLDownloadState()

    data class Downloading(
        val processId: String,
        val title: String,
        val progressPercent: Float,
        val etaSeconds: Long,
        val statusLine: String,
        val formatOption: DownloadFormatOption
    ) : YoutubeDLDownloadState()

    data class SavingMetadata(
        val title: String,
        val message: String = "Ses dosyası, kapak fotoğrafı ve etiketler doğrulanıyor..."
    ) : YoutubeDLDownloadState()

    data class Success(
        val savedMedia: SavedMediaResult
    ) : YoutubeDLDownloadState()

    data class Cancelled(
        val message: String = "İndirme işlemi kullanıcı tarafından iptal edildi."
    ) : YoutubeDLDownloadState()

    data class Error(
        val message: String,
        val technicalDetails: String? = null
    ) : YoutubeDLDownloadState()
}

enum class LikedSongsSourceType(
    val displayName: String,
    val description: String,
    val isMusicOnly: Boolean
) {
    YOUTUBE_MUSIC(
        displayName = "YT Müzik Şarkıları",
        description = "YouTube Music üzerinde beğenilen parçalar (LM)",
        isMusicOnly = true
    ),
    YOUTUBE_VIDEOS(
        displayName = "YT Beğenilen Videolar",
        description = "YouTube ana hesabında beğenilen tüm videolar (LL)",
        isMusicOnly = false
    )
}
