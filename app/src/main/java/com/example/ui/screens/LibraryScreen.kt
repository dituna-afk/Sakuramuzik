package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.FeatureTogglesConfig
import com.example.data.LocalCustomMinimalThemeConfig
import com.example.data.PlayCountManager
import com.example.data.UserPlaylist
import com.example.downloader.LikedSongsSourceType
import com.example.downloader.SavedMediaResult
import com.example.ui.components.LibraryDoctorCard
import com.example.ui.components.OfflineTagEditorDialog
import com.example.ui.components.OptimizedThumbnailImage
import com.example.ui.components.SakuraMusicStatsCard
import com.example.ui.components.rememberProgressiveWindowState
import com.example.ui.theme.LocalAppThemePalette
import java.io.File

private data class DownloadedPlaylistGroup(
    val name: String,
    val tracks: List<SavedMediaResult>,
    val coverModel: Any?
)

@Composable
fun LibraryScreen(
    downloadedTracks: List<SavedMediaResult>,
    playlists: List<UserPlaylist>,
    currentPlayingTrackId: String?,
    featureToggles: FeatureTogglesConfig = FeatureTogglesConfig(),
    playCountManager: PlayCountManager? = null,
    isBatchFetchingLyrics: Boolean = false,
    batchLyricsCompletedCount: Int = 0,
    batchLyricsTotalCount: Int = 0,
    onBatchFetchAndTranslateAllLyrics: () -> Unit = {},
    onUpdateFeatureToggles: ((FeatureTogglesConfig) -> FeatureTogglesConfig) -> Unit = {},
    onPlaySavedTrack: (SavedMediaResult) -> Unit,
    onDeleteSavedTrack: (SavedMediaResult) -> Unit,
    onAddTrackToPlaylist: (String, SavedMediaResult) -> Unit,
    onUpdateTrackMetadata: (SavedMediaResult, String, String, String, String?) -> Unit = { _, _, _, _, _ -> },
    onAutoFetchYoutubeTitle: (SavedMediaResult, (String, String) -> Unit) -> Unit = { _, _ -> },
    onRunLibraryDoctor: () -> Unit = {},
    onRefreshLibrary: () -> Unit,
    onDownloadAccountLibraryWithCookies: (LikedSongsSourceType) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    val sadeConfig = LocalCustomMinimalThemeConfig.current
    var trackForPlaylistModal by remember { mutableStateOf<SavedMediaResult?>(null) }
    var trackForTagEditor by remember { mutableStateOf<SavedMediaResult?>(null) }
    var selectedPlaylistFilter by remember { mutableStateOf<String?>(null) }

    val playlistGroups = remember(downloadedTracks, playlists) {
        val groupedByTrackMeta = downloadedTracks
            .mapNotNull { saved ->
                val pName = saved.trackInfo.effectivePlaylistName() ?: return@mapNotNull null
                pName to saved
            }
            .groupBy({ it.first }, { it.second })

        val mergedMap = linkedMapOf<String, MutableList<SavedMediaResult>>()
        groupedByTrackMeta.forEach { (name, tracks) ->
            mergedMap.getOrPut(name) { mutableListOf() }.addAll(tracks)
        }
        playlists.forEach { userPlaylist ->
            if (userPlaylist.tracks.isNotEmpty()) {
                val existingKey = mergedMap.keys.firstOrNull { it.equals(userPlaylist.name, ignoreCase = true) }
                    ?: userPlaylist.name
                val bucket = mergedMap.getOrPut(existingKey) { mutableListOf() }
                val existingPaths = bucket.map { it.audioFile.absolutePath }.toHashSet()
                userPlaylist.tracks.forEach { t ->
                    if (existingPaths.add(t.audioFile.absolutePath)) {
                        bucket.add(t)
                    }
                }
            }
        }

        mergedMap.map { (name, tracks) ->
            val matchingUserPlaylist = playlists.firstOrNull { it.name.equals(name, ignoreCase = true) }
            val userCoverUri = matchingUserPlaylist?.coverUri?.takeIf { it.isNotBlank() }
            val userCoverModel: Any? = when {
                userCoverUri == null -> null
                userCoverUri.startsWith("http", ignoreCase = true) -> userCoverUri
                File(userCoverUri).exists() -> File(userCoverUri)
                else -> null
            }
            val fallbackTrackCover: Any? = tracks.firstNotNullOfOrNull {
                it.coverFile?.takeIf { f -> f.exists() }
                    ?: it.trackInfo.playlistCoverUrl?.takeIf { u -> u.isNotBlank() }
                    ?: it.trackInfo.thumbnailUrl?.takeIf { u -> u.isNotBlank() }
            }
            DownloadedPlaylistGroup(
                name = name,
                tracks = tracks,
                coverModel = userCoverModel ?: fallbackTrackCover
            )
        }
    }

    val activeFilter = selectedPlaylistFilter?.takeIf { filterName ->
        playlistGroups.any { it.name.equals(filterName, ignoreCase = true) }
    }

    val filteredTracks = remember(downloadedTracks, playlistGroups, activeFilter) {
        if (activeFilter == null) {
            downloadedTracks
        } else {
            playlistGroups.firstOrNull { it.name.equals(activeFilter, ignoreCase = true) }?.tracks
                ?: downloadedTracks.filter { it.trackInfo.effectivePlaylistName().equals(activeFilter, ignoreCase = true) }
        }
    }

    val listState = rememberLazyListState()
    val headerCount = 4 +
        (if (playlistGroups.isNotEmpty()) 1 else 0) +
        (if (playCountManager != null && featureToggles.musicStatsEnabled) 1 else 0) +
        (if (featureToggles.libraryDoctorEnabled) 1 else 0)

    val windowState = rememberProgressiveWindowState(
        listState = listState,
        totalItemsCount = filteredTracks.size,
        headerItemsOffset = headerCount,
        initialBatchSize = 24,
        stepBatchSize = 24,
        activeWindowBuffer = 8
    )

    val displayedTracks = remember(filteredTracks, windowState.loadedCount) {
        filteredTracks.take(windowState.loadedCount)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "lib_header", contentType = "header") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.surfaceContainer.copy(alpha = 0.65f))
            ) {
                if (!sadeConfig.headerBannerWallpaperPath.isNullOrBlank()) {
                    AsyncImage(
                        model = File(sadeConfig.headerBannerWallpaperPath),
                        contentDescription = "Liste Başlığı GIF/Görsel",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                alpha = sadeConfig.headerBannerWallpaperOpacity.coerceIn(0.05f, 1f)
                            }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Çevrimdışı Kitaplığım",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.onBackground
                        )
                        Text(
                            text = if (activeFilter != null) {
                                "$activeFilter • ${filteredTracks.size} şarkı (${downloadedTracks.size} toplam)"
                            } else {
                                "${downloadedTracks.size} yerel ses dosyası (${displayedTracks.size} aktif yüklü)"
                            },
                            fontSize = 12.sp,
                            color = palette.onSurfaceMuted
                        )
                    }

                    IconButton(onClick = onRefreshLibrary) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Kitaplığı Yenile",
                            tint = palette.primaryAccent
                        )
                    }
                }
            }
        }

        if (playlistGroups.isNotEmpty()) {
            item(key = "lib_downloaded_playlists_section", contentType = "downloaded_playlists") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.surfaceContainer)
                        .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = palette.primaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "İndirilen Oynatma Listeleri (${playlistGroups.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.onSurface
                            )
                        }
                        if (activeFilter != null) {
                            TextButton(
                                onClick = { selectedPlaylistFilter = null },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Tümünü Göster",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primaryAccent
                                )
                            }
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("library_downloaded_playlists_row")
                    ) {
                        item(key = "__all_downloads__") {
                            val isSelected = activeFilter == null
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) palette.primaryAccent.copy(alpha = 0.2f)
                                        else palette.surfaceVariant.copy(alpha = 0.65f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) palette.primaryAccent else palette.dividerOrBorder,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { selectedPlaylistFilter = null }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(palette.primaryAccent.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = "Tüm İndirilenler",
                                        tint = palette.primaryAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Tümü",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.onSurface
                                    )
                                    Text(
                                        text = "${downloadedTracks.size} şarkı",
                                        fontSize = 10.sp,
                                        color = palette.onSurfaceMuted
                                    )
                                }
                            }
                        }

                        items(
                            items = playlistGroups,
                            key = { it.name }
                        ) { group ->
                            val isSelected = activeFilter.equals(group.name, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) palette.primaryAccent.copy(alpha = 0.2f)
                                        else palette.surfaceVariant.copy(alpha = 0.65f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) palette.primaryAccent else palette.dividerOrBorder,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        selectedPlaylistFilter = if (isSelected) null else group.name
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OptimizedThumbnailImage(
                                    model = group.coverModel,
                                    contentDescription = group.name,
                                    isWithinActiveWindow = true,
                                    fallbackIcon = Icons.AutoMirrored.Filled.QueueMusic,
                                    fallbackTint = palette.primaryAccent,
                                    backgroundColor = palette.surfaceContainer,
                                    iconSize = 20.dp,
                                    targetPixelSize = 96,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.width(120.dp)) {
                                    Text(
                                        text = group.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${group.tracks.size} şarkı",
                                        fontSize = 10.sp,
                                        color = palette.primaryAccent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Otomatik Şarkı Sözü Bulma & Türkçe Çeviri (Arka Plan İş Parçacığı - İndirme Motorundan Bağımsız)
        item(key = "lib_batch_lyrics_card", contentType = "batch_lyrics") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surfaceContainer)
                    .border(1.dp, palette.primaryAccent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Otomatik Şarkı Sözü & Türkçe Çeviri",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.onSurface
                        )
                        Text(
                            text = if (isBatchFetchingLyrics) {
                                "Taranıyor ve çevriliyor: $batchLyricsCompletedCount / $batchLyricsTotalCount şarkı..."
                            } else {
                                "LRCLIB & YT Music üzerinden eksik sözleri bulur, Türkçeye çevirip kalıcı .LRC kaydeder"
                            },
                            fontSize = 11.sp,
                            color = palette.onSurfaceMuted
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onBatchFetchAndTranslateAllLyrics,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBatchFetchingLyrics) palette.tertiaryAccent else palette.primaryAccent,
                            contentColor = palette.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("library_batch_lyrics_translate_button")
                    ) {
                        Icon(
                            imageVector = if (isBatchFetchingLyrics) Icons.Default.Close else Icons.Default.Translate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isBatchFetchingLyrics) "Durdur" else "Tüm Şarkılara Söz Bul & Çevir",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isBatchFetchingLyrics && batchLyricsTotalCount > 0) {
                    val progress = (batchLyricsCompletedCount.toFloat() / batchLyricsTotalCount.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = palette.primaryAccent,
                        trackColor = palette.surfaceVariant
                    )
                }
            }
        }

        // Akıllı Kütüphane, İstatistik & Paylaşım Araçları (Aç / Kapa Hızlı Kontrol Paneli)
        item(key = "lib_step3_panel", contentType = "step3_panel") {
            var expandStep3Toggles by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.surfaceContainer)
                    .border(1.dp, palette.primaryAccent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandStep3Toggles = !expandStep3Toggles },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Akıllı Kütüphane & Araçlar (Aç / Kapa)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.onSurface
                        )
                        Text(
                            text = if (expandStep3Toggles) "Modülleri aşağıdan tek dokunuşla açıp kapatabilirsiniz"
                            else "İstatistik, Kütüphane Doktoru, Etiket Düzenleyici, Auto-Play & Söz Kartı (Dokun: Aç/Kapa)",
                            fontSize = 11.sp,
                            color = palette.primaryAccent
                        )
                    }
                    TextButton(onClick = { expandStep3Toggles = !expandStep3Toggles }) {
                        Text(
                            text = if (expandStep3Toggles) "Gizle" else "Aç / Kapa",
                            fontWeight = FontWeight.Bold,
                            color = palette.primaryAccent
                        )
                    }
                }

                if (expandStep3Toggles) {
                    LibraryStep3ToggleRow(
                        title = "Müzik Dinleme İstatistikleri & Özet (Wrapped)",
                        checked = featureToggles.musicStatsEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateFeatureToggles { it.copy(musicStatsEnabled = enabled) }
                        }
                    )
                    LibraryStep3ToggleRow(
                        title = "Kütüphane Doktoru (Kopya & 'Parça X' İsim Onarıcı)",
                        checked = featureToggles.libraryDoctorEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateFeatureToggles { it.copy(libraryDoctorEnabled = enabled) }
                        }
                    )
                    LibraryStep3ToggleRow(
                        title = "Çevrimdışı ID3 Etiket, İsim & Kapak Düzenleyici",
                        checked = featureToggles.tagEditorEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateFeatureToggles { it.copy(tagEditorEnabled = enabled) }
                        }
                    )
                    LibraryStep3ToggleRow(
                        title = "Akıllı Otomatik Sıradaki Şarkı (Smart Auto-Play)",
                        checked = featureToggles.smartAutoplayEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateFeatureToggles { it.copy(smartAutoplayEnabled = enabled) }
                        }
                    )
                    LibraryStep3ToggleRow(
                        title = "Şarkı Sözü Kartı Oluşturucu & Paylaşım",
                        checked = featureToggles.lyricCardGeneratorEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateFeatureToggles { it.copy(lyricCardGeneratorEnabled = enabled) }
                        }
                    )
                }
            }
        }

        if (playCountManager != null && featureToggles.musicStatsEnabled) {
            item(key = "lib_stats_card", contentType = "stats_card") {
                SakuraMusicStatsCard(
                    downloadedTracks = downloadedTracks,
                    playCountManager = playCountManager,
                    isEnabled = featureToggles.musicStatsEnabled,
                    onToggleEnabled = { enabled ->
                        onUpdateFeatureToggles { it.copy(musicStatsEnabled = enabled) }
                    }
                )
            }
        }

        if (featureToggles.libraryDoctorEnabled) {
            item(key = "lib_doctor_card", contentType = "doctor_card") {
                LibraryDoctorCard(
                    downloadedTracks = downloadedTracks,
                    isEnabled = featureToggles.libraryDoctorEnabled,
                    onToggleEnabled = { enabled ->
                        onUpdateFeatureToggles { it.copy(libraryDoctorEnabled = enabled) }
                    },
                    onRunDoctorCleanup = onRunLibraryDoctor
                )
            }
        }

        item(key = "lib_cookie_sync_btn", contentType = "action_btn") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onDownloadAccountLibraryWithCookies(LikedSongsSourceType.YOUTUBE_MUSIC) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primaryAccent)
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("YT Müzik (LM)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = { onDownloadAccountLibraryWithCookies(LikedSongsSourceType.YOUTUBE_VIDEOS) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.surfaceContainer)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = palette.onSurface)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("YT Videolar (LL)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = palette.onSurface)
                }
            }
        }

        if (filteredTracks.isEmpty()) {
            item(key = "lib_empty", contentType = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.surfaceContainer)
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Henüz çevrimdışı kitaplığınızda şarkı bulunmuyor.",
                        color = palette.onSurfaceMuted
                    )
                }
            }
        } else {
            itemsIndexed(
                items = displayedTracks,
                key = { _, saved -> saved.audioFile.absolutePath },
                contentType = { _, _ -> "library_track_row" }
            ) { index, saved ->
                val isPlayingThis = currentPlayingTrackId == saved.trackInfo.id
                val isInActiveWindow = windowState.isTrackInActiveMemoryWindow(index)
                val playlistName = remember(saved.trackInfo.playlistTitle, saved.trackInfo.album, playlists) {
                    saved.trackInfo.effectivePlaylistName()
                        ?: playlists.firstOrNull { p ->
                            p.tracks.any { t -> t.audioFile.absolutePath == saved.audioFile.absolutePath }
                        }?.name
                }
                // Main Thread üzerinde dosya sistemi I/O (audioFile.length()) çağrısı yapılmaz; Room önbelleğindeki boyut kullanılır
                val fileInfoLabel = remember(saved.audioFile.name, saved.trackInfo.contentLengthBytes, playlistName) {
                    val ext = saved.audioFile.name.substringAfterLast('.', "M4A").uppercase()
                    val kb = (saved.trackInfo.contentLengthBytes / 1024L).coerceAtLeast(1L)
                    if (!playlistName.isNullOrBlank()) {
                        "$ext • $kb KB • Liste: $playlistName"
                    } else {
                        "$ext • $kb KB"
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isPlayingThis) palette.primaryAccent.copy(alpha = 0.16f)
                            else palette.surfaceContainer
                        )
                        .border(
                            width = 1.dp,
                            color = if (isPlayingThis) palette.primaryAccent else palette.dividerOrBorder,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onPlaySavedTrack(saved) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val coverModel = saved.coverFile ?: saved.trackInfo.thumbnailUrl
                    OptimizedThumbnailImage(
                        model = coverModel,
                        contentDescription = saved.trackInfo.title,
                        isWithinActiveWindow = isInActiveWindow,
                        fallbackIcon = Icons.Default.MusicNote,
                        fallbackTint = palette.primaryAccent,
                        backgroundColor = palette.surfaceVariant,
                        iconSize = 24.dp,
                        targetPixelSize = 128,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = saved.trackInfo.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = saved.trackInfo.artist,
                            fontSize = 13.sp,
                            color = palette.onSurfaceMuted,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = fileInfoLabel,
                            fontSize = 11.sp,
                            color = palette.primaryAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (featureToggles.tagEditorEnabled) {
                        IconButton(onClick = { trackForTagEditor = saved }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Etiket & İsim Düzenle",
                                tint = palette.primaryAccent
                            )
                        }
                    }

                    IconButton(onClick = { trackForPlaylistModal = saved }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Listeye Ekle",
                            tint = palette.secondaryAccent
                        )
                    }

                    IconButton(onClick = { onDeleteSavedTrack(saved) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Sil",
                            tint = palette.tertiaryAccent
                        )
                    }

                    IconButton(onClick = { onPlaySavedTrack(saved) }) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Çal",
                            tint = palette.primaryAccent
                        )
                    }
                }
            }
        }
    }

    val editingTrack = trackForTagEditor
    if (editingTrack != null && featureToggles.tagEditorEnabled) {
        OfflineTagEditorDialog(
            saved = editingTrack,
            onSaveMetadata = { newTitle, newArtist, newAlbum, newCoverUrl ->
                onUpdateTrackMetadata(editingTrack, newTitle, newArtist, newAlbum, newCoverUrl)
            },
            onAutoFetchYoutubeTitle = onAutoFetchYoutubeTitle,
            onDismiss = { trackForTagEditor = null }
        )
    }

    val modalTrack = trackForPlaylistModal
    if (modalTrack != null) {
        AlertDialog(
            onDismissRequest = { trackForPlaylistModal = null },
            title = { Text("'${modalTrack.trackInfo.title}' Şarkısını Listeye Ekle") },
            text = {
                if (playlists.isEmpty()) {
                    Text("Henüz bir çalma listeniz yok. 'Listeler' sekmesinden yeni bir liste oluşturabilirsiniz.")
                } else {
                    LazyColumn(
                        modifier = Modifier.height(240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(playlists, key = { it.id }, contentType = { "playlist_modal_btn" }) { playlist ->
                            Button(
                                onClick = {
                                    onAddTrackToPlaylist(playlist.id, modalTrack)
                                    trackForPlaylistModal = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${playlist.name} (${playlist.tracks.size})")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { trackForPlaylistModal = null }) {
                    Text("Kapat")
                }
            }
        )
    }
}

@Composable
private fun LibraryStep3ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalAppThemePalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surfaceVariant.copy(alpha = 0.6f))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
