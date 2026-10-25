package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.downloader.MediaTrackInfo
import com.example.downloader.SavedMediaResult
import java.io.File

@Entity(
    tableName = "local_tracks",
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["lastModified"])
    ]
)
data class LocalTrackEntity(
    @PrimaryKey val audioFilePath: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Long,
    val coverFilePath: String?,
    val thumbnailUrl: String?,
    val sourceUrl: String,
    val mimeType: String,
    val bitrateKbps: Int,
    val fileLength: Long,
    val lastModified: Long,
    val sidecarLastModified: Long
) {
    fun toSavedMediaResult(): SavedMediaResult {
        val audioFile = File(audioFilePath)
        val coverFile = coverFilePath?.takeIf { it.isNotBlank() }?.let { File(it) }
        val inferredPlaylist = album.takeIf { MediaTrackInfo.isMeaningfulPlaylistName(it) }
        return SavedMediaResult(
            audioFile = audioFile,
            coverFile = coverFile,
            mediaStoreUri = audioFile.toURI().toString(),
            trackInfo = MediaTrackInfo(
                id = trackId,
                title = title,
                artist = artist,
                album = album,
                durationSeconds = durationSeconds,
                thumbnailUrl = coverFile?.absolutePath ?: thumbnailUrl,
                sourceUrl = sourceUrl,
                streamUrl = audioFilePath,
                mimeType = mimeType,
                bitrateKbps = bitrateKbps,
                contentLengthBytes = fileLength,
                playlistTitle = inferredPlaylist
            )
        )
    }

    companion object {
        fun fromSavedMediaResult(
            saved: SavedMediaResult,
            lastModified: Long = saved.audioFile.lastModified(),
            fileLength: Long = saved.trackInfo.contentLengthBytes.takeIf { it > 0L } ?: saved.audioFile.length(),
            sidecarLastModified: Long = 0L
        ): LocalTrackEntity {
            return LocalTrackEntity(
                audioFilePath = saved.audioFile.absolutePath,
                trackId = saved.trackInfo.id,
                title = saved.trackInfo.title,
                artist = saved.trackInfo.artist,
                album = saved.trackInfo.album,
                durationSeconds = saved.trackInfo.durationSeconds,
                coverFilePath = saved.coverFile?.absolutePath,
                thumbnailUrl = saved.trackInfo.thumbnailUrl,
                sourceUrl = saved.trackInfo.sourceUrl,
                mimeType = saved.trackInfo.mimeType,
                bitrateKbps = saved.trackInfo.bitrateKbps,
                fileLength = fileLength,
                lastModified = lastModified,
                sidecarLastModified = sidecarLastModified
            )
        }
    }
}

@Dao
interface LocalTrackDao {
    @Query("SELECT * FROM local_tracks ORDER BY lastModified DESC")
    suspend fun getAllTracksOrdered(): List<LocalTrackEntity>

    @Query("SELECT * FROM local_tracks ORDER BY lastModified DESC LIMIT :limit OFFSET :offset")
    suspend fun getTracksPaged(limit: Int, offset: Int): List<LocalTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<LocalTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: LocalTrackEntity)

    @Query("DELETE FROM local_tracks WHERE audioFilePath = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM local_tracks WHERE audioFilePath NOT IN (:activePaths)")
    suspend fun pruneDeletedTracks(activePaths: List<String>)

    @Query("DELETE FROM local_tracks")
    suspend fun clearAll()
}

@Database(
    entities = [LocalTrackEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LocalTrackDatabase : RoomDatabase() {
    abstract fun localTrackDao(): LocalTrackDao

    companion object {
        @Volatile
        private var INSTANCE: LocalTrackDatabase? = null

        fun getInstance(context: Context): LocalTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalTrackDatabase::class.java,
                    "sakura_local_tracks.db"
                )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
