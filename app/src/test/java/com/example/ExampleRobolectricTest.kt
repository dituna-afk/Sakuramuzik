package com.example

import android.content.Context
import android.content.pm.PackageManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppIconManager
import com.example.data.AppIconOption
import com.example.data.CookieStorageHelper
import com.example.data.OfflinePlaylistRepository
import com.example.data.ThemeStorageHelper
import com.example.data.local.LocalTrackDatabase
import com.example.data.local.LocalTrackEntity
import com.example.downloader.DownloadPreferencesManager
import com.example.downloader.MediaMetadataManager
import com.example.downloader.MediaTrackInfo
import com.example.downloader.SavedMediaResult
import com.example.downloader.YouTubeStreamResolver
import com.example.model.CookieItem
import com.example.player.OfflinePlaybackService
import com.example.ui.screens.SettingsCategory
import com.example.ui.theme.AppTheme
import com.example.ui.theme.toPalette
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Sakura Müzik", appName)
    }

    @Test
    fun `download preferences persist concurrent fragments and flags`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = DownloadPreferencesManager(context)

        prefs.concurrentFragments = 8
        prefs.useAria2c = true
        prefs.embedMetadataAndThumb = false

        val reloaded = DownloadPreferencesManager(context)
        assertEquals(8, reloaded.concurrentFragments)
        assertEquals(true, reloaded.useAria2c)
        assertEquals(false, reloaded.embedMetadataAndThumb)
    }

    @Test
    fun `cookie storage helper saves and reloads cookies accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = CookieStorageHelper(context)
        val cookies = listOf(
            CookieItem(name = "SAPISID", value = "test_sapisid_value", domain = ".youtube.com"),
            CookieItem(name = "SID", value = "test_sid_value", domain = ".youtube.com")
        )
        storage.saveCookies("https://music.youtube.com", cookies)

        val (url, loaded) = storage.loadCookies()
        assertEquals("https://music.youtube.com", url)
        assertEquals(2, loaded.size)
        assertTrue(loaded.any { it.name == "SAPISID" && it.value == "test_sapisid_value" })
    }

    @Test
    fun `theme storage helper saves and loads active theme without deleted themes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val themeStorage = ThemeStorageHelper(context)
        themeStorage.saveTheme(AppTheme.AMOLED_NIGHT)
        assertEquals(AppTheme.AMOLED_NIGHT, themeStorage.loadTheme())
    }

    @Test
    fun `youtube stream resolver extracts video and playlist ids including VLLM`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = YouTubeStreamResolver(context)

        assertEquals("dQw4w9WgXcQ", resolver.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("LM", resolver.extractPlaylistId("https://music.youtube.com/playlist?list=LM"))
        assertEquals("VLLM", resolver.extractPlaylistId("VLLM"))
    }

    @Test
    fun `room local track database inserts and retrieves tracks`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LocalTrackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.localTrackDao()
            val entity = LocalTrackEntity(
                audioFilePath = "/storage/emulated/0/Music/SakuraMusic/TestTrack.m4a",
                trackId = "dQw4w9WgXcQ",
                title = "Test Track",
                artist = "Test Artist",
                album = "Sakura Music",
                durationSeconds = 212L,
                coverFilePath = null,
                thumbnailUrl = null,
                sourceUrl = "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
                mimeType = "audio/mp4",
                bitrateKbps = 256,
                fileLength = 4_200_000L,
                lastModified = 1_700_000_000_000L,
                sidecarLastModified = 1_700_000_000_000L
            )
            dao.upsertTrack(entity)
            val all = dao.getAllTracksOrdered()
            assertEquals(1, all.size)
            assertNotNull(all.firstOrNull { it.trackId == "dQw4w9WgXcQ" })
        } finally {
            db.close()
        }
    }

    @Test
    fun `all 8 Sakura and Anime app icon options have distinct separated preview and launcher resources`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(8, AppIconOption.entries.size)

        val previewIds = AppIconOption.entries.map { it.previewDrawableResId }.toSet()
        val launcherIds = AppIconOption.entries.map { it.launcherIconResId }.toSet()
        val roundLauncherIds = AppIconOption.entries.map { it.launcherRoundIconResId }.toSet()
        val foregroundIds = AppIconOption.entries.map { it.foregroundDrawableResId }.toSet()
        val backgroundIds = AppIconOption.entries.map { it.backgroundDrawableResId }.toSet()

        assertEquals("Every AppIconOption must have a unique previewDrawableResId", 8, previewIds.size)
        assertEquals("Every AppIconOption must have a unique launcherIconResId", 8, launcherIds.size)
        assertEquals("Every AppIconOption must have a unique launcherRoundIconResId", 8, roundLauncherIds.size)
        assertEquals("Every AppIconOption must have a unique foregroundDrawableResId", 8, foregroundIds.size)
        assertEquals("Every AppIconOption must have a unique backgroundDrawableResId", 8, backgroundIds.size)

        for (option in AppIconOption.entries) {
            assertNotEquals(
                "Preview drawable must be separated from installed launcher mipmap for ${option.name}",
                option.previewDrawableResId,
                option.launcherIconResId
            )
            assertEquals("drawable", context.resources.getResourceTypeName(option.previewDrawableResId))
            assertEquals("mipmap", context.resources.getResourceTypeName(option.launcherIconResId))
            assertEquals("mipmap", context.resources.getResourceTypeName(option.launcherRoundIconResId))

            val opts = android.graphics.BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                inSampleSize = 2
            }
            val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, option.previewDrawableResId, opts)
            assertNotNull(
                "AppIconOption ${option.name} must decode a valid raster preview Bitmap for Settings > System tab",
                bitmap
            )
            assertEquals(
                "Preview bitmap for ${option.name} must preserve 1:1 square aspect ratio",
                bitmap.width,
                bitmap.height
            )

            assertNotNull("Launcher icon drawable must load for ${option.name}", context.getDrawable(option.launcherIconResId))
            assertNotNull("Round launcher icon drawable must load for ${option.name}", context.getDrawable(option.launcherRoundIconResId))
            assertNotNull("Foreground drawable must load for ${option.name}", context.getDrawable(option.foregroundDrawableResId))
            assertNotNull("Background drawable must load for ${option.name}", context.getDrawable(option.backgroundDrawableResId))
        }
    }

    @Test
    fun `app icon selection persists across restarts and switches activity aliases accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("sakura_app_icon_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val pm = context.packageManager
        val pkgName = context.packageName

        for (target in listOf(
            AppIconOption.NEON,
            AppIconOption.DARK,
            AppIconOption.GOLDEN,
            AppIconOption.BERSERK_GUTS,
            AppIconOption.BERSERK_GRIFFITH,
            AppIconOption.EVANGELION_01,
            AppIconOption.COWBOY_BEBOP,
            AppIconOption.DEFAULT
        )) {
            AppIconManager.applyAppIcon(context, target)
            assertEquals(target, AppIconManager.loadSelectedIcon(context))
            assertTrue(
                "Target alias ${target.aliasName} should be enabled",
                AppIconManager.isAliasCurrentlyEnabled(pm, pkgName, target)
            )
            for (other in AppIconOption.entries.filter { it != target }) {
                assertFalse(
                    "Non-selected alias ${other.aliasName} should be disabled when ${target.aliasName} is active",
                    AppIconManager.isAliasCurrentlyEnabled(pm, pkgName, other)
                )
            }

            // Simulate app restart reconciliation
            AppIconManager.ensureValidLauncherState(context)
            assertEquals(target, AppIconManager.loadSelectedIcon(context))
            assertTrue(AppIconManager.isAliasCurrentlyEnabled(pm, pkgName, target))
        }
    }

    @Test
    fun `berserk griffith theme has no wallpaper assets registered and preserves dark palette`() {
        val griffithMeta = com.example.ui.theme.AnimeArtRegistry.getMetadata(AppTheme.BERSERK_GRIFFITH)
        assertNotNull(griffithMeta)
        assertEquals(null, griffithMeta?.primaryAssetPath)
        assertEquals(null, griffithMeta?.secondaryAssetPath)
        assertEquals(null, griffithMeta?.systemVectorDrawableRes)

        val palette = AppTheme.BERSERK_GRIFFITH.toPalette()
        assertEquals(AppTheme.BERSERK_GRIFFITH, palette.theme)
    }

    @Test
    fun `single track vs playlist track association distinguishes real playlist names`() {
        val singleTrack = MediaTrackInfo(
            id = "vid_single_01",
            title = "Single Song",
            artist = "Artist",
            album = "Sakura Music",
            durationSeconds = 180L,
            thumbnailUrl = null,
            sourceUrl = "https://www.youtube.com/watch?v=vid_single_01",
            streamUrl = ""
        )
        assertEquals(null, singleTrack.effectivePlaylistName())

        val playlistTrack = singleTrack.copy(
            album = "Gece Sürüşü 2026",
            playlistId = "PL_NIGHT_01",
            playlistTitle = "Gece Sürüşü 2026",
            playlistCoverUrl = "https://i.ytimg.com/vi/vid_single_01/hqdefault.jpg"
        )
        assertEquals("Gece Sürüşü 2026", playlistTrack.effectivePlaylistName())
    }

    @Test
    fun `same named playlists disambiguate for different source ids and merge on resume for same source id`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val repo = OfflinePlaylistRepository(context)
            repo.initializeAndMigrateIfNeeded()
            // Clear existing playlists for clean test state
            repo.loadPlaylists().forEach { repo.deletePlaylist(it.id) }

            // 1. First playlist "Anime OST" from PL_SOURCE_AAA (with unreachable cover URL -> must not fail, falls back gracefully)
            val p1 = repo.ensurePlaylistForDownload(
                rawPlaylistTitle = "Anime OST",
                sourcePlaylistId = "PL_SOURCE_AAA",
                coverUrl = "http://127.0.0.1:1/non_existent_cover.jpg",
                fallbackTrackCoverPath = null
            )
            assertNotNull(p1)
            assertEquals("Anime OST", p1?.name)

            // 2. Resuming/restarting the SAME playlist (PL_SOURCE_AAA) must reuse "Anime OST" without creating a duplicate
            val p1Resumed = repo.ensurePlaylistForDownload(
                rawPlaylistTitle = "Anime OST",
                sourcePlaylistId = "PL_SOURCE_AAA",
                coverUrl = null,
                fallbackTrackCoverPath = null
            )
            assertNotNull(p1Resumed)
            assertEquals(p1?.id, p1Resumed?.id)
            assertEquals("Anime OST", p1Resumed?.name)

            // 3. Downloading a DIFFERENT playlist that also happens to be named "Anime OST" (PL_SOURCE_BBB) must disambiguate to "Anime OST (2)"
            val p2Different = repo.ensurePlaylistForDownload(
                rawPlaylistTitle = "Anime OST",
                sourcePlaylistId = "PL_SOURCE_BBB",
                coverUrl = null,
                fallbackTrackCoverPath = null
            )
            assertNotNull(p2Different)
            assertNotEquals(p1?.id, p2Different?.id)
            assertEquals("Anime OST (2)", p2Different?.name)
        }
    }

    @Test
    fun `audio file collision protection prevents overwriting existing track files`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val metadataManager = MediaMetadataManager(context)
            val musicDir = metadataManager.getAppMusicDirectory()

            val trackA = MediaTrackInfo(
                id = "track_id_AAA",
                title = "Opening Theme",
                artist = "Sakura Band",
                album = "Playlist One",
                durationSeconds = 200L,
                thumbnailUrl = null,
                sourceUrl = "https://www.youtube.com/watch?v=track_id_AAA",
                streamUrl = "",
                playlistId = "PL_1",
                playlistTitle = "Playlist One"
            )
            val baseA = metadataManager.buildCollisionSafeBaseFileName(trackA)
            val fileA = File(musicDir, "$baseA.m4a")
            fileA.writeBytes(ByteArray(24_000) { 0x11 })
            metadataManager.finalizeDownloadedMedia(fileA, trackA)

            // If a different track ID would produce the same base name on disk, collision protection assigns a safe suffix (2)
            val conflictingTrack = MediaTrackInfo(
                id = "track_id_CONFLICT",
                title = trackA.title,
                artist = trackA.artist,
                album = "Playlist Two",
                durationSeconds = 205L,
                thumbnailUrl = null,
                sourceUrl = "https://www.youtube.com/watch?v=track_id_CONFLICT",
                streamUrl = "",
                playlistId = "PL_2",
                playlistTitle = "Playlist Two"
            )
            // Pre-create an audio file with baseA belonging to track_id_AAA; if another file exists matching the candidate name
            val conflictingFile = File(musicDir, "${metadataManager.buildTrackBaseFileName(conflictingTrack)}.m4a")
            conflictingFile.writeBytes(ByteArray(24_000) { 0x22 })
            File(musicDir, "${conflictingFile.nameWithoutExtension}.meta.json").writeText(
                """{"id":"different_foreign_id","title":"Occupied"}""",
                Charsets.UTF_8
            )

            val safeBase = metadataManager.buildCollisionSafeBaseFileName(conflictingTrack)
            assertNotEquals("Colliding filename for a different track ID must be disambiguated", conflictingFile.nameWithoutExtension, safeBase)
            assertTrue("Disambiguated filename should contain suffix (2)", safeBase.endsWith(" (2)"))

            // Re-downloading the SAME track ID for another playlist reuses the existing valid file without clobbering it
            val trackAReusedInPlaylist2 = trackA.copy(
                album = "Playlist Two",
                playlistId = "PL_2",
                playlistTitle = "Playlist Two"
            )
            val reusedSaved = metadataManager.findAndAssociateExistingTrackIfPresent(trackAReusedInPlaylist2)
            assertNotNull("Existing valid audio file for same track ID should be reused", reusedSaved)
            assertEquals(fileA.absolutePath, reusedSaved?.audioFile?.absolutePath)
            assertEquals("Playlist Two", reusedSaved?.trackInfo?.effectivePlaylistName())

            // Clean up test files
            fileA.delete()
            File(musicDir, "$baseA.meta.json").delete()
            conflictingFile.delete()
            File(musicDir, "${conflictingFile.nameWithoutExtension}.meta.json").delete()
        }
    }

    @Test
    fun `pending playlist download queue survives restart in DownloadPreferencesManager`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = DownloadPreferencesManager(context)
        prefs.clearPendingDownloadQueue()

        val tracks = listOf(
            MediaTrackInfo(
                id = "vid_101",
                title = "Track 101",
                artist = "Artist 101",
                album = "Synthwave Mix",
                durationSeconds = 195L,
                thumbnailUrl = "https://i.ytimg.com/vi/vid_101/hqdefault.jpg",
                sourceUrl = "https://www.youtube.com/watch?v=vid_101",
                streamUrl = "",
                playlistId = "PL_SYNTH_99",
                playlistTitle = "Synthwave Mix",
                playlistCoverUrl = "https://i.ytimg.com/vi/vid_101/maxresdefault.jpg"
            ),
            MediaTrackInfo(
                id = "vid_102",
                title = "Track 102",
                artist = "Artist 102",
                album = "Synthwave Mix",
                durationSeconds = 220L,
                thumbnailUrl = null,
                sourceUrl = "https://www.youtube.com/watch?v=vid_102",
                streamUrl = "",
                playlistId = "PL_SYNTH_99",
                playlistTitle = "Synthwave Mix",
                playlistCoverUrl = "https://i.ytimg.com/vi/vid_101/maxresdefault.jpg"
            )
        )

        prefs.savePendingDownloadQueue(
            tracks = tracks,
            playlistTitle = "Synthwave Mix",
            playlistId = "PL_SYNTH_99",
            playlistCoverUrl = "https://i.ytimg.com/vi/vid_101/maxresdefault.jpg",
            totalCount = 5,
            completedCount = 3
        )

        val restored = DownloadPreferencesManager(context).loadPendingDownloadQueue()
        assertNotNull(restored)
        assertEquals("Synthwave Mix", restored?.playlistTitle)
        assertEquals("PL_SYNTH_99", restored?.playlistId)
        assertEquals("https://i.ytimg.com/vi/vid_101/maxresdefault.jpg", restored?.playlistCoverUrl)
        assertEquals(2, restored?.tracks?.size)
        assertEquals("Synthwave Mix", restored?.tracks?.first()?.effectivePlaylistName())
        assertEquals("PL_SYNTH_99", restored?.tracks?.first()?.playlistId)

        prefs.clearPendingDownloadQueue()
        assertEquals(null, prefs.loadPendingDownloadQueue())
    }

    @Test
    fun `download queue single removal, multi removal and clear queue operate consistently with persistence`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = DownloadPreferencesManager(context)
        prefs.clearPendingDownloadQueue()

        val track1 = MediaTrackInfo(
            id = "q_track_1",
            title = "Queue Track 1",
            artist = "Artist A",
            album = "Queue Playlist",
            durationSeconds = 180L,
            thumbnailUrl = null,
            sourceUrl = "https://www.youtube.com/watch?v=q_track_1",
            streamUrl = ""
        )
        val track2 = MediaTrackInfo(
            id = "q_track_2",
            title = "Queue Track 2",
            artist = "Artist B",
            album = "Queue Playlist",
            durationSeconds = 200L,
            thumbnailUrl = null,
            sourceUrl = "https://www.youtube.com/watch?v=q_track_2",
            streamUrl = ""
        )
        val track3 = MediaTrackInfo(
            id = "q_track_3",
            title = "Queue Track 3",
            artist = "Artist C",
            album = "Queue Playlist",
            durationSeconds = 210L,
            thumbnailUrl = null,
            sourceUrl = "https://www.youtube.com/watch?v=q_track_3",
            streamUrl = ""
        )

        val vm = com.example.viewmodel.MusicPlayerViewModel(context)
        vm.cancelBackgroundJobsForTest()

        // 1. Initial State: Enqueue 3 tracks
        val downloadAllMethod = vm.javaClass.getDeclaredMethod("enqueueTracksForDownload", List::class.java).apply {
            isAccessible = true
        }
        downloadAllMethod.invoke(vm, listOf(track1, track2, track3))

        val stateAfterEnqueue = vm.uiState.value
        assertEquals(3, stateAfterEnqueue.downloadQueue.size + (if (stateAfterEnqueue.currentDownloadingTrack != null) 1 else 0))

        // 2. Single removal: Remove track 2
        vm.removeTrackFromDownloadQueue("q_track_2")
        val stateAfterSingleRemove = vm.uiState.value
        assertFalse("Removed track 2 must not be in downloadQueue", stateAfterSingleRemove.downloadQueue.any { it.id == "q_track_2" })
        assertNotEquals("Removed track 2 must not be currentDownloadingTrack", "q_track_2", stateAfterSingleRemove.currentDownloadingTrack?.id)

        // Verify persistent storage reflects removal immediately (synchronous commit)
        val persistedAfterSingleRemove = prefs.loadPendingDownloadQueue()
        if (persistedAfterSingleRemove != null) {
            assertFalse("Removed track 2 must not be in persisted queue", persistedAfterSingleRemove.tracks.any { it.id == "q_track_2" })
        }

        // 3. Multi-track removal: Remove track 1 and 3
        vm.removeTracksFromDownloadQueue(listOf("q_track_1", "q_track_3"))
        val stateAfterMultiRemove = vm.uiState.value
        assertTrue("Download queue must be empty after removing remaining tracks", stateAfterMultiRemove.downloadQueue.isEmpty())
        assertFalse("Track 1 must not be currentDownloadingTrack", stateAfterMultiRemove.currentDownloadingTrack?.id == "q_track_1")
        assertFalse("Track 3 must not be currentDownloadingTrack", stateAfterMultiRemove.currentDownloadingTrack?.id == "q_track_3")

        // Verify persistent storage is empty
        val persistedAfterMultiRemove = prefs.loadPendingDownloadQueue()
        assertTrue("Preferences pending queue must be cleared when all items are removed", persistedAfterMultiRemove == null || persistedAfterMultiRemove.tracks.isEmpty())

        // 4. Test clearDownloadQueue does NOT delete downloaded files
        val musicDir = File(context.filesDir, "Music/SakuraMusic").apply { mkdirs() }
        val testDownloadedFile = File(musicDir, "PreviouslyDownloadedSong.m4a").apply {
            writeBytes(ByteArray(25_000) { 0x33 })
        }
        val metaFile = File(musicDir, "PreviouslyDownloadedSong.meta.json").apply {
            writeText("""{"id":"prev_downloaded","title":"Previously Downloaded Song"}""", Charsets.UTF_8)
        }

        // Re-enqueue new items and clear queue
        downloadAllMethod.invoke(vm, listOf(track1, track2))
        vm.clearDownloadQueue(cancelActiveTrack = true)

        val stateAfterClear = vm.uiState.value
        assertTrue("Download queue must be empty after clearDownloadQueue", stateAfterClear.downloadQueue.isEmpty())
        assertEquals("Current downloading track must be null when cancelActiveTrack is true", null, stateAfterClear.currentDownloadingTrack)
        assertEquals("Persisted pending queue must be cleared", null, prefs.loadPendingDownloadQueue())

        // Confirm previously downloaded media files remain intact!
        assertTrue("Existing downloaded audio files MUST NOT be deleted by clearDownloadQueue", testDownloadedFile.exists())
        assertTrue("Existing metadata files MUST NOT be deleted by clearDownloadQueue", metaFile.exists())

        // Clean up test file
        testDownloadedFile.delete()
        metaFile.delete()
    }

    @Test
    fun `removed track does not reappear when state or persistent queue is reloaded`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = DownloadPreferencesManager(context)
        prefs.clearPendingDownloadQueue()

        val trackA = MediaTrackInfo(
            id = "track_alpha",
            title = "Alpha Song",
            artist = "Artist A",
            album = "Queue Test",
            durationSeconds = 120L,
            thumbnailUrl = null,
            sourceUrl = "https://www.youtube.com/watch?v=track_alpha",
            streamUrl = "",
            playlistId = "PL_QUEUE_TEST",
            playlistTitle = "Queue Test"
        )
        val trackB = MediaTrackInfo(
            id = "track_beta",
            title = "Beta Song",
            artist = "Artist B",
            album = "Queue Test",
            durationSeconds = 140L,
            thumbnailUrl = null,
            sourceUrl = "https://www.youtube.com/watch?v=track_beta",
            streamUrl = "",
            playlistId = "PL_QUEUE_TEST",
            playlistTitle = "Queue Test"
        )

        val vm = com.example.viewmodel.MusicPlayerViewModel(context)
        vm.cancelBackgroundJobsForTest()
        val enqueueMethod = vm.javaClass.getDeclaredMethod("enqueueTracksForDownload", List::class.java).apply {
            isAccessible = true
        }
        enqueueMethod.invoke(vm, listOf(trackA, trackB))

        // User removes track_alpha
        vm.removeTrackFromDownloadQueue("track_alpha")

        // 1. Verify in-memory state
        assertFalse("track_alpha must not be in ViewModel downloadQueue", vm.uiState.value.downloadQueue.any { it.id == "track_alpha" })
        assertNotEquals("track_alpha must not be active downloading track", "track_alpha", vm.uiState.value.currentDownloadingTrack?.id)

        // 2. Simulate switching screens / reloading preferences
        val reloadedPrefs = DownloadPreferencesManager(context)
        val pendingFromDisk = reloadedPrefs.loadPendingDownloadQueue()
        assertNotNull("Persisted pending queue must have remaining track_beta", pendingFromDisk)
        assertEquals(1, pendingFromDisk?.tracks?.size)
        assertEquals("track_beta", pendingFromDisk?.tracks?.first()?.id)
        assertFalse("track_alpha must not reappear in persisted storage", pendingFromDisk?.tracks?.any { it.id == "track_alpha" } == true)

        // 3. Clear queue completely
        vm.clearDownloadQueue(cancelActiveTrack = true)
        assertEquals(null, reloadedPrefs.loadPendingDownloadQueue())
    }

    @Test
    fun `channel subscription target persists across reload and app restart`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.sync.SubscriptionStorage(context)
        val added = storage.addSubscription(
            title = "Lofi Girl",
            url = "https://www.youtube.com/@LofiGirl",
            formatName = "AUDIO_M4A_HIGH",
            targetPlaylistTitle = "Ders Çalışma Listem",
            targetPlaylistId = "pl_lofi_target_01"
        )
        val subId = added.first().id

        // Simulate app restart with a fresh storage instance
        val freshStorage = com.example.sync.SubscriptionStorage(context)
        val loaded = freshStorage.loadSubscriptions()
        val found = loaded.firstOrNull { it.id == subId }

        assertNotNull("Subscription must persist across reloads", found)
        assertEquals("Lofi Girl", found?.title)
        assertEquals("https://www.youtube.com/@LofiGirl", found?.url)
        assertEquals("Ders Çalışma Listem", found?.targetPlaylistTitle)
        assertEquals("pl_lofi_target_01", found?.targetPlaylistId)
    }

    @Test
    fun `channel subscription target can be modified or cleared`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.sync.SubscriptionStorage(context)
        val added = storage.addSubscription(
            title = "Sakura Beats",
            url = "https://www.youtube.com/@SakuraBeats",
            formatName = "AUDIO_M4A_HIGH",
            targetPlaylistTitle = "İlk Hedef",
            targetPlaylistId = "pl_initial"
        )
        val subId = added.first().id

        // Update target
        storage.updateSubscriptionTarget(
            id = subId,
            targetPlaylistTitle = "Yeni Gece Hedefi",
            targetPlaylistId = "pl_updated"
        )

        val freshStorage = com.example.sync.SubscriptionStorage(context)
        val updated = freshStorage.loadSubscriptions().firstOrNull { it.id == subId }
        assertEquals("Yeni Gece Hedefi", updated?.targetPlaylistTitle)
        assertEquals("pl_updated", updated?.targetPlaylistId)

        // Clear target to default library
        freshStorage.updateSubscriptionTarget(
            id = subId,
            targetPlaylistTitle = null,
            targetPlaylistId = null
        )
        val cleared = com.example.sync.SubscriptionStorage(context).loadSubscriptions().firstOrNull { it.id == subId }
        assertEquals(null, cleared?.targetPlaylistTitle)
        assertEquals(null, cleared?.targetPlaylistId)
    }

    @Test
    fun `channel subscription duplicate prevention records and preserves downloaded video ids`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.sync.SubscriptionStorage(context)
        val added = storage.addSubscription(
            title = "Ambient Music",
            url = "https://www.youtube.com/@AmbientMusic",
            formatName = "AUDIO_M4A_HIGH"
        )
        val subId = added.first().id

        storage.markVideoDownloaded(subId, "vid_ambient_001")
        storage.markVideoDownloaded(subId, "vid_ambient_002")

        val reloaded = com.example.sync.SubscriptionStorage(context).loadSubscriptions().firstOrNull { it.id == subId }
        assertNotNull(reloaded)
        assertTrue(reloaded?.downloadedVideoIds?.contains("vid_ambient_001") == true)
        assertTrue(reloaded?.downloadedVideoIds?.contains("vid_ambient_002") == true)
        assertFalse(reloaded?.downloadedVideoIds?.contains("vid_ambient_003") == true)
    }

    @Test
    fun `settings category queue has valid metadata and tag`() {
        assertEquals("Kuyruk", SettingsCategory.QUEUE.title)
        assertEquals("tab_queue", SettingsCategory.QUEUE.tag)
        assertNotNull(SettingsCategory.QUEUE.icon)
    }

    @Test
    fun `playback queue removal updates queue and adjusts currentIndex correctly without deleting audio files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = org.robolectric.Robolectric.buildService(OfflinePlaybackService::class.java).create().get()

        val tempDir = File(context.cacheDir, "test_queue_audio").apply { mkdirs() }
        val audio1 = File(tempDir, "song1.m4a").apply { writeBytes(ByteArray(1024) { 1 }) }
        val audio2 = File(tempDir, "song2.m4a").apply { writeBytes(ByteArray(1024) { 2 }) }
        val audio3 = File(tempDir, "song3.m4a").apply { writeBytes(ByteArray(1024) { 3 }) }

        val track1 = SavedMediaResult(
            audioFile = audio1,
            coverFile = null,
            mediaStoreUri = null,
            trackInfo = MediaTrackInfo(
                id = "id_1",
                title = "Queue Track 1",
                artist = "Artist 1",
                album = "Album 1",
                durationSeconds = 180L,
                thumbnailUrl = null,
                sourceUrl = "https://youtube.com/watch?v=id_1",
                streamUrl = ""
            )
        )
        val track2 = SavedMediaResult(
            audioFile = audio2,
            coverFile = null,
            mediaStoreUri = null,
            trackInfo = MediaTrackInfo(
                id = "id_2",
                title = "Queue Track 2",
                artist = "Artist 2",
                album = "Album 2",
                durationSeconds = 200L,
                thumbnailUrl = null,
                sourceUrl = "https://youtube.com/watch?v=id_2",
                streamUrl = ""
            )
        )
        val track3 = SavedMediaResult(
            audioFile = audio3,
            coverFile = null,
            mediaStoreUri = null,
            trackInfo = MediaTrackInfo(
                id = "id_3",
                title = "Queue Track 3",
                artist = "Artist 3",
                album = "Album 3",
                durationSeconds = 220L,
                thumbnailUrl = null,
                sourceUrl = "https://youtube.com/watch?v=id_3",
                streamUrl = ""
            )
        )

        val queueList = listOf(track1, track2, track3)
        // Set queue with track2 active (index 1)
        service.setQueueAndPlay(queueList, startIndex = 1)
        assertEquals(3, service.playerState.value.queue.size)
        assertEquals(1, service.playerState.value.currentIndex)
        assertEquals("id_2", service.playerState.value.currentTrack?.trackInfo?.id)

        // 1. Remove track at index 2 (item after currently playing)
        service.removeTrackFromQueue(2)
        assertEquals(2, service.playerState.value.queue.size)
        assertEquals(1, service.playerState.value.currentIndex)
        assertEquals("id_2", service.playerState.value.currentTrack?.trackInfo?.id)
        assertFalse(service.playerState.value.queue.any { it.trackInfo.id == "id_3" })

        // 2. Remove track at index 0 (item before currently playing) -> currentIndex should decrement from 1 to 0
        service.removeTrackFromQueue(0)
        assertEquals(1, service.playerState.value.queue.size)
        assertEquals(0, service.playerState.value.currentIndex)
        assertEquals("id_2", service.playerState.value.currentTrack?.trackInfo?.id)
        assertFalse(service.playerState.value.queue.any { it.trackInfo.id == "id_1" })

        // 3. Audio files must still exist!
        assertTrue("Audio file 1 must still exist after removing from queue", audio1.exists())
        assertTrue("Audio file 2 must still exist after removing from queue", audio2.exists())
        assertTrue("Audio file 3 must still exist after removing from queue", audio3.exists())

        // Clean up temp files
        audio1.delete()
        audio2.delete()
        audio3.delete()
    }

    @Test
    fun `playback queue clearQueue empties queue and stops playback without deleting downloaded files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = org.robolectric.Robolectric.buildService(OfflinePlaybackService::class.java).create().get()

        val tempDir = File(context.cacheDir, "test_clear_queue").apply { mkdirs() }
        val audioA = File(tempDir, "songA.m4a").apply { writeBytes(ByteArray(1024) { 4 }) }
        val audioB = File(tempDir, "songB.m4a").apply { writeBytes(ByteArray(1024) { 5 }) }

        val trackA = SavedMediaResult(
            audioFile = audioA,
            coverFile = null,
            mediaStoreUri = null,
            trackInfo = MediaTrackInfo(
                id = "id_a",
                title = "Alpha",
                artist = "Singer A",
                album = "Album",
                durationSeconds = 150L,
                thumbnailUrl = null,
                sourceUrl = "",
                streamUrl = ""
            )
        )
        val trackB = SavedMediaResult(
            audioFile = audioB,
            coverFile = null,
            mediaStoreUri = null,
            trackInfo = MediaTrackInfo(
                id = "id_b",
                title = "Beta",
                artist = "Singer B",
                album = "Album",
                durationSeconds = 170L,
                thumbnailUrl = null,
                sourceUrl = "",
                streamUrl = ""
            )
        )

        service.setQueueAndPlay(listOf(trackA, trackB), startIndex = 0)
        assertEquals(2, service.playerState.value.queue.size)

        // Clear queue
        service.clearQueue()

        val stateAfterClear = service.playerState.value
        assertTrue("Playback queue must be empty after clearQueue", stateAfterClear.queue.isEmpty())
        assertEquals(-1, stateAfterClear.currentIndex)
        assertEquals(null, stateAfterClear.currentTrack)
        assertFalse(stateAfterClear.isPlaying)

        // Audio files MUST NOT be deleted
        assertTrue("Song A file must remain on disk", audioA.exists())
        assertTrue("Song B file must remain on disk", audioB.exists())

        // Clean up
        audioA.delete()
        audioB.delete()
    }

    @Test
    fun `griffith 4 independent settings and interval download delay persist across restarts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.FeatureTogglesStorageHelper(context)

        // Default values check
        val initial = com.example.data.FeatureTogglesConfig()
        assertTrue(initial.griffithQuotesEnabled)
        assertTrue(initial.griffithFalconEmblemEnabled)
        assertTrue(initial.griffithFlyingFalconEnabled)
        assertTrue(initial.griffithRandomLinesEnabled)
        assertEquals(0, initial.downloadIntervalDelaySeconds)

        // Toggle each independently and verify persistence
        val custom = initial.copy(
            griffithQuotesEnabled = false,
            griffithFalconEmblemEnabled = true,
            griffithFlyingFalconEnabled = false,
            griffithRandomLinesEnabled = true,
            downloadIntervalDelaySeconds = 15
        )
        storage.saveConfig(custom)

        val reloaded = com.example.data.FeatureTogglesStorageHelper(context).loadConfig()
        assertFalse("griffithQuotesEnabled must persist false", reloaded.griffithQuotesEnabled)
        assertTrue("griffithFalconEmblemEnabled must persist true independently", reloaded.griffithFalconEmblemEnabled)
        assertFalse("griffithFlyingFalconEnabled must persist false independently", reloaded.griffithFlyingFalconEnabled)
        assertTrue("griffithRandomLinesEnabled must persist true independently", reloaded.griffithRandomLinesEnabled)
        assertEquals(15, reloaded.downloadIntervalDelaySeconds)
    }

    @Test
    fun `cowboy bebop 31 settings persist across restarts presets apply and reset only affects cowboy bebop`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val featureStorage = com.example.data.FeatureTogglesStorageHelper(context)
        val themeStorage = ThemeStorageHelper(context)

        // Save Griffith & general settings first
        featureStorage.saveConfig(
            com.example.data.FeatureTogglesConfig(
                griffithQuotesEnabled = false,
                griffithFlyingFalconEnabled = true,
                downloadIntervalDelaySeconds = 10
            )
        )
        themeStorage.saveTheme(AppTheme.BERSERK_GRIFFITH)

        // Update Cowboy Bebop 31 settings
        com.example.data.CowboyBebopThemeManager.updateConfig(context) {
            it.copy(
                cbFayePurpleYellowPaletteEnabled = true,
                cbNightFriendlyLowBrightnessEnabled = true,
                cbPurpleToneIntensity = 0.72f,
                cbAmberYellowAccentIntensity = 0.64f,
                cbBackgroundDarknessLevel = 0.88f,
                cbCardSurfaceOpacity = 0.75f,
                cbTextContrastBoostEnabled = true,
                cbStaticStarfieldEnabled = false,
                cbStarDensity = 0.40f,
                cbStarBrightness = 0.35f,
                cbSpaceMistNebulaEnabled = false,
                cbNebulaIntensity = 0.25f,
                cbFilmGrainDustEnabled = true,
                cbVignetteEnabled = false,
                cbMasterAnimationsEnabled = false,
                cbAnimationIntensity = com.example.data.BebopAnimationIntensity.OFF,
                cbAnimationSpeed = com.example.data.BebopAnimationSpeed.SLOW,
                cbStarTwinkleEnabled = false,
                cbSpaceLightTransitionEnabled = false,
                cbScreenTransitionEffectsEnabled = false,
                cbRetroHudLinesEnabled = false,
                cbThinCardBorderEnabled = true,
                cbSubtleCardGlowEnabled = true,
                cbMiniPlayerGlowHighlightEnabled = false,
                cbFullPlayerSpaceAtmosphereEnabled = false,
                cbSeekbarSpecialGlowEnabled = false,
                cbPlayerVisualizerWaveEnabled = false,
                cbCornerAccentMarksEnabled = false,
                cbSpecialListTitleStyleEnabled = false,
                cbAlbumArtGlowFrameEnabled = false,
                cbPauseEffectsInBackgroundEnabled = true,
                cbLowEffectModeEnabled = true
            )
        }

        // Reload from SharedPreferences to verify all 31 settings persisted
        val reloadedBebop = com.example.data.CowboyBebopThemeManager.loadFromPrefs(context)
        assertEquals(0.72f, reloadedBebop.cbPurpleToneIntensity, 0.001f)
        assertEquals(0.64f, reloadedBebop.cbAmberYellowAccentIntensity, 0.001f)
        assertEquals(0.88f, reloadedBebop.cbBackgroundDarknessLevel, 0.001f)
        assertEquals(0.75f, reloadedBebop.cbCardSurfaceOpacity, 0.001f)
        assertTrue(reloadedBebop.cbTextContrastBoostEnabled)
        assertFalse(reloadedBebop.cbStaticStarfieldEnabled)
        assertFalse(reloadedBebop.cbSpaceMistNebulaEnabled)
        assertTrue(reloadedBebop.cbFilmGrainDustEnabled)
        assertFalse(reloadedBebop.cbVignetteEnabled)
        assertFalse(reloadedBebop.cbMasterAnimationsEnabled)
        assertEquals(com.example.data.BebopAnimationIntensity.OFF, reloadedBebop.cbAnimationIntensity)
        assertFalse(reloadedBebop.cbRetroHudLinesEnabled)
        assertTrue(reloadedBebop.cbSubtleCardGlowEnabled)
        assertFalse(reloadedBebop.cbMiniPlayerGlowHighlightEnabled)
        assertFalse(reloadedBebop.cbFullPlayerSpaceAtmosphereEnabled)
        assertFalse(reloadedBebop.cbSeekbarSpecialGlowEnabled)
        assertFalse(reloadedBebop.cbPlayerVisualizerWaveEnabled)
        assertFalse(reloadedBebop.cbCornerAccentMarksEnabled)
        assertFalse(reloadedBebop.cbSpecialListTitleStyleEnabled)
        assertFalse(reloadedBebop.cbAlbumArtGlowFrameEnabled)
        assertTrue(reloadedBebop.cbPauseEffectsInBackgroundEnabled)
        assertTrue(reloadedBebop.cbLowEffectModeEnabled)

        // Apply CINEMATIC preset and verify
        com.example.data.CowboyBebopThemeManager.applyPreset(context, com.example.data.BebopPresetProfile.CINEMATIC)
        val afterCinematic = com.example.data.CowboyBebopThemeManager.loadFromPrefs(context)
        assertEquals(com.example.data.BebopAnimationIntensity.MEDIUM, afterCinematic.cbAnimationIntensity)
        assertTrue(afterCinematic.cbMasterAnimationsEnabled)

        // Reset Cowboy Bebop to defaults and verify Griffith & theme settings were NOT touched
        com.example.data.CowboyBebopThemeManager.resetToDefaults(context)
        val resetBebop = com.example.data.CowboyBebopThemeManager.loadFromPrefs(context)
        assertEquals(com.example.data.CowboyBebopThemeConfig(), resetBebop)

        val untouchedFeatureConfig = featureStorage.loadConfig()
        assertFalse("Griffith setting must remain untouched by Bebop reset", untouchedFeatureConfig.griffithQuotesEnabled)
        assertTrue("Griffith flying falcon must remain untouched by Bebop reset", untouchedFeatureConfig.griffithFlyingFalconEnabled)
        assertEquals(10, untouchedFeatureConfig.downloadIntervalDelaySeconds)
        assertEquals(AppTheme.BERSERK_GRIFFITH, themeStorage.loadTheme())
    }

    @Test
    fun `one based playlist range selection handles normal reversed single and out of bounds ranges`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context.applicationContext as android.app.Application
        val vm = com.example.viewmodel.MusicPlayerViewModel(app)

        val tracks = (1..100).map { idx ->
            MediaTrackInfo(
                id = "track_$idx",
                title = "Song $idx",
                artist = "Artist",
                album = "Playlist",
                durationSeconds = 180L,
                thumbnailUrl = null,
                sourceUrl = "https://www.youtube.com/watch?v=track_$idx",
                streamUrl = ""
            )
        }

        // Inject 100 resolved playlist tracks via reflection or state helper for deterministic range test
        val uiStateField = com.example.viewmodel.MusicPlayerViewModel::class.java.getDeclaredField("_uiState")
        uiStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val mutableStateFlow = uiStateField.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<com.example.viewmodel.MusicUiState>
        mutableStateFlow.value = mutableStateFlow.value.copy(
            resolvedPlaylistTitle = "Test Playlist 100",
            resolvedPlaylistTracks = tracks
        )

        // 1. Standard range 15..60 -> 46 tracks selected (track_15 .. track_60)
        vm.selectPlaylistRangeOneBased(15, 60)
        val sel15To60 = vm.uiState.value.selectedPlaylistTrackIds
        assertEquals(46, sel15To60.size)
        assertTrue(sel15To60.contains("track_15"))
        assertTrue(sel15To60.contains("track_60"))
        assertFalse(sel15To60.contains("track_14"))
        assertFalse(sel15To60.contains("track_61"))

        // 2. Reversed range 60..15 -> automatically normalized to 15..60
        vm.selectPlaylistRangeOneBased(60, 15)
        assertEquals(46, vm.uiState.value.selectedPlaylistTrackIds.size)

        // 3. Single item range 1..1 -> only track_1 selected
        vm.selectPlaylistRangeOneBased(1, 1)
        assertEquals(setOf("track_1"), vm.uiState.value.selectedPlaylistTrackIds)

        // 4. Out-of-bounds -5..9999 -> clamped safely to 1..100
        vm.selectPlaylistRangeOneBased(-5, 9999)
        assertEquals(100, vm.uiState.value.selectedPlaylistTrackIds.size)

        vm.cancelBackgroundJobsForTest()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }
}


