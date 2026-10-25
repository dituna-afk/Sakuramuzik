package com.example.sync

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.CookieStorageHelper
import com.example.data.OfflinePlaylistRepository
import com.example.downloader.DownloadEngine
import com.example.downloader.DownloadFormatOption
import com.example.downloader.DownloadPreferencesManager
import com.example.downloader.MediaMetadataManager
import com.example.downloader.NetscapeCookieHelper
import com.example.downloader.YTDlpResolver
import com.example.downloader.YouTubeStreamResolver
import com.example.downloader.YoutubeDLDownloadManager
import com.example.downloader.YoutubeDLDownloadState
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SubscriptionSource(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val preferredFormatName: String = DownloadFormatOption.AUDIO_M4A_HIGH.name,
    val lastSyncedTimestamp: Long = 0L,
    val downloadedVideoIds: Set<String> = emptySet(),
    val isEnabled: Boolean = true,
    val targetPlaylistTitle: String? = null,
    val targetPlaylistId: String? = null
)

class SubscriptionStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSubscriptions(): List<SubscriptionSource> {
        val raw = prefs.getString(KEY_SUBSCRIPTIONS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<SubscriptionSource>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val idsArray = obj.optJSONArray("downloadedVideoIds")
                val ids = mutableSetOf<String>()
                if (idsArray != null) {
                    for (j in 0 until idsArray.length()) {
                        val vId = idsArray.optString(j)
                        if (vId.isNotBlank()) ids.add(vId)
                    }
                }
                val rawTargetTitle = obj.optString("targetPlaylistTitle", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                val rawTargetId = obj.optString("targetPlaylistId", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                result.add(
                    SubscriptionSource(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", "Kanal / Liste"),
                        url = obj.optString("url", ""),
                        preferredFormatName = obj.optString(
                            "preferredFormatName",
                            DownloadFormatOption.AUDIO_M4A_HIGH.name
                        ),
                        lastSyncedTimestamp = obj.optLong("lastSyncedTimestamp", 0L),
                        downloadedVideoIds = ids,
                        isEnabled = obj.optBoolean("isEnabled", true),
                        targetPlaylistTitle = rawTargetTitle,
                        targetPlaylistId = rawTargetId
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveSubscriptions(subscriptions: List<SubscriptionSource>) {
        val arr = JSONArray()
        subscriptions.forEach { sub ->
            val idsArr = JSONArray()
            sub.downloadedVideoIds.forEach { idsArr.put(it) }
            val obj = JSONObject().apply {
                put("id", sub.id)
                put("title", sub.title)
                put("artist", "")
                put("url", sub.url)
                put("preferredFormatName", sub.preferredFormatName)
                put("lastSyncedTimestamp", sub.lastSyncedTimestamp)
                put("downloadedVideoIds", idsArr)
                put("isEnabled", sub.isEnabled)
                if (!sub.targetPlaylistTitle.isNullOrBlank()) {
                    put("targetPlaylistTitle", sub.targetPlaylistTitle)
                }
                if (!sub.targetPlaylistId.isNullOrBlank()) {
                    put("targetPlaylistId", sub.targetPlaylistId)
                }
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_SUBSCRIPTIONS_JSON, arr.toString()).commit()
    }

    fun addSubscription(
        title: String,
        url: String,
        formatName: String,
        targetPlaylistTitle: String? = null,
        targetPlaylistId: String? = null
    ): List<SubscriptionSource> {
        val current = loadSubscriptions().toMutableList()
        current.add(
            0,
            SubscriptionSource(
                title = title.trim().ifEmpty { "YouTube Kanal / Liste" },
                url = url.trim(),
                preferredFormatName = formatName,
                targetPlaylistTitle = targetPlaylistTitle?.trim()?.takeIf { it.isNotBlank() },
                targetPlaylistId = targetPlaylistId?.trim()?.takeIf { it.isNotBlank() }
            )
        )
        saveSubscriptions(current)
        return current
    }

    fun updateSubscriptionTarget(
        id: String,
        targetPlaylistTitle: String?,
        targetPlaylistId: String?
    ): List<SubscriptionSource> {
        val updated = loadSubscriptions().map { sub ->
            if (sub.id == id) {
                sub.copy(
                    targetPlaylistTitle = targetPlaylistTitle?.trim()?.takeIf { it.isNotBlank() },
                    targetPlaylistId = targetPlaylistId?.trim()?.takeIf { it.isNotBlank() }
                )
            } else {
                sub
            }
        }
        saveSubscriptions(updated)
        return updated
    }

    fun removeSubscription(id: String): List<SubscriptionSource> {
        val updated = loadSubscriptions().filterNot { it.id == id }
        saveSubscriptions(updated)
        return updated
    }

    fun toggleSubscription(id: String, enabled: Boolean): List<SubscriptionSource> {
        val updated = loadSubscriptions().map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
        saveSubscriptions(updated)
        return updated
    }

    fun markVideoDownloaded(subscriptionId: String, videoId: String) {
        val updated = loadSubscriptions().map { sub ->
            if (sub.id == subscriptionId) {
                sub.copy(
                    lastSyncedTimestamp = System.currentTimeMillis(),
                    downloadedVideoIds = sub.downloadedVideoIds + videoId
                )
            } else {
                sub
            }
        }
        saveSubscriptions(updated)
    }

    fun isLikedSongsWifiAutoSyncEnabled(): Boolean = prefs.getBoolean(KEY_LIKED_SONGS_WIFI_SYNC, true)

    fun setLikedSongsWifiAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIKED_SONGS_WIFI_SYNC, enabled).apply()
    }

    fun getSyncedLikedSongIds(): Set<String> {
        return prefs.getStringSet(KEY_SYNCED_LIKED_IDS, emptySet()).orEmpty()
    }

    fun markLikedSongSynced(videoId: String) {
        val updated = getSyncedLikedSongIds().toMutableSet()
        updated.add(videoId)
        prefs.edit()
            .putStringSet(KEY_SYNCED_LIKED_IDS, updated)
            .putLong(KEY_LAST_LIKED_SYNC_TS, System.currentTimeMillis())
            .apply()
    }

    fun getLastLikedSongsSyncTimestamp(): Long = prefs.getLong(KEY_LAST_LIKED_SYNC_TS, 0L)

    fun isAutoSyncWifiOnly(): Boolean = prefs.getBoolean(KEY_WIFI_ONLY, true)

    fun setAutoSyncWifiOnly(wifiOnly: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply()
    }

    fun getSyncIntervalMinutes(): Long {
        if (prefs.contains(KEY_INTERVAL_MINUTES)) {
            return prefs.getLong(KEY_INTERVAL_MINUTES, 360L).coerceAtLeast(15L)
        }
        val hours = prefs.getLong(KEY_INTERVAL_HOURS, 6L)
        return (hours * 60L).coerceAtLeast(15L)
    }

    fun setSyncIntervalMinutes(minutes: Long) {
        prefs.edit().putLong(KEY_INTERVAL_MINUTES, minutes.coerceAtLeast(15L)).apply()
    }

    fun getSyncIntervalHours(): Long {
        return (getSyncIntervalMinutes() / 60L).coerceAtLeast(1L)
    }

    fun setSyncIntervalHours(hours: Long) {
        setSyncIntervalMinutes(hours * 60L)
    }

    companion object {
        private const val PREFS_NAME = "sakura_subscriptions_prefs"
        private const val KEY_SUBSCRIPTIONS_JSON = "subscriptions_json"
        private const val KEY_WIFI_ONLY = "auto_sync_wifi_only"
        private const val KEY_LIKED_SONGS_WIFI_SYNC = "liked_songs_wifi_auto_sync"
        private const val KEY_SYNCED_LIKED_IDS = "synced_liked_video_ids"
        private const val KEY_LAST_LIKED_SYNC_TS = "last_liked_sync_timestamp"
        private const val KEY_INTERVAL_HOURS = "sync_interval_hours"
        private const val KEY_INTERVAL_MINUTES = "sync_interval_minutes"
    }
}

/**
 * Dedicated Wi-Fi-only Worker that synchronizes YouTube Music Liked Songs (`VLLM` / `LM`)
 * directly from Google's `WEB_REMIX` (Client ID: 67) endpoint using Android `CookieManager`
 * cookies and `SAPISIDHASH` authorization, then downloads any newly liked songs not yet in the library.
 */
class LikedSongsSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val storage = SubscriptionStorage(applicationContext)
            if (!storage.isLikedSongsWifiAutoSyncEnabled()) {
                return@withContext Result.success()
            }

            val cookieStorage = CookieStorageHelper(applicationContext)
            val (_, savedCookies) = cookieStorage.loadCookies()

            val activeCookies = NetscapeCookieHelper.resolveActiveCookies(applicationContext, savedCookies)
            val cookieManagerHeader = runCatching {
                CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty()
            }.getOrDefault("")

            val activeCookieHeader = if (cookieManagerHeader.contains("SAPISID")) {
                cookieManagerHeader
            } else {
                activeCookies.joinToString("; ") { "${it.name}=${it.value}" }
            }

            if (activeCookieHeader.isBlank() ||
                (!activeCookieHeader.contains("SAPISID") && !activeCookieHeader.contains("__Secure-3PAPISID"))
            ) {
                Log.d(TAG, "Skipping LikedSongsSyncWorker: No valid SAPISID cookies configured yet.")
                return@withContext Result.success()
            }

            val downloadEngine = DownloadEngine()
            val metadataManager = MediaMetadataManager(applicationContext)
            val prefsManager = DownloadPreferencesManager(applicationContext)
            val streamResolver = YouTubeStreamResolver(applicationContext)
            val downloadManager = YoutubeDLDownloadManager(
                context = applicationContext,
                metadataManager = metadataManager,
                streamResolver = streamResolver,
                preferencesManager = prefsManager
            )

            val playlistRepository = OfflinePlaylistRepository(applicationContext)
            playlistRepository.initializeAndMigrateIfNeeded()

            // 1. Fetch pure YT Music Liked Songs (VLLM + WEB_REMIX Client ID 67)
            val likedResult = downloadEngine.fetchPureYtMusicLikedSongs(
                rawCookieHeader = activeCookieHeader,
                maxPages = 15
            ).getOrNull()
            val remoteLikedTracks = likedResult?.tracks.orEmpty()
            if (remoteLikedTracks.isEmpty()) {
                return@withContext Result.success()
            }

            // 2. Compare against already downloaded local tracks and previously synced IDs
            val localSaved = metadataManager.loadSavedDownloads()
            val existingLocalIds = localSaved.map { it.trackInfo.id }.toHashSet()
            val syncedLikedIds = storage.getSyncedLikedSongIds()

            val missingNewTracks = remoteLikedTracks.filter { track ->
                track.id.isNotBlank() &&
                    track.id !in existingLocalIds &&
                    track.id !in syncedLikedIds
            }

            if (missingNewTracks.isEmpty()) {
                return@withContext Result.success()
            }

            Log.i(TAG, "LikedSongsSyncWorker found ${missingNewTracks.size} new YT Music Liked Songs to download over Wi-Fi.")
            val preferredFormat = DownloadFormatOption.AUDIO_M4A_HIGH
            val likedPlaylistName = likedResult?.title?.takeIf { it.isNotBlank() } ?: "YouTube Music Beğenilen Şarkılar"
            val likedPlaylistId = likedResult?.playlistId ?: "VLLM"
            val likedCoverUrl = likedResult?.thumbnailUrl

            // Download new liked songs in background
            for (rawTrack in missingNewTracks.take(15)) {
                if (isStopped) break
                val track = rawTrack.copy(
                    album = likedPlaylistName,
                    playlistId = likedPlaylistId,
                    playlistTitle = likedPlaylistName,
                    playlistCoverUrl = likedCoverUrl ?: rawTrack.playlistCoverUrl
                )
                val state = downloadManager.downloadTrack(
                    url = track.sourceUrl.ifBlank { "https://music.youtube.com/watch?v=${track.id}" },
                    formatOption = preferredFormat,
                    cookies = activeCookies,
                    existingTrackInfo = track
                )
                if (state is YoutubeDLDownloadState.Success) {
                    storage.markLikedSongSynced(track.id)
                    playlistRepository.registerDownloadedPlaylistTrack(
                        saved = state.savedMedia,
                        explicitPlaylistTitle = likedPlaylistName,
                        explicitPlaylistId = likedPlaylistId,
                        explicitPlaylistCoverUrl = likedCoverUrl
                    )
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "LikedSongsSyncWorker failed: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "LikedSongsSyncWorker"
    }
}

class AutoSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private fun isChannelUrl(url: String): Boolean {
        val lower = url.lowercase(java.util.Locale.ROOT)
        return lower.contains("/@") ||
            lower.contains("/channel/") ||
            lower.contains("/c/") ||
            lower.contains("/user/")
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val storage = SubscriptionStorage(applicationContext)
            val subscriptions = storage.loadSubscriptions().filter { it.isEnabled && it.url.isNotBlank() }
            if (subscriptions.isEmpty()) return@withContext Result.success()

            val cookieStorage = CookieStorageHelper(applicationContext)
            val (_, savedCookies) = cookieStorage.loadCookies()
            val ytdlpResolver = YTDlpResolver(applicationContext)
            val streamResolver = YouTubeStreamResolver(applicationContext)
            val metadataManager = MediaMetadataManager(applicationContext)
            val prefsManager = DownloadPreferencesManager(applicationContext)
            val downloadManager = YoutubeDLDownloadManager(
                context = applicationContext,
                metadataManager = metadataManager,
                streamResolver = streamResolver,
                preferencesManager = prefsManager
            )

            val playlistRepository = OfflinePlaylistRepository(applicationContext)
            playlistRepository.initializeAndMigrateIfNeeded()
            val existingUserPlaylists = playlistRepository.loadPlaylists()

            val localSavedDownloads = metadataManager.loadSavedDownloads()
            val existingLocalIds = localSavedDownloads.map { it.trackInfo.id }.toHashSet()

            for (sub in subscriptions) {
                if (isStopped) break
                val isChannel = isChannelUrl(sub.url)
                val playlistId = if (!isChannel) streamResolver.extractPlaylistId(sub.url) else null
                val resolvedPlaylist = if (!playlistId.isNullOrBlank()) {
                    streamResolver.resolvePlaylist(playlistId, savedCookies)
                } else null

                val rawTracks: List<com.example.downloader.MediaTrackInfo> = when {
                    resolvedPlaylist != null -> {
                        resolvedPlaylist.tracks
                    }
                    isChannel -> {
                        val candidateUrl = if (!sub.url.endsWith("/videos") && (sub.url.contains("/@") || sub.url.contains("/channel/"))) {
                            "${sub.url.trimEnd('/')}/videos"
                        } else {
                            sub.url
                        }
                        val channelRes = ytdlpResolver.extractPlaylistMetadata(candidateUrl, savedCookies, maxItems = 15).getOrNull()
                            ?: ytdlpResolver.extractPlaylistMetadata(sub.url, savedCookies, maxItems = 15).getOrNull()
                        channelRes?.tracks ?: run {
                            val single = ytdlpResolver.extractSingleMetadata(sub.url, savedCookies).getOrNull()
                            if (single != null) listOf(single) else emptyList()
                        }
                    }
                    else -> {
                        val single = ytdlpResolver.extractSingleMetadata(sub.url, savedCookies).getOrNull()
                        if (single != null) listOf(single) else emptyList()
                    }
                }

                val newTracks = rawTracks.filter { track ->
                    track.id.isNotBlank() &&
                        track.id !in sub.downloadedVideoIds &&
                        track.id !in existingLocalIds
                }.take(5)

                val formatOption = DownloadFormatOption.fromName(sub.preferredFormatName)
                for (track in newTracks) {
                    if (isStopped) break

                    val userTargetTitle = sub.targetPlaylistTitle?.trim()?.takeIf { it.isNotBlank() }
                    val trackAlbum = track.album?.trim()?.takeIf { it.isNotBlank() }
                    val trackPlaylist = track.playlistTitle?.trim()?.takeIf { it.isNotBlank() }
                    val matchedPlaylist = if (isChannel && userTargetTitle == null) {
                        existingUserPlaylists.firstOrNull { pl ->
                            (trackAlbum != null && pl.name.equals(trackAlbum, ignoreCase = true)) ||
                                (trackPlaylist != null && pl.name.equals(trackPlaylist, ignoreCase = true))
                        }
                    } else null

                    val finalTargetPlaylistTitle = when {
                        userTargetTitle != null -> userTargetTitle
                        isChannel -> matchedPlaylist?.name
                        resolvedPlaylist != null -> resolvedPlaylist.title.takeIf { it.isNotBlank() }
                        else -> null
                    }

                    val finalTargetPlaylistId = sub.targetPlaylistId
                        ?: matchedPlaylist?.id
                        ?: resolvedPlaylist?.playlistId

                    val state = downloadManager.downloadTrack(
                        url = track.sourceUrl.ifBlank { "https://www.youtube.com/watch?v=${track.id}" },
                        formatOption = formatOption,
                        cookies = savedCookies,
                        existingTrackInfo = track
                    )
                    if (state is YoutubeDLDownloadState.Success) {
                        storage.markVideoDownloaded(sub.id, track.id)
                        existingLocalIds.add(track.id)
                        if (!finalTargetPlaylistTitle.isNullOrBlank()) {
                            playlistRepository.registerDownloadedPlaylistTrack(
                                saved = state.savedMedia,
                                explicitPlaylistTitle = finalTargetPlaylistTitle,
                                explicitPlaylistId = finalTargetPlaylistId,
                                explicitPlaylistCoverUrl = resolvedPlaylist?.thumbnailUrl
                            )
                        }
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("AutoSyncWorker", "Background sync failed", e)
            Result.retry()
        }
    }
}

object AutoSyncScheduler {
    const val PERIODIC_WORK_NAME = "sakura_auto_sync_periodic"
    const val IMMEDIATE_WORK_NAME = "sakura_auto_sync_immediate"
    const val LIKED_SONGS_WIFI_PERIODIC_WORK = "sakura_liked_songs_wifi_periodic"
    const val LIKED_SONGS_WIFI_IMMEDIATE_WORK = "sakura_liked_songs_wifi_immediate"
    private const val TAG = "AutoSyncScheduler"

    fun schedulePeriodicSync(context: Context) {
        try {
            val storage = SubscriptionStorage(context)
            val wifiOnly = storage.isAutoSyncWifiOnly()
            val intervalMinutes = storage.getSyncIntervalMinutes().coerceAtLeast(15L)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<AutoSyncWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to schedule periodic sync work: ${e.message}", e)
        }
    }

    /**
     * Schedules both an immediate Wi-Fi-constrained check and a 15-minute periodic Wi-Fi check
     * for YT Music Liked Songs (`VLLM`). If Wi-Fi is not currently connected, WorkManager holds
     * the task in memory and executes it the moment an UNMETERED (Wi-Fi) network connects.
     */
    fun scheduleLikedSongsWifiSync(context: Context, triggerImmediateWhenWifiAvailable: Boolean = true) {
        try {
            val storage = SubscriptionStorage(context)
            val wm = WorkManager.getInstance(context.applicationContext)
            if (!storage.isLikedSongsWifiAutoSyncEnabled()) {
                wm.cancelUniqueWork(LIKED_SONGS_WIFI_PERIODIC_WORK)
                wm.cancelUniqueWork(LIKED_SONGS_WIFI_IMMEDIATE_WORK)
                return
            }

            // Strictly NetworkType.UNMETERED (Wi-Fi Only) as requested
            val wifiConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<LikedSongsSyncWorker>(15L, TimeUnit.MINUTES)
                .setConstraints(wifiConstraints)
                .build()

            wm.enqueueUniquePeriodicWork(
                LIKED_SONGS_WIFI_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )

            if (triggerImmediateWhenWifiAvailable) {
                val oneTimeWifiRequest = OneTimeWorkRequestBuilder<LikedSongsSyncWorker>()
                    .setConstraints(wifiConstraints)
                    .build()

                wm.enqueueUniqueWork(
                    LIKED_SONGS_WIFI_IMMEDIATE_WORK,
                    ExistingWorkPolicy.REPLACE,
                    oneTimeWifiRequest
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to schedule LikedSongsSyncWorker: ${e.message}", e)
        }
    }

    fun triggerImmediateLikedSongsWifiSync(context: Context) {
        scheduleLikedSongsWifiSync(context, triggerImmediateWhenWifiAvailable = true)
    }

    fun triggerImmediateSync(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<AutoSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueue(request)
            scheduleLikedSongsWifiSync(context, triggerImmediateWhenWifiAvailable = true)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to trigger immediate sync work: ${e.message}", e)
        }
    }

    fun observePeriodicSyncWork(context: Context): Flow<List<WorkInfo>> {
        return try {
            WorkManager.getInstance(context.applicationContext)
                .getWorkInfosForUniqueWorkFlow(PERIODIC_WORK_NAME)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to get work info flow: ${e.message}", e)
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }
}
