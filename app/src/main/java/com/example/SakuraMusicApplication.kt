package com.example

import android.app.Application
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.CustomMinimalThemeManager
import com.example.data.local.LocalTrackDatabase
import com.example.sync.AutoSyncScheduler
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class SakuraMusicApplication : Application(), ImageLoaderFactory {
    private val appExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Unhandled background coroutine exception: ${throwable.message}", throwable)
    }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + appExceptionHandler)

    companion object {
        private const val TAG = "SakuraMusicApp"

        @Volatile
        var isYoutubeDLInitialized: Boolean = false
            private set

        @Volatile
        var isFFmpegInitialized: Boolean = false
            private set

        @Volatile
        var isAria2cInitialized: Boolean = false
            private set

        @Volatile
        var youtubeDLInitError: String? = null
            private set

        @Synchronized
        fun ensureYoutubeDLInitialized(appContext: android.content.Context): Boolean {
            if (isYoutubeDLInitialized) return true
            return try {
                YoutubeDL.getInstance().init(appContext)
                isYoutubeDLInitialized = true
                youtubeDLInitError = null
                try {
                    FFmpeg.getInstance().init(appContext)
                    isFFmpegInitialized = true
                } catch (e: Throwable) {
                    Log.w(TAG, "FFmpeg initialization failed: ${e.message}")
                    isFFmpegInitialized = false
                }
                try {
                    Aria2c.getInstance().init(appContext)
                    isAria2cInitialized = true
                } catch (e: Throwable) {
                    Log.w(TAG, "Aria2c initialization failed: ${e.message}")
                    isAria2cInitialized = false
                }
                true
            } catch (e: YoutubeDLException) {
                youtubeDLInitError = e.localizedMessage ?: "YoutubeDL initialization error"
                Log.e(TAG, "Failed to initialize youtubedl-android", e)
                false
            } catch (e: Throwable) {
                youtubeDLInitError = e.localizedMessage ?: "Native binary initialization error"
                Log.e(TAG, "Unexpected error initializing youtubedl-android", e)
                false
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val activityManager = getSystemService(android.app.ActivityManager::class.java)
        val isLowRam = activityManager?.isLowRamDevice == true
        val memoryPercent = if (isLowRam) 0.15 else 0.22
        val diskBytes = if (isLowRam) 120L * 1024L * 1024L else 250L * 1024L * 1024L

        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(memoryPercent)
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "sakura_artwork_cache"))
                    .maxSizeBytes(diskBytes)
                    .build()
            }
            .crossfade(false)
            .allowHardware(true)
            .allowRgb565(isLowRam) // Keep ARGB_8888 default for full-screen covers; small list items opt into RGB_565 per request
            .respectCacheHeaders(false)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            coil.Coil.imageLoader(this).memoryCache?.trimMemory(level)
        } catch (_: Throwable) {
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            coil.Coil.imageLoader(this).memoryCache?.clear()
        } catch (_: Throwable) {
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Move ALL startup I/O, PackageManager checks, Room warmup, and preferences initialization off the Main Thread
        applicationScope.launch(Dispatchers.IO) {
            try {
                CustomMinimalThemeManager.ensureInitialized(this@SakuraMusicApplication)
            } catch (e: Throwable) {
                Log.w(TAG, "Error initializing custom theme storage: ${e.message}")
            }
            try {
                com.example.downloader.LyricsProvider.init(this@SakuraMusicApplication)
            } catch (e: Throwable) {
                Log.w(TAG, "Error initializing LyricsProvider: ${e.message}")
            }
            try {
                LocalTrackDatabase.getInstance(this@SakuraMusicApplication)
            } catch (e: Throwable) {
                Log.w(TAG, "Error warming up Room database: ${e.message}")
            }
            try {
                com.example.data.AppIconManager.ensureValidLauncherState(this@SakuraMusicApplication)
            } catch (e: Throwable) {
                Log.e(TAG, "Error ensuring launcher state: ${e.message}", e)
            }
            try {
                // İlk ekran çizimi tamamlanana kadar ağır native binary açılımını sıraya koy
                kotlinx.coroutines.delay(650L)
                val initialized = ensureYoutubeDLInitialized(this@SakuraMusicApplication)
                if (initialized) {
                    Log.i(
                        TAG,
                        "ytdlnis engine initialized (YoutubeDL=$isYoutubeDLInitialized, FFmpeg=$isFFmpegInitialized, Aria2c=$isAria2cInitialized)"
                    )
                }
                kotlinx.coroutines.delay(350L)
                AutoSyncScheduler.schedulePeriodicSync(this@SakuraMusicApplication)
                AutoSyncScheduler.scheduleLikedSongsWifiSync(this@SakuraMusicApplication)
            } catch (e: Throwable) {
                Log.w(TAG, "Background initialization error: ${e.message}")
            }
        }
    }
}
