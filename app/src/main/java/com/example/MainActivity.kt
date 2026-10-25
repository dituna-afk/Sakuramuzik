package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.CookieAppScreen
import com.example.ui.YtMusicMainScreen
import com.example.ui.theme.DynamicSakuraPaletteExtractor
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.CookieViewModel
import com.example.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    private val musicPlayerViewModel: MusicPlayerViewModel by viewModels()
    private val cookieViewModel: CookieViewModel by viewModels()
    private var pendingSharedText by mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        musicPlayerViewModel.refreshDownloadedTracks()
    }

    private data class RootThemeSlice(
        val selectedTheme: com.example.ui.theme.AppTheme,
        val coverSource: String?,
        val dynamicCoverPaletteEnabled: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enableHighRefreshRateIfSupported()
        requestNecessaryPermissions()
        extractSharedUrlFromIntent(intent)?.let { shared ->
            pendingSharedText = shared
        }

        setContent {
            val themeSliceFlow = remember(musicPlayerViewModel) {
                musicPlayerViewModel.uiState
                    .map { s ->
                        RootThemeSlice(
                            selectedTheme = s.selectedTheme,
                            coverSource = s.lastSavedMedia?.coverFile?.absolutePath ?: s.currentTrack?.thumbnailUrl,
                            dynamicCoverPaletteEnabled = s.featureToggles.dynamicCoverPaletteEnabled
                        )
                    }
                    .distinctUntilChanged()
            }
            val initialState = remember(musicPlayerViewModel) { musicPlayerViewModel.uiState.value }
            val themeSlice by themeSliceFlow.collectAsState(
                initial = RootThemeSlice(
                    selectedTheme = initialState.selectedTheme,
                    coverSource = initialState.lastSavedMedia?.coverFile?.absolutePath
                        ?: initialState.currentTrack?.thumbnailUrl,
                    dynamicCoverPaletteEnabled = initialState.featureToggles.dynamicCoverPaletteEnabled
                )
            )
            val dynamicPalette by DynamicSakuraPaletteExtractor.rememberDynamicSakuraPalette(
                imageUrl = themeSlice.coverSource,
                baseTheme = themeSlice.selectedTheme,
                enabled = themeSlice.dynamicCoverPaletteEnabled
            )
            var showCookieInspectorModal by remember { mutableStateOf(false) }

            LaunchedEffect(pendingSharedText) {
                val shared = pendingSharedText
                if (!shared.isNullOrBlank()) {
                    musicPlayerViewModel.handleIncomingSharedUrl(shared, autoDownload = true)
                    pendingSharedText = null
                }
            }

            MyApplicationTheme(
                appTheme = themeSlice.selectedTheme,
                dynamicPaletteOverride = dynamicPalette
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showCookieInspectorModal) {
                        CookieAppScreen(
                            viewModel = cookieViewModel,
                            onBackToMusic = { showCookieInspectorModal = false }
                        )
                    } else {
                        YtMusicMainScreen(
                            musicViewModel = musicPlayerViewModel,
                            cookieViewModel = cookieViewModel
                        )
                    }
                }
            }
        }
    }

    private fun enableHighRefreshRateIfSupported() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    this.display
                } else {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay
                } ?: return
                val modes = display.supportedModes
                val currentMode = display.mode
                val bestMode = modes
                    .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
                    .maxByOrNull { it.refreshRate }
                if (bestMode != null && bestMode.modeId != currentMode.modeId) {
                    val params = window.attributes
                    params.preferredDisplayModeId = bestMode.modeId
                    window.attributes = params
                }
            }
        } catch (_: Throwable) {
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSharedUrlFromIntent(intent)?.let { shared ->
            pendingSharedText = shared
        }
    }

    private fun extractSharedUrlFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
                } else null
            }
            Intent.ACTION_VIEW -> {
                intent.dataString?.trim()
            }
            else -> null
        }
    }

    private fun requestNecessaryPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
