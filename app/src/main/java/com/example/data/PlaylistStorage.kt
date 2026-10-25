package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.downloader.MediaTrackInfo
import com.example.downloader.SavedMediaResult
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class UserPlaylist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val coverUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val tracks: List<SavedMediaResult> = emptyList()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val coverUri: String?,
    val createdAt: Long,
    val tracksJson: String
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun getAllPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: String)
}

@Database(entities = [PlaylistEntity::class], version = 1, exportSchema = false)
abstract class PlaylistDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: PlaylistDatabase? = null

        fun getInstance(context: Context): PlaylistDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlaylistDatabase::class.java,
                    "sakura_playlists.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class OfflinePlaylistRepository(context: Context) {
    private val appContext = context.applicationContext
    private val playlistDao = PlaylistDatabase.getInstance(appContext).playlistDao()
    private val legacyPrefs = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    private val metadataManager by lazy { com.example.downloader.MediaMetadataManager(appContext) }

    val playlistsFlow: Flow<List<UserPlaylist>> = playlistDao.observeAllPlaylists().map { list ->
        list.map { it.toDomain() }
    }.catch { e ->
        android.util.Log.e("OfflinePlaylistRepo", "Error observing playlists flow: ${e.message}")
        emit(emptyList())
    }

    suspend fun initializeAndMigrateIfNeeded() = withContext(Dispatchers.IO) {
        try {
            if (!legacyPrefs.getBoolean(KEY_MIGRATED_TO_ROOM, false)) {
                val rawLegacy = legacyPrefs.getString(KEY_PLAYLISTS_JSON, null)
                if (!rawLegacy.isNullOrBlank()) {
                    try {
                        val arr = JSONArray(rawLegacy)
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val playlist = parsePlaylistJson(obj)
                            playlistDao.insertOrUpdatePlaylist(playlist.toEntity())
                        }
                    } catch (_: Exception) {
                    }
                }
                legacyPrefs.edit().putBoolean(KEY_MIGRATED_TO_ROOM, true).apply()
            }
        } catch (e: Throwable) {
            android.util.Log.w("OfflinePlaylistRepo", "Migration note: ${e.message}")
        }
    }

    suspend fun loadPlaylists(): List<UserPlaylist> = withContext(Dispatchers.IO) {
        try {
            initializeAndMigrateIfNeeded()
            playlistDao.getAllPlaylists().map { it.toDomain() }
        } catch (e: Throwable) {
            android.util.Log.e("OfflinePlaylistRepo", "Error loading playlists: ${e.message}")
            emptyList()
        }
    }

    suspend fun createPlaylist(
        name: String,
        description: String,
        coverImageUri: String?
    ): List<UserPlaylist> = withContext(Dispatchers.IO) {
        val cleanName = name.trim().ifEmpty { "Yeni Liste" }
        val persistedCoverPath = persistCoverImageIfNeeded(coverImageUri, cleanName, null)
        val newPlaylist = UserPlaylist(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            description = description.trim(),
            coverUri = persistedCoverPath,
            createdAt = System.currentTimeMillis(),
            tracks = emptyList()
        )
        playlistDao.insertOrUpdatePlaylist(newPlaylist.toEntity())
        loadPlaylists()
    }

    suspend fun updatePlaylistCover(
        playlistId: String,
        newCoverUri: String?
    ): List<UserPlaylist> = withContext(Dispatchers.IO) {
        val existing = playlistDao.getPlaylistById(playlistId)?.toDomain() ?: return@withContext loadPlaylists()
        val persistedCoverPath = persistCoverImageIfNeeded(newCoverUri, existing.name, playlistId)
        val updated = existing.copy(coverUri = persistedCoverPath)
        playlistDao.insertOrUpdatePlaylist(updated.toEntity())
        loadPlaylists()
    }

    suspend fun deletePlaylist(playlistId: String): List<UserPlaylist> = withContext(Dispatchers.IO) {
        removeSourcePlaylistBinding(playlistId)
        playlistDao.deletePlaylistById(playlistId)
        loadPlaylists()
    }

    suspend fun addTrackToPlaylist(
        playlistId: String,
        track: SavedMediaResult
    ): List<UserPlaylist> = withContext(Dispatchers.IO) {
        val existing = playlistDao.getPlaylistById(playlistId)?.toDomain() ?: return@withContext loadPlaylists()
        val alreadyExists = existing.tracks.any {
            it.audioFile.absolutePath == track.audioFile.absolutePath ||
                (it.trackInfo.id.isNotBlank() && it.trackInfo.id == track.trackInfo.id)
        }
        val updatedCover = if (existing.coverUri.isNullOrBlank()) {
            track.coverFile?.takeIf { it.exists() }?.absolutePath ?: existing.coverUri
        } else {
            existing.coverUri
        }
        if (!alreadyExists || updatedCover != existing.coverUri) {
            val updatedTracks = if (alreadyExists) existing.tracks else existing.tracks + track
            val updated = existing.copy(
                coverUri = updatedCover,
                tracks = updatedTracks
            )
            playlistDao.insertOrUpdatePlaylist(updated.toEntity())
        }
        markTrackReconciled(playlistId, track.trackInfo.id.ifBlank { track.audioFile.absolutePath })
        loadPlaylists()
    }

    suspend fun removeTrackFromPlaylist(
        playlistId: String,
        audioFilePath: String
    ): List<UserPlaylist> = withContext(Dispatchers.IO) {
        val existing = playlistDao.getPlaylistById(playlistId)?.toDomain() ?: return@withContext loadPlaylists()
        val removedTrack = existing.tracks.firstOrNull { it.audioFile.absolutePath == audioFilePath }
        if (removedTrack != null) {
            markTrackReconciled(playlistId, removedTrack.trackInfo.id.ifBlank { audioFilePath })
        }
        val updated = existing.copy(
            tracks = existing.tracks.filterNot { it.audioFile.absolutePath == audioFilePath }
        )
        playlistDao.insertOrUpdatePlaylist(updated.toEntity())
        loadPlaylists()
    }

    /**
     * İndirilen bir oynatma listesi için gerçek adıyla (veya aynı isimli farklı bir liste varsa çakışmayı önleyerek
     * numaralandırılmış adıyla) yerel bir UserPlaylist oluşturur veya mevcut olanı bulur.
     * Kapak görselini mümkünse indirip yerel olarak kaydeder; indirilemezse indirme işlemini bozmaz ve yedek/varsayılan kapak kullanır.
     */
    suspend fun ensurePlaylistForDownload(
        rawPlaylistTitle: String,
        sourcePlaylistId: String?,
        coverUrl: String?,
        fallbackTrackCoverPath: String? = null
    ): UserPlaylist = withContext(Dispatchers.IO) {
        initializeAndMigrateIfNeeded()
        val baseTitle = normalizeDownloadedPlaylistTitle(rawPlaylistTitle)
        val cleanSourceId = sourcePlaylistId?.trim()?.takeIf { it.isNotBlank() }
        val allPlaylists = playlistDao.getAllPlaylists().map { it.toDomain() }
        val sourceBindings = loadSourcePlaylistBindings()

        // 1. Eğer aynı kaynak playlistId ile daha önce oluşturulmuş bir liste varsa doğrudan onu kullan (yarıda kalan/yeniden başlatılan indirmeler)
        if (cleanSourceId != null) {
            val matchedBySourceId = allPlaylists.firstOrNull { pl ->
                val boundSource = sourceBindings[pl.id] ?: extractSourceIdFromPlaylist(pl)
                boundSource != null && boundSource.equals(cleanSourceId, ignoreCase = true)
            }
            if (matchedBySourceId != null) {
                val updatedCover = resolvePlaylistCoverSafe(
                    existingCover = matchedBySourceId.coverUri,
                    playlistName = matchedBySourceId.name,
                    sourcePlaylistId = cleanSourceId,
                    coverUrl = coverUrl,
                    fallbackTrackCoverPath = fallbackTrackCoverPath
                )
                if (updatedCover != matchedBySourceId.coverUri) {
                    val updated = matchedBySourceId.copy(coverUri = updatedCover)
                    playlistDao.insertOrUpdatePlaylist(updated.toEntity())
                    return@withContext updated
                }
                return@withContext matchedBySourceId
            }
        }

        // 2. Aynı isimde mevcut bir liste var mı kontrol et
        val sameNamePlaylist = allPlaylists.firstOrNull { it.name.equals(baseTitle, ignoreCase = true) }
        if (sameNamePlaylist != null) {
            val existingSourceId = sourceBindings[sameNamePlaylist.id] ?: extractSourceIdFromPlaylist(sameNamePlaylist)
            val isSameOrCompatibleSource = cleanSourceId == null ||
                existingSourceId == null ||
                existingSourceId.equals(cleanSourceId, ignoreCase = true)

            if (isSameOrCompatibleSource) {
                if (cleanSourceId != null) {
                    saveSourcePlaylistBinding(sameNamePlaylist.id, cleanSourceId)
                }
                val updatedCover = resolvePlaylistCoverSafe(
                    existingCover = sameNamePlaylist.coverUri,
                    playlistName = sameNamePlaylist.name,
                    sourcePlaylistId = cleanSourceId,
                    coverUrl = coverUrl,
                    fallbackTrackCoverPath = fallbackTrackCoverPath
                )
                val updatedDesc = if (cleanSourceId != null && !sameNamePlaylist.description.contains(cleanSourceId)) {
                    buildDownloadedPlaylistDescription(cleanSourceId)
                } else {
                    sameNamePlaylist.description
                }
                if (updatedCover != sameNamePlaylist.coverUri || updatedDesc != sameNamePlaylist.description) {
                    val updated = sameNamePlaylist.copy(
                        coverUri = updatedCover,
                        description = updatedDesc
                    )
                    playlistDao.insertOrUpdatePlaylist(updated.toEntity())
                    return@withContext updated
                }
                return@withContext sameNamePlaylist
            }
        }

        // 3. Aynı isimli fakat FARKLI kaynaklı bir oynatma listesi varsa mevcut listeyi ezmemek için yeni isim üret ("Liste (2)" vb.)
        val existingNamesLower = allPlaylists.map { it.name.trim().lowercase(java.util.Locale.ROOT) }.toSet()
        var finalName = baseTitle
        if (finalName.lowercase(java.util.Locale.ROOT) in existingNamesLower) {
            var counter = 2
            while (counter <= 999) {
                val candidate = "$baseTitle ($counter)"
                if (candidate.lowercase(java.util.Locale.ROOT) !in existingNamesLower) {
                    finalName = candidate
                    break
                }
                counter++
            }
        }

        val resolvedCover = resolvePlaylistCoverSafe(
            existingCover = null,
            playlistName = finalName,
            sourcePlaylistId = cleanSourceId,
            coverUrl = coverUrl,
            fallbackTrackCoverPath = fallbackTrackCoverPath
        )

        val newPlaylist = UserPlaylist(
            id = UUID.randomUUID().toString(),
            name = finalName,
            description = buildDownloadedPlaylistDescription(cleanSourceId),
            coverUri = resolvedCover,
            createdAt = System.currentTimeMillis(),
            tracks = emptyList()
        )
        if (cleanSourceId != null) {
            saveSourcePlaylistBinding(newPlaylist.id, cleanSourceId)
        }
        playlistDao.insertOrUpdatePlaylist(newPlaylist.toEntity())
        newPlaylist
    }

    /**
     * İndirilen bir şarkıyı ait olduğu oynatma listesiyle ilişkilendirir ve Room veritabanına kaydeder.
     */
    suspend fun registerDownloadedPlaylistTrack(
        saved: SavedMediaResult,
        explicitPlaylistTitle: String? = null,
        explicitPlaylistId: String? = null,
        explicitPlaylistCoverUrl: String? = null
    ): List<UserPlaylist> = withContext(Dispatchers.IO) {
        val rawTitle = explicitPlaylistTitle?.takeIf { MediaTrackInfo.isMeaningfulPlaylistName(it) }
            ?: saved.trackInfo.effectivePlaylistName()
            ?: return@withContext loadPlaylists()

        val sourcePlaylistId = explicitPlaylistId?.takeIf { it.isNotBlank() }
            ?: saved.trackInfo.playlistId?.takeIf { it.isNotBlank() }
        val coverUrl = explicitPlaylistCoverUrl?.takeIf { it.isNotBlank() }
            ?: saved.trackInfo.playlistCoverUrl?.takeIf { it.isNotBlank() }
        val fallbackCover = saved.coverFile?.takeIf { it.exists() }?.absolutePath
            ?: saved.trackInfo.thumbnailUrl

        val targetPlaylist = ensurePlaylistForDownload(
            rawPlaylistTitle = rawTitle,
            sourcePlaylistId = sourcePlaylistId,
            coverUrl = coverUrl,
            fallbackTrackCoverPath = fallbackCover
        )

        // Eğer aynı isimli farklı bir liste çakışması nedeniyle liste adı "Liste (2)" olarak ayrıştırıldıysa,
        // şarkının yan veri (.meta.json) ve Room kaydındaki oynatma listesi adını da bu gerçek liste adıyla eşitle.
        val normalizedSaved = if (!saved.trackInfo.album.equals(targetPlaylist.name, ignoreCase = true) ||
            !saved.trackInfo.playlistTitle.equals(targetPlaylist.name, ignoreCase = true)
        ) {
            val updatedTrackInfo = saved.trackInfo.copy(
                album = targetPlaylist.name,
                playlistId = sourcePlaylistId ?: saved.trackInfo.playlistId,
                playlistTitle = targetPlaylist.name,
                playlistCoverUrl = targetPlaylist.coverUri ?: coverUrl
            )
            saved.copy(trackInfo = updatedTrackInfo)
        } else {
            saved
        }

        val currentEntity = playlistDao.getPlaylistById(targetPlaylist.id)?.toDomain() ?: targetPlaylist
        val existingIdx = currentEntity.tracks.indexOfFirst {
            it.audioFile.absolutePath == normalizedSaved.audioFile.absolutePath ||
                (it.trackInfo.id.isNotBlank() && it.trackInfo.id == normalizedSaved.trackInfo.id)
        }

        val updatedTracks = if (existingIdx >= 0) {
            currentEntity.tracks.toMutableList().apply { set(existingIdx, normalizedSaved) }
        } else {
            currentEntity.tracks + normalizedSaved
        }

        val updatedCover = if (currentEntity.coverUri.isNullOrBlank() || !isLocalCoverFileValid(currentEntity.coverUri)) {
            resolvePlaylistCoverSafe(
                existingCover = currentEntity.coverUri,
                playlistName = currentEntity.name,
                sourcePlaylistId = sourcePlaylistId,
                coverUrl = coverUrl,
                fallbackTrackCoverPath = fallbackCover
            )
        } else {
            currentEntity.coverUri
        }

        val updatedPlaylist = currentEntity.copy(
            coverUri = updatedCover,
            tracks = updatedTracks
        )
        playlistDao.insertOrUpdatePlaylist(updatedPlaylist.toEntity())
        markTrackReconciled(
            updatedPlaylist.id,
            normalizedSaved.trackInfo.id.ifBlank { normalizedSaved.audioFile.absolutePath }
        )
        loadPlaylists()
    }

    /**
     * Uygulama kapanıp açıldığında veya indirme yarıda kaldığında diskteki şarkıların (.meta.json / Room)
     * oynatma listesi ilişkilerini kontrol eder ve eksik kalanları ilgili oynatma listelerine bağlar.
     */
    suspend fun reconcileDownloadedTracksWithPlaylists(
        downloadedTracks: List<SavedMediaResult>
    ): List<UserPlaylist> = withContext(Dispatchers.IO) {
        try {
            initializeAndMigrateIfNeeded()
            val tracksWithPlaylist = downloadedTracks.filter {
                it.trackInfo.effectivePlaylistName() != null
            }
            if (tracksWithPlaylist.isEmpty()) {
                return@withContext loadPlaylists()
            }

            val currentPlaylists = playlistDao.getAllPlaylists().map { it.toDomain() }
            val reconciledKeys = loadReconciledTrackKeys()

            // Şarkıları oynatma listesi anahtarına (id veya isim) göre grupla
            for (saved in tracksWithPlaylist) {
                val plName = saved.trackInfo.effectivePlaylistName() ?: continue
                val plId = saved.trackInfo.playlistId?.takeIf { it.isNotBlank() }
                val trackKey = "${plId ?: plName.lowercase(java.util.Locale.ROOT)}::${saved.trackInfo.id.ifBlank { saved.audioFile.name }}"

                val alreadyInAnyMatchingPlaylist = currentPlaylists.any { pl ->
                    (pl.name.equals(plName, ignoreCase = true) ||
                        (plId != null && extractSourceIdFromPlaylist(pl).equals(plId, ignoreCase = true))) &&
                        pl.tracks.any { t ->
                            t.audioFile.absolutePath == saved.audioFile.absolutePath ||
                                (t.trackInfo.id.isNotBlank() && t.trackInfo.id == saved.trackInfo.id)
                        }
                }

                if (!alreadyInAnyMatchingPlaylist && trackKey !in reconciledKeys) {
                    registerDownloadedPlaylistTrack(
                        saved = saved,
                        explicitPlaylistTitle = plName,
                        explicitPlaylistId = plId,
                        explicitPlaylistCoverUrl = saved.trackInfo.playlistCoverUrl
                    )
                    reconciledKeys.add(trackKey)
                }
            }
            saveReconciledTrackKeys(reconciledKeys)
        } catch (e: Throwable) {
            android.util.Log.w("OfflinePlaylistRepo", "Reconcile note: ${e.message}")
        }
        loadPlaylists()
    }

    private fun normalizeDownloadedPlaylistTitle(rawTitle: String): String {
        val trimmed = rawTitle.trim()
        if (trimmed.isBlank()) return "İndirilen Oynatma Listesi"
        if (trimmed.contains("Beğenilen Şarkılar", ignoreCase = true) ||
            trimmed.equals("Favoriler", ignoreCase = true)
        ) {
            return trimmed
        }
        return trimmed
    }

    private fun buildDownloadedPlaylistDescription(sourcePlaylistId: String?): String {
        return if (!sourcePlaylistId.isNullOrBlank()) {
            "İndirilen oynatma listesi • Kaynak: $sourcePlaylistId"
        } else {
            "İndirilen oynatma listesi"
        }
    }

    private fun extractSourceIdFromPlaylist(playlist: UserPlaylist): String? {
        val desc = playlist.description
        if (desc.contains("Kaynak:")) {
            val after = desc.substringAfter("Kaynak:").trim().substringBefore(" ").trim()
            if (after.isNotBlank()) return after
        }
        return playlist.tracks.firstOrNull { !it.trackInfo.playlistId.isNullOrBlank() }?.trackInfo?.playlistId
    }

    private suspend fun resolvePlaylistCoverSafe(
        existingCover: String?,
        playlistName: String,
        sourcePlaylistId: String?,
        coverUrl: String?,
        fallbackTrackCoverPath: String?
    ): String? {
        if (isLocalCoverFileValid(existingCover)) {
            return existingCover
        }
        // 1. Önce oynatma listesinin kendi kapak URL'sini (varsa) indirip kaydetmeyi dene
        if (!coverUrl.isNullOrBlank()) {
            val savedCover = metadataManager.downloadAndSavePlaylistCover(
                playlistName = playlistName,
                playlistId = sourcePlaylistId,
                coverUrl = coverUrl
            )
            if (savedCover != null && savedCover.exists() && savedCover.length() > 512L) {
                return savedCover.absolutePath
            }
        }
        // 2. Oynatma listesi kapağı alınamazsa indirme başarısız olmasın; ilk parçanın yerel kapağını veya varsayılan kapağı (null) kullan
        if (!fallbackTrackCoverPath.isNullOrBlank()) {
            val fallbackFile = File(fallbackTrackCoverPath)
            if (fallbackFile.exists() && fallbackFile.length() > 512L) {
                return fallbackFile.absolutePath
            }
            val downloadedFallback = metadataManager.downloadAndSavePlaylistCover(
                playlistName = playlistName,
                playlistId = sourcePlaylistId,
                coverUrl = fallbackTrackCoverPath
            )
            if (downloadedFallback != null && downloadedFallback.exists() && downloadedFallback.length() > 512L) {
                return downloadedFallback.absolutePath
            }
        }
        return existingCover?.takeIf { isLocalCoverFileValid(it) }
    }

    private fun isLocalCoverFileValid(pathOrUri: String?): Boolean {
        if (pathOrUri.isNullOrBlank()) return false
        val f = File(pathOrUri)
        return f.exists() && f.isFile && f.length() > 512L
    }

    private suspend fun persistCoverImageIfNeeded(
        uriString: String?,
        playlistName: String = "playlist",
        playlistId: String? = null
    ): String? {
        if (uriString.isNullOrBlank()) return null
        val directFile = File(uriString)
        if (directFile.exists() && directFile.length() > 0L) return directFile.absolutePath
        if (uriString.startsWith("http://", ignoreCase = true) ||
            uriString.startsWith("https://", ignoreCase = true)
        ) {
            val downloaded = metadataManager.downloadAndSavePlaylistCover(
                playlistName = playlistName,
                playlistId = playlistId,
                coverUrl = uriString
            )
            return downloaded?.absolutePath
        }
        return try {
            val parsedUri = android.net.Uri.parse(uriString)
            val coversDir = File(appContext.filesDir, "playlist_covers").apply { mkdirs() }
            val destFile = File(coversDir, "cover_${System.currentTimeMillis()}.jpg")
            appContext.contentResolver.openInputStream(parsedUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0L) destFile.absolutePath else null
        } catch (_: Exception) {
            null
        }
    }

    private fun loadSourcePlaylistBindings(): MutableMap<String, String> {
        val raw = legacyPrefs.getString(KEY_SOURCE_PLAYLIST_BINDINGS, null) ?: return mutableMapOf()
        return try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k, "").trim()
                if (k.isNotBlank() && v.isNotBlank()) {
                    map[k] = v
                }
            }
            map
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveSourcePlaylistBinding(userPlaylistId: String, sourcePlaylistId: String) {
        try {
            val map = loadSourcePlaylistBindings()
            map[userPlaylistId] = sourcePlaylistId.trim()
            val obj = JSONObject()
            map.forEach { (k, v) -> obj.put(k, v) }
            legacyPrefs.edit().putString(KEY_SOURCE_PLAYLIST_BINDINGS, obj.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun removeSourcePlaylistBinding(userPlaylistId: String) {
        try {
            val map = loadSourcePlaylistBindings()
            if (map.remove(userPlaylistId) != null) {
                val obj = JSONObject()
                map.forEach { (k, v) -> obj.put(k, v) }
                legacyPrefs.edit().putString(KEY_SOURCE_PLAYLIST_BINDINGS, obj.toString()).apply()
            }
        } catch (_: Exception) {
        }
    }

    private fun loadReconciledTrackKeys(): MutableSet<String> {
        return legacyPrefs.getStringSet(KEY_RECONCILED_TRACK_KEYS, emptySet()).orEmpty().toMutableSet()
    }

    private fun saveReconciledTrackKeys(keys: Set<String>) {
        legacyPrefs.edit().putStringSet(KEY_RECONCILED_TRACK_KEYS, keys).apply()
    }

    private fun markTrackReconciled(playlistId: String, trackIdOrPath: String) {
        try {
            val keys = loadReconciledTrackKeys()
            keys.add("$playlistId::$trackIdOrPath")
            saveReconciledTrackKeys(keys)
        } catch (_: Exception) {
        }
    }

    private fun PlaylistEntity.toDomain(): UserPlaylist {
        return UserPlaylist(
            id = id,
            name = name,
            description = description,
            coverUri = coverUri,
            createdAt = createdAt,
            tracks = deserializeTracks(tracksJson)
        )
    }

    private fun UserPlaylist.toEntity(): PlaylistEntity {
        return PlaylistEntity(
            id = id,
            name = name,
            description = description,
            coverUri = coverUri,
            createdAt = createdAt,
            tracksJson = serializeTracks(tracks)
        )
    }

    private fun serializeTracks(tracks: List<SavedMediaResult>): String {
        val arr = JSONArray()
        tracks.forEach { saved ->
            val obj = JSONObject().apply {
                put("audioPath", saved.audioFile.absolutePath)
                put("coverPath", saved.coverFile?.absolutePath ?: "")
                put("mediaStoreUri", saved.mediaStoreUri ?: "")
                put("id", saved.trackInfo.id)
                put("title", saved.trackInfo.title)
                put("artist", saved.trackInfo.artist)
                put("album", saved.trackInfo.album)
                put("durationSec", saved.trackInfo.durationSeconds)
                put("thumbnailUrl", saved.trackInfo.thumbnailUrl ?: "")
                put("sourceUrl", saved.trackInfo.sourceUrl)
                put("streamUrl", saved.trackInfo.streamUrl)
                put("mimeType", saved.trackInfo.mimeType)
                put("bitrateKbps", saved.trackInfo.bitrateKbps)
                put("playlistId", saved.trackInfo.playlistId ?: "")
                put("playlistTitle", saved.trackInfo.playlistTitle ?: "")
                put("playlistCoverUrl", saved.trackInfo.playlistCoverUrl ?: "")
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun deserializeTracks(json: String): List<SavedMediaResult> {
        if (json.isBlank()) return emptyList()
        return try {
            val internalDir = File(appContext.filesDir, "SakuraDownloads")
            val internalCoversDir = File(internalDir, "Covers")
            val arr = JSONArray(json)
            val list = mutableListOf<SavedMediaResult>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val rawAudioPath = obj.optString("audioPath", "")
                if (rawAudioPath.isBlank()) continue
                val originalAudioFile = File(rawAudioPath)
                val resolvedAudioFile = if (originalAudioFile.exists()) {
                    originalAudioFile
                } else {
                    val migratedCandidate = File(internalDir, originalAudioFile.name)
                    if (migratedCandidate.exists()) migratedCandidate else originalAudioFile
                }
                val audioPath = resolvedAudioFile.absolutePath

                val rawCoverPath = obj.optString("coverPath", "")
                val coverFile = if (rawCoverPath.isNotBlank()) {
                    val origCover = File(rawCoverPath)
                    when {
                        origCover.exists() -> origCover
                        File(internalCoversDir, origCover.name).exists() -> File(internalCoversDir, origCover.name)
                        else -> null
                    }
                } else null

                val album = obj.optString("album", "Sakura Music")
                val plId = obj.optString("playlistId", "").trim().ifBlank { null }
                val plTitle = obj.optString("playlistTitle", "").trim().ifBlank {
                    album.takeIf { MediaTrackInfo.isMeaningfulPlaylistName(it) }
                }
                val plCover = obj.optString("playlistCoverUrl", "").trim().ifBlank { null }

                val trackInfo = MediaTrackInfo(
                    id = obj.optString("id", audioPath.hashCode().toString()),
                    title = obj.optString("title", resolvedAudioFile.nameWithoutExtension),
                    artist = obj.optString("artist", "Sakura Music"),
                    album = plTitle ?: album,
                    durationSeconds = obj.optLong("durationSec", 0L),
                    thumbnailUrl = coverFile?.absolutePath ?: obj.optString("thumbnailUrl", "").ifBlank { null },
                    sourceUrl = obj.optString("sourceUrl", audioPath),
                    streamUrl = audioPath,
                    mimeType = obj.optString("mimeType", "audio/mp4"),
                    bitrateKbps = obj.optInt("bitrateKbps", 128),
                    contentLengthBytes = if (resolvedAudioFile.exists()) resolvedAudioFile.length() else -1L,
                    playlistId = plId,
                    playlistTitle = plTitle,
                    playlistCoverUrl = plCover
                )
                list.add(
                    SavedMediaResult(
                        audioFile = resolvedAudioFile,
                        coverFile = coverFile,
                        mediaStoreUri = resolvedAudioFile.toURI().toString(),
                        trackInfo = trackInfo
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parsePlaylistJson(obj: JSONObject): UserPlaylist {
        val tracksJson = obj.optJSONArray("tracks")?.toString() ?: "[]"
        return UserPlaylist(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", "Liste"),
            description = obj.optString("description", ""),
            coverUri = obj.optString("coverUri", "").ifBlank { null },
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            tracks = deserializeTracks(tracksJson)
        )
    }

    companion object {
        private const val LEGACY_PREFS_NAME = "sakura_offline_playlists"
        private const val KEY_PLAYLISTS_JSON = "playlists_json"
        private const val KEY_MIGRATED_TO_ROOM = "migrated_to_room_v1"
        private const val KEY_SOURCE_PLAYLIST_BINDINGS = "source_playlist_bindings_v1"
        private const val KEY_RECONCILED_TRACK_KEYS = "reconciled_playlist_track_keys_v1"
    }
}
