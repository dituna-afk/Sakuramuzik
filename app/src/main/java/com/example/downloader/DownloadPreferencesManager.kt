package com.example.downloader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PendingPlaylistQueueState(
    val tracks: List<MediaTrackInfo>,
    val playlistTitle: String?,
    val playlistId: String?,
    val playlistCoverUrl: String?,
    val totalCount: Int,
    val completedCount: Int
)

enum class TranslationEngine(val displayName: String, val subtitle: String) {
    GOOGLE_TRANSLATE("Google Translate", "Hızlı ve ücretsiz küresel çeviri motoru"),
    DEEPL("DeepL Translate", "Yüksek kaliteli yapay zeka çeviri motoru (otomatik Google yedekli)")
}

enum class LyricsSource(
    val id: String,
    val shortBadge: String,
    val displayName: String,
    val subtitle: String
) {
    LRCLIB(
        id = "lrclib",
        shortBadge = "LRCLIB",
        displayName = "LRCLIB (Senkronize LRC Veritabanı)",
        subtitle = "Milyonlarca şarkı için milisaniye hassasiyetli açık kaynak LRC arşivi"
    ),
    YOUTUBE_MUSIC(
        id = "youtube_music",
        shortBadge = "YT Music",
        displayName = "YouTube Music / Altyazı (TimedText LRC)",
        subtitle = "YouTube Music ve video altyazı akışlarından doğrudan zaman damgalı söz çekimi"
    ),
    NETEASE_LRC(
        id = "netease_lrc",
        shortBadge = "NetEase",
        displayName = "NetEase Cloud Music (Senkronize LRC)",
        subtitle = "Özellikle Anime/J-Pop ve küresel parçalar için zengin senkronize LRC kütüphanesi"
    ),
    KUGOU_LRC(
        id = "kugou_lrc",
        shortBadge = "KuGou",
        displayName = "KuGou LRC Veritabanı (Senkronize LRC)",
        subtitle = "Alternatif senkronize LRC arama motoru ve geniş Asya/Avrupa kataloğu"
    ),
    LYRICS_OVH_GENIUS(
        id = "lyrics_ovh_genius",
        shortBadge = "Lyrics.ovh / Genius",
        displayName = "Lyrics.ovh & Genius (Şarkı Sözü Arşivi)",
        subtitle = "Düz metin şarkı sözlerini şarkı süresine göre otomatik zamanlayarak LRC olarak kaydeder"
    );

    companion object {
        val DEFAULT_ORDER: List<LyricsSource> = listOf(
            LRCLIB,
            YOUTUBE_MUSIC,
            NETEASE_LRC,
            KUGOU_LRC,
            LYRICS_OVH_GENIUS
        )

        fun fromId(id: String?): LyricsSource? = entries.firstOrNull { it.id == id || it.name == id }
    }
}

class DownloadPreferencesManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var useAria2c: Boolean
        get() = prefs.getBoolean(KEY_USE_ARIA2C, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_ARIA2C, value).apply()

    var useSponsorBlock: Boolean
        get() = prefs.getBoolean(KEY_USE_SPONSORBLOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_SPONSORBLOCK, value).apply()

    var sponsorBlockCategories: String
        get() = prefs.getString(KEY_SPONSORBLOCK_CATEGORIES, DEFAULT_SPONSORBLOCK_CATEGORIES)
            ?: DEFAULT_SPONSORBLOCK_CATEGORIES
        set(value) = prefs.edit().putString(KEY_SPONSORBLOCK_CATEGORIES, value).apply()

    var embedMetadataAndThumb: Boolean
        get() = prefs.getBoolean(KEY_EMBED_METADATA_THUMB, true)
        set(value) = prefs.edit().putBoolean(KEY_EMBED_METADATA_THUMB, value).apply()

    var writeAutoSubtitlesLrc: Boolean
        get() = prefs.getBoolean(KEY_WRITE_AUTO_SUBTITLES, true)
        set(value) = prefs.edit().putBoolean(KEY_WRITE_AUTO_SUBTITLES, value).apply()

    var isPlaylistsGridLayout: Boolean
        get() = prefs.getBoolean(KEY_PLAYLISTS_GRID_LAYOUT, false)
        set(value) = prefs.edit().putBoolean(KEY_PLAYLISTS_GRID_LAYOUT, value).apply()

    var concurrentFragments: Int
        get() = prefs.getInt(KEY_CONCURRENT_FRAGMENTS, 4).coerceIn(1, 16)
        set(value) = prefs.edit().putInt(KEY_CONCURRENT_FRAGMENTS, value.coerceIn(1, 16)).apply()

    var autoTranslateLyricsToTurkish: Boolean
        get() = prefs.getBoolean(KEY_AUTO_TRANSLATE_LYRICS, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_TRANSLATE_LYRICS, value).apply()

    var translationEngine: TranslationEngine
        get() {
            val saved = prefs.getString(KEY_TRANSLATION_ENGINE, TranslationEngine.GOOGLE_TRANSLATE.name)
            return TranslationEngine.entries.firstOrNull { it.name == saved } ?: TranslationEngine.GOOGLE_TRANSLATE
        }
        set(value) = prefs.edit().putString(KEY_TRANSLATION_ENGINE, value.name).apply()

    var lyricsSourceOrder: List<LyricsSource>
        get() {
            val raw = prefs.getString(KEY_LYRICS_SOURCE_ORDER, null)
            if (raw.isNullOrBlank()) return LyricsSource.DEFAULT_ORDER
            val parsed = raw.split(",")
                .mapNotNull { LyricsSource.fromId(it.trim()) }
                .distinct()
                .toMutableList()
            LyricsSource.DEFAULT_ORDER.forEach { src ->
                if (src !in parsed) parsed.add(src)
            }
            return parsed
        }
        set(value) {
            val normalized = value.distinct().toMutableList()
            LyricsSource.DEFAULT_ORDER.forEach { src ->
                if (src !in normalized) normalized.add(src)
            }
            prefs.edit().putString(KEY_LYRICS_SOURCE_ORDER, normalized.joinToString(",") { it.id }).apply()
        }

    var enabledLyricsSources: Set<LyricsSource>
        get() {
            val raw = prefs.getString(KEY_ENABLED_LYRICS_SOURCES, null)
            if (raw == null) return LyricsSource.DEFAULT_ORDER.toSet()
            if (raw.isBlank()) return setOf(LyricsSource.LRCLIB)
            val parsed = raw.split(",")
                .mapNotNull { LyricsSource.fromId(it.trim()) }
                .toSet()
            return parsed.ifEmpty { setOf(LyricsSource.LRCLIB) }
        }
        set(value) {
            val safeSet = value.ifEmpty { setOf(LyricsSource.LRCLIB) }
            prefs.edit().putString(KEY_ENABLED_LYRICS_SOURCES, safeSet.joinToString(",") { it.id }).apply()
        }

    fun getOrderedActiveLyricsSources(): List<LyricsSource> {
        val enabled = enabledLyricsSources
        val ordered = lyricsSourceOrder.filter { it in enabled }
        return ordered.ifEmpty { listOf(LyricsSource.LRCLIB) }
    }

    var defaultDownloadFormat: DownloadFormatOption
        get() {
            val saved = prefs.getString(KEY_DEFAULT_DOWNLOAD_FORMAT, DownloadFormatOption.AUDIO_OPUS_BEST.name)
            return DownloadFormatOption.fromName(saved)
        }
        set(value) = prefs.edit().putString(KEY_DEFAULT_DOWNLOAD_FORMAT, value.name).apply()

    fun savePendingDownloadQueue(
        tracks: List<MediaTrackInfo>,
        playlistTitle: String?,
        playlistId: String?,
        playlistCoverUrl: String?,
        totalCount: Int,
        completedCount: Int
    ) {
        if (tracks.isEmpty()) {
            clearPendingDownloadQueue()
            return
        }
        try {
            val root = JSONObject().apply {
                put("playlistTitle", playlistTitle ?: "")
                put("playlistId", playlistId ?: "")
                put("playlistCoverUrl", playlistCoverUrl ?: "")
                put("totalCount", totalCount)
                put("completedCount", completedCount)
                val arr = JSONArray()
                tracks.forEach { track ->
                    arr.put(
                        JSONObject().apply {
                            put("id", track.id)
                            put("title", track.title)
                            put("artist", track.artist)
                            put("album", track.album)
                            put("durationSeconds", track.durationSeconds)
                            put("streamUrl", track.streamUrl)
                            put("thumbnailUrl", track.thumbnailUrl ?: "")
                            put("sourceUrl", track.sourceUrl)
                            put("mimeType", track.mimeType)
                            put("playlistId", track.playlistId ?: "")
                            put("playlistTitle", track.playlistTitle ?: "")
                            put("playlistCoverUrl", track.playlistCoverUrl ?: "")
                        }
                    )
                }
                put("tracks", arr)
            }
            prefs.edit().putString(KEY_PENDING_PLAYLIST_QUEUE, root.toString()).commit()
        } catch (_: Exception) {
        }
    }

    fun loadPendingDownloadQueue(): PendingPlaylistQueueState? {
        val raw = prefs.getString(KEY_PENDING_PLAYLIST_QUEUE, null)?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val root = JSONObject(raw)
            val playlistTitle = root.optString("playlistTitle").takeIf { it.isNotBlank() }
            val playlistId = root.optString("playlistId").takeIf { it.isNotBlank() }
            val playlistCoverUrl = root.optString("playlistCoverUrl").takeIf { it.isNotBlank() }
            val totalCount = root.optInt("totalCount", 0)
            val completedCount = root.optInt("completedCount", 0)
            val arr = root.optJSONArray("tracks") ?: return null
            val list = mutableListOf<MediaTrackInfo>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val title = obj.optString("title")
                if (id.isBlank() || title.isBlank()) continue
                val trackPlaylistTitle = obj.optString("playlistTitle").takeIf { it.isNotBlank() } ?: playlistTitle
                val trackPlaylistId = obj.optString("playlistId").takeIf { it.isNotBlank() } ?: playlistId
                val trackPlaylistCover = obj.optString("playlistCoverUrl").takeIf { it.isNotBlank() } ?: playlistCoverUrl
                val album = obj.optString("album").takeIf { it.isNotBlank() }
                    ?: trackPlaylistTitle
                    ?: "Sakura Music"
                list.add(
                    MediaTrackInfo(
                        id = id,
                        title = title,
                        artist = obj.optString("artist", "YouTube"),
                        album = album,
                        durationSeconds = obj.optLong("durationSeconds", 0L),
                        thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        sourceUrl = obj.optString("sourceUrl", "https://www.youtube.com/watch?v=$id"),
                        streamUrl = obj.optString("streamUrl", ""),
                        mimeType = obj.optString("mimeType", "audio/mp4"),
                        playlistId = trackPlaylistId,
                        playlistTitle = trackPlaylistTitle,
                        playlistCoverUrl = trackPlaylistCover
                    )
                )
            }
            if (list.isEmpty()) return null
            PendingPlaylistQueueState(
                tracks = list,
                playlistTitle = playlistTitle,
                playlistId = playlistId,
                playlistCoverUrl = playlistCoverUrl,
                totalCount = totalCount.coerceAtLeast(list.size),
                completedCount = completedCount.coerceAtLeast(0)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clearPendingDownloadQueue() {
        prefs.edit().remove(KEY_PENDING_PLAYLIST_QUEUE).commit()
    }

    companion object {
        private const val PREFS_NAME = "sakura_download_engine_prefs"
        private const val KEY_DEFAULT_DOWNLOAD_FORMAT = "default_download_format"
        private const val KEY_PLAYLISTS_GRID_LAYOUT = "playlists_grid_layout_saved"
        private const val KEY_USE_ARIA2C = "use_aria2c_downloader"
        private const val KEY_USE_SPONSORBLOCK = "use_sponsorblock"
        private const val KEY_SPONSORBLOCK_CATEGORIES = "sponsorblock_categories"
        private const val KEY_EMBED_METADATA_THUMB = "embed_metadata_and_thumb"
        private const val KEY_WRITE_AUTO_SUBTITLES = "write_auto_subtitles_lrc"
        private const val KEY_CONCURRENT_FRAGMENTS = "concurrent_fragments"
        private const val KEY_AUTO_TRANSLATE_LYRICS = "auto_translate_lyrics_tr"
        private const val KEY_TRANSLATION_ENGINE = "translation_engine_type"
        private const val KEY_LYRICS_SOURCE_ORDER = "lyrics_source_priority_order_v1"
        private const val KEY_ENABLED_LYRICS_SOURCES = "enabled_lyrics_sources_v1"
        private const val KEY_PENDING_PLAYLIST_QUEUE = "pending_playlist_download_queue_v1"
        const val DEFAULT_SPONSORBLOCK_CATEGORIES = "sponsor,intro,outro,selfpromo,interaction,music_offtopic"
    }
}

